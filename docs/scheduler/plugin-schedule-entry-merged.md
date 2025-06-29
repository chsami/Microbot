# Plugin Schedule Entry

## Overview

The `PluginScheduleEntry` class is the core component of the Plugin Scheduler system, serving as the central data structure that connects plugins with their scheduling configuration. Each entry represents a scheduled plugin and encapsulates all the information needed to determine when a plugin should start and stop, as well as tracking its execution state and history.

This class acts as the bridge between the scheduler orchestrator (`SchedulerPlugin`), the plugin API (`SchedulablePlugin`), and the user interface, managing both user-defined conditions and plugin-provided conditions through separate `ConditionManager` instances.

## Class Definition

```java
@Data
@AllArgsConstructor
@Getter
@Slf4j
public class PluginScheduleEntry implements AutoCloseable {
    // Plugin reference and metadata
    private transient Plugin plugin;
    private String name;    
    private boolean enabled;
    private boolean allowContinue = true;
    private boolean hasStarted = false;
    
    // Condition management
    private ConditionManager startConditionManager;
    private ConditionManager stopConditionManager;
    
    // Scheduling properties
    private boolean allowRandomScheduling = true;
    private int runCount = 0;
    private int priority = 0; // Higher numbers = higher priority
    private boolean isDefault = false;
    
    // State tracking
    private String lastStopReason;
    private boolean lastRunSuccessful;
    private boolean onLastStopUserConditionsSatisfied = false;
    private boolean onLastStopPluginConditionsSatisfied = false; 
    private StopReason lastStopReasonType = StopReason.NONE;
    private Duration lastRunDuration = Duration.ZERO;
    private ZonedDateTime lastRunStartTime;
    private ZonedDateTime lastRunEndTime;
    
    // Stop reason enumeration 
    public enum StopReason {
        NONE("None"),
        MANUAL_STOP("Manually Stopped"),
        PLUGIN_FINISHED("Plugin Finished"),
        ERROR("Error"),
        SCHEDULED_STOP("Scheduled Stop"),
        INTERRUPTED("Interrupted"),
        HARD_STOP("Hard Stop");
        
        private final String description;
        
        // Implementation details...
    }
    
    // Other implementation details...
}
```

## Key Features

### Plugin Reference and Identification

Each `PluginScheduleEntry` maintains a reference to the actual RuneLite `Plugin` instance it schedules, along with a name for display and identification purposes. The plugin reference is marked as `transient` to ensure it's not serialized when the schedule entry is saved to configuration.

### Scheduling Properties

The class includes several properties that directly affect how a plugin gets scheduled:

#### Priority System

```java
private int priority = 0; // Higher numbers = higher priority
```

The `priority` field is used to determine which plugins are considered first during scheduling:

- Higher numbers indicate higher priority
- Plugins with the same priority are grouped together for scheduling decisions
- When multiple plugins are due to run at the same time, the highest priority plugins are considered first

The scheduler will always prefer to run the highest priority plugins first. Only if there are multiple plugins with the same priority level will other factors like the default status, randomization, or weighted selection be considered.

#### Default Plugin Status

```java
private boolean isDefault = false; // Flag to indicate if this is a default plugin
```

The `isDefault` flag marks a plugin as a "default" plugin that can be preempted by non-default plugins:

- Default plugins are lower-priority in the overall scheduling system
- Non-default plugins are always preferred over default plugins with the same priority
- The Scheduler can be configured to stop default plugins when a non-default plugin is about to run
- This allows users to run certain critical plugins at scheduled times without interference

The scheduler configuration includes settings like `prioritizeNonDefaultPlugins` which can automatically stop default plugins when a non-default plugin is scheduled to run soon.

#### Random Scheduling Support

```java
private boolean allowRandomScheduling = true; // Whether this plugin can be randomly scheduled
```

The `allowRandomScheduling` flag controls whether a plugin participates in the weighted random selection process:

- When `true` (the default), the plugin can be selected randomly among other plugins with the same priority
- When `false`, the plugin will be strictly scheduled based on its trigger time and priority
- Non-randomizable plugins are always prioritized over randomizable plugins with the same priority

This property is particularly important for plugins that must run at exact times (like plugins that perform critical actions at specific game events), as setting `allowRandomScheduling = false` will ensure they run exactly when scheduled, without any randomization.

