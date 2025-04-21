package net.runelite.client.plugins.microbot.util.coords;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

import java.util.ArrayList;
import java.util.List;

@Slf4j
public class Rs2WorldPoint {
    @Setter
    private WorldPoint worldPoint;

    // Constructor accepting coordinates
    public Rs2WorldPoint(int x, int y, int plane) {
        this.worldPoint = new WorldPoint(x, y, plane);
    }

    // Constructor accepting a WorldPoint instance
    public Rs2WorldPoint(WorldPoint worldPoint) {
        this.worldPoint = worldPoint;

    }

    // Getter methods
    public int getX() {
        return worldPoint.getX();
    }

    public int getY() {
        return worldPoint.getY();
    }

    public int getPlane() {
        return worldPoint.getPlane();
    }

    public WorldPoint getWorldPoint() {
        return worldPoint;
    }

    public List<WorldPoint> pathTo(WorldPoint other)
    {
        return pathTo(other, false);
    }

    public List<WorldPoint> pathTo(WorldPoint other, boolean fullPath)
    {
        Client client = Microbot.getClient();
        if (getPlane() != other.getPlane())
        {
            return null;
        }

        LocalPoint sourceLp = LocalPoint.fromWorld(client.getTopLevelWorldView(), getX(), getY());
        LocalPoint targetLp = LocalPoint.fromWorld(client.getTopLevelWorldView(), other.getX(), other.getY());
        if (sourceLp == null || targetLp == null)
        {
            return null;
        }

        int thisX = sourceLp.getSceneX();
        int thisY = sourceLp.getSceneY();
        int otherX = targetLp.getSceneX();
        int otherY = targetLp.getSceneY();

        Tile[][][] tiles = client.getTopLevelWorldView().getScene().getTiles();
        Tile sourceTile = tiles[getPlane()][thisX][thisY];

        Tile targetTile = tiles[getPlane()][otherX][otherY];
        List<Tile> checkpointTiles = fullPath ? Rs2Tile.fullPathTo(sourceTile,targetTile) : Rs2Tile.pathTo(sourceTile, targetTile);
        if (checkpointTiles == null)
        {
            return null;
        }
        List<WorldPoint> checkpointWPs = new ArrayList<>();
        for (Tile checkpointTile : checkpointTiles)
        {
            if (checkpointTile == null)
            {
                break;
            }
            checkpointWPs.add(checkpointTile.getWorldLocation());
        }
        return checkpointWPs;
    }

    public int distanceToPath(WorldPoint other)
    {
        if(other == null)
        {
            return Integer.MAX_VALUE;
        }
        List<WorldPoint> checkpointWPs = this.pathTo(other);
        if (checkpointWPs == null)
        {
            // No path found
            return Integer.MAX_VALUE;
        }

        WorldPoint destinationPoint = checkpointWPs.get(checkpointWPs.size() - 1);
        if (other.getX() != destinationPoint.getX() || other.getY() != destinationPoint.getY())
        {
            // Path found but not to the requested tile
            return Integer.MAX_VALUE;
        }
        WorldPoint Point1 = getWorldPoint();
        int distance = 0;
        for (WorldPoint Point2 : checkpointWPs)
        {
            distance += Point1.distanceTo2D(Point2);
            Point1 = Point2;
        }
        return distance;
    }

    /**
     * Converts an instanced worldPoint coordinate to a global worldpoint
     * this can be used for getting objects in instanced room by location
     * @param worldPoint
     * @return
     */
    public static WorldPoint convertInstancedWorldPoint(WorldPoint worldPoint) {
        if (worldPoint == null) return null;

        LocalPoint l = Rs2LocalPoint.fromWorldInstance(worldPoint);
        WorldPoint globalWorldPoint = WorldPoint.fromLocal(Microbot.getClient(), l);

        return globalWorldPoint;
    }


    public static WorldPoint toLocalInstance(WorldPoint worldPoint) {
        if (worldPoint == null) return null;


        return WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(),worldPoint).stream().findFirst().orElse(null);
    }

    /**
     * Calculate the distance quikcly with Chebyshev distance
     * https://iq.opengenus.org/euclidean-vs-manhattan-vs-chebyshev-distance/
     * @param a
     * @param b
     * @return
     */
    public static int quickDistance(WorldPoint a, WorldPoint b) {
        int dx = Math.abs(a.getX() - b.getX());
        int dy = Math.abs(normalizeY(a) - normalizeY(b));
        return Math.max(dx, dy);
    }

    /**
     * Normalize a point to use for comparison
     * This is used for caves
     * @param point
     * @return
     */
    public static int normalizeY(WorldPoint point) {
        int y = point.getY();

        if (y > 6400) {
            return y - 6400;
        }
        return y;
    }

    // Override equals, hashCode, and toString if necessary
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj instanceof Rs2WorldPoint) {
            Rs2WorldPoint other = (Rs2WorldPoint) obj;
            return worldPoint.equals(other.worldPoint);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return worldPoint.hashCode();
    }

    @Override
    public String toString() {
        return worldPoint.toString();
    }
}

