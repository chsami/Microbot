package net.runelite.client.plugins.microbot.shortestpath;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.SplitFlagMap;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Regression for the "walker deviates wide / traps itself near Varrock West Bank" bug.
 *
 * <p>The click layer had overshot onto {@code (3176,3428)} — a tile that is <em>not on the raw
 * route</em> — because route selection returned null on a stale anchor and the caller fell through
 * to clamping a far smoothed waypoint to a Euclidean radius. The game then pathed to that off-route
 * tile its own way, which is what produced the wide deviation and the backtracking.
 *
 * <p>The invariant this pins is therefore <strong>"the click target is on the raw route"</strong>,
 * not "the click target is in line of sight". A minimap click is resolved by the game's own
 * pathing, so line of sight is irrelevant to walking — a player clicks past a corner and lets the
 * server route them. An earlier revision clamped clicks to line of sight; that made the walker
 * advance corner-to-corner and was reverted. Do not reintroduce it: if a target sits on the planned
 * route, it does not matter which way the server walks there, because we arrive on the route.
 */
public class RouteClickTargetRegressionTest {

    private static SplitFlagMap collisionMap;

    private static final WorldPoint START = new WorldPoint(3183, 3435, 0);
    private static final WorldPoint GOAL = new WorldPoint(3173, 3399, 0);
    /** The historic bad click: ~10 tiles out, and crucially NOT on the raw route. */
    private static final WorldPoint OLD_DEVIATING_CLICK = new WorldPoint(3176, 3428, 0);

    private static List<WorldPoint> sharedRawPath;

    @BeforeClass
    public static void load() {
        collisionMap = SplitFlagMap.fromResources();
        // Computed once: each Pathfinder.run() reloads all transports and, via
        // CollisionMap.getCachedRegionId, calls Rs2Player.getWorldLocation(), which has no client
        // thread under test and blocks for its full timeout.
        sharedRawPath = computeRawPath(START, GOAL);
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
    public void theHistoricDeviatingClickIsNotOnTheRawRoute() {
        assertFalse("raw path should not be empty", sharedRawPath.isEmpty());
        assertFalse("(3176,3428) must not be on the raw route — selecting only on-route points is "
                        + "what prevents the game improvising a detour",
                sharedRawPath.contains(OLD_DEVIATING_CLICK));
    }

    /**
     * Route selection must be able to reach well past a corner in a single click, the way a player
     * would. A line-of-sight clamp reduced the first click on this route to ~3 tiles (just the
     * corner); the raw route offers far more forward reach than that within minimap range.
     */
    @Test
    public void rawRouteOffersLongForwardReachWithinMinimapRange() {
        int minimapReach = 10;
        int reachable = 0;
        for (WorldPoint p : sharedRawPath) {
            int dx = p.getX() - START.getX();
            int dy = p.getY() - START.getY();
            if (dx * dx + dy * dy <= minimapReach * minimapReach) {
                reachable++;
            }
        }
        assertTrue("the route should offer several on-route candidates within minimap reach, "
                        + "so a click is not artificially shortened to the nearest corner; got " + reachable,
                reachable >= 6);
    }
}
