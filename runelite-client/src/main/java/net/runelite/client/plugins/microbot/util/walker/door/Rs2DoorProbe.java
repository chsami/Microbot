package net.runelite.client.plugins.microbot.util.walker.door;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportEdge;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportExecutor;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportType;

/**
 * Door-probe logic that operates against a {@link DoorProbeContext} (the scan-scoped caches) and
 * explicit state (the session door blacklist, the recently-opened-door map), rather than
 * {@code Rs2Walker}'s static fields. The walker facade owns the state and passes it in.
 */
public final class Rs2DoorProbe {

    private Rs2DoorProbe() {
    }

    /**
     * Resolves an object's composition, memoised in the scan's composition cache when present so a
     * single raw-scene scan doesn't re-resolve the same object repeatedly. Falls back to a live
     * lookup when the context has no cache (outside a scan).
     */
    public static ObjectComposition resolveDoorComposition(DoorProbeContext ctx, TileObject object) {
        if (object == null) {
            return null;
        }
        Map<TileObject, Optional<ObjectComposition>> cache = ctx == null ? null : ctx.compositionCache();
        if (cache == null) {
            return Rs2GameObject.convertToObjectComposition(object);
        }
        return cache.computeIfAbsent(object,
                        ignored -> Optional.ofNullable(Rs2GameObject.convertToObjectComposition(object)))
                .orElse(null);
    }

