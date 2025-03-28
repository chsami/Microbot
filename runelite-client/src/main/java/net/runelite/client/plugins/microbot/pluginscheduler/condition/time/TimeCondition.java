package net.runelite.client.plugins.microbot.pluginscheduler.condition.time;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Abstract base class for all time-based conditions.
 * Provides common functionality for time calculations and event handling.
 */
@Slf4j
public abstract class TimeCondition implements Condition {
    
    @Getter
    protected boolean registered = false;
    protected static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");
    
    /**
     * Gets the current date and time in the system default time zone
     * 
     * @return The current ZonedDateTime
     */
    protected ZonedDateTime getNow() {
        return ZonedDateTime.now(ZoneId.systemDefault());
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.TIME;
    }
    
  
    
    
    /**
     * Default implementation for calculating progress percentage
     * Subclasses should override for more specific calculations
     */
    @Override
    public double getProgressPercentage() {
        return isSatisfied() ? 100.0 : 0.0;
    }
    
    /**
     * Default GameTick handler that subclasses can override
     */    
    public void onGameTick(GameTick gameTick) {
        // Default implementation does nothing
    }
    
    @Override
    public void reset() {
        reset(false);
    }
}