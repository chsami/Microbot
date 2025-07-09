package net.runelite.client.plugins.microbot.pluginscheduler.condition.logical;

import java.time.ZonedDateTime;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.function.Supplier;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;

/**
 * A condition that combines a manual lock with a predicate evaluation.
 * The condition is satisfied only when:
 * 1. It is not manually locked, AND
 * 2. The predicate evaluates to true
 * 
 * This allows plugins to define dynamic conditions that depend on the game state
 * while still maintaining the ability to manually lock/unlock the condition.
 */
@Slf4j
@EqualsAndHashCode(callSuper = true)
public class PredicateCondition<T> extends LockCondition {
    
    @Getter
    private final Predicate<T> predicate;
    private final Supplier<T> stateSupplier;
    private final String predicateDescription;
    
    /**
     * Creates a new predicate condition with a default reason.
     * 
     * @param predicate The predicate to evaluate
     * @param stateSupplier A supplier that provides the current state to evaluate against the predicate
     * @param predicateDescription A human-readable description of what the predicate checks
     * @throws IllegalArgumentException if predicate or stateSupplier is null
     */
    public PredicateCondition(Predicate<T> predicate, Supplier<T> stateSupplier, String predicateDescription) {
        super("Plugin is locked or predicate condition is not met", false);
        validateConstructorArguments(predicate, stateSupplier, predicateDescription);
        this.predicate = predicate;
        this.stateSupplier = stateSupplier;
        this.predicateDescription = predicateDescription != null ? predicateDescription : "Unknown predicate";
    }
    
    /**
     * Creates a new predicate condition with a specified reason.
     * 
     * @param reason The reason why the plugin is locked
     * @param predicate The predicate to evaluate
     * @param stateSupplier A supplier that provides the current state to evaluate against the predicate
     * @param predicateDescription A human-readable description of what the predicate checks
     * @throws IllegalArgumentException if predicate or stateSupplier is null
     */
    public PredicateCondition(String reason, Predicate<T> predicate, Supplier<T> stateSupplier, String predicateDescription) {
        super(reason, false);
        validateConstructorArguments(predicate, stateSupplier, predicateDescription);
        this.predicate = predicate;
        this.stateSupplier = stateSupplier;
        this.predicateDescription = predicateDescription != null ? predicateDescription : "Unknown predicate";
    }
    
    /**
     * Creates a new predicate condition with a specified reason and initial lock state.
     * 
     * @param reason The reason why the plugin is locked
     * @param defaultLock The initial lock state
     * @param predicate The predicate to evaluate
     * @param stateSupplier A supplier that provides the current state to evaluate against the predicate
     * @param predicateDescription A human-readable description of what the predicate checks
     * @throws IllegalArgumentException if predicate or stateSupplier is null
     */
    public PredicateCondition(String reason, boolean defaultLock, Predicate<T> predicate, Supplier<T> stateSupplier, String predicateDescription) {
        super(reason, defaultLock);
        validateConstructorArguments(predicate, stateSupplier, predicateDescription);
        this.predicate = predicate;
        this.stateSupplier = stateSupplier;
        this.predicateDescription = predicateDescription != null ? predicateDescription : "Unknown predicate";
    }
    
    /**
     * Validates that required constructor arguments are not null
     * 
     * @param predicate The predicate to evaluate
     * @param stateSupplier A supplier that provides the current state
     * @param predicateDescription A description of the predicate
     * @throws IllegalArgumentException if predicate or stateSupplier is null
     */
    private void validateConstructorArguments(Predicate<T> predicate, Supplier<T> stateSupplier, String predicateDescription) {
        if (predicate == null) {
            log.error("Predicate cannot be null in PredicateCondition constructor");
            throw new IllegalArgumentException("Predicate cannot be null");
        }
        if (stateSupplier == null) {
            log.error("State supplier cannot be null in PredicateCondition constructor");
            throw new IllegalArgumentException("State supplier cannot be null");
        }
        if (predicateDescription == null) {
            log.warn("Predicate description is null, using default");
        }
    }
    
    /**
     * Evaluates the current state against the predicate.
     * This method is thread-safe and handles exceptions safely.
     * 
     * @return True if the predicate is satisfied, false otherwise or if an exception occurs
     */
    public synchronized boolean evaluatePredicate() {
        try {
            if (stateSupplier == null) {
                log.warn("State supplier is null in predicateDescription: {}", predicateDescription);
                return false;
            }
            
            T currentState = stateSupplier.get();
            
            if (predicate == null) {
                log.warn("Predicate is null in predicateDescription: {}", predicateDescription);
                return false;
            }
            
            return predicate.test(currentState);
        } catch (Exception e) {
            log.error("Exception in predicateDescription: {} - {}", predicateDescription, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public synchronized boolean isSatisfied() {
        try {
            // The condition is satisfied only if:
            // 1. It's not manually locked (from parent class)
            // 2. The predicate evaluates to true
            return super.isSatisfied() && evaluatePredicate();
        } catch (Exception e) {
            log.error("Exception in isSatisfied for predicateDescription: {} - {}", predicateDescription, e.getMessage(), e);
            return false;
        }
    }
    
    @Override
    public synchronized String getDescription() {
        try {
            boolean predicateSatisfied = evaluatePredicate();
            return "Predicate Condition: " + 
                  (isLocked() ? "\nLOCKED - " + getReason() : "\nUNLOCKED") + 
                  "\nPredicate: " + predicateDescription + 
                  "\nPredicate Satisfied: " + (predicateSatisfied ? "Yes" : "No");
        } catch (Exception e) {
            log.error("Exception in getDescription for predicateDescription: {} - {}", predicateDescription, e.getMessage(), e);
            return "Predicate Condition: [Error retrieving description]";
        }
    }
    
    @Override
    public synchronized String getDetailedDescription() {
        return getDescription();
    }
    
    @Override
    public ConditionType getType() {
        return ConditionType.LOGICAL;
    }
    
    @Override
    public synchronized void reset(boolean randomize) {
        try {
            // Reset the lock state from parent class
            super.reset(randomize);
            // No need to reset predicate or supplier
        } catch (Exception e) {
            log.error("Exception in reset for predicateDescription: {} - {}", predicateDescription, e.getMessage(), e);
        }
    }
    
    @Override
    public Optional<ZonedDateTime> getCurrentTriggerTime() {
        // Predicate conditions don't have a specific trigger time
        return Optional.empty();
    }
    public void pause() {
       
                    
    }
    
   
    public void resume() {
            
        
    }
}
