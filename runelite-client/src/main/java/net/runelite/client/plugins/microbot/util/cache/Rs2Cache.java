package net.runelite.client.plugins.microbot.util.cache;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.cache.strategy.*;

import java.time.Instant;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Abstract base cache implementation following game cache framework guidelines.
 * 
 * This abstract class provides common cache functionality and is designed to be extended
 * by specific cache implementations like Rs2NpcCache, Rs2SkillCache, etc.
 * 
 * Key improvements:
 * - Composition over inheritance via strategy pattern
 * - Pluggable invalidation, query, and wrapper strategies  
 * - Thread-safe reads with minimal locks
 * - Unified interface for all cache types
 * - Configurable eviction policies
 * - Event-driven invalidation support
 * 
 * @param <K> The type of keys used in the cache
 * @param <V> The type of values stored in the cache
 */
@Slf4j
public abstract class Rs2Cache<K, V> implements AutoCloseable, CacheOperations<K, V> {
    
    @Getter
    private final String cacheName;
    
    @Getter
    private final CacheMode cacheMode;
    
    // Core cache storage
    private final ConcurrentHashMap<K, Object> cache; // Object to support wrapped values
    private final ConcurrentHashMap<K, Long> cacheTimestamps;
    private final AtomicLong lastGlobalInvalidation;
    private final AtomicBoolean isShutdown;
    private final AtomicLong cacheHits;
    private final AtomicLong cacheMisses;
    private final AtomicLong totalInvalidations;
    
    // Cache configuration
    private final long ttlMillis;
    private final long globalInvalidationInterval;
    private final long creationTime;
    
    // Strategy composition - following framework guidelines
    private final List<CacheUpdateStrategy<K, V>> updateStrategies;
    private final List<QueryStrategy<K, V>> queryStrategies;
    @SuppressWarnings("rawtypes")
    private volatile ValueWrapper valueWrapper; // Optional value wrapping
    
    // Optional scheduled cleanup task
    private ScheduledFuture<?> cleanupTask;
    
    // ============================================
    // Serialization Support
    // ============================================
    
    private String configKey;
    private boolean persistenceEnabled = false;
    
    /**
     * Enables persistence for this cache with the specified config key.
     * The cache will be automatically saved and loaded from RuneLite profile configuration.
     * 
     * @param configKey The config key to use for persistence
     * @return This cache for method chaining
     */
    public Rs2Cache<K, V> withPersistence(String configKey) {
        this.configKey = configKey;
        this.persistenceEnabled = true;
        log.debug("Enabled persistence for cache {} with config key: {}", cacheName, configKey);
        return this;
    }
    
    /**
     * Gets the config key for this cache.
     * 
     * @return The config key, or null if persistence is not enabled
     */
    public String getConfigKey() {
        return configKey;
    }
    
    /**
     * Checks if persistence is enabled for this cache.
     * 
     * @return true if persistence is enabled
     */
    public boolean isPersistenceEnabled() {
        return persistenceEnabled;
    }
    

    
    /**
     * Constructor for cache with default configuration.
     * 
     * @param cacheName The name of this cache for logging and debugging
     */
    public Rs2Cache(String cacheName) {
        this(cacheName, CacheMode.AUTOMATIC_INVALIDATION, 30_000L, 60_000L);
    }
    
    /**
     * Constructor for cache with specific mode.
     * 
     * @param cacheName The name of this cache for logging and debugging
     * @param cacheMode The cache invalidation mode
     */
    public Rs2Cache(String cacheName, CacheMode cacheMode) {
        this(cacheName, cacheMode, 30_000L, 60_000L);
    }
    
