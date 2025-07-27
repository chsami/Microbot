package net.runelite.client.plugins.microbot.util.cache.serialization;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.Rs2Cache;
import net.runelite.client.plugins.microbot.util.cache.model.SkillData;
import net.runelite.client.plugins.microbot.util.cache.model.SpiritTreeData;
import net.runelite.client.plugins.microbot.util.cache.model.VarbitData;
import net.runelite.client.plugins.microbot.util.farming.SpiritTree;

import java.lang.reflect.Type;
import java.util.Map;

/**
 * Serialization manager for Rs2UnifiedCache instances.
 * Handles automatic save/load to RuneLite profile configuration
 * similar to Rs2Bank serialization system.
 */
@Slf4j
public class CacheSerializationManager {
    
    private static final String CONFIG_GROUP = "microbot";
    private static final Gson gson;
    
    // Initialize Gson with custom adapters
    static {
        gson = new GsonBuilder()
                .registerTypeAdapter(Skill.class, new SkillAdapter())
                .registerTypeAdapter(Quest.class, new QuestAdapter())
                .registerTypeAdapter(QuestState.class, new QuestStateAdapter())
                .registerTypeAdapter(SkillData.class, new SkillDataAdapter())
                .registerTypeAdapter(VarbitData.class, new VarbitDataAdapter())
                .registerTypeAdapter(SpiritTree.class, new SpiritTreePatchAdapter())
                .registerTypeAdapter(SpiritTreeData.class, new SpiritTreeDataAdapter())
                .create();
    }
    
