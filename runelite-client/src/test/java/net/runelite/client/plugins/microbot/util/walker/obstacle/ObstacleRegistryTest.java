package net.runelite.client.plugins.microbot.util.walker.obstacle;

import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Headless tests for the P2 obstacle dispatch. Fake resolvers + an in-memory {@link LiveScene} verify the
 * registry's first-match-wins contract and the {@link ObstacleResolution} shape — the foundation the real
 * DoorResolver / MineableResolver / TransportResolver tests build on (docs/walker-p2-unification.md).
 */
public class ObstacleRegistryTest {

    private static WorldPoint wp(int x, int y) {
        return new WorldPoint(x, y, 0);
    }

    private static final LiveScene EMPTY_SCENE = new LiveScene() {
        public WorldPoint playerLocation() {
            return wp(0, 0);
        }

        public boolean isReachable(WorldPoint tile) {
            return false;
        }

		public boolean hasTransportAt(WorldPoint tile) {
			return false;
		}

        public TileObject objectAt(WorldPoint tile) {
            return null;
        }
    };

    private static final WalkerActions NO_ACTIONS = new WalkerActions() {
        public boolean interactAt(WorldPoint tile, String action) {
            return false;
        }

        public boolean walkToward(WorldPoint target) {
            return false;
        }

        public boolean waitUntilObjectGone(WorldPoint tile) {
            return false;
        }
    };

    private static ObstacleResolver resolver(boolean handles, ObstacleResolution result, boolean[] resolveRan) {
        return new ObstacleResolver() {
            public boolean handles(PlannedEdge edge, LiveScene scene) {
                return handles;
            }

            public ObstacleResolution resolve(PlannedEdge edge, LiveScene scene, WalkerActions actions) {
                resolveRan[0] = true;
                return result;
            }
        };
    }

    @Test
    public void firstMatchingResolverWinsAndLaterOnesAreNotConsulted() {
        boolean[] firstRan = {false};
        boolean[] secondRan = {false};
        ObstacleRegistry registry = new ObstacleRegistry(Arrays.asList(
                resolver(true, ObstacleResolution.interacted(), firstRan),
                resolver(true, ObstacleResolution.crossed(), secondRan)));

        ObstacleResolution r = registry.resolve(new PlannedEdge(wp(1, 1), wp(1, 2)), EMPTY_SCENE, NO_ACTIONS);

        assertEquals(ObstacleResolution.Kind.INTERACTED, r.kind());
        assertTrue(firstRan[0]);
        assertFalse("later resolver must not run once one handles", secondRan[0]);
    }

    @Test
    public void nonHandlingResolverIsSkipped() {
        boolean[] handledRan = {false};
        ObstacleRegistry registry = new ObstacleRegistry(Arrays.asList(
                resolver(false, ObstacleResolution.crossed(), new boolean[1]),
                resolver(true, ObstacleResolution.walkToOrigin(wp(3, 3)), handledRan)));

        ObstacleResolution r = registry.resolve(new PlannedEdge(wp(1, 1), wp(1, 2)), EMPTY_SCENE, NO_ACTIONS);

        assertEquals(ObstacleResolution.Kind.WALK_TO_ORIGIN, r.kind());
        assertEquals(wp(3, 3), r.walkTarget());
        assertTrue(handledRan[0]);
    }

    @Test
    public void noResolverMatchesReturnsNotApplicable() {
        ObstacleRegistry registry = new ObstacleRegistry(Collections.singletonList(
                resolver(false, ObstacleResolution.crossed(), new boolean[1])));

        assertEquals(ObstacleResolution.Kind.NOT_APPLICABLE,
                registry.resolve(new PlannedEdge(wp(1, 1), wp(1, 2)), EMPTY_SCENE, NO_ACTIONS).kind());
    }

    @Test
    public void nullEdgeIsNotApplicable() {
        ObstacleRegistry registry = new ObstacleRegistry(Collections.emptyList());
        assertEquals(ObstacleResolution.Kind.NOT_APPLICABLE,
                registry.resolve(null, EMPTY_SCENE, NO_ACTIONS).kind());
    }

    @Test(expected = NullPointerException.class)
    public void walkToOriginRejectsNullTarget() {
        ObstacleResolution.walkToOrigin(null);
    }

    @Test
    public void plannedEdgeClassification() {
        assertTrue(new PlannedEdge(wp(1, 1), wp(1, 2)).adjacent());
        assertFalse("non-adjacent step is not a plain walk", new PlannedEdge(wp(1, 1), wp(1, 5)).adjacent());
        assertTrue(new PlannedEdge(new WorldPoint(1, 1, 0), new WorldPoint(1, 1, 1)).crossPlane());
    }
}