    /**
     * Constructor for cache with full configuration.
     * 
     * @param cacheName The name of this cache for logging and debugging
     * @param cacheMode The cache invalidation mode
     * @param ttlMillis Time-to-live for individual cache entries in milliseconds
     * @param globalInvalidationInterval Interval for global cache invalidation in milliseconds
     */
    public Rs2Cache(String cacheName, CacheMode cacheMode, long ttlMillis, long globalInvalidationInterval) {
        this.cacheName = cacheName;
        this.cacheMode = cacheMode;
        this.ttlMillis = ttlMillis;
        this.globalInvalidationInterval = globalInvalidationInterval;
        this.creationTime = System.currentTimeMillis();
        
        this.cache = new ConcurrentHashMap<>();
        this.cacheTimestamps = new ConcurrentHashMap<>();
        this.lastGlobalInvalidation = new AtomicLong(System.currentTimeMillis());
        this.isShutdown = new AtomicBoolean(false);
        this.cacheHits = new AtomicLong(0);
        this.cacheMisses = new AtomicLong(0);
        this.totalInvalidations = new AtomicLong(0);
        
        // Initialize strategy collections - thread-safe
        this.updateStrategies = new CopyOnWriteArrayList<>();
        this.queryStrategies = new CopyOnWriteArrayList<>();
        this.valueWrapper = null;
        
        log.debug("Created unified cache: {} with mode: {}, TTL: {}ms, Global invalidation: {}ms", 
                cacheName, cacheMode, ttlMillis, globalInvalidationInterval);
    }
    
    // ============================================
    // Strategy Management - Composition Pattern
    // ============================================
    
    /**
     * Adds an invalidation strategy to this cache.
     * Follows framework guideline: "Pluggable invalidation strategies"
     * 
     * @param strategy The invalidation strategy to add
     * @return This cache for method chaining
     */
    public Rs2Cache<K, V> withUpdateStrategy(CacheUpdateStrategy<K, V> strategy) {
        updateStrategies.add(strategy);
        strategy.onAttach(this);
        log.debug("Added invalidation strategy {} to cache {}", strategy.getClass().getSimpleName(), cacheName);
        return this;
    }
    
    /**
     * Adds a query strategy to this cache.
     * Enables specialized queries without inheritance.
     * 
     * @param strategy The query strategy to add
     * @return This cache for method chaining
     */
    public Rs2Cache<K, V> withQueries(QueryStrategy<K, V> strategy) {
        queryStrategies.add(strategy);
        log.debug("Added query strategy {} to cache {}", strategy.getClass().getSimpleName(), cacheName);
        return this;
    }
    
    /**
     * Sets a value wrapper strategy for this cache.
     * Enables entity tracking, metadata, etc. without inheritance.
     * 
     * @param wrapper The value wrapper to use
     * @return This cache for method chaining
     */
    @SuppressWarnings("rawtypes")
    public Rs2Cache<K, V> withWrapper(ValueWrapper wrapper) {
        this.valueWrapper = wrapper;
        log.debug("Added value wrapper {} to cache {}", wrapper.getClass().getSimpleName(), cacheName);
        return this;
    }
    
    // ============================================
    // Core Cache Operations - Thread-Safe
    // ============================================
    
    /**
     * Gets a value from the cache if it exists and is not expired.
     * Thread-safe reads with minimal locks following framework guidelines.
     * 
     * @param key The key to retrieve
     * @return The cached value or null if not found or expired
     */
    @Override
    @SuppressWarnings("unchecked")
    public V get(K key) {
        if (isShutdown.get()) {
            return null;
        }
        
        // Only check for automatic invalidation in AUTOMATIC_INVALIDATION mode
        if (cacheMode == CacheMode.AUTOMATIC_INVALIDATION) {
            checkGlobalInvalidation();
        }
        
        Object cachedValue = cache.get(key);
        Long timestamp = cacheTimestamps.get(key);
        
        // Check if value exists and is not expired (respect cache mode)
        if (cachedValue != null && timestamp != null && !isExpired(timestamp)) {
            cacheHits.incrementAndGet();
            log.trace("Cache hit for key {} in cache {}", key, cacheName);
            
            // Unwrap value if wrapper is present
            if (valueWrapper != null) {
                return (V) valueWrapper.unwrap(cachedValue);
            } else {
                return (V) cachedValue;
            }
        }
        
        cacheMisses.incrementAndGet();
        log.trace("Cache miss for key {} in cache {}", key, cacheName);
        return null;
    }
    
