package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import org.junit.Test;

import java.util.Collections;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PathfinderThreadOwnershipTest {

    @Test
    public void collisionMapIsResolvedWhenRunStartsRatherThanDuringConstruction() {
        PathfinderConfig config = mock(PathfinderConfig.class);
        CollisionMap map = mock(CollisionMap.class);
        when(config.getMap()).thenReturn(map);

        Pathfinder pathfinder = new Pathfinder(
                config,
                WorldPointUtil.packWorldPoint(new WorldPoint(3200, 3200, 0)),
                Collections.singleton(WorldPointUtil.packWorldPoint(new WorldPoint(3201, 3200, 0))));

        verify(config, never()).getMap();

        pathfinder.cancel();
        pathfinder.run();

        verify(config).getMap();
        verify(map).beginSearch();
    }
}
