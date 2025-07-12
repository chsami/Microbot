package net.runelite.client.plugins.microbot;

import com.google.common.base.Strings;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.cache.Rs2CacheManager;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.ui.FontManager;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;
import net.runelite.client.ui.overlay.components.ButtonComponent;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.awt.*;
import java.util.Map;

public class MicrobotOverlay extends OverlayPanel {
    MicrobotPlugin plugin;
    MicrobotConfig config;
    public final ButtonComponent cacheButton;

    @Inject
    MicrobotOverlay(MicrobotPlugin plugin, MicrobotConfig config) {
        super(plugin);
        setPosition(OverlayPosition.TOP_RIGHT);
        this.plugin = plugin;
        this.config = config;
        
        // Initialize cache management button
        cacheButton = new ButtonComponent("Clear Caches");
        cacheButton.setPreferredSize(new Dimension(120, 25));
        cacheButton.setParentOverlay(this);
        cacheButton.setFont(FontManager.getRunescapeBoldFont());
        cacheButton.setOnClick(() -> {
            Rs2CacheManager.invalidateAllCaches(true);
            Microbot.openPopUp("Cache Manager", String.format("Cleared Cache:<br><br><col=ffffff>%s</col>", "All caches have been invalidated!"));
        });
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(200, 300));

        for (Map.Entry<WorldPoint, Integer> dangerousTile : Rs2Tile.getDangerousGraphicsObjectTiles().entrySet()) {
            drawTile(graphics, dangerousTile.getKey(), Color.RED, dangerousTile.getValue().toString());
        }
        
        // Show cache information if enabled in config and not hidden by overlapping widgets
        boolean shouldShowCache = config.showCacheInfo();

        // Always check for actual widget overlap with our render position
        if (shouldShowCache) {
            Rectangle estimatedCacheBounds = estimateCacheInfoBounds();
            if (estimatedCacheBounds != null) {
                // Convert estimatedCacheBounds (panel/canvas) to canvas coordinates if needed
                // Pass as canvas coordinates to plugin.hasWidgetOverlapWithBounds
                shouldShowCache = !plugin.hasWidgetOverlapWithBounds(estimatedCacheBounds);
            }
        }

        if (shouldShowCache) {
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Cache Manager")
                .color(Color.CYAN)
                .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(cacheButton);
            // Only hook mouse listener if visible
            cacheButton.hookMouseListener();

            // Render cache statistics tooltip when hovering over the button
            if (cacheButton.isHovered()) {
                renderCacheStatsTooltip(graphics);
            }
        } else {
            // Unhook mouse listener if not visible
            cacheButton.unhookMouseListener();
        }
        