    /**
     * Retrieves a value from the cache. If not present or expired, loads it using the provided supplier.
     * 
     * @param key The key to retrieve
     * @param valueLoader Supplier function to load the value if not cached or expired
     * @return The cached or newly loaded value
     */
    public V get(K key, Supplier<V> valueLoader) {
        if (isShutdown.get()) {
            log.warn("Cache {} is shut down, loading value directly", cacheName);
            return valueLoader.get();
        }
        
        V cachedValue = get(key);
        if (cachedValue != null) {
            return cachedValue;
        }
        
        // Load new value
        try {
            V newValue = valueLoader.get();
            if (newValue != null) {
                put(key, newValue);
                log.trace("Loaded new value for key {} in cache {}", key, cacheName);
            }
            return newValue;
        } catch (Exception e) {
            log.error("Error loading value for key {} in cache {}: {}", key, cacheName, e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Puts a value into the cache with current timestamp.
     * 
     * @param key The key to store
     * @param value The value to store
     */
    @Override
    public void put(K key, V value) {
        if (isShutdown.get() || value == null) {
            return;
        }
        
        // Wrap value if wrapper is present
        Object valueToStore = value;
        if (valueWrapper != null) {
            @SuppressWarnings("unchecked")
            Object wrapped = valueWrapper.wrap(value, key);
            valueToStore = wrapped;
        }
        
        cache.put(key, valueToStore);
        cacheTimestamps.put(key, System.currentTimeMillis());
        
        log.trace("Put value for key {} in cache {}", key, cacheName);
    }
    
    /**
     * Removes a specific key from the cache.
     * 
     * @param key The key to remove
     */
    @Override
    public void remove(K key) {
        cache.remove(key);
        cacheTimestamps.remove(key);
        log.trace("Removed key {} from cache {}", key, cacheName);
    }
    
    /**
     * Invalidates all cached data.
     */
    @Override
    public void invalidateAll() {

        int sizeBefore = cache.size();
        cache.clear();
        cacheTimestamps.clear();
        lastGlobalInvalidation.set(System.currentTimeMillis());
        totalInvalidations.incrementAndGet();
        
        log.debug("Invalidated all {} entries in cache {}", sizeBefore, cacheName);
    }
    
    // ============================================
    // Query Strategy Support
    // ============================================
    
    /**
     * Executes a query using registered query strategies.
     * 
     * @param criteria The query criteria
     * @return Stream of matching values
     */
    public Stream<V> query(QueryCriteria criteria) {
        for (QueryStrategy<K, V> strategy : queryStrategies) {
            for (Class<? extends QueryCriteria> supportedType : strategy.getSupportedQueryTypes()) {
                if (supportedType.isInstance(criteria)) {
                    return strategy.executeQuery(this, criteria);
                }
            }
        }
        
        log.warn("No query strategy found for criteria type: {} in cache {}", 
                criteria.getClass().getSimpleName(), cacheName);
        return Stream.empty();
    }
    
    /**
     * Gets all non-expired values from the cache.
     * 
     * @return Collection of cached values
     */
    @SuppressWarnings("unchecked")
    public Collection<V> values() {
        if (isShutdown.get()) {
            return Collections.emptyList();
        }
        
        if (cacheMode == CacheMode.AUTOMATIC_INVALIDATION) {
            checkGlobalInvalidation();
        }
        
        return cache.entrySet().stream()
                .filter(entry -> {
                    Long timestamp = cacheTimestamps.get(entry.getKey());
                    return timestamp != null && !isExpired(timestamp);
                })
                .map(entry -> {
                    if (valueWrapper != null) {
                        return (V) valueWrapper.unwrap(entry.getValue());
                    } else {
                        return (V) entry.getValue();
                    }
                })
                .collect(Collectors.toList());
    }
    
    // ============================================
    // Stream Support for Specialized Caches
    // ============================================
    
    /**
     * Gets all values as a stream for specialized cache implementations.
     * Each specialized cache can use this to implement its own domain-specific methods.
     * 
     * @return Stream of all cached values
     */
    public Stream<V> stream() {
        return values().stream();
    }
    
    /**
     * Returns statistics as a formatted string for legacy compatibility.
     */
    public String getStatisticsString() {
        CacheStatistics stats = getStatistics();
        return String.format(
            "Cache: %s | Size: %d | Hits: %d | Misses: %d | Hit Rate: %.2f%% | Mode: %s | Invalidations: %d | Memory: %s",
            stats.cacheName,
            stats.currentSize,
            stats.cacheHits,
            stats.cacheMisses,
            stats.getHitRate() * 100,
            stats.cacheMode.toString(),
            stats.totalInvalidations,
            stats.getFormattedMemorySize()
        );
    }

    // ============================================
    // Event Handling for Strategies
    // ============================================
    
    /**
     * Handles an event by delegating to all registered invalidation strategies.
     * 
     * @param event The event to handle
     */
    public void handleEvent(Object event) {
        for (CacheUpdateStrategy<K, V> strategy : updateStrategies) {
            for (Class<?> eventType : strategy.getHandledEventTypes()) {
                if (eventType.isInstance(event)) {
                    try {
                        strategy.handleEvent(event, this);
                    } catch (Exception e) {
                        log.error("Error handling event {} in strategy {} for cache {}: {}", 
                                event.getClass().getSimpleName(), 
                                strategy.getClass().getSimpleName(), 
                                cacheName, e.getMessage(), e);
                    }
                    break;
                }
            }
        }
    }
    
    // ============================================
    // CacheOperations Interface Implementation
    // ============================================
    
    @Override
    public boolean containsKey(K key) {
        return cache.containsKey(key) && !isExpired(cacheTimestamps.get(key));
    }
    
    @Override
    public int size() {
        return cache.size();
    }
    
    // ============================================
    // Print Functions for Cache Information
    // ============================================
    
    /**
     * Returns a detailed formatted string containing all cache information.
     * Includes cache metadata, statistics, configuration, and stored data.
     * 
     * @return Detailed multi-line string representation of the cache
     */
    public String printDetailedCacheInfo() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        
        sb.append("=".repeat(80)).append("\n");
        sb.append("                     DETAILED CACHE INFORMATION\n");
        sb.append("=".repeat(80)).append("\n");
        
        // Cache metadata
        sb.append(String.format("Cache Name: %s\n", cacheName));
        sb.append(String.format("Cache Mode: %s\n", cacheMode));
        sb.append(String.format("Created: %s\n", formatter.format(Instant.ofEpochMilli(creationTime))));
        sb.append(String.format("Uptime: %d ms\n", System.currentTimeMillis() - creationTime));
        sb.append(String.format("Is Shutdown: %s\n", isShutdown.get()));
        sb.append("\n");
        
        // Configuration
        sb.append("CONFIGURATION:\n");
        sb.append(String.format("TTL (milliseconds): %d\n", ttlMillis));
        sb.append(String.format("Global Invalidation Interval: %d ms\n", globalInvalidationInterval));
        sb.append(String.format("Persistence Enabled: %s\n", persistenceEnabled));
        if (persistenceEnabled) {
            sb.append(String.format("Config Key: %s\n", configKey));
        }
        sb.append("\n");
        
        // Statistics
        CacheStatistics stats = getStatistics();
        sb.append("STATISTICS:\n");
        sb.append(String.format("Current Size: %d entries\n", stats.currentSize));
        sb.append(String.format("Cache Hits: %d\n", stats.cacheHits));
        sb.append(String.format("Cache Misses: %d\n", stats.cacheMisses));
        sb.append(String.format("Hit Rate: %.2f%%\n", stats.getHitRate() * 100));
        sb.append(String.format("Total Invalidations: %d\n", stats.totalInvalidations));
        sb.append(String.format("Last Global Invalidation: %s\n", 
                formatter.format(Instant.ofEpochMilli(lastGlobalInvalidation.get()))));
        sb.append("\n");
        
        // Strategies
        sb.append("STRATEGIES:\n");
        sb.append(String.format("Update Strategies: %d\n", updateStrategies.size()));
        for (CacheUpdateStrategy<K, V> strategy : updateStrategies) {
            sb.append(String.format("  - %s\n", strategy.getClass().getSimpleName()));
        }
        sb.append(String.format("Query Strategies: %d\n", queryStrategies.size()));
        for (QueryStrategy<K, V> strategy : queryStrategies) {
            sb.append(String.format("  - %s\n", strategy.getClass().getSimpleName()));
        }
        sb.append(String.format("Value Wrapper: %s\n", 
                valueWrapper != null ? valueWrapper.getClass().getSimpleName() : "None"));
        sb.append("\n");
        
        // Cache entries
        sb.append("-".repeat(80)).append("\n");
        sb.append("                            CACHE ENTRIES\n");
        sb.append("-".repeat(80)).append("\n");
        
        sb.append(String.format("%-20s %-30s %-19s\n", "KEY", "VALUE", "TIMESTAMP"));
        sb.append("-".repeat(80)).append("\n");
        
        cache.entrySet().stream()
                .sorted((e1, e2) -> {
                    Long t1 = cacheTimestamps.get(e1.getKey());
                    Long t2 = cacheTimestamps.get(e2.getKey());
                    if (t1 == null || t2 == null) return 0;
                    return Long.compare(t2, t1); // Most recent first
                })
                .forEach(entry -> {
                    String key = String.valueOf(entry.getKey());
                    String value = String.valueOf(entry.getValue());
                    Long timestamp = cacheTimestamps.get(entry.getKey());
                    String timestampStr = timestamp != null ? 
                            formatter.format(Instant.ofEpochMilli(timestamp)) : "Unknown";
                    
                    // Truncate long values
                    if (key.length() > 20) key = key.substring(0, 17) + "...";
                    if (value.length() > 30) value = value.substring(0, 27) + "...";
                    
                    sb.append(String.format("%-20s %-30s %-19s\n", key, value, timestampStr));
                });
        
        if (cache.isEmpty()) {
            sb.append("No entries in cache\n");
        }
        
        sb.append("-".repeat(80)).append("\n");
        sb.append(String.format("Generated at: %s\n", formatter.format(Instant.now())));
        sb.append("=".repeat(80));
        
        return sb.toString();
    }
    
    /**
     * Returns a summary formatted string containing essential cache information.
     * Compact view showing key metrics and status.
     * 
     * @return Summary multi-line string representation of the cache
     */
    public String printCacheSummary() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        
        sb.append("┌─ CACHE SUMMARY: ").append(cacheName).append(" ")
                .append("─".repeat(Math.max(1, 45 - cacheName.length()))).append("┐\n");
        
        CacheStatistics stats = getStatistics();
        
        // Summary statistics
        sb.append(String.format("│ Entries: %-3d │ Mode: %-12s │ Status: %-8s │\n",
                stats.currentSize, cacheMode, isShutdown.get() ? "Shutdown" : "Active"));
        
        sb.append(String.format("│ Hits: %-6d │ Misses: %-6d │ Hit Rate: %5.1f%% │\n",
                stats.cacheHits, stats.cacheMisses, stats.getHitRate() * 100));
        
        // Configuration summary
        sb.append(String.format("│ TTL: %-7d ms │ Invalidations: %-8d │\n",
                ttlMillis, stats.totalInvalidations));
        
        // Strategy summary
        sb.append(String.format("│ Strategies: %-2d │ Persistence: %-8s │\n",
                updateStrategies.size() + queryStrategies.size(),
                persistenceEnabled ? "Enabled" : "Disabled"));
        
        // Uptime
        long uptimeMs = System.currentTimeMillis() - creationTime;
        String uptimeStr;
        if (uptimeMs < 60000) {
            uptimeStr = String.format("%ds", uptimeMs / 1000);
        } else if (uptimeMs < 3600000) {
            uptimeStr = String.format("%dm %ds", uptimeMs / 60000, (uptimeMs % 60000) / 1000);
        } else {
            uptimeStr = String.format("%dh %dm", uptimeMs / 3600000, (uptimeMs % 3600000) / 60000);
        }
        
        sb.append(String.format("│ Uptime: %-16s │ Generated: %-8s │\n",
                uptimeStr, formatter.format(Instant.now())));
        
        sb.append("└").append("─".repeat(63)).append("┘");
        
        return sb.toString();
    }
    
    // ============================================
    // Entry Access for Serialization
    // ============================================
    
    /**
     * Gets all cache entries for serialization purposes.
     * Returns a stream of key-value entries that are not expired.
     * 
     * @return Stream of cache entries suitable for serialization
     */
    public Stream<Map.Entry<K, V>> entryStream() {
        if (isShutdown.get()) {
            return Stream.empty();
        }
        
        if (cacheMode == CacheMode.AUTOMATIC_INVALIDATION) {
            checkGlobalInvalidation();
        }
        
        return cache.entrySet().stream()
                .filter(entry -> {
                    Long timestamp = cacheTimestamps.get(entry.getKey());
                    return timestamp != null && !isExpired(timestamp);
                })
                .map(entry -> {
                    V value;
                    if (valueWrapper != null) {
                        value = (V) valueWrapper.unwrap(entry.getValue());
                    } else {
                        value = (V) entry.getValue();
                    }
                    return new AbstractMap.SimpleEntry<>(entry.getKey(), value);
                });
    }
    
    /**
     * Gets all cache entries as a map for serialization purposes.
     * Only returns non-expired entries.
     * 
     * @return Map of all non-expired cache entries
     */
    public Map<K, V> getEntriesForSerialization() {
        Map<K, V> result = new ConcurrentHashMap<>();
        
        if (isShutdown.get()) {
            return result;
        }
        
        // Check for automatic invalidation
        if (cacheMode == CacheMode.AUTOMATIC_INVALIDATION) {
            checkGlobalInvalidation();
        }
        
        for (Map.Entry<K, Object> entry : cache.entrySet()) {
            K key = entry.getKey();
            Long timestamp = cacheTimestamps.get(key);
            
            // Only include non-expired entries
            if (timestamp != null && !isExpired(timestamp)) {
                Object cachedValue = entry.getValue();
                
                // Unwrap value if wrapper is present
                V value;
                if (valueWrapper != null) {
                    @SuppressWarnings("unchecked")
                    V unwrappedValue = (V) valueWrapper.unwrap(cachedValue);
                    value = unwrappedValue;
                } else {
                    @SuppressWarnings("unchecked")
                    V castedValue = (V) cachedValue;
                    value = castedValue;
                }
                
                result.put(key, value);
            }
        }
        
        return result;
    }
    
    /**
     * Gets all cache entries as a map for serialization.
     * 
     * @return Map of all non-expired cache entries
     */
    public Map<K, V> entryMap() {
        return entryStream().collect(Collectors.toMap(
                Map.Entry::getKey,
                Map.Entry::getValue,
                (existing, replacement) -> replacement, // Handle duplicates by keeping the latest
                ConcurrentHashMap::new
        ));
    }
    
    // ============================================
    // Private Helper Methods
    // ============================================
    
    /**
     * Checks if a timestamp is expired based on the TTL and cache mode.
     */
    private boolean isExpired(Long timestamp) {
        if (timestamp == null) {
            return true;
        }
        
        // EVENT_DRIVEN_ONLY mode: entries never expire by time
        if (cacheMode == CacheMode.EVENT_DRIVEN_ONLY) {
            return false;
        }
        
        // MANUAL_ONLY mode: entries never expire automatically
        if (cacheMode == CacheMode.MANUAL_ONLY) {
            return false;
        }
        
        // AUTOMATIC_INVALIDATION mode: check TTL
        return System.currentTimeMillis() - timestamp > ttlMillis;
    }
    
    /**
     * Checks if global invalidation is needed and performs it if necessary.
     */
    private void checkGlobalInvalidation() {
        if (cacheMode != CacheMode.AUTOMATIC_INVALIDATION) {
            return;
        }
        
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastGlobalInvalidation.get() > globalInvalidationInterval) {
            if (lastGlobalInvalidation.compareAndSet(lastGlobalInvalidation.get(), currentTime)) {
                log.debug("Performing global invalidation for cache {}", cacheName);
                invalidateAll();
            }
        }
    }
    
