# ConditionManager

## Overview

The `ConditionManager` class is a sophisticated component of the Plugin Scheduler system responsible for handling the hierarchical structure of conditions that determine when plugins should start and stop. It manages two separate condition hierarchies - one for plugin-defined conditions and another for user-defined conditions - and provides methods to evaluate, combine, and manipulate these conditions.

This class serves as the "brain" of the condition evaluation system, determining whether a plugin should start or stop based on complex logical structures of conditions.

## Class Definition

```java
@Slf4j
public class ConditionManager implements AutoCloseable {
    // Condition hierarchies
    private LogicalCondition pluginCondition;
    private LogicalCondition userLogicalCondition;
    
    // Event handling
    private final EventBus eventBus;
    private boolean eventsRegistered;
    
    // Watchdog system
    private boolean watchdogsRunning;
    private UpdateOption currentWatchdogUpdateOption;
    private long currentWatchdogInterval;
    private Supplier<LogicalCondition> currentWatchdogSupplier;
    
    // Other implementation details...
}
```

## Key Features

### Dual Condition Hierarchy System

The `ConditionManager` maintains two separate hierarchical structures:

1. **Plugin Conditions**: Defined programmatically by the plugin through the `SchedulablePlugin` interface
   - Controlled by the plugin developer
   - Typically express the plugin's business logic requirements
   - Cannot be modified by the user through the UI

2. **User Conditions**: Defined by the end-user through configuration
   - Controlled by the user
   - Express user preferences about when the plugin should run
   - Can be modified through the UI

When evaluating the complete condition set, the plugin conditions and user conditions are combined using configurable logic (typically AND):

```
Final Condition = Plugin Conditions AND User Conditions
```

This means both sets of conditions must be satisfied for the final result to be satisfied, giving both the developer and user appropriate control.

### Hierarchical Logical Structure

Each condition hierarchy can contain complex nested structures of logical operators (AND, OR, NOT) and various condition types:

```
UserLogicalCondition (AND)
├── TimeWindowCondition
├── InventoryItemCountCondition
└── OrCondition
    ├── SkillLevelCondition
    └── PlayerInAreaCondition
```

This allows for sophisticated condition combinations like:
- "Execute only between 8pm-10pm AND when inventory isn't full OR when in the mining area"

### Logic Types

The `ConditionManager` supports two primary logic types:

1. **Require All**: All conditions must be met (AND logic)
   ```java
   conditionManager.setRequireAll(); // Use AND logic
   ```

2. **Require Any**: Any condition can be met (OR logic)
   ```java
   conditionManager.setRequireAny(); // Use OR logic
   ```

### Condition Management Methods

The class provides various methods to manipulate and query its condition structures:

```java
// Add a user condition
public void addUserCondition(Condition condition) {
    ensureUserLogicalExists();
    userLogicalCondition.addCondition(condition);
}

// Remove a condition from both hierarchies
public boolean removeCondition(Condition condition) {
    boolean removed = false;
    if (userLogicalCondition != null) {
        removed |= userLogicalCondition.removeCondition(condition);
    }
    if (pluginCondition != null) {
        removed |= pluginCondition.removeCondition(condition);
    }
    return removed;
}

// Check if a condition exists in either hierarchy
public boolean containsCondition(Condition condition) {
    ensureUserLogicalExists();
    // Check user conditions
    if (userLogicalCondition.contains(condition)) {
        return true;
    }
    // Check plugin conditions
    return pluginCondition != null && pluginCondition.contains(condition);
}
```

### Condition Evaluation

The `ConditionManager` provides methods to evaluate whether its conditions are satisfied:

```java
// Check if all conditions (both plugin and user) are met
public boolean areAllConditionsMet() {
    // Check if plugin conditions are met (or if none exist)
    boolean pluginConditionsMet = !hasPluginConditions() || arePluginConditionsMet();
    
    // Check if user conditions are met (or if none exist)
    boolean userConditionsMet = !hasUserConditions() || areUserConditionsMet();
    
    // Both must be satisfied
    return pluginConditionsMet && userConditionsMet;
}

// Check only plugin-defined conditions
public boolean arePluginConditionsMet() {
    if (pluginCondition == null || pluginCondition.getConditions().isEmpty()) {
        return true; // No plugin conditions means this requirement is satisfied
    }
    return pluginCondition.isSatisfied();
}

// Check only user-defined conditions
public boolean areUserConditionsMet() {
    if (userLogicalCondition == null || userLogicalCondition.getConditions().isEmpty()) {
        return true; // No user conditions means this requirement is satisfied
    }
    return userLogicalCondition.isSatisfied();
}
```

