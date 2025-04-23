# Plugin Schedule Entry Finished Event

## Overview

The `PluginScheduleEntryFinishedEvent` is a crucial component of the Plugin Scheduler system's inter-component communication mechanism. This event is fired when a plugin self-reports that it has completed its assigned task and is ready to be stopped by the scheduler.

## Class Structure

```java
@Getter
public class PluginScheduleEntryFinishedEvent {
    private final Plugin plugin;
    private final ZonedDateTime finishDateTime;
    private final String reason;
    private final boolean success;
    
    // Constructor implementations
}
```

## Key Features

### Plugin Identification

The event carries a reference to the specific `Plugin` instance that has completed:

```java
private final Plugin plugin;
```

This allows the scheduler to correctly identify which scheduled plugin has finished, even when multiple plugins might be active or scheduled.

### Timestamp Tracking

The event includes a precise timestamp of when the plugin completed its work:

```java
private final ZonedDateTime finishDateTime;
```

This timestamp is used by the scheduler for logging, debugging, and calculating statistics about plugin execution times.

### Success Reporting

The event includes a boolean flag indicating whether the plugin completed successfully:

```java
private final boolean success;
```

This allows the scheduler to distinguish between different types of completion:
- `true`: The plugin completed its task as expected and terminated normally
- `false`: The plugin encountered an issue that prevented it from completing its task but was still able to gracefully report its status

### Reason Documentation

The event provides a human-readable explanation of why the plugin finished:

```java
private final String reason;
```

This reason string can be used for:
- User interface display
- Logging and diagnostics
- Decision-making about future scheduling attempts

## Technical Details

### Event Propagation

This event is sent through the RuneLite EventBus system:

```java
// Inside a plugin that wants to report completion
Microbot.getEventBus().post(new PluginScheduleEntryFinishedEvent(
    this,                   // The plugin itself
    "Inventory full",       // Reason for finishing
    true                    // Was successful
));
```

### Constructors

The class provides two constructor options:

```java
// Constructor with explicit timestamp
public PluginScheduleEntryFinishedEvent(
    Plugin plugin,
    ZonedDateTime finishDateTime,
    String reason,
    boolean success
)

// Constructor using current time as the finish time
public PluginScheduleEntryFinishedEvent(
    Plugin plugin,
    String reason,
    boolean success
)
```

The second constructor is a convenience method that automatically captures the current time.

### Immutability

All fields in the event are marked as `final`, ensuring the event is immutable once created. This prevents potential issues with event data being modified during propagation.

## Usage Example

### Inside a Plugin

A plugin can report its completion using code like this:

```java
// When the plugin has completed its task successfully
Microbot.getEventBus().post(new PluginScheduleEntryFinishedEvent(
    this,
    "Target level reached",
    true
));

// Or when the plugin needs to stop due to an issue
Microbot.getEventBus().post(new PluginScheduleEntryFinishedEvent(
    this,
    "Unable to find target NPC",
    false
));
```

### Using the ConditionProvider Interface

For plugins that implement the `ConditionProvider` interface, a convenience method is provided:

```java
// Inside a plugin that implements ConditionProvider
@Override
public void onSkillLevelReached(int level) {
    if (level >= targetLevel) {
        reportFinished("Target level " + level + " reached", true);
    }
}
```

The `reportFinished` method internally creates and posts the `PluginScheduleEntryFinishedEvent`.

### In the Scheduler

The scheduler listens for these events and processes them:

```java
@Subscribe
public void onPluginScheduleEntryFinishedEvent(PluginScheduleEntryFinishedEvent event) {
    if (currentPlugin != null && currentPlugin.getPlugin() == event.getPlugin()) {
        log.info("Plugin {} reported finished: {} (success={})",
                currentPlugin.getName(),
                event.getReason(),
                event.isSuccess());
        
        // Stop the plugin and record the finish reason
        currentPlugin.setStopReason(
            event.isSuccess() ? StopReason.PLUGIN_FINISHED : StopReason.ERROR);
        forceStopCurrentPluginScheduleEntry();
    }
}
```

## Relationship to Other Components

The `PluginScheduleEntryFinishedEvent` works alongside other events in the scheduler system:

- It differs from `PluginScheduleEntrySoftStopEvent`, which is sent by the scheduler to request that a plugin stop
- It is typically used after the plugin has responded to a soft stop request or has independently determined it should stop
- It provides feedback to the scheduler about the plugin's state at the end of execution