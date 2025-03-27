package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource;

import lombok.Builder;
import lombok.Getter;
import net.runelite.api.InventoryID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.Condition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.ConditionType;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import java.util.regex.Pattern;

/**
 * Condition that tracks a specific looted item quantity.
 * Focuses on tracking a single item for clarity and precision.
 */
@Getter
public class LootItemCondition implements Condition {
    private final String itemName;
    private final Pattern itemPattern;    
    private final int targetAmountMin;
    private final int targetAmountMax;
    
    private int currentTargetAmount;
    private int currentTrackedCount;
    private int lastInventoryCount;
    private int lastBankedCount;

    @Builder
    public LootItemCondition(String itemName, int targetAmount) {
        this.itemName = itemName;
        this.targetAmountMin = targetAmount;
        this.targetAmountMax = targetAmount;        
        this.itemPattern = Pattern.compile(itemName, Pattern.CASE_INSENSITIVE);        
        this.currentTargetAmount = Rs2Random.between(targetAmountMin, targetAmountMax);
        this.currentTrackedCount = 0;
        this.lastInventoryCount = 0;
        this.lastBankedCount = 0;
    }
    @Builder
    public LootItemCondition(String itemName, int targetAmountMin, int targetAmountMax) {
        this.itemName = itemName;
        this.targetAmountMin = targetAmountMin;
        this.targetAmountMax = targetAmountMax;        
        this.currentTargetAmount = Rs2Random.between(targetAmountMin, targetAmountMax);
        this.itemPattern = Pattern.compile(itemName, Pattern.CASE_INSENSITIVE);        
        this.currentTrackedCount = 0;
        this.lastInventoryCount = 0;
        this.lastBankedCount = 0;
    }
    
    /**
     * Creates a condition with randomized target between min and max
     */
    public static LootItemCondition createRandomized(String itemName, int minAmount, int maxAmount) {
        if (minAmount < 0 || maxAmount < 0) {
            throw new IllegalArgumentException("Amounts must be non-negative");
        }
        if (minAmount > maxAmount) {
            throw new IllegalArgumentException("Min amount cannot be greater than max amount");
        }
        
        // Create only ONE instance and keep it
        LootItemCondition condition = new LootItemCondition(itemName, minAmount, maxAmount);               
        // Return the SAME instance we created and initialized
        return condition;
    }
   
    @Override
    public boolean isMet() {
        return getCollectedAmount() >= currentTargetAmount;
    }

    private int getCollectedAmount() {
        return this.currentTrackedCount;
    }
  
    /**
     * Gets amount of items remaining to reach target
     */
    public int getRemainingAmount() {
        return Math.max(0, currentTargetAmount - getCollectedAmount());
    }

    /**
     * Gets progress percentage towards target
     */
    public double getProgressPercentage() {
        int current = getCurrentTrackedCount();
        int target = currentTargetAmount;
        
        if (current >= target) {
            return 100.0;
        }
        
        return (100.0 * current) / target;
    }

    @Override
    public String getDescription() {
        return String.format("Collect %d %s (Current: %d/%d - %.1f%%)", 
        currentTargetAmount, 
            itemName,
            getCollectedAmount(),
            currentTargetAmount,
            getProgressPercentage());
    }

    @Override
    public ConditionType getType() {
        return ConditionType.ITEM;
    }
    
    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {

        // Only process inventory container changes
        if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
                        
            
            // Get current count using pattern matching
            int currentInventoryCount = Rs2Inventory.count(item -> {
                // Use the pattern to check if item name matches
                if (item.getName() == null) return false;
                return itemPattern.matcher(item.getName()).matches();
            });
            // If this is the first update, initialize baseline
            
            if (currentInventoryCount>=lastInventoryCount) {
                
                                       
                // Only count the increase (new items added)
                int itemsAdded = lastInventoryCount - currentInventoryCount;
                // Add to our running total
                this.currentTrackedCount += itemsAdded;
            }
            lastInventoryCount = currentInventoryCount;
            
            return;
        }
        if (event.getContainerId() == InventoryID.BANK.getId()) {
         
        }


    }
    
 
    
    @Override
    public void onStatChanged(StatChanged event) {
        // Not used for this condition type
    }
    @Override
    public void reset() {

        this.currentTrackedCount = 0;
        this.lastInventoryCount = 0;
        this.lastBankedCount = 0;        
        // Randomize the target amount within the specified range
        this.currentTargetAmount = Rs2Random.between(targetAmountMin, targetAmountMax);
        // Reset the tracked count
        
    }
}