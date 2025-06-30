package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.LogicalRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.util.RequirementSelector;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Represents a comprehensive inventory and equipment layout plan.
 * This class manages the optimal placement of items across equipment slots and inventory slots,
 * considering constraints, priorities, and availability.
 */
@Slf4j
@Getter
public class InventorySetupPlanner {
    
    // Equipment slot assignments (fixed positions)
    private final Map<EquipmentInventorySlot, ItemRequirement> equipmentAssignments = new HashMap<>();
    
    // Specific inventory slot assignments (0-27)
    private final Map<Integer, ItemRequirement> inventorySlotAssignments = new HashMap<>();
    
    // Flexible inventory items that can be placed in any available slot
    private final List<ItemRequirement> flexibleInventoryItems = new ArrayList<>();
    
    // Missing mandatory items that could not be fulfilled
    private final List<ItemRequirement> missingMandatoryItems = new ArrayList<>();
    
    // Missing mandatory equipment slots
    private final Set<EquipmentInventorySlot> missingMandatoryEquipment = new HashSet<>();
    
    /**
     * Adds an equipment slot assignment.
     */
    public void addEquipmentSlotAssignment(EquipmentInventorySlot slot, ItemRequirement item) {
        equipmentAssignments.put(slot, item);
        log.debug("Assigned {} to equipment slot {}", item.getName(), slot);
    }
    
    /**
     * Adds a specific inventory slot assignment with stackability validation.
     */
    public void addInventorySlotAssignment(int slot, ItemRequirement item) {
        if (slot < 0 || slot > 27) {
            throw new IllegalArgumentException("Invalid inventory slot: " + slot);
        }
        
        // Validate stackability constraints
        if (item.getAmount() > 1 && !item.isStackable()) {
            log.warn("Cannot assign non-stackable item {} with amount {} to single slot {}", 
                    item.getName(), item.getAmount(), slot);
            // For non-stackable items with amount > 1, we need to handle differently
            // This should be caught earlier in the planning process
            return;
        }
        
        inventorySlotAssignments.put(slot, item);
        log.debug("Assigned {} (amount: {}, stackable: {}) to inventory slot {}", 
                item.getName(), item.getAmount(), item.isStackable(), slot);
    }
    
    /**
     * Adds a flexible inventory item with stackability considerations.
     */
    public void addFlexibleInventoryItem(ItemRequirement item) {
        // Validate that the item can fit in inventory
        if (!item.canFitInInventory()) {
            log.warn("Item {} requires {} slots but inventory is full", 
                    item.getName(), item.getRequiredInventorySlots());
            if (item.isMandatory()) {
                addMissingMandatoryInventoryItem(item);
            }
            return;
        }
        
        flexibleInventoryItems.add(item);
        log.debug("Added flexible inventory item: {} (requires {} slots)", 
                item.getName(), item.getRequiredInventorySlots());
    }
    
    /**
     * Adds a missing mandatory item.
     */
    public void addMissingMandatoryInventoryItem(ItemRequirement item) {
        missingMandatoryItems.add(item);
        log.warn("Missing mandatory item: {}", item.getName());
    }
    
    /**
     * Adds a missing mandatory equipment slot.
     */
    public void addMissingMandatoryEquipment(EquipmentInventorySlot slot) {
        missingMandatoryEquipment.add(slot);
        log.warn("Missing mandatory equipment for slot: {}", slot);
    }
    
    /**
     * Checks if an equipment slot is already occupied in this plan.
     */
    public boolean isEquipmentSlotOccupied(EquipmentInventorySlot slot) {
        return equipmentAssignments.containsKey(slot);
    }
    
    /**
     * Checks if an inventory slot is already occupied in this plan.
     */
    public boolean isInventorySlotOccupied(int slot) {
        return inventorySlotAssignments.containsKey(slot);
    }
    
    /**
     * Checks if the plan is feasible (no missing mandatory items).
     */
    public boolean isFeasible() {
        return missingMandatoryItems.isEmpty() && missingMandatoryEquipment.isEmpty();
    }
    
    /**
     * Gets the total number of inventory slots that will be occupied.
     * Properly accounts for stackability and amounts.
     */
    public int getTotalInventorySlotsNeeded() {
        int slotsNeeded = inventorySlotAssignments.size();
        
        // Calculate slots needed for flexible items considering stackability
        for (ItemRequirement item : flexibleInventoryItems) {
            slotsNeeded += item.getRequiredInventorySlots();
        }
        
        return slotsNeeded;
    }
    
    /**
     * Checks if the plan fits within inventory capacity (28 slots).
     */
    public boolean fitsInInventory() {
        return getTotalInventorySlotsNeeded() <= 28;
    }
    
    /**
     * Gets all occupied inventory slots.
     */
    public Set<Integer> getOccupiedInventorySlots() {
        Set<Integer> occupied = new HashSet<>(inventorySlotAssignments.keySet());
        
        // For flexible items, we'd need to simulate placement
        // For now, just assume they'll fit in remaining slots
        int flexibleItemsPlaced = 0;
        for (int slot = 0; slot < 28 && flexibleItemsPlaced < flexibleInventoryItems.size(); slot++) {
            if (!occupied.contains(slot)) {
                occupied.add(slot);
                flexibleItemsPlaced++;
            }
        }
        
        return occupied;
    }
    
