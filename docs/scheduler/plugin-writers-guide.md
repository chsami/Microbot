# Plugin Writer's Guide for the Scheduler Infrastructure

## Introduction

This guide provides comprehensive information for plugin developers who want to make their plugins compatible with the Plugin Scheduler system. By implementing the `SchedulablePlugin` interface, your plugin can take advantage of sophisticated scheduling capabilities, including condition-based starting and stopping,5. **Document Conditions**: Make sure your condition implementations have clear descriptions that explain what they do.

6. **Test Thoroughly**: Test your plugin with the scheduler under various scenarios to ensure it behaves as expected.

7. **Use LockCondition for Critical Operations**: Always protect critical operations with a LockCondition, especially in combat contexts. See [Combat Lock Examples](combat-lock-examples.md) for detailed patterns used in bossing plugins.

## Example Implementation: GotrPluginrity-based execution, and integration with the scheduler's user interface.

## Understanding the SchedulablePlugin Interface

The `SchedulablePlugin` interface is the cornerstone of the scheduler infrastructure. It defines the contract that plugins must follow to work with the scheduler system.

### Core Methods

```java
public interface SchedulablePlugin {
    // Required methods
    LogicalCondition getStartCondition();
    LogicalCondition getStopCondition();
    void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event);
    
    // Optional methods with default implementations
    void onStopConditionCheck();
    void reportFinished(String reason, boolean success);
    boolean allowHardStop();
    ConfigDescriptor getConfigDescriptor();
    // Lock-related methods
    boolean isLocked(Condition stopConditions);
    boolean lock(Condition stopConditions);
    boolean unlock(Condition stopConditions);
    Boolean toggleLock(Condition stopConditions);
}
```

Each method plays a specific role in how your plugin interacts with the scheduler:

1. **getStartCondition()**: Defines when your plugin is eligible to start.
2. **getStopCondition()**: Defines when your plugin should terminate.
3. **onPluginScheduleEntrySoftStopEvent()**: Handles graceful shutdown requests from the scheduler.
4. **onStopConditionCheck()**: Hook for updating condition state before evaluation.
5. **reportFinished()**: Allows the plugin to self-report task completion.
6. **allowHardStop()**: Indicates if the plugin can be forcibly terminated.
7. **Lock methods**: Prevent the plugin from being stopped during critical operations.
8. **getConfigDescriptor()**: Provides the scheduler with configuration information.

## Step-by-Step Implementation Guide

### Step 1: Implement the SchedulablePlugin Interface

```java
@PluginDescriptor(
    name = "My Schedulable Plugin",
    description = "A plugin that works with the scheduler",
    tags = {"microbot", "scheduler"},
    enabledByDefault = false    
)
@Slf4j
public class MyPlugin extends Plugin implements SchedulablePlugin {
    // Plugin implementation...
}
```

### Step 2: Define Stop Conditions

The stop condition determines when your plugin should terminate. This is a required implementation:

```java
@Override
public LogicalCondition getStopCondition() {
    // Create a logical condition structure for when the plugin should stop
    OrCondition orCondition = new OrCondition();
    
    // Create a lock condition to prevent stopping during critical operations
    LockCondition lockCondition = new LockCondition("Locked during critical operation");
    
    // Add your specific conditions
    orCondition.addCondition(new TimeCondition(30, TimeUnit.MINUTES));
    orCondition.addCondition(new InventoryFullCondition());
    
    // Combine with lock condition using AND logic
    AndCondition andCondition = new AndCondition();
    andCondition.addCondition(orCondition);
    andCondition.addCondition(lockCondition);
    
    return andCondition;
}
```

Real-world example from GotrPlugin:

```java
@Override
public LogicalCondition getStopCondition() {
    if (this.stopCondition == null) {
        this.stopCondition = createStopCondition();
    }
    return this.stopCondition;
}

private LogicalCondition createStopCondition() {
    if (this.lockCondition == null) {
        this.lockCondition = new LockCondition("Locked because the Plugin " + getName() + " is in a critical operation");
    }

    AndCondition andCondition = new AndCondition();
    andCondition.addCondition(lockCondition);
    return andCondition;
}
```

The GotrPlugin example shows a minimal implementation that only uses a lock condition. This is because the Guardians of the Rift minigame has its own natural start and end points, and the plugin uses the lock condition to prevent the scheduler from stopping it during an active game.

