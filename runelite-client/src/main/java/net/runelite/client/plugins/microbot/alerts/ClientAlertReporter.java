package net.runelite.client.plugins.microbot.alerts;

import com.google.common.base.Strings;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Singleton;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.ClientSessionManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ClientShutdown;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.events.PluginChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.PluginManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.MicrobotConfig;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.breakhandler.breakhandlerv2.BreakHandlerV2State;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;
import net.runelite.client.plugins.microbot.util.player.LogoutRequested;
import net.runelite.client.util.RunnableExceptionLogger;
import net.runelite.client.util.Text;

@Slf4j
@Singleton
public class ClientAlertReporter
{
	static final long HEARTBEAT_SECONDS = 120;
	static final long FIRST_HEARTBEAT_SECONDS = 30;
	static final long ERROR_STOP_WINDOW_MS = 30_000;
	static final long ERROR_ALERT_INTERVAL_MS = TimeUnit.MINUTES.toMillis(30);
	static final int MAX_RECENT_ERRORS = 50;
	static final long INTENDED_LOGOUT_WINDOW_MS = 30_000;
	static final int BANNED_LOGIN_INDEX = 14;
	static final long BAN_CHECK_DELAY_MS = 2_000;

	private final MicrobotApi microbotApi;
	private final ClientLinkToken clientLinkToken;
	private final ClientSessionManager clientSessionManager;
	private final PluginManager pluginManager;
	private final MicrobotPluginManager microbotPluginManager;
	private final Client client;
	private final ScheduledExecutorService executor;
	private final Deque<ScriptError> recentErrors = new ArrayDeque<>();
	private final Deque<ScriptError> unreportedErrors = new ArrayDeque<>();
	private final Map<String, Long> lastErrorAlertAt = new HashMap<>();
	private final AtomicBoolean errorReportQueued = new AtomicBoolean();
	private ScheduledFuture<?> heartbeatTask;
	private volatile boolean shuttingDown;
	private volatile GameState lastGameState;
	private volatile UUID reportedSessionId;
	private volatile long logoutRequestedAt;
	private volatile boolean awaitingRelog;
	private final Set<String> reportedBans = ConcurrentHashMap.newKeySet();

	@Inject
	ClientAlertReporter(MicrobotApi microbotApi, ClientLinkToken clientLinkToken, ClientSessionManager clientSessionManager,
		PluginManager pluginManager, MicrobotPluginManager microbotPluginManager, Client client, ScheduledExecutorService executor)
	{
		this.microbotApi = microbotApi;
		this.clientLinkToken = clientLinkToken;
		this.clientSessionManager = clientSessionManager;
		this.pluginManager = pluginManager;
		this.microbotPluginManager = microbotPluginManager;
		this.client = client;
		this.executor = executor;
	}

	public void start()
	{
		if (heartbeatTask == null)
		{
			heartbeatTask = executor.scheduleWithFixedDelay(RunnableExceptionLogger.wrap(this::heartbeat),
				FIRST_HEARTBEAT_SECONDS, HEARTBEAT_SECONDS, TimeUnit.SECONDS);
		}
		if (clientLinkToken.configured().isPresent())
		{
			log.info(clientLinkToken.isTelemetryDisabled()
				? "Bot alerts are off because telemetry is disabled"
				: "Bot alerts are on for this client");
		}
	}

	public void stop()
	{
		if (heartbeatTask != null)
		{
			heartbeatTask.cancel(false);
			heartbeatTask = null;
		}
	}

	public boolean isActive()
	{
		return !shuttingDown && clientLinkToken.get().isPresent();
	}

