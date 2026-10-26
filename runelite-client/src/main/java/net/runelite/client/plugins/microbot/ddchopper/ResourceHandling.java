package net.runelite.client.plugins.microbot.ddchopper;

import java.util.Locale;

/** Pure decisions for what to do with a full inventory of logs, plus the hint text the side panel shows. */
public final class ResourceHandling
{
	public enum Action
	{
		DROP, BANK, BURN
	}

	private ResourceHandling()
	{
	}

	/** Firemaking level needed to light a log, or -1 for something that is not a burnable log. */
	public static int firemakingLevel(String logName)
	{
		if (logName == null)
		{
			return -1;
		}
		switch (logName.toLowerCase(Locale.ROOT).trim())
		{
			case "logs":
				return 1;
			case "oak logs":
				return 15;
			case "willow logs":
				return 30;
			case "teak logs":
				return 35;
			case "maple logs":
				return 45;
			case "mahogany logs":
				return 50;
			case "yew logs":
				return 60;
			case "magic logs":
				return 75;
			case "redwood logs":
				return 90;
			default:
				return -1;
		}
	}

	/**
	 * What to do now. Burn needs a tinderbox and enough Firemaking for the log in hand; otherwise it falls back to
	 * dropping so the run never stalls.
	 */
	public static Action decide(ChopMode mode, boolean hasTinderbox, int firemakingLevel, String logName)
	{
		switch (mode)
		{
			case BANK:
				return Action.BANK;
			case BURN:
				int need = firemakingLevel(logName);
				return hasTinderbox && need > 0 && firemakingLevel >= need ? Action.BURN : Action.DROP;
			default:
				return Action.DROP;
		}
	}

	/** One-line hint for the panel. */
	public static String hint(ChopMode mode)
	{
		switch (mode)
		{
			case BANK:
				return "Bank: walk to the nearest bank and deposit the logs, then come back.";
			case BURN:
				return "Burn: light fires with a tinderbox for Firemaking XP. Needs a tinderbox; drops if you cannot burn the log yet.";
			default:
				return "Drop: drop the logs where you stand. Fastest Woodcutting XP.";
		}
	}
}
