package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.client.plugins.banktags.tabs.Layout;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsItem;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsStackCompareID;
import net.runelite.client.plugins.microbot.inventorysetups.InventorySetupsVariationMapping;
import net.runelite.client.plugins.microbot.inventorysetups.MInventorySetupsPlugin;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.LogicalRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.OrRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional.ConditionalRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.OrRequirementMode;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.registry.RequirementRegistry;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.util.RequirementSelector;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Delayed;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import javax.swing.SwingUtilities;

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
    private final  Map<EquipmentInventorySlot, List<ItemRequirement>>  missingMandatoryEquipment = new HashMap<>();
    
    // Optional: Requirements planning support (for new planning approach)
    private RequirementRegistry registry;
    private TaskContext taskContext;
    private OrRequirementMode orRequirementMode = OrRequirementMode.ANY_COMBINATION; // Default mode
    
    // Flag to control whether to log recommended/optional missing items in comprehensive analysis
    private boolean logOptionalMissingItems = true; // Default to true for backward compatibility
    
    /**
     * Default constructor for backward compatibility.
     */
    public InventorySetupPlanner() {
        // Default constructor - uses existing functionality
    }
    
    /**
     * Enhanced constructor for requirements-based planning.
     * 
     * @param registry The requirement registry containing all requirements
     * @param TaskContext The schedule context (PRE_SCHEDULE or POST_SCHEDULE)
     * @param orRequirementMode How to handle OR requirements (ANY_COMBINATION or SINGLE_TYPE)
     */
    public InventorySetupPlanner(RequirementRegistry registry, TaskContext taskContext, OrRequirementMode orRequirementMode) {
        this.registry = registry;
        this.taskContext = taskContext;
        this.orRequirementMode = orRequirementMode;
        this.logOptionalMissingItems = true; // Default to logging optional items
    }
    
    /**
     * Enhanced constructor for requirements-based planning with optional item logging control.
     * 
     * @param registry The requirement registry containing all requirements
     * @param TaskContext The schedule context (PRE_SCHEDULE or POST_SCHEDULE)
     * @param orRequirementMode How to handle OR requirements (ANY_COMBINATION or SINGLE_TYPE)
     * @param logOptionalMissingItems Whether to log recommended/optional missing items in comprehensive analysis
     */
    public InventorySetupPlanner(RequirementRegistry registry, TaskContext taskContext, OrRequirementMode orRequirementMode, boolean logOptionalMissingItems) {
        this.registry = registry;
        this.taskContext = taskContext;
        this.orRequirementMode = orRequirementMode;
        this.logOptionalMissingItems = logOptionalMissingItems;
    }
    
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
        log.info("Assigned {} (amount: {}, stackable: {}) to inventory slot {}", 
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
        log.debug("Missing mandatory item: {}", item.getName());
    }
    
    /**
     * Adds a missing mandatory equipment slot.
     */
    public void addMissingMandatoryEquipment(EquipmentInventorySlot slot, ItemRequirement item) {
        missingMandatoryEquipment.computeIfAbsent(slot, k -> new ArrayList<>()).add(item);
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
        
        log.info("Optimized placement for {} flexible inventory items (total slots needed: {})", 
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
                    
                    log.info("Consolidated stackable items: {} + {} = {} (total: {})", 
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
                
                log.info("Moved flexible item {} to specific slot {} for optimization", 
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
            log.info("Removed {} optional items to fit inventory constraints", toRemove.size());
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
            missingMandatoryEquipment.forEach((slot, items) -> sb.append("\n\t"+slot.name()+": ")
                .append(items.stream().map(ItemRequirement::getName).collect(Collectors.joining(", ")))
                .append(""));
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
            missingMandatoryEquipment.forEach((slot, items) -> {
                sb.append("\t").append(slot.name()).append(": ");
                if (items != null && !items.isEmpty()) {
                    sb.append(items.stream()
                        .map(ItemRequirement::getName)
                        .collect(Collectors.joining(", ")));
                } else {
                    sb.append("No items listed");
                }
                sb.append("\n");
            });
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
    
    // ========== REQUIREMENTS PLANNING METHODS ==========
    
    /**
     * Creates an optimized inventory setup plan from a requirement registry.
     * This is the main entry point for the new requirements-based planning approach.
     * 
     * @return true if planning was successful, false if mandatory requirements could not be fulfilled
     */
    public boolean createPlanFromRequirements() {
        if (registry == null || taskContext == null) {
            throw new IllegalStateException("Cannot create plan from requirements: registry or TaskContext is null");
        }
        
        StringBuilder planningLog = new StringBuilder();
        planningLog.append("Starting comprehensive requirement analysis for context: ").append(taskContext)
                   .append(" with OR mode: ").append(orRequirementMode).append("\n");
        
        // Track already planned items to avoid double-processing EITHER requirements
        Set<ItemRequirement> alreadyPlanned = new HashSet<>();
        
        // Get all requirements filtered by context using new context-aware methods
        Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> equipmentReqs = 
            registry.getEquipmentRequirements(taskContext);
        Map<Integer, LinkedHashSet<ItemRequirement>> slotSpecificReqs = 
            registry.getInventoryRequirements(taskContext);
        LinkedHashSet<OrRequirement> anySlotReqs = registry.getAnySlotLogicalRequirements(taskContext);
        // Get conditional requirements that contain only ItemRequirements
        List<ConditionalRequirement> conditionalReqs = registry.getConditionalItemRequirements(taskContext);
        List<ConditionalRequirement> externalConditionalReqs = registry.getExternalConditionalItemRequirements(taskContext);
        
        // Process conditional requirements to get active ItemRequirements and merge them
        integrateConditionalRequirements(conditionalReqs, externalConditionalReqs, equipmentReqs, slotSpecificReqs,anySlotReqs);
        
        // Step 1: Plan equipment slots (these have fixed positions)
        planningLog.append("\n=== STEP 1: EQUIPMENT PLANNING ===\n");
        if (!planEquipmentSlotsFromCache(equipmentReqs, alreadyPlanned)) {
            planningLog.append("FAILED: Mandatory equipment cannot be fulfilled\n");
            log.debug(planningLog.toString());
            return false; // Early exit if mandatory equipment cannot be fulfilled
        }
        
        // Log status after equipment planning
        planningLog.append("Equipment assignments: ").append(equipmentAssignments.size()).append("\n");
        planningLog.append("Items in alreadyPlanned: ").append(alreadyPlanned.size()).append("\n");
        planningLog.append("Missing mandatory items so far: ").append(missingMandatoryItems.size()).append("\n");
        
        for (ItemRequirement planned : alreadyPlanned) {
            planningLog.append("  - Already planned: ").append(planned.getName())
                      .append(" (IDs: ").append(planned.getIds()).append(")\n");
        }
        
        // Step 2: Plan specific inventory slots (0-27, excluding items already planned in equipment)
        planningLog.append("\n=== STEP 2: SPECIFIC SLOT PLANNING ===\n");
        planSpecificInventorySlots(slotSpecificReqs, alreadyPlanned);
        
        // Log status after specific slot planning
        planningLog.append("Specific inventory slots: ").append(inventorySlotAssignments.size()).append("\n");
        planningLog.append("Items in alreadyPlanned: ").append(alreadyPlanned.size()).append("\n");
        planningLog.append("Missing mandatory items so far: ").append(missingMandatoryItems.size()).append("\n");
        
        // Step 3: Plan flexible inventory items (any slot allowed, from the any-slot cache)
        planningLog.append("\n=== STEP  3: FLEXIBLE PLANNING===\n");
        planFlexibleInventoryItems(anySlotReqs, alreadyPlanned);
        
        // Log status after flexible planning
        planningLog.append("Flexible inventory items: ").append(flexibleInventoryItems.size()).append("\n");
        planningLog.append("Items in alreadyPlanned: ").append(alreadyPlanned.size()).append("\n");
        planningLog.append("Missing mandatory items final: ").append(missingMandatoryItems.size()).append("\n");
        
        for (ItemRequirement missing : missingMandatoryItems) {
            planningLog.append("  - Missing: ").append(missing.getName())
                      .append(" (IDs: ").append(missing.getIds())
                      .append(", Priority: ").append(missing.getPriority()).append(")\n");
        }
        
        // Step 4: Analyze and log comprehensive requirement status
        planningLog.append(getComprehensiveRequirementAnalysis(true));
        
        // Step 5: Optimize and validate the entire plan
        planningLog.append("\n=== STEP 4: OPTIMIZATION AND VALIDATION ===\n");
        boolean planValid = optimizeAndValidatePlan(this);
        
        if (!planValid) {
            planningLog.append("FAILED: Plan optimization and validation failed - see comprehensive analysis above for details\n");
            log.error(planningLog.toString());
            return false;
        }
        
        planningLog.append("SUCCESS: Created and validated optimal layout plan for context: ").append(taskContext).append("\n");
        
        // Output all planning logs at once
        log.info(planningLog.toString());
        
        return true;
    }
    
    /**
     * Integrates conditional requirements into the equipment and slot-specific requirements.
     * This method evaluates active conditional requirements and merges their ItemRequirements
     * into the appropriate LogicalRequirements for each slot.
     * 
     * @param conditionalReqs Standard conditional requirements containing only ItemRequirements
     * @param externalConditionalReqs External conditional requirements containing only ItemRequirements
     * @param equipmentReqs Equipment requirements map to be updated
     * @param slotSpecificReqs Slot-specific requirements map to be updated
     */
    private void integrateConditionalRequirements(
            List<ConditionalRequirement> conditionalReqs,
            List<ConditionalRequirement> externalConditionalReqs,
            Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> equipmentReqs,
            Map<Integer, LinkedHashSet<ItemRequirement>> slotSpecificReqs,
            LinkedHashSet<OrRequirement> anySlotReqs
            ) {
        
        log.debug("Integrating {} standard and {} external conditional requirements", 
                conditionalReqs.size(), externalConditionalReqs.size());
        
        // Process standard conditional requirements
        for (ConditionalRequirement conditionalReq : conditionalReqs) {
            processConditionalRequirement(conditionalReq, equipmentReqs, slotSpecificReqs, anySlotReqs,"standard");
        }
        
        // Process external conditional requirements
        for (ConditionalRequirement conditionalReq : externalConditionalReqs) {
            processConditionalRequirement(conditionalReq, equipmentReqs, slotSpecificReqs,anySlotReqs, "external");
        }
        
        log.debug("Completed integration of conditional requirements");
    }
    
    /**
     * Processes a single conditional requirement and integrates its active ItemRequirements
     * into the appropriate LogicalRequirements.
     * 
     * @param conditionalReq The conditional requirement to process
     * @param equipmentReqs Equipment requirements map to be updated
     * @param slotSpecificReqs Slot-specific requirements map to be updated
     * @param source Source type for logging ("standard" or "external")
     */
    private void processConditionalRequirement(
            ConditionalRequirement conditionalReq,
            Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> equipmentReqs,
            Map<Integer, LinkedHashSet<ItemRequirement>> slotSpecificReqs,
            LinkedHashSet<OrRequirement> anySlotReqs,
            String source) {
        
        try {
            // Get active requirements from the conditional requirement
            List<Requirement> activeRequirements = conditionalReq.getActiveRequirements();
            
            if (activeRequirements.isEmpty()) {
                log.debug("No active requirements for {} conditional requirement: {}", source, conditionalReq.getName());
                return;
            }
            
            log.info("Processing {} active requirements from {} conditional requirement: {}", 
                    activeRequirements.size(), source, conditionalReq.getName());
            
            // Process each active requirement
            for (Requirement activeReq : activeRequirements) {
                if (activeReq instanceof ItemRequirement) {
                    ItemRequirement itemReq = (ItemRequirement) activeReq;
                    log.debug("Integrating active ItemRequirement: {}", itemReq.getName());
                    integrateActiveItemRequirement(itemReq, equipmentReqs, slotSpecificReqs,anySlotReqs);
                } else if (activeReq instanceof LogicalRequirement) {
                    LogicalRequirement logicalReq = (LogicalRequirement) activeReq;
                    if (logicalReq.containsOnlyItemRequirements()) {
                        // Extract all ItemRequirements from the LogicalRequirement
                        List<ItemRequirement> itemRequirements = logicalReq.getAllItemRequirements();
                        for (ItemRequirement itemReq : itemRequirements) {
                            log.debug("Integrating ItemRequirement from LogicalRequirement: {}", itemReq.getName());
                            integrateActiveItemRequirement(itemReq, equipmentReqs, slotSpecificReqs,anySlotReqs);
                        }
                    } else {
                        log.error("\n\tSkipping LogicalRequirement with mixed requirement types in conditional: {} - consider correct impllementation of the condtional requirement: {}", 
                                conditionalReq.getName());
                    }
                } else {
                    log.warn("Unexpected requirement type in conditional requirement: {} (type: {})", 
                            activeReq.getClass().getSimpleName(), activeReq.getName());
                }
            }
            
        } catch (Exception e) {
            log.error("Error processing {} conditional requirement '{}': {}", 
                    source, conditionalReq.getName(), e.getMessage(), e);
        }
    }
    
    /**
     * Integrates a single active ItemRequirement into the appropriate slot requirements.
     * 
     * @param itemReq The active ItemRequirement to integrate
     * @param equipmentReqs Equipment requirements map to be updated
     * @param slotSpecificReqs Slot-specific requirements map to be updated
     */
    private void integrateActiveItemRequirement(
            ItemRequirement itemReq,
            Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> equipmentReqs,
            Map<Integer, LinkedHashSet<ItemRequirement>> slotSpecificReqs,
            LinkedHashSet<OrRequirement> anyslotSpecificReqs
            ) {
        
        try {
            switch (itemReq.getRequirementType()) {
                case EQUIPMENT:
                    if (itemReq.getEquipmentSlot() != null) {
                        integrateIntoEquipmentSlot(itemReq, equipmentReqs);
                    }
                    break;
                    
                case INVENTORY:
                    int slot = itemReq.hasSpecificInventorySlot() ? itemReq.getInventorySlot() : -1;
                    if  (slot!=-1){
                        integrateIntoInventorySlot(itemReq, slotSpecificReqs, slot);
                    }else{
                        // Flexible inventory item, add to anyslotSpecificReqs
                        log.debug("Adding flexible ItemRequirement '{}' to anyslotSpecificReqs", itemReq.getName());
                        anyslotSpecificReqs.add(new OrRequirement(
                            itemReq.getPriority(),
                            itemReq.getRating(),
                            "Flexible requirement for inventory. based on conditional requirement: " + itemReq.getName(),
                            itemReq.getTaskContext(),
                            ItemRequirement.class,
                            itemReq
                        ));
                    }
                    break;
                    
                case EITHER:
                    // For EITHER requirements, we can choose the best placement
                    // For now, prefer equipment slot if available, otherwise flexible inventory
                     OrRequirement newOrReq = new OrRequirement(
                    itemReq.getPriority(),
                    itemReq.getRating(),
                    "Conditional requirement for inventory flexible",
                    itemReq.getTaskContext(),
                    ItemRequirement.class,
                    itemReq
                    );
                    anyslotSpecificReqs.add(newOrReq);
                    break;
                    
                default:
                    log.debug("Skipping ItemRequirement with non-slot type: {} ({})", 
                            itemReq.getName(), itemReq.getRequirementType());
                    break;
            }
            
        } catch (Exception e) {
            log.error("Error integrating active ItemRequirement '{}': {}", itemReq.getName(), e.getMessage(), e);
        }
    }
    
    /**
     * Integrates an ItemRequirement into an equipment slot.
     * Properly merges conditional requirements with existing OR requirements.
     * 
     * @param itemReq The ItemRequirement to integrate
     * @param equipmentReqs Equipment requirements map to be updated
     */
    private void integrateIntoEquipmentSlot(
            ItemRequirement itemReq,
            Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> equipmentReqs) {
        
        EquipmentInventorySlot slot = itemReq.getEquipmentSlot();
        LinkedHashSet<ItemRequirement> existingLogical = equipmentReqs.getOrDefault(slot , new LinkedHashSet<>());
        
    
        // If existing is not an OrRequirement or we couldn't merge, try direct addition
        try {
            existingLogical.add(itemReq);
            log.debug("Added conditional ItemRequirement '{}' to existing equipment slot {}", 
                    itemReq.getName(), slot);
        } catch (IllegalArgumentException e) {
            log.error("Cannot integrate conditional ItemRequirement '{}' into equipment slot {} - incompatible types: {}", 
                    itemReq.getName(), slot, e.getMessage());
            // This should not happen in a well-designed system, but we log the error and continue
            throw e; // Rethrow to indicate failure
        }
        equipmentReqs.put(slot, existingLogical);
       
    }
    
    /**
     * Integrates an ItemRequirement into an inventory slot.
     * Properly merges conditional requirements with existing OR requirements.
     * 
     * @param itemReq The ItemRequirement to integrate
     * @param slotSpecificReqs Slot-specific requirements map to be updated
     * @param slot The inventory slot (-1 for flexible, 0-27 for specific)
     */
    private void integrateIntoInventorySlot(
            ItemRequirement itemReq,
            Map<Integer, LinkedHashSet<ItemRequirement>> slotSpecificReqs,
            int slot) {
        
        LinkedHashSet<ItemRequirement> existingLogical = slotSpecificReqs.getOrDefault(slot , new LinkedHashSet<>());
        
        if (existingLogical != null) {
                    
            // If we can't merge into existing requirement, try to add directly
            try {
                existingLogical.add(itemReq);
                log.debug("Added conditional ItemRequirement '{}' to existing inventory slot {}", 
                        itemReq.getName(), slot == -1 ? "flexible" : String.valueOf(slot));
            } catch (IllegalArgumentException e) {
                log.error("Cannot integrate conditional ItemRequirement '{}' into inventory slot {} - incompatible types: {}", 
                        itemReq.getName(), slot == -1 ? "flexible" : String.valueOf(slot), e.getMessage());
                // This should not happen in a well-designed system, but we log the error and continue
                throw e; // Rethrow to indicate failure
            }
        }else {
            throw new IllegalArgumentException("No existing logical requirement for inventory slot " +
                    (slot == -1 ? "flexible" : String.valueOf(slot)));
        }
        slotSpecificReqs.put(slot, existingLogical);
    }
        
    /**
     * Plans equipment slots from the new cache structure (LinkedHashSet<ItemRequirement> per slot).
     * Treats multiple ItemRequirements in the same slot as alternatives (OR logic).
     * 
     * @param equipmentReqs Map of equipment slot to set of ItemRequirements (alternatives for that slot)
     * @param alreadyPlanned Set to track already planned items
     * @return true if successful, false if mandatory equipment cannot be fulfilled
     */
    private boolean planEquipmentSlotsFromCache(Map<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> equipmentReqs, 
                                      Set<ItemRequirement> alreadyPlanned) {
        for (Map.Entry<EquipmentInventorySlot, LinkedHashSet<ItemRequirement>> entry : equipmentReqs.entrySet()) {
            EquipmentInventorySlot slot = entry.getKey();
            LinkedHashSet<ItemRequirement> slotItems = entry.getValue();
            
            if (slotItems.isEmpty()) {
                log.debug("No requirements for equipment slot {}", slot);
                continue;
            }
            
            log.debug("Planning equipment slot {} with {} alternatives", slot, slotItems.size());
            
            // Convert to list for compatibility with existing selector logic
            List<ItemRequirement> itemList = new ArrayList<>(slotItems);
            
            // Use enhanced item selection for equipment slots with proper slot and skill validation
            ItemRequirement bestItem = RequirementSelector.findBestAvailableItemForEquipmentSlot(
                itemList, slot, alreadyPlanned);
                
            if (bestItem != null) {
                addEquipmentSlotAssignment(slot, bestItem);
                alreadyPlanned.addAll(slotItems); // Mark as planned to avoid double-processing
                
                log.info("Assigned {} (type: {}) to equipment slot {}", 
                    bestItem.getName(), bestItem.getRequirementType(), slot);
            } else {
                // Check if any requirement was mandatory
                boolean hasMandatory = slotItems.stream().anyMatch(ItemRequirement::isMandatory);
                
                if (hasMandatory) {
                    for (ItemRequirement item : slotItems) {
                        if (item.isMandatory()) {
                            addMissingMandatoryEquipment(slot, item);
                        }
                    }
                    //addMissingMandatoryEquipment(slot);
                    log.warn("Cannot fulfill mandatory equipment requirement for slot {}", slot);
                    log.error("Planning failed: Missing mandatory equipment for slot {}", slot);
                    return false; // Early exit for mandatory equipment failure
                } else {
                    log.debug("Optional equipment not available for slot {}", slot);
                }
            }
        }
        return true; // All mandatory equipment successfully planned
    }
    
    
    /**
     * Plans specific inventory slots from context-filtered logical requirements.
     * With the new cache structure, each slot has exactly one LogicalRequirement for the given context.
     * 
     * @param slotSpecificReqs Slot-specific requirements (one LogicalRequirement per slot)
     * @param alreadyPlanned Set to track already planned items
     */
    private void planSpecificInventorySlots(Map<Integer, LinkedHashSet<ItemRequirement>> slotSpecificReqs, 
                                           Set<ItemRequirement> alreadyPlanned) {
        for (Map.Entry<Integer, LinkedHashSet<ItemRequirement>> entry : slotSpecificReqs.entrySet()) {
            int slot = entry.getKey();
            LinkedHashSet<ItemRequirement> itemSlotReq = entry.getValue();
            
            // Skip the "any slot" entry (-1) as we'll handle it in planFlexibleInventoryItems
            if (slot == -1) {
                continue;
            }
            

            
            ItemRequirement bestItem = RequirementSelector.findBestAvailableItemNotAlreadyPlannedForInventory(
                itemSlotReq, this);
                
            if (bestItem != null) {
                // Enhanced validation for slot assignment
                if (!ItemRequirement.canAssignToSpecificSlot(bestItem, slot)) {
                    log.warn("Cannot assign item {} to slot {} due to constraints. Moving to flexible items.", 
                            bestItem.getName(), slot);
                    throw new IllegalArgumentException(
                        "Item " + bestItem.getName() + " cannot be assigned to specific slot " + slot);
                    // Handle the item as flexible instead
                    //handleItemAsFlexible(bestItem, this, alreadyPlanned);
                } else {
                    // Item can be assigned to specific slot
                    ItemRequirement slotSpecificItem = bestItem.copyWithSpecificSlot(slot);
                    addInventorySlotAssignment(slot, slotSpecificItem);
                    alreadyPlanned.addAll(itemSlotReq); // Mark all alternatives as planned
                    //for (ItemRequirement item : itemSlotReq) {

                        
                    //}
                    log.info("Assigned {} to specific slot {} (amount: {}, stackable: {})", 
                            bestItem.getName(), slot, bestItem.getAmount(), bestItem.isStackable());
                }
            } else {
                // Handle missing mandatory items - convert LinkedHashSet to OrRequirement
                if (!itemSlotReq.isEmpty()) {
                    ItemRequirement firstItem = itemSlotReq.iterator().next();
                    OrRequirement slotOrRequirement = new OrRequirement(
                        firstItem.getPriority(),
                        firstItem.getRating(),
                        "Slot " + slot + " requirement alternatives",
                        firstItem.getTaskContext(),
                        ItemRequirement.class,
                        itemSlotReq.toArray(new ItemRequirement[0])
                    );
                    handleMissingMandatoryItem(Collections.singletonList(slotOrRequirement), this, "inventory slot " + slot);
                }
            }
        }
    }
    
    /**
     * Plans flexible inventory items from the any-slot cache (new cache structure).
     * These items can be placed in any available inventory slot.
     * 
     * @param anySlotReqs Set of OrRequirements for flexible inventory placement  
     * @param alreadyPlanned Set to track already planned items
     */
    private void planFlexibleInventoryItems(LinkedHashSet<OrRequirement> anySlotReqs, 
                                           Set<ItemRequirement> alreadyPlanned) {
        if (anySlotReqs == null || anySlotReqs.isEmpty()) {
            log.debug("No flexible inventory requirements to plan");
            return;
        }
        
        log.debug("Planning {} flexible inventory OrRequirements", anySlotReqs.size());
        
        for (OrRequirement orReq : anySlotReqs) {
            log.debug("Planning flexible OR requirement: {}", orReq.getName());
            
            // Extract ItemRequirements from the OrRequirement
            List<ItemRequirement> orItems = LogicalRequirement.extractItemRequirementsFromLogical(orReq);
            
            if (orItems.isEmpty()) {
                log.warn("OrRequirement has no ItemRequirements: {}", orReq.getName());
                continue;
            }
            
            // Check if this OR requirement has already been satisfied by equipment or specific slots
            int alreadySatisfiedAmount = calculateAlreadySatisfiedAmount(orItems, alreadyPlanned);
            int totalNeeded = orItems.get(0).getAmount(); // All items in OR should have same amount
            
            if (alreadySatisfiedAmount >= totalNeeded) {
                log.debug("OR requirement already fully satisfied by equipment/specific slots: {} satisfied out of {} needed", 
                        alreadySatisfiedAmount, totalNeeded);
                continue; // Skip this OR requirement as it's already satisfied
            }
            
            // Calculate remaining amount needed for inventory
            int remainingAmountNeeded = totalNeeded - alreadySatisfiedAmount;
            log.debug("OR requirement needs additional {} items for inventory (total needed: {}, already satisfied: {})", 
                    remainingAmountNeeded, totalNeeded, alreadySatisfiedAmount);
            // Plan remaining amount needed for inventory (pass remaining amount to avoid double calculation)
            List<ItemRequirement> plannedOrItems = planOrRequirement(orItems, remainingAmountNeeded, alreadyPlanned);
            
            // Check if the OR requirement is fully satisfied after planning
            int totalPlannedInventory = plannedOrItems.stream().mapToInt(ItemRequirement::getAmount).sum();
            int totalPlanned = totalPlannedInventory + alreadySatisfiedAmount;
            
            if (totalPlanned < totalNeeded) {
                if (orReq.getPriority() == RequirementPriority.MANDATORY) {
                    log.warn("Mandatory flexible OR requirement not fully satisfied: {} planned out of {} needed", 
                            totalPlanned, totalNeeded);
                    missingMandatoryItems.addAll(orItems);
                } else {
                    log.debug("Optional flexible OR requirement partially satisfied: {} planned out of {} needed", 
                            totalPlanned, totalNeeded);
                }
            } else {
                log.debug("Flexible OR requirement fully satisfied: {} planned (including {} already satisfied)", 
                        totalPlanned, alreadySatisfiedAmount);
            }                      
        }
        
        log.debug("Finished planning flexible inventory items. Total flexible items: {}", flexibleInventoryItems.size());
    }
    
    
    /**
     * Plans an OR requirement by selecting the best combination of available items to fulfill the specified amount needed.
     * This handles OR requirements according to the configured mode (ANY_COMBINATION or SINGLE_TYPE).
     * 
     * @param orItems All items in the OR requirement group
     * @param amountNeeded Amount still required (after accounting for equipment assignments)
     * @param alreadyPlanned Set of already planned items to avoid conflicts
     * @return List of ItemRequirements that were successfully planned
     */
    private List<ItemRequirement> planOrRequirement(List<ItemRequirement> orItems, int amountNeeded, Set<ItemRequirement> alreadyPlanned) {
        if (orItems.isEmpty()) {
            return new ArrayList<>();
        }
        
        log.debug("Planning OR requirement: {} amount needed from {} alternatives using mode: {}", 
                amountNeeded, orItems.size(), orRequirementMode);
        
        // If no amount needed, requirement is already satisfied
        if (amountNeeded <= 0) {
            log.info("OR requirement already fully satisfied - no additional inventory items needed");
            return new ArrayList<>();
        }
        
        switch (orRequirementMode) {
            case SINGLE_TYPE:
                return planOrRequirementSingleType(orItems, amountNeeded, alreadyPlanned);
            case ANY_COMBINATION:
            default:
                return planOrRequirementAnyCombination(orItems, amountNeeded, alreadyPlanned);
        }
        //log.debug("OR requirement planning completed with allready planned items: {}", alreadyPlanned.size());
    }
    
    /**
     * Calculates how much of an OR requirement has already been satisfied by equipment assignments or already planned items.
     * This prevents double-counting when an item can be equipped but also fulfill inventory requirements.
     * 
     * @param orItems All items in the OR requirement group
     * @param alreadyPlanned Set of already planned items
     * @return The amount already satisfied (0 if none)
     */
    private int calculateAlreadySatisfiedAmount(List<ItemRequirement> orItems, Set<ItemRequirement> alreadyPlanned) {
        int satisfiedAmount = 0;
        
        log.debug("Calculating already satisfied amount for OR group with {} items", orItems.size());
        log.debug("Current equipment assignments: {}", equipmentAssignments.size());
        log.debug("AlreadyPlanned set size: {}", alreadyPlanned.size());
        
        // Check if any item from the OR group has been assigned to equipment
        for (ItemRequirement orItem : orItems) {
            log.debug("Checking OR item: {} (IDs: {})", orItem.getName(), orItem.getIds());
            
            // Check if this specific item is in already planned (marked during equipment assignment)
            if (alreadyPlanned.contains(orItem)) {
                satisfiedAmount += orItem.getAmount();
                log.debug("Found OR item {} already planned with amount {}", orItem.getName(), orItem.getAmount());
                continue;
            }
            
            // Also check if any equipment assignment matches this item by ID
            for (Map.Entry<EquipmentInventorySlot, ItemRequirement> equipEntry : equipmentAssignments.entrySet()) {
                ItemRequirement equippedItem = equipEntry.getValue();
                log.debug("Comparing with equipped item: {} (IDs: {}) in slot {}", 
                        equippedItem.getName(), equippedItem.getIds(), equipEntry.getKey().name());
                
                // Check if the equipped item has the same ID as any of the OR alternatives
                if (orItem.getIds().stream().anyMatch(id -> equippedItem.getIds().contains(id))) {
                    satisfiedAmount += equippedItem.getAmount();
                    log.debug("MATCH FOUND! OR requirement satisfied by equipment: {} in slot {} with amount {}", 
                            equippedItem.getName(), equipEntry.getKey().name(), equippedItem.getAmount());
                    break; // Only count once per OR item
                } else {
                    log.debug("No match: OR item IDs {} vs equipped item IDs {}", orItem.getIds(), equippedItem.getIds());
                }
            }
        }        
        log.info("Total satisfied amount calculated: {}", satisfiedAmount);
        return satisfiedAmount;
    }
    
    /**
     * Checks if an item has already been planned in equipment slots, specific inventory slots, or the alreadyPlanned set.
     * This prevents double-planning the same item in flexible inventory when it's already assigned elsewhere.
     * 
     * @param item The item to check
     * @param alreadyPlanned Set of items already marked as planned
     * @return true if the item is already planned elsewhere, false otherwise
     */
    private boolean isItemAlreadyPlannedElsewhere(ItemRequirement item, Set<ItemRequirement> alreadyPlanned) {
        // Check if it's in the alreadyPlanned set
        if (alreadyPlanned.contains(item)) {
            return true;
        }
        
        // Check if any of the item's IDs match equipment assignments
        for (ItemRequirement equippedItem : equipmentAssignments.values()) {
            if (item.getIds().stream().anyMatch(id -> equippedItem.getIds().contains(id))) {
                return true;
            }
        }
        
        // Check if any of the item's IDs match specific inventory slot assignments
        for (ItemRequirement slotItem : inventorySlotAssignments.values()) {
            if (item.getIds().stream().anyMatch(id -> slotItem.getIds().contains(id))) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Plans an OR requirement using SINGLE_TYPE mode - must fulfill with exactly one item type.
     * 
     * @param orItems All items in the OR requirement group
     * @param amountNeeded Amount still required (after accounting for equipment assignments)
     * @param alreadyPlanned Set of already planned items
     * @return List of planned items (will contain at most one item type)
     */
    private List<ItemRequirement> planOrRequirementSingleType(List<ItemRequirement> orItems, 
                                                              int amountNeeded, 
                                                              Set<ItemRequirement> alreadyPlanned) {
        List<ItemRequirement> plannedItems = new ArrayList<>();
        
        // If no amount needed, requirement is already satisfied
        if (amountNeeded <= 0) {
            return plannedItems;
        }
        
        // Calculate what we have available for each item type
        Map<ItemRequirement, Integer> availableCounts = new HashMap<>();
        for (ItemRequirement item : orItems) {
            if (alreadyPlanned.contains(item)) {
                continue; // Skip already planned items
            }
            
            int inventoryQuantity= Rs2Inventory.itemQuantity(item.getId());
            int bankCount = Rs2Bank.count(item.getUnNotedId());
            int totalAvailable = inventoryQuantity + bankCount;
            
            if (totalAvailable >= amountNeeded) {
                availableCounts.put(item, totalAvailable);
            }
        }
        
        if (availableCounts.isEmpty()) {
            log.warn("SINGLE_TYPE mode: No single item type has enough quantity ({} needed)", amountNeeded);
            return plannedItems;
        }
        
        // Sort available items by preference (priority, then rating, then amount available)
        List<Map.Entry<ItemRequirement, Integer>> sortedAvailable = availableCounts.entrySet().stream()
            .sorted((a, b) -> {
                ItemRequirement itemA = a.getKey();
                ItemRequirement itemB = b.getKey();
                
                // First by priority (MANDATORY > RECOMMENDED > OPTIONAL)
                int priorityCompare = itemA.getPriority().compareTo(itemB.getPriority());
                if (priorityCompare != 0) {
                    return priorityCompare;
                }
                
                // Then by rating (higher is better)
                int ratingCompare = Integer.compare(itemB.getRating(), itemA.getRating());
                if (ratingCompare != 0) {
                    return ratingCompare;
                }
                
                // Finally by available amount (more is better)
                return Integer.compare(b.getValue(), a.getValue());
            })
            .collect(Collectors.toList());
        
        // Select the best single item type that can fulfill the entire requirement
        Map.Entry<ItemRequirement, Integer> bestChoice = sortedAvailable.get(0);
        ItemRequirement chosenItem = bestChoice.getKey();
        
        // Create a copy with the exact amount needed
        ItemRequirement plannedItem = chosenItem.copyWithAmount(amountNeeded);
        handleItemAsFlexible(plannedItem, this, alreadyPlanned);
        plannedItems.add(plannedItem); // Keep for tracking, but avoid duplicate addition later
        
        log.info("SINGLE_TYPE mode: Selected {} x{} (available: {}) for OR requirement", 
                chosenItem.getName(), amountNeeded, bestChoice.getValue());
        
        return plannedItems;
    }
    
    /**
     * Plans an OR requirement using ANY_COMBINATION mode - can fulfill with any combination of items.
     * This is the original behavior from PrePostScheduleRequirements.
     * 
     * @param orItems All items in the OR requirement group
     * @param amountNeeded Amount still required (after accounting for equipment assignments)
     * @param alreadyPlanned Set of already planned items
     * @return List of planned items (can be multiple types)
     */
    private List<ItemRequirement> planOrRequirementAnyCombination(List<ItemRequirement> orItems, 
                                                                  int amountNeeded, 
                                                                  Set<ItemRequirement> alreadyPlanned) {
        List<ItemRequirement> plannedItems = new ArrayList<>();
        
        // If no amount needed, requirement is already satisfied
        if (amountNeeded <= 0) {
            return plannedItems;
        }
        
        // Calculate what we have available for each item type
        Map<ItemRequirement, Integer> availableCounts = new HashMap<>();
        for (ItemRequirement item : orItems) {
            if (alreadyPlanned.contains(item)) {
                continue; // Skip already planned items
            }
            
            int inventoryQuantity = Rs2Inventory.itemQuantity(item.getId());
            int bankCount = Rs2Bank.count(item.getUnNotedId());
            int totalAvailable = inventoryQuantity + bankCount;
            
            if (totalAvailable > 0) {
                availableCounts.put(item, totalAvailable);
            }
        }
        
        // Sort available items by preference (priority, then rating, then amount available)
        List<Map.Entry<ItemRequirement, Integer>> sortedAvailable = availableCounts.entrySet().stream()
            .sorted((a, b) -> {
                ItemRequirement itemA = a.getKey();
                ItemRequirement itemB = b.getKey();
                
                // First by priority (MANDATORY > RECOMMENDED > OPTIONAL)
                int priorityCompare = itemA.getPriority().compareTo(itemB.getPriority());
                if (priorityCompare != 0) {
                    return priorityCompare;
                }
                
                // Then by rating (higher is better)
                int ratingCompare = Integer.compare(itemB.getRating(), itemA.getRating());
                if (ratingCompare != 0) {
                    return ratingCompare;
                }
                
                // Finally by available amount (more is better)
                return Integer.compare(b.getValue(), a.getValue());
            })
            .collect(Collectors.toList());
        log.debug("ANY_COMBINATION mode: Sorted available items by preference ({} total)", sortedAvailable.size());
        log.debug("Sorted available items: {}", sortedAvailable.stream()
            .map(e -> String.format("%s (available: %d)", e.getKey().getName(), e.getValue()))
            .collect(Collectors.joining(", ")));
        // Select items to fulfill the total amount needed (or as much as possible)
        int remainingNeeded = amountNeeded;
        
        for (Map.Entry<ItemRequirement, Integer> entry : sortedAvailable) {
            if (remainingNeeded <= 0) {
                break;
            }
            
            ItemRequirement item = entry.getKey();
            int available = entry.getValue();
            int amountToTake = Math.min(remainingNeeded, available);
            
            // Create a copy of the item with the actual amount we're planning to take
            ItemRequirement plannedItem = item.copyWithAmount(amountToTake);
            
            handleItemAsFlexible(plannedItem, this, alreadyPlanned);
            plannedItems.add(plannedItem); // Keep for tracking, but remove duplicate addition later
            
            remainingNeeded -= amountToTake;
            
            log.debug("ANY_COMBINATION mode: Planned {} x{} for OR requirement (remaining needed: {})\n\t item: \n\t\t{}", 
                item.getName(), amountToTake, remainingNeeded,item);
        }
        
        // Log the result
        if (remainingNeeded > 0) {
            log.warn("ANY_COMBINATION mode: OR requirement partially satisfied - planned {}/{} items from available options", 
                amountNeeded - remainingNeeded, amountNeeded);
            
            // Add a single "collective shortage" item to represent the unmet need
            if (!plannedItems.isEmpty()) {
                // Create a special shortage indicator using the first item as template
                ItemRequirement firstItem = orItems.get(0);
                String shortageDescription = String.format("OR requirement shortage: need %d more from any of %d item types", 
                    remainingNeeded, orItems.size());
                
                //ItemRequirement shortageItem = new ItemRequirement(
                //    -2, // Special shortage indicator ID
                //    remainingNeeded,
                 //   firstItem.getEquipmentSlot(),
                 //   firstItem.getInventorySlot(),
                 //   firstItem.getPriority(),
                 //   firstItem.getRating(),
                 //   shortageDescription,
                 //   firstItem.getTaskContext()
                //);
                
                //addMissingMandatoryInventoryItem(shortageItem);
            }
        } else {
            log.debug("ANY_COMBINATION mode: Successfully planned OR requirement: {} items from {} alternatives", 
                amountNeeded, plannedItems.size());
        }
        
        return plannedItems;
    }
    
    /**
     * Determines whether a missing item should be logged in the comprehensive analysis based on its priority and the flag.
     * 
     * @param item The item requirement to check
     * @return true if the item should be logged, false otherwise
     */
    private boolean shouldLogMissingItem(ItemRequirement item) {
        // Always log mandatory items
        if (item.getPriority() == RequirementPriority.MANDATORY) {
            return true;
        }
        
        // Log recommended/optional items only if the flag is enabled
        return logOptionalMissingItems;
    }
    
    /**
     * Logs a comprehensive analysis of all requirements including quantities, availability, and missing items.
     * This provides a single, detailed summary instead of multiple scattered log messages.
     */
    private String getComprehensiveRequirementAnalysis(boolean verbose) {
        StringBuilder analysis = new StringBuilder();
        analysis.append("\n").append("=".repeat(80));
        analysis.append("\n\tCOMPREHENSIVE REQUIREMENT ANALYSIS - ").append(taskContext);
        analysis.append("\n\tOR Requirement Mode: ").append(orRequirementMode);
        analysis.append("\n").append("=".repeat(80));
        
        // Equipment Analysis
        int plannedEquipment = equipmentAssignments.size();
        int missingEquipment = missingMandatoryEquipment.size();
        analysis.append("\n\n📦 EQUIPMENT ANALYSIS:");
        analysis.append("\n   ✓ Successfully planned: ").append(plannedEquipment).append(" slots");
        if (missingEquipment > 0) {
            analysis.append("\n   ❌ Missing mandatory: ").append(missingEquipment).append(" slots");
            for (Map.Entry<EquipmentInventorySlot, List<ItemRequirement>> entry : missingMandatoryEquipment.entrySet()) {
                EquipmentInventorySlot slot = entry.getKey();
                List<ItemRequirement> items = entry.getValue();
                if (items != null && !items.isEmpty()) {
                    analysis.append("\n      - Slot: ").append(slot.name()).append(" (missing ");                
                    for (ItemRequirement item : items) {
                        if (verbose) {
                            analysis.append("\n         ").append(item.displayString());
                        } else {
                            int available = 0;
                            try {
                                available = Rs2Inventory.itemQuantity(item.getId()) + Rs2Bank.count(item.getUnNotedId());
                            } catch (Exception e) {
                                // ignore, just show 0
                            }
                            analysis.append(item.getName())
                                .append(" [id:").append(item.getId()).append("]")
                                .append(", need: ").append(item.getAmount())
                                .append(", available: ").append(available);
                            if (item.isStackable()) analysis.append(", stackable");
                            if (item.getEquipmentSlot() != null)
                                analysis.append(", slot: ").append(item.getEquipmentSlot().name());
                            if (item.getRequirementType() == RequirementType.EITHER)
                                analysis.append(", flexible");
                            analysis.append("; ");
                        }
                    }
                     analysis.append(")\n");
                }               
            }
        }
        
        // Inventory Analysis with quantities and availability
        int plannedSpecificSlots = inventorySlotAssignments.size();
        int plannedFlexibleItems = flexibleInventoryItems.size();
        int missingMandatoryItems = this.missingMandatoryItems.size();
        int totalPlannedInventory = plannedSpecificSlots + plannedFlexibleItems;
        
        analysis.append("\n\n🎒 INVENTORY ANALYSIS:");
        analysis.append("\n   ✓ Successfully planned: ").append(totalPlannedInventory).append(" items");
        analysis.append("\n      - Specific slots: ").append(plannedSpecificSlots);
        analysis.append("\n      - Flexible items: ").append(plannedFlexibleItems);
        
        // DEBUG: Show what flexible items are planned
        if (!flexibleInventoryItems.isEmpty()) {
            analysis.append("\n   📋 DEBUG - Flexible items planned:");
            for (ItemRequirement flexItem : flexibleInventoryItems) {
                analysis.append("\n      - ").append(flexItem.getName()).append(" (IDs: ").append(flexItem.getIds()).append(", Priority: ").append(flexItem.getPriority()).append(")");
            }
        }
        
        if (missingMandatoryItems > 0) {
            analysis.append("\n   ❌ Missing mandatory items: ").append(missingMandatoryItems);
            
            // Group missing items by their logical requirement to handle OR requirements properly
            Map<String, List<ItemRequirement>> groupedMissingItems = new HashMap<>();
            
            for (ItemRequirement missingItem : this.missingMandatoryItems) {
                // Try to find the logical requirement this item belongs to
                String groupKey = findLogicalRequirementGroupKey(missingItem);
                groupedMissingItems.computeIfAbsent(groupKey, k -> new ArrayList<>()).add(missingItem);
            }
            
            // Analyze each group separately
            for (Map.Entry<String, List<ItemRequirement>> group : groupedMissingItems.entrySet()) {
                String groupKey = group.getKey();
                List<ItemRequirement> groupItems = group.getValue();
                
                if (groupKey.startsWith("OR:")) {
                    // Handle OR requirement group - analyze as collective need
                    // Check if we should log this based on priority and flag
                    if (shouldLogMissingItem(groupItems.get(0))) {
                        analyzeOrRequirementGroup(analysis, groupKey, groupItems);
                    }
                } else {
                    // Handle individual requirements
                    for (ItemRequirement missingItem : groupItems) {
                        if (shouldLogMissingItem(missingItem)) {
                            analyzeIndividualRequirement(analysis, missingItem);
                        }
                    }
                }
            }
        }
        
        analysis.append("\n\n📊 SUMMARY:");
        analysis.append("\n   Plan Feasible: ").append(isFeasible() ? "✅ YES" : "❌ NO");
        analysis.append("\n   Fits in Inventory: ").append(fitsInInventory() ? "✅ YES" : "❌ NO");
        analysis.append("\n   Total Slots Needed: ").append(getTotalInventorySlotsNeeded()).append("/28");
        analysis.append("\n").append("=".repeat(80));
        
        return analysis.toString();
    }
    
    /**
     * Finds the logical requirement group key for a given item requirement.
     * This helps identify if an item belongs to an OR requirement group.
     * 
     * @param item The item requirement to analyze
     * @return A group key string for grouping related requirements
     */
    private String findLogicalRequirementGroupKey(ItemRequirement item) {
        if (registry == null) {
            return "INDIVIDUAL:" + item.getName();
        }
        
        // Search through current context logical requirements to find which OR group this item belongs to
        Map<Integer, OrRequirement> slotSpecificReqs = registry.getInventorySlotLogicalRequirements(taskContext);
        
        for (Map.Entry<Integer, OrRequirement> entry : slotSpecificReqs.entrySet()) {
            OrRequirement logicalReq = entry.getValue();
            
            if (logicalReq instanceof OrRequirement) {
                List<ItemRequirement> orItems = OrRequirement.extractItemRequirementsFromLogical(logicalReq);
                
                // Check if this item belongs to this OR requirement
                for (ItemRequirement orItem : orItems) {
                    if (orItem.getIds().equals(item.getIds()) && 
                        orItem.getAmount() == item.getAmount() &&
                        Objects.equals(orItem.getEquipmentSlot(), item.getEquipmentSlot()) &&
                        Objects.equals(orItem.getInventorySlot(), item.getInventorySlot())) {
                        return "OR:" + logicalReq.getDescription();
                    }
                }
            }
        }
        
        return "INDIVIDUAL:" + item.getName();
    }
    
    /**
     * Analyzes an OR requirement group to calculate total available vs. total required items.
     * This provides a more accurate analysis for OR requirements like "5 food items from any combination".
     * Now includes detailed skill requirement checking and usability analysis.
     * 
     * @param analysis The analysis string builder
     * @param groupKey The group key identifying the OR requirement
     * @param groupItems All items in the OR requirement group
     */
    private void analyzeOrRequirementGroup(StringBuilder analysis, String groupKey, List<ItemRequirement> groupItems) {
        String orDescription = groupKey.substring(3); // Remove "OR:" prefix
        
        analysis.append("\n      🍎 OR Requirement Group: ").append(orDescription);
        analysis.append("\n         Mode: ").append(orRequirementMode);
        
        // Calculate total required amount (should be same for all items in OR requirement)
        int totalRequired = groupItems.get(0).getAmount();
        analysis.append("\n         Total Required: ").append(totalRequired);
        
        if (orRequirementMode == OrRequirementMode.SINGLE_TYPE) {
            analysis.append(" (exactly ").append(totalRequired).append(" of ONE type)");
        } else {
            analysis.append(" (any combination)");
        }
        
        // Calculate total available and usable items across all types in the OR requirement
        int totalInventoryQuantity = 0;
        int totalBankCount = 0;
        int totalUsableCount = 0;
        Map<String, ItemAvailabilityInfo> itemAnalysis = new HashMap<>();
        
        for (ItemRequirement item : groupItems) {
            int inventoryQuantity = 0;
            int inventoryCount = 0;
            int bankCount = 0;
            
            try {
                // Safely get inventory and bank counts with error handling
                inventoryQuantity = Rs2Inventory.itemQuantity(item.getId());
                bankCount = Rs2Bank.count(item.getUnNotedId());
                inventoryCount = Rs2Inventory.count(item.getId());
            } catch (ArrayIndexOutOfBoundsException e) {
                log.warn("ArrayIndexOutOfBoundsException when counting item '{}' (ID: {}): {}", 
                         item.getName(), item.getId(), e.getMessage());
                // Continue with 0 counts to avoid crashing
                inventoryQuantity = 0;
                inventoryCount = 0;
                bankCount = 0;
            } catch (Exception e) {
                log.warn("Unexpected error when counting item '{}' (ID: {}): {}", 
                         item.getName(), item.getId(), e.getMessage());
                inventoryQuantity = 0;
                inventoryCount = 0;
                bankCount = 0;
            }
            
            int totalCount = inventoryQuantity + bankCount;
            
            totalInventoryQuantity += inventoryQuantity;
            totalBankCount += bankCount;
            
            if (totalCount > 0) {
                // Analyze usability based on skill requirements and requirement type
                boolean canUse = checkItemUsability(item, inventoryQuantity, bankCount);
                boolean meetsSkillReqs = item.meetsSkillRequirements();
                
                ItemAvailabilityInfo info = new ItemAvailabilityInfo(
                    inventoryQuantity,inventoryCount, bankCount, canUse, meetsSkillReqs, item.getRequirementType(),
                    item.getSkillToUse(), item.getMinimumLevelToUse(),
                    item.getSkillToEquip(), item.getMinimumLevelToEquip()
                );
                
                itemAnalysis.put(item.getName(), info);
                
                if (canUse) {
                    totalUsableCount += totalCount;
                }
            }
        }
        
        int totalAvailable = totalInventoryQuantity + totalBankCount;
        analysis.append("\n         Total Available: ").append(totalAvailable).append(" (Inventory: ").append(totalInventoryQuantity).append(", Bank: ").append(totalBankCount).append(")");
        analysis.append("\n         Total Usable: ").append(totalUsableCount).append(" (considering skill requirements)");
        
        // Show detailed breakdown of available items with usability analysis
        if (!itemAnalysis.isEmpty()) {
            analysis.append("\n         Detailed Item Analysis:");
            for (Map.Entry<String, ItemAvailabilityInfo> entry : itemAnalysis.entrySet()) {
                String itemName = entry.getKey();
                ItemAvailabilityInfo info = entry.getValue();
                
                analysis.append("\n           🔍 ").append(itemName).append(": ")
                       .append(info.inventoryQuantity + info.bankCount).append(" total")
                       .append(" (Inv: ").append(info.inventoryQuantity).append("("+info.inventoryCount+")").append(", Bank: ").append(info.bankCount).append(")");
                
                if (info.canUse) {
                    analysis.append(" ✅ USABLE");
                } else {
                    analysis.append(" ❌ NOT USABLE");
                    
                    if (!info.meetsSkillRequirements) {
                        analysis.append(" - Skill requirements not met:");
                        
                        if (info.skillToUse != null && info.minimumLevelToUse != null) {
                            int currentLevel = Rs2Player.getRealSkillLevel(info.skillToUse);
                            if (currentLevel < info.minimumLevelToUse) {
                                analysis.append(" Need ").append(info.skillToUse.getName())
                                       .append(" ").append(info.minimumLevelToUse)
                                       .append(" (current: ").append(currentLevel).append(")");
                            }
                        }
                        
                        if (info.skillToEquip != null && info.minimumLevelToEquip != null) {
                            int currentLevel = Rs2Player.getRealSkillLevel(info.skillToEquip);
                            if (currentLevel < info.minimumLevelToEquip) {
                                analysis.append(" Need ").append(info.skillToEquip.getName())
                                       .append(" ").append(info.minimumLevelToEquip)
                                       .append(" to equip (current: ").append(currentLevel).append(")");
                            }
                        }
                    }
                    
                    // Add requirement type context
                    if (info.requirementType == RequirementType.EQUIPMENT && info.inventoryCount > 0 && info.bankCount == 0) {
                        analysis.append(" - Item in inventory but requirement needs it equipped");
                    } else if (info.requirementType == RequirementType.INVENTORY && info.inventoryCount == 0 && info.bankCount > 0) {
                        analysis.append(" - Item in bank but requirement needs it in inventory");
                    }
                }
            }
        }
        
        // Calculate shortage properly for OR requirements based on mode and usability
        boolean isSufficient = false;
        if (orRequirementMode == OrRequirementMode.SINGLE_TYPE) {
            // Check if any single item type has enough usable items
            boolean anySingleTypeHasEnoughUsable = itemAnalysis.values().stream()
                .anyMatch(info -> info.canUse && (info.inventoryCount + info.bankCount) >= totalRequired);
            
            if (anySingleTypeHasEnoughUsable) {
                analysis.append("\n         Status: ✅ SUFFICIENT (at least one usable type has ").append(totalRequired).append("+ items)");
                isSufficient = true;
            } else {
                int maxUsableSingleType = itemAnalysis.values().stream()
                    .filter(info -> info.canUse)
                    .mapToInt(info -> info.inventoryCount + info.bankCount)
                    .max()
                    .orElse(0);
                int shortage = totalRequired - maxUsableSingleType;
                analysis.append("\n         Status: ❌ INSUFFICIENT (need ").append(shortage).append(" more usable items of any single type)");
            }
        } else {
            // ANY_COMBINATION mode - consider only usable items
            if (totalUsableCount >= totalRequired) {
                analysis.append("\n         Status: ✅ SUFFICIENT (have ").append(totalUsableCount).append(" usable, need ").append(totalRequired).append(")");
                isSufficient = true;
            } else {
                int shortage = totalRequired - totalUsableCount;
                analysis.append("\n         Status: ❌ INSUFFICIENT (need ").append(shortage).append(" more usable items of any type)");
            }
        }
        
        analysis.append("\n         Priority: ").append(groupItems.get(0).getPriority());
        
        if (orRequirementMode == OrRequirementMode.SINGLE_TYPE) {
            analysis.append("\n         Note: Must have exactly ").append(totalRequired).append(" of ONE usable item type");
        } else {
            analysis.append("\n         Note: Any combination of usable items can fulfill this requirement");
        }
        
        // Add debugging hint if insufficient
        if (!isSufficient) {
            analysis.append("\n         💡 Debug Hint: Check if items exist but skill requirements aren't met, or items are in wrong location (bank vs inventory)");
        }
    }
    
    /**
     * Checks if an item is usable based on its requirement type and skill requirements.
     * 
     * @param item The item requirement to check
     * @param inventoryCount Number of items in inventory
     * @param bankCount Number of items in bank
     * @return true if the item can be used to fulfill the requirement
     */
    private boolean checkItemUsability(ItemRequirement item, int inventoryCount, int bankCount) {
        // First check if we have the item available
        if (inventoryCount + bankCount == 0) {
            return false;
        }
        
        // Check skill requirements
        if (!item.meetsSkillRequirements()) {
            return false;
        }
        
        // Check requirement type constraints
        RequirementType reqType = item.getRequirementType();
        switch (reqType) {
            case INVENTORY:
                // Must be available (can withdraw from bank if needed)
                return true;
            case EQUIPMENT:
                // Must be available and equippable (can equip from inventory or bank)
                return true;
            case EITHER:
                // Can be in inventory or equipped (most flexible)
                return true;
            default:
                return true;
        }
    }
    
    /**
     * Helper class to store item availability and usability information.
     */
    private static class ItemAvailabilityInfo {
        final int inventoryCount;
        final int inventoryQuantity;
        final int bankCount;
        final boolean canUse;
        final boolean meetsSkillRequirements;
        final RequirementType requirementType;
        final Skill skillToUse;
        final Integer minimumLevelToUse;
        final Skill skillToEquip;
        final Integer minimumLevelToEquip;
        
        ItemAvailabilityInfo(int inventoryCount,  int inventoryQuantity, int bankCount, boolean canUse, boolean meetsSkillRequirements,
                           RequirementType requirementType, Skill skillToUse, Integer minimumLevelToUse,
                           Skill skillToEquip, Integer minimumLevelToEquip) {
            this.inventoryCount = inventoryCount;
            this.inventoryQuantity = inventoryQuantity;
            this.bankCount = bankCount;
            this.canUse = canUse;
            this.meetsSkillRequirements = meetsSkillRequirements;
            this.requirementType = requirementType;
            this.skillToUse = skillToUse;
            this.minimumLevelToUse = minimumLevelToUse;
            this.skillToEquip = skillToEquip;
            this.minimumLevelToEquip = minimumLevelToEquip;
        }
    }
    
    /**
     * Analyzes an individual item requirement (not part of an OR group).
     * Now includes detailed skill requirement checking and usability analysis.
     * 
     * @param analysis The analysis string builder
     * @param missingItem The individual item requirement to analyze
     */
    private void analyzeIndividualRequirement(StringBuilder analysis, ItemRequirement missingItem) {
        analysis.append("\n      📋 Item: ").append(missingItem.getName());
        analysis.append("\n         Required: ").append(missingItem.getAmount());
        
        // Check current inventory and bank for availability with error handling
        int getInventoryQuantity = 0;
        int bankCount = 0;
        
        try {
            // Safely get inventory and bank counts with error handling
            getInventoryQuantity = Rs2Inventory.itemQuantity(missingItem.getId());
            bankCount = Rs2Bank.count(missingItem.getUnNotedId());
        } catch (ArrayIndexOutOfBoundsException e) {
            log.warn("ArrayIndexOutOfBoundsException when counting individual item '{}' (ID: {}): {}", 
                     missingItem.getName(), missingItem.getId(), e.getMessage());
            // Continue with 0 counts to avoid crashing
            analysis.append("\n         ⚠️ Error accessing item data - using 0 counts");
        } catch (Exception e) {
            log.warn("Unexpected error when counting individual item '{}' (ID: {}): {}", 
                     missingItem.getName(), missingItem.getId(), e.getMessage());
            analysis.append("\n         ⚠️ Error accessing item data - using 0 counts");
        }
        
        int totalAvailable = getInventoryQuantity + bankCount;
        
        analysis.append("\n         Available: ").append(totalAvailable).append(" (Inventory: ").append(getInventoryQuantity).append(", Bank: ").append(bankCount).append(")");
        
        // Analyze usability based on skill requirements and requirement type
        boolean canUse = checkItemUsability(missingItem, getInventoryQuantity, bankCount);
        boolean meetsSkillReqs = missingItem.meetsSkillRequirements();
        boolean hasEnoughQuantity = totalAvailable >= missingItem.getAmount();
        
        // Enhanced status analysis considering both quantity and usability
        if (hasEnoughQuantity && canUse) {
            analysis.append("\n         Status: ✅ SUFFICIENT AND USABLE");
            analysis.append("\n         💡 Debug Hint: Item is available and usable but wasn't selected - check planning logic");
        } else if (hasEnoughQuantity && !canUse) {
            analysis.append("\n         Status: ⚠️ AVAILABLE BUT NOT USABLE");
            
            if (!meetsSkillReqs) {
                analysis.append("\n         🚫 Skill Requirements NOT Met:");
                
                // Check skill to use requirements
                if (missingItem.getSkillToUse() != null && missingItem.getMinimumLevelToUse() != null) {
                    int currentLevel = Rs2Player.getRealSkillLevel(missingItem.getSkillToUse());
                    if (currentLevel < missingItem.getMinimumLevelToUse()) {
                        analysis.append("\n            - Need ").append(missingItem.getSkillToUse().getName())
                               .append(" ").append(missingItem.getMinimumLevelToUse())
                               .append(" to use (current: ").append(currentLevel).append(")");
                    }
                }
                
                // Check skill to equip requirements
                if (missingItem.getSkillToEquip() != null && missingItem.getMinimumLevelToEquip() != null) {
                    int currentLevel = Rs2Player.getRealSkillLevel(missingItem.getSkillToEquip());
                    if (currentLevel < missingItem.getMinimumLevelToEquip()) {
                        analysis.append("\n            - Need ").append(missingItem.getSkillToEquip().getName())
                               .append(" ").append(missingItem.getMinimumLevelToEquip())
                               .append(" to equip (current: ").append(currentLevel).append(")");
                    }
                }
            }
            
            // Add requirement type context
            RequirementType reqType = missingItem.getRequirementType();
            if (reqType == RequirementType.EQUIPMENT && getInventoryQuantity > 0 && bankCount == 0) {
                analysis.append("\n         📍 Location Issue: Item in inventory but requirement needs it equipped");
            } else if (reqType == RequirementType.INVENTORY && getInventoryQuantity == 0 && bankCount > 0) {
                analysis.append("\n         📍 Location Issue: Item in bank but requirement needs it in inventory");
            }
        } else {
            int shortage = missingItem.getAmount() - totalAvailable;
            analysis.append("\n         Status: ❌ INSUFFICIENT (need ").append(shortage).append(" more)");
        }
        
        // Add detailed item properties for debugging
        analysis.append("\n         Properties:");
        analysis.append("\n            - Name: ").append(missingItem.getName());
        analysis.append("\n            - ID: ").append(missingItem.getId());
        analysis.append("\n            - noted ID: ").append(missingItem.getNotedId());
        analysis.append("\n            - is noted item: ").append(missingItem.getNotedId() == missingItem.getId() );
        analysis.append("\n            - Amount: ").append(missingItem.getAmount());        
        analysis.append("\n            - Stackable: ").append(missingItem.isStackable() ? "Yes" : "No");        
        analysis.append("\n            - Priority: ").append(missingItem.getPriority());
        analysis.append("\n            - Requirement Type: ").append(missingItem.getRequirementType());
        
        if (missingItem.getEquipmentSlot() != null) {
            analysis.append("\n            - Equipment Slot: ").append(missingItem.getEquipmentSlot());
        }
        
        if (missingItem.getInventorySlot() >= 0) {
            analysis.append("\n            - Specific Slot: ").append(missingItem.getInventorySlot());
        }
        
        // Add skill requirements summary if present
        if (missingItem.getSkillToUse() != null || missingItem.getSkillToEquip() != null) {
            analysis.append("\n         Skill Requirements:");
            
            if (missingItem.getSkillToUse() != null && missingItem.getMinimumLevelToUse() != null) {
                int currentUseLevel = Rs2Player.getRealSkillLevel(missingItem.getSkillToUse());
                String useStatus = currentUseLevel >= missingItem.getMinimumLevelToUse() ? "✅" : "❌";
                analysis.append("\n            - Use: ").append(useStatus)
                       .append(" ").append(missingItem.getSkillToUse().getName())
                       .append(" ").append(missingItem.getMinimumLevelToUse())
                       .append(" (current: ").append(currentUseLevel).append(")");
            }
            
            if (missingItem.getSkillToEquip() != null && missingItem.getMinimumLevelToEquip() != null) {
                int currentEquipLevel = Rs2Player.getRealSkillLevel(missingItem.getSkillToEquip());
                String equipStatus = currentEquipLevel >= missingItem.getMinimumLevelToEquip() ? "✅" : "❌";
                analysis.append("\n            - Equip: ").append(equipStatus)
                       .append(" ").append(missingItem.getSkillToEquip().getName())
                       .append(" ").append(missingItem.getMinimumLevelToEquip())
                       .append(" (current: ").append(currentEquipLevel).append(")");
            }
        }
    }
    
    // ========== PLAN EXECUTION METHODS ==========
    
    /**
     * Banks all equipped and inventory items that are not part of this planned loadout.
     * This ensures a clean slate before executing the optimal layout plan.
     * 
     * @return true if banking was successful, false otherwise
     */
    public boolean bankItemsNotInPlan(CompletableFuture<Boolean> scheduledFuture) {
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
                if (scheduledFuture!=null && scheduledFuture.isCancelled()) {
                    log.info("Banking task cancelled, stopping equipment cleanup.");
                    return false;
                }
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
                        log.info("Keeping equipped item in slot {}: matches planned loadout", equipmentSlot.name());
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
                        log.info("Keeping inventory item in slot {}: {} (matches planned position)", 
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
                    log.info("Item {} in slot {} matches specific slot assignment", currentItem.getName(), currentSlot);
                    return true;
                } else {
                    log.info("Item {} in slot {} matches ID but insufficient quantity: {} < {}", 
                            currentItem.getName(), currentSlot, itemQuantity, specificSlotItem.getAmount());
                    return false;
                }
            } else {
                log.info("Item {} in slot {} doesn't match specific slot assignment", currentItem.getName(), currentSlot);
                return false;
            }
        }
        
        // Check if this item is among the flexible items
        for (ItemRequirement flexibleItem : flexibleInventoryItems) {
            if (flexibleItem.getId() == itemId) {
                log.info("Item {} in slot {} is a planned flexible item", currentItem.getName(), currentSlot);
                return true; // Flexible items can be anywhere
            }
        }
        
        // Not found in any planned positions
        log.info("Item {} in slot {} is not part of the planned loadout", currentItem.getName(), currentSlot);
        return false;
    }
    
    /**
     * Executes this inventory and equipment layout plan.
     * Withdraws and equips items according to the optimal layout.
     * 
     * @return true if execution was successful
     */
    public boolean executePlan(CompletableFuture<Boolean> scheduledFuture) {
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
                    if (scheduledFuture != null && scheduledFuture.isCancelled()) {
                        log.info("Plan execution cancelled, stopping equipment assignments.");
                        return false;
                    }
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
                    if (scheduledFuture != null && scheduledFuture.isCancelled()) {
                        log.info("Plan execution cancelled, stopping inventory slot assignments.");
                        return false;
                    }
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
                    if (scheduledFuture != null && scheduledFuture.isCancelled()) {
                        log.info("Plan execution cancelled, stopping flexible inventory items.");
                        return false;
                    }
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
        log.info("Handling equipment assignment: {} -> {}", slot.name(), item.getName());
        
        // Check if already equipped correctly (considering fuzzy matching for variations)
        Rs2ItemModel currentEquipped = Rs2Equipment.get(slot);
        if (currentEquipped != null) {
            if (item.isFuzzy()) {
                // For fuzzy items, check if any variation is equipped
                Collection<Integer> variations = InventorySetupsVariationMapping.getVariations(item.getId());
                if (variations.contains(currentEquipped.getId())) {
                    log.info("Item {} (or variation) already equipped in slot {}", item.getName(), slot.name());
                    return true;
                }
            } else {
                // Exact ID match
                if (item.getId() == currentEquipped.getId()) {
                    log.info("Item {} already equipped in slot {}", item.getName(), slot.name());
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
                log.info("Withdrew {} for equipment slot {}", item.getName(), slot.name());
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
        log.info("Handling inventory slot assignment: slot {} -> {}", slot, item.getName());
        
        // Use the static utility method from ItemRequirement
        return ItemRequirement.withdrawAndPlaceInSpecificSlot(slot, item);
    }
    
    /**
     * Handles a single flexible inventory item.
     */
    private boolean handleFlexibleInventoryItem(ItemRequirement item) {
        log.info("Handling flexible inventory item: {}", item.getName());
        
        // Check if item is already in inventory with sufficient amount (handles fuzzy matching)
        if (item.isAvailableInInventory()) {
            log.info("Flexible item {} already in inventory with sufficient amount", item.getName());
            return true;
        }
        
        // Try to withdraw the item if available in bank
        if (item.isAvailableInBank()) {
            int itemId = item.getId();
            if (Rs2Bank.withdrawX(itemId, item.getAmount())) {
                log.info("Withdrew flexible item: {} x{}", item.getName(), item.getAmount());
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
            log.info("Plan is not feasible - has missing mandatory items:");
            plan.getMissingMandatoryItems().forEach(item -> 
                log.info("  - Missing: {}", item.getName()));
            plan.getMissingMandatoryEquipment().forEach((slot,items) -> 
                log.info("  - Missing equipment slot: {}", slot+ " with items: {}", 
                         items.stream().map(ItemRequirement::getName).collect(Collectors.joining(", "))));
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
            log.info("Plan summary:\n{}", plan.getSummary());
        }
        return true; // Plan is valid and feasible
    }
    
    /**
     * Silent version of optimizeAndValidatePlan that suppresses logging.
     * Used when detailed analysis is already logged elsewhere.
     * 
     * @param plan The inventory setup plan to optimize and validate
     * @return true if the plan is valid and feasible, false otherwise
     */
    public static boolean optimizeAndValidatePlanSilent(InventorySetupPlanner plan) {
        // Step 1: Optimize flexible item placement
        plan.optimizeFlexibleItemPlacement();
        
        // Step 2: Validate plan feasibility (silent)
        if (!plan.isFeasible()) {
            return false; // Early exit if plan is not feasible
        }
        
        // Step 3: Comprehensive mandatory requirements validation (silent)
        boolean allMandatorySatisfied = validateAllMandatoryRequirementsSatisfied(plan);
        if (!allMandatorySatisfied) {
            return false; // Early exit if mandatory requirements are not satisfied
        }
        
        // Step 4: Validate inventory capacity (silent)
        if (!plan.fitsInInventory()) {
            return false; // Early exit if plan exceeds inventory capacity
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
                    unsatisfiedRequirements.add("(flexible inventory): "+ item.displayString() );
                    allSatisfied = false;
                }
            }
        }
        
        if (!allSatisfied) {
            StringBuilder unsatisfiedRequirementsBuilder = new StringBuilder();
            unsatisfiedRequirementsBuilder.append(String.format("\nFinal validation failed. {} mandatory requirements not satisfied:", unsatisfiedRequirements.size()));
            unsatisfiedRequirements.forEach(req -> unsatisfiedRequirementsBuilder.append(String.format("\n - {}", req)));
            log.error(unsatisfiedRequirementsBuilder.toString());
        } else {
            log.info("Final validation passed - all mandatory requirements in plan are satisfied");
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
        
        log.info("Added {} as flexible inventory item (amount: {}, stackable: {})", 
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
                log.info("Cannot fulfill mandatory item requirement for {}: {}", location, mandatoryItem.getName());
            }
        }
    }
    
    /**
     * Converts this plan to an InventorySetup that can be used with Rs2InventorySetup.
     * This allows reusing the existing inventory setup loading functionality.
     * 
     * @param setupName The name for the generated setup
     * @return InventorySetup generated from this plan, or null if conversion failed
     */
    public InventorySetup convertToInventorySetup(String setupName) {
        return convertToInventorySetup(setupName, java.awt.Color.RED, true, null, true, false, 0, false, -1);
    }
    
    /**
     * Converts this plan to an InventorySetup with customizable configuration.
     * This allows reusing the existing inventory setup loading functionality.
     * 
     * @param setupName The name for the generated setup
     * @param highlightColor The highlight color for differences
     * @param highlightDifference Whether to highlight differences
     * @param displayColor The display color (can be null)
     * @param filterBank Whether to filter bank
     * @param unorderedHighlight Whether to highlight unordered differences
     * @param spellbook The spellbook setting
     * @param favorite Whether the setup is marked as favorite
     * @param iconID The icon ID for the setup
     * @return InventorySetup generated from this plan, or null if conversion failed
     */
    public InventorySetup convertToInventorySetup(String setupName, java.awt.Color highlightColor, 
            boolean highlightDifference, java.awt.Color displayColor, boolean filterBank, 
            boolean unorderedHighlight, int spellbook, boolean favorite, int iconID) {
        try {
            log.debug("Converting InventorySetupPlanner to InventorySetup: {}", setupName);
            
            // Create inventory items list
            List<InventorySetupsItem> inventoryItems = 
                createInventoryItemsList();
            
            // Create equipment items list
            List<InventorySetupsItem> equipmentItems = 
                createEquipmentItemsList();
            
            // Create empty containers for special items (rune pouch, bolt pouch, quiver)
            List<InventorySetupsItem> runePouchItems = 
                createRunePouchItemsList();
            
            List<InventorySetupsItem> boltPouchItems = 
                createBoltPouchItemsList();
            
            List<InventorySetupsItem> quiverItems = 
                createQuiverItemsList();
            
            // Create the inventory setup using the same pattern as addInventorySetup
            return createInventorySetupFromLists(
                setupName,
                inventoryItems,
                equipmentItems, 
                runePouchItems,
                boltPouchItems,
                quiverItems,
                highlightColor,
                highlightDifference,
                displayColor,
                filterBank,
                unorderedHighlight,
                spellbook,
                favorite,
                iconID
            );
            
        } catch (Exception e) {
            log.error("Failed to convert InventorySetupPlanner to InventorySetup: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Creates the inventory items list from the plan.
     * Fills all 28 slots, using dummy items for empty slots.
     */
    private List<InventorySetupsItem> createInventoryItemsList() {
        List<InventorySetupsItem> inventoryItems = new ArrayList<>();
        
        // Initialize all 28 slots with dummy items
        for (int i = 0; i < 28; i++) {
            inventoryItems.add(InventorySetupsItem.getDummyItem());
        }
        
        // Fill specific slot assignments
        for (Map.Entry<Integer, ItemRequirement> entry : inventorySlotAssignments.entrySet()) {
            int slot = entry.getKey();
            ItemRequirement item = entry.getValue();
            
            if (slot >= 0 && slot < 28) {
                inventoryItems.set(slot, createInventorySetupsItem(item, slot));
                log.debug("\n\t Added specific slot assignment: {} -> slot {}", item.getName(), slot);
            }
        }
        
        // Fill flexible items in available slots
        int currentSlot = 0;
        for (ItemRequirement item : flexibleInventoryItems) {
            // Find next available slot
            while (currentSlot < 28 && !InventorySetupsItem.itemIsDummy(inventoryItems.get(currentSlot))) {
                currentSlot++;
            }
            
            if (currentSlot < 28) {
                inventoryItems.set(currentSlot, createInventorySetupsItem(item, currentSlot));
                log.debug("\n\t -Added flexible item: {} -> slot {}", item.getName(), currentSlot);
                currentSlot++;
            } else {
                log.warn("No available inventory slot for flexible item: {}", item.getName());
            }
        }
        
        return inventoryItems;
    }
    
    /**
     * Creates the equipment items list from the plan.
     * Fills all 14 equipment slots, using dummy items for empty slots.
     */
    private List<InventorySetupsItem> createEquipmentItemsList() {
        List<InventorySetupsItem> equipmentItems = new ArrayList<>();
        
        // Initialize all 14 equipment slots with dummy items
        for (int i = 0; i < 14; i++) {
            equipmentItems.add(InventorySetupsItem.getDummyItem());
        }
        
        // Fill equipment assignments
        for (Map.Entry<EquipmentInventorySlot, ItemRequirement> entry : equipmentAssignments.entrySet()) {
            EquipmentInventorySlot slot = entry.getKey();
            ItemRequirement item = entry.getValue();
            
            int slotIndex = slot.getSlotIdx();
            if (slotIndex >= 0 && slotIndex < 14) {
                equipmentItems.set(slotIndex, createInventorySetupsItem(item, slotIndex));
                log.debug("Added equipment assignment: {} -> slot {}", item.getName(), slot.name());
            }
        }
        
        return equipmentItems;
    }
    
    /**
     * Creates the rune pouch items list from the plan.
     * Detects RunePouchRequirement objects and converts their required runes to InventorySetupsItem objects.
     */
    private List<InventorySetupsItem> createRunePouchItemsList() {
        List<InventorySetupsItem> runePouchItems = new ArrayList<>();
        
        // Search for RunePouchRequirement in both inventory slot assignments and flexible items
        List<RunePouchRequirement> runePouchRequirements = new ArrayList<>();
        
        // Check inventory slot assignments for RunePouchRequirement instances
        for (ItemRequirement item : inventorySlotAssignments.values()) {
            if (item instanceof RunePouchRequirement) {
                runePouchRequirements.add((RunePouchRequirement) item);
            }
        }
        
        // Check flexible inventory items for RunePouchRequirement instances
        for (ItemRequirement item : flexibleInventoryItems) {
            if (item instanceof RunePouchRequirement) {
                runePouchRequirements.add((RunePouchRequirement) item);
            }
        }
        
        // Convert rune requirements to InventorySetupsItem objects
        if (!runePouchRequirements.isEmpty()) {
            log.debug("Found {} RunePouchRequirement(s) in plan", runePouchRequirements.size());
            
            // Collect all required runes from all RunePouchRequirements
            Map<net.runelite.client.plugins.microbot.util.magic.Runes, Integer> allRequiredRunes = new HashMap<>();
            
            for (RunePouchRequirement runePouchReq : runePouchRequirements) {
                // Merge rune requirements (taking the maximum quantity for each rune type)
                for (Map.Entry<net.runelite.client.plugins.microbot.util.magic.Runes, Integer> entry : runePouchReq.getRequiredRunes().entrySet()) {
                    net.runelite.client.plugins.microbot.util.magic.Runes rune = entry.getKey();
                    int requiredAmount = entry.getValue();
                    allRequiredRunes.merge(rune, requiredAmount, Integer::max);
                }
            }
            
            // Convert runes to InventorySetupsItem objects
            // Rune pouch has 4 slots (0-3), but we'll use all available slots
            int slotIndex = 0;
            for (Map.Entry<net.runelite.client.plugins.microbot.util.magic.Runes, Integer> entry : allRequiredRunes.entrySet()) {
                if (slotIndex >= 4) {
                    log.warn("Rune pouch can only hold 4 types of runes, skipping extra runes");
                    break;
                }
                
                net.runelite.client.plugins.microbot.util.magic.Runes rune = entry.getKey();
                int quantity = entry.getValue();
                
                // Create InventorySetupsItem for this rune
                InventorySetupsItem runeItem = new InventorySetupsItem(
                    rune.getItemId(),          // itemID
                    rune.name() + " Rune",     // name
                    quantity,                  // quantity
                    false,                     // fuzzy (runes don't have variations)
                    InventorySetupsStackCompareID.None, // stackCompare
                    false,                     // locked
                    slotIndex                  // slot in rune pouch
                );
                
                runePouchItems.add(runeItem);
                slotIndex++;
                
                log.debug("Added rune to pouch setup: {} x{} in slot {}", 
                    rune.name(), quantity, slotIndex - 1);
            }
        }
        
        // Fill remaining slots with dummy items if needed (rune pouch has 4 slots)
        while (runePouchItems.size() < 4) {
            InventorySetupsItem dummyItem = new InventorySetupsItem(
                -1,                               // dummy item ID
                "",                               // empty name
                0,                                // no quantity
                false,                            // not fuzzy
                InventorySetupsStackCompareID.None, // no stack compare
                false,                            // not locked
                runePouchItems.size()             // slot index
            );
            runePouchItems.add(dummyItem);
        }
        
        log.debug("Created rune pouch items list with {} items", runePouchItems.size());
        return runePouchItems;
    }
    
    /**
     * Creates the bolt pouch items list from the plan.
     * TODO: Currently returns empty list - can be enhanced to detect bolt pouch requirements
     */
    private List<InventorySetupsItem> createBoltPouchItemsList() {
        // For now, return empty list - can be enhanced later to handle bolt pouch requirements
        return new ArrayList<>();
    }
    
    /**
     * Creates the quiver items list from the plan.
     * TODO: Currently returns empty list - can be enhanced to detect quiver requirements
     */
    private List<InventorySetupsItem> createQuiverItemsList() {
        // For now, return empty list - can be enhanced later to handle quiver requirements
        return new ArrayList<>();
    }
    
    /**
     * Creates an InventorySetupsItem from an ItemRequirement.
     * Uses the same constructor pattern as found in MInventorySetupsPlugin.
     */
    private InventorySetupsItem createInventorySetupsItem(ItemRequirement item, int slot) {
        // Use fuzzy matching for items that have multiple variations
        boolean fuzzy = item.isStackable() || hasItemVariations(item.getId());
        
        // Default stack compare type - could be enhanced based on item type
        InventorySetupsStackCompareID stackCompare = 
            InventorySetupsStackCompareID.None;
        
        // Item is not locked by default - could be enhanced based on requirements
        boolean locked = false;
        
        return new InventorySetupsItem(
            item.getId(),
            item.getName(),
            item.getAmount(),
            fuzzy,
            stackCompare,
            locked,
            slot
        );
    }
    
    /**
     * Checks if an item has known variations (e.g., degraded equipment).
     */
    private boolean hasItemVariations(int itemId) {
        // Use the existing variation mapping to check for variations
        try {
            Collection<Integer> variations = 
                InventorySetupsVariationMapping.getVariations(itemId);
            return variations.size() > 1;
        } catch (Exception e) {
            // If variation mapping fails, default to false
            return false;
        }
    }
    
    /**
     * Creates an InventorySetup from item lists, reusing the same pattern as MInventorySetupsPlugin.addInventorySetup.
     */
    private InventorySetup createInventorySetupFromLists(
            String setupName,
            List<InventorySetupsItem> inventoryItems,
            List<InventorySetupsItem> equipmentItems,
            List<InventorySetupsItem> runePouchItems,
            List<InventorySetupsItem> boltPouchItems,
            List<InventorySetupsItem> quiverItems) {
        
        // Default settings - could be enhanced to be configurable
        java.awt.Color highlightColor = java.awt.Color.RED;
        boolean highlightDifference = true;
        java.awt.Color displayColor = null;
        boolean filterBank = true;
        boolean unorderedHighlight = false;
        int spellbook = 0; // Standard spellbook
        boolean favorite = false;
        int iconID = -1;
        
        return createInventorySetupFromLists(setupName, inventoryItems, equipmentItems, 
            runePouchItems, boltPouchItems, quiverItems, highlightColor, highlightDifference, 
            displayColor, filterBank, unorderedHighlight, spellbook, favorite, iconID);
    }
    
    /**
     * Creates an InventorySetup from item lists with configurable parameters.
     */
    private InventorySetup createInventorySetupFromLists(
            String setupName,
            List<InventorySetupsItem> inventoryItems,
            List<InventorySetupsItem> equipmentItems,
            List<InventorySetupsItem> runePouchItems,
            List<InventorySetupsItem> boltPouchItems,
            List<InventorySetupsItem> quiverItems,
            java.awt.Color highlightColor,
            boolean highlightDifference,
            java.awt.Color displayColor,
            boolean filterBank,
            boolean unorderedHighlight,
            int spellbook,
            boolean favorite,
            int iconID) {
        
        // Create the inventory setup using the same constructor as in addInventorySetup
        return new InventorySetup(
            inventoryItems,
            equipmentItems,
            runePouchItems,
            boltPouchItems,
            quiverItems,
            new java.util.HashMap<>(), // additionalFilteredItems
            setupName,
            "automatically Generated by the InventorySetupPlanner", // notes
            highlightColor,
            highlightDifference,
            displayColor,
            filterBank,
            unorderedHighlight,
            spellbook,
            favorite,
            iconID
        );
    }
    
    /**
     * Adds this plan as an InventorySetup to the MInventorySetupsPlugin with default settings.
     * Returns the created InventorySetup if successful, null otherwise.
     */
    public InventorySetup addToInventorySetupsPlugin(String setupName) {
        java.awt.Color highlightColor = java.awt.Color.RED;
        boolean highlightDifference = true;
        java.awt.Color displayColor = null;
        boolean filterBank = true;
        boolean unorderedHighlight = false;
        int spellbook = 0; // Standard spellbook
        boolean favorite = false;
        int iconID = -1;
        
        return addToInventorySetupsPlugin(setupName, highlightColor, highlightDifference, 
            displayColor, filterBank, unorderedHighlight, spellbook, favorite, iconID);
    }
    
    /**
     * Adds this plan as an InventorySetup to the MInventorySetupsPlugin with full configuration.
     * Returns the created InventorySetup if successful, null otherwise.
     */
    public InventorySetup addToInventorySetupsPlugin(String setupName, java.awt.Color highlightColor, 
            boolean highlightDifference, java.awt.Color displayColor, boolean filterBank, 
            boolean unorderedHighlight, int spellbook, boolean favorite, int iconID) {
        try {
            int MAX_SETUP_NAME_LENGTH = MInventorySetupsPlugin.MAX_SETUP_NAME_LENGTH;
            if( setupName.length() > MAX_SETUP_NAME_LENGTH) {
                // Trim the setup name to the maximum allowed length
                setupName = setupName.substring(0, MAX_SETUP_NAME_LENGTH);
            }
            // Convert this plan to an InventorySetup with all configuration parameters
            InventorySetup inventorySetup = convertToInventorySetup(setupName, highlightColor, 
                highlightDifference, displayColor, filterBank, unorderedHighlight, spellbook, favorite, iconID);
            
            if (inventorySetup == null) {
                log.error("Failed to convert plan to InventorySetup");
                return null;
            }
            
            // Update or add the setup using the same logic as Rs2InventorySetup
            updateSetup(inventorySetup);
            
            log.debug("Successfully added InventorySetup '{}' to MInventorySetupsPlugin", setupName);
            return inventorySetup;
            
        } catch (Exception e) {
            log.error("Failed to add plan to MInventorySetupsPlugin: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Updates an existing setup or adds a new one if it doesn't exist.
     * Uses the same logic as Rs2InventorySetup.updateSetup.
     * 
     * @param newSetup The setup to update/add
     */
    private void updateSetup(InventorySetup newSetup) {
        InventorySetup existingSetup = getInventorySetup(newSetup.getName());
        if (existingSetup != null) {
            MInventorySetupsPlugin.getInventorySetups().remove(existingSetup);
            MInventorySetupsPlugin plugin = getMInventorySetupsPlugin();
            if (plugin != null) {
                plugin.getCache().removeSetup(existingSetup);
            }
        }
        addSetupToPlugin(newSetup);
    }

    /**
     * Adds a setup to the plugin's setup list and cache.
     * Uses the same logic as Rs2InventorySetup.addSetupToPlugin.
     * 
     * @param setup The setup to add
     */
    private void addSetupToPlugin(InventorySetup setup) {        
        MInventorySetupsPlugin plugin = getMInventorySetupsPlugin();
        log.debug("\n\t Adding setup '{}' (name length{}) to MInventorySetupsPlugin", setup.getName() ,setup.getName().length()  ); 
        if (plugin != null) {
            plugin.addInventorySetup(setup);
            
            Rs2InventorySetup.isInventorySetup(setup.getName()); // Ensure setup is recognized as an inventory setup
            sleepUntil( () -> Rs2InventorySetup.isInventorySetup(setup.getName()), 5000);
            
            //plugin.getCache().addSetup(setup);
            //MInventorySetupsPlugin.getInventorySetups().add(setup);
            //plugin.getDataManager().updateConfig(true, false);
			//Layout setupLayout = plugin.getLayoutUtilities().createSetupLayout(setup);
			//plugin.getLayoutManager().saveLayout(setupLayout);
			//plugin.getTagManager().setHidden(setupLayout.getTag(), true);
			//SwingUtilities.invokeLater(() -> plugin.getPan().redrawOverviewPanel(false));
        }
    }
    
    /**
     * Helper method to get an inventory setup by name.
     * 
     * @param setupName The name of the setup to find
     * @return The InventorySetup if found, null otherwise
     */
    private InventorySetup getInventorySetup(String setupName) {
        return MInventorySetupsPlugin.getInventorySetups().stream()
            .filter(setup -> setup.getName().equalsIgnoreCase(setupName))
            .findFirst()
            .orElse(null);
    }
    
    /**
     * Helper method to get the MInventorySetupsPlugin instance.
     * 
     * @return MInventorySetupsPlugin instance or null if not available
     */
    private MInventorySetupsPlugin getMInventorySetupsPlugin() {
        return (MInventorySetupsPlugin) net.runelite.client.plugins.microbot.Microbot.getPlugin(
            MInventorySetupsPlugin.class.getName());
    }
    
    /**
     * Creates an Rs2InventorySetup instance from this planner for execution using existing Rs2 utilities.
     * This allows leveraging the existing loadInventory(), loadEquipment(), etc. methods.
     * 
     * @param setupName The name of the setup to create
     * @param mainScheduler The scheduler to monitor for cancellation
     * @return Rs2InventorySetup instance, or null if creation failed
     */
    public Rs2InventorySetup createRs2InventorySetup(String setupName, ScheduledFuture<?> mainScheduler) {
        try {
            // First, add this plan to the MInventorySetupsPlugin
            InventorySetup createdSetup = addToInventorySetupsPlugin(setupName);
            if (createdSetup == null) {
                log.error("Failed to add inventory setup to plugin");
                return null;
            }
            
            // Create Rs2InventorySetup using the setup name
            Rs2InventorySetup rs2Setup = 
                new Rs2InventorySetup(createdSetup.getName(), mainScheduler);
            
            log.info("\n\t-Successfully created Rs2InventorySetup from planner: {}", createdSetup.getName());
            return rs2Setup;
            
        } catch (Exception e) {
            log.error("Failed to create Rs2InventorySetup from planner: {}", e.getMessage(), e);
            return null;
        }
    }
    
    /**
     * Executes this plan using the Rs2InventorySetup approach.
     * This provides a more integrated solution that reuses existing bank and equipment management.
     * 
     * @param scheduledFuture The future to monitor for cancellation
     * @param setupName The name for the temporary setup
     * @return true if execution was successful
     */
    public boolean executeUsingRs2InventorySetup(CompletableFuture<Boolean> scheduledFuture, String setupName) {
        return executeUsingRs2InventorySetup(scheduledFuture, setupName, false);
    }
    
    /**
     * Executes this plan using the Rs2InventorySetup approach with optional banking of items not in setup.
     * This provides a more integrated solution that reuses existing bank and equipment management.
     * 
     * @param scheduledFuture The future to monitor for cancellation
     * @param setupName The name for the temporary setup
     * @param bankItemsNotInSetup Whether to bank items not in the setup first (excludes teleport items)
     * @return true if execution was successful
     */
    public boolean executeUsingRs2InventorySetup(CompletableFuture<Boolean> scheduledFuture, String setupName, boolean bankItemsNotInSetup) {
        try {
            log.info("\n\t-Executing plan using Rs2InventorySetup approach: {}", setupName);
            
            // Convert CompletableFuture to ScheduledFuture (simplified conversion)
            ScheduledFuture<?> mainScheduler = new ScheduledFuture<Object>() {
                @Override
                public long getDelay(TimeUnit unit) { return 0; }
                @Override
                public int compareTo(Delayed o) { return 0; }
                @Override
                public boolean cancel(boolean mayInterruptIfRunning) { return scheduledFuture.cancel(mayInterruptIfRunning); }
                @Override
                public boolean isCancelled() { return scheduledFuture.isCancelled(); }
                @Override
                public boolean isDone() { return scheduledFuture.isDone(); }
                @Override
                public Object get() throws InterruptedException, ExecutionException { return scheduledFuture.get(); }
                @Override
                public Object get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException { return scheduledFuture.get(timeout, unit); }
            };
            
            // Create Rs2InventorySetup from this plan
            Rs2InventorySetup rs2Setup = 
                createRs2InventorySetup(setupName, mainScheduler);
            
            if (rs2Setup == null) {
                log.error("Failed to create Rs2InventorySetup");
                return false;
            }
            if(rs2Setup.doesEquipmentMatch() && rs2Setup.doesInventoryMatch()){
                log.info("Plan already matches current inventory and equipment setup, skipping execution");
                return true; // No need to execute if already matches
            }
            if (!Rs2Bank.isOpen()) {
                if (!Rs2Bank.walkToBankAndUseBank() && !Rs2Player.isInteracting() && !Rs2Player.isMoving()) {
                    log.error("\n\tFailed to open bank for comprehensive item management");                    
                }
                boolean openBank= sleepUntil(() -> Rs2Bank.isOpen(), 5000);
                if (!openBank) {
                    log.error("\n\tFailed to open bank within timeout period,for invntory setup execution \"{}\"", setupName);
                    return false;
                }
            }
            
            // Bank items not in setup first if requested (excludes teleport items)
            if (bankItemsNotInSetup) {
                log.info("Banking items not in setup (excluding teleport items) before setting up: {}", setupName);
                if (!rs2Setup.bankAllItemsNotInSetup(true)) {
                    log.warn("Failed to bank all items not in setup, continuing with setup anyway");
                }
            }
            // Use existing Rs2InventorySetup methods to fulfill the requirements
            boolean equipmentSuccess = rs2Setup.loadEquipment();
            if (!equipmentSuccess) {
                log.error("Failed to load equipment using Rs2InventorySetup");
                return false;
            }
            
            boolean inventorySuccess = rs2Setup.loadInventory();
            if (!inventorySuccess) {
                log.error("Failed to load inventory using Rs2InventorySetup");
                return false;
            }
            
            // Verify the setup matches
            boolean equipmentMatches = rs2Setup.doesEquipmentMatch();
            boolean inventoryMatches = rs2Setup.doesInventoryMatch();
            
            if (equipmentMatches && inventoryMatches) {
                log.info("Successfully executed plan using Rs2InventorySetup: {}", setupName);
                return true;
            } else {
                log.warn("Plan execution completed but setup verification failed. Equipment matches: {}, Inventory matches: {}", 
                        equipmentMatches, inventoryMatches);
                return false;
            }
            
        } catch (Exception e) {
            log.error("Failed to execute plan using Rs2InventorySetup: {}", e.getMessage(), e);
            return false;
        }
    }
}
