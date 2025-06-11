package net.runelite.client.plugins.microbot.crafting.jewelry;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.crafting.jewelry.enums.*;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2RunePouch;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

public class JewelryScript extends Script {

    private final JewelryPlugin plugin;
    public static State state;
    private int staffItemID;

    @Inject
    public JewelryScript(JewelryPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCraftingSetup();
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2Walker.disableTeleports = true;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();
                
                if (hasStateChanged()) {
                    state = updateState();
                }
                
                if (state == null) return; // Used to switch into completion action 
                
                if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Rs2Antiban.getCategory().isBusy() || Microbot.pauseAllScripts) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                
                switch (state) {
                    case CUTTING:
                        Rs2Inventory.combineClosest(ItemID.CHISEL, plugin.getJewelry().getGem().getUncutItemID());
                        Rs2Dialogue.sleepUntilHasCombinationDialogue();
                        Rs2Dialogue.clickCombinationOption(plugin.getJewelry().getGem().getUncutItemName());
                        sleepUntil(Rs2Player::isAnimating);
                        Rs2Antiban.actionCooldown();
                        Rs2Antiban.takeMicroBreakByChance();
                        Rs2Bank.preHover();
                        break;
                    case BANKING:
                        boolean isBankOpen = Rs2Bank.isNearBank(plugin.getCraftingLocation().getBankLocation(),15) ? Rs2Bank.useBank() : Rs2Bank.walkToBankAndUseBank();
                        
                        if (!isBankOpen || !Rs2Bank.isOpen()) return;
                        
                        boolean shouldCutGems = plugin.getJewelry().getGem() != Gem.NONE 
                                && Rs2Bank.hasItem(plugin.getJewelry().getGem().getUncutItemID());
                        
                        if (shouldCutGems) {
                            if (!Rs2Inventory.isEmpty()) {
                                Rs2Bank.depositAllExcept(ItemID.CHISEL);
                                Rs2Inventory.waitForInventoryChanges(1800);
                            }
                            
                            if (!Rs2Inventory.hasItem(ItemID.CHISEL)) {
                                if (!Rs2Bank.hasItem(ItemID.CHISEL)) {
                                    Microbot.showMessage("Missing Chisel in Bank!");
                                    shutdown();
                                    return;
                                }
                                Rs2Bank.withdrawOne(ItemID.CHISEL);
                                Rs2Inventory.waitForInventoryChanges(1800);
                            }
                            
                            Rs2Bank.withdrawAll(plugin.getJewelry().getGem().getUncutItemID());
                            Rs2Inventory.waitForInventoryChanges(1800);
                            Rs2Bank.closeBank();
                            sleepUntil(() -> !Rs2Bank.isOpen());
                            return;
                        }

                        int withdrawAmount = plugin.getJewelry().getGem() != Gem.NONE ? 13 : 27;
                        
                        boolean shouldCraftJewelry = plugin.getJewelry().getGem() != Gem.NONE 
                                ? Rs2Bank.hasBankItem(plugin.getJewelry().getGem().getCutItemID(), withdrawAmount) && Rs2Bank.hasBankItem(plugin.getJewelry().getJewelryType().getItemID(), withdrawAmount)
                                : Rs2Bank.hasBankItem(plugin.getJewelry().getJewelryType().getItemID(), withdrawAmount);
                        
                        if (shouldCraftJewelry) {
                            if (!Rs2Inventory.isEmpty()){
                                Rs2Bank.depositAllExcept(plugin.getJewelry().getToolItemID());
                                Rs2Inventory.waitForInventoryChanges(1800);
                            }
                            
                            if (!Rs2Inventory.hasItem(plugin.getJewelry().getToolItemID())) {
                                if (!Rs2Bank.hasItem(plugin.getJewelry().getToolItemID())) {
                                    Microbot.showMessage("Missing tool item");
                                    shutdown();
                                    return;
                                }
                                Rs2Bank.withdrawOne(plugin.getJewelry().getToolItemID());
                                Rs2Inventory.waitForInventoryChanges(1800);
                            }
                            
                            if (plugin.getJewelry().getGem() != null) {
                                Rs2Bank.withdrawX(plugin.getJewelry().getGem().getCutItemID(), withdrawAmount);
                                Rs2Inventory.waitForInventoryChanges(1800);
                            }
                            
                            Rs2Bank.withdrawX(plugin.getJewelry().getJewelryType().getItemID(), withdrawAmount);
                            Rs2Inventory.waitForInventoryChanges(1800);
                            Rs2Bank.closeBank();
                            sleepUntil(() -> !Rs2Bank.isOpen());
                            return;
                        }
                        
                        switch (plugin.getCompletionAction()) {
//                            case ENCHANT:
//                                if (!Rs2Inventory.isEmpty()) {
//                                    Rs2Bank.depositAllExcept(false,"coins", "rune", plugin.getJewelry().getItemName());
//                                    Rs2Random.waitEx(900, 100);
//                                }
//                                if (plugin.getStaff().equals(Staff.NONE)) {
//                                    staffItemID = findSuitableStaff(plugin.getJewelry().getEnchantSpell());
//
//                                    if (staffItemID == -1) {
//                                        Microbot.showMessage("No suitable staff found for enchanting!");
//                                        shutdown();
//                                        return;
//                                    }
//                                } else {
//                                    staffItemID = plugin.getStaff().getItemID();
//                                }
//
//                                // Withdraw and equip the staff if needed
//                                if (!Rs2Equipment.hasEquipped(staffItemID)) {
//                                    Rs2Bank.withdrawOne(staffItemID);
//                                    Rs2Random.waitEx(1200, 300);
//                                    Rs2Bank.wearItem(staffItemID);
//                                    Rs2Random.waitEx(1200, 300);
//                                }
//
//                                // Calculate total jewelry
//                                int enchantJewelryInInventory = getNotedJewelryInInventory();
//                                int enchantJewelryInBank = Rs2Bank.bankItems().stream()
//                                        .filter(item -> item.getId() == plugin.getJewelry().getItemID())
//                                        .mapToInt(Rs2Item::getQuantity)
//                                        .sum();
//
//                                int totalEnchantJewelry = enchantJewelryInInventory + enchantJewelryInBank;
//                                
//
//                                Map<Integer, Integer> runesNeeded = calculateRunesNeeded(plugin.getJewelry().getEnchantSpell(), totalEnchantJewelry);
//                                if (!runesNeeded.isEmpty()) {
//                                    for (Map.Entry<Integer, Integer> entry : runesNeeded.entrySet()) {
//                                        int runeID = entry.getKey();
//                                        int quantityNeeded = entry.getValue();
//
//                                        if (Rs2Bank.hasBankItem(runeID, quantityNeeded)) {
//                                            Rs2Bank.withdrawX(runeID, quantityNeeded);
//                                            Rs2Random.waitEx(900, 100);
//                                        } else {
//                                            Microbot.showMessage("Missing required rune: " + runeID);
//                                            shutdown();
//                                            return;
//                                        }
//                                    }
//                                }
//
//                                // Withdraw all jewelry and remaining nature runes
//                                if (totalEnchantJewelry > 0) {
//                                    Rs2Bank.withdrawAll(plugin.getJewelry().getItemID());
//                                    Rs2Random.waitEx(900, 100);
//                                }
//                                
//                                Rs2Bank.closeBank();
//                                sleepUntil(() -> !Rs2Bank.isOpen());
//                                break;
//                                
                            case ALCH:
                                // Find the required staff
                                if (staffItemID == -1 || staffItemID == 0) {
                                    staffItemID = plugin.getStaff() != Staff.NONE ? plugin.getStaff().getItemID() : findSuitableFireStaff();
                                    
                                    if (staffItemID == -1) {
                                        Microbot.showMessage("No staff of fire-type found!");
                                        shutdown();
                                        return;
                                    }
                                }
                                
                                // Calculate total jewelry
                                int alchJewelryInInventory  = getNotedJewelryInInventory();
                                int alchJewelryInBank  = Rs2Bank.bankItems().stream()
                                        .filter(item -> item.getId() == plugin.getJewelry().getItemID())
                                        .mapToInt(Rs2ItemModel::getQuantity)
                                        .sum();
                                
                                int totalAlchJewelry = alchJewelryInInventory + alchJewelryInBank;
                                
                                if (totalAlchJewelry == 0) {
                                    Microbot.showMessage("All Jewelry has been alched!");
                                    shutdown();
                                    return;
                                }

                                // Calculate total nature runes
                                int natureRunesInInventory = getNatureRunesInInventory();
                                int natureRunesInBank = Rs2Bank.bankItems().stream()
                                        .filter(item -> item.getId() == ItemID.NATURE_RUNE)
                                        .mapToInt(Rs2ItemModel::getQuantity)
                                        .sum();
                                
                                int totalNatureRunes = natureRunesInInventory + natureRunesInBank;

                                // Check if required items are available
                                if (totalNatureRunes < totalAlchJewelry || (!Rs2Bank.hasItem(staffItemID) && !Rs2Equipment.hasEquipped(staffItemID))) {
                                    Microbot.showMessage("Missing required items");
                                    shutdown();
                                    return;
                                }
                                
                                if (Rs2Inventory.hasItem(plugin.getJewelry().getItemID())) {
                                    Rs2Bank.depositAll(plugin.getJewelry().getItemID());
                                    Rs2Inventory.waitForInventoryChanges(1800);
                                }
                                
                                if (!Rs2Inventory.isEmpty()) {
                                    Rs2Bank.depositAllExcept(false,"coins", "rune", plugin.getJewelry().getItemName());
                                    Rs2Inventory.waitForInventoryChanges(1800);
                                }

                                // Withdraw and equip the staff if needed
                                if (!Rs2Equipment.hasEquipped(staffItemID)) {
                                    Rs2Bank.withdrawOne(staffItemID);
                                    Rs2Inventory.waitForInventoryChanges(1800);
                                    Rs2Inventory.equip(staffItemID);
                                }


                                // Withdraw all jewelry and remaining nature runes
                                if (totalAlchJewelry > 0) {
                                    Rs2Bank.setWithdrawAsNote();
                                    Rs2Bank.withdrawAll(plugin.getJewelry().getItemID());
                                    Rs2Inventory.waitForInventoryChanges(1800);
                                    Rs2Bank.setWithdrawAsItem();
                                }

                                int natureRunesToWithdraw = totalAlchJewelry - natureRunesInInventory;

                                if (plugin.isUseRunePouch()) { // Check if the rune pouch is not already in the inventory
                                    if (!Rs2Inventory.hasRunePouch()) { 
                                        if (!Rs2Bank.hasRunePouch()) {
                                            Microbot.showMessage("Rune Pouch not found! Check your magic settings");
                                            shutdown();
                                            return;
                                        }
                                        
                                        Rs2Bank.withdrawRunePouch();
                                        Rs2Inventory.waitForInventoryChanges(1800);
                                    }
                                } else {
                                    // Otherwise, withdraw nature runes based on the needed amount
                                    if (natureRunesInInventory == 0 && natureRunesToWithdraw > 0) {
                                        Rs2Bank.withdrawAll(ItemID.NATURE_RUNE);
                                        Rs2Inventory.waitForInventoryChanges(1800);
                                    } else if (natureRunesToWithdraw > 0) {
                                        Rs2Bank.withdrawX(ItemID.NATURE_RUNE, natureRunesToWithdraw);
                                        Rs2Inventory.waitForInventoryChanges(1800);
                                    }
                                }

                                Rs2Bank.closeBank();
                                sleepUntil(() -> !Rs2Bank.isOpen());
                                break;
                            case NONE:
                                if (!Rs2Inventory.isEmpty()) {
                                    Rs2Bank.depositAll();
                                }
                                Microbot.showMessage("Crafting has been completed!");
                                shutdown();
                                return;
                        }
                        
                        break;
                    case CRAFTING:
                        TileObject furnaceObject = Rs2GameObject.findObjectById(plugin.getCraftingLocation().getFurnanceObjectID());
                        
                        if (furnaceObject == null) {
                            Rs2Walker.walkTo(plugin.getCraftingLocation().getFurnaceLocation());
                            return;
                        }

                        if (!Rs2Camera.isTileOnScreen(furnaceObject.getLocalLocation())) {
                            Rs2Camera.turnTo(furnaceObject.getLocalLocation());
                            return;
                        }

                        Rs2GameObject.interact(furnaceObject, "smelt");
                        sleepUntilTrue(() -> Rs2Widget.isGoldCraftingWidgetOpen() || Rs2Widget.isSilverCraftingWidgetOpen(), 500, 20000);
                        Rs2Widget.clickWidget(plugin.getJewelry().getItemName());
                        Rs2Antiban.actionCooldown();
                        Rs2Antiban.takeMicroBreakByChance();
                        break;
                    case ALCHING:
                        if (!Rs2Equipment.hasEquipped(staffItemID)) {
                            Rs2Inventory.equip(staffItemID);
                        }
                        
                        Rs2ItemModel notedJewelry = Rs2Inventory.get(plugin.getJewelry().getItemID() + 1);
                        if (notedJewelry.getSlot() != 11) {
                            Rs2Inventory.moveItemToSlot(notedJewelry, 11);
                            return;
                        }
                        
                        Rs2Magic.alch(notedJewelry);
                        Rs2Player.waitForXpDrop(Skill.MAGIC, 10000);
                        Rs2Antiban.actionCooldown();
                        break;
//                    case ENCHANTING:
//                        if (!Rs2Equipment.hasEquipped(staffItemID)) {
//                            Rs2Inventory.equip(staffItemID);
//                            Rs2Random.waitEx(900, 100);
//                        }
//                        
//                        Rs2Magic.cast(plugin.getJewelry().getEnchantSpell().getMagicAction());
//                        Rs2Inventory.interact(plugin.getJewelry().getItemName());
//                        Rs2Antiban.actionCooldown();
//                        sleepUntil(() -> !Rs2Inventory.hasItem(plugin.getJewelry().getItemName()), 30000);
//                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
        state = null;
    }
    
