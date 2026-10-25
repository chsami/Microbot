package net.runelite.client.plugins.microbot.questhelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class QuestPreflightTest
{
	private static QuestPreflight.Need need(String name, int qty, boolean consumed, Integer... ids)
	{
		return new QuestPreflight.Need(name, Arrays.asList(ids), qty, consumed);
	}

	private static Map<Integer, Integer> owned(int... idQty)
	{
		Map<Integer, Integer> map = new HashMap<>();
		for (int i = 0; i < idQty.length; i += 2)
		{
			map.put(idQty[i], idQty[i + 1]);
		}
		return map;
	}

	@Test
	public void notedStockIsFoldedOncePerIdAcrossDuplicateNeeds()
	{
		Map<Integer, Integer> onPlayer = owned(1512, 5);
		Map<Integer, Integer> owned = owned(1512, 5);
		List<QuestPreflight.Need> needs = Arrays.asList(need("Logs", 5, true, 1511), need("Logs", 3, true, 1511));
		QuestPreflight.foldNoted(needs, id -> id == 1511 ? 1512 : -1, onPlayer, owned);
		assertEquals(5, (int) onPlayer.get(1511));
		assertEquals(5, (int) owned.get(1511));
		assertEquals(0, QuestPreflight.compute(needs, owned).get(0).missing);
	}

	@Test
	public void reportsOnlyTheShortfall()
	{
		List<QuestPreflight.Shortfall> out = QuestPreflight.compute(
			Collections.singletonList(need("Air rune", 6, true, 556)), owned(556, 2));
		assertEquals(1, out.size());
		assertEquals(4, out.get(0).missing);
		assertEquals(2, out.get(0).owned);
	}

	@Test
	public void ownedEnoughMeansNothingMissing()
	{
		List<QuestPreflight.Shortfall> out = QuestPreflight.compute(
			Collections.singletonList(need("Rope", 1, false, 954)), owned(954, 3));
		assertEquals(0, out.get(0).missing);
	}

	@Test
	public void duplicatesTakeTheMaxNotTheSum()
	{
		List<QuestPreflight.Shortfall> out = QuestPreflight.compute(Arrays.asList(
			need("Air rune", 6, true, 556), need("Air rune", 10, true, 556), need("Air rune", 4, false, 556)),
			owned());
		assertEquals(1, out.size());
		assertEquals(10, out.get(0).needed);
		assertEquals(10, out.get(0).missing);
	}

	@Test
	public void alternatesCountTowardOwnership()
	{
		List<QuestPreflight.Shortfall> out = QuestPreflight.compute(
			Collections.singletonList(need("Pickaxe", 1, false, 1265, 1267, 1269)), owned(1267, 1));
		assertEquals(0, out.get(0).missing);
	}

	@Test
	public void alternatesAreSummedAcrossIds()
	{
		List<QuestPreflight.Shortfall> out = QuestPreflight.compute(
			Collections.singletonList(need("Logs", 5, true, 1511, 1521)), owned(1511, 2, 1521, 1));
		assertEquals(3, out.get(0).owned);
		assertEquals(2, out.get(0).missing);
	}

	@Test
	public void requirementsSharingAnAlternateMergeIntoOne()
	{
		List<QuestPreflight.Shortfall> out = QuestPreflight.compute(Arrays.asList(
			need("Bronze axe", 1, false, 1351, 1349), need("Iron axe", 1, false, 1349, 1353)), owned());
		assertEquals(1, out.size());
		assertEquals(new java.util.HashSet<>(Arrays.asList(1351, 1349, 1353)), new java.util.HashSet<>(out.get(0).ids));
		assertEquals(1, out.get(0).missing);
	}

	@Test
	public void unrelatedItemsStaySeparate()
	{
		List<QuestPreflight.Shortfall> out = QuestPreflight.compute(Arrays.asList(
			need("Rope", 1, false, 954), need("Bucket", 2, true, 1925)), owned(954, 1));
		assertEquals(2, out.size());
		assertEquals(0, out.get(0).missing);
		assertEquals(2, out.get(1).missing);
	}

	@Test
	public void ignoresEmptyAndPlaceholderRequirements()
	{
		List<QuestPreflight.Shortfall> out = QuestPreflight.compute(Arrays.asList(
			need("Weapon and armour", 1, false, -1), need("Nothing", 0, true, 954), need("Null ids", 1, true)),
			owned());
		assertTrue(out.isEmpty());
	}
}
