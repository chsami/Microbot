package net.runelite.client.plugins.microbot.util.mouse;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.mouse.naturalmouse.NaturalMouse;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class VirtualMouseRetargetTest {

    private static final Rectangle CHATBOX = new Rectangle(0, 600, 520, 200);

    private MockedStatic<Microbot> microbot;
    private NaturalMouse savedNaturalMouse;
    private NaturalMouse naturalMouse;
    private Mouse mouse;

    @Before
    public void setUp() {
        Client client = mock(Client.class);
        when(client.getViewportWidth()).thenReturn(1280);
        when(client.getViewportHeight()).thenReturn(800);
        when(client.getCanvasWidth()).thenReturn(1280);
        when(client.getCanvasHeight()).thenReturn(800);
        Widget chat = mock(Widget.class);
        when(chat.getBounds()).thenReturn(CHATBOX);
        when(client.getWidget(InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER)).thenReturn(chat);

        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()));

        mouse = mock(Mouse.class);
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        microbot.when(Microbot::getMouse).thenReturn(mouse);

        savedNaturalMouse = Microbot.naturalMouse;
        naturalMouse = mock(NaturalMouse.class);
        Microbot.naturalMouse = naturalMouse;
    }

    @After
    public void tearDown() {
        Microbot.naturalMouse = savedNaturalMouse;
        microbot.close();
    }

    private void mouseAt(int x, int y) {
        when(mouse.getMousePosition()).thenReturn(new java.awt.Point(x, y));
        when(mouse.getLastClick()).thenReturn(new Point(x, y));
    }

    @Test
    public void aMovedObjectIsRetargetedAboveTheChatboxNotOntoIt() {
        mouseAt(250, 300);
        Point current = new Point(250, 300);
        Rectangle trapdoor = new Rectangle(200, 560, 100, 100);

        for (int i = 0; i < 200; i++) {
            Point target = VirtualMouse.retarget(trapdoor, current);

            assertFalse(CHATBOX.contains(target.getX(), target.getY()));
            assertTrue(target.getY() <= CHATBOX.y - 4);
            assertTrue(target.getX() >= trapdoor.x && target.getX() <= trapdoor.x + trapdoor.width);
            assertTrue(target.getY() >= trapdoor.y);
        }
        verify(naturalMouse, Mockito.atLeastOnce()).moveTo(anyInt(), anyInt());
    }

    @Test
    public void theMouseOverTheChatboxIsRetargetedEvenInsideTheClickbox() {
        mouseAt(250, 650);
        Point current = new Point(250, 650);

        Point target = VirtualMouse.retarget(new Rectangle(200, 560, 100, 100), current);

        assertFalse(CHATBOX.contains(target.getX(), target.getY()));
    }

    @Test
    public void keepsTheCurrentPointWhenTheObjectIsFullyBehindTheChatbox() {
        mouseAt(250, 300);
        Point current = new Point(250, 300);

        assertSame(current, VirtualMouse.retarget(new Rectangle(100, 650, 60, 40), current));
        verify(naturalMouse, never()).moveTo(anyInt(), anyInt());
    }

    @Test
    public void keepsTheCurrentPointWhenTheMouseIsAlreadyInTheClearArea() {
        mouseAt(250, 580);
        Point current = new Point(250, 580);

        assertSame(current, VirtualMouse.retarget(new Rectangle(200, 560, 100, 100), current));
        verify(naturalMouse, never()).moveTo(anyInt(), anyInt());
    }
}
