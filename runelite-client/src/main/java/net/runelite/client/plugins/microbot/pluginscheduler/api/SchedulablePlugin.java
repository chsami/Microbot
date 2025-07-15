package net.runelite.client.plugins.microbot.pluginscheduler.api;


import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryFinishedEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntrySoftStopEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.plugins.microbot.pluginscheduler.ui.util.SchedulerUIUtils;
import net.runelite.client.plugins.microbot.pluginscheduler.util.SchedulerPluginUtil;

import org.slf4j.event.Level;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;

import java.awt.Component;
import java.awt.Window;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import javax.swing.*;
import javax.swing.border.EmptyBorder;



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
        boolean shouldStop = false;
        if (schedulablePlugin == null) {
            Microbot.log("\n SchedulerPlugin is not loaded. so stopping the current plugin.", Level.INFO);
            shouldStop = true;
            
        }else{
            PluginScheduleEntry currentPlugin =  schedulablePlugin.getCurrentPlugin();
            if (currentPlugin == null) {
                Microbot.log("\n SchedulerPlugin is not running any plugin. so stopping the current plugin.");
                shouldStop = true;
            }else{
                if (currentPlugin.isRunning() && currentPlugin.getPlugin() != null && !currentPlugin.getPlugin().equals(this)) {
                    Microbot.log("\n Current running plugin running by the SchedulerPlugin is not the same as the one being stopped. Stopping current plugin.");
                    shouldStop = true;
                }
            }
        }
        String prefix = "Plugin [" + this.getClass().getSimpleName() + "] finished: ";
        String reasonExt= reason == null ? prefix+"No reason provided" : prefix+reason;
        if (shouldStop){
    
            // If plugin finished unsuccessfully, show a non-blocking notification dialog
            if (!success) {
                Microbot.log("\nPlugin [" + this.getClass().getSimpleName() + "] stopped with error: " + reasonExt, Level.ERROR);
                Microbot.getClientThread().invokeLater(()->{
                    try {
                        SwingUtilities.invokeAndWait(() -> {
                            // Find a parent frame to attach the dialog to
                            Component clientComponent = (Component)Microbot.getClient();
                            Window window = SwingUtilities.getWindowAncestor(clientComponent);
                            // Create message with HTML for proper text wrapping
                            JLabel messageLabel = new JLabel("<html><div style='width: 350px;'>" + 
                                    "Plugin [" + ((Plugin)this).getClass().getSimpleName() + 
                                    "] stopped: " + (reason != null ? reason : "No reason provided") + 
                                    "</div></html>");
                            // Show error message if starting failed
                            JOptionPane.showMessageDialog(
                                window,
                                messageLabel,
                                "Plugin Stopped",
                                JOptionPane.WARNING_MESSAGE
                            );
                        
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        Microbot.log("Dialog display was interrupted: " + e.getMessage(), Level.WARN);
                    } catch (java.lang.reflect.InvocationTargetException e) {
                        Microbot.log("Error displaying plugin stopped dialog: " + e.getCause().getMessage(), Level.ERROR);
                    }
                });
            }
            // Capture the plugin reference explicitly to avoid timing issues
            final Plugin pluginToStop = (Plugin) this;
            Microbot.getClientThread().invokeLater(() -> {            
                Microbot.stopPlugin(pluginToStop);                
            });
            return;
        }else{
            if (success) {
                Microbot.log(reasonExt, Level.INFO);
            } else {
                Microbot.log(reasonExt, Level.ERROR);
            }            
            Microbot.getEventBus().post(new PluginScheduleEntryFinishedEvent(
                (Plugin) this, // "this" will be the plugin instance
                reasonExt,
                success
            ));
            return;
        }
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
    default public boolean allowHardStop(){
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
            List<LockCondition> allLockCondtions = ((LogicalCondition)condition).findAllLockConditions();
            // todo think of only allow one lock condition per plugin in the nested structure... because more makes no sense?
            
            return allLockCondtions.isEmpty() ? null : allLockCondtions.get(0);
                
            
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
        if (stopConditions == null) {
            return null; // No stop conditions defined
        }
        LockCondition lockCondition = getLockCondition( stopConditions);
        
        if (lockCondition != null) {
            return lockCondition.toggleLock();
        }
        return null;
    }
    default public ConfigDescriptor getConfigDescriptor(){
        return null;
    }
    /**
     * Gets the time until the next scheduled plugin will run.
     * This method checks the SchedulerPlugin for the upcoming plugin and calculates
     * the duration until it's scheduled to execute.
     * 
     * @return Optional containing the duration until the next plugin runs, 
     *         or empty if no plugin is upcoming or time cannot be determined
     */
    default Optional<Duration> getTimeUntilUpComingScheduledPlugin() {
        try {
           return SchedulerPluginUtil.getTimeUntilUpComingScheduledPlugin();
            
        } catch (Exception e) {
            Microbot.log("Error getting time until next scheduled plugin: " + e.getMessage(), Level.ERROR);
            return Optional.empty();
        }
    }
    
    /**
     * Gets information about the next scheduled plugin.
     * This method provides both the plugin entry and the time until it runs.
     * 
     * @return Optional containing a formatted string with plugin name and time until run,
     *         or empty if no plugin is upcoming
     */
    default Optional<String> getUpComingScheduledPluginInfo() {
        try {
            return SchedulerPluginUtil.getUpComingScheduledPluginInfo();            
        } catch (Exception e) {
            Microbot.log("Error getting next scheduled plugin info: " + e.getMessage(), Level.ERROR);
            return Optional.empty();
        }
    }
    
    /**
     * Gets the next scheduled plugin entry with its complete information.
     * This provides access to the full PluginScheduleEntry object.
     * 
     * @return Optional containing the next scheduled plugin entry,
     *         or empty if no plugin is upcoming
     */
    default Optional<PluginScheduleEntry> getNextUpComingPluginScheduleEntry() {
        try {
            return SchedulerPluginUtil.getNextUpComingPluginScheduleEntry();
        } catch (Exception e) {
            Microbot.log("Error getting next scheduled plugin entry: " + e.getMessage(), Level.ERROR);
            return Optional.empty();
        }
    }
}