    private boolean hasStateChanged() {
        // If state is null (on plugin start) OR switch to completion action
        if (state == null) return true;
        // If the player should bank (has finished cutting gems, crafting jewelry, alching, or enchanting)
        if (state != State.BANKING && shouldBank()) return true;
        if (state == State.BANKING && isCutting()) return true;
        if (state == State.BANKING && isCrafting()) return true;
        switch (plugin.getCompletionAction()) {
            case NONE:
//            case ENCHANT:
//                return state == State.BANKING && isEnchanting();
            case ALCH:
                return state == State.BANKING && isAlching();
        }
        return false;
    }
    
    private State updateState() {
        if (shouldBank()) return State.BANKING;
        if (isCutting()) return State.CUTTING;
        if (isCrafting()) return State.CRAFTING;
        if (state == null) {
            switch (plugin.getCompletionAction()) {
                case NONE:
//                case ENCHANT:
//                    if (isEnchanting()) return State.ENCHANTING;
//                    return State.BANKING;
                case ALCH:
                    if (isAlching()) return State.ALCHING;
                    return State.BANKING;
            }
        }
        return null;
    }
    
    private boolean shouldBank() {
        return hasFinishedCutting() || hasFinishedCrafting() || hasFinishedAlching() || hasFinishedEnchanting();
    }
    
