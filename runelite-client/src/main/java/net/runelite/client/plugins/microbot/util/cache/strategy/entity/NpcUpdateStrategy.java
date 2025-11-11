package net.runelite.client.plugins.microbot.util.cache.strategy.entity;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.ScheduledExecutorService;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheUpdateStrategy;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

/**
 * Enhanced cache update strategy for NPC data.
 * Handles NPC spawn/despawn events and provides scene scanning.
 * Follows the same pattern as ObjectUpdateStrategy for consistency.
 */
@Slf4j
public class NpcUpdateStrategy implements CacheUpdateStrategy<Integer, Rs2NpcModel> {
    
    GameState lastGameState = null;
    
    // ScheduledExecutorService for non-blocking operations
    private final ScheduledExecutorService executorService;
    private ScheduledFuture<?> periodicSceneScanTask;
    private ScheduledFuture<?> sceneScanTask;
    
    // Scene scan tracking
    private final AtomicBoolean scanActive = new AtomicBoolean(false);
    private final AtomicBoolean scanRequest = new AtomicBoolean(false);    
    private volatile long lastSceneScan = 0;
    private static final long MIN_SCAN_INTERVAL_MS = Constants.GAME_TICK_LENGTH;
    
    /**
     * Constructor initializes the executor service for background tasks.
     */
    public NpcUpdateStrategy() {
        this.executorService = Executors.newScheduledThreadPool(2, r -> {
            Thread thread = new Thread(r, "NpcUpdateStrategy-" + System.currentTimeMillis());
            thread.setDaemon(true);
            return thread;
        });
        log.debug("NpcUpdateStrategy initialized with ScheduledExecutorService");
    }
    
    @Override
    public void handleEvent(final Object event, CacheOperations<Integer, Rs2NpcModel> cache) {
        if (executorService == null || executorService.isShutdown()) {
            return; // Don't process events if shut down
        }                
        processEventInternal(event, cache);        
    }
    
