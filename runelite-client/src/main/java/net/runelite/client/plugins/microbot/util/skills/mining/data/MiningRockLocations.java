package net.runelite.client.plugins.microbot.util.skills.mining.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mining.enums.Rocks;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationOption;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.ResourceLocationOption;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.api.gameval.ItemID;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contains location data for different mining rock types.
 * This class provides optimized mining locations for each ore type with their requirements.
 * Data sourced from OSRS Wiki: https://oldschool.runescape.wiki/w/Mines
 */
@Getter
@RequiredArgsConstructor
public class MiningRockLocations {
    
    /**
     * Gets the best locations for a specific rock/ore type.
     * Locations are ordered by preference (best locations first).
     */
    public static List<LocationOption> getLocationsForRock(Rocks rock) {
        switch (rock) {
            case TIN:
                return getTinRockLocations();
            case COPPER:
                return getCopperRockLocations();
            case CLAY:
                return getClayRockLocations();
            case IRON:
                return getIronRockLocations();
            case SILVER:
                return getSilverRockLocations();
            case COAL:
                return getCoalRockLocations();
            case GOLD:
                return getGoldRockLocations();
            case GEM:
                return getGemRockLocations();
            case MITHRIL:
                return getMithrilRockLocations();
            case ADAMANTITE:
                return getAdamantiteRockLocations();
            case RUNITE:
                return getRuniteRockLocations();
            case BASALT:
                return getBasaltRockLocations();
            default:
                return new ArrayList<>();
        }
    }
    
    /**
     * Gets accessible locations for a specific rock type - filters out locations 
     * the player cannot access based on quest and skill requirements.
     * Uses streams for efficient filtering.
     */
    public static List<LocationOption> getAccessibleLocationsForRock(Rocks rock) {
        return getLocationsForRock(rock).stream()
                .filter(LocationOption::hasRequirements)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets the best accessible location for a rock type based on player position.
     * Returns null if no accessible locations are found.
     */
    public static LocationOption getBestAccessibleLocation(Rocks rock) {
        List<LocationOption> accessibleLocations = getAccessibleLocationsForRock(rock);
        
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
    
    /**
     * Gets the best locations for a specific rock type with resource information.
     * Locations are ordered by preference (best locations first).
     * Returns ResourceLocationOption instances with rock count data.
     */
    public static List<ResourceLocationOption> getResourceLocationsForRock(Rocks rock) {
        switch (rock) {
            case TIN:
                return getTinRockResourceLocations();
            case COPPER:
                return getCopperRockResourceLocations();
            case IRON:
                return getIronRockResourceLocations();
            case COAL:
                return getCoalRockResourceLocations();
            // Add other rock types as needed
            default:
                return new ArrayList<>();
        }
    }
    
    /**
     * Gets accessible resource locations for a specific rock type - filters out locations 
     * the player cannot access based on quest and skill requirements.
     */
    public static List<ResourceLocationOption> getAccessibleResourceLocationsForRock(Rocks rock) {
        return getResourceLocationsForRock(rock).stream()
                .filter(ResourceLocationOption::hasRequirements)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets the best accessible resource location for a rock type with minimum resource requirements.
     * Prioritizes accessible locations, then resource count, then proximity to player.
     */
    public static ResourceLocationOption getBestAccessibleResourceLocation(Rocks rock, int minResources) {
        List<ResourceLocationOption> accessibleLocations = getAccessibleResourceLocationsForRock(rock);
        
        if (accessibleLocations.isEmpty()) {
            return null;
        }
        
        // Filter by minimum resource requirements
        List<ResourceLocationOption> suitableLocations = accessibleLocations.stream()
                .filter(location -> location.hasMinimumResources(minResources))
                .collect(Collectors.toList());
        
        if (suitableLocations.isEmpty()) {
            // If no locations meet minimum requirements, use the best available
            suitableLocations = accessibleLocations;
        }
        
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation != null) {
            return suitableLocations.stream()
                    .max((loc1, loc2) -> Double.compare(
                            loc1.calculateResourceEfficiencyScore(playerLocation),
                            loc2.calculateResourceEfficiencyScore(playerLocation)
                    ))
                    .orElse(suitableLocations.get(0));
        }
        
        // If no player location, prefer locations with more resources
        return suitableLocations.stream()
                .max((loc1, loc2) -> Integer.compare(
                        loc1.getNumberOfResources(),
                        loc2.getNumberOfResources()
                ))
                .orElse(suitableLocations.get(0));
    }
    
    // Example resource location methods - showing tin and copper as examples
    private static List<ResourceLocationOption> getTinRockResourceLocations() {
        List<ResourceLocationOption> locations = new ArrayList<>();
        
        // Lumbridge Swamp West Mine - great for beginners, close to bank (estimated 4-5 tin rocks)
        locations.add(new ResourceLocationOption(
                new WorldPoint(3149, 3148, 0), 
                "Lumbridge Swamp West Mine",
                false, // F2P location
                5 // Number of tin rock spawns
        ));
        
        // Al Kharid Mine - close to Al Kharid bank (estimated 3-4 tin rocks)
        locations.add(new ResourceLocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine",
                false, // F2P location
                4 // Number of tin rock spawns
        ));
        
        return locations;
    }
    
    private static List<ResourceLocationOption> getCopperRockResourceLocations() {
        List<ResourceLocationOption> locations = new ArrayList<>();
        
        // Al Kharid Mine - excellent for beginners, close to bank (estimated 6-7 copper rocks)
        locations.add(new ResourceLocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine",
                false, // F2P location
                7 // Number of copper rock spawns
        ));
        
        // Lumbridge Swamp West Mine - good for F2P (estimated 4-5 copper rocks)
        locations.add(new ResourceLocationOption(
                new WorldPoint(3149, 3148, 0), 
                "Lumbridge Swamp West Mine",
                false, // F2P location
                5 // Number of copper rock spawns
        ));
        
        return locations;
    }
    
