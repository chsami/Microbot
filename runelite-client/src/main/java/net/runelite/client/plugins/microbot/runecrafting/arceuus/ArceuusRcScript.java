package net.runelite.client.plugins.microbot.runecrafting.arceuus;

import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

public class ArceuusRcScript extends Script {

    public static String version = "1.0.0";
    public static int darkAltarTripCount = 0;
    private final int BLOOD_ESSENCE_ACTIVE = ItemID.BLOOD_ESSENCE_ACTIVE;
    private final int BLOOD_ESSENCE = ItemID.BLOOD_ESSENCE;
    public static final WorldPoint ARCEUUS_BLOOD_ALTAR = new WorldPoint(1720, 3828, 0);
    public static final WorldPoint ARCEUUS_DARK_ALTAR = new WorldPoint(1718, 3880, 0);
    public static final WorldPoint DENSE_RUNESTONE = new WorldPoint(1760, 3853, 0);

    public boolean run(ArceuusRcConfig config) {
        Rs2Antiban.antibanSetupTemplates.applyUniversalAntibanSetup();
        if(Microbot.isLoggedIn()) {
            if(Rs2Inventory.hasItem(ItemID.DARK_ESSENCE_FRAGMENTS)) {
                darkAltarTripCount++;
                if(Rs2Inventory.hasItem(ItemID.DARK_ESSENCE_BLOCK)) {
                    darkAltarTripCount++;
                }
            }
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::executeTask, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void executeTask() {
        try {
            if (!super.run() || !Microbot.isLoggedIn()) {
                return;
            }
            if (shouldGoToBloodAltar()) {
                goToBloodAltar();
            } else if (shouldUseBloodAltar()) {
                useBloodAltar();
            } else if (shouldGoToDarkAltar()) {
                goToDarkAltar();
            } else if (shouldUseDarkAltar()) {
                useDarkAltar();
            } else if (shouldGoToRunestone()) {
                goToRunestone();
            } else if (shouldChipEssence()) {
                chipEssence();
            } else if (shouldMineEssence()) {
                mineEssence();
            }
        } catch (Exception e) {
            Microbot.log("Error in Arceuus Runecrafter: " + e.getMessage());
        }
    }

    public boolean shouldGoToBloodAltar() {
        return darkAltarTripCount >= 2
                && Rs2Inventory.hasItem(ItemID.DARK_ESSENCE_FRAGMENTS)
                && Rs2Player.getWorldLocation().distanceTo(ARCEUUS_BLOOD_ALTAR) > 10;
    }

    public void goToBloodAltar() {
        Rs2Walker.walkTo(ARCEUUS_BLOOD_ALTAR);
    }

    public boolean shouldGoToDarkAltar() {
        return Rs2Inventory.isFull()
                && Rs2Inventory.hasItem(ItemID.DENSE_ESSENCE_BLOCK)
                && Rs2Player.getWorldLocation().distanceTo(ARCEUUS_DARK_ALTAR) > 10
                && darkAltarTripCount < 2;
    }

    public void goToDarkAltar() {
        Rs2Walker.walkTo(ARCEUUS_DARK_ALTAR);
    }

    public boolean shouldGoToRunestone() {
        return !Rs2Inventory.isFull()
                && Rs2Player.getWorldLocation().distanceTo(DENSE_RUNESTONE) > 8
                && darkAltarTripCount < 2
                && !(Rs2Player.getWorldLocation().distanceTo(ARCEUUS_BLOOD_ALTAR) < 5
                    && Rs2Inventory.hasItem(ItemID.DARK_ESSENCE_BLOCK,ItemID.DARK_ESSENCE_FRAGMENTS));
    }

    public void goToRunestone() {
        Rs2Walker.walkTo(DENSE_RUNESTONE);
    }

    public boolean shouldUseBloodAltar() {
        return  Rs2Inventory.hasItem(ItemID.DARK_ESSENCE_FRAGMENTS)
                && Rs2GameObject.findObject("Blood Altar", true,10,false,Rs2Player.getWorldLocation()) != null;
    }

    public void useBloodAltar() {
        GameObject bloodAltar = Rs2GameObject.findObject("Blood Altar", true,10,false,Rs2Player.getWorldLocation());
        if (bloodAltar != null) {
            if (Rs2GameObject.interact(bloodAltar,"Bind"))
                Rs2Inventory.waitForInventoryChanges(6000);
            darkAltarTripCount = 0;

        }
    }

    public boolean shouldChipEssence() {
        return Rs2Inventory.isFull() && !Rs2Inventory.hasItem(ItemID.DARK_ESSENCE_FRAGMENTS) && Rs2Inventory.hasItem(ItemID.DARK_ESSENCE_BLOCK)
                || (!Rs2Inventory.hasItem(ItemID.DARK_ESSENCE_FRAGMENTS)
                    && Rs2Inventory.hasItem(ItemID.DARK_ESSENCE_BLOCK)
                    && Rs2GameObject.findObject("Blood Altar", true,10,false,Rs2Player.getWorldLocation()) != null);
    }

    public void chipEssence() {
        Rs2ItemModel chisel = Rs2Inventory.get(ItemID.CHISEL);
        if (Rs2Inventory.moveItemToSlot(chisel,27)) {
            sleepUntil(()->Rs2Inventory.slotContains(27,chisel.id),6000);
        }
        if(Rs2Inventory.combineClosest(ItemID.DARK_ESSENCE_BLOCK,ItemID.CHISEL))
            sleepUntil(()->!Rs2Inventory.hasItem(ItemID.DARK_ESSENCE_BLOCK),60000);
    }

    public boolean shouldUseDarkAltar() {
        GameObject darkAltar = Rs2GameObject.findObject("Dark altar", true,10,false,Rs2Player.getWorldLocation());
        return Rs2Inventory.isFull()
                && Rs2Inventory.hasItem(ItemID.DENSE_ESSENCE_BLOCK)
                && darkAltar != null;
    }

    public void useDarkAltar() {
        GameObject darkAltar = Rs2GameObject.findObject("Dark altar", true,10,false,Rs2Player.getWorldLocation());
        if (darkAltar != null) {
            Rs2GameObject.interact(darkAltar,"Venerate");
            sleepUntil(()->!Rs2Inventory.hasItem(ItemID.DENSE_ESSENCE_BLOCK),6000);
            darkAltarTripCount++;
        }
    }

    public void mineEssence() {
        if(!Rs2Inventory.hasItem(BLOOD_ESSENCE_ACTIVE)){
            Rs2Inventory.interact(BLOOD_ESSENCE, "Activate");
        }
        GameObject runeStone = Rs2GameObject.findObject("Dense runestone", true,11,false,Rs2Player.getWorldLocation());
        if (runeStone != null) {
            Rs2GameObject.interact(runeStone,"Chip");
            Rs2Player.waitForAnimation(10000);
        }
    }
    public static boolean shouldMineEssence() {
        GameObject runeStone = Rs2GameObject.findObject("Dense runestone", true,11,false,Rs2Player.getWorldLocation());
        return !Rs2Inventory.hasItem(ItemID.DARK_ESSENCE_BLOCK)
                && !Rs2Inventory.isFull()
                && !Rs2Player.isAnimating()
                && runeStone != null;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        darkAltarTripCount = 0;
    }
}
