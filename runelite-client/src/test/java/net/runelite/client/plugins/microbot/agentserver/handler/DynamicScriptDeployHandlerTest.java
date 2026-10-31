package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpServer;
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
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class DynamicScriptDeployHandlerTest {

	private static final Gson GSON = new Gson();
	private static HttpServer server;
	private static ExecutorService executor;
	private static int port;

	@BeforeClass
	public static void startServer() throws IOException {
		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		executor = Executors.newFixedThreadPool(2);
		server.setExecutor(executor);
		AgentHandler handler = new DynamicScriptDeployHandler(GSON);
		server.createContext(handler.getPath(), handler);
		server.start();
		port = server.getAddress().getPort();
	}

	@AfterClass
	public static void stopServer() {
		server.stop(0);
		executor.shutdownNow();
	}

	@Test
	public void deployRejectsUnparseableSourcePathWithBadRequest() throws IOException {
		HttpURLConnection conn = post("invalid-path", "scripts\u0000bot");

		assertEquals(400, conn.getResponseCode());
		String body = read(conn.getErrorStream());
		assertTrue(body, body.contains("Invalid sourcePath"));
	}

	@Test
	public void deployRejectsMissingDirectoryWithBadRequest() throws IOException {
		HttpURLConnection conn = post("missing-dir", "/nonexistent/microbot/deploy/source");

		assertEquals(400, conn.getResponseCode());
		String body = read(conn.getErrorStream());
		assertTrue(body, body.contains("sourcePath is not a directory"));
	}

	private static HttpURLConnection post(String name, String sourcePath) throws IOException {
		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("name", name);
		payload.put("sourcePath", sourcePath);
		byte[] bytes = GSON.toJson(payload).getBytes(StandardCharsets.UTF_8);

		HttpURLConnection conn = (HttpURLConnection) new URL("http://127.0.0.1:" + port + "/scripts/deploy").openConnection();
		conn.setRequestMethod("POST");
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		conn.setDoOutput(true);
		conn.setRequestProperty("Content-Type", "application/json");
		try (OutputStream out = conn.getOutputStream()) {
			out.write(bytes);
		}
		return conn;
	}

	private static String read(InputStream in) throws IOException {
		try (InputStream stream = in) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
