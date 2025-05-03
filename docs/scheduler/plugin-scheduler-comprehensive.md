# Plugin Scheduler System

## Overview

The Plugin Scheduler is a system that brings automation, conditional execution, and state management to plugins. It allows plugins to be automatically started and stopped based on configurable conditions, providing a powerful framework for creating intelligent and self-managing scripts.

## Core Components

### SchedulablePlugin Interface

The `SchedulablePlugin` interface is the foundation of the scheduler system. By implementing this interface, a plugin can define when it should start and stop, as well as handle various scheduler events.

```java
public interface SchedulablePlugin {
    // Define when the plugin should start (optional with default implementation)
    default LogicalCondition getStartCondition() {
        return new AndCondition();
    }
    
    // Define when the plugin should stop (required)
    LogicalCondition getStopCondition();
    
    // Called periodically when conditions are being evaluated
    default void onStopConditionCheck() {
        // Optional hook for condition updates
    }
    
    // Handle soft stop events triggered by the scheduler
    public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event);
    
    // Handle finished events (when plugin self-reports completion)
    default public void onPluginScheduleEntryFinishedEvent(PluginScheduleEntryFinishedEvent event) {
        // Default implementation does nothing - handled by scheduler
    }
    
    // Allow plugin to report that it has finished its task
    default public void reportFinished(String reason, boolean success) {
        Microbot.getEventBus().post(new PluginScheduleEntryFinishedEvent(
            (Plugin) this,
            reason,
            success
        ));
    }
    
    // Safety mechanism for critical operations
    default boolean isHardStoppable() {
        return false;
    }
    
    // Locking mechanism to prevent stopping during critical operations
    default LockCondition getLockCondition(Condition stopConditions) {...}
    default LockCondition findLockCondition(Condition condition) {...}
    default boolean isLocked(Condition stopConditions) {...}
    default boolean lock(Condition stopConditions) {...}
    default boolean unlock(Condition stopConditions) {...}
    default Boolean toggleLock(Condition stopConditions) {...}
}
```

### State Management

The scheduler system maintains state through the `PluginScheduleEntry` class, which handles:

1. **Run State** - Tracking whether a plugin is running, when it last ran, and how many times it has run
2. **Condition Management** - Maintaining and evaluating start and stop conditions
3. **Event Handling** - Managing the lifecycle of plugin execution through events
4. **Scheduling Logic** - Determining when plugins should run and stop

### Condition System

At the heart of the scheduler is the condition system, built around the `Condition` interface:

```java
public interface Condition {
    // Check if condition is currently met
    boolean isSatisfied();
    
    // Human-readable description
    String getDescription();
    
    // Detailed description with status
    String getDetailedDescription();
    
    // When the condition will next be satisfied (for time-based conditions)
    Optional<ZonedDateTime> getCurrentTriggerTime();
    
    // Type of condition
    ConditionType getType();
    
    // Reset the condition
    void reset(boolean randomize);
    
    // Event handling methods
    default void onGameStateChanged(GameStateChanged event) {...}
    default void onStatChanged(StatChanged event) {...}
    default void onItemContainerChanged(ItemContainerChanged event) {...}
    default void onGameTick(GameTick event) {...}
    // ... many more event handlers
    
    // Progress tracking
    default double getProgressPercentage() {...}
    default int getTotalConditionCount() {...}
    default int getMetConditionCount() {...}
    default String getStatusInfo(int indent, boolean showProgress) {...}
}
```

## Specialized Condition Types

The SchedulerPlugin system provides a rich set of condition types that can be used to define when plugins should start and stop. Below is a comprehensive overview of the implemented condition types and how to use them effectively.

### Logical Conditions

Logical conditions form the foundation of complex condition structures:

#### AndCondition

Requires all child conditions to be satisfied:

```java
// Creates a condition that is satisfied when ALL child conditions are met
AndCondition andCondition = new AndCondition();
andCondition.addCondition(new SkillLevelCondition(Skill.MINING, 50));
andCondition.addCondition(new DurationCondition(Duration.ofHours(2)));
```

#### OrCondition

