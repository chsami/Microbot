package net.runelite.client.plugins.microbot.util.walker.obstacle;

import java.util.ArrayList;
import java.util.List;

/**
 * The single dispatch point that replaces the walker's ~40-branch door/rockfall/shortcut/transport cascade
 * (P2; docs/walker-p2-unification.md). Holds an ordered list of {@link ObstacleResolver}s; the first whose
 * {@link ObstacleResolver#handles} matches the {@link PlannedEdge} resolves it. Order encodes precedence
 * (e.g. a specific mineable obstacle before a generic transport).
 */
public final class ObstacleRegistry {

    private final List<ObstacleResolver> resolvers;

    public ObstacleRegistry(List<ObstacleResolver> resolvers) {
        this.resolvers = new ArrayList<>(resolvers);
    }

    /**
     * Resolves the obstacle on {@code edge} via the first matching resolver, or {@link
     * ObstacleResolution#notApplicable()} when none matches (caller falls back to plain recovery).
     */
    public ObstacleResolution resolve(PlannedEdge edge, LiveScene scene, WalkerActions actions) {
        if (edge == null || scene == null) {
            return ObstacleResolution.notApplicable();
        }
        for (ObstacleResolver resolver : resolvers) {
            if (resolver.handles(edge, scene)) {
                return resolver.resolve(edge, scene, actions);
            }
        }
        return ObstacleResolution.notApplicable();
    }
}
