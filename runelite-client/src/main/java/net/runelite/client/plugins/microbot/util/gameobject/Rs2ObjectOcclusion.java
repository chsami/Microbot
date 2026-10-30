package net.runelite.client.plugins.microbot.util.gameobject;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.misc.VisibleClickPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;

import java.awt.Point;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Finds where an object can really be clicked. A real player cannot click through a tree that stands in
 * front of the target, but a menu entry injected by the bot goes through anyway, so the click point has
 * to be chosen where the target is visible and on top.
 *
 * <p>Occlusion rule: a {@link GameObject} within {@link #SEARCH_RADIUS_TILES} tiles that offers an action,
 * stands beside (not on) the target's footprint, has its nearest edge closer to the camera and whose clickbox
 * overlaps the target's is drawn in front of it, so its clickbox is cut out of the target's. If nothing is
 * left, the camera is turned; if the object stays covered the click is refused and a caller that may move the
 * player can ask {@link #findClearViewTile} where to stand.
 */
@Slf4j
public final class Rs2ObjectOcclusion {

	static final int SEARCH_RADIUS_TILES = 4;
	/** Camera turns tried in order, relative to the yaw at the start (2048 units = 360 degrees). */
	private static final int[] YAW_STEPS = {300, -300, 700};
	private static final int FULL_TURN = 2048;
	private static final int YAW_SETTLED_UNITS = 16;
	private static final int YAW_SETTLE_TIMEOUT_MS = 2000;
	private static final int CLEAR_VIEW_SEARCH_TILES = 5;
	private static final int CLEAR_VIEW_MAX_TARGET_DISTANCE = 7;
	private static final long COVERED_REPORT_MAX_AGE_MS = 15_000;
	private static final long REFUSAL_COOLDOWN_MS = 10_000;

	/** The last object a click refused because it stayed covered. */
	private static volatile CoverReport lastCovered;
	/** When each object (by hash) was last refused, so a repeat click does not spin the camera again. */
	private static final Map<Long, Long> refusals = new ConcurrentHashMap<>();

	private Rs2ObjectOcclusion() {
	}

	/** What a client-thread look at the screen found. */
	static final class Scan {
		final Area onScreen;
		final List<Shape> occluders;
		/** Centres of the occluders relative to the target centre, in tiles. */
		final List<Point2D.Double> offsets;
		/** The target centre in world tiles. */
		final Point2D.Double targetCentre;
		final int plane;
		/** The object has no clickbox on the viewport, so nothing can be said about what covers it. */
		final boolean offScreen;

		Scan(Area onScreen, List<Shape> occluders, List<Point2D.Double> offsets, Point2D.Double targetCentre, int plane, boolean offScreen) {
			this.onScreen = onScreen;
			this.occluders = occluders;
			this.offsets = offsets;
			this.targetCentre = targetCentre;
			this.plane = plane;
			this.offScreen = offScreen;
		}
	}

	private static final class CoverReport {
		final TileObject object;
		final Set<Point2D.Double> offsets;
		final Point2D.Double targetCentre;
		final int plane;
		final long at = System.currentTimeMillis();

		CoverReport(TileObject object, Set<Point2D.Double> offsets, Point2D.Double targetCentre, int plane) {
			this.object = object;
			this.offsets = offsets;
			this.targetCentre = targetCentre;
			this.plane = plane;
		}
	}

	/**
	 * The rectangle to click for {@code object}: its normal clickbox bounds when nothing covers it, a small
	 * rectangle on its visible part when something does, or null when it is off screen or stays covered after
	 * turning the camera (which is then put back where it was). Call from a script thread.
	 */
	public static Rectangle findClickArea(TileObject object) {
		if (recentlyRefused(object)) {
			log.warn("Object #{} was refused a moment ago as off screen or covered, not turning the camera again", object.getHash());
			return null;
		}
		Scan scan = scan(object);
		Rectangle area = clickAreaOf(object, scan);
		if (area != null) {
			return accepted(object, area);
		}
		log.info("Object #{} is {}, turning the camera", object.getHash(),
				scan.offScreen ? "off screen" : "covered by " + scan.occluders.size() + " nearer object(s)");
		Set<Point2D.Double> offsets = new LinkedHashSet<>(scan.offsets);
		Point2D.Double targetCentre = scan.targetCentre;
		int plane = scan.plane;
		int startYaw = Rs2Camera.getYaw();
		if (scan.offScreen) {
			Rs2Camera.turnTo(object);
			scan = scan(object);
			area = clickAreaOf(object, scan);
			if (area != null) {
				return accepted(object, area);
			}
			offsets.addAll(scan.offsets);
		}
		for (int step : YAW_STEPS) {
			turnCameraTo(Math.floorMod(startYaw + step, FULL_TURN));
			scan = scan(object);
			area = clickAreaOf(object, scan);
			if (area != null) {
				log.info("Object #{} is clear after turning the camera {} units", object.getHash(), step);
				return accepted(object, area);
			}
			offsets.addAll(scan.offsets);
			if (!scan.offScreen && !scan.occluders.isEmpty()) {
				targetCentre = scan.targetCentre;
				plane = scan.plane;
			}
		}
		log.warn("Object #{} is still {} after turning the camera", object.getHash(), offsets.isEmpty() ? "off screen" : "covered");
		turnCameraTo(startYaw);
		refusals.put(object.getHash(), System.currentTimeMillis());
		lastCovered = offsets.isEmpty() ? null : new CoverReport(object, offsets, targetCentre, plane);
		return null;
	}

	/** Whether a click on {@code object} was refused as off screen or covered within the last few seconds. */
	public static boolean recentlyRefused(TileObject object) {
		Long at = refusals.get(object.getHash());
		return at != null && System.currentTimeMillis() - at < REFUSAL_COOLDOWN_MS;
	}

	private static Rectangle accepted(TileObject object, Rectangle area) {
		refusals.remove(object.getHash());
		CoverReport report = lastCovered;
		if (report != null && report.object == object) {
			lastCovered = null;
		}
		return area;
	}

	/** The rectangle to click for {@code object} right now, without turning the camera; null when it is covered. */
	public static Rectangle visibleClickArea(TileObject object) {
		return clickAreaOf(object, scan(object));
	}

	/** Whether a click at {@code point} lands on a visible part of {@code object}. */
	public static boolean visibleAt(TileObject object, java.awt.Point point) {
		Scan scan = scan(object);
		if (scan.occluders.isEmpty()) {
			Rectangle plain = Rs2UiHelper.getObjectClickbox(object);
			Rectangle visible = Rs2UiHelper.isFallbackRectangle(plain) ? null : Rs2UiHelper.clipToCanvas(plain);
			return visible == null || visible.contains(point);
		}
		return VisibleClickPoint.visibleArea(scan.onScreen, scan.occluders).contains(point);
	}

	/**
	 * The nearest reachable tile, within a few tiles of the player, that sees {@code object} from at least a
	 * right angle away from everything that covered it, or null when {@code object} was not recently refused as
	 * covered or no such tile exists. Walking there is up to the caller.
	 */
	public static WorldPoint findClearViewTile(TileObject object) {
		CoverReport report = lastCovered;
		WorldPoint player = Rs2Player.getWorldLocation();
		if (report == null || report.object != object || player == null
				|| System.currentTimeMillis() - report.at > COVERED_REPORT_MAX_AGE_MS) {
			return null;
		}
		WorldPoint best = null;
		int bestDistance = Integer.MAX_VALUE;
		for (Map.Entry<WorldPoint, Integer> reachable : Rs2Tile.getReachableTilesFromTile(player, CLEAR_VIEW_SEARCH_TILES).entrySet()) {
			WorldPoint tile = reachable.getKey();
			Point2D.Double tileCentre = new Point2D.Double(tile.getX() + 0.5, tile.getY() + 0.5);
			double away = Math.max(Math.abs(tileCentre.x - report.targetCentre.x), Math.abs(tileCentre.y - report.targetCentre.y));
			if (tile.getPlane() == report.plane && away >= 1 && away <= CLEAR_VIEW_MAX_TARGET_DISTANCE
					&& reachable.getValue() > 0 && reachable.getValue() < bestDistance
					&& VisibleClickPoint.looksPastAll(tileCentre, report.targetCentre, report.offsets)) {
				best = tile;
				bestDistance = reachable.getValue();
			}
		}
		if (best != null) {
			// the player is about to move, so the next click should look again
			refusals.remove(object.getHash());
		}
		return best;
	}

	/** The click rectangle for what {@code scan} saw: the plain clickbox, a rectangle on the visible part, or null if covered. */
	private static Rectangle clickAreaOf(TileObject object, Scan scan) {
		if (scan.offScreen) {
			return null;
		}
		if (scan.occluders.isEmpty()) {
			return Rs2UiHelper.getObjectClickbox(object);
		}
		Optional<Point> point = VisibleClickPoint.pick(scan.onScreen, scan.occluders, ThreadLocalRandom.current());
		return point.map(p -> new Rectangle(p.x - VisibleClickPoint.MARGIN, p.y - VisibleClickPoint.MARGIN,
				2 * VisibleClickPoint.MARGIN + 1, 2 * VisibleClickPoint.MARGIN + 1)).orElse(null);
	}

	private static Scan scan(TileObject object) {
		Scan scan = Microbot.getClientThread().runOnClientThreadOptional(() -> ObjectOcclusionScanner.scanOnClientThread(object)).orElse(null);
		return scan != null ? scan : unknown();
	}

	/** Nothing is known to cover the object, or it is somewhere (a boat) this does not judge. */
	static Scan unknown() {
		return new Scan(new Area(), Collections.emptyList(), Collections.emptyList(), new Point2D.Double(), 0, false);
	}

	static Scan offScreen() {
		return new Scan(new Area(), Collections.emptyList(), Collections.emptyList(), new Point2D.Double(), 0, true);
	}

	private static void turnCameraTo(int yaw) {
		Rs2Camera.setYaw(yaw);
		boolean settled = sleepUntil(() -> {
			int gap = Math.abs(Rs2Camera.getYaw() - yaw);
			return Math.min(gap, FULL_TURN - gap) <= YAW_SETTLED_UNITS;
		}, YAW_SETTLE_TIMEOUT_MS);
		if (!settled) {
			log.warn("Camera did not settle on yaw {} within {} ms", yaw, YAW_SETTLE_TIMEOUT_MS);
		}
	}
}
