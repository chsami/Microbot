# Resource Conditions

Resource conditions in the Plugin Scheduler system allow plugins to be controlled based on the player's inventory, gathered resources, and item interactions.

## Overview

Resource conditions monitor various aspects of item and resource management in the game. They can track inventory contents, gathered resources, processed items, and loot drops, making them particularly useful for automation related to skilling, combat, and resource collection.

## Available Resource Conditions

### InventoryItemCountCondition

The `InventoryItemCountCondition` monitors the quantity of specified items in the player's inventory.

**Usage:**
```java
// Satisfied when inventory contains at least 1000 coins
InventoryItemCountCondition condition = new InventoryItemCountCondition(
    ItemID.COINS,       // Item ID to check
    1000,               // Quantity
      // Comparison operator
);
```

**Key features:**
- Monitors specific item IDs or any item (using ItemID.ANY)
- Supports various comparison types (equals, greater than, less than, etc.)
- Updates dynamically as inventory contents change
- Can track progress toward target quantities

### GatheredResourceCondition

The `GatheredResourceCondition` tracks resources that the player has gathered (like ore from mining or logs from woodcutting).

**Usage:**
```java
// Satisfied when player has gathered 100 yew logs
GatheredResourceCondition condition = new GatheredResourceCondition(
    ItemID.YEW_LOGS,    // Resource item ID
    100,                // Target quantity
    
);
```

**Key features:**
- Tracks the total amount of a resource gathered over time
- Persists count even if items are banked, dropped, or used
- Useful for long-term gathering goals
- Can be reset if needed

### ProcessItemCondition

The `ProcessItemCondition` monitors items that have been processed (like ores smelted into bars or logs made into bows).

**Usage:**
```java
// Satisfied when player has processed 50 yew logs into yew longbows
ProcessItemCondition condition = new ProcessItemCondition(
    ItemID.YEW_LOGS,    // Input item ID
    ItemID.YEW_LONGBOW, // Output item ID
    50,                 // Target quantity
    
);
```

**Key features:**
- Tracks the transformation of one item into another
- Monitors both input and output items
- Useful for crafting, smithing, and other processing skills
- Can be configured to track multiple possible outputs

### LootItemCondition

The `LootItemCondition` monitors items that have been looted from the ground.

**Usage:**
```java
// Satisfied when player has looted 10 dragon bones
LootItemCondition condition = new LootItemCondition(
    ItemID.DRAGON_BONES,    // Item ID to track
    10,                     // Target quantity
    
);
```

**Key features:**
- Specifically tracks items picked up from the ground
- Distinguishes between looted items and other inventory additions
- Useful for monster drop tracking
- Can be configured to track specific areas or with item filters

## Common Features of Resource Conditions

All resource conditions implement the `ResourceCondition` interface, which extends the base `Condition` interface and provides additional functionality:

- `getProgressPercentage()`: Returns the progress toward the condition goal as a percentage
- `reset()`: Resets the tracking counters to zero
- `getTrackedQuantity()`: Returns the current tracked quantity
- `getTargetQuantity()`: Returns the target quantity needed to satisfy the condition

## Using Resource Conditions as Start Conditions

When used as start conditions, resource conditions can trigger plugins based on inventory state or gathered resources:

```java
PluginScheduleEntry entry = new PluginScheduleEntry("MyPlugin", true);

// Start the plugin when inventory contains at least 1000 coins
entry.addStartCondition(new InventoryItemCountCondition(
    ItemID.COINS,
    1000,
    
));
```

## Using Resource Conditions as Stop Conditions

Resource conditions are particularly powerful as stop conditions for plugins:

```java
// Stop when inventory is full
entry.addStopCondition(new InventoryItemCountCondition(
    ItemID.ANY,
    28,
    
));

// OR stop when a specific goal is reached
entry.addStopCondition(new GatheredResourceCondition(
    ItemID.YEW_LOGS,
    1000,
    
));
```

## Combining with Logical Conditions

Resource conditions can be combined with logical conditions to create complex resource management rules:

```java
// Create a logical OR condition
OrCondition stopConditions = new OrCondition();

// Stop when inventory is full
stopConditions.addCondition(new InventoryItemCountCondition(
    ItemID.ANY,
    28,
    
));

// OR when the player has gathered 1000 yew logs
stopConditions.addCondition(new GatheredResourceCondition(
    ItemID.YEW_LOGS,
    1000,
    
));

// OR when the player has crafted 500 yew longbows
stopConditions.addCondition(new ProcessItemCondition(
    ItemID.YEW_LOGS,
    ItemID.YEW_LONGBOW,
    500,
    
));

// Add the combined stop conditions to the plugin schedule
entry.addStopCondition(stopConditions);
```

## Progress Tracking and Event Integration

Resource conditions integrate with the RuneLite event system to track changes in real-time:

- `ItemContainerChanged`: Updates inventory item counts
- `ItemSpawned`/`ItemDespawned`: Monitors ground items for looting
- `MenuOptionClicked`: Detects item processing actions
- `GameTick`: Periodically validates condition state