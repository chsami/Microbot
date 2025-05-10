package net.runelite.client.plugins.microbot.util.coords;

import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;

import static net.runelite.api.Constants.CHUNK_SIZE;

public class Rs2LocalPoint {
    /**
     * Used to convert a worldpoint in an instance to a localpoint
     * @param worldPoint
     * @return
     */
    public static LocalPoint fromWorldInstance(WorldPoint worldPoint)
    {
        int[][][] instanceTemplateChunks = Microbot.getClient().getTopLevelWorldView().getInstanceTemplateChunks();
        // Extract the coordinates from the WorldPoint
        int worldX = worldPoint.getX();
        int worldY = worldPoint.getY();
        int worldPlane = Microbot.getClient().getTopLevelWorldView().getPlane();

        // Loop through all chunks to find which one contains the world point
        for (int chunkX = 0; chunkX < instanceTemplateChunks[worldPlane].length; chunkX++)
        {
            for (int chunkY = 0; chunkY < instanceTemplateChunks[worldPlane][chunkX].length; chunkY++)
            {
                // Get the template chunk at this chunk position
                int templateChunk = instanceTemplateChunks[worldPlane][chunkX][chunkY];

                // Extract rotation, template chunk coordinates, and plane
                int rotation = (templateChunk >> 1) & 0x3;
                int templateChunkY = (templateChunk >> 3 & 0x7FF) * CHUNK_SIZE;
                int templateChunkX = (templateChunk >> 14 & 0x3FF) * CHUNK_SIZE;
                int templateChunkPlane = (templateChunk >> 24) & 0x3;

                // Check if the WorldPoint matches this chunk (after reversing rotation)
                WorldPoint rotatedWorldPoint = rotate(new WorldPoint(worldX, worldY, templateChunkPlane), rotation);

                if (rotatedWorldPoint.getX() >= templateChunkX && rotatedWorldPoint.getX() < templateChunkX + CHUNK_SIZE
                        && rotatedWorldPoint.getY() >= templateChunkY && rotatedWorldPoint.getY() < templateChunkY + CHUNK_SIZE)
                {
                    // Calculate local coordinates within the scene
                    int localX = (rotatedWorldPoint.getX() - templateChunkX) + (chunkX * CHUNK_SIZE);
                    int localY = (rotatedWorldPoint.getY() - templateChunkY) + (chunkY * CHUNK_SIZE);

                    // Return the corresponding LocalPoint
                    return  LocalPoint.fromScene(localX, localY, Microbot.getClient().getTopLevelWorldView());
                }
            }
        }

        // Return null if no matching chunk is found
        return null;
    }

    /**
     * Rotate the coordinates in the chunk according to chunk rotation
     *
     * @param point point
     * @param rotation rotation
     * @return world point
     */
    public static WorldPoint rotate(WorldPoint point, int rotation)
    {
        int chunkX = point.getX() & ~(CHUNK_SIZE - 1);
        int chunkY = point.getY() & ~(CHUNK_SIZE - 1);
        int x = point.getX() & (CHUNK_SIZE - 1);
        int y = point.getY() & (CHUNK_SIZE - 1);
        switch (rotation)
        {
            case 1:
                return new WorldPoint(chunkX + y, chunkY + (CHUNK_SIZE - 1 - x), point.getPlane());
            case 2:
                return new WorldPoint(chunkX + (CHUNK_SIZE - 1 - x), chunkY + (CHUNK_SIZE - 1 - y), point.getPlane());
            case 3:
                return new WorldPoint(chunkX + (CHUNK_SIZE - 1 - y), chunkY + x, point.getPlane());
        }
        return point;
    }

    public static Integer worldToLocalDistance(int distance) {
        return distance * Perspective.LOCAL_TILE_SIZE;
    }

    public static Integer localToWorldDistance(int distance) {
        return distance / Perspective.LOCAL_TILE_SIZE;
    }
}
