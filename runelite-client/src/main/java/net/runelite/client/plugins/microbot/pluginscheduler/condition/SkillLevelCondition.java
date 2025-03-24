package net.runelite.client.plugins.microbot.pluginscheduler.condition;

import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * Skill level-based condition for script execution.
 */
@Getter 
public class SkillLevelCondition implements Condition {
    private final Skill skill;
    private final int targetLevel;
    private final int startLevel;
    
    public SkillLevelCondition(Skill skill, int targetLevel) {
        this.skill = skill;
        this.targetLevel = targetLevel;
        this.startLevel = Microbot.getClient().getRealSkillLevel(skill);
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
        return Microbot.getClient().getRealSkillLevel(skill) >= targetLevel;
    }
    
    /**
     * Gets the number of levels gained since condition was created
     */
    public int getLevelsGained() {
        return Microbot.getClient().getRealSkillLevel(skill) - startLevel;
    }
    
    /**
     * Gets the number of levels remaining to reach target
     */
    public int getLevelsRemaining() {
        int currentLevel = Microbot.getClient().getRealSkillLevel(skill);
        return Math.max(0, targetLevel - currentLevel);
    }
    
    @Override
    public String getDescription() {
        return String.format("Reach %s level %d (Current: %d, Remaining: %d)", 
            skill.getName(), 
            targetLevel,
            Microbot.getClient().getRealSkillLevel(skill),
            getLevelsRemaining());
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.SKILL_LEVEL;
    }
}