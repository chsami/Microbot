package net.runelite.client.plugins.microbot.util.cache.strategy;

/**
 * Strategy interface for wrapping values before storing them in cache.
 * Allows adding metadata, spawn tracking, etc. without inheritance.
 * 
 * @param <V> The original value type
 * @param <W> The wrapped value type
 */
public interface ValueWrapper<V, W> {
    
    /**
     * Wraps a value before storing in cache.
     * 
     * @param value The original value
     * @param key The cache key for context
     * @return The wrapped value
     */
    W wrap(V value, Object key);
    
    /**
     * Unwraps a value when retrieving from cache.
     * 
     * @param wrappedValue The wrapped value from cache
     * @return The original value
     */
    V unwrap(W wrappedValue);
    
    /**
     * Gets the wrapped value type for type safety.
     * 
     * @return The wrapped type class
     */
    Class<W> getWrappedType();
}
