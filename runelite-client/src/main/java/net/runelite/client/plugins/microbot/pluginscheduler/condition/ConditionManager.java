package net.runelite.client.plugins.microbot.pluginscheduler.condition;

import lombok.Getter;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.api.events.StatChanged;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.ArrayList;
import java.util.List;

/**
 * Manages multiple conditions for script execution.
 * Supports AND/OR combinations of conditions and listens to relevant events
 * to update condition statuses.
 */
public class ConditionManager {
    @Getter
    private final List<Condition> conditions = new ArrayList<>();
    @Getter
    private LogicalCondition rootCondition;
    private final EventBus eventBus;
    
    public ConditionManager() {
        this.eventBus = Microbot.getEventBus();
        registerEvents();
    }
    public ConditionManager setRootCondition(LogicalCondition condition) {
        rootCondition = condition;
        for (Condition c : conditions) {
               rootCondition.addCondition(c);
        }
      return this;
   }
    private void registerEvents() {
        eventBus.register(this);
    }
    
    public void unregisterEvents() {
        eventBus.unregister(this);
    }
    
    @Subscribe
    public void onStatChanged(StatChanged event) {

        // Propagate event to all conditions
        for (Condition condition : conditions) {
            condition.onStatChanged(event);
        }
    }
    
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
         // Propagate event to all conditions
         for (Condition condition : conditions) {
            condition.onItemContainerChanged(event);
        }
    }
    
    public ConditionManager requireAll() {
        rootCondition = new AndCondition();
        return this;
    }
    
    public ConditionManager requireAny() {
        rootCondition = new OrCondition();
        return this;
    }
    
    public ConditionManager addCondition(Condition condition) {
        conditions.add(condition);
        
        // If no logical condition is set, default to AND
        if (rootCondition == null) {
            rootCondition = new AndCondition();
        }
        
        rootCondition.addCondition(condition);
        return this;
    }
   
    public boolean areConditionsMet() {
        if (conditions.isEmpty()) return true;
        
        return rootCondition != null && rootCondition.isMet();
    }
    
    public String getDescription() {
        if (conditions.isEmpty()) {
            return "No conditions";
        }        
        return rootCondition != null ? rootCondition.getDescription() : "Undefined conditions";
    }
    
   /**
     * Checks if the manager requires all conditions to be met (AND logic)
     */
    public boolean requiresAll() {
        return rootCondition instanceof AndCondition;
    }
}