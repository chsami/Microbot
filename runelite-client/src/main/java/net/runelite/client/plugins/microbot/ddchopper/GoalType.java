package net.runelite.client.plugins.microbot.ddchopper;

public enum GoalType
{
	TARGET_LEVEL("Target level"),
	RUN_TIME("Run time"),
	UNTIL_STOPPED("Until stopped");

	private final String label;

	GoalType(String label)
	{
		this.label = label;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