    private boolean hasFinishedCutting() {
        if (plugin.getJewelry().getGem() == Gem.NONE) return false;
        if(!Rs2Inventory.hasItem(ItemID.CHISEL)) return false;
        return Rs2Inventory.hasItem(plugin.getJewelry().getGem().getCutItemID()) && !Rs2Inventory.hasItem(plugin.getJewelry().getGem().getUncutItemID());
    }
    
    private boolean hasFinishedCrafting() {
        if (!Rs2Inventory.hasItem(plugin.getJewelry().getToolItemID())) return false;

        boolean hasCraftingItem = Rs2Inventory.hasItem(plugin.getJewelry().getJewelryType().getItemID());
        boolean hasNoGem = plugin.getJewelry().getGem() == Gem.NONE;
        boolean hasCutGem = Rs2Inventory.hasItem(plugin.getJewelry().getGem().getCutItemID());

        return Rs2Inventory.hasItem(plugin.getJewelry().getItemID()) 
                && hasNoGem ? !hasCraftingItem : !hasCraftingItem && !hasCutGem;
    }
    
    private boolean hasFinishedEnchanting() {
        return Rs2Equipment.hasEquipped(staffItemID) && !Rs2Inventory.hasItem(plugin.getJewelry().getItemName());
    }
    
    private boolean isCutting() {
        if (plugin.getJewelry().getGem() == Gem.NONE) return false;
        if (!Rs2Inventory.hasItem(ItemID.CHISEL)) return false;
        return Rs2Inventory.hasItem(plugin.getJewelry().getGem().getUncutItemID());
    }
    