    /**
     * Saves a cache to RuneLite profile configuration.
     * 
     * @param cache The cache to save
     * @param configKey The config key to save under
     * @param <K> The key type
     * @param <V> The value type
     */
    public static <K, V> void saveCache(Rs2Cache<K, V> cache, String configKey, String rsProfileKey) {
        try {            
            if (rsProfileKey == null || Microbot.getConfigManager() == null) {
                log.warn("Cannot save cache {}: profile key or config manager not available", configKey);
                return;
            }
            
            // For now, we'll serialize the cache data as a simplified format
            // Each cache type will need its own serialization strategy
            String json = serializeCacheData(cache, configKey);
            
            if (json != null && !json.isEmpty()) {
                log.debug(configKey + " JSON length: " + json.length());                
                Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, rsProfileKey, configKey, json);             
            } else {
                log.warn("No data to save for cache {}", configKey);
            }
            
        } catch (Exception e) {
            log.error("Failed to save cache {} to config", configKey, e);
        }
    }
    
    /**
     * Loads a cache from RuneLite profile configuration.
     * 
     * @param cache The cache to load into
     * @param configKey The config key to load from
     * @param <K> The key type
     * @param <V> The value type
     */
    public static <K, V> void loadCache(Rs2Cache<K, V> cache, String configKey, String rsProfileKey) {
        try {
            if (Microbot.getConfigManager() == null) {
                log.warn("Cannot load cache {}: config manager not available", configKey);
                return;
            }
            // No need to check profile key for loading - getRSProfileConfiguration handles it internally
            String json = Microbot.getConfigManager().getConfiguration(CONFIG_GROUP, rsProfileKey,configKey);
            if (json != null && !json.isEmpty()) {
                deserializeCacheData(cache, configKey, json);
                log.debug("Loaded cache {} from profile config, entries loaded: {}", configKey, cache.size());
            } else {
                log.warn("No cached data found for {}", configKey);
            }
            
        } catch (JsonSyntaxException e) {
            log.warn("Failed to parse cached data for {}, clearing corrupted cache and starting fresh", configKey, e);
            // Clear the corrupted cache data
            clearCache(configKey, rsProfileKey);
        } catch (Exception e) {
            log.error("Failed to load cache {} from config", configKey, e);
        }
    }
    
    /**
     * Clears cache data from profile configuration.
     * 
     * @param configKey The config key to clear
     */
    public static void clearCache(String configKey) {
        try {
            String rsProfileKey = Microbot.getConfigManager().getRSProfileKey();
            clearCache(configKey, rsProfileKey);
        } catch (Exception e) {
            log.error("Failed to clear cache {} from config", configKey, e);
        }
    }
    
    /**
     * Clears cache data from profile configuration for a specific profile.
     * 
     * @param configKey The config key to clear
     * @param rsProfileKey The profile key to clear from
     */
    public static void clearCache(String configKey, String rsProfileKey) {
        try {
            if (rsProfileKey != null && Microbot.getConfigManager() != null) {
                Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, rsProfileKey, configKey, null);
                log.debug("Cleared cache {} from profile config for profile: {}", configKey, rsProfileKey);
            }
        } catch (Exception e) {
            log.error("Failed to clear cache {} from config for profile: {}", configKey, rsProfileKey, e);
        }
    }
    
    /**
     * Serializes cache data to JSON based on cache type.
     * This method handles different cache types with specific serialization strategies.
     * Only persistent caches are serialized (Skills, Quests, Varbits).
     * NPC cache is excluded as it's dynamically loaded based on game scene.
     */
    @SuppressWarnings("unchecked")
    private static <K, V> String serializeCacheData(Rs2Cache<K, V> cache, String configKey) {
        try {
            log.debug("Starting serialization for cache type: {}", configKey);
            
            // Handle different cache types - using actual config keys from caches
            switch (configKey) {
                case "skills":
                    String skillJson = serializeSkillCache((Rs2Cache<Skill, SkillData>) cache);
                    log.debug("Skills serialization completed, JSON length: {}", skillJson != null ? skillJson.length() : 0);
                    return skillJson;
                case "quests":
                    String questJson = serializeQuestCache((Rs2Cache<Quest, QuestState>) cache);
                    log.debug("Quests serialization completed, JSON length: {}", questJson != null ? questJson.length() : 0);
                    return questJson;
                case "varbits":
                    String varbitJson = serializeVarbitCache((Rs2Cache<Integer, VarbitData>) cache);
                    log.debug("Varbits serialization completed, JSON length: {}", varbitJson != null ? varbitJson.length() : 0);
                    return varbitJson;
                case "varPlayerCache":
                    String varPlayerJson = serializeVarPlayerCache((Rs2Cache<Integer, VarbitData>) cache);
                    log.debug("VarPlayer serialization completed, JSON length: {}", varPlayerJson != null ? varPlayerJson.length() : 0);
                    return varPlayerJson;
                case "spiritTrees":
                    String spiritTreeJson = serializeSpiritTreeCache((Rs2Cache<SpiritTree, SpiritTreeData>) cache);
                    log.debug("SpiritTrees serialization completed, JSON length: {}", spiritTreeJson != null ? spiritTreeJson.length() : 0);
                    return spiritTreeJson;
                default:
                    log.warn("Unknown cache type for serialization: {}", configKey);
                    return null;
            }
        } catch (Exception e) {
            log.error("Failed to serialize cache data for {}", configKey, e);
            return null;
        }
    }
    
    /**
     * Deserializes cache data from JSON based on cache type.
     * Only persistent caches are deserialized (Skills, Quests, Varbits).
     * NPC cache is excluded as it's dynamically loaded based on game scene.
     */
    @SuppressWarnings("unchecked")
    private static <K, V> void deserializeCacheData(Rs2Cache<K, V> cache, String configKey, String json) {
        try {
            log.debug("Starting deserialization for cache type: {}, JSON length: {}", configKey, json != null ? json.length() : 0);
            
            switch (configKey) {
                case "skills":
                    deserializeSkillCache((Rs2Cache<Skill, SkillData>) cache, json);
                    break;
                case "quests":
                    deserializeQuestCache((Rs2Cache<Quest, QuestState>) cache, json);
                    break;
                case "varbits":
                    deserializeVarbitCache((Rs2Cache<Integer, VarbitData>) cache, json);
                    break;
                case "varPlayerCache":
                    deserializeVarPlayerCache((Rs2Cache<Integer, VarbitData>) cache, json);
                    break;
                case "spiritTrees":
                    deserializeSpiritTreeCache((Rs2Cache<SpiritTree, SpiritTreeData>) cache, json);
                    break;
                default:
                    log.warn("Unknown cache type for deserialization: {}", configKey);
            }
            
            log.debug("Deserialization completed for cache type: {}, final cache size: {}", configKey, cache.size());
        } catch (Exception e) {
            log.error("Failed to deserialize cache data for {}", configKey, e);
        }
    }
    
    // Skill cache serialization
    private static String serializeSkillCache(Rs2Cache<Skill, SkillData> cache) {
        // Use the new method to get all entries for serialization
        Map<Skill, SkillData> data = cache.getEntriesForSerialization();
        log.debug("Serializing {} skill entries", data.size());
        if (data.isEmpty()) {
            log.warn("Skills cache is empty during serialization");
            return "{}";
        }
        String json = gson.toJson(data);
        log.debug("Skills JSON preview: {}", json.length() > 200 ? json.substring(0, 200) + "..." : json);
        return json;
    }
    
    private static void deserializeSkillCache(Rs2Cache<Skill, SkillData> cache, String json) {
        Type type = new TypeToken<Map<Skill, SkillData>>(){}.getType();
        Map<Skill, SkillData> data = gson.fromJson(json, type);
        if (data != null) {
            int entriesLoaded = 0;
            for (Map.Entry<Skill, SkillData> entry : data.entrySet()) {
                cache.put(entry.getKey(), entry.getValue());
                entriesLoaded++;
            }
            log.debug("Deserialized {} skill entries into cache", entriesLoaded);
        } else {
            log.warn("Skill cache data was null after JSON parsing");
        }
    }
    
    // Quest cache serialization
    private static String serializeQuestCache(Rs2Cache<Quest, QuestState> cache) {
        // Use the new method to get all entries for serialization
        Map<Quest, QuestState> data = cache.getEntriesForSerialization();
        log.debug("Serializing {} quest entries", data.size());
        if (data.isEmpty()) {
            log.warn("Quest cache is empty during serialization");
            return "{}";
        }
        String json = gson.toJson(data);
        log.debug("Quest JSON preview: {}", json.length() > 200 ? json.substring(0, 200) + "..." : json);
        return json;
    }
    
    private static void deserializeQuestCache(Rs2Cache<Quest, QuestState> cache, String json) {
        Type type = new TypeToken<Map<Quest, QuestState>>(){}.getType();
        Map<Quest, QuestState> data = gson.fromJson(json, type);
        if (data != null) {
            int entriesLoaded = 0;
            for (Map.Entry<Quest, QuestState> entry : data.entrySet()) {
                cache.put(entry.getKey(), entry.getValue());
                entriesLoaded++;
            }
            log.debug("Deserialized {} quest entries into cache", entriesLoaded);
        } else {
            log.warn("Quest cache data was null after JSON parsing");
        }
    }
    
    // Varbit cache serialization
    private static String serializeVarbitCache(Rs2Cache<Integer, VarbitData> cache) {
        // Use the new method to get all entries for serialization
        Map<Integer, VarbitData> data = cache.getEntriesForSerialization();
        log.debug("Serializing {} varbit entries", data.size());
        if (data.isEmpty()) {
            log.warn("Varbit cache is empty during serialization");
            return "{}";
        }
        String json = gson.toJson(data);
        log.debug("Varbit JSON preview: {}", json.length() > 200 ? json.substring(0, 200) + "..." : json);
        return json;
    }
    
    private static void deserializeVarbitCache(Rs2Cache<Integer, VarbitData> cache, String json) {
        Type type = new TypeToken<Map<Integer, VarbitData>>(){}.getType();
        Map<Integer, VarbitData> data = gson.fromJson(json, type);
        if (data != null) {
            int entriesLoaded = 0;
            for (Map.Entry<Integer, VarbitData> entry : data.entrySet()) {
                cache.put(entry.getKey(), entry.getValue());
                entriesLoaded++;
            }
        
            log.debug("Deserialized {} varbit entries into cache", entriesLoaded);
        } else {
            log.warn("Varbit cache data was null after JSON parsing");
        }
    }
    
    // VarPlayer cache serialization - reuses VarbitData structure
    private static String serializeVarPlayerCache(Rs2Cache<Integer, VarbitData> cache) {
        // Use the new method to get all entries for serialization
        Map<Integer, VarbitData> data = cache.getEntriesForSerialization();
        log.debug("Serializing {} varplayer entries", data.size());
        if (data.isEmpty()) {
            log.warn("VarPlayer cache is empty during serialization");
            return "{}";
        }
        String json = gson.toJson(data);
        log.debug("VarPlayer JSON preview: {}", json.length() > 200 ? json.substring(0, 200) + "..." : json);
        return json;
    }
    
    private static void deserializeVarPlayerCache(Rs2Cache<Integer, VarbitData> cache, String json) {
        Type type = new TypeToken<Map<Integer, VarbitData>>(){}.getType();
        Map<Integer, VarbitData> data = gson.fromJson(json, type);
        if (data != null) {
            int entriesLoaded = 0;
            for (Map.Entry<Integer, VarbitData> entry : data.entrySet()) {
                cache.put(entry.getKey(), entry.getValue());
                entriesLoaded++;
            }
        
            log.debug("Deserialized {} varplayer entries into cache", entriesLoaded);
        } else {
            log.warn("VarPlayer cache data was null after JSON parsing");
        }
    }
    
    // Spirit tree cache serialization
    private static String serializeSpiritTreeCache(Rs2Cache<SpiritTree, SpiritTreeData> cache) {
        // Use the new method to get all entries for serialization
        Map<SpiritTree, SpiritTreeData> data = cache.getEntriesForSerialization();
        return gson.toJson(data);
    }
    
    private static void deserializeSpiritTreeCache(Rs2Cache<SpiritTree, SpiritTreeData> cache, String json) {
        Type type = new TypeToken<Map<SpiritTree, SpiritTreeData>>(){}.getType();
        Map<SpiritTree, SpiritTreeData> data = gson.fromJson(json, type);
        if (data != null) {
            int entriesLoaded = 0;
            for (Map.Entry<SpiritTree, SpiritTreeData> entry : data.entrySet()) {
                cache.put(entry.getKey(), entry.getValue());
                entriesLoaded++;
            }
            log.debug("Deserialized {} spirit tree entries into cache", entriesLoaded);
        }
    }
}
