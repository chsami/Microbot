package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorDetection;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorProbe;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import static net.runelite.client.plugins.microbot.util.walker.Rs2Walker.routeState;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class AdjacentDoorHandlerTest {
    private static final WorldPoint PLAYER = new WorldPoint(3101, 3513, 0);
    private static final WorldPoint FAR_PLAYER = new WorldPoint(3101, 3540, 0);
    private static final WorldPoint FROM = new WorldPoint(3101, 3512, 0);
    private static final WorldPoint TO = new WorldPoint(3101, 3511, 0);
    private static final WorldPoint DOOR_TILE = new WorldPoint(3102, 3512, 0);
    private static final List<WorldPoint> ROUTE = List.of(PLAYER, FROM, TO);

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2WalkerSceneObjects> sceneObjects;
    private MockedStatic<Rs2DoorDetection> detection;
    private MockedStatic<Rs2DoorProbe> probe;
    private MockedStatic<Rs2WalkerDoors> doors;
    private TileObject door;

    @Before
    public void setUp() {
        Rs2Walker.resetWalkSessionState();
        Rs2Walker.doorAttemptLedger.clearBlacklist();

        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(
                inv -> Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()));
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);

        door = mock(TileObject.class);
        when(door.getWorldLocation()).thenReturn(DOOR_TILE);
        ObjectComposition composition = mock(ObjectComposition.class);
        when(composition.getActions()).thenReturn(new String[]{"Open", null, null, null, null});

        sceneObjects = Mockito.mockStatic(Rs2WalkerSceneObjects.class);
        sceneObjects.when(() -> Rs2WalkerSceneObjects.near(TileObject.class, FROM, 3))
                .thenReturn(List.of(door));
        detection = Mockito.mockStatic(Rs2DoorDetection.class);
        detection.when(() -> Rs2DoorDetection.isDoorLikeSceneObject(door)).thenReturn(true);
        detection.when(() -> Rs2DoorDetection.resolveCompositionForDoorProbe(door)).thenReturn(composition);
        probe = Mockito.mockStatic(Rs2DoorProbe.class);
        probe.when(() -> Rs2DoorProbe.isCatalogTransportObject(door)).thenReturn(false);
        doors = Mockito.mockStatic(Rs2WalkerDoors.class, Mockito.CALLS_REAL_METHODS);
        theHandlerDeclines();
    }

    @After
    public void tearDown() {
        doors.close();
        probe.close();
        detection.close();
        sceneObjects.close();
        microbot.close();
        Rs2Walker.doorAttemptLedger.clearBlacklist();
        Rs2Walker.resetWalkSessionState();
    }

    @Test
    public void aShutDoorBesideTheEdgeIsActionable() {
        assertEquals(WalledDoorClaimPolicy.AdjacentDoor.ACTIONABLE,
                Rs2WalkerDoors.classifyDoorAdjacentToEdge(FROM, TO, PLAYER));
        assertEquals("a claimed door out of handler range is still actionable: it is approached first",
                WalledDoorClaimPolicy.AdjacentDoor.ACTIONABLE,
                Rs2WalkerDoors.classifyDoorAdjacentToEdge(FROM, TO, FAR_PLAYER));
    }

    @Test
    public void noDoorBesideTheEdgeIsNone() {
        sceneObjects.when(() -> Rs2WalkerSceneObjects.near(TileObject.class, FROM, 3))
                .thenReturn(Collections.emptyList());

        assertEquals(WalledDoorClaimPolicy.AdjacentDoor.NONE,
                Rs2WalkerDoors.classifyDoorAdjacentToEdge(FROM, TO, PLAYER));
    }

    @Test
    public void aDoorTwoTilesFromTheEdgeIsNone() {
        when(door.getWorldLocation()).thenReturn(new WorldPoint(3103, 3512, 0));

        assertEquals(WalledDoorClaimPolicy.AdjacentDoor.NONE,
                Rs2WalkerDoors.classifyDoorAdjacentToEdge(FROM, TO, PLAYER));
    }

    @Test
    public void aBlacklistedDoorIsBesideTheEdgeButUnusable() {
        Rs2Walker.doorAttemptLedger.blacklistDoor(DOOR_TILE);

        assertEquals(WalledDoorClaimPolicy.AdjacentDoor.UNUSABLE,
                Rs2WalkerDoors.classifyDoorAdjacentToEdge(FROM, TO, PLAYER));
    }

    @Test
    public void aDoorTheWalkerJustOpenedIsBesideTheEdgeButUnusable() {
        Rs2Walker.doorAttemptLedger.markStationaryDoorOpened(DOOR_TILE, System.currentTimeMillis());

        assertEquals(WalledDoorClaimPolicy.AdjacentDoor.UNUSABLE,
                Rs2WalkerDoors.classifyDoorAdjacentToEdge(FROM, TO, PLAYER));
    }

    @Test
    public void aCatalogTransportDoorIsBesideTheEdgeButUnusable() {
        probe.when(() -> Rs2DoorProbe.isCatalogTransportObject(door)).thenReturn(true);

        assertEquals(WalledDoorClaimPolicy.AdjacentDoor.UNUSABLE,
                Rs2WalkerDoors.classifyDoorAdjacentToEdge(FROM, TO, PLAYER));
    }

    @Test
    public void aDoorOutOfHandlerRangeIsNotACandidateSoTheWalkerApproachesInstead() {
        assertEquals(WalledDoorClaimPolicy.AdjacentDoorOutcome.NO_CANDIDATE,
                Rs2WalkerDoors.handleDoorAdjacentToClaimedEdge(FROM, TO, FAR_PLAYER, ROUTE));
    }

    @Test
    public void anUnusableDoorIsNotACandidate() {
        Rs2Walker.doorAttemptLedger.blacklistDoor(DOOR_TILE);

        assertEquals(WalledDoorClaimPolicy.AdjacentDoorOutcome.NO_CANDIDATE,
                Rs2WalkerDoors.handleDoorAdjacentToClaimedEdge(FROM, TO, PLAYER, ROUTE));
    }

    @Test
    public void aHandlerThatDeclinesReportsItSoTheClaimPathCanStillApproach() {
        claimHeldForTheEdge();

        assertEquals(WalledDoorClaimPolicy.AdjacentDoorOutcome.DECLINED,
                Rs2WalkerDoors.handleDoorAdjacentToClaimedEdge(FROM, TO, PLAYER, ROUTE));
        assertFalse("a declined pass never tried the door, so it must not count toward a strike-out",
                routeState.adjacentRefusalDoorAttempted);
    }

    @Test
    public void aClickThatDidNotTraverseStillCountsAsAnAttemptOnTheClaimedEdge() {
        claimHeldForTheEdge();
        doors.when(() -> Rs2WalkerDoors.tryHandleAdjacentBlockedDoorObject(
                any(), any(), any(), any(), any(), any())).thenAnswer(inv -> {
            Rs2Walker.doorAttemptLedger.markAttempt(DOOR_TILE, FROM, TO, System.currentTimeMillis() + 1L);
            return false;
        });

        assertEquals(WalledDoorClaimPolicy.AdjacentDoorOutcome.ATTEMPTED,
                Rs2WalkerDoors.handleDoorAdjacentToClaimedEdge(FROM, TO, PLAYER, ROUTE));
        assertTrue(routeState.adjacentRefusalDoorAttempted);
    }

    @Test
    public void anAttemptForAnEdgeThatIsNotClaimedDoesNotMarkTheClaim() {
        doors.when(() -> Rs2WalkerDoors.tryHandleAdjacentBlockedDoorObject(
                any(), any(), any(), any(), any(), any())).thenReturn(true);

        assertEquals(WalledDoorClaimPolicy.AdjacentDoorOutcome.ATTEMPTED,
                Rs2WalkerDoors.handleDoorAdjacentToClaimedEdge(FROM, TO, PLAYER, ROUTE));
        assertFalse(routeState.adjacentRefusalDoorAttempted);
    }

    private void theHandlerDeclines() {
        doors.when(() -> Rs2WalkerDoors.tryHandleAdjacentBlockedDoorObject(
                any(), any(), any(), any(), any(), any())).thenReturn(false);
    }

    private static void claimHeldForTheEdge() {
        long now = System.currentTimeMillis();
        routeState.adjacentRefusalFrom = FROM;
        routeState.adjacentRefusalTo = TO;
        routeState.adjacentRefusalAtMs = now;
        routeState.adjacentRefusalSinceMs = now;
    }
}
