package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;


public class TemporossWorkArea
{
    public final WorldPoint exitNpc;
    public final WorldPoint safePoint;
    public final WorldPoint bucketPoint;
    public final WorldPoint pumpPoint;
    public final WorldPoint ropePoint;
    public final WorldPoint hammerPoint;
    public final WorldPoint harpoonPoint;
    public final WorldPoint mastPoint;
    public final WorldPoint totemPoint;
    public final WorldPoint rangePoint;
    public final WorldPoint spiritPoolPoint;
    public final WorldPoint dockPumpPoint;
    public final WorldPoint dockBucketPoint;

    public TemporossWorkArea(WorldPoint exitNpc, boolean isWest)
    {
        this.exitNpc = exitNpc;

        if (isWest)
        {
            this.safePoint = exitNpc.dx(1).dy(2);  // West side safe point
            this.bucketPoint = exitNpc.dx(-3).dy(-1);
            this.pumpPoint = exitNpc.dx(-3).dy(-2);
            this.ropePoint = exitNpc.dx(-3).dy(-5);
            this.hammerPoint = exitNpc.dx(-3).dy(-6);
            this.harpoonPoint = exitNpc.dx(-2).dy(-7);
            this.mastPoint = exitNpc.dx(0).dy(-3);
            this.totemPoint = exitNpc.dx(8).dy(15);
            this.rangePoint = exitNpc.dx(3).dy(21);
            this.spiritPoolPoint = exitNpc.dx(11).dy(4);
            this.dockPumpPoint = exitNpc.dx(12).dy(6);    // TEMPOROSS_WATER_PUMP_DOCK
            this.dockBucketPoint = exitNpc.dx(12).dy(7);  // TEMPOROSS_CRATE_BUCKET
        }
        else
        {
            this.safePoint = exitNpc.dx(-1).dy(-2); // East side safe point
            this.bucketPoint = exitNpc.dx(3).dy(1);
            this.pumpPoint = exitNpc.dx(3).dy(2);
            this.ropePoint = exitNpc.dx(3).dy(5);
            this.hammerPoint = exitNpc.dx(3).dy(6);
            this.harpoonPoint = exitNpc.dx(2).dy(7);
            this.mastPoint = exitNpc.dx(0).dy(3);
            this.totemPoint = exitNpc.dx(-15).dy(-13);
            this.rangePoint = exitNpc.dx(-23).dy(-19);
            this.spiritPoolPoint = exitNpc.dx(-11).dy(-4);
            this.dockPumpPoint = exitNpc.dx(-12).dy(-6);    // TEMPOROSS_WATER_PUMP_DOCK
            this.dockBucketPoint = exitNpc.dx(-12).dy(-7);  // TEMPOROSS_CRATE_BUCKET
        }
    }

    public TileObject getBucketCrate()
    {
        return Rs2GameObject.findObject(ObjectID.TEMPOROSS_CRATE_BUCKET, bucketPoint);
    }

    public TileObject getPump()
    {
        return Rs2GameObject.findObject(ObjectID.TEMPOROSS_WATER_PUMP, pumpPoint);
    }

    public TileObject getRopeCrate()
    {
        return Rs2GameObject.findObject(ObjectID.TEMPOROSS_CRATE_ROPE, ropePoint);
    }

    public TileObject getHammerCrate()
    {
        return Rs2GameObject.findObject(ObjectID.TEMPOROSS_CRATE_HAMMER, hammerPoint);
    }

    public TileObject getHarpoonCrate()
    {
        return Rs2GameObject.findObject(ObjectID.TEMPOROSS_CRATE_HARPOON, harpoonPoint);
    }

    public TileObject getDockPump()
    {
        return Rs2GameObject.findObject(ObjectID.TEMPOROSS_WATER_PUMP_DOCK, dockPumpPoint);
    }

    public TileObject getDockBucketCrate()
    {
        return Rs2GameObject.findObject(ObjectID.TEMPOROSS_CRATE_BUCKET, dockBucketPoint);
    }

