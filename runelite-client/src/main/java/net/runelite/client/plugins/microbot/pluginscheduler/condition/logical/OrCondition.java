package net.runelite.client.plugins.microbot.pluginscheduler.condition.logical;

import java.time.ZonedDateTime;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeCondition;

/**
 * Logical OR combination of conditions - any can be met.
 */
@EqualsAndHashCode(callSuper = true)
public class OrCondition extends LogicalCondition {
    public OrCondition(Condition... conditions) {
        super(conditions);

    }
    @Override
    public boolean isSatisfied() {
        if (conditions.isEmpty()) return true;
        return conditions.stream().anyMatch(Condition::isSatisfied);
    }
    

    
    /**
     * Gets the next time this OR condition will be satisfied.
     * If any condition is satisfied, returns the trigger time of the first satisfied condition.
     * If no condition is satisfied, returns the earliest next trigger time among TimeConditions.
     * 
     * @return Optional containing the next trigger time, or empty if none available
     */
    @Override
    public Optional<ZonedDateTime> getCurrentTriggerTime() {
        if (conditions.isEmpty()) {
            return Optional.empty();
        }
      
        
        // If none satisfied, find earliest trigger time among TimeConditions
        ZonedDateTime earliestTimeSatisfied = null;
        ZonedDateTime earliestTimeUnSatisfied = null;
        int satisfiedCount = 0;
        for (Condition condition : conditions) {
            if (condition instanceof TimeCondition) {
                Optional<ZonedDateTime> nextTrigger = condition.getCurrentTriggerTime();
                if (condition.isSatisfied()) {
                    satisfiedCount++;
                    if (earliestTimeSatisfied == null || nextTrigger.get().isBefore(earliestTimeSatisfied)) {
                        earliestTimeSatisfied = nextTrigger.get();
                    }
                }else{
                    if (nextTrigger.isPresent()) {
                        ZonedDateTime triggerTime = nextTrigger.get();
                        if (earliestTimeUnSatisfied == null || triggerTime.isBefore(earliestTimeUnSatisfied)) {
                            earliestTimeUnSatisfied = triggerTime;
                        }
                    }
                }
            }
        }
        if (satisfiedCount > 0) {
            return earliestTimeSatisfied != null ? Optional.of(earliestTimeSatisfied) : Optional.empty();
        }else if (earliestTimeUnSatisfied != null) {
            return Optional.of(earliestTimeUnSatisfied);
        }else{
            return Optional.empty();
        }        
        
    }
    
    /**
     * Returns a detailed description of the OR condition with additional status information
     */
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        
        // Basic description
        sb.append("OR Logical Condition: Any condition can be satisfied\n");
        
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
        sb.append("OrCondition:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        sb.append("  │ Type: OR (Any condition can be satisfied)\n");
        sb.append("  │ Child Conditions: ").append(conditions.size()).append("\n");
        
        // Status information
        sb.append("  ├─ Status ──────────────────────────────────\n");
        boolean anySatisfied = isSatisfied();
        sb.append("  │ Satisfied: ").append(anySatisfied).append("\n");
        
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
}