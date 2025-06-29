package net.runelite.client.plugins.microbot.TaF.CalcifiedRockMiner;

import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.VERY_LOW;

public class CalcifiedRockMinerScript extends Script {
    public static final String VERSION = "1.0";
    public static final int WEEPING_ROCK = 51493;
    public static CalcifiedRockMinerState BOT_STATUS = CalcifiedRockMinerState.BANKING;
    private final WorldPoint CALCIFIED_ROCK_LOCATION = new WorldPoint(1516, 9545, 1);
    private final WorldPoint ANVIL = new WorldPoint(1447, 9584, 1);

    {
        Microbot.enableAutoRunOn = false;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = false;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.profileSwitching = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.04;
        Rs2Antiban.setActivityIntensity(VERY_LOW);
    }

    public boolean run(CalcifiedRockMinerConfig config) {
        BOT_STATUS = CalcifiedRockMinerState.MINING;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                switch (BOT_STATUS) {
                    case BANKING:
                        handleBanking(config);
                        break;
                    case CRUSHING:
                        handleCrushing(config);
                        break;
                    case MINING:
                        handleMining(config);
                        break;
                }
            } catch (Exception ex) {
                System.out.println("Exception message: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleMining(CalcifiedRockMinerConfig config) {
        if (Rs2Inventory.isFull()) {
            if (config.dropDeposits()) {
                Rs2Inventory.dropAll("Calcified deposit");
                Rs2Inventory.dropAll("uncut");
                return;
            } else if (config.crushDeposits()) {
                BOT_STATUS = CalcifiedRockMinerState.CRUSHING;
                return;
            } else {
                BOT_STATUS = CalcifiedRockMinerState.BANKING;
                return;
            }
        }
        if (Rs2Player.distanceTo(CALCIFIED_ROCK_LOCATION) > 15) {
            Rs2Walker.walkTo(CALCIFIED_ROCK_LOCATION);
        }

        if (hopIfTooManyPlayersNearby(config)) return; // Exit current cycle after hop

        if (Rs2Equipment.isWearing("Dragon pickaxe") || Rs2Equipment.isWearing("Crystal pickaxe")) {
            Rs2Combat.setSpecState(true, 1000);
            return;
        }

        if (config.focusCrackedWaterDeposits() && !Rs2Player.isMoving()) {
            var weepingRocks = Rs2GameObject.getDecorativeObjects(x -> x.getId() == WEEPING_ROCK, Rs2Player.getWorldLocation());
            if (weepingRocks != null && !weepingRocks.isEmpty()) {
                var rock = weepingRocks.stream().findFirst().get();
                MoveCameraToRock(rock.getWorldLocation());
                var distance = rock.getLocalLocation().distanceTo(Rs2Player.getLocalLocation());
                // 128 == 1 tile, so we must be mining the tear, if above, we should move to the tear
                if (distance > 128 || !Rs2Player.isAnimating()) {
                    Rs2Camera.turnTo(rock.getLocalLocation(), 45);
                    Microbot.getMouse().click(rock.getCanvasLocation());
                    Rs2Player.waitForXpDrop(Skill.MINING, true);
                    Rs2Antiban.actionCooldown();
                    Rs2Antiban.takeMicroBreakByChance();
                    sleepUntil(Rs2Player::isAnimating, 5000);
                    return;
                }
            }
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()){
            return;
        }

        GameObject rock = Rs2GameObject.findReachableObject("Calcified rocks", true, 12, CALCIFIED_ROCK_LOCATION);
        if (rock != null) {
            MoveCameraToRock(rock.getWorldLocation());
            if (Rs2GameObject.interact(rock)) {
                Rs2Player.waitForXpDrop(Skill.MINING, true);
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
                sleepUntil(Rs2Player::isAnimating, 5000);
            }
        } else {
            Microbot.log("No calcified rock found. Waiting...");
        }
    }

    private boolean hopIfTooManyPlayersNearby(CalcifiedRockMinerConfig config) {
        int maxPlayers = config.maxPlayersInArea();
        if (maxPlayers > 0) {
            WorldPoint localLocation = Rs2Player.getWorldLocation();

            long nearbyPlayers = Microbot.getClient().getPlayers().stream()
                    .filter(p -> p != null && p != Microbot.getClient().getLocalPlayer())
                    .filter(p -> {
                        return p.getWorldLocation().distanceTo(localLocation) <= 15;
                    })
                    //filter if players are using mining animation
                    .filter(p -> p.getAnimation() != -1)
                    .count();

            if (nearbyPlayers >= maxPlayers) {
                Microbot.log("Too many players nearby. Hopping...");
                Rs2Random.waitEx(3200, 800); // Delay to avoid UI locking
                int world = Login.getRandomWorld(Rs2Player.isMember());
                boolean hopped = Microbot.hopToWorld(world);
                if (!hopped) return false;
                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING);
                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);
            }
        }
        return false;
    }

    private void handleCrushing(CalcifiedRockMinerConfig config) {
        if (config.crushDeposits() && Rs2Inventory.hasItem("hammer") && Rs2Inventory.hasItem(29088)) {
            if (Rs2Player.getWorldLocation().distanceTo(ANVIL) < 3) {
                Rs2Inventory.interact(29088, "use");
                Rs2GameObject.interact("Anvil");
                sleep(400,600);
                Rs2Widget.sleepUntilHasWidget("How many would you like to smash?");
                sleep(200,400);
                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                sleep(200,400);
                sleepUntil(() -> !Rs2Player.isAnimating(), 16000);
                BOT_STATUS = CalcifiedRockMinerState.BANKING;
            }
            else {
                Rs2Walker.walkTo(ANVIL);
            }
        } else {
            BOT_STATUS = CalcifiedRockMinerState.BANKING;
        }
    }

    private void MoveCameraToRock(WorldPoint rock) {
        Rs2Camera.resetPitch();
        Rs2Camera.resetZoom();
        Rs2Camera.turnTo(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), rock));
    }

    private void handleBanking(CalcifiedRockMinerConfig config) {
        if (!config.dropDeposits()) {
            Rs2Bank.walkToBank(BankLocation.CAM_TORUM);
            Rs2Bank.openBank();
            Rs2Bank.depositAll("Calcified deposit");
            Rs2Bank.depositAll("Uncut");
            Rs2Bank.closeBank();

        }
        BOT_STATUS = CalcifiedRockMinerState.MINING;
    }

    public enum CalcifiedRockMinerState {BANKING, CRUSHING, MINING}

}
