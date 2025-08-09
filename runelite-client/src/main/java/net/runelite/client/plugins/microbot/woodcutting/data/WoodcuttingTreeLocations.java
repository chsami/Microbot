package net.runelite.client.plugins.microbot.woodcutting.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.woodcutting.enums.WoodcuttingTree;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contains location data for different woodcutting trees.
 * This class provides optimized locations for each tree type with their requirements.
 */
@Getter
@RequiredArgsConstructor
public class WoodcuttingTreeLocations {
    
    /**
     * Gets the best locations for a specific tree type.
     * Locations are ordered by preference (best locations first).
     */
    public static List<LocationRequirement.LocationOption> getLocationsForTree(WoodcuttingTree tree) {
        switch (tree) {
            case TREE:
                return getRegularTreeLocations();
            case OAK:
                return getOakTreeLocations();
            case WILLOW:
                return getWillowTreeLocations();
            case TEAK_TREE:
                return getTeakTreeLocations();
            case MAPLE:
                return getMapleTreeLocations();
            case MAHOGANY:
                return getMahoganyTreeLocations();
            case YEW:
                return getYewTreeLocations();
            case BLISTERWOOD:
                return getBlisterwoodTreeLocations();
            case MAGIC:
                return getMagicTreeLocations();
            case REDWOOD:
                return getRedwoodTreeLocations();
            case EVERGREEN_TREE:
            case DEAD_TREE:
                return getRegularTreeLocations(); // Same as regular trees
            default:
                return new ArrayList<>();
        }
    }
    
    /**
     * Gets accessible locations for a specific tree type - filters out locations 
     * the player cannot access based on quest and skill requirements.
     * Uses streams for efficient filtering.
     */
    public static List<LocationRequirement.LocationOption> getAccessibleLocationsForTree(WoodcuttingTree tree) {
        return getLocationsForTree(tree).stream()
                .filter(LocationRequirement.LocationOption::hasRequirements)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets the best accessible location for a tree type based on player position.
     * Returns null if no accessible locations are found.
     */
    public static LocationRequirement.LocationOption getBestAccessibleLocation(WoodcuttingTree tree) {
        List<LocationRequirement.LocationOption> accessibleLocations = getAccessibleLocationsForTree(tree);
        
        if (accessibleLocations.isEmpty()) {
            return null;
        }
        
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation != null) {
            return accessibleLocations.stream()
                    .min((loc1, loc2) -> Integer.compare(
                            playerLocation.distanceTo(loc1.getWorldPoint()),
                            playerLocation.distanceTo(loc2.getWorldPoint())
                    ))
                    .orElse(accessibleLocations.get(0));
        }
        
        return accessibleLocations.get(0);
    }
    
