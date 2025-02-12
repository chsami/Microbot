package net.runelite.client.plugins.microbot.fishing.aerial;

import net.runelite.api.ItemID;
import net.runelite.api.NPC;
import net.runelite.api.NpcID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.npc.Rs2Npc.validateInteractable;

public class AerialFishingScript extends Script {
    public static final String version = "1.1.0";
    public static int timeout = 0;
    public static final WorldPoint FISHING_SPOT = new WorldPoint(1376, 3629, 0);
    public boolean run(AerialFishingConfig config) {
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyFishingSetup();
        Rs2AntibanSettings.actionCooldownChance = 0.14;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.takeMicroBreaks = true;
        Rs2AntibanSettings.microBreakChance = 0.01;
        Rs2AntibanSettings.microBreakDurationLow = 1;
        Rs2AntibanSettings.microBreakDurationHigh = 5;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run() || !Microbot.isLoggedIn() || !Rs2Inventory.hasItem("fish chunks","king worm") || (!Rs2Equipment.isWearing(ItemID.CORMORANTS_GLOVE)&&!Rs2Equipment.isWearing(ItemID.CORMORANTS_GLOVE_22817))) {
                return;
            }

            if (Rs2AntibanSettings.actionCooldownActive) return;


            if (Rs2Inventory.isFull() || (!Rs2Inventory.hasItem(ItemID.MOLCH_PEARL) && Rs2Inventory.getEmptySlots() == 1)) {
                cutFish();
                return;
            }

            if(!Rs2Player.getWorldLocation().equals(FISHING_SPOT)) {
                Rs2Walker.walkTo(FISHING_SPOT,0);
            }


            NPC fishingspot = findFishingSpot();
            if (fishingspot == null) {
                return;
            }
            if(Rs2Player.isInteracting()) {
                return;
            }

            if (!Rs2Camera.isTileOnScreen(fishingspot.getLocalLocation())) {
                validateInteractable(fishingspot);
            }

            if (Rs2Npc.interact(fishingspot)) {
                if(sleepUntil(Rs2Player::isInteracting,1200)) {
                    sleepUntil(() -> Rs2Equipment.isWearing(ItemID.CORMORANTS_GLOVE_22817), () -> {
                        if((Rs2Inventory.getEmptySlots() <= 1 && Rs2Equipment.isWearing(ItemID.CORMORANTS_GLOVE)) || (Rs2Inventory.getEmptySlots() == 0 && Rs2Equipment.isWearing(ItemID.CORMORANTS_GLOVE_22817))) {
                            Microbot.log("Empty slot count:" + Rs2Inventory.getEmptySlots());
                            Rs2ItemModel knife = Rs2Inventory.get(ItemID.KNIFE);
                            Rs2Inventory.hover(knife);

                        }
                        else {
                            NPC preHoverSpot = findPreHoverSpot(fishingspot);
                            if (preHoverSpot != null) {
                               if (Rs2Npc.hoverOverActor(preHoverSpot)){

                                   if(Rs2Random.dicePercentage(20)){
                                       Microbot.getMouse().click();
                                   }
                               }
                            }
                        }
                    }, 5000, 100);
                    Rs2Antiban.actionCooldown();
                    Rs2Antiban.takeMicroBreakByChance();
                }
            }

        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }



    private NPC findFishingSpot() {
        return Rs2Npc.getNpc(NpcID.FISHING_SPOT_8523);
    }

    private NPC findPreHoverSpot(NPC exludedSpot) {
        return Rs2Npc.getNpcs(NpcID.FISHING_SPOT_8523).filter(x -> x != exludedSpot).findFirst().orElse(null);
    }

    private void cutFish() {
        Rs2ItemModel randomFish = Rs2Inventory.getRandom(ItemID.BLUEGILL,ItemID.COMMON_TENCH,ItemID.MOTTLED_EEL,ItemID.GREATER_SIREN);
        Rs2ItemModel knife = Rs2Inventory.get(ItemID.KNIFE);
        Rs2Inventory.combine(knife,randomFish);
        sleepUntil(() -> !Rs2Inventory.hasItem(ItemID.BLUEGILL,ItemID.COMMON_TENCH,ItemID.MOTTLED_EEL,ItemID.GREATER_SIREN),1000*60);
        Rs2Random.waitEx(2000,2000);
    }


    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        super.shutdown();
    }
}
