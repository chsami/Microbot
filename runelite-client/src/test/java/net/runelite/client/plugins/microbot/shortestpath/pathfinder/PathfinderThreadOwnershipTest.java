package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.ApiTestClient;
import net.runelite.client.plugins.microbot.api.playerstate.Rs2PlayerStateCache;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import org.junit.Test;

import java.util.Collections;

import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PathfinderThreadOwnershipTest {

    @Test
    public void offlinePlanningWithWorldMetadataDoesNotReadLivePlayerState() throws Exception {
        try (ApiTestClient runtime = new ApiTestClient()) {
            runtime.installCache("rs2PlayerStateCache", Rs2PlayerStateCache.class);
            PathfinderConfig config = new PathfinderConfig(
                    SplitFlagMap.fromResources(), Collections.emptyMap(), Collections.emptyList(),
                    runtime.client, null);
            java.lang.reflect.Field cutoff = PathfinderConfig.class.getDeclaredField("calculationCutoffMillis");
            cutoff.setAccessible(true);
            cutoff.setLong(config, 10_000L);
            WorldPoint target = new WorldPoint(3232, 3218, 0);
            Pathfinder pathfinder = new Pathfinder(config, new WorldPoint(3222, 3218, 0), target);

            pathfinder.run();

            assertTrue("offline route must reach its target", pathfinder.getPath().contains(target));
            verify(runtime.bridge, never()).runOnClientThreadOptional(any());
        }
    }

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
