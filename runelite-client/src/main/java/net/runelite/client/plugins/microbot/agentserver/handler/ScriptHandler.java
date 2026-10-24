package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginInstantiationException;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.agentserver.AgentServerPlugin;

import javax.swing.*;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Slf4j
public class ScriptHandler extends AgentHandler {

	private static final String BASE_PATH = "/scripts";
	private final ConcurrentHashMap<String, ScriptSession> sessions = new ConcurrentHashMap<>();

	public ScriptHandler(Gson gson) {
		super(gson);
	}

	@Override
	public String getPath() {
		return BASE_PATH;
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException {
		String sub = getSubPath(exchange, BASE_PATH);

		switch (sub) {
			case "":
			case "/":
				handleList(exchange);
				break;
			case "/start":
				handleStart(exchange);
				break;
			case "/stop":
				handleStop(exchange);
				break;
			case "/status":
				handleStatus(exchange);
				break;
			case "/results":
				handleResults(exchange);
				break;
			case "/health":
				handleHealth(exchange);
				break;
			case "/pause":
				handlePause(exchange, true);
				break;
			case "/resume":
				handlePause(exchange, false);
				break;
			default:
				sendJson(exchange, 404, errorResponse("Unknown endpoint: " + BASE_PATH + sub));
		}
	}

	private void handleList(HttpExchange exchange) throws IOException {
		try {
			requireGet(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		List<Map<String, Object>> scripts = new ArrayList<>();
		PluginManager pm = getPluginManager();

		if (pm == null) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("count", 0);
			result.put("scripts", scripts);
			result.put("error", "PluginManager not available");
			sendJson(exchange, 503, result);
			return;
		}

		for (Plugin plugin : pm.getPlugins()) {
			if (!isMicrobotPlugin(plugin)) continue;

			PluginDescriptor desc = plugin.getClass().getAnnotation(PluginDescriptor.class);
			if (desc == null) continue;

			Map<String, Object> entry = new LinkedHashMap<>();
			entry.put("name", desc.name());
			entry.put("className", plugin.getClass().getName());
			entry.put("active", pm.isActive(plugin));
			entry.put("enabled", pm.isPluginEnabled(plugin));
			scripts.add(entry);
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("count", scripts.size());
		result.put("scripts", scripts);
		sendJson(exchange, 200, result);
	}

	private void handleStart(HttpExchange exchange) throws IOException {
		try {
			requirePost(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		Map<String, Object> body = readJsonBody(exchange);
		Plugin plugin = resolvePlugin(body);
		if (plugin == null) {
			sendJson(exchange, 404, errorResponse("Plugin not found. Provide className or name in the request body."));
			return;
		}

		PluginManager pm = getPluginManager();
		if (pm.isActive(plugin)) {
			sendJson(exchange, 400, errorResponse("Plugin is already running"));
			return;
		}

		AtomicBoolean success = new AtomicBoolean(false);
		AtomicReference<String> error = new AtomicReference<>();

		try {
			SwingUtilities.invokeAndWait(() -> {
				try {
					pm.setPluginEnabled(plugin, true);
					success.set(pm.startPlugin(plugin));
				} catch (PluginInstantiationException e) {
					log.error("Failed to start plugin {}", plugin.getClass().getSimpleName(), e);
					error.set(e.getMessage());
				}
			});
		} catch (Exception e) {
			log.error("Error starting plugin on EDT", e);
			sendJson(exchange, 500, errorResponse("Failed to start plugin: " + e.getMessage()));
			return;
		}

		String className = plugin.getClass().getName();
		PluginDescriptor desc = plugin.getClass().getAnnotation(PluginDescriptor.class);
		String displayName = desc != null ? desc.name() : plugin.getClass().getSimpleName();

		if (success.get()) {
			ScriptSession session = new ScriptSession(displayName, className);
			sessions.put(className, session);

			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", true);
			result.put("name", displayName);
			result.put("className", className);
			result.put("status", "RUNNING");
			result.put("startedAt", Instant.now().toString());
			sendJson(exchange, 200, result);
		} else {
			String errMsg = error.get() != null ? error.get() : "Plugin failed to start";
			sendJson(exchange, 500, errorResponse(errMsg));
		}
	}

	private void handleStop(HttpExchange exchange) throws IOException {
		try {
			requirePost(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		Map<String, Object> body = readJsonBody(exchange);
		Plugin plugin = resolvePlugin(body);
		if (plugin == null) {
			sendJson(exchange, 404, errorResponse("Plugin not found. Provide className or name in the request body."));
			return;
		}

		if (plugin instanceof AgentServerPlugin) {
			sendJson(exchange, 400, errorResponse("Refusing to stop the Agent Server through its own API"));
			return;
		}

		PluginManager pm = getPluginManager();
		if (!pm.isActive(plugin)) {
			sendJson(exchange, 400, errorResponse("Plugin is not running"));
			return;
		}

		AtomicBoolean success = new AtomicBoolean(false);
		AtomicReference<String> error = new AtomicReference<>();

		try {
			SwingUtilities.invokeAndWait(() -> {
				try {
					pm.setPluginEnabled(plugin, false);
					success.set(pm.stopPlugin(plugin));
				} catch (PluginInstantiationException e) {
					log.error("Failed to stop plugin {}", plugin.getClass().getSimpleName(), e);
					error.set(e.getMessage());
				}
			});
		} catch (Exception e) {
			log.error("Error stopping plugin on EDT", e);
			sendJson(exchange, 500, errorResponse("Failed to stop plugin: " + e.getMessage()));
			return;
		}

		String className = plugin.getClass().getName();
		ScriptSession session = sessions.get(className);
		if (session != null) {
			session.markStopped();
		}

		if (success.get()) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("success", true);
			result.put("className", className);
			result.put("status", "STOPPED");
			sendJson(exchange, 200, result);
		} else {
			String errMsg = error.get() != null ? error.get() : "Plugin failed to stop";
			sendJson(exchange, 500, errorResponse(errMsg));
		}
	}

	private void handleStatus(HttpExchange exchange) throws IOException {
		try {
			requireGet(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		Map<String, String> params = parseQuery(exchange.getRequestURI());
		Plugin plugin = resolvePluginFromParams(params);
		if (plugin == null) {
			sendJson(exchange, 404, errorResponse("Plugin not found. Provide ?className= or ?name= parameter."));
			return;
		}

		PluginManager pm = getPluginManager();
		String className = plugin.getClass().getName();
		boolean active = pm != null && pm.isActive(plugin);

		ScriptSession session = sessions.get(className);
		if (session != null) {
			if (active && session.getStatus() != ScriptSession.Status.RUNNING) {
				sessions.put(className, new ScriptSession(
						plugin.getClass().getAnnotation(PluginDescriptor.class) != null
								? plugin.getClass().getAnnotation(PluginDescriptor.class).name()
								: plugin.getClass().getSimpleName(),
						className));
				session = sessions.get(className);
			}
			sendJson(exchange, 200, session.toMap(active));
		} else {
			PluginDescriptor desc = plugin.getClass().getAnnotation(PluginDescriptor.class);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("name", desc != null ? desc.name() : plugin.getClass().getSimpleName());
			result.put("className", className);
			result.put("active", active);
			result.put("status", active ? "RUNNING" : "STOPPED");
			sendJson(exchange, 200, result);
		}
	}

	private void handleResults(HttpExchange exchange) throws IOException {
		String method = exchange.getRequestMethod().toUpperCase();

		if ("GET".equals(method)) {
			Map<String, String> params = parseQuery(exchange.getRequestURI());
			String className = params.get("className");
			if (className == null || className.isEmpty()) {
				className = params.get("name");
				if (className != null) {
					Plugin p = findPluginByName(className);
					className = p != null ? p.getClass().getName() : null;
				}
			}
			if (className == null || className.isEmpty()) {
				sendJson(exchange, 400, errorResponse("Provide ?className= or ?name= parameter"));
				return;
			}

			List<Map<String, Object>> stored = ScriptResultStore.get(className);
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("className", className);
			result.put("count", stored.size());
			result.put("results", stored);
			sendJson(exchange, 200, result);

		} else if ("POST".equals(method)) {
			Map<String, Object> body = readJsonBody(exchange);
			String className = getStringField(body, "className");
			if (className == null || className.isEmpty()) {
				sendJson(exchange, 400, errorResponse("className is required"));
				return;
			}

			Map<String, Object> resultData = new LinkedHashMap<>(body);
			resultData.remove("className");
			ScriptResultStore.submit(className, resultData);

			Map<String, Object> response = new LinkedHashMap<>();
			response.put("stored", true);
			response.put("className", className);
			sendJson(exchange, 200, response);

		} else {
			sendJson(exchange, 405, errorResponse("Use GET or POST"));
		}
	}

	private void handlePause(HttpExchange exchange, boolean paused) throws IOException {
		try {
			requirePost(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}
		Microbot.pauseAllScripts.set(paused);
		Map<String, Object> result = new LinkedHashMap<>();
		result.put("success", true);
		result.put("scriptsPaused", paused);
		sendJson(exchange, 200, result);
	}

	private void handleHealth(HttpExchange exchange) throws IOException {
		try {
			requireGet(exchange);
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
			return;
		}

		Map<String, String> params = parseQuery(exchange.getRequestURI());
		String className = params.get("className");
		String name = params.get("name");

		if ((className == null || className.isEmpty()) && (name == null || name.isEmpty())) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("scripts", ScriptHeartbeatRegistry.getAllHealth());
			sendJson(exchange, 200, result);
			return;
		}

		if (className == null || className.isEmpty()) {
			Plugin plugin = findPluginByName(name);
			if (plugin == null) {
				sendJson(exchange, 404, errorResponse("Plugin not found: " + name));
				return;
			}
			className = plugin.getClass().getName();
		}

		List<Map<String, Object>> health = healthFor(className);
		if (health.isEmpty()) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("scriptClassName", className);
			result.put("loopCount", 0);
			result.put("lastHeartbeatAt", null);
			result.put("stalledMs", null);
			result.put("status", "NO_HEARTBEAT");
			Map<String, Object> lastError = AgentLogs.lastErrorFor(className);
			if (lastError != null) {
				result.put("lastError", lastError);
			}
			sendJson(exchange, 200, result);
			return;
		}
		if (health.size() == 1) {
			sendJson(exchange, 200, health.get(0));
			return;
		}

		Map<String, Object> result = new LinkedHashMap<>();
		result.put("className", className);
		result.put("scripts", health);
		sendJson(exchange, 200, result);
	}

	static List<Map<String, Object>> healthFor(String className) {
		Map<String, Object> exact = ScriptHeartbeatRegistry.getHealth(className);
		if (exact != null) {
			return List.of(withLastError(exact));
		}
		int dot = className.lastIndexOf('.');
		if (dot < 0) {
			return List.of();
		}
		String pluginPackage = className.substring(0, dot + 1);
		List<Map<String, Object>> matches = new ArrayList<>();
		for (Map.Entry<String, Map<String, Object>> entry : ScriptHeartbeatRegistry.getAllHealth().entrySet()) {
			if (entry.getKey().startsWith(pluginPackage)) {
				matches.add(withLastError(entry.getValue()));
			}
		}
		return matches;
	}

	static Map<String, Object> withLastError(Map<String, Object> health) {
		Map<String, Object> lastError = AgentLogs.lastErrorFor((String) health.get("scriptClassName"));
		if (lastError != null) {
			health.put("lastError", lastError);
		}
		return health;
	}

	private PluginManager getPluginManager() {
		try {
			return Microbot.getPluginManager();
		} catch (Exception e) {
			return null;
		}
	}

	private Plugin resolvePlugin(Map<String, Object> body) {
		PluginManager pm = getPluginManager();
		if (pm == null) return null;

		String className = getStringField(body, "className");
		if (className != null && !className.isEmpty()) {
			return Microbot.getPlugin(className);
		}

		String name = getStringField(body, "name");
		if (name != null && !name.isEmpty()) {
			return findPluginByName(name);
		}

		return null;
	}

	private Plugin resolvePluginFromParams(Map<String, String> params) {
		PluginManager pm = getPluginManager();
		if (pm == null) return null;

		String className = params.get("className");
		if (className != null && !className.isEmpty()) {
			return Microbot.getPlugin(className);
		}

		String name = params.get("name");
		if (name != null && !name.isEmpty()) {
			return findPluginByName(name);
		}

		return null;
	}

	private Plugin findPluginByName(String name) {
		PluginManager pm = getPluginManager();
		if (pm == null) return null;
		return matchPluginByName(pm.getPlugins(), name);
	}

	static Plugin matchPluginByName(Collection<Plugin> plugins, String name) {
		String wanted = name.trim().toLowerCase();
		List<Plugin> partial = new ArrayList<>();
		for (Plugin plugin : plugins) {
			if (!isMicrobotPlugin(plugin)) continue;
			PluginDescriptor desc = plugin.getClass().getAnnotation(PluginDescriptor.class);
			if (desc == null) continue;
			String plain = plainName(desc.name()).toLowerCase();
			if (plain.equals(wanted)) {
				return plugin;
			}
			if (plain.contains(wanted)) {
				partial.add(plugin);
			}
		}
		if (partial.size() > 1) {
			StringJoiner names = new StringJoiner(", ");
			for (Plugin plugin : partial) {
				names.add(plainName(plugin.getClass().getAnnotation(PluginDescriptor.class).name()));
			}
			throw new BadRequestException("Ambiguous plugin name '" + name + "' matches: " + names
					+ ". Use the exact name or className.");
		}
		return partial.isEmpty() ? null : partial.get(0);
	}

	static String plainName(String descriptorName) {
		return descriptorName.replaceAll("<[^>]*>", "").replaceFirst("^\\[[^\\]]*]\\s*", "").trim();
	}

	private static boolean isMicrobotPlugin(Plugin plugin) {
		return plugin.getClass().getPackageName().toLowerCase().contains("microbot");
	}
}
