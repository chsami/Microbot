# SchedulablePlugin Interface

## Overview

The `SchedulablePlugin` interface is the core integration point between standard RuneLite plugins and the Microbot Plugin Scheduler system. It defines a contract that plugins must implement to participate in the automated scheduling system, allowing them to be started and stopped based on configurable conditions while maintaining control over their execution lifecycle.

This interface leverages the event-based architecture of RuneLite to enable communication between the scheduler and plugins, with methods that define start and stop conditions, handle state transitions, and provide mechanisms for critical section protection.

## Interface Architecture

The `SchedulablePlugin` interface is designed with a combination of required methods that must be implemented by each plugin and default methods that provide standardized behavior. This approach allows plugins to focus on their specific scheduling requirements while inheriting common functionality from the interface.

The interface follows these core design principles:

1. **Condition-based Scheduling**: Uses logical conditions to determine when plugins should start or stop
2. **Event-driven Communication**: Relies on RuneLite's event system for lifecycle notifications
3. **Graceful Termination**: Provides mechanisms for both soft and hard stops
4. **Critical Section Protection**: Implements a locking system to prevent interruption during sensitive operations

## Key Methods

### Essential Methods for Implementation

#### `void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event)`

This is the only method without a default implementation that plugins must implement. It handles the soft stop request from the scheduler, which is triggered when stop conditions are met or when manual stop is initiated. The implementation should ensure the plugin stops gracefully, preserving state and cleaning up resources before terminating.

The implementation is expected to:

1. Verify the event is targeted at this specific plugin
2. Save any current state if needed
3. Clean up resources and close interfaces
4. Schedule the actual stop on the RuneLite client thread
5. Disable and stop the plugin through the Microbot plugin manager

### Start and Stop Condition Methods

#### `LogicalCondition getStartCondition()`

Defines when a plugin is eligible to start. The default implementation returns an empty `AndCondition`, meaning the plugin can start at any time if no specific condition is provided. Plugins can override this to specify precise starting conditions like time windows, player locations, or game states.

The scheduler evaluates this condition before starting the plugin. If it returns `null` or the condition is met, the plugin is eligible to start.

#### `LogicalCondition getStopCondition()`

Specifies when a plugin should stop running. The default implementation returns an empty `AndCondition`, meaning the plugin would run indefinitely unless manually stopped. Plugins should override this to define appropriate stop conditions, which might include:

- Time limits or specific time windows
- Resource counts (items collected, XP gained)
- Player state (inventory full, health low)
- Game state (logged out, in combat)

The scheduler continuously monitors these conditions while the plugin runs.

### State Management Methods

#### `void onStopConditionCheck()`

This method is called periodically (approximately once per second) while the plugin is running, just before the stop conditions are evaluated. Its purpose is to allow plugins to update any dynamic state information that might affect condition evaluation.

This is particularly useful when conditions depend on changing game state, such as inventory contents or skill levels. Plugins can use this hook to keep condition evaluation accurate without having to implement separate timer logic.

#### `void reportFinished(String reason, boolean success)`

Provides a way for plugins to proactively indicate completion without waiting for stop conditions to trigger. This method posts a `PluginScheduleEntryFinishedEvent` that notifies the scheduler the plugin has finished its task.

The implementation handles various edge cases:

- If the scheduler isn't loaded, it directly stops the plugin
- If no plugin is currently running in the scheduler, it stops itself
- If another plugin is running, it gracefully handles the mismatch

This method is commonly used when a plugin has met its objective (like completing a quest) or encountered a situation where it cannot continue (like running out of resources).

#### `boolean isHardStoppable()`

Indicates whether a plugin supports being forcibly terminated if it doesn't respond to a soft stop request. The default implementation returns `false`, meaning plugins will only be stopped gracefully. Plugins can override this to allow hard stops in specific situations.

### Lock Management Methods

The interface includes a comprehensive locking system that prevents plugins from being stopped during critical operations.

#### `LockCondition getLockCondition(Condition stopConditions)`

Retrieves the lock condition associated with a plugin's stop conditions. The default implementation recursively searches through the condition structure to find a `LockCondition` instance.

#### `boolean isLocked(Condition stopConditions)`

Checks if the plugin is currently locked, preventing it from being stopped.

#### `boolean lock(Condition stopConditions)`

Activates the lock to prevent the plugin from being stopped. Returns `true` if successful.

