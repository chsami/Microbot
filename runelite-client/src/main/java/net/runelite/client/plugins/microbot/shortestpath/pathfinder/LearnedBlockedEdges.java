package net.runelite.client.plugins.microbot.shortestpath.pathfinder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.RuneLite;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/**
 * Human-editable, on-disk store of blocked walking edges the walker <em>learned</em> at runtime — a
 * door it physically failed to traverse the same way twice (e.g. a one-way door, or door geometry the
 * static map doesn't encode). Distinct from the shipped {@code blocked_edges.tsv} resource (curated map-
 * data gaps) and from {@code restrictions.tsv} (quest/skill/item-gated tiles that auto-lift): entries
 * here are stable map properties safe to avoid permanently.
 *
 * <p>The file lives under {@code <runelite>/microbot/learned-blocked-edges.tsv} and shares the first
 * four columns of {@code blocked_edges.tsv} so a line can be copied between them by hand. Because it is
 * user-owned, parsing is deliberately lenient: a malformed row is logged and skipped, never fatal —
 * unlike the resource loader, which throws. Delete the file to reset everything the walker has learned.
 *
 * <p>Columns 5–6 ({@code Strikes}, {@code Last strike ms}) implement two-strike hardening: one bad
 * observation must not poison the store permanently (a mid-walk sample once blacklisted the Wydin shop
 * door and needed a hand-edit). A row is only <em>enforced on load</em> once two independent
 * observations agree; a first-strike row is probation — blocked for the session that observed it,
 * ignored by later sessions until re-confirmed. Rows without the columns (legacy, or hand-copied from
 * {@code blocked_edges.tsv}) parse as already-confirmed so existing behavior is preserved.
 *
 * <p>This class only does file I/O and parsing. The packed-edge encoding, the strike accounting and the
 * pathfinder wiring live in {@link PathfinderConfig}, which owns the authoritative in-memory state.
 */
@Slf4j
public final class LearnedBlockedEdges {
    private static final String DELIM_COLUMN = "\t";
    private static final String PREFIX_COMMENT = "#";
    private static final String HEADER = "# Origin\tDestination\tBidirectional\tDisplay info\tStrikes\tLast strike ms";
    /** Rows predating the strike columns were trusted unconditionally; keep them that way. */
    static final int LEGACY_STRIKES = 2;

    /**
     * One parsed row. {@code bidirectional} blocks the reverse edge too; {@code info} is free-text;
     * {@code strikes}/{@code lastStrikeAtMs} carry the two-strike confirmation state.
     */
    public static final class Edge {
        public final WorldPoint origin;
        public final WorldPoint destination;
        public final boolean bidirectional;
        public final String info;
        public final int strikes;
        public final long lastStrikeAtMs;

        public Edge(WorldPoint origin, WorldPoint destination, boolean bidirectional, String info) {
            this(origin, destination, bidirectional, info, 1, 0L);
        }

        public Edge(WorldPoint origin, WorldPoint destination, boolean bidirectional, String info,
                    int strikes, long lastStrikeAtMs) {
            this.origin = origin;
            this.destination = destination;
            this.bidirectional = bidirectional;
            this.info = info == null ? "" : info;
            this.strikes = strikes;
            this.lastStrikeAtMs = lastStrikeAtMs;
        }

        /** A copy with one more strike stamped at {@code atMs}. */
        public Edge withStrikeAt(long atMs) {
            return new Edge(origin, destination, bidirectional, info, strikes + 1, atMs);
        }
    }

    private LearnedBlockedEdges() {
    }

    /** Default store location, mirroring {@code LiveCollisionPersistence}'s {@code microbot} subdir. */
    public static File defaultFile() {
        return new File(new File(RuneLite.RUNELITE_DIR, "microbot"), "learned-blocked-edges.tsv");
    }

    /**
     * Reads every well-formed row. A missing file yields an empty list; a malformed row is skipped with
     * a warning so one bad hand-edit can't stop the walker from loading the rest.
     */
    public static List<Edge> load(File file) {
        List<Edge> edges = new ArrayList<>();
        if (file == null || !file.isFile()) {
            return edges;
        }

        try {
            String content = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
            try (Scanner scanner = new Scanner(content)) {
                while (scanner.hasNextLine()) {
                    String line = scanner.nextLine();
                    if (line.startsWith(PREFIX_COMMENT) || line.isBlank()) {
                        continue;
                    }
                    Edge edge = parseRow(line);
                    if (edge != null) {
                        edges.add(edge);
                    }
                }
            }
        } catch (IOException e) {
            log.warn("[Walker] Unable to read learned blocked edges from {}: {}", file, e.getMessage());
        }

        return edges;
    }

