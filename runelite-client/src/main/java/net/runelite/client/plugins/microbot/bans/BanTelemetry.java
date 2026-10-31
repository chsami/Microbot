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
import java.util.Objects;
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
import net.runelite.client.Notifier;
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
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.rs.auth.JagexAuthService;
import net.runelite.client.util.RunnableExceptionLogger;

@Slf4j
@Singleton
public class BanTelemetry
{
	static final int BANNED_LOGIN_INDEX = 14;
	static final int SAMPLE_TICKS = 100;
	static final long MAX_SAMPLE_SECONDS = 120;
	static final long RUNTIME_FLUSH_MINUTES = 60;
	static final long MAX_PENDING_SECONDS = 2 * 3600;
	static final String CUSTOM_PLUGIN = "custom";
	private static final String BREAK_HANDLER_PREFIX = "BreakHandler";

	private final Client client;
	private final ClientThread clientThread;
	private final PluginManager pluginManager;
	private final MicrobotPluginManager microbotPluginManager;
	private final MicrobotApi microbotApi;
	private final ClientSessionManager clientSessionManager;
	private final ScheduledExecutorService executor;
	private final MicrobotConfig config;
	private final ConfigManager configManager;
	private final Notifier notifier;
	private final boolean disableTelemetry;
	private final BanLedger ledger;
	private final Map<String, Long> pendingRuntime = new HashMap<>();
	private final Set<String> handledThisRun = ConcurrentHashMap.newKeySet();

	private ScheduledFuture<?> flushTask;
	private volatile boolean watching;
	private int ticks;
	private long lastSampleMillis;
	private GameState lastGameState;
	private long cachedHash = -1;
	private String cachedKey;
	private String cachedIdentity;
	private String cachedIdentityKey;
	private String lastLoggedInKey;
	private String identityKeyAtLastLogin;
	private int accountSwitchesAtLastLogin;
	private String playerNameAtLastLogin;
	private boolean onBanScreen;

	@Inject
	BanTelemetry(Client client, ClientThread clientThread, PluginManager pluginManager, MicrobotPluginManager microbotPluginManager,
		MicrobotApi microbotApi, ClientSessionManager clientSessionManager, ScheduledExecutorService executor, MicrobotConfig config,
		ConfigManager configManager, Notifier notifier, Gson gson, @Named("disableTelemetry") boolean disableTelemetry)
	{
		this.client = client;
		this.clientThread = clientThread;
		this.pluginManager = pluginManager;
		this.microbotPluginManager = microbotPluginManager;
		this.microbotApi = microbotApi;
		this.clientSessionManager = clientSessionManager;
		this.executor = executor;
		this.config = config;
		this.configManager = configManager;
		this.notifier = notifier;
		this.disableTelemetry = disableTelemetry;
		this.ledger = new BanLedger(new File(new File(RuneLite.RUNELITE_DIR, "microbot"), "ban-ledger").toPath(), gson, Clock.systemUTC());
	}

	public void start()
	{
		if (!watching)
		{
			watching = true;
			clientThread.invokeLater(this::watchLoginScreen);
		}
		if (flushTask == null)
		{
			flushTask = executor.scheduleWithFixedDelay(RunnableExceptionLogger.wrap(this::flushRuntime),
				RUNTIME_FLUSH_MINUTES, RUNTIME_FLUSH_MINUTES, TimeUnit.MINUTES);
		}
	}

	public void stop()
	{
		watching = false;
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
		GameState state = event.getGameState();
		if (state == GameState.LOGGED_IN && lastGameState != GameState.LOADING)
		{
			lastSampleMillis = System.currentTimeMillis();
			ticks = 0;
		}
		lastGameState = state;
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		String accountKey = currentAccountKey();
		String identityKey = currentIdentityKey();
		if (accountKey != null)
		{
			lastLoggedInKey = accountKey;
			identityKeyAtLastLogin = identityKey;
			accountSwitchesAtLastLogin = JagexAuthService.accountSwitches();
			if (client.getLocalPlayer() != null && client.getLocalPlayer().getName() != null)
			{
				playerNameAtLastLogin = client.getLocalPlayer().getName();
			}
		}
		if (++ticks < SAMPLE_TICKS)
		{
			return;
		}
		ticks = 0;
		long now = System.currentTimeMillis();
		long elapsed = lastSampleMillis == 0 ? 0 : Math.min((now - lastSampleMillis) / 1000, MAX_SAMPLE_SECONDS);
		lastSampleMillis = now;
		if (elapsed <= 0 || accountKey == null || isDisabled())
		{
			return;
		}
		List<String> scripts = activeScripts();
		if (scripts.isEmpty())
		{
			return;
		}
		String trustedIdentity = BanAttribution.identityTrusted(JagexAuthService.accountSwitches()) ? identityKey : null;
		boolean member = client.getVarpValue(VarPlayerID.ACCOUNT_CREDIT) > 0;
		int totalLevel = client.getTotalLevel();
		boolean breaks = breakHandlerActive();
		if (config.banReports() != BanReportMode.NEVER)
		{
			addPendingRuntime(BanLedger.split(scripts, elapsed));
		}
		executor.execute(() -> ledger.record(accountKey, trustedIdentity, elapsed, scripts, member, totalLevel, breaks));
	}

