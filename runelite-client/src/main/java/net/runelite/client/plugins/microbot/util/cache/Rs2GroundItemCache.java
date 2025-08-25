package net.runelite.client.plugins.microbot.util.cache;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Constants;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.strategy.entity.GroundItemUpdateStrategy;
import net.runelite.client.plugins.microbot.util.cache.util.LogOutputMode;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2CacheLoggingUtils;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItemModel;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Thread-safe cache for tracking ground items using the unified cache architecture.
 * Returns Rs2GroundItemModel objects for enhanced item handling.
 * Uses EVENT_DRIVEN_ONLY mode to persist items until despawn or game state changes.
 * 
 * This class extends Rs2UnifiedCache and provides specific ground item caching functionality
 * with proper EventBus integration for @Subscribe methods.
 */
@Slf4j
public class Rs2GroundItemCache extends Rs2Cache<String, Rs2GroundItemModel> {
    
    private static Rs2GroundItemCache instance;
    
    // Reference to the update strategy for scene scanning
    private GroundItemUpdateStrategy updateStrategy;
    
    /**
     * Private constructor for singleton pattern.
     */
    private Rs2GroundItemCache() {
        super("GroundItemCache", CacheMode.EVENT_DRIVEN_ONLY);
        this.updateStrategy = new GroundItemUpdateStrategy();
        this.withUpdateStrategy(this.updateStrategy);
    }
    
    /**
     * Gets the singleton instance of Rs2GroundItemCache.
     * 
     * @return The singleton ground item cache instance
     */
    public static synchronized Rs2GroundItemCache getInstance() {
        if (instance == null) {
            instance = new Rs2GroundItemCache();
        }
        return instance;
    }
    
    /**
     * Requests a scene scan to be performed when appropriate.
     * This is more efficient than immediate scanning.
     */
    public static void requestSceneScan() {
        getInstance().updateStrategy.requestSceneScan(getInstance());
    }
    
    /**
     * Starts periodic scene scanning to keep the cache fresh.
     * This is useful for long-running scripts that need up-to-date ground item data.
     * 
     * @param intervalSeconds How often to scan the scene in seconds
     */
    public static void startPeriodicSceneScan(long intervalSeconds) {
        getInstance().updateStrategy.schedulePeriodicSceneScan(getInstance(), intervalSeconds);
    }
    
    /**
     * Stops periodic scene scanning.
     */
    public static void stopPeriodicSceneScan() {
        getInstance().updateStrategy.stopPeriodicSceneScan();
    }
    
    /**
     * Overrides the get method to provide fallback scene scanning when cache is empty or key not found.
     * This ensures that even if events are missed, we can still retrieve ground items from the scene.
     * 
     * @param key The unique String key for the ground item
     * @return The ground item model if found in cache or scene, null otherwise
     */
    @Override
    public Rs2GroundItemModel get(String key) {
        // First try the regular cache lookup
        Rs2GroundItemModel cachedResult = super.get(key);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
            log.warn("Client or local player is null, cannot perform scene scan");
            return null;
        }
        
        // If not in cache and cache is very small, request and perform scene scan
        if (updateStrategy.requestSceneScan(this)) {
            log.debug("Cache miss for ground item key '{}' (size: {}), performing scene scan", key, this.size());            
            // Try again after scene scan
            return super.get(key);
        }else {
            log.debug("Cache miss for ground item key '{}' but scene scan not successful (size: {})", key, this.size());
        }
        
