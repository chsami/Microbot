package net.runelite.client.plugins.microbot.util.walker.door;

import java.util.List;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectQueryable;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;

/** Whether a door exists on a wall edge, judged from the scene objects alone. */
public final class DoorEdgeGeometry
{
	private static final List<String> DOOR_ACTIONS = List.of(
		"pay-toll", "pick-lock", "walk-through", "go-through", "open", "pass", "close");

	private DoorEdgeGeometry()
	{
	}

	/**
	 * Whether a door-like wall or game object sits on the edge, open or shut. Geometry only: unlike the door probe it
	 * ignores the attempt ledger (a door another player just closed), the session blacklist, the action filter and the
	 * distance limit, so "no door here" can be trusted before an edge is given up on. When the scene cannot be read, a
	 * door is assumed.
	 */
	public static boolean doorOnEdge(WorldPoint from, WorldPoint to)
	{
		if (from == null || to == null || from.getPlane() != to.getPlane())
		{
			return false;
		}
		return Microbot.getClientThread().runOnClientThreadOptional(() ->
			new Rs2TileObjectQueryable()
				.where(model -> isNear(model, from))
				.toList().stream()
				.map(Rs2TileObjectModel::getTileObject)
				.filter(object -> object instanceof WallObject || object instanceof GameObject)
				.filter(object -> Rs2DoorGeometry.isDoorOnSegment(object, object.getWorldLocation(), from, to))
				.anyMatch(object ->
				{
					ObjectComposition composition = Rs2DoorDetection.resolveCompositionForDoorProbe(object);
					return composition != null && (Rs2DoorClassifier.isDoorLikeGameObjectName(composition.getName())
						|| Rs2DoorClassifier.getDoorAction(composition, DOOR_ACTIONS) != null);
				})).orElse(Boolean.TRUE);
	}

	private static boolean isNear(Rs2TileObjectModel model, WorldPoint from)
	{
		WorldPoint at = model.getWorldLocation();
		return at != null && at.getPlane() == from.getPlane() && at.distanceTo2D(from) <= 3;
	}
}
