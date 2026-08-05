package shortestpath.pathfinder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import shortestpath.WorldPointUtil;
import shortestpath.transport.Transport;

/**
 * Structure-of-Arrays store for pathfinding nodes.
 * <p>
 * The previous design allocated one {@link Object} per explored tile (a {@code Node} or
 * {@code TransportNode}). Heavy searches explore hundreds of thousands of tiles, so this produced
 * hundreds of thousands of live objects, each carrying a ~16-byte header plus several object
 * references (issue #491). Here every node is instead an {@code int} index into parallel primitive
 * arrays, so a whole search holds only a handful of arrays regardless of how many nodes it visits.
 * <p>
 * The fields packed per node are exactly those of the old {@code Node}/{@code TransportNode}:
 * packed world position, the index of the previous node ({@link #NO_NODE} for the start),
 * accumulated cost, the transport differential cost (queue-ordering only), a set of boolean flags,
 * and the {@link AbstractNodeKind} ordinal for abstract nodes.
 * <p>
 * <strong>Threading.</strong> The search runs on a single worker thread, but the render thread
 * reads the partial path while the search is still running (progressive rendering via
 * {@code Pathfinder.getPath()}). Node data is write-once and is published to the render thread by
 * the single volatile {@code Pathfinder.bestLastNode} handoff rather than by marking these arrays
 * {@code volatile} (which would cripple the hot loop, see the field comment). The chain walks
 * ({@link #getPathSteps} / {@link #getClosestTilePosition}) snapshot the arrays into locals and
 * tolerate an index that is out of bounds or released. This means a walk concurrent with a
 * grow/release can never throw; at worst it yields a one-frame-stale path.
 */
public class NodeGraph
{
	public static final int NO_NODE = -1;

	private static final byte FLAG_BANK_VISITED = 1;       // bit0
	private static final byte FLAG_ABSTRACT = 1 << 1;      // bit1
	private static final byte FLAG_DELAYED_VISIT = 1 << 2; // bit2
	private static final byte FLAG_TRANSPORT = 1 << 3;     // bit3

	// Enum.values() copies on every call, so cache it for the abstractKind lookup.
	private static final AbstractNodeKind[] ABSTRACT_KINDS = AbstractNodeKind.values();

	// The node arrays are NOT volatile: making them volatile forces every accessor (packedPosition,
	// cost, isTile, compareCost, the append() writes, ...) to re-read the array reference on each
	// call and blocks the JIT from caching the base in a register or eliminating bounds checks. The
	// hot loop touches these hundreds of times per node, so volatile reads roughly halved field
	// throughput (~1.6x slower searches). Safe publication to the render thread is provided instead
	// by the single volatile Pathfinder.bestLastNode handoff: the worker writes the node data, then
	// volatile-writes bestLastNode; the render thread volatile-reads bestLastNode before walking,
	// which establishes happens-before for all the plain writes above it. The walk methods snapshot
	// the references into locals and tolerate a null (post-release) or stale-but-valid (mid-grow,
	// Arrays.copyOf preserves every index) array, so a concurrent grow/release never throws.
	private int[] packedPosition;
	private int[] previous;
	private int[] cost;
	private int[] differentialCost;
	private byte[] flags;
	private byte[] abstractKind;
	private Transport[] transport;
	private int size;

	public NodeGraph(int initialCapacity)
	{
		final int capacity = Math.max(1, initialCapacity);
		packedPosition = new int[capacity];
		previous = new int[capacity];
		cost = new int[capacity];
		differentialCost = new int[capacity];
		flags = new byte[capacity];
		abstractKind = new byte[capacity];
		transport = new Transport[capacity];
	}

	public int size()
	{
		return size;
	}

