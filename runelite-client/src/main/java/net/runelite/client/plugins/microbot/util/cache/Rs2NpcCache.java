package net.runelite.client.plugins.microbot.util.cache;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Constants;
import net.runelite.api.NPC;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.strategy.entity.NpcUpdateStrategy;
import net.runelite.client.plugins.microbot.util.cache.util.LogOutputMode;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2CacheLoggingUtils;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * Thread-safe cache for tracking NPCs using the unified cache architecture.
 * Returns Rs2NpcModel objects for enhanced NPC handling.
 * Uses EVENT_DRIVEN_ONLY mode to persist NPCs until despawn or game state changes.
 * 
 * This class extends Rs2UnifiedCache and provides specific NPC caching functionality
 * with proper EventBus integration for @Subscribe methods.
 */
@Slf4j
public class Rs2NpcCache extends Rs2Cache<Integer, Rs2NpcModel> {
    
    private static Rs2NpcCache instance;
    
    // Reference to the update strategy for scene scanning
    private NpcUpdateStrategy updateStrategy;
    
    /**
     * Private constructor for singleton pattern.
     */
    private Rs2NpcCache() {
        super("NpcCache", CacheMode.EVENT_DRIVEN_ONLY);
        this.updateStrategy = new NpcUpdateStrategy();
        this.withUpdateStrategy(this.updateStrategy);
    }
    
