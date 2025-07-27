package net.runelite.client.plugins.microbot.util.cache.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.DecorativeObject;
import net.runelite.api.GameObject;
import net.runelite.api.GroundObject;
import net.runelite.api.Model;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Renderable;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.cache.Rs2ObjectCache;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2ObjectModel;
import net.runelite.client.plugins.microbot.Microbot;

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

/**
 * Advanced cache-based utilities for game objects.
 * Provides scene-independent methods for finding and filtering objects.
 * 
 * This class offers high-performance object operations using cached data,
 * avoiding the need to iterate through scene tiles. Supports all object types:
 * GameObject, GroundObject, WallObject, and DecorativeObject.
 * 
 * @author Vox
 * @version 1.0
 */
@Slf4j
public class Rs2ObjectCacheUtils {

    // ============================================
    // Core Cache Access Methods
    // ============================================

    /**
     * Gets objects by their game ID.
     * 
     * @param objectId The object ID
     * @return Stream of matching objects
     */
    public static Stream<Rs2ObjectModel> getByGameId(int objectId) {
        try {
            return Rs2ObjectCache.getInstance().stream()
                    .filter(obj -> obj.getId() == objectId);
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    /**
     * Gets the first object matching the criteria.
     * 
     * @param objectId The object ID
     * @return Optional containing the first matching object
     */
    public static Optional<Rs2ObjectModel> getFirst(int objectId) {
        try {
            return Rs2ObjectCache.getInstance().stream()
                    .filter(obj -> obj.getId() == objectId)
                    .findFirst();
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Gets the closest object to the player.
     * 
     * @param objectId The object ID
     * @return Optional containing the closest object
     */
    public static Optional<Rs2ObjectModel> getClosest(int objectId) {
        try {
            return Rs2ObjectCache.getInstance().stream()
                    .filter(obj -> obj.getId() == objectId)
                    .min(Comparator.comparingInt(Rs2ObjectModel::getDistanceFromPlayer));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Gets all cached objects.
     * 
     * @return Stream of all objects
     */
    public static Stream<Rs2ObjectModel> getAll() {
        try {
            return Rs2ObjectCache.getInstance().stream();
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    // ============================================
    // Advanced Finding Methods
    // ============================================

    /**
     * Finds the nearest object matching a predicate.
     * 
     * @param predicate The predicate to match
     * @return Optional containing the first matching object
     */
    public static Optional<Rs2ObjectModel> findNearest(Predicate<Rs2ObjectModel> predicate) {
        try {
            return findClosest(predicate);
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    /**
     * Finds all objects matching a predicate.
     * 
     * @param predicate The predicate to match
     * @return Stream of matching objects
     */
    public static Stream<Rs2ObjectModel> findAll(Predicate<Rs2ObjectModel> predicate) {
        try {
            return Rs2ObjectCache.getInstance().stream().filter(predicate);
        } catch (Exception e) {
            return Stream.empty();
        }
    }

    /**
     * Finds the closest object matching a predicate.
     * 
     * @param predicate The predicate to match
     * @return Optional containing the closest matching object
     */
    public static Optional<Rs2ObjectModel> findClosest(Predicate<Rs2ObjectModel> predicate) {
        try {
            return Rs2ObjectCache.getInstance().stream()
                    .filter(predicate)
                    .min(Comparator.comparingInt(Rs2ObjectModel::getDistanceFromPlayer));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    // ============================================
    // Distance-Based Finding Methods
    // ============================================

    /**
     * Finds the first object within distance from player by ID.
     * 
     * @param objectId The object ID
     * @param distance Maximum distance in tiles
     * @return Optional containing the first matching object within distance
     */
    public static Optional<Rs2ObjectModel> findWithinDistance(int objectId, int distance) {
        return findNearest(obj -> obj.getId() == objectId && obj.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds all objects within distance from player by ID.
     * 
     * @param objectId The object ID
     * @param distance Maximum distance in tiles
     * @return Stream of matching objects within distance
     */
    public static Stream<Rs2ObjectModel> findAllWithinDistance(int objectId, int distance) {
        return findAll(obj -> obj.getId() == objectId && obj.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds the closest object by ID within distance from player.
     * 
     * @param objectId The object ID
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest matching object within distance
     */
    public static Optional<Rs2ObjectModel> findClosestWithinDistance(int objectId, int distance) {
        return findClosest(obj -> obj.getId() == objectId && obj.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds objects within distance from an anchor point.
     * 
     * @param predicate The predicate to match
     * @param anchor The anchor point
     * @param distance Maximum distance in tiles
     * @return Stream of matching objects within distance from anchor
     */
    public static Stream<Rs2ObjectModel> findWithinDistance(Predicate<Rs2ObjectModel> predicate, WorldPoint anchor, int distance) {
        return findAll(obj -> predicate.test(obj) && obj.getLocation().distanceTo(anchor) <= distance);
    }

    /**
     * Finds the closest object to an anchor point.
     * 
     * @param predicate The predicate to match
     * @param anchor The anchor point
     * @return Optional containing the closest matching object to anchor
     */
    public static Optional<Rs2ObjectModel> findClosest(Predicate<Rs2ObjectModel> predicate, WorldPoint anchor) {
        return findAll(predicate)
                .min(Comparator.comparingInt(obj -> obj.getLocation().distanceTo(anchor)));
    }

    /**
     * Finds the closest object to an anchor point within distance.
     * 
     * @param predicate The predicate to match
     * @param anchor The anchor point
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest matching object to anchor within distance
     */
    public static Optional<Rs2ObjectModel> findClosest(Predicate<Rs2ObjectModel> predicate, WorldPoint anchor, int distance) {
        return findWithinDistance(predicate, anchor, distance)
                .min(Comparator.comparingInt(obj -> obj.getLocation().distanceTo(anchor)));
    }

    // ============================================
    // Name-Based Finding Methods
    // ============================================

    /**
     * Creates a predicate that matches objects whose name contains the given string (case-insensitive).
     * 
     * @param objectName The name to match (partial or full)
     * @param exact Whether to match exactly or contain
     * @return Predicate for name matching
     */
    public static Predicate<Rs2ObjectModel> nameMatches(String objectName, boolean exact) {
        String lower = objectName.toLowerCase();
        return obj -> {
            String objName = obj.getName();
            if (objName == null) return false;
            return exact ? objName.equalsIgnoreCase(objectName) : objName.toLowerCase().contains(lower);
        };
    }

    /**
     * Creates a predicate that matches objects whose name contains the given string (case-insensitive).
     * 
     * @param objectName The name to match (partial)
     * @return Predicate for name matching
     */
    public static Predicate<Rs2ObjectModel> nameMatches(String objectName) {
        return nameMatches(objectName, false);
    }

    /**
     * Finds the first object by name.
     * 
     * @param objectName The object name
     * @param exact Whether to match exactly or contain
     * @return Optional containing the first matching object
     */
    public static Optional<Rs2ObjectModel> findNearestByName(String objectName, boolean exact) {
        return findNearest(nameMatches(objectName, exact));
    }

    /**
     * Finds the first object by name (partial match).
     * 
     * @param objectName The object name
     * @return Optional containing the first matching object
     */
    public static Optional<Rs2ObjectModel> findNearestByName(String objectName) {
        return findNearestByName(objectName, false);
    }

    /**
     * Finds the closest object by name.
     * 
     * @param objectName The object name
     * @param exact Whether to match exactly or contain
     * @return Optional containing the closest matching object
     */
    public static Optional<Rs2ObjectModel> findClosestByName(String objectName, boolean exact) {
        return findClosest(nameMatches(objectName, exact));
    }

    /**
     * Finds the closest object by name (partial match).
     * 
     * @param objectName The object name
     * @return Optional containing the closest matching object
     */
    public static Optional<Rs2ObjectModel> findClosestByName(String objectName) {
        return findClosestByName(objectName, false);
    }

    /**
     * Finds objects by name within distance from player.
     * 
     * @param objectName The object name
     * @param exact Whether to match exactly or contain
     * @param distance Maximum distance in tiles
     * @return Stream of matching objects within distance
     */
    public static Stream<Rs2ObjectModel> findByNameWithinDistance(String objectName, boolean exact, int distance) {
        return findAll(obj -> nameMatches(objectName, exact).test(obj) && obj.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds objects by name within distance from player (partial match).
     * 
     * @param objectName The object name
     * @param distance Maximum distance in tiles
     * @return Stream of matching objects within distance
     */
    public static Stream<Rs2ObjectModel> findByNameWithinDistance(String objectName, int distance) {
        return findByNameWithinDistance(objectName, false, distance);
    }

    /**
     * Finds the closest object by name within distance from player.
     * 
     * @param objectName The object name
     * @param exact Whether to match exactly or contain
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest matching object within distance
     */
    public static Optional<Rs2ObjectModel> findClosestByNameWithinDistance(String objectName, boolean exact, int distance) {
        return findClosest(obj -> nameMatches(objectName, exact).test(obj) && obj.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds the closest object by name within distance from player (partial match).
     * 
     * @param objectName The object name
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest matching object within distance
     */
    public static Optional<Rs2ObjectModel> findClosestByNameWithinDistance(String objectName, int distance) {
        return findClosestByNameWithinDistance(objectName, false, distance);
    }

    // ============================================
    // Array-Based ID Methods
    // ============================================

    /**
     * Finds the first object matching any of the given IDs.
     * 
     * @param objectIds Array of object IDs
     * @return Optional containing the first matching object
     */
    public static Optional<Rs2ObjectModel> findNearestByIds(Integer[] objectIds) {
        Set<Integer> idSet = Set.of(objectIds);
        return findClosest(obj -> idSet.contains(obj.getId()));
    }

    /**
     * Finds the closest object matching any of the given IDs.
     * 
     * @param objectIds Array of object IDs
     * @return Optional containing the closest matching object
     */
    public static Optional<Rs2ObjectModel> findClosestByIds(Integer[] objectIds) {
        Set<Integer> idSet = Set.of(objectIds);
        return findClosest(obj -> idSet.contains(obj.getId()));
    }

    /**
     * Finds objects matching any of the given IDs within distance.
     * 
     * @param objectIds Array of object IDs
     * @param distance Maximum distance in tiles
     * @return Stream of matching objects within distance
     */
    public static Stream<Rs2ObjectModel> findByIdsWithinDistance(Integer[] objectIds, int distance) {
        Set<Integer> idSet = Set.of(objectIds);
        return findAll(obj -> idSet.contains(obj.getId()) && obj.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds the closest object matching any of the given IDs within distance.
     * 
     * @param objectIds Array of object IDs
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest matching object within distance
     */
    public static Optional<Rs2ObjectModel> findClosestByIdsWithinDistance(Integer[] objectIds, int distance) {
        Set<Integer> idSet = Set.of(objectIds);
        return findClosest(obj -> idSet.contains(obj.getId()) && obj.isWithinDistanceFromPlayer(distance));
    }

    // ============================================
    // Type-Specific Finding Methods
    // ============================================

    /**
     * Finds objects of a specific type.
     * 
     * @param objectType The object type to find
     * @return Stream of objects of the specified type
     */
    public static Stream<Rs2ObjectModel> findByType(Rs2ObjectModel.ObjectType objectType) {
        return findAll(obj -> obj.getObjectType() == objectType);
    }

    /**
     * Finds the closest object of a specific type.
     * 
     * @param objectType The object type to find
     * @return Optional containing the closest object of the specified type
     */
    public static Optional<Rs2ObjectModel> findClosestByType(Rs2ObjectModel.ObjectType objectType) {
        return findClosest(obj -> obj.getObjectType() == objectType);
    }

    /**
     * Finds objects of a specific type within distance.
     * 
     * @param objectType The object type to find
     * @param distance Maximum distance in tiles
     * @return Stream of objects of the specified type within distance
     */
    public static Stream<Rs2ObjectModel> findByTypeWithinDistance(Rs2ObjectModel.ObjectType objectType, int distance) {
        return findAll(obj -> obj.getObjectType() == objectType && obj.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds the closest object of a specific type within distance.
     * 
     * @param objectType The object type to find
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest object of the specified type within distance
     */
    public static Optional<Rs2ObjectModel> findClosestByTypeWithinDistance(Rs2ObjectModel.ObjectType objectType, int distance) {
        return findClosest(obj -> obj.getObjectType() == objectType && obj.isWithinDistanceFromPlayer(distance));
    }

    // ============================================
    // Convenience Methods for Specific Object Types
    // ============================================

    /**
     * Finds GameObjects only.
     * 
     * @return Stream of GameObjects
     */
    public static Stream<Rs2ObjectModel> findGameObjects() {
        return findByType(Rs2ObjectModel.ObjectType.GAME_OBJECT);
    }

    /**
     * Finds the closest GameObject.
     * 
     * @return Optional containing the closest GameObject
     */
    public static Optional<Rs2ObjectModel> findClosestGameObject() {
        return findClosestByType(Rs2ObjectModel.ObjectType.GAME_OBJECT);
    }

    /**
     * Finds GameObjects within distance.
     * 
     * @param distance Maximum distance in tiles
     * @return Stream of GameObjects within distance
     */
    public static Stream<Rs2ObjectModel> findGameObjectsWithinDistance(int distance) {
        return findByTypeWithinDistance(Rs2ObjectModel.ObjectType.GAME_OBJECT, distance);
    }

    /**
     * Finds GroundObjects only.
     * 
     * @return Stream of GroundObjects
     */
    public static Stream<Rs2ObjectModel> findGroundObjects() {
        return findByType(Rs2ObjectModel.ObjectType.GROUND_OBJECT);
    }

    /**
     * Finds the closest GroundObject.
     * 
     * @return Optional containing the closest GroundObject
     */
    public static Optional<Rs2ObjectModel> findClosestGroundObject() {
        return findClosestByType(Rs2ObjectModel.ObjectType.GROUND_OBJECT);
    }

    /**
     * Finds GroundObjects within distance.
     * 
     * @param distance Maximum distance in tiles
     * @return Stream of GroundObjects within distance
     */
    public static Stream<Rs2ObjectModel> findGroundObjectsWithinDistance(int distance) {
        return findByTypeWithinDistance(Rs2ObjectModel.ObjectType.GROUND_OBJECT, distance);
    }

    /**
     * Finds WallObjects only.
     * 
     * @return Stream of WallObjects
     */
    public static Stream<Rs2ObjectModel> findWallObjects() {
        return findByType(Rs2ObjectModel.ObjectType.WALL_OBJECT);
    }

    /**
     * Finds the closest WallObject.
     * 
     * @return Optional containing the closest WallObject
     */
    public static Optional<Rs2ObjectModel> findClosestWallObject() {
        return findClosestByType(Rs2ObjectModel.ObjectType.WALL_OBJECT);
    }

    /**
     * Finds WallObjects within distance.
     * 
     * @param distance Maximum distance in tiles
     * @return Stream of WallObjects within distance
     */
    public static Stream<Rs2ObjectModel> findWallObjectsWithinDistance(int distance) {
        return findByTypeWithinDistance(Rs2ObjectModel.ObjectType.WALL_OBJECT, distance);
    }

    /**
     * Finds DecorativeObjects only.
     * 
     * @return Stream of DecorativeObjects
     */
    public static Stream<Rs2ObjectModel> findDecorativeObjects() {
        return findByType(Rs2ObjectModel.ObjectType.DECORATIVE_OBJECT);
    }

    /**
     * Finds the closest DecorativeObject.
     * 
     * @return Optional containing the closest DecorativeObject
     */
    public static Optional<Rs2ObjectModel> findClosestDecorativeObject() {
        return findClosestByType(Rs2ObjectModel.ObjectType.DECORATIVE_OBJECT);
    }

    /**
     * Finds DecorativeObjects within distance.
     * 
     * @param distance Maximum distance in tiles
     * @return Stream of DecorativeObjects within distance
     */
    public static Stream<Rs2ObjectModel> findDecorativeObjectsWithinDistance(int distance) {
        return findByTypeWithinDistance(Rs2ObjectModel.ObjectType.DECORATIVE_OBJECT, distance);
    }

    // ============================================
    // Action-Based Finding Methods
    // ============================================

    /**
     * Finds objects that have a specific action.
     * 
     * @param action The action to look for
     * @return Stream of objects with the specified action
     */
    public static Stream<Rs2ObjectModel> findByAction(String action) {
        return findAll(obj -> obj.hasAction(action));
    }

    /**
     * Finds the closest object that has a specific action.
     * 
     * @param action The action to look for
     * @return Optional containing the closest object with the specified action
     */
    public static Optional<Rs2ObjectModel> findClosestByAction(String action) {
        return findClosest(obj -> obj.hasAction(action));
    }

    /**
     * Finds objects with a specific action within distance.
     * 
     * @param action The action to look for
     * @param distance Maximum distance in tiles
     * @return Stream of objects with the specified action within distance
     */
    public static Stream<Rs2ObjectModel> findByActionWithinDistance(String action, int distance) {
        return findAll(obj -> obj.hasAction(action) && obj.isWithinDistanceFromPlayer(distance));
    }

    /**
     * Finds the closest object with a specific action within distance.
     * 
     * @param action The action to look for
     * @param distance Maximum distance in tiles
     * @return Optional containing the closest object with the specified action within distance
     */
    public static Optional<Rs2ObjectModel> findClosestByActionWithinDistance(String action, int distance) {
        return findClosest(obj -> obj.hasAction(action) && obj.isWithinDistanceFromPlayer(distance));
    }

    // ============================================
    // Size-Based Finding Methods
    // ============================================

    /**
     * Finds objects with a specific width.
     * 
     * @param width The width to match
     * @return Stream of objects with the specified width
     */
    public static Stream<Rs2ObjectModel> findByWidth(int width) {
        return findAll(obj -> obj.getWidth() == width);
    }

    /**
     * Finds objects with a specific height.
     * 
     * @param height The height to match
     * @return Stream of objects with the specified height
     */
    public static Stream<Rs2ObjectModel> findByHeight(int height) {
        return findAll(obj -> obj.getHeight() == height);
    }

    /**
     * Finds objects with specific dimensions.
     * 
     * @param width The width to match
     * @param height The height to match
     * @return Stream of objects with the specified dimensions
     */
    public static Stream<Rs2ObjectModel> findBySize(int width, int height) {
        return findAll(obj -> obj.getWidth() == width && obj.getHeight() == height);
    }

    /**
     * Finds large objects (width or height > 1).
     * 
     * @return Stream of large objects
     */
    public static Stream<Rs2ObjectModel> findLargeObjects() {
        return findAll(obj -> obj.getWidth() > 1 || obj.getHeight() > 1);
    }

    /**
     * Finds single-tile objects (width and height = 1).
     * 
     * @return Stream of single-tile objects
     */
    public static Stream<Rs2ObjectModel> findSingleTileObjects() {
        return findAll(obj -> obj.getWidth() == 1 && obj.getHeight() == 1);
    }

    // ============================================
    // Property-Based Finding Methods
    // ============================================

    /**
     * Finds solid objects (objects that block movement).
     * 
     * @return Stream of solid objects
     */
    public static Stream<Rs2ObjectModel> findSolidObjects() {
        return findAll(Rs2ObjectModel::isSolid);
    }

    /**
     * Finds non-solid objects (objects that don't block movement).
     * 
     * @return Stream of non-solid objects
     */
    public static Stream<Rs2ObjectModel> findNonSolidObjects() {
        return findAll(obj -> !obj.isSolid());
    }

    // ============================================
    // Age-Based Finding Methods
    // ============================================

    /**
     * Finds objects that have been cached for at least the specified number of ticks.
     * 
     * @param minTicks Minimum ticks since cache creation
     * @return Stream of objects aged at least minTicks
     */
    public static Stream<Rs2ObjectModel> findByMinAge(int minTicks) {
        return findAll(obj -> obj.getTicksSinceCreation() >= minTicks);
    }

    /**
     * Finds objects that have been cached for less than the specified number of ticks.
     * 
     * @param maxTicks Maximum ticks since cache creation
     * @return Stream of fresh objects
     */
    public static Stream<Rs2ObjectModel> findFresh(int maxTicks) {
        return findAll(obj -> obj.getTicksSinceCreation() <= maxTicks);
    }

    /**
     * Finds the oldest cached object.
     * 
     * @return Optional containing the oldest cached object
     */
    public static Optional<Rs2ObjectModel> findOldest() {
        return getAll().max(Comparator.comparingInt(Rs2ObjectModel::getTicksSinceCreation));
    }

    /**
     * Finds the newest cached object.
     * 
     * @return Optional containing the newest cached object
     */
    public static Optional<Rs2ObjectModel> findNewest() {
        return getAll().min(Comparator.comparingInt(Rs2ObjectModel::getTicksSinceCreation));
    }

    // ============================================
    // Scene and Viewport Extraction Methods
    // ============================================

    /**
     * Gets all objects currently in the scene (all cached objects).
     * This includes objects that may not be visible in the current viewport.
     * 
     * @return Stream of all objects in the scene
     */
    public static Stream<Rs2ObjectModel> getAllInScene() {
        return getAll();
    }

    /**
     * Gets all objects currently visible in the viewport (on screen).
     * Only includes objects that have a convex hull and are rendered.
     * 
     * @return Stream of objects visible in viewport
     */
    public static Stream<Rs2ObjectModel> getAllInViewport() {
        return filterVisibleInViewport(getAll());
    }

    /**
     * Gets all objects by ID that are currently visible in the viewport.
     * 
     * @param objectId The object ID to filter by
     * @return Stream of objects with the specified ID that are visible in viewport
     */
    public static Stream<Rs2ObjectModel> getAllInViewport(int objectId) {
        return filterVisibleInViewport(getByGameId(objectId));
    }

    /**
     * Gets the closest object in the viewport by ID.
     * 
     * @param objectId The object ID
     * @return Optional containing the closest object in viewport
     */
    public static Optional<Rs2ObjectModel> getClosestInViewport(int objectId) {
        return getAllInViewport(objectId)
                .min(Comparator.comparingInt(Rs2ObjectModel::getDistanceFromPlayer));
    }

    /**
     * Gets all objects in the viewport that are interactable (within reasonable distance).
     * 
     * @param maxDistance Maximum distance for interaction
     * @return Stream of interactable objects in viewport
     */
    public static Stream<Rs2ObjectModel> getAllInteractable(int maxDistance) {
        return getAllInViewport()
                .filter(obj -> isInteractable(obj, maxDistance));
    }

    /**
     * Gets all objects by ID in the viewport that are interactable.
     * 
     * @param objectId The object ID
     * @param maxDistance Maximum distance for interaction
     * @return Stream of interactable objects with the specified ID
     */
    public static Stream<Rs2ObjectModel> getAllInteractable(int objectId, int maxDistance) {
        return getAllInViewport(objectId)
                .filter(obj -> isInteractable(obj, maxDistance));
    }

    /**
     * Gets the closest interactable object by ID.
     * 
     * @param objectId The object ID
     * @param maxDistance Maximum distance for interaction
     * @return Optional containing the closest interactable object
     */
    public static Optional<Rs2ObjectModel> getClosestInteractable(int objectId, int maxDistance) {
        return getAllInteractable(objectId, maxDistance)
                .min(Comparator.comparingInt(Rs2ObjectModel::getDistanceFromPlayer));
    }

    // ============================================
    // Line of Sight Methods
    // ============================================

    
    /**
     * Finds objects that intersect the line of sight between two points.
     * This is useful for determining what objects are blocking visibility.
     * 
     * @param from Starting world point
     * @param to Destination world point
     * @return Stream of objects that might block line of sight
     */
    public static Stream<Rs2ObjectModel> getObjectsInLineOfSight(WorldPoint from, WorldPoint to) {
        if (from == null || to == null || from.getPlane() != to.getPlane()) {
            return Stream.empty();
        }
        
        // Calculate the bounding box that contains both points
        int minX = Math.min(from.getX(), to.getX());
        int maxX = Math.max(from.getX(), to.getX());
        int minY = Math.min(from.getY(), to.getY());
        int maxY = Math.max(from.getY(), to.getY());
        int plane = from.getPlane();
        
        // Add a small buffer to ensure we catch all relevant objects
        int buffer = 3;
        
        // Get all objects in the bounding box region
        return getAll()
            .filter(obj -> {
                WorldPoint objPoint = obj.getWorldLocation();
                if (objPoint == null) return false;
                
                // Check if in bounding box with buffer
                return objPoint.getPlane() == plane &&
                       objPoint.getX() >= (minX - buffer) && objPoint.getX() <= (maxX + buffer) &&
                       objPoint.getY() >= (minY - buffer) && objPoint.getY() <= (maxY + buffer);
            })
            // Filter to objects that actually intersect the line
            .filter(obj -> intersectsLine(obj, from, to));
    }
    
    /**
     * Determines if an object intersects the line between two points.
     * Uses the object's size and position to check for intersection.
     * 
     * @param obj The object to check
     * @param from Starting world point
     * @param to Destination world point
     * @return true if the object intersects the line
     */
    private static boolean intersectsLine(Rs2ObjectModel obj, WorldPoint from, WorldPoint to) {
        // For simplicity, consider the object as a rectangle/square
        // and check if the line intersects this rectangle
        WorldPoint objLocation = obj.getLocation();
        if (objLocation == null) return false;
        
        // Get object dimensions (default to 1x1)
        int sizeX = 1;
        int sizeY = 1;
        
        // Try to get actual dimensions if it's a GameObject
        if (obj.getObjectType() == Rs2ObjectModel.ObjectType.GAME_OBJECT && obj.getTileObject() instanceof GameObject) {
            GameObject gameObject = (GameObject) obj.getTileObject();
            sizeX = gameObject.getSceneMinLocation().distanceTo(gameObject.getSceneMaxLocation()) + 1;
            sizeY = sizeX; // Assume square for simplicity
        }
        
        // Create a WorldArea representing the object
        net.runelite.api.coords.WorldArea objArea = new net.runelite.api.coords.WorldArea(objLocation, sizeX, sizeY);
        
        // Use the line-of-sight method to check if this area blocks the line
        // First check if from point has line of sight to object
        boolean fromToObjLOS = new net.runelite.api.coords.WorldArea(from, 1, 1)
                .hasLineOfSightTo(net.runelite.client.plugins.microbot.Microbot.getClient().getTopLevelWorldView(), objArea);
                
        // Then check if object has line of sight to destination
        boolean objToToLOS = objArea
                .hasLineOfSightTo(net.runelite.client.plugins.microbot.Microbot.getClient().getTopLevelWorldView(), 
                                  new net.runelite.api.coords.WorldArea(to, 1, 1));
                                  
        // Object intersects line if both checks pass
        return fromToObjLOS && objToToLOS;
    }

    // ============================================
    // Line of Sight Utilities
    // ============================================
    
    /**
     * Checks if there is a line of sight between the player and a game object.
     * Uses RuneLite's WorldArea.hasLineOfSightTo for accurate scene collision detection.
     * 
     * @param object The object to check
     * @return True if line of sight exists, false otherwise
     */
    public static boolean hasLineOfSight(Rs2ObjectModel object) {
        if (object == null) return false;
        
        try {
            // Get player's current world location and create a small area (1x1)
            WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
            WorldPoint objectLocation = object.getLocation();
            
            // Check same plane
            if (playerLocation.getPlane() != objectLocation.getPlane()) {
                return false;
            }
            
            // For GameObjects, use the actual size of the object
            if (object.getObjectType() == Rs2ObjectModel.ObjectType.GAME_OBJECT &&
                object.getTileObject() instanceof GameObject) {
                GameObject gameObject = (GameObject) object.getTileObject();
                
                return new WorldArea(
                        objectLocation,
                        gameObject.sizeX(),
                        gameObject.sizeY())
                        .hasLineOfSightTo(
                                Microbot.getClient().getTopLevelWorldView(),
                                new WorldArea(playerLocation, 1, 1));
            } else {
                // For other objects, use 1x1 area as default
                return new WorldArea(objectLocation, 1, 1)
                        .hasLineOfSightTo(
                                Microbot.getClient().getTopLevelWorldView(),
                                new WorldArea(playerLocation, 1, 1));
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Checks if there is a line of sight between a specific point and a game object.
     * 
     * @param point The world point to check from
     * @param object The object to check against
     * @return True if line of sight exists, false otherwise
     */
    public static boolean hasLineOfSight(WorldPoint point, Rs2ObjectModel object) {
        if (object == null || point == null) return false;
        
        try {
            WorldPoint objectLocation = object.getLocation();
            
            // Check same plane
            if (point.getPlane() != objectLocation.getPlane()) {
                return false;
            }
            
            // For GameObjects, use the actual size of the object
            if (object.getObjectType() == Rs2ObjectModel.ObjectType.GAME_OBJECT &&
                object.getTileObject() instanceof GameObject) {
                GameObject gameObject = (GameObject) object.getTileObject();
                
                return new WorldArea(
                        objectLocation,
                        gameObject.sizeX(),
                        gameObject.sizeY())
                        .hasLineOfSightTo(
                                Microbot.getClient().getTopLevelWorldView(),
                                new WorldArea(point, 1, 1));
            } else {
                // For other objects, use 1x1 area as default
                return new WorldArea(objectLocation, 1, 1)
                        .hasLineOfSightTo(
                                Microbot.getClient().getTopLevelWorldView(),
                                new WorldArea(point, 1, 1));
            }
        } catch (Exception e) {
            return false;
        }
    }
    
    /**
     * Gets all objects that have line of sight to the player.
     * Useful for identifying interactive game objects.
     * 
     * @return Stream of objects with line of sight to player
     */
    public static Stream<Rs2ObjectModel> getObjectsWithLineOfSightToPlayer() {
        return getAll().filter(Rs2ObjectCacheUtils::hasLineOfSight);
    }
    
    /**
     * Gets all objects that have line of sight to a specific world point.
     * 
     * @param point The world point to check from
     * @return Stream of objects with line of sight to the point
     */
    public static Stream<Rs2ObjectModel> getObjectsWithLineOfSightTo(WorldPoint point) {
        return getAll().filter(object -> hasLineOfSight(point, object));
    }
    
    /**
     * Gets all objects at a location that have line of sight to the player.
     * 
     * @param worldPoint The world point to check at
     * @param maxDistance Maximum distance from the world point
     * @return Stream of objects at the location with line of sight
     */
    public static Stream<Rs2ObjectModel> getObjectsAtLocationWithLineOfSight(WorldPoint worldPoint, int maxDistance) {
        return getAll()
                .filter(object -> object.getLocation().distanceTo(worldPoint) <= maxDistance)
                .filter(Rs2ObjectCacheUtils::hasLineOfSight);
    }

    // ============================================
    // Viewport Visibility and Interactability Utilities
    // ============================================

    /**
     * Checks if any type of game object is visible in the current viewport using enhanced detection.
     * Supports all TileObject subtypes: GameObject, WallObject, GroundObject, DecorativeObject.
     * Uses client thread for safe access to viewport bounds, canvas coordinates, and object rendering data.
     * 
     * @param objectModel The object model to check (supports all object types)
     * @return true if the object is visible on screen
     */
    public static boolean isVisibleInViewport(Rs2ObjectModel objectModel) {
        try {
            if (objectModel == null || objectModel.getTileObject() == null) {
                return false;
            }

            TileObject tileObject = objectModel.getTileObject();
            
            // Use client thread for safe access to viewport and canvas coordinates
            Boolean result = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                Client client = Microbot.getClient();
                if (client == null) {
                    return false;
                }
                
                // Get canvas location (screen coordinates) for the object
                Point canvasLocation = tileObject.getCanvasLocation();
                if (canvasLocation == null) {
                    //return false; // Object is not visible (behind camera, too far, etc.)
                }
                
                // Check if canvas coordinates are within viewport bounds
                if (!isPointInViewport(client, canvasLocation)) {
                    //return false; // Object is outside visible screen area
                }
                
                // Enhanced visibility checks for better accuracy
                // First check convex hull (fast geometric check)
                Shape hull = getObjectConvexHull(tileObject);
                if (hull == null) {
                    // If no hull available, the basic canvas location check is sufficient
                    return true;
                }
                
                // Check if convex hull intersects with viewport
                Rectangle viewportBounds = getViewportBounds(client);
                if (!hull.intersects(viewportBounds)) {
                  //  return false; // Hull doesn't intersect viewport
                }
                
                // For maximum accuracy, check model visibility (optional enhanced check)
                return isObjectModelVisible(tileObject);
            }).orElse(false);
            
            return result;
            
        } catch (Exception e) {
            // Log error for debugging but don't spam logs
            if (Math.random() < 0.001) { // Log ~0.1% of errors to avoid spam
                log.debug("Error checking viewport visibility for object {}: {}", 
                         objectModel.getId(), e.getMessage());
            }
            return false;
        }
    }

    /**
     * Checks if a canvas point is within the current viewport bounds.
     * 
     * @param client The game client
     * @param point The canvas point to check
     * @return true if the point is within viewport bounds
     */
    private static boolean isPointInViewport(Client client, Point point) {
        if (point == null) {
            return false;
        }
        
        int viewportX = client.getViewportXOffset();
        int viewportY = client.getViewportYOffset();
        int viewportWidth = client.getViewportWidth();
        int viewportHeight = client.getViewportHeight();
        
        return point.getX() >= viewportX && 
               point.getX() <= (viewportX + viewportWidth) &&
               point.getY() >= viewportY && 
               point.getY() <= (viewportY + viewportHeight);
    }

    /**
     * Gets the viewport bounds as a Rectangle.
     * 
     * @param client The game client
     * @return Rectangle representing the viewport bounds
     */
    private static Rectangle getViewportBounds(Client client) {
        return new Rectangle(
            client.getViewportXOffset(),
            client.getViewportYOffset(), 
            client.getViewportWidth(),
            client.getViewportHeight()
        );
    }

    /**
     * Gets the convex hull for any type of tile object.
     * Based on ObjectIndicatorsOverlay and VisibilityHelper patterns.
     * 
     * @param tileObject The tile object
     * @return The convex hull shape, or null if not visible
     */
    public static Shape getObjectConvexHull(Object tileObject) {
        if (tileObject instanceof GameObject) {
            return ((GameObject) tileObject).getConvexHull();
        } else if (tileObject instanceof GroundObject) {
            return ((GroundObject) tileObject).getConvexHull();
        } else if (tileObject instanceof DecorativeObject) {
            return ((DecorativeObject) tileObject).getConvexHull();
        } else if (tileObject instanceof WallObject) {
            return ((WallObject) tileObject).getConvexHull();
        } else if (tileObject instanceof TileObject) {
            return ((TileObject) tileObject).getCanvasTilePoly();
        }
        return null;
    }

    /**
     * Checks if an object's model has visible triangles (enhanced visibility check).
     * Based on VisibilityHelper approach - checks model triangle transparency.
     * 
     * @param tileObject The tile object to check
     * @return true if the object has visible model triangles
     */
    public static boolean isObjectModelVisible(Object tileObject) {
        try {
            return checkObjectModelVisibility(tileObject);
        } catch (Exception e) {
            return true; // Default to visible on error
        }
    }

    /**
     * Performs the actual model visibility check for different object types.
     * 
     * @param tileObject The tile object to check
     * @return true if visible triangles are found
     */
    private static boolean checkObjectModelVisibility(Object tileObject) {
        if (tileObject instanceof GameObject) {
            Model model = extractModel(((GameObject) tileObject).getRenderable());
            return modelHasVisibleTriangles(model);
        } else if (tileObject instanceof GroundObject) {
            Model model = extractModel(((GroundObject) tileObject).getRenderable());
            return modelHasVisibleTriangles(model);
        } else if (tileObject instanceof DecorativeObject) {
            DecorativeObject decoObj = ((DecorativeObject) tileObject);
            Model model1 = extractModel(decoObj.getRenderable());
            Model model2 = extractModel(decoObj.getRenderable2());
            return modelHasVisibleTriangles(model1) || modelHasVisibleTriangles(model2);
        } else if (tileObject instanceof WallObject) {
            WallObject wallObj = ((WallObject) tileObject);
            Model model1 = extractModel(wallObj.getRenderable1());
            Model model2 = extractModel(wallObj.getRenderable2());
            return modelHasVisibleTriangles(model1) || modelHasVisibleTriangles(model2);
        }
        return true; // Default to visible for unknown types
    }

    /**
     * Extracts a Model from a Renderable object.
     * 
     * @param renderable The renderable object
     * @return The model, or null if not available
     */
    private static Model extractModel(Renderable renderable) {
        if (renderable == null) {
            return null;
        }
        return renderable instanceof Model ? (Model) renderable : renderable.getModel();
    }

    /**
     * Checks if a model has visible triangles by examining transparency.
     * 
     * @param model The model to check
     * @return true if visible triangles are found
     */
    private static boolean modelHasVisibleTriangles(Model model) {
        if (model == null) {
            return false;
        }
        
        byte[] triangleTransparencies = model.getFaceTransparencies();
        int triangleCount = model.getFaceCount();
        
        if (triangleTransparencies == null) {
            return true; // No transparency data means visible
        }
        
        // Check if any triangle is not fully transparent (255 = fully transparent)
        for (int i = 0; i < triangleCount; i++) {
            if ((triangleTransparencies[i] & 255) < 254) {
                return true;
            }
        }
        return false;
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
     * Filters a stream of objects to only include those visible in viewport.
     * 
     * @param objectStream Stream of objects to filter
     * @return Stream of objects visible in viewport
     */
    public static Stream<Rs2ObjectModel> filterVisibleInViewport(Stream<Rs2ObjectModel> objectStream) {
        return objectStream.filter(Rs2ObjectCacheUtils::isVisibleInViewport);
    }

    /**
     * Checks if an object is interactable (visible and within reasonable distance).
     * 
     * @param objectModel The object to check
     * @param maxDistance Maximum distance in tiles for interaction
     * @return true if the object is interactable
     */
    public static boolean isInteractable(Rs2ObjectModel objectModel, int maxDistance) {
        try {
            if (objectModel == null) {
                return false;
            }

            // Check if visible in viewport first
            if (!isVisibleInViewport(objectModel)) {
                return false;
            }

            // Check distance from player
            return objectModel.getDistanceFromPlayer() <= maxDistance;
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
     * Gets all objects visible in the viewport.
     * 
     * @return Stream of objects visible in viewport
     */
    public static Stream<Rs2ObjectModel> getVisibleInViewport() {
        return filterVisibleInViewport(getAll());
    }

    /**
     * Gets objects by ID that are visible in the viewport.
     * 
     * @param objectId The object ID
     * @return Stream of objects with the specified ID visible in viewport
     */
    public static Stream<Rs2ObjectModel> getVisibleInViewportById(int objectId) {
        return filterVisibleInViewport(getByGameId(objectId));
    }

    /**
     * Finds interactable objects by ID within distance from player.
     * 
     * @param objectId The object ID
     * @param maxDistance Maximum distance in tiles
     * @return Stream of interactable objects with the specified ID
     */
    public static Stream<Rs2ObjectModel> findInteractableById(int objectId, int maxDistance) {
        return getByGameId(objectId)
                .filter(obj -> isInteractable(obj, maxDistance));
    }

    /**
     * Finds interactable objects by name within distance from player.
     * 
     * @param name The object name
     * @param maxDistance Maximum distance in tiles
     * @return Stream of interactable objects with the specified name
     */
    public static Stream<Rs2ObjectModel> findInteractableByName(String name, int maxDistance) {

        return findAll(nameMatches(name, false))
                .filter(obj -> isInteractable(obj, maxDistance));
    }
}
