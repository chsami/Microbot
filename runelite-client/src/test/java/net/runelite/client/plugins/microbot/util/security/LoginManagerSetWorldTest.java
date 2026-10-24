package net.runelite.client.plugins.microbot.util.security;

import java.util.EnumSet;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.World;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.http.api.worlds.WorldResult;
import net.runelite.http.api.worlds.WorldType;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

public class LoginManagerSetWorldTest {
    private final AtomicBoolean onClientThread = new AtomicBoolean();
    private final AtomicBoolean createdOnClientThread = new AtomicBoolean();
    private final AtomicBoolean changedOnClientThread = new AtomicBoolean();
    private final AtomicInteger currentWorld = new AtomicInteger(301);
    private MockedStatic<Microbot> microbot;
    private Client client;
    private ClientThread clientThread;
    private World rsWorld;

    @Before public void setUp() throws Exception {
        LoginLockout.getInstance().recordSuccessfulLogin();
        var lastAttempt = LoginManager.class.getDeclaredField("LAST_LOGIN_ATTEMPT");
        lastAttempt.setAccessible(true);
        ((AtomicReference<?>) lastAttempt.get(null)).set(null);

        client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        when(client.getLoginIndex()).thenReturn(2);
        when(client.getCanvasWidth()).thenReturn(804);
        when(client.getWorld()).thenAnswer(inv -> currentWorld.get());
        rsWorld = mock(World.class);
        when(client.createWorld()).thenAnswer(inv -> {
            createdOnClientThread.set(onClientThread.get());
            return rsWorld;
        });
        doAnswer(inv -> {
            changedOnClientThread.set(onClientThread.get());
            currentWorld.set(308);
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
        assertTrue(LoginManager.setWorld(308));

        verify(client).changeWorld(rsWorld);
        verify(rsWorld).setId(308);
        verify(rsWorld).setAddress("oldschool8.runescape.com");
        assertTrue(createdOnClientThread.get());
        assertTrue(changedOnClientThread.get());
    }

    @Test public void doesNotChangeWorldWhenTheClientThreadNeverRunsIt() {
        reset(clientThread);
        when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());

        assertFalse(LoginManager.setWorld(308));

        verify(client, never()).createWorld();
        verify(client, never()).changeWorld(any());
    }

    @Test public void reportsFailureForAnUnknownWorld() {
        assertFalse(LoginManager.setWorld(999));

        verify(client, never()).changeWorld(any());
    }

    @Test public void loginStopsBeforeCredentialsWhenTheWorldCannotBeSelected() {
        reset(clientThread);
        when(clientThread.runOnClientThreadOptional(any())).thenReturn(Optional.empty());

        try (var keyboard = mockStatic(Rs2Keyboard.class)) {
            assertFalse(LoginManager.login("user", "", 308));

            keyboard.verifyNoInteractions();
        }
        verify(client, never()).setUsername(anyString());
        assertFalse(LoginManager.isLoginAttemptActive());
    }

    @Test public void loginSubmitsAfterTheWorldChangeIsObserved() {
        try (var keyboard = mockStatic(Rs2Keyboard.class)) {
            assertTrue(LoginManager.login("user", "", 308));
        }
        verify(client).changeWorld(rsWorld);
        verify(client).setUsername("user");
        assertTrue(changedOnClientThread.get());
    }

    @Test public void loginStopsWhenTheClientNeverReportsTheNewWorld() {
        doNothing().when(client).changeWorld(any());

        try (var keyboard = mockStatic(Rs2Keyboard.class)) {
            assertFalse(LoginManager.login("user", "", 308));

            keyboard.verifyNoInteractions();
        }
        verify(client).changeWorld(rsWorld);
        verify(client, never()).setUsername(anyString());
    }

    @Test public void loginSkipsTheWorldChangeWhenAlreadyOnTheTargetWorld() {
        currentWorld.set(308);

        try (var keyboard = mockStatic(Rs2Keyboard.class)) {
            assertTrue(LoginManager.login("user", "", 308));
        }
        verify(client, never()).createWorld();
        verify(client).setUsername("user");
    }

    @Test public void deprecatedLoginSetWorldUsesTheSamePath() {
        mock(Login.class, CALLS_REAL_METHODS).setWorld(308);

        verify(client).changeWorld(rsWorld);
        assertTrue(changedOnClientThread.get());
    }
}
