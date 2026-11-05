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
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2WidgetUnloadedStaticChildrenTest {

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

    private static Widget unloadedGroupWidget() {
        Widget widget = mock(Widget.class);
        when(widget.getText()).thenReturn("");
        when(widget.getName()).thenReturn("");
        when(widget.getStaticChildren()).thenThrow(new NullPointerException());
        return widget;
    }

    private static Widget textWidget(String text) {
        Widget widget = mock(Widget.class);
        when(widget.getText()).thenReturn(text);
        when(widget.getName()).thenReturn("");
        return widget;
    }

    @Test
    public void findWidgetByTextReturnsNullWhenStaticChildrenThrow() {
        assertNull(Rs2Widget.findWidget("Make", Collections.singletonList(unloadedGroupWidget()), false));
    }

    @Test
    public void findWidgetByTextSearchesSiblingAfterUnloadedGroup() {
        Widget match = textWidget("Make");
        assertSame(match, Rs2Widget.findWidget("Make", Arrays.asList(unloadedGroupWidget(), match), true));
    }

    @Test
    public void searchChildrenByTextStillDescendsDynamicChildren() {
        Widget parent = unloadedGroupWidget();
        Widget match = textWidget("Make");
        when(parent.getDynamicChildren()).thenReturn(new Widget[]{match});
        assertSame(match, Rs2Widget.searchChildren("make", parent, false));
    }

    @Test
    public void findWidgetBySpriteReturnsNullWhenStaticChildrenThrow() {
        Widget widget = unloadedGroupWidget();
        when(widget.getSpriteId()).thenReturn(1);
        assertNull(Rs2Widget.findWidget(2, Collections.singletonList(widget)));
    }

    @Test
    public void searchChildrenBySpriteStillDescendsDynamicChildren() {
        Widget parent = unloadedGroupWidget();
        when(parent.getSpriteId()).thenReturn(1);
        Widget match = mock(Widget.class);
        when(match.getSpriteId()).thenReturn(2);
        when(parent.getDynamicChildren()).thenReturn(new Widget[]{match});
        assertSame(match, Rs2Widget.searchChildren(2, parent));
    }

    @Test
    public void findWidgetsWithActionReturnsEmptyWhenStaticChildrenThrow() {
        Widget parent = unloadedGroupWidget();
        when(client.getWidget(anyInt(), anyInt())).thenReturn(parent);
        assertTrue(Rs2Widget.findWidgetsWithAction("Make", 270, 13, false).isEmpty());
    }
}
