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

    public static String getVersion() {
        return "0.0.1";
    }
    @Getter
    private final ZonedDateTime targetTime;
    
    
    private static final DateTimeFormatter FORMATTER = 
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    /**
     * Creates a condition that triggers once at the specified time
     * 
     * @param targetTime The time at which this condition should trigger
     */
    public SingleTriggerTimeCondition(ZonedDateTime targetTime) {
        super(1); // Only allow one trigger
        this.targetTime = targetTime;
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
        return new SingleTriggerTimeCondition(triggerTime);
    }

    @Override
    public boolean isSatisfied() {
        // If already triggered, return true
        if (hasTriggered()) {
            if (!canTriggerAgain()) {
                return true; // Only return true once after triggering                
            }
            return false; // Return false after reset  and we have triggered before
        }
        
        // Check if current time has passed the target time
        ZonedDateTime now = getNow();
        if (now.isAfter(targetTime) || now.isEqual(targetTime)) {
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
                targetTime.format(FORMATTER), triggerStatus, baseDescription);
    }
    
    /**
     * Returns a detailed description of the single trigger condition with additional status information
     */
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        
        ZonedDateTime now = getNow();
        String triggerStatus = hasTriggered() ? "triggered" : "not yet triggered";
        
        sb.append("One-time trigger at ").append(targetTime.format(FORMATTER))
          .append(" (").append(triggerStatus).append(")\n");
        
        if (!hasTriggered()) {
            if (now.isAfter(targetTime)) {
                sb.append("Ready to trigger now\n");
            } else {
                Duration timeUntilTrigger = Duration.between(now, targetTime);
                long seconds = timeUntilTrigger.getSeconds();
                sb.append("Time until trigger: ")
                  .append(String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60))
                  .append("\n");
            }
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
        sb.append("  │ Target Time: ").append(targetTime.format(dateTimeFormatter)).append("\n");
        sb.append("  │ Time Zone: ").append(targetTime.getZone().getId()).append("\n");
        
        // Status information
        sb.append("  ├─ Status ──────────────────────────────────\n");
        sb.append("  │ Satisfied: ").append(isSatisfied()).append("\n");
        sb.append("  │ Triggered: ").append(hasTriggered()).append("\n");
        
        ZonedDateTime now = getNow();
        if (!hasTriggered()) {
            if (now.isAfter(targetTime)) {
                sb.append("  │ Ready to trigger now\n");
            } else {
                Duration timeUntilTrigger = Duration.between(now, targetTime);
                long seconds = timeUntilTrigger.getSeconds();
                sb.append("  │ Time Until Trigger: ")
                  .append(String.format("%02d:%02d:%02d", seconds / 3600, (seconds % 3600) / 60, seconds % 60))
                  .append("\n");
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
        }
        sb.append("    Can Trigger Again: ").append(canTriggerAgain()).append("\n");
        
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
                targetTime.format(FORMATTER));
    }

    
    @Override
    public double getProgressPercentage() {
        
        ZonedDateTime now = getNow();
        if (now.isAfter(targetTime)) {
            return 100.0;
        }
        
        // Calculate time progress as percentage
        long totalSeconds = java.time.Duration.between(
                ZonedDateTime.now().withSecond(0).withNano(0), targetTime).getSeconds();
        long secondsRemaining = java.time.Duration.between(now, targetTime).getSeconds();
        
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
            return Optional.of(targetTime);
        }
        
        // Not triggered yet, return future target time
        return Optional.of(targetTime);
    }
}