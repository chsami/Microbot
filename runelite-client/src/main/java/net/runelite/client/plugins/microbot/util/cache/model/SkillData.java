package net.runelite.client.plugins.microbot.util.cache.model;

import lombok.Data;

/**
 * Data structure to hold skill information with temporal tracking.
 */
@Data
public class SkillData {
    
    private final int level;
    private final int boostedLevel;
    private final int experience;
    private final long lastUpdated; // Timestamp when this skill data was last updated
    private final Integer previousLevel; // Previous level before the update (null if unknown)
    private final Integer previousExperience; // Previous experience before the update (null if unknown)
    
    /**
     * Creates a new SkillData instance with current timestamp.
     * 
     * @param level The real (unboosted) skill level
     * @param boostedLevel The current boosted skill level  
     * @param experience The skill experience
     */
    public SkillData(int level, int boostedLevel, int experience) {
        this(level, boostedLevel, experience, System.currentTimeMillis(), null, null);
    }
    
    /**
     * Creates a new SkillData instance with previous values for change tracking.
     * 
     * @param level The real (unboosted) skill level
     * @param boostedLevel The current boosted skill level  
     * @param experience The skill experience
     * @param previousLevel The previous level (null if unknown)
     * @param previousExperience The previous experience (null if unknown)
     */
    public SkillData(int level, int boostedLevel, int experience, Integer previousLevel, Integer previousExperience) {
        this(level, boostedLevel, experience, System.currentTimeMillis(), previousLevel, previousExperience);
    }
    
    /**
     * Creates a new SkillData instance with full temporal tracking.
     * 
     * @param level The real (unboosted) skill level
     * @param boostedLevel The current boosted skill level  
     * @param experience The skill experience
     * @param lastUpdated Timestamp when this data was created/updated
     * @param previousLevel The previous level (null if unknown)
     * @param previousExperience The previous experience (null if unknown)
     */
    public SkillData(int level, int boostedLevel, int experience, long lastUpdated, Integer previousLevel, Integer previousExperience) {
        this.level = level;
        this.boostedLevel = boostedLevel;
        this.experience = experience;
        this.lastUpdated = lastUpdated;
        this.previousLevel = previousLevel;
        this.previousExperience = previousExperience;
    }
    
    /**
     * Creates a new SkillData with updated values while preserving previous state.
     * 
     * @param newLevel The new skill level
     * @param newBoostedLevel The new boosted level
     * @param newExperience The new experience
     * @return A new SkillData instance with the current values as previous values
     */
    public SkillData withUpdate(int newLevel, int newBoostedLevel, int newExperience) {
        return new SkillData(newLevel, newBoostedLevel, newExperience, this.level, this.experience);
    }
    
    /**
     * Checks if this skill data represents a level increase.
     * 
     * @return true if the level increased from the previous value
     */
    public boolean isLevelUp() {
        return previousLevel != null && level > previousLevel;
    }
    
    /**
     * Gets the experience gained since the last update.
     * 
     * @return the experience difference, or 0 if no previous experience
     */
    public int getExperienceGained() {
        return previousExperience != null ? experience - previousExperience : 0;
    }
}
