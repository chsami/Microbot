package net.runelite.client.plugins.microbot.bee.monkscript;

import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Inject;
import javax.swing.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

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

    private final WorldArea monkArea = new WorldArea(3040, 3475, 23, 36, 0);
    private final WorldPoint monkPoint = new WorldPoint(3052, 3491, 0);

    public boolean run(MonkKillerConfig config) {
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled() && !mainScheduledFuture.isDone()) {
            Microbot.log("Scheduled task already running.");
            return false;
        }

        Microbot.enableAutoRunOn = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2Antiban.setActivityIntensity(ActivityIntensity.LOW);
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    Microbot.log("Not logged in, skipping tick.");
                    return;}
                if (!super.run()) {Microbot.log("super.run() returned false, skipping tick.");
                    return;}
                if (Microbot.getClient() == null || Microbot.getClient().getLocalPlayer() == null) {
                    Microbot.log("Client or local player not ready. Skipping tick.");
                    return;
                }

                if (BreakHandlerScript.breakIn <= 60) {
                    Microbot.log("Break in less than 60 seconds, walking outside of monk's sanctuary");
                    walkTo(3051,3470,0);
                    Microbot.log("Turn on breakhandler if this is not desired behaviour");
                    sleep(60000);} //sleep until break

                if (Rs2Player.getRealSkillLevel(DEFENCE) >= config.defenseLevel()) {
                    JOptionPane.showMessageDialog(null, "The Script has Shut Down due to Defence Level reached");
                    shutdown();
                }

                if (isInMonkArea() && !underAttack()) {
                    Rs2NpcModel monk = findAvailableMonk();
                    if (monk != null) {
                        Microbot.log("monk is not null, entering attackMonk");
                        if (!attackMonk(monk)) {
                            Microbot.log("Failed to attack monk. Waiting...");
                        }
                    } else {
                        Microbot.log("monk is null, sleeping a bit then if not under attack, logging out");
                        sleep(5000,15000);
                        if (!underAttack()) {hopWorld();}
                    }
                } else if (!isInMonkArea()){
                    walkToMonkArea();
                } else if (underAttack() && !Rs2Player.waitForXpDrop(DEFENCE, 30000)){
                    Rs2NpcModel attackingMonk = findAttackingMonk();
                    if (attackingMonk != null) {
                        Microbot.log("attacking monk is not null");
                        boolean noHealthBar = attackingMonk.getHealthRatio() == -1;
                        Microbot.log("noHealthBar " + noHealthBar);
                        if (noHealthBar) {
                            Rs2Npc.interact(attackingMonk, "attack");
                        }
                        if (!Rs2Player.waitForXpDrop(DEFENCE, 30000) && noHealthBar) {Rs2Keyboard.enter();
                            Microbot.log("pressed enter as fall-back to stimulate auto-retaliate");};
                    } else {Microbot.log("attacking Monk is null");}
                }
                Rs2Player.eatAt(50);
            } catch (Exception ex) {
                Microbot.log("Fatal error in scheduled task: " + ex.getClass().getSimpleName() + ": " + ex.getMessage());
                System.out.println(ex.getMessage());
            }
        }, 0, 2000, TimeUnit.MILLISECONDS); // Executes every 1000 milliseconds (1 second)
        return true;
    }

    private boolean isInMonkArea() {
        Rs2PlayerModel localPlayer = Rs2Player.getLocalPlayer();
        if (localPlayer == null) {
            Microbot.log("Local player is null in isInMonkArea");
            return false;
        }
        return monkArea.contains(localPlayer.getWorldLocation());
    }

    private void walkToMonkArea() {
        walkTo(monkPoint);  // Walk to a specific point in the monk area
    }

    private Rs2NpcModel findAttackingMonk() {
        List<Rs2NpcModel> monks = Rs2Npc.getNpcsForPlayer("Monk", false);
        return monks.isEmpty() ? null : monks.get(0);
    }


    private Rs2NpcModel findAvailableMonk() {
        List<Rs2NpcModel> monks;
        Rs2PlayerModel localPlayer = Rs2Player.getLocalPlayer();

        if (localPlayer == null) {
            Microbot.log("Local player is null in findAvailableMonk.");
            return null;
        }

        try {
            Stream<Rs2NpcModel> npcStream = Rs2Npc.getNpcs("Monk");

            if (npcStream == null) {
                Microbot.log("NPC stream is null, skipping this check.");
                return null;
            }

            monks = npcStream.collect(Collectors.toList());
        } catch (Exception e) {
            Microbot.log("Error while getting monks: " + e.getClass().getSimpleName() + ": " + e.getMessage());
            return null;
        }

        for (Rs2NpcModel monk : monks) {
            if (!monk.isDead()
                    && (monk.getInteracting() == null || monk.getInteracting() == localPlayer)
                    && monk.getAnimation() == -1) {
                return monk;
            }
        }

        return null; // No monk found
    }


    private boolean attackMonk(Rs2NpcModel monk) {
        Player localPlayer = getLocalPlayer();

        if (monk == null) {
            Microbot.log("No monk found.");
            return false;
        }

        if (underAttack() || (monk.getInteracting() != null && monk.getInteracting() != localPlayer)) {
            Microbot.log("Monk is already in combat or we're under attack.");
            return false;
        }

        WorldPoint monkLocation = monk.getWorldLocation();
        WorldPoint playerLocation = localPlayer.getWorldLocation();
        int distance = monkLocation.distanceTo(playerLocation);

        // ✅ Only turn camera if monk is not visible
        if (!Rs2Camera.isTileOnScreen(monk.getLocalLocation())) {
            Rs2Camera.turnTo(monk);
        }

        if (distance > 3) {
            Microbot.log("Monk is far, walking using minimap...");
            Rs2Walker.walkMiniMap(monkLocation);
        }

        // Optional: wait until close before attacking
        sleepUntil(() -> localPlayer.getWorldLocation().distanceTo(monk.getWorldLocation()) <= 3, 3000);

        if (Rs2Npc.attack(monk)) {
            Microbot.log("Attacking monk...");
            return true;
        }

        Microbot.log("Failed to attack monk.");
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
        super.shutdown();
    }

}