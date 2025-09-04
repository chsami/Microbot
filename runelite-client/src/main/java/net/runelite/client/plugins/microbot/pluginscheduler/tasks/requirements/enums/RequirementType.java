package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums;

/**
 * Defines where a requirement needs to be located or what state it needs to be in
 * for optimal gameplay.
 */
public enum RequirementType {
    /**
     * Requirement must be equipped in an equipment slot
     */
    EQUIPMENT,
    
    /**
     * Requirement must be in inventory but not necessarily equipped
     */
    INVENTORY,
    
    /**
     * Requirement can be either equipped or in inventory
     */
    EITHER,
    
    /**
     * Requirement is related to player state (skills, quests, etc.)
     */
    PLAYER_STATE,
    
    /**
     * Requirement is related to game configuration (spellbook, etc.)
     */
    GAME_CONFIG,
    
    /**
     * Requirement is related to player location (must be at specific world point)
     */
    LOCATION,
    
    /**
     * Logical OR requirement - at least one child requirement must be fulfilled
     */
    OR_LOGICAL,
    
    /**
     * Conditional requirement - executes requirements in sequence based on conditions
     * This provides much more powerful workflow control than simple AND/OR logic
     */
    CONDITIONAL,
    
    /**
     * Shop requirement - buying or selling items from shops
     */
    SHOP,
    
    /**
     * Loot requirement - looting specific items from the ground or activities
     */
    LOOT,
    
    /**
     * Custom requirement - externally added requirements that should be fulfilled last
     * These are added by plugins through the custom requirement registration system
     */
    CUSTOM
}
