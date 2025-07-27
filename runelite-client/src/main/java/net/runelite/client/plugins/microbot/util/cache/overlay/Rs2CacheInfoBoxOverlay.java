package net.runelite.client.plugins.microbot.util.cache.overlay;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.*;
import java.util.List;

/**
 * Top-level overlay for displaying hover information boxes.
 * This overlay uses ALWAYS_ON_TOP layer to ensure info boxes appear above all other overlays.
 * 
 * @author Vox
 * @version 1.0
 */
@Slf4j
public class Rs2CacheInfoBoxOverlay extends Overlay {
    
    private final Client client;
    
    // Info box styling
    private static final Color INFO_BOX_BACKGROUND = new Color(0, 0, 0, 180); // Semi-transparent black
    private static final Color INFO_BOX_BORDER = new Color(255, 255, 255, 200); // Semi-transparent white
    private static final Color INFO_TEXT_COLOR = Color.WHITE;
    private static final int INFO_BOX_PADDING = 6;
    private static final int INFO_BOX_LINE_SPACING = 2;
    private static final long MAX_HOVER_INFO_AGE_MS = 100; // 100ms max age for hover info
    
    public Rs2CacheInfoBoxOverlay(Client client) {
        this.client = client;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ALWAYS_ON_TOP); // Ensure this appears on top of everything
    }
    
    @Override
    public Dimension render(Graphics2D graphics) {
        if (!isClientReady()) {
            return null;
        }
        
        // Get current hover information
        HoverInfoContainer.HoverInfo hoverInfo = HoverInfoContainer.getCurrentHoverInfo();
        if (hoverInfo == null || !hoverInfo.isFresh(MAX_HOVER_INFO_AGE_MS)) {
            // Clear stale hover info
            if (hoverInfo != null && !hoverInfo.isFresh(MAX_HOVER_INFO_AGE_MS)) {
                HoverInfoContainer.clearHoverInfo();
            }
            return null;
        }
        
        try {
            renderInfoBox(graphics, hoverInfo);
        } catch (Exception e) {
            log.warn("Error rendering cache info box overlay: {}", e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Renders the info box with hover information.
     * 
     * @param graphics The graphics context
     * @param hoverInfo The hover information to display
     */
    private void renderInfoBox(Graphics2D graphics, HoverInfoContainer.HoverInfo hoverInfo) {
        List<String> infoLines = hoverInfo.getInfoLines();
        if (infoLines.isEmpty()) {
            return;
        }
        
        // Set font for measurements
        Font originalFont = graphics.getFont();
        Font infoFont = new Font(Font.SANS_SERIF, Font.PLAIN, 12);
        graphics.setFont(infoFont);
        
        FontMetrics fontMetrics = graphics.getFontMetrics();
        int lineHeight = fontMetrics.getHeight();
        
        // Calculate info box dimensions
        int maxTextWidth = infoLines.stream()
            .mapToInt(fontMetrics::stringWidth)
            .max()
            .orElse(0);
        
        int infoBoxWidth = maxTextWidth + (INFO_BOX_PADDING * 2);
        int infoBoxHeight = (infoLines.size() * lineHeight) + 
                           ((infoLines.size() - 1) * INFO_BOX_LINE_SPACING) + 
                           (INFO_BOX_PADDING * 2);
        
        // Calculate position (offset from hover location to avoid overlapping with cursor)
        net.runelite.api.Point hoverLocation = hoverInfo.getLocation();
        int infoBoxX = hoverLocation.getX() + 15; // Offset right
        int infoBoxY = hoverLocation.getY() - infoBoxHeight - 10; // Offset up
        
        // Ensure info box stays within viewport bounds
        int viewportWidth = client.getCanvasWidth();
        int viewportHeight = client.getCanvasHeight();
        
        // Adjust X position if too far right
        if (infoBoxX + infoBoxWidth > viewportWidth) {
            infoBoxX = hoverLocation.getX() - infoBoxWidth - 15; // Move to left side
        }
        
        // Adjust Y position if too high
        if (infoBoxY < 0) {
            infoBoxY = hoverLocation.getY() + 25; // Move below cursor
        }
        
        // Ensure final position is still within bounds
        infoBoxX = Math.max(0, Math.min(infoBoxX, viewportWidth - infoBoxWidth));
        infoBoxY = Math.max(0, Math.min(infoBoxY, viewportHeight - infoBoxHeight));
        
        // Draw info box background
        graphics.setColor(INFO_BOX_BACKGROUND);
        graphics.fillRoundRect(infoBoxX, infoBoxY, infoBoxWidth, infoBoxHeight, 6, 6);
        
        // Draw info box border using entity color
        Color borderColor = hoverInfo.getBorderColor();
        if (borderColor != null) {
            graphics.setColor(borderColor);
        } else {
            graphics.setColor(INFO_BOX_BORDER);
        }
        graphics.setStroke(new BasicStroke(2.0f));
        graphics.drawRoundRect(infoBoxX, infoBoxY, infoBoxWidth, infoBoxHeight, 6, 6);
        
        // Draw info lines
        graphics.setColor(INFO_TEXT_COLOR);
        int currentY = infoBoxY + INFO_BOX_PADDING + fontMetrics.getAscent();
        
        for (String line : infoLines) {
            graphics.drawString(line, infoBoxX + INFO_BOX_PADDING, currentY);
            currentY += lineHeight + INFO_BOX_LINE_SPACING;
        }
        
        // Restore original font
        graphics.setFont(originalFont);
    }
    
    /**
     * Checks if the client is ready for rendering.
     * 
     * @return true if ready
     */
    private boolean isClientReady() {
        return client != null && 
               client.getGameState() != null &&
               client.getLocalPlayer() != null;
    }
}
