package model;

import java.io.Serializable;

public class Driver implements Serializable {
    private static final long serialVersionUID = 2L;

    private String id;
    private String name;
    private String plateNumber;
    private boolean archived;
    private boolean departing;
    private String route;
    private String currentStop;
    private String departureTime;
    private int roundTrips;
    private double totalEarnings;
    private String fromLocation;
    private String toLocation;
    private String currentStopAt;
    private double roundTripEarnings;

    public Driver(String id, String name, String plateNumber) {
        this.id = id;
        this.name = name;
        this.plateNumber = plateNumber;
        this.archived = false;
        this.departing = false;
        this.route = "";
        this.currentStop = "";
        this.departureTime = "";
        this.roundTrips = 0;
        this.totalEarnings = 0.0;
        this.fromLocation = "";
        this.toLocation = "";
        this.currentStopAt = null;
        this.roundTripEarnings = 0.0;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getPlateNumber() { return plateNumber; }
    public boolean isArchived() { return archived; }
    public boolean isDeparting() { return departing; }
    public String getRoute() { return route; }
    public String getCurrentStop() { return currentStop; }
    public String getDepartureTime() { return departureTime; }
    public int getRoundTrips() { return roundTrips; }
    public double getTotalEarnings() { return totalEarnings; }
    public String getFromLocation() { return fromLocation; }
    public String getToLocation() { return toLocation; }
    public String getCurrentStopAt() { return currentStopAt; }
    public double getRoundTripEarnings() { return roundTripEarnings; }

    public void setId(String id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setPlateNumber(String plateNumber) { this.plateNumber = plateNumber; }

    public void setArchived(boolean archived) {
        this.archived = archived;
        if (archived) stopDeparture();
    }

    public void startDeparture(String route, String currentStop, String departureTime) {
        this.departing = true;
        this.route = route;
        this.currentStop = currentStop;
        this.departureTime = departureTime;
        this.roundTrips++;
    }

    public void setRoundTripsValue(int roundTrips) { this.roundTrips = roundTrips; }
    public void setTotalEarningsValue(double totalEarnings) { this.totalEarnings = totalEarnings; }
    public void setCurrentStopAt(String value) { this.currentStopAt = value; }
    public void setRoundTripEarningsValue(double value) { this.roundTripEarnings = value; }

    public void setRouteLocations(String from, String to) {
        this.fromLocation = from == null ? "" : from;
        this.toLocation = to == null ? "" : to;
    }

    public void stopDeparture() {
        this.departing = false;
        this.route = "";
        this.currentStop = "";
        this.departureTime = "";
    }

    public void setCurrentStop(String currentStop) {
        this.currentStop = currentStop;
    }

    public void addEarnings(double amount) {
        this.totalEarnings += amount;
    }

    public boolean deductEarnings(double amount) {
        if (amount < 0 || amount > totalEarnings) return false;
        totalEarnings -= amount;
        return true;
    }
}
