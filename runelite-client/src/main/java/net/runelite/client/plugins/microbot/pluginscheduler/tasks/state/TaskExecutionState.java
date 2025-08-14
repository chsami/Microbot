package net.runelite.client.plugins.microbot.pluginscheduler.tasks.state;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.state.FulfillmentStep;

/**
 * Centralized state tracking system for pre/post schedule task execution and requirement fulfillment.
 * This class provides a single source of truth for the current execution state, eliminating
 * the redundant state tracking that existed across AbstractPrePostScheduleTasks and PrePostScheduleRequirements.
 */
@Slf4j
public class TaskExecutionState {
    
    /**
     * Represents the overall execution phase
     */
    public enum ExecutionPhase {
        IDLE("Idle"),
        PRE_SCHEDULE("Pre-Schedule"),
        MAIN_EXECUTION("Main Execution"), 
        POST_SCHEDULE("Post-Schedule");
        
        @Getter
        private final String displayName;
        
        ExecutionPhase(String displayName) {
            this.displayName = displayName;
        }
    }
    
    /**
     * Represents the current state of task execution
     */
    public enum ExecutionState {
        STARTING("Starting"),
        FULFILLING_REQUIREMENTS("Fulfilling Requirements"),
        CUSTOM_TASKS("Custom Tasks"),
        COMPLETED("Completed"),
        FAILED("Failed"),
        ERROR("Error");
        
        @Getter
        private final String displayName;
        
        ExecutionState(String displayName) {
            this.displayName = displayName;
        }
    }
    
    // Current execution state
    @Getter
    private volatile ExecutionPhase currentPhase = ExecutionPhase.IDLE;
    @Getter
    private volatile ExecutionState currentState = ExecutionState.STARTING;
    
    @Getter
    private volatile String currentDetails = null;
    @Getter
    private volatile boolean hasError = false;
    @Getter
    private volatile String errorMessage = null;
    
    // Progress tracking
    @Getter
    private volatile int currentStepNumber = 0;
    @Getter
    private volatile int totalSteps = 0;
    
    // Individual requirement tracking within steps
    @Getter
    private volatile FulfillmentStep currentStep = null;
    @Getter
    private volatile Object currentRequirement = null; // The specific requirement being processed
    @Getter
    private volatile String currentRequirementName = null; // readable name of current requirement
    @Getter
    private volatile int currentRequirementIndex = 0; // Current requirement index within step
    @Getter
    private volatile int totalRequirementsInStep = 0; // Total requirements in current step
    
    // Execution phase completion tracking - prevents multiple executions
    @Getter
    private volatile boolean hasPreTaskStarted = false;
    @Getter
    private volatile boolean hasPreTaskCompleted = false;
    @Getter
    private volatile boolean hasMainTaskStarted = false;
    @Getter
    private volatile boolean hasMainTaskCompleted = false;
    @Getter
    private volatile boolean hasPostTaskStarted = false;
    @Getter
    private volatile boolean hasPostTaskCompleted = false;
    
    /**
     * Updates the current execution phase and resets step tracking
     */
    public synchronized void update(ExecutionPhase phase, ExecutionState state) {
        this.currentPhase = phase;
        this.currentState = state;        
        // Mark phase as started
        switch (phase) {
            case PRE_SCHEDULE:
                this.hasPreTaskStarted = true;
                if (state == ExecutionState.COMPLETED|| state == ExecutionState.FAILED || state == ExecutionState.ERROR) {
                    this.hasPreTaskCompleted = true;
                } else {
                    this.hasPreTaskCompleted = false;
                }
                break;
            case MAIN_EXECUTION:
                if (!hasPreTaskCompleted) {
                    log.warn("Main execution started without pre-schedule tasks. Ensure pre-tasks are executed first.");
                }else{
                    hasPreTaskCompleted = true;
                }
                this.hasMainTaskStarted = true;
                if (state == ExecutionState.COMPLETED|| state == ExecutionState.FAILED || state == ExecutionState.ERROR) {
                    this.hasMainTaskCompleted = true;
                } else {
                    this.hasMainTaskCompleted = false;
                }
                break;
            case POST_SCHEDULE:
                this.hasPostTaskStarted = true;
                if (state == ExecutionState.COMPLETED|| state == ExecutionState.FAILED || state == ExecutionState.ERROR) {
                    this.hasPostTaskCompleted = true;
                } else {
                    this.hasPostTaskCompleted = false;
                }
                break;
            default:
                break;
        }
        
        log.debug("Execution phase updated to: {}", phase.getDisplayName());
    }
    
  
    
