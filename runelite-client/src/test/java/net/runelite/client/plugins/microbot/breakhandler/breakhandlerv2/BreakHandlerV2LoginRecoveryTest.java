package net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2;

import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.config.ConfigProfile;
import net.runelite.client.game.WorldService;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.accountselector.AutoLoginSuppressionRequest;
import net.runelite.client.plugins.microbot.accountselector.TerminalLogoutHold;
import net.runelite.client.plugins.microbot.breakhandler.BreakPreparation;
import net.runelite.client.plugins.microbot.util.antiban.enums.PlaySchedule;
import net.runelite.client.plugins.microbot.util.discord.Rs2Discord;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.world.RegionPreference;
import net.runelite.client.plugins.microbot.util.world.Rs2WorldUtil;
import net.runelite.client.plugins.microbot.util.world.WorldSelectionMode;
import net.runelite.http.api.worlds.WorldResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ScheduledExecutorService;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

public class BreakHandlerV2LoginRecoveryTest {
    private MockedStatic<Microbot> microbot;
    private MockedStatic<LoginManager> loginManager;
    private MockedStatic<Rs2WorldUtil> worldUtil;
    private MockedStatic<Rs2Discord> discord;
    private BreakHandlerV2Script script;
    private BreakHandlerV2Config config;
    private Client client;
    private ConfigManager configManager;
    private boolean loggedIn;

