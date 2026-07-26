package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Shared, accumulating holder for live collision. One instance is referenced by every {@code CollisionMap}
 * (which is a {@code ThreadLocal} over a shared immutable {@code SplitFlagMap}, so the live data must live
 * outside those per-thread instances).
 * <p>
 * Unlike the original single-scene holder, this accumulates every captured scene into a region-addressed
 * store: the client thread merges each new scene ({@link #set}/{@link #mergeScene}) and previously loaded
 * regions ({@link #putRegion}, from disk) into an immutable {@link LiveCollisionView} that the off-thread
 * pathfinder reads as one volatile load through {@link #current()}. Because each view is immutable, a reader
 * that pins it for the duration of one search sees a consistent view even if the client thread merges a
 * newer scene mid-search. Accumulation means the overlay keeps covering a region after the player leaves it,
 * so routing through already-seen ground reflects the client's real collision instead of the static map's
 * reconstruction.
 * <p>
 * When {@link #isEnabled()} is {@code false} — the default, and whenever the config flag is off —
 * {@link #current()} returns {@code null} and callers behave exactly as the static-only map does.
 */
public final class LiveCollisionOverlay {
    private volatile boolean enabled;
    private volatile LiveCollisionView current;

    /** Mutated only under {@code this}; the immutable {@link #current} view is what readers see. */
    private final Map<Integer, LiveCollisionRegion> regions = new HashMap<>();
    private final Set<Integer> dirty = new HashSet<>();

    /**
     * @return the current accumulated view, or {@code null} when disabled or nothing has been captured yet.
     * Callers doing more than one edge read should pin this into a local so the whole search sees one
     * immutable view.
     */
    public LiveCollisionView current() {
        return enabled ? current : null;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public synchronized void setEnabled(boolean enabled) {
        this.enabled = enabled;
        if (!enabled) {
            clearLocked();
        }
    }

    /**
     * Backwards-compatible entry point: merges a freshly captured scene into the accumulated store. No
     * effect while disabled. Named {@code set} for historical callers; it no longer replaces the previous
     * scene, it merges onto it.
     */
    public void set(LiveCollisionSnapshot snapshot) {
        mergeScene(snapshot);
    }

    /** Merges a captured scene into the store. No effect while disabled or for a {@code null} scene. */
    public synchronized void mergeScene(LiveCollisionSnapshot snapshot) {
        if (!enabled || snapshot == null) {
            return;
        }
        if (LiveCollisionRegions.mergeSceneInto(regions, dirty, snapshot)) {
            current = new LiveCollisionView(new HashMap<>(regions));
        }
    }

    /**
     * Merges a region loaded from persistence into the store. No effect while disabled. Used at enable-time
     * to seed the overlay with everything learned in earlier sessions.
     */
    public synchronized void putRegion(int regionId, LiveCollisionRegion region) {
        if (!enabled || region == null) {
            return;
        }
        final LiveCollisionRegion existing = regions.get(regionId);
        // The persisted region is the base; anything already captured this session is fresher and must
        // win on conflict, in case a live capture merged this region before the async load reached it.
        final LiveCollisionRegion merged = existing == null
                ? region
                : LiveCollisionRegions.merge(region, existing);
        regions.put(regionId, merged);
        current = new LiveCollisionView(new HashMap<>(regions));
    }

    /**
     * Removes and returns the regions that changed since the last drain, for the persistence layer to write
     * out. Returns immutable region references, safe to read off-thread.
     */
    public synchronized Map<Integer, LiveCollisionRegion> drainDirty() {
        if (dirty.isEmpty()) {
            return java.util.Collections.emptyMap();
        }
        final Map<Integer, LiveCollisionRegion> out = new HashMap<>(dirty.size() * 2);
        for (Integer id : dirty) {
            final LiveCollisionRegion r = regions.get(id);
            if (r != null) {
                out.put(id, r);
            }
        }
        dirty.clear();
        return out;
    }

    public int regionCount() {
        final LiveCollisionView view = current;
        return view == null ? 0 : view.regionCount();
    }

    public synchronized void clear() {
        clearLocked();
    }

    private void clearLocked() {
        regions.clear();
        dirty.clear();
        current = null;
    }
}