    /**
     * Optimizes the placement of flexible inventory items.
     * This method attempts to find the best slots for items that don't have specific slot requirements.
     * Considers stackability, space constraints, and item consolidation opportunities.
     */
    public void optimizeFlexibleItemPlacement() {
        if (flexibleInventoryItems.isEmpty()) {
            return;
        }
        
        // First, try to consolidate stackable items of the same type
        consolidateStackableItems();
        
        // Sort flexible items by priority and rating
        flexibleInventoryItems.sort((a, b) -> {
            int priorityCompare = a.getPriority().compareTo(b.getPriority());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            return Integer.compare(b.getRating(), a.getRating());
        });
        
        // Check if we have enough space
        if (!fitsInInventory()) {
            log.warn("Not enough inventory space for all items. Need {} slots, but only 28 available.",
                    getTotalInventorySlotsNeeded());
            
            // Attempt space optimization before removing items
            if (attemptSpaceOptimization()) {
                log.info("Space optimization successful - all items now fit");
            } else {
                // Remove lowest priority items until we fit, considering stackability
                removeItemsUntilFit();
            }
        }
        
        log.debug("Optimized placement for {} flexible inventory items (total slots needed: {})", 
                flexibleInventoryItems.size(), getTotalInventorySlotsNeeded());
    }
    
    /**
     * Consolidates stackable items of the same type to save space.
     */
    private void consolidateStackableItems() {
        Map<List<Integer>, ItemRequirement> stackableItems = new HashMap<>();
        List<ItemRequirement> toRemove = new ArrayList<>();
        List<ItemRequirement> toAdd = new ArrayList<>();
        
        for (ItemRequirement item : flexibleInventoryItems) {
            if (item.isStackable()) {
                List<Integer> itemIds = item.getIds();
                if (stackableItems.containsKey(itemIds)) {
                    // Found another item of the same type - consolidate
                    ItemRequirement existing = stackableItems.get(itemIds);
                    int newAmount = existing.getAmount() + item.getAmount();
                    
                    // Create consolidated item
                    ItemRequirement consolidated = existing.copyWithAmount(newAmount);
                    
                    // Mark items for replacement
                    toRemove.add(existing);
                    toRemove.add(item);
                    toAdd.add(consolidated);
                    
                    // Update the map
                    stackableItems.put(itemIds, consolidated);
                    
                    log.debug("Consolidated stackable items: {} + {} = {} (total: {})", 
                            existing.getName(), item.getName(), consolidated.getName(), newAmount);
                } else {
                    stackableItems.put(itemIds, item);
                }
            }
        }
        
        // Apply consolidation
        flexibleInventoryItems.removeAll(toRemove);
        flexibleInventoryItems.addAll(toAdd);
    }
    
    /**
     * Attempts various space optimization strategies.
     * @return true if optimization freed enough space
     */
    private boolean attemptSpaceOptimization() {
        int originalSlotsNeeded = getTotalInventorySlotsNeeded();
        
        // Strategy 1: Look for items that could be moved to specific slots to free flexible space
        optimizeSlotUtilization();
        
        // Strategy 2: Consolidate any remaining stackable items
        consolidateStackableItems();
        
        int newSlotsNeeded = getTotalInventorySlotsNeeded();
        boolean improved = newSlotsNeeded < originalSlotsNeeded;
        
        if (improved) {
            log.info("Space optimization reduced slot usage from {} to {}", originalSlotsNeeded, newSlotsNeeded);
        }
        
        return fitsInInventory();
    }
    
    /**
     * Optimizes slot utilization by moving flexible items to specific slots when beneficial.
     */
    private void optimizeSlotUtilization() {
        // Find unused specific slots that could accommodate flexible items
        Set<Integer> usedSlots = new HashSet<>(inventorySlotAssignments.keySet());
        List<Integer> availableSlots = new ArrayList<>();
        
        for (int slot = 0; slot < 28; slot++) {
            if (!usedSlots.contains(slot)) {
                availableSlots.add(slot);
            }
        }
        
        // Try to move single-slot flexible items to specific slots
        Iterator<ItemRequirement> flexIterator = flexibleInventoryItems.iterator();
        while (flexIterator.hasNext() && !availableSlots.isEmpty()) {
            ItemRequirement item = flexIterator.next();
            
            // Only move items that need exactly 1 slot
            if (item.getRequiredInventorySlots() == 1) {
                int targetSlot = availableSlots.remove(0);
                
                // Create slot-specific copy
                ItemRequirement slotSpecific = item.copyWithSpecificSlot(targetSlot);
                
                // Move to specific slot assignment
                inventorySlotAssignments.put(targetSlot, slotSpecific);
                flexIterator.remove();
                
                log.debug("Moved flexible item {} to specific slot {} for optimization", 
                        item.getName(), targetSlot);
            }
        }
    }
    
