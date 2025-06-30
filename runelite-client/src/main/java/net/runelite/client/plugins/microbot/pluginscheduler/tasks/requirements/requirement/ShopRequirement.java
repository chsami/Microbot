package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement;

import lombok.Getter;
import lombok.Setter;
import lombok.EqualsAndHashCode;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.VoxPlugins.util.VoxSylvaeUtil;
import net.runelite.client.plugins.microbot.VoxPlugins.util.models.sources.ShopSource;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.slf4j.event.Level;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Represents a requirement to buy or sell items from/to a shop.
 * This extends Requirement directly with additional properties specific to shop operations.
 * Enhanced with BanksShopper patterns for world hopping, stock tracking, and quantity management.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
public class ShopRequirement extends Requirement {
    
    // Pattern to detect charged items with numbers in parentheses, e.g., "Amulet of glory(6)"
    protected static final Pattern CHARGED_ITEM_PATTERN = Pattern.compile(".*\\((\\d+)\\)$");
    
    /**
     * The amount of items to buy or sell.
     * Positive values for buying, can be used for selling quantity as well.
     */
    private final int amount;
    
    /**
     * Information about the shop where this item can be purchased or sold.
     */
    private final ShopSource shopSource;
    
    /**
     * The shop operation type - either BUY or SELL.
     */
    private final ShopOperation operation;
    
    /**
     * Whether to handle noted items when selling (unnote them if needed).
     */
    @Setter
    private boolean handleNotedItems = true;
   
    /**
     * Minimum stock level required before attempting purchase.
     * If stock falls below this level, world hopping will be triggered.
     */
    @Setter
    private int minimumStock = 5;
    
    /**
     * Maximum quantity to buy per shop visit to avoid large price changes.
     */
    @Setter
    private int maxQuantityPerVisit = 10;
    
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
    private boolean enableBanking = false;
    
    public String getName() {
        return getIds().isEmpty() ? "No Shop Item" : VoxSylvaeUtil.getItemName(getIds().get(0));
    }
    
    /**
     * Returns a multi-line display string with detailed shop requirement information.
     * Uses StringBuilder with tabs for proper formatting.
     * 
     * @return A formatted string containing shop requirement details
     */
    public String displayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Enhanced Shop Requirement Details ===\n");
        sb.append("Name:\t\t\t").append(getName()).append("\n");
        sb.append("Type:\t\t\t").append(getRequirementType().name()).append("\n");
        sb.append("Priority:\t\t").append(getPriority().name()).append("\n");
        sb.append("Rating:\t\t\t").append(getRating()).append("/10\n");
        sb.append("Operation:\t\t").append(operation.name()).append("\n");
        sb.append("Amount:\t\t\t").append(amount).append("\n");
        sb.append("Item IDs:\t\t").append(getIds().toString()).append("\n");
        sb.append("Description:\t\t").append(getDescription() != null ? getDescription() : "No description").append("\n");
        
        // Enhanced shopping configuration
        sb.append("\n--- Shopping Configuration ---\n");
        sb.append("Minimum Stock:\t\t").append(minimumStock).append(" items\n");
        sb.append("Max Per Visit:\t\t").append(maxQuantityPerVisit).append(" items\n");
        sb.append("World Hopping:\t\t").append(enableWorldHopping ? "Enabled" : "Disabled").append("\n");
        sb.append("Use Next World:\t\t").append(useNextWorld ? "Yes" : "Random").append("\n");
        sb.append("Auto Banking:\t\t").append(enableBanking ? "Enabled" : "Disabled").append("\n");
        sb.append("Handle Noted:\t\t").append(handleNotedItems ? "Enabled" : "Disabled").append("\n");
        
        if (shopSource != null) {
            sb.append("\n--- Shop Source Information ---\n");
            sb.append(shopSource.displayString());
        } else {
            sb.append("Shop Source:\t\tNot specified\n");
        }
        
