package net.runelite.client.plugins.microbot.util.bank;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class Rs2BankNullPlayerLocationTest {

    private MockedStatic<Rs2Player> player;

    @Before
    public void setUp() {
        player = Mockito.mockStatic(Rs2Player.class);
        player.when(Rs2Player::getWorldLocation).thenReturn(null);
    }

    @After
    public void tearDown() {
        player.close();
    }

    @Test
    public void nearestBankLookupsReturnNoBankWithoutPlayerLocation() {
        assertNull(Rs2Bank.getNearestBank());
        assertNull(Rs2Bank.getNearestBank(null));
        assertNull(Rs2Bank.getNearestBank(null, 20));
        assertNull(Rs2Bank.getNearestBankRoute(null));
        assertTrue(Rs2Bank.getPathToNearestBank().isEmpty());
    }

    @Test
    public void isNearBankIsFalseWithoutPlayerLocation() {
        assertFalse(Rs2Bank.isNearBank(10));
        assertFalse(Rs2Bank.isNearBank(BankLocation.GRAND_EXCHANGE, 10));
    }

    @Test
    public void distanceBetweenIsUnreachableForNullEndpoint() {
        WorldPoint point = new WorldPoint(3164, 3487, 0);
        assertEquals(Integer.MAX_VALUE, Rs2Walker.getDistanceBetween(null, point));
        assertEquals(Integer.MAX_VALUE, Rs2Walker.getDistanceBetween(point, null));
    }
}
