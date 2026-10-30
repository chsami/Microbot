package net.runelite.client.plugins.microbot.util.security;

import com.google.gson.Gson;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.rs.auth.JagexAuthClient;
import net.runelite.client.rs.auth.JagexAuthService;
import net.runelite.client.rs.auth.SessionStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;
import org.mockito.MockedStatic;
import static org.junit.Assert.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

public class LoginManagerJagexAccountTest {
    // A rule (not @Test(timeout)) keeps @Before, the test and @After on one thread, where the static mocks live
    @Rule public Timeout timeout = Timeout.seconds(20);

    private static final String LEGACY_PASSWORD = encrypt("legacy-password");

    private MockedStatic<Microbot> microbot;
    private Client client;
    private Injector injector;
    private final ConfigManager configManager = mock(ConfigManager.class);

    @Before public void setUp() throws Exception {
        LoginLockout.getInstance().recordSuccessfulLogin();
        var lastAttempt = LoginManager.class.getDeclaredField("LAST_LOGIN_ATTEMPT");
        lastAttempt.setAccessible(true);
        ((AtomicReference<?>) lastAttempt.get(null)).set(null);

        client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        when(client.getWorld()).thenReturn(301);
        when(client.getLauncherDisplayName()).thenReturn("Jagex character");

        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
            try {
                return Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call());
            } catch (Exception e) {
                return Optional.empty(); // like ClientThread: a call that did not complete has no result
            }
        });

        SessionStore sessionStore = mock(SessionStore.class);
        when(sessionStore.load()).thenReturn(List.of());
        // Profile logins run on the shared single-thread executor, so nothing queued on it runs until the login returns
        ScheduledExecutorService busySharedExecutor = mock(ScheduledExecutorService.class);
        JagexAuthService jagexAuthService = new JagexAuthService(
            client, clientThread, mock(JagexAuthClient.class), sessionStore, new Gson(), busySharedExecutor);
        injector = mock(Injector.class);
        when(injector.getInstance(JagexAuthService.class)).thenReturn(jagexAuthService);

        microbot = mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        microbot.when(Microbot::getInjector).thenReturn(injector);
        microbot.when(Microbot::getConfigManager).thenReturn(configManager);
    }

    @After public void tearDown() {
        microbot.close();
    }

    @Test public void legacyProfileClearsTheJagexAccountWithoutWaitingOnTheSharedExecutor() {
        try (var keyboard = mockStatic(Rs2Keyboard.class)) {
            assertTrue(LoginManager.login("legacy-user", LEGACY_PASSWORD, 301));
        }
        verify(client).switchJagexAccount(null, null, null);
        verify(client).setUsername("legacy-user");
    }

    @Test public void importedProfileSwitchesToItsJagexAccountInsteadOfTypingCredentials() throws Exception {
        ConfigProfile profile = mock(ConfigProfile.class);
        when(profile.getName()).thenReturn("Imported character");
        when(profile.getJagexSessionId()).thenReturn(Encryption.encrypt("session-id"));
        when(profile.getJagexCharacterId()).thenReturn("42");
        when(configManager.getProfile()).thenReturn(profile);

        try (var keyboard = mockStatic(Rs2Keyboard.class)) {
            assertTrue(LoginManager.login("Imported character", "", 301));
        }
        verify(client).switchJagexAccount("session-id", "42", "Imported character");
        verify(client, never()).setUsername(any());
    }

    @Test public void launchCharacterIsSelectedAgainInsteadOfAssumed() {
        // after a legacy login the launch-time name is still reported, but its account is no longer selected
        try (var keyboard = mockStatic(Rs2Keyboard.class); var launch = mockStatic(JagexCredentials.class)) {
            launch.when(JagexCredentials::fromLaunch).thenReturn(new JagexCredentials("launch-session", "7", "Jagex character"));
            assertTrue(LoginManager.login("Jagex character", "", 301));
        }
        verify(client).switchJagexAccount("launch-session", "7", "Jagex character");
        verify(client, never()).setUsername(any());
    }

    /** Launcher "Play With Jagex Account" on a profile whose name is not the character's: keep the launch character. */
    @Test public void launchProfileKeepsTheLaunchCharacter() {
        ConfigProfile profile = mock(ConfigProfile.class);
        when(profile.getName()).thenReturn("Main#");
        when(profile.getPassword()).thenReturn("stored");
        when(configManager.getProfile()).thenReturn(profile);
        when(injector.getInstance(Key.get(String.class, Names.named("profile")))).thenReturn("Main#");

        try (var keyboard = mockStatic(Rs2Keyboard.class); var launch = mockStatic(JagexCredentials.class)) {
            launch.when(JagexCredentials::fromLaunch).thenReturn(new JagexCredentials("launch-session", "7", "Jagex character"));
            assertTrue(LoginManager.login("Main#", "", 301));
        }
        verify(client).switchJagexAccount("launch-session", "7", "Jagex character");
        verify(client, never()).setUsername(any());
    }

    /** Switching in-client to a different, legacy profile still clears the launch character. */
    @Test public void otherProfileThanTheLaunchOneStillLogsInLegacy() {
        ConfigProfile profile = mock(ConfigProfile.class);
        when(profile.getName()).thenReturn("legacy-user");
        when(configManager.getProfile()).thenReturn(profile);
        when(injector.getInstance(Key.get(String.class, Names.named("profile")))).thenReturn("Main#");

        try (var keyboard = mockStatic(Rs2Keyboard.class)) {
            assertTrue(LoginManager.login("legacy-user", LEGACY_PASSWORD, 301));
        }
        verify(client).switchJagexAccount(null, null, null);
        verify(client).setUsername("legacy-user");
    }

    @Test public void passwordlessProfileKeepsTheLaunchCharacter() {
        ConfigProfile profile = mock(ConfigProfile.class);
        when(profile.getName()).thenReturn("default");
        when(configManager.getProfile()).thenReturn(profile);

        try (var keyboard = mockStatic(Rs2Keyboard.class); var launch = mockStatic(JagexCredentials.class)) {
            launch.when(JagexCredentials::fromLaunch).thenReturn(new JagexCredentials("launch-session", "7", "Jagex character"));
            assertTrue(LoginManager.login("default", "", 301));
        }
        verify(client).switchJagexAccount("launch-session", "7", "Jagex character");
        verify(client, never()).switchJagexAccount(null, null, null);
        verify(client, never()).setUsername(any());
    }

    @Test public void passwordlessProfileWithoutLaunchCredentialsLogsInLegacy() {
        try (var keyboard = mockStatic(Rs2Keyboard.class); var launch = mockStatic(JagexCredentials.class)) {
            launch.when(JagexCredentials::fromLaunch).thenReturn(null);
            assertTrue(LoginManager.login("default", "", 301));
        }
        verify(client).switchJagexAccount(null, null, null);
        verify(client).setUsername("default");
    }

    @Test public void passwordlessProfileIgnoresCredentialsOfAnotherCharacter() {
        try (var keyboard = mockStatic(Rs2Keyboard.class); var launch = mockStatic(JagexCredentials.class)) {
            launch.when(JagexCredentials::fromLaunch).thenReturn(new JagexCredentials("other-session", "8", "Other character"));
            assertTrue(LoginManager.login("default", "", 301));
        }
        verify(client, never()).switchJagexAccount("other-session", "8", "Other character");
        verify(client).switchJagexAccount(null, null, null);
    }

    @Test public void loginStopsWhenTheAccountSwitchDoesNotRun() {
        doThrow(new IllegalStateException("client thread busy")).when(client).switchJagexAccount(null, null, null);

        try (var keyboard = mockStatic(Rs2Keyboard.class)) {
            assertFalse(LoginManager.login("legacy-user", LEGACY_PASSWORD, 301));
            keyboard.verifyNoInteractions();
        }
        verify(client, never()).setUsername(any());
    }

    private static String encrypt(String password) {
        try {
            return Encryption.encrypt(password);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
