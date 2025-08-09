package net.runelite.client.plugins.microbot.pluginscheduler.tasks.examples;

import java.util.Arrays;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.Priority;


import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.ScheduleContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.SpellbookRequirement;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;

/**
 * Example implementation of PrePostScheduleRequirements showing basic usage patterns.
 * This serves as a template for creating requirements collections for other plugins.
 * 
 * This example demonstrates:
 * - Basic equipment requirements with different priority levels
 * - Location requirements for pre and post schedule positioning
 * - Optional spellbook requirements
 * - How to organize requirements by category and effectiveness
 */
public class ExamplePrePostScheduleRequirements extends PrePostScheduleRequirements {
    
    public ExamplePrePostScheduleRequirements() {
        super("Example", "General", false);
       
        initializeRequirements();
    }
    
    /**
     * Initializes the item requirement collection with example items.
     * This demonstrates typical patterns for different equipment slots and priorities.
     */
    @Override
    protected boolean initializeRequirements() {
        this.getRegistry().clear(); // Clear previous requirements if any


         
        // Example: Optional teleport spellbook for faster travel
        SpellbookRequirement normalSpellbookRequirement = new SpellbookRequirement(
                Rs2Spellbook.MODERN,
                ScheduleContext.PRE_SCHEDULE,  // Only need it before script
                Priority.OPTIONAL,
                6,  // Rating 6/10 - moderately useful
                "Modern spellbook for teleport spells during travel"
        );                
        this.register(normalSpellbookRequirement);
        
        // Set location requirements  
        // Pre-schedule: Start at Grand Exchange for easy access to supplies
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE, true,ScheduleContext.PRE_SCHEDULE, Priority.RECOMMENDED));        
        // Post-schedule: Return to Grand Exchange for selling/organizing items
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE, true,ScheduleContext.POST_SCHEDULE, Priority.RECOMMENDED));
        
        ScheduleContext scheduleContext = ScheduleContext.PRE_SCHEDULE; // Default to pre-schedule context
        // HEAD - Example progression: best to worst
        this.register(new ItemRequirement(
            ItemID.GRACEFUL_HOOD,
            EquipmentInventorySlot.HEAD, Priority.RECOMMENDED, 8, "Graceful hood for weight reduction",scheduleContext
        ));
        this.register(new ItemRequirement(
            ItemID.VIKING_HELMET,
            EquipmentInventorySlot.HEAD, Priority.OPTIONAL, 5, "Basic head protection",scheduleContext
        ));
        
        // CAPE - Example with skill requirements
        this.register(new ItemRequirement(
            ItemID.GRACEFUL_CAPE,
            EquipmentInventorySlot.CAPE, Priority.RECOMMENDED, 8, "Graceful cape for weight reduction", scheduleContext
        ));
        this.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.SKILLCAPE_AGILITY, ItemID.SKILLCAPE_AGILITY_TRIMMED),
            1,
            EquipmentInventorySlot.CAPE,
            -2,
            Priority.OPTIONAL,
            10,
            "Agility cape (99 Agility required)",
            scheduleContext,
            Skill.AGILITY,
            99,
            null,
            null
        ));
        

        this.register(new ItemRequirement(
            ItemID.AMULET_OF_POWER,
            EquipmentInventorySlot.AMULET, Priority.OPTIONAL, 4, "Basic amulet of power", scheduleContext
        ));
        
        // BODY - Example weight reduction focus
        this.register(new ItemRequirement(
            ItemID.GRACEFUL_TOP,
            EquipmentInventorySlot.BODY, Priority.RECOMMENDED, 8, "Graceful top for weight reduction",scheduleContext
        ));
        this.register(new ItemRequirement(
            ItemID.HARDLEATHER_BODY,
            EquipmentInventorySlot.BODY, Priority.OPTIONAL, 3, "Basic leather body",scheduleContext
        ));
        
        // LEGS - Continue weight reduction theme
        this.register(new ItemRequirement(
            ItemID.GRACEFUL_LEGS,
            EquipmentInventorySlot.LEGS, Priority.RECOMMENDED, 8, "Graceful legs for weight reduction",scheduleContext
        ));
        this.register(new ItemRequirement(
            ItemID.LEATHER_CHAPS,
            EquipmentInventorySlot.LEGS, Priority.OPTIONAL, 3, "Basic leather chaps",scheduleContext
        ));
        
        // BOOTS - Complete the graceful set
        this.register(new ItemRequirement(
            ItemID.GRACEFUL_BOOTS,
            EquipmentInventorySlot.BOOTS, Priority.RECOMMENDED, 8, "Graceful boots for weight reduction",scheduleContext
        ));
        this.register(new ItemRequirement(
            ItemID.LEATHER_BOOTS,
            EquipmentInventorySlot.BOOTS, Priority.OPTIONAL, 3, "Basic leather boots",scheduleContext
        ));
        
        // GLOVES - Graceful gloves to complete the set
        this.register(new ItemRequirement(
            ItemID.GRACEFUL_GLOVES,
            EquipmentInventorySlot.GLOVES, Priority.RECOMMENDED, 8, "Graceful gloves for weight reduction",scheduleContext
        ));
        this.register(new ItemRequirement(
            ItemID.LEATHER_GLOVES,
            EquipmentInventorySlot.GLOVES, Priority.OPTIONAL, 3, "Basic leather gloves",scheduleContext
        ));
        
        // INVENTORY ITEMS - Example tools and supplies
        this.register(new ItemRequirement(
            ItemID.COINS, 1, -1,
            Priority.RECOMMENDED, 9, "Coins for purchases and teleports",scheduleContext
        ));
       
        this.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.LOBSTER, ItemID.SWORDFISH, ItemID.TUNA), 1, null,-1,
            Priority.OPTIONAL, 5, "Food for healing if needed",scheduleContext
        ));
        setInitialized(true);
        return true; // Initialization successful
        // EITHER ITEMS - Items that can be equipped or kept in inventory
        
    }
}
