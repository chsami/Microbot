package net.runelite.client.plugins.microbot.util.cache.strategy;

import java.util.stream.Stream;

/**
 * Strategy interface for specialized cache queries.
 * Allows caches to support complex queries without inheritance.
 * 
 * @param <K> Cache key type
 * @param <V> Cache value type
 */
public interface QueryStrategy<K, V> {
    
    /**
     * Executes a query against the cache.
     * 
     * @param cache The cache to query
     * @param criteria The query criteria
     * @return Stream of matching values
     */
    Stream<V> executeQuery(CacheOperations<K, V> cache, QueryCriteria criteria);
    
    /**
     * Gets the query types this strategy supports.
     * 
     * @return Array of supported query criteria classes
     */
    Class<? extends QueryCriteria>[] getSupportedQueryTypes();
}
