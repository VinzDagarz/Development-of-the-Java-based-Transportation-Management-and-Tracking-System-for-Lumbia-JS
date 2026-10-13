/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package gui;
import model.Driver;
import service.DriverService;
import javax.swing.JOptionPane;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.SwingUtilities;
import java.text.SimpleDateFormat;
import java.util.Date;
/**
 *
 * @author MSI
 */
public class DriverUser extends javax.swing.JFrame {
    private Driver currentDriver;
    private boolean timeSet = false;
    private String selectedRoute = "";
    private javax.swing.Timer clockTimer;
    private boolean databaseOnline;

    public DriverUser() {
        this(null);
    }

    public DriverUser(Driver driver) {
        currentDriver = driver;
        initComponents();
        setTitle("TranSeat - Driver");
        setResizable(true);
        setLocationRelativeTo(null);
        setExtendedState(javax.swing.JFrame.MAXIMIZED_BOTH);
        initializeDriverScreen();
        startLiveClock();
        setupSharedDatabaseTools();
        databaseOnline = service.DBConnection.isAvailable();
        applyDatabaseMode();
        itmFrom.addActionListener(e -> refreshStopChoicesForDirection());
        itmTo.addActionListener(e -> refreshStopChoicesForDirection());
    }

    private void applyDatabaseMode() {
        setTitle("TranSeat - Driver | " + (databaseOnline ? "ONLINE" : "OFFLINE"));
        if (!databaseOnline) {
            toggleDeparture.setEnabled(false);
            toggleSetR1.setEnabled(false);
            toggleSetR2.setEnabled(false);
            btnSet.setEnabled(false);
            btnCurrentTime.setEnabled(false);
            itmDeparture.setEnabled(false);
            itmMinute.setEnabled(false);
            itmTime.setEnabled(false);
            itmFrom.setEnabled(false);
            itmTo.setEnabled(false);
            itmR1.setEnabled(false);
            itmR2.setEnabled(false);
            JOptionPane.showMessageDialog(this,
                    "XAMPP/MySQL is offline. Driver login is allowed, but departure setting and route updates are disabled.",
                    "Offline Mode", JOptionPane.INFORMATION_MESSAGE);
        }
    }

    private void initializeDriverScreen() {
        toggleDeparture.setText("OFF");
        toggleDeparture.setSelected(false);
        toggleSetR1.setText("OFF");
        toggleSetR1.setSelected(false);
        toggleSetR2.setText("OFF");
        toggleSetR2.setSelected(false);
        if (currentDriver != null) {
            // Reload the latest saved driver state so reopening the dashboard
            // preserves the active departure and current route stop.
            currentDriver = DriverService.findById(currentDriver.getId());
            jLabel2.setText("Welcome, " + currentDriver.getName() + "!");
            if (currentDriver.isDeparting()) {
                selectedRoute = currentDriver.getRoute();
                timeSet = !currentDriver.getDepartureTime().isEmpty();
                toggleDeparture.setSelected(true);
                if ("R1".equals(selectedRoute)) {
                    toggleSetR1.setSelected(true);
                    itmR1.setSelectedItem(currentDriver.getCurrentStop());
                } else if ("R2".equals(selectedRoute)) {
                    toggleSetR2.setSelected(true);
                    itmR2.setSelectedItem(currentDriver.getCurrentStop());
                }
            }
        }
        updateToggleAppearance(toggleDeparture);
        updateToggleAppearance(toggleSetR1);
        updateToggleAppearance(toggleSetR2);
    }

