package net.runelite.client.plugins.microbot.questhelper;

import java.util.HashMap;
import java.util.Map;

/** Per-NPC rate limit for dismissing random-event NPCs. */
public final class RandomEventDismissRule {
	public static final long MIN_INTERVAL_MS = 3_000L;
	private final Map<Integer, Long> lastAttemptMs = new HashMap<>();

	/** True (and records the attempt) when this NPC index has not been dismissed within the interval. */
	public synchronized boolean shouldDismiss(int npcIndex, long nowMs) {
		Long last = lastAttemptMs.get(npcIndex);
		if (last != null && nowMs - last < MIN_INTERVAL_MS) {
			return false;
		}
		lastAttemptMs.put(npcIndex, nowMs);
		if (lastAttemptMs.size() > 64) {
			lastAttemptMs.values().removeIf(t -> nowMs - t >= MIN_INTERVAL_MS);
		}
		return true;
	}
}
