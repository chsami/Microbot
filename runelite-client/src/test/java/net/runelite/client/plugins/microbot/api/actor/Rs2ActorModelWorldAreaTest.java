package net.runelite.client.plugins.microbot.api.actor;

import java.util.function.Supplier;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldArea;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Rs2ActorModelWorldAreaTest {

    private MockedStatic<Microbot> microbot;
    private ClientThread clientThread;
    private NPC actor;
    private boolean onClientThread;

    @Before
    public void setUp() {
        clientThread = mock(ClientThread.class);
        when(clientThread.invoke(any(Supplier.class))).thenAnswer(inv -> {
            onClientThread = true;
            try {
                return ((Supplier<?>) inv.getArgument(0)).get();
            } finally {
                onClientThread = false;
            }
        });
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        actor = mock(NPC.class);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    @Test
    public void worldAreaIsReadOnClientThread() {
        WorldArea area = new WorldArea(3200, 3200, 1, 1, 0);
        when(actor.getWorldArea()).thenAnswer(inv -> {
            if (!onClientThread) {
                throw new IllegalStateException("must be called on client thread");
            }
            return area;
        });

        assertEquals(area, new Rs2ActorModel(actor).getWorldArea());
        verify(clientThread).invoke(any(Supplier.class));
    }

    @Test
    public void nullActorReturnsNullWithoutClientThread() {
        assertNull(new Rs2ActorModel(null).getWorldArea());
        verify(clientThread, never()).invoke(any(Supplier.class));
    }
}
