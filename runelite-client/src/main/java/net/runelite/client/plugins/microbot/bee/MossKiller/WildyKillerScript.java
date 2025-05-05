package net.runelite.client.plugins.microbot.bee.MossKiller;

import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.bee.MossKiller.Enums.CombatMode;
import net.runelite.client.plugins.microbot.bee.MossKiller.Enums.MossKillerState;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import javax.inject.Inject;
import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.api.EquipmentInventorySlot.AMMO;
import static net.runelite.api.EquipmentInventorySlot.WEAPON;
import static net.runelite.api.ItemID.*;
import static net.runelite.api.ObjectID.POOL_OF_REFRESHMENT;
import static net.runelite.api.Skill.*;
import static net.runelite.client.plugins.microbot.bee.MossKiller.Enums.CombatMode.LURE;
import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.LOW;
import static net.runelite.client.plugins.microbot.util.player.Rs2Player.*;
import static net.runelite.client.plugins.microbot.util.player.Rs2Pvp.getWildernessLevelFrom;
import static net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer.isPrayerActive;
import static net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer.toggle;
import static net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum.*;
import static net.runelite.client.plugins.skillcalculator.skills.MagicAction.HIGH_LEVEL_ALCHEMY;
import static net.runelite.client.plugins.skillcalculator.skills.MagicAction.WIND_BLAST;


public class WildyKillerScript extends Script {

    @Inject
    private Client client;

    @Inject
    ConfigManager configManager;

    @Inject
    private MossKillerPlugin mossKillerPlugin;

    @Inject
    private MossKillerConfig mossKillerConfig;

    @Inject
    private Rs2Pvp rs2Pvp;

    public static double version = 1.0;
    public static MossKillerConfig config;

    public boolean isStarted = false;
    public int playerCounter = 0;

    public final WorldPoint MOSS_GIANT_SPOT = new WorldPoint(3143, 3823, 0);
    public final WorldPoint VARROCK_SQUARE = new WorldPoint(3212, 3422, 0);
    public final WorldPoint VARROCK_WEST_BANK = new WorldPoint(3182, 3440, 0);
    public final WorldPoint TWENTY_WILD = new WorldPoint(3105, 3673, 0);
    public final WorldPoint ZERO_WILD = new WorldPoint(3106, 3523, 0);
    public final WorldPoint CASTLE_WARS = new WorldPoint(3142, 3473, 0);
    public final WorldPoint DWARFS = new WorldPoint(3210, 3797, 0);
    public static final WorldArea WEST_BARRIER_OUTSIDE = new WorldArea(3120, 3626, 3, 4, 0);
    public static final WorldArea NORTH_BARRIER_OUTSIDE = new WorldArea(3131, 3640, 6, 2, 0);
    public static final WorldArea MOSS_GIANT_AREA = new WorldArea(3122, 3752, 45, 91, 0);
    public static final WorldArea CORRIDOR = new WorldArea(3119, 3641, 25, 150, 0);
    public static final WorldArea TOTAL_FEROX_ENCLAVE = new WorldArea(3109, 3606, 56, 46, 0);
    public static final WorldArea FEROX_TELEPORT_AREA = new WorldArea(3147, 3631, 7, 7, 0);
    public static final WorldArea LUMBRIDGE_AREA = new WorldArea(3189, 3183, 63, 62, 0);
    public static final WorldArea CASTLE_WARS_AREA = new WorldArea(2433, 3076, 15, 25, 0);
    private static final WorldArea WILDERNESS_AREA = new WorldArea(2944, 3520, 448, 384, 0);
    public static final int COMBAT_TAB_WIDGET_ID = 35913791;  // Combat tab
    public static final int CHOOSE_SPELL_WIDGET_ID = 38862875; // Choose spell
    public static final int CHOOSE_SPELL_DEFENSIVE_WIDGET_ID = 38862870; // Choose spell
    public boolean hitsplatApplied = false;
    public boolean isTargetOutOfReach = false;


    // Items
    public final int AIR_RUNE = 556;
    public final int FIRE_RUNE = 554;
    public final int LAW_RUNE = 563;

    public int FOOD = 373;

    public int MOSSY_KEY = 22374;

    public int NATURE_RUNE = 561;
    public int DEATH_RUNE = 560;
    public int[] LOOT_LIST = new int[]{MOSSY_KEY, LAW_RUNE, AIR_RUNE, COSMIC_RUNE, STEEL_BAR, DEATH_RUNE, NATURE_RUNE, UNCUT_RUBY, UNCUT_DIAMOND, STEEL_KITESHIELD, MITHRIL_SWORD, BLACK_SQ_SHIELD};
    public int[] strengthPotionIds = {STRENGTH_POTION1, STRENGTH_POTION2, STRENGTH_POTION3, STRENGTH_POTION4}; // Replace ID1, ID2, etc., with the actual potion IDs.
    public int[] ALCHABLES = new int[]{STEEL_KITESHIELD, MITHRIL_SWORD, BLACK_SQ_SHIELD};


    boolean hasStrengthPotion = false;

    public MossKillerState state = MossKillerState.BANK;

    public void MossKillerScript(MossKillerConfig config) {
        WildyKillerScript.config = config;
    }

    public boolean run(MossKillerConfig config) {
        System.out.println("getting to run");
        WildyKillerScript.config = config;
        Microbot.enableAutoRunOn = false;
        Rs2Walker.disableTeleports = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.moveMouseOffScreenChance = 0.04;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.04;
        Rs2AntibanSettings.actionCooldownChance = 0.06;
        Rs2Antiban.setActivityIntensity(LOW);
        Rs2AntibanSettings.dynamicActivity = true;

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                Microbot.log("SoL " + state);
                Rs2AntibanSettings.antibanEnabled = mossKillerPlugin.currentTarget == null; // Enable Anti-Ban when no target is found

                if (isRunning() && BreakHandlerScript.breakIn <= 120 && Rs2Player.getWorldLocation().getY() < 3520) {
                    Microbot.log("On a break and not in wilderness");
                    if (isRunning()) {
                        sleep(10000);
                        if (isRunning()) {
                            sleepUntil(() -> !Rs2Player.isInCombat());
                        }
                        if (isRunning()) {
                            Rs2Player.logout();
                        }
                        sleep(120000);
                        return;
                    }
                    return;
                } else if (isRunning() && BreakHandlerScript.breakIn <= 120 && Rs2Player.getWorldLocation().getY() > 3520) {
                    Microbot.log("On a break and in wilderness");
                    if (isRunning()) {
                        toggleRunEnergyOn();
                        Rs2Bank.walkToBank();
                        sleep(60000);
                        if (isRunning()) {
                            Rs2Player.logout();
                        }
                        return;
                    }
                }


                if (mossKillerPlugin.shouldHopWorld()) {
                    Microbot.log("Player is dead! Hopping worlds...");
                    sleepUntil(() -> !Rs2Player.isInCombat());
                    sleep(4900);
                    performWorldHop();
                    sleep(2900);
                    mossKillerPlugin.resetWorldHopFlag(); // Reset after hopping
                }

                if (Rs2Player.getRealSkillLevel(DEFENCE) >= config.defenseLevel()) {
                    handleAsynchWalk("Twenty Wild");
                    sleepUntil(() -> (ShortestPathPlugin.pathfinder == null), 120000);
                    moarShutDown();
                }

                // CODE HERE
                switch (state) {
                    case BANK:
                        handleBanking();
                        break;
                    case TELEPORT:
                        varrockTeleport();
                        break;
                    case WALK_TO_BANK:
                        walkToVarrockWestBank();
                        break;
                    case CASTLE_WARS_TO_FEROX:
                        handleFerox();
                        break;
                    case WALK_TO_MOSS_GIANTS:
                        walkToMossGiants();
                        break;
                    case FIGHT_MOSS_GIANTS:
                        handleMossGiants();
                        break;
                    case PKER:
                        handlePker();
                        break;
                }
                if (mossKillerPlugin.getCurrentTarget() != null)
                    Microbot.log("Current target is " + mossKillerPlugin.getCurrentTarget().getName());
                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);


            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public void moarShutDown() {
        varrockTeleport();
        //static sleep to wait till out of combat
        sleep(10000);
        //turn off breakhandler
        MossKillerScript.stopBreakHandlerPlugin();
        //turn off autologin and all other scripts in 5 seconds
        Microbot.getClientThread().runOnSeperateThread(() -> {
            if (!Microbot.pauseAllScripts) {
                sleep(5000);
                Microbot.pauseAllScripts = true;
            }
            return null;
        });
        Rs2Player.logout();
        sleep(1000);
        shutdown();
    }

