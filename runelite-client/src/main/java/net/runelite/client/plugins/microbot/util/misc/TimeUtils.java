package net.runelite.client.plugins.microbot.util.misc;

import java.time.Duration;
import java.time.Instant;

public class TimeUtils {
    /**
     * Get formatted duration between two instants
     *
     * @param start
     * @param finish
     * @return duration as string formatted to d:mm:ss
     */
    public static String getFormattedDurationBetween(Instant start, Instant finish) {
        Duration duration = Duration.between(start, finish);

        // Check if the duration is negative
        boolean isNegative = duration.isNegative();

        // Use the absolute value of the duration for formatting
        duration = duration.abs();

        // Format the time components
        String formattedDuration = String.format("%d:%02d:%02d",
                duration.toHours(),
                duration.toMinutesPart(),
                duration.toSecondsPart());

        // Prefix with "-" if the original duration was negative
        return (isNegative ? "-" : "") + formattedDuration;
    }

    public static long getDurationInSeconds(Instant scriptStartTime, Instant now) {
        Duration duration = Duration.between(scriptStartTime, now);
        return duration.getSeconds();
    }
}