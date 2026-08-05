package shortestpath.pathfinder;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import shortestpath.PrimitiveIntHashMap;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;

public final class TransportAvailability
{
	public static final Transport[] EMPTY_TRANSPORTS = new Transport[0];

	// Transports grouped by origin tile, stored as flat arrays. The per-origin HashSet/HashMap
	// wrappers used while building are not retained (issue #491).
	//
	// transportsPacked is the pathfinding view: a transport is reachable from its literal origin
	// tile, and POH transports are additionally reachable from the canonical landing tile.
	// displayTransports is the coarse display view used by overlays and getTransports(): POH origin
	// tiles are collapsed into the landing tile only. The two maps share their Transport[] arrays
	// for every non-POH origin.
	private final PrimitiveIntHashMap<Transport[]> transportsPacked;
	private final PrimitiveIntHashMap<Transport[]> displayTransports;
	private final Transport[] usableTeleports;

	TransportAvailability(
		PrimitiveIntHashMap<Transport[]> transportsPacked,
		PrimitiveIntHashMap<Transport[]> displayTransports,
		Transport[] usableTeleports)
	{
		this.transportsPacked = transportsPacked;
		this.displayTransports = displayTransports;
		this.usableTeleports = usableTeleports;
	}

	public PrimitiveIntHashMap<Transport[]> getTransportsPacked()
	{
		return transportsPacked;
	}

	public PrimitiveIntHashMap<Transport[]> getDisplayTransports()
	{
		return displayTransports;
	}

	public Transport[] getUsableTeleports()
	{
		return usableTeleports;
	}

	/**
	 * The transports that start at the given origin tile in the display view, or an empty array.
	 */
	public Transport[] getTransportsAt(int origin)
	{
		return displayTransports.getOrDefault(origin, EMPTY_TRANSPORTS);
	}

	/*
	 * Build a TransportAvailability by incrementally adding available transports.
	 */
	public static final class Builder
	{
		// Temporary accumulation; converted to flat arrays in build() and not retained afterwards.
		private final Map<Integer, Set<Transport>> transportsByOrigin;
		private final Set<Transport> usableTeleports;
		private final Set<Integer> pohOrigins = new HashSet<>();

		public Builder(int expectedTransportCount)
		{
			this.transportsByOrigin = new HashMap<>(expectedTransportCount / 2);
			this.usableTeleports = new HashSet<>(expectedTransportCount / 20);
		}

		public void add(Transport transport)
		{
			if (transport.getOrigin() == WorldPointUtil.UNDEFINED)
			{
				usableTeleports.add(transport);
				return;
			}

			transportsByOrigin.computeIfAbsent(transport.getOrigin(), ignored -> new HashSet<>()).add(transport);
		}

		void remapPohTransports()
		{
			int pohLanding = WorldPointUtil.packWorldPoint(1923, 5709, 0);
			Set<Transport> pohTransports = new HashSet<>();

			for (Map.Entry<Integer, Set<Transport>> entry : transportsByOrigin.entrySet())
			{
				int origin = entry.getKey();
				int originX = WorldPointUtil.unpackWorldX(origin);
				int originY = WorldPointUtil.unpackWorldY(origin);
				if (shortestpath.ShortestPathPlugin.isInsidePoh(originX, originY))
				{
					pohTransports.addAll(entry.getValue());
					// Kept in the pathfinding view, collapsed out of the display view.
					pohOrigins.add(origin);
				}
			}

			if (!pohTransports.isEmpty())
			{
				transportsByOrigin.computeIfAbsent(pohLanding, ignored -> new HashSet<>()).addAll(pohTransports);
			}
		}

		public TransportAvailability build()
		{
			int expected = Math.max(1, transportsByOrigin.size());
			PrimitiveIntHashMap<Transport[]> packed = new PrimitiveIntHashMap<>(expected);
			PrimitiveIntHashMap<Transport[]> display = new PrimitiveIntHashMap<>(expected);
			for (Map.Entry<Integer, Set<Transport>> entry : transportsByOrigin.entrySet())
			{
				int origin = entry.getKey();
				Transport[] transports = entry.getValue().toArray(EMPTY_TRANSPORTS);
				packed.put(origin, transports);
				if (!pohOrigins.contains(origin))
				{
					display.put(origin, transports);
				}
			}
			return new TransportAvailability(packed, display, usableTeleports.toArray(EMPTY_TRANSPORTS));
		}
	}
}