    private static List<ResourceLocationOption> getIronRockResourceLocations() {
        List<ResourceLocationOption> locations = new ArrayList<>();
        
        // Add iron rock locations with resource counts
        // This would be expanded with actual iron mining locations
        
        return locations;
    }
    
    private static List<ResourceLocationOption> getCoalRockResourceLocations() {
        List<ResourceLocationOption> locations = new ArrayList<>();
        
        // Add coal rock locations with resource counts
        // This would be expanded with actual coal mining locations
        
        return locations;
    }
    
    private static List<LocationOption> getTinRockLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Lumbridge Swamp West Mine - great for beginners, close to bank
        locations.add(new LocationOption(
                new WorldPoint(3149, 3148, 0), 
                "Lumbridge Swamp West Mine", false
        ));
        
        // Varrock South West Mine - close to Varrock west bank
        locations.add(new LocationOption(
                new WorldPoint(3181, 3377, 0), 
                "Varrock South West Mine", false
        ));
        
        // Al Kharid Mine - close to Al Kharid bank
        locations.add(new LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine", false
        ));
        
        // Dwarven Mine - accessible via Falador
        locations.add(new LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine", false
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getCopperRockLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Al Kharid Mine - excellent for beginners, close to bank
        locations.add(new LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine", false
        ));
        
        // Lumbridge Swamp West Mine - good for F2P
        locations.add(new LocationOption(
                new WorldPoint(3149, 3148, 0), 
                "Lumbridge Swamp West Mine", false
        ));
        
        // Varrock South West Mine
        locations.add(new LocationOption(
                new WorldPoint(3181, 3377, 0), 
                "Varrock South West Mine", false
        ));
        
        // Dwarven Mine
        locations.add(new LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine", false
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getClayRockLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Varrock South West Mine - good clay rocks
        locations.add(new LocationOption(
                new WorldPoint(3181, 3377, 0), 
                "Varrock South West Mine", false
        ));
        
        // Dwarven Mine
        locations.add(new LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine", false
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getIronRockLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Mining Guild - premier location for iron with many rocks (8 iron rocks)
        Map<Skill, Integer> miningGuildSkills = new HashMap<>();
        miningGuildSkills.put(Skill.MINING, 60);
        locations.add(new LocationOption(
                new WorldPoint(3046, 9756, 0), 
                "Mining Guild (Members)",
                true,
                new HashMap<>(),
                miningGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Al Kharid Mine - excellent for F2P and low levels
        locations.add(new LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine", false
        ));
        
        // Varrock South East Mine - close to Varrock east bank
        locations.add(new LocationOption(
                new WorldPoint(3285, 3363, 0), 
                "Varrock South East Mine", false
        ));
        
        // Dwarven Mine - underground location
        locations.add(new LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine", false
        ));
        
        // Ardougne South East Mine
        locations.add(new LocationOption(
                new WorldPoint(2704, 3330, 0), 
                "Ardougne South East Mine", true
        ));
        
        // Bandit Camp Mine - excellent for higher levels with 16 iron rocks (Members only)
        locations.add(new LocationOption(
                new WorldPoint(3086, 3763, 0), 
                "Bandit Camp Mine (Members)",
                true
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getSilverRockLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Varrock South East Mine
        locations.add(new LocationOption(
                new WorldPoint(3285, 3363, 0), 
                "Varrock South East Mine",
                false
        ));
        
        // Dwarven Mine
        locations.add(new LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine",
                false
        ));
        
        // Al Kharid Mine
        locations.add(new LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine",false

        ));
        
        return locations;
    }
    
    private static List<LocationOption> getCoalRockLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Mining Guild - exceptional for coal with 57 coal rocks!
        Map<Skill, Integer> miningGuildSkills = new HashMap<>();
        miningGuildSkills.put(Skill.MINING, 60);
        locations.add(new LocationOption(
                new WorldPoint(3046, 9756, 0), 
                "Mining Guild (Members)",true,
                new HashMap<>(),
                miningGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
      
        // Dwarven Mine - accessible underground location
        locations.add(new LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine",false
        ));
        
        // Barbarian Village Mine
        locations.add(new LocationOption(
                new WorldPoint(3081, 3421, 0), 
                "Barbarian Village Mine",false
        ));
        
        // Seers' Village Coal Trucks - efficient for banking
        locations.add(new LocationOption(
                new WorldPoint(2569, 3462, 0), 
                "Seers' Village Coal Trucks",true
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getGoldRockLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Dwarven Mine
        locations.add(new LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine",false
        ));
        
        // Al Kharid Mine
        locations.add(new LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine",false
        ));
        
        // Crafting Guild - requires 40 Crafting
        Map<Skill, Integer> craftingGuildSkills = new HashMap<>();
        craftingGuildSkills.put(Skill.CRAFTING, 40);
        Map<Integer, Integer> craftingGuildItems = new HashMap<>();
        craftingGuildItems.put(ItemID.BROWN_APRON, 1);
        locations.add(new LocationOption(
                new WorldPoint(2938, 3283, 0), 
                "Crafting Guild",false,
                new HashMap<>(),
                craftingGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                craftingGuildItems
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getGemRockLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Shilo Village Gem Mine - requires Shilo Village quest
        Map<Quest, QuestState> shiloQuests = new HashMap<>();
        shiloQuests.put(Quest.SHILO_VILLAGE, QuestState.FINISHED);
        locations.add(new LocationOption(
                new WorldPoint(2824, 2997, 0), 
                "Shilo Village Gem Mine",true,
                shiloQuests,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Al Kharid Mine - has some gem rocks
        locations.add(new LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine",false
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getMithrilRockLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Mining Guild - excellent for mithril with 15 rocks
        Map<Skill, Integer> miningGuildSkills = new HashMap<>();
        miningGuildSkills.put(Skill.MINING, 60);
        locations.add(new LocationOption(
                new WorldPoint(3046, 9756, 0), 
                "Mining Guild (Members)",true,
                new HashMap<>(),
                miningGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Al Kharid Mine
        locations.add(new LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine",false
        ));
        
        // Dwarven Mine
        locations.add(new LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine",false
        ));
        
        // Lumbridge Swamp East Mine
        locations.add(new LocationOption(
                new WorldPoint(3229, 3148, 0), 
                "Lumbridge Swamp East Mine",false
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getAdamantiteRockLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Mining Guild - excellent for adamantite with 10 rocks
        Map<Skill, Integer> miningGuildSkills = new HashMap<>();
        miningGuildSkills.put(Skill.MINING, 60);
        locations.add(new LocationOption(
                new WorldPoint(3046, 9756, 0), 
                "Mining Guild (Members)",true,
                new HashMap<>(),
                miningGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Al Kharid Mine
        locations.add(new LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine",false
        ));
        
        // Lumbridge Swamp East Mine
        locations.add(new LocationOption(
                new WorldPoint(3229, 3148, 0), 
                "Lumbridge Swamp East Mine",false
        ));
        
        // Neitiznot Mine - requires The Fremennik Isles quest
        Map<Quest, QuestState> neitiznotQuests = new HashMap<>();
        neitiznotQuests.put(Quest.THE_FREMENNIK_ISLES, QuestState.FINISHED);
        locations.add(new LocationOption(
                new WorldPoint(2335, 3808, 0), 
                "Neitiznot Mine",true,
                neitiznotQuests,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getRuniteRockLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Mining Guild - best runite location with 2 rocks
        Map<Skill, Integer> miningGuildSkills = new HashMap<>();
        miningGuildSkills.put(Skill.MINING, 60);
        locations.add(new LocationOption(
                new WorldPoint(3046, 9756, 0), 
                "Mining Guild (Members)",true,
                new HashMap<>(),
                miningGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Heroes' Guild Mine - requires Heroes' Quest and Quest Points
        Map<Quest, QuestState> heroesQuests = new HashMap<>();
        heroesQuests.put(Quest.HEROES_QUEST, QuestState.FINISHED);
        locations.add(new LocationOption(
                new WorldPoint(2916, 3506, 0), 
                "Heroes' Guild Mine",true,
                heroesQuests,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Neitiznot Mine - requires The Fremennik Isles quest
        Map<Quest, QuestState> neitiznotQuests = new HashMap<>();
        neitiznotQuests.put(Quest.THE_FREMENNIK_ISLES, QuestState.FINISHED);
        locations.add(new LocationOption(
                new WorldPoint(2335, 3808, 0), 
                "Neitiznot Mine",true,
                neitiznotQuests,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Lava Maze Runite Mine - Wilderness, dangerous but accessible
        locations.add(new LocationOption(
                new WorldPoint(3058, 3884, 0), 
                "Lava Maze Runite Mine (Wilderness)",false
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getBasaltRockLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Weiss Basalt Mine - requires Making Friends with My Arm quest
        Map<Quest, QuestState> weissQuests = new HashMap<>();
        weissQuests.put(Quest.MAKING_FRIENDS_WITH_MY_ARM, QuestState.FINISHED);
        locations.add(new LocationOption(
                new WorldPoint(2857, 3937, 0), 
                "Weiss Basalt Mine",true,
                weissQuests,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
}
