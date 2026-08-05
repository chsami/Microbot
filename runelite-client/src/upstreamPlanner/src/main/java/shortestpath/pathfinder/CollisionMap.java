package shortestpath.pathfinder;

import shortestpath.PrimitiveIntList;
import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;

public class CollisionMap
{
	// Enum.values() makes copies every time which hurts performance in the hotpath
	private static final OrdinalDirection[] ORDINAL_VALUES = OrdinalDirection.values();

	private final SplitFlagMap collisionData;
	private final EdgeOverride edgeOverride;
	// This is only safe if pathfinding is single-threaded. Holds the ids of the neighbour nodes
	// appended to the NodeGraph during the most recent getNeighbors call.
	private final PrimitiveIntList neighbors = new PrimitiveIntList(16);
	private final boolean[] traversable = new boolean[8];

	public CollisionMap(SplitFlagMap collisionData)
	{
		this(collisionData, null);
	}

	public CollisionMap(SplitFlagMap collisionData, EdgeOverride edgeOverride)
	{
		this.collisionData = collisionData;
		this.edgeOverride = edgeOverride;
	}

	private static int packedPointFromOrdinal(int startPacked, OrdinalDirection direction)
	{
		final int x = WorldPointUtil.unpackWorldX(startPacked);
		final int y = WorldPointUtil.unpackWorldY(startPacked);
		final int plane = WorldPointUtil.unpackWorldPlane(startPacked);
		return WorldPointUtil.packWorldPoint(x + direction.x, y + direction.y, plane);
	}

	public byte getRegionPlaneCounts(int regionIndex)
	{
		return collisionData.getRegionPlaneCounts(regionIndex);
	}

	private boolean get(int x, int y, int z, int flag)
	{
		if (edgeOverride != null)
		{
			Boolean value = edgeOverride.edge(x, y, z, flag);
			if (value != null)
			{
				return value;
			}
		}
		return collisionData.get(x, y, z, flag);
	}

	public boolean n(int x, int y, int z)
	{
		return get(x, y, z, 0);
	}

	public boolean s(int x, int y, int z)
	{
		return n(x, y - 1, z);
	}

	public boolean e(int x, int y, int z)
	{
		return get(x, y, z, 1);
	}

	public boolean w(int x, int y, int z)
	{
		return e(x - 1, y, z);
	}

	private boolean ne(int x, int y, int z)
	{
		return n(x, y, z) && e(x, y + 1, z) && e(x, y, z) && n(x + 1, y, z);
	}

	private boolean nw(int x, int y, int z)
	{
		return n(x, y, z) && w(x, y + 1, z) && w(x, y, z) && n(x - 1, y, z);
	}

	private boolean se(int x, int y, int z)
	{
		return s(x, y, z) && e(x, y - 1, z) && e(x, y, z) && s(x + 1, y, z);
	}

	private boolean sw(int x, int y, int z)
	{
		return s(x, y, z) && w(x, y - 1, z) && w(x, y, z) && s(x - 1, y, z);
	}

	public boolean isBlocked(int x, int y, int z)
	{
		return !n(x, y, z) && !s(x, y, z) && !e(x, y, z) && !w(x, y, z);
	}

	public PrimitiveIntList getNeighbors(int node, VisitedTiles visited, PathfinderConfig config, int wildernessLevel, boolean targetInWilderness, NodeGraph graph)
	{
		if (graph.isTile(node))
		{
			return getTileNeighbors(node, visited, config, wildernessLevel, graph);
		}
		else
		{
			return getAbstractNodeNeighbors(node, visited, config, targetInWilderness, graph);
		}
	}

