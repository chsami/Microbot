package net.runelite.client.plugins.microbot.util.cache.strategy.entity;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.concurrent.ScheduledExecutorService;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheUpdateStrategy;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItemModel;
import net.runelite.client.plugins.microbot.util.cache.Rs2GroundItemCache;

/**
 * Enhanced cache update strategy for ground item data.
 * Handles automatic cache updates based on ground item spawn/despawn events and provides scene scanning.
 * Follows the same pattern as ObjectUpdateStrategy for consistency.
 */
@Slf4j
public class GroundItemUpdateStrategy implements CacheUpdateStrategy<String, Rs2GroundItemModel> {
    
    GameState lastGameState = null;
    
    // ScheduledExecutorService for non-blocking operations
    private final ScheduledExecutorService executorService;
    private ScheduledFuture<?> periodicSceneScanTask;
    private ScheduledFuture<?> sceneScanTask;
    
    // Scene scan tracking
    private final AtomicBoolean scanActive = new AtomicBoolean(false);
    private final AtomicBoolean scanRequest =  new AtomicBoolean(false);        
    private static final long MIN_SCAN_INTERVAL_MS = Constants.GAME_TICK_LENGTH; // Minimum interval between scans
    private volatile long lastSceneScan = 0; // Last time a scene scan was performed
    
