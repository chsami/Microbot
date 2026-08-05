package net.runelite.client.plugins.microbot.agentserver;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Provides;
import com.sun.net.httpserver.HttpServer;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.agentserver.handler.*;
import net.runelite.client.ui.DrawManager;

import javax.inject.Inject;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.PosixFilePermissions;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@PluginDescriptor(
		name = PluginDescriptor.Mocrosoft + "Agent Server",
		description = "HTTP server for AI agent communication - exposes widget inspection, game interaction, and state endpoints",
		tags = {"agent", "ai", "server", "automation"},
		enabledByDefault = false
)
@Slf4j
public class AgentServerPlugin extends Plugin {

	@Inject
	private AgentServerConfig config;

	@Inject
	private ConfigManager configManager;

	@Inject
	private Client client;

	@Inject
	private DrawManager drawManager;

	private HttpServer server;
	private net.runelite.client.plugins.microbot.agentserver.uds.UdsHttpServer udsServer;
	private ExecutorService executor;
	private Thread shutdownHook;
	private final Gson gson = new GsonBuilder().setPrettyPrinting().create();

	private java.util.concurrent.ScheduledExecutorService stealthScheduler;
	private volatile boolean stealthActive = false;
	private static final long STEALTH_IDLE_GRACE_MS = 20_000;

	private static Path defaultUdsSocketPath() {
		return Paths.get(System.getProperty("user.home"), ".runelite", ".agent.sock");
	}

	@Provides
	AgentServerConfig provideConfig(ConfigManager configManager) {
		return configManager.getConfig(AgentServerConfig.class);
	}

	@Override
	protected void startUp() throws Exception {
		if (config.bindOnlyWhileScriptsActive()) {
			log.info("Agent server in stealth-bind mode; socket will open when a script begins running.");
			startStealthScheduler();
			return;
		}
		actuallyStart();
	}

	private synchronized void actuallyStart() throws Exception {
		int maxResults = config.maxResults();

		stopServer();

		String token = ensureAuthToken();
		AgentHandler.setTokenSupplier(() -> configManager.getConfiguration(AgentServerConfig.GROUP, AgentServerConfig.KEY_TOKEN));

		executor = Executors.newFixedThreadPool(4, new ThreadFactory() {
			private final AtomicInteger count = new AtomicInteger(1);

			@Override
			public Thread newThread(Runnable r) {
				Thread t = new Thread(r, "AgentServer-" + count.getAndIncrement());
				t.setDaemon(true);
				return t;
			}
		});

		List<AgentHandler> handlers = buildHandlers(maxResults);

		AgentServerConfig.BindMode mode = config.bindMode();
		boolean started;
		if (mode == AgentServerConfig.BindMode.UDS) {
			started = startUds(handlers);
			if (!started) {
				log.warn("Falling back to TCP after UDS startup failure");
				started = startTcp(handlers);
			}
		} else {
			started = startTcp(handlers);
		}
		if (!started) return;

		shutdownHook = new Thread(() -> {
			stopServer();
			deleteTokenFile();
		}, "AgentServer-Shutdown");
		Runtime.getRuntime().addShutdownHook(shutdownHook);
		Path tokenFile = writeTokenFile(token);
		log.info("Agent server started ({} endpoints; auth enabled, token {})",
				handlers.size(), tokenFile != null ? "at " + tokenFile : "file unavailable");
	}

	private List<AgentHandler> buildHandlers(int maxResults) {
		return Arrays.asList(
				new WidgetListHandler(gson, maxResults),
				new WidgetSearchHandler(gson, maxResults),
				new WidgetDescribeHandler(gson),
				new WidgetClickHandler(gson),
				new StateHandler(gson, client),
				new InventoryHandler(gson),
				new NpcHandler(gson, maxResults),
				new ObjectHandler(gson, maxResults),
				new WalkHandler(gson),
				new WalkerShadowHandler(gson),
				new BankHandler(gson),
				new DialogueHandler(gson),
				new GroundItemHandler(gson, maxResults),
				new SkillsHandler(gson, client),
				new ScriptHandler(gson),
				new LoginHandler(gson, client),
				new LogoutHandler(gson, client),
				new ScreenshotHandler(gson, client, drawManager),
				new VarbitHandler(gson),
				new VarpHandler(gson),
				new WidgetInvokeHandler(gson),
				new SettingsHandler(gson),
				new KeyboardHandler(gson),
				new QuestHelperHandler(gson),
				new StateMachineDebugHandler(gson),
				new ProfileHandler(gson),
				new DynamicScriptDeployHandler(gson)
		);
	}