    public void handleAsynchWalk(String walkName) {
        scheduledFuture = scheduledExecutorService.schedule(() -> {
            try {
                Microbot.log("Entered Asynch Walking Thread");
                WorldPoint playerLocation = client.getLocalPlayer().getWorldLocation();

                if (playerLocation.getY() > 3520) {
                    // Check and reset the appropriate flag
                    switch (walkName) {
                        case "Twenty Wild":
                            Rs2Walker.walkTo(TWENTY_WILD);
                            if (mossKillerPlugin.playerJammed()) {
                                Microbot.log("restarting path");
                                Rs2Walker.setTarget(null);
                                Rs2Walker.walkTo(TWENTY_WILD);
                            }
                            // If the player has reached the target location (TWENTY_WILD)
                            if (Rs2Walker.getDistanceBetween(Rs2Player.getWorldLocation(), TWENTY_WILD) <= 5) {
                                Microbot.log("Reached TWENTY_WILD.");
                                sleep(1000);
                                if (isTeleBlocked()) {
                                    if(!Rs2Player.isInCombat()) {Rs2Player.logout();}
                                    handleAsynchWalk("Zero Wild");
                                }
                                teleportAndStopWalking();
                            }
                            break;
                        case "Zero Wild":
                            if (isTeleBlocked()) {
                                if(!Rs2Player.isInCombat()) {Rs2Player.logout();}
                                Rs2Walker.walkTo(ZERO_WILD);
                                if (mossKillerPlugin.playerJammed()) {
                                    Microbot.log("restarting path");
                                    Rs2Walker.setTarget(null);
                                    Rs2Walker.walkTo(ZERO_WILD);
                                }
                            } else {
                                handleAsynchWalk("Twenty Wild");
                            }
                            // If the player has reached the target location (TWENTY_WILD)
                            if (Rs2Walker.getDistanceBetween(Rs2Player.getWorldLocation(), ZERO_WILD) <= 5) {
                                Microbot.log("Reached ZERO_WILD.");
                                teleportAndStopWalking();
                            }// Perform walk
                            break;
                        case "Moss Giants":
                            if (!Rs2Player.isInMulti() && mossKillerPlugin.currentTarget == null) {
                                Rs2Walker.walkTo(MOSS_GIANT_SPOT);
                                if (mossKillerPlugin.playerJammed()) {
                                    Microbot.log("restarting path");
                                    Rs2Walker.setTarget(null);
                                    Rs2Walker.walkTo(MOSS_GIANT_SPOT);
                                }
                            } else if (Rs2Player.isInMulti() && mossKillerPlugin.currentTarget != null) {
                                sleepUntil(() -> Rs2Walker.walkWithState(MOSS_GIANT_SPOT) == WalkerState.ARRIVED, 12500);
                            }
                            //make sure this thread is still active if we're trying to deal with someone luring us or going in and out of multi

                            break;
                        case "Dwarfs":
                            Rs2Walker.walkTo(DWARFS); // Perform walk
                            break;
                        case "Start-up":
                            System.out.println("starting up the scheduled future");
                            break;
                    }
                }
                Microbot.log("Exiting Asynch Walking Thread");
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 600, TimeUnit.MILLISECONDS);
    }


    public void performWorldHop() {
        int maxRetries = 5; // Maximum number of attempts to hop worlds
        int retries = 0;
        boolean hopSuccessful = false;

        while (retries < maxRetries && !hopSuccessful) {
            int world;
            do {
                world = Login.getRandomWorld(false, null); // Get a random world
            } while (world == 301 || world == 308 || world == 316); // Skip restricted worlds

            int targetWorld = world;

            // Attempt to hop to the target world
            Microbot.hopToWorld(world);

            // Wait to verify the world hop succeeded
            hopSuccessful = sleepUntil(() -> Rs2Player.getWorld() == targetWorld, 10000);

            if (!hopSuccessful) {
                retries++;
                System.out.println("World hop failed, retrying... (" + retries + "/" + maxRetries + ")");
            }
        }

        if (!hopSuccessful) {
            System.out.println("Failed to hop worlds after " + maxRetries + " attempts.");
        } else {
            System.out.println("World hop successful to world: " + Rs2Player.getWorld());
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private int getRandomZoom(int min, int max) {
        return (int) (Math.random() * (max - min + 1)) + min;
    }

    public void handlePker() {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        int currentZoom = Rs2Camera.getZoom(); // Assume Rs2Camera.getZoom() retrieves the current zoom level.

        if (mossKillerPlugin.currentTarget != null) {
            // Check if the zoom is outside the 340-400 range
            if (currentZoom < 340 || currentZoom > 400) {
                int zoomValue = getRandomZoom(340, 400);
                Rs2Camera.setZoom(zoomValue);
            }
        } else {
            // Check if the zoom is outside the 230-290 range
            if (currentZoom < 230 || currentZoom > 290) {
                int zoomValue = getRandomZoom(230, 290);
                Rs2Camera.setZoom(zoomValue);
            }
        }

        int currentPitch = Rs2Camera.getPitch(); // Assume Rs2Camera.getPitch() retrieves the current pitch value.

        // Ensure the pitch is within the desired range
        if (currentPitch < 150 || currentPitch > 200) {
            int pitchValue = Rs2Random.between(150, 200); // Random value within the range
            Rs2Camera.setPitch(pitchValue); // Adjust the pitch
        }


        if (mossKillerPlugin.currentTarget == null) Rs2Player.eatAt(70);

        if (mossKillerPlugin.currentTarget == null) {
            // Get the actor we're interacting with
            Actor interactingActor = Rs2Player.getInteracting();

            // Check if it's a player (not an NPC)
            if (interactingActor != null && interactingActor instanceof Player) {
                // If it's a player, we need to break the interaction
                WorldPoint myTile = getWorldLocation();
                Rs2Walker.walkFastCanvas(myTile);
                sleep(600);
            }
        }


        if (mossKillerPlugin.currentTarget == null && MOSS_GIANT_AREA.contains(playerLocation)) {
            if (!Rs2Inventory.contains(MOSSY_KEY)) {
                state = MossKillerState.FIGHT_MOSS_GIANTS;
            }
        }

        if (mossKillerPlugin.currentTarget == null && playerLocation.getY() < 3520) {
            Microbot.log("not in wilderness and no target, teleport reset");
            state = MossKillerState.TELEPORT;
        }

        if (mossKillerPlugin.currentTarget != null
                && mossKillerPlugin.currentTarget.getCombatLevel() < 88
                && getWildernessLevelFrom(Rs2Player.getWorldLocation()) < 25) {
            if (Rs2Pvp.getWildernessLevelFrom(mossKillerPlugin.currentTarget.getWorldLocation()) == 0) {
                handleAsynchWalk("Twenty Wild");
                eatAt(70);
                sleep(1800);
                eatAt(70);
                sleep(1800);
                eatAt(70);
                sleep(1800);
                eatAt(70);
                sleep(1800);
            }

        }

        if (mossKillerPlugin.currentTarget != null
                && mossKillerPlugin.currentTarget.getCombatLevel() > 87
                && getWildernessLevelFrom(Rs2Player.getWorldLocation()) > 20) {
            toggle(STEEL_SKIN, true);
            toggle(MYSTIC_LORE, true);
            if (ShortestPathPlugin.getPathfinder() == null && !MossKillerPlugin.isPlayerSnared()) {
                handleAsynchWalk("Twenty Wild");
            }
        }

        if (mossKillerConfig.combatMode() != LURE) {
            if (Rs2Player.isInMulti()
                    && scheduledFuture.isDone()
                    && ShortestPathPlugin.getPathfinder() == null) {

                handleAsynchWalk("Moss Giants");
            }

        } else if (Rs2Player.isInMulti() && scheduledFuture.isDone() && mossKillerConfig.combatMode() == LURE) {
            handleAsynchWalk("Dwarfs");
        }

        if (mossKillerPlugin.currentTarget == null && Rs2Inventory.contains(MIND_RUNE) && TOTAL_FEROX_ENCLAVE.contains(playerLocation)) {
            if (!Rs2Equipment.isWearing(STAFF_OF_FIRE) && Rs2Inventory.hasItem(STAFF_OF_FIRE)) {
                Rs2Inventory.equip(STAFF_OF_FIRE);
            }

        }

        // Ensure mossKillerConfig is not null
        if (mossKillerConfig == null) {
            Microbot.log("Configuration is not initialized!, returning");
            return;
        }

        CombatMode mode = mossKillerConfig.combatMode();

        switch (mode) {
            case FLEE:
                state = MossKillerState.TELEPORT;
                break;
            case FIGHT:
                fight();
                break;
            case LURE:
                lure();
                break;
        }


        //if config is flee do this
        //if frozen, cast snare
        //teleport when arrived at 20 wild
        //(if tbed, continue running south to 0 wild (how to check if tbed?))

        //else if config is lure do this
        //run to multi or world position X (inputted by user)
        //send discord information either way(s)

    }

    private void lure() {
        if (!Rs2Equipment.hasEquipped(MAPLE_SHORTBOW) && Rs2Equipment.hasEquipped(ADAMANT_ARROW)) {
            Rs2Inventory.interact(MAPLE_SHORTBOW, "Wield");
        }
        Rs2Player.eatAt(70);
        //if snared, attack (include long-range logic
        //if not snared attack every 6 ticks
        //equip bow for highest dps at range
        //eating calculation to know when to attack them for a hit
        //Rs2Walker.walkTo("dwarfs asynch");
        //if near dwarfs ???
        //use long range weapons and stay in the dwarf area (dont be lured out)
    }

    private void fight() {

        if(Rs2Player.getBoostedSkillLevel(HITPOINTS) == 0) {
            Rs2Keyboard.typeString("gg");
            Rs2Keyboard.enter();
            sleep(1200,1800);
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        Microbot.log("FIGHT CASE");

        Rs2PlayerModel target = mossKillerPlugin.currentTarget;

        if (CORRIDOR.contains(playerLocation) && target != null
                && target.getCombatLevel() < 88
                && Rs2Inventory.hasItemAmount(MIND_RUNE, 750)) {
            Rs2Walker.setTarget(null);
            scheduledFuture.cancel(true);
        }

        eatingMethod(target);

        //if we have a target and we're autocasting
        if (target != null
                && mossKillerPlugin.getAttackStyle()
                && target.getCombatLevel() < 88) {
            if (weHaveEnoughEnergyToPersue() && !MossKillerPlugin.isPlayerSnared() || !isTargetPlayerFar(target)) {
                Rs2Inventory.interact(RUNE_SCIMITAR, "Wield");
            } else if (isTargetPlayerFarCasting(target)) {
                if (!castWindBlast(target)) {
                    if (Rs2Equipment.hasEquippedSlot(AMMO)
                            || Rs2Equipment.isEquipped(ADAMANT_ARROW, AMMO)) {
                        if (Rs2Inventory.contains(MAPLE_LONGBOW)) {
                            Rs2Inventory.interact(MAPLE_LONGBOW, "Wield");
                            setCombatStyle(target);
                        }
                    }
                }
            } else if (Rs2Inventory.contains(MAPLE_SHORTBOW)) {
                Rs2Inventory.interact(MAPLE_SHORTBOW, "Wield");
                setCombatStyle(target);

            }
        }


        if (target != null && Rs2Player.getRunEnergy() < 20) {
            Rs2Inventory.useRestoreEnergyItem();
        }

        if (target != null && scheduledFuture.isDone() && isStrengthPotionPresent() && target.getCombatLevel() < 88) {
            checkAndDrinkStrengthPotion();
        }

        if (target != null) {
            basicAttackSetup();
        }

        if (mossKillerPlugin.hasRuneScimitar()
                && target != null
                && target.getCombatLevel() < 88) {
            if (MossKillerPlugin.isPlayerSnared() && isTargetPlayerFar(target) || mossKillerPlugin.lobsterEaten()) {
                ultimateStrengthOff();
            } else {
                ultimateStrengthOn();
            }
        } else if (Rs2Prayer.isPrayerActive(ULTIMATE_STRENGTH)) {
            ultimateStrengthOff();
        }

        if (target != null && !mossKillerPlugin.lobsterEaten() && ShortestPathPlugin.getPathfinder() == null
                && target.getCombatLevel() < 88 && Rs2Player.getInteracting() != target) {
            basicAttackSetup();
            isTargetOnSameTile(target);
            Rs2Walker.setTarget(null);
            scheduledFuture.cancel(true);
            if(!Rs2Player.isInteracting()) {attack(target);}
            sleepUntil(() -> hitsplatApplied || MossKillerPlugin.isPlayerSnared() || healthIsLow());
            eatingMethod(target);
        }

        //if no attack delay from eating and not interacting with target, attack target
        if (target != null && target.getCombatLevel() < 88) {
            if (!mossKillerPlugin.lobsterEaten()
                    && Rs2Player.getInteracting() != target
                    && getPlayersInCombatLevelRange().stream()
                    .anyMatch(p -> p.getId() == target.getId())
                    && !TOTAL_FEROX_ENCLAVE.contains(playerLocation)) {
                if (!Rs2Player.isInMulti() && !isNpcInteractingWithMe()) {
                    if (ShortestPathPlugin.getPathfinder() == null)
                        if (doWeFocusCamera(target)) {
                            sleep(300);
                        }
                    basicAttackSetup();
                    Rs2Walker.setTarget(null);
                    scheduledFuture.cancel(true);
                    if(!Rs2Player.isInteracting()) {attack(target);}
                    sleepUntil(() -> hitsplatApplied || MossKillerPlugin.isPlayerSnared() || healthIsLow());
                    eatingMethod(target);

                }
            }
        }

        if (!Rs2Inventory.contains(FOOD) && target != null && Microbot.getClient().getBoostedSkillLevel(PRAYER) >= 1) {
            toggle(Rs2PrayerEnum.PROTECT_ITEM, true);
        }

        //if you're running away from target, and you're snared or without food, fight back a bit you coward
        if (target != null && ShortestPathPlugin.getPathfinder() != null && target.getCombatLevel() > 87) {
            if (MossKillerPlugin.isPlayerSnared() || !Rs2Inventory.contains(FOOD)) {

                if (!isTargetPlayerFar(target) && mossKillerPlugin.lobsterEaten()) {
                    Rs2Inventory.interact(RUNE_SCIMITAR, "Wield");
                    Rs2Walker.setTarget(null);
                    scheduledFuture.cancel(true);
                    if(!Rs2Player.isInteracting()) {attack(target);}
                    sleepUntil(() -> hitsplatApplied || MossKillerPlugin.isPlayerSnared());
                    eatingMethod(target);
                } else if (!castWindBlast(target)) {
                    if (Rs2Equipment.hasEquippedSlot(AMMO)
                            || Rs2Equipment.isEquipped(ADAMANT_ARROW, AMMO)) {
                        if (Rs2Inventory.contains(MAPLE_SHORTBOW) && mossKillerPlugin.lobsterEaten()) {
                            Rs2Inventory.interact(MAPLE_SHORTBOW, "Wield");
                            setCombatStyle(target);
                            Rs2Walker.setTarget(null);
                            scheduledFuture.cancel(true);
                            if(!Rs2Player.isInteracting()) {attack(target);}
                            sleepUntil(() -> hitsplatApplied || MossKillerPlugin.isPlayerSnared() || healthIsLow());
                            eatingMethod(target);
                        }
                    }
                }

            }
        }

        if (target != null
                && getWildernessLevelFrom(Rs2Player.getWorldLocation()) < 25
                && ShortestPathPlugin.getPathfinder() == null
                && !MossKillerPlugin.isPlayerSnared()
                && target.getCombatLevel() > 87) {
            Microbot.log("less than 25 wild");
            if (!Rs2Player.isTeleBlocked()) {
                handleAsynchWalk("Twenty Wild");
            } else {
                handleAsynchWalk("Zero Wild");
            }
        }

        if (target != null && target.getCombatLevel() > 87
                && getWildernessLevelFrom(Rs2Player.getWorldLocation()) > 20) {
            Microbot.log("Target is over level 87");
            eatingMethod(target);
            if (ShortestPathPlugin.getPathfinder() == null && !MossKillerPlugin.isPlayerSnared()) {
                handleAsynchWalk("Twenty Wild");
            }
            state = MossKillerState.TELEPORT;
        }

        if (target == null && ShortestPathPlugin.getPathfinder() == null && mossKillerPlugin.isSuperNullTarget()) {
            state = MossKillerState.TELEPORT;
        }

        if (target == null &&
                ShortestPathPlugin.getPathfinder() != null
                && mossKillerPlugin.isSuperNullTarget() &&
                mossKillerPlugin.playerJammed()) {
            Rs2Walker.setTarget(null);
            scheduledFuture.cancel(true);
            state = MossKillerState.TELEPORT;
        }

        if (target != null && !Rs2Player.isTeleBlocked() && playerLocation.getY() < 3675) {
            if (Rs2Inventory.contains(STAFF_OF_FIRE)) {
                Rs2Inventory.interact(STAFF_OF_FIRE, "Wield");
                if (Rs2Magic.canCast(MagicAction.VARROCK_TELEPORT)) {
                    Rs2Magic.cast(MagicAction.VARROCK_TELEPORT);
                    sleep(1200);
                }
            }
        }

    }

    public void basicAttackSetup() {

        Microbot.log("Entering basicAttackSetup");

        Player localPlayer = Microbot.getClient().getLocalPlayer();
        Rs2PlayerModel target = mossKillerPlugin.currentTarget;

        boolean useMage = mossKillerPlugin.useWindBlast();
        boolean useMelee = mossKillerPlugin.useMelee();
        boolean useRange = mossKillerPlugin.useRange();


        if (target != null
                && target.getCombatLevel() < 88
                && target.getOverheadIcon() == null
                && !MossKillerPlugin.isPlayerSnared()) {

            if (!isInMulti()) {
                if (Rs2Prayer.isPrayerActive(PROTECT_MAGIC) || Rs2Prayer.isPrayerActive(PROTECT_MELEE)) {
                Rs2Prayer.toggle(PROTECT_MAGIC, false);
                Rs2Prayer.toggle(PROTECT_RANGE, false);
            }}

            if (hasPlayerEquippedItem(target, MAPLE_SHORTBOW)) {
                if (Rs2Player.getRealSkillLevel(PRAYER) > 39 && Rs2Player.getBoostedSkillLevel(PRAYER) > 0) {
                    toggle(PROTECT_RANGE, true);
                }
            } else if (Rs2Prayer.isPrayerActive(PROTECT_RANGE)) {
                toggle(PROTECT_RANGE, false);
            }

            if (hasPlayerEquippedItem(target, RUNE_PLATEBODY) && Rs2Inventory.contains(DEATH_RUNE)) {
                castWindBlast(target);
                sleep(600);
            }

            if (weHaveEnoughEnergyToPersue()
                    || !isTargetPlayerFar(target)) {
                if (Rs2Inventory.contains(RUNE_SCIMITAR)) {
                    Rs2Inventory.interact(RUNE_SCIMITAR, "Wield");
                }
            } else if (!castWindBlast(target) && !isTargetPlayerFarCasting(target)) {
                if (Rs2Equipment.hasEquippedSlot(AMMO)
                        || Rs2Equipment.isEquipped(ADAMANT_ARROW, AMMO)) {
                    if (Rs2Inventory.contains(MAPLE_SHORTBOW)) {
                        Rs2Inventory.interact(MAPLE_SHORTBOW, "Wield");
                        setCombatStyle(target);
                    }
                }
            } else if (isTargetPlayerFarCasting(target) && !Rs2Inventory.contains(DEATH_RUNE)) {
                if (Rs2Inventory.contains(MAPLE_LONGBOW)) {
                    Rs2Inventory.interact(MAPLE_LONGBOW, "Wield");
                    setCombatStyle(target);
                }
            }

        }

        if (target != null
                && target.getCombatLevel() < 88
                && target.getOverheadIcon() == null
                && MossKillerPlugin.isPlayerSnared()) {

            if (!isInMulti()) {
                if (Rs2Prayer.isPrayerActive(PROTECT_RANGE) || Rs2Prayer.isPrayerActive(PROTECT_MAGIC) || Rs2Prayer.isPrayerActive(PROTECT_MELEE)) {
                    if (Rs2Prayer.isPrayerActive(PROTECT_RANGE)) {Rs2Prayer.toggle(PROTECT_RANGE, false);}
                    if (Rs2Prayer.isPrayerActive(PROTECT_MAGIC)) {Rs2Prayer.toggle(PROTECT_MAGIC, false);}
                    if (Rs2Prayer.isPrayerActive(PROTECT_MELEE)) {Rs2Prayer.toggle(PROTECT_MELEE, false);}
            }} if (isInMulti()) {
                monitorAttacks();
            }

            if (!isTargetPlayerFar(target)) {
                if (Rs2Inventory.contains(RUNE_SCIMITAR)) {
                    Rs2Inventory.interact(RUNE_SCIMITAR, "Wield");
                }
            } else if (!castWindBlast(target) && !isTargetPlayerFarCasting(target)) {
                if (Rs2Equipment.hasEquippedSlot(AMMO)
                        || Rs2Equipment.isEquipped(ADAMANT_ARROW, AMMO)) {
                    if (Rs2Inventory.contains(MAPLE_SHORTBOW)) {
                        Rs2Inventory.interact(MAPLE_SHORTBOW, "Wield");
                        setCombatStyle(target);
                    }
                }
            } else if (isTargetPlayerFarCasting(target) && !Rs2Inventory.contains(DEATH_RUNE)) {
                if (Rs2Inventory.contains(MAPLE_LONGBOW)) {
                    Rs2Inventory.interact(MAPLE_LONGBOW, "Wield");
                    setCombatStyle(target);
                }
            }

        }

        if (target != null && target.getOverheadIcon() != null && target.getCombatLevel() < 88) {

            //if player is a mage and praying, protect from mage
            if (Rs2Player.getRealSkillLevel(PRAYER) > 36) {
                Rs2Prayer.toggle(PROTECT_MAGIC, hasPlayerEquippedItem(target, STAFF_OF_FIRE)
                        || hasPlayerEquippedItem(target, STAFF_OF_AIR)
                        || hasPlayerEquippedItem(target, STAFF_OF_WATER)
                        || hasPlayerEquippedItem(target, STAFF_OF_EARTH)
                        || hasPlayerEquippedItem(target, BRYOPHYTAS_STAFF)
                        || hasPlayerEquippedItem(target, BRYOPHYTAS_STAFF_UNCHARGED));
            }

            //prot range if they have range gear
            if (Rs2Player.getRealSkillLevel(PRAYER) > 39) {
                Rs2Prayer.toggle(PROTECT_RANGE, hasPlayerEquippedItem(target, MAPLE_SHORTBOW));
            }


            if (useMelee && weHaveEnoughEnergyToPersue()) {
                if (!Rs2Equipment.hasEquipped(RUNE_SCIMITAR)) {
                    Rs2Inventory.interact(RUNE_SCIMITAR, "Wield");
                }
                if (localPlayer.getInteracting() != target &&
                        getPlayersInCombatLevelRange().contains(target) &&
                        !mossKillerPlugin.lobsterEaten()) {
                    if (ShortestPathPlugin.getPathfinder() == null && Rs2Player.getInteracting() != target) {
                        Rs2Walker.setTarget(null);
                        scheduledFuture.cancel(true);
                        if(!Rs2Player.isInteracting()) {attack(target);}
                        sleepUntil(() -> hitsplatApplied || MossKillerPlugin.isPlayerSnared() || healthIsLow());
                        eatingMethod(target);
                    }
                }
            } else if (useMelee && !weHaveEnoughEnergyToPersue() && isTargetPlayerFar(target)) {
                if (Rs2Prayer.isPrayerActive(ULTIMATE_STRENGTH)) {
                    ultimateStrengthOff();
                }

                if (doWeFocusCamera(target)) {
                    sleep(300);
                }

                if (!castWindBlastOverhead(target)) {
                    if (isTargetPlayerFar(target)) {
                        if (Rs2Equipment.hasEquippedSlot(AMMO)
                                || Rs2Equipment.isEquipped(ADAMANT_ARROW, AMMO)) {
                            if (!isTargetPlayerFarCasting(target)) {
                                if (Rs2Inventory.contains(MAPLE_SHORTBOW)) {
                                    Rs2Inventory.interact(MAPLE_SHORTBOW, "Wield");
                                    eatingMethod(target);
                                    setCombatStyle(target);
                                    eatingMethod(target);
                                }
                            } else if (isTargetPlayerFarCasting(target)) {
                                if (Rs2Inventory.contains(MAPLE_LONGBOW)) {
                                    Rs2Inventory.interact(MAPLE_LONGBOW, "Wield");
                                    eatingMethod(target);
                                    setCombatStyle(target);
                                    eatingMethod(target);

                                }
                            }

                        }
                    }
                }
            }

            if (!castWindBlastOverhead(target) && useRange && Rs2Equipment.hasEquipped(ADAMANT_ARROW)) {
                if (!Rs2Equipment.hasEquipped(MAPLE_SHORTBOW) && Rs2Equipment.hasEquipped(ADAMANT_ARROW)) {
                    Rs2Inventory.interact(MAPLE_SHORTBOW, "Wield");
                    eatingMethod(target);
                    if (scheduledFuture.isDone()) setCombatStyle(target);
                    eatingMethod(target);
                }

                if (localPlayer.getInteracting() != target &&
                        getPlayersInCombatLevelRange().contains(target) &&
                        !mossKillerPlugin.lobsterEaten()) {
                    if (ShortestPathPlugin.getPathfinder() == null && Rs2Player.getInteracting() != target)
                        if (doWeFocusCamera(target)) {
                            sleep(300);
                        }
                    Rs2Walker.setTarget(null);
                    scheduledFuture.cancel(true);
                    if(!Rs2Player.isInteracting()) {attack(target);}
                    sleepUntil(() -> hitsplatApplied || MossKillerPlugin.isPlayerSnared() || healthIsLow());
                    eatingMethod(target);
                }
            }

            if (useMage && MossKillerPlugin.isPlayerSnared()) {
                if (doWeFocusCamera(target)) {
                    sleep(300);
                }
                //snared and they're praying range
                if (!castWindBlastOverhead(target)) {
                    if (isTargetPlayerFar(target)) {
                        if (Rs2Equipment.hasEquippedSlot(AMMO)
                                || Rs2Equipment.isEquipped(ADAMANT_ARROW, AMMO)) {
                            if (!isTargetPlayerFarCasting(target)) {
                                if (Rs2Inventory.contains(MAPLE_SHORTBOW)) {
                                    Rs2Inventory.interact(MAPLE_SHORTBOW, "Wield");
                                    eatingMethod(target);
                                    setCombatStyle(target);
                                    eatingMethod(target);
                                }
                            } else if (isTargetPlayerFarCasting(target)) {
                                if (Rs2Inventory.contains(MAPLE_LONGBOW)) {
                                    Rs2Inventory.interact(MAPLE_LONGBOW, "Wield");
                                    eatingMethod(target);
                                    setCombatStyle(target);
                                    eatingMethod(target);

                                }
                            }

                        } else if (Rs2Inventory.contains(RUNE_SCIMITAR)) {
                            Rs2Equipment.interact(RUNE_SCIMITAR, "Wield");
                            eatingMethod(target);
                            if (!Rs2Prayer.isPrayerActive(ULTIMATE_STRENGTH) && Microbot.getClient().getBoostedSkillLevel(PRAYER) >= 1) {
                                ultimateStrengthOn();
                            }
                        }
                    } else if (Rs2Inventory.contains(RUNE_SCIMITAR)) {
                        Rs2Equipment.interact(RUNE_SCIMITAR, "Wield");
                        eatingMethod(target);
                    }
                }

            } else if (useMage) {

                if (!castWindBlastOverhead(target)) {
                    if (isTargetPlayerFar(target)
                            && !weHaveEnoughEnergyToPersue()
                            && !isTargetPlayerFarCasting(target)) {
                        if (Rs2Equipment.hasEquippedSlot(AMMO)
                                || Rs2Equipment.isEquipped(ADAMANT_ARROW, AMMO)) {
                            if (Rs2Inventory.contains(MAPLE_SHORTBOW)) {
                                Rs2Inventory.interact(MAPLE_SHORTBOW, "Wield");
                                eatingMethod(target);
                                setCombatStyle(target);
                                eatingMethod(target);
                            }

                        } else if (Rs2Inventory.contains(RUNE_SCIMITAR)) {
                            Rs2Equipment.interact(RUNE_SCIMITAR, "Wield");
                            eatingMethod(target);
                        }
                    } else if (isTargetPlayerFarCasting(target)) {
                        if (Rs2Inventory.contains(MAPLE_LONGBOW)) {
                            Rs2Inventory.interact(MAPLE_LONGBOW, "Wield");
                            setCombatStyle(target);
                            eatingMethod(target);
                        }

                    }
                }

            }

            if (MossKillerPlugin.isPlayerSnared() && isTargetPlayerFar(target) && target.getCombatLevel() < 88) {
                Microbot.log("Target is Far and You are Snared");
                if (isTargetPlayerFarCasting(target)) {
                    if (!castWindBlast(target)) {
                        if (Rs2Inventory.contains(MAPLE_LONGBOW) && Rs2Equipment.hasEquippedSlot(AMMO)) {
                            Rs2Inventory.interact(MAPLE_LONGBOW, "Wield");
                            setCombatStyle(target);
                            eatingMethod(target);
                        }
                    }
                } else if (!useMage) {
                    if (Rs2Equipment.hasEquippedSlot(AMMO)) {
                        if (Rs2Inventory.contains(MAPLE_SHORTBOW)) {
                            Rs2Inventory.interact(MAPLE_SHORTBOW, "Wield");
                            eatingMethod(target);
                            setCombatStyle(target);
                            eatingMethod(target);
                        }

                    } else {
                        equipBestAvailableStaff();
                    }

                }
            }

            Microbot.log("Leaving basicAttackSetup");
        }
    }

    public boolean healthIsLow() {
        if (Rs2Player.getHealthPercentage() <= 50.0 && Rs2Inventory.contains(FOOD)) {
            return true;
        } else if (Rs2Player.getHealthPercentage() <= 50.0 && !Rs2Inventory.contains(FOOD)) {
            return false;
        }
        return false;
    }

    public List<Rs2PlayerModel> getAttackers(List<Rs2PlayerModel> potentialTargets) {
        List<Rs2PlayerModel> attackers = new ArrayList<>();
        Rs2PlayerModel localPlayer = (Rs2PlayerModel) Microbot.getClient().getLocalPlayer();

        if (localPlayer != null) {
            for (Rs2PlayerModel player : potentialTargets) {
                if (player != null && player.getInteracting() == localPlayer) {
                    attackers.add(player);
                }
            }
        }

        return attackers;
    }


    public void monitorAttacks() {
        List<Rs2PlayerModel> potentialTargets = getPotentialTargets();  // Existing method to get potential targets
        List<Rs2PlayerModel> attackers = getAttackers(potentialTargets);  // Determine which targets are attacking the bot
        int attackingPlayers = attackers.size();

        System.out.println("Number of players attacking: " + attackingPlayers);

        if (attackingPlayers >= 3) {
            System.out.println("Under attack by multiple players, checking and eating.");
            if (Microbot.getClient().getRealSkillLevel(Skill.PRAYER) >= 43) {
                handleProtectionPrayers(attackers);}} // introduce spam eating here
           if (attackingPlayers == 2) {
            System.out.println("Under attack by exactly 2 players, eating at 90 HP.");
            handleProtectionPrayers(attackers);
            Rs2Player.eatAt(90);
        }
    }


    public String getCombatStyle(Rs2PlayerModel player) {
        int[] equipmentIds = player.getPlayerComposition().getEquipmentIds();

        if (equipmentIds == null) {
            return "UNKNOWN";
        }

        int MAPLE_SHORTBOW_ID = 2901;
        int MAPLE_LONGBOW_ID = 2899;
        int RUNE_SCIMITAR_ID = 3381;
        int GILDED_SCIMITAR_ID = 14437;
        int STAFF_OF_FIRE_ID = 3435;
        int STAFF_OF_WATER_ID = 3431;
        int STAFF_OF_EARTH_ID = 3433;
        int STAFF_OF_AIR_ID = 3429;

        // Check the weapon slot (usually index 3 in equipment array)
        int weaponId = equipmentIds[3];

        // Check for Ranged weapon (Maple Shortbow)
        if (weaponId == MAPLE_SHORTBOW_ID || weaponId == MAPLE_LONGBOW_ID ) {
            return "RANGE";
        }

        // Check for Melee weapons (Rune Scimitar, etc.)
        if (weaponId == RUNE_SCIMITAR_ID || weaponId == GILDED_SCIMITAR_ID) {
            return "MELEE";
        }

        // Check for Magic weapons (Staff of Fire, etc.)
        if (weaponId == STAFF_OF_FIRE_ID || weaponId == STAFF_OF_WATER_ID || weaponId == STAFF_OF_EARTH_ID || weaponId == STAFF_OF_AIR_ID) {
            return "MAGIC";
        }

        return "UNKNOWN";
    }

    private void handleProtectionPrayers(List<Rs2PlayerModel> attackers) {
        int meleeAttackers = 0;
        int rangedAttackers = 0;
        int magicAttackers = 0;

        int meleeCombatLevelSum = 0;
        int rangedCombatLevelSum = 0;
        int magicCombatLevelSum = 0;

        for (Rs2PlayerModel attacker : attackers) {
            String style = getCombatStyle(attacker);  // Now returns a String ("MELEE", "RANGE", "MAGIC")
            int combatLevel = attacker.getCombatLevel();

            switch (style) {
                case "MELEE":
                    meleeAttackers++;
                    meleeCombatLevelSum += combatLevel;
                    break;

                case "RANGE":
                    rangedAttackers++;
                    rangedCombatLevelSum += combatLevel;
                    break;

                case "MAGIC":
                    magicAttackers++;
                    magicCombatLevelSum += combatLevel;
                    break;

                default:
                    // Handle unknown combat style if necessary
                    break;
            }
        }

        // Determine the bot's own combat style and choose the correct prayer based on its weaknesses
        Rs2PrayerEnum optimalPrayer = null;
        switch (config.combatMode()) {
            case MAIN_MELEE:
                optimalPrayer = determineOptimalPrayerForMelee(meleeAttackers, rangedAttackers, magicAttackers,
                        meleeCombatLevelSum, rangedCombatLevelSum, magicCombatLevelSum);
                break;

            case MAIN_RANGE:
                optimalPrayer = determineOptimalPrayerForRange(meleeAttackers, rangedAttackers, magicAttackers,
                        meleeCombatLevelSum, rangedCombatLevelSum, magicCombatLevelSum);
                break;

            case MAIN_MAGE:
                optimalPrayer = determineOptimalPrayerForMage(meleeAttackers, rangedAttackers, magicAttackers,
                        meleeCombatLevelSum, rangedCombatLevelSum, magicCombatLevelSum);
                break;

            default:
                break;
        }

        // Only toggle prayer if the optimal prayer is different from the currently active one
        if (optimalPrayer != null && !isPrayerActive(optimalPrayer)) {
            toggle(optimalPrayer, true);  // Activate the optimal prayer
            System.out.println("Activated " + optimalPrayer.name());
        }
    }

    // Determine the optimal prayer for a Melee-based bot
    private Rs2PrayerEnum determineOptimalPrayerForMelee(int meleeAttackers, int rangedAttackers, int magicAttackers,
                                                         int meleeCombatLevelSum, int rangedCombatLevelSum, int magicCombatLevelSum) {
        // Melee is weak to Magic, so prioritize Protect from Magic
        if (magicAttackers >= 3 || (magicCombatLevelSum >= meleeCombatLevelSum && magicCombatLevelSum >= rangedCombatLevelSum)) {
            return Rs2PrayerEnum.PROTECT_MAGIC;
        }
        // If Magic is not a significant threat, fallback to Protect from Melee or Ranged based on their presence
        if (rangedAttackers >= 3 || rangedCombatLevelSum >= meleeCombatLevelSum) {
            return Rs2PrayerEnum.PROTECT_RANGE;
        }
        return Rs2PrayerEnum.PROTECT_MELEE;
    }

    // Determine the optimal prayer for a Ranged-based bot
    private Rs2PrayerEnum determineOptimalPrayerForRange(int meleeAttackers, int rangedAttackers, int magicAttackers,
                                                         int meleeCombatLevelSum, int rangedCombatLevelSum, int magicCombatLevelSum) {
        // Ranged is weak to Melee, so prioritize Protect from Melee
        if (meleeAttackers >= 3 || (meleeCombatLevelSum >= rangedCombatLevelSum && meleeCombatLevelSum >= magicCombatLevelSum)) {
            return Rs2PrayerEnum.PROTECT_MELEE;
        }
        // If Melee is not a significant threat, fallback to Protect from Magic or Ranged based on their presence
        if (magicAttackers >= 3 || magicCombatLevelSum >= rangedCombatLevelSum) {
            return Rs2PrayerEnum.PROTECT_MAGIC;
        }
        return Rs2PrayerEnum.PROTECT_RANGE;
    }

    // Determine the optimal prayer for a Mage-based bot
    private Rs2PrayerEnum determineOptimalPrayerForMage(int meleeAttackers, int rangedAttackers, int magicAttackers,
                                                        int meleeCombatLevelSum, int rangedCombatLevelSum, int magicCombatLevelSum) {
        // Magic is weak to Ranged, so prioritize Protect from Missiles
        if (rangedAttackers >= 3 || (rangedCombatLevelSum >= meleeCombatLevelSum && rangedCombatLevelSum >= magicCombatLevelSum)) {
            return Rs2PrayerEnum.PROTECT_RANGE;
        }
        // If Ranged is not a significant threat, fallback to Protect from Melee or Magic based on their presence
        if (meleeAttackers >= 3 || meleeCombatLevelSum >= magicCombatLevelSum) {
            return Rs2PrayerEnum.PROTECT_MELEE;
        }
        return Rs2PrayerEnum.PROTECT_MAGIC;
    }

    public boolean weHaveEnoughEnergyToPersue() {
        return Rs2Player.getRunEnergy() > 5 && hasEnergyPotion();
    }

    public boolean hasEnergyPotion() {
        return Rs2Inventory.contains(ENERGY_POTION1)
                || Rs2Inventory.contains(ENERGY_POTION2)
                || Rs2Inventory.contains(ENERGY_POTION3)
                || Rs2Inventory.contains(ENERGY_POTION4);
    }

    public boolean castWindBlast(Rs2PlayerModel target) {

        if (Rs2Player.getRealSkillLevel(MAGIC) > 40
                && isTargetPlayerFar(target)
                && !mossKillerPlugin.lobsterEaten()
                && Rs2Inventory.contains(DEATH_RUNE)
                && Rs2Magic.canCast(WIND_BLAST)) {

            doWeFocusCamera(target);
            equipBestAvailableStaff();
            Rs2Walker.setTarget(null);
            int retries = 0;
            do {
                Rs2Magic.castOn(WIND_BLAST, target);
                sleep(300);
                eatAt(70);
                sleepUntil(() -> Rs2Player.getAnimation() == 711 || Rs2Player.getAnimation() == 1162, 1000);

                boolean noAnimations = Rs2Player.getAnimation() != 711 && Rs2Player.getAnimation() != 1162;
                boolean isNotMoving = !Rs2Player.isMoving();

                if (noAnimations && isNotMoving && retries < 3) {
                    retries++;
                } else {
                    break; // Exit the loop after success or max retries
                }
            } while (true);
            eatingMethod(target);

            return true;
        }

        return false;
    }

    public boolean castWindBlastOverhead(Rs2PlayerModel target) {
        boolean useMelee = mossKillerPlugin.useMelee();

        if (Rs2Player.getRealSkillLevel(MAGIC) > 40
                && !mossKillerPlugin.lobsterEaten()
                && Rs2Inventory.contains(DEATH_RUNE)
                && !useMelee
                && Rs2Magic.canCast(WIND_BLAST)) {

            doWeFocusCamera(target);

            equipBestAvailableStaff();

            Rs2Walker.setTarget(null);
            int retries = 0;
            do {
                Rs2Magic.castOn(WIND_BLAST, target);
                sleepUntil(() -> Rs2Player.getAnimation() == 711 || Rs2Player.getAnimation() == 1162, 1000);

                boolean noAnimations = Rs2Player.getAnimation() != 711 && Rs2Player.getAnimation() != 1162;
                boolean isNotMoving = !Rs2Player.isMoving();

                if (noAnimations && isNotMoving && retries < 3) {
                    retries++;
                } else {
                    break; // Exit the loop after success or max retries
                }
            } while (true);
            eatingMethod(target);

            return true;
        }

        return false;
    }

    /**
     * Equips any available staff from inventory, prioritizing Bryophyta's staff
     */
    private void equipAnyAvailableStaff() {
        // Priority list: Bryophyta's staff first, then Fire staff, then any other staff
        if (Rs2Inventory.hasItem(BRYOPHYTAS_STAFF_UNCHARGED)) {
            Rs2Inventory.interact(BRYOPHYTAS_STAFF_UNCHARGED, "Wield");
            sleepUntil(() -> Rs2Equipment.hasEquipped(BRYOPHYTAS_STAFF_UNCHARGED), 2000);
        }
        else if (Rs2Inventory.hasItem(STAFF_OF_FIRE)) {
            Rs2Inventory.interact(STAFF_OF_FIRE, "Wield");
            sleepUntil(() -> Rs2Equipment.hasEquipped(STAFF_OF_FIRE), 2000);
        }
    }

    /**
     * Equips the best available staff, prioritizing Bryophyta's staff over others
     */
    private void equipBestAvailableStaff() {
        // Check what we currently have equipped
        boolean bryoStaffEquipped = Rs2Equipment.hasEquipped(BRYOPHYTAS_STAFF_UNCHARGED);
        boolean fireStaffEquipped = Rs2Equipment.hasEquipped(STAFF_OF_FIRE);
        boolean anyStaffEquipped = hasAnyStaffEquipped();

        // If Bryo staff is already equipped, we're good
        if (bryoStaffEquipped) {
            return;
        }

        // If we have Bryo staff in inventory, equip it (regardless of what's currently equipped)
        if (Rs2Inventory.hasItem(BRYOPHYTAS_STAFF_UNCHARGED)) {
            Rs2Inventory.interact(BRYOPHYTAS_STAFF_UNCHARGED, "Wield");
            sleepUntil(() -> Rs2Equipment.hasEquipped(BRYOPHYTAS_STAFF_UNCHARGED), 2000);
            return;
        }

        // If fire staff isn't equipped but is in inventory, and we don't have any staff equipped
        if (!fireStaffEquipped && Rs2Inventory.hasItem(STAFF_OF_FIRE) && !anyStaffEquipped) {
            Rs2Inventory.interact(STAFF_OF_FIRE, "Wield");
            sleepUntil(() -> Rs2Equipment.hasEquipped(STAFF_OF_FIRE), 2000);
        }
    }

    /**
     * Checks if the player has any staff equipped
     */
    /**
     * Checks if the player has any staff equipped
     * @return true if any staff is equipped, false otherwise
     */
    private boolean hasAnyStaffEquipped() {
        // Check if any item with "staff" in its name is equipped
        return Rs2Equipment.hasEquippedContains("staff");
    }

    public boolean doWeFocusCamera(Rs2PlayerModel target) {
        if (!Rs2Camera.isTileOnScreen(target.getLocalLocation())) {
            Rs2Camera.turnTo(target);
            return true;
        }
        return false;
    }

    public void eatingMethod(Rs2PlayerModel target) {

        if (target != null) {
            toggleRunEnergyOn();
            if (target.getHealthRatio() > 0 && target.getHealthScale() > 0) {
                int healthPercentage = (target.getHealthRatio() * 100) / target.getHealthScale();
                if (healthPercentage < 20) {
                    Rs2Player.eatAt(50);
                } else {
                    Rs2Player.eatAt(70); // Eat at 70 otherwise
                }
            } else if (target.getHealthRatio() <= 0 || target.getHealthScale() <= 0) {
                Rs2Player.eatAt(70);
            }
        }
    }

    public void isTargetOnSameTile(Rs2PlayerModel target) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (target.getWorldLocation() == playerLocation) {

            if (scheduledFuture.isDone()
                    && Rs2Player.getInteracting() != target
                    && target.getCombatLevel() < 88) {
                if (doWeFocusCamera(target)) {
                    sleep(300);
                }
                attack(target);
            }

        }
    }

    public void setCombatStyle(Rs2PlayerModel target) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        // Get the current attack style
        int attackStyle = Microbot.getVarbitPlayerValue(VarPlayer.ATTACK_STYLE);

        sleep(600);

        // Assuming currentTarget is already declared and holds the NPC or player target
        int distanceToTarget = target.getWorldLocation().distanceTo(playerLocation);
        // Check if the target is within 7 tiles
        if (distanceToTarget <= 7) {
            // If the target is within 7 tiles, switch to Short-range if not already set
            if (mossKillerPlugin.isDefensive()) {
                if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT) {
                    Rs2Tab.switchToCombatOptionsTab();
                    sleep(200); // Ensure the tab has time to switch
                }
                Rs2Combat.setAttackStyle(WidgetInfo.COMBAT_STYLE_TWO); // Set Short-range
            }
        } else {
            // If the target is more than 7 tiles away, switch to Long-range if not already set
            if (attackStyle != 3) {
                if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT) {
                    Rs2Tab.switchToCombatOptionsTab();
                    sleep(200); // Ensure the tab has time to switch
                }
                Rs2Combat.setAttackStyle(WidgetInfo.COMBAT_STYLE_FOUR); // Set Long-range
            }
        }
    }

    private void ultimateStrengthOn() {
        if (Microbot.getClient().getBoostedSkillLevel(PRAYER) >= 1
                && Microbot.getClient().getRealSkillLevel(PRAYER) >= 33) {
            toggle(Rs2PrayerEnum.ULTIMATE_STRENGTH, true);
        }
    }

    private void ultimateStrengthOff() {
        if (Microbot.getClient().getBoostedSkillLevel(PRAYER) >= 1
                && Microbot.getClient().getRealSkillLevel(PRAYER) >= 33) {
            toggle(Rs2PrayerEnum.ULTIMATE_STRENGTH, false);
        }
    }

    public static boolean isNpcInteractingWithMe() {
        // check if we're not in multi combat
        if (Rs2Player.isInMulti()) {
            return false;
        }

        List<Rs2NpcModel> interactingNpcs = Rs2Npc.getNpcsForPlayer(npc -> true)
                .collect(Collectors.toList());

        // return true if any NPC is interacting with us
        return !interactingNpcs.isEmpty();
    }

    private boolean isStrengthPotionPresent() {
        for (int id : strengthPotionIds) {
            if (Rs2Inventory.contains(id)) {
                return true; // Strength potion found in inventory
            }
        }
        return false; // No strength potion in inventory
    }

    private void checkAndDrinkStrengthPotion() {
        int currentStrengthLevel = Microbot.getClient().getRealSkillLevel(Skill.STRENGTH); // Unboosted strength level
        int boostedStrengthLevel = Microbot.getClient().getBoostedSkillLevel(Skill.STRENGTH); // Current boosted strength level

        // Calculate the strength potion boost
        int strengthPotionBoost = (int) Math.floor(3 + (0.1 * currentStrengthLevel)); // Potion boost value

        // Calculate the expected boosted strength level
        int expectedBoostedStrength = currentStrengthLevel + strengthPotionBoost;

        // Check if the boosted strength level is less than 2 levels below the expected boosted level
        if (boostedStrengthLevel < expectedBoostedStrength - 2) {
            System.out.println("are we getting into the drinking bracket?");
            // Try to drink any available Strength potion (1 to 4 doses)
            if (Rs2Inventory.contains("Strength potion(1)")) {
                Rs2Inventory.interact("Strength potion(1)", "Drink");
            } else if (Rs2Inventory.contains("Strength potion(2)")) {
                Rs2Inventory.interact("Strength potion(2)", "Drink");
            } else if (Rs2Inventory.contains("Strength potion(3)")) {
                Rs2Inventory.interact("Strength potion(3)", "Drink");
            } else if (Rs2Inventory.contains("Strength potion(4)")) {
                Rs2Inventory.interact("Strength potion(4)", "Drink");
            }

            sleep(300);
        } else {
            System.out.println("Boosted strength level is high enough, no need to drink.");
        }
    }


    private boolean isTargetPlayerFar(Rs2PlayerModel targetPlayer) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint targetLocation = targetPlayer.getWorldLocation();

        if (playerLocation == null || targetLocation == null) {
            return true; // Treat as far if locations are null
        }

        int dx = Math.abs(playerLocation.getX() - targetLocation.getX());
        int dy = Math.abs(playerLocation.getY() - targetLocation.getY());

        // Only North, South, East, and West are "close"
        return !(dx == 1 && dy == 0 || dx == 0 && dy == 1);
    }