Requires any child condition to be satisfied:

```java
// Creates a condition that is satisfied when ANY child condition is met
OrCondition orCondition = new OrCondition();
orCondition.addCondition(new SkillLevelCondition(Skill.MINING, 50));
orCondition.addCondition(new DurationCondition(Duration.ofHours(2)));
```

#### NotCondition

Inverts the result of another condition:

```java
// Creates a condition that is satisfied when the child condition is NOT met
NotCondition notCondition = new NotCondition(new CombatCondition());
```

#### LockCondition

Controls stopping behavior during critical operations:

```java
// Creates a condition that can be manually locked/unlocked
LockCondition lockCondition = new LockCondition("Banking in progress");

// Use in your plugin when performing critical operations
lockCondition.lock();   // Prevents stopping
// ... critical code ...
lockCondition.unlock(); // Allows stopping again
```

### Skill Conditions

Skill conditions monitor player skill states and progress:

#### SkillLevelCondition

Triggers when a specific skill reaches a target level:

```java
// Satisfied when Mining reaches level 70
SkillLevelCondition levelCondition = new SkillLevelCondition(Skill.MINING, 70);

// Satisfied when Construction is at least level 50
SkillLevelCondition minLevelCondition = new SkillLevelCondition(
    Skill.CONSTRUCTION, 50, Comparison.GREATER_THAN_OR_EQUAL);
```

#### SkillXpCondition

Monitors experience gain in skills:

```java
// Satisfied when gaining 50,000 XP in any skill
SkillXpGainedCondition anySkillXpCondition = new SkillXpGainedCondition(50000);

// Satisfied when gaining 30,000 XP specifically in Woodcutting
SkillXpGainedCondition woodcuttingXpCondition = new SkillXpGainedCondition(Skill.WOODCUTTING, 30000);
```

#### SkillValueCondition

Monitors current skill values like prayer points or hitpoints:

```java
// Satisfied when hitpoints drops below 15 (safety feature)
SkillValueCondition lowHpCondition = new SkillValueCondition(
    Skill.HITPOINTS, 15, Comparison.LESS_THAN);

// Satisfied when prayer points reach 0
SkillValueCondition noPrayerCondition = new SkillValueCondition(
    Skill.PRAYER, 0, Comparison.EQUALS);
```

### Location Conditions

Location conditions track player position:

#### PositionCondition

Triggers when the player is at or near specific coordinates:

```java
// Satisfied when exactly at coordinates (3165, 3487, 0)
PositionCondition exactPositionCondition = new PositionCondition(
    "Grand Exchange Center", 3165, 3487, 0);

// Satisfied when within 5 tiles of coordinates
PositionCondition nearPositionCondition = new PositionCondition(
    "Near Varrock Bank", 3185, 3440, 0, 5);
```

#### AreaCondition

Triggers when the player is inside a rectangular area:

```java
// Satisfied when inside the Varrock bank area
AreaCondition bankAreaCondition = new AreaCondition(
    "Varrock Bank", 3180, 3435, 3190, 3445, 0);
```

#### RegionCondition

Triggers when the player enters specific game regions:

```java
// Satisfied when in the Grand Exchange region
RegionCondition geRegionCondition = new RegionCondition(
    "Grand Exchange", 12598);

// Satisfied when in any of multiple regions
RegionCondition miningRegionsCondition = new RegionCondition(
    "Mining Areas", 13107, 13363, 11422);
```

### Time Conditions

Time conditions allow scheduling based on real-world time:

#### DurationCondition

Simple timer that triggers after running for a set amount of time:

```java
// Satisfied after running for 2 hours
DurationCondition durationCondition = new DurationCondition(Duration.ofHours(2));

// Satisfied after running for 45 minutes
DurationCondition shortDurationCondition = new DurationCondition(Duration.ofMinutes(45));
```

#### TimeWindowCondition

Triggers during specific time windows each day:

```java
// Satisfied between 8:00 PM and 11:00 PM every day
TimeWindowCondition eveningCondition = new TimeWindowCondition(
    LocalTime.of(20, 0), // 8:00 PM
    LocalTime.of(23, 0)  // 11:00 PM
);
```

