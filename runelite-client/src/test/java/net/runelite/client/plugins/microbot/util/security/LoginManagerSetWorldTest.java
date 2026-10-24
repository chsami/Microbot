package net.runelite.client.plugins.microbot.util.security;

import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import net.runelite.api.Client;
import net.runelite.api.World;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class LoginManagerSetWorldTest {
    private final AtomicBoolean onClientThread = new AtomicBoolean();
    private final AtomicBoolean createdOnClientThread = new AtomicBoolean();
    private final AtomicBoolean changedOnClientThread = new AtomicBoolean();
    private MockedStatic<Microbot> microbot;
    private Client client;
    private ClientThread clientThread;
    private World rsWorld;

    @Before public void setUp() {
        client = mock(Client.class);
        rsWorld = mock(World.class);
        when(client.createWorld()).thenAnswer(inv -> {
            createdOnClientThread.set(onClientThread.get());
            return rsWorld;
        });
        doAnswer(inv -> {
            changedOnClientThread.set(onClientThread.get());
            return null;
        }).when(client).changeWorld(any());

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

        net.runelite.http.api.worlds.World world = mock(net.runelite.http.api.worlds.World.class);
        when(world.getId()).thenReturn(308);
        when(world.getAddress()).thenReturn("oldschool8.runescape.com");
        when(world.getTypes()).thenReturn(EnumSet.noneOf(WorldType.class));
        WorldResult result = mock(WorldResult.class);
        when(result.findWorld(308)).thenReturn(world);
        WorldService worldService = mock(WorldService.class);
        when(worldService.getWorlds()).thenReturn(result);

        microbot = mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        microbot.when(Microbot::getWorldService).thenReturn(worldService);
    }

    @After public void tearDown() {
        microbot.close();
    }

    @Test public void changesWorldOnTheClientThread() {
        LoginManager.setWorld(308);

        verify(client).changeWorld(rsWorld);
        verify(rsWorld).setId(308);
        verify(rsWorld).setAddress("oldschool8.runescape.com");
        assertTrue(createdOnClientThread.get());
        assertTrue(changedOnClientThread.get());
    }

    @Test public void doesNotChangeWorldWhenTheClientThreadNeverRunsIt() {
        reset(clientThread);
        when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());

        LoginManager.setWorld(308);

        verify(client, never()).createWorld();
        verify(client, never()).changeWorld(any());
    }

    @Test public void deprecatedLoginSetWorldUsesTheSamePath() {
        mock(Login.class, CALLS_REAL_METHODS).setWorld(308);

        verify(client).changeWorld(rsWorld);
        assertTrue(changedOnClientThread.get());
    }
}
