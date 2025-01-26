package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;

public class WorldPointUtil {
    public static int packWorldPoint(WorldPoint point) {
        if (point == null) {
            return -1;
        }
        return packWorldPoint(point.getX(), point.getY(), point.getPlane());
    }

    // Packs a world point into a single int
    // First 15 bits are x, next 15 are y, last 2 bits are the plane
    public static int packWorldPoint(int x, int y, int plane) {
        return (x & 0x7FFF) | ((y & 0x7FFF) << 15) | ((plane & 0x3) << 30);
    }

    public static WorldPoint unpackWorldPoint(int packedPoint) {
        final int x = unpackWorldX(packedPoint);
        final int y = unpackWorldY(packedPoint);
        final int plane = unpackWorldPlane(packedPoint);
        return new WorldPoint(x, y, plane);
    }

    public static int unpackWorldX(int packedPoint) {
        return packedPoint & 0x7FFF;
    }

    public static int unpackWorldY(int packedPoint) {
        return (packedPoint >> 15) & 0x7FFF;
    }

    public static int unpackWorldPlane(int packedPoint) {
        return (packedPoint >> 30) & 0x3;
    }

    public static int distanceBetween(int previousPacked, int currentPacked) {
        return distanceBetween(previousPacked, currentPacked, 1);
    }

    public static int distanceBetween(int previousPacked, int currentPacked, int diagonal) {
        final int previousX = WorldPointUtil.unpackWorldX(previousPacked);
        final int previousY = WorldPointUtil.unpackWorldY(previousPacked);
        final int previousZ = WorldPointUtil.unpackWorldPlane(previousPacked);
        final int currentX = WorldPointUtil.unpackWorldX(currentPacked);
        final int currentY = WorldPointUtil.unpackWorldY(currentPacked);
        final int currentZ = WorldPointUtil.unpackWorldPlane(currentPacked);
        return distanceBetween(previousX, previousY, previousZ,
                currentX, currentY, currentZ, diagonal);
    }

    public static int distanceBetween(int previousX, int previousY, int previousZ,
                                      int currentX, int currentY, int currentZ, int diagonal) {
        final int dx = Math.abs(previousX - currentX);
        final int dy = Math.abs(previousY - currentY);
        final int dz = Math.abs(previousZ - currentZ);

        if (dz != 0) {
            return Integer.MAX_VALUE;
        }

        if (diagonal == 1) {
            return Math.max(dx, dy);
        } else if (diagonal == 2) {
            return dx + dy;
        }

        return Integer.MAX_VALUE;
    }

    public static int distanceBetween(WorldPoint previous, WorldPoint current) {
        return distanceBetween(previous, current, 1);
    }

    public static int distanceBetween(int packedPoint, WorldPoint current) {
        final int y = unpackWorldY(packedPoint);
        final int x = unpackWorldX(packedPoint);
        final int plane = unpackWorldPlane(packedPoint);
        return distanceBetween(new WorldPoint(x, y, plane), current, 1);
    }

    public static int distanceBetween(WorldPoint previous, WorldPoint current, int diagonal) {
        return distanceBetween(previous.getX(), previous.getY(), previous.getPlane(),
                current.getX(), current.getY(), current.getPlane(), diagonal);
    }

    // Matches WorldArea.distanceTo
    public static int distanceToArea(int packedPoint, WorldArea area) {
        final int plane = unpackWorldPlane(packedPoint);
        if (area.getPlane() != plane) {
            return Integer.MAX_VALUE;
        }

        final int y = unpackWorldY(packedPoint);
        final int x = unpackWorldX(packedPoint);
        final int areaMaxX = area.getX() + area.getWidth() - 1;
        final int areaMaxY = area.getY() + area.getHeight() - 1;
        final int dx = Math.max(Math.max(area.getX() - x, 0), x - areaMaxX);
        final int dy = Math.max(Math.max(area.getY() - y, 0), y - areaMaxY);

        return Math.max(dx, dy);
    }
}
