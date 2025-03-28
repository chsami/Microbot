package net.runelite.client.plugins.microbot.pluginscheduler.condition.time;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

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
public class TimeWindowCondition extends TimeCondition {
    private LocalTime currentStartTime;
    private final LocalTime startTimeMin;
    private final LocalTime startTimeMax;
    
    private LocalTime currentEndTime;
    private final LocalTime endTimeMin;
    private final LocalTime endTimeMax;
    
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
        startHour = Math.max(0, Math.min(23, startHour));
        startMinute = Math.max(0, Math.min(59, startMinute));
        endHour = Math.max(0, Math.min(23, endHour));
        endMinute = Math.max(0, Math.min(59, endMinute));
        this.startTimeMin = LocalTime.of(startHour, startMinute);
        this.startTimeMax = LocalTime.of(startHour, startMinute);
        this.endTimeMin = LocalTime.of(endHour, endMinute);
        this.endTimeMax = LocalTime.of(endHour, endMinute);
        this.currentStartTime = LocalTime.of(startHour, startMinute);
        this.currentEndTime = LocalTime.of(endHour, endMinute);
    }
    public TimeWindowCondition(int startHourMin, int startHourMax,
        int startMinuteMin, int startMinuteMax,
        int endHourMin, int endHourMax,
        int endMinuteMin, int endMinuteMax) {
        startHourMin = Math.max(0, Math.min(23, startHourMin));
        startHourMax = Math.max(0, Math.min(23, startHourMax));
        startMinuteMin = Math.max(0, Math.min(59, startMinuteMin));
        startMinuteMax = Math.max(0, Math.min(59, startMinuteMax));
        endHourMin = Math.max(0, Math.min(23, endHourMin));
        endHourMax = Math.max(0, Math.min(23, endHourMax));
        endMinuteMin = Math.max(0, Math.min(59, endMinuteMin));
        endMinuteMax = Math.max(0, Math.min(59, endMinuteMax));
        this.startTimeMin = LocalTime.of(startHourMin, startMinuteMin);
        this.startTimeMax = LocalTime.of(startHourMax, startMinuteMax);
        this.endTimeMin = LocalTime.of(endHourMin, endMinuteMin);
        this.endTimeMax = LocalTime.of(endHourMax, endMinuteMax);
        
        randomizeTimeWindow();
     
        

    }
    private void randomizeTimeWindow() {
        this.currentStartTime = LocalTime.of(   Rs2Random.between(startTimeMin.getHour(), startTimeMax.getHour()),
                                                Rs2Random.between(startTimeMin.getMinute(), startTimeMax.getMinute()));
        this.currentEndTime = LocalTime.of(     Rs2Random.between(endTimeMin.getHour(), endTimeMax.getHour()),
                                                Rs2Random.between(endTimeMin.getMinute(), endTimeMax.getMinute()));
    }

    
    
    /**
     * Creates a time window condition using LocalTime objects
     */
    public TimeWindowCondition(LocalTime startTime, LocalTime endTime) {
        this.startTimeMin = startTime;
        this.startTimeMax = startTime;
        this.endTimeMin = endTime;
        this.endTimeMax = endTime;
        this.currentStartTime = startTime;
        this.currentEndTime = endTime;

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
            earliestStart,   
            latestStart,0,0,
            earliestStart + minDurationHours, latestStart + maxDurationHours,0,0
        );
    }

    /**
     * Gets the total duration of this time window.
     * Handles cases where the window crosses midnight.
     * 
     * @return The duration of the time window
     */
    public Duration getTotalDuration() {
        if (currentStartTime.isAfter(currentEndTime)) {
            // Window crosses midnight
            Duration toMidnight = Duration.between(currentStartTime, LocalTime.MAX);
            Duration fromMidnight = Duration.between(LocalTime.MIN, currentEndTime);
            return toMidnight.plus(fromMidnight).plusNanos(1); // Add 1 nano for midnight itself
        } else {
            return Duration.between(currentStartTime, currentEndTime);
        }
    }
    @Override
    public boolean isSatisfied() {
                
        LocalTime now = LocalTime.now();
        boolean result;
        
        // Handle case where time window crosses midnight
        if (currentStartTime.isAfter(currentEndTime)) {
            result = !now.isAfter(currentEndTime) || !now.isBefore(currentStartTime);
        } else {
            result = !now.isBefore(currentStartTime) && !now.isAfter(currentEndTime);
        }
        
        if (result) {
            log.debug("Time window condition met: current time {} is within window {}-{}", 
                    now.format(TIME_FORMATTER), 
                    currentStartTime.format(TIME_FORMATTER), 
                    currentEndTime.format(TIME_FORMATTER));
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
        if (currentStartTime.isAfter(currentEndTime)) {
            // If we're in the window (after start or before end)
            if (!currentTime.isBefore(currentStartTime) || !currentTime.isAfter(currentEndTime)) {
                // If we're after start time, end time is end time today
                if (!currentTime.isBefore(currentStartTime)) {
                    return now.with(currentEndTime).plusDays(1);
                }
                // If we're before end time, end time is end time today
                return now.with(currentEndTime);
            } else {
                // We're not in the window, next window starts at start time today
                if (currentTime.isBefore(currentStartTime)) {
                    return now.with(currentStartTime);
                }
                // If we're after end time, next window starts at start time tomorrow
                return now.with(currentStartTime).plusDays(1);
            }
        } else {
            // Normal window (doesn't cross midnight)
            if (!currentTime.isBefore(currentStartTime) && !currentTime.isAfter(currentEndTime)) {
                // We're in the window, end time is end time today
                return now.with(currentEndTime);
            } else {
                // We're not in the window
                if (currentTime.isBefore(currentStartTime)) {
                    // Window starts later today
                    return now.with(currentStartTime);
                } else {
                    // Window starts tomorrow
                    return now.with(currentStartTime).plusDays(1);
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
        reset(false);
    }
    @Override
    public void reset(boolean randomize) {
        if (randomize) {
            randomizeTimeWindow();
        }
    }
    
    @Override
    public String getDescription() {
        LocalTime now = LocalTime.now();
        boolean isActive = isInTimeWindow(now);
        
        String randomRangeInfo = "";
        if (!startTimeMin.equals(startTimeMax) || !endTimeMin.equals(endTimeMax)) {
            randomRangeInfo = String.format(" (randomized from %s-%s to %s-%s)", 
                    startTimeMin.format(TIME_FORMATTER),
                    startTimeMax.format(TIME_FORMATTER),
                    endTimeMin.format(TIME_FORMATTER),
                    endTimeMax.format(TIME_FORMATTER));
        }
        
        return String.format("Time window: %s to %s%s (currently %s)", 
                currentStartTime.format(TIME_FORMATTER), 
                currentEndTime.format(TIME_FORMATTER),
                randomRangeInfo,
                isActive ? "active" : "inactive");
    }

    private boolean isInTimeWindow(LocalTime now) {
        if (currentEndTime.isAfter(currentStartTime)) {
            // Normal case (e.g., 8:00 to 16:00)
            return !now.isBefore(currentStartTime) && !now.isAfter(currentEndTime);
        } else {
            // Overnight case (e.g., 22:00 to 6:00)
            return !now.isBefore(currentStartTime) || !now.isAfter(currentEndTime);
        }
    }
}