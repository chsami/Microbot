package net.runelite.client.plugins.microbot.runecrafting.ourania.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.ItemID;

@Getter
@RequiredArgsConstructor
public enum Essence
{
	PURE_ESSENCE(ItemID.PURE_ESSENCE),
	DAEYALT_ESSENCE(ItemID.DAEYALT_ESSENCE);

	private final int itemId;
}
