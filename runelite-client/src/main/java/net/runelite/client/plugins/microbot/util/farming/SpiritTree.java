package net.runelite.client.plugins.microbot.util.farming;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.FarmingPatch;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Comprehensive enum for Spirit Tree patches and their states
 * Provides mapping of all spirit tree locations, requirements, and states
 */
@Slf4j
@Getter
@RequiredArgsConstructor
public enum SpiritTree {
    
    // Built-in spirit trees (non-farmable)
    TREE_GNOME_VILLAGE(
            "Tree Gnome Village",
            new WorldPoint(2542, 3170, 0),
            SpiritTreeType.BUILT_IN,
            List.of(Quest.TREE_GNOME_VILLAGE),
            0,
            -1,
            ObjectID.ENT, // Spirit Tree object ID i dont know why but it the id for "ENT" in tree gnome village
            "1: Tree Gnome Village"
    ),

    GNOME_STRONGHOLD(
            "Gnome Stronghold", 
            new WorldPoint(2461, 3446, 0),
            SpiritTreeType.BUILT_IN,
            List.of(Quest.THE_GRAND_TREE),
            0,
            -1,
            ObjectID.STRONGHOLD_ENT, // Spirit Tree object ID
            "2: Gnome Stronghold"
    ),

    BATTLEFIELD_KHAZARD(
            "Battlefield of Khazard",
            new WorldPoint(2555, 3259, 0),
            SpiritTreeType.BUILT_IN,
            List.of(Quest.TREE_GNOME_VILLAGE, Quest.THE_GRAND_TREE),
            0,
            -1,
            ObjectID.SPIRITTREE_SMALL, // Spirit Tree object ID  
            "3: Battlefield of Khazard"
    ),

    GRAND_EXCHANGE(
            "Grand Exchange",
            new WorldPoint(3185, 3508, 0),
            SpiritTreeType.BUILT_IN,
            List.of(Quest.TREE_GNOME_VILLAGE, Quest.THE_GRAND_TREE),
            0,
            -1,
            ObjectID.SPIRITTREE_SMALL, // Spirit Tree object ID
            "4: Grand Exchange"
    ),

    FELDIP_HILLS(
            "Feldip Hills",
            new WorldPoint(2488, 2850, 0),
            SpiritTreeType.BUILT_IN,
            List.of(Quest.TREE_GNOME_VILLAGE, Quest.THE_GRAND_TREE),
            0,
            -1,
            ObjectID.SPIRITTREE_SMALL, // Spirit Tree object ID
            "5: Feldip Hills"
    ),

    PRIFDDINAS(
            "Prifddinas",
            new WorldPoint(3274, 6123, 0),
            SpiritTreeType.BUILT_IN,
            List.of(Quest.TREE_GNOME_VILLAGE, Quest.THE_GRAND_TREE, Quest.SONG_OF_THE_ELVES),
            0,
            -1,
            ObjectID.SPIRITTREE_PRIF, // Prifddinas spirit tree
            "6: Prifddinas"
    ),

    POISON_WASTE(
            "Poison Waste",
            new WorldPoint(2339, 3109, 0),
            SpiritTreeType.BUILT_IN,
            List.of(Quest.THE_PATH_OF_GLOUPHRIE),
            0,
            -1,
            ObjectID.POG_SPIRIT_TREE_ALIVE_STATIC, // Poison Waste spirit tree
            "D: Poison Waste"
    ),

    // Farmable spirit tree patches
    PORT_SARIM(
            "Port Sarim",
            new WorldPoint(3059, 3257, 0),
            SpiritTreeType.FARMABLE,
            List.of(Quest.TREE_GNOME_VILLAGE, Quest.THE_GRAND_TREE),
            83,
            VarbitID.FARMING_TRANSMIT_A, // Port Sarim uses varbit 4771
            ObjectID.SPIRIT_TREE_FULLYGROWN, // Standard spirit patch id when fully grown  and available for travel -> healty , for states between there are other ids
            "7: Port Sarim"
    ),

    ETCETERIA(
            "Etceteria",
            new WorldPoint(2613, 3856, 0),
            SpiritTreeType.FARMABLE,
            List.of(Quest.TREE_GNOME_VILLAGE, Quest.THE_GRAND_TREE),
            83,
            VarbitID.FARMING_TRANSMIT_B, // Etceteria uses varbit 4772
            ObjectID.SPIRIT_TREE_FULLYGROWN, // Standard spirit patch id when fully grown  and available for travel -> healty , for states between there are other ids
            "8: Etceteria"
    ),

