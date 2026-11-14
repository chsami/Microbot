package net.runelite.client.plugins.microbot.mouserecorder;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

@Slf4j
final class InputUploader
{
	static final String TOKEN_HEADER = "X-Microbot-Client-Token";
	static final int MAX_QUEUED = 40;
	static final int RECORDER_VERSION = 2;
	static final long LIMIT_RETRY_MS = 30 * 60_000L;

	private static final MediaType GZIP = MediaType.parse("application/gzip");
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final long MAX_BACKOFF_MS = 60_000;

	enum State
	{
		IDLE,
		UPLOADING,
		RETRYING,
		NOT_LINKED,
		LINK_REMOVED,
		DAILY_LIMIT
	}

	static final class SessionInfo
	{
		final Instant startedAt;
		final UUID clientSessionId;
		final String clientVersion;
		final JsonObject meta;
		final String token;

		SessionInfo(Instant startedAt, UUID clientSessionId, String clientVersion, JsonObject meta, String token)
		{
			this.startedAt = startedAt;
			this.clientSessionId = clientSessionId;
			this.clientVersion = clientVersion;
			this.meta = meta;
			this.token = token;
		}
	}

	private static final class Generation
	{
		final SessionInfo info;
		String serverId;
		String token;
		int nextSeq;
		boolean dead;

		Generation(SessionInfo info)
		{
			this.info = info;
			this.token = info.token;
		}
	}

	private static final class Item
	{
		final Generation generation;
		final boolean close;
		private String text;
		private byte[] gzip;

		Item(Generation generation, String text, boolean close)
		{
			this.generation = generation;
			this.text = text;
			this.close = close;
		}

		synchronized byte[] gzip()
		{
			if (gzip == null && text != null)
			{
				gzip = InputChunker.gzip(text);
				text = null;
			}
			return gzip;
		}
	}

	private final OkHttpClient http;
	private final Gson gson;
	private final Supplier<Optional<String>> token;
	private final Consumer<String> onRejected;
	private final String apiUrl;
	private final Deque<Item> queue = new ArrayDeque<>();
	private final AtomicInteger dropped = new AtomicInteger();
	private final AtomicLong uploadedParts = new AtomicLong();
	private ScheduledExecutorService executor;
	private Generation current;
	private volatile State state = State.IDLE;
	private volatile String rejectedToken;
	private volatile long limitUntilMs;
	private long backoffMs;
	private long retryAtMs;

	InputUploader(OkHttpClient http, Gson gson, Supplier<Optional<String>> token, Consumer<String> onRejected, String apiUrl)
	{
		this.http = http;
		this.gson = gson;
		this.token = token;
		this.onRejected = onRejected;
		this.apiUrl = apiUrl.endsWith("/") ? apiUrl.substring(0, apiUrl.length() - 1) : apiUrl;
	}

	synchronized void startWorker()
	{
		if (executor != null)
		{
			return;
		}
		executor = Executors.newSingleThreadScheduledExecutor(r ->
		{
			Thread thread = new Thread(r, "microbot-input-upload");
			thread.setDaemon(true);
			return thread;
		});
		executor.scheduleWithFixedDelay(this::drainSafely, 1, 1, TimeUnit.SECONDS);
	}

	synchronized Future<?> flushAndStop()
	{
		ScheduledExecutorService running = executor;
		executor = null;
		if (running == null)
		{
			return CompletableFuture.completedFuture(null);
		}
		Future<?> last = running.submit(this::drainSafely);
		running.shutdown();
		return last;
	}

	synchronized void beginSession(SessionInfo info)
	{
		current = new Generation(info);
		dropped.set(0);
	}

	synchronized boolean hasSession()
	{
		return current != null;
	}

	synchronized void submit(String text)
	{
		if (current == null || text == null || text.isEmpty())
		{
			return;
		}
		Item item = new Item(current, text, false);
		enqueue(item);
		if (executor != null)
		{
			executor.execute(item::gzip);
		}
	}

	synchronized void endSession()
	{
		if (current == null)
		{
			return;
		}
		enqueue(new Item(current, null, true));
		current = null;
	}

	State state()
	{
		return state;
	}

	int dropped()
	{
		return dropped.get();
	}

	long uploadedParts()
	{
		return uploadedParts.get();
	}

	long limitUntilMs()
	{
		return limitUntilMs;
	}

	boolean isLinked()
	{
		Optional<String> value = token.get();
		return value.isPresent() && !value.get().equals(rejectedToken);
	}

	void retryNow()
	{
		retryAtMs = 0;
		limitUntilMs = 0;
	}

	synchronized int queued()
	{
		return queue.size();
	}

	private void enqueue(Item item)
	{
		if (queue.size() >= MAX_QUEUED)
		{
			Item oldest = queue.pollFirst();
			if (oldest != null && !oldest.close)
			{
				dropped.incrementAndGet();
			}
		}
		queue.addLast(item);
	}

	private synchronized void remove(Item item, boolean countDropped)
	{
		if (queue.remove(item) && countDropped && !item.close)
		{
			dropped.incrementAndGet();
		}
	}

	void drainSafely()
	{
		try
		{
			drain();
		}
		catch (RuntimeException e)
		{
			log.warn("Input upload failed unexpectedly", e);
			backoff();
		}
	}

	void drain()
	{
		while (true)
		{
			Item item;
			synchronized (this)
			{
				item = queue.peekFirst();
			}
			if (item == null)
			{
				if (state == State.UPLOADING || state == State.RETRYING)
				{
					state = State.IDLE;
				}
				return;
			}
			String value = ready();
			if (value == null)
			{
				return;
			}
			if (!send(item, value))
			{
				return;
			}
			remove(item, false);
		}
	}

