# SchedulableExamplePlugin

## Overview

The `SchedulableExamplePlugin` is a reference implementation demonstrating how to create plugins that work with the Plugin Scheduler system. It showcases various types of conditions for both starting and stopping a plugin, as well as proper implementation of the `SchedulablePlugin` interface.

## Key Features

1. **Comprehensive Condition Examples**: Demonstrates all major condition types:
   - Time-based conditions
   - Resource gathering conditions
   - Item looting conditions 
   - NPC kill count conditions
   - Process item conditions (crafting/smithing)
   - Location-based conditions

2. **Manual Testing Capabilities**: Includes hotkeys to manually trigger events for testing:
   - Finish plugin event trigger
   - Lock condition toggling
   - Custom area definition for location-based conditions

3. **Start Condition Examples**: Shows how to restrict plugin activation to specific locations:
   - Bank-based start conditions
   - Custom area start conditions

## Making Your Plugin Schedulable

### Step 1: Plugin Declaration

```java
@PluginDescriptor(
    name = "Schedulable Example",
    description = "Designed for use with the scheduler and testing its features",
    tags = {"microbot", "woodcutting", "combat", "scheduler", "condition"},
    enabledByDefault = false    
)
@Slf4j
public class SchedulableExamplePlugin extends Plugin implements SchedulablePlugin {
    // Plugin implementation...
}
```

A plugin becomes schedulable by implementing the `SchedulablePlugin` interface.

### Step 2: Implement SchedulablePlugin

The `SchedulablePlugin` interface requires implementation of key methods:

```java
public interface SchedulablePlugin {
    LogicalCondition getStartCondition();
    LogicalCondition getStopCondition();
    void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event);
    
    // Optional methods with default implementations
    void onStopConditionCheck();
    void reportFinished(String reason, boolean success);
}
```

### Step 3: Define Stop Conditions

```java
@Override
public LogicalCondition getStopCondition() {
    // Create an OR condition - stop when ANY of the enabled conditions are met
    OrCondition orCondition = new OrCondition();
    
    // Create a lock condition for manual prevention of stopping
    this.lockCondition = new LockCondition("Locked because the Plugin is in a critical operation");
    
    // Add enabled conditions based on configuration
    if (config.enableTimeCondition()) {
        orCondition.addCondition(createTimeCondition());
    }
    
    if (config.enableLootItemCondition()) {
        orCondition.addCondition(createLootItemCondition());
    }
    
    // Add more conditions...
    
    // Combine with lock condition using AND logic
    AndCondition andCondition = new AndCondition();
    andCondition.addCondition(orCondition);
    andCondition.addCondition(lockCondition);
    return andCondition;
}
```

### Step 4: Define Start Conditions (Optional)

```java
@Override
public LogicalCondition getStartCondition() {
    // Only create start conditions if enabled
    if (!config.enableLocationStartCondition()) {
        return null;  // null means plugin can start anytime
    }
    
    // Create a logical condition for start conditions
    LogicalCondition startCondition = new OrCondition();
    
    // Add conditions based on configuration
    if (config.locationStartType() == LocationStartType.BANK) {
        // Bank-based start condition
        BankLocation selectedBank = config.bankStartLocation();
        int distance = config.bankDistance();
        
        // Create condition using bank location
        Condition bankCondition = LocationCondition.atBank(selectedBank, distance);
        ((OrCondition) startCondition).addCondition(bankCondition);
    } 
    else if (config.locationStartType() == LocationStartType.CUSTOM_AREA) {
        // Custom area start condition logic
        // ...
    }
    
    return startCondition;
}
```

### Step 5: Implement Soft Stop Handler

```java
@Override
@Subscribe
public void onPluginScheduleEntrySoftStopEvent(PluginScheduleEntrySoftStopEvent event) {
    // Save state before stopping
    if (event.getPlugin() == this) {
        WorldPoint currentLocation = null;
        if (Microbot.isLoggedIn()) {
            currentLocation = Rs2Player.getWorldLocation();
        }
        Microbot.getConfigManager().setConfiguration("SchedulableExample", "lastLocation", currentLocation);
        log.info("Scheduling stop for plugin: {}", event.getPlugin().getClass().getSimpleName());
        
        // Schedule the stop operation on the client thread
        Microbot.getClientThread().invokeLater(() -> {
            try {                    
                Microbot.getPluginManager().setPluginEnabled(this, false);
                Microbot.getPluginManager().stopPlugin(this);
            } catch (Exception e) {
                log.error("Error stopping plugin", e);
            }
        });
    }
}
```

The `onPluginScheduleEntrySoftStopEvent` method is triggered when the Plugin Scheduler determines that a plugin's stop conditions have been met and requests the plugin to gracefully shut down. This implementation follows best practices for safely stopping a plugin:

1. **State Preservation**: First saves the current player location to configuration for later use.
2. **Thread Safety**: Uses `Microbot.getClientThread().invokeLater()` to ensure the plugin is stopped on the client thread, avoiding concurrency issues.
3. **Clean Shutdown**: Disables the plugin and then properly stops it using `Microbot.getPluginManager().stopPlugin(this)`.

#### Alternative Stopping Methods

You can also directly stop a plugin using:

```java
// Direct method to stop a plugin
Microbot.stopPlugin(this);
```

This is useful in situations where you need an immediate shutdown response, but be careful to ensure you've performed any necessary cleanup operations first.

### Understanding the Plugin Shutdown Process

The scheduler-managed shutdown process follows this sequence:

1. **Trigger**: Stop conditions are met or manual stop requested
2. **Soft Stop Request**: The scheduler sends `PluginScheduleEntrySoftStopEvent` to the plugin
3. **Plugin Cleanup**: The plugin performs necessary cleanup operations 
4. **Graceful Termination**: The plugin stops itself using one of the following methods:
   - `Microbot.getPluginManager().stopPlugin(this)`
   - `Microbot.stopPlugin(this)`
