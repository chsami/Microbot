package net.runelite.client.plugins.microbot.questhelper.scripted;

import java.util.Collections;
import java.util.List;
import net.runelite.client.plugins.microbot.questhelper.QuestPreflight;
import net.runelite.client.plugins.microbot.questhelper.questinfo.QuestHelperQuest;

/**
 * A quest with its own hand-written script. While the script is enabled and registered it replaces Quest Helper's
 * generic step execution for that quest; the quest script still runs the gates, the preflight, the supplies and the
 * stamina handling around it.
 */
public interface ScriptedQuest
{
	/** The quest this script plays. */
	QuestHelperQuest quest();

	/**
	 * One step of the quest, called on the quest thread once per main tick. Must not block for long: one game action
	 * (or one bounded wait) per call.
	 *
	 * @return true when the tick was used; false hands this tick to the generic Quest Helper steps
	 */
	boolean tick(ScriptedQuestHost host);

	/** Items the preflight must find in the inventory, the bank or the Grand Exchange, on top of Quest Helper's own list. */
	default List<QuestPreflight.Need> preflightNeeds()
	{
		return Collections.emptyList();
	}
}
