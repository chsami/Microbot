package net.runelite.client.plugins.microbot.pluginscheduler.condition.skill;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.util.ImageUtil;

import javax.swing.*;
import java.awt.image.BufferedImage;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Abstract base class for skill-based conditions.
 */
@Getter 
@EqualsAndHashCode(callSuper = false)
@Slf4j
public abstract class SkillCondition implements Condition {
    // Static icon cache to prevent repeated loading of the same icons
    private static final ConcurrentHashMap<Skill, Icon> ICON_CACHE = new ConcurrentHashMap<>();
    private static Icon OVERALL_ICON = null;
    
    // Static skill data caching for performance improvements
    private static final ConcurrentHashMap<Skill, Integer> SKILL_LEVELS = new ConcurrentHashMap<>();
    private static final ConcurrentHashMap<Skill, Long> SKILL_XP = new ConcurrentHashMap<>();
    private static transient int TOTAL_LEVEL = 0;
    private static transient long TOTAL_XP = 0;
    private static transient boolean SKILL_DATA_INITIALIZED = false;
    private static transient long LAST_UPDATE_TIME = 0;
    private static final long UPDATE_THROTTLE_MS = 600; // Update at most once every 600ms
    
    private static final int ICON_SIZE = 24; // Standard size for all skill icons
    protected final Skill skill;
    
    /**
     * Constructor requiring a skill to be set
     */
    protected SkillCondition(Skill skill) {
        this.skill = skill;
        
        // Initialize skill data if needed
        if (!SKILL_DATA_INITIALIZED) {
            initializeSkillData();
        }
    }
    
    /**
     * Gets the skill associated with this condition
     */
    public Skill getSkill() {
        return skill;
    }
    
    /**
     * Checks if this condition is for the total of all skills
     */
    public boolean isTotal() {
        return skill == null || skill == Skill.OVERALL;
    }
    
    /**
     * Gets all skills to be considered for total calculations
     * Excludes TOTAL itself and other non-tracked skills
     */
    protected Skill[] getAllTrackableSkills() {
        return Skill.values();
    }
    
    /**
     * Gets a properly scaled icon for the skill (24x24 pixels)
     * Uses a cache to avoid repeatedly loading the same icons
     */
    public Icon getSkillIcon() {
        try {
            // First check if we have a cached icon
            if (isTotal()) {
                if (OVERALL_ICON != null) {
                    return OVERALL_ICON;
                }
            } else if (skill != null && ICON_CACHE.containsKey(skill)) {
                return ICON_CACHE.get(skill);
            }
            
            // If not in cache, create the icon and cache it
            Icon icon = createSkillIcon();
            
            // Store in the appropriate cache
            if (isTotal()) {
                OVERALL_ICON = icon;
            } else if (skill != null) {
                ICON_CACHE.put(skill, icon);
            }
            
            return icon;
        } catch (Exception e) {
            // Fall back to generic skill icon
            return null;
        }
    }
    
    /**
     * Creates a skill icon - now separated from getSkillIcon to support caching
     */
    private Icon createSkillIcon() {
        try {
            // This only needs to be done once per skill, not on every UI render
            SkillIconManager iconManager = Microbot.getClientThread().runOnClientThreadOptional(
                () -> Microbot.getInjector().getInstance(SkillIconManager.class)).orElse(null);
            
            if (iconManager != null) {
                // Get the skill image (small=true for smaller version)
                BufferedImage skillImage;
                String skillName = isTotal() ? "overall" : skill.getName().toLowerCase();
                if (isTotal()) {                    
                    String skillIconPath = "/skill_icons/" + skillName + ".png";
                    skillImage = ImageUtil.loadImageResource(getClass(), skillIconPath);                    
                } else {
                    skillImage = iconManager.getSkillImage(skill, true);
                }
                
                // Scale the image if needed
                if (skillImage.getWidth() != ICON_SIZE || skillImage.getHeight() != ICON_SIZE) {
                    skillImage = ImageUtil.resizeImage(skillImage, ICON_SIZE, ICON_SIZE);
                }
                
                return new ImageIcon(skillImage);
            }
        } catch (Exception e) {
            // Silently fail and return null
        }
        return null;
    }
    
    /**
     * Reset condition - must be implemented by subclasses
     */
    @Override
    public void reset() {
        reset(false);
    }
    
