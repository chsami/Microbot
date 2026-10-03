package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class RouteSceneObjectSnapshotTest {

    @Test
    public void capturesBothSidesOfAnInRangeEdgeWithoutScanningBeyondRange() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> route = new ArrayList<>();
        for (int x = 0; x <= 18; x++) {
            route.add(new WorldPoint(3200 + x, 3200, 0));
        }

        List<WorldPoint> nearby = RouteSceneObjectSnapshot.nearbyRouteTiles(
                route, 0, 17, player, 13);

        assertTrue(nearby.contains(new WorldPoint(3213, 3201, 0)));
        assertTrue(nearby.contains(new WorldPoint(3213, 3199, 0)));
        assertFalse(nearby.contains(new WorldPoint(3214, 3200, 0)));
        assertFalse(nearby.contains(new WorldPoint(3200, 3200, 1)));
    }

    @Test
    public void onlyUsesRequestedForwardEdges() {
        WorldPoint player = new WorldPoint(3205, 3200, 0);
        List<WorldPoint> route = List.of(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0),
                new WorldPoint(3203, 3200, 0),
                new WorldPoint(3204, 3200, 0),
                new WorldPoint(3205, 3200, 0));

        List<WorldPoint> nearby = RouteSceneObjectSnapshot.nearbyRouteTiles(
                route, 3, 1, player, 13);

        assertTrue(nearby.contains(new WorldPoint(3203, 3200, 0)));
        assertTrue(nearby.contains(new WorldPoint(3204, 3200, 0)));
        assertFalse(nearby.contains(new WorldPoint(3201, 3200, 0)));
        assertFalse(nearby.contains(new WorldPoint(3206, 3200, 0)));
    }
}
