package net.runelite.client.plugins.microbot.util.cache.strategy.farming;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;

import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.util.cache.model.SpiritTreeData;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheUpdateStrategy;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2ObjectCacheUtils;
import net.runelite.client.plugins.microbot.util.farming.SpiritTree;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2ObjectModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Arrays;
import java.util.List;

/**
 * Cache update strategy for spirit tree farming data.
 * Handles WidgetLoaded, VarbitChanged, and GameObjectSpawned events to detect spirit tree states
 * and travel availability with enhanced contextual information.
 */
@Slf4j
public class SpiritTreeUpdateStrategy implements CacheUpdateStrategy<SpiritTree, SpiritTreeData> {
    
    // Widget constants for spirit tree detection
    private static final int ADVENTURE_LOG_GROUP_ID = 187;
    private static final int ADVENTURE_LOG_CONTAINER_CHILD = 0;
    private static final String SPIRIT_TREE_WIDGET_TITLE = SpiritTree.SPIRIT_TREE_WIDGET_TITLE;
    
    // Spirit tree object IDs for game object detection
    private static final List<Integer> SPIRIT_TREE_OBJECT_IDS = Arrays.asList(
        ObjectID.SPIRIT_TREE_FULLYGROWN,  // Standard spirit spiritTree id when fully grown  and available for travel -> healty )         
        ObjectID.SPIRITTREE_PRIF, // Prifddinas spirit tree  
        ObjectID.POG_SPIRIT_TREE_ALIVE_STATIC,  // Poison Waste spirit tree
        ObjectID.SPIRITTREE_SMALL, // Small spirit tree in  grand Exchange 1295
        ObjectID.ENT, // for the "great" trees in tree gnome village 1293
        ObjectID.STRONGHOLD_ENT // nd tree gnome stronghold 1294
    );
    
    // Spirit tree specific farming transmit varbits
    private static final List<Integer> SPIRIT_TREE_VARBIT_IDS = Arrays.asList(
        VarbitID.FARMING_TRANSMIT_A, // 4771 - Port Sarim and Farming Guild 
        VarbitID.FARMING_TRANSMIT_B, // 4772 - Etceteria and Brimhaven patches
        VarbitID.FARMING_TRANSMIT_F  // 7904 - Hosidius
    );
    
    @Override
    public void handleEvent(Object event, CacheOperations<SpiritTree, SpiritTreeData> cache) {
        try {
            
            if (event instanceof WidgetLoaded) {
                
                handleWidgetLoaded((WidgetLoaded) event, cache);
            } else if (event instanceof VarbitChanged) {
                handleVarbitChanged((VarbitChanged) event, cache);
            } else if (event instanceof GameObjectSpawned) {
                handleGameObjectSpawned((GameObjectSpawned) event, cache);
            }
        } catch (Exception e) {
            log.error("Error handling event in SpiritTreeUpdateStrategy: {}", e.getMessage(), e);
        }
    }
    
    @Override
    public Class<?>[] getHandledEventTypes() {
        return new Class<?>[]{WidgetLoaded.class, VarbitChanged.class, GameObjectSpawned.class};
    }
    
    /**
     * Handle widget loaded events to detect spirit tree widget opening
     */
    private void handleWidgetLoaded(WidgetLoaded event, CacheOperations<SpiritTree, SpiritTreeData> cache) {
        

        // Check if this is the adventure log widget with spirit tree locations
        if (event.getGroupId() == ADVENTURE_LOG_GROUP_ID) {            
            // Small delay to ensure widget is fully loaded
            Microbot.getClientThread().invokeLater(() -> {
                try {                    
                    if(Rs2Widget.isHidden(ADVENTURE_LOG_GROUP_ID, ADVENTURE_LOG_CONTAINER_CHILD)) {
                        log.debug("Adventure log widget not found, skipping spirit tree update");
                        return;
                    }
                    Widget titleWidget = Rs2Widget.getWidget(ADVENTURE_LOG_GROUP_ID, ADVENTURE_LOG_CONTAINER_CHILD);
                    if (titleWidget == null || titleWidget.isHidden()) {
                        log.debug("Adventure log widget not found, skipping spirit tree update");
                        return;
                    }
                    log.info("Adventure log widget loaded (group {}), checking for spirit tree locations", event.getGroupId());
                    boolean hasRightTitle= Rs2Widget.hasWidgetText(SPIRIT_TREE_WIDGET_TITLE,
                                                            ADVENTURE_LOG_GROUP_ID,
                                                                    ADVENTURE_LOG_CONTAINER_CHILD,
                                                                     false);                    
                            
                    if (hasRightTitle) {
                        log.info("Spirit tree locations widget detected, updating cache from widget data");
                        updateCacheFromWidget(cache);
                    }
                } catch (Exception e) {
                    log.debug("Error checking spirit tree widget: {}", e.getMessage());
                }
            });
        }
    }
    
