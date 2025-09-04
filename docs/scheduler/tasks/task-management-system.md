# Task Management System

## Overview

The Task Management System forms the infrastructure foundation of the Pre/Post Schedule Tasks architecture. Built around the [`AbstractPrePostScheduleTasks`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/AbstractPrePostScheduleTasks.java) base class, this system provides thread-safe execution services, lifecycle management, and comprehensive error handling for plugin preparation and cleanup operations.

## Architectural Foundation

### Design Principles

The task management infrastructure operates on several core principles that ensure reliable and maintainable plugin automation:

**Thread Safety**: All operations utilize dedicated executor services to prevent interference with main plugin execution and ensure consistent behavior across concurrent operations.

**Lifecycle Management**: The system provides clear separation between preparation (pre-schedule) and cleanup (post-schedule) phases, with well-defined hooks for custom plugin-specific operations.

**Resource Cleanup**: Implements AutoCloseable patterns with guaranteed resource cleanup, preventing memory leaks and ensuring proper shutdown procedures.

**Error Resilience**: Comprehensive error handling with timeout support, cancellation capabilities, and graceful degradation when operations cannot complete successfully.

### Infrastructure Components

The task management system consists of several interconnected components:

**Executor Services**: Separate thread pools for pre-schedule and post-schedule operations, ensuring isolated execution environments and preventing cross-contamination of operations.

**Future Management**: CompletableFuture-based task coordination enabling asynchronous execution with proper timeout handling and cancellation support.

**State Tracking**: The [`TaskExecutionState`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/state/TaskExecutionState.java) system provides comprehensive monitoring of task progress and status information for UI components.

**Emergency Controls**: Built-in cancellation support through hotkey integration (Ctrl+C) allowing users to abort problematic operations safely.

## Implementation Architecture

### Base Class Structure

The [`AbstractPrePostScheduleTasks`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/AbstractPrePostScheduleTasks.java) provides the foundation that all plugin task managers extend. This base class handles the complex infrastructure concerns while exposing simple extension points for plugin-specific logic.

Key extension points include:

- `getPrePostScheduleRequirements()`: Returns the requirements definition for the plugin
- `executeCustomPreScheduleTask()`: Plugin-specific preparation logic beyond requirement fulfillment
- `executeCustomPostScheduleTask()`: Plugin-specific cleanup logic beyond resource management
- `isScheduleMode()`: Detection logic for determining if the plugin is running under scheduler control

### Execution Flow

The task execution follows a predictable pattern that ensures consistent behavior across all plugins:

**Pre-Schedule Phase**: The system first fulfills all requirements defined in the plugin's requirements specification, then executes any custom pre-schedule tasks. This ensures that all necessary resources, equipment, and game state conditions are met before the main plugin logic begins.

**Main Execution**: After successful preparation, the system invokes the provided callback to start the main plugin execution. The plugin runs with the confidence that all prerequisites have been satisfied.

**Post-Schedule Phase**: When the plugin completes or is stopped, the system executes custom cleanup tasks followed by requirement-based cleanup operations, ensuring proper resource management and state restoration.

### Thread Management

The system utilizes separate thread pools for different phases of execution to ensure proper isolation and prevent interference:

**Pre-Schedule Executor**: Dedicated to requirement fulfillment and preparation tasks, with appropriate timeout handling to prevent hanging operations.

**Post-Schedule Executor**: Focused on cleanup and resource management operations, designed to complete even when main execution has failed or been cancelled.

**Main Thread Integration**: Careful coordination with the main plugin thread to ensure UI updates and game interactions occur safely.

## Key Features

### Cancellation Support

The system provides multiple levels of cancellation support to handle different failure scenarios:

**User-Initiated Cancellation**: Emergency hotkey support (Ctrl+C) allows users to abort operations that are taking too long or behaving unexpectedly.

**Timeout-Based Cancellation**: Configurable timeouts prevent operations from hanging indefinitely, with graceful degradation when operations cannot complete within reasonable time limits.

**Cascade Cancellation**: When one operation fails or is cancelled, the system intelligently determines whether to abort dependent operations or continue with available resources.

### Error Handling Strategy

The error handling approach balances robustness with user feedback:

**Graceful Degradation**: Optional requirements that cannot be fulfilled don't prevent plugin execution, allowing partial preparation when full requirements cannot be met.

**Detailed Logging**: Comprehensive logging provides developers with detailed information about failure causes while maintaining appropriate log levels for different scenarios.

**User Communication**: Integration with the UI system ensures users receive clear feedback about preparation status, failures, and recovery options.

### State Management

The [`TaskExecutionState`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/state/TaskExecutionState.java) system tracks execution progress and provides information for UI components:

**Progress Tracking**: Real-time monitoring of requirement fulfillment progress, including individual requirement status and overall completion percentage.

**Status Information**: Current operation details, time elapsed, estimated completion time, and success/failure indicators.

**UI Integration**: Seamless integration with overlay components and status panels for rich user feedback.

**State Reset Capabilities**: The state tracking system provides intelligent reset functionality for handling interruptions and preparing for subsequent task executions, ensuring clean state transitions.

### Game State Awareness and Initialization

