package net.runelite.client.plugins.microbot.util.cache;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.strategy.simple.VarbitUpdateStrategy;
import net.runelite.client.plugins.microbot.util.cache.serialization.CacheSerializable;
import net.runelite.client.plugins.microbot.util.cache.model.VarbitData;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Thread-safe cache for varbit values using the unified cache architecture.
 * Automatically updates when VarbitChanged events are received and supports persistence.
 * Stores VarbitData with contextual information about when and where changes occurred.
 * 
 * This class extends Rs2UnifiedCache and provides specific varbit caching functionality
 * with proper EventBus integration for @Subscribe methods.
 */
@Slf4j
public class Rs2VarbitCache extends Rs2Cache<Integer, VarbitData> implements CacheSerializable {
    
    private static Rs2VarbitCache instance;
    
    /**
     * Private constructor for singleton pattern.
     */
    private Rs2VarbitCache() {
        super("VarbitCache", CacheMode.EVENT_DRIVEN_ONLY);
        this.withUpdateStrategy(new VarbitUpdateStrategy())
                .withPersistence("varbits");
    }
    
    /**
     * Gets the singleton instance of Rs2VarbitCache.
     * 
     * @return The singleton varbit cache instance
     */
    public static synchronized Rs2VarbitCache getInstance() {
        if (instance == null) {
            instance = new Rs2VarbitCache();
        }
        return instance;
    }
    
    /**
     * Gets the cache instance for backward compatibility.
     * 
     * @return The singleton unified cache instance
     */
    public static Rs2Cache<Integer, VarbitData> getCache() {
        return getInstance();
    }
    
    public synchronized void close() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
    
    /**
     * Gets a varbit value from the cache or loads it from the client.
     * 
     * @param varbitId The varbit ID to retrieve
     * @return The varbit value
     */
    public static int getVarbitValue(int varbitId) {
        VarbitData data = getVarbitData(varbitId);        
        return data != null ? data.getValue() : 0;
    }
    
    /**
     * Loads varbit data from the client for a specific varbit ID.
     * 
     * @param varbitId The varbit ID to load data for
     * @return The VarbitData containing the current value
     */
    private static VarbitData loadVarbitDataFromClient(int varbitId) {
        try {
            // Additional safety check
            if (Microbot.getClient() == null) {
                log.warn("Client is null when loading varbit {}", varbitId);
                return new VarbitData(0);
            }
            
            int value = Microbot.getClientThread().runOnClientThreadOptional(() -> 
                Microbot.getClient().getVarbitValue(varbitId)).orElse(-1);
            log.debug("Loaded varbit from client: {} = {}", varbitId, value);
            return new VarbitData(value);
        } catch (Exception e) {
            log.error("Error loading varbit {}: {}", varbitId, e.getMessage(), e);
            return new VarbitData(-1);
        }
    }
    
    /**
     * Gets varbit data from the cache or loads it from the client.
     * 
     * @param varbitId The varbit ID to retrieve
     * @return The VarbitData containing value and contextual information
     */
    public static VarbitData getVarbitData(int varbitId) {
        // Validate input
        if (varbitId < 0) {
            log.warn("Invalid varbit ID: {}", varbitId);
            return new VarbitData(0);
        }        
        VarbitData cachedValue = getInstance().get(varbitId, () -> loadVarbitDataFromClient(varbitId));        
        if (cachedValue == null) {
            log.warn("Varbit {} not found in cache, returning default value", varbitId);
            return new VarbitData(-1);
        }
        return cachedValue;
    }
    
  

    
    /**
     * Manually updates a varbit value in the cache.
     * 
     * @param varbitId The varbit ID to update
     * @param value The new value
     */
    public static void updateVarbitValue(int varbitId, int value) {
        VarbitData oldData = getInstance().get(Integer.valueOf(varbitId));
        VarbitData newData = oldData != null ? 
            oldData.withUpdate(value, null, null, null) : 
            new VarbitData(value);
        
        getInstance().put(varbitId, newData);
        log.debug("Updated varbit cache: {} = {}", varbitId, value);
    }
    
    /**
     * Manually updates varbit data in the cache.
     * 
     * @param varbitId The varbit ID to update
     * @param varbitData The new varbit data
     */
    public static void updateVarbitData(int varbitId, VarbitData varbitData) {
        getInstance().put(varbitId, varbitData);
        log.debug("Updated varbit cache data: {} = {}", varbitId, varbitData.getValue());
    }
    
    /**
     * Invalidates all varbit cache entries.
     */
    public static void invalidateAllVarbits() {
        getInstance().invalidateAll();
        log.debug("Invalidated all varbit cache entries");
    }
    
