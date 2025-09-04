package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.InventorySetupPlanner;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.LogicalRequirement;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Utility class for selecting the best available items to fulfill requirements.
 * Contains logic for finding optimal items based on availability, priority, and constraints.
 */
@Slf4j
public class RequirementSelector {

    /**
     * Finds the best available item from a list of logical requirements.
     * 
     * @param logicalReqs List of logical requirements to evaluate
     * @return The best available ItemRequirement, or null if none found
     */
    public static ItemRequirement findBestAvailableItemForInventory(List<LogicalRequirement> logicalReqs, int inventorySlot) {
        log.debug("Finding best available item from {} logical requirements", logicalReqs.size());
        
        for (LogicalRequirement logicalReq : logicalReqs) {
            List<ItemRequirement> items = LogicalRequirement.extractItemRequirementsFromLogical(logicalReq);
            log.debug("Checking {} items in logical requirement: {}", items.size(), logicalReq.displayString());
            
            for (ItemRequirement item : items) {
                if (isItemAvailable(item) && canPlayerUse(item)) {
                    log.debug("Found available item: {}", item.displayString());
                    return item;
                }
            }
        }
        
        log.debug("No available items found in any logical requirement");
        return null;
    }

    /**
     * Finds the best available item that hasn't already been planned for use.
     * 
     * @param logicalReqs List of logical requirements to evaluate
     * @param plan Current inventory setup plan
     * @return The best available ItemRequirement not already planned, or null if none found
     */
    public static ItemRequirement findBestAvailableItemNotAlreadyPlannedForInventory(LinkedHashSet<ItemRequirement> items, InventorySetupPlanner plan) {
                                
        for (ItemRequirement item : items) {
            if (isItemAvailable(item) && 
                canPlayerUse(item) && 
                !isItemAlreadyPlanned(item, plan)) {
                log.debug("Found available item not already planned: {}", item.displayString());
                return item;
            }
        }
                
        log.debug("No available items found that aren't already planned");
        return null;
    }

    
    
    /**
     * Enhanced method for finding the best available item for a specific equipment slot.
     * This method considers already planned items to avoid conflicts and validates equipment slot compatibility.
     * 
     * @param logicalReqs The logical requirements to search through
     * @param targetEquipmentSlot The specific equipment slot to find an item for
     * @param alreadyPlanned Items already planned (for conflict avoidance)
     * @return The best available item for the slot, or null if none found
     */
    public static ItemRequirement findBestAvailableItemForEquipmentSlot(List<ItemRequirement> equipmentSlotReqs, 
                                                                        EquipmentInventorySlot targetEquipmentSlot,
                                                                        Set<ItemRequirement> alreadyPlanned) {
        
            List<ItemRequirement> items = equipmentSlotReqs;
            
            // Sort by priority, then by type priority: EQUIPMENT > EITHER > INVENTORY, then by rating
            items.sort((a, b) -> {
                // First sort by priority (MANDATORY > RECOMMENDED > OPTIONAL)
                int priorityCompare = a.getPriority().compareTo(b.getPriority());
                if (priorityCompare != 0) {
                    return priorityCompare;
                }
                
                // Then sort by type priority: EQUIPMENT > EITHER > INVENTORY
                int typePriorityA = getTypePriority(a.getRequirementType());
                int typePriorityB = getTypePriority(b.getRequirementType());
                
                if (typePriorityA != typePriorityB) {
                    return Integer.compare(typePriorityB, typePriorityA); // Higher priority first
                }
                
                // Finally by rating (higher is better)
                return Integer.compare(b.getRating(), a.getRating());
            });
            
            for (ItemRequirement item : items) {
                // Skip if already planned
                if (alreadyPlanned.contains(item)) {
                    continue;
                }
                
                // Validate that this item can be assigned to the target equipment slot and meets skill requirements
                if (!canAssignToEquipmentSlot(item, targetEquipmentSlot)) {
                    log.debug("Item {} cannot be assigned to equipment slot {}, skipping", 
                             item.getName(), targetEquipmentSlot.name());
                    continue;
                }
                
                // Check if item is available
                if (isItemAvailable(item)) {
                    log.debug("Found compatible item {} for equipment slot {}", 
                             item.getName(), targetEquipmentSlot.name());
                    return item;
                }
            }
        
        return null;
    }
    
    /**
     * Gets the type priority for sorting equipment requirements.
     * Higher numbers = higher priority.
     */
    private static int getTypePriority(RequirementType type) {
        switch (type) {
            case EQUIPMENT:
                return 3;
            case EITHER:
                return 2;
            case INVENTORY:
                return 1;
            default:
                return 0;
        }
    }
    
    /**
     * Validates if two ItemRequirements represent the same logical requirement.
     * This accounts for the fact that requirements may be copied or modified during planning.
     * 
     * @param original The original requirement
     * @param planned The planned requirement
     * @return true if they match, false otherwise
     */
    public static boolean itemRequirementMatches(ItemRequirement original, ItemRequirement planned) {
        // Check if they have overlapping item IDs
        return original.getIds().stream().anyMatch(planned.getIds()::contains) &&
               original.getAmount() <= planned.getAmount();
    }
    
