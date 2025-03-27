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
    public boolean isMet() {
        return !condition.isMet();
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
        }
        
        sb.append("\n");
        
        // Add the nested condition with additional indent
        sb.append(condition.getStatusInfo(indent + 2, showProgress));
        
        return sb.toString();
    }
}