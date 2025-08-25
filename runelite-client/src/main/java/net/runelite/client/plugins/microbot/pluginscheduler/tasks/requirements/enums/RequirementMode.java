package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums;

/**
 * Defines the source mode for requirement fulfillment.
 * This determines which cache (standard or external) to use for getting requirements.
 */
public enum RequirementMode {
    /**
     * Use only standard requirements from the main cache.
     * This is the default for normal pre/post schedule requirement fulfillment.
     */
    STANDARD,
    
    /**
     * Use only external requirements from the external cache.
     * This is used for externally added requirements that should not mix with standard ones.
     */
    EXTERNAL,
    
    /**
     * Use both standard and external requirements combined.
     * This is rarely used but available for special cases where both sources are needed.
     */
    BOTH
}
