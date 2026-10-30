package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.Collections;
import org.junit.Test;

public class QuestJournalSyncTest {
	@Test
	public void recognisesTheSyncStepText() {
		assertTrue(QuestJournalSync.isSyncStep(Collections.singletonList("Open the quest's journal to sync your current quest state.")));
		assertTrue(QuestJournalSync.isSyncStep(Arrays.asList("Open the quest's", "JOURNAL to SYNC your state.")));
	}

	@Test
	public void ignoresOrdinaryStepsAndMissingText() {
		assertFalse(QuestJournalSync.isSyncStep(Collections.singletonList("Read the journal of the dead.")));
		assertFalse(QuestJournalSync.isSyncStep(Collections.singletonList("Sync your clocks.")));
		assertFalse(QuestJournalSync.isSyncStep(Collections.emptyList()));
		assertFalse(QuestJournalSync.isSyncStep(null));
	}

	@Test
	public void scrollCentresTheEntryAndStaysInsideTheList() {
		assertEquals(0, QuestJournalSync.scrollToCentre(10, 16, 150, 3000));
		assertEquals(800 + 8 - 75, QuestJournalSync.scrollToCentre(800, 16, 150, 3000));
		assertEquals(2850, QuestJournalSync.scrollToCentre(2990, 16, 150, 3000));
		assertEquals(0, QuestJournalSync.scrollToCentre(500, 16, 150, 100));
	}

	@Test
	public void retriesAreRateLimited() {
		assertTrue(QuestJournalSync.mayRetry(1_000, 0));
		assertFalse(QuestJournalSync.mayRetry(1_000 + QuestJournalSync.RETRY_MS - 1, 1_000));
		assertTrue(QuestJournalSync.mayRetry(1_000 + QuestJournalSync.RETRY_MS, 1_000));
	}

	@Test
	public void afterGivingUpTheSyncRetriesOnALongBackoff() {
		long last = 5_000;
		assertFalse(QuestJournalSync.mayRetry(last + QuestJournalSync.RETRY_MS, last, true));
		assertTrue(QuestJournalSync.mayRetry(last + QuestJournalSync.GIVE_UP_BACKOFF_MS, last, true));
		assertTrue(QuestJournalSync.mayRetry(last + QuestJournalSync.RETRY_MS, last, false));
	}
}
