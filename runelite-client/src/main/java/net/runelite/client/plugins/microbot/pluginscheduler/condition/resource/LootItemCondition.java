package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource;

import lombok.Builder;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.InventoryID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Condition that tracks a specific looted item quantity.
 * Focuses on tracking a single item for clarity and precision.
 */
@Getter
@Slf4j
public class LootItemCondition extends ResourceCondition {
    private final String itemName;
    private final Pattern itemPattern;    
    private final int targetAmountMin;
    private final int targetAmountMax;
    private final boolean includeNoted;
    
    private int currentTargetAmount;
    private int currentTrackedCount;
    private int lastInventoryCount;
    private int lastBankedCount;


    public LootItemCondition(String itemName, int targetAmount,boolean includeNoted) {
        this.itemName = itemName;
        this.targetAmountMin = targetAmount;
        this.targetAmountMax = targetAmount;        
        this.includeNoted = includeNoted;
        this.itemPattern = createItemPattern(itemName);
        this.currentTargetAmount = Rs2Random.between(targetAmountMin, targetAmountMax);
        this.currentTrackedCount = 0;
        this.lastInventoryCount = 0;
        this.lastBankedCount = 0;
    }
    
    @Builder
    public LootItemCondition(String itemName, int targetAmountMin, int targetAmountMax,boolean includeNoted) {
        this.itemName = itemName;
        this.targetAmountMin = targetAmountMin;
        this.targetAmountMax = targetAmountMax;        
        this.itemPattern = createItemPattern(itemName);
        this.currentTargetAmount = Rs2Random.between(targetAmountMin, targetAmountMax);
        this.currentTrackedCount = 0;
        this.lastInventoryCount = 0;
        this.lastBankedCount = 0;
        this.includeNoted = includeNoted;
    }
    
    /**
     * Creates a condition with randomized target between min and max
     */
    public static LootItemCondition createRandomized(String itemName, int minAmount, int maxAmount) {
        return LootItemCondition.builder()
                .itemName(itemName)
                .targetAmountMin(minAmount)
                .targetAmountMax(maxAmount)
                .build();
    }
   
    @Override
    public boolean isSatisfied() {        
        return currentTrackedCount >= currentTargetAmount;
    }

    @Override
    public String getDescription() {
        String randomRangeInfo = "";
        
        if (targetAmountMin != targetAmountMax) {
            randomRangeInfo = String.format(" (randomized from %d-%d)", targetAmountMin, targetAmountMax);
        }
        
        return String.format("Loot %d %s%s (%d/%d, %.1f%%)", 
                currentTargetAmount, 
                itemName,
                randomRangeInfo,
                currentTrackedCount,
                currentTargetAmount,
                getProgressPercentage());
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
        this.lastBankedCount = 0;
    }
    
    @Override
    public double getProgressPercentage() {
        if (currentTargetAmount <= 0) {
            return 100.0;
        }
        
        double ratio = (double) currentTrackedCount / currentTargetAmount;
        return Math.min(100.0, ratio * 100.0);
    }
    
    
    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        log.info("ItemContainerChanged: is inventory {} - {}", event.getContainerId() == InventoryID.INVENTORY.getId(),getDescription());
        // Only care about inventory and bank changes
        if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
            updateItemCounts();
        } else if (event.getContainerId() == InventoryID.BANK.getId()) {
            // Could track bank items here if needed
        }
    }
    
    
    @Override
    public void onGameTick(GameTick gameTick) {
        // Check inventory periodically to catch any changes 
        // that might not trigger an ItemContainerChanged event
        updateItemCounts();
    }
    
    /**
     * Updates the tracked item counts by comparing current and last inventory counts
     */
    private void updateItemCounts() {
        int currentCount = getCurrentInventoryCount();
        
        // If inventory count increased, add to our tracked total
        if (currentCount > lastInventoryCount) {
            currentTrackedCount += (currentCount - lastInventoryCount);
        }
        
        lastInventoryCount = currentCount;
    }
    
    /**
     * Gets the current count of this item in the inventory
     */
    private int getCurrentInventoryCount() {
        int currentItemCountNoted = getNotedItems().stream().filter(item -> {
            if (item == null) {
                return false;
            }   ;         
            return itemPattern.matcher(item.getName()).matches();             
        }).collect(Collectors.toList()).size();
        int currentItemCountUnNoted = getUnNotedItems().stream().filter(item -> {
            if (item == null) {
                return false;
            }            
            return itemPattern.matcher(item.getName()).matches();             
        }).collect(Collectors.toList()).size();
        log.info("getCurrentInventoryCount: noted {} - unoted {}",currentItemCountNoted,currentItemCountUnNoted);
        if (includeNoted) {
            return currentItemCountNoted + currentItemCountUnNoted;
        }
        return currentItemCountUnNoted;        
    }

    /**
     * Creates an AND logical condition requiring multiple looted items with individual targets
     * All conditions must be satisfied (must loot the required number of each item)
     */
    public static LogicalCondition createAndCondition(List<String> itemNames, List<Integer> targetAmountsMins, List<Integer> targetAmountsMaxs) {
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
                    .build();
                    
            andCondition.addCondition(itemCondition);
        }
        
        return andCondition;
    }

    /**
     * Creates an OR logical condition requiring multiple looted items with individual targets
     * Any condition can be satisfied (must loot the required number of any one item)
     */
    public static LogicalCondition createOrCondition(List<String> itemNames, List<Integer> targetAmountsMins, List<Integer> targetAmountsMaxs) {
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
                    .build();
                    
            orCondition.addCondition(itemCondition);
        }
        
        return orCondition;
    }

    /**
     * Creates an AND logical condition requiring multiple looted items with the same target for all
     * All conditions must be satisfied (must loot the required number of each item)
     */
    public static LogicalCondition createAndCondition(List<String> itemNames, int targetAmountMin, int targetAmountMax) {
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
                    .build();
                    
            andCondition.addCondition(itemCondition);
        }
        
        return andCondition;
    }

    /**
     * Creates an OR logical condition requiring multiple looted items with the same target for all
     * Any condition can be satisfied (must loot the required number of any one item)
     */
    public static LogicalCondition createOrCondition(List<String> itemNames, int targetAmountMin, int targetAmountMax) {
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
                    .build();
                    
            orCondition.addCondition(itemCondition);
        }
        
        return orCondition;
    }
}