#### IntervalCondition

Triggers periodically at set intervals:

```java
// Satisfied every 30 minutes
IntervalCondition intervalCondition = new IntervalCondition(Duration.ofMinutes(30));

// Satisfied every 2 hours with randomization (±15 minutes)
IntervalCondition randomizedIntervalCondition = new IntervalCondition(
    Duration.ofHours(2), Duration.ofMinutes(15));
```

#### DayOfWeekCondition

Triggers on specific days of the week:

```java
// Satisfied on weekends
DayOfWeekCondition weekendCondition = new DayOfWeekCondition(
    DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    
// Satisfied Monday through Friday
DayOfWeekCondition weekdayCondition = new DayOfWeekCondition(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, 
    DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);
```

#### SingleTriggerTimeCondition

Triggers once at a specific time:

```java
// Satisfied at 12:30 PM today
SingleTriggerTimeCondition timeCondition = new SingleTriggerTimeCondition("12:30");

// Satisfied at a specific date and time
SingleTriggerTimeCondition dateTimeCondition = new SingleTriggerTimeCondition(
    ZonedDateTime.parse("2025-04-30T14:30:00Z"));
```

### NPC Conditions

NPC conditions monitor game NPCs:

#### NpcPresenceCondition

Triggers based on NPC presence:

```java
// Satisfied when any goblin is nearby
NpcPresenceCondition goblinCondition = new NpcPresenceCondition("Goblin");

// Satisfied when any NPC matching a pattern is within a specific distance
NpcProximityCondition guardCondition = new NpcProximityCondition(".*guard.*", 5);
```

#### NpcKillCountCondition

Tracks NPC kills:

```java
// Satisfied after killing 50 goblins
NpcKillCountCondition goblinKillsCondition = new NpcKillCountCondition("Goblin", 50);

// Satisfied after killing any 100 NPCs
NpcKillCountCondition anyKillsCondition = new NpcKillCountCondition(100);
```

### Resource Conditions

Resource conditions monitor item collection, processing, and inventory:

#### LootItemCondition

Tracks items looted:

```java
// Satisfied after looting 100 bones
LootItemCondition bonesCondition = new LootItemCondition("bones", 100);

// Satisfied after looting any items matching a pattern
LootItemCondition oreCondition = new LootItemCondition(".*ore", 50, true);
```

#### ProcessItemCondition

Tracks items processed:

```java
// Satisfied after processing 200 logs into planks
ProcessItemCondition plankMakingCondition = new ProcessItemCondition(
    "logs", "plank", 200);
```

#### GatheredResourceCondition

Tracks resources gathered:

```java
// Satisfied after gathering 500 resources of any type
GatheredResourceCondition resourceCondition = new GatheredResourceCondition(500);

// Satisfied after mining 200 iron ore
GatheredResourceCondition ironMiningCondition = new GatheredResourceCondition(
    "iron ore", 200);
```

### Inventory Conditions

Inventory conditions monitor player inventory states:

```java
// Satisfied when inventory is full
InventoryFullCondition inventoryFullCondition = new InventoryFullCondition();

// Satisfied when inventory contains at least 10 sharks
InventoryItemCountCondition sharksCondition = new InventoryItemCountCondition(
    "shark", 10, Comparison.GREATER_THAN_OR_EQUAL);
```

### Future Condition Types

The following condition types are planned for future implementation:

#### Combat Conditions

- **CombatLevelCondition**: Triggers when player combat level reaches a threshold
- **KillCountCondition**: More generalized kill counter for all enemy types
- **DamageTakenCondition**: Triggers based on damage received
- **SpecialAttackCondition**: Monitors special attack energy

#### Banking Conditions

- **BankValueCondition**: Triggers based on total bank value
- **BankItemCondition**: Monitors specific items in bank
- **GrandExchangeCondition**: Monitors prices or transactions

#### World Conditions

- **WorldPopulationCondition**: Triggers based on world player count
- **WorldHopCondition**: Triggers after a specific number of world hops

#### Quest Conditions

