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
import net.runelite.client.plugins.microbot.shortestpath.Transport;
import net.runelite.client.plugins.microbot.shortestpath.TransportType;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;

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
     * True when this scene object is the interactable listed on a transport catalog row (same
     * coordinates and object ids as TSV loaded into {@link Rs2PathApi#getTransports()}), and is not
     * itself door-like. Used to avoid treating a catalog transport as a plain door.
     */
    public static boolean isCatalogTransportObject(TileObject object) {
        if (object == null) {
            return false;
        }
        WorldPoint loc = object.getWorldLocation();
        if (loc == null) {
            return false;
        }
        int id = object.getId();
        if (id <= 0) {
            return false;
        }
        Map<WorldPoint, Set<Transport>> map = Rs2PathApi.getTransports();
        if (map == null || map.isEmpty()) {
            return false;
        }
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                WorldPoint catalogOrigin = new WorldPoint(loc.getX() + dx, loc.getY() + dy, loc.getPlane());
                Set<Transport> transports = map.get(catalogOrigin);
                if (transports == null || transports.isEmpty()) {
                    continue;
                }
                for (Transport t : transports) {
                    if (t != null && t.getObjectId() == id && !isDoorLikeCatalogTransport(t)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isDoorLikeCatalogTransport(Transport transport) {
        if (transport == null || transport.getType() != TransportType.TRANSPORT) {
            return false;
        }
        return Rs2DoorClassifier.isDoorLikeGameObjectName(transport.getName())
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
    public static boolean isDoorCandidateOnSegment(DoorProbeContext ctx, Set<WorldPoint> blacklist,
                                                   TileObject object, WorldPoint objectLocation,
                                                   WorldPoint playerLoc, WorldPoint fromWp, WorldPoint toWp,
                                                   List<String> doorActions, int searchDistance) {
        if (object == null || objectLocation == null) {
            return false;
        }
        WorldPoint loc = objectLocation;
        if (loc.getPlane() != playerLoc.getPlane()
                || loc.distanceTo2D(playerLoc) > searchDistance
                || blacklist.contains(loc)
                || (!(object instanceof WallObject) && !(object instanceof GameObject))
                || (isCatalogTransportObject(object) && !Rs2DoorDetection.isDoorLikeSceneObject(object))
                || !Rs2DoorGeometry.isDoorOnSegment(object, fromWp, toWp)) {
            return false;
        }
        ObjectComposition comp = resolveDoorComposition(ctx, object);
        return Rs2DoorClassifier.isDoorComposition(comp, doorActions);
    }

    /** Nearest walk-through door lying on the {@code fromWp -> toWp} segment, using scan snapshots when present. */
    public static TileObject findDoorNearSegment(DoorProbeContext ctx, Set<WorldPoint> blacklist,
                                                 Map<WorldPoint, Long> recentlyOpened, long stationaryDoorSuppressMs,
                                                 WorldPoint fromWp, WorldPoint toWp, List<String> doorActions) {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        if (playerLoc == null || fromWp == null || toWp == null || fromWp.getPlane() != toWp.getPlane()) {
            return null;
        }
        if (Rs2DoorHandler.recentlyOpenedStationaryDoorOnSegment(recentlyOpened, stationaryDoorSuppressMs, fromWp, toWp)) {
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
                    .filter(o -> isDoorCandidateOnSegment(ctx, blacklist, o, locations.get(o),
                            playerLoc, fromWp, toWp, doorActions, searchDistance))
                    .min(Comparator.comparingInt(o -> locations.get(o).distanceTo2D(playerLoc)))
                    .orElse(null);
            if (segmentCache != null) {
                segmentCache.put(segmentKey, Optional.ofNullable(match));
            }
            return match;
        }
        return Rs2GameObject.getAll(o -> isDoorCandidateOnSegment(ctx, blacklist, o, o.getWorldLocation(),
                        playerLoc, fromWp, toWp, doorActions, searchDistance), playerLoc, searchDistance).stream()
                .min(Comparator.comparingInt(o -> o.getWorldLocation().distanceTo2D(playerLoc)))
                .orElse(null);
    }
}
