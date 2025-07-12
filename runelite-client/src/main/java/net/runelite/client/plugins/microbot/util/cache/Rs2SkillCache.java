package net.runelite.client.plugins.microbot.util.cache;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.model.SkillData;
import net.runelite.client.plugins.microbot.util.cache.strategy.simple.SkillUpdateStrategy;
import net.runelite.client.plugins.microbot.util.cache.serialization.CacheSerializable;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.function.Supplier;

/**
 * Thread-safe cache for skill levels and experience using the unified cache architecture.
 * Automatically updates when StatChanged events are received and supports persistence.
 * 
 * This class extends Rs2UnifiedCache and provides specific skill caching functionality
 * with proper EventBus integration for @Subscribe methods.
 */
@Slf4j
public class Rs2SkillCache extends Rs2Cache<Skill, SkillData> implements CacheSerializable {
    
    private static Rs2SkillCache instance;
    
    /**
     * Private constructor for singleton pattern.
     */
    private Rs2SkillCache() {
        super("SkillCache", CacheMode.EVENT_DRIVEN_ONLY);
        this.withUpdateStrategy(new SkillUpdateStrategy())
                .withPersistence("skills");
    }
    
    /**
     * Gets the singleton instance of Rs2SkillCache.
     * 
     * @return The singleton skill cache instance
     */
    public static synchronized Rs2SkillCache getInstance() {
        if (instance == null) {
            instance = new Rs2SkillCache();
        }
        return instance;
    }
    
    /**
     * Gets the cache instance for backward compatibility.
     * 
     * @return The singleton unified cache instance
     */
    public static Rs2Cache<Skill, SkillData> getCache() {
        return getInstance();
    }
    
    /**
     * Loads skill data from the client for a specific skill.
     * 
     * @param skill The skill to load data for
     * @return The SkillData containing level, boosted level, and experience
     */
    private static SkillData loadSkillDataFromClient(Skill skill) {
        try {
            if (Microbot.getClient() == null) {
                log.warn("Client is null when loading skill data for {}", skill);
                return new SkillData(1, 1, 0);
            }
            final int[] skillValues = new int[3]; // [level, boostedLevel, experience]
            boolean loadedSuccessfully = Microbot.getClientThread().runOnClientThreadOptional( () -> {
                skillValues[0] = Microbot.getClient().getRealSkillLevel(skill);
                skillValues[1] = Microbot.getClient().getBoostedSkillLevel(skill);
                skillValues[2] = Microbot.getClient().getSkillExperience(skill);
                if (skillValues[0] < 0 || skillValues[1] < 0 || skillValues[2] < 0) {
                    log.warn("Invalid skill data for {}: level={}, boosted={}, exp={}", skill, skillValues[0], skillValues[1], skillValues[2]);
                    return false; // Skip if invalid
                }
                return true; // Ensure this runs on the client thread
             }).orElse(false);
                                            
            if (!loadedSuccessfully) {
                log.warn("Failed to load skill data for {}, using default values", skill);
                return new SkillData(1, 1, 0);
            }
            
            log.trace("Loaded skill data from client: {} (level: {}, boosted: {}, exp: {})", 
                     skill, skillValues[0], skillValues[1], skillValues[2]);
            return new SkillData(skillValues[0], skillValues[1], skillValues[2]);
        } catch (Exception e) {
            log.error("Error loading skill data for {}: {}", skill, e.getMessage(), e);
            return new SkillData(1, 1, 0);
        }
    }
    
    /**
     * Gets skill data from the cache or loads it from the client.
     * 
     * @param skill The skill to retrieve data for
     * @return The SkillData containing level, boosted level, and experience
     */
    public static SkillData getSkillData(Skill skill) {
        return getInstance().get(skill, () -> loadSkillDataFromClient(skill));
    }
    
    /**
     * Gets skill data from the cache or loads it with a custom supplier.
     * 
     * @param skill The skill to retrieve data for
     * @param valueLoader Custom supplier for loading the skill data
     * @return The SkillData
     */
    public static SkillData getSkillData(Skill skill, Supplier<SkillData> valueLoader) {
        return getInstance().get(skill, valueLoader);
    }
    
    /**
     * Gets the real (unboosted) level for a skill from the cache.
     * 
     * @param skill The skill to get the level for
     * @return The real skill level
     */
    public static int getRealSkillLevel(Skill skill) {
        return getSkillData(skill).getLevel();
    }
    
    /**
     * Gets the boosted level for a skill from the cache.
     * 
     * @param skill The skill to get the boosted level for
     * @return The boosted skill level
     */
    public static int getBoostedSkillLevel(Skill skill) {
        return getSkillData(skill).getBoostedLevel();
    }
    
    /**
     * Gets the experience for a skill from the cache.
     * 
     * @param skill The skill to get the experience for
     * @return The skill experience
     */
    public static int getSkillExperience(Skill skill) {
        return getSkillData(skill).getExperience();
    }
    
    /**
     * Manually updates skill data in the cache.
     * 
     * @param skill The skill to update
     * @param skillData The new skill data
     */
    public static void updateSkillData(Skill skill, SkillData skillData) {
        getInstance().put(skill, skillData);
    }
    
