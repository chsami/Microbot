package net.runelite.client.plugins.microbot.quest.logic;

import net.runelite.api.Quest;
import java.util.HashMap;
import java.util.Map;

/**
 * Registry for all quests
 */
public class QuestRegistry {
    private static final Map<Integer, IQuest> QUEST_MAP = new HashMap<>();

    static {
        QUEST_MAP.put(Quest.ROMEO__JULIET.getId(), new RomeoAndJuliet());
        QUEST_MAP.put(Quest.RUNE_MYSTERIES.getId(), new RuneMysteries());
        QUEST_MAP.put(Quest.PIRATES_TREASURE.getId(), new PiratesTreasure());

    }

    /**
     * Get the quest implementation for the given quest id
     * @param questId
     * @return
     */
    public static IQuest getQuest(int questId) {
        IQuest quest = QUEST_MAP.getOrDefault(questId, null);
        return quest;
    }
}
