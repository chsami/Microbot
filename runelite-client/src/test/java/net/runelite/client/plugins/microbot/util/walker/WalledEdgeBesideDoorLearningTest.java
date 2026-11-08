package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;
import java.util.Map;

import static net.runelite.client.plugins.microbot.util.walker.Rs2Walker.routeState;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;

public class WalledEdgeBesideDoorLearningTest {
    private static final WorldPoint PLAYER = new WorldPoint(2656, 3293, 0);
    private static final WorldPoint WALL_FROM = new WorldPoint(2656, 3292, 0);
    private static final WorldPoint WALL_TO = new WorldPoint(2656, 3291, 0);
    private static final List<WorldPoint> RAW_PATH = List.of(PLAYER, WALL_FROM, WALL_TO);
    private static final Map<WorldPoint, Integer> REACHABLE = Map.of(PLAYER, 0, WALL_FROM, 1);

    private MockedStatic<Rs2PathApi> pathApi;
    private MockedStatic<Rs2WalkerDoors> doors;

    @Before
    public void setUp() {
        Rs2Walker.currentTarget = null;
        Rs2Walker.resetWalkSessionState();
        Rs2Walker.doorAttemptLedger.drainWalkScopedBlocks();
        pathApi = Mockito.mockStatic(Rs2PathApi.class);
        pathApi.when(() -> Rs2PathApi.learnBlockedEdge(any(), any(), anyString())).thenReturn(true);
        doors = Mockito.mockStatic(Rs2WalkerDoors.class);
        doors.when(() -> Rs2WalkerDoors.sceneDoorAdjacentToEdge(WALL_FROM, WALL_TO)).thenReturn(true);
    }

    @After
    public void tearDown() {
        doors.close();
        pathApi.close();
        Rs2Walker.resetWalkSessionState();
        Rs2Walker.doorAttemptLedger.drainWalkScopedBlocks();
    }

    @Test
    public void firstRefusalBesideADoorHandsTheEdgeToTheDoorPipeline() {
        Rs2WalkerMovement.learnWalledRouteEdge(RAW_PATH, PLAYER, REACHABLE);

        pathApi.verify(() -> Rs2PathApi.learnBlockedEdge(any(), any(), anyString()), Mockito.never());
        assertEquals(WALL_FROM, routeState.walledDoorEdgeFrom);
        assertEquals(WALL_TO, routeState.walledDoorEdgeTo);
        assertEquals(WALL_FROM, routeState.adjacentRefusalFrom);
        assertTrue(routeState.adjacentRefusalSinceMs > 0L);
    }

    @Test
    public void spentClaimLearnsTheEdgeForThisWalkOnlyAndDropsTheClaim() {
        refusedBesideTheDoorSince(System.currentTimeMillis() - WalledDoorClaimPolicy.FRESH_MS - 1_000L);

        Rs2WalkerMovement.learnWalledRouteEdge(RAW_PATH, PLAYER, REACHABLE);

        pathApi.verify(() -> Rs2PathApi.learnBlockedEdge(eq(WALL_FROM), eq(WALL_TO), anyString()));
        List<WorldPoint[]> walkScoped = Rs2Walker.doorAttemptLedger.drainWalkScopedBlocks();
        assertEquals(1, walkScoped.size());
        assertArrayEquals(new WorldPoint[]{WALL_FROM, WALL_TO}, walkScoped.get(0));
        assertNull("a spent claim must not keep the walker yielding to the door handler",
                routeState.walledDoorEdgeFrom);
        assertNull(routeState.walledDoorEdgeTo);
        assertEquals(0L, routeState.walledDoorEdgeAtMs);
        assertNull(routeState.adjacentRefusalFrom);
        assertEquals(0L, routeState.adjacentRefusalSinceMs);
    }

    @Test
    public void anEdgeAlreadyBlockedIsNotWithdrawnAtTheNextWalk() {
        pathApi.when(() -> Rs2PathApi.learnBlockedEdge(any(), any(), anyString())).thenReturn(false);
        refusedBesideTheDoorSince(System.currentTimeMillis() - WalledDoorClaimPolicy.FRESH_MS - 1_000L);

        Rs2WalkerMovement.learnWalledRouteEdge(RAW_PATH, PLAYER, REACHABLE);

        assertTrue(Rs2Walker.doorAttemptLedger.drainWalkScopedBlocks().isEmpty());
        assertNull(routeState.walledDoorEdgeFrom);
    }

    @Test
    public void aPlainWallIsStillLearnedForTheSession() {
        doors.when(() -> Rs2WalkerDoors.sceneDoorAdjacentToEdge(WALL_FROM, WALL_TO)).thenReturn(false);

        Rs2WalkerMovement.learnWalledRouteEdge(RAW_PATH, PLAYER, REACHABLE);

        pathApi.verify(() -> Rs2PathApi.learnBlockedEdge(eq(WALL_FROM), eq(WALL_TO), anyString()));
        assertTrue(Rs2Walker.doorAttemptLedger.drainWalkScopedBlocks().isEmpty());
    }

    private static void refusedBesideTheDoorSince(long sinceMs) {
        long lastRefusal = System.currentTimeMillis() - 500L;
        routeState.walledDoorEdgeFrom = WALL_FROM;
        routeState.walledDoorEdgeTo = WALL_TO;
        routeState.walledDoorEdgeAtMs = lastRefusal;
        routeState.adjacentRefusalFrom = WALL_FROM;
        routeState.adjacentRefusalTo = WALL_TO;
        routeState.adjacentRefusalAtMs = lastRefusal;
        routeState.adjacentRefusalSinceMs = sinceMs;
    }
}