    private boolean isCrafting() {
        if(!Rs2Inventory.hasItem(plugin.getJewelry().getToolItemID())) return false;
        
        boolean hasCraftingItem = Rs2Inventory.hasItem(plugin.getJewelry().getJewelryType().getItemID());
        boolean hasNoGem = plugin.getJewelry().getGem() == Gem.NONE;
        boolean hasCutGem = Rs2Inventory.hasItem(plugin.getJewelry().getGem().getCutItemID());
        
        return hasNoGem ? hasCraftingItem : hasCraftingItem && hasCutGem;
    }
    
    private boolean isAlching() {
        if (!plugin.getCompletionAction().equals(CompletionAction.ALCH)) return false;
        if (!Rs2Equipment.hasEquippedSlot(EquipmentInventorySlot.WEAPON)) return false;

        staffItemID = plugin.getStaff() != Staff.NONE ? plugin.getStaff().getItemID() : findSuitableFireStaff();

        if (staffItemID == -1) return false; // No Fire Rune Staff equipped
        
        // Check if the noted jewelry item is in the inventory
        if (!Rs2Inventory.hasItem(plugin.getJewelry().getItemID() + 1)) return false;
        
        int notedJewelryAmount = Rs2Inventory.get(plugin.getJewelry().getItemID() + 1).getQuantity();
        int natureRuneAmount = getNatureRunesInInventory();
        
        return notedJewelryAmount <= natureRuneAmount || (plugin.isUseRunePouch() && !Rs2Inventory.hasRunePouch());
    }
    
