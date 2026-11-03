package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathTerminationReason;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

/**
 * The sealed-target fast path: an unreachable destination must fail in ~a thousand nodes, not by
 * flooding the entire world component.
 * <p>
 * Pinned against the live failure of 2026-08-06/07: 37 {@code SEARCH_EXHAUSTED} terminations at
 * ~1.1M nodes and 1.2-3.8s each, mostly for destinations TWO TILES from the player — a sealed tile
 * targeted by coordinate. The reverse probe explores only the target's own component and answers in
 * about a millisecond; the search then runs against the component's walkable rim so the walk still
 * ends beside the sealed area, which is all the old flood's best-effort path ever bought.
 */
public class SealedTargetFastPathTest {

    private static SplitFlagMap collisionMap;
    private static HashMap<WorldPoint, Set<Transport>> transports;

    /** Lumbridge courtyard: mapped, ordinary, walkable ground. */
    private static final WorldPoint SRC = new WorldPoint(3222, 3218, 0);

    /** Generous ceiling: the old failure mode expanded ~1.1M nodes; the fast path needs ~1k. */
    private static final long NODE_CEILING = 60_000;

    @BeforeClass
    public static void load() {
        collisionMap = SplitFlagMap.fromResources();
        transports = Transport.loadAllFromResources();
    }

    private static PathfinderConfig newConfig() {
        PathfinderConfig config = new PathfinderConfig(collisionMap, transports,
                Collections.emptyList(), null, null);
        try {
            java.lang.reflect.Field f = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
            f.setAccessible(true);
            f.setLong(config, 10_000);
            for (Map.Entry<WorldPoint, Set<Transport>> e : transports.entrySet()) {
                if (e.getKey() == null) continue;
                config.getTransports().put(e.getKey(), e.getValue());
                config.getTransportsPacked().put(WorldPointUtil.packWorldPoint(e.getKey()), e.getValue());
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        return config;
    }

    private static boolean hasAnyStepOut(CollisionMap map, int x, int y, int z) {
        return map.canStep(x, y, z, 1, 0) || map.canStep(x, y, z, -1, 0)
                || map.canStep(x, y, z, 0, 1) || map.canStep(x, y, z, 0, -1);
    }

    /**
     * Mirrors the probe's sealed reading: no neighbour can step INTO the tile from any of the 8
     * directions. An object footprint blocks entry from every side while its own edge flags can
     * still read as notional exits, so an exit-based test misses exactly the live case's tiles.
     */
    private static boolean noEntry(CollisionMap map, int x, int y, int z) {
        int[][] all = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};
        for (int[] d : all) {
            if (map.canStep(x - d[0], y - d[1], z, d[0], d[1])) {
                return false;
            }
        }
        return true;
    }

    /** A floorless upper plane has no map data: every edge reads blocked, and its rim is equally void. */
    @Test
    public void voidTargetFailsFastWithNoPath() {
        PathfinderConfig config = newConfig();
        WorldPoint dst = new WorldPoint(3222, 3218, 3);
        assumeTrue("precondition: the shipped map must seal the void tile",
                !hasAnyStepOut(config.getMap(), dst.getX(), dst.getY(), dst.getPlane()));

        long startedAt = System.currentTimeMillis();
        Pathfinder pf = new Pathfinder(config, SRC, dst);
        pf.run();
        long elapsed = System.currentTimeMillis() - startedAt;

        assertEquals(PathTerminationReason.SEARCH_EXHAUSTED, pf.getTerminationReason());
        assertTrue("void target must fail fast, took " + elapsed + "ms", elapsed < 2_000);
        assertTrue("void target must not flood: nodes=" + pf.getStats().getNodesChecked(),
                pf.getStats().getNodesChecked() < NODE_CEILING);
        assertTrue("no walkable rim means no path", pf.getPath().isEmpty());
    }

    /**
     * A fully-blocked tile beside walkable ground (an interactable's footprint, the live case's
     * shape): the search must end SEARCH_EXHAUSTED quickly WITH a best-effort path that stops on the
     * rim beside the sealed tile — the same utility the 1.1M-node flood used to buy for 3.8s.
     */
    @Test
    public void sealedTileWithWalkableRimYieldsTheApproachPath() {
        PathfinderConfig config = newConfig();
        CollisionMap map = config.getMap();
        map.beginSearch();

        // Self-locating with a PROVABLY REACHABLE rim: BFS the walkable area around SRC first, then
        // pick a sealed tile one of whose neighbours is in that area. Earlier attempts picked sealed
        // tiles by geometry alone and landed on moat/interior tiles whose rim is an unreachable
        // pocket — the unreachable-rim case, which is bounded elsewhere; this test is the live case:
        // an interactable's sealed footprint beside ground the player can stand on.
        Set<WorldPoint> reachable = new java.util.HashSet<>();
        java.util.ArrayDeque<WorldPoint> frontier = new java.util.ArrayDeque<>();
        reachable.add(SRC);
        frontier.add(SRC);
        int[][] dirs = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!frontier.isEmpty() && reachable.size() < 1_500) {
            WorldPoint c = frontier.poll();
            for (int[] d : dirs) {
                if (!map.canStep(c.getX(), c.getY(), 0, d[0], d[1])) {
                    continue;
                }
                WorldPoint n = new WorldPoint(c.getX() + d[0], c.getY() + d[1], 0);
                if (reachable.add(n)) {
                    frontier.add(n);
                }
            }
        }
        WorldPoint dst = null;
        int bestDist = Integer.MAX_VALUE;
        for (WorldPoint open : reachable) {
            for (int[] d : dirs) {
                int x = open.getX() + d[0];
                int y = open.getY() + d[1];
                WorldPoint cand = new WorldPoint(x, y, 0);
                if (reachable.contains(cand) || !noEntry(map, x, y, 0)) {
                    continue;
                }
                int dist = Math.max(Math.abs(x - SRC.getX()), Math.abs(y - SRC.getY()));
                if (dist >= 3 && dist < bestDist) {
                    bestDist = dist;
                    dst = cand;
                }
            }
        }
        assumeTrue("precondition: found a sealed tile whose rim the player can stand on", dst != null);

        long startedAt = System.currentTimeMillis();
        Pathfinder pf = new Pathfinder(config, SRC, dst);
        pf.run();
        long elapsed = System.currentTimeMillis() - startedAt;

        assertEquals("the ORIGINAL target is unreachable and the caller must hear it",
                PathTerminationReason.SEARCH_EXHAUSTED, pf.getTerminationReason());
        assertTrue("sealed target must fail fast, took " + elapsed + "ms for dst=" + dst,
                elapsed < 3_000);
        assertTrue("sealed target must not flood: nodes=" + pf.getStats().getNodesChecked() + " dst=" + dst,
                pf.getStats().getNodesChecked() < NODE_CEILING);
        assertTrue("the walk still gets an approach path to the rim", !pf.getPath().isEmpty());
        WorldPoint last = pf.getPath().get(pf.getPath().size() - 1);
        assertNotNull(last);
        assertTrue("approach path must end beside the sealed tile, ended at " + last + " for dst=" + dst,
                last.distanceTo2D(dst) <= 2);
    }

