package net.runelite.client.plugins.microbot.alerts;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class AlertEvent
{
	public static final String HEARTBEAT = "heartbeat";
	public static final String SCRIPT_STARTED = "script_started";
	public static final String SCRIPT_STOPPED = "script_stopped";
	public static final String SCRIPT_ERROR = "script_error";
	public static final String LOGGED_OUT = "logged_out";
	public static final String DIED = "died";
	public static final String BANNED = "banned";

	static final int MAX_PLUGIN_CHARS = 100;
	static final int MAX_VERSION_CHARS = 30;
	static final int MAX_MESSAGE_CHARS = 500;
	static final int MAX_RUNNING_SCRIPTS = 20;
	static final int MAX_AVAILABLE_SCRIPTS = 300;
	static final int MAX_SCRIPT_NAME_CHARS = 80;
	static final Pattern SCRIPT_ID = Pattern.compile("^[A-Za-z0-9_.$]{1,200}$");

	String type;
	String plugin;
	String pluginVersion;
	String reason;
	String message;
	@Builder.Default
	List<String> runningScripts = List.of();
	@Builder.Default
	List<AvailableScript> availableScripts = List.of();
	boolean loggedIn;
	boolean breakActive;
	boolean intended;

	public JsonObject toJson(UUID sessionId)
	{
		boolean stopped = SCRIPT_STOPPED.equals(type);
		JsonObject json = new JsonObject();
		json.addProperty("sessionId", sessionId.toString());
		json.addProperty("type", type);
		json.addProperty("plugin", truncate(plugin, MAX_PLUGIN_CHARS));
		json.addProperty("pluginVersion", truncate(pluginVersion, MAX_VERSION_CHARS));
		json.addProperty("reason", stopped ? reason : null);
		json.addProperty("message", stopped || SCRIPT_ERROR.equals(type) ? truncate(message, MAX_MESSAGE_CHARS) : null);
		JsonArray scripts = new JsonArray();
		runningScripts.stream()
			.map(script -> truncate(script, MAX_PLUGIN_CHARS))
			.filter(Objects::nonNull)
			.distinct()
			.limit(MAX_RUNNING_SCRIPTS)
			.forEach(scripts::add);
		json.add("runningScripts", scripts);
		json.add("availableScripts", availableScriptsJson());
		json.addProperty("loggedIn", loggedIn);
		json.addProperty("breakActive", breakActive);
		json.addProperty("intended", LOGGED_OUT.equals(type) && intended);
		return json;
	}

	private JsonArray availableScriptsJson()
	{
		Set<String> ids = new HashSet<>();
		JsonArray scripts = new JsonArray();
		availableScripts.stream()
			.filter(Objects::nonNull)
			.filter(script -> script.getId() != null && SCRIPT_ID.matcher(script.getId()).matches())
			.map(script -> new AvailableScript(script.getId(), truncate(script.getName(), MAX_SCRIPT_NAME_CHARS)))
			.filter(script -> script.getName() != null)
			.sorted(Comparator.comparing(AvailableScript::getName, String.CASE_INSENSITIVE_ORDER)
				.thenComparing(AvailableScript::getName)
				.thenComparing(AvailableScript::getId))
			.filter(script -> ids.add(script.getId()))
			.limit(MAX_AVAILABLE_SCRIPTS)
			.forEach(script ->
			{
				JsonObject entry = new JsonObject();
				entry.addProperty("id", script.getId());
				entry.addProperty("name", script.getName());
				scripts.add(entry);
			});
		return scripts;
	}

	static String truncate(String value, int max)
	{
		if (value == null)
		{
			return null;
		}
		String trimmed = value.trim();
		if (trimmed.isEmpty())
		{
			return null;
		}
		return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
	}

	@Value
	public static class AvailableScript
	{
		String id;
		String name;
	}
}
