# Rs2RunePouch Class Documentation

## [Back](development.md)

## Overview
The `Rs2RunePouch` class provides utilities to manage and interact with the Rune Pouch. It allows for checking rune contents, loading loadouts, and depositing runes into the bank.

## Methods

### `closeRunePouch`
- **Signature**: `public static boolean closeRunePouch()`
- **Description**: Closes the rune pouch interface if it is open.

### `contains`
- **Signature**: `public static boolean contains(int itemId)`
- **Description**: Checks if the rune pouch contains at least one of the specified rune by item ID.

### `contains`
- **Signature**: `public static boolean contains(int itemId, boolean allowCombinationRunes)`
- **Description**: Checks if the rune pouch contains at least one of the specified rune by item ID, with optional combination rune support.

### `contains`
- **Signature**: `public static boolean contains(int itemId, int amt)`
- **Description**: Checks if the rune pouch contains at least the specified quantity of the given rune item ID.

### `contains`
- **Signature**: `public static boolean contains(int itemId, int amt, boolean allowCombinationRunes)`
- **Description**: Checks if the rune pouch contains at least the specified quantity of the given rune item ID, with optional combination rune support.

### `contains`
- **Signature**: `public static boolean contains(Map<Runes, Integer> requiredRunes)`
- **Description**: Checks if the rune pouch contains all of the specified runes in the required quantities (strict match).

### `contains`
- **Signature**: `public static boolean contains(Map<Runes, Integer> requiredRunes, boolean allowCombinationRunes)`
- **Description**: Checks if the rune pouch contains the required runes and their quantities, with optional combination rune support.

### `contains`
- **Signature**: `public static boolean contains(Runes rune)`
- **Description**: Checks if the rune pouch contains at least one of the specified rune.

### `contains`
- **Signature**: `public static boolean contains(Runes rune, boolean allowCombinationRunes)`
- **Description**: Checks if the rune pouch contains at least one of the specified rune, with optional combination rune support.

### `contains`
- **Signature**: `public static boolean contains(Runes rune, int amt)`
- **Description**: Checks if the rune pouch contains at least the specified quantity of the given rune.

### `contains`
- **Signature**: `public static boolean contains(Runes rune, int amt, boolean allowCombinationRunes)`
- **Description**: Checks if the rune pouch contains at least the specified quantity of the given rune, with optional combination rune support.

### `depositAll`
- **Signature**: `public static boolean depositAll()`
- **Description**: Deposits all runes from the pouch into the bank. Requires the bank to be open.

### `fullUpdate`
- **Signature**: `public static void fullUpdate()`
- **Description**: Updates the internal state of the pouch from the current game varbits.

### `getLoadoutSlots`
- **Signature**: `public static Map<Integer, List<PouchSlot>> getLoadoutSlots()`
- **Description**: Retrieves the map of loaded pouch slots from the bank interface.

### `getQuantity`
- **Signature**: `public static int getQuantity(int itemId)`
- **Description**: Retrieves the current quantity of the specified rune item ID in the rune pouch.

### `getQuantity`
- **Signature**: `public static int getQuantity(Runes rune)`
- **Description**: Retrieves the current quantity of the specified rune in the rune pouch.

### `getRunes`
- **Signature**: `public static Map<Runes, Integer> getRunes()`
- **Description**: Retrieves the current rune pouch contents as a map of runes and their quantities.

### `getSlots`
- **Signature**: `public static List<PouchSlot> getSlots()`
- **Description**: Retrieves the list of current pouch slots.

### `isEmpty`
- **Signature**: `public static boolean isEmpty()`
- **Description**: Checks if the rune pouch is currently empty.

### `isRunePouchOpen`
- **Signature**: `public static boolean isRunePouchOpen()`
- **Description**: Checks if the rune pouch UI is currently open.

### `load`
- **Signature**: `public static boolean load(Map<Runes, Integer> requiredRunes)`
- **Description**: Attempts to load the specified runes into the rune pouch using strict matching. Can use bank loadouts or direct withdrawal.

### `loadFromInventorySetup`
- **Signature**: `public static boolean loadFromInventorySetup(Map<Runes, InventorySetupsItem> inventorySetupRunes)`
- **Description**: Attempts to load the specified runes into the rune pouch using fuzzy logic where applicable (e.g. from Inventory Setups).

### `onVarbitChanged`
- **Signature**: `public static void onVarbitChanged(VarbitChanged ev)`
- **Description**: Updates the specific pouch slot when a varbit is changed.

### `onWidgetLoaded`
- **Signature**: `public static void onWidgetLoaded(WidgetLoaded ev)`
- **Description**: Handles reading rune pouch loadouts from the bank interface widgets.

### `openRunePouch`
- **Signature**: `public static boolean openRunePouch()`
- **Description**: Opens the rune pouch UI.
