package net.runelite.client.plugins.microbot.pluginscheduler.condition.time;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;

import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Abstract base class for all time-based conditions.
 * Provides common functionality for time calculations and event handling.
 */
@Slf4j
@EqualsAndHashCode(callSuper = false)
public abstract class TimeCondition implements Condition {
    @Getter
    private final long maximumNumberOfRepeats;
    @Getter
    @Setter
    protected transient long currentValidResetCount = 0;
     // Last reset timestamp tracking
    protected transient LocalDateTime lastValidResetTime;
    protected static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    // Pause-related fields
    @Getter
    protected transient boolean isPaused = false;
    protected transient ZonedDateTime pauseStartTime;
    @Getter
    protected transient Duration totalPauseDuration = Duration.ZERO;
    
    @Setter
    private transient ZonedDateTime nextTriggerTime;
    
    /**
     * Calculates the current pause duration without unpausing the condition.
     * Provides high-resolution timing for pause duration with nanosecond precision.
     * 
     * @return The current pause duration, or Duration.ZERO if not paused
     */
    protected Duration getCurrentPauseDuration() {
        if (!isPaused || pauseStartTime == null) {
            return Duration.ZERO;
        }
        // Calculate with nanosecond precision for higher resolution
        return Duration.between(pauseStartTime, getNow());
    }
    
    /**
     * Gets the effective "now" time, adjusted for any active pause.
     * This method is used to provide a consistent time reference point that 
     * doesn't advance during pauses.
     * 
     * @return The current time if not paused, or the pause start time if paused
     */
    protected ZonedDateTime getEffectiveNow() {
        return isPaused ? pauseStartTime : getNow();
    }
    
    /**
     * This method returns the next trigger time, adjusted for any pauses.
     * If the condition is paused, the next trigger time is shifted by the duration of the pause.
     * This allows the condition to account for time spent in a paused state when calculating the next trigger.
     * 
     * Uses high-resolution pause duration tracking for more accurate calculations.
     * 
     * @return Optional containing the adjusted next trigger time, or empty if no trigger is set
     */
    public Optional<ZonedDateTime> getNextTriggerTimeWithPause() {
        if (nextTriggerTime == null) {
            return Optional.empty();
        }
        
        // If paused, adjust the trigger time by the current pause duration
        if (isPaused) {
            Duration currentPauseDuration = getCurrentPauseDuration();
            return Optional.of(nextTriggerTime.plus(currentPauseDuration));
        }
        
        return Optional.of(nextTriggerTime);
    }
    public TimeCondition() {
        // Default constructor
        this(0);                
    }
    /**
     * Constructor for TimeCondition with a specified repeat count
     * 
     * @param maximumNumberOfRepeats Maximum number of times this condition can repeat, zero or negative means infinite repeats
     */
    public TimeCondition(final long maximumNumberOfRepeats) {
        this.maximumNumberOfRepeats = maximumNumberOfRepeats;
        lastValidResetTime = LocalDateTime.now();
    }
    /**
     * Gets the current date and time in the system default time zone with maximum precision.
     * Uses the most precise clock available in the system for consistent timing.
     * 
     * @return The current ZonedDateTime with nanosecond precision
     */
    protected ZonedDateTime getNow() {
        return ZonedDateTime.now(ZoneId.systemDefault());
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.TIME;
    }
    
    /**
     * Pauses this time condition, preventing it from being satisfied until resumed.
     * When paused, the condition's trigger time will be shifted by the pause duration.
     * Uses high-precision time tracking to ensure accurate pause duration calculation.
     */
    public void pause() {
        if (!isPaused) {
            isPaused = true;
            pauseStartTime = getNow();
            log.debug("Time condition paused at: {}", pauseStartTime);
        }
    }
    
    /**
     * resumes this time condition, allowing it to be satisfied again.
     * The trigger time will be shifted by the duration of the pause with high precision.
     * Uses nanosecond-level precision for duration calculations.
     */
    public void resume() {
        if (isPaused) {
            ZonedDateTime now = getNow();
            Duration pauseDuration = Duration.between(pauseStartTime, now);
            totalPauseDuration = totalPauseDuration.plus(pauseDuration);
            isPaused = false;
            
            // Keep track of pause end time before nulling pauseStartTime
            ZonedDateTime pauseEndTime = now;
            pauseStartTime = null;
            
            // Call the subclass implementation to handle specific adjustments
            onResume(pauseDuration);
            
            log.debug("Time condition resumed at: {}, pause duration: {}, total pause duration: {}", 
                    pauseEndTime, formatDuration(pauseDuration), formatDuration(totalPauseDuration));
        }
    }
    
    /**
     * Called when the condition is resumed.
     * Subclasses should implement this method to adjust their trigger times.
     * 
     * @param pauseDuration The duration of the most recent pause
     */
    protected abstract void onResume(Duration pauseDuration);
    
