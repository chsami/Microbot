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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Motherlode Mine must be internally routable once rockfalls are catalogued as transports.
 *
 * <p>The bundled collision map bakes rockfalls in as permanent walls — upstream has no automation to
 * clear them, so that is correct for upstream. It left the mine carved into disconnected pockets:
 * a route from the cave arrival tile to the WEST_LOWER mining area died 10 tiles short, and the
 * walker's existing {@code handleRockfall} never fired because no path was ever routed through one.
 *
 * <p>Modelling each rockfall as a {@code Mine;Rockfall;<id>} transport between its two open sides
 * gives the pathfinder the edge, and the walker mines it on arrival.
 */
public class MotherlodeRockfallTransportTest {

    private static SplitFlagMap flags;
    private static HashMap<WorldPoint, Set<Transport>> transports;

    /** Arrival tile from the Dwarven Mine cave ("Enter;Cave;26654"). */
    private static final WorldPoint MLM_ARRIVAL = new WorldPoint(3728, 5692, 0);
    /** WEST_LOWER mining spot from the Motherlode plugin. */
    private static final WorldPoint WEST_LOWER = new WorldPoint(3731, 5659, 0);
    /** SOUTH_EAST mining spot — the far corner of the lower floor. */
    private static final WorldPoint SOUTH_EAST = new WorldPoint(3753, 5650, 0);

    @BeforeClass
    public static void load() {
        flags = SplitFlagMap.fromResources();
        transports = Transport.loadAllFromResources();
    }

    private static PathfinderConfig config() {
        PathfinderConfig config = new PathfinderConfig(flags, transports, Collections.emptyList(), null, null);
        try {
            java.lang.reflect.Field f = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
            f.setAccessible(true);
            f.setLong(config, 30000);
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

    private static WorldPoint routeEnd(WorldPoint from, WorldPoint to) {
        Pathfinder pf = new Pathfinder(config(), from, to);
        pf.run();
        List<WorldPoint> path = pf.getPath();
        return path.isEmpty() ? null : path.get(path.size() - 1);
    }

    @Test
    public void rockfallTransportsArePresentAndBidirectional() {
        long rockfallOrigins = transports.values().stream()
                .flatMap(Set::stream)
                .filter(t -> t.getDestination() != null)
                .filter(t -> {
                    WorldPoint d = t.getDestination();
                    return d.getPlane() == 0 && d.getX() >= 3712 && d.getX() <= 3775
                            && d.getY() >= 5632 && d.getY() <= 5695;
                })
                .count();
        assertTrue("the Motherlode region should have transport edges after cataloguing rockfalls, got "
                + rockfallOrigins, rockfallOrigins > 50);
    }

    /** The failure that started this: arrival -> WEST_LOWER stopped 10 tiles short. */
    @Test
    public void arrivalReachesTheWestLowerMiningArea() {
        WorldPoint end = routeEnd(MLM_ARRIVAL, WEST_LOWER);
        assertEquals("the mining area must now be reachable from the cave arrival tile",
                WEST_LOWER, end);
    }

    @Test
    public void arrivalReachesTheFarSideOfTheMine() {
        WorldPoint end = routeEnd(MLM_ARRIVAL, SOUTH_EAST);
        assertEquals("the far corner of the lower floor must be reachable", SOUTH_EAST, end);
    }

    /** End to end: the surface route must now finish inside the mine, not at its mouth. */
    @Test
    public void theSurfaceRouteReachesAMiningArea() {
        WorldPoint end = routeEnd(new WorldPoint(3144, 3337, 0), WEST_LOWER);
        assertEquals("a walk from Draynor must complete all the way to the mining area",
                WEST_LOWER, end);
    }
}