#### `boolean unlock(Condition stopConditions)`

Deactivates the lock, allowing the plugin to be stopped when conditions are met. Returns `true` if successful.

#### `Boolean toggleLock(Condition stopConditions)`

Toggles the lock state and returns the new state (`true` for locked, `false` for unlocked).

## Stop Mechanisms

Plugins can be stopped through various mechanisms:

1. **Manual Stop**: User explicitly stops the plugin
   - Appears as `StopReason.MANUAL_STOP`
   - Highest priority, will always attempt to stop
   - Flow: User Interface → SchedulerPlugin.forceStopCurrentPluginScheduleEntry() → PluginScheduleEntry.stop() → Plugin stops

2. **Plugin Finished**: Plugin self-reports completion using `reportFinished()`
   - Appears as `StopReason.PLUGIN_FINISHED`
   - Indicates normal completion
   - Flow: Plugin.reportFinished() → PluginScheduleEntryFinishedEvent → SchedulerPlugin.onPluginScheduleEntryFinishedEvent() → PluginScheduleEntry.stop() → Plugin stops

3. **Stop Conditions Met**: When plugin or user-defined stop conditions are satisfied
   - Appears as `StopReason.SCHEDULED_STOP`
   - Follows the soft-stop/hard-stop pattern
   - Flow: SchedulerPlugin.checkCurrentPlugin() → PluginScheduleEntry.checkConditionsAndStop() → PluginScheduleEntry.softStop() → PluginScheduleEntrySoftStopEvent → Plugin.onPluginScheduleEntrySoftStopEvent() → Plugin stops

4. **Error**: An exception occurs in the plugin
   - Appears as `StopReason.ERROR`
   - Immediate stop without soft-stop sequence
   - Flow: Exception → PluginScheduleEntry.setLastStopReasonType(ERROR) → Plugin disabled → SchedulerPlugin returns to SCHEDULING state

5. **Hard Stop**: Forced termination after soft-stop timeout
   - Appears as `StopReason.HARD_STOP`
   - Last resort when plugin doesn't respond to soft-stop
   - Flow: Timeout after soft-stop → PluginScheduleEntry.hardStop() → Microbot.stopPlugin() → Plugin forcibly terminated

## Integration with Scheduler Architecture

The `SchedulablePlugin` interface integrates with the broader scheduler architecture in several key ways:

1. **Plugin Registry**: The scheduler maintains a registry of `PluginScheduleEntry` objects, each referencing a `Plugin` that implements `SchedulablePlugin`.

2. **Condition Management**: The scheduler continuously evaluates both start and stop conditions through a `ConditionManager` that separates plugin-defined conditions from user-defined ones.

3. **Event Communication**: The scheduler posts events like `PluginScheduleEntrySoftStopEvent` to initiate plugin stops, and receives events like `PluginScheduleEntryFinishedEvent` when plugins self-report completion.

4. **Lifecycle Management**: The scheduler controls when plugins are enabled or disabled based on their schedule and conditions, but delegates the actual stopping process to the plugins themselves through the interface methods.

The relationship can be visualized as follows:

```ascii
┌─────────────────────┐     schedules      ┌───────────────────┐
│   SchedulerPlugin   ├────────────────────┤ PluginScheduleEntry│
│   (Orchestrator)    │                    │   (Data Model)    │
└─────────┬───────────┘                    └─────────┬─────────┘
          │                                          │
          │ manages                                  │
          │                                          │
          ▼                                          ▼
┌─────────────────────┐   implements    ┌───────────────────────┐
│ Regular RuneLite    │◄───────────────┤  SchedulablePlugin    │
│     Plugin          │                 │      (API)           │
└─────────────────────┘                 └───────────────────────┘
```

When the scheduler is running:

1. The `SchedulerPlugin` periodically checks each registered `PluginScheduleEntry`
2. If a plugin implements `SchedulablePlugin`, its conditions are retrieved and evaluated
3. The `SchedulerPlugin` makes decisions about starting/stopping based on these conditions
4. Events are sent back to the plugin through interface methods like `onPluginScheduleEntrySoftStopEvent`

## Plugin Conditions vs. User Conditions

An important concept in the scheduler system is the distinction between plugin conditions and user conditions:

### Plugin Conditions

