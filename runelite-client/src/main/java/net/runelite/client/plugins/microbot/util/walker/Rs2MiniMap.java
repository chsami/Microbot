package net.runelite.client.plugins.microbot.util.walker;

import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.SpriteID;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.ComponentID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.api.Varbits;
import net.runelite.client.plugins.microbot.util.coords.Rs2LocalPoint;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.annotation.Nullable;
import java.awt.*;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class Rs2MiniMap {

    /**
     * Converts a {@link LocalPoint} to a minimap coordinate {@link Point}.
     *
     * @param localPoint The local point to convert.
     * @return The corresponding minimap point, or {@code null} if conversion fails.
     */
    @Nullable
    public static Point localToMinimap(LocalPoint localPoint) {
        if (localPoint == null) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Perspective.localToMinimap(Microbot.getClient(), localPoint))
                .orElse(null);
    }

    /**
     * Converts a {@link WorldPoint} to a minimap coordinate {@link Point}.
     *
     * @param worldPoint The world point to convert.
     * @return The corresponding minimap point, or {@code null} if conversion fails.
     */
    @Nullable
    public static Point worldToMinimap(WorldPoint worldPoint) {
        if (worldPoint == null) return null;

        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), worldPoint);

        if (Microbot.getClient().getTopLevelWorldView().isInstance() && localPoint == null) {
            localPoint = Rs2LocalPoint.fromWorldInstance(worldPoint);
        }

        if (localPoint == null) {
            Microbot.log("Tried to walk worldpoint " + worldPoint + " using the canvas but localpoint returned null");
            return null;
        }

        final LocalPoint lp = localPoint;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Perspective.localToMinimap(Microbot.getClient(), lp))
                .orElse(null);
    }

    /**
     * Retrieves the minimap draw widget based on the current game view mode.
     *
     * @return The minimap draw widget, or {@code null} if not found.
     */
    public static Widget getMinimapDrawWidget() {
        if (Microbot.getClient().isResized()) {
            if (Microbot.getVarbitValue(Varbits.SIDE_PANELS) == 1) {
                return Rs2Widget.getWidget(ComponentID.RESIZABLE_VIEWPORT_BOTTOM_LINE_MINIMAP_DRAW_AREA);
            }
            return Rs2Widget.getWidget(ComponentID.RESIZABLE_VIEWPORT_MINIMAP_DRAW_AREA);
        }
        return Rs2Widget.getWidget(ComponentID.FIXED_VIEWPORT_MINIMAP_DRAW_AREA);
    }

    /**
     * Returns a simple elliptical clip area for the minimap.
     *
     * @return A {@link Shape} representing the minimap clip area.
     */
    private static Shape getMinimapClipAreaSimple() {
        Widget minimapDrawArea = getMinimapDrawWidget();
        if (minimapDrawArea == null) {
            return null;
        }
        Rectangle bounds = minimapDrawArea.getBounds();
        return new Ellipse2D.Double(bounds.getX(), bounds.getY(), bounds.getWidth(), bounds.getHeight());
    }

    /**
     * Retrieves the minimap clipping area as a polygon.
     *
     * @return A {@link Shape} representing the minimap's clickable area.
     */
    public static Shape getMinimapClipArea() {
        Widget minimapWidget = getMinimapDrawWidget();
        if (minimapWidget == null) {
            return null;
        }

        boolean isResized = Microbot.getClient().isResized();
        
        BufferedImage minimapSprite = Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getSpriteManager().getSprite(
                        isResized ? SpriteID.RESIZEABLE_MODE_MINIMAP_ALPHA_MASK : SpriteID.FIXED_MODE_MINIMAP_ALPHA_MASK, 0)).orElse(null);

        if (minimapSprite == null) {
            return getMinimapClipAreaSimple();
        }

        return bufferedImageToPolygon(minimapSprite, minimapWidget.getBounds());
    }

    /**
     * Converts a BufferedImage to a polygon by detecting the border based on the outside color.
     *
     * @param image         The image to convert.
     * @param minimapBounds The bounds of the minimap widget.
     * @return A polygon representing the minimap's clickable area.
     */
    private static Polygon bufferedImageToPolygon(BufferedImage image, Rectangle minimapBounds) {
        Color outsideColour = null;
        Color previousColour;
        final int width = image.getWidth();
        final int height = image.getHeight();
        List<java.awt.Point> points = new ArrayList<>();

        for (int y = 0; y < height; y++) {
            previousColour = outsideColour;
            for (int x = 0; x < width; x++) {
                int rgb = image.getRGB(x, y);
                int a = (rgb & 0xff000000) >>> 24;
                int r = (rgb & 0x00ff0000) >> 16;
                int g = (rgb & 0x0000ff00) >> 8;
                int b = (rgb & 0x000000ff);
                Color colour = new Color(r, g, b, a);
                if (x == 0 && y == 0) {
                    outsideColour = colour;
                    previousColour = colour;
                }
                if (!colour.equals(outsideColour) && previousColour.equals(outsideColour)) {
                    points.add(new java.awt.Point(x, y));
                }
                if ((colour.equals(outsideColour) || x == (width - 1)) && !previousColour.equals(outsideColour)) {
                    points.add(0, new java.awt.Point(x, y));
                }
                previousColour = colour;
            }
        }

        int offsetX = minimapBounds.x;
        int offsetY = minimapBounds.y;
        Polygon polygon = new Polygon();
        for (java.awt.Point point : points) {
            polygon.addPoint(point.x + offsetX, point.y + offsetY);
        }
        return polygon;
    }

    /**
     * Checks if a given point is inside the minimap clipping area.
     *
     * @param point The point to check.
     * @return {@code true} if the point is within the minimap bounds, {@code false} otherwise.
     */
    public static boolean isPointInsideMinimap(Point point) {
        Shape minimapClipArea = getMinimapClipArea();
        return minimapClipArea != null && minimapClipArea.contains(point.getX(), point.getY());
    }
}