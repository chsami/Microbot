package net.runelite.client.plugins.microbot.util.farming;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.util.cache.Rs2SpiritTreeCache;
import net.runelite.client.plugins.microbot.util.cache.model.SpiritTreeData;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Helper class for Spirit Tree operations and pathfinder integration.
 * Provides a high-level convenience API that delegates to the Rs2SpiritTreeCache
 * for consistent and performant access to spirit tree state information.
 */
@Slf4j
public class SpiritTreeHelper {

    /**
     * Check if a spirit tree transport is available.
     * This method integrates with the pathfinder to determine transport availability.
     *
     * @param transport The transport object to check (validates origin availability)
     * @return true if the spirit tree at the origin is available for travel
     */
    public static boolean isSpiritTreeTransportAvailable(Transport transport) {
        return Rs2SpiritTreeCache.isSpiritTreeTransportAvailable(transport);
    }

    /**
     * Get all available spirit tree origins as world points.
     * Used by pathfinder to determine valid transport starting points.
     *
     * @return Set of world points where spirit trees are available for use
     */
    public static Set<WorldPoint> getAvailableOrigins() {
        return Rs2SpiritTreeCache.getAvailableOrigins();
    }
    
    /**
     * Get all available spirit tree destinations as world points.
     * Used by pathfinder to determine valid transport destinations.
     * Note: This is an alias for getAvailableOrigins() since available origins can serve as destinations.
     *
     * @return Set of world points where spirit trees are available for travel
     */
    public static Set<WorldPoint> getAvailableDestinations() {
        return Rs2SpiritTreeCache.getAvailableDestinations();
    }

    /**
     * Check if a specific world point has an available spirit tree (origin check).
     *
     * @param origin The origin point to check (where a spirit tree should be standing)
     * @return true if a spirit tree is available at this location
     */
    public static boolean isOriginAvailable(WorldPoint origin) {
        return Rs2SpiritTreeCache.isOriginAvailable(origin);
    }
    
    /**
     * Check if a specific world point has an available spirit tree (destination check).
     * This is an alias for isOriginAvailable() for backward compatibility.
     *
     * @param destination The destination to check
     * @return true if a spirit tree is available at this location
     */
    public static boolean isDestinationAvailable(WorldPoint destination) {
        return Rs2SpiritTreeCache.isDestinationAvailable(destination);
    }