    public TileObject getMast() {
        //WorldPoint localInstance = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(),mastPoint).stream().findFirst().orElse(null);
    TileObject mast = Rs2GameObject.findGameObjectByLocation(mastPoint);
    if (mast != null && (mast.getId() == ObjectID.TEMPOROSS_MAST_BOTTOM_WEST || mast.getId() == ObjectID.TEMPOROSS_MAST_BOTTOM_EAST)) {
        return mast;
    }
    return null;
}

    public TileObject getBrokenMast() {
        // First check if we have a cached broken mast
        if (TemporossScript.cachedBrokenMast != null) {
            return TemporossScript.cachedBrokenMast;
        }
        
        // Fallback to the original method if cache is empty
        TileObject mast = Rs2GameObject.findGameObjectByLocation(mastPoint);
        if (mast != null && (mast.getId() == ObjectID.TEMPOROSS_MAST_BOTTOM_WEST_BROKEN || mast.getId() == ObjectID.TEMPOROSS_MAST_BOTTOM_EAST_BROKEN)) {
            // Update the cache
            TemporossScript.cachedBrokenMast = mast;
            return mast;
        }

        return null;
    }

    public TileObject getTotem() {
        //WorldPoint localInstance = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(),totemPoint).stream().findFirst().orElse(null);
        TileObject totem = Rs2GameObject.findGameObjectByLocation(totemPoint);
    if (totem != null && (totem.getId() == ObjectID.TEMPOROSS_TOTEM_SOUTH || totem.getId() == ObjectID.TEMPOROSS_TOTEM_NORTH)) {
        return totem;
    }
    return null;
}

    public TileObject getBrokenTotem() {
        // First check if we have a cached broken totem
        if (TemporossScript.cachedBrokenTotem != null) {
            return TemporossScript.cachedBrokenTotem;
        }
        
        // Fallback to the original method if cache is empty
        TileObject totem = Rs2GameObject.findGameObjectByLocation(totemPoint);
        if (totem != null && (totem.getId() == ObjectID.TEMPOROSS_TOTEM_NORTH_BROKEN || totem.getId() == ObjectID.TEMPOROSS_TOTEM_SOUTH_BROKEN)) {
            // Update the cache
            TemporossScript.cachedBrokenTotem = totem;
            return totem;
        }

        return null;
    }

    public TileObject getRange()
    {
        //WorldPoint localInstance = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(),rangePoint).stream().findFirst().orElse(null);
        return Rs2GameObject.findObject(ObjectID.TEMPOROSS_SHRINE_FIRE, rangePoint);
    }


    public TileObject getClosestTether() {
    TileObject mast = getMast();
    TileObject totem = getTotem();

    if (mast == null) {
        return totem;
    }

    if (totem == null) {
        return mast;
    }

    Rs2WorldPoint mastLocation = new Rs2WorldPoint(mast.getWorldLocation());
    Rs2WorldPoint totemLocation = new Rs2WorldPoint(totem.getWorldLocation());
    Rs2WorldPoint playerLocation = new Rs2WorldPoint(Microbot.getClient().getLocalPlayer().getWorldLocation());

    return mastLocation.distanceToPath(playerLocation.getWorldPoint()) <
            totemLocation.distanceToPath(playerLocation.getWorldPoint()) ? mast : totem;
}

    public String getAllPointsAsString() {
        String sb = "exitNpc=" + exitNpc +
                ", safePoint=" + safePoint +
                ", bucketPoint=" + bucketPoint +
                ", pumpPoint=" + pumpPoint +
                ", ropePoint=" + ropePoint +
                ", hammerPoint=" + hammerPoint +
                ", harpoonPoint=" + harpoonPoint +
                ", mastPoint=" + mastPoint +
                ", totemPoint=" + totemPoint +
                ", rangePoint=" + rangePoint +
                ", spiritPoolPoint=" + spiritPoolPoint;

        return sb;
    }
}
