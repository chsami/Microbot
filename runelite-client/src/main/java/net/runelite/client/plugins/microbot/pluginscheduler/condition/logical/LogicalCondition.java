package net.runelite.client.plugins.microbot.pluginscheduler.condition.logical;

import java.util.ArrayList;
import java.util.List;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.*;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.enums.UpdateOption;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.time.TimeCondition;

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
    
    /**
     * Recursively finds all non-TimeCondition instances in this logical condition structure.
     * This searches through the entire hierarchy including nested logical conditions.
     * 
     * @return A list of all non-TimeCondition instances found in this structure
     */
    public List<Condition> findNonTimeConditions() {
        List<Condition> nonTimeConditions = new ArrayList<>();
        
        // Recursively search for non-TimeCondition instances
        for (Condition condition : conditions) {
            // Check if this condition is NOT a TimeCondition
            if (condition.getType() != ConditionType.TIME) {
                nonTimeConditions.add(condition);
            }
            
            // If this is a logical condition, search inside it recursively
            if (condition instanceof LogicalCondition) {
                nonTimeConditions.addAll(((LogicalCondition) condition).findNonTimeConditions());
                continue;
            }
            
            // Special case for NotCondition which wraps a single condition
            if (condition instanceof NotCondition) {
                Condition wrappedCondition = ((NotCondition) condition).getCondition();
                
                // Check if the wrapped condition is NOT a TimeCondition
                if (wrappedCondition.getType() != ConditionType.TIME) {
                    nonTimeConditions.add(wrappedCondition);
                    continue;
                }
                
                // If the wrapped condition is a logical condition, search inside it
                if (wrappedCondition instanceof LogicalCondition) {
                    nonTimeConditions.addAll(((LogicalCondition) wrappedCondition).findNonTimeConditions());
                }
            }
        }
        
        return nonTimeConditions;
    }
    
    /**
     * Checks if this logical condition structure contains only TimeCondition instances.
     * 
     * @return true if all conditions in this structure are TimeConditions, false otherwise
     */
    public boolean hasOnlyTimeConditions() {
        return findNonTimeConditions().isEmpty();
    }

    /**
     * Creates a new logical condition of the same type (AND/OR) that contains only
     * TimeCondition instances from this logical structure. This preserves the nested structure
     * of logical conditions rather than flattening.
     * 
     * @return A new logical condition containing only TimeConditions with the same logical structure,
     *         or null if no time conditions exist in this structure
     */
    public LogicalCondition createTimeOnlyLogicalStructure() {
        // If there are no conditions at all, return null
        if (conditions.isEmpty()) {
            return null;
        }
        
        // Create a new logical condition of the same type
        LogicalCondition newLogical;
        if (this instanceof AndCondition) {
            newLogical = new AndCondition();
        } else if (this instanceof OrCondition) {
            newLogical = new OrCondition();
        } else {
            // For other logical types (like NOT), default to AND logic
            newLogical = new AndCondition();
        }
        
        boolean hasAnyTimeConditions = false;
        
        // Process each condition, preserving the structure
        for (Condition condition : conditions) {
            if (condition.getType() == ConditionType.TIME) {
                // Directly add time conditions
                newLogical.addCondition(condition);
                hasAnyTimeConditions = true;
            } else if (condition instanceof LogicalCondition) {
                // Recursively process nested logical conditions
                LogicalCondition nestedTimeOnly = ((LogicalCondition) condition).createTimeOnlyLogicalStructure();
                if (nestedTimeOnly != null && !nestedTimeOnly.getConditions().isEmpty()) {
                    newLogical.addCondition(nestedTimeOnly);
                    hasAnyTimeConditions = true;
                }
            } else if (condition instanceof NotCondition) {
                // Special handling for NOT conditions
                Condition wrappedCondition = ((NotCondition) condition).getCondition();
                
                // If the wrapped condition is a time condition, wrap it in a new NOT
                if (wrappedCondition.getType() == ConditionType.TIME) {
                    newLogical.addCondition(new NotCondition(wrappedCondition));
                    hasAnyTimeConditions = true;
                } 
                // If the wrapped condition is a logical condition, process it recursively
                else if (wrappedCondition instanceof LogicalCondition) {
                    LogicalCondition nestedTimeOnly = ((LogicalCondition) wrappedCondition).createTimeOnlyLogicalStructure();
                    if (nestedTimeOnly != null && !nestedTimeOnly.getConditions().isEmpty()) {
                        newLogical.addCondition(new NotCondition(nestedTimeOnly));
                        hasAnyTimeConditions = true;
                    }
                }
            }
        }
        
        // If no time conditions were found, return null
        if (!hasAnyTimeConditions) {
            return null;
        }
        
        // Post-processing: if we have a logical with only one nested condition,
        // and that nested condition is a logical of the same type, we can flatten it
        if (newLogical.getConditions().size() == 1) {
            Condition singleCondition = newLogical.getConditions().get(0);
            if (singleCondition instanceof LogicalCondition && 
                ((singleCondition instanceof AndCondition && newLogical instanceof AndCondition) ||
                 (singleCondition instanceof OrCondition && newLogical instanceof OrCondition))) {
                return (LogicalCondition) singleCondition;
            }
        }
        
        return newLogical;
    }

    /**
     * Evaluates whether this logical structure would be satisfied based solely on its
     * time conditions. This creates a time-only logical structure with the same type (AND/OR)
     * and checks if it is satisfied.
     * 
     * @return true if the time-only structure is satisfied, false if not satisfied or no time conditions exist
     */
    public boolean isTimeOnlyStructureSatisfied() {
        LogicalCondition timeOnlyLogical = createTimeOnlyLogicalStructure();
        if (timeOnlyLogical == null) {
            return false;  // No time conditions, so can't be satisfied
        }
        
        return timeOnlyLogical.isSatisfied();
    }
    
    /**
     * Evaluates whether this logical structure would be satisfied if all non-time conditions
     * were removed and only time conditions were evaluated. This helps determine if a plugin
     * schedule would run based solely on its time conditions.
     * 
     * @return true if time conditions alone would satisfy this structure, false otherwise
     */
    public boolean wouldBeTimeOnlySatisfied() {
        // If there are no time conditions at all, we can't satisfy this structure with time only
        List<Condition> timeConditions = findTimeConditions();
        if (timeConditions.isEmpty()) {
            return false;
        }
        
        // For AND logic, if all time conditions are satisfied, the overall structure would
        // be satisfied if non-time conditions were not considered
        if (this instanceof AndCondition) {
            for (Condition condition : timeConditions) {
                if (!condition.isSatisfied()) {
                    return false;
                }
            }
            return true;
        }
        // For OR logic, if any time condition is satisfied, the overall structure would
        // be satisfied if non-time conditions were not considered
        else if (this instanceof OrCondition) {
            for (Condition condition : timeConditions) {
                if (condition.isSatisfied()) {
                    return true;
                }
            }
            return false;
        }
        
        // For other logic types, create a new structure and evaluate it
        return isTimeOnlyStructureSatisfied();
    }
    
    
    /**
     * Updates this logical condition structure with new conditions from another logical condition.
     * This is a convenience method that uses the default update mode (ADD_ONLY).
     * 
     * @param newLogicalCondition The logical condition containing new conditions to add
     * @return true if any changes were made, false if no changes were needed
     */
    public boolean updateLogicalStructure(LogicalCondition newLogicalCondition) {
        return updateLogicalStructure(newLogicalCondition, UpdateOption.SYNC, true);
    }
    
    /**
     * Updates this logical condition structure with new conditions from another logical condition.
     * Provides fine-grained control over how conditions are merged.
     * 
     * @param newLogicalCondition The logical condition containing new conditions to add
     * @param updateMode Controls how conditions are merged (add only, sync, remove only, replace)
     * @param preserveState If true, existing condition state is preserved when possible
     * @return true if any changes were made, false if no changes were needed
     */
    public boolean updateLogicalStructure(LogicalCondition newLogicalCondition, UpdateOption updateMode, boolean preserveState) {
        if (newLogicalCondition == null) {
            return false;
        }
        
        if (updateMode == UpdateOption.REPLACE) {
            // For REPLACE mode, just copy all conditions from the new structure
            boolean anyChanges = false;
            this.conditions.clear();
            
            for (Condition newCondition : newLogicalCondition.getConditions()) {
                this.addCondition(newCondition);
                anyChanges = true;
            }
            
            return anyChanges;
        }
        
        boolean anyChanges = false;
        
        // First, handle removals if needed
        if (updateMode == UpdateOption.SYNC || updateMode == UpdateOption.REMOVE_ONLY) {
            anyChanges = removeNonMatchingConditions(newLogicalCondition) || anyChanges;
        }
        
        // Then handle additions if needed
        if (updateMode == UpdateOption.ADD_ONLY || updateMode == UpdateOption.SYNC) {
            anyChanges = addNewConditions(newLogicalCondition, preserveState) || anyChanges;
        }
        
        return anyChanges;
    }
    
    /**
     * Removes conditions from this logical structure that don't exist in the new structure.
     * This creates a synchronized view between the two condition trees.
     * 
     * @param newLogicalCondition The logical condition to compare against
     * @return true if any conditions were removed, false otherwise
     */
    private boolean removeNonMatchingConditions(LogicalCondition newLogicalCondition) {
        boolean anyRemoved = false;
        List<Condition> toRemove = new ArrayList<>();
        
        for (Condition existingCondition : conditions) {
            // Check if the existing condition is found in the new structure
            boolean foundMatch = false;
            
            // For non-logical conditions, check direct existence
            if (!(existingCondition instanceof LogicalCondition) && !(existingCondition instanceof NotCondition)) {
                foundMatch = newLogicalCondition.contains(existingCondition);
            }
            // For logical conditions, check by type and recursively
            else if (existingCondition instanceof LogicalCondition) {
                LogicalCondition existingLogical = (LogicalCondition) existingCondition;
                
                // Try to find a matching logical condition in the new structure
                for (Condition newCondition : newLogicalCondition.getConditions()) {
                    if (newCondition instanceof LogicalCondition &&
                        ((existingLogical instanceof AndCondition && newCondition instanceof AndCondition) ||
                         (existingLogical instanceof OrCondition && newCondition instanceof OrCondition))) {
                        
                        // Found a logical condition of the same type, process it recursively
                        LogicalCondition newLogical = (LogicalCondition) newCondition;
                        existingLogical.removeNonMatchingConditions(newLogical);
                        foundMatch = true;
                        break;
                    }
                }
            }
            // For not conditions, check if the wrapped condition exists
            else if (existingCondition instanceof NotCondition) {
                NotCondition existingNot = (NotCondition) existingCondition;
                Condition wrappedExistingCondition = existingNot.getCondition();
                
                // Check for matching NOT conditions
                for (Condition newCondition : newLogicalCondition.getConditions()) {
                    if (newCondition instanceof NotCondition) {
                        NotCondition newNot = (NotCondition) newCondition;
                        if (newNot.getCondition().equals(wrappedExistingCondition)) {
                            foundMatch = true;
                            
                            // If the wrapped conditions are logical, recursively process them
                            if (wrappedExistingCondition instanceof LogicalCondition && 
                                newNot.getCondition() instanceof LogicalCondition) {
                                ((LogicalCondition) wrappedExistingCondition).removeNonMatchingConditions(
                                    (LogicalCondition) newNot.getCondition());
                            }
                            break;
                        }
                    }
                }
            }
            
            // If no match was found, mark for removal
            if (!foundMatch) {
                toRemove.add(existingCondition);
            }
        }
        
        // Remove all conditions that weren't found in the new structure
        for (Condition conditionToRemove : toRemove) {
            conditions.remove(conditionToRemove);
            anyRemoved = true;
            log.debug("Removed condition from logical structure: {}", conditionToRemove.getDescription());
        }
        
        return anyRemoved;
    }

    /**
     * Identifies and removes conditions that can no longer trigger from a logical condition structure.
     * This is useful for cleaning up one-time conditions that have already triggered and cannot trigger again.
     * 
     * @param logicalCondition The logical condition structure to clean up
     * @return true if any conditions were removed, false otherwise
     */
    public static boolean removeNonTriggerableConditions(LogicalCondition logicalCondition) {
        if (logicalCondition == null || logicalCondition.getConditions().isEmpty()) {
            return false;
        }
        
        boolean anyRemoved = false;
        List<Condition> conditionsToRemove = new ArrayList<>();
        
        // First pass: identify conditions that can no longer trigger
        for (Condition condition : logicalCondition.getConditions()) {
            // Check direct non-triggerable conditions
            if (condition instanceof TimeCondition && !((TimeCondition) condition).canTriggerAgain()) {
                log.debug("Found non-triggerable time condition: {}", condition.getDescription());
                conditionsToRemove.add(condition);
                continue;
            } 
            
            // Handle nested logical conditions
            if (condition instanceof LogicalCondition) {
                // Recursively clean up nested structure
                if (removeNonTriggerableConditions((LogicalCondition) condition)) {
                    anyRemoved = true;
                }
                
                // If this leaves the nested logical empty, mark it for removal too
                if (((LogicalCondition) condition).getConditions().isEmpty()) {
                    conditionsToRemove.add(condition);
                }
            }
            
            // Handle NOT condition as a special case
            if (condition instanceof NotCondition) {
                NotCondition notCondition = (NotCondition) condition;
                Condition wrappedCondition = notCondition.getCondition();
                
                // If wrapped condition is a time condition that can't trigger
                if (wrappedCondition instanceof TimeCondition && 
                    !((TimeCondition) wrappedCondition).canTriggerAgain()) {
                    conditionsToRemove.add(condition);
                }
                // If wrapped condition is a logical, clean it up recursively
                else if (wrappedCondition instanceof LogicalCondition) {
                    if (removeNonTriggerableConditions((LogicalCondition) wrappedCondition)) {
                        anyRemoved = true;
                        // If cleaned condition is now empty, mark the NOT for removal
                        if (((LogicalCondition) wrappedCondition).getConditions().isEmpty()) {
                            conditionsToRemove.add(condition);
                        }
                    }
                }
            }
        }
        
        // Second pass: remove identified conditions
        for (Condition conditionToRemove : conditionsToRemove) {
            logicalCondition.removeCondition(conditionToRemove);
            log.debug("Removed non-triggerable condition: {}", conditionToRemove.getDescription());
            anyRemoved = true;
        }
        
        return anyRemoved;
    }
    
    /**
     * Adds new conditions from the provided structure that don't already exist in this structure.
     * This preserves the existing condition state while adding new conditions.
     * 
     * @param newLogicalCondition The logical condition containing new conditions to add
     * @param preserveState If true, existing conditions with the same description are kept
     * @return true if any conditions were added, false otherwise
     */
    private boolean addNewConditions(LogicalCondition newLogicalCondition, boolean preserveState) {
        boolean anyChanges = false;
        
        for (Condition newCondition : newLogicalCondition.getConditions()) {
            // For non-logical conditions, check if we need to add them
            if (!(newCondition instanceof LogicalCondition) && !(newCondition instanceof NotCondition)) {
                if (!this.contains(newCondition)) {
                    this.addCondition(newCondition);
                    anyChanges = true;
                }
                continue;
            }
            
            // Handle NotCondition as a special case
            if (newCondition instanceof NotCondition) {
                NotCondition newNotCondition = (NotCondition) newCondition;
                Condition wrappedNewCondition = newNotCondition.getCondition();
                
                // Check if we already have this NOT condition
                boolean exists = false;
                for (Condition existingCondition : this.conditions) {
                    if (existingCondition instanceof NotCondition) {
                        NotCondition existingNotCondition = (NotCondition) existingCondition;
                        Condition wrappedExistingCondition = existingNotCondition.getCondition();
                        
                        // Check if the NOT conditions are wrapping the same condition
                        if (wrappedExistingCondition.equals(wrappedNewCondition)) {
                            exists = true;
                            break;
                        }
                        
                        // If both wrap logical conditions, we need to update recursively
                        if (wrappedExistingCondition instanceof LogicalCondition && 
                            wrappedNewCondition instanceof LogicalCondition) {
                            if (((LogicalCondition) wrappedExistingCondition).updateLogicalStructure(
                                    (LogicalCondition) wrappedNewCondition, 
                                    preserveState ? UpdateOption.ADD_ONLY : UpdateOption.SYNC, 
                                    preserveState)) {
                                anyChanges = true;
                            }
                            exists = true;
                            break;
                        }
                    }
                }
                
                // If we don't have this NOT condition, add it
                if (!exists) {
                    this.addCondition(newCondition);
                    anyChanges = true;
                }
                continue;
            }
            
            // For logical conditions, recursively update if we find a matching logical type
            LogicalCondition newLogical = (LogicalCondition) newCondition;
            boolean foundMatchingLogical = false;
            
            for (Condition existingCondition : this.conditions) {
                if (existingCondition instanceof LogicalCondition) {
                    LogicalCondition existingLogical = (LogicalCondition) existingCondition;
                    
                    // If they're the same type of logical condition (AND/OR), update recursively
                    if ((existingLogical instanceof AndCondition && newLogical instanceof AndCondition) ||
                        (existingLogical instanceof OrCondition && newLogical instanceof OrCondition)) {
                        if (existingLogical.updateLogicalStructure(
                                newLogical, 
                                preserveState ? UpdateOption.ADD_ONLY : UpdateOption.SYNC, 
                                preserveState)) {
                            anyChanges = true;
                        }
                        foundMatchingLogical = true;
                        break;
                    }
                }
            }
            
            // If we didn't find a matching logical type, add the entire logical condition
            if (!foundMatchingLogical) {
                this.addCondition(newLogical);
                anyChanges = true;
            }
        }
        
        return anyChanges;
    }
    
    /**
     * Validates the logical condition structure, checking for common issues
     * like empty logical conditions or invalid condition nesting.
     * 
     * @return A list of validation issues, or an empty list if no issues were found
     */
    public List<String> validateStructure() {
        List<String> issues = new ArrayList<>();
        
        // Check for empty logical conditions
        if (conditions.isEmpty()) {
            issues.add("Empty logical condition: " + getClass().getSimpleName());
        }
        
        // Check for nested logical conditions of same type that could be flattened
        for (Condition condition : conditions) {
            if (condition instanceof LogicalCondition) {
                LogicalCondition nestedLogical = (LogicalCondition) condition;
                
                // Recursively validate nested structures
                issues.addAll(nestedLogical.validateStructure());
                
                // Check for unnecessary nesting (same logical type)
                if ((this instanceof AndCondition && nestedLogical instanceof AndCondition) ||
                    (this instanceof OrCondition && nestedLogical instanceof OrCondition)) {
                    issues.add("Unnecessary nesting of " + getClass().getSimpleName() + 
                              " contains nested " + nestedLogical.getClass().getSimpleName() + 
                              " that could be flattened");
                }
                
                // Check for empty nested logical conditions
                if (nestedLogical.getConditions().isEmpty()) {
                    issues.add("Empty nested logical condition: " + nestedLogical.getClass().getSimpleName());
                }
            }
        }
        
        return issues;
    }
    
    /**
     * Optimizes the logical condition structure by flattening unnecessary nesting
     * and removing empty logical conditions.
     * 
     * @return true if any optimizations were applied, false otherwise
     */
    public boolean optimizeStructure() {
        boolean anyChanges = false;
        
        // Remove empty nested logical conditions
        for (int i = conditions.size() - 1; i >= 0; i--) {
            Condition condition = conditions.get(i);
            if (condition instanceof LogicalCondition) {
                LogicalCondition nestedLogical = (LogicalCondition) condition;
                
                // Recursively optimize nested structure
                if (nestedLogical.optimizeStructure()) {
                    anyChanges = true;
                }
                
                // Remove if empty after optimization
                if (nestedLogical.getConditions().isEmpty()) {
                    conditions.remove(i);
                    anyChanges = true;
                    continue;
                }
                
                // Flatten nested logical conditions of same type
                if ((this instanceof AndCondition && nestedLogical instanceof AndCondition) ||
                    (this instanceof OrCondition && nestedLogical instanceof OrCondition)) {
                    
                    // Get all conditions from nested logical before we remove it
                    List<Condition> nestedConditions = new ArrayList<>(nestedLogical.getConditions());
                    
                    // Move all conditions from nested logical to this logical
                    // Need to iterate through a copy to avoid concurrent modification
                    for (Condition nestedCondition : nestedConditions) {
                        // Remove from nested logical first to avoid duplicates when we add to parent
                        nestedLogical.getConditions().remove(nestedCondition);
                        
                        // Add to parent logical if not already present
                        if (!this.contains(nestedCondition)) {
                            this.addCondition(nestedCondition);
                        }
                    }
                    
                    // Remove the now empty nested logical
                    conditions.remove(i);
                    anyChanges = true;
                }
            }
        }
        
        return anyChanges;
    }
    
    /**
     * Updates this logical condition structure with new conditions from another logical condition.
     * Only adds conditions that don't already exist in the structure.
     * 
     * @param newLogicalCondition The logical condition containing new conditions to add
     * @return true if any conditions were added, false if no changes were made
     */
    public boolean updateLogicalStructureOld(LogicalCondition newLogicalCondition) {
        if (newLogicalCondition == null || newLogicalCondition.getConditions().isEmpty()) {
            return false;
        }
        
        boolean anyChanges = false;
        
        for (Condition newCondition : newLogicalCondition.getConditions()) {
            // For non-logical conditions, check if we need to add them
            if (!(newCondition instanceof LogicalCondition) && !(newCondition instanceof NotCondition)) {
                if (!this.contains(newCondition)) {
                    this.addCondition(newCondition);
                    anyChanges = true;
                }
                continue;
            }
            
            // Handle NotCondition as a special case
            if (newCondition instanceof NotCondition) {
                NotCondition newNotCondition = (NotCondition) newCondition;
                Condition wrappedNewCondition = newNotCondition.getCondition();
                
                // Check if we already have this NOT condition
                boolean exists = false;
                for (Condition existingCondition : this.conditions) {
                    if (existingCondition instanceof NotCondition) {
                        NotCondition existingNotCondition = (NotCondition) existingCondition;
                        Condition wrappedExistingCondition = existingNotCondition.getCondition();
                        
                        // Check if the NOT conditions are wrapping the same condition
                        if (wrappedExistingCondition.equals(wrappedNewCondition)) {
                            exists = true;
                            break;
                        }
                        
                        // If both wrap logical conditions, we need to update recursively
                        if (wrappedExistingCondition instanceof LogicalCondition && 
                            wrappedNewCondition instanceof LogicalCondition) {
                            if (((LogicalCondition) wrappedExistingCondition).updateLogicalStructure(
                                    (LogicalCondition) wrappedNewCondition)) {
                                anyChanges = true;
                            }
                            exists = true;
                            break;
                        }
                    }
                }
                
                // If we don't have this NOT condition, add it
                if (!exists) {
                    this.addCondition(newCondition);
                    anyChanges = true;
                }
                continue;
            }
            
            // For logical conditions, recursively update if we find a matching logical type
            LogicalCondition newLogical = (LogicalCondition) newCondition;
            boolean foundMatchingLogical = false;
            
            for (Condition existingCondition : this.conditions) {
                if (existingCondition instanceof LogicalCondition) {
                    LogicalCondition existingLogical = (LogicalCondition) existingCondition;
                    
                    // If they're the same type of logical condition (AND/OR), update recursively
                    if ((existingLogical instanceof AndCondition && newLogical instanceof AndCondition) ||
                        (existingLogical instanceof OrCondition && newLogical instanceof OrCondition)) {
                        if (existingLogical.updateLogicalStructure(newLogical)) {
                            anyChanges = true;
                        }
                        foundMatchingLogical = true;
                        break;
                    }
                }
            }
            
            // If we didn't find a matching logical type, add the entire logical condition
            if (!foundMatchingLogical) {
                this.addCondition(newLogical);
                anyChanges = true;
            }
        }
        
        return anyChanges;
    }
    
    /**
     * Compares this logical condition structure with another one and returns differences.
     * This is useful for debugging and logging what changed during an update.
     * 
     * @param otherLogical The logical condition to compare with
     * @return A string describing the differences, or "No differences" if they're the same
     */
    public String getStructureDifferences(LogicalCondition otherLogical) {
        if (otherLogical == null) {
            return "Other logical condition is null";
        }
        
        StringBuilder differences = new StringBuilder();
        
        // Check for differences in logical type
        if ((this instanceof AndCondition && !(otherLogical instanceof AndCondition)) ||
            (this instanceof OrCondition && !(otherLogical instanceof OrCondition))) {
            differences.append("Different logical types: ")
                      .append(this.getClass().getSimpleName())
                      .append(" vs ")
                      .append(otherLogical.getClass().getSimpleName())
                      .append("\n");
        }
        
        // Find conditions in this that aren't in otherLogical
        for (Condition thisCondition : this.conditions) {
            if (!otherLogical.contains(thisCondition)) {
                differences.append("Only in this: \n\t\t").append(thisCondition.getDescription()).append("\n");
            }
        }
        
        // Find conditions in otherLogical that aren't in this
        for (Condition otherCondition : otherLogical.conditions) {
            if (!this.contains(otherCondition)) {
                differences.append("Only in other: \n\t\t").append(otherCondition.getDescription()).append("\n");
            }
        }
        
        // Check for nested differences in logical conditions
        for (Condition thisCondition : this.conditions) {
            if (thisCondition instanceof LogicalCondition) {
                LogicalCondition thisLogical = (LogicalCondition) thisCondition;
                
                // Find the corresponding logical condition in otherLogical
                for (Condition otherCondition : otherLogical.conditions) {
                    if (otherCondition instanceof LogicalCondition &&
                        ((thisLogical instanceof AndCondition && otherCondition instanceof AndCondition) ||
                         (thisLogical instanceof OrCondition && otherCondition instanceof OrCondition))) {
                        
                        LogicalCondition otherLogical2 = (LogicalCondition) otherCondition;
                        String nestedDifferences = thisLogical.getStructureDifferences(otherLogical2);
                        
                        if (!"No differences".equals(nestedDifferences)) {
                            differences.append("Nested differences in ")
                                      .append(thisLogical.getClass().getSimpleName())
                                      .append(":\n")
                                      .append(nestedDifferences)
                                      .append("\n");
                        }
                        break;
                    }
                }
            }
        }
        
        return differences.length() > 0 ? differences.toString() : "No differences";
    }
}