    /**
     * Invalidates a specific varbit cache entry.
     * 
     * @param varbitId The varbit ID to invalidate
     */
    public static void invalidateVarbit(int varbitId) {
        getInstance().remove(varbitId);
        log.debug("Invalidated varbit cache entry: {}", varbitId);
    }
    
    /**
     * Updates all cached varbits by retrieving fresh data from the game client.
     * This method iterates over all currently cached varbits and refreshes their data.
     * Since varbits can have any ID, this only updates currently cached entries.
     */
    public static void updateAllFromClient() {
        getInstance().update();
    }
    
    /**
     * Updates all cached data by retrieving fresh values from the game client.
     * Implements the abstract method from Rs2Cache.
     * 
     * Iterates over all currently cached varbit keys and refreshes their values from the client.
     */
    @Override
    public void update() {
        log.debug("Updating all cached varbits from client...");
        
        if (Microbot.getClient() == null) {
            log.warn("Cannot update varbits - client is null");
            return;
        }
        
        int beforeSize = size();
        int updatedCount = 0;
        
        // Get all currently cached varbit IDs (keys) and update them
        java.util.Set<Integer> cachedVarbitIds = entryStream()
            .map(java.util.Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
        
        for (Integer varbitId : cachedVarbitIds) {
            try {
                // Refresh the data from client using the private method
                VarbitData freshData = loadVarbitDataFromClient(varbitId);
                if (freshData != null) {
                    put(varbitId, freshData);
                    updatedCount++;
                    log.debug("Updated varbit {} with fresh value: {}", varbitId, freshData.getValue());
                }
            } catch (Exception e) {
                log.warn("Failed to update varbit {}: {}", varbitId, e.getMessage());
            }
        }
        
        log.info("Updated {} varbits from client (cache had {} entries total)", 
                updatedCount, beforeSize);
    }
    
    // ============================================
    // Legacy API Compatibility Methods
    // ============================================
    
    /**
     * Gets varbit value - Legacy compatibility method (already available as getVarbitValue).
     * 
     * @param varbitId The varbit ID
     * @return The varbit value
     */
    public static int get(int varbitId) {
        return getVarbitValue(varbitId);
    }
    
   
    
    /**
     * Event handler registration for the unified cache.
     * The unified cache handles events through its strategy automatically.
     */
  
        
    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        try {                                                         
            getInstance().handleEvent(event);
        } catch (Exception e) {
            log.error("Error handling VarbitChanged event: {}", e.getMessage(), e);
        }
    }
    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        try {
            switch (event.getGameState()) {
                case LOGGED_IN:
                case HOPPING:
                case LOGIN_SCREEN:
                case CONNECTION_LOST:
                    // Let the strategy handle cache invalidation                    
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling GameStateChanged event: {}", e.getMessage(), e);
        }
    }
    
    
    /**
     * Resets the singleton instance. Used for testing.
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }

    // ============================================
    // CacheSerializable Implementation
    // ============================================
    
    @Override
    public String getConfigKey() {
        return "varbits";
    }
    
    @Override
    public String getConfigGroup() {
        return "microbot";
    }
    
    @Override
    public boolean shouldPersist() {
        return true; // Varbits should be persisted for game state tracking
    }
    
    // ============================================
    // Print Functions for Cache Information
    // ============================================
    
    /**
     * Returns a detailed formatted string containing all varbit cache information.
     * Includes complete varbit data with contextual tracking and change information.
     * 
     * @return Detailed multi-line string representation of all cached varbits
     */
    public static String printDetailedVarbitInfo() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        
        sb.append("=".repeat(80)).append("\n");
        sb.append("                     DETAILED VARBIT CACHE INFORMATION\n");
        sb.append("=".repeat(80)).append("\n");
        
        // Cache metadata
        Rs2Cache<Integer, VarbitData> cache = getInstance();
        sb.append(String.format("Cache Name: %s\n", cache.getCacheName()));
        sb.append(String.format("Cache Mode: %s\n", cache.getCacheMode()));
        sb.append(String.format("Total Cached Varbits: %d\n", cache.size()));
        
        // Cache statistics
        var stats = cache.getStatistics();
        sb.append(String.format("Cache Hits: %d\n", stats.cacheHits));
        sb.append(String.format("Cache Misses: %d\n", stats.cacheMisses));
        sb.append(String.format("Hit Ratio: %.2f%%\n", stats.getHitRate() * 100));
        sb.append(String.format("Total Invalidations: %d\n", stats.totalInvalidations));
        sb.append(String.format("Uptime: %d ms\n", stats.uptime));
        sb.append(String.format("TTL: %d ms\n", stats.ttlMillis));
        sb.append("\n");
        