#### Run Count Tracking

```java
private int runCount = 0; // Track how many times this plugin has been run
```

The `runCount` property keeps track of how many times the plugin has been executed and is used for weighted selection:

- Plugins that have run less frequently are given a higher weight in the weighted selection algorithm
- This ensures a balanced distribution of execution time among different plugins
- The weight calculation is: `weight = (maxRuns - runCount + 1)` where `maxRuns` is the highest run count among all plugins

### Dual Condition Management System

#### User Conditions vs. Plugin Conditions

The class uses two distinct sets of conditions, each managed by separate `ConditionManager` instances:

1. **User Conditions**: Defined through the UI by the end user. These are conditions the user configures to determine when a plugin should start or stop.
   - Added via `addStartCondition()` and `addStopCondition()` methods
   - Persist across client sessions
   - Can be modified through the UI

2. **Plugin Conditions**: Defined programmatically by implementing the `SchedulablePlugin` interface. These are conditions defined within the plugin's own code.
   - Provided via `getStartCondition()` and `getStopCondition()` methods
   - Typically define the plugin's own business logic around when it should run
   - Cannot be modified through the UI

Both types of conditions are evaluated separately with their own logical rules, then combined for the final decision:

```java
// For start conditions
private boolean areUserStartConditionsMet() {
    if (startConditionManager.getUserConditions().isEmpty()) {
        return true;
    }
    return startConditionManager.areUserConditionsMet();
}

private boolean arePluginStartConditionsMet() {
    if (startConditionManager.getPluginConditions().isEmpty()) {
        return true;
    }
    return startConditionManager.arePluginConditionsMet();
}

// Both must be satisfied
public boolean isDueToRun() {
    // ... other checks ...
    if (areUserStartConditionsMet() && arePluginStartConditionsMet()) {
        return true;
    }
    return false;
}
```

The same pattern applies to stop conditions. This dual approach provides flexibility while maintaining control - users can automate plugins according to their needs, but plugins can still enforce their own operational requirements.

#### Condition Evaluation Logic

The combination of user and plugin conditions follows these rules:

- **Start Logic**: `(User Start Conditions AND Plugin Start Conditions)`
  - Both sets must be satisfied for the plugin to start
  - If either set is empty, it's treated as automatically satisfied

- **Stop Logic**: `(User Stop Conditions OR Plugin Stop Conditions)`
  - Either set being satisfied is sufficient to stop the plugin
  - If both sets are empty, the plugin won't stop automatically

This provides a balance of control between the user and the plugin developer.

### Stop Mechanism

The `PluginScheduleEntry` class supports several ways a plugin can be stopped, tracked through the `StopReason` enum:

| Stop Reason | Description | Initiated By |
|-------------|-------------|------------|
| `NONE` | Plugin hasn't stopped (still running or never started) | - |
| `MANUAL_STOP` | The user manually stopped the plugin | User |
| `PLUGIN_FINISHED` | The plugin self-reported completion through `reportFinished()` | Plugin |
| `ERROR` | An error occurred during plugin execution | System |
| `SCHEDULED_STOP` | The plugin was stopped due to its scheduled stop conditions being met | Scheduler |
| `INTERRUPTED` | Plugin was interrupted (e.g., client shutdown) | System |
| `HARD_STOP` | Plugin was forcibly terminated after not responding to a soft stop | Scheduler |

#### The Stopping Process

The stopping process follows a sophisticated pattern:

1. **Stop Initiation**: When conditions are met or user triggers a stop, the `stopInitiated` flag is set and the stop process begins
2. **Soft Stop**: A `PluginScheduleEntrySoftStopEvent` is sent to the plugin, allowing it to perform cleanup operations
3. **Grace Period**: The plugin gets time to clean up (based on `softStopRetryInterval`)
4. **Stop Monitoring**: A separate monitoring thread tracks the stopping process
5. **Hard Stop**: If the plugin doesn't respond within `hardStopTimeout`, a forced stop occurs

```java
private void softStop(boolean successfulRun) {
    // Set flags to track stop process
    stopInitiated = true;
    stopInitiatedTime = ZonedDateTime.now();
    lastStopAttemptTime = stopInitiatedTime;
    
    // Create and post the stop event
    PluginScheduleEntrySoftStopEvent stopEvent = new PluginScheduleEntrySoftStopEvent(
        this,
        isRunning(),
        areUserDefinedStopConditionsMet(),
        arePluginStopConditionsMet(),
        lastStopReasonType
    );
    
    // Post event to notify the plugin
    Microbot.getEventBus().post(stopEvent);
    
    // Start monitoring thread to track stop progress
    startStopMonitoringThread(successfulRun);
}
```

