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

	/** Forgets everything remembered between ticks; called when the quest helper is stopped. */
	void reset();

	/**
	 * True while the preflight, the fare and the supply pass must wait (the generic bank trip would pick a bank the
	 * script must not use, such as the Port Phasmatys bank in Morytania).
	 */
	default boolean deferGates()
	{
		return false;
	}

	/** Items the preflight must find in the inventory, the bank or the Grand Exchange, on top of Quest Helper's own list. */
	default List<QuestPreflight.Need> preflightNeeds()
	{
		return Collections.emptyList();
	}
}
