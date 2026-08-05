package net.runelite.client.plugins.microbot.util.walker.door;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class Rs2WalkerAwaitsTest {
    @Test
    public void shouldAcceptIdleDoorAwait_acceptsStationaryPastMinimum() {
        assertFalse(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, false, 1200L, false));
        assertTrue(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, false, 1201L, true));
    }

    /**
     * The old implementation ended in {@code return edgeResolved}, which made this branch dead code:
     * the traversal wait returns the moment the edge resolves, so it only ever reached here with
     * edgeResolved == false. A stalled interaction burned the full 2200ms budget instead of releasing.
     */
    @Test
    public void shouldAcceptIdleDoorAwait_acceptsUnresolvedEdgeWhenStalled() {
        assertTrue(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, false, 1500L, false));
    }

    @Test
    public void shouldAcceptIdleDoorAwait_rejectsMovingOrAnimating() {
        assertFalse(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(true, false, 5000L, true));
        assertFalse(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, true, 5000L, true));
    }

    @Test
    public void shouldAcceptIdleDoorAwait_rejectsBeforeMinimumElapsed() {
        assertFalse(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, false, 1200L, true));
        assertFalse(Rs2WalkerAwaits.shouldAcceptIdleDoorAwait(false, false, 800L, true));
    }
}
