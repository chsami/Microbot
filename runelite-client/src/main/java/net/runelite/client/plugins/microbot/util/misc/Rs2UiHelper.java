package net.runelite.client.plugins.microbot.util.misc;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Point;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.RuneLiteProperties;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer;

import java.awt.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
public class Rs2UiHelper {

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

    private static final double CLICK_FORCE_MIN = 0.55;
    private static final double CLICK_FORCE_MAX = 0.95;
    private static volatile double sessionClickForce = 0.0;

    static double getSessionClickForce() {
        double cached = sessionClickForce;
        if (cached != 0.0) return cached;
        synchronized (Rs2UiHelper.class) {
            if (sessionClickForce != 0.0) return sessionClickForce;
            sessionClickForce = CLICK_FORCE_MIN + java.util.concurrent.ThreadLocalRandom.current().nextDouble()
                    * (CLICK_FORCE_MAX - CLICK_FORCE_MIN);
            return sessionClickForce;
        }
    }

    public static Point getClickingPoint(Rectangle rectangle, boolean randomize) {
        if (rectangle == null) return new Point(1, 1);
        if (rectangle.getX() == 1 && rectangle.getY() == 1) return new Point(1, 1);
        if (rectangle.getX() == 0 && rectangle.getY() == 0) return new Point(1, 1);

        if (!randomize) return new Point((int) rectangle.getCenterX(), (int) rectangle.getCenterY());

        double force = getSessionClickForce();
        if (Rs2AntibanSettings.naturalMouse) {
            java.awt.Point mousePos = Microbot.getMouse().getMousePosition();
            return Rs2Random.randomPointEx(new Point(mousePos.x, mousePos.y), rectangle, force);
        } else
            return Rs2Random.randomPointEx(Microbot.getMouse().getLastClick(), rectangle, force);
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
            log.warn("LocalPoint is null");
            return getDefaultRectangle();
        }


        Shape clickbox = Microbot.getClientThread().runOnClientThreadOptional(() -> Perspective.getClickbox(Microbot.getClient(), Microbot.getClient().getTopLevelWorldView(), actor.getModel(), actor.getCurrentOrientation(), lp.getX(), lp.getY(),
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
     * Strips RuneLite/Jagex markup tags from the provided text.
     *
     * @param text the text from which to strip tags.
     * @return the text without tags.
	 *
	 * @deprecated Use {@link #stripTags(String)} or {@link net.runelite.client.plugins.microbot.util.text.Rs2TextSanitizer#stripTags(String)}.
     */
	@Deprecated
    public static String stripColTags(String text) {
        // Historic API name; in practice callers want RuneLite/Jagex markup removed, not only <col>.
        return Rs2TextSanitizer.stripTags(text);
    }

	/** Strip RuneLite/Jagex markup tags from text. */
	public static String stripTags(String text)
	{
		return Rs2TextSanitizer.stripTags(text);
	}

	/** Strip tags and normalize whitespace to single spaces. */
	public static String stripTagsToSpace(String text)
	{
		return Rs2TextSanitizer.stripTagsToSpace(text);
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

	/**
	 * Check if the current client version is compatible with the required minimum version
	 */
	public static boolean isClientVersionCompatible(String minClientVersion) {
		if (Microbot.isDebug()) {
			return true; // return true if we are developing
		}
		if (minClientVersion == null || minClientVersion.isEmpty()) {
			return true;
		}

		String currentVersion = RuneLiteProperties.getMicrobotVersion();
		if (currentVersion == null) {
			log.warn("Unable to determine current Microbot version");
			return false;
		}

		return compareVersions(currentVersion, minClientVersion) >= 0;
	}

	/**
	 * Compare two version strings using semantic versioning with support for 4-part versions
	 * Supports formats like: 1.9.7, 1.9.7.1, 1.9.8, 1.9.8.1
	 * @param version1 The first version to compare
	 * @param version2 The second version to compare
	 * @return -1 if version1 < version2, 0 if equal, 1 if version1 > version2
	 */
	public static int compareVersions(String version1, String version2) {
		if (version1 == null && version2 == null) return 0;
		if (version1 == null) return -1;
		if (version2 == null) return 1;

		// Split versions by dots and handle up to 4 parts (major.minor.patch.build)
		String[] v1Parts = version1.split("\\.");
		String[] v2Parts = version2.split("\\.");

		int maxLength = Math.max(v1Parts.length, v2Parts.length);

		for (int i = 0; i < maxLength; i++) {
			int v1Part = i < v1Parts.length ? parseVersionPart(v1Parts[i]) : 0;
			int v2Part = i < v2Parts.length ? parseVersionPart(v2Parts[i]) : 0;

			if (v1Part < v2Part) return -1;
			if (v1Part > v2Part) return 1;
		}

		return 0;
	}

	/**
	 * Parse a version part, extracting only the numeric portion
	 */
	public static int parseVersionPart(String part) {
		if (part == null || part.isEmpty()) return 0;

		StringBuilder numericPart = new StringBuilder();
		for (char c : part.toCharArray()) {
			if (!Character.isDigit(c)) break;
			numericPart.append(c);
		}

		try {
			return numericPart.length() > 0 ? Integer.parseInt(numericPart.toString()) : 0;
		} catch (NumberFormatException e) {
			return 0;
		}
	}
}
