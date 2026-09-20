# Microbot scripting API audit repairs — 20 September 2026

Implemented in the isolated `f911` worktree, based on `2c0721cf0a` (the audit used `a8a907eefd`). The original audit was read from the original workspace without editing that workspace. Source findings were revalidated; the checkout was clean before work. Initial validation was completed before committing. The repairs are now being prepared for Walker-V2 and a separate upstream development branch. No new runtime dependencies or RuneLite API changes. The branch ports explicitly declare Mockito inline 4.11.0 for the static-mocking regression tests and record its dependency checksums.

## Finding checklist

| Finding | Repair | Regression evidence |
|---|---|---|
| 1 — reachability origin/cache | Flood from local player; explicit origins keyed by origin, tick, world, view/scene identity and base; immutable published sets; actual collision dimensions | `Rs2ReachableTest`: disconnected/query-order case failed before repair; null target failed before repair; now covers view/world/base/scene changes and read-only results |
| 2 — cache construction threads | NPC/player/ground-item membership, context checks and publication confined to client executor; same-tick logout/hop and scene/view context invalidate | `ApiBoundaryRegressionTest.queryConstructionCollectsOnClientThreadAndInvalidatesWithinTick`: strict accessors and two concurrent readers |
| 3 — live model/widget access | Actor delegates and mutations marshalled; local-player constructor captures one reference; NPC and ground-item action capture/revalidation; widget property reads and traversal confined, mouse work outside callbacks | `ApiBoundaryRegressionTest.actorReadsAndMutationsAndWidgetTextAreConfined`, `detachedEntitiesCannotDispatch`; inventory widget replacement test |
| 4 — shared wait future | Each request owns a `FutureTask` and scheduler handle, including completion before handle assignment; cancellation never targets another request | `GlobalAwaitRegressionTest`: independent completion, cancellation (including a latch-blocked in-flight condition), repeated polls, immediate completion, callback not self-interrupted |
| 5 — tutorial bypass | Pause/interruption precede tutorial handling and game reads; tutorial support retained | `GlobalAwaitRegressionTest.pauseAndInterruptionPrecedeTutorialAndGameReads` and `incompleteTutorialCanRunOnlyWhenUnpausedAndNotInterrupted` |
| 6 — boat cache key | Resolve one indexed boat per requested player on the client thread; no shared cross-player result | `ApiBoundaryRegressionTest.boatQueriesDoNotShareAnotherPlayersResult`: two views and on-land player |
| 7 — NPC distance units | Native world-view coordinates; Chebyshev tile distance; reject invalid planes/views and missing positions | `ApiBoundaryRegressionTest.npcDistanceUsesTilesAndRejectsDifferentViewsAndPlanes` |
| 8 — ground-item view | Store originating view in constructor/cache; legacy constructor resolves local point's view | Cache/view identity regression and stale-view dispatch regression |
| 9 — ground-item values | Market/base/alchemy long totals; int compatibility methods saturate; market-backed GE total; profitable means proceeds exceed market plus supplied consumable cost | `ApiBoundaryRegressionTest.valuesUseMarketPricesLongArithmeticAndExplicitAlchemyCosts`: values above int max, profit/break-even/loss |
| 10 — NPC names | Both partial operands use `Locale.ROOT`; null names/search terms rejected | `ApiBoundaryRegressionTest.nameMatchingIsLocaleIndependentAndNullSafe`: Turkish default locale |
| 11 — absent inventory slot | Safe Optional fallback; validate slot/item ID; reject missing or changed slot | `InventoryDispatchRegressionTest.missingInterfaceSlotChangedItemAndUnknownActionDoNotDispatch` |
| 12 — dispatch result | Boolean submission status propagated through interaction, drop and sell entry points; unresolved actions rejected; widget revalidated before submission | Both `InventoryDispatchRegressionTest` cases; successful and failed submission; synthetic ground-item dispatch guard retained |
| 13 — invalid nearest distances | Null locations and cross-plane sentinel excluded even for unbounded searches; consistent `within`; player-relative queries restrict view | `AbstractEntityQueryableInterruptionTest.nearestRejectsMissingLocationsAndOtherPlanes` and `playerRelativeWithinRejectsMissingWorldView` |
| 14 — polling pressure/status | Monotonic deadline, off-thread polling interval, boolean `awaitOnClientThread`; legacy void wrapper retained | `GlobalAwaitRegressionTest.clientThreadPollingIsBoundedAndInterruptible`: timeout invocation bound, immediate success, interruption and client-thread rejection |

## Validation

- Baseline `:client:compileJava` passed with existing warnings. Initial reachability regressions failed against baseline as expected.
- Narrow tests were run after focused repair batches. The first expanded run passed 17 tests; the dispatch/boat batch passed 11 tests.
- Integrated `:client:runUnitTests --tests 'net.runelite.client.plugins.microbot.*'`: **724 tests, 722 passed, 2 skipped**. Both client-thread and query-terminal guardrails pass. These totals include existing tests, not just new regressions.
- `git diff --check` passed.
- The final rebuilt-client probe passed; see the live results below.