	@Subscribe(priority = 100)
	public void onClientShutdown(ClientShutdown event)
	{
		shuttingDown = true;
		stop();
		if (reportedSessionId != null)
		{
			event.waitFor(executor.submit(this::closeAbandonedSession));
		}
	}

	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		if (MicrobotConfig.configGroup.equals(event.getGroup())
			&& MicrobotConfig.keyAlertLinkToken.equals(event.getKey())
			&& isActive())
		{
			executor.execute(RunnableExceptionLogger.wrap(this::heartbeat));
		}
	}

	@Subscribe
	public void onPluginChanged(PluginChanged event)
	{
		Plugin plugin = event.getPlugin();
		if (!isScript(plugin) || !isActive())
		{
			return;
		}
		AlertEvent.AlertEventBuilder alert = snapshot()
			.plugin(scriptName(plugin))
			.pluginVersion(version(plugin));
		if (event.isLoaded())
		{
			send(alert.type(AlertEvent.SCRIPT_STARTED).build());
			return;
		}
		ScriptError error = recentErrorFor(plugin, System.currentTimeMillis());
		String reason = ScriptStopReason.detect(Thread.currentThread().getStackTrace(), error != null);
		send(alert.type(AlertEvent.SCRIPT_STOPPED)
			.reason(reason)
			.message(ScriptStopReason.ERROR.equals(reason) ? error.getMessage() : null)
			.build());
	}

	@Subscribe
	public void onLogoutRequested(LogoutRequested event)
	{
		logoutRequestedAt = System.currentTimeMillis();
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		GameState previous = lastGameState;
		GameState next = event.getGameState();
		lastGameState = next;
		if (next == GameState.LOGIN_SCREEN)
		{
			executor.schedule(RunnableExceptionLogger.wrap(this::checkBanned), BAN_CHECK_DELAY_MS, TimeUnit.MILLISECONDS);
		}
		else if (next == GameState.LOGGED_IN)
		{
			reportedBans.clear();
		}
		if (isLogout(previous, next))
		{
			boolean intended = isIntendedLogout(logoutRequestedAt, System.currentTimeMillis());
			logoutRequestedAt = 0;
			if (isActive())
			{
				awaitingRelog = true;
				send(snapshot().type(AlertEvent.LOGGED_OUT).loggedIn(false).intended(intended).build());
			}
			return;
		}
		if (next == GameState.LOGGED_IN && awaitingRelog)
		{
			awaitingRelog = false;
			if (isActive())
			{
				send(snapshot().type(AlertEvent.HEARTBEAT).loggedIn(true).build());
			}
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		if (event.getActor() == null || event.getActor() != client.getLocalPlayer() || !isActive())
		{
			return;
		}
		send(snapshot().type(AlertEvent.DIED).build());
	}

	void checkBanned()
	{
		if (!isActive()
			|| client.getGameState() != GameState.LOGIN_SCREEN
			|| client.getLoginIndex() != BANNED_LOGIN_INDEX)
		{
			return;
		}
		String username = Strings.nullToEmpty(client.getUsername());
		if (reportedBans.contains(username))
		{
			return;
		}
		if (deliver(snapshot().type(AlertEvent.BANNED).loggedIn(false).build()))
		{
			reportedBans.add(username);
		}
	}

	public void onScriptError(String loggerName, List<String> frames, String message)
	{
		ScriptError error = new ScriptError(System.currentTimeMillis(), loggerName, List.copyOf(frames), message);
		synchronized (recentErrors)
		{
			append(recentErrors, error);
			append(unreportedErrors, error);
		}
		if (errorReportQueued.compareAndSet(false, true))
		{
			executor.execute(this::reportErrors);
		}
	}

	private void reportErrors()
	{
		errorReportQueued.set(false);
		List<ScriptError> errors;
		synchronized (recentErrors)
		{
			errors = new ArrayList<>(unreportedErrors);
			unreportedErrors.clear();
		}
		List<Plugin> scripts = activeScripts();
		for (ScriptError error : errors)
		{
			Plugin plugin = attribute(error, scripts);
			if (plugin == null)
			{
				continue;
			}
			String key = plugin.getClass().getName();
			Long last = lastErrorAlertAt.get(key);
			if (last != null && error.getAt() - last < ERROR_ALERT_INTERVAL_MS)
			{
				continue;
			}
			lastErrorAlertAt.put(key, error.getAt());
			deliver(snapshot(scripts)
				.type(AlertEvent.SCRIPT_ERROR)
				.plugin(scriptName(plugin))
				.pluginVersion(version(plugin))
				.message(error.getMessage())
				.build());
		}
	}

	private void heartbeat()
	{
		if (!isActive())
		{
			return;
		}
		checkBanned();
		deliver(snapshot().type(AlertEvent.HEARTBEAT).build());
	}

	private void send(AlertEvent event)
	{
		executor.execute(() -> deliver(event));
	}

	private boolean deliver(AlertEvent event)
	{
		if (shuttingDown)
		{
			return false;
		}
		Optional<String> token = clientLinkToken.get();
		if (token.isEmpty())
		{
			return false;
		}
		UUID sessionId = reportingSession();
		if (sessionId == null)
		{
			return false;
		}
		String value = token.get();
		microbotApi.sendAlertEvent(value, event.toJson(sessionId), () -> onRejected(value));
		return true;
	}

	private UUID reportingSession()
	{
		UUID current = clientSessionManager.getMicrobotSessionId();
		UUID previous = reportedSessionId;
		if (current == null || current.equals(previous))
		{
			return previous;
		}
		reportedSessionId = current;
		if (previous != null)
		{
			closeSession(previous);
		}
		return current;
	}

	private void closeAbandonedSession()
	{
		UUID reported = reportedSessionId;
		if (reported != null && !reported.equals(clientSessionManager.getMicrobotSessionId()))
		{
			closeSession(reported);
		}
	}

	private void closeSession(UUID sessionId)
	{
		try
		{
			microbotApi.microbotDelete(sessionId);
		}
		catch (IOException e)
		{
			log.debug("Unable to close the previous alert session", e);
		}
	}

	private void onRejected(String token)
	{
		if (clientLinkToken.reject(token))
		{
			log.warn("The alert link token was revoked or is unknown, so bot alerts are off until a new one is set");
		}
	}

	private AlertEvent.AlertEventBuilder snapshot()
	{
		return snapshot(activeScripts());
	}

	private AlertEvent.AlertEventBuilder snapshot(List<Plugin> scripts)
	{
		return AlertEvent.builder()
			.runningScripts(scripts.stream().map(ClientAlertReporter::scriptName).collect(Collectors.toList()))
			.availableScripts(installedScripts())
			.loggedIn(isLoggedIn())
			.breakActive(isBreakActive());
	}

	private List<Plugin> activeScripts()
	{
		return pluginManager.getPlugins().stream()
			.filter(ClientAlertReporter::isScript)
			.filter(pluginManager::isPluginActive)
			.collect(Collectors.toList());
	}

	private List<AlertEvent.AvailableScript> installedScripts()
	{
		return pluginManager.getPlugins().stream()
			.filter(ClientAlertReporter::isScript)
			.map(plugin -> new AlertEvent.AvailableScript(plugin.getClass().getName(), scriptName(plugin)))
			.collect(Collectors.toList());
	}

	private ScriptError recentErrorFor(Plugin plugin, long now)
	{
		List<Plugin> candidates = List.of(plugin);
		synchronized (recentErrors)
		{
			Iterator<ScriptError> newestFirst = recentErrors.descendingIterator();
			while (newestFirst.hasNext())
			{
				ScriptError error = newestFirst.next();
				if (now - error.getAt() > ERROR_STOP_WINDOW_MS)
				{
					return null;
				}
				if (attribute(error, candidates) != null)
				{
					return error;
				}
			}
		}
		return null;
	}

	private String version(Plugin plugin)
	{
		return microbotPluginManager.getInstalledPluginVersion(plugin.getClass().getSimpleName())
			.orElse(plugin.getClass().getAnnotation(PluginDescriptor.class).version());
	}

	private boolean isLoggedIn()
	{
		GameState gameState = client.getGameState();
		return gameState != null && gameState.getState() >= GameState.LOADING.getState();
	}

	static boolean isBreakActive()
	{
		return BreakHandlerScript.isBreakActive()
			|| BreakHandlerScript.isMicroBreakActive()
			|| BreakHandlerV2State.isBreakActive();
	}

	static boolean isIntendedLogout(long requestedAt, long now)
	{
		return requestedAt > 0 && now >= requestedAt && now - requestedAt <= INTENDED_LOGOUT_WINDOW_MS;
	}

	static boolean isLogout(GameState previous, GameState next)
	{
		return next == GameState.LOGIN_SCREEN
			&& previous != null
			&& previous.getState() >= GameState.LOADING.getState();
	}

	static boolean isScript(Plugin plugin)
	{
		PluginDescriptor descriptor = plugin == null ? null : plugin.getClass().getAnnotation(PluginDescriptor.class);
		return descriptor != null && descriptor.isExternal() && !descriptor.hidden();
	}

	static String scriptName(Plugin plugin)
	{
		String name = Text.removeTags(Strings.nullToEmpty(plugin.getName()))
			.replaceFirst("^\\s*\\[[^\\]]*\\]", "")
			.trim();
		return name.isEmpty() ? plugin.getClass().getSimpleName() : name;
	}

	static Plugin attribute(ScriptError error, Collection<Plugin> scripts)
	{
		for (Plugin plugin : scripts)
		{
			String pkg = plugin.getClass().getPackageName();
			if (pkg.equals(Microbot.class.getPackageName()))
			{
				continue;
			}
			String prefix = pkg + ".";
			if (error.getLoggerName().startsWith(prefix) || error.getFrames().stream().anyMatch(frame -> frame.startsWith(prefix)))
			{
				return plugin;
			}
		}
		return null;
	}

	private static void append(Deque<ScriptError> errors, ScriptError error)
	{
		errors.addLast(error);
		while (errors.size() > MAX_RECENT_ERRORS)
		{
			errors.removeFirst();
		}
	}

	@Value
	static class ScriptError
	{
		long at;
		String loggerName;
		List<String> frames;
		String message;
	}
}
