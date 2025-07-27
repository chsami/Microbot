package net.runelite.client.plugins.microbot.util.cache;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.model.VarbitData;
import net.runelite.client.plugins.microbot.util.cache.serialization.CacheSerializable;
import net.runelite.client.plugins.microbot.util.cache.strategy.simple.VarPlayerUpdateStrategy;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Cache for varplayer (varp) values with enhanced tracking and contextual information.
 * Provides thread-safe access to varplayer data with temporal tracking, change history,
 * and contextual information about when and where values changed.
 * 
 * Reuses VarbitData model since varplayer and varbit data structures are identical.
 */
@Slf4j
public class Rs2VarPlayerCache extends Rs2Cache<Integer, VarbitData> implements CacheSerializable {
    
    private static volatile Rs2VarPlayerCache instance;
    
    /**
     * Private constructor for singleton pattern.
     */
    private Rs2VarPlayerCache() {
        super("VarPlayerCache", CacheMode.EVENT_DRIVEN_ONLY);
        
        // Set up update strategy to handle VarbitChanged events for varplayer values
        this.withUpdateStrategy(new VarPlayerUpdateStrategy())
            .withPersistence("varplayers");
        
        log.debug("Rs2VarPlayerCache initialized with EVENT_DRIVEN_ONLY mode");
    }
    
    /**
     * Gets the singleton instance of Rs2VarPlayerCache.
     * 
     * @return The singleton instance
     */
    public static Rs2VarPlayerCache getInstance() {
        if (instance == null) {
            synchronized (Rs2VarPlayerCache.class) {
                if (instance == null) {
                    instance = new Rs2VarPlayerCache();
                }
            }
        }
        return instance;
    }
    
    /**
     * Gets the cache instance for backward compatibility.
     * 
     * @return The singleton cache instance
     */
    public static Rs2Cache<Integer, VarbitData> getCache() {
        return getInstance();
    }
    
    /**
     * Loads varplayer data from the client for a specific varp ID.
     * 
     * @param varpId The varp ID to load data for
     * @return The VarbitData containing the current value
     */
    private static VarbitData loadVarPlayerDataFromClient(int varpId) {
        try {
            if (Microbot.getClient() == null) {
                log.warn("Client is null when loading varp {}", varpId);
                return new VarbitData(0);
            }
            
            int value = Microbot.getClientThread().runOnClientThreadOptional(() -> 
                Microbot.getClient().getVarpValue(varpId)).orElse(0);
            
            log.debug("Loaded varp from client: {} = {}", varpId, value);
            return new VarbitData(value);
        } catch (Exception e) {
            log.error("Error loading varp {}: {}", varpId, e.getMessage(), e);
            return new VarbitData(0);
        }
    }

    /**
     * Gets the current value of a varplayer.
     * If not cached, retrieves from client and caches the result.
     * 
     * @param varpId The varplayer ID
     * @return The current varplayer value, or 0 if not found
     */
    public static int getVarPlayerValue(int varpId) {
        try {
            VarbitData cached = getInstance().get(varpId);
            if (cached != null) {
                return cached.getValue();
            }
            
            // Not cached, get from client and cache it
            VarbitData freshData = loadVarPlayerDataFromClient(varpId);
            getInstance().put(varpId, freshData);
            return freshData.getValue();
            
        } catch (Exception e) {
            log.warn("Failed to get varplayer value for {}: {}", varpId, e.getMessage());
            return 0;
        }
    }
    
    /**
     * Gets varplayer data with full contextual information.
     * 
     * @param varpId The varplayer ID
     * @return Optional containing the VarbitData, or empty if not found
     */
    public static Optional<VarbitData> getVarPlayerData(int varpId) {
        return Optional.ofNullable(getInstance().get(varpId));
    }
    
    /**
     * Gets varplayers that have changed within the specified number of ticks.
     * 
     * @param ticks Number of ticks to look back
     * @return Stream of varplayer data that changed recently
     */
    public static Stream<Map.Entry<Integer, VarbitData>> getRecentlyChangedVarPlayers(int ticks) {
        long cutoffTime = System.currentTimeMillis() - (ticks * 600); // 600ms per tick
        return getInstance().entryStream()
            .filter(entry -> entry.getValue().getLastUpdated() >= cutoffTime)
            .filter(entry -> entry.getValue().hasValueChanged());
    }
    
    /**
     * Gets varplayers with a specific value.
     * 
     * @param value The value to search for
     * @return Stream of varplayer IDs that have the specified value
     */
    public static Stream<Integer> getVarPlayersWithValue(int value) {
        return getInstance().entryStream()
            .filter(entry -> entry.getValue().getValue() == value)
            .map(Map.Entry::getKey);
    }
    
