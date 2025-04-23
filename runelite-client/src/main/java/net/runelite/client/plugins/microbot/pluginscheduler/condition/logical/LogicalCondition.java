package net.runelite.client.plugins.microbot.pluginscheduler.condition.logical;

import java.util.ArrayList;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.*;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;

/**
 * Base class for logical combinations of conditions.
 * 
 * IMPORTANT: When adding new event types to the Condition interface, you MUST also:
 * 1. Override the event method in this class
 * 2. Use the propagateEvent() helper to forward the event to all child conditions
 * 
 * This ensures proper event propagation through the condition hierarchy.
 */
@Slf4j
@EqualsAndHashCode(callSuper = false)
public abstract class LogicalCondition implements Condition {

    public LogicalCondition(Condition... conditions) {
        for (Condition condition : conditions) {
            addCondition(condition);
        }
    }
    @Getter
    protected List<Condition> conditions = new ArrayList<>();
    
    public LogicalCondition addCondition(Condition condition) {
        //check if the condition is already in the list, with .equals()
        // this prevents duplicates and unnecessary processing, 
        for (Condition conditionInList : conditions) {
            if (conditionInList.equals(condition)) {
                return this;
            }
        }
        conditions.add(condition);
        return this;
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.LOGICAL;
    }
     /**
     * Helper method to propagate any event to all child conditions.
     * This centralizes the propagation logic to avoid code duplication.
     * 
     * @param <T> The event type
     * @param event The event object to propagate
     * @param eventHandler The method reference to the appropriate event handler
     */
    protected <T> void propagateEvent(T event, PropagationHandler<T> eventHandler) {
        for (Condition condition : conditions) {
            try {
                eventHandler.handle(condition, event);
            } catch (Exception e) {
                // Optional: Add logging
                log.error("Error propagating event to condition: " + condition.getClass().getSimpleName(), e);
                //log stack trace if needed
                e.printStackTrace();
            }
        }
    }
    
