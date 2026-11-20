package net.runelite.client.plugins.microbot.shortestpath;

import java.util.Arrays;
import java.util.Collection;

// This class is not intended as a general purpose replacement for a hashmap; it lacks convenience features
// found in regular maps and has no way to remove elements or get a list of keys/values.
public class PrimitiveIntHashMap<V> {
    private static final int MINIMUM_SIZE = 8;

    // Unless the hash function is really unbalanced, most things should fit within at least 8-element buckets
    // Buckets will grow as needed without forcing a rehash of the whole map
    private static final int DEFAULT_BUCKET_SIZE = 4;

    // How full the map should get before growing it again. Smaller values speed up lookup times at the expense of space
    private static final float DEFAULT_LOAD_FACTOR = 0.75f;

    private static class IntNode<V> {
        private final int key;
        private final V value;

        private IntNode(int key, V value) {
            this.key = key;
            this.value = value;
        }
    }

    // If buckets become too large then it may be worth converting large buckets into an array-backed binary tree
    private volatile IntNode<V>[][] buckets;
    private volatile int size;
    private int capacity;
    private int maxSize;
    private final float loadFactor;

    public PrimitiveIntHashMap(int initialSize) {
        this(initialSize, DEFAULT_LOAD_FACTOR);
    }

    public PrimitiveIntHashMap(int initialSize, float loadFactor) {
        if (loadFactor < 0.0f || loadFactor > 1.0f) {
            throw new IllegalArgumentException("Load factor must be between 0 and 1");
        }

        this.loadFactor = loadFactor;
        size = 0;
        setNewSize(initialSize);
        buckets = createBuckets(maxSize);
    }

    public int size() {
        return size;
    }

    public V get(int key) {
        return getOrDefault(key, null);
    }

    public V getOrDefault(int key, V defaultValue) {
        IntNode<V>[][] table = buckets;
        IntNode<V>[] bucket = table[getBucket(key, table)];
        if (bucket == null) {
            return defaultValue;
        }

        for (int i = 0; i < bucket.length; ++i) {
            IntNode<V> node = bucket[i];
            if (node == null) {
                break;
            }
            if (node.key == key) {
                return node.value;
            }
        }

        return defaultValue;
    }

    /* Associates the specified value with the specified key in this map.
     * If the map previously contained a mapping for the key, the old value is
     * replaced or appended if both the old and new value is a collection.
     */
    @SuppressWarnings("unchecked")
    public synchronized <E> V put(int key, V value) {
        if (value == null) {
            throw new IllegalArgumentException("Cannot insert a null value");
        }

        IntNode<V>[][] table = buckets;
        int bucketIndex = getBucket(key, table);
        IntNode<V>[] bucket = table[bucketIndex];

        if (bucket == null) {
            IntNode<V>[] created = createBucket(DEFAULT_BUCKET_SIZE);
            created[0] = new IntNode<>(key, value);
            table[bucketIndex] = created;
            incrementSize();
            return null;
        }

        for (int i = 0; i < bucket.length; ++i) {
            if (bucket[i] == null) {
                bucket[i] = new IntNode<>(key, value);
                incrementSize();
                return null;
            } else if (bucket[i].key == key) {
                V previous = bucket[i].value;
                if (previous instanceof Collection<?> && value instanceof Collection<?>) { // append
                    ((Collection<E>) bucket[i].value).addAll((Collection<E>) value);
                } else { // replace
                    bucket[i] = new IntNode<>(key, value);
                }
                return previous;
            }
        }

        // No space in the bucket, grow it
        growBucket(table, bucketIndex, new IntNode<>(key, value));
        incrementSize();
        return null;
    }

    // This hash seems to be most effective for packed WorldPoint's
    private static int hash(int value) {
        return value ^ (value >>> 5) ^ (value >>> 25);
    }

    private static int getBucket(int key, Object[] table) {
        return (hash(key) & 0x7FFFFFFF) & (table.length - 1);
    }

    private void incrementSize() {
        size++;
        if (size >= capacity) {
            rehash();
        }
    }

    private void growBucket(IntNode<V>[][] table, int bucketIndex, IntNode<V> node) {
        IntNode<V>[] oldBucket = table[bucketIndex];
        IntNode<V>[] newBucket = createBucket(Math.min(oldBucket.length, Integer.MAX_VALUE / 2 - 4) * 2);
        System.arraycopy(oldBucket, 0, newBucket, 0, oldBucket.length);
        newBucket[oldBucket.length] = node;
        table[bucketIndex] = newBucket;
    }

    private int getNewMaxSize(int size) {
        int nextPow2 = -1 >>> Integer.numberOfLeadingZeros(size);
        if (nextPow2 >= (Integer.MAX_VALUE >>> 1)) {
            return (Integer.MAX_VALUE >>> 1) + 1;
        }
        return nextPow2 + 1;
    }

    private void setNewSize(int size) {
        if (size < MINIMUM_SIZE) {
            size = MINIMUM_SIZE - 1;
        }

        maxSize = getNewMaxSize(size);
        capacity = (int)(maxSize * loadFactor);
    }

    private void growCapacity() {
        setNewSize(maxSize);
    }

    // Grow the bucket array then rehash all the values into new buckets and discard the old ones
    private void rehash() {
        growCapacity();

        IntNode<V>[][] oldBuckets = buckets;
        IntNode<V>[][] newBuckets = createBuckets(maxSize);

        for (int i = 0; i < oldBuckets.length; ++i) {
            IntNode<V>[] oldBucket = oldBuckets[i];
            if (oldBucket == null) {
                continue;
            }

            for (int ind = 0; ind < oldBucket.length; ++ind) {
                if (oldBucket[ind] == null) {
                    break;
                }

                int bucketIndex = getBucket(oldBucket[ind].key, newBuckets);
                IntNode<V>[] newBucket = newBuckets[bucketIndex];
                if (newBucket == null) {
                    newBucket = createBucket(DEFAULT_BUCKET_SIZE);
                    newBucket[0] = oldBucket[ind];
                    newBuckets[bucketIndex] = newBucket;
                } else {
                    int bInd;
                    for (bInd = 0; bInd < newBucket.length; ++bInd) {
                        if (newBucket[bInd] == null) {
                            newBucket[bInd] = oldBucket[ind];
                            break;
                        }
                    }

                    if (bInd >= newBucket.length) {
                        growBucket(newBuckets, bucketIndex, oldBucket[ind]);
                    }
                }
            }
        }

        buckets = newBuckets;
    }

    private IntNode<V>[][] createBuckets(int length) {
        @SuppressWarnings({"unchecked", "SuspiciousArrayCast"})
        IntNode<V>[][] temp = (IntNode<V>[][])new IntNode[length][];
        return temp;
    }

    private IntNode<V>[] createBucket(int size) {
        @SuppressWarnings({"unchecked", "SuspiciousArrayCast"})
        IntNode<V>[] temp = (IntNode<V>[])new IntNode[size];
        return temp;
    }

    // Debug helper to understand how effective a given hash may be at distributing values
    public double calculateFullness() {
        IntNode<V>[][] table = buckets;
        int size = 0;
        int usedSize = 0;
        for (int i = 0; i < table.length; ++i) {
            IntNode<V>[] bucket = table[i];
            if (bucket == null) continue;
            size += bucket.length;
            for (int j = 0; j < bucket.length; ++j) {
                if (bucket[j] == null) {
                    usedSize += j;
                    break;
                }
            }
        }
        return 100.0 * (double)usedSize / (double)size;
    }

    public synchronized void clear() {
        size = 0;
        buckets = createBuckets(buckets.length);
    }
}
