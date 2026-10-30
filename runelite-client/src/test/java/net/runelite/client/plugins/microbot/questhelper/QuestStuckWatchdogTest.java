package net.runelite.client.plugins.microbot.questhelper;

import net.runelite.client.plugins.microbot.questhelper.QuestStuckWatchdog.Stage;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class QuestStuckWatchdogTest
{
	private static final long I = 60_000L;

	@Test
	public void escalatesOneStagePerIntervalUntilGiveUp()
	{
		QuestStuckWatchdog w = new QuestStuckWatchdog(I);
		assertNull(w.tick(0, 1L, false));
		assertNull(w.tick(I - 1, 1L, false));
		assertEquals(Stage.REPLAN, w.tick(I, 1L, false));
		assertNull(w.tick(I + 1, 1L, false));
		assertEquals(Stage.BLACKLIST, w.tick(2 * I, 1L, false));
		assertEquals(Stage.PLANE_ESCAPE, w.tick(3 * I, 1L, false));
		assertEquals(Stage.TELEPORT, w.tick(4 * I, 1L, false));
		assertEquals(Stage.GIVE_UP, w.tick(5 * I, 1L, false));
		assertNull(w.tick(9 * I, 1L, false));
	}

	@Test
	public void lateTickStillAdvancesOnlyOneStage()
	{
		QuestStuckWatchdog w = new QuestStuckWatchdog(I);
		w.tick(0, 1L, false);
		assertEquals(Stage.REPLAN, w.tick(3 * I, 1L, false));
		assertEquals(Stage.BLACKLIST, w.tick(3 * I + 1, 1L, false));
	}

	@Test
	public void progressResetsStage()
	{
		QuestStuckWatchdog w = new QuestStuckWatchdog(I);
		w.tick(0, 1L, false);
		w.tick(I, 1L, false);
		assertEquals(Stage.REPLAN, w.current());
		assertNull(w.tick(I + 10, 2L, false));
		assertEquals(Stage.NONE, w.current());
		assertNull(w.tick(2 * I, 2L, false));
		assertEquals(Stage.REPLAN, w.tick(I + 10 + I, 2L, false));
	}

	@Test
	public void suppressedTicksDoNotCount()
	{
		QuestStuckWatchdog w = new QuestStuckWatchdog(I);
		w.tick(0, 1L, false);
		assertNull(w.tick(I, 1L, true));
		assertNull(w.tick(I + 1, 1L, false));
		assertNull(w.tick(2 * I - 1, 1L, false));
		assertEquals(Stage.REPLAN, w.tick(2 * I, 1L, false));
	}
}
