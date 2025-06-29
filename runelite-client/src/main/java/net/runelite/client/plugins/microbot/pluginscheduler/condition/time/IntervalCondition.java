package net.runelite.client.plugins.microbot.pluginscheduler.condition.time;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
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
        return "0.0.3";
    }
    
    /**
     * The base/average interval between triggers, primarily used for display purposes
     */
    @Getter
    private final Duration interval;
    
    /**
     * The next time this condition should trigger
     */
    @Getter
    @Setter
    private transient ZonedDateTime nextTriggerTime;
    
    /**
     * The variation factor (0.0-1.0) representing how much intervals can vary from the mean
     * For example, 0.2 means intervals can vary by ±20% from the mean value
     */
    @Getter
    private final double randomFactor;
    
    /**
     * The minimum possible interval duration when randomization is enabled
     */
    @Getter
    private final Duration minInterval;
    
    /**
     * The maximum possible interval duration when randomization is enabled
     */
    @Getter
    private final Duration maxInterval;
    
    /**
     * Whether this interval uses randomization (true) or fixed intervals (false)
     */
    @Getter
    private final boolean randomize;
    
    /**
     * Optional condition for initial delay before first trigger
     * When present, this condition must be satisfied before the interval triggers can begin
     */
    @Getter
    private final SingleTriggerTimeCondition initialDelayCondition;
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
     * @param variationFactor Variation factor (0-1.0) - how much to vary the interval by percentage
     * @param maximumNumberOfRepeats Maximum number of times this condition can trigger
     */
    public IntervalCondition(Duration interval, boolean randomize, double variationFactor, long maximumNumberOfRepeats) {
        this(interval, randomize, variationFactor, maximumNumberOfRepeats, null);
    }
    
    /**
     * Creates an interval condition with optional randomization and initial delay
     * 
     * @param interval The base time interval
     * @param randomize Whether to randomize intervals
     * @param variationFactor Variation factor (0-1.0) - how much to vary the interval by percentage
     * @param maximumNumberOfRepeats Maximum number of times this condition can trigger
     * @param initialDelaySeconds Initial delay in seconds before first trigger
     */
    public IntervalCondition(Duration interval, boolean randomize, double variationFactor, long maximumNumberOfRepeats, Long initialDelaySeconds) {
        super(maximumNumberOfRepeats);
        this.interval = interval;        
        this.randomFactor = Math.max(0, Math.min(1.0, variationFactor));
        
        // Set min/max intervals based on variation factor
        if (randomize && variationFactor > 0) {
            long baseMillis = interval.toMillis();
            long variation = (long) (baseMillis * variationFactor);
            this.minInterval = Duration.ofMillis(Math.max(0, baseMillis - variation));
            this.maxInterval = Duration.ofMillis(baseMillis + variation);
            this.randomize = true;
        } else {
            this.minInterval = interval;
            this.maxInterval = interval;
            this.randomize = false;
        }
        
        // Initialize initial delay if specified
        if (initialDelaySeconds != null && initialDelaySeconds > 0) {
            this.initialDelayCondition = SingleTriggerTimeCondition.afterDelay(initialDelaySeconds);
        } else {
            this.initialDelayCondition = null;
        }
        
        this.nextTriggerTime = calculateNextTriggerTime();
    }
    
    /**
     * Private constructor with explicit min/max interval values
     * 
     * @param interval The base/average time interval for display purposes
     * @param minInterval Minimum possible interval duration
     * @param maxInterval Maximum possible interval duration
     * @param randomize Whether to randomize intervals
     * @param variationFactor Variation factor (0-1.0) representing how much variation is allowed
     * @param maximumNumberOfRepeats Maximum number of times this condition can trigger
     */
    private IntervalCondition(Duration interval, Duration minInterval, Duration maxInterval, 
                              boolean randomize, double variationFactor, long maximumNumberOfRepeats) {
        this(interval, minInterval, maxInterval, randomize, variationFactor, maximumNumberOfRepeats, 0L);
    }
    
    /**
     * Private constructor with explicit min/max interval values and initial delay
     * 
     * @param interval The base/average time interval for display purposes
     * @param minInterval Minimum possible interval duration
     * @param maxInterval Maximum possible interval duration
     * @param randomize Whether to randomize intervals
     * @param variationFactor Variation factor (0-1.0) representing how much variation is allowed
     * @param maximumNumberOfRepeats Maximum number of times this condition can trigger
     * @param initialDelaySeconds Initial delay in seconds before first trigger
     */
    public IntervalCondition(Duration interval, Duration minInterval, Duration maxInterval, 
                              boolean randomize, double variationFactor, long maximumNumberOfRepeats,
                              Long initialDelaySeconds) {
        super(maximumNumberOfRepeats);
        this.interval = interval;
        this.randomFactor = Math.max(0, Math.min(1.0, variationFactor));
        this.minInterval = minInterval;
        this.maxInterval = maxInterval;
        // We consider it randomized if min and max are different
        this.randomize = !minInterval.equals(maxInterval);
        
        // Initialize initial delay if specified
        if (initialDelaySeconds != null && initialDelaySeconds > 0) {
            this.initialDelayCondition = SingleTriggerTimeCondition.afterDelay(initialDelaySeconds);
        } else {
            this.initialDelayCondition = null;
        }
        
        this.nextTriggerTime = calculateNextTriggerTime();
    }

    /**
     * Private constructor with explicit min/max interval values and initial delay
     * 
     * @param interval The base/average time interval for display purposes
     * @param minInterval Minimum possible interval duration
     * @param maxInterval Maximum possible interval duration
     * @param randomize Whether to randomize intervals
     * @param variationFactor Variation factor (0-1.0) representing how much variation is allowed
     * @param maximumNumberOfRepeats Maximum number of times this condition can trigger
     * @param initialDelaySeconds Initial delay in seconds before first trigger
     */
    public IntervalCondition(Duration interval, Duration minInterval, Duration maxInterval, 
                              boolean randomize, double variationFactor, long maximumNumberOfRepeats,
                              SingleTriggerTimeCondition initialDelayCondition) {
        super(maximumNumberOfRepeats);
        this.interval = interval;
        this.randomFactor = Math.max(0, Math.min(1.0, variationFactor));
        this.minInterval = minInterval;
        this.maxInterval = maxInterval;
        // We consider it randomized if min and max are different
        this.randomize = !minInterval.equals(maxInterval);
        
        // Initialize initial delay if specified
        if (initialDelayCondition != null) {
            this.initialDelayCondition = initialDelayCondition.copy();
        } else {
            this.initialDelayCondition = null;
        }
        
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
     * Creates an interval condition with randomized timing using a base time and variation factor
     * 
     * @param baseMinutes The average interval duration in minutes
     * @param variationFactor How much the interval can vary (0-1.0, e.g., 0.2 means ±20%)
     * @param maximumNumberOfRepeats Maximum number of times this condition can trigger
     * @return A randomized interval condition
     */
    public static IntervalCondition randomizedMinutesWithVariation(int baseMinutes, double variationFactor, long maximumNumberOfRepeats) {
        return new IntervalCondition(Duration.ofMinutes(baseMinutes), true, variationFactor, maximumNumberOfRepeats);
    }
    
    /**
     * Creates an interval condition that triggers at intervals between the provided min and max durations.
     * 
     * @param minDuration Minimum interval duration
     * @param maxDuration Maximum interval duration
     * @return A randomized interval condition
     */
    public static IntervalCondition createRandomized(Duration minDuration, Duration maxDuration) {
        // Validate inputs
        if (minDuration.compareTo(maxDuration) > 0) {
            throw new IllegalArgumentException("Minimum duration must be less than or equal to maximum duration");
        }
        
        // Create an average interval for display purposes
        long minMillis = minDuration.toMillis();
        long maxMillis = maxDuration.toMillis();
        Duration avgInterval = Duration.ofMillis((minMillis + maxMillis) / 2);
        
        // Calculate a randomization factor - represents how much the intervals can vary
        // from the mean value (as a percentage of the mean)
        double variationFactor = 0.0;
        if (minMillis < maxMillis) {
            // Calculate as a percentage of the average
            long halfRange = (maxMillis - minMillis) / 2;
            variationFactor = halfRange / (double) avgInterval.toMillis();
        }
        
        log.debug("createRandomized: min={}, max={}, avg={}, variationFactor={}", 
                minDuration, maxDuration, avgInterval, variationFactor);
        
        return new IntervalCondition(avgInterval, minDuration, maxDuration, true, variationFactor, 0);
    }

    /**
     * Creates an interval condition with randomized timing using seconds range
     * 
     * @param minSeconds Minimum interval in seconds
     * @param maxSeconds Maximum interval in seconds
     * @return A randomized interval condition
     */
    public static IntervalCondition randomizedSeconds(int minSeconds, int maxSeconds) {
        return createRandomized(Duration.ofSeconds(minSeconds), Duration.ofSeconds(maxSeconds));
    }
    
    /**
     * Creates an interval condition with randomized timing using minutes range
     * 
     * @param minMinutes Minimum interval in minutes
     * @param maxMinutes Maximum interval in minutes
     * @return A randomized interval condition
     */
    public static IntervalCondition randomizedMinutes(int minMinutes, int maxMinutes) {
        return createRandomized(Duration.ofMinutes(minMinutes), Duration.ofMinutes(maxMinutes));
    }
    
    /**
     * Creates an interval condition with randomized timing using hours range
     * 
     * @param minHours Minimum interval in hours
     * @param maxHours Maximum interval in hours
     * @return A randomized interval condition
     */
    public static IntervalCondition randomizedHours(int minHours, int maxHours) {
        return createRandomized(Duration.ofHours(minHours), Duration.ofHours(maxHours));
    }

    /**
     * Creates an interval condition with minutes and an initial delay
     * 
     * @param minutes The interval in minutes
     * @param initialDelaySeconds The initial delay in seconds before first trigger
     */
    public static IntervalCondition everyMinutesWithDelay(int minutes, Long initialDelaySeconds) {
        return new IntervalCondition(Duration.ofMinutes(minutes), false, 0.0, 0, initialDelaySeconds);
    }
    
    /**
     * Creates an interval condition with hours and an initial delay
     * 
     * @param hours The interval in hours
     * @param initialDelaySeconds The initial delay in seconds before first trigger
     */
    public static IntervalCondition everyHoursWithDelay(int hours, Long initialDelaySeconds) {
        return new IntervalCondition(Duration.ofHours(hours), false, 0.0, 0, initialDelaySeconds);
    }
    
    /**
     * Creates an interval condition with randomized timing using a base time and variation factor,
     * plus an initial delay before the first trigger
     * 
     * @param baseMinutes The average interval duration in minutes
     * @param variationFactor How much the interval can vary (0-1.0, e.g., 0.2 means ±20%)
     * @param maximumNumberOfRepeats Maximum number of times this condition can trigger
     * @param initialDelaySeconds The initial delay in seconds before first trigger
     * @return A randomized interval condition with initial delay
     */
    public static IntervalCondition randomizedMinutesWithVariationAndDelay(
            int baseMinutes, double variationFactor, long maximumNumberOfRepeats, Long initialDelaySeconds) {
        return new IntervalCondition(Duration.ofMinutes(baseMinutes), 
                true, variationFactor, maximumNumberOfRepeats, initialDelaySeconds);
    }

    /**
     * Creates an interval condition with randomized timing using seconds range and an initial delay
     * 
     * @param minSeconds Minimum interval in seconds
     * @param maxSeconds Maximum interval in seconds
     * @param initialDelaySeconds The initial delay in seconds before first trigger
     * @return A randomized interval condition with initial delay
     */
    public static IntervalCondition randomizedSecondsWithDelay(int minSeconds, int maxSeconds, Long initialDelaySeconds) {
        IntervalCondition condition = createRandomized(Duration.ofSeconds(minSeconds), Duration.ofSeconds(maxSeconds));
        return new IntervalCondition(
                condition.interval, 
                condition.minInterval, 
                condition.maxInterval, 
                condition.randomize, 
                condition.randomFactor, 
                0,
                initialDelaySeconds);
    }
    
    /**
     * Creates an interval condition with randomized timing using minutes range and an initial delay
     * 
     * @param minMinutes Minimum interval in minutes
     * @param maxMinutes Maximum interval in minutes
     * @param initialDelaySeconds The initial delay in seconds before first trigger
     * @return A randomized interval condition with initial delay
     */
    public static IntervalCondition randomizedMinutesWithDelay(int minMinutes, int maxMinutes, Long initialDelaySeconds) {
        IntervalCondition condition = createRandomized(Duration.ofMinutes(minMinutes), Duration.ofMinutes(maxMinutes));
        return new IntervalCondition(
                condition.interval, 
                condition.minInterval, 
                condition.maxInterval, 
                condition.randomize, 
                condition.randomFactor, 
                0,
                initialDelaySeconds);
    }
    
    /**
     * Creates an interval condition with randomized timing using hours range and an initial delay
     * 
     * @param minHours Minimum interval in hours
     * @param maxHours Maximum interval in hours
     * @param initialDelaySeconds The initial delay in seconds before first trigger
     * @return A randomized interval condition with initial delay
     */
    public static IntervalCondition randomizedHoursWithDelay(int minHours, int maxHours, Long initialDelaySeconds) {
        IntervalCondition condition = createRandomized(Duration.ofHours(minHours), Duration.ofHours(maxHours));
        return new IntervalCondition(
                condition.interval, 
                condition.minInterval, 
                condition.maxInterval, 
                condition.randomize, 
                condition.randomFactor, 
                0,
                initialDelaySeconds);
    }

    @Override
    public boolean isSatisfied() {
        if (nextTriggerTime == null) {
            return false;
        }
        if(!canTriggerAgain()) {
            return false;
        }
        
        // Check initial delay condition first (if exists)
        if (initialDelayCondition != null && !initialDelayCondition.isSatisfied()) {
            return false; // Initial delay hasn't been met yet
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
        String initialDelayInfo = "";
        
        // Check initial delay status
        if (initialDelayCondition != null && !initialDelayCondition.isSatisfied()) {
            Duration initialDelayRemaining = Duration.between(now, initialDelayCondition.getTargetTime());
            long seconds = initialDelayRemaining.getSeconds();
            if (seconds > 0) {
                initialDelayInfo = String.format(" (initial delay: %02d:%02d:%02d)", 
                    seconds / 3600, (seconds % 3600) / 60, seconds % 60);
            }
        }
        
        if (nextTriggerTime != null && (initialDelayCondition == null || initialDelayCondition.isSatisfied())) {
            if (now.isAfter(nextTriggerTime)) {
                timeLeft = " (ready now)";
            } else {
                Duration remaining = Duration.between(now, nextTriggerTime);
                long seconds = remaining.getSeconds();
                timeLeft = String.format(" (next in %02d:%02d:%02d)", 
                    seconds / 3600, (seconds % 3600) / 60, seconds % 60);
            }
        }
        
        // The condition was randomized if min and max intervals are different
        if (randomize) {
            // Show as a range when we have min and max
            return String.format("Every %s-%s%s%s", 
                    formatDuration(minInterval), 
                    formatDuration(maxInterval),
                    timeLeft,
                    initialDelayInfo);
        } else {
            // Fixed interval
            return String.format("Every %s%s%s", formatDuration(interval), timeLeft, initialDelayInfo);
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
        
        // Initial Delay information
        if (initialDelayCondition != null) {
            sb.append("  │ Initial Delay: ");
            ZonedDateTime now = getNow();
            if (initialDelayCondition.isSatisfied()) {
                sb.append("Completed\n");
            } else {
                Duration initialDelayRemaining = Duration.between(now, initialDelayCondition.getTargetTime());
                long seconds = initialDelayRemaining.getSeconds();
                if (seconds > 0) {
                    sb.append(String.format("%02d:%02d:%02d remaining\n", 
                        seconds / 3600, (seconds % 3600) / 60, seconds % 60));
                } else {
                    sb.append("Ready\n");
                }
            }
        }
        
        // Randomization
        sb.append("  ├─ Randomization ────────────────────────────\n");
        if (randomize) {
            sb.append("  │ Min Interval: ").append(formatDuration(minInterval)).append("\n");
            sb.append("  │ Max Interval: ").append(formatDuration(maxInterval)).append("\n");        
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
        
        // Reset initial delay condition if it exists
        if (initialDelayCondition != null) {
            initialDelayCondition.reset(false);
        }
        
        log.debug("IntervalCondition reset, next trigger at: {}", nextTriggerTime);
    }
    @Override
    public void hardReset() {
        // Reset the condition state
        this.currentValidResetCount = 0;
        this.lastValidResetTime = LocalDateTime.now();
        
        // Reset initial delay condition if it exists
        if (initialDelayCondition != null) {
            initialDelayCondition.hardReset();
        }
        this.nextTriggerTime = calculateNextTriggerTime();               
    }
   
    @Override
    public double getProgressPercentage() {
        ZonedDateTime now = getNow();
        if (nextTriggerTime == null) {
            return 0.0;
        }
        if (now.isAfter(nextTriggerTime)) {
            return 100.0;
        }
        
        // Calculate how much time has passed since the last trigger
        Duration timeUntilNextTrigger = Duration.between(now, nextTriggerTime);
        
        // If this is the first trigger (no lastValidResetTime), we need to use the interval
        // from initialization to calculate progress
        Duration lastInterval;
        if (lastValidResetTime == null) {
            // Use the average interval for randomized conditions
            if (randomize) {
                lastInterval = interval; // Average interval
            } else {
                lastInterval = interval; // Fixed interval
            }
        } else {
            // If we've had a previous trigger, calculate from that time to the next trigger
            Duration actualInterval = Duration.between(lastValidResetTime.atZone(getNow().getZone()), nextTriggerTime);
            lastInterval = actualInterval;
        }
        
        // Calculate ratio of elapsed time
        long remainingMillis = timeUntilNextTrigger.toMillis();
        long totalMillis = lastInterval.toMillis();
        
        double elapsedRatio = 1.0 - (remainingMillis / (double) totalMillis);
        return Math.max(0, Math.min(100, elapsedRatio * 100));
    }
    @Override
    public Optional<ZonedDateTime> getCurrentTriggerTime() {
        if (!canTriggerAgain()) {
            return Optional.empty(); // No trigger time if already triggered to often
        }
        ZonedDateTime now = getNow();

        if (initialDelayCondition != null && !initialDelayCondition.isSatisfied()) {
            return initialDelayCondition.getCurrentTriggerTime(); // Return the initial delay condition's trigger time
        }
        // If already satisfied (past the trigger time)
        if (now.isAfter(nextTriggerTime)) {
            return Optional.of(nextTriggerTime); // Return the passed time until reset
        }
        
        // Otherwise return the scheduled next trigger time
        return Optional.of(nextTriggerTime);
    }
    
    /**
     * Calculates the next trigger time based on the current configuration.
     * 
     * @return The next time this condition should trigger
     */
    private ZonedDateTime calculateNextTriggerTime() {
        ZonedDateTime now = getNow();
        
        // Skip the future interval calculation during initial creation or if can't trigger again
        boolean skipFutureInterval = !canTriggerAgain() || this.currentValidResetCount == 0;
        Duration nextInterval;
        
        // Generate a randomized interval if randomization is enabled
        if (randomize) {
            // Generate a random value between min and max interval
            long minMillis = minInterval.toMillis();
            long maxMillis = maxInterval.toMillis();
            long randomMillis = ThreadLocalRandom.current().nextLong(minMillis, maxMillis + 1);
            nextInterval = Duration.ofMillis(randomMillis);
            
            log.debug("Randomized interval: {}ms (between {}ms and {}ms)", 
                    randomMillis, minMillis, maxMillis);
        } 
        // Use fixed interval otherwise
        else {
            nextInterval = interval;
        }
        
        // For initial creation or when max triggers reached, trigger immediately
        if (skipFutureInterval) {
            return now;
        } 
        // Otherwise, schedule the next trigger based on the calculated interval
        else {
            return now.plus(nextInterval);
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