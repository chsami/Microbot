package net.runelite.client.plugins.microbot.util.bank;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import org.junit.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NearestBankPolicyTest {
    private static final WorldPoint FALADOR_NORTH = new WorldPoint(3024, 3504, 0);

    private static final List<WorldPoint> CANDIDATES = Arrays.asList(
            BankLocation.EDGEVILLE.getWorldPoint(),
            BankLocation.FALADOR_EAST.getWorldPoint(),
            BankLocation.FALADOR_WEST.getWorldPoint());

    @Test
    public void minigameBankExcludedFarAway() {
        assertFalse(NearestBankPolicy.isEligibleTarget(FALADOR_NORTH, BankLocation.ISLE_OF_SOULS));
        assertTrue(NearestBankPolicy.isEligibleTarget(new WorldPoint(2215, 2860, 0), BankLocation.ISLE_OF_SOULS));
        assertTrue(NearestBankPolicy.isEligibleTarget(FALADOR_NORTH, BankLocation.EDGEVILLE));
    }

    @Test
    public void minigamePortalRouteToFarBankRejected() {
        assertFalse(NearestBankPolicy.isPlausible(FALADOR_NORTH, BankLocation.ISLE_OF_SOULS.getWorldPoint(),
                CANDIDATES, true, false));
    }

    @Test
    public void farWalkingRouteRejectedByRatio() {
        assertFalse(NearestBankPolicy.isPlausible(FALADOR_NORTH, new WorldPoint(3024 + 300, 3504, 0),
                CANDIDATES, false, false));
    }

    @Test
    public void seedTilesSkipsBlockedAndOrdersByRing() {
        WorldPoint start = new WorldPoint(3030, 3510, 1);
        java.util.List<WorldPoint> seeds = NearestBankPolicy.seedTiles(start,
                p -> p.getX() <= 3029 && p.getPlane() == 1, 3, 3);
        assertTrue(seeds.size() == 3);
        for (WorldPoint s : seeds) {
            assertTrue(s.getX() == 3029);
        }
        assertTrue(NearestBankPolicy.seedTiles(start, p -> false, 3, 3).isEmpty());
    }

    @Test
    public void backOffOnlySameTileWithinWindow() {
        WorldPoint t = new WorldPoint(3030, 3510, 1);
        assertTrue(NearestBankPolicy.shouldBackOff(t, 1000, 1000 + 1000, t));
        assertFalse(NearestBankPolicy.shouldBackOff(t, 1000, 1000 + NearestBankPolicy.BACKOFF_MS, t));
        assertFalse(NearestBankPolicy.shouldBackOff(t, 1000, 2000, new WorldPoint(3031, 3510, 1)));
        assertFalse(NearestBankPolicy.shouldBackOff(null, 0, 1, t));
    }

    @Test
    public void straightLineFallbackPicksNearestBankFromUpstairs() {
        WorldPoint fortress = new WorldPoint(3030, 3510, 1);
        BankLocation b = NearestBankPolicy.nearestByDistance(fortress,
                Arrays.asList(BankLocation.EDGEVILLE, BankLocation.FALADOR_EAST, BankLocation.FALADOR_WEST), 150);
        assertTrue(b == BankLocation.EDGEVILLE || b == BankLocation.FALADOR_EAST || b == BankLocation.FALADOR_WEST);
        assertTrue(NearestBankPolicy.nearestByDistance(fortress, Arrays.asList(BankLocation.EDGEVILLE), 5) == null);
    }

    @Test
    public void nearBankAccepted() {
        assertTrue(NearestBankPolicy.isPlausible(FALADOR_NORTH, BankLocation.EDGEVILLE.getWorldPoint(),
                CANDIDATES, false, false));
    }

    @Test
    public void genuineItemTeleportStillAllowed() {
        WorldPoint desert = new WorldPoint(3300, 3000, 0);
        assertTrue(NearestBankPolicy.isPlausible(desert, BankLocation.EDGEVILLE.getWorldPoint(),
                CANDIDATES, false, true));
    }
}
