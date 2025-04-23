package net.runelite.client.plugins.microbot.pluginscheduler.condition.logical;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.DayOfWeekCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.IntervalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.SingleTriggerTimeCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeWindowCondition;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Optional;

/**
 * Logical NOT operator - inverts a condition.
 */
@EqualsAndHashCode(callSuper = false)
public class NotCondition implements Condition {
    @Getter
    private final Condition condition;
    
    public NotCondition(Condition condition) {
        this.condition = condition;
    }
    
    @Override
    public boolean isSatisfied() {
        if (condition instanceof SingleTriggerTimeCondition) {
            if (((SingleTriggerTimeCondition) condition).canTriggerAgain()) {
                return !condition.isSatisfied();
            }
            // should we only return true if the condition if it can trigger and is not satisfied? as we do it now
            return false;
        }
        return !condition.isSatisfied();
    }
    
    @Override
    public String getDescription() {
        return "NOT (" + condition.getDescription() + ")";
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.LOGICAL;
    }
    
    @Override
    public void onStatChanged(StatChanged event) {
        condition.onStatChanged(event);
    }
    
    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        condition.onItemContainerChanged(event);
    }
    
    @Override
    public void reset() {
        condition.reset();
    }
    @Override
    public void reset(boolean randomize) {        
        condition.reset(randomize);        
    }
    
    @Override
    public double getProgressPercentage() {
        // Invert the progress for NOT conditions
        double innerProgress = condition.getProgressPercentage();
        return 100.0 - innerProgress;
    }
    
    @Override
    public String getStatusInfo(int indent, boolean showProgress) {
        StringBuilder sb = new StringBuilder();
        
        // Add the NOT condition info
        String indentation = " ".repeat(indent);
        boolean isSatisfied = isSatisfied();
        
        sb.append(indentation)
          .append(getDescription())
          .append(" [")
          .append(isSatisfied ? "SATISFIED" : "NOT SATISFIED")
          .append("]");
        
        if (showProgress) {
            double progress = getProgressPercentage();
            if (progress > 0 && progress < 100) {
                sb.append(" (").append(String.format("%.1f%%", progress)).append(")");
            }
        }
        
        sb.append("\n");
        
        // Add the nested condition with additional indent
        sb.append(condition.getStatusInfo(indent + 2, showProgress));
        
        return sb.toString();
    }

    /**
     * Gets the next time this NOT condition will be satisfied.
     * For TimeConditions, this attempts to determine when the inner condition will change state.
     * 
     * @return Optional containing the next trigger time, or empty if none available
     */
    @Override
    public Optional<ZonedDateTime> getCurrentTriggerTime() {
        // For NOT condition with a TimeCondition, we need to consider state transitions
        if (condition instanceof TimeCondition) {
            boolean innerSatisfied = condition.isSatisfied();
            
            if (innerSatisfied) {
                // Inner condition is satisfied (NOT is not satisfied)
                // For DayOfWeekCondition, get the next non-active day
                if (condition instanceof DayOfWeekCondition) {
                    DayOfWeekCondition dayCondition = (DayOfWeekCondition) condition;
                    return dayCondition.getNextNonActiveDay();
                }
                // For TimeWindowCondition specifically, we can try to get its end time
                else if (condition instanceof TimeWindowCondition) {
                    TimeWindowCondition timeWindow = (TimeWindowCondition) condition;
                    // If we have access to end time, we could return it
                    if (timeWindow.getCurrentEndDateTime() != null) {
                        return Optional.of(timeWindow.getCurrentEndDateTime()
                            .atZone(timeWindow.getZoneId()));
                    }
                }
                // For IntervalCondition, estimate when the interval would reset
                else if (condition instanceof IntervalCondition) {
                    IntervalCondition intervalCondition = (IntervalCondition) condition;
                    // Calculate when the next interval would start after the current one
                    // This is our best estimate of when the NOT condition would become satisfied again
                    ZonedDateTime now = ZonedDateTime.now(ZoneId.systemDefault());
                    return Optional.of(now.plus(intervalCondition.getInterval()));
                }
                
                // For other time conditions or if end time isn't available,
                // we can't easily determine when the condition will stop being satisfied
                return Optional.empty();
            } else {
                // Inner condition is not satisfied (NOT is satisfied)
                // The next notable time point is when the inner condition becomes satisfied
                // (which would make the NOT condition unsatisfied)
                Optional<ZonedDateTime> nextInnerTrigger = condition.getCurrentTriggerTime();
                
                // If the inner condition has a next trigger time, that's when NOT will become unsatisfied
                return nextInnerTrigger;
            }
        }
        
        // For non-TimeCondition, use the default behavior
        // If the NOT is satisfied, return time in the past
        if (isSatisfied()) {
            return Optional.of(ZonedDateTime.now(ZoneId.systemDefault()).minusSeconds(1));
        }
        
        // If the NOT is not satisfied, we can't determine when it will become satisfied
        return Optional.empty();
    }

    /**
     * Returns a detailed description of the NOT condition with additional status information
     */
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        
        // Basic description
        sb.append("NOT Logical Condition: Inverts the inner condition\n");
        
        // Status information
        boolean satisfied = isSatisfied();
        sb.append("Status: ").append(satisfied ? "Satisfied" : "Not satisfied").append("\n");
        
        // Progress information (inverted)
        double progress = getProgressPercentage();
        sb.append(String.format("Inverted Progress: %.1f%%\n", progress)).append("\n");
        
        // Inner condition information
        sb.append("Inner Condition:\n");
        sb.append("  Type: ").append(condition.getClass().getSimpleName()).append("\n");
        sb.append("  Description: ").append(condition.getDescription()).append("\n");
        sb.append("  Status: ").append(condition.isSatisfied() ? "SATISFIED" : "NOT SATISFIED").append("\n");
        
        // If the inner condition has a detailed description and it's not too complex
        if (!(condition instanceof LogicalCondition)) {
            sb.append("\nInner Condition Details:\n");
            
            // Use reflection to safely try to access getDetailedDescription if available
            try {
                java.lang.reflect.Method detailedDescMethod = 
                    condition.getClass().getMethod("getDetailedDescription");
                if (detailedDescMethod != null) {
                    String innerDetails = (String) detailedDescMethod.invoke(condition);
                    // Add indentation to inner details
                    innerDetails = "  " + innerDetails.replace("\n", "\n  ");
                    sb.append(innerDetails);
                }
            } catch (Exception e) {
                // If detailed description isn't available, just use the regular description
                sb.append("  ").append(condition.getDescription());
            }
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        // Basic information
        sb.append("NotCondition:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        sb.append("  │ Type: NOT (Inverts inner condition)\n");
        sb.append("  │ Inner Condition: ").append(condition.getClass().getSimpleName()).append("\n");
        
        // Status information
        sb.append("  ├─ Status ──────────────────────────────────\n");
        boolean satisfied = isSatisfied();
        sb.append("  │ Satisfied: ").append(satisfied).append("\n");
        sb.append("  │ Inner Satisfied: ").append(condition.isSatisfied()).append("\n");
        sb.append("  │ Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
        
        // Inner condition
        sb.append("  └─ Inner Condition ─────────────────────────\n");
        
        // Format the inner condition's toString with proper indentation
        String innerString = condition.toString();
        String[] lines = innerString.split("\n");
        
        // For simple conditions that might not have fancy toString
        if (lines.length <= 1) {
            sb.append("    ").append(condition.getDescription());
        } else {
            // Skip the first line if it's just the class name
            for (int i = (lines[0].contains("Condition:") ? 1 : 0); i < lines.length; i++) {
                // Indent each line
                sb.append("    ").append(lines[i]).append("\n");
            }
        }
        
        return sb.toString();
    }
}