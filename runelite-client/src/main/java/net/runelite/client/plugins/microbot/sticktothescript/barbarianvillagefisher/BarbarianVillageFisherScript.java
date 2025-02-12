package net.runelite.client.plugins.microbot.sticktothescript.barbarianvillagefisher;

import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.sticktothescript.barbarianvillagefisher.enums.BarbarianFishingFunctions;
import net.runelite.client.plugins.microbot.sticktothescript.barbarianvillagefisher.enums.BarbarianFishingType;
import net.runelite.client.plugins.microbot.sticktothescript.common.Functions;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.npc.Rs2Npc.validateInteractable;

enum State {
    FISHING,
    WALK_TO_BANK,
    WALK_TO_FISHING_SPOT,
    BANKING,
    DROPPING,
    COOKING,
}


public class BarbarianVillageFisherScript extends Script {

    public static String version = "1.0.0";
    public State state = State.FISHING;
    public String debug = "";
    private boolean expectingXPDrop = false;
    private int currentlyCookingID = 0;

    private static int[] RawFish = {335, 331, 349};
    private static int BarbarianVillageFireID = 43475;
    private static WorldPoint BarbarianVillageFirePoint = new WorldPoint(3106, 3432, 0);
    private static WorldPoint BarbarianVilalgeFishingSpot = new WorldPoint(3108, 3432, 0);

    public boolean run(BarbarianVillageFisherConfig config) {
        BarbarianFishingType fishingType = config.sFishingType();
        BarbarianFishingFunctions fishingFunction = config.sFunction();

        String fishingAction = fishingType == BarbarianFishingType.BAIT_FISHING ? "Bait" : "Lure";
        String rodName = fishingType == BarbarianFishingType.BAIT_FISHING ? "Fishing rod" : "Fly fishing rod";
        String baitName = fishingType == BarbarianFishingType.BAIT_FISHING ? "Fishing bait" : "Feather";

        Microbot.enableAutoRunOn = false;

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        Rs2AntibanSettings.actionCooldownChance = 0.1;
        Rs2AntibanSettings.microBreakChance = 0.01;
        Rs2AntibanSettings.microBreakDurationLow = 0;
        Rs2AntibanSettings.microBreakDurationHigh = 3;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run() || !Microbot.isLoggedIn()) {
                return;
            }

            if (Rs2AntibanSettings.actionCooldownActive) {
                debug("Cooldown active");
                Rs2Antiban.actionCooldown();
                return;
            }

            determineState(config, fishingFunction, rodName, baitName);


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
            case WALK_TO_BANK:
                debug("Walking to bank");
                walkToBank();
                break;
            
            case BANKING:
                debug("Banking");
                bank(rodName, baitName);
                break;
            
            case DROPPING:
                debug("Dropping items");
                Rs2Inventory.dropAllExcept(rodName, baitName);
                break;
            
            case WALK_TO_FISHING_SPOT:
                debug("Walking to fishing spot");
                walkToFishingSpot();
                break;
            
            case FISHING:
                if (Rs2Player.isInteracting()) {
                    debug("Fishing");
                    sleep(256, 789);
                    return;
                }

                var fishingSpot = findFishingSpot();
                if (fishingSpot == null) {
                    debug("Found no fishing spot");
                    return;
                }

                if (!Rs2Camera.isTileOnScreen(fishingSpot.getLocalLocation())) {
                    validateInteractable(fishingSpot);
                }
    
                if (Rs2Npc.interact(fishingSpot, fishingAction)) {
                    debug("Interacted with fishing spot");
                    Rs2Antiban.actionCooldown();
                    Rs2Antiban.takeMicroBreakByChance();
                    sleep(235, 798);
                }
                break;

           case COOKING:
               if (!(Rs2Inventory.contains(RawFish[0]) || Rs2Inventory.contains(RawFish[1]) || Rs2Inventory.contains(RawFish[2]))) {
                   debug("Finished cooking");
                   return;
               }

               if (expectingXPDrop && Rs2Inventory.count(currentlyCookingID) != 0 && (Rs2Player.waitForXpDrop(Skill.COOKING, 4500))) {
                   debug("Actively cooking");
                   Rs2Antiban.actionCooldown();
                   Rs2Antiban.takeMicroBreakByChance();
                   sleep(235, 798);
                   return;
               }

               boolean barbarianVillageFire = Functions.isGameObjectOnTile(BarbarianVillageFirePoint, BarbarianVillageFireID);

               if (barbarianVillageFire && !Functions.closeToLocation(BarbarianVillageFirePoint)) {
                   debug("Walking to fire");
                   Rs2Walker.walkTo(BarbarianVillageFirePoint, 1);
                   sleep(180, 540);
                   return;
               }

               boolean interacted = false;
               debug("Using object on fire");
               if (Rs2Inventory.contains(RawFish[0])) {
                   interacted = Rs2Inventory.useItemOnObject(RawFish[0], BarbarianVillageFireID);
                   currentlyCookingID = RawFish[0];
               } else if (Rs2Inventory.contains(RawFish[1])) {
                   interacted = Rs2Inventory.useItemOnObject(RawFish[1], BarbarianVillageFireID);
                   currentlyCookingID = RawFish[1];
               } else if (Rs2Inventory.contains(RawFish[2])) {
                   interacted = Rs2Inventory.useItemOnObject(RawFish[2], BarbarianVillageFireID);
                   currentlyCookingID = RawFish[2];
               }

