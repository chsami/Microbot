package net.runelite.client.plugins.microbot.questhelper.combat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.Test;

public class QuestCombatFoodFallbackTest
{
	// {id, heal}; names are "f" + id
	private static String best(List<int[]> items, java.util.Set<Integer> keep)
	{
		return QuestCombatHandler.bestFoodName(items, i -> "f" + i[0], i -> i[0], i -> i[1], i -> i[1] > 0, i -> false, keep);
	}

	@Test
	public void keepsChosenWhenBanked()
	{
		assertEquals("Lobster", QuestCombatHandler.chooseFood("Lobster", true, false, "Tuna"));
	}

	@Test
	public void keepsChosenWhenHeld()
	{
		assertEquals("Lobster", QuestCombatHandler.chooseFood("Lobster", false, true, "Tuna"));
	}

	@Test
	public void fallsBackToBestBankFood()
	{
		assertEquals("Tuna", QuestCombatHandler.chooseFood("Lobster", false, false, "Tuna"));
	}

	@Test
	public void noFoodAtAll()
	{
		assertNull(QuestCombatHandler.chooseFood("Lobster", false, false, null));
	}

	@Test
	public void bestFoodPrefersHighestHealAndSkipsProtected()
	{
		List<int[]> items = Arrays.asList(new int[]{1, 10}, new int[]{2, 20}, new int[]{3, 5});
		assertEquals("f2", best(items, Collections.emptySet()));
		assertEquals("f1", best(items, Collections.singleton(2)));
	}

	@Test
	public void defaultFoodUsesOwnedOtherwiseLobster()
	{
		QuestCombatSetupDialog.Context ctx = new QuestCombatSetupDialog.Context();
		assertEquals("Lobster", QuestCombatSetupDialog.defaultFood(ctx));
		ctx.bestOwnedFood = "Tuna";
		assertEquals("Tuna", QuestCombatSetupDialog.defaultFood(ctx));
	}
}
