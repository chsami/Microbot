package net.runelite.client.plugins.microbot.util.cache;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.util.cache.strategy.entity.GroundItemUpdateStrategy;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItemModel;

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
    
    /**
     * Private constructor for singleton pattern.
     */
    private Rs2GroundItemCache() {
        super("GroundItemCache", CacheMode.EVENT_DRIVEN_ONLY);
        this.withUpdateStrategy(new GroundItemUpdateStrategy());
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
        return getGroundItemsById(itemId)
                .min((a, b) -> Integer.compare(a.getDistanceFromPlayer(), b.getDistanceFromPlayer()));
    }
    
    /**
     * Gets the closest ground item to the player with the specified name.
     * 
     * @param name The item name to search for
     * @return Optional containing the closest ground item
     */
    public static Optional<Rs2GroundItemModel> getClosestGroundItemByName(String name) {
        return getGroundItemsByName(name)
                .min((a, b) -> Integer.compare(a.getDistanceFromPlayer(), b.getDistanceFromPlayer()));
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
    
        
    @Subscribe
    public void onItemSpawned(ItemSpawned event) {
        getInstance().handleEvent(event);
    }
    
    @Subscribe
    public void onItemDespawned(ItemDespawned event) {
        getInstance().handleEvent(event);
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
        return getItemsByGameId(itemId)
                .min((a, b) -> Integer.compare(a.getDistanceFromPlayer(), b.getDistanceFromPlayer()));
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
        return getGroundItemsByName(itemName)
                .min((a, b) -> Integer.compare(a.getDistanceFromPlayer(), b.getDistanceFromPlayer()));
    }
    @Override
    public void update() {
        // This method can be used to trigger a manual update if needed
        // For example, to refresh the cache after a game state change
        log.debug("Updating ground item cache");
        // we can implement logic for like a scene scane  we do in Rs2GroundItems ?  an or like we do  in the object cache ?
    }
}
