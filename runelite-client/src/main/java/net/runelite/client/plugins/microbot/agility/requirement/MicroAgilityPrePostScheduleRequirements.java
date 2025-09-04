package net.runelite.client.plugins.microbot.agility.requirement;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.agility.MicroAgilityConfig;
import net.runelite.client.plugins.microbot.agility.enums.AgilityCourse;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.data.ItemRequirementCollection;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.SpellbookRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional.ConditionalRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.OrRequirement;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.item.Rs2ItemManager;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Runes;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Pre/Post Schedule Requirements implementation for the Micro Agility plugin.
 * This class defines all the requirements needed for agility training including:
 * - Alchemy requirements (items to alch, runes for alchemy)
 * - Graceful outfit recommendations
 * - Location requirements to move to agility course start
 */
@Slf4j
public class MicroAgilityPrePostScheduleRequirements extends PrePostScheduleRequirements {
    @Setter
    private MicroAgilityConfig config;

    public MicroAgilityPrePostScheduleRequirements(MicroAgilityConfig config) {
        super("MicroAgility", "Agility Training", false);
        this.config = config;
    }

    @Override
    protected boolean initializeRequirements() {
        if (config == null) {
            log.warn("Config is null, cannot initialize requirements");
            return false;
        }

        try {
            this.getRegistry().clear();

            // Initialize alchemy requirements if enabled
            if (config.alchemy()) {
                initializeAlchemyRequirements();
            }

            // Initialize graceful outfit recommendation
            initializeGracefulOutfitRequirement();

            // Initialize location requirement to move to agility course start
            initializeLocationRequirement();
            return true;
        } catch (Exception e) {
            log.error("Failed to initialize agility requirements", e);
            return false;
        }
    }

    /**
     * Initializes alchemy-related requirements including:
     * - Items to alch (based on bank/inventory availability)
     * - Fire runes or fire staff conditional requirement
     * - Nature runes
     * - Normal spellbook requirement
     */
    private void initializeAlchemyRequirements() {       
        if (!config.alchemy()) {
            log.info("Alchemy disabled");
            return;
        }
        final String itemsStr = config.itemsToAlch();
        if (itemsStr == null || itemsStr.trim().isEmpty()) {
            log.info("Alchemy -> no items to alch specified");
            return;
        }
        // Parse items to alch from config
        List<String> itemsToAlchList = parseItemsToAlch(itemsStr);
        log.info("Initializing alchemy requirements");

        if (itemsToAlchList.isEmpty()) {
            log.warn("No valid items to alch found in config");
            return;
        }

        // Create item requirements for items to alch based on available quantity
        Map<Integer, Integer> itemsWithQuantities = new HashMap<>();
        for (String itemName : itemsToAlchList) {
            int itemId = Rs2ItemManager.getItemIdByName(itemName,false);
            int itemNotedId = ItemRequirement.getNotedId(itemId);
            int itemUnNotedId = Rs2ItemModel.getUnNotedId(itemId);
            
            if (itemId != -1) {
                Rs2ItemModel itemModel = new Rs2ItemModel( itemId, 0, 0);//custom model to get name
                if (!itemModel.getName().equalsIgnoreCase(itemName)) {
                    log.warn("Item name '{}' does not exactly match found item name '{}'", itemName, itemModel.getName());
                    continue;
                }
                int availableUnNotedQuantity = 0;
                int availableQuantity = 0;
                int bankCount = 0;

                bankCount = Rs2Bank.count(itemUnNotedId);
                availableUnNotedQuantity = Rs2Inventory.itemQuantity(itemUnNotedId);
                availableUnNotedQuantity += bankCount;
                int availableNotedQuantity = (itemId !=itemNotedId) ? Rs2Inventory.itemQuantity(itemNotedId): 0;
                availableQuantity =availableUnNotedQuantity + availableNotedQuantity;
                if (availableQuantity > 0 ) {
                    itemsWithQuantities.put(itemId, availableQuantity);
                    ItemRequirement alchableItemReq = new ItemRequirement(
                        itemId,
                        availableQuantity,
                        -1, // Any inventory slot
                        RequirementPriority.MANDATORY,
                        7,
                        "Items to alch: " + itemName + " (available: " + availableQuantity   +")",
                        TaskContext.PRE_SCHEDULE
                    );
                    register(alchableItemReq);
                    log.info("\n\tAdded alch item requirement (total {}, UnNoted {},  noted {} ):\n{}", availableQuantity,availableUnNotedQuantity,availableNotedQuantity,alchableItemReq);                    
                        
                } else {
                    log.warn("\n\tItem {} with id {} (noted: {}) not found in inventory (unnoted {}, noted: {}) or bank (unnoted {}, noted: {})", itemName, itemId, itemNotedId, Rs2Inventory.itemQuantity(itemUnNotedId), Rs2Inventory.itemQuantity(itemNotedId) ,Rs2Bank.count(itemUnNotedId), Rs2Bank.count(itemNotedId));
                    
                }
            } else {
                log.warn("\n\tCould not find item ID for: {}, ", itemName);
            }
        }

        // Calculate total items to alch for rune requirements
        int totalItemsToAlch = itemsWithQuantities.values().stream().mapToInt(Integer::intValue).sum();
        if (totalItemsToAlch > 0) {
            // Initialize fire runes/staff conditional requirement
            initializeFireRuneRequirement(totalItemsToAlch);
            ItemRequirement natItemRequirement = new ItemRequirement(
                ItemID.NATURERUNE,
                totalItemsToAlch, // 1 nature rune per alch
                -1, // Any inventory slot
                RequirementPriority.MANDATORY,
                8,
                "Nature runes for alchemy (" + totalItemsToAlch + " needed)",
                TaskContext.PRE_SCHEDULE
            );
            log.info("Adding nature rune requirement: \n{}", natItemRequirement);
            // Initialize nature rune requirement
            register( natItemRequirement);

            // Require normal spellbook for alchemy
            register(new SpellbookRequirement(
                Rs2Spellbook.MODERN,
                TaskContext.PRE_SCHEDULE,
                RequirementPriority.MANDATORY,
                8,
                "Normal spellbook required for High Alchemy"
            ));

            log.info("Added alchemy requirements for {} total items", totalItemsToAlch);
        } else {
            log.warn("No items available for alchemy despite config being enabled");
        }
    }