        return sb.toString();
    }

    /**
     * Creates a new shop item requirement with schedule context.
     * 
     * @param itemIds List of item IDs that can fulfill this requirement, ordered by preference
     * @param amount Amount to buy or sell
     * @param operation Shop operation type (BUY or SELL)
     * @param requirementType Where this item should be located (equipment slot, inventory, or either)
     * @param priority Priority level of this item for plugin functionality
     * @param rating Effectiveness rating from 1-10 (10 being most effective)
     * @param description Human-readable description of the item's purpose    
     * @param scheduleContext When this requirement should be fulfilled
     * @param shopSource Information about the shop where this item can be purchased/sold
     */
    public ShopRequirement(
            List<Integer> itemIds,
            int amount,
            ShopOperation operation,
            RequirementType requirementType,
            Priority priority,
            int rating,
            String description,
            ScheduleContext scheduleContext,
            ShopSource shopSource) {
        
        super(requirementType, priority, rating, description, itemIds, scheduleContext);
        
        this.amount = amount;
        this.operation = operation;
        this.shopSource = shopSource;
    }
    /**
     * Creates a new shop item requirement.
     * Defaults to ScheduleContext.BOTH for backwards compatibility.
     * 
     * @param itemIds List of item IDs that can fulfill this requirement, ordered by preference
     * @param amount Amount to buy or sell
     * @param operation Shop operation type (BUY or SELL)
     * @param requirementType Where this item should be located (equipment slot, inventory, or either)
     * @param priority Priority level of this item for plugin functionality
     * @param rating Effectiveness rating from 1-10 (10 being most effective)
     * @param description Human-readable description of the item's purpose    
     * @param shopSource Information about the shop where this item can be purchased/sold
     */
    public ShopRequirement(
            List<Integer> itemIds,
            int amount,
            ShopOperation operation,
            RequirementType requirementType,
            Priority priority,
            int rating,
            String description,
            ShopSource shopSource) {
        
        super(requirementType, priority, rating, description, itemIds, ScheduleContext.BOTH);
        
        this.amount = amount;
        this.operation = operation;
        this.shopSource = shopSource;
    }
    /**
     * Creates a new shop item requirement with single item ID.
     */
    public ShopRequirement(
            int itemId,
            int amount,
            ShopOperation operation,
            RequirementType requirementType,
            Priority priority,
            int rating,
            String description,
            ShopSource shopSource) {
        
        this(Arrays.asList(itemId), amount, operation, requirementType, priority, rating, description, shopSource);
    }
    
    /**
     * Simplified constructor for common use case with existing ShopSource
     */
    public ShopRequirement(
            List<Integer> itemIds,
            int amount,
            ShopOperation operation,
            RequirementType requirementType,
            Priority priority,
            String description,
            ShopSource shopSource) {
        
        super(requirementType, priority, 5, description, itemIds, ScheduleContext.BOTH);
        
        this.amount = amount;
        this.operation = operation;
        this.shopSource = shopSource;
    }
    
    /**
     * Convenience constructor for simple shop item requirements using existing ShopSource
     */
    public ShopRequirement(
            int itemId,
            int amount,
            ShopOperation operation,
            ShopSource shopSource) {
        
        this(
            Arrays.asList(itemId),
            amount,
            operation,
            RequirementType.INVENTORY,
            Priority.MANDATORY,
            operation.name() + " item from shop: " + shopSource.getShopName(),
            shopSource
        );
    }
    
    /**
     * Enhanced shopping method with BanksShopper patterns.
     * Includes world hopping, stock tracking, quantity management, and banking.
     * 
     * @return true if the purchase was successful, false otherwise
     */
    private boolean buyFromShop() {
        return buyFromShop("Purchasing " + getName() + " from " + shopSource.getShopName());
    }
    
    /**
     * Enhanced shopping method with custom status message.
     * Implements BanksShopper patterns for optimal shopping experience.
     * 
     * @param customBuyMessage Custom message to display during purchase
     * @return true if the purchase was successful, false otherwise
     */
    private boolean buyFromShop(String customBuyMessage) {
        if (shopSource == null) {
            Microbot.log("No shop source specified for " + getName());
            return false;
        }
        
        try {
            Microbot.status = customBuyMessage;
            
            // Check if we already have enough
            if (Rs2Inventory.count(getIds().get(0)) >= amount) {
                Microbot.status = "Already have " + amount + "x " + getName();
                return true;
            }
            
            int totalPurchased = 0;
            int remainingToBuy = amount - Rs2Inventory.count(getIds().get(0));
            int maxAttempts = 5;
            int attempts = 0;
            
            while (remainingToBuy > 0 && attempts < maxAttempts) {
                attempts++;
                
                // Walk to the shop
                if (!Rs2Walker.walkTo(shopSource.getLocation())) {
                    Microbot.status = "Failed to walk to shop for " + getName();
                    sleep(1000, 2000);
                    continue;
                }
                
                // Open shop interface
                if (!Rs2Shop.openShop(shopSource.getShopNPC().getName())) {
                    Microbot.status = "Failed to open shop for " + getName();
                    sleep(1000, 2000);
                    continue;
                }
                
                // Wait for shop data to update (BanksShopper pattern)
                Rs2Random.waitEx(1800, 300);
                
                // Check stock levels
                if (!Rs2Shop.hasMinimumStock(getName(), minimumStock)) {
                    Microbot.status = "Insufficient stock for " + getName() + " (minimum: " + minimumStock + ")";
                    Rs2Shop.closeShop();
                    
                    if (enableWorldHopping) {
                        if (hopWorld()) {
                            attempts--; // Don't count world hop as failed attempt
                            continue;
                        } else {
                            Microbot.status = "Failed to hop worlds - insufficient stock";
                            return false;
                        }
                    } else {
                        return false;
                    }
                }
                
                // Calculate quantity to purchase this visit
                int quantityThisVisit = Math.min(remainingToBuy, maxQuantityPerVisit);
                quantityThisVisit = Math.min(quantityThisVisit, 28 - Rs2Inventory.count()); // Don't exceed inventory space
                
                if (quantityThisVisit <= 0) {
                    Microbot.status = "Inventory full - need to bank items";
                    Rs2Shop.closeShop();
                    
                    if (enableBanking) {
                        if (bankItems()) {
                            continue; // Try again after banking
                        }
                    }
                    return false;
                }
                
                // Purchase items using optimal buying method
                boolean purchaseSuccessful = false;
                try {
                    if (Rs2Shop.hasStock(getName())) {
                        purchaseSuccessful = Rs2Shop.buyItem(getName(), String.valueOf(quantityThisVisit));
                        if (purchaseSuccessful) {
                            totalPurchased += quantityThisVisit;
                            remainingToBuy -= quantityThisVisit;
                            
                            Microbot.status = "Successfully purchased " + quantityThisVisit + "x " + getName() + 
                                             " (" + totalPurchased + "/" + amount + " total)";
                        }
                    } else {
                        Microbot.status = getName() + " is not in stock";
                        purchaseSuccessful = false;
                    }
                    
                } catch (Exception e) {
                    Microbot.logStackTrace("ShopRequirement.buyFromShop - Purchase failed", e);
                    purchaseSuccessful = false;
                }
                
                // Wait between purchases (human-like behavior)
                Rs2Random.waitEx(900, 300);
                Rs2Shop.closeShop();
                
                if (!purchaseSuccessful) {
                    sleep(2000, 3000);
                    continue;
                }
                
                // Check if we should bank items
                if (enableBanking && Rs2Inventory.count() > 20) {
                    if (!bankItems()) {
                        Microbot.status = "Failed to bank items - continuing without banking";
                    }
                }
                
                // Brief pause between shop visits
                sleep(1000, 2000);
            }
            
            // Final check
            boolean success = Rs2Inventory.count(getIds().get(0)) >= amount;
            if (success) {
                Microbot.status = "Successfully completed purchase of " + totalPurchased + "x " + getName();
            } else {
                Microbot.status = "Purchase incomplete: " + totalPurchased + "/" + amount + " " + getName();
            }
            
            return success;
            
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.buyFromShop", e);
            return false;
        }
    }
    
    /**
     * Enhanced selling method with BanksShopper patterns.
     * 
     * @return true if the sale was successful, false otherwise
     */
    private boolean sellToShop() {
        if (shopSource == null) {
            Microbot.log("No shop source specified for selling " + getName());
            return false;
        }
        
        try {
            Microbot.status = "Selling " + getName() + " to " + shopSource.getShopName();
            
            // Check if we have items to sell
            int itemCount = Rs2Inventory.count(getIds().get(0));
            if (itemCount == 0) {
                Microbot.status = "No " + getName() + " to sell";
                return true; // Not an error condition
            }
            
            // Walk to the shop
            if (!Rs2Walker.walkTo(shopSource.getLocation())) {
                Microbot.status = "Failed to walk to shop for selling " + getName();
                return false;
            }
            
            // Open shop interface
            if (!Rs2Shop.openShop(shopSource.getShopNPC().getName())) {
                Microbot.status = "Failed to open shop for selling " + getName();
                return false;
            }
            
            // Wait for shop data to update
            Rs2Random.waitEx(1800, 300);
            
            // Check if shop is full
            if (Rs2Shop.isFull()) {
                Microbot.status = "Shop is full - cannot sell " + getName();
                Rs2Shop.closeShop();
                
                if (enableWorldHopping) {
                    if (hopWorld()) {
                        return sellToShop(); // Retry in new world
                    }
                }
                return false;
            }
            
            // Sell items using inventory method (standard approach for selling)
            boolean success = Rs2Inventory.sellItem(getName(), String.valueOf(itemCount));
            
            if (success) {
                Microbot.status = "Successfully sold " + itemCount + "x " + getName();
                Rs2Random.waitEx(900, 300);
            } else {
                Microbot.status = "Failed to sell " + getName();
            }
            
            Rs2Shop.closeShop();
            return success;
            
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.sellToShop", e);
            return false;
        }
    }
    
    /**
     * Hops to a new world using BanksShopper patterns.
     * 
     * @return true if world hop was successful, false otherwise
     */
    private boolean hopWorld() {
        try {
            Microbot.status = "Stock level low - hopping worlds";
            Rs2Shop.closeShop();
            
            // Wait to avoid "finish what you're doing" message
            Rs2Random.waitEx(3200, 800);
            
            // Get next world
            int world = useNextWorld ? 
                Login.getNextWorld(Rs2Player.isMember()) : 
                Login.getRandomWorld(Rs2Player.isMember());
            
            boolean hopped = Microbot.hopToWorld(world);
            if (!hopped) {
                Microbot.status = "Failed to hop to world " + world;
                return false;
            }
            
            // Wait for hop to complete
            sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING, 5000);
            sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN, 10000);
            
            Microbot.status = "Successfully hopped to world " + world;
            sleep(2000, 3000); // Additional wait for world to stabilize
            
            return true;
            
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.hopWorld", e);
            return false;
        }
    }
    
    /**
     * Banks purchased items if banking is enabled.
     * 
     * @return true if banking was successful, false otherwise
     */
    private boolean bankItems() {
        try {
            Microbot.status = "Banking purchased items";
            
            // Use Rs2Bank utility for banking
            boolean success = Rs2Bank.bankItemsAndWalkBackToOriginalPosition(
                Arrays.asList(getName()), 
                shopSource.getLocation()
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
     * Gets the primary item ID (first in the list).
     * 
     * @return The primary item ID, or -1 if there are no item IDs
     */
    private int getPrimaryItemId() {
        return getIds().isEmpty() ? -1 : getIds().get(0);
    }
    
    /**
     * Calculates the total cost for purchasing the required amount with dynamic pricing.
     * Uses ShopSource dynamic pricing calculations based on stock levels.
     * 
     * @return The total cost in coins, or -1 if calculation failed
     */
    private int calculateTotalCost() {
        if (shopSource == null) {
            return -1;
        }
        
        try {
            int currentCount = Rs2Inventory.count(getPrimaryItemId());
            int amountToBuy = Math.max(0, getAmount() - currentCount);
            
            if (amountToBuy <= 0) {
                return 0;
            }
            
            return ShopSource.getCostForBuyingX(shopSource, getPrimaryItemId(), amountToBuy);
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.calculateTotalCost", e);
            return -1;
        }
    }
    
    /**
     * Calculates the total value for selling items with dynamic pricing.
     * Uses ShopSource dynamic pricing calculations based on stock levels.
     * 
     * @return The total sell value in coins, or -1 if calculation failed
     */
    private int calculateTotalSellValue() {
        if (shopSource == null) {
            return -1;
        }
        
        try {
            int currentCount = Rs2Inventory.count(getPrimaryItemId());
            
            if (currentCount <= 0) {
                return 0;
            }
            
            return ShopSource.getCostForSellingX(shopSource, getPrimaryItemId(), currentCount);
        } catch (Exception e) {
            Microbot.logStackTrace("ShopRequirement.calculateTotalSellValue", e);
            return -1;
        }
    }
    
    /**
     * Estimates the number of world hops needed based on stock levels and requirements.
     * 
     * @return Estimated world hops needed, or -1 if cannot estimate
     */
    private int estimateWorldHopsNeeded() {
        if (shopSource == null || shopSource.getNumberInStock() <= 0) {
            return -1;
        }
        
        int remainingToBuy = Math.max(0, amount - Rs2Inventory.count(getPrimaryItemId()));
        int availablePerWorld = Math.max(shopSource.getNumberInStock() - minimumStock, 0);
        
        if (availablePerWorld <= 0) {
            return -1; // Insufficient stock per world
        }
        
        return (int) Math.ceil((double) remainingToBuy / Math.min(availablePerWorld, maxQuantityPerVisit));
    }
    
    /**
     * Implements the abstract fulfillRequirement method from the base Requirement class.
     * Handles both buying and selling operations based on the operation type.
     * 
     * @return true if the requirement was successfully fulfilled, false otherwise
     */
    @Override
    public boolean fulfillRequirement() {
        try {
            if (Microbot.getClient().isClientThread()) {
                Microbot.log("Please run fulfillRequirement() on a non-client thread.", Level.ERROR);
                return false;
            }
            Microbot.status = "Fulfilling shop requirement: " + operation.name() + " " + getName();
            
            boolean success = false;
            
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
     * Handles buy operations with proper banking logic for stackable and non-stackable items.
     * 
     * @return true if the buy operation was successful, false otherwise
     */
    private boolean handleBuyOperation() {
        try {
            // Check if we already have enough items
            if (hasRequiredAmount()) {
                Microbot.status = "Already have required amount of " + getName();
                return true;
            }
            
            // Validate item can be bought (is tradeable)
            if (!VoxSylvaeUtil.isTradeable(getPrimaryItemId())) {
                Microbot.log("Item " + getName() + " is not tradeable - cannot buy from shop");
                return !isMandatory();
            }
            
            // Get coins from bank if needed
            if (enableBanking) {
                if (!ensureSufficientCoins()) {
                    Microbot.status = "Failed to get sufficient coins for " + getName();
                    if (isMandatory()) {
                        return false;
                    }
                }
            }
            
            // Check if item is stackable to determine banking strategy
            boolean isStackable = VoxSylvaeUtil.isStackableOnClientThread(getPrimaryItemId());
            
            if (isStackable) {
                // Stackable items: Can buy large quantities, world hop when needed
                return handleStackableBuyOperation();
            } else {
                // Non-stackable items: Buy in batches, bank between batches
                return handleNonStackableBuyOperation();
            }
            
        } catch (Exception e) {
            Microbot.log("Error in buy operation for " + getName() + ": " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Handles buying stackable items (can buy large quantities without banking).
     * 
     * @return true if successful, false otherwise
     */
    private boolean handleStackableBuyOperation() {
        try {
            Microbot.status = "Buying stackable item: " + getName();
            
            // For stackable items, we can buy the full amount
            // Just need to world hop if stock is insufficient
            boolean purchaseSuccess = buyFromShop();
            
            // Bank the purchased stackable items if banking is enabled
            if (purchaseSuccess && enableBanking) {
                if (!bankPurchasedItems()) {
                    Microbot.status = "Purchase successful but failed to bank stackable items for " + getName();
                    // Don't fail the requirement just because banking failed
                }
            }
            
            return purchaseSuccess;
            
        } catch (Exception e) {
            Microbot.log("Error in stackable buy operation: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Handles buying non-stackable items (requires banking between batches).
     * 
     * @return true if successful, false otherwise
     */
    private boolean handleNonStackableBuyOperation() {
        try {
            Microbot.status = "Buying non-stackable item: " + getName();
            
            int totalPurchased = 0;
            int remainingToBuy = amount - Rs2Inventory.count(getPrimaryItemId());
            int maxAttempts = 10; // Allow more attempts for non-stackable items
            int attempts = 0;
            
            while (remainingToBuy > 0 && attempts < maxAttempts) {
                attempts++;
                
                // Calculate how much we can buy this batch (inventory space limited)
                int freeSlots = 28 - Rs2Inventory.count();
                int batchSize = Math.min(remainingToBuy, Math.min(freeSlots, maxQuantityPerVisit));
                
                if (batchSize <= 0) {
                    // Inventory full, bank items and return to shop
                    if (enableBanking) {
                        if (!bankPurchasedItems()) {
                            Microbot.status = "Failed to bank items during non-stackable purchase";
                            return false;
                        }
                        continue; // Try again after banking
                    } else {
                        Microbot.status = "Inventory full and banking disabled for " + getName();
                        return false;
                    }
                }
                
                // Perform the purchase for this batch
                int oldCount = Rs2Inventory.count(getPrimaryItemId());
                boolean batchSuccess = buyFromShopBatch(batchSize);
                
                if (batchSuccess) {
                    int purchased = Rs2Inventory.count(getPrimaryItemId()) - oldCount;
                    totalPurchased += purchased;
                    remainingToBuy -= purchased;
                    
                    Microbot.status = "Batch purchase successful: " + purchased + "x " + getName() + 
                                     " (" + totalPurchased + "/" + amount + " total)";
                } else {
                    Microbot.status = "Batch purchase failed for " + getName();
                    sleep(2000, 3000); // Wait before retry
                }
            }
            
            // Final banking if items remain in inventory
            if (enableBanking && Rs2Inventory.count(getPrimaryItemId()) > 0) {
                if (!bankPurchasedItems()) {
                    Microbot.status = "Final banking failed for " + getName();
                    // Don't fail the requirement just because banking failed
                }
            }
            
            return totalPurchased >= amount;
            
        } catch (Exception e) {
            Microbot.log("Error in non-stackable buy operation: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Handles sell operations with proper banking logic for noted and unnoted items.
     * 
     * @return true if the sell operation was successful, false otherwise
     */
    private boolean handleSellOperation() {
        try {
            // Validate item can be sold (is tradeable)
            if (!VoxSylvaeUtil.isTradeable(getPrimaryItemId())) {
                Microbot.log("Item " + getName() + " is not tradeable - cannot sell to shop");
                return !isMandatory();
            }
            
            // Get items from bank at the beginning if banking is enabled
            if (enableBanking) {
                if (!getItemsFromBankForSelling()) {
                    Microbot.status = "Failed to get " + getName() + " from bank for selling";
                    return !isMandatory(); // Not an error for optional requirements
                }
            }
            
            // Check if we have items to sell in inventory
            int inventoryCount = Rs2Inventory.count(getPrimaryItemId());
            if (inventoryCount == 0) {
                Microbot.status = "No " + getName() + " to sell";
                return !isMandatory(); // Not an error for optional requirements
            }
            
            // Check if items are noted and if they can be sold as noted
            boolean hasNotedItems = Rs2Inventory.hasNotedItem(getName());
            boolean isNoteable = VoxSylvaeUtil.isNoteable(getPrimaryItemId());
            
            if (hasNotedItems) {
                if (handleNotedItems && !isNoteable) {
                    // Items are noted but shouldn't be - this is an error state
                    Microbot.log("Item " + getName() + " is noted but is not noteable - inconsistent state");
                    return false;
                }
                
                if (!handleNotedItems && !unnoteItemsForSelling()) {
                    Microbot.status = "Failed to unnote " + getName() + " for selling";
                    return false;
                }
            }
            
            // Perform the sale
            boolean sellSuccess = sellToShop();
            
            // After selling, bank coins if banking is enabled
            if (sellSuccess && enableBanking) {
                if (!bankCoinsAfterSelling()) {
                    Microbot.status = "Sale successful but failed to bank coins";
                    // Don't fail the requirement just because banking failed
                }
            }
            
            return sellSuccess;
            
        } catch (Exception e) {
            Microbot.log("Error in sell operation for " + getName() + ": " + e.getMessage());
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
            
            int currentCoins = Rs2Inventory.count("Coins");
            if (currentCoins >= totalCost) {
                return true; // Already have enough coins
            }
            
            Microbot.status = "Getting coins from bank for " + getName();
            
            if (!Rs2Bank.openBank()) {
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
            sleepUntil(() -> Rs2Inventory.count("Coins") >= totalCost, 5000);
            
            Rs2Bank.closeBank();
            return Rs2Inventory.count("Coins") >= totalCost;
            
        } catch (Exception e) {
            Microbot.log("Error ensuring sufficient coins: " + e.getMessage());
            Rs2Bank.closeBank();
            return false;
        }
    }
    
    /**
     * Gets items from bank specifically for selling operations.
     * Handles noted items properly for selling.
     * 
     * @return true if items were successfully retrieved, false otherwise
     */
    private boolean getItemsFromBankForSelling() {
        try {
            Microbot.status = "Getting " + getName() + " from bank for selling";
            
            if (!Rs2Bank.openBank()) {
                Microbot.status = "Failed to open bank for " + getName();
                return false;
            }
            
            int currentCount = Rs2Inventory.count(getPrimaryItemId());
            int neededCount = amount - currentCount;
            
            if (neededCount <= 0) {
                Rs2Bank.closeBank();
                return true; // Already have enough
            }
            
            // Check if item is available in bank (either noted or unnoted)
            boolean hasUnnoted = Rs2Bank.hasItem(getName());
            boolean hasNoted = false;
            
            // Check for noted version if item is noteable
            if (VoxSylvaeUtil.isNoteable(getPrimaryItemId())) {
                int notedId = VoxSylvaeUtil.getLinkedNoteId(getPrimaryItemId());
                if (notedId != -1) {
                    hasNoted = Rs2Bank.hasItem(notedId);
                }
            }
            
            if (!hasUnnoted && !hasNoted) {
                Microbot.status = "Bank does not contain " + getName() + " (noted or unnoted)";
                Rs2Bank.closeBank();
                return false;
            }
            
            // Prefer noted items for selling (more efficient, stackable)
            if (hasNoted && handleNotedItems) {
                int notedId = VoxSylvaeUtil.getLinkedNoteId(getPrimaryItemId());
                Rs2Bank.withdrawX(notedId, neededCount);
                sleepUntil(() -> Rs2Inventory.count(notedId) >= neededCount, 5000);
            } else if (hasUnnoted) {
                Rs2Bank.withdrawX(getName(), neededCount);
                sleepUntil(() -> Rs2Inventory.count(getPrimaryItemId()) >= neededCount, 5000);
            }
            
            Rs2Bank.closeBank();
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error getting items from bank for selling: " + e.getMessage());
            Rs2Bank.closeBank();
            return false;
        }
    }
    
    /**
     * Purchases a specific batch size of items from the shop.
     * 
     * @param batchSize The number of items to buy in this batch
     * @return true if the batch purchase was successful, false otherwise
     */
    private boolean buyFromShopBatch(int batchSize) {
        try {
            if (shopSource == null) {
                Microbot.log("No shop source specified for batch purchase of " + getName());
                return false;
            }
            
            // Walk to the shop
            if (!Rs2Walker.walkTo(shopSource.getLocation())) {
                Microbot.status = "Failed to walk to shop for batch purchase of " + getName();
                return false;
            }
            
            // Open shop interface
            if (!Rs2Shop.openShop(shopSource.getShopNPC().getName())) {
                Microbot.status = "Failed to open shop for batch purchase of " + getName();
                return false;
            }
            
            // Wait for shop data to update
            Rs2Random.waitEx(1800, 300);
            
            // Check stock levels
            if (!Rs2Shop.hasMinimumStock(getName(), Math.min(batchSize, minimumStock))) {
                Microbot.status = "Insufficient stock for batch purchase of " + getName();
                Rs2Shop.closeShop();
                
                if (enableWorldHopping) {
                    return hopWorld(); // Try world hopping
                } else {
                    return false;
                }
            }
            
            // Perform the purchase
            boolean success = Rs2Shop.buyItem(getName(), String.valueOf(batchSize));
            Rs2Random.waitEx(900, 300);
            Rs2Shop.closeShop();
            
            return success;
            
        } catch (Exception e) {
            Microbot.log("Error in batch purchase: " + e.getMessage());
            Rs2Shop.closeShop();
            return false;
        }
    }
    
    /**
     * Banks purchased items and returns to the shop location.
     * 
     * @return true if banking was successful, false otherwise
     */
    private boolean bankPurchasedItems() {
        try {
            Microbot.status = "Banking purchased " + getName();
            
            if (shopSource == null) {
                Microbot.log("No shop source for banking return location");
                return false;
            }
            
            // Use Rs2Bank utility for banking with return to original position
            boolean success = Rs2Bank.bankItemsAndWalkBackToOriginalPosition(
                Arrays.asList(getName()), 
                shopSource.getLocation()
            );
            
            if (success) {
                Microbot.status = "Successfully banked " + getName();
            } else {
                Microbot.status = "Failed to bank " + getName();
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
            Microbot.status = "Unnoting " + getName() + " for selling";
            
            // Check if we have noted items
            int notedId = VoxSylvaeUtil.getLinkedNoteId(getPrimaryItemId());
            if (notedId == -1 || !Rs2Inventory.hasItem(notedId)) {
                return true; // No noted items or item is not noteable
            }
            
            // For unnoting, we typically need to use the items on a banker or shop keeper
            // This is a simplified implementation - in practice you would:
            // 1. Find the nearest banker or the shop keeper
            // 2. Use the noted items on them to unnote
            // 3. Wait for the unnoting to complete
            
            // For now, we'll try to use the shop keeper from our shop source
            if (shopSource != null && shopSource.getShopNPC() != null) {
                // Walk to shop keeper
                if (!Rs2Walker.walkTo(shopSource.getLocation())) {
                    Microbot.status = "Failed to walk to shop keeper for unnoting";
                    return false;
                }
                
                // Find the shop keeper NPC
                NPC shopKeeperNpc = Rs2Npc.getNpc(shopSource.getShopNPC().getName());
                if (shopKeeperNpc == null) {
                    Microbot.status = "Shop keeper not found for unnoting";
                    return false;
                }
                
                Rs2NpcModel shopKeeper = new Rs2NpcModel(shopKeeperNpc);
                
                // Use noted items on shop keeper (this may need adjustment based on actual game mechanics)
                if (Rs2Inventory.use(notedId)) {
                    sleep(600); // Game tick
                    if (Rs2Npc.interact(shopKeeper, "Use")) {
                        sleepUntil(() -> !Rs2Inventory.hasItem(notedId) || Rs2Inventory.count(getPrimaryItemId()) > 0, 5000);
                        return !Rs2Inventory.hasItem(notedId) || Rs2Inventory.count(getPrimaryItemId()) > 0;
                    }
                }
            }
            
            Microbot.log("Unnoting feature needs specific implementation for " + getName());
            return true; // Don't fail the requirement for now
            
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
            Microbot.status = "Banking coins after selling " + getName();
            
            if (!Rs2Bank.openBank()) {
                Microbot.status = "Failed to open bank for banking coins";
                return false;
            }
            
            Rs2Bank.depositAll("Coins");
            sleepUntil(() -> Rs2Inventory.count("Coins") == 0, 5000);
            
            Rs2Bank.closeBank();
            return true;
            
        } catch (Exception e) {
            Microbot.log("Error banking coins after selling: " + e.getMessage());
            Rs2Bank.closeBank();
            return false;
        }
    }
    
    /**
     * Checks if we already have the required amount of items.
     * 
     * @return true if we have enough items, false otherwise
     */
    private boolean hasRequiredAmount() {
        for (Integer itemId : getIds()) {
            if (Rs2Inventory.count(itemId) >= amount) {
                return true;
            }
        }
        return false;
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
        int inventoryCoins =Rs2Inventory.count(item -> coinIDs.contains(item.getId()));
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
                    sleepUntil(() -> Rs2Inventory.count(item -> coinIDs.contains(item.getId())) >= totalCost, 5000);
                    return Rs2Inventory.count(item -> coinIDs.contains(item.getId())) >= totalCost;
                } catch (Exception e) {
                    Microbot.log("Failed to withdraw coins: " + e.getMessage());
                    return false;
                }
            }
        }
        
        Microbot.log("Insufficient coins for shop requirements. Need " + totalCost + " coins.");
        return false;
    }
    
}
