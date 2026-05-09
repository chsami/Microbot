package net.runelite.client.plugins.microbot.util.leaguetransport;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class SeasonalTransportHandlersTest
{
	@Test
	public void defaultHandlerList_orderAndSize()
	{
		assertEquals(2, SeasonalTransportHandlers.defaultHandlerList().size());
		assertSame(SeasonalTransportHandlers.LEAGUES_AREA, SeasonalTransportHandlers.defaultHandlerList().get(0));
		assertSame(SeasonalTransportHandlers.MAP_OF_ALACRITY, SeasonalTransportHandlers.defaultHandlerList().get(1));
	}
}