    private boolean isTargetPlayerFarCasting(Rs2PlayerModel targetPlayer) {
        // Get the current player's location
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        // Get the target player's location
        WorldPoint targetLocation = targetPlayer.getWorldLocation();

        // If either location is null, consider them far away
        if (playerLocation == null || targetLocation == null) {
            return true; // Treat as farcasting if locations are null
        }

        // Calculate the Manhattan distance
        int distance = Math.abs(playerLocation.getX() - targetLocation.getX())
                + Math.abs(playerLocation.getY() - targetLocation.getY());
        // Check if the distance is greater than or equal to 10 tiles
        return distance >= 10;
    }


    private void equipItems() {
        String[] items = {"Leather vambraces",
                "Leather boots",
                "Studded chaps", "Rune Chainbody",
                "Amulet of power"};

        for (String item : items) {
            if (Rs2Bank.hasItem(item)) {
                Rs2Bank.withdrawOne(item, 1);
                Rs2Inventory.waitForInventoryChanges(2500);
                if (Rs2Inventory.hasItem(item)) {
                    Rs2Inventory.interact(item, "Wear");
                    Rs2Inventory.waitForInventoryChanges(2500);
                    verifyEquipment(item);
                }
            }
        }
    }

    private void verifyEquipment(String item) {
        if (!Rs2Equipment.hasEquippedContains(item)) {
            System.out.println("Failed to equip: " + item);
        } else {
            System.out.println("Successfully equipped: " + item);
        }
    }