    BRIMHAVEN(
            "Brimhaven",
            new WorldPoint(2800, 3202, 0),
            SpiritTreeType.FARMABLE,
            List.of(Quest.TREE_GNOME_VILLAGE, Quest.THE_GRAND_TREE),
            83,
            VarbitID.FARMING_TRANSMIT_B, // Brimhaven spirit tree patch
            ObjectID.SPIRIT_TREE_FULLYGROWN, // Standard spirit patch id when fully grown  and available for travel -> healty , for states between there are other ids
            "9: Brimhaven"
    ),

    HOSIDIUS(
            "Hosidius",
            new WorldPoint(1693, 3542, 0),
            SpiritTreeType.FARMABLE,
            List.of(Quest.TREE_GNOME_VILLAGE, Quest.THE_GRAND_TREE),
            83,
            VarbitID.FARMING_TRANSMIT_F, // Hosidius spirit tree patch
            ObjectID.SPIRIT_TREE_FULLYGROWN, // Standard spirit patch id when fully grown  and available for travel -> healty , for states between there are other ids
            "A: Hosidius"
    ),

    FARMING_GUILD(
            "Farming Guild",
            new WorldPoint(1251, 3750, 0),
            SpiritTreeType.FARMABLE,
            List.of(Quest.TREE_GNOME_VILLAGE, Quest.THE_GRAND_TREE),
            85, // Requires 85 Farming for the Farming Guild
            VarbitID.FARMING_TRANSMIT_A, // Farming Guild spirit tree patch
            ObjectID.SPIRIT_TREE_FULLYGROWN, // Standard spirit patch id when fully grown  and available for travel -> healty , for states between there are other ids
            "B: Farming Guild"
    );
      /**
     * Enum to differentiate between built-in and farmable spirit trees
     */
    public enum SpiritTreeType {
        BUILT_IN,    // Pre-existing spirit trees unlocked by quests
        FARMABLE     // Player-grown spirit trees requiring farming
    }
    // Widget constants for spirit tree detection
    private static final int ADVENTURE_LOG_GROUP_ID = 187;
    private static final int ADVENTURE_LOG_CONTAINER_CHILD = 0;
    private static final int ADVENTURE_LOG_CONTAINER_CHILD_OPTIONS = 3;
    public static final String SPIRIT_TREE_WIDGET_TITLE = "Spirit Tree Locations";
    private final String name;
    private final WorldPoint location;
    private final SpiritTreeType type;
    private final List<Quest> requiredQuests;
    private final int requiredFarmingLevel;
    private final int varbitId; // -1 for built-in trees
    private final int objectId;
    private final String adventureLogDisplayName;

    /**
     * Check if the spirit tree is available for travel based on requirements
     *
     * @return true if the spirit tree can be used for transportation
     */
    public boolean isAvailableForTravel() {
        // Check quest requirements
        for (Quest quest : requiredQuests) {
            if (Rs2Player.getQuestState(quest) != QuestState.FINISHED) {
                return false;
            }
        }

        // Check farming level for farmable trees
        if (type == SpiritTreeType.FARMABLE) {
            if (!Rs2Farming.hasRequiredFarmingLevel(requiredFarmingLevel)) {
                return false;
            }

            // For farmable trees, check if they are planted and healthy
            return isPatchHealthyAndGrown();
        }

        // Built-in trees are always available if quest requirements are met
        return true;
    }

    /**
     * Check if a farmable spirit tree patch is healthy and fully grown
     *
     * @return true if the patch contains a healthy, fully grown spirit tree
     */
    public boolean isPatchHealthyAndGrown() {
        if (type != SpiritTreeType.FARMABLE || varbitId == -1) {
            return false;
        }

        // Get the corresponding farming patch
        Optional<FarmingPatch> patch = Rs2Farming.getSpiritTreePatches().stream()
                .filter(p -> p.getLocation().equals(this.location))
                .findFirst();

        if (patch.isEmpty()) {
            return false;
        }

        // Check the predicted state
        CropState state = Rs2Farming.predictPatchState(patch.get());
        return state == CropState.HARVESTABLE || state == CropState.UNCHECKED;
    }

