package net.runelite.client.plugins.microbot.util.cache;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.cache.model.SpiritTreeData;
import net.runelite.client.plugins.microbot.util.cache.serialization.CacheSerializable;
import net.runelite.client.plugins.microbot.util.cache.strategy.farming.SpiritTreeUpdateStrategy;
import net.runelite.client.plugins.microbot.util.cache.util.LogOutputMode;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2CacheLoggingUtils;
import net.runelite.client.plugins.microbot.util.farming.SpiritTree;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

/**
 * Thread-safe cache for spirit tree farming states and travel availability using the unified cache architecture.
 * Automatically updates when WidgetLoaded, VarbitChanged, and GameObjectSpawned events are received and supports persistence.
 * 
 * This cache tracks both built-in spirit trees (quest unlocked) and farmable spirit trees (player grown),
 * storing comprehensive state information including crop states, travel availability, and detection context.
 * 
 * The cache integrates with:
 * - Spirit tree widget detection (Adventure Log - Spirit Tree Locations)
 * - Farming varbit monitoring for spiritTree state changes
 * - Game object spawning for real-time availability detection
 * - Persistent storage for cross-session state preservation
 */
@Slf4j
public class Rs2SpiritTreeCache extends Rs2Cache<SpiritTree, SpiritTreeData> implements CacheSerializable {
    
    private static Rs2SpiritTreeCache instance;
    
    // Cache configuration constants
    private static final long SPIRIT_TREE_DATA_TTL = 30 * 60 * 1000L; // 30 minutes
    private static final long STALE_DATA_THRESHOLD = 10 * 60 * 1000L; // 10 minutes
    
    /**
     * Private constructor for singleton pattern.
     */
    private Rs2SpiritTreeCache() {
        super("SpiritTreeCache", CacheMode.EVENT_DRIVEN_ONLY);
        this.withUpdateStrategy(new SpiritTreeUpdateStrategy())
                .withPersistence("spiritTrees");            
    }
    
    /**
     * Gets the singleton instance of Rs2SpiritTreeCache.
     * 
     * @return The singleton spirit tree cache instance
     */
    public static synchronized Rs2SpiritTreeCache getInstance() {
        if (instance == null) {
            instance = new Rs2SpiritTreeCache();
        }
        return instance;
    }
    
    /**
     * Gets the cache instance for backward compatibility.
     * 
     * @return The singleton cache instance
     */
    public static Rs2Cache<SpiritTree, SpiritTreeData> getCache() {
        return getInstance();
    }
    
    // ============================================
    // Core Spirit Tree Cache Operations
    // ============================================
    
    /**
     * Gets spirit tree data from the cache or initializes it with current state.
     * 
     * @param spiritTree The spirit tree spiritTree to retrieve data for
     * @return The SpiritTreeData containing state and availability information
     */
    public static SpiritTreeData getSpiritTreeData(SpiritTree spiritTree) {
        return getInstance().get(spiritTree, () -> {
            try {                            
                // Determine initial state based on spiritTree type
                CropState cropState = CropState.HARVESTABLE;
                boolean availableForTravel = spiritTree.hasQuestRequirements();
                
                if (spiritTree.getType() == SpiritTree.SpiritTreeType.FARMABLE) {
                    cropState = spiritTree.getPatchState();
                    availableForTravel &= spiritTree.isAvailableForTravel();
                } else if (spiritTree.getType() == SpiritTree.SpiritTreeType.POH) {
                    availableForTravel &= spiritTree.hasLevelRequirement();
                }
                
                log.debug("Initial spirit tree data for {}: \n\tcropState={}, available={}", 
                         spiritTree.name(), cropState, availableForTravel);
                
                return new SpiritTreeData(spiritTree, cropState, availableForTravel);
                
            } catch (Exception e) {
                log.error("Error loading initial spirit tree data for {}: {}", spiritTree.name(), e.getMessage(), e);
                // Return default state - assume unavailable to be safe
                return new SpiritTreeData(spiritTree, null, false);
            }
        });
    }
    
