package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpServer;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URL;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.Executors;

import static org.junit.Assert.*;

public class ScriptHandlerTest {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

	private static HttpServer server;
	private static int port;

	@BeforeClass
	public static void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newFixedThreadPool(2));

		ScriptHandler handler = new ScriptHandler(GSON);
		server.createContext(handler.getPath(), handler);
		server.start();

		port = server.getAddress().getPort();
	}

	@AfterClass
	public static void stopServer() {
		if (server != null) {
			server.stop(0);
		}
	}

	@After
	public void cleanUp() {
		ScriptResultStore.clearAll();
	}

	// ==========================================
	// Routing tests
	// ==========================================

	@Test
	public void testUnknownSubpathReturns404() throws IOException {
		Response resp = get("/scripts/nonexistent");
		assertEquals(404, resp.code);
		assertNotNull(resp.body.get("error"));
	}

	@Test
	public void testRejectsCrossOriginRequests() throws IOException, InterruptedException {
		// Pre-auth failure surface is indistinguishable from a generic 404 so that
		// scanners cannot fingerprint the agent server from an unauthenticated probe.
		HttpClient client = HttpClient.newHttpClient();
		HttpRequest req = HttpRequest.newBuilder()
				.uri(URI.create("http://127.0.0.1:" + port + "/scripts"))
				.header("Origin", "https://evil.example.com")
				.GET()
				.build();
		HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
		assertEquals(404, resp.statusCode());
		assertTrue("CORS wildcard must not be emitted",
				resp.headers().firstValue("Access-Control-Allow-Origin").isEmpty());
	}

	@Test
	public void testRejectsAttackerHostHeader() throws IOException {
		try (java.net.Socket sock = new java.net.Socket("127.0.0.1", port)) {
			sock.getOutputStream().write(
					("GET /scripts HTTP/1.1\r\n" +
							"Host: attacker.example.com\r\n" +
							"Connection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
			String statusLine = new java.io.BufferedReader(
					new java.io.InputStreamReader(sock.getInputStream(), StandardCharsets.UTF_8)).readLine();
			assertNotNull(statusLine);
			assertTrue("expected 404 status, got: " + statusLine, statusLine.startsWith("HTTP/1.1 404"));
		}
	}

	@Test
	public void testRejectsMissingTokenWhenConfigured() throws IOException {
		AgentHandler.setTokenSupplier(() -> "expected-token");
		try {
			HttpURLConnection conn = openConnection("/scripts");
			conn.setRequestMethod("GET");
			assertEquals(404, conn.getResponseCode());
		} finally {
			AgentHandler.setTokenSupplier(null);
		}
	}

	@Test
	public void testAcceptsValidToken() throws IOException {
		AgentHandler.setTokenSupplier(() -> "expected-token");
		try {
			HttpURLConnection conn = openConnection("/scripts");
			conn.setRequestMethod("GET");
			conn.setRequestProperty("X-Agent-Token", "expected-token");
			assertNotEquals(401, conn.getResponseCode());
			assertNotEquals(403, conn.getResponseCode());
		} finally {
			AgentHandler.setTokenSupplier(null);
		}
	}

	// ==========================================
	// GET /scripts - List
	// ==========================================

	@Test
	public void testListRejectsPost() throws IOException {
		Response resp = post("/scripts", Map.of());
		assertEquals(405, resp.code);
	}

	// ==========================================
	// POST /scripts/start - Start
	// ==========================================

	@Test
	public void testStartRejectsGet() throws IOException {
		Response resp = get("/scripts/start");
		assertEquals(405, resp.code);
	}

	@Test
	public void testStartMissingBodyReturnsPluginNotFound() throws IOException {
		Response resp = post("/scripts/start", Map.of());
		assertEquals(404, resp.code);
		assertTrue(resp.body.get("error").toString().contains("not found"));
	}

	@Test
	public void testStartNonexistentPluginReturns404() throws IOException {
		Response resp = post("/scripts/start", Map.of("className", "com.nonexistent.FakePlugin"));
		assertEquals(404, resp.code);
	}

	@Test
	public void testStartByNameNonexistentReturns404() throws IOException {
		Response resp = post("/scripts/start", Map.of("name", "Totally Fake Plugin"));
		assertEquals(404, resp.code);
	}

	// ==========================================
	// POST /scripts/stop - Stop
	// ==========================================

	@Test
	public void testStopRejectsGet() throws IOException {
		Response resp = get("/scripts/stop");
		assertEquals(405, resp.code);
	}

	@Test
	public void testStopNonexistentPluginReturns404() throws IOException {
		Response resp = post("/scripts/stop", Map.of("className", "com.nonexistent.FakePlugin"));
		assertEquals(404, resp.code);
	}

	@Test
	public void testStopMissingBodyReturnsPluginNotFound() throws IOException {
		Response resp = post("/scripts/stop", Map.of());
		assertEquals(404, resp.code);
	}

	// ==========================================
	// GET /scripts/status - Status
	// ==========================================

	@Test
	public void testStatusRejectsPost() throws IOException {
		Response resp = post("/scripts/status", Map.of());
		assertEquals(405, resp.code);
	}

	@Test
	public void testStatusMissingParamsReturns404() throws IOException {
		Response resp = get("/scripts/status");
		assertEquals(404, resp.code);
		assertTrue(resp.body.get("error").toString().contains("Provide"));
	}

	@Test
	public void testStatusNonexistentPluginReturns404() throws IOException {
		Response resp = get("/scripts/status?className=com.nonexistent.FakePlugin");
		assertEquals(404, resp.code);
	}

	@Test
	public void pauseAndResumeToggleAllScripts() throws IOException {
		try {
			Response paused = post("/scripts/pause", Map.of());
			assertEquals(200, paused.code);
			assertEquals(true, paused.body.get("scriptsPaused"));
			assertTrue(net.runelite.client.plugins.microbot.Microbot.pauseAllScripts.get());

			Response resumed = post("/scripts/resume", Map.of());
			assertEquals(false, resumed.body.get("scriptsPaused"));
			assertFalse(net.runelite.client.plugins.microbot.Microbot.pauseAllScripts.get());
			assertEquals(405, get("/scripts/pause").code);
		} finally {
			net.runelite.client.plugins.microbot.Microbot.pauseAllScripts.set(false);
		}
	}

	// ==========================================
	// POST /scripts/results - Submit results
	// ==========================================

	@Test
	public void testSubmitResultsStoresData() throws IOException {
		Map<String, Object> body = Map.of(
				"className", "com.test.MyPlugin",
				"passed", true,
				"kills", 42
		);
		Response resp = post("/scripts/results", body);

		assertEquals(200, resp.code);
		assertEquals(true, resp.body.get("stored"));
		assertEquals("com.test.MyPlugin", resp.body.get("className"));

		List<Map<String, Object>> stored = ScriptResultStore.get("com.test.MyPlugin");
		assertEquals(1, stored.size());
		assertEquals(true, stored.get(0).get("passed"));
	}

	@Test
	public void testSubmitResultsMissingClassNameReturns400() throws IOException {
		Response resp = post("/scripts/results", Map.of("passed", true));

		assertEquals(400, resp.code);
		assertTrue(resp.body.get("error").toString().contains("className"));
	}

	@Test
	public void testSubmitMultipleResultsAccumulate() throws IOException {
		post("/scripts/results", Map.of("className", "com.test.P", "run", 1));
		post("/scripts/results", Map.of("className", "com.test.P", "run", 2));
		post("/scripts/results", Map.of("className", "com.test.P", "run", 3));

		List<Map<String, Object>> stored = ScriptResultStore.get("com.test.P");
		assertEquals(3, stored.size());
	}

	@Test
	public void testSubmitResultsStripsClassNameFromStoredData() throws IOException {
		post("/scripts/results", Map.of("className", "com.test.P", "passed", true));

		List<Map<String, Object>> stored = ScriptResultStore.get("com.test.P");
		assertFalse(stored.get(0).containsKey("className"));
		assertEquals(true, stored.get(0).get("passed"));
	}

	// ==========================================
	// GET /scripts/results - Retrieve results
	// ==========================================

	@Test
	public void testGetResultsMissingParamReturns400() throws IOException {
		Response resp = get("/scripts/results");

		assertEquals(400, resp.code);
		assertTrue(resp.body.get("error").toString().contains("Provide"));
	}

	@Test
	public void testGetResultsReturnsStoredData() throws IOException {
		ScriptResultStore.submit("com.test.MyPlugin", Map.of("passed", true, "score", 100));

		Response resp = get("/scripts/results?className=com.test.MyPlugin");

		assertEquals(200, resp.code);
		assertEquals("com.test.MyPlugin", resp.body.get("className"));
		assertEquals(1.0, resp.body.get("count"));

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> results = (List<Map<String, Object>>) resp.body.get("results");
		assertNotNull(results);
		assertEquals(1, results.size());
		assertEquals(true, results.get(0).get("passed"));
	}

	@Test
	public void testGetResultsEmptyReturnsZeroCount() throws IOException {
		Response resp = get("/scripts/results?className=com.test.NothingHere");

		assertEquals(200, resp.code);
		assertEquals(0.0, resp.body.get("count"));

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> results = (List<Map<String, Object>>) resp.body.get("results");
		assertTrue(results.isEmpty());
	}

	@Test
	public void testGetResultsRejectsWrongMethod() throws IOException {
		HttpURLConnection conn = openConnection("/scripts/results?className=x");
		conn.setRequestMethod("DELETE");
		int code = conn.getResponseCode();
		assertEquals(405, code);
	}

	// ==========================================
	// Round-trip: submit then retrieve via HTTP
	// ==========================================

	@Test
	public void testResultsRoundTrip() throws IOException {
		post("/scripts/results", Map.of("className", "com.test.RT", "passed", false, "reason", "timeout"));
		post("/scripts/results", Map.of("className", "com.test.RT", "passed", true, "reason", "retry succeeded"));

		Response resp = get("/scripts/results?className=com.test.RT");
		assertEquals(200, resp.code);
		assertEquals(2.0, resp.body.get("count"));

		@SuppressWarnings("unchecked")
		List<Map<String, Object>> results = (List<Map<String, Object>>) resp.body.get("results");
		assertEquals(false, results.get(0).get("passed"));
		assertEquals("timeout", results.get(0).get("reason"));
		assertEquals(true, results.get(1).get("passed"));
		assertEquals("retry succeeded", results.get(1).get("reason"));
	}

	// ==========================================
	// Security headers (CORS wildcard must NOT be present)
	// ==========================================

	@Test
	public void testNoCorsWildcardOnGet() throws IOException {
		HttpURLConnection conn = openConnection("/scripts/results?className=x");
		conn.setRequestMethod("GET");
		conn.getResponseCode();
		assertNull(conn.getHeaderField("Access-Control-Allow-Origin"));
	}

	@Test
	public void testNoCorsWildcardOnPost() throws IOException {
		HttpURLConnection conn = openConnection("/scripts/results");
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setDoOutput(true);
		try (OutputStream os = conn.getOutputStream()) {
			os.write(GSON.toJson(Map.of("className", "x", "ok", true)).getBytes(StandardCharsets.UTF_8));
		}
		conn.getResponseCode();
		assertNull(conn.getHeaderField("Access-Control-Allow-Origin"));
	}

	// ==========================================
	// JSON content type
	// ==========================================

	@Test
	public void testResponseContentTypeIsJson() throws IOException {
		HttpURLConnection conn = openConnection("/scripts/results?className=x");
		conn.setRequestMethod("GET");
		conn.getResponseCode();
		assertTrue(conn.getHeaderField("Content-Type").contains("application/json"));
	}

	// ==========================================
	// Helpers
	// ==========================================

	private Response get(String path) throws IOException {
		HttpURLConnection conn = openConnection(path);
		conn.setRequestMethod("GET");
		return readResponse(conn);
	}

	private Response post(String path, Map<String, Object> body) throws IOException {
		HttpURLConnection conn = openConnection(path);
		conn.setRequestMethod("POST");
		conn.setRequestProperty("Content-Type", "application/json");
		conn.setDoOutput(true);
		try (OutputStream os = conn.getOutputStream()) {
			os.write(GSON.toJson(body).getBytes(StandardCharsets.UTF_8));
		}
		return readResponse(conn);
	}

	private HttpURLConnection openConnection(String path) throws IOException {
		URL url = new URL("http://127.0.0.1:" + port + path);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		return conn;
	}

	private Response readResponse(HttpURLConnection conn) throws IOException {
		int code = conn.getResponseCode();
		InputStream stream = code >= 400 ? conn.getErrorStream() : conn.getInputStream();
		String json;
		try (Scanner sc = new Scanner(stream, StandardCharsets.UTF_8.name())) {
			json = sc.useDelimiter("\\A").hasNext() ? sc.next() : "{}";
		}
		Map<String, Object> body = GSON.fromJson(json, MAP_TYPE);
		return new Response(code, body);
	}

	private static class Response {
		final int code;
		final Map<String, Object> body;

		Response(int code, Map<String, Object> body) {
			this.code = code;
			this.body = body;
		}
	}
}
