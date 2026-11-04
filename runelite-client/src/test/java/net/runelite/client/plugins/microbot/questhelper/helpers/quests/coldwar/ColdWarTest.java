package net.runelite.client.plugins.microbot.questhelper.helpers.quests.coldwar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.requirements.conditional.Conditions;
import net.runelite.client.plugins.microbot.questhelper.steps.ConditionalStep;
import net.runelite.client.plugins.microbot.questhelper.steps.DetailedQuestStep;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ColdWarTest
{
	private ColdWar coldWar;
	private Map<Integer, QuestStep> steps;

	@Before
	public void setUp()
	{
		coldWar = new ColdWar();
		coldWar.setConfig(mock(QuestHelperConfig.class));
		steps = coldWar.loadSteps();
	}

	@Test
	public void returnToZooPenguinUsesTuxedoTimeBeforeEnteringPen()
	{
		List<Map.Entry<Requirement, QuestStep>> entries = entriesOf(steps.get(50));
		assertEquals(3, entries.size());

		assertNull(entries.get(0).getKey());
		assertSame(coldWar.tuxedoTimeZooPenguinReturn, entries.get(0).getValue());

		assertSame(coldWar.isInPenguinPen, entries.get(1).getKey());
		assertSame(coldWar.returnToZooPenguin, entries.get(1).getValue());

		assertSame(coldWar.isPenguin, entries.get(2).getKey());
		assertSame(coldWar.enterPenguinPenZooPenguinReturn, entries.get(2).getValue());

		assertTrue(((DetailedQuestStep) coldWar.tuxedoTimeZooPenguinReturn).getRequirements().contains(coldWar.rawCodOrCharos));
		assertTrue(((DetailedQuestStep) coldWar.enterPenguinPenZooPenguinReturn).getRequirements().contains(coldWar.rawCodOrCharos));
		assertTrue(coldWar.returnToZooPenguin.getSubsteps().contains(coldWar.tuxedoTimeZooPenguinReturn));
		assertTrue(coldWar.returnToZooPenguin.getSubsteps().contains(coldWar.enterPenguinPenZooPenguinReturn));
	}

	@Test
	public void returnToThingUsesTuxedoTimeFirst()
	{
		List<Map.Entry<Requirement, QuestStep>> entries = entriesOf(steps.get(55));
		assertEquals(2, entries.size());

		assertNull(entries.get(0).getKey());
		assertSame(coldWar.tuxedoTimeThingReturn, entries.get(0).getValue());

		assertConditions(entries.get(1).getKey(), coldWar.isPenguin, coldWar.isAtLumbridgeSheepFarm);
		assertSame(coldWar.returnToThing, entries.get(1).getValue());

		assertTrue(coldWar.returnToThing.getSubsteps().contains(coldWar.tuxedoTimeThingReturn));
	}

	@Test
	public void askThingAboutOutpostUsesTuxedoTimeFirst()
	{
		List<Map.Entry<Requirement, QuestStep>> entries = entriesOf(steps.get(65));
		assertEquals(3, entries.size());

		assertNull(entries.get(0).getKey());
		assertSame(coldWar.stealCowbell, entries.get(0).getValue());

		assertConditions(entries.get(1).getKey(), coldWar.cowbell, coldWar.isPenguin, coldWar.isAtLumbridgeSheepFarm);
		assertSame(coldWar.askThingAboutOutpost, entries.get(1).getValue());

		assertSame(coldWar.cowbell, entries.get(2).getKey());
		assertSame(coldWar.tuxedoTimeOutpostInfo, entries.get(2).getValue());

		assertTrue(coldWar.askThingAboutOutpost.getSubsteps().contains(coldWar.tuxedoTimeOutpostInfo));
	}

	private static List<Map.Entry<Requirement, QuestStep>> entriesOf(QuestStep step)
	{
		assertTrue(step instanceof ConditionalStep);
		return new ArrayList<>(((ConditionalStep) step).getStepsMap().entrySet());
	}

	private static void assertConditions(Requirement requirement, Requirement... expected)
	{
		assertTrue(requirement instanceof Conditions);
		List<Requirement> actual = ((Conditions) requirement).getConditions();
		assertEquals(expected.length, actual.size());
		for (int i = 0; i < expected.length; i++)
		{
			assertSame(expected[i], actual.get(i));
		}
	}
}
