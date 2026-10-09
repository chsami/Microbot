package net.runelite.client.plugins.microbot.questhelper.config;

import java.lang.reflect.Field;
import net.runelite.api.Skill;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;
import net.runelite.client.plugins.microbot.questhelper.helpers.quests.currentaffairs.CurrentAffairs;
import net.runelite.client.plugins.microbot.questhelper.questhelpers.QuestHelper;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class SkillFilteringTest
{
	private ConfigManager configManager;
	private CurrentAffairs helper;

	@Before
	public void setUp() throws Exception
	{
		configManager = mock(ConfigManager.class);
		helper = new CurrentAffairs();
		Field field = QuestHelper.class.getDeclaredField("configManager");
		field.setAccessible(true);
		field.set(helper, configManager);
	}

	@Test
	public void initializesRequirementsBeforeReadingThem()
	{
		assertTrue(SkillFiltering.questPassesSkillFilter(helper));
	}

	@Test
	public void filtersOutQuestRequiringFilteredSkill()
	{
		when(configManager.getConfiguration(QuestHelperConfig.QUEST_BACKGROUND_GROUP, "skillfilter" + Skill.SAILING.getName()))
			.thenReturn("true");
		assertFalse(SkillFiltering.questPassesSkillFilter(helper));
	}
}
