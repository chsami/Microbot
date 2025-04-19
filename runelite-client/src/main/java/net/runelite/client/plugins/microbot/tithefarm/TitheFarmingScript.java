package net.runelite.client.plugins.microbot.tithefarm;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.tithefarm.enums.TitheFarmLanes;
import net.runelite.client.plugins.microbot.tithefarm.enums.TitheFarmMaterial;
import net.runelite.client.plugins.microbot.tithefarm.enums.TitheFarmState;
import net.runelite.client.plugins.microbot.tithefarm.models.TitheFarmPlant;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.tithefarm.enums.TitheFarmState.*;
import static net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue.hasSelectAnOption;

/**
 * TODO list:
 * -plants per hour
 * -check for seed dibber and spade inventory
 * -deposit sack
 * -test other plants
 * -move script into seperate folder
 */


public class TitheFarmingScript extends Script {

    final int FARM_DOOR = 27445;
    final String FERTILISER = "gricoller's fertiliser";

    public static List<TitheFarmPlant> plants = new ArrayList<>();


    public static TitheFarmState state = TitheFarmState.STARTING;

    public static int initialFruit = 0;
    public static int fruits = 0;

    public static final int WATERING_CANS_AMOUNT = 8;

    public static final int DISTANCE_TRESHHOLD_MINIMAP_WALK = 8;

    public static int gricollerCanCharges = -1;

    public static boolean init = true;

    public void init(TitheFarmingConfig config) {
        TitheFarmLanes lane = config.Lanes();

        if (lane == TitheFarmLanes.Randomize) {
            lane = TitheFarmLanes.values()[Random.random(0, TitheFarmLanes.values().length - 1)];
        }

        switch (lane) {
            case LANE_1_2:
                plants = new ArrayList<>(Arrays.asList(
                        new TitheFarmPlant(35, 25, 1),
                        new TitheFarmPlant(40, 25, 2),
                        new TitheFarmPlant(35, 28, 3),
                        new TitheFarmPlant(40, 28, 4),
                        new TitheFarmPlant(35, 31, 5),
                        new TitheFarmPlant(40, 31, 6),
                        new TitheFarmPlant(35, 34, 7),
                        new TitheFarmPlant(40, 34, 8),
                        new TitheFarmPlant(35, 40, 9),
                        new TitheFarmPlant(40, 40, 10),
                        new TitheFarmPlant(35, 43, 11),
                        new TitheFarmPlant(40, 43, 12),
                        new TitheFarmPlant(35, 46, 13),
                        new TitheFarmPlant(40, 46, 14),
                        new TitheFarmPlant(35, 49, 15),
                        new TitheFarmPlant(40, 49, 16),
                        new TitheFarmPlant(45, 49, 17),
                        new TitheFarmPlant(45, 46, 18),
                        new TitheFarmPlant(45, 49, 19),
                        new TitheFarmPlant(45, 46, 20),
                        new TitheFarmPlant(45, 43, 21)));
                break;
            case LANE_2_3:
                plants = new ArrayList<>(Arrays.asList(
                        new TitheFarmPlant(35, 31, -2),
                        new TitheFarmPlant(35, 28, -1),
                        new TitheFarmPlant(35, 25, 0),
                        new TitheFarmPlant(40, 25, 1),
                        new TitheFarmPlant(45, 25, 2),
                        new TitheFarmPlant(40, 28, 3),
                        new TitheFarmPlant(45, 28, 4),
                        new TitheFarmPlant(40, 31, 5),
                        new TitheFarmPlant(45, 31, 6),
                        new TitheFarmPlant(40, 34, 7),
                        new TitheFarmPlant(45, 34, 8),
                        new TitheFarmPlant(40, 40, 9),
                        new TitheFarmPlant(45, 40, 10),
                        new TitheFarmPlant(40, 43, 11),
                        new TitheFarmPlant(45, 43, 12),
                        new TitheFarmPlant(40, 46, 13),
                        new TitheFarmPlant(45, 46, 14),
                        new TitheFarmPlant(40, 49, 15),
                        new TitheFarmPlant(45, 49, 16)));
                break;
            case LANE_3_4:
                plants = new ArrayList<>(Arrays.asList(
                        new TitheFarmPlant(40, 31, -2),
                        new TitheFarmPlant(40, 28, -1),
                        new TitheFarmPlant(40, 25, 0),
                        new TitheFarmPlant(45, 25, 1),
                        new TitheFarmPlant(50, 25, 2),
                        new TitheFarmPlant(45, 28, 3),
                        new TitheFarmPlant(50, 28, 4),
                        new TitheFarmPlant(45, 31, 5),
                        new TitheFarmPlant(50, 31, 6),
                        new TitheFarmPlant(45, 34, 7),
                        new TitheFarmPlant(50, 34, 8),
                        new TitheFarmPlant(45, 40, 9),
                        new TitheFarmPlant(50, 40, 10),
                        new TitheFarmPlant(45, 43, 11),
                        new TitheFarmPlant(50, 43, 12),
                        new TitheFarmPlant(45, 46, 13),
                        new TitheFarmPlant(50, 46, 14),
                        new TitheFarmPlant(45, 49, 15),
                        new TitheFarmPlant(50, 49, 16)));
                break;
            case LANE_4_5:
                plants = new ArrayList<>(Arrays.asList(
                        new TitheFarmPlant(45, 31, 0),
                        new TitheFarmPlant(45, 28, 1),
                        new TitheFarmPlant(45, 25, 2),
                        new TitheFarmPlant(50, 25, 3),
                        new TitheFarmPlant(55, 25, 4),
                        new TitheFarmPlant(50, 28, 5),
                        new TitheFarmPlant(55, 28, 6),
                        new TitheFarmPlant(50, 31, 7),
                        new TitheFarmPlant(55, 31, 8),
                        new TitheFarmPlant(50, 34, 9),
                        new TitheFarmPlant(55, 34, 10),
                        new TitheFarmPlant(50, 40, 11),
                        new TitheFarmPlant(55, 40, 12),
                        new TitheFarmPlant(50, 43, 13),
                        new TitheFarmPlant(55, 43, 14),
                        new TitheFarmPlant(50, 46, 15),
                        new TitheFarmPlant(55, 46, 16),
                        new TitheFarmPlant(50, 49, 17),
                        new TitheFarmPlant(55, 49, 18)));
                break;
        }
    }


