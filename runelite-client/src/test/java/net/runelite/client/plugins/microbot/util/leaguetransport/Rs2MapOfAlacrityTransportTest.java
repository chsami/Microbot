package net.runelite.client.plugins.microbot.util.leaguetransport;

import org.junit.Test;

import java.util.Optional;

import static org.junit.Assert.*;

public class Rs2MapOfAlacrityTransportTest
{
	@Test
	public void parsesSpacedDashFormat()
	{
		Optional<Rs2MapOfAlacrityTransport.MoaParsedRow> opt = Rs2MapOfAlacrityTransport.parseMoaDisplayInfo("Map of Alacrity: Kourend - Castle");
		assertTrue(opt.isPresent());
		assertEquals("Kourend", opt.get().getRegion());
		assertEquals("Castle", opt.get().getShortcutName());
	}

	@Test
	public void parsesHyphenFallbackFormat()
	{
		Optional<Rs2MapOfAlacrityTransport.MoaParsedRow> opt = Rs2MapOfAlacrityTransport.parseMoaDisplayInfo("Map of Alacrity: Kourend-Castle");
		assertTrue(opt.isPresent());
		assertEquals("Kourend", opt.get().getRegion());
		assertEquals("Castle", opt.get().getShortcutName());
	}

	@Test
	public void parsesRegionContainingSpacedDash()
	{
		Optional<Rs2MapOfAlacrityTransport.MoaParsedRow> opt = Rs2MapOfAlacrityTransport.parseMoaDisplayInfo("Map of Alacrity: Kourend - Kingdom - Castle");
		assertTrue(opt.isPresent());
		assertEquals("Kourend - Kingdom", opt.get().getRegion());
		assertEquals("Castle", opt.get().getShortcutName());
	}

	@Test
	public void rejectsColonBeforeTitleEnd()
	{
		Optional<Rs2MapOfAlacrityTransport.MoaParsedRow> opt = Rs2MapOfAlacrityTransport.parseMoaDisplayInfo("Map: of Alacrity: Kourend - Castle");
		assertFalse(opt.isPresent());
	}
}

