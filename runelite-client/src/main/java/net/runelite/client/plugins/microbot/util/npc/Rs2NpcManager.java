package net.runelite.client.plugins.microbot.util.npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.slf4j.event.Level;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

/**
 * This class is responsible for managing NPCs in the game.
 * It provides utility methods for loading NPC data from JSON files and retrieving NPC stats.
 */

public class Rs2NpcManager {
    private static final Set<Integer> blacklistXpMultiplier = Set.of(8026, 8058, 8059, 8060, 8061, 7850, 7852, 7853, 7884, 7885, 7849, 7851, 7854, 7855, 7882, 7883, 7886, 7887, 7888, 7889, 494, 6640, 6656, 2042, 2043, 2044);
    public static Map<Integer, String> attackStyleMap;
    public static Map<Integer, String> attackAnimationMap;
    private static Map<Integer, Rs2NpcStats> statsMap;

    // NEW: A map keyed by NPC name, with a list of location objects
    private static Map<String, List<MonsterLocation>> locationMap;

    /**
     * Loads NPC data from JSON files.
     * This method should be called before using any other methods in this class.
     */
    public static void loadJson() throws Exception {
        if (statsMap != null) {
            return;
        }
        Type statsTypeToken = new TypeToken<Map<Integer, Rs2NpcStats>>() {}.getType();

        statsMap = loadNpcStatsFromJsonFile("/npc/monsters_complete.json");

        Type attackStyleTypeToken = new TypeToken<Map<Integer, String>>() {}.getType();
        attackStyleMap = loadJsonFile("/npc/npcs_attack_style.json", attackStyleTypeToken);

        Type attackAnimationTypeToken = new TypeToken<Map<Integer, String>>() {}.getType();
        attackAnimationMap = loadJsonFile("/npc/npcs_attack_animation.json", attackAnimationTypeToken);

        loadNpcLocationsByName();
    }

