package net.runelite.client.plugins.microbot.runecrafting.rcx;

import lombok.Setter;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

import java.util.concurrent.TimeUnit;

public class RcxScript extends Script {

    private RcxState state;
    @Setter
    private RcxPlugin plugin; // Plugin reference
    private WorldPoint altarLocation;
    private boolean hasTeletoPOH = false;

    public boolean run(RcxConfig config) {
        validateConfig(config);
        altarLocation = parseAltarLocation(config.altarLocation());

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;

                log("Checking for state changes...");
                if (hasStateChanged()) {
                    handleStateChange(updateState());
                }

                if (state == null) {
                    log("No state detected. Resetting to BANKING.");
                    handleStateChange(RcxState.BANKING);
                    return;
                }

                log("Current state: " + state);
                switch (state) {
                    case TELE_TO:
                        log("Entering TELE_TO state...");
                        handleTeleTo(config);
                        handleStateChange(updateState());
                        break;

                    case CRAFTING:
                        log("Entering CRAFTING state...");
                        handleCrafting(config);
                        handleStateChange(updateState());
                        break;

                    case BANKING:
                        log("Entering BANKING state...");
                        handleBanking(config);
                        handleStateChange(updateState());
                        break;

                    default:
                        log("Unknown state: " + state);
                }

            } catch (Exception ex) {
                log("Exception occurred: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        log("Shutting down script...");
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
        }
        super.shutdown();
    }

    private boolean hasStateChanged() {
        log("Determining if state has changed...");
        if (state == null) return true;

        switch (state) {
            case BANKING:
                return hasRequiredItems() || isInAltarRoom();
            case CRAFTING:
                return !hasRequiredItems() || !isInAltarRoom();
            case TELE_TO:
                return isInAltarRoom() || !hasRequiredItems();
            default:
                return false;
        }
    }

    private RcxState updateState() {
        log("Updating state...");
        if (isInAltarRoom() && hasRequiredItems()) return RcxState.CRAFTING;
        if (!isInAltarRoom() && hasRequiredItems()) return RcxState.TELE_TO;
        if (!hasRequiredItems()) return RcxState.BANKING;
        return state;
    }

    private void handleStateChange(RcxState newState) {
        log("Changing state to: " + newState);
        if (plugin != null) {
            plugin.setCurrentState(newState);
        }
        state = newState; // Update internal state
    }

    private void handleTeleTo(RcxConfig config) {
        log("Handling TELE_TO...");
        if (Rs2Player.getWorldLocation().distanceTo(altarLocation) > 10) {
            if (!hasTeletoPOH) {
                log("Teleporting to POH...");
                if (Rs2Equipment.interact(9790, "Tele to POH")) {
                    Rs2Antiban.moveMouseRandomly();
                    Rs2Antiban.actionCooldown();
                    sleep(600, 1200);
                    Rs2Tab.switchToInventoryTab();
                    sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 7769, 5000);
                    hasTeletoPOH = true;
                }
            } else {
                log("Using Portal Nexus to teleport to " + config.altarLocation());
                if (Rs2GameObject.interact("Portal Nexus", config.altarLocation())) {
                    sleep(600, 1200);
                    Rs2Antiban.moveMouseOffScreen();
                    sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 11830, 10000);
                    sleep(600, 1200);
                }
            }
        } else {
            log("Entering Locations...");
            Rs2GameObject.interact(34814, "Enter");
            sleep(600, 1200);
            Rs2Antiban.moveMouseRandomly();
            sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 11083, 3000);
        }
    }

    private void handleCrafting(RcxConfig config) {
        log("Handling CRAFTING...");
        Rs2GameObject.interact(34761, "Craft-rune");
        Rs2Player.waitForXpDrop(Skill.RUNECRAFT, false);
        sleep(600, 1200);
        Rs2Inventory.interact(9781, "Teleport");
        Rs2Antiban.moveMouseRandomly();
        sleepUntil(() -> !isInAltarRoom(), 3000);
    }

    private void handleBanking(RcxConfig config) {
        log("Handling BANKING...");
        boolean isBankOpen = Rs2Bank.isNearBank(15) ? Rs2Bank.useBank() : Rs2Bank.walkToBankAndUseBank(BankLocation.CRAFTING_GUILD);
        Rs2Antiban.takeMicroBreakByChance();
        sleepUntil(Rs2Bank::isOpen);
        Rs2Antiban.actionCooldown();

        if (!isBankOpen || !Rs2Bank.isOpen()) return;

        Rs2Bank.depositAll(ItemID.MIND_RUNE);
        Rs2Inventory.waitForInventoryChanges(1200);

        if (!Rs2Bank.hasItem(ItemID.PURE_ESSENCE)) {
            log("Pure Essence not found in bank!");
            return;
        }
        Rs2Bank.withdrawAll(ItemID.PURE_ESSENCE);
        Rs2Inventory.waitForInventoryChanges(1200);

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());
        hasTeletoPOH = false;
    }

    private boolean hasRequiredItems() {
        log("Checking for required items...");
        return Rs2Inventory.hasItem(ItemID.PURE_ESSENCE)
                && Rs2Inventory.hasItem(9781)
                && Rs2Equipment.isWearing(26850)
                && Rs2Inventory.hasItem(27281);
    }

    private boolean isInAltarRoom() {
        log("Checking if in Locations room...");
        return Rs2Player.getWorldLocation().getRegionID() == 11083;
    }

    private void validateConfig(RcxConfig config) {
        if (config.altarLocation() == null || config.altarLocation().isEmpty()) {
            throw new IllegalStateException("Locations location must be configured!");
        }
    }

    private WorldPoint parseAltarLocation(String altarName) {
        switch (altarName.toLowerCase()) {
            case "mind altar":
                return new WorldPoint(2981, 3513, 0);
            case "air altar":
                return new WorldPoint(2841, 4829, 0);
            default:
                throw new IllegalArgumentException("Unknown altar location: " + altarName);
        }
    }

    private void log(String message) {
        System.out.println("[LOG] " + message);
    }
}
