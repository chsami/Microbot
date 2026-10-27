package net.runelite.client.plugins.microbot.ddchopper;

import lombok.Getter;

/**
 * Every woodcutting axe, in ascending tier order (ordinal == tier). Requirements from the OSRS Wiki "Axe" page:
 * Woodcutting level to chop, Attack level only to wield (an axe works from the inventory without it).
 */
@Getter
public enum Axe
{
	BRONZE("Bronze axe", 1351, 1, 1, true),
	IRON("Iron axe", 1349, 1, 1, true),
	STEEL("Steel axe", 1353, 6, 5, true),
	BLACK("Black axe", 1361, 11, 10, true),
	MITHRIL("Mithril axe", 1355, 21, 20, true),
	ADAMANT("Adamant axe", 1357, 31, 30, true),
	RUNE("Rune axe", 1359, 41, 40, true),
	DRAGON("Dragon axe", 6739, 61, 60, true),
	/** Crystal axes are made from a crystal tool seed, never sold at the Grand Exchange. */
	CRYSTAL("Crystal axe", 23673, 71, 70, false);

	private final String itemName;
	private final int itemId;
	private final int woodcuttingLevel;
	private final int attackLevel;
	private final boolean buyable;

	Axe(String itemName, int itemId, int woodcuttingLevel, int attackLevel, boolean buyable)
	{
		this.itemName = itemName;
		this.itemId = itemId;
		this.woodcuttingLevel = woodcuttingLevel;
		this.attackLevel = attackLevel;
		this.buyable = buyable;
	}

	/** Item ids of every axe in tier order; a fresh array per call so callers cannot alter shared state. */
	public static Integer[] itemIds()
	{
		Integer[] ids = new Integer[values().length];
		for (Axe axe : values())
		{
			ids[axe.ordinal()] = axe.itemId;
		}
		return ids;
	}

	public boolean canChop(int woodcuttingLevel)
	{
		return woodcuttingLevel >= this.woodcuttingLevel;
	}

	public boolean canWield(int attackLevel)
	{
		return attackLevel >= this.attackLevel;
	}
}
