package net.runelite.client.plugins.microbot.shortestpath;

import org.junit.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class PrimitiveIntHashMapConcurrentReadTest {
    private static final int STABLE_KEYS = 64;
    private static final int ROUNDS = 40;
    private static final int GROWTH_KEYS = 60_000;
    private static final Integer MISSING = -1;

    @Test
    public void readerKeepsSeeingExistingEntriesWhileWriterGrowsTheMap() throws Exception {
        AtomicReference<PrimitiveIntHashMap<Integer>> shared = new AtomicReference<>(newMapWithStableKeys());
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread reader = new Thread(() -> {
            try {
                while (running.get()) {
                    PrimitiveIntHashMap<Integer> map = shared.get();
                    for (int key = 0; key < STABLE_KEYS; key++) {
                        Integer value = map.getOrDefault(key, MISSING);
                        if (value.intValue() != key) {
                            throw new AssertionError("key " + key + " read as " + value);
                        }
                    }
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        }, "primitive-int-hash-map-reader");
        reader.start();

        try {
            for (int round = 0; round < ROUNDS && failure.get() == null; round++) {
                PrimitiveIntHashMap<Integer> map = newMapWithStableKeys();
                shared.set(map);
                for (int key = STABLE_KEYS; key < GROWTH_KEYS; key++) {
                    map.put(key, key);
                }
            }
        } finally {
            running.set(false);
            reader.join(10_000);
        }

        assertNull(describe(failure.get()), failure.get());
    }

    @Test
    public void readerNeverThrowsWhileWriterClearsAndRefills() throws Exception {
        PrimitiveIntHashMap<Integer> map = new PrimitiveIntHashMap<>(8);
        AtomicBoolean running = new AtomicBoolean(true);
        AtomicReference<Throwable> failure = new AtomicReference<>();

        Thread reader = new Thread(() -> {
            try {
                while (running.get()) {
                    for (int key = 0; key < 4_096; key++) {
                        Integer value = map.getOrDefault(key, MISSING);
                        if (!MISSING.equals(value) && value.intValue() != key) {
                            throw new AssertionError("key " + key + " read as " + value);
                        }
                    }
                }
            } catch (Throwable t) {
                failure.compareAndSet(null, t);
            }
        }, "primitive-int-hash-map-reader");
        reader.start();

        try {
            for (int round = 0; round < 400 && failure.get() == null; round++) {
                map.clear();
                for (int key = 0; key < 4_096; key++) {
                    map.put(key, key);
                }
            }
        } finally {
            running.set(false);
            reader.join(10_000);
        }

        assertNull(describe(failure.get()), failure.get());
        assertEquals(4_096, map.size());
        for (int key = 0; key < 4_096; key++) {
            assertEquals(Integer.valueOf(key), map.get(key));
        }
    }

    @Test
    public void putReplacesPlainValuesAndAppendsCollections() {
        PrimitiveIntHashMap<Integer> plain = new PrimitiveIntHashMap<>(8);
        assertNull(plain.put(5, 1));
        assertEquals(Integer.valueOf(1), plain.put(5, 2));
        assertEquals(Integer.valueOf(2), plain.get(5));
        assertEquals(1, plain.size());

        PrimitiveIntHashMap<java.util.Set<String>> sets = new PrimitiveIntHashMap<>(8);
        java.util.Set<String> first = new java.util.HashSet<>(java.util.Collections.singleton("a"));
        sets.put(7, first);
        sets.put(7, new java.util.HashSet<>(java.util.Collections.singleton("b")));
        assertEquals(new java.util.HashSet<>(java.util.Arrays.asList("a", "b")), sets.get(7));
        assertEquals(1, sets.size());
    }

    private static PrimitiveIntHashMap<Integer> newMapWithStableKeys() {
        PrimitiveIntHashMap<Integer> map = new PrimitiveIntHashMap<>(8);
        for (int key = 0; key < STABLE_KEYS; key++) {
            map.put(key, key);
        }
        return map;
    }

    private static String describe(Throwable failure) {
        return failure == null ? "" : failure.toString();
    }
}