    /**
     * Calculates the estimated memory size of this cache in bytes.
     * Includes keys, values, timestamps, and internal data structures.
     * 
     * @return Estimated memory usage in bytes
     */
    public long getEstimatedMemorySize() {
        if (cache.isEmpty()) {
            return getEmptyCacheMemorySize();
        }
        
        long totalSize = 0;
        
        // Base cache object overhead
        totalSize += getBaseCacheMemorySize();
        
        // Calculate size of stored entries
        long keySize = 0;
        long valueSize = 0;
        long timestampSize = 0;
        
        // Sample a few entries to estimate average sizes
        int sampleSize = Math.min(5, cache.size());
        int sampledEntries = 0;
        
        for (Map.Entry<K, Object> entry : cache.entrySet()) {
            if (sampledEntries >= sampleSize) break;
            
            keySize += MemorySizeCalculator.calculateKeySize(entry.getKey());
            valueSize += MemorySizeCalculator.calculateValueSize(entry.getValue());
            timestampSize += Long.BYTES; // Long timestamp
            
            sampledEntries++;
        }
        
        if (sampledEntries > 0) {
            // Calculate average sizes and multiply by total entry count
            long avgKeySize = keySize / sampledEntries;
            long avgValueSize = valueSize / sampledEntries;
            long avgTimestampSize = timestampSize / sampledEntries;
            
            totalSize += (avgKeySize + avgValueSize + avgTimestampSize) * cache.size();
            
            // Add ConcurrentHashMap overhead per entry (Node objects, buckets)
            totalSize += cache.size() * 64; // Estimated overhead per map entry
        }
        
        return totalSize;
    }
    
