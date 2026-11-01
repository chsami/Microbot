package net.runelite.client.plugins.microbot.alerts;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.AccountHashChanged;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.client.ClientSessionManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.ui.DrawManager;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.Protocol;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.stubbing.Answer;

public class DashboardSyncLiveTest
{
	private static final UUID SESSION = UUID.fromString("00000000-0000-0000-0000-000000000042");
	private static final String REQUEST = "6f1c7a8e-1a2b-4c3d-9e8f-0123456789ab";

	private final MicrobotApi api = mock(MicrobotApi.class);
	private final ClientLinkToken linkToken = mock(ClientLinkToken.class);
	private final ClientSessionManager sessions = mock(ClientSessionManager.class);
	private final Client client = mock(Client.class);
	private final ClientThread clientThread = mock(ClientThread.class);
	private final DashboardControl control = mock(DashboardControl.class);
	private final ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
	private final Call longPollCall = mock(Call.class);
	private final List<Map.Entry<Long, Runnable>> scheduled = new ArrayList<>();
	private final List<Callback> longPolls = new ArrayList<>();
	private final List<Callback> liveSyncs = new ArrayList<>();
	private final List<JsonObject> livePayloads = new ArrayList<>();
	private DashboardSync sync;

	@Before
	public void setUp()
	{
		when(linkToken.get()).thenReturn(Optional.of("token"));
		when(sessions.getMicrobotSessionId()).thenReturn(SESSION);
		when(client.getGameState()).thenReturn(GameState.LOGIN_SCREEN);
		Answer<Void> runNow = invocation ->
		{
			((Runnable) invocation.getArgument(0)).run();
			return null;
		};
		doAnswer(runNow).when(clientThread).invoke(any(Runnable.class));
		doAnswer(runNow).when(executor).execute(any(Runnable.class));
		when(executor.schedule(any(Runnable.class), anyLong(), any(TimeUnit.class))).thenAnswer(invocation ->
		{
			scheduled.add(Map.entry(invocation.getArgument(1), invocation.getArgument(0)));
			return mock(ScheduledFuture.class);
		});
		when(api.syncDashboard(anyString(), any(JsonObject.class), anyInt(), any(Callback.class))).thenAnswer(invocation ->
		{
			if ((int) invocation.getArgument(2) == 0)
			{
				livePayloads.add(invocation.getArgument(1));
				liveSyncs.add(invocation.getArgument(3));
			}
			else
			{
				longPolls.add(invocation.getArgument(3));
			}
			return longPollCall;
		});
		sync = new DashboardSync(api, linkToken, sessions, mock(MicrobotConfig.class), client, clientThread, mock(ItemManager.class),
			mock(DrawManager.class), control, executor, new Gson());
		sync.start();
	}

	@Test
	public void aStatusChangeSendsAFreshSyncAtOnceWhileTheLongPollStaysOpen()
	{
		runScheduled(DashboardSync.FIRST_SYNC_MS);
		assertEquals(1, longPolls.size());

		changed(GameState.LOGIN_SCREEN);

		assertEquals(1, liveSyncs.size());
		assertEquals("LOGIN_SCREEN", status(0));
		verify(longPollCall, never()).cancel();
	}

	@Test
	public void burstsCollapseIntoOneTrailingSyncWithTheLatestState()
	{
		changed(GameState.LOGGING_IN);
		changed(GameState.LOGIN_SCREEN);
		changed(GameState.LOGGING_IN);
		changed(GameState.LOGIN_SCREEN);

		assertEquals(1, liveSyncs.size());
		assertEquals("LOGGING_IN", status(0));

		runScheduled(DashboardSync.LIVE_SYNC_WINDOW_MS);

		assertEquals(2, liveSyncs.size());
		assertEquals("LOGIN_SCREEN", status(1));

		runScheduled(DashboardSync.LIVE_SYNC_WINDOW_MS);
		assertEquals(2, liveSyncs.size());

		changed(GameState.LOGGING_IN);
		assertEquals(3, liveSyncs.size());
	}

