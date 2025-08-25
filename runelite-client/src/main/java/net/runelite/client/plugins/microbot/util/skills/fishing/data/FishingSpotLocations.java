package net.runelite.client.plugins.microbot.util.skills.fishing.data;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationOption;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.skills.fishing.enums.FishType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Contains location data for different fishing spot types.
 * This class provides optimized fishing locations for each fish type with their requirements.
 * Data sourced from OSRS Wiki: https://oldschool.runescape.wiki/w/Fishing
 */
@Getter
@RequiredArgsConstructor
public class FishingSpotLocations {
    
    /**
     * Gets the best locations for a specific fish type.
     * Locations are ordered by preference (best locations first).
     */
    public static List<LocationOption> getLocationsForFish(FishType fishType) {
        switch (fishType) {
            case SHRIMP:
                return getShrimpFishingLocations();
            case SARDINE:
                return getSardineFishingLocations();
            case MACKEREL:
                return getMackerelFishingLocations();
            case TROUT:
                return getTroutFishingLocations();
            case PIKE:
                return getPikeFishingLocations();
            case TUNA:
                return getTunaFishingLocations();
            case LOBSTER:
                return getLobsterFishingLocations();
            case MONKFISH:
                return getMonkfishFishingLocations();
            case KARAMBWANJI:
                return getKarambwanjiFishingLocations();
            case SHARK:
                return getSharkFishingLocations();
            case ANGLERFISH:
                return getAnglerfishFishingLocations();
            case KARAMBWAN:
                return getKarambwanFishingLocations();
            case BARBARIAN:
                return getBarbarianFishingLocations();
            case CAVE_EEL:
                return getCaveEelFishingLocations();
            case LAVA_EEL:
                return getLavaEelFishingLocations();
            case INFERNAL_EEL:
                return getInfernalEelFishingLocations();
            case SACRED_EEL:
                return getSacredEelFishingLocations();
            case DARK_CRAB:
                return getDarkCrabFishingLocations();
            case MINNOWS:
                return getMinnowsFishingLocations();
            case BLUEGILL:
                return getBluegillFishingLocations();
            case COMMON_TENCH:
                return getCommonTenchFishingLocations();
            default:
                return new ArrayList<>();
        }
    }
    
    /**
     * Gets accessible locations for a specific fish type - filters out locations 
     * the player cannot access based on quest and skill requirements.
     * Uses streams for efficient filtering.
     */
    public static List<LocationOption> getAccessibleLocationsForFish(FishType fishType) {
        return getLocationsForFish(fishType).stream()
                .filter(LocationOption::hasRequirements)
                .collect(Collectors.toList());
    }
    
    /**
     * Gets the best accessible location for a fish type based on player position.
     * Returns null if no accessible locations are found.
     */
    public static LocationOption getBestAccessibleLocation(FishType fishType) {
        List<LocationOption> accessibleLocations = getAccessibleLocationsForFish(fishType);
        
        if (accessibleLocations.isEmpty()) {
            return null;
        }
        
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation != null) {
            // Return the closest accessible location
            return accessibleLocations.stream()
                    .min((loc1, loc2) -> Integer.compare(
                            playerLocation.distanceTo(loc1.getWorldPoint()),
                            playerLocation.distanceTo(loc2.getWorldPoint())
                    ))
                    .orElse(accessibleLocations.get(0));
        }
        
