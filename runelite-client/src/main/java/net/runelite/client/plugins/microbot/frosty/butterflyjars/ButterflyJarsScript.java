package net.runelite.client.plugins.microbot.frosty.butterflyjars;

import lombok.Getter;
import net.runelite.api.GameState;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.frosty.butterflyjars.enums.State;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

public class ButterflyJarsScript extends Script {
    public static boolean test = false;
    private State state = State.BANKING;
    @Getter
    private int jarsBought = 0;


    public boolean run(ButterflyJarsConfig config) {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyUniversalAntibanSetup();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                switch (state) {
                    case BANKING:
                        Microbot.log("State: BANKING");
                        handleBanking();
                        break;
                    case BUYING:
                        Microbot.log("State: BUYING");
                        handleBuyingJars();
                        break;
                    case HOPPING:
                        Microbot.log("State: HOPPING");
                        handleHopping();
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                Microbot.log("Total time for loop: " + totalTime + "ms");

            } catch (Exception ex) {
                Microbot.log("Exception: " + ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        return true;
    }

    private void handleHopping() {
        if (Rs2Shop.isOpen()) {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleep(Rs2Random.randomGaussian(1100, 200));
        }
        Microbot.log("Entering handleHopping()");
        int world = Login.getNextWorld(Rs2Player.isMember());
        Microbot.log("Attempting to hop to world: " + world);
        boolean isHopped = Microbot.hopToWorld(world);
        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING);
        sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);
        sleep(Rs2Random.randomGaussian(1700, 200));
        state = State.BUYING;

        if (!isHopped) {
            Microbot.log("Failed to hop to world: " + world);
        }
    }

    private void handleBanking() {
        Microbot.log("Entering handleBanking()");

        if (Rs2Inventory.onlyContains("Coins") && (!Rs2Inventory.contains("Butterfly jar"))) {
            state = State.BUYING;
        }

        if (!Rs2Bank.isOpen()) {
            Rs2Bank.openBank();
            Microbot.log("Opened bank");
            Rs2Bank.depositAll();
            sleep(Rs2Random.randomGaussian(1100, 200));
        }

        if (!Rs2Inventory.contains(995)) {
            Rs2Bank.withdrawX(995, 10000);
            sleep(Rs2Random.randomGaussian(1100, 300));
            Microbot.log("Withdrawing 10,000 coins.");
            Rs2Inventory.waitForInventoryChanges(1200);
        }

        /*// Get the amount of coins in inventory
        int coinCount = Rs2Inventory.count(995);
        sleep(Rs2Random.randomGaussian(1100, 300));

        // If coin count is less than 1000, withdraw 10,000 coins
        if (coinCount < 1000) {
            Rs2Bank.withdrawX(995, 10000);
            sleep(Rs2Random.randomGaussian(1100, 300));
            Microbot.log("Withdrawing 10,000 coins because balance is below 1000");
            Rs2Inventory.waitForInventoryChanges(1200);
            sleep(Rs2Random.randomGaussian(900, 200));
        }*/

        if (Rs2Inventory.contains("Butterfly jar")) {
            Rs2Bank.depositAll("Butterfly jar");
            Microbot.log("Deposited Butterfly Jars");
        }

        if (Rs2Inventory.contains("Coins") && !Rs2Inventory.contains("Butterfly jar")) {
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleep(Rs2Random.randomGaussian(900, 200));
            Microbot.log("Closing bank and moving to BUYING");
        } else {
            state = State.BUYING;
        }
    }

    private void handleBuyingJars() {
        Microbot.log("Entering handleBuyingJars()");
        walkToNardahShop();
        sleepUntil(() -> (!Rs2Player.isMoving()));
        sleep(Rs2Random.randomGaussian(1100, 200));
        Rs2Npc.interact(1350, "Trade");
        sleep(Rs2Random.randomGaussian(1300, 200));
        sleepUntil(() -> Rs2Widget.isWidgetVisible(ComponentID.SHOP_INVENTORY_ITEM_CONTAINER));
        sleep(Rs2Random.randomGaussian(1300, 200));
        Microbot.log("Opened shop interface");

        if (!Rs2Shop.hasMinimumStock("Butterfly jar", 28)) {
            Microbot.log("Stock too low, hopping worlds");
            state = State.HOPPING;
            return;
        }

        Rs2Shop.buyItem("Butterfly Jar", "50");
        Rs2Inventory.waitForInventoryChanges(1300);
        Microbot.log("Bought Butterfly Jars");
        int jarsInInventory = Rs2Inventory.count("Butterfly jar");
        jarsBought += jarsInInventory;
        Microbot.log("Bought " + jarsInInventory + " Butterfly Jars. Total: " + jarsBought);

        if (Rs2Inventory.contains("Butterfly jar") && !Rs2Inventory.isEmpty()) {
            if (Rs2Shop.isOpen()) {
                Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                sleep(Rs2Random.randomGaussian(900, 200));
            }
            Microbot.log("Walking to bank");
            Rs2Bank.walkToBank(BankLocation.NARDAH);
            sleep(Rs2Random.randomGaussian(900, 200));
            Rs2Bank.openBank();
            sleep(Rs2Random.randomGaussian(700, 200));
            Microbot.log("Depositing jars, returning to BANKING");
            state = State.BANKING;
        } else {
            Microbot.log("No jars in inventory after buying, retrying");
        }
    }

    public void walkToNardahShop() {
        WorldPoint targetLocation = new WorldPoint(3437, 2900, 0); // Nardah shop coordinates
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        int distance = playerLocation.distanceTo(targetLocation);
        if (distance > 7) { // Only move if more than 2 tiles away
            Microbot.log("Walking to Nardah Shop at: " + targetLocation);
            Rs2Walker.walkFastCanvas(targetLocation);
        } else {
            Microbot.log("Already near Nardah Shop, no need to walk.");
        }
    }

    @Override
    public void shutdown() {
        Microbot.log("Shutting down script");
        state = State.BANKING;
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }

    private enum State {
        BANKING, BUYING, HOPPING
    }
}