- **Source**: Defined programmatically by implementing `getStartCondition()` and `getStopCondition()`
- **Purpose**: Express the plugin's intrinsic requirements and business logic
- **Control**: Controlled by the plugin developer
- **Example**: A mining plugin might define "stop when inventory is full" as a plugin condition because it's fundamental to the plugin's functionality
- **Default Behavior**: When a plugin doesn't define specific conditions (returns empty `AndCondition`), it has no inherent restrictions on when it can start or stop

### User Conditions

- **Source**: Added through UI or configuration by the end user
- **Purpose**: Express user preferences and personalization
- **Control**: Controlled by the end user
- **Example**: A user might add "only run between 8pm-2am" as a user condition because it's their preferred play time
- **Default Behavior**: If no user conditions are defined, the plugin will run continuously until manually stopped

### How They Work Together

The `PluginScheduleEntry` class maintains both sets of conditions using separate logical structures:

1. **Start Logic**: Plugin AND User start conditions must be met for the plugin to start
2. **Stop Logic**: Plugin OR User stop conditions must be met for the plugin to stop

This gives both the plugin developer and the end user appropriate control while ensuring proper plugin operation:

- The plugin can't start unless both the plugin requirements AND user preferences are satisfied
- The plugin will stop if EITHER the plugin determines it should stop OR the user's conditions determine it should stop

### Full User Control Scenario

When a plugin implements `SchedulablePlugin` but doesn't override `getStartCondition()` or `getStopCondition()` (or returns empty conditions), the execution is fully controlled by user-defined conditions:

1. **Starting**: The plugin can start any time, but only when user-defined start conditions are met
2. **Stopping**: The plugin will only stop when user-defined stop conditions are met or the user manually stops it
3. **Self-Reporting**: Even without defined conditions, the plugin can still use `reportFinished()` to signal completion

This design allows plugins to be made schedulable with minimal implementation effort while still giving users complete control over when they run.

## Implementation Guidelines

### Lock Condition Management

Critical operations in plugins should be protected with the locking mechanism:

1. Create a `LockCondition` in your stop condition structure
2. Call `lock()` before entering critical sections
3. Always call `unlock()` in a finally block to ensure the lock is released
4. Avoid long-running operations while locked, as this prevents the scheduler from stopping the plugin

### Condition State Updates

For plugins with dynamic stop conditions:

1. Override `onStopConditionCheck()` to update condition state
2. Keep these updates lightweight and focused
3. Avoid heavy computation or network operations
4. Update counters, flags, or other simple state variables

### Graceful Stopping

When implementing the stop event handler:

1. Check that the event is intended for this plugin
2. Save any critical state information
3. Close any open interfaces or dialogs
4. Release resources and cancel any pending operations
5. Use the client thread to ensure thread safety

## Event-Driven Communication Mechanism

The Plugin Scheduler system relies heavily on RuneLite's event bus for communication between components. Two primary events facilitate this communication:

### 1. PluginScheduleEntrySoftStopEvent

This event represents a request from the scheduler to a plugin asking it to gracefully stop execution.

**Flow:**

1. **Event Creation:** When stop conditions are met, PluginScheduleEntry.softStop() creates the event
2. **Event Posting:** The event is posted to the RuneLite EventBus
3. **Event Handling:** The target plugin's onPluginScheduleEntrySoftStopEvent() method is called
4. **Response:** The plugin performs cleanup operations and stops itself

**Example:**

```java
// Inside PluginScheduleEntry.softStop()
Microbot.getClientThread().runOnSeperateThread(() -> {
    Microbot.getEventBus().post(new PluginScheduleEntrySoftStopEvent(plugin, ZonedDateTime.now()));
    return false;                
});

// Inside the plugin implementation
@Subscribe
@Override
public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
    if (event.getPlugin() == this) {
        // Cleanup operations
        Microbot.getClientThread().invokeLater(() -> {
            Microbot.stopPlugin(this);
        });
    }
}
```

### 2. PluginScheduleEntryFinishedEvent

This event allows plugins to proactively inform the scheduler that they have completed their task and should be stopped.

**Flow:**

1. **Event Creation:** Plugin calls reportFinished() when its task is complete
2. **Validation:** reportFinished() verifies the scheduler is active and this plugin is the current running plugin
3. **Event Posting:** A PluginScheduleEntryFinishedEvent is posted to the RuneLite EventBus
4. **Event Handling:** SchedulerPlugin.onPluginScheduleEntryFinishedEvent() processes the event
5. **Plugin Stopping:** The scheduler initiates a stop with PLUGIN_FINISHED reason

