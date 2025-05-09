package net.runelite.client.plugins.microbot.pluginscheduler.condition.time;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Condition that is met at regular intervals.
 * Can be used for periodic tasks or for creating natural breaks.
 */

@Slf4j
@EqualsAndHashCode(callSuper = false , exclude = {"nextTriggerTime"})
public class IntervalCondition extends TimeCondition {
    /**
     * Version of the IntervalCondition class
     */
    public static String getVersion() {
        return "0.0.1";
    }
    @Getter
    private final Duration interval;
    private transient ZonedDateTime nextTriggerTime;
    @Getter
    private final boolean randomize;
    @Getter
    private final double randomFactor;
    
    // Add min/max interval support
    @Getter
    private final Duration minInterval;
    @Getter
    private final Duration maxInterval;
    @Getter
    private final boolean isRandomized; // True if using min/max intervals
 
    /**
     * Creates an interval condition that triggers at regular intervals
     * 
     * @param interval The time interval between triggers
     */
    public IntervalCondition(Duration interval) {
        this(interval, false, 0.0, 0);
    }
    
    /**
     * Creates an interval condition with optional randomization
     * 
     * @param interval The base time interval
     * @param randomize Whether to randomize intervals
     * @param randomFactor Randomization factor (0-1.0) - how much to vary the interval
     */
    public IntervalCondition(Duration interval, boolean randomize, double randomFactor, long maximumNumberOfRepeats) {
        super(maximumNumberOfRepeats);
        this.interval = interval;
        this.randomize = randomize;
        this.randomFactor = Math.max(0, Math.min(1.0, randomFactor));
        
        // Set min/max intervals based on randomization factor
        if (randomize && randomFactor > 0) {
            long baseMillis = interval.toMillis();
            long variation = (long) (baseMillis * randomFactor);
            this.minInterval = Duration.ofMillis(Math.max(0, baseMillis - variation));
            this.maxInterval = Duration.ofMillis(baseMillis + variation);
            this.isRandomized = true;
        } else {
            this.minInterval = interval;
            this.maxInterval = interval;
            this.isRandomized = false;
        }
        
        this.nextTriggerTime = calculateNextTriggerTime();
    }
    
    /**
     * Private constructor with explicit min/max interval values
     */
    private IntervalCondition(Duration interval, Duration minInterval, Duration maxInterval, 
                              boolean randomize, double randomFactor, long maximumNumberOfRepeats) {
        super(maximumNumberOfRepeats);
        this.interval = interval;
        this.randomize = randomize;
        this.randomFactor = randomFactor;
        this.minInterval = minInterval;
        this.maxInterval = maxInterval;
        this.isRandomized = !minInterval.equals(maxInterval);
        this.nextTriggerTime = calculateNextTriggerTime();
    }
    
    /**
     * Creates an interval condition with minutes
     */
    public static IntervalCondition everyMinutes(int minutes) {
        return new IntervalCondition(Duration.ofMinutes(minutes));
    }
    
    /**
     * Creates an interval condition with hours
     */
    public static IntervalCondition everyHours(int hours) {
        return new IntervalCondition(Duration.ofHours(hours));
    }
    
    /**
     * Creates an interval condition with randomized timing
     */
    public static IntervalCondition randomizedMinutes(int baseMinutes, double randomFactor ,long maximumNumberOfRepeats) {
        return new IntervalCondition(Duration.ofMinutes(baseMinutes), true, randomFactor, maximumNumberOfRepeats);
    }
    
    /**
     * Creates an interval condition that triggers at intervals between the provided min and max durations.
     * 
     * @param minDuration Minimum interval duration
     * @param maxDuration Maximum interval duration
     * @return A randomized interval condition
     */
    public static IntervalCondition createRandomized(Duration minDuration, Duration maxDuration) {
        // Create an average interval for display purposes
        long minMillis = minDuration.toMillis();
        long maxMillis = maxDuration.toMillis();
        Duration avgInterval = Duration.ofMillis((minMillis + maxMillis) / 2);
        
        // Calculate a randomization factor for backward compatibility
        double randomFactor = 0.0;
        if (minMillis < maxMillis) {
            long diff = maxMillis - minMillis;
            randomFactor = diff / (double)(avgInterval.toMillis() * 2);
        }
        
        return new IntervalCondition(avgInterval, minDuration, maxDuration, true, randomFactor, 0);
    }