#### Stop Monitoring

A dedicated monitoring thread watches the plugin during the stopping process:

```java
private void startStopMonitoringThread(boolean successfulRun) {
    if (isMonitoringStop) {
        return;
    }
    
    isMonitoringStop = true;
    
    stopMonitorThread = new Thread(() -> {
        try {
            // Keep checking until the stop completes or is abandoned
            while (stopInitiated && isMonitoringStop) {
                // Check if plugin has stopped running
                if (!isRunning()) {
                    // Plugin has stopped, update state and exit loop
                    stopInitiated = false;
                    hasStarted = false;
                    // ...other cleanup...
                    break;
                }
                
                // Check every 300ms to be responsive but not wasteful
                Thread.sleep(300);
            }
        } catch (InterruptedException e) {
            // Thread was interrupted, just exit
        } finally {
            isMonitoringStop = false;
        }
    });
    
    stopMonitorThread.setName("StopMonitor-" + name);
    stopMonitorThread.setDaemon(true); // Don't prevent JVM exit
    stopMonitorThread.start();
}
```

This ensures that plugins have a chance to clean up resources before being terminated while still preventing hung processes.

### Condition Management Methods

The class provides methods to manipulate its start and stop conditions:

```java
// Add a start condition to determine when the plugin should be activated
public void addStartCondition(Condition condition) {
    startConditionManager.addUserCondition(condition);
}

// Add a stop condition to determine when the plugin should be deactivated
public void addStopCondition(Condition condition) {
    stopConditionManager.addUserCondition(condition);
}
```

### Condition Watchdogs

Condition watchdogs are scheduled tasks that periodically update conditions from the plugin. This is particularly useful for dynamic conditions that need to be re-evaluated regularly:

```java
public boolean scheduleConditionWatchdogs(long checkIntervalMillis, UpdateOption updateOption) {
    if (!(this.plugin instanceof SchedulablePlugin)) {            
        return false;
    }
    
    SchedulablePlugin schedulablePlugin = (SchedulablePlugin) this.plugin;
    
    // Create suppliers that get the current plugin conditions
    Supplier<LogicalCondition> startConditionSupplier = 
        () -> schedulablePlugin.getStartCondition();
    
    Supplier<LogicalCondition> stopConditionSupplier = 
        () -> schedulablePlugin.getStopCondition();
    
    // Schedule the watchdogs
    startConditionWatchdogFuture = startConditionManager.scheduleConditionWatchdog(
        startConditionSupplier,
        checkIntervalMillis,
        updateOption
    );
    
    stopConditionWatchdogFuture = stopConditionManager.scheduleConditionWatchdog(
        stopConditionSupplier,
        checkIntervalMillis,
        updateOption
    );
    
    return true;
}
```

This mechanism allows plugins to provide fresh conditions at runtime, enabling adaptive behavior based on changing game states.

### Plugin Lock Mechanism

A sophisticated locking system allows plugins to temporarily prevent being stopped during critical operations:

```java
public boolean isLocked() {
    if (!(plugin instanceof SchedulablePlugin)) {
        return false;
    }
    
    SchedulablePlugin schedulablePlugin = (SchedulablePlugin) plugin;
    return schedulablePlugin.isLocked(null);
}

// SchedulablePlugin interface provides these methods:
// boolean lock(Condition stopCondition);
// boolean unlock(Condition stopCondition);
```

This is crucial for operations that should not be interrupted, such as trading, banking, or other sensitive activities.

### Progress Tracking

The class includes methods to track and report the progress of both start and stop conditions:

```java
// Get progress percentage toward stop conditions being met
public double getStopConditionProgress() {
    return stopConditionManager.getFullConditionProgress();
}

// Get progress percentage toward start conditions being met
public double getStartConditionProgress() {
    return startConditionManager.getFullConditionProgress();
}
```

### Timing Calculation

Methods are provided to calculate:

- When the plugin is next scheduled to run
- The duration until the next scheduled execution
- Whether the plugin is due to run based on its conditions
- Total and average runtime statistics
- Randomized intervals for more natural scheduling

