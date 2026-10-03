package net.runelite.client.plugins.microbot.util.walker.door;

import net.runelite.api.coords.WorldPoint;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntPredicate;

import static org.junit.Assert.assertEquals;

public class FirstRouteInteractionSelectorTest {

    private static final int X = 3200;
    private static final int Y = 3200;
    private static final WorldPoint PLAYER = point(0);
    private static final List<WorldPoint> ROUTE = eastRoute(10);

    private static WorldPoint point(int offset) {
        return new WorldPoint(X + offset, Y, 0);
    }

    private static List<WorldPoint> eastRoute(int lastOffset) {
        List<WorldPoint> route = new ArrayList<>();
        for (int offset = 0; offset <= lastOffset; offset++) {
            route.add(point(offset));
        }
        return route;
    }

    private static Map<WorldPoint, Integer> reachableThrough(int lastOffset) {
        Map<WorldPoint, Integer> reachable = new HashMap<>();
        for (int offset = 0; offset <= lastOffset; offset++) {
            reachable.put(point(offset), offset);
        }
        return reachable;
    }

    private static int firstInteraction(WorldPoint player, Map<WorldPoint, Integer> reachable,
                                        int range, IntPredicate pendingInteraction,
                                        IntPredicate transport) {
        return FirstRouteInteractionSelector.firstInteractionEdge(ROUTE, 0, 10, player, reachable,
                range, pendingInteraction, transport);
    }

    @Test
    public void firstReachableDoorIsSelectedFromRangeBeforeSecondDoor() {
        assertEquals(4, firstInteraction(PLAYER, reachableThrough(4), 10,
                edge -> edge == 4 || edge == 8, edge -> false));
    }

    @Test
    public void unresolvedFirstDoorStillOwnsNextPassEvenWhenBothNearSidesLookReachable() {
        Map<WorldPoint, Integer> reachable = reachableThrough(10);
        AtomicInteger secondDoorProbes = new AtomicInteger();
        IntPredicate pending = edge -> {
            if (edge == 8) {
                secondDoorProbes.incrementAndGet();
            }
            return edge == 4 || edge == 8;
        };

        assertEquals(4, firstInteraction(PLAYER, reachable, 10, pending, edge -> false));
        assertEquals("an unsuccessful or deferred first interaction leaves the same route door pending",
                4, firstInteraction(PLAYER, reachable, 10, pending, edge -> false));
        assertEquals("the second door must not even be probed", 0, secondDoorProbes.get());
    }

    @Test
    public void openedFirstDoorAllowsSecondDoorWhenItsNearSideBecomesReachable() {
        assertEquals(8, firstInteraction(PLAYER, reachableThrough(8), 10,
                edge -> edge == 8, edge -> false));
    }

    @Test
    public void wallBeforeDoorStopsScanRatherThanClickingThroughIt() {
        AtomicInteger doorProbes = new AtomicInteger();
        assertEquals(-1, firstInteraction(PLAYER, reachableThrough(2), 10, edge -> {
            if (edge == 4) {
                doorProbes.incrementAndGet();
            }
            return edge == 4;
        }, edge -> false));
        assertEquals(0, doorProbes.get());
    }

    @Test
    public void earlierNonObjectTransportStopsScanBeforeDoor() {
        assertEquals(-1, firstInteraction(PLAYER, reachableThrough(10), 10,
                edge -> edge == 4, edge -> edge == 2));
        FirstRouteInteractionSelector.Selection selection = FirstRouteInteractionSelector.selectFirst(
                ROUTE, 0, 10, PLAYER, reachableThrough(10), 10,
                edge -> edge == 4, edge -> edge == 2);
        assertEquals(FirstRouteInteractionSelector.Kind.BLOCKED_TRANSPORT, selection.kind());
        assertEquals(2, selection.edgeIndex());
    }

    @Test
    public void crossPlaneObjectTransportCanBeSelectedBeforeAVisibleLaterDoor() {
        List<WorldPoint> route = eastRoute(10);
        route.set(3, new WorldPoint(X + 3, Y, 1));
        assertEquals(2, FirstRouteInteractionSelector.firstInteractionEdge(
                route, 0, 10, PLAYER, reachableThrough(2), 10,
                edge -> edge == 2 || edge == 6, edge -> edge == 2));
    }

