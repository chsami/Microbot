package net.runelite.client.plugins.microbot.util.walker.obstacle;

import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Headless tests for {@link TransportResolver} — the stepping-stone recovery fix expressed in the P2
 * model. A stuck edge whose {@code from} is a reachable transport origin the player is off must resolve to
 * {@code WALK_TO_ORIGIN(from)}; every other case must decline so another resolver / plain recovery runs.
 */
public class TransportResolverTest {

    private final TransportResolver resolver = new TransportResolver();

    private static WorldPoint wp(int x, int y) {
        return new WorldPoint(x, y, 0);
    }

    /** Scene with a transport origin at {@code origin}, given player tile and reachable set. */
    private static LiveScene scene(WorldPoint player, WorldPoint origin, Set<WorldPoint> reachable) {
        final Set<Transport> t = new HashSet<>(Collections.singletonList(mock(Transport.class)));
        return new LiveScene() {
            public WorldPoint playerLocation() {
                return player;
            }

            public boolean isReachable(WorldPoint tile) {
                return reachable.contains(tile);
            }

            public Set<Transport> transportsAt(WorldPoint tile) {
                return origin.equals(tile) ? t : Collections.emptySet();
            }

            public TileObject objectAt(WorldPoint tile) {
                return null;
            }
        };
    }

    @Test
    public void walksToReachableOriginPlayerIsOff() {
        WorldPoint origin = wp(3154, 3363);
        LiveScene scene = scene(wp(3156, 3366), origin, new HashSet<>(java.util.Arrays.asList(origin)));
        PlannedEdge edge = new PlannedEdge(origin, wp(3153, 3363));

        assertTrue(resolver.handles(edge, scene));
        ObstacleResolution r = resolver.resolve(edge, scene, null);
        assertEquals(ObstacleResolution.Kind.WALK_TO_ORIGIN, r.kind());
        assertEquals(origin, r.walkTarget());
    }

    @Test
    public void declinesWhenNoTransportAtOrigin() {
        WorldPoint from = wp(3200, 3200);
        LiveScene scene = scene(wp(3202, 3202), wp(9999, 9999), new HashSet<>(java.util.Arrays.asList(from)));
        assertFalse(resolver.handles(new PlannedEdge(from, wp(3201, 3200)), scene));
    }

    @Test
    public void declinesWhenPlayerAlreadyOnOrigin() {
        WorldPoint origin = wp(3154, 3363);
        LiveScene scene = scene(origin, origin, new HashSet<>(java.util.Arrays.asList(origin)));
        assertFalse("normal loop owns the transport once on the origin",
                resolver.handles(new PlannedEdge(origin, wp(3153, 3363)), scene));
    }

    @Test
    public void declinesWhenOriginUnreachable() {
        WorldPoint origin = wp(3154, 3363);
        LiveScene scene = scene(wp(3156, 3366), origin, Collections.emptySet());
        assertFalse(resolver.handles(new PlannedEdge(origin, wp(3153, 3363)), scene));
    }
}
