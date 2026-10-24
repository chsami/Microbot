package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;

public class ObservePathValidationTest {

	private static HttpServer server;
	private static int port;

	@BeforeClass
	public static void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newFixedThreadPool(2));
		Gson gson = new Gson();
		for (AgentHandler handler : new AgentHandler[]{new ObserveHandler(gson, null), new PathHandler(gson)}) {
			server.createContext(handler.getPath(), handler);
		}
		server.start();
		port = server.getAddress().getPort();
	}

	@AfterClass
	public static void stopServer() {
		server.stop(0);
	}

	@Test
	public void observeRejectsPost() throws IOException {
		assertEquals(405, status("POST", "/observe"));
	}

	@Test
	public void observeRejectsNonNumericCursor() throws IOException {
		assertEquals(400, status("GET", "/observe?chatSince=abc"));
	}

	@Test
	public void pathRequiresTarget() throws IOException {
		assertEquals(400, status("GET", "/path"));
		assertEquals(400, status("GET", "/path?x=3200"));
	}

	@Test
	public void pathRejectsNonNumericCoordinates() throws IOException {
		assertEquals(400, status("GET", "/path?x=a&y=3200"));
		assertEquals(400, status("GET", "/path?x=3200&y=3200&fromX=1"));
	}

	@Test
	public void pathRejectsPostAndUnknownSubPath() throws IOException {
		assertEquals(405, status("POST", "/path?x=1&y=1"));
		assertEquals(404, status("GET", "/path/nope"));
	}

	private static int status(String method, String path) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
		conn.setRequestMethod(method);
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		return conn.getResponseCode();
	}
}
