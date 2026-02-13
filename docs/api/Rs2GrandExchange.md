# Rs2GrandExchange Class Documentation

## [Back](development.md)

## Overview
The `Rs2GrandExchange` class provides methods for interacting with the Grand Exchange in the game. It allows for buying, selling, collecting offers, and managing Grand Exchange slots.

## Methods

### `abortAllOffers`
- **Signature**: `public static boolean abortAllOffers(boolean collectToBank)`
- **Description**: Aborts all active Grand Exchange offers. Iterates through all Grand Exchange slots and attempts to abort any active offers. After aborting, collects all items from the Grand Exchange interface.

### `abortOffer`
- **Signature**: `public static boolean abortOffer(String name, boolean collectToBank)`
- **Description**: Aborts an active Grand Exchange offer for the specified item name. Attempts to locate a non-available Grand Exchange slot containing an offer matching the given item name (case-insensitive). If found, sends an "Abort offer" action. After aborting, collects all items from the Grand Exchange.

### `backToOverview`
- **Signature**: `public static void backToOverview()`
- **Description**: Clicks the back button to go from the buy/sell offer screen to the all slots overview.

### `buyItem`
- **Signature**: `public static boolean buyItem(String itemName, int price, int quantity)`
- **Description**: Creates and processes a request to buy an item on the Grand Exchange. Constructs a `BUY` type request using the specified item name, price, and quantity.

### `cancelSpecificOffers`
- **Signature**: `public static List<Map<String, Object>> cancelSpecificOffers(List<GrandExchangeSlots> slotsToCancel, boolean collectToBank)`
- **Description**: Cancels only specific Grand Exchange slots instead of all offers. Returns details of cancelled offers for potential restoration.

### `closeExchange`
- **Signature**: `public static void closeExchange()`
- **Description**: Closes the Grand Exchange interface.

### `collectAll`
- **Signature**: `public static boolean collectAll(boolean collectToBank)`
- **Description**: Collects all completed Grand Exchange offers either into the bank or inventory. Checks if all slots are empty first. If the inventory is full, attempts to open the bank and deposit all items before continuing.

### `collectAllToBank`
- **Signature**: `public static boolean collectAllToBank()`
- **Description**: Collects all completed Grand Exchange offers directly into the bank. Convenience method calling `collectAll(true)`.

### `collectAllToInventory`
- **Signature**: `public static boolean collectAllToInventory()`
- **Description**: Collects all completed Grand Exchange offers into the inventory. Convenience method calling `collectAll(false)`.

### `collectOffer`
- **Signature**: `public static boolean collectOffer(GrandExchangeSlots slot, boolean toBank)`
- **Description**: Collects items from a specific Grand Exchange slot.

### `collectOfferAndGetQuantity`
- **Signature**: `public static int collectOfferAndGetQuantity(GrandExchangeSlots slot, boolean toBank, int itemId)`
- **Description**: Collects a specific offer and returns the exact number of items obtained. Tracks the actual quantity collected.

### `findSlotForItem`
- **Signature**: `public static GrandExchangeSlots findSlotForItem(int itemId, boolean isSelling)`
- **Description**: Finds the Grand Exchange slot currently holding an offer for the specified item ID.

### `findSlotForItem`
- **Signature**: `public static GrandExchangeSlots findSlotForItem(String itemName, boolean isSelling)`
- **Description**: Finds the Grand Exchange slot currently holding an offer for the specified item name.

### `getActiveOfferSlots`
- **Signature**: `public static GrandExchangeSlots[] getActiveOfferSlots()`
- **Description**: Returns all slots with active offers.

### `getAdaptiveBuyPrice`
- **Signature**: `public static int getAdaptiveBuyPrice(int itemId, double basePercentage, int retryAttempt)`
- **Description**: Calculates an intelligent buy price based on market conditions and retry attempts.

