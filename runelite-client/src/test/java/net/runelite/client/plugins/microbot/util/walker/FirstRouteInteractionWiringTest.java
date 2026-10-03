package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.door.FirstRouteInteractionSelector;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class FirstRouteInteractionWiringTest {

    @Test
    public void expiredGateBehindAnchorDoesNotDispatchOrBlockDoorAhead() {
        assertCrossedGateAllowsDoor(false, true);
        assertCrossedGateAllowsDoor(false, false);
    }

    @Test
    public void gateCrossedByPlainWalkingDoesNotDispatchOrBlockDoorAhead() {
        assertCrossedGateAllowsDoor(true, true);
        assertCrossedGateAllowsDoor(true, false);
    }

    private static void assertCrossedGateAllowsDoor(boolean plainWalking, boolean closedGate) {
        List<WorldPoint> path = path();
        List<Rs2RouteStep> steps = steps(path);
        steps.set(3, transportStep(path, 3));
        for (int anchor : new int[]{4, 5}) {
            int start = FirstRouteInteractionSelector.scanStartEdge(path, anchor, path.get(anchor),
                    plainWalking ? null : path.get(3), plainWalking ? null : path.get(4), false);
            assertTrue("crossed gate must remain in the scene-door lookback", start <= 3);
            Map<Integer, Rs2TransportEdge> transports = Rs2Walker.collectRouteTransportsForInteractionScan(
                    steps, start, anchor, 8);
            assertFalse("the production map must exclude the crossed gate", transports.containsKey(3));
            AtomicInteger gateProbes = new AtomicInteger();
            FirstRouteInteractionSelector.Selection selection = select(path, start, path.get(anchor),
                    transports, edge -> {
                        if (edge == 3 && transports.containsKey(edge)) {
                            gateProbes.incrementAndGet();
                            return closedGate;
                        }
                        return edge == 6;
                    });
            assertEquals(FirstRouteInteractionSelector.Kind.INTERACTION, selection.kind());
            assertEquals("select the door ahead without waiting for the interim", 6, selection.edgeIndex());
            assertEquals("neither open nor closed crossed gates may be dispatched", 0, gateProbes.get());
            assertFalse(transports.containsKey(selection.edgeIndex()));
        }
    }

    @Test
    public void transportAtOrAheadOfAnchorRetainsOwnership() {
        List<WorldPoint> path = path();
        for (int edge : new int[]{4, 5}) {
            List<Rs2RouteStep> steps = steps(path);
            steps.set(edge, transportStep(path, edge));
            Map<Integer, Rs2TransportEdge> transports = Rs2Walker.collectRouteTransportsForInteractionScan(
                    steps, 2, 4, 8);
            assertSame(steps.get(edge).getTransport().orElseThrow(), transports.get(edge));
            for (boolean eligible : new boolean[]{true, false}) {
                FirstRouteInteractionSelector.Selection selection = select(path, 2, path.get(4), transports,
                        candidate -> transports.containsKey(candidate) ? eligible : candidate == 6);
                assertEquals(eligible ? FirstRouteInteractionSelector.Kind.INTERACTION
                        : FirstRouteInteractionSelector.Kind.BLOCKED_TRANSPORT, selection.kind());
                assertEquals(edge, selection.edgeIndex());
            }
        }
    }

    @Test
    public void ordinarySceneDoorLookbackIsPreserved() {
        List<WorldPoint> path = path();
        Map<Integer, Rs2TransportEdge> transports = Rs2Walker.collectRouteTransportsForInteractionScan(
                steps(path), 2, 4, 8);
        assertTrue(transports.isEmpty());
        FirstRouteInteractionSelector.Selection selection = select(path, 2, path.get(4), transports,
                edge -> edge == 3 || edge == 6);
        assertEquals(FirstRouteInteractionSelector.Kind.INTERACTION, selection.kind());
        assertEquals(3, selection.edgeIndex());
    }

    @Test
    public void transportCollectionHonorsScanWindowAndRouteEnd() {
        List<WorldPoint> path = path();
        List<Rs2RouteStep> steps = steps(path);
        steps.set(7, transportStep(path, 7));
        assertTrue(Rs2Walker.collectRouteTransportsForInteractionScan(steps, 2, 4, 5).isEmpty());
        assertTrue(Rs2Walker.collectRouteTransportsForInteractionScan(steps, 2, 4, 20).containsKey(7));
    }

    private static FirstRouteInteractionSelector.Selection select(List<WorldPoint> path, int start,
            WorldPoint player, Map<Integer, Rs2TransportEdge> transports,
            java.util.function.IntPredicate pending) {
        Map<WorldPoint, Integer> reachable = new HashMap<>();
        for (int i = 0; i < path.size(); i++) {
            reachable.put(path.get(i), i);
        }
        return FirstRouteInteractionSelector.selectFirst(path, start, 8, player, reachable, 10,
                pending, transports::containsKey);
    }

    private static List<WorldPoint> path() {
        List<WorldPoint> path = new ArrayList<>();
        for (int i = 0; i <= 10; i++) {
            path.add(new WorldPoint(3200 + i, 3200, 0));
        }
        return path;
    }

    private static List<Rs2RouteStep> steps(List<WorldPoint> path) {
        List<Rs2RouteStep> steps = new ArrayList<>();
        for (int i = 0; i < path.size() - 1; i++) {
            steps.add(Rs2RouteStep.walk(path.get(i), path.get(i + 1)));
        }
        return steps;
    }

    private static Rs2RouteStep transportStep(List<WorldPoint> path, int edge) {
        Rs2TransportEdge transport = new Rs2TransportEdge(path.get(edge), path.get(edge + 1),
                Rs2TransportType.TRANSPORT, Rs2TransportExecutor.OBJECT, Rs2TerminalTravelMode.UNSUPPORTED,
                "test", "Open", "Gate", 1, 1, false, false, true, 0, "", 0, List.of());
        return Rs2RouteStep.transport(path.get(edge), path.get(edge + 1), transport);
    }
}
