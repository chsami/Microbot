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
import net.runelite.client.plugins.microbot.util.cache.Rs2PohCache;
import net.runelite.client.plugins.microbot.util.cache.model.SkillData;
import net.runelite.client.plugins.microbot.util.cache.model.SpiritTreeData;
import net.runelite.client.plugins.microbot.util.cache.model.VarbitData;
import net.runelite.client.plugins.microbot.util.farming.SpiritTree;
import net.runelite.client.plugins.microbot.util.poh.data.*;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.runelite.client.plugins.microbot.util.cache.Rs2PohCache.POH_CACHE_KEY;

/**
 * Serialization manager for Rs2UnifiedCache instances.
 * Handles automatic save/load to RuneLite profile configuration
 * similar to Rs2Bank serialization system.
 * 
 * Includes cache freshness tracking to prevent loading stale cache data
 * that wasn't properly saved due to ungraceful client shutdowns.
 * 
 * Cache freshness is determined by whether data was saved after being loaded,
 * not by session ID or time limits (unless explicitly specified).
 * This ensures we only load cache data that was properly persisted after modifications.
 */
@Slf4j
public class CacheSerializationManager {
    private static final String VERSION = "1.0.0"; // Version for cache serialization format compatibility
    private static final String CONFIG_GROUP = "microbot";
    private static final String METADATA_SUFFIX = "_metadata";
    private static final Gson gson;
    
    // Session identifier to track cache freshness across client restarts
    private static final String SESSION_ID = UUID.randomUUID().toString();
    
    /**
     * Metadata class to track cache freshness and validity
     */
    private static class CacheMetadata {
        private final String version;
        private final String sessionId;
        private final String saveTimestampUtc; // UTC timestamp in ISO 8601 format
        private final boolean stale;
        
        // UTC formatter for consistent timestamp handling
        private static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ISO_INSTANT;
        
        public CacheMetadata(String version, String sessionId, String saveTimestampUtc, boolean stale) {
            this.version = version;
            this.sessionId = sessionId;
            this.saveTimestampUtc = saveTimestampUtc;
            this.stale = stale;
        }
        
        /**
         * Create CacheMetadata with current UTC timestamp
         */
        public static CacheMetadata createWithCurrentUtcTime(String version, String sessionId, boolean stale) {
            String utcTimestamp = Instant.now().atOffset(ZoneOffset.UTC).format(UTC_FORMATTER);
            return new CacheMetadata(version, sessionId, utcTimestamp, stale);
        }
        
        public boolean isNewVersion(String currentVersion){
            // Check if the current version is different from the saved version
            return !this.version.equals(currentVersion);
        }
        
        /**
         * Checks if this metadata indicates fresh cache data that was properly saved after loading.
         * 
         * @param maxAgeMs Maximum age in milliseconds (0 = ignore time completely)
         * @return true if cache data is fresh and was saved after loading
         */
        public boolean isFresh(long maxAgeMs) {
            // Data is fresh if it was saved after being loaded (indicating proper persistence)
            if (stale) {
                return false;
            }
            
            // If maxAgeMs is 0, we don't care about time - only that it was saved after load
            if (maxAgeMs == 0) {
                return true;
            }
            
            // Otherwise check if it's within the time limit
            long age = getAgeMs();
            return age <= maxAgeMs;
        }
        
        public boolean isFromCurrentSession() {
            return SESSION_ID.equals(sessionId);
        }
        
        /**
         * Get age in milliseconds from the UTC timestamp
         */
        public long getAgeMs() {
            try {
                Instant saveTime = Instant.parse(saveTimestampUtc);
                return Instant.now().toEpochMilli() - saveTime.toEpochMilli();
            } catch (Exception e) {
                log.warn("Failed to parse UTC timestamp '{}', treating as very old", saveTimestampUtc);
                return Long.MAX_VALUE; // Treat as very old if parsing fails
            }
        }
        
