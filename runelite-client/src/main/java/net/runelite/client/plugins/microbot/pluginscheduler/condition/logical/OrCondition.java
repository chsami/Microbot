package net.runelite.client.plugins.microbot.pluginscheduler.condition.logical;

import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;

/**
 * Logical OR combination of conditions - any can be met.
 */
public class OrCondition extends LogicalCondition {
    @Override
    public boolean isMet() {
        if (conditions.isEmpty()) return true;
        return conditions.stream().anyMatch(Condition::isMet);
    }
    
    @Override
    public String getDescription() {
        if (conditions.isEmpty()) {
            return "No conditions";
        }
        
        StringBuilder sb = new StringBuilder("ANY of: (");
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) sb.append(" OR ");
            sb.append(conditions.get(i).getDescription());
        }
        sb.append(")");
        return sb.toString();
    }
    @Override
    public void reset() {
        for (Condition condition : conditions) {
            condition.reset();
        }
    }
}