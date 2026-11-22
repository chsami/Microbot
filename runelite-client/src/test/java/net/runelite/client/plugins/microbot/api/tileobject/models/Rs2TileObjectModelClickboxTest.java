package net.runelite.client.plugins.microbot.api.tileobject.models;

import java.awt.Rectangle;
import java.awt.Shape;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.TileObject;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Rs2TileObjectModelClickboxTest {

    private final AtomicBoolean onClientThread = new AtomicBoolean();
    private MockedStatic<Microbot> microbot;
    private ClientThread clientThread;
    private TileObject tileObject;
    private Shape clickbox;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any(Callable.class))).thenAnswer(inv -> {
            onClientThread.set(true);
            try {
                return Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call());
            } finally {
                onClientThread.set(false);
            }
        });
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);

        clickbox = new Rectangle(10, 20, 30, 40);
        tileObject = mock(TileObject.class);
        when(tileObject.getClickbox()).thenAnswer(inv -> {
            if (!onClientThread.get()) {
                throw new IllegalStateException("must be called on client thread");
            }
            return clickbox;
        });
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    @Test
    public void apiModelReadsClickboxOnClientThread() {
        assertSame(clickbox, new Rs2TileObjectModel(tileObject).getClickbox());
    }


    @Test
    @SuppressWarnings("unchecked")
    public void apiModelReturnsNullWhenClientThreadYieldsNothing() {
        when(clientThread.runOnClientThreadOptional(any(Callable.class))).thenReturn(Optional.empty());

        assertNull(new Rs2TileObjectModel(tileObject).getClickbox());
        verify(tileObject, never()).getClickbox();
    }
}
