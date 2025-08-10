package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import lombok.EqualsAndHashCode;
import net.runelite.api.Constants;
import net.runelite.api.GameState;
import net.runelite.api.GrandExchangeOffer;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesAnalysis;
import net.runelite.client.plugins.microbot.util.grandexchange.models.GrandExchangeOfferDetails;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.shop.models.Rs2ShopItem;
import net.runelite.client.plugins.microbot.util.shop.models.Rs2ShopType;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.*;
import java.util.concurrent.ScheduledExecutorService;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.slf4j.event.Level;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Represents a requirement to buy or sell multiple items from/to the same shop.
 * This extends Requirement directly with additional properties specific to shop operations.
 * Enhanced with BanksShopper patterns for world hopping, stock tracking, and quantity management.
 * 
 * Supports batch operations for multiple items at the same shop location to optimize efficiency.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class ShopRequirement extends Requirement {
    
    // Pattern to detect charged items with numbers in parentheses, e.g., "Amulet of glory(6)"
    protected static final Pattern CHARGED_ITEM_PATTERN = Pattern.compile(".*\\((\\d+)\\)$");
    
    /**
     * Map of shop items to their individual requirements and settings.
     * All items must be from the same shop (same location and NPC).
     */
    private final Map<Rs2ShopItem, ShopItemRequirement> shopItemRequirements;
    
    /**
     * The primary shop information (location, NPC, type) - derived from the first shop item.
     * All shop items must match this shop's location and NPC.
     */
    private final Rs2ShopItem primaryShopItem;
    
    /**
     * The shop operation type - either BUY or SELL.
     * All items in this requirement must use the same operation.
     */
    private final ShopOperation operation;
    
    /**
     * Whether to handle noted items when selling (unnote them if needed).
     */
    @Setter
    private boolean handleNotedItems = true;
    
    /**
     * Whether to use next world progression or random world selection.
     */
    @Setter
    private boolean useNextWorld = false;
    
    /**
     * Whether to enable automatic world hopping when stock is low.
     */
    @Setter
    private boolean enableWorldHopping = true;
    
    /**
     * Whether to bank purchased items automatically.
     */
    @Setter
    private boolean enableBanking = true;
    
    @Setter
    private int timeout = 60000; // Default timeout for shop operations
    
    /**
     * Configuration for world hopping behavior with exponential backoff and retry limits.
     */
    @Setter
    private WorldHoppingConfig worldHoppingConfig = WorldHoppingConfig.createDefault();
    
    
    public String getName() {
        if (shopItemRequirements.isEmpty()) {
            return "No Shop Items";
        }
        if (shopItemRequirements.size() == 1) {
            return shopItemRequirements.values().iterator().next().getItemName();
        }
        return shopItemRequirements.size() + " items from " + primaryShopItem.getShopNpcName();
    }
    
    /**
     * Returns a multi-line display string with detailed shop requirement information.
     * Uses StringBuilder with tabs for proper formatting.
     * 
     * @return A formatted string containing shop requirement details
     */
    public String displayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Enhanced Multi-Item Shop Requirement Details ===\n");
        sb.append("Name:\t\t\t").append(getName()).append("\n");
        sb.append("Type:\t\t\t").append(getRequirementType().name()).append("\n");
        sb.append("Priority:\t\t").append(getPriority().name()).append("\n");
        sb.append("Rating:\t\t\t").append(getRating()).append("/10\n");
        sb.append("Operation:\t\t").append(operation.name()).append("\n");
        sb.append("Total Items:\t\t").append(shopItemRequirements.size()).append("\n");
        sb.append("Item IDs:\t\t").append(getIds().toString()).append("\n");
        sb.append("Description:\t\t").append(getDescription() != null ? getDescription() : "No description").append("\n");
        
        // Enhanced shopping configuration
        sb.append("\n--- Shopping Configuration ---\n");
        sb.append("World Hopping:\t\t").append(enableWorldHopping ? "Enabled" : "Disabled").append("\n");
        sb.append("Use Next World:\t\t").append(useNextWorld ? "Yes" : "Random").append("\n");
        sb.append("Auto Banking:\t\t").append(enableBanking ? "Enabled" : "Disabled").append("\n");
        sb.append("Handle Noted:\t\t").append(handleNotedItems ? "Enabled" : "Disabled").append("\n");
        
        // Individual item details
        sb.append("\n--- Individual Item Requirements ---\n");
        for (ShopItemRequirement itemReq : shopItemRequirements.values()) {
            sb.append("Item: ").append(itemReq.getItemName()).append("\n");
            sb.append("  Amount: ").append(itemReq.getAmount()).append(" (completed: ").append(itemReq.getCompletedAmount()).append(")\n");
            sb.append("  Base Stock: ").append(itemReq.getBaseStock()).append(" items\n");
            sb.append("  Stock Tolerance: ").append(itemReq.getStockTolerance()).append(" items\n");
            sb.append("  Max Per Visit: ").append(itemReq.getMaxQuantityPerVisit()).append(" items\n");
            sb.append("  Status: ").append(itemReq.isCompleted() ? "COMPLETED" : "PENDING").append("\n");
        }
        
        if (primaryShopItem != null) {
            sb.append("\n--- Shop Source Information ---\n");
            sb.append(primaryShopItem.displayString());
        } else {
            sb.append("Shop Source:\t\tNot specified\n");
        }
        
        return sb.toString();
    }

    /**
     * Creates a new multi-item shop requirement with schedule context.
     * 
     * @param shopItems Map of shop items to their individual requirements
     * @param operation Shop operation type (BUY or SELL)
     * @param requirementType Where this item should be located (equipment slot, inventory, or either)
     * @param priority Priority level of this item for plugin functionality
     * @param rating Effectiveness rating from 1-10 (10 being most effective)
     * @param description Human-readable description of the item's purpose    
     * @param scheduleContext When this requirement should be fulfilled
     */
    public ShopRequirement(            
            Map<Rs2ShopItem, ShopItemRequirement> shopItems,
            ShopOperation operation,
            RequirementType requirementType,
            Priority priority,
            int rating,
            String description,
            ScheduleContext scheduleContext
           ) {
        
        super(requirementType, priority, rating, description, extractItemIds(shopItems), scheduleContext);        
        
        if (shopItems.isEmpty()) {
            throw new IllegalArgumentException("Shop items map cannot be empty");
        }
        
        this.shopItemRequirements = new HashMap<>(shopItems);
        this.operation = operation;
        this.primaryShopItem = shopItems.keySet().iterator().next();
        
        // Validate all items are from the same shop
        validateSameShop();
    }
    
    /**
     * Convenience constructor for single item requirement (backward compatibility).
     */
    public ShopRequirement(            
            Rs2ShopItem shopItem,
            int amount,
            ShopOperation operation,
            RequirementType requirementType,
            Priority priority,
            int rating,
            String description,
            ScheduleContext scheduleContext
           ) {
        this(createSingleItemMap(shopItem, amount), operation, requirementType, priority, rating, description, scheduleContext);
    }
    
    /**
     * Helper method to extract item IDs from shop items map.
     */
    private static List<Integer> extractItemIds(Map<Rs2ShopItem, ShopItemRequirement> shopItems) {
        return shopItems.keySet().stream()
                .map(Rs2ShopItem::getItemId)
                .collect(Collectors.toList());
    }
    
    /**
     * Helper method to create single item map for backward compatibility.
     */
    private static Map<Rs2ShopItem, ShopItemRequirement> createSingleItemMap(Rs2ShopItem shopItem, int amount) {
        Map<Rs2ShopItem, ShopItemRequirement> map = new HashMap<>();
        map.put(shopItem, new ShopItemRequirement(shopItem, amount, 10)); // Default stockTolerance=10
        return map;
    }
    
    /**
     * Validates that all shop items are from the same shop (same location and NPC).
     */
    private void validateSameShop() {
        String primaryShopNpc = primaryShopItem.getShopNpcName();
        Rs2ShopType primaryShopType = primaryShopItem.getShopType();
        
        for (Rs2ShopItem item : shopItemRequirements.keySet()) {
            if (!item.getShopNpcName().equals(primaryShopNpc) || 
                !item.getShopType().equals(primaryShopType) ||
                !item.getLocation().equals(primaryShopItem.getLocation())) {
                throw new IllegalArgumentException(
                    "All shop items must be from the same shop. Found mismatch: " +
                    item.getShopNpcName() + " vs " + primaryShopNpc
                );
            }
        }
    }
    
    /**
     * Gets the total number of items needed across all shop items.
     */
    public int getTotalAmount() {
        return shopItemRequirements.values().stream()
                .mapToInt(ShopItemRequirement::getAmount)
                .sum();
    }
    
    /**
     * Gets the total number of completed items across all shop items.
     */
    public int getTotalCompletedAmount() {
        return shopItemRequirements.values().stream()
                .mapToInt(ShopItemRequirement::getCompletedAmount)
                .sum();
    }
    
    /**
     * Checks if all items in this requirement are completed.
     */
    public boolean isAllItemsCompleted() {
        return shopItemRequirements.values().stream()
                .allMatch(ShopItemRequirement::isCompleted);
    }
   
    
    /**
     * Enhanced shopping method with BanksShopper patterns.
     * Includes world hopping, stock tracking, quantity management, and banking.
     * Now properly distinguishes between Grand Exchange and regular shop operations.
     * 
     * @return true if the purchase was successful, false otherwise
     */
    private boolean buyFromShop() {
        return buyFromShop("Purchasing " + getName() + " from " + primaryShopItem.getShopNpcName());
    }
    
    /**
     * Enhanced shopping method with custom status message.
     * Implements BanksShopper patterns for optimal shopping experience.
     * Handles both Grand Exchange and regular shop operations appropriately.
     * 
     * @param customBuyMessage Custom message to display during purchase
     * @return true if the purchase was successful, false otherwise
     */
    private boolean buyFromShop(String customBuyMessage) {
        if (primaryShopItem == null || shopItemRequirements.isEmpty() || true) {
            Microbot.log("No shop source specified for " + getName());
            return false;
        }
        
        try {
            Microbot.status = customBuyMessage;
            
            // Check if we already have enough of all items
            if (isAllItemsCompleted()) {
                Microbot.status = "Already have all required items";
                return true;
            }
            
            // Handle Grand Exchange differently from regular shops
            if (primaryShopItem.getShopType() == Rs2ShopType.GRAND_EXCHANGE) {
                return buyFromGrandExchange();
            } else {
                return buyFromRegularShop();
            }
            
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.buyFromShop", e);
            return false;
        }
    }
    
    /**
     * Gets the primary item ID for legacy compatibility.
     */
    private int getPrimaryItemId() {
        return primaryShopItem.getItemId();
    }
    
    /**
     * Checks if we have sufficient inventory or bank items for a specific shop item requirement.
     */
    private boolean hasSufficientItems(ShopItemRequirement itemReq) {
        int currentCount = Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId()) + 
                          Rs2Bank.count(itemReq.getShopItem().getItemId());
        return currentCount >= itemReq.getAmount();
    }
    
    /**
     * Collects all completed Grand Exchange offers and updates item requirements accordingly.
     * This method handles offers from previous sessions and cancelled offers with partial fills.
     * 
     * @return true if any offers were collected, false otherwise
     */
    private boolean collectExistingCompletedOffers() {
        try {
            Map<GrandExchangeSlots, GrandExchangeOfferDetails> completedOffers = Rs2GrandExchange.getCompletedOffers();
            
            if (completedOffers.isEmpty()) {
                return false;
            }
            
            log.info("Found {} completed offers to collect", completedOffers.size());
            boolean collectedAny = false;
            
            for (Map.Entry<GrandExchangeSlots, GrandExchangeOfferDetails> entry : completedOffers.entrySet()) {
                GrandExchangeSlots slot = entry.getKey();
                GrandExchangeOfferDetails details = entry.getValue();
                
                // Find matching shop item requirement
                ShopItemRequirement matchingReq = null;
                for (ShopItemRequirement itemReq : shopItemRequirements.values()) {
                    log.info("Checking item requirement: {}  offer item ID: {} req item ID: {}", 
                             itemReq.getItemName(), details.getItemId(), itemReq.getShopItem().getItemId());
                    if (itemReq.getShopItem().getItemId() == details.getItemId()) {
                        matchingReq = itemReq;
                        break;
                    }
                }
                
                if (matchingReq != null) {
                    // Collect the offer and get exact quantity
                    int itemsTransacted = Rs2GrandExchange.collectOfferAndGetQuantity(slot, enableBanking, details.getItemId());
                    
                    if (itemsTransacted > 0) {
                        matchingReq.addCompletedAmount(itemsTransacted);
                        collectedAny = true;
                        
                        String transactionType = details.isSelling() ? "sold" : "bought";
                        log.info("Collected previous {} offer: {} {} of {}", 
                                transactionType, itemsTransacted, matchingReq.getItemName(), matchingReq.getItemName());
                        
                        Microbot.status = "Collected " + itemsTransacted + "x " + matchingReq.getItemName() + 
                                         " from previous " + transactionType + " offer";
                    }else{
                        log.warn("No items transacted for slot {} with details: {}", slot, details);
                    }
                }else{
                    //collect, to bank to free slot, but we dont need to update any requirements                    
                    Rs2GrandExchange.collectOfferAndGetQuantity(slot, enableBanking, details.getItemId());
                }
            }
            
            return collectedAny;
            
        } catch (Exception e) {
            log.error("Error collecting existing completed offers: {}", e.getMessage());
            return false;
        }
    }
    
    /**
     * Handles purchasing multiple items from the Grand Exchange.
     * Implements proper slot management, offer placement, waiting, and collection patterns.
     * Enhanced with 8-slot limit tracking and batch processing.
     * 
     * @return true if the purchase was successful, false otherwise
     */
    private boolean buyFromGrandExchange() {
        try {
            Microbot.status = "Buying " + getName() + " from Grand Exchange";
            WorldArea locationArea = primaryShopItem.getLocationArea();
            WorldPoint[] locationCorners = new WorldPoint[] {
                locationArea.toWorldPoint(), // Southwest corner (x, y)
                new WorldPoint(locationArea.getX() + locationArea.getWidth() - 1, locationArea.getY(), locationArea.getPlane()), // Southeast corner
                new WorldPoint(locationArea.getX(), locationArea.getY() + locationArea.getHeight() - 1, locationArea.getPlane()), // Northwest corner  
                new WorldPoint(locationArea.getX() + locationArea.getWidth() - 1, locationArea.getY() + locationArea.getHeight() - 1, locationArea.getPlane()) // Northeast corner
            };
            // Walk to Grand Exchange and open interface
            if (!Rs2Walker.isInArea(locationCorners) &&  !Rs2Walker.walkTo(BankLocation.GRAND_EXCHANGE.getWorldPoint(),10)) {
                Microbot.status = "Failed to walk to Grand Exchange";
                return false;
            }
            
            if (!Rs2GrandExchange.openExchange()) {
                Microbot.status = "Failed to open Grand Exchange interface";
                return false;
            }            
            
            // Wait for interface to stabilize and ensure GE is properly open
            if (!sleepUntil(() -> Rs2GrandExchange.isOpen(), 5000)) {
                Microbot.status = "Grand Exchange interface failed to open properly";
                return false;
            }
            sleep(600, 1200); // Allow interface to fully load
            
            // First, collect any existing completed offers from previous sessions
            collectExistingCompletedOffers();
            
            // Check if we have sufficient coins for all items
            int totalCost = calculateTotalCost();
            
            if (totalCost > 0 && Rs2Inventory.itemQuantity("Coins") + Rs2Bank.count("Coins") < totalCost) {
                Microbot.status = "Insufficient coins for Grand Exchange purchase (need " + totalCost + " coins)";
                return false;
            }
            
            // Process each item that still needs to be purchased
            List<ShopItemRequirement> pendingItems = shopItemRequirements.values().stream()
                    .filter(itemReq -> !itemReq.isCompleted())
                    .collect(Collectors.toList());
                    
            if (pendingItems.isEmpty()) {
                Microbot.status = "All items already obtained";
                return true;
            }
            
            //  Pre-check GE slot availability for all pending items
            int requiredSlots = pendingItems.size();
            if (!ensureGrandExchangeSlots(requiredSlots)) {
                Microbot.status = "Cannot allocate sufficient GE slots for " + requiredSlots + " items";
                return false;
            }
            
            
            // BATCH PROCESSING: First, place as many offers as possible in available slots
            Microbot.status = "Placing batch of Grand Exchange buy offers...";
            
            // Track what items we're buying and their slots
            Map<Integer, ShopItemRequirement> activeOffers = new HashMap<>();
            Map<Integer, GrandExchangeSlots> itemToSlotMap = new HashMap<>();
            Map<Integer, Integer> initialItemCounts = new HashMap<>();
            
            int placedOffers = 0;
            
            // First phase: Place as many offers as possible at once
            for (ShopItemRequirement itemReq : pendingItems) {
                // Stop if we've used all available GE slots
                if (Rs2GrandExchange.getAvailableSlotsCount() == 0) {
                    Microbot.status = "Maximum GE slots used - proceeding with " + placedOffers + " offers";
                    break;
                }
                
                // Calculate offer price using time-series data if enabled, fallback to traditional GE price
                int offerPrice;
                int itemId = itemReq.getShopItem().getItemId();
                
                if (itemReq.shouldUseTimeSeriesPricing()) {
                    // Use time-series average price for more intelligent buying
                    TimeSeriesAnalysis analysis = Rs2GrandExchange.getTimeSeriesData(
                        itemId, itemReq.getRecommendedTimeSeriesInterval());
                    
                    if (analysis.averagePrice > 0) {
                        // Use recommended buy price from time-series analysis
                        offerPrice = analysis.getRecommendedBuyPrice();
                        log.info("Using time-series buy price for {}: {} (avg: {}, high: {})", 
                                itemReq.getItemName(), offerPrice, analysis.averagePrice, analysis.averageHighPrice);
                    } else {
                        // Fallback to intelligent pricing with current market data
                        offerPrice = Rs2GrandExchange.getAdaptiveBuyPrice(itemId, itemReq.getShopItem().getPercentageBoughtAt(), 0);
                        log.info("Time-series unavailable for {}, using adaptive pricing: {}", 
                                itemReq.getItemName(), offerPrice);
                    }
                } else {
                    // Traditional pricing method
                    int gePrice = Microbot.getRs2ItemManager().getGEPrice(itemReq.getItemName());
                    offerPrice = Math.max((int) (gePrice * 1.1), (int) itemReq.getShopItem().getInitialPriceBuyAt());
                    log.debug("Using traditional buy price for {}: {} (GE: {})", 
                            itemReq.getItemName(), offerPrice, gePrice);
                }
                int initialPrice = (int)itemReq.getShopItem().getInitialPriceBuyAt();                
                log.info("using offer price for {}:\n\toffer price:{}, intial Price: {}", itemReq.getItemName(), offerPrice,initialPrice);
                // Ensure minimum price from shop item configuration
                offerPrice = Math.min(offerPrice, (int) itemReq.getShopItem().getInitialPriceBuyAt());
                
                int remainingAmount = itemReq.getRemainingAmount();
                
                // Check for duplicate offers before placing new ones
                cancelDuplicateOffers(itemId, itemReq.getItemName());
                
                // Find available slot
                GrandExchangeSlots availableSlot = Rs2GrandExchange.getAvailableSlot();
                if (availableSlot == null) {
                    log.warn("No available GE slots for {}", itemReq.getItemName());
                    continue;
                }
                
                // Place the buy order
                Microbot.status = "Placing offer #" + (placedOffers+1) + ": " + remainingAmount + "x " + 
                                 itemReq.getItemName() + " at " + offerPrice + " gp each";
                
                boolean offerPlaced = Rs2GrandExchange.buyItem(
                    itemReq.getItemName(), 
                    offerPrice, 
                    remainingAmount
                );
                
                if (!offerPlaced) {
                    Microbot.status = "Failed to place offer for " + itemReq.getItemName();
                    continue;
                }
                
                // Track this offer
                activeOffers.put(itemId, itemReq);
                itemToSlotMap.put(itemId, availableSlot);
                
                // Record initial item count for comparison later
                int initialCount = Rs2Inventory.itemQuantity(itemId) + Rs2Bank.count(itemId);
                initialItemCounts.put(itemId, initialCount);
                
                // Increment counter and brief pause between placing offers
                placedOffers++;
                sleep(Constants.GAME_TICK_LENGTH / 2);
            }
            
            if (placedOffers == 0) {
                Microbot.status = "Failed to place any Grand Exchange offers";
            } else {
                // Second phase: Wait for and collect offers
                Microbot.status = "Waiting for " + placedOffers + " Grand Exchange offers to complete...";
                log.info( "Waiting for " + placedOffers + " Grand Exchange offers to complete...");
                // Create a set of items that need to be completed
                Set<Integer> pendingItemIds = new HashSet<>(activeOffers.keySet());
                
                // Start time for timeout calculations
                long startTime = System.currentTimeMillis();
                long maxWaitTime = 120000; // 2 minutes total wait time for all offers
                
                // Wait for offers to complete and collect as they do
                while (!pendingItemIds.isEmpty() && System.currentTimeMillis() - startTime < maxWaitTime) {
                    // Check each pending item
                    for (Iterator<Integer> it = pendingItemIds.iterator(); it.hasNext();) {
                        int itemId = it.next();
                        ShopItemRequirement itemReq = activeOffers.get(itemId);
                        GrandExchangeSlots slot = itemToSlotMap.get(itemId);
                        
                        // Check if this offer has completed
                        boolean slotHasCompletedOffer = (slot != null) && Rs2GrandExchange.hasBoughtOffer(slot);
                        
                        // Also check if the item count has increased
                        int initialCount = initialItemCounts.get(itemId);
                        int currentCount = Rs2Inventory.itemQuantity(itemId) + Rs2Bank.count(itemId);                        
                        
                        // If either condition is met, consider the offer complete
                        if (slotHasCompletedOffer ) {
                            Microbot.status = "Offer completed for: " + itemReq.getItemName();
                            
                            // Use the enhanced collection method to get exact quantity
                            int itemsPurchased = 0;
                            if (slot != null && Rs2GrandExchange.hasBoughtOffer(slot)) {
                                // Use the new method to collect and get exact quantity
                                itemsPurchased = Rs2GrandExchange.collectOfferAndGetQuantity(slot, enableBanking, itemId);
                                log.debug("Collected {} items from offer for {}", itemsPurchased, itemReq.getItemName());
                            }
                            
                            // Update completed amount with actual purchased quantity
                            if (itemsPurchased > 0) {
                                itemReq.addCompletedAmount(itemsPurchased);
                                log.info("Updated completed amount for {}: purchased {} items, new total: {}/{}",
                                        itemReq.getItemName(), itemsPurchased, itemReq.getCompletedAmount(), itemReq.getAmount());
                            }else {
                                log.warn("No items collected from offer for {}", itemReq.getItemName());
                                return false; // If we couldn't collect, return false
                            }
                            
                            // Remove this item from pending list
                            it.remove();
                            
                            // Brief pause after collecting
                            sleep(Constants.GAME_TICK_LENGTH);
                        }
                    }
                    sleepUntil(()->Rs2GrandExchange.hasBoughtOffer(), (int)maxWaitTime/10 ); // Refresh state                    
                }
                
                // Handle any remaining pendingItemIds as unsuccessful
                if (!pendingItemIds.isEmpty()) {
                    Microbot.status = "Timed out waiting for " + pendingItemIds.size() + " offers";
                    
                    // List the items that didn't complete in time
                    for (int itemId : pendingItemIds) {
                        ShopItemRequirement itemReq = activeOffers.get(itemId);
                        log.warn("Offer timeout for: {}", itemReq.getItemName());
                    }
                }
            }
            
          
            
            // If we have any tracked slots that weren't properly cleared during processing,
            // make one final check and collection attempt for them
            for (Integer itemId : itemToSlotMap.keySet()) {
                GrandExchangeSlots slot = itemToSlotMap.get(itemId);
                ShopItemRequirement itemReq = activeOffers.get(itemId);
                
                if (slot != null && itemReq != null) {
                    // Check if there's an offer in this slot that needs collection
                    if (Rs2GrandExchange.hasBoughtOffer(slot)) {
                        Microbot.status = "Final collection for: " + itemReq.getItemName();
                        
                        // Use enhanced collection to get exact quantity
                        int itemsPurchased = Rs2GrandExchange.collectOfferAndGetQuantity(slot, enableBanking, itemId);
                        
                        if (itemsPurchased > 0) {
                            // Update completed amount with actual purchased quantity
                            itemReq.addCompletedAmount(itemsPurchased);
                            log.info("Final collection - Updated completed amount for {}: purchased {} items, new total: {}/{}",
                                    itemReq.getItemName(), itemsPurchased, itemReq.getCompletedAmount(), itemReq.getAmount());
                        }else {
                            log.warn("No items collected from final slot for {}", itemReq.getItemName());
                            return false; // If we couldn't collect, return false
                        }

                    }
                    
                    // Also check for cancelled offers with partial fills
                    else if (Rs2GrandExchange.isCancelledOfferWithItems(slot)) {
                        Microbot.status = "Collecting partial fill from cancelled offer: " + itemReq.getItemName();
                        
                        int itemsPurchased = Rs2GrandExchange.collectOfferAndGetQuantity(slot, enableBanking, itemId);
                        if (itemsPurchased > 0) {
                            itemReq.addCompletedAmount(itemsPurchased);
                            log.info("Collected {} items from cancelled offer for {}", itemsPurchased, itemReq.getItemName());
                        }
                        else {
                            log.warn("No items collected from cancelled offer for {}", itemReq.getItemName());
                            return false; // If we couldn't collect, return false
                        }
                    }
                }
            }
            
            // No need to clear allocations - using simplified approach  
            // clearGrandExchangeSlotAllocations();
            
            // Bank the items if banking is enabled
            if (enableBanking) {
                boolean hasItemsToBank = shopItemRequirements.values().stream()
                        .anyMatch(itemReq -> Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId()) > 0);
                        
                if (hasItemsToBank && !bankItems()) {
                    Microbot.status = "Purchase successful but failed to bank items";
                }
            }
            
            // Final completion check - items should already be updated during collection
            if (isAllItemsCompleted()) {
                Microbot.status = "Successfully purchased all items from Grand Exchange";
                log.info("Successfully purchased all items from Grand Exchange");
                return true;
            } else{              
                Microbot.status = "Some Grand Exchange purchases failed";
                log.warn("Some Grand Exchange purchases failed, remaining items: {}", 
                        shopItemRequirements.values().stream()
                            .filter(itemReq -> !itemReq.isCompleted())
                            .map(ShopItemRequirement::getItemName)
                            .collect(Collectors.joining(", ")));
                return  !isMandatory();
            }
            
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.buyFromGrandExchange", e);
            // No need to clear allocations - using simplified approach
            // clearGrandExchangeSlotAllocations();
            return false;
        }
    }
    
    /**
     * Handles selling multiple items to the Grand Exchange.
     * Implements proper slot management, offer placement, waiting, and collection patterns.
     * 
     * @return true if the sale was successful, false otherwise
     */
    private boolean sellToGrandExchange() {
        try {
            Microbot.status = "Selling items to Grand Exchange";
            
            // Get items from bank if needed first
            if (enableBanking && !getItemsFromBankForSelling()) {
                Microbot.status = "Failed to get items from bank for selling";
                return !isMandatory();
            }
            
            // Check if we have items to sell in inventory
            boolean hasItemsToSell = shopItemRequirements.values().stream()
                    .anyMatch(itemReq -> Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId()) > 0);
            
            if (!hasItemsToSell) {
                Microbot.status = "No items to sell";
                return !isMandatory();
            }
            
            // Walk to Grand Exchange and open interface
            if (!Rs2GrandExchange.walkToGrandExchange()) {
                Microbot.status = "Failed to walk to Grand Exchange for selling";
                return false;
            }
            
            if (!Rs2GrandExchange.openExchange()) {
                Microbot.status = "Failed to open Grand Exchange interface for selling";
                return false;
            }
            
            // Wait for interface to stabilize
            if (!sleepUntil(() -> Rs2GrandExchange.isOpen(), 3000)) {
                Microbot.status = "Grand Exchange interface failed to stabilize for selling";
                return false;
            }
            
            // First, collect any existing completed offers from previous sessions
            collectExistingCompletedOffers();
            
            // Process each item that has inventory stock to sell
            List<ShopItemRequirement> sellableItems = shopItemRequirements.values().stream()
                    .filter(itemReq -> Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId()) > 0)
                    .collect(Collectors.toList());
            
            if (sellableItems.isEmpty()) {
                Microbot.status = "No items found in inventory to sell";
                return true;
            }
                                    
            // First, try to free up GE slots if needed
            if (Rs2GrandExchange.getAvailableSlotsCount() == 0) {
                Microbot.status = "No free GE slots available for selling, attempting to free them";
                
                // Try to collect completed offers first
                if (Rs2GrandExchange.hasBoughtOffer() || Rs2GrandExchange.hasSoldOffer()) {
                    Rs2GrandExchange.collectAll(enableBanking);
                    sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() > 0, 3000);
                } else {
                    Rs2GrandExchange.abortAllOffers(enableBanking);
                    sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() > 0, 5000);
                }
                
                if (Rs2GrandExchange.getAvailableSlotsCount() == 0) {
                    Microbot.status = "Failed to free Grand Exchange slots for selling";
                    return !isMandatory();
                }
            }
            
            // BATCH PROCESSING: Place as many offers as possible first
            Microbot.status = "Placing batch of Grand Exchange sell offers...";
            
            // Track what items we're selling and their slots
            Map<Integer, ShopItemRequirement> activeOffers = new HashMap<>();
            Map<Integer, GrandExchangeSlots> itemToSlotMap = new HashMap<>();
            Map<Integer, Integer> initialInventoryCounts = new HashMap<>();
            
            int placedOffers = 0;
            
            // First phase: Place as many offers as possible at once
            for (ShopItemRequirement itemReq : sellableItems) {
                // Stop if we've used all available GE slots
                if (Rs2GrandExchange.getAvailableSlotsCount() == 0) {
                    Microbot.status = "Maximum GE slots used - proceeding with " + placedOffers + " offers";
                    break;
                }
                
                // Check inventory count for this item
                int inventoryCount = Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId());
                if (inventoryCount == 0) {
                    continue; // No items to sell for this item type
                }
                
                // Calculate sell price using time-series data if enabled, fallback to traditional GE price
                int sellPrice;
                int sellAmount = Math.min(inventoryCount, itemReq.getAmount());
                int itemId = itemReq.getShopItem().getItemId();
                
                if (itemReq.shouldUseTimeSeriesPricing()) {
                    // Use time-series average price for more intelligent selling
                    TimeSeriesAnalysis analysis = Rs2GrandExchange.getTimeSeriesData(
                        itemId, itemReq.getRecommendedTimeSeriesInterval());
                    
                    if (analysis.averagePrice > 0) {
                        // Use recommended sell price from time-series analysis
                        sellPrice = analysis.getRecommendedSellPrice();
                        log.info("Using time-series sell price for {}: {} (avg: {}, low: {})", 
                                itemReq.getItemName(), sellPrice, analysis.averagePrice, analysis.averageLowPrice);
                    } else {
                        // Fallback to intelligent pricing with current market data
                        sellPrice = Rs2GrandExchange.getAdaptiveSellPrice(itemId, itemReq.getShopItem().getInitialPriceSellAt(), 0);
                        log.info("Time-series unavailable for {}, using intelligent pricing: {}", 
                                itemReq.getItemName(), sellPrice);
                    }
                } else {
                    // Traditional pricing method
                    int gePrice = Microbot.getRs2ItemManager().getGEPrice(itemReq.getItemName());
                    sellPrice = Math.max((int) (gePrice * 0.9), (int) itemReq.getShopItem().getInitialPriceSellAt());
                    log.info("Using traditional sell price for {}: {} (GE: {})", 
                            itemReq.getItemName(), sellPrice, gePrice);
                }
                
                // Ensure minimum price from shop item configuration
                sellPrice = Math.max(sellPrice, (int) itemReq.getShopItem().getInitialPriceSellAt());
                
                // Check for duplicate offers before placing new ones
                cancelDuplicateOffers(itemId, itemReq.getItemName());
                
                // Find available slot
                GrandExchangeSlots availableSlot = Rs2GrandExchange.getAvailableSlot();
                if (availableSlot == null) {
                    log.warn("No available GE slots for selling {}", itemReq.getItemName());
                    continue;
                }
                
                // Place the sell order
                Microbot.status = "Placing offer #" + (placedOffers+1) + ": " + sellAmount + "x " + 
                                 itemReq.getItemName() + " at " + sellPrice + " gp each";
                
                boolean offerPlaced = Rs2GrandExchange.sellItem(
                    itemReq.getItemName(), 
                    sellAmount, 
                    sellPrice
                );
                
                if (!offerPlaced) {
                    Microbot.status = "Failed to place sell offer for " + itemReq.getItemName();
                    continue;
                }
                
                // Track this offer
                activeOffers.put(itemId, itemReq);
                itemToSlotMap.put(itemId, availableSlot);
                
                // Record initial inventory count for comparison later
                initialInventoryCounts.put(itemId, inventoryCount);
                
                // Increment counter and brief pause between placing offers
                placedOffers++;
                sleep(Constants.GAME_TICK_LENGTH / 2);
            }            
            if (placedOffers == 0) {
                Microbot.status = "Failed to place any Grand Exchange sell offers";
            } else {
                // Second phase: Wait for and collect offers
                Microbot.status = "Waiting for " + placedOffers + " Grand Exchange sell offers to complete...";
                
                // Create a set of items that need to be completed
                Set<Integer> pendingItemIds = new HashSet<>(activeOffers.keySet());
                
                // Start time for timeout calculations
                long startTime = System.currentTimeMillis();
                long maxWaitTime = 120000; // 2 minutes total wait time for all offers
                
                // Wait for offers to complete and collect as they do
                while (!pendingItemIds.isEmpty() && System.currentTimeMillis() - startTime < maxWaitTime) {
                    // Check each pending item
                    for (Iterator<Integer> it = pendingItemIds.iterator(); it.hasNext();) {
                        int itemId = it.next();
                        ShopItemRequirement itemReq = activeOffers.get(itemId);
                        GrandExchangeSlots slot = itemToSlotMap.get(itemId);
                        int initialInventoryCount = initialInventoryCounts.get(itemId);
                        
                        // Check if this offer has completed
                        boolean slotHasCompletedOffer = (slot != null) && Rs2GrandExchange.hasSoldOffer(slot);
                        
                        // Also check if the item count has decreased (items sold)
                        int currentInventoryCount = Rs2Inventory.itemQuantity(itemId);
                        boolean itemCountDecreased = currentInventoryCount < initialInventoryCount;
                        
                        // If either condition is met, consider the offer complete
                        if (slotHasCompletedOffer || itemCountDecreased) {
                            Microbot.status = "Sell offer completed for: " + itemReq.getItemName();
                       
                            // Use enhanced collection to get exact quantity sold
                            int itemsSold = 0;
                            if (slot != null && Rs2GrandExchange.hasSoldOffer(slot)) {
                                // Get the exact number of items sold from the offer
                                itemsSold = Rs2GrandExchange.getItemsSoldFromOffer(slot);
                                
                                // Collect the coins from this slot
                                Rs2GrandExchange.collectOffer(slot, enableBanking);
                                sleepUntil(() -> !Rs2GrandExchange.hasSoldOffer(slot) || 
                                          Rs2Inventory.itemQuantity("Coins") > 0, 3000);
                                
                                log.debug("Collected coins from sale of {} items of {}", itemsSold, itemReq.getItemName());
                            } else {
                                // Fallback: calculate based on inventory change
                                itemsSold = initialInventoryCount - currentInventoryCount;
                            }
                            
                            // Update completed amount based on how many items were actually sold
                            if (itemsSold > 0) {
                                itemReq.addCompletedAmount(itemsSold);
                                log.debug("Updated completed amount for {}: sold {} items, new total: {}/{}",
                                        itemReq.getItemName(), itemsSold, itemReq.getCompletedAmount(), itemReq.getAmount());
                            }
                            
                            // Remove this item from pending list
                            it.remove();
                            
                            // Brief pause after collecting
                            sleep(Constants.GAME_TICK_LENGTH);
                        }
                    }
                    sleepUntil(() -> Rs2GrandExchange.hasSoldOffer(), (int)maxWaitTime); // Refresh state
                    // Brief pause before next check to avoid CPU thrashing
                    sleep(1000);
                }
                
                // Handle any remaining pendingItemIds as unsuccessful
                if (!pendingItemIds.isEmpty()) {                    
                    Microbot.status = "Timed out waiting for " + pendingItemIds.size() + " sell offers";
                    log.warn("Timed out waiting for {} sell offers", pendingItemIds.size());
                    
                    // List the items that didn't complete in time
                    for (int itemId : pendingItemIds) {

                        ShopItemRequirement itemReq = activeOffers.get(itemId);
                        log.warn("Sell offer timeout for: {}", itemReq.getItemName());
                    }
                }
            }
            
         
            
            
            // No need to clear allocations - using simplified approach
            // clearGrandExchangeSlotAllocations();
     
            
            // Bank the coins if banking is enabled and we got them in inventory
            if (enableBanking && Rs2Inventory.itemQuantity("Coins") > 0) {
                if (!bankCoinsAfterSelling()) {
                    Microbot.status = "Sale successful but failed to bank coins";
                    // Don't fail the requirement just because banking failed
                }
            }
            
            if (isAllItemsCompleted()) {
                Microbot.status = "Successfully sold all items to Grand Exchange";
                log.info("Successfully sold all items to Grand Exchange");  
                return true;
            } else {
                Microbot.status = "Some Grand Exchange sales failed";
                log.warn("Some Grand Exchange sales failed, remaining items: {}", 
                        shopItemRequirements.values().stream()
                            .filter(itemReq -> !itemReq.isCompleted())
                            .map(ShopItemRequirement::getItemName)
                            .collect(Collectors.joining(", ")));
                return !isMandatory(); // Allow continuation if not mandatory
            }
            
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.sellToGrandExchange", e);
            return false;
        }
    }
    
    /**
     * Handles purchasing multiple items from regular shops with stock management and world hopping.
     * Supports both single and multi-item operations with individual item requirements.
     * 
     * @return true if the purchase was successful, false otherwise
     */
    private boolean buyFromRegularShop() {
        try {
            Microbot.status = "Buying items from " + primaryShopItem.getShopNpcName();
            
            int maxAttempts = enableWorldHopping ? 10 : 3; // More attempts if world hopping is enabled
            int attempts = 0;
            boolean allItemsCompleted = false;
            
            while (!allItemsCompleted && attempts < maxAttempts) {
                attempts++;
                
                // Walk to the shop
                if (!Rs2Walker.walkTo(primaryShopItem.getLocation())) {
                    Microbot.status = "Failed to walk to shop";
                    log.error("Failed to walk to shop: " + primaryShopItem.getLocation());
                    sleep(1000, 2000);
                    continue;
                }
                
                // Open shop interface
                if (!Rs2Shop.openShop(primaryShopItem.getShopNpcName())) {
                    Microbot.status = "Failed to open shop";
                    log.error("Failed to open shop: " + primaryShopItem.getShopNpcName());
                    sleep(1000, 2000);
                    continue;
                }
                
                sleepUntil(() -> Rs2Shop.isOpen(), Constants.GAME_TICK_LENGTH * 3); // Ensure shop is open
                if (!Rs2Shop.isOpen()) {
                    Microbot.status = "Shop interface not open";
                    log.error("Shop interface failed to open for " + primaryShopItem.getShopNpcName());
                    return false; // Exit if shop interface failed to open
                }
                
                // Process each item that still needs to be purchased
                List<ShopItemRequirement> pendingItems = shopItemRequirements.values().stream()
                        .filter(itemReq -> !itemReq.isCompleted())
                        .collect(Collectors.toList());
                
                if (pendingItems.isEmpty()) {
                    allItemsCompleted = true;
                    Rs2Shop.closeShop();
                    break;
                }
                
                boolean needWorldHop = false;
                boolean purchasedAnything = false;
                
                for (ShopItemRequirement itemReq : pendingItems) {
                    // Track initial inventory count before attempting purchase
                    int initialItemCount = Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId());
                    
                    if (itemReq.isCompleted()) {
                        continue; // Skip completed items
                    }
                    
                    // Get current stock level from the shop interface (real-time check)
                    int currentStock = getShopStock(itemReq.getItemName());
                    if (currentStock == -1) {
                        Microbot.status = itemReq.getItemName() + " not found in shop";
                        log.error("Shop item not found: " + itemReq.getItemName());
                        Rs2Shop.closeShop();
                        return false;
                    }
                    
                    // Check if stock is sufficient using new unified logic
                    if (!itemReq.canProcessInShop(currentStock, ShopOperation.BUY)) {
                        Microbot.status = "Insufficient stock for " + itemReq.getItemName() + 
                                         " (current: " + currentStock + ", minimum: " + itemReq.getMinimumStockForBuying() + ")";
                        needWorldHop = true;
                        log.warn("Insufficient stock for " + itemReq.getItemName() + 
                                 " in shop " + primaryShopItem.getShopNpcName() + 
                                 " (current: " + currentStock + ", minimum: " + itemReq.getMinimumStockForBuying() + ")");
                        continue; // Check other items
                    }
                    
                    // **STOCK MANAGEMENT FIX**: Calculate quantity using unified logic
                    int quantityThisVisit = itemReq.getQuantityForCurrentVisit(currentStock, ShopOperation.BUY);
                    quantityThisVisit = Math.min(quantityThisVisit, currentStock);
                    
                    // Check if item is stackable to determine inventory limit
                    boolean isStackable = itemReq.getShopItem().getItemComposition() != null && 
                                         itemReq.getShopItem().getItemComposition().isStackable();
                    if (!isStackable) {
                        // For non-stackable items, limit by available inventory space
                        int freeSlots = 28 - Rs2Inventory.count();
                        quantityThisVisit = Math.min(quantityThisVisit, freeSlots);
                    }
                    
                    if (quantityThisVisit <= 0) {
                        if (!isStackable && Rs2Inventory.count() >= 28) {
                            Microbot.status = "Inventory full - banking non-stackable items";
                            Rs2Shop.closeShop();
                            
                            if (enableBanking && bankItems()) {
                                attempts--; // Don't count banking as failed attempt
                                break; // Restart the shop visit after banking
                            }
                            return false;
                        }
                        continue; // Can't buy this item right now, check next
                    }
                    
                    // Purchase items using optimal buying method
                    boolean purchaseSuccessful = false;
                    try {
                        Microbot.status = "Purchasing " + quantityThisVisit + "x " + itemReq.getItemName() + 
                                         " (" + itemReq.getCompletedAmount() + "/" + itemReq.getAmount() + " total)";
                        
                        purchaseSuccessful = Rs2Shop.buyItem(itemReq.getItemName(), String.valueOf(quantityThisVisit));
                        if (purchaseSuccessful) {
                            // Wait for purchase to complete
                            sleepUntil(() -> Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId()) > initialItemCount, 3000);
                            
                            // Calculate how many items were actually purchased
                            int finalItemCount = Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId());
                            int itemsPurchased = finalItemCount - initialItemCount;
                            
                            // Update completed amount with actual purchased quantity
                            itemReq.addCompletedAmount(itemsPurchased);
                            purchasedAnything = true;
                            
                            log.debug("Purchased {} items of {}, new completion: {}/{}",
                                    itemsPurchased, itemReq.getItemName(), itemReq.getCompletedAmount(), itemReq.getAmount());
                            
                            Microbot.status = "Successfully purchased " + itemsPurchased + "x " + itemReq.getItemName();
                        }
                        
                    } catch (Exception e) {
                        Microbot.logStackTrace("ShopRequirement.buyFromRegularShop - Purchase failed for " + itemReq.getItemName(), e);
                        purchaseSuccessful = false;
                    }
                    
                    // Brief pause between item purchases
                    if (purchaseSuccessful) {
                        sleep(900, 300);
                    }
                }
                
                Rs2Shop.closeShop();
                
                // Handle world hopping if needed
                if (needWorldHop && !purchasedAnything && enableWorldHopping && primaryShopItem.getShopType().supportsWorldHopping()) {
                    if (hopWorld()) {
                        attempts--; // Don't count world hop as failed attempt
                        continue;
                    } else {
                        Microbot.status = "Failed to hop worlds - insufficient stock";
                        return false;
                    }
                }
                
                // Check if we should bank items
                if (enableBanking && Rs2Inventory.count() > 20) {
                    if (!bankItems()) {
                        Microbot.status = "Failed to bank items - continuing without banking";
                    }
                }
                
                // Update completion status for all items
                allItemsCompleted = isAllItemsCompleted();
                
                // Brief pause between shop visits
                sleep(1000, 2000);
            }
            
            // Final status update
            if (allItemsCompleted) {
                Microbot.status = "Successfully completed purchase of all items from regular shop";
            } else {
                Microbot.status = "Purchase incomplete after " + attempts + " attempts";
            }
            
            return allItemsCompleted;
            
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.buyFromRegularShop", e);
            return false;
        }
    }
    
    /**
     * Enhanced selling method with BanksShopper patterns for multiple items.
     * Implements proper while loop logic for selling multiple items across worlds.
     * 
     * @return true if the sale was successful, false otherwise
     */
    private boolean sellToRegularShop() {
        try {
            Microbot.status = "Selling items to " + primaryShopItem.getShopNpcName();
            
            int maxAttempts = enableWorldHopping ? 10 : 3; // More attempts if world hopping is enabled
            int attempts = 0;
            boolean allItemsCompleted = false;
            
            while (!allItemsCompleted && attempts < maxAttempts) {
                attempts++;
                
                // Walk to the shop
                if (!Rs2Walker.walkTo(primaryShopItem.getLocation())) {
                    Microbot.status = "Failed to walk to shop for selling";
                    sleep(1000, 2000);
                    continue;
                }
                
                // Open shop interface
                if (!Rs2Shop.openShop(primaryShopItem.getShopNpcName())) {
                    Microbot.status = "Failed to open shop for selling";
                    sleep(1000, 2000);
                    continue;
                }
                
                // Wait for shop data to update - check if shop is properly loaded
                if (!sleepUntil(() -> Rs2Shop.isOpen(), 3000)) {
                    Microbot.status = "Shop interface failed to stabilize for selling";
                    Rs2Shop.closeShop();
                    continue;
                }
                
                // Process each item that still needs to be sold
                List<ShopItemRequirement> pendingItems = shopItemRequirements.values().stream()
                        .filter(itemReq -> {
                            // Check if we have items to sell in inventory
                            int currentInventoryCount = Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId());
                            return currentInventoryCount > 0 && !itemReq.isCompleted();
                        })
                        .collect(Collectors.toList());
                
                if (pendingItems.isEmpty()) {
                    allItemsCompleted = true;
                    Rs2Shop.closeShop();
                    break;
                }
                
                boolean needWorldHop = false;
                boolean soldAnything = false;
                
                for (ShopItemRequirement itemReq : pendingItems) {
                    // Check if we still have items to sell
                    int currentInventoryCount = Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId());
                    if (currentInventoryCount == 0) {
                        continue; // No more of this item to sell
                    }
                    
                    // Get current shop stock and check if we can sell safely using unified API
                    int currentStock = getShopStock(itemReq.getItemName());
                    if (currentStock == -1) {
                        Microbot.status = itemReq.getItemName() + " not found in shop for selling";
                        Rs2Shop.closeShop();
                        return false;
                    }
                    
                    // **STOCK MANAGEMENT FIX**: Use unified stock validation
                    if (!itemReq.canProcessInShop(currentStock, operation)) {
                        Microbot.status = "Shop stock too high for " + itemReq.getItemName() + 
                                         " (current: " + currentStock + ") - cannot sell";
                        needWorldHop = true;
                        continue; // Check other items
                    }
                    
                    // **STOCK MANAGEMENT FIX**: Use unified quantity calculation
                    int quantityThisVisit = itemReq.getQuantityForCurrentVisit(currentStock, ShopOperation.SELL);
                    quantityThisVisit = Math.min(quantityThisVisit, currentInventoryCount);
                    
                    if (quantityThisVisit <= 0) {
                        continue; // Cannot sell any items this visit, check next item
                    }
                    
                    Microbot.status = "Selling " + quantityThisVisit + "x " + itemReq.getItemName() + 
                                     " (shop stock: " + currentStock + ")";
                    
                    // Sell items using inventory method (standard approach for selling)
                    boolean sellSuccessful = false;
                    try {
                        sellSuccessful = Rs2Inventory.sellItem(itemReq.getItemName(), String.valueOf(quantityThisVisit));
                        if (sellSuccessful) {
                            // Wait for sale to complete
                            sleepUntil(() -> Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId()) < currentInventoryCount, 3000);
                            
                            soldAnything = true;
                            Microbot.status = "Successfully sold " + quantityThisVisit + "x " + itemReq.getItemName();
                        }
                        
                    } catch (Exception e) {
                        Microbot.logStackTrace("ShopRequirement.sellToRegularShop - Sale failed for " + itemReq.getItemName(), e);
                        sellSuccessful = false;
                    }
                    
                    // Brief pause between item sales
                    if (sellSuccessful) {
                        sleep(900, 300);
                    }
                }
                
                Rs2Shop.closeShop();
                
                // Handle world hopping if needed
                if (needWorldHop && !soldAnything && enableWorldHopping && primaryShopItem.getShopType().supportsWorldHopping()) {
                    if (hopWorld()) {
                        attempts--; // Don't count world hop as failed attempt
                        continue;
                    } else {
                        Microbot.status = "Failed to hop worlds - shop stock too high";
                        return false;
                    }
                }
                
                // Update completion status for all items
                allItemsCompleted = shopItemRequirements.values().stream()
                        .allMatch(itemReq -> Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId()) == 0);
                
                // Brief pause between shop visits
                sleep(1000, 2000);
            }
            
            // Final status update
            if (allItemsCompleted) {
                Microbot.status = "Successfully completed sale of all items to regular shop";
            } else {
                Microbot.status = "Sale incomplete after " + attempts + " attempts";
            }
            
            return allItemsCompleted;
            
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.sellToRegularShop", e);
            return false;
        }
    }
    
    /**
     * Enhanced world hopping using WorldHoppingConfig patterns with exponential backoff.
     * Integrates with the sophisticated world hopping system for better reliability.
     * 
     * @return true if world hop was successful, false otherwise
     */
    private boolean hopWorld() {
        try {
            Microbot.status = "Stock level inadequate - hopping worlds with smart retry logic";
            Rs2Shop.closeShop();
            
            // Wait to avoid "finish what you're doing" message - ensure player is ready
            sleep(3200, 800); // Standard pre-hop delay
            
            int attempts = 0;
            int maxAttempts = worldHoppingConfig.getMaxWorldHops();
            
            while (attempts < maxAttempts) {
                attempts++;
                
                // Get next world using configured strategy
                int world = useNextWorld || worldHoppingConfig.isUseSequentialWorlds() ? 
                    Login.getNextWorld(Rs2Player.isMember()) : 
                    Login.getRandomWorld(Rs2Player.isMember());
                
                Microbot.status = "Attempting world hop " + attempts + "/" + maxAttempts + " to world " + world;
                
                boolean hopped = Microbot.hopToWorld(world);
                if (!hopped) {
                    Microbot.status = "Failed to hop to world " + world + " (attempt " + attempts + ")";
                    
                    // Use exponential backoff delay from WorldHoppingConfig
                    long retryDelay = worldHoppingConfig.getHopDelay(attempts);
                    Microbot.status = "Retrying in " + retryDelay + "ms with exponential backoff";
                    sleep((int) retryDelay, (int) (retryDelay * 0.1)); // 10% variance
                    continue;
                }
                
                // Wait for hop to complete with proper state checking
                boolean hopCompleted = sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING, 5000);
                if (hopCompleted) {
                    hopCompleted = sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN, 15000);
                }
                
                if (!hopCompleted) {
                    Microbot.status = "World hop to " + world + " failed to complete (attempt " + attempts + ")";
                    long retryDelay = worldHoppingConfig.getHopDelay(attempts);
                    sleep((int) retryDelay, (int) (retryDelay * 0.1));
                    continue;
                }
                
                Microbot.status = "Successfully hopped to world " + world + " (attempt " + attempts + ")";
                
                // Additional wait for world to stabilize (base delay)
                sleep(worldHoppingConfig.getBaseHopDelay(), worldHoppingConfig.getBaseHopDelay() / 3);
                
                return true;
            }
            
            Microbot.status = "Failed to hop worlds after " + maxAttempts + " attempts";
            return false;
            
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.hopWorld", e);
            return false;
        }
    }
    
    /**
     * Banks purchased items for multiple shop items if banking is enabled.
     * 
     * @return true if banking was successful, false otherwise
     */
    private boolean bankItems() {
        try {
            Microbot.status = "Banking purchased items";
            
            // Get all item names from our shop requirements
            List<String> itemNames = shopItemRequirements.keySet().stream()
                    .map(Rs2ShopItem::getItemName)
                    .collect(Collectors.toList());
            
            // Use Rs2Bank utility for banking
            boolean success = Rs2Bank.bankItemsAndWalkBackToOriginalPosition(
                itemNames, 
                primaryShopItem.getLocation()
            );
            
            if (success) {
                Microbot.status = "Successfully banked items";
            } else {
                Microbot.status = "Failed to bank items";
            }
            
            return success;
            
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.bankItems", e);
            return false;
        }
    }
    
 
    
    /**
     * Calculates the total cost for purchasing all required items with dynamic pricing.
     * Uses Rs2ShopItem dynamic pricing calculations based on stock levels.
     * 
     * @return The total cost in coins, or -1 if calculation failed
     */
    private int calculateTotalCost() {
        if (shopItemRequirements.isEmpty()) {
            return -1;
        }
        
        try {
            int totalCost = 0;
            
            for (ShopItemRequirement itemReq : shopItemRequirements.values()) {
                if (itemReq.isCompleted()) {
                    continue; // Skip completed items
                }
                
                int currentCount = Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId()) + 
                                 Rs2Bank.count(itemReq.getShopItem().getItemId());
                int amountToBuy = Math.max(0, itemReq.getAmount() - currentCount);
                
                if (amountToBuy > 0) {
                    int itemCost = itemReq.getShopItem().getCostForBuyingX(itemReq.getAmount(), amountToBuy);
                    if (itemCost > 0) {
                        totalCost += itemCost;
                    }
                }
            }
            
            return totalCost;
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.calculateTotalCost", e);
            return -1;
        }
    }
    
    /**
     * Calculates the total value for selling all items with dynamic pricing.
     * Uses Rs2ShopItem dynamic pricing calculations based on stock levels.
     * 
     * @return The total sell value in coins, or -1 if calculation failed
     */
    @SuppressWarnings("unused")
    private int calculateTotalSellValue() {
        try {
            int totalValue = 0;
            
            for (ShopItemRequirement itemReq : shopItemRequirements.values()) {
                int currentCount = Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId());
                
                if (currentCount > 0) {
                    int itemValue = itemReq.getShopItem().getReturnForSellingX(itemReq.getAmount(), currentCount);
                    if (itemValue > 0) {
                        totalValue += itemValue;
                    }
                }
            }
            
            return totalValue;
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.calculateTotalSellValue", e);
            return -1;
        }
    }
    
    /**
     * Estimates the number of world hops needed based on stock levels and requirements for multiple items.
     * 
     * @return Estimated world hops needed, or -1 if cannot estimate
     */
    @SuppressWarnings("unused")
    private int estimateWorldHopsNeeded() {
        try {
            int maxWorldHopsNeeded = 0;
            
            for (ShopItemRequirement itemReq : shopItemRequirements.values()) {
                if (itemReq.isCompleted()) {
                    continue; // Skip completed items
                }
                
                if (itemReq.getShopItem().getBaseStock() <= 0) {
                    return -1; // Cannot estimate for items with no base stock
                }
                
                int remainingToBuy = itemReq.getRemainingAmount();
                int availablePerWorld = Math.max(itemReq.getShopItem().getBaseStock() - itemReq.getMinimumStockForBuying(), 0);
                
                if (availablePerWorld <= 0) {
                    return -1; // Insufficient stock per world for this item
                }
                
                int worldHopsForThisItem = (int) Math.ceil((double) remainingToBuy / Math.min(availablePerWorld, itemReq.getMaxQuantityPerVisit()));
                maxWorldHopsNeeded = Math.max(maxWorldHopsNeeded, worldHopsForThisItem);
            }
            
            return maxWorldHopsNeeded;
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.estimateWorldHopsNeeded", e);
            return -1;
        }
    }
    public boolean isFulfilled() {
        // Check if all items are completed
        return shopItemRequirements.values().stream().allMatch(ShopItemRequirement::isCompleted);
    }
    /**
     * Implements the abstract fulfillRequirement method from the base Requirement class.
     * Handles both buying and selling operations based on the operation type.
     * 
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @return true if the requirement was successfully fulfilled, false otherwise
     */
    @Override
    public boolean fulfillRequirement(ScheduledExecutorService executorService) {
        try {
            if (Microbot.getClient().isClientThread()) {
                Microbot.log("Please run fulfillRequirement() on a non-client thread.", Level.ERROR);
                return false;
            }
            Microbot.status = "Fulfilling shop requirement: " + operation.name() + " " + getName();            
            boolean success = false;
            if (isFulfilled()) {
                Microbot.status = "Shop requirement not fulfilled, proceeding with " + operation.name();
                log.info("Shop requirement already fulfilled: " + getName() + " (" + operation + ")");
                return true; // Already fulfilled, no need to proceed
            }

               // Validate items can be sold (are tradeable)
            for (ShopItemRequirement itemReq : shopItemRequirements.values()) {
                boolean isAccessible = itemReq.getShopItem().canAccess();
                if (!itemReq.getShopItem().getItemComposition().isTradeable() || !isAccessible) {
                    Microbot.log("Item " + itemReq.getItemName() + " is not tradeable - cannot sell to shop or shop could not be accessed");
                    return !isMandatory();
                }
                //updateCompletedAmount(itemReq);
            }
            switch (operation) {
                case BUY:
                    success = handleBuyOperation();
                    break;
                case SELL:
                    success = handleSellOperation();
                    break;
                default:
                    Microbot.log("Unknown shop operation: " + operation);
                    return false;
            }
            
            if (!success && isMandatory()) {
                Microbot.log("MANDATORY shop requirement failed: " + getName() + " (" + operation + ")");
                return false;
            }
            
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error fulfilling shop requirement " + getName() + ": " + e.getMessage());
            return !isMandatory(); // Don't fail mandatory requirements due to exceptions
        }
    }
    
    /**
     * Handles buy operations with proper separation between Grand Exchange and regular shops.
     * 
     * @return true if the buy operation was successful, false otherwise
     */
    private boolean handleBuyOperation() {
        try {
            // Check if we already have enough items
            if (isAllItemsCompleted()) {
                Microbot.status = "Already have required amount of all items";
                log.info("All items already completed for shop requirement: " + getName());
                return true;
            }                                
            // Handle Grand Exchange vs Regular Shop differently
            if (primaryShopItem.getShopType() == Rs2ShopType.GRAND_EXCHANGE) {
                log.info("Handling Grand Exchange buy operation for shop requirement: " + getName());
                return handleGrandExchangeBuyOperation();
            } else {
                return handleRegularShopBuyOperation();
            }
            
        } catch (Exception e) {
            Microbot.log("Error in buy operation: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Handles buying multiple items from Grand Exchange - must wait for offers to complete.
     */
    private boolean handleGrandExchangeBuyOperation() {
        try {
            Microbot.status = "Buying items from Grand Exchange";
            
            // Get coins from bank if needed
            if (enableBanking && !ensureSufficientCoins()) {
                Microbot.status = "Failed to get sufficient coins for Grand Exchange";
                log.info("Insufficient coins for Grand Exchange purchase: " + getName());
                return isMandatory() ? false : true;
            }
            
            // Walk to Grand Exchange
            if (!Rs2Walker.walkTo(primaryShopItem.getLocation(), primaryShopItem.getLocationArea().getHeight())) {
                Microbot.status = "Failed to walk to Grand Exchange";
                log.info("Failed to walk to Grand Exchange for shop requirement: " + getName());
                return false;
            }
            
            // Calculate total cost for all items
            int totalCost = calculateTotalCost();
            
            if (Rs2Inventory.itemQuantity("Coins") < totalCost) {
                Microbot.status = "Insufficient coins for Grand Exchange purchase";
                return !isMandatory();
            }
            
            // Use the enhanced buyFromGrandExchange method that handles multiple items
            return buyFromGrandExchange();
            
        } catch (Exception e) {
            Microbot.log("Error in Grand Exchange buy operation: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Handles buying multiple items from regular shops with stock management and world hopping.
     */
    private boolean handleRegularShopBuyOperation() {
        try {
            // Get coins from bank if needed
            if (enableBanking && !ensureSufficientCoins()) {
                Microbot.status = "Failed to get sufficient coins for shop purchase";
                return isMandatory() ? false : true;
            }
            
            // For multi-item regular shop purchases, use the enhanced buyFromRegularShop method
            return buyFromRegularShop();
            
        } catch (Exception e) {
            Microbot.log("Error in regular shop buy operation: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Handles sell operations with proper separation between Grand Exchange and regular shops.
     * 
     * @return true if the sell operation was successful, false otherwise
     */
    private boolean handleSellOperation() {
        try {
         
            
            // Handle Grand Exchange vs Regular Shop differently
            if (primaryShopItem.getShopType() == Rs2ShopType.GRAND_EXCHANGE) {
                return handleGrandExchangeSellOperation();
            } else {
                return handleRegularShopSellOperation();
            }
            
        } catch (Exception e) {
            Microbot.log("Error in sell operation for " + getName() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Handles selling to Grand Exchange - must wait for offers to complete.
     */
    private boolean handleGrandExchangeSellOperation() {
        return sellToGrandExchange();
    }
    
    /**
     * Handles selling to regular shops with stock management and world hopping.
     */
    private boolean handleRegularShopSellOperation() {
        try {
            // Get items from bank at the beginning if banking is enabled
            if (enableBanking) {
                if (!getItemsFromBankForSelling()) {
                    Microbot.status = "Failed to get items from bank for selling";
                    return !isMandatory(); // Not an error for optional requirements
                }
            }
            
            // Check if we have any items to sell in inventory
            boolean hasItemsToSell = shopItemRequirements.values().stream()
                    .anyMatch(itemReq -> Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId()) > 0);
            
            if (!hasItemsToSell) {
                Microbot.status = "No items to sell";
                return !isMandatory(); // Not an error for optional requirements
            }
            
            // Check if any items are noted and handle them appropriately
            for (ShopItemRequirement itemReq : shopItemRequirements.values()) {
                boolean hasNotedItems = Rs2Inventory.hasNotedItem(itemReq.getItemName());
                boolean isNoteable = itemReq.getShopItem().isNoteable();
                
                if (hasNotedItems) {
                    if (handleNotedItems && !isNoteable) {
                        // Items are noted but shouldn't be - this is an error state
                        Microbot.log("Item " + itemReq.getItemName() + " is noted but is not noteable - inconsistent state");
                        return false;
                    }

                    if (!handleNotedItems && !unnoteItemsForSelling()) {
                        Microbot.status = "Failed to unnote " + itemReq.getItemName() + " for selling";
                        return false;
                    }
                }
            }
            
            // Perform the sale to regular shop
            boolean sellSuccess = sellToRegularShop();
            
            // After selling, bank coins if banking is enabled
            if (sellSuccess && enableBanking) {
                if (!bankCoinsAfterSelling()) {
                    Microbot.status = "Sale successful but failed to bank coins";
                    // Don't fail the requirement just because banking failed
                }
            }
            
            return sellSuccess;
            
        } catch (Exception e) {
            Microbot.log("Error in regular shop sell operation: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Ensures the player has sufficient coins for buying operations.
     * 
     * @return true if sufficient coins are available, false otherwise
     */
    private boolean ensureSufficientCoins() {
        try {
            int totalCost = calculateTotalCost();
            if (totalCost <= 0) {
                return true; // No cost or calculation failed
            }
            
            int currentCoins = Rs2Inventory.itemQuantity("Coins") + Rs2Bank.count("Coins");
            if (currentCoins >= totalCost) {
                return true; // Already have enough coins
            }
            
            Microbot.status = "Getting coins from bank for " + getName();
            Rs2Bank.walkToBankAndUseBank();
            if (!Rs2Bank.isOpen()) {
                Microbot.status = "Failed to open bank for coins";
                return false;
            }
            
            int neededCoins = totalCost - currentCoins;
            int bankCoins = Rs2Bank.count("Coins");
            
            if (bankCoins < neededCoins) {
                Microbot.status = "Insufficient coins in bank. Need " + neededCoins + ", have " + bankCoins;
                Rs2Bank.closeBank();
                return false;
            }
            
            Rs2Bank.withdrawX("Coins", neededCoins);
            sleepUntil(() -> Rs2Inventory.itemQuantity("Coins") >= totalCost, 5000);
            
            Rs2Bank.closeBank();
            return Rs2Inventory.itemQuantity("Coins") >= totalCost;
            
        } catch (Exception e) {
            Microbot.log("Error ensuring sufficient coins: " + e.getMessage());
            Rs2Bank.closeBank();
            return false;
        }
    }
    
    /**
     * Enhanced banking method for selling operations with comprehensive item handling.
     * Properly handles stackable/non-stackable and noted/unnoted items.
     * 
     * @return true if items were successfully retrieved, false otherwise
     */
    private boolean getItemsFromBankForSelling() {
        try {
            Microbot.status = "Getting items from bank for selling with enhanced logic";
            
            if (!Rs2Bank.openBank()) {
                Microbot.status = "Failed to open bank for items";
                return false;
            }
            
            // **BANKING IMPROVEMENT**: Calculate inventory space needed for non-stackable items
            int inventorySpaceNeeded = 0;
            List<ShopItemRequirement> itemsNeedingWithdrawal = new ArrayList<>();
            
            for (ShopItemRequirement itemReq : shopItemRequirements.values()) {
                int currentCount = Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId());
                int neededCount = itemReq.getAmount() - currentCount;
                
                if (neededCount <= 0) {
                    continue; // Already have enough of this item
                }
                
                // Check if item is available in bank 
                if (!Rs2Bank.hasItem(itemReq.getItemName())) {
                    Microbot.status = "Bank does not contain " + itemReq.getItemName() + ", cannot sell";
                    continue;
                }
                
                itemsNeedingWithdrawal.add(itemReq);
                
                // **BANKING IMPROVEMENT**: Count space needed for non-stackable items
                boolean isStackable = itemReq.getShopItem().getItemComposition() != null && 
                                     itemReq.getShopItem().getItemComposition().isStackable();
                if (!isStackable && !itemReq.getShopItem().isNoteable()) {
                    inventorySpaceNeeded += Math.min(neededCount, 28); // Cap at inventory limit
                } else {
                    inventorySpaceNeeded += 1; // Stackable or noted items take 1 slot
                }
            }
            
            // **BANKING IMPROVEMENT**: Check if we have enough inventory space
            int currentInventoryCount = Rs2Inventory.count();
            int availableSpace = 28 - currentInventoryCount;
            
            if (inventorySpaceNeeded > availableSpace) {
                Microbot.status = "Insufficient inventory space: need " + inventorySpaceNeeded + 
                                 ", have " + availableSpace + " - depositing items first";
                
                // Deposit non-essential items to make space
                Rs2Bank.depositAllExcept("Coins"); // Keep coins for potential fees
                sleepUntil(() -> Rs2Inventory.count() <= 1, 3000); // Should only have coins
            }
            
            boolean allItemsRetrieved = true;
            
            for (ShopItemRequirement itemReq : itemsNeedingWithdrawal) {
                int currentCount = Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId());
                int neededCount = itemReq.getAmount() - currentCount;
                
                // **BANKING IMPROVEMENT**: Smart withdrawal based on item properties
                boolean isStackable = itemReq.getShopItem().getItemComposition() != null && 
                                     itemReq.getShopItem().getItemComposition().isStackable();
                boolean isNoteable = itemReq.getShopItem().isNoteable();
                
                if (isNoteable && handleNotedItems) {
                    // **BANKING IMPROVEMENT**: Use noted items for efficient selling
                    Microbot.status = "Withdrawing " + neededCount + "x " + itemReq.getItemName() + " as noted";
                    Rs2Bank.setWithdrawAs(true); // Withdraw as noted
                    Rs2Bank.withdrawX(itemReq.getShopItem().getItemName(), neededCount);
                    sleepUntil(() -> Rs2Inventory.itemQuantity(itemReq.getShopItem().getNoteId()) >= neededCount, 5000);
                } else if (isStackable) {
                    // **BANKING IMPROVEMENT**: Stackable items can be withdrawn in full
                    Microbot.status = "Withdrawing " + neededCount + "x " + itemReq.getItemName() + " (stackable)";
                    Rs2Bank.setWithdrawAs(false); // Withdraw as unnoted
                    Rs2Bank.withdrawX(itemReq.getShopItem().getItemName(), neededCount);
                    sleepUntil(() -> Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId()) >= neededCount, 5000);
                } else {
                    // **BANKING IMPROVEMENT**: Non-stackable items need careful space management
                    int availableSlots = 28 - Rs2Inventory.count();
                    int withdrawAmount = Math.min(neededCount, availableSlots);
                    
                    if (withdrawAmount <= 0) {
                        Microbot.status = "No inventory space for non-stackable " + itemReq.getItemName();
                        allItemsRetrieved = false;
                        continue;
                    }
                    
                    Microbot.status = "Withdrawing " + withdrawAmount + "x " + itemReq.getItemName() + 
                                     " (non-stackable, space limited)";
                    Rs2Bank.setWithdrawAs(false); // Withdraw as unnoted
                    Rs2Bank.withdrawX(itemReq.getShopItem().getItemName(), withdrawAmount);
                    sleepUntil(() -> Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId()) >= 
                                   (currentCount + withdrawAmount), 5000);
                }
                
                // Brief pause between withdrawals
                sleep(500, 200);
            }
            
            Rs2Bank.setWithdrawAs(false); // Reset to default
            Rs2Bank.closeBank();
            
            return allItemsRetrieved;
            
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.getItemsFromBankForSelling", e);
            Rs2Bank.closeBank();
            return false;
        }
    }
    
    /**
     * Purchases a specific batch size of items from the shop with proper stock management.
     * Ensures we don't buy more than available stock and respects shop economics.
     * NOTE: This method is deprecated in favor of the enhanced buyFromRegularShop method.
     * 
     * @param batchSize The number of items to buy in this batch
     * @return true if the batch purchase was successful, false otherwise
     */
    @Deprecated
    private boolean buyFromShopBatch(int batchSize) {
        try {
            Microbot.log("buyFromShopBatch is deprecated - use enhanced buyFromRegularShop instead");
            
            // Walk to the shop
            if (!Rs2Walker.walkTo(primaryShopItem.getLocation())) {
                Microbot.status = "Failed to walk to shop for batch purchase";
                return false;
            }
            
            // Open shop interface
            if (!Rs2Shop.openShop(primaryShopItem.getShopNpcName())) {
                Microbot.status = "Failed to open shop for batch purchase";
                return false;
            }
            
            // Wait for shop data to update - check if shop is properly loaded
            if (!sleepUntil(() -> Rs2Shop.isOpen(), 3000)) {
                Microbot.status = "Shop interface failed to stabilize for batch purchase";
                Rs2Shop.closeShop();
                return false;
            }
            
            // Just purchase the primary item for compatibility
            String itemName = primaryShopItem.getItemName();
            
            // Get current stock level from the shop interface (real-time check)
            int currentStock = getShopStock(itemName);
            if (currentStock == -1) {
                Microbot.status = itemName + " not found in shop";
                Rs2Shop.closeShop();
                return false;
            }
            
            // Use minimum stock from first item requirement for compatibility
            int minimumStock = shopItemRequirements.values().iterator().next().getMinimumStock();
            
            // Check if stock is sufficient for our minimum requirements
            if (currentStock < minimumStock) {
                Microbot.status = "Insufficient stock for " + itemName + " (current: " + currentStock + ", minimum: " + minimumStock + ")";
                Rs2Shop.closeShop();
                
                if (enableWorldHopping && primaryShopItem.getShopType().supportsWorldHopping()) {
                    return hopToWorldWithStock(minimumStock);
                } else {
                    return false;
                }
            }
            
            // Use max quantity from first item requirement for compatibility
            int maxQuantityPerVisit = shopItemRequirements.values().iterator().next().getMaxQuantityPerVisit();
            
            // CRITICAL: Don't buy more than we should to avoid price manipulation
            int maxSafeToBuy = Math.min(currentStock - minimumStock, maxQuantityPerVisit);
            int actualBatchSize = Math.min(batchSize, maxSafeToBuy);
            
            if (actualBatchSize <= 0) {
                Microbot.status = "Cannot buy any " + itemName + " without affecting stock/price negatively";
                Rs2Shop.closeShop();
                
                if (enableWorldHopping) {
                    return hopToWorldWithStock(minimumStock);
                } else {
                    return false;
                }
            }
            
            Microbot.log("Buying " + actualBatchSize + " " + itemName + " (stock: " + currentStock + ", max safe: " + maxSafeToBuy + ")");
            
            int hasItemAmountBefore = Rs2Inventory.itemQuantity(itemName);
            
            // Perform the purchase with safe amount
            boolean success = Rs2Shop.buyItem(itemName, String.valueOf(actualBatchSize));
            if (!success) {
                Microbot.status = "Failed to initiate purchase of " + itemName;
                Rs2Shop.closeShop();
                return false;
            }
            
            // Wait for purchase to complete
            boolean purchaseCompleted = sleepUntil(() -> 
                Rs2Inventory.itemQuantity(itemName) >= hasItemAmountBefore + actualBatchSize, 5000);
                
            Rs2Shop.closeShop();
            
            if (!purchaseCompleted) {
                Microbot.status = "Purchase timed out for " + itemName;
                return false;
            }
            
            int actualPurchased = Rs2Inventory.itemQuantity(itemName) - hasItemAmountBefore;
            Microbot.status = "Successfully purchased " + actualPurchased + "x " + itemName;
            
            return actualPurchased > 0;
            
        } catch (Exception e) {
            Microbot.log("Error in batch purchase: " + e.getMessage());
            Rs2Shop.closeShop();
            return false;
        }
    }
    
    /**
     * Banks purchased items and returns to the shop location for multiple items.
     * 
     * @return true if banking was successful, false otherwise
     */
    private boolean bankPurchasedItems() {
        try {
            Microbot.status = "Banking purchased items";
            
            // Get all item names from our shop requirements
            List<String> itemNames = shopItemRequirements.keySet().stream()
                    .map(Rs2ShopItem::getItemName)
                    .collect(Collectors.toList());
            
            // Use Rs2Bank utility for banking with return to original position
            boolean success = Rs2Bank.bankItemsAndWalkBackToOriginalPosition(
                itemNames, 
                primaryShopItem.getLocation()
            );
            
            if (success) {
                Microbot.status = "Successfully banked purchased items";
            } else {
                Microbot.status = "Failed to bank purchased items";
            }
            
            return success;
            
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.bankPurchasedItems", e);
            return false;
        }
    }
    
    /**
     * Unnotes items for selling if they are noted and shop doesn't accept noted items.
     * 
     * @return true if items were successfully unnoted, false otherwise
     */
    private boolean unnoteItemsForSelling() {
        try {
            Microbot.status = "Unnoting items for selling";
            
            boolean allItemsUnnoted = true;
            
            for (ShopItemRequirement itemReq : shopItemRequirements.values()) {
                // Check if we have noted items for this shop item
                int notedId = itemReq.getShopItem().getNoteId();
                if (notedId == -1 || !Rs2Inventory.hasItem(notedId)) {
                    continue; // No noted items or item is not noteable
                }
                
                // For unnoting, we typically need to use the items on a banker or shop keeper
                // This is a simplified implementation - in practice you would:
                // 1. Find the nearest banker or the shop keeper
                // 2. Use the noted items on them to unnote
                // 3. Wait for the unnoting to complete
                
                // For now, we'll try to use the shop keeper from our primary shop source
                if (primaryShopItem != null && primaryShopItem.getShopNPC() != null) {
                    // Walk to shop keeper
                    if (!Rs2Walker.walkTo(primaryShopItem.getLocation())) {
                        Microbot.status = "Failed to walk to shop keeper for unnoting " + itemReq.getItemName();
                        allItemsUnnoted = false;
                        continue;
                    }
                    
                    // Find the shop keeper NPC
                    NPC shopKeeperNpc = Rs2Npc.getNpc(primaryShopItem.getShopNpcName());
                    if (shopKeeperNpc == null) {
                        Microbot.status = "Shop keeper not found for unnoting " + itemReq.getItemName();
                        allItemsUnnoted = false;
                        continue;
                    }
                    
                    Rs2NpcModel shopKeeper = new Rs2NpcModel(shopKeeperNpc);
                    
                    // Use noted items on shop keeper (this may need adjustment based on actual game mechanics)
                    if (Rs2Inventory.use(notedId)) {
                        sleep(600); // Game tick
                        if (Rs2Npc.interact(shopKeeper, "Use")) {
                            sleepUntil(() -> !Rs2Inventory.hasItem(notedId) || Rs2Inventory.itemQuantity(itemReq.getShopItem().getItemId()) > 0, 5000);
                            
                            if (Rs2Inventory.hasItem(notedId)) {
                                Microbot.log("Failed to unnote " + itemReq.getItemName());
                                allItemsUnnoted = false;
                            }
                        } else {
                            allItemsUnnoted = false;
                        }
                    } else {
                        allItemsUnnoted = false;
                    }
                }
            }
            
            if (!allItemsUnnoted) {
                Microbot.log("Unnoting feature needs specific implementation for some items");
            }
            
            return allItemsUnnoted; // Return success status based on all items
            
        } catch (Exception e) {
            Microbot.log("Error unnoting items for selling: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Banks coins after selling items.
     * 
     * @return true if coins were successfully banked, false otherwise
     */
    private boolean bankCoinsAfterSelling() {
        try {
            Microbot.status = "Banking coins after selling items";
            
            if (!Rs2Bank.isNearBank(10)) {
                Rs2Bank.walkToBank();
            }
            if (!Rs2Bank.openBank()) {
                Microbot.status = "Failed to open bank for banking coins";
                return false;
            }
            
            Rs2Bank.depositAll("Coins");
            sleepUntil(() -> Rs2Inventory.itemQuantity("Coins") == 0, 5000);
            
            Rs2Bank.closeBank();
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error banking coins after selling: " + e.getMessage());
            Rs2Bank.closeBank();
            return false;
        }
    }
    
    /**
     * Factory method to create a multi-item shop requirement for the same shop.
     * All items must be from the same shop (same location and NPC).
     * 
     * @param primaryShopItem The primary shop item (used for shop location/NPC validation)
     * @param itemRequirements Map of item IDs to their amounts and individual settings
     * @param operation Shop operation type (BUY or SELL)
     * @param requirementType Where this item should be located
     * @param priority Priority level of this item for plugin functionality
     * @param rating Effectiveness rating from 1-10
     * @param description Human-readable description
     * @param scheduleContext When this requirement should be fulfilled
     */
    public static ShopRequirement createMultiItemRequirement(
            Rs2ShopItem primaryShopItem,
            Map<Rs2ShopItem, MultiItemConfig> itemRequirements,
            ShopOperation operation,
            RequirementType requirementType,
            Priority priority,
            int rating,
            String description,
            ScheduleContext scheduleContext) {
        
        Map<Rs2ShopItem, ShopItemRequirement> shopItems = new HashMap<>();
        
        for (Map.Entry<Rs2ShopItem, MultiItemConfig> entry : itemRequirements.entrySet()) {
            Rs2ShopItem shopItem = entry.getKey();
            MultiItemConfig config = entry.getValue();
            
            shopItems.put(shopItem, new ShopItemRequirement(
                shopItem, 
                config.amount, 
                config.stockTolerance  // **UNIFIED SYSTEM**: Use stockTolerance directly
            ));
        }
        
        return new ShopRequirement(shopItems, operation, requirementType, priority, rating, description, scheduleContext);
    }
    
    /**
     * Enhanced configuration class for individual items in a multi-item requirement.
     * Updated to use the unified stock management system.
     */
    public static class MultiItemConfig {
        public final int amount;
        public final int stockTolerance; // **UNIFIED SYSTEM**: Replaces minimumStock + maxQuantityPerVisit
        
        /**
         * Creates a new MultiItemConfig with unified stock management.
         * 
         * @param amount Total amount needed for this item
         * @param stockTolerance Stock tolerance around baseStock (affects both min stock and max per visit)
         */
        public MultiItemConfig(int amount, int stockTolerance) {
            this.amount = amount;
            this.stockTolerance = stockTolerance;
        }
        
        /**
         * Creates a new MultiItemConfig with default stock tolerance.
         * 
         * @param amount Total amount needed for this item
         */
        public MultiItemConfig(int amount) {
            this(amount, 10); // Default tolerance of 10
        }
        
        /**
         * Legacy constructor for backward compatibility.
         * Converts old minimumStock/maxQuantityPerVisit to unified stockTolerance.
         * 
         * @param amount Total amount needed
         * @param minimumStock Legacy minimum stock (ignored in new system)
         * @param maxQuantityPerVisit Legacy max per visit (used as stockTolerance)
         */
        @Deprecated
        public MultiItemConfig(int amount, int minimumStock, int maxQuantityPerVisit) {
            this.amount = amount;
            this.stockTolerance = maxQuantityPerVisit; // Use maxQuantityPerVisit as tolerance
            
            // Log the conversion for debugging
            if (minimumStock != 5) { // 5 was the old default
                Microbot.log("MultiItemConfig: Converting legacy minimumStock=" + minimumStock + 
                           " to unified stockTolerance=" + this.stockTolerance);
            }
        }
        
        @Override
        public String toString() {
            return String.format("MultiItemConfig{amount=%d, stockTolerance=%d}", amount, stockTolerance);
        }
    }
   
     /**
     * Checks if the player has enough coins to buy all required shop items.
     * 
     * @return true if the player has sufficient funds, false otherwise
     */
    public boolean hasSufficientCoinsForRequirements() {
        int totalCost = calculateTotalCost();
        if (totalCost == -1) {
            Microbot.log("Failed to calculate total cost for shop requirements");
            return false;
        }
         
        if (totalCost <= 0) {
            return true;
        }
        final List<Integer> coinIDs = Arrays.asList(ItemID.COINS, ItemID.COINS_2, ItemID.COINS_3, ItemID.COINS_4,ItemID.COINS_5, ItemID.COINS_25, ItemID.COINS_100, ItemID.COINS_250,ItemID.COINS_1000, ItemID.COINS_10000);        
        
        
        
        // Check if player has enough coins in inventory
        int inventoryCoins =Rs2Inventory.itemQuantity(item -> coinIDs.contains(item.getId()));
        boolean hasCoinsInInventory = inventoryCoins >= totalCost;
        
        if (hasCoinsInInventory) {
            return true;
        }
        
        // If not in inventory, check bank
        if (Rs2Bank.isOpen() || Rs2Bank.openBank()) {
            int bankCoins = Rs2Bank.count("Coins");
            boolean hasCoinsInBank = bankCoins >= (totalCost - inventoryCoins);
            
            if (hasCoinsInBank) {
                // We need to use custom withdraw logic since Rs2Bank doesn't have a method
                // to withdraw a specific amount directly
                try {
                    Rs2Bank.withdrawX("Coins", totalCost - inventoryCoins);
                    
                    // Check if we now have enough coins
                    sleepUntil(() -> Rs2Inventory.itemQuantity(item -> coinIDs.contains(item.getId())) >= totalCost, 5000);
                    return Rs2Inventory.itemQuantity(item -> coinIDs.contains(item.getId())) >= totalCost;
                } catch (Exception e) {
                    Microbot.log("Failed to withdraw coins: " + e.getMessage());
                    return false;
                }
            }
        }
        
        Microbot.log("Insufficient coins for shop requirements. Need " + totalCost + " coins.");
        return false;
    }
    
    /**
     * Gets the current stock quantity for a given item name in the shop
     * @param itemName The name of the item to check stock for
     * @return The current stock quantity, or -1 if not found
     */
    private int getShopStock(String itemName) {
        if (Rs2Shop.shopItems == null || Rs2Shop.shopItems.isEmpty()) {
            return -1;
        }
        
        for (Rs2ItemModel item : Rs2Shop.shopItems) {
            if (item.getName().equalsIgnoreCase(itemName)) {
                return item.getQuantity();
            }
        }
        return -1;
    }
    
    /**
     * Attempts to hop to a world that has adequate stock for the items.
     * For buying: finds a world with at least minimumStock available
     * For selling: finds a world where shop isn't oversaturated (below baseStock + maxQuantityPerVisit)
     * 
     * @param minimumStock The minimum stock required for buying, or space available for selling
     * @return true if successfully hopped to a world with adequate stock/space
     */
    @SuppressWarnings("unused")
    private boolean hopToWorldWithStock(int minimumStock) {
        if (!enableWorldHopping || primaryShopItem.getShopType() == Rs2ShopType.GRAND_EXCHANGE) {
            return false; // No hopping needed for GE or if disabled
        }
        
        int maxWorldsToCheck = 5; // Limit world checks to avoid infinite loops
        int worldsChecked = 0;
        
        while (worldsChecked < maxWorldsToCheck) {
            // Hop to next world
            if (!hopWorld()) {
                Microbot.log("Failed to hop world, attempt " + (worldsChecked + 1));
                return false;
            }
            
            worldsChecked++;
            
            // Walk to shop location after hopping
            if (!Rs2Walker.walkTo(primaryShopItem.getLocation())) {
                Microbot.log("Failed to walk to shop after world hop");
                continue;
            }
            
            // Open shop to check stock
            if (!Rs2Shop.openShop(primaryShopItem.getShopNpcName())) {
                Microbot.log("Failed to open shop after world hop");
                continue;
            }
            
            // Wait for shop data to load - ensure shop is ready
            if (!sleepUntil(() -> Rs2Shop.isOpen(), 3000)) {
                Microbot.log("Shop interface failed to stabilize after world hop");
                Rs2Shop.closeShop();
                continue;
            }
            
            // Check stock for all items and see if this world is suitable
            boolean worldIsSuitable = true;
            
            for (ShopItemRequirement itemReq : shopItemRequirements.values()) {
                if (itemReq.isCompleted()) {
                    continue; // Skip completed items
                }
                
                int currentStock = getShopStock(itemReq.getItemName());
                if (currentStock == -1) {
                    worldIsSuitable = false;
                    break; // Item not found in shop, try next world
                }
                
                if (operation == ShopOperation.BUY) {
                    // For buying: need at least minimumStock available for this item
                    if (currentStock < itemReq.getMinimumStockForBuying()) {
                        worldIsSuitable = false;
                        break;
                    }
                } else if (operation == ShopOperation.SELL) {
                    // For selling: check if shop isn't oversaturated for this item
                    int maxDesiredStock = itemReq.getShopItem().getBaseStock() + itemReq.getMaxQuantityPerVisit();
                    if (currentStock > maxDesiredStock) {
                        worldIsSuitable = false;
                        break;
                    }
                }
            }
            
            if (worldIsSuitable) {
                Microbot.log("Found suitable world with good stock/space after " + worldsChecked + " hops");
                Rs2Shop.closeShop();
                return true;
            }
            
            Rs2Shop.closeShop();
            Microbot.log("World " + worldsChecked + " doesn't have suitable stock for all items, continuing search...");
        }
        
        Microbot.log("Failed to find suitable world after checking " + maxWorldsToCheck + " worlds");
        return false;
    }
    
    // ===== GRAND EXCHANGE SLOT MANAGEMENT =====
    
    /**
     * Ensures sufficient Grand Exchange slots are available for the required operations.
     * Implements proper slot allocation to prevent exceeding the 8-slot limit.
     * 
     * @param requiredSlots Number of slots needed for pending operations
     * @return true if sufficient slots can be made available, false otherwise
     */
    private boolean ensureGrandExchangeSlots(int requiredSlots) {
        try {
            // Check current slot availability
            int availableSlots = Rs2GrandExchange.getAvailableSlotsCount();
            
            if (availableSlots >= requiredSlots) {
                Microbot.status = "Sufficient GE slots available: " + availableSlots + "/" + requiredSlots;
                return true;
            }
            
            Microbot.status = "Need " + requiredSlots + " slots, have " + availableSlots + " - freeing slots";
            
            // Try to collect completed offers first (most efficient)
            if (Rs2GrandExchange.hasBoughtOffer() || Rs2GrandExchange.hasSoldOffer()) {
                Rs2GrandExchange.collectAll(enableBanking);
                sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() >= requiredSlots, 5000);
                
                if (Rs2GrandExchange.getAvailableSlotsCount() >= requiredSlots) {
                    Microbot.status = "Freed sufficient slots by collecting offers";
                    return true;
                }
            }
            
            // If still insufficient, abort pending offers as last resort
            if (Rs2GrandExchange.getAvailableSlotsCount() < requiredSlots) {
                Microbot.status = "Aborting pending offers to free slots";
                Rs2GrandExchange.abortAllOffers(enableBanking);
                sleepUntil(() -> Rs2GrandExchange.getAvailableSlotsCount() >= requiredSlots, 8000);
            }
            
            int finalAvailable = Rs2GrandExchange.getAvailableSlotsCount();
            if (finalAvailable >= requiredSlots) {
                Microbot.status = "Successfully freed " + finalAvailable + " GE slots";
                return true;
            } else {
                Microbot.status = "Failed to free sufficient GE slots: need " + requiredSlots + ", have " + finalAvailable;
                return false;
            }
            
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.ensureGrandExchangeSlots", e);
            return false;
        }
    }
    
    /**
     * Allocates a Grand Exchange slot for tracking purposes.
     * 
     * @param itemName Name of the item using the slot
     * @param slots Number of slots to allocate
     */
    /**
     * Cancels any existing Grand Exchange offers for the specified item ID to prevent duplicates.
     * This ensures we don't place multiple offers for the same item accidentally.
     * 
     * @param itemId The item ID to check for existing offers
     * @param itemName The item name for logging purposes
     * @return true if any offers were cancelled, false otherwise
     */
    private boolean cancelDuplicateOffers(int itemId, String itemName) {
        boolean cancelledAny = false;
        
        try {
            GrandExchangeOffer[] offers = Microbot.getClient().getGrandExchangeOffers();
            
            for (int slotIndex = 0; slotIndex < offers.length; slotIndex++) {
                final int finalSlotIndex = slotIndex; // Make effectively final for lambda
                GrandExchangeOffer offer = offers[slotIndex];
                
                // Skip empty slots
                if (offer == null || offer.getItemId() == 0) {
                    continue;
                }
                
                // Check if this offer is for our item
                if (offer.getItemId() == itemId) {
                    GrandExchangeSlots slot = GrandExchangeSlots.values()[slotIndex];
                    Microbot.status = "Cancelling existing offer for " + itemName + " in slot " + slot.ordinal();
                    
                    // Cancel the offer using item name (abortOffer method signature)
                    Rs2GrandExchange.abortOffer(itemName, enableBanking);
                    sleepUntil(() -> {
                        GrandExchangeOffer[] updatedOffers = Microbot.getClient().getGrandExchangeOffers();
                        return updatedOffers[finalSlotIndex] == null || updatedOffers[finalSlotIndex].getItemId() == 0;
                    }, 3000);
                    
                    cancelledAny = true;
                    sleep(Constants.GAME_TICK_LENGTH); // Brief pause between cancellations
                }
            }
            
            if (cancelledAny) {
                // Wait a moment for the interface to update after cancellations
                sleep(Constants.GAME_TICK_LENGTH * 2);
                Rs2GrandExchange.collectAllToBank(); // Collect any cancelled offers to bank
            }
            
        } catch (Exception e) {
            log.warn("Error checking for duplicate offers for {}: {}", itemName, e.getMessage());
        }
        
        return cancelledAny;
    }       
    
}