    /**
     * Updates the current fulfillment step and progress
     */
    public synchronized void updateFulfillmentStep(FulfillmentStep step, String details) {
        this.currentStep = step;
        this.currentDetails = details;
        this.currentState = ExecutionState.FULFILLING_REQUIREMENTS;
        this.currentStepNumber = step != null ? step.getOrder() : 0;
        this.totalSteps = FulfillmentStep.getTotalSteps();
        
        // Reset requirement tracking when starting new step
        this.currentRequirement = null;
        this.currentRequirementName = null;
        this.currentRequirementIndex = 0;
        this.totalRequirementsInStep = 0;
        
        log.debug("Fulfillment step updated to: {} ({}/{}) - {}", 
            step != null ? step.getDisplayName() : "None", 
            currentStepNumber, totalSteps, details);
    }
    
    /**
     * Updates the current fulfillment step with requirement counts
     */
    public synchronized void updateFulfillmentStep(FulfillmentStep step, String details, int totalRequirementsInStep) {
        updateFulfillmentStep(step, details);
        this.totalRequirementsInStep = totalRequirementsInStep;
        
        log.debug("Fulfillment step updated with {} total requirements", totalRequirementsInStep);
    }
    
    /**
     * Updates the current requirement being processed within a fulfillment step
     */
    public synchronized void updateCurrentRequirement(Object requirement, String requirementName, int requirementIndex) {
        this.currentRequirement = requirement;
        this.currentRequirementName = requirementName;
        this.currentRequirementIndex = requirementIndex;
        
        // Update details to show current requirement
        if (requirementName != null && totalRequirementsInStep > 0) {
            this.currentDetails = String.format("Processing: %s (%d/%d)", 
                requirementName, requirementIndex, totalRequirementsInStep);
        } else if (requirementName != null) {
            this.currentDetails = "Processing: " + requirementName;
        }
        
        log.debug("Current requirement updated to: {} ({}/{})", 
            requirementName, requirementIndex, totalRequirementsInStep);
    }
    

    
    /**
     * Marks the current execution as failed with an error message
     */
    public synchronized void markFailed(String errorMessage) {
        this.currentState = ExecutionState.FAILED;
        this.hasError = true;
        this.errorMessage = errorMessage;
        this.currentDetails = errorMessage;
        
        log.warn("Execution marked as failed: {}", errorMessage);
    }
    
    /**
     * Marks the current execution as having an error
     */
    public synchronized void markError(String errorMessage) {
        this.currentState = ExecutionState.ERROR;
        this.hasError = true;
        this.errorMessage = errorMessage;
        this.currentDetails = errorMessage;
        
        log.error("Execution marked as error: {}", errorMessage);
    }
    public synchronized void markCompleted() {
        this.currentState = ExecutionState.COMPLETED;
        this.hasError = false;
        this.errorMessage = null;
        this.currentDetails = "Execution completed successfully";
        
        log.info("Execution marked as completed");
    }
    public synchronized void markIdle() {
        this.currentPhase = ExecutionPhase.IDLE;
        this.currentState = ExecutionState.STARTING;
        this.currentStep = null;
        this.currentDetails = null;
        this.hasError = false;
        this.errorMessage = null;
        this.currentStepNumber = 0;
        this.totalSteps = 0;
        
        log.info("Execution state marked as idle");
    }
    public synchronized void clearRequirementState() {
        // Clear individual requirement tracking
        this.currentStep = null;
        this.currentRequirement = null;
        this.currentRequirementName = null;
        this.currentRequirementIndex = 0;
        this.totalRequirementsInStep = 0;
        
        log.debug("Current requirement state cleared");
    }
    /**
     * Clears all state and returns to idle
     */
    public synchronized void clear() {
        this.currentPhase = ExecutionPhase.IDLE;
        this.currentState = ExecutionState.STARTING;
        this.currentStep = null;
        this.currentDetails = null;
        this.hasError = false;
        this.errorMessage = null;
        this.currentStepNumber = 0;
        this.totalSteps = 0;
        this.currentRequirement = null;
        this.currentRequirementName = null;
        this.currentRequirementIndex = 0;
        this.totalRequirementsInStep = 0;
        
        log.debug("Execution state cleared");
    }
    
    /**
     * Resets all execution tracking to allow tasks to be run again.
     * This clears the completion flags but keeps current state if still executing.
     */
    public synchronized void reset() {
        this.hasPreTaskStarted = false;
        this.hasPreTaskCompleted = false;
        this.hasMainTaskStarted = false;
        this.hasMainTaskCompleted = false;
        this.hasPostTaskStarted = false;
        this.hasPostTaskCompleted = false;
        
        // Only clear current state if we're not actively executing
        if (currentPhase == ExecutionPhase.IDLE || hasError) {
            clear();
        }
        
        log.debug("\n\t##Task execution state reset - tasks can now be executed again##");
    }
    
