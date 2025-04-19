# Scheduler Plugin

## Overview

The `SchedulerPlugin` class is the central component of the Plugin Scheduler system. It serves as the orchestrator for automated plugin execution, managing when plugins start and stop based on configurable conditions and schedules.

## Class Structure

```java
@Slf4j
@PluginDescriptor(
    name = PluginDescriptor.Mocrosoft + PluginDescriptor.VOX + "Plugin Scheduler",
    description = "Schedule plugins at your will",
    tags = {"microbot", "schedule", "automation"},
    enabledByDefault = false
)
public class SchedulerPlugin extends Plugin {
    // Dependencies, state variables, configuration
    // Methods for plugin lifecycle management and scheduling
}
```

## Key Features

### Plugin Lifecycle Management

The `SchedulerPlugin` manages the complete lifecycle of scheduled plugins:

- **Registration**: Allows plugins to be registered with the scheduler
- **Activation**: Starts plugins when their start conditions are met
- **Monitoring**: Tracks running plugins and evaluates their stop conditions
- **Deactivation**: Stops plugins when their stop conditions are met or on manual intervention
- **Persistence**: Saves and loads scheduled plugin configurations

### Scheduling Algorithm

The scheduler implements a sophisticated algorithm to determine which plugins should run and when:

```java
/**
 * Core method to find the next plugin based on various criteria.
 * This uses sortPluginScheduleEntries with weighted selection to handle randomizable plugins.
 * 
 * @param isDueToRun If true, only returns plugins that are due to run now
 * @param timeWindow If not null, limits to plugins triggered within this time window
 * @return Optional containing the next plugin to run, or empty if none match criteria
 */
public Optional<PluginScheduleEntry> getNextScheduledPlugin(boolean isDueToRun, Duration timeWindow) {
    // Implementation details...
}
```

This algorithm considers:

- Whether plugins are enabled
- Whether start conditions are satisfied
- How long since each plugin has run (for balanced distribution)
- Randomization factors for more natural scheduling
- Priority settings for plugin execution order

### State Management

The scheduler maintains comprehensive state tracking:

```java
// Current state of the scheduler
@Getter
private SchedulerState currentState = SchedulerState.UNINITIALIZED;

// Currently running plugin
@Getter
private PluginScheduleEntry currentPlugin;

// List of all scheduled plugins
private List<PluginScheduleEntry> scheduledPlugins = new ArrayList<>();
```

The `SchedulerState` enum defines various states such as:

- `UNINITIALIZED`: Initial state before scheduler has started
- `IDLE`: Scheduler is running but no plugin is active
- `RUNNING`: A plugin is currently running
- `ON_BREAK`: The scheduler is taking a break between plugin executions
- `STOPPING`: A plugin is in the process of stopping
- `STARTING`: A plugin is in the process of starting
- `PAUSED`: The scheduler has been manually paused

### Integration with Other Systems

The scheduler integrates with other RuneLite + Microbot plugins and systems:

- **Break Handler**: Coordinates breaks between plugin executions
- **Auto Login**: Manages login/logout for scheduled plugins
- **Antiban**: Integrates with antiban features to make bot behavior more natural
- **Event System**: Uses RuneLite's event system to react to game state changes

```java
// Integration with break handler
private boolean isBreakHandlerEnabled() {
    // Implementation details...
}

// Integration with login system
private boolean isAutoLoginEnabled() {
    // Implementation details...
}

// Integration with antiban system
private boolean isAntibanEnabled() {
    // Implementation details...
}
```

### User Interface

The scheduler provides a comprehensive UI for managing scheduled plugins:

```java
@Inject
private ClientToolbar clientToolbar;

private NavigationButton navButton;
private SchedulerPanel panel;
private SchedulerWindow schedulerWindow;

public void openSchedulerWindow() {
    // Implementation details...
}
```

The UI allows users to:

- Add, remove, and configure scheduled plugins
- View the current state of the scheduler
- Monitor plugin execution history and statistics
- Configure conditions for starting and stopping plugins
- Manually start and stop plugins

### Condition Evaluation

The scheduler regularly evaluates conditions to determine when plugins should start or stop:

```java
/**
 * Checks if the current plugin should be stopped based on conditions
 */
private void checkCurrentPlugin() {
    // Implementation details...
}

/**
 * Schedules the next plugin to run if none is running
 */
private void scheduleNextPlugin() {
    // Implementation details...
}
```

### Weighted Selection Algorithm

For scenarios where multiple plugins could run, the scheduler uses a weighted selection algorithm to choose which plugin to run next:

```java
/**
 * Selects a plugin using weighted random selection.
 * Plugins with lower run counts have higher probability of being selected.
 */
private PluginScheduleEntry selectPluginWeighted(List<PluginScheduleEntry> plugins) {
    // Implementation details...
}
```

This provides a balanced distribution of plugin execution while maintaining an element of randomness.

## Technical Details

### Thread Management

The scheduler uses a combination of the RuneLite game thread and background threads:

- Game thread: Used for game-specific operations that must occur on the main thread
- Scheduled executor: Used for regular checking of conditions and scheduling operations
- Dedicated threads: Used for monitoring login states and other background operations

```java
@Inject
private ScheduledExecutorService executorService;

private ScheduledFuture<?> updateTask;
private Thread loginMonitor;
```

### State Transitions

The scheduler implements clear state transitions with validation:

1. `UNINITIALIZED` → `IDLE`: When scheduler starts but no plugin is running
2. `IDLE` → `STARTING`: When a plugin is about to start
3. `STARTING` → `RUNNING`: When a plugin has been successfully started
4. `RUNNING` → `STOPPING`: When a plugin's stop conditions are met or manual stop requested
5. `STOPPING` → `IDLE`: When a plugin has been successfully stopped
6. `IDLE` → `ON_BREAK`: When a break is initiated between plugin executions
7. `ON_BREAK` → `IDLE`: When a break completes
8. `*` → `PAUSED`: When scheduler is manually paused
9. `PAUSED` → `*`: When scheduler is resumed

### Event Handling

The scheduler subscribes to various RuneLite events to react to game state changes:

```java
@Subscribe
public void onGameStateChanged(GameStateChanged gameStateChanged) {
    // Implementation details...
}

@Subscribe
public void onStatChanged(StatChanged event) {
    // Implementation details...
}
```

### Serialization and Persistence

The scheduler implements serialization and deserialization of `PluginScheduleEntry` objects to save and load configurations:

```java
public void saveScheduledPlugins() {
    // Implementation details...
}

private void loadScheduledPlugin() {
    // Implementation details...
}
```

This allows users to create complex scheduling configurations that persist across client restarts.

## Usage Example

```java
// Get the scheduler plugin instance
SchedulerPlugin scheduler = injector.getInstance(SchedulerPlugin.class);

// Create a schedule entry for a plugin
PluginScheduleEntry entry = new PluginScheduleEntry(
    myPlugin,
    "Mining Bot",
    true  // enabled
);

// Add start and stop conditions
entry.addStartCondition(new TimeWindowCondition(
    LocalTime.of(9, 0),
    LocalTime.of(12, 0)
));
entry.addStopCondition(new InventoryItemCountCondition(
    ItemID.IRON_ORE, 
    28, 
    
));

// Register the entry with the scheduler
scheduler.addScheduledPlugin(entry);

// Start the scheduler
scheduler.startScheduler();
```