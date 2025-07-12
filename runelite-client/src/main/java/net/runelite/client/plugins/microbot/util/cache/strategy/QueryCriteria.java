package net.runelite.client.plugins.microbot.util.cache.strategy;

/**
 * Base interface for query criteria.
 * Specific strategies can define their own criteria types.
 */
public interface QueryCriteria {
    
    /**
     * Gets the type identifier for this query criteria.
     * 
     * @return The query type string
     */
    String getQueryType();
}
