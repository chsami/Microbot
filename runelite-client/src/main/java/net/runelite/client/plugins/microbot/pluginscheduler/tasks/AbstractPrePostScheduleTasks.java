package net.runelite.client.plugins.microbot.pluginscheduler.tasks;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.event.PluginScheduleEntryMainTaskFinishedEvent;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.state.TaskExecutionState;
import net.runelite.client.plugins.microbot.util.events.PluginPauseEvent;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.awt.event.KeyEvent;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.google.inject.Inject;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Abstract base class for managing pre and post schedule tasks for plugins operating under scheduler control.
 * <p>
 * This class provides a common infrastructure for handling:
 * <ul>
 *   <li>Executor service management for both pre and post tasks</li>
 *   <li>CompletableFuture lifecycle management with timeout support</li>
 *   <li>Thread-safe shutdown procedures</li>
 *   <li>Common error handling patterns</li>
 *   <li>AutoCloseable implementation for resource cleanup</li>
 *   <li>Emergency cancel hotkey (Ctrl+C) for aborting all tasks</li>
 * </ul>
 * <p>
 * Concrete implementations must provide:
 * <ul>
 *   <li>{@link #executePreScheduleTask(LockCondition)} - Plugin-specific preparation logic</li>
 *   <li>{@link #executePostScheduleTask(LockCondition)} - Plugin-specific cleanup logic</li>
 *   <li>{@link #isScheduleMode()} - Detection of scheduler mode</li>
 * </ul>
 * 
 * @see SchedulablePlugin
 * @since 1.0.0
 */
@Slf4j
public abstract class AbstractPrePostScheduleTasks implements AutoCloseable, KeyListener {
    
    // TODO: Consider adding configuration for default timeout values
    // TODO: Add metrics collection for task execution times and success rates
    // TODO: Implement retry mechanism for failed tasks with exponential backoff
    // TODO: Add support for task priority levels (critical vs optional tasks)
    // TODO: Consider adding a mechanism to pause/resume tasks based on external conditions
    // TODO: add custom tasks as callbacks to allow plugins to define their own pre/post tasks in addtion to the pre/post schedule requirements
    
    protected final SchedulablePlugin plugin;
    private ScheduledExecutorService postExecutorService;
    private ScheduledExecutorService preExecutorService;
    private CompletableFuture<Boolean> preScheduledFuture;
    private CompletableFuture<Boolean> postScheduledFuture;
    
    // Emergency cancel hotkey support (injected via plugin)
    
    private final KeyManager keyManager;
    
    // Centralized state tracking
    @Getter
    private final TaskExecutionState executionState = new TaskExecutionState();

    private final LockCondition prePostScheduleTaskLock = new LockCondition("Pre/Post Schedule Task Lock");

    /**
     * Constructor for AbstractPrePostScheduleTasks.
     * Initializes the task manager with the provided plugin instance.
     * 
     * @param plugin The SchedulablePlugin instance to manage
     */
    protected AbstractPrePostScheduleTasks(SchedulablePlugin plugin, KeyManager keyManager) {
        this.plugin = plugin;
        this.keyManager = keyManager;
        initializeCancel();
        log.info("Initialized pre/post schedule task manager for plugin: {}", plugin.getClass().getSimpleName());
    }
    
    /**
     * Initializes the emergency cancel hotkey functionality.
     * This method should be called after the plugin's KeyManager is available.
     * 
     * @param keyManager The KeyManager instance from the plugin
     */
    public final void initializeCancel() {        
        
        // Register emergency cancel hotkey if KeyManager is available
        try {
            if (keyManager != null) {
                keyManager.registerKeyListener(this);
                log.info("Registered emergency cancel hotkey (Ctrl+C) for plugin: {}", plugin.getClass().getSimpleName());
            }else{
                log.warn("KeyManager is not available, cannot register emergency cancel hotkey for plugin: {}", plugin.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.warn("Failed to register emergency cancel hotkey for plugin {}: {}", 
                    plugin.getClass().getSimpleName(), e.getMessage());
        }
    }
    
    /**
     * Executes pre-schedule preparation tasks on a separate thread.
     * This method runs preparation tasks asynchronously and calls the provided callback when complete.
     * 
     * @param callback The callback to execute when preparation is finished
     * @param timeout The timeout value (0 or negative means no timeout)
     * @param timeUnit The time unit for the timeout
     */
    public final void executePreScheduleTasks(Runnable callback, LockCondition lockCondition, int timeout, TimeUnit timeUnit) {
        // Check state before execution
        if (!executionState.canExecutePreTasks()) {
            log.warn("Pre-schedule tasks cannot be executed - already started and not completed. Use reset() to allow re-execution.");
            return;
        }
        
        if (postScheduledFuture != null && !postScheduledFuture.isDone()) {
            log.warn("Post-schedule task is still running, cannot execute pre-schedule tasks yet");
            return; // Cannot run pre-schedule tasks while post-schedule is still running
        }              
        if (preScheduledFuture != null && !preScheduledFuture.isDone()) {
            log.warn("Pre-schedule task already running, skipping duplicate execution");
            return; // Pre-schedule task already running, skip
        }
        
        // Update state to indicate pre-schedule tasks are starting
        executionState.updatePhase(TaskExecutionState.ExecutionPhase.PRE_SCHEDULE);
        
        // Initialize executor service for pre-actions
        if (preExecutorService == null || preExecutorService.isShutdown()) {
            preExecutorService = Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, getClass().getSimpleName() + "-PreSchedule");
                t.setDaemon(true);
                return t;
            });
        }
        look();      
        preScheduledFuture = CompletableFuture.supplyAsync(() -> {
            try {
                log.info("\n --> Starting pre-schedule preparation on separate thread for plugin: \n\t\t{}", 
                    plugin.getClass().getSimpleName());
                
                // Execute preparation actions
                boolean success = executePreScheduleTask(lockCondition);
                
                if (success) {
                    log.info("\n\tPre-schedule preparation completed successfully - executing callback");
                    executionState.updateState(TaskExecutionState.ExecutionState.COMPLETED, "Pre-schedule tasks completed successfully");
                    if (callback != null) {
                        callback.run();
                    }
                } else {
                    log.warn("\n\tPre-schedule preparation failed - stopping plugin");
                    executionState.updateState(TaskExecutionState.ExecutionState.FAILED, "Pre-schedule preparation failed");                                                        
                }
                
                return success;
            } catch (Exception e) {
                log.error("Error during pre-schedule preparation: {}", e.getMessage(), e);
                executionState.updateState(TaskExecutionState.ExecutionState.ERROR, "Pre-schedule preparation error: " + e.getMessage());                                
                throw new RuntimeException("Pre-schedule preparation failed", e);
            }
        }, preExecutorService);

        // Handle timeout and completion
        if (timeout > 0) {
            preScheduledFuture.orTimeout(timeout, timeUnit)
                .whenComplete((result, throwable) -> {
                    handlePreTaskCompletion(result, throwable);
                });
        } else {
            preScheduledFuture.whenComplete((result, throwable) -> {
                handlePreTaskCompletion(result, throwable);
            });
        }
    }
    private void look(){
        PluginPauseEvent.setPaused(true);
        prePostScheduleTaskLock.lock();
    }
    private void unlock() {
        PluginPauseEvent.setPaused(false);
        prePostScheduleTaskLock.unlock();
    }
    
    /**
     * Convenience method for executing pre-schedule tasks with default timeout.
     * 
     * @param callback The callback to execute when preparation is finished
     * @param lockCondition The lock condition to prevent running the pre-schedule tasks while the plugin is in a critical operation
     */
    public final void executePreScheduleTasks(Runnable callback, LockCondition lockCondition) {
        executePreScheduleTasks(callback,lockCondition, 0, TimeUnit.SECONDS);
    }
    /**
     * Convenience method for executing pre-schedule tasks with default timeout.
     * 
     * @param callback The callback to execute when preparation is finished
     */
    public final void executePreScheduleTasks(Runnable callback) {
        executePreScheduleTasks(callback,null, 0, TimeUnit.SECONDS);
    }


    public final boolean isPreScheduleRunning() {
        return preScheduledFuture != null && !preScheduledFuture.isDone();
    }
    /**
     * Executes post-schedule cleanup tasks when running under scheduler control.
     * This includes graceful shutdown procedures and resource cleanup.
     * 
     * @param lockCondition The lock condition to prevent interruption during critical operations
     * @param timeout The timeout value (0 or negative means no timeout)
     * @param timeUnit The time unit for the timeout
     */
    public final void executePostScheduleTasks(Runnable callback, LockCondition lockCondition, int timeout, TimeUnit timeUnit) {
        if( preScheduledFuture != null && !preScheduledFuture.isDone()) {
            log.warn("Pre-schedule task is still running, cannot execute post-schedule tasks yet");
            return; // Cannot run post-schedule tasks while pre-schedule is still running
        }               
        if (postScheduledFuture != null && !postScheduledFuture.isDone()) {
            log.warn("Post-schedule task already running, skipping duplicate execution");
            return;
        }
        
        initializePostExecutorService();
        look();
        postScheduledFuture = CompletableFuture.supplyAsync(() -> {
            try {
                if (lockCondition != null && lockCondition.isLocked()) {
                    log.info("Post-schedule: waiting for current operation to complete");
                    // Wait for lock to be released with reasonable timeout
                    int waitAttempts = 0;
                    while (lockCondition.isLocked() && waitAttempts < 60) { // Wait up to 60 seconds
                        try {
                            Thread.sleep(1000);
                            waitAttempts++;
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                            log.warn("Interrupted while waiting for lock release");
                            break;
                        }
                    }
                }
                
                log.info("\n\tStarting post-schedule tasks for plugin: \n\t\t{}", plugin.getClass().getSimpleName());
                
                // Execute cleanup actions
                boolean success = executePostScheduleTask(lockCondition);
                
                if (success) {
                    log.info("Post-schedule cleanup completed successfully");
                    if (callback != null) {
                        callback.run();
                    }
                } else {
                    log.warn("Post-schedule cleanup failed - stopping plugin");                    
                }
                
                return success;
                
            } catch (Exception ex) {
                log.error("Error during post-schedule cleanup: {}", ex.getMessage(), ex);                
                throw new RuntimeException("Post-schedule cleanup failed", ex);                
            }
        }, postExecutorService);
        
        // Handle timeout and completion
        if (timeout > 0) {
            postScheduledFuture.orTimeout(timeout, timeUnit)
                .whenComplete((result, throwable) -> {
                    handlePostTaskCompletion(result, throwable);
                    postScheduledFuture = null;
                });
        } else {
            postScheduledFuture.whenComplete((result, throwable) -> {
                handlePostTaskCompletion(result, throwable);
                postScheduledFuture = null;
            });
        }
    }
    
    /**
     * Convenience method for executing post-schedule tasks with default timeout.
     * 
     * @param callback The callback to execute when cleanup is finished
     * @param lockCondition The lock condition to prevent interruption during critical operations
     */
    public final void executePostScheduleTasks(Runnable callback, LockCondition lockCondition) {
        executePostScheduleTasks(callback, lockCondition, 0, TimeUnit.SECONDS);
    }
    /**
     * Convenience method for executing post-schedule tasks with default timeout.
     * @param callback The callback to execute when cleanup is finished
     * @return
     */
    public final void executePostScheduleTasks(Runnable callback) {
        executePostScheduleTasks(callback, null, 0, TimeUnit.SECONDS);
    }
    /**
     * Convenience method for executing post-schedule tasks with default timeout.
     * @param callback The callback to execute when cleanup is finished
     * @return
     */
    public final void executePostScheduleTasks(LockCondition lockCondition) {
        executePostScheduleTasks( () ->{} , lockCondition, 0, TimeUnit.SECONDS);
    }

    /**
     * Final implementation of pre-schedule task execution that enforces proper threading.
     * This method cannot be overridden - it ensures all pre-schedule tasks run through the proper
     * executor service infrastructure. Child classes provide their custom logic through 
     * {@link #executeCustomPreScheduleTask(LockCondition)}.
     * 
     * @param lockCondition The lock condition to prevent interruption during critical operations
     * @return true if preparation was successful, false otherwise
     */
    protected final boolean executePreScheduleTask(LockCondition lockCondition) {
        try {
            updateTaskState("PRE_SCHEDULE", "Starting", "Preparing for scheduled execution");
            log.debug("Executing standard pre-schedule requirements fulfillment");
            
            // Always fulfill the standard requirements first
            boolean standardRequirementsFulfilled = fulfillPreScheduleRequirements();
            
            if (!standardRequirementsFulfilled) {
                log.warn("Standard pre-schedule requirements fulfillment failed, but continuing with custom tasks");
            }
            
            // Execute any custom pre-schedule logic from the child class
            updateTaskState("PRE_SCHEDULE", "Custom Tasks", "Executing plugin-specific preparation");
            log.debug("Executing custom pre-schedule tasks");
            boolean customTasksSuccessful = executeCustomPreScheduleTask(preScheduledFuture,lockCondition);
            
            // Clear state when finished
            if (standardRequirementsFulfilled && customTasksSuccessful) {
                updateTaskState("PRE_SCHEDULE", "Completed", "Pre-schedule preparation finished");
                // Clear state after a brief delay to show completion
                scheduleStateClear(2000);
            } else {
                updateTaskState("PRE_SCHEDULE", "Failed", "Pre-schedule preparation encountered issues");
                // Clear state after a longer delay to show error
                scheduleStateClear(5000);
            }
            
            // Return true only if both standard and custom tasks succeeded
            // (or if we want to be more lenient, we could return true if custom tasks succeeded)
            return standardRequirementsFulfilled && customTasksSuccessful;
            
        } catch (Exception e) {
            updateTaskState("PRE_SCHEDULE", "Error", "Exception during preparation: " + e.getMessage());
            log.error("Error during pre-schedule task execution: {}", e.getMessage(), e);
            return false;
        } finally {
          
        }
    }
    
    /**
     * Final implementation of post-schedule task execution that enforces proper threading.
     * This method cannot be overridden - it ensures all post-schedule tasks run through the proper
     * executor service infrastructure. Child classes provide their custom logic through
     * {@link #executeCustomPostScheduleTask(LockCondition)}.
     * 
     * @param lockCondition The lock condition to prevent interruption during critical operations
     * @return true if cleanup was successful, false otherwise
     */
    protected final boolean executePostScheduleTask(LockCondition lockCondition) {
        try {
            updateTaskState("POST_SCHEDULE", "Starting", "Beginning post-schedule cleanup");
            log.debug("Executing custom post-schedule tasks");
            
            // Execute any custom post-schedule logic from the child class first
            // This allows plugins to handle their specific cleanup (like stopping scripts)
            updateTaskState("POST_SCHEDULE", "Custom Tasks", "Executing plugin-specific cleanup");
            boolean customTasksSuccessful = executeCustomPostScheduleTask(postScheduledFuture, lockCondition);
            
            if (!customTasksSuccessful) {
                log.warn("Custom post-schedule tasks failed, but continuing with standard cleanup");
            }
            
            // Always fulfill the standard requirements after custom tasks
            updateTaskState("POST_SCHEDULE", "Requirements", "Executing standard cleanup requirements");
            log.debug("Executing standard post-schedule requirements fulfillment");
            boolean standardRequirementsFulfilled = fulfillPostScheduleRequirements();
            log.info("Standard post-schedule requirements fulfilled: {}", standardRequirementsFulfilled);
            
            // Update completion state
            if (customTasksSuccessful || standardRequirementsFulfilled) {
                updateTaskState("POST_SCHEDULE", "Completed", "Post-schedule cleanup finished");
            } else {
                updateTaskState("POST_SCHEDULE", "Failed", "Post-schedule cleanup encountered issues");
            }
            
            // Return true if either succeeded (cleanup is more lenient than setup)
            return customTasksSuccessful || standardRequirementsFulfilled;
            
        } catch (Exception e) {
            updateTaskState("POST_SCHEDULE", "Error", "Exception during cleanup: " + e.getMessage());
            log.error("Error during post-schedule task execution: {}", e.getMessage(), e);
            return false;
        } finally {           
        }
    }
    public final boolean isPostScheduleRunning() {
        return postScheduledFuture != null && !postScheduledFuture.isDone();
    }

    /**
     * Abstract method that concrete implementations must provide for custom pre-schedule logic.
     * This method is called AFTER the standard requirement fulfillment logic and should contain
     * any plugin-specific preparation tasks that are not covered by the standard requirements.
     * 
     * IMPORTANT: This method is always called within the proper executor service threading context.
     * Do not call this method directly - use {@link #executePreScheduleTasks} instead.
     * 
     * @param lockCondition The lock condition to prevent interruption during critical operations
     * @return true if custom preparation was successful, false otherwise
     */
    protected abstract boolean executeCustomPreScheduleTask(CompletableFuture<Boolean> preScheduledFuture,LockCondition lockCondition);
    
    /**
     * Abstract method that concrete implementations must provide for custom post-schedule logic.
     * This method is called BEFORE the standard requirement fulfillment logic and should contain
     * any plugin-specific cleanup tasks (like stopping scripts, leaving minigames, etc.).
     * 
     * IMPORTANT: This method is always called within the proper executor service threading context.
     * Do not call this method directly - use {@link #executePostScheduleTasks} instead.
     * 
     * @param lockCondition The lock condition to prevent interruption during critical operations
     * @return true if custom cleanup was successful, false otherwise
     */
    protected abstract boolean executeCustomPostScheduleTask(CompletableFuture<Boolean> postScheduledFuture, LockCondition lockCondition);
    
    /**
     * Abstract method to determine if the plugin is running in schedule mode.
     * Concrete implementations should check their specific configuration to determine this.
     * 
     * @return true if the plugin is running under scheduler control, false otherwise
     */
    protected abstract String getConfigGroupName();
    
    public boolean isScheduleMode() {
        // Check if the plugin is running in schedule mode
        if (plugin == null) {
            log.warn("Plugin instance is null, cannot determine schedule mode");
            return false; // Cannot determine schedule mode without plugin instance
        }
        
        // Check if the plugin is running in schedule mode
        return isScheduleMode(this.plugin, getConfigGroupName());

    }
    /**
     * Checks if the plugin is running in schedule mode by checking the GOTR configuration.
     * 
     * @return true if scheduleMode flag is set, false otherwise
     */    
    public static boolean isScheduleMode( SchedulablePlugin plugin, String configGroupName) {

        SchedulerPlugin schedulablePlugin =  (SchedulerPlugin) Microbot.getPlugin(SchedulerPlugin.class.getName());
        Boolean scheduleModeConfig = null;
        Boolean scheduleModeDetect = false;
        try {
            scheduleModeConfig = Microbot.getConfigManager().getConfiguration(
                configGroupName, "scheduleMode", Boolean.class);
        } catch (Exception e) {
            log.error("Failed to check schedule mode: {}", e.getMessage());
            return false;
        }
        if (schedulablePlugin == null) {
            log.info("SchedulerPlugin is not running, cannot  can not be in schedule mode");
            scheduleModeDetect =  false; // SchedulerPlugin is not running, cannot determine schedule mode, so we dont run in schedule mode
        }else{
            PluginScheduleEntry currentPlugin =  schedulablePlugin.getCurrentPlugin();            
            if (currentPlugin == null) {
                log.info("No current plugin is running by the Scheduler Plugin, so it also can be the plugin is start in scheduler mode");
                scheduleModeDetect =  false; // No current plugin is running, so it can not be in schedule mode
            }else{
                if (currentPlugin.isRunning() && currentPlugin.getPlugin() != null && !currentPlugin.getPlugin().equals(plugin)) {
                    log.info("Current plugin {} is running, but it is not the same as the pluginScheduleEntry {}, so it can not be in schedule mode", 
                        currentPlugin.getPlugin().getClass().getSimpleName(),
                        plugin.getClass().getSimpleName());
                    scheduleModeDetect = false; // Current plugin is running, but it's not the same as the pluginSchedule
                    
                }else{
                    scheduleModeDetect = true;
                }
            }
        }      
        Microbot.getConfigManager().setConfiguration(configGroupName, "scheduleMode", scheduleModeDetect);                   
        return scheduleModeDetect != null && scheduleModeDetect;
      
    }
    private void setScheduleMode(boolean scheduleMode) {
        try {
            String configGroupName = getConfigGroupName();
            if (configGroupName == null || configGroupName.isEmpty()) {
                log.warn("Config group name is not set, cannot set schedule mode");
                return; // Cannot set schedule mode without config group
            }
            Microbot.getConfigManager().setConfiguration(configGroupName, "scheduleMode", scheduleMode);
        } catch (Exception e) {
            log.error("Failed to set schedule mode: {}", e.getMessage());
        }
    }
    protected void enableScheduleMode() {
        setScheduleMode(true);
    }
    protected void disableScheduleMode() {
        setScheduleMode(false);
    }
    
    
    /**
     * Abstract method that concrete implementations must provide to supply their requirements.
     * This method should return the PrePostScheduleRequirements instance that defines
     * what the plugin needs for optimal operation.
     * 
     * @return The PrePostScheduleRequirements instance for this plugin
     */
    protected abstract PrePostScheduleRequirements getPrePostScheduleRequirements();
    
    /**
     * Public accessor for the pre/post schedule requirements.
     * This allows external components (like UI panels) to access requirement information.
     * 
     * @return The PrePostScheduleRequirements instance, or null if not implemented
     */
    public final PrePostScheduleRequirements getRequirements() {
        return getPrePostScheduleRequirements();
    }
    
    /**
     * Adds a custom requirement to this plugin's pre/post schedule requirements.
     * Custom requirements are marked as CUSTOM type and are fulfilled after all standard requirements.
     * 
     * @param requirement The requirement to add
     * @param scheduleContext The context in which this requirement should be fulfilled
     * @return true if the requirement was successfully added, false otherwise
     */
    public boolean addCustomRequirement(Requirement requirement, 
                                      ScheduleContext scheduleContext) {
        PrePostScheduleRequirements requirements = getPrePostScheduleRequirements();
        if (requirements == null) {
            log.warn("Cannot add custom requirement: No pre/post schedule requirements defined for this plugin");
            return false;
        }
        
        // Mark this requirement as a custom requirement by creating a wrapper
        // We'll modify the requirement to ensure it's recognized as custom
        boolean success = requirements.addCustomRequirement(requirement, scheduleContext);
        
        if (success) {
            log.info("Successfully added custom requirement: {} for context: {}", 
                requirement.getDescription(), scheduleContext);
        } else {
            log.warn("Failed to add custom requirement: {} for context: {}", 
                requirement.getDescription(), scheduleContext);
        }
        
        return success;
    }
    
    /**
     * Default implementation for fulfilling pre-schedule requirements.
     * This method attempts to fulfill all pre-schedule requirements including:
     * - Location requirements (travel to pre-schedule location)
     * - Spellbook requirements (switch to required spellbook)
     * - Equipment and inventory setup (using the static methods from PrePostScheduleRequirements)
     * 
     * Child classes can override this method to provide custom behavior while still
     * leveraging the default requirement fulfillment logic.
     * 
     * @return true if all requirements were successfully fulfilled, false otherwise
     */
    protected boolean fulfillPreScheduleRequirements() {
        try {
            PrePostScheduleRequirements requirements = getPrePostScheduleRequirements();
            if (requirements == null) {
                log.info("No pre-schedule requirements defined");
                return true;
            }
            
            updateTaskState("PRE_SCHEDULE", "Requirements", "Analyzing requirements for " + requirements.getActivityType());
            log.info("Fulfilling pre-schedule requirements for {}", requirements.getActivityType());
            
            // Use the unified fulfillment method that handles all requirement types including conditional requirements
            boolean fulfilled = requirements.fulfillPreScheduleRequirements(preScheduledFuture, true); // Pass executor service
            
            if (!fulfilled) {
                log.error("Failed to fulfill pre-schedule requirements");
                return false;
            }
            
            log.info("Successfully fulfilled pre-schedule requirements");
            return true;
            
        } catch (Exception e) {
            log.error("Error fulfilling pre-schedule requirements: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Default implementation for fulfilling post-schedule requirements.
     * This method attempts to fulfill all post-schedule requirements including:
     * - Location requirements (travel to post-schedule location)  
     * - Spellbook restoration (switch back to original spellbook)
     * - Banking and cleanup operations
     * 
     * Child classes can override this method to provide custom behavior while still
     * leveraging the default requirement fulfillment logic.
     * 
     * @return true if all requirements were successfully fulfilled, false otherwise
     */
    protected boolean fulfillPostScheduleRequirements() {
        try {
            PrePostScheduleRequirements requirements = getPrePostScheduleRequirements();
            if (requirements == null) {
                log.info("No post-schedule requirements defined");
                return true;
            }
            
            updateTaskState("POST_SCHEDULE", "Requirements", "Processing post-schedule requirements for " + requirements.getActivityType());
            log.info("Fulfilling post-schedule requirements for {}", requirements.getActivityType());
            
            // Use the unified fulfillment method that handles all requirement types including conditional requirements
            boolean fulfilled = requirements.fulfillPostScheduleRequirements(postScheduledFuture, true); // Pass executor service
            
            if (!fulfilled) {
                log.error("Failed to fulfill all post-schedule requirements");
                return false;
            }
            
            log.info("Successfully fulfilled post-schedule requirements");
            return true;
            
        } catch (Exception e) {
            log.error("Error fulfilling post-schedule requirements: {}", e.getMessage(), e);
            return false;
        } finally {
            // Clear requirements state
            PrePostScheduleRequirements requirements = getPrePostScheduleRequirements();
            if (requirements != null) {
                try {
                    requirements.clearFulfillmentState();
                } catch (Exception e) {
                    log.warn("Failed to clear fulfillment state: {}", e.getMessage());
                }
            }
        }
    }
    
    /**
     * Handles the completion of pre-schedule tasks.
     * 
     * @param result The result of the task execution
     * @param throwable Any exception that occurred during execution
     */
    private void handlePreTaskCompletion(Boolean result, Throwable throwable) {
        unlock();
        if (throwable != null) {
            if (throwable instanceof TimeoutException) {
                log.warn("Pre-schedule task timed out for plugin: {}", plugin.getClass().getSimpleName());
                plugin.reportPreScheduleTaskFinished("Pre-schedule task timed out", false);
            } else {
                log.error("Pre-schedule task failed for plugin: {} - {}", 
                    plugin.getClass().getSimpleName(), throwable.getMessage());
                plugin.reportPreScheduleTaskFinished("Pre-schedule task failed: " + throwable.getMessage(), false);
            }
        } else if (result != null && result) {
            log.info("Pre-schedule task completed successfully for plugin: {}", plugin.getClass().getSimpleName());
            plugin.reportPreScheduleTaskFinished("Pre-schedule preparation completed successfully", true);
        }else{
            plugin.reportPreScheduleTaskFinished("\n\tPre-schedule preparation was not successfull", false);
        }

    }
    
    /**
     * Handles the completion of post-schedule tasks.
     * 
     * @param result The result of the task execution
     * @param throwable Any exception that occurred during execution
     */
    private void handlePostTaskCompletion(Boolean result, Throwable throwable) {
        unlock();
        if (throwable != null) {
            if (throwable instanceof TimeoutException) {
                log.warn("Post-schedule task timed out for plugin: {}", plugin.getClass().getSimpleName());             
                plugin.reportPostScheduleTaskFinished("Post-schedule task timed out", false);
            } else {
                log.error("Post-schedule task failed for plugin: {} - {}", 
                    plugin.getClass().getSimpleName(), throwable.getMessage());
                plugin.reportPostScheduleTaskFinished("Post-schedule task failed: " + throwable.getMessage(), false);
            }
        } else if (result != null && result) {
            log.debug("Post-schedule task completed successfully for plugin: {}", plugin.getClass().getSimpleName());
            plugin.reportPostScheduleTaskFinished("\\n" + //
                                "\\tPost-schedule task completed successfully", true);
        }else{
            plugin.reportPostScheduleTaskFinished("\n\tPost-schedule task was not successfull", false);
            

        }
    }
    
    /**
     * Initializes the post executor service if not already initialized.
     */
    private void initializePostExecutorService() {
        if (postExecutorService == null || postExecutorService.isShutdown()) {
            postExecutorService = Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, getClass().getSimpleName() + "-PostSchedule");
                t.setDaemon(true);
                return t;
            });
        }
    }
    
    /**
     * Safely shuts down an executor service with proper timeout handling.
     * 
     * @param executorService The executor service to shutdown
     * @param taskType The type of task (for logging purposes)
     */
    private void shutdownExecutorService(ScheduledExecutorService executorService, String taskType) {
        if (executorService != null && !executorService.isShutdown()) {
            try {
                executorService.shutdown();
                
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Executor service for {} tasks did not terminate gracefully, forcing shutdown", taskType);
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while shutting down {} executor service", taskType);
                executorService.shutdownNow();
            }
        }
    }
    
    /**
     * Returns whether any tasks are currently running.
     * 
     * @return true if any pre or post tasks are currently executing
     */
    public final boolean isRunning() {
        return (preScheduledFuture != null && !preScheduledFuture.isDone()) ||
               (postScheduledFuture != null && !postScheduledFuture.isDone());
    }
    
    /**
     * Cancels any running tasks and shuts down all executor services.
     * This method implements AutoCloseable for proper resource management.
     */
    @Override
    public void close() {
        // Unregister emergency cancel hotkey
        try {
            if (keyManager != null) {
                keyManager.unregisterKeyListener(this);
                log.debug("Unregistered emergency cancel hotkey for plugin: {}", plugin.getClass().getSimpleName());
            }
        } catch (Exception e) {
            log.warn("Failed to unregister emergency cancel hotkey for plugin {}: {}", 
                    plugin.getClass().getSimpleName(), e.getMessage());
        }
        unlock();
        // Cancel any running futures
        if (preScheduledFuture != null && !preScheduledFuture.isDone()) {
            preScheduledFuture.cancel(true);
            preScheduledFuture = null;
        }
        
        if (postScheduledFuture != null && !postScheduledFuture.isDone()) {
            postScheduledFuture.cancel(true);
            postScheduledFuture = null;
        }
        executionState.reset();
        
        // Shutdown executor services
        shutdownExecutorService(preExecutorService, "pre-schedule");
        shutdownExecutorService(postExecutorService, "post-schedule");
        
        preExecutorService = null;
        postExecutorService = null;
        disableScheduleMode(); // Reset schedule mode
        log.info("Closed {} pre-post Schedule task task manager", getClass().getSimpleName());
    }
    public void cancelPreScheduleTasks() {
        if (preScheduledFuture != null && !preScheduledFuture.isDone()) {
            preScheduledFuture.cancel(true);
            preScheduledFuture = null;
            log.info("Cancelled pre-schedule tasks for plugin: {}", plugin.getClass().getSimpleName());
        } else {
            log.warn("No pre-schedule tasks to cancel for plugin: {}", plugin.getClass().getSimpleName());
        }
    }
    
    public void cancelPostScheduleTasks() {
        if (postScheduledFuture != null && !postScheduledFuture.isDone()) {
            postScheduledFuture.cancel(true);
            postScheduledFuture = null;
            log.info("Cancelled post-schedule tasks for plugin: {}", plugin.getClass().getSimpleName());
        } else {
            log.warn("No post-schedule tasks to cancel for plugin: {}", plugin.getClass().getSimpleName());
        }
    }
    
    /**
     * Shutdown alias for compatibility. Calls {@link #close()}.
     */
    public final void shutdown() {
        close();
    }
    
    /**
     * Updates the current task state for overlay display
     * @deprecated Use executionState directly instead
     */
    @Deprecated
    protected void updateTaskState(String phase, String action, String requirementDescription) {
        // Convert old-style updates to new state system
        TaskExecutionState.ExecutionPhase execPhase = mapPhaseString(phase);
        if (execPhase != null) {
            executionState.updatePhase(execPhase);
        }
        
        TaskExecutionState.ExecutionState execState = mapActionString(action);
        executionState.updateState(execState, requirementDescription);
    }
    
    /**
     * Updates the current task state with specific requirement information
     * @deprecated Use executionState directly instead
     */
    @Deprecated
    protected void updateTaskStateWithRequirement(String phase, String action, String requirementType, Object requirementObject, String description) {
        // Convert old-style updates to new state system
        TaskExecutionState.ExecutionPhase execPhase = mapPhaseString(phase);
        if (execPhase != null) {
            executionState.updatePhase(execPhase);
        }
        
        TaskExecutionState.ExecutionState execState = mapActionString(action);
        executionState.updateState(execState, description);
    }
    
    /**
     * Maps old phase strings to new execution phases
     */
    private TaskExecutionState.ExecutionPhase mapPhaseString(String phase) {
        if (phase == null) return null;
        
        switch (phase) {
            case "PRE_SCHEDULE":
                return TaskExecutionState.ExecutionPhase.PRE_SCHEDULE;
            case "POST_SCHEDULE":
                return TaskExecutionState.ExecutionPhase.POST_SCHEDULE;
            default:
                return null;
        }
    }
    
    /**
     * Maps old action strings to new execution states
     */
    private TaskExecutionState.ExecutionState mapActionString(String action) {
        if (action == null) return TaskExecutionState.ExecutionState.STARTING;
        
        switch (action.toLowerCase()) {
            case "starting":
                return TaskExecutionState.ExecutionState.STARTING;
            case "requirements":
                return TaskExecutionState.ExecutionState.FULFILLING_REQUIREMENTS;
            case "custom tasks":
                return TaskExecutionState.ExecutionState.CUSTOM_TASKS;
            case "completed":
                return TaskExecutionState.ExecutionState.COMPLETED;
            case "failed":
                return TaskExecutionState.ExecutionState.FAILED;
            case "error":
                return TaskExecutionState.ExecutionState.ERROR;
            default:
                return TaskExecutionState.ExecutionState.FULFILLING_REQUIREMENTS;
        }
    }
    
    /**
     * Clears the current task state
     */
    protected void clearTaskState() {
        executionState.clear();
    }
    
    /**
     * Gets the current execution status for overlay display
     * @return A formatted string describing the current state, or null if not executing
     */
    public String getCurrentExecutionStatus() {
        return executionState.getDisplayStatus();
    }
    
    /**
     * Checks if any pre/post schedule task is currently executing
     */
    public boolean isExecuting() {
        return executionState.isExecuting();
    }
    
    /**
     * Resets the task execution state and cancels any running tasks.
     * This allows pre/post schedule tasks to be executed again.
     * 
     * WARNING: This will forcibly cancel any currently running tasks.
     */
    public synchronized void reset() {
        log.info("Resetting pre/post schedule tasks for plugin: {}", plugin.getClass().getSimpleName());
        unlock();  
        // Cancel and cleanup running futures
        if (preScheduledFuture != null && !preScheduledFuture.isDone()) {
            log.info("Cancelling running pre-schedule task");
            preScheduledFuture.cancel(true);
            preScheduledFuture = null;
        }
        
        if (postScheduledFuture != null && !postScheduledFuture.isDone()) {
            log.info("Cancelling running post-schedule task");
            postScheduledFuture.cancel(true);
            postScheduledFuture = null;
        }
        
        // Shutdown executor services
        shutdownExecutorService(preExecutorService, "Pre-schedule");
        shutdownExecutorService(postExecutorService, "Post-schedule");
        
        preExecutorService = null;
        postExecutorService = null;
        
        // Reset execution state
        executionState.reset();
        
        log.info("Reset completed - pre/post schedule tasks can now be executed again");
    }
    
    // Convenience methods for checking task states
    
    /**
     * Checks if pre-schedule tasks are completed
     */
    public boolean isPreTaskComplete() {
        return executionState.isPreTaskComplete();
    }
    
    /**
     * Checks if main task is running
     */
    public boolean isMainTaskRunning() {
        return executionState.isMainTaskRunning();
    }
    
    /**
     * Checks if main task is completed  
     */
    public boolean isMainTaskComplete() {
        return executionState.isMainTaskComplete();
    }
    
    /**
     * Checks if post-schedule tasks are completed
     */
    public boolean isPostTaskComplete() {
        return executionState.isPostTaskComplete();
    }
    
    /**
     * Checks if pre-schedule tasks are currently running
     */
    public boolean isPreTaskRunning() {
        return executionState.isPreTaskRunning();
    }
    
    /**
     * Checks if post-schedule tasks are currently running
     */
    public boolean isPostTaskRunning() {
        return executionState.isPostTaskRunning();
    }
    
    /**
     * Gets a detailed status string for debugging
     */
    public String getDetailedExecutionStatus() {
        return executionState.getDetailedStatus();
    }
    
    /**
     * Schedules clearing of the task state after a delay
     * @param delayMs Delay in milliseconds before clearing the state
     */
    private void scheduleStateClear(int delayMs) {
        new Thread(() -> {
            try {
                Thread.sleep(delayMs);
                clearTaskState();
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }
    
    /**
     * Emergency cancellation method for aborting all tasks and operations.
     * This method will:
     * 1. Cancel all pre and post schedule futures
     * 2. Shutdown all executor services
     * 3. Clear Rs2Walker target
     * 4. Reset task execution state
     * 
     * This is used by the Ctrl+C hotkey for emergency stops.
     */
    public final void emergencyCancel() {
        try {
            log.warn("\n=== EMERGENCY CANCELLATION TRIGGERED ===");
            log.warn("Plugin: {}", plugin.getClass().getSimpleName());
            
            // Cancel all futures immediately
            if (preScheduledFuture != null && !preScheduledFuture.isDone()) {
                log.info("  • Cancelling pre-schedule future");
                preScheduledFuture.cancel(true);
                preScheduledFuture = null;
            }
            
            if (postScheduledFuture != null && !postScheduledFuture.isDone()) {
                log.info("  • Cancelling post-schedule future");
                postScheduledFuture.cancel(true);
                postScheduledFuture = null;
            }
            
            // Shutdown executor services immediately
            if (preExecutorService != null && !preExecutorService.isShutdown()) {
                log.info("  • Shutting down pre-schedule executor service");
                preExecutorService.shutdownNow();
                preExecutorService = null;
            }
            
            if (postExecutorService != null && !postExecutorService.isShutdown()) {
                log.info("  • Shutting down post-schedule executor service");
                postExecutorService.shutdownNow();
                postExecutorService = null;
            }
            
            // Clear Rs2Walker target to stop any walking operations
            try {
                log.info("  • Clearing Rs2Walker target");
                Rs2Walker.setTarget(null);
            } catch (Exception e) {
                log.warn("Failed to clear Rs2Walker target: {}", e.getMessage());
            }
            
            // Reset task execution state
            log.info("  • Resetting task execution state");
            executionState.reset();                        
            log.warn("=== EMERGENCY CANCELLATION COMPLETED ===\n");
            
        } catch (Exception e) {
            log.error("Error during emergency cancellation: {}", e.getMessage(), e);
        }
    }
    
    // ==================== KeyListener Interface Methods ====================
    
    @Override
    public void keyTyped(KeyEvent e) {
        // Not needed for hotkey detection
    }
    
    @Override
    public void keyPressed(KeyEvent e) {
        // Check for Ctrl+C hotkey combination
        if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C) {
            log.info("Emergency cancel hotkey (Ctrl+C) detected for plugin: {}", plugin.getClass().getSimpleName());
            
            // Only trigger if we have running tasks
            if (isRunning()) {
                emergencyCancel();
            } else {
                log.info("No tasks currently running - emergency cancel not needed");
            }
        }
    }
    
    @Override
    public void keyReleased(KeyEvent e) {
        // Not needed for hotkey detection
    }
}
