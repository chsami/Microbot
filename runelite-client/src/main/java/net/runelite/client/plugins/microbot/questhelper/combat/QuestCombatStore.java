package net.runelite.client.plugins.microbot.questhelper.combat;

import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.QuestHelperConfig;

/** Per-quest saved combat loadouts, kept in the questhelper config group as JSON. */
public final class QuestCombatStore
{
	private static final String KEY_PREFIX = "combatLoadout.";

	/** Key suffix of the global "last used" setup; never a quest name. */
	private static final String GLOBAL_KEY = "__last__";

	private QuestCombatStore()
	{
	}

	/** The saved loadout for the quest, or null when none is stored. */
	public static QuestCombatLoadout load(String questKey)
	{
		ConfigManager configManager = Microbot.getConfigManager();
		if (configManager == null)
		{
			return null;
		}
		return QuestCombatLoadout.fromJson(
			configManager.getConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, KEY_PREFIX + questKey));
	}

	/** Stores the loadout for the quest. */
	public static void save(String questKey, QuestCombatLoadout loadout)
	{
		ConfigManager configManager = Microbot.getConfigManager();
		if (configManager != null)
		{
			configManager.setConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, KEY_PREFIX + questKey, loadout.toJson());
		}
	}

	/** The last used setup across quests, or null when none was saved yet. */
	public static QuestCombatLoadout loadGlobal()
	{
		return load(GLOBAL_KEY);
	}

	/** Stores the loadout as the global default, without its quest-specific per-fight rows. */
	public static void saveGlobal(QuestCombatLoadout loadout)
	{
		QuestCombatLoadout copy = QuestCombatLoadout.fromJson(loadout.toJson());
		if (copy == null)
		{
			return;
		}
		copy.setFights(new java.util.LinkedHashMap<>());
		save(GLOBAL_KEY, copy.normalised());
	}

	/** Removes every stored quest loadout. */
	public static void clearAll()
	{
		ConfigManager configManager = Microbot.getConfigManager();
		if (configManager == null)
		{
			return;
		}
		String groupPrefix = QuestHelperConfig.QUEST_HELPER_GROUP + ".";
		for (String fullKey : configManager.getConfigurationKeys(groupPrefix + KEY_PREFIX))
		{
			configManager.unsetConfiguration(QuestHelperConfig.QUEST_HELPER_GROUP, fullKey.substring(groupPrefix.length()));
		}
	}
}