    public void handleMossGiants() {

        //attack the target before the sleep
        if (!scheduledFuture.isDone() || mossKillerPlugin.currentTarget != null) {
            doWeFocusCamera(mossKillerPlugin.currentTarget);
            sleep(300);
            if (Rs2Player.getInteracting() != mossKillerPlugin.currentTarget
                    && mossKillerPlugin.currentTarget.getCombatLevel() < 88) {
                basicAttackSetup();
                attack(mossKillerPlugin.currentTarget);
                sleep(300);
                state = MossKillerState.PKER;
            }
        }

        if (mossKillerPlugin.currentTarget != null && mossKillerPlugin.currentTarget.getCombatLevel() > 87) {
            state = MossKillerState.PKER;
        }

        if (mossKillerPlugin.currentTarget != null && mossKillerPlugin.currentTarget.getCombatLevel() < 87) {
            state = MossKillerState.PKER;
        }

        if (Rs2Prayer.isPrayerActive(STEEL_SKIN)) {
            Rs2Prayer.toggle(STEEL_SKIN, false);
        }

        if (Rs2Prayer.isPrayerActive(MYSTIC_LORE)) {
            Rs2Prayer.toggle(MYSTIC_LORE, false);
        }

        if (Rs2Equipment.isWearing(STAFF_OF_FIRE)
                && !mossKillerPlugin.getAttackStyle()) {
            setAutocastFireStrike();
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (!Rs2Inventory.contains(FOOD) || BreakHandlerScript.breakIn <= 120) {
            Microbot.log("Inventory does not contains FOOD or break in less than 120");
            state = MossKillerState.TELEPORT;
            return;
        }

        if (!Rs2Inventory.hasItemAmount(MIND_RUNE, 15)) {
            Microbot.log("inverse of 15 mind runes, let's bail");
            state = MossKillerState.TELEPORT;
            return;
        }

        if (!Rs2Inventory.contains(FOOD) || Rs2Inventory.contains(MOSSY_KEY) || !Rs2Inventory.contains(MIND_RUNE)) {
            Microbot.log("You have a Mossy Key! OR out of food. Get outta there!");
            state = MossKillerState.TELEPORT;
            return;
        }

        if (Rs2Walker.getDistanceBetween(playerLocation, MOSS_GIANT_SPOT) > 10 && mossKillerPlugin.currentTarget == null
                && MOSS_GIANT_AREA.contains(playerLocation)) {
            Rs2Walker.walkTo(MOSS_GIANT_SPOT);
            return;
        }
        int lobsterCount = Rs2Inventory.count(ItemID.SWORDFISH);
        if (Rs2Walker.getDistanceBetween(playerLocation, MOSS_GIANT_SPOT) > 20
                && lobsterCount < 17) {
            Microbot.log("Less than 17 swordfish, teleport reset");
            state = MossKillerState.TELEPORT;
        } else if (lobsterCount == 17
                && playerLocation.getY() > 3500
                && !MOSS_GIANT_AREA.contains(playerLocation)) {
            Microbot.log("17 swordfish, trying to walk to moss giants");
            if (scheduledFuture.isDone()) { // Only initiate if not already walking to Moss Giants
                handleAsynchWalk("Moss Giants");
            }
            return;
        }

        int randomValue = (int) Rs2Random.truncatedGauss(60, 70, 4.0);
        eatAt(randomValue);

        // Check if loot is nearby and pick it up if it's in LOOT_LIST
        for (int lootItem : LOOT_LIST) {
            if (Rs2GroundItem.exists(lootItem, 7)
                    && Rs2Inventory.getEmptySlots() == 0) {
                eatAt(100);
                sleepUntil(() -> !Rs2Inventory.isFull());
                toggleRunEnergyOn();
                if (mossKillerPlugin.currentTarget != null) {
                    break;
                }
                Rs2GroundItem.interact(lootItem, "Take", 7);
                Rs2Inventory.waitForInventoryChanges(3500);

            } else if (Rs2GroundItem.exists(lootItem, 7)
                    && Rs2Inventory.getEmptySlots() > 0) {
                toggleRunEnergyOn();
                if (mossKillerPlugin.currentTarget != null) {
                    break;
                }
                Rs2GroundItem.interact(lootItem, "Take", 7);
                Rs2Inventory.waitForInventoryChanges(3500);
            }
        }

        if (mossKillerPlugin.currentTarget == null) {


            if (Rs2GroundItem.loot("Coins", 119, 7)) {
                Rs2Inventory.waitForInventoryChanges(3500);
            }

            if (Rs2GroundItem.loot("Chaos rune", 7, 7)) {
                Rs2Inventory.waitForInventoryChanges(3500);
            }
        }
        if (Rs2Inventory.contains(NATURE_RUNE) &&
                !Rs2Inventory.contains(STAFF_OF_FIRE) &&
                mossKillerPlugin.currentTarget == null &&
                Rs2Inventory.contains(ALCHABLES) &&
                config.alchLoot()){

            if (Microbot.getClient().getRealSkillLevel(MAGIC) > 54 && Rs2Magic.canCast(HIGH_LEVEL_ALCHEMY)) {

                if (Rs2Inventory.contains(STEEL_KITESHIELD)) {
                    Rs2Magic.alch("Steel kiteshield");
                } else if (Rs2Inventory.contains(BLACK_SQ_SHIELD)) {
                    Rs2Magic.alch("Black sq shield");
                } else if (Rs2Inventory.contains(MITHRIL_SWORD)) {
                    Rs2Magic.alch("Mithril sword");
                }

                Rs2Player.waitForXpDrop(Skill.MAGIC, 10000, false);
            }
        } else if (Rs2Inventory.contains(STAFF_OF_FIRE) && mossKillerPlugin.currentTarget == null) {
            Rs2Inventory.interact(STAFF_OF_FIRE, "Wield");
        }


        if (config.buryBones() && mossKillerPlugin.currentTarget == null) {
            if (Rs2Inventory.contains(BIG_BONES)) {
                sleep(600, 1750);
                Rs2Inventory.interact(BIG_BONES, "Bury");
                Rs2Player.waitForAnimation();
            }
            if (!Rs2Inventory.isFull() && Rs2GroundItem.interact(BIG_BONES, "Take", 2)) {
                toggleRunEnergyOn();
                sleepUntil(() -> Rs2Inventory.contains(BIG_BONES));
                if (Rs2Inventory.contains(BIG_BONES)) {
                    sleep(600, 1750);
                    Rs2Inventory.interact(BIG_BONES, "Bury");
                    Rs2Player.waitForAnimation();
                }
            }
        }

        // Check if any players are near
        if (mossKillerPlugin.currentTarget == null) {
            if (!getNearbyPlayers(7).isEmpty()) {
                if (playerCounter > 5) {
                    sleep(600, 1200);
                    int world = Login.getRandomWorld(false, null);
                    if (world == 301 || world == 308) {
                        return;
                    }
                    boolean isHopped = Microbot.hopToWorld(world);
                    sleepUntil(() -> isHopped, 5000);
                    if (!isHopped) return;
                    playerCounter = 0;
                    int randomThreshold = (int) Rs2Random.truncatedGauss(0, 5, 1.5); // Adjust mean and deviation as needed
                    if (randomThreshold > 3) {
                        Rs2Inventory.open();
                    }
                    return;
                }
                playerCounter++;
            } else {
                playerCounter = 0;
            }

            if (!Rs2Equipment.isWearing(STAFF_OF_FIRE) && mossKillerPlugin.currentTarget == null) {
                sleep(600);
                Rs2Inventory.interact(STAFF_OF_FIRE, "Wield");
                sleep(600);
                setAutocastFireStrike();
            }

            if (!Rs2Combat.inCombat() && mossKillerPlugin.currentTarget == null) {
                System.out.println("attackingmoss");
                Rs2NpcModel mossGiant = Rs2Npc.getNpc("Moss giant");

                if (mossGiant != null && Rs2Npc.getHealth(mossGiant) > 0) {
                    if (!Rs2Camera.isTileOnScreen(mossGiant.getLocalLocation())) {
                        Rs2Camera.turnTo(mossGiant);
                        sleep(500); // Allow time for camera to adjust
                    }

                    if (Objects.equals(mossGiant.getInteracting(), getLocalPlayer())) {
                        System.out.println("moss giant already attacking so not going to attack");
                    } else {
                        Rs2Npc.attack(mossGiant);
                    }

                } else {
                    System.out.println("Skipping attack: Moss Giant has 0 HP or is not found.");
                }
            }
            sleep(800, 2000);
        }


        if (Rs2Inventory.contains(MOSSY_KEY)) {
            state = MossKillerState.PKER;
        }
    }

    private List<Rs2PlayerModel> getPotentialTargets() {
        return getNearbyPlayers(12);
    }

    public List<Rs2PlayerModel> getNearbyPlayers(int distance) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        // Use the predicate-based getPlayers method directly
        return Rs2Player.getPlayers(p -> p != null &&
                        p.getWorldLocation().distanceTo(playerLocation) <= distance)
                .collect(Collectors.toList());
    }


