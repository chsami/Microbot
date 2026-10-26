package net.runelite.client.plugins.microbot.ddchopper;

/** Config dropdown: pick the tree automatically (by XP/hour) or force one. */
public enum TreeChoice
{
	AUTO("Auto (best XP/hr)", null),
	NORMAL("Tree", TreeType.NORMAL),
	OAK("Oak", TreeType.OAK),
	WILLOW("Willow", TreeType.WILLOW),
	TEAK("Teak (members)", TreeType.TEAK),
	MAPLE("Maple (members)", TreeType.MAPLE),
	MAHOGANY("Mahogany (members)", TreeType.MAHOGANY),
	YEW("Yew", TreeType.YEW),
	MAGIC("Magic (members)", TreeType.MAGIC),
	REDWOOD("Redwood (members)", TreeType.REDWOOD);

	private final String label;
	private final TreeType tree;

	TreeChoice(String label, TreeType tree)
	{
		this.label = label;
		this.tree = tree;
	}

	/** The forced tree, or null for {@link #AUTO}. */
	public TreeType tree()
	{
		return tree;
	}

	@Override
	public String toString()
	{
		return label;
	}
}