    /**
     * Gets all available spirit tree locations for travel.
     * These are the origins where spirit trees are available for use.
     * 
     * @return Set of world points where spirit trees are available for travel
     */
    public static Set<WorldPoint> getAvailableOrigins() {
        return getInstance().stream()
            .filter(SpiritTreeData::isAvailableForTravel)
            .map(data -> data.getSpiritTree().getLocation())
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    }
    
    /**
     * Gets all available spirit tree destinations for travel.
     * This is an alias for getAvailableOrigins() since in spirit tree context,
     * available origins can also serve as destinations.
     * 
     * @return Set of world points where spirit trees are available as destinations
     */
    public static Set<WorldPoint> getAvailableDestinations() {
        return getAvailableOrigins();
    }
    
    /**
     * Gets all spirit tree patches that are currently available for travel.
     * 
     * @return List of available spirit tree patches
     */
    public static List<SpiritTree> getAvailableSpiritTrees() {
        return getInstance().stream()
            .filter(SpiritTreeData::isAvailableForTravel)
            .map(SpiritTreeData::getSpiritTree)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets all farmable spirit tree patches and their current states.
     * 
     * @return List of spirit tree data for farmable patches only
     */
    public static List<SpiritTreeData> getFarmableTreeStates() {
        return getInstance().stream()
            .filter(data -> data.getSpiritTree().getType() == SpiritTree.SpiritTreeType.FARMABLE)
            .collect(Collectors.toList());
    }
    
    /**
     * Gets farmable patches that require attention (diseased, dead, or ready for harvest).
     * 
     * @return List of patches requiring farming attention
     */
    public static List<SpiritTreeData> getPatchesRequiringAttention() {
        return getInstance().stream()
            .filter(data -> data.getSpiritTree().getType() == SpiritTree.SpiritTreeType.FARMABLE)
            .filter(data -> {
                CropState state = data.getCropState();
                return state == CropState.DISEASED || 
                       state == CropState.DEAD;
            })
            .collect(Collectors.toList());
    }
    
    /**
     * Gets patches that are ready for planting (empty).
     * 
     * @return List of empty farmable patches
     */
    public static List<SpiritTreeData> getEmptyPatches() {
        return getInstance().stream()
            .filter(data -> data.getSpiritTree().getType() == SpiritTree.SpiritTreeType.FARMABLE)
            .filter(data -> data.getCropState() == CropState.EMPTY)
            .collect(Collectors.toList());
    }
    
    /**
     * Checks if a spirit tree is available for travel at the given origin location.
     * Uses WorldArea for robust location matching around both the spirit tree spiritTree
     * and the query location to handle slight coordinate variations.
     * 
     * @param origin The world point to check (where a spirit tree should be standing)
     * @return true if a spirit tree is available at this location
     */
    public static boolean isOriginAvailable(WorldPoint origin) {
        if (origin == null) {
            return false;
        }
        
        // Create a search area around the query point for robust matching
        WorldArea queryArea = new WorldArea(origin, 3, 3); // 3x3 area around query point
        
        return getInstance().stream()
            .filter(SpiritTreeData::isAvailableForTravel)
            .map(data -> data.getSpiritTree().getLocation())
            .filter(Objects::nonNull)
            .anyMatch(location -> {
                // Create an area around each spirit tree location (accounting for multi-tile objects)
                WorldArea spiritTreeArea = new WorldArea(location, 3, 3); // 3x3 area around spirit tree
                // Check if the areas intersect (on same plane)
                return queryArea.intersectsWith2D(spiritTreeArea) && 
                       queryArea.getPlane() == spiritTreeArea.getPlane();
            });
    }
    
    /**
     * Checks if a spirit tree is available as a destination for travel.
     * This is an alias for isOriginAvailable() since any available origin can serve as a destination.
     * 
     * @param destination The world point to check (where you want to travel TO)
     * @return true if a spirit tree is available at this destination
     */
    public static boolean isDestinationAvailable(WorldPoint destination) {
        return isOriginAvailable(destination);
    }

	/**
	 * Checks if a spirit tree transport is available for pathfinding.
	 * This method is specifically designed for pathfinder integration.
	 * Validates that the transport is of type SPIRIT_TREE and that both the origin and destination are available.
	 *
	 * @param transport The transport object to check
	 * @return true if the transport is a valid spirit tree and both ends are available for travel
	 */
    public static boolean isSpiritTreeTransportAvailable(Transport transport) {
        if (transport == null) {
            return false;
        }
        
        if (transport.getType() != TransportType.SPIRIT_TREE) {
            log.warn("Transport type {} is not SPIRIT_TREE, cannot check availability", transport.getType());
            return false;
        }
        
        return isOriginAvailable(transport.getOrigin()) & isDestinationAvailable(transport.getDestination());
    }
    
    /**
     * Gets the closest available spirit tree to a specific location.
     * 
     * @param fromLocation The location to measure distance from
     * @return Optional containing the closest available spirit tree data
     */
    public static Optional<SpiritTreeData> getClosestAvailableTree(WorldPoint fromLocation) {
        if (fromLocation == null) {
            return Optional.empty();
        }
        
        return getInstance().stream()
            .filter(data -> data.isAvailableForTravel())
            .filter(data -> data.getSpiritTree().getLocation() != null)
            .min((data1, data2) -> {
                int dist1 = data1.getSpiritTree().getLocation().distanceTo(fromLocation);
                int dist2 = data2.getSpiritTree().getLocation().distanceTo(fromLocation);
                return Integer.compare(dist1, dist2);
            });
    }
  
    // ============================================
    // Static Update Methods
    // ============================================
    
    /**
     * Static update method that loads and updates spirit tree cache based on FarmingHandler predictions.
     * This method checks all spirit tree patches using FarmingHandler to get the most up-to-date information
     * and updates the cache accordingly. It handles both initial cache population and updates based on
     * current cached state, ensuring the most accurate information is preserved.
     * 
     * This method should be called during cache initialization and when fresh farming data is needed.
     * It uses the same logic as dynamic updates but provides static access for initial loading.
     */
    @Override
    public void update() {
        try {
            log.debug("Starting static update from FarmingHandler for spirit tree cache");
            
            Rs2SpiritTreeCache cache = getInstance();
            
            // Get current player context
            WorldPoint playerLocation = getPlayerLocationSafely();
            Integer farmingLevel = getFarmingLevelSafely();
            
            // Initialize update counters
            int updatedCount = 0;
            int newEntriesCount = 0;
            int preservedCount = 0;
            
            // Process all spirit tree patches
            for (SpiritTree spiritTree : SpiritTree.values()) {
                try {
                    // Get existing cached data
                    SpiritTreeData existingData = getSpiritTreeData(spiritTree);                    
                    // Determine update strategy based on spiritTree type and existing data
                    SpiritTreeData updatedData = createUpdatedSpiritTreeData(
                        spiritTree, existingData, playerLocation, farmingLevel);
                    
                    // Update cache if we have new or updated data
                    if (updatedData != null) {
                        // Check if this is new data or an update
                        if (existingData == null) {
                            newEntriesCount++;
                            log.debug("Added new spirit tree data for {}: {}",
                                spiritTree.name(), getDataSummary(updatedData));
                        } else if (!isDataEquivalent(existingData, updatedData)) {
                            updatedCount++;
                            log.debug("Updated spirit tree data for {}: {} -> {}",
                                spiritTree.name(), getDataSummary(existingData), getDataSummary(updatedData));
                        } else {
                            preservedCount++;
                            log.debug("Preserved existing spirit tree data for {}: {}",
                                spiritTree.name(), getDataSummary(existingData));
                            continue; // Skip update if data is equivalent
                        }
                        
                        cache.put(spiritTree, updatedData);
                    } else if (existingData != null) {
                        // Keep existing data if no new information is available
                        preservedCount++;
                        log.trace("Preserved existing spirit tree data for {} (no new information): {}",
                            spiritTree.name(), getDataSummary(existingData));
                    }
                    
                } catch (Exception e) {
                    log.warn("Failed to update spirit tree data for spiritTree {}: {}", spiritTree.name(), e.getMessage());
                }
            }
            List<SpiritTreeData> farambleTrees = getFarmableTreeStates();
            int availabilityFarmableTrees = (int) farambleTrees.stream()
                .filter(SpiritTreeData::isAvailableForTravel)
                .count();
            log.debug(getFarmingStatusSummary());
            log.debug("Static spirit tree cache update completed: \n\t{} new entries, {} updated, {} preserved entries, {} farmable trees (available for travel: {})",
                newEntriesCount, updatedCount, preservedCount, farambleTrees.size(), availabilityFarmableTrees);
            
        } catch (Exception e) {
            log.error("Failed to update spirit tree cache from FarmingHandler: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Creates updated spirit tree data based on current farming information and existing cache data.
     * This method combines FarmingHandler predictions with existing cache data,
     * preserving the most recent and accurate information.
     */
    private static SpiritTreeData createUpdatedSpiritTreeData(SpiritTree spiritTree, 
                                                             SpiritTreeData existingData,
                                                             WorldPoint playerLocation, 
                                                             Integer farmingLevel) {
        try {
            // For built-in trees, check accessibility based on quest requirements
            if (spiritTree.getType() == SpiritTree.SpiritTreeType.BUILT_IN) {
                boolean accessible = spiritTree.hasQuestRequirements();                                               
                // If we have existing data and it's recent, preserve travel availability info
                if (existingData == null || existingData.isAvailableForTravel() != accessible) {
                    
                    // Update with quest accessibility but preserve recent travel information
                    return new SpiritTreeData(
                        spiritTree,
                        null, // Built-in trees don't have crop states
                        accessible, // Must be both accessible and available
                        playerLocation,
                        false, // Not detected via widget in static update
                        false // Not detected via game object in static update                        
                    );
                } 
            }            
            // For farmable trees, use FarmingHandler to predict state
            else if (spiritTree.getType() == SpiritTree.SpiritTreeType.FARMABLE ) {
                CropState predictedState = spiritTree.getPatchState(); // Uses Rs2Farming.predictPatchState internally
                CropState lastCropState = existingData != null ? existingData.getCropState() : null;
                if (predictedState != null && !predictedState.equals(lastCropState)) {
                    // Determine travel availability based on crop state 
                    boolean detectedViaWidget = existingData != null && existingData.isDetectedViaWidget();
                    boolean detectedViaNearPatch = existingData != null && existingData.isDetectedViaNearBy();                    
                    boolean availableForTravelLast  = existingData != null && existingData.isAvailableForTravel();
                    boolean availableForTravel = spiritTree.isAvailableForTravel();
                    if ((availableForTravel != availableForTravelLast) && 
                        (lastCropState!=null && (lastCropState == CropState.UNCHECKED || lastCropState == CropState.GROWING))) {
                        log.info("Spirit tree {} is now available, last available for travel was false, and tree was predicted updating to true", spiritTree.name());
                         // Use the more specific information: dynamic detection for travel, farming handler for crop state
                        return new SpiritTreeData(
                            spiritTree,
                            predictedState, // Always update with latest farming prediction
                            availableForTravel &&  farmingLevel >= 83, // Preserve recent dynamic travel detection
                            playerLocation,
                            false, 
                            false                            
                        );
                   
                    } else {                        
                        log.info("Spirit tree {} not updated, farm state: {}, available for travel last: {}, detected via widget: {}, detected via near patch: {}, last farming state: {}", 
                            spiritTree.name(), predictedState, availableForTravelLast, detectedViaWidget, detectedViaNearPatch, lastCropState);
                        
                    }                                                               
                   
                    
                } else {
                    // If FarmingHandler can't predict the state, preserve existing data if available
                    if (existingData != null) {
                        log.trace("No farming prediction available for {}, preserving existing data", spiritTree.name());
                        return existingData; // Return existing data unchanged
                    } else {
                        log.trace("No farming prediction or existing data for {}, skipping", spiritTree.name());
                        return null; // No data to work with
                    }
                }
            }
            
            return null; // Unknown spiritTree type or no data available
            
        } catch (Exception e) {
            log.warn("Failed to create updated spirit tree data for {}: {}", spiritTree.name(), e.getMessage());
            return existingData; // Fallback to existing data on error
        }
    }
    
    /**
     * Checks if two SpiritTreeData objects are equivalent for update purposes.
     * This avoids unnecessary cache updates when data hasn't meaningfully changed.
     */
    private static boolean isDataEquivalent(SpiritTreeData data1, SpiritTreeData data2) {
        if (data1 == null || data2 == null) {
            return data1 == data2;
        }
        
        return data1.getSpiritTree().equals(data2.getSpiritTree()) &&
               java.util.Objects.equals(data1.getCropState(), data2.getCropState()) &&
               data1.isAvailableForTravel() == data2.isAvailableForTravel();
    }
    
    /**
     * Creates a summary string for spirit tree data for logging purposes.
     */
    private static String getDataSummary(SpiritTreeData data) {
        if (data == null) {
            return "null";
        }
        
        return String.format("[%s|%s|travel=%s]", 
            data.getSpiritTree().name(),
            data.getCropState() != null ? data.getCropState().name() : "N/A",
            data.isAvailableForTravel());
    }    /**
     * Forces a refresh of farmable spirit tree states only.
     */
    public static void refreshFarmableStates() {
        log.debug("Refreshing farmable spirit tree states");    
        Rs2SpiritTreeCache.getInstance().update();
    }
    
    // ============================================
    // Cache Statistics and Monitoring
    // ============================================
    
    /**
     * Gets statistics about the spirit tree cache for debugging.
     * 
     * @return Formatted statistics string
     */
    public static String getCacheStatistics() {
        Rs2SpiritTreeCache cache = getInstance();
        
        long totalEntries = cache.size();
        long availableForTravel = cache.stream().filter(SpiritTreeData::isAvailableForTravel).count();
        long farmableEntries = cache.stream()
            .filter(data -> data.getSpiritTree().getType() == SpiritTree.SpiritTreeType.FARMABLE)
            .count();
        long staleEntries = cache.stream()
            .filter(data -> data.isStale(STALE_DATA_THRESHOLD))
            .count();
        
        return String.format(
            "SpiritTreeCache Stats: Total=%d, Available=%d, Farmable=%d, Stale=%d",
            totalEntries, availableForTravel, farmableEntries, staleEntries
        );
    }
    
    /**
     * Logs the current state of all cached spirit trees for debugging.
     */
    public static void logState(LogOutputMode mode) {
        StringBuilder logContent = new StringBuilder();
        logContent.append("=== Spirit Tree Cache States ===\n");
        logContent.append(String.format("%-20s %-12s %-12s %-10s %-10s %-8s\n",
            "Name", "Type", "CropState", "Available", "Updated", "Via"));

        getInstance().stream()
            .sorted(Comparator.comparing(data -> data.getSpiritTree().name()))
            .forEach(data -> {
            String spiritTreeType = data.getSpiritTree().getType().name();
            String cropState = data.getCropState() != null ? data.getCropState().name() : "N/A";
            String lastUpdated = Instant.ofEpochMilli(data.getLastUpdated())
                .atZone(ZoneId.systemDefault())
                .format(DateTimeFormatter.ofPattern("HH:mm:ss"));
            String detection = data.isDetectedViaWidget() ? "WIDGET" : 
                     (data.isDetectedViaNearBy() ? "NEARBY" : "INIT");

            logContent.append(String.format("%-20s %-12s %-12s %-10s %-10s %-8s\n",
                data.getSpiritTree().name(),
                spiritTreeType,
                cropState,
                data.isAvailableForTravel(),
                lastUpdated,
                detection
            ));
            });

        logContent.append("=== End Spirit Tree Cache States ===\n");

        Rs2CacheLoggingUtils.outputCacheLog(
            "spirit_tree",
            logContent.toString(),
            mode
        );
    }
    
    // ============================================
    // CacheSerializable Implementation
    // ============================================
    
    @Override
    public String getConfigKey() {
        return "spiritTrees";
    }
    
    @Override
    public boolean shouldPersist() {
        return true;
    }
    
    // ============================================
    // Event Handling
    // ============================================
    

    // ============================================
    // Event Handling Delegation to Update Strategy
    // ============================================

    

    /**
     * Handle WidgetLoaded event and delegate to update strategy.
     */
    @Subscribe
    public void onWidgetLoaded(net.runelite.api.events.WidgetLoaded event) {
        getInstance().handleEvent(event);
    }

    /**
     * Handle VarbitChanged event and delegate to update strategy.
     */
    @Subscribe
    public void onVarbitChanged(net.runelite.api.events.VarbitChanged event) {
        getInstance().handleEvent(event);
    }

    /**
     * Handle GameObjectSpawned event and delegate to update strategy.
     */
    @Subscribe
    public void onGameObjectSpawned(net.runelite.api.events.GameObjectSpawned event) {
        getInstance().handleEvent(event);
    }

    /**
     * Handle GameObjectSpawned event and delegate to update strategy.
     */
    @Subscribe
    public void onGameObjectDespawned(net.runelite.api.events.GameObjectDespawned event) {
        getInstance().handleEvent(event);
    }

    /**
     * Handle game state changes for cache lifecycle management (unchanged).
     */
    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        getInstance().handleEvent(event);
    }
    
    
    // ============================================
    // Utility Methods
    // ============================================
    
    /**
     * Validates that the spirit tree cache is properly initialized and functional.
     * 
     * @return true if the cache is ready for use
     */
    public static boolean isInitialized() {
        try {
            return instance != null;
        } catch (Exception e) {
            log.error("Error checking spirit tree cache initialization: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets a summary of spirit tree farming status for user display.
     * 
     * @return Formatted farming status summary
     */
    public static String getFarmingStatusSummary() {
        List<SpiritTreeData> farmableStates = getFarmableTreeStates();
        
        if (farmableStates.isEmpty()) {
            return "No farmable spirit tree data available";
        }
        
        long planted = farmableStates.stream()
            .filter(data -> data.getCropState() != CropState.EMPTY)
            .count();
        long grown = farmableStates.stream()
            .filter(data -> data.getCropState() == CropState.HARVESTABLE || 
                           data.getCropState() == CropState.UNCHECKED)
            .count();
        long readyForHarvest = farmableStates.stream()
            .filter(data -> data.getCropState() == CropState.HARVESTABLE)
            .count();
        long needsAttention = farmableStates.stream()
            .filter(data -> data.getCropState() == CropState.DISEASED || 
                           data.getCropState() == CropState.DEAD)
            .count();
        
        return String.format("Spirit Trees: %d/%d planted, %d grown (%d harvest ready), %d need attention",
            planted, farmableStates.size(), grown, readyForHarvest, needsAttention);
    }
    
    // ============================================
    // Private Utility Methods
    // ============================================
    
    /**
     * Get current player location safely
     */
    private static WorldPoint getPlayerLocationSafely() {
        try {
            if (Microbot.getClient() != null && 
                Microbot.getClient().getGameState() == GameState.LOGGED_IN &&
                Microbot.getClient().getLocalPlayer() != null) {
                return Rs2Player.getWorldLocation();
            }
        } catch (Exception e) {
            log.trace("Could not get player location: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get current farming level safely
     */
    private static Integer getFarmingLevelSafely() {
        try {
            if (Microbot.getClient() != null && 
                Microbot.getClient().getGameState() == GameState.LOGGED_IN) {
                return Rs2Player.getRealSkillLevel(Skill.FARMING);
            }
        } catch (Exception e) {
            log.trace("Could not get farming level: {}", e.getMessage());
        }
        return null;
    }
}