### Step 3: Define Start Conditions (Optional)

If you want to restrict when your plugin can start, implement the `getStartCondition()` method:

```java
@Override
public LogicalCondition getStartCondition() {
    // Create a logical condition for start conditions
    OrCondition startCondition = new OrCondition();
    
    // Add conditions based on your requirements
    startCondition.addCondition(new LocationCondition(
        "Grand Exchange", 
        20
    ));
    
    return startCondition;
}
```

If you don't need specific start conditions (the plugin can start anytime), you can use the default implementation which returns a simple `AndCondition`.

### Step 4: Implement the Soft Stop Handler

The soft stop handler is essential for graceful shutdown. It's triggered when the scheduler determines that your plugin should stop:

```java
@Override
@Subscribe
public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
    if (event.getPlugin() == this) {
        log.info("Scheduler requesting plugin shutdown");
        
        // Perform any necessary cleanup
        saveState();
        
        // Schedule the actual stop on the client thread
        Microbot.getClientThread().invokeLater(() -> {
            Microbot.stopPlugin(this);
            return true;
        });
    }
}
```

Real-world example from GotrPlugin:

```java
@Subscribe
public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
    if (event.getPlugin() == this) {
        Microbot.log("Scheduler about to turn off Guardians of the Rift");

        if (exitScheduledFuture != null) {     
            return; // Exit task is already scheduled           
        }

        exitScheduledFuture = exitExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (lockCondition != null && lockCondition.isLocked()) {
                    Microbot.log("Exiting GOTR - waiting for the game to end");
                    sleep(10000);
                    return;
                }
                gotrScript.shutdown();
                sleepUntil(() -> !gotrScript.isRunning(), 10000);
                GotrScript.leaveMinigame();

                Microbot.log("Successfully exited GOTR - stopping plugin");
                Microbot.getClientThread().invokeLater(() -> {
                    Microbot.stopPlugin(this); 
                    return true;
                });
            } catch (Exception ex) {
                Microbot.log("Error during safe exit: " + ex.getMessage());
                Microbot.getClientThread().invokeLater(() -> {
                    Microbot.stopPlugin(this); 
                    return true;
                });
            }
        }, 0, 500, TimeUnit.SECONDS);
    }
}
```

The GotrPlugin example demonstrates a more sophisticated approach:

1. It schedules a periodic check to see if it's safe to exit
2. It respects the lock condition to avoid stopping during an active game
3. It performs proper cleanup with `gotrScript.shutdown()`
4. It actively leaves the minigame area before stopping
5. It handles exceptions gracefully

### Step 5: Using the Lock Condition

The lock condition is a powerful feature that prevents your plugin from being stopped during critical operations:

```java
// Creating the lock condition
this.lockCondition = new LockCondition("Locked during critical operation");

// Locking before a critical operation
lockCondition.lock();
try {
    // Perform critical operation that shouldn't be interrupted
    performBankTransaction();
} finally {
    // Always unlock when the critical operation is complete
    lockCondition.unlock();
}
```

Real-world example from GotrPlugin:

```java
// During chat message handling
if (msg.contains("The rift becomes active!")) {
    if (Microbot.isPluginEnabled(BreakHandlerPlugin.class)) {
        BreakHandlerScript.setLockState(true);
    }
    GotrScript.nextGameStart = Optional.empty();
    GotrScript.timeSincePortal = Optional.of(Instant.now());
    GotrScript.isFirstPortal = true;
    GotrScript.state = GotrState.ENTER_GAME;
    if (lockCondition != null) {
        lockCondition.lock();
    }
}
else if (msg.toLowerCase().contains("closed the rift!") || msg.toLowerCase().contains("The great guardian was defeated!")) {
    if (Microbot.isPluginEnabled(BreakHandlerPlugin.class)) {
        Global.sleep(Rs2Random.randomGaussian(2000, 300));
        BreakHandlerScript.setLockState(false);
    }
    if (lockCondition != null) {
        lockCondition.unlock();
    }
    GotrScript.shouldMineGuardianRemains = true;
}
```

The GotrPlugin example shows how to:

1. Lock the plugin when the GOTR minigame starts
2. Unlock it when the minigame ends
3. Integrate with other systems like the break handler

### Step 6: Reporting Task Completion

If your plugin can determine when it has completed its task, it should report this to the scheduler:

