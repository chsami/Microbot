package net.runelite.client.plugins.microbot.runecrafting.bloods;

import com.google.inject.Inject;
import lombok.Getter;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.runecrafting.bloods.enums.cPouch;
import net.runelite.client.plugins.microbot.runecrafting.bloods.BloodsOverlay;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.inventory.RunePouch;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.runecrafting.bloods.enums.Locations;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.runecrafting.bloods.enums.Caves;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spells;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.runecrafting.bloods.enums.State;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.List;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

public class BloodsScript extends Script {

    @Getter
    private State state;
    private Caves caves;
    private Locations locations;
    private int bloodRunesCrafted = 0;
    private int startingXp;
    public int getStartingXp() {
        return startingXp;
    }

    @Inject
    private OverlayManager overlayManager;
    private BloodsOverlay overlay;

    public boolean run(BloodsConfig config) {
        Microbot.enableAutoRunOn = false;
        overlay = new BloodsOverlay(this);
        overlayManager.add(overlay);
        startingXp = Microbot.getClient().getSkillExperience(Skill.RUNECRAFT);

        Microbot.log("Starting script");
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyRunecraftingSetup();
        Rs2Antiban.setActivity(Activity.GENERAL_RUNECRAFT);

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {

            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();
                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }
                if (Rs2Inventory.hasItem(26786)) {
                    state = State.REPAIR_POUCHES;
                    return;
                }
                if (state == null) {
                    if (!Rs2Inventory.hasItem("Blood Rune")) {
                        state = State.BANKING;
                    } else {
                        if (Rs2Player.getWorldLocation().distanceTo(Locations.TRUE_BLOOD.getRuinsWorldPoint()) < 5) {
                            state = State.ENTERING;
                        }
                    }
                }
                switch (state) {
                    case BANKING:
                        Microbot.log("Banking");
                        Microbot.status = "Banking";

                        Rs2Equipment.interact(9781, "Teleport");
                        sleepUntil(() -> !Rs2Player.isAnimating() && Rs2Player.getLocalLocation().isInScene());
                        Rs2Bank.openBank();
                        Rs2Random.randomGaussian(800, 200);
                        Rs2Bank.depositAll("Blood rune");
                        Rs2Random.randomGaussian(800, 200);
                        Rs2Bank.withdrawAll("Pure essence");
                        Rs2Random.randomGaussian(600, 200);

                        if (Rs2Inventory.hasItem(27281) && !Rs2Inventory.hasDegradedPouch()) {
                            Rs2Bank.depositRunePouch();
                        }
                        state = State.FILL_POUCHES;
                        break;

                    case REPAIR_POUCHES:
                        Microbot.log("Repairing pouches");
                        Microbot.status = "Pouch repair";

                        if (!Rs2Bank.isOpen()) {
                            Rs2Bank.openBank();
                            Rs2Random.randomGaussian(600, 200);
                        } else {
                            if (Rs2Inventory.hasItem(26786)) {
                                if (Rs2Inventory.getEmptySlots() < 1) {
                                    Rs2Bank.depositAll("Pure essence");
                                    Rs2Random.randomGaussian(600, 200);
                                }
                            }
                            Rs2Bank.withdrawRunePouch();
                            sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(3000));
                            Rs2Bank.closeBank();
                            Rs2Random.randomGaussian(700,300);
                            Rs2Magic.repairPouchesWithLunar();
                            sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(3000));
                            Rs2Bank.openBank();
                            Rs2Random.randomGaussian(600,200);
                            Rs2Bank.depositRunePouch();
                            Rs2Random.randomGaussian(700, 300);
                            Rs2Bank.closeBank();
                            Rs2Random.wait(300,600);
                        }
                        state = State.GOING_HOME;
                        break;

