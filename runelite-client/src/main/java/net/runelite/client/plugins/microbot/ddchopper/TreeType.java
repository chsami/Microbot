package net.runelite.client.plugins.microbot.ddchopper;

import lombok.Getter;

/**
 * Trees this plugin can train on, in level order, with the in-game object name used to find them.
 * Levels and members flags from the OSRS Wiki "Woodcutting" page.
 */
@Getter
public enum TreeType
{
	NORMAL("Tree", "Logs", 1, false),
	OAK("Oak tree", "Oak logs", 15, false),
	WILLOW("Willow tree", "Willow logs", 30, false),
	TEAK("Teak tree", "Teak logs", 35, true),
	MAPLE("Maple tree", "Maple logs", 45, true),
	MAHOGANY("Mahogany tree", "Mahogany logs", 50, true),
	YEW("Yew tree", "Yew logs", 60, false),
	MAGIC("Magic tree", "Magic logs", 75, true),
	REDWOOD("Redwood tree", "Redwood logs", 90, true);

	private final String objectName;
	private final String logName;
	private final int level;
	private final boolean members;

	TreeType(String objectName, String logName, int level, boolean members)
	{
		this.objectName = objectName;
		this.logName = logName;
		this.level = level;
		this.members = members;
	}
}
