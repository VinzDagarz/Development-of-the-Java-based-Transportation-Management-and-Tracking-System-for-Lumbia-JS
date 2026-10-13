package service;

import java.text.SimpleDateFormat;
import java.util.Date;
import javax.swing.JLabel;
import javax.swing.Timer;

/**
 * Live 12-hour clock for the Swing dashboards.
 * The AM/PM marker automatically changes with the system time.
 */
public final class LiveClock {
    private LiveClock() { }

    public static Timer attach(final JLabel label) {
        final SimpleDateFormat format = new SimpleDateFormat("hh:mm:ss a");
        Timer timer = new Timer(1000, e -> label.setText(format.format(new Date())));
        label.setText(format.format(new Date()));
        timer.setRepeats(true);
        timer.start();
        return timer;
    }

    public static String currentHour() {
        return new SimpleDateFormat("h").format(new Date());
    }

    public static String currentMinute() {
        return new SimpleDateFormat("mm").format(new Date());
    }

    public static String currentPeriod() {
        return new SimpleDateFormat("a").format(new Date());
    }
}