### `getAdaptiveSellPrice`
- **Signature**: `public static int getAdaptiveSellPrice(int itemId, double basePercentage, int retryAttempt)`
- **Description**: Calculates an intelligent sell price based on market conditions and retry attempts.

### `getAvailableSlot`
- **Signature**: `public static GrandExchangeSlots getAvailableSlot()`
- **Description**: Retrieves the first available Grand Exchange slot.

### `getAvailableSlots`
- **Signature**: `public static GrandExchangeSlots[] getAvailableSlots()`
- **Description**: Retrieves all currently available Grand Exchange slots.

### `getAvailableSlotsCount`
- **Signature**: `public static int getAvailableSlotsCount()`
- **Description**: Returns the count of currently available Grand Exchange slots.

### `getAveragePrice`
- **Signature**: `public static int getAveragePrice(int itemId)`
- **Description**: Gets average price over the last hour.

### `getAveragePrice`
- **Signature**: `public static int getAveragePrice(int itemId, TimeSeriesInterval interval)`
- **Description**: Gets average price over a specific time period using time-series data.

### `getBuyingVolume`
- **Signature**: `public static int getBuyingVolume(int itemId)`
- **Description**: Gets the buying volume for an item from GE Tracker.

### `getCompletedOffers`
- **Signature**: `public static Map<GrandExchangeSlots, GrandExchangeOfferDetails> getCompletedOffers()`
- **Description**: Finds and returns all slots that have completed offers ready for collection.

### `getItemMappingData`
- **Signature**: `public static ItemMappingData getItemMappingData(int itemId)`
- **Description**: Gets item mapping data including trade limits from OSRS Wiki API.

### `getItemsBoughtFromOffer`
- **Signature**: `public static int getItemsBoughtFromOffer(GrandExchangeSlots slot)`
- **Description**: Gets the exact number of items bought from a specific Grand Exchange slot offer.

### `getItemsSoldFromOffer`
- **Signature**: `public static int getItemsSoldFromOffer(GrandExchangeSlots slot)`
- **Description**: Gets the exact number of items sold from a specific Grand Exchange slot offer.

### `getOfferDetails`
- **Signature**: `public static GrandExchangeOfferDetails getOfferDetails(GrandExchangeSlots slot)`
- **Description**: Gets detailed information about a Grand Exchange offer in the specified slot.

### `getOfferDetailsForCancellation`
- **Signature**: `public static Map<String, Object> getOfferDetailsForCancellation(GrandExchangeSlots slot)`
- **Description**: Enhanced slot management: Gets detailed information about an offer before cancelling it.

### `getPrice`
- **Signature**: `public static int getPrice(int itemId)`
- **Description**: Gets the overall price for an item from GE Tracker.

### `getRealTimePrices`
- **Signature**: `public static WikiPrice getRealTimePrices(int itemId)`
- **Description**: Gets real-time price data with caching from OSRS Wiki API (primary) or GE Tracker (fallback).

### `getSearchResultWidget`
- **Signature**: `public static Pair<Widget, Integer> getSearchResultWidget(String search, boolean exact)`
- **Description**: Searches the Grand Exchange item search results widget for an entry matching the specified search text.

### `getSellingVolume`
- **Signature**: `public static int getSellingVolume(int itemId)`
- **Description**: Gets the selling volume for an item from GE Tracker.

### `getSellPrice`
- **Signature**: `public static int getSellPrice(int itemId)`
- **Description**: Gets the selling price for an item from GE Tracker.

### `getSlotFromIndex`
- **Signature**: `public static GrandExchangeSlots getSlotFromIndex(int index)`
- **Description**: Helper method to get a GrandExchangeSlots enum value from its index.

### `getSlotIndex`
- **Signature**: `public static int getSlotIndex(GrandExchangeSlots slot)`
- **Description**: Gets the index (0-7) of the slot in the GE interface.

### `getTimeSeriesData`
- **Signature**: `public static TimeSeriesAnalysis getTimeSeriesData(int itemId, TimeSeriesInterval interval)`
- **Description**: Gets time-series data starting from current time going back.

