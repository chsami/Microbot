package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerConfig;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerPlugin;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;

import java.time.Duration;
import java.util.Random;

/** Feeds randomised break lengths into Microbot's Break Handler and renders its countdown for the overlay. */
final class BreakSync
{
	private final DDChopperConfig config;
	private final BreakPlan plan;
	private boolean synced;
	private int lastTotalBreaks = -1;

	BreakSync(DDChopperConfig config)
	{
		this.config = config;
		this.plan = new BreakPlan(config.minPlay(), config.maxPlay(), config.minBreak(), config.maxBreak(), new Random());
	}

	/** Pushes freshly picked break lengths to the Break Handler and makes sure it is running. */
	void sync()
	{
		if (!config.enableBreaks())
		{
			return;
		}
		int total = BreakHandlerScript.totalBreaks;
		if (synced && total == lastTotalBreaks)
		{
			return;
		}
		synced = true;
		lastTotalBreaks = total;
		int play = plan.nextPlayMinutes();
		int rest = plan.nextBreakMinutes();
		String group = BreakHandlerConfig.configGroup;
		Microbot.getConfigManager().setConfiguration(group, "Min Playtime", play);
		Microbot.getConfigManager().setConfiguration(group, "Max Playtime", play);
		Microbot.getConfigManager().setConfiguration(group, "Min BreakTime", rest);
		Microbot.getConfigManager().setConfiguration(group, "Max BreakTime", rest);
		Microbot.getConfigManager().setConfiguration(group, "Logout", config.logoutOnBreak());
		Microbot.getConfigManager().setConfiguration(group, "OnlyMicroBreaks", false);
		Plugin handler = Microbot.getPlugin(BreakHandlerPlugin.class);
		if (handler != null && !Microbot.getPluginManager().isActive(handler))
		{
			Microbot.startPlugin(handler);
		}
		Microbot.log("DD Chopper: next break after " + play + " min, lasting " + rest + " min");
	}

	/** Overlay text for the break countdown. */
	String countdownText()
	{
		if (!config.enableBreaks())
		{
			return "Off";
		}
		if (BreakHandlerScript.isBreakActive())
		{
			return BreakHandlerScript.breakDuration > 0
				? "On break, " + BreakHandlerScript.formatDuration(Duration.ofSeconds(BreakHandlerScript.breakDuration))
				: "On break";
		}
		return BreakHandlerScript.breakIn > 0
			? "in " + BreakHandlerScript.formatDuration(Duration.ofSeconds(BreakHandlerScript.breakIn)) : "Pending";
	}
}
