package net.runelite.client.plugins.microbot.util.security;

import com.google.gson.Gson;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.name.Names;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
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
import net.runelite.client.config.RuneScapeProfile;
import net.runelite.client.config.RuneScapeProfileType;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.rs.auth.JagexAuthClient;
import net.runelite.client.rs.auth.JagexAuthService;
import net.runelite.client.rs.auth.SessionStore;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.rules.Timeout;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.MockedStatic;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@RunWith(Parameterized.class)
public class LoginManagerLaunchModeTest {
    @Rule public Timeout timeout = Timeout.seconds(20);
    @Rule public TemporaryFolder folder = new TemporaryFolder();

    private static final String LAUNCH_CHARACTER = "Jagex character";
    private static final String LEGACY_PASSWORD = "legacy-password";
    private static final long STARTUP_PROFILE_ID = 1L;
    private static final long SWITCHED_PROFILE_ID = 2L;
    private static final String LINKED_RS_PROFILE = "rsprofile.linked";

    enum LaunchMode {
        JAGEX_LAUNCHER(true, false),
        RUNELITE_LAUNCHER_CREDENTIALS_FILE(true, false),
        MICROBOT_LAUNCHER(true, true),
        LEGACY(false, false);

        final boolean jagexAccount;
        final boolean profileArgument;

        LaunchMode(boolean jagexAccount, boolean profileArgument) {
            this.jagexAccount = jagexAccount;
            this.profileArgument = profileArgument;
        }
    }

    enum ProfileShape {
        DEFAULT("default", false, false, STARTUP_PROFILE_ID, false),
        NAMED("Main", false, false, STARTUP_PROFILE_ID, false),
        SAVED_LEGACY_PASSWORD("Main", true, false, STARTUP_PROFILE_ID, false),
        SAVED_SESSION("Imported character", false, true, STARTUP_PROFILE_ID, false),
        SWITCHED_TO_WITH_SAVED_PASSWORD("legacy-user", true, false, SWITCHED_PROFILE_ID, false),
        SWITCHED_TO_LINKED_WITH_SAVED_PASSWORD("Linked", true, false, SWITCHED_PROFILE_ID, true);

        final String name;
        final boolean password;
        final boolean session;
        final long id;
        final boolean linkedToLaunchCharacter;

        ProfileShape(String name, boolean password, boolean session, long id, boolean linkedToLaunchCharacter) {
            this.name = name;
            this.password = password;
            this.session = session;
            this.id = id;
            this.linkedToLaunchCharacter = linkedToLaunchCharacter;
        }
    }

    enum Expected { LAUNCH_CHARACTER, IMPORTED_CHARACTER, TYPED_CREDENTIALS }

    @Parameterized.Parameters(name = "{0} with {1} -> {2}")
    public static Collection<Object[]> matrix() {
        List<Object[]> rows = new ArrayList<>();
        for (LaunchMode mode : LaunchMode.values()) {
            for (ProfileShape shape : ProfileShape.values()) {
                rows.add(new Object[]{mode, shape, expected(mode, shape)});
            }
        }
        return rows;
    }

    private static Expected expected(LaunchMode mode, ProfileShape shape) {
        if (shape.session) {
            return Expected.IMPORTED_CHARACTER;
        }
        if (!mode.jagexAccount || shape == ProfileShape.SWITCHED_TO_WITH_SAVED_PASSWORD) {
            return Expected.TYPED_CREDENTIALS;
        }
        return Expected.LAUNCH_CHARACTER;
    }

    @Parameterized.Parameter(0) public LaunchMode mode;
    @Parameterized.Parameter(1) public ProfileShape shape;
    @Parameterized.Parameter(2) public Expected expected;

    private MockedStatic<Microbot> microbot;
    private Client client;

