package net.runelite.client.plugins.microbot.util.tile;

import net.runelite.api.Client;
import net.runelite.api.Tile;
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

public class Rs2TileNullPathEndpointTest {

    private MockedStatic<Microbot> microbot;

    @Before
    public void setUp() {
        Client client = mock(Client.class);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.invoke(any(Supplier.class))).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    @Test
    public void pathToReturnsNullForMissingEndpoint() {
        Tile tile = mock(Tile.class);

        assertNull(Rs2Tile.pathTo(null, tile));
        assertNull(Rs2Tile.pathTo(tile, null));
    }

    @Test
    public void fullPathToReturnsNullForMissingEndpoint() {
        Tile tile = mock(Tile.class);

        assertNull(Rs2Tile.fullPathTo(null, tile));
        assertNull(Rs2Tile.fullPathTo(tile, null));
    }
}
