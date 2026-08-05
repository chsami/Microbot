package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** One materialized edge in a chosen local pathfinder route. */
public final class PathEdge
{
	private final WorldPoint from;
	private final WorldPoint to;
	private final Transport transport;

	PathEdge(WorldPoint from, WorldPoint to, Transport transport)
	{
		this.from = from;
		this.to = to;
		this.transport = transport;
	}

	static List<PathEdge> fromForwardChain(Node lastNode)
	{
		if (lastNode == null)
		{
			return Collections.emptyList();
		}
		List<Node> nodes = lastNode.getNodePath();
		List<PathEdge> edges = new ArrayList<>(Math.max(0, nodes.size() - 1));
		for (int i = 1; i < nodes.size(); i++)
		{
			Node from = nodes.get(i - 1);
			Node to = nodes.get(i);
			Transport transport = to instanceof TransportNode
				? ((TransportNode) to).getTransport()
				: null;
			edges.add(new PathEdge(
				WorldPointUtil.unpackWorldPoint(from.packedPosition),
				WorldPointUtil.unpackWorldPoint(to.packedPosition),
				transport));
		}
		return Collections.unmodifiableList(edges);
	}

	/**
	 * Build the temporary local compatibility view for a completed engine-neutral route.
	 * The transport list is aligned with path edges and may contain {@code null} walking entries.
	 */
	static List<PathEdge> fromMaterializedRoute(
		List<WorldPoint> path, List<Transport> transportsByStep)
	{
		if (path == null || transportsByStep == null)
		{
			throw new IllegalArgumentException("materialized path and transports are required");
		}
		int expected = Math.max(0, path.size() - 1);
		if (transportsByStep.size() != expected)
		{
			throw new IllegalArgumentException(
				"materialized transport count must match path edge count");
		}
		List<PathEdge> edges = new ArrayList<>(expected);
		for (int i = 0; i < expected; i++)
		{
			WorldPoint from = path.get(i);
			WorldPoint to = path.get(i + 1);
			if (from == null || to == null)
			{
				throw new IllegalArgumentException("materialized path points must be non-null");
			}
			Transport transport = transportsByStep.get(i);
			if (transport != null && !to.equals(transport.getDestination()))
			{
				throw new IllegalArgumentException(
					"materialized transport destination must match its route step");
			}
			edges.add(new PathEdge(from, to, transport));
		}
		return Collections.unmodifiableList(edges);
	}

	/**
	 * Combine a normal start-to-meeting chain with the reverse-search meeting-to-goal chain.
	 * Reverse transport metadata belongs to the {@code from} node, unlike a forward chain where it
	 * belongs to the {@code to} node.
	 */
	static List<PathEdge> fromBidirectionalChains(Node forwardAtMeet, Node backwardAtMeet)
	{
		List<PathEdge> edges = new ArrayList<>(fromForwardChain(forwardAtMeet));
		for (Node from = backwardAtMeet; from != null && from.previous != null; from = from.previous)
		{
			Node to = from.previous;
			Transport transport = from instanceof TransportNode
				? ((TransportNode) from).getTransport()
				: null;
			edges.add(new PathEdge(
				WorldPointUtil.unpackWorldPoint(from.packedPosition),
				WorldPointUtil.unpackWorldPoint(to.packedPosition),
				transport));
		}
		return Collections.unmodifiableList(edges);
	}

	public WorldPoint getFrom()
	{
		return from;
	}

	public WorldPoint getTo()
	{
		return to;
	}

	public Transport getTransport()
	{
		return transport;
	}

	public boolean isTransport()
	{
		return transport != null;
	}
}
