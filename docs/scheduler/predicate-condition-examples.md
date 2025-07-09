# PredicateCondition Examples

This document provides practical examples of how to use the `PredicateCondition` class in your plugins to create dynamic stop conditions based on the game state.

## Overview

The `PredicateCondition` is a powerful extension of `LockCondition` that combines:
1. A manual lock mechanism (inherited from `LockCondition`)
2. A Java Predicate for evaluating dynamic game states

This makes it perfect for creating stop conditions that depend on the current state of the game rather than static values.

## Basic Implementation Pattern

The general pattern for implementing a `PredicateCondition` is:

```java
// Create the predicate function that evaluates game state
Predicate<T> myPredicate = gameState -> {
    // Logic to evaluate the game state
    return someBoolean; // true if condition is satisfied
};

// Create a supplier that provides the current state
Supplier<T> stateSupplier = () -> {
    // Return the current state to be evaluated
    return currentState;
};

// Create the PredicateCondition
PredicateCondition<T> condition = new PredicateCondition<>(
    "Human-readable reason for locking",
    myPredicate,
    stateSupplier,
    "Description of what the predicate checks"
);
```

## Example 1: Region-Based Agility Plugin

This example demonstrates how to use `PredicateCondition` to stop an agility plugin when the player leaves an agility course region:

```java
public class MicroAgilityPlugin extends Plugin implements SchedulablePlugin {
    private PredicateCondition<Player> notInCourseCondition;
    private LockCondition lockCondition;
    private LogicalCondition stopCondition = null;
    private final Set<Integer> courseRegionIds = new HashSet<>();
    
    @Inject
    private Client client;
    
    /**
     * Initialize the list of region IDs where agility courses are located
     */
    private void initializeCourseRegions() {
        // Add region IDs for all agility courses
        courseRegionIds.add(9781);  // Gnome Stronghold
        courseRegionIds.add(12338); // Draynor Village
        courseRegionIds.add(13105); // Al Kharid
        // ... other course regions
    }
    
    /**
     * Set up the predicate condition that will be used to determine if the player is in an agility course
     */
    private void setupPredicateCondition() {
        // This predicate checks if the player is in an agility course region
        Predicate<Player> notInAgilityRegion = player -> {
            if (player == null) return true; // If player is null, condition is satisfied (safer to stop)
            
            int playerRegionId = player.getWorldLocation().getRegionID();
            
            // Return true if player is NOT in a course (condition to stop is satisfied)
            return !courseRegionIds.contains(playerRegionId);
        };
        
        // Create the predicate condition
        notInCourseCondition = new PredicateCondition<>(
                "Player is currently in an agility course",
                notInAgilityRegion,
                () -> client.getLocalPlayer(),
                "Player is not in an agility course region"
        );
    }
    
    @Override
    public LogicalCondition getStopCondition() {
        if (this.stopCondition == null) {
            this.stopCondition = createStopCondition();
        }
        return this.stopCondition;
    }
    
    private LogicalCondition createStopCondition() {
        if (this.lockCondition == null) {
            this.lockCondition = new LockCondition("Locked because the Agility Plugin is in a critical operation");
        }
        
        // Setup course regions if not already done
        if (courseRegionIds.isEmpty()) {
            initializeCourseRegions();
        }
        
        // Setup predicate condition if not already done
        if (notInCourseCondition == null) {
            setupPredicateCondition();
        }
        
        // Combine the lock condition and the predicate condition with AND logic
        AndCondition andCondition = new AndCondition();
        andCondition.addCondition(lockCondition);
        andCondition.addCondition(notInCourseCondition);
        return andCondition;
    }
}
```

## Example 2: Combat State Monitoring

This example shows how to use `PredicateCondition` to track if a player is in combat:

