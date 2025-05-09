package net.runelite.client.plugins.microbot.bee.salamanders;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.hunter.HunterTrap;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.EXTREME;
import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.MODERATE;

public class SalamanderScript extends Script {
    public static final int SMALL_FISHING_NET = 303;
    public static final int ROPE = 954;
    public static int SalamandersCaught = 0;
    public boolean hasDied = false;

    public boolean run(SalamanderConfig config, SalamanderPlugin plugin) {
        Rs2Antiban.resetAntibanSettings();
        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.GENERAL_HUNTER);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (!this.isRunning()) return;

                // Check if we have enough supplies
                if (config.salamanderHunting().getName().equals("Black salamander") && hasDied) {
                    Microbot.log("We died - Restocking supplies");
                    handleBanking(config);
                } else if ((Rs2Inventory.count(ROPE) < 1 || Rs2Inventory.count(SMALL_FISHING_NET) < 1) && plugin.getTraps().isEmpty()) {
                    Microbot.log("Not enough supplies, need ropes and fishing nets");
                    handleBanking(config);
                    return;
                }


                // Get selected salamander type from config
                SalamanderHunting salamanderType = getSalamander(config);
                if (salamanderType == null) {
                    return;
                }

                // Walk to area if not nearby
                if (!isNearSalamanderArea(salamanderType)) {
                    Microbot.log("Walking to " + salamanderType.getName() + " hunting area");
                    Rs2Walker.walkTo(salamanderType.getHuntingPoint());
                    return;
                }

                // Count existing traps from plugin's trap map
                int activeTrapCount = plugin.getTraps().size();
                int maxTraps = getMaxTrapsForHunterLevel(config);

                // Tend to active traps
                boolean handledTrap = handleExistingTraps(plugin, config);
                if (handledTrap) return;

                // Set new traps if we have space and supplies
                if (activeTrapCount < maxTraps && !IsRopeOnTheGround()) {
                    setNewTrap(salamanderType, config);
                }

            } catch (Exception ex) {
                System.out.println("Salamander Script Error: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private SalamanderHunting getSalamander(SalamanderConfig config) {
        if (config.progressiveHunting()) {
            return getBestSalamander();
        }

        return config.salamanderHunting();
    }

    private SalamanderHunting getBestSalamander() {
        var skillLevel = Microbot.getClient().getRealSkillLevel(Skill.HUNTER);
        if (skillLevel > 78) {
            return SalamanderHunting.TECU;
        } else if (skillLevel > 66) {
            return SalamanderHunting.BLACK;
        } else if (skillLevel > 58) {
            return SalamanderHunting.RED;
        } else if (skillLevel > 46) {
            return SalamanderHunting.ORANGE;
        } else if (skillLevel > 28) {
            return SalamanderHunting.GREEN;
        }
        Microbot.log("Not high enough hunter level for any salamander");
        shutdown();
        return null;
    }

    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.actionCooldownChance = 0.1;
    }

    private void handleBanking(SalamanderConfig config) {
        Rs2Bank.walkToBank();
        Rs2Bank.openBank();
        Rs2Bank.withdrawX(ROPE, config.withdrawNumber());
        Rs2Bank.withdrawX(SMALL_FISHING_NET, config.withdrawNumber());
        Rs2Bank.closeBank();
        hasDied = false;
    }

    private boolean isNearSalamanderArea(SalamanderHunting salamanderType) {
        // Check if within ~20 tiles of the hunting point
        WorldPoint currentLocation = Rs2Player.getWorldLocation();
        return currentLocation.distanceTo(salamanderType.getHuntingPoint()) <= 20;
    }

    public int getMaxTrapsForHunterLevel(SalamanderConfig config) {
        int hunterLevel = Microbot.getClient().getRealSkillLevel(Skill.HUNTER);
        int base = 0;
        // In the wilderness we get +1 trap
        if (config.salamanderHunting() != null && config.salamanderHunting().getName().equals("Black salamander")) {
            base = 1;
        }
        if (hunterLevel >= 80) return 5 + base;
        if (hunterLevel >= 60) return 4 + base;
        if (hunterLevel >= 40) return 3 + base;
        if (hunterLevel >= 20) return 2 + base;
        return 1;
    }

    private boolean handleExistingTraps(SalamanderPlugin plugin, SalamanderConfig config) {
        // Filter for FULL traps and sort by time (traps about to collapse first) and then pick the first one
        var trapToHandle = plugin.getTraps().entrySet().stream()
                .filter(entry -> entry.getValue().getState() == HunterTrap.State.FULL)
                .sorted((a, b) -> Double.compare(b.getValue().getTrapTimeRelative(), a.getValue().getTrapTimeRelative())).collect(Collectors.toList()).stream().findFirst().orElse(null);
        if (trapToHandle == null) return false;
        WorldPoint location = trapToHandle.getKey();
        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
            var gameObject = Rs2GameObject.getGameObject(location);
            if (gameObject != null) {
                Rs2GameObject.interact(gameObject, "Reset");
                SalamandersCaught++;
                sleep(config.minSleepAfterCatch(), config.maxSleepAfterCatch());
                return true;
            }
        }
        return false;
    }

    private void setNewTrap(SalamanderHunting salamanderType, SalamanderConfig config) {
        if (Rs2GameObject.exists(salamanderType.getTreeId())) {
            Rs2GameObject.interact(salamanderType.getTreeId(), "Set-trap");
            sleep(config.minSleepAfterLay(), config.maxSleepAfterLay());
        }
    }

    public boolean IsRopeOnTheGround() {
        return Rs2GroundItem.exists(ROPE, 7) || Rs2GroundItem.exists(303, 7);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        SalamandersCaught = 0;
    }
}
