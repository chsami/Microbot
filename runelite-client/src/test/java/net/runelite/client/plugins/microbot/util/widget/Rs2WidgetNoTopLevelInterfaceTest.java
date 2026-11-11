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

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2WidgetNoTopLevelInterfaceTest {

    private MockedStatic<Microbot> microbot;
    private Client client;

    @Before
    public void setUp() throws Exception {
        client = mock(Client.class);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv ->
                Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()));
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    @Test
    public void findWidgetByTextReturnsNullWhenWidgetRootsThrows() {
        when(client.getWidgetRoots()).thenThrow(new NullPointerException());
        assertNull(Rs2Widget.findWidget("Play", null, false));
        assertFalse(Rs2Widget.hasWidget("Play"));
    }

    @Test
    public void findWidgetBySpriteReturnsNullWhenWidgetRootsThrows() {
        when(client.getWidgetRoots()).thenThrow(new NullPointerException());
        assertNull(Rs2Widget.findWidget(1234, null));
    }

    @Test
    public void findWidgetReturnsNullWhenWidgetRootsIsNull() {
        when(client.getWidgetRoots()).thenReturn(null);
        assertNull(Rs2Widget.findWidget("Play", null, false));
        assertNull(Rs2Widget.findWidget(1234, null));
    }

    @Test
    public void inspectorReturnsEmptyWhenWidgetRootsThrows() {
        when(client.getWidgetRoots()).thenThrow(new NullPointerException());
        assertTrue(Rs2WidgetInspector.getVisibleInterfaces().isEmpty());
        assertTrue(Rs2WidgetInspector.search("play").isEmpty());
    }

    @Test
    public void findWidgetStillSearchesRootsWhenPresent() {
        Widget root = mock(Widget.class);
        when(root.isHidden()).thenReturn(false);
        when(root.getText()).thenReturn("Play");
        when(root.getName()).thenReturn("");
        when(client.getWidgetRoots()).thenReturn(new Widget[]{root});
        assertSame(root, Rs2Widget.findWidget("Play", null, true));
    }
}
