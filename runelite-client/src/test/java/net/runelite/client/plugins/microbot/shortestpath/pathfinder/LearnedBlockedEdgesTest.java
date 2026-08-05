package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.shortestpath.WorldPointUtil;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Covers the learned-blocked-edge substrate: the human-editable TSV round-trip and its lenient parsing,
 * plus the packed-edge block check the pathfinder actually consults ({@link PathfinderConfig#isBlockedTransportStep}).
 * Deliberately avoids constructing a full {@link PathfinderConfig} (heavy game deps) — the graph wiring is
 * exercised through the same static predicate {@code getNeighbors}/{@code getReverseNeighbors} use.
 */
public class LearnedBlockedEdgesTest {

    private static final WorldPoint FROM = new WorldPoint(3200, 3200, 0);
    private static final WorldPoint TO = new WorldPoint(3201, 3200, 0); // one tile east

    @Test
    public void appendThenLoadRoundTrips() throws Exception {
        File file = Files.createTempFile("learned-edges", ".tsv").toFile();
        file.deleteOnExit();
        Files.delete(file.toPath()); // start from "no file" so append writes the header

        LearnedBlockedEdges.append(file, new LearnedBlockedEdges.Edge(FROM, TO, false, "wrong-traversal door @ 3200,3200,0"));

        List<LearnedBlockedEdges.Edge> loaded = LearnedBlockedEdges.load(file);
        assertEquals(1, loaded.size());
        assertEquals(FROM, loaded.get(0).origin);
        assertEquals(TO, loaded.get(0).destination);
        assertFalse(loaded.get(0).bidirectional);
        assertTrue(loaded.get(0).info.contains("wrong-traversal"));
    }

    @Test
    public void strikeColumnsRoundTripAndSaveRewrites() throws Exception {
        File file = Files.createTempFile("learned-edges-strikes", ".tsv").toFile();
        file.deleteOnExit();
        Files.delete(file.toPath());

        LearnedBlockedEdges.append(file, new LearnedBlockedEdges.Edge(FROM, TO, false, "probation", 1, 123456789L));
        List<LearnedBlockedEdges.Edge> loaded = LearnedBlockedEdges.load(file);
        assertEquals(1, loaded.size());
        assertEquals(1, loaded.get(0).strikes);
        assertEquals(123456789L, loaded.get(0).lastStrikeAtMs);

        LearnedBlockedEdges.save(file, List.of(loaded.get(0).withStrikeAt(987654321L)));
        loaded = LearnedBlockedEdges.load(file);
        assertEquals("save must rewrite, not append", 1, loaded.size());
        assertEquals(2, loaded.get(0).strikes);
        assertEquals(987654321L, loaded.get(0).lastStrikeAtMs);
        assertEquals("row identity survives the rewrite", FROM, loaded.get(0).origin);
    }

    @Test
    public void legacyRowsWithoutStrikeColumnsParseAsConfirmed() throws Exception {
        File file = Files.createTempFile("learned-edges-legacy", ".tsv").toFile();
        file.deleteOnExit();
        String content = String.join(System.lineSeparator(),
                "# Origin\tDestination\tBidirectional\tDisplay info",
                "3200 3200 0\t3201 3200 0\tfalse\tlegacy row");
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));

        List<LearnedBlockedEdges.Edge> loaded = LearnedBlockedEdges.load(file);
        assertEquals(1, loaded.size());
        assertEquals("rows predating strike tracking stay unconditionally trusted",
                LearnedBlockedEdges.LEGACY_STRIKES, loaded.get(0).strikes);
        assertEquals(0L, loaded.get(0).lastStrikeAtMs);
    }

    @Test
    public void loadMissingFileYieldsEmpty() {
        File missing = new File(System.getProperty("java.io.tmpdir"), "learned-edges-does-not-exist-" + System.nanoTime() + ".tsv");
        assertTrue(LearnedBlockedEdges.load(missing).isEmpty());
    }

    @Test
    public void malformedRowsAreSkippedNotFatal() throws Exception {
        File file = Files.createTempFile("learned-edges-malformed", ".tsv").toFile();
        file.deleteOnExit();
        String content = String.join(System.lineSeparator(),
                "# Origin\tDestination\tBidirectional\tDisplay info",
                "3200 3200 0\t3201 3200 0\tfalse\tgood row",
                "this is not a valid row",              // too few columns
                "3200 3200\t3201 3200 0\tfalse\tbad origin (2 coords)", // unparseable point
                "3300 3300 0\t3301 3300 0\ttrue\tsecond good row (bidirectional)");
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));

        List<LearnedBlockedEdges.Edge> loaded = LearnedBlockedEdges.load(file);
        assertEquals(2, loaded.size());
        assertTrue(loaded.get(1).bidirectional);
    }

    @Test
    public void learnedEdgeKeyBlocksTheCardinalStep() {
        Set<Long> blocked = new HashSet<>();
        blocked.add(PathfinderConfig.transportEdgeKey(
                WorldPointUtil.packWorldPoint(FROM),
                WorldPointUtil.packWorldPoint(TO)));

        // The exact learned direction is blocked...
        assertTrue(PathfinderConfig.isBlockedTransportStep(
                WorldPointUtil.packWorldPoint(FROM),
                WorldPointUtil.packWorldPoint(TO),
                blocked));

        // ...but the reverse edge is not (we learn only the attempted direction).
        assertFalse(PathfinderConfig.isBlockedTransportStep(
                WorldPointUtil.packWorldPoint(TO),
                WorldPointUtil.packWorldPoint(FROM),
                blocked));

        // An unrelated edge stays open.
        WorldPoint elsewhere = new WorldPoint(3500, 3500, 0);
        assertFalse(PathfinderConfig.isBlockedTransportStep(
                WorldPointUtil.packWorldPoint(elsewhere),
                WorldPointUtil.packWorldPoint(new WorldPoint(3501, 3500, 0)),
                blocked));
    }

    @Test
    public void emptyBlockSetNeverBlocks() {
        assertFalse(PathfinderConfig.isBlockedTransportStep(
                WorldPointUtil.packWorldPoint(FROM),
                WorldPointUtil.packWorldPoint(TO),
                new HashSet<>()));
    }
}
