package net.runelite.client.plugins.microbot.util.cache.strategy;

import java.util.function.Predicate;

/**
 * Predicate-based query criteria for simple filtering.
 * 
 * @param <V> The value type to filter
 */
public class PredicateQuery<V> implements QueryCriteria {
    
    private final Predicate<V> predicate;
    private final String description;
    
    /**
     * Creates a new predicate query.
     * 
     * @param predicate The predicate to apply
     * @param description A description of what this query does
     */
    public PredicateQuery(Predicate<V> predicate, String description) {
        this.predicate = predicate;
        this.description = description;
    }
    
    /**
     * Gets the predicate for this query.
     * 
     * @return The predicate function
     */
    public Predicate<V> getPredicate() {
        return predicate;
    }
    
    /**
     * Gets the description of this query.
     * 
     * @return The query description
     */
    public String getDescription() {
        return description;
    }
    
    @Override
    public String getQueryType() {
        return "PREDICATE:" + description;
    }
}
