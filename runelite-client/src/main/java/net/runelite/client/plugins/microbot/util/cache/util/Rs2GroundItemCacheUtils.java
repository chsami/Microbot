package net.runelite.client.plugins.microbot.util.cache.util;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.Rs2GroundItemCache;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItemModel;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Advanced cache-based utilities for ground items.
 * Provides scene-independent methods for finding and filtering ground items.
 * 
 * This class offers high-performance ground item operations using cached data,
 * avoiding the need to iterate through scene tiles.
 * 
 * @author Vox
 * @version 1.0
 */
public class Rs2GroundItemCacheUtils {

    // ============================================
    // Core Cache Access Methods
    // ============================================

    /**
     * Gets ground items by their game ID.
     * 
     * @param itemId The item ID
     * @return Stream of matching ground items
     */
    public static Stream<Rs2GroundItemModel> getByGameId(int itemId) {
        try {
            return Rs2GroundItemCache.getItemsByGameId(itemId);
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    /**
     * Gets the first ground item matching the criteria.
     * 
     * @param itemId The item ID
     * @return Optional containing the first matching ground item
     */
    public static Optional<Rs2GroundItemModel> getFirst(int itemId) {
        try {
            return Rs2GroundItemCache.getFirstItemByGameId(itemId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Gets the closest ground item to the player.
     * 
     * @param itemId The item ID
     * @return Optional containing the closest ground item
     */
    public static Optional<Rs2GroundItemModel> getClosest(int itemId) {
        try {
            return Rs2GroundItemCache.getClosestItemByGameId(itemId);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Gets all cached ground items.
     * 
     * @return Stream of all ground items
     */
    public static Stream<Rs2GroundItemModel> getAll() {
        try {
            return Rs2GroundItemCache.getAllItems();
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    // ============================================
    // Advanced Finding Methods
    // ============================================

    /**
     * Finds the first ground item matching a predicate.
     * 
     * @param predicate The predicate to match
     * @return Optional containing the first matching ground item
     */
    public static Optional<Rs2GroundItemModel> find(Predicate<Rs2GroundItemModel> predicate) {
        try {
            return Rs2GroundItemCache.getAllItems()
                    .filter(predicate)
                    .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Finds all ground items matching a predicate.
     * 
     * @param predicate The predicate to match
     * @return Stream of matching ground items
     */
    public static Stream<Rs2GroundItemModel> findAll(Predicate<Rs2GroundItemModel> predicate) {
        try {
            return Rs2GroundItemCache.getAllItems().filter(predicate);
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    /**
     * Finds the closest ground item matching a predicate.
     * 
     * @param predicate The predicate to match
     * @return Optional containing the closest matching ground item
     */
    public static Optional<Rs2GroundItemModel> findClosest(Predicate<Rs2GroundItemModel> predicate) {
        try {
            return Rs2GroundItemCache.getAllItems()
                    .filter(predicate)
                    .min(Comparator.comparingInt(Rs2GroundItemModel::getDistanceFromPlayer));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ============================================
    // Distance-Based Finding Methods
    // ============================================

    /**
     * Finds the first ground item within distance from player by ID.
     * 
     * @param itemId The item ID
     * @param distance Maximum distance in tiles
     * @return Optional containing the first matching ground item within distance
     */
    public static Optional<Rs2GroundItemModel> findWithinDistance(int itemId, int distance) {
        return find(item -> item.getId() == itemId && item.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds all ground items within distance from player by ID.
     * 
     * @param itemId The item ID
     * @param distance Maximum distance in tiles
     * @return Stream of matching ground items within distance
     */
    public static Stream<Rs2GroundItemModel> findAllWithinDistance(int itemId, int distance) {
        return findAll(item -> item.getId() == itemId && item.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds the closest ground item by ID within distance from player.
     * 
     * @param itemId The item ID
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest matching ground item within distance
     */
    public static Optional<Rs2GroundItemModel> findClosestWithinDistance(int itemId, int distance) {
        return findClosest(item -> item.getId() == itemId && item.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds ground items within distance from an anchor point.
     * 
     * @param predicate The predicate to match
     * @param anchor The anchor point
     * @param distance Maximum distance in tiles
     * @return Stream of matching ground items within distance from anchor
     */
    public static Stream<Rs2GroundItemModel> findWithinDistance(Predicate<Rs2GroundItemModel> predicate, WorldPoint anchor, int distance) {
        return findAll(item -> predicate.test(item) && item.getLocation().distanceTo(anchor) <= distance);
    }

    /**
     * Finds the closest ground item to an anchor point.
     * 
     * @param predicate The predicate to match
     * @param anchor The anchor point
     * @return Optional containing the closest matching ground item to anchor
     */
    public static Optional<Rs2GroundItemModel> findClosest(Predicate<Rs2GroundItemModel> predicate, WorldPoint anchor) {
        return findAll(predicate)
                .min(Comparator.comparingInt(item -> item.getLocation().distanceTo(anchor)));
    }

    /**
     * Finds the closest ground item to an anchor point within distance.
     * 
     * @param predicate The predicate to match
     * @param anchor The anchor point
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest matching ground item to anchor within distance
     */
    public static Optional<Rs2GroundItemModel> findClosest(Predicate<Rs2GroundItemModel> predicate, WorldPoint anchor, int distance) {
        return findWithinDistance(predicate, anchor, distance)
                .min(Comparator.comparingInt(item -> item.getLocation().distanceTo(anchor)));
    }

    // ============================================
    // Name-Based Finding Methods
    // ============================================

    /**
     * Creates a predicate that matches ground items whose name contains the given string (case-insensitive).
     * 
     * @param itemName The name to match (partial or full)
     * @param exact Whether to match exactly or contain
     * @return Predicate for name matching
     */
    public static Predicate<Rs2GroundItemModel> nameMatches(String itemName, boolean exact) {
        String lower = itemName.toLowerCase();
        return item -> {
            String name = item.getName();
            if (name == null) return false;
            return exact ? name.equalsIgnoreCase(itemName) : name.toLowerCase().contains(lower);
        };
    }

    /**
     * Creates a predicate that matches ground items whose name contains the given string (case-insensitive).
     * 
     * @param itemName The name to match (partial)
     * @return Predicate for name matching
     */
    public static Predicate<Rs2GroundItemModel> nameMatches(String itemName) {
        return nameMatches(itemName, false);
    }

    /**
     * Finds the first ground item by name.
     * 
     * @param itemName The item name
     * @param exact Whether to match exactly or contain
     * @return Optional containing the first matching ground item
     */
    public static Optional<Rs2GroundItemModel> findByName(String itemName, boolean exact) {
        return find(nameMatches(itemName, exact));
    }

    /**
     * Finds the first ground item by name (partial match).
     * 
     * @param itemName The item name
     * @return Optional containing the first matching ground item
     */
    public static Optional<Rs2GroundItemModel> findByName(String itemName) {
        return findByName(itemName, false);
    }

    /**
     * Finds the closest ground item by name.
     * 
     * @param itemName The item name
     * @param exact Whether to match exactly or contain
     * @return Optional containing the closest matching ground item
     */
    public static Optional<Rs2GroundItemModel> findClosestByName(String itemName, boolean exact) {
        return findClosest(nameMatches(itemName, exact));
    }

    /**
     * Finds the closest ground item by name (partial match).
     * 
     * @param itemName The item name
     * @return Optional containing the closest matching ground item
     */
    public static Optional<Rs2GroundItemModel> findClosestByName(String itemName) {
        return findClosestByName(itemName, false);
    }

    /**
     * Finds ground items by name within distance from player.
     * 
     * @param itemName The item name
     * @param exact Whether to match exactly or contain
     * @param distance Maximum distance in tiles
     * @return Stream of matching ground items within distance
     */
    public static Stream<Rs2GroundItemModel> findByNameWithinDistance(String itemName, boolean exact, int distance) {
        return findAll(item -> nameMatches(itemName, exact).test(item) && item.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds ground items by name within distance from player (partial match).
     * 
     * @param itemName The item name
     * @param distance Maximum distance in tiles
     * @return Stream of matching ground items within distance
     */
    public static Stream<Rs2GroundItemModel> findByNameWithinDistance(String itemName, int distance) {
        return findByNameWithinDistance(itemName, false, distance);
    }

    /**
     * Finds the closest ground item by name within distance from player.
     * 
     * @param itemName The item name
     * @param exact Whether to match exactly or contain
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest matching ground item within distance
     */
    public static Optional<Rs2GroundItemModel> findClosestByNameWithinDistance(String itemName, boolean exact, int distance) {
        return findClosest(item -> nameMatches(itemName, exact).test(item) && item.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds the closest ground item by name within distance from player (partial match).
     * 
     * @param itemName The item name
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest matching ground item within distance
     */
    public static Optional<Rs2GroundItemModel> findClosestByNameWithinDistance(String itemName, int distance) {
        return findClosestByNameWithinDistance(itemName, false, distance);
    }

    // ============================================
    // Array-Based ID Methods
    // ============================================

    /**
     * Finds the first ground item matching any of the given IDs.
     * 
     * @param itemIds Array of item IDs
     * @return Optional containing the first matching ground item
     */
    public static Optional<Rs2GroundItemModel> findByIds(Integer[] itemIds) {
        Set<Integer> idSet = Set.of(itemIds);
        return find(item -> idSet.contains(item.getId()));
    }

    /**
     * Finds the closest ground item matching any of the given IDs.
     * 
     * @param itemIds Array of item IDs
     * @return Optional containing the closest matching ground item
     */
    public static Optional<Rs2GroundItemModel> findClosestByIds(Integer[] itemIds) {
        Set<Integer> idSet = Set.of(itemIds);
        return findClosest(item -> idSet.contains(item.getId()));
    }

    /**
     * Finds ground items matching any of the given IDs within distance.
     * 
     * @param itemIds Array of item IDs
     * @param distance Maximum distance in tiles
     * @return Stream of matching ground items within distance
     */
    public static Stream<Rs2GroundItemModel> findByIdsWithinDistance(Integer[] itemIds, int distance) {
        Set<Integer> idSet = Set.of(itemIds);
        return findAll(item -> idSet.contains(item.getId()) && item.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds the closest ground item matching any of the given IDs within distance.
     * 
     * @param itemIds Array of item IDs
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest matching ground item within distance
     */
    public static Optional<Rs2GroundItemModel> findClosestByIdsWithinDistance(Integer[] itemIds, int distance) {
        Set<Integer> idSet = Set.of(itemIds);
        return findClosest(item -> idSet.contains(item.getId()) && item.isWithinDistanceFromPlayer(distance));
    }

    // ============================================
    // Value and Property-Based Methods
    // ============================================

    /**
     * Finds ground items with value greater than or equal to the specified amount.
     * 
     * @param minValue Minimum value
     * @return Stream of ground items with value >= minValue
     */
    public static Stream<Rs2GroundItemModel> findByMinValue(int minValue) {
        return findAll(item -> item.getValue() >= minValue);
    }

    /**
     * Finds the closest ground item with value greater than or equal to the specified amount.
     * 
     * @param minValue Minimum value
     * @return Optional containing the closest valuable ground item
     */
    public static Optional<Rs2GroundItemModel> findClosestByMinValue(int minValue) {
        return findClosest(item -> item.getValue() >= minValue);
    }

    /**
     * Finds ground items with value in the specified range.
     * 
     * @param minValue Minimum value (inclusive)
     * @param maxValue Maximum value (inclusive)
     * @return Stream of ground items with value in range
     */
    public static Stream<Rs2GroundItemModel> findByValueRange(int minValue, int maxValue) {
        return findAll(item -> item.getValue() >= minValue && item.getValue() <= maxValue);
    }

    /**
     * Finds stackable ground items.
     * 
     * @return Stream of stackable ground items
     */
    public static Stream<Rs2GroundItemModel> findStackable() {
        return findAll(Rs2GroundItemModel::isStackable);
    }

    /**
     * Finds noted ground items.
     * 
     * @return Stream of noted ground items
     */
    public static Stream<Rs2GroundItemModel> findNoted() {
        return findAll(Rs2GroundItemModel::isNoted);
    }

    /**
     * Finds tradeable ground items.
     * 
     * @return Stream of tradeable ground items
     */
    public static Stream<Rs2GroundItemModel> findTradeable() {
        return findAll(Rs2GroundItemModel::isTradeable);
    }

    /**
     * Finds ground items owned by the player.
     * 
     * @return Stream of owned ground items
     */
    public static Stream<Rs2GroundItemModel> findOwned() {
        return findAll(Rs2GroundItemModel::isOwned);
    }

    /**
     * Finds ground items not owned by the player.
     * 
     * @return Stream of unowned ground items
     */
    public static Stream<Rs2GroundItemModel> findUnowned() {
        return findAll(item -> !item.isOwned());
    }

    /**
     * Finds the most valuable ground item.
     * 
     * @return Optional containing the most valuable ground item
     */
    public static Optional<Rs2GroundItemModel> findMostValuable() {
        return getAll().max(Comparator.comparingInt(Rs2GroundItemModel::getValue));
    }

    /**
     * Finds the most valuable ground item within distance.
     * 
     * @param distance Maximum distance in tiles
     * @return Optional containing the most valuable ground item within distance
     */
    public static Optional<Rs2GroundItemModel> findMostValuableWithinDistance(int distance) {
        return findAll(item -> item.isWithinDistanceFromPlayer(distance))
                .max(Comparator.comparingInt(Rs2GroundItemModel::getValue));
    }

    // ============================================
    // Quantity-Based Methods
    // ============================================

    /**
     * Finds ground items with quantity greater than or equal to the specified amount.
     * 
     * @param minQuantity Minimum quantity
     * @return Stream of ground items with quantity >= minQuantity
     */
    public static Stream<Rs2GroundItemModel> findByMinQuantity(int minQuantity) {
        return findAll(item -> item.getQuantity() >= minQuantity);
    }

    /**
     * Finds ground items with quantity in the specified range.
     * 
     * @param minQuantity Minimum quantity (inclusive)
     * @param maxQuantity Maximum quantity (inclusive)
     * @return Stream of ground items with quantity in range
     */
    public static Stream<Rs2GroundItemModel> findByQuantityRange(int minQuantity, int maxQuantity) {
        return findAll(item -> item.getQuantity() >= minQuantity && item.getQuantity() <= maxQuantity);
    }

    /**
     * Finds the ground item with the highest quantity for a specific item ID.
     * 
     * @param itemId The item ID
     * @return Optional containing the ground item with highest quantity
     */
    public static Optional<Rs2GroundItemModel> findHighestQuantity(int itemId) {
        return getByGameId(itemId).max(Comparator.comparingInt(Rs2GroundItemModel::getQuantity));
    }

    // ============================================
    // Age-Based Methods
    // ============================================

    /**
     * Finds ground items that have been on the ground for at least the specified number of ticks.
     * 
     * @param minTicks Minimum ticks since spawn
     * @return Stream of ground items aged at least minTicks
     */
    public static Stream<Rs2GroundItemModel> findByMinAge(int minTicks) {
        return findAll(item -> item.getTicksSinceSpawn() >= minTicks);
    }

    /**
     * Finds ground items that have been on the ground for less than the specified number of ticks.
     * 
     * @param maxTicks Maximum ticks since spawn
     * @return Stream of fresh ground items
     */
    public static Stream<Rs2GroundItemModel> findFresh(int maxTicks) {
        return findAll(item -> item.getTicksSinceSpawn() <= maxTicks);
    }

    /**
     * Finds the oldest ground item.
     * 
     * @return Optional containing the oldest ground item
     */
    public static Optional<Rs2GroundItemModel> findOldest() {
        return getAll().max(Comparator.comparingInt(Rs2GroundItemModel::getTicksSinceSpawn));
    }

    /**
     * Finds the newest ground item.
     * 
     * @return Optional containing the newest ground item
     */
    public static Optional<Rs2GroundItemModel> findNewest() {
        return getAll().min(Comparator.comparingInt(Rs2GroundItemModel::getTicksSinceSpawn));
    }

    // ============================================
    // Scene and Viewport Extraction Methods
    // ============================================

    /**
     * Gets all ground items currently in the scene (all cached ground items).
     * This includes ground items that may not be visible in the current viewport.
     * 
     * @return Stream of all ground items in the scene
     */
    public static Stream<Rs2GroundItemModel> getAllInScene() {
        return getAll();
    }

    /**
     * Gets all ground items currently visible in the viewport (on screen).
     * Only includes ground items whose location can be converted to screen coordinates.
     * 
     * @return Stream of ground items visible in viewport
     */
    public static Stream<Rs2GroundItemModel> getAllInViewport() {
        return filterVisibleInViewport(getAll());
    }

    /**
     * Gets all ground items by ID that are currently visible in the viewport.
     * 
     * @param itemId The item ID to filter by
     * @return Stream of ground items with the specified ID that are visible in viewport
     */
    public static Stream<Rs2GroundItemModel> getAllInViewport(int itemId) {
        return filterVisibleInViewport(getByGameId(itemId));
    }

    /**
     * Gets the closest ground item in the viewport by ID.
     * 
     * @param itemId The item ID
     * @return Optional containing the closest ground item in viewport
     */
    public static Optional<Rs2GroundItemModel> getClosestInViewport(int itemId) {
        return getAllInViewport(itemId)
                .min(Comparator.comparingInt(Rs2GroundItemModel::getDistanceFromPlayer));
    }

    /**
     * Gets all ground items in the viewport that are interactable (within reasonable distance).
     * 
     * @param maxDistance Maximum distance for interaction
     * @return Stream of interactable ground items in viewport
     */
    public static Stream<Rs2GroundItemModel> getAllInteractable(int maxDistance) {
        return getAllInViewport()
                .filter(item -> isInteractable(item, maxDistance));
    }

    /**
     * Gets all ground items by ID in the viewport that are interactable.
     * 
     * @param itemId The item ID
     * @param maxDistance Maximum distance for interaction
     * @return Stream of interactable ground items with the specified ID
     */
    public static Stream<Rs2GroundItemModel> getAllInteractable(int itemId, int maxDistance) {
        return getAllInViewport(itemId)
                .filter(item -> isInteractable(item, maxDistance));
    }

    /**
     * Gets the closest interactable ground item by ID.
     * 
     * @param itemId The item ID
     * @param maxDistance Maximum distance for interaction
     * @return Optional containing the closest interactable ground item
     */
    public static Optional<Rs2GroundItemModel> getClosestInteractable(int itemId, int maxDistance) {
        return getAllInteractable(itemId, maxDistance)
                .min(Comparator.comparingInt(Rs2GroundItemModel::getDistanceFromPlayer));
    }
    
    // ============================================
    // Line of Sight Utilities
    // ============================================
    
    /**
     * Checks if there is a line of sight between the player and a ground item.
     * Uses RuneLite's WorldArea.hasLineOfSightTo for accurate scene collision detection.
     * 
     * @param groundItem The ground item to check
     * @return True if line of sight exists, false otherwise
     */
    public static boolean hasLineOfSight(Rs2GroundItemModel groundItem) {
        if (groundItem == null) return false;
        
        try {
            // Get player's current world location and create a small area (1x1)
            WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
            return hasLineOfSight(playerLocation, groundItem);
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Checks if there is a line of sight between a specific point and a ground item.
     * 
     * @param point The world point to check from
     * @param groundItem The ground item to check against
     * @return True if line of sight exists, false otherwise
     */
    public static boolean hasLineOfSight(WorldPoint point, Rs2GroundItemModel groundItem) {
        if (groundItem == null || point == null) return false;
        
        try {
            WorldPoint itemLocation = groundItem.getLocation();
            
            // Check same plane
            if (point.getPlane() != itemLocation.getPlane()) {
                return false;
            }
            
            // Ground items are always 1x1
            return new WorldArea(itemLocation, 1, 1)
                    .hasLineOfSightTo(
                            Microbot.getClient().getTopLevelWorldView(),
                            new WorldArea(point, 1, 1));
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Gets all ground items that have line of sight to the player.
     * Useful for identifying interactive items.
     * 
     * @return Stream of ground items with line of sight to player
     */
    public static Stream<Rs2GroundItemModel> getGroundItemsWithLineOfSightToPlayer() {
        return getAll().filter(Rs2GroundItemCacheUtils::hasLineOfSight);
    }
    
    /**
     * Gets all ground items that have line of sight to a specific world point.
     * 
     * @param point The world point to check from
     * @return Stream of ground items with line of sight to the point
     */
    public static Stream<Rs2GroundItemModel> getGroundItemsWithLineOfSightTo(WorldPoint point) {
        return getAll().filter(item -> hasLineOfSight(point, item));
    }
    
    /**
     * Gets all ground items at a location that have line of sight to the player.
     * 
     * @param worldPoint The world point to check at
     * @param maxDistance Maximum distance from the world point
     * @return Stream of ground items at the location with line of sight
     */
    public static Stream<Rs2GroundItemModel> getGroundItemsAtLocationWithLineOfSight(WorldPoint worldPoint, int maxDistance) {
        return getAll()
                .filter(item -> item.getLocation().distanceTo(worldPoint) <= maxDistance)
                .filter(Rs2GroundItemCacheUtils::hasLineOfSight);
    }
    
    // ============================================
    // Looting-Specific Utility Methods
    // ============================================
    
    /**
     * Gets all ground items that are lootable by the player.
     * 
     * @return Stream of lootable ground items
     */
    public static Stream<Rs2GroundItemModel> getLootableItems() {
        return findAll(Rs2GroundItemModel::isLootAble);
    }
    
    /**
     * Gets all lootable ground items within distance from player.
     * 
     * @param distance Maximum distance from player
     * @return Stream of lootable ground items within distance
     */
    public static Stream<Rs2GroundItemModel> getLootableItemsWithinDistance(int distance) {
        return findAll(item -> item.isLootAble() && item.isWithinDistanceFromPlayer(distance));
    }
    
    /**
     * Gets lootable ground items with value greater than or equal to the specified amount.
     * 
     * @param minValue Minimum total value threshold
     * @return Stream of valuable lootable ground items
     */
    public static Stream<Rs2GroundItemModel> getLootableItemsByValue(int minValue) {
        return findAll(item -> item.isLootAble() && item.isWorthLooting(minValue));
    }
    
    /**
     * Gets lootable ground items with Grand Exchange value greater than or equal to the specified amount.
     * 
     * @param minGeValue Minimum GE value threshold
     * @return Stream of valuable lootable ground items
     */
    public static Stream<Rs2GroundItemModel> getLootableItemsByGeValue(int minGeValue) {
        return findAll(item -> item.isLootAble() && item.isWorthLootingGe(minGeValue));
    }
    
    /**
     * Gets lootable ground items in value range.
     * 
     * @param minValue Minimum total value (inclusive)
     * @param maxValue Maximum total value (inclusive)
     * @return Stream of lootable ground items in value range
     */
    public static Stream<Rs2GroundItemModel> getLootableItemsByValueRange(int minValue, int maxValue) {
        return findAll(item -> item.isLootAble() && 
                              item.getTotalValue() >= minValue && 
                              item.getTotalValue() <= maxValue);
    }
    
    /**
     * Gets commonly desired loot items that are available for pickup.
     * 
     * @return Stream of common loot ground items
     */
    public static Stream<Rs2GroundItemModel> getCommonLootItems() {
        return findAll(item -> item.isLootAble() && item.isCommonLoot());
    }
    
    /**
     * Gets high-priority items that should be looted urgently.
     * 
     * @return Stream of priority loot ground items
     */
    public static Stream<Rs2GroundItemModel> getPriorityLootItems() {
        return findAll(item -> item.isLootAble() && item.shouldPrioritize());
    }
    
    /**
     * Gets items that are profitable to high alch.
     * 
     * @param minProfit The minimum profit threshold
     * @return Stream of profitable high alch ground items
     */
    public static Stream<Rs2GroundItemModel> getHighAlchProfitableItems(int minProfit) {
        return findAll(item -> item.isLootAble() && item.isProfitableToHighAlch(minProfit));
    }
    
    /**
     * Gets the most valuable lootable item within distance.
     * 
     * @param maxDistance Maximum distance from player
     * @return Optional containing the most valuable lootable item
     */
    public static Optional<Rs2GroundItemModel> getMostValuableLootableItem(int maxDistance) {
        return getLootableItemsWithinDistance(maxDistance)
                .max(Comparator.comparingInt(Rs2GroundItemModel::getTotalValue));
    }
    
    /**
     * Gets the closest valuable item to the player.
     * 
     * @param minValue Minimum value threshold
     * @return Optional containing the closest valuable lootable item
     */
    public static Optional<Rs2GroundItemModel> getClosestValuableItem(int minValue) {
        return findClosest(item -> item.isLootAble() && item.isWorthLooting(minValue));
    }
    
    /**
     * Gets the closest lootable item to the player.
     * 
     * @return Optional containing the closest lootable item
     */
    public static Optional<Rs2GroundItemModel> getClosestLootableItem() {
        return findClosest(Rs2GroundItemModel::isLootAble);
    }
    
    /**
     * Gets the closest lootable item to the player within distance.
     * 
     * @param maxDistance Maximum distance from player
     * @return Optional containing the closest lootable item within distance
     */
    public static Optional<Rs2GroundItemModel> getClosestLootableItemWithinDistance(int maxDistance) {
        return findClosest(item -> item.isLootAble() && item.isWithinDistanceFromPlayer(maxDistance));
    }
    
    /**
     * Gets lootable items that match any of the provided item IDs.
     * 
     * @param itemIds Array of item IDs to match
     * @return Stream of matching lootable ground items
     */
    public static Stream<Rs2GroundItemModel> getLootableItemsByIds(Integer[] itemIds) {
        Set<Integer> idSet = Set.of(itemIds);
        return findAll(item -> item.isLootAble() && idSet.contains(item.getId()));
    }
    
    /**
     * Gets lootable items by name pattern.
     * 
     * @param namePattern The name pattern (supports partial matches)
     * @param exact Whether to match exactly or use contains
     * @return Stream of matching lootable ground items
     */
    public static Stream<Rs2GroundItemModel> getLootableItemsByName(String namePattern, boolean exact) {
        return findAll(item -> item.isLootAble() && nameMatches(namePattern, exact).test(item));
    }
    
    /**
     * Gets lootable items by name pattern (partial match).
     * 
     * @param namePattern The name pattern
     * @return Stream of matching lootable ground items
     */
    public static Stream<Rs2GroundItemModel> getLootableItemsByName(String namePattern) {
        return getLootableItemsByName(namePattern, false);
    }
    
    // ============================================
    // Despawn-Based Utility Methods
    // ============================================
    
    /**
     * Gets ground items that will despawn within the specified number of seconds.
     * 
     * @param seconds The time threshold in seconds
     * @return Stream of ground items about to despawn
     */
    public static Stream<Rs2GroundItemModel> getItemsDespawningWithin(long seconds) {
        return findAll(item -> item.willDespawnWithin(seconds));
    }
    
    /**
     * Gets ground items that will despawn within the specified number of ticks.
     * 
     * @param ticks The time threshold in ticks
     * @return Stream of ground items about to despawn
     */
    public static Stream<Rs2GroundItemModel> getItemsDespawningWithinTicks(int ticks) {
        return findAll(item -> item.willDespawnWithinTicks(ticks));
    }
    
    /**
     * Gets lootable ground items that will despawn within the specified number of seconds.
     * 
     * @param seconds The time threshold in seconds
     * @return Stream of lootable ground items about to despawn
     */
    public static Stream<Rs2GroundItemModel> getLootableItemsDespawningWithin(long seconds) {
        return findAll(item -> item.isLootAble() && item.willDespawnWithin(seconds));
    }
    
    /**
     * Gets the ground item that will despawn next.
     * 
     * @return Optional containing the next ground item to despawn
     */
    public static Optional<Rs2GroundItemModel> getNextItemToDespawn() {
        return findAll(item -> !item.isDespawned())
                .min(Comparator.comparingLong(Rs2GroundItemModel::getSecondsUntilDespawn));
    }
    
    /**
     * Gets the lootable ground item that will despawn next.
     * 
     * @return Optional containing the next lootable ground item to despawn
     */
    public static Optional<Rs2GroundItemModel> getNextLootableItemToDespawn() {
        return findAll(item -> item.isLootAble() && !item.isDespawned())
                .min(Comparator.comparingLong(Rs2GroundItemModel::getSecondsUntilDespawn));
    }
    
    /**
     * Gets the time in seconds until the next item despawns.
     * 
     * @return Seconds until next despawn, or -1 if no items
     */
    public static long getSecondsUntilNextDespawn() {
        return getNextItemToDespawn()
                .map(Rs2GroundItemModel::getSecondsUntilDespawn)
                .orElse(-1L);
    }
    
    /**
     * Gets the time in seconds until the next lootable item despawns.
     * 
     * @return Seconds until next lootable item despawn, or -1 if no items
     */
    public static long getSecondsUntilNextLootableDespawn() {
        return getNextLootableItemToDespawn()
                .map(Rs2GroundItemModel::getSecondsUntilDespawn)
                .orElse(-1L);
    }
    
    /**
     * Gets ground items that have despawned and should be cleaned up.
     * 
     * @return Stream of despawned ground items
     */
    public static Stream<Rs2GroundItemModel> getDespawnedItems() {
        return findAll(Rs2GroundItemModel::isDespawned);
    }
    
    // ============================================
    // Statistics and Analysis Methods
    // ============================================
    
    /**
     * Gets the total value of all lootable items.
     * 
     * @return Total value of all lootable ground items
     */
    public static int getTotalLootableValue() {
        return getLootableItems()
                .mapToInt(Rs2GroundItemModel::getTotalValue)
                .sum();
    }
    
    /**
     * Gets the total Grand Exchange value of all lootable items.
     * 
     * @return Total GE value of all lootable ground items
     */
    public static int getTotalLootableGeValue() {
        return getLootableItems()
                .mapToInt(Rs2GroundItemModel::getTotalGeValue)
                .sum();
    }
    
    /**
     * Gets the count of lootable items.
     * 
     * @return Number of lootable ground items
     */
    public static int getLootableItemCount() {
        return (int) getLootableItems().count();
    }
    
    /**
     * Gets the count of lootable items within distance.
     * 
     * @param distance Maximum distance from player
     * @return Number of lootable ground items within distance
     */
    public static int getLootableItemCountWithinDistance(int distance) {
        return (int) getLootableItemsWithinDistance(distance).count();
    }
    
 
    
    /**
     * Gets statistics about lootable items.
     * 
     * @return Map containing lootable item statistics
     */
    public static Map<String, Object> getLootableItemStatistics() {
        Map<String, Object> stats = new HashMap<>();
        
        List<Rs2GroundItemModel> allItems = getAll().collect(Collectors.toList());
        List<Rs2GroundItemModel> lootableItems = getLootableItems().collect(Collectors.toList());
        
        stats.put("totalItems", allItems.size());
        stats.put("lootableItems", lootableItems.size());
        stats.put("lootablePercentage", allItems.isEmpty() ? 0 : (lootableItems.size() * 100.0 / allItems.size()));
        stats.put("totalLootableValue", lootableItems.stream().mapToInt(Rs2GroundItemModel::getTotalValue).sum());
        stats.put("totalLootableGeValue", lootableItems.stream().mapToInt(Rs2GroundItemModel::getTotalGeValue).sum());
        stats.put("averageLootableValue", lootableItems.isEmpty() ? 0 : 
                lootableItems.stream().mapToInt(Rs2GroundItemModel::getTotalValue).average().orElse(0));
        stats.put("lootableItemsDespawningIn30s", lootableItems.stream().filter(item -> item.willDespawnWithin(30)).count());
        stats.put("secondsUntilNextLootableDespawn", getSecondsUntilNextLootableDespawn());
        stats.put("priorityLootItems", getPriorityLootItems().count());
        stats.put("commonLootItems", getCommonLootItems().count());
        
        return stats;
    }
    
    // ============================================
    // Advanced Filtering Methods
    // ============================================
    
    /**
     * Gets ground items matching multiple criteria with custom predicates.
     * 
     * @param isLootable Whether to filter for lootable items only
     * @param minValue Minimum value filter (0 to ignore)
     * @param maxDistance Maximum distance filter (0 to ignore)
     * @param customPredicate Additional custom predicate (null to ignore)
     * @return Stream of matching ground items
     */
    public static Stream<Rs2GroundItemModel> getItemsWithCriteria(
            boolean isLootable, 
            int minValue, 
            int maxDistance, 
            Predicate<Rs2GroundItemModel> customPredicate) {
        
        Stream<Rs2GroundItemModel> stream = getAll();
        
        if (isLootable) {
            stream = stream.filter(Rs2GroundItemModel::isLootAble);
        }
        
        if (minValue > 0) {
            stream = stream.filter(item -> item.getTotalValue() >= minValue);
        }
        
        if (maxDistance > 0) {
            stream = stream.filter(item -> item.isWithinDistanceFromPlayer(maxDistance));
        }
        
        if (customPredicate != null) {
            stream = stream.filter(customPredicate);
        }
        
        return stream;
    }
    
    /**
     * Gets items that are both lootable and have line of sight to the player.
     * Useful for identifying immediately interactable loot.
     * 
     * @return Stream of lootable ground items with line of sight
     */
    public static Stream<Rs2GroundItemModel> getLootableItemsWithLineOfSight() {
        return getLootableItems().filter(Rs2GroundItemCacheUtils::hasLineOfSight);
    }
    
    /**
     * Gets lootable items within distance that have line of sight to the player.
     * 
     * @param maxDistance Maximum distance from player
     * @return Stream of lootable ground items within distance with line of sight
     */
    public static Stream<Rs2GroundItemModel> getLootableItemsWithLineOfSightWithinDistance(int maxDistance) {
        return getLootableItemsWithinDistance(maxDistance)
                .filter(Rs2GroundItemCacheUtils::hasLineOfSight);
    }
    
    /**
     * Gets the closest lootable item with line of sight to the player.
     * 
     * @return Optional containing the closest lootable item with line of sight
     */
    public static Optional<Rs2GroundItemModel> getClosestLootableItemWithLineOfSight() {
        return getLootableItemsWithLineOfSight()
                .min(Comparator.comparingInt(Rs2GroundItemModel::getDistanceFromPlayer));
    }
    
    /**
     * Gets the closest valuable lootable item with line of sight to the player.
     * 
     * @param minValue Minimum value threshold
     * @return Optional containing the closest valuable lootable item with line of sight
     */
    public static Optional<Rs2GroundItemModel> getClosestValuableLootableItemWithLineOfSight(int minValue) {
        return getLootableItemsWithLineOfSight()
                .filter(item -> item.isWorthLooting(minValue))
                .min(Comparator.comparingInt(Rs2GroundItemModel::getDistanceFromPlayer));
    }
    
    // ============================================
    // Tile-Based Access Methods (Rs2GroundItem replacement support)
    // ============================================
    
    /**
     * Gets all ground items at a specific tile location.
     * Direct replacement for Rs2GroundItem.getAllAt(x, y).
     * 
     * @param location The world point location of the tile
     * @return Stream of ground items at the specified tile
     */
    public static Stream<Rs2GroundItemModel> getItemsAtTile(WorldPoint location) {
        return getAll().filter(item -> item.getLocation().equals(location));
    }
    
    /**
     * Gets all ground items at a specific tile coordinate.
     * Direct replacement for Rs2GroundItem.getAllAt(x, y).
     * 
     * @param x The x coordinate of the tile
     * @param y The y coordinate of the tile
     * @return Stream of ground items at the specified tile
     */
    public static Stream<Rs2GroundItemModel> getItemsAtTile(int x, int y) {
        return getItemsAtTile(new WorldPoint(x, y, Microbot.getClient().getLocalPlayer().getWorldLocation().getPlane()));
    }
    
    /**
     * Gets all ground items within a range of a specific WorldPoint.
     * Direct replacement for Rs2GroundItem.getAllFromWorldPoint(range, worldPoint).
     * 
     * @param range The radius in tiles to search around the given world point
     * @param worldPoint The center WorldPoint to search around
     * @return Stream of ground items found within the specified range, sorted by proximity
     */
    public static Stream<Rs2GroundItemModel> getItemsFromWorldPoint(int range, WorldPoint worldPoint) {
        return getAll()
                .filter(item -> item.getLocation().distanceTo(worldPoint) <= range)
                .sorted(Comparator.comparingInt(item -> item.getLocation().distanceTo(worldPoint)));
    }
    
    /**
     * Gets all ground items within a range of the player.
     * Direct replacement for Rs2GroundItem.getAll(range).
     * 
     * @param range The radius in tiles to search around the player
     * @return Stream of ground items found within the specified range, sorted by proximity to player
     */
    public static Stream<Rs2GroundItemModel> getItemsAroundPlayer(int range) {
        try {
            WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
            return getItemsFromWorldPoint(range, playerLocation);
        } catch (Exception e) {
            return Stream.empty();
        }
    }
    
    // ============================================
    // Enhanced Interaction Utilities
    // ============================================
    
    /**
     * Finds the best ground item to interact with based on criteria.
     * 
     * @param itemId The item ID to search for
     * @param action The action to perform (e.g., "Take")
     * @param range Maximum search range
     * @return Optional containing the best item to interact with
     */
    public static Optional<Rs2GroundItemModel> findBestInteractionTarget(int itemId, String action, int range) {
        return getItemsAroundPlayer(range)
                .filter(item -> item.getId() == itemId)
                .filter(item -> item.isLootAble())
                .filter(Rs2GroundItemCacheUtils::hasLineOfSight)
                .min(Comparator.comparingInt(Rs2GroundItemModel::getDistanceFromPlayer));
    }
    
    /**
     * Finds the best ground item to interact with based on name.
     * 
     * @param itemName The item name to search for
     * @param action The action to perform (e.g., "Take")
     * @param range Maximum search range
     * @return Optional containing the best item to interact with
     */
    public static Optional<Rs2GroundItemModel> findBestInteractionTarget(String itemName, String action, int range) {
        return getItemsAroundPlayer(range)
                .filter(item -> nameMatches(itemName, false).test(item))
                .filter(item -> item.isLootAble())
                .filter(Rs2GroundItemCacheUtils::hasLineOfSight)
                .min(Comparator.comparingInt(Rs2GroundItemModel::getDistanceFromPlayer));
    }
    
    /**
     * Checks if any ground item exists with the specified ID within range.
     * Direct replacement for Rs2GroundItem.exists(id, range).
     * 
     * @param itemId The item ID to check for
     * @param range Maximum search range
     * @return true if the item exists within range
     */
    public static boolean exists(int itemId, int range) {
        return getItemsAroundPlayer(range)
                .anyMatch(item -> item.getId() == itemId);
    }
    
    /**
     * Checks if any ground item exists with the specified name within range.
     * Direct replacement for Rs2GroundItem.exists(itemName, range).
     * 
     * @param itemName The item name to check for
     * @param range Maximum search range
     * @return true if the item exists within range
     */
    public static boolean exists(String itemName, int range) {
        return getItemsAroundPlayer(range)
                .anyMatch(item -> nameMatches(itemName, false).test(item));
    }
    
    /**
     * Checks if valuable items exist on the ground based on minimum value.
     * Direct replacement for Rs2GroundItem.isItemBasedOnValueOnGround(value, range).
     * 
     * @param minValue Minimum value threshold
     * @param range Maximum search range
     * @return true if valuable items exist within range
     */
    public static boolean existsValueableItems(int minValue, int range) {
        return getLootableItemsWithinDistance(range)
                .anyMatch(item -> item.isWorthLooting(minValue));
    }
    
    // ============================================
    // Batch Processing Methods
    // ============================================
    
    /**
     * Gets multiple ground items of different IDs within range.
     * Useful for batch operations.
     * 
     * @param itemIds Array of item IDs to search for
     * @param range Maximum search range
     * @return Map of item ID to list of ground items
     */
    public static Map<Integer, List<Rs2GroundItemModel>> getBatchItems(Integer[] itemIds, int range) {
        Set<Integer> idSet = Set.of(itemIds);
        return getItemsAroundPlayer(range)
                .filter(item -> idSet.contains(item.getId()))
                .filter(item -> item.isLootAble())
                .collect(Collectors.groupingBy(Rs2GroundItemModel::getId));
    }
    
    /**
     * Gets ground items sorted by value within range.
     * Useful for priority-based looting.
     * 
     * @param range Maximum search range
     * @param minValue Minimum value threshold
     * @return Stream of ground items sorted by value (highest first)
     */
    public static Stream<Rs2GroundItemModel> getItemsSortedByValue(int range, int minValue) {
        return getLootableItemsWithinDistance(range)
                .filter(item -> item.getTotalValue() >= minValue)
                .sorted((a, b) -> Integer.compare(b.getTotalValue(), a.getTotalValue()));
    }
    
    /**
     * Gets ground items sorted by despawn urgency.
     * Items closest to despawning are returned first.
     * 
     * @param range Maximum search range
     * @return Stream of ground items sorted by despawn urgency
     */
    public static Stream<Rs2GroundItemModel> getItemsSortedByDespawnUrgency(int range) {
        return getLootableItemsWithinDistance(range)
                .filter(item -> !item.isDespawned())
                .sorted(Comparator.comparingLong(Rs2GroundItemModel::getSecondsUntilDespawn));
    }
    
    // ============================================
    // Viewport Visibility and Interactability Utilities
    // ============================================

    /**
     * Checks if a ground item is visible in the current viewport.
     * Uses the tile location with client thread safety to determine visibility.
     * 
     * @param groundItem The ground item to check
     * @return true if the ground item's location is visible on screen
     */
    public static boolean isVisibleInViewport(Rs2GroundItemModel groundItem) {
        try {
            if (groundItem == null || groundItem.getLocation() == null) {
                return false;
            }

            // Use client thread for safe access to client state
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Client client = Microbot.getClient();
                if (client == null) {
                    return false;
                }

                // Convert world point to local point
                LocalPoint localPoint = LocalPoint.fromWorld(client.getTopLevelWorldView(), groundItem.getLocation());
                if (localPoint == null) {
                    return false;
                }

                // Check if the local point can be converted to canvas coordinates
                net.runelite.api.Point canvasPoint = Perspective.localToCanvas(client, localPoint, client.getTopLevelWorldView().getPlane());
                return canvasPoint != null;
            }).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if any entity with a location is within the viewport by checking canvas conversion.
     * This is a generic method that can work with any entity that has a world location.
     * Uses client thread for safe access to client state.
     * 
     * @param worldPoint The world point to check
     * @return true if the location is visible on screen
     */
    public static boolean isLocationVisibleInViewport(net.runelite.api.coords.WorldPoint worldPoint) {
        try {
            if (worldPoint == null) {
                return false;
            }

            // Use client thread for safe access to client state
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Client client = Microbot.getClient();
                if (client == null) {
                    return false;
                }

                LocalPoint localPoint = LocalPoint.fromWorld(client.getTopLevelWorldView(), worldPoint);
                if (localPoint == null) {
                    return false;
                }

                net.runelite.api.Point canvasPoint = Perspective.localToCanvas(client, localPoint, client.getTopLevelWorldView().getPlane());
                return canvasPoint != null;
            }).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Filters a stream of ground items to only include those visible in viewport.
     * 
     * @param groundItemStream Stream of ground items to filter
     * @return Stream of ground items visible in viewport
     */
    public static Stream<Rs2GroundItemModel> filterVisibleInViewport(Stream<Rs2GroundItemModel> groundItemStream) {
        return groundItemStream.filter(Rs2GroundItemCacheUtils::isVisibleInViewport);
    }

    /**
     * Checks if a ground item is interactable (visible and within reasonable distance).
     * 
     * @param groundItem The ground item to check
     * @param maxDistance Maximum distance in tiles for interaction
     * @return true if the ground item is interactable
     */
    public static boolean isInteractable(Rs2GroundItemModel groundItem, int maxDistance) {
        try {
            if (groundItem == null) {
                return false;
            }

            // Check if visible in viewport first
            if (!isVisibleInViewport(groundItem)) {
                return false;
            }

            // Check distance from player
            return groundItem.getDistanceFromPlayer() <= maxDistance;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Checks if an entity at a world point is interactable (within reasonable distance and visible).
     * Uses client thread for safe access to player location.
     * 
     * @param worldPoint The world point to check
     * @param maxDistance Maximum distance in tiles for interaction
     * @return true if the location is potentially interactable
     */
    public static boolean isInteractable(net.runelite.api.coords.WorldPoint worldPoint, int maxDistance) {
        try {
            if (worldPoint == null) {
                return false;
            }

            // Use client thread for safe access to player location
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                net.runelite.api.coords.WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
                if (playerLocation.distanceTo(worldPoint) > maxDistance) {
                    return false;
                }
                
                // Check if visible in viewport (already uses client thread internally)
                return isLocationVisibleInViewport(worldPoint);
            }).orElse(false);
        } catch (Exception e) {
            return false;
        }
    }

    // ============================================
    // Updated Existing Methods to Use Local Functions
    // ============================================

    /**
     * Gets all ground items visible in the viewport.
     * 
     * @return Stream of ground items visible in viewport
     */
    public static Stream<Rs2GroundItemModel> getVisibleInViewport() {
        return filterVisibleInViewport(getAll());
    }

    /**
     * Gets ground items by ID that are visible in the viewport.
     * 
     * @param itemId The item ID
     * @return Stream of ground items with the specified ID visible in viewport
     */
    public static Stream<Rs2GroundItemModel> getVisibleInViewportById(int itemId) {
        return filterVisibleInViewport(getByGameId(itemId));
    }

    /**
     * Finds interactable ground items by ID within distance from player.
     * 
     * @param itemId The item ID
     * @param maxDistance Maximum distance in tiles
     * @return Stream of interactable ground items with the specified ID
     */
    public static Stream<Rs2GroundItemModel> findInteractableById(int itemId, int maxDistance) {
        return getByGameId(itemId)
                .filter(item -> isInteractable(item, maxDistance));
    }

    /**
     * Finds interactable ground items by name within distance from player.
     * 
     * @param name The item name
     * @param maxDistance Maximum distance in tiles
     * @return Stream of interactable ground items with the specified name
     */
    public static Stream<Rs2GroundItemModel> findInteractableByName(String name, int maxDistance) {
        return findAll(nameMatches(name, false))
                .filter(item -> isInteractable(item, maxDistance));
    }
}
