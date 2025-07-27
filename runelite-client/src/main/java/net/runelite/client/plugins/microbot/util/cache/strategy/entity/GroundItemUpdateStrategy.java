package net.runelite.client.plugins.microbot.util.cache.strategy.entity;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.TileItem;
import net.runelite.api.events.ItemDespawned;
import net.runelite.api.events.ItemSpawned;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheUpdateStrategy;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItemModel;

/**
 * Cache update strategy for ground item data.
 * Handles automatic cache updates based on ground item spawn/despawn events.
 */
@Slf4j
public class GroundItemUpdateStrategy implements CacheUpdateStrategy<String, Rs2GroundItemModel> {
    
    @Override
    public void handleEvent(Object event, net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations<String, Rs2GroundItemModel> cache) {
        if (event instanceof ItemSpawned) {
            handleItemSpawned((ItemSpawned) event, cache);
        } else if (event instanceof ItemDespawned) {
            handleItemDespawned((ItemDespawned) event, cache);
        }
    }
    
    private void handleItemSpawned(ItemSpawned event, net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations<String, Rs2GroundItemModel> cache) {
        TileItem item = event.getItem();
        if (item != null) {
            String key = generateKey(item, event.getTile().getWorldLocation());
            Rs2GroundItemModel groundItem = new Rs2GroundItemModel(item, event.getTile());
            cache.put(key, groundItem);
            log.trace("Added ground item {} at {} to cache via spawn event", item.getId(), event.getTile().getWorldLocation());
        }
    }
    
    private void handleItemDespawned(ItemDespawned event, net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations<String, Rs2GroundItemModel> cache) {
        TileItem item = event.getItem();
        if (item != null) {
            String key = generateKey(item, event.getTile().getWorldLocation());
            cache.remove(key);
            log.trace("Removed ground item {} at {} from cache via despawn event", item.getId(), event.getTile().getWorldLocation());
        }
    }
    
    /**
     * Generates a unique key for ground items based on item ID, quantity, and location.
     * 
     * @param item The tile item
     * @param location The world location
     * @return Unique key string
     */
    private String generateKey(TileItem item, net.runelite.api.coords.WorldPoint location) {
        return String.format("%d_%d_%d_%d_%d", 
                item.getId(), 
                item.getQuantity(), 
                location.getX(), 
                location.getY(), 
                location.getPlane());
    }
    
    @Override
    public Class<?>[] getHandledEventTypes() {
        return new Class<?>[]{ItemSpawned.class, ItemDespawned.class};
    }
    
    @Override
    public void onAttach(net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations<String, Rs2GroundItemModel> cache) {
        log.debug("GroundItemUpdateStrategy attached to cache");
    }
    
    @Override
    public void onDetach(net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations<String, Rs2GroundItemModel> cache) {
        log.debug("GroundItemUpdateStrategy detached from cache");
    }
}
