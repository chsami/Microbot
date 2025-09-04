
package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example;

import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.magic.Runes;

import java.time.Duration;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.reactivex.rxjava3.functions.BooleanSupplier;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

import net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example.enums.UnifiedLocation;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.data.ItemRequirementCollection;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.collection.LootRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.SpellbookRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.ShopRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.models.ShopOperation;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.ShopItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.util.shop.models.Rs2ShopItem;
import net.runelite.client.plugins.microbot.util.shop.models.Rs2ShopType;
import net.runelite.client.plugins.microbot.util.shop.StoreLocations;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesInterval;
import net.runelite.client.plugins.microbot.util.grounditem.models.Rs2SpawnLocation;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.OrRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional.ConditionalRequirement;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;

/**
 * Example implementation of PrePostScheduleRequirements for the SchedulableExample plugin.
 * This demonstrates configurable requirements that can be enabled/disabled via plugin configuration.
 * 
 * Features demonstrated:
 * - Spellbook requirement (optional Lunar spellbook)
 * - Location requirements (pre: Varrock West, post: Grand Exchange)
 * - Loot requirement (coins near Lumbridge)
 * - Equipment requirement (Staff of Air)
 * - Inventory requirement (10k coins)
 */
@Slf4j
public class SchedulableExamplePrePostScheduleRequirements extends PrePostScheduleRequirements {
    @Setter
    private SchedulableExampleConfig config;
    
    public SchedulableExamplePrePostScheduleRequirements(SchedulableExampleConfig config) {        
        super("SchedulableExample", "Testing", false);        
        this.config = config;                              
    }
    