    public void walkToMossGiants() {
        Microbot.log(String.valueOf(state));

        if (!scheduledFuture.isDone() || ShortestPathPlugin.pathfinder != null) {
            sleep(600);
            state = MossKillerState.PKER;
        }
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        int currentWorld = Microbot.getClient().getWorld();
        if (currentWorld == 301 || currentWorld == 308 || currentWorld == 316) {
            int world = Login.getRandomWorld(false, null);
            if (world == 301 || world == 308) {
                return;
            }
            boolean isHopped = Microbot.hopToWorld(world);
            sleepUntil(() -> isHopped, 5000);
            if (!isHopped) return;
        }

        if (mossKillerPlugin.currentTarget != null) {

            if (Rs2Player.isInCombat()) {
                Rs2Walker.setTarget(null);
                if (!scheduledFuture.isDone()) {
                    scheduledFuture.cancel(true);
                }

                state = MossKillerState.PKER;

            }
        }

        if (mossKillerPlugin.currentTarget == null) {
            if (WildyKillerScript.WEST_BARRIER_OUTSIDE.contains(playerLocation) ||
                    WildyKillerScript.NORTH_BARRIER_OUTSIDE.contains(playerLocation) ||
                    WildyKillerScript.CORRIDOR.contains(playerLocation)) {
                if (scheduledFuture.isDone()) { // Only initiate if not already walking to Moss Giants
                    handleAsynchWalk("Moss Giants");
                }
                state = MossKillerState.WALK_TO_MOSS_GIANTS;
            }
        }

        if (mossKillerPlugin.currentTarget == null) {
            if (MOSS_GIANT_AREA.contains(playerLocation)
                    && Rs2Walker.getDistanceBetween(playerLocation, MOSS_GIANT_SPOT) < 10) {
                Rs2Walker.walkTo(MOSS_GIANT_SPOT); //dont need to eat
            }
        }

        if (MOSS_GIANT_AREA.contains(playerLocation) && mossKillerPlugin.currentTarget == null) {
            state = MossKillerState.FIGHT_MOSS_GIANTS;
        }

        if (!Rs2Inventory.hasItemAmount(MIND_RUNE, 750) && mossKillerPlugin.currentTarget == null) {
            Microbot.log("Inventory Empty or Not Recently Banked");
            int lobsterCount = Rs2Inventory.count(ItemID.SWORDFISH);
            if (lobsterCount < 17) {
                Microbot.log("Less than 17 swordfish, teleport reset");
                state = MossKillerState.TELEPORT;
            }

            if (Rs2Inventory.hasItem(MOSSY_KEY)) {
                Microbot.log("Mossy key detected!");
                state = MossKillerState.TELEPORT;
            }

            if (MOSS_GIANT_AREA.contains(playerLocation) ||
                    CORRIDOR.contains(playerLocation) ||
                    TOTAL_FEROX_ENCLAVE.contains((playerLocation))
                            && mossKillerPlugin.currentTarget == null) {
                Microbot.log("You're in the wilderness and I don't get the problem");
                if (Rs2Equipment.isNaked()) {
                    state = MossKillerState.WALK_TO_BANK;
                }
            }
            if (playerLocation.getY() < 3520) {
                state = MossKillerState.WALK_TO_BANK;
            }
        }

        if (Rs2Inventory.hasItemAmount(MIND_RUNE, 750) && mossKillerPlugin.currentTarget == null) {
            if (MOSS_GIANT_AREA.contains(playerLocation) || CORRIDOR.contains(playerLocation) || TOTAL_FEROX_ENCLAVE.contains((playerLocation))) {
                Microbot.log("Looks like you're in the wilderness with 750 mind runes");
                if (Rs2Inventory.hasItem(FOOD) && Rs2Inventory.hasItem(LAW_RUNE) && Rs2Inventory.hasItem(AIR_RUNE) && !Rs2Inventory.hasItem(MOSSY_KEY)) {
                    Microbot.log("You should be attacking Moss Giants");
                    if (Rs2Player.getRunEnergy() < 90 && TOTAL_FEROX_ENCLAVE.contains(playerLocation)) {
                        Rs2Walker.walkTo(3130, 3636, 0);
                        if (Rs2GameObject.exists(POOL_OF_REFRESHMENT)) {
                            Rs2GameObject.interact(POOL_OF_REFRESHMENT, "Drink");
                            sleepUntil(() -> Rs2Player.getRunEnergy() == 100 && !Rs2Player.isAnimating(2000));
                        }
                    }
                    if (scheduledFuture.isDone()) { // Only initiate if not already walking to Moss Giants
                        handleAsynchWalk("Moss Giants");
                    } else if (!Rs2Inventory.hasItem(FOOD) && Rs2Inventory.hasItem(LAW_RUNE) && Rs2Inventory.hasItem(AIR_RUNE) && !Rs2Inventory.hasItem(MOSSY_KEY)) {
                        Microbot.log("You should be getting food");
                        if (scheduledFuture.isDone()) { // Only initiate if not already walking to Moss Giants
                            state = MossKillerState.BANK;
                        }
                        return;
                    }
                }
            }
        }
        if (Rs2Inventory.hasItemAmount(MIND_RUNE, 1500)) {
            state = MossKillerState.BANK;
        }
        if (playerLocation.getY() < 3520) {
            Microbot.log("You're not in the wilderness and have 750 Mind runes");
            if (Rs2Inventory.hasItem(LAW_RUNE) && Rs2Inventory.hasItem(AIR_RUNE) && !Rs2Inventory.hasItem(MOSSY_KEY)) {
                Microbot.log("Go to ferox");
                state = MossKillerState.CASTLE_WARS_TO_FEROX;
            } else {
                state = MossKillerState.WALK_TO_BANK;
            }
        }
        if (playerLocation.getY() > 9000) {
            Microbot.log("You're in castle wars portal");
            state = MossKillerState.CASTLE_WARS_TO_FEROX;
        }

    }

