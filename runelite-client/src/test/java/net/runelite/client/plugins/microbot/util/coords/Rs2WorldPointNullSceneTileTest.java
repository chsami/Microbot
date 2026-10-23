package net.runelite.client.plugins.microbot.util.coords;

import net.runelite.api.Client;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.function.Supplier;

import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2WorldPointNullSceneTileTest
{
    private static final int BASE_X = 3136;
    private static final int BASE_Y = 3136;
    private static final int SIZE = 104;
    private static final WorldPoint SOURCE = new WorldPoint(BASE_X + 50, BASE_Y + 50, 0);
    private static final WorldPoint TARGET = new WorldPoint(BASE_X + 55, BASE_Y + 50, 0);

    private MockedStatic<Microbot> microbot;
    private Tile[][][] tiles;

    @Before
    public void setUp()
    {
        tiles = new Tile[4][SIZE][SIZE];

        Scene scene = mock(Scene.class);
        when(scene.getTiles()).thenReturn(tiles);

        WorldView worldView = mock(WorldView.class);
        when(worldView.getBaseX()).thenReturn(BASE_X);
        when(worldView.getBaseY()).thenReturn(BASE_Y);
        when(worldView.getSizeX()).thenReturn(SIZE);
        when(worldView.getSizeY()).thenReturn(SIZE);
        when(worldView.getId()).thenReturn(-1);
        when(worldView.getScene()).thenReturn(scene);

        Client client = mock(Client.class);
        when(client.getTopLevelWorldView()).thenReturn(worldView);

        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.invoke(any(Supplier.class))).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());

        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
    }

    @After
    public void tearDown()
    {
        microbot.close();
    }

    @Test
    public void pathToReturnsNullWhenSourceSceneTileIsMissing()
    {
        tiles[0][55][50] = mock(Tile.class);

        assertNull(new Rs2WorldPoint(SOURCE).pathTo(TARGET));
        assertNull(new Rs2WorldPoint(SOURCE).pathTo(TARGET, true));
    }

    @Test
    public void pathToReturnsNullWhenTargetSceneTileIsMissing()
    {
        tiles[0][50][50] = mock(Tile.class);

        assertNull(new Rs2WorldPoint(SOURCE).pathTo(TARGET));
        assertNull(new Rs2WorldPoint(SOURCE).pathTo(TARGET, true));
    }
}
