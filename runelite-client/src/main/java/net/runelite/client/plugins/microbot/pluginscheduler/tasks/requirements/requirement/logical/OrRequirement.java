package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical;

import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * OR logical requirement - at least one child requirement must be fulfilled.
 * This is useful for situations where multiple alternatives exist but only one is needed.
 * 
 * All child requirements must be of the same type to ensure type safety and enable
 * efficient caching. Mixed requirement types should use ConditionalRequirement instead.
 * 
 * Examples:
 * - Food items: Any one type of food is sufficient
 * - Equipment slots: Any one item for the slot is sufficient  
 * - Transportation methods: Any one method to reach a location
 * 
 * Similar to OrCondition but adapted for the requirement system.
 */
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class OrRequirement extends LogicalRequirement {
    
    /**
     * Creates an OR requirement with explicit child type specification.
     * 
     * @param priority Priority level of this logical requirement
     * @param rating Effectiveness rating (1-10)
     * @param description Human-readable description
     * @param TaskContext When this requirement should be fulfilled
     * @param allowedChildType The class type that child requirements must be
     * @param requirements Child requirements - any one of these can fulfill the OR requirement
     */
    public OrRequirement(RequirementPriority priority, int rating, String description, 
                        TaskContext taskContext, Class<? extends Requirement> allowedChildType,
                        Requirement... requirements) {
        super(RequirementType.OR_LOGICAL, priority, rating, description, taskContext, allowedChildType, requirements);
    }
     public OrRequirement(RequirementPriority priority, int rating, String description, 
                        TaskContext taskContext, Class<? extends Requirement> allowedChildType) {
        super(RequirementType.OR_LOGICAL, priority, rating, description, taskContext, allowedChildType);
    }
    
    /**
     * Creates an OR requirement with the specified parameters, inferring child type from first requirement.
     * 
     * @param priority Priority level of this logical requirement
     * @param rating Effectiveness rating (1-10)
     * @param description Human-readable description
     * @param TaskContext When this requirement should be fulfilled
     * @param requirements Child requirements - any one of these can fulfill the OR requirement
     */
    public OrRequirement(RequirementPriority priority, int rating, String description, 
                        TaskContext taskContext, Requirement... requirements) {
        super(RequirementType.OR_LOGICAL, priority, rating, description, taskContext, 
              inferChildTypeFromRequirements(requirements), requirements);
    }
    
    /**
     * Helper method to infer the child type from the provided requirements.
     * If all requirements are ItemRequirements, returns ItemRequirement.class.
     * Otherwise returns the most common compatible type.
     */
    private static Class<? extends Requirement> inferChildTypeFromRequirements(Requirement... requirements) {
        if (requirements.length == 0) {
            return Requirement.class; // Default fallback
        }
        
        // Check if all requirements are ItemRequirements
        boolean allItemRequirements = true;
        for (Requirement req : requirements) {
            if (!(req instanceof ItemRequirement)) {
                allItemRequirements = false;
                break;
            }
        }
        
        if (allItemRequirements) {
            return ItemRequirement.class;
        }
        
        // Fallback to first requirement's class
        Requirement firstReq = requirements[0];
        if (firstReq instanceof LogicalRequirement) {
            return ((LogicalRequirement) firstReq).getAllowedChildType();
        } else {
            return firstReq.getClass();
        }
    }
    
    /**
     * Convenience constructor with default rating of 5, inferring child type from first requirement.
     * 
     * @param priority Priority level of this logical requirement  
     * @param description Human-readable description
     * @param TaskContext When this requirement should be fulfilled
     * @param requirements Child requirements - any one of these can fulfill the OR requirement
     */
    public OrRequirement(RequirementPriority priority, String description, TaskContext taskContext, 
                        Requirement... requirements) {
        this(priority, 5, description, taskContext, requirements);
    }
    
    /**
     * Checks if this OR requirement is logically fulfilled.
     * Returns true if at least one child requirement is fulfilled.
     * 
     * @return true if any child requirement is fulfilled, false if none are fulfilled
     */
    @Override
    public boolean isLogicallyFulfilled() {
        if (childRequirements.isEmpty()) {
            return true; // Empty OR is considered satisfied
        }        
        return childRequirements.stream().anyMatch(req -> {
            if (req instanceof LogicalRequirement) {
                return ((LogicalRequirement) req).isLogicallyFulfilled();
            } else {                
                return req.isFulfilled();
            }
        });
    }
    public boolean isFulfilled() {
        // Check if any child requirement is fulfilled
        return isLogicallyFulfilled();
    }
    
    /**
     * Gets requirements that are blocking fulfillment of this OR requirement.
     * For an OR requirement, all child requirements are blocking if none are fulfilled.
     * If at least one is fulfilled, nothing is blocking.
     * 
     * @return List of all child requirements if none are fulfilled, otherwise empty list
     */
    @Override
    public List<Requirement> getBlockingRequirements() {
        // For an OR requirement, if any requirement is fulfilled, nothing is blocking
        if (isLogicallyFulfilled()) {
            return new ArrayList<>();
        }
        
        // If we reach here, none are fulfilled, so all requirements are blocking
        List<Requirement> blocking = new ArrayList<>();
        
        for (Requirement child : childRequirements) {
            if (child instanceof LogicalRequirement) {
                // Add the logical requirement itself as blocking, not its children
                blocking.add(child);
            } else {
                blocking.add(child);
            }
        }
        
        return blocking;
    }
    
    /**
     * Gets the name of this OR requirement.
     * 
     * @return A descriptive name for this OR requirement
     */
    @Override
    public String getName() {
        if (childRequirements.isEmpty()) {
            return "Empty OR Requirement";
        }
        
        if (childRequirements.size() == 1) {
            return childRequirements.get(0).getName();
        }
        
        return String.format("OR(%s alternatives)", childRequirements.size());
    }
    
    /**
     * Gets the best fulfilled requirement from this OR requirement.
     * Returns the highest-rated fulfilled requirement, or empty if none are fulfilled.
     * 
     * @return The best fulfilled requirement, or empty optional
     */
    public java.util.Optional<Requirement> getBestFulfilledRequirement() {
        return childRequirements.stream()
                .filter(req -> {
                    if (req instanceof LogicalRequirement) {
                        return ((LogicalRequirement) req).isLogicallyFulfilled();
                    } else {
                        return req.isFulfilled();
                    }
                })
                .max(Requirement::compareTo);
    }
    
    /**
     * Gets all fulfilled requirements from this OR requirement.
     * Useful when multiple alternatives are fulfilled and you want to see all options.
     * 
     * @return List of all fulfilled requirements
     */
    public List<Requirement> getAllFulfilledRequirements() {
        return childRequirements.stream()
                .filter(req -> {
                    if (req instanceof LogicalRequirement) {
                        return ((LogicalRequirement) req).isLogicallyFulfilled();
                    } else {
                        return req.isFulfilled();
                    }
                })
                .collect(java.util.stream.Collectors.toList());
    }
    
    /**
     * Returns a detailed description of the OR requirement with status information.
     * 
     * @return Formatted description with child requirement status
     */
    public String getDetailedDescription() {
        StringBuilder sb = new StringBuilder();
        
        // Basic description
        sb.append("OR Requirement: Any requirement can be fulfilled\n");
        
        // Status information
        boolean fulfilled = isLogicallyFulfilled();
        sb.append("Status: ").append(fulfilled ? "Fulfilled" : "Not fulfilled").append("\n");
        sb.append("Child Requirements: ").append(childRequirements.size()).append("\n");
        
        // Progress information
        double progress = getProgressPercentage();
        sb.append(String.format("Overall Progress: %.1f%%\n", progress));
        
        // Count fulfilled requirements
        int fulfilledCount = getAllFulfilledRequirements().size();
        sb.append("Fulfilled Requirements: ").append(fulfilledCount).append("/").append(childRequirements.size()).append("\n\n");
        
        // List all child requirements
        sb.append("Child Requirements:\n");
        for (int i = 0; i < childRequirements.size(); i++) {
            Requirement requirement = childRequirements.get(i);
            boolean childFulfilled = requirement instanceof LogicalRequirement ? 
                    ((LogicalRequirement) requirement).isLogicallyFulfilled() : 
                    requirement.isFulfilled();
            
            sb.append(String.format("%d. %s [%s]\n", 
                    i + 1, 
                    requirement.getName(),
                    childFulfilled ? "FULFILLED" : "NOT FULFILLED"));
        }
        
        return sb.toString();
    }
    
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        
        // Basic information
        sb.append("OrRequirement:\n");
        sb.append("  ┌─ Configuration ─────────────────────────────\n");
        sb.append("  │ Type: OR (Any requirement can be fulfilled)\n");
        sb.append("  │ Child Requirements: ").append(childRequirements.size()).append("\n");
        sb.append("  │ Priority: ").append(priority.name()).append("\n");
        sb.append("  │ Rating: ").append(rating).append("/10\n");
        
        // Status information
        sb.append("  ├─ Status ──────────────────────────────────\n");
        boolean anyFulfilled = isLogicallyFulfilled();
        sb.append("  │ Fulfilled: ").append(anyFulfilled).append("\n");
        
        // Count fulfilled requirements
        int fulfilledCount = getAllFulfilledRequirements().size();
        sb.append("  │ Fulfilled Requirements: ").append(fulfilledCount).append("/").append(childRequirements.size()).append("\n");
        sb.append("  │ Progress: ").append(String.format("%.1f%%", getProgressPercentage())).append("\n");
        
        // Child requirements
        if (!childRequirements.isEmpty()) {
            sb.append("  ├─ Child Requirements ────────────────────────\n");
            
            for (int i = 0; i < childRequirements.size(); i++) {
                Requirement requirement = childRequirements.get(i);
                String prefix = (i == childRequirements.size() - 1) ? "  └─ " : "  ├─ ";
                
                boolean childFulfilled = requirement instanceof LogicalRequirement ? 
                        ((LogicalRequirement) requirement).isLogicallyFulfilled() : 
                        requirement.isFulfilled();
                
                sb.append(prefix).append(String.format("Requirement %d: %s [%s]\n", 
                        i + 1, 
                        requirement.getClass().getSimpleName(),
                        childFulfilled ? "FULFILLED" : "NOT FULFILLED"));
            }
        } else {
            sb.append("  └─ No Child Requirements ───────────────────────\n");
        }
        
        return sb.toString();
    }

    
     /**
     * Merges this OrRequirement with another OrRequirement.
     * Combines all child requirements, merges ids, and sets rating to highest of both.
     *
     * @param other The OrRequirement to merge with
     * @return A new merged OrRequirement
     */    
    public static OrRequirement merge(OrRequirement first, OrRequirement other) {
        if (!(other instanceof OrRequirement)) {
            throw new IllegalArgumentException("Can only merge with another OrRequirement");
        }
        OrRequirement otherOr = (OrRequirement) other;
        // Combine all children
        List<Requirement> mergedChildren = new ArrayList<>(first.childRequirements);
        for (Requirement req : otherOr.childRequirements) {
            if (!mergedChildren.contains(req)) {
                mergedChildren.add(req);
            }
        }
        // Merge ids
        List<Integer> mergedIds = new ArrayList<>();
        if (first.getIds() != null) mergedIds.addAll(first.getIds());
        if (otherOr.ids != null) {
            for (Integer id : otherOr.ids) {
                if (!mergedIds.contains(id)) mergedIds.add(id);
            }
        }
        // Find max rating
        int maxRating = first.getRating();
        for (Requirement req : mergedChildren) {
            if (req.getRating() > maxRating) {
                maxRating = req.getRating();
            }
        }
        // Use the first non-empty description, or combine
        String desc = (first.getDescription() != null && !first.getDescription().isEmpty()) ? first.getDescription() : otherOr.description;
        if (desc == null || desc.isEmpty()) desc = "Merged OR Requirement";
        // Use the higher priority
        RequirementPriority mergedPriority = first.getPriority().ordinal() < otherOr.priority.ordinal() ? first.getPriority() : otherOr.priority;
        // Use the most general allowedChildType
        Class<? extends Requirement> mergedType = first.allowedChildType.isAssignableFrom(otherOr.allowedChildType) ? first.allowedChildType : otherOr.allowedChildType;
        // Create merged OrRequirement
        OrRequirement merged = new OrRequirement(mergedPriority, maxRating, desc, first.getTaskContext(), mergedType);
        merged.ids = mergedIds;
        for (Requirement req : mergedChildren) {
            merged.addRequirement(req);
        }
        
        return merged;
    }
   
    
    // ==============================
    // Static Convenience Functions
    // ==============================
    
    // Pattern to detect charged items with numbers in parentheses, e.g., "Amulet of glory(6)"
    private static final Pattern CHARGED_ITEM_PATTERN = Pattern.compile(".*\\((\\d+)\\)$");
    
    /**
     * Creates an OR requirement from a list of item IDs with automatic rating assignment for charged items.
     * This convenience function automatically detects charged items in the list and assigns ratings based on charge levels.
     * 
     * @param itemIds List of item IDs to create OR group from
     * @param amount Amount of each item required (default: 1)
     * @param equipmentSlot Equipment slot for equipment requirements (null for inventory)
     * @param inventorySlot Inventory slot (-1 for any slot)
     * @param priority Priority level of the OR requirement
     * @param baseRating Base rating for non-charged items (1-10)
     * @param description Description of the OR requirement
     * @param TaskContext When this requirement should be fulfilled
     * @param skillToUse Skill required to use items (optional)
     * @param minimumLevelToUse Minimum level required to use items (optional)
     * @param skillToEquip Skill required to equip items (optional)
     * @param minimumLevelToEquip Minimum level required to equip items (optional)
     * @param preferLowerCharge If true, lower charge items get higher ratings; if false, higher charge items get higher ratings
     * @param chargedItemsOnly If true, only include charged items from the list; if false, include all items
     * @return OrRequirement with ItemRequirement children, rated appropriately
     */
    public static OrRequirement fromItemIds(List<Integer> itemIds, int amount, EquipmentInventorySlot equipmentSlot,
                                          int inventorySlot, RequirementPriority priority, int baseRating,
                                          String description, TaskContext taskContext,
                                          Skill skillToUse, Integer minimumLevelToUse,
                                          Skill skillToEquip, Integer minimumLevelToEquip,
                                          boolean preferLowerCharge, boolean chargedItemsOnly) {
        
        List<Requirement> itemRequirements = new ArrayList<>();
        
        for (Integer itemId : itemIds) {
            String itemName = getItemNameById(itemId);
            if (itemName == null) {
                continue; // Skip items with unknown names
            }
            
            boolean isCharged = isChargedItem(itemName);
            
            // Skip non-charged items if chargedItemsOnly is true
            if (chargedItemsOnly && !isCharged) {
                continue;
            }
            
            int rating = baseRating;
            
            // Adjust rating for charged items
            if (isCharged) {
                int chargeLevel = getChargeLevel(itemName);
                if (chargeLevel != Integer.MAX_VALUE) {
                    // Assign rating based on charge level
                    // For preferLowerCharge=true: lower charge = higher rating
                    // For preferLowerCharge=false: higher charge = higher rating
                    if (preferLowerCharge) {
                        // Lower charge gets higher rating (inverse relationship)
                        // Charge 1 = rating 10, Charge 10 = rating 1
                        rating = Math.max(1, Math.min(10, 11 - chargeLevel));
                    } else {
                        // Higher charge gets higher rating (direct relationship)
                        // Charge 1 = rating 1, Charge 10 = rating 10
                        rating = Math.max(1, Math.min(10, chargeLevel));
                    }
                }
            }
            
            // Create individual ItemRequirement for this item
            ItemRequirement itemReq = new ItemRequirement(
                itemId, amount, equipmentSlot, inventorySlot,
                priority, rating, itemName, taskContext,
                skillToUse, minimumLevelToUse, skillToEquip, minimumLevelToEquip, preferLowerCharge
            );
            
            itemRequirements.add(itemReq);
        }
        
        // Convert to array for constructor
        Requirement[] reqArray = itemRequirements.toArray(new Requirement[0]);
        
        return new OrRequirement(priority, baseRating, description, taskContext, reqArray);
    }
    
    /**
     * Simplified convenience method for creating OR requirement from item IDs with default parameters.
     * Uses preferLowerCharge=false and chargedItemsOnly=false by default.
     * 
     * @param itemIds List of item IDs to create OR group from
     * @param amount Amount required of each item
     * @param equipmentSlot Equipment slot for equipment requirements (null for inventory)
     * @param inventorySlot Inventory slot (-1 for any slot)
     * @param priority Priority level of the OR requirement
     * @param baseRating Base rating for items (1-10)
     * @param description Description of the OR requirement
     * @param TaskContext When this requirement should be fulfilled
     * @param preferLowerCharge Whether to prefer lower charge variants
     * @return OrRequirement with ItemRequirement children
     */
    public static OrRequirement fromItemIds(List<Integer> itemIds, int amount, EquipmentInventorySlot equipmentSlot,
                                          int inventorySlot, RequirementPriority priority, int baseRating,
                                          String description, TaskContext taskContext, boolean preferLowerCharge) {
        return fromItemIds(itemIds, amount, equipmentSlot, inventorySlot, priority, baseRating,
                          description, taskContext, null, null, null, null, preferLowerCharge, false);
    }
    
    /**
     * Convenience method for creating OR requirement from varargs item IDs.
     * 
     * @param amount Amount of each item required
     * @param equipmentSlot Equipment slot for equipment requirements (null for inventory)
     * @param inventorySlot Inventory slot (-1 for any slot)
     * @param priority Priority level of the OR requirement
     * @param baseRating Base rating for items (1-10)
     * @param description Description of the OR requirement
     * @param TaskContext When this requirement should be fulfilled
     * @param preferLowerCharge Whether to prefer lower charge variants
     * @param itemIds Varargs list of item IDs
     * @return OrRequirement with ItemRequirement children
     */
    public static OrRequirement fromItemIds(int amount, EquipmentInventorySlot equipmentSlot, int inventorySlot,
                                          RequirementPriority priority, int baseRating, String description,
                                          TaskContext taskContext, boolean preferLowerCharge, Integer... itemIds) {
        return fromItemIds(Arrays.asList(itemIds), amount, equipmentSlot, inventorySlot, priority, baseRating,
                          description, taskContext, preferLowerCharge);
    }
    
    /**
     * Utility method to get item name by ID.
     * 
     * @param itemId The item ID
     * @return Item name or null if not found
     */
    private static String getItemNameById(int itemId) {
        try {
            return Microbot.getClientThread().runOnClientThreadOptional(() -> 
            Microbot.getItemManager().getItemComposition(itemId).getName()
        ).orElse("");            
        } catch (Exception e) {
            return null;
        }
    }
    
    /**
     * Checks if an item name represents a charged item.
     * 
     * @param itemName The item name to check
     * @return true if the item appears to be charged, false otherwise
     */
    private static boolean isChargedItem(String itemName) {
        return itemName != null && CHARGED_ITEM_PATTERN.matcher(itemName).matches();
    }
    
    /**
     * Gets the charge level from an item name.
     * 
     * @param itemName The item name to parse
     * @return The charge level, or Integer.MAX_VALUE if not a charged item
     */
    private static int getChargeLevel(String itemName) {
        if (itemName == null) {
            return Integer.MAX_VALUE;
        }
        
        Matcher matcher = CHARGED_ITEM_PATTERN.matcher(itemName);
        if (matcher.matches()) {
            try {
                return Integer.parseInt(matcher.group(1));
            } catch (NumberFormatException e) {
                return Integer.MAX_VALUE;
            }
        }
        
        return Integer.MAX_VALUE;
    }
}
