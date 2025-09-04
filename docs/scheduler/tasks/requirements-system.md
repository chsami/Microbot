# Requirements System

## Overview

The Requirements System transforms plugin development by introducing a declarative approach to resource management and game state preparation. Built around the [`PrePostScheduleRequirements`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/PrePostScheduleRequirements.java) framework and powered by the [`RequirementRegistry`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/registry/RequirementRegistry.java), this system enables plugins to specify what they need rather than how to obtain it.

## Declarative Philosophy

### Paradigm Shift

Traditional plugin development required imperative programming for resource acquisition - writing step-by-step procedures to obtain items, navigate to locations, and configure game state. The requirements system introduces a declarative paradigm where plugins specify desired outcomes and delegate implementation details to intelligent fulfillment algorithms.

### Separation of Concerns

The system achieves clean separation between requirement specification and fulfillment implementation. Plugin developers focus on defining requirements using specialized requirement types, while the fulfillment engine handles the complex logistics of actually meeting those requirements.

### Intelligent Fulfillment

The [`RequirementSolver`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/util/RequirementSolver.java) analyzes requirement sets and determines optimal fulfillment strategies, considering factors like resource availability, player location, priority levels, and interdependencies between requirements.

## Requirement Type Ecosystem

### Item Requirements

The [`ItemRequirement`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/requirement/item/ItemRequirement.java) system handles equipment and inventory management with sophisticated features:

**Equipment Management**: Automatic detection of optimal equipment for activity requirements, with support for equipment slots, stat requirements, and compatibility checking.

**Inventory Planning**: Intelligent inventory space management that considers item stacking, quantities needed, and space optimization for different activities.

**Collection Integration**: The [`ItemRequirementCollection`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/data/ItemRequirementCollection.java) provides pre-configured equipment sets for common activities like combat, skilling, and specialized minigames.

### Location Requirements

The [`LocationRequirement`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/requirement/location/LocationRequirement.java) system manages player positioning and navigation:

**Smart Navigation**: Integration with the walking system to automatically navigate to required locations using optimal pathfinding algorithms.

**Proximity Handling**: Flexible proximity requirements that can specify exact positioning or acceptable ranges depending on activity needs.

**Resource Location Integration**: The [`ResourceLocationOption`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/requirement/location/ResourceLocationOption.java) connects location requirements with resource availability, choosing optimal locations based on current game state.

### Spellbook Requirements

The [`SpellbookRequirement`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/requirement/SpellbookRequirement.java) manages magic system configuration:

**Automatic Switching**: Seamless spellbook changes with restoration to original state during cleanup phases.

**Context Awareness**: Different spellbook requirements for different phases of execution, enabling complex spell usage patterns.

**State Preservation**: Careful tracking of original spellbook state to ensure proper restoration after task completion.

### Shop Requirements

The [`ShopRequirement`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/requirement/shop/) system handles Grand Exchange and NPC shop interactions:

**Multi-Item Support**: The [`MultiItemConfig`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/requirement/shop/models/MultiItemConfig.java) enables complex purchasing strategies for items that come in sets or have alternatives.

**World Hopping Integration**: The [`WorldHoppingConfig`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/requirement/shop/models/WorldHoppingConfig.java) provides intelligent world selection for optimal shop availability.

**Transaction Management**: Sophisticated handling of offer states, cancellations, and retry logic through the [`CancelledOfferState`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/requirement/shop/models/CancelledOfferState.java) system.

### Logical Requirements

The [`LogicalRequirement`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/requirement/logical/) system enables complex requirement combinations:

**OR Operations**: The [`OrRequirement`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/requirement/logical/OrRequirement.java) allows specification of alternative requirements, with intelligent selection based on the [`OrRequirementMode`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/enums/OrRequirementMode.java) configuration.

**Conditional Logic**: Advanced requirement combinations that adapt to current game state and player capabilities.

**Hierarchical Planning**: Complex requirement trees that enable sophisticated preparation strategies for advanced activities.

### Conditional Requirements

The [`ConditionalRequirement`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/requirement/conditional/) system provides state-dependent requirement activation:

**Dynamic Requirements**: Requirements that activate or deactivate based on current game conditions, player state, or plugin configuration.

**Ordered Execution**: The [`OrderedRequirement`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/requirement/conditional/OrderedRequirement.java) enables sequential requirement fulfillment when order matters.

**State-Driven Logic**: Requirements that adapt their behavior based on complex state evaluation using the [`ConditionalRequirementBuilder`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/util/ConditionalRequirementBuilder.java).

## Registry Architecture

### Centralized Management

The [`RequirementRegistry`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/registry/RequirementRegistry.java) serves as the central coordination point for all requirements within a plugin, providing:

**Uniqueness Enforcement**: Automatic prevention of duplicate requirements while allowing updates and refinements to existing requirements.

