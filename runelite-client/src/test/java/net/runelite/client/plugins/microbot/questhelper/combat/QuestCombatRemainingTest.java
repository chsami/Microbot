package net.runelite.client.plugins.microbot.questhelper.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.Test;

public class QuestCombatRemainingTest
{
	@Test
	public void valuesFromKeepsKeysAtOrAboveProgress()
	{
		Map<Integer, String> steps = new TreeMap<>();
		steps.put(0, "a");
		steps.put(5, "b");
		steps.put(8, "c");
		List<String> out = QuestCombatDetector.valuesFrom(steps, 5);
		assertEquals(new HashSet<>(Arrays.asList("b", "c")), new HashSet<>(out));
	}

	@Test
	public void valuesFromPastTheLastKeyIsEmpty()
	{
		Map<Integer, String> steps = new TreeMap<>();
		steps.put(1, "a");
		assertTrue(QuestCombatDetector.valuesFrom(steps, 2).isEmpty());
	}

	@Test
	public void protectedItemsAreNeverDeposited()
	{
		assertFalse(QuestCombatHandler.shouldDeposit(true, false, false));
		assertFalse(QuestCombatHandler.shouldDeposit(true, true, true));
	}

	@Test
	public void suppliesStayOnlyWhileFightsRemain()
	{
		assertFalse(QuestCombatHandler.shouldDeposit(false, true, true));
		assertTrue(QuestCombatHandler.shouldDeposit(false, true, false));
	}

	@Test
	public void unrelatedItemsAlwaysGo()
	{
		assertTrue(QuestCombatHandler.shouldDeposit(false, false, true));
		assertTrue(QuestCombatHandler.shouldDeposit(false, false, false));
	}

	@Test
	public void questLevelItemsProtectedOnlyBeforeStartOrWhenUnknown()
	{
		assertTrue(QuestCombatHandler.protectsQuestLevelItems(true, true));
		assertTrue(QuestCombatHandler.protectsQuestLevelItems(false, false));
		assertFalse(QuestCombatHandler.protectsQuestLevelItems(false, true));
	}
}
