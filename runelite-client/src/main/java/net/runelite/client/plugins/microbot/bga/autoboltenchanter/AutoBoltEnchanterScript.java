package net.runelite.client.plugins.microbot.bga.autoboltenchanter;

import java.util.concurrent.TimeUnit;

import net.runelite.api.Skill;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.bga.autoboltenchanter.enums.BoltType;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AutoBoltEnchanterScript extends Script {
    
    public static String version = "1.0";
    private AutoBoltEnchanterState state = AutoBoltEnchanterState.INITIALIZING;
    private AutoBoltEnchanterConfig config;
    private long stateStartTime = System.currentTimeMillis(); // remember when we started this state for timeout checking
    private BoltType selectedBoltType;

    public boolean run(AutoBoltEnchanterConfig config) {
        this.config = config; // save the config so we can use it later
        this.selectedBoltType = config.boltType(); // get the selected bolt type from config
        
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return; // if the parent script says to stop, then stop
                if (!Microbot.isLoggedIn()) return; // if we aren't logged into the game, wait
                if (Rs2Player.isMoving() || Rs2Player.isAnimating()) return; // if we are busy doing something, wait

                long startTime = System.currentTimeMillis(); // remember when this loop started

                switch (state) {
                    case INITIALIZING: handleInitializing(); break; // handle the setup phase
                    case BANKING: handleBanking(); break; // handle banking operations
                    case ENCHANTING: handleEnchanting(); break; // handle the enchanting process
                    case ERROR_RECOVERY: handleErrorRecovery(); break; // handle error situations
                }

                long endTime = System.currentTimeMillis(); // remember when this loop ended
                long totalTime = endTime - startTime; // calculate how long this loop took
                 {
                    log.info("Total time for loop " + totalTime);
                }
            } catch (Exception ex) {
                log.info("Error in main loop: " + ex.getMessage());
                // if something goes wrong, don't crash the whole script
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleInitializing() {
        log.info("State: INITIALIZING");
        Microbot.status = "Initializing..."; // tell the user we are starting up
        
        if (!validateConfig()) { // if our config settings are invalid
            log.info("Invalid configuration");
            shutdown(); // stop the plugin
            return;
        }
        
        if (!validateMagicLevel()) { // if we don't have the required magic level
            log.info("Insufficient magic level for " + selectedBoltType.getName());
            shutdown(); // stop the plugin
            return;
        }
        
        log.info("Initialization complete - switching to banking");
        changeState(AutoBoltEnchanterState.BANKING); // switch to banking to get our supplies
    }

    private void handleBanking() {
        log.info("State: BANKING");
        Microbot.status = "Banking..."; // tell the user we are handling banking
        
        if (!Rs2Bank.isNearBank(10)) { // if we are too far from any bank
            log.info("Not near bank - walking to bank");
            Rs2Bank.walkToBank(); // walk to the nearest bank
            return;
        }
        
        if (!Rs2Bank.isOpen()) { // if the bank interface isn't open yet
            log.info("Opening bank");
            Rs2Bank.openBank(); // click to open the bank
            sleepUntil(() -> Rs2Bank.isOpen(), 3000); // wait until the bank opens
            return;
        }
        
        // check if we already have all the required items
        if (hasRequiredItems()) {
            log.info("Have all required items - switching to enchanting");
            Rs2Bank.closeBank(); // close the bank interface
            changeState(AutoBoltEnchanterState.ENCHANTING); // switch to enchanting mode
            return;
        }
        
        // deposit everything first to start fresh
        if (!Rs2Inventory.isEmpty()) {
            log.info("Depositing inventory items");
            Rs2Bank.depositAll(); // put all our items into the bank
            sleepUntil(() -> Rs2Inventory.isEmpty(), 3000); // wait until our inventory is empty
            return;
        }
        
        // withdraw required runes
        if (!withdrawRequiredRunes()) {
            log.info("Failed to withdraw required runes");
            shutdown(); // stop the plugin
            return;
        }
        
        // withdraw bolts
        if (!withdrawBolts()) {
            log.info("Failed to withdraw bolts");
            shutdown(); // stop the plugin
            return;
        }
        
        log.info("Banking complete - switching to enchanting");
        Rs2Bank.closeBank(); // close the bank interface
        changeState(AutoBoltEnchanterState.ENCHANTING); // switch to enchanting mode
    }

    private void handleEnchanting() {
        log.info("State: ENCHANTING");
        Microbot.status = "Enchanting " + selectedBoltType.getName() + "..."; // tell the user what we are doing
        
        // check if we have bolts to enchant
        if (!Rs2Inventory.hasItem(selectedBoltType.getUnenchantedId())) {
            log.info("No more bolts to enchant");
            shutdown(); // stop the plugin
            return;
        }
        
        // check if we have required runes
        if (!hasRequiredRunes()) {
            log.info("Not enough runes - going back to bank");
            changeState(AutoBoltEnchanterState.BANKING); // go back to banking to get more runes
            return;
        }
        
        // open magic tab if not already open
        if (Rs2Tab.getCurrentTab() != InterfaceTab.MAGIC) {
            log.info("Opening magic tab");
            Rs2Tab.switchTo(InterfaceTab.MAGIC); // switch to the magic spellbook
            sleep(300, 600); // wait a bit for the interface to load
            return;
        }
        
        // cast the enchant spell
        if (Rs2Magic.canCast(selectedBoltType.getMagicAction())) {
            log.info("Casting " + selectedBoltType.getMagicAction().getName());
            boolean success = Rs2Magic.cast(selectedBoltType.getMagicAction()); // cast the enchant spell
            if (success) {
                sleepUntil(() -> Rs2Player.isAnimating(), 1000); // wait until we start the animation
                sleepUntil(() -> !Rs2Player.isAnimating(), 5000); // wait until the animation finishes
            } else {
                log.info("Failed to cast spell");
                changeState(AutoBoltEnchanterState.ERROR_RECOVERY); // switch to error recovery
            }
        } else {
            log.info("Cannot cast spell - switching to error recovery");
            changeState(AutoBoltEnchanterState.ERROR_RECOVERY); // switch to error recovery
        }
    }

    private void handleErrorRecovery() {
        log.info("State: ERROR_RECOVERY");
        Microbot.status = "Recovering from error..."; // tell the user we are fixing issues
        
        // check for timeout
        if (System.currentTimeMillis() - stateStartTime > 60000) { // if we've been stuck for more than 60 seconds
            log.info("State timeout - resetting to initializing");
            changeState(AutoBoltEnchanterState.INITIALIZING); // go back to the beginning
            return;
        }
        
        // try to recover by going back to banking
        log.info("Attempting recovery - going to banking");
        changeState(AutoBoltEnchanterState.BANKING); // try to recover by going to banking
    }

    private boolean validateConfig() {
        if (selectedBoltType == null) { // if no bolt type is selected
            log.info("No bolt type selected");
            return false;
        }
        log.info("Selected bolt type: " + selectedBoltType.getName());
        return true; // config is valid
    }

    private boolean validateMagicLevel() {
        int currentLevel = Rs2Player.getRealSkillLevel(Skill.MAGIC); // get our current magic level
        int requiredLevel = selectedBoltType.getLevelRequired(); // get the required level for this bolt type
        log.info("Magic level: " + currentLevel + " (required: " + requiredLevel + ")");
        return currentLevel >= requiredLevel; // check if we have enough levels
    }

    private boolean hasRequiredItems() {
        return hasRequiredRunes() && Rs2Inventory.hasItem(selectedBoltType.getUnenchantedId()); // check for both runes and bolts
    }

    private boolean hasRequiredRunes() {
        int[] runeIds = selectedBoltType.getRuneIds(); // get the rune ids we need
        int[] runeQuantities = selectedBoltType.getRuneQuantities(); // get how many of each rune we need
        
        for (int i = 0; i < runeIds.length; i++) {
            int required = runeQuantities[i]; // how many of this rune we need
            int available = Rs2Inventory.count(runeIds[i]); // how many we have in inventory
            if (available < required) { // if we don't have enough
                log.info("Missing rune: " + runeIds[i] + " (have: " + available + ", need: " + required + ")");
                return false;
            }
        }
        return true; // we have all required runes
    }

    private boolean withdrawRequiredRunes() {
        int[] runeIds = selectedBoltType.getRuneIds(); // get the rune ids we need
        
        for (int runeId : runeIds) {
            if (!Rs2Inventory.hasItem(runeId)) { // if we don't have this rune in inventory
                if (!Rs2Bank.hasItem(runeId)) { // if the bank doesn't have this rune
                    log.info("Bank missing rune: " + runeId);
                    return false;
                }
                log.info("Withdrawing all runes: " + runeId);
                Rs2Bank.withdrawAll(runeId); // withdraw all of this rune from the bank
                boolean withdrawn = sleepUntil(() -> Rs2Inventory.hasItem(runeId), 3000); // wait until the rune appears in our inventory
                if (!withdrawn) {
                    log.info("Failed to withdraw rune: " + runeId);
                    return false;
                }
            }
        }
        return true; // successfully withdrew all required runes
    }

    private boolean withdrawBolts() {
        if (Rs2Inventory.hasItem(selectedBoltType.getUnenchantedId())) {
            return true; // we already have bolts
        }
        
        if (!Rs2Bank.hasItem(selectedBoltType.getUnenchantedId())) { // if the bank doesn't have bolts
            log.info("Bank missing bolts: " + selectedBoltType.getUnenchantedId());
            return false;
        }
        
        log.info("Withdrawing all bolts: " + selectedBoltType.getName());
        Rs2Bank.withdrawAll(selectedBoltType.getUnenchantedId()); // withdraw all bolts from the bank
        boolean withdrawn = sleepUntil(() -> Rs2Inventory.hasItem(selectedBoltType.getUnenchantedId()), 3000); // wait until bolts appear in our inventory
        if (!withdrawn) {
            log.info("Failed to withdraw bolts");
            return false;
        }
        return true; // successfully withdrew bolts
    }

    private void changeState(AutoBoltEnchanterState newState) {
        if (newState != state) { // if we are actually changing to a different state
            log.info("State change: " + state + " -> " + newState);
            state = newState; // update our current state
            stateStartTime = System.currentTimeMillis(); // reset our timeout timer for the new state
        }
    }

    @Override
    public void shutdown() {
        super.shutdown(); // clean up the script properly
    }
}