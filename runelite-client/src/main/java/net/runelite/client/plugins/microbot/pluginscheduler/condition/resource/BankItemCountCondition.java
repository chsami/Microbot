package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.Getter;
import net.runelite.api.InventoryID;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

/**
 * Condition that tracks the number of items in bank.
 * Is satisfied when we have a certain number of items in the bank, and stays satisfied until reset.
 * TODO make proccesdItemCountCondition -> tracks the number of items processed in the inventory -> for now placeholder
 * track first if we "get" it in the inventory, then count down the procces items (not counting item dropped or banked -> track if bank is open or the item was dropp be the player)
 */
@Getter
public class BankItemCountCondition extends ResourceCondition {
    
    public static String getVersion() {
        return "0.0.1";
    }
    private final String itemName;
    private final int targetCountMin;
    private final int targetCountMax;
    
    private int currentTargetCount;
    private int currentItemCount;
    private boolean satisfied = false;
    
    /**
     * Creates a condition with fixed target count
     */    
    public BankItemCountCondition(String itemName, int targetCount) {
        super(itemName);
        this.itemName = itemName;        
        this.targetCountMin = targetCount;
        this.targetCountMax = targetCount;
        this.currentTargetCount = targetCount;
        updateCurrentCount();
    }
    
    /**
     * Creates a condition with target count range
     */
    @Builder
    public BankItemCountCondition(String itemName, int targetCountMin, int targetCountMax) {
        super(itemName);
        this.itemName = itemName;        
        this.targetCountMin = Math.max(0, targetCountMin);
        this.targetCountMax = Math.max(this.targetCountMin, targetCountMax);
        this.currentTargetCount = Rs2Random.between(this.targetCountMin, this.targetCountMax);
        updateCurrentCount();
    }
    
    /**
     * Creates a condition with randomized target between min and max
     */
    public static BankItemCountCondition createRandomized(String itemName, int minCount, int maxCount) {
        return BankItemCountCondition.builder()
                .itemName(itemName)
                .targetCountMin(minCount)
                .targetCountMax(maxCount)
                .build();
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
        return String.format("Have %d %s in bank (%d/%d)", 
                currentTargetCount, 
                itemName,
                currentItemCount,
                currentTargetCount);
    }
    @Override
    public String getDetailedDescription() {
        return String.format("BankItemCountCondition: %s\n" +
                "Target Count: %d - %d\n" +
                "Current Count: %d\n" +
                "Satisfied: %s",
                itemName,
                targetCountMin,
                targetCountMax,
                currentItemCount,
                satisfied ? "YES" : "NO");
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
    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event) {
        
        
        // Update count when bank container changes
        if (event.getContainerId() == InventoryID.BANK.getId()) {
            updateCurrentCount();
        }
    }
    
    private void updateCurrentCount() {
        // Check if we're using a specific item name or counting all items
        if (itemName == null || itemName.isEmpty()) {
            // Count all items in bank
            currentItemCount = Rs2Bank.getBankItemCount();
        } else {
            // Count specific items by name using pattern matching
            if (Rs2Bank.bankItems() != null && !Rs2Bank.bankItems().isEmpty()) {
                currentItemCount = Rs2Bank.bankItems().stream()
                    .filter(item -> {
                        if (item == null) {
                            return false;
                        }
                        return itemPattern.matcher(item.getName()).matches();
                    })
                    .mapToInt(Rs2ItemModel::getQuantity)
                    .sum();
            } else {
                // Fallback to direct count if bank items list isn't populated
                currentItemCount = Rs2Bank.count(itemName, false);
            }
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
    
    /**
     * Creates a pattern for matching item names
     */
    protected Pattern createItemPattern(String itemName) {
        if (itemName == null || itemName.isEmpty()) {
            return Pattern.compile(".*");
        }
        
        // Check if the name is already a regex pattern
        if (itemName.startsWith("^") || itemName.endsWith("$") || 
            itemName.contains(".*") || itemName.contains("[") || 
            itemName.contains("(")) {
            return Pattern.compile(itemName);
        }
        
        // Otherwise, create a contains pattern
        return Pattern.compile(".*" + Pattern.quote(itemName) + ".*", Pattern.CASE_INSENSITIVE);
    }

    /**
     * Creates an AND logical condition requiring multiple items with individual targets
     * All conditions must be satisfied (must have the required number of each item)
     */
    public static LogicalCondition createAndCondition(List<String> itemNames, List<Integer> targetCountsMins, List<Integer> targetCountsMaxs) {
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
            BankItemCountCondition itemCondition = BankItemCountCondition.builder()
                    .itemName(itemNames.get(i))
                    .targetCountMin(targetCountsMins.get(i))
                    .targetCountMax(targetCountsMaxs.get(i))
                    .build();
                    
            andCondition.addCondition(itemCondition);
        }
        
        return andCondition;
    }

    /**
     * Creates an OR logical condition requiring multiple items with individual targets
     * Any condition can be satisfied (must have the required number of any one item)
     */
    public static LogicalCondition createOrCondition(List<String> itemNames, List<Integer> targetCountsMins, List<Integer> targetCountsMaxs) {
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
            BankItemCountCondition itemCondition = BankItemCountCondition.builder()
                    .itemName(itemNames.get(i))
                    .targetCountMin(targetCountsMins.get(i))
                    .targetCountMax(targetCountsMaxs.get(i))
                    .build();
                    
            orCondition.addCondition(itemCondition);
        }
        
        return orCondition;
    }

    /**
     * Creates an AND logical condition requiring multiple items with the same target for all
     * All conditions must be satisfied (must have the required number of each item)
     */
    public static LogicalCondition createAndCondition(List<String> itemNames, int targetCountMin, int targetCountMax) {
        if (itemNames == null || itemNames.isEmpty()) {
            throw new IllegalArgumentException("Item name list cannot be null or empty");
        }
        
        // Create the logical condition
        AndCondition andCondition = new AndCondition();
        
        // Add a condition for each item with the same targets
        for (String itemName : itemNames) {
            BankItemCountCondition itemCondition = BankItemCountCondition.builder()
                    .itemName(itemName)
                    .targetCountMin(targetCountMin)
                    .targetCountMax(targetCountMax)
                    .build();
                    
            andCondition.addCondition(itemCondition);
        }
        
        return andCondition;
    }

    /**
     * Creates an OR logical condition requiring multiple items with the same target for all
     * Any condition can be satisfied (must have the required number of any one item)
     */
    public static LogicalCondition createOrCondition(List<String> itemNames, int targetCountMin, int targetCountMax) {
        if (itemNames == null || itemNames.isEmpty()) {
            throw new IllegalArgumentException("Item name list cannot be null or empty");
        }
        
        // Create the logical condition
        OrCondition orCondition = new OrCondition();
        
        // Add a condition for each item with the same targets
        for (String itemName : itemNames) {
            BankItemCountCondition itemCondition = BankItemCountCondition.builder()
                    .itemName(itemName)
                    .targetCountMin(targetCountMin)
                    .targetCountMax(targetCountMax)
                    .build();
                    
            orCondition.addCondition(itemCondition);
        }
        
        return orCondition;
    }
}