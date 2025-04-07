package net.runelite.client.plugins.microbot.frosty.bloods;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.frosty.bloods.enums.HomeTeleports;
import net.runelite.client.plugins.microbot.frosty.bloods.enums.State;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.frosty.bloods.enums.Teleports;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
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
import java.util.Map;
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
    @Inject
    private ClientThread clientThread;
    private int lumbyElite = -1;
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

                if (lumbyElite == -1) {
                    clientThread.invoke(() -> {
                        lumbyElite = Microbot.getClient().getVarbitValue(Varbits.DIARY_LUMBRIDGE_ELITE);
                    });
                    return;
                }

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
                        if (config.usePoh()) {
                            handleGoingHome();
                        } else {
                            handleArdyCloak();
                            break;
                        }
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
        Rs2Tab.switchToInventoryTab();

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
        if (!config.usePoh()) {
            handleFeroxRunEnergy();
        }

        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(false);
        }

        while (!Rs2Bank.isOpen() && isRunning() && !Rs2Inventory.allPouchesFull()) {
            Rs2Bank.openBank();
            Microbot.log("Opening bank");
            sleepUntil(Rs2Bank::isOpen, 2500);
            sleepGaussian(1100, 200);
        }

        if (!Rs2Inventory.hasAnyPouch()) {
            Rs2Bank.withdrawItem(26784);
            sleepGaussian(700, 200);
        }

        if (!config.usePoh() && !Rs2Equipment.isWearing("Ardougne cloak")) {
            Rs2Bank.withdrawAndEquip("Ardougne cloak");
            sleepGaussian(700, 200);
        }

        if (config.usePoh()) {
            if (Rs2Player.getRealSkillLevel(Skill.CRAFTING) == 99 && !Rs2Equipment.isWearing("Crafting cape")) {
                if (Rs2Bank.hasItem("Crafting cape")) {
                    Rs2Bank.withdrawAndEquip("Crafting cape");
                    sleepUntil(() -> Rs2Equipment.isWearing("Crafting cape"), 2400);
                }
            }
        }

        if (!Rs2Inventory.hasRunePouch()) {
            Rs2Bank.withdrawRunePouch();
            sleepGaussian(700, 200);
        }

        if (config.usePoh()) {
            boolean hasConstructionCape = Rs2Inventory.contains(HomeTeleports.CONSTRUCTION_CAPE.getItemIds());
            if (!hasConstructionCape) {
                for (Integer itemId : HomeTeleports.CONSTRUCTION_CAPE.getItemIds()) {
                    if (Rs2Bank.hasItem(itemId)) {
                        Rs2Bank.withdrawItem(itemId);
                        Rs2Inventory.waitForInventoryChanges(1200);
                        if (Rs2Inventory.contains(itemId)) {
                            break;
                        }
                    }
                }

                if (!Rs2Inventory.contains(HomeTeleports.CONSTRUCTION_CAPE.getItemIds())) {
                    for (Integer tabId : HomeTeleports.HOUSE_TAB.getItemIds()) {
                        if (Rs2Bank.hasItem(tabId)) {
                            Rs2Bank.withdrawAll(tabId);
                            Rs2Inventory.waitForInventoryChanges(1200);
                            Microbot.log("Withdrawing all House Tabs from the bank.");
                            break;
                        }
                    }
                }
            }
        }


        if (!Rs2Equipment.isWearing("Ring of dueling")) {
            Rs2Bank.withdrawAndEquip(2552);
            sleepUntil(() -> Rs2Equipment.isWearing("Ring of duelling"));
        }

        if (!config.usePoh() && lumbyElite != 1) {
            if (!Rs2Equipment.isWearing(9084)) {
                Microbot.log("Looking for and withdrawing lunar staff");
                Rs2Bank.withdrawAndEquip(9084);
                sleepUntil(() -> Rs2Equipment.isWearing(9084));
            }else if (!Rs2Equipment.isWearing(9084) && !Rs2Bank.hasItem(9084)) {
                Microbot.log("No lunar staff found, withdrawing dramen staff");
                Rs2Bank.withdrawAndEquip(772);
                sleepUntil(() -> Rs2Equipment.isWearing(772));
            }
        }

        if (!Rs2Inventory.contains(26392)) {
            if (!Rs2Bank.hasItem(26392)) {
                Rs2Bank.withdrawItem(26390);
                Microbot.log("Withdrawing blood essence");
                sleepGaussian(900, 200);
            } else {
                Rs2Bank.withdrawItem(26392);
                sleepGaussian(900, 200);
            }
        }

        handleFillPouch();

        if (Rs2Bank.isOpen() && Rs2Inventory.allPouchesFull() && Rs2Inventory.isFull()) {
            Microbot.log("We are full, lets go");
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
            sleepGaussian(600, 200);
            if (Rs2Inventory.contains(26390)) {
                Rs2Inventory.interact(26390, "Activate");
                Microbot.log("Activating blood essence");
                sleepGaussian(700, 200);
            }
            state = State.GOING_HOME;
        }
    }

    private void handleFeroxRunEnergy() {
        if (Rs2Player.getRunEnergy() < 45) {
            Microbot.log("We are thirsty...let us Drink");
            Rs2Walker.walkTo(3129, 3636, 0);
            Rs2GameObject.interact(39651, "Drink");
            sleepUntil(() -> (!Rs2Player.isInteracting()) && !Rs2Player.isAnimating() && Rs2Player.getRunEnergy() > 90);
            sleepGaussian(1100, 200);
        }
    }

    private void handleWalking() {
        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }

        Microbot.log("Walking to ruins");
        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        Microbot.log("Current location after waiting: " + currentLocation);
        if (currentLocation.equals(new WorldPoint(3447, 9824, 0))) {
            sleepGaussian(900, 200);
            Rs2GameObject.interact(16308, "Enter");
            sleepUntil(() -> Rs2Player.getWorldLocation().equals(new WorldPoint(3460, 9813, 0)), 1200);
            sleepGaussian(900, 200);
        }

        Microbot.log("Calling walker");
        sleepGaussian(1100, 200);
        Rs2Walker.walkTo(3555, 9783, 0);
        sleepUntil(() -> Rs2Player.getWorldLocation().equals(new WorldPoint(3555, 9783, 0)), 1200);

        state = State.CRAFTING;
    }

    private void handleCrafting() {
        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }

        Rs2GameObject.interact(25380, "Enter");
        sleepUntil(() -> !Rs2Player.isAnimating() && Rs2Player.getWorldLocation().getRegionID() == 12875);
        sleepGaussian(700, 200);
        Rs2GameObject.interact(43479, "Craft-rune");
        Rs2Player.waitForXpDrop(Skill.RUNECRAFT);
        plugin.updateXpGained();

        handleEmptyPouch();

        while (Rs2Player.getWorldLocation().getRegionID() == 12875) {
            if (Rs2Inventory.allPouchesEmpty() && !Rs2Inventory.contains("Pure essence")) {
                Microbot.log("We are in altar region and out of p ess, banking...");
                handleBankTeleport();
                sleepGaussian(500, 200);
            }
        }
        state = State.BANKING;
    }

    private void handleFillPouch() {
        while (!Rs2Inventory.allPouchesFull() || !Rs2Inventory.isFull() && isRunning()) {
            Microbot.log("Pouches are not full yet");
            if (Rs2Bank.isOpen()) {
                if (Rs2Inventory.contains("Blood rune")) {
                    Rs2Bank.depositAll("Blood rune");
                    sleepGaussian(500, 200);
                }
                Rs2Bank.withdrawAll("Pure essence");
                Rs2Inventory.fillPouches();
                sleepGaussian(900, 200);
            }
            if (!Rs2Inventory.isFull()) {
                Rs2Bank.withdrawAll("Pure essence");
                sleepUntil(Rs2Inventory::isFull);
            }
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
        Rs2Tab.switchToEquipmentTab();
        sleepGaussian(900, 200);

        Teleports bankTeleport = Teleports.CRAFTING_CAPE;
        for (Integer itemId : bankTeleport.getItemIds()) {
            if (!Rs2Equipment.isWearing(itemId)) {
                bankTeleport = Teleports.FEROX_ENCLAVE;
                break;
            }

            if (Rs2Equipment.hasEquipped(itemId)) {
                Microbot.log("Using " + bankTeleport.getName());
                Rs2Equipment.interact(itemId, bankTeleport.getInteraction());
                Teleports finalBankTeleport = bankTeleport;
                sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == finalBankTeleport.getBankingRegionId());
                sleepGaussian(900, 200);
                break;
            }
        }

        if (bankTeleport == Teleports.FEROX_ENCLAVE) {
            for (Integer itemId : bankTeleport.getItemIds()) {
                if (Rs2Equipment.hasEquipped(itemId)) {
                    Microbot.log("Using " + bankTeleport.getName());
                    Rs2Equipment.interact(itemId, bankTeleport.getInteraction());
                    Teleports finalBankTeleport1 = bankTeleport;
                    sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == finalBankTeleport1.getBankingRegionId());
                    sleepGaussian(900, 200);
                    break;
                }
            }
        }
    }

    private void handleArdyCloak() {
        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }

        if (Rs2Equipment.isWearing("Ardougne cloak")) {
            Rs2Equipment.interact("Ardougne cloak", "Kandarin Monastery");
        }
        sleepGaussian(900, 200);
        sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 10290);
        Rs2Walker.walkTo(2656, 3230, 0);
        sleepGaussian(700, 200);


        Microbot.log("Interacting with the fairies");
        Rs2GameObject.interact(29495, "Last-destination (DLS)");
        sleepGaussian(1300, 200);
        Microbot.log("Waiting for animation and region");
        Rs2Player.waitForAnimation(1200);
        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving()
                && Rs2Player.getWorldLocation() != null
                && Rs2Player.getWorldLocation().getRegionID() == 13721, 1200);

        state = State.WALKING_TO;
    }

    private void handleGoingHome() {
        // Need to find ideal way of checking when we are in POH
        // Look for portal or a room id

        if (plugin.isBreakHandlerEnabled()) {
            BreakHandlerScript.setLockState(true);
        }

        HomeTeleports homeTeleport = HomeTeleports.CONSTRUCTION_CAPE;
        if (!Rs2Inventory.contains(homeTeleport.getItemIds())) {
            Microbot.log("Con cape not found");
            homeTeleport = HomeTeleports.HOUSE_TAB;
        }
        for (Integer itemId : homeTeleport.getItemIds()) {
            if (Rs2Inventory.contains(itemId)) {
                Microbot.log("Using " + homeTeleport.getName());
                Rs2Inventory.interact(itemId, homeTeleport.getInteraction());
                sleepUntil(() -> Rs2Player.getWorldLocation() != null && Rs2Player.getWorldLocation().getRegionID() == 7769 || Rs2Player.getWorldLocation().getRegionID() == 53739
                        && !Rs2Player.isAnimating());
                /*sleepUntil(() -> client.isInInstancedRegion() && Rs2Player.getWorldLocation() != null
                        && !Rs2Player.isAnimating(), 1200);*/
                sleepGaussian(2500, 200);
                break;
            }
        }

        if (client.isInInstancedRegion()) {
            Microbot.log("Player is in an instanced region");
        }

        if (Rs2Player.getRunEnergy() < 45) {
            sleepGaussian(700, 200);
            Microbot.log("We are thirsty..let us Drink");
            List<Integer> poolObjectIds = Arrays.asList(29241, 29240, 29239, 29238, 29237);
            poolObjectIds.stream().filter(Rs2GameObject::exists).findFirst()
                    .ifPresent(objectId -> {
                        Rs2GameObject.interact(objectId, "Drink");
                        sleepUntil(() -> !Rs2Player.isInteracting() && Rs2Player.getRunEnergy() > 90);
                    });
        }

        Microbot.log("Interacting with the fairies");
        Rs2GameObject.interact(27097, "Ring-last-destination (DLS)");
        sleepGaussian(1300, 200);
        Microbot.log("Waiting for animation and region");
        Rs2Player.waitForAnimation(1200);
        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving()
                && Rs2Player.getWorldLocation() != null
                && Rs2Player.getWorldLocation().getRegionID() == 13721, 1200);
        sleepUntil(() -> Rs2Player.getWorldLocation().equals(new WorldPoint(3447, 9824, 0)), 1200);
        state = State.WALKING_TO;
    }
}