    /**
     * Get the closest available spirit tree to the player.
     *
     * @return Optional containing the closest available spirit tree
     */
    public static Optional<SpiritTree> getClosestAvailableTree() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return Optional.empty();
        }

        return Rs2SpiritTreeCache.getClosestAvailableTree(playerLocation)
                .map(SpiritTreeData::getPatch);
    }

    /**
     * Check spirit tree patches that need farming attention.
     * Useful for farming scripts to prioritize patch management.
     *
     * @return List of patches requiring attention (diseased, dead, or ready for harvest)
     */
    public static List<SpiritTree> getPatchesRequiringAttention() {
        return Rs2SpiritTreeCache.getPatchesRequiringAttention().stream()
                .map(SpiritTreeData::getPatch)
                .collect(Collectors.toList());
    }

    /**
     * Get priority patches for planting.
     * Returns empty patches sorted by farming level requirement and strategic value.
     *
     * @return List of patches prioritized for planting
     */
    public static List<SpiritTree> getPriorityPlantingPatches() {
        return Rs2SpiritTreeCache.getEmptyPatches().stream()
                .map(SpiritTreeData::getPatch)
                .filter(SpiritTree::hasFarmingLevelRequirement)
                .filter(SpiritTree::hasQuestRequirements)
                .sorted((patch1, patch2) -> {
                    // Sort by strategic value: lower farming requirement first, then by convenience
                    int levelDiff = Integer.compare(patch1.getRequiredFarmingLevel(), patch2.getRequiredFarmingLevel());
                    if (levelDiff != 0) {
                        return levelDiff;
                    }
                    
                    // Prioritize by strategic locations (Grand Exchange area, commonly used locations)
                    return Integer.compare(getStrategicValue(patch1), getStrategicValue(patch2));
                })
                .collect(Collectors.toList());
    }

    /**
     * Calculate strategic value of a spirit tree location
     * Higher values indicate more strategically valuable locations
     *
     * @param patch The spirit tree patch to evaluate
     * @return Strategic value score
     */
    private static int getStrategicValue(SpiritTree patch) {
        switch (patch) {
            case PORT_SARIM:
                return 10; // High value - good access to boats, farming
            case FARMING_GUILD:
                return 9;  // High value - farming hub
            case HOSIDIUS:
                return 8;  // Good value - Zeah access
            case BRIMHAVEN:
                return 7;  // Medium value - fruit tree nearby
            case ETCETERIA:
                return 6;  // Lower value - remote location
            default:
                return 5;  // Default value
        }
    }

    /**
     * Check if spirit tree patches should be refreshed in the pathfinder.
     * This method can be called periodically to update transport availability.
     *
     * @return true if any farmable spirit tree states have changed significantly
     */
    public static boolean shouldRefreshSpiritTreeStates() {
        // Check if any farmable patches have recently changed state using cached data
        List<SpiritTreeData> farmableStates = Rs2SpiritTreeCache.getFarmableTreeStates();
        
        // For now, we rely on the cache's automatic update system
        // This method can be enhanced with more sophisticated change detection
        long staleDataCount = farmableStates.stream()
                .filter(data -> data.isStale(10 * 60 * 1000L)) // 10 minutes
                .count();
                
        if (staleDataCount > 0) {
            log.debug("Found {} stale spirit tree entries, refresh recommended", staleDataCount);
            return true;
        }
        
        return false;
    }

    /**
     * Get a summary of spirit tree farming status.
     * Useful for reporting to users or logging.
     *
     * @return Formatted string with farming status summary
     */
    public static String getFarmingStatusSummary() {
        return Rs2SpiritTreeCache.getFarmingStatusSummary();
    }

    /**
     * Force refresh of farming patch states.
     * This can be called when the player visits a farming patch to update state.
     */
    public static void refreshPatchStates() {
        log.debug("Refreshing spirit tree patch states via cache");
        Rs2SpiritTreeCache.refreshFarmableStates();
    }

    /**
     * Check if player can use spirit tree transportation.
     * Validates basic requirements for spirit tree travel.
     *
     * @return true if player can use spirit trees
     */
    public static boolean canUseSpirituTrees() {
        // Check if player has completed the basic quest requirement
        return SpiritTree.TREE_GNOME_VILLAGE.hasQuestRequirements();
    }

    /**
     * Get recommended next action for spirit tree farming.
     * Provides guidance for farming scripts.
     *
     * @return String describing the recommended action
     */
    public static String getRecommendedFarmingAction() {
        if (!Rs2SpiritTreeCache.isInitialized()) {
            return "Initialize spirit tree cache system";
        }

        List<SpiritTreeData> needsAttention = Rs2SpiritTreeCache.getPatchesRequiringAttention();
        if (!needsAttention.isEmpty()) {
            SpiritTreeData data = needsAttention.get(0);
            SpiritTree patch = data.getPatch();
            CropState state = data.getCropState();
            
            if (state != null) {
                switch (state) {
                    case HARVESTABLE:
                    case UNCHECKED:
                        return "Check health of " + patch.getName() + " spirit tree";
                    case DISEASED:
                        return "Cure diseased spirit tree at " + patch.getName();
                    case DEAD:
                        return "Clear dead spirit tree at " + patch.getName();
                    default:
                        break;
                }
            }
        }

        List<SpiritTree> empty = getPriorityPlantingPatches();
        if (!empty.isEmpty()) {
            SpiritTree patch = empty.get(0);
            return "Plant spirit tree at " + patch.getName() + " (requires level " + patch.getRequiredFarmingLevel() + ")";
        }

        return "All spirit tree patches are being maintained";
    }
}
