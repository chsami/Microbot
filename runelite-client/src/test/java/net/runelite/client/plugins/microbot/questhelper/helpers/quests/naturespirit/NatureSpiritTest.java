package net.runelite.client.plugins.microbot.questhelper.helpers.quests.naturespirit;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.steps.DetailedQuestStep;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;

public class NatureSpiritTest
{
	private NatureSpirit natureSpirit;

	@Before
	public void setUp()
	{
		natureSpirit = new NatureSpirit();
		natureSpirit.setConfig(mock(QuestHelperConfig.class));
		natureSpirit.loadSteps();
	}

	@Test
	public void invisibleGhastStepUsesTheFilledPouch()
	{
		assertEquals(ItemID.DRUID_POUCH, natureSpirit.killGhasts.getIconItemID());
		assertTrue(((DetailedQuestStep) natureSpirit.killGhasts).getRequirements().contains(natureSpirit.druidPouchFull));
	}

	@Test
	public void visibleGhastStepDoesNotNeedTheFilledPouch()
	{
		assertFalse(((DetailedQuestStep) natureSpirit.killGhast).getRequirements().contains(natureSpirit.druidPouchFull));
	}
}
