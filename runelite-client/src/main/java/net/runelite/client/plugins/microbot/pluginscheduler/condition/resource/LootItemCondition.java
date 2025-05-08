package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.InventoryID;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.GroundObjectDespawned;
import net.runelite.api.events.GroundObjectSpawned;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.models.RS2Item;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

import static net.runelite.api.TileItem.OWNERSHIP_SELF;

import static net.runelite.api.TileItem.OWNERSHIP_GROUP;
import static net.runelite.api.TileItem.OWNERSHIP_OTHER;
import static net.runelite.api.TileItem.OWNERSHIP_NONE;

/**
 * Condition that tracks a specific looted item quantity.
 * Focuses on tracking a single item for clarity and precision.
 */
@Getter
@Slf4j
public class LootItemCondition extends ResourceCondition {    
    
    public static String getVersion() {
        return "0.0.1";
    }
    private final int targetAmountMin;
    private final int targetAmountMax;
    private final boolean includeNoted;
    private final boolean includeNoneOwner;
    private final boolean ignorePlayerDropped;
    
    private transient int currentTargetAmount;
    private transient int currentTrackedCount;
    private transient int lastInventoryCount;
    

    private final Map<WorldPoint, Integer> trackedItemQuantities = new HashMap<>();
    
    // Keep track of recently looted items to avoid double counting
    private final Map<WorldPoint, Long> recentlyLootedItems = new HashMap<>();
    
    // Key is a composite key of location and item ID to track multiple items at the same spot
    private static class TrackedItem {
        public final WorldPoint location;
        public final int itemId;
        public final int quantity;
        public final long timestamp;
        public final String itemName;
        
        public TrackedItem(WorldPoint location, int itemId, int quantity, String itemName) {
            this.location = location;
            this.itemId = itemId;
            this.quantity = quantity;
            this.timestamp = System.currentTimeMillis();
            this.itemName = itemName;
        }
        
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            TrackedItem that = (TrackedItem) o;
            return itemId == that.itemId && 
                   location.equals(that.location);
        }
        
