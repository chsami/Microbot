package net.runelite.client.plugins.microbot.util.cache.strategy.entity;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheUpdateStrategy;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

/**
 * Cache update strategy for NPC data.
 * Handles NPC spawn/despawn events to maintain cache consistency.
 */
@Slf4j
public class NpcUpdateStrategy implements CacheUpdateStrategy<Integer, Rs2NpcModel> {
    
    @Override
    public void handleEvent(Object event, net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations<Integer, Rs2NpcModel> cache) {
        if (event instanceof NpcSpawned) {
            handleNpcSpawned((NpcSpawned) event, cache);
        } else if (event instanceof NpcDespawned) {
            handleNpcDespawned((NpcDespawned) event, cache);
        }
    }
    
    private void handleNpcSpawned(NpcSpawned event, net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations<Integer, Rs2NpcModel> cache) {
        NPC npc = event.getNpc();
        if (npc != null) {
            Rs2NpcModel npcModel = new Rs2NpcModel(npc);
            cache.put(npc.getIndex(), npcModel);
            log.trace("Added NPC {} (index: {}) to cache via spawn event", npc.getName(), npc.getIndex());
        }
    }
    
    private void handleNpcDespawned(NpcDespawned event, net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations<Integer, Rs2NpcModel> cache) {
        NPC npc = event.getNpc();
        if (npc != null) {
            cache.remove(npc.getIndex());
            log.trace("Removed NPC {} (index: {}) from cache via despawn event", npc.getName(), npc.getIndex());
        }
    }
    
    @Override
    public Class<?>[] getHandledEventTypes() {
        return new Class<?>[]{NpcSpawned.class, NpcDespawned.class};
    }
    
    @Override
    public void onAttach(net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations<Integer, Rs2NpcModel> cache) {
        log.debug("NpcUpdateStrategy attached to cache");
    }
    
    @Override
    public void onDetach(net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations<Integer, Rs2NpcModel> cache) {
        log.debug("NpcUpdateStrategy detached from cache");
    }
}
