package net.runelite.client.plugins.microbot.ddchopper;

/** Wait timeouts and animation windows shared by the script's collaborators. */
final class ScriptTimings
{
	/** How long to wait for an inventory, bank or equipment change after one action. */
	static final int ACTION_TIMEOUT_MS = 3_000;
	/** Animation window that still counts as "busy" (chopping or lighting a fire). */
	static final int ANIMATION_WINDOW_MS = 1800;

	private ScriptTimings()
	{
	}
}