                    case FILL_POUCHES: {
                        Microbot.log("Filling Pouches");
                        Microbot.status = "Filling damn pouches";

                        if (!Rs2Bank.isOpen()) {
                            Rs2Bank.openBank();
                        }
                        if (cPouch.COLOSSAL.hasPouchInInventory()) {
                            if (!cPouch.COLOSSAL.isFull()) {
                                cPouch.COLOSSAL.fill();
                                System.out.println("Filled the Colossal Pouch.");
                            } else {
                                System.out.println("Pouch is already full.");
                            }
                        }
                        if (!Rs2Inventory.isFull() || !Rs2Inventory.anyPouchFull()) {
                            Rs2Bank.withdrawAll("Pure essence");
                            Rs2Inventory.fillPouches();
                        }
                        if (Rs2Inventory.getRemainingCapacityInPouches() > 0) {
                            Rs2Inventory.fillPouches();
                        }
                        if (!Rs2Inventory.isFull() && Rs2Inventory.allPouchesFull()) {
                            Rs2Bank.withdrawAll("Pure essence");
                        } else {
                            if (Rs2Inventory.allPouchesFull() && Rs2Inventory.isFull()) {
                                Rs2Bank.closeBank();
                            }
                        }
                        state = State.GOING_HOME;
                        break;
                    }

                    case GOING_HOME:
                        Microbot.log("Going home");
                        Microbot.status = "Going home";
                        if (!Rs2Equipment.interact(9790, "Tele to POH")) {
                            Microbot.log("Poh tele failed");
                            return;
                        }
                        sleepUntil(() -> !Rs2Player.isAnimating() && Microbot.getClient().getGameState().getState() == 30, 3000);
                        Rs2Random.randomGaussian(1000, 200);
                        if (Rs2Player.getRunEnergy() < 40) {
                            Microbot.log("Thirsty boi");
                            Rs2GameObject.interact(29241, "Drink");
                            sleepUntil(() -> !Rs2Player.isInteracting() && Rs2Player.getRunEnergy() > 90);
                        }
                        Microbot.log("Using Fairies");
                        if (!Rs2GameObject.interact(27097, "Ring-last-destination (DLS)")) {
                            Microbot.log("Fairies were of no help");
                            state = State.BANKING;
                            return;
                        }
                        sleepUntil(() -> Microbot.getClient().getGameState().getState() == 30, 4000);

                        state = State.TRAVISING;
                        Microbot.log("...leaving");
                        break;

