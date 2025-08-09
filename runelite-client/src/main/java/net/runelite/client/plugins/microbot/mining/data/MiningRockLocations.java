package net.runelite.client.plugins.microbot.mining.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.mining.enums.Rocks;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

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
    public static List<LocationRequirement.LocationOption> getLocationsForRock(Rocks rock) {
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
    public static List<LocationRequirement.LocationOption> getAccessibleLocationsForRock(Rocks rock) {
        return getLocationsForRock(rock).stream()
                .filter(LocationRequirement.LocationOption::hasRequirements)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets the best accessible location for a rock type based on player position.
     * Returns null if no accessible locations are found.
     */
    public static LocationRequirement.LocationOption getBestAccessibleLocation(Rocks rock) {
        List<LocationRequirement.LocationOption> accessibleLocations = getAccessibleLocationsForRock(rock);
        
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
    
    private static List<LocationRequirement.LocationOption> getTinRockLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Lumbridge Swamp West Mine - great for beginners, close to bank
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3149, 3148, 0), 
                "Lumbridge Swamp West Mine"
        ));
        
        // Varrock South West Mine - close to Varrock west bank
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3181, 3377, 0), 
                "Varrock South West Mine"
        ));
        
        // Al Kharid Mine - close to Al Kharid bank
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine"
        ));
        
        // Dwarven Mine - accessible via Falador
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine"
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getCopperRockLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Al Kharid Mine - excellent for beginners, close to bank
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine"
        ));
        
        // Lumbridge Swamp West Mine - good for F2P
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3149, 3148, 0), 
                "Lumbridge Swamp West Mine"
        ));
        
        // Varrock South West Mine
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3181, 3377, 0), 
                "Varrock South West Mine"
        ));
        
        // Dwarven Mine
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine"
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getClayRockLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Varrock South West Mine - good clay rocks
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3181, 3377, 0), 
                "Varrock South West Mine"
        ));
        
        // Dwarven Mine
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine"
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getIronRockLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Mining Guild - premier location for iron with many rocks (8 iron rocks)
        Map<Skill, Integer> miningGuildSkills = new HashMap<>();
        miningGuildSkills.put(Skill.MINING, 60);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3046, 9756, 0), 
                "Mining Guild (Members)",
                new HashMap<>(),
                miningGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Al Kharid Mine - excellent for F2P and low levels
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine"
        ));
        
        // Varrock South East Mine - close to Varrock east bank
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3285, 3363, 0), 
                "Varrock South East Mine"
        ));
        
        // Dwarven Mine - underground location
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine"
        ));
        
        // Ardougne South East Mine
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2704, 3330, 0), 
                "Ardougne South East Mine"
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getSilverRockLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Varrock South East Mine
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3285, 3363, 0), 
                "Varrock South East Mine"
        ));
        
        // Dwarven Mine
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine"
        ));
        
        // Al Kharid Mine
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine"
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getCoalRockLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Mining Guild - exceptional for coal with 57 coal rocks!
        Map<Skill, Integer> miningGuildSkills = new HashMap<>();
        miningGuildSkills.put(Skill.MINING, 60);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3046, 9756, 0), 
                "Mining Guild (Members)",
                new HashMap<>(),
                miningGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Miscellania Mine - very good for coal
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2530, 3888, 0), 
                "Miscellania Mine"
        ));
        
        // Dwarven Mine - accessible underground location
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine"
        ));
        
        // Barbarian Village Mine
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3081, 3421, 0), 
                "Barbarian Village Mine"
        ));
        
        // Seers' Village Coal Trucks - efficient for banking
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2569, 3462, 0), 
                "Seers' Village Coal Trucks"
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getGoldRockLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Dwarven Mine
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine"
        ));
        
        // Al Kharid Mine
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine"
        ));
        
        // Crafting Guild - requires 40 Crafting
        Map<Skill, Integer> craftingGuildSkills = new HashMap<>();
        craftingGuildSkills.put(Skill.CRAFTING, 40);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2938, 3283, 0), 
                "Crafting Guild",
                new HashMap<>(),
                craftingGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getGemRockLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Shilo Village Gem Mine - requires Shilo Village quest
        Map<Quest, QuestState> shiloQuests = new HashMap<>();
        shiloQuests.put(Quest.SHILO_VILLAGE, QuestState.FINISHED);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2824, 2997, 0), 
                "Shilo Village Gem Mine",
                shiloQuests,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Al Kharid Mine - has some gem rocks
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine"
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getMithrilRockLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Mining Guild - excellent for mithril with 15 rocks
        Map<Skill, Integer> miningGuildSkills = new HashMap<>();
        miningGuildSkills.put(Skill.MINING, 60);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3046, 9756, 0), 
                "Mining Guild (Members)",
                new HashMap<>(),
                miningGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Al Kharid Mine
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine"
        ));
        
        // Dwarven Mine
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3034, 9822, 0), 
                "Dwarven Mine"
        ));
        
        // Lumbridge Swamp East Mine
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3229, 3148, 0), 
                "Lumbridge Swamp East Mine"
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getAdamantiteRockLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Mining Guild - excellent for adamantite with 10 rocks
        Map<Skill, Integer> miningGuildSkills = new HashMap<>();
        miningGuildSkills.put(Skill.MINING, 60);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3046, 9756, 0), 
                "Mining Guild (Members)",
                new HashMap<>(),
                miningGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Al Kharid Mine
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3296, 3315, 0), 
                "Al Kharid Mine"
        ));
        
        // Lumbridge Swamp East Mine
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3229, 3148, 0), 
                "Lumbridge Swamp East Mine"
        ));
        
        // Neitiznot Mine - requires The Fremennik Isles quest
        Map<Quest, QuestState> neitiznotQuests = new HashMap<>();
        neitiznotQuests.put(Quest.THE_FREMENNIK_ISLES, QuestState.FINISHED);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2335, 3808, 0), 
                "Neitiznot Mine",
                neitiznotQuests,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getRuniteRockLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Mining Guild - best runite location with 2 rocks
        Map<Skill, Integer> miningGuildSkills = new HashMap<>();
        miningGuildSkills.put(Skill.MINING, 60);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3046, 9756, 0), 
                "Mining Guild (Members)",
                new HashMap<>(),
                miningGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Heroes' Guild Mine - requires Heroes' Quest and Quest Points
        Map<Quest, QuestState> heroesQuests = new HashMap<>();
        heroesQuests.put(Quest.HEROES_QUEST, QuestState.FINISHED);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2916, 3506, 0), 
                "Heroes' Guild Mine",
                heroesQuests,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Neitiznot Mine - requires The Fremennik Isles quest
        Map<Quest, QuestState> neitiznotQuests = new HashMap<>();
        neitiznotQuests.put(Quest.THE_FREMENNIK_ISLES, QuestState.FINISHED);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2335, 3808, 0), 
                "Neitiznot Mine",
                neitiznotQuests,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Lava Maze Runite Mine - Wilderness, dangerous but accessible
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(3058, 3884, 0), 
                "Lava Maze Runite Mine (Wilderness)"
        ));
        
        return locations;
    }
    
    private static List<LocationRequirement.LocationOption> getBasaltRockLocations() {
        List<LocationRequirement.LocationOption> locations = new ArrayList<>();
        
        // Weiss Basalt Mine - requires Making Friends with My Arm quest
        Map<Quest, QuestState> weissQuests = new HashMap<>();
        weissQuests.put(Quest.MAKING_FRIENDS_WITH_MY_ARM, QuestState.FINISHED);
        locations.add(new LocationRequirement.LocationOption(
                new WorldPoint(2857, 3937, 0), 
                "Weiss Basalt Mine",
                weissQuests,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
}