               if (interacted) {
                   sleepUntil(() -> !Rs2Player.isMoving() && Rs2Widget.findWidget("How many would you like to cook?", null, false) != null, 5000);
                   sleep(180, 540);
                   Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                   expectingXPDrop = true;
               }

               break;

            default:
                break;
           }

            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
            sleep(235, 798);
            return;
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    // Determine the state of the script
    private void determineState(BarbarianVillageFisherConfig config, BarbarianFishingFunctions fishingFunction, String rodName, String baitName) {
        // If we do not have the required materials to catch fish, we need to go to the bank and get them
        if (!Rs2Inventory.hasItem(rodName) || !Rs2Inventory.hasItem(baitName)) {
            debug("Need to get items");
            if (!Functions.closeToLocation(BankLocation.EDGEVILLE.getWorldPoint())) {
                state = State.WALK_TO_BANK;
            } else {
                state = State.BANKING;
            }
            return;
        }

        // If the inventory is full, we need to either cook, bank, or drop the items
        if (Rs2Inventory.isFull()) {
            if (fishingFunction == BarbarianFishingFunctions.DROP_RAW) {
                debug("Inventory is full. Dropping...");
                state = State.DROPPING;
            } else if (fishingFunction == BarbarianFishingFunctions.BANK_RAW) {
                if (!Functions.closeToLocation(BankLocation.EDGEVILLE.getWorldPoint())) {
                    debug("Inventory is full. Walking to bank...");
                    state = State.WALK_TO_BANK;
                } else {
                    debug("Banking");
                    state = State.BANKING;
                }
            } else if (fishingFunction == BarbarianFishingFunctions.COOK_AND_BANK) {
                if (Rs2Inventory.contains(RawFish[0]) || Rs2Inventory.contains(RawFish[1]) || Rs2Inventory.contains(RawFish[2])) {
                    debug("Inventory is full. Cooking...");
                    state = State.COOKING;
                } else {
                    if (!Functions.closeToLocation(BankLocation.EDGEVILLE.getWorldPoint())) {
                        state = State.WALK_TO_BANK;
                    } else {
                        state = State.BANKING;
                    }
                }
            } else if (fishingFunction == BarbarianFishingFunctions.COOK_AND_DROP) {
                if (Rs2Inventory.contains(RawFish[0]) || Rs2Inventory.contains(RawFish[1]) || Rs2Inventory.contains(RawFish[2])) {
                    debug("Inventory is full. Cooking...");
                    state = State.COOKING;
                } else {
                    debug("Inventory is full. Dropping...");
                    state = State.DROPPING;
                }
            }
        } else if (!Functions.closeToLocation(BarbarianVilalgeFishingSpot)) {
            debug("Walking to fishing spot");
            state = State.WALK_TO_FISHING_SPOT;
        } else {
            debug("Fishing");
            state = State.FISHING;
        }
    }

    // Locate the fishing spot and return the NPC
    private Rs2NpcModel findFishingSpot() {
        for (int fishingSpotId : FishingSpot.SALMON.getIds()) {
            var fishingSpot = Rs2Npc.getNpc(fishingSpotId);
            if (fishingSpot != null) {
                return fishingSpot;
            }
        }
        return null;
    }

    // Process for walking to the bank
    private void walkToBank() {
        if (Rs2Player.isMoving()) {
            return;
        }

        if (!Rs2Player.isRunEnabled()) {
            debug("Enabled run for bank");
            Rs2Player.toggleRunEnergy(true);
        }

        debug("Walking to bank");
        Rs2Walker.walkTo(BankLocation.EDGEVILLE.getWorldPoint(), 20); //walk to edgeville bank
    }

    // Process for walking to the fishing spot
    private void walkToFishingSpot() {
        if (Rs2Player.isMoving()) {
            return;
        }

        if (!Rs2Player.isRunEnabled()) {
            debug("Enabled run for fishing spot");
            Rs2Player.toggleRunEnergy(true);
        }

        debug("Walking to fishing spot");
        Rs2Walker.walkTo(BarbarianVilalgeFishingSpot, 9); //walk to fishing spot
    }
    
    // Handle all banking actions
    private void bank(String rodName, String baitName) {
        if (Rs2Bank.openBank()) {
            sleepUntil(Rs2Bank::isOpen);
            debug("Bank is open");
            Rs2Bank.depositAllExcept(rodName, baitName);
            debug("Items deposited");
            sleep(100, 300);

            if (!Rs2Inventory.hasItem(rodName)) {
                debug("Finding fishing rod");
                Rs2Bank.withdrawOne(rodName);

                sleepUntil(() -> Rs2Inventory.hasItem(rodName), 3500);

                // Exit if we did not end up finding it.
                if (!Rs2Inventory.hasItem(rodName)) {
                    debug("Could not find fishing rod in bank.");
                    Microbot.showMessage("Could not find fishing rod in bank.");
                    shutdown();
                }

            }

            if (!Rs2Inventory.hasItem(baitName)) {
                debug("Finding bait");
                Rs2Bank.withdrawAll(baitName);

                sleepUntil(() -> Rs2Inventory.hasItem(baitName), 3500);

                // Exit if we did not end up finding it.
                if (!Rs2Inventory.hasItem(baitName)) {
                    debug("Could not find bait in bank.");
                    Microbot.showMessage("Could not find bait in bank.");
                    shutdown();
                }
            }
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
