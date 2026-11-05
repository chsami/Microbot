package net.runelite.client.plugins.microbot;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;

final class HwidSpoofCheck
{
	static final String AGENT_PROPERTY = "microbot.hwid.agent";
	static final String AGENT_LOADED = "loaded";
	static final String AGENT_INERT = "inert";
	static final String LAUNCHER_DISABLED_PROPERTY = "microbot.hwid.launcherDisabled";
	static final String DEVICE_HOOK_PROPERTY = "microbot.hwid.device.installed";
	static final String RANDOM_DAT_CALL_PROPERTY = "microbot.hwid.randomdat.lastCall";

	static final String AGENT_MISSING = "Spoof device ID is on, but the HWID agent is not running, so this login sent the real device details. Close the client and start it again from the Microbot launcher with Spoof hardware IDs ticked. If you do not want spoofing, turn off Spoof device ID in Microbot > General.";
	static final String LAUNCHER_NOT_OPTED_IN = "Spoof device ID is on, but the launcher started the client with spoofing off, so this login sent the real device details. To spoof, tick Spoof hardware IDs in the Microbot launcher and restart the client. If it is already ticked, your launcher is too old to turn spoofing on; update it when a new version is out. If you do not want spoofing, turn off Spoof device ID in Microbot > General.";
	static final String SETTING_OFF_WITH_LAUNCHER_ON = "Spoof hardware IDs is ticked in the Microbot launcher, but Spoof device ID is off in Microbot > General, so this login sent the real device details. Turn on Spoof device ID there to spoof from your next login, or untick Spoof hardware IDs in the launcher if you do not want spoofing.";
	static final String LAUNCHER_DISABLED = "Spoof device ID is on, but Spoof hardware IDs is not ticked in the Microbot launcher, so this login sent the real device details. To spoof, tick Spoof hardware IDs in the launcher and restart the client. If you do not want spoofing, turn off Spoof device ID in Microbot > General.";
	/** Log only; raw JVM flags stay out of game chat. */
	static final String MANUAL_LAUNCH_HINT = "To start the client by hand with spoofing, add -javaagent:hwid-agent-<version>.jar -Dmicrobot.hwid.spoof=true to the JVM arguments.";
	static final String DEVICE_HOOK_MISSING = "Spoof device ID is on, but the HWID agent could not hook the device ID method in this client version. If this version reads the device ID, the real one was sent at this login.";
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
		if (startedAt < 0)
		{
			return Optional.empty();
		}
		String problem = spoofEnabled ? problem(startedAt) : settingOffProblem();
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

	/** The launcher started the agent on purpose, but the in-client setting is off. */
	private String settingOffProblem()
	{
		return AGENT_LOADED.equals(properties.apply(AGENT_PROPERTY)) ? SETTING_OFF_WITH_LAUNCHER_ON : null;
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
