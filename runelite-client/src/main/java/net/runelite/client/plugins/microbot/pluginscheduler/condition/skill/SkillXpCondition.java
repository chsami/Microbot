package net.runelite.client.plugins.microbot.pluginscheduler.condition.skill;

import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

/**
 * Skill XP-based condition for script execution.
 */
@Getter 
public class SkillXpCondition implements Condition {
    private final Skill skill;
    private int currentTargetXp;
    private final int targetXpMin;
    private final int targetXpMax;
    private int startXp;
    
    public SkillXpCondition(Skill skill, int targetXp) {
        this.skill = skill;
        this.currentTargetXp = targetXp;
        this.targetXpMin = targetXp;
        this.targetXpMax = targetXp;
        this.startXp = getCurrentXp();
    }
    public SkillXpCondition(Skill skill, int targetXpMin, int targetXpMax) {
        targetXpMin = Math.max(0, targetXpMin);
        targetXpMax = Math.min(Integer.MAX_VALUE, targetXpMax);
        
        this.skill = skill;
        this.currentTargetXp = Rs2Random.between(targetXpMin, targetXpMax);
        this.targetXpMin = targetXpMin;
        this.targetXpMax = targetXpMax;
        this.startXp = getCurrentXp();
    }
    
    
    public void reset(boolean randomize) {
        startXp = getCurrentXp();
        if (randomize) {
            currentTargetXp = Rs2Random.between(targetXpMin, targetXpMax);
        }
    }

    
    /**
     * Create a skill XP condition with random target between min and max
     */
    public static SkillXpCondition createRandomized(Skill skill, int minXp, int maxXp) {
        if (minXp == maxXp) {
            return new SkillXpCondition(skill, minXp);
        }
        
        
        return new SkillXpCondition(skill, minXp, maxXp);
    }
    
    @Override
    public boolean isSatisfied() {
        int currentXp = Microbot.getClient().getSkillExperience(skill);
        return (currentXp - startXp) >= currentTargetXp;
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
        return Math.max(0, currentTargetXp - getXpGained());
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
        int targetXp = currentTargetXp;
        
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
            currentTargetXp, 
            skill.getName(),
            getXpGained(),
            currentTargetXp,
            getProgressPercentage());
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.SKILL_XP;
    }
}