    public boolean run(TitheFarmingConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (init) {
                    state = STARTING;
                    plants = new ArrayList<>();
                    Rs2ItemModel rs2ItemSeed = Rs2Inventory.get(TitheFarmMaterial.getSeedForLevel().getFruitId());
                    initialFruit = rs2ItemSeed == null ? 0 : rs2ItemSeed.quantity;
                    init = false;
                    sleep(2000); //extra sleep to have the game initialize correctly
                }

                //Dialogue stuff only applicable if you enter for the first time
                if (Rs2Dialogue.isInDialogue()) {
                    Rs2Dialogue.clickContinue();
                    sleep(400, 600);
                    return;
                }

                if (hasSelectAnOption()) {
                    Rs2Keyboard.typeString("3");
                    sleep(1500, 1800);
                    return;
                }

                if (!isInMinigame() && !Rs2Inventory.hasItem(TitheFarmMaterial.getSeedForLevel().getName())) {
                    state = TitheFarmState.TAKE_SEEDS;
                }

                if (validateSeedsAndPatches() && isInMinigame()) {
                    state = TitheFarmState.LEAVE;
                }

                if (Rs2Inventory.hasItemAmount(TitheFarmMaterial.getSeedForLevel().getFruitId(), config.storeFruitTreshhold())) {
                    depositSack();
                    return;
                }

                switch (state) {
                    case LEAVE:
                        if (!depositSack()) {
                            leave();
                        }
                    break;
                    case TAKE_SEEDS:
                        if (isInMinigame()) {
                            state = TitheFarmState.STARTING;
                        } else {
                            takeSeeds();
                            if (Rs2Inventory.hasItem(TitheFarmMaterial.getSeedForLevel().getName())) {
                                enter();
                            }
                        }
                        break;
                    case STARTING:
                        Rs2Player.toggleRunEnergy(true);
                        Rs2Tab.switchToInventoryTab();
                        init(config);
                        validateInventory();
                        DropFertiliser();
                        validateRunEnergy();
                        if (state != RECHARING_RUN_ENERGY)
                            state = REFILL_WATERCANS;
                        break;
                    case RECHARING_RUN_ENERGY:
                        validateRunEnergy();
                        break;
                    case REFILL_WATERCANS:
                        refillWaterCans(config);
                        break;
                    case PLANTING_SEEDS:
                    case HARVEST:
                        coreLoop(config);
                        break;
                }

                if (config.enableDebugging() && plants.stream().anyMatch(x -> x.getGameObject() == null)) {
                    Microbot.showMessage("There is an empty plant gameobject!");
                }

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }

        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    /**
     * ALL PRIVATE SCRIPT METHODS
     */

