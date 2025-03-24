package net.runelite.client.plugins.microbot.pluginscheduler.condition;

import lombok.Getter;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;

/**
 * Logical NOT operator - inverts a condition.
 */
public class NotCondition implements Condition {
    @Getter
    private final Condition condition;
    
    public NotCondition(Condition condition) {
        this.condition = condition;
    }
    
    @Override
    public boolean isMet() {
        return !condition.isMet();
    }
    
    @Override
    public String getDescription() {
        return "NOT (" + condition.getDescription() + ")";
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.COMBINED;
    }
    
    @Override
    public void onStatChanged(StatChanged event) {
        condition.onStatChanged(event);
    }
    
    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        condition.onItemContainerChanged(event);
    }
}