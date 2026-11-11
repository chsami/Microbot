package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

public class StrongholdDoorNullLocationTest {

    private MockedStatic<Rs2Player> player;

    @Before
    public void setUp() {
        player = Mockito.mockStatic(Rs2Player.class);
    }

    @After
    public void tearDown() {
        player.close();
    }

    @Test
    public void missingPlayerLocationIsNotInTheStronghold() {
        player.when(Rs2Player::getWorldLocation).thenReturn(null);

        assertFalse(Rs2WalkerDoors.isInStrongholdOfSecurity());
    }

    @Test
    public void missingPlayerLocationSkipsTheDoorException() {
        player.when(Rs2Player::getWorldLocation).thenReturn(null);
        TileObject door = mock(TileObject.class);

        assertFalse(Rs2WalkerDoors.handleDoorException(door, "Open"));
        verifyNoInteractions(door);
    }

    @Test
    public void strongholdRegionIsStillDetected() {
        player.when(Rs2Player::getWorldLocation).thenReturn(WorldPoint.fromRegion(7505, 10, 10, 0));

        assertTrue(Rs2WalkerDoors.isInStrongholdOfSecurity());
    }

    @Test
    public void otherRegionIsNotTheStronghold() {
        player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3200, 3200, 0));

        assertFalse(Rs2WalkerDoors.isInStrongholdOfSecurity());
    }
}
