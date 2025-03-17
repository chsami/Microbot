package net.runelite.client.plugins.microbot.liftedmango.herbrun;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.awt.*;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.Microbot.log;
import static net.runelite.client.plugins.microbot.liftedmango.herbrun.HerbrunInfo.botStatus;
import static net.runelite.client.plugins.microbot.liftedmango.herbrun.HerbrunInfo.states;

@Slf4j

public class HerbrunScript extends Script {

    // Define the herb patch locations
    private static final WorldPoint trollheimHerb = new WorldPoint(2826, 3693, 0);
    private static final WorldPoint catherbyHerb = new WorldPoint(2812, 3465, 0);
    private static final WorldPoint morytaniaHerb = new WorldPoint(3604, 3529, 0);
    private static final WorldPoint varlamoreHerb = new WorldPoint(1582, 3093, 0);
    private static final WorldPoint hosidiusHerb = new WorldPoint(1739, 3552, 0);
    private static final WorldPoint ardougneHerb = new WorldPoint(2669, 3374, 0);
    private static final WorldPoint cabbageHerb = new WorldPoint(3058, 3310, 0);
    private static final WorldPoint farmingGuildHerb = new WorldPoint(1239, 3728, 0);
    private static final WorldPoint weissHerb = new WorldPoint(2847, 3935, 0);
    private static final WorldPoint harmonyHerb = new WorldPoint(3789, 2840, 0);

    //herb patch Object ID
    private static final int trollheimHerbPatchID = 18816;
    private static final int catherbyHerbPatchID = 8151;
    private static final int morytaniaHerbPatchID = 8153;
    private static final int varlamoreHerbPatchID = 50697;
    private static final int hosidiusHerbPatchID = 27115;
    private static final int ardougneHerbPatchID = 8152; //leprechaun 0
    private static final int cabbageHerbPatchID = 8150; //50698?
    private static final int farmingGuildHerbPatchID = 33979;
    private static final int weissHerbPatchID = 33176;
    private static final int harmonyHerbPatchID = 9372;

    //Leprechaun IDs:
    //IDS that are 0: Ardougne, Farming guild, morytania, hosidius, catherby, falador, weiss, harmony
    private static final int varlamoreLeprechaunID = NpcID.TOOL_LEPRECHAUN_12765;
    private static final int trollHeimLeprechaunID = NpcID.TOOL_LEPRECHAUN_757;

    //seed type
//    public static ItemID seeds = ;
    public static boolean test = false;
    public HerbrunScript() throws AWTException {
    }