5. **Completion Reporting**: Optionally, the plugin can report detailed completion status using:
   ```java
   reportFinished("Task completed successfully", true);
   ```

This approach ensures that plugins can safely save their state and perform cleanup operations before being terminated, preserving data integrity and preventing issues that could arise from abrupt termination.

## Understanding SchedulableExampleConfig

The `SchedulableExampleConfig` interface provides a comprehensive configuration system for the example plugin, demonstrating how to create configurable conditions:

### Config Sections

The config is organized into logical sections:

```java
@ConfigSection(
    name = "Start Conditions",
    description = "Conditions for when the plugin is allowed to start",
    position = 0
)
String startConditionSection = "startConditions";

@ConfigSection(
    name = "Stop Conditions",
    description = "Conditions for when the plugin should stop",
    position = 101
)
String stopSection = "stopConditions";

// More specific condition sections...
```

### Condition Configuration Groups

Each condition type has its own configuration group:

1. **Location Start Conditions**: Control where the plugin can start
   - Bank location selection
   - Custom area definition with hotkey
   - Area radius configuration

2. **Time Conditions**: Control duration-based stopping
   - Min/max runtime settings
   - Randomized intervals

3. **Loot Item Conditions**: Stop based on collected items
   - Item name patterns (supports regex)
   - Min/max item count targets
   - Logical operators (AND/OR)
   - Note item inclusion settings

4. **Resource Conditions**: Stop based on gathered resources
   - Resource type patterns
   - Min/max count settings
   - Logical operators

5. **Process Item Conditions**: Stop based on items processed
   - Source/target item tracking
   - Tracking mode selection
   - Min/max processed items

6. **NPC Conditions**: Stop based on NPC kill counts
   - NPC name patterns
   - Min/max kill targets
   - Per-NPC or total counting options

7. **Debug Options**: Test functionality without running the full plugin
   - Manual finish hotkey
   - Success reporting
   - Lock condition toggle

## Testing Conditions

The `SchedulableExamplePlugin` includes features specifically designed for testing the condition system:

### Manual Condition Triggers

```java
// HotkeyListener for testing PluginScheduleEntryFinishedEvent
private final HotkeyListener finishPluginHotkeyListener = new HotkeyListener(() -> config.finishPluginHotkey()) {
    @Override
    public void hotkeyPressed() {
        String reason = config.finishReason();
        boolean success = config.reportSuccessful();
        log.info("Manually triggering plugin finish: reason='{}', success={}", reason, success);
        reportFinished(reason, success);
    }
};

// HotkeyListener for toggling the lock condition
private final HotkeyListener lockConditionHotkeyListener = new HotkeyListener(() -> config.lockConditionHotkey()) {
    @Override
    public void hotkeyPressed() {
        boolean newState = toggleLock(currentCondition);
        log.info("Lock condition toggled: {}", newState ? "LOCKED - " + config.lockDescription() : "UNLOCKED");
    }
};
```

### Location-Based Start Conditions

```java
// HotkeyListener for the area marking
private final HotkeyListener areaHotkeyListener = new HotkeyListener(() -> config.areaMarkHotkey()) {
    @Override
    public void hotkeyPressed() {
        toggleCustomArea();
    }
};

private void toggleCustomArea() {
    // Logic to mark the current player position as center of a custom area
    // or to clear a previously defined area
}
```

## Understanding the LockCondition

The `LockCondition` is a special condition used to prevent a plugin from being stopped during critical operations, even if other stop conditions are met.

### How It Works

```java
public class LockCondition implements Condition {
    private final AtomicBoolean locked = new AtomicBoolean(false);
    private final String reason;
    
    // Constructor and methods...
    
    @Override
    public boolean isSatisfied() {
        // If locked, the condition is NOT satisfied, which prevents stopping
        return !isLocked();
    }
}
```

The lock condition works by returning `false` for `isSatisfied()` when locked, which prevents the stop condition from being met when used with AND logic.

### Usage in the Example Plugin

```java
// Create the lock condition
this.lockCondition = new LockCondition("Locked because the Plugin is in a critical operation");

// Add it to the condition structure with AND logic
AndCondition andCondition = new AndCondition();
andCondition.addCondition(orCondition);   // Other stop conditions
andCondition.addCondition(lockCondition); // Lock condition
```

### When to Use Lock Condition

The lock condition should be used during critical operations where stopping the plugin might leave game state inconsistent:

- **Banking operations**: Prevent stopping mid-transaction
- **Trading**: Ensure trades complete fully
- **Complex combat sequences**: Avoid stopping during multi-step attacks
- **Item processing**: Complete crafting/smithing operations fully

### Testing the Lock Condition

The example plugin provides a hotkey to test locking and unlocking:

```java
private final HotkeyListener lockConditionHotkeyListener = new HotkeyListener(() -> config.lockConditionHotkey()) {
    @Override
    public void hotkeyPressed() {
        boolean newState = toggleLock(currentCondition);
        log.info("Lock condition toggled: {}", newState ? "LOCKED - " + config.lockDescription() : "UNLOCKED");
    }
};
```

## Summary

The `SchedulableExamplePlugin` serves as a comprehensive demonstration of how to create plugins that work with the Plugin Scheduler system. The conditions it implements are designed for testing purposes but illustrate the patterns needed for real-world scheduling scenarios:

- Time-based scheduling using intervals
- Resource gathering targets
- Item processing goals
- NPC kill counts
- Location-based activation
- Manual intervention capabilities

By studying this example plugin, both plugin developers and script writers can understand how to make their own plugins schedulable, creating more sophisticated automation workflows that operate according to complex rules and conditions.