    private static List<LocationRequirement.LocationOption> getRegularTreeLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Lumbridge - great for beginners, close to bank
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3192, 3223, 0), 
                "Lumbridge General Trees"
        ));
        
        // Grand Exchange area - convenient banking
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3151, 3231, 0), 
                "Grand Exchange Trees"
        ));
        
        // Varrock East - multiple trees
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3227, 3457, 0), 
                "Varrock East Trees"
        ));
        
        // Falador Trees
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3002, 3374, 0), 
                "Falador Trees"
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getOakTreeLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Lumbridge area - best for low levels
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3190, 3247, 0), 
                "Lumbridge Oak Trees"
        ));
        
        // Varrock West Bank area
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3085, 3481, 0), 
                "Varrock West Oak Trees"
        ));
        
        // Draynor Village
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3103, 3279, 0), 
                "Draynor Village Oak Trees"
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getWillowTreeLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Port Sarim - excellent with nearby deposit box
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3059, 3253, 0), 
                "Port Sarim Willow Trees"
        ));
        
        // Draynor Village - popular location
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3088, 3235, 0), 
                "Draynor Village Willow Trees"
        ));
        
        // Barbarian Outpost
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2532, 3565, 0), 
                "Barbarian Outpost Willow Trees"
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getTeakTreeLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Castle Wars area - very popular for teaks
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2335, 3048, 0), 
                "Castle Wars Teak Trees"
        ));
        
        // Ape Atoll - requires quest
        Map<Quest, QuestState> apeAtollQuests = new HashMap<>();
        apeAtollQuests.put(Quest.MONKEY_MADNESS_I, QuestState.FINISHED);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2774, 2697, 0), 
                "Ape Atoll Teak Trees",
                apeAtollQuests,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Mos Le'Harmless - requires quest
        Map<Quest, QuestState> mosLeHarmlessQuests = new HashMap<>();
        mosLeHarmlessQuests.put(Quest.CABIN_FEVER, QuestState.FINISHED);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3832, 3067, 0), 
                "Mos Le'Harmless Teak Trees",
                mosLeHarmlessQuests,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getMapleTreeLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Seers' Village - very popular location
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2720, 3465, 0), 
                "Seers' Village Maple Trees"
        ));
        
        // Miscellania
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2550, 3869, 0), 
                "Miscellania Maple Trees"
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getMahoganyTreeLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Ape Atoll - requires quest
        Map<Quest, QuestState> apeAtollQuests = new HashMap<>();
        apeAtollQuests.put(Quest.MONKEY_MADNESS_I, QuestState.FINISHED);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2716, 2710, 0), 
                "Ape Atoll Mahogany Trees",
                apeAtollQuests,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Mos Le'Harmless - requires quest
        Map<Quest, QuestState> mosLeHarmlessQuests = new HashMap<>();
        mosLeHarmlessQuests.put(Quest.CABIN_FEVER, QuestState.FINISHED);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3824, 3053, 0), 
                "Mos Le'Harmless Mahogany Trees",
                mosLeHarmlessQuests,
              new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getYewTreeLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Woodcutting Guild - requires 60 Woodcutting
        Map<Skill, Integer> wcGuildSkills = new HashMap<>();
        wcGuildSkills.put(Skill.WOODCUTTING, 60);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(1591, 3483, 0), 
                "Woodcutting Guild Yew Trees",
                new HashMap<>(),
                wcGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
                
        ));
        
        // Falador
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3052, 3272, 0), 
                "Falador Yew Trees"
        ));
        
        // Lumbridge
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3165, 3220, 0), 
                "Lumbridge Yew Trees"
        ));
        
        // Seers' Village
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2710, 3513, 0), 
                "Seers' Village Yew Trees"
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getBlisterwoodTreeLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Darkmeyer - requires Sins of the Father
        Map<Quest, QuestState> darkmeberQuests = new HashMap<>();
        darkmeberQuests.put(Quest.SINS_OF_THE_FATHER, QuestState.FINISHED);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3631, 3362, 0), 
                "Darkmeyer Blisterwood Trees",
                darkmeberQuests,
                  new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getMagicTreeLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Woodcutting Guild - requires 60 Woodcutting
        Map<Skill, Integer> wcGuildSkills = new HashMap<>();
        wcGuildSkills.put(Skill.WOODCUTTING, 60);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(1610, 3443, 0), 
                "Woodcutting Guild Magic Trees",
                new HashMap<>(),
                wcGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Sorcerer's Tower
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2704, 3397, 0), 
                "Sorcerer's Tower Magic Tree"
        ));
        
        // Mage Training Arena
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3355, 3312, 0), 
                "Mage Training Arena Magic Tree"
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getRedwoodTreeLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Woodcutting Guild - requires 60 Woodcutting
        Map<Skill, Integer> wcGuildSkills = new HashMap<>();
        wcGuildSkills.put(Skill.WOODCUTTING, 60);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(1569, 3493, 0), 
                "Woodcutting Guild Redwood Trees",
                new HashMap<>(),
                wcGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
}
