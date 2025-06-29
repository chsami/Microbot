package net.runelite.client.plugins.microbot.TaF.VolcanicAshMiner;

import net.runelite.api.GameObject;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.mining.MiningAnimation;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity.VERY_LOW;

public class VolcanicAshMinerScript extends Script {
    public static final String VERSION = "1.0";
    public static VolcanicAshMinerState BOT_STATUS = VolcanicAshMinerState.MINING;
    private final WorldPoint VOLCANIC_ASH_LOCATION = new WorldPoint(3790, 3770, 0);

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

    public boolean run(VolcanicAshMinerConfig config) {
        BOT_STATUS = VolcanicAshMinerState.MINING;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                handleMining(config);
            } catch (Exception ex) {
                System.out.println("Exception message: " + ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleMining(VolcanicAshMinerConfig config) {
        if (Rs2Inventory.isFull()) {
            // Just for visual feedback in the overlay
            BOT_STATUS = VolcanicAshMinerState.DROPPING;
            Rs2Inventory.dropAll("soda ash");
            BOT_STATUS = VolcanicAshMinerState.MINING;
        }
        if (Rs2Player.distanceTo(VOLCANIC_ASH_LOCATION) > 15) {
            Rs2Walker.walkTo(VOLCANIC_ASH_LOCATION);
        }

        if (hopIfTooManyPlayersNearby(config)) return; // Exit current cycle after hop

        if (Rs2Equipment.isWearing("Dragon pickaxe") || Rs2Equipment.isWearing("Crystal pickaxe")) {
            Rs2Combat.setSpecState(true, 1000);
            return;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return;
        }

        GameObject rock = Rs2GameObject.findReachableObject("Ash pile", true, 12, VOLCANIC_ASH_LOCATION);
        if (rock != null) {
            if (Rs2GameObject.interact(rock)) {
                Rs2Player.waitForXpDrop(Skill.MINING, true);
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
        } else {
            Microbot.log("No Ash pile found. Waiting...");
        }
    }

    private boolean hopIfTooManyPlayersNearby(VolcanicAshMinerConfig config) {
        int maxPlayers = config.maxPlayersInArea();
        if (maxPlayers > 0) {
            WorldPoint localLocation = Rs2Player.getWorldLocation();

            long nearbyPlayers = Microbot.getClient().getPlayers().stream()
                    .filter(p -> p != null && p != Microbot.getClient().getLocalPlayer())
                    .filter(p -> {
                        return p.getWorldLocation().distanceTo(localLocation) <= 15;
                    })
                    //filter if players are using mining animation
                    .filter(p -> MiningAnimation.MINING_ANIMATIONS.contains(p))
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

    public enum VolcanicAshMinerState {DROPPING, MINING}

}
