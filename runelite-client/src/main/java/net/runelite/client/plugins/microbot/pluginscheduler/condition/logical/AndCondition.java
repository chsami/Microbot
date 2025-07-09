package net.runelite.client.plugins.microbot.pluginscheduler.condition.logical;

import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.List;
import java.util.ArrayList;
import lombok.EqualsAndHashCode;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeCondition;

/**
 * Logical AND combination of conditions - all must be met.
 */
@EqualsAndHashCode(callSuper = true)
public class AndCondition extends LogicalCondition {
    @Override
    public boolean isSatisfied() {
        if (conditions.isEmpty()) return true;
        return conditions.stream().allMatch(Condition::isSatisfied);
    }
    
    /**
     * Returns a detailed description of the AND condition with additional status information
     */
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        
        // Basic description
        sb.append("AND Logical Condition: All conditions must be satisfied\n");
        
        // Status information
        boolean satisfied = isSatisfied();
        sb.append("Status: ").append(satisfied ? "Satisfied" : "Not satisfied").append("\n");
        sb.append("Child Conditions: ").append(conditions.size()).append("\n");
        
        // Progress information
        double progress = getProgressPercentage();
        sb.append(String.format("Overall Progress: %.1f%%\n", progress));
        
        // Count satisfied conditions
        int satisfiedCount = 0;
        for (Condition condition : conditions) {
            if (condition.isSatisfied()) {
                satisfiedCount++;
            }
        }
        sb.append("Satisfied Conditions: ").append(satisfiedCount).append("/").append(conditions.size()).append("\n\n");
        