	private void ensureCapacity()
	{
		if (size < packedPosition.length)
		{
			return;
		}
		// Grow by 50% like ArrayList. Arrays.copyOf preserves every existing index, and node data
		// is write-once, so a render thread reading a pre-grow array still sees correct values for
		// the indices it walks.
		final int newCapacity = packedPosition.length + (packedPosition.length >> 1);
		packedPosition = Arrays.copyOf(packedPosition, newCapacity);
		previous = Arrays.copyOf(previous, newCapacity);
		cost = Arrays.copyOf(cost, newCapacity);
		differentialCost = Arrays.copyOf(differentialCost, newCapacity);
		flags = Arrays.copyOf(flags, newCapacity);
		abstractKind = Arrays.copyOf(abstractKind, newCapacity);
		transport = Arrays.copyOf(transport, newCapacity);
	}

	private int append(int packed, int prev, int nodeCost, int diffCost, byte flagBits, byte kind,
		Transport selectedTransport)
	{
		ensureCapacity();
		final int id = size;
		packedPosition[id] = packed;
		previous[id] = prev;
		cost[id] = nodeCost;
		differentialCost[id] = diffCost;
		abstractKind[id] = kind;
		flags[id] = flagBits;
		transport[id] = selectedTransport;
		size = id + 1;
		return id;
	}

	private int costOf(int id)
	{
		return id == NO_NODE ? 0 : cost[id];
	}

	/**
	 * The search root. Carries no previous node and zero cost.
	 */
	public int createStart(int packedPosition)
	{
		return append(packedPosition, NO_NODE, 0, 0, (byte) 0, (byte) 0, null);
	}

	/**
	 * A concrete walkable tile. Travel cost is the walking distance from the previous node, but
	 * only when the previous node is itself a tile (mirrors the old {@code Node.cost}); reaching a
	 * tile from an abstract node adds no travel cost.
	 */
	public int createTile(int packedPosition, int previous, boolean bankVisited)
	{
		return createTile(packedPosition, previous, bankVisited, 0);
	}

	public int createTile(int packedPosition, int previous, boolean bankVisited, int additionalCost)
	{
		final int travelTime = (previous != NO_NODE && isTile(previous))
			? WorldPointUtil.distanceBetween(this.packedPosition[previous], packedPosition)
			: 0;
		final byte flagBits = bankVisited ? FLAG_BANK_VISITED : 0;
		return append(packedPosition, previous, costOf(previous) + travelTime + additionalCost,
			0, flagBits, (byte) 0, null);
	}

	/**
	 * A transport destination tile. Cost is the previous cost plus the transport's travel time and
	 * any additional cost; there is no walking-distance term (mirrors the old {@code TransportNode}).
	 */
	public int createTransport(int packedPosition, int previous, int travelTime, int additionalCost,
		boolean bankVisited, boolean delayedVisit, int differentialCost, Transport selectedTransport)
	{
		byte flagBits = FLAG_TRANSPORT;
		if (bankVisited)
		{
			flagBits |= FLAG_BANK_VISITED;
		}
		if (delayedVisit)
		{
			flagBits |= FLAG_DELAYED_VISIT;
		}
		return append(packedPosition, previous, costOf(previous) + travelTime + additionalCost,
			differentialCost, flagBits, (byte) 0, selectedTransport);
	}

	/**
	 * An abstract search-state node (global teleports). Has no world position and inherits the
	 * previous node's cost (mirrors the old {@code Node.abstractNode}).
	 */
	public int createAbstract(AbstractNodeKind abstractKind, int previous, boolean bankVisited)
	{
		byte flagBits = FLAG_ABSTRACT;
		if (bankVisited)
		{
			flagBits |= FLAG_BANK_VISITED;
		}
		return append(WorldPointUtil.UNDEFINED, previous, costOf(previous), 0, flagBits,
			(byte) abstractKind.ordinal(), null);
	}

	public int packedPosition(int id)
	{
		return packedPosition[id];
	}

	public int previous(int id)
	{
		return previous[id];
	}

	public int cost(int id)
	{
		return cost[id];
	}

