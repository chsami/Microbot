package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;

/**
 * A conditional requirement that executes requirements in sequence based on conditions.
 * This addresses the real-world OSRS preparation workflows where order matters:
 * 
 * Examples:
 * - "If we don't have lunar spellbook AND magic level >= 65, switch to lunar"
 * - "First ensure we're at bank, then shop for items, then loot materials, then equip gear"
 * - "If missing rune pouches, shop for them, then ensure NPC contact runes"
 * 
 * This is much more powerful than simple AND/OR logic because:
 * - Handles temporal dependencies (sequence matters)
 * - Represents real OSRS preparation workflows
 * - Provides conditional logic based on game state
 * - Allows for complex decision trees
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class ConditionalRequirement extends Requirement {
    
    /**
     * Represents a single conditional step in the sequence.
     */
    @Getter
    public static class ConditionalStep {
        private final BooleanSupplier condition;
        private final Requirement requirement;
        private final String description;
        private final boolean isOptional;
        
        /**
         * Creates a mandatory conditional step.
         * 
         * @param condition The condition to check (e.g., () -> !Rs2Player.hasLunarSpellbook())
         * @param requirement The requirement to fulfill if condition is true
         * @param description Human-readable description of this step
         */
        public ConditionalStep(BooleanSupplier condition, Requirement requirement, String description) {
            this(condition, requirement, description, false);
        }
        
        /**
         * Creates a conditional step.
         * 
         * @param condition The condition to check
         * @param requirement The requirement to fulfill if condition is true
         * @param description Human-readable description of this step
         * @param isOptional Whether this step can be skipped if it fails
         */
        public ConditionalStep(BooleanSupplier condition, Requirement requirement, String description, boolean isOptional) {
            this.condition = condition;
            this.requirement = requirement;
            this.description = description;
            this.isOptional = isOptional;
        }
        
        /**
         * Checks if this step's condition is met and needs execution.
         * 
         * @return true if the condition is true (step needs to be executed)
         */
        public boolean needsExecution() {
            try {
                return condition.getAsBoolean();
            } catch (Exception e) {
                log.warn("Error checking condition for step '{}': {}", description, e.getMessage());
                return false;
            }
        }
        
        /**
         * Executes this step's requirement.
         * 
         * @param executorService The ScheduledExecutorService on which fulfillment is running
         * @return true if successfully fulfilled, false otherwise
         */
        public boolean execute(CompletableFuture<Boolean> scheduledFuture) {
            try {
                log.debug("Executing conditional step: {}", description);
                return requirement.fulfillRequirement(scheduledFuture);
            } catch (Exception e) {
                log.error("Error executing conditional step '{}': {}", description, e.getMessage());
                return isOptional; // Optional steps return true on error, mandatory steps return false
            }
        }
    }
    
    @Getter
    private final List<ConditionalStep> steps = new ArrayList<>();
    
    @Getter
    private final boolean stopOnFirstFailure;
    
    @Getter
    private final boolean allowParallelExecution;
    
    // Execution state tracking
    private volatile int currentStepIndex = 0;
    private volatile boolean allStepsCompleted = false;
    private volatile String lastFailureReason = null;
    
    /**
     * Creates a conditional requirement with sequential execution.
     * 
     * @param priority Priority level for this conditional requirement
     * @param rating Effectiveness rating (1-10)
     * @param description Human-readable description
     * @param scheduleContext When this requirement should be fulfilled
     * @param stopOnFirstFailure Whether to stop execution on first failure
     */
    public ConditionalRequirement(Priority priority, int rating, String description, 
                                ScheduleContext scheduleContext, boolean stopOnFirstFailure) {
        this(priority, rating, description, scheduleContext, stopOnFirstFailure, false);
    }
    
    /**
     * Creates a conditional requirement.
     * 
     * @param priority Priority level for this conditional requirement
     * @param rating Effectiveness rating (1-10)
     * @param description Human-readable description
     * @param scheduleContext When this requirement should be fulfilled
     * @param stopOnFirstFailure Whether to stop execution on first failure
     * @param allowParallelExecution Whether steps can be executed in parallel (when conditions don't depend on each other)
     */
    public ConditionalRequirement(Priority priority, int rating, String description, 
                                ScheduleContext scheduleContext, boolean stopOnFirstFailure, 
                                boolean allowParallelExecution) {
        super(RequirementType.CONDITIONAL, priority, rating, description, List.of(), scheduleContext);
        this.stopOnFirstFailure = stopOnFirstFailure;
        this.allowParallelExecution = allowParallelExecution;
    }
    
    /**
     * Adds a conditional step to this requirement.
     * Steps are executed in the order they are added.
     * 
     * @param condition The condition to check
     * @param requirement The requirement to fulfill if condition is true
     * @param description Description of this step
     * @return This ConditionalRequirement for method chaining
     */
    public ConditionalRequirement addStep(BooleanSupplier condition, Requirement requirement, String description) {
        return addStep(condition, requirement, description, false);
    }
    
    /**
     * Adds a conditional step to this requirement.
     * 
     * @param condition The condition to check
     * @param requirement The requirement to fulfill if condition is true
     * @param description Description of this step
     * @param isOptional Whether this step can be skipped if it fails
     * @return This ConditionalRequirement for method chaining
     */
    public ConditionalRequirement addStep(BooleanSupplier condition, Requirement requirement, 
                                         String description, boolean isOptional) {
        steps.add(new ConditionalStep(condition, requirement, description, isOptional));
        return this;
    }
    
    /**
     * Adds a step that always executes (unconditional).
     * 
     * @param requirement The requirement to always fulfill
     * @param description Description of this step
     * @return This ConditionalRequirement for method chaining
     */
    public ConditionalRequirement addAlwaysStep(Requirement requirement, String description) {
        return addStep(() -> true, requirement, description, false);
    }
    
    /**
     * Adds a step that executes only if a condition is NOT met.
     * 
     * @param condition The condition to check (step executes if this is false)
     * @param requirement The requirement to fulfill if condition is false
     * @param description Description of this step
     * @return This ConditionalRequirement for method chaining
     */
    public ConditionalRequirement addIfNotStep(BooleanSupplier condition, Requirement requirement, String description) {
        return addStep(() -> !condition.getAsBoolean(), requirement, description, false);
    }
    
    @Override
    public String getName() {
        return "Conditional: " + getDescription();
    }
    
    @Override
    public boolean fulfillRequirement(CompletableFuture<Boolean> scheduledFuture) {
        log.debug("Starting conditional requirement fulfillment: {}", getName());
        
        // Reset state
        currentStepIndex = 0;
        allStepsCompleted = false;
        lastFailureReason = null;
        
        if (steps.isEmpty()) {
            log.warn("No steps defined for conditional requirement: {}", getName());
            return true; // Empty requirement is considered fulfilled
        }
        
        // Execute steps in sequence
        for (int i = 0; i < steps.size(); i++) {
            if( scheduledFuture != null && scheduledFuture.isCancelled() || scheduledFuture.isDone()) {
                log.warn("Conditional requirement execution cancelled or completed prematurely: {}", getName());
                return false; // Stop if the scheduled future is cancelled or done
            }
            currentStepIndex = i;
            ConditionalStep step = steps.get(i);
            
            // Check if this step needs execution
            if (!step.needsExecution()) {
                log.debug("Skipping step {} (condition not met): {}", i, step.getDescription());
                continue;
            }
            
            // Execute the step
            log.debug("Executing step {}: {}", i, step.getDescription());
            boolean success = step.execute(scheduledFuture);
            
            if (!success) {
                lastFailureReason = "Step " + i + " failed: " + step.getDescription();
                log.warn("Conditional requirement step failed: {}", lastFailureReason);
                
                if (stopOnFirstFailure && !step.isOptional()) {
                    log.error("Stopping conditional requirement due to mandatory step failure: {}", lastFailureReason);
                    return false;
                }
            }
        }
        
        allStepsCompleted = true;
        log.debug("Conditional requirement completed successfully: {}", getName());
        return true;
    }
    
    /**
     * Checks if this conditional requirement is currently fulfilled.
     * This checks if all mandatory steps have been executed successfully.
     */
    @Override
    public boolean isFulfilled() {
        // If we haven't started or haven't completed all steps, not fulfilled
        if (!allStepsCompleted) {
            return false;
        }
        
        // Check if any mandatory steps still need execution
        for (ConditionalStep step : steps) {
            if (step.needsExecution() && !step.isOptional()) {
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
        return (double) currentStepIndex / steps.size();
    }
    
    /**
     * Gets the current step being executed.
     * 
     * @return The current step, or null if not started or completed
     */
    public ConditionalStep getCurrentStep() {
        if (currentStepIndex >= 0 && currentStepIndex < steps.size()) {
            return steps.get(currentStepIndex);
        }
        return null;
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
            sb.append("Completed");
        } else {
            sb.append("Progress: ").append(String.format("%.0f%%", getExecutionProgress() * 100));
            
            ConditionalStep currentStep = getCurrentStep();
            if (currentStep != null) {
                sb.append(" - Current: ").append(currentStep.getDescription());
            }
        }
        
        if (lastFailureReason != null) {
            sb.append(" - Last failure: ").append(lastFailureReason);
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        return "ConditionalRequirement{" +
                "name='" + getName() + '\'' +
                ", steps=" + steps.size() +
                ", progress=" + String.format("%.0f%%", getExecutionProgress() * 100) +
                ", completed=" + allStepsCompleted +
                '}';
    }
    
    /**
     * Returns a detailed display string with conditional requirement information.
     * 
     * @return A formatted string containing conditional requirement details
     */
    @Override
    public String displayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Conditional Requirement Details ===\n");
        sb.append("Name:\t\t\t").append(getName()).append("\n");
        sb.append("Type:\t\t\t").append(getRequirementType().name()).append("\n");
        sb.append("Priority:\t\t").append(getPriority().name()).append("\n");
        sb.append("Rating:\t\t\t").append(getRating()).append("/10\n");
        sb.append("Schedule Context:\t").append(getScheduleContext().name()).append("\n");
        sb.append("Stop on Failure:\t").append(stopOnFirstFailure ? "Yes" : "No").append("\n");
        sb.append("Parallel Execution:\t").append(allowParallelExecution ? "Yes" : "No").append("\n");
        sb.append("Total Steps:\t\t").append(steps.size()).append("\n");
        sb.append("Progress:\t\t").append(String.format("%.1f%%", getExecutionProgress() * 100)).append("\n");
        sb.append("Completed:\t\t").append(allStepsCompleted ? "Yes" : "No").append("\n");
        sb.append("Description:\t\t").append(getDescription() != null ? getDescription() : "No description").append("\n");
        
        // Add step details
        sb.append("\n--- Steps Details ---\n");
        for (int i = 0; i < steps.size(); i++) {
            ConditionalStep step = steps.get(i);
            sb.append("Step ").append(i + 1).append(":\t\t").append(step.getDescription()).append("\n");
            sb.append("\t\t\tOptional: ").append(step.isOptional() ? "Yes" : "No").append("\n");
            sb.append("\t\t\tRequirement: ").append(step.getRequirement().getName()).append("\n");
            if (i < currentStepIndex) {
                sb.append("\t\t\tStatus: Completed\n");
            } else if (i == currentStepIndex) {
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
     * @return A comprehensive string representation of this conditional requirement
     */
    public String toDetailedString() {
        return displayString();
    }
}
