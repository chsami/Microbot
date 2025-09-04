# Pre and Post Schedule Tasks Infrastructure

## Overview

The Pre and Post Schedule Tasks infrastructure provides a standardized way for Microbot plugins to handle preparation and cleanup when operating under scheduler control. This system ensures consistent resource management, proper plugin lifecycle handling, and graceful startup/shutdown procedures.

## Architecture

### Abstract Base Class: `AbstractPrePostScheduleTasks`

The `AbstractPrePostScheduleTasks` class provides:

- **Executor Service Management**: Automatic creation and lifecycle management of thread pools for pre and post tasks
- **CompletableFuture Handling**: Asynchronous task execution with timeout support and proper error handling
- **Resource Cleanup**: AutoCloseable implementation ensures proper shutdown of all resources
- **Common Error Patterns**: Standardized logging and error handling across all implementations
- **Thread Safety**: Safe concurrent execution and cancellation of tasks

### Key Features

1. **Asynchronous Execution**: Tasks run on separate threads to avoid blocking the main plugin thread
2. **Timeout Support**: Configurable timeouts with graceful handling of timeout scenarios
3. **Lock Integration**: Support for LockCondition to prevent interruption during critical operations
4. **Callback Support**: Execute callbacks when pre-tasks complete successfully
5. **Automatic Cleanup**: Resources are automatically cleaned up on plugin shutdown

## Implementation Guide

### Step 1: Create Your Task Implementation

Extend `AbstractPrePostScheduleTasks` and implement the three required abstract methods:

```java
public class YourPluginPrePostScheduleTasks extends AbstractPrePostScheduleTasks {
    
    public YourPluginPrePostScheduleTasks(SchedulablePlugin plugin) {
        super(plugin);
        // Initialize plugin-specific requirements or dependencies
    }
    
    @Override
    protected boolean executePreScheduleTask(LockCondition lockCondition) {
        // Add your plugin's preparation logic here
        // Return true if successful, false otherwise
    }
    
    @Override
    protected boolean executePostScheduleTask(LockCondition lockCondition) {
        // Add your plugin's cleanup logic here
        // Return true if successful, false otherwise
    }
    
    @Override
    protected boolean isScheduleMode() {
        // Check your plugin's configuration to determine if running under scheduler
        Boolean scheduleMode = Microbot.getConfigManager().getConfiguration(
            "YourPluginConfig", "scheduleMode", Boolean.class);
        return scheduleMode != null && scheduleMode;
    }
}
```

### Step 2: Integrate with Your Plugin

In your plugin class that implements `SchedulablePlugin`:

```java
@PluginDescriptor(name = "Your Plugin")
public class YourPlugin extends Plugin implements SchedulablePlugin {
    
    private YourPluginPrePostScheduleTasks prePostTasks;
    private LockCondition lockCondition;
    
    @Override
    protected void startUp() {
        // Initialize the task manager
        prePostTasks = new YourPluginPrePostScheduleTasks(this);
        
        // Execute pre-schedule tasks with callback
        prePostTasks.executePreScheduleTasks(() -> {
            // This callback runs when preparation is complete
            yourScript.run(config);
        });
    }
    
    @Override
    protected void shutDown() {
        // Clean up resources
        if (prePostTasks != null) {
            prePostTasks.close();
        }
    }
    
    @Subscribe
    public void onPluginScheduleEntryPostScheduleTaskEvent(PluginScheduleEntryPostScheduleTaskEvent event) {
        if (event.getPlugin() == this && prePostTasks != null) {
            if (lockCondition != null && lockCondition.isLocked()) {
                return; // respect critical section
            }
            // Execute post-schedule cleanup
            prePostTasks.executePostScheduleTasks(lockCondition);
        }
    }
    
    @Override
    public LogicalCondition getStopCondition() {
        if (lockCondition == null) {
            lockCondition = new LockCondition("Plugin locked during critical operation", false,true); //ensure unlock on shutdown of the plugin !
        }
        AndCondition condition = new AndCondition();
        condition.addCondition(lockCondition);
        return condition;
    }
}
```

### Step 3: Common Patterns

#### Banking and Equipment Management

```java
private boolean prepareOptimalSetup() {
    if (!Rs2Bank.openBank()) {
        return false;
    }
    
    // Deposit current items
    Rs2Bank.depositAll();
    Rs2Bank.depositEquipment();
    
    // Withdraw required items
    Rs2Bank.withdrawOne(ItemID.BRONZE_PICKAXE);
    Rs2Bank.withdrawX(ItemID.SALMON, 10);
    
    Rs2Bank.closeBank();
    return true;
}

private boolean bankAllItems() {
    if (!Rs2Bank.openBank()) {
        return false;
    }
    
    Rs2Bank.depositAll();
    Rs2Bank.depositEquipment();
    Rs2Bank.closeBank();
    return true;
}
```

#### Walking to Locations

