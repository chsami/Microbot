package net.runelite.client.plugins.microbot.util.misc;

import java.time.LocalDate;
import net.runelite.client.ui.SplashScreen;
import net.runelite.client.util.ImageUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class HalloweenSeasonTest
{
	@Test
	public void activeFromTwentiethOfOctoberToFirstOfNovember()
	{
		assertTrue(HalloweenSeason.isActive(LocalDate.of(2026, 10, 20)));
		assertTrue(HalloweenSeason.isActive(LocalDate.of(2026, 10, 31)));
		assertTrue(HalloweenSeason.isActive(LocalDate.of(2026, 11, 1)));
	}

	@Test
	public void inactiveOutsideTheSeason()
	{
		assertFalse(HalloweenSeason.isActive(LocalDate.of(2026, 10, 19)));
		assertFalse(HalloweenSeason.isActive(LocalDate.of(2026, 11, 2)));
		assertFalse(HalloweenSeason.isActive(LocalDate.of(2026, 12, 25)));
		assertFalse(HalloweenSeason.isActive(LocalDate.of(2027, 1, 1)));
	}

	@Test
	public void returnsEveryYear()
	{
		assertTrue(HalloweenSeason.isActive(LocalDate.of(2027, 10, 25)));
	}

	@Test
	public void halloweenSplashLoads()
	{
		assertEquals(200, ImageUtil.loadImageResource(SplashScreen.class, "microbot_splash_halloween.png").getWidth());
	}
}
