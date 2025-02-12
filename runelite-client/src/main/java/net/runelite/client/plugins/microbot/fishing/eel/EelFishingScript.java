package net.runelite.client.plugins.microbot.fishing.eel;

import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.client.game.FishingSpot;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.fishing.eel.enums.EelFishingSpot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.npc.Rs2Npc.validateInteractable;

public class EelFishingScript extends Script {

    public static String version = "1.1.0";
    private EelFishingConfig config;

    public static boolean hasRequiredGloves() {
        return Rs2Equipment.isWearing(ItemID.ICE_GLOVES) || Rs2Equipment.isWearing(ItemID.SMITHS_GLOVES_I);
    }

    public boolean run(EelFishingConfig config) {
        this.config = config;
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run() || !Microbot.isLoggedIn() || !Rs2Inventory.hasItem("bait") || !Rs2Inventory.hasItem("rod")) {
                return;
            }

            if (Rs2AntibanSettings.actionCooldownActive)
                return;

//            if (Rs2Player.isInteracting())
//                return;

            if (config.fishingSpot().equals(EelFishingSpot.INFERNAL_EEL) && !hasRequiredGloves()) {
                Microbot.log("You need ice gloves to fish infernal eels.");
                return;
            }

            if (Rs2Inventory.isFull()) {
                processEels(config);
                return;
            }

            var fishingspot = findFishingSpot();
            if (fishingspot == null) {
                return;
            }

            if (!Rs2Camera.isTileOnScreen(fishingspot.getLocalLocation())) {
                validateInteractable(fishingspot);
            }

            if (Rs2Npc.interact(fishingspot)) {
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }


        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    public void onGameTick() {

    }

    private NPC findFishingSpot() {
        for (int fishingSpotId : getFishingSpotIds(config.fishingSpot())) {
            NPC fishingspot = Rs2Npc.getNpc(fishingSpotId);
            if (fishingspot != null) {
                return fishingspot;
            }
        }
        return null;
    }

    private int[] getFishingSpotIds(EelFishingSpot spot) {
        switch (spot) {
            case INFERNAL_EEL:
                return FishingSpot.INFERNAL_EEL.getIds();
            case SACRED_EEL:
                return FishingSpot.SACRED_EEL.getIds();
            default:
                return new int[0];
        }
    }

    private void processEels(EelFishingConfig config) {
        if (config.fishingSpot() == EelFishingSpot.INFERNAL_EEL) {
            if (Rs2Inventory.hasItem(ItemID.HAMMER)) {
                if (config.useFastCombination()) {
                    Rs2Antiban.setActivityIntensity(ActivityIntensity.EXTREME);
                    while (Rs2Inventory.hasItem("Infernal eel")) {
                        Rs2Inventory.combineClosest("Infernal eel", "Hammer");
                    }
                    return;
                }
                Rs2Inventory.combineClosest("Infernal eel", "Hammer");
                sleepUntil(() -> !Rs2Inventory.hasItem("Infernal eel"), 50000); // Wait until all eels are processed
            }
        } else if (config.fishingSpot() == EelFishingSpot.SACRED_EEL) {
            if (Rs2Inventory.hasItem(ItemID.KNIFE)) {
                Rs2Inventory.combineClosest("Sacred eel", "Knife");
                sleepUntil(() -> !Rs2Inventory.hasItem("Sacred eel"), 50000); // Wait until all eels are processed
            }
        }
        Rs2Antiban.takeMicroBreakByChance();
    }

    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