    @Before
    public void setUp() throws Exception {
        client = mock(Client.class);
        ClientThread clientThread = mock(ClientThread.class);
        when(clientThread.runOnClientThreadOptional(any())).thenAnswer(inv -> {
            Callable<?> callable = inv.getArgument(0);
            return Optional.ofNullable(callable.call());
        });
        microbot = Mockito.mockStatic(Microbot.class, Mockito.CALLS_REAL_METHODS);
        microbot.when(Microbot::getClient).thenReturn(client);
        microbot.when(Microbot::getClientThread).thenReturn(clientThread);
        microbot.when(Microbot::isLoggedIn).thenAnswer(inv -> loggedIn);
        loginManager = Mockito.mockStatic(LoginManager.class);
        worldUtil = Mockito.mockStatic(Rs2WorldUtil.class);
        worldUtil.when(() -> Rs2WorldUtil.getRandomAccessibleWorld(anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(-1);
        discord = Mockito.mockStatic(Rs2Discord.class);

        config = mock(BreakHandlerV2Config.class);
        when(config.worldSelectionMode()).thenReturn(WorldSelectionMode.RANDOM_WORLD);
        when(config.regionPreference()).thenReturn(RegionPreference.ANY_REGION);
        when(config.minPlaytime()).thenReturn(60);
        when(config.maxPlaytime()).thenReturn(60);
        when(config.autoLogin()).thenReturn(true);
        when(config.useActiveProfile()).thenReturn(true);
        configManager = mock(ConfigManager.class);
        microbot.when(Microbot::getConfigManager).thenReturn(configManager);
        script = new BreakHandlerV2Script();
        setField("config", config);
        Microbot.pauseAllScripts.set(true);
    }

    @After
    public void tearDown() throws Exception {
        BreakPreparation.finishBreak();
        Microbot.pauseAllScripts.set(false);
        BreakHandlerV2State.setState(BreakHandlerV2State.WAITING_FOR_BREAK);
        Field executor = Script.class.getDeclaredField("scheduledExecutorService");
        executor.setAccessible(true);
        ((ScheduledExecutorService) executor.get(script)).shutdownNow();
        discord.close();
        worldUtil.close();
        loginManager.close();
        microbot.close();
    }

    @Test
    public void maxLoginAttemptsKeepRetryingInsteadOfEndingTheBreak() throws Exception {
        Instant breakEnd = Instant.now().minusSeconds(60);
        setField("breakEndTime", breakEnd);
        setField("loginRetryCount", 10);
        BreakHandlerV2State.setState(BreakHandlerV2State.LOGIN_REQUESTED);

        invoke("handleLoginRequested");

        assertEquals(BreakHandlerV2State.LOGIN_EXTENDED_SLEEP, BreakHandlerV2State.getCurrentState());
        Instant sleepEnd = (Instant) getField("extendedSleepEndsAt");
        assertTrue(sleepEnd.isAfter(Instant.now().plus(Duration.ofMinutes(4))));

        setField("extendedSleepEndsAt", Instant.now().minusSeconds(1));
        invoke("handleLoginExtendedSleep");

        assertEquals(BreakHandlerV2State.LOGIN_REQUESTED, BreakHandlerV2State.getCurrentState());
        assertEquals(0, getField("loginRetryCount"));
        assertNull(getField("nextLoginAttemptAt"));
        assertSame(breakEnd, getField("breakEndTime"));
        assertTrue(Microbot.pauseAllScripts.get());
    }

    @Test
    public void extendedSleepDoesNotBlockTheLoop() throws Exception {
        setField("extendedSleepEndsAt", Instant.now().plus(Duration.ofMinutes(5)));
        BreakHandlerV2State.setState(BreakHandlerV2State.LOGIN_EXTENDED_SLEEP);

        long started = System.nanoTime();
        invoke("handleLoginExtendedSleep");

        assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 1000);
        assertEquals(BreakHandlerV2State.LOGIN_EXTENDED_SLEEP, BreakHandlerV2State.getCurrentState());
    }

    @Test
    public void manualLoginDuringExtendedSleepEndsTheBreak() throws Exception {
        setField("extendedSleepEndsAt", Instant.now().plus(Duration.ofMinutes(5)));
        BreakHandlerV2State.setState(BreakHandlerV2State.LOGIN_EXTENDED_SLEEP);
        loggedIn = true;

        invoke("handleLoginExtendedSleep");

        assertEquals(BreakHandlerV2State.BREAK_ENDING, BreakHandlerV2State.getCurrentState());
        assertNull(getField("extendedSleepEndsAt"));
    }

    @Test
    public void loginBackoffWaitsWithoutBlocking() throws Exception {
        setField("activeProfile", profile());
        setField("loginRetryCount", 5);
        setField("nextLoginAttemptAt", Instant.now().plusSeconds(90));
        BreakHandlerV2State.setState(BreakHandlerV2State.LOGIN_REQUESTED);

        long started = System.nanoTime();
        invoke("handleLoginRequested");

        assertTrue(Duration.ofNanos(System.nanoTime() - started).toMillis() < 1000);
        loginManager.verify(() -> LoginManager.login(anyString(), anyString(), anyInt()), never());
        assertEquals(5, getField("loginRetryCount"));
    }

    @Test
    public void failedLoginSchedulesTheNextAttemptWithBackoff() throws Exception {
        setField("activeProfile", profile());
        setField("loginRetryCount", 4);
        when(client.getWorld()).thenReturn(308);
        loginManager.when(() -> LoginManager.login(anyString(), anyString(), anyInt())).thenReturn(false);
        BreakHandlerV2State.setState(BreakHandlerV2State.LOGIN_REQUESTED);

        invoke("handleLoginRequested");

        assertEquals(5, getField("loginRetryCount"));
        Instant nextAttempt = (Instant) getField("nextLoginAttemptAt");
        assertTrue(nextAttempt.isAfter(Instant.now().plusSeconds(80)));
        assertTrue(nextAttempt.isBefore(Instant.now().plusSeconds(91)));
        assertEquals(BreakHandlerV2State.LOGIN_REQUESTED, BreakHandlerV2State.getCurrentState());
    }

    @Test
    public void terminalLogoutHoldsTheLoginUntilTheAccountLogsInByHand() throws Exception {
        setField("activeProfile", profile());
        TerminalLogoutHold hold = new TerminalLogoutHold();
        hold.onAutoLoginSuppressionRequest(new AutoLoginSuppressionRequest());
        hold.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
        setField("terminalLogoutHold", hold);
        BreakHandlerV2State.setState(BreakHandlerV2State.LOGIN_REQUESTED);

        invoke("handleLoginRequested");
        invoke("handleLoginRequested");

        loginManager.verify(() -> LoginManager.login(anyString(), anyString(), anyInt()), never());
        assertEquals(BreakHandlerV2State.LOGIN_REQUESTED, BreakHandlerV2State.getCurrentState());
        assertEquals(0, getField("loginRetryCount"));

        hold.onGameStateChanged(gameState(GameState.LOGGED_IN));
        assertFalse(hold.isActive());
        loggedIn = true;
        invoke("handleLoginRequested");

        assertEquals(BreakHandlerV2State.BREAK_ENDING, BreakHandlerV2State.getCurrentState());
    }

    @Test
    public void terminalLogoutIsNotTreatedAsADisconnect() throws Exception {
        TerminalLogoutHold hold = new TerminalLogoutHold();
        hold.onAutoLoginSuppressionRequest(new AutoLoginSuppressionRequest());
        hold.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
        setField("terminalLogoutHold", hold);
        setField("nextBreakTime", Instant.now().plus(Duration.ofMinutes(30)));
        when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
        BreakHandlerV2State.setState(BreakHandlerV2State.WAITING_FOR_BREAK);

        invoke("detectUnexpectedLogout");

        assertEquals(BreakHandlerV2State.WAITING_FOR_BREAK, BreakHandlerV2State.getCurrentState());
        assertFalse((Boolean) getField("resumingAfterDisconnect"));

        hold.release();
        invoke("detectUnexpectedLogout");

        assertEquals(BreakHandlerV2State.LOGIN_REQUESTED, BreakHandlerV2State.getCurrentState());
    }

    private static GameStateChanged gameState(GameState state) {
        GameStateChanged event = new GameStateChanged();
        event.setGameState(state);
        return event;
    }

    @Test
    public void unavailableWorldListFallsBackToTheCurrentWorld() throws Exception {
        setField("activeProfile", profile());
        when(client.getWorld()).thenReturn(308);
        loginManager.when(() -> LoginManager.login(anyString(), anyString(), anyInt())).thenReturn(true);
        BreakHandlerV2State.setState(BreakHandlerV2State.LOGIN_REQUESTED);

        invoke("handleLoginRequested");

        loginManager.verify(() -> LoginManager.login(eq("player"), eq("secret"), eq(308)));
        assertEquals(BreakHandlerV2State.LOGGING_IN, BreakHandlerV2State.getCurrentState());
    }

    @Test
    public void availableWorldListSkipsAnInaccessibleCurrentWorld() throws Exception {
        stubAvailableWorldList();
        setField("activeProfile", profile());
        when(client.getWorld()).thenReturn(308);
        worldUtil.when(() -> Rs2WorldUtil.canAccessWorld(308)).thenReturn(false);
        BreakHandlerV2State.setState(BreakHandlerV2State.LOGIN_REQUESTED);

        invoke("handleLoginRequested");

        loginManager.verify(() -> LoginManager.login(anyString(), anyString(), anyInt()), never());
        assertEquals(1, getField("loginRetryCount"));
        assertEquals(BreakHandlerV2State.LOGIN_REQUESTED, BreakHandlerV2State.getCurrentState());
    }

    @Test
    public void availableWorldListFallsBackToAnAccessibleCurrentWorld() throws Exception {
        stubAvailableWorldList();
        setField("activeProfile", profile());
        when(client.getWorld()).thenReturn(308);
        worldUtil.when(() -> Rs2WorldUtil.canAccessWorld(308)).thenReturn(true);
        loginManager.when(() -> LoginManager.login(anyString(), anyString(), anyInt())).thenReturn(true);
        BreakHandlerV2State.setState(BreakHandlerV2State.LOGIN_REQUESTED);

        invoke("handleLoginRequested");

        loginManager.verify(() -> LoginManager.login(eq("player"), eq("secret"), eq(308)));
        assertEquals(BreakHandlerV2State.LOGGING_IN, BreakHandlerV2State.getCurrentState());
    }

    private void stubAvailableWorldList() {
        WorldService worldService = mock(WorldService.class);
        when(worldService.getWorlds()).thenReturn(mock(WorldResult.class));
        microbot.when(Microbot::getWorldService).thenReturn(worldService);
    }

    @Test
    public void missingProfileRetriesInsteadOfLeavingTheBreak() throws Exception {
        Instant breakEnd = Instant.now().minusSeconds(60);
        setField("breakEndTime", breakEnd);
        BreakHandlerV2State.setState(BreakHandlerV2State.LOGIN_REQUESTED);

        invoke("handleLoginRequested");

        assertEquals(BreakHandlerV2State.LOGIN_REQUESTED, BreakHandlerV2State.getCurrentState());
        assertEquals(1, getField("loginRetryCount"));
        assertNotNull(getField("nextLoginAttemptAt"));
        assertSame(breakEnd, getField("breakEndTime"));
        assertTrue(Microbot.pauseAllScripts.get());
    }

    @Test
    public void autoLoginTurnedOffDuringRetriesEndsTheBreak() throws Exception {
        when(config.autoLogin()).thenReturn(false);
        setField("activeProfile", profile());
        setField("loginRetryCount", 5);
        BreakHandlerV2State.setState(BreakHandlerV2State.LOGIN_REQUESTED);

        invoke("handleLoginRequested");

        assertEquals(BreakHandlerV2State.BREAK_ENDING, BreakHandlerV2State.getCurrentState());
        loginManager.verify(() -> LoginManager.login(anyString(), anyString(), anyInt()), never());
    }

    @Test
    public void autoLoginTurnedOffDuringExtendedSleepEndsTheBreak() throws Exception {
        when(config.autoLogin()).thenReturn(false);
        setField("extendedSleepEndsAt", Instant.now().minusSeconds(1));
        BreakHandlerV2State.setState(BreakHandlerV2State.LOGIN_EXTENDED_SLEEP);

        invoke("handleLoginExtendedSleep");

        assertEquals(BreakHandlerV2State.BREAK_ENDING, BreakHandlerV2State.getCurrentState());
        assertNull(getField("extendedSleepEndsAt"));
    }

    @Test
    public void profileLoginDisabledEndsTheBreakInsteadOfRetrying() throws Exception {
        when(config.useActiveProfile()).thenReturn(false);
        setField("breakEndTime", Instant.now().minusSeconds(1));
        BreakHandlerV2State.setState(BreakHandlerV2State.LOGGED_OUT);

        invoke("handleLoggedOut");

        assertEquals(BreakHandlerV2State.BREAK_ENDING, BreakHandlerV2State.getCurrentState());

        BreakHandlerV2State.setState(BreakHandlerV2State.LOGIN_REQUESTED);
        invoke("handleLoginRequested");

        assertEquals(BreakHandlerV2State.BREAK_ENDING, BreakHandlerV2State.getCurrentState());
        assertEquals(0, getField("loginRetryCount"));
    }

    @Test
    public void reloginAfterDisconnectKeepsThePendingBreak() throws Exception {
        Instant nextBreak = Instant.now().plus(Duration.ofMinutes(20));
        setField("nextBreakTime", nextBreak);
        setField("resumingAfterDisconnect", true);
        BreakHandlerV2State.setState(BreakHandlerV2State.BREAK_ENDING);

        invoke("handleBreakEnding");

        assertSame(nextBreak, getField("nextBreakTime"));
        assertEquals(false, getField("resumingAfterDisconnect"));
        assertEquals(BreakHandlerV2State.WAITING_FOR_BREAK, BreakHandlerV2State.getCurrentState());
    }

    @Test
    public void realBreakEndingSchedulesTheNextBreak() throws Exception {
        Instant nextBreak = Instant.now().plus(Duration.ofMinutes(20));
        setField("nextBreakTime", nextBreak);
        setField("breakEndTime", Instant.now().minusSeconds(1));
        BreakHandlerV2State.setState(BreakHandlerV2State.BREAK_ENDING);

        invoke("handleBreakEnding");

        Instant rescheduled = (Instant) getField("nextBreakTime");
        assertTrue(rescheduled.isAfter(Instant.now().plus(Duration.ofMinutes(59))));
    }

    @Test
    public void breakEndsBeforeScheduleOnlyWhenTheGapIsReal() {
        Instant now = Instant.now();
        Duration untilSchedule = Duration.ofHours(10);

        assertTrue(BreakHandlerV2Script.breakEndsBeforeSchedule(now.plusSeconds(600), untilSchedule, now));
        assertFalse(BreakHandlerV2Script.breakEndsBeforeSchedule(now.plus(untilSchedule), untilSchedule, now));
        assertFalse(BreakHandlerV2Script.breakEndsBeforeSchedule(now.plus(untilSchedule).minusSeconds(5), untilSchedule, now));
    }

    @Test
    public void loggedOutOutsideScheduleDoesNotRewriteTheBreakEveryTick() throws Exception {
        PlaySchedule outside = findScheduleFurthestFromStarting();
        when(config.usePlaySchedule()).thenReturn(true);
        when(config.playSchedule()).thenReturn(outside);
        Instant breakEnd = Instant.now().plus(outside.timeUntilNextSchedule());
        setField("breakEndTime", breakEnd);
        BreakHandlerV2State.setState(BreakHandlerV2State.LOGGED_OUT);

        invoke("handleLoggedOut");

        assertSame(breakEnd, getField("breakEndTime"));

        Instant shortBreakEnd = Instant.now().plusSeconds(600);
        setField("breakEndTime", shortBreakEnd);

        invoke("handleLoggedOut");

        Instant extended = (Instant) getField("breakEndTime");
        assertTrue(extended.isAfter(shortBreakEnd));
        assertEquals(BreakHandlerV2State.LOGGED_OUT, BreakHandlerV2State.getCurrentState());
    }

    @Test
    public void disabledBreakStartNotificationIsNotSent() throws Exception {
        when(config.enableDiscordWebhook()).thenReturn(true);
        setField("activeProfile", profile());

        invokeNotification(false);
        discord.verify(() -> Rs2Discord.sendCustomNotification(anyString(), anyString(), anyInt(), anyString(), anyString()), never());

        when(config.notifyOnBreakStart()).thenReturn(true);
        invokeNotification(false);
        discord.verify(() -> Rs2Discord.sendCustomNotification(eq("Break Started"), anyString(), anyInt(), anyString(), anyString()));
    }

    @Test
    public void restoredLogoutBreakKeepsItsEndTimeWhenLoggedIn() throws Exception {
        Instant savedEnd = restoreLogoutBreak(true);

        assertEquals(BreakHandlerV2State.INITIATING_BREAK, BreakHandlerV2State.getCurrentState());

        invoke("handleInitiatingBreak");

        assertEquals(BreakHandlerV2State.LOGOUT_REQUESTED, BreakHandlerV2State.getCurrentState());
        assertEquals(savedEnd, getField("breakEndTime"));
        assertEquals(0, getField("breaksActivatedCount"));
    }

    @Test
    public void restoredLogoutBreakKeepsItsEndTimeWhenAlreadyLoggedOut() throws Exception {
        Instant savedEnd = restoreLogoutBreak(true);
        loggedIn = false;

        invoke("handleInitiatingBreak");

        assertEquals(BreakHandlerV2State.LOGGED_OUT, BreakHandlerV2State.getCurrentState());
        assertEquals(savedEnd, getField("breakEndTime"));
        assertEquals(0, getField("breaksActivatedCount"));
    }

    @Test
    public void newLogoutBreakStillRollsADuration() throws Exception {
        when(config.minBreakDuration()).thenReturn(10);
        when(config.maxBreakDuration()).thenReturn(10);
        loggedIn = true;
        BreakHandlerV2State.setState(BreakHandlerV2State.INITIATING_BREAK);

        invoke("handleInitiatingBreak");

        assertEquals(BreakHandlerV2State.LOGOUT_REQUESTED, BreakHandlerV2State.getCurrentState());
        Instant breakEnd = (Instant) getField("breakEndTime");
        assertTrue(breakEnd.isAfter(Instant.now().plus(Duration.ofMinutes(9))));
        assertEquals(1, getField("breaksActivatedCount"));
    }

    @Test
    public void stoppingThePluginEndsThePersistedBreak() throws Exception {
        restoreLogoutBreak(false);

        script.shutdown();

        Mockito.verify(configManager).unsetConfiguration(BreakHandlerV2Config.configGroup, "persistedBreakEnd");
        Mockito.verify(configManager).unsetConfiguration(BreakHandlerV2Config.configGroup, "persistedBreakLogout");
        assertNull(getField("breakEndTime"));
        assertFalse((Boolean) getField("logoutBreakActive"));
    }

    private Instant restoreLogoutBreak(boolean playerLoggedIn) throws Exception {
        Instant savedEnd = Instant.ofEpochMilli(Instant.now().plus(Duration.ofMinutes(30)).toEpochMilli());
        when(configManager.getConfiguration(BreakHandlerV2Config.configGroup, "persistedBreakEnd", Long.class))
                .thenReturn(savedEnd.toEpochMilli());
        when(configManager.getConfiguration(BreakHandlerV2Config.configGroup, "persistedBreakLogout", Boolean.class))
                .thenReturn(true);
        loggedIn = playerLoggedIn;

        invoke("restorePersistedBreakState");

        assertEquals(savedEnd, getField("breakEndTime"));
        return savedEnd;
    }

    private ConfigProfile profile() {
        ConfigProfile profile = mock(ConfigProfile.class);
        when(profile.getName()).thenReturn("player");
        when(profile.getPassword()).thenReturn("secret");
        when(profile.getDiscordWebhookUrl()).thenReturn("https://example.invalid/webhook");
        return profile;
    }

    private PlaySchedule findScheduleFurthestFromStarting() {
        PlaySchedule furthest = null;
        for (PlaySchedule schedule : PlaySchedule.values()) {
            if (schedule.isOutsideSchedule()
                    && (furthest == null || schedule.timeUntilNextSchedule().compareTo(furthest.timeUntilNextSchedule()) > 0)) {
                furthest = schedule;
            }
        }
        if (furthest == null || furthest.timeUntilNextSchedule().compareTo(Duration.ofHours(1)) < 0) {
            throw new IllegalStateException("No schedule starts more than an hour from now");
        }
        return furthest;
    }

    private void invokeNotification(boolean logoutBreak) throws Exception {
        Method method = BreakHandlerV2Script.class.getDeclaredMethod("sendBreakStartedNotification", boolean.class);
        method.setAccessible(true);
        method.invoke(script, logoutBreak);
    }

    private void invoke(String name) throws Exception {
        Method method = BreakHandlerV2Script.class.getDeclaredMethod(name);
        method.setAccessible(true);
        method.invoke(script);
    }

    private void setField(String name, Object value) throws Exception {
        Field field = BreakHandlerV2Script.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(script, value);
    }

    private Object getField(String name) throws Exception {
        Field field = BreakHandlerV2Script.class.getDeclaredField(name);
        field.setAccessible(true);
        return field.get(script);
    }
}
