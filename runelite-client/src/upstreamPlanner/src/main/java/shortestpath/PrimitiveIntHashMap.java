package shortestpath;

import java.util.Arrays;
import java.util.Collection;

/**
 * A lightweight hash map keyed by primitive {@code int} values using an open-addressed table with
 * linear probing.
 * <p>
 * Keys and values are stored in two parallel arrays ({@link #keys} / {@link #values}) rather than a
 * node object per entry, so the map holds only a couple of arrays regardless of how many entries it
 * contains (issue #491). A slot is occupied iff its value reference is non-null; since null values
 * are rejected, {@code int} keys including {@code 0} need no separate sentinel. When the entry count
 * reaches the configured {@linkplain #capacity load threshold} the table is rehashed into a larger
 * power-of-two array.
 * <p>
 * This implementation is intentionally minimal and tailored for the plugin's pathfinding needs:
 * <ul>
 * <li>No support for removing entries.</li>
 * <li>No iteration views (keys, values, or entry set) are exposed beyond {@link #keys()}.</li>
 * <li>Duplicate key insertion replaces the previous value, or appends collection contents when both
 * the old and new values are {@link Collection}s (best effort; falls back to replacement on
 * errors).</li>
 * </ul>
 *
 * @param <V> the value type stored for each primitive {@code int} key. Must be non-null.
 */
public class PrimitiveIntHashMap<V>
{
	private static final int MINIMUM_SIZE = 8;

	// How full the map should get before growing it again. Smaller values speed up
	// lookup times at the expense of space
	private static final float DEFAULT_LOAD_FACTOR = 0.75f;
	private final float loadFactor;
	private int[] keys;
	private Object[] values;
	private int size;
	private int capacity;
	private int maxSize;
	private int mask;

	/**
	 * Creates a new map with the specified initial size and the default load factor (0.75).
	 *
	 * @param initialSize initial expected number of elements; rounded to the next power of two
	 *                    internally.
	 */
	public PrimitiveIntHashMap(int initialSize)
	{
		this(initialSize, DEFAULT_LOAD_FACTOR);
	}

	/**
	 * Creates a new map with the given initial size and load factor.
	 *
	 * @param initialSize initial expected number of elements; rounded up to maintain a
	 *                    power-of-two capacity.
	 * @param loadFactor  a value in the range {@code [0.0, 1.0]} determining when the map rehashes.
	 * @throws IllegalArgumentException if {@code loadFactor} is outside the inclusive range 0..1.
	 */
	public PrimitiveIntHashMap(int initialSize, float loadFactor)
	{
		if (loadFactor < 0.0f || loadFactor > 1.0f)
		{
			throw new IllegalArgumentException("Load factor must be between 0 and 1");
		}

		this.loadFactor = loadFactor;
		size = 0;
		setNewSize(initialSize);
		recreateArrays();
	}

	/**
	 * Hash function tuned for packed world point integer encodings. Mixes higher bits downward to
	 * reduce clustering while remaining inexpensive.
	 */
	private static int hash(int value)
	{
		// Full multiplicative avalanche. Linear probing is very sensitive to clustering, and packed
		// world points of nearby tiles differ only in a few low bits, so the cheap xor-shift mix used
		// previously left spatially-clustered transport origins clustered in the table too -> long
		// probe runs on the per-tile miss lookups. Fibonacci-style multiply + xorshift spreads them.
		int h = value * 0x9E3779B1;
		return h ^ (h >>> 16);
	}

	/**
	 * Returns the number of key/value pairs currently stored.
	 *
	 * @return current entry count (always {@code >= 0}).
	 */
	public int size()
	{
		return size;
	}

	/**
	 * Returns all keys present in the map as a freshly allocated {@code int[]}.
	 *
	 * @return array of all keys in unspecified order; length equals {@link #size()}.
	 */
	@SuppressWarnings("unused")
	public int[] keys()
	{
		int[] result = new int[size];
		int index = 0;
		for (int i = 0; i < values.length; ++i)
		{
			if (values[i] != null)
			{
				result[index++] = keys[i];
			}
		}
		return result;
	}

	/**
	 * Retrieves the value mapped to the provided key, or {@code null} if absent.
	 *
	 * @param key primitive key to look up.
	 * @return the mapped value, or {@code null} if the key does not exist.
	 */
	public V get(int key)
	{
		return getOrDefault(key, null);
	}

