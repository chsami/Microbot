package net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.enums;

/**
 * Update options for condition managers.
 * Controls how plugin conditions are merged during updates.
 */
public enum UpdateOption {
    /**
     * Only add new conditions, preserve existing conditions
     */
    ADD_ONLY,
    
    /**
     * Synchronize conditions to match the new structure (add new and remove missing)
     */
    SYNC,
    
    /**
     * Only remove conditions that don't exist in the new structure
     */
    REMOVE_ONLY,
    
    /**
     * Replace the entire condition structure with the new one
     */
    REPLACE
}
    