	public int differentialCost(int id)
	{
		return differentialCost[id];
	}

	/**
	 * The cost used for priority-queue ordering, includes the transport differential.
	 */
	public int compareCost(int id)
	{
		return cost[id] + differentialCost[id];
	}

	public boolean bankVisited(int id)
	{
		return (flags[id] & FLAG_BANK_VISITED) != 0;
	}

	public boolean isTile(int id)
	{
		return (flags[id] & FLAG_ABSTRACT) == 0;
	}

	public boolean isAbstract(int id)
	{
		return (flags[id] & FLAG_ABSTRACT) != 0;
	}

	public boolean isTransport(int id)
	{
		return (flags[id] & FLAG_TRANSPORT) != 0;
	}

	public boolean isDelayedVisit(int id)
	{
		return (flags[id] & FLAG_DELAYED_VISIT) != 0;
	}

	public AbstractNodeKind abstractKind(int id)
	{
		return ABSTRACT_KINDS[abstractKind[id]];
	}

	/**
	 * Walks the previous chain from {@code id} to the start, collecting the tile nodes (abstract
	 * nodes are skipped) into an ordered list of path steps.
	 * <p>
	 * Safe to call from the render thread during the search: the arrays are snapshotted into locals
	 * and the walk is bounds-tolerant, so a concurrent grow or {@link #release()} yields an empty
	 * or one-frame-stale result rather than throwing.
	 */
	public List<PathStep> getPathSteps(int id)
	{
		final int[] prev = previous;
		final int[] packed = packedPosition;
		final byte[] flg = flags;
		final Transport[] selectedTransports = transport;
		if (prev == null || packed == null || flg == null || selectedTransports == null
			|| id == NO_NODE)
		{
			return new ArrayList<>();
		}
		final int len = prev.length;

		int node = id;
		int n = 0;
		while (node != NO_NODE && node < len)
		{
			if ((flg[node] & FLAG_ABSTRACT) == 0)
			{
				n++;
			}
			node = prev[node];
		}

		final List<PathStep> pathSteps = new ArrayList<>(n);
		for (int i = 0; i < n; i++)
		{
			pathSteps.add(null);
		}

		node = id;
		int i = n;
		while (node != NO_NODE && node < len && i > 0)
		{
			if ((flg[node] & FLAG_ABSTRACT) == 0)
			{
				pathSteps.set(--i, new PathStep(packed[node],
					(flg[node] & FLAG_BANK_VISITED) != 0, selectedTransports[node]));
			}
			node = prev[node];
		}

		return pathSteps;
	}

	/**
	 * Walks the previous chain from {@code id} until the first tile node and returns its packed
	 * position, or {@link WorldPointUtil#UNDEFINED} if none. Same threading guarantees as
	 * {@link #getPathSteps}.
	 */
	public int getClosestTilePosition(int id)
	{
		final int[] prev = previous;
		final int[] packed = packedPosition;
		final byte[] flg = flags;
		if (prev == null || packed == null || flg == null)
		{
			return WorldPointUtil.UNDEFINED;
		}
		final int len = prev.length;
		int node = id;
		while (node != NO_NODE && node < len && (flg[node] & FLAG_ABSTRACT) != 0)
		{
			node = prev[node];
		}
		return (node != NO_NODE && node < len) ? packed[node] : WorldPointUtil.UNDEFINED;
	}

	/**
	 * Releases the backing arrays once the search is finished and the final path has been
	 * materialised, so the large per-search working set becomes eligible for garbage collection
	 * (the old design dropped the explored {@code Node} objects the same way by clearing the
	 * frontier collections). A render-thread walk in flight keeps its own local references and
	 * finishes safely.
	 */
	public void release()
	{
		packedPosition = null;
		previous = null;
		cost = null;
		differentialCost = null;
		flags = null;
		abstractKind = null;
		transport = null;
		size = 0;
	}
}
