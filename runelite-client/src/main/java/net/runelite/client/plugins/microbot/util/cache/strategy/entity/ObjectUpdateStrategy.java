package net.runelite.client.plugins.microbot.util.cache.strategy.entity;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheUpdateStrategy;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2ObjectModel;

/**
 * Enhanced cache update strategy for game object data.
 * Handles all types of object spawn/despawn events and provides intelligent scene scanning.
 * Uses String-based cache keys for better tracking and region change handling.
 */
@Slf4j
public class ObjectUpdateStrategy implements CacheUpdateStrategy<String, Rs2ObjectModel> {
    /**
     * Generates a unique cache key for any TileObject (GameObject, WallObject, GroundObject, DecorativeObject).
     * Uses canonical location logic for GameObjects, and world location for others.
     *
     * @param object The TileObject (GameObject, WallObject, etc.)
     * @param tile The tile containing the object
     * @return The cache key string
     */
    public static String generateCacheIdForObject(TileObject object, Tile tile) {
        if (object instanceof GameObject) {
            // Use canonical location logic for GameObjects
            ObjectUpdateStrategy strategy = new ObjectUpdateStrategy();
            return strategy.generateCacheIdForGameObject((GameObject) object, tile);
        } else if (object instanceof WallObject) {
            WallObject wallObject = (WallObject) object;
            ObjectUpdateStrategy strategy = new ObjectUpdateStrategy();
            return strategy.generateCacheId("WallObject", wallObject.getId(), wallObject.getWorldLocation());
        } else if (object instanceof GroundObject) {
            GroundObject groundObject = (GroundObject) object;
            ObjectUpdateStrategy strategy = new ObjectUpdateStrategy();
            return strategy.generateCacheId("GroundObject", groundObject.getId(), groundObject.getWorldLocation());
        } else if (object instanceof DecorativeObject) {
            DecorativeObject decorativeObject = (DecorativeObject) object;
            ObjectUpdateStrategy strategy = new ObjectUpdateStrategy();
            return strategy.generateCacheId("DecorativeObject", decorativeObject.getId(), decorativeObject.getWorldLocation());
        }
        // Fallback: use type name and world location if available
        String type = object != null ? object.getClass().getSimpleName() : "Unknown";
        WorldPoint location = object != null ? object.getWorldLocation() : null;
        return location != null ? String.format("%s_%d_%d_%d_%d", type, object.getId(), location.getX(), location.getY(), location.getPlane()) : type + "_null";
    }
    
    private volatile boolean needsSceneScan = false;
    private volatile long lastSceneScan = 0;
    private static final long MIN_SCAN_INTERVAL_MS = 2000; // Minimum 2 seconds between scans
    
    @Override
    public void handleEvent(Object event, CacheOperations<String, Rs2ObjectModel> cache) {
        if (event instanceof GameObjectSpawned) {
            handleGameObjectSpawned((GameObjectSpawned) event, cache);
        } else if (event instanceof GameObjectDespawned) {
            handleGameObjectDespawned((GameObjectDespawned) event, cache);
        } else if (event instanceof GroundObjectSpawned) {
            handleGroundObjectSpawned((GroundObjectSpawned) event, cache);
        } else if (event instanceof GroundObjectDespawned) {
            handleGroundObjectDespawned((GroundObjectDespawned) event, cache);
        } else if (event instanceof WallObjectSpawned) {
            handleWallObjectSpawned((WallObjectSpawned) event, cache);
        } else if (event instanceof WallObjectDespawned) {
            handleWallObjectDespawned((WallObjectDespawned) event, cache);
        } else if (event instanceof DecorativeObjectSpawned) {
            handleDecorativeObjectSpawned((DecorativeObjectSpawned) event, cache);
        } else if (event instanceof DecorativeObjectDespawned) {
            handleDecorativeObjectDespawned((DecorativeObjectDespawned) event, cache);
        } else if (event instanceof GameStateChanged) {
            handleGameStateChanged((GameStateChanged) event, cache);
        } else if (event instanceof GameTick) {
            handleGameTick((GameTick) event, cache);
        }
    }
    
    /**
     * Performs an intelligent scene scan to populate the cache.
     * Only scans if certain conditions are met to avoid unnecessary processing.
     * 
     * @param cache The cache to populate
     * @param force Whether to force a scan regardless of conditions
     */
    public void performSceneScan(CacheOperations<String, Rs2ObjectModel> cache, boolean force) {
        long currentTime = System.currentTimeMillis();
        if (!Microbot.loggedIn || Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
            log.debug("Cannot perform scene scan - not logged in");
            return;
        }
        // Respect minimum scan interval unless forced
        if (!force && (currentTime - lastSceneScan) < MIN_SCAN_INTERVAL_MS) {
            log.trace("Skipping scene scan due to minimum interval not reached");
            return;
        }
        
        // Only scan if really needed or forced
        if (!force && !needsSceneScan && cache.size() > 10) {
            log.trace("Skipping scene scan - cache is populated and no scan requested");
            return;
        }
        
        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            log.debug("Cannot perform scene scan - no player");
            return;
        }
        
