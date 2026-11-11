package net.runelite.client.plugins.microbot.api.actor;

import java.util.function.Supplier;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Projection;
import net.runelite.api.WorldView;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
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
import static org.mockito.Mockito.when;

public class Rs2ActorModelMainWorldProjectionTest {
    private static final WorldPoint ACTOR_LOCATION = new WorldPoint(3010, 3020, 0);

    private MockedStatic<Microbot> microbot;
    private Client client;
    private Actor actor;
    private WorldView boatWorldView;
    private Rs2ActorModel model;

    @Before
    @SuppressWarnings("unchecked")
    public void setUp() {
        client = mock(Client.class);
        actor = mock(Actor.class);
        boatWorldView = mock(WorldView.class);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.invoke(any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<Object>) invocation.getArgument(0)).get());

        when(actor.getWorldView()).thenReturn(boatWorldView);
        when(actor.getWorldLocation()).thenReturn(ACTOR_LOCATION);
        when(boatWorldView.isTopLevel()).thenReturn(false);
        when(boatWorldView.getPlane()).thenReturn(0);
        when(boatWorldView.getBaseX()).thenReturn(3000);
        when(boatWorldView.getBaseY()).thenReturn(3000);
        when(boatWorldView.getSizeX()).thenReturn(104);
        when(boatWorldView.getSizeY()).thenReturn(104);

        microbot = mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);

        model = new Rs2ActorModel(actor);
    }

    @After
    public void tearDown() {
        microbot.close();
    }

    @Test
    public void worldLocationFallsBackToActorLocationWhenProjectionLookupThrowsForGoneWorldView() {
        when(boatWorldView.getMainWorldProjection()).thenThrow(new NullPointerException());

        assertEquals(ACTOR_LOCATION, model.getWorldLocation());
    }

    @Test
    public void worldLocationFallsBackToActorLocationWhenProjectionHasNoResult() {
        Projection projection = mock(Projection.class);
        when(projection.project(anyFloat(), anyFloat(), anyFloat())).thenReturn(null);
        when(boatWorldView.getMainWorldProjection()).thenReturn(projection);

        assertEquals(ACTOR_LOCATION, model.getWorldLocation());
    }

    @Test
    public void worldLocationIsProjectedIntoTopLevelWorldView() {
        WorldView topLevel = mock(WorldView.class);
        when(topLevel.getBaseX()).thenReturn(2000);
        when(topLevel.getBaseY()).thenReturn(2500);
        when(client.getTopLevelWorldView()).thenReturn(topLevel);
        Projection projection = mock(Projection.class);
        when(projection.project(anyFloat(), anyFloat(), anyFloat()))
                .thenReturn(new float[]{5 * 128 + 64, 0, 7 * 128 + 64});
        when(boatWorldView.getMainWorldProjection()).thenReturn(projection);

        assertEquals(new WorldPoint(2005, 2507, 0), model.getWorldLocation());
    }

    @Test
    public void projectToMainWorldReturnsNullWithoutWorldViewOrPoint() {
        assertNull(Rs2ActorModel.projectToMainWorld(null, ACTOR_LOCATION));
        assertNull(Rs2ActorModel.projectToMainWorld(boatWorldView, null));
    }

    @Test
    public void projectToMainWorldReturnsNullWhenProjectionLookupThrows() {
        when(boatWorldView.getMainWorldProjection()).thenThrow(new NullPointerException());

        assertNull(Rs2ActorModel.projectToMainWorld(boatWorldView, ACTOR_LOCATION));
    }
}
