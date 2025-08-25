package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.OrRequirement;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2FuzzyItem;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import lombok.Getter;
import lombok.EqualsAndHashCode;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Enhanced item recommendation that supports multiple item IDs, different requirement types,
 * and priority-based selection logic. Generalized to handle both equipment and inventory requirements.
 * 
 * Supports both equipment requirements (must be equipped) and inventory requirements (must be in inventory).
 * Also includes charge detection for items like rings of dueling, binding necklaces, etc.
 */
@Getter
@EqualsAndHashCode(callSuper = true)
@Slf4j
public class ItemRequirement extends Requirement {
    
    
    /**
     * Default item count for this requirement.
     * This can be overridden if the plugin requires a specific count.
     */
    private final int amount;
    
    /**
     * The equipment slot this item should occupy (only relevant for EQUIPMENT and EITHER requirement types).
     * Null for pure inventory items.
     */
    private final EquipmentInventorySlot equipmentSlot;
    
    /**
     * The specific inventory slot this item should occupy (0-27).
     * -1 means any available slot.
     * Only relevant for INVENTORY and EITHER requirement types.
     * For EITHER requirements, this represents the preferred inventory slot if not equipped.
     */
    @Getter    
    private final Integer inventorySlot;
 
    /**
     * Skill name for the minimum level requirement to use this item (e.g., "Attack", "Runecraft").
     * Null if no level requirement.
     */
    private final Skill skillToUse;
    
    /**
     * Minimum player level required to use this item, if applicable.
     * Used for items that have a level requirement to be used effectively.
     * 
     * Null if no level requirement.
     */    
    private final Integer minimumLevelToUse;
    
    /**
     * Skill name for the minimum level requirement (e.g., "Attack", "Runecraft").
     * Null if no level requirement.
     */
    private final Skill skillToEquip;
    
    /**
     * Minimum player level required to use the highest priority item in the list.
     * Used for level-based item selection (e.g., attack level for weapons).
     * Null if no level requirement.
     */
    private final Integer minimumLevelToEquip;
    
    /**
     * Whether to use fuzzy matching for this item requirement.
     * When fuzzy is true, the requirement will match any variation of the same item.
     * For example, different charge states of an item (ring of dueling(8), ring of dueling(7), etc.)
     * or different variants of the same item (graceful pieces in different colors).
     * Uses InventorySetupsVariationMapping under the hood to map between equivalent item IDs.
     */
    private final boolean fuzzy;    
    private ItemComposition itemComposition = null; // Cached item composition for performance
   
    /**
     * Full constructor for item requirement with schedule context and inventory slot support.
     * The RequirementType is automatically inferred from the slot parameters:
     * - INVENTORY: equipmentSlot is null and inventorySlot is provided (not -1)
     * - EQUIPMENT: equipmentSlot is provided and inventorySlot is -1
     * - EITHER: both equipmentSlot and inventorySlot are provided (not -1)
     */
    public ItemRequirement(
            int itemId,
            int amount,
            EquipmentInventorySlot equipmentSlot,
            Integer inventorySlot,
            RequirementPriority priority,
            int rating,
            String description,
            TaskContext taskContext,
            Skill skillToUse,
            Integer minimumLevelToUse,
            Skill skillToEquip,
            Integer minimumLevelToEquip,
            boolean fuzzy) {
        
        // Call super constructor with inferred RequirementType and resolved item ID
        super(inferRequirementType(equipmentSlot, inventorySlot), priority, rating, description, 
              Arrays.asList(resolveOptimalItemId(itemId, amount)), taskContext);
        
        // Only override amount for equipment items (not inventory or ammo)
        if (equipmentSlot != null && equipmentSlot != EquipmentInventorySlot.AMMO) {
            amount = 1; // For non-ammo equipment items, amount is typically 1
        }
        this.amount = amount;
        this.equipmentSlot = equipmentSlot;
        this.inventorySlot = inventorySlot;
        this.skillToUse = skillToUse;
        this.minimumLevelToUse = minimumLevelToUse;
        this.skillToEquip = skillToEquip;
        this.minimumLevelToEquip = minimumLevelToEquip;
        if (isNoted()){
            this.fuzzy = true;
        }else if(Rs2FuzzyItem.isChargedItem(itemId)){
            this.fuzzy = fuzzy; // it can be desired to use the exact item.. 
        }else{
            this.fuzzy = fuzzy;
        }
        
        // Validate slot assignments
        validateSlotAssignments();
    }
    
    // Convenience constructors
    
    /**
     * Constructor for single item ID with equipment requirement.
     */
    public ItemRequirement(int itemId, int amount, EquipmentInventorySlot equipmentSlot, 
                        RequirementPriority priority, int rating, String description, TaskContext taskContext) {
        this(itemId, amount, equipmentSlot, -2, priority, rating, description, 
             taskContext, null, null, null, null, false);
    }
    
    /**
     * Constructor for single item ID with equipment requirement with default amount of 1.
     */
    public ItemRequirement(int itemId, EquipmentInventorySlot equipmentSlot, 
                        RequirementPriority priority, int rating, String description, TaskContext taskContext) {
        this(itemId, 1, equipmentSlot, -2, priority, rating, description, 
             taskContext, null, null, null, null, false);
    }
    
    /**
     * Constructor for single item ID with equipment requirement and skill requirements for use only.
     */
    public ItemRequirement(int itemId, int amount, EquipmentInventorySlot equipmentSlot, Integer inventorySlot,
                        RequirementPriority priority, int rating, String description, TaskContext taskContext, 
                        Skill skillToUse, Integer minimumLevelToUse) {
        this(itemId, amount, equipmentSlot, inventorySlot, priority, rating, description,
             taskContext, skillToUse, minimumLevelToUse, null, null, false);
    }

    /**
     * Constructor for single item ID with equipment requirement and skill requirements for use only.
    */
    public ItemRequirement(int itemId, int amount, EquipmentInventorySlot equipmentSlot,
                        RequirementPriority priority, int rating, String description, TaskContext taskContext, 
                        Skill skillToUse, Integer minimumLevelToUse) {
        this(itemId, amount, equipmentSlot, -2, priority, rating, description,
             taskContext, skillToUse, minimumLevelToUse, null, null, false);
    }
    
    /**
     * Constructor for single item ID with equipment requirement and both skill requirements (use and equip).
     */
    public ItemRequirement(int itemId, int amount, EquipmentInventorySlot equipmentSlot, Integer inventorySlot,
                        RequirementPriority priority, int rating, String description, TaskContext taskContext,
                        Skill skillToUse, Integer minimumLevelToUse, Skill skillToEquip, Integer minimumLevelToEquip) {
        this(itemId, amount, equipmentSlot, inventorySlot, priority, rating, description,
             taskContext, skillToUse, minimumLevelToUse, skillToEquip, minimumLevelToEquip, false);
    }

     /**
     * Constructor for single item ID with equipment requirement and both skill requirements (use and equip).
     */
    public ItemRequirement(int itemId, int amount, EquipmentInventorySlot equipmentSlot,
                        RequirementPriority priority, int rating, String description, TaskContext taskContext,
                        Skill skillToUse, Integer minimumLevelToUse, Skill skillToEquip, Integer minimumLevelToEquip) {
        this(itemId, amount, equipmentSlot, -2, priority, rating, description,
             taskContext, skillToUse, minimumLevelToUse, skillToEquip, minimumLevelToEquip, false);
    }
    
    /**
     * Constructor for single item ID with fuzzy option.
     */
    public ItemRequirement(int itemId, EquipmentInventorySlot equipmentSlot,
                        RequirementPriority priority, int rating, String description, TaskContext taskContext, boolean fuzzy) {
        this(itemId, 1, equipmentSlot, -2, priority, rating, description,
             taskContext, null, null, null, null, fuzzy);
    }
    
    /**
     * Additional constructors for inventory slot specification
     */
    
    /**
     * Constructor for single item ID with specific inventory slot.
     */
    public ItemRequirement(int itemId, int amount, Integer inventorySlot,
                        RequirementPriority priority, int rating, String description, TaskContext taskContext) {
        this(itemId, amount, null, inventorySlot, priority, rating, description, 
             taskContext, null, null, null, null, false);
    }
    
