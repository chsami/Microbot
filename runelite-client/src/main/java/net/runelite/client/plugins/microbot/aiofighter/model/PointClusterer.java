package net.runelite.client.plugins.microbot.aiofighter.model;

import net.runelite.api.coords.WorldPoint;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

public class PointClusterer
{
    private static final Logger log = LoggerFactory.getLogger(PointClusterer.class);

    private static final double MIN_CLUSTER_RADIUS        = 1.0;   // when spread is huge
    private static final double MAX_CLUSTER_RADIUS        = 5.0;   // when spread is tiny
    private static final double SPREAD_SMALL              = 3.0;   // avgDist ≤ 3 ⇒ max radius
    private static final double SPREAD_LARGE              = 15.0;  // avgDist ≥15 ⇒ min radius

    // new: how much we expand the radius each time if cluster size == 1
    private static final double CLUSTER_RADIUS_INCREMENT = 0.5;

    private final List<WorldPoint> cords;

    public PointClusterer(List<WorldPoint> cords)
    {
        this.cords = cords;
    }

    public WorldPoint getClosestToDenseCenterAdaptive()
    {
        if (cords.isEmpty())
        {
            log.warn("PointClusterer invoked with empty input");
            return null;
        }

        log.info("Starting clustering on {} points", cords.size());

        // 1) Compute global centroid
        double sumX = 0, sumY = 0;
        int plane = cords.get(0).getPlane();
        for (WorldPoint wp : cords)
        {
            sumX += wp.getX();
            sumY += wp.getY();
        }
        double centerX = sumX / cords.size();
        double centerY = sumY / cords.size();
        log.debug("Global centroid at ({}, {})", centerX, centerY);

        // 2) Measure average distance to that centroid
        double totalDist = 0;
        for (WorldPoint wp : cords)
        {
            double dx = wp.getX() - centerX;
            double dy = wp.getY() - centerY;
            totalDist += Math.sqrt(dx*dx + dy*dy);
        }
        double avgDist = totalDist / cords.size();
        log.info("Average spread = {}", avgDist);

        // 3) Map spread → initial clusterRadius
        double clusterRadius;
        if (avgDist <= SPREAD_SMALL)
        {
            clusterRadius = MAX_CLUSTER_RADIUS;
        }
        else if (avgDist >= SPREAD_LARGE)
        {
            clusterRadius = MIN_CLUSTER_RADIUS;
        }
        else
        {
            double t = (avgDist - SPREAD_SMALL) / (SPREAD_LARGE - SPREAD_SMALL);
            clusterRadius = MAX_CLUSTER_RADIUS - t * (MAX_CLUSTER_RADIUS - MIN_CLUSTER_RADIUS);
        }
        log.info("Using adaptive cluster radius = {}", clusterRadius);

        // 4) (Optional) Union-Find diagnostics...
        // [unchanged]

        // 5) Seed-based clustering: find densest neighborhood at the current radius
        List<WorldPoint> bestCluster = null;
        int bestSize = 0;
        for (WorldPoint seed : cords)
        {
            List<WorldPoint> neighbors = new ArrayList<>();
            for (WorldPoint other : cords)
            {
                if (seed.distanceTo(other) <= clusterRadius)
                {
                    neighbors.add(other);
                }
            }
            if (neighbors.size() > bestSize)
            {
                bestSize    = neighbors.size();
                bestCluster = neighbors;
            }
        }
        log.info("Densest seed-based cluster size = {} at radius {}", bestSize, clusterRadius);

        // 6) If the best cluster is only 1 point, nudge the radius upward until we get ≥2
        while (bestSize < 2 && clusterRadius < MAX_CLUSTER_RADIUS)
        {
            clusterRadius = Math.min(clusterRadius + CLUSTER_RADIUS_INCREMENT, MAX_CLUSTER_RADIUS);
            log.info("Increasing cluster radius to {} to get at least 2 points", clusterRadius);

            // recompute densest cluster at new radius
            bestSize = 0;
            bestCluster = null;
            for (WorldPoint seed : cords)
            {
                List<WorldPoint> neighbors = new ArrayList<>();
                for (WorldPoint other : cords)
                {
                    if (seed.distanceTo(other) <= clusterRadius)
                        neighbors.add(other);
                }
                if (neighbors.size() > bestSize)
                {
                    bestSize    = neighbors.size();
                    bestCluster = neighbors;
                }
            }
            log.info(" → New densest cluster size = {} at radius {}", bestSize, clusterRadius);
        }

        // 7) Decide whether to use the smaller cluster or all points
        List<WorldPoint> useSet = (bestSize >= 2 && bestSize < cords.size())
                ? bestCluster
                : cords;
        log.info("Using {} (size {}) for final centroid",
                (useSet == cords ? "all points" : "best sub-cluster"),
                useSet.size());

        // 8) Compute centroid of chosen set
        sumX = sumY = 0;
        for (WorldPoint wp : useSet)
        {
            sumX += wp.getX();
            sumY += wp.getY();
        }
        double avgX = sumX / useSet.size();
        double avgY = sumY / useSet.size();
        log.info("Chosen centroid at ({}, {})", avgX, avgY);

        // 9) Find the actual point nearest that centroid
        WorldPoint closest = null;
        double bestDistSq = Double.MAX_VALUE;
        for (WorldPoint wp : useSet)
        {
            double dx = wp.getX() - avgX;
            double dy = wp.getY() - avgY;
            double distSq = dx*dx + dy*dy;
            if (distSq < bestDistSq)
            {
                bestDistSq = distSq;
                closest    = wp;
            }
        }
        log.info("Closest point to centroid is {} (distSq={})", closest, bestDistSq);

        return closest;
    }

    // ... UnionFind class unchanged ...



/**
     * Simple Union-Find for diagnostics only.
     */
    private static class UnionFind
    {
        private final int[] parent, rank;

        UnionFind(int n)
        {
            parent = new int[n];
            rank   = new int[n];
            for (int i = 0; i < n; i++)
            {
                parent[i] = i;
                rank[i]   = 0;
            }
        }

        int find(int x)
        {
            if (parent[x] != x)
                parent[x] = find(parent[x]);
            return parent[x];
        }

        void union(int x, int y)
        {
            int rx = find(x), ry = find(y);
            if (rx == ry) return;
            if (rank[rx] < rank[ry])
                parent[rx] = ry;
            else if (rank[ry] < rank[rx])
                parent[ry] = rx;
            else
            {
                parent[ry] = rx;
                rank[rx]++;
            }
        }
    }
}
