package net.runelite.client.plugins.microbot;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Actor;
import net.runelite.api.Point;
import net.runelite.api.TileObject;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.api.actor.Rs2ActorModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.ActorModel;
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

public class ModelMinimapLocationClientThreadTest {

    private final AtomicBoolean onClientThread = new AtomicBoolean();
    private final Point minimapLocation = new Point(612, 84);
    private MockedStatic<Microbot> microbot;
    private ClientThread clientThread;
    private TileObject tileObject;
    private Actor actor;

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

        tileObject = mock(TileObject.class);
        when(tileObject.getMinimapLocation()).thenAnswer(inv -> requireClientThread());
        actor = mock(Actor.class);
        when(actor.getMinimapLocation()).thenAnswer(inv -> requireClientThread());
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    private Point requireClientThread() {
        if (!onClientThread.get()) {
            throw new IllegalStateException("must be called on client thread");
        }
        return minimapLocation;
    }

    @Test
    public void apiTileObjectModelReadsMinimapLocationOnClientThread() {
        assertSame(minimapLocation, new Rs2TileObjectModel(tileObject).getMinimapLocation());
    }

    @Test
    public void legacyTileObjectModelReadsMinimapLocationOnClientThread() {
        assertSame(minimapLocation, new net.runelite.client.plugins.microbot.util.tileobject.Rs2TileObjectModel(tileObject).getMinimapLocation());
    }

    @Test
    public void apiActorModelReadsMinimapLocationOnClientThread() {
        assertSame(minimapLocation, new Rs2ActorModel(actor).getMinimapLocation());
    }

    @Test
    public void legacyActorModelReadsMinimapLocationOnClientThread() {
        assertSame(minimapLocation, new ActorModel(actor).getMinimapLocation());
    }

    @Test
    @SuppressWarnings("unchecked")
    public void returnsNullWhenClientThreadYieldsNothing() {
        when(clientThread.runOnClientThreadOptional(any(Callable.class))).thenReturn(Optional.empty());

        assertNull(new Rs2TileObjectModel(tileObject).getMinimapLocation());
        assertNull(new Rs2ActorModel(actor).getMinimapLocation());
        verify(tileObject, never()).getMinimapLocation();
        verify(actor, never()).getMinimapLocation();
    }
}
