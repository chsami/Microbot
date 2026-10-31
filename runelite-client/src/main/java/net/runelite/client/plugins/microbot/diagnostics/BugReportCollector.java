package net.runelite.client.plugins.microbot.diagnostics;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonParseException;
import com.google.gson.JsonObject;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.Base64;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.ClientSessionManager;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigDescriptor;
import net.runelite.client.config.ConfigItemDescriptor;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptHeartbeatRegistry;
import net.runelite.client.plugins.microbot.alerts.DashboardScreenshot;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.ui.DrawManager;
import net.runelite.client.util.ImageUtil;

@Slf4j
@Singleton
public class BugReportCollector
{
	static final int MAX_LOG_BYTES = 256 * 1024;
	static final int MAX_DESCRIPTION_CHARS = 4000;
	static final int MAX_SETTING_CHARS = 500;
	static final long CAPTURE_TIMEOUT_SECONDS = 3;
	static final int PREVIEW_LOG_LINES = 40;
	static final int COPY_ACTIVITY_LINES = 15;
	static final String ACTIVITY_EXPLANATION = "Shorthand for the Microbot team: your position (p), animation (a), target (t), items gained (+) or lost (-) by item number,"
		+ " game settings (v), windows opened (w+) and closed (w-), game messages, your clicks and the script's status and messages."
		+ " A line starting with ! marks a stretch where nothing changed in the game while a script was running."
		+ " Players you click or target show as \"player\"; script messages are shown as the script printed them.";
	static final Pattern NAME_SETTING = Pattern.compile(
		"(?i)(player|teammate|mate|mule|partner|friend|leader|master|follower|buddy|clan|account|user|display|character).*names?|^(rsn|rsns|username|teammate|mule|partner|leader|master)$");

	private final Client client;
	private final ClientThread clientThread;
	private final ItemManager itemManager;
	private final ConfigManager configManager;
	private final PluginManager pluginManager;
	private final MicrobotPluginManager microbotPluginManager;
	private final DiagnosticReportCollector diagnosticReportCollector;
	private final ScriptErrorReporter scriptErrorReporter;
	private final DrawManager drawManager;
	private final ClientSessionManager clientSessionManager;
	private final MicrobotApi microbotApi;
	private final FlightRecorder flightRecorder;
	private final Gson gson;
	private final boolean disableTelemetry;