	/**
	 * Retrieves the value mapped to the provided key.
	 *
	 * @param key          primitive key to look up.
	 * @param defaultValue value to return if the key is not present.
	 * @return the mapped value, or {@code defaultValue} when absent.
	 */
	@SuppressWarnings("unchecked")
	public V getOrDefault(int key, V defaultValue)
	{
		final int slot = findSlot(key);
		if (slot < 0)
		{
			return defaultValue;
		}
		return (V) values[slot];
	}

	/**
	 * Associates the specified value with the given key.
	 * <p>
	 * If a mapping already exists and both the existing and new values implement {@link Collection},
	 * the method attempts to append all elements of the new collection into the existing one. If the
	 * append fails (e.g., due to incompatible element types or an unsupported operation) the existing
	 * value is replaced entirely. Otherwise the existing value is simply replaced.
	 *
	 * @param key   primitive key to insert or update.
	 * @param value non-null value to associate.
	 * @param <E>   inferred element type if both values are collections.
	 * @return the previous value mapped to {@code key} (if any), or {@code null} if inserting a new
	 * entry.
	 * @throws IllegalArgumentException if {@code value} is {@code null}.
	 */
	@SuppressWarnings({"unchecked"})
	public <E> V put(int key, V value)
	{
		if (value == null)
		{
			throw new IllegalArgumentException("Cannot insert a null value");
		}

		int i = (hash(key) & 0x7FFFFFFF) & mask;
		while (values[i] != null)
		{
			if (keys[i] == key)
			{
				V previous = (V) values[i];
				if (previous instanceof Collection<?> && value instanceof Collection<?>)
				{ // append
					try
					{
						Collection<E> prevCollection = (Collection<E>) values[i];
						Collection<E> newCollection = (Collection<E>) value;
						prevCollection.addAll(newCollection);
					}
					catch (ClassCastException | UnsupportedOperationException e)
					{
						// If the collections contain incompatible types or the operation is not
						// supported, just replace instead of append
						values[i] = value;
					}
				}
				else
				{ // replace
					values[i] = value;
				}
				return previous;
			}
			i = (i + 1) & mask;
		}

		keys[i] = key;
		values[i] = value;
		incrementSize();
		return null;
	}

	private int findSlot(int key)
	{
		int i = (hash(key) & 0x7FFFFFFF) & mask;
		while (values[i] != null)
		{
			if (keys[i] == key)
			{
				return i;
			}
			i = (i + 1) & mask;
		}
		return -1;
	}

	private void incrementSize()
	{
		size++;
		if (size >= capacity)
		{
			rehash();
		}
	}

	private int getNewMaxSize(int size)
	{
		int nextPow2 = -1 >>> Integer.numberOfLeadingZeros(size);
		if (nextPow2 >= (Integer.MAX_VALUE >>> 1))
		{
			return (Integer.MAX_VALUE >>> 1) + 1;
		}
		return nextPow2 + 1;
	}

	private void setNewSize(int size)
	{
		if (size < MINIMUM_SIZE)
		{
			size = MINIMUM_SIZE - 1;
		}

		maxSize = getNewMaxSize(size);
		mask = maxSize - 1;
		capacity = (int) (maxSize * loadFactor);
	}

	private void growCapacity()
	{
		setNewSize(maxSize);
	}

	// Grow the table then rehash all the values into it and discard the old arrays
	private void rehash()
	{
		growCapacity();

		final int[] oldKeys = keys;
		final Object[] oldValues = values;
		recreateArrays();

		for (int i = 0; i < oldValues.length; ++i)
		{
			if (oldValues[i] == null)
			{
				continue;
			}

			int slot = (hash(oldKeys[i]) & 0x7FFFFFFF) & mask;
			while (values[slot] != null)
			{
				slot = (slot + 1) & mask;
			}
			keys[slot] = oldKeys[i];
			values[slot] = oldValues[i];
		}
	}

	private void recreateArrays()
	{
		keys = new int[maxSize];
		values = new Object[maxSize];
	}

	/**
	 * Approximate fullness of the table as a percentage of the entry count over the table length.
	 *
	 * @return fullness percentage in {@code [0.0, 100.0]}, or {@link Double#NaN} when the map is
	 * empty.
	 */
	public double calculateFullness()
	{
		if (size == 0)
		{
			return Double.NaN;
		}
		return 100.0 * (double) size / (double) maxSize;
	}

	/**
	 * Removes all entries from the map. The backing arrays are retained and reused.
	 */
	public void clear()
	{
		size = 0;
		Arrays.fill(values, null);
	}
}