- **QuestCompletionCondition**: Triggers when specific quests are completed
- **QuestPointCondition**: Triggers at quest point thresholds

## Advanced Condition Usage

### Condition Composition

Complex condition trees can be built by nesting logical conditions:

```java
// Create a complex condition structure:
// ((level 70 OR 1000 ores) AND (not in combat)) OR (low hitpoints)
OrCondition rootCondition = new OrCondition();

// Left branch: ((level 70 OR 1000 ores) AND (not in combat))
AndCondition leftBranch = new AndCondition();
OrCondition goalCondition = new OrCondition();
goalCondition.addCondition(new SkillLevelCondition(Skill.MINING, 70));
goalCondition.addCondition(new GatheredResourceCondition("iron ore", 1000));
leftBranch.addCondition(goalCondition);
leftBranch.addCondition(new NotCondition(new CombatCondition()));

// Right branch: (low hitpoints)
SkillValueCondition safetyCondition = new SkillValueCondition(
    Skill.HITPOINTS, 15, Comparison.LESS_THAN);

// Add both branches to root
rootCondition.addCondition(leftBranch);
rootCondition.addCondition(safetyCondition);
```

### Progress Tracking

All conditions provide progress tracking via the `getProgressPercentage()` method:

```java
// Check the overall progress of a condition structure
double progress = rootCondition.getProgressPercentage();
log.info("Overall progress: {}%", progress);

// Get more detailed progress information
String detailedProgress = rootCondition.getStatusInfo(0, true);
log.debug("Detailed condition status:\n{}", detailedProgress);
```

### Condition Diagnostics

Logical conditions provide detailed information about their state:

```java
// Get a detailed description of the entire condition tree
String detailedDescription = rootCondition.getDetailedDescription();
log.debug("Condition structure:\n{}", detailedDescription);

// For better UI display with HTML formatting
String htmlDescription = rootCondition.getHtmlDescription(100);
tooltipLabel.setText(htmlDescription);
```

## Plugin Schedule Entry

A `PluginScheduleEntry` represents a scheduled plugin in the system, managing its execution lifecycle:

```java
// Create a schedule entry with interval timing
PluginScheduleEntry entry = new PluginScheduleEntry(
    "Mining Plugin",
    Duration.ofMinutes(30),
    true, // enabled
    false  // don't allow random scheduling
);

// Create a one-time scheduled entry
PluginScheduleEntry oneTimeEntry = PluginScheduleEntry.createOneTimeSchedule(
    "Daily Task Plugin",
    ZonedDateTime.parse("2025-04-24T08:00:00Z"),
    true  // enabled
);

// Add conditions
entry.addStartCondition(new TimeWindowCondition(
    LocalTime.of(8, 0), 
    LocalTime.of(12, 0)
));
entry.addStopCondition(new ItemQuantityCondition("coal", 1000));

// Check if the plugin is due to run
boolean shouldStart = entry.isDueToRun();

// Start the plugin
if (shouldStart) {
    entry.start();
}

// Check if stop conditions are met
boolean shouldStop = entry.shouldStop();

// Stop the plugin gracefully
if (shouldStop) {
    entry.softStop(true); // true = successful run
}
```

### Key Methods in PluginScheduleEntry

- `isDueToRun()` - Checks if the plugin should start based on conditions
- `start()` - Starts the plugin and begins monitoring stop conditions
- `softStop(boolean)` - Initiates a graceful stop of the plugin
- `hardStop(boolean)` - Forces an immediate stop of the plugin
- `shouldStop()` - Checks if stop conditions are met
- `addStartCondition(Condition)` - Adds a new start condition
- `addStopCondition(Condition)` - Adds a new stop condition

## Event System

The scheduler uses events for communication between plugins and the scheduler:

### PluginScheduleEntrySoftStopEvent

```java
public class PluginScheduleEntrySoftStopEvent {
    private final Plugin plugin;
    private final ZonedDateTime stopDateTime;
    
    public PluginScheduleEntrySoftStopEvent(Plugin plugin, ZonedDateTime stopDateTime) {
        this.plugin = plugin;
        this.stopDateTime = stopDateTime;
    }
}
```

