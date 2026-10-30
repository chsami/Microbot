package net.runelite.client.plugins.microbot;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetModalMode;
import net.runelite.client.callback.ClientThread;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class MicrobotOpenPopUpTest {

    private Client client;
    private Object previousClient;
    private Object previousClientThread;

    @Before
    public void setUp() throws Exception {
        client = mock(Client.class);
        ClientThread clientThread = mock(ClientThread.class);
        doAnswer(inv -> {
            ((Runnable) inv.getArgument(0)).run();
            return null;
        }).when(clientThread).invoke(any(Runnable.class));
        previousClient = swapStatic("client", client);
        previousClientThread = swapStatic("clientThread", clientThread);
    }

    @After
    public void tearDown() throws Exception {
        swapStatic("client", previousClient);
        swapStatic("clientThread", previousClientThread);
    }

    @Test
    public void popUpContainerMatchesEachTopLevel() {
        assertEquals(InterfaceID.Toplevel.NOTIFICATIONS, Microbot.popUpContainer(InterfaceID.TOPLEVEL));
        assertEquals(InterfaceID.ToplevelOsrsStretch.NOTIFICATIONS, Microbot.popUpContainer(InterfaceID.TOPLEVEL_OSRS_STRETCH));
        assertEquals(InterfaceID.ToplevelPreEoc.NOTIFICATIONS, Microbot.popUpContainer(InterfaceID.TOPLEVEL_PRE_EOC));
        assertEquals(-1, Microbot.popUpContainer(-1));
    }

    @Test
    public void opensOnFixedModeContainer() {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL);
        when(client.getWidget(InterfaceID.Toplevel.NOTIFICATIONS)).thenReturn(mock(Widget.class));

        Microbot.openPopUp("Microbot", "Enabled");

        verify(client).openInterface(InterfaceID.Toplevel.NOTIFICATIONS, InterfaceID.NOTIFICATION_DISPLAY, WidgetModalMode.MODAL_CLICKTHROUGH);
        verify(client).runScript(any(Object[].class));
    }

    @Test
    public void skipsWhenNotLoggedIn() {
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_OSRS_STRETCH);

        Microbot.openPopUp("Microbot", "Enabled");

        verify(client, never()).openInterface(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void skipsUnknownTopLevel() {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getTopLevelInterfaceId()).thenReturn(-1);

        Microbot.openPopUp("Microbot", "Enabled");

        verify(client, never()).openInterface(anyInt(), anyInt(), anyInt());
    }

    @Test
    public void swallowsMissingComponent() {
        when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
        when(client.getTopLevelInterfaceId()).thenReturn(InterfaceID.TOPLEVEL_PRE_EOC);
        when(client.getWidget(InterfaceID.ToplevelPreEoc.NOTIFICATIONS)).thenReturn(mock(Widget.class));
        when(client.openInterface(anyInt(), anyInt(), anyInt())).thenThrow(new IllegalStateException("component does not exist"));

        Microbot.openPopUp("Microbot", "Enabled");

        verify(client, never()).runScript(any(Object[].class));
    }

    private static Object swapStatic(String name, Object value) throws Exception {
        Field field = Microbot.class.getDeclaredField(name);
        field.setAccessible(true);
        Object previous = field.get(null);
        field.set(null, value);
        return previous;
    }
}
