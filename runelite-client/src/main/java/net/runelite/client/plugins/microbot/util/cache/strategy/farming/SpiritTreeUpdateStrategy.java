package net.runelite.client.plugins.microbot.util.cache.strategy.farming;

import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.questhelper.helpers.mischelpers.farmruns.CropState;
import net.runelite.client.plugins.microbot.util.cache.Rs2Cache;
import net.runelite.client.plugins.microbot.util.cache.model.SpiritTreeData;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheOperations;
import net.runelite.client.plugins.microbot.util.cache.strategy.CacheUpdateStrategy;
import net.runelite.client.plugins.microbot.util.farming.SpiritTree;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.poh.PohTeleports;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

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
		ObjectID.FARMING_SPIRIT_TREE_PATCH_5, // Object ID found in farming guild fully grown spirit tree patch
        ObjectID.SPIRIT_TREE_FULLYGROWN,  // Standard spirit spiritTree id when fully grown  and available for travel -> healty )         
        ObjectID.SPIRITTREE_PRIF, // Prifddinas spirit tree  
        ObjectID.POG_SPIRIT_TREE_ALIVE_STATIC,  // Poison Waste spirit tree
        ObjectID.SPIRITTREE_SMALL, // Small spirit tree in  grand Exchange 1295
        ObjectID.ENT, // for the "great" trees in tree gnome village 1293
        ObjectID.STRONGHOLD_ENT, // nd tree gnome stronghold 1294
        ObjectID.POH_SPIRIT_TREE // Player-owned house spirit tree object ID
    );
    
    // Spirit tree specific farming transmit varbits
    private static final List<Integer> SPIRIT_TREE_VARBIT_IDS = Arrays.asList(
        VarbitID.FARMING_TRANSMIT_A, // 4771 - Port Sarim and Farming Guild 
        VarbitID.FARMING_TRANSMIT_B, // 4772 - Etceteria and Brimhaven patches
        VarbitID.FARMING_TRANSMIT_F  // 7904 - Hosidius
    );

    /**
     * Handle an event from the client and update the cache accordingly.
     * @param event The event that occurred
     * @param cache The cache to potentially update
     */
    @Override
    public void handleEvent(Object event, CacheOperations<SpiritTree, SpiritTreeData> cache) {
        try {
            if (event instanceof WidgetLoaded) {
                handleWidgetLoaded((WidgetLoaded) event, cache);
            } else if (event instanceof VarbitChanged) {
                handleVarbitChanged((VarbitChanged) event, cache);
            } else if (event instanceof GameStateChanged) {
                handleGameStateChanged((GameStateChanged) event, cache);
            } else if (event instanceof GameObjectSpawned) {
                GameObject go = ((GameObjectSpawned) event).getGameObject();
                handleGameObjectChange(go, true, cache);
            } else if (event instanceof GameObjectDespawned) {
                GameObject go = ((GameObjectDespawned) event).getGameObject();
                handleGameObjectChange(go, false, cache);
            }
        } catch (Exception e) {
            log.error("Error handling event in SpiritTreeUpdateStrategy: {}", e.getMessage(), e);
        }
    }

    /**
     * Get the event types that are handled by this strategy
     */
    @Override
    public Class<?>[] getHandledEventTypes() {
        return new Class<?>[]{WidgetLoaded.class, VarbitChanged.class, GameStateChanged.class, GameObjectSpawned.class, GameObjectDespawned.class};
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
                    log.debug("Adventure log widget loaded (group {}), checking for spirit tree locations", event.getGroupId());
                    boolean hasRightTitle = Rs2Widget.hasWidgetText(SPIRIT_TREE_WIDGET_TITLE, ADVENTURE_LOG_GROUP_ID, ADVENTURE_LOG_CONTAINER_CHILD, false);
                            
                    if (hasRightTitle) {
                        log.debug("Spirit tree locations widget detected, updating cache from widget data");
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
        if(varbitId == VarbitID.POH_SPIRIT_TREE_UPROOTED){
            log.debug("TODO update cache for POH spirit tree uprooted varbit change,currently in POH? {} ",PohTeleports.isInHouse());
        }
    }

    /**
     * Handles changes to game objects related to spirit trees and updates the cache accordingly.
     * If the provided game object matches known spirit tree objects, it logs the detection
     * and updates the cache with the relevant state.
     *
     * @param gameObject The game object that has changed. May be null, in which case this method does nothing.
     * @param spawned    Indicates whether the game object was spawned (true) or despawned (false).
     * @param cache      The cache instance used for storing and updating {@link SpiritTreeData} for detected spirit trees.
     */
    private void handleGameObjectChange(GameObject gameObject, boolean spawned, CacheOperations<SpiritTree, SpiritTreeData> cache) {
        if(gameObject == null){
            return;
        }
        Arrays.stream(SpiritTree.values()).filter(tree -> tree.getType() == SpiritTree.SpiritTreeType.POH).forEach(tree -> {
            if (tree.getObjectId().contains(gameObject.getId())) {
                log.info("Found spirit tree object {} for POH spirit tree {}, {} to cache", gameObject.getId(), tree.name(), spawned ? "added" : "removed");
                SpiritTreeData newData = new SpiritTreeData(
                        tree,
                        spawned ? CropState.HARVESTABLE: CropState.DEAD,
                        spawned,
                        gameObject.getWorldLocation(),
                        false, // Not detected via widget
                        true // Detected via nearby tree if present
                );
                cache.put(tree, newData);
            }
        });

    }

    /**
     * Handle GameStateChanged events to detect POH region changes and validate spirit tree presence
     */
    private void handleGameStateChanged(GameStateChanged event, CacheOperations<SpiritTree, SpiritTreeData> cache) {
        GameState gameState = event.getGameState();
        
        // Only process when entering game or loading regions
        if (gameState != GameState.LOGGED_IN && gameState != GameState.LOADING) {
            return;
        }
        
        try {
            // Use unified region detection from Rs2Cache
            Rs2Cache.checkAndHandleRegionChange(cache);
        } catch (Exception e) {
            log.error("Error handling GameStateChanged in SpiritTreeUpdateStrategy: {}", e.getMessage(), e);
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
			log.debug("Widget extraction found {} available spirit tree destinations", availableSpiritTrees.size());
			for (SpiritTree spiritTree : SpiritTree.values()) {
				boolean isAvailable = availableSpiritTrees.contains(spiritTree);
				boolean availableForTravel = spiritTree.hasQuestRequirements();

				SpiritTreeData existingData = cache.get(spiritTree);

				// Create new data with widget detection
				SpiritTreeData newData = new SpiritTreeData(
					spiritTree,
					existingData != null ? existingData.getCropState() : null, // Preserve existing crop state if available
					isAvailable && availableForTravel,
					playerLocation,
					isAvailable, // Detected via widget
					false       // Not detected via near tree
				);

				cache.put(spiritTree, newData);

				log.debug("Updated spirit tree cache for via widget ({} for travel)\n\t{}", isAvailable ? "available" : "not available", spiritTree.name());
			}

		} catch (Exception e) {
			log.error("Error updating cache from spirit tree widget: {}", e.getMessage(), e);
		}
	}
    
    /**
     * Update farming states from varbit changes with object detection - only when near spirit tree patches
     */
    private void updateFarmingStatesFromVarbits(CacheOperations<SpiritTree, SpiritTreeData> cache) {
        try {
            WorldPoint playerLocation = getPlayerLocation();
            
            if (playerLocation == null) {
                return; // Can't determine player location
            }
            
            // Only update if player is near a spirit tree (within region)
            boolean nearSpiritTree = SpiritTree.getFarmableSpirtTrees().stream()
                .anyMatch(spiritTree -> Arrays.stream(spiritTree.getRegionIds())
                    .anyMatch(regionId -> regionId != -1 && regionId == playerLocation.getRegionID()));
            
            if (!nearSpiritTree) {
                log.trace("Player not near any spirit tree patches, skipping varbit update");
                return;
            }
            
            // Update all farmable spirit tree patches
            for (SpiritTree spiritTree : SpiritTree.getFarmableSpirtTrees()) {
                try {
                    CropState currentState = spiritTree.getPatchState();
                    boolean availableForTravel = spiritTree.isAvailableForTravel();
                    
                    // Enhanced: Check for nearby spirit tree objects to verify travel availability
                    boolean hasNearbyTravelObject = checkForNearbyTree(spiritTree, playerLocation);
                    
                    // Use object detection to override travel availability if object is found
                    if (hasNearbyTravelObject) {
                        availableForTravel = true;
                        log.debug("Found nearby travel object for {}, setting available=true", spiritTree.name());
                    }
                    
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
                            true  // Detected via varbit when near patch
                        );
                        
                        cache.put(spiritTree, newData);
                        log.debug("Updated spirit tree cache for {} via varbit (state: {}, available: {}, hasObject: {})", 
                                spiritTree.name(), currentState, availableForTravel, hasNearbyTravelObject);
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
     * Check for nearby spirit tree objects that have travel actions.
     * This integrates the object detection logic from the former GameObjectSpawned handler.
     * 
     * @param spiritTree The spirit tree patch to check for
     * @param playerLocation Current player location
     * @return true if a nearby object with travel action is found
     */
    private boolean checkForNearbyTree(SpiritTree spiritTree, WorldPoint playerLocation) {
        try {
            // Get all game objects near the spirit tree patch location
            Optional<GameObject> nearbyObject = Rs2GameObject.getGameObjects()
                .stream()
                .filter(obj -> SPIRIT_TREE_OBJECT_IDS.contains(obj.getId()))
                .filter(obj -> spiritTree.getLocation().distanceTo(obj.getWorldLocation()) <= 5)
                .findFirst();
            
            if (nearbyObject.isPresent()) {
                GameObject gameObject = nearbyObject.get();
                
                // Check if the game object has "Travel" action (indicates it's usable)
                try {
                    String[] actions = Microbot.getClient().getObjectDefinition(gameObject.getId()).getActions();
                    boolean hasTravel = Arrays.stream(actions)
                        .filter(Objects::nonNull)
                        .anyMatch(action -> action.equalsIgnoreCase("Travel"));
                    
                    if (hasTravel) {
                        log.debug("Found spirit tree object {} with Travel action at {} for patch {}", 
                                gameObject.getId(), gameObject.getWorldLocation(), spiritTree.name());
                        return true;
                    }
                } catch (Exception e) {
                    log.debug("Could not get actions for spirit tree object {}: {}", gameObject.getId(), e.getMessage());
                }
            }
            
            return false;
        } catch (Exception e) {
            log.debug("Error checking for nearby travel object for {}: {}", spiritTree.name(), e.getMessage());
            return false;
        }
    }
    
    /**

     * Find spirit tree spiritTree by object location
     */
    private SpiritTree findSpiritTreeByLocation(WorldPoint objectLocation) {
        if (objectLocation == null) {
            return null;
        }
        
        for (SpiritTree spiritTree : SpiritTree.values()) {
            WorldPoint spiritTreeLocation = spiritTree.getLocation();
            if (spiritTreeLocation != null && spiritTreeLocation.distanceTo(objectLocation) <= 5) {
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