    @Test
    public void unhandledCrossPlaneTransportStopsBeforeLaterDoor() {
        List<WorldPoint> route = eastRoute(10);
        route.set(3, new WorldPoint(X + 3, Y, 1));
        FirstRouteInteractionSelector.Selection selection = FirstRouteInteractionSelector.selectFirst(
                route, 0, 10, PLAYER, reachableThrough(2), 10,
                edge -> edge == 6, edge -> edge == 2);
        assertEquals(FirstRouteInteractionSelector.Kind.BLOCKED_TRANSPORT, selection.kind());
        assertEquals(2, selection.edgeIndex());
    }

    @Test
    public void transportOutsideInteractionRangeStillBlocksLaterInteraction() {
        FirstRouteInteractionSelector.Selection selection = FirstRouteInteractionSelector.selectFirst(
                ROUTE, 0, 10, PLAYER, reachableThrough(10), 3,
                edge -> edge == 5 || edge == 7, edge -> edge == 5);
        assertEquals(FirstRouteInteractionSelector.Kind.NONE, selection.kind());
        // The scan stops at the range boundary before either later interaction is in range.
        assertEquals(-1, selection.edgeIndex());

        selection = FirstRouteInteractionSelector.selectFirst(ROUTE, 5, 5, PLAYER,
                reachableThrough(10), 3, edge -> edge == 5 || edge == 7, edge -> edge == 5);
        assertEquals(FirstRouteInteractionSelector.Kind.BLOCKED_TRANSPORT, selection.kind());
        assertEquals(5, selection.edgeIndex());
    }

    @Test
    public void blockedObjectOriginUsesReachableAdjacentApproach() {
        List<WorldPoint> route = eastRoute(10);
        route.set(5, new WorldPoint(X + 5, Y, 1));
        assertEquals("the ladder origin itself is blocked, but the previous route tile is reachable",
                4, FirstRouteInteractionSelector.firstInteractionEdge(
                        route, 0, 10, PLAYER, reachableThrough(3), 10,
                        edge -> edge == 4, edge -> edge == 4));
    }

    @Test
    public void startEdgeTransportCanUseAdjacentReachablePlayerTile() {
        List<WorldPoint> route = new ArrayList<>();
        route.add(point(1));
        route.add(new WorldPoint(X + 1, Y, 1));
        assertEquals(0, FirstRouteInteractionSelector.firstInteractionEdge(
                route, 0, 1, PLAYER, reachableThrough(0), 10,
                edge -> edge == 0, edge -> edge == 0));
    }

    @Test
    public void wallBeforeUnrelatedLaterObjectTransportStillStopsScan() {
        assertEquals(-1, firstInteraction(PLAYER, reachableThrough(2), 10,
                edge -> edge == 5, edge -> edge == 5));
    }

    @Test
    public void outsideInteractionRangeDefersDoorUntilApproach() {
        assertEquals(-1, firstInteraction(PLAYER, reachableThrough(10), 3,
                edge -> edge == 5, edge -> false));
        assertEquals(5, firstInteraction(point(2), reachableThrough(10), 3,
                edge -> edge == 5, edge -> false));
    }

    @Test
    public void scanBacktracksToFirstGateEvenWhenAnchorIsBeyondIt() {
        int start = FirstRouteInteractionSelector.scanStartEdge(ROUTE, 6, point(3),
                null, null, false);
        assertEquals(4, start);
        assertEquals("both door sides may look reachable, but edge 4 is still the first interaction",
                4, FirstRouteInteractionSelector.firstInteractionEdge(ROUTE, start, 6,
                        point(3), reachableThrough(10), 10,
                        edge -> edge == 4 || edge == 8, edge -> false));
    }

    @Test
    public void exactRecentTransportLandingAdvancesPastItsBacktrackedEdge() {
        int start = FirstRouteInteractionSelector.scanStartEdge(ROUTE, 5, point(4),
                point(3), point(4), true);
        assertEquals(4, start);
        assertEquals(5, FirstRouteInteractionSelector.firstInteractionEdge(ROUTE, start, 6,
                point(4), reachableThrough(10), 10,
                edge -> edge == 3 || edge == 5, edge -> edge == 3));
    }

