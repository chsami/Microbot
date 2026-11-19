package net.runelite.client.plugins.microbot.questhelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class QuestScriptGroundPickupTest
{
	private static final WorldPoint FISH_FOOD_SPAWN = new WorldPoint(3108, 3356, 1);

	private final ItemRequirement fishFood = new ItemRequirement("Fish food", ItemID.FISH_FOOD);
	private final ItemRequirement poison = new ItemRequirement("Poison", ItemID.POISON);

	@Test
	public void tradableItemLyingAtThePickupStepIsLootedInsteadOfBanked()
	{
		List<Requirement> stepRequirements = Collections.singletonList(fishFood);

		assertSame(fishFood, QuestScript.stepGroundPickup(false, FISH_FOOD_SPAWN, stepRequirements,
			Collections.singletonList(fishFood), (item, point) -> point.equals(FISH_FOOD_SPAWN)));
	}

	@Test
	public void itemNotOnTheGroundStillGoesToTheBank()
	{
		List<Requirement> stepRequirements = Collections.singletonList(fishFood);

		assertNull(QuestScript.stepGroundPickup(false, FISH_FOOD_SPAWN, stepRequirements,
			Collections.singletonList(fishFood), (item, point) -> false));
	}

	@Test
	public void questLevelItemThatIsNotTheStepsOwnIsNotTreatedAsAPickup()
	{
		List<Requirement> stepRequirements = Collections.singletonList(fishFood);

		assertNull(QuestScript.stepGroundPickup(false, FISH_FOOD_SPAWN, stepRequirements,
			Collections.singletonList(poison), (item, point) -> true));
	}

	@Test
	public void picksTheMissingItemThatIsActuallyOnTheGround()
	{
		List<Requirement> stepRequirements = Arrays.asList(fishFood, poison);

		assertSame(poison, QuestScript.stepGroundPickup(false, FISH_FOOD_SPAWN, stepRequirements,
			Arrays.asList(fishFood, poison), (item, point) -> item == poison));
	}

	@Test
	public void npcAndObjectStepsKeepTheirOwnHandling()
	{
		List<Requirement> stepRequirements = Collections.singletonList(fishFood);

		assertNull(QuestScript.stepGroundPickup(true, FISH_FOOD_SPAWN, stepRequirements,
			Collections.singletonList(fishFood), (item, point) -> true));
	}

	@Test
	public void stepWithoutATileIsNotAPickup()
	{
		List<Requirement> stepRequirements = Collections.singletonList(fishFood);

		assertNull(QuestScript.stepGroundPickup(false, null, stepRequirements,
			Collections.singletonList(fishFood), (item, point) -> true));
	}

	@Test
	public void missingStepItemIsARespawnCandidateEvenWhenNothingIsOnTheGround()
	{
		List<Requirement> stepRequirements = Collections.singletonList(fishFood);

		assertSame(fishFood, QuestScript.stepGroundPickup(false, FISH_FOOD_SPAWN, stepRequirements,
			Arrays.asList(poison, fishFood), (item, point) -> true));
	}

	@Test
	public void unreachableBankStopsOnlyAfterThreeAttemptsSpanningTheMinimumTime()
	{
		QuestScript script = new QuestScript();
		long span = QuestScript.BANK_UNREACHABLE_MIN_SPAN_MS;

		assertFalse(script.noteBankUnreachable(1_000, span));
		assertFalse(script.noteBankUnreachable(1_500, span));
		assertFalse(script.noteBankUnreachable(2_000, span));
		assertFalse(script.noteBankUnreachable(1_000 + span - 1, span));
		assertTrue(script.noteBankUnreachable(1_000 + span, span));
		assertFalse(script.noteBankUnreachable(2_000 + span, span));
	}

	@Test
	public void unreachableBankNeedsThreeAttemptsEvenWhenTheyAreFarApart()
	{
		QuestScript script = new QuestScript();
		long span = QuestScript.BANK_UNREACHABLE_MIN_SPAN_MS;

		assertFalse(script.noteBankUnreachable(1_000, span));
		assertFalse(script.noteBankUnreachable(1_000 + span * 2, span));
		assertTrue(script.noteBankUnreachable(1_000 + span * 3, span));
	}

	@Test
	public void clearingTheUnreachableBankStreakStartsItOver()
	{
		QuestScript script = new QuestScript();
		long span = QuestScript.BANK_UNREACHABLE_MIN_SPAN_MS;

		assertFalse(script.noteBankUnreachable(1_000, span));
		assertFalse(script.noteBankUnreachable(2_000, span));
		script.clearBankUnreachable();

		assertFalse(script.noteBankUnreachable(1_000 + span, span));
		assertFalse(script.noteBankUnreachable(2_000 + span, span));
		assertFalse(script.noteBankUnreachable(3_000 + span, span));
		assertTrue(script.noteBankUnreachable(1_000 + span * 2, span));
	}

	@Test
	public void emptySpawnIsWaitedOnLongerThanAPlainUnreachableBank()
	{
		QuestScript script = new QuestScript();
		long wait = QuestScript.PICKUP_RESPAWN_WAIT_MS;

		assertTrue(wait > QuestScript.BANK_UNREACHABLE_MIN_SPAN_MS);
		assertFalse(script.noteBankUnreachable(1_000, wait));
		assertFalse(script.noteBankUnreachable(1_000 + QuestScript.BANK_UNREACHABLE_MIN_SPAN_MS, wait));
		assertFalse(script.noteBankUnreachable(1_000 + wait - 1, wait));
		assertTrue(script.noteBankUnreachable(1_000 + wait, wait));
	}

	@Test
	public void unreachableBankReasonNamesTheMissingItems()
	{
		String reason = QuestScript.bankUnreachableReason(Arrays.asList(fishFood, poison));

		assertTrue(reason, reason.contains("can't find a route to a bank from here to get: Fish food, Poison."));
		assertTrue(reason, reason.contains("leave this area"));
	}

	@Test
	public void unseenPickupReasonIsTrueForSpawnsAndForItemsThePlayerMustBring()
	{
		String reason = QuestScript.pickupUnavailableReason(fishFood);

		assertEquals("Can't see Fish food on the ground nearby and no bank can be reached from here. "
				+ "If it spawns here, wait for it to respawn or hop worlds; "
				+ "otherwise get it yourself or leave this area. Then turn the quest helper back on.", reason);
	}

	@Test
	public void unreachableBankStatusSaysWhatTheHelperIsLookingFor()
	{
		assertEquals("Looking for Fish food nearby (no bank reachable from here)",
				QuestScript.bankUnreachableStatus(fishFood));
		assertEquals("Looking for a route to a bank for missing items",
				QuestScript.bankUnreachableStatus(null));
	}

	@Test
	public void bankLookupIsSpacedOutWhileNoBankIsReachable()
	{
		QuestScript script = new QuestScript();
		long wait = QuestScript.PICKUP_RESPAWN_WAIT_MS;

		assertFalse(script.bankUnreachableStreakRunning());
		assertTrue(script.bankLookupDue(1_000));

		script.noteBankUnreachable(1_000, wait);

		assertTrue(script.bankUnreachableStreakRunning());
		assertFalse(script.bankLookupDue(1_000 + QuestScript.BANK_LOOKUP_RETRY_MS - 1));
		assertTrue(script.bankLookupDue(1_000 + QuestScript.BANK_LOOKUP_RETRY_MS));

		script.clearBankUnreachable();

		assertFalse(script.bankUnreachableStreakRunning());
		assertTrue(script.bankLookupDue(1_001));
	}
}
