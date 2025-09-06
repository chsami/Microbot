package net.runelite.client.plugins.microbot.util.cache.strategy;

import java.util.Map;
import java.util.stream.Stream;

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
     * Gets a raw cached value without triggering additional cache operations like scene scans.
     * This method bypasses any cache miss handling and returns only what's currently cached.
     * Used by update strategies during scene synchronization to avoid recursive scanning.
     * 
     * @param key The key to retrieve
     * @return The raw cached value or null if not present in cache
     */
    V getRawValue(K key);
    
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
    
    /**
     * Provides a stream of all cache entries.
     * This allows for efficient filtering and processing of cache contents.
     * Note: The stream should be used within the same thread context and not cached.
     * 
     * @return A stream of Map.Entry<K, V> representing all cache entries
     */
    Stream<Map.Entry<K, V>> entryStream();
    
    /**
     * Provides a stream of all cache keys.
     * 
     * @return A stream of keys in the cache
     */
    Stream<K> keyStream();
    
    /**
     * Provides a stream of all cache values.
     * 
     * @return A stream of values in the cache
     */
    Stream<V> valueStream();
    
    /**
     * Gets the name of this cache for logging and debugging.
     * 
     * @return The cache name
     */
    String getCacheName();

}
