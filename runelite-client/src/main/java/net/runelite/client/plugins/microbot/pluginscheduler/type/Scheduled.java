package net.runelite.client.plugins.microbot.pluginscheduler.type;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.event.ScheduledStopEvent;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class Scheduled {
    private transient Plugin plugin;
    private String name;
    private ScheduleType scheduleType;
    private int intervalValue; // The numeric value for the interval
    private String duration; // Optional duration to run the plugin
    private boolean enabled;
    private long lastRunTime; // When the plugin last ran (epoch millis)
    private long nextRunTime; // When the plugin should next run (epoch millis)
    private String cleanName;

    // Static formatter for time display
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    private static final SimpleDateFormat TIME_FORMAT = new SimpleDateFormat("HH:mm");
    private static final SimpleDateFormat DATE_TIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    // Time restriction fields
    private boolean timeRestrictionEnabled;
    private int startHour; // 0-23
    private int endHour; // 0-23

    public Scheduled(String pluginName, ScheduleType scheduleType, int intervalValue,
                     String duration, boolean enabled) {
        this.name = pluginName;
        this.scheduleType = scheduleType != null ? scheduleType : ScheduleType.HOURS;
        this.intervalValue = Math.max(1, intervalValue); // Ensure interval is at least 1
        this.duration = duration;
        this.enabled = enabled;
        this.cleanName = pluginName.replaceAll("<html>|</html>", "")
                .replaceAll("<[^>]*>([^<]*)</[^>]*>", "$1")
                .replaceAll("<[^>]*>", "");
        this.timeRestrictionEnabled = false;
        this.startHour = 0;
        this.endHour = 23;
        setNextRunTime(roundToMinutes(System.currentTimeMillis()));
    }

    public Plugin getPlugin() {
        PluginManager pluginManager = Microbot.getPluginManager();
        if (pluginManager != null && plugin == null) {
            plugin = pluginManager.getPlugins().stream()
                    .filter(p -> Objects.equals(p.getName(), name))
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

    public void stop() {
        if (getPlugin() == null) {
            return;
        }

        try {
            Microbot.getClientThread().runOnSeperateThread(() -> {
                Microbot.getEventBus().post(new ScheduledStopEvent(plugin));
                return false;
            });

        } catch (Exception ignored) {
        }
    }

    public boolean forceStop() {
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
     * Round time to nearest minute (rounding up at 30+ seconds)
     */
    private long roundToMinutes(long timeMillis) {
        return ((timeMillis + 30000) / 60000) * 60000;
    }

    /**
     * Set when this plugin should next run
     */
    public void setNextRunTime(long timeMillis) {
        // First round the time to minutes
        long roundedTime = roundToMinutes(timeMillis);

        // If time restriction is enabled, check if we need to adjust the time
        if (timeRestrictionEnabled) {
            // Get current hour (0-23)
            Calendar cal = Calendar.getInstance();
            cal.setTimeInMillis(roundedTime);
            int currentHour = cal.get(Calendar.HOUR_OF_DAY);

            // If outside allowed hours, adjust to next start hour
            if (!isHourWithinAllowedHours(currentHour)) {
                // Reset minutes and seconds
                cal.set(Calendar.MINUTE, Rs2Random.between(0, 5));
                cal.set(Calendar.SECOND, 0);

                // Set to the next available start hour
                if (startHour > currentHour) {
                    // Start hour is later today
                    cal.set(Calendar.HOUR_OF_DAY, startHour);
                } else {
                    // Start hour is tomorrow
                    cal.add(Calendar.DAY_OF_MONTH, 1);
                    cal.set(Calendar.HOUR_OF_DAY, startHour);
                }

                roundedTime = cal.getTimeInMillis();
            }
        }

        // Set the next run time
        this.nextRunTime = roundedTime;
    }

    /**
     * Check if the plugin is due to run, considering time restrictions
     */
    public boolean isDueToRun(long currentTimeMillis) {
        currentTimeMillis = roundToMinutes(currentTimeMillis);

        if (!enabled) {
            return false;
        }

        // Check if current time is within allowed hours
        if (timeRestrictionEnabled && !isWithinAllowedHours()) {
            setNextRunTime(currentTimeMillis);
            return false;
        }

        return currentTimeMillis >= nextRunTime;
    }

    /**
     * Check if the current hour is within the allowed time range for this plugin
     */
    private boolean isWithinAllowedHours() {
        Calendar calendar = Calendar.getInstance();
        int currentHour = calendar.get(Calendar.HOUR_OF_DAY);

        return isHourWithinAllowedHours(currentHour);
    }

    private boolean isHourWithinAllowedHours(int hour) {
        if (startHour <= endHour) {
            // Simple case: start time is before end time (e.g., 8am-8pm)
            return hour >= startHour && hour < endHour;
        } else {
            // Complex case: start time is after end time (e.g., 8pm-8am)
            // This means the allowed time spans midnight
            return hour >= startHour || hour < endHour;
        }
    }

    /**
     * Get a formatted display of the time restriction
     */
    public String getTimeRestrictionDisplay() {
        if (!timeRestrictionEnabled) {
            return "No time restriction";
        }

        return String.format("%02d:00-%02d:00", startHour, endHour);
    }

    /**
     * Update the lastRunTime to now and calculate the next run time
     */
    private void updateAfterRun() {
        lastRunTime = roundToMinutes(System.currentTimeMillis());
        long _nextRunTime;

        // Calculate next run time based on schedule type and interval
        switch (scheduleType) {
            case MINUTES:
                _nextRunTime = lastRunTime + TimeUnit.MINUTES.toMillis(intervalValue);
                break;
            case HOURS:
                _nextRunTime = lastRunTime + TimeUnit.HOURS.toMillis(intervalValue);
                break;
            case DAYS:
                _nextRunTime = lastRunTime + TimeUnit.DAYS.toMillis(intervalValue);
                break;
            default:
                _nextRunTime = lastRunTime + TimeUnit.HOURS.toMillis(1); // Default fallback
        }

        setNextRunTime(_nextRunTime + TimeUnit.MINUTES.toMillis(Rs2Random.between(0, 5)));
    }

    /**
     * Get a formatted display of the interval
     */
    public String getIntervalDisplay() {
        return "Every " + intervalValue + " " + scheduleType.toString().toLowerCase();
    }

    /**
     * Get a formatted display of when this plugin will run next
     */
    public String getNextRunDisplay() {
        long currentTimeMillis = roundToMinutes(System.currentTimeMillis());

        if (isRunning()) {
            return "Running";
        }

        if (!enabled) {
            return "Disabled";
        }

        if (currentTimeMillis >= nextRunTime) {
            return "After current plugin";
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
     * Convert a list of ScheduledPlugin objects to JSON
     */
    public static String toJson(List<Scheduled> plugins) {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(plugins);
    }

    /**
     * Parse JSON into a list of ScheduledPlugin objects
     */
    public static List<Scheduled> fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }

        try {
            Gson gson = new GsonBuilder().create();
            Type listType = new TypeToken<ArrayList<Scheduled>>() {
            }.getType();
            List<Scheduled> plugins = gson.fromJson(json, listType);

            // Fix any null scheduleType values
            for (Scheduled plugin : plugins) {
                if (plugin.getScheduleType() == null) {
                    plugin.scheduleType = ScheduleType.HOURS; // Default to HOURS
                }

                // Ensure times are rounded to minutes
                plugin.lastRunTime = plugin.roundToMinutes(plugin.lastRunTime);
                plugin.nextRunTime = plugin.roundToMinutes(plugin.nextRunTime);
            }

            return plugins;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Scheduled that = (Scheduled) o;
        return Objects.equals(name, that.name) &&
                Objects.equals(scheduleType, that.scheduleType) &&
                intervalValue == that.intervalValue;
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, scheduleType, intervalValue);
    }
}
