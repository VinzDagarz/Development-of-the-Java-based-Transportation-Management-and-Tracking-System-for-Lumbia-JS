/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 * Click nbfs://nbhost/SystemFileSystem/Templates/GUIForms/JFrame.java to edit this template
 */
package gui;
import model.Admin;
import service.AdminService;
import service.DriverService;
import model.Driver;
import javax.swing.JOptionPane;
import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.table.DefaultTableModel;
/**
 *
 * @author MSI
 */
public class AdministratorUser extends javax.swing.JFrame {
    private final AdminService adminService = new AdminService();
    private javax.swing.Timer refreshTimer;
    private javax.swing.Timer clockTimer;
    private javax.swing.Timer archiveClickTimer;
    private int archiveClickCount = 0;
    private static final String ARCHIVE_ACCESS_PASSWORD = "admin12345678";

    public AdministratorUser() {
        initComponents();
        boolean online = service.DBConnection.isAvailable();
        setTitle("TranSeat - Administrator | " + (online ? "ONLINE" : "OFFLINE"));
        setResizable(true);
        setLocationRelativeTo(null);
        setExtendedState(javax.swing.JFrame.MAXIMIZED_BOTH);
        setupAdminActions();
        refreshAllTables();

        refreshTimer = new javax.swing.Timer(1000, e -> refreshAllTables());
        refreshTimer.start();
        startLiveClock();
        setupSharedAdminViews();
    }

    private void setupSharedAdminViews() {
        javax.swing.JMenuBar bar = new javax.swing.JMenuBar();
        javax.swing.JMenu menu = new javax.swing.JMenu("Shared DB Views");

        javax.swing.JMenuItem current = new javax.swing.JMenuItem("View Current Earnings");
        current.addActionListener(e -> showCurrentEarnings());
        menu.add(current);

        javax.swing.JMenuItem visitors = new javax.swing.JMenuItem("View Passenger Visitor Log");
        visitors.addActionListener(e -> showVisitorLog());
        menu.add(visitors);

        javax.swing.JMenuItem refresh = new javax.swing.JMenuItem("Refresh From MySQL");
        refresh.addActionListener(e -> refreshAllTables());
        menu.add(refresh);

        bar.add(menu);
        setJMenuBar(bar);
    }

