package net.runelite.client.plugins.microbot.pluginscheduler.condition.logical;

import lombok.Getter;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;

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
    public boolean isSatisfied() {
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
}