        /**
         * Get the save timestamp as human-readable string
         */
        public String getSaveTimeFormatted() {
            try {
                Instant saveTime = Instant.parse(saveTimestampUtc);
                return saveTime.atOffset(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) + " UTC";
            } catch (Exception e) {
                return saveTimestampUtc; // Return raw if parsing fails
            }
        }
        
        public boolean isStale() {
            return stale;
        }        
    }
    
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
                .registerTypeAdapter(Rs2PohCache.TYPE_TOKEN, new PohTeleportDataAdapter())
                .create();
    }
    
    /**
     * Saves a cache to RuneLite profile configuration with character-specific keys.
     * Also stores metadata to track cache freshness and prevent loading stale data.
     *
     * @param cache The cache to save
     * @param configKey The base config key to save under
     * @param rsProfileKey The RuneLite profile key
     * @param playerName The player name for character-specific caching
     * @param <K> The key type
     * @param <V> The value type
     */
    public static <K, V> void saveCache(Rs2Cache<K, V> cache, String configKey, String rsProfileKey, String playerName) {
        try {
            if (rsProfileKey == null || Microbot.getConfigManager() == null) {
                log.warn("Cannot save cache {}: profile key or config manager not available", configKey);
                return;
            }

            if (playerName == null || playerName.trim().isEmpty()) {
                log.warn("Cannot save cache {}: player name not available", configKey);
                return;
            }

            // create character-specific config key
            String characterConfigKey = createCharacterSpecificKey(configKey, playerName);

            // Serialize the cache data
            String json = serializeCacheData(cache, configKey);

            if (json != null && !json.isEmpty()) {
                log.debug("{} JSON length: {} for player: {}", configKey, json.length(), playerName);
                Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, rsProfileKey, characterConfigKey, json);

                // Store metadata to track cache freshness
                // Mark as stale=false since we're actively saving cache data
                CacheMetadata metadata = CacheMetadata.createWithCurrentUtcTime(VERSION, SESSION_ID, false);
                String metadataJson = gson.toJson(metadata, CacheMetadata.class);
                String metadataKey = characterConfigKey + METADATA_SUFFIX;
                Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, rsProfileKey, metadataKey, metadataJson);
                log.info("Saved cache \"{}\" for player \"{}\" with updated metadata for session {} at {}", configKey, playerName, SESSION_ID, metadata.getSaveTimeFormatted());
            } else {
                log.warn("No data to save for cache {} for player {}", configKey, playerName);
            }

        } catch (Exception e) {
            log.error("Failed to save cache {} to config", configKey, e);
        }
    }



    /**
     * Gets the current player name using Rs2Player utility.
     *
     * @return Player name or null if not available
     */
    private static String getCurrentPlayerName() {
        try {
            if (!Microbot.isLoggedIn()) {
                return null;
            }
            // use Rs2Player to get local player and extract name
            var localPlayer = Rs2Player.getLocalPlayer();
            return localPlayer != null ? localPlayer.getName() : null;
        } catch (Exception e) {
            log.debug("Error getting current player name: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Creates a character-specific config key by appending player name.
     *
     * @param baseKey The base config key
     * @param playerName The player name
     * @return Character-specific config key
     */
    public static String createCharacterSpecificKey(String baseKey, String playerName) {
        // sanitize player name for config key usage
        String sanitizedPlayerName = playerName.replaceAll("[^a-zA-Z0-9_-]", "_");
        return baseKey + "_" + sanitizedPlayerName;
    }
    
    /**
     * Loads a cache from RuneLite profile configuration with character-specific keys.
     * Checks cache freshness metadata before loading to prevent loading stale data.
     *
     * @param cache The cache to load into
     * @param configKey The base config key to load from
     * @param rsProfileKey The profile key to load from
     * @param playerName The player name for character-specific caching
     * @param forceInvalidate Whether to force cache invalidation
     * @param <K> The key type
     * @param <V> The value type
     */
    public static <K, V> void loadCache(Rs2Cache<K, V> cache, String configKey, String rsProfileKey, String playerName, boolean forceInvalidate) {
        loadCache(cache, configKey, rsProfileKey, playerName, 0, forceInvalidate); // Default: ignore time, only check if saved after load
    }


    /**
     * Loads a cache from RuneLite profile configuration with age limit.
     * Checks cache freshness metadata before loading to prevent loading stale data.
     * 
     * @param cache The cache to load into
     * @param configKey The config key to load from
     * @param rsProfileKey The profile key to load from
     * @param maxAgeMs Maximum age in milliseconds (0 = ignore time completely)
     * @param <K> The key type
     * @param <V> The value type
     */
    public static <K, V> void loadCache(Rs2Cache<K, V> cache, String configKey, String rsProfileKey, String playerName, long maxAgeMs, boolean forceInvalidate) {
        try {
            if (Microbot.getConfigManager() == null) {
                log.warn("Cannot load cache {}: config manager not available", configKey);
                return;
            }

            if (playerName == null || playerName.trim().isEmpty()) {
                log.warn("Cannot load cache {}: player name not available", configKey);
                return;
            }

            // create character-specific config key
            String characterConfigKey = createCharacterSpecificKey(configKey, playerName);

            // Check cache freshness metadata first
            String metadataKey = characterConfigKey + METADATA_SUFFIX;
            String metadataJson = Microbot.getConfigManager().getConfiguration(CONFIG_GROUP, rsProfileKey, metadataKey);
            
            CacheMetadata metadata = null;
            boolean shouldLoadFromConfig = false;
            
            if (metadataJson != null && !metadataJson.isEmpty()) {
                try {
                    metadata = gson.fromJson(metadataJson, CacheMetadata.class);
                    if (metadata != null){
                        long age = metadata.getAgeMs();
                        boolean stale = metadata.isStale();
                        boolean fromCurrentSession = metadata.isFromCurrentSession();
                        String oldVersion = metadata.version;
                        boolean useLoadCacheData = !stale && metadata.isFresh(maxAgeMs) && !metadata.isNewVersion(VERSION);
                        if (useLoadCacheData) {
                            shouldLoadFromConfig = true;
                            log.info("\nCache \"{}\" for player \"{}\" has valid metadata, loading from config \n" + //
                                                                "  -stale: {}\n" + //
                                                                "  -age: {}ms -isfresh? {} -max Age {}ms\n" + //
                                                                "  -from current session: {}\n" + //
                                                                "  -current version {} \n-last version {}\n-is new version? {}",
                                    configKey, playerName, stale, age,  metadata.isFresh(maxAgeMs), maxAgeMs, fromCurrentSession, VERSION, oldVersion, metadata.isNewVersion(VERSION));                                    
                        } else {                        
                            log.warn("\nCache \"{}\" for player \"{}\" metadata indicated using fresh cache \n" + //
                                                                "  -stale: {}\n" + //
                                                                "  -age: {}ms -isfresh? {} -max {} ms\n" + //
                                                                "  -from current session: {}\n" + //
                                                                "  -current version {} - last version {}- is new version? {}",
                                    configKey, playerName, stale, age,  metadata.isFresh(maxAgeMs), maxAgeMs, fromCurrentSession, VERSION, oldVersion, metadata.isNewVersion(VERSION));                                    
                        }
                    }
                } catch (JsonSyntaxException e) {
                    log.warn("Failed to parse cache metadata for {} player {}, treating as stale", configKey, playerName, e);
                }
            } else {
                log.warn("No cache metadata found for {} player {}, treating as stale data", configKey, playerName);
            }
            
            if (!shouldLoadFromConfig) {
                // Invalidate cache and start fresh instead of loading potentially stale data
                if (forceInvalidate) cache.invalidateAll();                                
            }else{
                // Proceed with loading since metadata indicates fresh data
                String json = Microbot.getConfigManager().getConfiguration(CONFIG_GROUP, rsProfileKey, characterConfigKey);
                if (json != null && !json.isEmpty()) {
                    deserializeCacheData(cache, configKey, json);
                    log.debug("Loaded cache {} for player {} from profile config, entries loaded: {}", configKey, playerName, cache.size());
                } else {
                    log.warn("No cached data found for {} player {} despite fresh metadata", configKey, playerName);
                }
            }
             // Mark metadata as loaded but not yet saved to distinguish from fresh saves
            CacheMetadata loadedMetadata = CacheMetadata.createWithCurrentUtcTime(VERSION, SESSION_ID, true);
            String loadedMetadataJson = gson.toJson(loadedMetadata,CacheMetadata.class);
            Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, rsProfileKey, metadataKey, loadedMetadataJson);         
            Microbot.getConfigManager().sendConfig(); // must be called to ensure config changes are saved immediately to the cloud and/or disk

        } catch (JsonSyntaxException e) {
            log.warn("Failed to parse cached data for {} player {}, clearing corrupted cache and starting fresh", configKey, playerName, e);
            // Clear the corrupted cache data
            clearCache(configKey, rsProfileKey);
        } catch (Exception e) {
            log.error("Failed to load cache {} for player {} from config", configKey, playerName, e);
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
     * Also clears associated metadata.
     * 
     * @param configKey The config key to clear
     * @param rsProfileKey The profile key to clear from
     */
    public static void clearCache(String configKey, String rsProfileKey) {
        try {
            if (rsProfileKey != null && Microbot.getConfigManager() != null) {
                // get player name for character-specific caching
                String playerName = getCurrentPlayerName();
                if (playerName != null && !playerName.trim().isEmpty()) {
                    // create character-specific config key
                    String characterConfigKey = createCharacterSpecificKey(configKey, playerName);

                    // Clear the character-specific cache data
                    Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, rsProfileKey, characterConfigKey, null);
                    // Clear the character-specific metadata
                    String metadataKey = characterConfigKey + METADATA_SUFFIX;
                    Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, rsProfileKey, metadataKey, null);
                    log.debug("Cleared cache {} and metadata for player {} from profile config for profile: {}", configKey, playerName, rsProfileKey);
                } else {
                    // fallback to old method if player name not available
                    Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, rsProfileKey, configKey, null);
                    String metadataKey = configKey + METADATA_SUFFIX;
                    Microbot.getConfigManager().setConfiguration(CONFIG_GROUP, rsProfileKey, metadataKey, null);
                    log.debug("Cleared cache {} and metadata from profile config for profile: {} (no player name)", configKey, rsProfileKey);
                }
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
                case POH_CACHE_KEY:
                    String pohJson = serializePohCache((Rs2Cache<String, List<PohTeleport>>) cache);
                    log.debug("PoH serialization completed, JSON length: {}", pohJson != null ? pohJson.length() : 0);
                    return pohJson;
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
                case POH_CACHE_KEY:
                    deserializePohCache((Rs2Cache<String, List<PohTeleport>>) cache, json);
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
            int entriesSkipped = 0;
            for (Map.Entry<Skill, SkillData> entry : data.entrySet()) {
                // Only load entries that are not already present in cache (cache entries are newer)
                if (!cache.containsKey(entry.getKey())) {
                    cache.put(entry.getKey(), entry.getValue());
                    entriesLoaded++;
                } else {
                    entriesSkipped++;
                    log.debug("Skipped loading skill {} - already present in cache with newer data", entry.getKey());
                }
            }
            log.debug("Deserialized {} skill entries into cache, skipped {} existing entries", entriesLoaded, entriesSkipped);
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
            int entriesSkipped = 0;
            for (Map.Entry<Quest, QuestState> entry : data.entrySet()) {
                // Only load entries that are not already present in cache (cache entries are newer)
                if (!cache.containsKey(entry.getKey())) {
                    cache.put(entry.getKey(), entry.getValue());
                    entriesLoaded++;
                } else {
                    entriesSkipped++;
                    log.debug("Skipped loading quest {} - already present in cache with newer data", entry.getKey());
                }
            }
            log.debug("Deserialized {} quest entries into cache, skipped {} existing entries", entriesLoaded, entriesSkipped);
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
            int entriesSkipped = 0;
            for (Map.Entry<Integer, VarbitData> entry : data.entrySet()) {
                // Only load entries that are not already present in cache (cache entries are newer)
                if (!cache.containsKey(entry.getKey())) {
                    cache.put(entry.getKey(), entry.getValue());
                    entriesLoaded++;
                } else {
                    entriesSkipped++;
                    log.debug("Skipped loading varbit {} - already present in cache with newer data", entry.getKey());
                }
            }
        
            log.debug("Deserialized {} varbit entries into cache, skipped {} existing entries", entriesLoaded, entriesSkipped);
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
            int entriesSkipped = 0;
            for (Map.Entry<Integer, VarbitData> entry : data.entrySet()) {
                // Only load entries that are not already present in cache (cache entries are newer)
                if (!cache.containsKey(entry.getKey())) {
                    cache.put(entry.getKey(), entry.getValue());
                    entriesLoaded++;
                } else {
                    entriesSkipped++;
                    log.debug("Skipped loading varplayer {} - already present in cache with newer data", entry.getKey());
                }
            }
        
            log.debug("Deserialized {} varplayer entries into cache, skipped {} existing entries", entriesLoaded, entriesSkipped);
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
            int entriesSkipped = 0;
            for (Map.Entry<SpiritTree, SpiritTreeData> entry : data.entrySet()) {
                // Only load entries that are not already present in cache (cache entries are newer)
                if (!cache.containsKey(entry.getKey())) {
                    cache.put(entry.getKey(), entry.getValue());
                    entriesLoaded++;
                } else {
                    entriesSkipped++;
                    log.debug("Skipped loading spirit tree {} - already present in cache with newer data", entry.getKey());
                }
            }
            log.debug("Deserialized {} spirit tree entries into cache, skipped {} existing entries", entriesLoaded, entriesSkipped);
        } else {
            log.warn("Spirit tree cache data was null after JSON parsing");
        }
    }

    //Poh Serialization
    private static String serializePohCache(Rs2Cache<String, List<PohTeleport>> cache) {
        // Use the new method to get all entries for serialization
        Map<String, List<PohTeleport>> data = cache.getEntriesForSerialization();
        String json = gson.toJson(data, Rs2PohCache.TYPE_TOKEN);
        log.debug("Serialized Poh Cache");
        log.debug(json);
        return json;
    }

    //Poh Deserialization
    private static void deserializePohCache(Rs2Cache<String, List<PohTeleport>> cache, String json){
        log.debug("Deserializing Poh cache");
        log.debug(json);
        Map<String, List<PohTeleport>> data = gson.fromJson(json, Rs2PohCache.TYPE_TOKEN);
        if (data != null) {
            int entriesLoaded = 0;
            int entriesSkipped = 0;
            for (Map.Entry<String, List<PohTeleport>> entry : data.entrySet()) {
                // Only load entries that are not already present in cache (cache entries are newer)
                if (!cache.containsKey(entry.getKey())) {
                    cache.put(entry.getKey(), entry.getValue());
                    entriesLoaded++;
                } else {
                    entriesSkipped++;
                    log.debug("Skipped loading poh teleport type {} - already present in cache with newer data", entry.getKey());
                }
            }
            log.debug("Deserialized {} poh teleport entries into cache, skipped {} existing entries", entriesLoaded, entriesSkipped);
        } else {
            log.warn("Poh cache data was null after JSON parsing");
        }
    }
}
