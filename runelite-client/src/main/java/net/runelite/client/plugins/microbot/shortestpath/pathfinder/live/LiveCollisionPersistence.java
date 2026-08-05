package net.runelite.client.plugins.microbot.shortestpath.pathfinder.live;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.RuneLite;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Disk backing for the accumulating live-collision store, so what the bot learns while walking survives a
 * restart and keeps overriding the static map's reconstruction permanently (until a game update).
 * <p>
 * One binary file per region under
 * {@code ~/.runelite/microbot/live-collision/<cacheRevision>-c<captureVersion>/<regionId>.lcr}.
 * The cache revision and {@link LiveCollisionCapture#CAPTURE_VERSION capture-semantics version} form the
 * invalidation key: after either changes, previous regions are never read (and can be pruned later) rather
 * than being trusted against changed geometry or capture rules.
 * <p>
 * All I/O runs on a single daemon thread; loads and stores never touch the client or pathfinder threads.
 * {@link LiveCollisionOverlay#putRegion} and {@link LiveCollisionOverlay#drainDirty} are the only
 * synchronisation points, and both are already thread-safe.
 */
@Slf4j
public final class LiveCollisionPersistence {
    private static final int MAGIC = 0x4C435231; // "LCR1"
    private static final int VERSION = 1;

    private final File dir;
    /** Root removed by {@link #deleteAllNow()} — the whole {@code live-collision} tree (all revisions). */
    private final File deleteRoot;
    private final ExecutorService io = Executors.newSingleThreadExecutor(r -> {
        final Thread t = new Thread(r, "live-collision-io");
        t.setDaemon(true);
        return t;
    });

    public LiveCollisionPersistence(int cacheRevision) {
        final File liveCollisionBase = new File(new File(RuneLite.RUNELITE_DIR, "microbot"), "live-collision");
        // Store key = game cache revision + capture-semantics version. A change to either sends the
        // store to a fresh directory; the old ones are inert and pruned by pruneStaleStores().
        this.dir = new File(liveCollisionBase,
                cacheRevision + "-c" + LiveCollisionCapture.CAPTURE_VERSION);
        this.deleteRoot = liveCollisionBase;
    }

    /** Backs the store with an explicit directory instead of the shared user dir (tests, tooling). */
    public LiveCollisionPersistence(File dir) {
        this.dir = dir;
        this.deleteRoot = dir;
    }

    /**
     * Asynchronously reads every persisted region for this cache revision and merges it into {@code overlay}.
     * Safe to call once at enable-time; a corrupt or partial file is skipped, not fatal.
     */
    public void loadIntoAsync(LiveCollisionOverlay overlay) {
        io.execute(() -> {
            pruneStaleStores();
            final File[] files = dir.listFiles((d, name) -> name.endsWith(".lcr"));
            if (files == null) {
                return;
            }
            int loaded = 0;
            for (File f : files) {
                final Integer regionId = parseRegionId(f.getName());
                if (regionId == null) {
                    continue;
                }
                final LiveCollisionRegion region = readRegion(f);
                if (region != null) {
                    overlay.putRegion(regionId, region);
                    loaded++;
                }
            }
            if (loaded > 0) {
                log.debug("[LiveCollision] loaded {} persisted regions from {}", loaded, dir);
            }
        });
    }

    /** Asynchronously writes the given (region id -> region) entries, creating the directory as needed. */
    public void persist(Map<Integer, LiveCollisionRegion> dirtyRegions) {
        if (dirtyRegions == null || dirtyRegions.isEmpty()) {
            return;
        }
        // Copy the map reference set; the regions themselves are immutable so no defensive copy is needed.
        final Map<Integer, LiveCollisionRegion> batch = Map.copyOf(dirtyRegions);
        io.execute(() -> {
            if (!dir.exists() && !dir.mkdirs()) {
                log.warn("[LiveCollision] could not create {}", dir);
                return;
            }
            for (Map.Entry<Integer, LiveCollisionRegion> e : batch.entrySet()) {
                writeRegion(e.getKey(), e.getValue());
            }
        });
    }

    /**
     * Asynchronously deletes this store's entire on-disk tree (all cache revisions). Queued behind any
     * pending writes on the I/O thread, so a concurrent capture that re-persists afterwards is not lost.
     */
    public void deleteAllAsync() {
        io.execute(this::deleteAllNow);
    }

    /** Synchronously deletes this store's entire on-disk tree. */
    public void deleteAllNow() {
        deleteRecursively(deleteRoot);
    }

    /**
     * Removes every sibling store directory except the current key ({@code <rev>-c<captureVersion>}),
     * so data from an old game-cache revision or an old capture-semantics version is dropped rather
     * than lingering. No-op for the explicit-dir (test) constructor, where {@code dir == deleteRoot}.
     */
    private void pruneStaleStores() {
        if (dir.equals(deleteRoot)) {
            return;
        }
        final File[] siblings = deleteRoot.listFiles();
        if (siblings == null) {
            return;
        }
        for (File sibling : siblings) {
            if (sibling.isDirectory() && !sibling.getName().equals(dir.getName())) {
                deleteRecursively(sibling);
            }
        }
    }

    private static void deleteRecursively(File f) {
        if (f == null || !f.exists()) {
            return;
        }
        final File[] children = f.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        if (!f.delete()) {
            log.warn("[LiveCollision] could not delete {}", f);
        }
    }

    /** Flushes pending writes and stops the I/O thread. */
    public void shutdown() {
        io.shutdown();
        try {
            io.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private void writeRegion(int regionId, LiveCollisionRegion region) {
        final File target = new File(dir, regionId + ".lcr");
        final File tmp = new File(dir, regionId + ".lcr.tmp");
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(new FileOutputStream(tmp)))) {
            out.writeInt(MAGIC);
            out.writeInt(VERSION);
            out.writeInt(LiveCollisionCapture.CAPTURE_VERSION);
            out.writeInt(region.getPlaneCount());
            writeWords(out, region.northKnownWords());
            writeWords(out, region.northValueWords());
            writeWords(out, region.eastKnownWords());
            writeWords(out, region.eastValueWords());
        } catch (IOException ex) {
            log.warn("[LiveCollision] failed writing region {}: {}", regionId, ex.toString());
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            return;
        }
        try {
            Files.move(tmp.toPath(), target.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException atomicFailed) {
            try {
                Files.move(tmp.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ex) {
                log.warn("[LiveCollision] failed moving region {} into place: {}", regionId, ex.toString());
                //noinspection ResultOfMethodCallIgnored
                tmp.delete();
            }
        }
    }

    private static void writeWords(DataOutputStream out, long[] words) throws IOException {
        out.writeInt(words.length);
        for (long w : words) {
            out.writeLong(w);
        }
    }

    private static LiveCollisionRegion readRegion(File f) {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(new FileInputStream(f)))) {
            if (in.readInt() != MAGIC || in.readInt() != VERSION) {
                return null;
            }
            // Reject data produced by an older capture semantics version; a fresh capture would now record
            // these regions differently, so trusting the old bytes reintroduces exactly the stale-data bug
            // that previously required a manual "Reset learned collision".
            if (in.readInt() != LiveCollisionCapture.CAPTURE_VERSION) {
                return null;
            }
            final int planeCount = in.readInt();
            if (planeCount < 0 || planeCount > 4) {
                return null;
            }
            final long[] nK = readWords(in);
            final long[] nV = readWords(in);
            final long[] eK = readWords(in);
            final long[] eV = readWords(in);
            return LiveCollisionRegion.fromWords(planeCount, nK, nV, eK, eV);
        } catch (IOException | NegativeArraySizeException ex) {
            return null;
        }
    }

    private static long[] readWords(DataInputStream in) throws IOException {
        final int len = in.readInt();
        // A single region is at most 4 planes * 64 * 64 bits = 256 longs; reject anything absurd.
        if (len < 0 || len > 1024) {
            throw new IOException("implausible word length " + len);
        }
        final long[] words = new long[len];
        for (int i = 0; i < len; i++) {
            words[i] = in.readLong();
        }
        return words;
    }

    private static Integer parseRegionId(String fileName) {
        final int dot = fileName.indexOf(".lcr");
        if (dot <= 0) {
            return null;
        }
        try {
            return Integer.parseInt(fileName.substring(0, dot));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