	private boolean startTcp(List<AgentHandler> handlers) {
		int port = ensurePort();
		try {
			server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
		} catch (java.net.BindException e) {
			log.warn("Agent server port {} is already in use (likely another Microbot client). Skipping agent server startup for this instance.", port);
			stopServer();
			return false;
		} catch (IOException e) {
			log.warn("TCP bind failed on {}: {}", port, e.getMessage());
			stopServer();
			return false;
		}

		server.setExecutor(executor);
		for (AgentHandler handler : handlers) {
			server.createContext(handler.getPath(), handler);
		}
		server.start();
		log.info("Agent server bound to 127.0.0.1:{}", port);
		return true;
	}

	private boolean startUds(List<AgentHandler> handlers) {
		Path socketPath = defaultUdsSocketPath();
		try {
			net.runelite.client.plugins.microbot.agentserver.uds.UdsHttpServer.validatePathLength(socketPath);
		} catch (IOException e) {
			log.warn("UDS path validation failed: {}", e.getMessage());
			return false;
		}
		try {
			udsServer = new net.runelite.client.plugins.microbot.agentserver.uds.UdsHttpServer(socketPath, executor);
			for (AgentHandler handler : handlers) {
				udsServer.createContext(handler.getPath(), handler);
			}
			udsServer.start();
			log.info("Agent server bound to UDS {}", socketPath);
			return true;
		} catch (Exception e) {
			log.warn("UDS bind failed ({}): {}", socketPath, e.getMessage());
			udsServer = null;
			return false;
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event) {
		if (!AgentServerConfig.GROUP.equals(event.getGroup()) || !AgentServerConfig.KEY_TOKEN.equals(event.getKey())) {
			return;
		}
		String value = event.getNewValue();
		if (value == null || value.isEmpty()) {
			log.warn("Agent server auth token was cleared; regenerating to keep auth enforced");
			writeTokenFile(ensureAuthToken());
			return;
		}
		writeTokenFile(value);
	}

	private int ensurePort() {
		String stored = configManager.getConfiguration(AgentServerConfig.GROUP, AgentServerConfig.KEY_PORT);
		if (stored != null && !stored.isEmpty()) {
			try {
				return Integer.parseInt(stored);
			} catch (NumberFormatException ignored) {
				log.warn("Invalid agent server port '{}'; falling back to configured default {}", stored, config.port());
			}
		}
		return config.port();
	}

	private String ensureAuthToken() {
		String existing = configManager.getConfiguration(AgentServerConfig.GROUP, AgentServerConfig.KEY_TOKEN);
		if (existing != null && !existing.isEmpty()) {
			return existing;
		}
		String generated = UUID.randomUUID().toString().replace("-", "");
		configManager.setConfiguration(AgentServerConfig.GROUP, AgentServerConfig.KEY_TOKEN, generated);
		return generated;
	}

	private Path writeTokenFile(String token) {
		try {
			cleanupLegacyTokenLocations();
			Path dir = Paths.get(System.getProperty("user.home"), ".runelite");
			Files.createDirectories(dir);
			Path file = dir.resolve(".agent-token");

			boolean posix = java.nio.file.FileSystems.getDefault().supportedFileAttributeViews().contains("posix");

			java.util.Set<java.nio.file.OpenOption> opts = new java.util.HashSet<>();
			opts.add(java.nio.file.StandardOpenOption.CREATE);
			opts.add(java.nio.file.StandardOpenOption.WRITE);
			opts.add(java.nio.file.StandardOpenOption.TRUNCATE_EXISTING);

			java.nio.file.attribute.FileAttribute<?>[] attrs = posix
					? new java.nio.file.attribute.FileAttribute<?>[]{
							PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rw-------"))}
					: new java.nio.file.attribute.FileAttribute<?>[0];

			if (posix && Files.exists(file)) {
				try {
					Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
				} catch (IOException ignored) {
				}
			}

			try (java.nio.channels.SeekableByteChannel ch = Files.newByteChannel(file, opts, attrs)) {
				ch.write(java.nio.ByteBuffer.wrap(token.getBytes(StandardCharsets.UTF_8)));
			}

			if (posix) {
				try {
					Files.setPosixFilePermissions(file, PosixFilePermissions.fromString("rw-------"));
				} catch (IOException ignored) {
				}
			} else {
				try {
					file.toFile().setReadable(false, false);
					file.toFile().setWritable(false, false);
					file.toFile().setReadable(true, true);
					file.toFile().setWritable(true, true);
				} catch (SecurityException ignored) {
				}
			}
			return file;
		} catch (IOException e) {
			log.warn("Could not write agent token file: {}", e.getMessage());
			return null;
		}
	}

	@Override
	protected void shutDown() throws Exception {
		stopStealthScheduler();
		stopServer();
		deleteTokenFile();
	}

	private synchronized void startStealthScheduler() {
		stopStealthScheduler();
		stealthScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
			Thread t = new Thread(r, "AgentServer-Stealth");
			t.setDaemon(true);
			return t;
		});
		stealthScheduler.scheduleWithFixedDelay(this::evaluateStealthState, 2, 2, TimeUnit.SECONDS);
	}

