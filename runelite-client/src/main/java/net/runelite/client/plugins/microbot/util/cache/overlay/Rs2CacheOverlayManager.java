package net.runelite.client.plugins.microbot.util.cache.overlay;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2NpcCacheUtils;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2ObjectCacheUtils;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2GroundItemCacheUtils;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import javax.inject.Inject;

/**
 * Manager class for cache-based overlays.
 * Provides easy setup and configuration of cache overlays for development and debugging.
 * 
 * @author Vox
 * @version 1.0
 */
public class Rs2CacheOverlayManager {
    
    private final Client client;
    private final OverlayManager overlayManager;
    private final ModelOutlineRenderer modelOutlineRenderer;
    
    private Rs2NpcCacheOverlay npcOverlay;
    private Rs2ObjectCacheOverlay objectOverlay;
    private Rs2GroundItemCacheOverlay groundItemOverlay;
    private Rs2CacheInfoBoxOverlay hoverInfoOverlay;
    
    public Rs2CacheOverlayManager(Client client, OverlayManager overlayManager, ModelOutlineRenderer modelOutlineRenderer) {
        this.client = client;
        this.overlayManager = overlayManager;
        this.modelOutlineRenderer = modelOutlineRenderer;
        initializeOverlays();
    }
    
    /**
     * Initializes all cache overlays with default settings.
     */
    private void initializeOverlays() {
        // Create overlays
        npcOverlay = new Rs2NpcCacheOverlay(client, modelOutlineRenderer);
        objectOverlay = new Rs2ObjectCacheOverlay(client, modelOutlineRenderer);
        groundItemOverlay = new Rs2GroundItemCacheOverlay(client, modelOutlineRenderer);
        hoverInfoOverlay = new Rs2CacheInfoBoxOverlay(client);
        
        // Register the hover info overlay with the container for automatic management
        HoverInfoContainer.registerInfoBoxOverlay(overlayManager);
        
        // Configure default filters and settings
        configureDefaultSettings();
    }
    
    /**
     * Configures default settings for all overlays.
     */
    private void configureDefaultSettings() {
        // NPC overlay - show only NPCs within 10 tiles and visible
        npcOverlay.setRenderFilter(npc -> npc.getDistanceFromPlayer() <= 10);
        
        // Object overlay - show only interactable objects within 15 tiles
        objectOverlay.setRenderFilter(obj -> obj.getDistanceFromPlayer() <= 15);
        
        // Ground item overlay - show only items within 5 tiles
        groundItemOverlay.setRenderFilter(item -> item.getDistanceFromPlayer() <= 5);
    }
    
    // ============================================
    // Overlay Management Methods
    // ============================================
    
    /**
     * Enables NPC cache overlay.
     */
    public void enableNpcOverlay() {
        overlayManager.add(npcOverlay);
    }
    
    /**
     * Disables NPC cache overlay.
     */
    public void disableNpcOverlay() {
        overlayManager.remove(npcOverlay);
    }
    
    /**
     * Enables object cache overlay.
     */
    public void enableObjectOverlay() {
        overlayManager.add(objectOverlay);
    }
    
    /**
     * Disables object cache overlay.
     */
    public void disableObjectOverlay() {
        overlayManager.remove(objectOverlay);
    }
    
    /**
     * Enables ground item cache overlay.
     */
    public void enableGroundItemOverlay() {
        overlayManager.add(groundItemOverlay);
    }
    
    /**
     * Disables ground item cache overlay.
     */
    public void disableGroundItemOverlay() {
        overlayManager.remove(groundItemOverlay);
    }
    
    /**
     * Enables hover info overlay (always on top).
     */
    public void enableHoverInfoOverlay() {
        overlayManager.add(hoverInfoOverlay);
    }
    
    /**
     * Disables hover info overlay.
     */
    public void disableHoverInfoOverlay() {
        overlayManager.remove(hoverInfoOverlay);
    }
    
    /**
     * Enables all cache overlays including hover info.
     */
    public void enableAllOverlays() {
        enableNpcOverlay();
        enableObjectOverlay();
        enableGroundItemOverlay();
        enableHoverInfoOverlay();
    }
    
    /**
     * Disables all cache overlays including hover info.
     */
    public void disableAllOverlays() {
        disableNpcOverlay();
        disableObjectOverlay();
        disableGroundItemOverlay();
        disableHoverInfoOverlay();
    }
    
    // ============================================
    // Overlay Configuration Getters
    // ============================================
    
    public Rs2NpcCacheOverlay getNpcOverlay() {
        return npcOverlay;
    }
    
    public Rs2ObjectCacheOverlay getObjectOverlay() {
        return objectOverlay;
    }
    
    public Rs2GroundItemCacheOverlay getGroundItemOverlay() {
        return groundItemOverlay;
    }
    
    public Rs2CacheInfoBoxOverlay getHoverInfoOverlay() {
        return hoverInfoOverlay;
    }
    
    // ============================================
    // Quick Configuration Methods
    // ============================================
    
    /**
     * Quick setup for debugging NPCs by ID.
     * 
     * @param npcId The NPC ID to highlight
     */
    public void highlightNpcById(int npcId) {
        npcOverlay.setRenderFilter(npc -> npc.getId() == npcId);
        enableNpcOverlay();
    }
    
    /**
     * Quick setup for debugging objects by ID.
     * 
     * @param objectId The object ID to highlight
     */
    public void highlightObjectById(int objectId) {
        objectOverlay.setRenderFilter(obj -> obj.getId() == objectId);
        enableObjectOverlay();
    }
    
    /**
     * Quick setup for debugging ground items by ID.
     * 
     * @param itemId The item ID to highlight
     */
    public void highlightGroundItemById(int itemId) {
        groundItemOverlay.setRenderFilter(item -> item.getId() == itemId);
        enableGroundItemOverlay();
    }
    
    /**
     * Quick setup for debugging entities by name (case-insensitive).
     * 
     * @param entityName The entity name to highlight
     */
    public void highlightEntitiesByName(String entityName) {
        String lowerName = entityName.toLowerCase();
        
        npcOverlay.setRenderFilter(npc -> npc.getName() != null && 
                npc.getName().toLowerCase().contains(lowerName));
        objectOverlay.setRenderFilter(obj -> obj.getName() != null && 
                obj.getName().toLowerCase().contains(lowerName));
        groundItemOverlay.setRenderFilter(item -> item.getName() != null && 
                item.getName().toLowerCase().contains(lowerName));
        
        enableAllOverlays();
    }
    
    /**
     * Cleanup method to properly unregister all overlays including the hover info overlay.
     * Should be called when the overlay manager is no longer needed.
     */
    public void cleanup() {
        disableAllOverlays();
        HoverInfoContainer.unregisterInfoBoxOverlay(overlayManager);
    }
}
