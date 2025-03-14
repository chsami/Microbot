/*package net.runelite.client.plugins.microbot.frosty.trueblood;

import lombok.Getter;
import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import javax.inject.Inject;
import java.util.concurrent.TimeUnit;

public class TrueBloodScript extends Script {
    @Inject
    private Client client;
    private int currentRegionId;
    @Getter
    private boolean running = false;

    public boolean run(TrueBloodConfig config) {
        Microbot.log("Checking if script is enabled: " + config.enableScript());

        if (!config.enableScript()) {
            Microbot.log("Script is disabled in the config. Exiting...");
            return false;
        }

        running = true;
        Microbot.enableAutoRunOn = false;
        Microbot.log("Starting TrueBloodScript...");

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    Microbot.log("Not logged in. Exiting loop.");
                    return;
                }
                if (!super.run()) {
                    Microbot.log("Super.run() returned false. Exiting loop.");
                    return;
                }

                long startTime = System.currentTimeMillis();
                handleBanking();
                long endTime = System.currentTimeMillis();
                Microbot.log("Total time for loop: " + (endTime - startTime) + " ms");

            } catch (Exception ex) {
                Microbot.log("Error in TrueBloodScript: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        running = false;
        Microbot.log("TrueBloodScript has been shut down.");
    }

    private void updateRegion() {
        if (client.getLocalPlayer() != null) {
            this.currentRegionId = client.getLocalPlayer().getWorldLocation().getRegionID();
        }
    }

    private void handleBanking() {
        updateRegion();
        Microbot.log("Current Region ID: " + currentRegionId);

        if (currentRegionId == 11573) {
            Microbot.log("Player is in the Crafting Guild. Attempting to open bank...");
            boolean bankOpened = Rs2Bank.openBank();
            Microbot.log("Bank Opened: " + bankOpened);

            if (bankOpened) {
                sleepUntil(() -> Rs2Bank.isOpen(), 5000);
                Microbot.log("Bank is open: " + Rs2Bank.isOpen());

                if (Rs2Inventory.hasAnyPouch()) {
                    while (!Rs2Inventory.allPouchesFull() && isRunning()) {
                        Microbot.log("Pouches are not full. Continuing banking process...");
                        if (Rs2Bank.openBank()) {
                            Microbot.log("Withdrawing Purse Essence...");
                            Rs2Bank.withdrawAll("Purse essence");
                            Rs2Inventory.fillPouches();
                            Rs2Inventory.waitForInventoryChanges(1200);
                        } else {
                            Microbot.log("Failed to open bank.");
                            break;
                        }
                    }
                }
            }
        } else {
            Microbot.log("Player is not in the Crafting Guild, skipping bank actions.");
        }
    }
}*/
package net.runelite.client.plugins.microbot.frosty.trueblood;

