package net.runelite.client.plugins.microbot.gabplugs.sandminer;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.magic.Rs2Spellbook;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.gabplugs.sandminer.GabulhasSandMinerInfo.botStatus;
import static net.runelite.client.plugins.microbot.gabplugs.sandminer.GabulhasSandMinerInfo.states;

@Slf4j
public class GabulhasSandMinerScript extends Script {
    public static String version = "1.2";
    private final WorldPoint miningPoint = new WorldPoint(3165, 2905, 0);
    private final WorldPoint grinder = new WorldPoint(3150, 2908, 0);

    public boolean run(GabulhasSandMinerConfig config) {
        Microbot.enableAutoRunOn = false;
        if (config.turboMode()){
            Rs2Camera.setZoom(Rs2Random.randomGaussian(100, 20));
            Rs2Camera.setYaw((Rs2Random.dicePercentage(50)? Rs2Random.randomGaussian(750, 50) : Rs2Random.randomGaussian(1700, 50)));
            Rs2Camera.setPitch(Rs2Random.betweenInclusive(418, 512));
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                switch (botStatus) {
                    case Mining:
                        if (config.useHumidify()) humidifyIfNeeded();
                        miningLoop(config);
                        handleSafety();
                        botStatus = states.Depositing;
                        break;
                    case Depositing:
                        deposit(config);
                        botStatus = states.Mining;
                        break;
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleSafety() {
        var health = Rs2Player.getHealthPercentage();
        if (health < 25) {
            Microbot.log("Health is low: " + health + "%, banking for safety.");
            Rs2Bank.walkToBank();
            Rs2Player.logout();
            Microbot.stopPlugin(GabulhasSandMinerPlugin.class);
        }
    }

    @Override
    public void shutdown() {
        super.shutdown();
    }


    private void miningLoop(GabulhasSandMinerConfig config) {
        boolean firstRock = true;
        if (!config.turboMode()){
            Rs2Walker.walkTo(miningPoint, 0);
            sleep(100, 4000);
        }
        while (!Rs2Inventory.isFull() && super.isRunning()) {
            // Drop empty waterskins if not using humidify (only when idle)
            if (!config.useHumidify() && !Rs2Player.isAnimating()) {
                dropEmptyWaterskins();
            }
            
            while (Rs2Player.hopIfPlayerDetected(1, 3000, 100) && super.isRunning()) {
                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING);
                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);
                sleep(1200, 2000);
            }

            if (!config.turboMode()) sleep(Rs2Random.nextInt(300, 5000, 0.1, true));
            if (!Rs2Player.isInteracting() || !Rs2Player.isAnimating()) {
                if (config.turboMode()) {
                    if (firstRock) {
                        WorldPoint innerMiningPoint = (Rs2Random.dicePercentage(50)) ?
                                new WorldPoint(3164, 2905, 0) : new WorldPoint(3166, 2905, 0);
                        GameObject innerSandstoneRock = Rs2GameObject.getGameObject("Sandstone rocks", true, innerMiningPoint);
                        Rs2GameObject.interact(innerSandstoneRock, "Mine");
                        Rs2Player.waitForXpDrop(Skill.MINING, 15000);
                        Rs2Antiban.actionCooldown();
                        firstRock = false;
                        continue;
                    }
                }
                GameObject sandstoneRock = Rs2GameObject.getGameObject("Sandstone rocks", true, miningPoint, 5);
                if (sandstoneRock != null) {
                    Rs2GameObject.interact(sandstoneRock, "Mine");
                    if(config.turboMode()) {
                        Rs2Player.waitForXpDrop(Skill.MINING, 15000);
                    } else {
                        Rs2Player.waitForAnimation();
                    }
                    Rs2Antiban.actionCooldown();
                }
            }
        }
    }

    private void humidifyIfNeeded() {
        if (Rs2Equipment.isWearing("Circlet of water")) {
            return;
        }
        if (Rs2Magic.isSpellbook(Rs2Spellbook.LUNAR) && !Rs2Inventory.hasItem(ItemID.WATER_SKIN1, ItemID.WATER_SKIN2, ItemID.WATER_SKIN3, ItemID.WATER_SKIN4)) {
            System.out.println("Humidified");
            Rs2Magic.cast(MagicAction.HUMIDIFY);
            sleepUntilOnClientThread(() -> Rs2Inventory.hasItem("Waterskin(4)"));
            Rs2Antiban.actionCooldown();
            Rs2Antiban.takeMicroBreakByChance();
            sleep(1000, 2000);
        }
    }

    private void dropEmptyWaterskins() {
        while (Rs2Inventory.hasItem(ItemID.WATER_SKIN0)) {
            Rs2Inventory.drop(ItemID.WATER_SKIN0);
        }
    }

    private void deposit(GabulhasSandMinerConfig config) {
        if (!config.turboMode()) Rs2Walker.walkTo(grinder);
        GameObject sandstoneRock = Rs2GameObject.findObject(26199, grinder);
        Rs2GameObject.interact(sandstoneRock, "Deposit");
        while (Rs2Inventory.contains("Sandstone (1kg)", "Sandstone (2kg)", "Sandstone (5kg)", "Sandstone (10kg)") && super.isRunning()) {
            if (!config.turboMode()) {
                sleep(100, 3000);
            }else{
                sleepGaussian(300, 200);
            }
        }
    }
}

