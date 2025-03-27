package net.runelite.client.plugins.microbot.pluginscheduler.condition.skill;

import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;

/**
 * Skill level-based condition for script execution.
 */
@Getter 
public class SkillLevelCondition implements Condition {
    private final Skill skill;
    private final int targetLevel;
    private int startLevel;
    
    public SkillLevelCondition(Skill skill, int targetLevel) {
        this.skill = skill;
        this.targetLevel = targetLevel;
        this.startLevel = getCurrentLevel();
    }
    @Override
    public void reset() {
        startLevel = getCurrentLevel();
    }
    /**
     * Create a skill level condition with random target between min and max
     */
    public static SkillLevelCondition createRandomized(Skill skill, int minLevel, int maxLevel) {
        if (minLevel == maxLevel) {
            return new SkillLevelCondition(skill, minLevel);
        }
        
        int range = maxLevel - minLevel;
        int randomLevel = minLevel + (int)(Math.random() * (range + 1));
        return new SkillLevelCondition(skill, randomLevel);
    }
    
    @Override
    public boolean isMet() {
        return getCurrentLevel() >= targetLevel;
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
        return Math.max(0, targetLevel - currentLevel);
    }
    
    @Override
    public String getDescription() {
        return String.format("Reach %s level %d (Current: %d, Remaining: %d)", 
            skill.getName(), 
            targetLevel,
            getCurrentLevel(),
            getLevelsRemaining());
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
        int targetLevel = getTargetLevel();
        
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