import lombok.Getter;
import lombok.Setter;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectID;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.itemstats.potions.StaminaPotion;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.frosty.bloodx.enums.Caves;
import net.runelite.client.plugins.microbot.frosty.trueblood.enums.Altar;
import net.runelite.client.plugins.microbot.frosty.trueblood.enums.HomeTeleports;
import net.runelite.client.plugins.microbot.frosty.trueblood.enums.Teleports;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.JewelleryLocationEnum;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.RunePouch;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.frosty.trueblood.enums.State;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.reflection.Rs2Reflection;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class TrueBloodScript extends Script {
    @Inject
    private TrueBloodScript script;
    @Inject
    private TrueBloodConfig config;
    @Inject
    private Client client;
    private int currentRegionId;
    @Getter
    private State state = State.BANKING;
    @Getter
    @Setter
    private Altar altar = Altar.TRUE_BLOOD;
    @Getter
    private int bloodRunesCrafted = 0;
    @Getter
    private int initialRcXp = 0;
    public static boolean test = false;


    public boolean run(TrueBloodConfig config) {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
        Rs2Antiban.setActivity(Activity.CRAFTING_BLOODS_TRUE_ALTAR);
        Rs2Camera.setZoom(200);
        checkPouches();
        initialRcXp = client.getSkillExperience(Skill.RUNECRAFT);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                Teleports selectedTeleport = config.teleports();

                for (Integer itemId : selectedTeleport.getItemIds()) {
                    if (Rs2Inventory.hasItem(itemId)) {

                    }
                }
                if (Rs2Player.getWorldLocation().getRegionID() != selectedTeleport.getBankingRegionId() &&
                !Rs2Inventory.isFull()) {
                    Microbot.log("Not in correct banking region and inventory is not full, attempting to teleport.");
                    handleBankTeleport(config);
                    return;
                }
                if (Rs2Player.getWorldLocation().getRegionID() == 12894) {
                    Rs2Equipment.interact(9781, "Teleport");
                    sleep(Rs2Random.randomGaussian(900, 200));
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
                        break;
                    case IDLE:
                        return;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
            mainScheduledFuture = null;
        }

        scheduledExecutorService.shutdownNow();
        state = State.BANKING;
        bloodRunesCrafted = 0;
        initialRcXp = 0;
    }
    private void updateRegion() {
        if (client.getLocalPlayer() != null) {
            this.currentRegionId = client.getLocalPlayer().getWorldLocation().getRegionID();
            sleep(600);
        }
    }

    private void handleBanking() {
        HomeTeleports selectedHomeTeleport = config.homeTeleports();
        if (Rs2Inventory.anyPouchUnknown()) {
            Rs2Inventory.checkPouches();
            return;
        }
        if (Rs2Inventory.hasDegradedPouch()) {
            Rs2Magic.repairPouchesWithLunar();
            sleep(Rs2Random.randomGaussian(900, 200));
            return;
        }

        if (Rs2Inventory.allPouchesFull() && Rs2Inventory.isFull() &&
                Rs2Inventory.contains("Pure essence")) {
            Microbot.log("We are full, lets go home");
            state = State.GOING_HOME;
            return;
        } else {
            state = State.BANKING;
        }
        updateRegion();
        Microbot.log("Current Region ID: " + currentRegionId);
        Teleports selectedTeleport = config.teleports();
        int bankingRegion = selectedTeleport.getBankingRegionId();

        if (Rs2Player.getWorldLocation().getRegionID() == bankingRegion) {
            Microbot.log("Banking at " + selectedTeleport.getName() + " location");
            Rs2Bank.openBank();
            if (!Rs2Inventory.contains(26392)) {
                Rs2Bank.withdrawItem(26390);
                sleep(Rs2Random.randomGaussian(900, 200));
            }
            if (!Rs2Inventory.contains(8013) && selectedHomeTeleport == HomeTeleports.HOUSE_TAB) {
                Rs2Bank.withdrawX(8013, 50);
            }

            handleRing();
            handleFillPouch();

            if (Rs2Bank.isOpen() && Rs2Inventory.allPouchesFull() && Rs2Inventory.isFull()) {
                Microbot.log("Inventory and pouches are full. Closing bank and going home");
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
    }

    private void handleGoingHome() {
        HomeTeleports selectedHomeTeleport = config.homeTeleports();

        if (selectedHomeTeleport == HomeTeleports.HOUSE_TAB) {
            if (Rs2Inventory.hasItem(8013)) {
                Microbot.log("Using House Teleport tablet to teleport to POH.");
                Rs2Inventory.interact(8013, "Break");
                sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 53739);
                sleep(Rs2Random.randomGaussian(900, 200));
            } else {
                Microbot.log("House Teleport tablet not found.");
            }
        }
        else if (selectedHomeTeleport == HomeTeleports.CONSTRUCTION_CAPE) {
            handleConCape();
        }
        if (Rs2Player.getRunEnergy() < 40) {
            Microbot.log("Low run energy. Drinking from pool...");
            Rs2GameObject.interact(29241, "Drink");
            sleepUntil(() -> (!Rs2Player.isInteracting()) && Rs2Player.getRunEnergy() > 90);
        }
        Microbot.log("Using fairy ring...");
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

    private void handleWalking() {
        if (config.has74Agility()) {
            Microbot.log("Using agility shortcut for cave traversal.");
            handle74Agility();
        } else {
            Microbot.log("Using standard travel method (no agility shortcut).");
            handleWalkingNoAgility();
        }
    }

    private void interactWithFourth(Caves cave) {
        sleep(Rs2Random.randomGaussian(700, 200));
        Microbot.log("Looking for the furthest cave entrance: " + cave.getWhichCave());
        // Get all instances of the cave entrance (ID: 12771)
        List<GameObject> caveEntrances = Rs2GameObject.getGameObjects(cave.getIdCave());
        if (caveEntrances.isEmpty()) {
            Microbot.log("ERROR: No cave entrances found!");
            return;
        }
        WorldPoint playerPosition = Rs2Player.getWorldLocation();
        // Find the furthest cave entrance
        GameObject furthestCave = caveEntrances.stream()
                .max(Comparator.comparingInt(obj -> obj.getWorldLocation().distanceTo(playerPosition)))
                .orElse(null);

        if (furthestCave == null) {
            Microbot.log("ERROR: Could not determine the furthest cave entrance.");
            state = State.BANKING;
            return;
        }
        Microbot.log("Furthest cave entrance found at: " + furthestCave.getWorldLocation());

        Rs2Camera.turnTo(furthestCave);
        sleep(Rs2Random.randomGaussian(900, 200));
        // Interact with the furthest cave entrance
        boolean interacted = Rs2GameObject.interact(furthestCave, "Enter");
        sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 13977 && !Rs2Player.isAnimating());

        if (!interacted) {
            state = State.BANKING;
            Microbot.log("ERROR: Failed to interact with the furthest cave entrance.");
        }
    }
    private void interactWithFifth(Caves cave) {
        Microbot.log("Navigating to the fifth cave entrance: " + cave.getWhichCave());
        Rs2Walker.walkTo(3556, 9823, 0);
        sleep(Rs2Random.randomGaussian(900, 200));
        boolean interacted = Rs2GameObject.interact(cave.getIdCave(), "Enter");
        if (!interacted) {
            Microbot.log("ERROR: Could not interact with the cave (Object ID " + cave.getIdCave() + ")");
        } else { sleepUntil(() -> !Rs2Player.isAnimating());
            sleep(Rs2Random.randomGaussian(900, 200)); }
    }

    private void handle74Agility() {
        for (Caves cave : Caves.values()) {
            Microbot.log("Attempting to enter: " + cave.getWhichCave());
            sleepUntil(() -> Microbot.getClient().getGameState().getState() == 30, 4000);
            sleep(Rs2Random.randomGaussian(1300,200));
            WorldPoint beforePosition = Rs2Player.getWorldLocation();

            int beforeRegion = Rs2Player.getWorldLocation().getRegionID();
            int attempts = 0;
            boolean entered = false;

            if (cave == Caves.FOURTH_CAVE) {
                interactWithFourth(cave);
                sleep(Rs2Random.randomGaussian(900, 200));
                entered = sleepUntil(() -> !Rs2Player.isAnimating() &&
                        (!Rs2Player.getWorldLocation().equals(beforePosition) ||
                                Rs2Player.getWorldLocation().getRegionID() != beforeRegion), 7000);
                sleep(Rs2Random.randomGaussian(900, 200));
            } else if (cave == Caves.AGILITY_74) {
                interactWithFifth(cave);
                entered = sleepUntil(() -> !Rs2Player.isAnimating() &&
                                (!Rs2Player.getWorldLocation().equals(beforePosition) ||
                                        Rs2Player.getWorldLocation().getRegionID() != beforeRegion),
                        7000);
                sleep(Rs2Random.randomGaussian(1100, 200));
            } else {
                while (attempts < 3 && !entered) {
                    Microbot.log("Attempt #" + (attempts + 1) + " to enter " + cave.getWhichCave());
                    sleep(Rs2Random.randomGaussian(1300, 200));

                    boolean interacted = Rs2GameObject.interact(cave.getIdCave(), "Enter");
                    if (!interacted) {
                        Microbot.log("Interaction failed, retrying...");
                        sleep(1800);
                        attempts++;
                        continue;
                    } else { sleep(Rs2Random.randomGaussian(1100, 200));}
                    sleep(Rs2Random.randomGaussian(1300, 200));
                    entered = sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving() &&
                                    (!Rs2Player.getWorldLocation().equals(beforePosition) ||
                                            Rs2Player.getWorldLocation().getRegionID() != beforeRegion),
                            7000);

                    if (!entered) {
                        Microbot.log("Entry attempt failed, retrying...");
                        sleep(Rs2Random.randomGaussian(1500, 200));
                        attempts++;
                    }
                }
            }

            if (!entered) {
                Microbot.log("WARNING: Player did not enter " + cave.getWhichCave() + " after 3 attempts. Skipping...");
                continue;
            }
            sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating(), 1200);
            Microbot.log("Successfully entered: " + cave.getWhichCave());
        }

        Microbot.log("All caves traversed successfully!");
        sleep(Rs2Random.randomGaussian(1300, 400));
        state = State.CRAFTING;
    }

    private void handleWalkingNoAgility() {
        Rs2GameObject.interact(Caves.FIRST_CAVE.getIdCave(), "Enter");
        sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 13977);
        sleep(Rs2Random.randomGaussian(700, 200));
        Rs2Walker.walkTo(3555, 9778, 0);
        sleep(Rs2Random.randomGaussian(1100, 200));
        state = State.CRAFTING;
    }

    private void handleCrafting() {
        Rs2GameObject.interact(25380, "Enter");
        sleepUntil(() -> !Rs2Player.isAnimating() && Rs2Player.getWorldLocation().getRegionID() == 12875);
        sleep(Rs2Random.randomGaussian(700,200));
        Rs2GameObject.interact(43479, "Craft-rune");
        Rs2Player.waitForXpDrop(Skill.RUNECRAFT);

        handleEmptyPouch();
        bloodRunesCrafted = Rs2Inventory.count("Blood rune");

        if (Rs2Inventory.allPouchesEmpty() && !Rs2Inventory.contains("Pure essence")) {
            handleBankTeleport(config);
        }


        /*if (Rs2Inventory.allPouchesEmpty() && !Rs2Inventory.contains("Pure essence")) {
            sleep(Rs2Random.randomGaussian(1300,200));
            Rs2Tab.switchToEquipmentTab();
            sleep(Rs2Random.randomGaussian(900, 200));
            Rs2Equipment.interact(9781, "Teleport");
            sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 11571);
            Rs2Tab.switchToInventoryTab();
        } else { handleCrafting(); }*/
        state = State.BANKING;
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
    private void handleFillPouch() {
        while (!Rs2Inventory.allPouchesFull() || !Rs2Inventory.isFull() && isRunning()) {
            Microbot.log("Pouches are not full. Continuing banking process...");
            if (Rs2Bank.isOpen()) {
                if (Rs2Inventory.contains("Blood rune")) {
                    Rs2Bank.depositAll("Blood rune");
                    sleep(Rs2Random.randomGaussian(500, 200));
                }
                //sleep(Rs2Random.randomGaussian(700, 300));
                Microbot.log("Withdrawing Pure Essence...");
                Rs2Bank.withdrawAll("Pure essence");
                sleep(Rs2Random.randomGaussian(500,200));
                Rs2Inventory.fillPouches();
                Rs2Inventory.waitForInventoryChanges(600);
            }
            if (!Rs2Inventory.isFull()) {
                Rs2Bank.withdrawAll("Pure essence");
                sleepUntil(Rs2Inventory::isFull);
            }
        }
    }
    private void checkPouches() {
        Rs2Inventory.interact(26784, "Check");
    }

    private void handleConCape(){
        if (Rs2Inventory.hasItem(9790)) {
            Microbot.log("Found Construction Cape, using it to teleport to POH.");
            Rs2Inventory.interact(9790, "Tele to POH");
            sleepUntil(() -> Rs2Player.getWorldLocation() != null
                    && Rs2Player.getWorldLocation().getRegionID() == 53739
                    && !Rs2Player.isAnimating());
            sleep(Rs2Random.randomGaussian(900, 200));
        } else {
            Microbot.log("No Construction Cape found, returning.");
        }
    }
    /*private void handleCraftingCape(TrueBloodConfig config) {
        if (config.useCraftingCape() && Rs2Inventory.allPouchesEmpty() && !Rs2Inventory.contains("Pure essence")) {
            Rs2Tab.switchToEquipmentTab();
            sleep(Rs2Random.randomGaussian(1300, 200));
            Rs2Equipment.interact(9781, "Teleport");
            sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 11571);
            Rs2Tab.switchToInventoryTab();
        }
    }*/
    private void handleBankTeleport(TrueBloodConfig config) {
        Teleports selectedTeleport = config.teleports();
        if (!Rs2Inventory.allPouchesEmpty() || Rs2Inventory.contains("Pure essence")) {
            handleCrafting();
            return;
        }

        Rs2Tab.switchToEquipmentTab();
        sleep(Rs2Random.randomGaussian(1300, 200));

        if (selectedTeleport == Teleports.CRAFTING_CAPE) {
            Microbot.log("Using Crafting Cape to teleport.");
            Rs2Equipment.interact(9781, "Teleport");
            sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 11571);
            Rs2Tab.switchToInventoryTab();
            state = State.BANKING;
            return;
        }
        else if (selectedTeleport == Teleports.RING_OF_DUELING) {
            for (Integer itemId : selectedTeleport.getItemIds()) {
                if (Rs2Equipment.isWearing("Ring of dueling")) {
                    Microbot.log("Using Ring of Dueling (ID: " + itemId + ") to teleport to Castle Wars.");
                    Rs2Equipment.useRingAction(JewelleryLocationEnum.CASTLE_WARS);
                    sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 9776);
                    sleep(Rs2Random.randomGaussian(700, 200));
                    Rs2Tab.switchToInventoryTab();
                    state = State.BANKING;
                    return;
                }
            }
        }
    }

    private void handleRing() {
        boolean hasEquipped = false;

        for (int id : config.teleports().getItemIds()) {
            if (Rs2Equipment.hasEquipped(id)) {
                Microbot.log("We have ring equipped");
                hasEquipped = true;
                break;
            }
        }
        if (!hasEquipped) {
            Rs2Bank.withdrawAndEquip(config.teleports().getItemIds()[0]);
            sleep(Rs2Random.randomGaussian(500, 200));
        }
    }

    /*private void handleStamina() {
        if (Rs2Player.getRunEnergy() <40) {
            Rs2Bank.withdrawItem("Stamina potion");
            Rs2Inventory.waitForInventoryChanges(1200);
            Rs2Inventory.interact("Stamina potion", "Drink");
            sleepUntil(Rs2Player::hasStaminaBuffActive);
            sleep(Rs2Random.randomGaussian(900, 200));
        }
    }*/



    /*private void handleGear() {
        if (!Rs2Equipment.hasEquipped(9781)) { //Crafting cape
            Rs2Bank.withdrawAndEquip(9781);
        }
        if (!Rs2Inventory.contains(9790)) { //Con cape
            Rs2Bank.withdrawItem(9790);
        }
    }*/
}
