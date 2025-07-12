package net.runelite.client.plugins.microbot.util.farming;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingHandler;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingPatch;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingWorld;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.PatchImplementation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.timetracking.Tab;

import javax.inject.Singleton;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Utility class for farming operations and patch state management
 * Provides comprehensive farming functionality for the Microbot framework
 */
@Slf4j
@Singleton
public class Rs2Farming {

    private static FarmingWorld farmingWorld;
    private static FarmingHandler farmingHandler;

    /**
     * Get the FarmingWorld instance using the injector
     * 
     * @return FarmingWorld instance
     */
    private static FarmingWorld getFarmingWorld() {
        if (farmingWorld == null) {
            try {
                farmingWorld = Microbot.getInjector().getInstance(FarmingWorld.class);
                log.debug("Successfully retrieved FarmingWorld instance from injector");
            } catch (Exception e) {
                log.error("Failed to get FarmingWorld instance from injector", e);
                return null;
            }
        }
        return farmingWorld;
    }

    /**
     * Initialize the farming handler if not already initialized
     */
    private static void ensureFarmingHandlerInitialized() {
        if (farmingHandler == null) {
            farmingHandler = new FarmingHandler(Microbot.getClient(), Microbot.getConfigManager());
            log.debug("Initialized FarmingHandler");
        }
    }

