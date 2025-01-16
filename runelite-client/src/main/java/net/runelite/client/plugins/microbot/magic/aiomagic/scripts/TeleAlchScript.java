package net.runelite.client.plugins.microbot.magic.aiomagic.scripts;

import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
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
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class TeleAlchScript extends Script {

    private MagicState state;
    private final AIOMagicPlugin plugin;
    private boolean initialSwap = false;

    @Inject
    public TeleAlchScript(AIOMagicPlugin plugin) {
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
        Rs2Antiban.setActivity(Activity.TELEPORT_TRAINING);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

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

                        if (!Rs2Equipment.hasEquipped(plugin.getStaff().getItemID())) {
                            if (!Rs2Bank.hasItem(plugin.getStaff().getItemID())) {
                                Microbot.showMessage("Configured Staff not found!");
                                shutdown();
                                return;
                            }

                            Rs2Bank.withdrawAndEquip(plugin.getStaff().getItemID());
                        }

                        if (!Rs2Magic.hasRequiredRunes(plugin.getTeleportSpell().getRs2Spell(), false)) {
                            Map<Runes, Integer> requiredTeleportRunes = new HashMap<>(plugin.getTeleportSpell().getRs2Spell().getRequiredRunes());

                            requiredTeleportRunes.forEach((rune, quantity) -> {
                                if (!isRunning()) return;
                                int itemID = rune.getItemId();

                                if (!Rs2Bank.hasItem(itemID)) {
                                    Microbot.showMessage("Missing Runes");
                                    shutdown();
                                    return;
                                }

                                if (!Rs2Bank.withdrawAll(itemID)) {
                                    Microbot.log("Failed to withdraw " + rune.name());
                                }

                                Rs2Inventory.waitForInventoryChanges(1200);
                            });
                        }

                        if (!Rs2Magic.hasRequiredRunes(plugin.getAlchSpell(), false)) {
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
                        }

                        Rs2Bank.closeBank();
                        sleepUntil(() -> !Rs2Bank.isOpen());
                        break;
                    case CASTING:
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

                        Rs2Magic.cast(plugin.getTeleportSpell().getRs2Spell().getAction());
                        Rs2Player.waitForAnimation(1200);

                        Rs2Magic.alch(alchItem);
                        Rs2Player.waitForXpDrop(Skill.MAGIC, false);
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
        if (state == MagicState.CASTING && (!Rs2Magic.hasRequiredRunes(plugin.getTeleportSpell().getRs2Spell(), false)
                || !Rs2Magic.hasRequiredRunes(plugin.getAlchSpell(), false))) return true;
        if (state == MagicState.BANKING && (Rs2Magic.hasRequiredRunes(plugin.getTeleportSpell().getRs2Spell(), false)
                && Rs2Magic.hasRequiredRunes(plugin.getAlchSpell(), false))) return true;
        return false;
    }

    private MagicState updateState() {
        if (state == null) return Rs2Magic.hasRequiredRunes(plugin.getTeleportSpell().getRs2Spell(), false)
                && Rs2Magic.hasRequiredRunes(plugin.getAlchSpell(), false) ? MagicState.CASTING : MagicState.BANKING;
        if (state == MagicState.CASTING && (!Rs2Magic.hasRequiredRunes(plugin.getTeleportSpell().getRs2Spell(), false)
                || !Rs2Magic.hasRequiredRunes(plugin.getAlchSpell(), false))) return MagicState.BANKING;
        if (state == MagicState.BANKING && (Rs2Magic.hasRequiredRunes(plugin.getTeleportSpell().getRs2Spell(), false)
                && Rs2Magic.hasRequiredRunes(plugin.getAlchSpell(), false))) return MagicState.CASTING;
        return null;
    }
}