    /**
     * Gets varplayers that changed at a specific location.
     * 
     * @param x World X coordinate
     * @param y World Y coordinate
     * @param plane The plane
     * @return Stream of varplayer data that changed at the specified location
     */
    public static Stream<Map.Entry<Integer, VarbitData>> getVarPlayersChangedAt(int x, int y, int plane) {
        return getInstance().entryStream()
            .filter(entry -> {
                VarbitData data = entry.getValue();
                return data.getPlayerLocation() != null &&
                       data.getPlayerLocation().getX() == x &&
                       data.getPlayerLocation().getY() == y &&
                       data.getPlayerLocation().getPlane() == plane;
            });
    }
    
    /**
     * Checks if a varplayer has a specific value.
     * 
     * @param varpId The varplayer ID
     * @param value The value to check
     * @return true if the varplayer has the specified value
     */
    public static boolean hasValue(int varpId, int value) {
        return getVarPlayerValue(varpId) == value;
    }
    
    /**
     * Checks if a varplayer has changed recently.
     * 
     * @param varpId The varplayer ID
     * @param ticks Number of ticks to look back
     * @return true if the varplayer changed within the specified time
     */
    public static boolean hasChangedRecently(int varpId, int ticks) {
        VarbitData data = getInstance().get(varpId);
        if (data == null) return false;
        
        long cutoffTime = System.currentTimeMillis() - (ticks * 600);
        return data.getLastUpdated() >= cutoffTime && data.hasValueChanged();
    }
    
    /**
     * Gets the previous value of a varplayer if available.
     * 
     * @param varpId The varplayer ID
     * @return The previous value, or null if not available
     */
    public static Integer getPreviousValue(int varpId) {
        VarbitData data = getInstance().get(varpId);
        return data != null ? data.getPreviousValue() : null;
    }
    
    /**
     * Updates all cached varplayers by retrieving fresh data from the game client.
     * This method iterates over currently cached varplayer IDs and refreshes their values.
     */
    public static void updateAllFromClient() {
        getInstance().update();
    }
    
    /**
     * Updates all cached data by retrieving fresh values from the game client.
     * Implements the abstract method from Rs2Cache.
     * 
     * Iterates over all currently cached varplayer keys and refreshes their values from the client.
     */
    @Override
    public void update() {
        log.debug("Updating all cached varplayers from client...");
        
        if (Microbot.getClient() == null) {
            log.warn("Cannot update varplayers - client is null");
            return;
        }
        
        int beforeSize = size();
        int updatedCount = 0;
        
        // Get all currently cached varplayer IDs (keys) and update them
        java.util.Set<Integer> cachedVarpIds = entryStream()
            .map(java.util.Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
        
        for (Integer varpId : cachedVarpIds) {
            try {
                // Refresh the data from client using the private method
                VarbitData freshData = loadVarPlayerDataFromClient(varpId);
                if (freshData != null) {
                    put(varpId, freshData);
                    updatedCount++;
                    log.debug("Updated varp {} with fresh value: {}", varpId, freshData.getValue());
                }
            } catch (Exception e) {
                log.warn("Failed to update varp {}: {}", varpId, e.getMessage());
            }
        }
        
        log.info("Updated {} varplayers from client (cache had {} entries total)", 
                updatedCount, beforeSize);
    }

    // CacheSerializable implementation
    @Override
    public String getConfigKey() {
        return "varPlayerCache";
    }
    
    @Override
    public boolean shouldPersist() {
        // Varplayer values can be persisted as they represent player state
        return true;
    }
    
    /**
     * Clears all cached varplayer data.
     * Useful for testing or when switching profiles.
     */
    public static void clearCache() {
        getInstance().invalidateAll();
        log.debug("VarPlayer cache cleared");
    }
    
    /**
     * Gets cache statistics.
     * 
     * @return String containing cache statistics
     */
    public static String getCacheStats() {
        Rs2VarPlayerCache cache = getInstance();
        return String.format("VarPlayerCache - Size: %d", cache.size());
    }
    
    // ============================================
    // EventBus Integration
    // ============================================
    
    /**
     * Handles VarbitChanged events specifically for varplayer (varp) changes.
     * Filters out varbit events and only processes varplayer events.
     */
    @Subscribe
    public void onVarbitChanged(VarbitChanged event) {
        try {         
            // This is a varplayer event, handle it
            getInstance().handleEvent(event);            
            // Ignore varbit events (handled by Rs2VarbitCache)
        } catch (Exception e) {
            log.error("Error handling VarbitChanged event for varplayer: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Handles GameStateChanged events for cache management.
     */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        try {
            switch (event.getGameState()) {
                case LOGGED_IN:
                case HOPPING:
                case LOGIN_SCREEN:
                case CONNECTION_LOST:
                    // Let the strategy handle cache invalidation if needed
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling GameStateChanged event: {}", e.getMessage(), e);
        }
    }
}
