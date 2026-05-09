package net.runelite.client.plugins.microbot;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/** {@link MicrobotPlugin#leaguesLockedRegionCapturedRegionAfterNormalizeForTests(String)} — Leagues lock chat regex. */
public class MicrobotPluginLeaguesLockedRegionChatTest
{
	private static final int CAP = 4096;
	private static final String PHRASE = "You haven't unlocked access to the Lumbridge area.";

	@Test
	public void capturesRegionPlain()
	{
		assertEquals("lumbridge",
				MicrobotPlugin.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"You haven't unlocked access to the Lumbridge area"));
	}

	@Test
	public void capturesRegionWithTrailingPeriod()
	{
		assertEquals("lumbridge",
				MicrobotPlugin.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"You haven't unlocked access to the Lumbridge area."));
	}

	@Test
	public void capturesRegionWithTrailingComma()
	{
		assertEquals("ardougne",
				MicrobotPlugin.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"You haven't unlocked access to the Ardougne area,"));
	}

	@Test
	public void capturesRegionWithTrailingParen()
	{
		assertEquals("kourend",
				MicrobotPlugin.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"You haven't unlocked access to the Kourend area)"));
	}

	@Test
	public void capturesRegionWithParentheticalAfterArea()
	{
		assertEquals("lumbridge",
				MicrobotPlugin.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"You haven't unlocked access to the Lumbridge area (retry)"));
	}

	@Test
	public void capturesRegionWithUnicodeEllipsisTail()
	{
		assertEquals("zeah",
				MicrobotPlugin.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"You haven't unlocked access to the Zeah area\u2026"));
	}

	@Test
	public void greedyLastAreaWinsWhenInnerAreaInName()
	{
		assertEquals("southern desert",
				MicrobotPlugin.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"You haven't unlocked access to the southern desert area."));
	}

	@Test
	public void stripsColourTagsAndSmartApostrophe()
	{
		assertEquals("lumbridge",
				MicrobotPlugin.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"<col=ffffff>You haven\u2019t unlocked access to the Lumbridge area.</col>"));
	}

	@Test
	public void noMatchWrongCopy()
	{
		assertNull(MicrobotPlugin.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
				"Welcome to Lumbridge area"));
	}

	@Test
	public void gateMatchesExpectedCopy()
	{
		assertEquals(true, MicrobotPlugin.isLeaguesLockedAccessMessage("You haven't unlocked access to the Lumbridge area."));
	}

	@Test
	public void gateMatchesGliderCopy()
	{
		assertEquals(true, MicrobotPlugin.isLeaguesLockedAccessMessage(
				"You cannot take a glider to that destination as you don't have access to the Kharidian Desert area."));
	}

	@Test
	public void gateRejectsWrongOrder()
	{
		assertEquals(false, MicrobotPlugin.isLeaguesLockedAccessMessage("Area unlocked access to the Lumbridge."));
	}

	@Test
	public void clipPreservesMatchWhenPhraseInsideCap()
	{
		int prefixLen = CAP - PHRASE.length();
		StringBuilder prefix = new StringBuilder(prefixLen);
		for (int i = 0; i < prefixLen; i++)
		{
			prefix.append('x');
		}
		String raw = prefix + PHRASE;
		assertEquals(CAP, raw.length());
		assertEquals("lumbridge", MicrobotPlugin.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(raw));
	}

	@Test
	public void clipDropsMatchWhenPhraseOnlyAfterCutoff()
	{
		StringBuilder prefix = new StringBuilder(CAP);
		for (int i = 0; i < CAP; i++)
		{
			prefix.append('y');
		}
		String raw = prefix + PHRASE;
		assertNull(MicrobotPlugin.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(raw));
	}
}
