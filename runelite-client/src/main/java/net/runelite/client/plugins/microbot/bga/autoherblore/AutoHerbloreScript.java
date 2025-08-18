package net.runelite.client.plugins.microbot.bga.autoherblore;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
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

@Slf4j
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
                long startTime = System.currentTimeMillis(); // remember when this loop iteration started
                
                // mandatory checks following claude.md standards
                if (!super.run()) { // if the parent script tells us to stop
                     log.info("super.run() returned false - stopping script");
                    return;
                }
                if (!Microbot.isLoggedIn()) { // if we aren't logged into the game
                     log.info("not logged in - waiting");
                    return;
                }
                if (Rs2AntibanSettings.actionCooldownActive) { // if antiban is currently cooling down
                     log.info("antiban cooldown active - waiting");
                    return;
                }
                if (state == State.BANK) {
                     {
                        log.info("=== BANK State Debug ===");
                        log.info("near bank: " + Rs2Bank.isNearBank(10));
                        log.info("bank open: " + Rs2Bank.isOpen());
                        log.info("inventory empty: " + Rs2Inventory.isEmpty());
                        log.info("inventory slots used: " + (28 - Rs2Inventory.emptySlotCount()));
                    }
                    
                    if (!Rs2Bank.isNearBank(10)) { // if we are more than 10 tiles away from a bank
                         log.info("not near bank - walking to bank");
                        Rs2Bank.walkToBank(); // walk to the nearest bank
                        return;
                    }
                    if (!Rs2Bank.openBank()) { // if we failed to open the bank interface
                         log.info("failed to open bank - retrying next loop");
                        return;
                    }
                    
                     log.info("depositing all items in inventory");
                    Rs2Bank.depositAll(); // put all our items into the bank
                    Rs2Inventory.waitForInventoryChanges(1800); // wait for the deposit to complete
                    if (config.mode() == Mode.CLEAN_HERBS) {
                         log.info("=== CLEAN_HERBS Mode ===");
                        
                        if (current == null || !Rs2Bank.hasItem(current.grimy)) current = findHerb(); // find a herb we can clean
                        if (current == null) { // if we couldn't find any herbs to clean
                             log.info("no more herbs available - shutting down");
                            Microbot.showMessage("No more herbs");
                            shutdown();
                            return;
                        }
                        
                         {
                            log.info("selected herb: " + current.name());
                            log.info("bank has grimy herb: " + Rs2Bank.hasItem(current.grimy));
                            log.info("withdrawing 28 grimy " + current.name());
                        }
                        
                        Rs2Bank.withdrawX(current.grimy, 28); // withdraw 28 grimy herbs from the bank
                        Rs2Inventory.waitForInventoryChanges(1800); // wait for the herbs to appear in our inventory
                        
                         {
                            log.info("inventory has grimy herbs: " + Rs2Inventory.hasItem("grimy"));
                            log.info("closing bank and switching to CLEAN state");
                        }
                        
                        Rs2Bank.closeBank(); // close the bank interface
                        state = State.CLEAN; // switch to cleaning state
                        return;
                    }
                    if (config.mode() == Mode.UNFINISHED_POTIONS) {
                         log.info("=== UNFINISHED_POTIONS Mode ===");
                        
                        if (currentHerbForUnfinished == null || (!Rs2Bank.hasItem(currentHerbForUnfinished.clean) || !Rs2Bank.hasItem(ItemID.VIAL_WATER))) {
                            currentHerbForUnfinished = findHerbForUnfinished(); // find herbs and vials we can use
                        }
                        if (currentHerbForUnfinished == null) { // if we couldn't find herbs or vials
                             log.info("no more herbs or vials of water available - shutting down");
                            Microbot.showMessage("No more herbs or vials of water");
                            shutdown();
                            return;
                        }
                        
                        int herbCount = Rs2Bank.count(currentHerbForUnfinished.clean); // count clean herbs in bank
                        int vialCount = Rs2Bank.count(ItemID.VIAL_WATER); // count vials of water in bank
                        withdrawnAmount = Math.min(Math.min(herbCount, vialCount), 14); // calculate how many we can make

                         {
                            log.info("selected herb: " + currentHerbForUnfinished.name());
                            log.info("clean herb count in bank: " + herbCount);
                            log.info("vial count in bank: " + vialCount);
                            log.info("withdrawing amount: " + withdrawnAmount);
                        }

                        Rs2Bank.withdrawX(currentHerbForUnfinished.clean, withdrawnAmount); // withdraw clean herbs
                        Rs2Bank.withdrawX(ItemID.VIAL_WATER, withdrawnAmount); // withdraw vials of water
                        Rs2Inventory.waitForInventoryChanges(1800); // wait for items to appear in inventory
                        
                         {
                            log.info("inventory has clean herbs: " + Rs2Inventory.hasItem(currentHerbForUnfinished.clean));
                            log.info("inventory has vials: " + Rs2Inventory.hasItem(ItemID.VIAL_WATER));
                            log.info("closing bank and switching to MAKE_UNFINISHED state");
                        }
                        
                        Rs2Bank.closeBank(); // close the bank interface
                        state = State.MAKE_UNFINISHED; // switch to making unfinished potions
                        return;
                    }
                    if (config.mode() == Mode.FINISHED_POTIONS) {
                         log.info("=== FINISHED_POTIONS Mode ===");
                        
                        checkAndEquipAmulet(); // check if we need to equip amulet of chemistry
                        
                        if (currentPotion == null || !Rs2Bank.hasItem(currentPotion.unfinished) || !Rs2Bank.hasItem(currentPotion.secondary)) {
                            currentPotion = findPotion(); // find a potion we can make
                            if (currentPotion == null) { // if we couldn't find ingredients
                                 log.info("no more ingredients for selected potion - shutting down");
                                Microbot.showMessage("No more ingredients for selected potion");
                                shutdown();
                                return;
                            }
                        }
                        
                        int unfinishedCount = Rs2Bank.count(currentPotion.unfinished); // count unfinished potions in bank
                        int secondaryCount = Rs2Bank.count(currentPotion.secondary); // count secondary ingredients in bank
                        
                         {
                            log.info("selected potion: " + currentPotion.name());
                            log.info("unfinished potion count: " + unfinishedCount);
                            log.info("secondary ingredient count: " + secondaryCount);
                        }

                        if (isSuperCombat(currentPotion)) { // if we are making super combat potions
                             log.info("=== SUPER_COMBAT Special Handling ===");
                            
                            int torstolCount = Rs2Bank.count(ItemID.TORSTOL); // count torstol herbs
                            int superAttackCount = Rs2Bank.count(ItemID._4DOSE2ATTACK); // count super attack potions
                            int superStrengthCount = Rs2Bank.count(ItemID._4DOSE2STRENGTH); // count super strength potions
                            int superDefenceCount = Rs2Bank.count(ItemID._4DOSE2DEFENSE); // count super defence potions

                            withdrawnAmount = Math.min(Math.min(Math.min(Math.min(torstolCount, superAttackCount), superStrengthCount), superDefenceCount), 7); // calculate how many we can make (max 7)

                             {
                                log.info("torstol count: " + torstolCount);
                                log.info("super attack count: " + superAttackCount);
                                log.info("super strength count: " + superStrengthCount);
                                log.info("super defence count: " + superDefenceCount);
                                log.info("withdrawing " + withdrawnAmount + " of each ingredient");
                            }

                            Rs2Bank.withdrawX(ItemID.TORSTOL, withdrawnAmount); // withdraw torstol herbs
                            Rs2Bank.withdrawX(ItemID._4DOSE2ATTACK, withdrawnAmount); // withdraw super attack potions
                            Rs2Bank.withdrawX(ItemID._4DOSE2STRENGTH, withdrawnAmount); // withdraw super strength potions
                            Rs2Bank.withdrawX(ItemID._4DOSE2DEFENSE, withdrawnAmount); // withdraw super defence potions
                        } else if (usesStackableSecondary(currentPotion)) { // if this potion uses stackable secondary ingredients
                             log.info("=== STACKABLE_SECONDARY Handling ===");
                            
                            int secondaryRatio = getStackableSecondaryRatio(currentPotion); // get how many secondaries per potion
                            withdrawnAmount = Math.min(unfinishedCount, 27); // calculate how many potions we can fit (max 27)
                            int secondaryNeeded = withdrawnAmount * secondaryRatio; // calculate total secondaries needed

                             {
                                log.info("stackable secondary detected - potion: " + currentPotion.name());
                                log.info("secondary ratio: " + secondaryRatio + " per potion");
                                log.info("potions to make: " + withdrawnAmount);
                                log.info("total secondary needed: " + secondaryNeeded);
                                log.info("available secondary: " + secondaryCount);
                            }

                            if (secondaryCount < secondaryNeeded) { // if we don't have enough secondary ingredients
                                withdrawnAmount = secondaryCount / secondaryRatio; // reduce potions to what we can make
                                secondaryNeeded = withdrawnAmount * secondaryRatio; // recalculate secondaries needed
                                 {
                                    log.info("adjusted due to insufficient secondary - new potion count: " + withdrawnAmount);
                                    log.info("new secondary needed: " + secondaryNeeded);
                                }
                            }

                            Rs2Bank.withdrawX(currentPotion.unfinished, withdrawnAmount); // withdraw unfinished potions
                            Rs2Bank.withdrawX(currentPotion.secondary, secondaryNeeded); // withdraw secondary ingredients
                        } else { // regular potion with 1:1 ratio
                            withdrawnAmount = Math.min(Math.min(unfinishedCount, secondaryCount), 14); // calculate how many we can make (max 14)

                             {
                                log.info("=== REGULAR_POTION Handling ===");
                                log.info("regular 1:1 ratio potion");
                                log.info("withdrawing " + withdrawnAmount + " unfinished and secondary");
                            }

                            Rs2Bank.withdrawX(currentPotion.unfinished, withdrawnAmount); // withdraw unfinished potions
                            Rs2Bank.withdrawX(currentPotion.secondary, withdrawnAmount); // withdraw secondary ingredients
                        }
                        Rs2Inventory.waitForInventoryChanges(1800); // wait for items to appear in inventory
                        
                         {
                            log.info("inventory has unfinished: " + Rs2Inventory.hasItem(currentPotion.unfinished));
                            log.info("inventory has secondary: " + Rs2Inventory.hasItem(currentPotion.secondary));
                            log.info("closing bank and switching to MAKE_FINISHED state");
                        }
                        
                        Rs2Bank.closeBank(); // close the bank interface
                        state = State.MAKE_FINISHED; // switch to making finished potions
                        return;
                    }
                }
                if (config.mode() == Mode.CLEAN_HERBS && state == State.CLEAN) {
                     {
                        log.info("=== CLEAN State Debug ===");
                        log.info("inventory has grimy herbs: " + Rs2Inventory.hasItem("grimy"));
                        log.info("inventory slots used: " + (28 - Rs2Inventory.emptySlotCount()));
                    }
                    
                    if (Rs2Inventory.hasItem("grimy")) { // if we have grimy herbs in our inventory
                         log.info("cleaning herbs using zigzag pattern");
                        Rs2Inventory.cleanHerbs(InteractOrder.ZIGZAG); // clean all grimy herbs in zigzag order
                        Rs2Inventory.waitForInventoryChanges(1800); // wait for the cleaning to complete
                        
                         {
                            log.info("finished cleaning, inventory has grimy: " + Rs2Inventory.hasItem("grimy"));
                        }
                        return;
                    }
                    
                     log.info("no more grimy herbs - returning to BANK state");
                    state = State.BANK; // switch back to banking state
                }
                if (config.mode() == Mode.UNFINISHED_POTIONS && state == State.MAKE_UNFINISHED) {
                     {
                        log.info("=== MAKE_UNFINISHED State Debug ===");
                        log.info("currently making potions: " + currentlyMakingPotions);
                        log.info("inventory has clean herbs: " + Rs2Inventory.hasItem(currentHerbForUnfinished.clean));
                        log.info("inventory has vials: " + Rs2Inventory.hasItem(ItemID.VIAL_WATER));
                        log.info("withdrawn amount: " + withdrawnAmount);
                    }
                    
                    if (currentlyMakingPotions) { // if we are currently making potions
                        if (!Rs2Inventory.hasItem(currentHerbForUnfinished.clean) && !Rs2Inventory.hasItem(ItemID.VIAL_WATER)) { // if we ran out of both ingredients
                             log.info("finished making potions - no more ingredients, returning to BANK");
                            currentlyMakingPotions = false; // stop making potions
                            state = State.BANK; // go back to banking
                            return;
                        }
                         log.info("still making potions - waiting for completion");
                        return;
                    }

                    if (Rs2Inventory.hasItem(currentHerbForUnfinished.clean) && Rs2Inventory.hasItem(ItemID.VIAL_WATER)) { // if we have both ingredients
                         log.info("combining " + currentHerbForUnfinished.name() + " with vial of water");
                        
                        if (Rs2Inventory.combine(currentHerbForUnfinished.clean, ItemID.VIAL_WATER)) { // combine the ingredients
                            sleep(600, 800); // wait for the combination to start
                            if (withdrawnAmount > 1) { // if we are making more than 1 potion
                                 log.info("waiting for combination dialogue and pressing '1' to make all");
                                Rs2Dialogue.sleepUntilHasCombinationDialogue(); // wait for the dialogue
                                Rs2Keyboard.keyPress('1'); // press 1 to make all
                            }
                            currentlyMakingPotions = true; // mark that we are now making potions
                             log.info("started making unfinished potions");
                            return;
                        }
                    }
                    
                     log.info("unable to combine ingredients - returning to BANK state");
                    state = State.BANK; // go back to banking if we can't combine
                }
                if (config.mode() == Mode.FINISHED_POTIONS && state == State.MAKE_FINISHED) {
                     {
                        log.info("=== MAKE_FINISHED State Debug ===");
                        log.info("amulet broken: " + amuletBroken);
                        log.info("using amulet of chemistry: " + config.useAmuletOfChemistry());
                        log.info("currently making potions: " + currentlyMakingPotions);
                        log.info("potion type: " + currentPotion.name());
                    }
                    
                    if (amuletBroken && config.useAmuletOfChemistry()) { // if our amulet broke and we are using amulet feature
                         log.info("amulet broke - returning to BANK to get new one");
                        currentlyMakingPotions = false; // stop making potions
                        state = State.BANK; // go back to banking to get new amulet
                        return;
                    }
                    
                    if (currentlyMakingPotions) { // if we are currently making potions
                        if (isSuperCombat(currentPotion)) { // if making super combat potions
                            if (!Rs2Inventory.hasItem(ItemID.TORSTOL) || !Rs2Inventory.hasItem(ItemID._4DOSE2ATTACK) ||
                                    !Rs2Inventory.hasItem(ItemID._4DOSE2STRENGTH) || !Rs2Inventory.hasItem(ItemID._4DOSE2DEFENSE)) { // if we ran out of any ingredient
                                 log.info("super combat ingredients depleted - returning to BANK");
                                currentlyMakingPotions = false; // stop making potions
                                state = State.BANK; // go back to banking
                                return;
                            }
                        } else if (usesStackableSecondary(currentPotion)) { // if using stackable secondaries
                            if (!Rs2Inventory.hasItem(currentPotion.unfinished)) { // if we ran out of unfinished potions
                                 log.info("unfinished potions depleted (stackable) - returning to BANK");
                                currentlyMakingPotions = false; // stop making potions
                                state = State.BANK; // go back to banking
                                return;
                            }
                        } else { // regular potions
                            if (!Rs2Inventory.hasItem(currentPotion.unfinished) && !Rs2Inventory.hasItem(currentPotion.secondary)) { // if we ran out of both ingredients
                                 log.info("regular potion ingredients depleted - returning to BANK");
                                currentlyMakingPotions = false; // stop making potions
                                state = State.BANK; // go back to banking
                                return;
                            }
                        }
                         log.info("still making finished potions - waiting for completion");
                        return;
                    }

                    if (isSuperCombat(currentPotion)) { // if making super combat potions
                        if (Rs2Inventory.hasItem(ItemID.TORSTOL) && Rs2Inventory.hasItem(ItemID._4DOSE2ATTACK)) { // if we have torstol and super attack
                             log.info("combining torstol with super attack for super combat");
                            
                            if (Rs2Inventory.combine(ItemID.TORSTOL, ItemID._4DOSE2ATTACK)) { // combine torstol with super attack
                                sleep(600, 800); // wait for combination to start
                                if (withdrawnAmount > 1) { // if making more than 1 potion
                                     log.info("waiting for dialogue and pressing '1' to make " + withdrawnAmount);
                                    Rs2Dialogue.sleepUntilHasQuestion("How many do you wish to make?"); // wait for the dialogue
                                    Rs2Keyboard.keyPress('1'); // press 1 to make all
                                }
                                currentlyMakingPotions = true; // mark that we are now making potions
                                 log.info("started making super combat potions");
                                return;
                            }
                        }
                    } else if (Rs2Inventory.hasItem(currentPotion.unfinished) && Rs2Inventory.hasItem(currentPotion.secondary)) { // if we have unfinished and secondary
                         log.info("combining " + currentPotion.name() + " unfinished with secondary ingredient");
                        
                        if (Rs2Inventory.combine(currentPotion.unfinished, currentPotion.secondary)) { // combine unfinished with secondary
                            sleep(600, 800); // wait for combination to start
                            if (withdrawnAmount > 1) { // if making more than 1 potion
                                 log.info("waiting for dialogue and pressing '1' to make " + withdrawnAmount);
                                Rs2Dialogue.sleepUntilHasQuestion("How many do you wish to make?"); // wait for the dialogue
                                Rs2Keyboard.keyPress('1'); // press 1 to make all
                            }
                            currentlyMakingPotions = true; // mark that we are now making potions
                             log.info("started making " + currentPotion.name() + " potions");
                            return;
                        }
                    }
                    
                     log.info("unable to combine finished potion ingredients - returning to BANK state");
                    state = State.BANK; // go back to banking if we can't combine
                }
                
                // performance tracking following claude.md standards
                long endTime = System.currentTimeMillis(); // remember when this loop iteration ended
                long totalTime = endTime - startTime; // calculate how long this loop took to complete
                 {
                    log.info("total time for loop " + totalTime + "ms");
                }
            } catch (Exception e) {
                 log.info("exception in main loop: " + e.getMessage());
                Microbot.log(e.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }
    private Herb findHerb() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE); // get our current herblore level
        CleanHerbMode herbMode = config.cleanHerbMode(); // get the herb selection mode from config
        
         {
            log.info("=== Find Herb Validation ===");
            log.info("herblore level: " + level);
            log.info("herb selection mode: " + herbMode);
        }
        
        if (herbMode == CleanHerbMode.ANY_AND_ALL) { // if we want to clean any available herbs
            Herb[] herbs = Herb.values(); // get all possible herbs
            for (int i = herbs.length - 1; i >= 0; i--) { // start from highest level herbs
                Herb h = herbs[i];
                if (level >= h.level && Rs2Bank.hasItem(h.grimy)) { // if we have the level and bank has the herb
                     log.info("found herb: " + h.name() + " (level " + h.level + ")");
                    return h;
                }
            }
             log.info("no suitable herbs found in bank");
        } else { // if we want a specific herb
            Herb specificHerb = getHerbFromMode(herbMode); // get the specific herb from mode
            if (specificHerb != null && level >= specificHerb.level && Rs2Bank.hasItem(specificHerb.grimy)) { // if we can clean it and have it
                 log.info("found specific herb: " + specificHerb.name());
                return specificHerb;
            }
             log.info("specific herb not available: " + (specificHerb != null ? specificHerb.name() : "null"));
        }
        return null;
    }
    private Herb findHerbForUnfinished() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE); // get our current herblore level
        UnfinishedPotionMode potionMode = config.unfinishedPotionMode(); // get the potion selection mode from config
        
         {
            log.info("=== Find Herb For Unfinished Validation ===");
            log.info("herblore level: " + level);
            log.info("unfinished potion mode: " + potionMode);
            log.info("bank has vials of water: " + Rs2Bank.hasItem(ItemID.VIAL_WATER));
        }
        
        if (potionMode == UnfinishedPotionMode.ANY_AND_ALL) { // if we want to make any available unfinished potions
            Herb[] herbs = Herb.values(); // get all possible herbs
            for (int i = herbs.length - 1; i >= 0; i--) { // start from highest level herbs
                Herb h = herbs[i];
                if (level >= h.level && Rs2Bank.hasItem(h.clean) && Rs2Bank.hasItem(ItemID.VIAL_WATER)) { // if we have level, clean herbs, and vials
                     log.info("found herb for unfinished: " + h.name() + " (level " + h.level + ")");
                    return h;
                }
            }
             log.info("no suitable herbs or vials found in bank");
        } else { // if we want a specific herb
            Herb specificHerb = getHerbFromUnfinishedMode(potionMode); // get the specific herb from mode
            if (specificHerb != null && level >= specificHerb.level && Rs2Bank.hasItem(specificHerb.clean) && Rs2Bank.hasItem(ItemID.VIAL_WATER)) { // if we can make it and have ingredients
                 log.info("found specific herb for unfinished: " + specificHerb.name());
                return specificHerb;
            }
             log.info("specific herb for unfinished not available: " + (specificHerb != null ? specificHerb.name() : "null"));
        }
        return null;
    }
    private HerblorePotion findPotion() {
        int level = Rs2Player.getRealSkillLevel(Skill.HERBLORE); // get our current herblore level
        HerblorePotion selectedPotion = config.finishedPotion(); // get the selected potion from config
        
         {
            log.info("=== Find Potion Validation ===");
            log.info("herblore level: " + level);
            log.info("selected potion: " + (selectedPotion != null ? selectedPotion.name() : "null"));
        }
        
        if (selectedPotion != null) { // if a potion is selected
             log.info("potion level requirement: " + selectedPotion.level);
            
            if (level >= selectedPotion.level) { // if we have the required level
                if (isSuperCombat(selectedPotion)) { // if it's super combat potion
                    boolean hasTorstol = Rs2Bank.hasItem(ItemID.TORSTOL); // check if we have torstol
                    boolean hasSuperAttack = Rs2Bank.hasItem(ItemID._4DOSE2ATTACK); // check if we have super attack
                    boolean hasSuperStrength = Rs2Bank.hasItem(ItemID._4DOSE2STRENGTH); // check if we have super strength
                    boolean hasSuperDefence = Rs2Bank.hasItem(ItemID._4DOSE2DEFENSE); // check if we have super defence

                     {
                        log.info("super combat ingredient check:");
                        log.info("  has torstol: " + hasTorstol);
                        log.info("  has super attack: " + hasSuperAttack);
                        log.info("  has super strength: " + hasSuperStrength);
                        log.info("  has super defence: " + hasSuperDefence);
                    }

                    if (hasTorstol && hasSuperAttack && hasSuperStrength && hasSuperDefence) { // if we have all ingredients
                         log.info("all super combat ingredients available");
                        return selectedPotion;
                    }
                     log.info("missing super combat ingredients");
                } else { // regular potion
                    boolean hasUnfinished = Rs2Bank.hasItem(selectedPotion.unfinished); // check if we have unfinished potions
                    boolean hasSecondary = Rs2Bank.hasItem(selectedPotion.secondary); // check if we have secondary ingredient
                    
                     {
                        log.info("regular potion ingredient check:");
                        log.info("  has unfinished: " + hasUnfinished);
                        log.info("  has secondary: " + hasSecondary);
                    }
                    
                    if (hasUnfinished && hasSecondary) { // if we have both ingredients
                         log.info("all regular potion ingredients available");
                        return selectedPotion;
                    }
                     log.info("missing regular potion ingredients");
                }
            } else {
                 log.info("herblore level too low for selected potion");
            }
        }
        return null;
    }

    private void checkAndEquipAmulet() {
        if (!config.useAmuletOfChemistry()) { // if amulet feature is disabled
             log.info("amulet of chemistry feature disabled - skipping");
            return;
        }
        
         {
            log.info("=== Amulet Check ===");
            log.info("wearing regular amulet: " + Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY));
            log.info("wearing imbued amulet: " + Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED));
        }
        
        if (!Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY) && 
            !Rs2Equipment.isWearing(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED)) { // if not wearing any amulet of chemistry
            
             log.info("no amulet equipped - need to get one from bank");
            
            if (!Rs2Bank.isOpen()) { // if bank is not open
                 log.info("opening bank to get amulet");
                Rs2Bank.openBank(); // open the bank
                Rs2Inventory.waitForInventoryChanges(1800); // wait for bank to open
                if (!Rs2Bank.isOpen()) { // if bank still not open
                     log.info("failed to open bank for amulet");
                    return;
                }
            }
            
            if (Rs2Bank.hasItem(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED)) { // if we have imbued amulet
                 log.info("withdrawing and equipping imbued amulet of chemistry");
                Rs2Bank.withdrawAndEquip(ItemID.AMULET_OF_CHEMISTRY_IMBUED_CHARGED); // withdraw and equip imbued amulet
                Rs2Inventory.waitForInventoryChanges(1800); // wait for equip to complete
            } else if (Rs2Bank.hasItem(ItemID.AMULET_OF_CHEMISTRY)) { // if we have regular amulet
                 log.info("withdrawing and equipping regular amulet of chemistry");
                Rs2Bank.withdrawAndEquip(ItemID.AMULET_OF_CHEMISTRY); // withdraw and equip regular amulet
                Rs2Inventory.waitForInventoryChanges(1800); // wait for equip to complete
            } else { // if we don't have any amulet
                 log.info("no amulet of chemistry found in bank");
                Microbot.showMessage("No Amulet of Chemistry found in bank");
                return;
            }
            amuletBroken = false; // mark amulet as not broken
             log.info("amulet equipped successfully");
        } else {
             log.info("amulet already equipped");
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