    @Override
    public boolean isSatisfied() {
        if(!canTriggerAgain()) {
            return false;
        }
        
        ZonedDateTime now = getNow();
        if (now.isAfter(nextTriggerTime)) {            
            return true;
        }
        return false;
    }

    @Override
    public String getDescription() {
        ZonedDateTime now = getNow();
        String timeLeft = "";
        
        if (nextTriggerTime != null) {
            if (now.isAfter(nextTriggerTime)) {
                timeLeft = " (ready now)";
            } else {
                Duration remaining = Duration.between(now, nextTriggerTime);
                long seconds = remaining.getSeconds();
                timeLeft = String.format(" (next in %02d:%02d:%02d)", 
                    seconds / 3600, (seconds % 3600) / 60, seconds % 60);
            }
        }
        
        if (isRandomized) {
            return String.format("Every %s-%s%s", 
                    formatDuration(minInterval), 
                    formatDuration(maxInterval),
                    timeLeft);
        } else if (randomize) {
            return String.format("Every %s±%.0f%%%s", 
                    formatDuration(interval), randomFactor * 100, timeLeft);
        } else {
            return String.format("Every %s%s", formatDuration(interval), timeLeft);
        }
    }

    /**
     * Returns a detailed description of the interval condition with additional status information
     */
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(getDescription()).append("\n");
        
        ZonedDateTime now = getNow();
        if (nextTriggerTime != null) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            sb.append("Next trigger at: ").append(nextTriggerTime.format(formatter)).append("\n");
            
