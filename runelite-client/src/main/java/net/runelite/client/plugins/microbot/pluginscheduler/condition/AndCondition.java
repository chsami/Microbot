package net.runelite.client.plugins.microbot.pluginscheduler.type.condition;

/**
 * Logical AND combination of conditions - all must be met.
 */
public class AndCondition extends LogicalCondition {
    @Override
    public boolean isMet() {
        if (conditions.isEmpty()) return true;
        return conditions.stream().allMatch(Condition::isMet);
    }
    
    @Override
    public String getDescription() {
        if (conditions.isEmpty()) {
            return "No conditions";
        }
        
        StringBuilder sb = new StringBuilder("ALL of: (");
        for (int i = 0; i < conditions.size(); i++) {
            if (i > 0) sb.append(" AND ");
            sb.append(conditions.get(i).getDescription());
        }
        sb.append(")");
        return sb.toString();
    }
}