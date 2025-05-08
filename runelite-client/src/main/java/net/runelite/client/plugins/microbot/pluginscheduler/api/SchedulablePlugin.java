package net.runelite.client.plugins.microbot.pluginscheduler.api;


import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryFinishedEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;

import static net.runelite.client.plugins.microbot.Microbot.log;

import java.awt.List;
import org.slf4j.event.Level;
import lombok.extern.java.Log;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;


/**
 * Interface for plugins that want to provide custom stopping conditions and scheduling capabilities.
 * Implement this interface in your plugin to define when the scheduler should stop it or
 * to have your plugin report when it has finished its tasks.
 */


public interface SchedulablePlugin {

       
    default LogicalCondition getStartCondition() {
        return new AndCondition();
    }
    /**
     * Returns a logical condition structure that defines when the plugin should stop.
     * <p>
     * This allows for complex logical combinations (AND, OR, NOT) of conditions.
     * If this method returns null, the conditions from {@link #getStopConditions()}
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
    default LogicalCondition getStopCondition(){
        return new AndCondition();
    }
    
    
     /**
     * Called periodically when conditions are being evaluated by the scheduler.
     * <p>
     * Use this method to update any dynamic condition state if needed. This hook
     * allows plugins to refresh condition values or state before they're evaluated.
     * This method is called approximately once per second while the plugin is running.
     */
    default void onStopConditionCheck() {
        // Optional hook for condition updates
    }
    /**
     * Handles the {@link PluginScheduleEntry} posted by the scheduler when stop conditions are met.
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
    
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event);
    
    /**
     * Handles the {@link PluginScheduleEntryFinishedEvent} posted when a plugin self-reports completion.
     * <p>
     * This event handler is automatically called when a plugin posts a PluginScheduleEntryFinishedEvent
     * to indicate it has completed its task and should be stopped. The default implementation
     * allows the scheduler to know when to stop the plugin even when stop conditions aren't met.
     * <p>
     * Plugin developers should generally not override this method unless they need
     * custom finished behavior.
     * <p>
     * Note: This is an EventBus subscriber method and requires the implementing
     * plugin to be registered with the EventBus for it to be called.
     *
     * @param event The finished event containing the plugin reference and reason information
     */
    default public void onPluginScheduleEntryFinishedEvent(PluginScheduleEntryFinishedEvent event) {
        // Default implementation does nothing - handled by scheduler
    }
    
    /**
     * Allows a plugin to report that it has finished its task and is ready to be stopped.
     * Use this method when your plugin has completed its primary task successfully or
     * encountered a situation where it should be stopped even if the configured stop
     * conditions haven't been met yet.
     * 
     * @param reason A description of why the plugin is finished
     * @param success Whether the task was completed successfully
     */
    default public void reportFinished(String reason, boolean success) {
        SchedulerPlugin schedulablePlugin =  (SchedulerPlugin) Microbot.getPlugin(SchedulerPlugin.class.getName());
        if (schedulablePlugin == null) {
            Microbot.log("\n SchedulerPlugin is not loaded. so stopping the current plugin.", Level.INFO);
            Microbot.getClientThread().invoke(()-> Microbot.stopPlugin((Plugin)this));
            return;
        }
        PluginScheduleEntry currentPlugin =  schedulablePlugin.getCurrentPlugin();
        if (currentPlugin == null) {
            Microbot.log("\n SchedulerPlugin is not running any plugin. so stopping the current plugin.");
            Microbot.getClientThread().invoke(()-> Microbot.stopPlugin((Plugin)this));
            return;
        }
        if (currentPlugin.isRunning() && currentPlugin.getPlugin() != null && !currentPlugin.getPlugin().equals(this)) {
            Microbot.log("\nCurrent running plugin running by the SchedulerPlugin is not the same as the one being stopped. Stopping current plugin.");
            Microbot.getClientThread().invoke(()-> Microbot.stopPlugin((Plugin)this));
            return;
        }
        String prefix = "Plugin [" + this.getClass().getSimpleName() + "] finished: ";
        String reasonExt= reason == null ? prefix+"No reason provided" : prefix+reason;


        Microbot.getEventBus().post(new PluginScheduleEntryFinishedEvent(
            (Plugin) this, // "this" will be the plugin instance
            reasonExt,
            success
        ));
    }
    
    /**
     * Determines if this plugin can be forcibly terminated by the scheduler through a hard stop.
     * 
     * A hard stop means the scheduler can immediately terminate the plugin's execution
     * without waiting for the plugin to reach a safe stopping point.
     * When false (default), the scheduler will only perform soft stops, allowing the plugin
     * to terminate gracefully at designated checkpoints.
     * 
     * @return true if this plugin supports being forcibly terminated, false otherwise
     */
    default public boolean isHardStoppable(){
        return false;
    }

    /**
     * Returns the lock condition that can be used to prevent the plugin from being stopped.
     * The lock condition is stored in the plugin's stop condition structure and can be
     * toggled to prevent the plugin from being stopped during critical operations.
     * 
     * @return The lock condition for this plugin, or null if not present
     */
    default LockCondition getLockCondition(Condition stopConditions) {
        
        if (stopConditions != null) {
            return findLockCondition(stopConditions);
        }
        return null;
    }
    
    /**
     * Recursively searches for a LockCondition within a logical condition structure.
     * 
     * @param condition The condition to search within
     * @return The first LockCondition found, or null if none exists
     */
    default LockCondition findLockCondition(Condition condition) {
        if (condition instanceof LockCondition) {
            return (LockCondition) condition;
        }
        
        if (condition instanceof LogicalCondition) {
            LogicalCondition logicalCondition = (LogicalCondition) condition;
            for (Condition subCondition : logicalCondition.getConditions()) {
                LockCondition lockCondition = findLockCondition(subCondition);
                if (lockCondition != null) {
                    return lockCondition;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Checks if the plugin is currently locked from being stopped.
     * 
     * @return true if the plugin is locked, false otherwise
     */
    default boolean isLocked(Condition stopConditions) {
        LockCondition lockCondition = getLockCondition(stopConditions);
        return lockCondition != null && lockCondition.isLocked();
    }
    
    /**
     * Locks the plugin, preventing it from being stopped regardless of other conditions.
     * Use this during critical operations where the plugin should not be interrupted.
     * 
     * @return true if the plugin was successfully locked, false if no lock condition exists
     */
    default boolean lock(Condition stopConditions) {
        LockCondition lockCondition = getLockCondition(stopConditions);
        if (lockCondition != null) {
            lockCondition.lock();
            return true;
        }
        return false;
    }
    
    /**
     * Unlocks the plugin, allowing it to be stopped when stop conditions are met.
     * 
     * @return true if the plugin was successfully unlocked, false if no lock condition exists
     */
    default boolean unlock(Condition stopConditions) {
        LockCondition lockCondition = getLockCondition(stopConditions);
        if (lockCondition != null) {
            lockCondition.unlock();
            return true;
        }
        return false;
    }
    
    /**
     * Toggles the lock state of the plugin.
     * 
     * @return The new lock state (true if locked, false if unlocked), or null if no lock condition exists
     */
    default Boolean toggleLock(Condition stopConditions) {
        LockCondition lockCondition = getLockCondition( stopConditions);
        if (lockCondition != null) {
            return lockCondition.toggleLock();
        }
        return null;
    }
    default public ConfigDescriptor getConfigDescriptor(){
        return null;
    }

}