    /**
     * Creates conditional requirement for fire runes vs fire staff.
     * If player has fire staff available, require fire staff.
     * Otherwise, require fire runes.
     */
    private void initializeFireRuneRequirement(int totalItemsToAlch) {
        // Build list of all fire staves
        List<ItemRequirement> fireStaffRequirements = Arrays.stream(Rs2Staff.values())
            .filter(staff -> staff.getRunes().contains(Runes.FIRE) && staff != Rs2Staff.NONE)
            .map(staff -> new ItemRequirement(
                staff.getItemID(),
                1,
                EquipmentInventorySlot.WEAPON,
                -2, // Must be equipped
                RequirementPriority.MANDATORY,
                9,
                staff.name() + " equipped for fire runes",
                TaskContext.PRE_SCHEDULE
            ))
            .collect(Collectors.toList());

        // Create OR requirement for any fire staff
        OrRequirement fireStaffOrRequirement = new OrRequirement(
            RequirementPriority.MANDATORY,
            9,
            "Any fire staff equipped",
            TaskContext.PRE_SCHEDULE,
            ItemRequirement.class,
            fireStaffRequirements.toArray(new ItemRequirement[0])
        );

        // Create fire rune requirement
        ItemRequirement fireRuneRequirement = new ItemRequirement(
            ItemID.FIRERUNE,
            totalItemsToAlch * 5, // 5 fire runes per alch
            -1, // Any inventory slot
            RequirementPriority.MANDATORY,
            8,
            "Fire runes for alchemy (" + (totalItemsToAlch * 5) + " needed)",
            TaskContext.PRE_SCHEDULE
        );

        // Create conditional requirement
        ConditionalRequirement fireAlchRequirement = new ConditionalRequirement(
            RequirementPriority.MANDATORY,
            8,
            "Fire runes or fire staff for alchemy",
            TaskContext.PRE_SCHEDULE,
            false
        );

        // Add steps to conditional requirement
        fireAlchRequirement
            .addStep(
                () -> hasFireStaffAvailable(fireStaffRequirements),
                fireStaffOrRequirement,
                "Fire staff available - equip fire staff"
            )
            .addStep(
                () -> !hasFireStaffAvailable(fireStaffRequirements),
                fireRuneRequirement,
                "No fire staff available - get fire runes"
            );

        register(fireAlchRequirement);
    }

