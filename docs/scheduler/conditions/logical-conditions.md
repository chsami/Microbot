# Logical Conditions

Logical conditions are a powerful feature of the Plugin Scheduler that enable the combination of other conditions using logical operators.

## Overview

Logical conditions allow for the creation of complex conditional expressions by combining simpler conditions. They are essential for creating sophisticated scheduling rules based on multiple factors.

## Available Logical Conditions

### AndCondition

The `AndCondition` requires that all of its child conditions are satisfied for the condition itself to be satisfied.

**Usage:**
```java
// Create an AND condition
AndCondition condition = new AndCondition();

// Add child conditions
condition.addCondition(new InventoryItemCountCondition(ItemID.COINS, 1000, ));
condition.addCondition(new SkillLevelCondition(Skill.ATTACK, 60, ));
```

**Key features:**
- Requires all child conditions to be satisfied
- Can contain any type of condition, including other logical conditions
- Returns the minimum progress percentage among child conditions

### OrCondition

The `OrCondition` is satisfied if any of its child conditions are satisfied.

**Usage:**
```java
// Create an OR condition
OrCondition condition = new OrCondition();

// Add child conditions
condition.addCondition(new TimeWindowCondition(LocalTime.of(9, 0), LocalTime.of(12, 0)));
condition.addCondition(new TimeWindowCondition(LocalTime.of(14, 0), LocalTime.of(17, 0)));
```

**Key features:**
- Requires at least one child condition to be satisfied
- Useful for creating alternative paths to satisfy a condition
- Returns the maximum progress percentage among child conditions

### NotCondition

The `NotCondition` inverts the result of its child condition.

**Usage:**
```java
// Create a NOT condition
NotCondition condition = new NotCondition(
    new AreaCondition(new WorldArea(3200, 3200, 50, 50, 0))
);
```

**Key features:**
- Inverts the satisfaction state of the wrapped condition
- Progress percentage is inverted (100 - child progress)
- Useful for creating negative conditions like "not in an area" or "no items in inventory"

### LockCondition

The `LockCondition` is a special logical condition that remains satisfied once it becomes satisfied, regardless of the subsequent state of its child condition.

**Usage:**
```java
// Create a lock condition that stays satisfied once the player reaches level 70
LockCondition condition = new LockCondition(
    new SkillLevelCondition(Skill.MINING, 70, )
);
```

**Key features:**
- "Locks" to true once satisfied
- Useful for one-way transitions or milestone achievements
- Can be reset if needed

## Common Features of Logical Conditions

All logical conditions implement the `LogicalCondition` interface, which extends the base `Condition` interface and provides additional functionality for managing child conditions:

- `addCondition(Condition)`: Adds a child condition
- `removeCondition(Condition)`: Removes a child condition
- `getConditions()`: Returns all child conditions
- `contains(Condition)`: Checks if a specific condition is contained in the logical structure

## Using Logical Conditions as Start Conditions

When used as start conditions, logical conditions provide complex rules for when a plugin should be activated:

```java
PluginScheduleEntry entry = new PluginScheduleEntry("MyPlugin", true);

// Create a logical structure for starting conditions
OrCondition startCondition = new OrCondition();

// Add multiple time windows
startCondition.addCondition(new TimeWindowCondition(
    LocalTime.of(9, 0),
    LocalTime.of(12, 0)
));
startCondition.addCondition(new TimeWindowCondition(
    LocalTime.of(14, 0),
    LocalTime.of(17, 0)
));

entry.addStartCondition(startCondition);
```

## Using Logical Conditions as Stop Conditions

When used as stop conditions, logical conditions define complex rules for when a plugin should be deactivated:

```java
// Create a logical structure for stop conditions
AndCondition stopCondition = new AndCondition();

// Stop when inventory is full AND player is in a safe area
stopCondition.addCondition(new InventoryItemCountCondition(
    ItemID.ANY,
    28,
    
));
stopCondition.addCondition(new AreaCondition(
    new WorldArea(3200, 3200, 50, 50, 0)
));

entry.addStopCondition(stopCondition);
```

## Creating Complex Nested Logical Structures

Logical conditions can be nested to create complex conditional expressions:

```java
// Main structure is OR
OrCondition complexCondition = new OrCondition();

// First branch: AND condition
AndCondition firstBranch = new AndCondition();
firstBranch.addCondition(new TimeWindowCondition(LocalTime.of(9, 0), LocalTime.of(17, 0)));
firstBranch.addCondition(new SkillLevelCondition(Skill.MINING, 60, ));

// Second branch: AND condition with a NOT
AndCondition secondBranch = new AndCondition();
secondBranch.addCondition(new TimeWindowCondition(LocalTime.of(20, 0), LocalTime.of(23, 59)));
secondBranch.addCondition(new NotCondition(
    new AreaCondition(new WorldArea(3200, 3200, 50, 50, 0))
));

// Add branches to main structure
complexCondition.addCondition(firstBranch);
complexCondition.addCondition(secondBranch);

// Add to schedule entry
entry.addStartCondition(complexCondition);
```

This creates a structure that means: "Run the plugin if it's between 9 AM and 5 PM AND mining level is 60+, OR if it's between 8 PM and midnight AND the player is not in the specified area."