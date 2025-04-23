# NPC Conditions

NPC conditions in the Plugin Scheduler system allow plugins to be controlled based on NPC-related events and states in the game.

## Overview

NPC conditions monitor interactions with Non-Player Characters in the game, enabling plugins to respond to NPC presence, combat state, and other NPC-related factors. These conditions are particularly useful for combat automation, quest helpers, and NPC interaction scripts.

## Available NPC Conditions

### NpcCondition

The `NpcCondition` monitors the presence, proximity, or state of specific NPCs in the game.

**Usage:**
```java
// Satisfied when an NPC with ID 3080 (Moss giant) is within visibility range
NpcCondition condition = new NpcCondition(
    3080,       // NPC ID to check
    true        // Whether the NPC must be present to satisfy the condition
);
```

**Key features:**
- Monitors for specific NPC IDs or name patterns
- Can check for NPC presence or absence
- Can validate distance to the NPC
- Supports combat state checking (in combat, health percentage)
- Updates dynamically as NPCs spawn, despawn, or change state

## Common Features of NPC Conditions

All NPC conditions provide core functionality for NPC-based checks:

- `isSatisfied()`: Determines if the current NPC state satisfies the condition
- `getDescription()`: Returns a human-readable description of the NPC condition
- `reset()`: Refreshes any cached NPC data
- Various configuration options for specifying which NPCs to monitor and what states to check

## Using NPC Conditions as Start Conditions

When used as start conditions, NPC conditions can trigger plugins when specific NPCs appear or enter a certain state:

```java
PluginScheduleEntry entry = new PluginScheduleEntry("MyPlugin", true);

// Start the plugin when a Rock Crab appears
entry.addStartCondition(new NpcCondition(
    "Rock Crab",   // NPC name to check (can also use ID)
    true,          // NPC must be present
    15             // Within 15 tiles
));
```

## Using NPC Conditions as Stop Conditions

NPC conditions are also valuable as stop conditions to deactivate plugins based on NPC state:

```java
// Stop the plugin when the target NPC dies or despawns
entry.addStopCondition(new NpcCondition(
    3080,       // NPC ID (Moss giant)
    false       // NPC must NOT be present (i.e., has despawned)
));

// OR stop when the player is no longer in combat with any NPC
entry.addStopCondition(new NotCondition(
    new NpcCondition().inCombat(true)
));
```

## Combining with Logical Conditions

NPC conditions can be combined with logical conditions to create more complex NPC-related rules:

```java
// Create a logical AND condition
AndCondition combatCondition = new AndCondition();

// Require being in combat with an NPC
combatCondition.addCondition(new NpcCondition().inCombat(true));

// AND the NPC's health is below 25%
combatCondition.addCondition(new NpcCondition().healthPercentageLessThan(25));

// Add this combined condition as a start condition for a finishing move plugin
entry.addStartCondition(combatCondition);
```

## Advanced NPC Monitoring

For more complex NPC monitoring scenarios, multiple conditions can be combined:

```java
// Create a logical OR condition for multiple NPC types
OrCondition dragonTargets = new OrCondition();

// Add various dragon types
dragonTargets.addCondition(new NpcCondition(
    "Blue dragon", true, 20
));
dragonTargets.addCondition(new NpcCondition(
    "Red dragon", true, 20
));
dragonTargets.addCondition(new NpcCondition(
    "Green dragon", true, 20
));
dragonTargets.addCondition(new NpcCondition(
    "Black dragon", true, 20
));

// Add this combined condition to start a dragon-fighting plugin
entry.addStartCondition(dragonTargets);
```

## Event Integration

NPC conditions integrate with the RuneLite event system to track changes in real-time:

- `NpcSpawned`: Detects when new NPCs appear in the game
- `NpcDespawned`: Detects when NPCs are removed from the game
- `NpcChanged`: Detects when NPC properties or appearance changes
- `InteractingChanged`: Detects when the player starts or stops interacting with an NPC
- `HitsplatApplied`: Monitors damage dealt to NPCs
- `GameTick`: Periodically validates NPC state