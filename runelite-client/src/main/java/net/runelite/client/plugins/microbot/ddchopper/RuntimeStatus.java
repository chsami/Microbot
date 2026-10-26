package net.runelite.client.plugins.microbot.ddchopper;

/** Pure formatting of the run state shown in the side panel. */
public final class RuntimeStatus
{
	public enum State
	{
		RUNNING, PAUSED, ON_BREAK, STOPPED
	}

	private RuntimeStatus()
	{
	}

	public static State state(boolean running, boolean paused, boolean onBreak)
	{
		if (!running)
		{
			return State.STOPPED;
		}
		if (onBreak)
		{
			return State.ON_BREAK;
		}
		return paused ? State.PAUSED : State.RUNNING;
	}

	/** "RUNNING", or "STOPPED: No axe equipped/in inventory" when there is a reason. */
	public static String describe(State state, String reason)
	{
		String name = state == State.ON_BREAK ? "ON BREAK" : state.name();
		return reason == null || reason.isEmpty() ? name : name + ": " + reason;
	}

	/** "35 -> 60" for a target level goal, otherwise just the level. */
	public static String levelLine(GoalType goal, int level, int target)
	{
		return goal == GoalType.TARGET_LEVEL ? level + " -> " + target : String.valueOf(level);
	}
}