    /**
     * Initialize requirements based on configuration settings.
     */
    private boolean initializeConfigurableRequirements() {
        if (config == null) {            
            return false; // Ensure config is initialized before proceeding
        }
        if (!config.enablePrePostRequirements()) {            
            return true; // Skip requirements if disabled
        }
        boolean success = true;
        this.getRegistry().clear();
        // Configure spellbook requirements based on dropdown selection
        if (!config.preScheduleSpellbook().isNone()) {
            SpellbookRequirement preSpellbookRequirement = new SpellbookRequirement(
                config.preScheduleSpellbook().getSpellbook(),
                TaskContext.PRE_SCHEDULE,
                RequirementPriority.MANDATORY,
                7,
                "Pre-schedule spellbook: " + config.preScheduleSpellbook().getDisplayName()
            );
            this.register(preSpellbookRequirement);
        }
        
        if (!config.postScheduleSpellbook().isNone()) {
            SpellbookRequirement postSpellbookRequirement = new SpellbookRequirement(
                config.postScheduleSpellbook().getSpellbook(),
                TaskContext.POST_SCHEDULE,
                RequirementPriority.MANDATORY,
                7,
                "Post-schedule spellbook: " + config.postScheduleSpellbook().getDisplayName()
            );
            this.register(postSpellbookRequirement);
        }
        
        
       
        // Configure location requirements based on dropdown selection
        if (!config.preScheduleLocation().equals(UnifiedLocation.NONE)) {
            LocationRequirement preLocationRequirement;
            
            // Handle different location types appropriately
            switch (config.preScheduleLocation().getType()) {
                case BANK:
                    preLocationRequirement = new LocationRequirement(
                        (BankLocation) config.preScheduleLocation().getOriginalLocationData(),
                        true, // use transportation
                        -1, // no specific world required
                        TaskContext.PRE_SCHEDULE,
                        RequirementPriority.MANDATORY
                    );
                    break;
                    
                case DEPOSIT_BOX:
                case SLAYER_MASTER:
                case FARMING:
                case HUNTING:
                default:
                    // For non-bank locations, use WorldPoint
                    preLocationRequirement = new LocationRequirement(
                        config.preScheduleLocation().getWorldPoint(),
                        config.preScheduleLocation().getDisplayName(),
                        true, // use members
                        9,
                        true, // use transportation                        
                        -1, // no specific world required
                        TaskContext.PRE_SCHEDULE,
                        RequirementPriority.MANDATORY,
                        1,
                        "Must be at " + config.preScheduleLocation().getDisplayName() + " to begin the schedule"
                    );
                    break;
            }
            
            this.register(preLocationRequirement);
        }
       
        
        if (!config.postScheduleLocation().equals(UnifiedLocation.NONE)) {
            LocationRequirement postLocationRequirement;
            
            // Handle different location types appropriately
            switch (config.postScheduleLocation().getType()) {
                case BANK:
                    postLocationRequirement = new LocationRequirement(
                        (BankLocation) config.postScheduleLocation().getOriginalLocationData(),
                        true, // use transportation
                        -1, // no specific world required
                        TaskContext.POST_SCHEDULE,
                        RequirementPriority.MANDATORY
                    );
                    break;
                    
                case DEPOSIT_BOX:
                case SLAYER_MASTER:
                case FARMING:
                case HUNTING:
                default:
                    // For non-bank locations, use WorldPoint
                    postLocationRequirement = new LocationRequirement(
                        config.postScheduleLocation().getWorldPoint(),
                        config.postScheduleLocation().getDisplayName(),                        
                        true,
                        10, // acceptable distance
                        true, // use transportation                
                        -1, // no specific world required        
                        TaskContext.POST_SCHEDULE,
                        RequirementPriority.MANDATORY
                    );
                    break;
            }
            
            this.register(postLocationRequirement);
        }
        
        
        // Loot requirement - Coins near Lumbridge Castle
        if (config.enableLootRequirement()) {
            List<WorldPoint> coinSpawns = Arrays.asList(
                new WorldPoint(3205, 3229, 0), // Lumbridge Castle ground floor coin spawns
                new WorldPoint(3207, 3229, 0),
                new WorldPoint(3209, 3229, 0)
            );
            
            Rs2SpawnLocation coinsSpawnLocation = new Rs2SpawnLocation(
                ItemID.COINS,
                "Lumbridge Castle",
                "Ground Floor - East Wing",
                coinSpawns,
                false, // Not members only
                0, // Ground floor
                Duration.ofSeconds(30) // Respawn time
            );
            
            LootRequirement coinsLootRequirement = new LootRequirement(
                ItemID.COINS,
                5, // Amount to collect
                "Test coins collection from Lumbridge Castle spawns",
                coinsSpawnLocation
            );
            
            register(coinsLootRequirement);
        }
        // Equipment requirement - Staff of Air
        if (config.enableEquipmentRequirement()) {
            register(new ItemRequirement(
                ItemID.STAFF_OF_AIR,
                1,
                EquipmentInventorySlot.WEAPON,
                -2, // must be equipped (equipment slot enforced)
                RequirementPriority.MANDATORY,
                6,
                "Staff of Air for basic magic",
                TaskContext.PRE_SCHEDULE
            ));
            ItemRequirementCollection.registerAmuletOfGlory(this, 
            RequirementPriority.MANDATORY, 4, 
            TaskContext.PRE_SCHEDULE, 
            true);
            ItemRequirementCollection.registerRingOfDueling(this,
            RequirementPriority.MANDATORY, 4,
            TaskContext.PRE_SCHEDULE,
            true);
            ItemRequirementCollection.registerWoodcuttingAxes(this,RequirementPriority.MANDATORY,            
            TaskContext.PRE_SCHEDULE,-1); // -1 for no inventory slot means the axe can be placed in a any inventory slot, and also be equipped, -2 would mean it can only be equipped
            ItemRequirementCollection.registerPickAxes(this, RequirementPriority.MANDATORY,           
            TaskContext.POST_SCHEDULE);
        }
        
        // Inventory requirement - 10k coins
        if (config.enableInventoryRequirement()) {
            register(new ItemRequirement(
                ItemID.COINS,
                10000,
                -1, // inventory slot
                RequirementPriority.RECOMMENDED,
                8,
                "10,000 coins for general purposes",
                TaskContext.PRE_SCHEDULE
            ));
       
        
            // Add some basic optional requirements that are always available                    
            register( new ItemRequirement(
                ItemID.AIRRUNE,
                50,
                null, // no equipment slot
                -1, // inventory slot
                RequirementPriority.MANDATORY,
                5,
                "Basic runes for magic",
                TaskContext.PRE_SCHEDULE
            ));
             register( new ItemRequirement(
                ItemID.WATERRUNE,
                50,
                null, // no equipment slot
                -1, // inventory slot
                RequirementPriority.RECOMMENDED,
                5,
                "Basic runes for magic",
                TaskContext.PRE_SCHEDULE
            ));
             register( new ItemRequirement(
                ItemID.EARTHRUNE,
                50,
                null, // no equipment slot
                -1, // inventory slot
                RequirementPriority.RECOMMENDED,
                5,
                "Basic runes for magic",
                TaskContext.PRE_SCHEDULE
            ));

            // Basic runes for magic
            register(new ItemRequirement(
                ItemID.LAWRUNE,
                10,
                -1, // inventory slot
                RequirementPriority.MANDATORY,
                5,
                "Law runes for magic",
                TaskContext.PRE_SCHEDULE
            ));
            
            // ====================================================================
            // OR REQUIREMENT MODES DEMONSTRATION
            // ====================================================================
            // This example demonstrates both OR requirement modes:
            // 
            // 1. ANY_COMBINATION (default): Can fulfill with any combination of food items
            //    Example: 2 lobsters + 3 swordfish = 5 total food items ✓
            // 
            // 2. SINGLE_TYPE: Must fulfill with exactly one type of item
            //    Example: Exactly 5 lobsters OR 5 swordfish OR 5 monkfish ✓
            //    But NOT 2 lobsters + 3 swordfish ✗
            //
            // You can set the mode using: setOrRequirementMode(OrRequirementMode.SINGLE_TYPE)
            // Default mode is ANY_COMBINATION for backward compatibility
            // ====================================================================
            
            // Basic food for emergencies (demonstrates OR requirement)
            register(ItemRequirement.createOrRequirement(
                Arrays.asList(
                    ItemID.LOBSTER,
                    ItemID.SWORDFISH,
                    ItemID.MONKFISH,
                    ItemID.BREAD
                ),
                5,
                null, // no equipment slot
                -1, // inventory slot
                RequirementPriority.MANDATORY,
                4,
                "Basic food for health restoration (OR requirement - any combination or single type based on mode)",
                TaskContext.PRE_SCHEDULE
            ));
         }
         
         // Shop requirement - Multi-Item Maple Bow Trading Example
         // This demonstrates the unified stock management system with multi-item operations:
         // - Single BUY requirement for both maple bow types from Grand Exchange (pre-schedule)
         // - Single SELL requirement for both maple bow types to Brian's Archery Supplies (post-schedule)
         
         // Create shop items for both maple bow types
         Rs2ShopItem mapleLongbowGEItem = Rs2ShopItem.createGEItem(
             ItemID.MAPLE_LONGBOW, // Maple longbow ID (851)
             0.99, // sell at 0.99% of the GE price fast selling
             1.01  // buy at 101% of the GE price fast buying 
         );
         
         Rs2ShopItem mapleShortbowGEItem = Rs2ShopItem.createGEItem(
             ItemID.MAPLE_SHORTBOW, // Maple shortbow ID (853)
             0.99, // sell at 99% of the GE price lasted price
             1.01  // buy at 101% of the GE price lasted price
         );
         
         // Brian's Archery Supplies shop setup (Rimmington)
         WorldPoint brianShopLocation = new WorldPoint(2957, 3204, 0); // Brian's Archery Supplies in Rimmington
         WorldArea brianShopArea = new WorldArea(brianShopLocation.getX() - 4, brianShopLocation.getY() - 4, 8, 8, brianShopLocation.getPlane());
         
         Rs2ShopItem mapleLongbowShopItem = new Rs2ShopItem(
             ItemID.MAPLE_LONGBOW, // Maple longbow ID (851)
             "Brian", // Shop NPC name  
             brianShopArea, // Shop area
             Rs2ShopType.ARCHERY_SHOP,
             1.0, // 100% sell rate (from OSRS wiki)
             0.65, // 65% buy rate (from OSRS wiki - Brian buys at 65% value)
             2.0, // Change percent
             Map.of(), // No quest requirements
             false, // Not members only
             "Brian's Archery Supplies in Rimmington", // Notes
             Duration.ofMinutes(2), // Restock time: 2 minutes (from OSRS wiki)
             2 // Base stock: 2 maple longbows (from OSRS wiki)
         );
         
         Rs2ShopItem mapleShortbowShopItem = new Rs2ShopItem(
             ItemID.MAPLE_SHORTBOW, // Maple shortbow ID (853)
             "Brian", // Shop NPC name
             brianShopArea, // Same shop area
             Rs2ShopType.ARCHERY_SHOP,
             1.0, // 100% sell rate
             0.65, // 65% buy rate
             2.0, // Change percent
             Map.of(), // No quest requirements
             false, // Not members only
             "Brian's Archery Supplies in Rimmington", // Notes
             Duration.ofMinutes(2), // Restock time
             2 // Base stock: 2 maple shortbows
         );

         // Create multi-item shop requirements using the current Map-based system
         Map<Rs2ShopItem, ShopItemRequirement> geBuyItems = Map.of(
             mapleLongbowGEItem, new ShopItemRequirement(mapleLongbowGEItem, 
                                                            2, 
                                                            1,
                                                            TimeSeriesInterval.FIVE_MINUTES,
                                                            true), // 2 longbows, flexible buying
             mapleShortbowGEItem, new ShopItemRequirement(mapleShortbowGEItem, 
                                                        2, 
                                                    1,
                                                    TimeSeriesInterval.FIVE_MINUTES,
                                                    true)  // 2 shortbows, flexible buying
         );
         
         // Brian's shop: base stock=2, can sell up to 10 per world (max stock=12)
         Map<Rs2ShopItem, ShopItemRequirement> brianSellItems = Map.of(
             mapleLongbowShopItem, new ShopItemRequirement(mapleLongbowShopItem, 20, 10), // Sell up to 10 longbows per world
             mapleShortbowShopItem, new ShopItemRequirement(mapleShortbowShopItem, 20, 10)  // Sell up to 10 shortbows per world
         );
         
         // Single multi-item BUY requirement for Grand Exchange
         ShopRequirement buyMapleBowsRequirement = new ShopRequirement(
             geBuyItems,
             ShopOperation.BUY,
             RequirementType.SHOP,
             RequirementPriority.MANDATORY,
             8,
             "Buy maple bows (longbow x20, shortbow x20) from Grand Exchange (pre-schedule)",
             TaskContext.PRE_SCHEDULE
         );
        
         // Single multi-item SELL requirement for Brian's shop
         ShopRequirement sellMapleBowsRequirement = new ShopRequirement(
             brianSellItems,
             ShopOperation.SELL,
             RequirementType.SHOP,
             RequirementPriority.MANDATORY,
             8,
             "Sell up to 10 maple bows per type to Brian's Archery Supplies (post-schedule)",
             TaskContext.POST_SCHEDULE
         );
        
         if (config.enableShopRequirement()) {
             // Register the unified multi-item shop requirements
             this.register(buyMapleBowsRequirement);
             this.register(sellMapleBowsRequirement);
         }
         
         // Custom Shop Requirement - Hammer and Bucket from nearest General Store
         if (config.externalRequirements()) {
             // Find the nearest general store that has both hammer and bucket
             StoreLocations nearestStore = StoreLocations.getNearestStoreWithAllItems(ItemID.HAMMER, ItemID.BUCKET_EMPTY);
             log.info("Nearest general store with hammer and bucket: {}", nearestStore != null ? nearestStore.getName() : "None found");
             if (nearestStore != null) {
                 // Create shop items for hammer and bucket from the nearest general store
                 WorldArea storeArea = new WorldArea(
                     nearestStore.getLocation().getX() - 3, 
                     nearestStore.getLocation().getY() - 3, 
                     6, 6, 
                     nearestStore.getLocation().getPlane()
                 );
                 
                 Rs2ShopItem hammerShopItem = new Rs2ShopItem(
                     ItemID.HAMMER,
                     nearestStore.getNpcName(),
                     storeArea,
                     nearestStore.getShopType(),
                     nearestStore.getSellRate(), // Standard sell rate for general stores
                     nearestStore.getBuyRate(),
                     nearestStore.getChangePercent(),
                     nearestStore.getQuestRequirements(),
                     nearestStore.isMembers(),
                     "Hammer from " + nearestStore.getName(),
                     Duration.ofMinutes(5),
                     5 // Base stock for hammers
                 );
                 
                 Rs2ShopItem bucketShopItem = new Rs2ShopItem(
                     ItemID.BUCKET_EMPTY,
                     nearestStore.getNpcName(),
                     storeArea,
                     nearestStore.getShopType(),
                     nearestStore.getSellRate(), // Standard sell rate for general stores
                     nearestStore.getBuyRate(),
                     nearestStore.getChangePercent(),
                     nearestStore.getQuestRequirements(),
                     nearestStore.isMembers(),
                     "Empty bucket from " + nearestStore.getName(),
                     Duration.ofMinutes(5),
                     3 // Base stock for buckets
                 );
                 
                 // Create shop item requirements
                 ShopItemRequirement hammerRequirement = new ShopItemRequirement(hammerShopItem, 1, 0);
                 ShopItemRequirement bucketRequirement = new ShopItemRequirement(bucketShopItem, 1, 0);
                 
                 // Create the unified shop requirement for buying both items
                 Map<Rs2ShopItem, ShopItemRequirement> shopItems = new LinkedHashMap<>();
                 shopItems.put(hammerShopItem, hammerRequirement);
                 shopItems.put(bucketShopItem, bucketRequirement);
                 
                 ShopRequirement buyToolsRequirement = new ShopRequirement(
                     shopItems,
                     ShopOperation.BUY,
                     RequirementType.SHOP,
                     RequirementPriority.MANDATORY,
                     7,
                     "Buy hammer and bucket from nearest general store (" + nearestStore.getName() + ")",
                     TaskContext.PRE_SCHEDULE
                 );
                 
                 // Add as custom requirement to test external requirement fulfillment (step 7)
                 this.addCustomRequirement(buyToolsRequirement, TaskContext.PRE_SCHEDULE);
                 
                 log.info("Added custom shop requirement for hammer and bucket from: {}", nearestStore.getName());
             } else {
                 log.warn("No general store found with both hammer and bucket items");                 
                 success = false; // Mark as failure if no store found
             }
         }
        
         // === Alch Conditional Requirement Example ===
        if (config.enableConditionalItemRequirement()) {
            
            // Build fire staff requirements (all staves that provide fire runes)
            List<ItemRequirement> staffWithFireRunesRequirements = Arrays.stream(Rs2Staff.values())
                .filter(staff -> staff.getRunes().contains(Runes.FIRE) && staff != Rs2Staff.NONE)
                .map(staff -> new ItemRequirement(
                    staff.getItemID(),
                    1,
                    EquipmentInventorySlot.WEAPON,
                    -2,
                    RequirementPriority.MANDATORY,
                    10,
                    staff.name() + " equipped",
                    TaskContext.PRE_SCHEDULE,
                    null, null, null, null, false))
                .collect(java.util.stream.Collectors.toList());
            // Helper to check if any fire staff is available in inventory or bank
            BooleanSupplier hasFireStaffCondition = () -> hasFireStaffAvailable(staffWithFireRunesRequirements);
            OrRequirement fireStaffOrRequirement = new OrRequirement(
                RequirementPriority.MANDATORY,
                "Any fire staff equipped",
                TaskContext.PRE_SCHEDULE,
                staffWithFireRunesRequirements.toArray(new ItemRequirement[0])
            );

            ItemRequirement fireRuneRequirement = new ItemRequirement(
                ItemID.FIRERUNE,
                5,
                -1,
                RequirementPriority.MANDATORY,
                10,
                "Fire runes in inventory",
                TaskContext.PRE_SCHEDULE
            );

            ConditionalRequirement alchConditionalRequirement = new ConditionalRequirement(
                RequirementPriority.MANDATORY,
                10,
                "Alching: Fire staff or fire runes",
                TaskContext.PRE_SCHEDULE,
                false
            );
            alchConditionalRequirement
                .addStep(
                    () -> {
                        try {
                            return !hasFireStaffCondition.getAsBoolean();
                        } catch (Throwable t) {
                            return false;
                        }
                    },
                    fireRuneRequirement,
                    "Fire runes in inventory (no fire staff available)"
                )
                .addStep(
                    () -> {
                        try {
                            return hasFireStaffCondition.getAsBoolean();
                        } catch (Throwable t) {
                            return false;
                        }
                    },
                    fireStaffOrRequirement,
                    "Any fire staff equipped (fire staff available)"
                );

            SpellbookRequirement normalSpellbookRequirement = new SpellbookRequirement(
                Rs2Spellbook.MODERN,
                TaskContext.PRE_SCHEDULE,
                RequirementPriority.MANDATORY,
                10,
                "Normal spellbook required for High Alchemy"
            );

            ItemRequirement natureRuneRequirement = new ItemRequirement(
                ItemID.NATURERUNE,
                1,
                -2,
                RequirementPriority.MANDATORY,
                10,
                "Nature rune for alching",
                TaskContext.PRE_SCHEDULE
            );

            this.register(alchConditionalRequirement);
            this.register(normalSpellbookRequirement);
            this.register(natureRuneRequirement);
        }
        
        return success; // Return true if all requirements initialized successfully
    }

