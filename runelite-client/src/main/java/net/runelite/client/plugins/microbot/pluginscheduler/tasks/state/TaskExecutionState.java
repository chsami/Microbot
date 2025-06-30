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
    private volatile FulfillmentStep currentStep = null;
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
    private volatile Object currentRequirement = null; // The specific requirement being processed
    @Getter
    private volatile String currentRequirementName = null; // Human-readable name of current requirement
    @Getter
    private volatile int currentRequirementIndex = 0; // Current requirement index within step
    @Getter
    private volatile int totalRequirementsInStep = 0; // Total requirements in current step
    
    /**
     * Updates the current execution phase and resets step tracking
     */
    public synchronized void updatePhase(ExecutionPhase phase) {
        this.currentPhase = phase;
        this.currentState = ExecutionState.STARTING;
        this.currentStep = null;
        this.currentDetails = null;
        this.hasError = false;
        this.errorMessage = null;
        this.currentStepNumber = 0;
        this.totalSteps = 0;
        
        log.debug("Execution phase updated to: {}", phase.getDisplayName());
    }
    
    /**
     * Updates the current execution state within the current phase
     */
    public synchronized void updateState(ExecutionState state, String details) {
        this.currentState = state;
        this.currentDetails = details;
        
        if (state == ExecutionState.ERROR || state == ExecutionState.FAILED) {
            this.hasError = true;
            this.errorMessage = details;
        }
        
        log.debug("Execution state updated to: {} - {}", state.getDisplayName(), details);
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
     * Marks the current step as completed and moves to the next step
     */
    public synchronized void completeCurrentStep() {
        if (currentStep != null) {
            FulfillmentStep nextStep = currentStep.getNext();
            if (nextStep != null) {
                updateFulfillmentStep(nextStep, "Starting " + nextStep.getDescription());
            } else {
                // All fulfillment steps completed
                updateState(ExecutionState.COMPLETED, "All requirements fulfilled");
            }
        }
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
}