### `getTimeSeriesData`
- **Signature**: `public static TimeSeriesAnalysis getTimeSeriesData(int itemId, TimeSeriesInterval interval, Long fromTimestamp)`
- **Description**: Gets time-series price data from OSRS Wiki API.

### `hasBoughtOffer`
- **Signature**: `public static boolean hasBoughtOffer()`
- **Description**: Checks if any offer has a state of `BOUGHT`.

### `hasBoughtOffer`
- **Signature**: `public static boolean hasBoughtOffer(GrandExchangeSlots slot)`
- **Description**: Checks if a specific Grand Exchange slot has a completed buy offer ready to collect.

### `hasFinishedBuyingOffers`
- **Signature**: `public static boolean hasFinishedBuyingOffers()`
- **Description**: Checks if there are bought offers and no buying offers active.

### `hasFinishedSellingOffers`
- **Signature**: `public static boolean hasFinishedSellingOffers()`
- **Description**: Checks if there are sold offers and no selling offers active.

### `hasSoldOffer`
- **Signature**: `public static boolean hasSoldOffer()`
- **Description**: Checks if any offer has a state of `SOLD`.

### `hasSoldOffer`
- **Signature**: `public static boolean hasSoldOffer(GrandExchangeSlots slot)`
- **Description**: Checks if a specific Grand Exchange slot has a completed sell offer ready to collect.

### `isAllSlotsEmpty`
- **Signature**: `public static boolean isAllSlotsEmpty()`
- **Description**: Checks if all Grand Exchange slots are currently empty (available).

### `isCancelledOfferWithItems`
- **Signature**: `public static boolean isCancelledOfferWithItems(GrandExchangeSlots slot)`
- **Description**: Checks if an offer was cancelled and still has items that can be collected.

### `isOfferScreenOpen`
- **Signature**: `public static boolean isOfferScreenOpen()`
- **Description**: Checks if the offer screen (buy/sell offer screen) is open.

### `isOpen`
- **Signature**: `public static boolean isOpen()`
- **Description**: Checks if the Grand Exchange screen is open.

### `isSlotAvailable`
- **Signature**: `public static boolean isSlotAvailable(GrandExchangeSlots slot)`
- **Description**: Checks if a specified Grand Exchange slot is available for a new offer.

### `openExchange`
- **Signature**: `public static boolean openExchange()`
- **Description**: Opens the Grand Exchange. Interacts with the "Grand Exchange Clerk" NPC. Handles bank pin if necessary.

### `processOffer`
- **Signature**: `public static boolean processOffer(GrandExchangeRequest request)`
- **Description**: Processes a Grand Exchange offer request based on the action specified in the `GrandExchangeRequest`. Supports `COLLECT`, `BUY`, and `SELL` actions.

### `restoreOffer`
- **Signature**: `public static boolean restoreOffer(Map<String, Object> offerDetails, GrandExchangeSlots targetSlot)`
- **Description**: Attempts to restore a cancelled offer using the provided details.

### `sellInventory`
- **Signature**: `public static boolean sellInventory()`
- **Description**: Attempts to sell all tradeable items currently in the player's inventory on the Grand Exchange.

### `sellItem`
- **Signature**: `public static boolean sellItem(String itemName, int quantity, int price)`
- **Description**: Creates and processes a request to sell an item on the Grand Exchange. Constructs a `SELL` type request.

### `sellLoot`
- **Signature**: `public static boolean sellLoot(String npcName, List<String> itemsToNotSell)`
- **Description**: Sells all the tradeable loot items from a specific NPC name, excluding specified items.

### `setChatboxValue`
- **Signature**: `public static void setChatboxValue(int value)`
- **Description**: Sets a value in the chatbox input.

### `walkToGrandExchange`
- **Signature**: `public static boolean walkToGrandExchange()`
- **Description**: Walks to the Grand Exchange location.
