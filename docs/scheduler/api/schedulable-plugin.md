# SchedulablePlugin Interface

## Overview

The `SchedulablePlugin` interface is a crucial component that bridges plugins with the Plugin Scheduler system. By implementing this interface, plugins can define when they should start and stop, how they should respond to scheduling events, and provide advanced control over their lifecycle within the scheduler.

## Interface Definition

```java
public interface SchedulablePlugin {
    LogicalCondition getStartCondition();
    LogicalCondition getStopCondition();
    void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event);
    
    // Optional methods with default implementations
    void onStopConditionCheck();
    void reportFinished(String reason, boolean success);
    boolean isHardStoppable();
    LockCondition getLockCondition(Condition stopConditions);
    boolean isLocked(Condition stopConditions);
    boolean lock(Condition stopConditions);
    boolean unlock(Condition stopConditions);
    Boolean toggleLock(Condition stopConditions);
}
```

## Key Methods

### Essential Methods for Implementation

#### `LogicalCondition getStopCondition()`

This method defines the conditions under which a plugin should stop running. It returns a logical condition structure (using `AndCondition`, `OrCondition`, etc.) that the scheduler evaluates.

```java
@Override
public LogicalCondition getStopCondition() {
    // Create an OR condition - stop when ANY of these conditions are met
    OrCondition orCondition = new OrCondition();
    
    // Stop after a random time between 25-30 minutes
    orCondition.addCondition(IntervalCondition.createRandomized(
        Duration.ofMinutes(25), 
        Duration.ofMinutes(30)
    ));
    
    // Stop when inventory is full
    orCondition.addCondition(new InventoryItemCountCondition(
        ItemID.ANY, 28, 
    ));
    
    return orCondition;
}
```

#### `LogicalCondition getStartCondition()`

This method defines the conditions under which a plugin is allowed to start. If it returns `null`, the plugin can start anytime. Otherwise, the scheduler will only start the plugin when the conditions are met.

```java
@Override
public LogicalCondition getStartCondition() {
    // Create a condition that only allows the plugin to start at specific banks
    OrCondition startCondition = new OrCondition();
    
    // Allow starting at Grand Exchange or Varrock West
    startCondition.addCondition(LocationCondition.atBank(BankLocation.GRAND_EXCHANGE, 5));
    startCondition.addCondition(LocationCondition.atBank(BankLocation.VARROCK_WEST, 5));
    
    return startCondition;
}
```

#### `void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event)`

This method handles soft stop requests from the scheduler. It must be implemented to ensure proper plugin shutdown when stop conditions are met.

```java
@Override
@Subscribe
public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
    if (event.getPlugin() == this) {
        // Save current state if needed
        saveCurrentState();
        
        // Clean up resources
        closeOpenInterfaces();
        
        // Schedule the actual stop on the client thread
        Microbot.getClientThread().invokeLater(() -> {
            try {                    
                Microbot.getPluginManager().setPluginEnabled(this, false);
                Microbot.getPluginManager().stopPlugin(this);
            } catch (Exception e) {
                log.error("Error stopping plugin", e);
            }
        });
    }
}
```

### Optional Methods with Default Implementations

#### `void onStopConditionCheck()`

This method is called periodically by the scheduler when conditions are being evaluated. It allows plugins to update any dynamic condition state before evaluation.

```java
@Override
public void onStopConditionCheck() {
    // Update resource counts or other dynamic data used by conditions
    updateItemCounts();
    checkProgressMetrics();
}
```

#### `void reportFinished(String reason, boolean success)`

This convenience method allows a plugin to report that it has completed its task and should be stopped.

```java
// Example usage in your plugin code
if (inventoryContainsAllRequiredItems()) {
    reportFinished("Collected all required items", true);
}

// Or for an error condition
if (noTargetsFoundAfterTimeout()) {
    reportFinished("No valid targets found after timeout", false);
}
```

#### `boolean isHardStoppable()`

This method determines if the plugin can be forcibly terminated if it does not respond to a soft stop request.

```java
@Override
public boolean isHardStoppable() {
    // Allow hard stops if not in a critical section
    return !isInCriticalOperation();
}
```

## Lock Condition Support

The SchedulablePlugin interface includes several methods to manage a special `LockCondition` that can prevent a plugin from being stopped during critical operations.

### `LockCondition getLockCondition(Condition stopConditions)`

This method identifies the lock condition within the stop condition structure. The default implementation recursively searches for a `LockCondition` instance.

### Managing the Lock

```java
// In your plugin implementation
private void performBankingOperation() {
    // Lock to prevent interruption during banking
    lock(getStopCondition());
    
    try {
        // Banking operations here...
        openBank();
        depositItems();
        withdrawItems();
    } 
    finally {
        // Always unlock when done, even if exceptions occur
        unlock(getStopCondition());
    }
}
```

## Best Practices

### 1. Include a Lock Condition

Always include a lock condition in your stop condition structure if your plugin has critical operations:

```java
@Override
public LogicalCondition getStopCondition() {
    // Create core stop conditions
    OrCondition orCondition = new OrCondition();
    // Add various stop conditions...
    
    // Create lock condition
    LockCondition lockCondition = new LockCondition("Critical operation in progress");
    
    // Combine with AND logic so the plugin only stops when not locked
    AndCondition andCondition = new AndCondition();
    andCondition.addCondition(orCondition);
    andCondition.addCondition(lockCondition);
    return andCondition;
}
```

### 2. Use try-finally with Locks

Always use a try-finally block when locking to ensure the lock is released:

```java
public void performCriticalOperation() {
    lock(getStopCondition());
    try {
        // Critical operation here
    } 
    finally {
        unlock(getStopCondition());
    }
}
```

### 3. Keep onStopConditionCheck Light

The `onStopConditionCheck` method is called frequently, so keep it lightweight:

```java
@Override
public void onStopConditionCheck() {
    // Quick updates only, avoid heavy computation or network calls
    this.currentItemCount = countItems();
}
```

### 4. Use Randomized Conditions

Create randomized conditions to make bot behavior less predictable:

```java
// Instead of fixed time
IntervalCondition.createRandomized(
    Duration.ofMinutes(25), 
    Duration.ofMinutes(30)
);

// Instead of fixed item counts
new InventoryItemCountCondition(
    ItemID.LOBSTER, 
    Rs2Random.between(20, 25), 
    
);
```

## Integration with Plugin Lifecycle

The `SchedulablePlugin` interface integrates with the plugin's lifecycle through these events:

1. **Start**: The scheduler checks `getStartCondition()` to determine if the plugin can be started.

2. **Runtime**: The scheduler periodically calls `onStopConditionCheck()` to allow the plugin to update condition state.

3. **Stop Decision**: The scheduler evaluates the result of `getStopCondition()` to decide when to stop the plugin.

4. **Soft Stop**: When stop conditions are met, the scheduler sends a `PluginScheduleEntrySoftStopEvent` which triggers `onPluginScheduleEntrySoftStopEvent()`.

5. **Plugin Completion**: At any point, the plugin can call `reportFinished()` to indicate it has completed its task.

## Example Implementation

For a complete implementation example, see the [SchedulableExamplePlugin](../schedulable-example-plugin.md) which demonstrates all aspects of the `SchedulablePlugin` interface.

## Related Documentation

- [Plugin Schedule Entry Soft Stop Event](plugin-schedule-entry-soft-stop-event.md)
- [Plugin Schedule Entry Finished Event](plugin-schedule-entry-finished-event.md)