    /**
     * Formats a duration into a human-readable string with appropriate precision.
     * Shows milliseconds for durations less than 1 second for higher precision.
     * 
     * @param duration The duration to format
     * @return A human-readable string representation of the duration
     */
    protected String formatDuration(Duration duration) {
        long seconds = duration.getSeconds();
        int nanos = duration.getNano();
        
        // For very short durations, show milliseconds
        if (seconds == 0 && nanos > 0) {
            return String.format("%dms", nanos / 1_000_000);
        } else if (seconds < 60) {
            // For durations under a minute, show seconds with decimal precision if needed
            if (nanos > 0) {
                return String.format("%.2fs", seconds + (nanos / 1_000_000_000.0));
            }
            return seconds + "s";
        } else if (seconds < 3600) {
            return String.format("%dm %ds", seconds / 60, seconds % 60);
        } else {
            return String.format("%dh %dm", seconds / 3600, (seconds % 3600) / 60);
        }
    }
    
    @Override
    public String getDescription() {
        boolean canTrigger = canTriggerAgain();
        String triggerStatus = canTrigger ? "Can trigger" : "Cannot trigger";
        String pauseStatus = isPaused ? " (PAUSED)" : "";
        String triggerCount = "Trigger Count: " + (maximumNumberOfRepeats > 0 ? 
                                 " (" + currentValidResetCount + "/" + maximumNumberOfRepeats + ")" : 
                                 String.valueOf(currentValidResetCount));
        
        String lastReset = lastValidResetTime != null ? 
                "Last reset: " + lastValidResetTime.format(TIME_FORMATTER) : "";
        
        // Enhanced pause information
        StringBuilder pauseInfo = new StringBuilder();
        if (isPaused) {
            Duration currentPauseDuration = getCurrentPauseDuration();
            pauseInfo.append("Current pause: ").append(formatDuration(currentPauseDuration)).append("\n");
        }
        
        if (totalPauseDuration.toMillis() > 0) {
            pauseInfo.append("Total pause duration: ").append(formatDuration(totalPauseDuration));
        }
        
        return triggerStatus + pauseStatus + "\n" + triggerCount + "\n" + lastReset + 
               (pauseInfo.length() > 0 ? "\n" + pauseInfo.toString() : "");
    }
      
    
    /**
     * Default GameTick handler that subclasses can override
     */    
    public void onGameTick(GameTick gameTick) {
        // Default implementation does nothing
    }
    
    @Override
    public void reset() {        
        this.reset(true);
    }
    
    /**
     * Resets the condition with optional randomization.
     * Clears pause state and updates trigger times based on the randomize parameter.
     * 
     * @param randomize Whether to apply randomization during reset
     */
    @Override
    public void reset(boolean randomize) {
        // If paused, resume first
        if (isPaused) {
            resume();
        }
        
        // Reset total pause duration
        totalPauseDuration = Duration.ZERO;
        
        // Subclasses should override this to implement specific reset behavior
    }
    
    @Override
    public void hardReset() {
        // Reset the condition state completely
        this.currentValidResetCount = 0;
        this.lastValidResetTime = LocalDateTime.now();
        this.totalPauseDuration = Duration.ZERO;
        
        // Ensure not paused
        if (isPaused) {
            isPaused = false;
            pauseStartTime = null;
        }
        
        // Call normal reset with randomization
        reset(true);
    }
    
    void updateValidReset() {        
        if (isSatisfied()) {
            this.currentValidResetCount++;
            this.lastValidResetTime = LocalDateTime.now();                            
        }
        
    }
       
    /**
     * Gets the next time this time condition will be satisfied, accounting for pauses.
     * When paused, the trigger time is still calculated but effectively frozen until resumed.
     * 
     * @return Optional containing the next trigger time, or empty if not applicable
     */
    @Override
    public Optional<ZonedDateTime> getCurrentTriggerTime() {
        // If can't trigger again, don't provide a trigger time
        if (!canTriggerAgain()) {
            return Optional.empty();
        }
        
        // Calculate next trigger time (subclasses should override this)
        // Note: We don't return empty for paused conditions anymore, instead
        // we use getEffectiveNow() in calculations to freeze progress during pause
        if (isSatisfied()) {
            return Optional.of(getEffectiveNow());
        }
        
        // Default to using the next trigger time with pause handling
        return getNextTriggerTimeWithPause();
    }

    /**
     * Gets the duration until the next trigger time, accounting for pauses.
     * When paused, the duration is calculated from the pause start time, effectively
     * freezing the countdown until the condition is resumed.
     * 
     * @return Optional containing the duration until next trigger, or empty if not applicable
     */
    public Optional<Duration> getDurationUntilNextTrigger() {
        if(!canTriggerAgain()) {
            return Optional.empty(); // No duration if already triggered too often
        }
        
        Optional<ZonedDateTime> nextTrigger = getCurrentTriggerTime();
        if (nextTrigger.isPresent()) {
            // Use effective now for consistent pause behavior
            ZonedDateTime now = getEffectiveNow();
            ZonedDateTime triggerTime = nextTrigger.get();
            
            // If trigger time is in the future, return the duration
            if (triggerTime.isAfter(now)) {
                return Optional.of(Duration.between(now, triggerTime));
            }
            else {
                // If trigger time is in the past, return zero duration
                return Optional.of(Duration.ZERO);
            }
        }
        return Optional.empty();
    }

