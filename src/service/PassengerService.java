package service;

import model.Driver;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Passenger queries read the same live MySQL departure rows as PHP. */
public class PassengerService {
    public List<Driver> getAvailableDrivers(String direction) {
        String[] p = parseDirection(direction);
        if (p == null) return Collections.emptyList();
        return DriverService.getActiveDrivers(p[0], p[1], p[2]);
    }

    public String getProgress(String direction, String stop) {
        String[] p = parseDirection(direction);
        if (p == null) return "Unknown";
        List<String> stops = directionStops(p[0], p[1]);
        int i = index(stops, stop);
        return i < 0 ? "Unknown" : (i + 1) + "/" + stops.size();
    }

    public String getPassedStops(String direction, String stop) {
        String[] p = parseDirection(direction);
        if (p == null) return "None";
        List<String> stops = directionStops(p[0], p[1]);
        int i = index(stops, stop);
        return i <= 0 ? "None" : String.join(", ", stops.subList(0, i));
    }

    public String getNextStop(String direction, String stop) {
        String[] p = parseDirection(direction);
        if (p == null) return "Destination reached";
        List<String> stops = directionStops(p[0], p[1]);
        int i = index(stops, stop);
        return (i >= 0 && i + 1 < stops.size()) ? stops.get(i + 1) : "Destination reached";
    }

    public String getStatus(String direction, String current, String target) {
        String[] p = parseDirection(direction);
        if (p == null) return "Unknown";
        List<String> stops = directionStops(p[0], p[1]);
        int ci = index(stops, current), ti = index(stops, target);
        if (ci < 0 || ti < 0) return "Unknown";
        if (ci == ti) return "AT YOUR STOP";
        return ci < ti ? "APPROACHING" : "ALREADY PASSED";
    }

    private List<String> directionStops(String route, String from) {
        List<String> stops = new ArrayList<>(RouteService.getStops(route));
        if (!"Lumbia".equalsIgnoreCase(from)) Collections.reverse(stops);
        return stops;
    }

    private int index(List<String> list, String value) {
        if (value == null) return -1;
        for (int i=0;i<list.size();i++) if (list.get(i).equalsIgnoreCase(value.trim())) return i;
        return -1;
    }

    /** Format: R1:Lumbia:Cogon or R2:Carmen:Lumbia. */
    private String[] parseDirection(String direction) {
        if (direction == null) return null;
        String[] p = direction.split(":", -1);
        if (p.length != 3) return null;
        if (!("R1".equals(p[0]) || "R2".equals(p[0]))) return null;
        return p;
    }
}
