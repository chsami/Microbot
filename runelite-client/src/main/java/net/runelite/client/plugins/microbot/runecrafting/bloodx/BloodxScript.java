package net.runelite.client.plugins.microbot.runecrafting.bloodx;

import com.google.inject.Inject;
import lombok.Getter;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.runecrafting.bloodx.enums.Camera;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.runecrafting.bloodx.enums.Locations;
import net.runelite.client.plugins.microbot.runecrafting.bloodx.enums.Caves;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.runecrafting.bloodx.enums.State;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.List;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public class BloodxScript extends Script {

    @Getter
    private State state;
    private Locations locations;
    private Camera camera;
    private boolean initialise;
    public static int initialRcXp;
    public static int initialRcLvl;

    @Inject
    private OverlayManager overlayManager;

    private BloodxOverlay overlay;
    @Getter
    private int bloodRunesCrafted = 0;
    public int getInitialRcXp() {
        return initialRcXp;
    }


    public boolean run(BloodxConfig config) {
        Microbot.enableAutoRunOn = true;
        overlay = new BloodxOverlay(Microbot.getClient(), this);
        overlayManager.add(overlay);


        Microbot.log("Script started: BloodxScript");

        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_RUNECRAFT);
        determineStartingState();


        initialise = true;

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

                if (Rs2Inventory.anyPouchUnknown()) {
                    Rs2Inventory.checkPouches();
                    return;
                }

                if (initialise) {
                    Microbot.log("Initializing the script...");
                    initialRcXp = Microbot.getClient().getSkillExperience(Skill.RUNECRAFT);
                    initialRcLvl = Rs2Player.getRealSkillLevel(Skill.RUNECRAFT);
                    Microbot.log("Initial Runecrafting XP: " + initialRcXp + ", Level: " + initialRcLvl);

                    if (!Rs2Equipment.isWearing("Hat of the eye")) {
                        Microbot.log("No 'Hat of the eye' equipped. Switching to BANKING state.");
                        state = State.BANKING;
                        initialise = false;
                        return;
                    }
                    if (!Rs2Inventory.hasItem("Pure Essence", false)) {
                        Microbot.log("No Pure Essence in inventory.");
                        state = State.BANKING;
                        initialise = false;
                        return;
                    }
                    if (!Rs2Inventory.hasItem("", true)) {
                        Microbot.log("No pouch in inventory");
                        state = State.BANKING;
                        initialise = false;
                        return;
                    }
                    Microbot.log("Initialization complete. Switching to GOING_HOME state.");
                    state = State.GOING_HOME;
                }
                if (Rs2Player.isMoving() || Rs2Player.isInteracting() || Microbot.pauseAllScripts) {
                    Microbot.log("Player is busy (moving/interacting/paused). Waiting...");
                    return;
                }

                switch (state) {
                    case BANKING:
                        Microbot.log("State: BANKING");
                        initialise = false;
                        Microbot.status = "Banking";

                        if (!Rs2Bank.openBank()) {
                            Microbot.log("Failed to open bank. Retrying...");
                            return;
                        }

                        //make inventory clean
                        if (Rs2Inventory.hasItem("Blood rune", false) && Rs2Inventory.hasRunePouch()) {
                            Microbot.log("Depositing Blood runes...");
                            Rs2Bank.depositAll("Blood rune");
                            Rs2Random.wait(300, 600);
                            Rs2Bank.depositRunePouch();
                            Rs2Inventory.waitForInventoryChanges(1500);
                        }

                        // Repair degraded pouches before use
                        if (Rs2Inventory.hasItem(26786)) {
                            state = State.REPAIR_POUCHES;
                            return;
                        }

                        if (Rs2Inventory.hasAnyPouch() && Rs2Inventory.anyPouchEmpty() && !Rs2Inventory.isFull()) {
                            Microbot.log("Filling these damn pouches");
                            state = State.FILL_POUCHES;
                            return;
                        }

                        Rs2Bank.closeBank();

                        Microbot.log("Going home!!");
                        state = State.GOING_HOME;
                        break;


                    case GOING_HOME:
                        Microbot.log("State: GOING_HOME");
                        initialise = false;
                        Microbot.status = "Teleporting to POH";

                        if (!Rs2Equipment.interact(9790, "Tele to POH")) {
                            Microbot.log("Failed to teleport to POH.");
                            return;
                        }

                        sleepUntil(() -> !Rs2Player.isAnimating() && Rs2Player.getLocalLocation().isInScene());
                        if (Rs2Player.getRunEnergy() < 40) {
                            Microbot.log("Low run energy. Drinking from pool...");
                            Rs2GameObject.interact(29241, "Drink");
                            sleepUntil(() -> !Rs2Player.isInteracting() && Rs2Player.getRunEnergy() > 90);
                        }

                        Microbot.log("Using fairy ring...");
                        if (!Rs2GameObject.interact(27097, "Ring-last-destination (DLS)")) {
                            Microbot.log("Failed to use fairy ring.");
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
                        Microbot.status = "Walking to";
                        traverseCaves();
                        sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 14232);
                        state = State.ENTERING;
                        break;

                    case ENTERING:
                        Microbot.log("Entering ruins...");
                        initialise = false;
                        Microbot.status = "Entering ruins";

                        // Initialize locations if it's null
                        if (locations == null) {
                            Microbot.log("Warning: Locations variable was null, initializing...");
                            locations = Locations.TRUE_BLOOD; // Assign the correct location enum here
                        }

                        // Store the player's position and region before interacting
                        WorldPoint beforePosition = Rs2Player.getWorldLocation();
                        int beforeRegion = Rs2Player.getWorldLocation().getRegionID();

                        boolean enterRuins = Rs2GameObject.interact(locations.getRuinsID(), "Enter");

                        if (!enterRuins) {
                            Microbot.log("Interaction returned false, but checking for movement...");
                        }

                        boolean enteredRuins = sleepUntil(() ->
                                        !Rs2Player.getWorldLocation().equals(beforePosition) ||
                                                Rs2Player.getWorldLocation().getRegionID() != beforeRegion,
                                3000
                        );

                        if (!enteredRuins) {
                            Microbot.log("WARNING: Player did not enter the ruins properly. Retrying...");
                            return;
                        }

                        sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating(), 3000);
                        sleep(1200);

                        Microbot.log("Successfully entered the ruins.");
                        state = State.CRAFTING;
                        break;


                    case CRAFTING:
                        Microbot.log("State: CRAFTING - Crafting Blood Runes");
                        initialise = false;
                        Microbot.status = "Crafting Blood Runes";

                        // Define the altar location
                        Locations locations = Locations.TRUE_BLOOD;

                        while (Rs2Inventory.hasItem("Pure essence", false) || (Rs2Inventory.hasAnyPouch() && !Rs2Inventory.allPouchesEmpty())) {
                            // Interact with altar to craft runes
                            boolean interacted = Rs2GameObject.interact(locations.getAltarID(), "Craft-rune");
                            if (!interacted) {
                                Microbot.log("ERROR: Could not interact with the locations (ID: " + locations.getAltarID() + ")");
                                return;
                            }

                            // Wait until XP drop confirms successful crafting
                            sleepUntil(() -> Rs2Player.waitForXpDrop(Skill.RUNECRAFT, 2000));

                            Microbot.log("Successfully crafted Blood Runes at " + locations.getAltarName());

                            // Empty pouches if any essence remains
                            if (Rs2Inventory.hasAnyPouch() && !Rs2Inventory.allPouchesEmpty()) {
                                Microbot.log("Emptying pouches...");
                                Rs2Inventory.emptyPouches();
                                sleep(500);
                            }
                        }
                        bloodRunesCrafted += Rs2Inventory.count("Blood rune");

                        // Move to the next state (back to banking to restart the loop)
                        Microbot.log("All essence used. Exiting.");
                        state = State.EXITING;
                        break;


                    case EXITING:
                        Microbot.log("Lets go");
                        initialise = false;
                        Microbot.status = "Leaving";

                        if (!Rs2Inventory.isFull() && Rs2Inventory.contains("Blood rune")
                                && Rs2Inventory.allPouchesEmpty()) {
                            Rs2Inventory.interact("Crafting cape(t)", "Teleport");
                        }

                        sleepUntil(() -> !Rs2Player.isAnimating());
                        state = State.BANKING;
                        break;

                    case FILL_POUCHES:
                        Microbot.log("Filling Pouches");
                        initialise = false;
                        Microbot.status = "Filling damn pouches";

                        if (!Rs2Bank.isOpen()) {
                            Rs2Bank.openBank();
                            Rs2Random.wait(300, 600);
                        }

                        while (Rs2Bank.isOpen() && Rs2Inventory.contains("Pure essence")) {
                            Rs2Bank.withdrawAll("Pure essence");
                            sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(3000));

                            while (Rs2Inventory.hasItem("Pure essence") && !Rs2Inventory.allPouchesFull()
                            ) {
                                Rs2Inventory.fillPouches();
                                sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(3000));
                            }
                        }

                        Rs2Bank.closeBank();
                        state = State.GOING_HOME;
                        break;



                    case REPAIR_POUCHES:
                        System.out.println("Repairing pouches");
                        initialise = false;
                        Microbot.status = "Pouch repair";

                        if (Rs2Inventory.hasItem(26786)) {
                            Rs2Bank.withdrawRunePouch();
                            sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(3000));
                            Rs2Bank.closeBank();
                            Rs2Magic.repairPouchesWithLunar();
                            sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(3000));
                            Rs2Bank.openBank();
                            Rs2Bank.depositRunePouch();
                            Rs2Random.randomGaussian(700, 300);
                            Rs2Bank.closeBank();
                            state = State.BANKING;
                            break;
                        }



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

    private void traverseCaves() {
        for (Caves cave : Caves.values()) {
            Microbot.log("Attempting to enter: " + cave.getWhichCave());
            // Ensure game state is stable
            sleepUntil(() -> Microbot.getClient().getGameState().getState() == 30, 4000);
            sleep(800);
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
                                !Rs2Player.getWorldLocation().equals(beforePosition) ||
                                        Rs2Player.getWorldLocation().getRegionID() != beforeRegion,
                        7000);
            } else if (cave == Caves.AGILITY_74) {
                interactWithFifth(cave);
                entered = sleepUntil(() -> !Rs2Player.isAnimating() &&
                                !Rs2Player.getWorldLocation().equals(beforePosition) ||
                                        Rs2Player.getWorldLocation().getRegionID() != beforeRegion,
                        7000);
            } else {
                // Default handling for all other caves
                while (attempts < 3 && !entered) { // Retry up to 3 times
                    Microbot.log("Attempt #" + (attempts + 1) + " to enter " + cave.getWhichCave());

                    boolean interacted = Rs2GameObject.interact(cave.getIdCave(), "Enter");
                    if (!interacted) {
                        Microbot.log("Interaction failed, retrying...");
                        sleep(1000); // Short delay before retrying
                        attempts++;
                        continue;
                    }

                    // Wait for position or region change
                    entered = sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving() &&
                                    !Rs2Player.getWorldLocation().equals(beforePosition) ||
                                            Rs2Player.getWorldLocation().getRegionID() != beforeRegion,
                            7000
                    );

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
        sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 13977
        && !Rs2Player.isAnimating());
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

    private void handleCraftingCape() {
        Rs2Inventory.interact("Crafting cape", "Teleport");
        sleepUntil(() -> !Rs2Player.isAnimating());
    }

    private void determineStartingState() {
        Microbot.log("Determining starting state...");

        // Check if inventory already has Pure Essence (player has already banked)
        if (Rs2Inventory.hasItem("Pure Essence", false)) {
            Microbot.log("Player has Pure Essence. Skipping BANKING.");

            // Check if the player is inside the cave system
            if (isInsideCaveSystem()) {
                Microbot.log("Player is inside the cave system. Skipping WALKING_TO.");

                // Check if the player is near the Blood Locations
                if (Rs2Player.getWorldLocation().equals(Locations.TRUE_BLOOD.getRuinsWorldPoint())) {
                    Microbot.log("Player is at the Blood Locations. Starting with CRAFTING.");
                    state = State.CRAFTING;
                    return;
                }

                Microbot.log("Player is inside the cave system but not at the locations. Starting with WALKING_TO.");
                state = State.BANKING;
                return;
            }

            // If outside caves but has essence, start from GOING_HOME
            Microbot.log("Player is outside caves but has essence. Starting with GOING_HOME.");
            state = State.GOING_HOME;
            return;
        }

        // If no Pure Essence, start at BANKING
        Microbot.log("Player has no Pure Essence. Starting with BANKING.");
        state = State.BANKING;
    }

    public void adjustCamera(Camera preset) {
        Microbot.log("Adjusting Camera: " + preset.name());

        Rs2Camera.setPitch(preset.getPitch());
        Rs2Camera.setZoom(preset.getZoom());

        Microbot.log("Camera adjusted: Pitch " + preset.getPitch() +
                ", Yaw " + preset.getYaw() +
                ", Zoom " + preset.getZoom());
    }

    private boolean isInsideCaveSystem() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        for (Caves cave : Caves.values()) {
            if (playerLocation.equals(cave.getCaveWorldPoint())) {
                return true; // Player is inside one of the caves
            }
        }

        return false; // Player is not in the cave system
    }




    @Override
    public void shutdown() {
        super.shutdown();
        Microbot.log("Shutting down BloodxScript...");
        Rs2Antiban.resetAntibanSettings();

        // ✅ Remove overlay to prevent duplicates on restart
        if (overlayManager != null && overlay != null) {
            overlayManager.remove(overlay);
            overlay = null; // Reset overlay so it can be re-added correctly
        }
    }

}
