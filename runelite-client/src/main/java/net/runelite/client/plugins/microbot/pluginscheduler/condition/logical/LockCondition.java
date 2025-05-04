package net.runelite.client.plugins.microbot.pluginscheduler.condition.logical;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;

/**
 * A condition that can be manually locked/unlocked by a plugin.
 * When locked, the condition is always unsatisfied regardless of other conditions.
 * This can be used to prevent a plugin from being stopped during critical operations.
 */
@Slf4j
@EqualsAndHashCode(callSuper = false)
public class LockCondition implements Condition {
    
    private final AtomicBoolean locked = new AtomicBoolean(false);
    @Getter
    private final String reason;
    
    /**
     * Creates a new lock condition with a default reason.
     */
    public LockCondition() {
        this("Plugin is in a critical operation");
    }
      /**
     * Creates a new lock condition with a default reason.
     */
    public LockCondition(boolean defaultLock) {
        this("Plugin is in a critical operation");
        this.locked.set(defaultLock);        
    }
    
    /**
     * Creates a new lock condition with the specified reason.
     * 
     * @param reason The reason why the plugin is locked
     */
    public LockCondition(String reason) {
        this.reason = reason;
    }

    /**
     * Creates a new LockCondition with the specified reason and initial lock state.
     *
     * @param reason The reason or description for this lock condition
     * @param defaultLock The initial state of the lock (true for locked, false for unlocked)
     */
    public LockCondition(String reason, boolean defaultLock) {
        this.reason = reason;
        this.locked.set(defaultLock);
    }
    
    /**
     * Locks the condition, preventing the plugin from being stopped.
     */
    public void lock() {
        boolean wasLocked = locked.getAndSet(true);
        if (!wasLocked) {
            log.debug("LockCondition locked: {}", reason);
        }
    }
    
    /**
     * Unlocks the condition, allowing the plugin to be stopped.
     */
    public void unlock() {
        boolean wasLocked = locked.getAndSet(false);
        if (wasLocked) {
            log.debug("LockCondition unlocked: {}", reason);
        }
    }
    
    /**
     * Toggles the lock state.
     * 
     * @return The new lock state (true if locked, false if unlocked)
     */
    public boolean toggleLock() {
        boolean newState = !locked.get();
        locked.set(newState);        
        return newState;
    }
    
    /**
     * Checks if the condition is currently locked.
     * 
     * @return true if locked, false otherwise
     */
    public boolean isLocked() {
        return locked.get();
    }
    
    @Override
    public boolean isSatisfied() {
        // If locked, the condition is NOT satisfied, which prevents stopping
        return !isLocked();
    }
    
    @Override
    public String getDescription() {
        return "Lock Condition: " + (isLocked() ? "\nLOCKED - " + reason : "\nUNLOCKED");
    }
    
    @Override
    public String getDetailedDescription() {
        return getDescription();
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.LOGICAL;
    }
    
    @Override
    public void reset(boolean randomize) {
        // Reset does nothing by default - lock state is controlled manually
    }
    
    @Override
    public Optional<ZonedDateTime> getCurrentTriggerTime() {
        // Lock conditions don't have a specific trigger time
        return Optional.empty();
    }
}