package net.runelite.client.plugins.microbot.api.boat;

import java.util.function.Supplier;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.Projection;
import net.runelite.api.WorldEntity;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.boat.data.Heading;
import net.runelite.client.plugins.microbot.api.boat.models.Rs2BoatModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class Rs2BoatModelPlayerBoatLocationTest {
    private static final WorldPoint PLAYER_LOCATION = new WorldPoint(3010, 3020, 0);

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2Player> rs2Player;
    private Client client;
    private Player player;
    private WorldView boatWorldView;
    private Rs2BoatModel boat;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        client = mock(Client.class);
        player = mock(Player.class);
        boatWorldView = mock(WorldView.class);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.invoke(any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());

        when(client.getLocalPlayer()).thenReturn(player);
        when(player.getWorldView()).thenReturn(boatWorldView);
        when(boatWorldView.isTopLevel()).thenReturn(false);
        when(boatWorldView.getPlane()).thenReturn(0);
        when(boatWorldView.getBaseX()).thenReturn(3000);
        when(boatWorldView.getBaseY()).thenReturn(3000);
        when(boatWorldView.getSizeX()).thenReturn(104);
        when(boatWorldView.getSizeY()).thenReturn(104);

        microbot = mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        rs2Player = mockStatic(Rs2Player.class);
        rs2Player.when(Rs2Player::getWorldLocation).thenReturn(PLAYER_LOCATION);

        boat = new Rs2BoatModel(mock(WorldEntity.class));
    }

    @After
    public void tearDown() {
        rs2Player.close();
        microbot.close();
    }

    @Test
    public void returnsNullWhenProjectionLookupThrowsForGoneBoatWorldView() {
        when(boatWorldView.getMainWorldProjection()).thenThrow(new NullPointerException());

        assertNull(boat.getPlayerBoatLocation());
    }

    @Test
    public void returnsNullWhenProjectingThrows() {
        Projection projection = mock(Projection.class);
        when(projection.project(anyFloat(), anyFloat(), anyFloat())).thenThrow(new NullPointerException());
        when(boatWorldView.getMainWorldProjection()).thenReturn(projection);

        assertNull(boat.getPlayerBoatLocation());
    }

    @Test
    public void returnsNullWhenProjectionIsMissing() {
        when(boatWorldView.getMainWorldProjection()).thenReturn(null);

        assertNull(boat.getPlayerBoatLocation());
    }

    @Test
    public void returnsPlayerLocationWhenPlayerWorldViewIsNull() {
        when(player.getWorldView()).thenReturn(null);

        assertEquals(PLAYER_LOCATION, boat.getPlayerBoatLocation());
    }

    @Test
    public void returnsPlayerLocationWithoutProjectingWhenWorldViewIsTopLevel() {
        when(boatWorldView.isTopLevel()).thenReturn(true);

        assertEquals(PLAYER_LOCATION, boat.getPlayerBoatLocation());
        verify(boatWorldView, never()).getMainWorldProjection();
    }

    @Test
    public void returnsNullWhenPlayerIsOutsideWorldViewScene() {
        when(boatWorldView.getPlane()).thenReturn(1);

        assertNull(boat.getPlayerBoatLocation());
        verify(boatWorldView, never()).getMainWorldProjection();
    }

    @Test
    public void returnsNullWhenPlayerLocationIsUnknown() {
        rs2Player.when(Rs2Player::getWorldLocation).thenReturn(null);

        assertNull(boat.getPlayerBoatLocation());
    }

    @Test
    public void returnsNullWhenLocalPlayerIsMissing() {
        when(client.getLocalPlayer()).thenReturn(null);

        assertNull(boat.getPlayerBoatLocation());
    }

    @Test
    public void projectsBoatLocationIntoTopLevelWorldView() {
        WorldView topLevel = mock(WorldView.class);
        when(topLevel.getBaseX()).thenReturn(2000);
        when(topLevel.getBaseY()).thenReturn(2500);
        when(client.getTopLevelWorldView()).thenReturn(topLevel);
        Projection projection = mock(Projection.class);
        when(projection.project(anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(new float[]{5 * 128 + 64, 0, 7 * 128 + 64});
        when(boatWorldView.getMainWorldProjection()).thenReturn(projection);

        assertEquals(new WorldPoint(2005, 2507, 0), boat.getPlayerBoatLocation());
    }

    @Test
    public void directionIsUnknownWhenBoatLocationCannotBeProjected() {
        when(boatWorldView.getMainWorldProjection()).thenThrow(new NullPointerException());

        assertEquals(-1, boat.getDirection(new WorldPoint(2100, 2600, 0)));
    }

    @Test
    public void directionUsesProjectedBoatLocation() {
        WorldView topLevel = mock(WorldView.class);
        when(topLevel.getBaseX()).thenReturn(2000);
        when(topLevel.getBaseY()).thenReturn(2500);
        when(client.getTopLevelWorldView()).thenReturn(topLevel);
        Projection projection = mock(Projection.class);
        when(projection.project(anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(new float[]{5 * 128 + 64, 0, 7 * 128 + 64});
        when(boatWorldView.getMainWorldProjection()).thenReturn(projection);

        assertEquals(Heading.NORTH.getValue(), boat.getDirection(new WorldPoint(2005, 2600, 0)));
    }
}