    /**
     * Manually updates skill data in the cache.
     * 
     * @param skill The skill to update
     * @param level The real skill level
     * @param boostedLevel The boosted skill level
     * @param experience The skill experience
     */
    public static void updateSkillData(Skill skill, int level, int boostedLevel, int experience) {
        updateSkillData(skill, new SkillData(level, boostedLevel, experience));
    }
    
    /**
     * Updates all cached skills by retrieving fresh data from the game client.
     * Implements the abstract method from Rs2Cache.
     * 
     * Iterates over all currently cached skill keys and refreshes their data from the client.
     */
    @Override
    public void update() {
        log.debug("Updating all cached skills from client...");
        
        if (Microbot.getClient() == null) {
            log.warn("Cannot update skills - client is null");
            return;
        }
        
        int beforeSize = size();
        int updatedCount = 0;
        
        // Get all currently cached skill keys and update them
        java.util.Set<Skill> cachedSkills = entryStream()
            .map(java.util.Map.Entry::getKey)
            .collect(java.util.stream.Collectors.toSet());
        
        for (Skill skill : cachedSkills) {
            try {
                // Refresh the skill data from client using the private method
                SkillData freshData = loadSkillDataFromClient(skill);
                if (freshData != null) {
                    put(skill, freshData);
                    updatedCount++;
                    log.debug("Updated skill {} with fresh data: level={}, boosted={}, xp={}", 
                            skill, freshData.getLevel(), freshData.getBoostedLevel(), freshData.getExperience());
                }
            } catch (Exception e) {
                log.warn("Failed to update skill {}: {}", skill, e.getMessage());
            }
        }
        
        log.debug("Updated {} skills from client (cache had {} entries total)", 
                updatedCount, beforeSize);
    }
    
    // ============================================
    // Print Functions for Cache Information
    // ============================================
    
