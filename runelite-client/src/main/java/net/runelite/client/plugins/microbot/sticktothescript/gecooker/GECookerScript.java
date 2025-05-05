package net.runelite.client.plugins.microbot.sticktothescript.gecooker;

import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.cooking.enums.CookingItem;
import net.runelite.client.plugins.microbot.sticktothescript.common.enums.GEWorkLocation;
import net.runelite.client.plugins.microbot.sticktothescript.common.enums.LogType;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.sticktothescript.common.Functions;

import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

enum State {
    BANKING,
    COOKING,
    BANKING_FOR_FIRE_SUPPLIES,
    BUILDING_FIRE,
}

public class GECookerScript extends Script {

    public static String version = "1.0.0";
    public State state = State.BANKING;
    public String debug = "";
    private boolean expectingXPDrop = false;
    private WorldPoint lastFireLocation;

    private static List<WorldPoint> FireSpots = Arrays.asList(
            GEWorkLocation.NORTH_EAST.getWorldPoint(),
            GEWorkLocation.SOUTH_EAST.getWorldPoint(),
            GEWorkLocation.NORTH_WEST.getWorldPoint(),
            GEWorkLocation.SOUTH_WEST.getWorldPoint()
    );
    private static List<Integer> FireIDs = Arrays.asList(26185, 49927);

    public boolean run(GECookerConfig config) {
        CookingItem cookingItem = config.sCookItem();
        LogType logType = config.sLogType();
        GEWorkLocation desiredLocation = config.sLocation();

        Microbot.enableAutoRunOn = false;

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyCookingSetup();
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        Rs2AntibanSettings.actionCooldownChance = 0.1;
        Rs2AntibanSettings.microBreakChance = 0.01;
        Rs2AntibanSettings.microBreakDurationLow = 0;
        Rs2AntibanSettings.microBreakDurationHigh = 3;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run() || !Microbot.isLoggedIn()) {
                debug("Not running");
                return;
            }

            if (Rs2AntibanSettings.actionCooldownActive) {
                debug("Cooldown active");
                return;
            }

            determineState(cookingItem, logType);

            // If the state is not cooking, then let's reset the variable as we are not expecting an XP drop
            if (state != State.COOKING) {
                expectingXPDrop = false;
            }


            if (Rs2Dialogue.hasContinue()) {
                debug("Click to continue");
                Rs2Dialogue.clickContinue();
                return;
            }

           switch (state) {
            case COOKING:
                if (Rs2Inventory.count(cookingItem.getRawItemID()) == 0) {
                    debug("Out of cooking item in inventory");
                    return;
                }

                if (expectingXPDrop && Rs2Player.waitForXpDrop(Skill.COOKING, 4500)) {
                    debug("Interacting");
                    Rs2Antiban.actionCooldown();
                    Rs2Antiban.takeMicroBreakByChance();
                    sleep(256, 789);
                    return;
                }

                debug("Looking for fires to use");
                TileObject fireObject = findFireObject();

                if (fireObject != null) {
                    lastFireLocation = fireObject.getWorldLocation();
                    if (Rs2Player.distanceTo(fireObject.getWorldLocation()) > Rs2Walker.config.reachedDistance()) {
                        debug("Walking to existing fire");
                        Rs2Walker.walkTo(fireObject.getWorldLocation());
                        sleep(256, 789);
                        return;
                    } else {
                        debug("Using object on fire");
                        Rs2Inventory.useItemOnObject(cookingItem.getRawItemID(), fireObject.getId());
                        sleepUntil(() -> !Rs2Player.isMoving() && Rs2Widget.findWidget("How many would you like to cook?", null, false) != null, 5000);
                        sleep(180, 540);
                        Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                        expectingXPDrop = true; // Subsequent iterations can be expecting an XP drop until the state changes
                    }
                }
                break;

            case BANKING:
                debug("Banking");
                bank(cookingItem);
                break;

            case BANKING_FOR_FIRE_SUPPLIES:
                debug("Banking for fire supplies");
                bankForFireSupplies(logType);
                break;

            case BUILDING_FIRE:
                debug("Building fire");

                   if (Rs2Player.isInteracting()) {
                       debug("Interacting");
                       return;
                   }

                   if (Rs2Player.distanceTo(desiredLocation.getWorldPoint()) > 1) {
                       debug("Walking to desired fire location");
                       Rs2Walker.walkTo(desiredLocation.getWorldPoint(), 0);
                       sleep(180, 540);
                       return;
                   }

                   Rs2Inventory.combine("Tinderbox", logType.getLogName());
                   Rs2Player.waitForXpDrop(Skill.FIREMAKING, 20000);
                break;

            default:
                break;
           }

            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
            sleep(256, 789);
            return;
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    // Determine the state of the script
    private void determineState(CookingItem cookingItem, LogType logType) {
        debug("Determine state");
        // Check each fire spot to see if any of them have a fire ID on them
        if (findFireObject() != null) {
            debug("Fire found");
            if (Rs2Inventory.hasItem(cookingItem.getRawItemID())) {
                debug("Cooking");
                state = State.COOKING;
            } else {
                debug("Banking");
                state = State.BANKING;
            }
            return;
        }

        if (!Rs2Inventory.hasItem("Tinderbox") || !Rs2Inventory.hasItem(logType.getLogName())) {
            debug("Need fire supplies");
            state = State.BANKING_FOR_FIRE_SUPPLIES;
        } else {
            debug("Building fire");
            state = State.BUILDING_FIRE;
        }
    }