```java
// Get the next time this plugin is scheduled to run
public Optional<ZonedDateTime> getCurrentStartTriggerTime() {
    return startConditionManager.getCurrentTriggerTime();
}

// Check if the plugin is due to run based on its conditions
public boolean isDueToRun() {
    // Check basic preconditions
    if (isRunning() || !hasAnyStartConditions()) {
        return false;
    }
    
    // For diagnostic purposes, we may log detailed condition information
    if (Microbot.isDebug()) {
        String diagnosticInfo = diagnoseStartConditions();
        log.debug("\n[isDueToRun] - \n"+diagnosticInfo);
    }
    
    // Check if all start conditions are met (combining both user and plugin conditions)
    return startConditionManager.areAllConditionsMet();
}

// Get a user-friendly string showing when the plugin will next run
public String getNextStartTriggerTimeString() {
    Optional<ZonedDateTime> triggerTime = getCurrentStartTriggerTime();
    if (triggerTime.isPresent()) {
        ZonedDateTime now = ZonedDateTime.now();
        Duration until = Duration.between(now, triggerTime.get());
        if (until.isNegative()) {
            return "Due now";
        }
        return formatDuration(until) + " from now";
    }
    return "Not scheduled";
}
```

## The Plugin Scheduling Algorithm

The plugin scheduling process in the `SchedulerPlugin` uses several key properties from `PluginScheduleEntry` to determine which plugin to run next:

1. **Basic Filters**:
   - Only enabled plugins are considered
   - Only plugins that are due to run (conditions satisfied) are considered
   - Plugins with `stopInitiated` are excluded

2. **Priority-Based Selection**:
   - Plugins are first filtered by the highest `priority` value
   - Among equally high priority plugins, non-default plugins (`isDefault = false`) are preferred

3. **Random vs. Non-Random Selection**:
   - Non-randomizable plugins (`allowRandomScheduling = false`) are always preferred over randomizable ones
   - Non-randomizable plugins are strictly ordered by their trigger times (earliest first)

4. **Weighted Selection for Randomizable Plugins**:
   - For randomizable plugins with the same priority and default status, a weighted selection is applied
   - Plugins with lower `runCount` values receive higher weights
   - The weight formula: `weight = (maxRuns - plugin's runCount + 1)`
   - This creates a balanced distribution, with less-frequently run plugins having a higher chance of selection

5. **Final Selection**:
   - If multiple plugins have the exact same trigger time and other factors, a stable sort by name and object identity is used

### Non-Default Plugin Prioritization

The scheduler can be configured to interrupt or prevent default plugins from running when a non-default plugin is scheduled soon:

```java
// In SchedulerPlugin.java
if (prioritizeNonDefaultPlugins) {
    // Look for any upcoming non-default plugin within the configured time window
    PluginScheduleEntry upcomingNonDefault = getNextScheduledPlugin(false, 
                                                Duration.ofMinutes(nonDefaultPluginLookAheadMinutes))
        .filter(plugin -> !plugin.isDefault())
        .orElse(null);
        
    // If the next due plugin is a default plugin, don't start it
    if (nextDuePlugin.isPresent() && nextDuePlugin.get().isDefault()) {
        log.info("Not starting default plugin '{}' because non-default plugin '{}' is scheduled within {} minutes",
            nextDuePlugin.get().getCleanName(),
            upcomingNonDefault.getCleanName(),
            nonDefaultPluginLookAheadMinutes);
        return;
    }
}
```

This mechanism ensures that high-priority, non-default plugins can run as scheduled without being blocked by long-running default plugins.

## Integration with SchedulablePlugin

When a plugin implements the `SchedulablePlugin` interface, `PluginScheduleEntry` detects this and interacts with it differently:

1. Retrieves plugin-defined start and stop conditions
2. Sets up condition watchdogs to periodically refresh those conditions
3. Routes stop events to the plugin so it can handle cleanup
4. Respects the plugin's lock status when stopping
5. Tracks whether the plugin self-reports completion

This creates a strong relationship between the plugin and its schedule entry, allowing for sophisticated scheduling behaviors.

