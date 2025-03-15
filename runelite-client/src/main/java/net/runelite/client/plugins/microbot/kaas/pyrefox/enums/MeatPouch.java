package net.runelite.client.plugins.microbot.kaas.pyrefox.enums;

import lombok.Getter;
import net.runelite.api.ItemID;

public enum MeatPouch
{
	SMALL_MEAT_POUCH(ItemID.SMALL_MEAT_POUCH, ItemID.SMALL_MEAT_POUCH_OPEN),
	LARGE_MEAT_POUCH(ItemID.LARGE_MEAT_POUCH, ItemID.LARGE_MEAT_POUCH_OPEN);

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