The task management system requires careful coordination with game state to ensure proper initialization timing:

**Why Initialization Timing Matters**: Many requirements need access to live game information such as current player location for shop proximity calculations, world position data for location requirements, equipment state for validation, and banking locations for optimal routing strategies.

**Proper Initialization Pattern**: The system should initialize when `GameState.LOGGED_IN` is reached to ensure all game information is available:

```java
@Subscribe
public void onGameStateChanged(GameStateChanged event) {
    if (event.getGameState() == GameState.LOGGED_IN) {
        // Initialize when game information is available
        getPrePostScheduleTasks();
        
        // Auto-start if in scheduler mode
        if (prePostScheduleTasks != null && 
            prePostScheduleTasks.isScheduleMode() && 
            !prePostScheduleTasks.isPreTaskComplete()) {
            runPreScheduleTasks();
        }
    } else if (event.getGameState() == GameState.LOGIN_SCREEN) {
        // Reset on logout for fresh initialization
        prePostScheduleRequirements = null;
        prePostScheduleTasks = null;
    }
}
```

**Initialization Validation**: The system validates successful initialization before allowing task execution:

```java
@Override
public AbstractPrePostScheduleTasks getPrePostScheduleTasks() {
    if (prePostScheduleRequirements == null || prePostScheduleTasks == null) {
        if(Microbot.getClient().getGameState() != GameState.LOGGED_IN) {
               log.debug("My Plugin - Cannot return pre/post schedule tasks - not logged in");
                return null; // Return null if not logged in
        }
        this.prePostScheduleRequirements = new MyPluginRequirements(config);
        this.prePostScheduleTasks = new MyPluginTasks(this, keyManager, prePostScheduleRequirements);
        if (prePostScheduleRequirements.isInitialized()){log.info("My Plugin PrePostScheduleRequirements initialized:\n{}", prePostScheduleRequirements.getDetailedDisplay());}
    }
    
    // Critical: Validate initialization success
    if (!prePostScheduleRequirements.isInitialized()) {
        log.error("Failed to initialize requirements system!");
        this.prePostScheduleRequirements = null;
        this.prePostScheduleTasks = null;
        return null; // Prevent task execution with failed requirements
    }
    
    return this.prePostScheduleTasks;
}
```

**Wait-for-Initialization Pattern**: Before executing tasks, plugins should verify that requirements are properly initialized, as some requirements need game data that's only available after login.

## Integration Patterns

### Plugin Implementation

Plugins integrate with the task management system by extending the base class and implementing the required abstract methods. The implementation focuses on plugin-specific logic while delegating infrastructure concerns to the base class.

Examine the [`ExamplePrePostScheduleTasks`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/examples/ExamplePrePostScheduleTasks.java) for a comprehensive implementation example, or study the GOTR integration in the [GOTR plugin directory](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/runecrafting/gotr/) for production usage patterns.

### Scheduler Integration

The task management system integrates seamlessly with the broader scheduler architecture through the [`SchedulablePlugin`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/api/SchedulablePlugin.java) interface, providing automatic task execution when plugins are managed by the scheduler.

### Resource Coordination

The system coordinates with the requirements framework to ensure efficient resource utilization, preventing conflicts between concurrent operations and optimizing the order of requirement fulfillment.

## Performance Considerations

### Resource Efficiency

The task management system is designed for efficient resource utilization:

**Lazy Initialization**: Executor services and resources are created only when needed, reducing memory footprint for plugins that don't use task functionality.

**Resource Pooling**: Thread pools are managed efficiently to prevent resource exhaustion while maintaining responsive behavior.

**Cleanup Guarantees**: AutoCloseable implementation ensures resources are properly released even when exceptions occur.

### Scalability

The architecture supports multiple concurrent task managers without interference, enabling complex plugin ecosystems where multiple automation systems operate simultaneously.

## Extension and Customization

### Custom Task Types

While the base system handles standard preparation and cleanup operations, plugins can extend the functionality through custom task implementations that integrate with the existing infrastructure.

### Integration Hooks

The system provides multiple integration points for advanced customization, including custom requirement types, specialized fulfillment strategies, and plugin-specific UI components.

### Future Enhancements

The architecture is designed for extensibility, with clear separation of concerns enabling future enhancements such as task prioritization, dependency management, and performance optimization without breaking existing implementations.

## Implementation Guidelines

### Development Approach

When implementing task managers, focus on clear separation between requirement definition and custom logic. Use the requirements system for standard operations like equipment setup and inventory management, reserving custom tasks for plugin-specific operations that cannot be expressed as requirements.

### Error Handling

Implement robust error handling in custom task methods, providing clear error messages and appropriate recovery strategies. The base infrastructure handles many error scenarios, but plugin-specific operations require careful consideration of failure modes.

### Testing and Validation

The task management system provides multiple hooks for testing and validation, enabling comprehensive testing of both requirement fulfillment and custom task operations in isolation.

The Task Management System provides the reliable foundation necessary for sophisticated plugin automation while maintaining the flexibility needed for diverse plugin requirements. By handling the complex infrastructure concerns, it enables plugin developers to focus on their core functionality while benefiting from standardized, robust preparation and cleanup procedures.