    public boolean run(HerbrunConfig config) {

        int seedToPlant = config.SEED().getItemId();

        Microbot.enableAutoRunOn = false;
        botStatus = states.GEARING;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;


                switch (botStatus) {
                    case GEARING:
                        if (!config.enableGearing()) {
                            botStatus = states.TROLLHEIM_TELEPORT;
                        } else {
                            log.info("State: GEARING - Gearing up");
                            if (!Rs2Bank.isOpen()) {
                                log.info("State: GEARING - Bank opened");
                                Rs2Bank.useBank();
                                Rs2Bank.depositAll();
                                Rs2Inventory.waitForInventoryChanges(2000);
                                if (config.GRACEFUL()) {
                                    log.info("State: GEARING - Depositing equipment for graceful mode");
                                    Rs2Bank.depositEquipment();
                                    sleepUntil(Rs2Equipment::isNaked);
                                    sleep(200);
                                    equipGraceful(config);
                                }
                            }
                            withdrawHerbSetup(config);
                            Rs2Bank.closeBank();
                            sleep(100);
                            log.info("State: GEARING - Gearing complete");
                            sleep(200, 800); // Random sleep retained only in GEARING state
                            botStatus = states.TROLLHEIM_TELEPORT;
                        }
                        break;

                    case TROLLHEIM_TELEPORT:
                        log.info("State: TROLLHEIM_TELEPORT - Teleporting to Trollheim");
                        if (config.enableTrollheim()) {
                            handleTeleportToTrollheim();
                        } else {
                            botStatus = states.CATHERBY_TELEPORT;
                        }
                        break;

                    case TROLLHEIM_WALKING_TO_PATCH:
                        log.info("State: TROLLHEIM_WALKING_TO_PATCH - Walking to Trollheim patch");
                        handleWalkingToPatch(trollheimHerb, states.TROLLHEIM_HANDLE_PATCH);
                        break;

                    case TROLLHEIM_HANDLE_PATCH:
                        if (Rs2Player.distanceTo(trollheimHerb) < 8) {
                            log.info("State: TROLLHEIM_HANDLE_PATCH - Handling Trollheim patch");
                            printHerbPatchActions(trollheimHerbPatchID);
                            handleHerbPatch(trollheimHerbPatchID, seedToPlant, config, states.CATHERBY_TELEPORT);
                        }
                        break;

                    case CATHERBY_TELEPORT:
                        log.info("State: CATHERBY_TELEPORT - Catherby enabled: {}", config.enableCatherby());
                        if (config.enableCatherby()) {
                            log.info("State: CATHERBY_TELEPORT - Teleporting to Catherby");
                            handleTeleportToCatherby();
                        } else {
                            botStatus = states.MORYTANIA_TELEPORT;
                        }
                        break;

                    case CATHERBY_WALKING_TO_PATCH:
                        if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && !Rs2Player.isInteracting()) {
                            log.info("State: CATHERBY_WALKING_TO_PATCH - Walking to Catherby patch");
                            handleWalkingToPatch(catherbyHerb, states.CATHERBY_HANDLE_PATCH);
                        }
                        break;

                    case CATHERBY_HANDLE_PATCH:
                        if (Rs2Player.distanceTo(catherbyHerb) < 15) {
                            log.info("State: CATHERBY_HANDLE_PATCH - Handling Catherby patch");
                            printHerbPatchActions(catherbyHerbPatchID);
                            handleHerbPatch(catherbyHerbPatchID, seedToPlant, config, states.MORYTANIA_TELEPORT);
                        }
                        break;

                    case MORYTANIA_TELEPORT:
                        log.info("State: MORYTANIA_TELEPORT - Teleporting to Morytania");
                        if (config.enableMorytania()) {
                            handleTeleportToMorytania(config);
                            // Removed random sleep call here
                        } else {
                            botStatus = states.VARLAMORE_TELEPORT;
                        }
                        break;

                    case MORYTANIA_WALKING_TO_PATCH:
                        if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && !Rs2Player.isInteracting()) {
                            log.info("State: MORYTANIA_WALKING_TO_PATCH - Walking to Morytania patch");
                            handleWalkingToPatch(morytaniaHerb, states.MORYTANIA_HANDLE_PATCH);
                        }
                        break;

                    case MORYTANIA_HANDLE_PATCH:
                        log.info("State: MORYTANIA_HANDLE_PATCH - Handling Morytania patch");
                        if (Rs2Player.distanceTo(morytaniaHerb) < 15) {
                            printHerbPatchActions(morytaniaHerbPatchID);
                            handleHerbPatch(morytaniaHerbPatchID, seedToPlant, config, states.VARLAMORE_TELEPORT);
                        }
                        break;

                    case VARLAMORE_TELEPORT:
                        log.info("State: VARLAMORE_TELEPORT - Teleporting to Varlamore");
                        if (config.enableVarlamore()) {
                            handleTeleportToVarlamore(config);
                        } else {
                            botStatus = states.HOSIDIUS_TELEPORT;
                        }
                        break;

