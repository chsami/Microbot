package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression for the "walker deviates wide / traps itself near Varrock West Bank" bug.
 *
 * <p>Root cause: the click layer overshot the first line-of-sight waypoint by clamping the click to
 * a Euclidean radius (~10 tiles), landing on a tile with no straight walkable line-of-sight from the
 * player. A minimap click is resolved by the game's own pathing, so a no-LOS target let the game
 * improvise a detour around the bank. The fix restricts click targets to raw-path points the player
 * has straight walkable line-of-sight to (see {@code Rs2Walker.hasWalkableLineOfSight} /
 * {@code selectRouteClickTarget}).
 *
 * <p>This test pins the collision-map behaviour the fix relies on, using the same
 * {@code CollisionMap.canStep} line-walk the runtime helper uses.
 */
public class LineOfSightClickRegressionTest {

    private static SplitFlagMap collisionMap;

    private static final WorldPoint START = new WorldPoint(3183, 3435, 0);
    private static final WorldPoint GOAL = new WorldPoint(3173, 3399, 0);
    // The tile the old click layer selected: ~10 tiles out, off the raw path, NO LOS from start.
    private static final WorldPoint OLD_DEVIATING_CLICK = new WorldPoint(3176, 3428, 0);

    /**
     * The raw path is computed once for the whole class. Each {@code Pathfinder.run()} reloads all
     * transports and, via {@code CollisionMap.getCachedRegionId}, calls
     * {@code Rs2Player.getWorldLocation()} — which has no client thread under test and blocks for its
     * full 10s timeout. Computing per-test cost the suite ~20s and printed two alarming (harmless)
     * TimeoutException stack traces into CI output.
     */
    private static List<WorldPoint> sharedRawPath;

    @BeforeClass
    public static void load() {
        collisionMap = SplitFlagMap.fromResources();
        sharedRawPath = computeRawPath(START, GOAL);
    }

    /** Straight walkable line check — mirrors Rs2Walker.hasWalkableLineOfSight / PathSmoother.lineOfSight. */
    private static boolean los(CollisionMap map, WorldPoint from, WorldPoint to) {
        if (from.getPlane() != to.getPlane()) {
            return false;
        }
        int x = from.getX(), y = from.getY(), z = from.getPlane(), tx = to.getX(), ty = to.getY();
        int guard = 0;
        while ((x != tx || y != ty) && guard++ < 128) {
            int dx = Integer.signum(tx - x);
            int dy = Integer.signum(ty - y);
            if (!map.canStep(x, y, z, dx, dy)) {
                return false;
            }
            x += dx;
            y += dy;
        }
        return x == tx && y == ty;
    }

    private static List<WorldPoint> computeRawPath(WorldPoint start, WorldPoint goal) {
        HashMap<WorldPoint, Set<Transport>> transports = Transport.loadAllFromResources();
        PathfinderConfig config = new PathfinderConfig(collisionMap, transports,
                Collections.emptyList(), null, null);
        try {
            java.lang.reflect.Field f = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
            f.setAccessible(true);
            f.setLong(config, 10000);
            for (Map.Entry<WorldPoint, Set<Transport>> e : transports.entrySet()) {
                if (e.getKey() == null) continue;
                config.getTransports().put(e.getKey(), e.getValue());
                config.getTransportsPacked().put(WorldPointUtil.packWorldPoint(e.getKey()), e.getValue());
            }
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
        Pathfinder pf = new Pathfinder(config, start, goal);
        pf.run();
        return pf.getPath();
    }

    @Test
    public void oldDeviatingClickHasNoLineOfSightFromStart() {
        CollisionMap map = new CollisionMap(collisionMap);
        assertFalse("The old ~10-tile click (3176,3428) must NOT have straight LOS from the start — "
                        + "that is exactly why the game improvised a detour around the bank",
                los(map, START, OLD_DEVIATING_CLICK));
    }

    @Test
    public void losClampSelectsAnOnRouteTileNotTheDeviatingClick() {
        CollisionMap map = new CollisionMap(collisionMap);
        List<WorldPoint> raw = sharedRawPath;
        assertFalse("raw path should not be empty", raw.isEmpty());

        // Furthest forward raw point with straight LOS from the start = the LOS-clamped first click.
        WorldPoint losClick = null;
        for (WorldPoint p : raw) {
            if (los(map, START, p)) {
                losClick = p;
            }
        }
        assertTrue("There must be at least one raw point with LOS from the start", losClick != null);
        assertTrue("The LOS-clamped click must be on the raw route", raw.contains(losClick));
        assertFalse("The LOS-clamped click must not be the deviating off-route tile",
                losClick.equals(OLD_DEVIATING_CLICK));
    }

    @Test
    public void greedyLosChainReachesGoalInFewStraightClicks() {
        CollisionMap map = new CollisionMap(collisionMap);
        List<WorldPoint> raw = sharedRawPath;

        // Greedy LOS-clamp: from current tile, jump to the furthest raw tile with straight LOS, repeat.
        List<WorldPoint> clicks = new ArrayList<>();
        int cur = 0;
        int guard = 0;
        while (cur < raw.size() - 1 && guard++ < 60) {
            int next = cur;
            for (int i = cur + 1; i < raw.size(); i++) {
                if (los(map, raw.get(cur), raw.get(i))) {
                    next = i;
                }
            }
            if (next == cur) {
                next = cur + 1; // no LOS even to the neighbour: step one tile (shouldn't happen here)
            }
            // Every issued click must be a straight walkable line from where the click is issued.
            assertTrue("click " + clicks.size() + " must have LOS from the previous stop",
                    los(map, raw.get(cur), raw.get(next)));
            clicks.add(raw.get(next));
            cur = next;
        }

        assertEquals("Greedy LOS chain must end exactly on the goal",
                GOAL, clicks.get(clicks.size() - 1));
        // The 41-tile route collapses to a handful of straight clicks (measured: 4). Guard against a
        // regression that shatters it into many tiny clicks (which would look botlike / be slow).
        assertTrue("LOS clamp must not over-click a mostly-straight 41-tile route, got " + clicks.size(),
                clicks.size() <= 8);
    }
}