        return accessibleLocations.get(0);
    }
    
    private static List<LocationOption> getShrimpFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Draynor Village - excellent for beginners, close to bank
        locations.add(new LocationOption(
                new WorldPoint(3084, 3228, 0), 
                "Draynor Village",
                false // Not members only
        ));
        
        // Al Kharid - good for F2P, multiple spots
        locations.add(new LocationOption(
                new WorldPoint(3274, 3140, 0), 
                "Al Kharid",
                false // Not members only

        ));
        
        // Lumbridge Swamp - beginner friendly
        locations.add(new LocationOption(
                new WorldPoint(3244, 3153, 0), 
                "Lumbridge Swamp",
                false // Not members only
        ));
        
        // Mudskipper Point - popular F2P location
        locations.add(new LocationOption(
                new WorldPoint(2995, 3158, 0), 
                "Mudskipper Point",
                false // Not members only
        ));
        
        // Karamja - Musa Point
        locations.add(new LocationOption(
                new WorldPoint(2925, 3179, 0), 
                "Musa Point (Karamja)",
                false // Not members only
        ));        
        locations.add(new LocationOption(
                new WorldPoint(2836, 3431, 0), 
                "Catherby (Members)",
                true,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getSardineFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Same locations as shrimp but with bait fishing
        // Draynor Village
        locations.add(new LocationOption(
                new WorldPoint(3084, 3228, 0), 
                "Draynor Village",
                false // Not members only

        ));
        
        // Al Kharid
        locations.add(new LocationOption(
                new WorldPoint(3274, 3140, 0), 
                "Al Kharid",
                false // Not members only
        ));
        
        // Fishing Platform - requires quest
        Map<Quest, QuestState> fishingPlatformQuests = new HashMap<>();
        fishingPlatformQuests.put(Quest.SEA_SLUG, QuestState.FINISHED);
        locations.add(new LocationOption(
                new WorldPoint(2788, 3273, 0), 
                "Fishing Platform",
                true,
                fishingPlatformQuests,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getMackerelFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Catherby - excellent location with bank access
        
        locations.add(new LocationOption(
                new WorldPoint(2836, 3431, 0), 
                "Catherby (Members)",
                true,                
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Fishing Guild - requires 68 Fishing
        Map<Skill, Integer> fishingGuildSkills = new HashMap<>();
        fishingGuildSkills.put(Skill.FISHING, 68);
        locations.add(new LocationOption(
                new WorldPoint(2604, 3423, 0), 
                "Fishing Guild",
                true,
                new HashMap<>(),
                fishingGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getTroutFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Lumbridge River - great for beginners
        locations.add(new LocationOption(
                new WorldPoint(3238, 3241, 0), 
                "Lumbridge River",
                false
        ));
        
        // Barbarian Village - popular location
        locations.add(new LocationOption(
                new WorldPoint(3103, 3424, 0), 
                "Barbarian Village",
                false
        ));
        
        // Seers' Village - Members location
        
        locations.add(new LocationOption(
                new WorldPoint(2725, 3524, 0), 
                "Seers' Village (Members)",
                true,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Tree Gnome Stronghold - Members
        locations.add(new LocationOption(
                new WorldPoint(2389, 3422, 0), 
                "Tree Gnome Stronghold (Members)",
                true,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getPikeFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Same locations as trout but for pike fishing (bait)
        locations.add(new LocationOption(
                new WorldPoint(3238, 3241, 0), 
                "Lumbridge River",
                false
        ));
        
        locations.add(new LocationOption(
                new WorldPoint(3103, 3424, 0), 
                "Barbarian Village",
                false
        ));
        
        
        locations.add(new LocationOption(
                new WorldPoint(2725, 3524, 0), 
                "Seers' Village (Members)",
                true,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getTunaFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Karamja - F2P accessible
        locations.add(new LocationOption(
                new WorldPoint(2925, 3179, 0), 
                "Musa Point (Karamja)",
                false
        ));
        
        // Catherby - Members with excellent bank access
        
        locations.add(new LocationOption(
                new WorldPoint(2836, 3431, 0), 
                "Catherby (Members)",
                true,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Fishing Guild - requires 68 Fishing
        Map<Skill, Integer> fishingGuildSkills = new HashMap<>();
        fishingGuildSkills.put(Skill.FISHING, 68);
        locations.add(new LocationOption(
                new WorldPoint(2604, 3423, 0), 
                "Fishing Guild",
                true,                
                new HashMap<>(),
                fishingGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getLobsterFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Karamja - F2P accessible
        locations.add(new LocationOption(
                new WorldPoint(2925, 3179, 0), 
                "Musa Point (Karamja)",
                false
        ));
        
        // Catherby - Members with excellent bank access
        
        locations.add(new LocationOption(
                new WorldPoint(2836, 3431, 0), 
                "Catherby (Members)",
                true,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Fishing Guild - requires 68 Fishing
        Map<Skill, Integer> fishingGuildSkills = new HashMap<>();
        fishingGuildSkills.put(Skill.FISHING, 68);
        locations.add(new LocationOption(
                new WorldPoint(2604, 3423, 0), 
                "Fishing Guild",
                true,
                new HashMap<>(),
                fishingGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Rellekka - Members
        locations.add(new LocationOption(
                new WorldPoint(2641, 3696, 0), 
                "Rellekka (Members)",true,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getMonkfishFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Piscatoris - requires Swan Song quest
        Map<Quest, QuestState> swanSongQuests = new HashMap<>();
        swanSongQuests.put(Quest.SWAN_SONG, QuestState.FINISHED);
        Map<Skill, Integer> monkfishSkills = new HashMap<>();
        monkfishSkills.put(Skill.FISHING, 62);
        
        
        locations.add(new LocationOption(
                new WorldPoint(2308, 3700, 0), 
                "Piscatoris Fishing Colony",
                true,
                swanSongQuests,
                monkfishSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getKarambwanjiFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Fairy Ring CKR - requires Tai Bwo Wannai Trio quest
        Map<Quest, QuestState> karambwanjiQuests = new HashMap<>();
        karambwanjiQuests.put(Quest.TAI_BWO_WANNAI_TRIO, QuestState.FINISHED);
        
        
        locations.add(new LocationOption(
                new WorldPoint(2806, 3014, 0), 
                "Karamja (Fairy Ring CKR)",true,
                karambwanjiQuests,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getSharkFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        
        Map<Skill, Integer> sharkSkills = new HashMap<>();
        sharkSkills.put(Skill.FISHING, 76);
        
        // Catherby - excellent location with bank access
        locations.add(new LocationOption(
                new WorldPoint(2836, 3431, 0), 
                "Catherby (Members)",true,
                new HashMap<>(),
                sharkSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Fishing Guild - requires 68 Fishing
        Map<Skill, Integer> fishingGuildSkills = new HashMap<>();
        fishingGuildSkills.put(Skill.FISHING, 68);
        locations.add(new LocationOption(
                new WorldPoint(2604, 3423, 0), 
                "Fishing Guild",true,
                new HashMap<>(),
                fishingGuildSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Burgh de Rott - after In Aid of the Myreque
        Map<Quest, QuestState> myrequeQuests = new HashMap<>();
        myrequeQuests.put(Quest.IN_AID_OF_THE_MYREQUE, QuestState.FINISHED);
        locations.add(new LocationOption(
                new WorldPoint(3472, 3192, 0), 
                "Burgh de Rott",true,
                myrequeQuests,
                sharkSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getAnglerfishFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Port Piscarilius - requires Fishing Contest and 82 Fishing
        Map<Quest, QuestState> anglerfishQuests = new HashMap<>();
        anglerfishQuests.put(Quest.FISHING_CONTEST, QuestState.FINISHED);
        Map<Skill, Integer> anglerfishSkills = new HashMap<>();
        anglerfishSkills.put(Skill.FISHING, 82);
        
        
        locations.add(new LocationOption(
                new WorldPoint(1831, 3773, 0), 
                "Port Piscarilius", true,
                anglerfishQuests,
                anglerfishSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getKarambwanFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Fairy Ring DKP - requires Tai Bwo Wannai Trio quest
        Map<Quest, QuestState> karambwanQuests = new HashMap<>();
        karambwanQuests.put(Quest.TAI_BWO_WANNAI_TRIO, QuestState.FINISHED);
        Map<Skill, Integer> karambwanSkills = new HashMap<>();
        karambwanSkills.put(Skill.FISHING, 65);
        
        
        locations.add(new LocationOption(
                new WorldPoint(2898, 3119, 0), 
                "Karamja (Fairy Ring DKP)",true,
                karambwanQuests,
                karambwanSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getBarbarianFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        // Otto's Grotto - requires Barbarian Training
        Map<Skill, Integer> barbarianSkills = new HashMap<>();
        barbarianSkills.put(Skill.FISHING, 48);
        barbarianSkills.put(Skill.STRENGTH, 15);
        barbarianSkills.put(Skill.AGILITY, 15);
        Map<Quest, QuestState > barbarianQuest = new HashMap<>();
        barbarianQuest.put(Quest.BARBARIAN_TRAINING,QuestState.FINISHED); //must be check.. not sure about the value here..
        
        locations.add(new LocationOption(
                new WorldPoint(2500, 3509, 0), 
                "Otto's Grotto (Barbarian Training)",true,
                barbarianQuest,
                barbarianSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getCaveEelFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        Map<Skill, Integer> caveEelSkills = new HashMap<>();
        caveEelSkills.put(Skill.FISHING, 38);
        
        // Lumbridge Swamp Cave
        locations.add(new LocationOption(
                new WorldPoint(3244, 9570, 0), 
                "Lumbridge Swamp Cave East",true,
                new HashMap<>(),
                caveEelSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        locations.add(new LocationOption(
                new WorldPoint(3153, 9544, 0), 
                "Lumbridge Swamp Cave West",true,
                new HashMap<>(),
                caveEelSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>() //think we also need items ?
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getLavaEelFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        Map<Skill, Integer> lavaEelSkills = new HashMap<>();
        lavaEelSkills.put(Skill.FISHING, 53);
        
        
        // Taverley Dungeon
        locations.add(new LocationOption(
                new WorldPoint(2893, 9764, 0), 
                "Taverley Dungeon",true,
                new HashMap<>(),
                lavaEelSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Wilderness Lava Maze (dangerous)
        locations.add(new LocationOption(
                new WorldPoint(3071, 3840, 0), 
                "Wilderness Lava Maze (Dangerous!)",true,
                new HashMap<>(),
                lavaEelSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getInfernalEelFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        Map<Skill, Integer> infernalEelSkills = new HashMap<>();
        infernalEelSkills.put(Skill.FISHING, 80);
        
        
        // Mor Ul Rek (TzHaar city)
        locations.add(new LocationOption(
                new WorldPoint(2443, 5104, 0), 
                "Mor Ul Rek (TzHaar City)",true,
                new HashMap<>(),
                infernalEelSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getSacredEelFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        Map<Quest, QuestState> regicideQuests = new HashMap<>();
        regicideQuests.put(Quest.REGICIDE, QuestState.FINISHED);
        Map<Skill, Integer> sacredEelSkills = new HashMap<>();
        sacredEelSkills.put(Skill.FISHING, 87);
        
        
        // Zul-Andra
        locations.add(new LocationOption(
                new WorldPoint(2183, 3068, 0), 
                "Zul-Andra",true,
                regicideQuests,
                sacredEelSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getDarkCrabFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        Map<Skill, Integer> darkCrabSkills = new HashMap<>();
        darkCrabSkills.put(Skill.FISHING, 85);
        
        
        // Wilderness Dark Crabs (very dangerous)
        locations.add(new LocationOption(
                new WorldPoint(3362, 3802, 0), 
                "Wilderness Dark Crabs (Very Dangerous!)",true,
                new HashMap<>(),
                darkCrabSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        // Wilderness Resource Area (dangerous but safer)
        locations.add(new LocationOption(
                new WorldPoint(3186, 3925, 0), 
                "Wilderness Resource Area (Dangerous!)",true,
                new HashMap<>(),
                darkCrabSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getMinnowsFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        Map<Skill, Integer> minnowsSkills = new HashMap<>();
        minnowsSkills.put(Skill.FISHING, 82);
        
        
        // Fishing Guild Minnow Platform
        locations.add(new LocationOption(
                new WorldPoint(2609, 3444, 0), 
                "Fishing Guild Minnow Platform", true,
                new HashMap<>(),
                minnowsSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getBluegillFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        Map<Skill, Integer> bluegillSkills = new HashMap<>();
        bluegillSkills.put(Skill.FISHING, 35);
        
        
        // Molch Island - requires completion of The Depths of Despair
        Map<Quest, QuestState> molchQuests = new HashMap<>();
        molchQuests.put(Quest.THE_DEPTHS_OF_DESPAIR, QuestState.FINISHED);
        
        locations.add(new LocationOption(
                new WorldPoint(1370, 3632, 0), 
                "Molch Island",true,
                molchQuests,
                bluegillSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
    
    private static List<LocationOption> getCommonTenchFishingLocations() {
        List<LocationOption> locations = new ArrayList<>();
        
        Map<Skill, Integer> tenchSkills = new HashMap<>();
        tenchSkills.put(Skill.FISHING, 56);
        
        
        // Molch Island - requires completion of The Depths of Despair
        Map<Quest, QuestState> molchQuests = new HashMap<>();
        molchQuests.put(Quest.THE_DEPTHS_OF_DESPAIR, QuestState.FINISHED);
        
        locations.add(new LocationOption(
                new WorldPoint(1370, 3632, 0), 
                "Molch Island",true,
                molchQuests,
                tenchSkills,
                new HashMap<>(),
                new HashMap<>(),
                new HashMap<>()
        ));
        
        return locations;
    }
}
