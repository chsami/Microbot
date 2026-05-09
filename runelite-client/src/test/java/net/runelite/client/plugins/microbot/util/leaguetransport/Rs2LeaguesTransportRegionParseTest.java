package net.runelite.client.plugins.microbot.util.leaguetransport;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

/**
 * Table-style coverage for {@link Rs2LeaguesTransport#parseRegionName(String)} and locked-region capture
 * (audit Tier 5.4).
 */
public class Rs2LeaguesTransportRegionParseTest
{
	@Test
	public void parseRegionName_mapsEachEnumDisplayName()
	{
		for (LeaguesRegion r : LeaguesRegion.values())
		{
			assertEquals(r, Rs2LeaguesTransport.parseRegionName(r.getDisplayName()));
		}
	}

	@Test
	public void parseRegionName_trimsAndLowercases()
	{
		assertEquals(LeaguesRegion.MISTHALIN, Rs2LeaguesTransport.parseRegionName("  MISTHALIN  "));
		assertEquals(LeaguesRegion.KEBOS_AND_KOUREND, Rs2LeaguesTransport.parseRegionName("great kourend"));
	}

	@Test
	public void parseRegionName_unicodeApostropheNormalized()
	{
		assertEquals(LeaguesRegion.MISTHALIN, Rs2LeaguesTransport.parseRegionName("Misthalin"));
		assertEquals(LeaguesRegion.FREMENNIK, Rs2LeaguesTransport.parseRegionName("Fremennik Province"));
	}

	@Test
	public void parseRegionName_unknownReturnsNull()
	{
		assertNull(Rs2LeaguesTransport.parseRegionName("Aethermoor"));
		assertNull(Rs2LeaguesTransport.parseRegionName(""));
		assertNull(Rs2LeaguesTransport.parseRegionName("   "));
	}

	@Test
	public void captureLockedRegionFromChatRaw_extractsRegionPhrase()
	{
		Optional<String> cap = Rs2LeaguesTransport.captureLockedRegionFromChatRaw(
				"You haven't unlocked access to the Misthalin area yet.");
		assertEquals(Optional.of("misthalin"), cap);
	}

	@Test
	public void captureLockedRegionFromChatRaw_longRegionNameMatches()
	{
		Optional<String> cap = Rs2LeaguesTransport.captureLockedRegionFromChatRaw(
				"You haven't unlocked access to the Great Kourend and Kebos Lowlands area.");
		assertEquals(Optional.of("great kourend and kebos lowlands"), cap);
	}
}