    private static Edge parseRow(String line) {
        String[] fields = line.split(DELIM_COLUMN);
        if (fields.length < 2) {
            log.warn("[Walker] Skipping malformed learned-blocked-edge row (need Origin and Destination): {}", line);
            return null;
        }

        WorldPoint origin = parsePoint(fields[0]);
        WorldPoint destination = parsePoint(fields[1]);
        if (origin == null || destination == null) {
            log.warn("[Walker] Skipping learned-blocked-edge row with unparseable point(s): {}", line);
            return null;
        }

        boolean bidirectional = fields.length > 2 && Boolean.parseBoolean(fields[2].trim());
        String info = fields.length > 3 ? fields[3].trim() : "";
        int strikes = LEGACY_STRIKES;
        if (fields.length > 4 && !fields[4].trim().isEmpty()) {
            try {
                strikes = Integer.parseInt(fields[4].trim());
            } catch (NumberFormatException e) {
                log.warn("[Walker] Unparseable strike count, treating as confirmed: {}", line);
            }
        }
        long lastStrikeAtMs = 0L;
        if (fields.length > 5 && !fields[5].trim().isEmpty()) {
            try {
                lastStrikeAtMs = Long.parseLong(fields[5].trim());
            } catch (NumberFormatException e) {
                // timestamp is advisory; a missing one just widens the independence window
            }
        }
        return new Edge(origin, destination, bidirectional, info, strikes, lastStrikeAtMs);
    }

    private static WorldPoint parsePoint(String field) {
        if (field == null || field.isBlank()) {
            return null;
        }
        String[] parts = field.trim().split(" ");
        if (parts.length != 3) {
            return null;
        }
        try {
            return new WorldPoint(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    Integer.parseInt(parts[2]));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Appends one row, creating the parent directory and header on first write. Callers are responsible
     * for de-duplication (the {@link PathfinderConfig} in-memory set is the source of truth).
     */
    public static void append(File file, Edge edge) {
        if (file == null || edge == null || edge.origin == null || edge.destination == null) {
            return;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                Files.createDirectories(parent.toPath());
            }
            boolean newFile = !file.isFile() || file.length() == 0;
            StringBuilder sb = new StringBuilder();
            if (newFile) {
                sb.append(HEADER).append(System.lineSeparator());
            }
            sb.append(formatRow(edge)).append(System.lineSeparator());
            Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.warn("[Walker] Unable to append learned blocked edge to {}: {}", file, e.getMessage());
        }
    }

    /**
     * Rewrites the whole store (header + rows). Used when a strike count changes; {@link #append}
     * stays the cheap path for brand-new rows. The file is tiny — a walker learns a handful of edges
     * over its lifetime — so a full rewrite is simpler than in-place editing.
     */
    public static void save(File file, List<Edge> edges) {
        if (file == null || edges == null) {
            return;
        }
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.isDirectory()) {
                Files.createDirectories(parent.toPath());
            }
            StringBuilder sb = new StringBuilder(HEADER).append(System.lineSeparator());
            for (Edge edge : edges) {
                if (edge == null || edge.origin == null || edge.destination == null) {
                    continue;
                }
                sb.append(formatRow(edge)).append(System.lineSeparator());
            }
            Files.write(file.toPath(), sb.toString().getBytes(StandardCharsets.UTF_8),
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            log.warn("[Walker] Unable to save learned blocked edges to {}: {}", file, e.getMessage());
        }
    }

    private static String formatRow(Edge edge) {
        return formatPoint(edge.origin) + DELIM_COLUMN
                + formatPoint(edge.destination) + DELIM_COLUMN
                + edge.bidirectional + DELIM_COLUMN
                + (edge.info == null ? "" : edge.info) + DELIM_COLUMN
                + edge.strikes + DELIM_COLUMN
                + edge.lastStrikeAtMs;
    }

    private static String formatPoint(WorldPoint p) {
        return p.getX() + " " + p.getY() + " " + p.getPlane();
    }
}