    private TileObject findFireObject() {
        if (lastFireLocation != null) {
            TileObject fireObject = Rs2GameObject.findGameObjectByLocation(lastFireLocation);
            if (fireObject != null && FireIDs.contains(fireObject.getId())) {
                return fireObject;
            }
        }

        // check for forester campfires around each worksite
        for (WorldPoint fireSpot : FireSpots) {
            TileObject fireObject = Rs2GameObject.findReachableObject("forester", false, 3, fireSpot);

            if (fireObject != null) {
                return fireObject;
            }
        }

        // if no forester campfires, find regular fire
        for (WorldPoint fireSpot : FireSpots) {
            TileObject fireObject = Rs2GameObject.findGameObjectByLocation(fireSpot);
            if (fireObject != null && FireIDs.contains(fireObject.getId())) {
                return fireObject;
            }
        }

        return null;
    }

    // Handle all banking actions
    private void bank(CookingItem cookingItem) {
        if (Rs2Bank.openBank()) {
            sleepUntil(Rs2Bank::isOpen);
            debug("Bank is open");
            Rs2Bank.depositAll();
            debug("Items deposited");
            sleep(180, 540);

            Rs2Bank.withdrawAll(cookingItem.getRawItemID());
            sleepUntil(() -> Rs2Inventory.hasItem(cookingItem.getRawItemID()), 3500);

            // Exit if we did not end up finding it.
            if (!Rs2Inventory.hasItem(cookingItem.getRawItemID())) {
                debug("Could not find cooking item in bank.");
                Microbot.showMessage("Could not find cooking item in bank.");
                shutdown();
            }
            sleep(180, 540);
            Rs2Bank.closeBank();
        }
    }

    private void bankForFireSupplies(LogType logType) {
        if (Rs2Bank.openBank()) {
            sleepUntil(Rs2Bank::isOpen);
            debug("Bank is open");
            Rs2Bank.depositAll();
            debug("Items deposited");
            sleep(180, 540);

            Rs2Bank.withdrawOne(590);
            debug("Withdrew a tinderbox");

            sleepUntil(() -> Rs2Inventory.hasItem("Tinderbox"), 3500);

            // Exit if we did not end up finding it.
            if (!Rs2Inventory.hasItem("Tinderbox")) {
                debug("Could not find Tinderbox in bank.");
                Microbot.showMessage("Could not find Tinderbox in bank.");
                shutdown();
            }

            sleep(180, 540);
            Rs2Bank.withdrawOne(logType.getLogID());
            debug("Withdrew a log");

            sleepUntil(() -> Rs2Inventory.hasItem(logType.getLogID()), 3500);

            // Exit if we did not end up finding it.
            if (!Rs2Inventory.hasItem(logType.getLogID())) {
                debug("Could not find logs in bank.");
                Microbot.showMessage("Could not find logs in bank.");
                shutdown();
            }
            sleep(180, 540);
            Rs2Bank.closeBank();
        }
    }

    private void debug(String msg) {
        debug = msg;
        System.out.println(msg);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }
}
