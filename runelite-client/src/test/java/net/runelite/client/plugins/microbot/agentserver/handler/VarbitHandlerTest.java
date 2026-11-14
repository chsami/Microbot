package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpServer;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.playerstate.Rs2PlayerStateCache;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Type;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Optional;
import java.util.Scanner;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class VarbitHandlerTest {

	private static final Gson GSON = new GsonBuilder().create();
	private static final Type MAP_TYPE = new TypeToken<Map<String, Object>>() {}.getType();

	private static final int KNOWN_VARBIT_ID = 4070;
	private static final int UNKNOWN_VARBIT_ID = 16971;

	private static HttpServer server;
	private static int port;
	private static Field cacheField;
	private static Object previousCache;

	@BeforeClass
	public static void startServer() throws Exception {
		Client client = mock(Client.class);
		ClientThread clientThread = mock(ClientThread.class);
		when(clientThread.runOnClientThreadOptional(any())).thenAnswer(invocation -> {
			Callable<?> callable = invocation.getArgument(0);
			return Optional.ofNullable(callable.call());
		});
		when(client.getGameState()).thenReturn(GameState.LOGGED_IN);
		when(client.getVarbitValue(KNOWN_VARBIT_ID)).thenReturn(3);
		when(client.getVarbitValue(UNKNOWN_VARBIT_ID))
				.thenThrow(new IndexOutOfBoundsException("Varbit 16971 does not exist"));

		cacheField = Microbot.class.getDeclaredField("rs2PlayerStateCache");
		cacheField.setAccessible(true);
		previousCache = cacheField.get(null);
		cacheField.set(null, new Rs2PlayerStateCache(new EventBus(), client, clientThread));

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		server.setExecutor(Executors.newFixedThreadPool(2));

		VarbitHandler handler = new VarbitHandler(GSON);
		server.createContext(handler.getPath(), handler);
		server.start();

		port = server.getAddress().getPort();
	}

	@AfterClass
	public static void stopServer() throws IllegalAccessException {
		if (server != null) {
			server.stop(0);
		}
		if (cacheField != null) {
			cacheField.set(null, previousCache);
		}
	}

	@Test
	public void testUnknownIdReturns400() throws IOException {
		Response resp = get("/varbit?id=" + UNKNOWN_VARBIT_ID);
		assertEquals(400, resp.code);
		assertTrue(((String) resp.body.get("error")).toLowerCase().contains("invalid varbit id"));
	}

	@Test
	public void testNegativeIdReturns400() throws IOException {
		Response resp = get("/varbit?id=-1");
		assertEquals(400, resp.code);
		assertTrue(((String) resp.body.get("error")).toLowerCase().contains("invalid varbit id"));
	}

	@Test
	public void testNonNumericIdReturns400() throws IOException {
		Response resp = get("/varbit?id=abc");
		assertEquals(400, resp.code);
		assertTrue(((String) resp.body.get("error")).toLowerCase().contains("invalid varbit id"));
	}

	@Test
	public void testMissingIdReturns400() throws IOException {
		Response resp = get("/varbit");
		assertEquals(400, resp.code);
		assertTrue(((String) resp.body.get("error")).toLowerCase().contains("id"));
	}

	@Test
	public void testValidIdReturnsValue() throws IOException {
		Response resp = get("/varbit?id=" + KNOWN_VARBIT_ID);
		assertEquals(200, resp.code);
		assertEquals((double) KNOWN_VARBIT_ID, resp.body.get("varbitId"));
		assertEquals(3.0, resp.body.get("value"));
	}

	private Response get(String path) throws IOException {
		URL url = new URL("http://127.0.0.1:" + port + path);
		HttpURLConnection conn = (HttpURLConnection) url.openConnection();
		conn.setConnectTimeout(5000);
		conn.setReadTimeout(5000);
		conn.setRequestMethod("GET");
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
