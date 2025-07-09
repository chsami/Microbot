package net.runelite.client.plugins.microbot.pluginscheduler.condition.time;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * A time condition that triggers exactly once when the target time is reached.
 * After triggering, this condition remains in a "triggered" state until explicitly reset.
 * Perfect for one-time scheduled events or deadlines.
 */
@Slf4j
@EqualsAndHashCode(callSuper = false, exclude = {})
public class SingleTriggerTimeCondition extends TimeCondition {    
    @Getter
    private Duration definedDelay;
    @Getter    
    private long maximumNumberOfRepeats = 1;
    public static String getVersion() {
        return "0.0.1";
    }
 
    
    private static final DateTimeFormatter FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
     public SingleTriggerTimeCondition copy(boolean reset){
        SingleTriggerTimeCondition copy = new SingleTriggerTimeCondition(getNextTriggerTimeWithPause().orElse(getNow()), this.definedDelay, this.maximumNumberOfRepeats);
        if (reset) {
            copy.hardReset();
        }        
        return copy;
    }
    public SingleTriggerTimeCondition copy(){
        SingleTriggerTimeCondition copy = new SingleTriggerTimeCondition(getNextTriggerTimeWithPause().orElse(getNow()), this.definedDelay, this.maximumNumberOfRepeats);
           
        return copy;
    }
    /**
     * Creates a condition that triggers once at the specified time
     * 
     * @param targetTime The time at which this condition should trigger
     */
    public SingleTriggerTimeCondition(ZonedDateTime targetTime, Duration definedDelay, 
            long maximumNumberOfRepeats) {
        super(maximumNumberOfRepeats); // Only allow one trigger
        setNextTriggerTime(targetTime);
        this.definedDelay = definedDelay;
    }
    
    /**
     * Creates a condition that triggers once after the specified delay
     * 
     * @param delaySeconds Number of seconds in the future to trigger
     * @return A new SingleTriggerTimeCondition
     */
    public static SingleTriggerTimeCondition afterDelay(long delaySeconds) {
        ZonedDateTime triggerTime = ZonedDateTime.now(ZoneId.systemDefault())
                .plusSeconds(delaySeconds);        
        return new SingleTriggerTimeCondition(triggerTime ,Duration.ofSeconds(delaySeconds), 1);
    }


    @Override
    public boolean isSatisfied() {
        return isSatisfiedAt(getNextTriggerTimeWithPause().orElse(getNow()));
    }
    @Override
    public boolean isSatisfiedAt(ZonedDateTime triggerTime) {
        if (isPaused()) {
            return false; // Don't trigger if paused
        }
        // If already triggered, return true
        if (hasTriggered()) {
            if (!canTriggerAgain()) {
                return true; // Only return true once after triggering                
            }
            return false; // Return false after reset  and we have triggered before
        }
        
        // Check if current time has passed the target time
        ZonedDateTime now = getNow();
        if (now.isAfter(triggerTime) || now.isEqual(triggerTime)) {
            log.debug("SingleTriggerTimeCondition triggered at: {}", now.format(FORMATTER));
            return true;
        }
        
        return false;
    }

    
    @Override
    public String getDescription() {
        String triggerStatus = hasTriggered() ? "triggered" : "not yet triggered";
        String baseDescription = super.getDescription();
        return String.format("One-time trigger at %s (%s)\n%s", 
                getNextTriggerTimeWithPause().orElse(getNow()).format(FORMATTER), triggerStatus, baseDescription);
    }
    
    /**
     * Returns a detailed description of the single trigger condition with additional status information
     */
    @Override
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        
        ZonedDateTime now = getNow();
        String triggerStatus = hasTriggered() ? "triggered" : "not yet triggered";
        String pauseStatus = isPaused() ? " (PAUSED)" : "";
        
        sb.append("One-time trigger at ").append(getNextTriggerTimeWithPause().orElse(getNow()).format(FORMATTER))
          .append(" (").append(triggerStatus).append(")").append(pauseStatus).append("\n");
        
