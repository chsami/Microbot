package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.walker.recovery.WalledReplanLedger;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WalledReplanLedgerTest {
    private static final WorldPoint PLAYER = new WorldPoint(2665, 3334, 1);
    private static final WorldPoint TARGET = new WorldPoint(2666, 3334, 1);

    @Test
    public void strikesOutOnFifthConsecutiveReplanThenResets() {
        WalledReplanLedger ledger = new WalledReplanLedger();
        for (int i = 0; i < 4; i++) {
            assertFalse(ledger.record(PLAYER, TARGET, 5));
        }
        assertTrue(ledger.record(PLAYER, TARGET, 5));
        assertEquals(0, ledger.streak());
        assertFalse(ledger.record(PLAYER, TARGET, 5));
    }

    @Test
    public void changingTileOrTargetRestartsStreak() {
        WalledReplanLedger ledger = new WalledReplanLedger();
        for (int i = 0; i < 4; i++) {
            ledger.record(PLAYER, TARGET, 5);
        }
        assertFalse(ledger.record(new WorldPoint(2665, 3335, 1), TARGET, 5));
        assertEquals(1, ledger.streak());
        assertFalse(ledger.record(new WorldPoint(2665, 3335, 1), new WorldPoint(2666, 3335, 1), 5));
        assertEquals(1, ledger.streak());
    }

    @Test
    public void nullInputsResetWithoutStriking() {
        WalledReplanLedger ledger = new WalledReplanLedger();
        ledger.record(PLAYER, TARGET, 5);
        assertFalse(ledger.record(null, TARGET, 5));
        assertEquals(0, ledger.streak());
    }
}