	// Get neighbours for a walkable tile:
	//      * Neighbouring tiles we can walk to
	//      * A transition into banked state, if the current tile is a bank.
	//      * Transition into abstract global teleport nodes, if we haven't tried that yet.
	private PrimitiveIntList getTileNeighbors(int node, VisitedTiles visited, PathfinderConfig config, int wildernessLevel, NodeGraph graph)
	{
		final int packedPosition = graph.packedPosition(node);
		final int x = WorldPointUtil.unpackWorldX(packedPosition);
		final int y = WorldPointUtil.unpackWorldY(packedPosition);
		final int z = WorldPointUtil.unpackWorldPlane(packedPosition);

		neighbors.clear();

		// Either we have already visited a bank, if the current tile is a bank switch into the bankVisited state for the
		// rest of the path.
		boolean pathBankVisited = graph.bankVisited(node)
			|| (config.isBankPathEnabled() && config.bankAccessible(packedPosition));

		// Firstly check if there are any transports or teleports which are applicable from the current tile.
		Transport[] transports = config.getTransportsPacked(pathBankVisited).getOrDefault(packedPosition, TransportAvailability.EMPTY_TRANSPORTS);
		// If this tile was itself reached via a delayed-visit teleport (e.g. QUETZAL_WHISTLE), propagate its
		// differential cost to any competing delayed-visit transports emitted from here. This prevents the
		// pathfinder from choosing a chain (e.g. whistle → landing site A → fly to B) over a direct teleport
		// to B, because the chain inherits the teleport's penalty and is therefore always more expensive.
		int inheritedDifferential = (graph.isTransport(node) && graph.isDelayedVisit(node))
			? graph.differentialCost(node)
			: 0;
		for (Transport transport : transports)
		{
			boolean delayedVisit = transport.getType().sharesDestinationsWith() != null;
			// Do not consider a transport if we have already visited its target tile.
			// For transports that share destinations with a teleport, skip this check
			// so both can compete in the priority queue (delayed visit).
			if (!delayedVisit && visited.get(transport.getDestination(), pathBankVisited))
			{
				continue;
			}
			// Inherit the parent teleport's differential as a real cost on chained shared-destination transports,
			// so that chaining (e.g. fly to landing site A then use station to B) is always more expensive than
			// a direct teleport to B.
			int chainPenalty = (delayedVisit && inheritedDifferential > 0) ? inheritedDifferential : 0;
			// NB: Do not need to check for wilderness level for transports, since transports have specific origin tile.
			neighbors.add(graph.createTransport(
				transport.getDestination(),
				node,
				transport.getDuration(),
				config.getAdditionalTransportCost(transport) + chainPenalty,
				pathBankVisited,
				delayedVisit,
				delayedVisit ? config.getDifferentialCost(transport) : 0,
				transport));
		}

		// Global teleports are only considered from an abstract node, so each
		// wilderness/bank state expands them once.
		AbstractNodeKind abstractKind = AbstractNodeKind.fromWildernessLevel(wildernessLevel);
		if (!visited.getAbstract(abstractKind, pathBankVisited))
		{
			neighbors.add(graph.createAbstract(abstractKind, node, pathBankVisited));
		}

		// Then add tiles which we can walk to, which go into the FIFO boundary queue.
		if (isBlocked(x, y, z))
		{
			boolean westBlocked = isBlocked(x - 1, y, z);
			boolean eastBlocked = isBlocked(x + 1, y, z);
			boolean southBlocked = isBlocked(x, y - 1, z);
			boolean northBlocked = isBlocked(x, y + 1, z);
			boolean southWestBlocked = isBlocked(x - 1, y - 1, z);
			boolean southEastBlocked = isBlocked(x + 1, y - 1, z);
			boolean northWestBlocked = isBlocked(x - 1, y + 1, z);
			boolean northEastBlocked = isBlocked(x + 1, y + 1, z);
			traversable[0] = !westBlocked;
			traversable[1] = !eastBlocked;
			traversable[2] = !southBlocked;
			traversable[3] = !northBlocked;
			traversable[4] = !southWestBlocked && !westBlocked && !southBlocked;
			traversable[5] = !southEastBlocked && !eastBlocked && !southBlocked;
			traversable[6] = !northWestBlocked && !westBlocked && !northBlocked;
			traversable[7] = !northEastBlocked && !eastBlocked && !northBlocked;
		}
		else
		{
			traversable[0] = w(x, y, z);
			traversable[1] = e(x, y, z);
			traversable[2] = s(x, y, z);
			traversable[3] = n(x, y, z);
			traversable[4] = sw(x, y, z);
			traversable[5] = se(x, y, z);
			traversable[6] = nw(x, y, z);
			traversable[7] = ne(x, y, z);
		}

		for (int i = 0; i < traversable.length; i++)
		{
			OrdinalDirection d = ORDINAL_VALUES[i];
			int neighborPacked = packedPointFromOrdinal(packedPosition, d);
			if (visited.get(neighborPacked, pathBankVisited))
			{
				continue;
			}

			if (traversable[i])
			{
				neighbors.add(graph.createTile(neighborPacked, node, pathBankVisited,
					config.getAdditionalWalkingCost(neighborPacked)));
			}
			else if (Math.abs(d.x + d.y) == 1 && isBlocked(x + d.x, y + d.y, z))
			{
				// The transport starts from a blocked adjacent tile, e.g. fairy ring
				// Only checks non-teleport transports (includes portals and levers, but not
				// items and spells)
				Transport[] neighborTransports = config.getTransportsPacked(pathBankVisited).getOrDefault(neighborPacked,
					TransportAvailability.EMPTY_TRANSPORTS);
				for (Transport transport : neighborTransports)
				{
					if (transport.getOrigin() == Transport.UNDEFINED_ORIGIN
						|| !(transport.isUsableAtWildernessLevel(wildernessLevel))
						|| visited.get(transport.getOrigin(), pathBankVisited))
					{
						continue;
					}
					neighbors.add(graph.createTile(transport.getOrigin(), node, pathBankVisited,
						config.getAdditionalWalkingCost(transport.getOrigin())));
				}
			}
		}

		return neighbors;
	}

	// The only abstract nodes are currently for global teleports
	private PrimitiveIntList getAbstractNodeNeighbors(int node, VisitedTiles visited, PathfinderConfig config,
		boolean targetInWilderness, NodeGraph graph)
	{
		neighbors.clear();
		int sourceTile = graph.getClosestTilePosition(node);
		boolean bankVisited = graph.bankVisited(node);
		int maxWildernessLevel = graph.abstractKind(node).maxWildernessLevel();
		for (Transport transport : config.getUsableTeleports(bankVisited))
		{
			boolean delayedVisit = transport.getType().sharesDestinationsWith() != null;
			if (!delayedVisit && visited.get(transport.getDestination(), bankVisited))
			{
				continue;
			}
			if (!transport.isUsableAtWildernessLevel(maxWildernessLevel))
			{
				continue;
			}
			if (config.avoidWilderness(sourceTile, transport.getDestination(), targetInWilderness))
			{
				continue;
			}
			// The differential cost is only used for priority-queue ordering (compareCost), not
			// propagated as real cost, so applying it unconditionally is safe: a nearby partner
			// station can still win the dequeue race, and a far-away whistle still resolves as the
			// cheapest path because no competitor has a lower real cost to the same destination.
			int differentialCost = delayedVisit ? config.getDifferentialCost(transport) : 0;
			neighbors.add(graph.createTransport(
				transport.getDestination(),
				node,
				transport.getDuration(),
				config.getAdditionalTransportCost(transport),
				bankVisited,
				delayedVisit,
				differentialCost,
				transport));
		}
		return neighbors;
	}
}