    public void handleBanking() {

        Microbot.log(String.valueOf(state));

        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (!scheduledFuture.isDone()) {
            sleep(600);
            state = MossKillerState.PKER;
        }

        sleep(1200);

        if (CASTLE_WARS_AREA.contains(playerLocation)
                && Rs2Inventory.containsAll(AIR_RUNE, LAW_RUNE)
                && Rs2Equipment.isEquipped(STAFF_OF_FIRE, WEAPON)) {
            teleportAndStopWalking();
        }

        sleep(1200);

        if (MOSS_GIANT_AREA.contains(playerLocation) || Rs2GroundItem.exists(CHAOS_RUNE, 10)) {
            state = MossKillerState.FIGHT_MOSS_GIANTS;
        }

        sleep(6200);

        if (LUMBRIDGE_AREA.contains(playerLocation) || Rs2Equipment.isNaked()) {
            state = MossKillerState.WALK_TO_BANK;
        }

        sleep(1200);

        if (playerLocation.getY() > 3500
                && !TOTAL_FEROX_ENCLAVE.contains(playerLocation)) {
            Microbot.log("you're somewhere in wilderness and wanna bank?");
            sleep(1200);
            state = MossKillerState.TELEPORT;
        }

        sleep(1200);

        if (Rs2Inventory.hasItemAmount(MIND_RUNE, 1500) &&
                Rs2Walker.getDistanceBetween(playerLocation, VARROCK_WEST_BANK) > 6) {
            state = MossKillerState.WALK_TO_BANK;
        }


        if (Rs2Bank.openBank()) {
            sleepUntil(Rs2Bank::isOpen, 180000);
            Microbot.log("Finished the 3 minute sleepUntil bank is open");
            Rs2Bank.depositAll();
            sleepUntil(Rs2Inventory::isEmpty);
            if (Rs2Equipment.isNaked()) {
                sleep(1000, 1500);
                Rs2Bank.withdrawX(AIR_RUNE, 100);
                Rs2Inventory.waitForInventoryChanges(2500);
                Rs2Bank.withdrawX(MIND_RUNE, 100);
                sleepUntil(() -> Rs2Inventory.contains(MIND_RUNE));
                Rs2Inventory.waitForInventoryChanges(2500);
                Rs2Bank.withdrawOne("Staff of fire", 1);
                Rs2Inventory.waitForInventoryChanges(2500);
                if (!Rs2Equipment.isEquipped(STAFF_OF_FIRE, WEAPON)) {
                    if (Rs2Inventory.hasItem(STAFF_OF_FIRE)) {
                        Rs2Inventory.interact(STAFF_OF_FIRE, "Wield");
                    } else {
                        System.out.println("Staff of fire is not in the inventory.");
                    }
                }
                sleep(900, 3500);
                Rs2Bank.closeBank();
                sleep(900, 3500);
                setAutocastFireStrike();
                sleep(900, 3500);
                Rs2Bank.openBank();
                sleep(900, 3500);
                Rs2Bank.depositAll();

            }
            sleep(1000, 1500);
            if (!Rs2Bank.hasItem(AIR_RUNE) || !Rs2Bank.hasItem(LAW_RUNE) || !Rs2Bank.hasItem(FOOD)) {
                state = MossKillerState.WALK_TO_BANK;
                return;
            }
            int keyTotal = Rs2Bank.count("Mossy key");
            Microbot.log("Key Total: " + keyTotal);
            sleep(600, 900);
            if (Rs2Inventory.isEmpty()) {
                Rs2Bank.withdrawX(AIR_RUNE, 1550);
                Rs2Inventory.waitForInventoryChanges(2500);
                if (!Rs2Bank.hasItem(AIR_RUNE)) {
                    JOptionPane.showMessageDialog(null, "The Script has Shut Down due to no Air Runes in bank.");
                    moarShutDown();
                    shutdown();
                }
                sleep(500, 1000);
                Rs2Bank.withdrawX(LAW_RUNE, 5);
                Rs2Inventory.waitForInventoryChanges(2500);
                if (!Rs2Bank.hasItem(LAW_RUNE)) {
                    JOptionPane.showMessageDialog(null, "The Script has Shut Down due to no Law Runes in bank.");
                    moarShutDown();
                    shutdown();
                }
                sleep(500, 1000);
                Rs2Bank.withdrawX(MIND_RUNE, 750);
                Rs2Inventory.waitForInventoryChanges(2500);
                if (!Rs2Bank.hasItem(MIND_RUNE)) {
                    JOptionPane.showMessageDialog(null, "The Script has Shut Down due to no Mind Runes in bank.");
                    moarShutDown();
                    shutdown();
                }
                sleep(900, 2100);
                Rs2Bank.withdrawX(DEATH_RUNE, 30);
                Rs2Inventory.waitForInventoryChanges(2500);
                Rs2Bank.withdrawOne(ENERGY_POTION4);
                Rs2Inventory.waitForInventoryChanges(2500);
                for (int id : strengthPotionIds) {
                    if (Rs2Bank.hasItem(id)) {
                        Rs2Bank.withdrawOne(id);
                        break;
                    }
                }


                if (!Rs2Equipment.hasEquipped(RUNE_CHAINBODY)) {
                    OutfitHelper.equipOutfit(OutfitHelper.OutfitType.MAGE);
                    //equipItems();

                    CombatMode mode = mossKillerConfig.combatMode();

                    if (Objects.requireNonNull(mode) == CombatMode.FIGHT) {
                        sleep(1500, 2500);
                        if (Microbot.getClient().getRealSkillLevel(Skill.RANGED) >= 30) {
                            Rs2Bank.withdrawX(ADAMANT_ARROW, 75);
                        }
                        Rs2Inventory.waitForInventoryChanges(2500);
                        Rs2Inventory.interact(ADAMANT_ARROW, "Wield");
                        Rs2Inventory.waitForInventoryChanges(2500);
                        if (Microbot.getClient().getRealSkillLevel(Skill.RANGED) >= 30) {
                            Rs2Bank.withdrawOne(MAPLE_SHORTBOW);
                            Rs2Inventory.waitForInventoryChanges(2500);
                            Rs2Bank.withdrawOne(MAPLE_LONGBOW);
                        }
                        sleep(1500, 2500);
                        Rs2Bank.withdrawOne(RUNE_SCIMITAR);
                        Rs2Inventory.waitForInventoryChanges(2500);
                        for (int id : strengthPotionIds) {
                            if (Rs2Inventory.contains(id)) {
                                hasStrengthPotion = true;
                                break;
                            }
                        }

                        if (!hasStrengthPotion) for (int id : strengthPotionIds) {
                            if (Rs2Bank.hasItem(id)) {
                                Rs2Bank.withdrawOne(id);
                                break;
                            }
                        }

                    } else if (Objects.requireNonNull(mode) == LURE) {

                        sleep(1500, 2500);
                        Rs2Bank.withdrawX(ENERGY_POTION4, 2);
                        sleep(1500, 2500);
                    }
                }

                if (Rs2Inventory.containsAll(new int[]{AIR_RUNE, LAW_RUNE, MIND_RUNE})) {
                    if (Rs2Bank.closeBank()) {
                        state = MossKillerState.CASTLE_WARS_TO_FEROX;
                    }
                }
            } else Rs2Bank.depositAll();
            sleep(500, 1000);
        }

    }

