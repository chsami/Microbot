package net.runelite.client.plugins.microbot.scriptscheduler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.scriptscheduler.type.ScheduleType;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class ScheduledScript {
    private transient Plugin plugin;
    private String scriptName;
    private ScheduleType scheduleType;
    private int intervalValue; // The numeric value for the interval
    private String duration; // Optional duration to run the script
    private boolean enabled;
    private long lastRunTime; // When the script last ran (epoch millis)
    private long nextRunTime; // When the script should next run (epoch millis)
    private String cleanName;

    // Static formatter for time display
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
    private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public ScheduledScript(String scriptName, ScheduleType scheduleType, int intervalValue,
                           String duration, boolean enabled) {
        this.scriptName = scriptName;
        this.scheduleType = scheduleType != null ? scheduleType : ScheduleType.HOURS;
        this.intervalValue = Math.max(1, intervalValue); // Ensure interval is at least 1
        this.duration = duration;
        this.enabled = enabled;
        this.cleanName = scriptName.replaceAll("<html>|</html>", "")
                .replaceAll("<[^>]*>([^<]*)</[^>]*>", "$1")
                .replaceAll("<[^>]*>", "");

        // Set nextRunTime to now by default (run immediately)
        this.nextRunTime = roundToMinutes(System.currentTimeMillis());
    }

    public Plugin getPlugin() {
        if (plugin == null) {
            plugin = Microbot.getPluginManager().getPlugins().stream()
                    .filter(p -> Objects.equals(p.getName(), scriptName))
                    .findFirst()
                    .orElse(null);
        }
        return plugin;
    }

    public boolean start() {
        if (getPlugin() == null) {
            return false;
        }

        try {
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Microbot.startPlugin(plugin);
                return false;
            });

            // Update the last run time and calculate next run
            updateAfterRun();

            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean stop() {
        if (getPlugin() == null) {
            return false;
        }

        try {
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Microbot.stopPlugin(plugin);
                return false;
            });
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    public boolean isRunning() {
        return getPlugin() != null && Microbot.getPluginManager().isPluginEnabled(plugin);
    }

    /**
     * Round time to nearest minute (remove seconds and milliseconds)
     */
    private long roundToMinutes(long timeMillis) {
        return (timeMillis / 60000) * 60000;
    }

    /**
     * Set when this script should next run
     */
    public void setNextRunTime(long timeMillis) {
        this.nextRunTime = roundToMinutes(timeMillis);
    }

    /**
     * Check if the script is due to run
     */
    public boolean isDueToRun(long currentTimeMillis) {
        currentTimeMillis = roundToMinutes(currentTimeMillis);

        if (!enabled) {
            return false;
        }

        return currentTimeMillis >= nextRunTime;
    }

    /**
     * Update the lastRunTime to now and calculate the next run time
     */
    public void updateAfterRun() {
        lastRunTime = roundToMinutes(System.currentTimeMillis());

        // Calculate next run time based on schedule type and interval
        switch (scheduleType) {
            case MINUTES:
                nextRunTime = lastRunTime + TimeUnit.MINUTES.toMillis(intervalValue);
                break;
            case HOURS:
                nextRunTime = lastRunTime + TimeUnit.HOURS.toMillis(intervalValue);
                break;
            case DAYS:
                nextRunTime = lastRunTime + TimeUnit.DAYS.toMillis(intervalValue);
                break;
            default:
                nextRunTime = lastRunTime + TimeUnit.HOURS.toMillis(1); // Default fallback
        }
    }

    /**
     * Get a formatted display of the interval
     */
    public String getIntervalDisplay() {
        return "Every " + intervalValue + " " + scheduleType.toString().toLowerCase();
    }

    /**
     * Get a formatted display of when this script will run next
     */
    public String getNextRunDisplay(long currentTimeMillis) {
        currentTimeMillis = roundToMinutes(currentTimeMillis);

        if (!enabled) {
            return "Disabled";
        }

        if (isRunning()) {
            return "Running";
        }

        if (currentTimeMillis >= nextRunTime) {
            return "Ready to run";
        }

        long timeUntilMillis = nextRunTime - currentTimeMillis;
        long hours = TimeUnit.MILLISECONDS.toHours(timeUntilMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(timeUntilMillis) % 60;

        if (hours > 0) {
            return String.format("In %dh %dm", hours, minutes);
        } else {
            return String.format("In %dm", minutes);
        }
    }

    /**
     * Get a formatted time string for the next run
     */
    public String getNextRunTimeString() {
        return TIME_FORMAT.format(new Date(nextRunTime));
    }

    /**
     * Get the duration in minutes
     */
    public long getDurationMinutes() {
        if (duration == null || duration.isEmpty()) {
            return 0;
        }

        try {
            String[] parts = duration.split(":");
            if (parts.length == 2) {
                int hours = Integer.parseInt(parts[0]);
                int minutes = Integer.parseInt(parts[1]);
                return hours * 60L + minutes;
            }
        } catch (Exception e) {
            // Fall through to return 0
        }
        return 0;
    }

    /**
     * Convert a list of ScheduledScript objects to JSON
     */
    public static String toJson(List<ScheduledScript> scripts) {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(scripts);
    }

    /**
     * Parse JSON into a list of ScheduledScript objects
     */
    public static List<ScheduledScript> fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            Gson gson = new GsonBuilder().create();
            Type listType = new TypeToken<ArrayList<ScheduledScript>>() {
            }.getType();
            List<ScheduledScript> scripts = gson.fromJson(json, listType);

            // Fix any null scheduleType values
            for (ScheduledScript script : scripts) {
                if (script.getScheduleType() == null) {
                    script.scheduleType = ScheduleType.HOURS; // Default to HOURS
                }

                // Ensure times are rounded to minutes
                script.lastRunTime = script.roundToMinutes(script.lastRunTime);
                script.nextRunTime = script.roundToMinutes(script.nextRunTime);
            }

            return scripts;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ScheduledScript that = (ScheduledScript) o;
        return Objects.equals(scriptName, that.scriptName) &&
                Objects.equals(scheduleType, that.scheduleType) &&
                intervalValue == that.intervalValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(scriptName, scheduleType, intervalValue);
    }
}
