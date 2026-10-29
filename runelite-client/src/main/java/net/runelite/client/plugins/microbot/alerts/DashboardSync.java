package net.runelite.client.plugins.microbot.alerts;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.ClientSessionManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageUtil;
import net.runelite.client.util.RunnableExceptionLogger;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
@Singleton
public class DashboardSync
{
	static final int WAIT_SECONDS = 25;
	static final long FIRST_SYNC_MS = 15_000;
	static final long NEXT_SYNC_MS = 1_000;
	static final long IDLE_RETRY_MS = 30_000;
	static final long FIRST_BACKOFF_MS = 5_000;
	static final long MAX_BACKOFF_MS = 120_000;
	static final long CAPTURE_TIMEOUT_SECONDS = 5;
	static final String SCREENSHOT = "screenshot";
	static final String SCREENSHOT_NOTICE = "The Microbot dashboard took a screenshot of this client.";

	private final MicrobotApi microbotApi;
	private final ClientLinkToken clientLinkToken;
	private final ClientSessionManager clientSessionManager;
	private final MicrobotConfig config;
	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final DrawManager drawManager;
	private final DashboardControl dashboardControl;
	private final ScheduledExecutorService executor;
	private final Gson gson;
	private final XpTracker xpTracker = new XpTracker();
	private final AtomicBoolean capturing = new AtomicBoolean();
	private volatile boolean running;
	private volatile Call inFlight;
	private ScheduledFuture<?> next;
	private int failures;

	@Inject
	DashboardSync(MicrobotApi microbotApi, ClientLinkToken clientLinkToken, ClientSessionManager clientSessionManager, MicrobotConfig config,
		Client client, ClientThread clientThread, ItemManager itemManager, DrawManager drawManager, DashboardControl dashboardControl,
		ScheduledExecutorService executor, Gson gson)
	{
		this.microbotApi = microbotApi;
		this.clientLinkToken = clientLinkToken;
		this.clientSessionManager = clientSessionManager;
		this.config = config;
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.drawManager = drawManager;
		this.dashboardControl = dashboardControl;
		this.executor = executor;
		this.gson = gson;
	}

	public synchronized void start()
	{
		if (running)
		{
			return;
		}
		running = true;
		schedule(FIRST_SYNC_MS);
	}

	public synchronized void stop()
	{
		running = false;
		if (next != null)
		{
			next.cancel(false);
			next = null;
		}
		Call call = inFlight;
		if (call != null)
		{
			call.cancel();
		}
	}

	private synchronized void schedule(long delayMs)
	{
		if (running)
		{
			next = executor.schedule(RunnableExceptionLogger.wrap(this::sync), delayMs, TimeUnit.MILLISECONDS);
		}
	}

