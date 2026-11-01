package net.runelite.client.plugins.microbot.alerts;

import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.ClientSessionManager;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.LogoutRequested;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class ClientAlertReporterTest
{
	@PluginDescriptor(name = PluginDescriptor.Mocrosoft + "Fake Fisher", isExternal = true, version = "2.1.0")
	static class FakeScript extends Plugin
	{
	}

	@PluginDescriptor(name = "Break Handler")
	static class BuiltInPlugin extends Plugin
	{
	}

	private static final String TOKEN = ClientLinkTokenTest.CONFIG_TOKEN;
	private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000007");
	private static final String SCRIPT_LOGGER = FakeScript.class.getPackageName() + ".FishingScript";

	private final FakeScript script = new FakeScript();
	private MicrobotApi api;
	private ClientLinkToken token;
	private ClientSessionManager sessions;
	private PluginManager pluginManager;
	private ScheduledExecutorService executor;
	private Client client;
	private ClientAlertReporter reporter;

	@Before
	public void setUp()
	{
		api = mock(MicrobotApi.class);
		token = mock(ClientLinkToken.class);
		when(token.get()).thenReturn(Optional.of(TOKEN));
		sessions = mock(ClientSessionManager.class);
		when(sessions.getMicrobotSessionId()).thenReturn(SESSION);
		pluginManager = mock(PluginManager.class);
		BuiltInPlugin builtIn = new BuiltInPlugin();
		when(pluginManager.getPlugins()).thenReturn(List.of(script, builtIn));
		when(pluginManager.isPluginActive(script)).thenReturn(true);
		when(pluginManager.isPluginActive(builtIn)).thenReturn(true);
		MicrobotPluginManager microbotPluginManager = mock(MicrobotPluginManager.class);
		when(microbotPluginManager.getInstalledPluginVersion(anyString())).thenReturn(Optional.empty());
		client = mock(Client.class);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		executor = mock(ScheduledExecutorService.class);
		doAnswer(invocation ->
		{
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(executor).execute(any());
		doAnswer(invocation ->
		{
			invocation.<Runnable>getArgument(0).run();
			return CompletableFuture.completedFuture(null);
		}).when(executor).submit(any(Runnable.class));
		reporter = new ClientAlertReporter(api, token, sessions, pluginManager, microbotPluginManager, client, executor);
	}

	private List<JsonObject> sent()
	{
		ArgumentCaptor<JsonObject> captor = ArgumentCaptor.forClass(JsonObject.class);
		verify(api, atLeastOnce()).sendAlertEvent(any(), captor.capture(), any());
		return captor.getAllValues();
	}

	private List<JsonObject> sentOfType(String type)
	{
		return sent().stream().filter(json -> type.equals(json.get("type").getAsString())).collect(Collectors.toList());
	}

	@Test
	public void scriptStartReportsDisplayNameVersionAndRunningScripts()
	{
		reporter.onPluginChanged(new PluginChanged(script, true));

		JsonObject event = sentOfType(AlertEvent.SCRIPT_STARTED).get(0);
		assertEquals(SESSION.toString(), event.get("sessionId").getAsString());
		assertEquals("Fake Fisher", event.get("plugin").getAsString());
		assertEquals("2.1.0", event.get("pluginVersion").getAsString());
		assertEquals(1, event.getAsJsonArray("runningScripts").size());
		assertEquals("Fake Fisher", event.getAsJsonArray("runningScripts").get(0).getAsString());
		assertTrue(event.get("loggedIn").getAsBoolean());
		verify(api).sendAlertEvent(eq(TOKEN), any(), any());
	}

	@Test
	public void reportsListInstalledScriptsButNotBuiltInPlugins()
	{
		when(pluginManager.isPluginActive(script)).thenReturn(false);

		reporter.onPluginChanged(new PluginChanged(script, true));

		JsonObject event = sentOfType(AlertEvent.SCRIPT_STARTED).get(0);
		assertEquals(0, event.getAsJsonArray("runningScripts").size());
		assertEquals(1, event.getAsJsonArray("availableScripts").size());
		JsonObject available = event.getAsJsonArray("availableScripts").get(0).getAsJsonObject();
		assertEquals(FakeScript.class.getName(), available.get("id").getAsString());
		assertEquals("Fake Fisher", available.get("name").getAsString());
	}

	@Test
	public void builtInPluginsAreNotScripts()
	{
		reporter.onPluginChanged(new PluginChanged(new BuiltInPlugin(), false));
		verify(api, never()).sendAlertEvent(any(), any(), any());
	}

	@Test
	public void stopRightAfterAnErrorFromTheScriptHasReasonError()
	{
		reporter.onScriptError(SCRIPT_LOGGER, List.of(), "Loop failed (IllegalStateException: bank closed)");
		reporter.onPluginChanged(new PluginChanged(script, false));

		JsonObject error = sentOfType(AlertEvent.SCRIPT_ERROR).get(0);
		assertEquals("Fake Fisher", error.get("plugin").getAsString());
		assertEquals("Loop failed (IllegalStateException: bank closed)", error.get("message").getAsString());

		JsonObject stopped = sentOfType(AlertEvent.SCRIPT_STOPPED).get(0);
		assertEquals(ScriptStopReason.ERROR, stopped.get("reason").getAsString());
		assertEquals("Loop failed (IllegalStateException: bank closed)", stopped.get("message").getAsString());
	}

	@Test
	public void errorAttributedThroughStackFrames()
	{
		reporter.onScriptError("net.runelite.client.plugins.microbot.Microbot",
			List.of("java.util.ArrayList.get:427", SCRIPT_LOGGER + ".run:88"), "FishingScript (IndexOutOfBoundsException)");

		assertEquals(1, sentOfType(AlertEvent.SCRIPT_ERROR).size());
	}

	@Test
	public void stopWithoutRecentErrorHasReasonUnknown()
	{
		reporter.onPluginChanged(new PluginChanged(script, false));

		JsonObject stopped = sentOfType(AlertEvent.SCRIPT_STOPPED).get(0);
		assertEquals(ScriptStopReason.UNKNOWN, stopped.get("reason").getAsString());
		assertTrue(stopped.get("message").isJsonNull());
	}

	@Test
	public void repeatedErrorsFromOneScriptAreSentOnce()
	{
		for (int i = 0; i < 5; i++)
		{
			reporter.onScriptError(SCRIPT_LOGGER, List.of(), "Loop failed");
		}
		verify(api, times(1)).sendAlertEvent(any(), any(), any());
	}

	@Test
	public void errorsOutsideMicrobotScriptsAreNotSent()
	{
		reporter.onScriptError("net.runelite.client.plugins.gpu.GpuPlugin", List.of("net.runelite.client.plugins.gpu.GpuPlugin.startUp:12"), "GL error");
		verify(api, never()).sendAlertEvent(any(), any(), any());
	}

	@Test
	public void logoutFromTheGameReportsLoggedOut()
	{
		reporter.onGameStateChanged(gameState(GameState.LOGGED_IN));
		reporter.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));

		JsonObject event = sentOfType(AlertEvent.LOGGED_OUT).get(0);
		assertFalse(event.get("loggedIn").getAsBoolean());
		assertFalse(event.get("intended").getAsBoolean());
		assertEquals(1, event.getAsJsonArray("runningScripts").size());
	}

	@Test
	public void logoutRequestedThroughTheLogoutHelpersIsIntended()
	{
		reporter.onGameStateChanged(gameState(GameState.LOGGED_IN));
		reporter.onLogoutRequested(new LogoutRequested());
		reporter.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));

		assertTrue(sentOfType(AlertEvent.LOGGED_OUT).get(0).get("intended").getAsBoolean());
	}

	@Test
	public void theLogoutHelperAnnouncesTheRequestOnlyWhenLoggedIn()
	{
		EventBus bus = mock(EventBus.class);
		AtomicBoolean loggedIn = new AtomicBoolean(true);
		try (var microbot = mockStatic(Microbot.class);
			var keyboard = mockStatic(Rs2Keyboard.class);
			var tab = mockStatic(Rs2Tab.class);
			var widget = mockStatic(Rs2Widget.class))
		{
			microbot.when(Microbot::getEventBus).thenReturn(bus);
			microbot.when(Microbot::getClient).thenReturn(mock(Client.class));
			microbot.when(Microbot::isLoggedIn).thenAnswer(call -> loggedIn.get());

			Rs2Player.logout();
			verify(bus, times(1)).post(any(LogoutRequested.class));

			loggedIn.set(false);
			Rs2Player.logout();
			verify(bus, times(1)).post(any(LogoutRequested.class));
		}
	}

	@Test
	public void aLogoutRequestCoversOnlyTheNextLogout()
	{
		reporter.onGameStateChanged(gameState(GameState.LOGGED_IN));
		reporter.onLogoutRequested(new LogoutRequested());
		reporter.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
		reporter.onGameStateChanged(gameState(GameState.LOGGING_IN));
		reporter.onGameStateChanged(gameState(GameState.LOGGED_IN));
		reporter.onGameStateChanged(gameState(GameState.CONNECTION_LOST));
		reporter.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));

		List<JsonObject> logouts = sentOfType(AlertEvent.LOGGED_OUT);
		assertEquals(2, logouts.size());
		assertTrue(logouts.get(0).get("intended").getAsBoolean());
		assertFalse(logouts.get(1).get("intended").getAsBoolean());
	}

	@Test
	public void aLogoutRequestOnlyCountsWithinTheWindow()
	{
		long requestedAt = 1_000_000;
		assertTrue(ClientAlertReporter.isIntendedLogout(requestedAt, requestedAt));
		assertTrue(ClientAlertReporter.isIntendedLogout(requestedAt, requestedAt + ClientAlertReporter.INTENDED_LOGOUT_WINDOW_MS));
		assertFalse(ClientAlertReporter.isIntendedLogout(requestedAt, requestedAt + ClientAlertReporter.INTENDED_LOGOUT_WINDOW_MS + 1));
		assertFalse(ClientAlertReporter.isIntendedLogout(requestedAt, requestedAt - 1));
		assertFalse(ClientAlertReporter.isIntendedLogout(0, requestedAt));
	}

	@Test
	public void relogAfterALogoutSendsALoggedInHeartbeatRightAway()
	{
		reporter.onGameStateChanged(gameState(GameState.LOGGED_IN));
		reporter.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
		reporter.onGameStateChanged(gameState(GameState.LOGGING_IN));
		reporter.onGameStateChanged(gameState(GameState.LOADING));
		reporter.onGameStateChanged(gameState(GameState.LOGGED_IN));
		reporter.onGameStateChanged(gameState(GameState.LOADING));
		reporter.onGameStateChanged(gameState(GameState.LOGGED_IN));

		List<JsonObject> heartbeats = sentOfType(AlertEvent.HEARTBEAT);
		assertEquals(1, heartbeats.size());
		assertTrue(heartbeats.get(0).get("loggedIn").getAsBoolean());
		assertFalse(heartbeats.get(0).get("intended").getAsBoolean());
	}

	@Test
	public void loginWithoutAPriorLogoutSendsNoHeartbeat()
	{
		reporter.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
		reporter.onGameStateChanged(gameState(GameState.LOGGING_IN));
		reporter.onGameStateChanged(gameState(GameState.LOGGED_IN));
		reporter.onGameStateChanged(gameState(GameState.HOPPING));
		reporter.onGameStateChanged(gameState(GameState.LOGGED_IN));

		verify(api, never()).sendAlertEvent(any(), any(), any());
	}

	@Test
	public void onlyLeavingTheGameCountsAsLogout()
	{
		assertTrue(ClientAlertReporter.isLogout(GameState.LOGGED_IN, GameState.LOGIN_SCREEN));
		assertTrue(ClientAlertReporter.isLogout(GameState.CONNECTION_LOST, GameState.LOGIN_SCREEN));
		assertTrue(ClientAlertReporter.isLogout(GameState.HOPPING, GameState.LOGIN_SCREEN));
		assertFalse(ClientAlertReporter.isLogout(null, GameState.LOGIN_SCREEN));
		assertFalse(ClientAlertReporter.isLogout(GameState.LOGGING_IN, GameState.LOGIN_SCREEN));
		assertFalse(ClientAlertReporter.isLogout(GameState.LOGGED_IN, GameState.HOPPING));
		assertFalse(ClientAlertReporter.isLogout(GameState.LOGGED_IN, GameState.CONNECTION_LOST));
	}

	@Test
	public void nothingIsSentWithoutAToken()
	{
		when(token.get()).thenReturn(Optional.empty());
		reporter.onPluginChanged(new PluginChanged(script, true));
		reporter.onGameStateChanged(gameState(GameState.LOGGED_IN));
		reporter.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
		assertFalse(reporter.isActive());
		verify(api, never()).sendAlertEvent(any(), any(), any());
	}

	@Test
	public void nothingIsSentAfterClientShutdown()
	{
		reporter.onClientShutdown(new ClientShutdown());
		reporter.onPluginChanged(new PluginChanged(script, false));
		verify(api, never()).sendAlertEvent(any(), any(), any());
	}

	@Test
	public void heartbeatRunsWhenTelemetryIsTurnedOnAfterStartup()
	{
		when(token.isTelemetryDisabled()).thenReturn(true);
		reporter.start();
		ArgumentCaptor<Runnable> heartbeat = ArgumentCaptor.forClass(Runnable.class);
		verify(executor).scheduleWithFixedDelay(heartbeat.capture(), eq(ClientAlertReporter.FIRST_HEARTBEAT_SECONDS),
			eq(ClientAlertReporter.HEARTBEAT_SECONDS), eq(TimeUnit.SECONDS));

		heartbeat.getValue().run();
		assertEquals(1, sentOfType(AlertEvent.HEARTBEAT).size());
		assertEquals(FakeScript.class.getName(), sentOfType(AlertEvent.HEARTBEAT).get(0)
			.getAsJsonArray("availableScripts").get(0).getAsJsonObject().get("id").getAsString());
	}

	@Test
	public void aNewTokenSendsAHeartbeatRightAway()
	{
		ConfigChanged other = new ConfigChanged();
		other.setGroup(MicrobotConfig.configGroup);
		other.setKey(MicrobotConfig.keyInputIdleResumeMs);
		reporter.onConfigChanged(other);
		verify(api, never()).sendAlertEvent(any(), any(), any());

		ConfigChanged linked = new ConfigChanged();
		linked.setGroup(MicrobotConfig.configGroup);
		linked.setKey(MicrobotConfig.keyAlertLinkToken);
		reporter.onConfigChanged(linked);
		assertEquals(1, sentOfType(AlertEvent.HEARTBEAT).size());

		when(token.get()).thenReturn(Optional.empty());
		reporter.onConfigChanged(linked);
		assertEquals(1, sentOfType(AlertEvent.HEARTBEAT).size());
	}

	@Test
	public void eventsStayOnTheLastSessionUntilANewOneOpensThenTheOldOneIsClosed() throws IOException
	{
		UUID next = UUID.fromString("00000000-0000-0000-0000-000000000008");
		reporter.onPluginChanged(new PluginChanged(script, true));
		when(sessions.getMicrobotSessionId()).thenReturn(null);
		reporter.onPluginChanged(new PluginChanged(script, false));
		verify(api, never()).microbotDelete(any());

		when(sessions.getMicrobotSessionId()).thenReturn(next);
		reporter.onPluginChanged(new PluginChanged(script, true));

		verify(api).microbotDelete(SESSION);
		assertEquals(List.of(SESSION.toString(), SESSION.toString(), next.toString()),
			sent().stream().map(json -> json.get("sessionId").getAsString()).collect(Collectors.toList()));
	}

	@Test
	public void closingTheClientClosesALinkedSessionTheSessionManagerNoLongerTracks() throws IOException
	{
		reporter.onPluginChanged(new PluginChanged(script, true));
		when(sessions.getMicrobotSessionId()).thenReturn(null);

		reporter.onClientShutdown(new ClientShutdown());
		verify(api).microbotDelete(SESSION);
	}

	@Test
	public void closingTheClientLeavesTheCurrentSessionToTheSessionManager() throws IOException
	{
		reporter.onPluginChanged(new PluginChanged(script, true));
		reporter.onClientShutdown(new ClientShutdown());
		verify(api, never()).microbotDelete(any());
	}

	@Test
	public void anonymousClientsNeverCloseSessions() throws IOException
	{
		when(token.get()).thenReturn(Optional.empty());
		reporter.onPluginChanged(new PluginChanged(script, true));
		when(sessions.getMicrobotSessionId()).thenReturn(null);
		reporter.onClientShutdown(new ClientShutdown());
		verify(api, never()).microbotDelete(any());
		verify(executor, never()).submit(any(Runnable.class));
	}

	@Test
	public void unauthorizedResponseRejectsTheToken()
	{
		reporter.onPluginChanged(new PluginChanged(script, true));
		ArgumentCaptor<Runnable> onUnauthorized = ArgumentCaptor.forClass(Runnable.class);
		verify(api).sendAlertEvent(any(), any(), onUnauthorized.capture());

		onUnauthorized.getValue().run();
		verify(token).reject(TOKEN);
	}

	@Test
	public void scriptNamesDropAuthorPrefixAndTags()
	{
		assertEquals("Fake Fisher", ClientAlertReporter.scriptName(script));
		assertTrue(ClientAlertReporter.isScript(script));
		assertFalse(ClientAlertReporter.isScript(new BuiltInPlugin()));
		assertNull(ClientAlertReporter.attribute(
			new ClientAlertReporter.ScriptError(0, "other.Logger", List.of(), "x"), List.of(script)));
	}

	@Test
	public void deathOfTheLocalPlayerReportsDied()
	{
		Player player = mock(Player.class);
		when(client.getLocalPlayer()).thenReturn(player);
		reporter.onActorDeath(new ActorDeath(mock(NPC.class)));
		reporter.onActorDeath(new ActorDeath(mock(Player.class)));
		verify(api, never()).sendAlertEvent(any(), any(), any());

		reporter.onActorDeath(new ActorDeath(player));

		JsonObject event = sentOfType(AlertEvent.DIED).get(0);
		assertEquals(1, event.getAsJsonArray("runningScripts").size());
		assertEquals(1, sent().size());
	}

	@Test
	public void theDisabledAccountLoginScreenReportsBannedOncePerLogin()
	{
		runScheduledTasksRightAway();
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(client.getLoginIndex()).thenReturn(ClientAlertReporter.BANNED_LOGIN_INDEX);

		reporter.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
		reporter.onGameStateChanged(gameState(GameState.LOGGING_IN));
		reporter.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));

		JsonObject event = sentOfType(AlertEvent.BANNED).get(0);
		assertFalse(event.get("loggedIn").getAsBoolean());
		assertEquals(1, sentOfType(AlertEvent.BANNED).size());

		reporter.onGameStateChanged(gameState(GameState.LOGGED_IN));
		reporter.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
		assertEquals(2, sentOfType(AlertEvent.BANNED).size());
	}

	@Test
	public void aSecondDisabledAccountOnTheSameClientIsReportedToo()
	{
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(client.getLoginIndex()).thenReturn(ClientAlertReporter.BANNED_LOGIN_INDEX);
		when(client.getUsername()).thenReturn("first");
		reporter.checkBanned();
		reporter.checkBanned();
		when(client.getUsername()).thenReturn("second");
		reporter.checkBanned();
		when(client.getUsername()).thenReturn("first");
		reporter.checkBanned();

		assertEquals(2, sentOfType(AlertEvent.BANNED).size());
	}

	@Test
	public void aBanThatCouldNotBeSentIsTriedAgain()
	{
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(client.getLoginIndex()).thenReturn(ClientAlertReporter.BANNED_LOGIN_INDEX);
		when(client.getUsername()).thenReturn("first");
		when(sessions.getMicrobotSessionId()).thenReturn(null);
		reporter.checkBanned();
		verify(api, never()).sendAlertEvent(any(), any(), any());

		when(sessions.getMicrobotSessionId()).thenReturn(SESSION);
		reporter.checkBanned();

		assertEquals(1, sentOfType(AlertEvent.BANNED).size());
	}

	@Test
	public void otherLoginScreensAreNotBans()
	{
		runScheduledTasksRightAway();
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(client.getLoginIndex()).thenReturn(2);

		reporter.onGameStateChanged(gameState(GameState.LOGIN_SCREEN));
		reporter.checkBanned();

		verify(api, never()).sendAlertEvent(any(), any(), any());
	}

	@Test
	public void noBanIsReportedWithoutAToken()
	{
		when(token.get()).thenReturn(Optional.empty());
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(client.getLoginIndex()).thenReturn(ClientAlertReporter.BANNED_LOGIN_INDEX);

		reporter.checkBanned();

		verify(api, never()).sendAlertEvent(any(), any(), any());
	}

	private void runScheduledTasksRightAway()
	{
		doAnswer(invocation ->
		{
			invocation.<Runnable>getArgument(0).run();
			return null;
		}).when(executor).schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
	}

	private static GameStateChanged gameState(GameState state)
	{
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		return event;
	}
}
