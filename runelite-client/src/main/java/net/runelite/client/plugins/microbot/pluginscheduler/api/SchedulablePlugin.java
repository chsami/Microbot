package net.runelite.client.plugins.microbot.pluginscheduler.api;


import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.ExecutionResult;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryMainTaskFinishedEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPostScheduleTaskEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPostScheduleTaskFinishedEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPreScheduleTaskEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryPreScheduleTaskFinishedEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.AbstractPrePostScheduleTasks;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.pluginscheduler.util.SchedulerPluginUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.event.Level;

import com.google.common.eventbus.Subscribe;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;

import java.awt.Component;
import java.awt.Window;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.swing.*;



/**
 * Interface for plugins that want to provide custom stopping conditions and scheduling capabilities.
 * Implement this interface in your plugin to define when the scheduler should stop it or
 * to have your plugin report when it has finished its tasks.
 */


public interface SchedulablePlugin {

    Logger log = LoggerFactory.getLogger(SchedulablePlugin.class);

       
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
    @Subscribe
    default public void onPluginScheduleEntryPostScheduleTaskEvent(PluginScheduleEntryPostScheduleTaskEvent event){
        if (event.getPlugin() == this) {
            log.info("Plugin must implement it onPluginScheduleEntryPostScheduleTaskEvent method to handle post-schedule tasks.");
        } 
    }
    /*
     * Use {@link onPluginScheduleEntryPostScheduleTaskEvent} instead.
     */
    @Deprecated
    @Subscribe
    default public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntryPostScheduleTaskEvent event){
        log.warn("onPluginScheduleEntrySoftStopEvent is deprecated. Use onPluginScheduleEntryPostScheduleTaskEvent instead.");
        if (event.getPlugin() == this) {
            log.info("Plugin must implement it onPluginScheduleEntryPostScheduleTaskEvent method to handle post-schedule tasks.");
        }
    }
    
    /**
     * Handles the {@link PluginScheduleEntryPreScheduleTaskEvent} posted by the scheduler when a plugin should start pre-schedule tasks.
     * <p>
     * This event handler is called when the scheduler wants to trigger pre-schedule tasks for a plugin. 
     * The default implementation will run pre-schedule tasks (if available) and then execute the provided script callback.
     * <p>
     * The plugin should respond with a {@link PluginScheduleEntryPreScheduleTaskFinishedEvent} when pre-schedule tasks are complete.
     * <p>
     * Note: This is an EventBus subscriber method and requires the implementing
     * plugin to be registered with the EventBus for it to be called.
     *
     * @param event The pre-schedule task event containing the plugin reference that should start pre-schedule tasks
     * @param scriptSetupAndStartCallback Callback to execute after pre-schedule tasks complete (typically script setup and start)
     */
    default public void executePreScheduleTasks(Runnable postTaskCallback) {         
      
        
        AbstractPrePostScheduleTasks prePostTasks = getPrePostScheduleTasks();        
        if (prePostTasks != null) {
            // Plugin has pre/post tasks and is under scheduler control
            log.info("Plugin {} starting with pre-schedule tasks", this.getClass().getSimpleName());
            
            try {
                // Execute pre-schedule tasks with callback to start main script
                prePostTasks.executePreScheduleTasks(() -> {
                    log.info("Pre-Schedule Tasks completed successfully for {}", this.getClass().getSimpleName());
                    if (postTaskCallback != null) {
                        postTaskCallback.run(); // Execute script setup and start
                    }
                    // Report pre-schedule task completion - the scheduler will transition to RUNNING_PLUGIN state
                    Microbot.getEventBus().post(new PluginScheduleEntryPreScheduleTaskFinishedEvent(
                        (Plugin) this, ExecutionResult.SUCCESS, "Pre-schedule tasks completed successfully"));
                });
            } catch (Exception e) {
                log.error("Error during Pre-Schedule Tasks for {}", this.getClass().getSimpleName(), e);
                // Report pre-schedule task failure
                Microbot.getEventBus().post(new PluginScheduleEntryPreScheduleTaskFinishedEvent(
                    (Plugin) this, ExecutionResult.HARD_FAILURE, "Pre-schedule tasks failed: " + e.getMessage()));
            }
        } else {
            // No pre-schedule tasks or not under scheduler control - execute callback immediately
            log.info("Plugin {} has no pre-schedule tasks - executing callback immediately", this.getClass().getSimpleName());
            
            if (postTaskCallback != null) {
                postTaskCallback.run(); // Execute script setup,etc, post pre schedule tasks action
            }
            
            // Report completion immediately
            Microbot.getEventBus().post(new PluginScheduleEntryPreScheduleTaskFinishedEvent(
                (Plugin) this, ExecutionResult.SUCCESS, "No pre-schedule tasks - callback executed successfully"));
        }
    }


      /**
     * Tests only the post-schedule tasks functionality.
     * This method demonstrates how post-schedule tasks work and logs the results.
     */
    default public void executePostScheduleTasks(Runnable postTaskExecutionCallback) {
        log.info("Post-Schedule Tasks execution...");
        AbstractPrePostScheduleTasks prePostScheduleTasks = getPrePostScheduleTasks();
        if (prePostScheduleTasks == null) {
            log.warn("PrePostScheduleTasks not initialized - cannot test");
            if(postTaskExecutionCallback!= null) postTaskExecutionCallback.run();                        
            return;
        }
        
        try {
            if (prePostScheduleTasks.isPostScheduleRunning()) {
                log.warn("Post-Schedule Tasks are already running. Cannot start again.");
                return;
            }
            // Execute only post-schedule tasks using the public API
            prePostScheduleTasks.executePostScheduleTasks(() -> {
                log.info("Post-Schedule Tasks completed successfully");
                if(postTaskExecutionCallback!= null) postTaskExecutionCallback.run();                
            });
        } catch (Exception e) {
            log.error("Error during Post-Schedule Tasks test", e);            
        }
    }
    
    /**
     * Allows a plugin to report that it has finished its task and is ready to be stopped.
     * Use this method when your plugin has completed its primary task successfully or
     * encountered a situation where it should be stopped even if the configured stop
     * conditions haven't been met yet.
     * 
     * @param reason A description of why the plugin is finished
     * @param result The execution result (SUCCESS, SOFT_FAILURE, or HARD_FAILURE)
     */
    private String reportFinished_internal(String reason, boolean success) {
        if ( this instanceof Plugin && !Microbot.isPluginEnabled((Plugin)this)){
            log.warn("Plugin {} is not enabled, cannot report finished", this.getClass().getSimpleName());
            return null;
        };
        SchedulerPlugin schedulablePlugin =  (SchedulerPlugin) Microbot.getPlugin(SchedulerPlugin.class.getName());
        
        boolean shouldStop = false;
        if (schedulablePlugin == null) {
            Microbot.log("\n SchedulerPlugin is not loaded. so stopping the current plugin.", Level.INFO);
            shouldStop = true;
            
        }else{
            PluginScheduleEntry currentPlugin =  schedulablePlugin.getCurrentPlugin();
            if (currentPlugin == null) {
                Microbot.log("\n\t SchedulerPlugin is not running any plugin. so stopping the current plugin.");
                shouldStop = true;
            }else{
                if (currentPlugin.isRunning() && currentPlugin.getPlugin() != null && !currentPlugin.getPlugin().equals(this)) {
                    Microbot.log("\n\t Current running plugin running by the SchedulerPlugin is not the same as the one being stopped. Stopping current plugin.");
                    shouldStop = true;
                }
            }
        }
        String configGrpName  = "";
        if(getConfigDescriptor()!=null){
            configGrpName = getConfigDescriptor().getGroup().value();
        }
        boolean isSchedulerMode = AbstractPrePostScheduleTasks.isScheduleMode(this,configGrpName);
        log.info("test if plugin is in:\n\t\tscheduler mode: {} -- should stop {}", isSchedulerMode, shouldStop);
        String prefix = "Plugin [" + this.getClass().getSimpleName() + "] finished: ";
        String reasonExt= reason == null ? prefix+"No reason provided" : prefix+reason;
        if (!isSchedulerMode){    
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
            return reasonExt;
        }else{
            if (success) {
                log.info(reasonExt);
            } else {
                log.error(reasonExt);
            }                    
            return reasonExt;
        }
    }
    /**
     * Reports that the plugin has finished its main task.
     * 
     * @param reason A description of why the plugin finished
     * @param result The execution result (SUCCESS, SOFT_FAILURE, or HARD_FAILURE)
     */
    default public void reportFinished(String reason, ExecutionResult result) {
        String reasonExt = reportFinished_internal(reason, result.isSuccess());
        if (reasonExt == null) {
            return; // Plugin is not enabled or scheduler is not running
        }
        Microbot.getEventBus().post(new PluginScheduleEntryMainTaskFinishedEvent(
                (Plugin) this, // "this" will be the plugin instance
                reasonExt,
                result
            )
        );
    }

    /**
     * @deprecated Use {@link #reportFinished(String, ExecutionResult)} instead for granular result reporting
     */
    @Deprecated
    default public void reportFinished(String reason, boolean success) {
        ExecutionResult result = success ? ExecutionResult.SUCCESS : ExecutionResult.HARD_FAILURE;
        reportFinished(reason, result);
    }
    /**
     * Reports that the plugin's post-schedule tasks have finished.
     * 
     * @param reason A description of the completion
     * @param result The execution result (SUCCESS, SOFT_FAILURE, or HARD_FAILURE)
     */
    default public void reportPostScheduleTaskFinished(String reason, ExecutionResult result) {        
        String reasonExt = reportFinished_internal(reason, result.isSuccess());
        if (reasonExt == null) {
            return; // Plugin is not enabled or scheduler is not running
        }
        Microbot.getEventBus().post(new PluginScheduleEntryPostScheduleTaskFinishedEvent(
                (Plugin) this, // "this" will be the plugin instance
                result,
                reasonExt
            )
        );
    }

    /**
     * @deprecated Use {@link #reportPostScheduleTaskFinished(String, ExecutionResult)} instead for granular result reporting
     */
    @Deprecated
    default public void reportPostScheduleTaskFinished(String reason, boolean success) {        
        ExecutionResult result = success ? ExecutionResult.SUCCESS : ExecutionResult.HARD_FAILURE;
        reportPostScheduleTaskFinished(reason, result);
    }
    /**
     * Reports that the plugin's pre-schedule tasks have finished.
     * 
     * @param reason A description of the completion
     * @param result The execution result (SUCCESS, SOFT_FAILURE, or HARD_FAILURE)
     */
    default public void reportPreScheduleTaskFinished(String reason, ExecutionResult result) {
        if (!result.isSuccess()) {
            reason = reportFinished_internal(reason, result.isSuccess());
        }
        if (reason == null) {
            return; // Plugin is not enabled or scheduler is not running
        }
        Microbot.getEventBus().post(new PluginScheduleEntryPreScheduleTaskFinishedEvent(
                (Plugin) this, // "this" will be the plugin instance
                result,
                reason
            )
        );
    }

    /**
     * @deprecated Use {@link #reportPreScheduleTaskFinished(String, ExecutionResult)} instead for granular result reporting
     */
    @Deprecated
    default public void reportPreScheduleTaskFinished(String reason, boolean success) {
        ExecutionResult result = success ? ExecutionResult.SUCCESS : ExecutionResult.HARD_FAILURE;
        reportPreScheduleTaskFinished(reason, result);
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
    default Boolean toggleLock(Condition anyCondition) {
        if (anyCondition == null) {
            return null; // No stop conditions defined
        }
        LockCondition lockCondition = getLockCondition( anyCondition);
        
        if (lockCondition != null) {
            return lockCondition.toggleLock();
        }
        return null;
    }
    
    /**
     * Unlocks all lock conditions in both start and stop conditions.
     * This utility function provides defensive unlocking of all lock conditions
     * to prevent plugins from getting stuck in locked states.
     */
    default void unlockAllConditions() {
        unlockAllStartConditions();
        unlockAllStopConditions();
    }
    
    /**
     * Unlocks all lock conditions in the start conditions.
     * Recursively searches through the condition structure to find and unlock all lock conditions.
     */
    default void unlockAllStartConditions() {
        LogicalCondition startCondition = getStartCondition();
        if (startCondition != null) {
            List<LockCondition> allLockConditions = startCondition.findAllLockConditions();
            for (LockCondition lockCondition : allLockConditions) {
                if (lockCondition.isLocked()) {
                    lockCondition.unlock();
                }
            }
        }
    }
    
    /**
     * Unlocks all lock conditions in the stop conditions.
     * Recursively searches through the condition structure to find and unlock all lock conditions.
     */
    default void unlockAllStopConditions() {
        LogicalCondition stopCondition = getStopCondition();
        if (stopCondition != null) {
            List<LockCondition> allLockConditions = stopCondition.findAllLockConditions();
            for (LockCondition lockCondition : allLockConditions) {
                if (lockCondition.isLocked()) {
                    lockCondition.unlock();
                }
            }
        }
    }
    
    /**
     * Gets all lock conditions from both start and stop conditions.
     * 
     * @return List of all lock conditions found in the plugin's condition structure
     */
    default List<LockCondition> getAllLockConditions() {
        List<LockCondition> allLocks = new ArrayList<>();
        
        // get start condition locks
        LogicalCondition startCondition = getStartCondition();
        if (startCondition != null) {
            allLocks.addAll(startCondition.findAllLockConditions());
        }
        
        // get stop condition locks
        LogicalCondition stopCondition = getStopCondition();
        if (stopCondition != null) {
            allLocks.addAll(stopCondition.findAllLockConditions());
        }
        
        return allLocks;
    }
    
        /**
     * Provides the configuration descriptor for scheduler integration and per-entry configuration management.
     * <p>
     * <strong>Purpose:</strong> Enables the {@link SchedulerPlugin} to manage separate configuration instances
     * for each {@link net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry}.
     * <p>
     * <strong>Schedule Mode Detection:</strong> {@link AbstractPrePostScheduleTasks} uses this method:
     * <ul>
     *   <li>Non-null return → Scheduler mode (managed by SchedulerPlugin)</li>
     *   <li>Null return → Manual mode (direct plugin execution)</li>
     * </ul>
     * <p>
     * <strong>Benefits:</strong> Same plugin, different configurations per schedule slot; centralized management through scheduler UI.
     *
     * @return The configuration descriptor for per-entry configuration, or null if not supported
     * @see net.runelite.client.config.ConfigDescriptor
     * @see SchedulerPlugin
     * @see AbstractPrePostScheduleTasks#isScheduleMode()
     */
    default public ConfigDescriptor getConfigDescriptor(){
        // Default implementation returns null, subclasses should override if they support configuration
        return null;
    }

    /**
     * Returns the pre/post schedule tasks instance for scheduler integration.
     * <p>
     * <strong>Purpose:</strong> Provides setup/cleanup tasks and schedule mode detection for the {@link SchedulerPlugin}.
     * <p>
     * <strong>Integration:</strong> SchedulerPlugin uses this to:
     * <ul>
     *   <li>Execute pre-schedule tasks before plugin start</li>
     *   <li>Execute post-schedule tasks during plugin shutdown</li>
     *   <li>Detect scheduler vs manual operation mode</li>
     * </ul>
     * <p>
     * <strong>Schedule Mode Detection:</strong> Uses {@link #getConfigDescriptor()} and event context
     * to determine if plugin is under scheduler control.
     *
     * @return The pre/post schedule tasks instance, or null if not supported
     * @see AbstractPrePostScheduleTasks
     * @see AbstractPrePostScheduleTasks#isScheduleMode()
     * @see SchedulerPlugin
     */
    @Nullable
    default public AbstractPrePostScheduleTasks getPrePostScheduleTasks(){
        // Default implementation returns null, subclasses should override if they support pre/post tasks
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
    /**
     * Allows external registration of custom requirements to this plugin's pre/post schedule tasks.
     * Plugin developers can control whether and how to integrate external requirements.
     * 
     * @param requirement The requirement to add
     * @param TaskContext The context in which this requirement should be fulfilled (PRE_SCHEDULE, POST_SCHEDULE, or BOTH)
     * @return true if the requirement was accepted and registered, false if rejected
     */
    default boolean addCustomRequirement(Requirement requirement, 
                                      TaskContext taskContext) {
        AbstractPrePostScheduleTasks tasks = getPrePostScheduleTasks();
        if (tasks != null) {
            return tasks.addCustomRequirement(requirement, taskContext);
        }
        return false; // No pre/post tasks available, cannot register custom requirements
    }
    
    /**
     * Checks if this plugin supports external custom requirement registration.
     * 
     * @return true if custom requirements can be added, false otherwise
     */
    default boolean supportsCustomRequirements() {
        return getPrePostScheduleTasks() != null;
    }
    
}