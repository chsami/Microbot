package net.runelite.client.plugins.microbot.frosty.bloods;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.frosty.bloods.enums.HomeTeleports;
import net.runelite.client.plugins.microbot.frosty.bloods.enums.State;
import net.runelite.client.plugins.microbot.frosty.bloods.enums.Teleports;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;


import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.Global.sleepGaussian;

public class BloodsScript extends Script {
    private final BloodsPlugin plugin;
    public static State state;

    @Inject
    public BloodsScript(BloodsPlugin plugin) {
        this.plugin = plugin;
    }
    @Inject
    private BloodsConfig config;
    @Inject
    private Client client;

    public boolean run() {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
        Rs2Antiban.setActivity(Activity.CRAFTING_BLOODS_TRUE_ALTAR);
        Rs2Camera.setZoom(150);
        sleepGaussian(700, 200);
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

                //outside of Priff POH
                if (Rs2Player.getWorldLocation().getRegionID() == 12894) {
                    handleBankTeleport();
                    sleepGaussian(700, 200);
                    state = State.BANKING;
                    return;
                }

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
        //Rs2Player.logout();
    }
    private void checkPouches() {
        Rs2Inventory.interact(26784, "Check");
        sleepGaussian(900, 200);
    }

    private void handleBanking() {
        if (Rs2Inventory.hasDegradedPouch()) {
            Rs2Magic.repairPouchesWithLunar();
            sleepGaussian(900, 200);
            return;
        }
        if (Rs2Inventory.isFull() && Rs2Inventory.allPouchesFull() && Rs2Inventory.contains("Pure essence")) {
                    Microbot.log("We are full, skipping bank");
                    state = State.GOING_HOME;
                    return;
                }

        Rs2Bank.openBank();

        if (!Rs2Inventory.hasAnyPouch()) {
            Rs2Bank.withdrawItem(26784);
            sleepGaussian(700, 200);
        }

        if (!Rs2Inventory.hasRunePouch()) {
            Rs2Bank.withdrawRunePouch();
            sleepGaussian(700, 200);
        }


        if (config.teleports() == Teleports.RING_OF_DUELING && !Rs2Equipment.isWearing("Ring of dueling")) {
            Microbot.log("Getting RoD from bank and equipping");
            Rs2Bank.withdrawAndEquip(2552);
            sleepGaussian(700, 200);
        }
        if (config.teleports() == Teleports.CRAFTING_CAPE && !Rs2Equipment.isWearing(9781)) {
            Microbot.log("Getting Crafting cape (t) from bank and equipping");
            Rs2Bank.withdrawAndEquip(9781);
            sleepGaussian(700, 200);
        }
        if (config.homeTeleports() == HomeTeleports.HOUSE_TAB && !Rs2Inventory.contains(8013)) {
            Microbot.log("Getting house tabs from bank");
            Rs2Bank.withdrawAll(8013);
            sleepGaussian(700, 200);
        }
        if (config.homeTeleports() == HomeTeleports.CONSTRUCTION_CAPE && !Rs2Inventory.contains(9790)) {
            Microbot.log("Getting Con cape from bank");
            Rs2Bank.withdrawItem(9790);
            sleepGaussian(700, 200);
        }
        if (config.useDramenStaff() && !Rs2Equipment.isWearing(772)) {
            Microbot.log("Getting dramen staff from bank and equipping");
            Rs2Bank.withdrawAndEquip(772);
            sleepGaussian(700, 200);
        }

        if (!Rs2Inventory.contains(26392)) {
            if (!Rs2Bank.hasItem(26392)) {
                Rs2Bank.withdrawItem(26390);
                sleepGaussian(900, 200);
            } else {
                Rs2Bank.withdrawItem(26392);
            }
        }

        while (!Rs2Inventory.allPouchesFull() || !Rs2Inventory.isFull() && isRunning()) {
            Microbot.log("Pouches are not full yet");
            if (Rs2Bank.isOpen()) {
                if (Rs2Inventory.contains("Blood rune")) {
                    Rs2Bank.depositAll("Blood rune");
                    sleepGaussian(500, 200);
                }
                Rs2Bank.withdrawAll("Pure essence");
                Rs2Inventory.fillPouches();
                sleepGaussian(700, 200);
            }
            if (!Rs2Inventory.isFull()) {
                Rs2Bank.withdrawAll("Pure essence");
                sleepUntil(Rs2Inventory::isFull);
            }
        }
        if (Rs2Bank.isOpen() && Rs2Inventory.allPouchesFull() && Rs2Inventory.isFull()) {
            Microbot.log("We are full, lets go home");
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleepGaussian(600, 200);
            if (config.useBloodEssence()) {
                if (Rs2Inventory.contains(26390)) {
                    Rs2Inventory.interact(26390, "Activate");
                    sleepGaussian(700, 200);
                }
            }
            state = State.GOING_HOME;
        }
    }