        if (!hasTriggered() && !isPaused()) {
            if (now.isAfter(getNextTriggerTimeWithPause().orElse(getNow()))) {
                sb.append("Ready to trigger now\n");
            } else {
                Duration timeUntilTrigger = Duration.between(now, getNextTriggerTimeWithPause().orElse(getNow()));
                long seconds = timeUntilTrigger.getSeconds();
                sb.append("Time until trigger: ")
                  .append(String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60))
                  .append("\n");
            }
        } else if (isPaused()) {
            sb.append("Trigger time is paused and will be adjusted when resumed\n");
            Duration currentPauseDuration = Duration.between(pauseStartTime, now);
            sb.append("Current pause duration: ").append(formatDuration(currentPauseDuration)).append("\n");
        }
        
        sb.append("Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
        sb.append(super.getDescription());
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        
        // Basic information
        sb.append("SingleTriggerTimeCondition:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        sb.append("  │ Target Time: ").append(getNextTriggerTimeWithPause().orElse(getNow()).format(dateTimeFormatter)).append("\n");
        sb.append("  │ Time Zone: ").append(getNextTriggerTimeWithPause().orElse(getNow()).getZone().getId()).append("\n");
        
        // Status information
        sb.append("  ├─ Status ──────────────────────────────────\n");
        sb.append("  │ Satisfied: ").append(isSatisfied()).append("\n");
        sb.append("  │ Triggered: ").append(hasTriggered()).append("\n");
        sb.append("  │ Paused: ").append(isPaused()).append("\n");
        
        ZonedDateTime now = getNow();
        // Only show trigger time info if not paused
        if (!hasTriggered() && !isPaused()) {
            if (now.isAfter(getNextTriggerTimeWithPause().orElse(getNow()))) {
                sb.append("  │ Ready to trigger now\n");
            } else {
                Duration timeUntilTrigger = Duration.between(now, getNextTriggerTimeWithPause().orElse(getNow()));
                long seconds = timeUntilTrigger.getSeconds();
                sb.append("  │ Time Until Trigger: ")
                  .append(String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60))
                  .append("\n");
            }
        } else if (isPaused()) {
            sb.append("  │ Trigger time paused and will be adjusted when resumed\n");
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
        }
        sb.append("    Can Trigger Again: ").append(canTriggerAgain()).append("\n");
        
        // If paused, show pause duration
        if (isPaused()) {
            Duration currentPauseDuration = Duration.between(pauseStartTime, getNow());
            sb.append("    Current Pause Duration: ").append(formatDuration(currentPauseDuration)).append("\n");
        }
        if (totalPauseDuration.getSeconds() > 0) {
            sb.append("    Total Pause Duration: ").append(formatDuration(totalPauseDuration)).append("\n");
        }
        
        return sb.toString();
    }
    
    @Override
    public void reset(boolean randomize) {
        if (!isSatisfied()) {
            return;
        }
        currentValidResetCount++;   
        lastValidResetTime = LocalDateTime.now();    
        log.debug("SingleTriggerTimeCondition reset, will trigger again at: {}", 
                getNextTriggerTimeWithPause().orElse(getNow()).format(FORMATTER));
    }
    @Override
    public void hardReset() {
        // Reset the condition state
        this.currentValidResetCount = 0;
        this.lastValidResetTime = LocalDateTime.now();
        setNextTriggerTime(ZonedDateTime.now(ZoneId.systemDefault())
                .plusSeconds(definedDelay.getSeconds()));
    }

    
    @Override
    public double getProgressPercentage() {
        
        ZonedDateTime now = getNow();
        if (now.isAfter(getNextTriggerTimeWithPause().orElse(getNow()))) {
            return 100.0;
        }
        
        // Calculate time progress as percentage
        long totalSeconds = java.time.Duration.between(
                ZonedDateTime.now().withSecond(0).withNano(0), getNextTriggerTimeWithPause().orElse(getNow())).getSeconds();
        long secondsRemaining = java.time.Duration.between(now, getNextTriggerTimeWithPause().orElse(getNow())).getSeconds();
        
        if (totalSeconds <= 0) {
            return 0.0;
        }
        
        double progress = 100.0 * (1.0 - (secondsRemaining / (double) totalSeconds));
        return Math.min(99.9, Math.max(0.0, progress)); // Cap between 0-99.9%
    }
    
  
    
    @Subscribe
    public void onGameTick(GameTick event) {
        // Used to stay registered with the event bus
    }

    @Override
    public Optional<ZonedDateTime> getCurrentTriggerTime() {
        // If already triggered and reset occurred, no future trigger
        if (hasTriggered() && canTriggerAgain()) {
            return Optional.empty();
        }
        
        // If already triggered but not reset, return the target time (in the past)
        if (hasTriggered()) {
            return Optional.of(getNextTriggerTimeWithPause().orElse(getNow()));
        }
        
        // Not triggered yet, return future target time
        return Optional.of(getNextTriggerTimeWithPause().orElse(getNow()));
    }
    @Override
    protected void onResume(Duration pauseDuration) { 
        if (isPaused()) {
            return;
        }
        // getNextTriggerTimeWithPause() provide old next trigger time -> we are resumed..     
        ZonedDateTime nextTriggerTimeWithPauseDuration = getNextTriggerTimeWithPause().orElse(null);
        if (nextTriggerTimeWithPauseDuration != null) {
            nextTriggerTimeWithPauseDuration = nextTriggerTimeWithPauseDuration.plus(pauseDuration);
            // Shift the next trigger time by the pause duration
            setNextTriggerTime(nextTriggerTimeWithPauseDuration);
            log.info("SingleTriggerTimeCondition resumed, next trigger time shifted to: {}", nextTriggerTimeWithPauseDuration);
        }               
    }
}