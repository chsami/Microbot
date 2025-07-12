package net.runelite.client.plugins.microbot.util.cache.overlay;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.cache.Rs2GroundItemCache;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2GroundItemCacheUtils;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItemModel;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;
import java.util.function.Predicate;

/**
 * Overlay for rendering cached ground items with various highlight options.
 * Based on RuneLite's GroundItemsOverlay patterns but using the cache system.
 * 
 * @author Vox
 * @version 1.0
 */
public class Rs2GroundItemCacheOverlay extends Rs2BaseCacheOverlay {
    
    // Ground item-specific colors (Green theme)
    private static final Color GROUND_ITEM_BORDER_COLOR = Color.GREEN;
    private static final Color GROUND_ITEM_FILL_COLOR = new Color(0, 255, 0, 50); // Green with alpha
    
    // Rendering options
    private boolean renderTile = true;
    private boolean renderText = true;
    private boolean renderItemInfo = true; // Show item ID
    private boolean renderWorldCoordinates = false; // Show world coordinates
    private boolean onlyShowTextOnHover = true; // Only show text when mouse is hovering
    private Predicate<Rs2GroundItemModel> renderFilter;
    
    // Advanced rendering options
    private boolean renderQuantity = true; // Show quantity for stackable items
    private boolean renderValue = false; // Show item values
    private boolean renderDespawnTimer = false; // Show despawn countdown
    private boolean renderOwnershipIndicator = false; // Show ownership status
    
    // Text rendering offset to avoid overlapping ground items
    private static final int TEXT_OFFSET_Z = 20;
    
    // Value thresholds for color coding
    private int lowValueThreshold = 1000;
    private int mediumValueThreshold = 10000;
    private int highValueThreshold = 100000;
    
