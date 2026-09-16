package net.runelite.client.plugins.microbot.testing.webwalker;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.testing.TestResult;
import net.runelite.client.plugins.microbot.testing.TestResultWriter;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2PlannerShadowStats;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@PluginDescriptor(
        name = "GE Lumbridge Teleport Harness",
        description = "Stress tests webwalking between Lumbridge Castle and the Grand Exchange",
        tags = {"microbot", "test", "webwalker", "teleport"},
        enabledByDefault = false,
        hidden = true
)
@Slf4j
public class GeLumbridgeTeleportHarnessPlugin extends Plugin {
    private static final String TEST_SCRIPT_PROPERTY = "microbot.test.script";
    private static final String SCRIPT_NAME = "GE Lumbridge Teleport Harness";
    private static final String ITERATIONS_PROPERTY = "microbot.test.geLumbridge.iterations";
    private static final String WALK_TIMEOUT_PROPERTY = "microbot.test.geLumbridge.walkTimeoutMs";
    private static final String UPSTREAM_PLANNER_SHADOW_PROPERTY =
            "microbot.test.geLumbridge.upstreamPlannerShadow";
    private static final int DEFAULT_ITERATIONS = 10;
    private static final int DEFAULT_WALK_TIMEOUT_MS = 300000;
    private static final int SHADOW_SETTLE_TIMEOUT_MS = 120000;
    private static final WorldPoint LUMBRIDGE_CASTLE = new WorldPoint(3222, 3218, 0);
    private static final WorldPoint GRAND_EXCHANGE = new WorldPoint(3164, 3486, 0);
    private static final WorldPoint VARROCK_TELEPORT = new WorldPoint(3213, 3424, 0);
    private static final int DEFAULT_TOLERANCE = 2;

    @Inject
    private EventBus eventBus;

    private ExecutorService executor;
    private volatile WorldPoint lastLocation;
    private volatile LegOutcome activeLeg;

