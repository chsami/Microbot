package net.runelite.client.plugins.microbot.bga.autoherblore;

import lombok.Setter;
import net.runelite.api.Skill;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.bga.autoherblore.enums.CleanHerbMode;
import net.runelite.client.plugins.microbot.bga.autoherblore.enums.Herb;
import net.runelite.client.plugins.microbot.bga.autoherblore.enums.HerblorePotion;
import net.runelite.client.plugins.microbot.bga.autoherblore.enums.Mode;
import net.runelite.client.plugins.microbot.bga.autoherblore.enums.UnfinishedPotionMode;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import java.util.concurrent.TimeUnit;

public class AutoHerbloreScript extends Script {

    public static String version = "1.0";

    private enum State {
        BANK,
        CLEAN,
        MAKE_UNFINISHED,
        MAKE_FINISHED
    }
    private State state;
    private Herb current;
    private Herb currentHerbForUnfinished;
    private HerblorePotion currentPotion;
    private boolean currentlyMakingPotions;
    private int withdrawnAmount;
    private AutoHerbloreConfig config;
    @Setter
    private boolean amuletBroken = false;

    private boolean usesStackableSecondary(HerblorePotion potion) {
        return getStackableSecondaryRatio(potion) > 1;
    }

    private int getStackableSecondaryRatio(HerblorePotion potion) {
        if (potion.secondary == ItemID.PRIF_CRYSTAL_SHARD_CRUSHED) {
            return 4;
        } else if (potion.secondary == ItemID.SNAKEBOSS_SCALE) {
            return 20;
        } else if (potion.secondary == ItemID.LAVA_SHARD) {
            return 4;
        } else if (potion.secondary == ItemID.AMYLASE) {
            return 4;
        } else if (potion.secondary == ItemID.ARAXYTE_VENOM_SACK) {
            return 1; // Need to verify the correct ratio for this
        }
        return 1;
    }

    private boolean isSuperCombat(HerblorePotion potion) {
        return potion == HerblorePotion.SUPER_COMBAT;
    }
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
                        checkAndEquipAmulet();
                        
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

