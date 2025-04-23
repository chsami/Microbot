package net.runelite.client.plugins.microbot.pluginscheduler.condition.skill;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

/**
 * Skill XP-based condition for script execution.
 */
@Getter 
@EqualsAndHashCode(callSuper = true)
public class SkillXpCondition extends SkillCondition {
    private transient long currentTargetXp;
    private final long targetXpMin;
    private final long targetXpMax;
    private transient long startXp;
    private transient long[] startXpBySkill; // Used for total XP tracking
    private final boolean randomized;
    public SkillXpCondition(Skill skill, long targetXp) {
        super(skill); // Call parent constructor with skill
        this.currentTargetXp = targetXp;
        this.targetXpMin = targetXp;
        this.targetXpMax = targetXp;
        this.randomized = false;
        initializeXpTracking();
    }
    
    public SkillXpCondition(Skill skill, long targetXpMin, long targetXpMax) {
        super(skill); // Call parent constructor with skill
        targetXpMin = Math.max(0, targetXpMin);
        targetXpMax = Math.min(Long.MAX_VALUE, targetXpMax);
        
        this.currentTargetXp = Rs2Random.between((int)targetXpMin, (int)targetXpMax);
        this.targetXpMin = targetXpMin;
        this.targetXpMax = targetXpMax;
        this.randomized = true;
        initializeXpTracking();
    }
    
    /**
     * Initialize XP tracking for individual skill or all skills if total
     */
    private void initializeXpTracking() {
        if (isTotal()) {
            Skill[] skills = getAllTrackableSkills();
            startXpBySkill = new long[skills.length];
            long totalXp = 0;
            totalXp = Microbot.getClientThread().runOnClientThreadOptional(  () -> {
                return Microbot.getClient().getOverallExperience();                               
            }).orElse(0L);
            startXp = (long)totalXp;
        } else {
            startXp = getCurrentXp();
        }
    }
    
    @Override
    public void reset(boolean randomize) {
        if (randomize) {
            currentTargetXp = (long)Rs2Random.between((int)targetXpMin, (int)targetXpMax);
        }
        initializeXpTracking();
    }
    
    /**
     * Create a skill XP condition with random target between min and max
     */
    public static SkillXpCondition createRandomized(Skill skill, long minXp, long maxXp) {
        if (minXp == maxXp) {
            return new SkillXpCondition(skill, minXp);
        }
        
        return new SkillXpCondition(skill, minXp, maxXp);
    }
    
    @Override
    public boolean isSatisfied() {
        return getXpGained() >= currentTargetXp;
    }
    
    /**
     * Gets the amount of XP gained since condition was created
     */
    public long getXpGained() {
        if (isTotal()) {
            return getTotalXp() - startXp;
        } else {
            long currentXp = Microbot.getClient().getSkillExperience(skill);
            return currentXp - startXp;
        }
    }
    
    /**
     * Gets total XP across all skills
     */
    private long getTotalXp() {
        if (!isTotal()) {
            return getCurrentXp();
        }
        
        long total = 0;
        total = Microbot.getClientThread().runOnClientThreadOptional(  () -> {
            return Microbot.getClient().getOverallExperience();                               
        }).orElse(0L);
        return total;
    }
    
    /**
     * Gets the amount of XP remaining to reach target
     */
    public long getXpRemaining() {
        return Math.max(0, currentTargetXp - getXpGained());
    }
    
    /**
     * Gets the current XP
     */
    public long getCurrentXp() {
        if (isTotal()) {
            return getTotalXp();
        }
        return (long)Microbot.getClientThread().runOnClientThreadOptional(
            () -> Microbot.getClient().getSkillExperience(skill)).orElse(0);
    }
    
    /**
     * Gets the starting XP
     */
    public long getStartingXp() {
        return startXp;
    }
    
    /**
     * Gets progress percentage towards target
     */
    @Override
    public double getProgressPercentage() {
        long xpGained = getXpGained();
        long targetXp = currentTargetXp;
        
        if (xpGained >= targetXp) {
            return 100.0;
        }
        
        if (targetXp <= 0) {
            return 100.0;
        }
        
        return (100.0 * xpGained) / targetXp;
    }
    
    @Override
    public String getDescription() {
        String skillName = isTotal() ? "Total" : skill.getName();
        return String.format("Gain %d %s XP (Current: %d/%d - %.1f%%)", 
            currentTargetXp, 
            skillName,
            getXpGained(),
            currentTargetXp,
            getProgressPercentage());
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.SKILL_XP;
    }

    /**
     * Returns a detailed description of the XP condition with additional status information
     */
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        String skillName = isTotal() ? "Total" : skill.getName();
        
        // Basic description
        sb.append("Skill XP Condition: Gain ").append(currentTargetXp)
          .append(" ").append(skillName).append(" XP\n");
        
        // Randomization info if applicable
        if (targetXpMin != targetXpMax) {
            sb.append("Target Range: ").append(targetXpMin)
              .append("-").append(targetXpMax).append(" XP (randomized)\n");
        }
        
        // Status information
        boolean satisfied = isSatisfied();
        sb.append("Status: ").append(satisfied ? "Satisfied" : "Not satisfied").append("\n");
        
        // Progress information
        long xpGained = getXpGained();
        sb.append("XP Gained: ").append(xpGained).append("\n");
        sb.append("Starting XP: ").append(startXp).append("\n");
        sb.append("Current XP: ").append(getCurrentXp()).append("\n");
        
        if (!satisfied) {
            sb.append("XP Remaining: ").append(getXpRemaining()).append("\n");
        }
        
        sb.append("Progress: ").append(String.format("%.1f%%", getProgressPercentage()));
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String skillName = isTotal() ? "Total" : skill.getName();
        
        // Basic information
        sb.append("SkillXpCondition:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        sb.append("  │ Skill: ").append(skillName).append("\n");
        sb.append("  │ Target XP: ").append(currentTargetXp).append("\n");
        
        // Randomization
        boolean hasRandomization = targetXpMin != targetXpMax;
        if (hasRandomization) {
            sb.append("  │ Randomization: Enabled\n");
            sb.append("  │ Target Range: ").append(targetXpMin).append("-").append(targetXpMax).append(" XP\n");
        }
        
        // Status information
        sb.append("  ├─ Status ──────────────────────────────────\n");
        boolean satisfied = isSatisfied();
        sb.append("  │ Satisfied: ").append(satisfied).append("\n");
        sb.append("  │ XP Gained: ").append(getXpGained()).append("\n");
        
        if (!satisfied) {
            sb.append("  │ XP Remaining: ").append(getXpRemaining()).append("\n");
        }
        
        sb.append("  │ Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
        
        // Current state
        sb.append("  └─ Current State ──────────────────────────\n");
        sb.append("    Starting XP: ").append(startXp).append("\n");
        sb.append("    Current XP: ").append(getCurrentXp());
        
        return sb.toString();
    }
}