This event is triggered when the scheduler wants to stop a plugin due to met stop conditions. Plugins can handle this event to perform cleanup before stopping.

### PluginScheduleEntryFinishedEvent

```java
public class PluginScheduleEntryFinishedEvent {
    private final Plugin plugin;
    private final ZonedDateTime finishDateTime;
    private final String reason;
    private final boolean success;
    
    public PluginScheduleEntryFinishedEvent(Plugin plugin, String reason, boolean success) {
        this(plugin, ZonedDateTime.now(), reason, success);
    }
}
```

Plugins can post this event to signal they've completed their task, even if stop conditions aren't met. This allows plugins to self-manage their lifecycle.

## Use Cases

### 1. Time-Based Training Script

```java
@Override
public LogicalCondition getStopCondition() {
    OrCondition condition = new OrCondition();
    
    // Stop when reaching level 70 Woodcutting
    condition.addCondition(new SkillLevelCondition(Skill.WOODCUTTING, 70));
    
    // OR after 3 hours of runtime
    condition.addCondition(new DurationCondition(Duration.ofHours(3)));
    
    // OR when obtaining 1000 logs
    condition.addCondition(new ItemQuantityCondition("logs", 1000));
    
    return condition;
}
```

### 2. Resource Gathering with Banking

```java
@Override
public LogicalCondition getStopCondition() {
    AndCondition condition = new AndCondition();
    
    // Basic limit conditions in an OR group
    OrCondition limits = new OrCondition();
    limits.addCondition(new ItemQuantityCondition("yew logs", 1000)); // Stop when obtaining 1000 logs
    limits.addCondition(new DurationCondition(Duration.ofHours(4)));  // OR after 4 hours
    
    condition.addCondition(limits);
    
    // Add a lock condition to prevent stopping during critical operations
    condition.addCondition(new LockCondition());
    
    return condition;
}

// In banking code:
private void goToBank() {
    lock(getStopCondition()); // Prevent stopping during banking
    try {
        // Banking operations
        Rs2Bank.openBank();
        Rs2Bank.depositAll();
        // etc.
    } finally {
        unlock(getStopCondition()); // Allow stopping again
    }
}
```

### 3. Combat Script with Safety Features

```java
@Override
public LogicalCondition getStopCondition() {
    OrCondition condition = new OrCondition();
    
    // Stop when HP gets low for safety
    condition.addCondition(new SkillValueCondition(Skill.HITPOINTS, 15, Comparison.LESS_THAN));
    
    // Stop if we run out of food
    condition.addCondition(new ItemCountCondition(".*shark.*", 0, Comparison.EQUALS));
    
    // Stop when target is defeated
    condition.addCondition(new NpcDefeatedCondition("King Black Dragon"));
    
    // Stop after 60 minutes
    condition.addCondition(new DurationCondition(Duration.ofMinutes(60)));
    
    return condition;
}
```

## Advanced Features

### Lock Mechanism for Critical Operations

The scheduler provides a locking mechanism to temporarily prevent plugins from being stopped during critical operations:

```java
// In your plugin:
@Override
public void startScript() {
    while (running) {
        // Normal operation code...
        
        // Critical section that shouldn't be interrupted
        if (needToBank()) {
            lock(getStopCondition()); // Prevent stopping
            try {
                walkToBank();
                openBank();
                depositItems();
                withdrawItems();
            } finally {
                unlock(getStopCondition()); // Allow stopping again
            }
        }
    }
}
```

### Progress Tracking

The condition system tracks progress toward completion:

```java
// For plugin developers
double progress = getStopConditionProgress();
log.info("Plugin progress: {}%", progress);

// For users in UI
String status = "Progress: " + getStopConditionProgress() + "% complete";
```

### Diagnostic Tools

The scheduler provides diagnostic tools to help understand why plugins are starting or stopping:

```java
// Get detailed information about start conditions
String startDiagnostics = pluginScheduleEntry.diagnoseStartConditions();
log.debug(startDiagnostics);

// Get detailed information about stop conditions
String stopDiagnostics = pluginScheduleEntry.diagnoseStopConditions();
log.debug(stopDiagnostics);
```

