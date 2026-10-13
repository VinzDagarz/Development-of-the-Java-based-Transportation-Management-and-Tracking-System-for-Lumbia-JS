package service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Central route definitions used by both Driver and Passenger dashboards.
 * Keeping the stop lists in one place prevents the two screens from getting
 * out of sync.
 */
public final class RouteService {
    private static final List<String> R1_STOPS = Collections.unmodifiableList(Arrays.asList(
        "Centro Lumbia",
        "Palalan Road",
        "Masterson Ave.",
        "Kagay-an Bridge",
        "Rodelsa Circle",
        "Tomas Saco St.",
        "Fernandez St.",
        "Corrales Ave.",
        "Hayes St.",
        "Cogon Public Market"
    ));

    private static final List<String> R2_STOPS = Collections.unmodifiableList(Arrays.asList(
        "Centro Lumbia",
        "F. Delima St.",
        "Masterson Ave.",
        "Macapagal Drive",
        "SM Bypass Road",
        "C. Diel St.",
        "Villarin St.",
        "Max Suniel St.",
        "V. Neri St.",
        "Carmen Public Market"
    ));

    private RouteService() { }

    public static List<String> getStops(String route) {
        if ("R1".equalsIgnoreCase(route)) return R1_STOPS;
        if ("R2".equalsIgnoreCase(route)) return R2_STOPS;
        return Collections.emptyList();
    }

    public static int getStopIndex(String route, String stop) {
        List<String> stops = getStops(route);
        for (int i = 0; i < stops.size(); i++) {
            if (stops.get(i).equalsIgnoreCase(normalize(stop))) return i;
        }
        return -1;
    }

    public static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    public static String getProgress(String route, String currentStop) {
        List<String> stops = getStops(route);
        int index = getStopIndex(route, currentStop);
        if (index < 0 || stops.isEmpty()) return "Unknown";
        return (index + 1) + "/" + stops.size();
    }

    public static String getPassedStops(String route, String currentStop) {
        List<String> stops = getStops(route);
        int index = getStopIndex(route, currentStop);
        if (index <= 0) return "None";
        return String.join(", ", stops.subList(0, index));
    }

    public static String getNextStop(String route, String currentStop) {
        List<String> stops = getStops(route);
        int index = getStopIndex(route, currentStop);
        if (index < 0 || index + 1 >= stops.size()) return "Destination reached";
        return stops.get(index + 1);
    }
}
