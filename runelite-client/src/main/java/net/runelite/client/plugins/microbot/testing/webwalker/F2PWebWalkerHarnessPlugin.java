package net.runelite.client.plugins.microbot.testing.webwalker;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Player;
import net.runelite.api.WorldType;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.GameTick;
import net.runelite.client.eventbus.EventBus;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.PluginMessage;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.shortestpath.PlannerSelectionMode;
import net.runelite.client.plugins.microbot.agentserver.handler.WalkerShadowHandler;
import net.runelite.client.plugins.microbot.testing.TestResult;
import net.runelite.client.plugins.microbot.testing.TestResultWriter;
import net.runelite.client.plugins.microbot.util.walker.Rs2PathApi;
import net.runelite.client.plugins.microbot.util.walker.Rs2PlannerShadowContext;
import net.runelite.client.plugins.microbot.util.walker.Rs2PlannerShadowCoverageStats;
import net.runelite.client.plugins.microbot.util.walker.Rs2PlannerShadowStats;
import net.runelite.client.plugins.microbot.util.walker.Rs2TransportExecutor;
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
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

@PluginDescriptor(
        name = "F2P Web Walker Harness",
        description = "Runs F2P-only in-game webwalker regression routes in test mode",
        tags = {"microbot", "test", "webwalker", "f2p"},
        hidden = true
)
@Slf4j
public class F2PWebWalkerHarnessPlugin extends Plugin {
    private static final String ROUTE_FILTER_PROPERTY = "microbot.webwalker.case";
    private static final String TEST_ROUTE_FILTER_PROPERTY = "microbot.test.webwalker.case";
    private static final String STOP_ON_FAILURE_PROPERTY = "microbot.webwalker.stopOnFailure";
    private static final String TEST_STOP_ON_FAILURE_PROPERTY = "microbot.test.webwalker.stopOnFailure";
    private static final String WALK_TIMEOUT_PROPERTY = "microbot.webwalker.walkTimeoutMs";
    private static final String TEST_WALK_TIMEOUT_PROPERTY = "microbot.test.webwalker.walkTimeoutMs";
    private static final String USE_TELEPORTATION_SPELLS_PROPERTY = "microbot.webwalker.useTeleportationSpells";
    private static final String TEST_USE_TELEPORTATION_SPELLS_PROPERTY = "microbot.test.webwalker.useTeleportationSpells";
    private static final String UPSTREAM_PLANNER_SHADOW_PROPERTY = "microbot.webwalker.upstreamPlannerShadow";
    private static final String TEST_UPSTREAM_PLANNER_SHADOW_PROPERTY = "microbot.test.webwalker.upstreamPlannerShadow";
    private static final String PLANNER_MODE_PROPERTY = "microbot.webwalker.plannerMode";
    private static final String TEST_PLANNER_MODE_PROPERTY = "microbot.test.webwalker.plannerMode";
    private static final String EXPECT_LOCAL_FALLBACK_PROPERTY = "microbot.webwalker.expectLocalFallback";
    private static final String TEST_EXPECT_LOCAL_FALLBACK_PROPERTY = "microbot.test.webwalker.expectLocalFallback";
    private static final String TEST_SCRIPT_PROPERTY = "microbot.test.script";
    private static final String SCRIPT_NAME = "F2P Web Walker Harness";
    private static final int DEFAULT_WALK_TIMEOUT_MS = 240000;
    private static final int SHADOW_SETTLE_TIMEOUT_MS = 120000;
    private static final int RECOVERY_REPLAN_ACK_TIMEOUT_MS = 30000;

    @Inject
    private EventBus eventBus;

    private ExecutorService executor;
    private volatile WorldPoint lastLocation;

