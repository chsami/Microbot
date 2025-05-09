# Skill Conditions

Skill conditions in the Plugin Scheduler system allow plugins to be controlled based on the player's skill levels and experience points.

## Overview

Skill conditions monitor the player's progress in various skills, allowing plugins to respond to skill-related milestones and achievements. These conditions can be used to automate skill training, set goals, and manage plugin schedules based on skill progress.

## Available Skill Conditions

### SkillLevelCondition

The `SkillLevelCondition` monitors the player's actual level in a specific skill.

**Usage:**
```java
// Satisfied when player has at least level 70 Mining
SkillLevelCondition condition = new SkillLevelCondition(
    Skill.MINING,       // The skill to monitor
     70                 // Target level
);

// Satisfied when player gains 5 levels in Attack (relative)
SkillLevelCondition relativeCondition = SkillLevelCondition.createRelative(
    Skill.ATTACK,       // The skill to monitor
    5                   // Target level gain
);

// Satisfied when player reaches a random level between 70-80 in Mining
SkillLevelCondition randomizedCondition = SkillLevelCondition.createRandomized(
    Skill.MINING,       // The skill to monitor
    70,                 // Minimum target level
    80                  // Maximum target level
);
```

**Key features:**
- Monitors any skill in the game
- Can track total level using `Skill.OVERALL`
- Supports absolute level targets (reach a specific level)
- Supports relative level targets (gain X levels from current)
- Can use randomization within a min/max range
- Updates dynamically as skill levels change
- Provides progress tracking toward target levels

### SkillXpCondition

The `SkillXpCondition` monitors the player's experience points in a specific skill.

**Usage:**
```java
// Absolute XP goal: Satisfied when player has at least 1,000,000 XP in Woodcutting
SkillXpCondition condition = new SkillXpCondition(
    Skill.WOODCUTTING,  // The skill to monitor
    1_000_000           // Target XP (absolute)
);

// Relative XP goal: Satisfied when player gains 50,000 XP from the starting point
SkillXpCondition relativeCondition = SkillXpCondition.createRelative(
    Skill.WOODCUTTING,  // The skill to monitor
    50_000              // Target XP gain
);

// Randomized XP goal: Satisfied when player reaches a random XP between 1M-1.5M
SkillXpCondition randomizedCondition = SkillXpCondition.createRandomized(
    Skill.WOODCUTTING,  // The skill to monitor
    1_000_000,          // Minimum target XP
    1_500_000           // Maximum target XP
);

// Randomized relative XP goal: Satisfied when player gains a random amount of XP 
// between 50K-100K from starting point
SkillXpCondition relativeRandomCondition = SkillXpCondition.createRelativeRandomized(
    Skill.WOODCUTTING,  // The skill to monitor
    50_000,             // Minimum XP gain
    100_000             // Maximum XP gain
);
```

**Key features:**
- Monitors precise XP values rather than levels
- Useful for tracking progress between levels
- Can be used to set specific XP goals
- Provides accurate progress percentage toward XP targets
- Supports both absolute XP targets (reach a specific XP amount)
- Supports relative XP targets (gain X XP from current)
- Can use randomization within a min/max range for both absolute and relative targets

## Common Features of Skill Conditions

All skill conditions implement the `SkillCondition` interface, which extends the base `Condition` interface and provides additional functionality:

- `getProgressPercentage()`: Returns the progress toward the target level or XP as a percentage
- `reset()`: Resets any cached skill data
- `getSkill()`: Returns the skill being monitored
- `getTargetValue()`: Returns the target level or XP value

## Using Skill Conditions as Start Conditions

When used as start conditions, skill conditions can trigger plugins based on skill achievements:

```java
PluginScheduleEntry entry = new PluginScheduleEntry("MyPlugin", true);

// Start the plugin when the player reaches level 70 in Mining
entry.addStartCondition(new SkillLevelCondition(
    Skill.MINING,
     70
));
```

## Using Skill Conditions as Stop Conditions

Skill conditions can be used as stop conditions to end a plugin's execution when a skill goal is reached:

```java
// Stop when the player reaches level 80 in Mining
entry.addStopCondition(new SkillLevelCondition(
    Skill.MINING,
     80
));

// OR stop when the player gains 100,000 XP in Mining
entry.addStopCondition(SkillXpCondition.createRelative(
    Skill.MINING,
    100_000
));
```

## Tracking Relative Changes

Both `SkillXpCondition` and `SkillLevelCondition` support tracking relative changes, which is useful for setting goals based on progress from the current state rather than absolute values:

```java
// Satisfied when the player gains 50,000 XP in total from when the condition was created
SkillXpCondition condition = SkillXpCondition.createRelative(
    Skill.OVERALL,  // Track total XP across all skills
    50_000          // Target XP gain
);

// Satisfied when the player gains 5 levels in Mining from when the condition was created
SkillLevelCondition levelCondition = SkillLevelCondition.createRelative(
    Skill.MINING,   // The skill to monitor
    5               // Target level gain
);
```

## Combining with Logical Conditions

Skill conditions can be combined with logical conditions to create complex skill-based rules:

```java
// Create a logical AND condition
AndCondition skillGoals = new AndCondition();

// Require level 70 in Mining
skillGoals.addCondition(new SkillLevelCondition(
    Skill.MINING,
     70
));

// AND level 70 in Smithing
skillGoals.addCondition(new SkillLevelCondition(
    Skill.SMITHING,
     70
));

// Add these combined requirements as a start condition
entry.addStartCondition(skillGoals);
```

## Multi-Skill Training Scenarios

For multi-skill training scenarios, skill conditions can be configured to monitor several skills:

```java
// Create a logical OR condition for alternative training paths
OrCondition trainingGoals = new OrCondition();

// Path 1: Mining to level 80
trainingGoals.addCondition(new SkillLevelCondition(
    Skill.MINING,
     80
));

// Path 2: Fishing to level 80
trainingGoals.addCondition(new SkillLevelCondition(
    Skill.FISHING,
     80
));

// Path 3: Woodcutting to level 80
trainingGoals.addCondition(new SkillLevelCondition(
    Skill.WOODCUTTING,
     80
));

// Add these alternative goals as a stop condition
entry.addStopCondition(trainingGoals);
```

## Event Integration

Skill conditions integrate with the RuneLite event system to track changes in real-time:

- `StatChanged`: Updates skill levels and XP values when they change
- `GameTick`: Periodically validates condition state

## Performance Optimizations

The `SkillCondition` base class includes several optimizations for improved performance:

- **Static Caching**: Skill levels and XP values are cached in static maps to minimize client thread calls
- **Throttled Updates**: Updates are throttled to prevent excessive client thread operations
- **Icon Caching**: Skill icons are cached to improve UI rendering performance
- **Single Source of Truth**: All skill-related conditions use the same cached skill data
- **Efficient Event Handling**: Only relevant skill updates trigger condition recalculation

Example using the cached data:

```java
// Get cached skill data without requiring client thread call
int currentLevel = SkillCondition.getSkillLevel(Skill.MINING);
long currentXp = SkillCondition.getSkillXp(Skill.MINING);
int totalLevel = SkillCondition.getTotalLevel();
long totalXp = SkillCondition.getTotalXp();

// Force an update of all skill data (throttled to prevent performance issues)
SkillCondition.forceUpdate();
```