package net.runelite.client.plugins.microbot.pluginscheduler.condition.skill;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

/**
 * Skill level-based condition for script execution.
 */
@Getter 
@EqualsAndHashCode(callSuper = true)
public class SkillLevelCondition extends SkillCondition {
    /**
     * Version of the condition class.
     * Used for serialization and deserialization.
     */
    public static String getVersion() {
        return "0.0.1";
    }
    private transient int currentTargetLevel;
    @Getter
    private final int targetLevelMin;
    @Getter
    private final int targetLevelMax;
    private transient int startLevel;
    private transient int[] startLevelsBySkill; // Used for total level tracking
    @Getter
    private final boolean randomized;
    @Getter
    private final boolean relative; // Whether this is a relative or absolute level target

    /**
     * Creates an absolute level condition (must reach a specific level)
     */
    public SkillLevelCondition(Skill skill, int targetLevel) {
        super(skill); // Call parent constructor with skill
        this.currentTargetLevel = targetLevel;
        this.targetLevelMin = targetLevel;
        this.targetLevelMax = targetLevel;
        this.randomized = false;
        this.relative = false; // Absolute level target
        initializeLevelTracking();
    }
    
    /**
     * Creates a randomized absolute level condition (must reach a level within a range)
     */
    public SkillLevelCondition(Skill skill, int targetMinLevel, int targetMaxLevel) {
        super(skill); // Call parent constructor with skill
        targetMinLevel = Math.max(1, targetMinLevel);
        targetMaxLevel = Math.min(99, targetMaxLevel);
        this.currentTargetLevel = Rs2Random.between(targetMinLevel, targetMaxLevel);
        this.targetLevelMin = targetMinLevel;
        this.targetLevelMax = targetMaxLevel;
        this.randomized = true;
        this.relative = false; // Absolute level target
        initializeLevelTracking();
    }

    /**
     * Creates a relative level condition (must gain specific levels from current)
     */
    public SkillLevelCondition(Skill skill, int targetLevel, boolean relative) {
        super(skill); // Call parent constructor with skill
        this.currentTargetLevel = targetLevel;
        this.targetLevelMin = targetLevel;
        this.targetLevelMax = targetLevel;
        this.randomized = false;
        this.relative = relative;
        initializeLevelTracking();
    }
    
    /**
     * Creates a randomized relative level condition (must gain a random number of levels from current)
     */
    public SkillLevelCondition(Skill skill, int targetMinLevel, int targetMaxLevel, boolean relative) {
        super(skill); // Call parent constructor with skill
        targetMinLevel = Math.max(1, targetMinLevel);
        targetMaxLevel = Math.min(99, targetMaxLevel);
        this.currentTargetLevel = Rs2Random.between(targetMinLevel, targetMaxLevel);
        this.targetLevelMin = targetMinLevel;
        this.targetLevelMax = targetMaxLevel;
        this.randomized = true;
        this.relative = relative;
        initializeLevelTracking();
    }

    /**
     * Initialize level tracking for individual skill or all skills if total
     */
    private void initializeLevelTracking() {
        if (isTotal()) {
            Skill[] skills = getAllTrackableSkills();
            startLevelsBySkill = new int[skills.length];
            startLevel = getTotalLevel();
        } else {
            startLevel = getCurrentLevel();
        }
    }

    @Override
    public void reset(boolean randomize) {
        if (randomize) {
            currentTargetLevel = Rs2Random.between(targetLevelMin, targetLevelMax);
        }
        initializeLevelTracking();
    }

    /**
     * Create an absolute skill level condition with random target between min and max
     */
    public static SkillLevelCondition createRandomized(Skill skill, int minLevel, int maxLevel) {
        if (minLevel == maxLevel) {
            return new SkillLevelCondition(skill, minLevel);
        }
        
        return new SkillLevelCondition(skill, minLevel, maxLevel);
    }

    /**
     * Create a relative skill level condition (gain levels from current)
     */
    public static SkillLevelCondition createRelative(Skill skill, int targetLevel) {
        return new SkillLevelCondition(skill, targetLevel, true);
    }

