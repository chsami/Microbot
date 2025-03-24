package net.runelite.client.plugins.microbot.pluginscheduler.api;

import java.util.List;

import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.LogicalCondition;

/**
 * Interface for plugins that want to provide custom stopping conditions.
 * Implement this interface in your plugin to define when the scheduler should stop it.
 */
public interface StoppingConditionProvider {
    
    /**
     * Returns a list of conditions that, when met, should cause the plugin to stop.
     * 
     * @return List of stopping conditions
     */
    List<Condition> getStoppingConditions();
    
    /**
     * Returns a logical condition structure that defines when the plugin should stop.
     * This allows for complex logical combinations (AND, OR, NOT) of conditions.
     * If this method returns null, the conditions from getStoppingConditions() will be
     * combined with AND logic.
     * 
     * @return A logical condition structure, or null to use simple AND logic
     */
    default LogicalCondition getLogicalConditionStructure() {
        return null;
    }
    
    /**
     * Called when conditions are being evaluated.
     * Use this method to update any dynamic condition state if needed.
     */
    default void onConditionCheck() {
        // Optional hook for condition updates
    }
}