	private String ready()
	{
		long now = System.currentTimeMillis();
		if (now < limitUntilMs)
		{
			state = State.DAILY_LIMIT;
			return null;
		}
		Optional<String> value = token.get();
		if (value.isEmpty())
		{
			state = rejectedToken != null ? State.LINK_REMOVED : State.NOT_LINKED;
			return null;
		}
		if (value.get().equals(rejectedToken))
		{
			state = State.LINK_REMOVED;
			return null;
		}
		return now >= retryAtMs ? value.get() : null;
	}

	private boolean send(Item item, String value)
	{
		Generation generation = item.generation;
		if (generation.dead || generation.token != null && !generation.token.equals(value))
		{
			remove(item, true);
			return false;
		}
		state = State.UPLOADING;
		try
		{
			if (item.close)
			{
				return close(item, value);
			}
			if (generation.serverId == null && !createSession(item, value))
			{
				return false;
			}
			return putPart(item, value);
		}
		catch (IOException e)
		{
			log.debug("Input upload deferred: {}", e.getMessage());
			return backoff();
		}
	}

	private boolean createSession(Item item, String value) throws IOException
	{
		Generation generation = item.generation;
		JsonObject body = new JsonObject();
		body.addProperty("startedAt", generation.info.startedAt.toString());
		if (generation.info.clientSessionId != null)
		{
			body.addProperty("clientSessionId", generation.info.clientSessionId.toString());
		}
		body.addProperty("clientVersion", generation.info.clientVersion);
		body.addProperty("recorderVersion", RECORDER_VERSION);
		body.add("meta", generation.info.meta);
		Request request = new Request.Builder()
			.url(apiUrl + "/input/sessions")
			.header(TOKEN_HEADER, value)
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();
		try (Response response = http.newCall(request).execute())
		{
			int code = response.code();
			String text = bodyOf(response);
			if (code == 201)
			{
				String id = sessionId(text);
				if (id == null)
				{
					return backoff();
				}
				generation.serverId = id;
				generation.token = value;
				generation.nextSeq = 0;
				backoffMs = 0;
				return true;
			}
			return refused(code, text, value, item, true);
		}
	}

	private String sessionId(String text)
	{
		try
		{
			JsonObject created = gson.fromJson(text, JsonObject.class);
			return created != null && created.has("id") && !created.get("id").isJsonNull() ? created.get("id").getAsString() : null;
		}
		catch (JsonParseException | IllegalStateException | UnsupportedOperationException e)
		{
			return null;
		}
	}

	private boolean putPart(Item item, String value) throws IOException
	{
		Generation generation = item.generation;
		Request request = new Request.Builder()
			.url(apiUrl + "/input/sessions/" + generation.serverId + "/parts/" + generation.nextSeq)
			.header(TOKEN_HEADER, value)
			.put(RequestBody.create(GZIP, item.gzip()))
			.build();
		try (Response response = http.newCall(request).execute())
		{
			int code = response.code();
			String text = bodyOf(response);
			if (code == 200 || code == 201)
			{
				generation.nextSeq++;
				uploadedParts.incrementAndGet();
				backoffMs = 0;
				return true;
			}
			if (code == 404 || code == 409 && text.contains("session-closed"))
			{
				generation.serverId = null;
				return backoff();
			}
			if (code == 409)
			{
				generation.nextSeq++;
				return false;
			}
			return refused(code, text, value, item, false);
		}
	}

	private boolean close(Item item, String value) throws IOException
	{
		Generation generation = item.generation;
		if (generation.serverId == null)
		{
			return true;
		}
		JsonObject body = new JsonObject();
		if (generation.nextSeq > 0)
		{
			body.addProperty("finalSeq", generation.nextSeq - 1);
		}
		Request request = new Request.Builder()
			.url(apiUrl + "/input/sessions/" + generation.serverId + "/close")
			.header(TOKEN_HEADER, value)
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();
		try (Response response = http.newCall(request).execute())
		{
			int code = response.code();
			String text = bodyOf(response);
			if (code == 204 || code == 404)
			{
				return true;
			}
			return refused(code, text, value, item, false);
		}
	}

	private boolean refused(int code, String text, String value, Item item, boolean starting)
	{
		if (code == 401)
		{
			rejectedToken = value;
			state = State.LINK_REMOVED;
			onRejected.accept(value);
			log.warn("Your Microbot account link was removed, so input recording stopped uploading. Link this client again in Microbot Cloud to resume.");
			return false;
		}
		if (code == 429 && (text.contains("daily-limit") || text.contains("too-many-sessions")))
		{
			limitUntilMs = System.currentTimeMillis() + LIMIT_RETRY_MS;
			state = State.DAILY_LIMIT;
			log.info("Input recording reached today's upload limit; it tries again in 30 minutes.");
			return false;
		}
		if (code == 429 || code == 408 || code >= 500)
		{
			return backoff();
		}
		if (item.close)
		{
			remove(item, false);
			return false;
		}
		if (starting)
		{
			item.generation.dead = true;
		}
		log.warn("microbot.cloud refused part of your input recording ({}), so it was skipped.", code);
		remove(item, true);
		return false;
	}

	private boolean backoff()
	{
		backoffMs = backoffMs == 0 ? 2_000 : Math.min(MAX_BACKOFF_MS, backoffMs * 2);
		retryAtMs = System.currentTimeMillis() + backoffMs;
		state = State.RETRYING;
		return false;
	}

	private static String bodyOf(Response response) throws IOException
	{
		ResponseBody body = response.body();
		return body == null ? "" : body.string();
	}
}
