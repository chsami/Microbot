# SchedulableExamplePlugin Documentation

## Overview
The `SchedulableExamplePlugin` demonstrates how to create a plugin compatible with Microbot's scheduler system. It implements the `ConditionProvider` interface to define configurable conditions for when the plugin should automatically start and stop based on various in-game criteria. This plugin serves as a comprehensive example for developers wanting to create scripts that can be managed by the scheduler framework, providing a template for implementing different types of conditions and state management approaches.

The plugin provides a practical implementation of conditional logic that can be used to automate tasks in a controlled manner. By integrating with the scheduler, it enables users to create complex automation workflows where multiple plugins can work together in a sequenced manner, each starting and stopping based on specific game conditions.

## Key Features
- **Seamless integration** with the Microbot scheduler framework
- **Highly configurable** start and stop conditions
- **Location-based start conditions**:
  - Bank locations with configurable distance
  - Custom areas with adjustable radius
- **Multiple stop condition types**:
  - **Time-based**: Run for specified duration
  - **Resource collection**: Stop after gathering specific items
  - **Loot collection**: Stop after collecting specific drops
  - **Item processing**: Stop after crafting/converting items
  - **NPC kill counting**: Stop after killing a specific number of NPCs

## Architecture
The plugin consists of four primary components that work together to provide a complete implementation of a scheduler-compatible plugin:

1. **SchedulableExamplePlugin** (`SchedulableExamplePlugin.java`)
   - Main plugin class implementing `ConditionProvider` and `KeyListener`
   - Manages the plugin lifecycle and condition creation
   - Handles hotkey inputs for custom area marking
   - Serves as the central orchestrator that connects configuration, script execution, and condition evaluation
   - Implements the scheduler integration points through the `ConditionProvider` methods
   - Maintains state between sessions by saving and loading world locations

2. **SchedulableExampleConfig** (`SchedulableExampleConfig.java`)
   - Configuration interface with `@ConfigGroup` and `@ConfigItem` annotations
   - Defines all configurable parameters for the plugin
   - Organizes settings into logical sections using `@ConfigSection` annotations
   - Provides default values for configuration options
   - Includes setter methods for mutable state (like custom area coordinates)
   - Uses enums to define valid option sets (like `LocationStartType` and `ProcessTrackingMode`)
   - Implements hidden configuration items for internal state persistence
   - Creates a hierarchical organization of settings with sections that can be collapsed by default

3. **LocationStartNotificationOverlay** (`LocationStartNotificationOverlay.java`)
   - Visual overlay displaying location-based start condition information
   - Provides real-time feedback on condition status
   - Uses RuneLite's overlay system to render information on the game screen
   - Dynamically updates to show relevant details based on the current configuration
   - Shows information about bank locations or custom areas depending on the active configuration
   - Implements color-coded status indicators (green for met conditions, red for unmet)
   - Displays distance measurements to target locations when relevant
   - Updates in real-time as the player moves around the game world
   - Provides helpful guidance messages for setting up custom areas

## Condition Provider Implementation
The `ConditionProvider` interface is the key integration point between the plugin and the scheduler system. By implementing this interface, the plugin can define under what circumstances it should be automatically started or stopped by the scheduler. This provides a powerful abstraction that allows the scheduler to manage multiple plugins without needing to understand their specific functionality.

### Stop Conditions
The `getStopCondition()` method returns a logical condition determining when the plugin should automatically stop. This method is called by the scheduler to evaluate whether the plugin should be terminated. The implementation combines multiple condition types into a single logical expression that can be evaluated to determine if stopping criteria have been met:

```java
@Override
public LogicalCondition getStopCondition() {
    // Create an OR condition - we'll stop when ANY of the enabled conditions are met
    OrCondition orCondition = new OrCondition();
    
    // Add enabled conditions based on configuration
    if (config.enableTimeCondition()) {
        orCondition.addCondition(createTimeCondition());
    }
    
    if (config.enableLootItemCondition()) {
        orCondition.addCondition(createLootItemCondition());
    }
    
    // Add more conditions...
    
    // If no conditions were added, add a fallback time condition
    if (orCondition.getConditions().isEmpty()) {
        orCondition.addCondition(IntervalCondition.createRandomized(Duration.ofMinutes(5), Duration.ofMinutes(5)));
    }
    
    return orCondition;
}
```

### Start Conditions
The `getStartCondition()` method returns a logical condition determining when the plugin is allowed to start. The scheduler uses this to determine if the plugin should be automatically started when it's scheduled to run. Unlike stop conditions which should always return a value, start conditions can return `null` to indicate that the plugin can start without any preconditions:

