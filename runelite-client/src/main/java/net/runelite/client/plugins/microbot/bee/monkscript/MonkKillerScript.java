package net.runelite.client.plugins.microbot.bee.monkscript;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.bee.MossKiller.MossKillerScript;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;

import javax.inject.Inject;
import javax.swing.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.api.Skill.DEFENCE;
import static net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkTo;

public class MonkKillerScript extends Script {

    private final MonkKillerConfig config;
    private final PluginManager pluginManager;
    private final Client client;

    @Inject
    public MonkKillerScript(MonkKillerConfig config, Client client, PluginManager pluginManager) {
        this.config = config;
        this.client = client;
        this.pluginManager = pluginManager;
    }

    @Inject
    MonkKillerPlugin plugin;

    private boolean firstSetting = true;


    private final WorldArea monkArea = new WorldArea(3040, 3475, 23, 36, 0); // pseudo
    private final WorldPoint monkPoint = new WorldPoint(3052, 3491, 0); // Psuedo
    private Rs2PlayerModel localPlayer;

    public boolean run(MonkKillerConfig config) {
        Microbot.enableAutoRunOn = false;
        localPlayer = Rs2Player.getLocalPlayer();
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.actionCooldownChance = 1;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.moveMouseOffScreenChance = 0.90;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.90;
        Rs2AntibanSettings.takeMicroBreaks = true;
        Rs2AntibanSettings.microBreakChance = 0.50;
        Rs2AntibanSettings.microBreakDurationLow = 5;
        Rs2AntibanSettings.microBreakDurationHigh = 15;
        Rs2AntibanSettings.dynamicActivity = false;
        Rs2AntibanSettings.randomIntervals = true;
        Rs2Antiban.setActivityIntensity(ActivityIntensity.VERY_LOW);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (BreakHandlerScript.breakIn <= 60) {
                    Microbot.log("Break in less than 60 seconds, walking outside of monk's sanctuary");
                walkTo(3051,3470,0);
                Microbot.log("Turn on breakhandler if this is not the desired behaviour");}


                if (firstSetting && Rs2Player.getRealSkillLevel(DEFENCE) < 15) {
                    Rs2AntibanSettings.takeMicroBreaks = false;
                    firstSetting = false;
                }

                if (!firstSetting && Rs2Player.getRealSkillLevel(DEFENCE) > 15) {
                    Rs2AntibanSettings.takeMicroBreaks = true;
                    firstSetting = true;
                }

                if (Rs2Player.getRealSkillLevel(DEFENCE) >= config.defenseLevel()) {
                    JOptionPane.showMessageDialog(null, "The Script has Shut Down due to Defence Level reached");
                    MossKillerScript.stopAutologin();
                    MossKillerScript.stopBreakHandlerPlugin();
                    shutdown();
                }

                Rs2NpcModel monk = findAvailableMonk();
                if (isInMonkArea() && !underAttack()) {
                    if (monk != null) {
                            if (!attackMonk(monk)) {
                                Microbot.log("monk is not null, tried to attack the monk, it failed, just wait a bit longer for a successful attack");
                            }
                    } else {
                        Microbot.log("monk is null, sleeping a bit then if not under attack, logging out");
                        sleep(5000,15000);
                        if (!underAttack()) {hopWorld();}
                    }
                } else if (!isInMonkArea()){
                    walkToMonkArea();
                } else {
                    Rs2NpcModel monk1 = findAvailableMonk1();
                    if (monk1 != null) {
                        boolean isAttackingMe = monk1.getInteracting() != null &&
                                monk1.getInteracting().getName().equals(Microbot.getClient().getLocalPlayer().getName());
                        boolean noHealthBar = monk1.getHealthRatio() == -1;

                        if (isAttackingMe && noHealthBar) {
                            Rs2Npc.attack(monk1);
                        }
                    }
                }
                Rs2Player.eatAt(50);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 10000, TimeUnit.MILLISECONDS); // Executes every 1000 milliseconds (1 second)
        return true;
    }

    private boolean isInMonkArea() {
        // Check if player is in the defined monk area
        return monkArea.contains(localPlayer.getWorldLocation());
    }

    private void walkToMonkArea() {
        walkTo(monkPoint);  // Walk to a specific point in the monk area
    }

    private Rs2NpcModel findAvailableMonk1() {
        Rs2PlayerModel localPlayer = Rs2Player.getLocalPlayer();
        List<Rs2NpcModel> monks = Rs2Npc.getNpcs("Monk").collect(Collectors.toList());

        for (Rs2NpcModel monk : monks) {
            if (monk.getInteracting() != null &&
                    monk.getInteracting().getName().equals(localPlayer.getName())) {
                return monk;
            }
        }
        return null;
    }


    private Rs2NpcModel findAvailableMonk() {
        List<Rs2NpcModel> monks = Rs2Npc.getNpcs("Monk").collect(Collectors.toList()); // Directly collect Stream into List
        for (Rs2NpcModel monk : monks) {
            if (!monk.isDead()
                    && (monk.getInteracting() == null || monk.getInteracting() == localPlayer)
                    && monk.getAnimation() == -1 || monk.getInteracting() == localPlayer) {
                return monk;
            }
        }
        return null; // Return null if no suitable monk is found
    }

    private boolean attackMonk(Rs2NpcModel monk) {
        if (!underAttack()
                && (monk.getInteracting() != Microbot.getClient().getLocalPlayer())
        && (monk.getInteracting() == null)) {
            Rs2Npc.attack(monk);
            return true;
        }
        return false;
    }


    private boolean underAttack() {
        // Check if the player is interacting with any NPC
        return Rs2Player.isInCombat();
    }

    private void hopWorld() {
        Rs2Player.logout();
    }

    public Player getLocalPlayer() {
        Player localPlayer = Microbot.getClient().getLocalPlayer();
        if (localPlayer == null) {
            throw new IllegalStateException("Local player is not available.");
        }
        return localPlayer;
    }

    @Override
    public void shutdown() {
        firstSetting = true;
      super.shutdown();
    }

}