## Best Practices

1. **Use Appropriate Condition Types** - Choose the right condition type for your needs. For example, use `SkillLevelCondition` for level targets and `DurationCondition` for time limits.

2. **Lock During Critical Operations** - Use the lock mechanism to prevent interruptions during critical operations like banking, trading, or other operations that could leave the player in a bad state if interrupted.

3. **Report Completion** - Use `reportFinished()` to signal task completion when your plugin has successfully accomplished its goal, even if the stop conditions haven't been met yet.

4. **Combine Conditions Logically** - Use `AndCondition`, `OrCondition`, and `NotCondition` to create complex rules that properly represent when your plugin should stop.

5. **Handle Soft Stops Gracefully** - Implement proper cleanup in your `onPluginScheduleEntrySoftStopEvent` handler, saving state and cleaning up resources.

6. **Monitor Progress** - Implement meaningful progress tracking through the `getProgressPercentage()` method in your custom conditions.

7. **Add Detailed Descriptions** - Give conditions meaningful descriptions to help users understand what they're configuring.

8. **Use Reset Methods** - Call `reset()` when appropriate to ensure conditions start fresh when the plugin is restarted.

## Implementing Schedulable Plugins

This section provides a step-by-step guide for implementing the `SchedulablePlugin` interface to make your plugins work with the scheduler system. We'll use the `SchedulableExamplePlugin` as a reference implementation that demonstrates best practices.

### Step 1: Implement the SchedulablePlugin Interface

To make your plugin schedulable, it must implement the `SchedulablePlugin` interface:

```java
@PluginDescriptor(
        name = "My Schedulable Plugin",
        description = "A plugin that can be scheduled to run under specific conditions",
        tags = {"microbot", "scheduler"},
        enabledByDefault = false
)
public class MyPlugin extends Plugin implements SchedulablePlugin {
    // Plugin implementation...
}
```

### Step 2: Define Stop Conditions

The most important method to implement is `getStopCondition()`, which defines when your plugin should stop:

```java
@Override
public LogicalCondition getStopCondition() {
    // Create a logical condition structure for stopping
    OrCondition condition = new OrCondition();
    
    // Add your stop conditions
    condition.addCondition(new SkillLevelCondition(Skill.MINING, 70));
    condition.addCondition(new DurationCondition(Duration.ofHours(2)));
    
    // You can also add a lock condition to prevent stopping during critical operations
    AndCondition rootCondition = new AndCondition();
    rootCondition.addCondition(condition);
    rootCondition.addCondition(new LockCondition("Prevent stopping during critical operations"));
    
    return rootCondition;
}
```

### Step 3: Define Start Conditions (Optional)

You can optionally define when your plugin is allowed to start:

```java
@Override
public LogicalCondition getStartCondition() {
    // Create a logical condition structure for starting
    OrCondition startCondition = new OrCondition();
    
    // Add start conditions (e.g., only start at banks)
    Condition bankCondition = LocationCondition.atBank(BankLocation.GRAND_EXCHANGE, 5);
    startCondition.addCondition(bankCondition);
    
    return startCondition;
}
```

### Step 4: Handle Stop Events

Implement the `onPluginScheduleEntrySoftStopEvent` method to handle graceful shutdowns:

```java
@Override
@Subscribe
public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
    if (event.getPlugin() == this) {
        // Save state, clean up resources
        saveCurrentState();
        
        // Use the plugin manager to stop the plugin
        Microbot.getClientThread().invokeLater(() -> {
            Microbot.getPluginManager().setPluginEnabled(this, false);
            Microbot.getPluginManager().stopPlugin(this);
        });
    }
}
```

### Step 5: Report Completion (Optional)

You can proactively report when your plugin has completed its task:

```java
// Call this method when your task is complete
private void completeTask(boolean success) {
    reportFinished("Task completed successfully", success);
}
```

### Step 6: Use Lock Mechanism for Critical Operations

Implement locking for operations that shouldn't be interrupted:

