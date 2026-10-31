package net.runelite.client.plugins.microbot.agentserver.handler;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class ScriptHeartbeatRegistry {

	private ScriptHeartbeatRegistry() {}

	private static final ConcurrentHashMap<String, HeartbeatRecord> registry = new ConcurrentHashMap<>();

	public static void recordHeartbeat(String scriptClassName) {
		registry.computeIfAbsent(scriptClassName, HeartbeatRecord::new).beat();
	}

	public static void remove(String scriptClassName) {
		registry.remove(scriptClassName);
	}

	public static Map<String, Object> getHealth(String scriptClassName) {
		HeartbeatRecord record = registry.get(scriptClassName);
		if (record == null) {
			return null;
		}
		return record.toMap();
	}

	public static Set<String> scriptNames() {
		return new HashSet<>(registry.keySet());
	}

	public static Map<String, Map<String, Object>> getAllHealth() {
		Map<String, Map<String, Object>> all = new LinkedHashMap<>();
		for (Map.Entry<String, HeartbeatRecord> entry : registry.entrySet()) {
			all.put(entry.getKey(), entry.getValue().toMap());
		}
		return all;
	}

	static final class HeartbeatRecord {
		private final String scriptClassName;
		private final AtomicLong loopCount = new AtomicLong(0);
		private final AtomicLong lastHeartbeatMs = new AtomicLong(System.currentTimeMillis());

		HeartbeatRecord(String scriptClassName) {
			this.scriptClassName = scriptClassName;
		}

		void beat() {
			loopCount.incrementAndGet();
			lastHeartbeatMs.set(System.currentTimeMillis());
		}

		Map<String, Object> toMap() {
			long lastMs = lastHeartbeatMs.get();
			long stalledMs = System.currentTimeMillis() - lastMs;
			Map<String, Object> map = new LinkedHashMap<>();
			map.put("scriptClassName", scriptClassName);
			map.put("loopCount", loopCount.get());
			map.put("lastHeartbeatAt", Instant.ofEpochMilli(lastMs).toString());
			map.put("stalledMs", stalledMs);
			return map;
		}
	}
}