```java
@Override
public LogicalCondition getStartCondition() {
    // Default to no start conditions (always allowed to start)
    if (!config.enableLocationStartCondition()) {
        return null;
    }
    
    // Create a logical condition for start conditions
    LogicalCondition startCondition = null;
    
    // Create location-based condition based on selected type
    if (config.locationStartType() == SchedulableExampleConfig.LocationStartType.BANK) {
        // Bank-based start condition
        // ...
    } else if (config.locationStartType() == SchedulableExampleConfig.LocationStartType.CUSTOM_AREA) {
        // Custom area start condition
        // ...
    }
    
    return startCondition;
}
```

## Detailed Condition Types
The plugin implements several types of conditions that can be used to control when it should start or stop. Each condition type is implemented as a separate method that creates and configures a condition object based on the current plugin configuration.

### Time Condition
The time condition is the simplest form of stop condition, which will trigger after a specified duration has elapsed. This is useful for limiting the runtime of a plugin to prevent excessive resource usage or to simulate human-like play patterns with regular breaks:
```java
private Condition createTimeCondition() {
    int minMinutes = config.minRuntime();
    int maxMinutes = config.maxRuntime();
            
    return IntervalCondition.createRandomized(
        Duration.ofMinutes(minMinutes),
        Duration.ofMinutes(maxMinutes)
    );
}
```

### Loot Item Condition
The loot item condition is used to stop the plugin after collecting a specific number of items. This is particularly useful for gathering activities where you want to collect a certain amount of a resource before stopping. The condition supports both AND and OR logical operations, allowing for complex item collection goals:

```java
private LogicalCondition createLootItemCondition() {
    // Parse the comma-separated list of items
    List<String> lootItemsList = parseItemList(config.lootItems());
    
    boolean andLogical = config.itemsToLootLogical();
    int minLootItems = config.minItems();
    int maxLootItems = config.maxItems();
    
    // Create randomized targets for each item
    List<Integer> minLootItemPerPattern = new ArrayList<>();
    List<Integer> maxLootItemPerPattern = new ArrayList<>();
    
    // Generate target counts...
    
    // Create the appropriate logical condition based on config
    if (andLogical) {
        return LootItemCondition.createAndCondition(
            lootItemsList,
            minLootItemPerPattern,
            maxLootItemPerPattern,
            includeNoted,
            allowNoneOwner
        );
    } else {
        return LootItemCondition.createOrCondition(
            lootItemsList,
            minLootItemPerPattern,
            maxLootItemPerPattern,
            includeNoted,
            allowNoneOwner
        );
    }
}
```

### Gathered Resource Condition
The gathered resource condition is similar to the loot item condition but is specifically designed for tracking resources gathered through skilling activities (mining, fishing, woodcutting, etc.). This allows for more precise tracking of gathering activities and can differentiate between items obtained through different methods:

```java
private LogicalCondition createGatheredResourceCondition() {
    // Parse the comma-separated list of resources
    List<String> resourcesList = parseItemList(config.gatheredResources());
    
    boolean andLogical = config.resourcesLogical();
    int minResources = config.minResources();
    int maxResources = config.maxResources();
    boolean includeNoted = config.includeResourceNoted();
    
    // Create target lists with randomized counts for each resource
    List<Integer> minResourcesPerItem = new ArrayList<>();
    List<Integer> maxResourcesPerItem = new ArrayList<>();
    
    for (String resource : resourcesList) {
        int minCount = Rs2Random.between(minResources, maxResources);
        int maxCount = Rs2Random.between(minCount, maxResources);
        
        minResourcesPerItem.add(minCount);
        maxResourcesPerItem.add(maxCount);
    }
    
    // Create the appropriate logical condition based on configuration
    if (andLogical) {
        return GatheredResourceCondition.createAndCondition(
            resourcesList,
            minResourcesPerItem,
            maxResourcesPerItem,
            includeNoted
        );
    } else {
        return GatheredResourceCondition.createOrCondition(
            resourcesList,
            minResourcesPerItem,
            maxResourcesPerItem,
            includeNoted
        );
    }
}

### Process Item Condition
The process item condition is designed to track item transformation operations such as crafting, smithing, cooking, and other production skills. It can monitor the consumption of source items, the production of target items, or both, making it versatile for various crafting and production tasks:

```java
private Condition createProcessItemCondition() {
    ProcessItemCondition.TrackingMode trackingMode;
    
    // Map config enum to condition enum
    switch (config.trackingMode()) {
        case SOURCE_CONSUMPTION:
            trackingMode = ProcessItemCondition.TrackingMode.SOURCE_CONSUMPTION;
            break;
        // Other modes...
    }
    
    // Create the appropriate process item condition based on tracking mode
    if (trackingMode == ProcessItemCondition.TrackingMode.SOURCE_CONSUMPTION) {
        // If tracking source consumption
        // ...
    } 
    // Other tracking modes...
}
```

### NPC Kill Count Condition
The NPC kill count condition monitors the number of NPCs killed during the plugin's execution. It supports pattern matching for NPC names and can be configured to track kills per NPC type or the total kill count across all specified NPCs. This is particularly useful for combat training and slayer task automation:

```java
private LogicalCondition createNpcKillCountCondition() {
    // Parse the comma-separated list of NPC names
    List<String> npcNamesList = parseItemList(config.npcNames());
    
    boolean andLogical = config.npcLogical();
    int minKills = config.minKills();
    int maxKills = config.maxKills();
    boolean killsPerType = config.killsPerType();
    
    // If we're counting per NPC type vs. total kills...
}
```

## Custom Area Management
The custom area feature allows users to define a specific area in the game world where the plugin should operate. This is implemented through a combination of configuration settings, hotkey handling, and visual overlay feedback. The custom area is defined as a circle with a configurable radius centered on the player's position when the area is created:

```java
private void toggleCustomArea() {
    if (!Microbot.isLoggedIn()) {
        log.info("Cannot toggle custom area: Not logged in");
        return;
    }
    
    boolean isActive = config.customAreaActive();
    
    if (isActive) {
        // Clear the custom area
        config.setCustomAreaActive(false);
        config.setCustomAreaCenter(null);
        log.info("Custom area removed");
    } else {
        // Create new custom area at current position
        WorldPoint currentPos = null;
        if (Microbot.isLoggedIn()){
            currentPos = Rs2Player.getWorldLocation();
        }
        if (currentPos != null) {
            config.setCustomAreaCenter(currentPos);
            config.setCustomAreaActive(true);
            log.info("Custom area created at: " + currentPos.toString() + " with radius: " + config.customAreaRadius());
        }
    }
}
```

## Integration with Scheduler Events
The plugin integrates with the scheduler system by responding to events dispatched by the scheduler. The most important of these is the `PluginScheduleEntry`, which is triggered when the scheduler determines that a plugin should be stopped based on its stop conditions. The plugin handles this event by performing cleanup operations and then requesting that it be disabled:

```java
@Override
@Subscribe
public void onPluginScheduleEntry(PluginScheduleEntry event) {
    // Save location before stopping
    if (event.getPlugin() == this) {
        config.setLastLocation(Rs2Player.getWorldLocation());
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

## Helper Methods
The plugin includes several helper methods that provide utility functionality for various aspects of its operation. These methods encapsulate common operations and logic to improve code readability and maintainability:

```java
private List<String> parseItemList(String itemsString) {
    List<String> itemsList = new ArrayList<>();
    if (itemsString != null && !itemsString.isEmpty()) {
        String[] itemsArray = itemsString.split(",");
        for (String item : itemsArray) {
            String trimmedItem = item.trim();
            try {
                // Validate regex pattern
                java.util.regex.Pattern.compile(trimmedItem);
                itemsList.add(trimmedItem);
                log.debug("Valid item pattern found: {}", trimmedItem);
            } catch (java.util.regex.PatternSyntaxException e) {
                log.warn("Invalid regex pattern: '{}' - {}", trimmedItem, e.getMessage());
            }
        }
    }
    return itemsList;
}
```

## Usage Guide

### Setting Up the Plugin

1. **Enable the plugin** through RuneLite's plugin manager
   - Navigate to the plugin list and locate "Schedulable Example"
   - Check the checkbox to enable it
   - Note that the plugin can also be enabled by the scheduler when appropriate

2. **Configure desired start/stop conditions** in the plugin's configuration panel:
   - Click the configuration icon next to the plugin name
   - Expand the various sections to access different types of conditions
   - Configure at least one stop condition to ensure the plugin doesn't run indefinitely
   - Common configurations include:
     - Set time limits (minimum and maximum runtime)
     - Define item collection targets (specific items and quantities)
     - Configure NPC kill counts for combat activities
     - Set up resource gathering goals for skilling activities
     - Define item processing targets for crafting and production

3. **Set up location-based start conditions** if desired:
   - Enable the location start condition option
   - Choose between bank location or custom area:
     - **Bank Location**: Select a predefined bank location and set the maximum distance
     - **Custom Area**: Position your character in the desired location and press the configured area marking hotkey
   - The location overlay will show you when you're in a valid start position
   - For custom areas, you can adjust the radius to control the size of the valid area

4. **Start the plugin** in one of two ways:
   - Manually start it through the plugin manager
   - Let the scheduler start it automatically when scheduled and when start conditions are met
   
5. **Monitor the plugin's operation**:
   - Watch the status messages in the Microbot status area
   - Check the overlay for location-based information
   - The plugin will update its progress tracking as it runs

6. **The plugin will automatically stop** when any of the following occurs:
   - Any of the enabled stop conditions are satisfied
   - The scheduler sends a stop event
   - The plugin is manually disabled through the plugin manager

## Example Configuration

This configuration would make the plugin:
- Only start when the player is at the Grand Exchange
- Stop after running for 30-45 minutes OR after collecting 100-200 oak logs (whichever happens first)

```
enableLocationStartCondition: true
locationStartType: BANK
bankStartLocation: GRAND_EXCHANGE
bankDistance: 5

enableTimeCondition: true
minRuntime: 30
maxRuntime: 45

enableLootItemCondition: true
lootItems: "Oak logs"
minItems: 100
maxItems: 200
```

## Technical Implementation Notes

### Core Design Patterns and Principles

1. **Thread Safety**
   - The plugin uses `Microbot.getClientThread().invokeLater()` to ensure operations run on the client thread
   - This is critical for preventing race conditions and ensuring proper interaction with the game client
   - All UI updates and game state modifications should be performed on the client thread

2. **State Persistence**
   - Configuration state is saved between sessions using RuneLite's ConfigManager
   - The plugin maintains state across sessions by saving:
     - Last known player location
     - Custom area definitions
     - Configuration parameters
   - This allows seamless continuation of tasks even after client restarts

3. **Random Variance**
   - Stop conditions use randomized ranges to add human-like variability
   - The `Rs2Random.between()` utility is used to generate random values within configured ranges
   - This prevents predictable patterns that might appear bot-like
   - Different randomization approaches are used for different types of conditions

4. **Pattern Matching**
   - Item and NPC name matching supports regular expressions for flexibility
   - This allows for powerful pattern matching capabilities like:
     - Wildcards (e.g., ".*bones.*" to match any item containing "bones")
     - Character classes (e.g., "[A-Za-z]+ logs" to match any type of logs)
     - Alternations (e.g., "goblin|rat|spider" to match multiple NPC types)
   - Regular expression patterns are validated before use to prevent runtime errors

5. **Logical Composition**
   - Conditions can be combined with AND/OR logic for complex triggering
   - The `LogicalCondition` interface and its implementations (`AndCondition`, `OrCondition`) provide a composable framework
   - This allows for arbitrarily complex condition trees to be constructed
   - Each logical condition can contain any mix of primitive conditions or nested logical conditions

6. **State Machine Pattern**
   - The `SchedulableExampleScript` uses a state machine to manage its operation
   - Different states handle different aspects of the script's functionality
   - Transitions between states occur based on in-game conditions
   - This provides a clear, maintainable structure for complex bot logic

7. **Event-Driven Architecture**
   - The plugin responds to events from the scheduler and game client
   - Events trigger state changes and condition evaluations
   - This decouples the plugin's logic from the specific timing of game updates

## Extending the Plugin

### Adding New Condition Types

To extend the plugin with new types of conditions:

1. **Create a new condition class** implementing the `Condition` interface
   - Define the logic for when the condition is satisfied
   - Implement the `reset()` method to reinitialize the condition's state
   - Consider extending existing base classes like `ResourceCondition` if appropriate

2. **Add configuration options** to `SchedulableExampleConfig`
   - Create a new configuration section with `@ConfigSection` if needed
   - Add configuration items with `@ConfigItem` annotations
   - Define appropriate default values and descriptions
   - Consider using enums for options with a fixed set of valid values

3. **Implement a creation method** in `SchedulableExamplePlugin`
   - Create a method that constructs and configures your new condition
   - Add appropriate logic to handle configuration options
   - Include randomization if appropriate for human-like behavior
   - Handle edge cases and provide fallback values

4. **Add the condition** to the appropriate logical group in `getStopCondition()`
   - Check if the condition is enabled in the configuration
   - Add it to the existing logical condition structure (typically an `OrCondition`)
   - Consider how it interacts with other existing conditions

### Implementing New Features

To add entirely new functionality to the plugin:

1. **Extend the script class** with new methods and state management
   - Add new states to the state machine if needed
   - Implement the logic for the new functionality
   - Update the main loop to handle the new states and operations

2. **Update the configuration interface** with options for the new features
   - Group related settings into logical sections
   - Provide clear descriptions and default values
   - Add validation where appropriate

3. **Enhance the overlay** if visual feedback is needed
   - Add new information to the overlay rendering
   - Consider color coding or other visual cues for status
   - Ensure the overlay remains uncluttered and informative

4. **Add new condition types** if needed for the new functionality
   - Follow the steps outlined above for adding conditions
   - Ensure the conditions properly integrate with the new features

5. **Update documentation** to reflect the new capabilities
   - Document configuration options
   - Explain new condition types
   - Provide usage examples