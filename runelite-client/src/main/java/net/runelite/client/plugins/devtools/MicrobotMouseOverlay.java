package net.runelite.client.plugins.devtools;

import java.util.ArrayList;
import java.util.LinkedList;
import net.runelite.api.Client;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

import java.util.List;
import javax.inject.Inject;
import java.awt.*;
import java.awt.geom.AffineTransform;
import java.awt.geom.Path2D;
import java.awt.image.BufferedImage;

public class MicrobotMouseOverlay extends Overlay {
    private final Client client;
    private final DevToolsPlugin plugin;
    private float angle = 0.0f; // Rotation angle

    @Inject
    MicrobotMouseOverlay(Client client, DevToolsPlugin plugin) {
        this.client = client;
        this.plugin = plugin;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(Overlay.PRIORITY_LOW);
        setNaughty();
        // Increase the angle
        new Thread(() -> {
            try {
                while (true) {
                    angle += 0.004f; // Increment angle
                    if (angle >= 2 * Math.PI) {
                        angle -= (float) (2 * Math.PI);
                    }

                    Thread.sleep(10); // Control frame rate
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }




    @Override
    public Dimension render(Graphics2D g) {
        if (plugin.getMouseMovement().isActive()) {
            if (!Microbot.getMouse().getTimer().isRunning()) {
                Microbot.getMouse().getPoints().clear();
                Microbot.getMouse().getTimer().start();
            }
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            // Enable anti-aliasing for smooth rendering
            int CROSSHAIR_SIZE = 30; // Cursor image size
            int CORNER_SIZE = 10;

            BufferedImage cursorImage = new BufferedImage(CROSSHAIR_SIZE, CROSSHAIR_SIZE, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g2d = cursorImage.createGraphics();


            // Enable anti-aliasing for smooth rendering
            g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            // Rotate the graphics
            g2d.setColor(Microbot.getMouse().getRainbowColor());
            g2d.setStroke(new BasicStroke(2f));

            // Calculate the far edge (we subtract 1 because drawLine is inclusive)
            int max = CROSSHAIR_SIZE - 1;

            // ========= TOP-LEFT CORNER (inverted) =========
            //
            // The corner's "joint" is at (CORNER_SIZE, CORNER_SIZE).
            // Draw lines outward toward the top edge and left edge:
            //
            // Vertical line: from (CORNER_SIZE, 0) down to the joint
            // Horizontal line: from (0, CORNER_SIZE) right to the joint
            //
            g2d.drawLine(CORNER_SIZE, 0, CORNER_SIZE, CORNER_SIZE);
            g2d.drawLine(0, CORNER_SIZE, CORNER_SIZE, CORNER_SIZE);

            // ========= TOP-RIGHT CORNER (inverted) =========
            //
            // The corner's "joint" is at (max - CORNER_SIZE, CORNER_SIZE).
            // Draw lines outward toward the top edge and right edge:
            //
            // Vertical line: from (max - CORNER_SIZE, 0) down to the joint
            // Horizontal line: from (max, CORNER_SIZE) left to the joint
            //
            g2d.drawLine(max - CORNER_SIZE, 0, max - CORNER_SIZE, CORNER_SIZE);
            g2d.drawLine(max, CORNER_SIZE, max - CORNER_SIZE, CORNER_SIZE);

            // ========= BOTTOM-LEFT CORNER (inverted) =========
            //
            // The corner's "joint" is at (CORNER_SIZE, max - CORNER_SIZE).
            // Draw lines outward toward the bottom edge and left edge:
            //
            // Vertical line: from (CORNER_SIZE, max) up to the joint
            // Horizontal line: from (0, max - CORNER_SIZE) right to the joint
            //
            g2d.drawLine(CORNER_SIZE, max, CORNER_SIZE, max - CORNER_SIZE);
            g2d.drawLine(0, max - CORNER_SIZE, CORNER_SIZE, max - CORNER_SIZE);

            // ========= BOTTOM-RIGHT CORNER (inverted) =========
            //
            // The corner's "joint" is at (max - CORNER_SIZE, max - CORNER_SIZE).
            // Draw lines outward toward the bottom edge and right edge:
            //
            // Vertical line: from (max - CORNER_SIZE, max) up to the joint
            // Horizontal line: from (max, max - CORNER_SIZE) left to the joint
            //
            g2d.drawLine(max - CORNER_SIZE, max, max - CORNER_SIZE, max - CORNER_SIZE);
            g2d.drawLine(max, max - CORNER_SIZE, max - CORNER_SIZE, max - CORNER_SIZE);

            // Draw 4x4 dot in the center
            g2d.fillRect(CROSSHAIR_SIZE / 2 - 2, CROSSHAIR_SIZE / 2 - 2, 4, 4);


            g2d.dispose();
            // Mouse position
            int x = Microbot.getMouse().getLastMove().getX();
            int y = Microbot.getMouse().getLastMove().getY();


            // Draw the crosshair centered
            float drawX = x - CROSSHAIR_SIZE / 2.0f;
            float drawY = y - CROSSHAIR_SIZE / 2.0f;

            // Save the original graphics transform
            AffineTransform original = g.getTransform();
            // Rotate the cursor image
            g.rotate(angle, drawX + CROSSHAIR_SIZE / 2.0, drawY + CROSSHAIR_SIZE / 2.0);

            g.drawImage(cursorImage, (int) drawX, (int) drawY, null);
            //OverlayUtil.renderTextLocation(g, new net.runelite.api.Point(drawX, drawY), "✛", Microbot.getMouse().getRainbowColor());

            // Restore the original graphics transform
            g.setTransform(original);


            g.setStroke(new BasicStroke(3));
            //g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            var points = Microbot.getMouse().getPoints();
			// create a snapshot of the points to avoid concurrent modification
			var pointArray = points.toArray(new Point[0]);
			if (pointArray.length > 1)
			{
				Point firstPoint = pointArray[0];
				Point lastPoint = pointArray[pointArray.length - 1];

				if (firstPoint != null && lastPoint != null)
				{
					// Move to the first point
					Path2D path = new Path2D.Double();
					path.moveTo(firstPoint.getX(), firstPoint.getY());

					// For each intermediate pair of points, use a midpoint-based quadTo
					for (int i = 1; i < pointArray.length - 2; i++)
					{
						Point pCurrent = pointArray[i];
						Point pNext = pointArray[i + 1];

						// Calculate midpoints for a smoother curve
						double midX = (pCurrent.getX() + pNext.getX()) / 2.0;
						double midY = (pCurrent.getY() + pNext.getY()) / 2.0;

						// Draw a quadratic curve from pCurrent toward midX/midY
						path.quadTo(pCurrent.getX(), pCurrent.getY(), midX, midY);
					}

					// Finally, connect the last two points with a final quadTo
					Point secondLast = pointArray[pointArray.length - 2];
					path.quadTo(secondLast.getX(), secondLast.getY(), lastPoint.getX(), lastPoint.getY());

					// Optionally set a thicker stroke with round caps/joins for a "brush" feel
					g.setColor(Microbot.getMouse().getRainbowColor());
					g.setStroke(new BasicStroke(
						3.0f,
						BasicStroke.CAP_ROUND,
						BasicStroke.JOIN_ROUND
					));

					// Draw the smooth path
					g.draw(path);
				}
			}

        } else {
            Microbot.getMouse().getPoints().clear();
            Microbot.getMouse().getTimer().stop();
        }

        return null;
    }
}