package net.runelite.client.plugins.microbot.herbrun;

import java.awt.*;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;


import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.Microbot.log;
import static net.runelite.client.plugins.microbot.herbrun.HerbrunInfo.*;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;


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
                            break;
                        } else {
                            //Bank everything and withdraw all farming equipment
                            System.out.println("Gearing up");
                            if (!Rs2Bank.isOpen()) {
                                System.out.println("Bank opened");
                                Rs2Bank.useBank();
                                Rs2Bank.depositAll();
                                if (config.GRACEFUL()) {
                                    Rs2Bank.depositEquipment();
                                    sleep(200);
                                    equipGraceful(config);
                                }
                            }
                            withdrawHerbSetup(config);
                            Rs2Bank.closeBank();
                            sleep(100);
                            System.out.println("Gearing complete");
                            sleep(200, 800);
                            botStatus = states.TROLLHEIM_TELEPORT;
                            break;
                        }
                    case TROLLHEIM_TELEPORT:
                        System.out.println("Current state: TROLLHEIM_TELEPORT");
                        if (config.enableTrollheim()) {
                            handleTeleportToTrollheim();
                        } else {
                            botStatus = states.CATHERBY_TELEPORT;
                        }
                        break;
                    case TROLLHEIM_WALKING_TO_PATCH:
                        System.out.println("Current state: TROLLHEIM_WALKING_TO_PATCH");
                        handleWalkingToPatch(trollheimHerb, states.TROLLHEIM_HANDLE_PATCH);
                        break;
                    case TROLLHEIM_HANDLE_PATCH:
                        if (Rs2Player.distanceTo(trollheimHerb) < 8) {
                            System.out.println("Current state: TROLLHEIM_HANDLE_PATCH");
                            printHerbPatchActions(trollheimHerbPatchID);
                            handleHerbPatch(trollheimHerbPatchID, seedToPlant, config);
                            sleep(200);
                            addCompostandSeeds(config, trollheimHerbPatchID, seedToPlant, states.CATHERBY_TELEPORT);
                            break;
                        }
                    case CATHERBY_TELEPORT:
                        log("" + config.enableCatherby());
                        if (config.enableCatherby()) {
                            System.out.println("Current state: CATHERBY_TELEPORT");
                            handleTeleportToCatherby();
                        } else {
                            botStatus = states.MORYTANIA_TELEPORT;
                            break;
                        }
                    case CATHERBY_WALKING_TO_PATCH:
                        if (!Rs2Player.isMoving() &&
                                !Rs2Player.isAnimating() &&
                                !Rs2Player.isInteracting()) {
                            System.out.println("Current state: CATHERBY_WALKING_TO_PATCH");
                            handleWalkingToPatch(catherbyHerb, states.CATHERBY_HANDLE_PATCH);
                        }
                    case CATHERBY_HANDLE_PATCH:
                        if (Rs2Player.distanceTo(catherbyHerb) < 15) {
                            System.out.println("Current state: CATHERBY_HANDLE_PATCH");
                            printHerbPatchActions(catherbyHerbPatchID);
                            handleHerbPatch(catherbyHerbPatchID, seedToPlant, config);
                        }
                        if (!Rs2Player.isMoving() &&
                                !Rs2Player.isAnimating() &&
                                !Rs2Player.isInteracting()) {
                            addCompostandSeeds(config, catherbyHerbPatchID, seedToPlant, states.MORYTANIA_TELEPORT);
                        }
                        break;
                    case MORYTANIA_TELEPORT:
                        if (config.enableMorytania()) {
                            handleTeleportToMorytania(config);
                            sleep(600, 1200);
                        } else {
                            botStatus = states.VARLAMORE_TELEPORT;
                        }
                        break;
                    case MORYTANIA_WALKING_TO_PATCH:
                        if (!Rs2Player.isMoving() &&
                                !Rs2Player.isAnimating() &&
                                !Rs2Player.isInteracting()) {
                            handleWalkingToPatch(morytaniaHerb, states.MORYTANIA_HANDLE_PATCH);
                            break;
                        }
                    case MORYTANIA_HANDLE_PATCH:
                        System.out.println("Handling Morytania patch");
                        if (Rs2Player.distanceTo(morytaniaHerb) < 15) {
                            printHerbPatchActions(morytaniaHerbPatchID);
                            handleHerbPatch(morytaniaHerbPatchID, seedToPlant, config);
                        }
                        if (!Rs2Player.isMoving() &&
                                !Rs2Player.isAnimating() &&
                                !Rs2Player.isInteracting()) {
                            addCompostandSeeds(config, morytaniaHerbPatchID, seedToPlant, states.VARLAMORE_TELEPORT);
                        }
                        break;
                    case VARLAMORE_TELEPORT:
                        if (config.enableVarlamore()) {
                            handleTeleportToVarlamore(config);
                        } else {
                            botStatus = states.HOSIDIUS_TELEPORT;
                        }
                        break;
                    case VARLAMORE_WALKING_TO_PATCH:
                        if (!Rs2Player.isMoving() &&
                                !Rs2Player.isAnimating() &&
                                !Rs2Player.isInteracting()) {
                            handleWalkingToPatch(varlamoreHerb, states.VARLAMORE_HANDLE_PATCH);
                        }
                        break;
                    case VARLAMORE_HANDLE_PATCH:
                        if (!Rs2Player.isMoving() && !Rs2Player.isAnimating() && !Rs2Player.isInteracting()) {
                            printHerbPatchActions(varlamoreHerbPatchID);
                            handleHerbPatch(varlamoreHerbPatchID, seedToPlant, config);
                            addCompostandSeeds(config, varlamoreHerbPatchID, seedToPlant, states.HOSIDIUS_TELEPORT);
                        }
                        break;
                    case HOSIDIUS_TELEPORT:
                        if (config.enableHosidius()) {
                            handleTeleportToHosidius();
                        } else {
                            botStatus = states.ARDOUGNE_TELEPORT;
                        }
                        break;
                    case HOSIDIUS_WALKING_TO_PATCH:
                        if (!Rs2Player.isMoving() &&
                                !Rs2Player.isAnimating() &&
                                !Rs2Player.isInteracting()) {
                            handleWalkingToPatch(hosidiusHerb, states.HOSIDIUS_HANDLE_PATCH);
                        }
                        break;
                    case HOSIDIUS_HANDLE_PATCH:
                        if (Rs2Player.distanceTo(hosidiusHerb) < 15) {
                            printHerbPatchActions(hosidiusHerbPatchID);
                            handleHerbPatch(hosidiusHerbPatchID, seedToPlant, config);

                        }
                        if (!Rs2Player.isMoving() &&
                                !Rs2Player.isAnimating() &&
                                !Rs2Player.isInteracting()) {
                            addCompostandSeeds(config, hosidiusHerbPatchID, seedToPlant, states.ARDOUGNE_TELEPORT);
                        }
                        break;
                    case ARDOUGNE_TELEPORT:
                        if (!config.enableArdougne()) {
                            botStatus = states.FALADOR_TELEPORT;
                            break;
                        } else {
                            handleTeleportToArdougne(config);
                        }
                    case ARDOUGNE_WALKING_TO_PATCH:
                        if (!Rs2Player.isMoving() &&
                                !Rs2Player.isAnimating() &&
                                !Rs2Player.isInteracting()) {
                            log("Current state: ARDOUGNE_WALKING_TO_PATCH");
                            handleWalkingToPatch(ardougneHerb, states.ARDOUGNE_HANDLE_PATCH);
                        }
                        break;
                    case ARDOUGNE_HANDLE_PATCH:
                        if (Rs2Player.distanceTo(ardougneHerb) < 15) {
                            printHerbPatchActions(ardougneHerbPatchID);
                            handleHerbPatch(ardougneHerbPatchID, seedToPlant, config);
                        }
                        if (!Rs2Player.isMoving() &&
                                !Rs2Player.isAnimating() &&
                                !Rs2Player.isInteracting()) {
                            addCompostandSeeds(config, ardougneHerbPatchID, seedToPlant, states.FALADOR_TELEPORT);
                        }
                        break;
                    case FALADOR_TELEPORT:
                        if (config.enableFalador()) {
                            handleTeleportToFalador(config);
                        } else {
                            botStatus = states.WEISS_TELEPORT;
                        }
                        break;
                    case FALADOR_WALKING_TO_PATCH:
                        if (!Rs2Player.isMoving() &&
                                !Rs2Player.isAnimating() &&
                                !Rs2Player.isInteracting()) {
                            handleWalkingToPatch(cabbageHerb, states.FALADOR_HANDLE_PATCH);
                        }
                        break;
                    case FALADOR_HANDLE_PATCH:
                        if (Rs2Player.distanceTo(cabbageHerb) < 15) {
                            printHerbPatchActions(cabbageHerbPatchID);
                            handleHerbPatch(cabbageHerbPatchID, seedToPlant, config);
                        }
                        if (!Rs2Player.isMoving() &&
                                !Rs2Player.isAnimating() &&
                                !Rs2Player.isInteracting()) {
                            addCompostandSeeds(config, cabbageHerbPatchID, seedToPlant, botStatus = states.WEISS_TELEPORT);
                        }
                        break;
                    case WEISS_TELEPORT:
                        if (config.enableWeiss()) {
                            handleTeleportToWeiss();
                        } else {
                            botStatus = states.GUILD_TELEPORT;
                        }
                        break;
                    case WEISS_HANDLE_PATCH:
                        sleep(3000, 5000);
                        if (Rs2Player.distanceTo(weissHerb) < 15) {
                            printHerbPatchActions(weissHerbPatchID);
                            handleHerbPatch(weissHerbPatchID, seedToPlant, config);
                            sleep(800,1300);
                        }
                        if (!Rs2Player.isMoving() &&
                                !Rs2Player.isAnimating() &&
                                !Rs2Player.isInteracting()) {
                            addCompostandSeeds(config, weissHerbPatchID, seedToPlant, states.HARMONY_TELEPORT);
                            break;
                        }
                    case HARMONY_TELEPORT:
                        if (config.enableHarmony()) {
                            handleTeleportToHarmony();
                        } else {
                            botStatus = states.GUILD_TELEPORT;
                        }
                        break;
                    case HARMONY_WALKING_TO_PATCH:
                        System.out.println("Current state: HARMONY_WALKING_TO_PATCH");
                        handleWalkingToPatch(harmonyHerb, states.HARMONY_HANDLE_PATCH);
                        break;
                    case HARMONY_HANDLE_PATCH:
                        if (!Rs2Player.isWalking()) {
                            System.out.println("Current state: HARMONEY_HANDLE_PATCH");
                            printHerbPatchActions(harmonyHerbPatchID);
                            handleHerbPatch(harmonyHerbPatchID, seedToPlant, config);
                            sleep(200);
                            addCompostandSeeds(config, harmonyHerbPatchID, seedToPlant, states.GUILD_TELEPORT);
                            break;
                        }
                    case GUILD_TELEPORT:
                        if (config.enableGuild()) {
                            handleTeleportToGuild(config);
                            sleep(400);
                            botStatus = states.GUILD_WALKING_TO_PATCH;
                            break;
                        } else {
                            botStatus = states.FINISHED;
                            break;
                        }
                    case GUILD_WALKING_TO_PATCH:
                        if (!Rs2Player.isMoving() &&
                                !Rs2Player.isAnimating() &&
                                !Rs2Player.isInteracting()) {
                            handleWalkingToPatch(farmingGuildHerb, states.GUILD_HANDLE_PATCH);
                        }
                        break;
                    case GUILD_HANDLE_PATCH:
                        log(Rs2Player.isAnimating() + "");
                        log("handling herb patch...");
                        if (Rs2Player.distanceTo(farmingGuildHerb) < 20) {
                            printHerbPatchActions(farmingGuildHerbPatchID);
                            handleHerbPatch(farmingGuildHerbPatchID, seedToPlant, config);
                        }
                        if (!Rs2Player.isMoving() &&
                                !Rs2Player.isAnimating() &&
                                !Rs2Player.isInteracting()) {
                            addCompostandSeeds(config, farmingGuildHerbPatchID, seedToPlant, botStatus = states.FINISHED);
                        }
                        break;
                    case FINISHED:
                        shutdown();  // Optionally handle completion
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

    private boolean trollheimTeleport() {
        sleep(400);
        if (!Rs2Player.isAnimating()) {
            boolean success = Rs2Inventory.interact(ItemID.STONY_BASALT, "Troll Stronghold");
            Rs2Player.waitForAnimation();
            sleepUntil(() -> !Rs2Player.isAnimating());
            return success;
        }
        return true;
    }

    private void handleTeleportToTrollheim() {
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to Trollheim");
            boolean success = trollheimTeleport();
            if (success) {
                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving());
                System.out.println("Arrived at Trollheim teleport spot.");
                botStatus = states.TROLLHEIM_WALKING_TO_PATCH;
            }
        }
    }

    private boolean harmonyTeleport() {
        sleep(100);
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to Catherby");
            boolean success = Rs2Inventory.interact(ItemID.HARMONY_ISLAND_TELEPORT, "Break");
            Rs2Player.waitForAnimation();
            sleepUntil(() -> !Rs2Player.isAnimating());
            return success;
        }
        return false;
    }

    private void handleTeleportToHarmony() {
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to Harmony...");
            boolean success = harmonyTeleport();

            if (success) {
                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving());
                System.out.println("Arrived at Harmony teleport spot.");
                botStatus = states.HARMONY_WALKING_TO_PATCH;
            } else {
                System.out.println("Teleport to Harmony failed!");
            }
        }
    }


    private boolean catherbyTeleport() {
        sleep(100);
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to Catherby");
            boolean success = Rs2Inventory.interact(ItemID.CAMELOT_TELEPORT, "Break");
            Rs2Player.waitForAnimation();
            sleepUntil(() -> !Rs2Player.isAnimating());
            return success;
        }
        return false;
    }

    private void handleTeleportToCatherby() {
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to Catherby...");
            boolean success = catherbyTeleport();

            if (success) {
                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving());
                System.out.println("Arrived at Catherby teleport spot.");
                botStatus = states.CATHERBY_WALKING_TO_PATCH;
            } else {
                System.out.println("Teleport to Catherby failed!");
            }
        }
    }


    private boolean morytaniaTeleport(HerbrunConfig config) {
        sleep(100);
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to Morytania");
            if (config.USE_ECTOPHIAL()) {
                Rs2Inventory.interact(ItemID.ECTOPHIAL, "empty");
            } else {
                Rs2Inventory.interact(ItemID.FENKENSTRAINS_CASTLE_TELEPORT, "break");
            }
            Rs2Player.waitForAnimation();
            sleepUntil(() -> !Rs2Player.isAnimating());
            return true;
        }
        return false;
    }


    private void handleTeleportToMorytania(HerbrunConfig config) {
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to Morytania...");
            boolean success = morytaniaTeleport(config);  // Perform the teleport

            if (success) {
                // Wait until the player has stopped animating and moving
                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 10000);
                System.out.println("Arrived at Morytania teleport spot.");
                botStatus = states.MORYTANIA_WALKING_TO_PATCH;  // Move to the next state
            } else {
                System.out.println("Teleport to Morytania failed!");
            }
        }
    }

    private boolean varlamoreTeleport(HerbrunConfig config) {
        sleep(100);
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to Varlamore");
            if (config.USE_QUETZAL_WHISTLE()) {
                if (Rs2Inventory.contains(ItemID.PERFECTED_QUETZAL_WHISTLE)) {
                    boolean success = Rs2Inventory.interact(ItemID.PERFECTED_QUETZAL_WHISTLE, "Signal");
                } else if (Rs2Inventory.contains(ItemID.ENHANCED_QUETZAL_WHISTLE)) {
                    boolean success = Rs2Inventory.interact(ItemID.ENHANCED_QUETZAL_WHISTLE, "Signal");
                } else if (Rs2Inventory.contains(ItemID.BASIC_QUETZAL_WHISTLE)) {
                    boolean success = Rs2Inventory.interact(ItemID.BASIC_QUETZAL_WHISTLE, "Signal");
                }
            } else {
                boolean success = Rs2Inventory.interact(ItemID.CIVITAS_ILLA_FORTIS_TELEPORT, "break");
            }
            Rs2Player.waitForAnimation();
            sleepUntil(() -> !Rs2Player.isAnimating());
            return true;
        }
        return false;
    }


    private void handleTeleportToVarlamore(HerbrunConfig config) {
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to Varlamore...");
            boolean success = varlamoreTeleport(config);  // Perform teleportation
            if (success) {
                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving());
                System.out.println("Arrived at Varlamore teleport spot.");
                botStatus = states.VARLAMORE_WALKING_TO_PATCH;
            } else {
                System.out.println("Teleport to Varlamore failed!");
            }
        }
    }

    private boolean hosidiusTeleport() {
        sleep(100);
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to Hosidius");
            boolean success = Rs2Inventory.interact(ItemID.XERICS_TALISMAN, "rub");
            sleep(700, 1200);
            Rs2Keyboard.keyPress('2');
//            Rs2Widget.clickWidget("2: Xeric's glade");
            Rs2Player.waitForAnimation();
            sleepUntil(() -> !Rs2Player.isAnimating());
            return success;
        }
        return false;
    }

    private void handleTeleportToHosidius() {
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to Hosidius...");
            boolean success = hosidiusTeleport();

            if (success) {
                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving());
                System.out.println("Arrived at Hosidius teleport spot.");
                botStatus = states.HOSIDIUS_WALKING_TO_PATCH;
            } else {
                System.out.println("Teleport to Hosidius failed!");
            }
        }
    }

    private boolean ardougneTeleport(HerbrunConfig config) {

        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving() && !Rs2Player.isInteracting()) {
            System.out.println("Teleporting to Ardougne farm patch");
            if (config.ARDOUGNE_TELEPORT_OPTION()) {
                boolean success = Rs2Inventory.interact(config.CLOAK().getItemId(), "Farm Teleport");
            } else if (Rs2Inventory.contains(item -> item.getName().toLowerCase().contains("skills necklace"))) {
                Rs2Inventory.interact("skills necklace", "rub");
                sleepUntilTrue(() -> !Rs2Widget.isHidden(ComponentID.ADVENTURE_LOG_CONTAINER), Rs2Player::isMoving, 100, 10000);
                Rs2Keyboard.keyPress('1');
            }
            sleepUntil(() -> !Rs2Player.isAnimating());
            return true;
        } else {
            boolean success = Rs2Inventory.interact(ItemID.ARDOUGNE_TELEPORT, "Break");

            Rs2Player.waitForAnimation();
            sleepUntil(() -> !Rs2Player.isAnimating());
            sleep(100, 400);
            return true;
        }
    }

    private void handleTeleportToArdougne(HerbrunConfig config) {
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to Ardougne...");
            boolean success = ardougneTeleport(config);
            if (success) {
                // Wait until the player stops animating and moving after the teleport
                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 30000);

                // Ensure the teleport was successful before moving to the next state
                if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
                    System.out.println("Arrived at Ardougne teleport spot.");
                    if (Rs2Inventory.contains(config.CLOAK().getItemId())) {
                        botStatus = states.ARDOUGNE_HANDLE_PATCH; // Move to the next step only if teleport is complete
                    } else {
                        botStatus = states.ARDOUGNE_WALKING_TO_PATCH;
                    }

                } else {
                    System.out.println("Teleport to Ardougne failed! Retrying...");
                    botStatus = states.ARDOUGNE_TELEPORT; // Retry teleport if failed
                }
            }
        }
    }

    private boolean faladorTeleport(HerbrunConfig config) {
        sleep(100);
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to Falador herb patch");
            boolean success = Rs2Inventory.interact(config.RING().getItemId(), "Teleport");
            Rs2Player.waitForAnimation();
            sleepUntil(() -> !Rs2Player.isAnimating());
            sleep(200, 600);
            return true;
        }
        return false;
    }

    private void handleTeleportToFalador(HerbrunConfig config) {
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to Falador...");
            boolean success = faladorTeleport(config);  // Perform the teleport

            if (success) {
                // Wait until the player has stopped animating and moving
                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving());
                System.out.println("Arrived at Falador teleport spot.");
                botStatus = states.FALADOR_WALKING_TO_PATCH;  // Move to the next state
            } else {
                System.out.println("Teleport to Falador failed!");
            }
        }
    }

    private boolean guildTeleport(HerbrunConfig config) {
        sleep(100);
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to the Farming guild");
            if (!config.FARMING_CAPE()) {
                if (Rs2Inventory.contains(ItemID.SKILLS_NECKLACE1)) {
                    Rs2Inventory.interact(ItemID.SKILLS_NECKLACE1, "rub");
                } else if (Rs2Inventory.contains(ItemID.SKILLS_NECKLACE2)) {
                    Rs2Inventory.interact(ItemID.SKILLS_NECKLACE2, "rub");
                } else if (Rs2Inventory.contains(ItemID.SKILLS_NECKLACE3)) {
                    Rs2Inventory.interact(ItemID.SKILLS_NECKLACE3, "rub");
                } else if (Rs2Inventory.contains(ItemID.SKILLS_NECKLACE4)) {
                    Rs2Inventory.interact(ItemID.SKILLS_NECKLACE4, "rub");
                } else if (Rs2Inventory.contains(ItemID.SKILLS_NECKLACE5)) {
                    Rs2Inventory.interact(ItemID.SKILLS_NECKLACE5, "rub");
                } else if (Rs2Inventory.contains(ItemID.SKILLS_NECKLACE6)) {
                    Rs2Inventory.interact(ItemID.SKILLS_NECKLACE6, "rub");
                }
                sleep(700, 1100);
                Rs2Keyboard.keyPress('6');
                Rs2Player.waitForAnimation();
                sleepUntil(() -> !Rs2Player.isAnimating());
                return true;
            } else if (Rs2Equipment.hasEquipped(ItemID.FARMING_CAPE)) {
                Rs2Equipment.interact(ItemID.FARMING_CAPE, "teleport");
                return true;
            } else if (Rs2Equipment.hasEquipped(ItemID.FARMING_CAPET)) {
                Rs2Equipment.interact(ItemID.FARMING_CAPET, "teleport");
                return true;
            }
        }
        return false;
    }

    private void handleTeleportToGuild(HerbrunConfig config) {
        boolean hasTeleported = false;
        if (!hasTeleported) {
            System.out.println("Teleporting to the Farming guild...");
            guildTeleport(config);  // Perform guild teleport
            hasTeleported = true;
        }
        botStatus = states.GUILD_WALKING_TO_PATCH;
    }

    private boolean weissTeleport() {
        sleep(100);
        if (!Rs2Player.isAnimating()) {
            System.out.println("Teleporting to Weiss");
            boolean success = Rs2Inventory.interact(ItemID.ICY_BASALT, "Weiss");
            sleepUntil(() -> !Rs2Player.isAnimating());
            if (Rs2Player.distanceTo(weissHerb) < 10) {
                System.out.println("Arrived at Weiss teleport spot.");
                return success;
            }

        }
        return false;
    }

    private void handleTeleportToWeiss() {
        boolean hasTeleported = false;
        if (!hasTeleported) {
            System.out.println("Teleporting to Weiss...");
            weissTeleport();  // Perform WEISS teleport
            hasTeleported = true;
        }
        botStatus = states.WEISS_HANDLE_PATCH;
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


    private void handleHerbPatch(int patchId, int seedToPlant, HerbrunConfig config) {
        // Define possible actions the herb patch could have
        if (!Rs2Player.isMoving() &&
                !Rs2Player.isWalking() &&
                !Rs2Player.isAnimating() &&
                !Rs2Player.isInteracting()) {
            String[] possibleActions = {"pick", "rake", "Clear", "Inspect"};

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
                case "pick":
                    handlePickAction(herbPatch, patchId, config);
                    break;
                case "rake":
                    handleRakeAction(herbPatch);
                    break;
                case "Clear":
                    handleClearAction(herbPatch);
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
        if (Rs2Inventory.isFull()) {
            System.out.println("Noting herbs with tool leprechaun...");
            Rs2Inventory.useItemOnNpc(config.SEED().getHerbId(), leprechaun); // Note the herbs with tool leprechaun
            Rs2Player.waitForAnimation();
        }
        int timesToLoop = 2 + (int) (Math.random() * 6);

        Rs2GameObject.interact(herbPatch, "pick");
        Rs2Player.waitForXpDrop(Skill.FARMING);
        for (int i = 0; i < timesToLoop; i++) {
            Rs2GameObject.interact(herbPatch, "pick");
            sleep(25, 100);
        }
        Rs2Player.waitForAnimation();

        // Wait for the picking to complete (player stops animating and patch no longer has the "Pick" action)
        sleepUntil(() -> !Rs2GameObject.hasAction(Rs2GameObject.findObjectComposition(patchId), "Pick") ||
                (!Rs2Player.isAnimating() && !Rs2Player.isInteracting() && !Rs2Player.isMoving()));

        // After picking herbs, check if "rake" is an available action
        if (Rs2GameObject.hasAction(Rs2GameObject.findObjectComposition(patchId), "rake")) {
            System.out.println("Weeds grew, switching to rake action...");
            handleRakeAction(herbPatch);  // Handle raking if weeds grew
            return;  // Exit the method after raking
        }

        // If the inventory becomes full again while picking, note the herbs and pick the remaining ones
        if (Rs2GameObject.hasAction(Rs2GameObject.findObjectComposition(patchId), "Pick") && Rs2Inventory.isFull()) {
            System.out.println("Noting herbs with tool leprechaun...");
            Rs2Inventory.useItemOnNpc(config.SEED().getHerbId(), leprechaun);
            Rs2Player.waitForAnimation();

            // Pick any remaining herbs
            Rs2GameObject.interact(herbPatch, "pick");

            if (config.FAST_HERB()) {
                Rs2Player.waitForXpDrop(Skill.FARMING);
                timesToLoop = 2 + (int) (Math.random() * 6);
                for (int i = 0; i < timesToLoop; i++) {
                    Rs2GameObject.interact(herbPatch, "pick");
                    sleep(25, 100);
                }
            }

            Rs2Player.waitForAnimation();
            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
        }

        // Final check to ensure no lingering animations or interactions
        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving() && !Rs2Player.isInteracting(), 200);
    }


    private void handleRakeAction(GameObject herbPatch) {
        System.out.println("Raking the patch...");

        // Rake the patch
        Rs2GameObject.interact(herbPatch, "rake");

        Rs2Player.waitForAnimation();
        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());

        // Drop the weeds (assuming weeds are added to the inventory)
        if (!Rs2Player.isMoving() &&
                !Rs2Player.isAnimating() &&
                !Rs2Player.isInteracting() && !Rs2Player.isWalking()) {
            System.out.println("Dropping weeds...");
            Rs2Inventory.dropAll(ItemID.WEEDS);
            Rs2Player.waitForAnimation();
            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isInteracting());
        }
    }

    private void handleClearAction(GameObject herbPatch) {
        System.out.println("Clearing the herb patch...");

        // Try to interact with the patch using the "clear" action
        boolean interactionSuccess = Rs2GameObject.interact(herbPatch, "clear");
        Rs2Player.waitForAnimation();
        sleepUntil(() -> !Rs2Player.isAnimating());

        if (!interactionSuccess) {
            System.out.println("Failed to interact with the herb patch to clear it.");
            return;
        }

        // Wait for the clearing animation to finish
        Rs2Player.waitForAnimation();
        sleepUntil(() -> !Rs2Player.isAnimating() && Rs2Player.isInteracting() && Rs2Player.isWalking());
    }

    private void printHerbPatchActions(int patchId) {
        GameObject herbPatch = Rs2GameObject.findObjectByImposter(patchId, "clear");
        if (herbPatch == null) {
            System.out.println("Herb patch not found for ID: " + patchId);
            return;
        }

        ObjectComposition herbPatchComposition = Rs2GameObject.findObjectComposition(patchId);
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
                !Rs2Player.isInteracting() && !Rs2Player.isWalking()) {

            // Apply compost based on configuration
            System.out.println("Applying compost...");
            int compostItemId = config.COMPOST() ? ItemID.BOTTOMLESS_COMPOST_BUCKET_22997 : ItemID.ULTRACOMPOST;
            Rs2Inventory.use(compostItemId);
            Rs2GameObject.interact(patchId, "use");
            // Wait for farming XP drop to confirm compost application
            Rs2Player.waitForXpDrop(Skill.FARMING);
            sleep(50, 1200);

            // Plant seeds in the patch
            System.out.println("Planting seeds...");
            Rs2Inventory.use(seedToPlant);
            Rs2GameObject.interact(patchId, "use");

            // Wait until interaction is complete
            sleepUntil(Rs2Player::isInteracting);
            if (Rs2Inventory.contains(ItemID.EMPTY_BUCKET)) Rs2Inventory.drop(ItemID.EMPTY_BUCKET);

            // Update the bot status
            botStatus = state;
        }
    }

}