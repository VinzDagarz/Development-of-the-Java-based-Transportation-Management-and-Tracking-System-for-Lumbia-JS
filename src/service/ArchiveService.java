package service;

import model.Archive;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads and restores archived driver accounts from the same MySQL database
 * used by DriverService and the PHP application.
 */
public class ArchiveService {

    public List<Archive> getArchivedDrivers() {
        List<Archive> archived = new ArrayList<>();
        String sql = "SELECT driver_id, driver_name, plate_number "
                   + "FROM archivedrivers ORDER BY archived_at DESC, driver_id";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                archived.add(new Archive(rs.getString("driver_id"),
                        rs.getString("driver_name"),
                        rs.getString("plate_number")));
            }
        } catch (SQLException ex) {
            // Do not silently pretend the archive is empty if MySQL is unavailable.
            throw new IllegalStateException(
                    "Could not load archived drivers. Check that MySQL/XAMPP is running and the database is configured.",
                    ex);
        }
        return archived;
    }

    /** Marks the driver active again without changing their original ID. */
    public boolean restoreDriver(String driverID) {
        if (driverID == null || driverID.trim().isEmpty()) return false;
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE drivers SET is_archived=0, archived_at=NULL WHERE driver_id=? AND is_archived=1")) {
                ps.setString(1, driverID.trim());
                if (ps.executeUpdate() == 0) { con.rollback(); return false; }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "DELETE FROM archivedrivers WHERE driver_id=?")) {
                ps.setString(1, driverID.trim());
                ps.executeUpdate();
            }
            con.commit();
            return true;
        } catch (SQLException ex) {
            throw new IllegalStateException(
                    "Could not restore the driver. Check that MySQL/XAMPP is running.",
                    ex);
        }
    }
}
