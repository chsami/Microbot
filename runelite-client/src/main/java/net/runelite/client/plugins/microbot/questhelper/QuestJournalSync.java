package net.runelite.client.plugins.microbot.questhelper;

import java.util.List;
import java.util.Locale;

/**
 * Pure rules for the helper steps that only clear once the quest's journal has been opened,
 * e.g. "Open the quest's journal to sync your current quest state."
 */
final class QuestJournalSync {
	/** Minimum gap between two attempts, so a journal that will not open is not clicked at forever. */
	static final long RETRY_MS = 6_000;

	/** Gap between tries once the quest list would not open: the sync is not abandoned, only slowed. */
	static final long GIVE_UP_BACKOFF_MS = 60_000;

	private QuestJournalSync() {
	}

	static boolean isSyncStep(List<String> stepText) {
		if (stepText == null) {
			return false;
		}
		String text = String.join(" ", stepText).toLowerCase(Locale.ROOT);
		return text.contains("journal") && text.contains("sync");
	}

	/**
	 * The scroll offset that centres an entry in the quest list, clamped to the scrollable range
	 * (the content height less the viewport).
	 */
	static int scrollToCentre(int entryY, int entryHeight, int viewHeight, int scrollHeight) {
		int max = Math.max(0, scrollHeight - viewHeight);
		return Math.max(0, Math.min(max, entryY + entryHeight / 2 - viewHeight / 2));
	}

	static boolean mayRetry(long now, long lastAttempt) {
		return lastAttempt == 0 || now - lastAttempt >= RETRY_MS;
	}

	/** As above; after the attempts ran out the gap is {@link #GIVE_UP_BACKOFF_MS}. */
	static boolean mayRetry(long now, long lastAttempt, boolean gaveUp) {
		return gaveUp ? lastAttempt == 0 || now - lastAttempt >= GIVE_UP_BACKOFF_MS : mayRetry(now, lastAttempt);
	}
}
