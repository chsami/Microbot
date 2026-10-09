package net.runelite.client.plugins.microbot.diagnostics;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.classic.spi.StackTraceElementProxy;
import ch.qos.logback.core.UnsynchronizedAppenderBase;
import com.google.gson.JsonElement;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.ClientSessionManager;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.MicrobotApi;
import net.runelite.client.plugins.microbot.externalplugins.MicrobotPluginManager;

@Slf4j
@Singleton
public class ScriptErrorReporter extends UnsynchronizedAppenderBase<ILoggingEvent>
{
	static final int MAX_FINGERPRINTS = 100;
	private static final int MAX_FRAMES = 20;
	private static final int FINGERPRINT_FRAMES = 3;
	private static final long FLUSH_MINUTES = 5;

	private final MicrobotApi microbotApi;
	private final MicrobotPluginManager microbotPluginManager;
	private final ScheduledExecutorService executor;
	private final boolean disableTelemetry;
	private final ClientSessionManager clientSessionManager;
	private final Map<String, JsonObject> pending = new LinkedHashMap<>();
	private ScheduledFuture<?> flushTask;
	private volatile String playerName;

	@Inject
	ScriptErrorReporter(MicrobotApi microbotApi, MicrobotPluginManager microbotPluginManager, ScheduledExecutorService executor,
		ClientSessionManager clientSessionManager, @Named("disableTelemetry") boolean disableTelemetry)
	{
		this.microbotApi = microbotApi;
		this.microbotPluginManager = microbotPluginManager;
		this.executor = executor;
		this.disableTelemetry = disableTelemetry;
		this.clientSessionManager = clientSessionManager;
		setName("SCRIPT_ERROR_REPORTER");
	}

	@Override
	public void start()
	{
		super.start();
		flushTask = executor.scheduleWithFixedDelay(this::flush, FLUSH_MINUTES, FLUSH_MINUTES, TimeUnit.MINUTES);
	}

	@Override
	public void stop()
	{
		if (flushTask != null)
		{
			flushTask.cancel(false);
		}
		super.stop();
		flush();
	}

	@Override
	protected void append(ILoggingEvent event)
	{
		if (!event.getLevel().isGreaterOrEqual(Level.ERROR)
			|| event.getLoggerName().startsWith(ScriptErrorReporter.class.getPackageName())
			|| event.getLoggerName().equals(MicrobotApi.class.getName())
			|| isDisabled())
		{
			return;
		}

		IThrowableProxy root = rootCause(event.getThrowableProxy());
		List<String> frames = frames(root);
		String fingerprint = root == null
			? event.getLoggerName() + "|" + scrub(event.getMessage()).replaceAll("\\d+", "#")
			: root.getClassName() + "|" + frames.stream()
				.filter(frame -> !frame.startsWith("java.") && !frame.startsWith("javax.") && !frame.startsWith("jdk.") && !frame.startsWith("sun."))
				.limit(FINGERPRINT_FRAMES)
				.collect(Collectors.joining("|"));

		synchronized (pending)
		{
			JsonObject error = pending.get(fingerprint);
			if (error != null)
			{
				error.addProperty("count", error.get("count").getAsInt() + 1);
				return;
			}
			if (pending.size() >= MAX_FINGERPRINTS)
			{
				return;
			}
			pending.put(fingerprint, describe(event, root, frames, fingerprint));
		}
	}

	public void flush()
	{
		try
		{
			send();
		}
		catch (RuntimeException e)
		{
			log.debug("Error telemetry flush failed", e);
		}
	}