    /**
     * Constructor for EITHER requirement with both equipment and inventory slot specification.
     */
    public ItemRequirement(int itemId, int amount, EquipmentInventorySlot equipmentSlot, Integer inventorySlot,
                        RequirementPriority priority, int rating, String description, TaskContext taskContext) {
        this(itemId, amount, equipmentSlot, inventorySlot, priority, rating, description, 
             taskContext, null, null, null, null, false);
    }

     @Override
    public String getName() {
        if (this.itemComposition == null) {
            // Lazy load item composition if not already set
            setItemComp(getId());
        }
        if(isFuzzy()){
            if(this.itemComposition != null){
                boolean isCharged = Rs2FuzzyItem.isChargedItem(getId());
                if(isCharged){
                    return Rs2FuzzyItem.getBaseItemNameFromString( this.itemComposition.getName());
                }
                return this.itemComposition.getName();
            }else{
                return "Unknown Item (Fuzzy)";
            }

        }
        // Use the single item ID as the name
        return this.itemComposition != null ? this.itemComposition.getName() : "Unknown Item";
    }
    private void setItemComp(int itemId) {
        this.itemComposition = Microbot.getClientThread().runOnClientThreadOptional(() -> 
            Microbot.getItemManager().getItemComposition(itemId))    .orElse(null);
    }

 /**
     * Checks if this is a dummy item requirement.
     * Dummy item requirements have ID of -1 and are used to block inventory/equipment slots.
     * Dummy items serve several purposes:
     * 1. They represent empty slots in containers
     * 2. They maintain consistent array sizes for comparison
     * 3. They can be used as placeholders for required "empty" slots
     * 4. They're automatically considered "fulfilled" in requirement checking
     * 
     * @return true if this is a dummy item requirement (itemId == -1)
     */
    public boolean isDummyItemRequirement() {
        return getId() == -1;
    }

    /**
     * Creates a dummy item requirement for blocking an equipment slot.
     * Dummy requirements have MANDATORY priority, rating 10, itemId -1, amount -1, and no skill requirements.
     * They are used to reserve slots without specifying actual items.
     * 
     * @param equipmentSlot The equipment slot to block
     * @param TaskContext When this requirement applies
     * @param description Description for the dummy requirement
     * @return A dummy ItemRequirement for the specified equipment slot
     */
    public static ItemRequirement createDummyEquipmentRequirement(
            EquipmentInventorySlot equipmentSlot,
            TaskContext taskContext,
            String description) {
        if (equipmentSlot == null) {
            throw new IllegalArgumentException("Equipment slot cannot be null for dummy equipment requirement");
        }
        
        return new ItemRequirement(
            -1,  // dummy item ID
            -1,  // dummy amount
            equipmentSlot,
            -2,  // equipment-only slot indicator
            RequirementPriority.MANDATORY,
            10,  // rating
            description,
            taskContext,
            null,  // skillToUse
            -1,    // minimumLevelToUse
            null,  // skillToEquip
            -1,    // minimumLevelToEquip
            false  // fuzzy
        );
    }
    
    /**
     * Creates a dummy item requirement for blocking an inventory slot.
     * Dummy requirements have MANDATORY priority, rating 10, itemId -1, amount -1, and no skill requirements.
     * They are used to reserve slots without specifying actual items.
     * 
     * @param inventorySlot The inventory slot to block (0-27)
     * @param TaskContext When this requirement applies
     * @param description Description for the dummy requirement
     * @return A dummy ItemRequirement for the specified inventory slot
     * @throws IllegalArgumentException if inventorySlot is not between 0 and 27
     */
    public static ItemRequirement createDummyInventoryRequirement(
            int inventorySlot,
            TaskContext taskContext,
            String description) {
        if (inventorySlot < 0 || inventorySlot > 27) {
            throw new IllegalArgumentException("Inventory slot must be between 0 and 27, got: " + inventorySlot);
        }
        
        return new ItemRequirement(
            -1,  // dummy item ID
            -1,  // dummy amount
            null,  // equipmentSlot
            inventorySlot,
            RequirementPriority.MANDATORY,
            10,  // rating
            description,
            taskContext,
            null,  // skillToUse
            -1,    // minimumLevelToUse
            null,  // skillToEquip
            -1,    // minimumLevelToEquip
            false  // fuzzy
        );
    }

    // ========== EXISTING FACTORY METHODS ==========
    
    /**
     * Factory method to create an OrRequirement when multiple item IDs are provided.
     * Use this instead of ItemRequirement constructors with List<Integer> when you have multiple alternative items.
     * 
     * @param itemIds List of alternative item IDs
     * @param amount Amount required for each item
     * @param equipmentSlot Equipment slot if this is equipment
     * @param inventorySlot Inventory slot if specific slot required  
     * @param priority Priority level
     * @param rating Effectiveness rating
     * @param description Description for the OR requirement
     * @param TaskContext When to fulfill this requirement
     * @param skillToUse Skill required to use the items
     * @param minimumLevelToUse Minimum level to use
     * @param skillToEquip Skill required to equip
     * @param minimumLevelToEquip Minimum level to equip
     * @param fuzzy Whether to prefer lower charge variants
     * @return OrRequirement containing individual ItemRequirements for each ID
     */
    public static OrRequirement createOrRequirement(
            List<Integer> itemIds,
            int amount,
            EquipmentInventorySlot equipmentSlot,
            Integer inventorySlot,
            RequirementPriority priority,
            int rating,
            String description,
            TaskContext taskContext,
            Skill skillToUse,
            Integer minimumLevelToUse,
            Skill skillToEquip,
            Integer minimumLevelToEquip,
            boolean fuzzy) {
        
        if (itemIds.size() <= 1) {
            throw new IllegalArgumentException("Use regular ItemRequirement constructor for single item ID");
        }
        
        // Create individual ItemRequirements for each ID
        ItemRequirement[] requirements = new ItemRequirement[itemIds.size()];
        for (int i = 0; i < itemIds.size(); i++) {
            int itemId = itemIds.get(i);
            String itemName = Microbot.getClientThread().runOnClientThreadOptional(() -> 
                Microbot.getItemManager().getItemComposition(itemId).getName()).orElse("Unknown Item");
            String itemDescription = description + " (" + itemName + ")";
            
            requirements[i] = new ItemRequirement(
                itemId, amount, equipmentSlot, inventorySlot,
                priority, rating, itemDescription, taskContext,
                skillToUse, minimumLevelToUse, skillToEquip, minimumLevelToEquip,
                fuzzy
            );
        }
        
        return new OrRequirement(priority, rating, description, taskContext, ItemRequirement.class,requirements);
    }
    
    /**
     * Factory method for equipment OR requirements with default parameters.
     */
    public static OrRequirement createOrRequirement(
            List<Integer> itemIds,
            EquipmentInventorySlot equipmentSlot,
            RequirementPriority priority,
            int rating,
            String description,
            TaskContext taskContext) {
        return createOrRequirement(itemIds, 1, equipmentSlot, -2, priority, rating, description, 
                                 taskContext, null, null, null, null, false);
    }
    /**
     * Factory method for inventory OR requirements with default parameters.
     */
    public static OrRequirement createOrRequirement(
            List<Integer> itemIds,
            int amount,            
            Integer inventorySlot,
            RequirementPriority priority,
            int rating,
            String description,
            TaskContext taskContext) {
        return createOrRequirement(itemIds, amount, null, inventorySlot, priority, rating, description,
                                 taskContext, null, null, null, null, false);
    }
    /**
     * Factory method for inventory OR requirements with default parameters.
     */
    public static OrRequirement createOrRequirement(
            List<Integer> itemIds,
            int amount,
            EquipmentInventorySlot equipmentSlot,
            Integer inventorySlot,
            RequirementPriority priority,
            int rating,
            String description,
            TaskContext taskContext) {
        return createOrRequirement(itemIds, amount, equipmentSlot, inventorySlot, priority, rating, description,
                                 taskContext, null, null, null, null, false);
    }

     
     /**
     * Factory method for inventory OR requirements with default parameters.
     */
    public static OrRequirement createOrRequirement(
            List<Integer> itemIds,
            int amount,
            EquipmentInventorySlot equipmentSlot,
            Integer inventorySlot,
            RequirementPriority priority,
            int rating,
            String description,
            TaskContext taskContext,
            Skill skillToUse,
            Integer minimumLevelToUse,
            Skill skillToEquip,
            Integer minimumLevelToEquip
            ) {
        return createOrRequirement(itemIds, amount, equipmentSlot, inventorySlot, priority, rating, description,
                                 taskContext, skillToUse, minimumLevelToUse, skillToEquip, minimumLevelToEquip, false);
    }
    
