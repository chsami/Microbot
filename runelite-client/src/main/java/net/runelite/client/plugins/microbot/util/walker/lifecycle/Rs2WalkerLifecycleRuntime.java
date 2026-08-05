package net.runelite.client.plugins.microbot.util.walker.lifecycle;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Player;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2PlannerShadowContext;
import net.runelite.client.plugins.microbot.util.walker.Rs2RouteRequest;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.ui.overlay.worldmap.WorldMapPoint;
import net.runelite.client.ui.overlay.worldmap.WorldMapPointManager;

import java.util.Set;
import java.util.Objects;

@Slf4j
public final class Rs2WalkerLifecycleRuntime {

    private Rs2WalkerLifecycleRuntime() {
    }

    public static void applyWalkerDestination(WorldPoint target) {
        applyWalkerDestination(target, false);
    }

    /** Apply a destination while retaining whether the request was a recovery/replan. */
    public static void applyWalkerDestination(WorldPoint target, boolean replan) {
		applyWalkerDestination(target, replan
				? Rs2PlannerShadowContext.Invocation.ACTIVE_REPLAN
				: Rs2PlannerShadowContext.Invocation.ACTIVE_ROUTE);
	}

	/** Apply a destination with explicit, evidence-only invocation classification. */
	public static void applyWalkerDestination(
			WorldPoint target,
			Rs2PlannerShadowContext.Invocation invocation) {
        Objects.requireNonNull(invocation, "invocation");
		if (invocation == Rs2PlannerShadowContext.Invocation.SYNCHRONOUS_QUERY) {
			throw new IllegalArgumentException("active destination cannot be a synchronous query");
		}
        if (target == null) {
            return;
        }
        if (!Microbot.isLoggedIn()) {
            log.warn("Unable to apply walker destination: not logged in");
            return;
        }
        Client client = Microbot.getClient();
        if (client == null) {
            log.warn("Unable to apply walker destination: client unavailable");
            return;
        }
        Player localPlayer = Microbot.getClientThread().invoke(() -> client.getLocalPlayer());
        if (!Rs2PathApi.isStartPointSet() && localPlayer == null) {
            log.warn("Start point is not set and player is null");
            return;
        }

        WorldMapPointManager wmm = Microbot.getWorldMapPointManager();
        if (wmm == null) {
            Rs2Walker.clearWalkingRoute("walker:wmm-unavailable retry-setTarget dest=" + target);
            return;
        }
        wmm.removeIf(x -> x == Rs2PathApi.getMarker());
        Rs2PathApi.setMarker(new WorldMapPoint(target, Rs2PathApi.MARKER_IMAGE));
        Rs2PathApi.getMarker().setName("Target");
        Rs2PathApi.getMarker().setTarget(Rs2PathApi.getMarker().getWorldPoint());
        Rs2PathApi.getMarker().setJumpOnClick(true);
        wmm.add(Rs2PathApi.getMarker());

        WorldPoint start = Microbot.getClientThread().invoke(() -> {
            if (client.getTopLevelWorldView().isInstance()) {
                LocalPoint localLoc = Rs2Player.getLocalLocation();
                WorldPoint computed = localLoc != null ? WorldPoint.fromLocalInstance(client, localLoc) : null;
                if (computed == null) {
                    log.warn("[Walker] setTarget: instance localPoint conversion returned null (localLoc={} target={}) — falling back to raw world location",
                            localLoc, target);
                    computed = Rs2Player.getWorldLocation();
                }
                WorldPoint exitPortal = net.runelite.client.plugins.microbot.shortestpath.PohPanel.getExitPortalTile();
                if (exitPortal != null) {
                    Microbot.log("[Walker] In POH instance — remapping pathfinder start " + computed
                            + " -> exit portal " + exitPortal);
                    computed = exitPortal;
                }
                return computed;
            }
            return Rs2Player.getWorldLocation();
        });
        final WorldPoint effectiveStart = Rs2PathApi.isStartPointSet()
                ? Rs2PathApi.getActiveRouteStart().orElse(start)
                : start;
        Rs2PathApi.setLastLocation(effectiveStart);
        Microbot.getClientThread().runOnSeperateThread(
                () -> restartPathfinding(effectiveStart, Set.of(target), invocation));
    }

    public static boolean restartPathfinding(WorldPoint start, WorldPoint end) {
        return restartPathfinding(start, Set.of(end));
    }

    public static boolean restartPathfinding(WorldPoint start, Set<WorldPoint> ends) {
        return restartPathfinding(
                start, ends, Rs2PlannerShadowContext.Invocation.ACTIVE_ROUTE);
    }

    private static boolean restartPathfinding(
            WorldPoint start,
            Set<WorldPoint> ends,
            Rs2PlannerShadowContext.Invocation invocation) {
        if (start == null || ends == null || ends.isEmpty()) {
            return false;
        }
        WorldPoint refreshTarget = ends != null && !ends.isEmpty() ? ends.iterator().next() : null;
        int reachedDistance = Rs2Walker.config != null ? Rs2Walker.config.reachedDistance() : 10;
        return Rs2PathApi.restartActiveRoute(
                Rs2RouteRequest.toAny(start, ends)
                        .withRefreshTarget(refreshTarget)
                        .withRefreshPolicy(Rs2RouteRequest.RefreshPolicy.ALWAYS),
                Rs2Player.isInCave(),
                reachedDistance,
                invocation);
    }
}