    public void setAutocastFireStrike() {

        if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT) {
            // Step 1: Open the Combat tab
            if (!Rs2Widget.clickWidget(COMBAT_TAB_WIDGET_ID)) {
                System.out.println("Failed to click the Combat tab.");
                return;
            }
        }
        // Optional: Wait for the combat tab to fully load
        sleep(1500); // Adjust this timing as needed or use a condition to validate

        if (!config.forceDefensive()) {
            if (Microbot.getClient().getRealSkillLevel(DEFENCE) < 60 && Rs2Widget.isWidgetVisible(ComponentID.COMBAT_DEFENSIVE_SPELL_BOX)) {
                if (!Rs2Widget.clickWidget(CHOOSE_SPELL_DEFENSIVE_WIDGET_ID)) {
                    System.out.println("Failed to click 'Choose spell Defensive' widget.");
                    return;
                }
            } else if (Microbot.getClient().getRealSkillLevel(DEFENCE) >= 60 && Rs2Widget.isWidgetVisible(ComponentID.COMBAT_SPELL_BOX)) {
                if (!Rs2Widget.clickWidget(CHOOSE_SPELL_WIDGET_ID)) {
                    System.out.println("Failed to click 'Choose spell' widget.");
                    return;
                }
            }

        } else if (config.forceDefensive()) {
            if (Rs2Widget.isWidgetVisible(ComponentID.COMBAT_DEFENSIVE_SPELL_BOX)) {
                if (!Rs2Widget.clickWidget(CHOOSE_SPELL_DEFENSIVE_WIDGET_ID)) {
                    System.out.println("Failed to click 'Choose spell Defensive' widget.");
                    return;
                }
            }

        }
        sleep(1500);

        Rs2Widget.clickWidget("Fire Strike", true);

