package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class CatalogAndTraceTest {

	private static final Gson GSON = new Gson();
	private static HttpServer server;
	private static int port;

	static class EchoHandler extends AgentHandler {
		EchoHandler() {
			super(GSON);
		}

		@Override
		public String getPath() {
			return "/echo";
		}

		@Override
		protected void handleRequest(HttpExchange exchange) throws IOException {
			sendJson(exchange, 200, readJsonBody(exchange));
		}
	}

	@BeforeClass
	public static void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newFixedThreadPool(2));
		EchoHandler echo = new EchoHandler();
		server.createContext(echo.getPath(), echo);
		CatalogHandler catalog = new CatalogHandler(GSON, List.of("/echo"));
		server.createContext(catalog.getPath(), catalog);
		server.start();
		port = server.getAddress().getPort();
	}

	@AfterClass
	public static void stopServer() {
		server.stop(0);
	}

	@After
	public void disableTrace() {
		AgentTrace.setFile(null);
	}

	@Test
	public void rootListsEndpoints() throws IOException {
		HttpURLConnection conn = open("GET", "/");
		assertEquals(200, conn.getResponseCode());
		JsonObject body = GSON.fromJson(read(conn.getInputStream()), JsonObject.class);
		assertEquals("/echo", body.getAsJsonArray("endpoints").get(0).getAsString());
	}

	@Test
	public void unknownPathReturnsJsonWithEndpoints() throws IOException {
		HttpURLConnection conn = open("GET", "/nope");
		assertEquals(404, conn.getResponseCode());
		JsonObject body = GSON.fromJson(read(conn.getErrorStream()), JsonObject.class);
		assertEquals("Unknown endpoint: /nope", body.get("error").getAsString());
	}

	@Test
	public void unknownPathStaysOpaqueWithoutToken() throws IOException {
		AgentHandler.setTokenSupplier(() -> "secret");
		try {
			HttpURLConnection conn = open("GET", "/nope");
			assertEquals(404, conn.getResponseCode());
			assertEquals("Not Found", read(conn.getErrorStream()));
		} finally {
			AgentHandler.setTokenSupplier(null);
		}
	}

	@Test
	public void traceRecordsRequestsWhenEnabled() throws Exception {
		Path file = Files.createTempDirectory("agent-trace").resolve("trace.jsonl");
		AgentTrace.setFile(file);

		HttpURLConnection conn = open("POST", "/echo?x=1");
		conn.setDoOutput(true);
		try (OutputStream os = conn.getOutputStream()) {
			os.write("{\"name\":\"Banker\"}".getBytes(StandardCharsets.UTF_8));
		}
		assertEquals(200, conn.getResponseCode());

		long deadline = System.currentTimeMillis() + 2000;
		while (!Files.exists(file) && System.currentTimeMillis() < deadline) {
			Thread.sleep(20);
		}
		List<String> lines = Files.readAllLines(file);
		assertEquals(1, lines.size());
		@SuppressWarnings("unchecked")
		Map<String, Object> line = GSON.fromJson(lines.get(0), Map.class);
		assertEquals("POST", line.get("method"));
		assertEquals("/echo?x=1", line.get("uri"));
		assertEquals(200.0, line.get("status"));
		assertEquals("{\"name\":\"Banker\"}", line.get("body"));
		assertTrue(lines.get(0), lines.get(0).contains("/echo?x=1"));
	}

	@Test
	public void traceIsOffByDefault() throws Exception {
		assertFalse(AgentTrace.isEnabled());
		assertEquals(200, open("GET", "/").getResponseCode());
	}

	private static HttpURLConnection open(String method, String path) throws IOException {
		HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + path).openConnection();
		conn.setRequestMethod(method);
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		return conn;
	}

	private static String read(InputStream in) throws IOException {
		return new String(in.readAllBytes(), StandardCharsets.UTF_8);
	}
}
