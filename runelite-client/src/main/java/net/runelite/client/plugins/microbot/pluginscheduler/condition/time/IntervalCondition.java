package net.runelite.client.plugins.microbot.pluginscheduler.condition.time;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Condition that is met at regular intervals.
 * Can be used for periodic tasks or for creating natural breaks.
 */
@Getter
@Slf4j
public class IntervalCondition implements Condition {
    private final Duration interval;
    private ZonedDateTime nextTriggerTime;
    private boolean randomize;
    private double randomFactor;
    private boolean registered = false;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    /**
     * Creates an interval condition that triggers at regular intervals
     * 
     * @param interval The time interval between triggers
     */
    public IntervalCondition(Duration interval) {
        this(interval, false, 0);
    }
    
    /**
     * Creates an interval condition with optional randomization
     * 
     * @param interval The base time interval
     * @param randomize Whether to randomize intervals
     * @param randomFactor Randomization factor (0-1.0) - how much to vary the interval
     */
    public IntervalCondition(Duration interval, boolean randomize, double randomFactor) {
        this.interval = interval;
        this.randomize = randomize;
        this.randomFactor = Math.max(0, Math.min(1.0, randomFactor));
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
    public static IntervalCondition randomizedMinutes(int baseMinutes, double randomFactor) {
        return new IntervalCondition(Duration.ofMinutes(baseMinutes), true, randomFactor);
    }
    
    /**
     * Creates an interval condition that triggers at intervals between the provided min and max durations.
     * 
     * @param minDuration Minimum interval duration
     * @param maxDuration Maximum interval duration
     * @return A randomized interval condition
     */
    public static IntervalCondition createRandomized(Duration minDuration, Duration maxDuration) {
        if (minDuration.equals(maxDuration)) {
            return new IntervalCondition(minDuration);
        }
        
        long minSeconds = minDuration.getSeconds();
        long maxSeconds = maxDuration.getSeconds();
        double randomFactor = 0.0;
        
        if (maxSeconds > minSeconds) {
            // Calculate randomization factor based on the difference between min and max
            long avgSeconds = (minSeconds + maxSeconds) / 2;
            randomFactor = (double)(maxSeconds - minSeconds) / (2 * avgSeconds);
            
            // Create the interval condition with randomization
            return new IntervalCondition(Duration.ofSeconds(avgSeconds), true, randomFactor);
        } else {
            return new IntervalCondition(minDuration);
        }
    }

    /**
     * Creates an interval condition from a ZonedDateTime object.
     * The interval will be the duration between now and the specified time,
     * which will repeat indefinitely.
     * 
     * @param firstTrigger The first time this interval should trigger
     * @return An interval condition
     */
    public static IntervalCondition fromFirstTrigger(ZonedDateTime firstTrigger) {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        Duration initialDelay = Duration.between(now, firstTrigger);
        
        // If the time is in the past, adjust to make it valid
        if (initialDelay.isNegative() || initialDelay.isZero()) {
            log.warn("First trigger time is in the past, using default 1 hour interval");
            return new IntervalCondition(Duration.ofHours(1));
        }
        
        // We'll use the initial delay as the interval duration
        return new IntervalCondition(initialDelay);
    }

    /**
     * Gets the total duration of this interval (always returns the base interval).
     * 
     * @return The interval duration
     */
    public Duration getTotalDuration() {
        return interval;
    }
   /**
     * Gets the end time for this interval condition.
     * This is when the next trigger will occur.
     * 
     * @return The next trigger time
     */
    public ZonedDateTime getEndTime() {
        return nextTriggerTime;
    }
    private ZonedDateTime calculateNextTriggerTime() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        Duration actualInterval = interval;
        
        if (randomize && randomFactor > 0) {
            long baseSeconds = interval.getSeconds();
            long variation = (long)(baseSeconds * randomFactor);
            long lower = Math.max(1, baseSeconds - variation);
            long upper = baseSeconds + variation;
            long randomSeconds = ThreadLocalRandom.current().nextLong(lower, upper + 1);
            actualInterval = Duration.ofSeconds(randomSeconds);
        }
        
        return now.plus(actualInterval);
    }
    
    @Override
    public boolean isMet() {
        // Register for events if not already registered
        if (!registered) {
            Microbot.getEventBus().register(this);
            registered = true;
        }
        
            
        
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        boolean result = now.isEqual(nextTriggerTime) || now.isAfter(nextTriggerTime);
        
        if (result) {
            //log.info("Interval condition met at {}, current trigger at {}", 
            //        now.format(TIME_FORMATTER),
            //        nextTriggerTime.format(TIME_FORMATTER));
            
            // Schedule next trigger
            //nextTriggerTime = calculateNextTriggerTime();
            
        }
        
        return result;
    }
    
    @Subscribe
    public void onGameTick(GameTick event) {
        // This ensures we check the condition on every game tick
    }
    
    @Override
    public void reset() {
        nextTriggerTime = calculateNextTriggerTime();
    }
    
    /**
     * Gets the time remaining until the next trigger
     */
    public Duration getTimeRemaining() {
        ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
        return Duration.between(now, nextTriggerTime).isNegative() ? 
                Duration.ZERO : Duration.between(now, nextTriggerTime);
    }
    
    @Override
    public String getDescription() {
        long hours = interval.toHours();
        long minutes = interval.toMinutesPart();
        long seconds = interval.toSecondsPart();
        
        StringBuilder sb = new StringBuilder("Triggers every ");
        if (hours > 0) sb.append(hours).append("h ");
        if (minutes > 0 || hours > 0) sb.append(minutes).append("m ");
        if (seconds > 0 || (hours == 0 && minutes == 0)) sb.append(seconds).append("s");
        
        if (randomize) {
            sb.append(" (±").append((int)(randomFactor * 100)).append("%)");
        }
        
        // Calculate progress based on time remaining until next trigger
        Duration remaining = getTimeRemaining();
        Duration total = getTotalDuration();
        
        if (remaining.isZero()) {
            // Condition is met
            sb.append(", progress: 100.0%");
        } else if (!total.isZero()) {
            // Calculate progress as percentage of time elapsed
            double progressPercent = 100.0 * (1.0 - (double)remaining.toMillis() / total.toMillis());
            progressPercent = Math.min(100.0, Math.max(0.0, progressPercent)); // Clamp between 0-100%
            sb.append(String.format(", progress: %.1f%%", progressPercent));
        }
        
        sb.append(", next at ").append(nextTriggerTime.format(TIME_FORMATTER));
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
                log.debug("Error unregistering interval condition", e);
            }
            registered = false;
        }
    }
    
    @Override
    public double getProgressPercentage() {
        Duration total = getTotalDuration();
        Duration remaining = getTimeRemaining();
        
        if (total.isZero()) {
            return isMet() ? 100.0 : 0.0;
        }
        
        return 100.0 * (1.0 - (double)remaining.toMillis() / total.toMillis());
    }
}