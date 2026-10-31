package net.runelite.client.plugins.microbot.questhelper.scripted;

import java.util.Collection;
import java.util.EnumMap;
import java.util.Map;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;
import net.runelite.client.plugins.microbot.questhelper.scripted.animalmagnetism.AnimalMagnetismScript;

/** Maps a quest to the script that overrides its generic steps. */
public final class ScriptedQuestRegistry
{
	private static final ScriptedQuestRegistry SHARED = new ScriptedQuestRegistry(new AnimalMagnetismScript());

	private final Map<QuestHelperQuest, ScriptedQuest> scripts = new EnumMap<>(QuestHelperQuest.class);

	public ScriptedQuestRegistry(ScriptedQuest... quests)
	{
		for (ScriptedQuest quest : quests)
		{
			scripts.put(quest.quest(), quest);
		}
	}

	/** The registry the quest script uses. */
	public static ScriptedQuestRegistry shared()
	{
		return SHARED;
	}

	/** The script for {@code quest}, or null when it has none. */
	public ScriptedQuest find(QuestHelperQuest quest)
	{
		return quest == null ? null : scripts.get(quest);
	}

	/**
	 * The script to delegate to: null when scripted quests are switched off in the config or the quest has no script,
	 * so the generic steps run.
	 */
	public ScriptedQuest overrideFor(QuestHelperQuest quest, boolean enabled)
	{
		return enabled ? find(quest) : null;
	}

	public Collection<ScriptedQuest> all()
	{
		return scripts.values();
	}
}