        sb.append("-".repeat(80)).append("\n");
        sb.append("                            VARBIT DETAILS\n");
        sb.append("-".repeat(80)).append("\n");
        
        // Headers
        sb.append(String.format("%-10s %-8s %-13s %-15s %-25s %-19s\n", 
                "VARBIT ID", "VALUE", "PREV VALUE", "CHANGED", "PLAYER LOCATION", "LAST UPDATED"));
        sb.append("-".repeat(80)).append("\n");
        
        // Get all varbit entries and sort by ID
        cache.values().stream()
                .filter(data -> data != null)
                .sorted((data1, data2) -> {
                    // We need to find the keys for sorting, but we'll sort by last updated for now
                    return Long.compare(data2.getLastUpdated(), data1.getLastUpdated());
                })
                .forEach(data -> {
                    String lastUpdated = formatter.format(Instant.ofEpochMilli(data.getLastUpdated()));
                    String prevValue = data.getPreviousValue() != null ? 
                            data.getPreviousValue().toString() : "-";
                    String changed = data.hasValueChanged() ? "YES" : "-";
                    String location = data.getPlayerLocation() != null ? 
                            String.format("(%d,%d,%d)", 
                                    data.getPlayerLocation().getX(),
                                    data.getPlayerLocation().getY(),
                                    data.getPlayerLocation().getPlane()) : "-";
                    
                    sb.append(String.format("%-10s %-8d %-13s %-15s %-25s %-19s\n",
                            "Unknown", // We don't have access to the varbit ID in this context
                            data.getValue(),
                            prevValue,
                            changed,
                            location,
                            lastUpdated));
                    
                    // Additional contextual information
                    if (!data.getNearbyNpcIds().isEmpty() || !data.getNearbyObjectIds().isEmpty()) {
                        if (!data.getNearbyNpcIds().isEmpty()) {
                            sb.append(String.format("  └─ Nearby NPCs: %s\n", 
                                    data.getNearbyNpcIds().toString()));
                        }
                        if (!data.getNearbyObjectIds().isEmpty()) {
                            sb.append(String.format("  └─ Nearby Objects: %s\n", 
                                    data.getNearbyObjectIds().toString()));
                        }
                    }
                });
        
        sb.append("-".repeat(80)).append("\n");
        sb.append(String.format("Generated at: %s\n", formatter.format(Instant.now())));
        sb.append("=".repeat(80));
        
        return sb.toString();
    }
    
    /**
     * Returns a summary formatted string containing essential varbit cache information.
     * Compact view showing key metrics and recent changes.
     * 
     * @return Summary multi-line string representation of varbit cache
     */
    public static String printVarbitSummary() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        
        sb.append("┌─ VARBIT CACHE SUMMARY ").append("─".repeat(44)).append("┐\n");
        
        Rs2Cache<Integer, VarbitData> cache = getInstance();
        var stats = cache.getStatistics();
        
        // Summary statistics
        sb.append(String.format("│ Varbits Cached: %-3d │ Hits: %-6d │ Hit Rate: %5.1f%% │\n",
                cache.size(), stats.cacheHits, stats.getHitRate() * 100));
        
        // Count changed varbits
        long changedCount = cache.values().stream()
                .filter(data -> data != null && data.hasValueChanged())
                .count();
        
        sb.append(String.format("│ Changed Varbits: %-3d │ Total Changes: %-8d │\n",
                changedCount, stats.totalInvalidations));
        
        sb.append("├─ RECENT CHANGES ").append("─".repeat(46)).append("┤\n");
        
        // Show recent varbit changes
        cache.values().stream()
                .filter(data -> data != null && data.hasValueChanged())
                .sorted((data1, data2) -> Long.compare(data2.getLastUpdated(), data1.getLastUpdated()))
                .limit(5) // Show only last 5 changes
                .forEach(data -> {
                    String timeStr = formatter.format(Instant.ofEpochMilli(data.getLastUpdated()));
                    sb.append(String.format("│ Varbit changed: %d → %d (%s) %-18s │\n",
                            data.getPreviousValue() != null ? data.getPreviousValue() : 0,
                            data.getValue(),
                            timeStr, ""));
                });
        
        if (changedCount == 0) {
            sb.append("│ No recent varbit changes detected ").append(" ".repeat(28)).append("│\n");
        }
        
        sb.append("└").append("─".repeat(63)).append("┘");
        
        return sb.toString();
    }
    
    // ============================================
}
