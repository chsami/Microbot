package net.runelite.client.plugins.microbot.pluginscheduler.condition.time;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;

import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Condition that is met when the current time is within a specific daily time window.
 * This can be used to restrict script execution to certain hours of the day.
 */
@Getter
@Slf4j
public class TimeWindowCondition implements Condition {
    private final LocalTime startTime;
    private final LocalTime endTime;
    private boolean registered = false;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");
    
    /**
     * Creates a time window condition that is met when the current time falls 
     * between the specified start and end times.
     * 
     * @param startHour Starting hour (0-23)
     * @param startMinute Starting minute (0-59)
     * @param endHour Ending hour (0-23)
     * @param endMinute Ending minute (0-59)
     */
    public TimeWindowCondition(int startHour, int startMinute, int endHour, int endMinute) {
        this.startTime = LocalTime.of(startHour, startMinute);
        this.endTime = LocalTime.of(endHour, endMinute);
    }

    
    
    /**
     * Creates a time window condition using LocalTime objects
     */
    public TimeWindowCondition(LocalTime startTime, LocalTime endTime) {
        this.startTime = startTime;
        this.endTime = endTime;
    }
    
    /**
     * Creates a time window condition for a specific hour range
     */
    public static TimeWindowCondition forHours(int startHour, int endHour) {
        return new TimeWindowCondition(startHour, 0, endHour, 0);
    }
    
    /**
     * Creates a time window for "day time" (8AM to 8PM)
     */
    public static TimeWindowCondition dayTimeHours() {
        return forHours(8, 20);
    }
    
    /**
     * Creates a time window for "night time" (8PM to 8AM)
     */
    public static TimeWindowCondition nightTimeHours() {
        return new TimeWindowCondition(20, 0, 8, 0);
    }
    /**
     * Creates a time window from ZonedDateTime objects, extracting just the time components.
     * 
     * @param start The start time
     * @param end The end time
     * @return A new TimeWindowCondition
     */
    public static TimeWindowCondition fromZonedDateTime(ZonedDateTime start, ZonedDateTime end) {
        LocalTime startTime = start.toLocalTime();
        LocalTime endTime = end.toLocalTime();
        return new TimeWindowCondition(startTime, endTime);
    }

    /**
     * Creates a randomized time window within specified boundaries.
     * 
     * @param earliestStart Earliest possible start hour (0-23)
     * @param latestStart Latest possible start hour (0-23)
     * @param minDurationHours Minimum duration in hours
     * @param maxDurationHours Maximum duration in hours
     * @return A randomized time window
     */
    public static TimeWindowCondition createRandomized(
            int earliestStart, int latestStart, 
            int minDurationHours, int maxDurationHours) {
        
        // Generate random start hour
        int range = (latestStart - earliestStart) + 1;
        int startHour = earliestStart + (int)(Math.random() * range);
        
        // Generate random duration
        int durationRange = (maxDurationHours - minDurationHours) + 1;
        int durationHours = minDurationHours + (int)(Math.random() * durationRange);
        
        // Calculate end hour (handle overflow to next day)
        int endHour = (startHour + durationHours) % 24;
        
        return new TimeWindowCondition(
                startHour, 0,  // Start at the beginning of the hour
                endHour, 0     // End at the beginning of the hour
        );
    }

    /**
     * Gets the total duration of this time window.
     * Handles cases where the window crosses midnight.
     * 
     * @return The duration of the time window
     */
    public Duration getTotalDuration() {
        if (startTime.isAfter(endTime)) {
            // Window crosses midnight
            Duration toMidnight = Duration.between(startTime, LocalTime.MAX);
            Duration fromMidnight = Duration.between(LocalTime.MIN, endTime);
            return toMidnight.plus(fromMidnight).plusNanos(1); // Add 1 nano for midnight itself
        } else {
            return Duration.between(startTime, endTime);
        }
    }
    @Override
    public boolean isMet() {
        // Register for events if not already registered
        if (!registered) {
            Microbot.getEventBus().register(this);
            registered = true;
        }
        
        LocalTime now = LocalTime.now();
        boolean result;
        
        // Handle case where time window crosses midnight
        if (startTime.isAfter(endTime)) {
            result = !now.isAfter(endTime) || !now.isBefore(startTime);
        } else {
            result = !now.isBefore(startTime) && !now.isAfter(endTime);
        }
        
        if (result) {
            log.debug("Time window condition met: current time {} is within window {}-{}", 
                    now.format(TIME_FORMATTER), 
                    startTime.format(TIME_FORMATTER), 
                    endTime.format(TIME_FORMATTER));
        }
        
        return result;
    }
    /**
     * Gets the calculated end time for the current or next active window.
     * If currently in an active window, returns the end time today.
     * If not in active window, returns the start time of the next active window.
     * 
     * @return The end time of the current window or start of next window
     */
    public ZonedDateTime getEndTime() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        LocalTime currentTime = now.toLocalTime();
        