    /**
     * Loads a JSON file and deserializes it into a map.
     * @param filename The name of the JSON file to load.
     * @param typeToken The type token of the map.
     * @param <T> The type of the values in the map.
     * @return The deserialized map.
     */
    private static <T> Map<Integer, T> loadJsonFile(String filename, Type typeToken) {
        Gson gson = new Gson();
        try (InputStream inputStream = Rs2NpcStats.class.getResourceAsStream(filename)) {
            if (inputStream == null) {
                Microbot.log(Level.ERROR,"Failed to load " + filename);
                return Collections.emptyMap();
            }
            return gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), typeToken);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
    private static <T> T loadLocJsonFile(String filename, Type typeToken)
    {
        Gson gson = new Gson();
        try (InputStream inputStream = Rs2NpcStats.class.getResourceAsStream(filename))
        {
            if (inputStream == null)
            {
                Microbot.log(Level.ERROR,"Failed to load {}", filename);
                return null;
            }
            return gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), typeToken);
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    private static Map<Integer, Rs2NpcStats> loadNpcStatsFromJsonFile(String filename) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Rs2NpcStats.class, Rs2NpcStats.NPC_STATS_TYPE_ADAPTER)
                .create();

        try (InputStream inputStream = Rs2NpcStats.class.getResourceAsStream(filename)) {
            if (inputStream == null) {
                Microbot.log(Level.ERROR,"Failed to load " + filename);
                return Collections.emptyMap();
            }

            // Deserialize the JSON directly into a Map<Integer, Rs2NpcStats>
            Type typeToken = new TypeToken<Map<Integer, Rs2NpcStats>>() {
            }.getType();

            return gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), typeToken);

        } catch (IOException e) {
            throw new RuntimeException("Error reading JSON file: " + filename, e);
        }
    }

    /**
     * Helper method to load location data from a JSON file
     * where the keys are NPC names, not IDs.
     */
    private static void loadNpcLocationsByName()
    {
        String filename = "/npc/npcs_locations.json"; // Adjust if needed

        // The JSON structure is:
        // {
        //   "Goblin": [
        //     {
        //       "location_name": "Goblin Village",
        //       "mapID": 99,
        //       "coords": [[3200,3420,0], [3201,3421,0]]
        //     }
        //   ],
        //   "Cow": [ ... ],
        //   ...
        // }
        //
        // So we parse into Map<String, List<MonsterLocationDTO>>
        Type type = new TypeToken<Map<String, List<MonsterLocationDTO>>>() {}.getType();
        Map<String, List<MonsterLocationDTO>> rawMap = loadLocJsonFile(filename, type);

        if (rawMap == null)
        {
            Microbot.log(Level.INFO,"No location data found in {}", filename);
            locationMap = Collections.emptyMap();
            return;
        }

        // Convert from DTO to final MonsterLocation model
        locationMap = new HashMap<>();

        for (Map.Entry<String, List<MonsterLocationDTO>> entry : rawMap.entrySet())
        {
            String npcName = entry.getKey();
            List<MonsterLocationDTO> dtoList = entry.getValue();

            List<MonsterLocation> converted = dtoList.stream()
                    .map(Rs2NpcManager::dtoToMonsterLocation)
                    .collect(Collectors.toList());

            locationMap.put(npcName, converted);
        }

        Microbot.log("Loaded {} NPC names with location data from {}", locationMap.size(), filename);
    }

    /**
     * Converts a MonsterLocationDTO to our MonsterLocation model,
     * turning coords into WorldPoints.
     */
    private static MonsterLocation dtoToMonsterLocation(MonsterLocationDTO dto)
    {
        MonsterLocation loc = new MonsterLocation();
        loc.setLocationName(dto.getLocation_name());
        loc.setMapID(dto.getMapID());

        if (dto.getCoords() != null)
        {
            for (List<Integer> cord : dto.getCoords())
            {
                if (cord.size() == 3)
                {
                    int x = cord.get(0);
                    int y = cord.get(1);
                    int plane = cord.get(2);
                    loc.getCoords().add(new WorldPoint(x, y, plane));
                }
            }
        }
        return loc;
    }


    /**
     * Retrieves the stats of an NPC.
     * @param npcId The ID of the NPC.
     * @return The stats of the NPC, or null if the NPC does not exist.
     */
    @Nullable
    public static Rs2NpcStats getStats(int npcId) {
        return statsMap.get(npcId);
    }

    /**
     * Retrieves the health of an NPC.
     * @param npcId The ID of the NPC.
     * @return The health of the NPC, or -1 if the NPC does not exist or its health is unknown.
     */
    public static int getHealth(int npcId) {
        Rs2NpcStats s = statsMap.get(npcId);
        return s != null && s.getHitpoints() != -1 ? s.getHitpoints() : -1;
    }

    /**
     * Retrieves the attack speed of an NPC.
     * @param npcId The ID of the NPC.
     * @return The attack speed of the NPC, or -1 if the NPC does not exist or its attack speed is unknown.
     */
    public static int getAttackSpeed(int npcId) {
        Rs2NpcStats s = statsMap.get(npcId);
        Microbot.log(s.toString());
        return s.getAttackSpeed() != -1 ? s.getAttackSpeed() : -1;
    }

    /**
     * Retrieves the XP modifier of an NPC.
     * @param npcId The ID of the NPC.
     * @return The XP modifier of the NPC, or 1.0 if the NPC does not exist or its XP modifier is unknown.
     */
    public static double getXpModifier(int npcId) {
        if (blacklistXpMultiplier.contains(npcId)) {
            return 1.0;
        } else {
            Rs2NpcStats s = statsMap.get(npcId);
            return s == null ? 1.0 : s.calculateXpModifier();
        }
    }

    /**
     * Retrieves the attack style of an NPC.
     * @param npcId The ID of the NPC.
     * @return The attack style of the NPC, or null if the NPC does not exist or its attack style is unknown.
     */
    public static String getAttackStyle(int npcId) {
        return attackStyleMap.get(npcId);
    }

    // Get all slayer monsters
    public static List<Integer> getSlayerMonsters()
    {
        return statsMap.entrySet().stream()
                .filter(e -> e.getValue().isSlayerMonster())
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * Get slayer monsters by category (category is the same as the slayer task name, e.g., Monster: Fire Giant, Category/TaskName: Fire Giants).
     * This will get all monster variations for the task including superior variant.
     */
    public static List<String> getSlayerMonstersByCategory(String category)
    {
        return statsMap.values().stream()
                .filter(rs2NpcStats -> rs2NpcStats.getCategory() != null &&
                        rs2NpcStats.getCategory().stream().anyMatch(c -> c.equalsIgnoreCase(category)))
                .map(Rs2NpcStats::getName).distinct()
                .collect(Collectors.toList());
    }


    // ---------------------------------------------------------
    //         NEW: Retrieve location data by NPC name
    // ---------------------------------------------------------
    /**
     * Gets the list of locations for an NPC by its name as defined in the
     * location JSON file. Returns an empty list if no data is found.
     */
    public static List<MonsterLocation> getNpcLocations(String npcName)
    {
        if (locationMap == null)
        {
            return Collections.emptyList();
        }
        return locationMap.getOrDefault(npcName, Collections.emptyList());
    }

    /**
     * Gets the closest location for an NPC by its name, with an additional
     * filter for minimum clustering of NPCs to avoid stragglers and a filter to avoid the Wilderness.
     *
     * @param npcName The name of the NPC.
     * @param minClustering The minimum number of NPCs required to consider a location.
     * @param avoidWilderness Whether to avoid locations in the Wilderness.
     */
    public static MonsterLocation getClosestLocation(String npcName, int minClustering, boolean avoidWilderness) {
        ShortestPathPlugin.getPathfinderConfig().setUseBankItems(true);
        Microbot.log(Level.INFO,"Finding closest location for: " + npcName);

        List<MonsterLocation> allLocations = getNpcLocations(npcName);
        if (allLocations.isEmpty()) {
            Microbot.log(Level.INFO,"No locations found for " + npcName);
            return null;
        }

        List<String> allNames = allLocations.stream()
                .map(MonsterLocation::getLocationName)
                .collect(Collectors.toList());
        Microbot.log(Level.INFO,"Found " + allLocations.size() + " locations for " + npcName + ": " + String.join(", ", allNames));

        List<MonsterLocation> validLocations = allLocations.stream()
                .filter(loc -> loc.getCoords().size() > minClustering)
                .filter(loc -> !avoidWilderness || !loc.getLocationName().contains("Wilderness"))
                .collect(Collectors.toList());
        if (validLocations.isEmpty()) {
            Microbot.log(Level.INFO,"No valid locations after filtering for " + npcName);
            return null;
        }

        List<WorldPoint> centers = validLocations.stream()
                .map(MonsterLocation::getBestClusterCenter)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
        if (centers.isEmpty()) {
            Microbot.log(Level.INFO,"Could not compute any centers for " + npcName);
            return null;
        }

        // 6) Find nearest and return
        int idx = Rs2Walker.findNearestAccessibleTarget(centers, true);
        MonsterLocation closest = validLocations.get(idx);
        if (closest.getCoords().isEmpty()) {
            Microbot.log(Level.INFO,"Closest location had no coords for " + npcName);
            return null;
        }

        Microbot.log(Level.INFO,"Closest location for " + npcName + ": " + closest.getLocationName());
        return closest;
    }


    public static MonsterLocation getClosestLocation(String npcName, int minClustering)
    {
        return getClosestLocation(npcName, minClustering, false);
    }

    public static MonsterLocation getClosestLocation(String npcName)
    {
        return getClosestLocation(npcName, 1, false);
    }

}