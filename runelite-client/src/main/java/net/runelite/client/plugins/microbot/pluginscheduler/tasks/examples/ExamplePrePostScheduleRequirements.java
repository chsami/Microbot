package net.runelite.client.plugins.microbot.pluginscheduler.tasks.examples;

import java.util.Arrays;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;


import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.PrePostScheduleRequirements;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
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
                TaskContext.PRE_SCHEDULE,  // Only need it before script
                RequirementPriority.RECOMMENDED,
                6,  // Rating 6/10 - moderately useful
                "Modern spellbook for teleport spells during travel"
        );                
        this.register(normalSpellbookRequirement);
        
        // Set location requirements  
        // Pre-schedule: Start at Grand Exchange for easy access to supplies
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE, true,-1,TaskContext.PRE_SCHEDULE, RequirementPriority.RECOMMENDED));        
        // Post-schedule: Return to Grand Exchange for selling/organizing items
        this.register(new LocationRequirement(BankLocation.GRAND_EXCHANGE, true,-1,TaskContext.POST_SCHEDULE, RequirementPriority.RECOMMENDED));
        
        TaskContext taskContext = TaskContext.PRE_SCHEDULE; // Default to pre-schedule context
        // HEAD - Example progression: best to worst
        this.register(new ItemRequirement(
            ItemID.GRACEFUL_HOOD,
            EquipmentInventorySlot.HEAD, RequirementPriority.RECOMMENDED, 8, "Graceful hood for weight reduction",taskContext
        ));
        this.register(new ItemRequirement(
            ItemID.VIKING_HELMET,
            EquipmentInventorySlot.HEAD, RequirementPriority.RECOMMENDED, 5, "Basic head protection",taskContext
        ));
        
        // CAPE - Example with skill requirements
        this.register(new ItemRequirement(
            ItemID.GRACEFUL_CAPE,
            EquipmentInventorySlot.CAPE, RequirementPriority.RECOMMENDED, 8, "Graceful cape for weight reduction", taskContext
        ));
        this.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.SKILLCAPE_AGILITY, ItemID.SKILLCAPE_AGILITY_TRIMMED),
            1,
            EquipmentInventorySlot.CAPE,
            -2,
            RequirementPriority.RECOMMENDED,
            10,
            "Agility cape (99 Agility required)",
            taskContext,
            Skill.AGILITY,
            99,
            null,
            null
        ));
        

        this.register(new ItemRequirement(
            ItemID.AMULET_OF_POWER,
            EquipmentInventorySlot.AMULET, RequirementPriority.RECOMMENDED, 4, "Basic amulet of power", taskContext
        ));
        
        // BODY - Example weight reduction focus
        this.register(new ItemRequirement(
            ItemID.GRACEFUL_TOP,
            EquipmentInventorySlot.BODY, RequirementPriority.RECOMMENDED, 8, "Graceful top for weight reduction",taskContext
        ));
        this.register(new ItemRequirement(
            ItemID.HARDLEATHER_BODY,
            EquipmentInventorySlot.BODY, RequirementPriority.RECOMMENDED, 3, "Basic leather body",taskContext
        ));
        
        // LEGS - Continue weight reduction theme
        this.register(new ItemRequirement(
            ItemID.GRACEFUL_LEGS,
            EquipmentInventorySlot.LEGS, RequirementPriority.RECOMMENDED, 8, "Graceful legs for weight reduction",taskContext
        ));
        this.register(new ItemRequirement(
            ItemID.LEATHER_CHAPS,
            EquipmentInventorySlot.LEGS, RequirementPriority.RECOMMENDED, 3, "Basic leather chaps",taskContext
        ));
        
        // BOOTS - Complete the graceful set
        this.register(new ItemRequirement(
            ItemID.GRACEFUL_BOOTS,
            EquipmentInventorySlot.BOOTS, RequirementPriority.RECOMMENDED, 8, "Graceful boots for weight reduction",taskContext
        ));
        this.register(new ItemRequirement(
            ItemID.LEATHER_BOOTS,
            EquipmentInventorySlot.BOOTS, RequirementPriority.RECOMMENDED, 3, "Basic leather boots",taskContext
        ));
        
        // GLOVES - Graceful gloves to complete the set
        this.register(new ItemRequirement(
            ItemID.GRACEFUL_GLOVES,
            EquipmentInventorySlot.GLOVES, RequirementPriority.RECOMMENDED, 8, "Graceful gloves for weight reduction",taskContext
        ));
        this.register(new ItemRequirement(
            ItemID.LEATHER_GLOVES,
            EquipmentInventorySlot.GLOVES, RequirementPriority.RECOMMENDED, 3, "Basic leather gloves",taskContext
        ));
        
        // INVENTORY ITEMS - Example tools and supplies
        this.register(new ItemRequirement(
            ItemID.COINS, 1, -1,
            RequirementPriority.RECOMMENDED, 9, "Coins for purchases and teleports",taskContext
        ));
       
        this.register(ItemRequirement.createOrRequirement(
            Arrays.asList(ItemID.LOBSTER, ItemID.SWORDFISH, ItemID.TUNA), 1, null,-1,
            RequirementPriority.RECOMMENDED, 5, "Food for healing if needed",taskContext
        ));        
        return true; // Initialization successful
        // EITHER ITEMS - Items that can be equipped or kept in inventory        
    }
}