    /**
     * Gets the base memory size of an empty cache.
     */
    private long getEmptyCacheMemorySize() {
        long size = 0;
        
        // Object header + all instance fields
        size += 12; // Object header (64-bit JVM with compressed OOPs)
        size += 4 * 8; // 8 reference fields (String, CacheMode, 2 ConcurrentHashMaps, 4 AtomicLong, AtomicBoolean)
        size += 8 * 4; // 4 long fields
        size += 4 * 2; // 2 int fields (if any)
        size += 4 * 2; // 2 CopyOnWriteArrayList references
        size += 4; // ValueWrapper reference
        
        // Empty ConcurrentHashMap overhead (x2 for cache and timestamps)
        size += 2 * (12 + 4 + 4*3 + 16 + 16*4); // Object + fields + empty bucket array
        
        // AtomicLong objects (6 total)
        size += 6 * (12 + 8); // Object header + long value
        
        // AtomicBoolean object
        size += 12 + 1; // Object header + boolean value
        
        // CopyOnWriteArrayList objects (2 total)
        size += 2 * (12 + 4*3 + 16); // Object + fields + empty array
        
        // String objects (cacheName)
        if (cacheName != null) {
            size += 12 + 4 + 16 + (cacheName.length() * 2); // String + char array
        }
        
        return size;
    }
    
