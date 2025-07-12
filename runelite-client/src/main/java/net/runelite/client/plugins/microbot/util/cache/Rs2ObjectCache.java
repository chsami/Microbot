package net.runelite.client.plugins.microbot.util.cache;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.strategy.entity.ObjectUpdateStrategy;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2ObjectModel;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2ObjectModel.ObjectType;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Thread-safe cache for tracking game objects using the unified cache architecture.
 * Handles GameObject, GroundObject, WallObject, and DecorativeObject types.
 * Returns Rs2ObjectModel objects for enhanced object handling.
 * Uses EVENT_DRIVEN_ONLY mode to persist objects until despawn or game state changes.
 * 
 * This class extends Rs2Cache and provides specific object caching functionality
 * with proper EventBus integration for @Subscribe methods.
 * 
 * Key Changes:
 * - Uses String-based keys for better tracking across region changes
 * - Implements region change detection to clear stale objects
 * - Handles the fact that RuneLite doesn't fire despawn events on region changes
 */
@Slf4j
public class Rs2ObjectCache extends Rs2Cache<String, Rs2ObjectModel> {
    
    private static Rs2ObjectCache instance;
    
    // Track current regions to detect changes and clear stale objects
    private static int[] lastKnownRegions = null;
    
    // Reference to the update strategy for scene scanning
    private ObjectUpdateStrategy updateStrategy;
    
    /**
     * Private constructor for singleton pattern.
     */
    private Rs2ObjectCache() {
        super("ObjectCache", CacheMode.EVENT_DRIVEN_ONLY);
        this.updateStrategy = new ObjectUpdateStrategy();
        this.withUpdateStrategy(this.updateStrategy);
        
        log.debug("Rs2ObjectCache initialized with String-based keys, region change detection, and scene scanning");
    }
    
    /**
     * Gets the singleton instance of Rs2ObjectCache.
     * 
     * @return The singleton object cache instance
     */
    public static synchronized Rs2ObjectCache getInstance() {
        if (instance == null) {
            instance = new Rs2ObjectCache();
        }
        return instance;
    }
    
    /**
     * Requests an scene scan to be performed when appropriate.
     * This is more efficient than immediate scanning.
     */
    public static void requestSceneScan() {
        getInstance().updateStrategy.requestSceneScan();
    }
    
    /**
     * Forces an immediate scene scan to populate the cache.
     * Use this when you need to ensure the cache is fully populated.
     */
    public static void forceSceneScan() {
        getInstance().updateStrategy.performSceneScan(getInstance(), true);
    }
    
    /**
     * Overrides the get method to provide fallback scene scanning when cache is empty or key not found.
     * This ensures that even if events are missed, we can still retrieve objects from the scene.
     * 
     * @param key The unique String key for the object
     * @return The object model if found in cache or scene, null otherwise
     */
    @Override
    public Rs2ObjectModel get(String key) {
        // First try the regular cache lookup
        Rs2ObjectModel cachedResult = super.get(key);
        if (cachedResult != null) {
            return cachedResult;
        }
        if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
            log.warn("Client or local player is null, cannot perform scene scan");
            return null;
        }
        // If not in cache and cache is very small, request and perform scene scan
        if (updateStrategy.shouldPerformSceneScan(this)) {
            log.info("Cache miss for key '{}' and cache is small (size: {}), performing scene scan", key, this.size());
            //updateStrategy.performSceneScan(this, false);
            
            // Try again after scene scan
            return super.get(key);
        }
        
