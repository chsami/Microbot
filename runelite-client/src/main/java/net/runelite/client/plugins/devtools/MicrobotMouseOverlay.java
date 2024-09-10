package net.runelite.client.plugins.devtools;

import net.runelite.api.Client;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.Path2D;

public class MicrobotMouseOverlay extends Overlay {
    private final Client client;
    private final DevToolsPlugin plugin;

    @Inject
    MicrobotMouseOverlay(Client client, DevToolsPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(Overlay.PRIORITY_LOW);
    }

    @Override
    public Dimension render(Graphics2D g) {
        if (plugin.getMouseMovement().isActive()) {
            if (!Microbot.getMouse().getTimer().isRunning()) {
                Microbot.getMouse().getPoints().clear();
                Microbot.getMouse().getTimer().start();
            }
            //g.setFont(new Font("Tahoma", Font.BOLD, 18));
            g.setFont(g.getFont().deriveFont(40.0f));
            // Get the FontMetrics for the current font
            FontMetrics metrics = g.getFontMetrics(g.getFont());

// Get the width and height of the character
            int charWidth = metrics.stringWidth("⊹");
            int charHeight = metrics.getAscent(); // ascent gives the height of the character above the baseline

// Calculate the new position
            int x = Microbot.getMouse().getLastMove().getX() - (charWidth / 2);
            int y = Microbot.getMouse().getLastMove().getY() + (charHeight / 2);

            OverlayUtil.renderTextLocation(g, new net.runelite.api.Point(x, y), "⊹", Microbot.getMouse().getRainbowColor());

            g.setStroke(new BasicStroke(3));
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            var points = Microbot.getMouse().getPoints();
            if (points.size() > 1) {
                Path2D path = new Path2D.Double();
                net.runelite.api.Point firstPoint = points.getFirst();
                path.moveTo(firstPoint.getX(), firstPoint.getY());

                for (int i = 1; i < points.size(); i++) {
                    net.runelite.api.Point p = points.get(i);
                    path.lineTo(p.getX(), p.getY());
                    g.setColor(Microbot.getMouse().getRainbowColor());
                }

                g.draw(path);
            }
            // draw trail of mouse movements

        } else {
            Microbot.getMouse().getPoints().clear();
            Microbot.getMouse().getTimer().stop();
        }

        return null;
    }
}