    /**
     * Removes lowest priority items until the plan fits in inventory.
     * CRITICAL: Never removes MANDATORY items - they are protected from removal.
     */
    private void removeItemsUntilFit() {
        List<ItemRequirement> toRemove = new ArrayList<>();
        
        // Sort flexible items by priority (mandatory items should not be removed)
        flexibleInventoryItems.sort((a, b) -> {
            // Mandatory items always stay (higher priority)
            int priorityCompare = a.getPriority().compareTo(b.getPriority());
            if (priorityCompare != 0) {
                return priorityCompare;
            }
            // For same priority, prefer higher rating
            return Integer.compare(b.getRating(), a.getRating());
        });
        
        while (!fitsInInventory() && !flexibleInventoryItems.isEmpty()) {
            // Find the lowest priority non-mandatory item to remove
            ItemRequirement itemToRemove = null;
            for (int i = flexibleInventoryItems.size() - 1; i >= 0; i--) {
                ItemRequirement item = flexibleInventoryItems.get(i);
                if (!item.isMandatory()) {
                    itemToRemove = item;
                    break;
                }
            }
            
            if (itemToRemove != null) {
                flexibleInventoryItems.remove(itemToRemove);
                toRemove.add(itemToRemove);
                log.info("Removed optional item due to space constraints: {} (needs {} slots)", 
                        itemToRemove.getName(), itemToRemove.getRequiredInventorySlots());
            } else {
                // All remaining items are mandatory - cannot remove any more
                log.error("Cannot fit all mandatory items in inventory. Need {} slots but only 28 available.", 
                        getTotalInventorySlotsNeeded());
                
                // Mark remaining mandatory items as missing if they don't fit
                for (ItemRequirement mandatoryItem : flexibleInventoryItems) {
                    if (mandatoryItem.isMandatory()) {
                        missingMandatoryItems.add(mandatoryItem);
                        log.error("Cannot fit mandatory item: {} (needs {} slots)", 
                                mandatoryItem.getName(), mandatoryItem.getRequiredInventorySlots());
                    }
                }
                
                // Clear all remaining flexible items since they can't fit
                flexibleInventoryItems.clear();
                break;
            }
        }
        
        if (!toRemove.isEmpty()) {
            log.debug("Removed {} optional items to fit inventory constraints", toRemove.size());
        }
    }
    
    /**
     * Gets a summary of the layout plan.
     */
    public String getSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Inventory Layout Plan Summary ===\n");
        sb.append("Equipment assignments: ").append(equipmentAssignments.size()).append("\n");
        sb.append("Specific inventory slots: ").append(inventorySlotAssignments.size()).append("\n");
        sb.append("Flexible inventory items: ").append(flexibleInventoryItems.size()).append("\n");
        sb.append("Total inventory slots needed: ").append(getTotalInventorySlotsNeeded()).append("/28\n");
        sb.append("Plan feasible: ").append(isFeasible()).append("\n");
        sb.append("Fits in inventory: ").append(fitsInInventory()).append("\n");
        
        if (!missingMandatoryItems.isEmpty()) {
            sb.append("Missing mandatory items: ");
            missingMandatoryItems.forEach(item -> sb.append(item.getName()).append(", "));
            sb.append("\n");
        }
        
        if (!missingMandatoryEquipment.isEmpty()) {
            sb.append("Missing mandatory equipment slots: ");
            missingMandatoryEquipment.forEach(slot -> sb.append(slot.name()).append(", "));
            sb.append("\n");
        }
        