        Scene scene = player.getWorldView().getScene();
        if (scene == null) {
            log.debug("Cannot perform scene scan - no scene");
            return;
        }
        
        Tile[][][] tiles = scene.getTiles();
        if (tiles == null) {
            log.debug("Cannot perform scene scan - no tiles");
            return;
        }
        
        // Collect all objects first to avoid recursive cache calls
        java.util.Map<String, Rs2ObjectModel> objectsToAdd = new java.util.HashMap<>();
        int z = player.getWorldView().getPlane();
        
        log.debug("Starting intelligent scene scan (cache size: {}, forced: {})", cache.size(), force);
        
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
                            String cacheId = generateCacheIdForGameObject(gameObject, tile);
                            if (!objectsToAdd.containsKey(cacheId)) {
                                Rs2ObjectModel objectModel = new Rs2ObjectModel(gameObject, tile);
                                objectsToAdd.put(cacheId, objectModel);
                            }
                        }
                    }
                }
                
                // Check GroundObject
                GroundObject groundObject = tile.getGroundObject();
                if (groundObject != null) {
                    String cacheId = generateCacheId("GroundObject", groundObject.getId(), groundObject.getWorldLocation());
                    if (!objectsToAdd.containsKey(cacheId)) {
                        Rs2ObjectModel objectModel = new Rs2ObjectModel(groundObject, tile);
                        objectsToAdd.put(cacheId, objectModel);
                    }
                }
                
                // Check WallObject
                WallObject wallObject = tile.getWallObject();
                if (wallObject != null) {
                    String cacheId = generateCacheId("WallObject", wallObject.getId(), wallObject.getWorldLocation());
                    if (!objectsToAdd.containsKey(cacheId)) {
                        Rs2ObjectModel objectModel = new Rs2ObjectModel(wallObject, tile);
                        objectsToAdd.put(cacheId, objectModel);
                    }
                }
                
                // Check DecorativeObject
                DecorativeObject decorativeObject = tile.getDecorativeObject();
                if (decorativeObject != null) {
                    String cacheId = generateCacheId("DecorativeObject", decorativeObject.getId(), decorativeObject.getWorldLocation());
                    if (!objectsToAdd.containsKey(cacheId)) {
                        Rs2ObjectModel objectModel = new Rs2ObjectModel(decorativeObject, tile);
                        objectsToAdd.put(cacheId, objectModel);
                    }
                }
            }
        }
        
        // Now update the cache in batch to avoid recursive calls
        int addedObjects = 0;
        for (java.util.Map.Entry<String, Rs2ObjectModel> entry : objectsToAdd.entrySet()) {
            String cacheId = entry.getKey();
            Rs2ObjectModel objectModel = entry.getValue();
            
            // Only add if not already in cache (avoid recursive get calls by checking internally)
            if (!cache.containsKey(cacheId)) {
                cache.put(cacheId, objectModel);
                addedObjects++;
            }
        }
        
        lastSceneScan = currentTime;
        needsSceneScan = false;
        
        if (addedObjects > 0) {
            log.debug("Intelligent scene scan completed - added {} objects (total cache size: {})", 
                     addedObjects, cache.size());
        } else {
            log.trace("Scene scan completed - no new objects added");
        }
    }
    
    /**
     * Requests a scene scan to be performed when appropriate.
     * This is more efficient than immediate scanning.
     */
    public void requestSceneScan() {
        needsSceneScan = true;
        log.debug("Scene scan requested");
    }
    
    /**
     * Checks if a scene scan is needed based on cache state.
     * 
     * @param cache The cache to check
     * @return true if a scan would be beneficial
     */
    public boolean shouldPerformSceneScan(CacheOperations<String, Rs2ObjectModel> cache) {
        return needsSceneScan || cache.size() < 5; // Very small cache suggests missing objects
    }
    
    private void handleGameObjectSpawned(GameObjectSpawned event, CacheOperations<String, Rs2ObjectModel> cache) {
        GameObject gameObject = event.getGameObject();
        Tile tile = event.getTile();
        if (gameObject != null && tile != null) {
            // Only add multi-tile objects from their primary (southwest) tile to prevent duplicates
            if (isPrimaryTile(gameObject, tile)) {
                Rs2ObjectModel objectModel = new Rs2ObjectModel(gameObject, tile);
                String cacheId = generateCacheIdForGameObject(gameObject, tile);
                cache.put(cacheId, objectModel);
                
                log.debug("Added GameObject {} (id: {}) to cache via spawn event from primary tile", 
                         gameObject.getId(), cacheId);
            } else {
                log.trace("Skipped GameObject {} spawn event from non-primary tile", gameObject.getId());
            }
        }
    }
    
    private void handleGameObjectDespawned(GameObjectDespawned event, CacheOperations<String, Rs2ObjectModel> cache) {
        GameObject gameObject = event.getGameObject();
        Tile tile = event.getTile();
        if (gameObject != null && tile != null) {
            // Only process despawn events from the primary tile to prevent multiple removal attempts
            if (isPrimaryTile(gameObject, tile)) {
                String cacheId = generateCacheIdForGameObject(gameObject, tile);
                cache.remove(cacheId);
                log.debug("Removed GameObject {} (id: {}) from cache via despawn event from primary tile", 
                         gameObject.getId(), cacheId);
            } else {
                log.trace("Skipped GameObject {} despawn event from non-primary tile", gameObject.getId());
            }
        }
    }
    
    private void handleGroundObjectSpawned(GroundObjectSpawned event, CacheOperations<String, Rs2ObjectModel> cache) {
        GroundObject groundObject = event.getGroundObject();
        Tile tile = event.getTile();
        if (groundObject != null && tile != null) {
            Rs2ObjectModel objectModel = new Rs2ObjectModel(groundObject, tile);
            String cacheId = generateCacheId("GroundObject", groundObject.getId(), groundObject.getWorldLocation());
            cache.put(cacheId, objectModel);
            log.debug("Added GroundObject {} (id: {}) to cache via spawn event", groundObject.getId(), cacheId);
        }
    }
    
    private void handleGroundObjectDespawned(GroundObjectDespawned event, CacheOperations<String, Rs2ObjectModel> cache) {
        GroundObject groundObject = event.getGroundObject();
        if (groundObject != null) {            
            String cacheId = generateCacheId("GroundObject", groundObject.getId(), groundObject.getWorldLocation());
            cache.remove(cacheId);
            log.debug("Removed GroundObject {} (id: {}) from cache via despawn event", groundObject.getId(), cacheId);
        }
    }
    
    private void handleWallObjectSpawned(WallObjectSpawned event, CacheOperations<String, Rs2ObjectModel> cache) {
        WallObject wallObject = event.getWallObject();
        Tile tile = event.getTile();
        if (wallObject != null && tile != null) {
            Rs2ObjectModel objectModel = new Rs2ObjectModel(wallObject, tile);
            String cacheId = generateCacheId("WallObject", wallObject.getId(), wallObject.getWorldLocation());
            cache.put(cacheId, objectModel);
            log.debug("Added WallObject {} (id: {}) to cache via spawn event", wallObject.getId(), cacheId);
        }
    }
    
    private void handleWallObjectDespawned(WallObjectDespawned event, CacheOperations<String, Rs2ObjectModel> cache) {
        WallObject wallObject = event.getWallObject();
        if (wallObject != null) {
            String cacheId = generateCacheId("WallObject", wallObject.getId(), wallObject.getWorldLocation());
            cache.remove(cacheId);
            log.debug("Removed WallObject {} (id: {}) from cache via despawn event", wallObject.getId(), cacheId);
        }
    }
    
    private void handleDecorativeObjectSpawned(DecorativeObjectSpawned event, CacheOperations<String, Rs2ObjectModel> cache) {
        DecorativeObject decorativeObject = event.getDecorativeObject();
        Tile tile = event.getTile();
        if (decorativeObject != null && tile != null) {
            Rs2ObjectModel objectModel = new Rs2ObjectModel(decorativeObject, tile);
            String cacheId = generateCacheId("DecorativeObject", decorativeObject.getId(), decorativeObject.getWorldLocation());
            cache.put(cacheId, objectModel);
            log.debug("Added DecorativeObject {} (id: {}) to cache via spawn event", decorativeObject.getId(), cacheId);
        }
    }
    
    private void handleDecorativeObjectDespawned(DecorativeObjectDespawned event, CacheOperations<String, Rs2ObjectModel> cache) {
        DecorativeObject decorativeObject = event.getDecorativeObject();
        if (decorativeObject != null) {
            String cacheId = generateCacheId("DecorativeObject", decorativeObject.getId(), decorativeObject.getWorldLocation());
            cache.remove(cacheId);
            log.debug("Removed DecorativeObject {} (id: {}) from cache via despawn event", decorativeObject.getId(), cacheId);
        }
    }
    
    private void handleGameStateChanged(GameStateChanged event, CacheOperations<String, Rs2ObjectModel> cache) {
        switch (event.getGameState()) {
            case LOGGED_IN:
                // Request scene scan when logging in - might have missed spawn events
                requestSceneScan();
                log.debug("Player logged in - requesting scene scan");
                break;
            case LOADING:
                // Request scene scan during loading - new area might have objects
                requestSceneScan();
                log.debug("Game loading - requesting scene scan");
                break;
            case LOGIN_SCREEN:
            case CONNECTION_LOST:
                // Clear scan request when logging out
                needsSceneScan = false;
                log.debug("Player logged out - clearing scan request");
                break;
            default:
                break;
        }
    }
    
    private void handleGameTick(GameTick event, CacheOperations<String, Rs2ObjectModel> cache) {
        // Perform scene scan if requested and conditions are met
        if (shouldPerformSceneScan(cache)) {
            performSceneScan(cache, false);
        }
    }
    
    /**
     * Gets the canonical world location for a GameObject.
     * For multi-tile objects, this returns the southwest tile location.
     * 
     * @param gameObject The GameObject to get the canonical location for
     * @param tile The tile from the event
     * @return The canonical world location
     */
    private WorldPoint getCanonicalLocation(GameObject gameObject, Tile tile) {
        // For multi-tile objects, we need to ensure we use the southwest tile consistently
        Point sceneMinLocation = gameObject.getSceneMinLocation();
        Point currentSceneLocation = tile.getSceneLocation();
        
        // If this is the southwest tile, use this tile's location
        if (sceneMinLocation != null && currentSceneLocation != null && 
            sceneMinLocation.getX() == currentSceneLocation.getX() && 
            sceneMinLocation.getY() == currentSceneLocation.getY()) {
            return tile.getWorldLocation();
        }
        
        // Otherwise, we need to calculate the southwest tile's world location
        // This is tricky without scene-to-world conversion, so we'll use a different approach
        WorldPoint currentLocation = tile.getWorldLocation();
        if (sceneMinLocation != null && currentSceneLocation != null) {
            int deltaX = currentSceneLocation.getX() - sceneMinLocation.getX();
            int deltaY = currentSceneLocation.getY() - sceneMinLocation.getY();
            return new WorldPoint(currentLocation.getX() - deltaX, currentLocation.getY() - deltaY, currentLocation.getPlane());
        }
        
        return currentLocation;
    }
    
    /**
     * Checks if the given tile is the primary (southwest) tile for a GameObject.
     * 
     * @param gameObject The GameObject to check
     * @param tile The tile to verify
     * @return true if this is the primary tile, false otherwise
     */
    private boolean isPrimaryTile(GameObject gameObject, Tile tile) {
        Point sceneMinLocation = gameObject.getSceneMinLocation();
        Point currentSceneLocation = tile.getSceneLocation();
        
        return sceneMinLocation != null && currentSceneLocation != null && 
               sceneMinLocation.getX() == currentSceneLocation.getX() && 
               sceneMinLocation.getY() == currentSceneLocation.getY();
    }

    /**
     * Generates a unique object ID for tracking.
     * For GameObjects, uses the canonical (southwest) location to ensure consistent caching.
     */
    private String generateCacheId(String type, int objectID, net.runelite.api.coords.WorldPoint location) {
        return String.format("%s_%d_%d_%d_%d", type, objectID, location.getX(), location.getY(), location.getPlane());
    }
    
    /**
     * Generates a unique object ID for tracking GameObjects using their canonical location.
     * This ensures that multi-tile GameObjects have consistent cache keys.
     */
    private String generateCacheIdForGameObject(GameObject gameObject, Tile tile) {
        WorldPoint canonicalLocation = getCanonicalLocation(gameObject, tile);
        return generateCacheId("GameObject", gameObject.getId(), canonicalLocation);
    }
    
    @Override
    public Class<?>[] getHandledEventTypes() {
        return new Class<?>[]{
            GameObjectSpawned.class, GameObjectDespawned.class,
            GroundObjectSpawned.class, GroundObjectDespawned.class,
            WallObjectSpawned.class, WallObjectDespawned.class,
            DecorativeObjectSpawned.class, DecorativeObjectDespawned.class,
            GameStateChanged.class, GameTick.class
        };
    }
    
    @Override
    public void onAttach(CacheOperations<String, Rs2ObjectModel> cache) {
        log.debug("ObjectUpdateStrategy attached to cache");
    }
    
    @Override
    public void onDetach(CacheOperations<String, Rs2ObjectModel> cache) {
        log.debug("ObjectUpdateStrategy detached from cache");
    }
}