    /** The probe must not disturb ordinary reachable routes: same courtyard, short hop, reached. */
    @Test
    public void reachableTargetStillReached() {
        PathfinderConfig config = newConfig();
        WorldPoint dst = new WorldPoint(3232, 3218, 0);

        Pathfinder pf = new Pathfinder(config, SRC, dst);
        pf.run();

        assertEquals(PathTerminationReason.TARGET_REACHED, pf.getTerminationReason());
        assertTrue(!pf.getPath().isEmpty());
        assertEquals(dst, pf.getPath().get(pf.getPath().size() - 1));
    }

    @Test
    public void midRiverTargetPublishesTheNearestBankAsSubstitute() {
        PathfinderConfig config = newConfig();
        WorldPoint dst = new WorldPoint(2466, 3151, 0);
        assumeTrue("precondition: the shipped map must seal the river tile",
                config.getMap().isBlocked(dst.getX(), dst.getY(), dst.getPlane()));

        Pathfinder pf = new Pathfinder(config, new WorldPoint(2212, 2859, 0), dst);
        pf.run();

        assertEquals(PathTerminationReason.SEARCH_EXHAUSTED, pf.getTerminationReason());
        WorldPoint substitute = pf.getNearestSealedRimSubstitute();
        assertNotNull(substitute);
        assertTrue("mid-river goal must publish a substitute other than itself: " + substitute,
                !substitute.equals(dst));
        assertTrue("substitute must be open ground: " + substitute,
                !config.getMap().isBlocked(substitute.getX(), substitute.getY(), substitute.getPlane()));
        assertTrue("substitute must be beside the river spot: " + substitute,
                substitute.distanceTo2D(dst) <= 10);
        assertTrue("the substitute search must run: nodes=" + pf.getStats().getNodesChecked(),
                pf.getStats().getNodesChecked() > 0);
    }

    @Test
    public void midRiverTargetFromTheBankYieldsAnApproachPath() {
        PathfinderConfig config = newConfig();
        WorldPoint dst = new WorldPoint(2466, 3151, 0);
        WorldPoint src = new WorldPoint(2456, 3151, 0);
        assumeTrue("precondition: the shipped map must seal the river tile",
                config.getMap().isBlocked(dst.getX(), dst.getY(), dst.getPlane()));

        Pathfinder pf = new Pathfinder(config, src, dst);
        pf.run();

        assertEquals(PathTerminationReason.SEARCH_EXHAUSTED, pf.getTerminationReason());
        assertTrue("the walk gets an approach path toward the river spot", !pf.getPath().isEmpty());
        WorldPoint last = pf.getPath().get(pf.getPath().size() - 1);
        assertTrue("approach path must end on the bank beside the spot, ended at " + last,
                last.distanceTo2D(dst) <= 10);
    }

    /** Regression from 2026-08-15: 3539 -> 3538 -> 3537 must be normalized in one plan. */
    @Test
    public void burthorpeNestedSealedShellPublishesTheOuterApproachOnce() {
        PathfinderConfig config = newConfig();
        WorldPoint src = new WorldPoint(2935, 3456, 0);
        WorldPoint dst = new WorldPoint(2907, 3539, 0);

        Pathfinder pf = new Pathfinder(config, src, dst);
        pf.run();

        assertEquals(PathTerminationReason.SEARCH_EXHAUSTED, pf.getTerminationReason());
        WorldPoint substitute = pf.getNearestSealedRimSubstitute();
        assertNotNull(substitute);
        assertTrue("nested sealed rim must be resolved beyond the immediate 3538 shell: " + substitute,
                !substitute.equals(new WorldPoint(2907, 3538, 0)));
    }
}
