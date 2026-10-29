package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.sun.net.httpserver.HttpServer;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginHealth;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManifest;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class PluginHubHandlerTest {

	private final Gson gson = new Gson();
	private final MicrobotPluginManager manager = mock(MicrobotPluginManager.class);
	private final MicrobotPluginManifest manifest = new MicrobotPluginManifest();
	private HttpServer server;

	@Before
	public void startServer() throws IOException {
		manifest.setInternalName("AutoMiner");
		manifest.setDisplayName("Auto Miner");
		manifest.setVersion("2.0.0");
		when(manager.findManifest("AutoMiner")).thenReturn(Optional.of(manifest));
		when(manager.getInstalledPluginVersion("AutoMiner")).thenReturn(Optional.empty());
		when(manager.getPluginHealth(any(), any())).thenAnswer(invocation ->
				MicrobotPluginHealth.evaluate(invocation.getArgument(0), invocation.getArgument(1), true));
		when(manager.installPlugin(any(), any())).thenReturn(CompletableFuture.completedFuture(null));
		when(manager.updatePlugin(any(), any())).thenReturn(CompletableFuture.completedFuture(null));

		server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
		PluginHubHandler handler = new PluginHubHandler(gson, manager, 50);
		server.createContext(handler.getPath(), handler);
		server.start();
	}

	@After
	public void stopServer() {
		server.stop(0);
	}

	@Test
	public void installRefusesPluginDisabledUpstream() throws IOException {
		manifest.setDisable(true);

		assertEquals(409, post("/plugins/install", "{\"name\":\"AutoMiner\"}"));
		verify(manager, never()).installPlugin(any(), any());
	}

	@Test
	public void installSkipsPluginAlreadyAtRequestedVersion() throws IOException {
		when(manager.isLoaded("AutoMiner")).thenReturn(true);
		when(manager.getInstalledPluginVersion("AutoMiner")).thenReturn(Optional.of("2.0.0"));

		assertEquals(200, post("/plugins/install", "{\"name\":\"AutoMiner\"}"));
		verify(manager, never()).installPlugin(any(), any());
		verify(manager, never()).updatePlugin(any(), any());
	}

	@Test
	public void installOfLoadedPluginAtOtherVersionUpdatesIt() throws IOException {
		when(manager.isLoaded("AutoMiner")).thenReturn(true);
		when(manager.getInstalledPluginVersion("AutoMiner")).thenReturn(Optional.of("1.0.0"));

		assertEquals(200, post("/plugins/install", "{\"name\":\"AutoMiner\"}"));
		verify(manager).updatePlugin(manifest, null);
		verify(manager, never()).installPlugin(any(), any());
	}

	@Test
	public void installThatDoesNotLoadThePluginReportsFailure() throws IOException {
		when(manager.isLoaded("AutoMiner")).thenReturn(false);

		assertEquals(502, post("/plugins/install", "{\"name\":\"AutoMiner\"}"));
	}

	@Test
	public void removeOfPluginNotInstalledIsNotFound() throws IOException {
		assertEquals(404, post("/plugins/remove", "{\"name\":\"AutoMiner\"}"));
		verify(manager, never()).removePlugin(any());
	}

	@Test
	public void removeThatLeavesThePluginLoadedReportsConflict() throws IOException {
		when(manager.isLoaded("AutoMiner")).thenReturn(true);
		when(manager.removePlugin(any())).thenReturn(CompletableFuture.completedFuture(null));

		assertEquals(409, post("/plugins/remove", "{\"name\":\"AutoMiner\"}"));
	}

	@Test
	public void unknownPluginNameIsBadRequest() throws IOException {
		when(manager.findManifest("Nope")).thenReturn(Optional.empty());

		assertEquals(400, post("/plugins/install", "{\"name\":\"Nope\"}"));
	}

	@Test
	public void installRejectsVersionTheHubDoesNotOffer() throws IOException {
		manifest.setAvailableVersions(List.of("1.0.0", "2.0.0"));

		assertEquals(400, post("/plugins/install", "{\"name\":\"AutoMiner\",\"version\":\"9.9.9\"}"));
		verify(manager, never()).installPlugin(any(), any());
	}

	@Test
	public void invalidTimeoutIsRejectedBeforeAnyInstallStarts() throws IOException {
		assertEquals(400, post("/plugins/install", "{\"name\":\"AutoMiner\",\"timeout\":\"soon\"}"));
		verify(manager, never()).installPlugin(any(), any());
	}

	@Test
	public void listFiltersOnInstalledState() throws IOException {
		MicrobotPluginManifest other = new MicrobotPluginManifest();
		other.setInternalName("AutoFisher");
		when(manager.getManifestMap()).thenReturn(Map.of("AutoMiner", manifest, "AutoFisher", other));
		when(manager.isLoaded("AutoMiner")).thenReturn(true);

		Map<String, Object> body = get("/plugins?installed=true");
		assertEquals(1.0, body.get("total"));
	}

	private int post(String path, String json) throws IOException {
		HttpURLConnection connection = open(path);
		connection.setRequestMethod("POST");
		connection.setDoOutput(true);
		try (OutputStream out = connection.getOutputStream()) {
			out.write(json.getBytes(StandardCharsets.UTF_8));
		}
		return connection.getResponseCode();
	}

	private Map<String, Object> get(String path) throws IOException {
		HttpURLConnection connection = open(path);
		try (InputStream in = connection.getInputStream()) {
			return gson.fromJson(new String(in.readAllBytes(), StandardCharsets.UTF_8),
					new TypeToken<Map<String, Object>>() {}.getType());
		}
	}

	private HttpURLConnection open(String path) throws IOException {
		return (HttpURLConnection) new URL("http://127.0.0.1:" + server.getAddress().getPort() + path).openConnection();
	}
}
