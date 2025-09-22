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
import net.runelite.client.plugins.microbot.util.cache.compression.CompressionUtils;
import net.runelite.client.plugins.microbot.util.cache.model.SkillData;
import net.runelite.client.plugins.microbot.util.cache.model.SpiritTreeData;
import net.runelite.client.plugins.microbot.util.cache.model.VarbitData;
import net.runelite.client.plugins.microbot.util.farming.SpiritTree;
import net.runelite.client.plugins.microbot.util.poh.data.*;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.config.ConfigManager;

import java.lang.reflect.Type;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static net.runelite.client.plugins.microbot.util.cache.Rs2PohCache.POH_CACHE_KEY;

/**
 * Serialization manager for Rs2UnifiedCache instances.
 * Handles automatic save/load to file-based storage under .runelite/microbot-profiles
 * to prevent RuneLite profile bloat and improve performance.
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
    private static final String BASE_DIRECTORY = ".runelite/microbot-profiles";
    private static final String CACHE_SUBDIRECTORY = "caches";
    private static final String METADATA_SUFFIX = ".metadata";
    private static final String JSON_EXTENSION = ".json";

    // Cloud sync constants
    private static final String CONFIG_GROUP = "microbot";
    private static final String COMPRESSED_SUFFIX = "_compressed";

    private static final Gson gson;
    
    // Session identifier to track cache freshness across client restarts
    private static final String SESSION_ID = UUID.randomUUID().toString();
    
    /**
     * Enhanced metadata class to track cache freshness, validity, and integrity.
     * Inspired by GLite's PersistenceMetadata with data size tracking and cache naming.
     */
    private static class CacheMetadata {
        private final String version;
        private final String sessionId;
        private final String saveTimestampUtc; // UTC timestamp in ISO 8601 format
        private final boolean stale;
        private final int dataSize; // Size of serialized data for integrity checks
        private final String cacheName; // Name of cache for debugging
        
        // UTC formatter for consistent timestamp handling
        private static final DateTimeFormatter UTC_FORMATTER = DateTimeFormatter.ISO_INSTANT;
        
        public CacheMetadata(String version, String sessionId, String saveTimestampUtc, boolean stale, int dataSize, String cacheName) {
            this.version = version;
            this.sessionId = sessionId;
            this.saveTimestampUtc = saveTimestampUtc;
            this.stale = stale;
            this.dataSize = dataSize;
            this.cacheName = cacheName;
        }
        
        /**
         * Create CacheMetadata with current UTC timestamp
         */
        public static CacheMetadata createWithCurrentUtcTime(String version, String sessionId, boolean stale, int dataSize, String cacheName) {
            String utcTimestamp = Instant.now().atOffset(ZoneOffset.UTC).format(UTC_FORMATTER);
            return new CacheMetadata(version, sessionId, utcTimestamp, stale, dataSize, cacheName);
        }

        /**
         * Create CacheMetadata with current UTC timestamp and convenience method for common use
         */
        public static CacheMetadata createWithCurrentUtcTime(String version, String sessionId, boolean stale) {
            String utcTimestamp = Instant.now().atOffset(ZoneOffset.UTC).format(UTC_FORMATTER);
            return new CacheMetadata(version, sessionId, utcTimestamp, stale, 0, "unknown");
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

        /**
         * Get the data size for integrity checking
         */
        public int getDataSize() {
            return dataSize;
        }

        /**
         * Get the cache name for debugging
         */
        public String getCacheName() {
            return cacheName;
        }

        /**
         * Validates that this metadata has reasonable values
         */
        public void validate() throws IllegalStateException {
            if (version == null || version.trim().isEmpty()) {
                throw new IllegalStateException("Version cannot be null or empty");
            }
            if (dataSize < 0) {
                throw new IllegalStateException("Data size cannot be negative");
            }
            if (saveTimestampUtc == null || saveTimestampUtc.trim().isEmpty()) {
                throw new IllegalStateException("Save timestamp cannot be null or empty");
            }
        }

        /**
         * Gets a human-readable age description
         */
        public String getFormattedAge() {
            long ageMs = getAgeMs();

            if (ageMs < 1000) {
                return ageMs + "ms ago";
            } else if (ageMs < 60_000) {
                return (ageMs / 1000) + "s ago";
            } else if (ageMs < 3_600_000) {
                return (ageMs / 60_000) + "m ago";
            } else if (ageMs < 86_400_000) {
                return (ageMs / 3_600_000) + "h ago";
            } else {
                return (ageMs / 86_400_000) + "d ago";
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("CacheMetadata{");
            sb.append("version='").append(version).append("'");
            sb.append(", cacheName='").append(cacheName).append("'");
            sb.append(", timestamp=").append(getSaveTimeFormatted());
            sb.append(" (").append(getFormattedAge()).append(")");
            sb.append(", dataSize=").append(dataSize);
            sb.append(", stale=").append(stale);
            sb.append(", sessionId='").append(sessionId).append("'");
            sb.append("}");
            return sb.toString();
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
     * Saves a cache to file-based storage with character-specific directory structure.
     * Also stores metadata to track cache freshness and prevent loading stale data.
     *
     * @param cache The cache to save
     * @param configKey The cache type identifier (skills, quests, etc.)
     * @param rsProfileKey The RuneLite profile key
     * @param playerName The player name for character-specific caching
     * @param <K> The key type
     * @param <V> The value type
     */
    public static <K, V> void saveCache(Rs2Cache<K, V> cache, String configKey, String rsProfileKey, String playerName) {
        try {
            if (rsProfileKey == null) {
                log.warn("Cannot save cache {}: profile key not available", configKey);
                return;
            }

            if (playerName == null || playerName.trim().isEmpty()) {
                log.warn("Cannot save cache {}: player name not available", configKey);
                return;
            }

            // create directory structure
            Path cacheDir = getCacheDirectory(rsProfileKey, playerName);
            Files.createDirectories(cacheDir);

            // serialize cache data
            String json = serializeCacheData(cache, configKey);
            if (json == null || json.trim().isEmpty()) {
                log.warn("No data to save for cache {} for player {}", configKey, playerName);
                return;
            }

            // save cache data file
            Path cacheFile = cacheDir.resolve(configKey + JSON_EXTENSION);
            Files.write(cacheFile, json.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            // create and save metadata with data size and cache name
            CacheMetadata metadata = CacheMetadata.createWithCurrentUtcTime(VERSION, SESSION_ID, false, json.length(), configKey);
            String metadataJson = gson.toJson(metadata, CacheMetadata.class);
            Path metadataFile = cacheDir.resolve(configKey + METADATA_SUFFIX);
            Files.write(metadataFile, metadataJson.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            log.info("Saved cache \"{}\" for player \"{}\" to file ({} chars) at {}",
                    configKey, playerName, json.length(), metadata.getSaveTimeFormatted());

        } catch (IOException e) {
            log.error("Failed to save cache {} to file for player {}", configKey, playerName, e);
        } catch (Exception e) {
            log.error("Failed to save cache {} for player {}", configKey, playerName, e);
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
     * Loads a cache from file-based storage with character-specific directory structure.
     * Checks cache freshness metadata before loading to prevent loading stale data.
     *
     * @param cache The cache to load into
     * @param configKey The cache type identifier (skills, quests, etc.)
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
     * Loads a cache from file-based storage with age limit.
     * Checks cache freshness metadata before loading to prevent loading stale data.
     *
     * @param cache The cache to load into
     * @param configKey The cache type identifier
     * @param rsProfileKey The profile key to load from
     * @param playerName The player name for character-specific caching
     * @param maxAgeMs Maximum age in milliseconds (0 = ignore time completely)
     * @param forceInvalidate Whether to force cache invalidation
     * @param <K> The key type
     * @param <V> The value type
     */
    public static <K, V> void loadCache(Rs2Cache<K, V> cache, String configKey, String rsProfileKey, String playerName, long maxAgeMs, boolean forceInvalidate) {
        try {
            if (rsProfileKey == null) {
                log.warn("Cannot load cache {}: profile key not available", configKey);
                return;
            }

            if (playerName == null || playerName.trim().isEmpty()) {
                log.warn("Cannot load cache {}: player name not available", configKey);
                return;
            }

            Path cacheDir = getCacheDirectory(rsProfileKey, playerName);
            Path metadataFile = cacheDir.resolve(configKey + METADATA_SUFFIX);
            Path cacheFile = cacheDir.resolve(configKey + JSON_EXTENSION);

            // check if files exist
            if (!Files.exists(metadataFile) || !Files.exists(cacheFile)) {
                log.debug("No cache files found for {} player {}, starting fresh", configKey, playerName);
                if (forceInvalidate) cache.invalidateAll();

                // create initial stale metadata to track first load
                CacheMetadata loadedMetadata = CacheMetadata.createWithCurrentUtcTime(VERSION, SESSION_ID, true, 0, configKey);
                String metadataJson = gson.toJson(loadedMetadata, CacheMetadata.class);
                Files.createDirectories(cacheDir);
                Files.write(metadataFile, metadataJson.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
                return;
            }

            // read and validate metadata
            String metadataJson = Files.readString(metadataFile);
            CacheMetadata metadata = gson.fromJson(metadataJson, CacheMetadata.class);

            if (metadata == null || !metadata.isFresh(maxAgeMs)) {
                log.warn("Cache \"{}\" for player \"{}\" metadata indicates stale data (age: {}ms, fresh: {})",
                        configKey, playerName, metadata != null ? metadata.getAgeMs() : "unknown",
                        metadata != null ? metadata.isFresh(maxAgeMs) : false);

                if (forceInvalidate) cache.invalidateAll();
                return;
            }

            // load cache data
            String json = Files.readString(cacheFile);
            if (json != null && !json.trim().isEmpty()) {
                deserializeCacheData(cache, configKey, json);
                log.debug("Loaded cache {} for player {} from file, entries loaded: {}", configKey, playerName, cache.size());
            } else {
                log.warn("Cache file exists but contains no data for {} player {}", configKey, playerName);
            }

            // mark as loaded but stale until next save
            CacheMetadata loadedMetadata = CacheMetadata.createWithCurrentUtcTime(VERSION, SESSION_ID, true, json.length(), configKey);
            String updatedMetadataJson = gson.toJson(loadedMetadata, CacheMetadata.class);
            Files.write(metadataFile, updatedMetadataJson.getBytes(), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

        } catch (JsonSyntaxException e) {
            log.warn("Failed to parse cache data for {} player {}, clearing corrupted cache", configKey, playerName, e);
            clearCacheFiles(configKey, rsProfileKey, playerName);
        } catch (IOException e) {
            log.error("Failed to load cache {} for player {} from file", configKey, playerName, e);
        } catch (Exception e) {
            log.error("Failed to load cache {} for player {}", configKey, playerName, e);
        }
    }
    
    /**
     * Clears cache files for current player and profile.
     *
     * @param configKey The cache type to clear
     */
    public static void clearCache(String configKey) {
        try {
            String rsProfileKey = Microbot.getConfigManager() != null ? Microbot.getConfigManager().getRSProfileKey() : null;
            String playerName = getCurrentPlayerName();
            clearCacheFiles(configKey, rsProfileKey, playerName);
        } catch (Exception e) {
            log.error("Failed to clear cache {} files", configKey, e);
        }
    }
    
    /**
     * Clears cache files for a specific profile and player.
     *
     * @param configKey The cache type to clear
     * @param rsProfileKey The profile key
     */
    public static void clearCache(String configKey, String rsProfileKey) {
        String playerName = getCurrentPlayerName();
        clearCacheFiles(configKey, rsProfileKey, playerName);
    }

    /**
     * Clears cache files for specific cache type, profile, and player.
     *
     * @param configKey The cache type to clear
     * @param rsProfileKey The profile key
     * @param playerName The player name
     */
    public static void clearCacheFiles(String configKey, String rsProfileKey, String playerName) {
        try {
            if (rsProfileKey == null || playerName == null || playerName.trim().isEmpty()) {
                log.warn("Cannot clear cache files: profile key or player name not available");
                return;
            }

            Path cacheDir = getCacheDirectory(rsProfileKey, playerName);
            Path cacheFile = cacheDir.resolve(configKey + JSON_EXTENSION);
            Path metadataFile = cacheDir.resolve(configKey + METADATA_SUFFIX);

            Files.deleteIfExists(cacheFile);
            Files.deleteIfExists(metadataFile);

            log.debug("Cleared cache files for {} player {}", configKey, playerName);
        } catch (IOException e) {
            log.error("Failed to clear cache files for {} player {}", configKey, playerName, e);
        }
    }

    /**
     * Gets the cache directory path for a profile and player using URL encoding for safety.
     * This ensures that different profiles/players don't collide into the same directory.
     */
    private static Path getCacheDirectory(String profileKey, String playerName) {
        try {
            // use URL encoding to safely handle special characters while preserving uniqueness
            String encodedPlayerName = URLEncoder.encode(playerName, StandardCharsets.UTF_8);
            String encodedProfileKey = URLEncoder.encode(profileKey, StandardCharsets.UTF_8);

            Path userHome = Paths.get(System.getProperty("user.home"));
            return userHome.resolve(BASE_DIRECTORY)
                          .resolve(encodedProfileKey)
                          .resolve(encodedPlayerName)
                          .resolve(CACHE_SUBDIRECTORY);
        } catch (Exception e) {
            log.error("Failed to encode path components, falling back to basic sanitization", e);
            // fallback to basic sanitization if URL encoding fails
            String sanitizedPlayerName = playerName.replaceAll("[^a-zA-Z0-9_-]", "_");
            String sanitizedProfileKey = profileKey.replaceAll("[^a-zA-Z0-9_-]", "_");

            Path userHome = Paths.get(System.getProperty("user.home"));
            return userHome.resolve(BASE_DIRECTORY)
                          .resolve(sanitizedProfileKey)
                          .resolve(sanitizedPlayerName)
                          .resolve(CACHE_SUBDIRECTORY);
        }
    }

    /**
     * Serializes cache data to JSON based on cache type.
     * This method handles different cache types with specific serialization strategies.
     * Only persistent caches are serialized (Skills, Quests, Varbits).
     * NPC cache is excluded as it's dynamically loaded based on game scene.
     */
    @SuppressWarnings("unchecked")
    public static <K, V> String serializeCacheData(Rs2Cache<K, V> cache, String configKey) {
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
    public static <K, V> void deserializeCacheData(Rs2Cache<K, V> cache, String configKey, String json) {
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
     * Saves cache data to RuneLite profile using compression for optional cloud sync.
     * This is a secondary storage method used when cloud sync is enabled.
     *
     * @param cache The cache to save
     * @param configKey The cache type identifier
     * @param rsProfileKey The RuneLite profile key
     * @param playerName The player name
     * @param <K> The key type
     * @param <V> The value type
     */
    public static <K, V> void saveCacheToProfile(Rs2Cache<K, V> cache, String configKey, String rsProfileKey, String playerName) {
        try {
            ConfigManager configManager = Microbot.getConfigManager();
            if (configManager == null) {
                log.warn("Cannot save cache to profile: ConfigManager not available");
                return;
            }

            // check if cloud sync is enabled
            boolean cloudSyncEnabled = configManager.getConfiguration(CONFIG_GROUP, "enableCacheCloudSync", Boolean.class);
            if (!cloudSyncEnabled) {
                log.debug("Cloud sync disabled, skipping profile save for cache {}", configKey);
                return;
            }

            if (playerName == null || playerName.trim().isEmpty()) {
                log.warn("Cannot save cache to profile: player name not available");
                return;
            }

            // serialize cache data
            String jsonData = serializeCacheData(cache, configKey);
            if (jsonData == null || jsonData.trim().isEmpty()) {
                log.debug("No data to save to profile for cache {} player {}", configKey, playerName);
                return;
            }

            // compress data for profile storage
            CompressionUtils.CompressedData compressedData = CompressionUtils.compressForProfile(jsonData);
            if (!compressedData.isValid()) {
                log.warn("Cache data too large for profile storage: {} (cache: {}, player: {})",
                        compressedData.getCompressionSummary(), configKey, playerName);
                return;
            }

            // save compressed data to profile
            String profileKey = createCharacterSpecificKey(configKey + COMPRESSED_SUFFIX, playerName);
            configManager.setConfiguration(CONFIG_GROUP, profileKey, compressedData.compressedData);

            log.debug("Saved compressed cache {} to profile for player {} - {}",
                    configKey, playerName, compressedData.getCompressionSummary());

        } catch (Exception e) {
            log.error("Failed to save cache {} to profile for player {}", configKey, playerName, e);
        }
    }

    /**
     * Loads cache data from RuneLite profile using decompression for cloud sync.
     * This is a secondary storage method used when cloud sync is enabled and local files don't exist.
     *
     * @param cache The cache to load into
     * @param configKey The cache type identifier
     * @param rsProfileKey The profile key to load from
     * @param playerName The player name
     * @param forceInvalidate Whether to force cache invalidation
     * @param <K> The key type
     * @param <V> The value type
     * @return true if data was loaded from profile, false otherwise
     */
    public static <K, V> boolean loadCacheFromProfile(Rs2Cache<K, V> cache, String configKey, String rsProfileKey, String playerName, boolean forceInvalidate) {
        try {
            ConfigManager configManager = Microbot.getConfigManager();
            if (configManager == null) {
                log.debug("Cannot load cache from profile: ConfigManager not available");
                return false;
            }

            // check if cloud sync is enabled
            boolean cloudSyncEnabled = configManager.getConfiguration(CONFIG_GROUP, "enableCacheCloudSync", Boolean.class);
            if (!cloudSyncEnabled) {
                log.debug("Cloud sync disabled, skipping profile load for cache {}", configKey);
                return false;
            }

            if (playerName == null || playerName.trim().isEmpty()) {
                log.debug("Cannot load cache from profile: player name not available");
                return false;
            }

            // try to load compressed data from profile
            String profileKey = createCharacterSpecificKey(configKey + COMPRESSED_SUFFIX, playerName);
            String compressedData = configManager.getConfiguration(CONFIG_GROUP, profileKey);
            if (compressedData == null || compressedData.trim().isEmpty()) {
                log.debug("No compressed cache data found in profile for {} player {}", configKey, playerName);
                return false;
            }

            // decompress data
            String jsonData = CompressionUtils.decompressFromProfile(compressedData);
            if (jsonData == null || jsonData.trim().isEmpty()) {
                log.warn("Failed to decompress cache data from profile for {} player {}", configKey, playerName);
                return false;
            }

            // deserialize cache data
            if (forceInvalidate) cache.invalidateAll();
            deserializeCacheData(cache, configKey, jsonData);

            log.debug("Loaded cache {} for player {} from compressed profile data ({} chars decompressed)",
                    configKey, playerName, jsonData.length());
            return true;

        } catch (Exception e) {
            log.error("Failed to load cache {} from profile for player {}", configKey, playerName, e);
            return false;
        }
    }

    /**
     * Enhanced cache saving that implements dual storage: files (primary) + compressed profiles (secondary).
     * Files provide fast local access, while compressed profiles enable cross-device sync.
     *
     * @param cache The cache to save
     * @param configKey The cache type identifier
     * @param rsProfileKey The RuneLite profile key
     * @param playerName The player name
     * @param <K> The key type
     * @param <V> The value type
     */
    public static <K, V> void saveCacheWithDualStorage(Rs2Cache<K, V> cache, String configKey, String rsProfileKey, String playerName) {
        // primary storage: save to file for fast local access
        saveCache(cache, configKey, rsProfileKey, playerName);

        // secondary storage: save to compressed profile for cloud sync (if enabled)
        saveCacheToProfile(cache, configKey, rsProfileKey, playerName);
    }

    /**
     * Enhanced cache loading with file-first strategy and profile fallback.
     * Prioritizes local files for performance, falls back to compressed profile data for cloud sync.
     *
     * @param cache The cache to load into
     * @param configKey The cache type identifier
     * @param rsProfileKey The profile key to load from
     * @param playerName The player name
     * @param forceInvalidate Whether to force cache invalidation
     * @param <K> The key type
     * @param <V> The value type
     */
    public static <K, V> void loadCacheWithDualStorage(Rs2Cache<K, V> cache, String configKey, String rsProfileKey, String playerName, boolean forceInvalidate) {
        // try file-based loading first (faster)
        try {
            if (rsProfileKey != null && playerName != null && !playerName.trim().isEmpty()) {
                Path cacheDir = getCacheDirectory(rsProfileKey, playerName);
                Path metadataFile = cacheDir.resolve(configKey + METADATA_SUFFIX);
                Path cacheFile = cacheDir.resolve(configKey + JSON_EXTENSION);

                if (Files.exists(metadataFile) && Files.exists(cacheFile)) {
                    // file exists, use normal file loading
                    loadCache(cache, configKey, rsProfileKey, playerName, forceInvalidate);
                    return;
                }
            }
        } catch (Exception e) {
            log.debug("File-based loading failed, will try profile fallback: {}", e.getMessage());
        }

        // fallback to compressed profile data (cloud sync)
        boolean loadedFromProfile = loadCacheFromProfile(cache, configKey, rsProfileKey, playerName, forceInvalidate);
        if (loadedFromProfile) {
            log.debug("Loaded cache {} for player {} from profile data (cloud sync)", configKey, playerName);
        } else {
            log.debug("No cache data found for {} player {} in files or profile", configKey, playerName);
            if (forceInvalidate) cache.invalidateAll();
        }
    }
}
