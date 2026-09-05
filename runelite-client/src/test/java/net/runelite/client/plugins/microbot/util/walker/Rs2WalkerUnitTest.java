package net.runelite.client.plugins.microbot.util.walker;
import net.runelite.client.plugins.microbot.util.walker.stall.Rs2WalkerStallPolicy;
import net.runelite.client.plugins.microbot.util.walker.geometry.WalkerPathGeometry;
import net.runelite.client.plugins.microbot.util.walker.obstacle.Rs2ObstacleHandler;
import net.runelite.client.plugins.microbot.util.walker.recovery.RouteRecovery;
import net.runelite.client.plugins.microbot.util.walker.door.Rs2DoorGeometry;

import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeoutException;
import java.util.function.Predicate;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests covering the pure-logic walker helpers extracted during the Tier-3
 * robustness sprint: sidestep-recovery ranking (#15), minimap forward-scan (#21),
 * quest-lock dialogue heuristic (#19), and UNREACHABLE telemetry (#22).
 *
 * The main walker is static-heavy and deeply coupled to the RuneLite client — the
 * integration contract is covered by {@link Rs2WalkerIntegrationTest}. These tests
 * pin the pieces that CAN run without a live client so regressions in the
 * refactored code paths get caught by {@code runUnitTests}.
 */
public class Rs2WalkerUnitTest {

    @Test
    public void teleportItemLeafActionSupportsNestedUpstreamLabels() {
        assertEquals("rimmington",
                Rs2WalkerTransports.teleportItemLeafAction("Max cape: POH Portals: Rimmington"));
        assertEquals("fishing guild",
                Rs2WalkerTransports.teleportItemLeafAction("Max cape: Fishing Teleports: Fishing Guild"));
        assertEquals("teleport",
                Rs2WalkerTransports.teleportItemLeafAction("Quest point cape: Teleport"));
        assertEquals("chronicle", Rs2WalkerTransports.teleportItemLeafAction("Chronicle"));
        assertEquals("", Rs2WalkerTransports.teleportItemLeafAction(null));
    }

    @Test
    public void teleportWildernessLimitIsInclusiveWithoutOffByOne() {
        assertTrue(Rs2WalkerTransports.isTeleportAllowedAtWildernessLevel(20, 20));
        assertFalse(Rs2WalkerTransports.isTeleportAllowedAtWildernessLevel(21, 20));
    }

    @Test
    public void quetzalDestinationLabelsUseCurrentLandingAndMapText() {
        assertEquals("Quetzacalli Gorge",
                Rs2WalkerTransports.quetzalMapLabelForDestination(new WorldPoint(1510, 3222, 0)));
        assertEquals("Cam Torum",
                Rs2WalkerTransports.quetzalMapLabelForDestination(new WorldPoint(1446, 3108, 0)));
    }

    @Test
    public void terminalTravelTransport_onlyMatchesShipNpcAndBoat() {
        assertTrue(Rs2WalkerTransports.isTerminalTravelTransport(TransportType.SHIP));
        assertTrue(Rs2WalkerTransports.isTerminalTravelTransport(TransportType.NPC));
        assertTrue(Rs2WalkerTransports.isTerminalTravelTransport(TransportType.BOAT));

        assertFalse(Rs2WalkerTransports.isTerminalTravelTransport(TransportType.CHARTER_SHIP));
        assertFalse(Rs2WalkerTransports.isTerminalTravelTransport(TransportType.TRANSPORT));
        assertFalse(Rs2WalkerTransports.isTerminalTravelTransport(null));
    }

    /**
     * The direct-travel early release (Mountain Guide burned the whole 5s dialogue wait standing
     * at Auburn Valley): landed means moved-from-start AND at the destination, same plane.
     */
    @Test
    public void terminalLanding_requiresMovementPlusDestinationProximityOnTheSamePlane() {
        WorldPoint dest = new WorldPoint(1700, 3141, 0);
        WorldPoint origin = new WorldPoint(3280, 3412, 0);

        assertTrue("landed at the exact destination after travelling",
                Rs2WalkerTransports.hasLandedAtTerminalDestination(dest, origin, dest));
        assertTrue("the landing AREA counts: the guide dropped the player 4 tiles from the tile",
                Rs2WalkerTransports.hasLandedAtTerminalDestination(new WorldPoint(1704, 3141, 0), origin, dest));
        assertFalse("6 tiles out is not landed",
                Rs2WalkerTransports.hasLandedAtTerminalDestination(new WorldPoint(1706, 3141, 0), origin, dest));
        assertFalse("still standing where the wait began proves nothing (short-crossing guard)",
                Rs2WalkerTransports.hasLandedAtTerminalDestination(dest, dest, dest));
        assertFalse("a short crossing: stepping about near an origin beside the destination must "
                        + "not release — the player has not closed distance on the destination",
                Rs2WalkerTransports.hasLandedAtTerminalDestination(
                        new WorldPoint(1704, 3143, 0), new WorldPoint(1704, 3142, 0), dest));
        assertFalse("wrong plane is not the destination",
                Rs2WalkerTransports.hasLandedAtTerminalDestination(new WorldPoint(1700, 3141, 1), origin, dest));
        assertFalse(Rs2WalkerTransports.hasLandedAtTerminalDestination(null, origin, dest));
        assertFalse(Rs2WalkerTransports.hasLandedAtTerminalDestination(dest, origin, null));
    }

    @Test
    public void terminalNpcInteractionCandidates_onlyFallbackForLegacyShipLabels() {
        assertEquals(Arrays.asList("Musa Point", "Travel"),
                Rs2WalkerTransports.terminalNpcInteractionCandidates(TransportType.SHIP, "Musa Point"));
        assertEquals(Collections.singletonList("Travel"),
                Rs2WalkerTransports.terminalNpcInteractionCandidates(TransportType.SHIP, "Travel"));
        assertEquals(Collections.singletonList("Talk-to"),
                Rs2WalkerTransports.terminalNpcInteractionCandidates(TransportType.SHIP, "Talk-to"));
        assertEquals(Collections.singletonList("Follow"),
                Rs2WalkerTransports.terminalNpcInteractionCandidates(TransportType.NPC, "Follow"));
        assertTrue(Rs2WalkerTransports.terminalNpcInteractionCandidates(TransportType.NPC, null).isEmpty());
    }

    @Test
    public void terminalTravelAttempt_isOncePerExactEdgeUntilWalkStateReset() {
        Transport ship = portSarimToMusaShip();

        assertTrue(Rs2WalkerTransports.markTerminalTravelAttempt(ship));
        assertFalse(Rs2WalkerTransports.markTerminalTravelAttempt(ship));

        Rs2Walker.clearWalkerDedupeForTesting();
        assertTrue(Rs2WalkerTransports.markTerminalTravelAttempt(ship));
    }

    @Test
    public void terminalTravelLanding_acceptsExactOrImmediateContinuationOnly() {
        Transport ship = portSarimToMusaShip();
        WorldPoint modernGroundLanding = new WorldPoint(2956, 3146, 0);
        List<WorldPoint> modernPath = Arrays.asList(
                ship.getOrigin(),
                ship.getDestination(),
                modernGroundLanding);

        assertTrue(Rs2WalkerTransports.hasReachedTerminalTravelLanding(
                ship, modernPath, 1, ship.getDestination()));
        assertTrue(Rs2WalkerTransports.hasReachedTerminalTravelLanding(
                ship, modernPath, 1, modernGroundLanding));
        assertFalse("standing at the origin is not a completed trip",
                Rs2WalkerTransports.hasReachedTerminalTravelLanding(ship, modernPath, 1, ship.getOrigin()));

        List<WorldPoint> loopingPath = Arrays.asList(
                ship.getOrigin(),
                ship.getDestination(),
                new WorldPoint(2957, 3143, 1),
                modernGroundLanding);
        assertFalse("an arbitrary later path point must not prove terminal arrival",
                Rs2WalkerTransports.hasReachedTerminalTravelLanding(ship, loopingPath, 1, modernGroundLanding));
        assertFalse(Rs2WalkerTransports.hasReachedTerminalTravelLanding(
                ship, modernPath, 1, new WorldPoint(3200, 3200, 0)));
    }

    private static Transport portSarimToMusaShip() {
        return new Transport(
                new WorldPoint(3029, 3217, 0),
                new WorldPoint(2956, 3143, 1),
                "Musa Point",
                TransportType.SHIP,
                false,
                "Musa Point",
                "Captain Tobias",
                3644,
                10);
    }

    @Test
    public void terminalTravelObjectCandidate_matchesConfiguredSemanticTargetNearOrigin() {
        Transport ferry = new Transport(
                new WorldPoint(3271, 3144, 0),
                new WorldPoint(3148, 2843, 0),
                "",
                TransportType.BOAT,
                true,
                "Board",
                "Ferry",
                41311,
                8);

        assertTrue(Rs2WalkerTransports.isTerminalTravelObjectCompositionCandidate(
                ferry,
                ferry.getOrigin(),
                "<col=ffff00>Ferry</col>",
                new String[]{"<col=ff9040>Board</col>"}));
        assertTrue("nearby multi-tile object anchors remain eligible",
                Rs2WalkerTransports.isTerminalTravelObjectCompositionCandidate(
                        ferry,
                        new WorldPoint(3273, 3144, 0),
                        "Ferry",
                        new String[]{"Board"}));
        assertFalse(Rs2WalkerTransports.isTerminalTravelObjectCompositionCandidate(
                ferry, ferry.getOrigin(), "Boat", new String[]{"Board"}));
        assertFalse(Rs2WalkerTransports.isTerminalTravelObjectCompositionCandidate(
                ferry, ferry.getOrigin(), "Ferry", new String[]{"Travel"}));
        assertFalse(Rs2WalkerTransports.isTerminalTravelObjectCompositionCandidate(
                ferry, new WorldPoint(3275, 3144, 0), "Ferry", new String[]{"Board"}));

        // The dispatch resolves the scene object BEFORE the (contains-matching) NPC lookup, so this
        // matcher is what keeps an NPC terminal from being hijacked by a same-named object. Veos is
        // the case that must stay rejected: his row's id column holds an NPC id, and his menu offers
        // Talk-to rather than the row's destination label, so no object can satisfy it.
        Transport veos = new Transport(
                new WorldPoint(3055, 3245, 0),
                new WorldPoint(1824, 3695, 1),
                "Port Piscarilius",
                TransportType.SHIP,
                true,
                "Port Piscarilius",
                "Veos",
                10724,
                6);
        assertFalse("an NPC terminal must not resolve to a scene object",
                Rs2WalkerTransports.isTerminalTravelObjectCompositionCandidate(
                        veos, veos.getOrigin(), "Veos", new String[]{"Talk-to"}));

        Transport ordinaryObject = new Transport(
                ferry.getOrigin(), ferry.getDestination(), "", TransportType.TRANSPORT,
                true, "Board", "Ferry", 41311, 8);
        assertFalse(Rs2WalkerTransports.isTerminalTravelObjectCompositionCandidate(
                ordinaryObject, ferry.getOrigin(), "Ferry", new String[]{"Board"}));
    }

    @Test
    public void alKharidTollLanding_requiresExactSelectedDestination() {
        Transport eastbound = new Transport(
                new WorldPoint(3267, 3227, 0),
                new WorldPoint(3268, 3227, 0),
                "Gate",
                TransportType.TRANSPORT,
                false,
                "Pay-toll(10gp)",
                "Gate",
                net.runelite.api.ObjectID.CITY_GATE_2786,
                2);

        assertTrue(Rs2WalkerTransports.hasReachedAlKharidTollDestination(
                eastbound, eastbound.getDestination()));
        assertFalse("the adjacent origin must never count as a crossing",
                Rs2WalkerTransports.hasReachedAlKharidTollDestination(eastbound, eastbound.getOrigin()));
        assertFalse(Rs2WalkerTransports.hasReachedAlKharidTollDestination(
                eastbound, new WorldPoint(3268, 3228, 0)));
        assertFalse(Rs2WalkerTransports.hasReachedAlKharidTollDestination(eastbound, null));
    }

    @Test
    public void alKharidTollLanding_rejectsUnrelatedTransport() {
        Transport door = new Transport(
                new WorldPoint(3152, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                "Door",
                TransportType.TRANSPORT,
                false,
                "Open",
                "Door",
                136);

        assertFalse(Rs2WalkerTransports.hasReachedAlKharidTollDestination(
                door, door.getDestination()));
    }

    @Test
    public void alKharidTollSegment_matchesOnlyCrossGateEdges() {
        assertTrue(Rs2WalkerDoors.isAlKharidTollGateSegment(
                new WorldPoint(3267, 3227, 0), new WorldPoint(3268, 3227, 0)));
        assertTrue(Rs2WalkerDoors.isAlKharidTollGateSegment(
                new WorldPoint(3268, 3228, 0), new WorldPoint(3267, 3228, 0)));

        assertFalse("an along-gate step is not a crossing",
                Rs2WalkerDoors.isAlKharidTollGateSegment(
                        new WorldPoint(3267, 3227, 0), new WorldPoint(3267, 3228, 0)));
        assertFalse(Rs2WalkerDoors.isAlKharidTollGateSegment(
                new WorldPoint(3267, 3227, 0), new WorldPoint(3268, 3227, 1)));
        assertFalse(Rs2WalkerDoors.isAlKharidTollGateSegment(
                new WorldPoint(3152, 3363, 0), new WorldPoint(3153, 3363, 0)));
    }

    @Test
    public void alKharidTollObjectCandidate_requiresGateActionAndSelectedEdgeLocation() {
        Transport payToll = alKharidGateTransport("Pay-toll(10gp)");

        assertTrue(Rs2WalkerTransports.isAlKharidTollGateCompositionCandidate(
                payToll,
                new WorldPoint(3268, 3227, 0),
                "Gate",
                new String[]{"Open", "<col=ff9040>Pay-toll(10gp)</col>"}));
        assertFalse("a stale id collision must not make an unrelated object eligible",
                Rs2WalkerTransports.isAlKharidTollGateCompositionCandidate(
                        payToll,
                        new WorldPoint(3268, 3227, 0),
                        "Lever",
                        new String[]{"Pay-toll(10gp)"}));
        assertFalse(Rs2WalkerTransports.isAlKharidTollGateCompositionCandidate(
                payToll,
                new WorldPoint(3268, 3227, 0),
                "Gate",
                new String[]{"Open"}));
        assertFalse(Rs2WalkerTransports.isAlKharidTollGateCompositionCandidate(
                payToll,
                new WorldPoint(3269, 3227, 0),
                "Gate",
                new String[]{"Pay-toll(10gp)"}));

        Transport open = alKharidGateTransport("Open");
        assertTrue(Rs2WalkerTransports.isAlKharidTollGateCompositionCandidate(
                open,
                new WorldPoint(3267, 3228, 0),
                "City gate",
                new String[]{"Open"}));
    }

    private static Transport alKharidGateTransport(String action) {
        return new Transport(
                new WorldPoint(3267, 3227, 0),
                new WorldPoint(3268, 3227, 0),
                "Gate",
                TransportType.TRANSPORT,
                false,
                action,
                "Gate",
                net.runelite.api.ObjectID.CITY_GATE_2786,
                2);
    }

    @Test
    public void canoeStationsSelectTheirOwnMapInterfaceAndUnknownIdsFailClosed() {
        assertEquals(InterfaceID.CanoeMapLum.MAIN_MAP, Rs2WalkerTransports.canoeMapMainComponentId(12163));
        assertEquals(InterfaceID.CanoeMapLum.DESTINATIONS,
                Rs2WalkerTransports.canoeMapDestinationsComponentId(39638));
        assertEquals(InterfaceID.CanoeMapDougne.MAIN_MAP,
                Rs2WalkerTransports.canoeMapMainComponentId(60845));
        assertEquals(InterfaceID.CanoeMapDougne.DESTINATIONS,
                Rs2WalkerTransports.canoeMapDestinationsComponentId(60849));
        assertEquals(-1, Rs2WalkerTransports.canoeMapMainComponentId(99999));
        assertEquals(-1, Rs2WalkerTransports.canoeMapDestinationsComponentId(99999));
    }

    @Test
    public void recoveryReplanTestHookIsTestOnlyTargetBoundAndOneShot() {
        String previousTestMode = System.getProperty("microbot.test.mode");
        WorldPoint previousTarget = Rs2Walker.currentTarget;
        try {
            System.clearProperty("microbot.test.mode");
            Rs2Walker.currentTarget = new WorldPoint(3029, 3217, 0);
            assertFalse(Rs2Walker.requestRecoveryReplanForTest());
            assertFalse(Rs2Walker.consumeRecoveryReplanForTest());

            System.setProperty("microbot.test.mode", "true");
            Rs2Walker.currentTarget = null;
            assertFalse(Rs2Walker.requestRecoveryReplanForTest());

            Rs2Walker.currentTarget = new WorldPoint(3029, 3217, 0);
            assertTrue(Rs2Walker.requestRecoveryReplanForTest());
            assertTrue(Rs2Walker.consumeRecoveryReplanForTest());
            assertFalse("one request must be consumed exactly once",
                    Rs2Walker.consumeRecoveryReplanForTest());
        } finally {
            Rs2Walker.clearWalkerDedupeForTesting();
            Rs2Walker.currentTarget = previousTarget;
            if (previousTestMode == null) {
                System.clearProperty("microbot.test.mode");
            } else {
                System.setProperty("microbot.test.mode", previousTestMode);
            }
        }
    }

    @Test
    public void clientThreadTimeoutDetectionWalksTheCauseChain() {
        assertTrue(Rs2Walker.isClientThreadReadTimeout(
                new RuntimeException("outer", new RuntimeException(
                        "Timed out waiting for client thread", new TimeoutException()))));
        assertFalse(Rs2Walker.isClientThreadReadTimeout(
                new RuntimeException("ordinary failure")));
        assertFalse(Rs2Walker.isClientThreadReadTimeout(null));
    }

    @Test
    public void collisionFreeRouteIndexFallbackIsBoundedAndDistanceTagged() {
        WorldPoint origin = new WorldPoint(3200, 3200, 2);
        Map<WorldPoint, Integer> nearby = Rs2WalkerDoors.nearbyTilesIgnoringCollision(origin, 2);

        assertEquals(25, nearby.size());
        assertEquals(Integer.valueOf(0), nearby.get(origin));
        assertEquals(Integer.valueOf(2), nearby.get(new WorldPoint(3202, 3202, 2)));
        assertFalse(nearby.containsKey(new WorldPoint(3203, 3200, 2)));
        assertTrue(Rs2WalkerDoors.nearbyTilesIgnoringCollision(null, 2).isEmpty());
        assertTrue(Rs2WalkerDoors.nearbyTilesIgnoringCollision(origin, -1).isEmpty());
    }

    @Before
    public void resetTelemetry() {
        Rs2Walker.clearWalkerDedupeForTesting();
        Rs2Walker.Telemetry.reset();
        Rs2WalkerDoors.doorAttemptLedgerForTesting().clearBlacklist();
    }

    @After
    public void tearDown() {
        Rs2Walker.clearWalkerDedupeForTesting();
        Rs2Walker.Telemetry.reset();
        Rs2WalkerDoors.doorAttemptLedgerForTesting().clearBlacklist();
    }

    @Test
    public void shortTransportSuppression_coversAdjacentSamePlaneTransports() {
        Transport door = new Transport(
                new WorldPoint(3123, 3360, 0),
                new WorldPoint(3123, 3361, 0),
                "Door",
                TransportType.TRANSPORT,
                false,
                "Open",
                "Door",
                136);

        assertEquals(new HashSet<>(Arrays.asList(
                        new WorldPoint(3123, 3360, 0),
                        new WorldPoint(3123, 3361, 0))),
                Rs2WalkerTransports.adjacentSamePlaneTransportSuppressionPoints(door, null));
    }

    /**
     * Agility shortcuts catalogued as two opposing adjacent entries must be suppressible like doors.
     * Observed live near (3150..3151, 3363): the walker crossed the shortcut, the strict landing
     * check failed because it landed a tile off the catalogued destination, so suppression was
     * skipped — leaving the inverse entry immediately eligible. It took the same shortcut straight
     * back and could not recover. Suppression is deliberately type-agnostic; only adjacency and
     * plane matter.
     */
    @Test
    public void adjacentTransportSuppression_coversAgilityShortcuts() {
        Transport shortcut = new Transport(
                new WorldPoint(3151, 3363, 0),
                new WorldPoint(3150, 3363, 0),
                "Shortcut",
                TransportType.AGILITY_SHORTCUT,
                false,
                "Climb-over",
                "Stile",
                0);

        assertEquals("An adjacent same-plane agility shortcut must yield both tiles for suppression",
                new HashSet<>(Arrays.asList(
                        new WorldPoint(3151, 3363, 0),
                        new WorldPoint(3150, 3363, 0))),
                Rs2WalkerTransports.adjacentSamePlaneTransportSuppressionPoints(shortcut, null));
    }

    @Test
    public void shortTransportSuppression_coversTwoTileDoorEdges() {
        Transport puzzleDoor = new Transport(
                new WorldPoint(3106, 9765, 0),
                new WorldPoint(3104, 9765, 0),
                "Draynor basement puzzle door",
                TransportType.TRANSPORT,
                false,
                "Open",
                "Door",
                137);

        assertEquals(new HashSet<>(Arrays.asList(
                        new WorldPoint(3106, 9765, 0),
                        new WorldPoint(3104, 9765, 0))),
                Rs2WalkerTransports.adjacentSamePlaneTransportSuppressionPoints(puzzleDoor, null));
    }

    @Test
    public void shortTransportSuppression_ignoresLongTransports() {
        Transport ladder = new Transport(
                new WorldPoint(3092, 3361, 0),
                new WorldPoint(3117, 9753, 0),
                "Ladder",
                TransportType.TRANSPORT,
                false,
                "Climb-down",
                "Ladder",
                133);

        assertTrue(Rs2WalkerTransports.adjacentSamePlaneTransportSuppressionPoints(ladder, null).isEmpty());
    }

    @Test
    public void rangedObjectTransportDelegatesOriginReachabilityToServer() {
        assertTrue(Rs2WalkerTransports.requiresReachableObjectOrigin(false));
        assertFalse(Rs2WalkerTransports.requiresReachableObjectOrigin(true));
    }

    @Test
    public void rangedDoorRequiresReachableNearSideSoItCannotClickThroughEarlierDoor() {
        WorldPoint firstNearSide = new WorldPoint(2991, 3341, 1);
        WorldPoint secondNearSide = new WorldPoint(2990, 3337, 1);
        Map<WorldPoint, Integer> reachable = new HashMap<>();
        reachable.put(firstNearSide, 0);

        assertTrue(Rs2WalkerDoors.isDoorNearSideReachable(reachable, firstNearSide));
        assertFalse(Rs2WalkerDoors.isDoorNearSideReachable(reachable, secondNearSide));
        assertTrue(Rs2WalkerDoors.isDoorNearSideReachable(null, secondNearSide));
    }

    @Test
    public void shouldRecalculatePathAfterTransport_includesOriginlessTeleport() {
        Transport varrockTeleport = new Transport(
                new WorldPoint(3213, 3424, 0),
                "Varrock Teleport",
                TransportType.TELEPORTATION_SPELL,
                false,
                20,
                Collections.emptyMap());

        assertTrue(Rs2WalkerTransports.shouldRecalculatePathAfterTransport(varrockTeleport));
    }

    @Test
    public void rawTransportDispatch_allowsImmediateOriginlessTeleportEdge() {
        List<WorldPoint> rawPath = Arrays.asList(
                new WorldPoint(2610, 3100, 0),
                new WorldPoint(3213, 3424, 0),
                new WorldPoint(3214, 3424, 0));

        assertTrue(Rs2Walker.isRawTransportOriginNearPlayer(
                rawPath, 0, new WorldPoint(2610, 3100, 0), 2));
    }

    @Test
    public void rawTransportDispatch_defersFutureTransportUntilApproach() {
        List<WorldPoint> rawPath = Arrays.asList(
                new WorldPoint(2610, 3100, 0),
                new WorldPoint(2611, 3100, 0),
                new WorldPoint(2623, 3093, 0),
                new WorldPoint(3303, 3333, 0));

        assertFalse("A future edge inside the broad handler window must not start a long run",
                Rs2Walker.isRawTransportOriginNearPlayer(
                        rawPath, 2, new WorldPoint(2610, 3100, 0), 2));
        assertTrue("The same edge becomes dispatchable once the route has approached it",
                Rs2Walker.isRawTransportOriginNearPlayer(
                        rawPath, 2, new WorldPoint(2622, 3093, 0), 2));
    }

    @Test
    public void rawTransportDispatch_rejectsOtherPlane() {
        List<WorldPoint> rawPath = Arrays.asList(
                new WorldPoint(2775, 3234, 1),
                new WorldPoint(2772, 3234, 0));

        assertFalse(Rs2Walker.isRawTransportOriginNearPlayer(
                rawPath, 0, new WorldPoint(2775, 3234, 0), 2));
    }

    @Test
    public void plannedTransportApproach_clicksUntilDispatchRange() {
        WorldPoint player = new WorldPoint(2760, 3229, 0);
        WorldPoint charterOrigin = new WorldPoint(2760, 3238, 0);

        assertTrue("A planned transport nine tiles ahead still needs a route approach click",
                Rs2Walker.shouldApproachPlannedTransportOrigin(
                        true, charterOrigin, player, 2));
        assertFalse("Once beside the transport, its handler owns the next action",
                Rs2Walker.shouldApproachPlannedTransportOrigin(
                        true, charterOrigin, new WorldPoint(2760, 3236, 0), 2));
    }

    @Test
    public void plannedTransportApproach_rejectsOrdinaryAndOtherPlaneSteps() {
        WorldPoint player = new WorldPoint(2760, 3229, 0);

        assertFalse(Rs2Walker.shouldApproachPlannedTransportOrigin(
                false, new WorldPoint(2760, 3238, 0), player, 2));
        assertFalse(Rs2Walker.shouldApproachPlannedTransportOrigin(
                true, new WorldPoint(2760, 3238, 1), player, 2));
    }

    @Test
    public void shouldRecalculatePathAfterTransport_skipsAdjacentSamePlaneTransport() {
        Transport door = new Transport(
                new WorldPoint(3123, 3360, 0),
                new WorldPoint(3123, 3361, 0),
                "Door",
                TransportType.TRANSPORT,
                false,
                "Open",
                "Door",
                136);

        assertFalse(Rs2WalkerTransports.shouldRecalculatePathAfterTransport(door));
    }

    @Test
    public void isSettledNearAdjacentSamePlaneLanding_acceptsNearDestinationOffOrigin() {
        Transport door = new Transport(
                new WorldPoint(3152, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                "Door",
                TransportType.TRANSPORT,
                false,
                "Open",
                "Door",
                136);

        assertTrue(Rs2WalkerTransports.isSettledNearAdjacentSamePlaneLanding(
                door,
                new WorldPoint(3154, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                0));
    }

    @Test
    public void isSettledNearAdjacentSamePlaneLanding_rejectsOriginTile() {
        Transport door = new Transport(
                new WorldPoint(3152, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                "Door",
                TransportType.TRANSPORT,
                false,
                "Open",
                "Door",
                136);

        assertFalse(Rs2WalkerTransports.isSettledNearAdjacentSamePlaneLanding(
                door,
                new WorldPoint(3152, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                0));
    }

    @Test
    public void isSettledNearAdjacentSamePlaneLanding_rejectsTilesTooFarFromDestination() {
        Transport door = new Transport(
                new WorldPoint(3152, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                "Door",
                TransportType.TRANSPORT,
                false,
                "Open",
                "Door",
                136);

        assertFalse(Rs2WalkerTransports.isSettledNearAdjacentSamePlaneLanding(
                door,
                new WorldPoint(3155, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                0));
    }

    @Test
    public void isSettledNearAdjacentSamePlaneLanding_acceptsBoundedForwardAgilityOvershoot() {
        Transport steppingStone = new Transport(
                new WorldPoint(3154, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                "Stepping stone",
                TransportType.AGILITY_SHORTCUT,
                false,
                "Jump-onto",
                "Stepping stone",
                16533);

        assertTrue(Rs2WalkerTransports.isSettledNearAdjacentSamePlaneLanding(
                steppingStone,
                new WorldPoint(3149, 3363, 0),
                steppingStone.getDestination(),
                0));
    }

    @Test
    public void isSettledNearAdjacentSamePlaneLanding_rejectsReverseOrUnboundedAgilityMovement() {
        Transport steppingStone = new Transport(
                new WorldPoint(3154, 3363, 0),
                new WorldPoint(3153, 3363, 0),
                "Stepping stone",
                TransportType.AGILITY_SHORTCUT,
                false,
                "Jump-onto",
                "Stepping stone",
                16533);

        assertFalse(Rs2WalkerTransports.isSettledNearAdjacentSamePlaneLanding(
                steppingStone,
                new WorldPoint(3155, 3363, 0),
                steppingStone.getDestination(),
                0));
        assertFalse(Rs2WalkerTransports.isSettledNearAdjacentSamePlaneLanding(
                steppingStone,
                new WorldPoint(3147, 3363, 0),
                steppingStone.getDestination(),
                0));
        assertFalse(Rs2WalkerTransports.isSettledNearAdjacentSamePlaneLanding(
                steppingStone,
                new WorldPoint(3149, 3365, 0),
                steppingStone.getDestination(),
                0));
    }

    @Test
    public void shouldRecalculatePathAfterTransport_includesLongDistanceTransport() {
        Transport ship = new Transport(
                new WorldPoint(3054, 3245, 0),
                new WorldPoint(2956, 3146, 0),
                "Port Sarim to Karamja",
                TransportType.SHIP,
                false,
                "Cross",
                "Gangplank",
                2082);

        assertTrue(Rs2WalkerTransports.shouldRecalculatePathAfterTransport(ship));
    }

    @Test
    public void shouldRecalculatePathAfterTransport_includesSamePlaneCoordinateBandTransport() {
        Transport varrockSewerLadder = new Transport(
                new WorldPoint(3237, 9858, 0),
                new WorldPoint(3236, 3458, 0),
                "Varrock Sewers ladder",
                TransportType.TRANSPORT,
                false,
                "Climb-up",
                "Ladder",
                11806);

        assertTrue(Rs2WalkerTransports.shouldRecalculatePathAfterTransport(varrockSewerLadder));
    }

    @Test
    public void hasPendingRouteStepBeforeArrival_detectsTransportBeforeDestination() {
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3220, 3473, 0),
                new WorldPoint(3221, 3473, 0),
                new WorldPoint(3222, 3473, 0),
                new WorldPoint(3229, 3473, 0));

        assertTrue(Rs2WalkerMovement.hasPendingRouteStepBeforeArrival(
                path,
                new WorldPoint(3229, 3473, 0),
                0,
                i -> i == 1));
    }

    @Test
    public void hasPendingRouteStepBeforeArrival_ignoresStepsInsideArrivalTolerance() {
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3220, 3473, 0),
                new WorldPoint(3227, 3473, 0),
                new WorldPoint(3228, 3473, 0),
                new WorldPoint(3229, 3473, 0));

        assertFalse(Rs2WalkerMovement.hasPendingRouteStepBeforeArrival(
                path,
                new WorldPoint(3229, 3473, 0),
                2,
                i -> i == 2));
    }

    // ---------------------------------------------------------------------------
    // #15 — Sidestep recovery ranking
    // ---------------------------------------------------------------------------

    @Test
    public void rankSidestep_nullReachable_returnsEmpty() {
        List<WorldPoint> ranked = Rs2Walker.rankSidestepTilesToward(null, new WorldPoint(3200, 3200, 0));
        assertTrue("null input must yield an empty ranking, never NPE", ranked.isEmpty());
    }

    @Test
    public void rankSidestep_emptyReachable_returnsEmpty() {
        List<WorldPoint> ranked = Rs2Walker.rankSidestepTilesToward(
                Collections.emptySet(), new WorldPoint(3200, 3200, 0));
        assertTrue(ranked.isEmpty());
    }

    @Test
    public void rankSidestep_singleTile_returnsSingleton() {
        WorldPoint only = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> ranked = Rs2Walker.rankSidestepTilesToward(
                Collections.singleton(only), new WorldPoint(3210, 3210, 0));
        assertEquals(1, ranked.size());
        assertSame(only, ranked.get(0));
    }

    /**
     * The whole point of the ranking: closer-to-target tiles come first. The old
     * implementation picked randomly from reachable tiles and could walk AWAY from
     * the target, which caused repeated stall loops in narrow corridors.
     */
    @Test
    public void rankSidestep_closestToTargetIsFirst() {
        WorldPoint target = new WorldPoint(3220, 3200, 0);
        WorldPoint near = new WorldPoint(3210, 3200, 0);   // 10 away
        WorldPoint mid = new WorldPoint(3205, 3200, 0);    // 15 away
        WorldPoint far = new WorldPoint(3200, 3200, 0);    // 20 away

        List<WorldPoint> ranked = Rs2Walker.rankSidestepTilesToward(
                Arrays.asList(far, near, mid), target);

        assertEquals(3, ranked.size());
        assertSame("closest tile must lead the ranking", near, ranked.get(0));
        assertSame(mid, ranked.get(1));
        assertSame(far, ranked.get(2));
    }

    @Test
    public void rankSidestep_preservesAllTilesIncludingEquidistant() {
        WorldPoint target = new WorldPoint(3200, 3200, 0);
        // Both 5 tiles from target in Chebyshev, on opposite sides.
        WorldPoint east = new WorldPoint(3205, 3200, 0);
        WorldPoint west = new WorldPoint(3195, 3200, 0);
        WorldPoint north = new WorldPoint(3200, 3205, 0);
        Set<WorldPoint> reachable = new HashSet<>(Arrays.asList(east, west, north));

        List<WorldPoint> ranked = Rs2Walker.rankSidestepTilesToward(reachable, target);

        assertEquals("no tile may be dropped by the ranking", 3, ranked.size());
        assertTrue(ranked.contains(east));
        assertTrue(ranked.contains(west));
        assertTrue(ranked.contains(north));
    }

    @Test
    public void getClosestTileIndex_usesReachableDistanceWhenAvailable() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint farByWorldDistance = new WorldPoint(3210, 3200, 0);
        WorldPoint nearReachable = new WorldPoint(3220, 3200, 0);
        List<WorldPoint> path = Arrays.asList(farByWorldDistance, nearReachable);
        Map<WorldPoint, Integer> reachable = new HashMap<>();
        reachable.put(farByWorldDistance, 8);
        reachable.put(nearReachable, 3);

        assertEquals(1, WalkerPathGeometry.getClosestTileIndex(path, player, reachable));
    }

    @Test
    public void getClosestTileIndex_fallsBackToWorldDistanceWhenNoReachablePathTile() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3210, 3200, 0),
                new WorldPoint(3202, 3200, 0),
                new WorldPoint(3220, 3200, 0));

        assertEquals(1, WalkerPathGeometry.getClosestTileIndex(path, player, Collections.emptyMap()));
    }

    // ---------------------------------------------------------------------------
    // #21 — Minimap forward-scan
    // ---------------------------------------------------------------------------

    /**
     * When no path tile past the start index qualifies, the function must return the
     * start index — the walker still needs something to click at.
     */
    @Test
    public void findFurthest_shortPath_returnsStart() {
        List<WorldPoint> path = Collections.singletonList(new WorldPoint(3200, 3200, 0));
        int idx = RouteRecovery.findFurthestClickableIndex(path, 0, new WorldPoint(3200, 3200, 0),
                wp -> false, 14);
        assertEquals(0, idx);
    }

    @Test
    public void findFurthest_outOfBoundsStart_returnsStartUnchanged() {
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0), new WorldPoint(3201, 3200, 0));
        assertEquals(-1, RouteRecovery.findFurthestClickableIndex(path, -1, new WorldPoint(3200, 3200, 0),
                wp -> false, 14));
        assertEquals(5, RouteRecovery.findFurthestClickableIndex(path, 5, new WorldPoint(3200, 3200, 0),
                wp -> false, 14));
    }

    /**
     * Cross-plane path steps are transports (stairs/ladders). Clicking past them would
     * make the walker walk into an unreachable spot on the other plane.
     */
    @Test
    public void findFurthest_stopsAtPlaneChange() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),  // 0: player
                new WorldPoint(3201, 3200, 0),  // 1: same plane
                new WorldPoint(3202, 3200, 0),  // 2: same plane
                new WorldPoint(3203, 3200, 1),  // 3: plane change (stairs up)
                new WorldPoint(3204, 3200, 1)); // 4: beyond plane change

        int idx = RouteRecovery.findFurthestClickableIndex(path, 1, player, wp -> false, 14);

        assertEquals("scan must stop at the tile BEFORE the plane change", 2, idx);
    }

    /**
     * Transport origins need explicit `handleTransports` interaction. Skipping past a
     * transport origin via a long minimap click would bypass that interaction.
     */
    @Test
    public void findFurthest_stopsAtTransportOrigin() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint transportOrigin = new WorldPoint(3203, 3200, 0);
        Predicate<WorldPoint> isTransportOrigin = transportOrigin::equals;
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0),
                transportOrigin,                  // 3: transport — scan must stop HERE
                new WorldPoint(3204, 3200, 0));

        int idx = RouteRecovery.findFurthestClickableIndex(path, 1, player, isTransportOrigin, 14);

        assertEquals("scan must stop at the tile before a transport origin", 2, idx);
    }

    @Test
    public void findFurthest_stopsAtEuclideanLimitCardinal() {
        // On a cardinal axis, Euclidean distance equals |dx|, so the scan reaches
        // the full reach value (14) — diagonals are bounded tighter.
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3205, 3200, 0),  // 0: 5 away
                new WorldPoint(3210, 3200, 0),  // 1: 10 away
                new WorldPoint(3214, 3200, 0),  // 2: 14 away (AT limit)
                new WorldPoint(3215, 3200, 0),  // 3: 15 away (OVER limit)
                new WorldPoint(3220, 3200, 0)); // 4: 20 away

        int idx = RouteRecovery.findFurthestClickableIndex(path, 0, player, wp -> false, 14);

        assertEquals("scan must include the tile at the limit and stop at the tile over it",
                2, idx);
    }

    @Test
    public void findFurthest_boundedByEuclideanCircleOnDiagonal() {
        // The reach parameter is a Euclidean radius because the minimap's clickable
        // area is a circle. On a diagonal each step adds sqrt(2) to Euclidean
        // distance, so with reach=14 the furthest reachable diagonal tile is at
        // Chebyshev 9 (Euclidean sqrt(162)≈12.73 ≤ 14); the tile at Chebyshev 10
        // is Euclidean sqrt(200)≈14.14 and must be rejected.
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> diagonalPath = Arrays.asList(
                new WorldPoint(3201, 3201, 0),
                new WorldPoint(3205, 3205, 0),
                new WorldPoint(3209, 3209, 0),  // Chebyshev 9, Euclidean ~12.73 — in
                new WorldPoint(3210, 3210, 0)); // Chebyshev 10, Euclidean ~14.14 — out

        int idx = RouteRecovery.findFurthestClickableIndex(diagonalPath, 0, player, wp -> false, 14);

        assertEquals("scan must stop at the last diagonal tile inside the Euclidean circle",
                2, idx);
    }

    @Test
    public void findFurthest_nullPredicate_treatsAsNoTransport() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0),
                new WorldPoint(3203, 3200, 0));

        int idx = RouteRecovery.findFurthestClickableIndex(path, 0, player, null, 14);

        assertEquals("null predicate must not NPE and must allow full scan", 2, idx);
    }

    /**
     * The anchored forward scan is shared by every route-click predicate (line-of-sight, reachable,
     * walkable, off-scene). If the smoothed-&gt;raw anchor points past the player's vicinity, the scan
     * breaks immediately on the Euclidean bound and returns null for ALL of them — observed live as
     * {@code sel=none}, which dropped route clicks onto the off-route wall-nudge clamp. Selection
     * must therefore retry anchored at the player's own closest raw tile.
     */
    @Test
    public void rawPathScan_staleAnchorPastPlayerYieldsNothingForEveryPredicate() {
        WorldPoint player = new WorldPoint(3183, 3435, 0);
        List<WorldPoint> raw = new java.util.ArrayList<>();
        for (int i = 0; i < 40; i++) {
            raw.add(new WorldPoint(3183, 3435 - i, 0)); // 40-tile route running south
        }

        assertNotNull("anchored at the player, the scan must find a forward route point",
                Rs2WalkerMovement.findFurthestRawPathPointMatching(raw, player, 10, 0, wp -> true));

        assertNull("a stale anchor near the goal must yield nothing even for an always-true predicate",
                Rs2WalkerMovement.findFurthestRawPathPointMatching(raw, player, 10, 38, wp -> true));
    }

    /**
     * Anti-ban: consecutive clicks must not all cover the same tile span, but the jittered reach has
     * hard bounds. Too short and the click lands inside the interim-close threshold, clearing the
     * checkpoint immediately and causing click thrash; too long and it falls outside the minimap
     * clip. Varying reach is the safe axis because it only changes how far ALONG the route we click,
     * never sideways — lateral tile offsets leave the route and were removed for that reason.
     */
    @Test
    public void routeClickReach_staysWithinSafeBandAndActuallyVaries() {
        int max = 10;
        java.util.Set<Integer> seen = new HashSet<>();
        for (int i = 0; i < 400; i++) {
            int reach = Rs2WalkerMovement.routeClickReach(max);
            assertTrue("reach must never exceed the caller's minimap reach, got " + reach, reach <= max);
            assertTrue("reach must stay clear of the interim-close threshold, got " + reach, reach >= 7);
            seen.add(reach);
        }
        assertTrue("reach must actually vary between clicks, saw only " + seen, seen.size() > 1);
    }

    /** A caller reach at or below the floor must be returned unchanged rather than inverted. */
    @Test
    public void routeClickReach_degenerateBoundsAreSafe() {
        for (int max : new int[]{0, 1, 5, 7}) {
            int reach = Rs2WalkerMovement.routeClickReach(max);
            assertEquals("a reach at/below the floor must pass through unchanged", max, reach);
        }
    }

    /**
     * Leaving the Motherlode Mine must still be able to clear a rockfall standing in the path.
     *
     * <p>The gate used to require the walk TARGET to be in region 14936, so a walk from inside the
     * mine to the Dwarven Mine (region 12185) bailed before inspecting anything. Observed live:
     * {@code at=(3751,5672) goal=(3040,9810)} — every outbound route was unable to mine through.
     */
    @Test
    public void rockfallGateAllowsWalksLeavingTheMotherlodeMine() {
        WorldPoint insideMlm = new WorldPoint(3751, 5672, 0);
        WorldPoint dwarvenMineGoal = new WorldPoint(3040, 9810, 0);

        assertEquals("precondition: this tile is the Motherlode Mine region",
                Rs2ObstacleHandler.MOTHERLODE_MINE_REGION, insideMlm.getRegionID());
        assertNotEquals("precondition: the outbound goal is in a different region — this is exactly "
                        + "what the old target-based gate rejected",
                Rs2ObstacleHandler.MOTHERLODE_MINE_REGION, dwarvenMineGoal.getRegionID());

        List<WorldPoint> path = Arrays.asList(insideMlm, new WorldPoint(3750, 5672, 0), dwarvenMineGoal);
        assertTrue("standing in the mine must qualify regardless of where the walk ends",
                Rs2ObstacleHandler.isMotherlodeRockfallCandidate(insideMlm, path, 0));
    }

    /** A rockfall ahead on the path qualifies even when the player has not entered the mine yet. */
    @Test
    public void rockfallGateAllowsApproachingTheMineFromOutside() {
        WorldPoint outside = new WorldPoint(3060, 9766, 0); // Dwarven Mine cave mouth
        List<WorldPoint> path = Arrays.asList(outside, new WorldPoint(3728, 5692, 0));

        assertNotEquals("precondition: the player is not in the mine yet",
                Rs2ObstacleHandler.MOTHERLODE_MINE_REGION, outside.getRegionID());
        assertTrue("a path tile inside the mine must still arm the handler",
                Rs2ObstacleHandler.isMotherlodeRockfallCandidate(outside, path, 0));
    }

    /** The gate must stay closed everywhere else — rockfalls exist only in the Motherlode Mine. */
    @Test
    public void rockfallGateStaysClosedAwayFromTheMine() {
        WorldPoint varrock = new WorldPoint(3210, 3424, 0);
        List<WorldPoint> path = Arrays.asList(varrock, new WorldPoint(3211, 3424, 0));

        assertFalse("an unrelated surface walk must not pay for the scene lookup",
                Rs2ObstacleHandler.isMotherlodeRockfallCandidate(varrock, path, 0));
        assertFalse("a null or empty path must never arm the handler",
                Rs2ObstacleHandler.isMotherlodeRockfallCandidate(varrock, null, 0));
    }

    // The partial-retry budget classification moved to WalkExit; its cases, including this
    // underground-goal regression, now live in WalkExitTest as explicit sets.

    /**
     * How long the walker will actually tolerate a motionless player before recovering.
     *
     * <p>Pinned as wall-clock seconds rather than as multipliers, because the multipliers are not the
     * thing anyone cares about — "how long does it sit there" is. It used to be up to 36s: a flat 12s
     * grace after every successful click, refreshed each pass, and then a 12s base scaled as far as
     * 2x. The grace is gone (tile changes already refresh the clock, so it only ever bound the case
     * where the player was NOT moving) and the interim multiplier is 1.25 rather than 1.75.
     *
     * <p>The base stays 12s on purpose: the longest legitimate motionless stretch measured across
     * four live farm runs is ~7.1s, waiting out a transport handoff. Cutting the base is how you get
     * a walker that interrupts its own ships.
     */
    @Test
    public void stallBudgetStaysWithinItsMeasuredEnvelope() {
        long plain = Rs2WalkerStallPolicy.computeThresholdMs(Rs2Walker.STALL_BASE_MS,
                Rs2Walker.STALL_COMBAT_MULTIPLIER, Rs2Walker.STALL_ANIMATING_MULTIPLIER,
                Rs2Walker.STALL_MOVING_MULTIPLIER, Rs2Walker.STALL_INTERIM_MINIMAP_MULTIPLIER,
                Rs2Walker.STALL_INTERACTING_MULTIPLIER, false, false, false, false, false);
        long withInterim = Rs2WalkerStallPolicy.computeThresholdMs(Rs2Walker.STALL_BASE_MS,
                Rs2Walker.STALL_COMBAT_MULTIPLIER, Rs2Walker.STALL_ANIMATING_MULTIPLIER,
                Rs2Walker.STALL_MOVING_MULTIPLIER, Rs2Walker.STALL_INTERIM_MINIMAP_MULTIPLIER,
                Rs2Walker.STALL_INTERACTING_MULTIPLIER, false, false, false, true, false);
        long worst = Rs2WalkerStallPolicy.computeThresholdMs(Rs2Walker.STALL_BASE_MS,
                Rs2Walker.STALL_COMBAT_MULTIPLIER, Rs2Walker.STALL_ANIMATING_MULTIPLIER,
                Rs2Walker.STALL_MOVING_MULTIPLIER, Rs2Walker.STALL_INTERIM_MINIMAP_MULTIPLIER,
                Rs2Walker.STALL_INTERACTING_MULTIPLIER, true, true, true, true, true);

        assertEquals("plain stall budget", 12_000L, plain);
        assertEquals("the common case: a sticky interim is live for most of a walk", 15_000L, withInterim);
        assertEquals("worst case, everything applying at once", 24_000L, worst);
        assertTrue("must stay clear of the ~7.1s transport handoff measured live", plain >= 10_000L);
    }

    /**
     * Off-path recovery must be able to step BACKWARD onto the route. When the player is pushed off
     * the path (e.g. stuck flush against a castle wall) and nothing ahead is reachable, the rejoin
     * helper picks the nearest reachable raw point even if it sits behind the anchor. Forward-only
     * selection would return null here and the walker would stall.
     */
    @Test
    public void findReachableRejoin_stepsBackwardWhenNothingAheadReachable() {
        WorldPoint player = new WorldPoint(3203, 3201, 0); // one tile off the line, beside the wall
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),  // 0
                new WorldPoint(3201, 3200, 0),  // 1
                new WorldPoint(3202, 3200, 0),  // 2
                new WorldPoint(3203, 3200, 0),  // 3 (anchor)
                new WorldPoint(3204, 3200, 0),  // 4
                new WorldPoint(3205, 3200, 0),  // 5
                new WorldPoint(3206, 3200, 0)); // 6
        // Only the tiles behind the anchor are reachable; the wall blocks everything ahead.
        Set<WorldPoint> reachable = new HashSet<>(Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0)));

        WorldPoint rejoin = Rs2Walker.findReachableRejoinRawPathPoint(path, player, 10, 3, reachable::contains);

        assertEquals("must rejoin the route by stepping back onto the nearest reachable raw tile",
                new WorldPoint(3202, 3200, 0), rejoin);
    }

    /**
     * When points ahead of the anchor are reachable, rejoin must prefer the furthest-forward one so
     * recovery never sacrifices progress or snaps back to an already-travelled branch.
     */
    @Test
    public void findReachableRejoin_prefersFurthestForwardReachable() {
        WorldPoint player = new WorldPoint(3203, 3201, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),  // 0
                new WorldPoint(3201, 3200, 0),  // 1
                new WorldPoint(3202, 3200, 0),  // 2
                new WorldPoint(3203, 3200, 0),  // 3 (anchor)
                new WorldPoint(3204, 3200, 0),  // 4
                new WorldPoint(3205, 3200, 0),  // 5
                new WorldPoint(3206, 3200, 0)); // 6
        Set<WorldPoint> reachable = new HashSet<>(path); // everything reachable

        // maxEuclidean = 3 -> (3206,3200) is Euclidean sqrt(10) > 3 and must be rejected;
        // (3205,3200) is sqrt(5) <= 3 and is the furthest-forward admissible point.
        WorldPoint rejoin = Rs2Walker.findReachableRejoinRawPathPoint(path, player, 3, 3, reachable::contains);

        assertEquals("rejoin must prefer the furthest-forward reachable raw tile inside the reach circle",
                new WorldPoint(3205, 3200, 0), rejoin);
    }

    @Test
    public void findReachableRejoin_returnsNullWhenNothingReachable() {
        WorldPoint player = new WorldPoint(3203, 3201, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3202, 3200, 0),
                new WorldPoint(3203, 3200, 0),
                new WorldPoint(3204, 3200, 0));

        assertNull("no reachable raw point => null (caller lets stall/recalc take over)",
                Rs2Walker.findReachableRejoinRawPathPoint(path, player, 10, 1, wp -> false));
    }

    @Test
    public void findReachableRejoin_nullInputsAreSafe() {
        WorldPoint player = new WorldPoint(3203, 3201, 0);
        List<WorldPoint> path = Collections.singletonList(new WorldPoint(3203, 3200, 0));
        assertNull(Rs2Walker.findReachableRejoinRawPathPoint(null, player, 10, 0, wp -> true));
        assertNull(Rs2Walker.findReachableRejoinRawPathPoint(path, null, 10, 0, wp -> true));
        assertNull(Rs2Walker.findReachableRejoinRawPathPoint(path, player, 10, 0, null));
        assertNull(Rs2Walker.findReachableRejoinRawPathPoint(Collections.emptyList(), player, 10, 0, wp -> true));
    }

    @Test
    public void stabilizeRouteProgressIndex_doesNotJumpBackToEarlierNearbyBranch() {
        WorldPoint target = new WorldPoint(3200, 3201, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0),
                new WorldPoint(3203, 3200, 0),
                new WorldPoint(3203, 3201, 0),
                new WorldPoint(3202, 3201, 0),
                new WorldPoint(3201, 3201, 0),
                target);

        assertEquals(5, Rs2Walker.stabilizeRouteProgressIndex(path, 5, target, new WorldPoint(3202, 3201, 0)));
        assertEquals("nearby earlier branch must not become the active route index",
                5,
                Rs2Walker.stabilizeRouteProgressIndex(path, 2, target, new WorldPoint(3202, 3201, 0)));
        assertEquals(6, Rs2Walker.stabilizeRouteProgressIndex(path, 6, target, new WorldPoint(3201, 3201, 0)));
    }

    @Test
    public void hintRouteProgressIndex_keepsProgressAnchoredAheadOfEarlierBranch() {
        WorldPoint target = new WorldPoint(3200, 3201, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0),
                new WorldPoint(3203, 3200, 0),
                new WorldPoint(3203, 3201, 0),
                new WorldPoint(3202, 3201, 0),
                new WorldPoint(3201, 3201, 0),
                target);

        Rs2Walker.hintRouteProgressIndex(path, 5, target);

        assertEquals("a clicked-ahead route checkpoint should not snap back to a nearby earlier branch",
                5,
                Rs2Walker.stabilizeRouteProgressIndex(path, 2, target, new WorldPoint(3202, 3201, 0)));
    }

    @Test
    public void findForwardRecoveryIndex_prefersLaterReachableBranch() {
        WorldPoint player = new WorldPoint(1000, 1000, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(998, 1000, 0),
                new WorldPoint(999, 1000, 0),
                new WorldPoint(1000, 1001, 0),
                new WorldPoint(1015, 1000, 0),
                new WorldPoint(1016, 1001, 0),
                new WorldPoint(1002, 1002, 0),
                new WorldPoint(1003, 1001, 0));
        Set<WorldPoint> reachable = new HashSet<>(Arrays.asList(
                new WorldPoint(1002, 1002, 0),
                new WorldPoint(1003, 1001, 0)));

        int idx = RouteRecovery.findForwardRecoveryIndex(path, 3, player, 13, reachable, wp -> true);

        assertEquals("recovery should scan forward before falling back to an earlier branch", 6, idx);
    }

    @Test
    public void findFurthestClickableIndex_canReturnEarlierNearbyBranch() {
        WorldPoint player = new WorldPoint(1000, 1000, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(998, 1000, 0),
                new WorldPoint(999, 1000, 0),
                new WorldPoint(1000, 1001, 0),
                new WorldPoint(1015, 1000, 0),
                new WorldPoint(1016, 1001, 0));

        int idx = RouteRecovery.findFurthestClickableIndex(path, 3, player, wp -> false, 13);

        assertEquals("generic fallback is allowed to backtrack; recovery clamps this at the call site", 2, idx);
    }

    @Test
    public void findFurthestForwardClickableIndex_doesNotBacktrackToEarlierNearbyBranch() {
        WorldPoint player = new WorldPoint(1000, 1000, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(998, 1000, 0),
                new WorldPoint(999, 1000, 0),
                new WorldPoint(1000, 1001, 0),
                new WorldPoint(1015, 1000, 0),
                new WorldPoint(1016, 1001, 0));

        int idx = RouteRecovery.findFurthestForwardClickableIndex(path, 3, player, wp -> false, 13);

        assertEquals("normal route following should interpolate toward the forward tile, not backtrack", 3, idx);
    }

    @Test
    public void findFurthestRawPathPointMatching_keepsPrimaryClickOnForwardRawRoute() {
        WorldPoint player = new WorldPoint(1000, 1000, 0);
        List<WorldPoint> rawPath = Arrays.asList(
                new WorldPoint(998, 1000, 0),
                new WorldPoint(999, 1000, 0),
                new WorldPoint(1000, 1001, 0),
                new WorldPoint(1010, 1000, 0),
                new WorldPoint(1009, 1000, 0),
                new WorldPoint(1008, 1000, 0));

        WorldPoint target = Rs2WalkerMovement.findFurthestRawPathPointMatching(
                rawPath,
                player,
                13,
                3,
                wp -> true);

        assertEquals("raw-route selection must not snap back to an earlier nearby branch",
                new WorldPoint(1008, 1000, 0), target);
    }

    @Test
    public void findFurthestRawPathPointMatching_honorsCandidatePredicate() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint blocked = new WorldPoint(3203, 3200, 0);
        WorldPoint allowed = new WorldPoint(3202, 3200, 0);
        List<WorldPoint> rawPath = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3201, 3200, 0),
                allowed,
                blocked);

        WorldPoint target = Rs2WalkerMovement.findFurthestRawPathPointMatching(
                rawPath,
                player,
                13,
                0,
                wp -> !wp.equals(blocked));

        assertEquals("primary raw-route target must be the furthest acceptable raw tile",
                allowed, target);
    }

    @Test
    public void findFurthestRawPathPointMatching_doesNotReturnCurrentTile() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> rawPath = Arrays.asList(
                player,
                new WorldPoint(3215, 3200, 0));

        WorldPoint target = Rs2WalkerMovement.findFurthestRawPathPointMatching(
                rawPath,
                player,
                10,
                0,
                wp -> true);

        assertNull("same-tile route targets are no-op clicks, not recovery candidates", target);
    }

    @Test
    public void findFurthestRawPathPointMatching_stopsBeforeRouteStepBudgetIsExceeded() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        List<WorldPoint> rawPath = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3205, 3200, 0),
                new WorldPoint(3210, 3200, 0),
                new WorldPoint(3216, 3200, 0));

        WorldPoint target = Rs2WalkerMovement.findFurthestRawPathPointMatching(
                rawPath,
                player,
                13,
                0,
                wp -> true);

        assertEquals("route step budget keeps one minimap click from spanning too far ahead",
                new WorldPoint(3210, 3200, 0), target);
    }

    @Test
    public void findFurthestForwardClickableIndex_stopsBeforeTransportOrigin() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint transportOrigin = new WorldPoint(3203, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0),
                transportOrigin,
                new WorldPoint(3204, 3200, 0));

        int idx = RouteRecovery.findFurthestForwardClickableIndex(path, 0, player, transportOrigin::equals, 13);

        assertEquals("route clicks must not skip a planned transport origin", 1, idx);
    }

    @Test
    public void offPathRecalcDeferralReason_prefersSettlingAndBusyState() {
        long now = 10_000L;

        assertEquals("door-settling", Rs2Walker.offPathRecalcDeferralReason(
                true, false, false, true, true, false, false,
                now, 0L, 0L, 0L, 0L));
        assertEquals("transport-settling", Rs2Walker.offPathRecalcDeferralReason(
                true, false, false, true, false, true, false,
                now, 0L, 0L, 0L, 0L));
        assertEquals("moving", Rs2Walker.offPathRecalcDeferralReason(
                true, false, false, true, false, false, false,
                now, 0L, 0L, 0L, 0L));
    }

    /**
     * The Gu'Tanoth rogue drift: ogre combat dragged the player a tile per second for 27s.
     * Moving stayed true with no walker click in flight, "moving" deferred the off-path recalc
     * every pass, and the walker was fully passive until the script gave up. Busy state without
     * walker-owned movement must NOT defer — the recalc fires and replans from the drift position.
     */
    @Test
    public void offPathRecalcDeferralReason_unownedBusyStateDoesNotDefer() {
        long now = 10_000L;

        assertEquals(null, Rs2Walker.offPathRecalcDeferralReason(
                true, false, false, false, false, false, false,
                now, 0L, 0L, 0L, 0L));
        assertEquals(null, Rs2Walker.offPathRecalcDeferralReason(
                false, true, false, false, false, false, false,
                now, 0L, 0L, 0L, 0L));
        assertEquals(null, Rs2Walker.offPathRecalcDeferralReason(
                false, false, true, false, false, false, false,
                now, 0L, 0L, 0L, 0L));
        // The drift refreshes lastMovedAtMs every tick — "recent-movement" must not defer
        // unowned movement either, or the tile-per-second creep re-arms it forever.
        assertEquals(null, Rs2Walker.offPathRecalcDeferralReason(
                true, false, false, false, false, false, false,
                now, 9_900L, 0L, 0L, 0L));
        // Settle windows are walker-owned by construction (bounded, set on our own interaction)
        // and keep deferring regardless of the ownership flag.
        assertEquals("door-settling", Rs2Walker.offPathRecalcDeferralReason(
                true, false, false, false, true, false, false,
                now, 0L, 0L, 0L, 0L));
    }

    @Test
    public void offPathRecalcDeferralReason_recentProgressDefersAfterMovementStops() {
        long now = 10_000L;

        assertEquals("route-progress", Rs2Walker.offPathRecalcDeferralReason(
                false, false, false, false, false, false, false,
                now, 0L, 8_000L, 0L, 0L));
        assertEquals("recent-click", Rs2Walker.offPathRecalcDeferralReason(
                false, false, false, false, false, false, false,
                now, 0L, 0L, 8_000L, 0L));
        assertEquals("interim-progress", Rs2Walker.offPathRecalcDeferralReason(
                false, false, false, false, false, false, true,
                now, 0L, 0L, 0L, 8_000L));
    }

    @Test
    public void offPathRecalcDeferralReason_allowsRecalcWhenSignalsExpired() {
        long now = 10_000L;

        assertEquals(null, Rs2Walker.offPathRecalcDeferralReason(
                false, false, false, true, false, false, false,
                now, 7_000L, 6_000L, 7_000L, 7_000L));
    }

    /**
     * A target &le;100 chebyshev used to skip the bank compare outright, so a purchasable gate
     * 30 straight-line tiles away (Shantay: ~700 by inventory-only path) never got its fare
     * withdrawn — the walker silently took the detour. The ceiling decides when "close" is a lie.
     */
    @Test
    public void shortWalkDirectPathCeiling_flagsGateDetours() {
        assertTrue("the Shantay detour (700 tiles for a 30-tile hop) must escalate to the bank compare",
                700 > Rs2WalkerMovement.shortWalkDirectPathCeiling(30));
        assertTrue("an honest town wiggle (150 tiles for an 80-tile hop) must stay direct",
                150 <= Rs2WalkerMovement.shortWalkDirectPathCeiling(80));
        assertEquals("tiny distances keep a floor so building detours don't trip it",
                60, Rs2WalkerMovement.shortWalkDirectPathCeiling(5));
        assertEquals(300, Rs2WalkerMovement.shortWalkDirectPathCeiling(100));
    }

    /**
     * A guarded door replies with a conversation instead of opening; re-clicking it cancels that menu,
     * so whatever answers dialogue never gets a menu that survives long enough to act on and the walk
     * livelocks at the door. The walker holds off while a menu is up — but the hold-off must be
     * BOUNDED, or a plain walk with no dialogue logic behind it would stall forever on any stray
     * conversation.
     */
    /**
     * Ranged obstacle dispatch: click the stairs/door from where we stand and let the SERVER walk us,
     * instead of walking to an approach tile we guessed at. The guess is what failed at the Black
     * Knights' ladder, the Falador staircase and the guarded door — never the interaction.
     * <p>
     * The contract that must not slip is ROUTE ORDER: a further obstacle may never be actioned before
     * the one in front of the player.
     */
    @Test
    public void shouldDispatchTransportAtRange_decisionTable() {
        int near = 2;
        int far = 13;

        // Legacy band is untouched — always dispatchable, whatever else is true.
        assertTrue("standing on the origin still dispatches",
                Rs2Walker.shouldDispatchTransportAtRange(0, near, far,
                        false, false, true, true, true, false));
        assertTrue(Rs2Walker.shouldDispatchTransportAtRange(2, near, far,
                false, false, true, true, true, false));

        // The new band.
        assertTrue("first obstacle, object transport, in range — click it from here",
                Rs2Walker.shouldDispatchTransportAtRange(7, near, far,
                        true, true, false, false, false, true));
        assertFalse("route order: something unresolved is closer",
                Rs2Walker.shouldDispatchTransportAtRange(7, near, far,
                        false, true, false, false, false, true));
        assertFalse("dialogue/widget transports gain nothing and must not fire early",
                Rs2Walker.shouldDispatchTransportAtRange(7, near, far,
                        true, false, false, false, false, true));
        assertFalse("beyond the scan's reach",
                Rs2Walker.shouldDispatchTransportAtRange(14, near, far,
                        true, true, false, false, false, true));
        assertFalse("instances keep the legacy band — raw coords make 'on route' unreliable",
                Rs2Walker.shouldDispatchTransportAtRange(7, near, far,
                        true, true, true, false, false, true));
        assertFalse("never interrupt a settle window",
                Rs2Walker.shouldDispatchTransportAtRange(7, near, far,
                        true, true, false, true, false, true));
        assertFalse("the server declined this edge before — walk onto the origin instead",
                Rs2Walker.shouldDispatchTransportAtRange(7, near, far,
                        true, true, false, false, true, true));
        assertFalse("kill switch off restores the old behaviour exactly",
                Rs2Walker.shouldDispatchTransportAtRange(7, near, far,
                        true, true, false, false, false, false));
        assertFalse("a negative distance (different plane) never dispatches",
                Rs2Walker.shouldDispatchTransportAtRange(-1, near, far,
                        true, true, false, false, false, true));
    }

    @Test
    public void doorDialogueDeferActive_holdsOffButAlwaysReleases() {
        long max = 5_000L;
        assertTrue("hold off while the menu is fresh",
                Rs2WalkerDoors.doorDialogueDeferActive(10_000L, 10_500L, max));
        assertTrue("still holding just inside the bound",
                Rs2WalkerDoors.doorDialogueDeferActive(10_000L, 14_999L, max));
        assertFalse("nothing answered it — resume clicking rather than stall the walk",
                Rs2WalkerDoors.doorDialogueDeferActive(10_000L, 15_000L, max));
        assertFalse("no hold-off recorded means no deferral",
                Rs2WalkerDoors.doorDialogueDeferActive(0L, 99_999L, max));
    }

    @Test
    public void offPathRecalcDeferredWaitMs_isBounded() {
        assertEquals(1200, Rs2Walker.offPathRecalcDeferredWaitMs(
                "route-progress", 10_000L, 0L, 9_700L, 0L, 0L));
        assertEquals(250, Rs2Walker.offPathRecalcDeferredWaitMs(
                "route-progress", 10_000L, 0L, 6_600L, 0L, 0L));
    }

    @Test
    public void interpolateClickableTarget_usesInterpolatedPointWhenUsable() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint fallback = new WorldPoint(3213, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                fallback,
                new WorldPoint(3220, 3200, 0));

        WorldPoint target = RouteRecovery.interpolateClickableTarget(path, 2, player, fallback, 12, wp -> true);

        assertEquals(new WorldPoint(3212, 3200, 0), target);
    }

    @Test
    public void interpolateClickableTarget_fallsBackWhenInterpolatedPointUnusable() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint fallback = new WorldPoint(3213, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                fallback,
                new WorldPoint(3220, 3200, 0));

        WorldPoint target = RouteRecovery.interpolateClickableTarget(path, 2, player, fallback, 12, wp -> false);

        assertEquals("unusable interpolated tiles must not replace the known path waypoint",
                fallback, target);
    }

    @Test
    public void interpolateClickableTarget_shortensOutOfReachForwardWaypoint() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint forward = new WorldPoint(3220, 3200, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                forward);

        WorldPoint target = RouteRecovery.interpolateClickableTarget(path, 1, player, forward, 12, wp -> true);

        assertEquals("out-of-minimap forward waypoints should be shortened to a clickable tile",
                new WorldPoint(3212, 3200, 0), target);
    }

    @Test
    public void clampToEuclideanRadius_shortensDiagonalTargetInsideCircle() {
        WorldPoint player = new WorldPoint(2875, 3418, 0);
        WorldPoint target = new WorldPoint(2886, 3428, 0);

        WorldPoint clamped = RouteRecovery.clampToEuclideanRadius(player, target, 10);

        assertTrue(clamped.distanceTo2D(player) <= 10);
        assertTrue(clamped.getX() > player.getX());
        assertTrue(clamped.getY() > player.getY());
    }

    @Test
    public void clampToEuclideanRadius_keepsInRangeTarget() {
        WorldPoint player = new WorldPoint(3200, 3200, 0);
        WorldPoint target = new WorldPoint(3206, 3203, 0);

        assertEquals(target, RouteRecovery.clampToEuclideanRadius(player, target, 10));
    }

    @Test
    public void directMinimapTarget_usesEuclideanRatherThanChebyshevRange() {
        WorldPoint player = new WorldPoint(3289, 3476, 0);

        assertFalse("A diagonal endpoint can be Chebyshev-close but outside the circular minimap reach",
                Rs2WalkerMovement.shouldAttemptDirectMinimapTarget(
                        new WorldPoint(3300, 3487, 0), player, 12));
        assertTrue("A cardinal endpoint on the Euclidean boundary remains eligible",
                Rs2WalkerMovement.shouldAttemptDirectMinimapTarget(
                        new WorldPoint(3301, 3476, 0), player, 12));
        assertFalse("A different plane is never a direct minimap target",
                Rs2WalkerMovement.shouldAttemptDirectMinimapTarget(
                        new WorldPoint(3289, 3476, 1), player, 12));
    }

    @Test
    public void localRecoveryCandidate_rejectsSpatiallyNearFutureRouteFold() {
        List<WorldPoint> raw = new java.util.ArrayList<>();
        int x = 2855;
        int y = 3440;
        raw.add(new WorldPoint(x, y, 0));
        for (int i = 0; i < 15; i++) {
            raw.add(new WorldPoint(--x, y, 0));
        }
        for (int i = 0; i < 15; i++) {
            raw.add(new WorldPoint(x, ++y, 0));
        }
        for (int i = 0; i < 15; i++) {
            raw.add(new WorldPoint(++x, y, 0));
        }
        for (int i = 0; i < 15; i++) {
            raw.add(new WorldPoint(x, --y, 0));
        }
        // The future branch returns spatially close to the player, but remains 60 raw route steps
        // ahead. Its proximity must not override the immediate route frontier.
        int[] smoothedToRaw = {0, 6, 60};

        assertTrue(RouteRecovery.isLocalRecoveryCandidateOnForwardRoute(
                raw, smoothedToRaw, 0, 1, 48));
        assertFalse(RouteRecovery.isLocalRecoveryCandidateOnForwardRoute(
                raw, smoothedToRaw, 0, 2, 48));
    }

    @Test
    public void localRecoveryCandidate_rejectsTransportJumpAndBackwardCandidate() {
        List<WorldPoint> raw = Arrays.asList(
                new WorldPoint(2808, 3436, 0),
                new WorldPoint(2809, 3436, 0),
                new WorldPoint(2900, 3400, 0));
        int[] smoothedToRaw = {0, 1, 2};

        assertFalse(RouteRecovery.isLocalRecoveryCandidateOnForwardRoute(
                raw, smoothedToRaw, 0, 2, 48));
        assertFalse(RouteRecovery.isLocalRecoveryCandidateOnForwardRoute(
                raw, smoothedToRaw, 1, 0, 48));
    }

    // ---------------------------------------------------------------------------
    // Raw-path wall-door segment probing
    // ---------------------------------------------------------------------------

    @Test
    public void wallDoorTouchesSegment_crossingDoorEdge_returnsTrue() {
        WallObject door = mock(WallObject.class);
        when(door.getWorldLocation()).thenReturn(new WorldPoint(3123, 3361, 0));
        when(door.getOrientationA()).thenReturn(8); // south-facing door edge

        assertTrue(Rs2DoorGeometry.wallDoorTouchesSegment(door,
                new WorldPoint(3123, 3361, 0),
                new WorldPoint(3123, 3360, 0)));
        assertTrue(Rs2DoorGeometry.wallDoorTouchesSegment(door,
                new WorldPoint(3123, 3360, 0),
                new WorldPoint(3123, 3361, 0)));
    }

    @Test
    public void wallDoorTouchesSegment_diagonalStepThroughGateCorner_returnsTrue() {
        WallObject gate = mock(WallObject.class);
        when(gate.getWorldLocation()).thenReturn(new WorldPoint(3240, 3302, 0));
        when(gate.getOrientationA()).thenReturn(4); // gate blocks 3240,3302 <-> 3241,3302

        assertTrue(Rs2DoorGeometry.wallDoorTouchesSegment(gate,
                new WorldPoint(3240, 3301, 0),
                new WorldPoint(3241, 3302, 0)));
        assertTrue(Rs2DoorGeometry.wallDoorTouchesSegment(gate,
                new WorldPoint(3241, 3302, 0),
                new WorldPoint(3240, 3301, 0)));
    }

    @Test
    public void wallDoorTouchesSegment_startingBesideDoorAndMovingAway_returnsFalse() {
        WallObject door = mock(WallObject.class);
        when(door.getWorldLocation()).thenReturn(new WorldPoint(3123, 3361, 0));
        when(door.getOrientationA()).thenReturn(8); // door blocks 3123,3361 <-> 3123,3360

        assertFalse("standing on the door's south neighbor and walking southwest must not re-open the door",
                Rs2DoorGeometry.wallDoorTouchesSegment(door,
                        new WorldPoint(3123, 3360, 0),
                        new WorldPoint(3122, 3359, 0)));
    }

    @Test
    public void isDoorEdgeNudgeResolved_movesToWrongNeighbor_returnsFalse() {
        assertFalse(Rs2WalkerDoors.isDoorEdgeNudgeResolved(
                new WorldPoint(3240, 3301, 0),
                new WorldPoint(3239, 3302, 0),
                new WorldPoint(3240, 3301, 0),
                new WorldPoint(3241, 3302, 0)));
    }

    @Test
    public void isDoorEdgeNudgeResolved_crossesToDoorTarget_returnsTrue() {
        assertTrue(Rs2WalkerDoors.isDoorEdgeNudgeResolved(
                new WorldPoint(3240, 3301, 0),
                new WorldPoint(3241, 3302, 0),
                new WorldPoint(3240, 3301, 0),
                new WorldPoint(3241, 3302, 0)));
    }

    /**
     * The nudge now clicks a route point PAST the door, so a successful crossing keeps going. The
     * live log's exact case: south door 3369->3368, player observed at 3365 — through the door and
     * three tiles beyond — reported unresolved by the near-toWp rule.
     */
    @Test
    public void isDoorEdgeNudgeResolved_ranOnPastTheDoor_returnsTrue() {
        assertTrue(Rs2WalkerDoors.isDoorEdgeNudgeResolved(
                new WorldPoint(3106, 3369, 0),
                new WorldPoint(3106, 3365, 0),
                new WorldPoint(3106, 3369, 0),
                new WorldPoint(3106, 3368, 0)));
    }

    /**
     * A running player covers two tiles a tick and may NEVER be observed on toWp itself: a nudge
     * starting on fromWp has beforeTo=1, so "afterTo < beforeTo" could only fire on exactly toWp.
     * Observed live as 3369 -> 3367 -> 3365 with every poll reading unresolved.
     */
    @Test
    public void isDoorEdgeNudgeResolved_runningSkipsTheFarSideTile_returnsTrue() {
        assertTrue(Rs2WalkerDoors.isDoorEdgeNudgeResolved(
                new WorldPoint(3106, 3369, 0),
                new WorldPoint(3106, 3367, 0),
                new WorldPoint(3106, 3369, 0),
                new WorldPoint(3106, 3368, 0)));
    }

    /** Walking parallel along the NEAR side of the wall is not a crossing, however far it gets. */
    @Test
    public void isDoorEdgeNudgeResolved_parallelOnTheNearSide_returnsFalse() {
        assertFalse(Rs2WalkerDoors.isDoorEdgeNudgeResolved(
                new WorldPoint(3106, 3369, 0),
                new WorldPoint(3103, 3369, 0),
                new WorldPoint(3106, 3369, 0),
                new WorldPoint(3106, 3368, 0)));
    }

    @Test
    public void isDoorEdgeNudgeResolved_eastDoorCrossedAtSpeed_returnsTrue() {
        assertTrue(Rs2WalkerDoors.isDoorEdgeNudgeResolved(
                new WorldPoint(3240, 3301, 0),
                new WorldPoint(3243, 3301, 0),
                new WorldPoint(3240, 3301, 0),
                new WorldPoint(3241, 3301, 0)));
    }

    @Test
    public void shouldClearInterimTarget_closeToCheckpoint_returnsTrue() {
        assertTrue(Rs2WalkerMovement.shouldClearInterimTarget(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2889, 3396, 0),
                1_000L,
                1_500L,
                2_000L));
    }

    @Test
    public void shouldClearInterimTarget_preclickDistanceStillKeepsCheckpoint() {
        assertFalse(Rs2WalkerMovement.shouldClearInterimTarget(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2884, 3396, 0),
                1_000L,
                1_500L,
                2_000L));
    }

    @Test
    public void distanceToInterimOrMax_samePlaneReturnsDistance() {
        assertEquals(8, Rs2WalkerMovement.distanceToInterimOrMax(
                new WorldPoint(2850, 3506, 0),
                new WorldPoint(2849, 3498, 0)));
    }

    @Test
    public void shouldClearInterimTarget_expiredCheckpoint_returnsTrue() {
        assertTrue(Rs2WalkerMovement.shouldClearInterimTarget(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                1_500L,
                12_000L));
    }

    @Test
    public void shouldClearInterimTarget_staleProgress_returnsTrue() {
        assertTrue(Rs2WalkerMovement.shouldClearInterimTarget(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                1_500L,
                5_000L));
    }

    @Test
    public void shouldClearInterimTarget_activeFarCheckpoint_returnsFalse() {
        assertFalse(Rs2WalkerMovement.shouldClearInterimTarget(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                4_500L,
                5_000L));
    }

    /**
     * The player has walked well past its closest approach, so the checkpoint is abandoned even though
     * progress was recorded a moment ago — route-index advance renews that timestamp every pass, which
     * is exactly how a dead interim survived to its 10s cap while a transport dispatch waited on it.
     */
    @Test
    public void shouldClearInterimTarget_movingAwayFromCheckpoint_returnsTrue() {
        assertTrue(Rs2WalkerMovement.shouldClearInterimTarget(
                new WorldPoint(2973, 3350, 0),
                new WorldPoint(2960, 3343, 0),
                1_000L,
                4_900L,
                5_000L,
                6));
    }

    /** Rounding a wall costs a few tiles and must not abandon a checkpoint still being approached. */
    @Test
    public void shouldClearInterimTarget_detourWithinMargin_returnsFalse() {
        assertFalse(Rs2WalkerMovement.shouldClearInterimTarget(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                4_900L,
                5_000L,
                8));
    }

    /** Unknown best distance leaves the abandon check inert — behaviour matches the 5-arg form. */
    @Test
    public void shouldClearInterimTarget_unknownBestDistance_returnsFalse() {
        assertFalse(Rs2WalkerMovement.shouldClearInterimTarget(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                4_900L,
                5_000L,
                Integer.MAX_VALUE));
    }

    @Test
    public void shouldYieldForActiveRecoveryInterim_recentProgress_returnsTrue() {
        assertTrue(Rs2WalkerMovement.shouldYieldForActiveRecoveryInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2884, 3396, 0),
                1_000L,
                2_500L,
                3_000L,
                Integer.MAX_VALUE,
                0L,
                0L,
                false));
    }

    @Test
    public void shouldYieldForActiveRecoveryInterim_staleProgress_returnsFalse() {
        assertFalse(Rs2WalkerMovement.shouldYieldForActiveRecoveryInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                1_500L,
                5_000L,
                Integer.MAX_VALUE,
                0L,
                0L,
                false));
    }

    @Test
    public void shouldYieldForActiveRecoveryInterim_recentRecoveryClick_returnsTrue() {
        assertTrue(Rs2WalkerMovement.shouldYieldForActiveRecoveryInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                0L,
                3_000L,
                Integer.MAX_VALUE,
                0L,
                2_000L,
                false));
    }

    @Test
    public void shouldYieldForActiveRecoveryInterim_movingAway_returnsFalse() {
        assertFalse(Rs2WalkerMovement.shouldYieldForActiveRecoveryInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                4_900L,
                5_000L,
                5,
                0L,
                0L,
                true));
    }

    @Test
    public void shouldDeferRouteWorkForActiveInterim_movingFarCheckpoint_returnsTrue() {
        assertTrue(Rs2Walker.shouldDeferRouteWorkForActiveInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                4_500L,
                5_000L,
                Integer.MAX_VALUE,
                0L,
                true,
                5));
    }

    @Test
    public void shouldDeferRouteWorkForActiveInterim_recentProgressStoppedFar_returnsTrue() {
        assertTrue(Rs2Walker.shouldDeferRouteWorkForActiveInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                4_900L,
                5_000L,
                Integer.MAX_VALUE,
                0L,
                false,
                5));
    }

    @Test
    public void shouldDeferRouteWorkForActiveInterim_closeCheckpoint_returnsFalse() {
        assertFalse(Rs2Walker.shouldDeferRouteWorkForActiveInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2886, 3396, 0),
                1_000L,
                4_500L,
                5_000L,
                Integer.MAX_VALUE,
                0L,
                true,
                5));
    }

    @Test
    public void shouldDeferRouteWorkForActiveInterim_staleStoppedCheckpoint_returnsFalse() {
        assertFalse(Rs2Walker.shouldDeferRouteWorkForActiveInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                1_500L,
                5_000L,
                Integer.MAX_VALUE,
                0L,
                false,
                5));
    }

    @Test
    public void shouldDeferRouteWorkForActiveInterim_movingAway_returnsFalse() {
        assertFalse(Rs2Walker.shouldDeferRouteWorkForActiveInterim(
                new WorldPoint(2890, 3396, 0),
                new WorldPoint(2880, 3396, 0),
                1_000L,
                4_900L,
                5_000L,
                5,
                0L,
                true,
                5));
    }

    @Test
    public void interimPreclickTiles_runHandsOffEarlierThanWalk() {
        assertEquals(6, Rs2WalkerMovement.interimPreclickTiles(false));
        assertEquals(8, Rs2WalkerMovement.interimPreclickTiles(true));
    }

    @Test
    public void routeMovementClickPhase_labelsContinuationSeparatelyFromRecovery() {
        assertEquals("stall_recovery_click", Rs2WalkerMovement.routeMovementClickPhase("stall recovery click"));
        assertEquals("active_route_idle_nudge", Rs2WalkerMovement.routeMovementClickPhase("active route idle nudge"));
        assertEquals("interim_close_route_click", Rs2WalkerMovement.routeMovementClickPhase("interim close route click"));
        assertEquals("route_movement_click", Rs2WalkerMovement.routeMovementClickPhase("other"));
    }

    @Test
    public void routeArrivalSatisfied_preventsRecoveryClickAtGoal() {
        WorldPoint goal = new WorldPoint(3304, 3336, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3300, 3333, 0),
                goal);

        assertTrue(Rs2Walker.routeArrivalSatisfied(goal, goal, path, 10));
    }

    @Test
    public void routeArrivalSatisfied_usesTightFinalApproachThreshold() {
        WorldPoint goal = new WorldPoint(3304, 3336, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3300, 3333, 0),
                goal);

        assertTrue(Rs2Walker.routeArrivalSatisfied(
                new WorldPoint(3303, 3336, 0), goal, path, 10));
        assertFalse(Rs2Walker.routeArrivalSatisfied(
                new WorldPoint(3302, 3336, 0), goal, path, 10));
        assertFalse(Rs2Walker.routeArrivalSatisfied(
                new WorldPoint(3304, 3336, 1), goal, path, 10));
    }

    @Test
    public void walkStepPathReachesTarget_acceptsEndpointWithinArrivalDistance() {
        WorldPoint goal = new WorldPoint(3304, 3336, 0);
        List<WorldPoint> path = Arrays.asList(
                new WorldPoint(3300, 3333, 0),
                new WorldPoint(3303, 3336, 0));

        assertTrue(Rs2Walker.walkStepPathReachesTarget(path, goal, 1));
    }

    @Test
    public void walkStepPathReachesTarget_rejectsMultiTilePartialPath() {
        WorldPoint goal = new WorldPoint(3304, 3336, 0);
        List<WorldPoint> partialPath = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3210, 3210, 0),
                new WorldPoint(3220, 3220, 0));

        assertFalse("a completed partial path must not leave walkStep reporting MOVING forever",
                Rs2Walker.walkStepPathReachesTarget(partialPath, goal, 1));
    }

    @Test
    public void walkStepPathReachesTarget_rejectsMissingPathOrEndpoint() {
        WorldPoint goal = new WorldPoint(3304, 3336, 0);

        assertFalse(Rs2Walker.walkStepPathReachesTarget(null, goal, 1));
        assertFalse(Rs2Walker.walkStepPathReachesTarget(Collections.emptyList(), goal, 1));
        assertFalse(Rs2Walker.walkStepPathReachesTarget(Collections.singletonList(null), goal, 1));
    }

    @Test
    public void shouldRunActiveRouteIdleNudge_waitsForImmediateTransport() {
        assertFalse(Rs2WalkerMovement.shouldRunActiveRouteIdleNudge(true, true));
        assertTrue(Rs2WalkerMovement.shouldRunActiveRouteIdleNudge(true, false));
        assertFalse(Rs2WalkerMovement.shouldRunActiveRouteIdleNudge(false, false));
    }

    // Startup-preclick skipping moved to SegmentGate; its cases live in SegmentGateTest.

    @Test
    public void rawPathForwardAnchorIndex_keepsFallbackAheadOfAnchor() {
        List<WorldPoint> rawPath = Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0),
                new WorldPoint(3203, 3200, 0),
                new WorldPoint(3204, 3200, 0),
                new WorldPoint(3205, 3200, 0),
                new WorldPoint(3206, 3200, 0),
                new WorldPoint(3207, 3200, 0),
                new WorldPoint(3208, 3200, 0),
                new WorldPoint(3209, 3200, 0),
                new WorldPoint(3210, 3200, 0),
                new WorldPoint(3210, 3201, 0),
                new WorldPoint(3210, 3202, 0),
                new WorldPoint(3210, 3203, 0),
                new WorldPoint(3210, 3204, 0),
                new WorldPoint(3209, 3204, 0),
                new WorldPoint(3208, 3204, 0),
                new WorldPoint(3207, 3204, 0),
                new WorldPoint(3206, 3204, 0),
                new WorldPoint(3205, 3204, 0),
                new WorldPoint(3204, 3204, 0));
        WorldPoint playerOnReturnBranch = rawPath.get(20);

        assertEquals("forward anchor must keep raw fallback on the current return branch",
                20,
                Rs2Walker.rawPathForwardAnchorIndex(rawPath, playerOnReturnBranch, 14));
    }

    @Test
    public void didTraverseInteractedDoor_crossesDoorTowardSegmentDestination_returnsTrue() {
        assertTrue(Rs2WalkerDoors.didTraverseInteractedDoor(
                new WorldPoint(2465, 3494, 0),
                new WorldPoint(2465, 3493, 0),
                new WorldPoint(2465, 3493, 0),
                new WorldPoint(2465, 3494, 0),
                new WorldPoint(2465, 3493, 0)));
    }

    @Test
    public void didTraverseInteractedDoor_movesWithoutCrossingObject_returnsFalse() {
        assertFalse(Rs2WalkerDoors.didTraverseInteractedDoor(
                new WorldPoint(2465, 3494, 0),
                new WorldPoint(2465, 3495, 0),
                new WorldPoint(2465, 3493, 0),
                new WorldPoint(2465, 3494, 0),
                new WorldPoint(2465, 3493, 0)));
    }

    @Test
    public void didTraverseInteractedDoor_crossesObjectButMovesAwayFromDestination_returnsFalse() {
        assertFalse(Rs2WalkerDoors.didTraverseInteractedDoor(
                new WorldPoint(1987, 5568, 0),
                new WorldPoint(1986, 5568, 0),
                new WorldPoint(1987, 5568, 0),
                new WorldPoint(1987, 5568, 0),
                new WorldPoint(1988, 5568, 0)));
    }

    @Test
    public void shouldBlacklistDoorAfterWrongTraversal_teleportAway_returnsTrue() {
        assertTrue(Rs2WalkerDoors.shouldBlacklistDoorAfterWrongTraversal(
                new WorldPoint(1987, 5568, 0),
                new WorldPoint(2435, 3519, 0),
                new WorldPoint(1987, 5568, 0),
                new WorldPoint(1988, 5569, 0)));
    }

    @Test
    public void shouldBlacklistDoorAfterWrongTraversal_startedFarFromDoor_returnsFalse() {
        assertFalse("movement from an earlier minimap click must not blacklist a valid gate",
                Rs2WalkerDoors.shouldBlacklistDoorAfterWrongTraversal(
                        new WorldPoint(3270, 3320, 0),
                        new WorldPoint(3275, 3325, 0),
                        new WorldPoint(3262, 3322, 0),
                        new WorldPoint(3261, 3321, 0)));
    }

    @Test
    public void shouldBlacklistDoorAfterWrongTraversal_progressTowardEdge_returnsFalse() {
        assertFalse(Rs2WalkerDoors.shouldBlacklistDoorAfterWrongTraversal(
                new WorldPoint(2465, 3494, 0),
                new WorldPoint(2465, 3493, 0),
                new WorldPoint(2465, 3494, 0),
                new WorldPoint(2465, 3493, 0)));
    }

    @Test
    public void transportSettle_endsOnceArrivedAndIdle() {
        // The fix for the fixed ~900ms post-transport freeze: standing at the planned destination,
        // neither moving nor animating, past the one-tick floor => the settle is OVER.
        WorldPoint dest = new WorldPoint(3205, 3209, 0);
        assertFalse("arrived and idle must end the settle",
                Rs2Walker.transportSettlePending(400L, dest, dest, false, false));
        // ... including standing one tile off the exact destination tile.
        assertFalse(Rs2Walker.transportSettlePending(400L, new WorldPoint(3206, 3209, 0), dest, false, false));
    }

    @Test
    public void transportSettle_holdsWhileTravelingOrWithinFloor() {
        WorldPoint dest = new WorldPoint(3205, 3209, 0);
        // within the one-tick floor: always settling, even if already at the destination
        assertTrue(Rs2Walker.transportSettlePending(100L, dest, dest, false, false));
        // mid-travel (moving) at the destination tile: still settling
        assertTrue(Rs2Walker.transportSettlePending(400L, dest, dest, true, false));
        // still animating (stairs/ship): still settling
        assertTrue(Rs2Walker.transportSettlePending(400L, dest, dest, false, true));
        // far from the destination: still settling until the ceiling
        assertTrue(Rs2Walker.transportSettlePending(400L, new WorldPoint(3300, 3300, 0), dest, false, false));
        // ceiling passed: settle over regardless
        assertFalse(Rs2Walker.transportSettlePending(901L, new WorldPoint(3300, 3300, 0), dest, false, false));
    }

    @Test
    public void transportSettle_unknownDestinationFallsBackToHalfWindow() {
        assertTrue(Rs2Walker.transportSettlePending(400L, new WorldPoint(3205, 3209, 0), null, false, false));
        assertFalse(Rs2Walker.transportSettlePending(500L, new WorldPoint(3205, 3209, 0), null, false, false));
    }

    @Test
    public void shouldBlacklistDoorAfterWrongTraversal_sampledWhileMoving_returnsFalse() {
        // The Wydin's-shop poisoning: the interact walked the player toward the door, the progress wait
        // sampled MID-WALK (after=3012,3211 with the player still moving), and the walker blacklisted —
        // and learn-persisted — the shop's front door as permanently blocked. A same-plane sample taken
        // while the player is walking is a point along the path, never a traversal verdict.
        assertFalse("mid-walk sample must not blacklist the door",
                Rs2WalkerDoors.shouldBlacklistDoorAfterWrongTraversal(
                        new WorldPoint(3008, 3207, 0),   // before: en route toward the door
                        new WorldPoint(3012, 3211, 0),   // after: still walking
                        new WorldPoint(3012, 3204, 0),
                        new WorldPoint(3011, 3204, 0),
                        true));
    }

    @Test
    public void shouldBlacklistDoorAfterWrongTraversal_settledWrongWayDisplacement_stillBlacklists() {
        // Same shape of movement, but the player has STOPPED: a door that displaced the player the wrong
        // way and left them settled there is a genuine wrong traversal — the original blacklist case.
        assertTrue(Rs2WalkerDoors.shouldBlacklistDoorAfterWrongTraversal(
                new WorldPoint(3011, 3205, 0),           // started beside the edge
                new WorldPoint(3016, 3206, 0),           // settled 5 tiles away on the wrong side
                new WorldPoint(3012, 3204, 0),
                new WorldPoint(3011, 3204, 0),
                false));
    }

    @Test
    public void shouldBlacklistDoorAfterWrongTraversal_planeChangeTrustedEvenWhileMoving() {
        // A plane change cannot come from walking — the door acted. Trusted regardless of motion state.
        assertTrue(Rs2WalkerDoors.shouldBlacklistDoorAfterWrongTraversal(
                new WorldPoint(3011, 3205, 0),
                new WorldPoint(3011, 3205, 1),
                new WorldPoint(3012, 3204, 0),
                new WorldPoint(3011, 3204, 0),
                true));
    }

    // ---------------------------------------------------------------------------
    // #19 — Quest-lock dialogue heuristic
    // ---------------------------------------------------------------------------

    @Test
    public void questLock_nullAndEmpty_returnFalse() {
        assertFalse(Rs2WalkerDoors.hasQuestLockKeywords(null));
        assertFalse(Rs2WalkerDoors.hasQuestLockKeywords(""));
    }

    @Test
    public void questLock_benignDialogueReturnsFalse() {
        assertFalse(Rs2WalkerDoors.hasQuestLockKeywords("Hello there, adventurer!"));
        assertFalse(Rs2WalkerDoors.hasQuestLockKeywords("Would you like to trade?"));
        assertFalse(Rs2WalkerDoors.hasQuestLockKeywords("Click to continue"));
    }

    @Test
    public void questLock_commonGatingPhrasesReturnTrue() {
        assertTrue(Rs2WalkerDoors.hasQuestLockKeywords("You need to have completed Cook's Assistant."));
        assertTrue(Rs2WalkerDoors.hasQuestLockKeywords("You must first finish the quest."));
        assertTrue(Rs2WalkerDoors.hasQuestLockKeywords("You have not yet proven yourself."));
        assertTrue(Rs2WalkerDoors.hasQuestLockKeywords("You cannot enter until you're a member."));
        assertTrue(Rs2WalkerDoors.hasQuestLockKeywords("You can't enter without the key."));
        assertTrue(Rs2WalkerDoors.hasQuestLockKeywords("This area requires you to have level 50 Agility."));
    }

    @Test
    public void questLock_isCaseInsensitive() {
        assertTrue(Rs2WalkerDoors.hasQuestLockKeywords("YOU MUST COMPLETE THE QUEST"));
        assertTrue(Rs2WalkerDoors.hasQuestLockKeywords("you Need To finish first"));
    }

    @Test
    public void questLock_detectsBareQuestMention() {
        // The standalone "quest" keyword is a last-resort safety net — gate dialogues
        // almost always include it even when phrasing is unusual.
        assertTrue(Rs2WalkerDoors.hasQuestLockKeywords("Only those who have finished the holy quest may pass."));
    }

    // ---------------------------------------------------------------------------
    // Goal-tile object guard (D3 requirement #1 — the Gift of Peace lesson)
    // ---------------------------------------------------------------------------
    //
    // An object standing ON the walk target is the destination, not an obstacle en route. Seeded
    // from the Stronghold corridor (2026-08-13): the goal chest was Open-clicked and its failed
    // traversal waited out on three consecutive runs, ~9s each, before arrived-within-distance.

    @Test
    public void goalTileChestIsNotAnObstacleWhenTheWalkMayFinishBesideIt() {
        WorldPoint goal = new WorldPoint(1907, 5223, 0);
        WorldPoint beside = new WorldPoint(1906, 5224, 0);
        assertTrue(Rs2WalkerDoors.goalTileObjectIsNotAnObstacle(false, goal, 4, goal, beside, goal));
    }

    @Test
    public void aWallDoorOnTheGoalEdgeIsStillAnObstacle() {
        // A door on the goal tile's EDGE may genuinely need opening to step onto the goal.
        WorldPoint goal = new WorldPoint(1907, 5223, 0);
        WorldPoint beside = new WorldPoint(1906, 5223, 0);
        assertFalse(Rs2WalkerDoors.goalTileObjectIsNotAnObstacle(true, goal, 4, goal, beside, goal));
    }

    @Test
    public void aDistanceZeroWalkStillAttemptsTheGoalTileObject() {
        // The walk MUST end on the tile itself; if an openable object seals it, opening is honest.
        WorldPoint goal = new WorldPoint(1907, 5223, 0);
        WorldPoint beside = new WorldPoint(1906, 5224, 0);
        assertFalse(Rs2WalkerDoors.goalTileObjectIsNotAnObstacle(false, goal, 0, goal, beside, goal));
    }

    @Test
    public void anObjectShortOfTheGoalIsStillAnObstacle() {
        // Only the goal tile's own object is exempt; a chest two tiles early still blocks the route
        // even when its near side is adjacent to it.
        WorldPoint goal = new WorldPoint(1907, 5223, 0);
        WorldPoint doorTile = new WorldPoint(1905, 5225, 0);
        WorldPoint besideDoor = new WorldPoint(1904, 5226, 0);
        assertFalse(Rs2WalkerDoors.goalTileObjectIsNotAnObstacle(false, goal, 4, doorTile, besideDoor, doorTile));
    }

    @Test
    public void aFarNearSideDoesNotQualifyForTheGoalSkip() {
        // The skip is only honest when the walk can FINISH from the near side; a ranged detection
        // several tiles out must still be handled as an obstacle if crossing is required later.
        WorldPoint goal = new WorldPoint(1907, 5223, 0);
        WorldPoint farAway = new WorldPoint(1900, 5230, 0);
        assertFalse(Rs2WalkerDoors.goalTileObjectIsNotAnObstacle(false, goal, 4, goal, farAway, goal));
    }

    // ---------------------------------------------------------------------------
    // Walled-net door adjacency (D3 requirement #2 — the double-gate wing lesson)
    // ---------------------------------------------------------------------------

    @Test
    public void aGateWingParallelBesideTheEdgeCountsAsAdjacent() {
        // Stronghold 2026-08-13 14:00: primary wing at (1875,5239); the slave wing's edge
        // (1875,5240)->(1876,5240) was learned as walled while the primary was being opened.
        assertTrue(Rs2WalkerDoors.doorTileAdjacentToEdgeEndpoints(
                new WorldPoint(1875, 5239, 0), new WorldPoint(1875, 5240, 0), new WorldPoint(1876, 5240, 0)));
    }

    @Test
    public void aGateSharingTheDiagonalEdgesCornerCountsAsAdjacent() {
        // Same run: primary wing at (1903,5243); the diagonal step (1903,5242)->(1904,5243) learned.
        assertTrue(Rs2WalkerDoors.doorTileAdjacentToEdgeEndpoints(
                new WorldPoint(1903, 5243, 0), new WorldPoint(1903, 5242, 0), new WorldPoint(1904, 5243, 0)));
    }

    @Test
    public void aDoorTwoTilesAwayDoesNotSuppressLearning() {
        assertFalse(Rs2WalkerDoors.doorTileAdjacentToEdgeEndpoints(
                new WorldPoint(1875, 5237, 0), new WorldPoint(1875, 5240, 0), new WorldPoint(1876, 5240, 0)));
    }

    @Test
    public void aDoorOnAnotherPlaneDoesNotSuppressLearning() {
        assertFalse(Rs2WalkerDoors.doorTileAdjacentToEdgeEndpoints(
                new WorldPoint(1875, 5239, 1), new WorldPoint(1875, 5240, 0), new WorldPoint(1876, 5240, 0)));
    }

    @Test
    public void faladorFirstGateWingOwnsTheBlockedFrontierBeforeTheLaterDoor() {
        // Live regression 2026-08-16: the route first left reachability across this wing edge,
        // but lookahead clicked the geometrically exact door at (2990,3337) five tiles away.
        WorldPoint blockedFrom = new WorldPoint(2991, 3341, 1);
        WorldPoint blockedTo = new WorldPoint(2991, 3340, 1);
        assertTrue(Rs2WalkerDoors.doorTileAdjacentToEdgeEndpoints(
                new WorldPoint(2990, 3340, 1), blockedFrom, blockedTo));
        assertFalse(Rs2WalkerDoors.doorTileAdjacentToEdgeEndpoints(
                new WorldPoint(2990, 3337, 1), blockedFrom, blockedTo));
    }

    // ---------------------------------------------------------------------------
    // Session blacklist invariants (#19 support)
    // ---------------------------------------------------------------------------

    @Test
    public void sessionBlacklist_addAndMembership() {
        WorldPoint door = new WorldPoint(3210, 3220, 0);
        assertFalse(Rs2WalkerDoors.doorAttemptLedgerForTesting().isDoorBlacklisted(door));
        Rs2WalkerDoors.doorAttemptLedgerForTesting().blacklistDoor(door);
        assertTrue(Rs2WalkerDoors.doorAttemptLedgerForTesting().isDoorBlacklisted(door));
    }

    @Test
    public void sessionBlacklist_worldPointEqualityDrivesMembership() {
        // Two WorldPoints built from the same coords must hash/equal the same way —
        // otherwise the blacklist guard at handleDoors entry would miss re-attempts.
        Rs2WalkerDoors.doorAttemptLedgerForTesting().blacklistDoor(new WorldPoint(3210, 3220, 0));
        assertTrue(Rs2WalkerDoors.doorAttemptLedgerForTesting().isDoorBlacklisted(new WorldPoint(3210, 3220, 0)));
        assertFalse(Rs2WalkerDoors.doorAttemptLedgerForTesting().isDoorBlacklisted(new WorldPoint(3210, 3221, 0)));
        assertFalse("different plane must not collide",
                Rs2WalkerDoors.doorAttemptLedgerForTesting().isDoorBlacklisted(new WorldPoint(3210, 3220, 1)));
    }

    // ---------------------------------------------------------------------------
    // #22 — UNREACHABLE telemetry
    // ---------------------------------------------------------------------------

    @Test
    public void telemetry_recordUnreachable_incrementsCounterAndSetsReason() {
        assertEquals(0, Rs2Walker.Telemetry.unreachableCount.get());
        assertEquals("", Rs2Walker.Telemetry.lastReason);

        Rs2Walker.Telemetry.recordUnreachable("no-walkable-path",
                new WorldPoint(3200, 3200, 0), new WorldPoint(3300, 3300, 0),
                new WorldPoint(3250, 3250, 0), 42, 2, null);

        assertEquals(1, Rs2Walker.Telemetry.unreachableCount.get());
        assertEquals("unreachable:no-walkable-path", Rs2Walker.Telemetry.lastReason);
        assertNotSame("lastEventAtMs must have been stamped", 0L,
                Rs2Walker.Telemetry.lastEventAtMs.get());
    }

    @Test
    public void telemetry_recordUnreachable_nullMetricsDoesNotThrow() {
        Rs2Walker.Telemetry.recordUnreachable("partial-retries-exhausted",
                null, null, null, 0, 2, null);
        assertEquals(1, Rs2Walker.Telemetry.unreachableCount.get());
    }

    @Test
    public void telemetry_recordUnreachable_withRouteMetricsDoesNotLeakPlannerState() {
        Rs2RouteMetrics metrics = new Rs2RouteMetrics(2_000_000L, 12L, 30L, 4L);

        Rs2Walker.Telemetry.recordUnreachable("no-walkable-path",
                new WorldPoint(3200, 3200, 0), new WorldPoint(3201, 3201, 0),
                null, 0, 0, metrics);

        assertEquals(1, Rs2Walker.Telemetry.unreachableCount.get());
    }

    @Test
    public void telemetry_counterIsIndependentOfOtherReasons() {
        Rs2Walker.Telemetry.recordOffPathRecalc(new WorldPoint(3200, 3200, 0), 10);
        Rs2Walker.Telemetry.recordStallRecalc(11_000L, new WorldPoint(3200, 3200, 0));
        Rs2Walker.Telemetry.recordPartialRetry(1, 5);

        assertEquals("unreachable counter must not move for other events",
                0, Rs2Walker.Telemetry.unreachableCount.get());
        assertEquals(1, Rs2Walker.Telemetry.offPathRecalcCount.get());
        assertEquals(1, Rs2Walker.Telemetry.stallRecalcCount.get());
        assertEquals(1, Rs2Walker.Telemetry.partialRetryCount.get());
    }

    @Test
    public void telemetry_recordOffPathRecalcDeferred_setsReasonButDoesNotCountAsRecalc() {
        Rs2Walker.Telemetry.recordOffPathRecalcDeferred("route-progress",
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3210, 3210, 0),
                20);

        assertEquals(1, Rs2Walker.Telemetry.offPathRecalcDeferredCount.get());
        assertEquals("off-path-deferred:route-progress", Rs2Walker.Telemetry.lastReason);
        assertEquals(0, Rs2Walker.Telemetry.totalRecalcs());
    }

    @Test
    public void telemetry_reset_clearsUnreachable() {
        Rs2Walker.Telemetry.recordUnreachable("no-walkable-path",
                new WorldPoint(0, 0, 0), new WorldPoint(1, 1, 0), null, 0, 0, null);
        Rs2Walker.Telemetry.recordUnreachable("partial-retries-exhausted",
                new WorldPoint(0, 0, 0), new WorldPoint(1, 1, 0), null, 0, 0, null);
        assertEquals(2, Rs2Walker.Telemetry.unreachableCount.get());

        Rs2Walker.Telemetry.reset();

        assertEquals(0, Rs2Walker.Telemetry.unreachableCount.get());
        assertEquals("", Rs2Walker.Telemetry.lastReason);
    }

    @Test
    public void telemetry_totalRecalcs_doesNotIncludeUnreachable() {
        // totalRecalcs() feeds health dashboards — an UNREACHABLE is a terminal state,
        // not a recalc, so it must be counted separately.
        Rs2Walker.Telemetry.recordOffPathRecalc(new WorldPoint(3200, 3200, 0), 5);
        Rs2Walker.Telemetry.recordUnreachable("no-walkable-path",
                new WorldPoint(0, 0, 0), new WorldPoint(1, 1, 0), null, 0, 0, null);

        assertEquals(1, Rs2Walker.Telemetry.totalRecalcs());
    }

    @Test
    public void walkUntil_immediatelySatisfiedConditionReturnsArrivedWithoutWalkerSetup() {
        WorldPoint target = new WorldPoint(3200, 3200, 0);

        assertTrue(Rs2Walker.walkUntil(target, 2, () -> true));
        assertEquals(WalkerState.ARRIVED,
                Rs2Walker.walkWithStateUntil(target, 2, () -> true));
    }

    @Test
    public void walkUntil_failedConditionFallsBackToNormalWalkerResult() {
        WorldPoint target = new WorldPoint(3200, 3200, 0);

        assertFalse(Rs2Walker.walkUntil(target, 2, () -> {
            throw new IllegalStateException("test condition failure");
        }));
    }

    @Test(expected = NullPointerException.class)
    public void walkUntil_rejectsNullCondition() {
        Rs2Walker.walkUntil(new WorldPoint(3200, 3200, 0), 2, null);
    }

    // ---- arrival beside an unwalkable target (false-success near interactables) ---------------------

    /**
     * "Within distance of an object" was reported as ARRIVED on straight-line distance alone. With a
     * wall between, the caller then interacted from the wrong side and failed while the walker claimed
     * success — the silent-wrong-success case.
     */
    @Test
    public void hasReachableNeighbour_trueWhenWeCanStandBesideTheTarget() {
        WorldPoint chest = new WorldPoint(3200, 3200, 0);
        java.util.Map<WorldPoint, Integer> reachable = new java.util.HashMap<>();
        reachable.put(new WorldPoint(3200, 3199, 0), 1);   // directly south of it
        assertTrue(Rs2Walker.hasReachableNeighbour(chest, reachable));
    }

    @Test
    public void hasReachableNeighbour_acceptsDiagonalNeighbours() {
        WorldPoint chest = new WorldPoint(3200, 3200, 0);
        java.util.Map<WorldPoint, Integer> reachable = new java.util.HashMap<>();
        reachable.put(new WorldPoint(3201, 3201, 0), 1);
        assertTrue(Rs2Walker.hasReachableNeighbour(chest, reachable));
    }

    /** Near in a straight line, but every adjacent tile is on the far side of a wall. */
    @Test
    public void hasReachableNeighbour_falseWhenOnlyDistantTilesAreReachable() {
        WorldPoint chest = new WorldPoint(3200, 3200, 0);
        java.util.Map<WorldPoint, Integer> reachable = new java.util.HashMap<>();
        reachable.put(new WorldPoint(3205, 3200, 0), 5);
        reachable.put(new WorldPoint(3200, 3205, 0), 5);
        assertFalse(Rs2Walker.hasReachableNeighbour(chest, reachable));
    }

    /** The target's own tile being reachable is not the question — we must stand BESIDE it. */
    @Test
    public void hasReachableNeighbour_targetTileItselfDoesNotCount() {
        WorldPoint chest = new WorldPoint(3200, 3200, 0);
        java.util.Map<WorldPoint, Integer> reachable = new java.util.HashMap<>();
        reachable.put(chest, 0);
        assertFalse(Rs2Walker.hasReachableNeighbour(chest, reachable));
    }

    /** A neighbour on another plane is not somewhere we can stand to use it. */
    @Test
    public void hasReachableNeighbour_ignoresOtherPlanes() {
        WorldPoint chest = new WorldPoint(3200, 3200, 0);
        java.util.Map<WorldPoint, Integer> reachable = new java.util.HashMap<>();
        reachable.put(new WorldPoint(3200, 3199, 1), 1);
        assertFalse(Rs2Walker.hasReachableNeighbour(chest, reachable));
    }

    @Test
    public void hasReachableNeighbour_toleratesMissingInputs() {
        assertFalse(Rs2Walker.hasReachableNeighbour(null, new java.util.HashMap<>()));
        assertFalse(Rs2Walker.hasReachableNeighbour(new WorldPoint(3200, 3200, 0), null));
        assertFalse(Rs2Walker.hasReachableNeighbour(new WorldPoint(3200, 3200, 0), new java.util.HashMap<>()));
    }

    // ---- walled route edge learning (the Sinclair Mansion deadlock) ---------------------------------

    private static java.util.Map<WorldPoint, Integer> reachableSet(WorldPoint... tiles) {
        java.util.Map<WorldPoint, Integer> m = new java.util.HashMap<>();
        for (int i = 0; i < tiles.length; i++) {
            m.put(tiles[i], i);
        }
        return m;
    }

    /**
     * The route steps out of the BFS at b -> that edge is what is actually walled, whatever the shipped
     * map claims. Learning it is what turns a permanent refuse/replan oscillation into one replan.
     */
    @Test
    public void firstWalledRawEdge_findsTheStepThatLeavesTheBfs() {
        WorldPoint p = new WorldPoint(2740, 3469, 0);
        WorldPoint a = new WorldPoint(2740, 3468, 0);
        WorldPoint b = new WorldPoint(2740, 3467, 0);
        java.util.List<WorldPoint> raw = java.util.Arrays.asList(p, a, b, new WorldPoint(2740, 3466, 0));
        WorldPoint[] edge = Rs2WalkerMovement.firstWalledRawEdge(raw, p, reachableSet(p, a), 12);
        assertNotNull(edge);
        assertEquals(a, edge[0]);
        assertEquals(b, edge[1]);
    }

    /** Every step reachable — nothing is walled, so nothing may be learned. */
    @Test
    public void firstWalledRawEdge_allReachableLearnsNothing() {
        WorldPoint p = new WorldPoint(2740, 3469, 0);
        WorldPoint a = new WorldPoint(2740, 3468, 0);
        java.util.List<WorldPoint> raw = java.util.Arrays.asList(p, a);
        assertNull(Rs2WalkerMovement.firstWalledRawEdge(raw, p, reachableSet(p, a), 12));
    }

    /**
     * Beyond the BFS budget "not reachable" means far away, not walled. Learning there would block a
     * perfectly good edge permanently — the failure mode the two-strike store exists to avoid.
     */
    @Test
    public void firstWalledRawEdge_ignoresStepsBeyondTheBfsBudget() {
        WorldPoint p = new WorldPoint(2740, 3469, 0);
        WorldPoint far = new WorldPoint(2740, 3449, 0);
        java.util.List<WorldPoint> raw = java.util.Arrays.asList(p, far);
        assertNull(Rs2WalkerMovement.firstWalledRawEdge(raw, p, reachableSet(p), 12));
    }

    /**
     * A tile sitting AT the BFS budget never had its neighbours enumerated, so the next route tile is
     * missing for want of budget, not because anything blocks it. Convicting that edge writes a lie
     * into the learned-blocked-edge store and routing believes it for the rest of the session.
     *
     * <p>Pinned from a real farm run at the Port Sarim / Land's End docks: a click to (2760,3238) was
     * refused as walled and the edge (2759,3230)->(2759,3231) learned — nine seconds later the walker
     * was standing on (2760,3238), having simply walked there. Chebyshev-near, step-far.
     */
    @Test
    public void firstWalledRawEdge_doesNotConvictTheBfsFrontierItself() {
        WorldPoint player = new WorldPoint(2772, 3234, 0);
        WorldPoint onFrontier = new WorldPoint(2759, 3230, 0);
        WorldPoint beyond = new WorldPoint(2759, 3231, 0);
        java.util.List<WorldPoint> raw = java.util.Arrays.asList(player, onFrontier, beyond);

        java.util.Map<WorldPoint, Integer> reachable = new java.util.HashMap<>();
        reachable.put(player, 0);
        // Thirteen tiles away as the crow flies, but twenty STEPS around the dock buildings — exactly
        // the budget, so the BFS stopped here and knows nothing about what lies past it.
        reachable.put(onFrontier, 20);

        assertNull("a tile at the budget proves nothing about its neighbour",
                Rs2WalkerMovement.firstWalledRawEdge(raw, player, reachable, 20));
    }

    /** An interior tile DID have its neighbours enumerated, so a missing neighbour is genuinely walled. */
    @Test
    public void firstWalledRawEdge_stillConvictsAnEdgeLeavingTheBfsInterior() {
        WorldPoint player = new WorldPoint(2772, 3234, 0);
        WorldPoint interior = new WorldPoint(2770, 3234, 0);
        WorldPoint walled = new WorldPoint(2769, 3234, 0);
        java.util.List<WorldPoint> raw = java.util.Arrays.asList(player, interior, walled);

        java.util.Map<WorldPoint, Integer> reachable = new java.util.HashMap<>();
        reachable.put(player, 0);
        reachable.put(interior, 2);

        WorldPoint[] edge = Rs2WalkerMovement.firstWalledRawEdge(raw, player, reachable, 20);
        assertNotNull("the BFS had budget left at this tile and still could not reach the next one", edge);
        assertEquals(interior, edge[0]);
        assertEquals(walled, edge[1]);
    }

    @Test
    public void firstWalledRawEdge_toleratesMissingInputs() {
        WorldPoint p = new WorldPoint(2740, 3469, 0);
        assertNull(Rs2WalkerMovement.firstWalledRawEdge(null, p, reachableSet(p), 12));
        assertNull(Rs2WalkerMovement.firstWalledRawEdge(java.util.Collections.emptyList(), p, reachableSet(p), 12));
        assertNull(Rs2WalkerMovement.firstWalledRawEdge(java.util.Arrays.asList(p), p, null, 12));
    }

    // ---- post-door route target (chain the click past the opened door) ------------------------------

    /**
     * After a door opens, the follow-through click should make route progress, not step one tile.
     * Every candidate must sit in the player-origin reachability map — the tile the previous attempt
     * at this feature clicked was one the walled-route net had just refused, precisely because the
     * selection ran ungated.
     */

    private static java.util.List<WorldPoint> northRoute(int startY, int count) {
        java.util.List<WorldPoint> route = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            route.add(new WorldPoint(3100, startY + i, 0));
        }
        return route;
    }

    @Test
    public void postDoorTarget_picksTheFurthestReachableRoutePoint() {
        java.util.List<WorldPoint> route = northRoute(3200, 8); // door edge 3201 -> 3202
        WorldPoint from = route.get(1);
        WorldPoint to = route.get(2);
        WorldPoint player = route.get(1);
        java.util.Map<WorldPoint, Integer> reachable =
                reachableSet(route.get(3), route.get(4), route.get(5));
        assertEquals(route.get(5),
                Rs2WalkerDoors.selectPostDoorRouteTarget(route, from, to, player, reachable, 13));
    }

    /** An unreachable far candidate must not be clicked; the furthest REACHABLE one wins instead. */
    @Test
    public void postDoorTarget_skipsTilesTheBfsCannotVouchFor() {
        java.util.List<WorldPoint> route = northRoute(3200, 8);
        WorldPoint from = route.get(1);
        WorldPoint to = route.get(2);
        WorldPoint player = route.get(1);
        java.util.Map<WorldPoint, Integer> reachable = reachableSet(route.get(3), route.get(4));
        assertEquals(route.get(4),
                Rs2WalkerDoors.selectPostDoorRouteTarget(route, from, to, player, reachable, 13));
    }

    /** Nothing reachable past the door: null, and the caller keeps the single-tile nudge. */
    @Test
    public void postDoorTarget_nullWhenNothingPastTheDoorIsReachable() {
        java.util.List<WorldPoint> route = northRoute(3200, 8);
        java.util.Map<WorldPoint, Integer> reachable = reachableSet(route.get(0), route.get(1));
        assertNull(Rs2WalkerDoors.selectPostDoorRouteTarget(route, route.get(1), route.get(2),
                route.get(1), reachable, 13));
    }

    /** The edge must be ON the route: a route that merely passes nearby proves nothing beyond the door. */
    @Test
    public void postDoorTarget_nullWhenTheEdgeIsNotOnTheRoute() {
        java.util.List<WorldPoint> route = northRoute(3200, 8);
        WorldPoint offRouteFrom = new WorldPoint(3105, 3201, 0);
        WorldPoint offRouteTo = new WorldPoint(3105, 3202, 0);
        java.util.Map<WorldPoint, Integer> reachable = reachableSet(route.get(4));
        assertNull(Rs2WalkerDoors.selectPostDoorRouteTarget(route, offRouteFrom, offRouteTo,
                route.get(1), reachable, 13));
    }

    /** Candidates stop at the Euclidean cap and at a plane change — the same rules as route clicks. */
    @Test
    public void postDoorTarget_respectsTheCapAndThePlane() {
        java.util.List<WorldPoint> route = northRoute(3200, 12);
        WorldPoint player = route.get(1);
        java.util.Map<WorldPoint, Integer> reachable =
                reachableSet(route.get(3), route.get(9));
        // route.get(9) is 8 tiles from the player — inside a cap of 13, outside a cap of 6.
        assertEquals(route.get(9),
                Rs2WalkerDoors.selectPostDoorRouteTarget(route, route.get(1), route.get(2), player, reachable, 13));
        assertEquals(route.get(3),
                Rs2WalkerDoors.selectPostDoorRouteTarget(route, route.get(1), route.get(2), player, reachable, 6));

        java.util.List<WorldPoint> upstairs = new java.util.ArrayList<>(northRoute(3200, 4));
        upstairs.add(new WorldPoint(3100, 3204, 1));
        java.util.Map<WorldPoint, Integer> upstairsReachable = reachableSet(route.get(3));
        assertEquals(route.get(3),
                Rs2WalkerDoors.selectPostDoorRouteTarget(upstairs, upstairs.get(1), upstairs.get(2),
                        upstairs.get(1), upstairsReachable, 13));
    }

    @Test
    public void postDoorTarget_toleratesMissingInputs() {
        java.util.List<WorldPoint> route = northRoute(3200, 4);
        WorldPoint p = route.get(0);
        java.util.Map<WorldPoint, Integer> reachable = reachableSet(route.get(3));
        assertNull(Rs2WalkerDoors.selectPostDoorRouteTarget(null, p, route.get(1), p, reachable, 13));
        assertNull(Rs2WalkerDoors.selectPostDoorRouteTarget(route, null, route.get(1), p, reachable, 13));
        assertNull(Rs2WalkerDoors.selectPostDoorRouteTarget(route, p, null, p, reachable, 13));
        assertNull(Rs2WalkerDoors.selectPostDoorRouteTarget(route, p, route.get(1), null, reachable, 13));
        assertNull(Rs2WalkerDoors.selectPostDoorRouteTarget(route, p, route.get(1), p, null, 13));
        assertNull(Rs2WalkerDoors.selectPostDoorRouteTarget(route, p, route.get(1), p,
                new java.util.HashMap<>(), 13));
    }

    // ---- zoom-aware minimap reach --------------------------------------------------------------------
    //
    // The minimap shows 20*4/zoom tiles of radius. Reach follows what the USER's zoom makes visible
    // in BOTH directions: zoomed out, big strides (capped at the reachability BFS horizon — beyond
    // it a wall between could not be detected); zoomed in, SHORT strides. The first cut of this
    // floored at the old flat 11, which quietly broke the zoomed-in half: an 11-tile stride on a
    // minimap showing ~8 tiles of radius selects a point on or past the rim.

    private static final int MIN_REACH = 5;
    private static final int CAP = 18;
    private static final int FALLBACK = 11;

    @Test
    public void zoomAwareReach_zoomedOutStridesFurtherUpToTheBfsHorizon() {
        assertEquals(18, Rs2WalkerMovement.zoomAwareMinimapReach(4.0, MIN_REACH, CAP, FALLBACK)); // default: 20-2 -> cap
        assertEquals(18, Rs2WalkerMovement.zoomAwareMinimapReach(2.0, MIN_REACH, CAP, FALLBACK)); // fully out: 38 -> cap
    }

    @Test
    public void zoomAwareReach_zoomedInStridesShorter() {
        assertEquals(14, Rs2WalkerMovement.zoomAwareMinimapReach(5.0, MIN_REACH, CAP, FALLBACK)); // pinned-era zoom: 16-2
        assertEquals(11, Rs2WalkerMovement.zoomAwareMinimapReach(6.0, MIN_REACH, CAP, FALLBACK)); // 13-2
        // Fully zoomed in the visible radius is ~8: the stride must SHRINK below the old flat 11.
        assertEquals(8, Rs2WalkerMovement.zoomAwareMinimapReach(8.0, MIN_REACH, CAP, FALLBACK));
    }

    @Test
    public void zoomAwareReach_extremeZoomStopsAtTheFunctionalFloor() {
        assertEquals(MIN_REACH, Rs2WalkerMovement.zoomAwareMinimapReach(16.0, MIN_REACH, CAP, FALLBACK)); // 5-2=3 -> floor
    }

    @Test
    public void zoomAwareReach_degenerateZoomFallsBackToTheFlatReach() {
        assertEquals(FALLBACK, Rs2WalkerMovement.zoomAwareMinimapReach(0.0, MIN_REACH, CAP, FALLBACK));
        assertEquals(FALLBACK, Rs2WalkerMovement.zoomAwareMinimapReach(-1.0, MIN_REACH, CAP, FALLBACK));
    }
}