    private void showCurrentEarnings() {
        java.util.List<String[]> rows = DriverService.getCurrentEarnings(200);
        String[] cols = {"Driver ID","Route","Amount","Earning Date"};
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(cols,0) {
            @Override public boolean isCellEditable(int r,int c){return false;}
        };
        for (String[] row : rows) model.addRow(new Object[]{row[0],row[1],
                (Double.parseDouble(row[2]) >= 0 ? "+" : "") + "₱" +
                String.format("%.2f", Double.parseDouble(row[2])), row[3]});
        if (rows.isEmpty()) model.addRow(new Object[]{"-","-","No current earnings records","-"});
        javax.swing.JTable table = new javax.swing.JTable(model);
        javax.swing.JScrollPane pane = new javax.swing.JScrollPane(table);
        pane.setPreferredSize(new java.awt.Dimension(720,400));
        javax.swing.JOptionPane.showMessageDialog(this,pane,"Current Earnings (shared MySQL)",javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    private void showVisitorLog() {
        java.util.List<String[]> rows = getVisitorRows();
        String[] cols = {"Visitor Name","Login","Logout","Status"};
        javax.swing.table.DefaultTableModel model = new javax.swing.table.DefaultTableModel(cols,0) {
            @Override public boolean isCellEditable(int r,int c){return false;}
        };
        for (String[] row : rows) model.addRow(row);
        if (rows.isEmpty()) model.addRow(new Object[]{"-","-","-","No visitor records"});
        javax.swing.JTable table = new javax.swing.JTable(model);
        javax.swing.JScrollPane pane = new javax.swing.JScrollPane(table);
        pane.setPreferredSize(new java.awt.Dimension(720,400));
        javax.swing.JOptionPane.showMessageDialog(this,pane,"Passenger Visitor Log (shared MySQL)",javax.swing.JOptionPane.INFORMATION_MESSAGE);
    }

    private java.util.List<String[]> getVisitorRows() {
        java.util.List<String[]> rows = new java.util.ArrayList<>();
        String sql = "SELECT visitor_name,login_at,logout_at FROM visitors ORDER BY login_at DESC LIMIT 200";
        try (java.sql.Connection con = service.DBConnection.getConnection();
             java.sql.PreparedStatement ps = con.prepareStatement(sql);
             java.sql.ResultSet rs = ps.executeQuery()) {
            while (rs.next()) rows.add(new String[]{
                rs.getString(1), rs.getString(2),
                rs.getString(3) == null ? "—" : rs.getString(3),
                rs.getString(3) == null ? "Currently Viewing" : "Logged Out"
            });
        } catch (Exception ignored) { }
        return rows;
    }

    private void setupAdminActions() {
        btnAddDriver.addActionListener(e -> addDriver());
        btnUpdateDriver.addActionListener(e -> updateDriver());
        // A rapid five-click gesture opens the protected archive dashboard.
        // A normal single click still archives the selected driver after the
        // short gesture window expires.
        archiveClickTimer = new javax.swing.Timer(1400, e -> {
            archiveClickTimer.stop();
            if (archiveClickCount < 5) {
                archiveDriver();
            }
            archiveClickCount = 0;
        });
        archiveClickTimer.setRepeats(false);
        btnArchiveDriver.addActionListener(e -> handleArchiveButtonClick());
        btnSearch.addActionListener(e -> searchDriver());
        btnAddEarning.addActionListener(e -> addEarning());
        btnDeductEarning.addActionListener(e -> deductEarning());

        tblDriverList.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) loadSelectedDriver();
        });
    }

    private void addDriver() {
        String id = txtIDManage.getText().trim();
        String name = txtDriverName.getText().trim();
        String plate = txtDriverPlate.getText().trim();

        if (id.isEmpty() || name.isEmpty() || plate.isEmpty()) {
            showMessage("Driver ID, Driver Name, and Plate Number are required.", false);
            return;
        }

        if (adminService.addDriver(id, name, plate)) {
            showMessage("Driver account created successfully. The driver can now log in using the added name.", true);
            clearDriverFields();
            refreshAllTables();
        } else {
            showMessage("Driver ID or Driver Name already exists, or the information is incomplete.", false);
        }
    }

    private void updateDriver() {
        String id = txtIDManage.getText().trim();
        String name = txtDriverName.getText().trim();
        String plate = txtDriverPlate.getText().trim();

        if (id.isEmpty() || name.isEmpty() || plate.isEmpty()) {
            showMessage("Search/select a driver and complete all fields before updating.", false);
            return;
        }

        if (adminService.updateDriver(id, name, plate)) {
            showMessage("Driver information updated.", true);
            refreshAllTables();
        } else {
            showMessage("Unable to update. Check the ID, name uniqueness, and archived status.", false);
        }
    }

    private void handleArchiveButtonClick() {
        archiveClickCount++;
        if (archiveClickTimer != null) {
            archiveClickTimer.stop();
        }

        if (archiveClickCount >= 5) {
            archiveClickCount = 0;
            if (archiveClickTimer != null) archiveClickTimer.stop();
            javax.swing.JPasswordField passwordField = new javax.swing.JPasswordField();
            int result = JOptionPane.showConfirmDialog(this, passwordField,
                    "Enter Archive Dashboard Password",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION
                    && ARCHIVE_ACCESS_PASSWORD.equals(
                            new String(passwordField.getPassword()))) {
                new AdminArchive().setVisible(true);
                dispose();
            } else if (result == JOptionPane.OK_OPTION) {
                JOptionPane.showMessageDialog(this, "Incorrect password.",
                        "Access Denied", JOptionPane.WARNING_MESSAGE);
            }
            return;
        }

        archiveClickTimer.restart();
    }

    private void archiveDriver() {
        String id = txtIDManage.getText().trim();
        if (id.isEmpty()) {
            showMessage("Enter or search a Driver ID first.", false);
            return;
        }

        Driver driver = DriverService.findById(id);
        if (driver == null || driver.isArchived()) {
            showMessage("Active driver not found.", false);
            return;
        }

        int answer = JOptionPane.showConfirmDialog(this,
                "Archive driver " + driver.getName() + " (" + driver.getId() + ")?",
                "Confirm Archive", JOptionPane.YES_NO_OPTION);

        if (answer == JOptionPane.YES_OPTION) {
            if (adminService.archiveDriver(id)) {
                showMessage("Driver archived. The driver can no longer log in.", true);
                clearDriverFields();
                refreshAllTables();
            } else {
                showMessage("Action Failed! Could not archive this driver. Check the database connection and try again.", false);
            }
        }
    }

    private void searchDriver() {
        String id = txtIDManage.getText().trim();
        Driver driver = DriverService.findById(id);

        if (driver == null || driver.isArchived()) {
            showMessage("No active driver found with ID: " + id, false);
            return;
        }

        txtIDManage.setText(driver.getId());
        txtDriverName.setText(driver.getName());
        txtDriverPlate.setText(driver.getPlateNumber());
    }

    private void loadSelectedDriver() {
        int row = tblDriverList.getSelectedRow();
        if (row < 0) return;
        String id = String.valueOf(tblDriverList.getValueAt(row, 0));
        Driver driver = DriverService.findById(id);
        if (driver != null) {
            txtIDManage.setText(driver.getId());
            txtDriverName.setText(driver.getName());
            txtDriverPlate.setText(driver.getPlateNumber());
        }
    }

    private void addEarning() {
        String id = txtIDStat.getText().trim();
        Double amount = parseAmount(txtAddEarningStatus.getText().trim());
        if (amount == null || amount <= 0) {
            showMessage("Enter a valid positive earning amount.", false);
            return;
        }
        if (DriverService.addEarning(id, amount)) {
            showMessage("₱" + String.format("%.2f", amount) + " added to driver earnings.", true);
            txtAddEarningStatus.setText("");
            refreshAllTables();
        } else {
            showMessage("Driver ID not found or driver is archived.", false);
        }
    }

    private void deductEarning() {
        String id = txtIDStat.getText().trim();
        Double amount = parseAmount(txtDeduction.getText().trim());
        if (amount == null || amount <= 0) {
            showMessage("Enter a valid positive deduction amount.", false);
            return;
        }
        if (DriverService.deductEarning(id, amount)) {
            showMessage("₱" + String.format("%.2f", amount) + " deducted from driver earnings.", true);
            txtDeduction.setText("");
            refreshAllTables();
        } else {
            showMessage("Deduction failed. Check the Driver ID and available earnings.", false);
        }
    }

    private Double parseAmount(String value) {
        try {
            return Double.valueOf(value.replace(",", ""));
        } catch (Exception ex) {
            return null;
        }
    }

    private void refreshAllTables() {
        refreshDriverList();
        refreshDepartureStatus();
        refreshEarnings();
    }

    private void refreshDriverList() {
        DefaultTableModel model = (DefaultTableModel) tblDriverList.getModel();
        model.setRowCount(0);
        for (Driver d : DriverService.getActiveDrivers()) {
            model.addRow(new Object[] { d.getId(), d.getName(), d.getPlateNumber() });
        }
    }

    private void startLiveClock() {
        final SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss a");
        clockTimer = new javax.swing.Timer(1000, e ->
                jLabel2.setText("Welcome, Admin!   |   " + format.format(new Date())));
        clockTimer.setInitialDelay(0);
        clockTimer.start();
    }

    private void refreshDepartureStatus() {
        DefaultTableModel model = (DefaultTableModel) tblDepartureStatus.getModel();
        model.setColumnIdentifiers(new Object[]{"Driver ID","Departure","Status","Round Trips","Route","Current Stop"});
        model.setRowCount(0);
        for (Driver d : DriverService.getActiveDrivers()) {
            if (d.isDeparting()) {
                model.addRow(new Object[] {
                    d.getId(), d.getDepartureTime(), "Available", d.getRoundTrips(),
                    d.getRoute() + " " + d.getFromLocation() + " → " + d.getToLocation(),
                    d.getCurrentStop()
                });
            }
        }
        if (model.getRowCount() == 0) {
            model.addRow(new Object[] {"-","-","No active departure",0,"-","-"});
        }
    }

    private void refreshEarnings() {
        DefaultTableModel model = (DefaultTableModel) tblDriverStatus1.getModel();
        model.setRowCount(0);
        for (Driver d : DriverService.getActiveDrivers()) {
            model.addRow(new Object[] {
                d.getId(), d.getName(), d.getPlateNumber(),
                d.isDeparting() ? "Available" : "Not Available",
                String.format("₱%.2f", d.getTotalEarnings())
            });
        }
    }

    private void clearDriverFields() {
        txtIDManage.setText("");
        txtDriverName.setText("");
        txtDriverPlate.setText("");
    }

    private void showMessage(String message, boolean success) {
        JOptionPane.showMessageDialog(this, message,
                success ? "TranSeat" : "Action Failed",
                success ? JOptionPane.INFORMATION_MESSAGE : JOptionPane.WARNING_MESSAGE);
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
        jPanel4 = new javax.swing.JPanel();
        jLabel3 = new javax.swing.JLabel();
        jLabel4 = new javax.swing.JLabel();
        txtDriverName = new javax.swing.JTextField();
        jLabel5 = new javax.swing.JLabel();
        txtDriverPlate = new javax.swing.JTextField();
        jScrollPane1 = new javax.swing.JScrollPane();
        tblDriverList = new javax.swing.JTable();
        btnAddDriver = new javax.swing.JButton();
        btnUpdateDriver = new javax.swing.JButton();
        btnArchiveDriver = new javax.swing.JButton();
        jLabel6 = new javax.swing.JLabel();
        txtIDManage = new javax.swing.JTextField();
        btnSearch = new javax.swing.JButton();
        jPanel17 = new javax.swing.JPanel();
        jPanel18 = new javax.swing.JPanel();
        jLabel8 = new javax.swing.JLabel();
        jScrollPane3 = new javax.swing.JScrollPane();
        tblDriverStatus1 = new javax.swing.JTable();
        jLabel9 = new javax.swing.JLabel();
        txtIDStat = new javax.swing.JTextField();
        jLabel18 = new javax.swing.JLabel();
        txtAddEarningStatus = new javax.swing.JTextField();
        btnAddEarning = new javax.swing.JButton();
        jLabel19 = new javax.swing.JLabel();
        txtDeduction = new javax.swing.JTextField();
        btnDeductEarning = new javax.swing.JButton();
        jPanel5 = new javax.swing.JPanel();
        jPanel19 = new javax.swing.JPanel();
        jLabel20 = new javax.swing.JLabel();
        jScrollPane2 = new javax.swing.JScrollPane();
        tblDepartureStatus = new javax.swing.JTable();
        btnLogOut = new javax.swing.JButton();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);

        jPanel1.setBackground(new java.awt.Color(255, 102, 0));

        jPanel2.setBackground(new java.awt.Color(204, 204, 0));

        jLabel1.setIcon(new javax.swing.ImageIcon(getClass().getResource("/photos/TranSeat Logo 2 (1).jpg"))); // NOI18N

        jLabel2.setFont(new java.awt.Font("Verdana", 1, 15)); // NOI18N
        jLabel2.setText("Welcome, Admin!");

        jPanel3.setBackground(new java.awt.Color(204, 255, 51));
        jPanel3.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        jPanel4.setBackground(new java.awt.Color(255, 255, 153));
        jPanel4.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        jLabel3.setText("Driver Management");

        javax.swing.GroupLayout jPanel4Layout = new javax.swing.GroupLayout(jPanel4);
        jPanel4.setLayout(jPanel4Layout);
        jPanel4Layout.setHorizontalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel4Layout.createSequentialGroup()
                .addGap(17, 17, 17)
                .addComponent(jLabel3)
                .addContainerGap(18, Short.MAX_VALUE))
        );
        jPanel4Layout.setVerticalGroup(
            jPanel4Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel4Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel3)
                .addContainerGap())
        );

        jLabel4.setText("Add Driver Name:");

        txtDriverName.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtDriverNameActionPerformed(evt);
            }
        });

        jLabel5.setText("Driver's Plate No.");

        txtDriverPlate.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtDriverPlateActionPerformed(evt);
            }
        });

        tblDriverList.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null},
                {null, null, null},
                {null, null, null},
                {null, null, null}
            },
            new String [] {
                "Driver ID", "Driver Name", "Driver Plate No."
            }
        ));
        jScrollPane1.setViewportView(tblDriverList);

        btnAddDriver.setText("Add");

        btnUpdateDriver.setText("Update");

        btnArchiveDriver.setText("Archive");

        jLabel6.setText("ID:");

        txtIDManage.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtIDManageActionPerformed(evt);
            }
        });

        btnSearch.setText("Search");

        javax.swing.GroupLayout jPanel3Layout = new javax.swing.GroupLayout(jPanel3);
        jPanel3.setLayout(jPanel3Layout);
        jPanel3Layout.setHorizontalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addGap(6, 6, 6)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(txtIDManage, javax.swing.GroupLayout.PREFERRED_SIZE, 79, javax.swing.GroupLayout.PREFERRED_SIZE)
                            .addComponent(jLabel6))
                        .addGap(18, 18, 18)
                        .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.CENTER)
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addGap(16, 16, 16)
                                .addComponent(txtDriverName)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(txtDriverPlate))
                            .addGroup(jPanel3Layout.createSequentialGroup()
                                .addComponent(jLabel4, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(jLabel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                        .addGap(10, 10, 10))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(251, 251, 251))
                    .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel3Layout.createSequentialGroup()
                        .addComponent(jScrollPane1, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                        .addGap(6, 6, 6))
                    .addGroup(jPanel3Layout.createSequentialGroup()
                        .addComponent(btnAddDriver, javax.swing.GroupLayout.PREFERRED_SIZE, 66, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnUpdateDriver)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnArchiveDriver)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(btnSearch, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))))
        );
        jPanel3Layout.setVerticalGroup(
            jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel3Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel4, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel4)
                    .addComponent(jLabel5)
                    .addComponent(jLabel6))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(txtDriverName, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtDriverPlate, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(txtIDManage, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel3Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(btnAddDriver)
                    .addComponent(btnUpdateDriver)
                    .addComponent(btnArchiveDriver)
                    .addComponent(btnSearch))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.UNRELATED)
                .addComponent(jScrollPane1, javax.swing.GroupLayout.DEFAULT_SIZE, 87, Short.MAX_VALUE)
                .addGap(40, 40, 40))
        );

        jPanel17.setBackground(new java.awt.Color(255, 102, 51));
        jPanel17.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        jPanel18.setBackground(new java.awt.Color(255, 153, 102));
        jPanel18.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        jLabel8.setText("Driver Earnings");

        javax.swing.GroupLayout jPanel18Layout = new javax.swing.GroupLayout(jPanel18);
        jPanel18.setLayout(jPanel18Layout);
        jPanel18Layout.setHorizontalGroup(
            jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel18Layout.createSequentialGroup()
                .addGap(17, 17, 17)
                .addComponent(jLabel8)
                .addContainerGap(19, Short.MAX_VALUE))
        );
        jPanel18Layout.setVerticalGroup(
            jPanel18Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(javax.swing.GroupLayout.Alignment.TRAILING, jPanel18Layout.createSequentialGroup()
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addComponent(jLabel8)
                .addContainerGap())
        );

        tblDriverStatus1.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null},
                {null, null, null, null, null}
            },
            new String [] {
                "Driver ID", "Driver Name", "Driver Plate No.", "Status", "Total Earnings"
            }
        ));
        jScrollPane3.setViewportView(tblDriverStatus1);

        jLabel9.setText("Driver ID:");

        txtIDStat.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtIDStatActionPerformed(evt);
            }
        });

        jLabel18.setText("Add Earnings:");

        txtAddEarningStatus.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtAddEarningStatusActionPerformed(evt);
            }
        });

        btnAddEarning.setText("Add Earning");
        btnAddEarning.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnAddEarningActionPerformed(evt);
            }
        });

        jLabel19.setText("Deduction from Penalty:");

        txtDeduction.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                txtDeductionActionPerformed(evt);
            }
        });

        btnDeductEarning.setText("Deduct Earning");
        btnDeductEarning.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                btnDeductEarningActionPerformed(evt);
            }
        });

        javax.swing.GroupLayout jPanel17Layout = new javax.swing.GroupLayout(jPanel17);
        jPanel17.setLayout(jPanel17Layout);
        jPanel17Layout.setHorizontalGroup(
            jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel17Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jScrollPane3, javax.swing.GroupLayout.Alignment.TRAILING, javax.swing.GroupLayout.DEFAULT_SIZE, 497, Short.MAX_VALUE)
                    .addGroup(jPanel17Layout.createSequentialGroup()
                        .addGap(6, 6, 6)
                        .addGroup(jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.TRAILING)
                            .addComponent(jLabel19)
                            .addGroup(jPanel17Layout.createSequentialGroup()
                                .addComponent(jLabel9)
                                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                                .addComponent(txtIDStat, javax.swing.GroupLayout.PREFERRED_SIZE, 81, javax.swing.GroupLayout.PREFERRED_SIZE)
                                .addGap(27, 27, 27)
                                .addComponent(jLabel18)))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING, false)
                            .addComponent(txtAddEarningStatus)
                            .addComponent(txtDeduction, javax.swing.GroupLayout.DEFAULT_SIZE, 118, Short.MAX_VALUE))
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addGroup(jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addComponent(btnAddEarning, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                            .addComponent(btnDeductEarning, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                    .addGroup(jPanel17Layout.createSequentialGroup()
                        .addComponent(jPanel18, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addGap(0, 0, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel17Layout.setVerticalGroup(
            jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel17Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel18, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel9)
                    .addComponent(txtIDStat, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(jLabel18)
                    .addComponent(txtAddEarningStatus, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnAddEarning))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel17Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.BASELINE)
                    .addComponent(jLabel19)
                    .addComponent(txtDeduction, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                    .addComponent(btnDeductEarning))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane3, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                .addGap(20, 20, 20))
        );

        jPanel5.setBackground(new java.awt.Color(255, 153, 153));
        jPanel5.setBorder(javax.swing.BorderFactory.createEtchedBorder());

        jPanel19.setBackground(new java.awt.Color(255, 204, 255));
        jPanel19.setBorder(javax.swing.BorderFactory.createBevelBorder(javax.swing.border.BevelBorder.RAISED));

        jLabel20.setText("Departure Status");

        javax.swing.GroupLayout jPanel19Layout = new javax.swing.GroupLayout(jPanel19);
        jPanel19.setLayout(jPanel19Layout);
        jPanel19Layout.setHorizontalGroup(
            jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel19Layout.createSequentialGroup()
                .addGap(17, 17, 17)
                .addComponent(jLabel20)
                .addContainerGap(17, Short.MAX_VALUE))
        );
        jPanel19Layout.setVerticalGroup(
            jPanel19Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel19Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jLabel20, javax.swing.GroupLayout.PREFERRED_SIZE, 24, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addContainerGap(javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
        );

        tblDepartureStatus.setModel(new javax.swing.table.DefaultTableModel(
            new Object [][] {
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null},
                {null, null, null, null}
            },
            new String [] {
                "Driver ID", "Departure Set", "Status", "Round Trip(s)"
            }
        ));
        jScrollPane2.setViewportView(tblDepartureStatus);

        javax.swing.GroupLayout jPanel5Layout = new javax.swing.GroupLayout(jPanel5);
        jPanel5.setLayout(jPanel5Layout);
        jPanel5Layout.setHorizontalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel5Layout.createSequentialGroup()
                        .addComponent(jPanel19, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addComponent(jScrollPane2))
                .addContainerGap())
        );
        jPanel5Layout.setVerticalGroup(
            jPanel5Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel5Layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel19, javax.swing.GroupLayout.PREFERRED_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.PREFERRED_SIZE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(jScrollPane2, javax.swing.GroupLayout.PREFERRED_SIZE, 0, Short.MAX_VALUE)
                .addGap(71, 71, 71))
        );

        btnLogOut.setFont(new java.awt.Font("Segoe UI", 1, 24)); // NOI18N
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
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addContainerGap()
                        .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                        .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                        .addComponent(jPanel17, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addGap(119, 119, 119)
                                .addComponent(btnLogOut, javax.swing.GroupLayout.PREFERRED_SIZE, 188, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addGap(29, 29, 29)
                                .addComponent(jLabel2, javax.swing.GroupLayout.PREFERRED_SIZE, 156, javax.swing.GroupLayout.PREFERRED_SIZE))
                            .addGroup(jPanel2Layout.createSequentialGroup()
                                .addContainerGap()
                                .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                        .addGap(67, 67, 67)
                        .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)))
                .addContainerGap())
        );
        jPanel2Layout.setVerticalGroup(
            jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(jPanel2Layout.createSequentialGroup()
                .addComponent(jLabel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addGroup(jPanel2Layout.createSequentialGroup()
                        .addComponent(jLabel1, javax.swing.GroupLayout.DEFAULT_SIZE, 106, Short.MAX_VALUE)
                        .addGap(18, 18, 18)
                        .addComponent(btnLogOut, javax.swing.GroupLayout.PREFERRED_SIZE, 69, javax.swing.GroupLayout.PREFERRED_SIZE))
                    .addComponent(jPanel5, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addGroup(jPanel2Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel17, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(jPanel3, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE))
                .addGap(12, 12, 12))
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
                .addComponent(jPanel2, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addContainerGap())
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

    private void txtIDStatActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtIDStatActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtIDStatActionPerformed

    private void btnAddEarningActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnAddEarningActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnAddEarningActionPerformed

    private void txtAddEarningStatusActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtAddEarningStatusActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtAddEarningStatusActionPerformed

    private void txtDeductionActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtDeductionActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtDeductionActionPerformed

    private void btnDeductEarningActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnDeductEarningActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_btnDeductEarningActionPerformed

    private void txtIDManageActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtIDManageActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtIDManageActionPerformed

    private void txtDriverNameActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtDriverNameActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtDriverNameActionPerformed

    private void txtDriverPlateActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_txtDriverPlateActionPerformed
        // TODO add your handling code here:
    }//GEN-LAST:event_txtDriverPlateActionPerformed

    private void btnLogOutActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_btnLogOutActionPerformed
        if (refreshTimer != null) refreshTimer.stop();
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
            java.util.logging.Logger.getLogger(AdministratorUser.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (InstantiationException ex) {
            java.util.logging.Logger.getLogger(AdministratorUser.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (IllegalAccessException ex) {
            java.util.logging.Logger.getLogger(AdministratorUser.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        } catch (javax.swing.UnsupportedLookAndFeelException ex) {
            java.util.logging.Logger.getLogger(AdministratorUser.class.getName()).log(java.util.logging.Level.SEVERE, null, ex);
        }
        //</editor-fold>

        /* Create and display the form */
        java.awt.EventQueue.invokeLater(new Runnable() {
            public void run() {
                new AdministratorUser().setVisible(true);
            }
        });
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JButton btnAddDriver;
    private javax.swing.JButton btnAddEarning;
    private javax.swing.JButton btnArchiveDriver;
    private javax.swing.JButton btnDeductEarning;
    private javax.swing.JButton btnLogOut;
    private javax.swing.JButton btnSearch;
    private javax.swing.JButton btnUpdateDriver;
    private javax.swing.JLabel jLabel1;
    private javax.swing.JLabel jLabel18;
    private javax.swing.JLabel jLabel19;
    private javax.swing.JLabel jLabel2;
    private javax.swing.JLabel jLabel20;
    private javax.swing.JLabel jLabel3;
    private javax.swing.JLabel jLabel4;
    private javax.swing.JLabel jLabel5;
    private javax.swing.JLabel jLabel6;
    private javax.swing.JLabel jLabel8;
    private javax.swing.JLabel jLabel9;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JPanel jPanel17;
    private javax.swing.JPanel jPanel18;
    private javax.swing.JPanel jPanel19;
    private javax.swing.JPanel jPanel2;
    private javax.swing.JPanel jPanel3;
    private javax.swing.JPanel jPanel4;
    private javax.swing.JPanel jPanel5;
    private javax.swing.JScrollPane jScrollPane1;
    private javax.swing.JScrollPane jScrollPane2;
    private javax.swing.JScrollPane jScrollPane3;
    private javax.swing.JTable tblDepartureStatus;
    private javax.swing.JTable tblDriverList;
    private javax.swing.JTable tblDriverStatus1;
    private javax.swing.JTextField txtAddEarningStatus;
    private javax.swing.JTextField txtDeduction;
    private javax.swing.JTextField txtDriverName;
    private javax.swing.JTextField txtDriverPlate;
    private javax.swing.JTextField txtIDManage;
    private javax.swing.JTextField txtIDStat;
    // End of variables declaration//GEN-END:variables
}