    /**
     * Calculates progress percentage toward next trigger.
     * If the condition is paused, the progress remains frozen at its current value
     * rather than resetting to zero. This provides a more accurate representation
     * of the condition's state during pauses.
     * 
     * @return Progress percentage (0-100) toward next trigger time
     */
    @Override
    public double getProgressPercentage() {
        if (!canTriggerAgain()) {
            return 0.0; // No progress if already triggered too often
        }
        
        // If already satisfied, return 100%
        if (isSatisfied()) {
            return 100.0;
        }
        
        // Calculate progress based on time until next trigger
        Optional<ZonedDateTime> nextTrigger = getCurrentTriggerTime();
        if (nextTrigger.isPresent()) {
            // When paused, use the pause start time as the reference point
            // to keep progress frozen rather than resetting to 0%
            ZonedDateTime now = getEffectiveNow();
            ZonedDateTime triggerTime = nextTrigger.get();
            
            // If trigger is in the past, it's either 0% or 100%
            if (!triggerTime.isAfter(now)) {
                return isSatisfied() ? 100.0 : 0.0;
            }
            
            // Calculate progress based on reference point and trigger time
            return calculateProgressTowardTrigger(now, triggerTime);
        }
        
        // Default behavior
        return 0.0;
    }

    /**
     * Calculates progress percentage toward a specific trigger time.
     * Base implementation for subclasses to override with specific calculations.
     * This method should account for pause states by using the effective now time.
     * 
     * When the progress calculation is requested during a paused state,
     * the same progress value should be maintained rather than advancing.
     * 
     * @param now Current time (or effective current time during pause)
     * @param triggerTime Target trigger time
     * @return Progress percentage (0-100)
     */
    protected double calculateProgressTowardTrigger(ZonedDateTime now, ZonedDateTime triggerTime) {
        // Default implementation uses a simple linear progress calculation
        // Subclasses should override this to provide more specific implementations
        if (now.isAfter(triggerTime)) {
            return 100.0;
        }
        
        try {
            // Calculate total duration from start to trigger
            Duration totalDuration = Duration.between(getNextTriggerTimeWithPause().orElse(now), triggerTime);
            // Calculate elapsed duration
            Duration elapsedDuration = Duration.between(now, triggerTime);
            
            if (totalDuration.isZero()) {
                return 100.0; // Avoid division by zero
            }
            
            // Calculate progress percentage
            double progress = 100.0 * (1.0 - (elapsedDuration.toMillis() / (double) totalDuration.toMillis()));
            // Ensure progress stays within 0-100 range
            return Math.min(100.0, Math.max(0.0, progress));
        } catch (Exception e) {
            // If any calculation errors occur, return 0%
            return 0.0;
        }
    }

    /**
     * Check if this condition uses randomization
     * @return true if randomization is enabled, false otherwise
     */
    public boolean isUseRandomization() {
        return false; // Default implementation, subclasses should override if needed
    }
     /**
     * Checks if this condition can trigger again (hasn't triggered yet)
     * 
     * @return true if the condition hasn't triggered yet
     */
    public boolean canTriggerAgain(){
        if (maximumNumberOfRepeats <= 0){
            return true;
        }
        if (currentValidResetCount < maximumNumberOfRepeats) {
            return true;
        }
        return false;
    }
    abstract public boolean isSatisfiedAt(ZonedDateTime time);
    
    /**
     * Checks if this condition has already triggered
     * 
     * @return true if the condition has triggered at least once
     */
    public boolean hasTriggered() {
        return currentValidResetCount > 0;
    }
      
    @Override
    public boolean isSatisfied() {
        // A condition cannot be satisfied while paused
        if (isPaused) {
            return false;
        }
        
        // Default implementation defers to subclasses using isSatisfiedAt
        return isSatisfiedAt(getNow());
    }

    /**
     * Gets the estimated time until this time condition will be satisfied.
     * This implementation leverages getCurrentTriggerTime() to provide accurate estimates
     * for time-based conditions, taking into account pause adjustments.
     * 
     * @return Optional containing the estimated duration until satisfaction, or empty if not determinable
     */
    @Override
    public Optional<Duration> getEstimatedTimeWhenIsSatisfied() {
        // If the condition is already satisfied, return zero duration
        if (isSatisfied()) {
            return Optional.of(Duration.ZERO);
        }
        
        // Get the next trigger time, accounting for pauses
        Optional<ZonedDateTime> triggerTime = getNextTriggerTimeWithPause();
        if (!triggerTime.isPresent()) {
            // Try the regular getCurrentTriggerTime as fallback
            triggerTime = getCurrentTriggerTime();
        }
        
        if (triggerTime.isPresent()) {
            ZonedDateTime now = getEffectiveNow();
            Duration duration = Duration.between(now, triggerTime.get());
            
            // Ensure we don't return negative durations
            if (duration.isNegative()) {
                return Optional.of(Duration.ZERO);
            }
            return Optional.of(duration);
        }
        
        // If we can't determine the trigger time, return empty
        return Optional.empty();
    }
}