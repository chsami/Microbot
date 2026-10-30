package net.runelite.client.plugins.microbot.questhelper;

/**
 * What an NPC step does with an NPC it knows about. A click the server cannot act on (the NPC is behind a closed door)
 * repeats every tick without effect, while walking lets the walker open the door; and walking toward an NPC the player
 * already stands next to does nothing at all. So: click when the NPC is in sight and either right beside the player or
 * reachable by the route planner, walk otherwise, and walk instead after a click on it just proved useless. The click
 * itself is verified by {@link VerifiedInteraction}, which also turns the camera.
 */
public final class QuestNpcApproach
{
	/** Distance in tiles up to which an NPC in line of sight is clicked without asking the planner. */
	static final int POINT_BLANK = 2;

	/** The next move toward an NPC. */
	public enum Move
	{
		/** Click the NPC. */
		INTERACT,
		/** Walk closer. */
		WALK
	}

	private QuestNpcApproach()
	{
	}

	/**
	 * @param lineOfSight    there is line of sight to it; bars, fences and counters do not block it, so this alone proves
	 *                       nothing about a closed door beyond them
	 * @param reachable      the route planner reaches it (only asked beyond {@link #POINT_BLANK})
	 * @param distance       tiles between the player and the NPC
	 * @param clickFailedRecently a verified click on this NPC just had no effect
	 */
	public static Move decide(boolean lineOfSight, boolean reachable, int distance, boolean clickFailedRecently)
	{
		if (!lineOfSight || clickFailedRecently)
		{
			return Move.WALK;
		}
		return reachable || distance <= POINT_BLANK ? Move.INTERACT : Move.WALK;
	}
}
