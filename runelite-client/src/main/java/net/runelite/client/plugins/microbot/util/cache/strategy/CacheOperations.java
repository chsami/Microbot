package net.runelite.client.plugins.microbot.util.cache.strategy;

/**
 * Interface providing controlled access to cache operations for strategies.
 * This follows the framework guideline of providing limited, safe access to cache internals.
 */
public interface CacheOperations<K, V> {
    
    /**
     * Gets a value from the cache.
     * 
     * @param key The key to retrieve
     * @return The cached value or null if not found
     */
    V get(K key);
    
    /**
     * Puts a value into the cache.
     * 
     * @param key The key to store
     * @param value The value to store
     */
    void put(K key, V value);
    
    /**
     * Removes a specific key from the cache.
     * 
     * @param key The key to remove
     */
    void remove(K key);
    
    /**
     * Invalidates all cached data.
     */
    void invalidateAll();
    
    /**
     * Checks if the cache contains a specific key.
     * 
     * @param key The key to check
     * @return True if the key exists and is not expired
     */
    boolean containsKey(K key);
    
    /**
     * Gets the current size of the cache.
     * 
     * @return The number of entries in the cache
     */
    int size();
}