    /**
     * Functional interface for event propagation handling
     */
    @FunctionalInterface
    protected interface PropagationHandler<T> {
        void handle(Condition condition, T event);
    }
    
    
    @Override
    public void onStatChanged(StatChanged event) {
        propagateEvent(event, (condition, e) -> condition.onStatChanged(e));
    }
    
    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        propagateEvent(event, (condition, e) -> condition.onItemContainerChanged(e));
    }
    
    @Override
    public void onGameTick(GameTick event) {
        propagateEvent(event, (condition, e) -> condition.onGameTick(e));
    }
    
    @Override
    public void onNpcChanged(NpcChanged event) {
        propagateEvent(event, (condition, e) -> condition.onNpcChanged(e));
    }
    
    @Override
    public void onNpcSpawned(NpcSpawned event) {
        propagateEvent(event, (condition, e) -> condition.onNpcSpawned(e));
    }
    
    @Override
    public void onNpcDespawned(NpcDespawned event) {
        propagateEvent(event, (condition, e) -> condition.onNpcDespawned(e));
    }
    
    @Override
    public void onGroundObjectSpawned(GroundObjectSpawned event) {
        propagateEvent(event, (condition, e) -> condition.onGroundObjectSpawned(e));
    }
    
    @Override
    public void onGroundObjectDespawned(GroundObjectDespawned event) {
        propagateEvent(event, (condition, e) -> condition.onGroundObjectDespawned(e));
    }
    
    @Override
    public void onItemSpawned(ItemSpawned event) {
        propagateEvent(event, (condition, e) -> condition.onItemSpawned(e));
    }
    
    @Override
    public void onItemDespawned(ItemDespawned event) {
        propagateEvent(event, (condition, e) -> condition.onItemDespawned(e));
    }
    
    @Override
    public void onMenuOptionClicked(MenuOptionClicked event) {
        propagateEvent(event, (condition, e) -> condition.onMenuOptionClicked(e));
    }
    
    @Override
    public void onChatMessage(ChatMessage event) {
        propagateEvent(event, (condition, e) -> condition.onChatMessage(e));
    }
    
    @Override
    public void onHitsplatApplied(HitsplatApplied event) {
        propagateEvent(event, (condition, e) -> condition.onHitsplatApplied(e));
    }
    
    @Override
    public void onVarbitChanged(VarbitChanged event) {
        propagateEvent(event, (condition, e) -> condition.onVarbitChanged(e));
    }
    
    @Override
    public void onInteractingChanged(InteractingChanged event) {
        propagateEvent(event, (condition, e) -> condition.onInteractingChanged(e));
    }
    
    @Override
    public void onAnimationChanged(AnimationChanged event) {
        propagateEvent(event, (condition, e) -> condition.onAnimationChanged(e));
    }
    @Override
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        propagateEvent(gameStateChanged, (condition, e) -> condition.onGameStateChanged(e));
    }
    

   
    /**
     * Checks if this logical condition contains the specified condition,
     * either directly or within any nested logical conditions.
     * 
     * @param targetCondition The condition to search for
     * @return true if the condition exists within this logical structure, false otherwise
     */
    public boolean contains(Condition targetCondition) {        
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
                if (((NotCondition) condition).getCondition().equals(targetCondition)) {
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
            if (condition.equals(targetCondition)) {
                //log.info("Found condition: {} equals\nthe condition {}\nin the logical{}",targetCondition.getDescription(),
                //condition.getDescription(), this.getDescription());
                return true;
            }
        }
        
        // Not found
        return false;
    }
    
    @Override
    public double getProgressPercentage() {
        if (conditions.isEmpty()) {
            return isSatisfied() ? 100.0 : 0.0;
        }
        
        // For AND conditions, use the average progress (average over links)
        if (this instanceof AndCondition) {
            return conditions.stream()
                .mapToDouble(Condition::getProgressPercentage)
                .average()
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
            
            // Add count of met conditions
            int total = getTotalConditionCount();
            int met = getMetConditionCount();
            if (total > 0) {
                sb.append(" - ").append(met).append("/").append(total).append(" conditions SATISFIED");
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
    
    public void softReset() { 
        if (isSatisfied()){
            for (Condition condition : conditions) {
                condition.reset();
            }
        }
    }
    
    public void softReset(boolean randomize) {
        if (isSatisfied()){
            for (Condition condition : conditions) {
                condition.reset(randomize);
            }
        }        
    }
    public void reset() { 
        
        for (Condition condition : conditions) {
            condition.reset();
        }
    
    }
    
    public void reset(boolean randomize) {
        
        for (Condition condition : conditions) {
            condition.reset(randomize);
        }
        
    }


    /**
     * Adds a condition to a specific position in the condition tree
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

    /**
     * Gets a human-readable description of this logical condition.
     * This provides a default implementation that subclasses can override.
     * 
     * @return A string description of the logical condition
     */
    @Override
    public String getDescription() {
        if (conditions.isEmpty()) {
            return "No conditions";
        }
        
        String conditionType = (this instanceof AndCondition) ? "ALL of" : 
                              ((this instanceof OrCondition) ? "ANY of" : "Logical group of");
        
        StringBuilder sb = new StringBuilder(conditionType).append(": (");
        String separator = (this instanceof AndCondition) ? " AND " : 
                          ((this instanceof OrCondition) ? " OR " : ", ");
        
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) sb.append(separator);
            sb.append(conditions.get(i).getDescription());
        }
        sb.append(")");
        return sb.toString();
    }
    
    /**
     * Gets a description formatted for HTML display in UI components.
     * This is useful for tooltips and other rich text displays.
     * 
     * @param maxLength Maximum length of descriptions before truncating
     * @return HTML formatted description
     */
    public String getHtmlDescription(int maxLength) {
        if (conditions.isEmpty()) {
            return "<html>No conditions</html>";
        }
        
        StringBuilder sb = new StringBuilder("<html>");
        
        // Add operator with appropriate styling
        if (this instanceof AndCondition) {
            sb.append("<b style='color:#e67e22'>ALL</b> of: (");
        } else if (this instanceof OrCondition) {
            sb.append("<b style='color:#3498db'>ANY</b> of: (");
        } else {
            sb.append("<b>Logical group</b> of: (");
        }
        
        // Add child conditions with appropriate separators
        String separator = (this instanceof AndCondition) ? " <b>AND</b> " : 
                          ((this instanceof OrCondition) ? " <b>OR</b> " : ", ");
        
        int totalLength = 0;
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) sb.append(separator);
            
            String description = conditions.get(i).getDescription();
            
            // Truncate long descriptions
            if (maxLength > 0 && totalLength + description.length() > maxLength) {
                description = description.substring(0, Math.max(10, maxLength - totalLength - 3)) + "...";
            }
            
            // Style based on satisfied state
            if (conditions.get(i).isSatisfied()) {
                sb.append("<span style='color:#2ecc71'>").append(description).append("</span>");
            } else {
                sb.append("<span style='color:#e74c3c'>").append(description).append("</span>");
            }
            
            totalLength += description.length();
        }
        
        sb.append(")</html>");
        return sb.toString();
    }
    
    /**
     * Gets a simple HTML representation for use in tooltips.
     * 
     * @return HTML formatted description
     */
    public String getTooltipHtml() {
        return getHtmlDescription(100);
    }

    /**
     * Recursively finds all TimeCondition instances in this logical condition structure.
     * This searches through the entire hierarchy including nested logical conditions.
     * 
     * @return A list of all TimeCondition instances found in this structure
     */
    public List<Condition> findTimeConditions() {
        List<Condition> timeConditions = new ArrayList<>();
        
        // Recursively search for TimeCondition instances
        for (Condition condition : conditions) {
            // Check if this condition is a TimeCondition
            if (condition.getType() == ConditionType.TIME) {
                timeConditions.add(condition);
                continue;
            }
            
            // If this is a logical condition, search inside it recursively
            if (condition instanceof LogicalCondition) {
                timeConditions.addAll(((LogicalCondition) condition).findTimeConditions());
                continue;
            }
            
            // Special case for NotCondition which wraps a single condition
            if (condition instanceof NotCondition) {
                Condition wrappedCondition = ((NotCondition) condition).getCondition();
                
                // Check if the wrapped condition is a TimeCondition
                if (wrappedCondition.getType() == ConditionType.TIME) {
                    timeConditions.add(wrappedCondition);
                    continue;
                }
                
                // If the wrapped condition is a logical condition, search inside it
                if (wrappedCondition instanceof LogicalCondition) {
                    timeConditions.addAll(((LogicalCondition) wrappedCondition).findTimeConditions());
                }
            }
        }
        
        return timeConditions;
    }
}


