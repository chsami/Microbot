# Rs2Bank Class Documentation

## [Back](development.md)

## Overview
The `Rs2Bank` class manages interactions with the banking system in the game, facilitating operations like opening the bank, depositing, withdrawing, and managing inventory items.

## Methods

### `closeBank`
- **Signature**: `public static boolean closeBank()`
- **Description**: Closes the bank interface if it is open, ensuring it is properly closed by verifying the interface status.

### `depositAll`
- **Signature**: `public static boolean depositAll(int id)`
- **Description**: Deposits all instances of a specified item by its ID into the bank.

### `depositAllExcept(Integer... ids)`
- **Signature**: `public static boolean depositAllExcept(Integer... ids)`
- **Description**: Deposits all items except those identified by provided IDs.

### `depositAllExcept(String... names)`
- **Signature**: `public static boolean depositAllExcept(String... names)`
- **Description**: Deposits all items except those with names matching the provided strings.

### `depositEquipment`
- **Signature**: `public static void depositEquipment()`
- **Description**: Deposits all items currently equipped by the player into the bank.

### `depositOne`
- **Signature**: `public static void depositOne(int id)`
- **Description**: Deposits a single item into the bank by its ID.

### `depositOne(String name)`
- **Signature**: `public static void depositOne(String name)`
- **Description**: Deposits one item by its name, handling partial name matches.

### `depositX`
- **Signature**: `public static void depositX(int id, int amount)`
- **Description**: Deposits a specified quantity of an item by its ID.

### `findBankItem`
- **Signature**: `public static Rs2Item findBankItem(String name)`
- **Description**: Searches for a bank item by its name, supporting both exact and partial matches.

### `getNearestBank`
- **Signature**: `public static BankLocation getNearestBank()`
- **Description**: Identifies the nearest bank location relative to the player's current position.

### `handleBankPin`
- **Signature**: `public static boolean handleBankPin(String pin)`
- **Description**: Interacts with the bank PIN interface, entering the provided PIN if necessary.

### `hasItem`
- **Signature**: `public static boolean hasItem(int id)`
- **Description**: Verifies whether the player has a specific item in the bank, identified by its ID.

### `invokeMenu`
- **Signature**: `public static void invokeMenu(int entryIndex, Rs2Item rs2Item)`
- **Description**: Executes menu actions for a specified item and menu entry, adjusting according to the container type.

### `isOpen`
- **Signature**: `public static boolean isOpen()`
- **Description**: Checks if the bank interface is currently open. Notifies the user if a bank PIN needs to be entered.

### `openBank`
- **Signature**: `public static boolean openBank()`
- **Description**: Attempts to open the bank by interacting with the nearest NPC named "banker" or other bank-related objects.

### `openBank(NPC npc)`
- **Signature**: `public static boolean openBank(NPC npc)`
- **Description**: Opens the bank by directly interacting with a specified NPC.

### `openBank(TileObject object)`
- **Signature**: `public static boolean openBank(TileObject object)`
- **Description**: Attempts to open the bank by interacting with a specified tile object.

### `updateLocalBank`
- **Signature**: `public static void updateLocalBank(ItemContainerChanged e)`
- **Description**: Updates the cached list of bank items based on changes detected in an item container event.

### `useBank`
- **Signature**: `public static boolean useBank()`
- **Description**: Facilitates the use of a bank through available interaction means.

### `walkToBank`
- **Signature**: `public static boolean walkToBank()`
- **Description**: Directs the player to walk to the nearest bank location.

### `wearItem(int id)`
- **Signature**: `public static void wearItem(int id)`
- **Description**: Equips an item directly from the bank by its ID.

### `withdrawAll`
- **Signature**: `public static void withdrawAll(int id)`
- **Description**: Withdraws all instances of a specified item from the bank by its ID.

### `withdrawAll(String name)`
- **Signature**: `public static void withdrawAll(String name)`
- **Description**: Withdraws all items with a specified name from the bank.

### `withdrawAndEquip(int id)`
- **Signature**: `public static void withdrawAndEquip(int id)`
- **Description**: Withdraws and immediately equips an item by its ID.

### `withdrawOne`
- **Signature**: `public static void withdrawOne(int id)`
- **Description**: Withdraws a single item from the bank by its ID.

### `withdrawOne(String name)`
- **Signature**: `public static void withdrawOne(String name)`
- **Description**: Withdraws a single item based on its name from the bank.

### `withdrawX`
- **Signature**: `public static void withdrawX(int id, int amount)`
- **Description**: Withdraws a specified quantity of an item from the bank by its ID.

## Additional Details
For more complex methods involving item identification and interaction, examples of usage can further clarify the expected behavior and potential edge cases.
