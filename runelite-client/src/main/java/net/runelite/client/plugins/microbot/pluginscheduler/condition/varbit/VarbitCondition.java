package net.runelite.client.plugins.microbot.pluginscheduler.condition.varbit;

import java.time.ZonedDateTime;
import java.util.Optional;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

/**
 * A condition that checks the current value of a Varbit or VarPlayer.
 * This can be used to track game state like quest progress, minigame scores, 
 * collection log completions, etc.
 */
@Slf4j
@EqualsAndHashCode(callSuper = false)
public class VarbitCondition implements Condition {
    
    public static String getVersion() {
        return "0.0.1";
    }
    /**
     * Defines the different types of variables that can be tracked
     */
    public enum VarType {
        VARBIT,
        VARPLAYER
    }
    
    /**
     * Comparison operators for the varbit value
     */
    public enum ComparisonOperator {
        EQUALS("equals"),
        NOT_EQUALS("not equals"),
        GREATER_THAN("greater than"),
        GREATER_THAN_OR_EQUALS("greater than or equals"),
        LESS_THAN("less than"),
        LESS_THAN_OR_EQUALS("less than or equals");
        
        private final String displayName;
        
        ComparisonOperator(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    @Getter private final String name;
    @Getter private final VarType varType;
    @Getter private final int varId;
    @Getter private final int targetValue;
    @Getter private final ComparisonOperator operator;
    
    
    @Getter private final boolean relative;    
    @Getter private final boolean randomized;
    @Getter private final int targetValueMin;
    @Getter private final int targetValueMax;
    
    @Getter @Setter private transient int currentValue;
    @Getter private transient boolean satisfied;
    @Getter private transient int startValue;
    @Getter private transient int effectiveTargetValue;

    /**
     * Creates a new VarbitCondition with absolute target value
     * 
     * @param name A human-readable name for this condition
     * @param varType Whether this is tracking a Varbit or VarPlayer variable
     * @param varId The ID of the variable to track
     * @param targetValue The target value to compare against
     * @param operator The comparison operator to use
     */
    public VarbitCondition(String name, VarType varType, int varId, int targetValue, ComparisonOperator operator) {
        this.name = name;
        this.varType = varType;
        this.varId = varId;
        this.targetValue = targetValue;
        this.operator = operator;
        this.relative = false;
        this.randomized = false;
        this.targetValueMin = targetValue;
        this.targetValueMax = targetValue;
        this.effectiveTargetValue = targetValue;
        
        // Initialize current value and starting value
        updateCurrentValue();
        this.startValue = this.currentValue;
        this.satisfied = checkSatisfied();
    }
    
    /**
     * Creates a new VarbitCondition with absolute target value and randomization range
     * 
     * @param name A human-readable name for this condition
     * @param varType Whether this is tracking a Varbit or VarPlayer variable
     * @param varId The ID of the variable to track
     * @param targetValueMin The minimum target value to compare against
     * @param targetValueMax The maximum target value to compare against
     * @param operator The comparison operator to use
     */
    public VarbitCondition(String name, VarType varType, int varId, int targetValueMin, int targetValueMax, ComparisonOperator operator) {
        this.name = name;
        this.varType = varType;
        this.varId = varId;
        this.targetValueMin = targetValueMin;
        this.targetValueMax = targetValueMax;
        this.targetValue = Rs2Random.between(targetValueMin, targetValueMax);
        this.operator = operator;
        this.relative = false;
        this.randomized = true;
        this.effectiveTargetValue = this.targetValue;
        
        // Initialize current value and starting value
        updateCurrentValue();
        this.startValue = this.currentValue;
        this.satisfied = checkSatisfied();
    }
    
    /**
     * Creates a new VarbitCondition with relative target value
     * 
     * @param name A human-readable name for this condition
     * @param varType Whether this is tracking a Varbit or VarPlayer variable
     * @param varId The ID of the variable to track
     * @param targetValue The target value delta to compare against
     * @param operator The comparison operator to use
     * @param relative Whether this is a relative target value
     */
    public VarbitCondition(String name, VarType varType, int varId, int targetValue, ComparisonOperator operator, boolean relative) {
        this.name = name;
        this.varType = varType;
        this.varId = varId;
        this.targetValue = targetValue;
        this.operator = operator;
        this.relative = relative;
        this.randomized = false;
        this.targetValueMin = targetValue;
        this.targetValueMax = targetValue;
        
        // Initialize current value and starting value
        updateCurrentValue();
        this.startValue = this.currentValue;
        
        // Calculate effective target value for relative mode
        if (relative) {
            calculateEffectiveTargetValue();
        } else {
            this.effectiveTargetValue = targetValue;
        }
        
        this.satisfied = checkSatisfied();
    }
    
    /**
     * Creates a new VarbitCondition with relative target value and randomization range
     * 
     * @param name A human-readable name for this condition
     * @param varType Whether this is tracking a Varbit or VarPlayer variable
     * @param varId The ID of the variable to track
     * @param targetValueMin The minimum target value delta to compare against
     * @param targetValueMax The maximum target value delta to compare against
     * @param operator The comparison operator to use
     * @param relative Whether this is a relative target value
     */
    public VarbitCondition(String name, VarType varType, int varId, int targetValueMin, int targetValueMax, ComparisonOperator operator, boolean relative) {
        this.name = name;
        this.varType = varType;
        this.varId = varId;
        this.targetValueMin = targetValueMin;
        this.targetValueMax = targetValueMax;
        this.targetValue = Rs2Random.between(targetValueMin, targetValueMax);
        this.operator = operator;
        this.relative = relative;
        this.randomized = true;
        
        // Initialize current value and starting value
        updateCurrentValue();
        this.startValue = this.currentValue;
        
        // Calculate effective target value for relative mode
        
        calculateEffectiveTargetValue();
        
        
        
        
        this.satisfied = checkSatisfied();
    }
    
    /**
     * Calculate the effective target value based on the starting value and target delta
     */
    private void calculateEffectiveTargetValue() {
        if (relative) {
            switch (operator) {
                case EQUALS:
                case GREATER_THAN:
                case GREATER_THAN_OR_EQUALS:
                    this.effectiveTargetValue = startValue + targetValue;
                    break;
                case LESS_THAN:
                case LESS_THAN_OR_EQUALS:
                    this.effectiveTargetValue = Math.max(0, startValue - targetValue);
                    break;
                case NOT_EQUALS:
                    this.effectiveTargetValue = startValue; // Not straightforward for NOT_EQUALS, use start value
                    break;
                default:
                    this.effectiveTargetValue = targetValue;
            }
        } else {
            this.effectiveTargetValue = targetValue;
        }
    }
    
    /**
     * Create a VarbitCondition with relative target value
     */
    public static VarbitCondition createRelative(String name, VarType varType, int varId, int targetValue, ComparisonOperator operator) {
        return new VarbitCondition(name, varType, varId, targetValue, operator, true);
    }
    
    /**
     * Create a VarbitCondition with randomized relative target value
     */
    public static VarbitCondition createRelativeRandomized(String name, VarType varType, int varId, int targetValueMin, int targetValueMax, ComparisonOperator operator) {
        return new VarbitCondition(name, varType, varId, targetValueMin, targetValueMax, operator, true);
    }
    
    /**
     * Create a VarbitCondition with randomized absolute target value
     */
    public static VarbitCondition createRandomized(String name, VarType varType, int varId, int targetValueMin, int targetValueMax, ComparisonOperator operator) {
        return new VarbitCondition(name, varType, varId, targetValueMin, targetValueMax, operator);
    }
    
    /**
     * Updates the current value from the game
     */
    private void updateCurrentValue() {
        try {
            if (Microbot.isLoggedIn()){
                if (varType == VarType.VARBIT) {
                    this.currentValue = Microbot.getVarbitValue(varId);
                } else {
                    this.currentValue = Microbot.getVarbitPlayerValue(varId);
                }
            }else{
                this.currentValue = -1;
            }
        } catch (Exception e) {
            log.error("Error getting current value for " + varType + " " + varId, e);
            this.currentValue = -1;
        }
    }
    
    /**
     * Checks if the condition is satisfied based on the current value and operator
     */
    private boolean checkSatisfied() {
        if ( this.startValue ==-1) {
            updateCurrentValue();
            this.startValue = this.currentValue;
            if (relative) {
                calculateEffectiveTargetValue();
            } else {
                this.effectiveTargetValue = targetValue;
            }
            if (this.startValue == -1) {
                return false; // Not logged in or error getting value
            }            
        }
        int compareValue = relative ? effectiveTargetValue : targetValue;
        
        switch (operator) {
            case EQUALS:
                return currentValue == compareValue;
            case NOT_EQUALS:
                return currentValue != compareValue;
            case GREATER_THAN:
                return currentValue > compareValue;
            case GREATER_THAN_OR_EQUALS:
                return currentValue >= compareValue;
            case LESS_THAN:
                return currentValue < compareValue;
            case LESS_THAN_OR_EQUALS:
                return currentValue <= compareValue;
            default:
                return false;
        }
    }
    
    /**
     * Get the value change since the condition was created
     */
    public int getValueChange() {
        return currentValue - startValue;
    }
    
    /**
     * Get the value needed to reach the target
     */
    public int getValueNeeded() {
        if (!relative) {
            return 0;
        }
        
        switch (operator) {
            case EQUALS:
                return effectiveTargetValue - currentValue;
            case GREATER_THAN:
            case GREATER_THAN_OR_EQUALS:
                return Math.max(0, effectiveTargetValue - currentValue);
            case LESS_THAN:
            case LESS_THAN_OR_EQUALS:
                return Math.max(0, currentValue - effectiveTargetValue);
            case NOT_EQUALS:
            default:
                return 0;
        }
    }
    
    /**
     * Called when a varbit changes
     */
    @Override
    public void onVarbitChanged(VarbitChanged event) {
        boolean oldSatisfied = this.satisfied;
        updateCurrentValue();
        this.satisfied = checkSatisfied();
        
        // Log when the condition changes state
        if (oldSatisfied != this.satisfied) {
            log.debug("VarbitCondition '{}' changed state: {} -> {}", 
                    name, oldSatisfied, this.satisfied);
        }
    }
    
    @Override
    public boolean isSatisfied() {
        updateCurrentValue();
        this.satisfied = checkSatisfied();
        return this.satisfied;
    }
    
    @Override
    public String getDescription() {
        String varTypeDisplay = varType.toString().toLowerCase();
        updateCurrentValue();
        
        StringBuilder description = new StringBuilder(name);
        description.append(" (").append(varTypeDisplay).append(" ID: ").append(varId);
        description.append(", Name: ").append(this.name);
        description.append(", Operate: ").append(this.operator.getDisplayName());
        // Show randomization range if applicable
        if (randomized) {
            description.append(", random ");
        }
        
        // Show relative or absolute mode
        if (relative) {
            if (operator == ComparisonOperator.EQUALS) {
                description.append(", change by ").append(targetValue);
            } else {
                description.append(", ").append(operator.getDisplayName())
                          .append(" change of ").append(targetValue);
            }
            if (this.startValue ==-1) {
                description.append(", starting value unknown");
            } else {
                description.append(", starting ").append(startValue);
            }
            // Add current progress for relative mode
            description.append(", changed ").append(getValueChange());
            
            int valueNeeded = getValueNeeded();
            if (valueNeeded > 0) {
                description.append(", need ").append(valueNeeded).append(" more");
            }
        } else {
            description.append(", ").append(operator.getDisplayName())
                      .append(" ").append(targetValue);
        }
        if (currentValue != -1) {
            description.append(", current ").append(currentValue);
        }else{
            description.append(", current value unknown");
        }
        description.append(")");
        return description.toString();
    }
    
    @Override
    public String getDetailedDescription() {
        updateCurrentValue();
        StringBuilder desc = new StringBuilder();
        
        desc.append("VarbitCondition: ").append(name).append("\n")
            .append("Type: ").append(varType.toString()).append("\n")
            .append("ID: ").append(varId).append("\n")
            .append("Mode: ").append(relative ? "Relative" : "Absolute").append("\n");
        
        if (randomized) {
            desc.append("Target range: ").append(targetValueMin).append("-").append(targetValueMax).append("\n");
        }
        
        if (relative) {
            desc.append("Target change: ").append(targetValue).append("\n")
                .append("Starting value: ").append(startValue).append("\n")
                .append("Current value: ").append(currentValue).append("\n")
                .append("Value change: ").append(getValueChange()).append("\n")
                .append("Effective target: ").append(effectiveTargetValue).append("\n");
        } else {
            desc.append("Target value: ").append(targetValue).append("\n")
                .append("Current value: ").append(currentValue).append("\n");
        }
        
        desc.append("Operator: ").append(operator.getDisplayName()).append("\n")
            .append("Satisfied: ").append(isSatisfied() ? "Yes" : "No").append("\n")
            .append("Progress: ").append(String.format("%.1f%%", getProgressPercentage()));
        
        return desc.toString();
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.VARBIT;
    }
    
    @Override
    public void reset(boolean randomize) {
        updateCurrentValue();
        
        // Reset starting value
        this.startValue = this.currentValue;
        
        // Randomize target if needed
        if (randomize && randomized) {
            int newTarget = Rs2Random.between(targetValueMin, targetValueMax);
            
            // Use reflection to update the final field (not ideal but necessary for this design)
            try {
                java.lang.reflect.Field targetField = VarbitCondition.class.getDeclaredField("targetValue");
                targetField.setAccessible(true);
                
                // Remove final modifier
                java.lang.reflect.Field modifiersField = java.lang.reflect.Field.class.getDeclaredField("modifiers");
                modifiersField.setAccessible(true);
                modifiersField.setInt(targetField, targetField.getModifiers() & ~java.lang.reflect.Modifier.FINAL);
                
                // Set new value
                targetField.set(this, newTarget);
            } catch (Exception e) {
                log.error("Error updating target value", e);
            }
        }
        
        // Recalculate effective target for relative mode
        if (relative) {
            calculateEffectiveTargetValue();
        }
        
        this.satisfied = checkSatisfied();
    }
    
    @Override
    public Optional<ZonedDateTime> getCurrentTriggerTime() {
        return Condition.super.getCurrentTriggerTime();
    }
    
    @Override
    public double getProgressPercentage() {
        updateCurrentValue();
        
        // For binary conditions (equals/not equals), return either 0 or 100
        if (operator == ComparisonOperator.EQUALS || operator == ComparisonOperator.NOT_EQUALS) {
            return isSatisfied() ? 100.0 : 0.0;
        }
        
        // For relative mode with increase operators
        if (relative && (operator == ComparisonOperator.GREATER_THAN || 
                        operator == ComparisonOperator.GREATER_THAN_OR_EQUALS)) {
            int change = getValueChange();
            if (change >= targetValue) {
                return 100.0;
            }
            return targetValue > 0 ? Math.min(100.0, (change * 100.0) / targetValue) : 0.0;
        }
        
        // For relative mode with decrease operators
        if (relative && (operator == ComparisonOperator.LESS_THAN || 
                        operator == ComparisonOperator.LESS_THAN_OR_EQUALS)) {
            int change = startValue - currentValue;
            if (change >= targetValue) {
                return 100.0;
            }
            return targetValue > 0 ? Math.min(100.0, (change * 100.0) / targetValue) : 0.0;
        }
        
        // For absolute mode comparisons
        int compareValue = relative ? effectiveTargetValue : targetValue;
        double progress = 0.0;
        
        switch (operator) {
            case GREATER_THAN:
            case GREATER_THAN_OR_EQUALS:
                if (currentValue >= compareValue) {
                    progress = 100.0;
                } else if (compareValue > 0) {
                    progress = Math.min(100.0, (currentValue * 100.0) / compareValue);
                }
                break;
                
            case LESS_THAN:
            case LESS_THAN_OR_EQUALS:
                // For "less than" we show progress if we're below the target
                if (currentValue <= compareValue) {
                    progress = 100.0;
                } else if (currentValue > 0) {
                    // Inverse progress - as we get closer to the target
                    progress = Math.min(100.0, (compareValue * 100.0) / currentValue);
                }
                break;
                
            default:
                progress = isSatisfied() ? 100.0 : 0.0;
        }
        
        return progress;
    }
}