    /**
     * Creates a copy of this ItemRequirement with a specific inventory slot.
     * Useful for slot-specific placement during layout planning.
     * 
     * @param targetSlot The target inventory slot (0-27)
     * @return A new ItemRequirement with the specified slot
     */
    public ItemRequirement copyWithSpecificSlot(int targetSlot) {
        if (targetSlot < 0 || targetSlot > 27) {
            throw new IllegalArgumentException("Invalid inventory slot: " + targetSlot);
        }
        
        return new ItemRequirement(
            getId(),
            amount,
            equipmentSlot,
            targetSlot,
            priority,
            rating,
            description,
            taskContext,
            skillToUse,
            minimumLevelToUse,
            skillToEquip,
            minimumLevelToEquip,
            fuzzy
        );
    }
    
    /**
     * Creates a copy of this ItemRequirement with a different amount.
     * Useful for partial fulfillment scenarios.
     * 
     * @param newAmount The new amount for the requirement
     * @return A new ItemRequirement with the specified amount
     */
    public ItemRequirement copyWithAmount(int newAmount) {
        return new ItemRequirement(
            getId(),
            newAmount,
            equipmentSlot,
            inventorySlot,
            priority,
            rating,
            description,
            taskContext,
            skillToUse,
            minimumLevelToUse,
            skillToEquip,
            minimumLevelToEquip,
            fuzzy
        );
    }
    
    /**
	 * Retrieves the wiki URL for this item based on the URL suffix or item id.
	 *
	 * @return the wiki URL as a {@link String}, or {@code null} if not available
	 */
    // TODO when implented the  wikiscrapper 
	/**@Nullable 
	public String getWikiUrl()
	{
		if (getUrlSuffix() != null) {
			return "https://oldschool.runescape.wiki/w/" + getUrlSuffix();
		}

		if (getId() != -1) {
			return "https://oldschool.runescape.wiki/w/Special:Lookup?type=item&id=" + getId();
		}

		return null;
	}**/

    /**
     * Infers the RequirementType from the provided slot parameters.
     * 
     * @param equipmentSlot The equipment slot (can be null)
     * @param inventorySlot The inventory slot (-1 for any slot)
     * @return The inferred RequirementType
     */
    private static RequirementType inferRequirementType(EquipmentInventorySlot equipmentSlot, Integer inventorySlot) {
        boolean hasEquipmentSlot = equipmentSlot != null; // null indicates, we dont allow the item placed into any equipment slot,
        boolean hasInventorySlot = inventorySlot != -2 || inventorySlot==null; //-2 indicates we dont allow to be in inventory, -1 indicates any slot, 0-27 indicates specific slot
        
        if (hasEquipmentSlot && hasInventorySlot) {
            return RequirementType.EITHER;
        } else if (hasEquipmentSlot) {
            return RequirementType.EQUIPMENT;
        } else {
            return RequirementType.INVENTORY;
        }
    }
    
    /**
     * Checks if this item is available in either the player's inventory or bank.
     * Now properly checks the required amount.
     * 
     * @return true if the item is available with sufficient quantity, false otherwise
     */
    public boolean isAvailableInInventoryOrBank() {
        return getTotalAvailableQuantity() >= amount;
    }
    
    /**
     * Checks if this item is available in the player's inventory.
     * Now properly checks the required amount.
     * 
     * @return true if the item is in inventory with sufficient quantity, false otherwise
     */
    public boolean isAvailableInInventory() {
        return getInventoryQuantity() >= amount;
    }
    
    /**
     * Checks if this item is available in the player's bank.
     * Now properly checks the required amount.
     * 
     * @return true if the item is in bank with sufficient quantity, false otherwise
     */
    public boolean isAvailableInBank() {
        return getBankCount() >= amount;
    }
    
    public boolean canBeUsed() {
        // Check if the item is available in inventory or bank
        if (isAvailableInInventoryOrBank()) {
             
            // Check skill to use if specified
            if (skillToUse != null && minimumLevelToUse != null) {
                int currentLevel = Rs2Player.getRealSkillLevel(skillToUse);
                if (currentLevel < minimumLevelToUse) {
                    return false;
                }
            }
           
        }else{
            return false;
        }
        return true;
        
    }
    public boolean canBeEquipped() {
        // Check if the item is available in inventory or bank
        if (isAvailableInInventoryOrBank()) {
            // Check skill to equip if specified
            if (skillToEquip != null && minimumLevelToEquip != null) {
                int currentLevel = Rs2Player.getRealSkillLevel(skillToEquip);
                if (currentLevel < minimumLevelToEquip) {
                    return false;
                }
            }
           
        }else{
            return false;
        }
        return isEquipment();
        
    }
    /**
     * Checks if the player meets the skill requirements to use this item.
     * 
     * @return true if the player meets skill requirements, false otherwise
     */
    public boolean meetsSkillRequirements() {
        // Check skill to equip if specified
        if (skillToEquip != null && minimumLevelToEquip != null) {
            int currentLevel = Rs2Player.getRealSkillLevel(skillToEquip);
            if (currentLevel < minimumLevelToEquip) {
                return false;
            }
        }
        
        // Check skill to use if specified
        if (skillToUse != null && minimumLevelToUse != null) {
            int currentLevel = Rs2Player.getRealSkillLevel(skillToUse);
            if (currentLevel < minimumLevelToUse) {
                return false;
            }
        }
        
        return true;
    }
    // TODO implement meets requirements --- has the item availble in inventory or bank, and has the required skill level to use it, and for equipment items, has the required skill level to equip it
    

    /**
     * Attempts to equip this item from inventory or bank with improved logic.
     * Handles charged items and proper equipment verification.
     * 
     * @return true if successfully equipped, false otherwise
     */
    private boolean equip() {
        if (Microbot.getClient().isClientThread()) {
            log.error("Please run equip() on a non-client thread.");
            return false;
        }
        
        if (equipmentSlot == null) {
            return false; // Not an equipment item
        }
        
        if (!meetsSkillRequirements()) {
            log.error("Skill requirements not met for " + getDescription());
            return false;
        }
        
        // Check if already equipped
        if (hasRequiredItemEquipped()) {
            return true;
        }
        
        // Try to equip from inventory first
        if (tryEquipFromInventory()) {
            return true;
        }
        
        // Try to withdraw from bank and equip
        return tryEquipFromBank();
    }
    
    /**
     * Helper method to try equipping from inventory.
     */
    private boolean tryEquipFromInventory() {
        int itemId = getId();
        return tryEquipSingleItem(itemId);
    }
    
