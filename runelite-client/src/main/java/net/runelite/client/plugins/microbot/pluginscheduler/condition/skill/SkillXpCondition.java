package net.runelite.client.plugins.microbot.pluginscheduler.condition.skill;

import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;

/**
 * Skill XP-based condition for script execution.
 */
@Getter 
public class SkillXpCondition implements Condition {
    private final Skill skill;
    private final int targetXp;
    private int startXp;
    
    public SkillXpCondition(Skill skill, int targetXp) {
        this.skill = skill;
        this.targetXp = targetXp;
        this.startXp = getCurrentXp();
    }
    public void reset() {
        startXp = getCurrentXp();
    }

    
    /**
     * Create a skill XP condition with random target between min and max
     */
    public static SkillXpCondition createRandomized(Skill skill, int minXp, int maxXp) {
        if (minXp == maxXp) {
            return new SkillXpCondition(skill, minXp);
        }
        
        int range = maxXp - minXp;
        int randomXp = minXp + (int)(Math.random() * (range + 1));
        return new SkillXpCondition(skill, randomXp);
    }
    
    @Override
    public boolean isMet() {
        int currentXp = Microbot.getClient().getSkillExperience(skill);
        return (currentXp - startXp) >= targetXp;
    }
    
    /**
     * Gets the amount of XP gained since condition was created
     */
    public int getXpGained() {
        int currentXp = Microbot.getClient().getSkillExperience(skill);
        return currentXp - startXp;
    }
    
    /**
     * Gets the amount of XP remaining to reach target
     */
    public int getXpRemaining() {
        return Math.max(0, targetXp - getXpGained());
    }
    
    /**
     * Gets the current XP
     */
    public int getCurrentXp() {
        return Microbot.getClientThread().runOnClientThread( ()->Microbot.getClient().getSkillExperience(skill));
    }
    /**
     * Gets the starting XP
     */
    public int getStartingXp() {
        return startXp;
    }
    /**
     * Gets progress percentage towards target
     */
    @Override
    public double getProgressPercentage() {
        int currentXp = getCurrentXp();
        int startingXp = getStartingXp();
        int targetXp = getTargetXp();
        
        if (currentXp >= targetXp) {
            return 100.0;
        }
        
        int xpGained = currentXp - startingXp;
        int xpNeeded = targetXp - startingXp;
        
        if (xpNeeded <= 0) {
            return 100.0;
        }
        
        return (100.0 * xpGained) / xpNeeded;
    }
    
    @Override
    public String getDescription() {
        return String.format("Gain %d %s XP (Current: %d/%d - %.1f%%)", 
            targetXp, 
            skill.getName(),
            getXpGained(),
            targetXp,
            getProgressPercentage());
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.SKILL_XP;
    }
}