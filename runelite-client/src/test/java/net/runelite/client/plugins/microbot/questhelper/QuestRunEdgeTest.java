package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class QuestRunEdgeTest {
	@Test
	public void onlyTheRunningToStoppedTransitionCounts() {
		assertTrue(QuestRunEdge.justStopped(true, false));
		assertFalse(QuestRunEdge.justStopped(false, false));
		assertFalse(QuestRunEdge.justStopped(true, true));
		assertFalse(QuestRunEdge.justStopped(false, true));
	}
}
