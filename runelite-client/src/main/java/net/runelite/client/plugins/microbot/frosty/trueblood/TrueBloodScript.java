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

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.frosty.bloodx.enums.Caves;
import net.runelite.client.plugins.microbot.frosty.trueblood.enums.Altar;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.frosty.trueblood.enums.State;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;


public class TrueBloodScript extends Script {
    @Inject
    private TrueBloodScript script;
    @Inject
    private Client client;

    private int currentRegionId;
    private State state = State.BANKING;

    public static boolean test = false;

    public boolean run(TrueBloodConfig config) {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
        Rs2Antiban.setActivity(Activity.CRAFTING_BLOODS_TRUE_ALTAR);
        checkPouches();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();


                //CODE HERE
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
        state = State.BANKING;
    }


    private void updateRegion() {
        if (client.getLocalPlayer() != null) {
            this.currentRegionId = client.getLocalPlayer().getWorldLocation().getRegionID();
        }
    }

    private void handleBanking() {
        boolean pouchesAreFull = Rs2Inventory.allPouchesFull();
        boolean inventoryIsFull = Rs2Inventory.isFull();

        if (pouchesAreFull && inventoryIsFull) {
            Microbot.log("Pouches and inventory are full. Skipping banking and going home...");
            state = State.GOING_HOME;
            return; // Exit method early to prevent opening the bank
        }

        if (Rs2Inventory.hasDegradedPouch()) {
            Rs2Magic.repairPouchesWithLunar();
            sleep(600);
            checkPouches();
            return;
        }

        updateRegion();
        Microbot.log("Current Region ID: " + currentRegionId);

        if (currentRegionId == 11571) {
            boolean bankOpen = Rs2Bank.openBank();
            if (bankOpen) {
                sleepUntil(Rs2Bank::isOpen, 5000);
                if (!pouchesAreFull || !inventoryIsFull) {
                    handleFillPouch();
                } else {
                    state = State.GOING_HOME;
                }
                if (Rs2Bank.isOpen() && Rs2Inventory.allPouchesFull() && Rs2Inventory.isFull()) {
                    Microbot.log("Inventory and pouches are full. Closing bank and switching to GOING_HOME...");
                    Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                    sleepUntil(() -> !Rs2Bank.isOpen(), 1200);
                    state = State.GOING_HOME;
                }
            }
        } else {
            Microbot.log("Player is not in the Crafting Guild, skipping bank actions.");
        }
    }