                    case VARLAMORE_WALKING_TO_PATCH:
                        if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && !Rs2Player.isInteracting()) {
                            log.info("State: VARLAMORE_WALKING_TO_PATCH - Walking to Varlamore patch");
                            handleWalkingToPatch(varlamoreHerb, states.VARLAMORE_HANDLE_PATCH);
                        }
                        break;

                    case VARLAMORE_HANDLE_PATCH:
                        if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && !Rs2Player.isInteracting()) {
                            log.info("State: VARLAMORE_HANDLE_PATCH - Handling Varlamore patch");
                            printHerbPatchActions(varlamoreHerbPatchID);
                            handleHerbPatch(varlamoreHerbPatchID, seedToPlant, config, states.HOSIDIUS_TELEPORT);
                        }
                        break;

                    case HOSIDIUS_TELEPORT:
                        log.info("State: HOSIDIUS_TELEPORT - Teleporting to Hosidius");
                        if (config.enableHosidius()) {
                            handleTeleportToHosidius();
                        } else {
                            botStatus = states.ARDOUGNE_TELEPORT;
                        }
                        break;

                    case HOSIDIUS_WALKING_TO_PATCH:
                        if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && !Rs2Player.isInteracting()) {
                            log.info("State: HOSIDIUS_WALKING_TO_PATCH - Walking to Hosidius patch");
                            handleWalkingToPatch(hosidiusHerb, states.HOSIDIUS_HANDLE_PATCH);
                        }
                        break;

                    case HOSIDIUS_HANDLE_PATCH:
                        log.info("State: HOSIDIUS_HANDLE_PATCH - Handling Hosidius patch");
                        if (Rs2Player.distanceTo(hosidiusHerb) < 15) {
                            printHerbPatchActions(hosidiusHerbPatchID);
                            handleHerbPatch(hosidiusHerbPatchID, seedToPlant, config, states.ARDOUGNE_TELEPORT);
                        }
                        break;

                    case ARDOUGNE_TELEPORT:
                        log.info("State: ARDOUGNE_TELEPORT - Teleporting to Ardougne");
                        if (!config.enableArdougne()) {
                            botStatus = states.FALADOR_TELEPORT;
                        } else {
                            handleTeleportToArdougne(config);
                        }
                        break;

                    case ARDOUGNE_WALKING_TO_PATCH:
                        if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && !Rs2Player.isInteracting()) {
                            log.info("State: ARDOUGNE_WALKING_TO_PATCH - Walking to Ardougne patch");
                            handleWalkingToPatch(ardougneHerb, states.ARDOUGNE_HANDLE_PATCH);
                        }
                        break;

                    case ARDOUGNE_HANDLE_PATCH:
                        log.info("State: ARDOUGNE_HANDLE_PATCH - Handling Ardougne patch");
                        if (Rs2Player.distanceTo(ardougneHerb) < 15) {
                            printHerbPatchActions(ardougneHerbPatchID);
                            handleHerbPatch(ardougneHerbPatchID, seedToPlant, config, states.FALADOR_TELEPORT);
                        }

                        break;

                    case FALADOR_TELEPORT:
                        log.info("State: FALADOR_TELEPORT - Teleporting to Falador");
                        if (config.enableFalador()) {
                            handleTeleportToFalador(config);
                        } else {
                            botStatus = states.WEISS_TELEPORT;
                        }
                        break;

                    case FALADOR_WALKING_TO_PATCH:
                        if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && !Rs2Player.isInteracting()) {
                            log.info("State: FALADOR_WALKING_TO_PATCH - Walking to Falador patch");
                            handleWalkingToPatch(cabbageHerb, states.FALADOR_HANDLE_PATCH);
                        }
                        break;

                    case FALADOR_HANDLE_PATCH:
                        log.info("State: FALADOR_HANDLE_PATCH - Handling Falador patch");
                        if (Rs2Player.distanceTo(cabbageHerb) < 15) {
                            printHerbPatchActions(cabbageHerbPatchID);
                            handleHerbPatch(cabbageHerbPatchID, seedToPlant, config, states.WEISS_TELEPORT);
                        }
                        break;

                    case WEISS_TELEPORT:
                        log.info("State: WEISS_TELEPORT - Teleporting to Weiss");
                        if (config.enableWeiss()) {
                            handleTeleportToWeiss();
                        } else {
                            botStatus = states.HARMONY_TELEPORT;
                        }
                        break;

                    case WEISS_WALKING_TO_PATCH:
                        if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && !Rs2Player.isInteracting()) {
                            log.info("State: WEISS_WALKING_TO_PATCH - Walking to Weiss patch");
                            handleWalkingToPatch(weissHerb, states.WEISS_HANDLE_PATCH);
                        }
                        break;

                    case WEISS_HANDLE_PATCH:
                        log.info("State: WEISS_HANDLE_PATCH - Handling Weiss patch");
                        // Removed random sleep calls (sleep(3000, 5000) and sleep(800, 1300))
                        if (Rs2Player.distanceTo(weissHerb) < 15) {
                            printHerbPatchActions(weissHerbPatchID);
                            handleHerbPatch(weissHerbPatchID, seedToPlant, config, states.HARMONY_TELEPORT);
                        }
                        break;

                    case HARMONY_TELEPORT:
                        log.info("State: HARMONY_TELEPORT - Teleporting to Harmony");
                        if (config.enableHarmony()) {
                            handleTeleportToHarmony();
                        } else {
                            botStatus = states.GUILD_TELEPORT;
                        }
                        break;

                    case HARMONY_WALKING_TO_PATCH:
                        log.info("State: HARMONY_WALKING_TO_PATCH - Walking to Harmony patch");
                        handleWalkingToPatch(harmonyHerb, states.HARMONY_HANDLE_PATCH);
                        break;

                    case HARMONY_HANDLE_PATCH:
                        if (!Rs2Player.isMoving()) {
                            log.info("State: HARMONY_HANDLE_PATCH - Handling Harmony patch");
                            printHerbPatchActions(harmonyHerbPatchID);
                            handleHerbPatch(harmonyHerbPatchID, seedToPlant, config, states.GUILD_TELEPORT);
                        }
                        break;

                    case GUILD_TELEPORT:
                        log.info("State: GUILD_TELEPORT - Teleporting to Guild");
                        if (config.enableGuild()) {
                            handleTeleportToGuild(config);
                            sleep(400);
                            botStatus = states.GUILD_WALKING_TO_PATCH;
                        } else {
                            botStatus = states.FINISHED;
                        }
                        break;

                    case GUILD_WALKING_TO_PATCH:
                        if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && !Rs2Player.isInteracting()) {
                            log.info("State: GUILD_WALKING_TO_PATCH - Walking to Guild patch");
                            handleWalkingToPatch(farmingGuildHerb, states.GUILD_HANDLE_PATCH);
                        }
                        break;

                    case GUILD_HANDLE_PATCH:
                        log.info("State: GUILD_HANDLE_PATCH - Handling farming guild herb patch");
                        if (Rs2Player.distanceTo(farmingGuildHerb) < 20) {
                            printHerbPatchActions(farmingGuildHerbPatchID);
                            handleHerbPatch(farmingGuildHerbPatchID, seedToPlant, config, states.FINISHED);
                        }
                        break;

                    case FINISHED:
                        log.info("State: FINISHED - Shutting down");
                        shutdown();
                        break;
                }



            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private void checkBeforeWithdrawAndEquip(String itemName) {
        if (!Rs2Equipment.isWearing(itemName)) {
            Rs2Bank.withdrawAndEquip(itemName);
        }
    }

    private void equipGraceful(HerbrunConfig config) {
        checkBeforeWithdrawAndEquip("GRACEFUL HOOD");
        if (!config.FARMING_CAPE()) {
            checkBeforeWithdrawAndEquip("GRACEFUL CAPE");
        }
        checkBeforeWithdrawAndEquip("GRACEFUL BOOTS");
        checkBeforeWithdrawAndEquip("GRACEFUL GLOVES");
        checkBeforeWithdrawAndEquip("GRACEFUL TOP");
        checkBeforeWithdrawAndEquip("GRACEFUL LEGS");
    }

    private void withdrawHerbSetup(HerbrunConfig config) {
        Rs2Bank.withdrawX(config.SEED().getItemId(), 10);
        if (config.COMPOST()) {
            Rs2Bank.withdrawOne(ItemID.BOTTOMLESS_COMPOST_BUCKET_22997);
        } else {
            Rs2Bank.withdrawX(ItemID.ULTRACOMPOST, 8);
        }
        Rs2Bank.withdrawOne(ItemID.RAKE);
        Rs2Bank.withdrawOne(ItemID.SEED_DIBBER);
        Rs2Bank.withdrawOne(ItemID.SPADE);
        if (config.enableMorytania()) {
            if (config.USE_ECTOPHIAL()) {
                Rs2Bank.withdrawOne(ItemID.ECTOPHIAL);
            } else {
                Rs2Bank.withdrawOne(ItemID.FENKENSTRAINS_CASTLE_TELEPORT);
            }
        }
        if (config.enableVarlamore()) {
            if (config.USE_QUETZAL_WHISTLE()) {
                if (Rs2Bank.hasItem(ItemID.PERFECTED_QUETZAL_WHISTLE)) {
                    Rs2Bank.withdrawOne(ItemID.PERFECTED_QUETZAL_WHISTLE);
                } else if (Rs2Bank.hasItem(ItemID.ENHANCED_QUETZAL_WHISTLE)) {
                    Rs2Bank.withdrawOne(ItemID.ENHANCED_QUETZAL_WHISTLE);
                } else if (Rs2Bank.hasItem(ItemID.BASIC_QUETZAL_WHISTLE)) {
                    Rs2Bank.withdrawOne(ItemID.BASIC_QUETZAL_WHISTLE);
                }
            } else {
                Rs2Bank.withdrawOne(ItemID.CIVITAS_ILLA_FORTIS_TELEPORT);
            }
        }
        if (config.enableHosidius()) {
            Rs2Bank.withdrawOne(ItemID.XERICS_TALISMAN);
        }
        if (config.enableArdougne()) {
            if (config.ARDOUGNE_TELEPORT_OPTION()) {
                Rs2Bank.withdrawOne(config.CLOAK().getItemId());
            } else {
                Rs2Bank.withdrawOne(ItemID.ARDOUGNE_TELEPORT);
            }
        }
        if (config.enableGuild()) {
            if (!config.FARMING_CAPE()) {
                if (Rs2Bank.hasItem(ItemID.SKILLS_NECKLACE1)) {
                    Rs2Bank.withdrawOne(ItemID.SKILLS_NECKLACE1);
                } else if (Rs2Bank.hasItem(ItemID.SKILLS_NECKLACE2)) {
                    Rs2Bank.withdrawOne(ItemID.SKILLS_NECKLACE2);
                } else if (Rs2Bank.hasItem(ItemID.SKILLS_NECKLACE3)) {
                    Rs2Bank.withdrawOne(ItemID.SKILLS_NECKLACE3);
                } else if (Rs2Bank.hasItem(ItemID.SKILLS_NECKLACE4)) {
                    Rs2Bank.withdrawOne(ItemID.SKILLS_NECKLACE4);
                } else if (Rs2Bank.hasItem(ItemID.SKILLS_NECKLACE5)) {
                    Rs2Bank.withdrawOne(ItemID.SKILLS_NECKLACE5);
                } else {
                    Rs2Bank.withdrawOne(ItemID.SKILLS_NECKLACE6);
                }
            } else {
                if (Rs2Bank.hasItem(ItemID.FARMING_CAPE)) {
                    Rs2Bank.withdrawOne(ItemID.FARMING_CAPE);
                } else if (Rs2Bank.hasItem(ItemID.FARMING_CAPET)) {
                    Rs2Bank.withdrawOne(ItemID.FARMING_CAPET);
                }
            }
        }
        if (config.enableFalador()) {
            if (config.FALADOR_TELEPORT_OPTION()) {
                Rs2Bank.withdrawOne(config.RING().getItemId());
            } else {
                Rs2Bank.withdrawOne(ItemID.FALADOR_TELEPORT);
            }
        }
        if (config.enableWeiss()) {
            Rs2Bank.withdrawOne(ItemID.ICY_BASALT);
        }
        if (config.enableCatherby()) {
            Rs2Bank.withdrawOne(ItemID.CAMELOT_TELEPORT);
        }
        if (config.enableTrollheim()) {
            Rs2Bank.withdrawOne(ItemID.STONY_BASALT);
        }
        if (config.enableHarmony()) {
            Rs2Bank.withdrawOne(ItemID.HARMONY_ISLAND_TELEPORT);
        }
        checkBeforeWithdrawAndEquip("Magic secateurs");
    }

    private void handleTeleportToTrollheim() {
        System.out.println("Teleporting to Trollheim");
        botStatus = states.TROLLHEIM_WALKING_TO_PATCH;
    }

    private void handleTeleportToHarmony() {
        System.out.println("Teleporting to Harmony...");
        botStatus = states.HARMONY_WALKING_TO_PATCH;
    }

    private void handleTeleportToCatherby() {
        System.out.println("Teleporting to Catherby...");
        botStatus = states.CATHERBY_WALKING_TO_PATCH;
    }

    private void handleTeleportToMorytania(HerbrunConfig config) {
        System.out.println("Teleporting to Morytania...");
        botStatus = states.MORYTANIA_WALKING_TO_PATCH;
    }

    private void handleTeleportToVarlamore(HerbrunConfig config) {
        System.out.println("Teleporting to Varlamore...");
        botStatus = states.VARLAMORE_WALKING_TO_PATCH;
    }

    private void handleTeleportToHosidius() {
        System.out.println("Teleporting to Hosidius...");
        botStatus = states.HOSIDIUS_WALKING_TO_PATCH;
    }

    private void handleTeleportToArdougne(HerbrunConfig config) {
        System.out.println("Teleporting to Ardougne...");
        botStatus = states.ARDOUGNE_WALKING_TO_PATCH;
    }

    private void handleTeleportToFalador(HerbrunConfig config) {
        System.out.println("Teleporting to Falador...");
        botStatus = states.FALADOR_WALKING_TO_PATCH;
    }

    private void handleTeleportToGuild(HerbrunConfig config) {
        System.out.println("Teleporting to the Farming guild...");
        botStatus = states.GUILD_WALKING_TO_PATCH;
    }

    private void handleTeleportToWeiss() {
        System.out.println("Teleporting to Weiss...");
        botStatus = states.WEISS_WALKING_TO_PATCH;
    }



    private void handleWalkingToPatch(WorldPoint location, states nextState) {
        System.out.println("Walking to the herb patch...");

        // Start walking to the location
        Rs2Walker.walkTo(location);
        // Wait until the player reaches within 2 tiles of the location and has stopped moving
        sleepUntil(() -> Rs2Player.distanceTo(location) < 5);
        if (Rs2Player.distanceTo(location) < 5) {
            log("Arrived at herb patch.");
            botStatus = nextState;
        }
    }


    private void handleHerbPatch(int patchId, int seedToPlant, HerbrunConfig config, states nextState) {
        // Define possible actions the herb patch could have
        if (!Rs2Player.isMoving() &&
                !Rs2Player.isAnimating() &&
                !Rs2Player.isInteracting()) {
            String[] possibleActions = {"Pick", "Rake", "Clear", "Inspect"};

            GameObject herbPatch = null;
            String foundAction = null;

            // Loop through the possible actions and try to find the herb patch with any valid action
            for (String action : possibleActions) {
                herbPatch = Rs2GameObject.findObjectByImposter(patchId, action);  // Find object by patchId and action
                if (herbPatch != null) {
                    foundAction = action;
                    break;  // Exit the loop once we find the patch with a valid action
                }
            }

            // If no herb patch is, print an error and return
            if (herbPatch == null) {
                System.out.println("Herb patch not found with any of the possible actions!");
                return;
            }

            // Handle the patch based on the action found
            switch (foundAction) {
                case "Pick":
                    handlePickAction(herbPatch, patchId, config);
                    break;
                case "Rake":
                    handleRakeAction(herbPatch);
                    break;
                case "Clear":
                    handleClearAction(herbPatch);
                    break;
                case "Inspect":
                    if(Rs2GameObject.convertGameObjectToObjectComposition(herbPatch.getId()).getName().equals("Herbs")) {
                        botStatus = nextState;
                    }
                    else {
                        log.info("Patch is empty, planting seeds...");
                        addCompostandSeeds(config, patchId, seedToPlant, nextState);
                    }
                    break;

                default:
                    System.out.println("Unexpected action found on herb patch: " + foundAction);
                    break;
            }

        }

    }

    private void handlePickAction(GameObject herbPatch, int patchId, HerbrunConfig config) {
        System.out.println("Picking herbs...");
        Rs2NpcModel leprechaun = Rs2Npc.getNpc("Tool leprechaun");

        // If inventory is full, note herbs first
        if (Rs2Inventory.isFull()) {
            noteHerbs(config, leprechaun);
        }

        int loopCount = Rs2Random.randomGaussian(5, 2);
        pickHerbs(herbPatch, config, loopCount);

        // Continuously update the patch composition for each check
        sleepUntil(() -> !Rs2GameObject.hasAction(getPatchComp(patchId), "Pick") ||
                !Rs2Player.isAnimating());

        // If the patch now offers the "rake" action, handle raking and exit.
        if (Rs2GameObject.hasAction(getPatchComp(patchId), "rake")) {
            System.out.println("Weeds grew, switching to rake action...");
            handleRakeAction(herbPatch);
            return;
        }

        // If still pickable and inventory is full, note herbs and pick the remaining ones.
        if (Rs2GameObject.hasAction(getPatchComp(patchId), "Pick") && Rs2Inventory.isFull()) {
            noteHerbs(config, leprechaun);
            loopCount = Rs2Random.randomGaussian(5, 2);
            pickHerbs(herbPatch, config, loopCount);
        }

    }

    private void noteHerbs(HerbrunConfig config, Rs2NpcModel leprechaun) {
        System.out.println("Noting herbs with tool leprechaun...");
        Rs2Inventory.useItemOnNpc(config.SEED().getHerbId(), leprechaun);
        Rs2Inventory.waitForInventoryChanges(7000);
    }

    private void pickHerbs(GameObject herbPatch, HerbrunConfig config, int loopCount) {
        Rs2GameObject.interact(herbPatch, "pick");
        if (config.FAST_HERB()) {
            Rs2Player.waitForXpDrop(Skill.FARMING);
            for (int i = 0; i < loopCount; i++) {
                Rs2GameObject.interact(herbPatch, "pick");
                sleep(25, 100);
            }
        }
        Rs2Player.waitForAnimation(15000);
    }

    /**
     * Helper method to update and retrieve the current patch composition.
     */
    private ObjectComposition getPatchComp(int patchId) {
        return Rs2GameObject.findObjectComposition(patchId);
    }


    private void handleRakeAction(GameObject herbPatch) {
        System.out.println("Raking the patch...");

        // Rake the patch
        Rs2GameObject.interact(herbPatch, "rake");

        Rs2Player.waitForAnimation(12000);

        // Drop the weeds (assuming weeds are added to the inventory)
        if (!Rs2Player.isMoving() &&
                !Rs2Player.isAnimating() &&
                !Rs2Player.isInteracting()) {
            System.out.println("Dropping weeds...");
            Rs2Inventory.dropAll(ItemID.WEEDS);
        }
    }

    private void handleClearAction(GameObject herbPatch) {
        System.out.println("Clearing the herb patch...");

        // Try to interact with the patch using the "clear" action
        boolean interactionSuccess = Rs2GameObject.interact(herbPatch, "clear");

        if (!interactionSuccess) {
            System.out.println("Failed to interact with the herb patch to clear it.");
            return;
        }

        // Wait for the clearing animation to finish
        Rs2Player.waitForAnimation(12000);
    }

    private void printHerbPatchActions(int patchId) {
        TileObject herbPatch = Rs2GameObject.findObjectById(patchId);
        if (herbPatch == null) {
            System.out.println("Herb patch not found for ID: " + patchId);
            return;
        }

        ObjectComposition herbPatchComposition = Objects.requireNonNull(Rs2GameObject.findObjectComposition(patchId)).getImpostor();
        System.out.println("Available actions for herb patch:");
        for (String action : herbPatchComposition.getActions()) {
            if (action != null) {
                System.out.println(action);  // Print each available action
            }
        }
    }

    private void addCompostandSeeds(HerbrunConfig config, int patchId, int seedToPlant, states state) {
        // Check that the player is idle before interacting with the patch
        if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() &&
                !Rs2Player.isInteracting()) {

            // Apply compost based on configuration
            System.out.println("Applying compost...");
            int compostItemId = config.COMPOST() ? ItemID.BOTTOMLESS_COMPOST_BUCKET_22997 : ItemID.ULTRACOMPOST;
            Rs2Inventory.useItemOnObject(compostItemId, patchId);
            // Wait for farming XP drop to confirm compost application
            Rs2Player.waitForXpDrop(Skill.FARMING);
            sleep(50, 1200);

            // Plant seeds in the patch
            System.out.println("Planting seeds...");
            Rs2Inventory.useItemOnObject(seedToPlant, patchId);

            // Wait until interaction is complete
            Rs2Player.waitForAnimation();
            if (Rs2Inventory.contains(ItemID.EMPTY_BUCKET)) Rs2Inventory.drop(ItemID.EMPTY_BUCKET);

            // Update the bot status
            botStatus = state;
        }
    }

}