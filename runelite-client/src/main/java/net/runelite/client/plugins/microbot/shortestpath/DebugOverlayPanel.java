package net.runelite.client.plugins.microbot.shortestpath;

import lombok.Getter;
import lombok.Setter;
import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.ComponentConstants;
import net.runelite.client.ui.overlay.components.LayoutableRenderableEntity;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.*;
import java.util.List;

public class DebugOverlayPanel extends OverlayPanel {
    private final ShortestPathPlugin plugin;
    private final SeparatorLine separator;

    @Inject
    public DebugOverlayPanel(ShortestPathPlugin plugin) {
        super(plugin);
        this.plugin = plugin;

        separator = new SeparatorLine();
        separator.setColor(new Color(0, true)); // Invisible color

        setPosition(OverlayPosition.TOP_LEFT);
    }

    private LineComponent makeLine(String left, String right) {
        return LineComponent.builder()
                .left(left)
                .right(right)
                .build();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        Pathfinder pathfinder = ShortestPathPlugin.getPathfinder();
        Pathfinder.PathfinderStats stats;
        if (pathfinder == null || (stats = pathfinder.getStats()) == null) {
            return null;
        }

        List<LayoutableRenderableEntity> components = panelComponent.getChildren();

        components.add(
                TitleComponent.builder()
                        .text("Shortest Path Debug")
                        .color(Color.ORANGE)
                        .build()
        );

        components.add(separator);

        String pathLength = Integer.toString(pathfinder.getPath().size());
        components.add(makeLine("Path Length:", pathLength));

        components.add(separator);

        String nodes = Integer.toString(stats.getNodesChecked());
        components.add(makeLine("Nodes:", nodes));

        String transports = Integer.toString(stats.getTransportsChecked());
        components.add(makeLine("Transports:", transports));

        String totalNodes = Integer.toString(stats.getTotalNodesChecked());
        components.add(makeLine("Total:", totalNodes));

        components.add(separator);

        double milliTime = stats.getElapsedTimeNanos() / 1000000.0;
        String time = String.format("%.2fms", milliTime);
        components.add(makeLine("Time:", time));

        return super.render(graphics);
    }

    @Setter
    private static class SeparatorLine implements LayoutableRenderableEntity {
        private Color color = Color.GRAY;
        private Point preferredLocation = new Point();
        private Dimension preferredSize = new Dimension(ComponentConstants.STANDARD_WIDTH, 4);

        @Getter
        private final Rectangle bounds = new Rectangle();

        @Override
        public Dimension render(Graphics2D graphics) {
            final int separatorX = preferredLocation.x;
            final int separatorY = preferredLocation.y + 4;
            final int width = preferredSize.width;
            final int height = Math.max(preferredSize.height, 2);

            // Draw bar
            if (color != null && color.getAlpha() != 0) {
                graphics.setColor(color);
                graphics.fillRect(separatorX, separatorY, width, height);
            }

            final Dimension dimension = new Dimension(width, height + 4);
            bounds.setLocation(preferredLocation);
            bounds.setSize(dimension);

            return dimension;
        }
    }
}
