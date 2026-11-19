package net.runelite.client.plugins.microbot.questhelper;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.ItemID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.item.ItemRequirement;
import org.junit.Test;

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
	public void unreachableBankStopsOnTheThirdAttemptInARow()
	{
		QuestScript script = new QuestScript();

		assertFalse(script.noteBankUnreachable());
		assertFalse(script.noteBankUnreachable());
		assertTrue(script.noteBankUnreachable());
		assertFalse(script.noteBankUnreachable());
	}

	@Test
	public void unreachableBankReasonNamesTheMissingItems()
	{
		String reason = QuestScript.bankUnreachableReason(Arrays.asList(fishFood, poison));

		assertTrue(reason, reason.contains("No bank can be reached from here"));
		assertTrue(reason, reason.contains("(Fish food, Poison)"));
	}
}
