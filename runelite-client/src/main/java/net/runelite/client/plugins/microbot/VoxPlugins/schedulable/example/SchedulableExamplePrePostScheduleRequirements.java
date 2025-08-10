package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;

import net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example.enums.UnifiedLocation;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.data.ItemRequirementCollection;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.collection.LootRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.SpellbookRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.ShopRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.ShopItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.shop.ShopOperation;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementType;
import net.runelite.client.plugins.microbot.util.shop.models.Rs2ShopItem;
import net.runelite.client.plugins.microbot.util.shop.models.Rs2ShopType;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.grandexchange.models.TimeSeriesInterval;
import net.runelite.client.plugins.microbot.util.grounditem.models.Rs2SpawnLocation;

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
    
    private final SchedulableExampleConfig config;
    
    public SchedulableExamplePrePostScheduleRequirements(SchedulableExampleConfig config) {        
        super("SchedulableExample", "Testing", false);
        
        this.config = config;
        // Initialize requirements based on configuration                
        if (config.enablePrePostRequirements()) {
            initializeConfigurableRequirements();
            initializeRequirements();
        }
    }
    
    /**
     * Initialize requirements based on configuration settings.
     */
    private void initializeConfigurableRequirements() {
        this.clearFulfillmentState();
        this.getRegistry().clear();
        // Configure spellbook requirements based on dropdown selection
        if (!config.preScheduleSpellbook().isNone()) {
            SpellbookRequirement preSpellbookRequirement = new SpellbookRequirement(
                config.preScheduleSpellbook().getSpellbook(),
                ScheduleContext.PRE_SCHEDULE,
                Priority.MANDATORY,
                7,
                "Pre-schedule spellbook: " + config.preScheduleSpellbook().getDisplayName()
            );
            this.register(preSpellbookRequirement);
        }
        
        if (!config.postScheduleSpellbook().isNone()) {
            SpellbookRequirement postSpellbookRequirement = new SpellbookRequirement(
                config.postScheduleSpellbook().getSpellbook(),
                ScheduleContext.POST_SCHEDULE,
                Priority.MANDATORY,
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
                        ScheduleContext.PRE_SCHEDULE,
                        Priority.MANDATORY
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
                        true, // use transportation                        
                        ScheduleContext.PRE_SCHEDULE,
                        Priority.MANDATORY
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
                        ScheduleContext.POST_SCHEDULE,
                        Priority.MANDATORY
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
                        true, // use transportation                        
                        ScheduleContext.POST_SCHEDULE,
                        Priority.MANDATORY
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
                -2,
                Priority.MANDATORY,
                6,
                "Staff of Air for basic magic",
                ScheduleContext.PRE_SCHEDULE
            ));
            ItemRequirementCollection.registerAmuletOfGlory(this, 
            Priority.MANDATORY, 4, 
            ScheduleContext.PRE_SCHEDULE, 
            true);
            ItemRequirementCollection.registerRingOfDueling(this,
            Priority.MANDATORY, 4,
            ScheduleContext.PRE_SCHEDULE,
            true);
            ItemRequirementCollection.registerWoodcuttingAxes(this,Priority.MANDATORY,            
            ScheduleContext.PRE_SCHEDULE,-1); // -1 for no inventory slot means the axe can be placed in a any inventory slot, and also be equipped, -2 would mean it can only be equipped
            ItemRequirementCollection.registerPickAxes(this, Priority.MANDATORY,           
            ScheduleContext.POST_SCHEDULE);
        }
        
        // Inventory requirement - 10k coins
        if (config.enableInventoryRequirement()) {
            register(new ItemRequirement(
                ItemID.COINS,
                10000,
                -1, // inventory slot
                Priority.RECOMMENDED,
                8,
                "10,000 coins for general purposes",
                ScheduleContext.PRE_SCHEDULE
            ));
       
        
            // Add some basic optional requirements that are always available                    
            register( new ItemRequirement(
                ItemID.AIRRUNE,
                50,
                null, // no equipment slot
                -1, // inventory slot
                Priority.OPTIONAL,
                5,
                "Basic runes for magic",
                ScheduleContext.PRE_SCHEDULE
            ));
             register( new ItemRequirement(
                ItemID.WATERRUNE,
                50,
                null, // no equipment slot
                -1, // inventory slot
                Priority.OPTIONAL,
                5,
                "Basic runes for magic",
                ScheduleContext.PRE_SCHEDULE
            ));
             register( new ItemRequirement(
                ItemID.EARTHRUNE,
                50,
                null, // no equipment slot
                -1, // inventory slot
                Priority.OPTIONAL,
                5,
                "Basic runes for magic",
                ScheduleContext.PRE_SCHEDULE
            ));

            // Basic runes for magic
            register(new ItemRequirement(
                ItemID.LAWRUNE,
                10,
                -1, // inventory slot
                Priority.MANDATORY,
                5,
                "Law runes for magic",
                ScheduleContext.PRE_SCHEDULE
            ));
            
            // Basic food for emergencies
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
                Priority.MANDATORY,
                4,
                "Basic food for health restoration",
                ScheduleContext.PRE_SCHEDULE
            ));
         }
         
         // Shop requirement - Multi-Item Maple Bow Trading Example
         // This demonstrates the unified stock management system with multi-item operations:
         // - Single BUY requirement for both maple bow types from Grand Exchange (pre-schedule)
         // - Single SELL requirement for both maple bow types to Brian's Archery Supplies (post-schedule)
         
         // Create shop items for both maple bow types
         Rs2ShopItem mapleLongbowGEItem = Rs2ShopItem.createGEItem(
             ItemID.MAPLE_LONGBOW, // Maple longbow ID (851)
             0.99, // sell at 110% of the GE price
             1.01  // buy at 90% of the GE price
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
                                                            true), // 20 longbows, flexible buying
             mapleShortbowGEItem, new ShopItemRequirement(mapleShortbowGEItem, 
                                                        2, 
                                                    1,
                                                    TimeSeriesInterval.FIVE_MINUTES,
                                                    true)  // 20 shortbows, flexible buying
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
             Priority.MANDATORY,
             8,
             "Buy maple bows (longbow x20, shortbow x20) from Grand Exchange (pre-schedule)",
             ScheduleContext.PRE_SCHEDULE
         );
        
         // Single multi-item SELL requirement for Brian's shop
         ShopRequirement sellMapleBowsRequirement = new ShopRequirement(
             brianSellItems,
             ShopOperation.SELL,
             RequirementType.SHOP,
             Priority.MANDATORY,
             8,
             "Sell up to 10 maple bows per type to Brian's Archery Supplies (post-schedule)",
             ScheduleContext.POST_SCHEDULE
         );
        
         if (config.enableShopRequirement()) {
             // Register the unified multi-item shop requirements
             this.register(buyMapleBowsRequirement);
             this.register(sellMapleBowsRequirement);
         }
    }

    
    
    /**
     * Initialize the base item requirements collection.
     * This demonstrates basic equipment and inventory requirements.
     */
    @Override
    protected void initializeRequirements() {
        if (config == null){
            log.error("SchedulableExampleConfig is not initialized. Cannot proceed with requirements setup.");
            return; // Ensure config is initialized before proceeding
        }
        initializeConfigurableRequirements();
    }
    
    /**
     * Gets a display string showing which requirements are currently enabled.
     * Useful for debugging and logging.
     */
    public String getEnabledRequirementsDisplay() {
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
        }
        
        sb.append("Total Pre\\Post Requirements Registered: ").append(getRegistry().getAllRequirements().size()).append("\n");
        sb.append(" Pre Requirements Registered: ").append(getRequirements(ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        sb.append("    - Spellbook Requirements: ").append(getRequirements(SpellbookRequirement.class,ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        sb.append("    - Location Requirements: ").append(getRequirements(LocationRequirement.class, ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        sb.append("    - Loot Requirements: ").append(getRequirements(LootRequirement.class, ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        sb.append("    - Equipment Requirements: ").append(getRequirements(ItemRequirement.class, ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        sb.append("    - Shop Requirements: ").append(getRequirements(ShopRequirement.class, ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        sb.append(" Post Requirements Registered: ").append(getRequirements(ScheduleContext.POST_SCHEDULE).size()).append("\n");
        sb.append("    - Spellbook Requirements: ").append(getRequirements(SpellbookRequirement.class, ScheduleContext.POST_SCHEDULE).size()).append("\n");
        sb.append("    - Location Requirements: ").append(getRequirements(LocationRequirement.class, ScheduleContext.POST_SCHEDULE).size()).append("\n");
        sb.append("    - Loot Requirements: ").append(getRequirements(LootRequirement.class, ScheduleContext.POST_SCHEDULE).size()).append("\n");
        sb.append("    - Equipment Requirements: ").append(getRequirements(ItemRequirement.class, ScheduleContext.POST_SCHEDULE).size()).append("\n");
        sb.append("    - Shop Requirements: ").append(getRequirements(ShopRequirement.class, ScheduleContext.POST_SCHEDULE).size()).append("\n");
        
        return sb.toString();
    }
}