        sleep(1500);

    }

    public void setAutocastDeathBlast() {
        // Step 1: Open the Combat tab
        if (!Rs2Widget.clickWidget(COMBAT_TAB_WIDGET_ID)) {
            System.out.println("Failed to click the Combat tab.");
            return;
        }
        // Optional: Wait for the combat tab to fully load
        sleep(600); // Adjust this timing as needed or use a condition to validate

        if (Microbot.getClient().getRealSkillLevel(DEFENCE) < 60) {
            if (!Rs2Widget.clickWidget(CHOOSE_SPELL_DEFENSIVE_WIDGET_ID)) {
                System.out.println("Failed to click 'Choose spell Defensive' widget.");
                return;
            }
        } else if (Microbot.getClient().getRealSkillLevel(DEFENCE) >= 60) {
            if (!Rs2Widget.clickWidget(CHOOSE_SPELL_WIDGET_ID)) {
                System.out.println("Failed to click 'Choose spell' widget.");
                return;
            }
        }

        sleep(600);

        Rs2Widget.clickWidget("Wind Blast");

        sleep(800);

    }

    public void handleFerox() {
        Microbot.log(String.valueOf(state));

        if (!scheduledFuture.isDone()) {
            sleep(600);
            state = MossKillerState.PKER;
        }

        sleep(1200);
        if (Rs2Equipment.isNaked()) {
            state = MossKillerState.BANK;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        //if not within 8 tiles and Y < 5000 walk to CASTLE_WARS
        if (Rs2Walker.getDistanceBetween(playerLocation, CASTLE_WARS) > 6
                && playerLocation.getY() < 5000
                && playerLocation.getY() > 3100
                && !TOTAL_FEROX_ENCLAVE.contains(playerLocation)
                && !Rs2Inventory.contains(FOOD)) {
            Microbot.log("Should be walking to castle wars entry");
            Rs2Walker.walkTo(CASTLE_WARS);
            //toggleRunEnergyOff();
            if (mossKillerPlugin.playerJammed()) {
                teleportAndStopWalking();
                sleep(1200);
                Rs2Walker.setTarget(null);
                sleep(1200);
                Rs2Walker.restartPathfinding(playerLocation, CASTLE_WARS);
            }
            return;
        }
        // TO DO if helmet and cape is equipped, unequip helmet and cape
        if (Rs2Walker.getDistanceBetween(playerLocation, CASTLE_WARS) <= 6) {
            Random random = new Random();
            boolean interactWithSaradominist = random.nextBoolean(); // Generates true or false randomly
            if (Rs2Equipment.isWearing(BLUE_WIZARD_HAT)) {
                sleep(1200);
                Rs2Equipment.unEquip(BLUE_WIZARD_HAT);
                sleep(1200);
            }
            if (interactWithSaradominist) {
                Rs2Npc.interact("Saradominist recruiter", "Join Castle Wars");
            } else {
                Rs2Npc.interact("Zamorakian recruiter", "Join Castle Wars");
            }

            sleep(4000, 5000);

            return;
        }
        if (playerLocation.getY() > 9000) {
            Rs2GameObject.interact("Portal", "Exit");
            sleep(4000, 5000);
            return;
        }

        if (!Rs2Inventory.hasItem(FOOD)) {
            Rs2GameObject.interact("Bank chest", "Use");
            sleepUntil(Rs2Bank::isOpen, 10000);
            if (Rs2Bank.isOpen()) {
                //withdraw 20 food, close bank
                Rs2Bank.withdrawX(FOOD, 17);
                Rs2Inventory.waitForInventoryChanges(2500);
                if (!Rs2Bank.hasItem(FOOD)) {
                    JOptionPane.showMessageDialog(null, "The Script has Shut Down due to no FOOD in bank.");
                    shutdown();
                }
                if (Microbot.getClient().getRealSkillLevel(Skill.RANGED) >= 30 &&
                        !Rs2Equipment.isEquipped(ADAMANT_ARROW, AMMO)) {
                    sleep(1500, 2500);
                    Rs2Inventory.interact(ADAMANT_ARROW, "Wield");
                }

                sleep(1500, 2500);
                if (Microbot.getClient().getRealSkillLevel(Skill.RANGED) >= 30 &&
                        !Rs2Inventory.contains(MAPLE_SHORTBOW)) {
                    Rs2Bank.withdrawOne(MAPLE_SHORTBOW);
                }
                sleep(1500, 2500);

                if (Microbot.getClient().getRealSkillLevel(Skill.RANGED) >= 30 &&
                        !Rs2Inventory.contains(MAPLE_LONGBOW)) {
                    Rs2Bank.withdrawOne(MAPLE_LONGBOW);
                }
                sleep(1500, 2500);
                Rs2Bank.withdrawOne(RUNE_SCIMITAR);
                Rs2Inventory.waitForInventoryChanges(2500);
                if(Rs2Bank.hasItem(BRYOPHYTAS_STAFF_UNCHARGED)) {
                    Rs2Bank.withdrawOne(BRYOPHYTAS_STAFF_UNCHARGED);
                    Rs2Inventory.waitForInventoryChanges(2500);
                }
                if (!Rs2Equipment.isWearing(BLUE_WIZARD_HAT) && !Rs2Inventory.contains(BLUE_WIZARD_HAT)) {
                    Rs2Bank.withdrawOne(BLUE_WIZARD_HAT);
                    Rs2Inventory.waitForInventoryChanges(2500);
                }
                if (!Rs2Equipment.isWearing(BLUE_WIZARD_HAT) && Rs2Inventory.contains(BLUE_WIZARD_HAT)) {
                    Rs2Inventory.interact(BLUE_WIZARD_HAT, "Wear");
                    Rs2Inventory.waitForInventoryChanges(2500);
                }

                if (!Rs2Equipment.isWearing(STAFF_OF_FIRE)) {
                    Rs2Bank.withdrawOne(STAFF_OF_FIRE);
                    Rs2Inventory.waitForInventoryChanges(2500);
                    Rs2Inventory.interact(STAFF_OF_FIRE, "Wield");
                    Rs2Inventory.waitForInventoryChanges(2500);
                }

                if (Rs2Inventory.hasItemAmount(MAPLE_SHORTBOW, 2)) {
                    sleep(900, 1200);
                    Rs2Bank.depositOne(MAPLE_SHORTBOW);
                    sleep(900, 1200);
                }

                if (Rs2Inventory.hasItemAmount(RUNE_SCIMITAR, 2)) {
                    sleep(900, 1200);
                    Rs2Bank.depositOne(RUNE_SCIMITAR);
                    sleep(900, 1200);

                }
                sleep(900, 1200);
                Rs2Bank.closeBank();
                sleep(300, 1200);
            }
        }
        sleep(600, 1200);
        if (playerLocation.getY() < 3100) {
            Microbot.log("trying to open big door");
            Rs2GameObject.interact(30387);
            sleepUntil(Rs2Dialogue::isInDialogue);
            if (Rs2Dialogue.isInDialogue()) {
                Rs2Dialogue.keyPressForDialogueOption("Yes");
            }
            sleepUntil(() -> FEROX_TELEPORT_AREA.contains(playerLocation));
        } else if (playerLocation.getY() > 3100) {
            state = MossKillerState.WALK_TO_MOSS_GIANTS;
        }

        if (FEROX_TELEPORT_AREA.contains(playerLocation)) {
            Microbot.log("in ferox teleport area");
        }
        state = MossKillerState.WALK_TO_MOSS_GIANTS;
    }


    public void walkToVarrockWestBank() {

        Microbot.log(String.valueOf(state));

        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (!scheduledFuture.isDone()) {
            sleep(600);
            if (isRunning()) {
                state = MossKillerState.PKER;
            }
        }

        if (MOSS_GIANT_AREA.contains(playerLocation)) {
            state = MossKillerState.FIGHT_MOSS_GIANTS;
        }

        if (isRunning()) {

            if (Rs2Inventory.hasItemAmount(MIND_RUNE, 750)
                    && !Rs2Inventory.hasItem(FOOD)) {
                state = MossKillerState.CASTLE_WARS_TO_FEROX;
                return;
            }

            if (Rs2Inventory.containsAll(new int[]{AIR_RUNE, LAW_RUNE, FOOD, MIND_RUNE})
                    && !Rs2Inventory.contains(MOSSY_KEY)
                    && TOTAL_FEROX_ENCLAVE.contains(playerLocation)) {
                state = MossKillerState.WALK_TO_MOSS_GIANTS;
                return;
            }

            if (TOTAL_FEROX_ENCLAVE.contains(playerLocation)) {
                state = MossKillerState.BANK;
            }

            if (Rs2Walker.getDistanceBetween(playerLocation, VARROCK_WEST_BANK) > 6
                    || Rs2Player.isTeleBlocked()
                    || getWildernessLevelFrom(Rs2Player.getWorldLocation()) <= 20) {
                Rs2Bank.walkToBank(BankLocation.VARROCK_WEST);
            }

            if (Rs2Walker.getDistanceBetween(playerLocation, VARROCK_WEST_BANK) <= 6) {
                Microbot.log("distance to varrock west bank  < 6, bank now");
                state = MossKillerState.BANK;
            }
        }
    }

    public void varrockTeleport() {
        Microbot.log(String.valueOf(state));
        Rs2Player.eatAt(70);
        sleep(200);
        if (!scheduledFuture.isDone()) {
            state = MossKillerState.PKER;
        }
        sleep(200);
        if (mossKillerPlugin.currentTarget != null) {
            state = MossKillerState.PKER;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (Rs2Inventory.containsAll(AIR_RUNE, LAW_RUNE, MIND_RUNE, FOOD)
                && !Rs2Inventory.contains(MOSSY_KEY) && MOSS_GIANT_AREA.contains(playerLocation)
                && mossKillerPlugin.currentTarget == null) {
            state = MossKillerState.FIGHT_MOSS_GIANTS;
        }

        sleep(600);

        if (playerLocation.getY() > 3500) {
            if (mossKillerPlugin.isTeleblocked()) {
                walkToAndTeleportZEROWILD();
                return;
            }
            walkToAndTeleport();
        } else {
            state = MossKillerState.WALK_TO_BANK;
        }

        Microbot.log(String.valueOf(Rs2Walker.getDistanceBetween(playerLocation, VARROCK_SQUARE)));
        if (mossKillerPlugin.currentTarget != null && mossKillerPlugin.currentTarget.getCombatLevel() > 87) {
            state = MossKillerState.PKER;
        }

        Rs2Player.eatAt(70);
        sleep(1000, 2000);

        if (Rs2Walker.getDistanceBetween(playerLocation, VARROCK_SQUARE) <= 10 && playerLocation.getY() < 5000) {
            //toggleRunEnergyOff();
            state = MossKillerState.WALK_TO_BANK;
            return;
        }

        if (Rs2Inventory.contains(MOSSY_KEY)) {
            walkToAndTeleport();
        }

        Rs2Player.eatAt(70);
        sleep(2000, 3500);
        Rs2Player.eatAt(70);

    }


    public void walkToAndTeleport() {

        // Get the player's current location
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        // Walk to TWENTY_WILD if not already close enough
        if (Rs2Walker.getDistanceBetween(playerLocation, TWENTY_WILD) > 5
                && !MOSS_GIANT_AREA.contains(playerLocation)
                || Rs2Inventory.contains(MOSSY_KEY)
                || BreakHandlerScript.breakIn <= 120
                || !Rs2Inventory.contains(FOOD)
                || !Rs2Inventory.hasItemAmount(MIND_RUNE, 15)) {
            if (scheduledFuture.isDone() && !Rs2Inventory.hasItemAmount(FOOD, 17)) { // Only initiate if not already walking to Twenty Wild
                handleAsynchWalk("Twenty Wild");
            }
            if (Rs2Inventory.hasItemAmount(FOOD, 17)) {
                state = MossKillerState.WALK_TO_MOSS_GIANTS;
            }
            Microbot.log("Hitting Return");
            return;
        } else if (Rs2Walker.getDistanceBetween(playerLocation, TWENTY_WILD) < 5) {
            teleportAndStopWalking();
        }

        // Check if the player has teleported (Y-coordinate condition)
        if (playerLocation.getY() < 3500) {
            Microbot.log("Teleport successful.");
            state = MossKillerState.WALK_TO_BANK;
        }
    }

    public void walkToAndTeleportZEROWILD() {

        // Get the player's current location
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        // Walk to TWENTY_WILD if not already close enough
        if (Rs2Walker.getDistanceBetween(playerLocation, TWENTY_WILD) > 3) {
            if (scheduledFuture.isDone()) { // Only initiate if not already walking to Zero Wild
                handleAsynchWalk("Zero Wild");
            }
            return;
        }

        // Check if the player has teleported (Y-coordinate condition)
        if (playerLocation.getY() < 3500) {
            Microbot.log("Teleport successful.");
            state = MossKillerState.WALK_TO_BANK;
        }
    }

    private void teleportAndStopWalking() {
        if (Rs2Inventory.containsAll(AIR_RUNE, LAW_RUNE) && getWildernessLevelFrom(Rs2Player.getWorldLocation()) <= 20) {
            if (Rs2Inventory.contains(STAFF_OF_FIRE)) {
                sleep(600);
                Rs2Inventory.interact(STAFF_OF_FIRE, "Wield");
            }
            sleep(600);
            Rs2Magic.cast(MagicAction.VARROCK_TELEPORT);
            Microbot.log("Script has cast Varrock Teleport");
        } else if (!Rs2Inventory.containsAll(AIR_RUNE, LAW_RUNE)) {
            Microbot.log("Missing runes for teleportation.");
            state = MossKillerState.WALK_TO_BANK;
        }
    }

    public void toggleRunEnergyOn() {
        if (!Rs2Player.isRunEnabled() && Rs2Player.getRunEnergy() > 0) {
            Rs2Player.toggleRunEnergy(true);
        }
    }

    public void toggleRunEnergyOff() {
        if (Rs2Player.isRunEnabled() && Rs2Player.getRunEnergy() > 0) {
            Rs2Player.toggleRunEnergy(false);
        }
    }

}
