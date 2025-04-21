# Plugin Scheduler System

## Overview

The Plugin Scheduler is a sophisticated system that allows for the automatic scheduling and management of plugins based on various conditions. It provides a flexible framework for defining when plugins should start and stop, using a powerful condition-based approach.

## Key Components

The Plugin Scheduler system consists of several key components:

1. **[SchedulerPlugin](scheduler-plugin.md)**: The main plugin that manages the scheduling of other plugins.
2. **[PluginScheduleEntry](plugin-schedule-entry.md)**: Represents a scheduled plugin with start and stop conditions.
3. **[ConditionManager](conditions/README.md)**: Manages logical conditions for plugin scheduling in a hierarchical structure.
4. **[Condition](conditions/README.md)**: The base interface for all conditions that determine when plugins should run.
5. **[SchedulablePlugin](schedulable-plugin.md)**: Interface that plugins must implement to be schedulable by the Scheduler.

## Making Your Plugin Schedulable

To make your plugin schedulable by the Plugin Scheduler, follow these steps:



1. **Implement the `SchedulablePlugin` interface**:
   ```java
   public class MyPlugin extends Plugin implements SchedulablePlugin {
       // Plugin implementation...
   }
   ```

2. **Define stop conditions**:
   ```java
   @Override
   public LogicalCondition getStopCondition() {
       // Create conditions that determine when your plugin should stop
       OrCondition orCondition = new OrCondition();
       
       // Add time-based condition to stop after 30 minutes
       orCondition.addCondition(IntervalCondition.createRandomized(
           Duration.ofMinutes(25), 
           Duration.ofMinutes(30)
       ));
       
       // Add inventory-based condition to stop when inventory is full
       // Add other conditions as needed
       
       return orCondition;
   }
   ```

3. **Define optional start conditions**:
   ```java
   @Override
   public LogicalCondition getStartCondition() {
       // Create conditions that determine when your plugin can start
       // Return null if the plugin can start anytime
   }
   ```

4. **Implement the soft stop event handler**:
   ```java
   @Override
   @Subscribe
   public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
       if (event.getPlugin() == this) {
           // Save state if needed
           // Clean up resources
       }
   }
   ```

For complete details about implementing the SchedulablePlugin interface, see the [SchedulablePlugin documentation](schedulable-plugin.md).

## Condition System

The heart of the Plugin Scheduler is its condition system, which allows for complex logic to determine when plugins should start and stop. Conditions can be combined using logical operators (AND/OR) to create sophisticated scheduling rules.

### Condition Types

The system supports various types of conditions, each serving a specific purpose:

1. **[Time Conditions](conditions/time-conditions.md)**: Schedule plugins based on time-related factors such as intervals, specific times, or day of week.
2. **[Skill Conditions](conditions/skill-conditions.md)**: Trigger plugins based on skill levels or experience.
3. **[Resource Conditions](conditions/resource-conditions.md)**: Manage plugins based on inventory items, gathered resources, or loot.
4. **[Location Conditions](conditions/location-conditions.md)**: Control plugins based on player position or area.
5. **[NPC Conditions](conditions/npc-conditions.md)**: Trigger plugins based on NPC-related events.
6. **[Logical Conditions](conditions/logical-conditions.md)**: Combine other conditions using logical operators (AND, OR, NOT).

### Lock Condition

The `LockCondition` is a special condition that can prevent a plugin from being stopped during critical operations:

```java
// In your plugin's getStopCondition method:
LockCondition lockCondition = new LockCondition("Critical banking operation in progress");
AndCondition andCondition = new AndCondition();
andCondition.addCondition(orCondition);   // Other stop conditions
andCondition.addCondition(lockCondition); // Add the lock condition
return andCondition;
```

You can then control the lock in your plugin code:

```java
// Lock to prevent stopping during critical operations
lockCondition.lock();

// Critical operation here...

// Unlock when safe to stop
lockCondition.unlock();
```

The lock condition ensures that your plugin won't be interrupted during critical operations, such as banking, trading, or complex interactions that should not be interrupted.

## Start and Stop Conditions

Each scheduled plugin can have both start and stop conditions:

- **Start Conditions**: Determine when a plugin should be activated.
- **Stop Conditions**: Determine when a plugin should be deactivated.

These conditions operate independently, allowing for flexible plugin lifecycle management. The PluginScheduleEntry class manages these conditions through separate ConditionManager instances.

## Plugin Scheduling Events

The scheduler uses events to communicate with plugins about their lifecycle:

- **[Plugin Schedule Entry Soft Stop Event](plugin-schedule-entry-soft-stop-event.md)**: Sent by the scheduler to request plugins to stop gracefully
- **[Plugin Schedule Entry Finished Event](plugin-schedule-entry-finished-event.md)**: Sent by plugins to notify the scheduler they've completed their task

## Usage Examples

### Basic Scheduling

```java
// Schedule a plugin to run every 30 minutes
PluginScheduleEntry entry = new PluginScheduleEntry(
    "MyPlugin",
    Duration.ofMinutes(30),
    true,  // enabled
    true   // allow random scheduling
);
```

### Advanced Condition-Based Scheduling

```java
// Create a schedule entry
PluginScheduleEntry entry = new PluginScheduleEntry("MyPlugin", true);

// Add a time window condition (run between 9 AM and 5 PM)
entry.addStartCondition(new TimeWindowCondition(
    LocalTime.of(9, 0),
    LocalTime.of(17, 0)
));

// Add a stop condition (stop when inventory is full)
entry.addStopCondition(new InventoryItemCountCondition(
    ItemID.ANY,
    28,
    
));

// Register the scheduled entry
schedulerPlugin.registerScheduledPlugin(entry);
```

## Example Implementation

For a complete example of a schedulable plugin, see the [SchedulableExamplePlugin](schedulable-example-plugin.md), which demonstrates all aspects of making a plugin work with the scheduler system.

## Further Documentation

For more detailed information about each component, refer to the specific documentation files:

- [SchedulerPlugin](scheduler-plugin.md)
- [PluginScheduleEntry](plugin-schedule-entry.md)
- [SchedulablePlugin](api/schedulable-plugin.md)
- [Plugin Schedule Entry Soft Stop Event](plugin-schedule-entry-soft-stop-event.md)
- [Plugin Schedule Entry Finished Event](plugin-schedule-entry-finished-event.md)
- [SchedulableExamplePlugin](schedulable-example-plugin.md)

For condition-specific documentation:

- [Time Conditions](conditions/time-conditions.md)
- [Skill Conditions](conditions/skill-conditions.md)
- [Resource Conditions](conditions/resource-conditions.md)
- [Location Conditions](conditions/location-conditions.md)
- [NPC Conditions](conditions/npc-conditions.md)
- [Logical Conditions](conditions/logical-conditions.md)