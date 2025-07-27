package net.runelite.client.plugins.microbot.VoxPlugins.rs2cachedebugger;

import net.runelite.api.*;
import net.runelite.client.plugins.microbot.util.cache.overlay.Rs2NpcCacheOverlay;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;
import java.util.function.Predicate;

import javax.inject.Inject;

/**
 * Rs2 Cache Debugger NPC Overlay with configurable rendering and filtering.
 * Extends the base NPC cache overlay with config-driven customization.
 * 
 * @author Vox
 * @version 3.0 - Cache Debugging Focus
 */
public class Rs2CacheDebuggerNpcOverlay extends Rs2NpcCacheOverlay {
    
    private Rs2CacheDebuggerConfig config;
    private Predicate<Rs2NpcModel> renderFilter;
    @Inject
    public Rs2CacheDebuggerNpcOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer) {
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
     * Set the render filter for NPCs
     */
    public Rs2CacheDebuggerNpcOverlay setRenderFilter(Predicate<Rs2NpcModel> filter) {
        this.renderFilter = filter;
        return this;
    }
    
    /**
     * Update rendering options based on config
     */
    private void updateRenderingOptions() {
        if (config == null) return;
        
        // Update rendering options based on config
        setRenderHull(config.npcRenderStyle() == RenderStyle.HULL || 
                     config.npcRenderStyle() == RenderStyle.BOTH);
        setRenderTile(config.npcRenderStyle() == RenderStyle.TILE || 
                     config.npcRenderStyle() == RenderStyle.BOTH);
        setRenderName(config.npcShowNames());
        setRenderOutline(config.npcRenderStyle() == RenderStyle.OUTLINE);
        
        // New configuration options
        setRenderNpcInfo(config.npcShowId());
        setRenderWorldCoordinates(config.npcShowCoordinates());
        setRenderCombatLevel(config.npcShowCombatLevel());
        setRenderDistance(config.npcShowDistance());
    }
    
    @Override
    protected Color getBorderColorForNpc(Rs2NpcModel npcModel) {
        if (config == null) {
            return super.getBorderColorForNpc(npcModel);
        }
        
        // Check for specific NPC categories first
        Color categoryColor = getNpcCategoryColor(npcModel);
        if (categoryColor != null) {
            return categoryColor;
        }
        
        // Fallback to config default
        return config.npcBorderColor();
    }
    
    @Override
    protected Color getFillColorForNpc(Rs2NpcModel npcModel) {
        Color borderColor = getBorderColorForNpc(npcModel);
        return new Color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), 50);
    }
    
    /**
     * Gets color based on NPC category (bank, shop, combat, etc.)
     * 
     * @param npcModel The NPC model
     * @return Category-specific color or null if no category match
     */
    private Color getNpcCategoryColor(Rs2NpcModel npcModel) {
        String name = npcModel.getName().toLowerCase();
        
        // Get actions from the NPC composition
        NPCComposition composition = npcModel.getTransformedComposition();
        String[] actions = null;
        if (composition != null) {
            actions = composition.getActions();
        }
        
        // Bank NPCs
        if (hasAction(actions, "bank") || name.contains("banker")) {
            return Color.GREEN;
        }
        
        // Shop NPCs
        if (hasAction(actions, "trade") || hasAction(actions, "shop") || name.contains("shop")) {
            return Color.CYAN;
        }
        
        // Combat NPCs (aggressive or high level)
        if (npcModel.getCombatLevel() > 100) {
            return Color.RED;
        }
        
        // Training NPCs (low level combat)
        if (npcModel.getCombatLevel() > 0 && npcModel.getCombatLevel() <= 100) {
            return Color.ORANGE;
        }
        
        return null; // No category match
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
    
    /**
     * Get the interacting color for NPCs targeting the player
     */
    public Color getInteractingColor() {
        if (config != null) {
            return config.npcInteractingColor();
        }
        return Color.RED; // Default fallback
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        if (config == null || !config.enableNpcOverlay()) {
            return null;
        }
        
        // Update configuration
        updateRenderingOptions();
        
        // Apply filter if set
        if (renderFilter != null) {
            setRenderFilter(renderFilter);
        }
        
        return super.render(graphics);
    }
}