	@Inject
	BugReportCollector(Client client, ClientThread clientThread, ItemManager itemManager, ConfigManager configManager,
		PluginManager pluginManager, MicrobotPluginManager microbotPluginManager, DiagnosticReportCollector diagnosticReportCollector,
		ScriptErrorReporter scriptErrorReporter, DrawManager drawManager, ClientSessionManager clientSessionManager,
		MicrobotApi microbotApi, FlightRecorder flightRecorder, Gson gson, @Named("disableTelemetry") boolean disableTelemetry)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.itemManager = itemManager;
		this.configManager = configManager;
		this.pluginManager = pluginManager;
		this.microbotPluginManager = microbotPluginManager;
		this.diagnosticReportCollector = diagnosticReportCollector;
		this.scriptErrorReporter = scriptErrorReporter;
		this.drawManager = drawManager;
		this.clientSessionManager = clientSessionManager;
		this.microbotApi = microbotApi;
		this.flightRecorder = flightRecorder;
		this.disableTelemetry = disableTelemetry;
		this.gson = gson.newBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	}

	public CompletableFuture<BugReportSnapshot> capture()
	{
		String diagnostics = diagnosticReportCollector.collectReport();
		String recorder = flightRecorder.dump();
		CompletableFuture<JsonObject> gameState = gameState();
		CompletableFuture<byte[]> screenshot = screenshot();
		CompletableFuture<String> rawLog = CompletableFuture.supplyAsync(this::readLog)
			.exceptionally(e -> null)
			.completeOnTimeout(null, CAPTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
		return CompletableFuture.allOf(gameState, screenshot, rawLog).thenApply(ignored ->
		{
			JsonObject state = gameState.join();
			String raw = rawLog.join();
			Set<String> names = scriptErrorReporter.namesIn(raw);
			return BugReportSnapshot.builder()
				.diagnostics(diagnostics)
				.gameState(state == null ? null : gson.toJson(state))
				.loggedIn(state != null && state.has("location"))
				.screenshot(screenshot.join())
				.log(raw == null ? null : scriptErrorReporter.scrubLogNames(raw, names))
				.recorder(recorder)
				.names(names)
				.build();
		});
	}

	public JsonObject payload(BugReportSnapshot snapshot, @Nullable Plugin plugin, String description, Set<BugReportSection> include)
	{
		JsonObject payload = new JsonObject();
		payload.addProperty("microbotVersion", RuneLiteProperties.getMicrobotVersion());
		String internalName = plugin == null ? null : plugin.getClass().getSimpleName();
		if (plugin != null)
		{
			payload.addProperty("plugin", internalName);
			payload.addProperty("pluginVersion", pluginVersion(plugin));
		}
		String text = description == null ? "" : description.trim();
		payload.addProperty("description", text.length() > MAX_DESCRIPTION_CHARS ? text.substring(0, MAX_DESCRIPTION_CHARS) : text);
		payload.addProperty("diagnostics", snapshot.getDiagnostics());
		Set<String> names = new LinkedHashSet<>(snapshot.getNames());
		names.addAll(scriptErrorReporter.namesIn(null));
		if (plugin != null)
		{
			names.addAll(settingNames(plugin));
		}
		if (include.contains(BugReportSection.PLUGIN_SETTINGS) && plugin != null)
		{
			JsonObject settings = pluginSettings(plugin);
			if (settings != null)
			{
				payload.addProperty("pluginConfig", ScriptErrorReporter.scrubNames(gson.toJson(settings), names));
			}
		}
		if (include.contains(BugReportSection.GAME_STATE) && snapshot.getGameState() != null)
		{
			payload.addProperty("gameState", ScriptErrorReporter.scrubNames(snapshot.getGameState(), names));
		}
		if (include.contains(BugReportSection.LOG))
		{
			String errors = scriptErrorReporter.recentErrors(internalName);
			if (!errors.isEmpty())
			{
				payload.addProperty("error", ScriptErrorReporter.scrubNames(errors, names));
			}
			if (snapshot.getLog() != null)
			{
				payload.addProperty("log", ScriptErrorReporter.scrubNames(snapshot.getLog(), names));
			}
		}
		if (include.contains(BugReportSection.RECORDER) && snapshot.getRecorder() != null)
		{
			String recorder = include.contains(BugReportSection.LOG) ? snapshot.getRecorder() : FlightRecorder.withoutErrors(snapshot.getRecorder());
			payload.addProperty("recorder", ScriptErrorReporter.scrubNames(recorder, names));
		}
		if (include.contains(BugReportSection.SCREENSHOT) && snapshot.getScreenshot() != null)
		{
			payload.addProperty("screenshot", Base64.getEncoder().encodeToString(snapshot.getScreenshot()));
		}
		return payload;
	}

	public String preview(JsonObject payload, String pluginLabel, boolean forCopy)
	{
		StringBuilder out = new StringBuilder();
		section(out, "What you wrote", text(payload, "description"));
		String versions = "Microbot " + text(payload, "microbotVersion");
		if (payload.has("plugin"))
		{
			versions += ", " + pluginLabel + " " + text(payload, "pluginVersion");
		}
		section(out, "Versions", versions);
		if (payload.has("pluginConfig"))
		{
			section(out, "Plugin settings", readable(text(payload, "pluginConfig")));
		}
		if (payload.has("gameState"))
		{
			section(out, "Game state", readable(text(payload, "gameState")));
		}
		if (payload.has("error"))
		{
			section(out, "Recent errors", text(payload, "error"));
		}
		if (payload.has("log"))
		{
			section(out, "Recent log", lastLines(text(payload, "log"), PREVIEW_LOG_LINES, forCopy));
		}
		if (payload.has("recorder"))
		{
			section(out, "Recent activity", ACTIVITY_EXPLANATION + "\n" + lastLines(FlightRecorder.withoutLegend(text(payload, "recorder")),
				forCopy ? COPY_ACTIVITY_LINES : PREVIEW_LOG_LINES, forCopy));
		}
		if (payload.has("screenshot"))
		{
			int bytes = Base64.getDecoder().decode(text(payload, "screenshot")).length;
			section(out, "Screenshot", forCopy
				? "A game screenshot (" + Math.max(1, bytes / 1024) + " KB) is not included in copied text"
				: "The game screenshot shown above (" + Math.max(1, bytes / 1024) + " KB)");
		}
		section(out, "Technical details for the Microbot team", text(payload, "diagnostics"));
		return out.toString();
	}

	static String lastLines(String text, int count, boolean forCopy)
	{
		int end = text.length();
		while (end > 0 && text.charAt(end - 1) == '\n')
		{
			end--;
		}
		int lines = 1;
		for (int i = text.indexOf('\n'); i >= 0 && i < end; i = text.indexOf('\n', i + 1))
		{
			lines++;
		}
		if (lines <= count)
		{
			return text;
		}
		int earlier = lines - count;
		String prefix = "... and " + earlier + (forCopy ? " earlier lines, left out here\n" : " earlier lines, which are also sent\n");
		if (count <= 0)
		{
			return prefix;
		}
		int start = 0;
		for (int skipped = 0; skipped < earlier; skipped++)
		{
			start = text.indexOf('\n', start) + 1;
		}
		return prefix + text.substring(start, end);
	}

	String readable(String json)
	{
		JsonElement element;
		try
		{
			element = gson.fromJson(json, JsonElement.class);
		}
		catch (JsonParseException e)
		{
			return json;
		}
		if (element == null || !element.isJsonObject())
		{
			return json;
		}
		StringBuilder out = new StringBuilder();
		render(out, element, 0);
		return out.toString();
	}

	private static void render(StringBuilder out, JsonElement element, int depth)
	{
		String indent = "  ".repeat(depth);
		if (element.isJsonObject())
		{
			for (Map.Entry<String, JsonElement> entry : element.getAsJsonObject().entrySet())
			{
				JsonElement value = entry.getValue();
				if (value.isJsonNull() || value.isJsonPrimitive())
				{
					out.append(indent).append(entry.getKey()).append(": ").append(value.isJsonNull() ? "" : value.getAsString()).append('\n');
				}
				else if (value.isJsonArray() && value.getAsJsonArray().size() == 0)
				{
					out.append(indent).append(entry.getKey()).append(": none\n");
				}
				else
				{
					out.append(indent).append(entry.getKey()).append(":\n");
					render(out, value, depth + 1);
				}
			}
			return;
		}
		for (JsonElement item : element.getAsJsonArray())
		{
			if (item.isJsonObject() && item.getAsJsonObject().entrySet().stream().allMatch(e -> e.getValue().isJsonPrimitive()))
			{
				out.append(indent).append("- ").append(item.getAsJsonObject().entrySet().stream()
					.map(e -> e.getKey() + ": " + e.getValue().getAsString())
					.collect(Collectors.joining(", "))).append('\n');
			}
			else if (item.isJsonPrimitive())
			{
				out.append(indent).append("- ").append(item.getAsString()).append('\n');
			}
			else
			{
				out.append(indent).append("-\n");
				render(out, item, depth + 1);
			}
		}
	}

	private static String text(JsonObject payload, String key)
	{
		return payload.has(key) && !payload.get(key).isJsonNull() ? payload.get(key).getAsString() : "";
	}

	private static void section(StringBuilder out, String title, String body)
	{
		out.append("== ").append(title).append(" ==\n").append(body.endsWith("\n") ? body : body + "\n").append('\n');
	}

	public boolean isTelemetryDisabled()
	{
		return disableTelemetry;
	}

	public boolean canSubmit()
	{
		return clientSessionManager.getMicrobotSessionId() != null;
	}

	public CompletableFuture<BugReportReceipt> submit(JsonObject payload)
	{
		UUID sessionId = clientSessionManager.getMicrobotSessionId();
		if (sessionId == null)
		{
			CompletableFuture<BugReportReceipt> failed = new CompletableFuture<>();
			failed.completeExceptionally(new MicrobotApi.BugReportRejectedException(403, null));
			return failed;
		}
		JsonObject body = payload.deepCopy();
		body.addProperty("sessionId", sessionId.toString());
		return microbotApi.submitBugReport(body);
	}

	String pluginVersion(Plugin plugin)
	{
		String internalName = plugin.getClass().getSimpleName();
		PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class);
		return microbotPluginManager.getInstalledPluginVersion(internalName)
			.orElse(descriptor == null ? null : descriptor.version());
	}

	Set<String> settingNames(Plugin plugin)
	{
		Set<String> names = new LinkedHashSet<>();
		Config config = pluginManager.getPluginConfigProxy(plugin);
		ConfigDescriptor descriptor = config == null ? null : configManager.getConfigDescriptor(config);
		if (descriptor == null || descriptor.getGroup() == null)
		{
			return names;
		}
		for (ConfigItemDescriptor item : descriptor.getItems())
		{
			if (!NAME_SETTING.matcher(item.getItem().keyName()).find() && !NAME_SETTING.matcher(item.getItem().name()).find())
			{
				continue;
			}
			String value = configManager.getConfiguration(descriptor.getGroup().value(), item.getItem().keyName());
			if (value == null)
			{
				continue;
			}
			for (String part : value.split("[,;\\n]"))
			{
				String name = part.trim();
				if (!name.isEmpty() && name.length() <= ScriptErrorReporter.MAX_NAME_LENGTH && !name.equalsIgnoreCase("true") && !name.equalsIgnoreCase("false"))
				{
					names.add(name);
				}
			}
		}
		return names;
	}

	@Nullable
	JsonObject pluginSettings(Plugin plugin)
	{
		Config config = pluginManager.getPluginConfigProxy(plugin);
		if (config == null)
		{
			return null;
		}
		ConfigDescriptor descriptor = configManager.getConfigDescriptor(config);
		if (descriptor == null || descriptor.getGroup() == null)
		{
			return null;
		}
		String group = descriptor.getGroup().value();
		JsonObject settings = new JsonObject();
		for (ConfigItemDescriptor item : descriptor.getItems())
		{
			if (item.getItem().hidden())
			{
				continue;
			}
			String key = item.getItem().keyName();
			if (item.getItem().secret())
			{
				settings.addProperty(key, "[hidden]");
				continue;
			}
			String value = configManager.getConfiguration(group, key);
			if (value == null)
			{
				continue;
			}
			value = DiagnosticReport.redact(value);
			settings.addProperty(key, value.length() > MAX_SETTING_CHARS ? value.substring(0, MAX_SETTING_CHARS) + "..." : value);
		}
		return settings;
	}

	private CompletableFuture<JsonObject> gameState()
	{
		CompletableFuture<JsonObject> result = new CompletableFuture<>();
		clientThread.invoke(() ->
		{
			try
			{
				result.complete(captureGameState());
			}
			catch (RuntimeException e)
			{
				log.debug("Bug report could not read the game state", e);
				result.complete(null);
			}
		});
		return result.completeOnTimeout(null, CAPTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private JsonObject captureGameState()
	{
		JsonObject state = new JsonObject();
		GameState gameState = client.getGameState();
		state.addProperty("gameState", gameState == null ? null : gameState.name());
		state.add("scripts", gson.toJsonTree(ScriptHeartbeatRegistry.getAllHealth()));
		Player player = client.getLocalPlayer();
		if (gameState != GameState.LOGGED_IN || player == null)
		{
			return state;
		}
		state.addProperty("world", client.getWorld());
		WorldPoint location = player.getLocalLocation() == null ? null : WorldPoint.fromLocalInstance(client, player.getLocalLocation());
		if (location != null)
		{
			JsonObject point = new JsonObject();
			point.addProperty("x", location.getX());
			point.addProperty("y", location.getY());
			point.addProperty("plane", location.getPlane());
			point.addProperty("regionId", location.getRegionID());
			point.addProperty("instance", client.getTopLevelWorldView() != null && client.getTopLevelWorldView().isInstance());
			state.add("location", point);
		}
		state.addProperty("animation", player.getAnimation());
		state.addProperty("moving", player.getPoseAnimation() != player.getIdlePoseAnimation());
		Actor target = player.getInteracting();
		if (target instanceof NPC)
		{
			state.addProperty("interacting", target.getName() + " (" + ((NPC) target).getId() + ")");
		}
		else if (target != null)
		{
			state.addProperty("interacting", "another player");
		}
		state.addProperty("hitpoints", client.getBoostedSkillLevel(Skill.HITPOINTS) + "/" + client.getRealSkillLevel(Skill.HITPOINTS));
		state.addProperty("prayer", client.getBoostedSkillLevel(Skill.PRAYER) + "/" + client.getRealSkillLevel(Skill.PRAYER));
		state.addProperty("runEnergy", client.getEnergy() / 100);
		state.addProperty("runEnabled", client.getVarpValue(VarPlayerID.OPTION_RUN) == 1);
		JsonObject levels = new JsonObject();
		for (Skill skill : Skill.values())
		{
			levels.addProperty(skill.getName(), client.getRealSkillLevel(skill));
		}
		state.add("levels", levels);
		state.add("inventory", items(client.getItemContainer(InventoryID.INV)));
		state.add("equipment", items(client.getItemContainer(InventoryID.WORN)));
		return state;
	}

	private JsonArray items(ItemContainer container)
	{
		JsonArray items = new JsonArray();
		if (container == null)
		{
			return items;
		}
		for (Item item : container.getItems())
		{
			if (item.getId() <= 0 || item.getQuantity() <= 0)
			{
				continue;
			}
			JsonObject entry = new JsonObject();
			entry.addProperty("id", item.getId());
			entry.addProperty("name", itemManager.getItemComposition(item.getId()).getName());
			entry.addProperty("quantity", item.getQuantity());
			items.add(entry);
		}
		return items;
	}

	private CompletableFuture<byte[]> screenshot()
	{
		CompletableFuture<byte[]> result = new CompletableFuture<>();
		drawManager.requestNextFrameListener(image ->
		{
			BufferedImage frame = ImageUtil.bufferedImageFromImage(image);
			CompletableFuture.runAsync(() ->
			{
				try
				{
					result.complete(DashboardScreenshot.encode(frame));
				}
				catch (IOException | RuntimeException e)
				{
					log.debug("Bug report could not encode the screenshot", e);
					result.complete(null);
				}
			});
		});
		return result.completeOnTimeout(null, CAPTURE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
	}

	private String readLog()
	{
		try
		{
			return ScriptErrorReporter.tail(new File(RuneLite.LOGS_DIR, "client.log"), MAX_LOG_BYTES);
		}
		catch (IOException e)
		{
			log.debug("Bug report could not read the client log", e);
			return null;
		}
	}
}
