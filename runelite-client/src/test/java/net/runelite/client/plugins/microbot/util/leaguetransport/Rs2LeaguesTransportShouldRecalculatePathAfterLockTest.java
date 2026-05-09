package net.runelite.client.plugins.microbot.util.leaguetransport;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * Guards {@link Rs2LeaguesTransport#shouldRecalculatePathAfterLock} defensive null semantics
 * (unknown callers / tests — production chat path normally passes non-null after a successful record).
 */
public class Rs2LeaguesTransportShouldRecalculatePathAfterLockTest
{
    /**
     * Arbitrary non-null second argument; {@link Rs2LeaguesTransport#shouldRecalculatePathAfterLock(String, Integer)}
     * ignores {@code packedDest} when {@code region == null} (arguments are still evaluated by the caller).
     */
    private static final Integer PACKED_DEST_PLACEHOLDER_WHEN_REGION_NULL = 0x13371337;

    @Test
    public void nullRegionForcesRecalculateDecisionTrue()
    {
        assertTrue(Rs2LeaguesTransport.shouldRecalculatePathAfterLock(null, PACKED_DEST_PLACEHOLDER_WHEN_REGION_NULL));
    }

    @Test
    public void nullPackedDestForcesRecalculateDecisionTrue()
    {
        assertTrue(Rs2LeaguesTransport.shouldRecalculatePathAfterLock("Ardougne", null));
    }
}