**Example:**

```java
// Inside the plugin when a task is complete
reportFinished("Mining task completed - inventory full", true);

// Inside the reportFinished() method
Microbot.getEventBus().post(new PluginScheduleEntryFinishedEvent(
    (Plugin) this,
    "Plugin [" + this.getClass().getSimpleName() + "] finished: " + reason,
    success
));

// Inside the SchedulerPlugin
@Subscribe
public void onPluginScheduleEntryFinishedEvent(PluginScheduleEntryFinishedEvent event) {
    if (currentPlugin != null && event.getPlugin() == currentPlugin.getPlugin()) {
        // Stop the plugin with the success state from the event
        currentPlugin.stop(event.isSuccess(), StopReason.PLUGIN_FINISHED, event.getReason());
    }
}
```

### Self-Reporting Plugin Completion

The `reportFinished()` method is a key component that allows plugins to proactively signal completion. It works as follows:

1. **Validation Checks:**
   - Verifies the SchedulerPlugin is loaded
   - Confirms there's a current plugin running
   - Ensures the current plugin matches this plugin instance

2. **Event Creation and Posting:**
   - Creates a PluginScheduleEntryFinishedEvent with:
     - Reference to the plugin
     - Formatted reason message
     - Success status flag
   - Posts the event to the RuneLite EventBus

3. **Fallback Handling:**
   - If validation checks fail, the plugin stops itself directly
   - This ensures plugins can always stop themselves, even if the scheduler isn't functioning properly

The self-reporting mechanism gives plugins control over their lifecycle while still maintaining the scheduler's orchestration role.

## Technical Notes

### Thread Safety Considerations

The `SchedulablePlugin` interface methods may be called from different threads:

- Events are typically dispatched on the RuneLite event thread
- Condition checks are called from the scheduler's timer thread
- Plugin operations should be performed on the client thread for game state interactions

Proper thread management is essential for stable operation. Use `Microbot.getClientThread().invokeLater()` for game state interactions.

### Serialization Impact

The `PluginScheduleEntry` class handles serialization of plugin schedules, but plugin instances themselves are marked as `transient`. This means:

1. Any plugin-specific state must be managed separately
2. Plugin references are re-established when schedules are loaded
3. Condition objects are serialized and deserialized, so they should be designed with serialization in mind


## Best Practices

When implementing the `SchedulablePlugin` interface, consider the following best practices:

1. **Clear Conditions**: Define explicit start and stop conditions that clearly express your plugin's requirements.

2. **Respect Soft Stops**: Implement `onPluginScheduleEntrySoftStopEvent` to clean up resources properly.

3. **Use Locks Carefully**: Lock your plugin only during critical operations that should not be interrupted.

4. **Self-Report Completion**: Use `reportFinished()` when your plugin naturally completes its task.

5. **Handle Time Appropriately**: Include time-based conditions in your stop conditions to ensure your plugin doesn't run indefinitely.

6. **Update Conditions**: Use `onStopConditionCheck()` to refresh dynamic conditions based on changing game state.

## Component Relationships

The `SchedulablePlugin` interface is part of a larger system that enables automatic scheduling of plugins. Here's how the components interact:

### System Architecture

```ascii
┌───────────────────────────────┐
│         SchedulerPlugin       │
│   (Central Orchestrator)      │
├───────────────────────────────┤
│ - Manages scheduling cycle    │
│ - Handles state transitions   │
│ - Evaluates conditions        │
│ - Starts & stops plugins      │
└─────────────────┬─────────────┘
                  │ manages
                  │ multiple
                  ▼
┌───────────────────────────────┐
│      PluginScheduleEntry      │
│        (Data Model)           │
├───────────────────────────────┤
│ - Stores config & state       │
│ - Tracks execution metrics    │
│ - Contains conditions         │
│ - References plugin instance  │
└─────────────────┬─────────────┘
                  │ references
                  │
                  ▼
┌───────────────────────────────┐         ┌───────────────────────────┐
│   Plugin implementing         │         │                           │
│   SchedulablePlugin           │◄────────┤    Regular RuneLite       │
│   (Plugin API Contract)       │implements│    Plugin                 │
├───────────────────────────────┤         ├───────────────────────────┤
│ - Defines start/stop logic    │         │ - Standard RuneLite       │
│ - Handles events              │         │   plugin functionality    │
│ - Reports completion          │         │                           │
└───────────────────────────────┘         └───────────────────────────┘
```

