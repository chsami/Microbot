# Rs2DepositBox Class Documentation

## [Back](development.md)

## Overview
The `Rs2DepositBox` class provides utility methods for interacting with deposit boxes in the game, including opening, closing, and depositing items.

## Methods

### `bankItemsAndWalkBackToOriginalPosition`
- **Signature**: `public static boolean bankItemsAndWalkBackToOriginalPosition(List<String> itemNames, WorldPoint initialPlayerLocation)`
- **Description**: Banks items if the inventory does not have enough empty slots. Will walk back to the initial player location.

### `bankItemsAndWalkBackToOriginalPosition`
- **Signature**: `public static boolean bankItemsAndWalkBackToOriginalPosition(List<String> itemNames, WorldPoint initialPlayerLocation, int emptySlotCount)`
- **Description**: Banks items if your inventory does not have enough empty slots. Will walk back to the initial player location.

### `bankItemsAndWalkBackToOriginalPosition`
- **Signature**: `public static boolean bankItemsAndWalkBackToOriginalPosition(List<String> itemNames, WorldPoint initialPlayerLocation, int emptySlotCount, int distance)`
- **Description**: Banks items if the inventory does not have enough empty slots. Will walk back to the initial player location, allowing a specified distance deviation.

### `bankItemsAndWalkBackToOriginalPosition`
- **Signature**: `public static boolean bankItemsAndWalkBackToOriginalPosition(List<String> itemNames, boolean exactItemNames, WorldPoint initialPlayerLocation, int emptySlotCount, int distance)`
- **Description**: Banks items at a deposit box if the inventory does not have enough empty slots. Will walk back to the initial player location. Supports exact or partial item name matching.

### `closeDepositBox`
- **Signature**: `public static boolean closeDepositBox()`
- **Description**: Closes the deposit box interface.

### `depositAll`
- **Signature**: `public static void depositAll()`
- **Description**: Deposits all items from the inventory into the deposit box.

### `depositAll`
- **Signature**: `public static boolean depositAll(Integer... ids)`
- **Description**: Deposits only the inventory items that match the specified IDs.

### `depositAll`
- **Signature**: `public static boolean depositAll(List<String> names)`
- **Description**: Deposits all inventory items that match the specified names (partial match).

### `depositAll`
- **Signature**: `public static boolean depositAll(List<String> names, boolean exact)`
- **Description**: Deposits all inventory items that match the specified names with option for exact match.

### `depositAll`
- **Signature**: `public static boolean depositAll(Predicate<Rs2ItemModel> predicate)`
- **Description**: Deposits all inventory items that match the given predicate.

### `depositAll`
- **Signature**: `public static boolean depositAll(String... names)`
- **Description**: Deposits all inventory items that match the specified names (partial match).

### `depositAllExcept`
- **Signature**: `public static boolean depositAllExcept(Integer... ids)`
- **Description**: Deposits all inventory items except those with the specified IDs.

### `depositAllExcept`
- **Signature**: `public static boolean depositAllExcept(List<String> names)`
- **Description**: Deposits all inventory items excluding items with the specified names (partial match).

### `depositAllExcept`
- **Signature**: `public static boolean depositAllExcept(List<String> names, boolean exact)`
- **Description**: Deposits all inventory items excluding items with the specified names with option for exact match.

### `depositAllExcept`
- **Signature**: `public static boolean depositAllExcept(String... names)`
- **Description**: Deposits all inventory items except those with the specified names.

### `depositEquipment`
- **Signature**: `public static void depositEquipment()`
- **Description**: Deposits all equipment into the deposit box.

### `depositOne`
- **Signature**: `public static void depositOne(int itemId)`
- **Description**: Deposits a specific item by its ID.

### `depositOne`
- **Signature**: `public static void depositOne(Rs2ItemModel rs2Item)`
- **Description**: Deposits a specific item by its `Rs2ItemModel` reference.

### `depositOne`
- **Signature**: `public static void depositOne(String itemName)`
- **Description**: Deposits a specific item by its name (partial match).

### `depositOne`
- **Signature**: `public static void depositOne(String itemName, boolean exact)`
- **Description**: Deposits a specific item by its name with option for exact match.

### `depositX`
- **Signature**: `public static void depositX(int itemId, int amount)`
- **Description**: Deposits a specified quantity of an item by its ID.

### `depositX`
- **Signature**: `public static void depositX(Rs2ItemModel rs2Item, int amount)`
- **Description**: Deposits a specified quantity of an item by its `Rs2ItemModel` reference.

### `depositX`
- **Signature**: `public static void depositX(String itemName, int amount)`
- **Description**: Deposits a specified quantity of an item by its name (partial match).

### `depositX`
- **Signature**: `public static void depositX(String itemName, int amount, boolean exact)`
- **Description**: Deposits a specified quantity of an item by its name with option for exact match.

### `getDepositBoxBounds`
- **Signature**: `public static Rectangle getDepositBoxBounds()`
- **Description**: Retrieves the bounding rectangle of the deposit box widget.

### `getDepositBoxWidget`
- **Signature**: `public static Widget getDepositBoxWidget()`
- **Description**: Retrieves the widget for the deposit box if it is currently open.

### `getItems`
- **Signature**: `public static List<Widget> getItems()`
- **Description**: Retrieves the list of item widgets in the deposit box container.

### `getItemWidget`
- **Signature**: `public static Widget getItemWidget(int slotId)`
- **Description**: Retrieves the widget of an item based on the specified slot ID in the deposit box.

### `getNearestDepositBox`
- **Signature**: `public static DepositBoxLocation getNearestDepositBox()`
- **Description**: Returns the nearest accessible deposit box to the local player’s current location.

### `getNearestDepositBox`
- **Signature**: `public static DepositBoxLocation getNearestDepositBox(WorldPoint worldPoint)`
- **Description**: Returns the nearest accessible deposit box to the specified world point.

### `getNearestDepositBox`
- **Signature**: `public static DepositBoxLocation getNearestDepositBox(WorldPoint worldPoint, int maxObjectSearchRadius)`
- **Description**: Finds the nearest accessible deposit box from the given world point, scanning for objects or using pathfinding.

### `isOpen`
- **Signature**: `public static boolean isOpen()`
- **Description**: Checks if the deposit box interface is open.

### `itemBounds`
- **Signature**: `public static Rectangle itemBounds(Rs2ItemModel rs2Item)`
- **Description**: Gets the bounding rectangle for the slot of the specified item in the deposit box.

### `openDepositBox`
- **Signature**: `public static boolean openDepositBox()`
- **Description**: Opens the deposit box interface by interacting with a nearby deposit box.

### `walkToAndUseDepositBox`
- **Signature**: `public static boolean walkToAndUseDepositBox()`
- **Description**: Walks to the nearest deposit box location and opens it.

### `walkToAndUseDepositBox`
- **Signature**: `public static boolean walkToAndUseDepositBox(DepositBoxLocation depositBoxLocation)`
- **Description**: Walks to the specified deposit box location and opens it.

### `walkToDepositBox`
- **Signature**: `public static boolean walkToDepositBox()`
- **Description**: Walks to the nearest deposit box location.

### `walkToDepositBox`
- **Signature**: `public static boolean walkToDepositBox(DepositBoxLocation depositBoxLocation)`
- **Description**: Walks to the specified deposit box location.
