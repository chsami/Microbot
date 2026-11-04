package net.runelite.client.plugins.microbot;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.LongSupplier;

final class HwidSpoofCheck
{
	static final String AGENT_PROPERTY = "microbot.hwid.agent";
	static final String LAUNCHER_DISABLED_PROPERTY = "microbot.hwid.launcherDisabled";
	static final String DEVICE_HOOK_PROPERTY = "microbot.hwid.device.installed";
	static final String PLATFORM_HOOK_PROPERTY = "microbot.hwid.platform.installed";
	static final String RANDOM_DAT_CALL_PROPERTY = "microbot.hwid.randomdat.lastCall";

	static final String AGENT_MISSING = "Spoof device ID is on, but the HWID agent is not attached, so this login sent the real random.dat (and the real device ID, if this client version reads it). Turn on Spoof hardware IDs in the launcher and restart, or start the client with -javaagent:hwid-agent-<version>.jar -Dmicrobot.hwid.spoof=true.";
	static final String DEVICE_HOOK_MISSING = "Spoof device ID is on, but the HWID agent could not hook the device ID method in this client version. If this version reads the device ID, the real one was sent at this login.";
	static final String PLATFORM_HOOK_MISSING = "Spoof device ID is on, but the HWID agent could not hook the platform details in this client version, so the login packet kept the real signer hash and process names.";
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
		if (!spoofEnabled || startedAt < 0 || Boolean.parseBoolean(properties.apply(LAUNCHER_DISABLED_PROPERTY)))
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

	private String problem(long startedAt)
	{
		if (properties.apply(AGENT_PROPERTY) == null)
		{
			return AGENT_MISSING;
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