    /**
     * Gets the base memory size of cache infrastructure.
     */
    private long getBaseCacheMemorySize() {
        long size = getEmptyCacheMemorySize();
        
        // Add strategy collections overhead
        size += updateStrategies.size() * 4; // Reference per strategy
        size += queryStrategies.size() * 4; // Reference per strategy
        
        // Add minimal strategy object overhead (strategies are typically small)
        size += (updateStrategies.size() + queryStrategies.size()) * 32; // Estimated per strategy
        
        return size;
    }
    
    /**
     * Returns memory usage information as a formatted string.
     */
    public String getMemoryUsageString() {
        long memoryBytes = getEstimatedMemorySize();
        return String.format("Memory: %s (%d bytes)", 
                MemorySizeCalculator.formatMemorySize(memoryBytes), memoryBytes);
    }
    
    /**
     * Gets cache statistics for monitoring, including memory usage.
     */
    public CacheStatistics getStatistics() {
        return new CacheStatistics(
                cacheName,
                cacheMode,
                size(),
                cacheHits.get(),
                cacheMisses.get(),
                totalInvalidations.get(),
                System.currentTimeMillis() - creationTime,
                ttlMillis,
                globalInvalidationInterval,
                getEstimatedMemorySize()
        );
    }
    
    /**
     * Cache statistics data class.
     */
    public static class CacheStatistics {
        public final String cacheName;
        public final CacheMode cacheMode;
        public final int currentSize;
        public final long cacheHits;
        public final long cacheMisses;
        public final long totalInvalidations;
        public final long uptime;
        public final long ttlMillis;
        public final long globalInvalidationInterval;
        public final long estimatedMemoryBytes;
        
