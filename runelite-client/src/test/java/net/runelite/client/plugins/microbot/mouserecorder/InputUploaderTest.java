package net.runelite.client.plugins.microbot.mouserecorder;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class InputUploaderTest
{
	private static final String TOKEN = "mbc_" + "a".repeat(43);
	private static final String SESSION = "11111111-1111-1111-1111-111111111111";

	private final MockWebServer server = new MockWebServer();
	private InputUploader uploader;

	@Before
	public void setUp() throws IOException
	{
		server.start();
		uploader = new InputUploader(new OkHttpClient(), new Gson(), () -> Optional.of(TOKEN), server.url("/api").toString());
	}

	@After
	public void tearDown() throws IOException
	{
		server.shutdown();
	}

	private static InputUploader.SessionInfo info()
	{
		JsonObject meta = new JsonObject();
		meta.addProperty("canvasWidth", 765);
		return new InputUploader.SessionInfo(Instant.parse("2026-11-14T12:00:00Z"), UUID.fromString("22222222-2222-2222-2222-222222222222"), "2.6.50", meta);
	}

	private static String gunzip(byte[] body) throws IOException
	{
		try (GZIPInputStream in = new GZIPInputStream(new ByteArrayInputStream(body)))
		{
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static MockResponse created()
	{
		return new MockResponse().setResponseCode(201).setBody("{\"id\":\"" + SESSION + "\"}");
	}

	@Test
	public void startsTheSessionUploadsPartsInOrderAndCloses() throws Exception
	{
		server.enqueue(created());
		server.enqueue(new MockResponse().setResponseCode(201).setBody("{}"));
		server.enqueue(new MockResponse().setResponseCode(201).setBody("{}"));
		server.enqueue(new MockResponse().setResponseCode(204));

		uploader.beginSession(info());
		uploader.submit("{\"t\":1,\"e\":\"mv\"}\n");
		uploader.submit("{\"t\":30001,\"e\":\"mv\"}\n");
		uploader.endSession();
		uploader.drain();

		RecordedRequest start = server.takeRequest();
		assertEquals("POST", start.getMethod());
		assertEquals("/api/input/sessions", start.getPath());
		assertEquals(TOKEN, start.getHeader(InputUploader.TOKEN_HEADER));
		JsonObject body = new Gson().fromJson(start.getBody().readUtf8(), JsonObject.class);
		assertEquals("2026-11-14T12:00:00Z", body.get("startedAt").getAsString());
		assertEquals("22222222-2222-2222-2222-222222222222", body.get("clientSessionId").getAsString());
		assertEquals(InputUploader.RECORDER_VERSION, body.get("recorderVersion").getAsInt());
		assertEquals(765, body.getAsJsonObject("meta").get("canvasWidth").getAsInt());

		RecordedRequest first = server.takeRequest();
		assertEquals("PUT", first.getMethod());
		assertEquals("/api/input/sessions/" + SESSION + "/parts/0", first.getPath());
		assertTrue(first.getHeader("Content-Type").startsWith("application/gzip"));
		assertEquals("{\"t\":1,\"e\":\"mv\"}\n", gunzip(first.getBody().readByteArray()));
		assertEquals("/api/input/sessions/" + SESSION + "/parts/1", server.takeRequest().getPath());
		RecordedRequest close = server.takeRequest();
		assertEquals("/api/input/sessions/" + SESSION + "/close", close.getPath());
		assertTrue(close.getBody().readUtf8().contains("\"finalSeq\":1"));
		assertEquals(2, uploader.uploadedParts());
		assertEquals(InputUploader.State.IDLE, uploader.state());
	}

	@Test
	public void aRetriedPartSendsTheSameBytes() throws Exception
	{
		server.enqueue(created());
		server.enqueue(new MockResponse().setResponseCode(503));
		server.enqueue(new MockResponse().setResponseCode(200).setBody("{}"));

		uploader.beginSession(info());
		uploader.submit("{\"t\":1,\"e\":\"mv\"}\n");
		uploader.drain();
		assertEquals(InputUploader.State.RETRYING, uploader.state());
		assertEquals(1, uploader.queued());
		uploader.retryNow();
		uploader.drain();

		server.takeRequest();
		byte[] firstTry = server.takeRequest().getBody().readByteArray();
		byte[] secondTry = server.takeRequest().getBody().readByteArray();
		assertArrayEquals(firstTry, secondTry);
		assertEquals(0, uploader.queued());
	}

	@Test
	public void aRefusedTokenStopsUploading() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(401).setBody("{\"code\":\"unauthorized\"}"));

		uploader.beginSession(info());
		uploader.submit("{\"t\":1,\"e\":\"mv\"}\n");
		uploader.drain();
		uploader.drain();

		assertEquals(InputUploader.State.NOT_LINKED, uploader.state());
		assertEquals(1, server.getRequestCount());
		assertEquals(1, uploader.queued());
		assertTrue(!uploader.isLinked());
	}

	@Test
	public void theDailyLimitHoldsUploadsUntilTomorrow() throws Exception
	{
		server.enqueue(created());
		server.enqueue(new MockResponse().setResponseCode(429).setBody("{\"code\":\"daily-limit\"}"));

		uploader.beginSession(info());
		uploader.submit("{\"t\":1,\"e\":\"mv\"}\n");
		uploader.drain();
		uploader.retryNow();
		uploader.drain();

		assertEquals(InputUploader.State.DAILY_LIMIT, uploader.state());
		assertEquals(2, server.getRequestCount());
	}

	@Test
	public void aClosedServerSessionIsReplaced() throws Exception
	{
		server.enqueue(created());
		server.enqueue(new MockResponse().setResponseCode(409).setBody("{\"code\":\"session-closed\"}"));
		server.enqueue(new MockResponse().setResponseCode(201).setBody("{\"id\":\"33333333-3333-3333-3333-333333333333\"}"));
		server.enqueue(new MockResponse().setResponseCode(201).setBody("{}"));

		uploader.beginSession(info());
		uploader.submit("{\"t\":1,\"e\":\"mv\"}\n");
		uploader.drain();
		uploader.retryNow();
		uploader.drain();

		server.takeRequest();
		server.takeRequest();
		assertEquals("/api/input/sessions", server.takeRequest().getPath());
		assertEquals("/api/input/sessions/33333333-3333-3333-3333-333333333333/parts/0", server.takeRequest().getPath());
	}

	@Test
	public void theQueueIsBoundedAndCountsDroppedChunks()
	{
		uploader.beginSession(info());
		for (int i = 0; i < InputUploader.MAX_QUEUED + 5; i++)
		{
			uploader.submit("{\"t\":" + i + ",\"e\":\"mv\"}\n");
		}

		assertEquals(InputUploader.MAX_QUEUED, uploader.queued());
		assertEquals(5, uploader.dropped());
	}

	@Test
	public void nothingIsSentWithoutALinkedAccount()
	{
		InputUploader unlinked = new InputUploader(new OkHttpClient(), new Gson(), Optional::empty, server.url("/api").toString());
		unlinked.beginSession(info());
		unlinked.submit("{\"t\":1,\"e\":\"mv\"}\n");
		unlinked.drain();

		assertEquals(InputUploader.State.NOT_LINKED, unlinked.state());
		assertEquals(0, server.getRequestCount());
	}
}
