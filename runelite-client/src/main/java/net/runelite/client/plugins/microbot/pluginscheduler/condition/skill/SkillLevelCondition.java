package net.runelite.client.plugins.microbot.pluginscheduler.condition.skill;

import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

/**
 * Skill level-based condition for script execution.
 */
@Getter 
public class SkillLevelCondition implements Condition {
    private final Skill skill;
    private int currentTargetLevel;
    private final int targetLevelMin;
    private final int targetLevelMax;
    private int startLevel;
    
    public SkillLevelCondition(Skill skill, int targetLevel) {
        this.skill = skill;
        this.currentTargetLevel = targetLevel;
        this.targetLevelMin = targetLevel;
        this.targetLevelMax = targetLevel;
        this.startLevel = getCurrentLevel();
    }
    public SkillLevelCondition(Skill skill, int targetMinLevel, int targetMaxLevel) {
        this.skill = skill;
        targetMinLevel = Math.max(1, targetMinLevel);
        targetMaxLevel = Math.min(99, targetMaxLevel);
        this.currentTargetLevel = Rs2Random.between(targetMinLevel, targetMaxLevel);
        this.targetLevelMin = targetMinLevel;
        this.targetLevelMax = targetMaxLevel;
        this.startLevel = getCurrentLevel();
    }
    @Override
    public void reset() {
        reset(false);
    }

    @Override
    public void reset(boolean randomize) {
        if (randomize) {
            currentTargetLevel = Rs2Random.between(targetLevelMin, targetLevelMax);
        }
        startLevel = getCurrentLevel();        

    }
    /**
     * Create a skill level condition with random target between min and max
     */
    public static SkillLevelCondition createRandomized(Skill skill, int minLevel, int maxLevel) {
        if (minLevel == maxLevel) {
            return new SkillLevelCondition(skill, minLevel);
        }
        
        return new SkillLevelCondition(skill, minLevel, maxLevel);
    }
    
    @Override
    public boolean isSatisfied() {
        return getCurrentLevel() >= currentTargetLevel;
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
        int currentLevel = getCurrentLevel();
        return Math.max(0, currentTargetLevel - currentLevel);
    }
    
    @Override
    public String getDescription() {
        int currentLevel = getCurrentLevel();
        int levelsNeeded = Math.max(0, currentTargetLevel - currentLevel);
        String randomRangeInfo = "";
        
        if (targetLevelMin != targetLevelMax) {
            randomRangeInfo = String.format(" (randomized from %d-%d)", targetLevelMin, targetLevelMax);
        }
        
        if (levelsNeeded <= 0) {
            return String.format("%s level %d or higher%s (currently %d, goal reached)", 
                    skill.getName(), currentTargetLevel, randomRangeInfo, currentLevel);
        } else {
            return String.format("%s level %d or higher%s (currently %d, need %d more)", 
                    skill.getName(), currentTargetLevel, randomRangeInfo, currentLevel, levelsNeeded);
        }
    }
    /**
     * Gets the current skill level
     */
    public int getCurrentLevel() {
        return  Microbot.getClientThread().runOnClientThread(()->Microbot.getClient().getRealSkillLevel(skill));
    }
    /**
     * Gets the starting skill level
     */
    public int getStartingLevel() {
        return startLevel;
    }
    @Override
    public ConditionType getType() {
        return ConditionType.SKILL_LEVEL;
    }

    @Override
    public double getProgressPercentage() {
        int currentLevel = getCurrentLevel();
        int startingLevel = getStartingLevel();
        int targetLevel = getCurrentTargetLevel();
        
        if (currentLevel >= targetLevel) {
            return 100.0;
        }
        
        int levelsGained = currentLevel - startingLevel;
        int levelsNeeded = targetLevel - startingLevel;
        
        if (levelsNeeded <= 0) {
            return 100.0;
        }
        
        return (100.0 * levelsGained) / levelsNeeded;
    }
    
}