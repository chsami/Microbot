package net.runelite.client.plugins.microbot.frosty.bloodx;

import com.google.inject.Inject;
import lombok.Getter;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.frosty.bloodx.enums.Altar;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.frosty.bloodx.enums.Locations;
import net.runelite.client.plugins.microbot.frosty.bloodx.enums.Caves;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.frosty.bloodx.enums.State;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.List;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public class BloodxScript extends Script {

    private State state;
    private Altar altar;
    private boolean initialise;
    public static int initialRcXp;
    public static int initialRcLvl;

    public boolean run(BloodxConfig config) {
        Microbot.enableAutoRunOn = true;
        Microbot.log("Script started: BloodxScript");

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_RUNECRAFT);

        Microbot.log("Initializing the script...");
        initialRcXp = Microbot.getClient().getSkillExperience(Skill.RUNECRAFT);
        initialRcLvl = Rs2Player.getRealSkillLevel(Skill.RUNECRAFT);
        Microbot.log("Initial Runecrafting XP: " + initialRcXp + ", Level: " + initialRcLvl);

        determineStartingState();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    Microbot.log("Not logged in. Pausing...");
                    return;
                }

                if (!super.run()) {
                    Microbot.log("Super.run() returned false. Exiting...");
                    return;
                }

                if (!Rs2Equipment.isWearing("Hat of the eye")) {
                    Microbot.log("No 'Hat of the eye' equipped. Switching to BANKING state.");
                    state = State.BANKING;
                    initialise = false;
                    return;
                }

                if (!Rs2Inventory.hasItem("Pure Essence", false) && !Rs2Inventory.allPouchesFull()) {
                    Microbot.log("No Pure Essence in inventory. Switching to BANKING state.");
                    state = State.BANKING;
                    initialise = false;
                    return;
                }
                state = State.GOING_HOME;

                if (Rs2Player.isMoving() || Rs2Player.isInteracting() || Microbot.pauseAllScripts) {
                    Microbot.log("Player is busy (moving/interacting/paused). Waiting...");
                    return;
                }

                switch (state) {
                    case BANKING:
                        Microbot.log("State: BANKING");
                        initialise = false;

                        if (!Rs2Bank.openBank()) {
                            Microbot.log("Failed to open bank. Retrying...");
                            return;
                        }
                        Microbot.status = "Opening bank";

                        if (Rs2Inventory.hasItem("Blood rune", false)) {
                            Rs2Bank.depositAll("Blood rune");
                            Rs2Inventory.waitForInventoryChanges(1500);
                        }

                        handleFillPouch();
                        sleep(1200);

                        state = State.GOING_HOME;
                        Microbot.log("Banking complete. Switching to GOING_HOME state.");
                        break;

                    case GOING_HOME:
                        Microbot.log("State: GOING_HOME");
                        initialise = false;
                        Microbot.status = "Teleporting to POH";
                        if (Rs2Inventory.hasItem(9790)) {
                            Microbot.log("Found Construction Cape, using it to teleport to POH.");
                            Rs2Inventory.interact(9790, "Tele to POH");
                            if (!sleepUntil(PohTeleports::checkIsInHouse, 8000)) {
                                Microbot.log("Teleport to POH failed or took too long.");
                                return;
                            }
                        } else {
                            Microbot.log("No Construction Cape found, returning.");
                            return;
                        }
                        if (Rs2Player.getRunEnergy() < 40) {
                            Microbot.log("Low run energy. Drinking from pool...");
                            Rs2GameObject.interact(29241, "Drink");
                            sleepUntil(() -> !Rs2Player.isInteracting());
                        }

                        Microbot.log("Using fairy ring...");
                        if (!Rs2GameObject.interact(27097, "Ring-last-destination (DLS)")) {
                            Microbot.log("Failed to use fairy ring.");
                            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving()
                                    && Rs2Player.getWorldLocation().getRegionID() == 13721, 5000);
                            return;
                        }

                        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving()
                                && Rs2Player.getWorldLocation().getRegionID() == 13721, 5000);

                        state = State.WALKING_TO;
                        Microbot.log("Teleportation complete. Switching to WALKING_TO state.");
                        break;

                    case WALKING_TO:
                        Microbot.log("State: WALKING_TO - Navigating through caves");
                        initialise = false;
                        traverseCaves();
                        state = State.CRAFTING;
                        break;

                    case CRAFTING:
                        Microbot.log("State: CRAFTING - Crafting Blood Runes");
                        initialise = false;
                        Microbot.status = "Crafting Blood Runes";

                        // Define the altar (currently only one, but expandable)
                        Altar altar = Altar.TRUE_BLOOD;

                        // Walk to the altar if not already there
                        if (!Rs2Player.getWorldLocation().equals(altar.getAltarWorldPoint())) {
                            Microbot.log("Walking to the altar...");
                            Rs2Walker.walkTo(altar.getAltarWorldPoint());
                            sleepUntil(() -> Rs2Player.getWorldLocation().equals(altar.getAltarWorldPoint()), 5000);
                            sleep(600); // Small delay to prevent early interaction
                        }

                        // Interact with the altar to craft runes
                        boolean interacted = Rs2GameObject.interact(altar.getAltarID(), "Craft-rune");

                        if (!interacted) {
                            Microbot.log("ERROR: Could not interact with the altar (ID: " + altar.getAltarID() + ")");
                            return;
                        }

                        // Wait until the crafting animation completes
                        sleepUntil(() -> !Rs2Player.isAnimating(), 5000);

                        Microbot.log("Successfully crafted Blood Runes at " + altar.getAltarName());

                        // Move to the next state (back to banking to restart the loop)
                        state = State.BANKING;
                        break;


                    default:
                        Microbot.log("Unhandled state: " + state);
                        break;
                }
            } catch (Exception ex) {
                Microbot.log("Exception caught: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);

        Microbot.log("Script initialization complete.");
        return true;
    }

    private void handleFillPouch() {
        if (Rs2Inventory.hasAnyPouch()) {
            while (!Rs2Inventory.allPouchesFull() && isRunning()) {
                Rs2Bank.withdrawAll("Purse essence");Rs2Inventory.waitForInventoryChanges(800);
                Rs2Inventory.fillPouches();
                Rs2Inventory.waitForInventoryChanges(1200);
            }
        }
    }
    private void traverseCaves() {
        for (Caves cave : Caves.values()) {
            Microbot.log("Attempting to enter: " + cave.getWhichCave());
            sleepUntil(() -> Microbot.getClient().getGameState().getState() == 30, 4000);
            sleep(800);
            sleepUntil(() -> !Rs2Player.isMoving(), 2000);


            if (cave == Caves.FOURTH_CAVE) {
                interactWithFourth(cave);
            } else if (cave == Caves.AGILITY_74) {
                interactWithFifth(cave);
            } else {
                boolean interacted = Rs2GameObject.interact(cave.getIdCave(), "Enter");
                if (!interacted) {
                    Microbot.log("Failed to click cave: " + cave.getWhichCave() + ". Retrying...");
                    continue;
                }
            }

            Microbot.log("Waiting for entry confirmation: " + cave.getWhichCave());
            boolean enteredCave = sleepUntil(() -> Rs2Player.getWorldLocation().equals(cave.getCaveWorldPoint()), 7000);
            if (!enteredCave) {
                Microbot.log("WARNING: Player did not enter " + cave.getWhichCave() + " properly. Retrying...");
                continue;
            }

            sleepUntil(() -> !Rs2Player.isMoving(), 3000);
            sleep(1200);

            Microbot.log("Successfully entered: " + cave.getWhichCave());
        }

        Microbot.log("All caves traversed successfully!");
    }

    private void interactWithFourth(Caves cave) {
        Microbot.log("Looking for specific cave entrance: " + cave.getWhichCave());

        Rs2Walker.walkTo(3492, 9862, 0);
        sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating());
        Rs2Random.wait(600, 1000);

        Rs2GameObject.getGameObjects(cave.getIdCave()).stream()
                .filter(obj -> obj.getWorldLocation().equals(cave.getCaveWorldPoint())) // Match by WorldPoint
                .findFirst()
                .ifPresentOrElse(
                        obj -> {
                            Microbot.log("Interacting with cave at: " + obj.getWorldLocation());
                            Rs2GameObject.interact("Cave entrance", "Enter");
                        },
                        () -> Microbot.log("ERROR: Could not find the specific cave at: " + cave.getCaveWorldPoint())
                );
    }

    private void interactWithFifth(Caves cave) {
        Microbot.log("Navigating to the fifth cave entrance: " + cave.getWhichCave());
        Rs2Walker.walkTo(3560, 9814, 0);
        sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating(), 5000);
        Rs2Random.wait(600, 1000);
        boolean interacted = Rs2GameObject.interact(cave.getIdCave(), "Enter");

        if (!interacted) {
            Microbot.log("ERROR: Could not interact with the cave (Object ID " + cave.getIdCave() + ")");
        }
    }

    private void determineStartingState() {
        Microbot.log("Determining starting state...");

        if (Rs2Inventory.hasItem("Pure Essence", false) || Rs2Inventory.allPouchesFull()) {
            Microbot.log("Player has Pure Essence. Skipping BANKING.");

            if (isInsideCaveSystem()) {
                Microbot.log("Player is inside the cave system. Skipping WALKING_TO.");
                if (Rs2Player.getWorldLocation().equals(Altar.TRUE_BLOOD.getAltarWorldPoint())) {
                    Microbot.log("Player is at the Blood Altar. Starting with CRAFTING.");
                    state = State.CRAFTING;
                    return;
                }

                Microbot.log("Player is inside the cave system but not at the altar. Starting with WALKING_TO.");
                state = State.WALKING_TO;
                return;
            }

            Microbot.log("Player is outside caves but has essence. Starting with GOING_HOME.");
            state = State.GOING_HOME;
            return;
        }

        Microbot.log("Player has no Pure Essence. Starting with BANKING.");
        state = State.BANKING;
    }


    private boolean isInsideCaveSystem() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        for (Caves cave : Caves.values()) {
            if (playerLocation.equals(cave.getCaveWorldPoint())) {
                return true;
            }
        }
        return false;
    }


    @Override
    public void shutdown() {
        super.shutdown();
        Microbot.log("Shutting down BloodxScript...");

        // Cancel scheduled executor to stop script execution
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
        }

        // Reset all script-related variables
        state = null;
        altar = null;

        // Reset Anti-ban settings
        Rs2Antiban.resetAntibanSettings();

        // Reset any inventory or state flags
        initialRcXp = 0;
        initialRcLvl = 0;

        Microbot.enableAutoRunOn = false; // Reset auto-run setting
        Microbot.status = "Idle"; // Set status to idle

        Microbot.log("BloodxScript has been fully stopped.");
    }

}