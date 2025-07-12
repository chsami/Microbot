package net.runelite.client.plugins.microbot.VoxPlugins.rs2cachedebugger;

import net.runelite.api.*;
import net.runelite.client.plugins.microbot.util.cache.overlay.Rs2GroundItemCacheOverlay;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItemModel;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;
import java.util.function.Predicate;

import javax.inject.Inject;

/**
 * Rs2 Cache Debugger Ground Item Overlay with configurable rendering and filtering.
 * Extends the base Ground Item cache overlay with config-driven customization.
 * 
 * @author Vox
 * @version 3.0 - Cache Debugging Focus
 */
public class Rs2CacheDebuggerGroundItemOverlay extends Rs2GroundItemCacheOverlay {
    
    private Rs2CacheDebuggerConfig config;
    private Predicate<Rs2GroundItemModel> renderFilter;
    @Inject
    public Rs2CacheDebuggerGroundItemOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer) {
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
     * Set the render filter for Ground Items
     */
    public Rs2CacheDebuggerGroundItemOverlay setRenderFilter(Predicate<Rs2GroundItemModel> filter) {
        this.renderFilter = filter;
        return this;
    }
    
    /**
     * Update rendering options based on config
     */
    private void updateRenderingOptions() {
        if (config == null) return;
        
        // Update rendering options based on config
        setRenderTile(config.groundItemRenderStyle() == RenderStyle.TILE || 
                     config.groundItemRenderStyle() == RenderStyle.BOTH);
        setRenderText(config.groundItemShowNames());
        
        // New configuration options
        setRenderItemInfo(config.groundItemShowId());
        setRenderWorldCoordinates(config.groundItemShowCoordinates());
        
        // Advanced rendering features from Rs2GroundItemCacheOverlay
        setRenderQuantity(config.groundItemShowQuantity());
        setRenderValue(config.groundItemShowValues());
        setRenderDespawnTimer(config.groundItemShowDespawnTimer());
        setRenderOwnershipIndicator(config.groundItemShowOwnership());
        
        // Set value thresholds for color coding
        setValueThresholds(
            config.groundItemLowValueThreshold(),
            config.groundItemMediumValueThreshold(), 
            config.groundItemHighValueThreshold()
        );
    }
    
    @Override
    protected Color getDefaultBorderColor() {
        if (config != null) {
            return config.groundItemBorderColor();
        }
        return super.getDefaultBorderColor();
    }
    
    @Override
    protected Color getDefaultFillColor() {
        if (config != null) {
            // Create fill color with alpha from border color
            Color borderColor = config.groundItemBorderColor();
            return new Color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), 50);
        }
        return super.getDefaultFillColor();
    }
    
    /**
     * Gets the border color for a specific ground item based on value and configuration.
     * 
     * @param itemModel The ground item model
     * @return The border color for this item
     */
    @Override
    protected Color getBorderColorForItem(Rs2GroundItemModel itemModel) {
        if (config != null && config.groundItemShowValues()) {
            return getItemValueColor(itemModel);
        }
        return getDefaultBorderColor();
    }
    
    /**
     * Gets the fill color for a specific ground item based on value and configuration.
     * 
     * @param itemModel The ground item model
     * @return The fill color for this item
     */
    @Override
    protected Color getFillColorForItem(Rs2GroundItemModel itemModel) {
        if (config != null && config.groundItemShowValues()) {
            Color borderColor = getItemValueColor(itemModel);
            return new Color(borderColor.getRed(), borderColor.getGreen(), borderColor.getBlue(), 50);
        }
        return getDefaultFillColor();
    }
    
    /**
     * Gets the color for a ground item based on its value.
     * 
     * @param itemModel The ground item model
     * @return The value-based color
     */
    private Color getItemValueColor(Rs2GroundItemModel itemModel) {
        int totalValue = itemModel.getTotalValue();
        
        if (totalValue >= config.groundItemHighValueThreshold()) {
            return Color.RED; // High value items in red
        } else if (totalValue >= config.groundItemMediumValueThreshold()) {
            return Color.ORANGE; // Medium value items in orange
        } else if (totalValue >= config.groundItemLowValueThreshold()) {
            return Color.YELLOW; // Low value items in yellow
        } else {
            return getDefaultBorderColor(); // Default color for very low value items
        }
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        if (config == null || !config.enableGroundItemOverlay()) {
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