    @Override
    protected void startUp() {
        if (!isHarnessTarget()) {
            return;
        }

        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                .setNameFormat("GeLumbridgeTeleportHarness-%d")
                .build());
        executor.submit(this::runHarness);
    }

    @Override
    protected void shutDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
        Rs2Walker.setTarget(null);
        activeLeg = null;
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!isHarnessTarget()) {
            return;
        }

        Player player = Microbot.getClient().getLocalPlayer();
        if (player == null) {
            return;
        }

        WorldPoint location = player.getWorldLocation();
        lastLocation = location;

        LegOutcome leg = activeLeg;
        if (leg != null && location != null) {
            observeLeg(leg, location);
        }
    }

    private void runHarness() {
        GeLumbridgeTeleportResult result = new GeLumbridgeTeleportResult(SCRIPT_NAME);
        result.iterations = intProperty(ITERATIONS_PROPERTY, DEFAULT_ITERATIONS);
        result.walkTimeoutMs = intProperty(WALK_TIMEOUT_PROPERTY, DEFAULT_WALK_TIMEOUT_MS);
        result.upstreamPlannerShadow = Boolean.parseBoolean(
                System.getProperty(UPSTREAM_PLANNER_SHADOW_PROPERTY, "false"));
        Rs2PlannerShadowStats shadowBaseline = result.upstreamPlannerShadow
                ? Rs2PathApi.getShadowStats()
                : null;

        int exitCode = 0;
        try {
            if (!sleepUntil(() -> safeLocation() != null, 60000)) {
                result.addError("Timed out waiting for local player location before starting GE/Lumbridge harness");
                result.complete("login_failure");
                writeAndExit(result, result.exitCode);
                return;
            }

            applyShortestPathOverrides(result.upstreamPlannerShadow);
            log.info("[GeLumbridgeTeleportHarness] Starting {} iteration(s)", result.iterations);

            LegOutcome setup = runLeg("setup", 0, "setup-to-lumbridge-castle",
                    safeLocation(), LUMBRIDGE_CASTLE, DEFAULT_TOLERANCE, result.walkTimeoutMs);
            result.legs.add(setup);
            result.addCheck(setup.name, setup.passed, setup.error);
            if (!setup.passed) {
                result.complete("completed");
                writeAndExit(result, result.exitCode);
                return;
            }

            for (int iteration = 1; iteration <= result.iterations; iteration++) {
                LegOutcome toGe = runLeg("lumbridge-to-ge", iteration, "lumbridge-to-grand-exchange",
                        LUMBRIDGE_CASTLE, GRAND_EXCHANGE, DEFAULT_TOLERANCE, result.walkTimeoutMs);
                result.legs.add(toGe);
                result.addCheck(toGe.id, toGe.passed, toGe.error);
                if (!toGe.passed) {
                    exitCode = 1;
                    break;
                }

                LegOutcome toLumbridge = runLeg("ge-to-lumbridge", iteration, "grand-exchange-to-lumbridge-castle",
                        GRAND_EXCHANGE, LUMBRIDGE_CASTLE, DEFAULT_TOLERANCE, result.walkTimeoutMs);
                result.legs.add(toLumbridge);
                result.addCheck(toLumbridge.id, toLumbridge.passed, toLumbridge.error);
                if (!toLumbridge.passed) {
                    exitCode = 1;
                    break;
                }
            }

            if (!captureShadowEvidence(result, shadowBaseline)) {
                exitCode = 1;
            }

            result.complete("completed");
            writeAndExit(result, exitCode == 0 ? result.exitCode : exitCode);
        } catch (Throwable t) {
            log.error("[GeLumbridgeTeleportHarness] Harness crashed", t);
            result.addError(t.getClass().getSimpleName() + ": " + t.getMessage());
            result.complete("crash");
            writeAndExit(result, result.exitCode);
        }
    }

    private LegOutcome runLeg(String kind, int iteration, String name, WorldPoint expectedStart,
                              WorldPoint destination, int tolerance, int timeoutMs) {
        LegOutcome outcome = new LegOutcome();
        outcome.kind = kind;
        outcome.iteration = iteration;
        outcome.id = iteration == 0 ? name : "iteration-" + iteration + "-" + name;
        outcome.name = outcome.id;
        outcome.expectedStart = format(expectedStart);
        outcome.destination = format(destination);
        outcome.startedAt = Instant.now().toString();
        outcome.initialLocation = format(safeLocation());
        outcome.initialDistanceToDestination = distance(safeLocation(), destination);
        outcome.tolerance = tolerance;

        log.info("[GeLumbridgeTeleportHarness] Running {} from {} to {}", outcome.id, safeLocation(), destination);
        activeLeg = outcome;
        long startedAt = System.currentTimeMillis();
        outcome.walkerState = walk(destination, tolerance, timeoutMs, outcome);
        outcome.durationMs = System.currentTimeMillis() - startedAt;
        activeLeg = null;

        WorldPoint end = safeLocation();
        outcome.finishedAt = Instant.now().toString();
        outcome.endLocation = format(end);
        outcome.distanceToDestination = distance(end, destination);
        outcome.passed = WalkerState.ARRIVED.name().equals(outcome.walkerState)
                && outcome.distanceToDestination <= tolerance
                && !outcome.retreatAfterVarrockTeleport;
        if (!outcome.passed) {
            outcome.error = "Leg failed: state=" + outcome.walkerState
                    + ", end=" + outcome.endLocation
                    + ", distanceToDestination=" + outcome.distanceToDestination
                    + ", retreatAfterVarrockTeleport=" + outcome.retreatAfterVarrockTeleport;
        }

        log.info("[GeLumbridgeTeleportHarness] {} finished: state={}, end={}, distance={}, duration={}ms, varrockTeleport={}, retreatAfterTeleport={}",
                outcome.id, outcome.walkerState, outcome.endLocation, outcome.distanceToDestination,
                outcome.durationMs, outcome.varrockTeleportDetected, outcome.retreatAfterVarrockTeleport);
        return outcome;
    }

    private void observeLeg(LegOutcome leg, WorldPoint location) {
        leg.sampleCount++;
        int distanceToDestination = distance(location, parsePoint(leg.destination));
        if (distanceToDestination > leg.previousDistanceToDestination) {
            leg.destinationRegressionTicks++;
            leg.maxConsecutiveDestinationRegressionTicks = Math.max(
                    leg.maxConsecutiveDestinationRegressionTicks,
                    leg.destinationRegressionTicks);
        } else {
            leg.destinationRegressionTicks = 0;
        }
        leg.previousDistanceToDestination = distanceToDestination;

        if (!"lumbridge-to-ge".equals(leg.kind)) {
            return;
        }

        if (!leg.varrockTeleportDetected && location.distanceTo2D(VARROCK_TELEPORT) <= 8) {
            leg.varrockTeleportDetected = true;
            leg.varrockTeleportLocation = format(location);
            leg.distanceToExpectedStartAtVarrockTeleport = location.distanceTo2D(LUMBRIDGE_CASTLE);
            leg.distanceToDestinationAtVarrockTeleport = location.distanceTo2D(GRAND_EXCHANGE);
            leg.previousDistanceToExpectedStart = leg.distanceToExpectedStartAtVarrockTeleport;
            leg.previousDistanceToGeAfterTeleport = leg.distanceToDestinationAtVarrockTeleport;
            leg.observations.add("Varrock teleport detected at " + format(location));
            return;
        }

        if (!leg.varrockTeleportDetected || leg.retreatAfterVarrockTeleport || leg.postTeleportTicks >= 12) {
            return;
        }

        leg.postTeleportTicks++;
        int distanceToExpectedStart = location.distanceTo2D(LUMBRIDGE_CASTLE);
        int distanceToGe = location.distanceTo2D(GRAND_EXCHANGE);
        boolean movedTowardOriginalStart = distanceToExpectedStart <= leg.previousDistanceToExpectedStart - 2;
        boolean movedAwayFromDestination = distanceToGe > leg.previousDistanceToGeAfterTeleport;
        if (movedTowardOriginalStart && movedAwayFromDestination) {
            leg.retreatAfterVarrockTeleport = true;
            leg.observations.add("Retreat after Varrock teleport at " + format(location)
                    + ": distanceToLumbridge=" + distanceToExpectedStart
                    + ", distanceToGe=" + distanceToGe);
        }
        leg.previousDistanceToExpectedStart = distanceToExpectedStart;
        leg.previousDistanceToGeAfterTeleport = distanceToGe;
    }

    private String walk(WorldPoint destination, int tolerance, int timeoutMs, LegOutcome outcome) {
        ExecutorService walkExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                .setNameFormat("GeLumbridgeTeleportLeg-%d")
                .build());
        Future<WalkerState> future = walkExecutor.submit(() -> Rs2Walker.walkWithState(destination, tolerance));

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS).name();
        } catch (TimeoutException e) {
            outcome.walkerThreadDump = dumpWalkerThread();
            future.cancel(true);
            Rs2Walker.setTarget(null);
            return "TIMEOUT";
        } catch (Exception e) {
            log.warn("[GeLumbridgeTeleportHarness] Webwalker leg failed for destination {}", destination, e);
            return "ERROR:" + e.getClass().getSimpleName();
        } finally {
            walkExecutor.shutdownNow();
        }
    }

    private boolean captureShadowEvidence(GeLumbridgeTeleportResult result,
                                          Rs2PlannerShadowStats baseline) {
        if (!result.upstreamPlannerShadow) {
            return true;
        }

        result.shadowSettled = sleepUntil(() -> Rs2PathApi.getShadowStats().getPending() == 0,
                SHADOW_SETTLE_TIMEOUT_MS);
        Rs2PlannerShadowStats stats = Rs2PathApi.getShadowStats();
        long submitted = stats.getSubmitted() - baseline.getSubmitted();
        long completed = stats.getCompleted() - baseline.getCompleted();
        long discarded = stats.getDiscarded() - baseline.getDiscarded();
        long pending = submitted - completed - discarded;
        long divergences = stats.getDivergences() - baseline.getDivergences();
        long failures = stats.getFailures() - baseline.getFailures();
        boolean passed = result.shadowSettled
                && submitted > 0
                && completed > 0
                && pending == 0
                && divergences == 0
                && failures == 0;
        if (!passed) {
            result.shadowError = "Planner shadow evidence failed: settled=" + result.shadowSettled
                    + ", submitted=" + submitted
                    + ", completed=" + completed
                    + ", pending=" + pending
                    + ", divergences=" + divergences
                    + ", failures=" + failures;
        }
        result.addCheck("upstream planner shadow", passed, result.shadowError);
        return passed;
    }

    private void applyShortestPathOverrides(boolean upstreamPlannerShadow) {
        Map<String, Object> config = new HashMap<>();
        config.put("useTeleportationSpells", true);
        config.put("plannerSelectionMode", upstreamPlannerShadow ? "SHADOW" : "LOCAL");

        Map<String, Object> data = new HashMap<>();
        data.put("config", config);

        eventBus.post(new PluginMessage("shortestpath", "path", data));
        log.info("[GeLumbridgeTeleportHarness] Applied shortest path overrides: {}", config);
    }

    private WorldPoint safeLocation() {
        return lastLocation;
    }

    private static boolean isHarnessTarget() {
        return "true".equals(System.getProperty("microbot.test.mode"))
                && System.getProperty(TEST_SCRIPT_PROPERTY, "").contains(SCRIPT_NAME);
    }

    private static int intProperty(String property, int defaultValue) {
        String value = System.getProperty(property);
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private static int distance(WorldPoint from, WorldPoint to) {
        if (from == null || to == null) {
            return Integer.MAX_VALUE;
        }
        return from.distanceTo(to);
    }

    private static WorldPoint parsePoint(String point) {
        if (point == null || point.isBlank()) {
            return null;
        }
        String[] parts = point.split(",");
        if (parts.length != 3) {
            return null;
        }
        return new WorldPoint(Integer.parseInt(parts[0]), Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    private static String format(WorldPoint point) {
        if (point == null) {
            return null;
        }
        return point.getX() + "," + point.getY() + "," + point.getPlane();
    }

    private static void writeAndExit(GeLumbridgeTeleportResult result, int exitCode) {
        TestResultWriter.write(result);
        System.exit(exitCode);
    }

    public static class GeLumbridgeTeleportResult extends TestResult {
        public int iterations;
        public int walkTimeoutMs;
        public boolean upstreamPlannerShadow;
        public boolean shadowSettled;
        public String shadowError;
        public List<LegOutcome> legs = new ArrayList<>();

        public GeLumbridgeTeleportResult(String script) {
            super(script);
        }
    }

    public static class LegOutcome {
        public String id;
        public String kind;
        public int iteration;
        public String name;
        public String expectedStart;
        public String destination;
        public int tolerance;
        public String startedAt;
        public String finishedAt;
        public String initialLocation;
        public int initialDistanceToDestination;
        public String walkerState;
        public boolean passed;
        public String error;
        public long durationMs;
        public String endLocation;
        public int distanceToDestination;
        public int sampleCount;
        public int previousDistanceToDestination = Integer.MAX_VALUE;
        public int destinationRegressionTicks;
        public int maxConsecutiveDestinationRegressionTicks;
        public boolean varrockTeleportDetected;
        public String varrockTeleportLocation;
        public int distanceToExpectedStartAtVarrockTeleport;
        public int distanceToDestinationAtVarrockTeleport;
        public int postTeleportTicks;
        public int previousDistanceToExpectedStart = Integer.MAX_VALUE;
        public int previousDistanceToGeAfterTeleport = Integer.MAX_VALUE;
        public boolean retreatAfterVarrockTeleport;
        public String walkerThreadDump;
        public List<String> observations = new ArrayList<>();
    }

    private static String dumpWalkerThread() {
        return Thread.getAllStackTraces().entrySet().stream()
                .filter(entry -> entry.getKey().getName().startsWith("GeLumbridgeTeleportLeg-"))
                .findFirst()
                .map(entry -> {
                    StringBuilder dump = new StringBuilder(entry.getKey().getName())
                            .append(" state=")
                            .append(entry.getKey().getState());
                    for (StackTraceElement frame : entry.getValue()) {
                        dump.append('\n').append("  at ").append(frame);
                    }
                    return dump.toString();
                })
                .orElse("No GeLumbridgeTeleportLeg thread found");
    }
}
