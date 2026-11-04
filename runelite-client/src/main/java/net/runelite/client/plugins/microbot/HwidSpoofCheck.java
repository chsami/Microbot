package net.runelite.client.plugins.microbot;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;

final class HwidSpoofCheck
{
	static final String AGENT_PROPERTY = "microbot.hwid.agent";
	static final String AGENT_INERT = "inert";
	static final String LAUNCHER_DISABLED_PROPERTY = "microbot.hwid.launcherDisabled";
	static final String DEVICE_HOOK_PROPERTY = "microbot.hwid.device.installed";
	static final String PLATFORM_HOOK_PROPERTY = "microbot.hwid.platform.installed";
	static final String RANDOM_DAT_CALL_PROPERTY = "microbot.hwid.randomdat.lastCall";

	static final String AGENT_MISSING = "Spoof device ID is on, but the HWID agent is not running, so this login sent the real device details. Update the Microbot launcher, make sure Spoof hardware IDs is ticked, and restart the client.";
	static final String LAUNCHER_NOT_OPTED_IN = "Spoof device ID is on, but the launcher started the client without it, so this login sent the real device details. Update the Microbot launcher, tick Spoof hardware IDs, and restart the client.";
	static final String LAUNCHER_DISABLED = "Spoof device ID has no effect until you tick Spoof hardware IDs in the Microbot launcher and restart the client. This login sent the real device details.";
	/** Log only; raw JVM flags stay out of game chat. */
	static final String MANUAL_LAUNCH_HINT = "To start the client by hand with spoofing, add -javaagent:hwid-agent-<version>.jar -Dmicrobot.hwid.spoof=true to the JVM arguments.";
	static final String DEVICE_HOOK_MISSING = "Spoof device ID is on, but the HWID agent could not hook the device ID method in this client version. If this version reads the device ID, the real one was sent at this login.";
	static final String PLATFORM_HOOK_MISSING = "Spoof device ID is on, but some client details were sent unchanged at this login. The device ID and random.dat are still spoofed. Update Microbot, or report this on Discord if it keeps happening.";
	static final String RANDOM_DAT_HOOK_MISSED = "Spoof device ID is on, but the HWID agent's random.dat hook did not run during this login, so the real random.dat may have been sent.";

	private final Function<String, String> properties;
	private final LongSupplier clock;
	private final Set<String> reported = new HashSet<>();
	private long loginStartedAt;

	HwidSpoofCheck()
	{
		this(System::getProperty, System::currentTimeMillis);
	}

	HwidSpoofCheck(Function<String, String> properties, LongSupplier clock)
	{
		this.properties = properties;
		this.clock = clock;
	}

	synchronized void loginStarted()
	{
		loginStartedAt = clock.getAsLong();
	}

	synchronized Optional<String> loggedIn(boolean spoofEnabled)
	{
		long startedAt = loginStartedAt;
		loginStartedAt = -1;
		if (!spoofEnabled || startedAt < 0)
		{
			return Optional.empty();
		}
		String problem = problem(startedAt);
		if (problem == null || !reported.add(problem))
		{
			return Optional.empty();
		}
		return Optional.of(problem);
	}

	/** Whether the log line for this problem should carry {@link #MANUAL_LAUNCH_HINT}. */
	static boolean needsManualLaunchHint(String problem)
	{
		return AGENT_MISSING.equals(problem) || LAUNCHER_NOT_OPTED_IN.equals(problem);
	}

	private String problem(long startedAt)
	{
		if (Boolean.parseBoolean(properties.apply(LAUNCHER_DISABLED_PROPERTY)))
		{
			return LAUNCHER_DISABLED;
		}
		String agent = properties.apply(AGENT_PROPERTY);
		if (agent == null)
		{
			return AGENT_MISSING;
		}
		if (AGENT_INERT.equals(agent))
		{
			return LAUNCHER_NOT_OPTED_IN;
		}
		if (properties.apply(DEVICE_HOOK_PROPERTY) == null)
		{
			return DEVICE_HOOK_MISSING;
		}
		if (properties.apply(PLATFORM_HOOK_PROPERTY) == null)
		{
			return PLATFORM_HOOK_MISSING;
		}
		if (lastRandomDatCall() < startedAt)
		{
			return RANDOM_DAT_HOOK_MISSED;
		}
		return null;
	}

	private long lastRandomDatCall()
	{
		String value = properties.apply(RANDOM_DAT_CALL_PROPERTY);
		if (value == null)
		{
			return -1;
		}
		try
		{
			return Long.parseLong(value);
		}
		catch (NumberFormatException e)
		{
			return -1;
		}
	}
}