    /**
     * Handle varbit changed events for spirit tree farming transmit varbits
     */
    private void handleVarbitChanged(VarbitChanged event, CacheOperations<SpiritTree, SpiritTreeData> cache) {
        int varbitId = event.getVarbitId();
        
        // Check if this is a spirit tree farming transmit varbit
        if (SPIRIT_TREE_VARBIT_IDS.contains(varbitId)) {
            log.debug("Spirit tree farming varbit {} changed to value {}, updating spirit tree farming states", varbitId, event.getValue());
            
            // Update farming states for all farmable spirit tree patches
            updateFarmingStatesFromVarbits(cache);
        }
    }
    
    /**
     * Handle game object spawned events for spirit tree objects
     */
    private void handleGameObjectSpawned(GameObjectSpawned event, CacheOperations<SpiritTree, SpiritTreeData> cache) {
        int objectId = event.getGameObject().getId();
        
        // Check if this is a spirit tree object
        if (SPIRIT_TREE_OBJECT_IDS.contains(objectId)) {
            WorldPoint objectLocation = event.getGameObject().getWorldLocation();
            log.info("Spirit tree object {} spawned at {}", objectId, objectLocation);
            
            // Find the corresponding spirit tree
            SpiritTree matchingPatch = findPatchByLocation(objectLocation);
            if (matchingPatch != null && matchingPatch.getType() == SpiritTree.SpiritTreeType.FARMABLE) {
                updatePatchFromGameObject(cache, matchingPatch, event.getGameObject());
            }else if (matchingPatch != null) {
                log.debug("Spirit tree object {} spawned but not farmable, skipping update", matchingPatch.name());
            }
        }
    }
    