```java
public class CombatPluginExample extends Plugin implements SchedulablePlugin {
    private PredicateCondition<Player> notInCombatCondition;
    private LockCondition lockCondition;
    
    @Inject
    private Client client;
    
    private void setupCombatCondition() {
        Predicate<Player> notInCombat = player -> {
            if (player == null) return true;
            
            // Check if the player is in combat
            boolean inCombat = player.getInteracting() != null || 
                              (player.getHealthScale() > 0 && 
                               System.currentTimeMillis() - player.getLastCombatTime() < 5000);
            
            // Return true if NOT in combat (condition to stop is satisfied)
            return !inCombat;
        };
        
        notInCombatCondition = new PredicateCondition<>(
            "Player is currently in combat",
            notInCombat,
            () -> client.getLocalPlayer(),
            "Player is not in combat"
        );
    }
    
    @Override
    public LogicalCondition getStopCondition() {
        if (lockCondition == null) {
            lockCondition = new LockCondition("Critical combat operation in progress");
        }
        
        if (notInCombatCondition == null) {
            setupCombatCondition();
        }
        
        AndCondition stopCondition = new AndCondition();
        stopCondition.addCondition(lockCondition);
        stopCondition.addCondition(notInCombatCondition);
        
        return stopCondition;
    }
}
```

## Example 3: Multiple State Conditions

This example demonstrates combining multiple predicate conditions:

```java
public class FishingPluginExample extends Plugin implements SchedulablePlugin {
    private PredicateCondition<Player> notFishingCondition;
    private PredicateCondition<Player> inventoryFullCondition;
    private LockCondition lockCondition;
    
    @Inject
    private Client client;
    
    private void setupConditions() {
        // Check if the player is not fishing
        Predicate<Player> notFishing = player -> {
            if (player == null) return true;
            return player.getAnimation() != FISHING_ANIMATION;
        };
        
        // Check if inventory is full
        Predicate<Player> inventoryFull = player -> {
            if (player == null) return false;
            return client.getItemContainer(InventoryID.INVENTORY).size() >= 28;
        };
        
        notFishingCondition = new PredicateCondition<>(
            "Player is actively fishing",
            notFishing,
            () -> client.getLocalPlayer(),
            "Player is not currently fishing"
        );
        
        inventoryFullCondition = new PredicateCondition<>(
            "Inventory has space",
            inventoryFull,
            () -> client.getLocalPlayer(),
            "Inventory is full"
        );
    }
    
    @Override
    public LogicalCondition getStopCondition() {
        if (lockCondition == null) {
            lockCondition = new LockCondition("Critical fishing operation in progress");
        }
        
        if (notFishingCondition == null || inventoryFullCondition == null) {
            setupConditions();
        }
        
        // Create a structure: (Lock AND (NotFishing OR InventoryFull))
        OrCondition fishingOrInventoryCondition = new OrCondition();
        fishingOrInventoryCondition.addCondition(notFishingCondition);
        fishingOrInventoryCondition.addCondition(inventoryFullCondition);
        
        AndCondition stopCondition = new AndCondition();
        stopCondition.addCondition(lockCondition);
        stopCondition.addCondition(fishingOrInventoryCondition);
        
        return stopCondition;
    }
}
```

## Best Practices

When using `PredicateCondition`, follow these best practices:

1. **Safety Checks**: Always handle null values in your predicates to prevent NullPointerExceptions.

2. **Clear Descriptions**: Provide meaningful descriptions for your predicate conditions, as these will be shown in the UI.

3. **Logical Grouping**: Use logical conditions (AND/OR) to group predicate conditions with other conditions in meaningful ways.

4. **State Suppliers**: Create efficient state suppliers that provide only the necessary game state for evaluation.

5. **Locking Logic**: Remember that the condition is only satisfied when both the lock is unlocked AND the predicate returns true.

6. **Performance**: Keep predicate evaluation efficient as it may be called frequently.

7. **Debugging**: Use `Microbot.log()` in your predicates during development to debug condition evaluation.

## Conclusion

The `PredicateCondition` class provides a powerful way to create dynamic stop conditions based on the current game state. By leveraging Java Predicates, you can create sophisticated conditions that respond to the game environment in real-time, making your plugins more intelligent and responsive.
