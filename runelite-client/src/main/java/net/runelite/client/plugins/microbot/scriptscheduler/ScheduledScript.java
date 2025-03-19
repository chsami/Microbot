package net.runelite.client.plugins.microbot.scriptscheduler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.lang.reflect.Type;
import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ScheduledScript {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private String scriptName;
    private String startTime; // Format: "HH:mm"
    private String duration; // Format: "HH:mm", optional
    private ScheduleType scheduleType;
    private List<Integer> days = new ArrayList<>(); // 1 = Monday, 7 = Sunday
    private boolean enabled = true;

    public enum ScheduleType {
        HOURLY("Hourly"),
        DAILY("Daily"),
        WEEKDAYS("Weekdays"),
        WEEKENDS("Weekends"),
        CUSTOM("Custom");

        private final String displayName;

        ScheduleType(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    /**
     * Gets a formatted description of the schedule
     */
    public String getScheduleDescription() {
        if (scheduleType == null) {
            return "Unknown";
        }

        switch (scheduleType) {
            case HOURLY:
                return "Hourly";
            case DAILY:
                return "Daily";
            case WEEKDAYS:
                return "Weekdays";
            case WEEKENDS:
                return "Weekends";
            case CUSTOM:
                if (days == null || days.isEmpty()) {
                    return "Custom (No days selected)";
                }
                return days.stream()
                        .map(day -> {
                            switch (day) {
                                case 1: return "Mon";
                                case 2: return "Tue";
                                case 3: return "Wed";
                                case 4: return "Thu";
                                case 5: return "Fri";
                                case 6: return "Sat";
                                case 7: return "Sun";
                                default: return "";
                            }
                        })
                        .collect(Collectors.joining(", "));
            default:
                return "Unknown";
        }
    }

    /**
     * Checks if the given day is valid for this script's schedule
     */
    public boolean isCorrectDay(int day) {
        switch (scheduleType) {
            case HOURLY:
            case DAILY:
                return true;
            case WEEKDAYS:
                return day >= 1 && day <= 5;
            case WEEKENDS:
                return day == 6 || day == 7;
            case CUSTOM:
                return days.contains(day);
            default:
                return false;
        }
    }

    /**
     * Checks if this script should run at the current time
     */
    public boolean shouldRunNow(LocalDateTime now) {
        if (!enabled) {
            return false;
        }

        LocalTime currentTime = now.toLocalTime();
        int currentDay = now.getDayOfWeek().getValue();

        if (!isCorrectDay(currentDay)) {
            return false;
        }

        if (scheduleType == ScheduleType.HOURLY) {
            try {
                LocalTime scheduleTime = LocalTime.parse(startTime, TIME_FORMATTER);

                // For hourly schedules, we only care if the minute matches
                // We should run at the specified minute every hour
                return currentTime.getMinute() == scheduleTime.getMinute();
            } catch (Exception e) {
                return false;
            }
        } else {
            return currentTime.getHour() == LocalTime.parse(startTime, TIME_FORMATTER).getHour()
                    && currentTime.getMinute() == LocalTime.parse(startTime, TIME_FORMATTER).getMinute();
        }
    }

    /**
     * Calculates the next time this script will run
     */
    public LocalDateTime getNextRunTime(LocalDateTime now) {
        if (!enabled) {
            return null;
        }

        try {
            LocalTime scheduleTime = LocalTime.parse(startTime, TIME_FORMATTER);

            if (scheduleType == ScheduleType.HOURLY) {
                int scheduleMinute = scheduleTime.getMinute();
                int scheduleHour = scheduleTime.getHour();

                // Create a candidate time with the current hour and the scheduled minute
                LocalDateTime candidate = now.withMinute(scheduleMinute).withSecond(0).withNano(0);

                // If we haven't reached the start time yet today
                LocalDateTime startTimeToday = now.toLocalDate().atTime(scheduleHour, scheduleMinute);
                if (now.isBefore(startTimeToday)) {
                    // The first run will be at the start time
                    candidate = startTimeToday;
                } else if (candidate.isBefore(now)) {
                    // If the candidate time is in the past, move to the next hour
                    candidate = candidate.plusHours(1);
                }

                // Check if this day is valid for the schedule
                if (isCorrectDay(candidate.getDayOfWeek().getValue())) {
                    return candidate;
                } else {
                    // Find the next valid day
                    for (int i = 1; i <= 7; i++) {
                        candidate = candidate.plusDays(1).withHour(scheduleHour).withMinute(scheduleMinute);
                        if (isCorrectDay(candidate.getDayOfWeek().getValue())) {
                            return candidate;
                        }
                    }
                }
            } else {
                // For other schedule types, find the next occurrence of the time
                LocalDateTime candidate = now.withHour(scheduleTime.getHour())
                        .withMinute(scheduleTime.getMinute())
                        .withSecond(0)
                        .withNano(0);

                if (candidate.isBefore(now)) {
                    candidate = candidate.plusDays(1);
                }

                // Find the next valid day
                for (int i = 0; i < 7; i++) {
                    if (isCorrectDay(candidate.getDayOfWeek().getValue())) {
                        return candidate;
                    }
                    candidate = candidate.plusDays(1);
                }
            }
        } catch (Exception e) {
            // If we can't parse the time, return null
            return null;
        }

        return null;
    }

    /**
     * Gets the duration in minutes, or 0 if no duration is set
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

    public static String toJson(List<ScheduledScript> scripts) {
        Gson gson = new GsonBuilder().create();
        return gson.toJson(scripts);
    }

    public static List<ScheduledScript> fromJson(String json) {
        Gson gson = new GsonBuilder().create();
        Type type = new TypeToken<List<ScheduledScript>>(){}.getType();
        List<ScheduledScript> scripts = gson.fromJson(json, type);
        return scripts != null ? scripts : new ArrayList<>();
    }
}