    /**
     * Constructor initializes the executor service for background tasks.
     */
    public GroundItemUpdateStrategy() {
        this.executorService = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "GroundItemUpdateStrategy-" + System.currentTimeMillis());
            thread.setDaemon(true);
            return thread;
        });
        log.debug("GroundItemUpdateStrategy initialized with ScheduledExecutorService");
    }
    
    @Override
    public void handleEvent(Object event, CacheOperations<String, Rs2GroundItemModel> cache) {
        if (executorService == null || executorService.isShutdown() || !Microbot.loggedIn || Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
            log.warn("GroundItemUpdateStrategy is shut down or not logged in, ignoring event: {}", event.getClass().getSimpleName());
            return; // Don't process events if shut down
        }
        if (scanActive.get()){
            log.debug("Skipping event processing - scan already active: {}", event.getClass().getSimpleName());
            return; // Don't process events if a scan is already active
        }
        // Submit event handling to executor service for non-blocking processing
        //executorService.submit(() -> {
          //  try {
                processEventInternal(event, cache);
            //} catch (Exception e) {
              //  log.error("Error processing event: {}", event.getClass().getSimpleName(), e);
            //}
        //});
    }
    
    /**
     * Internal method to process events - runs on background thread.
     */
    private void processEventInternal(Object event, CacheOperations<String, Rs2GroundItemModel> cache) {
        if (event instanceof ItemSpawned) {
            handleItemSpawned((ItemSpawned) event, cache);
        } else if (event instanceof ItemDespawned) {
            handleItemDespawned((ItemDespawned) event, cache);
        } else if (event instanceof GameStateChanged) {
            handleGameStateChanged((GameStateChanged) event, cache);
        }
    }
    
    /**
     * Performs an scene scan to populate the cache.
     * Only scans if certain conditions are met to avoid unnecessary processing.
     * This method runs asynchronously on a background thread to avoid blocking.
     * 
     * @param cache The cache to populate
     */
    public void performSceneScan(CacheOperations<String, Rs2GroundItemModel> cache,long delayMs) {
        if (executorService == null || executorService.isShutdown()) {
            log.debug("Skipping ground item scene scan - strategy is shut down or not logged in");
            return;
        }
        
        // Respect minimum scan interval unless forced
        if ((System.currentTimeMillis() - lastSceneScan) < MIN_SCAN_INTERVAL_MS) {
            log.debug("Skipping ground item scene scan due to minimum interval not reached");
            scanActive.set(false);
            return;
        }
        
        if (sceneScanTask != null && !sceneScanTask.isDone()) {
            log.debug("Skipping ground item scene scan - already scheduled or running");
            return; // Don't perform scan if already scheduled or running
        }
        
        if (scanActive.compareAndSet(false, true)) {
            // Submit scene scan to executor service for non-blocking execution
            sceneScanTask = executorService.schedule(() -> {
                try {
                    performSceneScanInternal(cache);
                } catch (Exception e) {
                    log.error("Error during ground item scene scan", e);
                } finally {
                    scanActive.set(false);                    
                }
            }, delayMs, TimeUnit.MILLISECONDS); // delay before scan
        } else {
            log.debug("Skipping ground item scene scan - already active");
            return; // Don't perform scan if already active
        }
    }
    
    /**
     * Schedules a periodic scene scan task.
     * This is useful for ensuring the cache stays up-to-date even if events are missed.
     * 
     * @param cache The cache to scan
     * @param intervalSeconds How often to scan in seconds
     */
    public void schedulePeriodicSceneScan(CacheOperations<String, Rs2GroundItemModel> cache, long intervalSeconds) {
        if (executorService == null || executorService.isShutdown()) {
            log.warn("Cannot schedule periodic scan - strategy is shut down");
            return;
        }
        
        // Cancel existing task if any
        stopPeriodicSceneScan();
        
        periodicSceneScanTask = executorService.scheduleWithFixedDelay(() -> {
            try {
                if (scanActive.compareAndSet(false, true)) {
                    if (scanRequest.get() && Microbot.loggedIn) {
                        log.debug("Periodic ground item scene scan triggered");
                        performSceneScanInternal(cache);
                    }
                } else {
                    log.debug("Skipping scheduled ground item scene scan - already active");
                }
            } catch (Exception e) {
                log.error("Error in periodic ground item scene scan", e);
            } finally {
                scanActive.set(false);                
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        
        log.debug("Scheduled periodic ground item scene scan every {} seconds", intervalSeconds);
    }
    
    /**
     * Stops periodic scene scanning.
     */
    public void stopPeriodicSceneScan() {
        if (periodicSceneScanTask != null && !periodicSceneScanTask.isDone()) {
            periodicSceneScanTask.cancel(false);
            periodicSceneScanTask = null;
            log.debug("Stopped periodic ground item scene scanning");
        }
    }
    
    /**
     * Checks if a scene scan is needed based on cache state.
     * 
     * @param cache The cache to check
     * @return true if a scan would be beneficial
     */
    public boolean requestSceneScan(CacheOperations<String, Rs2GroundItemModel> cache) {
        if (scanActive.get()) {
            log.debug("Skipping scene scan request - already active");
            return false; // Don't request scan if already active
        }
        if (scanRequest.compareAndSet(false, true)) {
            log.debug("Ground item scene scan requested");
            performSceneScan(cache, 5); // Perform with 5ms delay for stability
        }
        if (!Microbot.getClient().isClientThread()){
            sleepUntil(()->!scanRequest.get(), 1000); // Wait until scan is requested and reset
        }        
        return !scanRequest.get(); // Return true if scan was requested,reseted               
    }
    
    /**
     * Performs the actual scene scan to synchronize ground items with the current scene.
     * This method both adds new items found in the scene AND removes cached items no longer present.
     * Provides complete scene synchronization in a single operation.
     */
    private void performSceneScanInternal(CacheOperations<String, Rs2GroundItemModel> cache) {
        try {
            long currentTime = System.currentTimeMillis();
            
           // Check minimum interval to prevent excessive scanning
            if (currentTime - lastSceneScan < MIN_SCAN_INTERVAL_MS) {
                log.debug("Skipping Ground scene scan - too soon since last scan");
                scanActive.set(false);
                return;
            }
            Player player = Microbot.getClient().getLocalPlayer();
            if (player == null) {
                log.debug("Cannot perform ground item scene scan - no player");
                scanActive.set(false);
                return;
            }
            
            Scene scene = player.getWorldView().getScene();
            if (scene == null) {
                log.debug("Cannot perform ground item scene scan - no scene");
                scanActive.set(false);
                return;
            }
            
            Tile[][][] tiles = scene.getTiles();
            if (tiles == null) {
                log.debug("Cannot perform ground item scene scan - no tiles");
                scanActive.set(false);
                return;
            }

            // Build a set of all currently existing ground item keys from the scene
            java.util.Set<String> currentSceneKeys = new java.util.HashSet<>();
            int addedItems = 0;
            int z = player.getWorldView().getPlane();
            
            log.debug("Starting ground item scene synchronization (cache size: {})", cache.size());
            
            // Phase 1: Scan scene and add new items
            for (int x = 0; x < Constants.SCENE_SIZE; x++) {
                for (int y = 0; y < Constants.SCENE_SIZE; y++) {
                    Tile tile = tiles[z][x][y];
                    if (tile == null) continue;
                    if(tile.getGroundItems() == null) continue; // Ensure ground items are loaded
                    
                    // Get ground items on this tile
                    for (TileItem tileItem : tile.getGroundItems()) {
                        if (tileItem != null) {
                            String key = generateKey(tileItem, tile.getWorldLocation());
                            currentSceneKeys.add(key); // Track all scene items
                            
                            // Only add if not already in cache to avoid recursive calls
                            if (!cache.containsKey(key)) {
                                Rs2GroundItemModel groundItemModel = new Rs2GroundItemModel(tileItem, tile);                                
                                cache.put(key, groundItemModel);
                                addedItems++;
                            }
                        }
                    }
                }
            }
            
            // Phase 2: Remove cached items no longer in scene
            int removedItems = 0;
            if (!currentSceneKeys.isEmpty()) {
                // Find cached items that are no longer in the scene using CacheOperations streaming
                List<String> keysToRemove = cache.entryStream()
                    .map(java.util.Map.Entry::getKey)
                    .filter(key -> !currentSceneKeys.contains(key))
                    .collect(Collectors.toList());
                
                // Remove the items that are no longer in scene
                for (String key : keysToRemove) {
                    Rs2GroundItemModel item = cache.getRawValue(key); // Use raw value to avoid triggering recursive scene scans
                    cache.remove(key);
                    if (item != null) {
                        removedItems++;
                        log.trace("Removed ground item not in scene: ID {} ({})", item.getId(), key);
                    }
                }
            }
                                            
            // Log comprehensive results
            if (addedItems > 0 || removedItems > 0) {
                log.debug("Ground item scene synchronization completed - added {} items, removed {} items (total cache size: {})", 
                        addedItems, removedItems, cache.size());
            } else {
                log.debug("Ground item scene synchronization completed - no changes made");
            }
            
            scanRequest.set(false); //NOT in finally block to allow for rescan if there are an error 
        }catch (Exception e) {
            log.error("Error during ground item scene synchronization", e);                
        }finally {
            scanActive.set(false);
            lastSceneScan = System.currentTimeMillis(); // Update last scan time                
        }
    }
    
    private void handleItemSpawned(ItemSpawned event, CacheOperations<String, Rs2GroundItemModel> cache) {
        TileItem item = event.getItem();
        if (item != null) {
            String key = generateKey(item, event.getTile().getWorldLocation());
            Rs2GroundItemModel groundItem = new Rs2GroundItemModel(item, event.getTile());
            cache.put(key, groundItem);
            log.trace("Added ground item {} at {} to cache via spawn event", item.getId(), event.getTile().getWorldLocation());
        }
    }
    
    private void handleItemDespawned(ItemDespawned event, CacheOperations<String, Rs2GroundItemModel> cache) {
        TileItem item = event.getItem();
        //Rs2GroundItemModel groundItem = new Rs2GroundItemModel(item, event.getTile());        
        if (item != null) {
            String key = generateKey(item, event.getTile().getWorldLocation());
            cache.remove(key);
            log.trace("Removed ground item {} at {} from cache via despawn event", item.getId(), event.getTile().getWorldLocation());
        }
    }
    /**
     * Cleanup persistent items that don't naturally despawn.
     * This method follows the same pattern as performSceneScan with proper async execution.
     * 
     * @param cache The cache operations interface
     * @param delayMs Delay before performing the cleanup
     */
    public void cleanupPersistentItems(CacheOperations<String, Rs2GroundItemModel> cache, long delayMs) {
        if (executorService == null || executorService.isShutdown()) {
            log.debug("Skipping persistent item cleanup - strategy is shut down");
            return;
        }
        
        // Submit cleanup to executor service for non-blocking execution
        executorService.schedule(() -> {
            try {
                cleanupPersistentItemsInternal(cache);
            } catch (Exception e) {
                log.error("Error during persistent item cleanup", e);
            }
        }, delayMs, TimeUnit.MILLISECONDS);
    }
    
    /**
     * Internal method that performs the actual persistent item cleanup.
     * Directly removes persistent ground items that don't naturally despawn without scene scanning.
     * 
     * @param cache The cache operations interface
     * @return The number of persistent items cleaned up
     */
    private int cleanupPersistentItemsInternal(CacheOperations<String, Rs2GroundItemModel> cache) {
        // Use CacheOperations streaming to find and remove persistent items
        List<String> keysToRemove = cache.entryStream()
            .filter(entry -> {
                Rs2GroundItemModel item = entry.getValue();
                // Check if this is a persistent item (using isPersistened method)
                return item.isPersistened();
            })
            .map(java.util.Map.Entry::getKey)
            .collect(Collectors.toList());
        
        if (keysToRemove.isEmpty()) {
            log.debug("No persistent ground items found to cleanup");
            return 0;
        }
        
        // Remove all persistent items directly
        int removedCount = 0;
        for (String key : keysToRemove) {
            Rs2GroundItemModel item = cache.getRawValue(key); // Use raw value to avoid triggering recursive scene scans
            cache.remove(key);
            if (item != null) {
                removedCount++;
                log.trace("Removed persistent ground item: ID {} ({})", item.getId(), key);
            }
        }
        
        log.debug("Cleaned up {} persistent ground items", removedCount);
        return removedCount;
    }
    
    /**
     * Handles game state changes for ground item cache management.
     * 
     * <p><strong>Ground Item Despawn Handling Strategy:</strong></p>
     * <ul>
     *   <li>Unlike NPCs, ground items have complex despawn timing that isn't always captured by {@link net.runelite.api.events.ItemDespawned}</li>
     *   <li>Items can despawn based on game ticks elapsed since spawn time, which may not trigger ItemDespawned events</li>
     *   <li>We rely on {@link Rs2GroundItemCache#performPeriodicCleanup()} to check {@link Rs2GroundItemModel#isDespawned()}</li>
     *   <li>The {@link Rs2GroundItemCache#isExpired(String)} method integrates despawn timing directly into cache operations</li>
     *   <li>This dual approach ensures expired ground items are removed even when events are missed</li>
     * </ul>
     * 
     * <p><strong>Why scene scanning is essential for ground items:</strong></p>
     * <ul>
     *   <li>Ground items have complex despawn mechanics not always captured by ItemDespawned events</li>
     *   <li>Persistent items (player drops, quest items) may have indefinite lifespans requiring special detection</li>
     *   <li>Events can be missed during region changes, network issues, or client restarts</li>
     *   <li>Scene scanning ensures cache synchronization with actual game state after login/loading</li>
     *   <li>The {@link #performSceneScan(CacheOperations, long)} method provides complete scene-to-cache synchronization</li>
     * </ul>
     * 
     * <p><strong>Persistent Item Handling:</strong></p>
     * <ul>
     *   <li>Persistent items are detected using {@link Rs2GroundItemModel#isPersistened()}</li>
     *   <li>Scene scanning includes cleanup of persistent items that should no longer exist</li>
     *   <li>Combines natural despawn cleanup with scene validation for comprehensive item management</li>
     * </ul>
     * 
     * <p><strong>Game State Specific Actions:</strong></p>
     * <ul>
     *   <li><strong>LOGGED_IN/LOADING:</strong> Perform scene scan with 2-tick delay for stability and cleanup persistent items</li>
     *   <li><strong>LOGOUT States:</strong> Cancel ongoing scan operations and invalidate entire cache</li>
     *   <li><strong>Other States:</strong> Update state tracking without additional actions</li>
     * </ul>
     * 
     * @param event The game state change event
     * @param cache The ground item cache operations interface
     */
    private void handleGameStateChanged(GameStateChanged event, CacheOperations<String, Rs2GroundItemModel> cache) {
        switch (event.getGameState()) {
            case LOGGED_IN:               
                // Ground item despawn handling is managed by Rs2GroundItemCache.performPeriodicCleanup()
                // and Rs2GroundItemCache.isExpired() using Rs2GroundItemModel.isDespawned()
                lastGameState = GameState.LOGGED_IN;
                log.debug("Player logged in - ground item despawn handled by periodic cleanup");
                
                // Perform scene scan to synchronize cache with current scene and cleanup persistent items                
                performSceneScan(cache, Constants.GAME_TICK_LENGTH*2); // 2 ticks delay for stability
                break;
            case LOADING:
                // Ground item despawn handling is managed by Rs2GroundItemCache.performPeriodicCleanup()
                // and Rs2GroundItemCache.isExpired() using Rs2GroundItemModel.isDespawned()
                lastGameState = GameState.LOADING;
                log.debug("Game loading - ground item despawn handled by periodic cleanup");
                
                // Perform scene scan after loading completes and cleanup persistent items
                performSceneScan(cache, Constants.GAME_TICK_LENGTH*2); // 2 ticks delay for stability                
                break;
            case LOGIN_SCREEN:
            case LOGGING_IN:
            case CONNECTION_LOST:                                             
                if (sceneScanTask != null && !sceneScanTask.isDone()) {
                    sceneScanTask.cancel(true);
                    sceneScanTask = null;
                }
                // Clear scan request when logging out and stop periodic scanning
                scanRequest.set(false); // Reset scan request
                cache.invalidateAll();
                lastGameState = event.getGameState();
                log.debug("Player logged out - cleared ground item cache and stopped operations");
                break;
            default:
                lastGameState = event.getGameState();
                break;
        }
    }
    
    /**
     * Generates a unique key for ground items based on item ID, quantity, and location.
     * 
     * @param item The tile item
     * @param location The world location
     * @return Unique key string
     */
    private String generateKey(TileItem item, net.runelite.api.coords.WorldPoint location) {
        return String.format("%d_%d_%d_%d_%d", 
                item.getId(), 
                item.getQuantity(), 
                location.getX(), 
                location.getY(), 
                location.getPlane());
    }
    
    @Override
    public Class<?>[] getHandledEventTypes() {
        return new Class<?>[]{ItemSpawned.class, ItemDespawned.class, GameStateChanged.class};
    }
    
    @Override
    public void onAttach(CacheOperations<String, Rs2GroundItemModel> cache) {
        log.debug("GroundItemUpdateStrategy attached to cache");
    }
    
    @Override
    public void onDetach(CacheOperations<String, Rs2GroundItemModel> cache) {
        log.debug("GroundItemUpdateStrategy detached from cache");
        // Cancel periodic scanning when detaching
        if (periodicSceneScanTask != null && !periodicSceneScanTask.isDone()) {
            periodicSceneScanTask.cancel(false);
            periodicSceneScanTask = null;
        }
    }
    
    @Override
    public void close() {
        log.debug("Shutting down GroundItemUpdateStrategy");
        stopPeriodicSceneScan();
        if (sceneScanTask != null && !sceneScanTask.isDone()) {
            sceneScanTask.cancel(false);
            sceneScanTask = null;
            log.debug("Cancelled active ground item scene scan task");
        }
        shutdownExecutorService();
    }
    /**
     * Shuts down the executor service gracefully, waiting for currently executing tasks to complete.
     * If the executor does not terminate within the initial timeout, it attempts a forced shutdown.
     * Logs warnings or errors if the shutdown process does not complete as expected.
     * If interrupted during shutdown, the method forces shutdown and re-interrupts the current thread.
     */
    private void shutdownExecutorService() {
        if (executorService != null && !executorService.isShutdown()) {
            // Shutdown executor service
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("Executor service did not terminate gracefully, forcing shutdown");
                    executorService.shutdownNow();
                    
                    // Wait a bit more for tasks to respond to being cancelled
                    if (!executorService.awaitTermination(2, TimeUnit.SECONDS)) {
                        log.error("Executor service did not terminate after forced shutdown");
                    }
                }
            } catch (InterruptedException e) {
                log.warn("Interrupted during executor shutdown", e);
                executorService.shutdownNow();
                Thread.currentThread().interrupt();
            }        
        }                     
    }
}