	private void send()
	{
		UUID sessionId = clientSessionManager.getMicrobotSessionId();
		if (sessionId == null)
		{
			return;
		}
		JsonArray errors = new JsonArray();
		synchronized (pending)
		{
			pending.values().forEach(errors::add);
			pending.clear();
		}
		if (errors.size() == 0 || isDisabled())
		{
			return;
		}
		errors.forEach(error -> attribute(error.getAsJsonObject()));

		JsonObject payload = new JsonObject();
		payload.addProperty("sessionId", sessionId.toString());
		payload.addProperty("microbotVersion", RuneLiteProperties.getMicrobotVersion());
		payload.addProperty("microbotCommit", RuneLiteProperties.getMicrobotCommit());
		payload.addProperty("buildChannel", RuneLiteProperties.getMicrobotBuildChannel());
		payload.addProperty("javaVersion", System.getProperty("java.version"));
		payload.addProperty("osName", System.getProperty("os.name"));
		payload.addProperty("osArch", System.getProperty("os.arch"));
		payload.add("errors", errors);
		try
		{
			microbotApi.submitErrors(payload, () -> requeue(errors));
		}
		catch (RuntimeException e)
		{
			requeue(errors);
			throw e;
		}
	}

	public void rememberPlayerName(String name)
	{
		if (name != null && !name.isEmpty())
		{
			playerName = name;
		}
	}

	private void requeue(JsonArray errors)
	{
		synchronized (pending)
		{
			for (JsonElement element : errors)
			{
				JsonObject error = element.getAsJsonObject();
				String fingerprint = error.get("fingerprint").getAsString();
				JsonObject existing = pending.get(fingerprint);
				if (existing != null)
				{
					existing.addProperty("count", existing.get("count").getAsInt() + error.get("count").getAsInt());
				}
				else if (pending.size() < MAX_FINGERPRINTS)
				{
					pending.put(fingerprint, error);
				}
			}
		}
	}

	int pendingCount()
	{
		synchronized (pending)
		{
			return pending.size();
		}
	}

	private JsonObject describe(ILoggingEvent event, IThrowableProxy root, List<String> frames, String fingerprint)
	{
		JsonObject error = new JsonObject();
		error.addProperty("fingerprint", fingerprint);
		error.addProperty("count", 1);
		error.addProperty("firstSeen", event.getTimeStamp());
		error.addProperty("logger", event.getLoggerName());
		error.addProperty("thread", scrub(event.getThreadName()));
		error.addProperty("message", scrub(event.getMessage()));
		if (root != null)
		{
			error.addProperty("exception", root.getClassName());
			error.addProperty("exceptionMessage", scrub(root.getMessage()));
		}
		JsonArray stack = new JsonArray();
		frames.forEach(stack::add);
		error.add("stack", stack);
		return error;
	}

	private boolean isDisabled()
	{
		return disableTelemetry || Microbot.isTelemetryDisabled();
	}

	private void attribute(JsonObject error)
	{
		List<Plugin> plugins = microbotPluginManager.getInstalledPlugins();
		for (JsonElement frame : error.getAsJsonArray("stack"))
		{
			for (Plugin plugin : plugins)
			{
				String pkg = plugin.getClass().getPackageName();
				if (!pkg.equals(Microbot.class.getPackageName()) && frame.getAsString().startsWith(pkg + "."))
				{
					String internalName = plugin.getClass().getSimpleName();
					error.addProperty("plugin", internalName);
					error.addProperty("pluginVersion", microbotPluginManager.getInstalledPluginVersion(internalName)
						.orElse(plugin.getClass().getAnnotation(PluginDescriptor.class).version()));
					return;
				}
			}
		}
	}

	private static IThrowableProxy rootCause(IThrowableProxy ex)
	{
		while (ex != null && ex.getCause() != null && ex.getCause() != ex)
		{
			ex = ex.getCause();
		}
		return ex;
	}

	private static List<String> frames(IThrowableProxy ex)
	{
		List<String> frames = new ArrayList<>();
		if (ex == null)
		{
			return frames;
		}
		for (StackTraceElementProxy proxy : ex.getStackTraceElementProxyArray())
		{
			if (frames.size() >= MAX_FRAMES)
			{
				break;
			}
			StackTraceElement element = proxy.getStackTraceElement();
			frames.add(element.getClassName() + "." + element.getMethodName() + ":" + element.getLineNumber());
		}
		return frames;
	}

	private String scrub(String raw)
	{
		String name = playerName;
		if (raw != null && name != null && !name.isEmpty())
		{
			raw = raw.replace(name, "[player]").replace(name.replace('\u00A0', ' '), "[player]");
		}
		return DiagnosticReport.clean(raw);
	}
}
