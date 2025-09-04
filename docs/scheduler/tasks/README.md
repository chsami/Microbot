# Pre/Post Schedule Tasks System Documentation

## Overview

The Pre/Post Schedule Tasks system transforms plugin development by introducing automated preparation and cleanup capabilities into the Plugin Scheduler. This evolutionary enhancement shifts plugin architecture from manual resource management to declarative requirement specification, creating more reliable and user-friendly automation.

### The Transformation

Traditional plugin development required manual handling of equipment setup, inventory management, location positioning, and resource cleanup. The task system automates these operations through a declarative approach where plugins specify requirements rather than implementation details.

### System Architecture

The task system operates through three interconnected layers:

**Infrastructure Layer**: The [`AbstractPrePostScheduleTasks`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/AbstractPrePostScheduleTasks.java) provides thread-safe execution services, lifecycle management, and error handling foundations.

**Requirements Layer**: The [`PrePostScheduleRequirements`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/PrePostScheduleRequirements.java) framework enables declarative specification of plugin needs through a comprehensive requirement type system.

**Integration Layer**: The enhanced [`SchedulablePlugin`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/api/SchedulablePlugin.java) interface connects plugins seamlessly with the scheduler ecosystem.

## Evolution Through Three Commits

The system development followed three strategic phases:

### Phase 1: Enhanced Requirements Framework

Introduced flexible requirement definitions with sophisticated logical operations, enabling complex requirement combinations through OR operation modes and intelligent planning algorithms.

### Phase 2: Robust Infrastructure

Enhanced task lifecycle management with comprehensive cancellation support, sophisticated error handling, and rich user interface components for real-time monitoring.

### Phase 3: Production Integration

Established core infrastructure with the complete requirement type ecosystem, demonstrating real-world implementation through the GOTR plugin integration.

## Core Concepts

### Declarative Requirements

The system's power emerges from its declarative nature. Instead of writing procedural code to acquire items or navigate to locations, plugins declare requirements through specialized classes. The [`RequirementRegistry`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/registry/RequirementRegistry.java) manages these declarations and coordinates their fulfillment.

### Intelligent Fulfillment

The [`RequirementSolver`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/util/RequirementSolver.java) analyzes requirements and determines optimal fulfillment strategies, considering factors like resource availability, player location, and requirement priorities.

### Context-Aware Execution

Requirements operate within specific contexts (PRE_SCHEDULE, POST_SCHEDULE, BOTH) allowing the system to coordinate when different preparations occur during the plugin lifecycle.

## Documentation Structure

### Foundation Documentation

- **[Task Management System](task-management-system.md)** — Comprehensive guide to the infrastructure layer, covering execution services, lifecycle management, and error handling patterns
- **[Requirements System](requirements-system.md)** — Complete exploration of the requirement type ecosystem, fulfillment strategies, and advanced logical combinations
- **[Enhanced SchedulablePlugin API](enhanced-schedulable-plugin-api.md)** — Advanced integration patterns for scheduler connectivity and lifecycle management

### Implementation Resources

- **[Plugin Writer's Guide](plugin-writers-guide.md)** - Practical step-by-step implementation guidance with decision trees and best practices
- **[Requirements Integration](requirements-integration.md)** - Strategies for using requirements beyond scheduler context, enabling code reuse across different plugin architectures

### Reference Implementations

Study these production codebases for practical implementation insights:

- **GOTR Integration**: Examine [GOTR Plugin Implementation](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/runecrafting/gotr/) demonstrating sophisticated minigame automation with comprehensive preparation requirements
- **Example Plugin**: Review [SchedulableExample Implementation](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/VoxPlugins/schedulable/example/) showcasing complete system integration patterns

## Implementation Approach

### Understanding Before Implementation

The task system's effectiveness depends on understanding its conceptual framework before diving into implementation specifics. Focus on grasping how declarative requirements translate into automated behaviors.

### Three-Component Integration

Every implementation involves three fundamental components working in coordination:

**Requirements Definition**: Create a class extending [`PrePostScheduleRequirements`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/PrePostScheduleRequirements.java) that declares what your plugin needs to operate successfully.

**Task Management**: Develop a class extending [`AbstractPrePostScheduleTasks`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/AbstractPrePostScheduleTasks.java) that handles both requirement fulfillment and custom plugin-specific preparation or cleanup operations.

**Plugin Integration**: Modify your plugin class to implement the enhanced [`SchedulablePlugin`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/api/SchedulablePlugin.java) interface, establishing the connection with the scheduler system.

### Development Philosophy

Embrace the separation between "what" and "how" - specify what your plugin needs rather than how to obtain it. This approach creates more maintainable, reliable, and adaptable automation systems.

## System Benefits

### Transformation for Developers

The system revolutionizes plugin development by eliminating repetitive resource management code, providing standardized error handling patterns, and enabling focus on core plugin functionality rather than setup procedures.

### Enhanced User Experience

Users benefit from consistent preparation procedures across all plugins, automatic resource acquisition, intelligent optimization of preparation paths, and comprehensive progress feedback through rich UI components.

### Ecosystem Advantages

The framework promotes code reuse through shared requirement implementations, ensures quality consistency across plugin behaviors, optimizes performance through centralized resource management, and simplifies maintenance through standardized patterns.

## Technical Foundation

### Key Components

Explore the complete implementation through the [Task System Directory](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/) which contains all system components including requirement types, management infrastructure, UI components, and example implementations.

### Extension Points

The system design emphasizes extensibility through well-defined interfaces, allowing custom requirement types, specialized fulfillment strategies, and plugin-specific preparation procedures while maintaining integration with the broader framework.

## Next Steps for Implementation

1. **Conceptual Understanding**: Begin with the [Task Management System](task-management-system.md) documentation to understand infrastructure concepts
2. **Requirement Design**: Study the [Requirements System](requirements-system.md) to plan your plugin's requirement specification
3. **Practical Implementation**: Follow the [Plugin Writer's Guide](plugin-writers-guide.md) for step-by-step integration instructions
4. **Reference Study**: Examine the [GOTR implementation](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/runecrafting/gotr/) for production patterns
5. **Advanced Integration**: Explore [Requirements Integration](requirements-integration.md) for sophisticated usage patterns beyond basic scheduler integration

The Pre/Post Schedule Tasks system represents a paradigm shift toward intelligent, declarative automation that handles the complexity of game state management while maintaining the flexibility needed for diverse plugin requirements.
