package net.runelite.client.plugins.microbot.util.cache;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Constants;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.strategy.entity.ObjectUpdateStrategy;
import net.runelite.client.plugins.microbot.util.cache.util.LogOutputMode;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2CacheLoggingUtils;
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
    public static boolean requestSceneScan() {
        return getInstance().updateStrategy.requestSceneScan(getInstance());
    }
       
    
    /**
     * Starts periodic scene scanning to keep the cache fresh.
     * This is useful for long-running scripts that need up-to-date object data.
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
        if (updateStrategy.requestSceneScan(this)) {
            log.debug("Cache miss for key '{}' (size: {}), performing scene scan", key, this.size());
            //updateStrategy.performSceneScan(this, false);            
            // Try again after scene scan
            return super.get(key);
        }else {
            log.debug("Cache miss for key '{}' (size: {}), but scene scan not requested not successful", key, this.size());
        }
        
        return null;
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
     * Invalidates all object cache entries and performs a fresh scene scan.
     */
    public static void invalidateAllObjectsAndScanScene() {
        getInstance().invalidateAll();
        requestSceneScan();
        log.debug("Invalidated all object cache entries and triggered scene scan");
    }
    
  
    
    /**
     * Event handler registration for the unified cache.
     * The unified cache handles events through its strategy automatically.
     * Region change detection is now handled primarily in onGameTick() to prevent
     * redundant checks during burst spawn events.
     */

    @Subscribe(priority = 50)
    public void onGameObjectSpawned(final GameObjectSpawned event) {
        // Region change check now handled in onGameStateChanged() to prevent redundant checks
        getInstance().handleEvent(event);
    }
    
    @Subscribe(priority = 60)
    public void onGameObjectDespawned(final GameObjectDespawned event) {
        getInstance().handleEvent(event);
    }
    
    @Subscribe(priority = 50)
    public void onGroundObjectSpawned(final GroundObjectSpawned event) {
        // Region change check now handled in onGameStateChanged() to prevent redundant checks
        getInstance().handleEvent(event);
    }
    
    @Subscribe(priority = 60)
    public void onGroundObjectDespawned(final GroundObjectDespawned event) {
        getInstance().handleEvent(event);
    }
    
    @Subscribe(priority = 50)
    public void onWallObjectSpawned(final WallObjectSpawned event) {
        // Region change check now handled in onGameStateChanged() to prevent redundant checks
        getInstance().handleEvent(event);
    }
    
    @Subscribe(priority = 60)
    public void onWallObjectDespawned(final WallObjectDespawned event) {
        getInstance().handleEvent(event);
    }
    
    @Subscribe(priority = 50)
    public void onDecorativeObjectSpawned(final DecorativeObjectSpawned event) {
        // Region change check now handled in onGameStateChanged() to prevent redundant checks
        getInstance().handleEvent(event);
    }
    
    @Subscribe(priority = 60)
    public void onDecorativeObjectDespawned(final DecorativeObjectDespawned event) {
        getInstance().handleEvent(event);
    }   
    @Subscribe(priority = 40)
    public void onGameStateChanged(final GameStateChanged event) {
        // Removed old region detection - now handled by unified Rs2Cache system
        // Also let the strategy handle the event
        getInstance().handleEvent(event);
    }
    
    @Subscribe(priority = 110)
    public void onGameTick(final GameTick event) {
        // Let the strategy handle scanning
        getInstance().handleEvent(event);
    }
    
    /**
     * Resets the singleton instance. Used for testing.
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.invalidateAll();
            instance = null;
            log.debug("Rs2ObjectCache instance reset");
        }
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
        
        return String.format("Objects by type - Game: %d, Wall: %d, Decorative: %d, Ground: %d, Tile: %d (Total: %d)",
            gameObjectCount, wallObjectCount, decorativeObjectCount, groundObjectCount, tileObjectCount, 
            cache.size());
    }
    
    /**
     * Logs the current state of all cached objects for debugging.
     * 
     * @param dumpToFile Whether to also dump the information to a file
     */
    public static void logState(LogOutputMode mode) {
        var cache = getInstance();
        var stats = cache.getStatistics();
        
        // Create the log content
        StringBuilder logContent = new StringBuilder();
        
        String header = String.format("=== Object Cache State (%d entries) ===", cache.size());
        logContent.append(header).append("\n");
        
        String statsInfo = Rs2CacheLoggingUtils.formatCacheStatistics(
            stats.getHitRate(), stats.cacheHits, stats.cacheMisses, stats.cacheMode.toString());        
        logContent.append(statsInfo).append("\n\n");
        
        if (cache.size() == 0) {
            logContent.append("Cache is empty\n");
        } else {
            // Table format for objects
            final String[] headers = {"Name", "Type", "ID", "Location", "Distance", "Actions"};
            final int[] columnWidths = {25, 12, 8, 18, 8, 30};
            logContent.append("\n").append(Rs2CacheLoggingUtils.formatTableHeader(headers, columnWidths));

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

            // Use a fixed-size buffer to avoid excessive StringBuilder growth
            int maxRows = mode == LogOutputMode.CONSOLE_ONLY ? 50 : cache.size();
            int rowCount = 0;
            // Precompute distances and actions in parallel for performance
            class ObjectLogInfo {
                Rs2ObjectModel obj;
                int distance;
                String actionsStr;
                ObjectLogInfo(Rs2ObjectModel obj, int distance, String actionsStr) {
                    this.obj = obj;
                    this.distance = distance;
                    this.actionsStr = actionsStr;
                }
            }

            List<ObjectLogInfo> objects = cache.values().parallelStream().limit(50)
                .map(obj -> {
                    int distance = Integer.MAX_VALUE;
                    if (finalPlayerLocation != null && obj.getLocation() != null) {
                        try {
                            distance = obj.getLocation().distanceTo(finalPlayerLocation);
                        } catch (Exception ignored) {}
                    }
                    String actionsStr = "";
                    try {
                        String[] actions = obj.getActions();
                        if (actions != null && actions.length > 0) {
                            actionsStr = Arrays.stream(actions)
                                .filter(Objects::nonNull)
                                .filter(action -> !action.trim().isEmpty())
                                .collect(Collectors.joining(","));
                        }
                    } catch (Exception ignored) {}
                    return new ObjectLogInfo(obj, distance, actionsStr);
                })
                .collect(Collectors.toList());

            // Sort by distance (single-threaded, but fast on precomputed values)
            if (finalPlayerLocation != null) {
                objects.sort(Comparator.comparingInt(info -> info.distance));
            }

            for (ObjectLogInfo info : objects) {
                if (rowCount++ >= maxRows) break;
                try {
                    String[] values = {
                        Rs2CacheLoggingUtils.truncate(info.obj.getName() != null ? info.obj.getName() : "Unknown", 24),
                        info.obj.getObjectType() != null ? info.obj.getObjectType().name() : "Unknown",
                        String.valueOf(info.obj.getId()),
                        Rs2CacheLoggingUtils.formatLocation(info.obj.getLocation()),
                        info.distance == Integer.MAX_VALUE ? "N/A" : String.valueOf(info.distance),
                        Rs2CacheLoggingUtils.truncate(info.actionsStr, 29)
                    };
                    logContent.append(Rs2CacheLoggingUtils.formatTableRow(values, columnWidths));
                } catch (Exception e) {
                    log.debug("Error processing object for logging: {}", e.getMessage());
                }
            }

            logContent.append(Rs2CacheLoggingUtils.formatTableFooter(columnWidths));
            String limitMsg = Rs2CacheLoggingUtils.formatLimitMessage(cache.size(), maxRows);
            if (!limitMsg.isEmpty()) {
                logContent.append(limitMsg).append("\n");
            }
        }
        
        String footer = "=== End Object Cache State ===";
        logContent.append(footer).append("\n");        
        Rs2CacheLoggingUtils.outputCacheLog(getInstance().getCacheName(), logContent.toString(), mode);         
    }
    
   
    
    /**
     * Implementation of abstract update method from Rs2Cache.
     * Clears the cache and performs a complete scene scan to reload all objects from the scene.
     * This ensures the cache is fully refreshed with current scene data.
     */
    @Override
    public void update() {
        // Call the update method with a default delay of 0
        update(Constants.CLIENT_TICK_LENGTH /2);
    }
    /**
     * Updates the object cache by clearing it and performing a scene scan.
     * This is useful for refreshing the cache after significant game state changes.
     * 
     * @param delayMs Optional delay in milliseconds before performing the update
     */
    public void update(long delayMs) {
        log.debug("Starting object cache update - clearing cache and performing scene scan after delay: {}ms", delayMs);            
        // Clear the entire cache
        this.invalidateAll();    
        // Perform a complete scene scan to repopulate the cache
        updateStrategy.performSceneScan(this, delayMs );                

    }
}