    /**
     * Gets a concise status string for overlay display
     * @return A formatted status string, or null if idle
     */
    public String getDisplayStatus() {
        if (currentPhase == ExecutionPhase.IDLE) {
            return null;
        }
        
        StringBuilder status = new StringBuilder();
        status.append(currentPhase.getDisplayName());
        
        if (currentState == ExecutionState.FULFILLING_REQUIREMENTS && currentStep != null) {
            // Show step progress: "Pre-Schedule: Items (3/5)"
            status.append(": ").append(currentStep.getDisplayName())
                  .append(" (").append(currentStepNumber).append("/").append(totalSteps).append(")");
                  
            // Add requirement progress if available: "Pre-Schedule: Items (3/5) [2/4]"
            if (totalRequirementsInStep > 0 && currentRequirementIndex > 0) {
                status.append(" [").append(currentRequirementIndex).append("/").append(totalRequirementsInStep).append("]");
            }
        } else if (currentState != ExecutionState.STARTING) {
            // Show state: "Pre-Schedule: Custom Tasks"
            status.append(": ").append(currentState.getDisplayName());
        }
        
        return status.toString();
    }
    
    /**
     * Gets a detailed status string including current details and requirement name
     * @return A formatted detailed status string, or null if idle
     */
    public String getDetailedStatus() {
        String displayStatus = getDisplayStatus();
        if (displayStatus == null) {
            return null;
        }
        
        StringBuilder detailed = new StringBuilder(displayStatus);
        
        // Add current requirement name if available
        if (currentRequirementName != null && !currentRequirementName.isEmpty()) {
            detailed.append(" - ").append(currentRequirementName);
        } else if (currentDetails != null && !currentDetails.isEmpty()) {
            detailed.append(" - ").append(currentDetails);
        }
        
        return detailed.toString();
    }
    
    /**
     * Checks if any execution is currently in progress
     */
    public boolean isExecuting() {
        return currentPhase != ExecutionPhase.IDLE;
    }
    
    /**
     * Checks if requirements are currently being fulfilled
     */
    public boolean isFulfillingRequirements() {
        return currentState == ExecutionState.FULFILLING_REQUIREMENTS;
    }
    
    /**
     * Checks if the current execution is in an error state
     */
    public boolean isInErrorState() {
        return hasError;
    }
    
    /**
     * Gets the current progress as a percentage (0-100)
     */
    public int getProgressPercentage() {
        if (totalSteps == 0) {
            return 0;
        }
        return (int) ((double) currentStepNumber / totalSteps * 100);
    }
    
    // Convenience methods for checking task completion and execution states
    
    /**
     * Checks if pre-schedule tasks can be executed (not started or already completed)
     */
    public boolean canExecutePreTasks() {
        return !hasPreTaskStarted || hasPreTaskCompleted;
    }
    
    /**
     * Checks if main task can be executed (pre-tasks completed, main not started or already completed)
     */
    public boolean canExecuteMainTask() {
        return hasPreTaskCompleted && (!hasMainTaskStarted || hasMainTaskCompleted);
    }
    
    /**
     * Checks if post-schedule tasks can be executed (main task completed, post not started or already completed)
     */
    public boolean canExecutePostTasks() {
        return hasMainTaskStarted && (!hasPostTaskStarted || hasPostTaskCompleted);
    }
    
    /**
     * Checks if pre-schedule tasks are currently running
     */
    public boolean isPreTaskRunning() {
        return hasPreTaskStarted && !hasPreTaskCompleted && currentPhase == ExecutionPhase.PRE_SCHEDULE;
    }
    
    /**
     * Checks if main task is currently running
     */
    public boolean isMainTaskRunning() {
        return hasMainTaskStarted && !hasMainTaskCompleted && currentPhase == ExecutionPhase.MAIN_EXECUTION;
    }
    
    /**
     * Checks if post-schedule tasks are currently running
     */
    public boolean isPostTaskRunning() {
        return hasPostTaskStarted && !hasPostTaskCompleted && currentPhase == ExecutionPhase.POST_SCHEDULE;
    }
    
    /**
     * Checks if pre-schedule tasks are completed
     */
    public boolean isPreTaskComplete() {
        return hasPreTaskCompleted;
    }
    
    /**
     * Checks if main task is completed
     */
    public boolean isMainTaskComplete() {
        return hasMainTaskCompleted;
    }
    
    /**
     * Checks if post-schedule tasks are completed
     */
    public boolean isPostTaskComplete() {
        return hasPostTaskCompleted;
    }
    
    /**
     * Checks if all tasks (pre, main, post) are completed
     */
    public boolean areAllTasksComplete() {
        return hasPreTaskCompleted && hasMainTaskCompleted && hasPostTaskCompleted;
    }
}