    private boolean hasFinishedAlching() {
        return Rs2Equipment.hasEquipped(staffItemID) && !Rs2Inventory.hasItem(plugin.getJewelry().getItemID() + 1);
    }
    
    private int getNatureRunesInInventory() {
        return plugin.isUseRunePouch() ? Rs2RunePouch.getQuantity(ItemID.NATURE_RUNE) : Rs2Inventory.items().stream()
                .filter(item -> item.getId() == ItemID.NATURE_RUNE)
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }
    
    private int getNotedJewelryInInventory() {
        return Rs2Inventory.items().stream()
                .filter(item -> item.getId() == (plugin.getJewelry().getItemID() + 1))
                .mapToInt(Rs2ItemModel::getQuantity)
                .sum();
    }

//    private boolean isEnchanting() {
//        if (!plugin.getCompletionAction().equals(CompletionAction.ENCHANT)) return false;
//
//        staffItemID = findSuitableStaff(plugin.getJewelry().getEnchantSpell());
//        if (staffItemID == -1 || (!Rs2Equipment.hasEquipped(staffItemID) && !Rs2Bank.hasItem(staffItemID))) return false;
//        
//        if (!Rs2Inventory.hasItem(plugin.getJewelry().getItemID())) return false;
//        
//        int totalJewelry = Rs2Inventory.get(plugin.getJewelry().getItemID()).quantity;
//        Map<Integer, Integer> runesNeeded = calculateRunesNeeded(plugin.getJewelry().getEnchantSpell(), totalJewelry);
//
//        return runesNeeded.isEmpty();
//    }