```java
// Report successful completion
reportFinished("Task completed successfully", true);

// Report unsuccessful completion
reportFinished("Failed to complete task: insufficient resources", false);
```

The scheduler will handle this report and update the plugin's status accordingly.

### Step 7: Providing Configuration Information

If your plugin has configurable options that should be accessible from the scheduler interface, implement the `getConfigDescriptor()` method:

```java
@Override
public ConfigDescriptor getConfigDescriptor() {
    if (Microbot.getConfigManager() == null) {
        return null;
    }
    MyPluginConfig conf = Microbot.getConfigManager().getConfig(MyPluginConfig.class);
    return Microbot.getConfigManager().getConfigDescriptor(conf);
}
```

Real-world example from GotrPlugin:

```java
@Override
public ConfigDescriptor getConfigDescriptor() {
    if (Microbot.getConfigManager() == null) {
        return null;
    }
    GotrConfig conf = Microbot.getConfigManager().getConfig(GotrConfig.class);
    return Microbot.getConfigManager().getConfigDescriptor(conf);
}
```

## Condition Types and Logical Structures

### Common Condition Types

The scheduler provides various condition types for different scenarios:

1. **Time Conditions**:
   - `TimeCondition`: Stops after a specified duration
   - `IntervalCondition`: Runs at specific intervals
   - `TimeWindowCondition`: Runs within specific time windows
   - `DayOfWeekCondition`: Runs on specific days of the week
   - `SingleTriggerTimeCondition`: Runs once at a specific time

2. **Resource Conditions**:
   - `InventoryItemCountCondition`: Stops when inventory contains a specific number of items
   - `BankItemCountCondition`: Stops based on bank contents
   - `GatheredResourceCondition`: Tracks gathered resources
   - `LootItemCondition`: Tracks looted items
   - `ProcessItemCondition`: Tracks item processing (crafting, smithing, etc.)

3. **Location Conditions**:
   - `AreaCondition`: Checks if player is in a specific area
   - `RegionCondition`: Checks if player is in a specific region
   - `PositionCondition`: Checks if player is at a specific position

4. **Skill Conditions**:
   - `SkillLevelCondition`: Stops when a skill reaches a level
   - `SkillXpCondition`: Stops after gaining a certain amount of XP

5. **NPC Conditions**:
   - `NpcKillCountCondition`: Stops after killing a number of NPCs

6. **Varbit Conditions**:
   - `VarbitCondition`: Tracks game state variables

7. **Logical Conditions**:
   - `AndCondition`: Combines conditions with AND logic
   - `OrCondition`: Combines conditions with OR logic
   - `NotCondition`: Inverts a condition
   - `LockCondition`: Special condition for preventing plugin termination

### Creating Logical Structures

Conditions can be combined using logical operators:

1. **AND Logic**: All conditions must be satisfied

```java
AndCondition andCondition = new AndCondition();
andCondition.addCondition(conditionA);
andCondition.addCondition(conditionB);
```

2. **OR Logic**: Any condition can be satisfied

```java
OrCondition orCondition = new OrCondition();
orCondition.addCondition(conditionA);
orCondition.addCondition(conditionB);
```

3. **NOT Logic**: Inverts a condition

```java
NotCondition notCondition = new NotCondition(conditionA);
```

4. **Complex Logic**: Conditions can be nested

```java
// (A OR B) AND C
AndCondition root = new AndCondition();
OrCondition orGroup = new OrCondition();

orGroup.addCondition(conditionA);
orGroup.addCondition(conditionB);

root.addCondition(orGroup);
root.addCondition(conditionC);
```

## Best Practices

1. **Always Use Lock Conditions**: Include a lock condition in your stop condition structure to prevent your plugin from being stopped during critical operations.

2. **Handle Soft Stops Gracefully**: Implement proper cleanup in your `onPluginScheduleEntrySoftStopEvent` method.

3. **Use the Client Thread**: Always stop your plugin on the client thread to avoid synchronization issues.

4. **Report Completion**: Use `reportFinished()` when your plugin completes its task, rather than letting it run until stop conditions are met.

5. **Provide Clear Configuration**: Implement `getConfigDescriptor()` to make your plugin's configuration accessible from the scheduler.

6. **Document Conditions**: Make sure your condition implementations have clear descriptions that explain what they do.