    /**
     * Gets the singleton instance of Rs2NpcCache.
     * 
     * @return The singleton NPC cache instance
     */
    public static synchronized Rs2NpcCache getInstance() {
        if (instance == null) {
            instance = new Rs2NpcCache();
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
     * This is useful for long-running scripts that need up-to-date NPC data.
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
     * This ensures that even if events are missed, we can still retrieve NPCs from the scene.
     * 
     * @param key The NPC index key
     * @return The NPC model if found in cache or scene, null otherwise
     */
    @Override
    public Rs2NpcModel get(Integer key) {
        // First try the regular cache lookup
        Rs2NpcModel cachedResult = super.get(key);
        if (cachedResult != null) {
            return cachedResult;
        }
        
        if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
            log.warn("Client or local player is null, cannot perform scene scan");
            return null;
        }
        
        // If not in cache and cache is very small, request and perform scene scan
        if (updateStrategy.requestSceneScan(this)) {
            log.debug("Cache miss for NPC index '{}' (size: {}), performing scene scan", key, this.size());            
            // Try again after scene scan if still missing, the npc is not in the scene
            return super.get(key);
        }else {
            log.debug("Cache miss for NPC index '{}' but scene scan not a successful request, returning null", key);
        }
        
        return null;
    }
    
    // ============================================
    // Legacy API Compatibility Methods
    // ============================================
    
    /**
     * Gets an NPC by its index.
     * 
     * @param index The NPC index
     * @return Optional containing the NPC model if found
     */
    public static Optional<Rs2NpcModel> getNpcByIndex(int index) {
        return Optional.ofNullable(getInstance().get(index));
    }
    
    
    /**
     * Gets NPCs by ID - Legacy compatibility method.
     * 
     * @param npcId The NPC ID
     * @return Stream of matching NPCs
     */
    public static Stream<Rs2NpcModel> getNpcsById(int npcId) {
        return getInstance().stream()
                .filter(npc -> npc.getId() == npcId);
    }
    
    /**
     * Gets first NPC by ID - Legacy compatibility method.
     * 
     * @param npcId The NPC ID
     * @return Optional containing the first matching NPC
     */
    public static Optional<Rs2NpcModel> getFirstNpcById(int npcId) {
        return getNpcsById(npcId).findFirst();
    }
    
    /**
     * Gets all NPCs - Legacy compatibility method.
     * 
     * @return Stream of all NPCs
     */
    public static Stream<Rs2NpcModel> getAllNpcs() {
        return getInstance().stream();
    }
    
    /**
     * Gets all NPCs matching a specific name (case-insensitive).
     * 
     * @param name The NPC name to search for
     * @return Stream of matching Rs2NpcModel objects
     */
    public static Stream<Rs2NpcModel> getNpcsByName(String name) {
        return getInstance().stream()
                .filter(npc -> npc.getName() != null && 
                              npc.getName().toLowerCase().contains(name.toLowerCase()));
    }
    
    /**
     * Gets all NPCs within a certain distance from a location.
     * 
     * @param location The center location
     * @param maxDistance The maximum distance in tiles
     * @return Stream of NPCs within the specified distance
     */
    public static Stream<Rs2NpcModel> getNpcsWithinDistance(net.runelite.api.coords.WorldPoint location, int maxDistance) {
        return getInstance().stream()
                .filter(npc -> npc.getWorldLocation() != null &&
                              npc.getWorldLocation().distanceTo(location) <= maxDistance);
    }
    
    /**
     * Gets the first NPC matching the specified name.
     * 
     * @param name The NPC name
     * @return Optional containing the first matching NPC model
     */
    public static Optional<Rs2NpcModel> getFirstNpcByName(String name) {
        return getNpcsByName(name).findFirst();
    }
    
    /**
     * Gets NPCs matching a specific combat level.
     * 
     * @param combatLevel The combat level to search for
     * @return Stream of matching NPCs
     */
    public static Stream<Rs2NpcModel> getNpcsByCombatLevel(int combatLevel) {
        return getAllNpcs()
                .filter(npc -> npc.getCombatLevel() == combatLevel);
    }
    
    /**
     * Gets NPCs that are currently in combat.
     * 
     * @return Stream of NPCs in combat
     */
    public static Stream<Rs2NpcModel> getNpcsInCombat() {
        return getAllNpcs()
                .filter(npc -> npc.isInteracting());
    }
    
    /**
     * Gets the closest NPC to the player with the specified ID.
     * 
     * @param npcId The NPC ID to search for
     * @return Optional containing the closest NPC
     */
    public static Optional<Rs2NpcModel> getClosestNpcByGameId(int npcId) {
        return getNpcsById(npcId)
                .min((a, b) -> Integer.compare(a.getDistanceFromPlayer(), b.getDistanceFromPlayer()));
    }
    
    /**
     * Gets the closest NPC to the player with the specified name.
     * 
     * @param name The NPC name to search for
     * @return Optional containing the closest NPC
     */
    public static Optional<Rs2NpcModel> getClosestNpcByName(String name) {
        return getNpcsByName(name)
                .min((a, b) -> Integer.compare(a.getDistanceFromPlayer(), b.getDistanceFromPlayer()));
    }
    
    /**
     * Gets the closest NPC to a specific anchor point with the specified ID.
     * 
     * @param npcId The NPC ID to search for
     * @param anchorPoint The anchor point to calculate distance from
     * @return Optional containing the closest NPC
     */
    public static Optional<Rs2NpcModel> getClosestNpcByGameId(int npcId, net.runelite.api.coords.WorldPoint anchorPoint) {
        return getNpcsById(npcId)
                .min((a, b) -> Integer.compare(
                    a.getWorldLocation().distanceTo(anchorPoint), 
                    b.getWorldLocation().distanceTo(anchorPoint)
                ));
    }
    
    /**
     * Gets the closest NPC to a specific anchor point with the specified name.
     * 
     * @param name The NPC name to search for
     * @param anchorPoint The anchor point to calculate distance from
     * @return Optional containing the closest NPC
     */
    public static Optional<Rs2NpcModel> getClosestNpcByName(String name, net.runelite.api.coords.WorldPoint anchorPoint) {
        return getNpcsByName(name)
                .min((a, b) -> Integer.compare(
                    a.getWorldLocation().distanceTo(anchorPoint), 
                    b.getWorldLocation().distanceTo(anchorPoint)
                ));
    }
    
    /**
     * Gets the total number of cached NPCs.
     * 
     * @return The total NPC count
     */
    public static int getNpcCount() {
        return getInstance().size();
    }
    
    /**
     * Manually adds an NPC to the cache.
     * 
     * @param npc The NPC to add
     */
    public static void addNpc(NPC npc) {
        if (npc != null) {
            Rs2NpcModel npcModel = new Rs2NpcModel(npc);
            getInstance().put(npc.getIndex(), npcModel);
            log.debug("Manually added NPC: {} [{}] at {}", npc.getName(), npc.getId(), npc.getWorldLocation());
        }
    }
    
    /**
     * Manually removes an NPC from the cache.
     * 
     * @param index The NPC index to remove
     */
    public static void removeNpc(int index) {
        getInstance().remove(index);
        log.debug("Manually removed NPC with index: {}", index);
    }
    
    /**
     * Invalidates all NPC cache entries.
     */
    public static void invalidateAllNpcs() {
        getInstance().invalidateAll();
        log.debug("Invalidated all NPC cache entries");
    }
    
    /**
     * Event handler registration for the unified cache.
     * The unified cache handles events through its strategy automatically.
     */
    
        
    @Subscribe(priority = 10) // High priority to ensure we capture all NPC events
    public void onNpcSpawned(final NpcSpawned event) {        
        getInstance().handleEvent(event);
    }
    
    @Subscribe(priority = 20) // first handle despawn events to ensure NPCs are removed before any other processing
    public void onNpcDespawned(final NpcDespawned event) {        
        getInstance().handleEvent(event);
    }
    @Subscribe(priority = 40)
    public void onGameStateChanged(final GameStateChanged event) {      
        // Also let the strategy handle the event, region changes and loading of a map trigger despawn events for NPCs correctly
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
    
    /**
     * Gets cache mode - Legacy compatibility method.
     * 
     * @return The cache mode
     */
    public static CacheMode getNpcCacheMode() {
        return getInstance().getCacheMode();
    }
    
    /**
     * Gets cache statistics - Legacy compatibility method.
     * 
     * @return Statistics string for debugging
     */
    public static String getNpcCacheStatistics() {
        return getInstance().getStatisticsString();
    }
    
    /**
     * Immediately updates the cache by invoking the update method with a default parameter of 0.
     * This method overrides the parent implementation to provide a default update behavior.
     */
    @Override
    public void update() {
        update(Constants.CLIENT_TICK_LENGTH*2);

    }
    /**
     * Updates the NPC cache by clearing it and performing a scene scan.
     * This is useful for refreshing the cache after significant game state changes.
     * 
     * @param delayMs Optional delay in milliseconds before performing the update
     */
    public void update(long delayMs) {
        log.debug("Starting NPC cache update - clearing cache and performing scene scan, delay: {} ms", delayMs);
        int sizeBefore = this.size();
        
        // Clear the entire cache
        this.invalidateAll();
        
        // Perform a complete scene scan to repopulate the cache
        updateStrategy.performSceneScan(this,delayMs);
        
        int sizeAfter = this.size();
        log.debug("NPC cache update completed - NPCs before: {}, after: {}", sizeBefore, sizeAfter);
    }
    
    /**
     * Logs the current state of all cached NPCs for debugging.
     * 
     * @param outputMode Where to direct the output (CONSOLE_ONLY, FILE_ONLY, or BOTH)
     */
    public static void logState(LogOutputMode outputMode) {
        var cache = getInstance();
        var stats = cache.getStatistics();        
        // Create the log content
        StringBuilder logContent = new StringBuilder();        
        String header = String.format("=== NPC Cache State (%d entries) ===", cache.size());      
        logContent.append(header).append("\n");
        
        String statsInfo = Rs2CacheLoggingUtils.formatCacheStatistics(
            stats.getHitRate(), stats.cacheHits, stats.cacheMisses, stats.cacheMode.toString());
     
        logContent.append(statsInfo).append("\n\n");
        
        if (cache.size() == 0) {
            String emptyMsg = "Cache is empty";
            
            logContent.append(emptyMsg).append("\n");
        } else {
            // Table format for NPCs
            String[] headers = {"Name", "ID", "Combat Level", "Distance", "Location", "Health", "Cache Timestamp"};
            int[] columnWidths = {25, 8, 12, 8, 18, 8, 22};
            
            String tableHeader = Rs2CacheLoggingUtils.formatTableHeader(headers, columnWidths);
          
            logContent.append("\n").append(tableHeader);
            int maxRows = outputMode == LogOutputMode.CONSOLE_ONLY ? 50 : cache.size();
            // Sort NPCs by distance (closest first)
            cache.stream()
                .filter(npc -> {
                    try {
                        // Filter out NPCs with invalid distance calculations
                        return npc != null && npc.getDistanceFromPlayer() < Integer.MAX_VALUE;
                    } catch (Exception e) {
                        return false; // Exclude NPCs that cause exceptions
                    }
                })
                .sorted((a, b) -> {
                    try {
                        // Ensure both NPCs have valid distance data
                        if (a == null && b == null) return 0;
                        if (a == null) return 1;
                        if (b == null) return -1;
                        
                        int distanceA = a.getDistanceFromPlayer();
                        int distanceB = b.getDistanceFromPlayer();
                        
                        // Handle negative distances (invalid) by treating them as maximum distance
                        if (distanceA < 0) distanceA = Integer.MAX_VALUE;
                        if (distanceB < 0) distanceB = Integer.MAX_VALUE;
                        
                        return Integer.compare(distanceA, distanceB);
                    } catch (Exception e) {
                        // If comparison fails, use index as fallback to maintain consistency
                        return Integer.compare(
                            a != null ? a.getIndex() : Integer.MAX_VALUE,
                            b != null ? b.getIndex() : Integer.MAX_VALUE
                        );
                    }
                })
                .forEach(npc -> {
                    // Get cache timestamp for this NPC
                    Long cacheTimestamp = cache.getCacheTimestamp(Integer.valueOf(npc.getIndex()));
                    String cacheTimestampStr = cacheTimestamp != null ? 
                        Rs2Cache.formatUtcTimestamp(cacheTimestamp) : "N/A";
                    
                    String[] values = {
                        Rs2CacheLoggingUtils.truncate(npc.getName(), 24),
                        String.valueOf(npc.getId()),
                        String.valueOf(npc.getCombatLevel()),
                        String.valueOf(npc.getDistanceFromPlayer()),
                        Rs2CacheLoggingUtils.formatLocation(npc.getWorldLocation()),
                        npc.getHealthRatio() != -1 ? String.valueOf(npc.getHealthRatio()) : "N/A",
                        Rs2CacheLoggingUtils.truncate(cacheTimestampStr, 21)
                    };
                    
                    String row = Rs2CacheLoggingUtils.formatTableRow(values, columnWidths);
                  
                    logContent.append(row);
                });
            
            String tableFooter = Rs2CacheLoggingUtils.formatTableFooter(columnWidths);
        
            logContent.append(tableFooter);
            
            String limitMsg = Rs2CacheLoggingUtils.formatLimitMessage(cache.size(), maxRows);
            if (!limitMsg.isEmpty()) {
               
                logContent.append(limitMsg).append("\n");
            }
        }
        
        String footer = "=== End NPC Cache State ===";      
        logContent.append(footer).append("\n");        
        // Use the new output mode utility
        Rs2CacheLoggingUtils.outputCacheLog("npc", logContent.toString(), outputMode);
    }

    /**
     * Logs the current state of all cached NPCs for debugging.
     * 
     * @param dumpToFile Whether to also dump the information to a file
     */
    public static void logState(boolean dumpToFile) {
        net.runelite.client.plugins.microbot.util.cache.util.LogOutputMode outputMode = 
            dumpToFile ? net.runelite.client.plugins.microbot.util.cache.util.LogOutputMode.BOTH 
                      : net.runelite.client.plugins.microbot.util.cache.util.LogOutputMode.CONSOLE_ONLY;
        logState(outputMode);
    }    /**
     * Logs the current state of all cached NPCs for debugging (console only).
     */
    public static void logState() {
        logState(false);
    }
}