        public CacheStatistics(String cacheName, CacheMode cacheMode, int currentSize, 
                             long cacheHits, long cacheMisses, long totalInvalidations,
                             long uptime, long ttlMillis, long globalInvalidationInterval,
                             long estimatedMemoryBytes) {
            this.cacheName = cacheName;
            this.cacheMode = cacheMode;
            this.currentSize = currentSize;
            this.cacheHits = cacheHits;
            this.cacheMisses = cacheMisses;
            this.totalInvalidations = totalInvalidations;
            this.uptime = uptime;
            this.ttlMillis = ttlMillis;
            this.globalInvalidationInterval = globalInvalidationInterval;
            this.estimatedMemoryBytes = estimatedMemoryBytes;
        }
        
        public double getHitRate() {
            long total = cacheHits + cacheMisses;
            return total == 0 ? 0.0 : (double) cacheHits / total;
        }
        
        public String getFormattedMemorySize() {
            return MemorySizeCalculator.formatMemorySize(estimatedMemoryBytes);
        }
    }
    
    // ============================================
    // Abstract Methods for Specialized Cache Updates
    // ============================================
    
    /**
     * Updates all cached data by retrieving fresh values from the game client.
     * Each cache implementation must provide its own strategy for refreshing cached data.
     * This method should iterate over existing cache entries and refresh them with current data.
     */
    public abstract void update();
    
    @Override
    public void close() {
        if (isShutdown.compareAndSet(false, true)) {
            if (cleanupTask != null) {
                cleanupTask.cancel(true);
            }
            
            // Detach all strategies
            for (CacheUpdateStrategy<K, V> strategy : updateStrategies) {
                try {
                    strategy.onDetach(this);
                } catch (Exception e) {
                    log.warn("Error detaching strategy {} from cache {}: {}", 
                            strategy.getClass().getSimpleName(), cacheName, e.getMessage());
                }
            }
            
            cache.clear();
            cacheTimestamps.clear();
            log.debug("Closed cache: {}", cacheName);
        }
    }
}
