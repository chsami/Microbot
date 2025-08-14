package net.runelite.client.plugins.microbot;

import com.google.common.base.Strings;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.cache.Rs2CacheManager;
import net.runelite.client.plugins.microbot.util.cache.Rs2ObjectCache;
import net.runelite.client.plugins.microbot.util.cache.util.Rs2CacheLoggingUtils;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
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
    public final ButtonComponent logCacheButton;

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
            try {
                Rs2CacheManager.invalidateAllCaches(false);
                Rs2CacheManager.triggerSceneScansForAllCaches(); // Repopulate caches immediately
                //Microbot.getClientThread().runOnClientThreadOptional(
                  //  ()-> {Microbot.openPopUp("Cache Manager", String.format("Cleared Cache:<br><br><col=ffffff>%s</col>", "All caches have been invalidated!"));
                    //    return true;}
                    //); // causes Exception java.lang.IllegalStateException: component does not exist
            } catch (Exception e) {
                Microbot.log("Cache Manager: All caches have been invalidated and repopulated!");
            }
        });
        
        // Initialize cache logging button
        logCacheButton = new ButtonComponent("Log to Files");
        logCacheButton.setPreferredSize(new Dimension(60, 25));
        logCacheButton.setParentOverlay(this);
        logCacheButton.setFont(FontManager.getRunescapeBoldFont());
        logCacheButton.setOnClick(() -> {
            // Run on separate thread to avoid blocking UI
            new Thread(() -> {
                try {
                    Rs2CacheLoggingUtils.logAllCachesToFiles();
                    //Microbot.openPopUp("Cache Logger", String.format("Cache Logging:<br><br><col=ffffff>%s</col>", "All cache states dumped to files successfully!"));// causes Exception 
                } catch (Exception e) {                  
                    // Fallback to console if popup fails
                    Microbot.log(" \"All cache states dumped to files successfully!\"" + e.getMessage());                    
                }
            }).start();
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
            shouldShowCache = shouldShowCache && Microbot.isLoggedIn() && !Rs2Bank.isOpen() && !Rs2Widget.isWidgetVisible(InterfaceID.Worldmap.CLOSE) && !Rs2GrandExchange.isOpen();
        }

        if (shouldShowCache) {
            panelComponent.getChildren().add(TitleComponent.builder()
                .text("Cache Manager")
                .color(Color.CYAN)
                .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(cacheButton);
            panelComponent.getChildren().add(logCacheButton);
            
            // Only hook mouse listeners if visible
            cacheButton.hookMouseListener();
            logCacheButton.hookMouseListener();

            // Render cache statistics tooltip when hovering over the clear cache button
            if (cacheButton.isHovered()) {
                renderCacheStatsTooltip(graphics);
            }
            
            // Render logging info tooltip when hovering over the log cache button
            if (logCacheButton.isHovered()) {
                renderLogCacheTooltip(graphics);
            }
        } else {
            // Unhook mouse listeners if not visible
            cacheButton.unhookMouseListener();
            logCacheButton.unhookMouseListener();
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
                int estimatedHeight = 85; // Title + separator + 2 buttons + padding
                
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
                objectTypeStats = Rs2ObjectCache.getObjectTypeStatistics();
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

    /**
     * Renders logging information as a tooltip box when hovering over the log cache button
     */
    private void renderLogCacheTooltip(Graphics2D graphics) {
        try {
            // Set smaller font for tooltip
            Font originalFont = graphics.getFont();
            Font tooltipFont = new Font(Font.SANS_SERIF, Font.PLAIN, 10);
            graphics.setFont(tooltipFont);
            
            // Define tooltip content
            String[] lines = {
                "Log Cache States to Files",
                "",
                "• Saves all cache data to log files",
                "• Files saved in ~/.runelite/microbot-plugins/cache/",
                "• Includes: NPCs, Objects, Ground Items,", 
                "  Skills, Varbits, VarPlayers, Quests",
                "• No console output (file only)",
                "• Useful for analysis and debugging"
            };
            
            // Calculate tooltip position (next to the button)
            Rectangle buttonBounds = logCacheButton.getBounds();
            int tooltipX = buttonBounds.x + buttonBounds.width + 10;
            int tooltipY = buttonBounds.y;
            
            // Calculate tooltip dimensions
            FontMetrics metrics = graphics.getFontMetrics();
            int maxWidth = 0;
            int totalHeight = 0;
            
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    int lineWidth = metrics.stringWidth(line.trim());
                    maxWidth = Math.max(maxWidth, lineWidth);
                    totalHeight += metrics.getHeight();
                } else {
                    totalHeight += metrics.getHeight() / 2; // Half height for empty lines
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
            graphics.setColor(Color.GREEN);
            graphics.drawRect(tooltipX, tooltipY, backgroundWidth, backgroundHeight);
            
            // Render tooltip text
            graphics.setColor(Color.WHITE);
            int lineY = tooltipY + metrics.getAscent() + padding;
            
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    graphics.drawString(line.trim(), tooltipX + padding, lineY);
                    lineY += metrics.getHeight();
                } else {
                    lineY += metrics.getHeight() / 2; // Half height for empty lines
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

