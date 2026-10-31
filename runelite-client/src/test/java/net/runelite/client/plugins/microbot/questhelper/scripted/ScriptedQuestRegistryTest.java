package net.runelite.client.plugins.microbot.questhelper.scripted;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism.AnimalMagnetismScript;
import org.junit.Test;

public class ScriptedQuestRegistryTest
{
	@Test
	public void animalMagnetismIsRegistered()
	{
		ScriptedQuest script = ScriptedQuestRegistry.shared().find(QuestHelperQuest.ANIMAL_MAGNETISM);
		assertNotNull(script);
		assertTrue(script instanceof AnimalMagnetismScript);
		assertEquals(QuestHelperQuest.ANIMAL_MAGNETISM, script.quest());
	}

	@Test
	public void otherQuestsHaveNoScript()
	{
		assertNull(ScriptedQuestRegistry.shared().find(QuestHelperQuest.COOKS_ASSISTANT));
		assertNull(ScriptedQuestRegistry.shared().find(null));
	}

	@Test
	public void overrideOnlyWhenEnabled()
	{
		ScriptedQuestRegistry registry = ScriptedQuestRegistry.shared();
		assertSame(registry.find(QuestHelperQuest.ANIMAL_MAGNETISM),
			registry.overrideFor(QuestHelperQuest.ANIMAL_MAGNETISM, true));
		assertNull(registry.overrideFor(QuestHelperQuest.ANIMAL_MAGNETISM, false));
		assertNull(registry.overrideFor(QuestHelperQuest.COOKS_ASSISTANT, true));
	}

	@Test
	public void configDefaultsToOnAndTheGateFollowsIt()
	{
		QuestHelperConfig config = new QuestHelperConfig()
		{
		};
		assertTrue(config.useScriptedQuests());
		assertNotNull(ScriptedQuestRegistry.shared()
			.overrideFor(QuestHelperQuest.ANIMAL_MAGNETISM, config.useScriptedQuests()));
	}
}
