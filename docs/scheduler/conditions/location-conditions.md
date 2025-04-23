# Location Conditions

Location conditions in the Plugin Scheduler system allow plugins to be controlled based on the player's position in the game world.

## Overview

Location conditions monitor the player's physical location in the game, enabling plugins to respond to position-based triggers. These conditions are useful for area-specific automation, region-based task scheduling, and creating location-aware plugin behaviors.

## Available Location Conditions

### PositionCondition

The `PositionCondition` checks if the player is at a specific tile position in the game world.

**Usage:**
```java
// Satisfied when the player is at the Grand Exchange center tile
PositionCondition condition = new PositionCondition(
    new WorldPoint(3165, 3487, 0) // GE center coordinates
);
```

**Key features:**
- Checks for exact position matching
- Can include or ignore the plane/level coordinate
- Can specify a tolerance radius to create a small area around the target position
- Useful for precise location triggers

### AreaCondition

The `AreaCondition` checks if the player is within a defined area in the game world.

**Usage:**
```java
// Satisfied when the player is in the Grand Exchange area
AreaCondition condition = new AreaCondition(
    new WorldArea(3151, 3473, 30, 30, 0) // GE area
);
```

**Key features:**
- Defines rectangular areas using WorldArea
- Can cover multiple planes/levels
- Useful for monitoring presence in towns, dungeons, or training areas
- Can be inverted to check if player is outside an area

## Common Features of Location Conditions

All location conditions provide core functionality for position-based checks:

- `isSatisfied()`: Determines if the player's current position satisfies the condition
- `getDescription()`: Returns a human-readable description of the location condition
- `reset()`: Refreshes any cached location data
- Various configuration options for making the check more or less strict

## Using Location Conditions as Start Conditions

When used as start conditions, location conditions can trigger plugins when the player enters specific areas:

```java
PluginScheduleEntry entry = new PluginScheduleEntry("MyPlugin", true);

// Start the plugin when the player enters the Wilderness
entry.addStartCondition(new AreaCondition(
    new WorldArea(2944, 3520, 448, 448, 0) // Wilderness area
));
```

## Using Location Conditions as Stop Conditions

Location conditions are also useful as stop conditions to deactivate plugins when the player leaves or enters an area:

```java
// Stop the plugin when the player returns to a safe area (e.g., Lumbridge)
entry.addStopCondition(new AreaCondition(
    new WorldArea(3206, 3208, 30, 30, 0) // Lumbridge area
));
```

## Combining with Logical Conditions

Location conditions can be combined with logical conditions to create more complex location-based rules:

```java
// Create a logical NOT condition
NotCondition notInSafeArea = new NotCondition(
    new AreaCondition(new WorldArea(3206, 3208, 30, 30, 0)) // Lumbridge area
);

// Create a logical AND condition
AndCondition dangerousCondition = new AndCondition();

// Require being in the wilderness
dangerousCondition.addCondition(new AreaCondition(
    new WorldArea(2944, 3520, 448, 448, 0) // Wilderness area
));

// AND not being in a safe spot
dangerousCondition.addCondition(notInSafeArea);

// Add this combined condition as a start condition
entry.addStartCondition(dangerousCondition);
```

## Creating Complex Area Monitoring

For complex regions that cannot be represented by a single rectangle, multiple area conditions can be combined using logical OR:

```java
// Create a logical OR condition for multiple areas
OrCondition bankingAreas = new OrCondition();

// Add various bank areas
bankingAreas.addCondition(new AreaCondition(
    new WorldArea(3207, 3215, 10, 10, 0) // Lumbridge bank
));
bankingAreas.addCondition(new AreaCondition(
    new WorldArea(3180, 3433, 15, 15, 0) // Varrock West bank
));
bankingAreas.addCondition(new AreaCondition(
    new WorldArea(3251, 3420, 10, 10, 0) // Varrock East bank
));
bankingAreas.addCondition(new AreaCondition(
    new WorldArea(3088, 3240, 10, 10, 0) // Draynor bank
));

// Add this combined condition to stop a plugin when in any bank
entry.addStopCondition(bankingAreas);
```

## Event Integration

Location conditions integrate with the RuneLite event system to track changes in real-time:

- `GameTick`: Periodically checks the player's position against the condition
- Efficient position comparison to minimize performance impact