    private void refreshStopChoicesForDirection() {
        String route = determineRoute();
        if (route.isEmpty()) return;
        String from = String.valueOf(itmFrom.getSelectedItem());
        java.util.List<String> stops = new java.util.ArrayList<>(service.RouteService.getStops(route));
        if (!"Lumbia".equalsIgnoreCase(from)) java.util.Collections.reverse(stops);
        javax.swing.DefaultComboBoxModel<String> model = new javax.swing.DefaultComboBoxModel<>();
        for (String stop : stops) model.addElement(stop);
        if ("R1".equals(route)) {
            String old = String.valueOf(itmR1.getSelectedItem());
            itmR1.setModel(model);
            if (stops.contains(old)) itmR1.setSelectedItem(old);
        } else {
            String old = String.valueOf(itmR2.getSelectedItem());
            itmR2.setModel(model);
            if (stops.contains(old)) itmR2.setSelectedItem(old);
        }
    }

    private void startLiveClock() {
        final SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss a");
        clockTimer = new javax.swing.Timer(1000, e -> {
            String welcome = currentDriver == null ? "Welcome, Driver!" :
                    "Welcome, " + currentDriver.getName() + "!";
            jLabel2.setText(welcome + "   |   " + format.format(new Date()));
        });
        clockTimer.setInitialDelay(0);
        clockTimer.start();
    }

    /**
     * The PHP driver dashboard has Round Trip Calculator and End Round Trip
     * actions. These menu actions use the exact same MySQL rows and rules so
     * either application can continue the work of the other.
     */
    private void setupSharedDatabaseTools() {
        JMenuBar bar = new JMenuBar();
        JMenu menu = new JMenu("Shared DB Tools");

        JMenu calc = new JMenu("Round Trip Calculator");
        int[] adds = {5,10,12,15,17,20,23,25,30};
        int[] subs = {1,3,5,10,20};
        for (int n : adds) {
            JMenuItem item = new JMenuItem("Add ₱" + n);
            item.addActionListener(e -> changeRoundTrip("add", n));
            calc.add(item);
        }
        calc.addSeparator();
        for (int n : subs) {
            JMenuItem item = new JMenuItem("Subtract ₱" + n);
            item.addActionListener(e -> changeRoundTrip("subtract", n));
            calc.add(item);
        }
        menu.add(calc);

        JMenuItem end = new JMenuItem("End Round Trip");
        end.addActionListener(e -> endRoundTripFromJava());
        menu.add(end);

        JMenuItem refresh = new JMenuItem("Refresh From MySQL");
        refresh.addActionListener(e -> reloadCurrentDriver());
        menu.add(refresh);

        bar.add(menu);
        setJMenuBar(bar);
    }

    private void reloadCurrentDriver() {
        if (currentDriver == null) return;
        currentDriver = DriverService.findById(currentDriver.getId());
        if (currentDriver != null) initializeDriverScreen();
    }