    /**
     * Get all farming patches of a specific type
     *
     * @param tab The farming tab type (HERB, TREE, ALLOTMENT, etc.)
     * @return List of farming patches for the specified tab
     */
    public static List<FarmingPatch> getPatchesByTab(Tab tab) {
        ensureFarmingHandlerInitialized();
        
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            FarmingWorld world = getFarmingWorld();
            if (world == null || world.getTabs() == null) {
                log.warn("FarmingWorld {} or tabs not initialized {}", world, world != null ? world.getTabs() : "null");
                return List.<FarmingPatch>of();
            }
            return world.getTabs().getOrDefault(tab, java.util.Set.of()).stream().collect(Collectors.toList());
        }).orElse(List.of());
    }

    /**
     * Get all spirit tree patches
     *
     * @return List of spirit tree farming patches
     */
    public static List<FarmingPatch> getSpiritTreePatches() {
        return getPatchesByTab(Tab.TREE).stream()
                .filter(patch -> patch.getImplementation() == PatchImplementation.SPIRIT_TREE)
                .collect(Collectors.toList());
    }

    /**
     * Get all herb patches
     *
     * @return List of herb farming patches
     */
    public static List<FarmingPatch> getHerbPatches() {
        return getPatchesByTab(Tab.HERB);
    }

    /**
     * Get all fruit tree patches
     *
     * @return List of fruit tree farming patches
     */
    public static List<FarmingPatch> getFruitTreePatches() {
        return getPatchesByTab(Tab.TREE).stream()
                .filter(patch -> patch.getImplementation() == PatchImplementation.FRUIT_TREE)
                .collect(Collectors.toList());
    }

    /**
     * Get all tree patches (regular trees)
     *
     * @return List of tree farming patches
     */
    public static List<FarmingPatch> getTreePatches() {
        return getPatchesByTab(Tab.TREE).stream()
                .filter(patch -> patch.getImplementation() == PatchImplementation.TREE)
                .collect(Collectors.toList());
    }

    /**
     * Predict the state of a farming patch based on time tracking data
     *
     * @param patch The farming patch to predict
     * @return The predicted crop state
     */
    public static CropState predictPatchState(FarmingPatch patch) {
        ensureFarmingHandlerInitialized();
        
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            return farmingHandler.predictPatch(patch);
        }).orElse(CropState.EMPTY);
    }

    /**
     * Get patches that are ready for action (not growing)
     *
     * @param patches List of patches to filter
     * @return List of patches that are ready (harvestable, diseased, dead, or empty)
     */
    public static List<FarmingPatch> getReadyPatches(List<FarmingPatch> patches) {
        return patches.stream()
                .filter(patch -> {
                    CropState state = predictPatchState(patch);
                    return state != CropState.GROWING;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get patches that are healthy and ready for harvest
     *
     * @param patches List of patches to filter
     * @return List of patches ready for harvest
     */
    public static List<FarmingPatch> getHarvestablePatches(List<FarmingPatch> patches) {
        return patches.stream()
                .filter(patch -> {
                    CropState state = predictPatchState(patch);
                    return state == CropState.HARVESTABLE || state == CropState.UNCHECKED;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get patches that need attention (diseased or dead)
     *
     * @param patches List of patches to filter
     * @return List of patches needing attention
     */
    public static List<FarmingPatch> getPatchesNeedingAttention(List<FarmingPatch> patches) {
        return patches.stream()
                .filter(patch -> {
                    CropState state = predictPatchState(patch);
                    return state == CropState.DISEASED || state == CropState.DEAD;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get empty patches ready for planting
     *
     * @param patches List of patches to filter
     * @return List of empty patches
     */
    public static List<FarmingPatch> getEmptyPatches(List<FarmingPatch> patches) {
        return patches.stream()
                .filter(patch -> {
                    CropState state = predictPatchState(patch);
                    return state == CropState.EMPTY;
                })
                .collect(Collectors.toList());
    }

    /**
     * Find the closest patch to the player's current location
     *
     * @param patches List of patches to search
     * @return Optional containing the closest patch, empty if no patches available
     */
    public static Optional<FarmingPatch> getClosestPatch(List<FarmingPatch> patches) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null || patches.isEmpty()) {
            return Optional.empty();
        }

        return patches.stream()
                .min((patch1, patch2) -> {
                    int distance1 = playerLocation.distanceTo(patch1.getLocation());
                    int distance2 = playerLocation.distanceTo(patch2.getLocation());
                    return Integer.compare(distance1, distance2);
                });
    }

    /**
     * Check if player has the required farming level for a specific patch type
     *
     * @param requiredLevel The required farming level
     * @return true if player meets the requirement
     */
    public static boolean hasRequiredFarmingLevel(int requiredLevel) {
        return Rs2Player.getRealSkillLevel(Skill.FARMING) >= requiredLevel;
    }

    /**
     * Check if player has completed required quests for farming
     *
     * @param questState The required quest state
     * @return true if quest requirement is met
     */
    public static boolean hasQuestRequirement(QuestState questState) {
        // This would need to be implemented based on specific quest requirements
        // For now, return true as a placeholder
        return true;
    }

    /**
     * Get patches within a certain distance of a location
     *
     * @param patches List of patches to filter
     * @param location The reference location
     * @param maxDistance Maximum distance in tiles
     * @return List of patches within the specified distance
     */
    public static List<FarmingPatch> getPatchesWithinDistance(List<FarmingPatch> patches, WorldPoint location, int maxDistance) {
        return patches.stream()
                .filter(patch -> location.distanceTo(patch.getLocation()) <= maxDistance)
                .collect(Collectors.toList());
    }

    /**
     * Check if a patch is accessible (player is within range)
     *
     * @param patch The patch to check
     * @param maxDistance Maximum distance to consider accessible
     * @return true if patch is accessible
     */
    public static boolean isPatchAccessible(FarmingPatch patch, int maxDistance) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return false;
        }
        return playerLocation.distanceTo(patch.getLocation()) <= maxDistance;
    }

    /**
     * Get a patch by its region name and varbit (for unique identification)
     *
     * @param regionName The name of the farming region
     * @param varbit The varbit ID of the patch
     * @return Optional containing the patch if found
     */
    public static Optional<FarmingPatch> getPatchByRegionAndVarbit(String regionName, int varbit) {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            FarmingWorld world = getFarmingWorld();
            if (world == null || world.getTabs() == null) {
                return Optional.<FarmingPatch>empty();
            }
            return world.getTabs().values().stream()
                    .flatMap(java.util.Set::stream)
                    .filter(patch -> patch.getVarbit() == varbit && 
                                   patch.getRegion() != null && 
                                   regionName.equals(patch.getRegion().getName()))
                    .findFirst();
        }).orElse(Optional.empty());
    }

    /**
     * Log farming patch states for debugging
     *
     * @param patches List of patches to log
     */
    public static void logPatchStates(List<FarmingPatch> patches) {
        patches.forEach(patch -> {
            CropState state = predictPatchState(patch);
            String regionName = patch.getRegion() != null ? patch.getRegion().getName() : "Unknown";
            log.info("Patch in {} - State: {}, Varbit: {}, Location: {}", 
                    regionName, state, patch.getVarbit(), patch.getLocation());
        });
    }

    /**
     * Check if farming tracking is properly initialized
     *
     * @return true if farming systems are ready
     */
    public static boolean isFarmingSystemReady() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            FarmingWorld world = getFarmingWorld();
            return world != null && 
                   world.getTabs() != null && 
                   !world.getTabs().isEmpty();
        }).orElse(false);
    }
}
