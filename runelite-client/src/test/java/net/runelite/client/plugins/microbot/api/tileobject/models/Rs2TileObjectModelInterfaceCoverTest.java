package net.runelite.client.plugins.microbot.api.tileobject.models;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class Rs2TileObjectModelInterfaceCoverTest {

    private static final int OBJECT_ID = 16671;
    private static final WorldPoint STAIRS = new WorldPoint(3205, 3208, 0);
    private static final Rectangle CHATBOX = new Rectangle(0, 600, 520, 200);

    private final AtomicReference<Rectangle> clickbox = new AtomicReference<>();
    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Walker> walker;
    private MockedStatic<Rs2Camera> camera;
    private boolean savedCantReach;
    private boolean savedDetection;
    private TileObject object;

    private static LocalPoint scene(int x, int y) {
        return new LocalPoint(x * 128 + 64, y * 128 + 64, -1);
    }

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        savedCantReach = Microbot.cantReachTarget;
        savedDetection = Microbot.isCantReachTargetDetectionEnabled;
        Microbot.cantReachTarget = false;
        Microbot.isCantReachTargetDetectionEnabled = true;

        Client client = mock(Client.class);
        ObjectComposition composition = mock(ObjectComposition.class);
        when(composition.getActions()).thenReturn(new String[]{"Climb-down", null, null, null, null});
        when(composition.getName()).thenReturn("Staircase");
        when(client.getObjectDefinition(OBJECT_ID)).thenReturn(composition);
        Player player = mock(Player.class);
        when(player.getLocalLocation()).thenReturn(scene(50, 50));
        when(client.getLocalPlayer()).thenReturn(player);
        when(client.getViewportWidth()).thenReturn(1280);
        when(client.getViewportHeight()).thenReturn(800);
        Widget chat = mock(Widget.class);
        when(chat.getBounds()).thenReturn(CHATBOX);
        when(client.getWidget(InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER)).thenReturn(chat);

        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.invoke(any(Supplier.class))).thenAnswer(inv -> ((Supplier<?>) inv.getArgument(0)).get());
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()));

        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        walker = Mockito.mockStatic(Rs2Walker.class);
        camera = Mockito.mockStatic(Rs2Camera.class);
        camera.when(() -> Rs2Camera.isTileOnScreen(any(LocalPoint.class))).thenReturn(true);

        object = mock(TileObject.class);
        when(object.getId()).thenReturn(OBJECT_ID);
        when(object.getWorldLocation()).thenReturn(STAIRS);
        when(object.getLocalLocation()).thenReturn(scene(52, 50));
        when(object.getWorldView()).thenReturn(mock(WorldView.class));
        when(object.getClickbox()).thenAnswer(inv -> clickbox.get());
    }

    @After
    public void tearDown() {
        camera.close();
        walker.close();
        microbot.close();
        Microbot.cantReachTarget = savedCantReach;
        Microbot.isCantReachTargetDetectionEnabled = savedDetection;
    }

    private Rectangle clickedArea() {
        ArgumentCaptor<Rectangle> area = ArgumentCaptor.forClass(Rectangle.class);
        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), area.capture()));
        return area.getValue();
    }

    @Test
    public void clicksOnlyThePartOfTheObjectAboveTheChatbox() {
        clickbox.set(new Rectangle(200, 560, 100, 100));

        assertTrue(new Rs2TileObjectModel(object).click("Climb-down"));

        assertEquals(new Rectangle(200, 560, 100, 36), clickedArea());
        camera.verify(() -> Rs2Camera.turnTo(any(TileObject.class)), never());
    }

    @Test
    public void turnsTheCameraWhenTheObjectIsBehindTheChatbox() {
        clickbox.set(new Rectangle(100, 650, 60, 40));
        camera.when(() -> Rs2Camera.turnTo(any(TileObject.class)))
                .thenAnswer(inv -> {
                    clickbox.set(new Rectangle(600, 300, 60, 40));
                    return null;
                });

        assertTrue(new Rs2TileObjectModel(object).click("Climb-down"));

        assertEquals(new Rectangle(600, 300, 60, 40), clickedArea());
    }

    @Test
    public void doesNotClickTheChatboxWhenTheObjectStaysBehindIt() {
        clickbox.set(new Rectangle(100, 650, 60, 40));

        assertFalse(new Rs2TileObjectModel(object).click("Climb-down"));

        camera.verify(() -> Rs2Camera.turnTo(any(TileObject.class)));
        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
    }

    @Test
    public void turnsTheCameraOnlyOnceWhenTheTileWasOffScreen() {
        clickbox.set(new Rectangle(100, 650, 60, 40));
        camera.when(() -> Rs2Camera.isTileOnScreen(any(LocalPoint.class))).thenReturn(false);

        assertFalse(new Rs2TileObjectModel(object).click("Climb-down"));

        camera.verify(() -> Rs2Camera.turnTo(any(TileObject.class)), times(1));
        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
    }

    @Test
    public void aClickboxOverTheTopLeftCornerIsClickedAsARealArea() {
        clickbox.set(new Rectangle(-50, -40, 200, 200));

        assertTrue(new Rs2TileObjectModel(object).click("Climb-down"));

        Rectangle area = clickedArea();
        assertEquals(new Rectangle(2, 2, 148, 158), area);
        assertFalse(Rs2UiHelper.isFallbackRectangle(area));
    }

    @Test
    public void theCoveredWarningIsRateLimitedPerObject() {
        long start = 1_000_000L;

        assertTrue(Rs2TileObjectModel.shouldWarnCovered(-77, start));
        assertFalse(Rs2TileObjectModel.shouldWarnCovered(-77, start + 1_000));
        assertTrue(Rs2TileObjectModel.shouldWarnCovered(-78, start + 2_000));
        assertTrue(Rs2TileObjectModel.shouldWarnCovered(-77, start + 3_000));
        assertFalse(Rs2TileObjectModel.shouldWarnCovered(-77, start + 4_000));
        assertTrue(Rs2TileObjectModel.shouldWarnCovered(-77,
                start + 3_000 + Rs2TileObjectModel.COVERED_WARN_INTERVAL_MS));
    }

    @Test
    public void anUnrenderedObjectStillUsesTheFallbackClick() {
        assertTrue(new Rs2TileObjectModel(object).click("Climb-down"));

        clickedArea();
        camera.verify(() -> Rs2Camera.turnTo(any(TileObject.class)), never());
    }
}
