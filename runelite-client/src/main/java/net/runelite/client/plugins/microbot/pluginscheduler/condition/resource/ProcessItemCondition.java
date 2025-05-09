package net.runelite.client.plugins.microbot.pluginscheduler.condition.resource;

import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.InventoryID;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.AndCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.LogicalCondition;
import net.runelite.client.plugins.microbot.pluginscheduler.condition.logical.OrCondition;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collector;
import java.util.stream.Collectors;


/**
 * Condition that tracks item processing activities (smithing, herblore, crafting, etc.)
 * Can track both source items being consumed and target items being produced
 */
@Slf4j
@Getter
public class ProcessItemCondition extends ResourceCondition {    
    public static String getVersion() {
        return "0.0.1";
    }
    // Tracking options
    private final List<ItemTracker> sourceItems; // Items being consumed
    private final List<ItemTracker> targetItems; // Items being produced
    private final TrackingMode trackingMode; // How we want to track progress
    
    // Target count configuration
    private final int targetCountMin;
    private final int targetCountMax;
    private transient int currentTargetCount;
    
    // State tracking
    private transient Map<String, Integer> previousInventoryCounts = new HashMap<>();
    private transient int processedCount = 0;
    private transient boolean satisfied = false;
    private transient Instant lastInventoryChange = Instant.now();
    private transient boolean isProcessingActive = false;
    private transient boolean initialInventoryLoaded = false;
    public  List<Pattern>getInputItemPatterns() {
        return sourceItems.stream().map(ItemTracker::getItemPattern).collect(Collectors.toList());
    }
    public List<String> getInputItemPatternStrings() {
        return sourceItems.stream().map( (trakedItem)-> trakedItem.getItemPattern().toString()).collect(Collectors.toList());
    }
    public  List<Pattern>getOutputItemPatterns() {
        return targetItems.stream().map(ItemTracker::getItemPattern).collect(Collectors.toList());
    }
    public List<String> getOutputItemPatternStrings() {
        return targetItems.stream().map( (trakedItem)-> trakedItem.getItemPattern().toString()).collect(Collectors.toList());
    }
    /**
     * Tracking mode determines what we're counting
     */
    public enum TrackingMode {
        SOURCE_CONSUMPTION, // Count when source items are consumed
        TARGET_PRODUCTION,  // Count when target items are produced
        EITHER,            // Count either source consumption or target production
        BOTH               // Require both source consumption and target production
    }
    
    /**
     * Inner class to track details about items being processed
     */
    @Data
    public static class ItemTracker {        
        private final Pattern itemPattern;
        private final int quantityPerProcess; // How many of this item are consumed/produced per process
        
        public ItemTracker(String itemName, int quantityPerProcess) {
            this.itemPattern = createItemPattern(itemName);
            this.quantityPerProcess = quantityPerProcess;
        }
        
        private static Pattern createItemPattern(String itemName) {
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
    }
    
    /**
     * Full constructor with Builder support
     */
    @Builder
    public ProcessItemCondition(
            List<ItemTracker> sourceItems,
            List<ItemTracker> targetItems,
            TrackingMode trackingMode,
            int targetCountMin,
            int targetCountMax) {
        super();
        this.sourceItems = sourceItems != null ? sourceItems : new ArrayList<>();
        this.targetItems = targetItems != null ? targetItems : new ArrayList<>();
        this.trackingMode = trackingMode != null ? trackingMode : TrackingMode.EITHER;
        this.targetCountMin = Math.max(0, targetCountMin);
        this.targetCountMax = Math.max(this.targetCountMin, targetCountMax);
        this.currentTargetCount = Rs2Random.between(this.targetCountMin, this.targetCountMax);
    }
    
    /**
     * Create a condition for tracking production of a specific item
     */
    public static ProcessItemCondition forProduction(String targetItemName, int count) {
        return forProduction(targetItemName, 1, count);
    }
    
    /**
     * Create a condition for tracking production of a specific item with quantity per process
     */
    public static ProcessItemCondition forProduction(String targetItemName, int quantityPerProcess, int count) {
        List<ItemTracker> targetItems = new ArrayList<>();
        targetItems.add(new ItemTracker(targetItemName, quantityPerProcess));
        
        return ProcessItemCondition.builder()
                .targetItems(targetItems)
                .trackingMode(TrackingMode.TARGET_PRODUCTION)
                .targetCountMin(count)
                .targetCountMax(count)
                .build();
    }
    