            if (now.isAfter(nextTriggerTime)) {
                sb.append("Status: Ready to trigger\n");
            } else {
                Duration remaining = Duration.between(now, nextTriggerTime);
                long seconds = remaining.getSeconds();
                sb.append("Time remaining: ")
                  .append(String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60))
                  .append("\n");
            }
        }
        
        sb.append("Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
        
        if (randomize) {
            sb.append("Randomization: Enabled (±").append(String.format("%.0f", randomFactor * 100)).append("%)\n");
        }
        
        // Add lastValidResetTime information
        if (lastValidResetTime != null && currentValidResetCount > 0) {
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
            sb.append("Last reset: ").append(lastValidResetTime.format(formatter)).append("\n");
            
            // Calculate time since the last reset
            Duration sinceLastReset = Duration.between(lastValidResetTime, LocalDateTime.now());
            long seconds = sinceLastReset.getSeconds();
            sb.append("Time since last reset: ")
              .append(String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60))
              .append("\n");
        }
        
        sb.append(super.getDescription());
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        // Basic information
        sb.append("IntervalCondition:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        sb.append("  │ Interval: ").append(formatDuration(interval)).append("\n");
        
        // Randomization
        sb.append("  ├─ Randomization ────────────────────────────\n");
        if (isRandomized) {
            sb.append("  │ Min Interval: ").append(formatDuration(minInterval)).append("\n");
            sb.append("  │ Max Interval: ").append(formatDuration(maxInterval)).append("\n");
        } else if (randomize) {
            sb.append("  │ Randomization: Enabled\n");
            sb.append("  │ Random Factor: ±").append(String.format("%.0f%%", randomFactor * 100)).append("\n");
        } else {
            sb.append("  │ Randomization: Disabled\n");
        }
        
        // Status information
        sb.append("  ├─ Status ──────────────────────────────────\n");
        sb.append("  │ Satisfied: ").append(isSatisfied()).append("\n");
        
        ZonedDateTime now = getNow();
        if (nextTriggerTime != null) {
            sb.append("  │ Next Trigger: ").append(nextTriggerTime.format(dateTimeFormatter)).append("\n");
            
            if (!now.isAfter(nextTriggerTime)) {
                Duration remaining = Duration.between(now, nextTriggerTime);
                long seconds = remaining.getSeconds();
                sb.append("  │ Time Remaining: ")
                  .append(String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60))
                  .append("\n");
            } else {
                sb.append("  │ Ready to trigger\n");
            }
        }
        
        sb.append("  │ Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
        
        // Tracking info
        sb.append("  └─ Tracking ────────────────────────────────\n");
        sb.append("    Reset Count: ").append(currentValidResetCount);
        if (this.getMaximumNumberOfRepeats() > 0) {
            sb.append("/").append(getMaximumNumberOfRepeats());
        } else {
            sb.append(" (unlimited)");
        }
        sb.append("\n");
        if (lastValidResetTime != null) {
            sb.append("    Last Reset: ").append(lastValidResetTime.format(dateTimeFormatter)).append("\n");
            
            // Add time since last reset
            Duration sinceLastReset = Duration.between(lastValidResetTime, LocalDateTime.now());
            long seconds = sinceLastReset.getSeconds();
            sb.append("    Time Since Reset: ")
              .append(String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60))
              .append("\n");
        }
        sb.append("    Can Trigger Again: ").append(canTriggerAgain()).append("\n");
        
        return sb.toString();
    }

    @Override
    public void reset(boolean randomize) {
        updateValidReset();
        this.nextTriggerTime = calculateNextTriggerTime();        
        log.debug("IntervalCondition reset, next trigger at: {}", nextTriggerTime);
    }
   
    @Override
    public double getProgressPercentage() {
        ZonedDateTime now = getNow();
        if (now.isAfter(nextTriggerTime)) {
            return 100.0;
        }
        
        // Calculate how much time has passed since the last trigger
        Duration timeUntilNextTrigger = Duration.between(now, nextTriggerTime);
        double elapsedRatio = 1.0 - (timeUntilNextTrigger.toMillis() / (double) interval.toMillis());
        return Math.max(0, Math.min(100, elapsedRatio * 100));
    }
    @Override
    public Optional<ZonedDateTime> getCurrentTriggerTime() {
        if (!canTriggerAgain()) {
            return Optional.empty(); // No trigger time if already triggered to often
        }
        ZonedDateTime now = getNow();

        
        // If already satisfied (past the trigger time)
        if (now.isAfter(nextTriggerTime)) {
            return Optional.of(nextTriggerTime); // Return the passed time until reset
        }
        
        // Otherwise return the scheduled next trigger time
        return Optional.of(nextTriggerTime);
    }
    
    private ZonedDateTime calculateNextTriggerTime() {
        ZonedDateTime now = getNow();
        
        // If using min/max intervals
        if (isRandomized) {
            long minMillis = minInterval.toMillis();
            long maxMillis = maxInterval.toMillis();
            long randomMillis = ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1);
            Duration randomizedInterval = Duration.ofMillis(randomMillis);
            
            if (canTriggerAgain() && this.currentValidResetCount > 0) {
                // If the condition has already been triggered, we need to set the next trigger time
                return now.plus(randomizedInterval);
            } else {
                // Initial creation
                return now;
            }
        }
        // If using randomize factor approach
        else if (randomize && randomFactor > 0) {
            long intervalMillis = interval.toMillis();
            long variance = (long) (intervalMillis * randomFactor);
            long randomAdditionalMillis = ThreadLocalRandom.current().nextLong(-variance, variance + 1);
            
            Duration randomizedInterval = Duration.ofMillis(intervalMillis + randomAdditionalMillis);
            if (canTriggerAgain() && this.currentValidResetCount > 0) {
                return now.plus(randomizedInterval);
            } else {
                return now;
            }
        } 
        // Fixed interval
        else {
            if (canTriggerAgain() && this.currentValidResetCount > 0) {
                return now.plus(interval);
            } else {
                return now;
            }
        }
    }
    
    private String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        if (seconds < 60) {
            return seconds + "s";
        } else if (seconds < 3600) {
            return String.format("%dm %ds", seconds / 60, seconds % 60);
        } else {
            return String.format("%dh %dm", seconds / 3600, (seconds % 3600) / 60);
        }
    }
}