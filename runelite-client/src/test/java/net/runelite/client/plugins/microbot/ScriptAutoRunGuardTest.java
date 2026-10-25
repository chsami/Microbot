package net.runelite.client.plugins.microbot;

import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Tests for the auto-run guard in {@link Script#isBlockingInterfaceOpen()}. */
public class ScriptAutoRunGuardTest {
    private Client client;
    private ClientThread clientThread;

    /** Mocks the client and a client thread that runs each callable inline. */
    @Before public void setUp() {
        client = mock(Client.class);
        clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv ->
                Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()));
    }

    /** With no interface widgets present, auto-run may click. */
    @Test public void noInterfaceOpenAllowsAutoRun() {
        assertFalse(check());
    }

    /** A visible root of each bank, shop, GE, skilling and chat interface blocks auto-run. */
    @Test public void eachBlockingInterfaceStopsAutoRun() {
        int[] groups = {
                InterfaceID.BANKMAIN, InterfaceID.BANK_DEPOSITBOX, InterfaceID.BANKPIN_KEYPAD,
                InterfaceID.SHOPMAIN, InterfaceID.GE_OFFERS, InterfaceID.SKILLMULTI, InterfaceID.SMITHING,
                InterfaceID.CHAT_LEFT, InterfaceID.CHAT_RIGHT, InterfaceID.CHATMENU, InterfaceID.OBJECTBOX
        };
        for (int group : groups) {
            reset(client);
            showRoot(group, false);
            assertTrue("group " + group, check());
        }
    }

    /** A loaded but hidden interface root doesn't block auto-run. */
    @Test public void hiddenInterfaceRootDoesNotStopAutoRun() {
        showRoot(InterfaceID.BANKMAIN, true);
        assertFalse(check());
    }

    /** If the client thread returns nothing, the guard treats an interface as open and skips the click. */
    @Test public void unanswerableClientThreadSkipsAutoRun() {
        doReturn(Optional.empty()).when(clientThread).runOnClientThreadOptional(any());
        assertTrue(check());
    }

    /** Makes the client return a root widget for {@code group}, hidden or visible. */
    private void showRoot(int group, boolean hidden) {
        Widget root = mock(Widget.class);
        when(root.isHidden()).thenReturn(hidden);
        when(client.getWidget(eq(group), anyInt())).thenReturn(root);
    }

    /** Runs the guard with {@link Microbot}'s static client and client thread pointed at the mocks. */
    private boolean check() {
        try (MockedStatic<Microbot> microbot = mockStatic(Microbot.class)) {
            microbot.when(Microbot::getClient).thenReturn(client);
            microbot.when(Microbot::getClientThread).thenReturn(clientThread);
            return Script.isBlockingInterfaceOpen();
        }
    }
}
