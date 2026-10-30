package net.runelite.client.plugins.microbot.questhelper;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.microbot.questhelper.combat.QuestPotions;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import org.junit.Test;

public class QuestPreflightWithdrawTest
{
	private static final int STAMINA_4 = 12625;
	private static final int EXTENDED_2 = 31644;
	private static final int VARROCK_TAB = 8007;
	private static final int COINS = 995;

	private static Map<Integer, Integer> counts(int... idQty)
	{
		Map<Integer, Integer> map = new HashMap<>();
		for (int i = 0; i < idQty.length; i += 2)
		{
			map.put(idQty[i], idQty[i + 1]);
		}
		return map;
	}

	private static QuestPreflight.Need stamina(int qty)
	{
		return QuestPreflight.supplyNeed("Stamina potion", QuestPotions.Kind.STAMINA.doseIds(), qty, true);
	}

	@Test
	public void extendedStaminaOnTheBodyCountsTowardTheKeepAtLeastCount()
	{
		// keep at least 3, one extended potion carried, two plain ones in the bank: withdraw two
		QuestPreflight.WithdrawPlan plan = QuestPreflight.planWithdrawals(Arrays.asList(stamina(3)),
			counts(EXTENDED_2, 1), counts(STAMINA_4, 2), 20, 0, id -> false);
		assertEquals(1, plan.takes.size());
		assertEquals(STAMINA_4, plan.takes.get(0).id);
		assertEquals(2, plan.takes.get(0).quantity);
		assertTrue(plan.leftForSpace.isEmpty() && plan.notInBank.isEmpty());
	}

	@Test
	public void nothingIsWithdrawnWhenAlreadyCarried()
	{
		QuestPreflight.WithdrawPlan plan = QuestPreflight.planWithdrawals(Arrays.asList(stamina(1)),
			counts(EXTENDED_2, 1), counts(STAMINA_4, 5), 20, 0, id -> false);
		assertTrue(plan.takes.isEmpty());
	}

	@Test
	public void stackableTeleportTabsTakeOneSlotAndSpaceIsReportedForTheRest()
	{
		List<QuestPreflight.Need> needs = Arrays.asList(
			QuestPreflight.supplyNeed("Varrock teleport", Arrays.asList(VARROCK_TAB), 3, true), stamina(3));
		// only 1 free slot beyond the 0 reserved: the tab stack takes it, stamina cannot fit
		QuestPreflight.WithdrawPlan plan = QuestPreflight.planWithdrawals(needs, counts(),
			counts(VARROCK_TAB, 10, STAMINA_4, 3), 1, 0, id -> id == VARROCK_TAB);
		assertEquals(1, plan.takes.size());
		assertEquals(3, plan.takes.get(0).quantity);
		assertEquals(Arrays.asList("Stamina potion x3 of 3"), plan.leftForSpace);
	}

	@Test
	public void reservedSlotsForQuestItemsAreNeverUsed()
	{
		QuestPreflight.WithdrawPlan plan = QuestPreflight.planWithdrawals(Arrays.asList(stamina(3)),
			counts(), counts(STAMINA_4, 3), 5, 4, id -> false);
		assertEquals(1, plan.takes.get(0).quantity);
		assertEquals(Arrays.asList("Stamina potion x2 of 3"), plan.leftForSpace);
	}

	@Test
	public void shortBankIsReportedAsNotInBankAndSplitsAcrossDoses()
	{
		QuestPreflight.WithdrawPlan plan = QuestPreflight.planWithdrawals(Arrays.asList(stamina(4)),
			counts(), counts(STAMINA_4, 1, 12627, 1), 20, 0, id -> false);
		assertEquals(2, plan.takes.size());
		assertEquals(Arrays.asList("Stamina potion x2 of 4"), plan.notInBank);
	}

	@Test
	public void extendedStaminaIsRecognisedAsStaminaButNotAsPlain()
	{
		assertTrue(QuestPotions.isExtendedStamina("Extended stamina potion(2)"));
		assertTrue(!QuestPotions.isExtendedStamina("Stamina potion(4)"));
		assertTrue(QuestPotions.isRunEnergyName("Extended stamina potion"));
		assertTrue(QuestPotions.runPotionMatches("Extended stamina potion", "Stamina potion"));
	}
}
