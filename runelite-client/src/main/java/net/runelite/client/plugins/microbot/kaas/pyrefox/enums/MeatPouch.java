package net.runelite.client.plugins.microbot.kaas.pyrefox.enums;

import lombok.Getter;
import net.runelite.api.gameval.ItemID;

public enum MeatPouch
{
	SMALL_MEAT_POUCH(ItemID.HG_MEATPOUCH_SMALL, ItemID.HG_MEATPOUCH_SMALL_OPEN),
	LARGE_MEAT_POUCH(ItemID.HG_MEATPOUCH_LARGE, ItemID.HG_MEATPOUCH_LARGE_OPEN);

	@Getter
	private final int closedItemID;

	@Getter
	private final int openItemID;

	MeatPouch(int closedItemID, int openItemID)
	{
		this.openItemID = openItemID;
		this.closedItemID = closedItemID;
	}
}
