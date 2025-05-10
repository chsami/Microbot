package net.runelite.client.plugins.microbot.winecollector;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.kaas.pyrefox.helpers.BankHelper;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

public class WineCollectorScript extends Script {

    public static final String version = "1.0.0";

    private static final String WINE_NAME = "Eclipse red";
    private WorldPoint initialLocation;
    private WorldPoint wineArea;

    public boolean run(WineCollectorConfig config) {
        if (Microbot.isLoggedIn()) {
            initialLocation = Rs2Player.getWorldLocation();
            wineArea = initialLocation.getPlane() == 2 ? initialLocation : new WorldPoint(1556, 3034, 2);
            Microbot.log("Script started at: " + initialLocation);
            Microbot.log("Wine area set to: " + wineArea);
        }

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Microbot.pauseAllScripts) return;

                if (Rs2Inventory.isFull()) {
                    bankItems();
                } else {
                    collectWine();
                }

            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void collectWine() {
        Microbot.status = "Collecting wine";

        if (Rs2Player.getWorldLocation().getPlane() != 2) {
            Microbot.status = "Walking to wine area";
            Rs2Walker.walkTo(wineArea);
            sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 2, 10000);
            sleep(500, 1000);
            return;
        }

        Rs2Random.waitEx(900, 300);

        if (Rs2GroundItem.exists(WINE_NAME, 10)) {
            Microbot.status = "Picking up wine";
            Rs2Random.waitEx(550, 250);
            Rs2GroundItem.pickup(WINE_NAME, 10);
            sleepUntil(() -> Rs2Inventory.hasItem(WINE_NAME), 3000);
            Rs2Random.waitEx(850, 350);
        } else {
            Microbot.status = "No wine - hopping world";
            Rs2Random.waitEx(3200, 1500);

            int world;
            boolean hopped = false;
            int attempts = 0;

            while (!hopped && attempts < 10) {
                world = Login.getRandomWorld(Rs2Player.isMember());
                if (!isDangerousWorld(world)) {
                    hopped = Microbot.hopToWorld(world);
                    if (hopped) {
                        Microbot.status = "Hopped to world: " + world;
                        Rs2Random.waitEx(2750, 750);
                        return;
                    }
                }
                attempts++;
                if (!hopped && attempts < 10) {
                    Rs2Random.waitEx(600, 200);
                }
            }
        }
    }

    private void bankItems() {
        Microbot.status = "Banking items";

        if (BankHelper.walkToAndOpenBank(BankLocation.HUNTERS_GUILD)) {
            Rs2Random.waitEx(800, 400);
            Rs2Bank.depositAll();
            Rs2Bank.closeBank();
            Microbot.status = "Returning to wine collection area";
            Rs2Walker.walkTo(wineArea);
            sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() == 2, 10000);
        }
    }

    private boolean isDangerousWorld(int world) {
        int[] dangerousWorlds = {
                324, 325, 337, 392, 393, 417, 318, 319, 370, 471, 474, 475
        };
        for (int w : dangerousWorlds) {
            if (world == w) return true;
        }
        return false;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }
}