    private void coreLoop(TitheFarmingConfig config) {
        if (Rs2Player.isMoving()) return;
        Comparator<TitheFarmPlant> sortByIndex = Comparator.comparingInt(TitheFarmPlant::getIndex);
        TitheFarmPlant plant = null;
        if (state != HARVEST) {
             plant = plants.stream()
                    .sorted(sortByIndex)
                    .filter(TitheFarmPlant::isEmptyPatchOrSeedling) //empty patch and seedling first
                    .findFirst()
                    .orElseGet(() ->
                            plants.stream()
                                    .sorted(sortByIndex)
                                    .filter(TitheFarmPlant::isStage1) // then stage1 plants
                                    .findFirst()
                                    .orElseGet(() ->
                                            plants.stream()
                                                    .sorted(sortByIndex)
                                                    .filter(TitheFarmPlant::isStage2) //then stage2 plants
                                                    .findFirst()
                                                    .orElse(null)
                                    )
                    );
        }

        if (state == TitheFarmState.HARVEST && hasAllEmptyPatches()) {
            state = STARTING;
        }

        if (plant == null && plants.stream().anyMatch(TitheFarmPlant::isValidToHarvest)) {
            state = TitheFarmState.HARVEST;
            plant = plants.stream()
                    .sorted(sortByIndex)
                    .filter(TitheFarmPlant::isValidToHarvest)
                    .findFirst()
                    .orElse(null);
        }

        if (plant == null) return;

        final TitheFarmPlant finalPlant = plant;

        if (plant.getGameObject().getWorldLocation().distanceTo2D(Microbot.getClient().getLocalPlayer().getWorldLocation()) > DISTANCE_TRESHHOLD_MINIMAP_WALK) {
            //Important to know that there are two world locations when you are in an instance
            //thats why we use the world location of the getLocalPlayer instead of Rs2Player.getWorldLocation
            //because Rs2Player.getWorldLocation will give us the world location in the instance and we do not want that
            WorldPoint w = WorldPoint.fromRegion(Microbot.getClient().getLocalPlayer().getWorldLocation().getRegionID(),
                    plant.regionX,
                    plant.regionY,
                    Microbot.getClient().getPlane());
            Rs2Walker.walkMiniMap(w, 1);
            return;
        }

        if (plant.isEmptyPatch()) { //start planting seeds
            Rs2Inventory.interact(TitheFarmMaterial.getSeedForLevel().getName(), "Use");
            clickPatch(plant);
            sleepUntil(Rs2Player::isAnimating, config.sleepAfterPlantingSeed());
            if (Rs2Player.isAnimating()) {
                sleepUntil(() -> plants.stream().noneMatch(x -> x.getIndex() == finalPlant.getIndex() && x.isEmptyPatch()));
            }
        }

        if (plant.isValidToWater()) {
            clickPatch(plant, "water");
            sleepUntil(Rs2Player::isAnimating, config.sleepAfterWateringSeed());
            if (Rs2Player.isAnimating()) {
                sleepUntil(() -> plants.stream().noneMatch(x -> x.getIndex() == finalPlant.getIndex() && x.isValidToWater()));
            }
            plant.setPlanted(Instant.now());
        }


        if (plant.isValidToHarvest()) {
            clickPatch(plant, "harvest");
            sleepUntil(Rs2Player::isAnimating, config.sleepAfterHarvestingSeed());
            if (Rs2Player.isAnimating()) {
                sleepUntil(() -> plants.stream().anyMatch(x -> x.getIndex() == finalPlant.getIndex() && x.isEmptyPatch()));
            }
        }
    }

        // Helper method to validate inventory items
        private void validateInventory() {
            if (!Rs2Inventory.hasItem(ItemID.SEED_DIBBER) || !Rs2Inventory.hasItem(ItemID.SPADE)) {
                Microbot.showMessage("You need a seed dibber and a spade in your inventory!");
                shutdown();
            }
            if (!Rs2Inventory.hasItemAmount("watering can", WATERING_CANS_AMOUNT) && !Rs2Inventory.hasItem(ItemID.GRICOLLERS_CAN)) {
                Microbot.showMessage("You need at least 8 watering can(8) or a Gricoller's can!");
                shutdown();
            }
        }

// Helper method to validate run energy and patches
        private void validateRunEnergy() {
            if (Microbot.getClient().getEnergy() < 4000 && hasAllEmptyPatches() && state != RECHARING_RUN_ENERGY) {
                state = RECHARING_RUN_ENERGY;
                Microbot.log("Recharging run energy...");
                Rs2Inventory.useRestoreEnergyItem();
            } else if (state == RECHARING_RUN_ENERGY && Microbot.getClient().getEnergy() >= 4000) {
                state = STARTING;
            }
        }

        private boolean validateSeedsAndPatches() {
            if (!Rs2Inventory.hasItem(TitheFarmMaterial.getSeedForLevel().getName())) {
                return true;
            }
            return false;
        }



    private static void clickPatch(TitheFarmPlant plant) {
        WorldPoint worldPoint = WorldPoint.fromRegion(Microbot.getClient().getLocalPlayer().getWorldLocation().getRegionID(),
                plant.regionX,
                plant.regionY,
                Microbot.getClient().getPlane());

        Rs2GameObject.interact(worldPoint);

        //Point point = Calculations.worldToCanvas(worldPoint.getX(), worldPoint.getY());
        //Microbot.getMouse().click(point);
    }