```java
// Check if the plugin implements SchedulablePlugin
public boolean isSchedulablePlugin() {
    return this.plugin instanceof SchedulablePlugin;
}

// Register plugin conditions
private boolean registerPluginStartingConditions(UpdateOption updateOption) {
    if (!(this.plugin instanceof SchedulablePlugin)) {
        return false;
    }
    
    SchedulablePlugin provider = (SchedulablePlugin) plugin;
    LogicalCondition pluginLogic = provider.getStartCondition();
    
    if (pluginLogic != null) {
        return startConditionManager.updatePluginCondition(pluginLogic, updateOption);
    }
    
    return false;
}
```

## Usage Example

Here's an extended example showing how to create and configure a `PluginScheduleEntry` with scheduling properties:

```java
// Create a high-priority, non-default schedule entry for a critical plugin
PluginScheduleEntry criticalEntry = new PluginScheduleEntry(
    criticalPlugin,     // Plugin instance
    "Critical Task",    // Display name
    true                // Enabled
);
criticalEntry.setPriority(10);         // High priority (default is 0)
criticalEntry.setDefault(false);       // Not a default plugin
criticalEntry.setAllowRandomScheduling(false); // Must run at exact scheduled time

// Add start condition (when the plugin should activate)
criticalEntry.addStartCondition(new TimeWindowCondition(
    LocalTime.of(12, 0),
    LocalTime.of(12, 30)
));

// Create a low-priority default plugin that can be randomized
PluginScheduleEntry defaultEntry = new PluginScheduleEntry(
    backgroundPlugin,   // Plugin instance
    "Background Task",  // Display name
    true                // Enabled
);
defaultEntry.setPriority(0);           // Low priority
defaultEntry.setDefault(true);         // This is a default plugin
defaultEntry.setAllowRandomScheduling(true); // Can be randomly scheduled

// Register with the scheduler
schedulerPlugin.addScheduledPlugin(criticalEntry);
schedulerPlugin.addScheduledPlugin(defaultEntry);
```

In this example, the critical task will always run at exactly 12:00-12:30, while the background task will run based on weighted selection when no critical tasks are scheduled.

## Internal Lifecycle

The internal plugin lifecycle follows this pattern:

1. **Creation**: Plugin schedule entry is created and configured
2. **Registration**: Entry is registered with the `SchedulerPlugin`
3. **Start Check**: Periodically checks if start conditions are met
4. **Activation**: When conditions are met, plugin is started
5. **Monitoring**: While running, stop conditions are evaluated
6. **Deactivation**: When stop conditions are met, stopping process begins
7. **Cleanup**: Plugin gets opportunity to clean up before full stop
8. **Reset**: Conditions are reset for the next activation cycle

## Key Methods

### Starting and Stopping

```java
// Start the plugin
public boolean start(boolean logConditions) {
    if (getPlugin() == null || !this.isEnabled() || isRunning()) {
        return false;
    }
    
    // Log conditions if requested
    if (logConditions) {
        logStartConditionsWithDetails();
        logStopConditionsWithDetails();
    }
    
    // Reset conditions if needed
    if (!this.allowContinue || lastStopReasonType != StopReason.INTERRUPTED) {
        resetStopConditions();
    }
    
    // Set state and start the plugin
    this.lastRunStartTime = ZonedDateTime.now();
    Microbot.startPlugin(plugin);
    return true;
}

// Check if stop conditions are met
public boolean shouldStop() {
    if (!isRunning()) {
        return false;
    }
    
    // Check if the plugin is locked
    if (isLocked()) {
        return false;
    }
    
    // Check both plugin and user conditions
    return arePluginStopConditionsMet() && areUserDefinedStopConditionsMet();
}
```

### Condition Evaluation

```java
// Check if plugin-defined stop conditions are met
private boolean arePluginStopConditionsMet() {
    if (stopConditionManager.getPluginConditions().isEmpty()) {
        return true;
    }
    return stopConditionManager.arePluginConditionsMet();
}

// Check if user-defined stop conditions are met
private boolean areUserDefinedStopConditionsMet() {
    if (stopConditionManager.getUserConditions().isEmpty()) {
        return true;
    }
    return stopConditionManager.areUserConditionsMet();
}
```

### Condition Validation and Optimization

