package net.runelite.client.plugins.microbot.questhelper.planner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import org.junit.Test;

public class QuestStaminaRulesTest
{
	@Test
	public void takesTheWantedCountWhenThereIsRoom()
	{
		assertEquals(2, QuestStaminaRules.allowed(2, 0, 10, 3, false));
		assertEquals(2, QuestStaminaRules.toWithdraw(2, 0, 10, 3, false));
		assertEquals(1, QuestStaminaRules.toWithdraw(2, 1, 10, 3, false));
		assertEquals(0, QuestStaminaRules.toWithdraw(2, 2, 10, 3, false));
	}

	@Test
	public void neverTakesSlotsReservedForQuestItems()
	{
		assertEquals(1, QuestStaminaRules.allowed(4, 0, 5, 4, false));
		assertEquals(0, QuestStaminaRules.allowed(4, 0, 4, 4, false));
		assertEquals(0, QuestStaminaRules.allowed(4, 0, 2, 9, false));
	}

	@Test
	public void heldStaminasCountAsRoomAndAreDroppedFirstWhenSlotsRunShort()
	{
		assertEquals(1, QuestStaminaRules.allowed(2, 2, 0, 1, false));
		assertTrue(QuestStaminaRules.mustDeposit(2, 2, 0, 1, false));
		assertFalse(QuestStaminaRules.mustDeposit(2, 2, 0, 0, false));
	}

	@Test
	public void restrictedStepsAllowNone()
	{
		assertEquals(0, QuestStaminaRules.allowed(4, 3, 20, 0, true));
		assertTrue(QuestStaminaRules.mustDeposit(4, 3, 20, 0, true));
		assertFalse(QuestStaminaRules.mustDeposit(4, 0, 20, 0, true));
		assertEquals(0, QuestStaminaRules.toWithdraw(4, 0, 20, 0, true));
	}

	@Test
	public void wantedIsCappedAtFour()
	{
		assertEquals(4, QuestStaminaRules.allowed(27, 0, 27, 0, false));
		assertEquals(0, QuestStaminaRules.allowed(-3, 0, 27, 0, false));
	}

	@Test
	public void rewithdrawOnlyWhenABankIsOpenOrOnTheWay()
	{
		assertTrue(QuestStaminaRules.withdrawNow(true, false));
		assertTrue(QuestStaminaRules.withdrawNow(false, true));
		assertFalse(QuestStaminaRules.withdrawNow(false, false));
	}

	@Test
	public void restrictionTextsDecideWhetherStaminasStay()
	{
		assertTrue(QuestRestrictions.restrictsStamina(
			QuestRestrictions.fromTexts(Arrays.asList("You must have an empty inventory")), 20));
		assertTrue(QuestRestrictions.restrictsStamina(
			QuestRestrictions.fromTexts(Arrays.asList("Requires 10 free inventory slots")), 10));
		assertFalse(QuestRestrictions.restrictsStamina(
			QuestRestrictions.fromTexts(Arrays.asList("Requires 10 free inventory slots")), 14));
		assertFalse(QuestRestrictions.restrictsStamina(QuestRestrictions.fromTexts(Arrays.asList("Bring a rope")), 0));
	}
}