**Type-Safe Access**: Efficient retrieval of requirements by type, context, and priority level with compile-time safety guarantees.

**Consistency Guarantees**: Validation of requirement combinations to prevent conflicting or impossible requirement sets.

### Context Management

The [`TaskContext`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/enums/TaskContext.java) system enables requirements to specify when they should be fulfilled:

**PRE_SCHEDULE**: Requirements fulfilled before main plugin execution begins, ensuring all prerequisites are met.

**POST_SCHEDULE**: Requirements for cleanup and restoration operations after plugin completion.

**BOTH**: Requirements that must be fulfilled immediately when encountered, for urgent or time-sensitive operations.

### Priority Framework

The [`RequirementPriority`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/enums/RequirementPriority.java) system enables intelligent requirement prioritization:

**MANDATORY**: Requirements that must be fulfilled for plugin execution to proceed.

**RECOMMENDED**: Requirements that significantly improve plugin performance but aren't essential.

## Advanced Features

### OR Requirement Modes

The [`OrRequirementMode`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/enums/OrRequirementMode.java) system provides sophisticated strategies for handling alternative requirements:

 **ANY_COMBINATION**: The total amount can be fulfilled by any combination of items in the OR requirement. For example, if 5 food items are needed, you could have 2 lobsters + 3 swordfish.
 **SINGLE_TYPE**: Must fulfill the entire amount with exactly one type of item from the OR requirement. For example, if 5 food items are needed, you must have exactly 5 lobsters OR 5 swordfish, but not a combination.
### Requirement Selection

The [`RequirementSelector`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/util/RequirementSelector.java) provides sophisticated algorithms for choosing optimal requirement combinations when multiple options are available.

### Collection Systems

Pre-configured requirement collections eliminate repetitive specification of common requirement patterns:

**Skill Outfits**: Complete equipment sets for various skills including woodcutting, mining, fishing, and combat activities.

**Combat Configurations**: Comprehensive combat setups including weapons, armor, consumables, and utility items.

**Utility Collections**: Common requirement patterns for activities like banking, transportation, and resource gathering.

## Implementation Patterns

### Basic Requirements Definition

Plugin requirements are defined by extending [`PrePostScheduleRequirements`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/requirements/PrePostScheduleRequirements.java) and implementing the `initializeRequirements()` method. This method uses the registry to declare all requirements the plugin needs for successful operation.

### Advanced Requirement Combinations

Complex activities often require sophisticated requirement combinations that adapt to current game state. The logical requirement system enables these advanced patterns through OR operations, conditional requirements, and hierarchical planning structures.

### Integration with Task Management

Requirements integrate seamlessly with the task management system through automatic fulfillment during pre-schedule phases and cleanup during post-schedule phases. This integration ensures that plugins always start with their requirements satisfied and clean up properly when execution completes.

## Performance and Optimization

### Efficient Fulfillment

The requirement fulfillment engine optimizes execution by:

**Dependency Analysis**: Understanding relationships between requirements to optimize fulfillment order.

**Resource Sharing**: Identifying opportunities to fulfill multiple requirements with single operations.

**Path Optimization**: Coordinating location-based requirements to minimize unnecessary travel.

### Caching and State Management

The system employs intelligent caching strategies to avoid redundant validation and fulfillment operations, while maintaining accuracy through proper cache invalidation when game state changes.

### Asynchronous Operations

Where appropriate, the system utilizes asynchronous fulfillment patterns to improve responsiveness and allow for parallel requirement processing when operations don't interfere with each other.

## Extension and Customization

### Custom Requirement Types

The system is designed for extensibility, enabling plugins to create custom requirement types for specialized needs while integrating with the existing fulfillment infrastructure.

### Specialized Fulfillment Strategies

Plugins can provide custom fulfillment strategies for complex or highly specialized requirements that cannot be handled by the standard fulfillment algorithms.

### Integration Hooks

Multiple integration points enable custom UI components, specialized validation logic, and plugin-specific optimization strategies while maintaining compatibility with the broader system.

## Real-World Usage

### Example Implementations

Study the [`ExamplePrePostScheduleRequirements`](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/pluginscheduler/tasks/examples/ExamplePrePostScheduleRequirements.java) for comprehensive implementation patterns, or examine the GOTR integration in the [GOTR plugin directory](../../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/runecrafting/gotr/) for production usage.

### Integration Patterns

The requirements system integrates with various aspects of the plugin ecosystem, from simple equipment setups to complex multi-phase activities requiring sophisticated resource coordination.

### Evolution and Enhancement

The requirement system continues to evolve based on real-world usage patterns, with new requirement types and fulfillment strategies added to address emerging plugin needs while maintaining backward compatibility.

The Requirements System represents a fundamental advancement in plugin automation, shifting from imperative resource management to declarative requirement specification. This transformation enables more reliable, maintainable, and user-friendly automation while providing the flexibility needed for diverse plugin requirements.
