package net.runelite.client.plugins.microbot.util.leaguetransport;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Locked-region gametext: regex capture must yield phrases that {@link LeaguesTransportRegions#parseRegionNameNormalized}
 * can map to {@link LeaguesRegion} — tests use region-tier wording (Misthalin, Kandarin, …), not city labels.
 */
public class LeaguesTransportChatTest
{
	private static final int CAP = 4096;
	private static final String PHRASE = "You haven't unlocked access to the Misthalin area.";

	@Test
	public void capturesRegionPlain()
	{
		assertEquals("misthalin",
				LeaguesTransportChat.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"You haven't unlocked access to the Misthalin area"));
	}

	@Test
	public void capturesRegionWithTrailingPeriod()
	{
		assertEquals("misthalin",
				LeaguesTransportChat.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"You haven't unlocked access to the Misthalin area."));
	}

	@Test
	public void capturesRegionWithTrailingComma()
	{
		assertEquals("kandarin",
				LeaguesTransportChat.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"You haven't unlocked access to the Kandarin area,"));
	}

	@Test
	public void capturesRegionWithTrailingParen()
	{
		assertEquals("kourend",
				LeaguesTransportChat.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"You haven't unlocked access to the Kourend area)"));
	}

	@Test
	public void capturesRegionWithParentheticalAfterArea()
	{
		assertEquals("asgarnia",
				LeaguesTransportChat.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"You haven't unlocked access to the Asgarnia area (retry)"));
	}

	@Test
	public void capturesRegionWithUnicodeEllipsisTail()
	{
		assertEquals("misthalin",
				LeaguesTransportChat.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"You haven't unlocked access to the Misthalin area\u2026"));
	}

	@Test
	public void greedyLastAreaWinsWhenInnerAreaInName()
	{
		assertEquals("southern desert",
				LeaguesTransportChat.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"You haven't unlocked access to the southern desert area."));
	}

	@Test
	public void stripsColourTagsAndSmartApostrophe()
	{
		assertEquals("misthalin",
				LeaguesTransportChat.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"<col=ffffff>You haven\u2019t unlocked access to the Misthalin area.</col>"));
	}

	@Test
	public void noMatchWrongCopy()
	{
		assertNull(LeaguesTransportChat.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
				"Welcome to Misthalin area"));
	}

	@Test
	public void gateMatchesExpectedCopy()
	{
		assertEquals(true, LeaguesTransportChat.isLeaguesLockedAccessMessage(
				"You haven't unlocked access to the Misthalin area."));
	}

	@Test
	public void gateMatchesGliderCopy()
	{
		assertEquals(true, LeaguesTransportChat.isLeaguesLockedAccessMessage(
				"You cannot take a glider to that destination as you don't have access to the Kharidian Desert area."));
	}

	@Test
	public void gateMatchesBlockedTeleportPrefixCopy()
	{
		assertEquals(true, LeaguesTransportChat.isLeaguesLockedAccessMessage(
				"Your teleport is blocked as you haven't unlocked access to the Asgarnia area."));
	}

	@Test
	public void capturesRegionBlockedTeleportPrefixCopy()
	{
		assertEquals("asgarnia",
				LeaguesTransportChat.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(
						"Your teleport is blocked as you haven't unlocked access to the Asgarnia area."));
	}

	@Test
	public void gateRejectsWrongOrder()
	{
		assertEquals(false, LeaguesTransportChat.isLeaguesLockedAccessMessage(
				"Area unlocked access to the Misthalin."));
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
		assertEquals("misthalin", LeaguesTransportChat.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(raw));
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
		assertNull(LeaguesTransportChat.leaguesLockedRegionCapturedRegionAfterNormalizeForTests(raw));
	}
}
