# Queryable API Documentation

**Version:** 2.1.0  
**Last Updated:** November 18, 2025

---

## Table of Contents

1. [Introduction](#introduction)
2. [Why Use Queryable API?](#why-use-queryable-api)
3. [Core Concepts](#core-concepts)
4. [Getting Started](#getting-started)
5. [Available Queryables](#available-queryables)
6. [API Reference](#api-reference)
7. [Common Patterns](#common-patterns)
8. [Advanced Usage](#advanced-usage)
9. [Performance Tips](#performance-tips)
10. [Migration Guide](#migration-guide)

---

## Introduction

The **Queryable API** is a modern, fluent interface for querying game entities in Microbot. It provides a type-safe, chainable way to filter and find NPCs, players, ground items, and tile objects with minimal boilerplate code.

### Design Philosophy

- **Fluent Interface**: Chain multiple filters for readable code
- **Type-Safe**: Compile-time checking prevents errors
- **Performance**: Leverages efficient caching and streaming
- **Intuitive**: Natural language-like queries
- **Flexible**: Custom predicates for complex filters

---

## Why Use Queryable API?

### Old Way (Legacy) ❌

```java
// Verbose and hard to read
NPC banker = null;
for (NPC npc : client.getNpcs()) {
    if (npc.getName() != null && 
        npc.getName().equals("Banker") && 
        !npc.getInteracting() != null) {
        if (banker == null || 
            npc.getWorldLocation().distanceTo(player.getWorldLocation()) < 
            banker.getWorldLocation().distanceTo(player.getWorldLocation())) {
            banker = npc;
        }
    }
}

// Stream API - better but still verbose
NPC banker = Rs2Npc.getNpcs().stream()
    .filter(npc -> npc.getName() != null)
    .filter(npc -> npc.getName().equals("Banker"))
    .filter(npc -> npc.getInteracting() == null)
    .min(Comparator.comparingInt(npc -> 
        npc.getWorldLocation().distanceTo(player.getWorldLocation())))
    .orElse(null);
```

### New Way (Queryable) ✅

```java
// Clean, readable, and concise
@Inject private Rs2NpcCache npcCache;

Rs2NpcModel banker = npcCache.query()
        .withName("Banker")
        .where(npc -> !npc.isInteracting())
        .nearest();
```

**Benefits:**
- 📖 **Readable**: Self-documenting code
- 🚀 **Faster Development**: Less code to write
- 🐛 **Fewer Bugs**: Type-safe operations
- 🔧 **Maintainable**: Easy to modify queries
- ⚡ **Performant**: Optimized internally

---

## Core Concepts

### 1. Entity Models

All queryable entities implement the `IEntity` interface:

```java
public interface IEntity {
    String getName();           // Entity name
    int getId();               // Entity ID
    WorldPoint getWorldLocation(); // World coordinates
    // ... other common properties
}
```

**Available Models:**
- `Rs2NpcModel` - NPCs
- `Rs2PlayerModel` - Players
- `Rs2TileItemModel` - Ground items
- `Rs2TileObjectModel` - Game objects

**Example setup (used throughout examples):**
```java
@Inject private Rs2NpcCache npcCache;
@Inject private Rs2PlayerCache playerCache;
@Inject private Rs2TileItemCache tileItemCache;
@Inject private Rs2TileObjectCache tileObjectCache;
// Use <cache>.query() to build queries.
```

### 2. Queryable Interface

All queryables implement `IEntityQueryable<Q, E>`:

```java
public interface IEntityQueryable<Q, E> {
    Q where(Predicate<E> predicate);     // Custom filter
    Q within(int distance);               // Distance from player
    Q within(WorldPoint anchor, int distance); // Distance from point
    E first();                            // First match
    E nearest();                          // Nearest to player
    E nearest(int maxDistance);           // Nearest within range
    E nearest(WorldPoint anchor, int maxDistance); // Nearest to point
    E withName(String name);             // Find by name
    E withNames(String... names);        // Find by multiple names
    E withId(int id);                    // Find by ID
    E withIds(int... ids);               // Find by multiple IDs
    List<E> toList();                    // Get all matches
}
```

### 3. Fluent Chaining

Methods return the queryable itself, allowing chaining:

```java
npcCache.query()
    .withName("Guard")           // Filter by name
    .where(npc -> !npc.isInteracting())  // Add custom filter
    .within(15)                  // Within 15 tiles
    .nearest();                  // Get nearest match
```

### 4. Lazy Evaluation

Queries are not executed until a terminal operation is called:

```java
// No execution yet - just building the query
@Inject private Rs2NpcCache npcCache;

Rs2NpcQueryable query = npcCache.query()
        .withName("Guard")
        .within(10);

// NOW it executes
Rs2NpcModel guard = query.nearest();  // Terminal operation
```

---

## Getting Started

Always inject the corresponding cache and call `query()` from it to build queries against the per-tick cache.

### Basic Query Structure

Every query follows this pattern:

```java
// 1. Inject the cache for the entity type
@Inject private Rs2NpcCache npcCache;

// 2. Create queryable from the cache
npcCache.query()
    
// 3. Add filters (optional, chainable)
    .withName("name")
    .where(entity -> condition)
    .within(distance)
    
// 4. Execute with terminal operation
    .nearest();  // or first(), toList(), etc.
```

### Simple Examples

**Find nearest NPC by name:**
```java
@Inject private Rs2NpcCache npcCache;

Rs2NpcModel banker = npcCache.query()
        .withName("Banker")
        .nearest();
```

**Find nearest ground item:**
```java
@Inject private Rs2TileItemCache tileItemCache;

Rs2TileItemModel coins = tileItemCache.query()
        .withName("Coins")
        .nearest();
```

**Find nearest player:**
```java
@Inject private Rs2PlayerCache playerCache;

Rs2PlayerModel player = playerCache.query()
        .withName("PlayerName")
        .nearest();
```

**Find nearest tree (tile object):**
```java
@Inject private Rs2TileObjectCache tileObjectCache;

Rs2TileObjectModel tree = tileObjectCache.query()
        .withName("Tree")
        .nearest();
```

---

## Available Queryables

### 1. Rs2NpcQueryable - NPC Queries

**Import:**
```java
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
```

**Basic Usage:**
```java
// Find nearest banker
Rs2NpcModel banker = npcCache.query()
    .withName("Banker")
    .nearest();

// Find all guards within 10 tiles
List<Rs2NpcModel> guards = npcCache.query()
    .withName("Guard")
    .within(10)
    .toList();

// Find nearest non-interacting cow
Rs2NpcModel cow = npcCache.query()
    .withName("Cow")
    .where(npc -> !npc.isInteracting())
    .nearest();
```

**Rs2NpcModel Methods:**
```java
npc.getName()              // "Guard"
npc.getId()                // 123
npc.getWorldLocation()     // WorldPoint
npc.isInteracting()        // true/false
npc.getHealthRatio()       // 0-30
npc.getAnimation()         // Animation ID
npc.click("Attack")        // Interact with NPC
npc.interact("Bank")       // Alternative interact method
```

### 2. Rs2TileItemQueryable - Ground Item Queries

**Import:**
```java
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
```

**Basic Usage:**
```java
// Find nearest coins
Rs2TileItemModel coins = tileItemCache.query()
    .withName("Coins")
    .nearest();

// Find valuable items
List<Rs2TileItemModel> loot = tileItemCache.query()
    .where(item -> item.getTotalValue() > 1000)
    .toList();

// Find nearest lootable item
Rs2TileItemModel lootable = tileItemCache.query()
    .where(Rs2TileItemModel::isLootAble)
    .nearest();
```

**Rs2TileItemModel Methods:**
```java
item.getName()             // "Coins"
item.getId()               // Item ID
item.getQuantity()         // Stack size
item.getTotalValue()       // GE value
item.getTotalGeValue()     // Total GE value
item.isLootAble()          // Can loot?
item.isOwned()             // Owned by player?
item.isStackable()         // Is stackable?
item.isNoted()             // Is noted?
item.isTradeable()         // Is tradeable?
item.isMembers()           // Members item?
item.isDespawned()         // Has despawned?
item.willDespawnWithin(seconds) // Will despawn soon?
item.pickup()              // Pick up item
```

### 3. Rs2PlayerQueryable - Player Queries

**Import:**
```java
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
```

**Basic Usage:**
```java
// Find nearest player
Rs2PlayerModel player = playerCache.query()
    .nearest();

// Find player by name
Rs2PlayerModel target = playerCache.query()
    .withName("PlayerName")
    .nearest();

// Find all friends nearby
List<Rs2PlayerModel> friends = playerCache.query()
    .where(Rs2PlayerModel::isFriend)
    .within(20)
    .toList();
```

**Rs2PlayerModel Methods:**
```java
player.getName()              // Player name
player.getCombatLevel()       // Combat level
player.getHealthRatio()       // Health (-1 if not visible)
player.isFriend()             // Is friend?
player.isClanMember()         // In your clan?
player.isFriendsChatMember()  // In friends chat?
player.getSkullIcon()         // Skull icon (-1 if none)
player.getOverheadIcon()      // Prayer icon (HeadIcon or null)
player.getAnimation()         // Current animation
player.isInteracting()        // Is interacting?
```

### 4. Rs2TileObjectQueryable - Tile Object Queries

**Import:**
```java
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
```

**Basic Usage:**
```java
// Find nearest tree
Rs2TileObjectModel tree = tileObjectCache.query()
    .withName("Tree")
    .nearest();

// Find nearest bank booth
Rs2TileObjectModel bank = tileObjectCache.query()
    .withName("Bank booth")
    .nearest();

// Find all rocks within 15 tiles
List<Rs2TileObjectModel> rocks = tileObjectCache.query()
    .where(obj -> obj.getName() != null && 
                  obj.getName().contains("rocks"))
    .within(15)
    .toList();
```

**Rs2TileObjectModel Methods:**
```java
obj.getName()              // "Tree"
obj.getId()                // Object ID
obj.getWorldLocation()     // WorldPoint
obj.click("Chop down")     // Interact with object
obj.click("Mine")          // Interact with a specific action
```

---

## API Reference

### Terminal Operations

These execute the query and return results:

#### `nearest()`
Returns the nearest entity to the player.

```java
Rs2NpcModel npc = npcCache.query()
    .withName("Guard")
    .nearest();
```

#### `nearest(int maxDistance)`
Returns the nearest entity within max distance from player.

```java
Rs2NpcModel npc = npcCache.query()
    .withName("Guard")
    .nearest(10);  // Within 10 tiles
```

#### `nearest(WorldPoint anchor, int maxDistance)`
Returns the nearest entity to a specific point.

```java
WorldPoint location = new WorldPoint(3100, 3500, 0);
Rs2NpcModel npc = npcCache.query()
    .withName("Guard")
    .nearest(location, 5);
```

#### `first()`
Returns the first matching entity (not necessarily nearest).

```java
Rs2NpcModel npc = npcCache.query()
    .withName("Guard")
    .first();
```

#### `withName(String name)`
Finds nearest entity with exact name (case-insensitive).

```java
Rs2NpcModel banker = npcCache.query()
    .withName("Banker");  // Terminal operation
```

#### `withNames(String... names)`
Finds nearest entity matching any of the names.

```java
Rs2NpcModel npc = npcCache.query()
    .withNames("Banker", "Bank clerk", "Bank assistant");
```

#### `withId(int id)`
Finds nearest entity with specific ID.

```java
Rs2NpcModel npc = npcCache.query()
    .withId(1234);
```

#### `withIds(int... ids)`
Finds nearest entity matching any of the IDs.

```java
Rs2NpcModel npc = npcCache.query()
    .withIds(1234, 5678, 9012);
```

#### `toList()`
Returns all matching entities as a list.

```java
List<Rs2NpcModel> guards = npcCache.query()
    .withName("Guard")
    .toList();
```

#### `count()`
Returns the number of matching entities.

```java
int guardCount = npcCache.query()
    .withName("Guard")
    .within(10)
    .count();
```

### Filter Operations

These filter entities and return the queryable for chaining:

#### `where(Predicate<E> predicate)`
Adds a custom filter using a lambda expression.

```java
npcCache.query()
    .where(npc -> npc.getHealthRatio() > 0)
    .where(npc -> !npc.isInteracting())
    .nearest();
```

#### `within(int distance)`
Filters entities within distance from player.

```java
npcCache.query()
    .withName("Guard")
    .within(10)
    .toList();
```

#### `within(WorldPoint anchor, int distance)`
Filters entities within distance from a specific point.

```java
WorldPoint location = new WorldPoint(3100, 3500, 0);
npcCache.query()
    .withName("Guard")
    .within(location, 15)
    .toList();
```

---

## Common Patterns

### Pattern 1: Find Nearest Non-Interacting NPC

```java
Rs2NpcModel cow = npcCache.query()
    .withName("Cow")
    .where(npc -> !npc.isInteracting())
    .nearest();

if (cow != null) {
    cow.click("Attack");
}
```

### Pattern 2: Find Valuable Loot

```java
Rs2TileItemModel loot = tileItemCache.query()
    .where(item -> item.getTotalGeValue() >= 5000)
    .where(Rs2TileItemModel::isLootAble)
    .nearest(10);

if (loot != null) {
    loot.pickup();
}
```

### Pattern 3: Find Multiple NPCs

```java
List<Rs2NpcModel> guards = npcCache.query()
    .withName("Guard")
    .where(npc -> !npc.isInteracting())
    .within(15)
    .toList();

for (Rs2NpcModel guard : guards) {
    // Do something with each guard
}
```

### Pattern 4: Find by Multiple Names

```java
Rs2NpcModel banker = npcCache.query()
    .withNames("Banker", "Bank clerk", "Bank assistant");

if (banker != null) {
    banker.click("Bank");
}
```

### Pattern 5: Complex Query with Multiple Filters

```java
Rs2NpcModel target = npcCache.query()
    .withName("Goblin")
    .where(npc -> !npc.isInteracting())
    .where(npc -> npc.getHealthRatio() > 0)
    .where(npc -> npc.getAnimation() == -1)  // Not animating
    .within(10)
    .nearest();
```

### Pattern 6: Find Nearest Object by Partial Name

```java
Rs2TileObjectModel tree = tileObjectCache.query()
    .where(obj -> obj.getName() != null && 
                  obj.getName().toLowerCase().contains("tree"))
    .nearest();
```

### Pattern 7: Find Items About to Despawn

```java
List<Rs2TileItemModel> despawning = tileItemCache.query()
    .where(item -> item.willDespawnWithin(30))  // 30 seconds
    .where(item -> item.getTotalValue() > 1000)
    .toList();
```

### Pattern 8: Find Friends Nearby

```java
List<Rs2PlayerModel> friends = playerCache.query()
    .where(Rs2PlayerModel::isFriend)
    .within(20)
    .toList();
```

### Pattern 9: Find Low Health Enemies

```java
Rs2NpcModel weakEnemy = npcCache.query()
    .withName("Goblin")
    .where(npc -> npc.getHealthRatio() > 0 && 
                  npc.getHealthRatio() < 10)  // Low health
    .nearest();
```

### Pattern 10: Find Specific Object by ID

```java
Rs2TileObjectModel altar = tileObjectCache.query()
    .withId(409)  // Altar object ID
    .nearest();
```

---

## Advanced Usage

### Custom Predicates

Create reusable predicates for common filters:

```java
// Define predicates
Predicate<Rs2NpcModel> isAlive = npc -> npc.getHealthRatio() > 0;
Predicate<Rs2NpcModel> notBusy = npc -> !npc.isInteracting();
Predicate<Rs2NpcModel> notAnimating = npc -> npc.getAnimation() == -1;

// Use them
Rs2NpcModel target = npcCache.query()
    .withName("Cow")
    .where(isAlive)
    .where(notBusy)
    .where(notAnimating)
    .nearest();
```

### Combining Predicates

```java
// Combine with AND
Predicate<Rs2NpcModel> attackable = 
    npc -> !npc.isInteracting() && npc.getHealthRatio() > 0;

Rs2NpcModel target = npcCache.query()
    .withName("Goblin")
    .where(attackable)
    .nearest();

// Combine with OR
Predicate<Rs2TileItemModel> valuableOrStackable =
    item -> item.getTotalValue() > 1000 || item.isStackable();

Rs2TileItemModel loot = tileItemCache.query()
    .where(valuableOrStackable)
    .nearest();
```

### Distance-Based Queries

```java
// Find nearest NPC within specific range
WorldPoint homeBase = new WorldPoint(3100, 3500, 0);

Rs2NpcModel nearbyEnemy = npcCache.query()
    .withName("Goblin")
    .within(homeBase, 10)  // Within 10 tiles of home base
    .nearest(homeBase, 10);  // Get nearest to home base
```

### Sorting and Limiting

```java
// Get 5 nearest guards
List<Rs2NpcModel> guards = npcCache.query()
    .withName("Guard")
    .toList()
    .stream()
    .sorted(Comparator.comparingInt(npc -> 
        npc.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())))
    .limit(5)
    .collect(Collectors.toList());
```

### Null Safety

Always check for null results:

```java
Rs2NpcModel banker = npcCache.query()
    .withName("Banker")
    .nearest();

if (banker != null) {
    banker.click("Bank");
} else {
    log.warn("No banker found nearby");
}
```

### Using with sleepUntil

```java
// Wait until target NPC appears
Rs2NpcModel target = sleepUntilNotNull(() -> 
    npcCache.query()
        .withName("Banker")
        .nearest(),
    5000, 600  // 5 second timeout, check every 600ms
);

if (target != null) {
    target.click("Bank");
}
```

---

## Performance Tips

### ✅ DO:

**1. Use specific filters early:**
```java
// Good - filters by name first
npcCache.query()
    .withName("Guard")
    .where(npc -> !npc.isInteracting())
    .nearest();
```

**2. Limit search radius:**
```java
// Good - only searches within 10 tiles
npcCache.query()
    .withName("Guard")
    .within(10)
    .nearest();
```

**3. Cache results when possible:**
```java
// Good - query once, use multiple times
List<Rs2NpcModel> guards = npcCache.query()
    .withName("Guard")
    .toList();

for (Rs2NpcModel guard : guards) {
    // Process each guard
}
```

**4. Use method references:**
```java
// Good - cleaner and potentially faster
tileItemCache.query()
    .where(Rs2TileItemModel::isLootAble)
    .nearest();
```

### ❌ DON'T:

**1. Don't query in tight loops:**
```java
// Bad - queries on every iteration
while (true) {
    Rs2NpcModel npc = npcCache.query()
        .withName("Guard")
        .nearest();
    // ...
    sleep(100);  // Still too frequent
}

// Good - reasonable interval
while (true) {
    Rs2NpcModel npc = npcCache.query()
        .withName("Guard")
        .nearest();
    // ...
    sleep(600);  // ~1 game tick
}
```

**2. Don't use expensive operations in predicates:**
```java
// Bad - calls API repeatedly in filter
npcCache.query()
    .where(npc -> someExpensiveApiCall(npc))
    .nearest();

// Good - call API once, cache result
boolean shouldFilter = someExpensiveApiCall();
npcCache.query()
    .where(npc -> shouldFilter)
    .nearest();
```

**3. Don't create unnecessary lists:**
```java
// Bad - creates full list just to get one item
Rs2NpcModel npc = npcCache.query()
    .withName("Guard")
    .toList()
    .get(0);

// Good - gets first directly
Rs2NpcModel npc = npcCache.query()
    .withName("Guard")
    .first();
```

---

## Migration Guide

### From Legacy API to Queryable API

#### NPCs

**Legacy:**
```java
// Old way
NPC npc = Rs2Npc.getNpc("Banker");
NPC nearest = Rs2Npc.getNearestNpc("Guard");
List<NPC> npcs = Rs2Npc.getNpcs(NpcID.GUARD);
```

**Queryable:**
```java
// New way
Rs2NpcModel npc = npcCache.query()
    .withName("Banker");

Rs2NpcModel nearest = npcCache.query()
    .withName("Guard")
    .nearest();

List<Rs2NpcModel> npcs = npcCache.query()
    .withId(NpcID.GUARD)
    .toList();
```

#### Ground Items

**Legacy:**
```java
// Old way
TileItem item = Rs2GroundItem.findItem("Coins");
TileItem nearest = Rs2GroundItem.getNearestItem("Dragon bones");
```

**Queryable:**
```java
// New way
Rs2TileItemModel item = tileItemCache.query()
    .withName("Coins")
    .first();

Rs2TileItemModel nearest = tileItemCache.query()
    .withName("Dragon bones")
    .nearest();
```

#### Game Objects

**Legacy:**
```java
// Old way
TileObject tree = Rs2GameObject.findObject("Tree");
TileObject nearest = Rs2GameObject.findObjectById(1234);
```

**Queryable:**
```java
// New way
Rs2TileObjectModel tree = tileObjectCache.query()
    .withName("Tree")
    .nearest();

Rs2TileObjectModel nearest = tileObjectCache.query()
    .withId(1234)
    .nearest();
```

### Step-by-Step Migration

**Step 1:** Replace direct method calls with queryable:
```java
// Before
NPC banker = Rs2Npc.getNpc("Banker");

// After
Rs2NpcModel banker = npcCache.query().withName("Banker");
```

**Step 2:** Add filters if needed:
```java
// Before
NPC cow = Rs2Npc.getNpcs().stream()
    .filter(npc -> npc.getName().equals("Cow"))
    .filter(npc -> npc.getInteracting() == null)
    .findFirst()
    .orElse(null);

// After
Rs2NpcModel cow = npcCache.query()
    .withName("Cow")
    .where(npc -> !npc.isInteracting())
    .nearest();
```

**Step 3:** Update interaction methods:
```java
// Before
if (banker != null) {
    Rs2Npc.interact(banker, "Bank");
}

// After
if (banker != null) {
    banker.click("Bank");
    // or
    banker.interact("Bank");
}
```

---

## Examples by Use Case

### Combat Scripts

```java
// Find nearest attackable enemy
Rs2NpcModel enemy = npcCache.query()
    .withName("Goblin")
    .where(npc -> !npc.isInteracting())
    .where(npc -> npc.getHealthRatio() > 0)
    .nearest(10);

if (enemy != null && !Rs2Player.isInCombat()) {
    enemy.click("Attack");
    sleepUntil(() -> Rs2Player.isInCombat(), 2000);
}
```

### Looting Scripts

```java
// Find valuable loot
Rs2TileItemModel loot = tileItemCache.query()
    .where(Rs2TileItemModel::isLootAble)
    .where(item -> item.getTotalGeValue() >= 5000)
    .nearest(15);

if (loot != null) {
    loot.pickup();
    sleepUntil(() -> Rs2Inventory.contains(loot.getName()), 3000);
}
```

### Skilling Scripts

```java
// Find nearest available tree
Rs2TileObjectModel tree = tileObjectCache.query()
    .where(obj -> obj.getName() != null && 
                  obj.getName().equals("Oak tree"))
    .nearest(10);

if (tree != null && !Rs2Player.isAnimating()) {
    tree.click("Chop down");
    sleepUntil(() -> Rs2Player.isAnimating(), 3000);
}
```

### Banking Scripts

```java
// Find nearest bank
Rs2TileObjectModel bank = tileObjectCache.query()
    .withNames("Bank booth", "Bank chest", "Bank")
    .nearest(20);

if (bank != null && !Rs2Bank.isOpen()) {
    bank.click("Bank");
    sleepUntil(() -> Rs2Bank.isOpen(), 5000);
}
```

---

## Troubleshooting

### Query Returns Null

**Problem:** Query returns `null` even though entity exists.

**Solutions:**

1. **Check distance:**
```java
// Increase search radius
.nearest(20)  // Instead of default
```

2. **Verify name:**
```java
// Names are case-insensitive but must be exact
.withName("Banker")  // Not "banker" or "Bank"
```

3. **Check filters:**
```java
// Simplify query to find the issue
Rs2NpcModel test1 = npcCache.query().withName("Banker");
Rs2NpcModel test2 = npcCache.query().withName("Banker").where(filter);
// If test1 works but test2 doesn't, your filter is too restrictive
```

4. **Verify entity is loaded:**
```java
// Wait for entity to appear
Rs2NpcModel npc = sleepUntilNotNull(() -> 
    npcCache.query().withName("Banker").nearest(),
    5000, 600
);
```

### Performance Issues

**Problem:** Queries are slow or cause lag.

**Solutions:**

1. **Limit search area:**
```java
.within(15)  // Only search nearby
```

2. **Cache results:**
```java
// Query once per game tick, not every iteration
List<Rs2NpcModel> npcs = npcCache.query()
    .withName("Guard")
    .toList();
```

3. **Simplify predicates:**
```java
// Avoid complex calculations in where() clauses
.where(npc -> simpleCheck(npc))  // Good
.where(npc -> complexCalculation(npc))  // Bad
```

### Interaction Failures

**Problem:** Entity found but click doesn't work.

**Solutions:**

1. **Check null:**
```java
Rs2NpcModel npc = npcCache.query().withName("Banker");
if (npc != null) {  // Always check
    npc.click("Bank");
}
```

2. **Wait for action:**
```java
npc.click("Bank");
sleepUntil(() -> Rs2Bank.isOpen(), 5000);
```

3. **Verify entity still exists:**
```java
if (npc != null && npc.getWorldLocation() != null) {
    npc.click("Bank");
}
```

---

## Best Practices Summary

✅ **DO:**
- Use queryable API for new code
- Chain filters for readability
- Check for null results
- Use `nearest()` for single entities
- Use `toList()` for multiple entities
- Cache query results when appropriate
- Use method references when possible
- Add distance limits to queries

❌ **DON'T:**
- Query in tight loops without delays
- Use expensive operations in filters
- Forget null checks
- Create unnecessary intermediate lists
- Use legacy API for new code
- Query without distance limits

---

## Quick Reference

### Imports

```java
// NPCs
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;

// Ground Items
import net.runelite.client.plugins.microbot.api.tileitem.Rs2TileItemCache;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;

// Players
import net.runelite.client.plugins.microbot.api.player.Rs2PlayerCache;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;

// Tile Objects
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
```

### Quick Examples

```java
// Find nearest NPC
npcCache.query().withName("Banker").nearest();

// Find ground item
tileItemCache.query().withName("Coins").nearest();

// Find player
playerCache.query().withName("PlayerName").nearest();

// Find object
tileObjectCache.query().withName("Tree").nearest();

// Complex query
npcCache.query()
    .withName("Guard")
    .where(npc -> !npc.isInteracting())
    .within(10)
    .nearest();
```

---

## Additional Resources

- **CLAUDE.md** - Full framework documentation
- **Example Scripts** - See `api/*/ApiExample.java` files
- **Discord** - https://discord.gg/zaGrfqFEWE
- **Website** - https://themicrobot.com

---

**Last Updated:** November 18, 2025  
**Microbot Version:** 2.1.0  
**For questions or issues, please visit our Discord community.**