    /**
     * Create a condition for tracking consumption of a specific source item
     */
    public static ProcessItemCondition forConsumption(String sourceItemName, int count) {
        return forConsumption(sourceItemName, 1, count);
    }
    
    /**
     * Create a condition for tracking consumption of a specific source item with quantity per process
     */
    public static ProcessItemCondition forConsumption(String sourceItemName, int quantityPerProcess, int count) {
        List<ItemTracker> sourceItems = new ArrayList<>();
        sourceItems.add(new ItemTracker(sourceItemName, quantityPerProcess));
        
        return ProcessItemCondition.builder()
                .sourceItems(sourceItems)
                .trackingMode(TrackingMode.SOURCE_CONSUMPTION)
                .targetCountMin(count)
                .targetCountMax(count)
                .build();
    }
    
    /**
     * Create a condition for tracking a complete recipe (source items and target items)
     */
    public static ProcessItemCondition forRecipe(String sourceItemName, int sourceQuantity, 
            String targetItemName, int targetQuantity, int count) {
        List<ItemTracker> sourceItems = new ArrayList<>();
        sourceItems.add(new ItemTracker(sourceItemName, sourceQuantity));
        
        List<ItemTracker> targetItems = new ArrayList<>();
        targetItems.add(new ItemTracker(targetItemName, targetQuantity));
        
        return ProcessItemCondition.builder()
                .sourceItems(sourceItems)
                .targetItems(targetItems)
                .trackingMode(TrackingMode.BOTH)
                .targetCountMin(count)
                .targetCountMax(count)
                .build();
    }
    
    /**
     * Create a condition for tracking multiple source items being consumed
     */
    public static ProcessItemCondition forMultipleConsumption(
            List<String> sourceItemNames, 
            List<Integer> sourceQuantities, 
            int count) {
        List<ItemTracker> sourceItems = new ArrayList<>();
        for (int i = 0; i < sourceItemNames.size(); i++) {
            int quantity = i < sourceQuantities.size() ? sourceQuantities.get(i) : 1;
            sourceItems.add(new ItemTracker(sourceItemNames.get(i), quantity));
        }
        
        return ProcessItemCondition.builder()
                .sourceItems(sourceItems)
                .trackingMode(TrackingMode.SOURCE_CONSUMPTION)
                .targetCountMin(count)
                .targetCountMax(count)
                .build();
    }
    
    /**
     * Create a condition for tracking multiple target items being produced
     */
    public static ProcessItemCondition forMultipleProduction(
            List<String> targetItemNames, 
            List<Integer> targetQuantities, 
            int count) {
        List<ItemTracker> targetItems = new ArrayList<>();
        for (int i = 0; i < targetItemNames.size(); i++) {
            int quantity = i < targetQuantities.size() ? targetQuantities.get(i) : 1;
            targetItems.add(new ItemTracker(targetItemNames.get(i), quantity));
        }
        
        return ProcessItemCondition.builder()
                .targetItems(targetItems)
                .trackingMode(TrackingMode.TARGET_PRODUCTION)
                .targetCountMin(count)
                .targetCountMax(count)
                .build();
    }
    
    /**
     * Create a randomized condition for production
     */
    public static ProcessItemCondition createRandomizedProduction(String targetItemName, int minCount, int maxCount) {
        List<ItemTracker> targetItems = new ArrayList<>();
        targetItems.add(new ItemTracker(targetItemName, 1));
        
        return ProcessItemCondition.builder()
                .targetItems(targetItems)
                .trackingMode(TrackingMode.TARGET_PRODUCTION)
                .targetCountMin(minCount)
                .targetCountMax(maxCount)
                .build();
    }
    
    /**
     * Create a randomized condition for consumption
     */
    public static ProcessItemCondition createRandomizedConsumption(String sourceItemName, int minCount, int maxCount) {
        List<ItemTracker> sourceItems = new ArrayList<>();
        sourceItems.add(new ItemTracker(sourceItemName, 1));
        
        return ProcessItemCondition.builder()
                .sourceItems(sourceItems)
                .trackingMode(TrackingMode.SOURCE_CONSUMPTION)
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
        
        // Check if processed count meets or exceeds target
        if (processedCount >= currentTargetCount) {
            satisfied = true;
            return true;
        }
        
        return false;
    }
    