### Component Responsibilities

#### 1. SchedulerPlugin (Orchestrator)

- **Primary Role:** Central controller that manages the entire scheduling system
- **Responsibilities:**
  - Maintains the scheduler's state machine (16 distinct states)
  - Executes the scheduling algorithm to determine which plugin runs next
  - Processes condition evaluations to start/stop plugins
  - Manages integration with other systems (BreakHandler, AutoLogin)
  - Provides UI for configuration and monitoring

#### 2. PluginScheduleEntry (Data Model)

- **Primary Role:** Container for plugin scheduling configuration and execution state
- **Responsibilities:**
  - Stores start and stop conditions for a specific plugin
  - Tracks execution metrics (run count, duration, last run time)
  - Maintains plugin priority and randomization settings
  - Records state information (enabled/disabled, running/stopped)
  - Handles watchdog functionality to monitor plugin execution

#### 3. SchedulablePlugin (API Contract)

- **Primary Role:** Interface implemented by plugins to participate in scheduling
- **Responsibilities:**
  - Defines plugin-specific start and stop conditions
  - Responds to scheduler events (start request, soft stop, hard stop)
  - Reports task completion back to the scheduler
  - Protects critical sections during execution
  - Provides hooks for condition evaluation

### Data Flow Between Components

1. **Registration Flow:**

   ```text
   Plugin implements SchedulablePlugin → User configures in UI → 
   SchedulerPlugin creates PluginScheduleEntry → Entry stored in scheduler
   ```

2. **Startup Flow:**

   ```text
   SchedulerPlugin evaluates conditions → Matches found → SchedulerPlugin references 
   PluginScheduleEntry → Entry points to Plugin → Plugin starts
   ```

3. **Stopping Flow:**

   ```text
   Stop conditions met → SchedulerPlugin posts event → 
   Plugin's onPluginScheduleEntrySoftStopEvent handler called → 
   Plugin stops gracefully → PluginScheduleEntry updated
   ```

4. **Self-Completion Flow:**

   ```text
   Plugin determines it's finished → Plugin calls reportFinished() →
   SchedulerPlugin processes finish event → Updates PluginScheduleEntry → 
   Selects next plugin
   ```

### Integration Points

1. **Condition System Integration:**

   - `SchedulablePlugin.getStartCondition()` - Plugin defines when it can start
   - `SchedulablePlugin.getStopCondition()` - Plugin defines when it should stop
   - These combine with user-configured conditions in the PluginScheduleEntry

2. **Event System Integration:**

   - `PluginScheduleEntrySoftStopEvent` - Sent from scheduler to plugin requesting stop
   - `PluginScheduleEntryFinishedEvent` - Sent from plugin to scheduler reporting completion

3. **State Protection Integration:**

   - `SchedulablePlugin.lockPlugin()` - Prevents scheduler from stopping during critical operations
   - `SchedulablePlugin.unlockPlugin()` - Releases lock when safe to stop

## Practical Implementation

When implementing the `SchedulablePlugin` interface in your plugin, consider the following workflow:

1. **Define Start and Stop Conditions:**

   ```java
   @Override
   public LogicalCondition getStartCondition() {
       AndCondition startConditions = new AndCondition();
       startConditions.addCondition(new TimeWindowCondition(
           LocalTime.of(9, 0), LocalTime.of(17, 0)
       ));
       startConditions.addCondition(new InventoryItemCountCondition(
           ItemID.COINS, 1000, ComparisonType.MORE_THAN
       ));
       return startConditions;
   }

   @Override
   public LogicalCondition getStopCondition() {
       OrCondition stopConditions = new OrCondition();
       stopConditions.addCondition(new InventoryItemCountCondition(
           ItemID.DRAGON_BONES, 28
       ));
       stopConditions.addCondition(new PlayerHealthCondition(
           10, ComparisonType.LESS_THAN_OR_EQUAL
       ));
       return stopConditions;
   }
   ```

