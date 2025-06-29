package net.runelite.client.plugins.microbot.runecrafting.ourania.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.api.coords.WorldPoint;

@Getter
@RequiredArgsConstructor
public enum Path
{
	SHORT(new WorldPoint(3058, 5579, 0)),
	LONG(new WorldPoint(3052, 5587, 0));

	private final WorldPoint worldPoint;
}
