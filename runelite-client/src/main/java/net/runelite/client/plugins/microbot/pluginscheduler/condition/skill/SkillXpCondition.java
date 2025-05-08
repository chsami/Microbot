package net.runelite.client.plugins.microbot.pluginscheduler.condition.skill;



import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

/**
 * Skill XP-based condition for script execution.
 */
@Getter 
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class SkillXpCondition extends SkillCondition {
    

    public static String getVersion() {
        return "0.0.1";
    }
    private transient long currentTargetXp;// relative and absolute mode difference
    private final long targetXpMin;
    private final long targetXpMax;
    private transient long startXp;
    private transient long[] startXpBySkill; // Used for total XP tracking
    private final boolean randomized;
    @Getter
    private final boolean relative; // Whether this is a relative or absolute XP target

    /**
     * Creates an absolute XP condition (must reach specific XP amount)
     */
    public SkillXpCondition(Skill skill, long targetXp) {
        super(skill);
        this.currentTargetXp = targetXp;
        this.targetXpMin = targetXp;
        this.targetXpMax = targetXp;
        this.randomized = false;
        this.relative = false; // Absolute XP target
        initializeXpTracking();
    }
    
    /**
     * Creates a randomized absolute XP condition (must reach specific XP amount within a range)
     */
    public SkillXpCondition(Skill skill, long targetXpMin, long targetXpMax) {
        super(skill);
        targetXpMin = Math.max(0, targetXpMin);
        targetXpMax = Math.min(Long.MAX_VALUE, targetXpMax);
        
        this.currentTargetXp = Rs2Random.between((int)targetXpMin, (int)targetXpMax);
        this.targetXpMin = targetXpMin;
        this.targetXpMax = targetXpMax;
        this.randomized = true;
        this.relative = false; // Absolute XP target
        initializeXpTracking();
    }

    /**
     * Creates a relative XP condition (must gain specific amount of XP from current)
     */
    public SkillXpCondition(Skill skill, long targetXp, boolean relative) {
        super(skill);
        this.currentTargetXp = targetXp;
        this.targetXpMin = targetXp;
        this.targetXpMax = targetXp;
        this.randomized = false;
        this.relative = relative;
        initializeXpTracking();
    }
    
    /**
     * Creates a randomized relative XP condition (must gain a random amount of XP from current)
     */
    public SkillXpCondition(Skill skill, long targetXpMin, long targetXpMax, boolean relative) {
        super(skill);
        targetXpMin = Math.max(0, targetXpMin);
        targetXpMax = Math.min(Long.MAX_VALUE, targetXpMax);
        
        this.currentTargetXp = Rs2Random.between((int)targetXpMin, (int)targetXpMax);
        this.targetXpMin = targetXpMin;
        this.targetXpMax = targetXpMax;
        this.randomized = true;
        this.relative = relative;
        initializeXpTracking();
    }
    
    /**
     * Initialize XP tracking for individual skill or all skills if total
     */
    private void initializeXpTracking() {
        if (isTotal()) {
            Skill[] skills = getAllTrackableSkills();
            this.startXpBySkill = new long[skills.length];
            long totalXp = getTotalXp();
            this.startXp = totalXp;
        } else {
            this.startXp = getCurrentXp();
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
     * Create an absolute skill XP condition with random target between min and max
     */
    public static SkillXpCondition createRandomized(Skill skill, long minXp, long maxXp) {
        if (minXp == maxXp) {
            return new SkillXpCondition(skill, minXp);
        }
        
        return new SkillXpCondition(skill, minXp, maxXp);
    }

    /**
     * Create a relative skill XP condition (gain XP from current)
     */
    public static SkillXpCondition createRelative(Skill skill, long targetXp) {
        return new SkillXpCondition(skill, targetXp, true);
    }

    /**
     * Create a relative skill XP condition with random target between min and max
     */
    public static SkillXpCondition createRelativeRandomized(Skill skill, long minXp, long maxXp) {
        if (minXp == maxXp) {
            return new SkillXpCondition(skill, minXp, true);
        }
        
        return new SkillXpCondition(skill, minXp, maxXp, true);
    }
    
    @Override
    public boolean isSatisfied() {
        if (relative) {
            // For relative mode, we need to check if we've gained the target amount of XP
            return getXpGained() >= currentTargetXp;
        } else {
            // For absolute mode, we need to check if our current XP is at or above the target
            return getCurrentXp() >= currentTargetXp;
        }
    }
    
    /**
     * Gets the amount of XP gained since condition was created
     */
    public long getXpGained() {
        if (isTotal()) {
            return getTotalXp() - startXp;
        } else {
            return getCurrentXp() - startXp;
        }
    }
    
  
    
    /**
     * Gets the amount of XP remaining to reach target
     */
    public long getXpRemaining() {
        if (relative) {
            return Math.max(0, currentTargetXp - getXpGained());
        } else {
            return Math.max(0, currentTargetXp - getCurrentXp());
        }
    }
    
    /**
     * Gets the current XP
     * Uses static cached data from SkillCondition
     */
    public long getCurrentXp() {
        if (isTotal()) {
            return getTotalXp();
        }
        
        // Use static cached data from SkillCondition class
        return SkillCondition.getSkillXp(skill);
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
        if (relative) {
            long xpGained = getXpGained();
            if (xpGained >= currentTargetXp) {
                return 100.0;
            }
            
            if (currentTargetXp <= 0) {
                return 100.0;
            }
            
            return (100.0 * xpGained) / currentTargetXp;
        } else {
            // For absolute targets, we need to calculate progress from 0 to target
            long currentXp = getCurrentXp();
            
            if (currentXp >= currentTargetXp) {
                return 100.0;
            }
            
            if (currentTargetXp <= 0) {
                return 100.0;
            }
            
            return (100.0 * currentXp) / currentTargetXp;
        }
    }
    
    @Override
    public String getDescription() {
        String skillName = isTotal() ? "Total" : skill.getName();
        
        if (relative) {
            long xpGained = getXpGained();
            long currentXp = getCurrentXp();                       
            String randomRangeInfo = "";
            
            if (targetXpMin != targetXpMax) {
                randomRangeInfo = String.format(" (randomized from %d-%d)", targetXpMin, targetXpMax);
            }
            
            return String.format("Gain Relative %d %s XP%s (gained: %d - %.1f%%, current total: %d)", 
                currentTargetXp, 
                skillName,
                randomRangeInfo,                
                xpGained,
                getProgressPercentage(),currentXp);
        } else {
            long currentXp = getCurrentXp();
            String randomRangeInfo = "";
            
            if (targetXpMin != targetXpMax) {
                randomRangeInfo = String.format(" (randomized from %d-%d)", targetXpMin, targetXpMax);
            }
            
            if (currentXp >= currentTargetXp) {
                return String.format("Reach Total %d %s XP%s (currently: %d, goal reached)", 
                        currentTargetXp, 
                        skillName,
                        randomRangeInfo,
                        currentXp);
            } else {
                return String.format("Reach Total %d %s XP%s (currently: %d, need %d more ( %.1f%%))", 
                        currentTargetXp, 
                        skillName,
                        randomRangeInfo,
                        currentXp,
                        getXpRemaining(),
                        getProgressPercentage()
                        );
            }
        }
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.SKILL;
    }

    /**
     * Returns a detailed description of the XP condition with additional status information
     */
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        String skillName = isTotal() ? "Total" : skill.getName();
        
        // Basic description
        if (relative) {
            sb.append("Skill XP Condition: Gain ").append(currentTargetXp)
              .append(" ").append(skillName).append(" XP from starting XP\n");
        } else {
            sb.append("Skill XP Condition: Reach ").append(currentTargetXp)
              .append(" ").append(skillName).append(" XP total\n");
        }
        
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
        sb.append("\nSkillXpCondition:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        sb.append("  │ Skill: ").append(skillName).append("\n");
        
        if (relative) {
            sb.append("  │ Mode: Relative (gain from current)\n");
            sb.append("  │ Target XP gain: ").append(currentTargetXp).append("\n");
        } else {
            sb.append("  │ Mode: Absolute (reach total)\n");
            sb.append("  │ Target XP total: ").append(currentTargetXp).append("\n");
        }
        
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
        
        if (relative) {
            sb.append("  │ XP Gained: ").append(getXpGained()).append("\n");
            
            if (!satisfied) {
                sb.append("  │ XP Remaining: ").append(getXpRemaining()).append("\n");
            }
        } else {
            long currentXp = getCurrentXp();
            if (currentXp >= currentTargetXp) {
                sb.append("  │ Current XP: ").append(currentXp).append(" (goal reached)\n");
            } else {
                sb.append("  │ Current XP: ").append(currentXp).append("\n");
                sb.append("  │ XP Remaining: ").append(getXpRemaining()).append("\n");
            }
        }
        
        sb.append("  │ Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
        
        // Current state
        sb.append("  └─ Current State ──────────────────────────\n");
        sb.append("    Starting XP: ").append(startXp).append("\n");
        sb.append("    Current XP: ").append(getCurrentXp());
        
        return sb.toString();
    }
    @Override
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN) {
            super.onGameStateChanged(gameStateChanged);
            initializeXpTracking();
        }else{
            
        }
    }
}