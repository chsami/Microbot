package net.runelite.client.plugins.microbot.pluginscheduler.tasks;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.api.SchedulablePlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LockCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;

import net.runelite.client.plugins.microbot.pluginscheduler.tasks.state.TaskExecutionState;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;


import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

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
public abstract class AbstractPrePostScheduleTasks implements AutoCloseable {
    
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
    
    // Centralized state tracking
    @Getter
    private final TaskExecutionState executionState = new TaskExecutionState();

    /**
     * Constructor for AbstractPrePostScheduleTasks.
     * Initializes the task manager with the provided plugin instance.
     * 
     * @param plugin The SchedulablePlugin instance to manage
     */
    protected AbstractPrePostScheduleTasks(SchedulablePlugin plugin) {
        this.plugin = plugin;
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
        if (postScheduledFuture != null && !postScheduledFuture.isDone()) {
            log.warn("Post-schedule task is still running, cannot execute pre-schedule tasks yet");
            return; // Cannot run pre-schedule tasks while post-schedule is still running
        }
        if (!isScheduleMode()) {
            log.info("Plugin {} is not in schedule mode, skipping pre-schedule tasks", plugin.getClass().getSimpleName());
            // Not in schedule mode, execute callback immediately
            if (callback != null) {
                callback.run();
            }
            return;
        }
        
        if (preScheduledFuture != null && !preScheduledFuture.isDone()) {
            log.warn("Pre-schedule task already running, skipping duplicate execution");
            return; // Pre-schedule task already running, skip
        }
        // Initialize executor service for pre-actions
        if (preExecutorService == null || preExecutorService.isShutdown()) {
            preExecutorService = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, getClass().getSimpleName() + "-PreSchedule");
                t.setDaemon(true);
                return t;
            });
        }
        
        preScheduledFuture = CompletableFuture.supplyAsync(() -> {
            try {
                log.info("\n --> Starting pre-schedule preparation on separate thread for plugin: \n\t\t{}", 
                    plugin.getClass().getSimpleName());
                
                // Execute preparation actions
                boolean success = executePreScheduleTask(lockCondition);
                
                if (success) {
                    log.info("\n\tPre-schedule preparation completed successfully - executing callback");
                    if (callback != null) {
                        callback.run();
                    }
                } else {
                    log.warn("\n\tPre-schedule preparation failed - stopping plugin");
                    plugin.reportFinished("Pre-schedule preparation failed", false);
                }
                
                return success;
            } catch (Exception e) {
                log.error("Error during pre-schedule preparation: {}", e.getMessage(), e);
                plugin.reportFinished("Pre-schedule preparation error: " + e.getMessage(), false);
                return false;
            }
        }, preExecutorService);

        // Handle timeout and completion
        if (timeout > 0) {
            preScheduledFuture.orTimeout(timeout, timeUnit)
                .whenComplete((result, throwable) -> {
                    handlePreTaskCompletion(result, throwable);
                    shutdownExecutorService(preExecutorService, "pre-schedule");
                });
        } else {
            preScheduledFuture.whenComplete((result, throwable) -> {
                handlePreTaskCompletion(result, throwable);
                shutdownExecutorService(preExecutorService, "pre-schedule");
            });
        }
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
        if (!isScheduleMode()) {
            // Not in schedule mode, execute callback immediately
            if (callback != null) {
                callback.run();
            }
            log.info("Plugin {} is not in schedule mode, skipping post-schedule tasks", plugin.getClass().getSimpleName());
            return; // Not in schedule mode, no post-actions needed
        }
        
        if (postScheduledFuture != null && !postScheduledFuture.isDone()) {
            log.warn("Post-schedule task already running, skipping duplicate execution");
            return;
        }
        
        initializePostExecutorService();
        
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
                    plugin.reportFinished("Post-schedule cleanup failed", false);
                }
                
                return success;
                
            } catch (Exception ex) {
                log.error("Error during post-schedule cleanup: {}", ex.getMessage(), ex);
                plugin.reportFinished("Post-schedule cleanup error: " + ex.getMessage(), false);
                
                return false;
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
            boolean customTasksSuccessful = executeCustomPreScheduleTask(lockCondition);
            
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
            boolean customTasksSuccessful = executeCustomPostScheduleTask(lockCondition);
            
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
    protected abstract boolean executeCustomPreScheduleTask(LockCondition lockCondition);
    
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
    protected abstract boolean executeCustomPostScheduleTask(LockCondition lockCondition);
    
    /**
     * Abstract method to determine if the plugin is running in schedule mode.
     * Concrete implementations should check their specific configuration to determine this.
     * 
     * @return true if the plugin is running under scheduler control, false otherwise
     */
    protected abstract String getConfigGroupName();
         /**
     * Checks if the plugin is running in schedule mode by checking the GOTR configuration.
     * 
     * @return true if scheduleMode flag is set, false otherwise
     */    
    protected boolean isScheduleMode() {
        try {
            String configGroupName = getConfigGroupName();
            if (configGroupName == null || configGroupName.isEmpty()) {
                log.warn("Config group name is not set, cannot determine schedule mode");
                return false; // Cannot determine schedule mode without config group
            }
            Boolean scheduleMode = Microbot.getConfigManager().getConfiguration(
                configGroupName, "scheduleMode", Boolean.class);
            return scheduleMode != null && scheduleMode;
        } catch (Exception e) {
            log.error("Failed to check schedule mode: {}", e.getMessage());
            return false;
        }
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
            boolean fulfilled = requirements.fulfillPreScheduleRequirements( true); // Default proximity and save spellbook
            
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
            boolean fulfilled = requirements.fulfillPostScheduleRequirements(true); // Default proximity and save spellbook
            
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
        if (throwable != null) {
            if (throwable instanceof TimeoutException) {
                log.warn("Pre-schedule task timed out for plugin: {}", plugin.getClass().getSimpleName());
                plugin.reportFinished("Pre-schedule task timed out", false);
            } else {
                log.error("Pre-schedule task failed for plugin: {} - {}", 
                    plugin.getClass().getSimpleName(), throwable.getMessage());
                plugin.reportFinished("Pre-schedule task failed: " + throwable.getMessage(), false);
            }
        } else if (result != null && result) {
            log.info("Pre-schedule task completed successfully for plugin: {}", plugin.getClass().getSimpleName());

        }
    }
    
    /**
     * Handles the completion of post-schedule tasks.
     * 
     * @param result The result of the task execution
     * @param throwable Any exception that occurred during execution
     */
    private void handlePostTaskCompletion(Boolean result, Throwable throwable) {
        if (throwable != null) {
            if (throwable instanceof TimeoutException) {
                log.warn("Post-schedule task timed out for plugin: {}", plugin.getClass().getSimpleName());
                // Force stop the plugin if post-task times out
                Microbot.getClientThread().invokeLater(() -> {
                    Microbot.stopPlugin((net.runelite.client.plugins.Plugin) plugin);
                    return true;
                });
            } else {
                log.error("Post-schedule task failed for plugin: {} - {}", 
                    plugin.getClass().getSimpleName(), throwable.getMessage());
            }
        } else if (result != null && result) {
            log.debug("Post-schedule task completed successfully for plugin: {}", plugin.getClass().getSimpleName());
        }
    }
    
    /**
     * Initializes the post executor service if not already initialized.
     */
    private void initializePostExecutorService() {
        if (postExecutorService == null || postExecutorService.isShutdown()) {
            postExecutorService = Executors.newSingleThreadScheduledExecutor(r -> {
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
        // Cancel any running futures
        if (preScheduledFuture != null && !preScheduledFuture.isDone()) {
            preScheduledFuture.cancel(true);
            preScheduledFuture = null;
        }
        
        if (postScheduledFuture != null && !postScheduledFuture.isDone()) {
            postScheduledFuture.cancel(true);
            postScheduledFuture = null;
        }
        
        // Shutdown executor services
        shutdownExecutorService(preExecutorService, "pre-schedule");
        shutdownExecutorService(postExecutorService, "post-schedule");
        
        preExecutorService = null;
        postExecutorService = null;
        disableScheduleMode(); // Reset schedule mode
        log.info("Closed {} pre-post Schedule task task manager", getClass().getSimpleName());
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
}
