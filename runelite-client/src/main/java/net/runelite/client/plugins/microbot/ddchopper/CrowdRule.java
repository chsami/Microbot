package net.runelite.client.plugins.microbot.ddchopper;

/**
 * Which world-hop rule applies. Avoiding crowds and seeking a busy world (for Forestry events) are opposites, so
 * they are mutually exclusive: Forestry wins while Forestry events are enabled. Pure.
 */
public final class CrowdRule
{
	public enum Mode
	{
		OFF, AVOID_CROWD, SEEK_BUSY
	}

	private CrowdRule()
	{
	}

	public static Mode resolve(boolean forestryEnabled, boolean preferBusy, boolean avoidCrowd)
	{
		if (forestryEnabled && preferBusy)
		{
			return Mode.SEEK_BUSY;
		}
		return avoidCrowd ? Mode.AVOID_CROWD : Mode.OFF;
	}

	/** True when both options are on and one is being overridden. */
	public static boolean conflict(boolean forestryEnabled, boolean preferBusy, boolean avoidCrowd)
	{
		return forestryEnabled && preferBusy && avoidCrowd;
	}

	/** Panel note for the conflict, or empty when there is none. */
	public static String conflictNote(boolean forestryEnabled, boolean preferBusy, boolean avoidCrowd)
	{
		return conflict(forestryEnabled, preferBusy, avoidCrowd)
			? "Both busy-world and crowd-avoid are on: Forestry wins, so crowd-avoid is ignored." : "";
	}
}
