package net.runelite.client.plugins.microbot.util.bank;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

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
    public void walkToBankIsFalseWithoutBankLocation() {
        assertFalse(Rs2Bank.walkToBank());
        assertFalse(Rs2Bank.walkToBank(null));
        assertFalse(Rs2Bank.walkToBank(null, false));
    }

    @Test
    public void miningGuildRequirementsAreUnmetWithoutPlayerLocation() {
        player.when(Rs2Player::isMember).thenReturn(true);
        player.when(Rs2Player::isInMemberWorld).thenReturn(true);
        assertFalse(BankLocation.MINING_GUILD.hasRequirements());
    }

    @Test
    public void walkBackIsNotDoneWithoutPlayerLocation() {
        WorldPoint origin = new WorldPoint(2742, 3444, 0);
        try (MockedStatic<Rs2Inventory> inventory = Mockito.mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Walker> walker = Mockito.mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Tile> tile = Mockito.mockStatic(Rs2Tile.class)) {
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(20);

            assertFalse(Rs2Bank.bankItemsAndWalkBackToOriginalPosition(List.of("Flax"), false, BankLocation.CAMELOT, origin, 0, 4));

            walker.verifyNoInteractions();
            tile.verifyNoInteractions();
        }
    }

    @Test
    public void walkBackIsNotDoneWhenPlayerLocationIsLostDuringWalk() {
        WorldPoint origin = new WorldPoint(2742, 3444, 0);
        player.when(Rs2Player::getWorldLocation).thenReturn(origin, (WorldPoint) null);
        try (MockedStatic<Rs2Inventory> inventory = Mockito.mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Walker> walker = Mockito.mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Tile> tile = Mockito.mockStatic(Rs2Tile.class)) {
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(20);
            tile.when(() -> Rs2Tile.isTileReachable(origin)).thenReturn(true);

            assertFalse(Rs2Bank.bankItemsAndWalkBackToOriginalPosition(List.of("Flax"), false, BankLocation.CAMELOT, origin, 0, 4));

            walker.verify(() -> Rs2Walker.walkFastCanvas(origin));
        }
    }

    @Test
    public void walkBackIsDoneWhenPlayerIsAtOrigin() {
        WorldPoint origin = new WorldPoint(2742, 3444, 0);
        player.when(Rs2Player::getWorldLocation).thenReturn(origin);
        try (MockedStatic<Rs2Inventory> inventory = Mockito.mockStatic(Rs2Inventory.class);
             MockedStatic<Rs2Walker> walker = Mockito.mockStatic(Rs2Walker.class);
             MockedStatic<Rs2Tile> tile = Mockito.mockStatic(Rs2Tile.class)) {
            inventory.when(Rs2Inventory::emptySlotCount).thenReturn(20);
            tile.when(() -> Rs2Tile.isTileReachable(origin)).thenReturn(true);

            assertTrue(Rs2Bank.bankItemsAndWalkBackToOriginalPosition(List.of("Flax"), false, BankLocation.CAMELOT, origin, 0, 4));
        }
    }

    @Test
    public void distanceBetweenIsUnreachableForNullEndpoint() {
        WorldPoint point = new WorldPoint(3164, 3487, 0);
        assertEquals(Integer.MAX_VALUE, Rs2Walker.getDistanceBetween(null, point));
        assertEquals(Integer.MAX_VALUE, Rs2Walker.getDistanceBetween(point, null));
    }
}
