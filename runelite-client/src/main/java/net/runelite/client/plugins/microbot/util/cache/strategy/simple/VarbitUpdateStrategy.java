package net.runelite.client.plugins.microbot.util.cache.strategy.simple;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.VarbitChanged;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.Rs2NpcCache;
import net.runelite.client.plugins.microbot.util.cache.Rs2ObjectCache;
import net.runelite.client.plugins.microbot.util.cache.model.VarbitData;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheUpdateStrategy;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Cache update strategy for varbit data.
 * Handles VarbitChanged events to update varbit information with enhanced data
 * including temporal tracking, player location, and nearby entity context.
 */
@Slf4j
public class VarbitUpdateStrategy implements CacheUpdateStrategy<Integer, VarbitData> {
    
    @Override
    public void handleEvent(Object event, CacheOperations<Integer, VarbitData> cache) {
        if (event instanceof VarbitChanged) {
            handleVarbitChanged((VarbitChanged) event, cache);
        }
    }
    
    private void handleVarbitChanged(VarbitChanged event, CacheOperations<Integer, VarbitData> cache) {
        try {
            int varbitId = event.getVarbitId();
            if (varbitId != -1) {
                // Get current value from client
                int newValue = Microbot.getClient().getVarbitValue(varbitId);
                
                // Get existing data to preserve previous value
                VarbitData existingData = cache.get(varbitId);
                Integer previousValue = existingData != null ? existingData.getValue() : null;
                
                // Collect contextual information
                WorldPoint finalPlayerLocation = null;
                List<Integer> nearbyNpcIds = null;
                List<Integer> nearbyObjectIds = null;
                
                try {
                    // Get player location
                    if (Microbot.getClient().getLocalPlayer() != null) {
                        finalPlayerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
                    }
                    
                    // Get nearby NPCs within 10 tiles
                    if (finalPlayerLocation != null) {
                        final WorldPoint playerLoc = finalPlayerLocation;
                        nearbyNpcIds = Rs2NpcCache.getInstance().stream()
                            .filter(npc -> npc.getWorldLocation() != null && 
                                          npc.getWorldLocation().distanceTo(playerLoc) <= 10)
                            .map(npc -> npc.getId())
                            .distinct()
                            .collect(Collectors.toList());
                        
                        // Get nearby objects within 10 tiles
                        nearbyObjectIds = Rs2ObjectCache.getInstance().stream()
                            .filter(obj -> obj.getWorldLocation() != null && 
                                          obj.getWorldLocation().distanceTo(playerLoc) <= 10)
                            .map(obj -> obj.getId())
                            .distinct()
                            .collect(Collectors.toList());
                    }
                } catch (Exception e) {
                    log.debug("Could not collect contextual information for varbit {}: {}", varbitId, e.getMessage());
                    // Continue with null values - the VarbitData constructor handles this gracefully
                }
                
                // Create new VarbitData with contextual information
                VarbitData newData = new VarbitData(newValue, previousValue, finalPlayerLocation, nearbyNpcIds, nearbyObjectIds);
                
                // Update the cache
                cache.put(varbitId, newData);
                log.trace("Updated varbit cache: {} = {} (previous: {}) at location: {}", 
                         varbitId, newValue, previousValue, finalPlayerLocation);
            }
        } catch (Exception e) {
            log.error("Error handling VarbitChanged event: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public Class<?>[] getHandledEventTypes() {
        return new Class<?>[]{VarbitChanged.class};
    }
    
    @Override
    public void onAttach(CacheOperations<Integer, VarbitData> cache) {
        log.debug("VarbitUpdateStrategy attached to cache");
    }
    
    @Override
    public void onDetach(CacheOperations<Integer, VarbitData> cache) {
        log.debug("VarbitUpdateStrategy detached from cache");
    }
}