                        if (isSuperCombat(currentPotion)) {
                            int torstolCount = Rs2Bank.count(ItemID.TORSTOL);
                            int superAttackCount = Rs2Bank.count(ItemID._4DOSE2ATTACK);
                            int superStrengthCount = Rs2Bank.count(ItemID._4DOSE2STRENGTH);
                            int superDefenceCount = Rs2Bank.count(ItemID._4DOSE2DEFENSE);

                            withdrawnAmount = Math.min(Math.min(Math.min(Math.min(torstolCount, superAttackCount), superStrengthCount), superDefenceCount), 7);

                            Rs2Bank.withdrawX(ItemID.TORSTOL, withdrawnAmount);
                            Rs2Bank.withdrawX(ItemID._4DOSE2ATTACK, withdrawnAmount);
                            Rs2Bank.withdrawX(ItemID._4DOSE2STRENGTH, withdrawnAmount);
                            Rs2Bank.withdrawX(ItemID._4DOSE2DEFENSE, withdrawnAmount);
                        } else if (usesStackableSecondary(currentPotion)) {
                            int secondaryRatio = getStackableSecondaryRatio(currentPotion);
                            withdrawnAmount = Math.min(unfinishedCount, 27);
                            int secondaryNeeded = withdrawnAmount * secondaryRatio;

                            Microbot.log("Stackable secondary detected - Potion: " + currentPotion.name() + 
                                        ", Ratio: " + secondaryRatio + " per potion" +
                                        ", Potions to make: " + withdrawnAmount + 
                                        ", Total secondary needed: " + secondaryNeeded +
                                        ", Available secondary: " + secondaryCount);

                            if (secondaryCount < secondaryNeeded) {
                                withdrawnAmount = secondaryCount / secondaryRatio;
                                secondaryNeeded = withdrawnAmount * secondaryRatio;
                                Microbot.log("Adjusted due to insufficient secondary - New potion count: " + withdrawnAmount + 
                                           ", New secondary needed: " + secondaryNeeded);
                            }

                            Rs2Bank.withdrawX(currentPotion.unfinished, withdrawnAmount);
                            Rs2Bank.withdrawX(currentPotion.secondary, secondaryNeeded);
                        } else {
                            withdrawnAmount = Math.min(Math.min(unfinishedCount, secondaryCount), 14);

                            Rs2Bank.withdrawX(currentPotion.unfinished, withdrawnAmount);
                            Rs2Bank.withdrawX(currentPotion.secondary, withdrawnAmount);
                        }
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
                            if (withdrawnAmount > 1) {
                                Rs2Dialogue.sleepUntilHasCombinationDialogue();
                                Rs2Keyboard.keyPress('1');
                            }
                            currentlyMakingPotions = true;
                            return;
                        }
                    }
                    state = State.BANK;
                }
                if (config.mode() == Mode.FINISHED_POTIONS && state == State.MAKE_FINISHED) {
                    if (amuletBroken && config.useAmuletOfChemistry()) {
                        currentlyMakingPotions = false;
                        state = State.BANK;
                        return;
                    }
                    
                    if (currentlyMakingPotions) {
                        if (isSuperCombat(currentPotion)) {
                            if (!Rs2Inventory.hasItem(ItemID.TORSTOL) || !Rs2Inventory.hasItem(ItemID._4DOSE2ATTACK) ||
                                    !Rs2Inventory.hasItem(ItemID._4DOSE2STRENGTH) || !Rs2Inventory.hasItem(ItemID._4DOSE2DEFENSE)) {
                                currentlyMakingPotions = false;
                                state = State.BANK;
                                return;
                            }
                        } else if (usesStackableSecondary(currentPotion)) {
                            if (!Rs2Inventory.hasItem(currentPotion.unfinished)) {
                                currentlyMakingPotions = false;
                                state = State.BANK;
                                return;
                            }
                        } else {
                            if (!Rs2Inventory.hasItem(currentPotion.unfinished) && !Rs2Inventory.hasItem(currentPotion.secondary)) {
                                currentlyMakingPotions = false;
                                state = State.BANK;
                                return;
                            }
                        }
                        return;
                    }

                    if (isSuperCombat(currentPotion)) {
                        if (Rs2Inventory.hasItem(ItemID.TORSTOL) && Rs2Inventory.hasItem(ItemID._4DOSE2ATTACK)) {
                            if (Rs2Inventory.combine(ItemID.TORSTOL, ItemID._4DOSE2ATTACK)) {
                                sleep(600, 800);
                                if (withdrawnAmount > 1) {
                                    Rs2Dialogue.sleepUntilHasQuestion("How many do you wish to make?");
                                    Rs2Keyboard.keyPress('1');
                                }
                                currentlyMakingPotions = true;
                                return;
                            }
                        }
                    } else if (Rs2Inventory.hasItem(currentPotion.unfinished) && Rs2Inventory.hasItem(currentPotion.secondary)) {
                        if (Rs2Inventory.combine(currentPotion.unfinished, currentPotion.secondary)) {
                            sleep(600, 800);
                            if (withdrawnAmount > 1) {
                                Rs2Dialogue.sleepUntilHasQuestion("How many do you wish to make?");
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
        CleanHerbMode herbMode = config.cleanHerbMode();
        
        if (herbMode == CleanHerbMode.ANY_AND_ALL) {
            Herb[] herbs = Herb.values();
            for (int i = herbs.length - 1; i >= 0; i--) {
                Herb h = herbs[i];
                if (level >= h.level && Rs2Bank.hasItem(h.grimy)) {
                    return h;
                }
            }
        } else {
            Herb specificHerb = getHerbFromMode(herbMode);
            if (specificHerb != null && level >= specificHerb.level && Rs2Bank.hasItem(specificHerb.grimy)) {
                return specificHerb;
            }
        }
        return null;
    }
    private Herb findHerbForUnfinished() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        UnfinishedPotionMode potionMode = config.unfinishedPotionMode();
        
        if (potionMode == UnfinishedPotionMode.ANY_AND_ALL) {
            Herb[] herbs = Herb.values();
            for (int i = herbs.length - 1; i >= 0; i--) {
                Herb h = herbs[i];
                if (level >= h.level && Rs2Bank.hasItem(h.clean) && Rs2Bank.hasItem(ItemID.VIAL_WATER)) {
                    return h;
                }
            }
        } else {
            Herb specificHerb = getHerbFromUnfinishedMode(potionMode);
            if (specificHerb != null && level >= specificHerb.level && Rs2Bank.hasItem(specificHerb.clean) && Rs2Bank.hasItem(ItemID.VIAL_WATER)) {
                return specificHerb;
            }
        }
        return null;
    }
    private HerblorePotion findPotion() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE);
        HerblorePotion selectedPotion = config.finishedPotion();
        if (selectedPotion != null) {
            
            if (level >= selectedPotion.level) {
                if (isSuperCombat(selectedPotion)) {
                    boolean hasTorstol = Rs2Bank.hasItem(ItemID.TORSTOL);
                    boolean hasSuperAttack = Rs2Bank.hasItem(ItemID._4DOSE2ATTACK);
                    boolean hasSuperStrength = Rs2Bank.hasItem(ItemID._4DOSE2STRENGTH);
                    boolean hasSuperDefence = Rs2Bank.hasItem(ItemID._4DOSE2DEFENSE);

                    if (hasTorstol && hasSuperAttack && hasSuperStrength && hasSuperDefence) {
                        return selectedPotion;
                    }
                } else {
                    if (Rs2Bank.hasItem(selectedPotion.unfinished) && Rs2Bank.hasItem(selectedPotion.secondary)) {
                        return selectedPotion;
                    }
                }
            }
        }
        return null;
    }

    private void checkAndEquipAmulet() {
        if (!config.useAmuletOfChemistry()) {
            return;
        }
        
        if (!Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY) && 
            !Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED)) {
            
            if (!Rs2Bank.isOpen()) {
                Rs2Bank.openBank();
                Rs2Inventory.waitForInventoryChanges(1800);
                if (!Rs2Bank.isOpen()) return;
            }
            
            if (Rs2Bank.hasItem(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED)) {
                Rs2Bank.withdrawAndEquip(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED);
                Rs2Inventory.waitForInventoryChanges(1800);
            } else if (Rs2Bank.hasItem(ItemID.AMULET_OF_CHEMISTRY)) {
                Rs2Bank.withdrawAndEquip(ItemID.AMULET_OF_CHEMISTRY);
                Rs2Inventory.waitForInventoryChanges(1800);
            } else {
                Microbot.showMessage("No Amulet of Chemistry found in bank");
                return;
            }
            amuletBroken = false;
        }
    }

    private Herb getHerbFromMode(CleanHerbMode mode) {
        switch (mode) {
            case GUAM: return Herb.GUAM;
            case MARRENTILL: return Herb.MARRENTILL;
            case TARROMIN: return Herb.TARROMIN;
            case HARRALANDER: return Herb.HARRALANDER;
            case RANARR: return Herb.RANARR;
            case TOADFLAX: return Herb.TOADFLAX;
            case IRIT: return Herb.IRIT;
            case AVANTOE: return Herb.AVANTOE;
            case KWUARM: return Herb.KWUARM;
            case SNAPDRAGON: return Herb.SNAPDRAGON;
            case CADANTINE: return Herb.CADANTINE;
            case LANTADYME: return Herb.LANTADYME;
            case DWARF: return Herb.DWARF;
            case TORSTOL: return Herb.TORSTOL;
            default: return null;
        }
    }
    
    private Herb getHerbFromUnfinishedMode(UnfinishedPotionMode mode) {
        switch (mode) {
            case GUAM_POTION_UNF: return Herb.GUAM;
            case MARRENTILL_POTION_UNF: return Herb.MARRENTILL;
            case TARROMIN_POTION_UNF: return Herb.TARROMIN;
            case HARRALANDER_POTION_UNF: return Herb.HARRALANDER;
            case RANARR_POTION_UNF: return Herb.RANARR;
            case TOADFLAX_POTION_UNF: return Herb.TOADFLAX;
            case IRIT_POTION_UNF: return Herb.IRIT;
            case AVANTOE_POTION_UNF: return Herb.AVANTOE;
            case KWUARM_POTION_UNF: return Herb.KWUARM;
            case SNAPDRAGON_POTION_UNF: return Herb.SNAPDRAGON;
            case CADANTINE_POTION_UNF: return Herb.CADANTINE;
            case LANTADYME_POTION_UNF: return Herb.LANTADYME;
            case DWARF_WEED_POTION_UNF: return Herb.DWARF;
            case TORSTOL_POTION_UNF: return Herb.TORSTOL;
            default: return null;
        }
    }

    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }
}