    /**
     * Helper method to try equipping from bank.
     */
    private boolean tryEquipFromBank() {
        // Ensure bank is open
        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.openBank()) {
                return false;
            }
            sleepUntil(()->Rs2Bank.isOpen(), 3000);
            if(!Rs2Bank.isOpen()) {
                log.error("Failed to open bank for equipping item: " + getDescription());
                return false;
            }
        }
        
        int itemId = getId();
        return tryEquipFromBankSingle(itemId);
    }
    
    /**
     * Helper method to try equipping a single item from inventory.
     */
    private boolean tryEquipSingleItem(Integer itemId) {
        if (Rs2Inventory.hasItem(itemId)) {
            Rs2ItemModel item = Rs2Inventory.get(itemId);
            if (item != null) {
                // Check for common equip actions: "wear", "wield", "equip"
                List<String> actionList = new ArrayList<>(Arrays.asList(item.getInventoryActions()));
                for (String action : actionList) {
                    if (action != null && (action.equalsIgnoreCase("wear") || 
                                         action.equalsIgnoreCase("wield") || 
                                         action.equalsIgnoreCase("equip"))) {
                        Rs2Inventory.interact(item.getSlot(), action);
                        if (sleepUntil(() -> Rs2Equipment.isWearing(itemId), 1800)) {
                            return true;
                        }
                        break;
                    }
                }
            }
        }
        return false;
    }
    
    /**
     * Helper method to try equipping a single item from bank.
     */
    private boolean tryEquipFromBankSingle(Integer itemId) {
        if (Rs2Bank.hasItem(itemId)) {
            Rs2Bank.withdrawAndEquip(itemId);
            return sleepUntil(() -> Rs2Equipment.isWearing(itemId), 3000);
        }
        return false;
    }
    
    /**
     * Attempts to withdraw this item from the bank.
     * 
     * @return true if successfully withdrawn, false otherwise
     */
    /**
     * Attempts to withdraw this item from the bank with improved logic.
     * Handles charged items and proper quantity calculation.
     * 
     * @return true if successfully withdrawn, false otherwise
     */
    private boolean withdrawFromBank(CompletableFuture<Boolean> scheduledFuture) {
        if (Microbot.getClient().isClientThread()) {
            log.error("Please run withdrawFromBank() on a non-client thread.");
            return false;
        }
        
        if (!meetsSkillRequirements()) {
            log.error("Skill requirements not met for " + getDescription());
            return false;
        }
        
        // Check if already have enough in inventory
        if (hasRequiredItemInInventory()) {
            return true;
        }
        
        // Ensure we're near and have bank open
        if (!Rs2Bank.isNearBank(6)) {
            log.error("Not near a bank, cannot withdraw item: " + getDescription());
            Rs2Bank.walkToBank();
        }
        
        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.openBank()) {
                return false;
            }
            sleepUntil(()->Rs2Bank.isOpen(), 3000);
        }
        
        // Handle charged items with preference logic - since we now only have one item, check if it's charged
        int itemId = getId();
        return tryWithdrawItem(itemId, scheduledFuture);
    }
    
    
    /**
     * Helper method to attempt withdrawing a specific item.
     */
    private boolean tryWithdrawItem(Integer itemId, CompletableFuture<Boolean> scheduledFuture) {
      
        
        if (Rs2Bank.hasItem(itemId)) {
            int quantity = getOptimalQuantity(itemId);
            if (quantity > 0) {
                Rs2Bank.withdrawX(itemId, quantity);
            } else if (quantity < 0) {
                Rs2Bank.depositX(itemId, Math.abs(quantity));
            }
            
            return sleepUntil(() -> isAvailableInInventory(), 3000);
        }
        return false;
    }
    
  
    
    /**
     * Checks if this requirement specifies a particular inventory slot.
     * 
     * @return true if a specific inventory slot is specified (0-27), false if any slot (-1)
     */
    public boolean hasSpecificInventorySlot() {
        return inventorySlot >= 0 && inventorySlot <= 27;
    }
    
    /**
     * Checks if this requirement allows any inventory slot.
     * 
     * @return true if any slot is allowed (-1), false if a specific slot is required
     */
    public boolean allowsAnyInventorySlot() {
        return inventorySlot == -1;
    }
    
    /**
     * Checks if this item requirement can be fulfilled by placing the item in the given inventory slot.
     * 
     * @param slot The inventory slot to check (0-27)
     * @return true if the item can be placed in this slot, false otherwise
     */
    public boolean canBePlacedInInventorySlot(int slot) {
        if (slot < 0 || slot > 27) {
            return false;
        }
        
        // If requirement type is EQUIPMENT only, it cannot be placed in inventory
        if (requirementType == RequirementType.EQUIPMENT) {
            return false;
        }
        
        // If specific slot required, check if it matches
        if (hasSpecificInventorySlot()) {
            return inventorySlot == slot;
        }
        
        // Otherwise, any slot is fine
        return true;
    }
    
    /**
     * Gets the total count of this item across inventory, equipment, and bank.
     * Properly handles fuzzy matching for charged items and item variations.
     * 
     * @return the total available count
     */
    public int getTotalAvailableQuantity() {
        if (fuzzy) {
            // For fuzzy matching, check all variations of the item
            return getFuzzyInventoryQuantity() + getFuzzyBankCount() + getFuzzyEquippedCount();
        } else {
            // Exact item ID matching
            int itemId = getId();
            return Rs2Inventory.itemQuantity(itemId) + Rs2Bank.count(getUnNotedId()) + getEquippedCount();
        }
    }
    public int getTotalAvailableCount() {
        if (fuzzy) {
            // For fuzzy matching, check all variations of the item
            return getFuzzyInventoryCount() + getFuzzyBankCount() + getFuzzyEquippedCount();
        } else {
            // Exact item ID matching
            int itemId = getId();
            return Rs2Inventory.count(itemId) + Rs2Bank.count(getBankCount()) + getEquippedCount();
        }
    }
    
    
    /**
     * Gets the count of this item currently in inventory.
     * Properly handles fuzzy matching for charged items and item variations.
     * 
     * @return the inventory count
     */
    public int getInventoryQuantity() {
        if (fuzzy) {
            return getFuzzyInventoryQuantity();
        } else {
            return Rs2Inventory.itemQuantity(getId());
        }
    }
    
    /**
     * Gets the count of this item currently in bank.
     * Properly handles fuzzy matching for charged items and item variations.
     * 
     * @return the bank count
     */
    public int getBankCount() {
        if (fuzzy) {
            return getFuzzyBankCount();
        } else {
            return Rs2Bank.count(getUnNotedId());
        }
    }
    
    /**
     * Gets the fuzzy count of this item in inventory (includes all variations).
     * Uses Rs2FuzzyItem for comprehensive fuzzy matching including ID-based variations
     * and name-based charged item variants.
     * 
     * @return the fuzzy inventory count
     */
    private int getFuzzyInventoryCount() {
        return Rs2FuzzyItem.getFuzzyInventoryCount(getId(), true);
    }
    private int getFuzzyInventoryQuantity() {
        return Rs2FuzzyItem.getFuzzyInventoryQuantity(getId(), true);
    }
    
    
    /**
     * Gets the fuzzy count of this item in bank (includes all variations).
     * Uses Rs2FuzzyItem for comprehensive fuzzy matching including ID-based variations
     * and name-based charged item variants.
     * 
     * @return the fuzzy bank count
     */
    private int getFuzzyBankCount() {
        return Rs2FuzzyItem.getFuzzyBankCount(getId(), true);
    }
    
    /**
     * Gets the fuzzy count of this item currently equipped (includes all variations).
     * Uses Rs2FuzzyItem for comprehensive fuzzy matching including ID-based variations
     * and name-based charged item variants.
     * 
     * @return the fuzzy equipped count (0 or 1 for most items)
     */
    private int getFuzzyEquippedCount() {
        if (equipmentSlot != null) {
            return Rs2FuzzyItem.getFuzzyEquippedCount(getId(), true);
        }
        return 0;
    }
    
    /**
     * Gets the count of this item currently equipped.
     * 
     * @return the equipped count (0 or 1 for most items)
     */
    private int getEquippedCount() {
        if (equipmentSlot != null) {
            return Rs2Equipment.isWearing(getId()) ? 1 : 0;
        }
        return 0;
    }
    
    /**
     * Gets the optimal quantity to withdraw/deposit for this item.
     * Considers current inventory amount and desired amount.
     * 
     * @param itemId The specific item ID to calculate for
     * @return Positive for withdraw, negative for deposit, 0 for no action needed
     */
    private int getOptimalQuantity(Integer itemId) {
        int currentAmount = Rs2Inventory.count(itemId);
        int targetAmount = this.amount > 0 ? this.amount : 1;
        
        return targetAmount - currentAmount;
    }

    /**
     * Gets the primary item ID for this requirement.
     * This is usually the first ID in the list, which typically represents the best option.
     * 
    /**
     * Gets the resolved item ID for this requirement.
     * This returns the auto-resolved ID (potentially noted variant) that should be used for all operations.
     * 
     * @return the resolved item ID for this requirement
     */
    public int getId() {
        if (ids.isEmpty()) {
            throw new IllegalStateException("ItemRequirement must have exactly one item ID");
        }
        if (ids.size() > 1) {
            throw new IllegalStateException("ItemRequirement has multiple IDs, use createOrRequirement() factory method instead");
        }
        return ids.get(0);
    }

   
    /**
     * Checks if the player meets the skill requirements to equip a specific item.
     * Validates both the minimum level and required skill for equipping.
     * 
     * @param item The ItemRequirement to check skill requirements for
     * @return true if player can equip the item, false if skill requirements aren't met
     */
    public static boolean canPlayerEquipItem(ItemRequirement item) {
        // Check if item has skill requirements for equipping
        if (item.getSkillToEquip() != null && item.getMinimumLevelToEquip() > 0) {
            int playerLevel = Rs2Player.getRealSkillLevel(item.getSkillToEquip());
            
            if (playerLevel < item.getMinimumLevelToEquip()) {
                log.debug("Player " + item.getSkillToEquip().getName() + " level (" + playerLevel + 
                           ") insufficient to equip item requiring level " + item.getMinimumLevelToEquip());
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Checks if the player meets the skill requirements to use a specific item.
     * Validates both the minimum level and required skill for using.
     * 
     * @param item The ItemRequirement to check skill requirements for
     * @return true if player can use the item, false if skill requirements aren't met
     */
    public static boolean canPlayerUseItem(ItemRequirement item) {
        // Check if item has skill requirements for using
        if (item.getSkillToUse() != null && item.getMinimumLevelToUse() > 0) {
            int playerLevel = Rs2Player.getRealSkillLevel(item.getSkillToUse());
            
            if (playerLevel < item.getMinimumLevelToUse()) {
                log.debug("Player " + item.getSkillToUse().getName() + " level (" + playerLevel + 
                           ") insufficient to use item requiring level " + item.getMinimumLevelToUse());
                return false;
            }
        }
        
        return true;
    }
    
    /**
     * Returns a multi-line display string with detailed item requirement information.
     * Uses StringBuilder with tabs for proper formatting.
     * 
     * @return A formatted string containing item requirement details
     */
    public String displayString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== Item Requirement Details ===\n");
        sb.append("  -Name:\t\t\t").append(getName()).append("\n");
        sb.append("  -Type:\t\t\t").append(getRequirementType().name()).append("\n");
        sb.append("  -Priority:\t\t").append(getPriority().name()).append("\n");
        sb.append("  -Rating:\t\t\t").append(getRating()).append("/10\n");
        sb.append("  -Description:\t").append(getDescription()).append("\n");
        sb.append("  -Schedule Context:\t").append(getTaskContext().name()).append("\n");
        sb.append("  -Item ID:\t\t").append(getId()).append("\n");
        sb.append("  -Item unnoted ID:\t\t").append(getUnNotedId()).append("\n");
        sb.append("  -ItemModel unnoted ID:\t\t").append(Rs2ItemModel.getUnNotedId(getId())).append("\n");
        sb.append("  -Item noted ID:\t\t").append(getNotedId()).append("\n");
        sb.append("  -ItemModel noted ID:\t\t").append(Rs2ItemModel.getNotedId(getId())).append("\n");
        sb.append("  -Item linked ID:\t").append( getUnNotedId()).append("\n");
        sb.append("  -Amount:\t\t\t").append(amount).append("\n");
        
        if (equipmentSlot != null) {
            sb.append("  -Equipment Slot:\t").append(equipmentSlot.name()).append("\n");
        }
        
        if (inventorySlot != null && inventorySlot >= 0) {
            sb.append("  -Inventory Slot:\t").append(inventorySlot).append("\n");
        }
        
        if (skillToUse != null) {
            sb.append("  -Skill to Use:\t").append(skillToUse.getName()).append("\n");
            sb.append("  -Min Level to Use:\t").append(minimumLevelToUse != null ? minimumLevelToUse : "N/A").append("\n");
        }
        
        if (skillToEquip != null) sb.append("  -Skill to Equip:\t").append(skillToEquip.getName()).append("\n");
        if (minimumLevelToEquip != null) sb.append("  -Min Level to Equip:\t").append(minimumLevelToEquip).append("\n");
        
        sb.append("  -Fuzzy Charge:\t").append(fuzzy).append("\n");
        sb.append("  -Is Available in Inventory:\t").append(isAvailableInInventory()).append("\n");
        sb.append("  -Is Available in Bank:\t").append(isAvailableInBank()).append("\n");
        sb.append("  -Total Available Count:\t").append(getTotalAvailableCount()).append("\n");
        sb.append("      - Banked: ").append(getBankCount()).append(" Inventory:").append(getFuzzyInventoryCount()).append("\n");
        sb.append("  -Total Available Quantity:\t").append(getTotalAvailableQuantity()).append("\n");
        sb.append("      - Banked: ").append(getBankCount()).append(" Inventory:").append(getInventoryQuantity()).append("\n");
        sb.append("  -Can be Used:\t\t").append(canBeUsed()).append("\n");
        sb.append("  -Can be Equipped:\t").append(canBeEquipped()).append("\n");
        sb.append("  -Meets Skill Req.:\t").append(meetsSkillRequirements()).append("\n");
        sb.append("  -Is Dummy Item:\t\t").append(isDummyItemRequirement()).append("\n");
        
        return sb.toString();
    }



    /**
     * Enhanced toString method that uses displayString for comprehensive output.
     * 
     * @return A comprehensive string representation of this item requirement
     */
    @Override
    public String toString() {
        return displayString();
    }
    
    /**
     * Implements the abstract fulfillRequirement method from the base Requirement class.
     * Attempts to fulfill this item requirement by checking availability and managing inventory/equipment.
     * 
     * @param executorService The ScheduledExecutorService on which fulfillment is running
     * @return true if the requirement was successfully fulfilled, false otherwise
     */
    @Override
    public boolean fulfillRequirement(CompletableFuture<Boolean> scheduledFuture) {
        try {
            if (Microbot.getClient().isClientThread()) {
                log.error("Please run fulfillRequirement() on a non-client thread.");
                return false;
            }
             // Dummy items are always considered "fulfilled" since they're just slot placeholders
            if (isDummyItemRequirement()) {
                log.debug("Dummy item requirement automatically fulfilled: {}", getDescription());
                return true;
            }
            // Check if the requirement is already fulfilled
            if (isRequirementAlreadyFulfilled()) {
                return true;
            }
            
            // Check if the item is available in inventory or bank
            if (!isAvailableInInventoryOrBank()) {
                if (isMandatory()) {
                    log.error("MANDATORY item requirement cannot be fulfilled: " + getName() + " - Item not available");
                    return false;
                } else {
                    log.error("OPTIONAL/RECOMMENDED item requirement skipped: " + getName() + " - Item not available");
                    return true; // Non-mandatory requirements return true if item isn't available
                }
            }
            
            // Handle equipment requirements
            if (requirementType == RequirementType.EQUIPMENT || requirementType == RequirementType.EITHER) {
                if (!fulfillEquipmentRequirement(scheduledFuture)) {
                    return !isMandatory(); // Return false only for mandatory requirements
                }
            }
            
            // Handle inventory requirements
            if (requirementType == RequirementType.INVENTORY || requirementType == RequirementType.EITHER) {
                if (!fulfillInventoryRequirement(scheduledFuture)) {
                    return !isMandatory(); // Return false only for mandatory requirements
                }
            }
            
            return true;
            
        } catch (Exception e) {
            log.error("Error fulfilling item requirement " + getName() + ": " + e.getMessage());
            return !isMandatory(); // Don't fail mandatory requirements due to exceptions
        }
    }
    
    /**
     * Checks if this requirement is currently fulfilled without attempting to fulfill it.
     * This is more efficient than fulfillRequirement() for status checking.
     * 
     * @return true if the requirement is already met, false otherwise
     */
    @Override
    public boolean isFulfilled() {
        return isRequirementAlreadyFulfilled();
    }
    
    /**
     * Checks if this requirement is already fulfilled based on the requirement type.
     * 
     * @return true if the requirement is already met, false otherwise
     */
    private boolean isRequirementAlreadyFulfilled() {
        switch (requirementType) {
            case EQUIPMENT:
                return hasRequiredItemEquipped();
            case INVENTORY:
                return hasRequiredItemInInventory();
            case EITHER:
                return hasRequiredItemEquipped() || hasRequiredItemInInventory();
            default:
                return false;
        }
    }
    
    /**
     * Attempts to fulfill an equipment requirement by equipping the required item.
     * 
     * @param scheduledFuture The CompletableFuture to monitor for cancellation
     * @return true if the equipment requirement was fulfilled, false otherwise
     */
    private boolean fulfillEquipmentRequirement(CompletableFuture<Boolean> scheduledFuture) {
      
        return equip();
    }
    
    /**
     * Attempts to fulfill an inventory requirement by ensuring the required item is in inventory.
     * Handles specific slot requirements and proper quantity management.
     * 
     * @param scheduledFuture The CompletableFuture to monitor for cancellation
     * @return true if the inventory requirement was fulfilled, false otherwise
     */
    private boolean fulfillInventoryRequirement(CompletableFuture<Boolean> scheduledFuture) {
       
        if (hasSpecificInventorySlot()) {
            return withdrawAndPlaceInSpecificSlot(scheduledFuture);
        } else {
            return withdrawFromBank(scheduledFuture);
        }
    }
    
    /**
     * Withdraws the item and places it in the specific inventory slot if required.
     * Creates a proper copy of the requirement with the target slot.
     * 
     * @param scheduledFuture The CompletableFuture to monitor for cancellation
     * @return true if successfully placed in the specific slot, false otherwise
     */
    private boolean withdrawAndPlaceInSpecificSlot(CompletableFuture<Boolean> scheduledFuture) {
        if (Microbot.getClient().isClientThread()) {
            log.error("Please run withdrawAndPlaceInSpecificSlot() on a non-client thread.");
            return false;
        }
        
        // Check if already have the item in the correct slot
        if (hasItemInSpecificSlot(inventorySlot)) {
            return true;
        }
        
        // Ensure we have the item available
        if (!isAvailableInInventoryOrBank()) {
            return false;
        }
        
        // Handle case where item is already in inventory but wrong slot
        if (isAvailableInInventory()) {
            return moveToSpecificSlot();
        }
        
        // Withdraw from bank to specific slot
        return withdrawFromBankToSpecificSlot();
    }
    
    /**
     * Checks if the required item is in the specific inventory slot with correct amount.
     * 
     * @param slot The inventory slot to check
     * @return true if the item is in the slot with sufficient quantity
     */
    private boolean hasItemInSpecificSlot(int slot) {
        Rs2ItemModel item = Rs2Inventory.get(slot);
        if (item == null) {
            return false;
        }
        
        for (Integer itemId : ids) {
            if (item.getId() == itemId && item.getQuantity() >= amount) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * Moves the item from its current inventory position to the specific slot.
     * 
     * @return true if successfully moved, false otherwise
     */
    private boolean moveToSpecificSlot() {
        // Find the item in inventory
        for (Integer itemId : ids) {
            Rs2ItemModel item = Rs2Inventory.get(itemId);
            if (item != null && item.getQuantity() >= amount) {
                // Use the available moveItemToSlot method
                return Rs2Inventory.moveItemToSlot(item, inventorySlot);
            }
        }
        return false;
    }
    
    /**
     * Withdraws the item from bank directly to the specific inventory slot.
     * Since withdrawToSlot doesn't exist, we'll withdraw and then move.
     * 
     * @return true if successfully withdrawn to slot, false otherwise
     */
    private boolean withdrawFromBankToSpecificSlot() {
        if (!Rs2Bank.isOpen()) {
            if (!Rs2Bank.openBank()) {
                return false;
            }
            sleepUntil(() -> Rs2Bank.isOpen(), 3000);
        }
        
        // Find best available item in bank
        int itemId = getUnNotedId();
        if (Rs2Bank.count(itemId) >= amount) {
            // Clear target slot if needed by depositing the item there
            Rs2ItemModel targetSlotItem = Rs2Inventory.get(inventorySlot);
            if (targetSlotItem != null) {
                Rs2Bank.depositOne(targetSlotItem.getId());
                sleepUntil(() -> Rs2Inventory.get(inventorySlot) == null, 2000);
            }
            
            // Withdraw the item (it will go to any available slot)
            Rs2Bank.withdrawX(itemId, amount);
            sleepUntil(() -> Rs2Inventory.itemQuantity(itemId) >= amount, 3000);
            
            // Now move to the specific slot
            Rs2ItemModel withdrawnItem = Rs2Inventory.get(itemId);
            if (withdrawnItem != null) {
                return Rs2Inventory.moveItemToSlot(withdrawnItem, inventorySlot);
            }
        }
    
        return false;
    }
    
    /**
     * Checks if the required item is equipped in the correct slot (if specified).
     * 
     * @return true if the required item is equipped, false otherwise
     */
    private boolean hasRequiredItemEquipped() {
        int itemId = getId();
        if (equipmentSlot != null) {
            return Rs2Equipment.isWearing(itemId);
        } else {
            return Rs2Equipment.isWearing(itemId);
        }
    }
    
    /**
     * Checks if the required item is in inventory with the correct amount.
     * 
     * @return true if the required item is in inventory with sufficient quantity, false otherwise
     */
    private boolean hasRequiredItemInInventory() {
        return Rs2Inventory.count(getId()) >= amount;
    }
    
    /**
     * Validates slot assignments to ensure consistency between requirement type and slot specifications.
     * Throws IllegalArgumentException if the configuration is invalid.
     */
    private void validateSlotAssignments() {
        // Validate inventory slot range
        if (inventorySlot < -2 || inventorySlot > 27) {
            throw new IllegalArgumentException("Inventory slot must be between -1 (any slot) and 27, got: " + inventorySlot);
        }
        
        // Validate requirement type and slot consistency
        switch (requirementType) {
            case EQUIPMENT:
                if (equipmentSlot == null || inventorySlot!= -2) {
                    throw new IllegalArgumentException("EQUIPMENT requirement must specify an equipment slot");
                }
                break;
            case INVENTORY:
                if (equipmentSlot != null) {
                    throw new IllegalArgumentException("INVENTORY requirement should not specify an equipment slot");
                }
                if( inventorySlot != -1) {
                    if(!isStackable() && amount > 1) {
                        throw new IllegalArgumentException("INVENTORY requirement with non-stackable items must have inventory slot -1 (any slot) or amount 1. Item ID: " + getId() + ", Amount: " + amount);
                    }
                }
                break;
            case EITHER:
                if ( !(equipmentSlot != null || inventorySlot>=-1) ) {
                    throw new IllegalArgumentException("EITHER requirement must specify at least one of equipment or inventory slot");
                }
                // EITHER requirements can have equipment slot specified (preferred) and optional inventory slot
                break;
            default:
                throw new IllegalArgumentException("Unsupported requirement type: " + requirementType);
        }
    }
    
    /**
     * Resolves the optimal item ID for the given amount, automatically detecting when noted variants should be used.
     * This method is called during construction to auto-resolve noted items for stackable requirements.
     * 
     * @param originalItemId The original item ID provided to the constructor
     * @param amount The amount required
     * @return The optimal item ID (potentially noted variant) to use
     */
    private static int resolveOptimalItemId(int originalItemId, int amount) {
        if (amount <= 1) {
            return originalItemId; // Single items don't need noting
        }
        
        try {
            ItemComposition composition = Microbot.getClientThread().runOnClientThreadOptional(() -> 
                Microbot.getItemManager().getItemComposition(originalItemId)
            ).orElse(null);
            
            if (composition == null) {
                log.warn("Could not get item composition for item ID: {}, using original ID", originalItemId);
                return originalItemId;
            }
            
            if (composition.isStackable()) {
                return originalItemId; // Already stackable, no need to change
            }
            
            // Check if this item has a noted variant
            int linkedNoteId = composition.getLinkedNoteId();
            if (linkedNoteId != originalItemId) {
                ItemComposition notedComposition = Microbot.getClientThread().runOnClientThreadOptional(() -> 
                    Microbot.getItemManager().getItemComposition(linkedNoteId)
                ).orElse(null);
                
                if (notedComposition != null && notedComposition.isStackable()) {
                    log.debug("Auto-resolved {} (ID: {}) to noted variant {} (ID: {}) for amount {}", 
                            composition.getName(), originalItemId, 
                            notedComposition.getName(), linkedNoteId, amount);
                    return linkedNoteId; // Use noted version
                }
            }
            
            // If we reach here, item is not stackable and has no noted variant
            log.debug("Item {} (ID: {}) with amount {} has no stackable noted variant, keeping original", 
                    composition.getName(), originalItemId, amount);
            return originalItemId;
            
        } catch (Exception e) {
            log.error("Error resolving optimal item ID for {} with amount {}: {}", 
                    originalItemId, amount, e.getMessage());
            return originalItemId; // Fall back to original on error
        }
    }
    
    private  static int getNotedItemId(ItemComposition composition) {
        try {
            
            if (composition == null) {                
                return -1;
            }
            
            int itemId = composition.getId();            
            // If already stackable, return original ID
            if (composition.isStackable()) {
                return itemId;
            }            
            // Check if this item has a noted variant
            int linkedNoteId = composition.getLinkedNoteId();
            ItemComposition linkedComposition = Microbot.getClientThread().runOnClientThreadOptional(() -> 
                   Microbot.getItemManager().getItemComposition(linkedNoteId)
                ).orElse(null);

            if (linkedComposition.isStackable() && !composition.isStackable()) {
                log.debug("Found noted variant for {} (ID: {}) -> {} (ID: {})", 
                        composition.getName(), itemId, 
                        linkedComposition.getName(), linkedNoteId);
                return linkedNoteId; // Use noted version                
              
            }
            if (linkedComposition.isStackable() && composition.isStackable())            {
                log.debug("Item {} (ID: {}) has only a stackable variant", 
                        composition.getName(), itemId, 
                        linkedComposition.getName(), linkedNoteId);
                return linkedNoteId < itemId ? linkedNoteId : itemId; // Use noted version if it has lower ID
            }
            // If we reach here, item is not stackable and has no noted variant
            log.debug("Item {} (ID: {}) has no stackable noted variant", 
                    composition.getName(), itemId);
            return itemId;
            
        } catch (Exception e) {
            //log.error("Error getting noted item ID for {}: {}", itemId, e.getMessage());
            return -1; // Fall back to original on error
        }
    }
    private  static int getUnNotedId(ItemComposition composition) {
        try {
            
              if (composition == null) {          
                log.warn("Could not get item composition for item ID, returning original ID");      
                return -1;
            }
            int itemId = composition.getId();                   
            // Check if this item has a noted variant
            int linkedNoteId = composition.getLinkedNoteId();
            if (linkedNoteId == -1){
                log.debug("Item {} (ID: {}) has no noted variant, returning original ID", 
                        composition.getName(), itemId);
                return itemId; // No noted variant, return original ID
            }
            ItemComposition linkedComposition = Microbot.getClientThread().runOnClientThreadOptional(() -> 
                    Microbot.getItemManager().getItemComposition(linkedNoteId)
                ).orElse(null);

            if (!linkedComposition.isStackable() && composition.isStackable()) {
                log.debug("Found unnoted variant for {} (ID: {}) -> {} (ID: {})", 
                        composition.getName(), itemId, 
                        linkedComposition.getName(), linkedNoteId);
                return linkedNoteId; // Use noted version                
              
            }
            if (linkedComposition.isStackable() && composition.isStackable())            {
                log.debug("Item {} (ID: {}) has only a stackable variant", 
                        composition.getName(), itemId, 
                        linkedComposition.getName(), linkedNoteId);
                
                return linkedNoteId < itemId ? linkedNoteId : itemId; // Use noted version if it has lower ID
            }
            // If we reach here, item is not stackable and has no noted variant
            log.debug("Item {} (ID: {}) has no non stackable variant", 
                    composition.getName(), itemId);
            return itemId;
            
        } catch (Exception e) {      
            log.error("Error getting unnoted item ID:{}", e.getMessage());      
            return -1; // Fall back to original on error
        }
    }
    private  static int getLinkedItemId(ItemComposition composition) {
        try {
            if (composition == null) {
                log.warn("no item composition for item ID, returning -1");
                return -1;
            }
            
        
            // Check if this item has a noted variant
            return  composition.getLinkedNoteId();
            
            
        } catch (Exception e) {
            log.error("Error getting linked item ID: {}",  e.getMessage());
            return -1; // Fall back to original on error
        }
    }
   
    
    /**
     * Checks if this item is stackable using the resolved item ID.
     * 
     * @return true if this item is stackable, false otherwise
     */
    public boolean isStackable() {
        int itemId = getId();
        
        try {
            if (itemComposition == null) {
                this.setItemComp(itemId);
            }
            return  itemComposition !=null ? itemComposition.isStackable(): false;
        } catch (Exception e) {
            log.error("Error checking if item " + itemId + " is stackable: " + e.getMessage());
            return false;
        }
    }

    /**
     * Gets the noted variant of an item ID if it exists and is stackable.
     * Returns the original ID if the item is already stackable or has no noted variant.
     * Uses lazy loading pattern for ItemComposition.
     * 
     * @param itemId The original item ID
     * @return The noted item ID if available and stackable, otherwise the original item ID
     */
    public static int getNotedId(int itemId) {      
            ItemComposition composition = Microbot.getClientThread().runOnClientThreadOptional(() -> 
                 Microbot.getItemManager().getItemComposition(itemId)
            ).orElse(null);            
            return getNotedItemId(composition);            
    }
    public boolean isNoted() {
         try {
            if (itemComposition == null) {
                this.setItemComp(getId());
            }
            return itemComposition.getNote() == 799 && itemComposition.isStackable() && itemComposition.getLinkedNoteId() != -1;
        } catch (Exception e) {
            log.error("Error getting noted ID for item " + getId() + ": " + e.getMessage());
            return false;
        }
    }
    public int getNotedId(){
        int itemId = getId();
        try {
            if (itemComposition == null) {
                this.setItemComp(itemId);
            }
            return getNotedItemId( itemComposition);
        } catch (Exception e) {
            log.error("Error getting noted ID for item " + itemId + ": " + e.getMessage());
            return itemId; // Fallback to original ID on error
        }
    }
     public int getUnNotedId(){
        int itemId = getId();
        try {
            if (itemComposition == null) {
                this.setItemComp(itemId);
            }
            return getUnNotedId(itemComposition);
        } catch (Exception e) {
            log.error("Error getting noted ID for item " + itemId + ": " + e.getMessage());
            return itemId; // Fallback to original ID on error
        }
    }
    public int getLinkedId(){
        int itemId = getId();
        try {
            if (itemComposition == null) {
                this.setItemComp(itemId);
            }
            return getLinkedItemId(itemComposition);
        } catch (Exception e) {
            log.error("Error getting linked ID for item " + itemId + ": " + e.getMessage());
            return itemId; // Fallback to original ID on error
        }
    }
    public boolean isEquipment() {
        int itemId = getId();
        try {
            if (itemComposition == null) {
                this.setItemComp(itemId);
            }

            final ItemStats itemStats =  Microbot.getClientThread().runOnClientThreadOptional(() -> 
                Microbot.getItemManager().getItemStats(itemId)
                ).orElse(null);

            if (itemStats == null || !itemStats.isEquipable()) {
                return false;
            }
            final ItemEquipmentStats equipmentStats = itemStats.getEquipment();
            if (equipmentStats == null) {
                return false;
            }        
            return true;

        } catch (Exception e) {
            log.error("Error checking if item " + itemId + " is equipped: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Gets the number of inventory slots this requirement would occupy.
     * For stackable items with any amount, this is 1 slot.
     * For non-stackable items, this equals the amount required.
     * 
     * @return the number of inventory slots needed
     */
    public int getRequiredInventorySlots() {
        if (requirementType == RequirementType.EQUIPMENT) {
            return 0; // Equipment items don't occupy inventory slots when equipped
        }
        
        if (isStackable()) {
            return 1; // Stackable items only need one slot regardless of amount
        } else {
            return amount; // Non-stackable items need one slot per item
        }
    }
    
    /**
     * Checks if this item requirement can be placed in inventory considering stackability and amount.
     * Non-stackable items with amount > 1 need multiple slots.
     * 
     * @return true if the item can be placed in inventory, false if not enough slots
     */
    public boolean canFitInInventory() {
        return canFitInInventory(Rs2Inventory.emptySlotCount());
    }
    
    /**
     * Checks if this item requirement can fit in the given number of available slots.
     * 
     * @param availableSlots The number of available inventory slots
     * @return true if the item can fit, false otherwise
     */
    public boolean canFitInInventory(int availableSlots) {
        return getRequiredInventorySlots() <= availableSlots;
    }
    
    /**
     * Checks if this requirement meets all conditions (availability, skill requirements, etc.).
     * 
     * @return true if all requirements are met, false otherwise
     */
    public boolean meetsAllRequirements() {
        return isAvailableInInventoryOrBank() && meetsSkillRequirements();
    }
    
    
    // ========== STATIC UTILITY METHODS ==========
    
    /**
     * Checks if an item is present in a specific inventory slot.
     * 
     * @param slot The inventory slot to check (0-27)
     * @param item The ItemRequirement to check for
     * @return true if the item is in the specified slot
     */
    public static boolean hasItemInSpecificSlot(int slot, ItemRequirement item) {
        if (slot < 0 || slot > 27) {
            log.warn("Invalid inventory slot: {}", slot);
            return false;
        }
        
        Rs2ItemModel slotItem = Rs2Inventory.getItemInSlot(slot);
        if (slotItem == null) {
            return false;
        }
        
        // Check if the slot contains the item's ID
        if (slotItem.getId() == item.getId()) {
            log.debug("Found {} in slot {}", item.getName(), slot);
            return true;
        }
        
        return false;
    }
    
    /**
     * Withdraws an item and places it in a specific inventory slot.
     * 
     * @param slot The target inventory slot (0-27)
     * @param item The ItemRequirement to withdraw and place
     * @return true if successful
     */
    public static boolean withdrawAndPlaceInSpecificSlot(int slot, ItemRequirement item) {
        if (slot < 0 || slot > 27) {
            log.error("Invalid inventory slot: {}", slot);
            return false;
        }
        
        log.debug("Attempting to withdraw {} and place in slot {}", item.displayString(), slot);
        
        // Check if item is already in the correct slot
        if (hasItemInSpecificSlot(slot, item)) {
            log.debug("Item {} already in slot {}", item.getName(), slot);
            return true;
        }
        
        // Ensure slot is empty or contains the same item
        Rs2ItemModel currentSlotItem = Rs2Inventory.getItemInSlot(slot);
        if (currentSlotItem != null) {
            if (currentSlotItem.getId() != item.getId()) {
                log.warn("Slot {} contains different item: {}", slot, currentSlotItem.getName());
                return false;
            }
        }
        
        // Try to withdraw the item
        boolean withdrawSuccess = false;
        int itemId = item.getId();
        if (Rs2Bank.hasItem(itemId)) {
            if (Rs2Bank.withdrawX(itemId, item.getAmount())) {
                withdrawSuccess = true;
                log.debug("Successfully withdrew {} (ID: {})", item.getName(), itemId);
            } else {
                log.warn("Failed to withdraw {} (ID: {})", item.getName(), itemId);
            }
        }
        
        if (!withdrawSuccess) {
            log.error("Could not withdraw any variant of {}", item.getName());
            return false;
        }
        
        // Wait for withdrawal to complete
        sleepUntil(() -> Rs2Inventory.hasItemAmount(item.getId(), item.getAmount(), false), 3000);
        
        // Verify the item is now in inventory and in the correct slot if needed
        if (hasItemInSpecificSlot(slot, item)) {
            log.debug("Successfully placed {} in slot {}", item.getName(), slot);
            return true;
        } else {
            log.warn("Item {} not found in expected slot {} after withdrawal", item.getName(), slot);
            // Check if it's anywhere in inventory
            if (Rs2Inventory.hasItem(item.getId())) {
                log.debug("Item {} found in inventory but not in expected slot", item.getName());
                return true; // Close enough for now
            }
            return false;
        }
    }
    
    /**
     * Checks if an item can be assigned to a specific slot based on its constraints.
     * 
     * @param item The ItemRequirement to check
     * @param slot The target slot number
     * @return true if the item can be assigned to the slot
     */
    public static boolean canAssignToSpecificSlot(ItemRequirement item, int slot) {
        // For inventory slots (0-27), check if the item can fit in inventory
        if (slot >= 0 && slot <= 27) {
            return item.getRequirementType() != RequirementType.EQUIPMENT && 
                   (item.getInventorySlot() == null || item.getInventorySlot() == -1 || item.getInventorySlot() == slot);
        }
        
        // For equipment slots, check if the item can be equipped
        return item.getRequirementType() != RequirementType.INVENTORY && 
               (item.getEquipmentSlot() != null && item.getEquipmentSlot().getSlotIdx() == slot);
    }
    
    /**
     * Validates that an item meets all suitability requirements for use.
     * 
     * @param item The ItemRequirement to validate
     * @return true if the item is suitable for use
     */
    public static boolean validateItemSuitability(ItemRequirement item) {
        log.debug("Validating suitability for item: {}", item.displayString());
        
        // Check skill requirements for usage
        if (item.getSkillToUse() != null && item.getMinimumLevelToUse() > 0) {
            int currentLevel = Rs2Player.getRealSkillLevel(item.getSkillToUse());
            if (currentLevel < item.getMinimumLevelToUse()) {
                log.warn("Insufficient {} level for {}: {} < {}", 
                        item.getSkillToUse().getName(), item.getName(), 
                        currentLevel, item.getMinimumLevelToUse());
                return false;
            }
        }
        
        // Check skill requirements for equipping
        if (item.getSkillToEquip() != null && item.getMinimumLevelToEquip() > 0) {
            int currentLevel = Rs2Player.getRealSkillLevel(item.getSkillToEquip());
            if (currentLevel < item.getMinimumLevelToEquip()) {
                log.warn("Insufficient {} level to equip {}: {} < {}", 
                        item.getSkillToEquip().getName(), item.getName(), 
                        currentLevel, item.getMinimumLevelToEquip());
                return false;
            }
        }
        
        log.debug("Item {} passes all suitability checks", item.getName());
        return true;
    }
    
    // Advanced fuzzy item utility methods
    
    /**
     * Gets the total charges available for this item across all locations.
     * Only applicable for charged items.
     * 
     * @return total charges available, or 0 if not a charged item
     */
    public int getTotalCharges() {
        return Rs2FuzzyItem.getTotalCharges(getId());
    }
    
    /**
     * Gets the count of charged items within a specific charge range in inventory.
     * 
     * @param minCharges minimum charge level (inclusive)
     * @param maxCharges maximum charge level (inclusive)
     * @return count of charged items in the specified range
     */
    public int getChargedInventoryCount(int minCharges, int maxCharges) {
        return Rs2FuzzyItem.getChargedInventoryCount(getId(), true, minCharges, maxCharges);
    }
    public int getChargedInventoryQuantity(int minCharges, int maxCharges) {
        return Rs2FuzzyItem.getChargedInventoryQuantity(getId(), true, minCharges, maxCharges);
    }
    
    /**
     * Gets the count of charged items within a specific charge range in bank.
     * 
     * @param minCharges minimum charge level (inclusive)
     * @param maxCharges maximum charge level (inclusive)
     * @return count of charged items in the specified range
     */
    public int getChargedBankCount(int minCharges, int maxCharges) {
        return Rs2FuzzyItem.getChargedBankCount(getId(), true, minCharges, maxCharges);
    }
    
    /**
     * Gets all fuzzy item variations available in inventory with detailed information.
     * 
     * @return list of FuzzyItemInfo objects sorted by charges (highest first)
     */
    public List<Rs2FuzzyItem.FuzzyItemInfo> getAllFuzzyItemsInInventory() {
        return Rs2FuzzyItem.getAllFuzzyItemsInInventory(getId(), true);
    }
    
    /**
     * Gets all fuzzy item variations available in bank with detailed information.
     * 
     * @return list of FuzzyItemInfo objects sorted by charges (highest first)
     */
    public List<Rs2FuzzyItem.FuzzyItemInfo> getAllFuzzyItemsInBank() {
        return Rs2FuzzyItem.getAllFuzzyItemsInBank(getId(), true);
    }
    
    /**
     * Gets the best (highest charged) fuzzy item match in inventory.
     * 
     * @return the best FuzzyItemInfo match, or null if none found
     */
    public Rs2FuzzyItem.FuzzyItemInfo getBestFuzzyItemInInventory() {
        return Rs2FuzzyItem.getBestFuzzyItemInInventory(getId(), true);
    }
    
    /**
     * Gets the best (highest charged) fuzzy item match in bank.
     * 
     * @return the best FuzzyItemInfo match, or null if none found
     */
    public Rs2FuzzyItem.FuzzyItemInfo getBestFuzzyItemInBank() {
        return Rs2FuzzyItem.getBestFuzzyItemInBank(getId(), true);
    }
    
    /**
     * Gets information about the fuzzy item currently equipped.
     * 
     * @return the equipped FuzzyItemInfo, or null if none found
     */
    public Rs2FuzzyItem.FuzzyItemInfo getFuzzyItemEquipped() {
        return Rs2FuzzyItem.getFuzzyItemEquipped(getId(), true);
    }
}