    @Override
    public String getDescription() {
        StringBuilder sb = new StringBuilder();
        
        if (trackingMode == TrackingMode.SOURCE_CONSUMPTION || trackingMode == TrackingMode.BOTH) {
            sb.append("Process ");
            for (int i = 0; i < sourceItems.size(); i++) {
                ItemTracker item = sourceItems.get(i);
                sb.append(item.getQuantityPerProcess()).append(" ").append(item.getItemName());
                if (i < sourceItems.size() - 1) {
                    sb.append(", ");
                }
            }
        }
        
        if (trackingMode == TrackingMode.TARGET_PRODUCTION || trackingMode == TrackingMode.BOTH) {
            if (trackingMode == TrackingMode.BOTH) {
                sb.append(" into ");
            } else {
                sb.append("Create ");
            }
            
            for (int i = 0; i < targetItems.size(); i++) {
                ItemTracker item = targetItems.get(i);
                sb.append(item.getQuantityPerProcess()).append(" ").append(item.getItemName());
                if (i < targetItems.size() - 1) {
                    sb.append(", ");
                }
            }
        }
        
        // Add target info
        sb.append(": ").append(processedCount).append("/").append(currentTargetCount);
        
        if (targetCountMin != targetCountMax) {
            sb.append(" (randomized from ").append(targetCountMin).append("-").append(targetCountMax).append(")");
        }
        
        return sb.toString();
    }
    
    @Override
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        
        // Basic description
        sb.append("Process Item Condition\n");
        
