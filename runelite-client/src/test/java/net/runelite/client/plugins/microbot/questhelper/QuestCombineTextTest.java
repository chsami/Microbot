package net.runelite.client.plugins.microbot.questhelper;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public class QuestCombineTextTest
{
	@Test
	public void combineStepsAreRecognised()
	{
		assertTrue(QuestCombineText.isCombineText(List.of("Use the three map parts together to repair the map.")));
		assertTrue(QuestCombineText.isCombineText(List.of("Combine the two halves of the key.")));
		assertTrue(QuestCombineText.isCombineText(List.of("Put the pieces together.")));
	}

	@Test
	public void otherUseStepsAreNot()
	{
		assertFalse(QuestCombineText.isCombineText(List.of("Use the silk on the magic door.")));
		assertFalse(QuestCombineText.isCombineText(List.of("Talk to Klarense and buy his boat.")));
		assertFalse(QuestCombineText.isCombineText(null));
	}
}