    /**
     * Finds items that can fulfill multiple requirements simultaneously.
     * Useful for optimizing inventory space.
     * 
     * @param logicalReqs The logical requirements to analyze
     * @return Map of items to the number of requirements they can fulfill
     */
    public static Map<ItemRequirement, Integer> findMultiPurposeItems(List<LogicalRequirement> logicalReqs) {
        Map<ItemRequirement, Integer> multiPurposeMap = new HashMap<>();
        List<ItemRequirement> allItems = LogicalRequirement.extractAllItemRequirements(logicalReqs);
        
        for (ItemRequirement item : allItems) {
            int count = 0;
            for (ItemRequirement other : allItems) {
                if (itemRequirementMatches(item, other)) {
                    count++;
                }
            }
            if (count > 1) {
                multiPurposeMap.put(item, count);
            }
        }
        
        return multiPurposeMap;
    }
    
    

    /**
     * Checks if an item is currently available (in inventory, equipment, or bank).
     * Dummy items are always considered "available" since they're just slot placeholders.
     * Uses the ItemRequirement's own availability checking methods which properly handle fuzzy matching and amounts.
     * 
     * @param item ItemRequirement to check
     * @return true if the item is available
     */
    public static boolean isItemAvailable(ItemRequirement item) {
        // Dummy items are always considered available since they're just slot placeholders
        if (item.isDummyItemRequirement()) {
            return true;
        }
        
        // Use ItemRequirement's own availability checking which handles fuzzy matching and amounts properly
        return item.isAvailableInInventoryOrBank();
    }

    /**
     * Validates if the player can use or equip an item based on skill requirements.
     * Checks both equipment and usage skill requirements.
     * 
     * @param item ItemRequirement to validate
     * @return true if player meets all skill requirements for the item
     */
    public static boolean canPlayerUse(ItemRequirement item) {
        // Dummy items can always be used
        if (item.isDummyItemRequirement()) {
            return true;
        }                        
        // For all items, check usage requirements if specified
        if (!ItemRequirement.canPlayerUseItem(item)) {
            log.debug("Player cannot use item {} due to skill requirements", item.getName());
            return false;
        }
        
        return true;
    }

    /**
     * Validates if an item can be assigned to a specific equipment slot and meets skill requirements.
     * 
     * @param item ItemRequirement to validate
     * @param targetEquipmentSlot Target equipment slot
     * @return true if item can be assigned to the slot and player meets requirements
     */
    public static boolean canAssignToEquipmentSlot(ItemRequirement item, EquipmentInventorySlot targetEquipmentSlot) {
        // Dummy items can always be assigned
        if (item.isDummyItemRequirement()) {
            return true;
        }
        
        // Item must be able to be equipped
        if (!item.canBeEquipped()) {
            log.debug("Item {} cannot be equipped", item.getName());
            return false;
        }
        
        // Check if item matches the equipment slot
        if (item.getEquipmentSlot() != null && item.getEquipmentSlot() != targetEquipmentSlot) {
            log.debug("Item {} equipment slot mismatch: expected {}, actual {}", 
                     item.getName(), targetEquipmentSlot, item.getEquipmentSlot());
            return false;
        }
        
        // Check skill requirements for equipping
        if (!ItemRequirement.canPlayerEquipItem(item)) {
            log.debug("Player cannot equip item {} due to skill requirements", item.getName());
            return false;
        }
          // Check skill requirements for equipping
        if (!ItemRequirement.canPlayerUseItem(item)) {
            log.debug("Player cannot use item {} due to skill requirements", item.getName());
            return false;
        }
        
        return true;
    }

   

    /**
     * Checks if an item is already planned for use in the current plan.
     * 
     * @param item ItemRequirement to check
     * @param plan Current inventory setup plan
     * @return true if the item is already planned
     */
    private static boolean isItemAlreadyPlanned(ItemRequirement item, InventorySetupPlanner plan) {
        // Check equipment assignments
        for (ItemRequirement plannedItem : plan.getEquipmentAssignments().values()) {
            if (plannedItem != null && hasMatchingItemId(plannedItem, item)) {
                return true;
            }
        }
        
        // Check inventory slot assignments
        for (ItemRequirement plannedItem : plan.getInventorySlotAssignments().values()) {
            if (plannedItem != null && hasMatchingItemId(plannedItem, item)) {
                return true;
            }
        }
        
        // Check flexible inventory items
        for (ItemRequirement plannedItem : plan.getFlexibleInventoryItems()) {
            if (hasMatchingItemId(plannedItem, item)) {
                return true;
            }
        }
        
        return false;
    }

    /**
     * Checks if two ItemRequirements have matching item IDs.
     * 
     * @param item1 First item requirement
     * @param item2 Second item requirement
     * @return true if they have at least one matching item ID
     */
    private static boolean hasMatchingItemId(ItemRequirement item1, ItemRequirement item2) {
        // Since ItemRequirement now only supports single IDs, simply compare them
        return item1.getId() == item2.getId();
    }
}