    private void changeRoundTrip(String type, double amount) {
        if (currentDriver == null || !currentDriver.isDeparting()) {
            JOptionPane.showMessageDialog(this,
                    "Round Trip Calculator is available only while Departure is ON.",
                    "Round Trip Calculator", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (DriverService.changeRoundTripEarnings(currentDriver.getId(), type, amount)) {
            currentDriver = DriverService.findById(currentDriver.getId());
            JOptionPane.showMessageDialog(this,
                    "Round Trip Earnings: ₱" + String.format("%.2f", currentDriver.getRoundTripEarnings()),
                    "Updated", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this,
                    "The calculator amount is invalid, the departure is inactive, or the subtraction would go below ₱0.00.",
                    "Calculator", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void endRoundTripFromJava() {
        if (currentDriver == null || !currentDriver.isDeparting()) {
            JOptionPane.showMessageDialog(this, "There is no active round trip to end.",
                    "End Round Trip", JOptionPane.WARNING_MESSAGE);
            return;
        }
        String[] reasons = {"Completed round trip","Accident","Flat tire","Engine failure",
                "Crime involving passengers","Natural disaster"};
        String reason = (String) JOptionPane.showInputDialog(this,
                "Choose the round-trip result:", "End Round Trip",
                JOptionPane.QUESTION_MESSAGE, null, reasons, reasons[0]);
        if (reason == null) return;
        if (DriverService.endRoundTrip(currentDriver.getId(), reason)) {
            currentDriver = DriverService.findById(currentDriver.getId());
            toggleDeparture.setSelected(false);
            toggleSetR1.setSelected(false);
            toggleSetR2.setSelected(false);
            updateToggleAppearance(toggleDeparture);
            updateToggleAppearance(toggleSetR1);
            updateToggleAppearance(toggleSetR2);
            JOptionPane.showMessageDialog(this,
                    "Round trip ended as \"" + reason + "\". Its round-trip earnings were transferred to Total Earnings.",
                    "Round Trip Ended", JOptionPane.INFORMATION_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(this, "Unable to end the round trip.",
                    "End Round Trip", JOptionPane.WARNING_MESSAGE);
        }
    }

    private void setCurrentDepartureTime() {
        Date now = new Date();
        itmDeparture.setSelectedItem(new SimpleDateFormat("h").format(now));
        itmMinute.setSelectedItem(new SimpleDateFormat("mm").format(now));
        itmTime.setSelectedItem(new SimpleDateFormat("a").format(now));
    }

    private void updateToggleAppearance(javax.swing.JToggleButton button) {
        button.setText(button.isSelected() ? "ON" : "OFF");
        button.setBackground(button.isSelected() ? java.awt.Color.GREEN : java.awt.Color.RED);
        button.setForeground(button.isSelected() ? java.awt.Color.BLACK : java.awt.Color.WHITE);
    }

    private String determineRoute() {
        String from = String.valueOf(itmFrom.getSelectedItem());
        String to = String.valueOf(itmTo.getSelectedItem());
        if (("Lumbia".equals(from) && "Cogon".equals(to)) ||
            ("Cogon".equals(from) && "Lumbia".equals(to))) return "R1";
        if (("Lumbia".equals(from) && "Carmen".equals(to)) ||
            ("Carmen".equals(from) && "Lumbia".equals(to))) return "R2";
        return "";
    }

    private String selectedDepartureTime() {
        return String.valueOf(itmDeparture.getSelectedItem()) + ":" +
               String.valueOf(itmMinute.getSelectedItem()) + " " +
               String.valueOf(itmTime.getSelectedItem());
    }

/**
     * This method is called from within the constructor to initialize the form.
     * WARNING: Do NOT modify this code. The content of this method is always
     * regenerated by the Form Editor.
     */
    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = new javax.swing.JPanel();
        jPanel2 = new javax.swing.JPanel();
        jLabel1 = new javax.swing.JLabel();
        jLabel2 = new javax.swing.JLabel();
        jPanel3 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        itmDeparture = new javax.swing.JComboBox<>();
        itmMinute = new javax.swing.JComboBox<>();
        itmTime = new javax.swing.JComboBox<>();
        btnCurrentTime = new javax.swing.JButton();
        btnSet = new javax.swing.JButton();
        jPanel4 = new javax.swing.JPanel();
        jLabel4 = new javax.swing.JLabel();
        toggleDeparture = new javax.swing.JToggleButton();
        jLabel5 = new javax.swing.JLabel();
        jLabel6 = new javax.swing.JLabel();
        itmFrom = new javax.swing.JComboBox<>();
        itmTo = new javax.swing.JComboBox<>();
        jLabel7 = new javax.swing.JLabel();
        jPanel5 = new javax.swing.JPanel();
        jLabel8 = new javax.swing.JLabel();
        itmR2 = new javax.swing.JComboBox<>();
        itmR1 = new javax.swing.JComboBox<>();
        jLabel9 = new javax.swing.JLabel();
        toggleSetR1 = new javax.swing.JToggleButton();
        toggleSetR2 = new javax.swing.JToggleButton();
        btnLogOut = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel1.setBackground(new java.awt.Color(255, 102, 51));

        jPanel2.setBackground(new java.awt.Color(204, 204, 0));

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/photos/TranSeat Logo 2 (1).jpg"))); // NOI18N

        jLabel2.setFont(new java.awt.Font("Malgun Gothic", 1, 15)); // NOI18N
        jLabel2.setText("Welcome, Driver!");

        jPanel3.setBackground(new java.awt.Color(255, 153, 51));
        jPanel3.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        jLabel3.setFont(new java.awt.Font("Segoe UI", 1, 15)); // NOI18N
        jLabel3.setText("Departure Set");

        itmDeparture.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "11", "12" }));
        itmDeparture.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmDepartureActionPerformed(evt);
            }
        });

