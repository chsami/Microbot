package net.runelite.client.plugins.microbot.api.tileobject.models;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Player;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.when;

public class Rs2TileObjectModelClickRecoveryTest {

    private static final int OBJECT_ID = 1530;
    private static final WorldPoint DOOR = new WorldPoint(3200, 3200, 0);

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Walker> walker;
    private MockedStatic<Rs2Camera> camera;
    private Client client;
    private TileObject object;
    private Player player;
    private boolean savedDetection;
    private boolean savedCantReach;
    private int savedRetries;

    private static LocalPoint scene(int x, int y) {
        return new LocalPoint(x * 128 + 64, y * 128 + 64, -1);
    }

    @Before
    public void setUp() {
        savedDetection = Microbot.isCantReachTargetDetectionEnabled;
        savedCantReach = Microbot.cantReachTarget;
        savedRetries = Microbot.cantReachTargetRetries;
        Microbot.isCantReachTargetDetectionEnabled = true;
        Microbot.cantReachTarget = false;
        Microbot.cantReachTargetRetries = 0;

        client = mock(Client.class);
        ObjectComposition composition = mock(ObjectComposition.class);
        when(composition.getActions()).thenReturn(new String[]{"Open", null, null, null, null});
        when(composition.getName()).thenReturn("Door");
        when(client.getObjectDefinition(OBJECT_ID)).thenReturn(composition);
        player = mock(Player.class);
        when(player.getLocalLocation()).thenReturn(scene(50, 50));
        when(client.getLocalPlayer()).thenReturn(player);

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
        when(object.getWorldLocation()).thenReturn(DOOR);
        when(object.getLocalLocation()).thenReturn(scene(52, 50));
        when(object.getWorldView()).thenReturn(mock(WorldView.class));
    }

    @After
    public void tearDown() {
        camera.close();
        walker.close();
        microbot.close();
        Microbot.isCantReachTargetDetectionEnabled = savedDetection;
        Microbot.cantReachTarget = savedCantReach;
        Microbot.cantReachTargetRetries = savedRetries;
    }

    @Test
    public void clicksWithoutWalkingWhenNothingFailed() {
        assertTrue(new Rs2TileObjectModel(object).click("Open"));

        walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), times(1));
    }

    @Test
    public void walksBesideTheObjectThenClicksAfterCantReach() {
        Microbot.cantReachTarget = true;
        walker.when(() -> Rs2Walker.walkTo(eq(DOOR), eq(2))).thenReturn(true);

        assertTrue(new Rs2TileObjectModel(object).click("Open"));

        walker.verify(() -> Rs2Walker.walkTo(eq(DOOR), eq(2)), times(1));
        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), times(1));
        assertFalse(Microbot.cantReachTarget);
        assertEquals(0, Microbot.cantReachTargetRetries);
    }

    @Test
    public void doesNotClickWhenTheRecoveryWalkFails() {
        Microbot.cantReachTarget = true;
        walker.when(() -> Rs2Walker.walkTo(eq(DOOR), eq(2))).thenReturn(false);

        assertFalse(new Rs2TileObjectModel(object).click("Open"));

        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
        assertTrue(Microbot.cantReachTarget);
        assertEquals(1, Microbot.cantReachTargetRetries);
    }

    @Test
    public void pausesScriptsOnceRetriesAreExhausted() {
        Microbot.cantReachTarget = true;
        Microbot.cantReachTargetRetries = 5;
        boolean savedPause = Microbot.pauseAllScripts.get();
        try {
            assertFalse(new Rs2TileObjectModel(object).click("Open"));

            assertTrue(Microbot.pauseAllScripts.get());
            walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
            microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
            microbot.verify(() -> Microbot.showMessage(contains("for 5 times")), times(1));
        } finally {
            Microbot.pauseAllScripts.set(savedPause);
        }
    }

    @Test
    public void exhaustedFailsafeDoesNotLatchForLaterClicks() {
        Microbot.cantReachTarget = true;
        Microbot.cantReachTargetRetries = 5;
        boolean savedPause = Microbot.pauseAllScripts.get();
        try {
            assertFalse(new Rs2TileObjectModel(object).click("Open"));

            assertFalse(Microbot.cantReachTarget);
            assertEquals(0, Microbot.cantReachTargetRetries);

            Microbot.pauseAllScripts.set(false);
            assertTrue(new Rs2TileObjectModel(object).click("Open"));

            microbot.verify(() -> Microbot.showMessage(any(String.class)), times(1));
            microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), times(1));
            assertFalse(Microbot.pauseAllScripts.get());
        } finally {
            Microbot.pauseAllScripts.set(savedPause);
        }
    }

    @Test
    public void walksTowardObjectsOutOfInteractRange() {
        when(player.getLocalLocation()).thenReturn(scene(0, 0));
        when(object.getLocalLocation()).thenReturn(scene(60, 0));

        assertFalse(new Rs2TileObjectModel(object).click("Open"));

        walker.verify(() -> Rs2Walker.walkTo(DOOR), times(1));
        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
    }
}
