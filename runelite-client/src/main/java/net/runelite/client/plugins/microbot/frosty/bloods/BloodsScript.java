package net.runelite.client.plugins.microbot.frosty.bloods;

import net.runelite.api.Skill;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.frosty.bloods.enums.HomeTeleports;
import net.runelite.client.plugins.microbot.frosty.bloods.enums.State;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

public class BloodsScript extends Script {
    private final BloodsPlugin plugin;
    public static State state;

    @Inject
    public BloodsScript(BloodsPlugin plugin) {
        this.plugin = plugin;
    }
    @Inject
    private BloodsConfig config;

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
        Rs2Antiban.setActivity(Activity.CRAFTING_BLOODS_TRUE_ALTAR);
        Rs2Camera.setZoom(250);
        state = State.BANKING;
        Microbot.log("Script has started");
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();


                if (Rs2Inventory.anyPouchUnknown()) {
                    checkPouches();
                    return;
                }
                /*if (stateChanged()) {
                    state = updateState();
                }
                if (state == null) {
                    Microbot.log("Can not determine state");
                    shutdown();
                    return;
                }*/

                switch (state) {
                    case BANKING:
                        handleBanking();
                        break;
                    case GOING_HOME:
                        handleGoingHome();
                        break;
                    case WALKING_TO:
                        handleWalking();
                        break;
                    case CRAFTING:
                        handleCrafting();
                        return;
                }






                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
                Microbot.log("Error in script" + ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }
    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
        Microbot.log("Script has been stopped");
    }
    private void checkPouches() {
        sleep(Rs2Random.randomGaussian(700, 200));
        Rs2Inventory.interact(26784, "Check");
        sleep(Rs2Random.randomGaussian(900, 200));
    }

    private void handleBanking() {
        if (Rs2Inventory.hasDegradedPouch()) {
            Rs2Magic.repairPouchesWithLunar();
            sleep(Rs2Random.randomGaussian(900, 200));
            return;
        }
        Rs2Bank.openBank();
        if (!Rs2Inventory.contains(26392)) {
            Rs2Bank.withdrawItem(26390);
            sleep(Rs2Random.randomGaussian(900, 200));
        }
        while (!Rs2Inventory.allPouchesFull() || !Rs2Inventory.isFull() && isRunning()) {
            Microbot.log("Pouches are not full yet");
            if (Rs2Bank.isOpen()) {
                if (Rs2Inventory.contains("Blood rune")) {
                    Rs2Bank.depositAll("Blood rune");
                    sleep(Rs2Random.randomGaussian(500, 200));
                }
                Rs2Bank.withdrawAll("Pure essence");
                Rs2Inventory.fillPouches();
                sleep(Rs2Random.randomGaussian(700, 200));
            }
            if (!Rs2Inventory.isFull()) {
                Rs2Bank.withdrawAll("Pure essence");
                sleepUntil(Rs2Inventory::isFull);
            }
        }
        if (Rs2Bank.isOpen() && Rs2Inventory.allPouchesFull() && Rs2Inventory.isFull()) {
            Microbot.log("We are full, lets go home");
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleep(Rs2Random.randomGaussian(900, 200));
            if (config.useBloodEssence()) {
                if (Rs2Inventory.contains(26390)) {
                    Rs2Inventory.interact(26390, "Activate");
                    sleep(Rs2Random.randomGaussian(1300, 200));
                }
            }
            sleepUntil(() -> !Rs2Bank.isOpen(), 1200);
            state = State.GOING_HOME;
        }
    }

    private void handleGoingHome() {
        handleConCape();
        if (Rs2Player.getRunEnergy() < 40) {
            Microbot.log("Low run energy. Drinking from pool...");
            Rs2GameObject.interact(29241, "Drink");
            sleepUntil(() -> (!Rs2Player.isInteracting()) && Rs2Player.getRunEnergy() > 90);
        }
        Microbot.log("Interacting with the fairies");
        if (!Rs2GameObject.interact(27097, "Ring-last-destination (DLS)")) {
            sleep(Rs2Random.randomGaussian(1300, 200));
            Microbot.log("Failed to use fairy ring.");
            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving()
                    && Rs2Player.getWorldLocation() != null
                    && Rs2Player.getWorldLocation().getRegionID() == 13721, 1200);
        }
        sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 13721);
        sleep(Rs2Random.randomGaussian(1300, 200));
        state = State.WALKING_TO;
    }

    private void handleConCape(){
        if (Rs2Inventory.hasItem(9790)) {
            Microbot.log("Using Con cape to get home");
            Rs2Inventory.interact(9790, "Tele to POH");
            sleepUntil(() -> Rs2Player.getWorldLocation() != null
                    && Rs2Player.getWorldLocation().getRegionID() == 53739
                    && !Rs2Player.isAnimating());
            sleep(Rs2Random.randomGaussian(900, 200));
        } else {
            Microbot.log("No Construction Cape found, returning.");
        }
    }

    private void handleWalking() {
        Rs2GameObject.interact(16308, "Enter");
        sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 13977 &&
                !Rs2Player.isAnimating() && Rs2Player.getWorldLocation().getX() == 3460);
        sleep(Rs2Random.randomGaussian(1500, 200));
        Rs2Walker.walkTo(3555, 9783, 0);
        sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating() && !Rs2Player.isInteracting() &&
                Rs2Player.getWorldLocation().getRegionID() == 14232); //weird region area, on both sides of cave
        state = State.CRAFTING;
    }

    private void handleCrafting() {
        Rs2GameObject.interact(25380, "Enter");
        sleepUntil(() -> !Rs2Player.isAnimating() && Rs2Player.getWorldLocation().getRegionID() == 12875);
        sleep(Rs2Random.randomGaussian(700,200));
        Rs2GameObject.interact(43479, "Craft-rune");
        Rs2Player.waitForXpDrop(Skill.RUNECRAFT);

        handleEmptyPouch();

        if (Rs2Inventory.allPouchesEmpty() && !Rs2Inventory.contains("Pure essence")) {
            sleep(Rs2Random.randomGaussian(900, 200));
            Rs2Equipment.interact(9781, "Teleport");
            sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 11571);
            Rs2Tab.switchToInventoryTab();
            sleep(Rs2Random.randomGaussian(500,200));
            state = State.BANKING;
        }
    }

    private void handleEmptyPouch() {
        while (!Rs2Inventory.allPouchesEmpty() && isRunning()) {
            Microbot.log("Pouches are not empty. Crafting more");
            Rs2Inventory.emptyPouches();
            Rs2Inventory.waitForInventoryChanges(600);
            sleep(Rs2Random.randomGaussian(700,200));
            Rs2GameObject.interact(43479, "Craft-rune");
            Rs2Player.waitForXpDrop(Skill.RUNECRAFT);
        }
    }


}
