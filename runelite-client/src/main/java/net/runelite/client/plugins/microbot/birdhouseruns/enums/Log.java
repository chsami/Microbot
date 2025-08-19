package net.runelite.client.plugins.microbot.birdhouseruns.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.gameval.ItemID;

@Getter
@RequiredArgsConstructor
public enum Log
{
	NORMAL_LOGS("Logs", ItemID.LOGS),
	OAK_LOGS("Oak logs", ItemID.OAK_LOGS),
	WILLOW_LOGS("Willow logs", ItemID.WILLOW_LOGS),
	TEAK_LOGS("Teak logs", ItemID.TEAK_LOGS),
	MAPLE_LOGS("Maple logs", ItemID.MAPLE_LOGS),
	MAHOGANY_LOGS("Mahogany logs", ItemID.MAHOGANY_LOGS),
	YEW_LOGS("Yew logs", ItemID.YEW_LOGS),
	MAGIC_LOGS("Magic logs", ItemID.MAGIC_LOGS),
	REDWOOD_LOGS("Redwood logs", ItemID.REDWOOD_LOGS);

	private final String itemName;
	private final int itemId;
}
