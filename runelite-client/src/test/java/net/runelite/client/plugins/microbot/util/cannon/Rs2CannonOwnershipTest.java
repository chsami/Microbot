package net.runelite.client.plugins.microbot.util.cannon;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.RETURNS_SELF;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Rs2CannonOwnershipTest {

    private static final WorldPoint CANNON_CENTER = new WorldPoint(3200, 3200, 0);

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Inventory> inventory;
    private Rs2TileObjectModel cannon;

    @Before
    public void setUp() {
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any(Callable.class))).thenReturn(Optional.of(0));
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);

        inventory = Mockito.mockStatic(Rs2Inventory.class);
        inventory.when(() -> Rs2Inventory.hasItemAmount(eq("cannonball"), anyInt(), anyBoolean())).thenReturn(true);

        cannon = mock(Rs2TileObjectModel.class);
        when(cannon.getRuneLiteWorldLocation()).thenReturn(CANNON_CENTER);
        Rs2TileObjectQueryable query = mock(Rs2TileObjectQueryable.class, RETURNS_SELF);
        when(query.nearestOnClientThread(anyInt())).thenReturn(cannon);
        Rs2TileObjectCache cache = mock(Rs2TileObjectCache.class);
        when(cache.query()).thenReturn(query);
        microbot.when(Microbot::getRs2TileObjectCache).thenReturn(cache);
    }

    @After
    public void tearDown() {
        inventory.close();
        microbot.close();
        Microbot.pauseAllScripts.set(false);
    }

    private void ownCannonAt(int coord) {
        microbot.when(() -> Microbot.getVarbitPlayerValue(VarPlayerID.OWNEDMCANNON)).thenReturn(coord);
    }

    private static int coordOf(int x, int y, int plane) {
        return (plane << 28) | (x << 14) | y;
    }

    @Test
    public void refillReturnsFalseWhenNoCannonOwned() {
        ownCannonAt(0);
        assertFalse(Rs2Cannon.refill(15));
        assertFalse(Microbot.pauseAllScripts.get());
        verify(cannon, never()).click(anyString());
    }

    @Test
    public void refillReturnsFalseWhenOwnedCannonIsElsewhere() {
        ownCannonAt(coordOf(3300, 3300, 0));
        assertFalse(Rs2Cannon.refill(15));
        assertFalse(Microbot.pauseAllScripts.get());
        verify(cannon, never()).click(anyString());
    }

    @Test
    public void repairReturnsFalseWhenNoCannonOwned() {
        ownCannonAt(-1);
        assertFalse(Rs2Cannon.repair());
        verify(cannon, never()).click(anyString());
    }

    @Test
    public void repairInteractsWithOwnedCannonWithoutCannonPlugin() {
        ownCannonAt(coordOf(CANNON_CENTER.getX() - 1, CANNON_CENTER.getY() - 1, CANNON_CENTER.getPlane()));
        assertTrue(Rs2Cannon.repair());
        verify(cannon).click("Repair");
    }
}