        // If window crosses midnight
        if (startTime.isAfter(endTime)) {
            // If we're in the window (after start or before end)
            if (!currentTime.isBefore(startTime) || !currentTime.isAfter(endTime)) {
                // If we're after start time, end time is end time today
                if (!currentTime.isBefore(startTime)) {
                    return now.with(endTime).plusDays(1);
                }
                // If we're before end time, end time is end time today
                return now.with(endTime);
            } else {
                // We're not in the window, next window starts at start time today
                if (currentTime.isBefore(startTime)) {
                    return now.with(startTime);
                }
                // If we're after end time, next window starts at start time tomorrow
                return now.with(startTime).plusDays(1);
            }
        } else {
            // Normal window (doesn't cross midnight)
            if (!currentTime.isBefore(startTime) && !currentTime.isAfter(endTime)) {
                // We're in the window, end time is end time today
                return now.with(endTime);
            } else {
                // We're not in the window
                if (currentTime.isBefore(startTime)) {
                    // Window starts later today
                    return now.with(startTime);
                } else {
                    // Window starts tomorrow
                    return now.with(startTime).plusDays(1);
                }
            }
        }
    }
    @Subscribe
    public void onGameTick(GameTick event) {
        // This ensures we check the condition on every game tick
    }
    
    @Override
    public void reset() {
        // Nothing to reset for this condition
    }
    
    @Override
    public String getDescription() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        LocalTime currentTime = now.toLocalTime();
        boolean isActive = isMet();
        
        StringBuilder sb = new StringBuilder(String.format("Run during time window: %s - %s", 
                startTime.format(TIME_FORMATTER), 
                endTime.format(TIME_FORMATTER)));
        
        // Add progress information
        if (isActive) {
            // We're in the active window, calculate progress
            Duration windowDuration = getTotalDuration();
            
            // Calculate time elapsed in the current window
            Duration elapsed;
            if (startTime.isAfter(endTime)) {
                // Window crosses midnight
                if (currentTime.isBefore(endTime)) {
                    // In the part after midnight
                    elapsed = Duration.between(startTime, LocalTime.MAX).plus(
                            Duration.between(LocalTime.MIN, currentTime));
                } else {
                    // In the part before midnight
                    elapsed = Duration.between(startTime, currentTime);
                }
            } else {
                // Normal window
                elapsed = Duration.between(startTime, currentTime);
            }
            
            // Calculate progress percentage
            double progressPercent = 100.0 * elapsed.toSeconds() / (double) windowDuration.toSeconds();
            progressPercent = Math.min(100.0, Math.max(0.0, progressPercent));
            
            sb.append(String.format(", progress: %.1f%%", progressPercent));
            sb.append(" (active now)");
        } else {
            // Not active, calculate time until next window
            ZonedDateTime nextStart = getEndTime(); // In this class, getEndTime() returns next start if not active
            Duration timeUntilNext = Duration.between(now, nextStart);
            
            long hoursUntil = timeUntilNext.toHours();
            long minutesUntil = timeUntilNext.toMinutesPart();
            
            if (hoursUntil > 0) {
                sb.append(String.format(", starts in %dh %dm", hoursUntil, minutesUntil));
            } else {
                sb.append(String.format(", starts in %dm", minutesUntil));
            }
        }
        
        return sb.toString();
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.TIME;
    }
    
    @Override
    public void unregisterEvents() {
        if (registered) {
            try {
                Microbot.getEventBus().unregister(this);
            } catch (Exception e) {
                log.debug("Error unregistering time window condition", e);
            }
            registered = false;
        }
    }
}