    /**
     * Internal method to process events - runs on background thread.
     */
    private void processEventInternal(Object event, CacheOperations<Integer, Rs2NpcModel> cache) {
        if (event instanceof NpcSpawned) {
            handleNpcSpawned((NpcSpawned) event, cache);
        } else if (event instanceof NpcDespawned) {
            handleNpcDespawned((NpcDespawned) event, cache);
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
    public void performSceneScan(CacheOperations<Integer, Rs2NpcModel> cache, long delayMs) {
        if (executorService == null || executorService.isShutdown() ) {
            log.debug("Skipping NPC scene scan - strategy is shut down or not logged in");
            return;
        }
        
        // Respect minimum scan interval unless forced
        if ((System.currentTimeMillis() - lastSceneScan) < MIN_SCAN_INTERVAL_MS) {
            log.debug("Skipping NPC scene scan due to minimum interval not reached");
            scanActive.set(false);
            return;
        }
        
        if (sceneScanTask != null && !sceneScanTask.isDone()) {
            log.debug("Skipping NPC scene scan - already scheduled or running");
            return; // Don't perform scan if already scheduled or running
        }
        
        if (scanActive.compareAndSet(false, true)) {
            // Submit scene scan to executor service for non-blocking execution
            sceneScanTask = executorService.schedule(() -> {
                try {
                    performSceneScanInternal(cache);
                } catch (Exception e) {
                    log.error("Error during NPC scene scan", e);
                } finally {
                    scanActive.set(false);
                    scanRequest.set(false); // Reset request after scan
                }
            }, delayMs, TimeUnit.MILLISECONDS); // delay before scan
        } else {
            log.debug("Skipping NPC scene scan - already active");
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
    public void schedulePeriodicSceneScan(CacheOperations<Integer, Rs2NpcModel> cache, long intervalSeconds) {
        if (executorService == null || executorService.isShutdown()) {
            log.warn("Cannot schedule periodic scan - strategy is shut down");
            return;
        }
        
        // Cancel existing task if any
        stopPeriodicSceneScan();
        
        periodicSceneScanTask = executorService.scheduleWithFixedDelay(() -> {
            try {
                if (scanActive.compareAndSet(false, true)) {
                    if (scanRequest.get() && Microbot.isLoggedIn()) {
                        log.debug("Periodic NPC scene scan triggered");
                        performSceneScanInternal(cache);
                    }
                } else {
                    log.debug("Skipping scheduled NPC scene scan - already active");
                }
            } catch (Exception e) {
                log.error("Error in periodic NPC scene scan", e);
            } finally {
                scanActive.set(false);
            }
        }, intervalSeconds, intervalSeconds, TimeUnit.SECONDS);
        
        log.debug("Scheduled periodic NPC scene scan every {} seconds", intervalSeconds);
    }
    
    /**
     * Stops periodic scene scanning.
     */
    public void stopPeriodicSceneScan() {
        if (periodicSceneScanTask != null && !periodicSceneScanTask.isDone()) {
            periodicSceneScanTask.cancel(false);
            periodicSceneScanTask = null;
            log.debug("Stopped periodic NPC scene scanning");
        }
    }
    
    
    /**
     * Checks if a scene scan is needed based on cache state.
     * 
     * @param cache The cache to check
     * @return true if a scan would be beneficial
     */    
    public boolean requestSceneScan(CacheOperations<Integer, Rs2NpcModel> cache) {
        if (scanActive.get()) {
            log.debug("Skipping scene scan request - already active");
            return false; // Don't request scan if already active
        }
        if (scanRequest.compareAndSet(false, true)) {
            log.debug("NPC scene scan requested");
            performSceneScan(cache, 5); // Perform with 5ms delay for stability
        }
        if (!Microbot.getClient().isClientThread()){
            sleepUntil(()->!scanRequest.get(), 1000); // Wait until scan is requested and reset
        }        
        return !scanRequest.get(); // Return true if scan was requested,reseted               
    }
    
    /**
     * Performs the actual scene scan to synchronize NPCs with the current scene.
     * This method both adds new NPCs found in the scene AND removes cached NPCs no longer present.
     * Provides complete scene synchronization in a single operation.
     */
    private void performSceneScanInternal(CacheOperations<Integer, Rs2NpcModel> cache) {
        try {
            long currentTime = System.currentTimeMillis();
            
            // Check minimum interval to prevent excessive scanning
            if (currentTime - lastSceneScan < MIN_SCAN_INTERVAL_MS) {
                log.debug("Skipping NPC scene scan - too soon since last scan");
                scanActive.set(false);
                return;
            }
            
            Player player = Microbot.getClient().getLocalPlayer();
            WorldView wv = null;
            if (player == null) {
                wv =  (Microbot.getClient().getTopLevelWorldView());
                if (wv == null) {
                    log.debug("Cannot perform NPC scene scan - no world view available");
                    scanActive.set(false);
                    return;
                }
            }else {
                wv = player.getWorldView();
            }
            
            // Build a set of all currently existing NPC indices from the scene
            java.util.Set<Integer> currentSceneIndices = new java.util.HashSet<>();
            int addedNpcs = 0;
            log.debug("Starting NPC scene synchronization (cache size: {})", cache.size());
            
            // Phase 1: Scan scene and add new NPCs
            for (NPC npc : wv.npcs()) {
                if (npc != null && npc.getId() != -1) { // Use ID instead of getName() to avoid client thread requirement
                    currentSceneIndices.add(npc.getIndex()); // Track all scene NPCs
                    
                    // Only add if not already in cache to avoid recursive calls
                    if (!cache.containsKey(npc.getIndex())) {
                        Rs2NpcModel npcModel = new Rs2NpcModel(npc);
                        cache.put(npc.getIndex(), npcModel);
                        addedNpcs++;
                    }
                }
            }
            
            // Phase 2: Remove cached NPCs no longer in scene
            int removedNpcs = 0;
            if (!currentSceneIndices.isEmpty()) {
                // Find cached NPCs that are no longer in the scene using CacheOperations streaming
                java.util.List<Integer> keysToRemove = cache.entryStream()
                    .map(java.util.Map.Entry::getKey)
                    .filter(key -> !currentSceneIndices.contains(key))
                    .collect(java.util.stream.Collectors.toList());
                
                // Remove the NPCs that are no longer in scene
                for (Integer key : keysToRemove) {
                    // Use raw cached value to avoid triggering recursive scene scans
                    Rs2NpcModel npc = cache.getRawValue(key);
                    cache.remove(key);
                    if (npc != null) {
                        removedNpcs++;
                        log.trace("Removed NPC not in scene: ID {} (index: {})", npc.getId(), key);
                    }
                }
            }
            
            // Log comprehensive results
            if (addedNpcs > 0 || removedNpcs > 0) {
                log.debug("NPC scene synchronization completed - added {} NPCs, removed {} NPCs (total cache size: {})", 
                        addedNpcs, removedNpcs, cache.size());
            } else {
                log.debug("NPC scene synchronization completed - no changes made");
            }
            scanRequest.set(false); // Reset request after scan
        } catch (Exception e) {
            log.error("Error during NPC scene synchronization", e);
        } finally {
            lastSceneScan =  System.currentTimeMillis();   // Update last scan time;                
            scanActive.set(false);                
        }
    }
    
    private void handleNpcSpawned(NpcSpawned event, CacheOperations<Integer, Rs2NpcModel> cache) {
        NPC npc = event.getNpc();
        if (npc != null) {
            Rs2NpcModel npcModel = new Rs2NpcModel(npc);
            cache.put(npc.getIndex(), npcModel);
            log.trace("Added NPC ID {} (index: {}) to cache via spawn event", npc.getId(), npc.getIndex());
        }
    }
    
    private void handleNpcDespawned(NpcDespawned event, CacheOperations<Integer, Rs2NpcModel> cache) {
        NPC npc = event.getNpc();
        if (npc != null) {
            cache.remove(npc.getIndex());
            log.trace("Removed NPC ID {} (index: {}) from cache via despawn event", npc.getId(), npc.getIndex());
        }
    }
    
    /**
     * Handles game state changes for NPC cache management.
     * 
     * <p><strong>Why NPCs don't require scene scanning on LOGGED_IN/LOADING:</strong></p>
     * <ul>
     *   <li>NPCs are automatically managed by the RuneLite client's event system</li>
     *   <li>{@link net.runelite.api.events.NpcSpawned} events are reliably triggered when NPCs appear</li>
     *   <li>{@link net.runelite.api.events.NpcDespawned} events are reliably triggered when NPCs disappear</li>
     *   <li>Region changes automatically trigger proper spawn/despawn events for all NPCs</li>
     *   <li>This makes manual scene scanning unnecessary and potentially wasteful</li>
     * </ul>
     * 
     * <p>This is different from ground items, which have timing-based despawn mechanics
     * that require additional handling through periodic cleanup.</p>
     * 
     * @param event The game state change event
     * @param cache The NPC cache operations interface
     */
    private void handleGameStateChanged(GameStateChanged event, CacheOperations<Integer, Rs2NpcModel> cache) {
        switch (event.getGameState()) {
            case LOGGED_IN:
                // NPCs are handled entirely by spawn/despawn events - no manual scanning needed
                lastGameState = GameState.LOGGED_IN;
                log.debug("Player logged in - NPC events will handle population automatically");
                break;
            case LOADING:
                // Region changes during loading automatically trigger NPC despawn/spawn events
                lastGameState = GameState.LOADING;
                log.debug("Game loading - NPC events will handle region change automatically");
                break;
            case LOGIN_SCREEN:
            case LOGGING_IN:
            case CONNECTION_LOST:
                // Clear any ongoing operations and invalidate cache on logout
                if (sceneScanTask != null && !sceneScanTask.isDone()) {
                    sceneScanTask.cancel(true);
                    sceneScanTask = null;
                }
                cache.invalidateAll();
                lastGameState = event.getGameState();
                log.debug("Player logged out - cleared NPC cache and stopped operations");
                break;
            default:
                lastGameState = event.getGameState();
                break;
        }
    }
    
    @Override
    public Class<?>[] getHandledEventTypes() {
        return new Class<?>[]{NpcSpawned.class, NpcDespawned.class, GameStateChanged.class};
    }
    
    @Override
    public void onAttach(CacheOperations<Integer, Rs2NpcModel> cache) {
        log.debug("NpcUpdateStrategy attached to cache");
    }
    
    @Override
    public void onDetach(CacheOperations<Integer, Rs2NpcModel> cache) {
        log.debug("NpcUpdateStrategy detached from cache");
        // Cancel periodic scanning when detaching
        if (periodicSceneScanTask != null && !periodicSceneScanTask.isDone()) {
            periodicSceneScanTask.cancel(false);
            periodicSceneScanTask = null;
        }
    }
    
    @Override
    public void close() {
        log.debug("Shutting down NpcUpdateStrategy");
        stopPeriodicSceneScan();
        if (sceneScanTask != null && !sceneScanTask.isDone()) {
            sceneScanTask.cancel(false);
            sceneScanTask = null;
            log.debug("Cancelled active NPC scene scan task");
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