    /**
     * Update cache from spirit tree widget data
     */
    private void updateCacheFromWidget(CacheOperations<SpiritTree, SpiritTreeData> cache) {
        try {
            // Extract available destinations from the widget
            List<SpiritTree> availableSpiritTrees = SpiritTree.extractAvailableFromWidget();
            
            WorldPoint playerLocation = getPlayerLocation();
            Integer farmingLevel = getFarmingLevel();
            
            log.debug("Widget extraction found {} available spirit tree destinations", availableSpiritTrees.size());
            
            // Update cache for all available destinations
            for (SpiritTree spiritTree : availableSpiritTrees) {
                SpiritTreeData existingData = cache.get(spiritTree);
                
                // Create new data with widget detection
                SpiritTreeData newData = new SpiritTreeData(
                    spiritTree,
                    existingData != null ? existingData.getCropState() : null, // Preserve crop state
                    true && spiritTree.hasQuestRequirements(), // Available for travel (detected in widget)
                    playerLocation,
                    true,  // Detected via widget
                    false, // Not detected via game object
                    farmingLevel
                );
                
                cache.put(spiritTree, newData);

                log.info("Updated spirit tree cache for via widget (available for travel)\n\t{}", spiritTree.toString());
            }
            
            // Check for patches that might no longer be available
            updateUnavailableSpiritTreesFromWidget(cache, availableSpiritTrees, playerLocation, farmingLevel);
            
        } catch (Exception e) {
            log.error("Error updating cache from spirit tree widget: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Update farming states from varbit changes - only when near spirit tree patches
     */
    private void updateFarmingStatesFromVarbits(CacheOperations<SpiritTree, SpiritTreeData> cache) {
        try {
            WorldPoint playerLocation = getPlayerLocation();
            Integer farmingLevel = getFarmingLevel();
            
            if (playerLocation == null) {
                return; // Can't determine player location
            }
            
            // Only update if player is near a spirit tree (within 10 tiles)
            boolean nearSpiritTree = SpiritTree.getFarmableSpirtTrees().stream()
                .anyMatch(spiritTree -> playerLocation.distanceTo(spiritTree.getLocation()) <= 10);
            
            if (!nearSpiritTree) {
                log.trace("Player not near any spirit tree patches, skipping varbit update");
                return;
            }
            
            // Update all farmable spirit tree patches
            for (SpiritTree spiritTree : SpiritTree.getFarmableSpirtTrees()) {
                try {
                    CropState currentState = spiritTree.getPatchState();
                    boolean availableForTravel = spiritTree.isAvailableForTravel();
                    
                    SpiritTreeData existingData = cache.get(spiritTree);
                    
                    // Only update if state actually changed or this is new data
                    if (existingData == null || 
                        existingData.getCropState() != currentState ||
                        existingData.isAvailableForTravel() != availableForTravel) {
                        
                        SpiritTreeData newData = new SpiritTreeData(
                            spiritTree,
                            currentState,
                            availableForTravel,
                            playerLocation,
                            false, // Not detected via widget
                            false, // Not detected via game object
                            farmingLevel
                        );
                        
                        cache.put(spiritTree, newData);
                        log.debug("Updated spirit tree cache for {} via varbit (state: {}, available: {})", 
                                spiritTree.name(), currentState, availableForTravel);
                    }
                } catch (Exception e) {
                    log.debug("Error updating farming state for spiritTree {}: {}", spiritTree.name(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.error("Error updating farming states from varbits: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Update spiritTree data from game object interaction using Rs2ObjectModel utilities
     */
    private void updatePatchFromGameObject(CacheOperations<SpiritTree, SpiritTreeData> cache, 
                                          SpiritTree spiritTree, net.runelite.api.GameObject gameObject) {
        try {
            WorldPoint playerLocation = getPlayerLocation();
            Integer farmingLevel = getFarmingLevel();
            
            // Find the Rs2ObjectModel for this game object
            Rs2ObjectModel objectModel = Rs2ObjectCacheUtils.getByGameId(gameObject.getId())
                .filter(model -> model.getLocation().equals(gameObject.getWorldLocation()))
                .findFirst()
                .orElse(null);
            
            // Check if the game object has "Travel" action (indicates it's usable)
            boolean hasTravel = false;
            try {
                String[] actions = Microbot.getClient().getObjectDefinition(gameObject.getId()).getActions();
                if (actions != null) {
                    for (String action : actions) {
                        if ("Travel".equals(action)) {
                            hasTravel = true;
                            break;
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("Could not get actions for spirit tree object {}: {}", gameObject.getId(), e.getMessage());
            }
            
            CropState currentCropState = spiritTree.getType() == SpiritTree.SpiritTreeType.FARMABLE ? 
                                       spiritTree.getPatchState() : null;
            
            SpiritTreeData newData = new SpiritTreeData(
                spiritTree,
                currentCropState,
                hasTravel, // Available based on "Travel" action presence
                playerLocation,
                false, // Not detected via widget
                true,  // Detected via game object
                farmingLevel
            );
            
            cache.put(spiritTree, newData);
            log.debug("Updated spirit tree cache for {} via game object (travel available: {}, object model: {})", 
                    spiritTree.name(), hasTravel, objectModel != null ? "found" : "not found");
            
        } catch (Exception e) {
            log.error("Error updating spiritTree from game object: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Update patches that are no longer available based on widget data
     */
    private void updateUnavailableSpiritTreesFromWidget(CacheOperations<SpiritTree, SpiritTreeData> cache,
                                                   List<SpiritTree> availableSpiritTrees,
                                                   WorldPoint playerLocation, Integer farmingLevel) {
        // Check all patches to see if any are missing from the widget but were previously available
        for (SpiritTree spiritTree : SpiritTree.values()) {
            if (!availableSpiritTrees.contains(spiritTree)) {
                SpiritTreeData existingData = cache.get(spiritTree);
                
                // If we had this as available before, mark it as unavailable
                if (existingData != null && existingData.isAvailableForTravel()) {
                    SpiritTreeData updatedData = existingData.withUpdatedAvailability(
                        false, true, false, playerLocation, farmingLevel);
                    
                    cache.put(spiritTree, updatedData);
                    log.debug("Marked spirit tree {} as unavailable (not found in widget)", spiritTree.name());
                }
            }
        }
    }
    
    /**
     * Find spirit tree spiritTree by object location
     */
    private SpiritTree findPatchByLocation(WorldPoint objectLocation) {
        if (objectLocation == null) {
            return null;
        }
        
        for (SpiritTree spiritTree : SpiritTree.values()) {
            WorldPoint patchLocation = spiritTree.getLocation();
            if (patchLocation != null && patchLocation.distanceTo(objectLocation) <= 3) {
                return spiritTree;
            }
        }
        
        return null;
    }
    
    /**
     * Get current player location safely
     */
    private WorldPoint getPlayerLocation() {
        try {
            if (Microbot.getClient() != null && 
                Microbot.getClient().getGameState() == GameState.LOGGED_IN &&
                Microbot.getClient().getLocalPlayer() != null) {
                return Microbot.getClient().getLocalPlayer().getWorldLocation();
            }
        } catch (Exception e) {
            log.trace("Could not get player location: {}", e.getMessage());
        }
        return null;
    }
    
    /**
     * Get current farming level safely
     */
    private Integer getFarmingLevel() {
        try {
            if (Microbot.getClient() != null && 
                Microbot.getClient().getGameState() == GameState.LOGGED_IN) {
                return Microbot.getClient().getRealSkillLevel(Skill.FARMING);
            }
        } catch (Exception e) {
            log.trace("Could not get farming level: {}", e.getMessage());
        }
        return null;
    }
}
