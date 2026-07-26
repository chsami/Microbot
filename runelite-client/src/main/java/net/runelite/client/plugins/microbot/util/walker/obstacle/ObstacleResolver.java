package net.runelite.client.plugins.microbot.util.walker.obstacle;

/**
 * Resolves one kind of dynamic obstacle blocking a {@link PlannedEdge} — a door, a rockfall, an agility
 * shortcut / transport. Replaces a whole family of bespoke {@code Rs2Walker} handlers with one small unit
 * that plugs into the {@link ObstacleRegistry} (P2; docs/walker-p2-unification.md).
 * <p>
 * {@link #handles} is a pure, cheap classification (does this resolver apply to this edge, given the
 * scene?); {@link #resolve} is where the (possibly acting) resolution happens. Keeping the two split lets
 * the classification be unit-tested headlessly with a fake {@link LiveScene}.
 */
public interface ObstacleResolver {

    /**
     * @return {@code true} if this resolver is responsible for the obstacle on {@code edge}. Must not act
     * (no clicks/interactions) — classification only.
     */
    boolean handles(PlannedEdge edge, LiveScene scene);

    /**
     * Attempts to make {@code edge} traversable, using {@code actions} to interact with the world. Only
     * called when {@link #handles} returned {@code true} for the same edge/scene.
     */
    ObstacleResolution resolve(PlannedEdge edge, LiveScene scene, WalkerActions actions);
}