    @Test
    public void recoveryScanKeepsUncrossedTransportButFindsDoorAfterObservedLanding() {
        int beforeCrossing = FirstRouteInteractionSelector.scanStartEdge(ROUTE, 6, point(3),
                point(3), point(4), false, 3);
        assertEquals(3, beforeCrossing);
        assertEquals(-1, FirstRouteInteractionSelector.firstInteractionEdge(ROUTE, beforeCrossing, 5,
                point(3), reachableThrough(10), 10,
                edge -> edge == 5, edge -> edge == 3));

        int afterCrossing = FirstRouteInteractionSelector.scanStartEdge(ROUTE, 6, point(4),
                point(3), point(4), true, 3);
        assertEquals(4, afterCrossing);
        assertEquals(5, FirstRouteInteractionSelector.firstInteractionEdge(ROUTE, afterCrossing, 5,
                point(4), reachableThrough(10), 10,
                edge -> edge == 5, edge -> edge == 3));
    }

    @Test
    public void recentTransportAtFinalBacktrackedEdgeCanBeSkipped() {
        List<WorldPoint> route = eastRoute(5);
        assertEquals(4, FirstRouteInteractionSelector.scanStartEdge(route, 5, point(4),
                point(3), point(4), true, 2));
    }

    @Test
    public void recentLongTransportLandingOneTileOffDestinationAdvancesPastExactEdge() {
        List<WorldPoint> route = new ArrayList<>();
        route.add(point(0));
        route.add(point(1));
        route.add(point(2));
        route.add(point(3));
        route.add(point(5));
        route.add(point(6));
        WorldPoint offTileLanding = new WorldPoint(X + 5, Y + 1, 0);
        assertEquals(4, FirstRouteInteractionSelector.scanStartEdge(route, 4,
                offTileLanding, point(3), point(5), true));
        assertEquals("being equally close to origin and destination does not prove crossing",
                2, FirstRouteInteractionSelector.scanStartEdge(route, 4,
                        new WorldPoint(X + 4, Y + 1, 0), point(3), point(5), true));
    }

    @Test
    public void futureDetourTransportIsNotSkippedBecauseDestinationIsCloser() {
        List<WorldPoint> route = new ArrayList<>();
        route.add(new WorldPoint(X, Y, 0));
        route.add(new WorldPoint(X + 1, Y, 0));
        route.add(new WorldPoint(X + 5, Y + 2, 0));
        route.add(new WorldPoint(X + 4, Y + 2, 0));
        route.add(new WorldPoint(X + 3, Y + 2, 0));
        int start = FirstRouteInteractionSelector.scanStartEdge(route, 3, PLAYER,
                route.get(2), route.get(3), false);
        assertEquals(1, start);
        assertEquals(2, FirstRouteInteractionSelector.firstInteractionEdge(route, start, 4,
                PLAYER, null, 10, edge -> edge == 2 || edge == 3, edge -> edge == 2));
    }

    @Test
    public void expiredTransportBehindPlayerAllowsDoorAhead() {
        assertDoorAfterCrossedTransport(point(3), point(4));
    }

    @Test
    public void transportCrossedByPlainWalkingAllowsDoorAhead() {
        assertDoorAfterCrossedTransport(null, null);
    }

    private static void assertDoorAfterCrossedTransport(WorldPoint origin, WorldPoint destination) {
        int anchor = 4;
        int start = FirstRouteInteractionSelector.scanStartEdge(ROUTE, anchor, point(4),
                origin, destination, false);
        assertEquals(2, start);
        assertEquals(5, FirstRouteInteractionSelector.firstInteractionEdge(ROUTE, start, 6,
                point(4), reachableThrough(10), 10, edge -> edge == 5,
                edge -> edge == 3 && FirstRouteInteractionSelector.isTransportAtOrAhead(edge, anchor)));
        assertEquals(false, FirstRouteInteractionSelector.isTransportAtOrAhead(3, anchor));
    }

    @Test
    public void transportAtOrAheadOfAnchorStillBlocksDoor() {
        for (int transport : new int[]{4, 5}) {
            FirstRouteInteractionSelector.Selection selection = FirstRouteInteractionSelector.selectFirst(
                    ROUTE, 2, 8, point(4), reachableThrough(10), 10, edge -> edge == 7,
                    edge -> edge == transport && FirstRouteInteractionSelector.isTransportAtOrAhead(edge, 4));
            assertEquals(FirstRouteInteractionSelector.Kind.BLOCKED_TRANSPORT, selection.kind());
            assertEquals(transport, selection.edgeIndex());
        }
    }

}
