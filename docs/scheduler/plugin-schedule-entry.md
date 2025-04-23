# Plugin Schedule Entry

## Overview

The `PluginScheduleEntry` class is a core component of the Plugin Scheduler system that represents a plugin scheduled for execution. It encapsulates all the information needed to determine when a plugin should start and stop, as well as tracking its execution state and history.

## Class Structure

```java
@Data
@AllArgsConstructor
@Getter
@Slf4j
public class PluginScheduleEntry {
    private transient Plugin plugin;
    private String name;    
    // ... additional fields
    
    public enum StopReason {
        NONE,
        CONDITIONS_MET,
        MANUAL_STOP,
        PLUGIN_FINISHED,
        ERROR,
        SCHEDULED_STOP,
        HARD_STOP_TIMEOUT
    }
}
```

## Key Features

### Plugin Reference and Identification

Each `PluginScheduleEntry` maintains a reference to the actual RuneLite `Plugin` instance it schedules, along with a name for display and identification purposes. The plugin reference is marked as `transient` to ensure it's not serialized when the schedule entry is saved to configuration.

### Start and Stop Conditions

The class uses separate `ConditionManager` instances to manage the conditions that determine when a plugin should start and stop:

```java
// Start conditions determine when the plugin should activate
private ConditionManager startConditions;

// Stop conditions determine when the plugin should deactivate
private ConditionManager stopConditions;
```

These condition managers contain hierarchical logical structures of various condition types (time, skill, resource, location, NPC, and logical conditions) that are evaluated to determine when to start or stop the plugin.

### Scheduling Properties

The class contains various scheduling properties that control its behavior:

- **Enabled/Disabled State**: Controls whether the entry is considered for scheduling.
- **Randomization**: Optional randomization of scheduling times for more natural behavior.
- **Run History**: Tracks when the plugin has been executed in the past.
- **Runtime Statistics**: Records durations of previous executions.
- **Maximum Runtime**: Optional limit on how long a plugin can run.
- **Stop on Conditions Met**: Flag indicating whether the plugin should stop when stop conditions are met.

### Stop Reason Tracking

The `StopReason` enum provides a detailed categorization of why a plugin stopped:

- `NONE`: Plugin hasn't stopped (still running or never started).
- `CONDITIONS_MET`: Stop conditions were satisfied.
- `MANUAL_STOP`: The user manually stopped the plugin.
- `PLUGIN_FINISHED`: The plugin self-reported completion.
- `ERROR`: An error occurred during plugin execution.
- `SCHEDULED_STOP`: The plugin was stopped due to its scheduled time expiring.
- `HARD_STOP_TIMEOUT`: The plugin didn't respond to a soft stop and was forcibly terminated.

### Condition Management Methods

The class provides methods to manipulate its start and stop conditions:

```java
// Add a start condition to determine when the plugin should be activated
public void addStartCondition(Condition condition) {
    startConditions.addUserCondition(condition);
}

// Add a stop condition to determine when the plugin should be deactivated
public void addStopCondition(Condition condition) {
    stopConditions.addUserCondition(condition);
}
```

### Progress Tracking

The class includes methods to track and report the progress of both start and stop conditions:

```java
// Get progress percentage toward stop conditions being met
public double getStopConditionProgress() {
    return stopConditions.getFullConditionProgress();
}

// Get progress percentage toward start conditions being met
public double getStartConditionProgress() {
    return startConditions.getFullConditionProgress();
}
```

### Timing Calculation

The class provides methods to calculate:

- When the plugin is next scheduled to run
- The duration until the next scheduled execution
- Whether the plugin is due to run based on its conditions
- Total and average runtime statistics
- Randomized intervals for more natural scheduling

## Example Usage

```java
// Create a schedule entry for a plugin
PluginScheduleEntry entry = new PluginScheduleEntry(
    myPlugin,         // Reference to the plugin instance
    "MyAwesomeBot",   // Display name
    true              // Enabled state
);

// Add start condition (run at specific time each day)
entry.addStartCondition(new TimeWindowCondition(
    LocalTime.of(8, 0),   // Start at 8:00 AM
    LocalTime.of(10, 0)   // End at 10:00 AM
));

// Add stop condition (run for 30 minutes, or until inventory is full)
OrCondition stopConditions = new OrCondition();
stopConditions.addCondition(new SingleTriggerTimeCondition(
    ZonedDateTime.now().plusMinutes(30)
));
stopConditions.addCondition(new InventoryItemCountCondition(
    ItemID.ANY, 28, 
));
entry.addStopCondition(stopConditions);

// Configure additional properties
entry.setAllowRandomScheduling(true);
entry.setMaximumRuntime(Duration.ofHours(1));
entry.setStopOnConditionsMet(true);
```

## Technical Details

### Serialization and Persistence

The class is designed to be serialized and deserialized, with `transient` markers on fields that shouldn't persist (like the plugin reference). This allows schedule entries to be saved to the RuneLite config and restored when the client restarts.

### Event Integration

The class integrates with the RuneLite event system through its condition managers, which propagate game events to all registered conditions.

### Time-Based Trigger Calculation

The class includes algorithms to calculate when conditions will be satisfied next, especially for time-based conditions, allowing the scheduler to determine the optimal execution order of multiple plugins.

### Plugin Type Detection

The class can check if its plugin implements the `ConditionProvider` interface, which allows for more scheduling and condition management directly from the plugin.