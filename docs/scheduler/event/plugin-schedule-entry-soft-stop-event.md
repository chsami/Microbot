# Plugin Schedule Entry Soft Stop Event

## Overview

The `PluginScheduleEntrySoftStopEvent` is a critical component of the Plugin Scheduler system that enables graceful termination of plugins. This event is sent by the scheduler to notify a plugin that it should begin its shutdown process due to stop conditions being met or other scheduling requirements.

## Class Structure

```java
@Getter
public class PluginScheduleEntrySoftStopEvent {
    private final Plugin plugin;
    private final ZonedDateTime stopDateTime;
    
    public PluginScheduleEntrySoftStopEvent(Plugin plugin, ZonedDateTime stopDateTime) {
        this.plugin = plugin;
        this.stopDateTime = stopDateTime;
    }
}
```

## Key Features

### Plugin Identification

The event carries a reference to the specific `Plugin` instance that should stop:

```java
private final Plugin plugin;
```

This allows the event to specifically target the plugin that needs to be stopped, enabling precise communication even in a multi-plugin environment.

### Timestamp Tracking

The event includes a timestamp indicating when the stop request was issued:

```java
private final ZonedDateTime stopDateTime;
```

This timestamp serves several purposes:
- Records when the stop decision was made for logging and analytics
- Enables time-based escalation if the plugin doesn't respond promptly
- Provides context to the plugin about the timing of the stop request

## Technical Details

### Event Propagation

This event is sent through the RuneLite EventBus system:

```java
// Inside the scheduler when a plugin should be stopped
Microbot.getEventBus().post(new PluginScheduleEntrySoftStopEvent(
    plugin,               // The plugin to stop
    ZonedDateTime.now()   // Current time
));
```

### Soft Stop Concept

The term "soft stop" in the event name is significant:

1. It indicates that this is a request for the plugin to stop gracefully, not an immediate termination command
2. It gives the plugin an opportunity to:
   - Complete critical operations in progress
   - Save any necessary state
   - Clean up resources
   - Reach a safe termination point

### Immutability

All fields in the event are marked as `final`, ensuring the event is immutable once created. This prevents potential issues with event data being modified during propagation.

## Usage Example

### In the Scheduler

The scheduler creates and posts this event when a plugin's stop conditions are met:

```java
private void softStopPlugin(PluginScheduleEntry entry) {
    Plugin plugin = entry.getPlugin();
    if (plugin != null) {
        log.debug("Sending soft stop request to plugin {}", entry.getName());
        
        // Post the event to the EventBus
        Microbot.getEventBus().post(new PluginScheduleEntrySoftStopEvent(
            plugin,
            ZonedDateTime.now()
        ));
        
        // Set state to indicate stopping is in progress
        entry.setStopReason(StopReason.CONDITIONS_MET);
        currentState = SchedulerState.STOPPING;
        
        // Schedule hard stop fallback if plugin doesn't respond in time
        if (entry.isHardStoppable()) {
            scheduleHardStopFallback(entry, Duration.ofSeconds(30));
        }
    }
}
```

### In a Plugin Implementing ConditionProvider

Plugins that implement the `ConditionProvider` interface can handle the event:

```java
@Subscribe
@Override
public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
    // Only respond if this event is targeted at this plugin
    if (event.getPlugin() == this) {
        log.info("Received soft stop request, cleaning up and stopping...");
        
        // Perform necessary cleanup
        saveCurrentProgress();
        closeOpenInterfaces();
        
        // Actually stop the plugin
        Microbot.stopPlugin(this);
    }
}
```

## Relationship to Other Components

The `PluginScheduleEntrySoftStopEvent` is part of a coordinated stop sequence:

1. **Trigger**: Stop conditions are met, manual stop requested, or scheduler is shutting down
2. **Soft Stop**: The scheduler sends `PluginScheduleEntrySoftStopEvent` to request graceful termination
3. **Plugin Response**: The plugin performs cleanup and either:
   - Stops itself directly using `Microbot.stopPlugin(this)`
   - Posts a `PluginScheduleEntryFinishedEvent` to report completion
4. **Hard Stop Fallback**: If the plugin doesn't respond within a timeout period and is marked as hard-stoppable, the scheduler may forcibly terminate it

## Best Practices

### For Plugin Developers

1. **Implement the Event Handler**: Ensure your plugin properly handles the `PluginScheduleEntrySoftStopEvent` if it implements `ConditionProvider`
2. **Respond Promptly**: Complete cleanup operations quickly to avoid being hard-stopped
3. **Check Target Plugin**: Always verify that the event is targeting your plugin before processing it
4. **Report Completion**: Consider posting a `PluginScheduleEntryFinishedEvent` to provide more context about the stop reason

### For Scheduler Implementation

1. **Timeout Management**: Set appropriate timeouts for plugins to respond to soft stop requests
2. **Escalation Path**: Define clear escalation when plugins don't respond to soft stops
3. **Stop Context**: Provide useful context in the event for why the plugin is being stopped
4. **Validation**: Verify that the plugin is actually running before sending a stop event