package net.runelite.client.plugins.microbot.ddchopper;

/** Wait timeouts and animation windows shared by the script's collaborators. */
final class ScriptTimings
{
	/** How long to wait for an inventory, bank or equipment change after one action. */
	static final int ACTION_TIMEOUT_MS = 3_000;
	/** Animation window that still counts as "busy" (chopping or lighting a fire). */
	static final int ANIMATION_WINDOW_MS = 1800;

	/** Animation window that means a fire-lighting attempt has started. */
	static final int LIGHT_START_ANIMATION_MS = 1200;
	/** How long lighting one fire may take before the next tick re-evaluates. */
	static final int LIGHT_FINISH_TIMEOUT_MS = 12_000;

	private ScriptTimings()
	{
	}
}