    @Override
    protected void startUp() {
        if (!isHarnessTarget()) {
            return;
        }

        executor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                .setNameFormat("F2PWebWalkerHarness-%d")
                .build());
        executor.submit(this::runHarness);
    }

    @Override
    protected void shutDown() {
        if (executor != null) {
            executor.shutdownNow();
        }
    }

    @Subscribe
    public void onGameTick(GameTick event) {
        if (!isHarnessTarget()) {
            return;
        }

        Player player = Microbot.getClient().getLocalPlayer();
        if (player != null) {
            lastLocation = player.getWorldLocation();
        }
    }

    private void runHarness() {
        WebWalkerTestResult result = new WebWalkerTestResult(SCRIPT_NAME);
        result.routeFilter = property(TEST_ROUTE_FILTER_PROPERTY, ROUTE_FILTER_PROPERTY, "all");
        result.stopOnFailure = Boolean.parseBoolean(property(TEST_STOP_ON_FAILURE_PROPERTY, STOP_ON_FAILURE_PROPERTY, "true"));
        result.walkTimeoutMs = intProperty(TEST_WALK_TIMEOUT_PROPERTY, WALK_TIMEOUT_PROPERTY, DEFAULT_WALK_TIMEOUT_MS);
        boolean legacyShadow = Boolean.parseBoolean(property(
                TEST_UPSTREAM_PLANNER_SHADOW_PROPERTY, UPSTREAM_PLANNER_SHADOW_PROPERTY, "false"));
        result.plannerMode = plannerModeProperty(legacyShadow).name();
        result.upstreamPlannerShadow = plannerMode(result.plannerMode).comparisonEnabled();
        result.expectLocalFallback = Boolean.parseBoolean(property(
                TEST_EXPECT_LOCAL_FALLBACK_PROPERTY, EXPECT_LOCAL_FALLBACK_PROPERTY, "false"));

        int exitCode = 0;
        try {
            List<F2PWebWalkerRoute> routes = F2PWebWalkerRoute.selected(result.routeFilter);
            result.selectedRoutes = routes.stream().map(route -> route.id).collect(Collectors.toList());

            if (!sleepUntil(() -> safeLocation() != null, 60000)) {
                result.addError("Timed out waiting for local player location before starting webwalker routes");
                result.complete("login_failure");
                writeAndExit(result, result.exitCode);
                return;
            }

            log.info("[F2PWebWalkerHarness] Starting {} route(s): {}", routes.size(), result.selectedRoutes);
            for (F2PWebWalkerRoute route : routes) {
                if (Thread.currentThread().isInterrupted()) {
                    result.addError("Harness interrupted before route " + route.id);
                    exitCode = 1;
                    break;
                }

                applyShortestPathOverrides(route, result.plannerMode);
                long expectedExecutorBefore = shadowExecutorCompleted(route.expectedShadowExecutor);
                long activeReplansBefore = shadowCoverageCompleted(
                        Rs2PlannerShadowContext.Coverage.ACTIVE_REPLAN);
                long recoveryReplansBefore = shadowCoverageCompleted(
                        Rs2PlannerShadowContext.Coverage.RECOVERY_REPLAN);
                long recoveryArrivalsBefore = shadowRecoveryArrivals();
                long bankRouteFromBankBefore = shadowCoverageCompleted(
                        Rs2PlannerShadowContext.Coverage.BANK_ROUTE_FROM_BANK);
                long bankRouteItemGatedBefore = shadowCoverageCompleted(
                        Rs2PlannerShadowContext.Coverage.BANK_ROUTE_FROM_BANK_SELECTS_ITEM_GATED_TRANSPORT);
                runBankRouteComparisons(route);
                RouteOutcome outcome = runRoute(route, result.walkTimeoutMs);
                result.routes.add(outcome);

                result.addCheck(route.id + " setup", outcome.setupPassed, outcome.setupError);
                result.addCheck(route.id + " route", outcome.passed, outcome.error);

                boolean executorEvidencePassed = verifyExpectedShadowExecutor(
                        route, outcome, expectedExecutorBefore, result.upstreamPlannerShadow);
                if (route.expectedShadowExecutor != null) {
                    result.addCheck(route.id + " shadow executor", executorEvidencePassed,
                            outcome.shadowExecutorError);
                }

                boolean activeReplanEvidencePassed = verifyExpectedActiveReplans(
                        route, outcome, activeReplansBefore, result.upstreamPlannerShadow);
                if (route.minimumExpectedActiveReplanComparisons > 0) {
                    result.addCheck(route.id + " active replans", activeReplanEvidencePassed,
                            outcome.activeReplanError);
                }

                boolean recoveryEvidencePassed = verifyExpectedRecoveryReplans(
                        route, outcome, recoveryReplansBefore, recoveryArrivalsBefore,
                        result.upstreamPlannerShadow);
                if (route.minimumExpectedRecoveryReplanComparisons > 0
                        || route.minimumExpectedRecoveryArrivals > 0) {
                    result.addCheck(route.id + " recovery replans", recoveryEvidencePassed,
                            outcome.recoveryReplanError);
                }

                boolean bankRouteEvidencePassed = verifyExpectedBankRoutes(
                        route, outcome, bankRouteFromBankBefore, bankRouteItemGatedBefore,
                        result.upstreamPlannerShadow);
                if (route.minimumExpectedBankRouteFromBankComparisons > 0
                        || route.minimumExpectedBankRouteFromBankItemGatedComparisons > 0) {
                    result.addCheck(route.id + " bank route comparisons", bankRouteEvidencePassed,
                            outcome.bankRouteComparisonError);
                }

                if (!outcome.passed || !executorEvidencePassed || !activeReplanEvidencePassed
                        || !recoveryEvidencePassed || !bankRouteEvidencePassed) {
                    exitCode = 1;
                    if (result.stopOnFailure) {
                        log.warn("[F2PWebWalkerHarness] Stopping on first failed route: {}", route.id);
                        break;
                    }
                }
            }

            if (!captureShadowEvidence(result)) {
                exitCode = 1;
            }

            result.complete("completed");
            writeAndExit(result, exitCode == 0 ? result.exitCode : exitCode);
        } catch (Throwable t) {
            log.error("[F2PWebWalkerHarness] Harness crashed", t);
            result.addError(t.getClass().getSimpleName() + ": " + t.getMessage());
            result.complete("crash");
            writeAndExit(result, result.exitCode);
        }
    }

    private RouteOutcome runRoute(F2PWebWalkerRoute route, int walkTimeoutMs) {
        RouteOutcome outcome = new RouteOutcome();
        outcome.id = route.id;
        outcome.name = route.name;
        outcome.destination = format(route.destination);
        outcome.startTolerance = route.startTolerance;
        outcome.destinationTolerance = route.destinationTolerance;
        outcome.repetitions = route.repetitions;
        outcome.currentLocationStart = route.currentLocationStart;
        outcome.requireF2PWorld = route.requireF2PWorld;
        outcome.requireMembersWorld = route.requireMembersWorld;
        outcome.forceNoAgilityShortcuts = route.forceNoAgilityShortcuts;
        outcome.forceNoTeleports = route.forceNoTeleports;
        outcome.forceCanoes = route.forceCanoes;
        outcome.forceShips = route.forceShips;
        outcome.expectedShadowExecutor = route.expectedShadowExecutor == null
                ? null : route.expectedShadowExecutor.name();
        outcome.minimumExpectedShadowExecutions = route.minimumExpectedShadowExecutions;
        outcome.forcedActiveReplans = route.forcedActiveReplans;
        outcome.minimumExpectedActiveReplanComparisons = route.minimumExpectedActiveReplanComparisons;
        outcome.forcedRecoveryReplans = route.forcedRecoveryReplans;
        outcome.forcedSetupRecoveryReplans = route.forcedSetupRecoveryReplans;
        outcome.minimumExpectedRecoveryReplanComparisons =
                route.minimumExpectedRecoveryReplanComparisons;
        outcome.minimumExpectedRecoveryArrivals = route.minimumExpectedRecoveryArrivals;
        outcome.forcedBankRouteComparisons = route.forcedBankRouteComparisons;
        outcome.minimumExpectedBankRouteFromBankComparisons =
                route.minimumExpectedBankRouteFromBankComparisons;
        outcome.minimumExpectedBankRouteFromBankItemGatedComparisons =
                route.minimumExpectedBankRouteFromBankItemGatedComparisons;
        outcome.startedAt = Instant.now().toString();
        outcome.setupPassed = true;

        log.info("[F2PWebWalkerHarness] Running {}: {} -> {}", route.id, route.start, route.destination);

        WorldPoint current = safeLocation();
        outcome.initialLocation = format(current);
        if (current == null) {
            outcome.setupError = "Player location unavailable before setup";
            outcome.error = outcome.setupError;
            return outcome;
        }

        boolean membersWorld = Microbot.getClient().getWorldType().contains(WorldType.MEMBERS);
        outcome.membersWorld = membersWorld;
        if (route.requireF2PWorld && membersWorld) {
            outcome.setupPassed = false;
            outcome.setupError = "Route " + route.id + " requires a F2P world, but the client is on a members world";
            outcome.error = outcome.setupError;
            outcome.finishedAt = Instant.now().toString();
            return outcome;
        }
        if (route.requireMembersWorld && !membersWorld) {
            outcome.setupPassed = false;
            outcome.setupError = "Route " + route.id
                    + " requires a members world, but the client is on a free world";
            outcome.error = outcome.setupError;
            outcome.finishedAt = Instant.now().toString();
            return outcome;
        }

        WorldPoint start = route.currentLocationStart ? current : route.start;
        outcome.start = format(start);
        if (start == null) {
            outcome.setupPassed = false;
            outcome.setupError = "Start location unavailable for " + route.id;
            outcome.error = outcome.setupError;
            outcome.finishedAt = Instant.now().toString();
            return outcome;
        }

        for (int i = 1; i <= route.repetitions; i++) {
            RouteAttemptOutcome attempt = runAttempt(route, start, i, walkTimeoutMs);
            outcome.attempts.add(attempt);
            outcome.initialDistanceToStart = i == 1 ? attempt.initialDistanceToStart : outcome.initialDistanceToStart;
            outcome.setupState = attempt.setupState;
            outcome.setupDurationMs += attempt.setupDurationMs;
            outcome.routeStartLocation = attempt.routeStartLocation;
            outcome.distanceToStartAfterSetup = attempt.distanceToStartAfterSetup;
            outcome.walkerState = attempt.walkerState;
            outcome.walkDurationMs += attempt.walkDurationMs;
            outcome.endLocation = attempt.endLocation;
            outcome.distanceToDestination = attempt.distanceToDestination;

            if (!attempt.setupPassed) {
                outcome.setupPassed = false;
                outcome.setupError = attempt.setupError;
                outcome.error = attempt.setupError;
                break;
            }

            if (!attempt.passed) {
                outcome.error = attempt.error;
                break;
            }

            outcome.successfulAttempts++;
        }

        outcome.passed = outcome.setupPassed && outcome.successfulAttempts == route.repetitions;
        outcome.finishedAt = Instant.now().toString();
        log.info("[F2PWebWalkerHarness] {} finished: passed={}, attempts={}/{}, state={}, end={}, distance={}, duration={}ms",
                route.id, outcome.passed, outcome.successfulAttempts, route.repetitions, outcome.walkerState,
                outcome.endLocation, outcome.distanceToDestination, outcome.walkDurationMs);
        return outcome;
    }

    private RouteAttemptOutcome runAttempt(F2PWebWalkerRoute route, WorldPoint start, int attemptNumber, int walkTimeoutMs) {
        RouteAttemptOutcome attempt = new RouteAttemptOutcome();
        attempt.attempt = attemptNumber;
        attempt.start = format(start);
        attempt.destination = format(route.destination);
        attempt.startedAt = Instant.now().toString();

        WorldPoint current = safeLocation();
        attempt.initialLocation = format(current);
        attempt.initialDistanceToStart = distance(current, start);
        if (attempt.initialDistanceToStart > route.startTolerance) {
            long setupStart = System.currentTimeMillis();
            attempt.setupState = walk(start, route.startTolerance, walkTimeoutMs, 0,
                    route.forcedSetupRecoveryReplans);
            attempt.setupDurationMs = System.currentTimeMillis() - setupStart;
        } else {
            attempt.setupState = WalkerState.ARRIVED.name();
            attempt.setupDurationMs = 0;
        }

        WorldPoint beforeRoute = safeLocation();
        attempt.routeStartLocation = format(beforeRoute);
        attempt.distanceToStartAfterSetup = distance(beforeRoute, start);
        attempt.setupPassed = WalkerState.ARRIVED.name().equals(attempt.setupState)
                && attempt.distanceToStartAfterSetup <= route.startTolerance;
        if (!attempt.setupPassed) {
            attempt.setupError = "Setup webwalk failed for " + route.id + " attempt " + attemptNumber
                    + ": state=" + attempt.setupState
                    + ", location=" + attempt.routeStartLocation
                    + ", distanceToStart=" + attempt.distanceToStartAfterSetup;
            attempt.error = attempt.setupError;
            attempt.finishedAt = Instant.now().toString();
            return attempt;
        }

        long walkStart = System.currentTimeMillis();
        attempt.walkerState = walk(route.destination, route.destinationTolerance, walkTimeoutMs,
                route.forcedActiveReplans, route.forcedRecoveryReplans);
        attempt.walkDurationMs = System.currentTimeMillis() - walkStart;

        WorldPoint end = safeLocation();
        attempt.endLocation = format(end);
        attempt.distanceToDestination = distance(end, route.destination);
        attempt.passed = WalkerState.ARRIVED.name().equals(attempt.walkerState)
                && attempt.distanceToDestination <= route.destinationTolerance;
        if (!attempt.passed) {
            attempt.error = "Route webwalk failed for " + route.id + " attempt " + attemptNumber
                    + ": state=" + attempt.walkerState
                    + ", location=" + attempt.endLocation
                    + ", distanceToDestination=" + attempt.distanceToDestination;
        }

        attempt.finishedAt = Instant.now().toString();
        return attempt;
    }

    private String walk(
            WorldPoint destination,
            int tolerance,
            int timeoutMs,
            int forcedActiveReplans,
            int forcedRecoveryReplans
    ) {
        ExecutorService walkExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                .setNameFormat("F2PWebWalkerLeg-%d")
                .build());
        Future<WalkerState> future = walkExecutor.submit(() -> Rs2Walker.walkWithState(destination, tolerance));
        ExecutorService activeReplanExecutor = null;
        if (forcedActiveReplans > 0) {
            activeReplanExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                    .setNameFormat("F2PWebWalkerReplan-%d")
                    .build());
            activeReplanExecutor.submit(() -> forceActiveReplans(
                    future, destination, tolerance, forcedActiveReplans));
        }
        ExecutorService recoveryReplanExecutor = null;
        if (forcedRecoveryReplans > 0) {
            recoveryReplanExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder()
                    .setNameFormat("F2PWebWalkerRecovery-%d")
                    .build());
            recoveryReplanExecutor.submit(() -> forceRecoveryReplans(
                    future, destination, tolerance, forcedRecoveryReplans));
        }

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS).name();
        } catch (TimeoutException e) {
            future.cancel(true);
            Rs2Walker.setTarget(null);
            return "TIMEOUT";
        } catch (Exception e) {
            log.warn("[F2PWebWalkerHarness] Webwalker leg failed for destination {}", destination, e);
            return "ERROR:" + e.getClass().getSimpleName();
        } finally {
            if (activeReplanExecutor != null) {
                activeReplanExecutor.shutdownNow();
            }
            if (recoveryReplanExecutor != null) {
                recoveryReplanExecutor.shutdownNow();
            }
            walkExecutor.shutdownNow();
        }
    }

    private void forceActiveReplans(
            Future<WalkerState> walkFuture,
            WorldPoint destination,
            int tolerance,
            int count
    ) {
        WorldPoint lastReplanLocation = null;
        for (int index = 1; index <= count && !Thread.currentThread().isInterrupted(); index++) {
            WorldPoint previous = lastReplanLocation;
            boolean ready = sleepUntil(() -> {
                WorldPoint current = safeLocation();
                return !walkFuture.isDone()
                        && current != null
                        && current.distanceTo2D(destination) > tolerance + 10
                        && Rs2PathApi.getActiveRouteStatus().isReady()
                        && (previous == null || current.distanceTo2D(previous) >= 4);
            }, 30000);
            if (!ready || walkFuture.isDone()) {
                log.warn("[F2PWebWalkerHarness] Unable to trigger active replan {}/{} before route ended",
                        index, count);
                return;
            }

            long generation = Rs2PathApi.getActiveRouteStatus().getGeneration();
            lastReplanLocation = safeLocation();
            Rs2Walker.recalculatePath();
            log.info("[F2PWebWalkerHarness] Triggered active replan {}/{} at generation {}",
                    index, count, generation);
            sleepUntil(() -> walkFuture.isDone()
                    || Rs2PathApi.getActiveRouteStatus().getGeneration() > generation, 10000);
        }
    }

    private void forceRecoveryReplans(
            Future<WalkerState> walkFuture,
            WorldPoint destination,
            int tolerance,
            int count
    ) {
        WorldPoint lastReplanLocation = safeLocation();
        for (int index = 1; index <= count && !Thread.currentThread().isInterrupted(); index++) {
            WorldPoint previous = lastReplanLocation;
            boolean ready = sleepUntil(() -> {
                WorldPoint current = safeLocation();
                return !walkFuture.isDone()
                        && current != null
                        && current.distanceTo2D(destination) > tolerance + 10
                        && Rs2PathApi.getActiveRouteStatus().isReady()
                        && (previous == null || current.distanceTo2D(previous) >= 4);
            }, 30000);
            if (!ready || walkFuture.isDone()) {
                log.warn("[F2PWebWalkerHarness] Unable to trigger recovery replan {}/{} before route ended",
                        index, count);
                return;
            }

            long generation = Rs2PathApi.getActiveRouteStatus().getGeneration();
            lastReplanLocation = safeLocation();
            if (!Rs2Walker.requestRecoveryReplanForTest()) {
                log.warn("[F2PWebWalkerHarness] Recovery replan {}/{} was rejected at generation {}",
                        index, count, generation);
                return;
            }
            log.info("[F2PWebWalkerHarness] Queued recovery replan {}/{} at generation {}",
                    index, count, generation);
            boolean consumed = sleepUntil(() -> walkFuture.isDone()
                    || Rs2PathApi.getActiveRouteStatus().getGeneration() > generation,
                    RECOVERY_REPLAN_ACK_TIMEOUT_MS);
            if (!consumed || (walkFuture.isDone()
                    && Rs2PathApi.getActiveRouteStatus().getGeneration() <= generation)) {
                log.warn("[F2PWebWalkerHarness] Recovery replan {}/{} was not acknowledged",
                        index, count);
                return;
            }
            // Measure the next progress interval from the point where this replan was consumed,
            // not from where it was queued while the previous route was still moving.
            lastReplanLocation = safeLocation();
        }
    }

    private WorldPoint safeLocation() {
        return lastLocation;
    }

    private boolean captureShadowEvidence(WebWalkerTestResult result) {
        if (!result.upstreamPlannerShadow) {
            return true;
        }

        result.shadowSettled = sleepUntil(() -> Rs2PathApi.getShadowStats().getPending() == 0,
                SHADOW_SETTLE_TIMEOUT_MS);
        Rs2PlannerShadowStats stats = Rs2PathApi.getShadowStats();
        result.shadowEvidence = WalkerShadowHandler.snapshot();

        boolean expectedFallbackObserved = !result.expectLocalFallback
                || (stats.getFailures() > 0
                    && stats.getLocalFallbackFailures() > 0
                    && stats.getUpstreamCanarySelections() == 0);
        boolean unexpectedFailure = !result.expectLocalFallback && stats.getFailures() != 0;
        boolean canarySelectionObserved = plannerMode(result.plannerMode).f2pCanaryEnabled()
                && !result.expectLocalFallback
                ? stats.getUpstreamCanarySelections() > 0
                : true;
        boolean passed = result.shadowSettled
                && stats.getSubmitted() > 0
                && stats.getCompleted() > 0
                && stats.getPending() == 0
                && stats.getDivergences() == 0
                && !unexpectedFailure
                && expectedFallbackObserved
                && canarySelectionObserved;
        if (!passed) {
            result.shadowError = "Planner shadow evidence failed: settled=" + result.shadowSettled
                    + ", submitted=" + stats.getSubmitted()
                    + ", completed=" + stats.getCompleted()
                    + ", pending=" + stats.getPending()
                    + ", divergences=" + stats.getDivergences()
                    + ", failures=" + stats.getFailures()
                    + ", upstreamCanarySelections=" + stats.getUpstreamCanarySelections()
                    + ", localFallbackFailures=" + stats.getLocalFallbackFailures()
                    + ", expectLocalFallback=" + result.expectLocalFallback;
        }
        result.addCheck("planner comparison and selection evidence", passed, result.shadowError);
        return passed;
    }

    private boolean verifyExpectedShadowExecutor(
            F2PWebWalkerRoute route,
            RouteOutcome outcome,
            long before,
            boolean upstreamPlannerShadow
    ) {
        if (route.expectedShadowExecutor == null) {
            return true;
        }
        outcome.shadowExecutorSettled = upstreamPlannerShadow
                && sleepUntil(() -> Rs2PathApi.getShadowStats().getPending() == 0,
                SHADOW_SETTLE_TIMEOUT_MS);
        long after = shadowExecutorCompleted(route.expectedShadowExecutor);
        outcome.observedShadowExecutions = Math.max(0L, after - before);
        boolean passed = upstreamPlannerShadow
                && outcome.shadowExecutorSettled
                && outcome.observedShadowExecutions >= route.minimumExpectedShadowExecutions;
        if (!passed) {
            outcome.shadowExecutorError = "Expected at least " + route.minimumExpectedShadowExecutions
                    + " completed " + route.expectedShadowExecutor + " shadow comparison(s), observed "
                    + outcome.observedShadowExecutions + "; shadowEnabled=" + upstreamPlannerShadow
                    + ", settled=" + outcome.shadowExecutorSettled;
        }
        return passed;
    }

    private static long shadowExecutorCompleted(Rs2TransportExecutor executor) {
        if (executor == null) {
            return 0L;
        }
        return Rs2PathApi.getShadowStats().getTransportExecutors().get(executor).getCompleted();
    }

    private boolean verifyExpectedActiveReplans(
            F2PWebWalkerRoute route,
            RouteOutcome outcome,
            long before,
            boolean upstreamPlannerShadow
    ) {
        if (route.minimumExpectedActiveReplanComparisons == 0) {
            return true;
        }
        outcome.activeReplansSettled = upstreamPlannerShadow
                && sleepUntil(() -> Rs2PathApi.getShadowStats().getPending() == 0,
                SHADOW_SETTLE_TIMEOUT_MS);
        long after = shadowCoverageCompleted(Rs2PlannerShadowContext.Coverage.ACTIVE_REPLAN);
        outcome.observedActiveReplanComparisons = Math.max(0L, after - before);
        boolean passed = upstreamPlannerShadow
                && outcome.activeReplansSettled
                && outcome.observedActiveReplanComparisons
                >= route.minimumExpectedActiveReplanComparisons;
        if (!passed) {
            outcome.activeReplanError = "Expected at least "
                    + route.minimumExpectedActiveReplanComparisons
                    + " completed ACTIVE_REPLAN shadow comparison(s), observed "
                    + outcome.observedActiveReplanComparisons
                    + "; shadowEnabled=" + upstreamPlannerShadow
                    + ", settled=" + outcome.activeReplansSettled;
        }
        return passed;
    }

    private static long shadowCoverageCompleted(Rs2PlannerShadowContext.Coverage coverage) {
        Rs2PlannerShadowCoverageStats stats = Rs2PathApi.getShadowStats().getCoverage().get(coverage);
        return stats == null ? 0L : stats.getCompleted();
    }

    private boolean verifyExpectedRecoveryReplans(
            F2PWebWalkerRoute route,
            RouteOutcome outcome,
            long replansBefore,
            long arrivalsBefore,
            boolean upstreamPlannerShadow
    ) {
        if (route.minimumExpectedRecoveryReplanComparisons == 0
                && route.minimumExpectedRecoveryArrivals == 0) {
            return true;
        }
        outcome.recoveryReplansSettled = upstreamPlannerShadow
                && sleepUntil(() -> Rs2PathApi.getShadowStats().getPending() == 0,
                SHADOW_SETTLE_TIMEOUT_MS);
        outcome.observedRecoveryReplanComparisons = Math.max(0L,
                shadowCoverageCompleted(Rs2PlannerShadowContext.Coverage.RECOVERY_REPLAN)
                        - replansBefore);
        outcome.observedRecoveryArrivals = Math.max(0L,
                shadowRecoveryArrivals() - arrivalsBefore);
        boolean passed = upstreamPlannerShadow
                && outcome.recoveryReplansSettled
                && outcome.observedRecoveryReplanComparisons
                >= route.minimumExpectedRecoveryReplanComparisons
                && outcome.observedRecoveryArrivals >= route.minimumExpectedRecoveryArrivals;
        if (!passed) {
            outcome.recoveryReplanError = "Expected at least "
                    + route.minimumExpectedRecoveryReplanComparisons
                    + " completed RECOVERY_REPLAN comparison(s) and "
                    + route.minimumExpectedRecoveryArrivals
                    + " recovered arrival(s), observed "
                    + outcome.observedRecoveryReplanComparisons + " and "
                    + outcome.observedRecoveryArrivals
                    + "; shadowEnabled=" + upstreamPlannerShadow
                    + ", settled=" + outcome.recoveryReplansSettled;
        }
        return passed;
    }

    private static long shadowRecoveryArrivals() {
        return Rs2PathApi.getShadowStats().getExecution().getRecoveryArrived();
    }

    private void runBankRouteComparisons(F2PWebWalkerRoute route) {
        for (int index = 1; index <= route.forcedBankRouteComparisons; index++) {
            Rs2Walker.compareRoutes(route.start, route.destination);
            boolean settled = sleepUntil(() -> Rs2PathApi.getShadowStats().getPending() == 0,
                    SHADOW_SETTLE_TIMEOUT_MS);
            if (!settled) {
                log.warn("[F2PWebWalkerHarness] Bank route comparison {}/{} did not settle",
                        index, route.forcedBankRouteComparisons);
                return;
            }
            log.info("[F2PWebWalkerHarness] Completed bank route comparison {}/{} for {}",
                    index, route.forcedBankRouteComparisons, route.id);
        }
    }

    private boolean verifyExpectedBankRoutes(
            F2PWebWalkerRoute route,
            RouteOutcome outcome,
            long beforeFromBank,
            long beforeItemGated,
            boolean upstreamPlannerShadow
    ) {
        if (route.minimumExpectedBankRouteFromBankComparisons == 0
                && route.minimumExpectedBankRouteFromBankItemGatedComparisons == 0) {
            return true;
        }
        outcome.bankRouteComparisonsSettled = upstreamPlannerShadow
                && sleepUntil(() -> Rs2PathApi.getShadowStats().getPending() == 0,
                SHADOW_SETTLE_TIMEOUT_MS);
        outcome.observedBankRouteFromBankComparisons = Math.max(0L,
                shadowCoverageCompleted(Rs2PlannerShadowContext.Coverage.BANK_ROUTE_FROM_BANK)
                        - beforeFromBank);
        outcome.observedBankRouteFromBankItemGatedComparisons = Math.max(0L,
                shadowCoverageCompleted(
                        Rs2PlannerShadowContext.Coverage.BANK_ROUTE_FROM_BANK_SELECTS_ITEM_GATED_TRANSPORT)
                        - beforeItemGated);
        boolean passed = upstreamPlannerShadow
                && outcome.bankRouteComparisonsSettled
                && outcome.observedBankRouteFromBankComparisons
                >= route.minimumExpectedBankRouteFromBankComparisons
                && outcome.observedBankRouteFromBankItemGatedComparisons
                >= route.minimumExpectedBankRouteFromBankItemGatedComparisons;
        if (!passed) {
            outcome.bankRouteComparisonError = "Expected at least "
                    + route.minimumExpectedBankRouteFromBankComparisons
                    + " BANK_ROUTE_FROM_BANK comparison(s) and "
                    + route.minimumExpectedBankRouteFromBankItemGatedComparisons
                    + " item-gated bank comparison(s), observed "
                    + outcome.observedBankRouteFromBankComparisons + " and "
                    + outcome.observedBankRouteFromBankItemGatedComparisons
                    + "; shadowEnabled=" + upstreamPlannerShadow
                    + ", settled=" + outcome.bankRouteComparisonsSettled;
        }
        return passed;
    }

    private void applyShortestPathOverrides(F2PWebWalkerRoute route, String plannerMode) {
        Map<String, Object> config = new HashMap<>();

        config.put("plannerSelectionMode", plannerMode);

        String value = property(TEST_USE_TELEPORTATION_SPELLS_PROPERTY, USE_TELEPORTATION_SPELLS_PROPERTY, "");
        if (!value.isBlank()) {
            config.put("useTeleportationSpells", Boolean.parseBoolean(value));
        }

        if (route.forceNoAgilityShortcuts) {
            config.put("useAgilityShortcuts", false);
            config.put("useGrappleShortcuts", false);
        }

        if (route.forceNoTeleports) {
            config.put("useTeleportationItems", "None");
            config.put("useTeleportationLevers", false);
            config.put("useTeleportationMinigames", false);
            config.put("useTeleportationPortals", false);
            config.put("useTeleportationSpells", false);
            config.put("useWildernessObelisks", false);
        }

        if (route.forceCanoes) {
            config.put("useCanoes", true);
        }

        if (route.forceShips) {
            config.put("useShips", true);
        }

        if (route.requireMembersWorld) {
            config.put("useBoats", false);
            config.put("useFairyRings", false);
            config.put("useGnomeGliders", false);
            config.put("useMagicCarpets", false);
            config.put("useMagicMushtrees", false);
            config.put("useQuetzals", false);
            config.put("useSpiritTrees", false);
            config.put("useWildernessObelisks", false);
            if (route.expectedShadowExecutor == Rs2TransportExecutor.TERMINAL_TRAVEL) {
                config.put("useBoats", true);
                config.put("useShips", true);
            } else if (route.expectedShadowExecutor == Rs2TransportExecutor.GNOME_GLIDER) {
                config.put("useGnomeGliders", true);
            } else if (route.expectedShadowExecutor == Rs2TransportExecutor.SPIRIT_TREE) {
                config.put("useSpiritTrees", true);
            }
        }

        if (config.isEmpty()) {
            return;
        }

        Map<String, Object> data = new HashMap<>();
        data.put("config", config);

        eventBus.post(new PluginMessage("shortestpath", "path", data));
        log.info("[F2PWebWalkerHarness] Applied shortest path overrides for {}: {}", route.id, config);
    }

    private static boolean isHarnessTarget() {
        return "true".equals(System.getProperty("microbot.test.mode"))
                && System.getProperty(TEST_SCRIPT_PROPERTY, "").contains(SCRIPT_NAME);
    }

    private static String property(String preferred, String legacy, String defaultValue) {
        String value = System.getProperty(preferred);
        if (value != null && !value.isBlank()) {
            return value;
        }

        value = System.getProperty(legacy);
        if (value != null && !value.isBlank()) {
            return value;
        }

        return defaultValue;
    }

    private static PlannerSelectionMode plannerModeProperty(boolean legacyShadow) {
        String value = property(TEST_PLANNER_MODE_PROPERTY, PLANNER_MODE_PROPERTY, "");
        return value.isBlank()
                ? (legacyShadow ? PlannerSelectionMode.SHADOW : PlannerSelectionMode.LOCAL)
                : plannerMode(value);
    }

    private static PlannerSelectionMode plannerMode(String value) {
        return PlannerSelectionMode.fromConfigValue(value, PlannerSelectionMode.LOCAL);
    }

    private static int intProperty(String preferred, String legacy, int defaultValue) {
        String value = property(preferred, legacy, String.valueOf(defaultValue));
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

    private static String format(WorldPoint point) {
        if (point == null) {
            return null;
        }
        return point.getX() + "," + point.getY() + "," + point.getPlane();
    }

    private static void writeAndExit(WebWalkerTestResult result, int exitCode) {
        TestResultWriter.write(result);
        System.exit(exitCode);
    }

    public static class WebWalkerTestResult extends TestResult {
        public String routeFilter;
        public boolean stopOnFailure;
        public int walkTimeoutMs;
        public String plannerMode;
        public boolean upstreamPlannerShadow;
        public boolean expectLocalFallback;
        public boolean shadowSettled;
        public String shadowError;
        public Map<String, Object> shadowEvidence;
        public List<String> selectedRoutes = new ArrayList<>();
        public List<RouteOutcome> routes = new ArrayList<>();

        public WebWalkerTestResult(String script) {
            super(script);
        }
    }

    public static class RouteOutcome {
        public String id;
        public String name;
        public String start;
        public String destination;
        public int startTolerance;
        public int destinationTolerance;
        public int repetitions;
        public int successfulAttempts;
        public boolean currentLocationStart;
        public boolean requireF2PWorld;
        public boolean requireMembersWorld;
        public boolean membersWorld;
        public boolean forceNoAgilityShortcuts;
        public boolean forceNoTeleports;
        public boolean forceCanoes;
        public boolean forceShips;
        public String expectedShadowExecutor;
        public int minimumExpectedShadowExecutions;
        public int forcedActiveReplans;
        public int minimumExpectedActiveReplanComparisons;
        public boolean activeReplansSettled;
        public long observedActiveReplanComparisons;
        public String activeReplanError;
        public int forcedRecoveryReplans;
        public int forcedSetupRecoveryReplans;
        public int minimumExpectedRecoveryReplanComparisons;
        public int minimumExpectedRecoveryArrivals;
        public boolean recoveryReplansSettled;
        public long observedRecoveryReplanComparisons;
        public long observedRecoveryArrivals;
        public String recoveryReplanError;
        public int forcedBankRouteComparisons;
        public int minimumExpectedBankRouteFromBankComparisons;
        public int minimumExpectedBankRouteFromBankItemGatedComparisons;
        public boolean bankRouteComparisonsSettled;
        public long observedBankRouteFromBankComparisons;
        public long observedBankRouteFromBankItemGatedComparisons;
        public String bankRouteComparisonError;
        public boolean shadowExecutorSettled;
        public long observedShadowExecutions;
        public String shadowExecutorError;
        public String startedAt;
        public String finishedAt;
        public String initialLocation;
        public int initialDistanceToStart;
        public String setupState;
        public boolean setupPassed;
        public String setupError;
        public long setupDurationMs;
        public String routeStartLocation;
        public int distanceToStartAfterSetup;
        public String walkerState;
        public boolean passed;
        public String error;
        public long walkDurationMs;
        public String endLocation;
        public int distanceToDestination;
        public List<RouteAttemptOutcome> attempts = new ArrayList<>();
    }

    public static class RouteAttemptOutcome {
        public int attempt;
        public String start;
        public String destination;
        public String startedAt;
        public String finishedAt;
        public String initialLocation;
        public int initialDistanceToStart;
        public String setupState;
        public boolean setupPassed;
        public String setupError;
        public long setupDurationMs;
        public String routeStartLocation;
        public int distanceToStartAfterSetup;
        public String walkerState;
        public boolean passed;
        public String error;
        public long walkDurationMs;
        public String endLocation;
        public int distanceToDestination;
    }
}