2. **Implement Stop Event Handler:**

   ```java
   @Subscribe
   public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
       // Ensure this event is for our plugin
       if (event.getPlugin() != this) {
           return;
       }
       
       // Save state if needed
       saveCurrentProgress();
       
       // Log the stop reason
       log.info("Plugin stopping due to: " + event.getReason());
       
       // Allow the plugin to be stopped
       Microbot.stopPlugin(this);
   }
   ```

3. **Report Completion When Done:**

   ```java
   private void checkTaskCompletion() {
       if (isTaskComplete()) {
           // Report we're done to the scheduler
           reportFinished("Task completed successfully", true);
       } else if (isTaskFailed()) {
           reportFinished("Task failed: out of resources - inventory setup dont match", false); //USE with CAUTION only if you want your plugin is not started agin by the scheduler plugin can not be started again by the scheduler until it is enable again by the user in the scheduler plan (UI)
       }
   }
   ```

4. **Protect Critical Sections:**

   ```java
   @Override
   public void onGameTick() {
       try {
           // Lock to prevent interruption during critical operation
           lockPlugin();
           
           // Perform sensitive operation that shouldn't be interrupted
           performBankTransaction();
       } finally {
           // Always unlock when done
           unlockPlugin();
       }
   }
   ```

## How the Scheduler Selects the Next Plugin

The Plugin Scheduler uses a sophisticated algorithm to determine which plugin to run next. Understanding this algorithm helps when configuring your plugin's schedule settings.

### Multi-Factor Selection Algorithm

```text
START SELECTION
  Filter out disabled plugins
  Filter out plugins currently running
  Group remaining plugins by priority (highest first)
  
  FOR EACH priority group:
    Split into non-default and default plugins
    
    IF non-default plugins exist:
      Evaluate start conditions for each plugin
      Separate into "can start" and "cannot start" groups
      IF "can start" group is not empty:
        IF randomization enabled:
          Apply weighted random selection based on run count
        ELSE:
          Select first plugin
      ELSE:
        Continue to next sub-group
        
    IF default plugins exist AND no non-default plugin selected:
      Evaluate start conditions for each plugin
      Separate into "can start" and "cannot start" groups
      IF "can start" group is not empty:
        IF randomization enabled:
          Apply weighted random selection based on run count
        ELSE:
          Select first plugin
      ELSE:
        Continue to next priority group
        
  IF no plugin selected:
    Return null (scheduler will enter BREAK state)
  ELSE:
    Return selected plugin
END SELECTION
```

### Selection Factors (in order of importance)

1. **Plugin Priority**: Higher priority plugins (larger number) are always evaluated first
2. **Plugin Type**: Non-default plugins take precedence over default plugins
3. **Start Conditions**: Only plugins whose start conditions are met are considered
4. **Randomization Setting**: Controls whether selection within a group is deterministic or random
5. **Run Count Balance**: Less frequently run plugins get higher weighting during random selection

### Weighting Formula for Random Selection

When randomization is enabled, plugins are selected using a weighted algorithm:

```java
// For each plugin in the eligible group
double weight = BASE_WEIGHT;

// Adjust based on how often this plugin has run compared to others
if (averageRunCount > 0 && plugin.getRunCount() < averageRunCount) {
    // Plugin has run less than average, increase its chance of selection
    double runCountRatio = (double) plugin.getRunCount() / averageRunCount;
    weight += (1.0 - runCountRatio) * RUN_COUNT_WEIGHT;
}

// Plugins with higher priority get a slight boost even within their priority group
weight += (plugin.getPriority() - baseGroupPriority) * PRIORITY_BONUS;

// Add weight to selection map
weightMap.put(plugin, weight);
```

This approach ensures all plugins get fair execution time while still respecting priorities.

## State Transition Flow and Condition Evaluation

The scheduler manages a complex state machine that determines when and how plugins are executed. Here's how your plugin interacts with this state machine:

### Key State Transitions

1. **SCHEDULING → STARTING_PLUGIN**:
   - Triggered when: Your plugin is selected to run next
   - Requirements: All start conditions must be met
   - Actions: Scheduler enables your plugin and starts watching it

2. **STARTING_PLUGIN → RUNNING_PLUGIN**:
   - Triggered when: Your plugin activates successfully  
   - Requirements: Plugin starts without errors
   - Actions: Scheduler begins monitoring stop conditions