    private void handleGoingHome() {
        handleConCape();
        if (Rs2Player.getRunEnergy() < 40) {
            Microbot.log("Low run energy. Drinking from pool...");
            Rs2GameObject.interact(29241, "Drink");
            sleepUntil(() -> !Rs2Player.isInteracting());
        }

        Microbot.log("Using fairy ring...");
        if (!Rs2GameObject.interact(27097, "Ring-last-destination (DLS)")) {
            Microbot.log("Failed to use fairy ring.");
            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving()
                    && Rs2Player.getWorldLocation() != null
                    && Rs2Player.getWorldLocation().getRegionID() == 13721, 1200);
            return;
        }
        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving()
                && Rs2Player.getWorldLocation().getRegionID() == 13721, 1200);

        Microbot.log("Teleport successful! Switching to IDLE.");
        state = State.WALKING_TO;
    }

    private void handleWalking() {
        for (Caves cave : Caves.values()) {
            Microbot.log("Attempting to enter: " + cave.getWhichCave());

            // Ensure game state is stable
            sleepUntil(() -> Microbot.getClient().getGameState().getState() == 30, 4000);
            sleep(1200);

            // Stop player movement before interacting
            sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating());

            // Store the player's position and region before interacting
            WorldPoint beforePosition = Rs2Player.getWorldLocation();
            int beforeRegion = Rs2Player.getWorldLocation().getRegionID();

            int attempts = 0;
            boolean entered = false;

            if (cave == Caves.FOURTH_CAVE) {
                interactWithFourth(cave);
                entered = sleepUntil(() -> !Rs2Player.isAnimating() &&
                                (!Rs2Player.getWorldLocation().equals(beforePosition) ||
                                        Rs2Player.getWorldLocation().getRegionID() != beforeRegion),
                        7000);
                sleep(1200);
            } else if (cave == Caves.AGILITY_74) {
                interactWithFifth(cave);
                entered = sleepUntil(() -> !Rs2Player.isAnimating() &&
                                (!Rs2Player.getWorldLocation().equals(beforePosition) ||
                                        Rs2Player.getWorldLocation().getRegionID() != beforeRegion),
                        7000);
                sleep(1200);
            } else {
                // Default handling for all other caves, with retries
                while (attempts < 3 && !entered) {
                    Microbot.log("Attempt #" + (attempts + 1) + " to enter " + cave.getWhichCave());

                    boolean interacted = Rs2GameObject.interact(cave.getIdCave(), "Enter");
                    if (!interacted) {
                        Microbot.log("Interaction failed, retrying...");
                        sleep(1800);
                        attempts++;
                        continue;
                    }

                    // Wait for position or region change
                    entered = sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving() &&
                                    (!Rs2Player.getWorldLocation().equals(beforePosition) ||
                                            Rs2Player.getWorldLocation().getRegionID() != beforeRegion),
                            7000
                    );
                    sleep(1200);

                    if (!entered) {
                        Microbot.log("Entry attempt failed, retrying...");
                        sleep(1200); // Small delay before retrying
                        attempts++;
                    }
                }
            }

            if (!entered) {
                Microbot.log("WARNING: Player did not enter " + cave.getWhichCave() + " after 3 attempts. Skipping...");
                continue;
            }

            // Ensure the player has stopped moving before continuing
            sleepUntil(() -> !Rs2Player.isMoving(), 3000);
            sleep(1200);

            Microbot.log("Successfully entered: " + cave.getWhichCave());
        }

        Microbot.log("All caves traversed successfully!");
        Rs2Random.wait(400, 800);
        state = State.CRAFTING;
    }

    private void interactWithFourth(Caves cave) {
        Microbot.log("Looking for the furthest cave entrance: " + cave.getWhichCave());

        // Get all instances of the cave entrance (ID: 12771)
        List<GameObject> caveEntrances = Rs2GameObject.getGameObjects(cave.getIdCave());
        if (caveEntrances.isEmpty()) {
            Microbot.log("ERROR: No cave entrances found!");
            return;
        }

        // Get the player's position
        WorldPoint playerPosition = Rs2Player.getWorldLocation();

        // Find the furthest cave entrance
        GameObject furthestCave = caveEntrances.stream()
                .max(Comparator.comparingInt(obj -> obj.getWorldLocation().distanceTo(playerPosition)))
                .orElse(null);

        if (furthestCave == null) {
            Microbot.log("ERROR: Could not determine the furthest cave entrance.");
            return;
        }

        Microbot.log("Furthest cave entrance found at: " + furthestCave.getWorldLocation());

        // Interact with the furthest cave entrance
        boolean interacted = Rs2GameObject.interact(furthestCave, "Enter");

        // Wait until the player has entered the correct region
        sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 13977 && !Rs2Player.isAnimating());

        if (!interacted) {
            Microbot.log("ERROR: Failed to interact with the furthest cave entrance.");
        }
    }

    private void interactWithFifth(Caves cave) {
        Microbot.log("Navigating to the fifth cave entrance: " + cave.getWhichCave());

        // Walk to the expected location before interacting
        Rs2Walker.walkTo(3560, 9814, 0);
        sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating(), 5000);
        Rs2Random.wait(600, 1000);

        // Try interacting directly with the object by ID
        boolean interacted = Rs2GameObject.interact(cave.getIdCave(), "Enter");

        if (!interacted) {
            Microbot.log("ERROR: Could not interact with the cave (Object ID " + cave.getIdCave() + ")");
        }
    }


    private void handleCrafting() {
        Altar altar = Altar.TRUE_BLOOD;

        // Ensure we're at the altar before interacting
        if (!Rs2Player.getWorldLocation().equals(altar.getAltarWorldPoint())) {
            Microbot.log("Walking to the altar...");
            Rs2Walker.walkTo(altar.getAltarWorldPoint());
            sleepUntil(() -> Rs2Player.getWorldLocation().equals(altar.getAltarWorldPoint()), 5000);
            sleep(600);
        }

        // Try to enter the altar
        boolean enteredAltar = Rs2GameObject.interact(altar.getAltarID(), "Enter");
        if (!enteredAltar) {
            Microbot.log("ERROR: Could not interact with the altar (ID: " + altar.getAltarID() + ")");
            return;
        }
        sleepUntil(() -> !Rs2Player.isAnimating(), 5000);

        // Try to craft runes at the ruins
        boolean craftingRunes = Rs2GameObject.interact(altar.getRuinsID(), "Craft-rune");
        if (!craftingRunes) {
            Microbot.log("ERROR: Could not craft runes at the altar.");
            return;
        }

        sleepUntil(() -> !Rs2Player.isAnimating(), 5000);

        // Change state only after crafting completes successfully
        state = State.BANKING;
    }



    private void handleFillPouch() {
        while (!Rs2Inventory.allPouchesFull() && isRunning()) {
            Microbot.log("Pouches are not full. Continuing banking process...");
            if (Rs2Bank.openBank()) {
                Microbot.log("Withdrawing Pure Essence...");
                Rs2Bank.withdrawAll("Pure essence");
                Rs2Inventory.fillPouches();
                Rs2Inventory.waitForInventoryChanges(1200);
            }
            if (!Rs2Inventory.isFull()) {
                Rs2Bank.withdrawAll("Pure essence");
                sleepUntil(Rs2Inventory::isFull);
            }
        }
    }
    private void handleConCape(){
        if (Rs2Inventory.hasItem(9790)) {
            Microbot.log("Found Construction Cape, using it to teleport to POH.");
            Rs2Inventory.interact(9790, "Tele to POH");
            sleepUntil(() -> Rs2Player.getWorldLocation() != null
                    && Rs2Player.getWorldLocation().getRegionID() == 53739
                    && !Rs2Player.isAnimating());
            sleep(600, 800);
        } else {
            Microbot.log("No Construction Cape found, returning.");
            return;
        }
    }
    private void checkPouches() {
        Rs2Inventory.interact(26784, "Check");
    }


}