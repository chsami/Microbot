package net.runelite.client.plugins.microbot.bga.autoherblore;

import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.bga.autoherblore.enums.Herb;
import net.runelite.client.plugins.microbot.bga.autoherblore.enums.Mode;
import net.runelite.client.plugins.microbot.bga.autoherblore.enums.HerblorePotion;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import java.util.concurrent.TimeUnit;

public class AutoHerbloreScript extends Script {

    private enum State { BANK, CLEAN, MAKE_UNFINISHED, MAKE_FINISHED }
    private State state;
    private Herb current;
    private Herb currentHerbForUnfinished;
    private HerblorePotion currentPotion;
    private boolean currentlyMakingPotions;
    private int withdrawnAmount;
    private AutoHerbloreConfig config;
    public boolean run(AutoHerbloreConfig config) {
        this.config = config;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyHerbloreSetup();
        state = State.BANK;
        current = null;
        currentHerbForUnfinished = null;
        currentPotion = null;
        currentlyMakingPotions = false;
        withdrawnAmount = 0;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                if (state == State.BANK) {
                    if (!Rs2Bank.isNearBank(10)) {
                        Rs2Bank.walkToBank();
                        return;
                    }
                    if (!Rs2Bank.openBank()) return;
                    Rs2Bank.depositAll();
                    Rs2Inventory.waitForInventoryChanges(1800);
                    if (config.mode() == Mode.CLEAN_HERBS) {
                        if (current == null || !Rs2Bank.hasItem(current.grimy)) current = findHerb();
                        if (current == null) {
                            Microbot.showMessage("No more herbs");
                            shutdown();
                            return;
                        }
                        Rs2Bank.withdrawX(current.grimy, 28);
                        Rs2Inventory.waitForInventoryChanges(1800);
                        Rs2Bank.closeBank();
                        state = State.CLEAN;
                        return;
                    }
                    if (config.mode() == Mode.UNFINISHED_POTIONS) {
                        if (currentHerbForUnfinished == null || (!Rs2Bank.hasItem(currentHerbForUnfinished.clean) || !Rs2Bank.hasItem(ItemID.VIAL_WATER))) {
                            currentHerbForUnfinished = findHerbForUnfinished();
                            if (currentHerbForUnfinished == null) {
                                Microbot.showMessage("No more herbs or vials of water");
                                shutdown();
                                return;
                            }
                        }
                        int herbCount = Rs2Bank.count(currentHerbForUnfinished.clean);
                        int vialCount = Rs2Bank.count(ItemID.VIAL_WATER);
                        withdrawnAmount = Math.min(Math.min(herbCount, vialCount), 14);
                        
                        Rs2Bank.withdrawX(currentHerbForUnfinished.clean, withdrawnAmount);
                        Rs2Bank.withdrawX(ItemID.VIAL_WATER, withdrawnAmount);
                        Rs2Inventory.waitForInventoryChanges(1800);
                        Rs2Bank.closeBank();
                        state = State.MAKE_UNFINISHED;
                        return;
                    }
                    if (config.mode() == Mode.FINISHED_POTIONS) {
                        if (currentPotion == null || !Rs2Bank.hasItem(currentPotion.unfinished) || !Rs2Bank.hasItem(currentPotion.secondary)) {
                            currentPotion = findPotion();
                            if (currentPotion == null) {
                                Microbot.showMessage("No more ingredients for selected potion");
                                shutdown();
                                return;
                            }
                        }
                        int unfinishedCount = Rs2Bank.count(currentPotion.unfinished);
                        int secondaryCount = Rs2Bank.count(currentPotion.secondary);
                        withdrawnAmount = Math.min(Math.min(unfinishedCount, secondaryCount), 14);
                        
                        Rs2Bank.withdrawX(currentPotion.unfinished, withdrawnAmount);
                        Rs2Bank.withdrawX(currentPotion.secondary, withdrawnAmount);
                        Rs2Inventory.waitForInventoryChanges(1800);
                        Rs2Bank.closeBank();
                        state = State.MAKE_FINISHED;
                        return;
                    }
                }
                if (config.mode() == Mode.CLEAN_HERBS && state == State.CLEAN) {
                    if (Rs2Inventory.hasItem("grimy")) {
                        Rs2Inventory.cleanHerbs(InteractOrder.ZIGZAG);
                        Rs2Inventory.waitForInventoryChanges(1800);
                        return;
                    }
                    state = State.BANK;
                }
                if (config.mode() == Mode.UNFINISHED_POTIONS && state == State.MAKE_UNFINISHED) {
                    if (currentlyMakingPotions) {
                        if (!Rs2Inventory.hasItem(currentHerbForUnfinished.clean) && !Rs2Inventory.hasItem(ItemID.VIAL_WATER)) {
                            currentlyMakingPotions = false;
                            state = State.BANK;
                            return;
                        }
                        return;
                    }
                    
                    if (Rs2Inventory.hasItem(currentHerbForUnfinished.clean) && Rs2Inventory.hasItem(ItemID.VIAL_WATER)) {
                        if (Rs2Inventory.combine(currentHerbForUnfinished.clean, ItemID.VIAL_WATER)) {
                            sleep(600, 800);
                            // Only press 1 if we're making more than 1 potion
                            if (withdrawnAmount > 1) {
                                Rs2Keyboard.keyPress('1');
                            }
                            currentlyMakingPotions = true;
                            return;
                        }
                    }
                    state = State.BANK;
                }
                if (config.mode() == Mode.FINISHED_POTIONS && state == State.MAKE_FINISHED) {
                    if (currentlyMakingPotions) {
                        if (!Rs2Inventory.hasItem(currentPotion.unfinished) && !Rs2Inventory.hasItem(currentPotion.secondary)) {
                            currentlyMakingPotions = false;
                            state = State.BANK;
                            return;
                        }
                        return;
                    }
                    
                    if (Rs2Inventory.hasItem(currentPotion.unfinished) && Rs2Inventory.hasItem(currentPotion.secondary)) {
                        if (Rs2Inventory.combine(currentPotion.unfinished, currentPotion.secondary)) {
                            sleep(600, 800);
                            // Only press 1 if we're making more than 1 potion
                            if (withdrawnAmount > 1) {
                                Rs2Keyboard.keyPress('1');
                            }
                            currentlyMakingPotions = true;
                            return;
                        }
                    }
                    state = State.BANK;
                }
            } catch (Exception e) {
                Microbot.log(e.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }
    private Herb findHerb() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        for (Herb h : Herb.values()) if (level >= h.level && Rs2Bank.hasItem(h.grimy)) return h;
        return null;
    }
    private Herb findHerbForUnfinished() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        // Find any herb we can make unfinished potions with, starting from the lowest level herb
        for (Herb h : Herb.values()) {
            if (level >= h.level && Rs2Bank.hasItem(h.clean) && Rs2Bank.hasItem(ItemID.VIAL_WATER)) {
                return h;
            }
        }
        return null;
    }
    private HerblorePotion findPotion() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        HerblorePotion selectedPotion = config.potion();
        if (selectedPotion != null && level >= selectedPotion.level && Rs2Bank.hasItem(selectedPotion.unfinished) && Rs2Bank.hasItem(selectedPotion.secondary)) {
            return selectedPotion;
        }
        return null;
    }
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }
}
