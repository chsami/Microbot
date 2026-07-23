package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

/**
 * Shared, atomically-swappable holder for the current {@link LiveCollisionSnapshot}.
 * <p>
 * One instance is referenced by every {@code CollisionMap} — which is a {@code ThreadLocal} over a shared
 * immutable {@code SplitFlagMap}, so the live overlay must live outside those per-thread instances. The
 * client thread swaps the snapshot via {@link #set}; the off-thread pathfinder reads it as a single
 * volatile load through {@link #current()}. Because a snapshot is immutable, a reader that pins the
 * reference for the duration of one search sees a consistent view even if the client thread swaps in a
 * newer one mid-search.
 * <p>
 * When {@link #isEnabled()} is {@code false} — the default, and whenever the config flag is off — there is
 * no snapshot and callers behave exactly as the static-only map does today.
 */
public final class LiveCollisionOverlay {
    private volatile boolean enabled;
    private volatile LiveCollisionSnapshot current;

    /**
     * @return the current snapshot, or {@code null} when disabled or nothing has been captured yet.
     * Callers doing more than one edge read should pin this into a local so the whole search sees one
     * immutable view.
     */
    public LiveCollisionSnapshot current() {
        return enabled ? current : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            current = null;
        }
    }

    /** Swaps in a freshly captured snapshot. No effect while disabled. */
    public void set(LiveCollisionSnapshot snapshot) {
        if (enabled) {
            current = snapshot;
        }
    }

    public void clear() {
        current = null;
    }
}
