# Rs2Shop Class Documentation

## [Back](development.md)

## Overview
The `Rs2Shop` class manages interactions with shops in the game, including opening shops, buying items, checking stock, and handling shop interfaces.

## Methods

### `buyItem`
- **Signature**: `public static boolean buyItem(String itemName, String quantity)`
- **Description**: Buys a specified quantity of an item from the shop by its name.

### `buyItem`
- **Signature**: `public static boolean buyItem(int itemId, String quantity)`
- **Description**: Buys a specified quantity of an item from the shop by its ID.

### `buyItemOptimally`
- **Signature**: `public static void buyItemOptimally(String itemName, int desiredQuantity)`
- **Description**: Buys an item in an optimal way given the desired total quantity. Uses allowed purchase quantities (50, 10, 5, 1) to minimize calls.

### `closeShop`
- **Signature**: `public static void closeShop()`
- **Description**: Closes the shop interface if it is open.

### `getNearestShopNpc`
- **Signature**: `public static Rs2NpcModel getNearestShopNpc(String npcName)`
- **Description**: Finds the nearest shop NPC with the specified name and "Trade" action (partial name matching).

### `getNearestShopNpc`
- **Signature**: `public static Rs2NpcModel getNearestShopNpc(String npcName, boolean exact)`
- **Description**: Finds the nearest shop NPC with the specified name and "Trade" action.

### `getSlot`
- **Signature**: `public static int getSlot(String itemName)`
- **Description**: Retrieves the slot number of the specified item in the shop.

### `hasMinimumStock`
- **Signature**: `public static boolean hasMinimumStock(int itemId, int minimumQuantity)`
- **Description**: Checks if the specified item is in stock in the shop with quantity >= minimumQuantity.

### `hasMinimumStock`
- **Signature**: `public static boolean hasMinimumStock(String itemName, int minimumQuantity)`
- **Description**: Checks if the specified item is in stock in the shop with quantity >= minimumQuantity.

### `hasStock`
- **Signature**: `public static boolean hasStock(int itemId)`
- **Description**: Checks if the specified item is in stock in the shop.

### `hasStock`
- **Signature**: `public static boolean hasStock(String itemName)`
- **Description**: Checks if the specified item is in stock in the shop.

### `isFull`
- **Signature**: `public static boolean isFull()`
- **Description**: Checks if the shop is completely full.

### `isOpen`
- **Signature**: `public static boolean isOpen()`
- **Description**: Checks if the shop interface is open.

### `openShop`
- **Signature**: `public static boolean openShop(String npc)`
- **Description**: Opens the shop interface by interacting with the specified NPC (partial name match).

### `openShop`
- **Signature**: `public static boolean openShop(String npcName, boolean exact)`
- **Description**: Opens the shop interface by interacting with the specified NPC.

### `storeShopItemsInMemory`
- **Signature**: `public static void storeShopItemsInMemory(ItemContainerChanged e, int id)`
- **Description**: Updates the shop items in memory based on the provided event.

### `waitForShopChanges`
- **Signature**: `public static boolean waitForShopChanges()`
- **Description**: Waits for the shop content to change by comparing the current items with the cached values.
