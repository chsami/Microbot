package shortestpath;

import java.util.Arrays;

/**
 * A minimal, growable list implementation for primitive {@code int} values.
 * <p>
 * This class avoids boxing overhead present in {@link java.util.List Integer}
 * collections
 * by storing values in a backing {@code int[]} that grows as needed. It
 * purposefully
 * implements only the operations required by the pathfinding logic in this
 * plugin; it is
 * <strong>not</strong> a drop‑in replacement for {@link java.util.ArrayList}.
 * <p>
 * The growth policy increases capacity by 50% (similar to {@code ArrayList})
 * when the
 * existing array is exhausted. Capacity never shrinks.
 */
public class PrimitiveIntList
{
	private static final int MAX_ARRAY_SIZE = Integer.MAX_VALUE - 8;

	private int[] elementData;
	private int size;

	/**
	 * Creates a new list with the specified initial capacity.
	 *
	 * @param initialCapacity the initial length of the backing array (must be
	 *                        {@code >= 0}).
	 * @param initialize      if {@code true}, the {@link #size} is set equal to
	 *                        {@code initialCapacity},
	 *                        effectively pre-filling the logical list with zeroes.
	 *                        If {@code false},
	 *                        the list is created empty.
	 * @throws IllegalArgumentException if {@code initialCapacity < 0}.
	 */
	public PrimitiveIntList(int initialCapacity, boolean initialize)
	{
		if (initialCapacity < 0)
		{
			throw new IllegalArgumentException("Illegal capacity: " + initialCapacity);
		}
		this.elementData = new int[initialCapacity];
		if (initialize)
		{
			size = initialCapacity;
		}
	}

	/**
	 * Creates an empty list with the given backing array capacity.
	 *
	 * @param initialCapacity initial length of the internal array (must be
	 *                        {@code >= 0}).
	 */
	public PrimitiveIntList(int initialCapacity)
	{
		this(initialCapacity, false);
	}

	/**
	 * Creates an empty list with a default initial capacity of 10.
	 */
	public PrimitiveIntList()
	{
		this(10);
	}

	private static int hugeCapacity(int minCapacity)
	{
		if (minCapacity < 0)
		{ // overflow
			throw new OutOfMemoryError();
		}
		return (minCapacity > MAX_ARRAY_SIZE) ? Integer.MAX_VALUE : MAX_ARRAY_SIZE;
	}

	/**
	 * Ensures that the backing array can store at least {@code minCapacity}
	 * elements.
	 * If the current capacity is already sufficient the call is a no-op.
	 *
	 * @param minCapacity the desired minimum capacity (ignored if {@code <= 0}).
	 */
	public void ensureCapacity(int minCapacity)
	{
		if (minCapacity > 0)
		{
			ensureCapacityInternal(minCapacity);
		}
	}

	private void ensureCapacityInternal(int minCapacity)
	{
		if (minCapacity - elementData.length > 0)
		{
			grow(minCapacity);
		}
	}

	private void grow(int minCapacity)
	{
		int oldCapacity = elementData.length;
		int newCapacity = oldCapacity + (oldCapacity >> 1);
		if (newCapacity - minCapacity < 0)
		{
			newCapacity = minCapacity;
		}
		if (newCapacity - MAX_ARRAY_SIZE > 0)
		{
			newCapacity = hugeCapacity(minCapacity);
		}
		elementData = Arrays.copyOf(elementData, newCapacity);
	}

	/**
	 * Returns the number of elements that have been added to (or initialized in)
	 * this list.
	 *
	 * @return current element count (always {@code >= 0}).
	 */
	public int size()
	{
		return size;
	}

	/**
	 * Indicates whether the list currently holds zero elements.
	 *
	 * @return {@code true} if {@link #size()} is zero; {@code false} otherwise.
	 */
	public boolean isEmpty()
	{
		return size == 0;
	}

