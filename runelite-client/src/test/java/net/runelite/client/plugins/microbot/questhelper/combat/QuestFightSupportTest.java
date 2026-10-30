package net.runelite.client.plugins.microbot.questhelper.combat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.questhelper.QuestPreflight;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class QuestFightSupportTest
{
	@Test
	public void detectsDragonFights()
	{
		assertTrue(QuestFightSupport.isDragonFight("Elvarg", null));
		assertTrue(QuestFightSupport.isDragonFight("green dragon", null));
		assertTrue(QuestFightSupport.isDragonFight("Boss", Arrays.asList("Kill it. Bring antifire.")));
		assertFalse(QuestFightSupport.isDragonFight("Black Knight", Arrays.asList("Kill the knight.")));
	}

	@Test
	public void antifireNames()
	{
		assertTrue(QuestFightSupport.isAntifire("Extended super antifire(3)"));
		assertTrue(QuestFightSupport.isAntifire("Antifire potion(4)"));
		assertFalse(QuestFightSupport.isAntifire("Prayer potion(4)"));
	}

	@Test
	public void antifireTiming()
	{
		long d = 6 * 60_000L;
		assertTrue(QuestFightSupport.antifireDue(0, 1000, d, false, false));
		assertFalse(QuestFightSupport.antifireDue(0, 1000, d, false, true));
		assertFalse(QuestFightSupport.antifireDue(1000, 2000, d, false, false));
		assertTrue(QuestFightSupport.antifireDue(1000, 20_000, d, true, false));
		assertFalse(QuestFightSupport.antifireDue(1000, 20_000, d, false, false));
		assertTrue(QuestFightSupport.antifireDue(1000, 1000 + d, d, false, false));
	}

	@Test
	public void nearestSafeSpot()
	{
		WorldPoint here = new WorldPoint(10, 10, 0);
		List<WorldPoint> spots = Arrays.asList(new WorldPoint(20, 10, 0), new WorldPoint(12, 10, 0), new WorldPoint(11, 10, 1));
		assertEquals(new WorldPoint(12, 10, 0), QuestFightSupport.nearestSafeSpot(here, spots));
		assertNull(QuestFightSupport.nearestSafeSpot(here, Collections.emptyList()));
		assertTrue(QuestFightSupport.usesSafeSpot(QuestCombatLoadout.Style.RANGED));
		assertFalse(QuestFightSupport.usesSafeSpot(QuestCombatLoadout.Style.MELEE));
	}

	@Test
	public void doseFamilyOrdering()
	{
		List<String> names = Arrays.asList("Prayer potion(2)", "Prayer potion(4)", "Super restore(4)", "Prayer potion(1)");
		List<String> out = ChargedItemNames.ordered(names, s -> 0, s -> s, 0, "Prayer potion");
		assertEquals(Arrays.asList("Prayer potion(4)", "Prayer potion(2)", "Prayer potion(1)"), out);
	}

	@Test
	public void needsAnotherPlayer()
	{
		assertTrue(QuestPreflight.needsAnotherPlayer("SHIELD_OF_ARRAV_PHOENIX_GANG"));
		assertFalse(QuestPreflight.needsAnotherPlayer("COOKS_ASSISTANT"));
		assertTrue(QuestPreflight.anotherPlayerFailure("SHIELD_OF_ARRAV_BLACK_ARM_GANG", "Shield of Arrav")
			.startsWith(QuestPreflight.FAILED_PREFIX));
		assertNull(QuestPreflight.anotherPlayerFailure("COOKS_ASSISTANT", "Cook's Assistant"));
	}

	@Test
	public void questWornSlotBeatsSavedGear()
	{
		java.util.Set<String> quest = new java.util.HashSet<>(Arrays.asList("head", "body"));
		assertTrue(QuestFightSupport.slotConflicts("head", quest));
		assertTrue(QuestFightSupport.slotConflicts("BODY", quest));
		assertFalse(QuestFightSupport.slotConflicts("weapon", quest));
		assertFalse(QuestFightSupport.slotConflicts("", quest));
	}
}
