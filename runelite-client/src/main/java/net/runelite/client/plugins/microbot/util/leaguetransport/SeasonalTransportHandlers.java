package net.runelite.client.plugins.microbot.util.leaguetransport;

import net.runelite.client.plugins.microbot.shortestpath.Transport;

import java.util.List;

/**
 * Built-in {@link SeasonalTransportHandler} instances and default ordering (Leagues Area, then Map of Alacrity).
 */
public final class SeasonalTransportHandlers
{
	private SeasonalTransportHandlers()
	{
	}

	public static final SeasonalTransportHandler LEAGUES_AREA = new SeasonalTransportHandler()
	{
		@Override
		public boolean matches(Transport transport)
		{
			return Rs2LeaguesTransport.matchesLeaguesAreaTransportPrefix(transport);
		}

		@Override
		public boolean tryUse(Transport transport)
		{
			return Rs2LeaguesTransport.tryHandleLeaguesAreaTransport(transport);
		}
	};

	public static final SeasonalTransportHandler MAP_OF_ALACRITY = new SeasonalTransportHandler()
	{
		@Override
		public boolean matches(Transport transport)
		{
			return Rs2MapOfAlacrityTransport.isMapOfAlacrityTransport(transport);
		}

		@Override
		public boolean tryUse(Transport transport)
		{
			return Rs2MapOfAlacrityTransport.tryUse(transport);
		}
	};

	public static List<SeasonalTransportHandler> defaultHandlerList()
	{
		return List.of(LEAGUES_AREA, MAP_OF_ALACRITY);
	}
}