        // List all child conditions
        sb.append("Child Conditions:\n");
        for (int i = 0; i < conditions.size(); i++) {
            Condition condition = conditions.get(i);
            sb.append(String.format("%d. %s [%s]\n", 
                    i + 1, 
                    condition.getDescription(),
                    condition.isSatisfied() ? "SATISFIED" : "NOT SATISFIED"));
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        // Basic information
        sb.append("AndCondition:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        sb.append("  │ Type: AND (All conditions must be satisfied)\n");
        sb.append("  │ Child Conditions: ").append(conditions.size()).append("\n");
        
        // Status information
        sb.append("  ├─ Status ──────────────────────────────────\n");
        boolean allSatisfied = isSatisfied();
        sb.append("  │ Satisfied: ").append(allSatisfied).append("\n");
        
        // Count satisfied conditions
        int satisfiedCount = 0;
        for (Condition condition : conditions) {
            if (condition.isSatisfied()) {
                satisfiedCount++;
            }
        }
        sb.append("  │ Satisfied Conditions: ").append(satisfiedCount).append("/").append(conditions.size()).append("\n");
        sb.append("  │ Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
        
        // Child conditions
        if (!conditions.isEmpty()) {
            sb.append("  ├─ Child Conditions ────────────────────────\n");
            
            for (int i = 0; i < conditions.size(); i++) {
                Condition condition = conditions.get(i);
                String prefix = (i == conditions.size() - 1) ? "  └─ " : "  ├─ ";
                
                sb.append(prefix).append(String.format("Condition %d: %s [%s]\n", 
                        i + 1, 
                        condition.getClass().getSimpleName(),
                        condition.isSatisfied() ? "SATISFIED" : "NOT SATISFIED"));
            }
        } else {
            sb.append("  └─ No Child Conditions ───────────────────────\n");
        }
        
        return sb.toString();
    }

    /**
     * For an AND condition, any unsatisfied child condition blocks the entire AND.
     * This method returns all child conditions that are currently not satisfied.
     * 
     * @return List of all unsatisfied child conditions
     */
    @Override
    public List<Condition> getBlockingConditions() {
        List<Condition> blockingConditions = new ArrayList<>();
        
        // In an AND condition, any unsatisfied condition blocks the entire AND
        for (Condition condition : conditions) {
            if (!condition.isSatisfied()) {
                blockingConditions.add(condition);
            }
        }
        
        return blockingConditions;
    }

    /**
     * Gets the next time this AND condition will be satisfied.
     * If all conditions are satisfied, returns the minimum trigger time among all conditions.
     * If any condition is not satisfied, returns the maximum trigger time among unsatisfied TimeConditions.
     * 
     * @return Optional containing the next trigger time, or empty if none available
     */
    @Override
    public Optional<ZonedDateTime> getCurrentTriggerTime() {
        if (conditions.isEmpty()) {
            return Optional.empty();
        }
        
        boolean allSatisfied = true;
        ZonedDateTime minTriggerTime = null;
        ZonedDateTime maxUnsatisfiedTimeConditionTriggerTime = null;
        
        // Check if all conditions are satisfied and track min trigger time
        for (Condition condition : conditions) {
            // Check if this condition is satisfied
            if (!condition.isSatisfied()) {
                allSatisfied = false;
                
                // For unsatisfied TimeConditions, track the maximum trigger time
                if (condition instanceof TimeCondition) {
                    Optional<ZonedDateTime> nextTrigger = condition.getCurrentTriggerTime();
                    if (nextTrigger.isPresent()) {
                        ZonedDateTime triggerTime = nextTrigger.get();
                        if (maxUnsatisfiedTimeConditionTriggerTime == null || 
                            triggerTime.isAfter(maxUnsatisfiedTimeConditionTriggerTime)) {
                            maxUnsatisfiedTimeConditionTriggerTime = triggerTime;
                        }
                    }
                }
            } 
            // If satisfied, track the minimum trigger time
            else {
                Optional<ZonedDateTime> triggerTime = condition.getCurrentTriggerTime();
                if (triggerTime.isPresent()) {
                    if (minTriggerTime == null || triggerTime.get().isBefore(minTriggerTime)) {
                        minTriggerTime = triggerTime.get();
                    }
                }
            }
        }
        
        // If all conditions are satisfied, return the minimum trigger time
        if (allSatisfied) {
            return minTriggerTime != null ? Optional.of(minTriggerTime) : Optional.empty();
        }
        
        // If at least one condition is not satisfied, return the maximum trigger time
        // of unsatisfied TimeConditions
        return maxUnsatisfiedTimeConditionTriggerTime != null ? 
            Optional.of(maxUnsatisfiedTimeConditionTriggerTime) : Optional.empty();
    }
    public void pause() {
        // Pause all child conditions
        for (Condition condition : conditions) {
            condition.pause();
        }
                
        
    }
    
   
    public void resume() {
        // Resume all child conditions
        for (Condition condition : conditions) {
            condition.resume();
        }        
        
    }

    /**
     * Gets the estimated time until this AND condition will be satisfied.
     * For an AND condition, this returns the maximum (latest) estimated time
     * among all child conditions, since all conditions must be satisfied
     * for the entire AND condition to be satisfied.
     * 
     * @return Optional containing the estimated duration until satisfaction, or empty if not determinable
     */
    @Override
    public Optional<Duration> getEstimatedTimeWhenIsSatisfied() {
        if (conditions.isEmpty()) {
            return Optional.of(Duration.ZERO);
        }
        
        // If all conditions are already satisfied, return zero
        if (isSatisfied()) {
            return Optional.of(Duration.ZERO);
        }
        
        Duration longestTime = Duration.ZERO;
        boolean hasEstimate = false;
        boolean allHaveEstimates = true;
        
        for (Condition condition : conditions) {
            Optional<Duration> estimate = condition.getEstimatedTimeWhenIsSatisfied();
            
            if (estimate.isPresent()) {
                hasEstimate = true;
                Duration currentEstimate = estimate.get();
                
                if (currentEstimate.compareTo(longestTime) > 0) {
                    longestTime = currentEstimate;
                }
            } else {
                // If any condition can't provide an estimate, we can't provide a reliable estimate
                // for the entire AND condition
                allHaveEstimates = false;
            }
        }
        
        // Only return an estimate if all conditions can provide estimates
        return (hasEstimate && allHaveEstimates) ? Optional.of(longestTime) : Optional.empty();
    }
}