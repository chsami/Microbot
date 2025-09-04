package net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.util;

import net.runelite.api.Skill;
import net.runelite.api.ItemID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.RequirementPriority;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.enums.TaskContext;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.item.ItemRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.location.LocationRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.Requirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.SpellbookRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional.ConditionalRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.conditional.OrderedRequirement;
import net.runelite.client.plugins.microbot.pluginscheduler.tasks.requirements.requirement.logical.OrRequirement;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.stream.Collectors;

/**
 * Utility class for creating common conditional and ordered requirements.
 * Provides pre-built conditions and requirement patterns for typical OSRS workflows.
 */
public class ConditionalRequirementBuilder {
    
    /**
     * Creates a spellbook switching conditional requirement.
     * Only switches if the player has the required magic level and doesn't already have the spellbook.
     * 
     * @param targetSpellbook The spellbook to switch to
     * @param requiredLevel Minimum magic level required
     * @param priority Priority level for this requirement
     * @param TaskContext When to fulfill this requirement
     * @return ConditionalRequirement for spellbook switching
     */
    public static ConditionalRequirement createSpellbookSwitcher(Rs2Spellbook targetSpellbook, int requiredLevel, 
                                                               RequirementPriority priority, TaskContext taskContext) {
        ConditionalRequirement spellbookSwitcher = new ConditionalRequirement(
                priority, 8, "Smart Spellbook Switching", taskContext, false
        );
        
        // Only switch if we have the level and don't already have the spellbook
        BooleanSupplier needsSpellbookSwitch = () -> 
                Rs2Player.getRealSkillLevel(Skill.MAGIC) >= requiredLevel && ! Rs2Magic.isSpellbook(targetSpellbook);
        
        SpellbookRequirement spellbookReq = new SpellbookRequirement(
                targetSpellbook, taskContext, priority, 8,
                "Switch to " + targetSpellbook + " spellbook"
        );
        
        spellbookSwitcher.addStep(needsSpellbookSwitch, spellbookReq, 
                "Check magic level and switch to " + targetSpellbook + " if needed", true);
        
        return spellbookSwitcher;
    }
    
    /**
     * Creates an equipment upgrade conditional requirement.
     * Upgrades to better equipment if the player can afford it and doesn't already have it.
     * 
     * @param basicItemIds Basic equipment item IDs (always required)
     * @param upgradeItemIds Upgrade equipment item IDs (conditional)
     * @param minGpRequired Minimum GP required for upgrade
     * @param equipmentSlot Equipment slot type
     * @param description Description for the requirement
     * @param priority Priority level
     * @param TaskContext When to fulfill this requirement
     * @return ConditionalRequirement for equipment upgrading
     */
    public static ConditionalRequirement createEquipmentUpgrader(int[] basicItemIds, int[] upgradeItemIds,
                                                               int minGpRequired, EquipmentInventorySlot equipmentSlot,
                                                               String description, RequirementPriority priority, 
                                                               TaskContext taskContext) {
        ConditionalRequirement equipmentUpgrader = new ConditionalRequirement(
                priority, 7, "Smart Equipment Upgrading: " + description, taskContext, false
        );
        
        // Step 1: Ensure we have basic equipment
        OrRequirement basicEquipment =  ItemRequirement.createOrRequirement(
                Arrays.stream(basicItemIds).boxed().collect(Collectors.toList()),1,  equipmentSlot,-2,
                priority, 6, "Basic " + description, taskContext
        );
        
        equipmentUpgrader.addStep(
                () -> !hasAnyItem(basicItemIds) && !hasAnyItem(upgradeItemIds),
                basicEquipment,
                "Get basic " + description + " if none available"
        );
        
        // Step 2: Upgrade if affordable and beneficial
        if (upgradeItemIds.length > 0 && minGpRequired > 0) {
            OrRequirement upgradeEquipment = ItemRequirement.createOrRequirement(
                    Arrays.stream(upgradeItemIds).boxed().collect(Collectors.toList()),  equipmentSlot,
                    RequirementPriority.RECOMMENDED, 9, "Upgraded " + description, taskContext
            );
            
            equipmentUpgrader.addStep(
                    () -> !hasAnyItem(upgradeItemIds) && hasGP(minGpRequired),
                    upgradeEquipment,
                    "Upgrade to better " + description + " if affordable",
                    true // Optional upgrade
            );
        }
        
        return equipmentUpgrader;
    }
    