        @Override
        public int hashCode() {
            return Objects.hash(location, itemId);
        }
    }
    
    // Replace the existing tracking collections with new ones
    private final Set<TrackedItem> trackedItems = new HashSet<>();
    private final Map<WorldPoint, List<TrackedItem>> itemsByLocation = new HashMap<>();

    public LootItemCondition(String itemName, int targetAmount, boolean includeNoted) {
        super(itemName);

        this.targetAmountMin = targetAmount;
        this.targetAmountMax = targetAmount;        
        this.includeNoted = includeNoted;        
        this.currentTargetAmount = Rs2Random.between(targetAmountMin, targetAmountMax);
        this.currentTrackedCount = 0;
        this.lastInventoryCount = 0;
        this.includeNoneOwner = false;
        this.isIncludeNoneOwner();
        this.ignorePlayerDropped = false;
    }
    
    @Builder
    public LootItemCondition(String itemName, int targetAmountMin, 
                            int targetAmountMax, 
                            boolean includeNoted, 
                            boolean includeNoneOwner,
                            boolean ignorePlayerDropped) {
        super(itemName);        
        this.targetAmountMin = targetAmountMin;
        this.targetAmountMax = targetAmountMax;                
        this.currentTargetAmount = Rs2Random.between(targetAmountMin, targetAmountMax);
        this.currentTrackedCount = 0;
        this.lastInventoryCount = 0;
        this.includeNoted = includeNoted;
        this.includeNoneOwner = includeNoneOwner;
        this.ignorePlayerDropped = ignorePlayerDropped;
         // Initialize with existing items on the ground
         scanForExistingItems();
    }
    
    /**
     * Creates a condition with randomized target between min and max
     */
    public static LootItemCondition createRandomized(String itemName, int minAmount, int maxAmount, boolean includeNoted, boolean includeNoneOwner) {   
        return LootItemCondition.builder()
                .itemName(itemName)
                .targetAmountMin(minAmount)
                .targetAmountMax(maxAmount)
                .includeNoted(includeNoted)
                .includeNoneOwner(includeNoneOwner)
                .build();
    }


    /**
     * Creates an AND logical condition requiring multiple looted items with individual targets
     */
    public static LogicalCondition createAndCondition(List<String> itemNames, List<Integer> targetAmountsMins, List<Integer> targetAmountsMaxs,boolean includeNoted, boolean includeNoneOwner) {
        // Validate input
        if (itemNames == null || itemNames.isEmpty()) {
            throw new IllegalArgumentException("Item name list cannot be null or empty");
        }
        
        // Determine the smallest list size for safe iteration
        int minSize = Math.min(itemNames.size(), 
                      Math.min(targetAmountsMins != null ? targetAmountsMins.size() : 0,
                               targetAmountsMaxs != null ? targetAmountsMaxs.size() : 0));
        
        // If target amounts not provided or empty, default to single item each
        if (targetAmountsMins == null || targetAmountsMins.isEmpty()) {
            targetAmountsMins = new ArrayList<>(itemNames.size());
            for (int i = 0; i < itemNames.size(); i++) {
                targetAmountsMins.add(1);
            }
        }
        
        if (targetAmountsMaxs == null || targetAmountsMaxs.isEmpty()) {
            targetAmountsMaxs = new ArrayList<>(targetAmountsMins);
        }
        
        // Create the logical condition
        AndCondition andCondition = new AndCondition();
        
        // Add a condition for each item
        for (int i = 0; i < minSize; i++) {
            LootItemCondition itemCondition = LootItemCondition.builder()
                    .itemName(itemNames.get(i))
                    .targetAmountMin(targetAmountsMins.get(i))
                    .targetAmountMax(targetAmountsMaxs.get(i))
                    .includeNoted(includeNoted)
                    .includeNoneOwner(includeNoneOwner)
                    .build();
                    
            andCondition.addCondition(itemCondition);
        }
        
        return andCondition;
    }

    /**
     * Creates an OR logical condition requiring multiple looted items with individual targets
     */
    public static LogicalCondition createOrCondition(List<String> itemNames, 
                        List<Integer> targetAmountsMins, List<Integer> targetAmountsMaxs,
                         boolean includeNoted, boolean includeNoneOwner) {
        if (itemNames == null || itemNames.isEmpty()) {
            throw new IllegalArgumentException("Item name list cannot be null or empty");
        }
        
        // Determine the smallest list size for safe iteration
        int minSize = Math.min(itemNames.size(), 
                      Math.min(targetAmountsMins != null ? targetAmountsMins.size() : 0,
                               targetAmountsMaxs != null ? targetAmountsMaxs.size() : 0));
        
        // If target amounts not provided or empty, default to single item each
        if (targetAmountsMins == null || targetAmountsMins.isEmpty()) {
            targetAmountsMins = new ArrayList<>(itemNames.size());
            for (int i = 0; i < itemNames.size(); i++) {
                targetAmountsMins.add(1);
            }
        }
        
        if (targetAmountsMaxs == null || targetAmountsMaxs.isEmpty()) {
            targetAmountsMaxs = new ArrayList<>(targetAmountsMins);
        }
        
        // Create the logical condition
        OrCondition orCondition = new OrCondition();
        
        // Add a condition for each item
        for (int i = 0; i < minSize; i++) {
            LootItemCondition itemCondition = LootItemCondition.builder()
                    .itemName(itemNames.get(i))
                    .targetAmountMin(targetAmountsMins.get(i))
                    .targetAmountMax(targetAmountsMaxs.get(i))
                    .includeNoted(includeNoted)
                    .includeNoneOwner(includeNoneOwner)
                    .build();
                    
            orCondition.addCondition(itemCondition);
        }
        
        return orCondition;
    }

    /**
     * Creates an AND logical condition requiring multiple looted items with the same target for all
     */
    public static LogicalCondition createAndCondition(List<String> itemNames, int targetAmountMin, int targetAmountMax,boolean includeNoted, boolean includeNoneOwner) {
        if (itemNames == null || itemNames.isEmpty()) {
            throw new IllegalArgumentException("Item name list cannot be null or empty");
        }
        
        // Create the logical condition
        AndCondition andCondition = new AndCondition();
        
        // Add a condition for each item with the same targets
        for (String itemName : itemNames) {
            LootItemCondition itemCondition = LootItemCondition.builder()
                    .itemName(itemName)
                    .targetAmountMin(targetAmountMin)
                    .targetAmountMax(targetAmountMax)
                    .includeNoted(includeNoted)
                    .includeNoneOwner(includeNoneOwner)
                    .build();
                    
            andCondition.addCondition(itemCondition);
        }
        
        return andCondition;
    }

    /**
     * Creates an OR logical condition requiring multiple looted items with the same target for all
     */
    public static LogicalCondition createOrCondition(List<String> itemNames, int targetAmountMin, int targetAmountMax,boolean includeNoted, boolean includeNoneOwner) {
        if (itemNames == null || itemNames.isEmpty()) {
            throw new IllegalArgumentException("Item name list cannot be null or empty");
        }
        
        // Create the logical condition
        OrCondition orCondition = new OrCondition();
        
        // Add a condition for each item with the same targets
        for (String itemName : itemNames) {
            LootItemCondition itemCondition = LootItemCondition.builder()
                    .itemName(itemName)
                    .targetAmountMin(targetAmountMin)
                    .targetAmountMax(targetAmountMax)
                    .includeNoted(includeNoted)
                    .includeNoneOwner(includeNoneOwner)
                    .build();
                    
            orCondition.addCondition(itemCondition);
        }
        
        return orCondition;
    }
   
    @Override
    public boolean isSatisfied() {        
        return currentTrackedCount >= currentTargetAmount;
    }

    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        String noteState = includeNoted ? " (including noted)" : "";
        String ownerState = includeNoneOwner ? " (any owner)" : " (player owned)";
        String randomRangeInfo = "";
        
        if (targetAmountMin != targetAmountMax) {
            randomRangeInfo = String.format(" (randomized from %d-%d)", targetAmountMin, targetAmountMax);
        }
        
        sb.append(String.format("Loot %d %s%s%s", 
                currentTargetAmount,                
                noteState,
                ownerState,
                randomRangeInfo));
        
        // Add progress tracking
        sb.append(String.format(" (%d/%d, %.1f%%)", 
                currentTrackedCount,
                currentTargetAmount,
                getProgressPercentage()));
                
        return sb.toString();
    }
    
    /**
     * Returns a detailed description of the loot item condition with additional status information
     */
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        
        // Basic description
        sb.append(String.format("Loot %d %s", currentTargetAmount, getItemPattern().pattern()));
        
        // Add randomization info if applicable
        if (targetAmountMin != targetAmountMax) {
            sb.append(String.format(" (randomized from %d-%d)", targetAmountMin, targetAmountMax));
        }
        
        sb.append("\n");
        
        // Status information
        sb.append("Status: ").append(isSatisfied() ? "Satisfied" : "Not satisfied").append("\n");
        sb.append("Progress: ").append(String.format("%d/%d (%.1f%%)", 
                currentTrackedCount, 
                currentTargetAmount,
                getProgressPercentage())).append("\n");
        
        // Configuration information
        sb.append("Item Pattern: ").append(itemPattern.pattern()).append("\n");
        sb.append("Include Noted Items: ").append(includeNoted ? "Yes" : "No").append("\n");
        sb.append("Include Items from Other Players: ").append(includeNoneOwner ? "Yes" : "No").append("\n");
        
        // Tracking information
        sb.append("Currently Tracking: ").append(itemsByLocation.size()).append(" ground locations\n");
        sb.append("Current Inventory Count: ").append(lastInventoryCount);
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        String itemName = getItemName();
        // Basic information
        sb.append("LootItemCondition:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        sb.append("  │ Item: ").append(itemName != null && !itemName.isEmpty() ? itemName : "Any").append("\n");
        
        if (itemPattern != null && !itemPattern.pattern().equals(".*")) {
            sb.append("  │ Pattern: ").append(itemPattern.pattern()).append("\n");
        }
        
        sb.append("  │ Target Amount: ").append(currentTargetAmount).append("\n");
        sb.append("  │ Include Noted: ").append(includeNoted ? "Yes" : "No").append("\n");
        sb.append("  │ Track Non-Owned: ").append(includeNoneOwner ? "Yes" : "No").append("\n");
        
        // Randomization
        sb.append("  ├─ Randomization ────────────────────────────\n");
        boolean hasRandomization = targetAmountMin != targetAmountMax;
        sb.append("  │ Randomization: ").append(hasRandomization ? "Enabled" : "Disabled").append("\n");
        if (hasRandomization) {
            sb.append("  │ Target Range: ").append(targetAmountMin).append("-").append(targetAmountMax).append("\n");
        }
        
        // Status information
        sb.append("  ├─ Status ──────────────────────────────────\n");
        sb.append("  │ Satisfied: ").append(isSatisfied()).append("\n");
        sb.append("  │ Current Count: ").append(currentTrackedCount).append("\n");
        sb.append("  │ Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
        
        // Tracking info
        sb.append("  └─ Tracking ────────────────────────────────\n");
        sb.append("    Ground Locations: ").append(itemsByLocation.size()).append("\n");
        sb.append("    Inventory Count: ").append(lastInventoryCount).append("\n");
        sb.append("    Recent Loots: ").append(recentlyLootedItems.size());
        
        return sb.toString();
    }

    @Override
    public void reset() {
        reset(false);
    }

    @Override
    public void reset(boolean randomize) {
        if (randomize) {
            this.currentTargetAmount = Rs2Random.between(targetAmountMin, targetAmountMax);
        }
        this.currentTrackedCount = 0;
        this.lastInventoryCount = getCurrentInventoryCount();
        this.trackedItems.clear();
        this.itemsByLocation.clear();
        this.recentlyLootedItems.clear();
        // Re-scan for items after reset
        scanForExistingItems();
    }
    
    @Override
    public double getProgressPercentage() {
        if (currentTargetAmount <= 0) {
            return 100.0;
        }
        
        double ratio = (double) currentTrackedCount / currentTargetAmount;
        return Math.min(100.0, ratio * 100.0);
    }    

        /**
     * Called when an item spawns on the ground - we check if it matches our target item
     */
    @Override
    public void onItemSpawned(ItemSpawned event) {
        TileItem tileItem = event.getItem();
        WorldPoint location = event.getTile().getWorldLocation();

        Client client = Microbot.getClient();
        if (client == null) {
            return;
        }

        boolean isPlayerOwned = tileItem.getOwnership() == OWNERSHIP_SELF || 
                        tileItem.getOwnership() == OWNERSHIP_GROUP ||
                        (includeNoneOwner && 
                        (tileItem.getOwnership() == OWNERSHIP_NONE || 
                            tileItem.getOwnership() == OWNERSHIP_OTHER));
                            
        if (isPlayerOwned) {
            // Get the item name
            String spawnedItemName = Microbot.getClientThread().runOnClientThreadOptional(() -> 
                Microbot.getItemManager().getItemComposition(tileItem.getId()).getName()
            ).orElse("");
            
            // Check if this item was likely dropped by the player
            boolean playerDropped = isLikelyPlayerDroppedItem(location, System.currentTimeMillis());
            
            // Add to pending events
            if (!playerDropped || !ignorePlayerDropped) {
                if (itemPattern.matcher(spawnedItemName).matches()) {
                    pendingEvents.add(new ItemTrackingEvent(
                        System.currentTimeMillis(),
                        location,
                        spawnedItemName,
                        tileItem.getId(),
                        tileItem.getQuantity(),
                        isPlayerOwned,
                        ItemTrackingEvent.EventType.ITEM_SPAWNED
                    ));
                }
            }
        }
    }
 
    /**
     * Called when an item despawns from the ground - check if it was one of our tracked items
     */
    @Override
    public void onItemDespawned(ItemDespawned event) {
        WorldPoint location = event.getTile().getWorldLocation();
        TileItem tileItem = event.getItem();
        
        // Get the item name
        String despawnedItemName = Microbot.getClientThread().runOnClientThreadOptional(() -> 
            Microbot.getItemManager().getItemComposition(tileItem.getId()).getName()
        ).orElse("");
        boolean isPlayerOwned = tileItem.getOwnership() == OWNERSHIP_SELF || 
                        tileItem.getOwnership() == OWNERSHIP_GROUP ||
                        (includeNoneOwner && 
                        (tileItem.getOwnership() == OWNERSHIP_NONE || 
                            tileItem.getOwnership() == OWNERSHIP_OTHER));
                            
        // Check if we have any tracked items at this location 
        if (itemsByLocation.containsKey(location)) {
            // Find the specific item that despawned
            List<TrackedItem> itemsAtLocation = itemsByLocation.get(location);
            
            // Look for a matching item at this location
            for (TrackedItem trackedItem : itemsAtLocation) {
                if (trackedItem.itemId == tileItem.getId() && 
                    itemPattern.matcher(despawnedItemName).matches()) {
                    
                    if(Microbot.isDebug()) {
                        log.info("Item despawned that we were tracking: {} x{} at {}", 
                            despawnedItemName, trackedItem.quantity, location);
                    }
                    
                    pendingEvents.add(new ItemTrackingEvent(
                        System.currentTimeMillis(),
                        location,
                        despawnedItemName,
                        tileItem.getId(),
                        trackedItem.quantity,
                        isPlayerOwned, // We know it's tracked, so it must be player-owned-> it should be playerowned
                        ItemTrackingEvent.EventType.ITEM_DESPAWNED
                    ));
                    
                    break; // Found the specific item
                }
            }
        }
    }
    @Override
    public void onGroundObjectSpawned(GroundObjectSpawned event) {
        // Not used for this implementation
    }
    
    @Override
    public void onGroundObjectDespawned(GroundObjectDespawned event) {
        // Not used for this implementation
    }
    
     /**
     * Called when item containers change (inventory, bank, etc.)
     * We need to check if we gained any of our target items.
     */
    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        // Only interested in inventory changes
        if (event.getContainerId() != InventoryID.INVENTORY.getId()) {
            return;
        }
        
        int currentCount = getCurrentInventoryCount();
        
        // Add to pending events if inventory count changed
        if (currentCount != lastInventoryCount) {
            pendingEvents.add(new ItemTrackingEvent(
                System.currentTimeMillis(),
                null, // No specific location for inventory changes
                getItemName(),
                -1, // No specific ID for general inventory changes
                Math.abs(currentCount - lastInventoryCount),
                true, // Always player owned
                ItemTrackingEvent.EventType.INVENTORY_CHANGED
            ));
            
            // Update the last count right away to detect further changes
            lastInventoryCount = currentCount;
        }
    }
    
     /**
     * Process all pending tracking events at the end of a game tick
     */
    @Override
    protected void processPendingEvents() {
        // Sort events by timestamp to process in the correct sequence
        pendingEvents.sort(Comparator.comparing(e -> e.timestamp));
        
        // Track inventory changes and despawned items in this tick
        boolean hadInventoryIncrease = false;
        int inventoryGained = 0;
        int totalDespawnedQuantity = 0;
        List<ItemTrackingEvent> despawnedItems = new ArrayList<>();
        
        // First pass - collect information
        for (ItemTrackingEvent event : pendingEvents) {
            switch (event.eventType) {
                case ITEM_SPAWNED:
                    // Track this new item
                    if (event.isPlayerOwned && itemPattern.matcher(event.itemName).matches()) {
                        // Create a new tracked item with unique instance ID (timestamp-based)
                        TrackedItem newItem = new TrackedItem(
                            event.location, 
                            event.itemId, 
                            event.quantity,
                            event.itemName
                        );
                        
                        // Add to our tracked sets
                        trackedItems.add(newItem);
                        
                        // Add to location mapping
                        itemsByLocation.computeIfAbsent(event.location, k -> new ArrayList<>())
                            .add(newItem);
                            
                        if (Microbot.isDebug()) {
                            log.info("Tracking new item: {} x{} at {} (id: {})", 
                                event.itemName, event.quantity, event.location, newItem.timestamp);
                        }
                    }
                    break;
                    
                case ITEM_DESPAWNED:
                    // Save despawned events for correlation
                    despawnedItems.add(event);
                    
                    // Find and remove the tracked item
                    if (itemsByLocation.containsKey(event.location)) {
                        List<TrackedItem> itemsAtLocation = itemsByLocation.get(event.location);
                        
                        // Find first matching item by ID for removal - ONLY REMOVE ONE INSTANCE
                        boolean itemRemoved = false;
                        TrackedItem itemToRemove = null;
                        
                        // Find the matching item to remove
                        for (TrackedItem item : itemsAtLocation) {
                            if (item.itemId == event.itemId && !itemRemoved) {
                                itemToRemove = item;
                                totalDespawnedQuantity += item.quantity;
                                itemRemoved = true;
                                break;
                            }
                        }
                        
                        // Remove the specific item
                        if (itemToRemove != null) {
                            itemsAtLocation.remove(itemToRemove);
                            trackedItems.remove(itemToRemove);
                            
                            if (Microbot.isDebug()) {
                                log.info("Item despawned: {} x{} at {} (id: {})", 
                                    event.itemName, itemToRemove.quantity, event.location, itemToRemove.timestamp);
                            }
                        }
                        
                        // Remove the location entry if no more items there
                        if (itemsAtLocation.isEmpty()) {
                            itemsByLocation.remove(event.location);
                        }
                        
                        // Record that we just looted from this location
                        recentlyLootedItems.put(event.location, System.currentTimeMillis());
                    }
                    break;
                    
                case INVENTORY_CHANGED:
                    // Only consider increases for looting
                    if (event.quantity > 0) {
                        hadInventoryIncrease = true;
                        inventoryGained += event.quantity;
                    }
                    break;
            }
        }
        
        // Second pass - correlate despawns with inventory increases
        if (hadInventoryIncrease && !despawnedItems.isEmpty()) {
            // We had both despawns and inventory increases, likely a loot event
            if (Microbot.isDebug()) {
                log.info("Correlated loot event: gained {} items after {} despawns totaling {} quantity", 
                    inventoryGained, despawnedItems.size(), totalDespawnedQuantity);
            }
            
            // If we have enough evidence that items were looted
            if (totalDespawnedQuantity > 0) {
                // If inventory gained matches exactly what was despawned, use that number
                // Otherwise, use the despawn quantity as it might be more accurate for stacked items
                int countToAdd = (inventoryGained == totalDespawnedQuantity) ? 
                    inventoryGained : totalDespawnedQuantity;
                
                // Count this as looted items
                currentTrackedCount += countToAdd;
                
                if (Microbot.isDebug()) {
                    log.info("Added {} to tracking count (now {})", countToAdd, currentTrackedCount);
                }
            } else {
                // Fallback to inventory changes if we can't correlate with despawns
                currentTrackedCount += inventoryGained;
            }
        }
        
        // Clean up old entries from recently looted map (older than 5 seconds)
        long now = System.currentTimeMillis();
        recentlyLootedItems.entrySet().removeIf(entry -> now - entry.getValue() > 5000);
        
        // Clear processed events
        pendingEvents.clear();
    }
    
    /**
     * Check inventory every game tick to catch changes that might not trigger ItemContainerChanged
     */
    @Override
    public void onGameTick(GameTick gameTick) {
        // Update player position for dropped item tracking
        updatePlayerPosition();
        
        // Process any pending events
        processPendingEvents();
        
        // Also do a final inventory check
        int currentCount = getCurrentInventoryCount();
        if (currentCount != lastInventoryCount) {            
            if (currentCount > lastInventoryCount) {
                int gained = currentCount - lastInventoryCount;
                if (Microbot.isDebug()) {
                    log.info("Game tick detected uncaught inventory increase of {} {}", gained, getItemName());
                }
                
                // Count as looted if we're tracking items on the ground
                currentTrackedCount += gained;
            }
            
            lastInventoryCount = currentCount;
        }
    }
    
    /**
     * Gets the current count of this item in the inventory
     */
    private int getCurrentInventoryCount() {
               
        int currentItemCountNoted = getNotedItems().stream().filter(item -> {
            if (item == null) {
                return false;
            }
            
            return itemPattern.matcher(item.getName()).matches();             
        }).mapToInt(Rs2ItemModel::getQuantity).sum();
        
        int currentItemCountUnNoted = getUnNotedItems().stream().filter(item -> {
            if (item == null) {
                return false;
            }            
            return itemPattern.matcher(item.getName()).matches();             
        }).mapToInt(Rs2ItemModel::getQuantity).sum();
             
        
        if (includeNoted) {
            return currentItemCountNoted + currentItemCountUnNoted;
        }
        return currentItemCountUnNoted;
    }
    @Override
    public void onGameStateChanged(GameStateChanged gameStateChanged) {
        // Reset the condition if we log out or change worlds
        if (gameStateChanged.getGameState() == GameState.LOGGED_IN ){
            scanForExistingItems();
        }
    }
    /**
     * Scans for existing ground items that match our criteria and adds them to tracking
     */
    private void scanForExistingItems() {
        // Scan a generous range (maximum view distance)
        int scanRange = 32;
        if (Microbot.getClient() == null || !Microbot.isLoggedIn()) {
            return;
        }
        // Get all items on the ground
        RS2Item[] groundItems = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Rs2GroundItem.getAll(scanRange)
        ).orElse(new RS2Item[] {});
        
        if (Microbot.isDebug()) {
            log.info("Scanning for existing ground items - found {} total items", groundItems.length);
        }
        
        // Filter and track matching items
        for (RS2Item rs2Item : groundItems) {
            if (rs2Item == null) continue;
            
            // Check if this item matches our criteria
            boolean isPlayerOwned = rs2Item.getTileItem().getOwnership() == OWNERSHIP_SELF || 
                    rs2Item.getTileItem().getOwnership() == OWNERSHIP_GROUP ||
                    (includeNoneOwner && 
                    (rs2Item.getTileItem().getOwnership() == OWNERSHIP_NONE || 
                        rs2Item.getTileItem().getOwnership() == OWNERSHIP_OTHER));
            
            if (isPlayerOwned && itemPattern.matcher(rs2Item.getItem().getName()).matches()) {
                WorldPoint location = rs2Item.getTile().getWorldLocation();
                
                // Check if this item was likely dropped by the player
                boolean playerDropped = isLikelyPlayerDroppedItem(location, System.currentTimeMillis());
                
                // Only track if not player-dropped or we're including player-dropped items
                if (!playerDropped || !ignorePlayerDropped) {
                    // Create a tracked item
                    TrackedItem newItem = new TrackedItem(
                        location, 
                        rs2Item.getItem().getId(), 
                        rs2Item.getTileItem().getQuantity(),
                        rs2Item.getItem().getName()
                    );
                    
                    // Add to our tracking collections
                    trackedItems.add(newItem);
                    
                    // Add to location mapping
                    itemsByLocation.computeIfAbsent(location, k -> new ArrayList<>())
                        .add(newItem);
                        
                    if (Microbot.isDebug()) {
                        log.info("Found existing item to track: {} x{} at {}", 
                            rs2Item.getItem().getName(), 
                            rs2Item.getTileItem().getQuantity(), 
                            location);
                    }
                }
            }
        }
        
        if (Microbot.isDebug()) {
            log.info("Now tracking {} items at {} locations after initial scan", 
                trackedItems.size(), itemsByLocation.size());
        }
    }

}