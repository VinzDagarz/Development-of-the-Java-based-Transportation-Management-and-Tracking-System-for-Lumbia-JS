package service;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * Central MySQL connection for TranSeat.
 *
 * XAMPP defaults are used: localhost, port 3306, database transeatsys_db,
 * username root, and an empty password. Change these values if your
 * MySQL installation uses different credentials.
 */
public final class DBConnection {
    // Defaults for a standard XAMPP installation.
    // They can be overridden without editing this source:
    // -Dtranseat.db.host=localhost
    // -Dtranseat.db.port=3306
    // -Dtranseat.db.name=transeatsys_db
    // -Dtranseat.db.user=root
    // -Dtranseat.db.password=
    private static final String HOST =
            System.getProperty("transeat.db.host", "localhost");
    private static final String PORT =
            System.getProperty("transeat.db.port", "3306");
    private static final String DATABASE =
            System.getProperty("transeat.db.name", "transeatsys_db");
    private static final String USER =
            System.getProperty("transeat.db.user", "root");
    private static final String PASSWORD =
            System.getProperty("transeat.db.password", "");

    private DBConnection() { }

    public static Connection getConnection() throws SQLException {
        loadDriver();
        String url = "jdbc:mysql://" + HOST + ":" + PORT + "/" + DATABASE
                + "?useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=Asia/Manila&connectTimeout=5000";
        return DriverManager.getConnection(url, USER, PASSWORD);
    }

    private static void loadDriver() throws SQLException {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException ex) {
            throw new SQLException(
                    "MySQL Connector/J is missing from the NetBeans project classpath.",
                    ex);
        }
    }

    /**
     * Creates the database itself if it does not exist yet.
     * The tables are created by importing db.sql supplied with this project.
     */
    public static void createDatabaseIfNeeded() throws SQLException {
        loadDriver();
        String url = "jdbc:mysql://" + HOST + ":" + PORT
                + "/?useSSL=false&allowPublicKeyRetrieval=true"
                + "&serverTimezone=Asia/Manila&connectTimeout=5000";
        try (Connection con = DriverManager.getConnection(url, USER, PASSWORD);
             Statement st = con.createStatement()) {
            st.executeUpdate("CREATE DATABASE IF NOT EXISTS transeatsys_db "
                    + "CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
        }
    }

    public static boolean isAvailable() {
        try (Connection con = getConnection()) {
            return con != null && !con.isClosed();
        } catch (SQLException ex) {
            return false;
        }
    }
}
