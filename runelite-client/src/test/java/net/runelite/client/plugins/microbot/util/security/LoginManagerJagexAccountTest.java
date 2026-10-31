package net.runelite.client.plugins.microbot.util.security;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.google.gson.Gson;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;
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
import org.slf4j.LoggerFactory;
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
    private final Logger logger = (Logger) LoggerFactory.getLogger(LoginManager.class);
    private final ListAppender<ILoggingEvent> logs = new ListAppender<>();

    @Before public void setUp() throws Exception {
        LoginLockout.getInstance().recordSuccessfulLogin();
        resetLastLoginAttempt();
        var savedPasswordWarned = LoginManager.class.getDeclaredField("SAVED_PASSWORD_WARNED");
        savedPasswordWarned.setAccessible(true);
        ((AtomicBoolean) savedPasswordWarned.get(null)).set(false);
        logs.start();
        logger.addAppender(logs);

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
        logger.detachAppender(logs);
        microbot.close();
    }

    @Test public void profileSwitchedToAfterLaunchWithAPasswordClearsTheJagexAccountWithoutWaitingOnTheSharedExecutor() {
        switchedToProfile("legacy-user", LEGACY_PASSWORD);

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
        switchedToProfile("legacy-user", LEGACY_PASSWORD);
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

    @Test public void startupProfileWithASavedPasswordKeepsTheLaunchCharacter() {
        ConfigProfile profile = startupProfile("Main", LEGACY_PASSWORD);

        try (var keyboard = mockStatic(Rs2Keyboard.class); var launch = mockStatic(JagexCredentials.class)) {
            launch.when(JagexCredentials::fromLaunch).thenReturn(new JagexCredentials("launch-session", "7", "Jagex character"));
            assertTrue(LoginManager.login(profile.getName(), profile.getPassword(), 301));
        }
        verify(client).switchJagexAccount("launch-session", "7", "Jagex character");
        verify(client, never()).switchJagexAccount(null, null, null);
        verify(client, never()).setUsername(any());
        verify(client, never()).setPassword(any());
    }

    @Test public void startupProfileWithASavedPasswordKeepsTheJagexAccountWhenTheCredentialsFileNamesAnotherCharacter() {
        ConfigProfile profile = startupProfile("Main", LEGACY_PASSWORD);

        try (var keyboard = mockStatic(Rs2Keyboard.class); var launch = mockStatic(JagexCredentials.class)) {
            launch.when(JagexCredentials::fromLaunch).thenReturn(new JagexCredentials("other-session", "8", "Other character"));
            assertTrue(LoginManager.login(profile.getName(), profile.getPassword(), 301));
        }
        verify(client, never()).switchJagexAccount(any(), any(), any());
        verify(client, never()).setUsername(any());
        assertTrue(warnings().stream().anyMatch(w -> w.contains("different character")));
    }

    @Test public void startupProfileWithoutLaunchCredentialsKeepsTheJagexAccountAndSaysSo() {
        ConfigProfile profile = startupProfile("default", null);

        try (var keyboard = mockStatic(Rs2Keyboard.class); var launch = mockStatic(JagexCredentials.class)) {
            launch.when(JagexCredentials::fromLaunch).thenReturn(null);
            assertTrue(LoginManager.login(profile.getName(), profile.getPassword(), 301));
        }
        verify(client, never()).switchJagexAccount(any(), any(), any());
        verify(client, never()).setUsername(any());
        assertTrue(warnings().stream().anyMatch(w -> w.contains("No Jagex Account credentials")));
    }

    @Test public void launchProfileDoesNotSwitchToCredentialsOfAnotherCharacter() {
        startupProfile("Main#", LEGACY_PASSWORD);
        when(injector.getInstance(Key.get(String.class, Names.named("profile")))).thenReturn("Main#");

        try (var keyboard = mockStatic(Rs2Keyboard.class); var launch = mockStatic(JagexCredentials.class)) {
            launch.when(JagexCredentials::fromLaunch).thenReturn(new JagexCredentials("other-session", "8", "Other character"));
            assertTrue(LoginManager.login("Main#", LEGACY_PASSWORD, 301));
        }
        verify(client, never()).switchJagexAccount(any(), any(), any());
        verify(client, never()).setUsername(any());
        assertTrue(warnings().stream().anyMatch(w -> w.contains("different character")));
    }

    @Test public void startupProfileWithASavedPasswordWarnsOncePerSession() throws Exception {
        ConfigProfile profile = startupProfile("Main", LEGACY_PASSWORD);

        try (var keyboard = mockStatic(Rs2Keyboard.class); var launch = mockStatic(JagexCredentials.class)) {
            launch.when(JagexCredentials::fromLaunch).thenReturn(new JagexCredentials("launch-session", "7", "Jagex character"));
            assertTrue(LoginManager.login(profile.getName(), profile.getPassword(), 301));
            resetLastLoginAttempt();
            assertTrue(LoginManager.login(profile.getName(), profile.getPassword(), 301));
        }
        verify(client, times(2)).switchJagexAccount("launch-session", "7", "Jagex character");
        List<String> warnings = warnings();
        assertEquals(1, warnings.size());
        String warning = warnings.get(0);
        assertTrue(warning.contains("Jagex character"));
        assertTrue(warning.contains("Play without Jagex Account"));
        assertFalse(warning.contains("legacy-password"));
        assertFalse(warning.contains(LEGACY_PASSWORD));
        assertFalse(warning.contains("launch-session"));
    }

    @Test public void startupProfileWithoutAPasswordDoesNotWarn() {
        ConfigProfile profile = startupProfile("default", null);

        try (var keyboard = mockStatic(Rs2Keyboard.class); var launch = mockStatic(JagexCredentials.class)) {
            launch.when(JagexCredentials::fromLaunch).thenReturn(new JagexCredentials("launch-session", "7", "Jagex character"));
            assertTrue(LoginManager.login(profile.getName(), profile.getPassword(), 301));
        }
        verify(client).switchJagexAccount("launch-session", "7", "Jagex character");
        assertTrue(warnings().isEmpty());
    }

    @Test public void explicitCredentialsOnTheStartupProfileAreTypedForAnotherUsername() {
        startupProfile("Main", LEGACY_PASSWORD);

        try (var keyboard = mockStatic(Rs2Keyboard.class); var launch = mockStatic(JagexCredentials.class)) {
            launch.when(JagexCredentials::fromLaunch).thenReturn(new JagexCredentials("launch-session", "7", "Jagex character"));
            assertTrue(LoginManager.login("legacy-user", LEGACY_PASSWORD, 301));
        }
        verify(client).switchJagexAccount(null, null, null);
        verify(client).setUsername("legacy-user");
    }

    @Test public void loginStopsWhenTheAccountSwitchDoesNotRun() {
        doThrow(new IllegalStateException("client thread busy")).when(client).switchJagexAccount(null, null, null);

        try (var keyboard = mockStatic(Rs2Keyboard.class)) {
            assertFalse(LoginManager.login("legacy-user", LEGACY_PASSWORD, 301));
            keyboard.verifyNoInteractions();
        }
        verify(client, never()).setUsername(any());
    }

    private List<String> warnings() {
        return logs.list.stream()
            .filter(event -> event.getLevel() == Level.WARN)
            .map(ILoggingEvent::getFormattedMessage)
            .collect(Collectors.toList());
    }

    private static void resetLastLoginAttempt() throws Exception {
        var lastAttempt = LoginManager.class.getDeclaredField("LAST_LOGIN_ATTEMPT");
        lastAttempt.setAccessible(true);
        ((AtomicReference<?>) lastAttempt.get(null)).set(null);
    }

    private ConfigProfile startupProfile(String name, String password) {
        ConfigProfile profile = profile(1L, name, password);
        when(configManager.getStartupProfileId()).thenReturn(1L);
        return profile;
    }

    private void switchedToProfile(String name, String password) {
        profile(2L, name, password);
        when(configManager.getStartupProfileId()).thenReturn(1L);
    }

    private ConfigProfile profile(long id, String name, String password) {
        ConfigProfile profile = mock(ConfigProfile.class);
        when(profile.getId()).thenReturn(id);
        when(profile.getName()).thenReturn(name);
        when(profile.getPassword()).thenReturn(password);
        when(configManager.getProfile()).thenReturn(profile);
        return profile;
    }

    private static String encrypt(String password) {
        try {
            return Encryption.encrypt(password);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }
}