    /**
     * Create a relative skill level condition with random target between min and max
     */
    public static SkillLevelCondition createRelativeRandomized(Skill skill, int minLevel, int maxLevel) {
        if (minLevel == maxLevel) {
            return new SkillLevelCondition(skill, minLevel, true);
        }
        
        return new SkillLevelCondition(skill, minLevel, maxLevel, true);
    }
    
    @Override
    public boolean isSatisfied() {
        if (relative) {
            // For relative mode, check if we've gained the target number of levels
            return getLevelsGained() >= currentTargetLevel;
        } else {
            // For absolute mode, check if our current level is at or above the target
            return getCurrentLevel() >= currentTargetLevel;
        }
    }
    
    /**
     * Gets the number of levels gained since condition was created
     */
    public int getLevelsGained() {
        return getCurrentLevel() - startLevel;
    }
    
    /**
     * Gets the number of levels remaining to reach target
     */
    public int getLevelsRemaining() {
        if (relative) {
            return Math.max(0, currentTargetLevel - getLevelsGained());
        } else {
            return Math.max(0, currentTargetLevel - getCurrentLevel());
        }
    }
    
    /**
     * Gets the current skill level or total level if this is a total skill condition
     * Uses the SkillCondition's cached data to avoid client thread calls
     */
    public int getCurrentLevel() {
        // Use static cached data from SkillCondition class
        if (isTotal()) {
            return SkillCondition.getTotalLevel();
        }
        return SkillCondition.getSkillLevel(skill);
    }
    
    /**
     * Gets the starting skill level
     */
    public int getStartingLevel() {
        return startLevel;
    }

    /**
     * Gets the target skill level to reach (for absolute mode),
     * or the target level gain (for relative mode)
     */
    public int getCurrentTargetLevel() {
        return currentTargetLevel;
    }
    
    @Override
    public String getDescription() {
        String skillName = isTotal() ? "Total" : skill.getName();
        String randomRangeInfo = "";
        
        if (targetLevelMin != targetLevelMax) {
            randomRangeInfo = String.format(" (randomized from %d-%d)", targetLevelMin, targetLevelMax);
        }

        if (relative) {
            int levelsGained = getLevelsGained();
            
            return String.format("Gain %d %s levels%s (gained: %d - %.1f%%)", 
                    currentTargetLevel, 
                    skillName, 
                    randomRangeInfo,
                    levelsGained,
                    getProgressPercentage());
        } else {
            int currentLevel = getCurrentLevel();
            int levelsNeeded = Math.max(0, currentTargetLevel - currentLevel);
            
            if (levelsNeeded <= 0) {
                return String.format("%s level %d or higher%s (currently %d, goal reached)", 
                        skillName, currentTargetLevel, randomRangeInfo, currentLevel);
            } else {
                return String.format("%s level %d or higher%s (currently %d, need %d more)", 
                        skillName, currentTargetLevel, randomRangeInfo, currentLevel, levelsNeeded);
            }
        }
    }
    
    /**
     * Returns a detailed description of the level condition with additional status information
     */
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        String skillName = isTotal() ? "Total" : skill.getName();
        
        // Basic description
        if (relative) {
            sb.append("Skill Level Condition: Gain ").append(currentTargetLevel)
              .append(" ").append(skillName).append(" levels from starting level\n");
        } else {
            sb.append("Skill Level Condition: Reach ").append(currentTargetLevel)
              .append(" ").append(skillName).append(" level\n");
        }
        
        // Randomization info if applicable
        if (targetLevelMin != targetLevelMax) {
            sb.append("Target Range: ").append(targetLevelMin)
              .append("-").append(targetLevelMax).append(" (randomized)\n");
        }
        
        // Status information
        int currentLevel = getCurrentLevel();
        boolean satisfied = isSatisfied();
        sb.append("Status: ").append(satisfied ? "Satisfied" : "Not satisfied").append("\n");
        
        // Progress information
        int levelsGained = getLevelsGained();
        sb.append("Starting Level: ").append(startLevel).append("\n");
        sb.append("Current Level: ").append(currentLevel).append("\n");
        sb.append("Levels Gained: ").append(levelsGained).append("\n");
        
        if (!satisfied) {
            sb.append("Levels Remaining: ").append(getLevelsRemaining()).append("\n");
        }
        
