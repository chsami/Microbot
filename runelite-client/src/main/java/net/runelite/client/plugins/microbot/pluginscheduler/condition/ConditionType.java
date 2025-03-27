package net.runelite.client.plugins.microbot.pluginscheduler.condition;

/**
 * Defines the types of conditions available for script execution.
 */
public enum ConditionType {
    TIME("time"),
    SKILL_LEVEL("skill_level"),
    SKILL_XP("skill_xp"),
    ITEM("item"),
    PROFIT("profit"),
    LOCATION("location"),
    LOGICAL("LOGICAL");
    
    
    private final String identifier;
    
    ConditionType(String identifier) {
        this.identifier = identifier;
    }
    
    public String getIdentifier() {
        return identifier;
    }
    
    /**
     * Finds a condition type by its identifier string.
     */
    public static ConditionType fromIdentifier(String identifier) {
        for (ConditionType type : values()) {
            if (type.identifier.equals(identifier)) {
                return type;
            }
        }
        return null;
    }
    
    @Override
    public String toString() {
        return identifier;
    }
}