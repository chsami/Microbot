package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.NullObjectID;
import net.runelite.api.ObjectID;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;


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

    public TemporossWorkArea(WorldPoint exitNpc, boolean isWest)
    {
        this.exitNpc = exitNpc;
        this.safePoint = exitNpc.dx(1).dy(1);

        if (isWest)
        {
            this.bucketPoint = exitNpc.dx(-3).dy(-1);
            this.pumpPoint = exitNpc.dx(-3).dy(-2);
            this.ropePoint = exitNpc.dx(-3).dy(-5);
            this.hammerPoint = exitNpc.dx(-3).dy(-6);
            this.harpoonPoint = exitNpc.dx(-2).dy(-7);
            this.mastPoint = exitNpc.dx(0).dy(-3);
            this.totemPoint = exitNpc.dx(8).dy(15);
            this.rangePoint = exitNpc.dx(3).dy(21);
            this.spiritPoolPoint = exitNpc.dx(11).dy(4);
        }
        else
        {
            this.bucketPoint = exitNpc.dx(3).dy(1);
            this.pumpPoint = exitNpc.dx(3).dy(2);
            this.ropePoint = exitNpc.dx(3).dy(5);
            this.hammerPoint = exitNpc.dx(3).dy(6);
            this.harpoonPoint = exitNpc.dx(2).dy(7);
            this.mastPoint = exitNpc.dx(0).dy(3);
            this.totemPoint = exitNpc.dx(-15).dy(-13);
            this.rangePoint = exitNpc.dx(-23).dy(-19);
            this.spiritPoolPoint = exitNpc.dx(-11).dy(-4);
        }
    }

    public TileObject getBucketCrate()
    {
        return Rs2GameObject.findObject(ObjectID.BUCKETS, bucketPoint);
    }

    public TileObject getPump()
    {
        return Rs2GameObject.findObject(ObjectID.WATER_PUMP_41000, pumpPoint);
    }

    public TileObject getRopeCrate()
    {
        return Rs2GameObject.findObject(ObjectID.ROPES, ropePoint);
    }

    public TileObject getHammerCrate()
    {
        return Rs2GameObject.findObject(ObjectID.HAMMERS_40964, hammerPoint);
    }

    public TileObject getHarpoonCrate()
    {
        return Rs2GameObject.findObject(ObjectID.HARPOONS, harpoonPoint);
    }

    public TileObject getMast() {
        WorldPoint localInstance = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(),mastPoint).stream().findFirst().orElse(null);
    TileObject mast = Rs2GameObject.findGameObjectByLocation(localInstance);
    if (mast != null && (mast.getId() == NullObjectID.NULL_41352 || mast.getId() == NullObjectID.NULL_41353)) {
        return mast;
    }
    return null;
}

    public TileObject getBrokenMast() {
        WorldPoint localInstance = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(),mastPoint).stream().findFirst().orElse(null);
    TileObject mast = Rs2GameObject.findGameObjectByLocation(localInstance);
    if (mast != null && (mast.getId() == ObjectID.DAMAGED_MAST_40996 || mast.getId() == ObjectID.DAMAGED_MAST_40997))
        return mast;

    return null;
    }

    public TileObject getTotem() {
        WorldPoint localInstance = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(),totemPoint).stream().findFirst().orElse(null);
        TileObject totem = Rs2GameObject.findGameObjectByLocation(localInstance);
    if (totem != null && (totem.getId() == NullObjectID.NULL_41355 || totem.getId() == NullObjectID.NULL_41354)) {
        return totem;
    }
    return null;
}

    public TileObject getBrokenTotem() {
        WorldPoint localInstance = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(),totemPoint).stream().findFirst().orElse(null);
    TileObject totem = Rs2GameObject.findGameObjectByLocation(localInstance);
    if (totem != null && (totem.getId() == ObjectID.DAMAGED_TOTEM_POLE || totem.getId() == ObjectID.DAMAGED_TOTEM_POLE_41011))
        return totem;

    return null;
    }

    public TileObject getRange()
    {
        WorldPoint localInstance = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(),rangePoint).stream().findFirst().orElse(null);
        return Rs2GameObject.findObject(ObjectID.SHRINE_41236, localInstance);
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