	@Test
	public void worldHopsAndRegionLoadsSendNothingExtra()
	{
		changed(GameState.LOGGED_IN);
		sync.onGameTick(new GameTick());
		assertEquals(1, liveSyncs.size());

		for (int i = 0; i < 3; i++)
		{
			if (i != 1)
			{
				changed(GameState.HOPPING);
			}
			changed(GameState.LOADING);
			changed(GameState.LOGGED_IN);
			sync.onGameTick(new GameTick());
			runScheduled(DashboardSync.LIVE_SYNC_WINDOW_MS);
		}
		assertEquals(1, liveSyncs.size());

		changed(GameState.LOGIN_SCREEN);
		assertEquals("LOGIN_SCREEN", status(1));
	}

	@Test
	public void aLongPollCapturedMidHopIsCorrectedOnceBackInGame()
	{
		changed(GameState.LOGGED_IN);
		sync.onGameTick(new GameTick());
		runScheduled(DashboardSync.LIVE_SYNC_WINDOW_MS);
		assertEquals(1, liveSyncs.size());

		changed(GameState.HOPPING);
		runScheduled(DashboardSync.FIRST_SYNC_MS);
		changed(GameState.LOADING);
		changed(GameState.LOGGED_IN);
		assertEquals(1, liveSyncs.size());

		sync.onGameTick(new GameTick());
		assertEquals(2, liveSyncs.size());
		assertEquals("LOGGED_IN", status(1));
	}

	@Test
	public void aFailedLiveCaptureIsRetriedAfterTheWindowWithoutUsingACapSlot()
	{
		when(client.getGameState()).thenThrow(new IllegalStateException("not ready"));
		sync.onGameStateChanged(event(GameState.LOGIN_SCREEN));
		assertEquals(0, scheduled.stream().filter(task -> task.getKey() == DashboardSync.LIVE_SYNC_LIMIT_WINDOW_MS).count());

		doReturn(GameState.LOGIN_SCREEN).when(client).getGameState();
		runScheduled(DashboardSync.LIVE_SYNC_WINDOW_MS);
		assertEquals(1, liveSyncs.size());
		assertEquals("LOGIN_SCREEN", status(0));
		assertEquals(1, scheduled.stream().filter(task -> task.getKey() == DashboardSync.LIVE_SYNC_LIMIT_WINDOW_MS).count());

		runScheduled(DashboardSync.LIVE_SYNC_WINDOW_MS);
		doThrow(new IllegalStateException("not ready")).when(client).getGameState();
		sync.onGameStateChanged(event(GameState.LOGGING_IN));
		runScheduled(DashboardSync.LIVE_SYNC_WINDOW_MS);
		assertEquals(1, scheduled.stream().filter(task -> task.getKey() == DashboardSync.LIVE_SYNC_WINDOW_MS).count());

		runScheduled(DashboardSync.LIVE_SYNC_WINDOW_MS);
		assertEquals(0, scheduled.stream().filter(task -> task.getKey() == DashboardSync.LIVE_SYNC_WINDOW_MS).count());
		assertEquals(1, liveSyncs.size());
	}

	@Test
	public void connectionLossesAreSentOnlyOnceTheyLastOrEndAtTheLoginScreen()
	{
		changed(GameState.LOGGED_IN);
		sync.onGameTick(new GameTick());
		runScheduled(DashboardSync.LIVE_SYNC_WINDOW_MS);
		assertEquals(1, liveSyncs.size());

		changed(GameState.CONNECTION_LOST);
		changed(GameState.LOGGED_IN);
		sync.onGameTick(new GameTick());
		runScheduled(DashboardSync.CONNECTION_LOST_HOLD_MS);
		assertEquals(1, liveSyncs.size());

		changed(GameState.CONNECTION_LOST);
		changed(GameState.LOGIN_SCREEN);
		runScheduled(DashboardSync.CONNECTION_LOST_HOLD_MS);
		assertEquals(2, liveSyncs.size());
		assertEquals("LOGIN_SCREEN", status(1));

		runScheduled(DashboardSync.LIVE_SYNC_WINDOW_MS);
		changed(GameState.CONNECTION_LOST);
		assertEquals(2, liveSyncs.size());
		runScheduled(DashboardSync.CONNECTION_LOST_HOLD_MS);
		assertEquals(3, liveSyncs.size());
		assertEquals("CONNECTION_LOST", status(2));
	}

