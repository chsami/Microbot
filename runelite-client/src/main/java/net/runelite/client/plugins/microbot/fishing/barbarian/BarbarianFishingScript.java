package net.runelite.client.plugins.microbot.fishing.barbarian;

import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.npc.Rs2Npc.validateInteractable;

public class BarbarianFishingScript extends Script {
    private long specReadyTime = 0;
    private boolean specActivated = false;
    public static String version = "1.1.3";
    public static int timeout = 0;
    private BarbarianFishingConfig config;

    public boolean run(BarbarianFishingConfig config) {
        this.config = config;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run() || !Microbot.isLoggedIn() || !Rs2Inventory.hasItem("feather") || !Rs2Inventory.hasItem("rod")) {
                return;
            }

            if (Rs2Equipment.hasEquipped(ItemID.DRAGON_HARPOON)) {
                if (Rs2Combat.getSpecEnergy() == 1000) {
                    if (specReadyTime == 0) {
                        double delay = Rs2Random.gaussRand(45000, 30000); // Delay in ms (mean 1200, stddev 200)
                        specReadyTime = System.currentTimeMillis() + (long) delay;
                    } else if (!specActivated && System.currentTimeMillis() >= specReadyTime) {
                        Rs2Combat.setSpecState(true);
                        specActivated = true;
                    }
                } else {
                    specReadyTime = 0;
                    specActivated = false;
                }
            }

            if (Rs2AntibanSettings.actionCooldownActive) return;

            if (Rs2Player.isInteracting())
                return;

            if (Rs2Inventory.isFull()) {
                dropInventoryItems(config);
                return;
            }

            Rs2NpcModel fishingspot = findFishingSpot();
            if (fishingspot == null) {
                return;
            }

            if (!Rs2Camera.isTileOnScreen(fishingspot.getLocalLocation())) {
                validateInteractable(fishingspot);
            }

            if(Rs2Npc.interact(fishingspot, "Use-rod")) {
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            };

        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public void onGameTick() {

    }

    private Rs2NpcModel findFishingSpot() {
        for (int fishingSpotId : FishingSpot.BARB_FISH.getIds()) {
            Rs2NpcModel fishingSpot = Rs2Npc.getNpc(fishingSpotId);
            if (fishingSpot != null) {
                return fishingSpot;
            }
        }
        return null;
    }

    private void dropInventoryItems(BarbarianFishingConfig config) {
        InteractOrder dropOrder = config.dropOrder() == InteractOrder.RANDOM ? InteractOrder.random() : config.dropOrder();
        Rs2Inventory.dropAll(x -> x.name.toLowerCase().contains("leaping"), dropOrder);
    }

    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}