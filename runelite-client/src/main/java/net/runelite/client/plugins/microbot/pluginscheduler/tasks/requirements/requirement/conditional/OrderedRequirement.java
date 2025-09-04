package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

/**
 * An ordered requirement that executes requirements in strict sequence.
 * Unlike ConditionalRequirement which uses conditions, this executes ALL steps in order.
 * 
 * Perfect for workflows where order matters:
 * - First shop for supplies
 * - Then loot materials 
 * - Then equip gear
 * - Then go to location
 * 
 * Each step must complete successfully before proceeding to the next.
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class OrderedRequirement extends Requirement {
    
    /**
     * Represents a single ordered step in the sequence.
     */
    @Getter
    public static class OrderedStep {
        private final Requirement requirement;
        private final String description;
        private final boolean isMandatory;
        
        /**
         * Creates a mandatory ordered step.
         * 
         * @param requirement The requirement to fulfill
         * @param description Human-readable description of this step
         */
        public OrderedStep(Requirement requirement, String description) {
            this(requirement, description, true);
        }
        
        /**
         * Creates an ordered step.
         * 
         * @param requirement The requirement to fulfill
         * @param description Human-readable description of this step
         * @param isMandatory Whether this step must succeed for the sequence to continue
         */
        public OrderedStep(Requirement requirement, String description, boolean isMandatory) {
            this.requirement = requirement;
            this.description = description;
            this.isMandatory = isMandatory;
        }
        
        /**
         * Executes this step's requirement.
         * 
         * @param executorService The ScheduledExecutorService on which fulfillment is running
         * @return true if successfully fulfilled, false otherwise
         */
        public boolean execute(CompletableFuture<Boolean> scheduledFuture) {
            try {
                log.debug("Executing ordered step: {}", description);
                return requirement.fulfillRequirement(scheduledFuture);
            } catch (Exception e) {
                log.error("Error executing ordered step '{}': {}", description, e.getMessage());
                return false; // Defer optional skip policy to the caller (allowSkipOptional)
            }
        }
        
        /**
         * Checks if this step is currently fulfilled.
         * 
         * @return true if the requirement is fulfilled
         */
        public boolean isFulfilled() {
            try {
                return requirement.isFulfilled();
            } catch (Exception e) {
                log.warn("Error checking fulfillment for step '{}': {}", description, e.getMessage());
                return false;
            }
        }
    }
    
    @Getter
    private final List<OrderedStep> steps = new ArrayList<>();
    
    @Getter
    private final boolean allowSkipOptional;
    
    @Getter
    private final boolean resumeFromLastFailed;
    
    // Execution state tracking
    private volatile int currentStepIndex = 0;
    private volatile int lastCompletedStep = -1;
    private volatile boolean allStepsCompleted = false;
    private volatile String lastFailureReason = null;
    
    /**
     * Creates an ordered requirement.
     * 
     * @param priority Priority level for this ordered requirement
     * @param rating Effectiveness rating (1-10)
     * @param description Human-readable description
     * @param TaskContext When this requirement should be fulfilled
     * @param allowSkipOptional Whether optional steps can be skipped on failure
     * @param resumeFromLastFailed Whether to resume from the last failed step or restart
     */
    public OrderedRequirement(RequirementPriority priority, int rating, String description, 
                            TaskContext taskContext, boolean allowSkipOptional, 
                            boolean resumeFromLastFailed) {
        super(RequirementType.CONDITIONAL, priority, rating, description, List.of(), taskContext);
        this.allowSkipOptional = allowSkipOptional;
        this.resumeFromLastFailed = resumeFromLastFailed;
    }
    
    /**
     * Creates an ordered requirement with default settings.
     * 
     * @param priority Priority level for this ordered requirement
     * @param rating Effectiveness rating (1-10)
     * @param description Human-readable description
     * @param TaskContext When this requirement should be fulfilled
     */
    public OrderedRequirement(RequirementPriority priority, int rating, String description, TaskContext taskContext) {
        this(priority, rating, description, taskContext, true, true);
    }
    
    /**
     * Adds a mandatory step to this ordered requirement.
     * Steps are executed in the order they are added.
     * 
     * @param requirement The requirement to fulfill
     * @param description Description of this step
     * @return This OrderedRequirement for method chaining
     */
    public OrderedRequirement addStep(Requirement requirement, String description) {
        return addStep(requirement, description, true);
    }
    
    /**
     * Adds an optional step to this ordered requirement.
     * 
     * @param requirement The requirement to fulfill
     * @param description Description of this step
     * @return This OrderedRequirement for method chaining
     */
    public OrderedRequirement addOptionalStep(Requirement requirement, String description) {
        return addStep(requirement, description, false);
    }
    
    /**
     * Adds a step to this ordered requirement.
     * 
     * @param requirement The requirement to fulfill
     * @param description Description of this step
     * @param isMandatory Whether this step must succeed
     * @return This OrderedRequirement for method chaining
     */
    public OrderedRequirement addStep(Requirement requirement, String description, boolean isMandatory) {
        steps.add(new OrderedStep(requirement, description, isMandatory));
        return this;
    }
    
    @Override
    public String getName() {
        return "Ordered: " + getDescription();
    }
    
    @Override
    public boolean fulfillRequirement(CompletableFuture<Boolean> scheduledFuture) {
        log.debug("Starting ordered requirement fulfillment: {}", getName());
        
        // Determine starting point
        int startIndex = resumeFromLastFailed ? Math.max(0, lastCompletedStep + 1) : 0;
        
        if (startIndex == 0) {
            // Fresh start - reset state
            currentStepIndex = 0;
            lastCompletedStep = -1;
            allStepsCompleted = false;
            lastFailureReason = null;
        }
        
        if (steps.isEmpty()) {
            log.warn("No steps defined for ordered requirement: {}", getName());
            return true; // Empty requirement is considered fulfilled
        }
        
        // Execute steps in strict order starting from determined index
        for (int i = startIndex; i < steps.size(); i++) {
            if( scheduledFuture!= null && (scheduledFuture.isCancelled() || scheduledFuture.isDone())) {
                log.warn("Ordered requirement execution cancelled or completed prematurely: {}", getName());
                return false; // Stop if the scheduled future is cancelled or done
            }
            currentStepIndex = i;
            OrderedStep step = steps.get(i);
            
            log.debug("Executing ordered step {}: {}", i, step.getDescription());
            boolean success = step.execute(scheduledFuture);
            
            if (success) {
                lastCompletedStep = i;
                log.debug("Completed step {}: {}", i, step.getDescription());
            } else {
                lastFailureReason = "Step " + i + " failed: " + step.getDescription();
                log.warn("Ordered requirement step failed: {}", lastFailureReason);
                
                if (step.isMandatory()) {
                    log.error("Stopping ordered requirement due to mandatory step failure: {}", lastFailureReason);
                    return false;
                } else if (!allowSkipOptional) {
                    log.error("Stopping ordered requirement due to optional step failure (skip not allowed): {}", lastFailureReason);
                    return false;
                } else {
                    log.warn("Skipping optional step failure: {}", lastFailureReason);
                    lastCompletedStep = i; // Mark as completed even though it failed (optional)
                }
            }
        }
        
        allStepsCompleted = true;
        log.debug("Ordered requirement completed successfully: {}", getName());
        return true;
    }
    
    /**
     * Checks if this ordered requirement is currently fulfilled.
     * This checks if all mandatory steps have been completed.
     */
    @Override
    public boolean isFulfilled() {
        // If we haven't completed all steps, check current state
        if (!allStepsCompleted) {
            return false;
        }
        
        // Check if all mandatory steps are still fulfilled
        for (OrderedStep step : steps) {
            if (step.isMandatory() && !step.isFulfilled()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Gets the current execution progress as a percentage.
     * 
     * @return Progress from 0.0 to 1.0
     */
    public double getExecutionProgress() {
        if (steps.isEmpty()) {
            return 1.0;
        }
        return (double) (lastCompletedStep + 1) / steps.size();
    }
    
    /**
     * Gets the current step being executed.
     * 
     * @return The current step, or null if not started or completed
     */
    public OrderedStep getCurrentStep() {
        if (currentStepIndex >= 0 && currentStepIndex < steps.size()) {
            return steps.get(currentStepIndex);
        }
        return null;
    }
    
    /**
     * Gets the next step to be executed.
     * 
     * @return The next step, or null if all steps completed
     */
    public OrderedStep getNextStep() {
        int nextIndex = lastCompletedStep + 1;
        if (nextIndex >= 0 && nextIndex < steps.size()) {
            return steps.get(nextIndex);
        }
        return null;
    }
    
    /**
     * Gets the number of completed steps.
     * 
     * @return Number of successfully completed steps
     */
    public int getCompletedStepCount() {
        return lastCompletedStep + 1;
    }
    
    /**
     * Gets the number of remaining steps.
     * 
     * @return Number of steps still to be executed
     */
    public int getRemainingStepCount() {
        return Math.max(0, steps.size() - getCompletedStepCount());
    }
    
    /**
     * Resets the execution state to start from the beginning.
     */
    public void reset() {
        currentStepIndex = 0;
        lastCompletedStep = -1;
        allStepsCompleted = false;
        lastFailureReason = null;
        log.debug("Reset ordered requirement: {}", getName());
    }
    
    /**
     * Gets the reason for the last failure, if any.
     * 
     * @return Failure reason or null if no failure
     */
    public String getLastFailureReason() {
        return lastFailureReason;
    }
    
    /**
     * Gets a detailed status string for display/debugging.
     * 
     * @return Status string with progress and current step info
     */
    public String getDetailedStatus() {
        StringBuilder sb = new StringBuilder();
        sb.append(getName()).append(" - ");
        
        if (allStepsCompleted) {
            sb.append("Completed (").append(steps.size()).append("/").append(steps.size()).append(" steps)");
        } else {
            sb.append("Progress: ").append(getCompletedStepCount()).append("/").append(steps.size()).append(" steps");
            
            OrderedStep nextStep = getNextStep();
            if (nextStep != null) {
                sb.append(" - Next: ").append(nextStep.getDescription());
            }
        }
        
        if (lastFailureReason != null) {
            sb.append(" - Last failure: ").append(lastFailureReason);
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "OrderedRequirement{" +
                "name='" + getName() + '\'' +
                ", steps=" + steps.size() +
                ", completed=" + getCompletedStepCount() +
                ", allCompleted=" + allStepsCompleted +
                '}';
    }
    
    /**
     * Returns a detailed display string with ordered requirement information.
     * 
     * @return A formatted string containing ordered requirement details
     */
    @Override
    public String displayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Ordered Requirement Details ===\n");
        sb.append("Name:\t\t\t").append(getName()).append("\n");
        sb.append("Type:\t\t\t").append(getRequirementType().name()).append("\n");
        sb.append("Priority:\t\t").append(getPriority().name()).append("\n");
        sb.append("Rating:\t\t\t").append(getRating()).append("/10\n");
        sb.append("Schedule Context:\t").append(getTaskContext().name()).append("\n");
        sb.append("Allow Skip Optional:\t").append(allowSkipOptional ? "Yes" : "No").append("\n");
        sb.append("Resume from Failed:\t").append(resumeFromLastFailed ? "Yes" : "No").append("\n");
        sb.append("Total Steps:\t\t").append(steps.size()).append("\n");
        sb.append("Completed Steps:\t").append(getCompletedStepCount()).append("/").append(steps.size()).append("\n");
        sb.append("Progress:\t\t").append(String.format("%.1f%%", getExecutionProgress() * 100)).append("\n");
        sb.append("All Completed:\t\t").append(allStepsCompleted ? "Yes" : "No").append("\n");
        sb.append("Description:\t\t").append(getDescription() != null ? getDescription() : "No description").append("\n");
        
        // Add step details
        sb.append("\n--- Steps Details ---\n");
        for (int i = 0; i < steps.size(); i++) {
            OrderedStep step = steps.get(i);
            sb.append("Step ").append(i + 1).append(":\t\t").append(step.getDescription()).append("\n");
            sb.append("\t\t\tMandatory: ").append(step.isMandatory() ? "Yes" : "No").append("\n");
            sb.append("\t\t\tRequirement: ").append(step.getRequirement().getName()).append("\n");
            
            // Show execution status
            if (i < currentStepIndex) {
                sb.append("\t\t\tStatus: Completed\n");
            } else if (i == currentStepIndex && !allStepsCompleted) {
                sb.append("\t\t\tStatus: Current Step\n");
            } else {
                sb.append("\t\t\tStatus: Pending\n");
            }
        }
        
        if (lastFailureReason != null) {
            sb.append("\n--- Last Failure ---\n");
            sb.append("Reason:\t\t\t").append(lastFailureReason).append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Enhanced toString method that uses displayString for comprehensive output.
     * 
     * @return A comprehensive string representation of this ordered requirement
     */
    public String toDetailedString() {
        return displayString();
    }
}
