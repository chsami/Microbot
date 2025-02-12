package net.runelite.client.plugins.microbot.util.overlay;

import net.runelite.api.NPC;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;

import java.awt.*;

public class OverlayHelper {
    public static void drawTile(Graphics2D graphics, LocalPoint localPoint, Font font, String text, Color color) {
        if (localPoint == null) return;

        Polygon poly = Perspective.getCanvasTilePoly(Microbot.getClient(), localPoint);

        if (poly != null) {
            graphics.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 50));
            graphics.fill(poly);
            graphics.setColor(color);
            graphics.draw(poly);
        }

        Point canvasPoint = Perspective.getCanvasTextLocation(Microbot.getClient(), graphics, localPoint, text, 0);
        if (canvasPoint != null) {
            graphics.setFont(new Font("Arial", Font.BOLD, 12));
            graphics.setColor(Color.BLACK);
            graphics.drawString(text, canvasPoint.getX() + 1, canvasPoint.getY() + 1);
            graphics.setColor(color);
            graphics.drawString(text, canvasPoint.getX(), canvasPoint.getY());
        }
    }

    public static void drawTile(Graphics2D graphics, LocalPoint localPoint, String text, Color color) {
        Font font = new Font("Arial", Font.BOLD, 12);
        drawTile(graphics, localPoint, font, text, color);
    }
    
    public static void drawNPC(Graphics2D graphics, NPC npc, Color color) {
        if (npc == null) return;
        if (npc.isDead()) return;

        LocalPoint localPoint = npc.getLocalLocation();
        if (localPoint != null) {
            Shape shape = npc.getConvexHull();
            if (shape != null) {
                graphics.setColor(color);
                graphics.draw(shape);
            }
        }
    }
}
