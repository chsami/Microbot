package net.runelite.client.plugins.microbot.runecrafting.gotr.requirement;

import java.util.Arrays;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.data.ItemRequirementCollection;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.SpellbookRequirement;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;

public class GotrPrePostScheduleRequirements extends PrePostScheduleRequirements{
    
    
    
    public GotrPrePostScheduleRequirements() {
        super("GOTR", "Runecrafting", false);
        
       
        initializeRequirements();
    }
    
    /**
     * Initializes the item requirement collection for Guardians of the Rift minigame.
     * Based on OSRS Wiki strategies and best-in-slot items for runecrafting.
     * Now uses ItemRequirementCollection for standardized outfit and equipment registration.
     */
    @Override
    protected boolean initializeRequirements() {      
        this.getRegistry().clear(); // Clear previous requirements to avoid duplicates 
        // Initialize the optional Lunar spellbook requirement

        SpellbookRequirement lunarSpellbookRequirement = new SpellbookRequirement(
                Rs2Spellbook.LUNAR,
                // Only needed before the schedule starts, for NPC Contact spell
                // Apply to both pre and post schedule ->  when both we are not swtiching back to the current. whe a post schedule se
                ScheduleContext.PRE_SCHEDULE,   
                Priority.OPTIONAL,
                8,     // Rating 8/10 - very useful but not mandatory
                "Lunar spellbook for NPC Contact spell to repair pouches during GOTR minigame"
        );
       
         // Capture spellbook requirement in the collection
        register(lunarSpellbookRequirement);
        
        // Set location requirements
        // Pre-schedule: GOTR bank for optimal setup and preparation
        this.register(new LocationRequirement(BankLocation.GUARDIANS_OF_THE_RIFT, true,ScheduleContext.PRE_SCHEDULE, Priority.MANDATORY));             
        
        
        // Register complete outfit collections using the new ItemRequirementCollection utility
        
        // Runecrafting outfit (Robes of the Eye) - highest priority for GOTR
        ItemRequirementCollection.registerRunecraftingOutfit(this, Priority.RECOMMENDED, ScheduleContext.PRE_SCHEDULE);
        
        // Graceful outfit - weight reduction and run energy restoration
        ItemRequirementCollection.registerGracefulOutfit(this, Priority.OPTIONAL, ScheduleContext.PRE_SCHEDULE);
        
        // Basic mining equipment for mining fragments in GOTR
        ItemRequirementCollection.registerPickAxes(this,Priority.MANDATORY,  ScheduleContext.PRE_SCHEDULE);
        
        // Rune pouches for efficient essence carrying
        // TODO: Update ItemRequirementCollection.registerRunePouches to accept ScheduleContext
        // ItemRequirementCollection.registerRunePouches(this, Priority.RECOMMENDED, ScheduleContext.PRE_SCHEDULE);
        
        // Runes for NPC Contact spell (pouch repair)
        // TODO: Update ItemRequirementCollection.registerRunesForNPCContact to accept ScheduleContext
        // ItemRequirementCollection.registerRunesForNPCContact(this, Priority.OPTIONAL, 6, ScheduleContext.PRE_SCHEDULE);
        
        
        // Additional GOTR-specific items that aren't in the standard collections
        ItemRequirementCollection.registerProspectorOutfit(this, Priority.OPTIONAL,6, ScheduleContext.PRE_SCHEDULE);
        
        
        // Skill capes for additional benefits
        this.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.SKILLCAPE_RUNECRAFTING, ItemID.SKILLCAPE_RUNECRAFTING_TRIMMED), 1,
            EquipmentInventorySlot.CAPE, -1, Priority.RECOMMENDED, 10, "Runecrafting cape (any variant)", ScheduleContext.PRE_SCHEDULE,
            Skill.RUNECRAFT, 99, null, null, false
        ));
        this.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.SKILLCAPE_MINING, ItemID.SKILLCAPE_MINING_TRIMMED), 1,
            EquipmentInventorySlot.CAPE, -1, Priority.OPTIONAL, 4, "Mining cape (any variant)", ScheduleContext.PRE_SCHEDULE,
            Skill.MINING, 99, null, null, false
        ));
        
        // AMULET - Binding necklace essential for combination runes
        this.register(new ItemRequirement(
            ItemID.MAGIC_EMERALD_NECKLACE,
            EquipmentInventorySlot.AMULET, Priority.RECOMMENDED, 8, "Binding necklace (essential for combination runes)", ScheduleContext.PRE_SCHEDULE
        ));
        ItemRequirementCollection.registerAmuletOfGlory(this, Priority.OPTIONAL, 6, ScheduleContext.PRE_SCHEDULE,true);              
        // Varrock armour for mining fragments
        this.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.VARROCK_ARMOUR_EASY, ItemID.VARROCK_ARMOUR_MEDIUM, ItemID.VARROCK_ARMOUR_HARD, ItemID.VARROCK_ARMOUR_ELITE),
            EquipmentInventorySlot.BODY, Priority.OPTIONAL, 8, "Varrock armour (any variant, for mining fragments)", ScheduleContext.PRE_SCHEDULE
        ));
        
        // RING - Ring of endurance excellent for run energy, then teleport rings
        this.register(new ItemRequirement(
            ItemID.RING_OF_ENDURANCE,
            EquipmentInventorySlot.RING, Priority.RECOMMENDED, 8, "Ring of endurance (for run energy)", ScheduleContext.PRE_SCHEDULE
        ));
        
        // Alternative boots for weight reduction
        this.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.IKOV_BOOTSOFLIGHTNESS, ItemID.IKOV_BOOTSOFLIGHTNESSWORN),
            EquipmentInventorySlot.BOOTS, Priority.OPTIONAL, 4, "Boots of lightness (for weight reduction)", ScheduleContext.PRE_SCHEDULE
        ));

        //set location post-schedule requirements - go to grand exchange after GOTR
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE,true, ScheduleContext.POST_SCHEDULE, Priority.MANDATORY));
        // Post-schedule: Guardians of the Rift bank for returning after minigame
        //this.register(new LocationRequirement(BankLocation.GUARDIANS_OF_THE_RIFT,true, ScheduleContext.POST_SCHEDULE, Priority.OPTIONAL));
        this.setInitialized(true); // Mark requirements as initialized
        return true; // Return true to indicate successful initialization
    }
}
