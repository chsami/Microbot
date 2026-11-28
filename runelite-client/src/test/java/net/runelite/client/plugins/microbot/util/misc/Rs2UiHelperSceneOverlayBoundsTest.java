package net.runelite.client.plugins.microbot.util.misc;

import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Rs2UiHelperSceneOverlayBoundsTest {

    private static final Rectangle VIEWPORT = new Rectangle(0, 0, 1280, 800);
    private static final Rectangle CHATBOX = new Rectangle(0, 600, 520, 200);
    private static final Rectangle SIDE_PANEL = new Rectangle(1040, 460, 240, 340);
    private static final Rectangle MINIMAP = new Rectangle(1100, 0, 180, 170);

    private MockedStatic<Microbot> microbot;
    private Client client;

    @Before
    public void setUp() {
        client = mock(Client.class);
        when(client.getViewportWidth()).thenReturn(VIEWPORT.width);
        when(client.getViewportHeight()).thenReturn(VIEWPORT.height);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()));
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    @Test
    public void classicLayoutContainersStretchedToTheCanvasLeaveVisibleTargetsClickable() {
        Rectangle chatArea = new Rectangle(0, 638, 519, 162);
        Rectangle sideArea = new Rectangle(1039, 464, 241, 336);
        Widget hiddenChild = widget(VIEWPORT);
        when(hiddenChild.isHidden()).thenReturn(true);
        Widget chatChild = widget(chatArea);
        Widget chatRoot = widget(VIEWPORT);
        when(chatRoot.getStaticChildren()).thenReturn(new Widget[]{chatChild, hiddenChild});
        Widget chat = widget(VIEWPORT);
        when(chat.getNestedChildren()).thenReturn(new Widget[]{chatRoot});
        Widget sideChild = widget(sideArea);
        Widget side = widget(VIEWPORT);
        when(side.getStaticChildren()).thenReturn(new Widget[]{sideChild, null});
        Widget minimap = widget(MINIMAP);
        when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_PRE_EOC);
        when(client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_CONTAINER)).thenReturn(side);
        when(client.getWidget(InterfaceID.ToplevelPreEoc.CHAT_CONTAINER)).thenReturn(chat);
        when(client.getWidget(InterfaceID.ToplevelPreEoc.MAP_CONTAINER)).thenReturn(minimap);

        List<Rectangle> bounds = Rs2UiHelper.visibleSceneOverlayBounds();

        assertEquals(new HashSet<>(Arrays.asList(sideArea, chatArea, MINIMAP)), new HashSet<>(bounds));
        assertEquals(3, bounds.size());
        Rectangle fishingSpot = new Rectangle(600, 300, 60, 50);
        Rectangle depositBox = new Rectangle(400, 450, 70, 90);
        assertEquals(fishingSpot, Rs2UiHelper.clearClickArea(fishingSpot));
        assertEquals(depositBox, Rs2UiHelper.clearClickArea(depositBox));
        assertNull(Rs2UiHelper.clearClickArea(new Rectangle(100, 680, 60, 40)));
    }

    @Test
    public void aStretchedContainerWithoutVisibleChildrenCoversNothing() {
        Widget side = widget(new Rectangle(-10, -10, 1300, 820));
        when(client.getWidget(InterfaceID.ToplevelOsrsStretch.SIDE_MENU)).thenReturn(side);

        assertTrue(Rs2UiHelper.visibleSceneOverlayBounds().isEmpty());
    }

    @Test
    public void onlyTheActiveToplevelContainersAreRead() {
        Widget side = widget(SIDE_PANEL);
        Widget chat = widget(CHATBOX);
        Widget minimap = widget(MINIMAP);
        when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_OSRS_STRETCH);
        when(client.getWidget(InterfaceID.ToplevelOsrsStretch.SIDE_MENU)).thenReturn(side);
        when(client.getWidget(InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER)).thenReturn(chat);
        when(client.getWidget(InterfaceID.ToplevelOsrsStretch.MAP_CONTAINER)).thenReturn(minimap);

        assertEquals(Arrays.asList(SIDE_PANEL, CHATBOX, MINIMAP), Rs2UiHelper.visibleSceneOverlayBounds());
        verify(client, never()).getWidget(InterfaceID.ToplevelPreEoc.SIDE_CONTAINER);
        verify(client, never()).getWidget(InterfaceID.ToplevelPreEoc.CHAT_CONTAINER);
        verify(client, never()).getWidget(InterfaceID.ToplevelPreEoc.MAP_CONTAINER);
    }

    @Test
    public void classicLayoutContainersAreUsedAsTheyAreWhenNotStretched() {
        Widget side = widget(SIDE_PANEL);
        Widget chat = widget(CHATBOX);
        Widget minimap = widget(MINIMAP);
        when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_PRE_EOC);
        when(client.getWidget(InterfaceID.ToplevelPreEoc.SIDE_CONTAINER)).thenReturn(side);
        when(client.getWidget(InterfaceID.ToplevelPreEoc.CHAT_CONTAINER)).thenReturn(chat);
        when(client.getWidget(InterfaceID.ToplevelPreEoc.MAP_CONTAINER)).thenReturn(minimap);

        assertEquals(Arrays.asList(SIDE_PANEL, CHATBOX, MINIMAP), Rs2UiHelper.visibleSceneOverlayBounds());
        verify(client, never()).getWidget(InterfaceID.ToplevelOsrsStretch.SIDE_MENU);
    }

    private static Widget widget(Rectangle bounds) {
        Widget widget = mock(Widget.class);
        when(widget.getBounds()).thenReturn(new Rectangle(bounds));
        return widget;
    }
}
