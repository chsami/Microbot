package net.runelite.client.plugins.microbot.diagnostics;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Client;
import net.runelite.client.ClientSessionManager;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.ui.DrawManager;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class BugReportCollectorTest
{
	@PluginDescriptor(name = "Blast Furnace", version = "1.0.0")
	static class FurnacePlugin extends Plugin
	{
	}

	@ConfigGroup("furnace")
	interface FurnaceConfig extends Config
	{
		@ConfigItem(keyName = "bars", name = "Bars", description = "")
		default String bars()
		{
			return "STEEL";
		}

		@ConfigItem(keyName = "webhook", name = "Webhook", description = "", secret = true)
		default String webhook()
		{
			return "";
		}

		@ConfigItem(keyName = "lastRun", name = "", description = "", hidden = true)
		default String lastRun()
		{
			return "";
		}

		@ConfigItem(keyName = "notes", name = "Notes", description = "")
		default String notes()
		{
			return "";
		}

		@ConfigItem(keyName = "teammateName", name = "Teammate", description = "")
		default String teammateName()
		{
			return "";
		}
	}

	private static final BugReportSnapshot SNAPSHOT = BugReportSnapshot.builder()
		.diagnostics("Microbot diagnostics")
		.gameState("{\"world\": 360}")
		.log("2026-10-24 line\n")
		.recorder("# legend\n812 08:13:02.4 click=Fill>Crucible(44776,GAME_OBJECT_FIRST_OPTION)\n")
		.screenshot(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3})
		.loggedIn(true)
		.build();

	private final FurnacePlugin plugin = new FurnacePlugin();
	private ConfigManager configManager;
	private ScriptErrorReporter errors;
	private ClientSessionManager sessions;
	private MicrobotApi api;
	private BugReportCollector collector;

	@Before
	public void setUp() throws Exception
	{
		configManager = mock(ConfigManager.class);
		PluginManager pluginManager = mock(PluginManager.class);
		MicrobotPluginManager microbotPluginManager = mock(MicrobotPluginManager.class);
		errors = mock(ScriptErrorReporter.class);
		sessions = mock(ClientSessionManager.class);
		api = mock(MicrobotApi.class);

		FurnaceConfig config = mock(FurnaceConfig.class);
		when(pluginManager.getPluginConfigProxy(plugin)).thenReturn(config);
		when(configManager.getConfigDescriptor(config)).thenReturn(descriptor());
		when(configManager.getConfiguration("furnace", "bars")).thenReturn("STEEL");
		when(configManager.getConfiguration("furnace", "webhook")).thenReturn("https://discord.com/api/webhooks/1/abc");
		when(configManager.getConfiguration("furnace", "lastRun")).thenReturn("yesterday");
		when(configManager.getConfiguration("furnace", "notes")).thenReturn("saved in /home/alice/notes.txt");
		when(microbotPluginManager.getInstalledPluginVersion("FurnacePlugin")).thenReturn(Optional.of("1.4.2"));
		when(errors.recentErrors(any())).thenReturn("");

		collector = new BugReportCollector(mock(Client.class), mock(ClientThread.class), mock(ItemManager.class), configManager,
			pluginManager, microbotPluginManager, mock(DiagnosticReportCollector.class), errors, mock(DrawManager.class),
			sessions, api, mock(FlightRecorder.class), new Gson(), false);
	}

	private static ConfigDescriptor descriptor() throws Exception
	{
		List<ConfigItemDescriptor> items = new ArrayList<>();
		for (String name : new String[]{"bars", "webhook", "lastRun", "notes", "teammateName"})
		{
			Method method = FurnaceConfig.class.getMethod(name);
			items.add(new ConfigItemDescriptor(method.getAnnotation(ConfigItem.class), String.class, null, null, null));
		}
		return new ConfigDescriptor(FurnaceConfig.class.getAnnotation(ConfigGroup.class), List.of(), items, null);
	}

	@Test
	public void payloadCarriesEveryChosenSection()
	{
		when(errors.recentErrors("FurnacePlugin")).thenReturn("2x FurnacePlugin: loop failed\n");

		JsonObject payload = collector.payload(SNAPSHOT, plugin, "  Stands still at the belt  ", EnumSet.allOf(BugReportSection.class));

		assertEquals("FurnacePlugin", payload.get("plugin").getAsString());
		assertEquals("1.4.2", payload.get("pluginVersion").getAsString());
		assertEquals("Stands still at the belt", payload.get("description").getAsString());
		assertEquals("Microbot diagnostics", payload.get("diagnostics").getAsString());
		assertEquals("{\"world\": 360}", payload.get("gameState").getAsString());
		assertEquals("2026-10-24 line\n", payload.get("log").getAsString());
		assertTrue(payload.get("recorder").getAsString().contains("click=Fill>Crucible"));
		assertEquals("2x FurnacePlugin: loop failed\n", payload.get("error").getAsString());
		assertEquals("/9j/AQID", payload.get("screenshot").getAsString());
		assertTrue(payload.has("pluginConfig"));
	}

	@Test
	public void payloadLeavesOutUncheckedSections()
	{
		JsonObject payload = collector.payload(SNAPSHOT, plugin, "broken", EnumSet.noneOf(BugReportSection.class));

		assertEquals("Microbot diagnostics", payload.get("diagnostics").getAsString());
		for (String key : new String[]{"gameState", "log", "error", "screenshot", "pluginConfig", "recorder"})
		{
			assertFalse(key, payload.has(key));
		}
		verify(errors, never()).recentErrors(any());
	}

	@Test
	public void recorderLeavesOutScriptErrorsWhenTheLogIsUnticked()
	{
		BugReportSnapshot snapshot = BugReportSnapshot.builder()
			.diagnostics("d")
			.recorder("# legend\n1 08:00:00.0 out=\"Waiting\"\n2 08:00:00.6 err=\"java.lang.NullPointerException\"\n")
			.build();

		JsonObject payload = collector.payload(snapshot, plugin, "broken", EnumSet.of(BugReportSection.RECORDER));

		assertEquals("# legend\n1 08:00:00.0 out=\"Waiting\"\n", payload.get("recorder").getAsString());
	}

	@Test
	public void previewExplainsTheActivityShorthandAndCopiesFewerLines()
	{
		StringBuilder recorder = new StringBuilder(FlightRecorder.LEGEND + "\n");
		for (int i = 0; i < 60; i++)
		{
			recorder.append(i).append(" 08:00:00.0 p=3200,").append(i).append(",0\n");
		}
		JsonObject payload = new JsonObject();
		payload.addProperty("description", "stuck");
		payload.addProperty("microbotVersion", "2.6.48");
		payload.addProperty("recorder", recorder.toString());

		String shown = collector.preview(payload, "Blast Furnace", false);
		String copied = collector.preview(payload, "Blast Furnace", true);

		assertTrue(shown.contains(BugReportCollector.ACTIVITY_EXPLANATION));
		assertFalse(shown.contains(FlightRecorder.LEGEND));
		assertTrue(shown.contains("... and 20 earlier lines, which are also sent"));
		assertTrue(copied.contains("... and 45 earlier lines, left out here"));
	}

	@Test
	public void lastLinesKeepsTheTailAndCountsWhatWasLeftOut()
	{
		assertEquals("... and 1 earlier lines, which are also sent\nb\nc", BugReportCollector.lastLines("a\nb\nc\n", 2, false));
		assertEquals("... and 2 earlier lines, left out here\nc", BugReportCollector.lastLines("a\n\nc\n\n", 1, true));
		assertEquals("a\nb\n", BugReportCollector.lastLines("a\nb\n", 2, false));
		assertEquals("", BugReportCollector.lastLines("", 2, false));
		assertEquals("... and 2 earlier lines, left out here\n", BugReportCollector.lastLines("a\nb\n", 0, true));
	}

	@Test
	public void clientReportHasNoPluginAndListsEveryError()
	{
		when(errors.recentErrors(null)).thenReturn("1x client: walker failed\n");

		JsonObject payload = collector.payload(SNAPSHOT, null, "walker", EnumSet.allOf(BugReportSection.class));

		assertFalse(payload.has("plugin"));
		assertFalse(payload.has("pluginConfig"));
		assertEquals("1x client: walker failed\n", payload.get("error").getAsString());
	}

	@Test
	public void longDescriptionIsCut()
	{
		String text = "x".repeat(BugReportCollector.MAX_DESCRIPTION_CHARS + 50);

		JsonObject payload = collector.payload(SNAPSHOT, null, text, EnumSet.noneOf(BugReportSection.class));

		assertEquals(BugReportCollector.MAX_DESCRIPTION_CHARS, payload.get("description").getAsString().length());
	}

	@Test
	public void pluginSettingsHideSecretsSkipHiddenItemsAndRedactPaths()
	{
		JsonObject settings = collector.pluginSettings(plugin);

		assertEquals("STEEL", settings.get("bars").getAsString());
		assertEquals("[hidden]", settings.get("webhook").getAsString());
		assertFalse(settings.has("lastRun"));
		assertEquals("saved in [path]", settings.get("notes").getAsString());
	}

	@Test
	public void pluginWithoutConfigHasNoSettings()
	{
		assertNull(collector.pluginSettings(new FurnacePlugin()));
	}

	@Test
	public void previewUsesTheFormsWordsAndThePluginsDisplayName()
	{
		JsonObject payload = collector.payload(SNAPSHOT, plugin, "broken", EnumSet.allOf(BugReportSection.class));

		String preview = collector.preview(payload, "Blast Furnace", false);

		assertTrue(preview, preview.startsWith("== What you wrote ==\nbroken\n"));
		assertTrue(preview, preview.contains("== Versions ==\nMicrobot "));
		assertTrue(preview, preview.contains(", Blast Furnace 1.4.2\n"));
		assertTrue(preview, preview.contains("== Technical details for the Microbot team ==\nMicrobot diagnostics\n"));
		assertTrue(preview, preview.indexOf("== Technical details") > preview.indexOf("== Screenshot =="));
		assertTrue(preview, preview.contains("== Plugin settings =="));
		assertTrue(preview, preview.contains("== Game state ==\nworld: 360\n"));
		assertTrue(preview, preview.contains("== Recent log ==\n2026-10-24 line\n"));
		assertTrue(preview, preview.contains("== Screenshot ==\nThe game screenshot shown above (1 KB)"));
		assertFalse(preview, preview.contains("/9j/"));
		assertFalse(preview, preview.contains("FurnacePlugin"));
	}

	@Test
	public void namesFoundInTheLogAreRemovedFromEverySection()
	{
		when(errors.recentErrors("FurnacePlugin")).thenReturn("1x FurnacePlugin: could not trade MuleB\n");
		when(configManager.getConfiguration("furnace", "notes")).thenReturn("give bars to MuleB");
		BugReportSnapshot snapshot = BugReportSnapshot.builder()
			.diagnostics("Microbot diagnostics")
			.gameState("{\"interacting\": \"MuleB\"}")
			.log("line\n")
			.names(java.util.Set.of("MuleB"))
			.build();

		JsonObject payload = collector.payload(snapshot, plugin, "broken", EnumSet.allOf(BugReportSection.class));

		assertFalse(payload.toString(), payload.toString().contains("MuleB"));
		assertTrue(payload.get("error").getAsString().contains("could not trade [player]"));
	}

	@Test
	public void namesInNameSettingsAreRemovedEverywhere()
	{
		when(configManager.getConfiguration("furnace", "teammateName")).thenReturn("Zezima");
		when(errors.recentErrors("FurnacePlugin")).thenReturn("1x FurnacePlugin: waiting for Zezima\n");
		BugReportSnapshot snapshot = BugReportSnapshot.builder()
			.diagnostics("Microbot diagnostics")
			.gameState("{\"world\": 360}")
			.log("12:00 waiting for zezima at the bank\n")
			.build();

		JsonObject payload = collector.payload(snapshot, plugin, "broken", EnumSet.allOf(BugReportSection.class));

		assertFalse(payload.toString(), payload.toString().toLowerCase().contains("zezima"));
		assertTrue(payload.get("log").getAsString().contains("waiting for [player] at the bank"));
		assertTrue(payload.get("pluginConfig").getAsString().contains("\"teammateName\": \"[player]\""));
		assertEquals(java.util.Set.of("Zezima"), collector.settingNames(plugin));
	}

	@Test
	public void readableRendersObjectsListsAndEmptyLists()
	{
		String shown = collector.readable("{\"world\":360,\"location\":{\"x\":3185,\"y\":3436},\"inventory\":[{\"id\":379,\"name\":\"Lobster\",\"quantity\":5}],\"equipment\":[]}");

		assertEquals("world: 360\nlocation:\n  x: 3185\n  y: 3436\ninventory:\n  - id: 379, name: Lobster, quantity: 5\nequipment: none\n", shown);
		assertEquals("not json", collector.readable("not json"));
	}

	@Test
	public void previewShowsOnlyTheLastLogLinesAndSaysTheRestIsSent()
	{
		StringBuilder log = new StringBuilder();
		for (int i = 1; i <= BugReportCollector.PREVIEW_LOG_LINES + 5; i++)
		{
			log.append("line ").append(i).append('\n');
		}

		String shown = BugReportCollector.lastLines(log.toString(), BugReportCollector.PREVIEW_LOG_LINES, false);
		String copied = BugReportCollector.lastLines(log.toString(), BugReportCollector.PREVIEW_LOG_LINES, true);

		assertTrue(shown, shown.startsWith("... and 5 earlier lines, which are also sent\nline 6\n"));
		assertTrue(shown, shown.endsWith("line " + (BugReportCollector.PREVIEW_LOG_LINES + 5)));
		assertTrue(copied, copied.startsWith("... and 5 earlier lines, left out here\nline 6\n"));
		assertEquals("a\nb\n", BugReportCollector.lastLines("a\nb\n", BugReportCollector.PREVIEW_LOG_LINES, false));
	}

	@Test
	public void captureFinishesWhenTheGameThreadAndFrameNeverAnswer() throws Exception
	{
		long start = System.nanoTime();

		BugReportSnapshot snapshot = collector.capture().get(BugReportCollector.CAPTURE_TIMEOUT_SECONDS + 5, TimeUnit.SECONDS);

		assertNull(snapshot.getGameState());
		assertNull(snapshot.getScreenshot());
		assertFalse(snapshot.isLoggedIn());
		assertTrue(System.nanoTime() - start < TimeUnit.SECONDS.toNanos(BugReportCollector.CAPTURE_TIMEOUT_SECONDS + 4));
	}

	@Test
	public void submitAddsTheSessionId()
	{
		UUID session = UUID.fromString("00000000-0000-0000-0000-000000000007");
		when(sessions.getMicrobotSessionId()).thenReturn(session);
		when(api.submitBugReport(any())).thenReturn(CompletableFuture.completedFuture(new BugReportReceipt("MB-7F3K9Q", "secret")));
		JsonObject payload = collector.payload(SNAPSHOT, plugin, "broken", EnumSet.noneOf(BugReportSection.class));

		assertEquals("MB-7F3K9Q", collector.submit(payload).join().getCode());

		ArgumentCaptor<JsonObject> sent = ArgumentCaptor.forClass(JsonObject.class);
		verify(api).submitBugReport(sent.capture());
		assertEquals(session.toString(), sent.getValue().get("sessionId").getAsString());
		assertFalse(payload.has("sessionId"));
	}

	@Test
	public void submitWithoutSessionFailsWithoutCallingTheApi() throws InterruptedException
	{
		when(sessions.getMicrobotSessionId()).thenReturn(null);

		assertFalse(collector.canSubmit());
		try
		{
			collector.submit(new JsonObject()).get();
			fail();
		}
		catch (ExecutionException e)
		{
			assertEquals(403, ((MicrobotApi.BugReportRejectedException) e.getCause()).getStatus());
		}
		verify(api, never()).submitBugReport(any());
	}
}
