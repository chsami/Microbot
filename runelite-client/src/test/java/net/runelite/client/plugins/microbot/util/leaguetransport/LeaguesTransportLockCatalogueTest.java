package net.runelite.client.plugins.microbot.util.leaguetransport;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LeaguesTransportLockCatalogueTest
{
	@Test
	public void normalizeMethodStripsHandlerSuffix()
	{
		String raw = "TELEPORTATION_SPELL:Lumbridge Teleport|handler=Foo";
		assertEquals("TELEPORTATION_SPELL:Lumbridge Teleport", LeaguesTransportLockCatalogue.normalizeLockCatalogueMethod(raw));
	}

	@Test
	public void buildDedupeKeyJoinsPackedDestAndMethod()
	{
		assertEquals("12345|SPELL:Foo", LeaguesTransportLockCatalogue.buildDedupeKey(12345, "SPELL:Foo"));
	}
}
