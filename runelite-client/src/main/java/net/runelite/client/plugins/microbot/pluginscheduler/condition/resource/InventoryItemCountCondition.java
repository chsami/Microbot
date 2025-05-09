package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import lombok.Builder;
import lombok.Getter;
import net.runelite.api.InventoryID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

/**
 * Condition that tracks the total number of items in inventory.
 * Can be set to track noted items as well. TODO Rename into GatheredItemCountCondition
 */
@Getter
public class InventoryItemCountCondition extends ResourceCondition {
    
    public static String getVersion() {
        return "0.0.1";
    }
    private final boolean includeNoted;    
    private final int targetCountMin;
    private final int targetCountMax;
    
    private transient int currentTargetCount;
    private transient int currentItemCount;
    private transient boolean satisfied = false;
    private transient boolean initialInventoryLoaded = false;
    public InventoryItemCountCondition(String itemName, int targetCount, boolean includeNoted) {
        super(itemName);
        this.includeNoted = includeNoted;
        
        this.targetCountMin = targetCount;
        this.targetCountMax = targetCount;
        this.currentTargetCount = targetCount;
        updateCurrentCount();
    }
    
    @Builder
    public InventoryItemCountCondition(String itemName,int targetCountMin, int targetCountMax, boolean includeNoted) {
        super(itemName);
        this.includeNoted = includeNoted;        
        this.targetCountMin = Math.max(0, targetCountMin);
        
        // If not tracking noted items, limit max count to inventory size (28)
        this.targetCountMax = includeNoted ? 
                Math.min(Integer.MAX_VALUE, targetCountMax) : 
                Math.min(28, targetCountMax);
                
        this.currentTargetCount = Rs2Random.between(this.targetCountMin, this.targetCountMax);
        updateCurrentCount();
    }
    
    /**
     * Creates a condition with randomized target between min and max
     */
    public static InventoryItemCountCondition createRandomized(int minCount, int maxCount, boolean includeNoted) {
        return InventoryItemCountCondition.builder()
                .targetCountMin(minCount)
                .targetCountMax(maxCount)
                .includeNoted(includeNoted)
                .build();
    }
    
    /**
     * Creates an AND logical condition requiring multiple items with individual targets
     * All conditions must be satisfied (must have the required number of each item)
     */
    public static LogicalCondition createAndCondition(List<String> itemNames, List<Integer> targetCountsMins, List<Integer> targetCountsMaxs, boolean includeNoted) {
        if (itemNames == null || itemNames.isEmpty()) {
            throw new IllegalArgumentException("Item name list cannot be null or empty");
        }
        
        // Determine the smallest list size for safe iteration
        int minSize = Math.min(itemNames.size(), 
                      Math.min(targetCountsMins != null ? targetCountsMins.size() : 0,
                               targetCountsMaxs != null ? targetCountsMaxs.size() : 0));
        
        // If target counts not provided or empty, default to single item each
        if (targetCountsMins == null || targetCountsMins.isEmpty()) {
            targetCountsMins = new ArrayList<>(itemNames.size());
            for (int i = 0; i < itemNames.size(); i++) {
                targetCountsMins.add(1);
            }
        }
        
        if (targetCountsMaxs == null || targetCountsMaxs.isEmpty()) {
            targetCountsMaxs = new ArrayList<>(targetCountsMins);
        }
        
        // Create the logical condition
        AndCondition andCondition = new AndCondition();
        
        // Add a condition for each item
        for (int i = 0; i < minSize; i++) {
            InventoryItemCountCondition itemCondition = InventoryItemCountCondition.builder()
                    .itemName(itemNames.get(i))
                    .targetCountMin(targetCountsMins.get(i))
                    .targetCountMax(targetCountsMaxs.get(i))
                    .includeNoted(includeNoted)
                    .build();
                    
            andCondition.addCondition(itemCondition);
        }
        
        return andCondition;
    }
    
    /**
     * Creates an OR logical condition requiring multiple items with individual targets
     * Any condition can be satisfied (must have the required number of any one item)
     */
    public static LogicalCondition createOrCondition(List<String> itemNames, List<Integer> targetCountsMins, List<Integer> targetCountsMaxs, boolean includeNoted) {
        if (itemNames == null || itemNames.isEmpty()) {
            throw new IllegalArgumentException("Item name list cannot be null or empty");
        }
        
        // Determine the smallest list size for safe iteration
        int minSize = Math.min(itemNames.size(), 
                      Math.min(targetCountsMins != null ? targetCountsMins.size() : 0,
                               targetCountsMaxs != null ? targetCountsMaxs.size() : 0));
        
        // If target counts not provided or empty, default to single item each
        if (targetCountsMins == null || targetCountsMins.isEmpty()) {
            targetCountsMins = new ArrayList<>(itemNames.size());
            for (int i = 0; i < itemNames.size(); i++) {
                targetCountsMins.add(1);
            }
        }
        
        if (targetCountsMaxs == null || targetCountsMaxs.isEmpty()) {
            targetCountsMaxs = new ArrayList<>(targetCountsMins);
        }
        
        // Create the logical condition
        OrCondition orCondition = new OrCondition();
        
        // Add a condition for each item
        for (int i = 0; i < minSize; i++) {
            InventoryItemCountCondition itemCondition = InventoryItemCountCondition.builder()
                    .itemName(itemNames.get(i))
                    .targetCountMin(targetCountsMins.get(i))
                    .targetCountMax(targetCountsMaxs.get(i))
                    .includeNoted(includeNoted)
                    .build();
                    
            orCondition.addCondition(itemCondition);
        }
        
        return orCondition;
    }
    
