package net.runelite.client.plugins.microbot.shortestpath;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayPriority;

import java.awt.*;
import java.util.List;

public class PathMinimapOverlay extends Overlay {
    private static final int TILE_WIDTH = 2;
    private static final int TILE_HEIGHT = 2;

    private final Client client;
    private final ShortestPathPlugin plugin;
    private final ShortestPathConfig config;

    @Inject
    private PathMinimapOverlay(Client client, ShortestPathPlugin plugin, ShortestPathConfig config) {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(OverlayPriority.LOW);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
		if (!plugin.drawMinimap) {
			return null;
		}
        if (ShortestPathPlugin.getPathfinder() == null)
            return null;

        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        graphics.setClip(plugin.getMinimapClipArea());

        List<WorldPoint> pathPoints = plugin.getPathfinder().getPath();
        Color pathColor = plugin.getPathfinder().isDone() ? plugin.colourPath : plugin.colourPathCalculating;
        for (WorldPoint pathPoint : pathPoints) {
            if (pathPoint.getPlane() != client.getPlane()) {
                continue;
            }

            drawOnMinimap(graphics, pathPoint, pathColor);
        }

        return null;
    }

    private void drawOnMinimap(Graphics2D graphics, WorldPoint location, Color color) {
        for (WorldPoint point : WorldPoint.toLocalInstance(client, location)) {
            LocalPoint lp = LocalPoint.fromWorld(client, point);

            if (lp == null) {
                continue;
            }

            Point posOnMinimap = Perspective.localToMinimap(client, lp);

            if (posOnMinimap == null) {
                continue;
            }

            renderMinimapRect(client, graphics, posOnMinimap, TILE_WIDTH, TILE_HEIGHT, color);
        }
    }

    public static void renderMinimapRect(Client client, Graphics2D graphics, Point center, int width, int height, Color color) {
        double angle = client.getCameraYawTarget() * Math.PI / 1024.0d;

        graphics.setColor(color);
        graphics.rotate(angle, center.getX(), center.getY());
        graphics.fillRect(center.getX() - width / 2, center.getY() - height / 2, width, height);
        graphics.rotate(-angle, center.getX(), center.getY());
    }
}
