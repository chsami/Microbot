package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums;

/**
 * Represents the priority level of a requirement.
 * Used to determine how essential a requirement is for optimal plugin performance.
 */
public enum Priority {
    /**
     * Essential requirements that are absolutely required for the plugin to function.
     * Plugin should not start or should warn user if these requirements are unavailable.
     */
    MANDATORY,
    
    /**
     * Important requirements that significantly improve plugin performance or efficiency.
     * Plugin can function without these but with reduced effectiveness.
     */
    RECOMMENDED,
    
    /**
     * Nice-to-have requirements that provide minor benefits or convenience.
     * Plugin works normally without these requirements.
     */
    OPTIONAL
}