    /**
     * Checks if any fire staff is available in inventory or bank.
     */
    private boolean hasFireStaffAvailable(List<ItemRequirement> staffReqs) {
        int[] staffIds = staffReqs.stream().mapToInt(ItemRequirement::getId).toArray();
        return Rs2Inventory.contains(staffIds) || Rs2Bank.hasItem(staffIds) || Rs2Equipment.isWearing(staffIds);
    }

    /**
     * Initializes graceful outfit recommendation for agility training.
     */
    private void initializeGracefulOutfitRequirement() {
        ItemRequirementCollection.registerGracefulOutfit(
            this,
            RequirementPriority.RECOMMENDED,
            TaskContext.PRE_SCHEDULE,
            8
        );
    }

    /**
     * Initializes location requirement to move to the beginning of the selected agility course.
     */
    private void initializeLocationRequirement() {
        AgilityCourse selectedCourse = config.agilityCourse();
        if (selectedCourse != null && selectedCourse.getHandler() != null) {
            WorldPoint courseStartLocation = selectedCourse.getHandler().getStartPoint();
            if (courseStartLocation != null) {
                register(new LocationRequirement(
                    courseStartLocation,
                    selectedCourse.name() + " Start",
                    true, // Members flag - all courses are members
                    10, // Acceptable distance
                    true, // Use transports
                    -1, // Any world
                    TaskContext.PRE_SCHEDULE,
                    RequirementPriority.MANDATORY,
                    6,
                    "Move to " + selectedCourse.name() + " agility course start"
                ));
            } else {
                log.warn("No start location found for agility course: {}", selectedCourse.name());
            }
        }
        
        // Add Grand Exchange location requirement for post-schedule tasks
        register(new LocationRequirement(
            new WorldPoint(3164, 3486, 0), // Grand Exchange center
            "Grand Exchange",
            false, // F2P location
            15, // Acceptable distance
            true, // Use transports
            -1, // Any world
            TaskContext.POST_SCHEDULE,
            RequirementPriority.RECOMMENDED,
            5,
            "Move to Grand Exchange for banking and trading"
        ));
        log.info("Added Grand Exchange location requirement for post-schedule tasks");
    }

    /**
     * Parses the comma-separated list of items to alch from config.
     */
    private List<String> parseItemsToAlch(String itemsToAlchString) {
        if (itemsToAlchString == null || itemsToAlchString.trim().isEmpty()) {
            return Collections.emptyList();
        }

        return Arrays.stream(itemsToAlchString.split(","))
            .map(String::trim)
            .filter(item -> !item.isEmpty())
            .collect(Collectors.toList());
    }

  


    @Override
    public String getDetailedDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append("MicroAgility Requirements Status:\n");

        sb.append("  - Alchemy Enabled: ").append(config.alchemy() ? "YES" : "NO").append("\n");        
        if (config.alchemy()) {
            sb.append("  - Items to Alch: ").append(config.itemsToAlch()).append("\n");
            
            // Show parsed items and their availability
            List<String> itemsToAlchList = parseItemsToAlch(config.itemsToAlch());
            for (String itemName : itemsToAlchList) {
                int itemId = Rs2ItemManager.getItemIdByName(itemName, false);
                int notedItemId = ItemRequirement.getNotedId(itemId);
                int unnotedId =  Rs2ItemModel.getUnNotedId(itemId);
                int availableUnNotedQuantity = Rs2Inventory.count(itemId) ;
                int availableNotedQuantity = itemId != notedItemId ? Rs2Inventory.itemQuantity(notedItemId):0;
                int availableQuantityInBank = Rs2Bank.count(itemId);
                if (itemId != -1) {
                    int availableQuantity = availableQuantityInBank + availableUnNotedQuantity + availableNotedQuantity;
                    sb.append("    - ").append(itemName).append(" (ID: ").append(itemId).append("-noted ID: ").append(notedItemId) 
                        .append("\n\t -Available noted: ").append(availableNotedQuantity)
                        .append("\n\tAvailable unnoted: ").append(availableUnNotedQuantity)
                        .append("\n\tAvailable in bank: ").append(availableQuantityInBank)
                        .append("\n\tTotal available: ").append(availableQuantity).append(")\n");
                } else {
                    sb.append("    - ").append(itemName).append(" (NOT FOUND)\n");
                }
            }
        }
        
        sb.append("  - Selected Course: ").append(config.agilityCourse() != null ? config.agilityCourse().name() : "NONE").append("\n");
        sb.append(super.getDetailedDisplay());
        
        return sb.toString();
    }

    
}