    @Before public void setUp() throws Exception {
        LoginLockout.getInstance().recordSuccessfulLogin();
        var lastAttempt = LoginManager.class.getDeclaredField("LAST_LOGIN_ATTEMPT");
        lastAttempt.setAccessible(true);
        ((AtomicReference<?>) lastAttempt.get(null)).set(null);

        client = mock(Client.class);
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        when(client.getWorld()).thenReturn(301);
        when(client.getLauncherDisplayName()).thenReturn(mode.jagexAccount ? LAUNCH_CHARACTER : null);

        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
            try {
                return Optional.ofNullable(((Callable<?>) inv.getArgument(0)).call());
            } catch (Exception e) {
                return Optional.empty();
            }
        });

        SessionStore sessionStore = mock(SessionStore.class);
        when(sessionStore.load()).thenReturn(List.of());
        JagexAuthService jagexAuthService = new JagexAuthService(
            client, clientThread, mock(JagexAuthClient.class), sessionStore, new Gson(), mock(ScheduledExecutorService.class));
        Injector injector = mock(Injector.class);
        when(injector.getInstance(JagexAuthService.class)).thenReturn(jagexAuthService);
        when(injector.getInstance(Key.get(String.class, Names.named("profile"))))
            .thenReturn(mode.profileArgument ? startupProfileName() : null);

        ConfigProfile profile = mock(ConfigProfile.class);
        when(profile.getId()).thenReturn(shape.id);
        when(profile.getName()).thenReturn(shape.name);
        when(profile.getPassword()).thenReturn(shape.password ? Encryption.encrypt(LEGACY_PASSWORD) : null);
        if (shape.session) {
            when(profile.getJagexSessionId()).thenReturn(Encryption.encrypt("imported-session"));
            when(profile.getJagexCharacterId()).thenReturn("42");
        }
        if (shape.linkedToLaunchCharacter) {
            when(profile.getDefaultForRsProfiles()).thenReturn(List.of(LINKED_RS_PROFILE));
        }

        ConfigManager configManager = mock(ConfigManager.class);
        when(configManager.getProfile()).thenReturn(profile);
        when(configManager.getStartupProfileId()).thenReturn(STARTUP_PROFILE_ID);
        when(configManager.getRSProfiles()).thenReturn(List.of(
            new RuneScapeProfile(LAUNCH_CHARACTER, RuneScapeProfileType.STANDARD, 99L, LINKED_RS_PROFILE)));

        microbot = mockStatic(Microbot.class);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        microbot.when(Microbot::getInjector).thenReturn(injector);
        microbot.when(Microbot::getConfigManager).thenReturn(configManager);
    }

    @After public void tearDown() {
        microbot.close();
    }

    @Test public void scriptedRelogPicksTheAccountForTheLaunchMode() throws Exception {
        JagexCredentials launchCredentials = launchCredentials();
        try (var keyboard = mockStatic(Rs2Keyboard.class); var launch = mockStatic(JagexCredentials.class)) {
            launch.when(JagexCredentials::fromLaunch).thenReturn(launchCredentials);
            assertTrue(LoginManager.login(301));
        }

        switch (expected) {
            case LAUNCH_CHARACTER:
                verify(client).switchJagexAccount("launch-session", "7", LAUNCH_CHARACTER);
                verify(client, never()).switchJagexAccount(null, null, null);
                verify(client, never()).setUsername(any());
                break;
            case IMPORTED_CHARACTER:
                verify(client).switchJagexAccount("imported-session", "42", "Imported character");
                verify(client, never()).switchJagexAccount(null, null, null);
                verify(client, never()).setUsername(any());
                break;
            case TYPED_CREDENTIALS:
                if (mode.jagexAccount) {
                    verify(client).switchJagexAccount(null, null, null);
                } else {
                    verify(client, never()).switchJagexAccount(any(), any(), any());
                }
                verify(client).setUsername(shape.name);
                if (shape.password) {
                    verify(client).setPassword(LEGACY_PASSWORD);
                }
                break;
        }
    }

    private String startupProfileName() {
        return shape.id == STARTUP_PROFILE_ID ? shape.name : "Startup profile";
    }

    private JagexCredentials launchCredentials() throws Exception {
        switch (mode) {
            case RUNELITE_LAUNCHER_CREDENTIALS_FILE:
                File file = folder.newFile("credentials.properties");
                Files.writeString(file.toPath(), "JX_CHARACTER_ID=7\nJX_SESSION_ID=launch-session\nJX_DISPLAY_NAME=" + LAUNCH_CHARACTER + "\n");
                return JagexCredentials.load(file);
            case LEGACY:
                return null;
            default:
                return new JagexCredentials("launch-session", "7", LAUNCH_CHARACTER);
        }
    }
}
