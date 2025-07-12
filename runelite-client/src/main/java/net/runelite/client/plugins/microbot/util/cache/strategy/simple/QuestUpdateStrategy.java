package net.runelite.client.plugins.microbot.util.cache.strategy.simple;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheUpdateStrategy;

/**
 * Cache update strategy for quest data.
 * Handles VarbitChanged events to update quest state information.
 */
@Slf4j
public class QuestUpdateStrategy implements CacheUpdateStrategy<Quest, QuestState> {
    
    @Override
    public void handleEvent(Object event, CacheOperations<Quest, QuestState> cache) {
        if (event instanceof VarbitChanged) {
            handleVarbitChanged((VarbitChanged) event, cache);
        }
    }
    
    private void handleVarbitChanged(VarbitChanged event, CacheOperations<Quest, QuestState> cache) {
        try {
            // We could be more selective here and only update quests related to the changed varbit,
            // but for simplicity and accuracy, we'll check if any quest might be affected
            // and refresh the currently active quest if we can detect it
            
            // For now, we'll handle this in a simple way - let the quest cache
            // determine which quest to update based on its own logic
            log.trace("VarbitChanged event received, quest cache will handle specific updates");
            
        } catch (Exception e) {
            log.error("Error handling VarbitChanged event for quests: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public Class<?>[] getHandledEventTypes() {
        return new Class<?>[]{VarbitChanged.class};
    }
    
    @Override
    public void onAttach(CacheOperations<Quest, QuestState> cache) {
        log.debug("QuestUpdateStrategy attached to cache");
    }
    
    @Override
    public void onDetach(CacheOperations<Quest, QuestState> cache) {
        log.debug("QuestUpdateStrategy detached from cache");
    }
}
