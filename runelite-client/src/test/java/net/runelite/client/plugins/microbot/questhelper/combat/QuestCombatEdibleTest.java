package net.runelite.client.plugins.microbot.questhelper.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class QuestCombatEdibleTest
{
	private static int pickId(List<int[]> items, Set<Integer> protectedIds)
	{
		int[] picked = QuestCombatHandler.pickEdible(items, i -> i[0], i -> i[1], protectedIds);
		return picked == null ? -1 : picked[0];
	}

	@Test
	public void picksHighestHealing()
	{
		assertEquals(2, pickId(Arrays.asList(new int[]{1, 3}, new int[]{2, 20}, new int[]{3, 5}), Collections.emptySet()));
	}

	@Test
	public void skipsProtectedItems()
	{
		Set<Integer> keep = new HashSet<>(Collections.singletonList(2));
		assertEquals(3, pickId(Arrays.asList(new int[]{1, 3}, new int[]{2, 20}, new int[]{3, 5}), keep));
	}

	@Test
	public void onlyProtectedFoodMeansNoFood()
	{
		assertNull(QuestCombatHandler.pickEdible(Collections.singletonList(new int[]{2, 20}), i -> i[0], i -> i[1],
			Collections.singleton(2)));
	}
}
