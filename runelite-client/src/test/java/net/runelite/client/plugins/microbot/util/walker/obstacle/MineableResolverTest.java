package net.runelite.client.plugins.microbot.util.walker.obstacle;

import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Headless tests for {@link MineableResolver}'s pure classification ({@code handles}). {@code resolve}
 * still delegates to game-coupled {@code Rs2ObstacleHandler} at this adapter stage, so it is covered by the
 * live walk at the dispatch cutover rather than here.
 */
public class MineableResolverTest {

    private final MineableResolver resolver = new MineableResolver();

    private static WorldPoint wp(int x, int y) {
        return new WorldPoint(x, y, 0);
    }

    private static LiveScene sceneWith(Map<WorldPoint, TileObject> objects) {
        return new LiveScene() {
            public WorldPoint playerLocation() {
                return wp(0, 0);
            }

            public boolean isReachable(WorldPoint tile) {
                return true;
            }

			public boolean hasTransportAt(WorldPoint tile) {
				return false;
			}

            public TileObject objectAt(WorldPoint tile) {
                return objects.get(tile);
            }
        };
    }

    private static TileObject objectWithId(int id) {
        TileObject o = mock(TileObject.class);
        when(o.getId()).thenReturn(id);
        return o;
    }

    @Test
    public void handlesEdgeWithRockfallOnDestination() {
        Map<WorldPoint, TileObject> objs = new HashMap<>();
        objs.put(wp(3727, 5683), objectWithId(ObjectID.MOTHERLODE_ROCKFALL_1));
        assertTrue(resolver.handles(new PlannedEdge(wp(3728, 5683), wp(3727, 5683)), sceneWith(objs)));
    }

    @Test
    public void handlesEdgeWithRockfallOnOrigin() {
        Map<WorldPoint, TileObject> objs = new HashMap<>();
        objs.put(wp(3728, 5683), objectWithId(ObjectID.MOTHERLODE_ROCKFALL_2));
        assertTrue(resolver.handles(new PlannedEdge(wp(3728, 5683), wp(3727, 5683)), sceneWith(objs)));
    }

    @Test
    public void ignoresNonRockfallObject() {
        Map<WorldPoint, TileObject> objs = new HashMap<>();
        objs.put(wp(3727, 5683), objectWithId(12345));
        assertFalse(resolver.handles(new PlannedEdge(wp(3728, 5683), wp(3727, 5683)), sceneWith(objs)));
    }

    @Test
    public void ignoresEdgeWithNoObject() {
        assertFalse(resolver.handles(new PlannedEdge(wp(3728, 5683), wp(3727, 5683)), sceneWith(new HashMap<>())));
    }
}
