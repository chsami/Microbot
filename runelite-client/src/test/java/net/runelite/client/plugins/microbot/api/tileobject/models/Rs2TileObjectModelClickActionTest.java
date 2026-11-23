package net.runelite.client.plugins.microbot.api.tileobject.models;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
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
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class Rs2TileObjectModelClickActionTest {

    private static final int OBJECT_ID = 16671;
    private static final WorldPoint STAIRS = new WorldPoint(3205, 3208, 0);

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Walker> walker;
    private MockedStatic<Rs2Camera> camera;
    private Client client;
    private ObjectComposition composition;
    private TileObject object;
    private boolean savedCantReach;
    private boolean savedDetection;

    private static LocalPoint scene(int x, int y) {
        return new LocalPoint(x * 128 + 64, y * 128 + 64, -1);
    }

    @Before
    public void setUp() {
        savedCantReach = Microbot.cantReachTarget;
        savedDetection = Microbot.isCantReachTargetDetectionEnabled;
        Microbot.cantReachTarget = false;
        Microbot.isCantReachTargetDetectionEnabled = true;

        client = mock(Client.class);
        composition = mock(ObjectComposition.class);
        when(composition.getActions()).thenReturn(new String[]{"Climb-up", "<col=ff9040>Climb-down</col>", null, null, null});
        when(composition.getName()).thenReturn("Staircase");
        when(client.getObjectDefinition(OBJECT_ID)).thenReturn(composition);
        Player player = mock(Player.class);
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
        when(object.getWorldLocation()).thenReturn(STAIRS);
        when(object.getLocalLocation()).thenReturn(scene(52, 50));
        when(object.getWorldView()).thenReturn(mock(WorldView.class));
    }

    @After
    public void tearDown() {
        camera.close();
        walker.close();
        microbot.close();
        Microbot.cantReachTarget = savedCantReach;
        Microbot.isCantReachTargetDetectionEnabled = savedDetection;
    }

    private NewMenuEntry sentEntry() {
        ArgumentCaptor<NewMenuEntry> entry = ArgumentCaptor.forClass(NewMenuEntry.class);
        microbot.verify(() -> Microbot.doInvoke(entry.capture(), any(Rectangle.class)));
        return entry.getValue();
    }

    @Test
    public void sendsTheMatchingOption() {
        assertTrue(new Rs2TileObjectModel(object).click("climb-down"));

        assertEquals(MenuAction.GAME_OBJECT_SECOND_OPTION, sentEntry().getType());
    }

    @Test
    public void refusesAnActionTheObjectDoesNotHave() {
        assertFalse(new Rs2TileObjectModel(object).click("Bottom-floor"));

        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
    }

    @Test
    public void refusesBeforeRecoveringOrWalking() {
        Microbot.cantReachTarget = true;

        assertFalse(new Rs2TileObjectModel(object).click("Bottom-floor"));

        walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class), anyInt()), never());
        walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class)), never());
        camera.verify(() -> Rs2Camera.turnTo(any(TileObject.class)), never());
        assertTrue(Microbot.cantReachTarget);
    }

    @Test
    public void ignoresColourTagsInTheRequestedAction() {
        assertTrue(new Rs2TileObjectModel(object).click("<col=ff9040>Climb-down</col>"));

        assertEquals(MenuAction.GAME_OBJECT_SECOND_OPTION, sentEntry().getType());
    }

    @Test
    public void matchesTheVisibleImpostorsActions() {
        ObjectComposition impostor = mock(ObjectComposition.class);
        when(impostor.getActions()).thenReturn(new String[]{"Inspect", null, "Take", null, null});
        when(impostor.getName()).thenReturn("Bar dispenser");
        when(composition.getImpostorIds()).thenReturn(new int[]{1, 2});
        when(composition.getImpostor()).thenReturn(impostor);

        assertFalse(new Rs2TileObjectModel(object).click("Climb-up"));
        assertTrue(new Rs2TileObjectModel(object).click("Take"));

        assertEquals(MenuAction.GAME_OBJECT_THIRD_OPTION, sentEntry().getType());
    }

    @Test
    public void sendsTheFirstOptionForANullOrBlankAction() {
        assertTrue(new Rs2TileObjectModel(object).click((String) null));
        assertTrue(new Rs2TileObjectModel(object).click("  "));

        ArgumentCaptor<NewMenuEntry> entries = ArgumentCaptor.forClass(NewMenuEntry.class);
        microbot.verify(() -> Microbot.doInvoke(entries.capture(), any(Rectangle.class)), Mockito.times(2));
        entries.getAllValues().forEach(e -> assertEquals(MenuAction.GAME_OBJECT_FIRST_OPTION, e.getType()));
    }

    @Test
    public void sendsTheFirstOptionWhenNoActionIsGiven() {
        assertTrue(new Rs2TileObjectModel(object).click());

        assertEquals(MenuAction.GAME_OBJECT_FIRST_OPTION, sentEntry().getType());
    }

    @Test
    public void usesTheSelectedWidgetOnTheObject() {
        when(client.isWidgetSelected()).thenReturn(true);

        assertTrue(new Rs2TileObjectModel(object).click("Use"));

        assertEquals(MenuAction.WIDGET_TARGET_ON_GAME_OBJECT, sentEntry().getType());
    }
}
