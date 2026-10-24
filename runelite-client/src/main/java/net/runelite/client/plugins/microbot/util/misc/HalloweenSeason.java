package net.runelite.client.plugins.microbot.util.misc;

import java.time.LocalDate;
import java.time.Month;

public final class HalloweenSeason
{
	private HalloweenSeason()
	{
	}

	public static boolean isActive()
	{
		return isActive(LocalDate.now());
	}

	public static boolean isActive(LocalDate date)
	{
		return (date.getMonth() == Month.OCTOBER && date.getDayOfMonth() >= 20)
			|| (date.getMonth() == Month.NOVEMBER && date.getDayOfMonth() == 1);
	}
}
