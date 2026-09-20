package net.runelite.client.plugins.microbot.util.reachable;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.function.Supplier;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

public class Rs2ReachableTest
{
    private Object oldClient;
    private Object oldThread;
    private Client client;
    private WorldView view;
    private final WorldPoint origin = new WorldPoint(3201, 3201, 0);
    private final WorldPoint isolated = new WorldPoint(3205, 3205, 0);

    private static Object replace(String name, Object value) throws Exception
    {
        Field field = Microbot.class.getDeclaredField(name);
        field.setAccessible(true);
        Object old = field.get(null);
        field.set(null, value);
        return old;
    }

    @Before
    public void setUp() throws Exception
    {
        client = mock(Client.class);
        when(client.isClientThread()).thenReturn(true);
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        ClientThread thread = mock(ClientThread.class);
        when(thread.invoke(any(Supplier.class))).thenAnswer(i -> ((Supplier<?>) i.getArgument(0)).get());
        oldClient = replace("client", client);
        oldThread = replace("clientThread", thread);
        view = mock(WorldView.class);
        Player player = mock(Player.class);
        when(client.getLocalPlayer()).thenReturn(player);
        when(client.getTopLevelWorldView()).thenReturn(view);
        when(player.getWorldView()).thenReturn(view);
        when(player.getWorldLocation()).thenReturn(origin);
        when(view.getBaseX()).thenReturn(3200);
        when(view.getBaseY()).thenReturn(3200);
        when(client.getTickCount()).thenReturn(1);
        int[][] flags = new int[104][104];
        for (int[] row : flags) Arrays.fill(row, CollisionDataFlag.BLOCK_MOVEMENT_FULL);
        flags[1][1] = flags[2][1] = flags[5][5] = 0;
        CollisionData collision = mock(CollisionData.class);
        when(collision.getFlags()).thenReturn(flags);
        when(view.getCollisionMaps()).thenReturn(new CollisionData[]{collision});
    }

    @After
    public void tearDown() throws Exception
    {
        replace("client", oldClient);
        replace("clientThread", oldThread);
    }

    @Test
    public void disconnectedTargetDoesNotBecomeOrigin()
    {
        assertFalse(Rs2Reachable.isReachable(isolated));
        assertTrue(Rs2Reachable.isReachable(origin));
        Rs2Reachable.getReachableTiles(isolated);
        assertTrue(Rs2Reachable.isReachable(origin));
        assertFalse(Rs2Reachable.isReachable(isolated));
    }

    @Test
    public void cacheRejectsWorldAndSceneReplacementAndCannotBeMutated()
    {
        assertTrue(Rs2Reachable.isReachable(origin));
        try {
            Rs2Reachable.getReachableTiles().clear();
            fail("Published reachability must be read-only");
        } catch (UnsupportedOperationException expected) { }
        when(view.getScene()).thenReturn(mock(Scene.class));
        assertTrue(Rs2Reachable.isReachable(origin));
        when(client.getWorld()).thenReturn(302);
        assertTrue(Rs2Reachable.isReachable(origin));
        when(view.getBaseX()).thenReturn(3300);
        assertFalse(Rs2Reachable.isReachable(origin));
        assertFalse(Rs2Reachable.isReachable(origin, mock(WorldView.class)));
        when(client.getGameState()).thenReturn(GameState.HOPPING);
        assertFalse(Rs2Reachable.isReachable(origin));
    }

    @Test
    public void missingAndDifferentPlaneTargetsAreUnreachable()
    {
        assertFalse(Rs2Reachable.isReachable(null));
        assertFalse(Rs2Reachable.isReachable(new WorldPoint(3201, 3201, 1)));
    }

    @Test
    public void failedFloodClearsPublishedTilesAndCachedContext()
    {
        CollisionData collision = view.getCollisionMaps()[0];
        int[][] validFlags = collision.getFlags();
        Runnable[] failures = {
                () -> when(view.getCollisionMaps()).thenReturn(null),
                () -> when(view.getCollisionMaps()).thenReturn(new CollisionData[0]),
                () -> when(view.getCollisionMaps()).thenReturn(new CollisionData[]{null}),
                () -> when(collision.getFlags()).thenReturn(null),
                () -> when(collision.getFlags()).thenReturn(new int[0][]),
                () -> when(collision.getFlags()).thenReturn(new int[][]{null}),
                () -> when(view.getBaseX()).thenReturn(3300)
        };
        for (Runnable failure : failures) {
            when(view.getCollisionMaps()).thenReturn(new CollisionData[]{collision});
            when(collision.getFlags()).thenReturn(validFlags);
            when(view.getBaseX()).thenReturn(3200);
            when(client.getTickCount()).thenReturn(1);
            assertFalse(Rs2Reachable.getReachableTiles(origin).isEmpty());
            when(client.getTickCount()).thenReturn(2);
            failure.run();
            assertTrue(Rs2Reachable.getReachableTiles(origin).isEmpty());
            assertTrue(Rs2Reachable.getReachableTiles().isEmpty());
            // Restore the old cache key with unavailable data: stale tiles must not reappear.
            when(view.getBaseX()).thenReturn(3200);
            when(client.getTickCount()).thenReturn(1);
            when(view.getCollisionMaps()).thenReturn(null);
            assertTrue(Rs2Reachable.getReachableTiles(origin).isEmpty());
        }
    }
}