        sb.append("Progress: ").append(String.format("%.1f%%", getProgressPercentage()));
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String skillName = isTotal() ? "Total" : skill.getName();
        
        // Basic information
        sb.append("SkillLevelCondition:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        sb.append("  │ Skill: ").append(skillName).append("\n");
        
        if (relative) {
            sb.append("  │ Mode: Relative (gain from current)\n");
            sb.append("  │ Target Level gain: ").append(currentTargetLevel).append("\n");
        } else {
            sb.append("  │ Mode: Absolute (reach total)\n");
            sb.append("  │ Target Level: ").append(currentTargetLevel).append("\n");
        }
        
        // Randomization
        boolean hasRandomization = targetLevelMin != targetLevelMax;
        if (hasRandomization) {
            sb.append("  │ Randomization: Enabled\n");
            sb.append("  │ Target Range: ").append(targetLevelMin).append("-").append(targetLevelMax).append("\n");
        }
        
        // Status information
        sb.append("  ├─ Status ──────────────────────────────────\n");
        int currentLevel = getCurrentLevel();
        boolean satisfied = isSatisfied();
        sb.append("  │ Satisfied: ").append(satisfied).append("\n");
        
        if (relative) {
            sb.append("  │ Levels Gained: ").append(getLevelsGained()).append("\n");
            
            if (!satisfied) {
                sb.append("  │ Levels Remaining: ").append(getLevelsRemaining()).append("\n");
            }
        } else {
            if (currentLevel >= currentTargetLevel) {
                sb.append("  │ Current Level: ").append(currentLevel).append(" (goal reached)\n");
            } else {
                sb.append("  │ Current Level: ").append(currentLevel).append("\n");
                sb.append("  │ Levels Remaining: ").append(getLevelsRemaining()).append("\n");
            }
        }
        
        sb.append("  │ Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
        
        // Current state
        sb.append("  └─ Current State ──────────────────────────\n");
        sb.append("    Starting Level: ").append(startLevel).append("\n");
        sb.append("    Current Level: ").append(currentLevel);
        
        return sb.toString();
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.SKILL;
    }

    @Override
    public double getProgressPercentage() {
        if (relative) {
            int levelsGained = getLevelsGained();
            
            if (levelsGained >= currentTargetLevel) {
                return 100.0;
            }
            
            if (currentTargetLevel <= 0) {
                return 100.0;
            }
            
            return (100.0 * levelsGained) / currentTargetLevel;
        } else {
            int currentLevel = getCurrentLevel();
            int startingLevel = getStartingLevel();
            int targetLevel = getCurrentTargetLevel();
            if (currentLevel==0 || startingLevel == 0 || targetLevel ==0){
                return 0.0;
            }
            if (currentLevel >= targetLevel) {
                return 100.0;
            }
            
            if (currentLevel == startingLevel) {
                // If we haven't gained any levels yet, estimate progress using XP
                // This provides better feedback for the user
                Skill skill = getSkill();
                if (skill != null) {
                    // Use cached XP data
                    long currentXp = SkillCondition.getSkillXp(skill);
                    
                    long levelStartXp = net.runelite.api.Experience.getXpForLevel(currentLevel);
                    long nextLevelXp = net.runelite.api.Experience.getXpForLevel(currentLevel + 1);
                    long xpInLevel = currentXp - levelStartXp;
                    long xpNeededForNextLevel = nextLevelXp - levelStartXp;
                    
                    // Progress within the current level (0-100%)
                    double levelProgress = (100.0 * xpInLevel) / xpNeededForNextLevel;
                    
                    // Total levels needed and percentage of one level
                    int levelsNeeded = targetLevel - currentLevel;
                    double oneLevel = 100.0 / levelsNeeded;
                    
                    // Return progress for partially completed level
                    return (levelProgress * oneLevel) / 100.0;
                }
            }
            
            int levelsGained = currentLevel - startingLevel;
            int levelsNeeded = targetLevel - startingLevel;
            
            if (levelsNeeded <= 0) {
                return 100.0;
            }
            
            return (100.0 * levelsGained) / levelsNeeded;
        }
    }
}