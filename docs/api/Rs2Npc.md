# Rs2Npc Class Documentation
## [Back](development.md)

> **Deprecated:** `Rs2Npc` is deprecated and marked for removal. Use the **Queryable API** via `Microbot.getRs2NpcCache().query()` instead. It provides a fluent, type-safe interface with better performance through tick-based caching.
>
> See the [Queryable API Documentation & Migration Guide](../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/QUERYABLE_API.md#migration-guide) for migration steps and side-by-side examples.
>
> **Quick comparison:**
> ```java
> // Legacy (deprecated)
> NPC banker = Rs2Npc.getNpc("Banker");
> Rs2Npc.interact(banker, "Bank");
>
> // Queryable API (recommended)
> Rs2NpcModel banker = Microbot.getRs2NpcCache().query()
>     .withName("Banker")
>     .where(npc -> !npc.isInteracting())
>     .nearest();
> banker.click("Bank");
> ```

## Overview
The `Rs2Npc` class provides a comprehensive suite of methods for interacting with NPCs within the game. These methods include retrieving NPCs by various attributes, validating NPC interactability, performing actions like attacking or pickpocketing, and handling NPC visibility and targeting through the game's camera and walking systems.

## Methods

### `getNpcByIndex`
- **Description**: Retrieves an NPC based on its unique index.
- **Parameters**:
    - `index`: `int` - The index of the NPC.
- **Returns**: `NPC` - The NPC corresponding to the given index or `null` if no NPC with that index exists.

### `validateInteractable`
- **Description**: Validates if an NPC can be interacted with by ensuring it is not null, then walking to its location and turning the camera towards it.
- **Parameters**:
    - `npc`: `NPC` - The NPC to validate.
- **Returns**: `NPC` - The same NPC if it is valid for interaction, `null` otherwise.

### `getNpcsForPlayer`
- **Description**: Retrieves a list of NPCs that are currently interacting with the player.
- **Returns**: `List<NPC>` - A list of NPCs sorted by their distance from the player.

### `getHealth`
- **Description**: Computes the health of an NPC based on its health ratio and scale.
- **Parameters**:
    - `npc`: `Actor` - The NPC whose health is to be determined.
- **Returns**: `int` - The estimated health of the NPC.

### `getNpcs`
- **Description**: Retrieves a stream of all NPCs currently in the game environment, filtered by non-null, non-dead status, and sorted by proximity to the player.
- **Returns**: `Stream<NPC>` - A stream of NPCs.

### `interact`
- **Description**: Performs a specified action on an NPC, such as "attack" or "talk-to".
- **Parameters**:
    - `npc`: `NPC` - The NPC to interact with.
    - `action`: `String` - The action to perform.
- **Returns**: `boolean` - `true` if the action was successfully initiated, `false` otherwise.

### `hasLineOfSight`
- **Description**: Checks if the player has a clear line of sight to the NPC.
- **Parameters**:
    - `npc`: `NPC` - The NPC to check visibility.
- **Returns**: `boolean` - `true` if there is a line of sight, `false` otherwise.

### `attack`
- **Description**: Initiates an attack on a given NPC.
- **Parameters**:
    - `npc`: `NPC` - The NPC to attack.
- **Returns**: `boolean` - `true` if the attack was successfully initiated, `false` otherwise.

## Remarks
- **Thread Safety**: Most operations that involve game data fetches are run on the client thread to ensure safe access.
- **NPC Interaction**: This class is central for tasks that require direct NPC interaction, providing both high-level methods for complex actions and low-level methods for precise control.

## Conclusion
The `Rs2Npc` class is an essential component of the Microbot client, facilitating advanced NPC interaction capabilities for automation tasks. Its methods are designed to handle various aspects of NPC engagement, from basic interactions to complex behaviors like combat and pathfinding.
