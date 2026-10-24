package net.runelite.client.plugins.microbot.agentserver.handler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

public final class RecentLog {

	private final int capacity;
	private final Deque<Map<String, Object>> entries = new ArrayDeque<>();
	private long lastSeq;

	public RecentLog(int capacity) {
		this.capacity = capacity;
	}

	public int capacity() {
		return capacity;
	}

	public synchronized void add(Map<String, Object> entry) {
		entry.put("seq", ++lastSeq);
		entry.put("at", System.currentTimeMillis());
		entries.addLast(entry);
		if (entries.size() > capacity) {
			entries.removeFirst();
		}
	}

	public synchronized long cursor() {
		return lastSeq;
	}

	public List<Map<String, Object>> since(long afterSeq, int limit) {
		return since(afterSeq, limit, entry -> true);
	}

	public synchronized List<Map<String, Object>> since(long afterSeq, int limit, Predicate<Map<String, Object>> filter) {
		List<Map<String, Object>> newer = new ArrayList<>();
		for (Map<String, Object> entry : entries) {
			if ((long) entry.get("seq") > afterSeq && filter.test(entry)) {
				newer.add(entry);
			}
		}
		int from = Math.max(0, newer.size() - limit);
		return new ArrayList<>(newer.subList(from, newer.size()));
	}

	public synchronized void clear() {
		entries.clear();
		lastSeq = 0;
	}
}
