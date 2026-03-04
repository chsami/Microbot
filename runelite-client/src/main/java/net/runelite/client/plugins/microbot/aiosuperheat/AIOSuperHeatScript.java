package net.runelite.client.plugins.microbot.aiosuperheat;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.aiosuperheat.enums.SuperHeatItem;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;
import java.util.concurrent.TimeUnit;

public class AIOSuperHeatScript extends Script {

    public boolean run(AIOSuperHeatConfig config) {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2Antiban.setActivity(Activity.SUPERHEATING);
        Rs2AntibanSettings.universalAntiban = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn())
                    return;
                if (!super.run())
                    return;

                SuperHeatItem item = config.itemToSuperHeat();
                if (item == null)
                    return;

                int magicLevel = Rs2Player.getRealSkillLevel(Skill.MAGIC);
                int smithingLevel = Rs2Player.getRealSkillLevel(Skill.SMITHING);
                if (magicLevel == 0 || smithingLevel == 0) {
                    return; // Wait for skill data to load
                }

                if (magicLevel < 43 || smithingLevel < item.getRequiredLevel()) {
                    Microbot.showMessage("You do not have the required level for this item");
                    shutdown();
                    return;
                }

                if (needsBanking(item)) {
                    handleBanking(item);
                } else {
                    handleCasting(item);
                }

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 800, TimeUnit.MILLISECONDS);

        return true;
    }

    private boolean needsBanking(SuperHeatItem item) {
        if (!Rs2Inventory.hasItem(item.getItemID()))
            return true;
        if (item.getSecondaryItemID() != -1
                && !Rs2Inventory.hasItemAmount(item.getSecondaryItemID(), item.getSecondaryAmount()))
            return true;
        if (!Rs2Inventory.hasItem(ItemID.NATURERUNE))
            return true;
        return false;
    }

    private void handleBanking(SuperHeatItem item) {
        if (!Rs2Bank.isOpen()) {
            boolean isBankOpen = Rs2Bank.isNearBank(15) ? Rs2Bank.openBank() : Rs2Bank.walkToBankAndUseBank();
            if (!isBankOpen || !Rs2Bank.isOpen())
                return;
        }

        // Deposit everything except nature runes
        if (Rs2Inventory.all().stream().anyMatch(i -> i != null && (int) i.getId() != ItemID.NATURERUNE)) {
            Rs2Bank.depositAllExcept(ItemID.NATURERUNE);
            Rs2Inventory.waitForInventoryChanges(2000);
            Rs2Antiban.actionCooldown();
        }

        // Check for Fire Staff
        List<Rs2Staff> staffList = Rs2Magic.findStavesByRunes(List.of(Runes.FIRE));
        boolean hasFireStaffEquipped = staffList.stream()
                .map(Rs2Staff::getItemID)
                .anyMatch(Rs2Equipment::isWearing);

        if (!hasFireStaffEquipped) {
            Rs2ItemModel staffItem = Rs2Bank.bankItems().stream()
                    .filter(rs2Item -> staffList.stream()
                            .map(Rs2Staff::getItemID)
                            .anyMatch(id -> id == rs2Item.getId()))
                    .findFirst()
                    .orElse(null);

            if (staffItem != null) {
                if (Rs2Bank.withdrawAndEquip(staffItem.getId())) {
                    sleepUntil(() -> Rs2Equipment.isWearing(staffItem.getId()), 2000);
                    Rs2Antiban.actionCooldown();
                }
            }
        }

        // Withdraw Nature Runes if needed
        if (!Rs2Inventory.hasItem(ItemID.NATURERUNE)) {
            if (Rs2Bank.hasItem(ItemID.NATURERUNE)) {
                Rs2Bank.withdrawAll(ItemID.NATURERUNE);
                Rs2Inventory.waitForInventoryChanges(2000);
                Rs2Antiban.actionCooldown();
            } else {
                Microbot.showMessage("Out of Nature Runes!");
                shutdown();
                return;
            }
        }

        // Calculate and withdraw Ore and Secondary Ingredient
        int emptySlots = Rs2Inventory.emptySlotCount();
        if (emptySlots > 0) {
            int secondaryAmount = item.getSecondaryAmount();
            int sets = (secondaryAmount == 0) ? emptySlots : emptySlots / (secondaryAmount + 1);

            if (sets > 0) {
                // Withdraw Ore
                if (Rs2Bank.hasBankItem(item.getItemID(), sets)) {
                    Rs2Bank.withdrawX(item.getItemID(), sets);
                    Rs2Inventory.waitForInventoryChanges(2000);
                    Rs2Antiban.actionCooldown();
                } else {
                    Microbot.showMessage("Out of ores!");
                    shutdown();
                    return;
                }

                // Withdraw Secondary Ingredient if needed
                if (item.getSecondaryItemID() != -1) {
                    int secondaryToWithdraw = sets * item.getSecondaryAmount();
                    if (Rs2Bank.hasBankItem(item.getSecondaryItemID(), secondaryToWithdraw)) {
                        Rs2Bank.withdrawX(item.getSecondaryItemID(), secondaryToWithdraw);
                        Rs2Inventory.waitForInventoryChanges(2000);
                        Rs2Antiban.actionCooldown();
                    } else {
                        Microbot.showMessage("Out of secondary ingredients!");
                        shutdown();
                        return;
                    }
                }
            }
        }

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen(), 2000);
    }

    private void handleCasting(SuperHeatItem item) {
        if (Rs2Inventory.hasItem(ItemID.NATURERUNE)) {
            Rs2Magic.superHeat(item.getItemID());
            if (Rs2Player.waitForXpDrop(Skill.MAGIC, 10000, false)) {
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
        }
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