```java
// Validates stop conditions structure and logs issues
private void validateStopConditions() {
    LogicalCondition stopLogical = getStopConditionManager().getFullLogicalCondition();
    if (stopLogical != null) {
        List<String> issues = stopLogical.validateStructure();
        if (!issues.isEmpty()) {
            log.warn("Validation issues found in stop conditions for '{}':", name);
            for (String issue : issues) {
                log.warn("  - {}", issue);
            }
        }
    }
}

// Optimizes condition structures by flattening unnecessary nesting
private void optimizeConditionStructures() {
    LogicalCondition startLogical = getStartConditionManager().getFullLogicalCondition();
    if (startLogical != null) {
        boolean optimized = startLogical.optimizeStructure();
        if (optimized) {
            log.debug("Optimized start condition structure for '{}'", name);
        }
    }
    
    LogicalCondition stopLogical = getStopConditionManager().getFullLogicalCondition();
    if (stopLogical != null) {
        boolean optimized = stopLogical.optimizeStructure();
        if (optimized) {
            log.debug("Optimized stop condition structure for '{}'", name);
        }
    }
}
```

## Stop Flags and Monitoring

The `stopInitiated` flag is a crucial part of the stopping process, indicating that the plugin is in the process of being stopped. This flag is set in the `softStop()` method when the stop process begins and cleared when the plugin is fully stopped:

```java
// Set when stop process begins
stopInitiated = true;
stopInitiatedTime = ZonedDateTime.now();

// Checked by monitoring thread
while (stopInitiated && isMonitoringStop) {
    if (!isRunning()) {
        // Plugin has stopped, clear the flag
        stopInitiated = false;
        break;
    }
    Thread.sleep(300);
}
```

The stop monitoring thread continuously checks if the plugin has stopped running and updates the state once the stop is complete. If the plugin doesn't stop within the configured timeout, a hard stop may be initiated.

### Hard Stop Fallback

If a plugin doesn't respond to a soft stop within the configured timeout, a hard stop is performed:

```java
private void hardStop(boolean successfulRun) {
    log.warn("Performing hard stop on plugin '{}'", name);
    
    // Force stop the plugin
    Microbot.stopPlugin(plugin);
    
    // Update state
    lastStopReasonType = StopReason.HARD_STOP;
    lastStopReason = "Plugin did not respond to soft stop and was forcibly terminated";
    stopInitiated = false;
    hasStarted = false;
    
    // ...additional cleanup...
}
```

## Best Practices

When working with `PluginScheduleEntry`:

1. Prefer setting both start and stop conditions for predictable behavior
2. Use plugin-defined conditions for essential business logic
3. Allow user-defined conditions for customization
4. Consider plugin safety with appropriate lock usage during critical operations
5. Use the soft-stop mechanism to ensure your plugin can clean up properly
6. Make good use of the `reportFinished()` method to signal natural completion
7. Set appropriate priority levels - use higher values only for truly critical plugins
8. Mark essential time-sensitive plugins as `allowRandomScheduling = false`
9. Use the `isDefault` flag for background plugins that can be interrupted

## Advanced Features

The class includes several advanced features:

- Condition validation and optimization
- Detailed logging and diagnostics
- Time condition randomization for bot detection prevention
- Support for complex logical hierarchies through the `LogicalCondition` framework
- Serialization support for configuration persistence

## Relationship with SchedulerPlugin

The `PluginScheduleEntry` class works closely with the `SchedulerPlugin` class, which acts as the orchestrator for the entire scheduling system:

1. **SchedulerPlugin** manages a collection of `PluginScheduleEntry` instances
2. It periodically checks each entry to determine if it should start or stop
3. It handles the scheduling of breaks between plugin executions
4. It manages the overall scheduler state (IDLE, EXECUTING, STOPPING, etc.)
5. It provides methods to add, remove, and configure scheduled plugins

This separation of concerns allows the `PluginScheduleEntry` to focus on the state and management of an individual plugin while the `SchedulerPlugin` handles the higher-level orchestration.

```java
// In SchedulerPlugin:
public void checkPluginsToStart() {
    for (PluginScheduleEntry entry : scheduledPlugins) {
        if (entry.isDueToRun()) {
            entry.start(true);
        }
    }
}

public void checkPluginsToStop() {
    for (PluginScheduleEntry entry : getRunningScheduledPlugins()) {
        if (entry.shouldStop()) {
            entry.initiateStop(PluginScheduleEntry.StopReason.SCHEDULED_STOP, "Stop conditions met", true);
        }
    }
}
```
