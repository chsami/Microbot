package net.runelite.client.plugins.microbot.pluginscheduler.condition;

import net.runelite.api.NPC;

/**
 * Defines the types of conditions available for script execution.
 */
public enum ConditionType {
    TIME("TIME"),
    SKILL_LEVEL("SKILL_LEVEL"),
    SKILL_XP("SKILL_XP"),
    RESOURCE("RESOURCE"),    
    LOCATION("location"),
    LOGICAL("LOGICAL"),
    NPC("NPC");
    
    
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