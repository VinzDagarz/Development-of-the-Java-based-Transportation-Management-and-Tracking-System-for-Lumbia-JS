package service;

public class AdminService {
    public boolean addDriver(String id, String name, String plate) {
        return DriverService.addDriver(id, name, plate);
    }
    public boolean updateDriver(String id, String name, String plate) {
        return DriverService.updateDriver(id, name, plate);
    }
    public boolean archiveDriver(String id) {
        return DriverService.archiveDriver(id);
    }
}