    /**
     * Creates a shop-then-equip ordered requirement.
     * First shops for items, then ensures they are equipped.
     * 
     * @param shopLocation Where to shop
     * @param itemIds Items to shop for
     * @param itemName Display name for items
     * @param quantity How many to buy
     * @param priority Priority level
     * @param TaskContext When to fulfill this requirement
     * @return OrderedRequirement for shop-then-equip workflow
     */
    public static OrderedRequirement createShopThenEquip(BankLocation shopLocation, int[] itemIds, 
                                                        String itemName, int quantity, RequirementPriority priority, 
                                                        TaskContext taskContext) {
        OrderedRequirement shopThenEquip = new OrderedRequirement(
                priority, 8, "Shop and Equip: " + itemName, taskContext
        );
        
        // Step 1: Go to shop location
        LocationRequirement location = new LocationRequirement(
                shopLocation, true,-1, taskContext, priority
        );
        shopThenEquip.addStep(location, "Travel to " + shopLocation.name() + " for shopping");
        
        // Step 2: Shop for items (assuming ShopRequirement exists)
        // This is a placeholder - you'll need to implement ShopRequirement.createBuyRequirement
        // ShopRequirement shopReq = ShopRequirement.createBuyRequirement(itemIds[0], quantity, priority);
        // shopThenEquip.addStep(shopReq, "Buy " + quantity + "x " + itemName);
        
        // Step 3: Equip the items (Note: This is a simplified example - you may need to specify equipment slot)
//        ItemRequirement equipmentReq = ItemRequirement.createOrRequirement(
  //              Arrays.stream(itemIds).boxed().collect(Collectors.toList()), null,null,-1,
   //             priority, 7, "Equipped " + itemName, TaskContext
    //    );
     //   shopThenEquip.addStep(equipmentReq, "Equip " + itemName);
        
        return shopThenEquip;
    }
    
    /**
     * Creates a bank-preparation ordered requirement.
     * Ensures player is at bank, withdraws needed items, and organizes inventory.
     * 
     * @param bankLocation Preferred bank location
     * @param withdrawItems Items to withdraw from bank
     * @param priority Priority level
     * @param TaskContext When to fulfill this requirement
     * @return OrderedRequirement for bank preparation
     */
    public static OrderedRequirement createBankPreparation(BankLocation bankLocation, ItemRequirement[] withdrawItems,
                                                          RequirementPriority priority, TaskContext taskContext) {
        OrderedRequirement bankPrep = new OrderedRequirement(
                priority, 9, "Bank Preparation", taskContext
        );
        
        // Step 1: Go to bank
        LocationRequirement bankLocationReq = new LocationRequirement(
                bankLocation, true, -1,taskContext, priority
        );
        bankPrep.addStep(bankLocationReq, "Travel to " + bankLocation.name() + " bank");
        
        // Step 2: Open bank
        // This would need a custom requirement for opening bank
        // bankPrep.addStep(new CustomRequirement(() -> Rs2Bank.openBank()), "Open bank");
        
        // Step 3: Withdraw each required item
        for (int i = 0; i < withdrawItems.length; i++) {
            ItemRequirement item = withdrawItems[i];
            bankPrep.addStep(item, "Withdraw " + item.getName(), !item.isMandatory());
        }
        
        return bankPrep;
    }
    
    /**
     * Creates a level-based conditional requirement.
     * Only fulfills the requirement if the player has sufficient level.
     * 
     * @param skill Required skill
     * @param requiredLevel Minimum level required
     * @param requirement Requirement to fulfill if level is met
     * @param description Description for this conditional
     * @param priority Priority level
     * @param TaskContext When to fulfill this requirement
     * @return ConditionalRequirement based on skill level
     */
    public static ConditionalRequirement createLevelBasedRequirement(Skill skill, int requiredLevel, 
                                                                   Requirement requirement, String description,
                                                                   RequirementPriority priority, TaskContext taskContext) {
        ConditionalRequirement levelBased = new ConditionalRequirement(
                priority, 8, "Level-based: " + description, taskContext, false
        );
        
        BooleanSupplier hasLevel = () ->Rs2Player.getRealSkillLevel(skill) >= requiredLevel;
        
        levelBased.addStep(hasLevel, requirement, 
                description + " (requires " + skill.getName() + " level " + requiredLevel + ")", true);
        
        return levelBased;
    }
    
    // Helper methods for common conditions
    private static boolean isCurrentSpellbook(Rs2Spellbook spellbook) {
        return Rs2Magic.isSpellbook(spellbook);      
    }
    
    private static boolean hasAnyItem(int[] itemIds) {
        for (int itemId : itemIds) {
            if (Rs2Inventory.hasItem(itemId)) {
                return true;
            }
        }
        return false;
    }
    
    private static boolean hasGP(int amount) {        return Rs2Inventory.hasItem(ItemID.COINS) &&
               Rs2Inventory.itemQuantity(ItemID.COINS) >= amount;
    }
}
