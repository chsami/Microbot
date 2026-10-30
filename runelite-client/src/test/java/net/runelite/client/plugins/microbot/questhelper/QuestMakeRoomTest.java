package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.runelite.client.plugins.microbot.questhelper.QuestMakeRoom.Action;
import net.runelite.client.plugins.microbot.questhelper.QuestMakeRoom.Slot;
import org.junit.Test;

/** Rag and Bone Man I: a full inventory (8 pots, 8 logs, 5 lobsters, 6 bones, coins) and a bone to pick up. */
public class QuestMakeRoomTest
{
	private static List<Slot> liveInventory()
	{
		List<Slot> slots = new ArrayList<>();
		slots.add(new Slot(1931, "Pot", 1, false, true));
		slots.add(new Slot(1511, "Logs", 40, false, true));
		slots.add(new Slot(379, "Lobster", 150, true, false));
		slots.add(new Slot(526, "Bones", 60, false, true));
		slots.add(new Slot(995, "Coins", 28, false, true));
		return slots;
	}

	@Test
	public void aFreeSlotNeedsNothing()
	{
		assertEquals(Action.NONE, QuestMakeRoom.decide(1, true, liveInventory()).action);
	}

	@Test
	public void hurtAndCarryingFoodMeansEating()
	{
		QuestMakeRoom.Decision decision = QuestMakeRoom.decide(0, true, liveInventory());
		assertEquals(Action.EAT, decision.action);
		assertEquals("Lobster", decision.slot.name);
	}

	@Test
	public void atFullHealthTheCheapestUnneededItemIsDropped()
	{
		List<Slot> items = liveInventory();
		items.add(new Slot(1925, "Bucket", 2, false, false));
		QuestMakeRoom.Decision decision = QuestMakeRoom.decide(0, false, items);
		assertEquals(Action.DROP, decision.action);
		assertEquals("Bucket", decision.slot.name);
	}

	@Test
	public void theFoodGoesWhenItIsTheOnlyThingNotNeeded()
	{
		QuestMakeRoom.Decision decision = QuestMakeRoom.decide(0, false, liveInventory());
		assertEquals(Action.DROP, decision.action);
		assertEquals("Lobster", decision.slot.name);
	}

	@Test
	public void questItemsAreNeverDroppedEvenWhenCheap()
	{
		List<Slot> onlyNeeded = Arrays.asList(
			new Slot(1931, "Pot", 1, false, true), new Slot(1511, "Logs", 40, false, true));
		QuestMakeRoom.Decision decision = QuestMakeRoom.decide(0, false, onlyNeeded);
		assertEquals(Action.STUCK, decision.action);
		assertNull(decision.slot);
		assertTrue(QuestMakeRoom.decide(0, true, onlyNeeded).action == Action.STUCK);
	}

	@Test
	public void killTargetsInSightAreAttackedAtOnceWithinTwelveTiles()
	{
		assertTrue(KillStepRule.attackAtOnce(11, true));
		assertTrue(KillStepRule.attackAtOnce(KillStepRule.ATTACK_AT_ONCE_TILES, true));
		assertEquals(false, KillStepRule.attackAtOnce(13, true));
		assertEquals(false, KillStepRule.attackAtOnce(3, false));
	}
}
