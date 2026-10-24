package net.runelite.client.plugins.microbot.util.gameobject;

import java.awt.Rectangle;
import java.util.Optional;
import java.util.concurrent.Callable;
import net.runelite.api.Client;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.Assert.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class Rs2GameObjectClickObjectWorldViewTest {

    private static final int OBJECT_ID = 43700;
    private static final WorldPoint LOCATION = new WorldPoint(3200, 3200, 0);

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> player;
    private MockedStatic<Rs2Camera> camera;
    private boolean savedCantReachTarget;
    private Client client;
    private TileObject object;

    @Before
    public void setUp() {
        savedCantReachTarget = Microbot.cantReachTarget;
        Microbot.cantReachTarget = false;

        client = mock(Client.class);
        when(client.getLocalPlayer()).thenReturn(null);
        ObjectComposition composition = mock(ObjectComposition.class);
        when(composition.getActions()).thenReturn(new String[]{"Deposit", null, null, null, null});
        when(composition.getName()).thenReturn("Salvaging station");
        when(client.getObjectDefinition(OBJECT_ID)).thenReturn(composition);

        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call()));

        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        player = Mockito.mockStatic(Rs2Player.class);
        player.when(Rs2Player::getWorldLocation).thenReturn(LOCATION);
        camera = Mockito.mockStatic(Rs2Camera.class);
        camera.when(() -> Rs2Camera.isTileOnScreen(any(LocalPoint.class))).thenReturn(true);

        object = mock(TileObject.class);
        when(object.getId()).thenReturn(OBJECT_ID);
        when(object.getWorldLocation()).thenReturn(LOCATION);
        when(object.getLocalLocation()).thenReturn(new LocalPoint(6400, 6400, -1));
    }

    @After
    public void tearDown() {
        camera.close();
        player.close();
        microbot.close();
        Microbot.cantReachTarget = savedCantReachTarget;
    }

    @Test
    public void returnsFalseWithoutInvokingWhenWorldViewIsUnloaded() {
        when(object.getWorldView()).thenReturn(null);

        assertFalse(Rs2GameObject.clickObject(object, "Deposit"));
        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
    }

    @Test
    public void doesNotWalkTowardObjectWhoseWorldViewIsUnloaded() {
        when(object.getWorldView()).thenReturn(null);
        player.when(Rs2Player::getWorldLocation).thenReturn(new WorldPoint(3300, 3300, 0));
        when(object.getLocalLocation()).thenReturn(null);

        try (MockedStatic<Rs2Walker> walker = Mockito.mockStatic(Rs2Walker.class)) {
            assertFalse(Rs2GameObject.clickObject(object, "Deposit"));
            walker.verify(() -> Rs2Walker.walkTo(any(WorldPoint.class)), never());
        }
    }

    @Test
    public void returnsFalseWhenInteractionThrows() {
        when(object.getWorldView()).thenReturn(mock(WorldView.class));
        when(client.isWidgetSelected()).thenThrow(new IllegalStateException("boom"));

        assertFalse(Rs2GameObject.clickObject(object, "Deposit"));
        microbot.verify(() -> Microbot.doInvoke(any(NewMenuEntry.class), any(Rectangle.class)), never());
    }
}
