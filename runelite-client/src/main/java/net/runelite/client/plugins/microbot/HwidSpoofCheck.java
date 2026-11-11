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
	static final String PLATFORM_HOOK_PROPERTY = "microbot.hwid.platform.installed";
	static final String RANDOM_DAT_CALL_PROPERTY = "microbot.hwid.randomdat.lastCall";
	static final String PLATFORM_MISSED_PROPERTY = "microbot.hwid.platform.missed";
	static final String PLATFORM_SCANNED_PROPERTY = "microbot.hwid.platform.scanned";
	static final String SIGNER_STATUS_PROPERTY = "microbot.hwid.signer.status";
	static final String SIGNER_CHANGED = "changed";
	static final String SIGNER_EXPIRED = "expired";

	static final String LOGIN_BLOCKED_TITLE = "Microbot stopped this login";
	static final String LOGIN_BLOCKED_STAY = "Stay logged out";
	static final String LOGIN_BLOCKED_BYPASS = "Log in without spoofing";
	private static final String LOGIN_BLOCKED_QUESTION = "\n\nSpoofing is not working. Are you sure you want to log in without it?"
		+ "\nIf you choose \"" + LOGIN_BLOCKED_BYPASS + "\", spoofing is switched off until you restart the client, and your real device ID and client details are sent. Then click Login again.";

	static final String AGENT_MISSING_CHAT = "Spoofing is not active, so this login sent your real device details. Restart the client from the Microbot launcher and check that Spoof hardware IDs is ticked. Already ticked? Report it in the Microbot Discord.";
	static final String AGENT_INERT_CHAT = "This client started with spoofing off, so this login sent your real device details. Restart from the Microbot launcher, or turn off Spoof device ID in Microbot > General.";
	static final String PLATFORM_HOOK_MISSING_CHAT = "Spoof device ID is on, but this client version could not hide some client details at login, so they were sent unchanged. Your device ID and login seed are still spoofed, so keep Spoof device ID on and report this in the Microbot Discord.";
	static final String HOOK_FAILED_CHAT = "Spoof device ID is on, but spoofing did not work in this client version, so this login may have sent the real device details. Report it in the Microbot Discord, or turn off Spoof device ID in Microbot > General.";

	enum Problem
	{
		AGENT_MISSING(AGENT_MISSING_CHAT, "HWID agent not attached (microbot.hwid.agent is not set). To start the client by hand with spoofing, add -javaagent:hwid-agent-<version>.jar to the JVM arguments."),
		AGENT_INERT(AGENT_INERT_CHAT, "HWID agent attached with -Dmicrobot.hwid.spoof=false, so it registered no transformer (microbot.hwid.agent=inert). Drop that flag to spoof."),
		DEVICE_HOOK_MISSING(HOOK_FAILED_CHAT, "HWID agent loaded but could not hook the device ID method in this client version (microbot.hwid.device.installed is not set). If this version reads the device ID, the real one was sent."),
		RANDOM_DAT_HOOK_MISSED(HOOK_FAILED_CHAT, "HWID agent loaded but its random.dat hook did not run during this login (microbot.hwid.randomdat.lastCall is older than the login start), so the real random.dat may have been sent."),
		PLATFORM_HOOK_MISSING(PLATFORM_HOOK_MISSING_CHAT, "HWID agent loaded but could not hook both the signer hash and the process names in this client version (microbot.hwid.platform.installed is not set), so those were sent unchanged."),
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

	/** A reason a login must not be sent at all: it would carry details no stock RuneLite client sends. */
	enum LoginBlock
	{
		SIGNER_CHANGED(
			"Why: RuneLite now signs its client with a new certificate. This Microbot version would send the old signature, which no real RuneLite client sends any more, so the login would stand out."
				+ "\n\nWhat to do: update Microbot, then log in again.",
			"Login blocked: the stock RuneLite client jar no longer hashes to the bundled signer hash (microbot.hwid.signer.status=changed). Rebundle StockSignerHash.BUNDLED."),
		SIGNER_EXPIRED(
			"Why: the RuneLite certificate this Microbot version copies ran out on 15 April 2027, and Microbot could not reach RuneLite to check that real clients still send the same signature. Sending a signature that may be out of date would make the login stand out."
				+ "\n\nWhat to do: check your internet connection and restart the client, or update Microbot.",
			"Login blocked: the bundled signer certificate is past its expiry and the stock client could not be checked (microbot.hwid.signer.status=expired)."),
		PLATFORM_DETAILS_MIXED(
			"Why: the game changed how it reads client details, and this Microbot version can only hide some of them. The login would send a mix of real and spoofed details that no real RuneLite client sends."
				+ "\n\nWhat to do: update Microbot. If you are already on the newest version, report this in the Microbot Discord.",
			"Login blocked: the signer hash or agent name hook is not fully installed in this client version (microbot.hwid.platform.missed=true, or microbot.hwid.platform.scanned=true without microbot.hwid.platform.installed), so those details would be sent partly unspoofed.");

		private final String message;
		private final String detail;

		LoginBlock(String reason, String detail)
		{
			this.message = "Spoof device ID is on, and this login was stopped before anything was sent.\n\n" + reason + LOGIN_BLOCKED_QUESTION;
			this.detail = detail;
		}

		/** Shown to the player: why the login was stopped and what to do. */
		String message()
		{
			return message;
		}

		String logMessage()
		{
			return detail;
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

	/**
	 * Asked when a login starts, before the client has sent anything. Only blocks on what is known to be
	 * wrong while the agent is spoofing; a missing or inert agent is the player's launcher choice and is
	 * reported after login as before.
	 */
	Optional<LoginBlock> loginBlock(boolean spoofEnabled)
	{
		if (!spoofEnabled || Boolean.parseBoolean(properties.apply(LAUNCHER_DISABLED_PROPERTY))
			|| !AGENT_LOADED.equals(properties.apply(AGENT_PROPERTY)))
		{
			return Optional.empty();
		}
		String signer = properties.apply(SIGNER_STATUS_PROPERTY);
		if (SIGNER_CHANGED.equals(signer))
		{
			return Optional.of(LoginBlock.SIGNER_CHANGED);
		}
		if (SIGNER_EXPIRED.equals(signer))
		{
			return Optional.of(LoginBlock.SIGNER_EXPIRED);
		}
		if (properties.apply(PLATFORM_MISSED_PROPERTY) != null)
		{
			return Optional.of(LoginBlock.PLATFORM_DETAILS_MIXED);
		}
		// Once the agent has loaded every class it could hook, a hook that is still not installed never will be.
		if (properties.apply(PLATFORM_SCANNED_PROPERTY) != null && properties.apply(PLATFORM_HOOK_PROPERTY) == null)
		{
			return Optional.of(LoginBlock.PLATFORM_DETAILS_MIXED);
		}
		return Optional.empty();
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
		// Checked last: its chat line says the device ID and random.dat are still spoofed,
		// which only holds once both of those hooks are known to have run.
		if (properties.apply(PLATFORM_HOOK_PROPERTY) == null)
		{
			return Problem.PLATFORM_HOOK_MISSING;
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