### Condition Watchdog System

The `ConditionManager` includes a sophisticated watchdog system that periodically updates conditions from a supplier function (typically from the plugin itself):

```java
/**
 * Schedules a periodic task to update conditions from the given supplier.
 * This allows plugins to dynamically update their conditions based on changing game state.
 */
public ScheduledFuture<?> scheduleConditionWatchdog(
    Supplier<LogicalCondition> conditionSupplier,
    long checkIntervalMillis,
    UpdateOption updateOption
) {
    // Store the current settings
    this.currentWatchdogSupplier = conditionSupplier;
    this.currentWatchdogInterval = checkIntervalMillis;
    this.currentWatchdogUpdateOption = updateOption;
    
    // Create and schedule the watchdog task
    ScheduledFuture<?> future = SHARED_WATCHDOG_EXECUTOR.scheduleAtFixedRate(
        () -> {
            try {
                // Get the latest condition from the supplier
                LogicalCondition newCondition = conditionSupplier.get();
                if (newCondition != null) {
                    // Update the plugin condition with the new one
                    updatePluginCondition(newCondition, updateOption);
                }
            } catch (Exception e) {
                log.error("Error in condition watchdog", e);
            }
        },
        checkIntervalMillis, // Initial delay
        checkIntervalMillis, // Periodic interval
        TimeUnit.MILLISECONDS
    );
    
    // Track the future so it can be canceled later
    watchdogFutures.add(future);
    watchdogsRunning = true;
    
    return future;
}
```

This watchdog system enables plugins to dynamically update their conditions based on changing game state, supporting more sophisticated automation patterns.

### Condition Update Options

The watchdog system supports several update strategies for merging new conditions with existing ones:

1. **ADD_ONLY**: Only add new conditions, never remove existing ones
2. **REMOVE_ONLY**: Only remove conditions that no longer exist
3. **SYNC**: Fully synchronize the condition structure with the new one (default)
4. **REPLACE**: Replace the entire condition structure with the new one

```java
/**
 * Updates the plugin condition structure using the specified update option.
 */
public boolean updatePluginCondition(LogicalCondition newCondition, UpdateOption option) {
    if (newCondition == null) {
        return false;
    }
    
    switch (option) {
        case ADD_ONLY:
            // Only add new conditions, don't remove existing ones
            return mergeAddOnly(pluginCondition, newCondition);
        
        case REMOVE_ONLY:
            // Only remove conditions that are no longer present
            return mergeRemoveOnly(pluginCondition, newCondition);
        
        case SYNC:
            // Full synchronization - add new ones, remove old ones
            return synchronizeConditions(pluginCondition, newCondition);
        
        case REPLACE:
            // Complete replacement
            setPluginCondition(newCondition);
            return true;
            
        default:
            // Default to sync behavior
            return synchronizeConditions(pluginCondition, newCondition);
    }
}
```

### Event System Integration

The `ConditionManager` integrates with RuneLite's event system to update conditions based on in-game events:

```java
/**
 * Registers event listeners with the RuneLite event bus to receive relevant game events.
 * This enables conditions to update based on game state changes.
 */
public void registerEvents() {
    if (!eventsRegistered) {
        eventBus.register(this);
        eventsRegistered = true;
        log.debug("Registered condition event listeners");
    }
}

/**
 * Unregisters event listeners to prevent receiving events when not needed.
 */
public void unregisterEvents() {
    if (eventsRegistered) {
        eventBus.unregister(this);
        eventsRegistered = false;
        log.debug("Unregistered condition event listeners");
    }
}

/**
 * Example of an event handler that updates conditions based on game events
 */
@Subscribe
public void onGameTick(GameTick event) {
    // Update all conditions with the latest game state
    for (Condition condition : getConditions()) {
        condition.update();
    }
}
```

This event integration ensures conditions are kept up-to-date with the current game state.

### Time Condition Management

The `ConditionManager` provides special handling for time-based conditions, which have unique properties like future trigger times:

```java
/**
 * Gets the next scheduled trigger time from all time conditions.
 * This is the earliest time when any time condition would be satisfied.
 */
public Optional<ZonedDateTime> getCurrentTriggerTime() {
    List<TimeCondition> timeConditions = getAllTimeConditions();
    if (timeConditions.isEmpty()) {
        return Optional.empty();
    }
    
    // Find the earliest trigger time among all time conditions
    return timeConditions.stream()
        .map(TimeCondition::getCurrentTriggerTime)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .min(ZonedDateTime::compareTo);
}

/**
 * Checks if all time-only conditions would be satisfied, ignoring non-time conditions.
 * This is useful for diagnostic purposes to determine if time conditions are blocking execution.
 */
public boolean wouldBeTimeOnlySatisfied() {
    // Check if we have any time conditions
    List<TimeCondition> timeConditions = getAllTimeConditions();
    if (timeConditions.isEmpty()) {
        return true; // No time conditions means they're not blocking
    }
    
    // Check if all time conditions are satisfied
    return timeConditions.stream().allMatch(Condition::isSatisfied);
}
```

### Progress Tracking

The `ConditionManager` can calculate progress toward conditions being met:

```java
/**
 * Gets the overall progress percentage toward the next trigger time.
 * Useful for UI display of how close a plugin is to running.
 */
public double getProgressTowardNextTrigger() {
    // Implementation to calculate progress between 0-100%
}

/**
 * Gets the percentage of conditions that are currently satisfied.
 */
public double getFullConditionProgress() {
    List<Condition> conditions = getConditions();
    if (conditions.isEmpty()) {
        return 100.0; // No conditions means 100% done
    }
    
    // Calculate percentage of satisfied conditions
    long satisfiedCount = conditions.stream()
        .filter(Condition::isSatisfied)
        .count();
    
    return (satisfiedCount * 100.0) / conditions.size();
}
```

## Relationship with PluginScheduleEntry

Each `PluginScheduleEntry` contains two `ConditionManager` instances:

1. `startConditionManager`: Manages conditions that determine when to start the plugin
2. `stopConditionManager`: Manages conditions that determine when to stop the plugin

The `PluginScheduleEntry` uses these managers to evaluate:
- Whether the plugin is "due to run" (should start)
- Whether the plugin should stop
- The next scheduled activation time
- Progress toward starting or stopping

```java
// In PluginScheduleEntry
public boolean isDueToRun() {
    // Check if we're already running
    if (isRunning()) {
        return false;
    }
    
    // Check if start conditions are met
    return startConditionManager.areAllConditionsMet();
}

public boolean shouldStop() {
    if (!isRunning()) {
        return false;
    }
    
    // Check if stop conditions are met
    return stopConditionManager.areAllConditionsMet();
}
```

## Best Practices

When working with `ConditionManager`:

1. **Appropriate Logic Type**: Choose the appropriate logic type (AND/OR) based on your requirements
    - Use AND (requireAll) when all conditions must be satisfied
    - Use OR (requireAny) when any condition can trigger the action

2. **Performance Considerations**: Avoid excessive condition nesting and large condition trees
    - Very complex condition structures can impact performance
    - Use logical grouping to optimize evaluation

3. **Condition Registration**: Ensure conditions are registered with the appropriate manager
    - User conditions should be added via `addUserCondition()`
    - Plugin conditions should be set via `setPluginCondition()` or `updatePluginCondition()`

4. **Resource Cleanup**: Always call `close()` when the manager is no longer needed
    - This ensures watchdog tasks are properly canceled
    - Event listeners are unregistered

5. **Watchdog Usage**: Use watchdogs judiciously
    - Frequent updates can impact performance
    - Consider appropriate update intervals based on your plugin's needs

6. **Condition Synchronization**: Choose the right update option for your use case
    - `SYNC` for complete condition synchronization
    - `ADD_ONLY` to preserve existing conditions while adding new ones
    - `REPLACE` when you want to completely reset the condition structure

## Summary

The `ConditionManager` class is the sophisticated "brain" behind the Plugin Scheduler's condition evaluation system. By maintaining separate hierarchies for user and plugin conditions, it creates a powerful but flexible framework for defining when plugins should start and stop. Its integration with the RuneLite event system, watchdog capabilities, and hierarchical logical structure enable complex automation patterns that can adapt to the changing game state.