	@Test
	public void loggingInWaitsForTheNextTickAndLoadingIsIgnored()
	{
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		sync.onGameStateChanged(event(GameState.LOADING));
		sync.onGameStateChanged(event(GameState.LOGGED_IN));
		assertEquals(0, liveSyncs.size());

		sync.onGameTick(new GameTick());
		sync.onGameTick(new GameTick());
		assertEquals(1, liveSyncs.size());

		sync.onAccountHashChanged(new AccountHashChanged());
		sync.onGameTick(new GameTick());
		runScheduled(DashboardSync.LIVE_SYNC_WINDOW_MS);
		assertEquals(2, liveSyncs.size());
		assertEquals("LOGGED_IN", status(1));
	}

	@Test
	public void liveSyncsAreCappedPerTenMinutesAndTheLatestChangeWaitsForRoom()
	{
		for (int i = 0; i < DashboardSync.LIVE_SYNC_LIMIT + 5; i++)
		{
			changed(i % 2 == 0 ? GameState.LOGIN_SCREEN : GameState.LOGGING_IN);
			runScheduled(DashboardSync.LIVE_SYNC_WINDOW_MS);
		}
		assertEquals(DashboardSync.LIVE_SYNC_LIMIT, liveSyncs.size());

		runScheduled(DashboardSync.LIVE_SYNC_LIMIT_WINDOW_MS);

		assertEquals(DashboardSync.LIVE_SYNC_LIMIT + 1, liveSyncs.size());
		assertEquals("LOGIN_SCREEN", status(DashboardSync.LIVE_SYNC_LIMIT));

		runScheduled(DashboardSync.LIVE_SYNC_LIMIT_WINDOW_MS);
		assertEquals(DashboardSync.LIVE_SYNC_LIMIT + 1, liveSyncs.size());
	}

	@Test
	public void noLiveSyncWithoutTokenOrSessionOrWhileBackingOff() throws Exception
	{
		when(linkToken.get()).thenReturn(Optional.empty());
		changed(GameState.LOGIN_SCREEN);

		when(linkToken.get()).thenReturn(Optional.of("token"));
		when(sessions.getMicrobotSessionId()).thenReturn(null);
		changed(GameState.LOGGING_IN);

		when(sessions.getMicrobotSessionId()).thenReturn(SESSION);
		runScheduled(DashboardSync.FIRST_SYNC_MS);
		longPolls.get(0).onFailure(longPollCall, new IOException("offline"));
		changed(GameState.LOGIN_SCREEN);

		assertEquals(0, liveSyncs.size());
	}

	@Test
	public void noLiveSyncOnceStopped()
	{
		sync.stop();
		changed(GameState.LOGIN_SCREEN);

		assertEquals(0, liveSyncs.size());
	}

	@Test
	public void commandsFromTheLiveSyncAreDispatchedOnce() throws Exception
	{
		changed(GameState.LOGIN_SCREEN);

		liveSyncs.get(0).onResponse(mock(Call.class), response(200, "{\"commands\":[{\"id\":\"" + REQUEST + "\",\"type\":\"stop_scripts\"}]}"));

		verify(control, times(1)).run("token", REQUEST, "stop_scripts", null);
		verify(linkToken, never()).reject(anyString());
	}

	@Test
	public void anUnauthorizedLiveSyncRejectsTheToken() throws Exception
	{
		changed(GameState.LOGIN_SCREEN);

		liveSyncs.get(0).onResponse(mock(Call.class), response(401, ""));

		verify(linkToken).reject("token");
		verify(control, never()).run(anyString(), anyString(), anyString(), any());
	}

	private void changed(GameState state)
	{
		when(client.getGameState()).thenReturn(state);
		sync.onGameStateChanged(event(state));
	}

	private static GameStateChanged event(GameState state)
	{
		GameStateChanged event = new GameStateChanged();
		event.setGameState(state);
		return event;
	}

	private String status(int index)
	{
		return livePayloads.get(index).getAsJsonObject("gameState").get("status").getAsString();
	}

	private void runScheduled(long delayMs)
	{
		scheduled.stream().filter(task -> task.getKey() == delayMs).findFirst().ifPresent(task ->
		{
			scheduled.remove(task);
			task.getValue().run();
		});
	}

	private static Response response(int code, String body)
	{
		return new Response.Builder()
			.request(new Request.Builder().url("http://localhost/").build())
			.protocol(Protocol.HTTP_1_1)
			.code(code)
			.message("status")
			.body(ResponseBody.create(MediaType.get("application/json"), body))
			.build();
	}
}
