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
    /**
     * Resets the condition to its initial state.
     * <p>
     * For example, in a time-based condition, calling this method
     * will update the reference timestamp used for calculating
     * time intervals.
     */
    void reset();
    
    default void unregisterEvents(){

    }

    default void onStatChanged(StatChanged event) {
        // This event handler is called whenever a skill stat changes
        // Useful for skill-based conditions
    }
    default void onItemContainerChanged(ItemContainerChanged event) {
        // This event handler is called whenever inventory or bank contents change
        // Useful for item-based conditions
    }

    /**
     * Returns the progress percentage for this condition (0-100).
     * For simple conditions that are either met or not met, this will return 0 or 100.
     * For conditions that track progress (like XP conditions), this will return a value between 0 and 100.
     * 
     * @return Percentage of condition completion (0-100)
     */
    default double getProgressPercentage() {
        // Default implementation returns 0 for not met, 100 for met
        return isMet() ? 100.0 : 0.0;
    }
    
    /**
     * Gets the total number of leaf conditions in this condition tree.
     * For simple conditions, this is 1.
     * For logical conditions, this is the sum of all contained conditions' counts.
     *
     * @return Total number of leaf conditions in this tree
     */
    default int getTotalConditionCount() {
        return 1; // Simple conditions count as 1
    }
    
    /**
     * Gets the number of leaf conditions that are currently met.
     * For simple conditions, this is 0 or 1.
     * For logical conditions, this is the sum of all contained conditions' met counts.
     *
     * @return Number of met leaf conditions in this tree
     */
    default int getMetConditionCount() {
        return isMet() ? 1 : 0; // Simple conditions return 1 if met, 0 otherwise
    }
    
    /**
     * Generates detailed status information for this condition and any nested conditions.
     * 
     * @param indent Current indentation level for nested formatting
     * @param showProgress Whether to include progress percentage in the output
     * @return A string with detailed status information
     */
    default String getStatusInfo(int indent, boolean showProgress) {
        StringBuilder sb = new StringBuilder();
        
        String indentation = " ".repeat(indent);
        boolean isMet = isMet();
        
        sb.append(indentation)
          .append(getDescription())
          .append(" [")
          .append(isMet ? "MET" : "NOT MET")
          .append("]");
        
        if (showProgress) {
            double progress = getProgressPercentage();
            if (progress > 0 && progress < 100) {
                sb.append(" (").append(String.format("%.1f%%", progress)).append(")");
            }
        }
        
        return sb.toString();
    }
    
}