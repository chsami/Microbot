package net.runelite.client.plugins.microbot.util.cache.overlay;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.outline.ModelOutlineRenderer;

import java.awt.*;

/**
 * Base overlay class for cache-based entity rendering.
 * Provides common rendering utilities and setup for cache overlays.
 * 
 * @author Vox
 * @version 1.0
 */
public abstract class Rs2BaseCacheOverlay extends Overlay {
    
    protected final Client client;
    protected final ModelOutlineRenderer modelOutlineRenderer;
    
    // Default rendering settings (now abstract - overridden in subclasses)
    protected static final float DEFAULT_BORDER_WIDTH = 2.0f;
    
    /**
     * Gets the default border color for this entity type.
     * @return The default border color
     */
    protected abstract Color getDefaultBorderColor();
    
    /**
     * Gets the default fill color for this entity type.
     * @return The default fill color
     */
    protected abstract Color getDefaultFillColor();
    
    public Rs2BaseCacheOverlay(Client client, ModelOutlineRenderer modelOutlineRenderer) {
        this.client = client;
        this.modelOutlineRenderer = modelOutlineRenderer;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }
    
    /**
     * Renders a polygon with border and fill colors.
     * 
     * @param graphics The graphics context
     * @param shape The shape to render
     * @param borderColor The border color
     * @param fillColor The fill color
     * @param borderWidth The border width
     */
    protected void renderShape(Graphics2D graphics, Shape shape, Color borderColor, Color fillColor, float borderWidth) {
        if (shape == null) {
            return;
        }
        
        // Set stroke
        graphics.setStroke(new BasicStroke(borderWidth));
        
        // Draw border
        graphics.setColor(borderColor);
        graphics.draw(shape);
        
        // Fill shape
        if (fillColor != null) {
            graphics.setColor(fillColor);
            graphics.fill(shape);
        }
    }
    
    /**
     * Renders a polygon using OverlayUtil for consistency with RuneLite.
     * 
     * @param graphics The graphics context
     * @param shape The shape to render
     * @param borderColor The border color
     * @param fillColor The fill color
     * @param stroke The stroke to use
     */
    protected void renderPolygon(Graphics2D graphics, Shape shape, Color borderColor, Color fillColor, Stroke stroke) {
        if (shape != null) {
            OverlayUtil.renderPolygon(graphics, shape, borderColor, fillColor, stroke);
        }
    }
    
    /**
     * Renders text at a specific location.
     * 
     * @param graphics The graphics context
     * @param text The text to render
     * @param point The location to render at
     * @param color The text color
     */
    protected void renderText(Graphics2D graphics, String text, net.runelite.api.Point point, Color color) {
        if (point != null && text != null && !text.isEmpty()) {
            OverlayUtil.renderTextLocation(graphics, point, text, color);
        }
    }
    
    /**
     * Checks if the client is available and ready for rendering.
     * 
     * @return true if client is ready
     */
    protected boolean isClientReady() {
        return Microbot.isLoggedIn() && client != null;
    }
    
    /**
     * Gets a color with modified alpha for fill colors.
     * 
     * @param baseColor The base color
     * @param alpha The alpha value (0-255)
     * @return Color with modified alpha
     */
    protected Color withAlpha(Color baseColor, int alpha) {
        return new Color(baseColor.getRed(), baseColor.getGreen(), baseColor.getBlue(), alpha);
    }
    
    // ============================================
    // Enhanced Utility Methods
    // ============================================
    
    /**
     * Checks if an entity is within the viewport bounds.
     * Uses a simple canvas bounds check as a fast pre-filter.
     * 
     * @param canvasPoint The canvas point to check
     * @return true if the point is within viewport bounds
     */
    protected boolean isWithinViewportBounds(net.runelite.api.Point canvasPoint) {
        if (canvasPoint == null || client == null) {
            return false;
        }
        
        // Get canvas dimensions (viewport size)
        int canvasWidth = client.getCanvasWidth();
        int canvasHeight = client.getCanvasHeight();
        
        // Check if point is within bounds with some margin
        return canvasPoint.getX() >= -50 && canvasPoint.getX() <= canvasWidth + 50 &&
               canvasPoint.getY() >= -50 && canvasPoint.getY() <= canvasHeight + 50;
    }
    
    /**
     * Renders a shape with enhanced visual effects.
     * 
     * @param graphics The graphics context
     * @param shape The shape to render
     * @param borderColor The border color
     * @param fillColor The fill color
     * @param borderWidth The border width
     * @param dashedBorder Whether to use a dashed border
     */
    protected void renderShapeEnhanced(Graphics2D graphics, Shape shape, Color borderColor, 
                                     Color fillColor, float borderWidth, boolean dashedBorder) {
        if (shape == null) {
            return;
        }
        
        // Set stroke (dashed or solid)
        if (dashedBorder) {
            float[] dash = {5.0f, 5.0f};
            graphics.setStroke(new BasicStroke(borderWidth, BasicStroke.CAP_ROUND, 
                    BasicStroke.JOIN_ROUND, 1.0f, dash, 0.0f));
        } else {
            graphics.setStroke(new BasicStroke(borderWidth));
        }
        
        // Fill shape first
        if (fillColor != null) {
            graphics.setColor(fillColor);
            graphics.fill(shape);
        }
        
        // Draw border
        graphics.setColor(borderColor);
        graphics.draw(shape);
    }
    
    /**
     * Renders text with a background for better visibility.
     * 
     * @param graphics The graphics context
     * @param text The text to render
     * @param point The location to render at
     * @param textColor The text color
     * @param backgroundColor The background color (null for no background)
     */
    protected void renderTextWithBackground(Graphics2D graphics, String text, net.runelite.api.Point point, 
                                          Color textColor, Color backgroundColor) {
        if (point == null || text == null || text.isEmpty()) {
            return;
        }
        
        if (backgroundColor != null) {
            // Calculate text bounds for background
            FontMetrics metrics = graphics.getFontMetrics();
            int textWidth = metrics.stringWidth(text);
            int textHeight = metrics.getHeight();
            
            // Draw background rectangle
            graphics.setColor(backgroundColor);
            graphics.fillRect(point.getX() - 2, point.getY() - textHeight + 2, 
                    textWidth + 4, textHeight);
        }
        
        // Render the text
        renderText(graphics, text, point, textColor);
    }
}
