package net.runelite.client.plugins.microbot.util.walker.obstacle;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.Transport;

import java.util.Set;

/**
 * Resolves a planned edge blocked because its far side is across a transport / agility shortcut (a stepping
 * stone, ladder, cave, teleport) whose origin the player has not yet stepped onto (P2;
 * docs/walker-p2-unification.md).
 * <p>
 * Fully pure — the actual transport interaction stays in the normal walk loop, which fires once the player
 * stands on the origin. This resolver only decides "walk onto the reachable origin", which is exactly the
 * stepping-stone recovery fix ({@code RouteRecovery.findReachableTransportOriginAhead}) expressed in the
 * unified model, so it is unit-tested headlessly with an in-memory {@link LiveScene}.
 */
public final class TransportResolver implements ObstacleResolver {

    @Override
    public boolean handles(PlannedEdge edge, LiveScene scene) {
        if (edge == null || scene == null || edge.from() == null) {
            return false;
        }
        final WorldPoint origin = edge.from();
        final Set<Transport> transports = scene.transportsAt(origin);
        if (transports == null || transports.isEmpty()) {
            return false;
        }
        final WorldPoint player = scene.playerLocation();
        // Applies only when the player is off the origin but can still reach it: then stepping onto it lets
        // the normal loop take the transport. When already on the origin, that loop owns it (not recovery);
        // when the origin is unreachable, this resolver can't help.
        return player != null && !player.equals(origin) && scene.isReachable(origin);
    }

    @Override
    public ObstacleResolution resolve(PlannedEdge edge, LiveScene scene, WalkerActions actions) {
        return ObstacleResolution.walkToOrigin(edge.from());
    }
}