        return sb.toString();
    }

    /**
     * Gets a detailed string representation of the inventory setup plan.
     * Shows equipment assignments, inventory slot assignments, and flexible items.
     * 
     * @return A comprehensive string describing the planned setup
     */
    public String getDetailedPlanString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Inventory Setup Plan Details ===\n");
        sb.append("Plan Feasible:\t\t").append(isFeasible() ? "Yes" : "No").append("\n");
        sb.append("Fits in Inventory:\t").append((getTotalInventorySlotsNeeded() <= 28) ? "Yes" : "No").append("\n");
        sb.append("Total Slots Needed:\t").append(getTotalInventorySlotsNeeded()).append("/28\n\n");
        
        // Equipment assignments
        sb.append("=== EQUIPMENT ASSIGNMENTS ===\n");
        if (equipmentAssignments.isEmpty()) {
            sb.append("\tNo equipment assignments.\n");
        } else {
            for (Map.Entry<EquipmentInventorySlot, ItemRequirement> entry : equipmentAssignments.entrySet()) {
                sb.append("\t").append(entry.getKey().name()).append(":\t");
                sb.append(formatItemRequirement(entry.getValue())).append("\n");
            }
        }
        sb.append("\n");
        
        // Specific inventory slot assignments
        sb.append("=== SPECIFIC INVENTORY SLOT ASSIGNMENTS ===\n");
        if (inventorySlotAssignments.isEmpty()) {
            sb.append("\tNo specific slot assignments.\n");
        } else {
            for (int slot = 0; slot < 28; slot++) {
                if (inventorySlotAssignments.containsKey(slot)) {
                    ItemRequirement item = inventorySlotAssignments.get(slot);
                    sb.append("\tSlot ").append(slot).append(":\t\t");
                    sb.append(formatItemRequirement(item)).append("\n");
                }
            }
        }
        sb.append("\n");
        
        // Flexible inventory items
        sb.append("=== FLEXIBLE INVENTORY ITEMS ===\n");
        if (flexibleInventoryItems.isEmpty()) {
            sb.append("\tNo flexible items.\n");
        } else {
            for (int i = 0; i < flexibleInventoryItems.size(); i++) {
                ItemRequirement item = flexibleInventoryItems.get(i);
                sb.append("\t").append(i + 1).append(". ").append(formatItemRequirement(item));
                sb.append(" (needs ").append(item.getRequiredInventorySlots()).append(" slots)\n");
            }
        }
        sb.append("\n");
        
        // Missing mandatory items
        if (!missingMandatoryItems.isEmpty()) {
            sb.append("=== MISSING MANDATORY ITEMS ===\n");
            for (int i = 0; i < missingMandatoryItems.size(); i++) {
                ItemRequirement item = missingMandatoryItems.get(i);
                sb.append("\t").append(i + 1).append(". ").append(formatItemRequirement(item)).append("\n");
            }
            sb.append("\n");
        }
        
        // Missing mandatory equipment
        if (!missingMandatoryEquipment.isEmpty()) {
            sb.append("=== MISSING MANDATORY EQUIPMENT SLOTS ===\n");
            for (EquipmentInventorySlot slot : missingMandatoryEquipment) {
                sb.append("\t").append(slot.name()).append("\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Formats an ItemRequirement for display.
     * 
     * @param item The item requirement to format
     * @return A formatted string representation
     */
    private String formatItemRequirement(ItemRequirement item) {
        StringBuilder sb = new StringBuilder();
        sb.append(item.getName());
        if (item.getAmount() > 1) {
            sb.append(" x").append(item.getAmount());
        }
        sb.append(" [").append(item.getPriority().name()).append(", Rating: ").append(item.getRating()).append("]");
        if (item.isStackable()) {
            sb.append(" (stackable)");
        }
        return sb.toString();
    }
    
    /**
     * Gets a summary of occupied slots for logging.
     */
    public String getOccupiedSlotsSummary() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== SLOT UTILIZATION SUMMARY ===\n");
        
        // Equipment slots
        sb.append("Equipment slots occupied: ").append(equipmentAssignments.size()).append("\n");
        if (!equipmentAssignments.isEmpty()) {
            sb.append("  Slots: ").append(equipmentAssignments.keySet().stream()
                .map(Object::toString)
                .collect(Collectors.joining(", "))).append("\n");
        }
        
        // Specific inventory slots
        sb.append("Specific inventory slots: ").append(inventorySlotAssignments.size()).append("\n");
        if (!inventorySlotAssignments.isEmpty()) {
            sb.append("  Slots: ").append(inventorySlotAssignments.keySet().stream()
                .sorted()
                .map(Object::toString)
                .collect(Collectors.joining(", "))).append("\n");
        }
        
        // Available inventory slots for flexible items
        Set<Integer> occupiedSlots = new HashSet<>(inventorySlotAssignments.keySet());
        int availableSlots = 28 - occupiedSlots.size();
        int flexibleSlotsNeeded = flexibleInventoryItems.stream()
            .mapToInt(ItemRequirement::getRequiredInventorySlots)
            .sum();
        
        sb.append("Available inventory slots: ").append(availableSlots).append("\n");
        sb.append("Flexible items slots needed: ").append(flexibleSlotsNeeded).append("\n");
        sb.append("Slot surplus/deficit: ").append(availableSlots - flexibleSlotsNeeded).append("\n");
        
        return sb.toString();
    }
    
    /**
     * Enhanced toString that provides detailed plan information.
     */
    @Override
    public String toString() {
        return getDetailedPlanString();
    }
    
    // ========== PLAN EXECUTION METHODS ==========
    
    /**
     * Banks all equipped and inventory items that are not part of this planned loadout.
     * This ensures a clean slate before executing the optimal layout plan.
     * 
     * @return true if banking was successful, false otherwise
     */
    public boolean bankItemsNotInPlan() {
        try {
            log.info("Analyzing current equipment and inventory state...");
            
            // Quick check: if plan is empty, bank everything
            boolean hasEquipmentPlan = !equipmentAssignments.isEmpty();
            boolean hasInventoryPlan = !inventorySlotAssignments.isEmpty() || !flexibleInventoryItems.isEmpty();
            
            if (!hasEquipmentPlan && !hasInventoryPlan) {
                log.info("Plan is empty - banking all equipment and inventory items");
            } else {
                log.info("Plan has {} equipment assignments, {} specific inventory slots, {} flexible items", 
                        equipmentAssignments.size(), 
                        inventorySlotAssignments.size(), 
                        flexibleInventoryItems.size());
            }
            
            // Ensure bank is open
            if (!Rs2Bank.isOpen()) {
                log.warn("Bank is not open for cleanup banking. Opening bank...");
                if (!Rs2Bank.walkToBankAndUseBank()) {
                    log.error("Failed to open bank for cleanup banking");
                    return false;
                }
                sleepUntil(() -> Rs2Bank.isOpen(), 5000);
            }
            
            boolean bankingSuccess = true;
            int itemsBanked = 0;
            
            // Step 1: Bank equipped items not in the plan
            log.info("Banking equipped items not in planned loadout...");
            for (EquipmentInventorySlot equipmentSlot : EquipmentInventorySlot.values()) {
                // Check if this slot is planned to have an item
                ItemRequirement plannedItem = equipmentAssignments.get(equipmentSlot);
                
                // Get currently equipped item in this slot
                Rs2ItemModel equippedItem = Rs2Equipment.get(equipmentSlot);
                
                if (equippedItem != null) { // Something is equipped in this slot
                    boolean shouldKeepEquipped = false;
                    
                    if (plannedItem != null) {
                        // Check if the currently equipped item matches the planned item
                        shouldKeepEquipped = plannedItem.getId() == equippedItem.getId();
                    }
                    
                    if (!shouldKeepEquipped) {
                        // Unequip the item (this will move it to inventory, then we can bank it)
                        log.info("Unequipping item from slot {}: {}", equipmentSlot.name(), equippedItem.getName());
                        
                        if (Rs2Equipment.interact(item -> item.getSlot() == equipmentSlot.getSlotIdx(), "remove")) {
                            sleepUntil(() -> Rs2Equipment.get(equipmentSlot) == null, 3000);
                            
                            // Now bank the item from inventory
                            if (Rs2Inventory.hasItem(equippedItem.getId())) {
                                Rs2Bank.depositOne(equippedItem.getId());
                                sleepUntil(() -> !Rs2Inventory.hasItem(equippedItem.getId()), 3000);
                                itemsBanked++;
                            }
                        } else {
                            log.warn("Failed to unequip item from slot {}", equipmentSlot.name());
                            bankingSuccess = false;
                        }
                    } else {
                        log.debug("Keeping equipped item in slot {}: matches planned loadout", equipmentSlot.name());
                    }
                }
            }
            
            // Step 2: Bank inventory items not in the plan
            log.info("Banking inventory items not in planned loadout...");
            
            // Collect all planned item IDs for quick lookup
            Set<Integer> plannedItemIds = new HashSet<>();
            
            // Add planned equipment item IDs
            for (ItemRequirement equippedItem : equipmentAssignments.values()) {
                plannedItemIds.add(equippedItem.getId());
            }
            
            // Add planned specific slot item IDs
            for (ItemRequirement slotItem : inventorySlotAssignments.values()) {
                plannedItemIds.add(slotItem.getId());
            }
            
            // Add planned flexible item IDs
            for (ItemRequirement flexibleItem : flexibleInventoryItems) {
                plannedItemIds.add(flexibleItem.getId());
            }
            
            // Check each inventory slot
            for (int inventorySlot = 0; inventorySlot < 28; inventorySlot++) {
                final int currentSlot = inventorySlot;  // Make it effectively final for lambda
                Rs2ItemModel currentItem = Rs2Inventory.getItemInSlot(inventorySlot);
                if (currentItem != null) {
                    boolean shouldKeepInInventory = false;
                    
                    // Check if this item is part of the planned loadout
                    if (plannedItemIds.contains(currentItem.getId())) {
                        // Further check: is this item in the right place according to the plan?
                        shouldKeepInInventory = isItemInCorrectPlanPosition(currentItem, inventorySlot);
                    }
                    
                    if (!shouldKeepInInventory) {
                        // Bank the item
                        log.info("Banking inventory item from slot {}: {} x{}", 
                                inventorySlot, currentItem.getName(), currentItem.getQuantity());
                        Rs2Bank.depositOne(currentItem.getId());
                        if (  sleepUntil(() -> Rs2Inventory.getItemInSlot(currentSlot) == null || 
                                     Rs2Inventory.getItemInSlot(currentSlot).getId() != currentItem.getId(), 3000)) {                           
                            itemsBanked++;
                        } else {
                            log.warn("Failed to bank item from inventory slot {}: {}", inventorySlot, currentItem.getName());
                            bankingSuccess = false;
                        }
                    } else {
                        log.debug("Keeping inventory item in slot {}: {} (matches planned position)", 
                                inventorySlot, currentItem.getName());
                    }
                }
            }
            
            if (bankingSuccess) {
                log.info("Successfully banked {} items not in planned loadout", itemsBanked);
            } else {
                log.warn("Banking completed with some failures. {} items banked.", itemsBanked);
            }
            
            return bankingSuccess;
            
        } catch (Exception e) {
            log.error("Error banking items not in plan: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Checks if an item is in the correct position according to this plan.
     * This considers both specific slot assignments and flexible item allowances.
     * 
     * @param currentItem The item currently in inventory
     * @param currentSlot The slot where the item is currently located
     * @return true if the item should stay in its current position, false if it should be banked
     */
    private boolean isItemInCorrectPlanPosition(Rs2ItemModel currentItem, int currentSlot) {
        int itemId = currentItem.getId();
        int itemQuantity = currentItem.getQuantity();
        
        // Check if this slot has a specific assignment that matches
        ItemRequirement specificSlotItem = inventorySlotAssignments.get(currentSlot);
        if (specificSlotItem != null) {
            // Check if the current item matches the planned item for this slot
            if (specificSlotItem.getId() == itemId) {
                // Check quantity requirements
                if (specificSlotItem.getAmount() <= itemQuantity) {
                    log.debug("Item {} in slot {} matches specific slot assignment", currentItem.getName(), currentSlot);
                    return true;
                } else {
                    log.debug("Item {} in slot {} matches ID but insufficient quantity: {} < {}", 
                            currentItem.getName(), currentSlot, itemQuantity, specificSlotItem.getAmount());
                    return false;
                }
            } else {
                log.debug("Item {} in slot {} doesn't match specific slot assignment", currentItem.getName(), currentSlot);
                return false;
            }
        }
        
        // Check if this item is among the flexible items
        for (ItemRequirement flexibleItem : flexibleInventoryItems) {
            if (flexibleItem.getId() == itemId) {
                log.debug("Item {} in slot {} is a planned flexible item", currentItem.getName(), currentSlot);
                return true; // Flexible items can be anywhere
            }
        }
        
        // Not found in any planned positions
        log.debug("Item {} in slot {} is not part of the planned loadout", currentItem.getName(), currentSlot);
        return false;
    }
    
    /**
     * Executes this inventory and equipment layout plan.
     * Withdraws and equips items according to the optimal layout.
     * 
     * @return true if execution was successful
     */
    public boolean executePlan() {
        try {
            log.info("\n--- Executing Inventory and Equipment Layout Plan ---");
            
            // Ensure bank is open
            if (!Rs2Bank.isOpen()) {
                if (!Rs2Bank.walkToBankAndUseBank()) {
                    log.error("Failed to open bank for plan execution");
                    return false;
                }
                sleepUntil(() -> Rs2Bank.isOpen(), 5000);
            }
            
            boolean success = true;
            
            // Step 1: Handle equipment assignments
            if (!equipmentAssignments.isEmpty()) {
                log.info("Executing equipment assignments ({} items)...", equipmentAssignments.size());
                for (Map.Entry<EquipmentInventorySlot, ItemRequirement> entry : equipmentAssignments.entrySet()) {
                    EquipmentInventorySlot slot = entry.getKey();
                    ItemRequirement item = entry.getValue();
                    
                    if (!handleEquipmentAssignment(slot, item)) {
                        log.error("Failed to fulfill equipment assignment: {} -> {}", slot.name(), item.getName());
                        success = false;
                    }
                }
            }
            
            // Step 2: Handle specific inventory slot assignments
            if (!inventorySlotAssignments.isEmpty()) {
                log.info("Executing specific inventory slot assignments ({} items)...", inventorySlotAssignments.size());
                for (Map.Entry<Integer, ItemRequirement> entry : inventorySlotAssignments.entrySet()) {
                    Integer slot = entry.getKey();
                    ItemRequirement item = entry.getValue();
                    
                    if (!handleInventorySlotAssignment(slot, item)) {
                        log.error("Failed to fulfill inventory slot assignment: slot {} -> {}", slot, item.getName());
                        success = false;
                    }
                }
            }
            
            // Step 3: Handle flexible inventory items
            if (!flexibleInventoryItems.isEmpty()) {
                log.info("Executing flexible inventory items ({} items)...", flexibleInventoryItems.size());
                for (ItemRequirement item : flexibleInventoryItems) {
                    if (!handleFlexibleInventoryItem(item)) {
                        log.error("Failed to fulfill flexible inventory item: {}", item.getName());
                        // Don't mark as failed for optional items
                        if (item.isMandatory()) {
                            success = false;
                        }
                    }
                }
            }
            
            if (success) {
                log.info("Successfully executed all plan assignments");
            } else {
                log.error("Some plan assignments failed to execute");
            }
            
            return success;
            
        } catch (Exception e) {
            log.error("Error executing plan: {}", e.getMessage(), e);
            return false;
        }
    }
    
    /**
     * Handles a single equipment assignment.
     */
    private boolean handleEquipmentAssignment(EquipmentInventorySlot slot, ItemRequirement item) {
        log.debug("Handling equipment assignment: {} -> {}", slot.name(), item.getName());
        
        // Check if already equipped correctly (considering fuzzy matching for variations)
        Rs2ItemModel currentEquipped = Rs2Equipment.get(slot);
        if (currentEquipped != null) {
            if (item.isFuzzy()) {
                // For fuzzy items, check if any variation is equipped
                Collection<Integer> variations = net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsVariationMapping.getVariations(item.getId());
                if (variations.contains(currentEquipped.getId())) {
                    log.debug("Item {} (or variation) already equipped in slot {}", item.getName(), slot.name());
                    return true;
                }
            } else {
                // Exact ID match
                if (item.getId() == currentEquipped.getId()) {
                    log.debug("Item {} already equipped in slot {}", item.getName(), slot.name());
                    return true;
                }
            }
        }
        
        // Try to withdraw and equip the item if available
        if (item.isAvailableInBank()) {
            int itemId = item.getId();
            Rs2Bank.withdrawAndEquip(itemId);
            if (sleepUntil(() -> Rs2Equipment.get(slot) != null &&
                        Rs2Equipment.get(slot).getId() == itemId, 5000)) {
                log.debug("Withdrew {} for equipment slot {}", item.getName(), slot.name());
                log.info("Successfully equipped {} in slot {}", item.getName(), slot.name());
                return true;
            }
        }
        
        log.error("Failed to equip {} in slot {}", item.getName(), slot.name());
        return false;
    }
    
    /**
     * Handles a single inventory slot assignment.
     */
    private boolean handleInventorySlotAssignment(int slot, ItemRequirement item) {
        log.debug("Handling inventory slot assignment: slot {} -> {}", slot, item.getName());
        
        // Use the static utility method from ItemRequirement
        return ItemRequirement.withdrawAndPlaceInSpecificSlot(slot, item);
    }
    
    /**
     * Handles a single flexible inventory item.
     */
    private boolean handleFlexibleInventoryItem(ItemRequirement item) {
        log.debug("Handling flexible inventory item: {}", item.getName());
        
        // Check if item is already in inventory with sufficient amount (handles fuzzy matching)
        if (item.isAvailableInInventory()) {
            log.debug("Flexible item {} already in inventory with sufficient amount", item.getName());
            return true;
        }
        
        // Try to withdraw the item if available in bank
        if (item.isAvailableInBank()) {
            int itemId = item.getId();
            if (Rs2Bank.withdrawX(itemId, item.getAmount())) {
                log.debug("Withdrew flexible item: {} x{}", item.getName(), item.getAmount());
                return true;
            } else {
                log.warn("Failed to withdraw flexible item: {} x{}", item.getName(), item.getAmount());
            }
        }
        
        log.error("Could not withdraw flexible item: {}", item.getName());
        return false;
    }
    
    // ========== VALIDATION AND ANALYSIS METHODS ==========
    
    /**
     * Performs an enhanced early feasibility check to determine if all mandatory requirements can be fulfilled.
     * This simulates the actual planning logic to detect slot competition and conflicts early.
     * 
     * @param context The schedule context to check
     * @param equipmentReqs Equipment requirements by slot
     * @param slotSpecificReqs Inventory slot requirements
     * @return true if all mandatory requirements appear to be fulfillable
     */
    public static boolean performEarlyFeasibilityCheck(
            net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext context,
            Map<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> equipmentReqs,
            Map<Integer, LinkedHashSet<LogicalRequirement>> slotSpecificReqs) {
        
        boolean allMandatoryFulfillable = true;
        List<String> unfulfillableItems = new ArrayList<>();
        List<String> conflictWarnings = new ArrayList<>();
        
        // Track equipment slot assignments to detect conflicts
        Map<EquipmentInventorySlot, ItemRequirement> simulatedEquipmentAssignments = new HashMap<>();
        Set<ItemRequirement> alreadyAssigned = new HashSet<>();
        
        // Enhanced check for mandatory equipment requirements with conflict detection
        for (Map.Entry<EquipmentInventorySlot, LinkedHashSet<LogicalRequirement>> entry : equipmentReqs.entrySet()) {
            EquipmentInventorySlot slot = entry.getKey();
            LinkedHashSet<LogicalRequirement> logicalReqs = entry.getValue();
            
            // Filter by context
            List<LogicalRequirement> contextReqs = LogicalRequirement.filterByContext(new ArrayList<>(logicalReqs), context);
            
            // Get all mandatory requirements for this slot
            List<ItemRequirement> mandatoryItems = LogicalRequirement.extractMandatoryItemRequirements(contextReqs);
            
            if (!mandatoryItems.isEmpty()) {
                // Check if there's a dummy item requirement that blocks this slot
                boolean hasDummyRequirement = mandatoryItems.stream().anyMatch(ItemRequirement::isDummyItemRequirement);
                
                if (hasDummyRequirement) {
                    // Dummy items are always "available" and block the slot
                    ItemRequirement dummyItem = mandatoryItems.stream()
                        .filter(ItemRequirement::isDummyItemRequirement)
                        .findFirst()
                        .orElse(null);
                    
                    if (dummyItem != null) {
                        simulatedEquipmentAssignments.put(slot, dummyItem);
                        alreadyAssigned.add(dummyItem);
                        log.debug("Early feasibility: Dummy item blocks equipment slot {}", slot);
                    }
                } else {
                    // Use enhanced selection logic that prioritizes EQUIPMENT over EITHER
                    ItemRequirement bestItem = RequirementSelector.findBestAvailableItemForEquipmentSlot(contextReqs, slot, alreadyAssigned);
                    
                    if (bestItem != null) {
                        simulatedEquipmentAssignments.put(slot, bestItem);
                        alreadyAssigned.add(bestItem);
                        log.debug("Early feasibility: Can assign {} to equipment slot {}", bestItem.getName(), slot);
                    } else {
                        unfulfillableItems.add("Equipment slot " + slot + " (mandatory items: " + 
                            mandatoryItems.stream().map(ItemRequirement::getName).collect(Collectors.joining(", ")) + ")");
                        allMandatoryFulfillable = false;
                        log.warn("Early feasibility: Cannot fulfill mandatory equipment requirement for slot {}", slot);
                    }
                }
            }
        }
        
        // Enhanced check for mandatory inventory requirements
        LinkedHashSet<LogicalRequirement> anySlotReqs = slotSpecificReqs.getOrDefault(-1, new LinkedHashSet<>());
        List<LogicalRequirement> contextInventoryReqs = LogicalRequirement.filterByContext(new ArrayList<>(anySlotReqs), context);
        
        for (LogicalRequirement logicalReq : contextInventoryReqs) {
            List<ItemRequirement> mandatoryItems = LogicalRequirement.extractMandatoryItemRequirements(Arrays.asList(logicalReq));
            
            if (!mandatoryItems.isEmpty()) {
                ItemRequirement bestItem = RequirementSelector.findBestAvailableItemNotAlreadyPlanned(Arrays.asList(logicalReq), null);
                
                if (bestItem == null) {
                    unfulfillableItems.add("Inventory requirement (mandatory items: " + 
                        mandatoryItems.stream().map(ItemRequirement::getName).collect(Collectors.joining(", ")) + ")");
                    allMandatoryFulfillable = false;
                    log.warn("Early feasibility: Cannot fulfill mandatory inventory requirement for: {}", logicalReq.getDescription());
                } else {
                    alreadyAssigned.add(bestItem);
                    log.debug("Early feasibility: Can fulfill inventory requirement with {}", bestItem.getName());
                }
            }
        }
        
        // Log results
        if (!allMandatoryFulfillable) {
            log.error("Early feasibility check FAILED. Unfulfillable mandatory requirements:");
            unfulfillableItems.forEach(item -> log.error("  - {}", item));
        } else {
            log.info("Early feasibility check PASSED. All mandatory requirements appear fulfillable.");
        }
        
        if (!conflictWarnings.isEmpty()) {
            log.warn("Potential conflicts detected:");
            conflictWarnings.forEach(warning -> log.warn("  - {}", warning));
        }
        
        return allMandatoryFulfillable;
    }
    
    /**
     * Optimizes and validates the entire plan with comprehensive checks.
     * This includes space optimization, conflict resolution, feasibility checking, and comprehensive mandatory validation.
     * 
     * @param plan The inventory setup plan to optimize and validate
     * @return true if the plan is valid and feasible, false otherwise
     */
    public static boolean optimizeAndValidatePlan(InventorySetupPlanner plan) {
        // Step 1: Optimize flexible item placement
        plan.optimizeFlexibleItemPlacement();
        
        // Step 2: Validate plan feasibility
        if (!plan.isFeasible()) {
            log.warn("Plan is not feasible - has missing mandatory items:");
            plan.getMissingMandatoryItems().forEach(item -> 
                log.warn("  - Missing: {}", item.getName()));
            plan.getMissingMandatoryEquipment().forEach(slot -> 
                log.warn("  - Missing equipment slot: {}", slot));
            return false; // Early exit if plan is not feasible
        }
        
        // Step 3: Comprehensive mandatory requirements validation
        boolean allMandatorySatisfied = validateAllMandatoryRequirementsSatisfied(plan);
        if (!allMandatorySatisfied) {
            log.error("CRITICAL: Final validation failed - not all mandatory requirements are satisfied in the plan");
            return false; // Early exit if mandatory requirements are not satisfied
        }
        
        // Step 4: Validate inventory capacity
        if (!plan.fitsInInventory()) {
            log.warn("Plan does not fit in inventory - needs {} slots but only 28 available", 
                    plan.getTotalInventorySlotsNeeded());
            return false; // Early exit if plan exceeds inventory capacity
        } else {
            log.info("Plan successfully created - uses {}/28 inventory slots", 
                    plan.getTotalInventorySlotsNeeded());
        }
        
        // Step 5: Log summary for debugging
        if (log.isDebugEnabled()) {
            log.debug("Plan summary:\n{}", plan.getSummary());
        }
        return true; // Plan is valid and feasible
    }
    
    /**
     * Validates that all mandatory requirements are actually satisfied in the final plan.
     * This is a comprehensive check that goes beyond just checking for "missing" items.
     * 
     * @param plan The inventory setup plan to validate
     * @return true if all mandatory requirements are satisfied, false otherwise
     */
    public static boolean validateAllMandatoryRequirementsSatisfied(InventorySetupPlanner plan) {
        // This would need access to the registry, so we'll keep this method simpler
        // and focus on validating the plan itself rather than cross-referencing requirements
        
        // Check if all mandatory items in the plan are actually present
        boolean allSatisfied = true;
        List<String> unsatisfiedRequirements = new ArrayList<>();
        
        // Validate equipment assignments
        for (Map.Entry<EquipmentInventorySlot, ItemRequirement> entry : plan.getEquipmentAssignments().entrySet()) {
            ItemRequirement item = entry.getValue();
            if (item.isMandatory()) {
                // Use ItemRequirement's own availability checking which handles fuzzy matching and amounts
                if (!item.isAvailableInInventoryOrBank()) {
                    unsatisfiedRequirements.add(item.getName() + " (equipment slot: " + entry.getKey() + ")");
                    allSatisfied = false;
                }
            }
        }
        
        // Validate inventory assignments
        for (ItemRequirement item : plan.getInventorySlotAssignments().values()) {
            if (item.isMandatory()) {
                // Use ItemRequirement's own availability checking which handles fuzzy matching and amounts
                if (!item.isAvailableInInventoryOrBank()) {
                    unsatisfiedRequirements.add(item.getName() + " (inventory)");
                    allSatisfied = false;
                }
            }
        }
        
        // Validate flexible items
        for (ItemRequirement item : plan.getFlexibleInventoryItems()) {
            if (item.isMandatory()) {
                // Use ItemRequirement's own availability checking which handles fuzzy matching and amounts
                if (!item.isAvailableInInventoryOrBank()) {
                    unsatisfiedRequirements.add(item.getName() + " (flexible inventory)");
                    allSatisfied = false;
                }
            }
        }
        
        if (!allSatisfied) {
            log.error("Final validation failed. {} mandatory requirements not satisfied:", unsatisfiedRequirements.size());
            unsatisfiedRequirements.forEach(req -> log.error("  - {}", req));
        } else {
            log.debug("Final validation passed - all mandatory requirements in plan are satisfied");
        }
        
        return allSatisfied;
    }
    
    /**
     * Handles an item as flexible inventory item with proper conflict checking.
     * 
     * @param bestItem The item to handle as flexible
     * @param plan The inventory setup plan
     * @param alreadyPlanned Set of already planned items
     */
    public static void handleItemAsFlexible(ItemRequirement bestItem, InventorySetupPlanner plan, Set<ItemRequirement> alreadyPlanned) {
        plan.addFlexibleInventoryItem(bestItem);
        alreadyPlanned.add(bestItem);
        
        log.debug("Added {} as flexible inventory item (amount: {}, stackable: {})", 
                bestItem.getName(), bestItem.getAmount(), bestItem.isStackable());
    }
    
    /**
     * Handles missing mandatory items by adding them to the appropriate missing lists.
     * 
     * @param contextReqs The requirements that couldn't be fulfilled
     * @param plan The inventory setup plan
     * @param location Description of where the item was needed (for logging)
     */
    public static void handleMissingMandatoryItem(List<LogicalRequirement> contextReqs, InventorySetupPlanner plan, String location) {
        // Check if any requirement was mandatory
        boolean hasMandatory = LogicalRequirement.hasMandatoryItems(contextReqs);
        
        if (hasMandatory) {
            List<ItemRequirement> mandatoryItems = LogicalRequirement.extractMandatoryItemRequirements(contextReqs);
            for (ItemRequirement mandatoryItem : mandatoryItems) {
                plan.addMissingMandatoryInventoryItem(mandatoryItem);
                log.warn("Cannot fulfill mandatory item requirement for {}: {}", location, mandatoryItem.getName());
            }
        }
    }
}
