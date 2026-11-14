package net.runelite.client.plugins.microbot.mouserecorder;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
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

	private static final MediaType GZIP = MediaType.parse("application/gzip");
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");
	private static final long MAX_BACKOFF_MS = 60_000;

	enum State
	{
		IDLE,
		UPLOADING,
		RETRYING,
		NOT_LINKED,
		DAILY_LIMIT
	}

	static final class SessionInfo
	{
		final Instant startedAt;
		final UUID clientSessionId;
		final String clientVersion;
		final JsonObject meta;

		SessionInfo(Instant startedAt, UUID clientSessionId, String clientVersion, JsonObject meta)
		{
			this.startedAt = startedAt;
			this.clientSessionId = clientSessionId;
			this.clientVersion = clientVersion;
			this.meta = meta;
		}
	}

	private static final class Generation
	{
		final SessionInfo info;
		String serverId;
		int nextSeq;
		boolean dead;

		Generation(SessionInfo info)
		{
			this.info = info;
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

		byte[] gzip()
		{
			if (gzip == null)
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
	private final String apiUrl;
	private final Deque<Item> queue = new ArrayDeque<>();
	private ScheduledExecutorService executor;
	private Generation current;
	private volatile State state = State.IDLE;
	private volatile int dropped;
	private volatile long uploadedParts;
	private long backoffMs;
	private long retryAtMs;
	private String rejectedToken;
	private LocalDate limitDay;

	InputUploader(OkHttpClient http, Gson gson, Supplier<Optional<String>> token, String apiUrl)
	{
		this.http = http;
		this.gson = gson;
		this.token = token;
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

	void stopWorker()
	{
		ScheduledExecutorService running;
		synchronized (this)
		{
			running = executor;
			executor = null;
		}
		if (running == null)
		{
			return;
		}
		running.submit(this::drainSafely);
		running.shutdown();
		try
		{
			running.awaitTermination(5, TimeUnit.SECONDS);
		}
		catch (InterruptedException e)
		{
			Thread.currentThread().interrupt();
		}
		running.shutdownNow();
	}

	synchronized void beginSession(SessionInfo info)
	{
		current = new Generation(info);
	}

	synchronized void submit(String text)
	{
		if (current == null || text == null || text.isEmpty())
		{
			return;
		}
		enqueue(new Item(current, text, false));
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
		return dropped;
	}

	long uploadedParts()
	{
		return uploadedParts;
	}

	void retryNow()
	{
		retryAtMs = 0;
	}

	synchronized int queued()
	{
		return queue.size();
	}

	boolean isLinked()
	{
		Optional<String> value = token.get();
		return value.isPresent() && !value.get().equals(rejectedToken);
	}

	private void enqueue(Item item)
	{
		if (queue.size() >= MAX_QUEUED)
		{
			Item oldest = queue.pollFirst();
			if (oldest != null && !oldest.close)
			{
				dropped++;
			}
		}
		queue.addLast(item);
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
				state = State.IDLE;
				return;
			}
			if (!ready())
			{
				return;
			}
			if (!send(item))
			{
				return;
			}
			synchronized (this)
			{
				if (queue.peekFirst() == item)
				{
					queue.pollFirst();
				}
			}
		}
	}

	private boolean ready()
	{
		long now = System.currentTimeMillis();
		if (limitDay != null)
		{
			if (LocalDate.now(ZoneOffset.UTC).isAfter(limitDay))
			{
				limitDay = null;
			}
			else
			{
				state = State.DAILY_LIMIT;
				return false;
			}
		}
		if (!isLinked())
		{
			state = State.NOT_LINKED;
			return false;
		}
		return now >= retryAtMs;
	}

	private boolean send(Item item)
	{
		String value = token.get().orElse(null);
		if (value == null)
		{
			state = State.NOT_LINKED;
			return false;
		}
		if (item.generation.dead)
		{
			if (!item.close)
			{
				dropped++;
			}
			return true;
		}
		state = State.UPLOADING;
		try
		{
			if (item.close)
			{
				return close(item.generation, value);
			}
			if (item.generation.serverId == null && !createSession(item.generation, value))
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

	private boolean createSession(Generation generation, String value) throws IOException
	{
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
			if (code == 201)
			{
				JsonObject created = gson.fromJson(bodyOf(response), JsonObject.class);
				generation.serverId = created.get("id").getAsString();
				generation.nextSeq = 0;
				backoffMs = 0;
				return true;
			}
			return refused(code, value, response, generation);
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
			if (code == 200 || code == 201)
			{
				generation.nextSeq++;
				uploadedParts++;
				backoffMs = 0;
				return true;
			}
			if (code == 404 || code == 409 && bodyOf(response).contains("session-closed"))
			{
				generation.serverId = null;
				return backoff();
			}
			if (code == 409)
			{
				generation.nextSeq++;
				return false;
			}
			return refused(code, value, response, null);
		}
	}

	private boolean close(Generation generation, String value) throws IOException
	{
		if (generation.serverId == null)
		{
			return true;
		}
		JsonObject body = new JsonObject();
		body.addProperty("finalSeq", generation.nextSeq - 1);
		Request request = new Request.Builder()
			.url(apiUrl + "/input/sessions/" + generation.serverId + "/close")
			.header(TOKEN_HEADER, value)
			.post(RequestBody.create(JSON, gson.toJson(body)))
			.build();
		try (Response response = http.newCall(request).execute())
		{
			int code = response.code();
			if (code == 204 || code == 404)
			{
				return true;
			}
			return refused(code, value, response, null);
		}
	}

	private boolean refused(int code, String value, Response response, Generation starting) throws IOException
	{
		if (code == 401)
		{
			rejectedToken = value;
			state = State.NOT_LINKED;
			log.warn("The Microbot account link was refused, so input recording stopped uploading; link the client again to resume");
			return false;
		}
		if (code == 429)
		{
			String text = bodyOf(response);
			if (text.contains("daily-limit") || text.contains("too-many-sessions"))
			{
				limitDay = LocalDate.now(ZoneOffset.UTC);
				state = State.DAILY_LIMIT;
				log.info("Input recording reached today's upload limit; it resumes tomorrow");
				return false;
			}
			return backoff();
		}
		if (code >= 500 || code == 408)
		{
			return backoff();
		}
		log.warn("Input upload refused with {} {}; skipping {}", code, bodyOf(response), starting != null ? "this session" : "this chunk");
		if (starting != null)
		{
			starting.dead = true;
			return false;
		}
		dropped++;
		synchronized (this)
		{
			queue.pollFirst();
		}
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
