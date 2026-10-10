package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.Actor;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class Rs2WalkerInteractingActorThreadTest {

    private MockedStatic<Microbot> microbot;
    private MockedStatic<Rs2PathApi> pathApi;
    private MockedStatic<Rs2Player> player;
    private final AtomicBoolean onClientThread = new AtomicBoolean(false);
    private ClientThread clientThread;

    @Before
    public void setUp() {
        clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
            Callable<?> callable = inv.getArgument(0);
            onClientThread.set(true);
            try {
                return Optional.ofNullable(callable.call());
            } finally {
                onClientThread.set(false);
            }
        });
        microbot = Mockito.mockStatic(Microbot.class);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);

        Rs2ActiveRouteStatus status = mock(Rs2ActiveRouteStatus.class);
        when(status.isPresent()).thenReturn(true);
        when(status.getWalkablePath()).thenReturn(Arrays.asList(
                new WorldPoint(3200, 3200, 0),
                new WorldPoint(3201, 3200, 0),
                new WorldPoint(3202, 3200, 0)));
        pathApi = Mockito.mockStatic(Rs2PathApi.class);
        pathApi.when(Rs2PathApi::getActiveRouteStatus).thenReturn(status);

        player = Mockito.mockStatic(Rs2Player.class);
    }

    @After
    public void tearDown() {
        player.close();
        pathApi.close();
        microbot.close();
    }

    private Actor clientThreadOnlyActor(WorldPoint location) {
        Actor actor = mock(Actor.class);
        when(actor.getWorldLocation()).thenAnswer(inv -> {
            if (!onClientThread.get()) {
                throw new IllegalStateException("must be called on client thread");
            }
            return location;
        });
        return actor;
    }

    private static boolean invoke() throws Exception {
        Method method = Rs2Walker.class.getDeclaredMethod("interactingActorNearWalkablePath");
        method.setAccessible(true);
        return (boolean) method.invoke(null);
    }

    @Test
    public void readsInteractingActorLocationOnClientThread() throws Exception {
        Actor actor = clientThreadOnlyActor(new WorldPoint(3201, 3202, 0));
        player.when(Rs2Player::getInteracting).thenReturn(actor);
        assertTrue(invoke());
    }

    @Test
    public void actorFarFromPathIsNotNear() throws Exception {
        Actor actor = clientThreadOnlyActor(new WorldPoint(3220, 3220, 0));
        player.when(Rs2Player::getInteracting).thenReturn(actor);
        assertFalse(invoke());
    }

    @Test
    public void emptyClientThreadResultIsTreatedAsNotNear() throws Exception {
        Actor actor = clientThreadOnlyActor(new WorldPoint(3201, 3202, 0));
        player.when(Rs2Player::getInteracting).thenReturn(actor);
        Mockito.doReturn(Optional.empty()).when(clientThread).runOnClientThreadOptional(any());
        assertFalse(invoke());
    }
}