    /**
     * Check if the spirit tree is currently planted (any stage)
     *
     * @return true if a spirit tree is planted in this patch
     */
    public boolean isPatchOccupied() {
        if (type != SpiritTreeType.FARMABLE || varbitId == -1) {
            return type == SpiritTreeType.BUILT_IN; // Built-in trees are always "occupied"
        }

        Optional<FarmingPatch> patch = Rs2Farming.getSpiritTreePatches().stream()
                .filter(p -> p.getLocation().equals(this.location))
                .findFirst();

        if (patch.isEmpty()) {
            return false;
        }

        CropState state = Rs2Farming.predictPatchState(patch.get());
        return state != CropState.EMPTY;
    }

    /**
     * Get the current state of the farming patch
     *
     * @return CropState of the patch, or null for built-in trees
     */
    public CropState getPatchState() {
        if (type != SpiritTreeType.FARMABLE) {
            return null;
        }

        Optional<FarmingPatch> patch = Rs2Farming.getSpiritTreePatches().stream()
                .filter(p -> p.getLocation().equals(this.location))
                .findFirst();

        return patch.map(Rs2Farming::predictPatchState).orElse(CropState.EMPTY);
    }

    /**
     * Check if the player has the required farming level for this patch
     *
     * @return true if farming level requirement is met
     */
    public boolean hasFarmingLevelRequirement() {
        return Rs2Player.getRealSkillLevel(Skill.FARMING) >= requiredFarmingLevel;
    }

    /**
     * Check if all quest requirements are met
     *
     * @return true if all required quests are completed
     */
    public boolean hasQuestRequirements() {
        return requiredQuests.stream()
                .allMatch(quest -> Rs2Player.getQuestState(quest) == QuestState.FINISHED);
    }

