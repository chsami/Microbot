package net.runelite.client.plugins.microbot.frosty.artioprayer;

import net.runelite.api.World;
import net.runelite.api.coords.WorldPoint;

import java.util.Random;

public enum WalkingPoints {
    TREE1(new WorldPoint(2457, 3489, 1)),
    TREE2(new WorldPoint(2455, 3488, 1)),
    TREE3(new WorldPoint(2454, 3488, 1)),
    TREE4(new WorldPoint(2451, 3488, 1));

    private final WorldPoint worldPoint;

    WalkingPoints(WorldPoint worldPoint) {
        this.worldPoint = worldPoint;
    }

    public WorldPoint getWorldPoint() {
        return worldPoint;
    }

    // ✅ Method to get a random WalkingPoint
    public static WalkingPoints getRandom() {
        WalkingPoints[] points = values();
        return points[new Random().nextInt(points.length)];
    }
}