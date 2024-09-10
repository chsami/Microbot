package net.runelite.client.plugins.microbot.util.mouse;

import lombok.Getter;
import net.runelite.api.Point;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;

import javax.swing.*;
import java.awt.*;
import java.util.LinkedList;

@Getter
public abstract class Mouse {
    private static final int POINT_LIFETIME = 12;// Maximum number of points to store
    final int MAX_POINTS = 500;
    LinkedList<Point> points = new LinkedList<>();
    Point lastClick = new Point(-1, -1); // getter for last click
    // getter for click before last click
    Point lastClick2 = new Point(-1, -1);
    Point lastMove = new Point(-1, -1); // getter for last move
    float hue = 0.0f; // Initial hue value
    Timer timer = new Timer(POINT_LIFETIME, e -> {
        if (!points.isEmpty()) {
            try {
                points.removeFirst();
            } catch (Exception ignore) {
            }

        }
    });

    public Mouse() {
    }

    public Color getRainbowColor() {
        hue += 0.001f; // Increment hue to cycle through colors
        if (hue > 1.0f) {
            hue = 0.0f; // Reset hue when it exceeds 1.0
        }
        return Color.getHSBColor(hue, 1.0f, 1.0f);
    }

    public Canvas getCanvas() {
        return Microbot.getClient().getCanvas();
    }

    public int randomizeClick() {
        return (int) Rs2Random.normalRange(-10, 10, 4);
    }


    public abstract void setLastClick(Point point);

    public abstract void setLastMove(Point point);


    public abstract Mouse click(int x, int y);

    public abstract Mouse click(double x, double y);

    public abstract Mouse click(Rectangle rectangle);

    public abstract Mouse click(int x, int y, boolean rightClick);

    public abstract Mouse click(Point point);

    public abstract Mouse click(Point point, boolean rightClick);

    public abstract Mouse click(Point point, NewMenuEntry entry);

    public abstract Mouse click();

    public abstract Mouse move(Point point);

    public abstract Mouse move(Rectangle rect);

    public abstract Mouse move(int x, int y);

    public abstract Mouse move(double x, double y);

    public abstract Mouse move(Polygon polygon);

    public abstract Mouse scrollDown(Point point);

    public abstract Mouse scrollUp(Point point);

    public abstract Mouse drag(Point startPoint, Point endPoint);

    public abstract java.awt.Point getMousePosition();

}
