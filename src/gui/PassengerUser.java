package gui;

import model.Driver;
import service.LoginService;
import service.PassengerService;
import service.RouteService;
import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

public class PassengerUser extends JFrame {
    private final PassengerService passengerService = new PassengerService();
    private final JComboBox<String> itmRoutes = new JComboBox<>(new String[]{
        "R1:Lumbia:Cogon", "R1:Cogon:Lumbia", "R2:Lumbia:Carmen", "R2:Carmen:Lumbia"
    });
    private final JComboBox<String> itmPassengerStop = new JComboBox<>();
    private final JTable tblAvailableDriver = new JTable();
    private final JLabel lblLive = new JLabel("● LIVE - MySQL shared with PHP dashboard");
    private final JLabel lblInfo = new JLabel();
    private Timer refreshTimer, clockTimer;
    private final String passengerName;
    private int visitorId;

    public PassengerUser() { this("Passenger"); }

    public PassengerUser(String name) {
        passengerName = (name == null || name.trim().isEmpty()) ? "Passenger" : name.trim();
        initComponents();
        setTitle("TranSeat - Passenger Dashboard");
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setResizable(true);
        setLocationRelativeTo(null);
        setExtendedState(JFrame.MAXIMIZED_BOTH);
        setMinimumSize(new Dimension(980, 560));
        visitorId = new LoginService().recordVisitorLogin(passengerName);
        updateStopChoices();
        refreshAvailableDrivers();
        refreshTimer = new Timer(1000, e -> refreshAvailableDrivers());
        refreshTimer.start();
        startLiveClock();
    }

