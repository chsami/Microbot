package shortestpath.pathfinder;

/**
 * A minimal growable FIFO of primitive {@code int} node ids backed by a ring buffer.
 * <p>
 * Replaces the {@code ArrayDeque<Node>} boundary queue in {@link Pathfinder} so the search frontier
 * no longer boxes node references. Only the operations the pathfinder needs are implemented; it is
 * single-threaded (worker only) like the queue it replaces.
 */
class IntDeque
{
	private int[] elements;
	private int head;
	private int tail;
	private int size;

	IntDeque(int initialCapacity)
	{
		elements = new int[Math.max(1, initialCapacity)];
	}

	int size()
	{
		return size;
	}

	boolean isEmpty()
	{
		return size == 0;
	}

	void addLast(int value)
	{
		if (size == elements.length)
		{
			grow();
		}
		elements[tail] = value;
		tail = increment(tail);
		size++;
	}

	void addFirst(int value)
	{
		if (size == elements.length)
		{
			grow();
		}
		head = decrement(head);
		elements[head] = value;
		size++;
	}

	/**
	 * @return the first element, or {@link NodeGraph#NO_NODE} if empty.
	 */
	int peekFirst()
	{
		return size == 0 ? NodeGraph.NO_NODE : elements[head];
	}

	/**
	 * @return the removed first element, or {@link NodeGraph#NO_NODE} if empty.
	 */
	int pollFirst()
	{
		if (size == 0)
		{
			return NodeGraph.NO_NODE;
		}
		final int value = elements[head];
		head = increment(head);
		size--;
		return value;
	}

	void clear()
	{
		head = 0;
		tail = 0;
		size = 0;
	}

	private int increment(int index)
	{
		return index + 1 == elements.length ? 0 : index + 1;
	}

	private int decrement(int index)
	{
		return index == 0 ? elements.length - 1 : index - 1;
	}

	private void grow()
	{
		final int oldCapacity = elements.length;
		final int[] grown = new int[oldCapacity << 1];
		for (int i = 0; i < size; i++)
		{
			grown[i] = elements[(head + i) % oldCapacity];
		}
		elements = grown;
		head = 0;
		tail = size;
	}
}
