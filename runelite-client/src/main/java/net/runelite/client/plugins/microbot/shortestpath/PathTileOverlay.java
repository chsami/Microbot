package net.runelite.client.plugins.microbot.shortestpath;

import com.google.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.Tile;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.CollisionMap;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.awt.*;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;

public class PathTileOverlay extends Overlay {
    private final Client client;
    private final ShortestPathPlugin plugin;
    private static final int TRANSPORT_LABEL_GAP = 3;

    @Inject
    public PathTileOverlay(Client client, ShortestPathPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setPriority(Overlay.PRIORITY_LOW);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    private void renderTransports(Graphics2D graphics) {
        if (plugin == null) return;
        if (plugin.getTransports() == null) return;
        if (plugin.getPathfinderFuture() == null || !plugin.getPathfinderFuture().isDone()) return;
        for (WorldPoint a : plugin.getTransports().keySet()) {
            drawTile(graphics, a, plugin.colourTransports, -1, true);

            Point ca = tileCenter(a);

            if (ca == null) {
                continue;
            }

            StringBuilder s = new StringBuilder();
            for (Transport b : plugin.getTransports().getOrDefault(a, new HashSet<>())) {
                for (WorldPoint destination : WorldPoint.toLocalInstance(client, b.getDestination())) {
                    Point cb = tileCenter(destination);
                    if (cb != null) {
                        graphics.drawLine(ca.getX(), ca.getY(), cb.getX(), cb.getY());
                    }
                    if (destination.getPlane() > a.getPlane()) {
                        s.append("+");
                    } else if (destination.getPlane() < a.getPlane()) {
                        s.append("-");
                    } else {
                        s.append("=");
                    }
                }
            }
            graphics.setColor(Color.WHITE);
            graphics.drawString(s.toString(), ca.getX(), ca.getY());
        }
    }

    private void renderCollisionMap(Graphics2D graphics) {
        CollisionMap map = plugin.getMap();
        for (Tile[] row : client.getScene().getTiles()[client.getPlane()]) {
            for (Tile tile : row) {
                if (tile == null) {
                    continue;
                }

                Polygon tilePolygon = Perspective.getCanvasTilePoly(client, tile.getLocalLocation());

                if (tilePolygon == null) {
                    continue;
                }

                WorldPoint location = client.isInInstancedRegion() ?
                        WorldPoint.fromLocalInstance(client, tile.getLocalLocation()) : tile.getWorldLocation();
                int x = location.getX();
                int y = location.getY();
                int z = location.getPlane();

                String s = (!map.n(x, y, z) ? "n" : "") +
                        (!map.s(x, y, z) ? "s" : "") +
                        (!map.e(x, y, z) ? "e" : "") +
                        (!map.w(x, y, z) ? "w" : "");

                if (map.isBlocked(x, y, z)) {
                    graphics.setColor(plugin.colourCollisionMap);
                    graphics.fill(tilePolygon);
                }
                if (!s.isEmpty() && !s.equals("nsew")) {
                    graphics.setColor(Color.WHITE);
                    int stringX = (int) (tilePolygon.getBounds().getCenterX() - graphics.getFontMetrics().getStringBounds(s, graphics).getWidth() / 2);
                    int stringY = (int) tilePolygon.getBounds().getCenterY();
                    graphics.drawString(s, stringX, stringY);
                }
            }
        }
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (plugin.drawTransports) {
            this.renderTransports(graphics);
        }

        if (plugin.drawCollisionMap) {
            this.renderCollisionMap(graphics);
        }

        if (plugin.drawTiles && plugin.getPathfinder() != null && plugin.getPathfinder().getPath() != null) {
            Color color;
            if (plugin.getPathfinder().isDone()) {
                color = new Color(
                        plugin.colourPath.getRed(),
						plugin.colourPath.getGreen(),
						plugin.colourPath.getBlue(),
					 plugin.colourPath.getAlpha() / 2);
            } else {
                color = new Color(
                        plugin.colourPathCalculating.getRed(),
						plugin.colourPathCalculating.getGreen(),
						plugin.colourPathCalculating.getBlue(),
					 plugin.colourPathCalculating.getAlpha() / 2);
            }

            List<WorldPoint> path = plugin.getPathfinder().getPath();
            int counter = 0;
            if (TileStyle.LINES.equals(plugin.pathStyle)) {
                for (int i = 1; i < path.size(); i++) {
                    float step = i / (float) path.size();
                    Color newColor = generateGradient(step);
                    newColor = new Color(newColor.getRed(), newColor.getGreen(), newColor.getBlue(), 75);
                    drawLine(graphics, path.get(i - 1), path.get(i), newColor, 1 + counter++);
                    drawTransportInfo(graphics, path.get(i - 1), path.get(i));
                }
            } else {
                boolean showTiles = TileStyle.TILES.equals(plugin.pathStyle);
                for (int i = 0; i < path.size(); i++) {
                    float step = i / (float) path.size();
                    Color newColor = generateGradient(step);
                    newColor = new Color(newColor.getRed(), newColor.getGreen(), newColor.getBlue(), 75);
                    drawTile(graphics, path.get(i), newColor, counter++, showTiles);
                    drawTransportInfo(graphics, path.get(i), (i + 1 == path.size()) ? null : path.get(i + 1));
                }
            }
        }

        return null;
    }

    private Point tileCenter(WorldPoint b) {
        if (b.getPlane() != client.getPlane()) {
            return null;
        }

        LocalPoint lp = LocalPoint.fromWorld(client, b);
        if (lp == null) {
            return null;
        }

        Polygon poly = Perspective.getCanvasTilePoly(client, lp);
        if (poly == null) {
            return null;
        }

        int cx = poly.getBounds().x + poly.getBounds().width / 2;
        int cy = poly.getBounds().y + poly.getBounds().height / 2;
        return new Point(cx, cy);
    }

    private void drawTile(Graphics2D graphics, WorldPoint location, Color color, int counter, boolean draw) {
        for (WorldPoint point : WorldPoint.toLocalInstance(client, location)) {
            if (point.getPlane() != client.getPlane()) {
                continue;
            }

            LocalPoint lp = LocalPoint.fromWorld(client, point);
            if (lp == null) {
                continue;
            }

            Polygon poly = Perspective.getCanvasTilePoly(client, lp);
            if (poly == null) {
                continue;
            }

            if (draw) {
                graphics.setColor(color);
                graphics.fill(poly);
            }

            drawCounter(graphics, poly.getBounds().getCenterX(), poly.getBounds().getCenterY(), counter);
        }
    }

    private void drawLine(Graphics2D graphics, WorldPoint startLoc, WorldPoint endLoc, Color color, int counter) {
        Collection<WorldPoint> starts = WorldPoint.toLocalInstance(client, startLoc);
        Collection<WorldPoint> ends = WorldPoint.toLocalInstance(client, endLoc);

        if (starts.isEmpty() || ends.isEmpty()) {
            return;
        }

        WorldPoint start = starts.iterator().next();
        WorldPoint end = ends.iterator().next();

        final int z = client.getPlane();
        if (start.getPlane() != z) {
            return;
        }

        LocalPoint lpStart = LocalPoint.fromWorld(client, start);
        LocalPoint lpEnd = LocalPoint.fromWorld(client, end);

        if (lpStart == null || lpEnd == null) {
            return;
        }

        final int startHeight = Perspective.getTileHeight(client, lpStart, z);
        final int endHeight = Perspective.getTileHeight(client, lpEnd, z);

        Point p1 = Perspective.localToCanvas(client, lpStart.getX(), lpStart.getY(), startHeight);
        Point p2 = Perspective.localToCanvas(client, lpEnd.getX(), lpEnd.getY(), endHeight);

        if (p1 == null || p2 == null) {
            return;
        }

        Line2D.Double line = new Line2D.Double(p1.getX(), p1.getY(), p2.getX(), p2.getY());

        graphics.setColor(color);
        graphics.setStroke(new BasicStroke(4));
        graphics.draw(line);

        if (counter == 1) {
            drawCounter(graphics, p1.getX(), p1.getY(), 0);
        }
        drawCounter(graphics, p2.getX(), p2.getY(), counter);
    }

    private void drawCounter(Graphics2D graphics, double x, double y, int counter) {
        if (plugin.getPathfinder() == null) return;
        if (counter >= 0 && !TileCounter.DISABLED.equals(plugin.showTileCounter)) {
            int n = plugin.tileCounterStep > 0 ? plugin.tileCounterStep : 1;
            int s = plugin.getPathfinder().getPath().size();
            if ((counter % n != 0) && (s != (counter + 1))) {
                return;
            }
            if (TileCounter.REMAINING.equals(plugin.showTileCounter)) {
                counter = s - counter - 1;
            }
            if (n > 1 && counter == 0) {
                return;
            }
            String counterText = Integer.toString(counter);
            graphics.setColor(plugin.colourText);
            graphics.drawString(
                    counterText,
                    (int) (x - graphics.getFontMetrics().getStringBounds(counterText, graphics).getWidth() / 2), (int) y);
        }
    }

    private void drawTransportInfo(Graphics2D graphics, WorldPoint location, WorldPoint locationEnd) {
        if (locationEnd == null || !plugin.showTransportInfo) {
            return;
        }
        for (WorldPoint point : WorldPoint.toLocalInstance(client, location)) {
            for (WorldPoint pointEnd : WorldPoint.toLocalInstance(client, locationEnd))
            {
                if (point.getPlane() != client.getPlane()) {
                    continue;
                }

                int vertical_offset = 0;
                for (Transport transport : plugin.getTransports().getOrDefault(point, new HashSet<>())) {
                    if (pointEnd == null || !pointEnd.equals(transport.getDestination())) {
                        continue;
                    }

                    String text = transport.getDisplayInfo();
                    if (text == null || text.isEmpty()) {
                        continue;
                    }

                    LocalPoint lp = LocalPoint.fromWorld(client, point);
                    if (lp == null) {
                        continue;
                    }

                    Point p = Perspective.localToCanvas(client, lp, client.getPlane());
                    if (p == null) {
                        continue;
                    }

                    Rectangle2D textBounds = graphics.getFontMetrics().getStringBounds(text, graphics);
                    double height = textBounds.getHeight();
                    int x = (int) (p.getX() - textBounds.getWidth() / 2);
                    int y = (int) (p.getY() - height) - (vertical_offset);
                    graphics.setColor(Color.BLACK);
                    graphics.drawString(text, x + 1, y + 1);
                    graphics.setColor(plugin.colourText);
                    graphics.drawString(text, x, y);

                    vertical_offset += (int) height + TRANSPORT_LABEL_GAP;
                }
            }
        }
    }

    public static Color generateGradient(float step) {
        if (step < 0) {
            step = 0;
        } else if (step > 1) {
            step = 1;
        }

        float[] startComponents = Color.RED.getRGBColorComponents(null);
        float[] endComponents = Color.GREEN.getRGBColorComponents(null);

        float[] interpolatedComponents = new float[3];

        for (int j = 0; j < 3; j++) {
            interpolatedComponents[j] = startComponents[j] + step * (endComponents[j] - startComponents[j]);
        }

        return new Color(interpolatedComponents[0], interpolatedComponents[1], interpolatedComponents[2]);
    }
}
