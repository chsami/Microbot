package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

/**
 * Abstract base class for all resource-based conditions.
 * Provides common functionality for tracking items, materials and other resources.
 */
@Slf4j
public abstract class ResourceCondition implements Condition {
    @Getter
    protected final Pattern itemPattern;  
    public ResourceCondition() {
        this.itemPattern = null;
    }  
    public ResourceCondition(String itemPatternString) {
        this.itemPattern = createItemPattern(itemPatternString);
    }
    public final String getItemPatternString() {
        return itemPattern == null ? null : itemPattern.pattern().toString();
    }
    /**
     * Returns the condition type for resource conditions
     */
    @Override
    public ConditionType getType() {
        return ConditionType.RESOURCE;
    }
    
    /**
     * Default implementation for detailed description - subclasses should override
     */
    @Override
    public String getDetailedDescription() {
        return "Resource Condition: " + getDescription();
    }
    
    /**
     * Default implementation for calculating progress percentage
     * Subclasses should override for more specific calculations
     */
    @Override
    public double getProgressPercentage() {
        return isSatisfied() ? 100.0 : 0.0;
    }
    
    /**
     * Checks if an item is in noted form
     * @param itemId The item ID to check
     * @return true if the item is noted, false otherwise
     */
    public static boolean isNoted(int itemId) {
        try {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
                
                int linkedId = itemComposition.getLinkedNoteId();
                if (linkedId <= 0) {
                    return false;
                }
                ItemComposition linkedItemComposition = Microbot.getItemManager().getItemComposition(linkedId);
                boolean isNoted = itemComposition.getNote() == 799;
                             
                boolean isNoteable = isNoteable(itemId);
                        
                return isNoted && !isNoteable;
            }).orElse(null);
        } catch (Exception e) {
            log.error("Error checking if item is noted, itemId: {}, error: {}", itemId, e.getMessage());
            return false;
        }
    }
    
    /**
     * Checks if an item can be noted
     * @param itemId The item ID to check
     * @return true if the item can be noted, false otherwise
     */
    public static boolean isNoteable(int itemId) {
        if (itemId < 0) {
            return false;
        }
        try {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> {
                ItemComposition itemComposition = Microbot.getItemManager().getItemComposition(itemId);
                int linkedId = itemComposition.getLinkedNoteId();
                if (linkedId <= 0) {
                    return false;
                }      
                     
                ItemComposition linkedItemComposition = Microbot.getItemManager().getItemComposition(linkedId);
                
                boolean unlinkedIsNoted = itemComposition.getNote() == 799;       
                return !unlinkedIsNoted;
            }).orElse(false);
        } catch (Exception e) {
            log.error("Error checking if item is noteable, itemId: {}, error: {}", itemId, e.getMessage());
            return false;
        }
    }

    /**
     * Gets a list of noted items from the inventory
     * @return List of noted item models
     */
    public static List<Rs2ItemModel> getNotedItems() {
       return Rs2Inventory.all().stream()
           .filter(item -> item.isNoted() && item.isStackable())
           .collect(Collectors.toList());
    }
    
    /**
     * Gets a list of un-noted items from the inventory that could be noted
     * @return List of un-noted item models
     */
    public static List<Rs2ItemModel> getUnNotedItems() {
        return Rs2Inventory.all().stream()
            .filter(item -> (!item.isNoted()) )
            .collect(Collectors.toList());
    }
    
    /**
     * Checks if an item model represents a noted item
     * @param notedItem The item model to check
     * @return true if the item is noted, false otherwise
     */
    public static boolean isNoted(Rs2ItemModel notedItem) {
        return notedItem != null && isNoted(notedItem.getId());
    }
    
    /**
     * Checks if an item model represents an un-noted item
     * @param item The item model to check
     * @return true if the item is un-noted, false otherwise
     */
    public static boolean isUnNoted(Rs2ItemModel item) {
        if (item == null) {
            return false;
        }
        return !isNoted(item.getId());
    }
    
    /**
     * Normalizes an item name for comparison (lowercase and trim)
     * @param name The item name to normalize
     * @return The normalized item name
     */
    protected String normalizeItemName(String name) {
        if (name == null) return "";
        return name.toLowerCase().trim();
    }

    /**
     * Creates a pattern for matching item names
     * @param itemName The item name pattern to match
     * @return A compiled regex pattern for matching the item name
     */
    protected Pattern createItemPattern(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return Pattern.compile(".*", Pattern.CASE_INSENSITIVE);
        }
        
        // Check if the name is already a regex pattern
        if (itemName.startsWith("^") || itemName.endsWith("$") || 
            itemName.contains(".*") || itemName.contains("[") || 
            itemName.contains("(")) {
            return Pattern.compile(itemName,Pattern.CASE_INSENSITIVE);
        }
        
        // Otherwise, create a contains pattern
        return Pattern.compile(".*" + Pattern.quote(itemName) + ".*", Pattern.CASE_INSENSITIVE);
    }
    /**
     * Extracts a clean, readable item name from the item's regex pattern.
     * 
     * This method processes the pattern string to remove special regex characters:
     * - For "contains" patterns (wrapped in ".*"), the wrapping characters are removed
     * - For patterns created with Pattern.quote(), the \Q and \E escape sequences are removed
     * 
     * @return A cleaned string representation of the item name suitable for display
     */
    public String getItemName() {
        // Extract a clean item name from the pattern for display
        String patternStr = itemPattern.pattern();
        // If it's a "contains" pattern (created with .*pattern.*)
        if (patternStr.startsWith(".*") && patternStr.endsWith(".*")) {
            patternStr = patternStr.substring(2, patternStr.length() - 2);
        }
        // Handle patterns that were created with Pattern.quote() which escapes special characters
        if (patternStr.startsWith("\\Q") && patternStr.endsWith("\\E")) {
            patternStr = patternStr.substring(2, patternStr.length() - 2);
        }
        return patternStr;
    }
    
    
    /**
     * Class for tracking resource-related events and their metadata.
     * Used to record when items are spawned, despawned, or inventory changes occur.
     */
    protected static class ItemTrackingEvent {
        public final long timestamp;
        public final WorldPoint location;
        public final String itemName;
        public final int itemId;
        public final int quantity;
        public final boolean isPlayerOwned;
        public final EventType eventType;
        
        /**
         * Defines the different types of item events that can be tracked.
         */
        public enum EventType {
            ITEM_SPAWNED,
            ITEM_DESPAWNED,
            INVENTORY_CHANGED
        }
        
        /**
         * Creates a new item tracking event with the specified parameters.
         * 
         * @param timestamp The time when the event occurred
         * @param location The world location where the event occurred
         * @param itemName The name of the item involved
         * @param itemId The ID of the item involved
         * @param quantity The quantity of items involved
         * @param isPlayerOwned Whether the item is owned by the player
         * @param eventType The type of event (spawn, despawn, inventory change)
         */
        public ItemTrackingEvent(long timestamp, WorldPoint location, String itemName, 
                               int itemId, int quantity, boolean isPlayerOwned, EventType eventType) {
            this.timestamp = timestamp;
            this.location = location;
            this.itemName = itemName;
            this.itemId = itemId;
            this.quantity = quantity;
            this.isPlayerOwned = isPlayerOwned;
            this.eventType = eventType;
        }
    }
    
    /**
     * Queue of item events waiting to be processed at the end of a game tick.
     * Events are accumulated during the tick and processed together for efficiency.
     */
    protected final List<ItemTrackingEvent> pendingEvents = new ArrayList<>();
    
    /**
     * Map tracking recently dropped items by the player, keyed by world location.
     * Values are timestamps when the items were dropped, used to identify player actions.
     */
    protected final Map<WorldPoint, Long> playerDroppedItems = new HashMap<>();
    
    /**
     * The player's last known position in the game world.
     * Used for determining if items appearing nearby were likely dropped by the player.
     */
    protected WorldPoint lastPlayerPosition = null;
    
    /**
     * Determines if an item spawned at a location was likely dropped by the player.
     * This is estimated based on proximity to the player's last known position and
     * previously tracked player-dropped items.
     *
     * @param location The world location where the item appeared
     * @param timestamp When the item appeared (millisecond timestamp)
     * @return true if the item was likely dropped by the player, false otherwise
     */
    protected boolean isLikelyPlayerDroppedItem(WorldPoint location, long timestamp) {
        // Check if this is near the player's last position
        if (lastPlayerPosition != null) {
            int distance = location.distanceTo2D(lastPlayerPosition);
            // Items dropped by players typically appear at their location or 1 tile away
            if (distance <= 1) {
                // Track this as a likely player-dropped item
                playerDroppedItems.put(location, timestamp);
                return true;
            }
        }
        return playerDroppedItems.containsKey(location);
    }
    
    /**
     * Processes all pending item tracking events that accumulated during the game tick.
     * This method should be called at the end of each game tick to update resource tracking.
     * Base implementation only clears events; subclasses should override with specific logic.
     */
    protected void processPendingEvents() {
        // Default implementation just clears events
        // Subclasses should override with specific implementation
        pendingEvents.clear();
        
        // Clean up old entries from player dropped items map (older than 10 seconds)
        long now = System.currentTimeMillis();
        playerDroppedItems.entrySet().removeIf(entry -> now - entry.getValue() > 10000);
    }
    
    /**
     * Updates the player's current position for use in dropped item tracking.
     * Called each game tick to maintain accurate position information.
     */
    protected void updatePlayerPosition() {
        if (Microbot.getClient() != null && Microbot.getClient().getLocalPlayer() != null) {
            lastPlayerPosition = Microbot.getClient().getLocalPlayer().getWorldLocation();
        }
    }
    
    /**
     * Handles the GameTick event from RuneLite's event system.
     * Updates player position and processes any accumulated item events.
     *
     * @param event The game tick event object
     */
    @Override
    public void onGameTick(GameTick event) {
        // Update player position
        updatePlayerPosition();
        
        // Process any pending events
        processPendingEvents();
    }
}