```java
private void performCriticalOperation() {
    // Lock the plugin to prevent stopping
    lock(getStopCondition());
    
    try {
        // Perform operations that shouldn't be interrupted
        goToBank();
        depositItems();
        withdrawItems();
    } finally {
        // Always unlock afterward
        unlock(getStopCondition());
    }
}
```

## Example: SchedulableExamplePlugin

The `SchedulableExamplePlugin` is a comprehensive example that demonstrates how to implement a schedulable plugin with various condition types. It's designed specifically for testing and showcasing the scheduler system's capabilities.

### Key Features

The example plugin demonstrates:

1. **Multiple Condition Types**: Showcases how to use and combine different types of conditions
2. **Configuration UI**: Advanced configuration UI for all supported condition types
3. **Testing Utilities**: Hotkeys to test various scheduler features
4. **State Management**: Demonstrates proper state saving and restoration
5. **Location-Based Start Conditions**: Shows how to implement location-based plugin activation

### Configuration Structure

The `SchedulableExampleConfig` interface provides an extensive configuration system that allows testing different condition types:

```java
@ConfigGroup("SchedulableExample")
public interface SchedulableExampleConfig extends Config {
    // Time Conditions
    boolean enableTimeCondition();
    int minRuntime();
    int maxRuntime();
    
    // Loot Item Conditions
    boolean enableLootItemCondition();
    String lootItems();
    boolean itemsToLootLogical();
    int minItems();
    int maxItems();
    
    // Plus many more condition types...
}
```

### Creating Dynamic Stop Conditions

The example plugin demonstrates how to dynamically create stop conditions based on user configuration:

```java
private LogicalCondition createStopCondition() {
    // Create an OR condition - we'll stop when ANY of the enabled conditions are met
    OrCondition orCondition = new OrCondition();
    
    // Add enabled conditions based on configuration
    if (config.enableTimeCondition()) {
        orCondition.addCondition(createTimeCondition());
    }
    
    if (config.enableLootItemCondition()) {
        orCondition.addCondition(createLootItemCondition());
    }
    
    if (config.enableGatheredResourceCondition()) {
        orCondition.addCondition(createGatheredResourceCondition());
    }
    
    // Add more condition types...
    
    // Add a lock condition using AND logic so we can prevent stopping during critical operations
    AndCondition andCondition = new AndCondition();
    andCondition.addCondition(orCondition);
    andCondition.addCondition(lockCondition);
    
    return andCondition;
}
```

### Location-Based Start Conditions

The example plugin showcases how to implement location-based start conditions, allowing the plugin to only start when the player is at specific locations:

```java
private LogicalCondition createStartCondition() {
    // Only create start conditions if enabled
    if (!config.enableLocationStartCondition()) {
        return null;
    }
    
    // Create based on selected location type
    if (config.locationStartType() == LocationStartType.BANK) {
        // Bank-based start condition
        BankLocation selectedBank = config.bankStartLocation();
        int distance = config.bankDistance();
        
        OrCondition startCondition = new OrCondition();
        startCondition.addCondition(LocationCondition.atBank(selectedBank, distance));
        return startCondition;
    } 
    else if (config.locationStartType() == LocationStartType.CUSTOM_AREA) {
        // Custom area start condition
        if (config.customAreaActive() && config.customAreaCenter() != null) {
            WorldPoint center = config.customAreaCenter();
            int radius = config.customAreaRadius();
            
            OrCondition startCondition = new OrCondition();
            startCondition.addCondition(LocationCondition.createArea(
                "Custom Start Area", center, radius * 2, radius * 2));
            return startCondition;
        }
    }
    
    return null;
}
```

### Testing Scheduler Features

The example plugin includes hotkeys for testing various scheduler features:

1. **Area Marking**: Define custom areas for location-based conditions
2. **Plugin Finishing**: Test the `PluginScheduleEntryFinishedEvent` to simulate completed tasks
3. **Lock Toggling**: Test the locking mechanism to prevent stopping during critical operations

