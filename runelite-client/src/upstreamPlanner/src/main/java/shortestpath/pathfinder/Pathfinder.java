package shortestpath.pathfinder;

import java.util.List;
import java.util.Set;

import lombok.Getter;
import shortestpath.PrimitiveIntList;
import shortestpath.WorldPointUtil;
import shortestpath.leagues.LeagueModeState;

public class Pathfinder implements Runnable
{
	private final PathfinderStats stats;
	@Getter
	private final int start;
	@Getter
	private final Set<Integer> targets;
	private final PathfinderConfig config;
	private final CollisionMap map;
	private final boolean targetInWilderness;
	private final boolean targetInBlockedRegion;
	private final Runnable completionCallback;
	// Nodes are stored structure-of-arrays style: each node is an int id into the graph, instead of
	// an object per explored tile. This keeps a whole search to a handful of arrays (issue #491).
	private final NodeGraph graph = new NodeGraph(1 << 14);
	// Capacities should be enough to store all nodes without requiring the queue to grow
	// They were found by checking the max queue size
	private final IntDeque boundary = new IntDeque(4096);
	private final IntMinHeap pending = new IntMinHeap(graph, 256);
	private final VisitedTiles visited;
	@Getter
	private volatile boolean done = false;
	private volatile boolean cancelled = false;
	// Read by the render thread during the search to draw the partial path; written by the worker.
	private volatile int bestLastNode = NodeGraph.NO_NODE;
	// The path the render thread builds progressively while the search runs.
	private List<PathStep> pathSteps = List.of();
	private boolean pathNeedsUpdate = false;
	// Built once on the worker thread when the search finishes, then served to the render thread so
	// it never walks the node chain (which is released) after the search is done.
	private volatile List<PathStep> finalPath = null;
	private volatile int closestReachedPoint = WorldPointUtil.UNDEFINED;
	private int bestRemainingDistance = Integer.MAX_VALUE;
	private int bestTravelledDistance = Integer.MAX_VALUE;
	private int bestX = Integer.MAX_VALUE;
	private int bestY = Integer.MAX_VALUE;
	private int reachedTarget = WorldPointUtil.UNDEFINED;
	private PathTerminationReason terminationReason;
	/**
	 * Teleportation transports are updated when this changes.
	 * Can be either:
	 * 0 = all teleports can be used (e.g. Chronicle)
	 * 20 = most teleports can be used (e.g. Varrock Teleport)
	 * 30 = some teleports can be used (e.g. Amulet of Glory)
	 * 31 = no teleports can be used
	 */
	private int wildernessLevel;

	public Pathfinder(PathfinderConfig config, int start, Set<Integer> targets, Runnable completionCallback)
	{
		stats = new PathfinderStats();
		this.config = config;
		this.map = config.getMap();
		this.start = start;
		this.targets = targets;
		this.completionCallback = completionCallback;
		visited = new VisitedTiles(map);
		targetInWilderness = WildernessChecker.isInWilderness(targets);
		targetInBlockedRegion = anyInBlockedRegion(config.getLeagueModeState(), targets);
		wildernessLevel = 31;
	}

	private static boolean anyInBlockedRegion(LeagueModeState league, Set<Integer> packed)
	{
		if (!league.isSeasonal() || packed == null || packed.isEmpty())
		{
			return false;
		}
		for (Integer point : packed)
		{
			if (league.isInBlockedRegion(point))
			{
				return true;
			}
		}
		return false;
	}

	public Pathfinder(PathfinderConfig config, int start, Set<Integer> targets)
	{
		this(config, start, targets, null);
	}

	public void cancel()
	{
		cancelled = true;
	}

	public PathfinderStats getStats()
	{
		if (stats.started && stats.ended)
		{
			return stats;
		}

		// Don't give incomplete results
		return null;
	}

	public List<PathStep> getPath()
	{
		int lastNode = bestLastNode; // For thread safety, read bestLastNode once
		if (lastNode == NodeGraph.NO_NODE)
		{
			List<PathStep> finalised = finalPath;
			return finalised != null ? finalised : pathSteps;
		}

		// Once the search is finished the node graph is released, so serve the pre-built snapshot.
		if (done)
		{
			List<PathStep> finalised = finalPath;
			if (finalised != null)
			{
				return finalised;
			}
		}

		if (pathNeedsUpdate)
		{
			List<PathStep> walked = graph.getPathSteps(lastNode);
			// An empty result means the graph was released mid-walk; keep the last good path.
			if (!walked.isEmpty())
			{
				pathSteps = walked;
				pathNeedsUpdate = false;
			}
		}

		return pathSteps;
	}

