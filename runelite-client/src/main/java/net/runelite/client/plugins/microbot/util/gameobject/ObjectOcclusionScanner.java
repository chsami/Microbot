package net.runelite.client.plugins.microbot.util.gameobject;

import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Perspective;
import net.runelite.api.Scene;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.WorldView;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.misc.VisibleClickPoint;

import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.geom.Area;
import java.awt.geom.Point2D;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * The client-thread half of {@link Rs2ObjectOcclusion}: reads the scene and the clickboxes. Every method here
 * runs inside the lambda {@link Rs2ObjectOcclusion} hands to the client thread and must not be called from
 * anywhere else.
 */
final class ObjectOcclusionScanner {

	private ObjectOcclusionScanner() {
	}

	static Rs2ObjectOcclusion.Scan scanOnClientThread(TileObject object) {
		Client client = Microbot.getClient();
		WorldView worldView = object.getWorldView();
		Shape clickbox = object.getClickbox();
		LocalPoint local = object.getLocalLocation();
		WorldPoint world = object.getWorldLocation();
		// boats and other worldviews have their own camera space, so their clicks stay as they were
		if (worldView == null || local == null || world == null || client.getTopLevelWorldView() == null
				|| worldView.getId() != client.getTopLevelWorldView().getId()) {
			return Rs2ObjectOcclusion.unknown();
		}
		if (clickbox == null) {
			return Rs2ObjectOcclusion.offScreen();
		}

		Rectangle bounds = clickbox.getBounds().intersection(new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(),
				client.getViewportWidth(), client.getViewportHeight()));
		List<Shape> occluders = new ArrayList<>();
		List<Point2D.Double> offsets = new ArrayList<>();
		double centreX = local.getX() / (double) Perspective.LOCAL_TILE_SIZE;
		double centreY = local.getY() / (double) Perspective.LOCAL_TILE_SIZE;
		if (bounds.isEmpty()) {
			return Rs2ObjectOcclusion.offScreen();
		}
		collectNearerClickboxes(client, worldView, object, local, bounds, occluders, offsets);
		if (occluders.isEmpty()) {
			return Rs2ObjectOcclusion.unknown();
		}
		Area onScreen = new Area(clickbox);
		onScreen.intersect(new Area(bounds));
		// local coordinates are scene tiles times the tile size, so this puts the centre in world tiles
		Point2D.Double targetCentre = new Point2D.Double(world.getX() - local.getSceneX() + centreX, world.getY() - local.getSceneY() + centreY);
		return new Rs2ObjectOcclusion.Scan(onScreen, occluders, offsets, targetCentre, world.getPlane(), false);
	}

	/** Clickboxes of the named, actionable game objects near {@code target} that stand in front of it and overlap {@code bounds}. */
	private static void collectNearerClickboxes(Client client, WorldView worldView, TileObject target, LocalPoint targetLocal,
												Rectangle bounds, List<Shape> occluders, List<Point2D.Double> offsets) {
		Scene scene = worldView.getScene();
		Tile[][] plane = scene == null ? null : scene.getTiles()[target.getPlane()];
		if (plane == null) {
			return;
		}
		Rectangle2D targetFootprint = footprint(target, targetLocal);
		int targetX = targetLocal.getSceneX();
		int targetY = targetLocal.getSceneY();
		Set<GameObject> seen = Collections.newSetFromMap(new IdentityHashMap<>());
		for (int x = Math.max(0, targetX - Rs2ObjectOcclusion.SEARCH_RADIUS_TILES); x <= Math.min(plane.length - 1, targetX + Rs2ObjectOcclusion.SEARCH_RADIUS_TILES); x++) {
			for (int y = Math.max(0, targetY - Rs2ObjectOcclusion.SEARCH_RADIUS_TILES); y <= Math.min(plane[x].length - 1, targetY + Rs2ObjectOcclusion.SEARCH_RADIUS_TILES); y++) {
				Tile tile = plane[x][y];
				if (tile == null) {
					continue;
				}
				for (GameObject other : tile.getGameObjects()) {
					// cheap checks first; the clickbox is only built for what survives them
					if (other == null || other == target || !seen.add(other)) {
						continue;
					}
					LocalPoint otherLocal = other.getLocalLocation();
					if (otherLocal == null
							|| !VisibleClickPoint.isInFront(footprint(other, otherLocal), targetFootprint, client.getCameraX(), client.getCameraY())
							|| !offersAction(client, other)) {
						continue;
					}
					Shape otherBox = other.getClickbox();
					if (otherBox == null || !otherBox.getBounds().intersects(bounds)) {
						continue;
					}
					occluders.add(otherBox);
					offsets.add(new Point2D.Double((otherLocal.getX() - targetLocal.getX()) / (double) Perspective.LOCAL_TILE_SIZE,
							(otherLocal.getY() - targetLocal.getY()) / (double) Perspective.LOCAL_TILE_SIZE));
				}
			}
		}
	}

	/** The tiles an object stands on, in local units. */
	private static Rectangle2D footprint(TileObject object, LocalPoint local) {
		int tile = Perspective.LOCAL_TILE_SIZE;
		if (object instanceof GameObject) {
			GameObject gameObject = (GameObject) object;
			var min = gameObject.getSceneMinLocation();
			return new Rectangle2D.Double(min.getX() * tile, min.getY() * tile, gameObject.sizeX() * tile, gameObject.sizeY() * tile);
		}
		return new Rectangle2D.Double(local.getSceneX() * tile, local.getSceneY() * tile, tile, tile);
	}

	/** Scenery with no action other than Examine offers nothing a click could take away from the target. */
	private static boolean offersAction(Client client, GameObject object) {
		ObjectComposition composition = client.getObjectDefinition(object.getId());
		if (composition != null && composition.getImpostorIds() != null) {
			composition = composition.getImpostor();
		}
		String[] actions = composition == null ? null : composition.getActions();
		if (actions == null) {
			return false;
		}
		for (String action : actions) {
			if (action != null && !action.equalsIgnoreCase("Examine")) {
				return true;
			}
		}
		return false;
	}
}