        return null;
    }
    
    /**
     * Gets a ground item by its unique key.
     * 
     * @param key The unique key for the ground item
     * @return Optional containing the ground item model if found
     */
    public static Optional<Rs2GroundItemModel> getGroundItemByKey(String key) {
        return Optional.ofNullable(getInstance().get(key));
    }
    
    /**
     * Gets all ground items matching a specific item ID.
     * 
     * @param itemId The item ID to search for
     * @return Stream of matching Rs2GroundItemModel objects
     */
    public static Stream<Rs2GroundItemModel> getGroundItemsById(int itemId) {
        return getInstance().stream()
                .filter(item -> item.getId() == itemId);
    }
    
    /**
     * Gets all ground items matching a specific name (case-insensitive).
     * 
     * @param name The item name to search for
     * @return Stream of matching Rs2GroundItemModel objects
     */
    public static Stream<Rs2GroundItemModel> getGroundItemsByName(String name) {
        return getInstance().stream()
                .filter(item -> item.getName() != null && 
                               item.getName().toLowerCase().contains(name.toLowerCase()));
    }
    
    /**
     * Gets all ground items within a certain distance from a location.
     * 
     * @param location The center location
     * @param maxDistance The maximum distance in tiles
     * @return Stream of ground items within the specified distance
     */
    public static Stream<Rs2GroundItemModel> getGroundItemsWithinDistance(WorldPoint location, int maxDistance) {
        return getInstance().stream()
                .filter(item -> item.getLocation() != null &&
                               item.getLocation().distanceTo(location) <= maxDistance);
    }
    
    /**
     * Gets the first ground item matching the specified ID.
     * 
     * @param itemId The item ID
     * @return Optional containing the first matching ground item model
     */
    public static Optional<Rs2GroundItemModel> getFirstGroundItemById(int itemId) {
        return getGroundItemsById(itemId).findFirst();
    }
    
    /**
     * Gets the first ground item matching the specified name.
     * 
     * @param name The item name
     * @return Optional containing the first matching ground item model
     */
    public static Optional<Rs2GroundItemModel> getFirstGroundItemByName(String name) {
        return getGroundItemsByName(name).findFirst();
    }
    
    /**
     * Gets all cached ground items as Rs2GroundItemModel objects.
     * 
     * @return Stream of all cached ground items
     */
    public static Stream<Rs2GroundItemModel> getAllGroundItems() {
        return getInstance().stream();
    }
    
    /**
     * Gets ground items with a specific quantity.
     * 
     * @param quantity The quantity to search for
     * @return Stream of matching ground items
     */
    public static Stream<Rs2GroundItemModel> getGroundItemsByQuantity(int quantity) {
        return getAllGroundItems()
                .filter(item -> item.getQuantity() == quantity);
    }
    
    /**
     * Gets ground items within a specific value range.
     * 
     * @param minValue The minimum value (inclusive)
     * @param maxValue The maximum value (inclusive)
     * @return Stream of matching ground items
     */
    public static Stream<Rs2GroundItemModel> getGroundItemsByValueRange(int minValue, int maxValue) {
        return getAllGroundItems()
                .filter(item -> {
                    int totalValue = item.getHaPrice() * item.getQuantity();
                    return totalValue >= minValue && totalValue <= maxValue;
                });
    }
    
    /**
     * Gets the closest ground item to the player with the specified ID.
     * 
     * @param itemId The item ID to search for
     * @return Optional containing the closest ground item
     */
    public static Optional<Rs2GroundItemModel> getClosestGroundItemById(int itemId) {
        WorldPoint playerLocation = null;
        try {
            if (Microbot.getClient() != null && Microbot.getClient().getLocalPlayer() != null) {
                playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
            }
        } catch (Exception e) {
            log.debug("Could not get player location for distance calculation: {}", e.getMessage());
        }
        
        if (playerLocation == null) {
            return getGroundItemsById(itemId).findFirst();
        }
        
        final WorldPoint finalPlayerLocation = playerLocation;
        return getGroundItemsById(itemId)
                .min((a, b) -> {
                    try {
                        int distA = a.getLocation() != null ? a.getLocation().distanceTo(finalPlayerLocation) : Integer.MAX_VALUE;
                        int distB = b.getLocation() != null ? b.getLocation().distanceTo(finalPlayerLocation) : Integer.MAX_VALUE;
                        return Integer.compare(distA, distB);
                    } catch (Exception e) {
                        return 0;
                    }
                });
    }
    
    /**
     * Gets the closest ground item to the player with the specified name.
     * 
     * @param name The item name to search for
     * @return Optional containing the closest ground item
     */
    public static Optional<Rs2GroundItemModel> getClosestGroundItemByName(String name) {
        WorldPoint playerLocation = null;
        try {
            if (Microbot.getClient() != null && Microbot.getClient().getLocalPlayer() != null) {
                playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
            }
        } catch (Exception e) {
            log.debug("Could not get player location for distance calculation: {}", e.getMessage());
        }
        
        if (playerLocation == null) {
            return getGroundItemsByName(name).findFirst();
        }
        
        final WorldPoint finalPlayerLocation = playerLocation;
        return getGroundItemsByName(name)
                .min((a, b) -> {
                    try {
                        int distA = a.getLocation() != null ? a.getLocation().distanceTo(finalPlayerLocation) : Integer.MAX_VALUE;
                        int distB = b.getLocation() != null ? b.getLocation().distanceTo(finalPlayerLocation) : Integer.MAX_VALUE;
                        return Integer.compare(distA, distB);
                    } catch (Exception e) {
                        return 0;
                    }
                });
    }
    
    /**
     * Gets the closest ground item to a specific anchor point with the specified ID.
     * 
     * @param itemId The item ID to search for
     * @param anchorPoint The anchor point to calculate distance from
     * @return Optional containing the closest ground item
     */
    public static Optional<Rs2GroundItemModel> getClosestGroundItemById(int itemId, WorldPoint anchorPoint) {
        return getGroundItemsById(itemId)
                .min((a, b) -> Integer.compare(
                    a.getLocation().distanceTo(anchorPoint), 
                    b.getLocation().distanceTo(anchorPoint)
                ));
    }
    
    /**
     * Gets ground items sorted by value (highest first).
     * 
     * @return List of ground items sorted by total value descending
     */
    public static List<Rs2GroundItemModel> getGroundItemsSortedByValue() {
        return getAllGroundItems()
                .sorted((a, b) -> Integer.compare(
                    b.getHaPrice() * b.getQuantity(), 
                    a.getHaPrice() * a.getQuantity()
                ))
                .collect(Collectors.toList());
    }
    
    /**
     * Gets valuable ground items above a certain threshold.
     * 
     * @param minValue The minimum total value threshold
     * @return Stream of valuable ground items
     */
    public static Stream<Rs2GroundItemModel> getValuableGroundItems(int minValue) {
        return getAllGroundItems()
                .filter(item -> (item.getHaPrice() * item.getQuantity()) >= minValue);
    }
    
    /**
     * Gets the total number of cached ground items.
     * 
     * @return The total ground item count
     */
    public static int getGroundItemCount() {
        return getInstance().size();
    }
    
    /**
     * Gets the total number of ground items by ID.
     * 
     * @param itemId The item ID to count
     * @return The count of ground items with the specified ID
     */
    public static long getGroundItemCountById(int itemId) {
        return getGroundItemsById(itemId).count();
    }
    
    /**
     * Manually adds a ground item to the cache.
     * 
     * @param tileItem The tile item to add
     * @param tile The tile containing the item
     */
    public static void addGroundItem(TileItem tileItem, net.runelite.api.Tile tile) {
        if (tileItem != null && tile != null) {
            String key = generateKey(tileItem, tile.getWorldLocation());
            Rs2GroundItemModel groundItem = new Rs2GroundItemModel(tileItem, tile);
            getInstance().put(key, groundItem);
            log.debug("Manually added ground item: {} at {}", tileItem.getId(), tile.getWorldLocation());
        }
    }
    
    /**
     * Manually removes a ground item from the cache.
     * 
     * @param key The ground item key to remove
     */
    public static void removeGroundItem(String key) {
        getInstance().remove(key);
        log.debug("Manually removed ground item with key: {}", key);
    }
    
    /**
     * Invalidates all ground item cache entries.
     */
    public static void invalidateAllGroundItems() {
        getInstance().invalidateAll();
        log.debug("Invalidated all ground item cache entries");
    }
    
    /**
   
    
    /**
     * Generates a unique key for ground items based on item ID, quantity, and location.
     * 
     * @param item The tile item
     * @param location The world location
     * @return Unique key string
     */
    public static String generateKey(TileItem item, WorldPoint location) {
        return String.format("%d_%d_%d_%d_%d", 
                item.getId(), 
                item.getQuantity(), 
                location.getX(), 
                location.getY(), 
                location.getPlane());
    }
    
    /**
     * Event handler registration for the unified cache.
     * The unified cache handles events through its strategy automatically.
     */
    
        
    @Subscribe(priority = 10) 
    public void onItemSpawned(ItemSpawned event) {
        getInstance().handleEvent(event);
    }
    
    @Subscribe(priority = 20) // Ensure despawn events are handled first
    public void onItemDespawned(ItemDespawned event) {
        getInstance().handleEvent(event);
    }

    @Subscribe(priority = 40)
    public void onGameStateChanged(final GameStateChanged event) {
        // Removed old region detection - now handled by unified Rs2Cache system
        // Also let the strategy handle the event
        getInstance().handleEvent(event);
    }

    
    /**
     * Resets the singleton instance. Used for testing.
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.invalidateAll();
            
            instance = null;
        }
    }
    
    // ============================================
    // Legacy API Compatibility Methods
    // ============================================
    
    /**
     * Gets ground items by their game ID - Legacy compatibility method.
     * 
     * @param itemId The item ID
     * @return Stream of matching ground items
     */
    public static Stream<Rs2GroundItemModel> getItemsByGameId(int itemId) {
        return getInstance().stream()
                .filter(item -> item.getId() == itemId);
    }
    
    /**
     * Gets first ground item by game ID - Legacy compatibility method.
     * 
     * @param itemId The item ID
     * @return Optional containing the first matching ground item
     */
    public static Optional<Rs2GroundItemModel> getFirstItemByGameId(int itemId) {
        return getItemsByGameId(itemId).findFirst();
    }
    
    /**
     * Gets closest ground item by game ID - Legacy compatibility method.
     * 
     * @param itemId The item ID
     * @return Optional containing the closest ground item
     */
    public static Optional<Rs2GroundItemModel> getClosestItemByGameId(int itemId) {
        WorldPoint playerLocation = null;
        try {
            if (Microbot.getClient() != null && Microbot.getClient().getLocalPlayer() != null) {
                playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
            }
        } catch (Exception e) {
            log.debug("Could not get player location for distance calculation: {}", e.getMessage());
        }
        
        if (playerLocation == null) {
            return getItemsByGameId(itemId).findFirst();
        }
        
        final WorldPoint finalPlayerLocation = playerLocation;
        return getItemsByGameId(itemId)
                .min((a, b) -> {
                    try {
                        int distA = a.getLocation() != null ? a.getLocation().distanceTo(finalPlayerLocation) : Integer.MAX_VALUE;
                        int distB = b.getLocation() != null ? b.getLocation().distanceTo(finalPlayerLocation) : Integer.MAX_VALUE;
                        return Integer.compare(distA, distB);
                    } catch (Exception e) {
                        return 0;
                    }
                });
    }
    
    /**
     * Gets all ground items - Legacy compatibility method.
     * 
     * @return Stream of all ground items
     */
    public static Stream<Rs2GroundItemModel> getAllItems() {
        return getInstance().stream();
    }
    
    /**
     * Gets item count - Legacy compatibility method.
     * 
     * @return Total number of cached ground items
     */
    public static int getItemCount() {
        return getInstance().size();
    }
    
    /**
     * Gets cache mode - Legacy compatibility method.
     * 
     * @return The cache mode
     */
    public static CacheMode getGroundItemCacheMode() {
        return getInstance().getCacheMode();
    }
    
    /**
     * Gets cache statistics - Legacy compatibility method.
     * 
     * @return Statistics string for debugging
     */
    public static String getGroundItemCacheStatistics() {
        return getInstance().getStatisticsString();
    }
    
    /**
     * Gets closest ground item by name - Legacy compatibility method.
     * 
     * @param itemName The item name to search for
     * @return Optional containing the closest ground item
     */
    public static Optional<Rs2GroundItemModel> getClosestItemByName(String itemName) {
        WorldPoint playerLocation = null;
        try {
            if (Microbot.getClient() != null && Microbot.getClient().getLocalPlayer() != null) {
                playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
            }
        } catch (Exception e) {
            log.debug("Could not get player location for distance calculation: {}", e.getMessage());
        }
        
        if (playerLocation == null) {
            return getGroundItemsByName(itemName).findFirst();
        }
        
        final WorldPoint finalPlayerLocation = playerLocation;
        return getGroundItemsByName(itemName)
                .min((a, b) -> {
                    try {
                        int distA = a.getLocation() != null ? a.getLocation().distanceTo(finalPlayerLocation) : Integer.MAX_VALUE;
                        int distB = b.getLocation() != null ? b.getLocation().distanceTo(finalPlayerLocation) : Integer.MAX_VALUE;
                        return Integer.compare(distA, distB);
                    } catch (Exception e) {
                        return 0;
                    }
                });
    }
    @Override
    public void update(){
         update(Constants.CLIENT_TICK_LENGTH*2);
    }
    
    public void update(long delay) {
        log.debug("Starting ground item cache update - clearing cache and performing scene scan, delay: {}ms", delay);
        int sizeBefore = this.size();
        
        // Clear the entire cache
        this.invalidateAll();
        
        // Perform a complete scene scan to repopulate the cache
        updateStrategy.performSceneScan(this, delay);
        
        int sizeAfter = this.size();
        log.debug("Ground item cache update completed - items before: {}, after: {}", sizeBefore, sizeAfter);
    }
    
    /**
     * Logs the current state of all cached ground items for debugging.
     * 
     * @param dumpToFile Whether to also dump the information to a file
     */
    public static void logState(LogOutputMode mode) {
        var cache = getInstance();
        var stats = cache.getStatistics();
        
        // Create the log content
        StringBuilder logContent = new StringBuilder();
        
        String header = String.format("=== Ground Item Cache State (%d entries) ===", cache.size());
        logContent.append(header).append("\n");
        
        String statsInfo = Rs2CacheLoggingUtils.formatCacheStatistics(
            stats.getHitRate(), stats.cacheHits, stats.cacheMisses, stats.cacheMode.toString());
        logContent.append(statsInfo).append("\n\n");
        
        if (cache.size() == 0) {
            String emptyMsg = "Cache is empty";            
            logContent.append(emptyMsg).append("\n");
        } else {
            // Table format for ground items with enhanced timing information
            String[] headers = {"Name", "Quantity", "ID", "Location", "Distance", "GE Price", "HA Price", "Owned", "Spawn Time UTC", "Despawn Time UTC", "Should Despawn?", "Ticks Left", "Cache Timestamp"};
            int[] columnWidths = {20, 8, 8, 18, 8, 10, 10, 6, 22, 22, 14, 10, 22};
            
            String tableHeader = Rs2CacheLoggingUtils.formatTableHeader(headers, columnWidths);
            logContent.append("\n").append(tableHeader);
            
            // Get player location once for distance calculations
            WorldPoint playerLocation = null;
            try {
                if (Microbot.getClient() != null && Microbot.getClient().getLocalPlayer() != null) {
                    playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
                }
            } catch (Exception e) {
                log.debug("Could not get player location for distance calculations: {}", e.getMessage());
            }
            
            final WorldPoint finalPlayerLocation = playerLocation;
            
            // Convert to list and sort by total value (highest first)
            List<Rs2GroundItemModel> items = cache.stream()
                .limit(50) // Limit early to avoid processing too many items
                .collect(Collectors.toList());
            
            // Sort by total value with safe calculation
            items.sort((a, b) -> {
                try {
                    int valueA = a.getTotalGeValue();
                    int valueB = b.getTotalGeValue();
                    return Integer.compare(valueB, valueA); // Highest first
                } catch (Exception e) {
                    return 0; // If value calculation fails, consider them equal
                }
            });
            int maxRows = mode == LogOutputMode.CONSOLE_ONLY ? 50 : cache.size();
            // Process each item safely
            for (Rs2GroundItemModel item : items) {
                try {
                    // Calculate distance safely
                    String distanceStr = "N/A";
                    if (finalPlayerLocation != null && item.getLocation() != null) {
                        try {
                            int distance = item.getLocation().distanceTo(finalPlayerLocation);
                            distanceStr = String.valueOf(distance);
                        } catch (Exception e) {
                            distanceStr = "Error";
                        }
                    }
                    
                    // Get values safely
                    String geValueStr = "N/A";
                    String haValueStr = "N/A";
                    try {
                        geValueStr = String.valueOf(item.getTotalGeValue());
                        haValueStr = String.valueOf(item.getTotalHaValue());
                    } catch (Exception e) {
                        log.debug("Error getting item values: {}", e.getMessage());
                    }
                    
                    // Get cache timestamp for this ground item
                    String cacheTimestampStr = "N/A";
                    try {
                        // Generate key manually using the same format as generateKey method
                        String itemKey = String.format("%d_%d_%d_%d_%d", 
                            item.getId(), 
                            item.getQuantity(), 
                            item.getLocation().getX(), 
                            item.getLocation().getY(), 
                            item.getLocation().getPlane());
                        Long cacheTimestamp = cache.getCacheTimestamp(itemKey);
                        if (cacheTimestamp != null) {
                            cacheTimestampStr = Rs2Cache.formatUtcTimestamp(cacheTimestamp);
                        }
                    } catch (Exception e) {
                        log.debug("Error getting cache timestamp: {}", e.getMessage());
                    }
                    
                    // Get despawn information safely with enhanced UTC timing
                    String despawnTimeStr = "N/A";
                    String shouldDespawnStr = "No";
                    String spawnTimeStr = "N/A";
                    String ticksLeftStr = "N/A";
                    try {
                        // Get spawn time in UTC
                        if (item.getSpawnTimeUtc() != null) {
                            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")
                                .withZone(ZoneOffset.UTC);
                            spawnTimeStr = formatter.format(item.getSpawnTimeUtc()) + " UTC";
                        }
                        
                        // Get despawn time in UTC
                        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd/MM HH:mm:ss")
                            .withZone(ZoneOffset.UTC);
                        despawnTimeStr = formatter.format(item.getDespawnTime().atZone(ZoneOffset.UTC)) + " UTC";
                        
                        // Use tick-based despawn detection for accuracy
                        shouldDespawnStr = item.isDespawned() ? "Yes" : "No";
                        ticksLeftStr = String.valueOf(item.getTicksUntilDespawn());
                        
                    } catch (Exception e) {
                        log.debug("Error getting despawn information: {}", e.getMessage());
                    }
                    
                    String[] values = {
                        Rs2CacheLoggingUtils.truncate(item.getName() != null ? item.getName() : "Unknown", 19),
                        String.valueOf(item.getQuantity()),
                        String.valueOf(item.getId()),
                        Rs2CacheLoggingUtils.formatLocation(item.getLocation()),
                        distanceStr,
                        geValueStr,
                        haValueStr,
                        item.isOwned() ? "Yes" : "No",
                        Rs2CacheLoggingUtils.truncate(spawnTimeStr, 21),
                        Rs2CacheLoggingUtils.truncate(despawnTimeStr, 21),
                        shouldDespawnStr,
                        ticksLeftStr,
                        Rs2CacheLoggingUtils.truncate(cacheTimestampStr, 21)
                    };
                    
                    String row = Rs2CacheLoggingUtils.formatTableRow(values, columnWidths);
                    logContent.append(row);
                } catch (Exception e) {
                    log.debug("Error processing ground item for logging: {}", e.getMessage());
                    // Skip this item and continue with the next one
                }
            }
            
            String tableFooter = Rs2CacheLoggingUtils.formatTableFooter(columnWidths);            
            logContent.append(tableFooter);
            
            String limitMsg = Rs2CacheLoggingUtils.formatLimitMessage(cache.size(), maxRows);
            if (!limitMsg.isEmpty()) {                
                logContent.append(limitMsg).append("\n");
            }
        }
        
        String footer = "=== End Ground Item Cache State ===";
        logContent.append(footer).append("\n");
        
        // Dump to file if requested
        Rs2CacheLoggingUtils.outputCacheLog(getInstance().getCacheName(), logContent.toString(), mode);         
        
    }
    
    /**
     * Override periodic cleanup to check for despawned ground items.
     * This method is called by the ScheduledExecutorService in the base cache
     * to remove items that have naturally despawned based on their game timer.
     */
    @Override
    protected void performPeriodicCleanup() {
        updateStrategy.performSceneScan(instance, Constants.CLIENT_TICK_LENGTH /2);        
    }
    
    /**
     * Override isExpired to use ground item despawn timing instead of generic TTL.
     * This integrates the despawn logic directly with the cache's expiration system.
     * 
     * @param key The cache key to check for expiration
     * @return true if the ground item should be considered expired (despawned)
     */
    @Override
    protected boolean isExpired(String key) {
        // For EVENT_DRIVEN_ONLY mode with ground items, check despawn status directly
        if (getCacheMode() == CacheMode.EVENT_DRIVEN_ONLY) {
            // Access the cached value directly using the protected method to avoid recursion
            Rs2GroundItemModel groundItem = getRawCachedValue(key);
            if (groundItem != null && groundItem.isDespawned()) {
                // Item has despawned - remove it immediately from cache
                remove(key);
                log.debug("Removed despawned ground item during expiration check: {} (ID: {}) at {}", 
                    groundItem.getName(), groundItem.getId(), groundItem.getLocation());
                return true;
            }
            // If item is not in cache, consider it expired
            if (groundItem == null) {
                return true;
            }
            // Item exists and is not despawned
            return false;
        }
        
        // For other modes, fall back to the default TTL behavior
        return super.isExpired(key);
    }
    
   
}
