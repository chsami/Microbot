package shortestpath.pathfinder;

import java.util.Arrays;

/**
 * A binary min-heap of primitive {@code int} node ids ordered by {@link NodeGraph#compareCost}.
 * <p>
 * Replaces the {@code PriorityQueue<TransportNode>} pending queue in {@link Pathfinder} so transport
 * candidates are stored as int ids rather than boxed node objects. The ordering key is fixed when a
 * node is created (its differential cost never changes), so no decrease-key support is needed; the
 * pathfinder discards stale cheaper duplicates with its dequeue-time visited re-check. Single-threaded
 * (worker only), matching the queue it replaces.
 */
class IntMinHeap
{
	private final NodeGraph graph;
	private int[] heap;
	private int size;

	IntMinHeap(NodeGraph graph, int initialCapacity)
	{
		this.graph = graph;
		this.heap = new int[Math.max(1, initialCapacity)];
	}

	int size()
	{
		return size;
	}

	boolean isEmpty()
	{
		return size == 0;
	}

	/**
	 * @return the minimum-cost element, or {@link NodeGraph#NO_NODE} if empty.
	 */
	int peek()
	{
		return size == 0 ? NodeGraph.NO_NODE : heap[0];
	}

	void add(int id)
	{
		if (size == heap.length)
		{
			heap = Arrays.copyOf(heap, heap.length << 1);
		}
		heap[size] = id;
		siftUp(size);
		size++;
	}

	/**
	 * @return the removed minimum-cost element, or {@link NodeGraph#NO_NODE} if empty.
	 */
	int poll()
	{
		if (size == 0)
		{
			return NodeGraph.NO_NODE;
		}
		final int top = heap[0];
		size--;
		if (size > 0)
		{
			heap[0] = heap[size];
			siftDown(0);
		}
		return top;
	}

	void clear()
	{
		size = 0;
	}

	private void siftUp(int index)
	{
		final int id = heap[index];
		final int key = graph.compareCost(id);
		while (index > 0)
		{
			final int parent = (index - 1) >> 1;
			if (key >= graph.compareCost(heap[parent]))
			{
				break;
			}
			heap[index] = heap[parent];
			index = parent;
		}
		heap[index] = id;
	}

	private void siftDown(int index)
	{
		final int id = heap[index];
		final int key = graph.compareCost(id);
		final int half = size >> 1;
		while (index < half)
		{
			int child = (index << 1) + 1;
			int childKey = graph.compareCost(heap[child]);
			final int right = child + 1;
			if (right < size)
			{
				final int rightKey = graph.compareCost(heap[right]);
				if (rightKey < childKey)
				{
					child = right;
					childKey = rightKey;
				}
			}
			if (key <= childKey)
			{
				break;
			}
			heap[index] = heap[child];
			index = child;
		}
		heap[index] = id;
	}
}
