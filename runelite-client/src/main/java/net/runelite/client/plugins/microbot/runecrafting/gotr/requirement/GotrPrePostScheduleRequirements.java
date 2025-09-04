package net.runelite.client.plugins.microbot.runecrafting.gotr.requirement;

import java.util.Arrays;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.data.ItemRequirementCollection;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;
import net.runelite.client.plugins.microbot.runecrafting.gotr.GotrConfig;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.SpellbookRequirement;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
@Slf4j
public class GotrPrePostScheduleRequirements extends PrePostScheduleRequirements{
    @Setter
    GotrConfig config;
    /**
     * Constructor for GOTR pre/post schedule requirements.
     * Initializes the requirements for Guardians of the Rift minigame.
     */
    
    public GotrPrePostScheduleRequirements( GotrConfig config) {
        super("GOTR", "Runecrafting", false);                       
    }
    
    /**
     * Initializes the item requirement collection for Guardians of the Rift minigame.
     * Based on OSRS Wiki strategies and best-in-slot items for runecrafting.
     * Now uses ItemRequirementCollection for standardized outfit and equipment registration.
     */
    @Override
    protected boolean initializeRequirements() {      
        if (!Microbot.isLoggedIn()) {
            log.info("Cannot initialize GOTR requirements - not logged in");
            return false; 
        }
        this.getRegistry().clear(); // Clear previous requirements to avoid duplicates 
        
        log.info("Initializing Guardians of the Rift pre/post schedule requirements...");
        // ====================================================================
        // Pre-Schedule Requirements for Guardians of the Rift
        // ====================================================================
        // Initialize the optional Lunar spellbook requirement
        SpellbookRequirement lunarSpellbookRequirement = new SpellbookRequirement(
                Rs2Spellbook.LUNAR,
                // Only needed before the schedule starts, for NPC Contact spell
                // Apply to both pre and post schedule ->  when both we are not swtiching back to the current. whe a post schedule se
                TaskContext.PRE_SCHEDULE,   
                RequirementPriority.RECOMMENDED,
                8,     // Rating 8/10 - very useful but not mandatory
                "Lunar spellbook for NPC Contact spell to repair pouches during GOTR minigame"
        );
       
         // Capture spellbook requirement in the collection
        register(lunarSpellbookRequirement);
        
        // Set location requirements
        // Pre-schedule: GOTR bank for optimal setup and preparation
        int[] gotrWorlds = {    409, 
                                445, 
                                464, 
                                478, 
                                522, 
                                534 }; // GOTR worlds
        
        // Use LocationRequirementUtil to select world with most players from GOTR worlds
        int mostPopulatedGotrWorld = Rs2WorldUtil.getMostPopulatedWorldFromList(gotrWorlds);
        if (mostPopulatedGotrWorld == -1) {           
           return false; // No valid GOTR world found
        }
       
        int selectedWorld = mostPopulatedGotrWorld != -1 ? mostPopulatedGotrWorld : 522; // Fallback to 522
        
        this.register(new LocationRequirement(BankLocation.GUARDIANS_OF_THE_RIFT, 15, true, selectedWorld, TaskContext.PRE_SCHEDULE, RequirementPriority.MANDATORY));             
        
        
        // Register complete outfit collections using the new ItemRequirementCollection utility
        
        // Runecrafting outfit (Robes of the Eye) - highest priority for GOTR
        ItemRequirementCollection.registerRunecraftingOutfit(this, RequirementPriority.RECOMMENDED, TaskContext.PRE_SCHEDULE);
        
        // Graceful outfit - weight reduction and run energy restoration
        ItemRequirementCollection.registerGracefulOutfit(this, RequirementPriority.RECOMMENDED, TaskContext.PRE_SCHEDULE,1);
        
        // Basic mining equipment for mining fragments in GOTR
        ItemRequirementCollection.registerPickAxes(this,RequirementPriority.MANDATORY,  TaskContext.PRE_SCHEDULE);
        
        // Rune pouches for efficient essence carrying
        // TODO: Update ItemRequirementCollection.registerRunePouches to accept TaskContext
        // ItemRequirementCollection.registerRunePouches(this, Priority.RECOMMENDED, TaskContext.PRE_SCHEDULE);
        
        // Runes for NPC Contact spell (pouch repair)
        // TODO: Update ItemRequirementCollection.registerRunesForNPCContact to accept TaskContext
        // ItemRequirementCollection.registerRunesForNPCContact(this, Priority.OPTIONAL, 6, TaskContext.PRE_SCHEDULE);
        
        
        // Additional GOTR-specific items that aren't in the standard collections
        ItemRequirementCollection.registerProspectorOutfit(this, RequirementPriority.RECOMMENDED,6, TaskContext.PRE_SCHEDULE);
        
        
        // Skill capes for additional benefits
        this.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.SKILLCAPE_RUNECRAFTING, ItemID.SKILLCAPE_RUNECRAFTING_TRIMMED), 1,
            EquipmentInventorySlot.CAPE, -2, RequirementPriority.RECOMMENDED, 10, "Runecrafting cape (any variant)", TaskContext.PRE_SCHEDULE,
            Skill.RUNECRAFT, 99, null, null, false
        ));
        this.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.SKILLCAPE_MINING, ItemID.SKILLCAPE_MINING_TRIMMED), 1,
            EquipmentInventorySlot.CAPE, -2, RequirementPriority.RECOMMENDED, 4, "Mining cape (any variant)", TaskContext.PRE_SCHEDULE,
            Skill.MINING, 99, null, null, false
        ));
        
        // AMULET - Binding necklace essential for combination runes
        this.register(new ItemRequirement(
            ItemID.MAGIC_EMERALD_NECKLACE,
            EquipmentInventorySlot.AMULET, RequirementPriority.RECOMMENDED, 8, "Binding necklace (essential for combination runes)", TaskContext.PRE_SCHEDULE
        ));
        ItemRequirementCollection.registerAmuletOfGlory(this, RequirementPriority.RECOMMENDED, 6, TaskContext.PRE_SCHEDULE,true);              
        // Varrock armour for mining fragments
        this.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.VARROCK_ARMOUR_EASY, ItemID.VARROCK_ARMOUR_MEDIUM, ItemID.VARROCK_ARMOUR_HARD, ItemID.VARROCK_ARMOUR_ELITE),
            EquipmentInventorySlot.BODY, RequirementPriority.RECOMMENDED, 8, "Varrock armour (any variant, for mining fragments)", TaskContext.PRE_SCHEDULE
        ));
        
        // RING - Ring of endurance excellent for run energy, then teleport rings
        this.register(new ItemRequirement(
            ItemID.RING_OF_ENDURANCE,
            EquipmentInventorySlot.RING, RequirementPriority.RECOMMENDED, 8, "Ring of endurance (for run energy)", TaskContext.PRE_SCHEDULE
        ));
        
        // Alternative boots for weight reduction
        this.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.IKOV_BOOTSOFLIGHTNESS, ItemID.IKOV_BOOTSOFLIGHTNESSWORN),
            EquipmentInventorySlot.BOOTS, RequirementPriority.RECOMMENDED, 4, "Boots of lightness (for weight reduction)", TaskContext.PRE_SCHEDULE
        ));
        // ====================================================================
        // Post-Schedule Requirements for Guardians of the Rift
        // ====================================================================
        //set location post-schedule requirements - go to grand exchange after GOTR
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE,
            20,
                true, -1,TaskContext.POST_SCHEDULE, RequirementPriority.MANDATORY));
        // Post-schedule: Guardians of the Rift bank for returning after minigame
        //this.register(new LocationRequirement(BankLocation.GUARDIANS_OF_THE_RIFT,true, TaskContext.POST_SCHEDULE, Priority.OPTIONAL));        

        return true; // Return true to indicate successful initialization
    }
}
