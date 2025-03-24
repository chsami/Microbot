package net.runelite.client.plugins.microbot.pluginscheduler.condition;

import java.util.ArrayList;
import java.util.List;

import lombok.Getter;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;

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
        return ConditionType.COMBINED;
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
}


