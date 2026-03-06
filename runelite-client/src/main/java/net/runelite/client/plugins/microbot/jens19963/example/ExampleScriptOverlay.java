package net.runelite.client.plugins.microbot.jens19963.example;

import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import net.runelite.client.plugins.microbot.util.reachable.Rs2Reachable;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import javax.inject.Inject;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class ExampleScriptOverlay extends Overlay {

    private static final Color REACHABLE_COLOR = new Color(0, 255, 0, 50);
    private static final Color REACHABLE_BORDER_COLOR = new Color(0, 255, 0, 150);

    @Inject
    private Client client;

    @Inject
    public ExampleScriptOverlay() {
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!Microbot.isLoggedIn())
        {
            return null;
        }

        WorldPoint playerLocation = WorldPoint.fromLocalInstance(client, client.getLocalPlayer().getLocalLocation());
        if (playerLocation == null)
        {
            return null;
        }

        // Get reachable tiles as packed ints
        var reachablePacked = Rs2Reachable.getReachableTiles(playerLocation);
        if (reachablePacked == null || reachablePacked.isEmpty())
        {
            return null;
        }

        // Convert packed ints back to world points in current plane/instance
        List<WorldPoint> reachableWorldPoints = new ArrayList<>();
        for (int packed : reachablePacked)
        {
            WorldPoint wp = WorldPointUtil.unpackWorldPoint(packed);
            if (wp.getPlane() == playerLocation.getPlane())
            {
                reachableWorldPoints.add(wp);
            }
        }

        for (WorldPoint worldPoint : reachableWorldPoints)
        {
            LocalPoint localPoint = LocalPoint.fromWorld(client, worldPoint);
            if (localPoint == null)
            {
                continue;
            }

            Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
            if (poly == null)
            {
                continue;
            }

            graphics.setColor(REACHABLE_COLOR);
            graphics.fillPolygon(poly);

            graphics.setColor(REACHABLE_BORDER_COLOR);
            graphics.drawPolygon(poly);
        }

        return null;
    }
}