	public PathfinderResult getResult()
	{
		PathfinderStats currentStats = getStats();
		if (currentStats == null)
		{
			return null;
		}

		List<PathStep> currentPath = getPath();
		boolean reached = reachedTarget != WorldPointUtil.UNDEFINED;
		int target = reached ? reachedTarget : (targets.isEmpty() ? WorldPointUtil.UNDEFINED : targets.iterator().next());
		// getStats() only returns non-null once the search has ended, so the snapshot is set.
		return new PathfinderResult(
			start,
			target,
			reached,
			currentPath,
			closestReachedPoint,
			currentStats.getNodesChecked(),
			currentStats.getTransportsChecked(),
			currentStats.getElapsedTimeNanos(),
			terminationReason
		);
	}

	private void addNeighbors(int node, boolean nodeIsTile, int nodePacked)
	{
		PrimitiveIntList nodes = map.getNeighbors(node, visited, config, wildernessLevel, targetInWilderness, graph);
		final int count = nodes.size();
		for (int i = 0; i < count; i++)
		{
			int neighbor = nodes.get(i);
			// Each graph.xxx(id) re-indexes a backing array, so read each neighbour field once and
			// reuse the loop-invariant node fields passed in (the JIT cached these for free when nodes
			// were objects, but not when they are int ids into structure-of-arrays storage).
			final boolean neighborIsTile = graph.isTile(neighbor);
			if (nodeIsTile && neighborIsTile)
			{
				final int neighborPacked = graph.packedPosition(neighbor);
				if (config.avoidWilderness(nodePacked, neighborPacked, targetInWilderness))
				{
					continue;
				}
				if (config.avoidBlockedRegion(nodePacked, neighborPacked, targetInBlockedRegion))
				{
					continue;
				}
			}

			final boolean neighborIsTransport = graph.isTransport(neighbor);
			// For delayed-visit nodes (shared destinations), don't mark as visited on enqueue.
			// They will be checked and marked when dequeued from pending.
			if (!(neighborIsTransport && graph.isDelayedVisit(neighbor)))
			{
				visited.set(neighbor, graph);
			}
			if (neighborIsTransport)
			{
				pending.add(neighbor);
				++stats.transportsChecked;
			}
			else
			{
				boundary.addLast(neighbor);
				++stats.nodesChecked;
			}
		}
	}

	/**
	 * Pathfinding to an unreachable target is slightly different from normal pathfinding.
	 * Straight-line movement before diagonal movement is no longer prioritized, because the
	 * original target is moved to the closest reachable tile. To avoid having to move the
	 * original target we instead do the following to favour the closest reachable tile:
	 * - 1) Pick the path with the minimum Euclidean distance (no need to use square root though)
	 * - 2) If a tie occurs, pick the path with minimum travelled distance
	 * - 3) If another tie occurs, pick the path with minimum x-coordinate
	 * - 4) If another tie occurs, pick the path with minimum y-coordinate
	 */
	private boolean updateBestPathWhenUnreachable(int node, int packedPosition)
	{
		boolean update = false;

		final int travelledDistance = graph.cost(node);
		for (int target : targets)
		{
			int remainingDistance = WorldPointUtil.distanceBetween(target, packedPosition, WorldPointUtil.EUCLIDEAN_SQUARED_DISTANCE_METRIC);
			int x = WorldPointUtil.unpackWorldX(packedPosition);
			int y = WorldPointUtil.unpackWorldY(packedPosition);
			if ((remainingDistance < bestRemainingDistance) ||
				(remainingDistance == bestRemainingDistance && travelledDistance < bestTravelledDistance) ||
				(remainingDistance == bestRemainingDistance && travelledDistance == bestTravelledDistance && x < bestX) ||
				(remainingDistance == bestRemainingDistance && travelledDistance == bestTravelledDistance && x == bestX && y < bestY))
			{
				bestRemainingDistance = remainingDistance;
				bestTravelledDistance = travelledDistance;
				bestX = x;
				bestY = y;
				bestLastNode = node;
				pathNeedsUpdate = true;
				update = true;
			}
		}

		return update;
	}