        return null;
    }
    
    /**
     * Updates the cache by scanning the entire scene for all tile objects.
     * This is used as a fallback when events are missed or on cache initialization.
     * 
     * @deprecated Use {@link #forceSceneScan()} instead for better performance
     */
    @Deprecated
    public static void updateCacheFromSceneScan() {
        log.debug("Legacy updateCacheFromSceneScan called - using scene scan");
        forceSceneScan();
    }
    
    /**
     * Retrieves all tile objects from the scene and returns them as Rs2ObjectModel objects.
     * This method scans the entire scene and returns all objects without adding them to the cache.
     * 
     * @return List of all objects currently in the scene
     */
    public static List<Rs2ObjectModel> getAllObjectsFromScene() {
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return Collections.emptyList();
        }
        
        Scene scene = player.getWorldView().getScene();
        if (scene == null) {
            return Collections.emptyList();
        }
        
        Tile[][][] tiles = scene.getTiles();
        if (tiles == null) {
            return Collections.emptyList();
        }
        
        List<Rs2ObjectModel> allObjects = new ArrayList<>();
        int z = player.getWorldView().getPlane();
        
        for (int x = 0; x < Constants.SCENE_SIZE; x++) {
            for (int y = 0; y < Constants.SCENE_SIZE; y++) {
                Tile tile = tiles[z][x][y];
                if (tile == null) continue;
                
                // Check GameObjects
                GameObject[] gameObjects = tile.getGameObjects();
                if (gameObjects != null) {
                    for (GameObject gameObject : gameObjects) {
                        if (gameObject == null) continue;
                        
                        // Only add if it's the primary location for multi-tile objects
                        if (gameObject.getSceneMinLocation().equals(tile.getSceneLocation())) {
                            allObjects.add(new Rs2ObjectModel(gameObject, tile));
                        }
                    }
                }
                
                // Check GroundObject
                GroundObject groundObject = tile.getGroundObject();
                if (groundObject != null) {
                    allObjects.add(new Rs2ObjectModel(groundObject, tile));
                }
                
                // Check WallObject
                WallObject wallObject = tile.getWallObject();
                if (wallObject != null) {
                    allObjects.add(new Rs2ObjectModel(wallObject, tile));
                }
                
                // Check DecorativeObject
                DecorativeObject decorativeObject = tile.getDecorativeObject();
                if (decorativeObject != null) {
                    allObjects.add(new Rs2ObjectModel(decorativeObject, tile));
                }
            }
        }
        
        return allObjects;
    }
    
    /**
     * Gets an object by its unique String-based key.
     * 
     * @param key The unique String key for the object
     * @return Optional containing the object model if found
     */
    public static Optional<Rs2ObjectModel> getObjectByKey(String key) {
        return Optional.ofNullable(getInstance().get(key));
    }
    
    /**
     * Gets all objects matching a specific ID.
     * 
     * @param objectId The object ID to search for
     * @return Stream of matching Rs2ObjectModel objects
     */
    public static Stream<Rs2ObjectModel> getObjectsById(int objectId) {
        return getInstance().stream()
                .filter(obj -> obj.getId() == objectId);
    }
    
    /**
     * Gets all objects matching a specific name (case-insensitive).
     * 
     * @param name The object name to search for
     * @return Stream of matching Rs2ObjectModel objects
     */
    public static Stream<Rs2ObjectModel> getObjectsByName(String name) {
        return getInstance().stream()
                .filter(obj -> obj.getName() != null && 
                              obj.getName().toLowerCase().contains(name.toLowerCase()));
    }
    
    /**
     * Gets all objects of a specific type.
     * 
     * @param objectType The type of objects to search for
     * @return Stream of matching Rs2ObjectModel objects
     */
    public static Stream<Rs2ObjectModel> getObjectsByType(ObjectType objectType) {
        return getInstance().stream()
                .filter(obj -> obj.getObjectType() == objectType);
    }
    
    /**
     * Gets all objects within a certain distance from a location.
     * 
     * @param location The center location
     * @param maxDistance The maximum distance in tiles
     * @return Stream of objects within the specified distance
     */
    public static Stream<Rs2ObjectModel> getObjectsWithinDistance(WorldPoint location, int maxDistance) {
        return getInstance().stream()
                .filter(obj -> obj.getWorldLocation() != null &&
                              obj.getWorldLocation().distanceTo(location) <= maxDistance);
    }
    
    /**
     * Gets the first object matching the specified ID.
     * 
     * @param objectId The object ID
     * @return Optional containing the first matching object model
     */
    public static Optional<Rs2ObjectModel> getFirstObjectById(int objectId) {
        return getObjectsById(objectId).findFirst();
    }
    
    /**
     * Gets the first object matching the specified name.
     * 
     * @param name The object name
     * @return Optional containing the first matching object model
     */
    public static Optional<Rs2ObjectModel> getFirstObjectByName(String name) {
        return getObjectsByName(name).findFirst();
    }
 
    
    
    /**
     * Gets all objects - Legacy compatibility method.
     * 
     * @return Stream of all objects
     */
    public static Stream<Rs2ObjectModel> getAllObjects() {
        return getInstance().values().stream();
    }
    
    /**
     * Gets all GameObjects from the cache.
     * 
     * @return Stream of GameObject models
     */
    public static Stream<Rs2ObjectModel> getGameObjects() {
        return getObjectsByType(ObjectType.GAME_OBJECT);
    }
    
    /**
     * Gets all GroundObjects from the cache.
     * 
     * @return Stream of GroundObject models
     */
    public static Stream<Rs2ObjectModel> getGroundObjects() {
        return getObjectsByType(ObjectType.GROUND_OBJECT);
    }
    
    /**
     * Gets all WallObjects from the cache.
     * 
     * @return Stream of WallObject models
     */
    public static Stream<Rs2ObjectModel> getWallObjects() {
        return getObjectsByType(ObjectType.WALL_OBJECT);
    }
    
    /**
     * Gets all DecorativeObjects from the cache.
     * 
     * @return Stream of DecorativeObject models
     */
    public static Stream<Rs2ObjectModel> getDecorativeObjects() {
        return getObjectsByType(ObjectType.DECORATIVE_OBJECT);
    }
    
    /**
     * Gets the closest object to the player with the specified ID.
     * 
     * @param objectId The object ID to search for
     * @return Optional containing the closest object
     */
    public static Optional<Rs2ObjectModel> getClosestObjectById(int objectId) {
        return getClosestObjectById(objectId, Microbot.getClient().getLocalPlayer().getWorldLocation());
    }
    
    /**
     * Gets the closest object to the player with the specified name.
     * 
     * @param name The object name to search for
     * @return Optional containing the closest object
     */
    public static Optional<Rs2ObjectModel> getClosestObjectByName(String name) {
        return getClosestObjectByName(name, Microbot.getClient().getLocalPlayer().getWorldLocation());
    }
    
    /**
     * Gets the closest object to a specific anchor point with the specified ID.
     * 
     * @param objectId The object ID to search for
     * @param anchorPoint The anchor point to calculate distance from
     * @return Optional containing the closest object
     */
    public static Optional<Rs2ObjectModel> getClosestObjectById(int objectId, WorldPoint anchorPoint) {
        return getObjectsById(objectId)
            .min((o1, o2) -> Integer.compare(
                o1.getWorldLocation().distanceTo(anchorPoint),
                o2.getWorldLocation().distanceTo(anchorPoint)
            ));
    }
    
    /**
     * Gets the closest object to a specific anchor point with the specified name.
     * 
     * @param name The object name to search for
     * @param anchorPoint The anchor point to calculate distance from
     * @return Optional containing the closest object
     */
    public static Optional<Rs2ObjectModel> getClosestObjectByName(String name, WorldPoint anchorPoint) {
        return getObjectsByName(name)
            .min((o1, o2) -> Integer.compare(
                o1.getWorldLocation().distanceTo(anchorPoint),
                o2.getWorldLocation().distanceTo(anchorPoint)
            ));
    }
    
    /**
     * Gets objects sorted by distance from player (closest first).
     * 
     * @return List of objects sorted by distance ascending
     */
    public static List<Rs2ObjectModel> getObjectsSortedByDistance() {
        WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
        return getInstance().values().stream()
            .sorted((o1, o2) -> Integer.compare(
                o1.getWorldLocation().distanceTo(playerLocation),
                o2.getWorldLocation().distanceTo(playerLocation)
            ))
            .collect(Collectors.toList());
    }
    
    /**
     * Gets the total number of cached objects.
     * 
     * @return The total object count
     */
    public static int getObjectCount() {
        return getInstance().size();
    }
    
    /**
     * Gets the total number of objects by ID.
     * 
     * @param objectId The object ID to count
     * @return The count of objects with the specified ID
     */
    public static long getObjectCountById(int objectId) {
        return getObjectsById(objectId).count();
    }
    
    /**
     * Gets the count of objects by type.
     * 
     * @param objectType The object type to count
     * @return The count of objects with the specified type
     */
    public static long getObjectCountByType(ObjectType objectType) {
        return getObjectsByType(objectType).count();
    }
    
   
    
    /**
     * Forces a cache refresh by performing a scene scan.
     * This is useful when you suspect the cache is out of sync with the actual scene.
     */
    public static void forceRefresh() {
        log.debug("Forcing cache refresh via scene scan");
        forceSceneScan();
    }
    
    /**
     * Manually adds a GameObject to the cache.
     * 
     * @param gameObject The game object to add
     * @param tile The tile containing the object
     */
    private static void addGameObject(GameObject gameObject, Tile tile) {
        if (gameObject != null && tile != null) {
            String key = ObjectUpdateStrategy.generateCacheIdForObject(gameObject, tile);
            Rs2ObjectModel objectModel = new Rs2ObjectModel(gameObject, tile);
            getInstance().put(key, objectModel);
            log.debug("Manually added GameObject: {} at {} (key: {})", gameObject.getId(), gameObject.getWorldLocation(), key);
        }
    }
    
    /**
     * Manually adds a GroundObject to the cache.
     * 
     * @param groundObject The ground object to add
     * @param tile The tile containing the object
     */
    private static void addGroundObject(GroundObject groundObject, Tile tile) {
        if (groundObject != null && tile != null) {
            String key = ObjectUpdateStrategy.generateCacheIdForObject(groundObject, tile);
            Rs2ObjectModel objectModel = new Rs2ObjectModel(groundObject, tile);
            getInstance().put(key, objectModel);
            log.debug("Manually added GroundObject: {} at {} (key: {})", groundObject.getId(), groundObject.getWorldLocation(), key);
        }
    }
    
    /**
     * Manually adds a WallObject to the cache.
     * 
     * @param wallObject The wall object to add
     * @param tile The tile containing the object
     */
    private static void addWallObject(WallObject wallObject, Tile tile) {
        if (wallObject != null && tile != null) {
            String key = ObjectUpdateStrategy.generateCacheIdForObject(wallObject, tile);
            Rs2ObjectModel objectModel = new Rs2ObjectModel(wallObject, tile);
            getInstance().put(key, objectModel);
            log.debug("Manually added WallObject: {} at {} (key: {})", wallObject.getId(), wallObject.getWorldLocation(), key);
        }
    }
    
    /**
     * Manually adds a DecorativeObject to the cache.
     * 
     * @param decorativeObject The decorative object to add
     * @param tile The tile containing the object
     */
    private static void addDecorativeObject(DecorativeObject decorativeObject, Tile tile) {
        if (decorativeObject != null && tile != null) {
            String key = ObjectUpdateStrategy.generateCacheIdForObject(decorativeObject, tile);
            Rs2ObjectModel objectModel = new Rs2ObjectModel(decorativeObject, tile);
            getInstance().put(key, objectModel);
            log.debug("Manually added DecorativeObject: {} at {} (key: {})", decorativeObject.getId(), decorativeObject.getWorldLocation(), key);
        }
    }
    
    /**
     * Manually removes an object from the cache.
     * 
     * @param objectModel The object model to remove
     */
    private static void removeObject(Rs2ObjectModel objectModel) {
        if (objectModel == null) {
            log.warn("Attempted to remove null object from cache");
            return;
        }
        
        // Generate the key based on object properties
        String key = ObjectUpdateStrategy.generateCacheIdForObject(objectModel.getTileObject(),objectModel.getTile());
        
        getInstance().remove(key);
        log.debug("Manually removed object with key: {}", key);
    }
    
    /**
     * Invalidates all object cache entries.
     */
    public static void invalidateAllObjectsAndScanScene() {
        getInstance().invalidateAll();
        forceSceneScan();
        log.debug("Invalidated all object cache entries");
    }
    
  
    
    /**
     * Checks for region changes and clears cache if regions have changed.
     * This handles the issue where RuneLite doesn't fire despawn events on region changes.
     */
    private static void checkAndHandleRegionChange() {
        Client client = Microbot.getClient();
        if (client == null) return;
        
        @SuppressWarnings("deprecation")
        int[] currentRegions = client.getMapRegions();
        if (currentRegions == null) return;
        
        // Check if regions have changed
        if (lastKnownRegions == null || !Arrays.equals(lastKnownRegions, currentRegions)) {
            if (lastKnownRegions != null) {
                log.debug("Region change detected - clearing object cache. Old regions: {}, New regions: {}", 
                    Arrays.toString(lastKnownRegions), Arrays.toString(currentRegions));
                getInstance().invalidateAll();
            }
            lastKnownRegions = currentRegions.clone();
        }
    }
    
    /**
     * Event handler registration for the unified cache.
     * The unified cache handles events through its strategy automatically.
     * These handlers now focus on region change detection and strategy coordination.
     */

    @Subscribe
    public void onGameObjectSpawned(final GameObjectSpawned event) {
        checkAndHandleRegionChange();
        getInstance().handleEvent(event);
    }
    
    @Subscribe
    public void onGameObjectDespawned(final GameObjectDespawned event) {
        getInstance().handleEvent(event);
    }
    
    @Subscribe
    public void onGroundObjectSpawned(final GroundObjectSpawned event) {
        checkAndHandleRegionChange();
        getInstance().handleEvent(event);
    }
    
    @Subscribe
    public void onGroundObjectDespawned(final GroundObjectDespawned event) {
        getInstance().handleEvent(event);
    }
    
    @Subscribe
    public void onWallObjectSpawned(final WallObjectSpawned event) {
        checkAndHandleRegionChange();
        getInstance().handleEvent(event);
    }
    
    @Subscribe
    public void onWallObjectDespawned(final WallObjectDespawned event) {
        getInstance().handleEvent(event);
    }
    
    @Subscribe
    public void onDecorativeObjectSpawned(final DecorativeObjectSpawned event) {
        checkAndHandleRegionChange();
        getInstance().handleEvent(event);
    }
    
    @Subscribe
    public void onDecorativeObjectDespawned(final DecorativeObjectDespawned event) {
        getInstance().handleEvent(event);
    }
    
    @Subscribe
    public void onGameStateChanged(final GameStateChanged event) {
        switch (event.getGameState()) {
            case LOGGED_IN:
                // Check for region change when logging in or transitioning areas
                checkAndHandleRegionChange();
                break;
            case LOGIN_SCREEN:
            case CONNECTION_LOST:
                // Clear cache when logging out
                log.debug("Player logging out, clearing object cache");
                invalidateAll();
                lastKnownRegions = null;
                break;
            default:
                break;
        }
        
        // Also let the strategy handle the event
        getInstance().handleEvent(event);
    }
    
    @Subscribe
    public void onGameTick(final GameTick event) {
        // Periodically check for region changes to catch any missed transitions
        checkAndHandleRegionChange();
        
        // Let the strategy handle intelligent scanning
        getInstance().handleEvent(event);
    }
    
    /**
     * Resets the singleton instance. Used for testing.
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.invalidateAll();
            instance = null;
            lastKnownRegions = null;
            log.debug("Rs2ObjectCache instance reset");
        }
    }
    
    /**
     * Gets cache mode - Legacy compatibility method.
     * 
     * @return The cache mode
     */
    public static CacheMode getObjectCacheMode() {
        return getInstance().getCacheMode();
    }
    
    /**
     * Gets cache statistics - Legacy compatibility method.
     * 
     * @return Statistics string for debugging
     */
    public static String getObjectCacheStatistics() {
        Rs2ObjectCache cache = getInstance();
        return String.format("ObjectCache Stats - Size: %d, Mode: %s, Regions: %s", 
            cache.size(), 
            cache.getCacheMode(),
            lastKnownRegions != null ? Arrays.toString(lastKnownRegions) : "null");
    }
    
    /**
     * Gets object type statistics for display in overlays.
     * 
     * @return Statistics string with object type counts
     */
    public static String getObjectTypeStatistics() {
        Rs2ObjectCache cache = getInstance();
        
        // Count objects by type
        int gameObjectCount = 0;
        int wallObjectCount = 0;
        int decorativeObjectCount = 0;
        int groundObjectCount = 0;
        int tileObjectCount = 0;
        
        for (Rs2ObjectModel objectModel : cache.values()) {
            if (objectModel == null) continue;
            
            switch (objectModel.getObjectType()) {
                case GAME_OBJECT:
                    gameObjectCount++;
                    break;
                case WALL_OBJECT:
                    wallObjectCount++;
                    break;
                case DECORATIVE_OBJECT:
                    decorativeObjectCount++;
                    break;
                case GROUND_OBJECT:
                    groundObjectCount++;
                    break;
                case TILE_OBJECT:
                    tileObjectCount++;
                    break;
            }
        }
        
        return String.format("GO: %d, WO: %d, DO: %d, GRO: %d, TO: %d", 
            gameObjectCount, wallObjectCount, decorativeObjectCount, groundObjectCount, tileObjectCount);
    }

    /**
     * Implementation of abstract update method from Rs2Cache.
     * Clears the cache and performs a complete scene scan to reload all objects from the scene.
     * This ensures the cache is fully refreshed with current scene data.
     */
    @Override
    public void update() {
        log.info("Starting object cache update - clearing cache and performing scene scan");
        int sizeBefore = this.size();
        
        // Clear the entire cache
        this.invalidateAll();
        
        // Perform a complete scene scan to repopulate the cache
        updateStrategy.performSceneScan(this, true);
        
        int sizeAfter = this.size();
        log.info("Object cache update completed - objects before: {}, after: {}", sizeBefore, sizeAfter);
    }
}