3. **RUNNING_PLUGIN → SOFT_STOPPING_PLUGIN**:
   - Triggered when: Any stop condition is met OR your plugin calls reportFinished()
   - Actions: Scheduler sends PluginScheduleEntrySoftStopEvent to your plugin

4. **SOFT_STOPPING_PLUGIN → HARD_STOPPING_PLUGIN**:
   - Triggered when: Your plugin doesn't stop within timeout period
   - Actions: Scheduler forcibly disables the plugin

### Condition Evaluation Cycle

```text
While in RUNNING_PLUGIN state:
  Every ~1 second:
    Call plugin.onStopConditionCheck() to refresh condition state
    Evaluate plugin's stop conditions
    Evaluate user-defined stop conditions
    IF any condition is true:
      Transition to SOFT_STOPPING_PLUGIN
      Send soft stop event to plugin
    ELSE:
      Continue monitoring
```

This dual-layer condition system (plugin-defined + user-defined) provides maximum flexibility while maintaining plugin control over its execution criteria.

## Best Practices for SchedulablePlugin Implementation

To make the most of the Scheduler system, follow these guidelines when implementing `SchedulablePlugin`:

1. **Use Specific Conditions**: Define clear, specific conditions that accurately represent when your plugin should start and stop.

   ```java
   // Good: Specific condition
   new InventoryItemCountCondition(ItemID.DRAGON_BONES, 28)
   
   // Avoid: Overly general condition
   new TimeElapsedCondition(Duration.ofMinutes(30))
   ```

2. **Implement Graceful Stopping**: Always handle the `onPluginScheduleEntrySoftStopEvent` properly to ensure your plugin can be stopped cleanly.

   ```java
   @Subscribe
   public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
       if (event.getPlugin() != this) {
           return;
       }
       
       // Save progress
       savePluginState();
       
       // Release resources
       cleanupResources();
       
       // Stop the plugin
       Microbot.stopPlugin(this);
   }
   ```

3. **Use Critical Section Protection**: Lock the plugin during critical operations to prevent interruption.

   ```java
   // Banking is a critical operation that shouldn't be interrupted
   try {
       lockPlugin();
       performBankingOperation();
   } finally {
       unlockPlugin();
   }
   ```

4. **Self-Report Completion**: Use `reportFinished()` when your plugin completes its task or encounters a situation where it should stop.

   ```java
   if (isInventoryFull() && task.isComplete()) {
       reportFinished("Task completed successfully", true);
       return;
   }
   
   if (isOutOfSupplies()) {
       reportFinished("Out of required supplies", false);
       return;
   }
   ```

5. **Update Condition State**: Implement `onStopConditionCheck()` to refresh any dynamic condition state.

   ```java
   @Override
   public void onStopConditionCheck() {
       // Update our cached values that conditions might use
       this.currentPlayerHealth = getPlayerHealth();
       this.nearbyEnemyCount = countNearbyEnemies();
   }
   ```

6. **Design for Recovery**: Make your plugin resilient to interruptions by saving state periodically.

7. **Balance Priorities**: Set appropriate priorities for your plugin to ensure it runs when most appropriate.

   - High priority (50+): Critical scripts that should run first
   - Medium priority (20-49): Standard task scripts
   - Low priority (1-19): Background or maintenance scripts
   - Default priority (0): Last to run

8. **Consider Randomization**: Enable randomization for most plugins to create more natural bot behavior patterns.

## Conclusion

The `SchedulablePlugin` interface serves as the foundation of Microbot's advanced plugin scheduling system, enabling sophisticated automation workflows with natural-looking behavior patterns. By implementing this interface, your plugins become part of an intelligent ecosystem that can coordinate multiple activities, respect constraints, and adapt to changing conditions.

Key benefits of using the scheduler system include:

- Reduced detection risk through coordinated breaks and natural activity patterns
- Lower development burden by leveraging shared infrastructure for timing and conditions
- More sophisticated automation flows by chaining plugins together
- Better user experience through consistent configuration and monitoring

For comprehensive examples of `SchedulablePlugin` implementations, refer to the following reference plugins:

- `WoodcuttingPlugin`: Demonstrates basic resource gathering with simple conditions
- `FletchingPlugin`: Shows workflow stages with progress reporting
- `CombatPlugin`: Illustrates complex condition structures and critical section protection

These reference implementations provide patterns you can adapt for your own plugins to integrate seamlessly with the scheduler system.

