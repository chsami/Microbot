package net.runelite.client.plugins.microbot.util.npc;

import lombok.Data;
import net.runelite.api.coords.WorldPoint;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Represents one location entry, holding a name, optional mapID,
 * and a list of actual WorldPoints for coords.
 */
@Data
public class MonsterLocation
{
    private String locationName;
    private Integer mapID;
    private List<WorldPoint> coords = new ArrayList<>();

    /**
     * Finds the "center" of all coords (by averaging their X and Y)
     * and returns whichever actual WorldPoint from the list is
     * closest to that center. Returns null if there are no coords.
     */
    public WorldPoint getClosestToCenter()
    {
        if (coords.isEmpty())
        {
            return null;
        }

        // Sum up the X and Y values
        double sumX = 0;
        double sumY = 0;
        // We'll assume all coords share the same plane; if not, we can adjust
        int plane = coords.get(0).getPlane();

        for (WorldPoint wp : coords)
        {
            sumX += wp.getX();
            sumY += wp.getY();
        }

        // Calculate the average X and Y (the "center")
        double avgX = sumX / coords.size();
        double avgY = sumY / coords.size();

        // Now find which actual coordinate in coords is closest to that average
        WorldPoint closest = null;
        double bestDistanceSq = Double.MAX_VALUE;
        for (WorldPoint wp : coords)
        {
            double dx = wp.getX() - avgX;
            double dy = wp.getY() - avgY;
            double distanceSq = dx*dx + dy*dy;

            if (distanceSq < bestDistanceSq)
            {
                bestDistanceSq = distanceSq;
                closest = wp;
            }
        }
        return closest;
    }

    public WorldPoint getClosestToCenterParallel() {
        if (coords.isEmpty()) {
            return null;
        }

        double sumX = coords.parallelStream().mapToDouble(WorldPoint::getX).sum();
        double sumY = coords.parallelStream().mapToDouble(WorldPoint::getY).sum();

        double avgX = sumX / coords.size();
        double avgY = sumY / coords.size();

        // Find the minimum by comparing squared distance to (avgX, avgY)
        return coords.parallelStream()
                .min(Comparator.comparingDouble(wp -> {
                    double dx = wp.getX() - avgX;
                    double dy = wp.getY() - avgY;
                    return dx * dx + dy * dy;
                }))
                .orElse(null);
    }

}
