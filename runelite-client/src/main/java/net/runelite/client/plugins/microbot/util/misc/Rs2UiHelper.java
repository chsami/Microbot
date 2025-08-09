package net.runelite.client.plugins.microbot.util.misc;

import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;

import java.awt.*;

public class Rs2UiHelper {

	public static final Pattern COL_TAG_PATTERN = Pattern.compile("<col=[^>]+>|</col>");
	// Regex to extract base name and numeric suffix, e.g., "Super attack (4)" -> "Super attack", 4
	public static final Pattern ITEM_NAME_SUFFIX_PATTERN = Pattern.compile("^(.*?)(?:\\s*\\((\\d+)\\))?$");

    public static boolean isRectangleWithinViewport(Rectangle rectangle) {
        int viewportHeight = Microbot.getClient().getViewportHeight();
        int viewportWidth = Microbot.getClient().getViewportWidth();

        return !(rectangle.getX() > (double) viewportWidth) &&
                !(rectangle.getY() > (double) viewportHeight) &&
                !(rectangle.getX() < 0.0) &&
                !(rectangle.getY() < 0.0);
    }

    public static boolean isRectangleWithinCanvas(Rectangle rectangle) {
        int canvasHeight = Microbot.getClient().getCanvasHeight();
        int canvasWidth = Microbot.getClient().getCanvasWidth();

        return rectangle.getX() + rectangle.getWidth() <= (double) canvasWidth &&
                rectangle.getY() + rectangle.getHeight() <= (double) canvasHeight;
    }

    public static boolean isRectangleWithinRectangle(Rectangle main, Rectangle sub) {
        return main.contains(sub);
    }

    public static Point getClickingPoint(Rectangle rectangle, boolean randomize) {
        if (rectangle == null) return new Point(1, 1);
        if (rectangle.getX() == 1 && rectangle.getY() == 1) return new Point(1, 1);
        if (rectangle.getX() == 0 && rectangle.getY() == 0) return new Point(1, 1);

        if (!randomize) return new Point((int) rectangle.getCenterX(), (int) rectangle.getCenterY());

        //check if mouse is already within the rectangle and return current position
        if (Rs2AntibanSettings.naturalMouse) {
            java.awt.Point mousePos = Microbot.getMouse().getMousePosition();
            if (isMouseWithinRectangle(rectangle)) return new Point(mousePos.x, mousePos.y);
            else return Rs2Random.randomPointEx(new Point(mousePos.x, mousePos.y), rectangle, 0.78);
        } else
            return Rs2Random.randomPointEx(Microbot.getMouse().getLastClick(), rectangle, 0.78);
    }

    //check if mouse is already within the rectangle
    public static boolean isMouseWithinRectangle(Rectangle rectangle) {
        java.awt.Point mousePos = Microbot.getMouse().getMousePosition();
        if (rectangle.getX() == 1 && rectangle.getY() == 1) return true;
        if (rectangle.getX() == 0 && rectangle.getY() == 0) return true;
        return rectangle.contains(mousePos);
    }

    public static Rectangle getActorClickbox(Actor actor) {
        LocalPoint lp = actor.getLocalLocation();
        if (lp == null) {
            Microbot.log("LocalPoint is null");
            return getDefaultRectangle();
        }


        Shape clickbox = Microbot.getClientThread().runOnClientThreadOptional(() -> Perspective.getClickbox(Microbot.getClient(), actor.getModel(), actor.getCurrentOrientation(), lp.getX(), lp.getY(),
                Perspective.getTileHeight(Microbot.getClient(), lp, actor.getWorldLocation().getPlane())))
                .orElse(null);

        if (clickbox == null) return getDefaultRectangle();  //return a small rectangle if clickbox is null
        

        return new Rectangle(clickbox.getBounds());
    }

    public static Rectangle getObjectClickbox(TileObject object) {

        if (object == null) return getDefaultRectangle();  //return a small rectangle if object is null
        Shape clickbox = Microbot.getClientThread().runOnClientThreadOptional(object::getClickbox).orElse(null);
        if (clickbox == null) return getDefaultRectangle();  //return a small rectangle if clickbox is null
        if (clickbox.getBounds() == null) return getDefaultRectangle();


        return new Rectangle(clickbox.getBounds());
    }
    
    public static Rectangle getTileClickbox(Tile tile) {
        if (tile == null) return getDefaultRectangle();

        LocalPoint localPoint = tile.getLocalLocation();
        if (localPoint == null) return getDefaultRectangle();

        // Get the screen point of the tile center
        Point screenPoint = Perspective.localToCanvas(Microbot.getClient(), localPoint, Microbot.getClient().getPlane());

        if (screenPoint == null) return getDefaultRectangle();
        
        int tileSize = Perspective.LOCAL_TILE_SIZE;
        int halfSize = tileSize / 4;

        return new Rectangle(screenPoint.getX() - halfSize, screenPoint.getY() - halfSize, tileSize / 2, tileSize / 2);
    }

    // check if a menu entry is a actor
    public static boolean hasActor(NewMenuEntry entry) {
        return entry.getActor() != null;
    }

    // check if a menu entry is a game object
    public static boolean isGameObject(NewMenuEntry entry) {
        return entry.getGameObject() != null;
    }

    /**
     * Strips color tags from the provided text.
     *
     * @param text the text from which to strip color tags.
     * @return the text without color tags.
     */
    public static String stripColTags(String text) {
        return text != null ? COL_TAG_PATTERN.matcher(text).replaceAll("") : "";
    }

	public static Rectangle getDefaultRectangle() {
		int randomValue = ThreadLocalRandom.current().nextInt(3) - 1;
		return new Rectangle(randomValue, randomValue, Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight());
	}

	/**
	 * Extracts the first integer number from a given string.
	 * For example: "+1%", "123abc", "Price: 99 dollars" will result in 1, 123, 99
	 *
	 * @param input The input string containing a number.
	 * @return The extracted number, or -1 if no number is found.
	 */
	public static int extractNumber(String input) {
		// Define a regex pattern to match one or more digits
		Pattern pattern = Pattern.compile("\\d+");
		Matcher matcher = pattern.matcher(input);

		// Find the first match and parse it as an integer
		if (matcher.find()) {
			return Integer.parseInt(matcher.group());
		}

		// Return -1 if no number is found
		return -1;
	}
}
