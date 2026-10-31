package net.runelite.client.plugins.microbot.bans;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Component;
import java.io.File;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.swing.SwingUtilities;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.ClientSessionManager;
import net.runelite.client.RuneLite;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.alerts.ClientAlertReporter;
import net.runelite.client.util.RunnableExceptionLogger;

@Slf4j
@Singleton
public class BanTelemetry
{
	static final int BANNED_LOGIN_INDEX = 14;
	static final int SAMPLE_TICKS = 100;
	static final long MAX_SAMPLE_SECONDS = 120;
	static final long BAN_CHECK_SECONDS = 2;
	static final long RUNTIME_FLUSH_MINUTES = 60;
	static final long MAX_PENDING_SECONDS = 2 * 3600;
	private static final String BREAK_HANDLER_PREFIX = "BreakHandler";

	private final Client client;
	private final ClientThread clientThread;
	private final PluginManager pluginManager;
	private final MicrobotApi microbotApi;
	private final ClientSessionManager clientSessionManager;
	private final ScheduledExecutorService executor;
	private final MicrobotConfig config;
	private final ConfigManager configManager;
	private final boolean disableTelemetry;
	private final BanLedger ledger;
	private final Map<String, Long> pendingRuntime = new HashMap<>();
	private final Set<String> handledThisRun = ConcurrentHashMap.newKeySet();

	private ScheduledFuture<?> banCheckTask;
	private ScheduledFuture<?> flushTask;
	private int ticks;
	private long lastSampleMillis;
	private volatile String lastAccountKey;
	private boolean onBanScreen;
	private volatile boolean prompting;

