package net.runelite.client.plugins.microbot.gabplugs.karambwans;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.gabplugs.karambwans.GabulhasKarambwansInfo.botStatus;
import static net.runelite.client.plugins.microbot.gabplugs.karambwans.GabulhasKarambwansInfo.states;

@Slf4j
public class GabulhasKarambwansScript extends Script {
    public static double version = 1.1;
    private final WorldPoint zanarisRingPoint = new WorldPoint(2412, 4435, 0);
    private final WorldPoint fishingPoint = new WorldPoint(2900, 3112, 0);
    private final WorldPoint bankPoint = new WorldPoint(2381, 4455, 0);

    public boolean run(GabulhasKarambwansConfig config) {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.setActivity(Activity.CATCHING_RAW_KARAMBWAN);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                switch (botStatus) {
                    case FISHING:
                        fishingLoop();
                        Rs2Antiban.takeMicroBreakByChance();
                        botStatus = states.WALKING_TO_RING_TO_BANK;
                        Rs2Player.waitForAnimation();
                        break;
                    case WALKING_TO_RING_TO_BANK:
                        walkToRingToBank();
                        Rs2Random.waitEx(400, 200);
                        botStatus = states.WALKING_TO_BANK;
                        break;
                    case WALKING_TO_BANK:
                        doBank();
                        botStatus = states.BANKING;
                        Rs2Random.waitEx(400, 200);
                        break;
                    case BANKING:
                        useBank();
                        botStatus = states.WALKING_TO_FISH;
                        Rs2Random.waitEx(400, 200);
                        break;
                    case WALKING_TO_FISH:
                        walkToFish();
                        botStatus = states.FISHING;
                        Rs2Random.waitEx(400, 200);
                        break;
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private void fishingLoop() {
        while (!Rs2Inventory.isFull() && super.isRunning()) {
            if (!Rs2Player.isInteracting() || !Rs2Player.isAnimating()) {
                if (Rs2Inventory.contains(ItemID.TBWT_RAW_KARAMBWANJI)) {
                    interactWithFishingSpot();
                    Rs2Player.waitForAnimation();
                    sleep(2000, 4000);
                } else {
                    Microbot.showMessage("Raw karambwanji not detected. Shutting down");
                    shutdown();
                    return;
                }
            }
        }
    }

    private void walkToRingToBank() {
        WorldPoint ringLocation = new WorldPoint(2900, 3111, 0); // Karamja ring
        GameObject fairyRing = Rs2GameObject.getGameObject(ringLocation);
        if (fairyRing != null) {
            Rs2GameObject.interact(fairyRing, "Zanaris");
            Rs2Player.waitForAnimation();
        }
    }

    private void doBank() {
        Rs2Walker.walkTo(bankPoint, 3);
        while (!Rs2Player.isInArea(bankPoint, 4) && super.isRunning()) {
            Rs2Player.waitForWalking();
        }
        Rs2Bank.openBank();
    }

    private void useBank() {
        Rs2Bank.depositAll(ItemID.TBWT_RAW_KARAMBWAN);
        Rs2Inventory.waitForInventoryChanges(2000);
        if (Rs2Inventory.contains("scroll") || Rs2Inventory.contains("Scroll")) {
            Rs2Bank.depositAll("scroll");
            Rs2Bank.depositAll("Scroll");
            Rs2Inventory.waitForInventoryChanges(2000);
        }
        if (Rs2Inventory.contains(ItemID.FISH_BARREL_OPEN) || Rs2Inventory.contains(ItemID.FISH_BARREL_CLOSED)) {
            Rs2Bank.emptyFishBarrel();
            Rs2Inventory.waitForInventoryChanges(2000);
        }
    }

    private void interactWithFishingSpot() {
        Rs2Npc.interact(NpcID._0_45_48_KARAMBWAN, "Fish");
    }

    private void walkToFish() {
        Rs2Walker.walkTo(zanarisRingPoint, 3);
        Rs2Player.waitForWalking();
        WorldPoint ringLocation = new WorldPoint(2412, 4434, 0); // Zanaris ring
        GameObject fairyRing = Rs2GameObject.getGameObject(ringLocation);
        if (fairyRing != null) {
            Rs2GameObject.interact(fairyRing, "Last-destination (DKP)");
            sleepUntil(() -> Rs2Player.distanceTo(fishingPoint) < 2);
        } else {
            Rs2Walker.walkTo(fishingPoint, 1);
            Rs2Player.waitForWalking();
        }
    }
}

