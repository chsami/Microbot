package net.runelite.client.plugins.microbot.moonsOfPeril.handlers;

import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.moonsOfPeril.enums.State;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.moonsOfPeril.moonsOfPerilConfig;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class ResupplyHandler implements BaseHandler {


    private final int minMoonlightPotions;
    private final int minCookedBream;
    private final int potionBatchSize;
    private final WorldPoint supplyLocation = new WorldPoint(1513, 9692, 0);
    private final boolean debugLogging;

    public ResupplyHandler(moonsOfPerilConfig cfg) {
        this.minMoonlightPotions = cfg.moonlightPotionsMinimum();
        this.minCookedBream      = cfg.cookedBreamMinimum();
        this.potionBatchSize     = cfg.moonlightPotionsQuantum();
        this.debugLogging = cfg.debugLogging();
    }

    @Override
    public boolean validate() {
        return checkSupplies(minMoonlightPotions, minCookedBream);
    }

    @Override
    public State execute() {
        BreakHandlerScript.setLockState(true);
        walkToSupplies();
        makeMoonlightPotions(potionBatchSize);
        obtainBream();
        BossHandler.rechargeRunEnergy();
        BreakHandlerScript.setLockState(false);
        return State.IDLE;
    }

    private boolean checkSupplies(int moonlightPotionMinimum, int cookedBreamMinimum) {
        if ((countMoonlightPotions() < moonlightPotionMinimum)
                || (Rs2Inventory.count(ItemID.BREAM_FISH_COOKED) < cookedBreamMinimum)) {
            return true;
        } else {
            if (debugLogging) {Microbot.log("No need to resupply right now.");}
        }
        return false;
    }

    private void walkToSupplies() {
        if (debugLogging) {Microbot.log("Attempting to walk to supply area 1");}
        Rs2Walker.walkWithState(supplyLocation, 0);
        if (debugLogging) {Microbot.log("Arrived at supply area 1");}
    }

    private void makeMoonlightPotions(int moonlightPotionsQuantum) {
        if (debugLogging) {Microbot.log("Need a total of " + moonlightPotionsQuantum + " Moonlight potions");}
        int amountToCreate = checkPotionQuantum(moonlightPotionsQuantum);
        if (debugLogging) {Microbot.log("Need to create " + amountToCreate + " Moonlight potions");}

        if ((amountToCreate > 0) && (Rs2Player.distanceTo(supplyLocation) <= 10)) {
            /* Take herblore supplies */
            while (Rs2Inventory.count(ItemID.VIAL_WATER) < amountToCreate) {
                if (debugLogging) {Microbot.log("Take herblore supplies from supply crate");}
                if (Rs2GameObject.interact(ObjectID.PMOON_SUPPLY_CRATE, "Take from")) {
                    Rs2Dialogue.sleepUntilHasDialogueOption("Take herblore supplies.");
                    Rs2Dialogue.clickOption("Take herblore supplies.");
                    Rs2Inventory.waitForInventoryChanges(1_500);
                }
            }

            /* Forage for grubs */
            while (Rs2Inventory.count(ItemID.MOONLIGHT_GRUB)
                    + Rs2Inventory.count(ItemID.MOONLIGHT_GRUB_PASTE) < amountToCreate) {
                if (debugLogging) {Microbot.log("Collect Moonlight Grub");}
                if (Rs2GameObject.interact(ObjectID.PMOON_GRUB_SAPLING, "Collect-from")) {
                    sleepUntil(() -> Rs2Inventory.count(ItemID.MOONLIGHT_GRUB)
                            + Rs2Inventory.count(ItemID.MOONLIGHT_GRUB_PASTE) >= amountToCreate, 8_000);
                }
            }

            /* Grind to paste */
            if (Rs2Inventory.contains(ItemID.PESTLE_AND_MORTAR)) {
                Rs2Inventory.combine(ItemID.PESTLE_AND_MORTAR, ItemID.MOONLIGHT_GRUB);
                sleepUntil(() -> !Rs2Inventory.contains(ItemID.MOONLIGHT_GRUB), 8_000);
            } else {
                if (debugLogging) {Microbot.log("Need a pestle and mortar!");}
            }

            /* Mix potion */
            if (Rs2Inventory.combine(ItemID.MOONLIGHT_GRUB_PASTE, ItemID.VIAL_WATER)) {
                sleepUntil(() -> !Rs2Player.isAnimating(), 10_000);
            }

            /* Drop leftovers */
            while (Rs2Inventory.contains(ItemID.PESTLE_AND_MORTAR))  Rs2Inventory.drop(ItemID.PESTLE_AND_MORTAR);
            sleep(600);
            while (Rs2Inventory.contains(ItemID.MOONLIGHT_GRUB))      Rs2Inventory.drop(ItemID.MOONLIGHT_GRUB);
            sleep(600);
            while (Rs2Inventory.contains(ItemID.MOONLIGHT_GRUB_PASTE))Rs2Inventory.drop(ItemID.MOONLIGHT_GRUB_PASTE);
            sleep(600);
            while (Rs2Inventory.contains(ItemID.VIAL_WATER))Rs2Inventory.drop(ItemID.VIAL_WATER);
            sleep(600);
            while (Rs2Inventory.contains(ItemID.VIAL_EMPTY))Rs2Inventory.drop(ItemID.VIAL_EMPTY);
            sleep(600);
        }
    }

    private void obtainBream() {
        fishBream();
        cookBream();
    }

    private void fishBream() {
        if (!Rs2Inventory.contains(ItemID.BIG_NET)) {
            if (Rs2GameObject.interact(ObjectID.PMOON_SUPPLY_CRATE, "Take from")) {
                Rs2Dialogue.sleepUntilHasDialogueOption("Take fishing supplies.");
                Rs2Dialogue.clickOption("Take fishing supplies.");
                Rs2Inventory.waitForInventoryChanges(4_000);
            }
            Rs2Walker.walkFastCanvas(new WorldPoint(1520,9689,0));
            while (!Rs2Inventory.isFull() && Rs2Inventory.contains(ItemID.BIG_NET)) {
                if (!Rs2Player.isAnimating()) {
                    Rs2GameObject.interact(51367, "Fish"); // restart if animation stopped
                    sleep(3000, 4000);
                }
                sleep(300, 500);
            }
            if (debugLogging) {Microbot.log("Inventory should now be full of fish");}
            sleep(600, 900);
            Rs2Inventory.drop(ItemID.BIG_NET);
        }

    }

    private void cookBream() {
        if (debugLogging) {Microbot.log("Walking to cooking stove");}
        Rs2Walker.walkFastCanvas(new WorldPoint(1512, 9693, 0));

        while (Rs2Inventory.contains(ItemID.BREAM_FISH_RAW)) {
            if (!Rs2Player.isAnimating()) {
                if (Rs2GameObject.interact(ObjectID.PMOON_RANGE, "Cook")) {
                    sleep(600, 900);
                }
            }
            sleep(900, 1200);
        }
        if (debugLogging) {Microbot.log("Finished cooking bream.");}
    }

    /**
     * Return int: the total number of Moonlight Potions currently in inventory
     */
    private int countMoonlightPotions() {
        return Rs2Inventory.count(ItemID._4DOSEMOONLIGHTPOTION)
                + Rs2Inventory.count(ItemID._3DOSEMOONLIGHTPOTION)
                + Rs2Inventory.count(ItemID._2DOSEMOONLIGHTPOTION)
                + Rs2Inventory.count(ItemID._1DOSEMOONLIGHTPOTION);
    }

    /**
     * Return int: The number of new Moonlight potions we need to make
     */
    private int checkPotionQuantum(int target)
    {
        int desired = target - countMoonlightPotions();
        if (desired <= 0) return 0;

        final int overheadSlots = 2; // 1 mortar + 1 extra slot for double pulls from crate
        int requiredSlots = desired * 2 + overheadSlots;    // 2 per potion + 1 mortar + 1 space for double potion pulls from crate
        if (debugLogging) {Microbot.log("Required free inventory slots: " + requiredSlots);}
        int freeSlots     = Rs2Inventory.emptySlotCount();
        if (debugLogging) {Microbot.log("Current free inventory slots: " + freeSlots);}

        /* --------- Free inventory space by dropping fish ---------------- */
        while (freeSlots < requiredSlots &&
                (Rs2Inventory.contains(ItemID.BREAM_FISH_COOKED) ||
                        Rs2Inventory.contains(ItemID.BREAM_FISH_RAW)))
        {
            if (Rs2Inventory.contains(ItemID.BREAM_FISH_RAW)) {
                Rs2Inventory.drop(ItemID.BREAM_FISH_RAW);
            } else {
                Rs2Inventory.drop(ItemID.BREAM_FISH_COOKED);
            }
            sleep(400, 600);
            freeSlots = Rs2Inventory.emptySlotCount();
        }
        if (freeSlots < requiredSlots) {
            desired = Math.max((freeSlots - overheadSlots) / 2, 0);
        }
        return desired;
    }
}