	/**
	 * Tests whether the specified primitive value exists in the list.
	 *
	 * @param e the value to search for.
	 * @return {@code true} if the value occurs at least once; {@code false}
	 * otherwise.
	 */
	public boolean contains(int e)
	{
		return indexOf(e) >= 0;
	}

	/**
	 * Returns the index of the first occurrence of the given value, or {@code -1}
	 * if absent.
	 *
	 * @param e the value to locate.
	 * @return zero-based index of the value, or {@code -1} if not found.
	 */
	public int indexOf(int e)
	{
		for (int i = 0; i < size; i++)
		{
			if (e == elementData[i])
			{
				return i;
			}
		}
		return -1;
	}

	/**
	 * Retrieves the value at the specified index.
	 *
	 * @param index zero-based position of the element to return.
	 * @return the value stored at {@code index}.
	 * @throws IndexOutOfBoundsException if {@code index < 0 || index >= size}.
	 */
	public int get(int index)
	{
		rangeCheck(index);
		return elementData[index];
	}

	/**
	 * Replaces the value at the specified index.
	 *
	 * @param index   zero-based position of the element to overwrite.
	 * @param element the new value.
	 * @return the previous value stored at {@code index}.
	 * @throws IndexOutOfBoundsException if {@code index < 0 || index >= size}.
	 */
	public int set(int index, int element)
	{
		rangeCheck(index);
		int oldValue = elementData[index];
		elementData[index] = element;
		return oldValue;
	}

	/**
	 * Appends a value to the end of the list, growing the backing array if
	 * required.
	 *
	 * @param e value to append.
	 */
	public void add(int e)
	{
		ensureCapacityInternal(size + 1);
		elementData[size++] = e;
	}

	/**
	 * Inserts a value at the specified index, shifting subsequent elements one
	 * position to the right.
	 *
	 * @param index   zero-based insertion point (may be equal to {@link #size()} to
	 *                append).
	 * @param element the value to insert.
	 * @throws IndexOutOfBoundsException if {@code index < 0 || index > size}.
	 */
	public void add(int index, int element)
	{
		rangeCheckForAdd(index);
		ensureCapacityInternal(size + 1);
		System.arraycopy(elementData, index, elementData, index + 1, size - index);
		elementData[index] = element;
		size++;
	}

	/**
	 * Removes the value at the specified index and compacts the list.
	 *
	 * @param index zero-based index of the element to remove.
	 * @return the removed value.
	 * @throws IndexOutOfBoundsException if {@code index < 0 || index >= size}.
	 */
	public int removeAt(int index)
	{
		rangeCheck(index);
		int oldValue = elementData[index];
		int numMoved = size - index - 1;
		if (numMoved > 0)
		{
			System.arraycopy(elementData, index + 1, elementData, index, numMoved);
		}
		elementData[--size] = 0;
		return oldValue;
	}

	/**
	 * Removes the first occurrence of the specified value, if present.
	 *
	 * @param e value to remove.
	 * @return {@code true} if a value was removed; {@code false} otherwise.
	 */
	public boolean remove(int e)
	{
		for (int i = 0; i < size; i++)
		{
			if (e == elementData[i])
			{
				fastRemove(i);
				return true;
			}
		}
		return false;
	}

	private void fastRemove(int index)
	{
		int numMoved = size - index - 1;
		if (numMoved > 0)
		{
			System.arraycopy(elementData, index + 1, elementData, index, numMoved);
		}
		elementData[--size] = 0;
	}

	/**
	 * Removes all elements from the list and resets {@link #size()} to zero.
	 * The backing array is retained for reuse.
	 */
	public void clear()
	{
		for (int i = 0; i < size; i++)
		{
			elementData[i] = 0;
		}
		size = 0;
	}

	private void rangeCheck(int index)
	{
		if (index >= size)
		{
			throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
		}
	}

	private void rangeCheckForAdd(int index)
	{
		if (index > size || index < 0)
		{
			throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + size);
		}
	}
}
