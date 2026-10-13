package service;

import model.Driver;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

/**
 * Offline Java-only storage used only when the MySQL/XAMPP database is unavailable.
 * When MySQL is online, the application uses the database as its source of truth.
 */
public final class LocalDataStore {
    private static final File DATA_DIR = new File(
            System.getProperty("user.home"), "TranSeatManagementSystem");
    private static final File DATA_FILE = new File(DATA_DIR, "drivers.dat");
    private static final File TEMP_FILE = new File(DATA_DIR, "drivers.tmp");

    private LocalDataStore() { }

    public static synchronized List<Driver> loadDrivers() {
        if (!DATA_FILE.exists()) return new ArrayList<Driver>();
        try (ObjectInputStream in = new ObjectInputStream(new FileInputStream(DATA_FILE))) {
            Object object = in.readObject();
            if (object instanceof DriverData) {
                return new ArrayList<Driver>(((DriverData) object).drivers);
            }
        } catch (Exception ignored) { }
        return new ArrayList<Driver>();
    }

    public static synchronized void saveDrivers(List<Driver> drivers) {
        try {
            if (!DATA_DIR.exists() && !DATA_DIR.mkdirs()) return;
            DriverData data = new DriverData(drivers);
            try (ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(TEMP_FILE))) {
                out.writeObject(data);
                out.flush();
            }
            try {
                Files.move(TEMP_FILE.toPath(), DATA_FILE.toPath(),
                        StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (Exception atomicMoveNotSupported) {
                Files.move(TEMP_FILE.toPath(), DATA_FILE.toPath(),
                        StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception ignored) { }
    }

    public static synchronized Driver findById(String id) {
        if (id == null) return null;
        for (Driver d : loadDrivers()) {
            if (id.trim().equalsIgnoreCase(d.getId()) && !d.isArchived()) return d;
        }
        return null;
    }

    public static synchronized Driver findByName(String name) {
        if (name == null) return null;
        for (Driver d : loadDrivers()) {
            if (name.trim().equalsIgnoreCase(d.getName()) && !d.isArchived()) return d;
        }
        return null;
    }

    public static synchronized List<Driver> getActiveDrivers() {
        List<Driver> result = new ArrayList<Driver>();
        for (Driver d : loadDrivers()) {
            if (!d.isArchived()) result.add(d);
        }
        return result;
    }

    public static synchronized boolean addDriver(String id, String name, String plate) {
        if (blank(id) || blank(name) || blank(plate)) return false;
        List<Driver> drivers = loadDrivers();
        for (Driver d : drivers) {
            if (id.trim().equalsIgnoreCase(d.getId()) ||
                name.trim().equalsIgnoreCase(d.getName())) return false;
        }
        drivers.add(new Driver(id.trim(), name.trim(), plate.trim()));
        saveDrivers(drivers);
        return true;
    }

    public static synchronized boolean updateDriver(String id, String name, String plate) {
        if (blank(id) || blank(name) || blank(plate)) return false;
        List<Driver> drivers = loadDrivers();
        for (Driver d : drivers) {
            if (!id.trim().equalsIgnoreCase(d.getId()) &&
                name.trim().equalsIgnoreCase(d.getName()) && !d.isArchived()) return false;
        }
        for (Driver d : drivers) {
            if (id.trim().equalsIgnoreCase(d.getId()) && !d.isArchived()) {
                d.setName(name.trim());
                d.setPlateNumber(plate.trim());
                saveDrivers(drivers);
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean archiveDriver(String id) {
        if (blank(id)) return false;
        List<Driver> drivers = loadDrivers();
        for (Driver d : drivers) {
            if (id.trim().equalsIgnoreCase(d.getId()) && !d.isArchived()) {
                d.setArchived(true);
                saveDrivers(drivers);
                return true;
            }
        }
        return false;
    }

    public static synchronized void replaceDriver(Driver updated) {
        if (updated == null) return;
        List<Driver> drivers = loadDrivers();
        for (int i = 0; i < drivers.size(); i++) {
            if (updated.getId().equalsIgnoreCase(drivers.get(i).getId())) {
                drivers.set(i, updated);
                saveDrivers(drivers);
                return;
            }
        }
        drivers.add(updated);
        saveDrivers(drivers);
    }

    private static boolean blank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static class DriverData implements Serializable {
        private static final long serialVersionUID = 2L;
        private final List<Driver> drivers;
        DriverData(List<Driver> drivers) {
            this.drivers = new ArrayList<Driver>(drivers);
        }
    }
}
