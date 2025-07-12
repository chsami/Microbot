package net.runelite.client.plugins.microbot.util.cache;

/**
 * Enumeration defining different cache invalidation modes.
 * Controls how and when cache entries are invalidated.
 * 
 * @author Vox
 * @version 1.0
 */
public enum CacheMode {
    /**
     * Automatic invalidation mode.
     * Cache entries are automatically invalidated based on TTL (Time-To-Live) 
     * and global invalidation intervals. This is the default behavior for 
     * most caches that need periodic refresh.
     */
    AUTOMATIC_INVALIDATION,
    
    /**
     * Event-driven invalidation mode.
     * Cache entries are only invalidated when specific events occur.
     * No automatic timeout-based invalidation is performed.
     * 
     * This mode is ideal for entity caches (NPCs, Objects, Ground Items) 
     * where data should persist until:
     * - GameState changes
     * - Entity despawn events
     * - Manual invalidation
     */
    EVENT_DRIVEN_ONLY,
    
    /**
     * Manual invalidation mode.
     * Cache entries are never automatically invalidated.
     * Invalidation must be triggered manually by calling invalidation methods.
     * 
     * This mode provides maximum control over cache lifecycle
     * and is suitable for data that rarely changes.
     */
    MANUAL_ONLY
}