    private int findSuitableStaff(EnchantSpell enchantSpell) {
        List<Integer> requiredRuneIDs = getRequiredRuneIDs(plugin.getJewelry().getEnchantSpell());

        // Find a staff that provides any of the required runes
        return Stream.of(Staff.values())
                .filter(staff -> !staff.equals(Staff.NONE)) // Skip NONE
                .filter(staff -> staff.getRuneItemIDs().stream().anyMatch(requiredRuneIDs::contains))
                .map(Staff::getItemID)
                .filter(itemID -> Rs2Inventory.hasItem(itemID) || Rs2Equipment.hasEquipped(itemID) || Rs2Bank.hasItem(itemID))
                .findFirst()
                .orElse(-1); // Return -1 if no staff is found
    }
    
    private List<Integer> getRequiredRuneIDs(EnchantSpell enchantSpell) {
        Set<Map<Integer, Integer>> requiredRunes = enchantSpell.getRequiredRunes();
        
        return requiredRunes.stream()
                .flatMap(map -> map.keySet().stream())
                .distinct()
                .collect(Collectors.toList());
    }

    private int findSuitableFireStaff() {
        return Staff.getFireRuneStaffs().stream()
                .map(Staff::getItemID)
                .filter(itemID -> Rs2Inventory.hasItem(itemID) || Rs2Equipment.hasEquipped(itemID))
                .findFirst()
                .orElseGet(() -> {
                    int[] fireRuneStaffIDs = Staff.getFireRuneStaffs().stream()
                            .mapToInt(Staff::getItemID)
                            .toArray();
                    return Rs2Bank.hasItem(fireRuneStaffIDs)
                            ? Arrays.stream(fireRuneStaffIDs).filter(Rs2Bank::hasItem).findFirst().orElse(-1)
                            : -1;
                });
    }

    /**
     * Calculates the number of each rune needed for the given spell.
     *
     * @param enchantSpell The spell requiring runes.
     * @param totalJewelry The total amount of jewelry that needs to be processed.
     * @return A map where the key is the rune ID and the value is the additional runes needed.
     */
    private Map<Integer, Integer> calculateRunesNeeded(EnchantSpell enchantSpell, int totalJewelry) {
        // Step 1: Aggregate required runes based on the number of casts
        Map<Integer, Integer> requiredRunes = enchantSpell.getRequiredRunes().stream()
                .flatMap(map -> map.entrySet().stream())
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue() * totalJewelry, Integer::sum));

        // Step 2: Adjust for runes provided by the equipped staff
        if (staffItemID != -1) {
            Staff equippedStaff = Staff.getByItemID(staffItemID);
            if (equippedStaff != null) {
                for (int runeID : equippedStaff.getRuneItemIDs()) {
                    requiredRunes.remove(runeID); // Staff provides this rune
                }
            }
        }

        // Step 3: Count available runes from inventory & rune pouch
        Map<Integer, Integer> availableRunes = new HashMap<>();

        // Inventory
        Rs2Inventory.items().stream()
                .filter(item -> requiredRunes.containsKey(item.getId()))
                .forEach(item -> availableRunes.merge(item.getId(), item.getQuantity(), Integer::sum));

        // Rune Pouch
        if (plugin.isUseRunePouch()) {
            Rs2RunePouch.getRunes().forEach((runeID, quantity) -> {
                if (requiredRunes.containsKey(runeID)) {
                    availableRunes.merge(runeID, quantity, Integer::sum);
                }
            });
        }

        // Step 4: Calculate the deficit
        Map<Integer, Integer> runesNeeded = new HashMap<>();
        requiredRunes.forEach((runeID, totalRequired) -> {
            int available = availableRunes.getOrDefault(runeID, 0);
            int deficit = Math.max(totalRequired - available, 0);
            if (deficit > 0) {
                runesNeeded.put(runeID, deficit);
            }
        });

        return runesNeeded;
    }
}
