package model;

/** Read-only view of a driver account currently marked as archived. */
public class Archive {
    private final String driverID;
    private final String driverName;
    private final String driverPlateNo;

    public Archive(String driverID, String driverName, String driverPlateNo) {
        this.driverID = driverID;
        this.driverName = driverName;
        this.driverPlateNo = driverPlateNo;
    }

    /** Convenience constructor retained for callers that use numeric IDs. */
    public Archive(int driverID, String driverName, String driverPlateNo) {
        this(String.valueOf(driverID), driverName, driverPlateNo);
    }

    public String getDriverID() { return driverID; }
    public String getDriverName() { return driverName; }
    public String getDriverPlateNo() { return driverPlateNo; }
}