	private synchronized void stopStealthScheduler() {
		if (stealthScheduler != null) {
			stealthScheduler.shutdownNow();
			stealthScheduler = null;
		}
	}

	private synchronized void evaluateStealthState() {
		try {
			boolean anyScriptAlive = hasActiveScript();
			if (anyScriptAlive && !stealthActive) {
				log.info("Stealth bind: activating agent server (script detected)");
				actuallyStart();
				stealthActive = true;
			} else if (!anyScriptAlive && stealthActive) {
				log.info("Stealth bind: tearing down agent server (no active scripts for {}ms)", STEALTH_IDLE_GRACE_MS);
				stopServer();
				deleteTokenFile();
				stealthActive = false;
			}
		} catch (Exception e) {
			log.warn("Stealth evaluation failed: {}", e.getMessage());
		}
	}

	private boolean hasActiveScript() {
		java.util.Map<String, java.util.Map<String, Object>> all = ScriptHeartbeatRegistry.getAllHealth();
		if (all.isEmpty()) return false;
		long now = System.currentTimeMillis();
		for (java.util.Map<String, Object> entry : all.values()) {
			Object stalledMs = entry.get("stalledMs");
			if (stalledMs instanceof Number) {
				if (((Number) stalledMs).longValue() < STEALTH_IDLE_GRACE_MS) return true;
			}
		}
		return false;
	}

	private void deleteTokenFile() {
		try {
			Path file = Paths.get(System.getProperty("user.home"), ".runelite", ".agent-token");
			Files.deleteIfExists(file);
		} catch (IOException e) {
			log.debug("Could not delete agent token file: {}", e.getMessage());
		}
		cleanupLegacyTokenLocations();
	}

	private void cleanupLegacyTokenLocations() {
		Path home = Paths.get(System.getProperty("user.home"));

		Path oldDotMicrobot = home.resolve(".microbot");
		try {
			Files.deleteIfExists(oldDotMicrobot.resolve("agent-token"));
		} catch (IOException ignored) {
		}
		pruneEmptyDir(oldDotMicrobot);

		Path runeliteMicrobot = home.resolve(".runelite").resolve("microbot");
		try {
			Files.deleteIfExists(runeliteMicrobot.resolve("agent-token"));
		} catch (IOException ignored) {
		}
		pruneEmptyDir(runeliteMicrobot);
	}

	private void pruneEmptyDir(Path dir) {
		try (java.util.stream.Stream<Path> entries = Files.list(dir)) {
			if (entries.findAny().isEmpty()) {
				Files.deleteIfExists(dir);
			}
		} catch (java.nio.file.NoSuchFileException ignored) {
		} catch (IOException e) {
			log.debug("Could not remove empty dir {}: {}", dir, e.getMessage());
		}
	}

	private synchronized void stopServer() {
		AgentHandler.setTokenSupplier(null);
		if (server != null) {
			server.stop(0);
			server = null;
			log.info("Agent server stopped (TCP)");
		}
		if (udsServer != null) {
			udsServer.stop();
			udsServer = null;
			log.info("Agent server stopped (UDS)");
		}
		if (executor != null) {
			executor.shutdownNow();
			try {
				executor.awaitTermination(2, TimeUnit.SECONDS);
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			}
			executor = null;
		}
		if (shutdownHook != null) {
			try {
				Runtime.getRuntime().removeShutdownHook(shutdownHook);
			} catch (IllegalStateException ignored) {
			}
			shutdownHook = null;
		}
	}

}
