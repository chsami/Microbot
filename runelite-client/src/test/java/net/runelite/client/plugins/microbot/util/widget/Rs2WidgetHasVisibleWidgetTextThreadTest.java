package net.runelite.client.plugins.microbot.util.widget;

import net.runelite.api.Client;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2WidgetHasVisibleWidgetTextThreadTest {

    private MockedStatic<Microbot> microbot;
    private final AtomicBoolean onClientThread = new AtomicBoolean(false);
    private ClientThread clientThread;
    private Client client;

    @Before
    public void setUp() {
        client = mock(Client.class);
        clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
            Callable<?> callable = inv.getArgument(0);
            boolean wasOnClientThread = onClientThread.getAndSet(true);
            try {
                return Optional.ofNullable(callable.call());
            } finally {
                onClientThread.set(wasOnClientThread);
            }
        });
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    private Widget rootWithText(String text) {
        Widget root = mock(Widget.class);
        when(root.getText()).thenReturn(text);
        when(root.getName()).thenReturn("");
        when(root.isHidden()).thenAnswer(inv -> {
            if (!onClientThread.get()) {
                throw new IllegalStateException("must be called on client thread");
            }
            return false;
        });
        return root;
    }

    @Test
    public void checksVisibilityOnClientThreadWhenCalledFromScriptThread() {
        Widget root = rootWithText("Make");
        when(client.getWidgetRoots()).thenReturn(new Widget[]{root});

        assertTrue(Rs2Widget.hasVisibleWidgetText("Make"));
    }

    @Test
    public void returnsFalseWhenNoWidgetMatches() {
        Widget root = rootWithText("Bank");
        when(client.getWidgetRoots()).thenReturn(new Widget[]{root});

        assertFalse(Rs2Widget.hasVisibleWidgetText("Make"));
    }

    @Test
    public void returnsFalseWhenClientThreadReturnsNothing() {
        doReturn(Optional.empty()).when(clientThread).runOnClientThreadOptional(any());

        assertFalse(Rs2Widget.hasVisibleWidgetText("Make"));
    }
}
