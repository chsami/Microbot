package net.runelite.client.plugins.microbot.magic.aiomagic.scripts;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.magic.aiomagic.AIOMagicPlugin;
import net.runelite.client.plugins.microbot.magic.aiomagic.enums.MagicState;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Item;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

import javax.inject.Inject;
import java.util.List;
import java.util.concurrent.TimeUnit;

public class AlchScript extends Script {

    private MagicState state;
    private final AIOMagicPlugin plugin;
    private boolean initialSwap = false;

    @Inject
    public AlchScript(AIOMagicPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2Antiban.setActivity(Activity.ALCHING);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                if (plugin.getAlchItemNames().isEmpty()) {
                    Microbot.showMessage("Alch Item list is empty");
                    shutdown();
                    return;
                }

                if (hasStateChanged()) {
                    state = updateState();
                }

                if (state == null) {
                    Microbot.showMessage("Unable to evaluate state");
                    shutdown();
                    return;
                }

                switch (state) {
                    case BANKING:
                        boolean isBankOpen = Rs2Bank.isNearBank(15) ? Rs2Bank.useBank() : Rs2Bank.walkToBankAndUseBank();
                        if (!isBankOpen || !Rs2Bank.isOpen()) return;

                        Rs2Bank.depositAllExcept(ItemID.NATURE_RUNE);
                        Rs2Inventory.waitForInventoryChanges(1200);

                        List<Rs2Staff> staffList = Rs2Magic.findStavesByRunes(List.of(Runes.FIRE));

                        boolean hasFireStaffEquipped = staffList.stream()
                                .map(Rs2Staff::getItemID)
                                .anyMatch(Rs2Equipment::hasEquipped);

                        if (!hasFireStaffEquipped) {
                            Rs2Item staffItem = Rs2Bank.bankItems().stream()
                                    .filter(rs2Item -> staffList.stream()
                                            .map(Rs2Staff::getItemID)
                                            .anyMatch(id -> id == rs2Item.getId()))
                                    .findFirst()
                                    .orElse(null);

                            if (staffItem == null) {
                                Microbot.showMessage("Unable to find staff");
                                shutdown();
                                return;
                            }

                            Rs2Bank.withdrawAndEquip(staffItem.getId());
                        }

                        if (!Rs2Inventory.hasItem(ItemID.NATURE_RUNE)) {
                            if (!Rs2Bank.hasItem(ItemID.NATURE_RUNE)) {
                                Microbot.showMessage("Nature Runes not found");
                                shutdown();
                                return;
                            }

                            Rs2Bank.withdrawAll(ItemID.NATURE_RUNE);
                            Rs2Inventory.waitForInventoryChanges(1200);
                        }

                        if (plugin.getAlchItemNames().stream()
                                .noneMatch(itemName -> Rs2Bank.hasItem(itemName) || Rs2Inventory.hasItem(itemName))) {
                            Microbot.showMessage("No Alch Items Found");
                            shutdown();
                            return;
                        }
                        
                        plugin.getAlchItemNames().forEach((itemName) -> {
                            if (!isRunning()) return;
                            Rs2Item rs2Item = Rs2Bank.getBankItem(itemName);

                            if (!rs2Item.isStackable() && Rs2Bank.hasWithdrawAsItem()) {
                                Rs2Bank.setWithdrawAsNote();
                            } else if (rs2Item.isStackable() && Rs2Bank.hasWithdrawAsNote()) {
                                Rs2Bank.setWithdrawAsItem();
                            }

                            Rs2Bank.withdrawAll(itemName);
                            Rs2Inventory.waitForInventoryChanges(1200);
                        });

                        Rs2Bank.closeBank();
                        sleepUntil(() -> !Rs2Bank.isOpen());

                        if (Rs2Tab.getCurrentTab() != InterfaceTab.INVENTORY) {
                            Rs2Tab.switchToInventoryTab();
                        }

                        break;
                    case CASTING:
                        if (!Rs2Inventory.hasItem(ItemID.NATURE_RUNE)) {
                            Microbot.showMessage("Nature Runes not found");
                            shutdown();
                            return;
                        }
                        if (!Rs2Inventory.hasItem(plugin.getAlchItemNames().get(0))) {
                            plugin.getAlchItemNames().remove(0);
                            return;
                        }

                        Rs2Item alchItem = Rs2Inventory.get(plugin.getAlchItemNames().get(0));
                        int inventorySlot = Rs2Player.getRealSkillLevel(Skill.MAGIC) >= 55 ? 11 : 4;

                        if (!initialSwap && Rs2Inventory.getItemInSlot(inventorySlot) == null) {
                            Rs2Item natureRunes = Rs2Inventory.get(ItemID.NATURE_RUNE);
                            if (Rs2Random.dicePercentage(0.5)) {
                                Rs2Inventory.moveItemToSlot(natureRunes, alchItem.getSlot());
                            } else {
                                Rs2Inventory.moveItemToSlot(alchItem, natureRunes.getSlot());
                            }
                            initialSwap = true;
                            return;
                        }

                        if (alchItem.getSlot() != inventorySlot) {
                            Rs2Inventory.moveItemToSlot(alchItem, inventorySlot);
                            return;
                        }

                        Rs2Magic.alch(alchItem);
                        Rs2Player.waitForXpDrop(Skill.MAGIC, 10000, false);
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        initialSwap = false;
        super.shutdown();
    }

    private boolean hasStateChanged() {
        if (state == null) return true;
        if (state == MagicState.BANKING && hasRequiredItems()) return true;
        if (state == MagicState.CASTING && !hasRequiredItems()) return true;
        return false;
    }

    private MagicState updateState() {
        if (state == null) return hasRequiredItems() ? MagicState.CASTING : MagicState.BANKING;
        if (state == MagicState.BANKING && hasRequiredItems()) return MagicState.CASTING;
        if (state == MagicState.CASTING && !hasRequiredItems()) return MagicState.BANKING;
        return null;
    }

    private boolean hasRequiredItems() {
        Rs2Spells alchSpell = plugin.getAlchSpell();
        return Rs2Inventory.hasItem(plugin.getAlchItemNames()) && Rs2Magic.hasRequiredRunes(alchSpell, false);
    }
}