    /**
     * Reset condition with option to randomize targets
     */
    public abstract void reset(boolean randomize);
    
    /**
     * Initializes skill data tracking for better performance
     */
    private static void initializeSkillData() {    
        if (!Microbot.isLoggedIn()){
            SKILL_DATA_INITIALIZED = false;
            return;
        }
        if (SKILL_DATA_INITIALIZED) {
            return;
        }
        log.info("Initializing skill data");
        Microbot.getClientThread().invoke(() -> {
            try {
                // Initialize skill level and XP caches
                for (Skill skill : Skill.values()) {
                    SKILL_LEVELS.put(skill, Microbot.getClient().getRealSkillLevel(skill));
                    SKILL_XP.put(skill, (long) Microbot.getClient().getSkillExperience(skill));
                }
                TOTAL_LEVEL = Microbot.getClient().getTotalLevel();
                TOTAL_XP = Microbot.getClient().getOverallExperience();
                SKILL_DATA_INITIALIZED = true;
                LAST_UPDATE_TIME = System.currentTimeMillis();
            } catch (Exception e) {
                // Ignore errors during initialization
            }            
        });
    }
    
    /**
     * Gets the current level for a skill from the cache
     */
    public static int getSkillLevel(Skill skill) {
        if (!SKILL_DATA_INITIALIZED) {
            initializeSkillData();
        }
        
        // If the skill is null or OVERALL, return the total level
        if (skill == null || skill == Skill.OVERALL) {
            return TOTAL_LEVEL;
        }
        
        return SKILL_LEVELS.getOrDefault(skill, 0);
    }
    
    /**
     * Gets the current XP for a skill from the cache
     */
    public static long getSkillXp(Skill skill) {
        if (!SKILL_DATA_INITIALIZED) {
            initializeSkillData();
        }
        
        // If the skill is null or OVERALL, return the total XP
        if (skill == null || skill == Skill.OVERALL) {
            return TOTAL_XP;
        }
        
        return SKILL_XP.getOrDefault(skill, 0L);
    }
    
    /**
     * Gets the current total level from the cache
     */
    public static int getTotalLevel() {
        if (!SKILL_DATA_INITIALIZED) {
            initializeSkillData();
        }
        return TOTAL_LEVEL;
    }
    
    /**
     * Gets the current total XP from the cache
     */
    public static long getTotalXp() {
        if (!SKILL_DATA_INITIALIZED) {
            initializeSkillData();
        }
        return TOTAL_XP;
    }
    
    /**
     * Forces an update of all skill data (throttled to prevent performance issues)
     */
    public static void forceUpdate() {
        // Only update once every UPDATE_THROTTLE_MS milliseconds
        long currentTime = System.currentTimeMillis();
        if (currentTime - LAST_UPDATE_TIME < UPDATE_THROTTLE_MS) {
            return;
        }
        SKILL_DATA_INITIALIZED = false;
        initializeSkillData();
       
    }
    
    /**
     * Updates skill data when stats change
     */
    @Override
    public void onStatChanged(StatChanged event) {
        if (!SKILL_DATA_INITIALIZED) {
            initializeSkillData();
            return;
        }
        
        Skill updatedSkill = event.getSkill();
        
        // Update throttling - only update once every UPDATE_THROTTLE_MS milliseconds
        long currentTime = System.currentTimeMillis();
        if (currentTime - LAST_UPDATE_TIME < UPDATE_THROTTLE_MS) {
            return;
        }
        
        // Update cached values
        Microbot.getClientThread().invokeLater(() -> {
            try {
                // Update the specific skill
                int newLevel = Microbot.getClient().getRealSkillLevel(updatedSkill);
                long newXp = Microbot.getClient().getSkillExperience(updatedSkill);
                
                SKILL_LEVELS.put(updatedSkill, newLevel);
                SKILL_XP.put(updatedSkill, newXp);
                
                // Update total level and XP
                TOTAL_LEVEL = Microbot.getClient().getTotalLevel();
                TOTAL_XP = Microbot.getClient().getOverallExperience();
                LAST_UPDATE_TIME = currentTime;
            } catch (Exception e) {
                // Ignore errors during update
            }
            
        });
    }
    @Override
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
            SKILL_DATA_INITIALIZED = false;
            initializeSkillData();
        }else{
            SKILL_DATA_INITIALIZED = false;
        }
    }
}