	/**
	 * Update wilderness level based on the current node position.
	 */
	private void updateWildernessLevel(int packedPosition)
	{
		if (wildernessLevel > 0)
		{
			// These are overlapping boundaries, so if the node isn't in level 30, it's in 0-29
			// likewise, if the node isn't in level 20, it's in 0-19
			if (wildernessLevel > 30 && !WildernessChecker.isInLevel30Wilderness(packedPosition))
			{
				wildernessLevel = 30;
			}
			if (wildernessLevel > 20 && !WildernessChecker.isInLevel20Wilderness(packedPosition))
			{
				wildernessLevel = 20;
			}
			if (wildernessLevel > 0 && !WildernessChecker.isInWilderness(packedPosition))
			{
				wildernessLevel = 0;
			}
		}
	}

	@Override
	public void run()
	{
		stats.start();
		boundary.addFirst(graph.createStart(start));

		long cutoffDurationMillis = config.getCalculationCutoffMillis();
		long cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;

		while (!cancelled && (!boundary.isEmpty() || !pending.isEmpty()))
		{
			int boundaryHead = boundary.peekFirst();
			int pendingHead = pending.peek();

			int node;
			if (pendingHead != NodeGraph.NO_NODE
				&& (boundaryHead == NodeGraph.NO_NODE || graph.compareCost(pendingHead) < graph.cost(boundaryHead)))
			{
				node = pending.poll();

				// For delayed-visit nodes, check if the destination was already
				// reached by a cheaper path while this node was queued.
				if (graph.isDelayedVisit(node))
				{
					int packed = graph.packedPosition(node);
					boolean bank = graph.bankVisited(node);
					if (visited.get(packed, bank))
					{
						continue;
					}
					visited.set(packed, bank);
				}
			}
			else
			{
				node = boundary.pollFirst();
			}
			if (node == NodeGraph.NO_NODE)
			{
				continue;
			}
			// Read the node's tile-ness and position once; every graph.xxx(id) re-indexes a backing
			// array, and these are used by several of the checks below.
			final boolean nodeIsTile = graph.isTile(node);
			final int nodePacked = nodeIsTile ? graph.packedPosition(node) : WorldPointUtil.UNDEFINED;
			if (nodeIsTile)
			{
				updateWildernessLevel(nodePacked);

				if (targets.contains(nodePacked))
				{
					bestLastNode = node;
					pathNeedsUpdate = true;
					reachedTarget = nodePacked;
					terminationReason = PathTerminationReason.TARGET_REACHED;
					break;
				}

				if (updateBestPathWhenUnreachable(node, nodePacked))
				{
					cutoffTimeMillis = System.currentTimeMillis() + cutoffDurationMillis;
				}
			}

			if (System.currentTimeMillis() > cutoffTimeMillis)
			{
				terminationReason = PathTerminationReason.CUTOFF_REACHED;
				break;
			}

			addNeighbors(node, nodeIsTile, nodePacked);
		}

		if (cancelled)
		{
			terminationReason = PathTerminationReason.CANCELLED;
		}
		else if (terminationReason == null)
		{
			terminationReason = PathTerminationReason.SEARCH_EXHAUSTED;
		}

		// Materialise the final path and closest reached tile on the worker thread, publish them,
		// then release the large node graph. Once done is set the render thread serves finalPath
		// and never touches the released graph, so this is race-free with progressive rendering.
		int lastNode = bestLastNode;
		if (lastNode != NodeGraph.NO_NODE)
		{
			finalPath = graph.getPathSteps(lastNode);
			closestReachedPoint = graph.getClosestTilePosition(lastNode);
		}
		else
		{
			finalPath = pathSteps;
			closestReachedPoint = start;
		}

		done = !cancelled;

		boundary.clear();
		visited.clear();
		pending.clear();
		graph.release();

		stats.end(); // Include cleanup in stats to get the total cost of pathfinding

		if (completionCallback != null)
		{
			completionCallback.run();
		}
	}

	public static class PathfinderStats
	{
		@Getter
		private int nodesChecked = 0, transportsChecked = 0;
		private long startNanos, endNanos;
		private volatile boolean started = false, ended = false;

		public int getTotalNodesChecked()
		{
			return nodesChecked + transportsChecked;
		}

		public long getElapsedTimeNanos()
		{
			return endNanos - startNanos;
		}

		private void start()
		{
			started = true;
			nodesChecked = 0;
			transportsChecked = 0;
			startNanos = System.nanoTime();
		}

		private void end()
		{
			endNanos = System.nanoTime();
			ended = true;
		}
	}
}
