package net.runelite.client.plugins.microbot.scriptscheduler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import net.runelite.client.plugins.microbot.scriptscheduler.type.ScheduleType;

import java.lang.reflect.Type;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Getter
public class ScheduledScript implements Comparable<ScheduledScript> {
    private String scriptName;
    private ScheduleType scheduleType;
    private int intervalValue; // The numeric value for the interval
    private String duration; // Optional duration to run the script
    private boolean enabled;
    private LocalDateTime lastRunTime; // Track when the script last ran

    // Static formatter for time display
    public static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    public ScheduledScript(String scriptName, ScheduleType scheduleType, int intervalValue,
                           String duration, boolean enabled) {
        this.scriptName = scriptName;
        this.scheduleType = scheduleType != null ? scheduleType : ScheduleType.HOURS;
        this.intervalValue = Math.max(1, intervalValue); // Ensure interval is at least 1
        this.duration = duration;
        this.enabled = enabled;
        this.lastRunTime = LocalDateTime.now(); // Set lastRunTime to now to prevent immediate execution
    }


    public void setEnabled(boolean enabled) { this.enabled = enabled; }
    public void setLastRunTime(LocalDateTime lastRunTime) { this.lastRunTime = lastRunTime; }

    /**
     * Calculate the next time this script should run
     */
    /**
     * Calculate the next time this script should run
     */
    public LocalDateTime getNextRunTime(LocalDateTime now) {
        if (!enabled || lastRunTime == null) {
            return now.plusHours(1); // Default to 1 hour from now if disabled or no last run time
        }

        // Handle null scheduleType
        if (scheduleType == null) {
            return lastRunTime.plusHours(intervalValue);
        }

        // Calculate next run time based on schedule type and interval
        switch (scheduleType) {
            case MINUTES:
                return lastRunTime.plusMinutes(intervalValue);
            case HOURS:
                return lastRunTime.plusHours(intervalValue);
            case DAYS:
                return lastRunTime.plusDays(intervalValue);
            default:
                return lastRunTime.plusHours(1); // Default fallback
        }
    }

    /**
     * Check if the script is due to run
     */
    public boolean isDueToRun(LocalDateTime now) {
        if (!enabled) {
            return false;
        }

        if (lastRunTime == null) {
            // If lastRunTime is null, set it to now and return false to prevent immediate execution
            lastRunTime = now;
            return false;
        }

        LocalDateTime nextRun = getNextRunTime(now);
        return !now.isBefore(nextRun);
    }


    /**
     * Get a formatted display of the interval
     */
    public String getIntervalDisplay() {
        if (scheduleType == null) {
            return "Every " + intervalValue + " hours"; // Default to hours if scheduleType is null
        }
        return "Every " + intervalValue + " " + scheduleType.toString().toLowerCase();
    }

    /**
     * Get a formatted display of when this script will run next
     */
    public String getNextRunDisplay(LocalDateTime now) {
        if (!enabled) {
            return "Disabled";
        }

        if (lastRunTime == null) {
            return "Ready to run";
        }

        LocalDateTime nextRun = getNextRunTime(now);
        Duration timeUntil = Duration.between(now, nextRun);

        if (timeUntil.isNegative() || timeUntil.isZero()) {
            return "Ready to run";
        }

        long hours = timeUntil.toHours();
        long minutes = timeUntil.toMinutesPart();

        if (hours > 0) {
            return String.format("In %dh %dm", hours, minutes);
        } else {
            return String.format("In %dm", minutes);
        }
    }

    /**
     * Get the duration in minutes
     */
    public long getDurationMinutes() {
        if (duration == null || duration.isEmpty()) {
            return 0;
        }

        try {
            LocalTime durationTime = LocalTime.parse(duration, TIME_FORMATTER);
            return durationTime.getHour() * 60L + durationTime.getMinute();
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * Get the start time for hourly schedules
     */
    public String getStartTime() {
        if (lastRunTime != null) {
            return lastRunTime.format(TIME_FORMATTER);
        }
        return "--:--";
    }

    /**
     * Convert a list of ScheduledScript objects to JSON
     */
    public static String toJson(List<ScheduledScript> scripts) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                .create();
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
            Gson gson = new GsonBuilder()
                    .registerTypeAdapter(LocalDateTime.class, new LocalDateTimeAdapter())
                    .create();
            Type listType = new TypeToken<ArrayList<ScheduledScript>>(){}.getType();
            List<ScheduledScript> scripts = gson.fromJson(json, listType);

            // Fix any null scheduleType values
            for (ScheduledScript script : scripts) {
                if (script.getScheduleType() == null) {
                    script.scheduleType = ScheduleType.HOURS; // Default to HOURS
                }
            }

            return scripts;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }


    /**
     * Adapter class to handle serialization/deserialization of LocalDateTime
     */
    private static class LocalDateTimeAdapter implements com.google.gson.JsonSerializer<LocalDateTime>,
            com.google.gson.JsonDeserializer<LocalDateTime> {
        private static final DateTimeFormatter formatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

        @Override
        public com.google.gson.JsonElement serialize(LocalDateTime src,
                                                     java.lang.reflect.Type typeOfSrc,
                                                     com.google.gson.JsonSerializationContext context) {
            return src == null ? null : new com.google.gson.JsonPrimitive(formatter.format(src));
        }

        @Override
        public LocalDateTime deserialize(com.google.gson.JsonElement json,
                                         java.lang.reflect.Type typeOfT,
                                         com.google.gson.JsonDeserializationContext context)
                throws com.google.gson.JsonParseException {
            return json == null ? null : LocalDateTime.parse(json.getAsString(), formatter);
        }
    }

    @Override
    public int compareTo(ScheduledScript other) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime thisNext = this.getNextRunTime(now);
        LocalDateTime otherNext = other.getNextRunTime(now);

        // Handle null cases
        if (thisNext == null && otherNext == null) return 0;
        if (thisNext == null) return 1;
        if (otherNext == null) return -1;

        return thisNext.compareTo(otherNext);
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