    private void handleGoingHome() {
        HomeTeleports homeTeleport = config.homeTeleports();
        for (Integer itemId : homeTeleport.getItemIds()) {
            if (Rs2Inventory.contains(itemId)) {
                Microbot.log("Using " + homeTeleport.getName() + " to go home.");
                Rs2Inventory.interact(itemId, homeTeleport.getInteraction());
                sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 7769 && !Rs2Player.isAnimating());
                sleepGaussian(900, 200);
                break;
            }
        }

        if (Rs2Player.getRunEnergy() < 40) {
            Microbot.log("We are thirsty..let us Drink");
            List<Integer> poolObjectIds = Arrays.asList(29241, 29240, 29239, 29238, 29237);
            for (int objectId : poolObjectIds) {
                Rs2GameObject.interact(objectId, "Drink");
                sleepUntil(() -> (!Rs2Player.isInteracting()) && Rs2Player.getRunEnergy() > 90);
            }
        }

        Microbot.log("Interacting with the fairies");
        Rs2GameObject.interact(27097, "Ring-last-destination (DLS)");
            sleepGaussian(1300, 200);
            Microbot.log("Waiting for animation and region");
            Rs2Player.waitForAnimation(1200);
            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving()
                    && Rs2Player.getWorldLocation() != null
                    && Rs2Player.getWorldLocation().getRegionID() == 13721, 1200);

        state = State.WALKING_TO;
    }

    private void handleWalking() {
        Microbot.log("Walking to ruins");
        if (!Rs2GameObject.interact(16308, "Enter")) {
            Microbot.log("Failed to find first cave");
        }
        sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 13977 && !Rs2Player.isAnimating());
        sleepGaussian(1500, 200);
        Rs2GameObject.interact(5046, "Enter");
        sleepGaussian(500, 200);
        Rs2GameObject.interact(5046, "Enter");
        sleepUntil(() -> !Rs2Player.isInteracting() && !Rs2Player.isMoving() && !Rs2Player.isAnimating(900));
        sleepGaussian(1300, 200);
        Rs2Walker.walkTo(3555, 9783, 0);
        state = State.CRAFTING;
    }

    private void handleCrafting() {
        Rs2GameObject.interact(25380, "Enter");
        sleepUntil(() -> !Rs2Player.isAnimating() && Rs2Player.getWorldLocation().getRegionID() == 12875);
        sleepGaussian(700, 200);
        Rs2GameObject.interact(43479, "Craft-rune");
        Rs2Player.waitForXpDrop(Skill.RUNECRAFT);
        plugin.updateXpGained();

        handleEmptyPouch();

        if (Rs2Inventory.allPouchesEmpty() && !Rs2Inventory.contains("Pure essence")) {
            handleBankTeleport();
            sleepGaussian(500, 200);
        }
    }

    private void handleEmptyPouch() {
        while (!Rs2Inventory.allPouchesEmpty() && isRunning()) {
            Microbot.log("Pouches are not empty. Crafting more");
            Rs2Inventory.emptyPouches();
            Rs2Inventory.waitForInventoryChanges(600);
            sleepGaussian(700, 200);
            Rs2GameObject.interact(43479, "Craft-rune");
            Rs2Player.waitForXpDrop(Skill.RUNECRAFT);
            plugin.updateXpGained();
        }
    }

    private void handleBankTeleport() {
        Teleports selectedTeleport = config.teleports();
        if (selectedTeleport == Teleports.CRAFTING_CAPE) {
            Microbot.log("Banking with crafting cape.");
            Rs2Tab.switchToEquipmentTab();
            sleepGaussian(700, 200);
            Rs2Equipment.interact(9781, "Teleport");
            sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 11571);
            Rs2Tab.switchToInventoryTab();
            state = State.BANKING;
        } else if (selectedTeleport == Teleports.RING_OF_DUELING) {
            for (Integer itemId : selectedTeleport.getItemIds()) {
                if (Rs2Equipment.isWearing("Ring of Dueling")) {
                    Microbot.log("Banking with RoD: " + itemId);
                    Rs2Tab.switchToEquipmentTab();
                    sleepGaussian(700, 200);
                    Rs2Equipment.useRingAction(JewelleryLocationEnum.CASTLE_WARS);
                    sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 9776);
                    Rs2Tab.switchToInventoryTab();
                    state = State.BANKING;
                    return;
                }
            }
        }
    }

}


