package net.runelite.client.plugins.microbot.questhelper.helpers.quests.coldwar;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.requirements.Requirement;
import net.runelite.client.plugins.microbot.questhelper.steps.ConditionalStep;
import net.runelite.client.plugins.microbot.questhelper.steps.QuestStep;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class ColdWarTest
{
	@Test
	public void returnToZooPenguinUsesTuxedoTimeBeforeEnteringPen()
	{
		ColdWar coldWar = new ColdWar();
		coldWar.setConfig(mock(QuestHelperConfig.class));
		Map<Integer, QuestStep> steps = coldWar.loadSteps();

		QuestStep step = steps.get(50);
		assertTrue(step instanceof ConditionalStep);

		List<Map.Entry<Requirement, QuestStep>> entries = new ArrayList<>(((ConditionalStep) step).getStepsMap().entrySet());
		assertEquals(3, entries.size());

		assertNull(entries.get(0).getKey());
		assertSame(coldWar.tuxedoTime, entries.get(0).getValue());

		assertSame(coldWar.isInPenguinPen, entries.get(1).getKey());
		assertSame(coldWar.returnToZooPenguin, entries.get(1).getValue());

		assertSame(coldWar.isPenguin, entries.get(2).getKey());
		assertSame(coldWar.enterPenguinPen, entries.get(2).getValue());
	}
}
