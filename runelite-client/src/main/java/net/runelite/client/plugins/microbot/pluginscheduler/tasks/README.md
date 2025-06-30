# Pre and Post Schedule Tasks Infrastructure


the  "prepareOptimalSetup" can be done at any bank ! #file:Rs2Bank.java -walk to nearst bank for example
start to implent the location Requirement, add it to PrePostScheduleRequirements.
than at to GotrPrePostScheduleRequirements
next step is than to generatlize the current #sym:executePostScheduleTask(LockCondition)  and #sym:executePostScheduleTask(LockCondition)  in #file:AbstractPrePostScheduleTasks.java  provide defualt implentationt -> try to full fill all the #file:PrePostScheduleRequirements.java a child  provides via a new function protected abstract PrePostScheduleRequirements getPrePostScheduleRequirements

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
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
        if (event.getPlugin() == this && prePostTasks != null) {
            // Execute post-schedule cleanup
            prePostTasks.executePostScheduleTasks(lockCondition);
        }
    }
    
    @Override
    public LogicalCondition getStopCondition() {
        if (lockCondition == null) {
            lockCondition = new LockCondition("Plugin locked during critical operation");
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
protected boolean executePreScheduleTask(LockCondition lockCondition) {
    try {
        if (lockCondition != null) {
            lockCondition.lock(); // Prevent interruption during setup
        }
        
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

- `executePreScheduleTask(LockCondition lockCondition)` - Plugin-specific preparation logic
- `executePostScheduleTask(LockCondition lockCondition)` - Plugin-specific cleanup logic
- `isScheduleMode()` - Determine if plugin is under scheduler control

## Error Handling

The infrastructure provides comprehensive error handling:

1. **Timeout Handling**: Tasks that exceed their timeout are automatically cancelled
2. **Exception Logging**: All exceptions are logged with appropriate detail levels
3. **Graceful Degradation**: Failed pre-tasks report plugin failure; failed post-tasks still attempt plugin shutdown
4. **Resource Cleanup**: Resources are always cleaned up, even when errors occur

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
3. **Handle failures gracefully**: Return false from task methods to indicate failure, but avoid throwing exceptions
4. **Log appropriately**: Use structured logging with appropriate log levels
5. **Clean up resources**: Always implement proper resource cleanup in your task methods
6. **Test both modes**: Ensure your plugin works both with and without scheduler control



we also have to consider better fullfillment porcces and definining #file:ItemRequirement.java becasue when a #file:ItemRequirement.java is a Equipiment req. we have the in equipment solt set. for the inventory item not. a inventroy item must not have a inventroy solt assigen but it could be an option ? should we make #file:ItemRequirement.java a abstract base class and implement a 