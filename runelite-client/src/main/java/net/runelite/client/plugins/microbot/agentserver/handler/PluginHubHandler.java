package net.runelite.client.plugins.microbot.agentserver.handler;

import com.google.common.base.Strings;
import com.google.gson.Gson;
import com.sun.net.httpserver.HttpExchange;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginHealth;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManifest;

import java.io.IOException;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantLock;
import java.util.stream.Collectors;

public class PluginHubHandler extends AgentHandler {

	private static final String BASE_PATH = "/plugins";
	private static final int DEFAULT_TIMEOUT_SECONDS = 60;
	private static final int MAX_TIMEOUT_SECONDS = 300;

	private final MicrobotPluginManager pluginManager;
	private final int maxResults;
	private final ReentrantLock taskLock = new ReentrantLock();
	private volatile Future<?> pendingTask;

	public PluginHubHandler(Gson gson, MicrobotPluginManager pluginManager, int maxResults) {
		super(gson);
		this.pluginManager = pluginManager;
		this.maxResults = maxResults;
	}

	@Override
	public String getPath() {
		return BASE_PATH;
	}

	@Override
	protected void handleRequest(HttpExchange exchange) throws IOException {
		String sub = getSubPath(exchange, BASE_PATH);
		try {
			switch (sub) {
				case "":
				case "/":
					requireGet(exchange);
					handleList(exchange);
					break;
				case "/install":
					requirePost(exchange);
					runExclusive(exchange, () -> handleInstall(exchange));
					break;
				case "/remove":
					requirePost(exchange);
					runExclusive(exchange, () -> handleRemove(exchange));
					break;
				default:
					sendJson(exchange, 404, errorResponse("Unknown endpoint: " + BASE_PATH + sub));
			}
		} catch (HttpMethodException e) {
			sendJson(exchange, 405, errorResponse(e.getMessage()));
		}
	}

	private void handleList(HttpExchange exchange) throws IOException {
		Map<String, String> params = parseQuery(exchange.getRequestURI());
		String query = Strings.nullToEmpty(params.get("q")).toLowerCase(Locale.ROOT);
		boolean installedOnly = "true".equalsIgnoreCase(params.get("installed"));

		List<Map<String, Object>> plugins = pluginManager.getManifestMap().values().stream()
				.filter(manifest -> query.isEmpty() || matches(manifest, query))
				.filter(manifest -> !installedOnly || pluginManager.isLoaded(manifest.getInternalName()))
				.sorted(Comparator.comparing(MicrobotPluginManifest::getInternalName))
				.map(this::describe)
				.collect(Collectors.toList());

		sendJson(exchange, 200, paginate(plugins,
				getIntParam(params, "offset", 0),
				getIntParam(params, "limit", maxResults),
				"plugins"));
	}

	private void handleInstall(HttpExchange exchange) throws IOException {
		Map<String, Object> body = readJsonBody(exchange);
		MicrobotPluginManifest manifest = requireManifest(body);
		String version = getStringField(body, "version");
		int timeout = timeoutSeconds(body);
		String internalName = manifest.getInternalName();
		String targetVersion = Strings.isNullOrEmpty(version) ? manifest.getVersion() : version;
		boolean loaded = pluginManager.isLoaded(internalName);

		if (loaded && pluginManager.getInstalledPluginVersion(internalName).filter(targetVersion::equals).isPresent()) {
			sendJson(exchange, 200, describe(manifest));
			return;
		}

		if (!Strings.isNullOrEmpty(version) && !manifest.getAvailableVersions().isEmpty()
				&& !manifest.getAvailableVersions().contains(version)) {
			throw new BadRequestException(internalName + " has no version " + version + "; available: " + manifest.getAvailableVersions());
		}

		MicrobotPluginHealth health = pluginManager.getPluginHealth(manifest, version);
		if (health.isBlocking()) {
			sendJson(exchange, 409, errorResponse("Cannot install " + internalName + " " + health.getVersion() + ": " + health.getLabel()));
			return;
		}

		if (loaded && !requireLoadedFromHub(exchange, internalName)) {
			return;
		}
		Future<?> task = loaded
				? pluginManager.updatePlugin(manifest, version)
				: pluginManager.installPlugin(manifest, version);
		if (!awaitTask(exchange, task, timeout)) {
			return;
		}

		if (!pluginManager.isLoaded(internalName)) {
			sendJson(exchange, 502, errorResponse("Install of " + internalName + " did not load the plugin; check the client log"));
			return;
		}
		sendJson(exchange, 200, describe(manifest));
	}

