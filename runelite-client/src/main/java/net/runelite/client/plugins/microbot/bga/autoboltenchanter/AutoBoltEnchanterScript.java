package net.runelite.client.plugins.microbot.bga.autoboltenchanter;

import java.util.concurrent.TimeUnit;
import java.util.Optional;
import java.util.Set;
import java.util.HashSet;

import net.runelite.api.Skill;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.bga.autoboltenchanter.enums.BoltType;
import net.runelite.client.plugins.microbot.util.magic.Rs2Staff;
import net.runelite.client.plugins.microbot.util.magic.Runes;
import net.runelite.client.plugins.microbot.bga.autoboltenchanter.utils.StaffUtils;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.api.gameval.ItemID;

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
                log.info("Total time for loop " + totalTime);
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
        
        // try to equip a suitable staff if we have one available in inventory
        Rs2Staff currentStaff = StaffUtils.getEquippedStaff();
        if (currentStaff != null) {
            log.info("Currently equipped staff: {}", currentStaff.name());
        } else {
            // try to equip from inventory
            for (Rs2Staff staff : Rs2Staff.values()) {
                if (staff != Rs2Staff.NONE && Rs2Inventory.hasItem(staff.getItemID()) && 
                    StaffUtils.calculateStaffScore(staff, selectedBoltType.getRuneIds()) > 0) {
                    log.info("Equipping {} from inventory", staff.name());
                    Rs2Inventory.wield(staff.getItemID());
                    break;
                }
            }
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
        
        // check for missing items and show popup if any are missing
        String missingItems = getMissingItemsList();
        if (!missingItems.isEmpty()) {
            log.info("Missing required items: {}", missingItems);
            Microbot.showMessage("Missing items: " + missingItems);
            shutdown(); // stop the plugin
            return;
        }
        
        // deposit everything first to start fresh
        if (!Rs2Inventory.isEmpty()) {
            log.info("Depositing inventory items");
            Rs2Bank.depositAll(); // put all our items into the bank
            sleepUntil(() -> Rs2Inventory.isEmpty(), 3000); // wait until our inventory is empty
            return;
        }
        
        // try to equip the best available staff for this bolt type
        if (StaffUtils.equipBestAvailableStaff(selectedBoltType.getRuneIds())) {
            log.info("Staff equipped successfully");
            // wait for the equipment to register
            sleepUntil(() -> StaffUtils.getEquippedStaff() != null, 3000);
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
            // check if there are bolts available in the bank before shutting down
            if (Rs2Bank.isNearBank(10) && Rs2Bank.hasItem(selectedBoltType.getUnenchantedId())) {
                log.info("No bolts in inventory but found in bank - going back to banking");
                changeState(AutoBoltEnchanterState.BANKING); // go back to banking to get more bolts
                return;
            } else {
                log.info("No more bolts to enchant in inventory or bank");
                shutdown(); // stop the plugin
                return;
            }
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
        
        // cast the crossbow bolt enchant spell using sprite index
        log.info("Casting Enchant Crossbow Bolt spell");
        boolean success = castCrossbowBoltEnchantSpell(); // cast the crossbow bolt enchant spell
        if (success) {
            // wait for the production widget to open
            log.info("Waiting for production widget to open");
            boolean widgetOpened = sleepUntil(() -> Rs2Widget.isProductionWidgetOpen(), 3000); // wait for production menu
            if (widgetOpened) {
                log.info("Production widget opened, selecting bolt type: " + selectedBoltType.getEnchantedName());
                // select the correct bolt type from the production menu
                boolean selected = Rs2Widget.clickWidget(selectedBoltType.getEnchantedName(), Optional.of(270), 13, false);
                if (selected) {
                    log.info("Successfully selected bolt type, waiting for enchanting to complete");
                    sleepUntil(() -> Rs2Player.isAnimating(), 2000); // wait until we start the animation
                    sleepUntil(() -> !Rs2Player.isAnimating(), 10000); // wait until the animation finishes
                } else {
                    log.info("Failed to select bolt type from production menu");
                    changeState(AutoBoltEnchanterState.ERROR_RECOVERY); // switch to error recovery
                }
            } else {
                log.info("Production widget failed to open");
                changeState(AutoBoltEnchanterState.ERROR_RECOVERY); // switch to error recovery
            }
        } else {
            log.info("Failed to cast spell");
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
        int[] runeQuantities = selectedBoltType.getRuneQuantities(); // get how many of each rune we need per cast
        
        if (!Rs2Inventory.hasItem(selectedBoltType.getUnenchantedId())) {
            log.info("No bolts in inventory to enchant");
            return false;
        }
        
        Rs2Staff equippedStaff = StaffUtils.getEquippedStaff();
        Set<Integer> providedRunes = StaffUtils.getProvidedRunes(); // get runes provided by equipped staff
        log.info("Checking runes with equipped staff: {}, provided runes: {}", 
                equippedStaff != null ? equippedStaff.name() : "none", providedRunes);
        
        for (int i = 0; i < runeIds.length; i++) {
            int runeId = runeIds[i];
            int runesNeededPerCast = runeQuantities[i]; // how many of this rune we need per cast
            
            // check if this rune is provided by equipped staff
            if (providedRunes.contains(runeId)) {
                log.info("Rune {} provided by equipped staff", getRuneName(runeId));
                continue; // skip checking inventory for this rune
            }
            
            int available = Rs2Inventory.itemQuantity(runeId); // how many we have in inventory
            if (available < runesNeededPerCast) { // if we don't have enough for one cast
                String runeName = getRuneName(runeId); // get the human-readable rune name
                log.info("Missing rune: {} (have: {}, need: {} per cast)", runeName, available, runesNeededPerCast);
                return false;
            }
        }
        return true; // we have all required runes for at least one cast
    }

    private boolean withdrawRequiredRunes() {
        int[] runeIds = selectedBoltType.getRuneIds(); // get the rune ids we need
        
        // debug logging for staff detection
        Rs2Staff equippedStaff = StaffUtils.getEquippedStaff();
        if (equippedStaff != null) {
            log.info("Detected equipped staff: {}", equippedStaff.name());
            log.info("Staff provides runes: {}", equippedStaff.getRunes());
        } else {
            log.info("No equipped staff detected");
        }
        
        Set<Integer> providedRunes = StaffUtils.getProvidedRunes(); // get runes provided by equipped staff
        log.info("Total provided runes: {}", providedRunes);
        
        for (int runeId : runeIds) {
            // skip runes that are provided by equipped staff
            if (providedRunes.contains(runeId)) {
                log.info("Skipping {} withdrawal - provided by equipped staff", getRuneName(runeId));
                continue;
            }
            
            if (!Rs2Inventory.hasItem(runeId)) { // if we don't have this rune in inventory
                if (!Rs2Bank.hasItem(runeId)) { // if the bank doesn't have this rune
                    log.info("Bank missing rune: {}", getRuneName(runeId));
                    return false;
                }
                log.info("Withdrawing all {} from bank", getRuneName(runeId));
                Rs2Bank.withdrawAll(runeId); // withdraw all available runes
                boolean withdrawn = sleepUntil(() -> Rs2Inventory.hasItem(runeId), 3000); // wait until runes appear in inventory
                if (!withdrawn) {
                    log.info("Failed to withdraw rune: {}", getRuneName(runeId));
                    return false;
                }
                log.info("Successfully withdrew {} (total now: {})", getRuneName(runeId), Rs2Inventory.itemQuantity(runeId));
            }
        }
        return true; // successfully withdrew all required runes
    }

    private boolean withdrawBolts() {
        if (Rs2Inventory.hasItem(selectedBoltType.getUnenchantedId())) {
            return true; // we already have bolts
        }
        
        if (!Rs2Bank.hasItem(selectedBoltType.getUnenchantedId())) { // if the bank doesn't have bolts
            log.info("Bank missing bolts: {}", selectedBoltType.getName());
            return false;
        }
        
        log.info("Withdrawing all bolts: {}", selectedBoltType.getName());
        Rs2Bank.withdrawAll(selectedBoltType.getUnenchantedId()); // withdraw all available bolts
        boolean withdrawn = sleepUntil(() -> Rs2Inventory.hasItem(selectedBoltType.getUnenchantedId()), 3000); // wait until bolts appear in our inventory
        if (!withdrawn) {
            log.info("Failed to withdraw bolts");
            return false;
        }
        return true; // successfully withdrew bolts
    }

    private boolean castCrossbowBoltEnchantSpell() {
        // cast the crossbow bolt enchant spell using the specific widget ID 218.10
        try {
            log.info("Clicking Crossbow Bolt Enchantments spell at widget 218.10");
            boolean success = Rs2Widget.clickWidget(218, 10); // click the crossbow bolt enchantments spell
            if (success) {
                log.info("Successfully clicked Crossbow Bolt Enchantments spell");
                return true;
            } else {
                log.info("Failed to click Crossbow Bolt Enchantments spell widget");
                return false;
            }
        } catch (Exception e) {
            log.info("Error casting crossbow bolt enchant spell: " + e.getMessage());
            return false;
        }
    }

    private String getRuneName(int runeId) {
        switch (runeId) {
            case ItemID.AIRRUNE: return "Air rune";
            case ItemID.WATERRUNE: return "Water rune";
            case ItemID.EARTHRUNE: return "Earth rune";
            case ItemID.FIRERUNE: return "Fire rune";
            case ItemID.MINDRUNE: return "Mind rune";
            case ItemID.COSMICRUNE: return "Cosmic rune";
            case ItemID.NATURERUNE: return "Nature rune";
            case ItemID.BLOODRUNE: return "Blood rune";
            case ItemID.LAWRUNE: return "Law rune";
            case ItemID.SOULRUNE: return "Soul rune";
            case ItemID.DEATHRUNE: return "Death rune";
            default: return "Unknown rune (" + runeId + ")"; // fallback for unknown runes
        }
    }

    private String getMissingItemsList() {
        StringBuilder missingItems = new StringBuilder();
        
        // check for missing bolts
        if (!Rs2Bank.hasItem(selectedBoltType.getUnenchantedId()) && !Rs2Inventory.hasItem(selectedBoltType.getUnenchantedId())) {
            missingItems.append(selectedBoltType.getName());
        }
        
        // check for missing runes
        int[] runeIds = selectedBoltType.getRuneIds();
        Set<Integer> providedRunes = StaffUtils.getProvidedRunes(); // get runes provided by equipped staff
        
        for (int runeId : runeIds) {
            // skip runes provided by equipped staff
            if (providedRunes.contains(runeId)) {
                continue;
            }
            
            if (!Rs2Bank.hasItem(runeId) && !Rs2Inventory.hasItem(runeId)) {
                if (missingItems.length() > 0) {
                    missingItems.append(" - ");
                }
                missingItems.append(getRuneName(runeId));
            }
        }
        
        return missingItems.toString();
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