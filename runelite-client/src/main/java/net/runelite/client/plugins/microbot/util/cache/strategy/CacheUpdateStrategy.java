package net.runelite.client.plugins.microbot.util.cache.strategy;

/**
 * Strategy interface for handling cache updates based on events.
 * Follows the game cache framework guidelines for pluggable cache enhancement.
 * 
 * These strategies handle event-driven cache updates, enriching cache data
 * with temporal information, contextual data, and change tracking rather
 * than just invalidating entries.
 * 
 * @param <K> Cache key type
 * @param <V> Cache value type
 */
public interface CacheUpdateStrategy<K, V> {
    
    /**
     * Handles an event and potentially updates cache entries with enhanced data.
     * 
     * @param event The event that occurred
     * @param cache The cache to potentially update
     */
    void handleEvent(Object event, CacheOperations<K, V> cache);
    
    /**
     * Gets the event types this strategy handles.
     * 
     * @return Array of event classes this strategy processes
     */
    Class<?>[] getHandledEventTypes();
    
    /**
     * Called when the strategy is attached to a cache.
     * 
     * @param cache The cache this strategy is attached to
     */
    default void onAttach(CacheOperations<K, V> cache) {
        // Default: no action
    }
    
    /**
     * Called when the strategy is detached from a cache.
     * 
     * @param cache The cache this strategy was attached to
     */
    default void onDetach(CacheOperations<K, V> cache) {
        // Default: no action
    }
}