        // Source items
        if (!sourceItems.isEmpty()) {
            sb.append("Source Items: ");
            for (int i = 0; i < sourceItems.size(); i++) {
                ItemTracker item = sourceItems.get(i);
                sb.append(item.getQuantityPerProcess()).append("x ").append(item.getItemName());
                if (i < sourceItems.size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append("\n");
        }
        
        // Target items
        if (!targetItems.isEmpty()) {
            sb.append("Target Items: ");
            for (int i = 0; i < targetItems.size(); i++) {
                ItemTracker item = targetItems.get(i);
                sb.append(item.getQuantityPerProcess()).append("x ").append(item.getItemName());
                if (i < targetItems.size() - 1) {
                    sb.append(", ");
                }
            }
            sb.append("\n");
        }
        
        // Tracking mode
        sb.append("Tracking Mode: ").append(trackingMode).append("\n");
        
        // Status
        sb.append("Status: ").append(isSatisfied() ? "Satisfied" : "Not Satisfied").append("\n");
        sb.append("Progress: ").append(processedCount).append("/").append(currentTargetCount)
          .append(" (").append(String.format("%.1f%%", getProgressPercentage())).append(")\n");
        
        if (targetCountMin != targetCountMax) {
            sb.append("Target Range: ").append(targetCountMin).append("-").append(targetCountMax).append("\n");
        }
        
        // Current state
        sb.append("Processing Active: ").append(isProcessingActive ? "Yes" : "No").append("\n");
        
        return sb.toString();
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
        processedCount = 0;
        previousInventoryCounts.clear();
        initialInventoryLoaded = false;
    }
    
    @Override
    public double getProgressPercentage() {
        if (satisfied) {
            return 100.0;
        }
        
        if (currentTargetCount <= 0) {
            return 100.0;
        }
        
        return Math.min(100.0, (processedCount * 100.0) / currentTargetCount);
    }
    
    @Override
    public void onItemContainerChanged(ItemContainerChanged event) {
        // Only process inventory changes
        if (event.getContainerId() != InventoryID.INVENTORY.getId()) {
            return;
        }
        
        // Don't process changes if bank is open (banking items)
        if (Rs2Bank.isOpen()) {
            return;
        }
        
        // Update item tracking
        updateItemTracking();
    }
    
    @Override
    public void onGameTick(GameTick event) {
        // Load initial inventory if not yet loaded
        if (!initialInventoryLoaded) {
            updateItemTracking();
            initialInventoryLoaded = true;
        }
    }
    
    /**
     * Update the tracking of items and detect processing
     */
    private void updateItemTracking() {
        Map<String, Integer> currentCounts = getCurrentInventoryCounts();
        
        // Skip first inventory load
        if (previousInventoryCounts.isEmpty()) {
            previousInventoryCounts = currentCounts;
            return;
        }
        
        // Detect changes in inventory
        boolean sourceConsumed = false;
        boolean targetProduced = false;
        
        // Check source items consumption
        if (trackingMode == TrackingMode.SOURCE_CONSUMPTION || 
            trackingMode == TrackingMode.EITHER || 
            trackingMode == TrackingMode.BOTH) {
            
            sourceConsumed = detectSourceConsumption(previousInventoryCounts, currentCounts);
        }
        
        // Check target items production
        if (trackingMode == TrackingMode.TARGET_PRODUCTION || 
            trackingMode == TrackingMode.EITHER || 
            trackingMode == TrackingMode.BOTH) {
            
            targetProduced = detectTargetProduction(previousInventoryCounts, currentCounts);
        }
        
        // Update processed count based on tracking mode
        boolean processDetected = false;
        
        switch (trackingMode) {
            case SOURCE_CONSUMPTION:
                processDetected = sourceConsumed;
                break;
            case TARGET_PRODUCTION:
                processDetected = targetProduced;
                break;
            case EITHER:
                processDetected = sourceConsumed || targetProduced;
                break;
            case BOTH:
                processDetected = sourceConsumed && targetProduced;
                break;
        }
        
        if (processDetected) {
            processedCount++;
            isProcessingActive = true;
            lastInventoryChange = Instant.now();
            log.debug("Process detected: {} -> {}/{}",
                    trackingMode, processedCount, currentTargetCount);
        } else {
            // If no changes detected for 3 seconds, consider processing inactive
            if (isProcessingActive && Instant.now().minusSeconds(3).isAfter(lastInventoryChange)) {
                isProcessingActive = false;
            }
        }
        
        // Update previous counts for next comparison
        previousInventoryCounts = currentCounts;
    }
    
    /**
     * Detect if source items were consumed by comparing previous and current inventory
     */
    private boolean detectSourceConsumption(Map<String, Integer> previous, Map<String, Integer> current) {
        if (sourceItems.isEmpty()) {
            return false;
        }
        
        // Check if all source items were reduced by expected amounts
        for (ItemTracker sourceItem : sourceItems) {
            int prevCount = previous.getOrDefault(sourceItem.getItemName(), 0);
            int currCount = current.getOrDefault(sourceItem.getItemName(), 0);
            
            // Check if the item was consumed by the expected amount
            if (currCount >= prevCount || prevCount - currCount != sourceItem.getQuantityPerProcess()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Detect if target items were produced by comparing previous and current inventory
     */
    private boolean detectTargetProduction(Map<String, Integer> previous, Map<String, Integer> current) {
        if (targetItems.isEmpty()) {
            return false;
        }
        
        // Check if all target items were increased by expected amounts
        for (ItemTracker targetItem : targetItems) {
            int prevCount = previous.getOrDefault(targetItem.getItemName(), 0);
            int currCount = current.getOrDefault(targetItem.getItemName(), 0);
            
            // Check if the item was produced by the expected amount
            if (currCount <= prevCount || currCount - prevCount != targetItem.getQuantityPerProcess()) {
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Get current inventory counts for relevant items
     */
    private Map<String, Integer> getCurrentInventoryCounts() {
        Map<String, Integer> counts = new HashMap<>();
        
        // Get all inventory items
        List<Rs2ItemModel> invItems = Rs2Inventory.all();
        
        // Count source items
        for (ItemTracker sourceItem : sourceItems) {
            int total = countItems(invItems, sourceItem.getItemPattern());
            counts.put(sourceItem.getItemName(), total);
        }
        
        // Count target items
        for (ItemTracker targetItem : targetItems) {
            int total = countItems(invItems, targetItem.getItemPattern());
            counts.put(targetItem.getItemName(), total);
        }
        
        return counts;
    }
    
    /**
     * Count items in inventory that match a pattern
     */
    private int countItems(List<Rs2ItemModel> items, Pattern pattern) {
        return items.stream()
                .filter(item -> pattern.matcher(item.getName()).matches())
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }
    
    // Factory methods for logical conditions
    
    /**
     * Creates an AND logical condition requiring multiple processing conditions
     */
    public static LogicalCondition createAndCondition(List<ProcessItemCondition> conditions) {
        AndCondition andCondition = new AndCondition();
        for (ProcessItemCondition condition : conditions) {
            andCondition.addCondition(condition);
        }
        return andCondition;
    }
    
    /**
     * Creates an OR logical condition requiring any of multiple processing conditions
     */
    public static LogicalCondition createOrCondition(List<ProcessItemCondition> conditions) {
        OrCondition orCondition = new OrCondition();
        for (ProcessItemCondition condition : conditions) {
            orCondition.addCondition(condition);
        }
        return orCondition;
    }
}