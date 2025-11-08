package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.overlay;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.HazardData;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.model.ProjectileData;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.Rs2HazardTracker;
import net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.trackers.Rs2ProjectileTracker;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.*;

/**
 * main scene overlay for PvM combat system
 * renders projectiles, hazards, and safe tiles directly on game world
 * uses DYNAMIC position to render in-world indicators
 */
@Slf4j
@Singleton
public class Rs2PvMSceneOverlay extends Overlay {

    private final Client client;
    private final Rs2ProjectileTracker projectileTracker;
    private final Rs2HazardTracker hazardTracker;

    @Inject
    public Rs2PvMSceneOverlay(
        Client client,
        Rs2ProjectileTracker projectileTracker,
        Rs2HazardTracker hazardTracker
    ) {
        this.client = client;
        this.projectileTracker = projectileTracker;
        this.hazardTracker = hazardTracker;

        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Rs2PvMOverlayConfig config = Rs2PvMOverlayConfig.getInstance();
        
        if (!config.isEnableOverlay()) {
            return null;
        }

        try {
            // render hazards first (background layer)
            if (config.isShowHazards()) {
                renderHazards(graphics);
            }

            // render safe tiles (middle layer)
            if (config.isShowSafeTiles()) {
                renderSafeTiles(graphics);
            }

            // render projectiles last (foreground layer)
            if (config.isShowProjectiles()) {
                renderProjectiles(graphics);
            }

        } catch (Exception ex) {
            log.error("Error rendering PvM scene overlay: {}", ex.getMessage(), ex);
        }

        return null;
    }

    /**
     * render all tracked projectiles
     */
    private void renderProjectiles(Graphics2D graphics) {
        if (projectileTracker == null) {
            return;
        }

        for (ProjectileData data : projectileTracker.values()) {
            if (data == null) {
                continue;
            }

            renderProjectile(graphics, data);
        }
    }

    /**
     * render single projectile based on style
     */
    private void renderProjectile(Graphics2D graphics, ProjectileData data) {
        Rs2PvMOverlayConfig config = Rs2PvMOverlayConfig.getInstance();
        Rs2PvMOverlayConfig.ProjectileStyle style = config.getProjectileStyle();

        switch (style) {
            case TARGET_TILE:
                renderProjectileTargetTile(graphics, data);
                break;
            case CURRENT_POSITION:
                renderProjectileCurrentPosition(graphics, data);
                break;
            case FULL_PATH:
                renderProjectileFullPath(graphics, data);
                break;
        }
    }

    /**
     * render projectile target tile (where it will land)
     */
    private void renderProjectileTargetTile(Graphics2D graphics, ProjectileData data) {
        Rs2PvMOverlayConfig config = Rs2PvMOverlayConfig.getInstance();
        
        WorldPoint target = data.getTargetWorldPoint();
        if (target == null) {
            return;
        }

        LocalPoint local = LocalPoint.fromWorld(client, target);
        if (local == null) {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, local);
        if (poly == null) {
            return;
        }

        // color based on AOE vs player-targeted
        Color color = data.isAoe() ? config.getAoeProjectileColor() : config.getPlayerProjectileColor();
        
        // render tile outline
        OverlayUtil.renderPolygon(graphics, poly, color, 
            new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() / 3),
            new BasicStroke(config.getProjectileOutlineWidth()));