        return super.render(graphics);
    }

    /**
     * Estimates the bounds where cache information would be rendered
     * This helps determine potential overlap before actually rendering
     */
    private Rectangle estimateCacheInfoBounds() {
        try {
            // Get the current overlay position and size
            Rectangle currentBounds = this.getBounds();
            
            // If we don't have bounds yet, use preferred location and calculate estimated size
            if (currentBounds == null || (currentBounds.width == 0 && currentBounds.height == 0)) {
                java.awt.Point preferredLocation = this.getPreferredLocation();
                if (preferredLocation == null) {
                    // Use default DYNAMIC position (top-left area)
                    preferredLocation = new java.awt.Point(10, 10);
                }
                
                // Estimate cache info panel size based on typical components
                // Title: ~150x20, Button: ~120x25, spacing: ~5-10px
                int estimatedWidth = 200;  // panelComponent preferred width
                int estimatedHeight = 60; // Title + separator + button + padding
                
                return new Rectangle(preferredLocation.x, preferredLocation.y, 
                                   estimatedWidth, estimatedHeight);
            }
            
            // If we have existing bounds, estimate where cache info would appear within them
            // Cache info appears after dangerous tiles, so add some offset
            int cacheStartY = currentBounds.y + 10; // Some offset for dangerous tiles rendering
            int cacheHeight = 60; // Estimated height for cache components
            
            return new Rectangle(currentBounds.x, cacheStartY, 
                               currentBounds.width, cacheHeight);
                               
        } catch (Exception e) {
            // Fallback: return a small default area
            return new Rectangle(10, 10, 200, 60);
        }
    }

    /**
     * Renders cache statistics as a tooltip box when hovering over the button
     */
    private void renderCacheStatsTooltip(Graphics2D graphics) {
        try {
            // Set smaller font for tooltip
            Font originalFont = graphics.getFont();
            Font tooltipFont = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
            graphics.setFont(tooltipFont);
            
            // Get cache statistics
            String cacheStats = Rs2CacheManager.getAllCacheStatisticsString();
            
            // Get object type statistics
            String objectTypeStats = "";
            try {
                objectTypeStats = net.runelite.client.plugins.microbot.util.cache.Rs2ObjectCache.getObjectTypeStatistics();
            } catch (Exception e) {
                objectTypeStats = "Object stats unavailable";
            }
            
            // Combine cache stats with object type stats
            String[] cacheLines = cacheStats.split("\n");
            String[] allLines = new String[cacheLines.length + 1];
            System.arraycopy(cacheLines, 0, allLines, 0, cacheLines.length);
            allLines[cacheLines.length] = objectTypeStats;
            
            // Calculate tooltip position (next to the button)
            Rectangle buttonBounds = cacheButton.getBounds();
            int tooltipX = buttonBounds.x + buttonBounds.width + 10;
            int tooltipY = buttonBounds.y;
            
            // Calculate tooltip dimensions
            FontMetrics metrics = graphics.getFontMetrics();
            int maxWidth = 0;
            int totalHeight = 0;
            
            for (String line : allLines) {
                if (!line.trim().isEmpty()) {
                    int lineWidth = metrics.stringWidth(line.trim());
                    maxWidth = Math.max(maxWidth, lineWidth);
                    totalHeight += metrics.getHeight();
                }
            }
            
            int padding = 6;
            int backgroundWidth = maxWidth + (padding * 2);
            int backgroundHeight = totalHeight + (padding * 2);
            
            // Draw semi-transparent background
            Color backgroundColor = new Color(0, 0, 0, 180);
            graphics.setColor(backgroundColor);
            graphics.fillRect(tooltipX, tooltipY, backgroundWidth, backgroundHeight);
            
            // Draw border
            graphics.setColor(Color.CYAN);
            graphics.drawRect(tooltipX, tooltipY, backgroundWidth, backgroundHeight);
            
            // Render cache statistics text
            graphics.setColor(Color.WHITE);
            int lineY = tooltipY + metrics.getAscent() + padding;
            
            for (String line : allLines) {
                if (!line.trim().isEmpty()) {
                    graphics.drawString(line.trim(), tooltipX + padding, lineY);
                    lineY += metrics.getHeight();
                }
            }
            
            // Restore original font
            graphics.setFont(originalFont);
            
        } catch (Exception e) {
            // Silent fail for overlay rendering
        }
    }

    private void drawTile(Graphics2D graphics, WorldPoint point, Color color, @Nullable String label) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        if (point.distanceTo(playerLocation) >= 32) {
            return;
        }

        LocalPoint lp;
        if (Microbot.getClient().getTopLevelWorldView().getScene().isInstance()) {
            WorldPoint worldPoint = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView().getScene(), point).stream().findFirst().get();
            lp = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint);
        } else {
            lp = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), point);
        }

        if (lp == null) {
            return;
        }


        Polygon poly = Perspective.getCanvasTilePoly(Microbot.getClient(), lp);
        if (poly != null) {
            OverlayUtil.renderPolygon(graphics, poly, color, new Color(0, 0, 0, 50), new BasicStroke(2f));
        }

        if (!Strings.isNullOrEmpty(label)) {
            Point canvasTextLocation = Perspective.getCanvasTextLocation(Microbot.getClient(), graphics, lp, label, 0);
            if (canvasTextLocation != null) {
                OverlayUtil.renderTextLocation(graphics, canvasTextLocation, label, color);
            }
        }
    }
}

