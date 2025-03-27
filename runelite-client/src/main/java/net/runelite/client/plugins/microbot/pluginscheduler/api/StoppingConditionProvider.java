package net.runelite.client.plugins.microbot.pluginscheduler.api;

import java.util.List;



import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.ScheduledStopEvent;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.eventbus.Subscribe;
/**
 * Interface for plugins that want to provide custom stopping conditions.
 * Implement this interface in your plugin to define when the scheduler should stop it.
 */

public interface StoppingConditionProvider {
    
    /**
     * Returns a list of conditions that, when met, should cause the plugin to stop.
     * <p>
     * This is the primary method for defining simple stopping conditions. For more
     * complex logical relationships between conditions, use {@link #getLogicalConditionStructure()}.
     * 
     * @return List of stopping conditions that will be evaluated by the scheduler
     */
    List<Condition> getStoppingConditions();
    
    /**
     * Returns a logical condition structure that defines when the plugin should stop.
     * <p>
     * This allows for complex logical combinations (AND, OR, NOT) of conditions.
     * If this method returns null, the conditions from {@link #getStoppingConditions()}
     * will be combined with AND logic (all conditions must be met).
     * <p>
     * Example for creating a complex condition: "(A OR B) AND C":
     * <pre>
     * &#64;Override
     * public LogicalCondition getLogicalConditionStructure() {
     *     AndCondition root = new AndCondition();
     *     OrCondition orGroup = new OrCondition();
     *     
     *     orGroup.addCondition(conditionA);
     *     orGroup.addCondition(conditionB);
     *     
     *     root.addCondition(orGroup);
     *     root.addCondition(conditionC);
     *     
     *     return root;
     * }
     * </pre>
     * 
     * @return A logical condition structure, or null to use simple AND logic
     */
    default LogicalCondition getLogicalConditionStructure() {
        return null;
    }
     /**
     * Called periodically when conditions are being evaluated by the scheduler.
     * <p>
     * Use this method to update any dynamic condition state if needed. This hook
     * allows plugins to refresh condition values or state before they're evaluated.
     * This method is called approximately once per second while the plugin is running.
     */
    default void onConditionCheck() {
        // Optional hook for condition updates
    }
    /**
     * Handles the {@link ScheduledStopEvent} posted by the scheduler when stop conditions are met.
     * <p>
     * This event handler is automatically called when the scheduler determines that
     * all required conditions for stopping have been met. The default implementation 
     * calls {@link Microbot#stopPlugin(net.runelite.client.plugins.Plugin)} to gracefully
     * stop the plugin.
     * <p>
     * Plugin developers should generally not override this method unless they need
     * custom stop behavior. If overridden, make sure to either call the default 
     * implementation or properly stop the plugin.
     * <p>
     * Note: This is an EventBus subscriber method and requires the implementing
     * plugin to be registered with the EventBus for it to be called.
     *
     * @param event The stop event containing the plugin reference that should be stopped
     */
    
    public void onScheduledStopEvent(ScheduledStopEvent event);
    

}