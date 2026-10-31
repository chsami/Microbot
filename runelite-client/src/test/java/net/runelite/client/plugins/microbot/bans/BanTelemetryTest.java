package net.runelite.client.plugins.microbot.bans;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameTick;
import net.runelite.client.ClientSessionManager;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.mockito.ArgumentCaptor;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BanTelemetryTest
{
	private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000042");

	@Rule
	public TemporaryFolder folder = new TemporaryFolder();

	private Client client;
	private ClientThread clientThread;
	private MicrobotApi api;
	private ClientSessionManager sessions;
	private BanTelemetry telemetry;
	private final AtomicReference<BanReportMode> mode = new AtomicReference<>(BanReportMode.ASK);
	private final AtomicReference<String> selection = new AtomicReference<>("111");

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		clientThread = mock(ClientThread.class);
		api = mock(MicrobotApi.class);
		sessions = mock(ClientSessionManager.class);
		when(sessions.getMicrobotSessionId()).thenReturn(SESSION);
		when(client.getAccountHash()).thenReturn(-1L);
		ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
		doAnswer(invocation ->
		{
			((Runnable) invocation.getArgument(0)).run();
			return null;
		}).when(executor).execute(any());
		MicrobotConfig config = new MicrobotConfig()
		{
			@Override
			public BanReportMode banReports()
			{
				return mode.get();
			}
		};
		telemetry = new BanTelemetry(client, clientThread, mock(PluginManager.class), mock(MicrobotPluginManager.class), api, sessions,
			executor, config, mock(ConfigManager.class), mock(Notifier.class), new Gson(), false, folder.getRoot().toPath(), selection::get);
	}

	private BooleanSupplier startWatcher()
	{
		telemetry.start();
		ArgumentCaptor<BooleanSupplier> watcher = ArgumentCaptor.forClass(BooleanSupplier.class);
		verify(clientThread).invokeLater(watcher.capture());
		return watcher.getValue();
	}

	private void botted(long accountHash, String identity)
	{
		new BanLedger(folder.getRoot().toPath(), new Gson(), Clock.systemUTC())
			.record(BanLedger.accountKey(accountHash), BanLedger.identityKey(identity).orElse(null), 600, List.of("PluginX"), true, 1200, false);
	}

	private void loggedInAs(long accountHash)
	{
		when(client.getAccountHash()).thenReturn(accountHash);
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		telemetry.onGameTick(new GameTick());
		when(client.getAccountHash()).thenReturn(-1L);
	}

	private void showBanScreen(BooleanSupplier watcher)
	{
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		when(client.getLoginIndex()).thenReturn(BanTelemetry.BANNED_LOGIN_INDEX);
		watcher.getAsBoolean();
	}

	private void leaveBanScreen(BooleanSupplier watcher)
	{
		when(client.getLoginIndex()).thenReturn(2);
		watcher.getAsBoolean();
	}

	@Test
	public void aRelogThatReselectsTheSameCharacterReportsThatAccount()
	{
		mode.set(BanReportMode.ALWAYS);
		when(api.submitBanReport(any())).thenReturn(CompletableFuture.completedFuture(true));
		botted(7, "jagex|111");
		BooleanSupplier watcher = startWatcher();
		loggedInAs(7);
		selection.set("111");

		showBanScreen(watcher);
		watcher.getAsBoolean();
		leaveBanScreen(watcher);
		showBanScreen(watcher);

		ArgumentCaptor<JsonObject> payload = ArgumentCaptor.forClass(JsonObject.class);
		verify(api, times(1)).submitBanReport(payload.capture());
		assertEquals(BanLedger.accountKey(7), payload.getValue().get("accountKey").getAsString());
		assertEquals("unknown", payload.getValue().get("banKind").getAsString());
		assertEquals(SESSION.toString(), payload.getValue().get("sessionId").getAsString());
	}

	@Test
	public void aBanAfterSwitchingToAnotherCharacterIsNotBlamedOnTheLastOne()
	{
		mode.set(BanReportMode.ALWAYS);
		when(api.submitBanReport(any())).thenReturn(CompletableFuture.completedFuture(true));
		botted(7, "jagex|111");
		BooleanSupplier watcher = startWatcher();
		loggedInAs(7);
		selection.set("222");

		showBanScreen(watcher);

		verify(api, never()).submitBanReport(any());
	}

	@Test
	public void aFreshClientFindsTheAccountThroughTheLedger()
	{
		mode.set(BanReportMode.ALWAYS);
		when(api.submitBanReport(any())).thenReturn(CompletableFuture.completedFuture(true));
		botted(9, "jagex|222");
		selection.set("222");
		BooleanSupplier watcher = startWatcher();

		showBanScreen(watcher);

		ArgumentCaptor<JsonObject> payload = ArgumentCaptor.forClass(JsonObject.class);
		verify(api).submitBanReport(payload.capture());
		assertEquals(BanLedger.accountKey(9), payload.getValue().get("accountKey").getAsString());
	}

	@Test
	public void neverSendSendsNothing()
	{
		mode.set(BanReportMode.NEVER);
		botted(7, "jagex|111");
		BooleanSupplier watcher = startWatcher();
		loggedInAs(7);

		showBanScreen(watcher);

		verify(api, never()).submitBanReport(any());
	}

	@Test
	public void anAccountWithoutBottingHistoryIsNotReported()
	{
		mode.set(BanReportMode.ALWAYS);
		BooleanSupplier watcher = startWatcher();
		loggedInAs(7);

		showBanScreen(watcher);

		verify(api, never()).submitBanReport(any());
	}

	@Test
	public void choosingNeverDropsRuntimeThatWasWaiting()
	{
		when(api.submitPluginRuntime(any())).thenReturn(CompletableFuture.completedFuture(true));
		telemetry.addPendingRuntime(Map.of("PluginX", 600L));
		mode.set(BanReportMode.NEVER);

		telemetry.flushRuntime();
		mode.set(BanReportMode.ASK);
		telemetry.flushRuntime();

		verify(api, never()).submitPluginRuntime(any());
	}

	@Test
	public void flushIsDoneOnlyWhenTheUploadIsDone()
	{
		CompletableFuture<Boolean> upload = new CompletableFuture<>();
		when(api.submitPluginRuntime(any())).thenReturn(upload);
		telemetry.addPendingRuntime(Map.of("PluginX", 600L));

		CompletableFuture<?> flush = telemetry.flushRuntime();

		assertFalse(flush.isDone());
		upload.complete(true);
		assertTrue(flush.isDone());
	}

	@Test
	public void aFailedUploadIsSentAgainNextTime()
	{
		when(api.submitPluginRuntime(any())).thenReturn(CompletableFuture.completedFuture(false), CompletableFuture.completedFuture(true));
		telemetry.addPendingRuntime(Map.of("PluginX", 600L));

		telemetry.flushRuntime();
		telemetry.flushRuntime();
		telemetry.flushRuntime();

		ArgumentCaptor<JsonObject> payload = ArgumentCaptor.forClass(JsonObject.class);
		verify(api, times(2)).submitPluginRuntime(payload.capture());
		JsonObject retry = payload.getAllValues().get(1);
		assertEquals(SESSION.toString(), retry.get("sessionId").getAsString());
		assertEquals(600, retry.getAsJsonArray("plugins").get(0).getAsJsonObject().get("seconds").getAsLong());
	}

	@Test
	public void runtimeWaitsForASession()
	{
		when(sessions.getMicrobotSessionId()).thenReturn(null, SESSION);
		when(api.submitPluginRuntime(any())).thenReturn(CompletableFuture.completedFuture(true));
		telemetry.addPendingRuntime(Map.of("PluginX", 600L));

		assertTrue(telemetry.flushRuntime().isDone());
		verify(api, never()).submitPluginRuntime(any());
		telemetry.flushRuntime();
		verify(api).submitPluginRuntime(any());
	}

	@Test
	public void pendingRuntimeIsCappedPerPlugin()
	{
		when(api.submitPluginRuntime(any())).thenReturn(CompletableFuture.completedFuture(true));
		telemetry.addPendingRuntime(Map.of("PluginX", BanTelemetry.MAX_PENDING_SECONDS));
		telemetry.addPendingRuntime(Map.of("PluginX", 600L));

		telemetry.flushRuntime();

		ArgumentCaptor<JsonObject> payload = ArgumentCaptor.forClass(JsonObject.class);
		verify(api).submitPluginRuntime(payload.capture());
		assertEquals(BanTelemetry.MAX_PENDING_SECONDS, payload.getValue().getAsJsonArray("plugins").get(0).getAsJsonObject().get("seconds").getAsLong());
	}

	@Test
	public void theLoginScreenWatcherSurvivesErrorsAndStopsWithTheService()
	{
		BooleanSupplier watcher = startWatcher();
		when(client.getGameState()).thenThrow(new IllegalStateException("not ready"));

		assertFalse(watcher.getAsBoolean());

		telemetry.stop();
		assertTrue(watcher.getAsBoolean());
	}

	@Test
	public void restartingDoesNotKeepTheOldWatcher()
	{
		telemetry.start();
		telemetry.stop();
		telemetry.start();
		ArgumentCaptor<BooleanSupplier> watchers = ArgumentCaptor.forClass(BooleanSupplier.class);
		verify(clientThread, times(2)).invokeLater(watchers.capture());
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);

		assertTrue(watchers.getAllValues().get(0).getAsBoolean());
		assertFalse(watchers.getAllValues().get(1).getAsBoolean());
	}
}