    private static void clickPatch(TitheFarmPlant plant, String action) {
        WorldPoint worldPoint = WorldPoint.fromRegion(Microbot.getClient().getLocalPlayer().getWorldLocation().getRegionID(),
                plant.regionX,
                plant.regionY,
                Microbot.getClient().getPlane());

        Rs2GameObject.interact(worldPoint, action);
    }

    private static void DropFertiliser() {
        if (Rs2Inventory.hasItem("Gricoller's fertiliser")) {
            Rs2Inventory.drop("Gricoller's fertiliser");
        }
    }

    private void refillWaterCans(TitheFarmingConfig config) {
        if (TitheFarmMaterial.hasGricollersCan()) {
            checkGricollerCharges();
            sleepUntil(() -> gricollerCanCharges != -1);
            if (gricollerCanCharges < config.gricollerCanRefillTreshhold()) {
                walkToBarrel();
                Rs2Inventory.interact(ItemID.GRICOLLERS_CAN, "Use");
                Rs2GameObject.interact("Water barrel");
                sleepUntil(Rs2Player::isAnimating, 10000);
            } else {
                state = PLANTING_SEEDS;
            }
        } else if (TitheFarmMaterial.hasWateringCanToBeFilled()) {
            walkToBarrel();
            Rs2Inventory.interact(TitheFarmMaterial.getWateringCanToBeFilled(), "Use");
            Rs2GameObject.interact(ObjectID.WATER_BARREL, "Use");
            sleepUntil(() -> Rs2Inventory.hasItemAmount(ItemID.WATERING_CAN8, WATERING_CANS_AMOUNT), 60000);
        } else {
            state = PLANTING_SEEDS;
        }
    }

    private void walkToBarrel() {
        final TileObject gameObject = Rs2GameObject.findObjectById(ObjectID.WATER_BARREL);
        if (gameObject.getWorldLocation().distanceTo2D(Microbot.getClient().getLocalPlayer().getWorldLocation()) > DISTANCE_TRESHHOLD_MINIMAP_WALK) {
            Rs2Walker.walkMiniMap(gameObject.getWorldLocation(), 1);
            sleepUntil(Rs2Player::isMoving);
        }
        sleepUntil(() -> gameObject.getWorldLocation().distanceTo2D(Microbot.getClient().getLocalPlayer().getWorldLocation()) < DISTANCE_TRESHHOLD_MINIMAP_WALK);
    }

    private void checkGricollerCharges() {
        gricollerCanCharges = -1;
        Rs2Inventory.interact(ItemID.GRICOLLERS_CAN, "check");
    }

    private void takeSeeds() {
        if (Rs2Inventory.hasItem(TitheFarmMaterial.getSeedForLevel().getName())) {
            Rs2Inventory.drop(TitheFarmMaterial.getSeedForLevel().getName());
            sleep(400, 600);
        }
        Rs2GameObject.interact(ObjectID.SEED_TABLE);
        boolean result = Rs2Widget.sleepUntilHasWidget(TitheFarmMaterial.getSeedForLevel().getName());
        if (!result) return;
        keyPress(TitheFarmMaterial.getSeedForLevel().getOption());
        sleep(1000);
        Rs2Keyboard.typeString(String.valueOf(Random.random(1000, 10000)));
        sleep(600);
        Rs2Keyboard.enter();
        sleepUntil(() -> Rs2Inventory.hasItem(TitheFarmMaterial.getSeedForLevel().getName()));
    }

    private void enter() {
        WallObject farmDoor = Rs2GameObject.findDoor(FARM_DOOR);
        Rs2GameObject.interact(farmDoor);
        sleepUntil(this::isInMinigame);
    }

    private boolean depositSack() {
        if (Rs2Inventory.hasItem(TitheFarmMaterial.getSeedForLevel().getFruitId())) {
            Microbot.log("Storing fruits into sack for experience...");
            Rs2GameObject.interact(ObjectID.SACK_27431);
            Rs2Player.waitForWalking();
            Rs2Player.waitForAnimation();
            return true;
        }
        return false;
    }

    private void leave() {
        WallObject farmDoor = Rs2GameObject.findDoor(FARM_DOOR);
        Rs2GameObject.interact(farmDoor);
        sleepUntil(() -> !Rs2Inventory.hasItem(FERTILISER), 8000);
    }

    private boolean hasAllEmptyPatches() {
        return plants.stream().allMatch(TitheFarmPlant::isEmptyPatch);
    }

    private boolean isInMinigame() {
        return Rs2Widget.getWidget(15794178) != null;
    }
}
