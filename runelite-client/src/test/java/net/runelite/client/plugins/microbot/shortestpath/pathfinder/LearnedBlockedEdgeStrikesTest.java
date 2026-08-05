package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import net.runelite.api.coords.WorldPoint;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Two-strike hardening, tested purely through {@code learnBlockedEdge}'s return value (true = newly
 * blocked this session, false = already enforced) and the on-disk rows — no private state:
 *
 * <ul>
 *   <li>The observing session blocks immediately (it watched the failure happen).</li>
 *   <li>A single strike does NOT survive a restart — the row is probation, so one bad sample (the
 *       Wydin door poisoning, which needed a hand-edit) self-heals.</li>
 *   <li>A second observation, independent by the 10-minute window, confirms and enforces forever.</li>
 *   <li>Legacy rows without strike columns keep their unconditional trust.</li>
 * </ul>
 *
 * A "restart" is simulated with the same package-private seam the store already exposes:
 * {@code setLearnedBlockedEdgesFileForTest} clears and reloads from the file.
 */
public class LearnedBlockedEdgeStrikesTest {

    private static final WorldPoint FROM = new WorldPoint(3012, 3204, 0);
    private static final WorldPoint TO = new WorldPoint(3011, 3204, 0);

    private static SplitFlagMap collisionMap;

    private PathfinderConfig config;
    private File store;

    @BeforeClass
    public static void loadMap() {
        collisionMap = SplitFlagMap.fromResources();
    }

    @Before
    public void setUp() throws Exception {
        store = Files.createTempFile("learned-strikes", ".tsv").toFile();
        store.deleteOnExit();
        Files.delete(store.toPath());
        config = new PathfinderConfig(collisionMap, new HashMap<>(), Collections.emptyList(), null, null);
        config.setLearnedBlockedEdgesFileForTest(store);
    }

    private void simulateRestart() {
        config.setLearnedBlockedEdgesFileForTest(store);
    }

    @Test
    public void firstStrikeBlocksTheSessionButDoesNotSurviveRestart() {
        assertTrue("first observation must block this session",
                config.learnBlockedEdge(FROM, TO, "wrong-traversal"));
        assertFalse("repeat in the same session is already enforced",
                config.learnBlockedEdge(FROM, TO, "wrong-traversal"));

        List<LearnedBlockedEdges.Edge> rows = LearnedBlockedEdges.load(store);
        assertEquals(1, rows.size());
        assertEquals("persisted on probation", 1, rows.get(0).strikes);

        simulateRestart();
        assertTrue("a probation row must NOT be enforced on load — learning it again must succeed",
                config.learnBlockedEdge(FROM, TO, "wrong-traversal"));
        assertEquals("a re-observation within the independence window must not confirm",
                1, LearnedBlockedEdges.load(store).get(0).strikes);
    }

    @Test
    public void independentSecondStrikeConfirmsAndEnforces() {
        long elevenMinutesAgo = System.currentTimeMillis() - 11 * 60_000L;
        LearnedBlockedEdges.append(store, new LearnedBlockedEdges.Edge(
                FROM, TO, false, "wrong-traversal", 1, elevenMinutesAgo));
        simulateRestart();

        assertTrue("probation row is not enforced, so the session may observe it again",
                config.learnBlockedEdge(FROM, TO, "wrong-traversal"));

        List<LearnedBlockedEdges.Edge> rows = LearnedBlockedEdges.load(store);
        assertEquals(1, rows.size());
        assertEquals("independent second strike must confirm", 2, rows.get(0).strikes);

        simulateRestart();
        assertFalse("a confirmed row must be enforced on load",
                config.learnBlockedEdge(FROM, TO, "wrong-traversal"));
    }

    @Test
    public void legacyRowsWithoutStrikeColumnsStayEnforced() throws Exception {
        String content = "# Origin\tDestination\tBidirectional\tDisplay info" + System.lineSeparator()
                + "3012 3204 0\t3011 3204 0\tfalse\tlegacy hand-copied row" + System.lineSeparator();
        Files.write(store.toPath(), content.getBytes(StandardCharsets.UTF_8));
        simulateRestart();

        assertFalse("legacy rows predate strike tracking and keep their unconditional trust",
                config.learnBlockedEdge(FROM, TO, "wrong-traversal"));
    }
}
