package net.runelite.client.plugins.microbot.pluginscheduler.condition;

import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;

/**
 * Base interface for script execution conditions.
 * Provides common functionality for condition checking and configuration.
 */
public interface Condition {
    /**
     * Checks if the condition is currently met
     * @return true if condition is satisfied, false otherwise
     */
    boolean isMet();
    
    /**
     * Returns a human-readable description of this condition
     * @return description string
     */
    String getDescription();
    
    /**
     * Returns the type of this condition
     * @return ConditionType enum value
     */
    ConditionType getType();

    default void onStatChanged(StatChanged event) {
        // This event handler is called whenever a skill stat changes
        // Useful for skill-based conditions
    }
    default void onItemContainerChanged(ItemContainerChanged event) {
        // This event handler is called whenever inventory or bank contents change
        // Useful for item-based conditions
    }
}