## Contracts and lifecycle

See [QUERYABLE_API.md](../../runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/QUERYABLE_API.md) for one-shot query ownership, live wrappers, native versus projected coordinates, all-view anchors, timeout behavior, unsupported player interactions and submission versus completion. Added client-thread count/reachable terminals and aligned examples. Entity guides record reachability origin and item-identity pitfalls.

Local-player cache position/view/context now publish in one immutable holder. Inventory membership is invalidated on logout/hop/connection-loss and null container updates. These are narrowly justified lifecycle repairs; this work does not redefine ownership of every subclass executor or prove that every lifecycle risk in the audit caused a live failure.

## Guardrail baseline review

The scanner recognizes direct bridge lambdas/method references, but does not propagate confinement through private callees or stream lambdas. Its baseline was updated for reviewed client-thread-only helpers (with assertions), changed lambda numbering, and removed direct model/cache calls. Additional API methods became inferred when moved into client-thread callbacks, exposing pre-existing call sites in unchanged legacy utilities; those are recorded as existing debt rather than silently claiming the entire scripting facade is now thread-safe. The scanner implementation and enforcement rules were not weakened. Strict executor-based behavioral tests cover the repaired boundaries.

## Limits and follow-up risks

- Lists snapshot membership, not live entity state. Raw actors/widgets/collections returned from facades still require client-thread access. Several separate safe getters do not form an atomic observation.
- Mouse work must remain off-thread; a target can change after capture or during mouse travel. Submission does not guarantee game completion.
- An already executing predicate/bridge call can exceed the wait's polling deadline; the predicate must be short and nonblocking. Legacy wait methods outside the audited client-thread helper retain their existing behavior.
- Explicit `WorldPoint` anchors have no view identity and retain all-view scope. Callers must select the intended view when native coordinate ranges overlap. Existing actor main-world projection remains available separately.
- No speculative performance optimization or spatial index was added. Removing the incorrectly shared boat result is a correctness repair; no performance improvement is claimed. End-to-end live query timing is observational, not a before/after latency or allocation benchmark.
- Subview transitions, reconnects, world hops and concurrent target replacement have automated coverage where described; any scenarios not exercised live are listed with the live results.

## Live results

The client was rebuilt and restarted from this worktree, then logged in using the existing active profile. A temporary Gradle init script disabled `runDebug`'s debugger suspension; repository build settings were not changed. The probe verified that `Rs2Reachable` was loaded from `f911/Microbot/runelite-client/build/classes/java/main/`, not the original checkout.

Final observations:

- Player origin reachable; an unreachable tile was found; flooding explicitly from that tile did not alter player-relative answers.
- NPC/player/ground-item queries returned 15 / 1 / 6 entities; nearest-reachable NPC returned a candidate.
- Three query calls took about 120 ms in one live sample. This is not a performance benchmark or optimization claim.
- The client-thread wait returned success for a true condition and timeout for an always-false condition.
- An unknown inventory action returned false. `Use` returned true, selection was observed, and `deselect` returned true with selection then observed cleared. The probe waits for inventory visibility before deselecting: Escape can hide the tab asynchronously, and an unavailable interface correctly yields false.
- Paused and interrupted Script guards both rejected execution.
- No error-level client log entries or relevant facade warnings appeared in the final launch/probe log.
- The temporary plugin was undeployed; the repaired client was left logged in.

Only one world view was present. Live world hops, reconnect during an active query, sailing/subview transitions, and active gameplay script shutdown were not exercised. Automated tests cover the specific cache-context, different-view/plane, cancellation, pause, missing-state and stale-reference cases listed above; they do not substitute for those full live scenarios.

Probe source/result: `%TEMP%/microbot-debug-probes/api-audit-20260920-f911/`.
Final live log: `%TEMP%/microbot-api-client-final.log`.
Unit-test log: `%TEMP%/microbot-api-audit-tests.log`.

## Upstream branch port

`codex/upstream-api-repairs` is based directly on upstream `chsami/development` at `59e44f4655`. It contains the API repairs without merging Walker-V2 history. Upstream human-input takeover handling is retained in scheduled waits, client-thread waits and the script guard; a regression covers takeover before polling or tutorial execution.

The Microbot unit suite passed 1,228 tests (1,226 passed, 2 skipped). After the final takeover regression, 20 focused wait/takeover and guardrail tests passed with baseline regeneration disabled. Compilation and whitespace checks passed. No live-client restart was performed for this branch port; the live evidence above belongs to the original repair baseline.

Mockito inline 4.11.0 is explicitly declared for static mocks, with verification checksums. Runtime dependencies are unchanged. The branch-specific guardrail baseline retains the original reviewed helper/lambda adjustments; three additional entries refer to unchanged upstream walker code (canvas dimensions and nested widget traversal), not new walker changes.
