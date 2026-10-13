package service;

import model.Driver;
import model.Login;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;

/** Authentication against the same admins/drivers tables used by PHP. */
public class LoginService {

    public boolean isAdmin(Login login) {
        if (login == null || !"Admin".equalsIgnoreCase(login.getUserType())
                || login.getName() == null || login.getSecret() == null) return false;

        // Online mode: authenticate against MySQL.
        if (DBConnection.isAvailable()) {
            String sql = "SELECT password_hash FROM admins WHERE username=?";
            try (Connection con = DBConnection.getConnection();
                 PreparedStatement ps = con.prepareStatement(sql)) {
                ps.setString(1, login.getName().trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) return false;
                    String stored = rs.getString(1);
                    return stored != null && stored.matches("(?i)^[a-f0-9]{64}$")
                            && MessageDigest.isEqual(stored.toLowerCase().getBytes(StandardCharsets.UTF_8),
                               sha256(login.getSecret()).getBytes(StandardCharsets.UTF_8));
                }
            } catch (Exception ex) {
                return false;
            }
        }

        // Offline mode: the administrator account remains available without XAMPP.
        return "admin".equalsIgnoreCase(login.getName().trim())
                && "admin12345678".equals(login.getSecret());
    }

    /** PHP-compatible driver login: name = driver_name, secret = driver_id. */
    public Driver authenticateDriver(Login login) {
        if (login == null || !"Driver".equalsIgnoreCase(login.getUserType())
                || login.getName() == null || login.getSecret() == null) return null;

        if (DBConnection.isAvailable()) {
            Driver d = DriverService.findByName(login.getName());
            return d != null && d.getId().equals(login.getSecret().trim()) ? d : null;
        }

        // Offline mode: use the Java local store. The secret is still the Driver ID.
        Driver d = LocalDataStore.findByName(login.getName());
        return d != null && d.getId().equals(login.getSecret().trim()) ? d : null;
    }

    public boolean isPassenger(Login login) {
        return login != null && "Passenger".equalsIgnoreCase(login.getUserType())
                && login.getName() != null && !login.getName().trim().isEmpty();
    }

    public int recordVisitorLogin(String name) {
        String sql = "INSERT INTO visitors(visitor_name,login_at) VALUES(?,NOW())";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, java.sql.Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, name);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) { return rs.next() ? rs.getInt(1) : 0; }
        } catch (Exception ex) { return 0; }
    }

    public void recordVisitorLogout(int visitorId) {
        if (visitorId <= 0) return;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement("UPDATE visitors SET logout_at=NOW() WHERE id=?")) {
            ps.setInt(1, visitorId); ps.executeUpdate();
        } catch (Exception ignored) { }
    }

    private String sha256(String value) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
        StringBuilder out = new StringBuilder();
        for (byte b : bytes) out.append(String.format("%02x", b));
        return out.toString();
    }
}