        // render projectile info if enabled
        if (config.isShowProjectileInfo()) {
            String info = String.format("ID:%d T:%d", data.getId(), data.getTicksUntilImpact());
            net.runelite.api.Point textLoc = Perspective.getCanvasTextLocation(client, graphics, local, info, 0);
            if (textLoc != null) {
                OverlayUtil.renderTextLocation(graphics, textLoc, info, color);
            }
        }
    }

    /**
     * render projectile current position
     */
    private void renderProjectileCurrentPosition(Graphics2D graphics, ProjectileData data) {
        Rs2PvMOverlayConfig config = Rs2PvMOverlayConfig.getInstance();
        
        if (!data.hasRecentPositionUpdate()) {
            // fall back to target tile if position outdated
            renderProjectileTargetTile(graphics, data);
            return;
        }

        LocalPoint currentPos = data.getCurrentPosition();
        if (currentPos == null) {
            return;
        }

        // render small marker at current position
        net.runelite.api.Point canvasPoint = Perspective.localToCanvas(client, currentPos, 0);
        if (canvasPoint == null) {
            return;
        }

        Color color = data.isAoe() ? config.getAoeProjectileColor() : config.getPlayerProjectileColor();
        
        // draw circle at current position
        int radius = 8;
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(config.getProjectileOutlineWidth()));
        graphics.drawOval(canvasPoint.getX() - radius, canvasPoint.getY() - radius, radius * 2, radius * 2);

        // draw filled center
        Color fillColor = new Color(color.getRed(), color.getGreen(), color.getBlue(), color.getAlpha() / 2);
        graphics.setColor(fillColor);
        graphics.fillOval(canvasPoint.getX() - radius / 2, canvasPoint.getY() - radius / 2, radius, radius);

        // also render target tile for reference
        renderProjectileTargetTile(graphics, data);
    }

    /**
     * render full projectile path (current position + trajectory to target)
     */
    private void renderProjectileFullPath(Graphics2D graphics, ProjectileData data) {
        Rs2PvMOverlayConfig config = Rs2PvMOverlayConfig.getInstance();
        
        if (!data.hasRecentPositionUpdate()) {
            renderProjectileTargetTile(graphics, data);
            return;
        }

        LocalPoint currentPos = data.getCurrentPosition();
        WorldPoint target = data.getTargetWorldPoint();
        
        if (currentPos == null || target == null) {
            return;
        }

        LocalPoint targetLocal = LocalPoint.fromWorld(client, target);
        if (targetLocal == null) {
            return;
        }

        // get canvas points
        net.runelite.api.Point currentCanvas = Perspective.localToCanvas(client, currentPos, 0);
        net.runelite.api.Point targetCanvas = Perspective.getCanvasTextLocation(client, graphics, targetLocal, "", 0);

        if (currentCanvas == null || targetCanvas == null) {
            return;
        }

        Color color = data.isAoe() ? config.getAoeProjectileColor() : config.getPlayerProjectileColor();

        // draw line from current position to target
        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(config.getProjectileOutlineWidth(), BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND, 
            0, new float[]{5, 5}, 0)); // dashed line
        graphics.drawLine(currentCanvas.getX(), currentCanvas.getY(), targetCanvas.getX(), targetCanvas.getY());

        // render current position marker
        renderProjectileCurrentPosition(graphics, data);
    }

    /**
     * render all hazard tiles
     */
    private void renderHazards(Graphics2D graphics) {
        if (hazardTracker == null) {
            return;
        }

        for (HazardData hazard : hazardTracker.values()) {
            if (hazard == null || hazard.getLocation() == null) {
                continue;
            }

            renderHazardTile(graphics, hazard);
        }
    }

    /**
     * render single hazard tile
     */
    private void renderHazardTile(Graphics2D graphics, HazardData hazard) {
        Rs2PvMOverlayConfig config = Rs2PvMOverlayConfig.getInstance();
        
        LocalPoint local = LocalPoint.fromWorld(client, hazard.getLocation());
        if (local == null) {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, local);
        if (poly == null) {
            return;
        }

        // render hazard with danger color
        Color outlineColor = config.getHazardColor();
        Color fillColor = new Color(outlineColor.getRed(), outlineColor.getGreen(), outlineColor.getBlue(), 
            outlineColor.getAlpha() / 2);

        OverlayUtil.renderPolygon(graphics, poly, outlineColor, fillColor,
            new BasicStroke(config.getHazardOutlineWidth()));
    }

    /**
     * render safe tiles near player
     * useful for quick dodging decisions
     */
    private void renderSafeTiles(Graphics2D graphics) {
        WorldPoint playerLoc = client.getLocalPlayer().getWorldLocation();
        if (playerLoc == null) {
            return;
        }

        // check 3x3 area around player for safe tiles
        int searchRadius = 3;
        for (int dx = -searchRadius; dx <= searchRadius; dx++) {
            for (int dy = -searchRadius; dy <= searchRadius; dy++) {
                WorldPoint tile = playerLoc.dx(dx).dy(dy);
                
                // check if tile is safe
                if (hazardTracker != null && !hazardTracker.isLocationDangerous(tile)) {
                    renderSafeTile(graphics, tile);
                }
            }
        }
    }

    /**
     * render single safe tile
     */
    private void renderSafeTile(Graphics2D graphics, WorldPoint location) {
        Rs2PvMOverlayConfig config = Rs2PvMOverlayConfig.getInstance();
        
        LocalPoint local = LocalPoint.fromWorld(client, location);
        if (local == null) {
            return;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, local);
        if (poly == null) {
            return;
        }

        // subtle green highlight for safe tiles
        Color color = config.getSafeTileColor();
        OverlayUtil.renderPolygon(graphics, poly, 
            new Color(color.getRed(), color.getGreen(), color.getBlue(), 0), // no outline
            color,
            new BasicStroke(1));
    }
}
