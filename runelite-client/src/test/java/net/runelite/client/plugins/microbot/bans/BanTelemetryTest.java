package net.runelite.client.plugins.microbot.bans;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.BooleanSupplier;
import net.runelite.api.Client;
import net.runelite.api.GameState;
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

	@Before
	public void setUp()
	{
		client = mock(Client.class);
		clientThread = mock(ClientThread.class);
		api = mock(MicrobotApi.class);
		sessions = mock(ClientSessionManager.class);
		when(sessions.getMicrobotSessionId()).thenReturn(SESSION);
		telemetry = new BanTelemetry(client, clientThread, mock(PluginManager.class), mock(MicrobotPluginManager.class), api, sessions,
			mock(ScheduledExecutorService.class), new MicrobotConfig() { }, mock(ConfigManager.class), mock(Notifier.class), new Gson(),
			false, folder.getRoot().toPath());
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
		telemetry.start();
		ArgumentCaptor<BooleanSupplier> watcher = ArgumentCaptor.forClass(BooleanSupplier.class);
		verify(clientThread).invokeLater(watcher.capture());
		when(client.getGameState()).thenThrow(new IllegalStateException("not ready"));

		assertFalse(watcher.getValue().getAsBoolean());

		telemetry.stop();
		assertTrue(watcher.getValue().getAsBoolean());
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
