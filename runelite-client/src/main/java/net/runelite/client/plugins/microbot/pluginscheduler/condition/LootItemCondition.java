package net.runelite.client.plugins.microbot.pluginscheduler.condition;

import lombok.Builder;
import lombok.Getter;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.StatChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Condition that tracks a specific looted item quantity.
 * Focuses on tracking a single item for clarity and precision.
 */
@Getter
public class LootItemCondition implements Condition {
    private final String itemName;
    private final Pattern itemPattern;    
    private final int targetAmount;
    private final Instant startTime;
    
    private int currentTrackedCount;
    private int lastInventoryCount;
    private int lastBankedCount;

    @Builder
    public LootItemCondition(String itemName, int targetAmount) {
        this.itemName = itemName;
        this.targetAmount = targetAmount;
        this.startTime = Instant.now();
        this.itemPattern = Pattern.compile(itemName, Pattern.CASE_INSENSITIVE);        
        this.currentTrackedCount = 0;
        this.lastInventoryCount = 0;
        this.lastBankedCount = 0;
    }
    
    /**
     * Creates a condition with randomized target between min and max
     */
    public static LootItemCondition createRandomized(String itemName, int minAmount, int maxAmount) {
        if (minAmount == maxAmount) {
            return LootItemCondition.builder()
                .itemName(itemName)                
                .targetAmount(minAmount)
                .build();
        }
        
        int range = maxAmount - minAmount;
        int randomAmount = minAmount + (int)(Math.random() * (range + 1));
        
        return LootItemCondition.builder()
            .itemName(itemName)
            .targetAmount(randomAmount)
            .build();
    }
   
    @Override
    public boolean isMet() {
        return getCollectedAmount() >= targetAmount;
    }

    private int getCollectedAmount() {
        return this.currentTrackedCount;
    }
  
    /**
     * Gets amount of items remaining to reach target
     */
    public int getRemainingAmount() {
        return Math.max(0, targetAmount - getCollectedAmount());
    }

    /**
     * Gets progress percentage towards target
     */
    public double getProgressPercentage() {
        if (targetAmount <= 0) return 100.0;
        return Math.min(100.0, (getCollectedAmount() / (double) targetAmount) * 100);
    }

    

    @Override
    public String getDescription() {
        return String.format("Collect %d %s (Current: %d/%d - %.1f%%)", 
            targetAmount, 
            itemName,
            getCollectedAmount(),
            targetAmount,
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
}