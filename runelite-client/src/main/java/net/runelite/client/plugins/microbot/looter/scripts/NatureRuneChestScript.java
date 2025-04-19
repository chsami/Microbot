package net.runelite.client.plugins.microbot.looter.scripts;

import net.runelite.api.GameObject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.looter.AutoLooterConfig;
import net.runelite.client.plugins.microbot.looter.enums.LooterState;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntilTrue;

public class NatureRuneChestScript extends Script {

    LooterState state;
    boolean init = true;

    public boolean run(AutoLooterConfig config) {
        Microbot.enableAutoRunOn = false;

        if (config.worldHop()) {
            Microbot.showMessage("Make sure autologin plugin is enabled and randomWorld checkbox is checked!");
        }
        Rs2Antiban.resetAntibanSettings();
        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.GENERAL_COLLECTING);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                long startTime = System.currentTimeMillis();

                if(init){
                    getState(config);
                }

                if (Rs2Player.isMoving() || Rs2Player.isAnimating() || Microbot.pauseAllScripts) return;

                switch (state) {
                    case LOOTING:
                        if (config.worldHop()) {
                            Rs2Player.logoutIfPlayerDetected(1, 10);
                            return;
                        }
                        Optional<GameObject> natureRuneChest = Rs2GameObject.getGameObjects().stream()
                                        .filter(obj -> obj.getId() == config.natureRuneChestLocation().getObjectID())
                                        .sorted(Comparator.comparingInt(obj -> Rs2Player.getWorldLocation().distanceTo(obj.getWorldLocation())))
                                        .findFirst();
                        if (natureRuneChest.isPresent()) {
                            if(Rs2GameObject.interact(natureRuneChest.get(), "Search for traps")){
                                Rs2Antiban.actionCooldown();
                                sleepUntilTrue(() -> !Rs2Player.isInteracting(), 500, 8000);
                                sleep(Rs2Random.between(18000, 20000));
                            }
                        }
                        break;
                    case WALKING:
                        Rs2Walker.walkTo(config.natureRuneChestLocation().getWorldPoint());
                        sleepUntilTrue(() -> isNearNatureRuneChest(config, 6) && !Rs2Player.isMoving(), 600, 300000);
                        if (!isNearNatureRuneChest(config, 6)) return;
                        state = LooterState.LOOTING;
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown(){
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }

    private void getState(AutoLooterConfig config) {
        if (!isNearNatureRuneChest(config, 6)) {
            state = LooterState.WALKING;
            init = false;
            return;
        }
        state = LooterState.LOOTING;
        init = false;
    }

    private boolean isNearNatureRuneChest(AutoLooterConfig config, int distance) {
        return Rs2Player.getWorldLocation().distanceTo(config.natureRuneChestLocation().getWorldPoint()) <= distance;
    }

    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 15;
        Rs2AntibanSettings.actionCooldownChance = 0.4;
        Rs2AntibanSettings.microBreakChance = 0.15;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.1;
    }
}