    private void initComponents() {
        JPanel root = new JPanel(new BorderLayout(12,12));
        root.setBorder(BorderFactory.createEmptyBorder(15,15,15,15));
        root.setBackground(new Color(255,153,0));

        JPanel header = new JPanel(new BorderLayout(10,10));
        header.setBackground(new Color(204,204,0));
        header.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(160,130,0)),
                BorderFactory.createEmptyBorder(10,12,10,12)));
        JLabel title = new JLabel("TranSeat Passenger Dashboard");
        title.setFont(new Font("Verdana",Font.BOLD,20));
        JLabel welcome = new JLabel("Welcome, "+passengerName+"!");
        welcome.setFont(new Font("Verdana",Font.BOLD,14));
        lblLive.setFont(new Font("Segoe UI",Font.BOLD,12));
        JPanel titleBox = new JPanel();
        titleBox.setOpaque(false);
        titleBox.setLayout(new BoxLayout(titleBox,BoxLayout.Y_AXIS));
        titleBox.add(title); titleBox.add(Box.createVerticalStrut(5));
        titleBox.add(welcome); titleBox.add(Box.createVerticalStrut(5)); titleBox.add(lblLive);
        header.add(titleBox,BorderLayout.WEST);
        JButton logout = new JButton("Log Out");
        logout.addActionListener(e -> logout());
        header.add(logout,BorderLayout.EAST);
        root.add(header,BorderLayout.NORTH);

        JPanel controls = new JPanel(new GridBagLayout());
        controls.setBackground(new Color(255,204,153));
        controls.setBorder(BorderFactory.createTitledBorder("Passenger Route Monitor"));
        GridBagConstraints g = new GridBagConstraints();
        g.insets = new Insets(6,8,6,8); g.anchor = GridBagConstraints.WEST;
        g.gridx=0;g.gridy=0; controls.add(new JLabel("Direction:"),g);
        g.gridx=1;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL; controls.add(itmRoutes,g);
        g.gridx=2;g.weightx=0;g.fill=GridBagConstraints.NONE; controls.add(new JLabel("Your Stop:"),g);
        g.gridx=3;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL; controls.add(itmPassengerStop,g);
        g.gridx=0;g.gridy=1;g.gridwidth=4;g.weightx=1;g.fill=GridBagConstraints.HORIZONTAL;
        lblInfo.setFont(new Font("Segoe UI",Font.PLAIN,12)); controls.add(lblInfo,g);
        root.add(controls,BorderLayout.NORTH);

        DefaultTableModel model = new DefaultTableModel(
                new Object[][]{}, new String[]{"Driver ID","Plate No.","Current Stop","Location Time","Next Stop","Departure","Status at Your Stop"}) {
            @Override public boolean isCellEditable(int r,int c){return false;}
        };
        tblAvailableDriver.setModel(model); tblAvailableDriver.setRowHeight(28);
        tblAvailableDriver.getTableHeader().setReorderingAllowed(false);
        tblAvailableDriver.setAutoCreateRowSorter(true);
        root.add(new JScrollPane(tblAvailableDriver),BorderLayout.CENTER);

        itmRoutes.addActionListener(e -> { updateStopChoices(); refreshAvailableDrivers(); });
        itmPassengerStop.addActionListener(e -> refreshAvailableDrivers());
        setContentPane(root); pack(); setSize(1000,560);
    }

    private String direction() { return String.valueOf(itmRoutes.getSelectedItem()); }

    private void updateStopChoices() {
        String d = direction();
        String[] p = d.split(":",-1);
        if (p.length != 3) return;
        List<String> stops = new ArrayList<>(RouteService.getStops(p[0]));
        if (!"Lumbia".equalsIgnoreCase(p[1])) java.util.Collections.reverse(stops);
        String old = String.valueOf(itmPassengerStop.getSelectedItem());
        DefaultComboBoxModel<String> model = new DefaultComboBoxModel<>();
        for(String stop:stops) model.addElement(stop);
        itmPassengerStop.setModel(model);
        if (stops.contains(old)) itmPassengerStop.setSelectedItem(old);
        else if(model.getSize()>0) itmPassengerStop.setSelectedIndex(0);
    }

    private void refreshAvailableDrivers() {
        String d = direction(), target = String.valueOf(itmPassengerStop.getSelectedItem());
        DefaultTableModel model=(DefaultTableModel)tblAvailableDriver.getModel();
        model.setRowCount(0);
        List<Driver> drivers=passengerService.getAvailableDrivers(d);
        for(Driver driver:drivers) {
            model.addRow(new Object[]{
                driver.getId(),driver.getPlateNumber(),driver.getCurrentStop(),
                formatTimestamp(driver.getCurrentStopAt()),
                passengerService.getNextStop(d,driver.getCurrentStop()),
                driver.getDepartureTime(),
                passengerService.getStatus(d,driver.getCurrentStop(),target)
            });
        }
        if(drivers.isEmpty()) model.addRow(new Object[]{"-","-","No active jeepney","—","—","—","Not Available"});
        lblInfo.setText(d+" • Your stop: "+target+" • Shared DB refresh: every second");
    }

    private String formatTimestamp(String value) {
        if(value==null || value.trim().isEmpty()) return "—";
        try {
            Date dt = java.sql.Timestamp.valueOf(value);
            return new SimpleDateFormat("MMM dd, yyyy hh:mm:ss a").format(dt);
        } catch(Exception e) { return value; }
    }

    private void startLiveClock() {
        clockTimer = new Timer(1000,e -> lblLive.setText("● LIVE - Current time: "+
                new SimpleDateFormat("hh:mm:ss a").format(new Date())+" - MySQL shared with PHP"));
        clockTimer.setInitialDelay(0); clockTimer.start();
    }

    private void logout() {
        if(refreshTimer!=null) refreshTimer.stop();
        if(clockTimer!=null) clockTimer.stop();
        new LoginService().recordVisitorLogout(visitorId);
        new UserLogin().setVisible(true); dispose();
    }

    @Override public void dispose() {
        if(visitorId>0 && (refreshTimer==null || !refreshTimer.isRunning())) {
            // logout() already records the timestamp; do not duplicate it.
        }
        super.dispose();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new PassengerUser().setVisible(true));
    }
}
