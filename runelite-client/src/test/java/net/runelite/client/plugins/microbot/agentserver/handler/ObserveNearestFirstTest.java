package net.runelite.client.plugins.microbot.agentserver.handler;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ObserveNearestFirstTest {

	@Test
	public void sortsByDistanceAscending() {
		List<String> sorted = ObserveHandler.nearestFirst(Arrays.asList("ccc", "a", "bb"), String::length);
		assertEquals(Arrays.asList("a", "bb", "ccc"), sorted);
	}

	@Test
	public void readsEachDistanceOnceEvenWhenItChangesBetweenReads() {
		List<Object> items = new ArrayList<>();
		for (int i = 0; i < 500; i++) {
			items.add(new Object());
		}
		Random random = new Random(42);
		AtomicInteger reads = new AtomicInteger();
		Map<Object, Integer> firstRead = new IdentityHashMap<>();

		List<Object> sorted = ObserveHandler.nearestFirst(items, item -> {
			reads.incrementAndGet();
			int value = random.nextInt(30);
			firstRead.putIfAbsent(item, value);
			return value;
		});

		assertEquals(items.size(), reads.get());
		assertEquals(items.size(), sorted.size());
		for (int i = 1; i < sorted.size(); i++) {
			assertTrue(firstRead.get(sorted.get(i - 1)) <= firstRead.get(sorted.get(i)));
		}
	}
}
