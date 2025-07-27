package net.runelite.client.plugins.microbot.VoxPlugins.rs2cachedebugger;

import net.runelite.api.*;
import net.runelite.client.plugins.microbot.util.cache.overlay.Rs2ObjectCacheOverlay;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2ObjectModel;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;
import java.awt.*;
import java.util.function.Predicate;

import javax.inject.Inject;

import lombok.extern.slf4j.Slf4j;

/**
 * Rs2 Cache Debugger Object Overlay with configurable rendering and filtering.
 * Extends the base Object cache overlay with config-driven customization.
 * 
 * @author Vox
 * @version 3.0 - Cache Debugging Focus
 */
@Slf4j
public class Rs2CacheDebuggerObjectOverlay extends Rs2ObjectCacheOverlay {
    
    private Rs2CacheDebuggerConfig config;
    private Predicate<Rs2ObjectModel> renderFilter;
    @Inject
    public Rs2CacheDebuggerObjectOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer) {
        super(client, modelOutlineRenderer);
    }
    
    /**
     * Set the configuration for this overlay
     */
    public void setConfig(Rs2CacheDebuggerConfig config) {
        this.config = config;
        updateRenderingOptions();
    }
    
    /**
     * Set the render filter for Objects
     */
    public Rs2CacheDebuggerObjectOverlay setRenderFilter(Predicate<Rs2ObjectModel> filter) {
        this.renderFilter = filter;
        return this;
    }
    
    /**
     * Update rendering options based on config
     */
    private void updateRenderingOptions() {
        if (config == null) return;
        
        // Update rendering options based on config
        setRenderHull(config.objectRenderStyle() == RenderStyle.HULL || 
                     config.objectRenderStyle() == RenderStyle.BOTH);
        setRenderTile(config.objectRenderStyle() == RenderStyle.TILE || 
                     config.objectRenderStyle() == RenderStyle.BOTH);
        setRenderClickbox(config.objectRenderStyle() == RenderStyle.CLICKBOX);
        setRenderOutline(config.objectRenderStyle() == RenderStyle.OUTLINE);
        
        // New configuration options
        setRenderObjectInfo(config.objectShowId());
        setRenderObjectName(config.objectShowNames());
        setRenderWorldCoordinates(config.objectShowCoordinates());
        setOnlyShowTextOnHover(true); // Always show text only on hover for better UX
        
        // Enable/disable object types based on config
        setEnableGameObjects(config.showGameObjects());
        setEnableWallObjects(config.showWallObjects());
        setEnableDecorativeObjects(config.showDecorativeObjects());
        setEnableGroundObjects(config.showGroundObjects());
        
        // Configure text rendering style for debugging        
    }
    
    @Override
    protected Color getBorderColorForObject(Rs2ObjectModel objectModel) {
        if (config == null) {
            if (log.isDebugEnabled()) {
                log.debug("Config is null, using super.getBorderColorForObject for object {}", objectModel.getId());
            }
            return super.getBorderColorForObject(objectModel);
        }
        
        
        
        // Check for object category-based coloring first (if enabled) 
        if (config.enableObjectCategoryColoring()) {
            Color categoryColor = getObjectCategoryColor(objectModel);
            if (categoryColor != null) {
               
                return categoryColor;
            } 
        } 
                
        // Check for object type-based coloring (if enabled)
        if (config.enableObjectTypeColoring()) {
            Color typeColor = getBorderColorForObjectType(objectModel.getObjectType());
            if (typeColor != null) {              
                return typeColor;
            } 
        } 
        // Fallback to config default
        Color defaultColor = config.objectBorderColor();      
        return defaultColor;
    }
    
    @Override
    protected Color getBorderColorForObjectType(Rs2ObjectModel.ObjectType objectType) {
        if (config == null || !config.enableObjectTypeColoring()) {
            return super.getBorderColorForObjectType(objectType);
        }
        
        // Use the new config options for different object types
        switch (objectType) {
            case GAME_OBJECT:
                return config.gameObjectColor();
            case WALL_OBJECT:
                return config.wallObjectColor();
            case DECORATIVE_OBJECT:
                return config.decorativeObjectColor();
            case GROUND_OBJECT:
                return config.groundObjectColor();
            case TILE_OBJECT:
                return new Color(255, 165, 0); // Orange - same as TILE_OBJECT_COLOR
            default:
                return config.objectBorderColor();
        }
    }
    
    @Override
    protected Color getFillColorForObject(Rs2ObjectModel objectModel) {
        Color borderColor = getBorderColorForObject(objectModel);
        return new Color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), 50);
    }
    
    /**
     * Gets color based on object category (bank, altar, resource, etc.)
     * 
     * @param objectModel The object model
     * @return Category-specific color or null if no category match
     */
    private Color getObjectCategoryColor(Rs2ObjectModel objectModel) {
        String name = objectModel.getName().toLowerCase();
        String[] actions = objectModel.getActions();
        
        // Bank objects
        if (name.contains("bank") || hasAction(actions, "bank") || hasAction(actions, "collect")) {
            return config.bankColor();
        }
        
        // Altar objects 
        if (name.contains("altar") || hasAction(actions, "pray-at") || hasAction(actions, "pray")) {
            return config.altarColor();
        }
        
        // Resource objects (trees, rocks, fishing spots, etc.)
        if (isResourceObject(name, actions)) {
            return config.resourceColor();
        }
        
        return null; // No category match
    }
    
    /**
     * Checks if an object is a resource object (trees, rocks, fishing spots, etc.)
     * 
     * @param name The object name (lowercase)
     * @param actions The object actions
     * @return true if this is a resource object
     */
    private boolean isResourceObject(String name, String[] actions) {
        // Trees
        if (name.contains("tree") || name.contains("log") || hasAction(actions, "chop")) {
            return true;
        }
        
        // Rocks and mining
        if (name.contains("rock") || name.contains("ore") || hasAction(actions, "mine")) {
            return true;
        }
        
        // Fishing spots
        if (name.contains("fishing") || name.contains("pool") || hasAction(actions, "fish")) {
            return true;
        }
        
        // Other resources
        if (hasAction(actions, "pick") || hasAction(actions, "harvest") || hasAction(actions, "gather")) {
            return true;
        }
        
        return false;
    }
    
    /**
     * Checks if an action array contains a specific action
     * 
     * @param actions The actions array
     * @param action The action to look for
     * @return true if the action exists
     */
    private boolean hasAction(String[] actions, String action) {
        if (actions == null || action == null) {
            return false;
        }
        
        for (String a : actions) {
            if (a != null && a.toLowerCase().contains(action.toLowerCase())) {
                return true;
            }
        }
        return false;
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        if (config == null || !config.enableObjectOverlay()) {
            return null;
        }
        try{
            // Update configuration
            updateRenderingOptions();
        
            // Apply filter if set
            if (renderFilter != null) {
                setRenderFilter(renderFilter);
            }        
            return super.render(graphics);
        } catch (Exception e) {
            log.error("Error rendering Rs2CacheDebuggerObjectOverlay: {}", e.getMessage(), e);
            return null;    
        }
    }
}
