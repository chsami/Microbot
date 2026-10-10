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

import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2WidgetNullRootTest {

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
    public void clickWidgetReturnsFalseWhenRootWidgetNotLoaded() {
        when(client.getWidget(270, 13)).thenReturn(null);
        assertFalse(Rs2Widget.clickWidget("Make", Optional.of(270), 13, false));
        microbot.verify(Microbot::getMouse, Mockito.never());
    }

    @Test
    public void findWidgetSkipsNullEntriesInChildren() {
        Widget match = mock(Widget.class);
        when(match.getText()).thenReturn("Make");
        when(match.getName()).thenReturn("");
        assertSame(match, Rs2Widget.findWidget("Make", Arrays.asList(null, match), true));
        assertNull(Rs2Widget.findWidget("Make", Arrays.asList((Widget) null), true));
    }

    @Test
    public void searchChildrenReturnsNullForNullWidget() {
        assertNull(Rs2Widget.searchChildren("Make", null, false));
        assertNull(Rs2Widget.searchChildren(1234, null));
    }
}