	private void sync()
	{
		Optional<String> token = clientLinkToken.get();
		UUID sessionId = clientSessionManager.getMicrobotSessionId();
		if (token.isEmpty() || sessionId == null)
		{
			schedule(IDLE_RETRY_MS);
			return;
		}
		captureState()
			.orTimeout(CAPTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
			.whenCompleteAsync((state, error) ->
			{
				if (error != null)
				{
					log.debug("Unable to read the game state for the dashboard", error);
				}
				send(token.get(), payload(sessionId, config.allowDashboardScreenshots(), config.allowDashboardControl(), error == null ? state : null));
			}, executor);
	}

	private CompletableFuture<JsonObject> captureState()
	{
		CompletableFuture<JsonObject> captured = new CompletableFuture<>();
		clientThread.invoke(() ->
		{
			try
			{
				captured.complete(DashboardGameState.capture(client, itemManager, xpTracker));
			}
			catch (RuntimeException e)
			{
				captured.completeExceptionally(e);
			}
		});
		return captured;
	}

	private synchronized void send(String token, JsonObject payload)
	{
		if (!running)
		{
			return;
		}
		inFlight = microbotApi.syncDashboard(token, payload, WAIT_SECONDS, new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				inFlight = null;
				if (!call.isCanceled())
				{
					log.debug("Dashboard sync failed", e);
					retryLater();
				}
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				inFlight = null;
				int code;
				String body;
				try (response)
				{
					code = response.code();
					ResponseBody responseBody = response.body();
					body = responseBody == null ? "" : responseBody.string();
				}
				catch (IOException e)
				{
					onFailure(call, e);
					return;
				}
				handle(token, code, body);
			}
		});
	}

	private synchronized void handle(String token, int code, String body)
	{
		if (!running)
		{
			return;
		}
		if (code == 401)
		{
			if (clientLinkToken.reject(token))
			{
				log.warn("The alert link token was revoked or is unknown, so bot alerts are off until a new one is set");
			}
			schedule(IDLE_RETRY_MS);
			return;
		}
		if (code >= 300)
		{
			log.debug("Dashboard sync rejected: HTTP {}", code);
			retryLater();
			return;
		}
		failures = 0;
		dispatch(token, commands(gson, body));
		schedule(NEXT_SYNC_MS);
	}

	void dispatch(String token, List<Command> commands)
	{
		commands.stream()
			.filter(command -> SCREENSHOT.equals(command.getType()))
			.findFirst()
			.ifPresent(command -> takeScreenshot(token, command.getId()));
		commands.stream()
			.filter(command -> !SCREENSHOT.equals(command.getType()))
			.forEach(command -> dashboardControl.run(token, command.getId(), command.getType(), command.getScriptId()));
	}

	private void retryLater()
	{
		failures++;
		schedule(backoff(failures));
	}

	private void takeScreenshot(String token, String requestId)
	{
		if (!config.allowDashboardScreenshots())
		{
			microbotApi.failScreenshot(token, requestId, "disabled");
			return;
		}
		if (!capturing.compareAndSet(false, true))
		{
			return;
		}
		AtomicBoolean answered = new AtomicBoolean();
		drawManager.requestNextFrameListener(image ->
		{
			if (!answered.compareAndSet(false, true))
			{
				return;
			}
			BufferedImage frame = ImageUtil.bufferedImageFromImage(image);
			executor.execute(() -> upload(token, requestId, frame));
		});
		executor.schedule(() ->
		{
			if (answered.compareAndSet(false, true))
			{
				capturing.set(false);
				microbotApi.failScreenshot(token, requestId, "capture_failed");
			}
		}, CAPTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private void upload(String token, String requestId, BufferedImage frame)
	{
		try
		{
			microbotApi.uploadScreenshot(token, requestId, DashboardScreenshot.encode(frame),
				() -> microbotApi.failScreenshot(token, requestId, "upload_failed"));
			notifyPlayer();
		}
		catch (IOException | RuntimeException e)
		{
			log.debug("Unable to encode the dashboard screenshot", e);
			microbotApi.failScreenshot(token, requestId, "capture_failed");
		}
		finally
		{
			capturing.set(false);
		}
	}

	private void notifyPlayer()
	{
		log.info(SCREENSHOT_NOTICE);
		clientThread.invoke(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", SCREENSHOT_NOTICE, null);
			}
		});
	}

	static JsonObject payload(UUID sessionId, boolean screenshotsAllowed, boolean controlAllowed, JsonObject gameState)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("sessionId", sessionId.toString());
		payload.addProperty("screenshotsAllowed", screenshotsAllowed);
		payload.addProperty("controlAllowed", controlAllowed);
		payload.add("gameState", gameState);
		return payload;
	}

	static List<Command> commands(Gson gson, String body)
	{
		List<Command> commands = new ArrayList<>();
		try
		{
			JsonObject json = gson.fromJson(body, JsonObject.class);
			JsonArray entries = json == null ? null : json.getAsJsonArray("commands");
			if (entries == null)
			{
				return commands;
			}
			for (JsonElement element : entries)
			{
				JsonObject command = element.getAsJsonObject();
				commands.add(new Command(UUID.fromString(command.get("id").getAsString()).toString(), command.get("type").getAsString(),
					optionalString(command.get("scriptId"))));
			}
		}
		catch (JsonParseException | IllegalStateException | IllegalArgumentException | NullPointerException | ClassCastException e)
		{
			log.debug("Ignoring an unreadable dashboard sync response", e);
		}
		return commands;
	}

	private static String optionalString(JsonElement element)
	{
		return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString() ? element.getAsString() : null;
	}

	@Value
	static class Command
	{
		String id;
		String type;
		String scriptId;
	}

	static long backoff(int failures)
	{
		int doublings = Math.min(Math.max(failures - 1, 0), 10);
		return Math.min(MAX_BACKOFF_MS, FIRST_BACKOFF_MS << doublings);
	}
}
