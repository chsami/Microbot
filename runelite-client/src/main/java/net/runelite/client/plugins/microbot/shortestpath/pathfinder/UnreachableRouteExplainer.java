package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

@Slf4j
public final class UnreachableRouteExplainer
{
	private static final long SEARCH_BUDGET_MS = 5_000L;
	private static final int MAX_LISTED_BLOCKERS = 2;

	private UnreachableRouteExplainer()
	{
	}

	public static String explain(PathfinderConfig live, WorldPoint start, WorldPoint target, WorldPoint closest)
	{
		return explain(live, start, target, closest, SEARCH_BUDGET_MS);
	}

	static String explain(PathfinderConfig live, WorldPoint start, WorldPoint target, WorldPoint closest, long budgetMs)
	{
		String prefix = "Can't reach " + format(target);
		if (live != null && start != null && target != null)
		{
			try
			{
				List<Transport> blockers = fewestBlockersRoute(live, start, target, budgetMs);
				if (blockers != null && !blockers.isEmpty())
				{
					return prefix + ": " + describeBlockers(live, blockers) + ".";
				}
			}
			catch (RuntimeException e)
			{
				log.debug("[Walker] unreachable explanation failed: {}", e.toString());
			}
		}
		String nearest = closest != null && !closest.equals(start)
			? " The closest the walker can get is " + format(closest) + "."
			: "";
		return prefix + ": the walker found no route it can use from where you are."
			+ " Check your Web Walker transport settings or start from somewhere else." + nearest;
	}

	static List<Transport> fewestBlockersRoute(PathfinderConfig live, WorldPoint start, WorldPoint target, long budgetMs)
	{
		PathfinderConfig relaxed = live.copyIgnoringTransportRequirements();
		CollisionMap map = relaxed.getMap();
		map.beginSearch();
		VisitedTiles expanded = new VisitedTiles(map);
		VisitedTiles queued = new VisitedTiles(map);
		int targetPacked = WorldPointUtil.packWorldPoint(target);
		Set<Integer> targets = Collections.singleton(targetPacked);
		PriorityQueue<Step> queue = new PriorityQueue<>(4096, STEP_ORDER);
		int startPacked = WorldPointUtil.packWorldPoint(start);
		queued.set(startPacked);
		queue.add(new Step(new Node(startPacked, null), 0, null, null));
		long deadline = System.currentTimeMillis() + budgetMs;
		int polled = 0;
		while (!queue.isEmpty())
		{
			if ((++polled & 1023) == 0 && System.currentTimeMillis() > deadline)
			{
				return null;
			}
			Step step = queue.poll();
			if (expanded.get(step.node.packedPosition))
			{
				continue;
			}
			expanded.set(step.node.packedPosition);
			if (step.node.packedPosition == targetPacked)
			{
				List<Transport> blockers = new ArrayList<>();
				for (Step s = step; s != null; s = s.previous)
				{
					if (s.transport != null && isBlocked(live, s.transport))
					{
						blockers.add(0, s.transport);
					}
				}
				return blockers;
			}
			for (Node next : map.getNeighbors(step.node, expanded, relaxed, targets))
			{
				Transport transport = next instanceof TransportNode ? ((TransportNode) next).getTransport() : null;
				boolean blocked = transport != null && isBlocked(live, transport);
				if (!blocked)
				{
					if (queued.get(next.packedPosition))
					{
						continue;
					}
					queued.set(next.packedPosition);
				}
				queue.add(new Step(next, step.blockers + (blocked ? 1 : 0), step, transport));
			}
		}
		return null;
	}

	static String describeBlockers(PathfinderConfig live, List<Transport> blockers)
	{
		List<String> parts = new ArrayList<>();
		for (Transport transport : blockers.subList(0, Math.min(MAX_LISTED_BLOCKERS, blockers.size())))
		{
			parts.add(label(transport) + ", but " + reason(live, transport));
		}
		String text = "the route needs " + String.join("; and ", parts);
		int unlisted = blockers.size() - parts.size();
		return unlisted > 0 ? text + " (and " + unlisted + " more)" : text;
	}

	private static String reason(PathfinderConfig live, Transport transport)
	{
		if (isUsable(live, transport))
		{
			return "the walker could not get through it earlier and will skip it until you restart the client";
		}
		String reason = live.describeTransportRejection(transport);
		return reason == null ? "it is not available to you right now" : reason;
	}

	private static boolean isBlocked(PathfinderConfig live, Transport transport)
	{
		return !isUsable(live, transport) || isLearnedBlocked(live, transport);
	}

	private static final class Step
	{
		private final Node node;
		private final int blockers;
		private final Step previous;
		private final Transport transport;

		private Step(Node node, int blockers, Step previous, Transport transport)
		{
			this.node = node;
			this.blockers = blockers;
			this.previous = previous;
			this.transport = transport;
		}
	}

	private static final Comparator<Step> STEP_ORDER = Comparator
		.comparingInt((Step step) -> step.blockers)
		.thenComparingInt(step -> step.node.cost);

	private static boolean isUsable(PathfinderConfig live, Transport transport)
	{
		if (transport.getOrigin() == null)
		{
			return live.getUsableTeleportsSnapshot().contains(transport);
		}
		Set<Transport> usable = live.getTransports().getOrDefault(transport.getOrigin(), Collections.emptySet());
		return usable.contains(transport);
	}

	private static boolean isLearnedBlocked(PathfinderConfig live, Transport transport)
	{
		return transport.getOrigin() != null && transport.getDestination() != null
			&& live.isLearnedBlockedTransport(
				WorldPointUtil.packWorldPoint(transport.getOrigin()),
				WorldPointUtil.packWorldPoint(transport.getDestination()));
	}

	static String label(Transport transport)
	{
		String name = blankToNull(transport.getName());
		String action = blankToNull(transport.getAction());
		String destination = blankToNull(transport.getDisplayInfo());
		TransportType type = transport.getType();
		if (type == null || type == TransportType.TRANSPORT)
		{
			String what = action != null && name != null ? action + " " + name : name != null ? name : "a passage";
			return "\"" + what + "\"" + (transport.getOrigin() != null ? " at " + format(transport.getOrigin()) : "");
		}
		StringBuilder label = new StringBuilder("the ").append(PathfinderConfig.humanize(type).toLowerCase());
		if (destination != null)
		{
			label.append(" to ").append(destination);
		}
		if (name != null)
		{
			label.append(" (").append(name).append(')');
		}
		return label.toString();
	}

	private static String blankToNull(String value)
	{
		return value == null || value.trim().isEmpty() ? null : value.trim();
	}

	private static String format(WorldPoint point)
	{
		if (point == null)
		{
			return "the destination";
		}
		return point.getX() + ", " + point.getY() + (point.getPlane() != 0 ? ", " + point.getPlane() : "");
	}
}