	@Inject
	BanTelemetry(Client client, ClientThread clientThread, PluginManager pluginManager, MicrobotApi microbotApi,
		ClientSessionManager clientSessionManager, ScheduledExecutorService executor, MicrobotConfig config,
		ConfigManager configManager, Gson gson, @Named("disableTelemetry") boolean disableTelemetry)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.pluginManager = pluginManager;
		this.microbotApi = microbotApi;
		this.clientSessionManager = clientSessionManager;
		this.executor = executor;
		this.config = config;
		this.configManager = configManager;
		this.disableTelemetry = disableTelemetry;
		this.ledger = new BanLedger(new File(new File(RuneLite.RUNELITE_DIR, "microbot"), "ban-ledger").toPath(), gson, Clock.systemUTC());
	}

	public void start()
	{
		if (banCheckTask == null)
		{
			banCheckTask = executor.scheduleWithFixedDelay(RunnableExceptionLogger.wrap(() -> clientThread.invoke(this::checkBanScreen)),
				BAN_CHECK_SECONDS, BAN_CHECK_SECONDS, TimeUnit.SECONDS);
		}
		if (flushTask == null)
		{
			flushTask = executor.scheduleWithFixedDelay(RunnableExceptionLogger.wrap(this::flushRuntime),
				RUNTIME_FLUSH_MINUTES, RUNTIME_FLUSH_MINUTES, TimeUnit.MINUTES);
		}
	}

	public void stop()
	{
		if (banCheckTask != null)
		{
			banCheckTask.cancel(false);
			banCheckTask = null;
		}
		if (flushTask != null)
		{
			flushTask.cancel(false);
			flushTask = null;
		}
	}

	@Subscribe
	public void onClientShutdown(ClientShutdown event)
	{
		event.waitFor(executor.submit(this::flushRuntime));
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			lastSampleMillis = System.currentTimeMillis();
			ticks = 0;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (++ticks < SAMPLE_TICKS)
		{
			return;
		}
		ticks = 0;
		long now = System.currentTimeMillis();
		long elapsed = lastSampleMillis == 0 ? 0 : Math.min((now - lastSampleMillis) / 1000, MAX_SAMPLE_SECONDS);
		lastSampleMillis = now;
		if (elapsed <= 0 || isDisabled())
		{
			return;
		}
		long accountHash = client.getAccountHash();
		if (accountHash == -1)
		{
			return;
		}
		List<String> scripts = activeScripts();
		if (scripts.isEmpty())
		{
			return;
		}
		String accountKey = BanLedger.accountKey(accountHash);
		lastAccountKey = accountKey;
		String identityKey = BanLedger.identityKey(loginIdentity()).orElse(null);
		boolean member = client.getVarpValue(VarPlayerID.ACCOUNT_CREDIT) > 0;
		int totalLevel = client.getTotalLevel();
		boolean breaks = breakHandlerActive();
		addPendingRuntime(scripts, elapsed);
		executor.execute(() -> ledger.record(accountKey, identityKey, elapsed, scripts, member, totalLevel, breaks));
	}

	private void checkBanScreen()
	{
		boolean banned = client.getGameState() == GameState.LOGIN_SCREEN && client.getLoginIndex() == BANNED_LOGIN_INDEX;
		boolean entered = banned && !onBanScreen;
		onBanScreen = banned;
		if (!entered || isDisabled() || prompting)
		{
			return;
		}
		Optional<String> identityKey = BanLedger.identityKey(loginIdentity());
		String fallback = lastAccountKey;
		Component parent = client.getCanvas();
		executor.execute(() -> onBanDetected(identityKey, fallback, parent));
	}

	private void onBanDetected(Optional<String> identityKey, String fallback, Component parent)
	{
		Optional<String> accountKey = identityKey.flatMap(ledger::accountFor);
		if (accountKey.isEmpty() && fallback != null)
		{
			accountKey = Optional.of(fallback);
		}
		if (accountKey.isEmpty())
		{
			log.info("Ban screen seen for an account Microbot has no botting history for; no ban report");
			return;
		}
		String key = accountKey.get();
		if (ledger.recentlyReported(key) || ledger.report(key, BanKind.UNKNOWN.value, null).isEmpty() || !handledThisRun.add(key))
		{
			return;
		}

		BanReportMode mode = config.banReports();
		if (mode == BanReportMode.NEVER)
		{
			return;
		}
		if (mode == BanReportMode.ALWAYS)
		{
			send(key, BanKind.UNKNOWN);
			return;
		}
		prompting = true;
		SwingUtilities.invokeLater(() -> BanReportDialog.ask(parent, answer ->
		{
			prompting = false;
			if (answer.isRemember())
			{
				configManager.setConfiguration(MicrobotConfig.configGroup, MicrobotConfig.keyBanReports,
					answer.isSend() ? BanReportMode.ALWAYS : BanReportMode.NEVER);
			}
			if (answer.isSend())
			{
				executor.execute(() -> send(key, answer.getKind()));
			}
			else if (answer.getKind() == BanKind.NOT_A_BAN || answer.isRemember())
			{
				executor.execute(() -> ledger.markReported(key));
			}
		}));
	}

	private void send(String accountKey, BanKind kind)
	{
		UUID sessionId = clientSessionManager.getMicrobotSessionId();
		Optional<JsonObject> report = ledger.report(accountKey, kind.value, RuneLiteProperties.getMicrobotVersion());
		if (sessionId == null || report.isEmpty() || isDisabled())
		{
			return;
		}
		JsonObject payload = report.get();
		payload.addProperty("sessionId", sessionId.toString());
		if (microbotApi.submitBanReport(payload))
		{
			ledger.markReported(accountKey);
			log.info("Sent an anonymous ban report");
		}
	}

	void flushRuntime()
	{
		UUID sessionId = clientSessionManager.getMicrobotSessionId();
		Map<String, Long> batch;
		synchronized (pendingRuntime)
		{
			if (pendingRuntime.isEmpty())
			{
				return;
			}
			batch = new HashMap<>(pendingRuntime);
			pendingRuntime.clear();
		}
		if (sessionId == null || isDisabled())
		{
			return;
		}
		JsonObject payload = new JsonObject();
		payload.addProperty("sessionId", sessionId.toString());
		JsonArray plugins = new JsonArray();
		batch.forEach((plugin, seconds) ->
		{
			JsonObject entry = new JsonObject();
			entry.addProperty("plugin", plugin);
			entry.addProperty("seconds", seconds);
			plugins.add(entry);
		});
		payload.add("plugins", plugins);
		if (!microbotApi.submitPluginRuntime(payload))
		{
			synchronized (pendingRuntime)
			{
				batch.forEach((plugin, seconds) -> pendingRuntime.merge(plugin, seconds, (a, b) -> Math.min(a + b, MAX_PENDING_SECONDS)));
			}
		}
	}

	private void addPendingRuntime(List<String> scripts, long elapsed)
	{
		synchronized (pendingRuntime)
		{
			for (String script : scripts)
			{
				pendingRuntime.merge(script, elapsed, (a, b) -> Math.min(a + b, MAX_PENDING_SECONDS));
			}
		}
	}

	private List<String> activeScripts()
	{
		return pluginManager.getPlugins().stream()
			.filter(ClientAlertReporter::isScript)
			.filter(pluginManager::isPluginActive)
			.map(plugin -> plugin.getClass().getSimpleName())
			.distinct()
			.collect(Collectors.toList());
	}

	private boolean breakHandlerActive()
	{
		for (Plugin plugin : pluginManager.getPlugins())
		{
			if (plugin.getClass().getSimpleName().startsWith(BREAK_HANDLER_PREFIX) && pluginManager.isPluginActive(plugin))
			{
				return true;
			}
		}
		return false;
	}

	@SuppressWarnings("deprecation")
	private String loginIdentity()
	{
		String launcherName = client.getLauncherDisplayName();
		if (launcherName != null && !launcherName.isBlank())
		{
			return launcherName;
		}
		return client.getUsername();
	}

	private boolean isDisabled()
	{
		return disableTelemetry || Microbot.isTelemetryDisabled();
	}
}
