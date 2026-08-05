package net.runelite.client.plugins.microbot.util.walker.recovery;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Headless scenario tests for {@link RouteRecovery} decisions — the foundation of the walker test harness.
 * <p>
 * Each test constructs a stuck situation entirely in memory — a raw path, the set of tiles reachable from
 * the player, and a transport-origin predicate — with no live client, and asserts the recovery decision. This is what
 * turns "verify a recovery change with a 5-minute live walk" into "verify it in milliseconds", which is the
 * prerequisite for safely rewriting the walker's recovery/executor rather than patching it live. New
 * recovery decisions are extracted into {@code RouteRecovery} as pure functions and exercised here.
 */
public class RouteRecoveryTest {

    private static WorldPoint wp(int x, int y) {
        return new WorldPoint(x, y, 0);
    }

    /** A raw path walking west across a River-Lum-style stepping-stone crossing (origin at 3154,3363). */
    private static List<WorldPoint> steppingStonePath() {
        return Arrays.asList(
                wp(3158, 3363), wp(3157, 3363), wp(3156, 3363), wp(3155, 3363),
                wp(3154, 3363),                 // stone origin (east-bank jump-off tile)
                wp(3153, 3363), wp(3152, 3363), wp(3151, 3363), wp(3150, 3363), wp(3149, 3363));
    }

	private static Predicate<WorldPoint> transportAt(WorldPoint origin) {
		return origin::equals;
    }

    @Test
    public void walksToReachableStoneOriginInsteadOfFarBank() {
        List<WorldPoint> path = steppingStonePath();
        WorldPoint player = wp(3156, 3366);          // stranded a few tiles off the near bank
        WorldPoint origin = wp(3154, 3363);
        // Near bank + origin reachable; the far side of the stones is NOT (across water).
        Set<WorldPoint> reachable = new HashSet<>(Arrays.asList(
                wp(3158, 3363), wp(3157, 3363), wp(3156, 3363), wp(3155, 3363), origin, player));

        WorldPoint chosen = RouteRecovery.findReachableTransportOriginAhead(
                path, 0, player, reachable, transportAt(origin), 15, 40);

        assertEquals("recovery should target the reachable stone origin, not the far bank", origin, chosen);
    }

    @Test
    public void returnsNullWhenNoTransportOnRoute() {
        List<WorldPoint> path = steppingStonePath();
        WorldPoint player = wp(3156, 3366);
        Set<WorldPoint> reachable = new HashSet<>(path);
        reachable.add(player);

        assertNull(RouteRecovery.findReachableTransportOriginAhead(
				path, 0, player, reachable, ignored -> false, 15, 40));
    }

    @Test
    public void returnsNullWhenTransportOriginNotReachable() {
        List<WorldPoint> path = steppingStonePath();
        WorldPoint player = wp(3156, 3366);
        WorldPoint origin = wp(3154, 3363);
        Set<WorldPoint> reachableWithoutOrigin = new HashSet<>(Arrays.asList(player, wp(3157, 3363)));

        assertNull("an unreachable origin must not be targeted",
                RouteRecovery.findReachableTransportOriginAhead(
                        path, 0, player, reachableWithoutOrigin, transportAt(origin), 15, 40));
    }

    @Test
    public void returnsNullWhenTransportOriginBeyondEuclideanReach() {
        List<WorldPoint> path = steppingStonePath();
        WorldPoint player = wp(3156, 3366);
        WorldPoint origin = wp(3154, 3363);          // ~3 tiles away (euclideanSq 13)
        Set<WorldPoint> reachable = new HashSet<>(Arrays.asList(origin, player));

        assertNull("origin beyond the minimap reach must not be targeted",
                RouteRecovery.findReachableTransportOriginAhead(
                        path, 0, player, reachable, transportAt(origin), 1, 40));
    }

    @Test
    public void interpolationKeepsFallbackAlreadyInsideTargetRadius() {
        WorldPoint player = wp(3200, 3200);
        WorldPoint fallback = wp(3203, 3200);
        List<WorldPoint> path = Arrays.asList(player, fallback, wp(3220, 3200));

        assertEquals("an already-clickable fallback must not be pushed farther away",
                fallback,
                RouteRecovery.interpolateClickableTarget(path, 2, player, fallback, 5, point -> true));
    }
}
