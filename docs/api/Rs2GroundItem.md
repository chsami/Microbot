# Rs2GroundItem Class Documentation
## [Back](development.md)

> **Deprecated:** `Rs2GroundItem` is deprecated and marked for removal. Use the **Queryable API** via `Microbot.getRs2TileItemCache().query()` instead. It provides a fluent, type-safe interface with better performance through tick-based caching.
>
> See the [Queryable API Documentation & Migration Guide](../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/QUERYABLE_API.md#migration-guide) for migration steps and side-by-side examples.
>
> **Quick comparison:**
> ```java
> // Legacy (deprecated)
> Rs2GroundItem.loot("Dragon bones", 1, 10);
>
> // Queryable API (recommended)
> Rs2TileItemModel bones = Microbot.getRs2TileItemCache().query()
>     .withName("Dragon bones")
>     .nearest();
> bones.click("Take");
> ```

## Overview
The `Rs2GroundItem` class provides methods to interact with ground items within the game, facilitating operations like picking up, checking existence, and interacting with items based on various criteria such as item ID, name, or location.

## Methods

### `exists`
- **Signature**: `public static boolean exists(String itemName, int range)`
- **Description**: Checks if a ground item with the specified name exists within a given range.

### `exists`
- **Signature**: `public static boolean exists(int itemId, int range)`
- **Description**: Checks if a ground item with the specified ID exists within a given range.

### `getAll`
- **Signature**: `public static RS2Item[] getAll(int range)`
- **Description**: Retrieves all ground items within a specified range from the player's location.

### `getAllAt`
- **Signature**: `public static RS2Item[] getAllAt(int x, int y)`
- **Description**: Retrieves all ground items located at a specific tile.

### `getTile`
- **Signature**: `public static Tile getTile(int x, int y)`
- **Description**: Retrieves the tile at a given world coordinate.

### `interact`
- **Signature**: `public static boolean interact(String itemName, String action, int x, int y)`
- **Description**: Interacts with a ground item by name and specified action at a specific tile location.

### `interact`
- **Signature**: `public static boolean interact(int itemId, String action, int x, int y)`
- **Description**: Interacts with a ground item by ID and specified action at a specific tile location.

### `interact`
- **Signature**: `public static boolean interact(String itemName, String action, int range)`
- **Description**: Interacts with all ground items matching the specified name and action within a certain range.

### `interact`
- **Signature**: `public static boolean interact(int itemId, String action, int range)`
- **Description**: Interacts with all ground items matching the specified ID and action within a certain range.

### `loot`
- **Signature**: `public static boolean loot(String lootItem, int minQuantity, int range)`
- **Description**: Attempts to pick up ground items that match the specified item name, quantity, and within a certain range.

### `lootAllItemBasedOnValue`
- **Signature**: `public static boolean lootAllItemBasedOnValue(int value, int range)`
- **Description**: Attempts to pick up all ground items based on their total value exceeding a specified amount within a certain range.

### `lootAtGePrice`
- **Signature**: `public static boolean lootAtGePrice(int minGePrice)`
- **Description**: Attempts to pick up items based on their Grand Exchange price being above a certain threshold.

### `lootItemBasedOnValue`
- **Signature**: `public static boolean lootItemBasedOnValue(int value, int range)`
- **Description**: Attempts to pick up a single item based on its total value exceeding a specified amount within a certain range.

### `pickup`
- **Signature**: `public static boolean pickup(String lootItem, int range)`
- **Description**: Alias for looting an item, focusing on the action of picking it up.

### `take`
- **Signature**: `public static boolean take(String lootItem, int range)`
- **Description**: Another alias for looting an item, emphasizing the action of taking it.

## Additional Details
This class is essential for scripts that automate the collection or interaction with items on the ground within the game, improving efficiency and managing inventory based on item properties and player needs.
