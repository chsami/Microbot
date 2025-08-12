package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.models.ShopOperation;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesInterval;
import net.runelite.client.plugins.microbot.util.shop.models.Rs2ShopItem;
import net.runelite.client.plugins.microbot.util.shop.models.Rs2ShopType;

/**
 * Represents an individual item requirement within a shop operation.
 * Uses unified stock management system based on operation type.
 * Enhanced with Grand Exchange time-series pricing configuration.
 * 
 * Key improvements:
 * - Extracted from inner class for better reusability
 * - Enhanced stock validation logic
 * - Better quantity tracking and completion status
 * - Time-series pricing configuration for Grand Exchange operations
 */
@Getter
@EqualsAndHashCode()
@Slf4j
public class ShopItemRequirement {
    private final Rs2ShopItem shopItem;
    private final int amount;
    private final int stockTolerance;
    private int completedAmount = 0;
    
    // Grand Exchange time-series configuration
    private final TimeSeriesInterval timeSeriesInterval;
    private final boolean useTimeSeriesAveraging;
    
    /**
     * Creates a shop item requirement with unified stock management.
     * Uses the Rs2ShopItem's baseStock for stock calculations.
     * 
     * @param shopItem The shop item to buy/sell (contains baseStock information)
     * @param amount Total amount needed
     * @param stockTolerance Acceptable deviation from shopItem's baseStock
     * @param timeSeriesInterval Time-series interval for Grand Exchange price averaging
     * @param useTimeSeriesAveraging Whether to use time-series averaging for Grand Exchange
     * 
     * For BUY operations: We buy when shop has (baseStock - stockTolerance) or more
     * For SELL operations: We sell when shop has (baseStock + stockTolerance) or less
     */
    public ShopItemRequirement(Rs2ShopItem shopItem, int amount, int stockTolerance, 
                             TimeSeriesInterval timeSeriesInterval, boolean useTimeSeriesAveraging) {
        if (shopItem == null) {
            throw new IllegalArgumentException("ShopItem cannot be null");
        }
        if (amount <= 0) {
            throw new IllegalArgumentException("Amount must be positive, got: " + amount);
        }
        if (stockTolerance < 0) {
            throw new IllegalArgumentException("Stock tolerance cannot be negative, got: " + stockTolerance);
        }
        
        this.shopItem = shopItem;
        this.amount = amount;
        this.stockTolerance = stockTolerance;
        this.timeSeriesInterval = timeSeriesInterval != null ? timeSeriesInterval : TimeSeriesInterval.ONE_HOUR;
        this.useTimeSeriesAveraging = useTimeSeriesAveraging;
    }
    
    /**
     * Creates a shop item requirement with default time-series configuration.
     * Uses 1-hour averaging for Grand Exchange operations by default.
     */
    public ShopItemRequirement(Rs2ShopItem shopItem, int amount, int stockTolerance) {
        this(shopItem, amount, stockTolerance, TimeSeriesInterval.ONE_HOUR, true);
    }
    
  
    
    /**
     * For BUY operations: Checks if shop has enough stock to allow buying
     * For SELL operations: Checks if shop has low enough stock to allow selling
     */
    public boolean canProcessInShop(int currentShopStock, ShopOperation operation) {
        if (operation == ShopOperation.BUY) {
            return currentShopStock >= getMinimumStockForBuying();
        } else { // SELL
            return currentShopStock <= getMaximumStockForSelling();
        }
    }
    
    /**
     * For BUY operations: Minimum stock required in shop to allow buying
     */
    public int getMinimumStockForBuying() {
        return Math.max(0, shopItem.getBaseStock() - stockTolerance);
    }
    
    /**
     * For SELL operations: Maximum stock allowed in shop to allow selling
     */
    public int getMaximumStockForSelling() {
        return shopItem.getBaseStock() + stockTolerance;
    }
    public int allowedToBuy(int currentStock){
        return Math.max(0, currentStock - getMinimumStockForBuying() + 1);
    }
    public int allowedToSell(int currentStock){
        return Math.max(0, getMaximumStockForSelling() - currentStock );
    }
    
 
    
    /**
     * Calculates how many items we can safely buy/sell in current shop stock situation
     */
    public int getQuantityForCurrentVisit(int currentShopStock, ShopOperation operation) {        
        int remaining = getRemainingAmount();       
        if (operation == ShopOperation.BUY) {
            // Can't buy more than available stock
            int availableStockForBuying =allowedToBuy(currentShopStock);
            log.info(" Remaing {}  to buy -- Available stock for buying {}: {}",remaining, shopItem.getItemName(), availableStockForBuying);
    
            return Math.min(remaining, availableStockForBuying);
        } else { // SELL
            // Can't sell more than shop can accept
            int shopCapacityForSelling =allowedToSell(currentShopStock);
            return Math.min( remaining, shopCapacityForSelling);
        }
    }
    
    /**
     * Legacy compatibility - returns baseStock from shopItem
     */
    @Deprecated
    public int getMinimumStock() {
        return shopItem.getBaseStock();
    }
    
    /**
     * Gets the base stock from the shop item
     */
    public int getBaseStock() {
        return shopItem.getBaseStock();
    }
    
    public boolean isCompleted() {
        return completedAmount >= amount;
    }
    
    public int getRemainingAmount() {
        return Math.max(0, amount - completedAmount);
    }
    
    public void addCompletedAmount(int additionalAmount) {
        if (additionalAmount < 0) {
            throw new IllegalArgumentException("Additional amount cannot be negative: " + additionalAmount);
        }
        this.completedAmount = Math.min(this.amount, this.completedAmount + additionalAmount);
    }
    
    public void setCompletedAmount(int newAmount) {
        if (newAmount < 0) {
            throw new IllegalArgumentException("Completed amount cannot be negative: " + newAmount);
        }
        this.completedAmount = Math.min(this.amount, newAmount);
    }
    
    public String getItemName() {
        return shopItem.getItemName();
    }
    
    public int getItemId() {
        return shopItem.getItemId();
    }
    
    /**
     * Progress percentage (0.0 to 1.0)
     */
    public double getProgress() {
        return amount > 0 ? (double) completedAmount / amount : 1.0;
    }
    
    /**
     * Human readable progress string
     */
    public String getProgressString() {
        return String.format("%d/%d (%.1f%%)", completedAmount, amount, getProgress() * 100);
    }
    
    /**
     * Determines if this item requirement should use time-series pricing.
     * Only applies to Grand Exchange operations when enabled.
     */
    public boolean shouldUseTimeSeriesPricing() {
        return useTimeSeriesAveraging && isGrandExchangeItem();
    }
    
    /**
     * Checks if this item is for Grand Exchange operations.
     * This can be determined from the shop item's context or type.
     */
    private boolean isGrandExchangeItem() {        
        return shopItem.getShopType()==Rs2ShopType.GRAND_EXCHANGE; // Default to true for now - can be refined based on shop context
    }
    
    /**
     * Gets the recommended time-series interval for pricing this item.
     * Returns the configured interval, defaulting to 1-hour if not set.
     */
    public TimeSeriesInterval getRecommendedTimeSeriesInterval() {
        return timeSeriesInterval;
    }
    
    @Override
    public String toString() {
        return String.format("ShopItemRequirement{item='%s', amount=%d, completed=%d, stockTolerance=%d, baseStock=%d}", 
                           getItemName(), amount, completedAmount, stockTolerance, getBaseStock());
    }
}
