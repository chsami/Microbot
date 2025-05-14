package net.runelite.client.plugins.microbot.wheat;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

public class WheatScript extends Script {

    public static double version = 1.0;

    public static WheatConfig config;

    private static final WorldArea bankArea = new WorldArea(3092, 3242, 2, 5, 0);
    private static final WorldArea wheatArea = new WorldArea(3107, 3270, 12, 7, 0);
    private static final WorldPoint wheatGate = new WorldPoint(3106, 3273, 0);
    public static int profit;
    private int pickedWheat = 0;

    private static int bankingCountDown = 30;
    private static int pickingCountdown = 120;

    public boolean run(WheatConfig config) {
        WheatScript.config = config;
        pickedWheat = Rs2Inventory.count(1947);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) return;
            if (!Microbot.isLoggedIn()) return;
            if (bankingCountDown == 0 || pickingCountdown == 0) {
                System.out.println("Bot is stuck, logging out: banking " + bankingCountDown + ", picking " + pickingCountdown);
                Rs2Player.logout();
                shutdown();
            }
            try {
                handleWheat();
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    public void handleWheat() {
        if (Rs2Inventory.isFull()) {
            System.out.println("Inventory full");
            if (pickingCountdown != 120) {
                System.out.println("Reset picking countdown from " + pickingCountdown);
                pickingCountdown = 120;

            }
            handleBanking();
        } else {
            System.out.println("Time to pick wheat");
            if (bankingCountDown != 30) {
                System.out.println("Reset banking countdown from " + bankingCountDown);
                bankingCountDown = 30;
            }
            handleWheating();
        }
    }

    public void handleWheating() {
        pickingCountdown -= 1;
        WorldPoint currentPlayerWorldLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
        if (wheatArea.contains(currentPlayerWorldLocation)) {
            int WHEAT_GAMEOBJECT = 15506;
            int startCountInventory = Rs2Inventory.count(1947);
            if (Rs2GameObject.getGameObjects(obj -> obj.getId() == WHEAT_GAMEOBJECT).stream().filter(g -> wheatArea.contains(g.getWorldLocation())).findFirst().get().getWorldLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation()) == 0) {
                Microbot.getMouse().click(Microbot.getClient().getLocalPlayer().getCanvasTilePoly().getBounds());
            } else {
                Rs2GameObject.interact(WHEAT_GAMEOBJECT, "Pick");
            }
            sleep(1, 650);
            if (Random.random(1, 5) == 3) {
                sleepUntil(() -> Rs2Inventory.count(1947) == startCountInventory + 1);
            }
            if (Random.random(1, 15) == 3) {
                sleep(20, 1650);
            }
            if (Rs2Inventory.count(1947) > startCountInventory || Rs2Inventory.count(1947) > pickedWheat) {
                System.out.println("Picked some wheat...");
                pickedWheat += 1;
                profit += 70;
            }


        } else {
            System.out.println("Player not in WHEAT area " + currentPlayerWorldLocation +
                    "not in " + wheatArea.toWorldPointList());
            WorldPoint randomTile = wheatArea.toWorldPointList().get(Random.random(0, wheatArea.toWorldPointList().size() - 1));

            System.out.println("Walking to Wheat at: " + randomTile);
            Rs2Walker.walkTo(randomTile);
        }
    }

    public void handleBanking() {
        bankingCountDown -= 1;
        if (bankArea.contains(Microbot.getClient().getLocalPlayer().getWorldLocation())) {
            if (Rs2Bank.isOpen()) {
                Rs2Bank.depositAll();
                pickedWheat = 0;
            } else {
                Rs2Bank.openBank(Rs2Npc.getNpc(1613));
                sleep(350, 1800);
            }
        } else {
            WorldPoint randomBankPoint = bankArea.toWorldPointList().get(Random.random(0, bankArea.toWorldPointList().size() - 1));
            Rs2Walker.walkTo(randomBankPoint);
        }
    }

}