    /**
     * Get distance from player's current location
     *
     * @return distance in tiles, or -1 if player location is unknown
     */
    public int getDistanceFromPlayer() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return -1;
        }
        return playerLocation.distanceTo(location);
    }

    /**
     * Check if the spirit tree is within range for interaction
     *
     * @param maxDistance maximum distance to consider "in range"
     * @return true if within range
     */
    public boolean isInRange(int maxDistance) {
        int distance = getDistanceFromPlayer();
        return distance != -1 && distance <= maxDistance;
    }

    /**
     * Get all available spirit trees for travel
     *
     * @return List of spirit trees available for transportation
     */
    public static List<SpiritTree> getAvailableForTravel() {
        return Arrays.stream(values())
                .filter(SpiritTree::isAvailableForTravel)
                .collect(Collectors.toList());
    }

    /**
     * Get all farmable spirit tree patches
     *
     * @return List of farmable spirit tree patches
     */
    public static List<SpiritTree> getFarmableSpirtTrees() {
        return Arrays.stream(values())
                .filter(patch -> patch.getType() == SpiritTreeType.FARMABLE)
                .collect(Collectors.toList());
    }

    /**
     * Get all built-in spirit trees
     *
     * @return List of built-in spirit trees
     */
    public static List<SpiritTree> getBuiltInTrees() {
        return Arrays.stream(values())
                .filter(patch -> patch.getType() == SpiritTreeType.BUILT_IN)
                .collect(Collectors.toList());
    }

    /**
     * Get farmable patches that are ready for harvest
     *
     * @return List of patches ready for harvest
     */
    public static List<SpiritTree> getHarvestablePatches() {
        return getFarmableSpirtTrees().stream()
                .filter(SpiritTree::isPatchHealthyAndGrown)
                .collect(Collectors.toList());
    }

    /**
     * Get farmable patches that are empty and ready for planting
     *
     * @return List of empty patches
     */
    public static List<SpiritTree> getEmptyPatches() {
        return getFarmableSpirtTrees().stream()
                .filter(patch -> {
                    CropState state = patch.getPatchState();
                    return state == CropState.EMPTY;
                })
                .collect(Collectors.toList());
    }

    /**
     * Get patches that need attention (diseased or dead)
     *
     * @return List of patches needing attention
     */
    public static List<SpiritTree> getPatchesNeedingAttention() {
        return getFarmableSpirtTrees().stream()
                .filter(patch -> {
                    CropState state = patch.getPatchState();
                    return state == CropState.DISEASED || state == CropState.DEAD;
                })
                .collect(Collectors.toList());
    }

    /**
     * Find spirit tree by adventure log display name
     *
     * @param displayName The display name from adventure log
     * @return Optional containing the matching spirit tree
     */
    public static Optional<SpiritTree> findByAdventureLogName(String displayName) {
        return Arrays.stream(values())
                .filter(tree -> tree.getAdventureLogDisplayName().equalsIgnoreCase(displayName))
                .findFirst();
    }

    /**
     * Find spirit tree by location
     *
     * @param location The world point location
     * @return Optional containing the matching spirit tree
     */
    public static Optional<SpiritTree> findByLocation(WorldPoint location) {
        return findByArea(new WorldArea(location, 3, 3));
    }

    /**
     * Find spirit tree by area intersection (2D)
     * Uses WorldArea.intersectsWith2D to allow for fuzzy/adjacent matching.
     *
     * @param area The WorldArea to check for intersection
     * @return Optional containing the matching spirit tree
     */
    public static Optional<SpiritTree> findByArea(WorldArea area) {
        return Arrays.stream(values())
                .filter(tree -> {
                    WorldArea treeArea = new WorldArea(tree.getLocation(), 3, 3);
                    return area.intersectsWith2D(treeArea);
                })
                .findFirst();
    }

    /**
     * Find the closest spirit tree to a given location
     *
     * @param location The reference location
     * @return Optional containing the closest spirit tree
     */
    public static Optional<SpiritTree> findClosestTo(WorldPoint location) {
        return Arrays.stream(values())
                .min((tree1, tree2) -> {
                    int distance1 = location.distanceTo(tree1.getLocation());
                    int distance2 = location.distanceTo(tree2.getLocation());
                    return Integer.compare(distance1, distance2);
                });
    }

    /**
     * Extract available spirit tree from the adventure log widget
     * This method checks the adventure log interface to determine which spirit trees
     * are currently available for travel
     *
     * @return List of available spirit tree destinations
     */
    public static List<SpiritTree> extractAvailableFromWidget() {
        if (!Rs2Widget.hasWidgetText(SPIRIT_TREE_WIDGET_TITLE, ADVENTURE_LOG_CONTAINER_CHILD_OPTIONS, ADVENTURE_LOG_CONTAINER_CHILD, false)) {
            log.info("Adventure log widget does not contain spirit tree information.");
            return List.of();
        }
        // Simplified widget access without ComponentID dependency
        Widget adventureLog = Rs2Widget.getWidget(ADVENTURE_LOG_GROUP_ID, ADVENTURE_LOG_CONTAINER_CHILD_OPTIONS); // Adventure Log container
        if (adventureLog == null || adventureLog.isHidden()) {
            log.info("Adventure log container is not visible or accessible for spirit tree extraction.");
            return List.of();
        }
        
        try {
            // Simplified text extraction approach
            List<String> widgetTexts = new ArrayList<>();
            if (adventureLog.getText() != null) {
                widgetTexts.add(adventureLog.getText());
            }
            
            // Check children widgets for text content
            Widget[] children = adventureLog.getChildren();
            if (children != null) {
                for (Widget child : children) {
                    if (child != null 
                        && child.getText() != null 
                        && !child.getText().contains("<col=5f5f5f>") 
                        && !child.getText().toLowerCase().contains("cancel")) { // if the color is not greyed out
                        log.debug("Found child widget text: {}", child.getText());
                        widgetTexts.add(child.getText());
                    }
                }
            }

            return widgetTexts.stream()
                    .map(text -> findByAdventureLogName(text.trim()))
                    .filter(Optional::isPresent)
                    .map(Optional::get)
                    .collect(Collectors.toList());
                    
        } catch (Exception e) {
            log.error("Error extracting spirit tree destinations from widget: {}", e.getMessage());
            return List.of();
        }
    }

    /**
     * Log current state of all spirit tree patches for debugging
     */
    public static void logAllPatchStates() {
        log.info("=== Spirit Tree Patch States ===");
        for (SpiritTree patch : values()) {
            String status;
            if (patch.getType() == SpiritTreeType.BUILT_IN) {
                status = patch.hasQuestRequirements() ? "Available" : "Quest Requirements Not Met";
            } else {
                CropState state = patch.getPatchState();
                status = state != null ? state.toString() : "Unknown";
                if (state == CropState.HARVESTABLE || state == CropState.UNCHECKED) {
                    status += " (Ready for Travel)";
                }
            }
            
            log.info("{} - {} - Level Req: {} - Available: {}", 
                    patch.getName(), status, patch.getRequiredFarmingLevel(), patch.isAvailableForTravel());
        }
        log.info("===============================");
    }

  
}
