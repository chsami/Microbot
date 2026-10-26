package net.runelite.client.plugins.microbot.questhelper.planner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public class QuestQueueTest
{
	private static final class MemoryStore implements QuestQueue.Store
	{
		String json;

		@Override
		public String read()
		{
			return json;
		}

		@Override
		public void write(String value)
		{
			json = value;
		}
	}

	private static QuestProfile profile(String name, QuestProfile.Type type)
	{
		return QuestProfile.defaultFor(name, type == QuestProfile.Type.COMBAT);
	}

	@Test
	public void queueSurvivesARestartInOrderWithItsProfiles()
	{
		MemoryStore store = new MemoryStore();
		QuestQueue queue = new QuestQueue(store);
		QuestProfile a = profile("Cooks Assistant", QuestProfile.Type.TRAVEL);
		a.setObtain(QuestProfile.Obtain.BANK_ONLY);
		a.getSupplies().setStaminaQty(3);
		queue.setEntries(Arrays.asList(a, profile("Imp Catcher", QuestProfile.Type.COMBAT)));
		queue.setMode(QuestQueue.Mode.RANDOM);
		queue.setPause(2, 7);
		queue.setDefaultMinStamina(3);
		queue.markSkipped("Dragon Slayer I");

		QuestQueue reloaded = new QuestQueue(store);
		reloaded.load();
		assertEquals(2, reloaded.entries().size());
		assertEquals("Cooks Assistant", reloaded.entries().get(0).getQuestName());
		assertEquals(QuestProfile.Obtain.BANK_ONLY, reloaded.entries().get(0).getObtain());
		assertEquals(3, reloaded.entries().get(0).getSupplies().getStaminaQty());
		assertEquals(QuestProfile.Type.COMBAT, reloaded.entries().get(1).getType());
		assertEquals(QuestQueue.Mode.RANDOM, reloaded.mode());
		assertEquals(2, reloaded.pauseMinMinutes());
		assertEquals(7, reloaded.pauseMaxMinutes());
		assertEquals(3, reloaded.defaultMinStamina());
		assertTrue(reloaded.skipped().contains("Dragon Slayer I"));
	}

	@Test
	public void corruptOrMissingJsonGivesAnEmptyQueue()
	{
		MemoryStore store = new MemoryStore();
		store.json = "{not json";
		QuestQueue queue = new QuestQueue(store);
		queue.load();
		assertTrue(queue.entries().isEmpty());
		assertFalse(queue.isActive());
	}

	@Test
	public void fixedQueueAdvancesThenStops()
	{
		QuestQueue queue = new QuestQueue(new MemoryStore());
		queue.setEntries(Arrays.asList(profile("A", QuestProfile.Type.TRAVEL), profile("B", QuestProfile.Type.TRAVEL)));
		assertFalse(queue.isActive());
		assertTrue(queue.start());
		assertEquals("A", queue.current().getQuestName());
		assertEquals("B", queue.advance().getQuestName());
		assertNull(queue.advance());
		assertFalse(queue.isActive());
	}

	@Test
	public void emptyFixedQueueDoesNotStartButRandomDoes()
	{
		QuestQueue queue = new QuestQueue(new MemoryStore());
		assertFalse(queue.start());
		queue.setMode(QuestQueue.Mode.RANDOM);
		assertTrue(queue.start());
		assertNull(queue.current());
		queue.setRandomCurrent(profile("X", QuestProfile.Type.TRAVEL));
		assertEquals("X", queue.current().getQuestName());
		queue.advance();
		assertNull(queue.current());
		assertTrue(queue.isActive());
	}

	@Test
	public void activeStateAndCursorArePersisted()
	{
		MemoryStore store = new MemoryStore();
		QuestQueue queue = new QuestQueue(store);
		queue.setEntries(Arrays.asList(profile("A", QuestProfile.Type.TRAVEL), profile("B", QuestProfile.Type.TRAVEL)));
		queue.start();
		queue.advance();
		QuestQueue reloaded = new QuestQueue(store);
		reloaded.load();
		assertTrue(reloaded.isActive());
		assertEquals("B", reloaded.current().getQuestName());
	}

	@Test
	public void savedProfileWinsOverTheSuggestedDefault()
	{
		QuestQueue queue = new QuestQueue(new MemoryStore());
		assertEquals(QuestProfile.Type.COMBAT, queue.profileOrDefault("Q", true).getType());
		QuestProfile saved = profile("Q", QuestProfile.Type.TRAVEL);
		saved.setObtain(QuestProfile.Obtain.BANK_ONLY);
		queue.saveProfile(saved);
		QuestProfile got = queue.profileOrDefault("Q", true);
		assertEquals(QuestProfile.Type.TRAVEL, got.getType());
		assertEquals(QuestProfile.Obtain.BANK_ONLY, got.getObtain());
	}

	@Test
	public void moveReordersWithinBounds()
	{
		List<String> list = new ArrayList<>(Arrays.asList("a", "b", "c"));
		assertTrue(QuestQueue.move(list, 2, -1));
		assertEquals(Arrays.asList("a", "c", "b"), list);
		assertFalse(QuestQueue.move(list, 0, -1));
		assertFalse(QuestQueue.move(list, 2, 1));
	}
}