	private void handleRemove(HttpExchange exchange) throws IOException {
		Map<String, Object> body = readJsonBody(exchange);
		MicrobotPluginManifest manifest = requireManifest(body);
		int timeout = timeoutSeconds(body);
		if (!pluginManager.isLoaded(manifest.getInternalName())) {
			sendJson(exchange, 404, errorResponse(manifest.getInternalName() + " is not installed"));
			return;
		}
		if (!requireLoadedFromHub(exchange, manifest.getInternalName())) {
			return;
		}

		if (!awaitTask(exchange, pluginManager.removePlugin(manifest), timeout)) {
			return;
		}
		if (pluginManager.isLoaded(manifest.getInternalName())) {
			sendJson(exchange, 409, errorResponse(manifest.getInternalName() + " is still loaded; check the client log"));
			return;
		}
		sendJson(exchange, 200, describe(manifest));
	}

	private boolean requireLoadedFromHub(HttpExchange exchange, String internalName) throws IOException {
		if (pluginManager.isLoadedFromHub(internalName)) {
			return true;
		}
		sendJson(exchange, 409, errorResponse(internalName
				+ " is loaded from outside the Plugin Hub (a dev build or sideloaded jar); restart the client without it first"));
		return false;
	}

	private void runExclusive(HttpExchange exchange, PluginRequest request) throws IOException {
		if (!taskLock.tryLock()) {
			sendBusy(exchange);
			return;
		}
		try {
			Future<?> pending = pendingTask;
			if (pending != null && !pending.isDone()) {
				sendBusy(exchange);
				return;
			}
			request.handle();
		} finally {
			taskLock.unlock();
		}
	}

	private void sendBusy(HttpExchange exchange) throws IOException {
		sendJson(exchange, 409, errorResponse("Another plugin install or removal is still running; poll GET /plugins?installed=true"));
	}

	private interface PluginRequest {
		void handle() throws IOException;
	}

	private MicrobotPluginManifest requireManifest(Map<String, Object> body) {
		String name = getStringField(body, "name");
		if (Strings.isNullOrEmpty(name)) {
			throw new BadRequestException("Field 'name' is required (internal name or display name)");
		}
		return pluginManager.findManifest(name)
				.orElseThrow(() -> new BadRequestException("No Plugin Hub entry named '" + name + "'. Search with GET /plugins?q="));
	}

	/**
	 * Sends a 202 or 500 itself when the task did not finish, and returns false in that case.
	 */
	private boolean awaitTask(HttpExchange exchange, Future<?> task, int timeout) throws IOException {
		pendingTask = task;
		try {
			task.get(timeout, TimeUnit.SECONDS);
			return true;
		} catch (TimeoutException e) {
			Map<String, Object> result = new LinkedHashMap<>();
			result.put("status", "pending");
			result.put("message", "Still running after " + timeout + "s; poll GET /plugins?installed=true");
			sendJson(exchange, 202, result);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			sendJson(exchange, 500, errorResponse("Interrupted while waiting for the plugin task"));
		} catch (ExecutionException e) {
			sendJson(exchange, 500, errorResponse("Plugin task failed: " + e.getCause()));
		}
		return false;
	}

	private static int timeoutSeconds(Map<String, Object> body) {
		Integer requested = getIntField(body, "timeout");
		return Math.max(1, Math.min(requested == null ? DEFAULT_TIMEOUT_SECONDS : requested, MAX_TIMEOUT_SECONDS));
	}

	private static boolean matches(MicrobotPluginManifest manifest, String query) {
		return Strings.nullToEmpty(manifest.getInternalName()).toLowerCase(Locale.ROOT).contains(query)
				|| Strings.nullToEmpty(manifest.getDisplayName()).toLowerCase(Locale.ROOT).contains(query)
				|| Strings.nullToEmpty(manifest.getDescription()).toLowerCase(Locale.ROOT).contains(query);
	}

	private Map<String, Object> describe(MicrobotPluginManifest manifest) {
		MicrobotPluginHealth health = pluginManager.getPluginHealth(manifest, null);
		Map<String, Object> entry = new LinkedHashMap<>();
		entry.put("internalName", manifest.getInternalName());
		entry.put("name", manifest.getDisplayName());
		entry.put("description", manifest.getDescription());
		entry.put("latestVersion", manifest.getVersion());
		entry.put("installedVersion", pluginManager.getInstalledPluginVersion(manifest.getInternalName()).orElse(null));
		entry.put("loaded", pluginManager.isLoaded(manifest.getInternalName()));
		entry.put("health", health.getLabel());
		entry.put("installable", !health.isBlocking());
		return entry;
	}
}