    /**
     * Creates an AND logical condition requiring multiple items with the same target for all
     * All conditions must be satisfied (must have the required number of each item)
     */
    public static LogicalCondition createAndCondition(List<String> itemNames, int targetCountMin, int targetCountMax, boolean includeNoted) {
        if (itemNames == null || itemNames.isEmpty()) {
            throw new IllegalArgumentException("Item name list cannot be null or empty");
        }
        
        // Create the logical condition
        AndCondition andCondition = new AndCondition();
        
        // Add a condition for each item with the same targets
        for (String itemName : itemNames) {
            InventoryItemCountCondition itemCondition = InventoryItemCountCondition.builder()
                    .itemName(itemName)
                    .targetCountMin(targetCountMin)
                    .targetCountMax(targetCountMax)
                    .includeNoted(includeNoted)
                    .build();
                    
            andCondition.addCondition(itemCondition);
        }
        
        return andCondition;
    }
    
    /**
     * Creates an OR logical condition requiring multiple items with the same target for all
     * Any condition can be satisfied (must have the required number of any one item)
     */
    public static LogicalCondition createOrCondition(List<String> itemNames, int targetCountMin, int targetCountMax, boolean includeNoted) {
        if (itemNames == null || itemNames.isEmpty()) {
            throw new IllegalArgumentException("Item name list cannot be null or empty");
        }
        
        // Create the logical condition
        OrCondition orCondition = new OrCondition();
        
        // Add a condition for each item with the same targets
        for (String itemName : itemNames) {
            InventoryItemCountCondition itemCondition = InventoryItemCountCondition.builder()
                    .itemName(itemName)
                    .targetCountMin(targetCountMin)
                    .targetCountMax(targetCountMax)
                    .includeNoted(includeNoted)
                    .build();
                    
            orCondition.addCondition(itemCondition);
        }
        
        return orCondition;
    }
    
    @Override
    public boolean isSatisfied() {
        // Once satisfied, stay satisfied until reset
        if (satisfied) {
            return true;
        }
        
        // Check if current count meets or exceeds target
        if (currentItemCount >= currentTargetCount) {
            satisfied = true;
            return true;
        }
        
        return false;
    }
    
    @Override
    public String getDescription() {
        String itemTypeDesc = includeNoted ? " (including noted)" : "";
        String randomRangeInfo = "";
        
        if (targetCountMin != targetCountMax) {
            randomRangeInfo = String.format(" (randomized from %d-%d)", targetCountMin, targetCountMax);
        }
        
        if (getItemName() == null || getItemName().isEmpty()) {
            return String.format("Have %d total items in inventory%s%s (%d/%d, %.1f%%)", 
                    currentTargetCount, 
                    itemTypeDesc,
                    randomRangeInfo,
                    currentItemCount,
                    currentTargetCount,
                    getProgressPercentage());
        } else {
            return String.format("Have %d %s in inventory%s%s (%d/%d, %.1f%%)", 
                    currentTargetCount, 
                    getItemName(),
                    itemTypeDesc,
                    randomRangeInfo,
                    currentItemCount,
                    currentTargetCount,
                    getProgressPercentage());
        }
    }
    public String getDetailedDescription() {
        return String.format("Inventory Item Count Condition: %s\n" +
                "Target Count: %d (current: %d)\n" +
                "Include Noted: %s\n" +
                "Progress: %.1f%%",
                getItemName(),
                currentTargetCount,
                currentItemCount,
                includeNoted ? "Yes" : "No",
                getProgressPercentage());
    }
    @Override
    public void reset() {
        reset(false);
    }
    
    @Override
    public void reset(boolean randomize) {
        if (randomize && targetCountMin != targetCountMax) {
            currentTargetCount = Rs2Random.between(targetCountMin, targetCountMax);
        }
        satisfied = false;
        initialInventoryLoaded = false;
        updateCurrentCount();
    }
    
    @Override
    public double getProgressPercentage() {
        if (satisfied) {
            return 100.0;
        }
        
        if (currentTargetCount <= 0) {
            return 100.0;
        }
        
        return Math.min(100.0, (currentItemCount * 100.0) / currentTargetCount);
    }
    
    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
              
        if (event.getContainerId() == InventoryID.INVENTORY.getId()) {
            if (Rs2Bank.isOpen()) {
                return;
            }
            updateCurrentCount();
        }
    }
    @Override
    public void onGameTick(GameTick event) {
        // Load initial inventory if not yet loaded
        if (!initialInventoryLoaded) {
            updateCurrentCount();
            initialInventoryLoaded = true;
        }
    }
    
    private void updateCurrentCount() {
            
        // Count specific items by name (using existing pattern matching)
        
        int currentItemCountNoted = getNotedItems().stream().filter(item -> {
            if (item == null) {
                return false;
            }   ;         
            return itemPattern.matcher(item.getName()).matches();             
        }).mapToInt(item -> item.getQuantity()).sum();
        int currentItemCountUnNoted = getUnNotedItems().stream().filter(item -> {
            if (item == null) {
                return false;
            }            
            return itemPattern.matcher(item.getName()).matches();             
        }).mapToInt(item -> item.getQuantity()).sum();
        if (includeNoted) {
            currentItemCount = currentItemCountNoted + currentItemCountUnNoted;
        } else {
            currentItemCount = currentItemCountUnNoted;
        }
        
    }
    
    @Override
    public int getTotalConditionCount() {
        return 1;
    }
    
    @Override
    public int getMetConditionCount() {
        return isSatisfied() ? 1 : 0;
    }
}