	private boolean watchLoginScreen()
	{
		if (!watching)
		{
			return true;
		}
		boolean banned = client.getGameState() == GameState.LOGIN_SCREEN && client.getLoginIndex() == BANNED_LOGIN_INDEX;
		boolean entered = banned && !onBanScreen;
		onBanScreen = banned;
		if (entered && !isDisabled())
		{
			BanAttribution.Context context = BanAttribution.Context.builder()
				.identityKey(currentIdentityKey())
				.identityKeyAtLastLogin(identityKeyAtLastLogin)
				.lastLoggedInKey(lastLoggedInKey)
				.accountSwitches(JagexAuthService.accountSwitches())
				.accountSwitchesAtLastLogin(accountSwitchesAtLastLogin)
				.build();
			Component parent = client.getCanvas();
			String loginName = loginIdentity();
			String playerName = playerNameAtLastLogin;
			executor.execute(RunnableExceptionLogger.wrap(() -> onBanDetected(context, loginName, playerName, parent)));
		}
		return false;
	}

	private void onBanDetected(BanAttribution.Context context, String loginName, String playerName, Component parent)
	{
		Optional<String> accountKey = BanAttribution.resolve(context, ledger::accountFor);
		String shownName = accountKey.isPresent() && accountKey.get().equals(context.getLastLoggedInKey()) && playerName != null
			? playerName
			: loginName;
		if (accountKey.isEmpty())
		{
			log.info("Ban screen seen, but Microbot can't tell which botted account it is; no ban report");
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
		SwingUtilities.invokeLater(() ->
		{
			try
			{
				BanReportDialog.ask(parent, shownName, answer -> onAnswer(key, answer));
			}
			catch (RuntimeException e)
			{
				log.warn("Could not show the ban report dialog", e);
			}
		});
	}

	private void onAnswer(String key, BanReportDialog.Answer answer)
	{
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
		microbotApi.submitBanReport(payload).thenAccept(sent ->
		{
			if (sent)
			{
				executor.execute(() -> ledger.markReported(accountKey));
				log.info("Sent an anonymous ban report");
				clientThread.invokeLater(() -> notifier.notify("Ban report sent. Thank you for helping other Microbot players."));
			}
		});
	}

	void flushRuntime()
	{
		UUID sessionId = clientSessionManager.getMicrobotSessionId();
		if (sessionId == null || isDisabled())
		{
			return;
		}
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
		microbotApi.submitPluginRuntime(payload).thenAccept(sent ->
		{
			if (!sent)
			{
				addPendingRuntime(batch);
			}
		});
	}

	private void addPendingRuntime(Map<String, Long> seconds)
	{
		synchronized (pendingRuntime)
		{
			seconds.forEach((plugin, value) -> pendingRuntime.merge(plugin, value, (a, b) -> Math.min(a + b, MAX_PENDING_SECONDS)));
		}
	}

	private String currentAccountKey()
	{
		long hash = client.getAccountHash();
		if (hash == -1)
		{
			return null;
		}
		if (hash != cachedHash || cachedKey == null)
		{
			cachedHash = hash;
			cachedKey = BanLedger.accountKey(hash);
		}
		return cachedKey;
	}

	private String currentIdentityKey()
	{
		String identity = loginIdentity();
		if (!Objects.equals(identity, cachedIdentity))
		{
			cachedIdentity = identity;
			cachedIdentityKey = BanLedger.identityKey(identity).orElse(null);
		}
		return cachedIdentityKey;
	}

	private List<String> activeScripts()
	{
		return pluginManager.getPlugins().stream()
			.filter(ClientAlertReporter::isScript)
			.filter(pluginManager::isPluginActive)
			.map(plugin -> plugin.getClass().getSimpleName())
			.map(name -> microbotPluginManager.getInstalledPluginVersion(name).isPresent() ? name : CUSTOM_PLUGIN)
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
		String username = client.getUsername();
		if (username != null && !username.isBlank())
		{
			return username;
		}
		return client.getLauncherDisplayName();
	}

	private boolean isDisabled()
	{
		return disableTelemetry || Microbot.isTelemetryDisabled();
	}
}