```java
private boolean walkToLocation() {
    if (Rs2Bank.isNearBank(BankLocation.GRAND_EXCHANGE, 6)) {
        return true;
    }
    
    boolean walkResult = Rs2Walker.walkWithBankedTransports(
        BankLocation.GRAND_EXCHANGE.getWorldPoint(), 
        true,   // Use bank items for transportation
        false   // Don't force banking route
    );
    
    return sleepUntil(() -> Rs2Bank.isNearBank(BankLocation.GRAND_EXCHANGE, 6), 30000);
}
```

#### Lock Management

```java
@Override
protected boolean executeCustomPreScheduleTask(CompletableFuture<Boolean> preScheduledFuture, LockCondition lockCondition) {
    if (lockCondition != null) {
        lockCondition.lock(); // Prevent interruption during setup
    }
    
    try {
        // Perform critical setup operations
        return performSetup();
        
    } finally {
        if (lockCondition != null) {
            lockCondition.unlock();
        }
    }
}
```

## Method Reference

### AbstractPrePostScheduleTasks Methods

#### Public Methods

- `executePreScheduleTasks(Runnable callback)` - Execute pre-tasks with callback
- `executePreScheduleTasks(Runnable callback, int timeout, TimeUnit timeUnit)` - Execute pre-tasks with timeout
- `executePostScheduleTasks(LockCondition lockCondition)` - Execute post-tasks
- `executePostScheduleTasks(LockCondition lockCondition, int timeout, TimeUnit timeUnit)` - Execute post-tasks with timeout
- `isRunning()` - Check if any tasks are currently executing
- `close()` - Clean up all resources and cancel running tasks
- `shutdown()` - Alias for close()

#### Abstract Methods (Must Implement)

- `executeCustomPreScheduleTask(CompletableFuture<Boolean> preScheduledFuture, LockCondition lockCondition)` - Plugin-specific preparation logic
- `executeCustomPostScheduleTask(CompletableFuture<Boolean> postScheduledFuture, LockCondition lockCondition)` - Plugin-specific cleanup logic
- `getPrePostScheduleRequirements()` - Return the requirements instance for this plugin

## Error Handling

The infrastructure provides comprehensive error handling through centralized try-catch blocks in the base class:

1. **Centralized Exception Handling**: All exceptions from custom task methods are caught and logged by the parent class
2. **Timeout Handling**: Tasks that exceed their timeout are automatically cancelled
3. **Proper Error Reporting**: Failures are reported to the scheduler with appropriate ExecutionResult values
4. **Resource Cleanup**: Resources are always cleaned up, even when errors occur
5. **No Redundant Error Handling**: Custom task methods should NOT include try-catch blocks - let errors bubble up to the centralized handlers

## TODO Items for Future Enhancement

The following TODO items are included in the abstract class for future improvements:

- **Configuration for default timeout values**: Allow global configuration of timeout defaults
- **Metrics collection**: Track task execution times and success rates for monitoring
- **Retry mechanism**: Implement exponential backoff for failed tasks
- **Task priority levels**: Support for critical vs optional task classification

## Integration with Requirements System

The task infrastructure works seamlessly with the existing requirements system located in:
`runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/`

You can use requirement classes like `ItemRequirement`, `RequirementCollection`, etc. within your task implementations for standardized equipment and item management.

## Examples

See the following implementations for reference:

1. **GotrPrePostScheduleTasks**: Complete implementation for Guardians of the Rift plugin
2. **ExamplePrePostScheduleTasks**: Template implementation showing common patterns

## Best Practices

1. **Always check isScheduleMode()**: Only perform schedule-specific logic when actually running under scheduler
2. **Use lock conditions**: Prevent interruption during critical operations like minigame participation
3. **Handle failures gracefully**: Return false from task methods to indicate failure - exceptions will be handled by the parent class
4. **Log appropriately**: Use structured logging with appropriate log levels
5. **Avoid redundant error handling**: Do not add try-catch blocks in custom task methods - the parent class provides centralized error handling
6. **Clean up resources**: Always implement proper resource cleanup in your task methods
7. **Test both modes**: Ensure your plugin works both with and without scheduler control


<<<<<<< HEAD
Design note: 
Further improvemnts: We should improve the requirement fulfillment flow and clarify ItemRequirement semantics. Equipment requirements target a specific EquipmentInventorySlot; inventory requirements should not. To avoid overloading a single type with sentinel values (e.g., null slot), consider:
=======
Design note: We should improve the requirement fulfillment flow and clarify ItemRequirement semantics. Equipment requirements target a specific EquipmentInventorySlot; inventory requirements should not. To avoid overloading a single type with sentinel values (e.g., null slot), consider:
>>>>>>> ff36783985 ((feat,bugfixes,core): cache architecture overhaul and comprehensive pre/post schedule tasks system)
- Making ItemRequirement an abstract base type.
- Introduce EquipmentRequirement (has a non-null EquipmentInventorySlot).
- Introduce InventoryRequirement (no slot; optional quantity/stack rules).
This separation will eliminate magic values, reduce null checks, and make the fulfillment process simpler and safer.