    /**
     * Whether the catalog transport executor, rather than generic door detection, owns this scene
     * object. A route-selected OBJECT executor has the exact edge, action and completion predicate;
     * allowing the geometry-based door cascade to claim the same object gives two independent
     * handlers permission to cross it and can immediately reverse a successful transport.
     */
    public static boolean isCatalogTransportObject(TileObject object) {
        if (object == null) {
            return false;
        }
        WorldPoint loc = object.getWorldLocation();
        if (loc == null || object.getId() <= 0) {
            return false;
        }

		for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
				WorldPoint origin = new WorldPoint(loc.getX() + dx, loc.getY() + dy, loc.getPlane());
				for (Rs2TransportEdge transport : Rs2PathApi.getCatalogTransportEdges(origin)) {
					if (transport == null || transport.getObjectId() != object.getId()) {
						continue;
					}
					if (isObjectExecutorTransport(transport)) {
						return true;
					}
                }
            }
        }
		return false;
    }

	/**
	 * True for a catalogued interaction handled by the generic object executor. The executor can
	 * click doors, stairs, ladders, stiles and other one-action scene objects from range; it is also
	 * the sole owner of those objects while their edge is selected by the route.
	 */
	static boolean isObjectExecutorTransport(Rs2TransportEdge transport) {
		return transport != null
				&& transport.getExecutor() == Rs2TransportExecutor.OBJECT;
	}

	public static boolean isDoorLikeCatalogTransport(Rs2TransportEdge transport) {
		if (transport == null || transport.getType() != Rs2TransportType.TRANSPORT) {
            return false;
        }
        // The ACTION wins over the name. A stile is named door-like and a fence gap is not named at
        // all, but both are crossed by moving through them, and the door cascade can only wait for an
        // edge to open — a wait a moves-you obstacle can never satisfy. Deciding on the name alone is
        // what handed a Climb-over stile to the door handler and cost twenty seconds per crossing.
        if (Rs2DoorClassifier.isMovesYouAction(transport.getAction())) {
            return false;
        }
		return Rs2DoorClassifier.isDoorLikeGameObjectName(transport.getTarget())
                || Rs2DoorClassifier.isDoorLikeGameObjectName(transport.getDisplayInfo())
                || isDoorLikeTransportAction(transport.getAction());
    }

    private static boolean isDoorLikeTransportAction(String action) {
        if (action == null) {
            return false;
        }
        return Rs2DoorClassifier.doorActionPriorityIndex(action) != Integer.MAX_VALUE;
    }

    /** Whether {@code object} (at {@code objectLocation}) is a walk-through door lying on the segment. */
    public static boolean isDoorCandidateOnSegment(DoorProbeContext ctx, DoorAttemptLedger ledger,
                                                   TileObject object, WorldPoint objectLocation,
                                                   WorldPoint playerLoc, WorldPoint fromWp, WorldPoint toWp,
                                                   List<String> doorActions, int searchDistance) {
        if (object == null || objectLocation == null) {
            return false;
        }
        WorldPoint loc = objectLocation;
        // Cheap, player-relative checks first — these depend on where the player is standing, so they
        // stay live rather than memoised.
        if (loc.getPlane() != playerLoc.getPlane()
                || loc.distanceTo2D(playerLoc) > searchDistance
                || ledger.isDoorBlacklisted(loc)
                || (!(object instanceof WallObject) && !(object instanceof GameObject))) {
            return false;
        }
        if (isCatalogTransportObject(ctx, object)) {
            return false;
        }
        // Snapshot location, not object.getWorldLocation(): this runs per candidate per segment.
        if (!Rs2DoorGeometry.isDoorOnSegment(object, loc, fromWp, toWp)) {
            return false;
        }
        ObjectComposition comp = resolveDoorComposition(ctx, object);
        if (!Rs2DoorClassifier.isDoorComposition(comp, doorActions)) {
            return false;
        }
        // The decide table's classification rule (D3 requirement #3): an Open-actioned GameObject
        // with a non-door name is scenery, not a route door — see Rs2DoorClassifier.isRouteDoorObject.
        return Rs2DoorClassifier.isRouteDoorObject(object instanceof WallObject, comp.getName(),
                Rs2DoorClassifier.getDoorAction(comp, doorActions));
    }

    /**
     * "An object owned by the catalog transport executor" — the expensive, segment-independent half of
     * the candidate test, memoised for the scan via {@link DoorProbeContext#objectEligibilityCache()}.
     * <p>
     * The answer depends only on the object (id, location, composition), yet the probe re-evaluated it
     * for every route segment against the entire snapshot, paying a {@code getWorldLocation()}, nine
     * transport-map lookups and an uncached composition resolve each time. With no cache available the
     * behaviour is unchanged, just uncached.
     */
    private static boolean isCatalogTransportObject(DoorProbeContext ctx, TileObject object) {
        Map<TileObject, Boolean> cache = ctx == null ? null : ctx.objectEligibilityCache();
        if (cache == null) {
            return isCatalogTransportObject(object);
        }
        return cache.computeIfAbsent(object, Rs2DoorProbe::isCatalogTransportObject);
    }

    /** Nearest walk-through door lying on the {@code fromWp -> toWp} segment, using scan snapshots when present. */
    public static TileObject findDoorNearSegment(DoorProbeContext ctx, DoorAttemptLedger ledger,
                                                 long stationaryDoorSuppressMs,
                                                 WorldPoint fromWp, WorldPoint toWp, List<String> doorActions) {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null || fromWp == null || toWp == null || fromWp.getPlane() != toWp.getPlane()) {
            return null;
        }
        if (ledger.recentlyOpenedDoorOnSegment(fromWp, toWp, stationaryDoorSuppressMs, System.currentTimeMillis())) {
            return null;
        }

        final int searchDistance = 10;
        List<WallObject> wallSnapshot = ctx.wallSnapshot();
        List<GameObject> gameObjectSnapshot = ctx.gameObjectSnapshot();
        if (wallSnapshot != null || gameObjectSnapshot != null) {
            Map<TileObject, WorldPoint> locations = ctx.locationSnapshot();
            if (locations == null) {
                return null;
            }
            Map<String, Optional<TileObject>> segmentCache = ctx.segmentCache();
            String segmentKey = fromWp.getX() + "," + fromWp.getY() + "," + fromWp.getPlane()
                    + ">" + toWp.getX() + "," + toWp.getY() + "," + toWp.getPlane();
            if (segmentCache != null && segmentCache.containsKey(segmentKey)) {
                return segmentCache.get(segmentKey).orElse(null);
            }
            List<TileObject> candidates = new ArrayList<>(
                    (wallSnapshot != null ? wallSnapshot.size() : 0)
                            + (gameObjectSnapshot != null ? gameObjectSnapshot.size() : 0));
            if (wallSnapshot != null) {
                candidates.addAll(wallSnapshot);
            }
            if (gameObjectSnapshot != null) {
                candidates.addAll(gameObjectSnapshot);
            }
            TileObject match = candidates.stream()
                    .filter(o -> isDoorCandidateOnSegment(ctx, ledger, o, locations.get(o),
                            playerLoc, fromWp, toWp, doorActions, searchDistance))
                    .min(Comparator.comparingInt(o -> locations.get(o).distanceTo2D(playerLoc)))
                    .orElse(null);
            if (segmentCache != null) {
                segmentCache.put(segmentKey, Optional.ofNullable(match));
            }
            return match;
        }
        return Rs2GameObject.getAll(o -> isDoorCandidateOnSegment(ctx, ledger, o, o.getWorldLocation(),
                        playerLoc, fromWp, toWp, doorActions, searchDistance), playerLoc, searchDistance).stream()
                .min(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo2D(playerLoc)))
                .orElse(null);
    }
}