    /**
     * Returns a detailed formatted string containing all skill cache information.
     * Includes complete skill data with temporal tracking and change information.
     * 
     * @return Detailed multi-line string representation of all cached skills
     */
    public static String printDetailedSkillInfo() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault());
        
        sb.append("=".repeat(80)).append("\n");
        sb.append("                     DETAILED SKILL CACHE INFORMATION\n");
        sb.append("=".repeat(80)).append("\n");
        
        // Cache metadata
        Rs2Cache<Skill, SkillData> cache = getInstance();
        sb.append(String.format("Cache Name: %s\n", cache.getCacheName()));
        sb.append(String.format("Cache Mode: %s\n", cache.getCacheMode()));
        sb.append(String.format("Total Cached Skills: %d\n", cache.size()));
        
        // Cache statistics
        var stats = cache.getStatistics();
        sb.append(String.format("Cache Hits: %d\n", stats.cacheHits));
        sb.append(String.format("Cache Misses: %d\n", stats.cacheMisses));
        sb.append(String.format("Hit Ratio: %.2f%%\n", stats.getHitRate() * 100));
        sb.append(String.format("Total Invalidations: %d\n", stats.totalInvalidations));
        sb.append(String.format("Uptime: %d ms\n", stats.uptime));
        sb.append(String.format("TTL: %d ms\n", stats.ttlMillis));
        sb.append("\n");
        
        sb.append("-".repeat(80)).append("\n");
        sb.append("                            SKILL DETAILS\n");
        sb.append("-".repeat(80)).append("\n");
        
        // Headers
        sb.append(String.format("%-15s %-6s %-8s %-12s %-12s %-10s %-19s\n", 
                "SKILL", "LEVEL", "BOOSTED", "EXPERIENCE", "EXP GAINED", "LEVEL UP", "LAST UPDATED"));
        sb.append("-".repeat(80)).append("\n");
        
        // Iterate through all skills
        for (Skill skill : Skill.values()) {
            SkillData data = cache.get(skill);
            if (data != null) {
                String lastUpdated = formatter.format(Instant.ofEpochMilli(data.getLastUpdated()));
                String expGained = data.getExperienceGained() > 0 ? 
                        String.format("+%d", data.getExperienceGained()) : "-";
                String levelUp = data.isLevelUp() ? "YES" : "-";
                
                sb.append(String.format("%-15s %-6d %-8d %-12d %-12s %-10s %-19s\n",
                        skill.name(),
                        data.getLevel(),
                        data.getBoostedLevel(),
                        data.getExperience(),
                        expGained,
                        levelUp,
                        lastUpdated));
                
                // Additional details for skills with changes
                if (data.getPreviousLevel() != null || data.getPreviousExperience() != null) {
                    sb.append(String.format("  └─ Previous: Level %s, Experience %s\n",
                            data.getPreviousLevel() != null ? data.getPreviousLevel() : "Unknown",
                            data.getPreviousExperience() != null ? data.getPreviousExperience() : "Unknown"));
                }
            }
        }
        
        sb.append("-".repeat(80)).append("\n");
        sb.append(String.format("Generated at: %s\n", formatter.format(Instant.now())));
        sb.append("=".repeat(80));
        
        return sb.toString();
    }
    
    /**
     * Returns a summary formatted string containing essential skill cache information.
     * Compact view showing key metrics and recent changes.
     * 
     * @return Summary multi-line string representation of skill cache
     */
    public static String printSkillSummary() {
        StringBuilder sb = new StringBuilder();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        
        sb.append("┌─ SKILL CACHE SUMMARY ").append("─".repeat(45)).append("┐\n");
        
        Rs2Cache<Skill, SkillData> cache = getInstance();
        var stats = cache.getStatistics();
        
        // Summary statistics
        sb.append(String.format("│ Skills Cached: %-3d │ Hits: %-6d │ Hit Rate: %5.1f%% │\n",
                cache.size(), stats.cacheHits, stats.getHitRate() * 100));
        
        // Combat level calculation
        int attack = getRealSkillLevel(Skill.ATTACK);
        int strength = getRealSkillLevel(Skill.STRENGTH);
        int defence = getRealSkillLevel(Skill.DEFENCE);
        int hitpoints = getRealSkillLevel(Skill.HITPOINTS);
        int prayer = getRealSkillLevel(Skill.PRAYER);
        int ranged = getRealSkillLevel(Skill.RANGED);
        int magic = getRealSkillLevel(Skill.MAGIC);
        
        double combatLevel = (defence + hitpoints + Math.floor(prayer / 2)) * 0.25 +
                Math.max(attack + strength, Math.max(ranged * 1.5, magic * 1.5)) * 0.325;
        
        // Calculate total level
        int totalLevel = 0;
        for (Skill skill : Skill.values()) {
            totalLevel += getRealSkillLevel(skill);
        }
        
        sb.append(String.format("│ Combat Level: %-6.1f │ Total Level: %-8d │\n",
                combatLevel, totalLevel));
        
        sb.append("├─ RECENT CHANGES ").append("─".repeat(46)).append("┤\n");
        
        // Show skills with recent changes (level ups or significant exp gains)
        boolean hasChanges = false;
        for (Skill skill : Skill.values()) {
            SkillData data = cache.get(skill);
            if (data != null && (data.isLevelUp() || data.getExperienceGained() > 0)) {
                hasChanges = true;
                String timeStr = formatter.format(Instant.ofEpochMilli(data.getLastUpdated()));
                if (data.isLevelUp()) {
                    sb.append(String.format("│ %-12s LEVEL UP! %d → %d (%s) %-14s │\n",
                            skill.name(), data.getPreviousLevel(), data.getLevel(), timeStr, ""));
                } else if (data.getExperienceGained() > 1000) {
                    sb.append(String.format("│ %-12s +%-8d exp (%s) %-20s │\n",
                            skill.name(), data.getExperienceGained(), timeStr, ""));
                }
            }
        }
        
        if (!hasChanges) {
            sb.append("│ No recent skill changes detected ").append(" ".repeat(27)).append("│\n");
        }
        
        sb.append("└").append("─".repeat(63)).append("┘");
        
        return sb.toString();
    }
    
    // ============================================
    // Legacy API Compatibility Methods
    // ============================================
    
    /**
     * Checks if skill meets level requirement - Legacy compatibility method.
     * 
     * @param skill The skill to check
     * @param levelRequired The required level
     * @param boosted Whether to use boosted level (true) or real level (false)
     * @return true if the requirement is met
     */
    public static boolean hasSkillRequirement(Skill skill, int levelRequired, boolean boosted) {
        int currentLevel = boosted ? getBoostedSkillLevel(skill) : getRealSkillLevel(skill);
        return currentLevel >= levelRequired;
    }
    
    /**
     * Invalidates all skill cache entries.
     */
    public static void invalidateAllSkills() {
        getInstance().invalidateAll();
        log.debug("Invalidated all skill cache entries");
    }
    
    /**
     * Invalidates a specific skill cache entry.
     * 
     * @param skill The skill to invalidate
     */
    public static void invalidateSkill(Skill skill) {
        getInstance().remove(skill);
        log.debug("Invalidated skill cache entry: {}", skill);
    }
    
    /**
     * Event handler registration for the unified cache.
     * The unified cache handles events through its strategy automatically.
     */
 
    @Subscribe
    public void onStatChanged(StatChanged event) {
        try {
            getInstance().handleEvent(event);
        } catch (Exception e) {
            log.error("Error handling StatChanged event: {}", e.getMessage(), e);
        }
    }
    
    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        try {
            switch (event.getGameState()) {
                case LOGGED_IN:
                case HOPPING:
                case LOGIN_SCREEN:
                case CONNECTION_LOST:
                    // Let the strategy handle cache invalidation
                    break;
                default:
                    break;
            }
        } catch (Exception e) {
            log.error("Error handling GameStateChanged event: {}", e.getMessage(), e);
        }
    }

    /**
     * Resets the singleton instance. Used for testing.
     */
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
    
    // ============================================
    // CacheSerializable Implementation
    // ============================================
    
    @Override
    public String getConfigKey() {
        return "skills";
    }
    
    @Override
    public String getConfigGroup() {
        return "microbot";
    }
    
    @Override
    public boolean shouldPersist() {
        return true; // Skills should always be persisted for progress tracking
    }
}
