package net.runelite.client.plugins.microbot.mouserecorder;

import java.io.IOException;
import java.util.Optional;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class InputAccessCheckTest
{
	private static final String TOKEN = "mbc_" + "b".repeat(43);

	private final MockWebServer server = new MockWebServer();
	private final AtomicReference<String> token = new AtomicReference<>(TOKEN);
	private final LinkedBlockingQueue<Boolean> decisions = new LinkedBlockingQueue<>();
	private InputAccessCheck check;

	@Before
	public void setUp() throws IOException
	{
		server.start();
		check = new InputAccessCheck(new OkHttpClient(), () -> Optional.ofNullable(token.get()), decisions::add, server.url("/api/").toString());
	}

	@After
	public void tearDown() throws IOException
	{
		server.shutdown();
	}

	@Test
	public void onlyNoContentAllowsAndOnlyForbiddenDenies()
	{
		assertEquals(Boolean.TRUE, InputAccessCheck.decide(204));
		assertEquals(Boolean.FALSE, InputAccessCheck.decide(403));
		assertNull(InputAccessCheck.decide(401));
		assertNull(InputAccessCheck.decide(429));
		assertNull(InputAccessCheck.decide(503));
	}

	@Test
	public void asksOncePerTokenAndAgainWhenTheLinkChanges() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(204));
		server.enqueue(new MockResponse().setResponseCode(403).setBody("{\"code\":\"not-available\"}"));

		check.poll();
		assertEquals(Boolean.TRUE, decisions.poll(5, TimeUnit.SECONDS));
		check.poll();
		token.set("mbc_" + "c".repeat(43));
		check.poll();
		assertEquals(Boolean.FALSE, decisions.poll(5, TimeUnit.SECONDS));

		RecordedRequest first = server.takeRequest();
		assertEquals("/api/input/access", first.getPath());
		assertEquals(TOKEN, first.getHeader(InputUploader.TOKEN_HEADER));
		assertEquals(2, server.getRequestCount());
	}

	@Test
	public void anUnlinkedClientAsksNothing()
	{
		token.set(null);

		check.poll();

		assertEquals(0, server.getRequestCount());
	}

	@Test
	public void aServerErrorDecidesNothing() throws Exception
	{
		server.enqueue(new MockResponse().setResponseCode(503));

		check.poll();
		server.takeRequest(5, TimeUnit.SECONDS);

		assertNull(decisions.poll(500, TimeUnit.MILLISECONDS));
	}
}
