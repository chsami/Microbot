package net.runelite.client.plugins.microbot.grapefarmer;

import net.runelite.api.*;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.*;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.Microbot.log;


public class GrapeFarmerScript extends Script {

    private enum State {
        ADD_SALTPETRE,
        PLANT_GRAPE_SEED,
        CHECK_HEALTH,
        PICK_GRAPES,
        CLEAR_VINE,
        NONE
    }

    private static final Map<Integer, Integer> patchMap = new LinkedHashMap<>() {{
        put(Varbits.GRAPES_4959, 11816); // a1
        put(Varbits.GRAPES_4960, 11817); // a2
        put(Varbits.GRAPES_4961, 11947); // a3
        put(Varbits.GRAPES_4962, 12598); // a4
        put(Varbits.GRAPES_4963, 12599); // a5
        put(Varbits.GRAPES_4964, 12600); // a6
        put(Varbits.GRAPES_4958, 11815); // b1
        put(Varbits.GRAPES_4957, 11814); // b2
        put(Varbits.GRAPES_4956, 11813); // b3
        put(Varbits.GRAPES_4955, 11812); // b4
        put(Varbits.GRAPES_4954, 11811); // b5
        put(Varbits.GRAPES_4953, 11810); // b6
    }};


    public boolean run(GrapeFarmerConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                if (config.GEARING()) {
                    if (!this.isRunning()) return;
                    if (!Rs2Inventory.contains(ItemID.GARDENING_TROWEL)) {
                        Rs2Bank.useBank();
                        Rs2Bank.depositAll();
                        if (Rs2Bank.hasItem(ItemID.FARMING_CAPE) || Rs2Bank.hasItem(ItemID.FARMING_CAPET)) {
                            Rs2Bank.withdrawAndEquip(Rs2Bank.hasItem(ItemID.FARMING_CAPE) ? ItemID.FARMING_CAPE : ItemID.FARMING_CAPET);
                        }
                        Rs2Bank.withdrawAllButOne(ItemID.GRAPE_SEED);
                        Rs2Bank.withdrawOne(ItemID.GARDENING_TROWEL);
                        Rs2Bank.withdrawOne(ItemID.SEED_DIBBER);
                        Rs2Bank.withdrawOne(ItemID.SPADE);
                        Rs2Bank.withdrawAllButOne(ItemID.BOLOGAS_BLESSING);
                        Rs2Bank.withdrawX(ItemID.SALTPETRE, 12);
                        if (config.FARMING_OUTFIT()) {
                            Rs2Bank.depositEquipment();
                            Rs2Bank.withdrawAndEquip(ItemID.FARMERS_STRAWHAT_13647);
                            Rs2Bank.withdrawAndEquip(ItemID.FARMERS_SHIRT);
                            Rs2Bank.withdrawAndEquip(ItemID.FARMERS_BORO_TROUSERS_13641);
                            Rs2Bank.withdrawAndEquip(ItemID.FARMERS_BOOTS_13645);
                            if (Rs2Bank.hasItem(ItemID.GRACEFUL_CAPE)) {
                                Rs2Bank.withdrawAndEquip(ItemID.GRACEFUL_CAPE);
                            }
                            if (Rs2Bank.hasItem(ItemID.GRACEFUL_GLOVES)) {
                                Rs2Bank.withdrawAndEquip(ItemID.GRACEFUL_GLOVES);
                            }
                        }
                        if (Rs2Bank.hasItem(ItemID.MAGIC_SECATEURS)) {
                            Rs2Bank.withdrawAndEquip(ItemID.MAGIC_SECATEURS);
                        }
                        Rs2Bank.closeBank();
                        sleep(300);
                    }
                    if (Rs2Inventory.contains(ItemID.GARDENING_TROWEL)  && Rs2Inventory.count(ItemID.SALTPETRE) < 1) {
                        Rs2Bank.useBank();
                        Rs2Bank.depositAll(ItemID.ZAMORAKS_GRAPES);
                        Rs2Bank.depositAll(ItemID.GRAPES);
                        Rs2Bank.withdrawX(ItemID.SALTPETRE, 12);
                        Rs2Bank.closeBank();
                    }
                }

                for (Map.Entry<Integer, Integer> entry : patchMap.entrySet()) {
                    int varbitKey = entry.getKey();
                    int gameObjectId = entry.getValue();

                    while (true) {

                        if (!this.isRunning()) return;
                        if (Rs2Player.isMoving() || Rs2Player.isAnimating() ||
                                Rs2Player.isInteracting() || Rs2Player.isMoving()) {
                            continue;
                        }

                        int currentVarbitValue = Microbot.getVarbitValue(varbitKey);
                        State currentState = getStateForVarbit(currentVarbitValue);

                        if (currentState == State.NONE) {
                            System.out.println("State is DEFAULT for GroundObject ID: " + gameObjectId + ". Moving to the next patch.");
                            break;
                        }

                        switch (currentState) {
                            case CHECK_HEALTH:
                                System.out.println("Varbit value checkhealth: " + currentVarbitValue);
                                System.out.println("Checking health of GroundObject ID: " + gameObjectId);
                                checkHealth(gameObjectId);
                                break;

                            case PICK_GRAPES:
                                System.out.println("Picking grapes at GroundObject ID: " + gameObjectId);
                                pickGrapes(gameObjectId);
                                waitForCompletion();
                                break;

                            case CLEAR_VINE:
                                System.out.println("Clearing vine at GroundObject ID: " + gameObjectId);
                                clearVine(gameObjectId);
                                waitForCompletion();
                                break;

                            case ADD_SALTPETRE:
                                System.out.println("Adding salt petre to GroundObject ID: " + gameObjectId);
                                addSaltpetre(gameObjectId);
                                waitForCompletion();
                                break;

                            case PLANT_GRAPE_SEED:
                                System.out.println("Planting grape seed at GroundObject ID: " + gameObjectId);
                                plantSeed(gameObjectId);
                                waitForCompletion();
                                break;

                            default:
                                System.out.println("Unhandled state for GroundObject ID: " + gameObjectId);
                                break;
                        }
                    }
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

    private void plantSeed(int gameObjectId) {
        if (Rs2Inventory.use(ItemID.GRAPE_SEED)) {
            Rs2GameObject.interact(gameObjectId);
            Rs2Player.waitForXpDrop(Skill.FARMING);
        }
    }

    private void addSaltpetre(int gameObjectId) {
        if (Rs2GameObject.interact(gameObjectId)) {
            sleep(4000, 4500);
        }

    }

    private void clearVine(int gameObjectId) {
        if (!Rs2Player.isMoving() && !Rs2Player.isAnimating(5000) && !Rs2Player.isInteracting()) {
            if (Rs2GameObject.interact(gameObjectId)) {
                Rs2Player.waitForAnimation(2500);
                sleepUntil(() -> !Rs2Player.isAnimating() &&
                        !Rs2Player.isMoving() &&
                        !Rs2Player.isInteracting());
            }
        }
    }

    private void pickGrapes(int gameObjectId) {
        Rs2NpcModel leprechaun = Rs2Npc.getNpc(0);
        if (leprechaun != null) {
            if (Rs2Inventory.isFull()) {
                Rs2Inventory.use(ItemID.ZAMORAKS_GRAPES);
                Rs2Npc.interact(leprechaun);
                sleepUntil(() -> !Rs2Inventory.contains(ItemID.ZAMORAKS_GRAPES), 5000);
                sleep(100,600);
                if (Rs2Inventory.contains(ItemID.GRAPES)) {
                    Rs2Inventory.use(ItemID.GRAPES);
                    Rs2Npc.interact(leprechaun);
                    sleepUntil(() -> !Rs2Inventory.contains(ItemID.GRAPES), 5000);
                    sleep(50, 500);
                }
            }
        }

        if (!Rs2Inventory.isFull() && Rs2GameObject.interact(gameObjectId)) {
            Rs2Player.waitForAnimation(500);
        }
    }

    private static State getStateForVarbit(int varbitValue) {
        log("Current varbit value: " + varbitValue);
        if (varbitValue == 0) {
            // Empty, empty+fertilizer
            return State.ADD_SALTPETRE;
        }
        if (varbitValue == 1) {
            return State.PLANT_GRAPE_SEED;
        }
        if (varbitValue >= 2 && varbitValue < 9) {
            // Growing grape
            return State.NONE;
        }
        if (varbitValue == 9) {
            return State.CHECK_HEALTH;
        }
        if (varbitValue >= 10 && varbitValue < 15) {
            // Harvestable grape
            return State.PICK_GRAPES;
        }
        if (varbitValue == 15) {
            return State.CLEAR_VINE;
        }
        return null;
    }

    private static void checkHealth(int gameObjectId) {
        System.out.println("Interacting with GroundObject ID: " + gameObjectId + " using action: Check-health");
        Rs2GameObject.interact(gameObjectId);
        Rs2Player.waitForXpDrop(Skill.FARMING);
    }


    private void waitForCompletion() {
        sleepUntil(() -> !Rs2Player.isMoving() && !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
    }


    @Override
    public void shutdown() {
        super.shutdown();
    }
}