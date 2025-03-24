package net.runelite.client.plugins.microbot.pluginscheduler.type.condition;

import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * Skill XP-based condition for script execution.
 */
@Getter 
public class SkillXpCondition implements Condition {
    private final Skill skill;
    private final int targetXp;
    private final int startXp;
    
    public SkillXpCondition(Skill skill, int targetXp) {
        this.skill = skill;
        this.targetXp = targetXp;
        this.startXp = Microbot.getClient().getSkillExperience(skill);
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
     * Gets progress percentage towards target
     */
    public double getProgressPercentage() {
        if (targetXp <= 0) return 100.0;
        return Math.min(100.0, (getXpGained() / (double) targetXp) * 100);
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