    /**
     * Checks if any fire staff from the requirements is available in inventory or bank.
     */
    private static boolean hasFireStaffAvailable(List<ItemRequirement> staffReqs) {
        int[] staffIds = staffReqs.stream().mapToInt(ItemRequirement::getId).toArray();
        return Rs2Inventory.contains(staffIds) || Rs2Bank.hasItem(staffIds)|| Rs2Equipment.isWearing(staffIds);
    }

    
    
    /**
     * Initialize the base item requirements collection.
     * This demonstrates basic equipment and inventory requirements.
     */
    @Override
    protected boolean initializeRequirements() {
        if (config == null){
            return false; // Ensure config is initialized before proceeding
        }        
        return initializeConfigurableRequirements();
    }
    
    /**
     * Gets a display string showing which requirements are currently enabled.
     * Useful for debugging and logging.
     */
    public String getDetailedDisplay() {
        StringBuilder sb = new StringBuilder();
        sb.append("SchedulableExample Requirements Status:\n");
        sb.append("  Pre/Post Requirements: ").append(config.enablePrePostRequirements() ? "ENABLED" : "DISABLED").append("\n");
        
        if (config.enablePrePostRequirements()) {
            // Show new dropdown configurations
            sb.append("  - Pre-Schedule Spellbook: ").append(config.preScheduleSpellbook().getDisplayName()).append("\n");
            sb.append("  - Post-Schedule Spellbook: ").append(config.postScheduleSpellbook().getDisplayName()).append("\n");
            sb.append("  - Pre-Schedule Location: ").append(config.preScheduleLocation().getDisplayName()).append("\n");
            sb.append("  - Post-Schedule Location: ").append(config.postScheduleLocation().getDisplayName()).append("\n");
            
            // Show legacy configurations            
            sb.append("  - Loot Requirement: ").append(config.enableLootRequirement() ? "ENABLED (Coins at Lumbridge)" : "DISABLED").append("\n");
            sb.append("  - Equipment Requirement: ").append(config.enableEquipmentRequirement() ? "ENABLED (Staff of Air)" : "DISABLED").append("\n");
            sb.append("  - Inventory Requirement: ").append(config.enableInventoryRequirement() ? "ENABLED (10k Coins)" : "DISABLED").append("\n");
            sb.append("  - Shop Requirement: ").append(config.enableShopRequirement() ? "ENABLED (Hammer & Bucket from nearest general store)" : "DISABLED").append("\n");
        }
        sb.append(super.getDetailedDisplay());
        
        return sb.toString();
    }
   
}
