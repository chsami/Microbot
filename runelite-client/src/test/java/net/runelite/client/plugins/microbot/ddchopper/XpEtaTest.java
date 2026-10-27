package net.runelite.client.plugins.microbot.ddchopper;

import net.runelite.api.Experience;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class XpEtaTest
{
	@Test
	public void zeroRateGivesNoEstimate()
	{
		assertEquals(XpEta.NONE, XpEta.millisToNextLevel(30, 0, 0));
		assertEquals(XpEta.UNKNOWN, XpEta.format(XpEta.NONE));
	}

	@Test
	public void nextLevelUsesRate()
	{
		long xp = Experience.getXpForLevel(30);
		// 1,000 xp short at 36,000 xp/h is 100 s.
		long nextXp = Experience.getXpForLevel(31);
		assertEquals(100_000, XpEta.millisToNextLevel(30, nextXp - 1_000, 36_000));
		assertEquals(XpEta.NONE, XpEta.millisToNextLevel(30, xp, 0));
	}

	@Test
	public void alreadyAtOrOverTargetIsZero()
	{
		long xp = Experience.getXpForLevel(35);
		assertEquals(0, XpEta.millisToLevel(35, xp, 10_000));
		assertEquals(0, XpEta.millisToLevel(35, xp + 5_000, 0));
		assertEquals(0, XpEta.millisToLevel(20, xp, 10_000));
	}

	@Test
	public void levelCapHasNoNextLevel()
	{
		assertEquals(XpEta.NONE, XpEta.millisToNextLevel(99, Experience.getXpForLevel(99), 50_000));
	}

	@Test
	public void targetAboveCapIsClamped()
	{
		long xp = Experience.getXpForLevel(99) - 36_000;
		assertEquals(3_600_000, XpEta.millisToLevel(120, xp, 36_000));
	}

	@Test
	public void formatBoundaries()
	{
		assertEquals("0s", XpEta.format(0));
		assertEquals("59s", XpEta.format(59_999));
		assertEquals("1m 0s", XpEta.format(60_000));
		assertEquals("1m 50s", XpEta.format(110_000));
		assertEquals("59m 59s", XpEta.format(3_599_999));
		assertEquals("1h 0m", XpEta.format(3_600_000));
		assertEquals("2h 5m", XpEta.format(7_500_000));
	}
}
