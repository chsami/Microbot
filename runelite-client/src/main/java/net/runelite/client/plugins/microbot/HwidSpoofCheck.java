package net.runelite.client.plugins.microbot;

import java.util.EnumSet;
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

	static final String AGENT_MISSING_CHAT = "Spoofing is not active, so this login sent your real device details. Restart the client from the Microbot launcher and check that Spoof hardware IDs is ticked. Already ticked? Report it in the Microbot Discord.";
	static final String AGENT_INERT_CHAT = "This client started with spoofing off, so this login sent your real device details. Restart from the Microbot launcher, or turn off Spoof device ID in Microbot > General.";
	static final String HOOK_FAILED_CHAT = "Spoof device ID is on, but spoofing did not work in this client version, so this login may have sent the real device details. Report it in the Microbot Discord, or turn off Spoof device ID in Microbot > General.";

	enum Problem
	{
		AGENT_MISSING(AGENT_MISSING_CHAT, "HWID agent not attached (microbot.hwid.agent is not set). To start the client by hand with spoofing, add -javaagent:hwid-agent-<version>.jar to the JVM arguments."),
		AGENT_INERT(AGENT_INERT_CHAT, "HWID agent attached with -Dmicrobot.hwid.spoof=false, so it registered no transformer (microbot.hwid.agent=inert). Drop that flag to spoof."),
		DEVICE_HOOK_MISSING(HOOK_FAILED_CHAT, "HWID agent loaded but could not hook the device ID method in this client version (microbot.hwid.device.installed is not set). If this version reads the device ID, the real one was sent."),
		RANDOM_DAT_HOOK_MISSED(HOOK_FAILED_CHAT, "HWID agent loaded but its random.dat hook did not run during this login (microbot.hwid.randomdat.lastCall is older than the login start), so the real random.dat may have been sent."),
		SETTING_OFF_WITH_LAUNCHER_ON(null, "HWID agent loaded, but Spoof device ID is off in Microbot > General, so this login sent the real device details.");

		private final String chat;
		private final String detail;

		Problem(String chat, String detail)
		{
			this.chat = chat;
			this.detail = detail;
		}

		Optional<String> chatMessage()
		{
			return Optional.ofNullable(chat);
		}

		String logMessage()
		{
			return chat == null ? detail : chat + " " + detail;
		}
	}

	private final Function<String, String> properties;
	private final LongSupplier clock;
	private final Set<Problem> reported = EnumSet.noneOf(Problem.class);
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

	synchronized Optional<Problem> loggedIn(boolean spoofEnabled)
	{
		long startedAt = loginStartedAt;
		loginStartedAt = -1;
		if (startedAt < 0 || Boolean.parseBoolean(properties.apply(LAUNCHER_DISABLED_PROPERTY)))
		{
			return Optional.empty();
		}
		Problem problem = spoofEnabled ? problem(startedAt) : settingOffProblem();
		if (problem == null || !reported.add(problem))
		{
			return Optional.empty();
		}
		return Optional.of(problem);
	}

	private Problem settingOffProblem()
	{
		return AGENT_LOADED.equals(properties.apply(AGENT_PROPERTY)) ? Problem.SETTING_OFF_WITH_LAUNCHER_ON : null;
	}

	private Problem problem(long startedAt)
	{
		String agent = properties.apply(AGENT_PROPERTY);
		if (agent == null)
		{
			return Problem.AGENT_MISSING;
		}
		if (AGENT_INERT.equals(agent))
		{
			return Problem.AGENT_INERT;
		}
		if (properties.apply(DEVICE_HOOK_PROPERTY) == null)
		{
			return Problem.DEVICE_HOOK_MISSING;
		}
		if (lastRandomDatCall() < startedAt)
		{
			return Problem.RANDOM_DAT_HOOK_MISSED;
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
