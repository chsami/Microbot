package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement;

import lombok.Getter;
import lombok.Setter;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;

/**
 * Abstract base class for all requirement types in the plugin scheduler system.
 * This class defines common properties and behaviors for all requirements.
 */
@Getter
@AllArgsConstructor
@EqualsAndHashCode
public abstract class Requirement implements Comparable<Requirement> {
    
    /**
     * The type of requirement (equipment, inventory, player state, etc.)
     */
    protected final RequirementType requirementType;
    
    /**
     * Priority level of this requirement for plugin functionality.
     */
    protected final Priority priority;
    
    /**
     * Effectiveness rating from 1-10 (10 being most effective).
     * Used for comparison when multiple valid options are available.
     */
    protected final int rating;
    
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
    protected final List<Integer> ids;
    
    /**
     * Context for when this requirement should be fulfilled.
     * PRE_SCHEDULE means before script execution, POST_SCHEDULE means after completion.
     * Can be null for requirements that don't have schedule-specific behavior.
     */
    @Setter
    protected ScheduleContext scheduleContext;
    
 
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
     * @param executorService The ScheduledExecutorService on which this requirement fulfillment is running
     * @return true if the requirement was fulfilled successfully, false otherwise
     */
    public abstract boolean fulfillRequirement(CompletableFuture<Boolean> scheduledFuture);
            
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
        return priority == Priority.MANDATORY;
    }
    
    /**
     * Check if this is a recommended requirement.
     * 
     * @return true if this requirement has RECOMMENDED priority, false otherwise
     */
    public boolean isRecommended() {
        return priority == Priority.RECOMMENDED;
    }
    
    /**
     * Check if this is an optional requirement.
     * 
     * @return true if this requirement has OPTIONAL priority, false otherwise
     */
    public boolean isOptional() {
        return priority == Priority.OPTIONAL;
    }
    
    /**
     * Check if this requirement should be fulfilled before script execution.
     * 
     * @return true if this requirement has PRE_SCHEDULE or BOTH context, false otherwise
     */
    public boolean isPreSchedule() {
        return scheduleContext == ScheduleContext.PRE_SCHEDULE || scheduleContext == ScheduleContext.BOTH;
    }
    
    /**
     * Check if this requirement should be fulfilled after script completion.
     * 
     * @return true if this requirement has POST_SCHEDULE or BOTH context, false otherwise
     */
    public boolean isPostSchedule() {
        return scheduleContext == ScheduleContext.POST_SCHEDULE || scheduleContext == ScheduleContext.BOTH;
    }
    
    /**
     * Check if this requirement has a specific schedule context.
     * Since scheduleContext is never null (defaults to BOTH), this always returns true.
     * 
     * @return true always, as all requirements have a schedule context
     */
    public boolean hasScheduleContext() {
        return true;
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
               Objects.equals(scheduleContext, that.scheduleContext);
    }

    @Override
    public int hashCode() {
        return Objects.hash(requirementType, priority, rating, description, ids, scheduleContext);
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
        sb.append("Schedule:\t").append(scheduleContext.name()).append("\n");
        sb.append("IDs:\t\t").append(ids.toString()).append("\n");
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
