package net.runelite.client.plugins.microbot.gabplugs.karambwans;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.Notifier;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.gabplugs.karambwans.GabulhasKarambwansInfo.botStatus;
import static net.runelite.client.plugins.microbot.gabplugs.karambwans.GabulhasKarambwansInfo.states;

@Slf4j
public class GabulhasKarambwansScript extends Script {
    public static double version = 1.0;
    @Inject
    private Notifier notifier;

    private final WorldPoint fishingPoint = new WorldPoint(2899, 3118, 0);

    private WorldPoint bankPoint = new WorldPoint(2381, 4455, 0);

    public boolean run(GabulhasKarambwansConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                switch (botStatus) {
                    case FISHING:
                        fishingLoop();
                        botStatus = states.WALKING_TO_BANK;
                        sleep(1000, 2000);
                        break;
                    case WALKING_TO_RING_TO_BANK:
                        walkToRingToBank();
                        botStatus = states.WALKING_TO_BANK;
                        sleep(100, 3000);



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
            sleep(100, 3000);
            if (!Rs2Player.isInteracting() || !Rs2Player.isAnimating()) {
                if (Rs2Inventory.contains("Raw karambwanji")) {
                    interactWithFishingSpot();
                } else {
                    while (true) {
                    }
                }
            }
        }
    }

    private void walkToRingToBank() {
        Rs2GameObject.interact(29495, "Zanaris");
        sleepUntil(() -> Rs2Player.getWorldLocation().equals(fishingPoint));
    }

    private void doBank() {
        Rs2Walker.walkTo(bankPoint, 3);
        while (!Rs2Player.isNearArea(bankPoint, 4)  && super.isRunning()) {
            Rs2Random.waitEx(400, 200);
        }
        Rs2Bank.useBank();
    }

    private void useBank() {
        Rs2Bank.depositAll(3142);
        Rs2Inventory.waitForInventoryChanges(1000);
        Rs2Bank.emptyFishBarrel();
        Rs2Random.waitEx(600,200);
    }

    private void interactWithFishingSpot() {
        Rs2Npc.interact(4712, "Fish");
    }

    private void walkToFish() {



            Rs2Walker.walkTo(fishingPoint, 2);
            Rs2Random.waitEx(400, 200);


        System.out.println("Done walking");


    }
}