    public Rs2GroundItemCacheOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer) {
        super(client, modelOutlineRenderer);
    }
    
    @Override
    protected Color getDefaultBorderColor() {
        return GROUND_ITEM_BORDER_COLOR;
    }
    
    @Override
    protected Color getDefaultFillColor() {
        return GROUND_ITEM_FILL_COLOR;
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        if (!isClientReady()) {
            return null;
        }
        
        // Render all visible ground items from cache
        Rs2GroundItemCache.getInstance().stream()
                .filter(item -> renderFilter == null || renderFilter.test(item))
                .filter(Rs2GroundItemCacheUtils::isVisibleInViewport)
                .forEach(item -> renderGroundItemOverlay(graphics, item));
        
        return null;
    }
    
    /**
     * Renders a single ground item with the configured options.
     * 
     * @param graphics The graphics context
     * @param itemModel The ground item model to render
     */
    private void renderGroundItemOverlay(Graphics2D graphics, Rs2GroundItemModel itemModel) {
        try {
            Color borderColor = getBorderColorForItem(itemModel);
            Color fillColor = getFillColorForItem(itemModel);
            float borderWidth = DEFAULT_BORDER_WIDTH;
            
            // Render tile highlight
            if (renderTile) {
                renderItemTile(graphics, itemModel, borderColor, fillColor, borderWidth);
            }
            
            // Render text information if enabled
            if (renderText || renderItemInfo || renderWorldCoordinates || 
                renderQuantity || renderValue || renderDespawnTimer || renderOwnershipIndicator) {
                renderItemText(graphics, itemModel, borderColor);
            }
            
        } catch (Exception e) {
            // Silent fail to avoid spam
        }
    }
    
    /**
     * Renders a tile highlight for the ground item.
     * 
     * @param graphics The graphics context
     * @param itemModel The ground item model
     * @param borderColor The border color
     * @param fillColor The fill color
     * @param borderWidth The border width
     */
    private void renderItemTile(Graphics2D graphics, Rs2GroundItemModel itemModel, 
                               Color borderColor, Color fillColor, float borderWidth) {
        LocalPoint localPoint = LocalPoint.fromWorld(client.getTopLevelWorldView(), itemModel.getLocation());
        if (localPoint == null) {
            return;
        }
        
        Polygon tilePoly = Perspective.getCanvasTilePoly(client, localPoint);
        if (tilePoly != null) {
            Stroke stroke = new BasicStroke(borderWidth);
            renderPolygon(graphics, tilePoly, borderColor, fillColor, stroke);
        }
    }
    
    /**
     * Renders text information for the ground item with hover detection and background.
     * 
     * @param graphics The graphics context
     * @param itemModel The ground item model
     * @param color The text color
     */
    private void renderItemText(Graphics2D graphics, Rs2GroundItemModel itemModel, Color color) {
        LocalPoint localPoint = LocalPoint.fromWorld(client.getTopLevelWorldView(), itemModel.getLocation());
        if (localPoint == null) {
            return;
        }

        // Check if we should only show text on hover
        if (onlyShowTextOnHover && !isMouseHoveringOver(localPoint)) {
            return;
        }

        // Build single line of information
        StringBuilder infoText = new StringBuilder();
        
        // Main item name and quantity
        if (renderText) {
            infoText.append(buildItemText(itemModel));
        }
        
        // Add item ID and coordinates if enabled
        if (renderItemInfo || renderWorldCoordinates) {
            if (infoText.length() > 0) {
                infoText.append(" | ");
            }
            
            if (renderItemInfo) {
                infoText.append("ID:").append(itemModel.getId());
            }
            
            if (renderWorldCoordinates) {
                if (renderItemInfo) {
                    infoText.append(" ");
                }
                WorldPoint wp = itemModel.getLocation();
                infoText.append("(").append(wp.getX()).append(",").append(wp.getY()).append(")");
            }
        }
        
        // Add additional information based on settings
        if (renderQuantity && itemModel.getQuantity() > 1) {
            if (infoText.length() > 0) {
                infoText.append(" ");
            }
            infoText.append("x").append(itemModel.getQuantity());
        }
        
        if (renderValue) {
            if (infoText.length() > 0) {
                infoText.append(" ");
            }
            infoText.append(formatValue(itemModel.getTotalValue()));
        }
        
        if (renderDespawnTimer && !itemModel.isDespawned()) {
            if (infoText.length() > 0) {
                infoText.append(" ");
            }
            infoText.append("⏰").append(formatDespawnTime(itemModel.getSecondsUntilDespawn()));
        }
        
        if (renderOwnershipIndicator) {
            if (infoText.length() > 0) {
                infoText.append(" ");
            }
            infoText.append(itemModel.isOwned() ? "👤" : "🌐");
        }

        // Get canvas point with Z offset for text
        net.runelite.api.Point canvasPoint = Perspective.localToCanvas(client, localPoint, 
                client.getTopLevelWorldView().getPlane(), TEXT_OFFSET_Z);

        if (canvasPoint != null && infoText.length() > 0) {
            renderTextWithBackground(graphics, infoText.toString(), canvasPoint, color);
        }
    }
    
    /**
     * Checks if the mouse is hovering over a ground item location.
     * 
     * @param localPoint The ground item location
     * @return true if mouse is hovering over the location
     */
    private boolean isMouseHoveringOver(LocalPoint localPoint) {
        net.runelite.api.Point mousePos = client.getMouseCanvasPosition();
        if (mousePos == null) {
            return false;
        }
        
        // Check if mouse is over the tile
        Polygon tilePoly = Perspective.getCanvasTilePoly(client, localPoint);
        return tilePoly != null && tilePoly.contains(mousePos.getX(), mousePos.getY());
    }
    
    /**
     * Renders text with a semi-transparent background for better readability.
     * 
     * @param graphics The graphics context
     * @param text The text to render
     * @param location The location to render at
     * @param color The text color
     */
    private void renderTextWithBackground(Graphics2D graphics, String text, 
                                        net.runelite.api.Point location, Color color) {
        FontMetrics fm = graphics.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int textHeight = fm.getHeight();
        
        // Create background rectangle
        int padding = 4;
        int backgroundX = location.getX() - padding;
        int backgroundY = location.getY() - textHeight - padding;
        int backgroundWidth = textWidth + (padding * 2);
        int backgroundHeight = textHeight + (padding * 2);
        
        // Draw semi-transparent background
        Color backgroundColor = new Color(0, 0, 0, 128); // Semi-transparent black
        graphics.setColor(backgroundColor);
        graphics.fillRect(backgroundX, backgroundY, backgroundWidth, backgroundHeight);
        
        // Draw text
        renderText(graphics, text, location, color);
    }
    
    /**
     * Builds the text to display for a ground item.
     * 
     * @param itemModel The ground item model
     * @return The text to display
     */
    private String buildItemText(Rs2GroundItemModel itemModel) {
        StringBuilder text = new StringBuilder();
        
        text.append(itemModel.getName());
        
        if (itemModel.getQuantity() > 1) {
            text.append(" (").append(itemModel.getQuantity()).append(")");
        }
        
        return text.toString();
    }
    
    /**
     * Formats the item value for display.
     * 
     * @param value The item value
     * @return The formatted value string
     */
    private String formatValue(int value) {
        // Simple color formatting without ColorUtils dependency
        if (value < lowValueThreshold) {
            return String.format("%d gp", value);
        } else if (value < mediumValueThreshold) {
            return String.format("%d gp", value);
        } else if (value < highValueThreshold) {
            return String.format("%d gp", value);
        } else {
            return String.format("%d gp", value);
        }
    }
    
    /**
     * Formats the despawn time for display.
     * 
     * @param despawnSeconds The despawn time in seconds
     * @return The formatted time string
     */
    private String formatDespawnTime(long despawnSeconds) {
        if (despawnSeconds <= 0) {
            return "";
        }
        
        long minutes = despawnSeconds / 60;
        long seconds = despawnSeconds % 60;
        
        return String.format("%02d:%02d", minutes, seconds);
    }
    
    // ============================================
    // Configuration Methods
    // ============================================
    
    public Rs2GroundItemCacheOverlay setRenderTile(boolean renderTile) {
        this.renderTile = renderTile;
        return this;
    }
    
    public Rs2GroundItemCacheOverlay setRenderText(boolean renderText) {
        this.renderText = renderText;
        return this;
    }
    
    public Rs2GroundItemCacheOverlay setRenderItemInfo(boolean renderItemInfo) {
        this.renderItemInfo = renderItemInfo;
        return this;
    }
    
    public Rs2GroundItemCacheOverlay setRenderWorldCoordinates(boolean renderWorldCoordinates) {
        this.renderWorldCoordinates = renderWorldCoordinates;
        return this;
    }
    
    public Rs2GroundItemCacheOverlay setRenderFilter(Predicate<Rs2GroundItemModel> renderFilter) {
        this.renderFilter = renderFilter;
        return this;
    }
    
    // Configuration methods for advanced rendering options
    public void setRenderQuantity(boolean renderQuantity) {
        this.renderQuantity = renderQuantity;
    }
    
    public void setRenderValue(boolean renderValue) {
        this.renderValue = renderValue;
    }
    
    public void setRenderDespawnTimer(boolean renderDespawnTimer) {
        this.renderDespawnTimer = renderDespawnTimer;
    }
    
    public void setRenderOwnershipIndicator(boolean renderOwnershipIndicator) {
        this.renderOwnershipIndicator = renderOwnershipIndicator;
    }
    
    public void setValueThresholds(int low, int medium, int high) {
        this.lowValueThreshold = low;
        this.mediumValueThreshold = medium;
        this.highValueThreshold = high;
    }
    
    /**
     * Gets the border color for a specific ground item.
     * Can be overridden by subclasses to provide per-item coloring.
     * 
     * @param itemModel The ground item model
     * @return The border color for this item
     */
    protected Color getBorderColorForItem(Rs2GroundItemModel itemModel) {
        return getDefaultBorderColor();
    }
    
    /**
     * Gets the fill color for a specific ground item.
     * Can be overridden by subclasses to provide per-item coloring.
     * 
     * @param itemModel The ground item model
     * @return The fill color for this item
     */
    protected Color getFillColorForItem(Rs2GroundItemModel itemModel) {
        return getDefaultFillColor();
    }
    
    public Rs2GroundItemCacheOverlay setOnlyShowTextOnHover(boolean onlyShowTextOnHover) {
        this.onlyShowTextOnHover = onlyShowTextOnHover;
        return this;
    }
}
