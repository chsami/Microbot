package net.runelite.client.plugins.microbot.util.cache.serialization;

/**
 * Interface for cache values that can be serialized to RuneLite profile config.
 * Implementing this interface indicates that the cache supports persistence across sessions.
 */
public interface CacheSerializable {
    
    /**
     * Gets the config key for this cache type.
     * This should be unique for each cache type.
     * 
     * @return The config key for storing this cache data
     */
    String getConfigKey();
    
    /**
     * Gets the config group for this cache type.
     * Typically "microbot" for all microbot caches.
     * 
     * @return The config group for storing this cache data
     */
    default String getConfigGroup() {
        return "microbot";
    }
    
    /**
     * Determines if this cache should be persisted.
     * Some cache types like NPCs, Objects, and Ground Items should not be persisted
     * as they are dynamically loaded and change frequently.
     * 
     * @return true if this cache should be saved/loaded from config
     */
    boolean shouldPersist();
}
