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
    public void rimmingtonPrefersFootBankOverCheaperShipBank() {
        WorldPoint rimmington = new WorldPoint(2969, 3206, 0);
        NearestBankPolicy.BankOption ship = new NearestBankPolicy.BankOption(BankLocation.PORT_KHAZARD, 20, false, true);
        NearestBankPolicy.BankOption falador = new NearestBankPolicy.BankOption(BankLocation.FALADOR_WEST, 90, false, false);
        NearestBankPolicy.BankOption pick = NearestBankPolicy.choose(rimmington, rimmington, Arrays.asList(ship, falador));
        assertTrue(pick.bank == BankLocation.FALADOR_WEST);
    }

    @Test
    public void shipBankIsKeptWhenNothingIsReachableOnFoot() {
        NearestBankPolicy.BankOption ship = new NearestBankPolicy.BankOption(BankLocation.PORT_KHAZARD, 20, false, true);
        assertTrue(NearestBankPolicy.choose(new WorldPoint(2969, 3206, 0), null, Arrays.asList(ship)).bank == BankLocation.PORT_KHAZARD);
    }

    @Test
    public void travelTransportTypes() {
        assertTrue(NearestBankPolicy.isTravelTransport(net.runelite.client.plugins.microbot.util.walker.Rs2TransportType.SHIP));
        assertTrue(NearestBankPolicy.isTravelTransport(net.runelite.client.plugins.microbot.util.walker.Rs2TransportType.QUETZAL));
        assertTrue(NearestBankPolicy.isTravelTransport(net.runelite.client.plugins.microbot.util.walker.Rs2TransportType.CHARTER_SHIP));
        assertFalse(NearestBankPolicy.isTravelTransport(net.runelite.client.plugins.microbot.util.walker.Rs2TransportType.TRANSPORT));
    }

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

    private static final WorldPoint ARDOUGNE_GARDEN = new WorldPoint(2566, 3332, 0);

    @Test
    public void freeHomeTeleportDoesNotBeatNearbyBank() {
        List<NearestBankPolicy.BankOption> options = Arrays.asList(
                new NearestBankPolicy.BankOption(BankLocation.LUMBRIDGE_TOP, 1, true),
                new NearestBankPolicy.BankOption(BankLocation.ARDOUGNE_NORTH, 60, false));
        NearestBankPolicy.BankOption pick = NearestBankPolicy.choose(ARDOUGNE_GARDEN, ARDOUGNE_GARDEN, options);
        assertTrue(pick.bank == BankLocation.ARDOUGNE_NORTH);
    }

    @Test
    public void homeTeleportDroppedUnlessFar() {
        assertFalse(NearestBankPolicy.homeTeleportAllowed(ARDOUGNE_GARDEN, BankLocation.ARDOUGNE_NORTH.getWorldPoint()));
        assertTrue(NearestBankPolicy.homeTeleportAllowed(ARDOUGNE_GARDEN, BankLocation.LUMBRIDGE_TOP.getWorldPoint()));
        assertTrue(NearestBankPolicy.choose(ARDOUGNE_GARDEN, ARDOUGNE_GARDEN, Arrays.asList(
                new NearestBankPolicy.BankOption(BankLocation.ARDOUGNE_NORTH, 60, true))) == null);
    }

    @Test
    public void returnLegDecidesBetweenBanks() {
        WorldPoint returnPoint = BankLocation.ARDOUGNE_SOUTH.getWorldPoint();
        List<NearestBankPolicy.BankOption> options = Arrays.asList(
                new NearestBankPolicy.BankOption(BankLocation.ARDOUGNE_NORTH, 50, false),
                new NearestBankPolicy.BankOption(BankLocation.ARDOUGNE_SOUTH, 60, false));
        assertTrue(NearestBankPolicy.choose(ARDOUGNE_GARDEN, returnPoint, options).bank == BankLocation.ARDOUGNE_SOUTH);
    }

    @Test
    public void nearbyBankWinsTies() {
        WorldPoint player = BankLocation.ARDOUGNE_NORTH.getWorldPoint();
        List<NearestBankPolicy.BankOption> options = Arrays.asList(
                new NearestBankPolicy.BankOption(BankLocation.EDGEVILLE, 10, false),
                new NearestBankPolicy.BankOption(BankLocation.ARDOUGNE_NORTH, 10, false));
        // Edgeville's return leg is hundreds of tiles; nearby bank is also strictly cheaper here.
        assertTrue(NearestBankPolicy.choose(player, player, options).bank == BankLocation.ARDOUGNE_NORTH);
    }
}
