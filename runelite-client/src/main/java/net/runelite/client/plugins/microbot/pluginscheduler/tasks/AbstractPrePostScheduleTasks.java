package net.runelite.client.plugins.microbot.pluginscheduler.tasks;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.SchedulerPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.event.ExecutionResult;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.model.PluginScheduleEntry;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
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
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.input.KeyListener;
import net.runelite.client.input.KeyManager;

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

    private final LockCondition prePostScheduleTaskLock = new LockCondition("Pre/Post Schedule Task Lock", false, true);

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
    private boolean canStartAnyTask(){
        // Check if the plugin is running in schedule mode
        if (plugin == null) {
            log.warn("Plugin instance is null, cannot determine schedule mode");
            return false; // Cannot determine schedule mode without plugin instance
        }

        if (getRequirements() == null || !getRequirements().isInitialized()) {
            log.warn("Requirements are not initialized, cannot execute pre-schedule tasks");
            return false; // Cannot run pre-schedule tasks if requirements are not met
        }
      
        
        if (postScheduledFuture != null && !postScheduledFuture.isDone()) {
            log.warn("Post-schedule task is still running, cannot execute pre-schedule tasks yet");
            return false; // Cannot run pre-schedule tasks while post-schedule is still running
        }              
        if (preScheduledFuture != null && !preScheduledFuture.isDone()) {
            log.warn("Pre-schedule task already running, skipping duplicate execution");
            return false; // Pre-schedule task already running, skip
        }
        return true;
    }
    public boolean canStartPreScheduleTasks() {
          // Check state before execution
        if (!executionState.canExecutePreTasks()) {
            log.warn("Pre-schedule tasks cannot be executed - already started and not completed. Use reset() to allow re-execution.");
            return false;
        }
        // Check if the plugin is running in schedule mode
        return canStartAnyTask();

    }
    public boolean canStartPostScheduleTasks() {
        // Check state before execution
        if (!executionState.canExecutePostTasks()) {
            log.warn("Post-schedule tasks cannot be executed - already started and not completed. Use reset() to allow re-execution.\n -executionState: {}",executionState);
            return false;
        }
        // Check if the plugin is running in schedule mode
        return canStartAnyTask();
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
        if (!canStartPreScheduleTasks()) {
            log.warn("Cannot execute pre-schedule tasks - conditions not met");
            return; // Cannot run pre-schedule tasks if conditions are not met
        }
        
        // Update state to indicate pre-schedule tasks are starting
        executionState.update(  TaskExecutionState.ExecutionPhase.PRE_SCHEDULE,TaskExecutionState.ExecutionState.STARTING);
        
        // Initialize executor service for pre-actions
        if (preExecutorService == null || preExecutorService.isShutdown()) {
            preExecutorService = Executors.newScheduledThreadPool(2, r -> {
                Thread t = new Thread(r, getClass().getSimpleName() + "-PreSchedule");
                t.setDaemon(true);
                return t;
            });
        }
        lockPrePostTask();      
        preScheduledFuture = CompletableFuture.supplyAsync(() -> {
            try {
                log.info("\n --> Starting pre-schedule preparation on separate thread for plugin: \n\t\t{}", 
                    plugin.getClass().getSimpleName());
                
                // Execute preparation actions
                boolean success = executePreScheduleTask(lockCondition);
                
                if (success) {
                    log.info("\n\tPre-schedule preparation completed successfully - executing callback");
                    executionState.update(  TaskExecutionState.ExecutionPhase.PRE_SCHEDULE,TaskExecutionState.ExecutionState.COMPLETED);
                    if (callback != null) {
                        callback.run();
                    }
                } else {
                    log.warn("\n\tPre-schedule preparation failed - stopping plugin");
                    executionState.update(  TaskExecutionState.ExecutionPhase.PRE_SCHEDULE,TaskExecutionState.ExecutionState.FAILED);                                                             
                }
                
                return success;
            } catch (Exception e) {
                log.error("Error during pre-schedule preparation: {}", e.getMessage(), e);
                executionState.update(  TaskExecutionState.ExecutionPhase.PRE_SCHEDULE,TaskExecutionState.ExecutionState.ERROR);                                               
                 // Unlock is handled in handlePreTaskCompletion via whenComplete
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
    private void lockPrePostTask(){
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
        
        if (!canStartPostScheduleTasks()) {
            log.warn("Cannot execute post-schedule tasks - conditions not met");
            return; // Cannot run post-schedule tasks if conditions are not met
        }
        executionState.update(  TaskExecutionState.ExecutionPhase.POST_SCHEDULE,TaskExecutionState.ExecutionState.STARTING);           
        
        initializePostExecutorService();
        lockPrePostTask();
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
            executionState.update(  TaskExecutionState.ExecutionPhase.PRE_SCHEDULE,TaskExecutionState.ExecutionState.FULFILLING_REQUIREMENTS);                               
            log.debug("Executing standard pre-schedule requirements fulfillment");
            
            // Always fulfill the standard requirements first
            boolean standardRequirementsFulfilled = fulfillPreScheduleRequirements();
            
            if (!standardRequirementsFulfilled) {
                log.warn("Standard pre-schedule requirements fulfillment failed, but continuing with custom tasks");
            }
            
            // Execute any custom pre-schedule logic from the child class            
            executionState.update(  TaskExecutionState.ExecutionPhase.PRE_SCHEDULE,TaskExecutionState.ExecutionState.CUSTOM_TASKS);                               
            log.debug("Executing custom pre-schedule tasks");
            boolean customTasksSuccessful = executeCustomPreScheduleTask(preScheduledFuture,lockCondition);
            
            // Clear state when finished
            if (standardRequirementsFulfilled && customTasksSuccessful) {
                executionState.update(  TaskExecutionState.ExecutionPhase.PRE_SCHEDULE,TaskExecutionState.ExecutionState.COMPLETED);                                               
            } else {
                executionState.update(  TaskExecutionState.ExecutionPhase.PRE_SCHEDULE,TaskExecutionState.ExecutionState.FAILED);                           
            }
            
            // Return true only if both standard and custom tasks succeeded
            // (or if we want to be more lenient, we could return true if custom tasks succeeded)
            return standardRequirementsFulfilled && customTasksSuccessful;
            
        } catch (Exception e) {
            executionState.update(  TaskExecutionState.ExecutionPhase.PRE_SCHEDULE,TaskExecutionState.ExecutionState.ERROR);           
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
            executionState.update(  TaskExecutionState.ExecutionPhase.POST_SCHEDULE,TaskExecutionState.ExecutionState.CUSTOM_TASKS);           
            log.debug("Executing custom post-schedule tasks");            
            // Execute any custom post-schedule logic from the child class first
            // This allows plugins to handle their specific cleanup (like stopping scripts)
            
            boolean customTasksSuccessful = executeCustomPostScheduleTask(postScheduledFuture, lockCondition);
            
            if (!customTasksSuccessful) {
                log.warn("Custom post-schedule tasks failed, but continuing with standard cleanup");
                return false;
            }
            
            // Always fulfill the standard requirements after custom tasks            
            executionState.update(  TaskExecutionState.ExecutionPhase.POST_SCHEDULE,TaskExecutionState.ExecutionState.FULFILLING_REQUIREMENTS);           
            log.debug("Executing standard post-schedule requirements fulfillment");
            boolean standardRequirementsFulfilled = fulfillPostScheduleRequirements();
            log.info("Standard post-schedule requirements fulfilled: {}", standardRequirementsFulfilled);
            
            // Update completion state
            if (customTasksSuccessful && standardRequirementsFulfilled) {
                executionState.update(  TaskExecutionState.ExecutionPhase.POST_SCHEDULE,TaskExecutionState.ExecutionState.COMPLETED);                               
                // Clear state after a brief delay to show completion
                scheduleStateClear(2000);                
            } else {
                executionState.update(  TaskExecutionState.ExecutionPhase.POST_SCHEDULE,TaskExecutionState.ExecutionState.FAILED);                
            }
            
            // Return true if both succeeded (cleanup is more lenient than setup)
            return customTasksSuccessful && standardRequirementsFulfilled;
            
        } catch (Exception e) {
            executionState.update(  TaskExecutionState.ExecutionPhase.POST_SCHEDULE,TaskExecutionState.ExecutionState.ERROR);
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
    protected String getConfigGroupName(){
        /**
         * Returns the configuration group name for this plugin.
         * This is used by the scheduler to manage configuration state.
         * 
         * @return The configuration group name
         */
        ConfigDescriptor pluginConfigDescriptor = this.plugin.getConfigDescriptor();
        if (pluginConfigDescriptor == null) {
            log.warn("\"{}\" plugin config descriptor is null", this.plugin.getClass().getSimpleName());
            return ""; // Default group name if descriptor is not available
        }
        String configGroupName = pluginConfigDescriptor.getGroup().value();
        if (configGroupName == null || configGroupName.isEmpty()) {
            log.warn("\"{}\" plugin config group name is null or empty");
            return ""; // Default group name if descriptor is not available
        }
        log.info("\"{}\" plugin config group name: {}", this.plugin.getClass().getSimpleName(),configGroupName);
        return configGroupName;
    }
    
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
        Boolean scheduleModeConfig = false;
        Boolean scheduleModeDetect = false;
        try {
            scheduleModeConfig = Microbot.getConfigManager().getConfiguration(
                configGroupName, "scheduleMode", Boolean.class);
        } catch (Exception e) {
            log.error("Failed to check schedule mode: {}", e.getMessage());
            return false;
        }
        if (schedulablePlugin == null) {
            log.warn("SchedulerPlugin is not running, cannot  can not be in schedule mode");
            scheduleModeDetect =  false; // SchedulerPlugin is not running, cannot determine schedule mode, so we dont run in schedule mode
        }else{
            PluginScheduleEntry currentPlugin =  schedulablePlugin.getCurrentPlugin();            
            if (currentPlugin == null) {
                log.warn("\nNo current plugin is running by the Scheduler Plugin, so it also can not be the plugin is start in scheduler mode");
                scheduleModeDetect =  false; // No current plugin is running, so it can not be in schedule mode
            }else{
                if (currentPlugin.isRunning() && currentPlugin.getPlugin() != null && !currentPlugin.getPlugin().equals(plugin)) {
                    log.warn("\n\tCurrent plugin {} is running, but it is not the same as the pluginScheduleEntry {}, so it can not be in schedule mode", 
                        currentPlugin.getPlugin().getClass().getSimpleName(),
                        plugin.getClass().getSimpleName());
                    scheduleModeDetect = false; // Current plugin is running, but it's not the same as the pluginSchedule
                    
                }else{
                    scheduleModeDetect = true;
                }
            }
        }      
        
        if (configGroupName.isEmpty()) {
            log.warn("Config group name is empty, cannot determine schedule mode");            
        }else if(scheduleModeConfig){
            Microbot.getConfigManager().setConfiguration(configGroupName, "scheduleMode", scheduleModeConfig);                   
            scheduleModeDetect = true; // If scheduleMode config is set, we are in schedule mode
        }
               log.debug("\nPlugin {}, with config group name {}, \nis running in schedule mode (plugin detect): {}\n\t\tSchedule mode config: {}",
            plugin.getClass().getSimpleName(), configGroupName, scheduleModeDetect, scheduleModeConfig);
        return  scheduleModeDetect;
      
    }
    private void setScheduleMode(boolean scheduleMode) {
        try {
            String configGroupName = getConfigGroupName();
            if (configGroupName == null || configGroupName.isEmpty()) {
                log.warn("\"{}\" plugin config group name is null or empty", this.plugin.getClass().getSimpleName());
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
     * @param TaskContext The context in which this requirement should be fulfilled
     * @return true if the requirement was successfully added, false otherwise
     */
    public boolean addCustomRequirement(Requirement requirement, 
                                      TaskContext taskContext) {
        PrePostScheduleRequirements requirements = getPrePostScheduleRequirements();
        if (requirements == null) {
            log.warn("Cannot add custom requirement: No pre/post schedule requirements defined for this plugin");
            return false;
        }
        
        // Mark this requirement as a custom requirement by creating a wrapper
        // We'll modify the requirement to ensure it's recognized as custom
        boolean success = requirements.addCustomRequirement(requirement, taskContext);
        
        if (success) {
            log.info("Successfully added custom requirement: {} for context: {}", 
                requirement.getDescription(), taskContext);
        } else {
            log.warn("Failed to add custom requirement: {} for context: {}", 
                requirement.getDescription(), taskContext);
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
            
            executionState.update(  TaskExecutionState.ExecutionPhase.PRE_SCHEDULE,TaskExecutionState.ExecutionState.FULFILLING_REQUIREMENTS);           
            log.info("\n\tFulfilling pre-schedule requirements for {}", requirements.getActivityType());
            
            // Use the unified fulfillment method that handles all requirement types including conditional requirements
            boolean fulfilled = requirements.fulfillPreScheduleRequirements(preScheduledFuture, true, executionState); // Pass executor service
            
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
            executionState.update(  TaskExecutionState.ExecutionPhase.POST_SCHEDULE,TaskExecutionState.ExecutionState.FULFILLING_REQUIREMENTS);                       
            log.info("Fulfilling post-schedule requirements for {}", requirements.getActivityType());
            
            // Use the unified fulfillment method that handles all requirement types including conditional requirements
            boolean fulfilled = requirements.fulfillPostScheduleRequirements(postScheduledFuture, true,executionState ); // Pass executor service
            
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
                    clearRequirementState();
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
                plugin.reportPreScheduleTaskFinished("Pre-schedule task timed out", ExecutionResult.SOFT_FAILURE);
            } else {
                log.error("Pre-schedule task failed for plugin: {} - {}", 
                    plugin.getClass().getSimpleName(), throwable.getMessage());
                plugin.reportPreScheduleTaskFinished("Pre-schedule task failed: " + throwable.getMessage(), ExecutionResult.HARD_FAILURE);
            }
        } else if (result != null && result) {
            log.info("Pre-schedule task completed successfully for plugin: {}", plugin.getClass().getSimpleName());
            executionState.update(  TaskExecutionState.ExecutionPhase.MAIN_EXECUTION,TaskExecutionState.ExecutionState.STARTING);
            plugin.reportPreScheduleTaskFinished("Pre-schedule preparation completed successfully", ExecutionResult.SUCCESS);
        }else{
            executionState.update(  TaskExecutionState.ExecutionPhase.MAIN_EXECUTION,TaskExecutionState.ExecutionState.ERROR);
            plugin.reportPreScheduleTaskFinished("\n\tPre-schedule preparation was not successful", ExecutionResult.SOFT_FAILURE);
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
                plugin.reportPostScheduleTaskFinished("Post-schedule task timed out", ExecutionResult.SOFT_FAILURE);
            } else {
                log.error("Post-schedule task failed for plugin: {} - {}", 
                    plugin.getClass().getSimpleName(), throwable.getMessage());
                plugin.reportPostScheduleTaskFinished("Post-schedule task failed: " + throwable.getMessage(), ExecutionResult.HARD_FAILURE);
            }
        } else if (result != null && result) {
            log.debug("Post-schedule task completed successfully for plugin: {}", plugin.getClass().getSimpleName());
            executionState.update(  TaskExecutionState.ExecutionPhase.POST_SCHEDULE,TaskExecutionState.ExecutionState.COMPLETED);
            plugin.reportPostScheduleTaskFinished("Post-schedule task completed successfully", ExecutionResult.SUCCESS);
        }else{
            executionState.update(  TaskExecutionState.ExecutionPhase.POST_SCHEDULE,TaskExecutionState.ExecutionState.ERROR);
            plugin.reportPostScheduleTaskFinished("\n\tPost-schedule task was not successful", ExecutionResult.SOFT_FAILURE);            
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
     * Clears the current task state
     */
    protected void clearTaskState() {
        executionState.clear();
    }
    protected void clearRequirementState() {
        executionState.clearRequirementState();
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
        if ( getRequirements()!= null) {
            clearRequirementState();
            getRequirements().reset();  
        }
        log.info("Reset completed - pre/post schedule tasks can now be executed again");
    }
    
    // Convenience methods for checking task states
    
    /**
     * Checks if pre-schedule tasks are completed
     */
    public boolean isPreTaskComplete() {
        return executionState.isPreTaskComplete();
    }
    
    public boolean isHasPreTaskStarted() {
        return executionState.isPreTaskRunning();
    }
    /**
     * Checks if main task is running
     */
    public boolean isHasMainTaskStarted() {
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
            unlock();                   
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