        itmMinute.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] {
            "00","01","02","03","04","05","06","07","08","09",
            "10","11","12","13","14","15","16","17","18","19",
            "20","21","22","23","24","25","26","27","28","29",
            "30","31","32","33","34","35","36","37","38","39",
            "40","41","42","43","44","45","46","47","48","49",
            "50","51","52","53","54","55","56","57","58","59"
        }));

        btnCurrentTime.setText("Current Time");
        btnCurrentTime.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnCurrentTimeActionPerformed(evt);
            }
        });

        itmTime.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "AM", "PM", " " }));
        itmTime.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmTimeActionPerformed(evt);
            }
        });

        btnSet.setText("Set");
        btnSet.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnSetActionPerformed(evt);
            }
        });

        jPanel4.setBackground(new java.awt.Color(255, 51, 0));
        jPanel4.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        jLabel4.setFont(new java.awt.Font("Verdana", 1, 12)); // NOI18N
        jLabel4.setText("Departure Setter");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(30, 30, 30)
                .addComponent(jLabel4)
                .addContainerGap(31, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel4)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        toggleDeparture.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleDepartureActionPerformed(evt);
            }
        });

        jLabel5.setText("From:");

        jLabel6.setText("To:");

        itmFrom.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Lumbia", "Cogon", "Carmen" }));

        itmTo.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Cogon", "Lumbia", "Carmen" }));

        jLabel7.setFont(new java.awt.Font("Segoe UI", 1, 15)); // NOI18N
        jLabel7.setText("Departure Time:");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addComponent(jLabel7)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(itmDeparture, javax.swing.GroupLayout.PREFERRED_SIZE, 50, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(itmMinute, javax.swing.GroupLayout.PREFERRED_SIZE, 55, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(itmTime, javax.swing.GroupLayout.PREFERRED_SIZE, 62, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnCurrentTime)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSet)
                        .addGap(0, 8, Short.MAX_VALUE))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(toggleDeparture, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addGap(6, 6, 6)
                                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                                    .addGroup(jPanel3Layout.createSequentialGroup()
                                        .addComponent(jLabel5)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(itmFrom, javax.swing.GroupLayout.PREFERRED_SIZE, 103, javax.swing.GroupLayout.PREFERRED_SIZE)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(jLabel6)
                                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                        .addComponent(itmTo, javax.swing.GroupLayout.PREFERRED_SIZE, 107, javax.swing.GroupLayout.PREFERRED_SIZE))
                                    .addComponent(jLabel3))
                                .addGap(0, 0, Short.MAX_VALUE)))
                        .addContainerGap())))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(5, 5, 5)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(toggleDeparture, javax.swing.GroupLayout.PREFERRED_SIZE, 26, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel3)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel5)
                    .addComponent(jLabel6)
                    .addComponent(itmFrom, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(itmTo, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                        .addComponent(itmDeparture, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(itmMinute, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(itmTime, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addComponent(btnCurrentTime)
                        .addComponent(btnSet))
                    .addComponent(jLabel7))
                .addGap(73, 73, 73))
        );

        jPanel5.setBackground(new java.awt.Color(255, 153, 51));
        jPanel5.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        jLabel8.setText("R1 (Lumbia-Cogon)");

        itmR2.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Centro Lumbia", "F. Delima St.", "Masterson Ave.", "Macapagal Drive", "SM Bypass Road", "C. Diel St.", "Villarin St.", "Max Suniel St.", "V. Neri St. ", "Carmen Public Market" }));
        itmR2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmR2ActionPerformed(evt);
            }
        });

        itmR1.setModel(new javax.swing.DefaultComboBoxModel<>(new String[] { "Centro Lumbia ", "Palalan Road", "Masterson Ave. ", "Kagay-an Bridge", "Rodelsa Circle", "Tomas Saco St.", "Fernandez St.", "Corrales Ave.", "Hayes St.", "Cogon Public Market" }));
        itmR1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                itmR1ActionPerformed(evt);
            }
        });

        jLabel9.setText("R2 (Lumbia-Carmen)");

        toggleSetR1.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleSetR1ActionPerformed(evt);
            }
        });

        toggleSetR2.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                toggleSetR2ActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(jLabel9)
                        .addGap(0, 0, Short.MAX_VALUE))
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel5Layout.createSequentialGroup()
                                .addComponent(jLabel8)
                                .addGap(0, 0, Short.MAX_VALUE))
                            .addComponent(itmR2, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(itmR1, 0, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(toggleSetR1, javax.swing.GroupLayout.DEFAULT_SIZE, 155, Short.MAX_VALUE)
                            .addComponent(toggleSetR2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
                .addContainerGap())
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel8)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(itmR1, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(toggleSetR1, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(jLabel9)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(itmR2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(toggleSetR2, javax.swing.GroupLayout.PREFERRED_SIZE, 22, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addContainerGap(34, Short.MAX_VALUE))
        );

        btnLogOut.setText("Log Out");
        btnLogOut.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnLogOutActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel2Layout = new javax.swing.GroupLayout(jPanel2);
        jPanel2.setLayout(jPanel2Layout);
        jPanel2Layout.setHorizontalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addGap(16, 16, 16)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                        .addComponent(jLabel2)
                        .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel1)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                        .addComponent(btnLogOut)))
                .addContainerGap(19, Short.MAX_VALUE))
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel2)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jLabel1))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGap(33, 33, 33)
                        .addComponent(btnLogOut)))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jPanel3, javax.swing.GroupLayout.PREFERRED_SIZE, 138, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jPanel5, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel1Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel2, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void toggleDepartureActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleDepartureActionPerformed
        updateToggleAppearance(toggleDeparture);
        if (currentDriver == null) {
            toggleDeparture.setSelected(false);
            updateToggleAppearance(toggleDeparture);
            return;
        }

        if (toggleDeparture.isSelected()) {
            if (!timeSet) {
                JOptionPane.showMessageDialog(this, "Set the departure route and time first.",
                        "Departure", JOptionPane.WARNING_MESSAGE);
                toggleDeparture.setSelected(false);
                updateToggleAppearance(toggleDeparture);
                return;
            }

            if (selectedRoute.isEmpty()) selectedRoute = determineRoute();
            if (selectedRoute.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Choose a valid route: Lumbia-Cogon (R1) or Lumbia-Carmen (R2).",
                        "Departure", JOptionPane.WARNING_MESSAGE);
                toggleDeparture.setSelected(false);
                updateToggleAppearance(toggleDeparture);
                return;
            }

            String stop = "R1".equals(selectedRoute)
                    ? String.valueOf(itmR1.getSelectedItem())
                    : String.valueOf(itmR2.getSelectedItem());

            DriverService.startDeparture(currentDriver.getId(), selectedRoute,
                    stop, selectedDepartureTime(),
                    String.valueOf(itmFrom.getSelectedItem()),
                    String.valueOf(itmTo.getSelectedItem()));
            currentDriver = DriverService.findById(currentDriver.getId());

            if ("R1".equals(selectedRoute)) toggleSetR1.setSelected(true);
            if ("R2".equals(selectedRoute)) toggleSetR2.setSelected(true);
            updateToggleAppearance(toggleSetR1);
            updateToggleAppearance(toggleSetR2);

            JOptionPane.showMessageDialog(this,
                    "Departure is now ACTIVE on " + selectedRoute + ".\nPassenger dashboard will now receive your live route status.",
                    "Departure Started", JOptionPane.INFORMATION_MESSAGE);
        } else {
            // PHP requires every active departure to be ended with a round-trip
            // result. Do the same in Java so earnings and end_reason stay in sync.
            toggleDeparture.setSelected(true);
            updateToggleAppearance(toggleDeparture);
            endRoundTripFromJava();
        }
    }//GEN-LAST:event_toggleDepartureActionPerformed

    private void itmTimeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmTimeActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_itmTimeActionPerformed

    private void itmDepartureActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmDepartureActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_itmDepartureActionPerformed

    private void btnCurrentTimeActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnCurrentTimeActionPerformed
        setCurrentDepartureTime();
        JOptionPane.showMessageDialog(this,
                "Departure time fields were set to the current time.",
                "Current Time", JOptionPane.INFORMATION_MESSAGE);
    }//GEN-LAST:event_btnCurrentTimeActionPerformed

    private void btnSetActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnSetActionPerformed
        String route = determineRoute();
        if (route.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Select a valid route: Lumbia-Cogon (R1) or Lumbia-Carmen (R2).",
                    "Invalid Route", JOptionPane.WARNING_MESSAGE);
            return;
        }

        if (currentDriver != null && currentDriver.isDeparting()) {
            JOptionPane.showMessageDialog(this,
                    "Stop the current departure before setting a new route or departure time.",
                    "Departure Active", JOptionPane.WARNING_MESSAGE);
            return;
        }

        selectedRoute = route;
        timeSet = true;
        JOptionPane.showMessageDialog(this,
                "Departure time set to " + selectedDepartureTime() + " for " + route + ".",
                "Departure Set", JOptionPane.INFORMATION_MESSAGE);

        if ("R1".equals(route)) {
            toggleSetR2.setSelected(false);
            updateToggleAppearance(toggleSetR2);
        } else {
            toggleSetR1.setSelected(false);
            updateToggleAppearance(toggleSetR1);
        }
    }//GEN-LAST:event_btnSetActionPerformed

    private void itmR1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmR1ActionPerformed
        if (currentDriver != null && currentDriver.isDeparting()
                && "R1".equals(currentDriver.getRoute())) {
            String stop = String.valueOf(itmR1.getSelectedItem());
            if (DriverService.updateStop(currentDriver.getId(), "R1", stop)) {
                currentDriver = DriverService.findById(currentDriver.getId());
            }
        }
    }//GEN-LAST:event_itmR1ActionPerformed

    private void itmR2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_itmR2ActionPerformed
        if (currentDriver != null && currentDriver.isDeparting()
                && "R2".equals(currentDriver.getRoute())) {
            String stop = String.valueOf(itmR2.getSelectedItem());
            if (DriverService.updateStop(currentDriver.getId(), "R2", stop)) {
                currentDriver = DriverService.findById(currentDriver.getId());
            }
        }
    }//GEN-LAST:event_itmR2ActionPerformed

    private void toggleSetR1ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleSetR1ActionPerformed
        if (currentDriver != null && currentDriver.isDeparting()
                && !"R1".equals(currentDriver.getRoute())) {
            toggleSetR1.setSelected(false);
            updateToggleAppearance(toggleSetR1);
            JOptionPane.showMessageDialog(this,
                    "The current departure is on " + currentDriver.getRoute()
                            + ". Stop that departure before switching routes.",
                    "Route Locked", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (toggleSetR1.isSelected()) {
            toggleSetR2.setSelected(false);
            selectedRoute = "R1";
        } else if ("R1".equals(selectedRoute)) {
            selectedRoute = "";
        }
        updateToggleAppearance(toggleSetR1);
        updateToggleAppearance(toggleSetR2);

        if (currentDriver != null && currentDriver.isDeparting()
                && "R1".equals(currentDriver.getRoute()) && toggleSetR1.isSelected()) {
            String stop = String.valueOf(itmR1.getSelectedItem());
            if (DriverService.updateStop(currentDriver.getId(), "R1", stop)) {
                currentDriver = DriverService.findById(currentDriver.getId());
            }
        }
    }//GEN-LAST:event_toggleSetR1ActionPerformed

    private void toggleSetR2ActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_toggleSetR2ActionPerformed
        if (currentDriver != null && currentDriver.isDeparting()
                && !"R2".equals(currentDriver.getRoute())) {
            toggleSetR2.setSelected(false);
            updateToggleAppearance(toggleSetR2);
            JOptionPane.showMessageDialog(this,
                    "The current departure is on " + currentDriver.getRoute()
                            + ". Stop that departure before switching routes.",
                    "Route Locked", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (toggleSetR2.isSelected()) {
            toggleSetR1.setSelected(false);
            selectedRoute = "R2";
        } else if ("R2".equals(selectedRoute)) {
            selectedRoute = "";
        }
        updateToggleAppearance(toggleSetR1);
        updateToggleAppearance(toggleSetR2);

        if (currentDriver != null && currentDriver.isDeparting()
                && "R2".equals(currentDriver.getRoute()) && toggleSetR2.isSelected()) {
            String stop = String.valueOf(itmR2.getSelectedItem());
            if (DriverService.updateStop(currentDriver.getId(), "R2", stop)) {
                currentDriver = DriverService.findById(currentDriver.getId());
            }
        }
    }//GEN-LAST:event_toggleSetR2ActionPerformed

    private void btnLogOutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLogOutActionPerformed
        /*
         * IMPORTANT: Logging out must NOT stop the driver's departure.
         * The departure state is persisted by DriverService in MySQL,
         * so the Passenger dashboard can continue seeing this jeepney as
         * active and the driver can log back in without discrepancies.
         * The only way to stop the departure is to manually switch the
         * Departure Setter OFF.
         */
        new UserLogin().setVisible(true);
        dispose();
    }//GEN-LAST:event_btnLogOutActionPerformed

    /**
     * @param args the command line arguments
     */
    public static void main(String args[]) {
        /* Set the Nimbus look and feel */
        //<editor-fold defaultstate="collapsed" desc=" Look and feel setting code (optional) ">
        /* If Nimbus (introduced in Java SE 6) is not available, stay with the default look and feel.
         * For details see http://download.oracle.com/javase/tutorial/uiswing/lookandfeel/plaf.html 
         */
        try {
            for (javax.swing.UIManager.LookAndFeelInfo info : javax.swing.UIManager.getInstalledLookAndFeels()) {
                if ("Nimbus".equals(info.getName())) {
                    javax.swing.UIManager.setLookAndFeel(info.getClassName());
                    break;
                }
            }
        } catch (ClassNotFoundException ex) {
            java.util.logging.Logger.getLogger(DriverUser.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(DriverUser.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(DriverUser.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(DriverUser.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new DriverUser().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnCurrentTime;
    private javax.swing.JButton btnLogOut;
    private javax.swing.JButton btnSet;
    private javax.swing.JComboBox<String> itmDeparture;
    private javax.swing.JComboBox<String> itmMinute;
    private javax.swing.JComboBox<String> itmFrom;
    private javax.swing.JComboBox<String> itmR1;
    private javax.swing.JComboBox<String> itmR2;
    private javax.swing.JComboBox<String> itmTime;
    private javax.swing.JComboBox<String> itmTo;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel7;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JToggleButton toggleDeparture;
    private javax.swing.JToggleButton toggleSetR1;
    private javax.swing.JToggleButton toggleSetR2;
    // End of variables declaration//GEN-END:variables
}
