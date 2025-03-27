package net.runelite.client.plugins.microbot.pluginscheduler.condition.logical;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;

/**
 * Base class for logical combinations of conditions.
 */
public abstract class LogicalCondition implements Condition {
    @Getter
    protected List<Condition> conditions = new ArrayList<>();
    
    public LogicalCondition addCondition(Condition condition) {
        conditions.add(condition);
        return this;
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.LOGICAL;
    }
    
    @Override
    public void onStatChanged(StatChanged event) {
        // Propagate to child conditions
        for (Condition condition : conditions) {
            condition.onStatChanged(event);
        }
    }
    
    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        // Propagate to child conditions
        for (Condition condition : conditions) {
            condition.onItemContainerChanged(event);
        }
    }
    
    /**
     * Checks if this logical condition contains the specified condition,
     * either directly or within any nested logical conditions.
     * 
     * @param targetCondition The condition to search for
     * @return true if the condition exists within this logical structure, false otherwise
     */
    public boolean contains(Condition targetCondition) {
        // Direct reference check within this logical condition's immediate children
        if (conditions.contains(targetCondition)) {
            return true;
        }
        
        // Recursively search in nested logical conditions
        for (Condition condition : conditions) {
            // If this is a logical condition, search within it
            if (condition instanceof LogicalCondition) {
                if (((LogicalCondition) condition).contains(targetCondition)) {
                    return true;
                }
            }
            // Special case for NotCondition which wraps a single condition
            else if (condition instanceof NotCondition) {
                if (((NotCondition) condition).getCondition() == targetCondition) {
                    return true;
                }
                
                // If the wrapped condition is itself a logical condition, search within it
                Condition wrappedCondition = ((NotCondition) condition).getCondition();
                if (wrappedCondition instanceof LogicalCondition) {
                    if (((LogicalCondition) wrappedCondition).contains(targetCondition)) {
                        return true;
                    }
                }
            }
        }
        
        // Not found
        return false;
    }
    
    @Override
    public double getProgressPercentage() {
        if (conditions.isEmpty()) {
            return isMet() ? 100.0 : 0.0;
        }
        
        // For AND conditions, use the minimum progress (weakest link)
        if (this instanceof AndCondition) {
            return conditions.stream()
                .mapToDouble(Condition::getProgressPercentage)
                .min()
                .orElse(0.0);
        }
        // For OR conditions, use the maximum progress (strongest link)
        else if (this instanceof OrCondition) {
            return conditions.stream()
                .mapToDouble(Condition::getProgressPercentage)
                .max()
                .orElse(0.0);
        }
        
        // Default fallback to average progress
        return conditions.stream()
            .mapToDouble(Condition::getProgressPercentage)
            .average()
            .orElse(0.0);
    }
    
    @Override
    public int getTotalConditionCount() {
        if (conditions.isEmpty()) {
            return 0;
        }
        
        // Sum up all nested condition counts
        return conditions.stream()
            .mapToInt(Condition::getTotalConditionCount)
            .sum();
    }
    
    @Override
    public int getMetConditionCount() {
        if (conditions.isEmpty()) {
            return 0;
        }
        
        // Sum up all nested met condition counts
        return conditions.stream()
            .mapToInt(Condition::getMetConditionCount)
            .sum();
    }
    
    @Override
    public String getStatusInfo(int indent, boolean showProgress) {
        StringBuilder sb = new StringBuilder();
        
        // Add the logical condition info
        String indentation = " ".repeat(indent);
        boolean isMet = isMet();
        
        sb.append(indentation)
          .append(getDescription())
          .append(" [")
          .append(isMet ? "MET" : "NOT MET")
          .append("]");
        
        if (showProgress) {
            double progress = getProgressPercentage();
            if (progress > 0 && progress < 100) {
                sb.append(" (").append(String.format("%.1f%%", progress)).append(")");
            }
            
            // Add count of met conditions
            int total = getTotalConditionCount();
            int met = getMetConditionCount();
            if (total > 0) {
                sb.append(" - ").append(met).append("/").append(total).append(" conditions met");
            }
        }
        
        sb.append("\n");
        
        // Add nested conditions with additional indent
        for (Condition condition : conditions) {
            sb.append(condition.getStatusInfo(indent + 2, showProgress)).append("\n");
        }
        
        return sb.toString();
    }

    /**
     * Removes a condition from this logical condition or its nested structure.
     * Returns true if the condition was found and removed.
     */
    public boolean removeCondition(Condition targetCondition) {
        // Direct removal from this logical condition's immediate children
        if (conditions.remove(targetCondition)) {
            return true;
        }
        
        // Search nested logical conditions
        for (int i = 0; i < conditions.size(); i++) {
            Condition condition = conditions.get(i);
            
            // Handle nested logical conditions
            if (condition instanceof LogicalCondition) {
                LogicalCondition nestedLogical = (LogicalCondition) condition;
                if (nestedLogical.removeCondition(targetCondition)) {
                    // If this left the nested logical empty, remove it too
                    if (nestedLogical.getConditions().isEmpty()) {
                        conditions.remove(i);
                    }
                    return true;
                }
            }
            // Handle NOT condition as a special case
            else if (condition instanceof NotCondition) {
                NotCondition notCondition = (NotCondition) condition;
                
                // If NOT directly wraps our target condition
                if (notCondition.getCondition() == targetCondition) {
                    conditions.remove(i);
                    return true;
                }
                
                // If NOT wraps a logical condition, try removing from there
                if (notCondition.getCondition() instanceof LogicalCondition) {
                    LogicalCondition nestedLogical = (LogicalCondition) notCondition.getCondition();
                    if (nestedLogical.removeCondition(targetCondition)) {
                        // If this left the nested logical empty, remove the NOT condition too
                        if (nestedLogical.getConditions().isEmpty()) {
                            conditions.remove(i);
                        }
                        return true;
                    }
                }
            }
        }
        
        return false;
    }

    /**
     * Finds a logical condition that contains the given condition.
     * Useful for determining which logical group a condition belongs to.
     * 
     * @param targetCondition The condition to find
     * @return The logical condition containing the target, or null if not found
     */
    public LogicalCondition findContainingLogical(Condition targetCondition) {
        // Check if it's directly in this logical's conditions
        if (conditions.contains(targetCondition)) {
            return this;
        }
        
        // Search in nested logical conditions
        for (Condition condition : conditions) {
            if (condition instanceof LogicalCondition) {
                LogicalCondition result = ((LogicalCondition) condition).findContainingLogical(targetCondition);
                if (result != null) {
                    return result;
                }
            } else if (condition instanceof NotCondition) {
                NotCondition notCondition = (NotCondition) condition;
                
                // If NOT directly wraps our target
                if (notCondition.getCondition() == targetCondition) {
                    return this;
                }
                
                // If NOT wraps a logical, search in there
                if (notCondition.getCondition() instanceof LogicalCondition) {
                    LogicalCondition result = 
                        ((LogicalCondition) notCondition.getCondition()).findContainingLogical(targetCondition);
                    if (result != null) {
                        return result;
                    }
                }
            }
        }
        
        return null;
    }

    /**
     * Adds a condition to a specific position in the condition list.
     * Useful for preserving ordering when reconstructing the tree.
     */
    public LogicalCondition addConditionAt(int index, Condition condition) {
        if (index >= 0 && index <= conditions.size()) {
            conditions.add(index, condition);
        } else {
            conditions.add(condition);
        }
        return this;
    }
}


