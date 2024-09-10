package net.runelite.client.plugins.microbot.util.npc;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;

import javax.annotation.Nullable;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * This class is responsible for managing NPCs in the game.
 * It provides utility methods for loading NPC data from JSON files and retrieving NPC stats.
 */
@Slf4j
public class Rs2NpcManager {
    private static final Set<Integer> blacklistXpMultiplier = Set.of(8026, 8058, 8059, 8060, 8061, 7850, 7852, 7853, 7884, 7885, 7849, 7851, 7854, 7855, 7882, 7883, 7886, 7887, 7888, 7889, 494, 6640, 6656, 2042, 2043, 2044);
    public static Map<Integer, String> attackStyleMap;
    public static Map<Integer, String> attackAnimationMap;
    private static Map<Integer, Rs2NpcStats> statsMap;

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
                System.out.println("Failed to load " + filename);
                return Collections.emptyMap();
            }
            return gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), typeToken);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<Integer, Rs2NpcStats> loadNpcStatsFromJsonFile(String filename) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapter(Rs2NpcStats.class, Rs2NpcStats.NPC_STATS_TYPE_ADAPTER)
                .create();

        try (InputStream inputStream = Rs2NpcStats.class.getResourceAsStream(filename)) {
            if (inputStream == null) {
                System.out.println("Failed to load " + filename);
                return Collections.emptyMap();
            }

            // Deserialize the JSON directly into a Map<Integer, Rs2NpcStats>
            Type typeToken = new TypeToken<Map<Integer, Rs2NpcStats>>() {
            }.getType();
            Map<Integer, Rs2NpcStats> statsMap = gson.fromJson(new InputStreamReader(inputStream, StandardCharsets.UTF_8), typeToken);

            return statsMap;

        } catch (IOException e) {
            throw new RuntimeException("Error reading JSON file: " + filename, e);
        }
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
        log.info(s.toString());
        return s != null && s.getAttackSpeed() != -1 ? s.getAttackSpeed() : -1;
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
}