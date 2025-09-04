package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement;

import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
/**
 * Abstract base class for all requirement types in the plugin scheduler system.
 * This class defines common properties and behaviors for all requirements.
 */
@Getter
@AllArgsConstructor
@Slf4j
public abstract class Requirement implements Comparable<Requirement> {
    
    /**
     * The type of requirement (equipment, inventory, player state, etc.)
     */
    protected final RequirementType requirementType;
    
    /**
     * Priority level of this requirement for plugin functionality.
     */
    protected final RequirementPriority priority;
    
    /**
     * Effectiveness rating from 1-10 (10 being most effective).
     * Used for comparison when multiple valid options are available.
     */
    protected int rating;
    
    /**
     * Human-readable description explaining the purpose and effectiveness.
     * Should include context about why this requirement is useful for the specific plugin/activity.
     */
    protected final String description;
    
    /**
     * List of identifiers for this requirement.
     * These could be item IDs, NPC IDs, object IDs, varbit IDs, etc. depending on the requirement type.
     * For items, this can represent multiple alternative item IDs that satisfy the same requirement.
     * Can be empty if not applicable.
     */
    protected List<Integer> ids;
    
    /**
     * Context for when this requirement should be fulfilled.
     * PRE_SCHEDULE means before script execution, POST_SCHEDULE means after completion.
     * Can be null for requirements that don't have schedule-specific behavior.
     */
    @Setter
    protected TaskContext taskContext;
    
 
    /**
     * Gets the human-readable name of this requirement.
     * This is used for display purposes in overlays and logging.
     * 
     * @return The name of this requirement
     */
    public abstract String getName();
    
    /**
     * Abstract method to fulfill this requirement.
     * Each requirement type implements its own fulfillment logic.
     * 
     * @param scheduledFuture The CompletableFuture for cancellation support
     * @return true if the requirement was fulfilled successfully, false otherwise
     */
    public abstract boolean fulfillRequirement(CompletableFuture<Boolean> scheduledFuture);
    /**
     * Executes this step's requirement with timeout support.
     * 
     * @param scheduledFuture The CompletableFuture for cancellation support
     * @param timeoutSeconds Maximum time allowed for this step
     * @return true if successfully fulfilled, false otherwise
     */
    public boolean fulfillRequirementWithTimeout(CompletableFuture<Boolean> scheduledFuture, long timeoutSeconds) {
        try {
            log.debug("Executing ordered step with {}s timeout: {}", timeoutSeconds, description);
            
            CompletableFuture<Boolean> stepFuture = CompletableFuture.supplyAsync(() -> 
                fulfillRequirement(scheduledFuture)
            );
            
            return stepFuture.get(timeoutSeconds, TimeUnit.SECONDS);
        } catch (TimeoutException e) {
            log.error("Step '{}' timed out after {} seconds", description, timeoutSeconds);
            return !isMandatory();
        } catch (Exception e) {
            log.error("Error executing ordered step '{}': {}", description, e.getMessage());
            return !isMandatory();
        }
    }
    /**
     * Checks if this requirement is currently fulfilled.
     * This is a convenience method that calls fulfillRequirement() for consistency
     * with the condition system and logical requirements.
     * 
     * @return true if the requirement is fulfilled, false otherwise
     */
    public abstract boolean isFulfilled();
    
    
    /**
     * Compare requirements based on priority first, then rating.
     * This allows sorting requirements by importance.
     * 
     * @param other The requirement to compare with
     * @return A negative value if this requirement is more important, zero if equally important,
     *         or a positive value if less important
     */
    @Override
    public int compareTo(Requirement other) {
        // First compare by priority (MANDATORY > RECOMMENDED > OPTIONAL)
        int priorityComparison = other.getPriority().ordinal() - this.getPriority().ordinal();
        if (priorityComparison != 0) {
            return priorityComparison;
        }
        
        // If same priority, compare by rating (higher rating is better)
        return other.getRating() - this.getRating();
    }
    
    /**
     * Check if this is a mandatory requirement.
     * 
     * @return true if this requirement has MANDATORY priority, false otherwise
     */
    public boolean isMandatory() {
        return priority == RequirementPriority.MANDATORY;
    }
    
    /**
     * Check if this is a recommended requirement.
     * 
     * @return true if this requirement has RECOMMENDED priority, false otherwise
     */
    public boolean isRecommended() {
        return priority == RequirementPriority.RECOMMENDED;
    }
    
  
    
    /**
     * Check if this requirement should be fulfilled before script execution.
     * 
     * @return true if this requirement has PRE_SCHEDULE or BOTH context, false otherwise
     */
    public boolean isPreSchedule() {
        return taskContext == TaskContext.PRE_SCHEDULE || taskContext == TaskContext.BOTH;
    }
    
    /**
     * Check if this requirement should be fulfilled after script completion.
     * 
     * @return true if this requirement has POST_SCHEDULE or BOTH context, false otherwise
     */
    public boolean isPostSchedule() {
        return taskContext == TaskContext.POST_SCHEDULE || taskContext == TaskContext.BOTH;
    }
    
    /**
     * Check if this requirement has a specific schedule context task set.
     * Since TaskContext is never null (defaults to BOTH), this always returns true.
     * 
     * @return true always, as all requirements have a schedule context
     */
    public boolean hasTaskContext() {
        return taskContext != null;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        
        Requirement that = (Requirement) obj;
        return rating == that.rating &&
               Objects.equals(requirementType, that.requirementType) &&
               Objects.equals(priority, that.priority) &&
               Objects.equals(description, that.description) &&
               Objects.equals(ids, that.ids) &&
               Objects.equals(taskContext, that.taskContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requirementType, priority, rating, description, ids, taskContext);
    }
    
    /**
     * Returns a multi-line display string with detailed information about this requirement.
     * Uses StringBuilder with tabs for proper formatting.
     * 
     * @return A formatted string containing requirement details
     */
    public String displayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Requirement Details ===\n");
        sb.append("Name:\t\t").append(getName()).append("\n");
        sb.append("Type:\t\t").append(requirementType.name()).append("\n");
        sb.append("Priority:\t").append(priority.name()).append("\n");
        sb.append("Rating:\t\t").append(rating).append("/10\n");
        sb.append("Schedule:\t").append(taskContext.name()).append("\n");
        sb.append("IDs:\t\t").append(ids != null ? ids.toString() : "[]").append("\n");
        sb.append("Description:\t").append(description != null ? description : "No description").append("\n");
        return sb.toString();
    }
    
    /**
     * Gets a unique identifier for this requirement.
     * This is used by the RequirementRegistry to ensure uniqueness.
     * The default implementation uses the requirement type, description, and IDs.
     * Subclasses can override this for more specific uniqueness logic.
     * 
     * @return A unique identifier string for this requirement
     */
    public String getUniqueIdentifier() {
        return String.format("%s:%s:%s", 
                requirementType.name(), 
                description != null ? description.hashCode() : "null",
                ids != null ? ids.hashCode() : "null");
    }
}