7. **Test Thoroughly**: Test your plugin with the scheduler under various scenarios to ensure it behaves as expected.

## Example Implementation: GotrPlugin

The Guardians of the Rift plugin (GotrPlugin) is a real-world example of a schedulable plugin. It demonstrates several best practices:

1. **Minimal Stop Condition**: Uses only a lock condition since the minigame has natural start and end points.

2. **Lock Management**: Locks the plugin during active games and unlocks it when games end.

3. **Safe Shutdown**: Implements a sophisticated shutdown procedure that:
   - Checks if it's safe to exit
   - Completes necessary cleanup
   - Leaves the minigame area
   - Handles exceptions

4. **Integration with Other Systems**: Works with the break handler to coordinate breaks.

5. **Config Descriptor**: Provides configuration information to the scheduler.

By following these patterns, your plugin can work seamlessly with the scheduler system, providing a better user experience and more reliable operation.

## Reference

For more information, see the following resources:

- [Scheduler User Guide](user-guide.md): How to use the scheduler from a user perspective
- [Defining Conditions](defining-conditions.md): Detailed information on condition types
- [API Documentation: SchedulablePlugin](api/schedulable-plugin.md): Full API reference for the SchedulablePlugin interface
- [Combat Lock Examples](combat-lock-examples.md): Examples of using LockCondition in combat and bossing plugins

## Location-Based Conditions

The scheduler provides robust location-based conditions that can be used to start or stop plugins based on the player's position in the game world. This section covers how to leverage these powerful tools.

### Working with LocationCondition Utilities

The `LocationCondition` abstract class provides several utility methods for creating location-based conditions:

#### 1. Bank Location Conditions

You can create conditions that trigger when the player is near a specific bank:

```java
// Create a condition that is satisfied when the player is at the Grand Exchange
Condition atBankCondition = LocationCondition.atBank(BankLocation.GRAND_EXCHANGE, 20);

// This condition will be satisfied when the player is within 20 tiles of the GE bank
```

#### 2. Working with Multiple Points

Sometimes, you want a condition that triggers at any of several points:

```java
// Define multiple points where the player may be
WorldPoint[] importantLocations = new WorldPoint[] {
    new WorldPoint(3222, 3218, 0),  // Lumbridge
    new WorldPoint(3165, 3485, 0),  // Grand Exchange
    new WorldPoint(2964, 3378, 0)   // Falador
};

// Create a condition that is satisfied at any of these points
Condition atAnyPointCondition = LocationCondition.atAnyPoint(
    "At a major city", 
    importantLocations, 
    10  // Within 10 tiles of any point
);
```

#### 3. Creating Area Conditions

For rectangular areas, you can use the `createArea` utility method:

```java
// Create a condition for a rectangular area centered around a point
WorldPoint center = new WorldPoint(3222, 3218, 0);  // Lumbridge
AreaCondition lumbridgeAreaCondition = LocationCondition.createArea(
    "Lumbridge Center", 
    center, 
    20,  // Width in tiles
    20   // Height in tiles
);
```

#### 4. Working with Multiple Areas

You can also create conditions that check if the player is in any of several areas:

```java
// Define multiple areas
WorldArea[] trainingAreas = new WorldArea[] {
    new WorldArea(3207, 3206, 30, 30, 0),  // Lumbridge cows
    new WorldArea(3244, 3295, 20, 15, 0)   // Varrock east mine
};

// Create a condition that is satisfied in any of these areas
Condition inAnyAreaCondition = LocationCondition.inAnyArea(
    "At training location", 
    trainingAreas
);
```

Alternatively, you can define areas using coordinate arrays:

```java
// Define areas using coordinate arrays [x1, y1, x2, y2, plane]
int[][] areaDefs = new int[][] {
    {3207, 3206, 3237, 3236, 0},  // Lumbridge cows
    {3244, 3295, 3264, 3310, 0}   // Varrock east mine
};

// Create a condition that is satisfied in any of these areas
Condition inAnyAreaCondition = LocationCondition.inAnyArea(
    "At training location", 
    areaDefs
);
```

### Practical Use Cases for Location Conditions

Location conditions can serve various purposes:

1. **Start Conditions**: Define where a plugin can start
   ```java
   @Override
   public LogicalCondition getStartCondition() {
       // Only start the woodcutting plugin when in a woodcutting area
       return (LogicalCondition) LocationCondition.inAnyArea(
           "At woodcutting location",
           new int[][] {
               {3163, 3415, 3173, 3425, 0},  // Varrock west trees
               {3040, 3308, 3055, 3323, 0}   // Falador trees
           }
       );
   }
   ```

2. **Stop Conditions**: Define where a plugin should stop
   ```java
   OrCondition stopCondition = new OrCondition();
   
   // Stop if inventory is full OR player leaves the mining area
   stopCondition.addCondition(new InventoryFullCondition());
   
   NotCondition notInMiningArea = new NotCondition(
       LocationCondition.inAnyArea(
           "Mining area",
           new int[][] {{3027, 9733, 3055, 9747, 0}}  // Mines
       )
   );
   stopCondition.addCondition(notInMiningArea);
   ```

3. **Safety Checks**: Prevent dangerous activities
   ```java
   // Don't allow stop in dangerous areas
   LockCondition lockCondition = new LockCondition("In wilderness");
   
   // Lock when entering wilderness
   if (LocationCondition.inAnyArea(
           "Wilderness", 
           new int[][] {{3008, 3525, 3071, 3589, 0}}
       ).isSatisfied()) {
       lockCondition.lock();
   }
   ```



4. **Arceuus script**: using condition-based locking :
```java
private LogicalCondition createStopCondition() {
    // Import required classes
    import java.util.Arrays;
    import java.util.List;
  
    
    // Create location conditions for Dense Runestone and Blood Altar
    // NOTE: Update these coordinates with the actual in-game coordinates
    LocationCondition atDenseRunestone = new AreaCondition("At Dense Runestone", 1760, 3850, 1780, 3870, 0);
    LogicalCondition  notAtDenseRunestone = new NotCondition(atDenseRunestone);
    LocationCondition atBloodAltar = new AreaCondition("At Blood Altar", 1710, 3820, 1730, 3840, 0);
    
    
    
    // Option 1: Using createAndCondition helper method for more readable code
    // Create a list of items to check (both Dark essence types)
    List<String> darkEssenceItems = Arrays.asList("Dark essence fragments", "Dark essence block");
    
    // Create an AND condition that checks if both items have count >=1
    // Each condition is satisfied when the item count is >=1 (using NOT to invert the default behavior item count <1)
    LogicalCondition noDarkEssence = new AndCondition();
    for (String itemName : darkEssenceItems) {
        NotCondition noItem = new NotCondition(
            new InventoryItemCountCondition(itemName, 1, true)
        );
        noDarkEssence.addCondition(noItem);
    }
    
    // Option 2: Using a single regex pattern to match both item types (more efficient)
    // This creates a condition that checks if there are any Dark essence items (fragments or blocks), with count >=1, count all matching items
    InventoryItemCountCondition hasAnyDarkEssence = new InventoryItemCountCondition(
        "Dark essence.*", 1, true); // Regex pattern to match both item types
    
    // Invert the condition to check if there are NO dark essence items in the inventory
    NotCondition noDarkEssenceItems = new NotCondition(hasAnyDarkEssence);
    
    // Use an ANDCondition for being at the Blood Altar with any dark essence item
    AndCondition atBloodAltarWithEssence = new AndCondition();
    atBloodAltarWithNoEssence.addCondition(atBloodAltar);
    atBloodAltarWithNoEssence.addCondition(hasAnyDarkEssence); // Using Option 2: the regex pattern approach
    // we can invert it, so the condition is true if we are not at the blood alter or we dont have any dark essence items
    LogicalCondition notAtBloodAltarOrNoDarkEssenceItems = new NotCondition(atBloodAltarWithEssence);
    // Alternatively, using Option 3: createOrCondition helper for multiple items with count>=1
    List<String> darkEssenceTypes = Arrays.asList("Dark essence fragments", "Dark essence block");
    LogicalCondition hasDarkEssenceAlt =  InventoryItemCountCondition.createOrCondition(
        darkEssenceTypes, 1, 1, true);
    
      
    // Create the stop condition, so we can stop when we are not at the runestone and (we are not at Blood Altar or we have no essences
    LocationCondition logicalStopCondition = new AndCondition();
    logicalStopCondition.addCondition(notAtDenseRunestone);
    logicalStopCondition.addCondition(notAtBloodAltarOrNoDarkEssenceItems);
    return logicalStopCondition;
}
```