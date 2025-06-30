package net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.VoxPlugins.util.models.sources.SpawnLocation;
import net.runelite.client.plugins.microbot.VoxPlugins.schedulable.example.enums.UnifiedLocation;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.data.RequirementCollections;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.LocationRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.LootRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.SpellbookRequirement;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
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
    
    private final SchedulableExampleConfig config;
    
    public SchedulableExamplePrePostScheduleRequirements(SchedulableExampleConfig config) {        
        super("SchedulableExample", "Testing", false);
        
        this.config = config;
        // Initialize requirements based on configuration
        if (config.enablePrePostRequirements()) {
            initializeConfigurableRequirements();
        }
        
        initializeRequirements();
    }
    
    /**
     * Initialize requirements based on configuration settings.
     */
    private void initializeConfigurableRequirements() {
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
                        true, // use transportation
                        config.preScheduleLocation().getDisplayName(),
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
                        true, // use transportation
                        config.postScheduleLocation().getDisplayName(),
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
            
            SpawnLocation coinsSpawnLocation = new SpawnLocation(
                "Coins",
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
            RequirementCollections.registerAmuletOfGlory(this, 
            Priority.MANDATORY, 4, 
            ScheduleContext.PRE_SCHEDULE, 
            true);
            RequirementCollections.registerRingOfDueling(this,
            Priority.MANDATORY, 4,
            ScheduleContext.PRE_SCHEDULE,
            true);
            RequirementCollections.registerWoodcuttingAxes(this,Priority.MANDATORY,            
            ScheduleContext.PRE_SCHEDULE,-1); // -1 for no inventory slot means the axe can be placed in a any inventory slot, and also be equipped, -2 would mean it can only be equipped
            RequirementCollections.registerPickAxes(this, Priority.MANDATORY,           
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
        
        sb.append("Total Requirements Registered: ").append(getRegistry().getAllRequirements().size()).append("\n");
        sb.append(" Pre Requirements Registered: ").append(getRequirements(ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        sb.append("    - Spellbook Requirements: ").append(getRequirements(SpellbookRequirement.class,ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        sb.append("    - Location Requirements: ").append(getRequirements(LocationRequirement.class, ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        sb.append("    - Loot Requirements: ").append(getRequirements(LootRequirement.class, ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        sb.append("    - Equipment Requirements: ").append(getRequirements(ItemRequirement.class, ScheduleContext.PRE_SCHEDULE).size()).append("\n");
        sb.append(" Post Requirements Registered: ").append(getRequirements(ScheduleContext.POST_SCHEDULE).size()).append("\n");
        sb.append("    - Spellbook Requirements: ").append(getRequirements(SpellbookRequirement.class, ScheduleContext.POST_SCHEDULE).size()).append("\n");
        sb.append("    - Location Requirements: ").append(getRequirements(LocationRequirement.class, ScheduleContext.POST_SCHEDULE).size()).append("\n");
        sb.append("    - Loot Requirements: ").append(getRequirements(LootRequirement.class, ScheduleContext.POST_SCHEDULE).size()).append("\n");
        sb.append("    - Equipment Requirements: ").append(getRequirements(ItemRequirement.class, ScheduleContext.POST_SCHEDULE).size()).append("\n");
        
        return sb.toString();
    }
}