                    case TRAVISING:
                        Microbot.log("Gotta go");
                        Microbot.status = "Travising";
                        travisTheHoles();
                        sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 14232);
                        state = State.ENTERING; 
                        break;
                        
                    case ENTERING:
                        Microbot.log("Entering ruins");
                        Microbot.status = "Entering ruins";
                        if (locations == null) {
                            Microbot.log("Location are null");
                            locations = Locations.TRUE_BLOOD;
                        }
                        WorldPoint beforePosition = Rs2Player.getWorldLocation();
                        int beforeRuins = Rs2Player.getWorldLocation().getRegionID();
                        
                        boolean enterRuins = Rs2GameObject.interact(locations.getRuinsID());
                        if (!enterRuins) {
                            Microbot.log("No interaction, going back to banking");
                            state = State.BANKING;
                        }
                        boolean enteredRuins = sleepUntil(() -> !Rs2Player.getWorldLocation().equals(beforePosition) ||
                                Rs2Player.getWorldLocation().getRegionID() != beforeRuins, 4000);
                        if (!enteredRuins) {
                            Microbot.log("Player didn't enter");
                            return;
                        }
                        sleepUntil(() -> !Rs2Player.isAnimating() && Rs2Player.isMoving());
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
    private void travisTheHoles() {
        for (Caves caves : Caves.values()) {
            Microbot.log("Entering" + caves.getWhichCave());
            sleepUntil(() -> Microbot.getClient().getGameState().getState() == 30, 4000);
            sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating());

            WorldPoint beforePosition = Rs2Player.getWorldLocation();
            int beforeRegion = Rs2Player.getWorldLocation().getRegionID();
            int attempts = 0;
            boolean entered=false;

            if (caves == Caves.FOURTH_CAVE) {
                interactWithFourth(caves);
                entered = sleepUntil(() -> !Rs2Player.getWorldLocation().equals(beforePosition) ||
                        Rs2Player.getWorldLocation().getRegionID() != beforeRegion, 4000);
            } else if (caves == Caves.AGILITY_74) {
                interactWithFifth(caves);
                entered = sleepUntil(() -> !Rs2Player.getWorldLocation().equals(beforePosition) ||
                        Rs2Player.getWorldLocation().getRegionID() != beforeRegion, 4000);
            } else {
                while (attempts <3 && !entered) {
                    Microbot.log("Attempt #" + (attempts +1) + "to enter" + caves.getWhichCave());
                    boolean interacted = Rs2GameObject.interact(caves.getIdCave(), "Enter");
                    if (!interacted) {
                        Microbot.log("Interaction failed, retrying...");
                        sleepUntil(() -> !Rs2Player.isMoving());
                        attempts++;
                        continue;
                    }
                    entered = sleepUntil(() -> !Rs2Player.getWorldLocation().equals(beforePosition) ||
                            Rs2Player.getWorldLocation().getRegionID() != beforeRegion, 4000);
                    if (!entered) {
                        Microbot.log("Failed to enter");
                        sleep(1200);
                        attempts++;
                    }
                }
            }
            if (!entered) {
                Microbot.log("WARNING: Player did not enter " + caves.getWhichCave() + " after 3 attempts. Skipping...");
                sleepUntil(() -> !Rs2Player.isMoving(), 3000);
                sleep(1200);
                Microbot.log("Successfully entered: " + caves.getWhichCave());
            }
            Microbot.log("All caves traversed successfully!");
            Rs2Random.wait(400, 800);
        }
    }
    private void interactWithFourth(Caves cave) {
        Microbot.log("Looking for the furthest cave entrance: " + cave.getWhichCave());
        List<GameObject> caveEntrances = Rs2GameObject.getGameObjects(cave.getIdCave());
        if (caveEntrances.isEmpty()) {
            Microbot.log("ERROR: No cave entrances found!");
            return;
        }
        WorldPoint playerPosition = Rs2Player.getWorldLocation();
        GameObject furthestCave = caveEntrances.stream()
                .max(Comparator.comparingInt(obj -> obj.getWorldLocation().distanceTo(playerPosition)))
                .orElse(null);
        if (furthestCave == null) {
            Microbot.log("ERROR: Could not determine the furthest cave entrance.");
            return;
        }
        Microbot.log("Furthest cave entrance found at: " + furthestCave.getWorldLocation());
        boolean interacted = Rs2GameObject.interact(furthestCave, "Enter");
        sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 13977
                && !Rs2Player.isAnimating());
        if (!interacted) {
            Microbot.log("ERROR: Failed to interact with the furthest cave entrance.");
        }
    }
    private void interactWithFifth(Caves cave) {
        Microbot.log("Navigating to the fifth cave entrance: " + cave.getWhichCave());
        Rs2Walker.walkTo(3560, 9814, 0);
        sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating(), 5000);
        Rs2Random.wait(600, 1000);
        boolean interacted = Rs2GameObject.interact(cave.getIdCave(), "Enter");
        if (!interacted) {
            Microbot.log("ERROR: Could not interact with the cave (Object ID " + cave.getIdCave() + ")");
            state = State.BANKING;
        }
    }
    public int getBloodRunesCrafted() {
        int currentXp = Microbot.getClient().getSkillExperience(Skill.RUNECRAFT);
        return (currentXp - startingXp) / 10;
    }



    @Override
    public void shutdown() {
        super.shutdown();
        Microbot.log("Shutting down BloodsScript...");
        Rs2Antiban.resetAntibanSettings();
        if (overlayManager != null && overlay != null) {
            overlayManager.remove(overlay);
            overlay = null;
        }
    }
}
