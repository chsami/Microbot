# Rs2Gembag Class Documentation

## [Back](development.md)

## Overview
The `Rs2Gembag` class manages the state and interactions with the Gem Bag. It tracks the contents of the gem bag based on chat messages and inventory interactions.

## Methods

### `checkGemBag`
- **Signature**: `public static void checkGemBag()`
- **Description**: Initiates checking the gem bag contents. Interacts with the gem bag to "Check" it, which triggers a chat message with the contents.

### `getGemBagContents`
- **Signature**: `public static List<Rs2ItemModel> getGemBagContents()`
- **Description**: Retrieves the current list of items believed to be in the gem bag.

### `getGemBagItemIds`
- **Signature**: `public static List<Integer> getGemBagItemIds()`
- **Description**: Returns the list of item IDs that correspond to the gem bag (open and closed).

### `getGemCount`
- **Signature**: `public static int getGemCount(String gem)`
- **Description**: Gets the count of a specific gem in the gem bag by its name. Returns 0 if the gem bag is missing or contents are unknown.

### `getTotalGemCount`
- **Signature**: `public static int getTotalGemCount()`
- **Description**: Gets the total count of all gems currently stored in the gem bag.

### `hasGemBag`
- **Signature**: `public static boolean hasGemBag()`
- **Description**: Checks if the player has a gem bag in their inventory.

### `isAnyGemSlotFull`
- **Signature**: `public static boolean isAnyGemSlotFull()`
- **Description**: Checks if any gem slot in the gem bag is full (quantity 60).

### `isGemBagOpen`
- **Signature**: `public static boolean isGemBagOpen()`
- **Description**: Checks if the gem bag is currently open (based on item ID).

### `isUnknown`
- **Signature**: `public static boolean isUnknown()`
- **Description**: Checks if the contents of the gem bag are currently unknown (e.g., haven't been checked yet).

### `onChatMessage`
- **Signature**: `public static void onChatMessage(ChatMessage event)`
- **Description**: Handles chat messages to update gem bag contents. Listens for messages indicating gems found or the "Check" message from the gem bag.

### `onMenuOptionClicked`
- **Signature**: `public static void onMenuOptionClicked(MenuOptionClicked event)`
- **Description**: Handles menu option clicks related to the gem bag, such as "Empty" or "Fill", to update the internal state of the bag contents.

### `resetGemBagContents`
- **Signature**: `public static void resetGemBagContents()`
- **Description**: Clears the internal record of the gem bag contents.