```java
// HotkeyListener for testing PluginScheduleEntryFinishedEvent
private final HotkeyListener finishPluginHotkeyListener = new HotkeyListener(() -> config.finishPluginHotkey()) {
    @Override
    public void hotkeyPressed() {
        String reason = config.finishReason();
        boolean success = config.reportSuccessful();
        reportFinished(reason, success);
    }
};

// HotkeyListener for toggling the lock condition
private final HotkeyListener lockConditionHotkeyListener = new HotkeyListener(() -> config.lockConditionHotkey()) {
    @Override
    public void hotkeyPressed() {
        boolean newState = toggleLock(currentCondition);
        log.info("Lock condition toggled: {}", newState ? "LOCKED" : "UNLOCKED");
    }
};
```

## Practical Implementation Guide

When implementing the SchedulablePlugin interface in your own plugins, follow these best practices:

### 1. Define Clear Stop Conditions

Carefully consider when your plugin should stop. Common patterns include:

```java
@Override
public LogicalCondition getStopCondition() {
    OrCondition stopWhen = new OrCondition();
    
    // Stop for safety reasons
    stopWhen.addCondition(new SkillValueCondition(Skill.HITPOINTS, 15, Comparison.LESS_THAN));
    
    // Stop when goal is reached
    stopWhen.addCondition(new SkillLevelCondition(Skill.MINING, 70));
    stopWhen.addCondition(new ItemQuantityCondition("iron ore", 1000));
    
    // Stop after maximum time
    stopWhen.addCondition(new DurationCondition(Duration.ofHours(3)));
    
    return stopWhen;
}
```

### 2. Track State Properly

Save and restore state to allow for graceful restarts:

```java
@Override
protected void startUp() {
    // Load saved state
    loadState();
    
    // Begin script
    script.run(config, savedState);
}

@Override
protected void shutDown() {
    if (script != null) {
        // Save current state
        saveState();
        script.shutdown();            
    }
}
```

### 3. Safely Handle Critical Operations

Use the lock mechanism during operations that shouldn't be interrupted:

```java
private void bank() {
    lock(getStopCondition());
    try {
        // Banking logic
        walkToBank();
        openBank();
        depositItems();
        withdrawItems();
    } 
    catch (Exception e) {
        log.error("Error during banking", e);
    }
    finally {
        unlock(getStopCondition());
    }
}
```

### 4. Report Progress and Completion

Proactively report when tasks are completed:

```java
@Override
public void onStopConditionCheck() {
    // Update progress
    itemsCollected = script.getCollectedItemCount();
    
    // Check if we've met our goal
    if (itemsCollected >= targetAmount) {
        reportFinished("Collected " + itemsCollected + " items", true);
    }
}
```

### 5. Define Specific Configurations

Create configuration options for your schedulable plugin:

```java
@ConfigGroup("MyPlugin")
public interface MyPluginConfig extends Config {
    @ConfigItem(
        keyName = "targetLevel",
        name = "Target Level",
        description = "Stop when reaching this level"
    )
    default int targetLevel() {
        return 70;
    }
    
    @ConfigItem(
        keyName = "maxRuntime",
        name = "Maximum Runtime (minutes)",
        description = "Stop after this many minutes"
    )
    default int maxRuntime() {
        return 120;
    }
}
```

## Testing Your Schedulable Plugin

When testing your schedulable plugin, consider these approaches (as demonstrated in the example plugin):

1. **Add Debug Hotkeys**: Create hotkeys to trigger events like the `PluginScheduleEntryFinishedEvent`
2. **Create Visual Indicators**: Add overlays to show condition status
3. **Log Condition States**: Log the state of conditions during execution
4. **Test with Various Conditions**: Try different combinations of conditions to ensure they work together properly
5. **Simulate Edge Cases**: Test what happens when conditions are met during critical operations

## Conclusion

The Plugin Scheduler system provides a powerful framework for creating intelligent, self-managing plugins. By implementing the `SchedulablePlugin` interface and defining appropriate conditions, you can create plugins that start and stop intelligently based on game state, time, or other factors.

Whether you're creating a training script, resource gathering tool, or combat bot, the scheduler system helps you define precisely when it should run and when it should stop, while providing mechanisms for graceful shutdown and state management.