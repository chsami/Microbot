package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.Client;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import static net.runelite.api.Constants.CHUNK_SIZE;
import static net.runelite.api.Perspective.LOCAL_COORD_BITS;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class WorldPointUtil {
    public static final int UNDEFINED = -1;

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

    public static int dxdy(int packedPoint, int dx, int dy) {
        int x = unpackWorldX(packedPoint);
        int y = unpackWorldY(packedPoint);
        int z = unpackWorldPlane(packedPoint);
        return packWorldPoint(x + dx, y + dy, z);
    }

    public static int distanceBetween(int previousPacked, int currentPacked) {
        return distanceBetween(previousPacked, currentPacked, 1);
    }

    public static int distanceBetween2D(int previousPacked, int currentPacked) {
        return distanceBetween2D(previousPacked, currentPacked, 1);
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

    public static int distanceBetween2D(int previousPacked, int currentPacked, int diagonal) {
        final int previousX = WorldPointUtil.unpackWorldX(previousPacked);
        final int previousY = WorldPointUtil.unpackWorldY(previousPacked);
        final int currentX = WorldPointUtil.unpackWorldX(currentPacked);
        final int currentY = WorldPointUtil.unpackWorldY(currentPacked);
        return distanceBetween2D(previousX, previousY, currentX, currentY, diagonal);
    }

    public static int distanceBetween(int previousX, int previousY, int previousZ,
                                      int currentX, int currentY, int currentZ, int diagonal) {
        final int dz = Math.abs(previousZ - currentZ);

        if (dz != 0) {
            return Integer.MAX_VALUE;
        }

        return distanceBetween2D(previousX, previousY, currentX, currentY, diagonal);
    }

    public static int distanceBetween2D(int previousX, int previousY,
                                        int currentX, int currentY, int diagonal) {
        final int dx = Math.abs(previousX - currentX);
        final int dy = Math.abs(previousY - currentY);

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

    private static int rotate(int originalX, int originalY, int z, int rotation) {
        int chunkX = originalX & ~(CHUNK_SIZE - 1);
        int chunkY = originalY & ~(CHUNK_SIZE - 1);
        int x = originalX & (CHUNK_SIZE - 1);
        int y = originalY & (CHUNK_SIZE - 1);
        switch (rotation) {
            case 1:
                return packWorldPoint(chunkX + y, chunkY + (CHUNK_SIZE - 1 - x), z);
            case 2:
                return packWorldPoint(chunkX + (CHUNK_SIZE - 1 - x), chunkY + (CHUNK_SIZE - 1 - y), z);
            case 3:
                return packWorldPoint(chunkX + (CHUNK_SIZE - 1 - y), chunkY + x, z);
        }
        return packWorldPoint(originalX, originalY, z);
    }

    public static int fromLocalInstance(Client client, LocalPoint localPoint) {
        WorldView worldView = client.getWorldView(localPoint.getWorldView());
        int plane = worldView.getPlane();

        if (!worldView.isInstance()) {
            return packWorldPoint(
                    (localPoint.getX() >> LOCAL_COORD_BITS) + worldView.getBaseX(),
                    (localPoint.getY() >> LOCAL_COORD_BITS) + worldView.getBaseY(),
                    plane);
        }

        int[][][] instanceTemplateChunks = worldView.getInstanceTemplateChunks();

        // get position in the scene
        int sceneX = localPoint.getSceneX();
        int sceneY = localPoint.getSceneY();

        // get chunk from scene
        int chunkX = sceneX / CHUNK_SIZE;
        int chunkY = sceneY / CHUNK_SIZE;

        // get the template chunk for the chunk
        int templateChunk = instanceTemplateChunks[plane][chunkX][chunkY];

        int rotation = templateChunk >> 1 & 0x3;
        int templateChunkY = (templateChunk >> 3 & 0x7FF) * CHUNK_SIZE;
        int templateChunkX = (templateChunk >> 14 & 0x3FF) * CHUNK_SIZE;
        int templateChunkPlane = templateChunk >> 24 & 0x3;

        // calculate world point of the template
        int x = templateChunkX + (sceneX & (CHUNK_SIZE - 1));
        int y = templateChunkY + (sceneY & (CHUNK_SIZE - 1));

        // create and rotate point back to 0, to match with template
        return rotate(x, y, templateChunkPlane, 4 - rotation);
    }

    public static Collection<Integer> toLocalInstance(Client client, int packedPoint) {
        WorldView worldView = client.getTopLevelWorldView();

        if (!worldView.isInstance()) {
            return Collections.singleton(packedPoint);
        }

        int baseX = worldView.getBaseX();
        int baseY = worldView.getBaseY();
        int worldPointX = unpackWorldX(packedPoint);
        int worldPointY = unpackWorldY(packedPoint);
        int worldPointPlane = unpackWorldPlane(packedPoint);

        int[][][] instanceTemplateChunks = worldView.getInstanceTemplateChunks();

        // find instance chunks using the template point. there might be more than one.
        List<Integer> worldPoints = new ArrayList<>();
        for (int z = 0; z < instanceTemplateChunks.length; z++) {
            for (int x = 0; x < instanceTemplateChunks[z].length; ++x) {
                for (int y = 0; y < instanceTemplateChunks[z][x].length; ++y) {
                    int chunkData = instanceTemplateChunks[z][x][y];
                    int rotation = chunkData >> 1 & 0x3;
                    int templateChunkY = (chunkData >> 3 & 0x7FF) * CHUNK_SIZE;
                    int templateChunkX = (chunkData >> 14 & 0x3FF) * CHUNK_SIZE;
                    int plane = chunkData >> 24 & 0x3;
                    if (worldPointX >= templateChunkX && worldPointX < templateChunkX + CHUNK_SIZE
                            && worldPointY >= templateChunkY && worldPointY < templateChunkY + CHUNK_SIZE
                            && plane == worldPointPlane) {
                        worldPoints.add(rotate(
                                baseX + x * CHUNK_SIZE + (worldPointX & (CHUNK_SIZE - 1)),
                                baseY + y * CHUNK_SIZE + (worldPointY & (CHUNK_SIZE - 1)),
                                z,
                                rotation));
                    }
                }
            }
        }
        return worldPoints;
    }

    private static boolean isInScene(WorldView worldView, int packedPoint) {
        int x = unpackWorldX(packedPoint);
        int y = unpackWorldY(packedPoint);

        int baseX = worldView.getBaseX();
        int baseY = worldView.getBaseY();

        int maxX = baseX + worldView.getSizeX();
        int maxY = baseY + worldView.getSizeY();

        return x >= baseX && x < maxX && y >= baseY && y < maxY;
    }

    public static LocalPoint toLocalPoint(Client client, int packedPoint) {
        WorldView worldView = client.getTopLevelWorldView();

        if (worldView.getPlane() != unpackWorldPlane(packedPoint)) {
            return null;
        }

        if (!isInScene(worldView, packedPoint)) {
            return null;
        }

        return new LocalPoint(
                (unpackWorldX(packedPoint) - worldView.getBaseX() << LOCAL_COORD_BITS) + (1 << LOCAL_COORD_BITS - 1),
                (unpackWorldY(packedPoint) - worldView.getBaseY() << LOCAL_COORD_BITS) + (1 << LOCAL_COORD_BITS - 1),
                worldView.getId());
    }
}