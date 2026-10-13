package service;

import model.Driver;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Single source of truth for driver/departure/earnings data.
 *
 * IMPORTANT: Java and the PHP application both read and write the same
 * MySQL database (transeatsys_db) when online. LocalDataStore is used only
 * as the Java fallback when MySQL/XAMPP is unavailable.
 */
public class DriverService {

    /** Permanent driver accounts required by the TranSeat project. */
    private static final String[][] PERMANENT_DRIVERS = {
        {"01", "Vince082197", "KVJP90"},
        {"02", "Doug080808", "ABC123"},
        {"03", "Ronald212121", "DEF88C"},
        {"04", "Louie345678", "XYZFG6"},
        {"05", "Jacob123412", "JKAUG9"}
    };

    /**
     * Makes sure the five required driver accounts exist in both online and
     * offline Java storage. Existing account data and archive status are preserved.
     */
    public static synchronized void ensurePermanentDrivers() {
        // Always seed the Java-only store so the accounts are available when
        // MySQL/XAMPP is unavailable.
        for (String[] d : PERMANENT_DRIVERS) {
            if (LocalDataStore.findById(d[0]) == null) {
                LocalDataStore.replaceDriver(new Driver(d[0], d[1], d[2]));
            }
        }

        try (Connection con = DBConnection.getConnection()) {
            for (String[] d : PERMANENT_DRIVERS) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO drivers(driver_id,driver_name,plate_number,is_archived) " +
                        "VALUES(?,?,?,0) ON DUPLICATE KEY UPDATE driver_id=VALUES(driver_id)")) {
                    ps.setString(1, d[0]);
                    ps.setString(2, d[1]);
                    ps.setString(3, d[2]);
                    ps.executeUpdate();
                }
            }
        } catch (SQLException ignored) {
            // Offline mode continues using LocalDataStore.
        }
    }

    private static final String ACTIVE_DRIVER_SQL =
            "SELECT d.id, d.driver_id, d.driver_name, d.plate_number, d.is_archived, " +
            "dep.route_code, dep.from_location, dep.to_location, dep.departure_hour, " +
            "dep.departure_minute, dep.departure_period, dep.current_stop, dep.current_stop_at, " +
            "dep.is_active, dep.round_trips, dep.round_trip_earnings, " +
            "COALESCE(e.total_earnings,0) AS total_earnings " +
            "FROM drivers d " +
            "LEFT JOIN departures dep ON dep.driver_pk=d.id " +
            "LEFT JOIN earnings e ON e.driver_pk=d.id " +
            "WHERE d.is_archived=0";

    public static synchronized void reload() { /* DB is queried live. */ }

    public static synchronized boolean addDriver(String id, String name, String plate) {
        if (isBlank(id) || isBlank(name) || isBlank(plate)) return false;
        String sql = "INSERT INTO drivers(driver_id,driver_name,plate_number) VALUES(?,?,?)";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, id.trim());
            ps.setString(2, name.trim());
            ps.setString(3, plate.trim());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) ensureEarningsRow(con, rs.getInt(1));
            }
            LocalDataStore.replaceDriver(new Driver(id.trim(), name.trim(), plate.trim()));
            return true;
        } catch (SQLException ex) { return LocalDataStore.addDriver(id, name, plate); }
    }

    public static synchronized boolean updateDriver(String id, String name, String plate) {
        if (isBlank(id) || isBlank(name) || isBlank(plate)) return false;
        String sql = "UPDATE drivers SET driver_name=?, plate_number=? WHERE driver_id=? AND is_archived=0";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, name.trim());
            ps.setString(2, plate.trim());
            ps.setString(3, id.trim());
            boolean changed = ps.executeUpdate() > 0;
            if (changed) LocalDataStore.replaceDriver(new Driver(id.trim(), name.trim(), plate.trim()));
            return changed;
        } catch (SQLException ex) { return LocalDataStore.updateDriver(id, name, plate); }
    }

    public static synchronized boolean archiveDriver(String id) {
        if (isBlank(id)) return false;
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            // Save a durable archive snapshot before marking the driver inactive.
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO archivedrivers (driver_id, driver_name, plate_number, archived_at) " +
                    "SELECT driver_id, driver_name, plate_number, NOW() FROM drivers " +
                    "WHERE driver_id=? AND is_archived=0 " +
                    "ON DUPLICATE KEY UPDATE driver_name=VALUES(driver_name), " +
                    "plate_number=VALUES(plate_number), archived_at=VALUES(archived_at)")) {
                ps.setString(1, id.trim());
                if (ps.executeUpdate() == 0) { con.rollback(); return false; }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE drivers SET is_archived=1, archived_at=NOW() WHERE driver_id=? AND is_archived=0")) {
                ps.setString(1, id.trim());
                if (ps.executeUpdate() == 0) { con.rollback(); return false; }
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE departures SET is_active=0, ended_at=NOW() " +
                    "WHERE driver_pk=(SELECT id FROM drivers WHERE driver_id=?) AND is_active=1")) {
                ps.setString(1, id.trim());
                ps.executeUpdate();
            }
            con.commit();
            LocalDataStore.archiveDriver(id);
            return true;
        } catch (SQLException ex) { return LocalDataStore.archiveDriver(id); }
    }

    public static synchronized Driver findById(String id) {
        ensurePermanentDrivers();
        if (isBlank(id)) return null;
        return findOne(ACTIVE_DRIVER_SQL + " AND d.driver_id=?", id.trim());
    }

    public static synchronized Driver findByName(String name) {
        ensurePermanentDrivers();
        if (isBlank(name)) return null;
        return findOne(ACTIVE_DRIVER_SQL + " AND LOWER(d.driver_name)=LOWER(?)", name.trim());
    }

    private static Driver findOne(String sql, String value) {
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, value);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                Driver found = mapDriver(rs);
                LocalDataStore.replaceDriver(found);
                return found;
            }
        } catch (SQLException ex) {
            return sql.contains("d.driver_id=?") ? LocalDataStore.findById(value) : LocalDataStore.findByName(value);
        }
    }

    public static synchronized List<Driver> getActiveDrivers() {
        ensurePermanentDrivers();
        List<Driver> result = new ArrayList<>();
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(ACTIVE_DRIVER_SQL);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Driver found = mapDriver(rs);
                result.add(found);
                LocalDataStore.replaceDriver(found);
            }
        } catch (SQLException ignored) {
            result.addAll(LocalDataStore.getActiveDrivers());
        }
        return Collections.unmodifiableList(result);
    }

    public static synchronized List<Driver> getActiveDrivers(String route, String from, String to) {
        ensurePermanentDrivers();
        List<Driver> result = new ArrayList<>();
        String sql = ACTIVE_DRIVER_SQL +
                " AND dep.route_code=? AND dep.from_location=? AND dep.to_location=? AND dep.is_active=1 " +
                "ORDER BY d.driver_id";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, route); ps.setString(2, from); ps.setString(3, to);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) result.add(mapDriver(rs));
            }
        } catch (SQLException ignored) {
            for (Driver d : LocalDataStore.getActiveDrivers()) {
                if (d.isDeparting()
                        && route.equalsIgnoreCase(d.getRoute())
                        && from.equalsIgnoreCase(d.getFromLocation())
                        && to.equalsIgnoreCase(d.getToLocation())) {
                    result.add(d);
                }
            }
        }
        return Collections.unmodifiableList(result);
    }

    /**
     * Matches PHP set_departure: configure the departure but do not create a
     * stop-time record until the driver explicitly records a location.
     */
    public static synchronized boolean setDeparture(String driverId, String route,
                                                    String from, String to, String time,
                                                    boolean startActive) {
        if (isBlank(driverId) || isBlank(route) || isBlank(from) || isBlank(to)) return false;
        String[] parts = splitTime(time);
        int hour = parseInt(parts[0], 12);
        int minute = parseInt(parts[1], 0);
        String period = "PM".equalsIgnoreCase(parts[2]) ? "PM" : "AM";
        List<String> stops = new ArrayList<>(RouteService.getStops(route));
        if (stops.isEmpty()) return false;
        String firstStop = ("Lumbia".equals(from)) ? stops.get(0) : stops.get(stops.size() - 1);

        try (Connection con = DBConnection.getConnection()) {
            Integer pk = findDriverPk(con, driverId);
            if (pk == null) return false;

            Integer departurePk = findDeparturePk(con, pk);
            if (departurePk != null) {
                try (PreparedStatement ps = con.prepareStatement(
                        "SELECT is_active FROM departures WHERE id=?")) {
                    ps.setInt(1, departurePk);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next() && rs.getBoolean(1)) return false;
                    }
                }
            }
            if (departurePk == null) {
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO departures(driver_pk,route_code,from_location,to_location," +
                        "departure_hour,departure_minute,departure_period,current_stop,is_active,round_trips," +
                        "round_trip_earnings,current_stop_at,departed_at,ended_at,end_reason) " +
                        "VALUES(?,?,?,?,?,?,?,?,0,0,0,NULL,NULL,NULL,NULL)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    bindDeparture(ps, pk, route, from, to, hour, minute, period, firstStop);
                    ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE departures SET route_code=?,from_location=?,to_location=?,departure_hour=?," +
                        "departure_minute=?,departure_period=?,current_stop=?,current_stop_at=NULL," +
                        "ended_at=NULL,end_reason=NULL,round_trip_earnings=0 WHERE driver_pk=?")) {
                    ps.setString(1, route); ps.setString(2, from); ps.setString(3, to);
                    ps.setInt(4, hour); ps.setInt(5, minute); ps.setString(6, period);
                    ps.setString(7, firstStop); ps.setInt(8, pk);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM departure_stop_times WHERE departure_pk=?")) {
                    ps.setInt(1, departurePk); ps.executeUpdate();
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "DELETE FROM current_earnings WHERE departure_pk=?")) {
                    ps.setInt(1, departurePk); ps.executeUpdate();
                }
            }

            if (startActive) {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE departures SET is_active=1,round_trips=round_trips+1," +
                        "departed_at=NOW(),ended_at=NULL,end_reason=NULL,round_trip_earnings=0,current_stop_at=NULL " +
                        "WHERE driver_pk=?")) {
                    ps.setInt(1, pk); ps.executeUpdate();
                }
            } else {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE departures SET is_active=0 WHERE driver_pk=?")) {
                    ps.setInt(1, pk); ps.executeUpdate();
                }
            }
            ensureEarningsRow(con, pk);
            return true;
        } catch (SQLException ex) { return false; }
    }

    // Backward-compatible methods used by the existing Swing UI.
    public static synchronized void startDeparture(Driver driver, String route,
                                                    String stop, String time) {
        if (driver != null) {
            String from = driver.getFromLocation();
            String to = driver.getToLocation();
            if (isBlank(from) || isBlank(to)) {
                from = defaultFrom(route); to = defaultTo(route);
            }
            setDeparture(driver.getId(), route, from, to, time, true);
        }
    }

    public static synchronized void startDeparture(String driverId, String route,
                                                    String stop, String time) {
        setDeparture(driverId, route, defaultFrom(route), defaultTo(route), time, true);
    }

    public static synchronized void startDeparture(String driverId, String route,
                                                    String stop, String time,
                                                    String from, String to) {
        setDeparture(driverId, route, from, to, time, true);
    }

    public static synchronized boolean stopDeparture(String driverId) {
        if (isBlank(driverId)) return false;
        String sql = "UPDATE departures SET is_active=0,ended_at=NOW() " +
                     "WHERE driver_pk=(SELECT id FROM drivers WHERE driver_id=?) AND is_active=1";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, driverId.trim());
            return ps.executeUpdate() > 0;
        } catch (SQLException ex) { return false; }
    }

    public static synchronized void stopDeparture(Driver driver) {
        if (driver != null) stopDeparture(driver.getId());
    }

    /** Records the next stop in exactly the same order required by PHP. */
    public static synchronized boolean updateStop(String driverId, String route, String stop) {
        if (isBlank(driverId) || isBlank(route) || isBlank(stop)) return false;
        try (Connection con = DBConnection.getConnection()) {
            Integer pk = findDriverPk(con, driverId);
            if (pk == null) return false;
            DepartureInfo dep = getDepartureInfo(con, pk);
            if (dep == null || !dep.active || !route.equalsIgnoreCase(dep.route)) return false;

            List<String> stops = new ArrayList<>(RouteService.getStops(dep.route));
            if (!dep.from.equals("Lumbia")) Collections.reverse(stops);
            int idx = indexOf(stops, stop);
            if (idx < 0) return false;

            String last = null;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT stop_name FROM departure_stop_times WHERE departure_pk=? " +
                    "ORDER BY passed_at DESC,id DESC LIMIT 1")) {
                ps.setInt(1, dep.id);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) last = rs.getString(1); }
            }
            // The driver combo boxes represent the driver's manually selected
            // current stop, so allow the selected stop to become the current
            // stop immediately. The passenger dashboard reads this same row.
            Timestamp now = new Timestamp(System.currentTimeMillis());
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO departure_stop_times(departure_pk,stop_name,passed_at) VALUES(?,?,NOW()) " +
                    "ON DUPLICATE KEY UPDATE passed_at=VALUES(passed_at)")) {
                ps.setInt(1, dep.id); ps.setString(2, stop); ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE departures SET current_stop=?,current_stop_at=NOW() WHERE driver_pk=? AND is_active=1")) {
                ps.setString(1, stop); ps.setInt(2, pk); ps.executeUpdate();
            }
            return true;
        } catch (SQLException ex) { return false; }
    }

    public static synchronized boolean updateStopTime(String driverId, String stop, String time24) {
        if (isBlank(driverId) || isBlank(stop) || isBlank(time24)) return false;
        try (Connection con = DBConnection.getConnection()) {
            Integer pk = findDriverPk(con, driverId);
            if (pk == null) return false;
            DepartureInfo dep = getDepartureInfo(con, pk);
            if (dep == null || !dep.active) return false;
            List<String> stops = new ArrayList<>(RouteService.getStops(dep.route));
            if (!dep.from.equals("Lumbia")) Collections.reverse(stops);
            if (indexOf(stops, stop) < 0) return false;
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE departure_stop_times SET passed_at=? WHERE departure_pk=? AND stop_name=?")) {
                ps.setString(1, time24); ps.setInt(2, dep.id); ps.setString(3, stop);
                if (ps.executeUpdate() == 0) return false;
            }
            if (stop.equals(dep.currentStop)) {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE departures SET current_stop_at=? WHERE driver_pk=?")) {
                    ps.setString(1, time24); ps.setInt(2, pk); ps.executeUpdate();
                }
            }
            return true;
        } catch (SQLException ex) { return false; }
    }

    public static synchronized double getRoundTripEarnings(String driverId) {
        if (isBlank(driverId)) return 0;
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COALESCE(round_trip_earnings,0) FROM departures " +
                     "WHERE driver_pk=(SELECT id FROM drivers WHERE driver_id=?)")) {
            ps.setString(1, driverId);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getDouble(1) : 0; }
        } catch (SQLException ex) { return 0; }
    }

    public static synchronized boolean changeRoundTripEarnings(String driverId, String type, double amount) {
        if (isBlank(driverId) || amount <= 0 || !("add".equals(type) || "subtract".equals(type))) return false;
        int[] allowedAdd = {5,10,12,15,17,20,23,25,30};
        int[] allowedSub = {1,3,5,10,20};
        int whole = (int)amount;
        boolean allowed = false;
        int[] list = "add".equals(type) ? allowedAdd : allowedSub;
        for (int n : list) if (n == whole && amount == whole) allowed = true;
        if (!allowed) return false;
        double delta = "subtract".equals(type) ? -amount : amount;

        try (Connection con = DBConnection.getConnection()) {
            Integer pk = findDriverPk(con, driverId);
            if (pk == null) return false;
            DepartureInfo dep = getDepartureInfo(con, pk);
            if (dep == null || !dep.active) return false;
            double newTotal = dep.roundTripEarnings + delta;
            if (newTotal < 0) return false;
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE departures SET round_trip_earnings=? WHERE driver_pk=? AND is_active=1")) {
                ps.setDouble(1, newTotal); ps.setInt(2, pk); ps.executeUpdate();
            }
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO current_earnings(driver_pk,departure_pk,amount,earning_date) VALUES(?,?,?,NOW())")) {
                ps.setInt(1, pk); ps.setInt(2, dep.id); ps.setDouble(3, delta); ps.executeUpdate();
            }
            con.commit();
            return true;
        } catch (SQLException ex) { return false; }
    }

    public static synchronized boolean endRoundTrip(String driverId, String reason) {
        String[] reasons = {"Completed round trip","Accident","Flat tire","Engine failure",
                "Crime involving passengers","Natural disaster"};
        boolean valid = false;
        for (String r : reasons) if (r.equals(reason)) valid = true;
        if (isBlank(driverId) || !valid) return false;

        try (Connection con = DBConnection.getConnection()) {
            Integer pk = findDriverPk(con, driverId);
            if (pk == null) return false;
            DepartureInfo dep = getDepartureInfo(con, pk);
            if (dep == null || !dep.active) return false;
            con.setAutoCommit(false);
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE departures SET is_active=0,ended_at=NOW(),end_reason=? WHERE driver_pk=?")) {
                ps.setString(1, reason); ps.setInt(2, pk); ps.executeUpdate();
            }
            ensureEarningsRow(con, pk);
            if (dep.roundTripEarnings > 0) {
                try (PreparedStatement ps = con.prepareStatement(
                        "UPDATE earnings SET total_earnings=total_earnings+? WHERE driver_pk=?")) {
                    ps.setDouble(1, dep.roundTripEarnings); ps.setInt(2, pk); ps.executeUpdate();
                }
                insertTransaction(con, pk, "EARNING", dep.roundTripEarnings);
            }
            con.commit();
            return true;
        } catch (SQLException ex) { return false; }
    }

    public static synchronized List<String[]> getCurrentEarnings(int limit) {
        List<String[]> rows = new ArrayList<>();
        String sql = "SELECT d.driver_id,CONCAT('R',SUBSTRING(p.route_code,2),' ',p.from_location,' → ',p.to_location)," +
                "ce.amount,ce.earning_date FROM current_earnings ce " +
                "JOIN drivers d ON d.id=ce.driver_pk JOIN departures p ON p.id=ce.departure_pk " +
                "ORDER BY ce.earning_date DESC LIMIT ?";
        try (Connection con = DBConnection.getConnection();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, Math.max(1, limit));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) rows.add(new String[]{
                    rs.getString(1), rs.getString(2),
                    rs.getBigDecimal(3).toPlainString(), rs.getString(4)
                });
            }
        } catch (SQLException ignored) { }
        return rows;
    }

    private static Driver mapDriver(ResultSet rs) throws SQLException {
        Driver d = new Driver(rs.getString("driver_id"), rs.getString("driver_name"), rs.getString("plate_number"));
        d.setArchived(rs.getBoolean("is_archived"));
        String route = rs.getString("route_code");
        boolean active = rs.getBoolean("is_active");
        d.setRouteLocations(rs.getString("from_location"), rs.getString("to_location"));
        d.setCurrentStopAt(rs.getString("current_stop_at"));
        d.setRoundTripEarningsValue(rs.getDouble("round_trip_earnings"));
        d.setRoundTripsValue(rs.getInt("round_trips"));
        d.setTotalEarningsValue(rs.getDouble("total_earnings"));
        if (active && route != null && !route.isEmpty()) {
            String period = rs.getString("departure_period");
            String time = String.format("%d:%02d %s", rs.getInt("departure_hour"),
                    rs.getInt("departure_minute"), period);
            d.startDeparture(route, rs.getString("current_stop"), time);
            d.setRoundTripsValue(rs.getInt("round_trips"));
            d.setRoundTripEarningsValue(rs.getDouble("round_trip_earnings"));
            d.setCurrentStopAt(rs.getString("current_stop_at"));
        }
        return d;
    }

    private static Integer findDriverPk(Connection con, String driverId) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT id FROM drivers WHERE driver_id=? AND is_archived=0")) {
            ps.setString(1, driverId.trim());
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : null; }
        }
    }

    private static Integer findDeparturePk(Connection con, int driverPk) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT id FROM departures WHERE driver_pk=?")) {
            ps.setInt(1, driverPk);
            try (ResultSet rs = ps.executeQuery()) { return rs.next() ? rs.getInt(1) : null; }
        }
    }

    private static DepartureInfo getDepartureInfo(Connection con, int pk) throws SQLException {
        String sql = "SELECT id,route_code,from_location,to_location,current_stop,is_active,round_trip_earnings " +
                "FROM departures WHERE driver_pk=?";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, pk);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new DepartureInfo(rs.getInt(1),rs.getString(2),rs.getString(3),rs.getString(4),
                        rs.getString(5),rs.getBoolean(6),rs.getDouble(7));
            }
        }
    }

    private static void bindDeparture(PreparedStatement ps, int pk, String route, String from, String to,
                                      int hour, int minute, String period, String firstStop) throws SQLException {
        ps.setInt(1, pk); ps.setString(2, route); ps.setString(3, from); ps.setString(4, to);
        ps.setInt(5, hour); ps.setInt(6, minute); ps.setString(7, period); ps.setString(8, firstStop);
    }

    private static void ensureEarningsRow(Connection con, int driverPk) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO earnings(driver_pk,total_earnings) VALUES(?,0) ON DUPLICATE KEY UPDATE driver_pk=driver_pk")) {
            ps.setInt(1, driverPk); ps.executeUpdate();
        }
    }

    private static void insertTransaction(Connection con, int driverPk, String type, double amount) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "INSERT INTO earning_transactions(driver_pk,transaction_type,amount) VALUES(?,?,?)")) {
            ps.setInt(1, driverPk); ps.setString(2, type); ps.setDouble(3, amount); ps.executeUpdate();
        }
    }

    public static synchronized boolean addEarning(String id, double amount) {
        if (isBlank(id) || amount <= 0) return false;
        return changeEarning(id, amount, "EARNING");
    }

    public static synchronized boolean deductEarning(String id, double amount) {
        if (isBlank(id) || amount <= 0) return false;
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            Integer pk = findDriverPk(con, id);
            if (pk == null) { con.rollback(); return false; }
            ensureEarningsRow(con, pk);
            double current = 0;
            try (PreparedStatement ps = con.prepareStatement(
                    "SELECT total_earnings FROM earnings WHERE driver_pk=? FOR UPDATE")) {
                ps.setInt(1, pk);
                try (ResultSet rs = ps.executeQuery()) { if (rs.next()) current = rs.getDouble(1); }
            }
            if (amount > current) { con.rollback(); return false; }
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE earnings SET total_earnings=total_earnings-? WHERE driver_pk=?")) {
                ps.setDouble(1, amount); ps.setInt(2, pk); ps.executeUpdate();
            }
            insertTransaction(con, pk, "PENALTY", amount);
            con.commit();
            return true;
        } catch (SQLException ex) { return false; }
    }

    private static boolean changeEarning(String id, double amount, String type) {
        try (Connection con = DBConnection.getConnection()) {
            Integer pk = findDriverPk(con, id);
            if (pk == null) return false;
            ensureEarningsRow(con, pk);
            try (PreparedStatement ps = con.prepareStatement(
                    "UPDATE earnings SET total_earnings=total_earnings+? WHERE driver_pk=?")) {
                ps.setDouble(1, amount); ps.setInt(2, pk); ps.executeUpdate();
            }
            insertTransaction(con, pk, type, amount);
            return true;
        } catch (SQLException ex) { return false; }
    }

    private static class DepartureInfo {
        final int id; final String route, from, to, currentStop; final boolean active; final double roundTripEarnings;
        DepartureInfo(int id,String route,String from,String to,String currentStop,boolean active,double earnings) {
            this.id=id; this.route=route; this.from=from; this.to=to; this.currentStop=currentStop;
            this.active=active; this.roundTripEarnings=earnings;
        }
    }

    private static int indexOf(List<String> list, String value) {
        if (value == null) return -1;
        for (int i=0;i<list.size();i++) if (list.get(i).equalsIgnoreCase(value.trim())) return i;
        return -1;
    }

    private static String defaultFrom(String route) { return "R2".equalsIgnoreCase(route) ? "Lumbia" : "Lumbia"; }
    private static String defaultTo(String route) { return "R2".equalsIgnoreCase(route) ? "Carmen" : "Cogon"; }

    private static String[] splitTime(String time) {
        String[] parts = (time == null ? "" : time.trim()).split("\\s+");
        String clock = parts.length > 0 ? parts[0] : "12:00";
        String period = parts.length > 1 ? parts[1] : "AM";
        String[] hm = clock.split(":");
        return new String[]{hm.length > 0 ? hm[0] : "12", hm.length > 1 ? hm[1] : "0", period};
    }

    private static int parseInt(String value, int fallback) {
        try { return Integer.parseInt(value); } catch (Exception ex) { return fallback; }
    }
    private static boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}
