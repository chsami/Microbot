# Walker planner selection gate

## F2P production selection evidence complete

The upstream-compatible boundary is correct, and the reviewed upstream core now satisfies the F2P-scoped
correctness, runtime, rollback and performance evidence gates. `Skretzo/shortest-path` remains the
maintenance-preferred candidate algorithm and reviewed reference data; members-policy selection is not
justified until its separate evidence slice passes.
Microbot's resolved planning snapshot remains authoritative for executable-edge admission, live collision,
account policy and intentional data overlays. Microbot should continue to own banking orchestration,
interaction, recovery and automation policy.

This avoids an indefinitely diverging local search fork while keeping Microbot runtime behavior independent
of a RuneLite display plugin. The `Rs2RoutePlanner` boundary keeps those responsibilities separate and makes
the production choice reversible.

## Required evidence

A production-core decision requires all of these gates:

1. **Pinned implementation:** the packaged core matches the reviewed upstream commit except for the declared,
   digest-pinned adapter patch surface.
2. **Headless correctness:** every parity corpus case agrees on termination, endpoint, route cost and exact
   selected edge; every intentional policy divergence remains documented and observable.
3. **Headless performance:** at least five clean, same-revision comparison runs satisfy the performance
   thresholds below.
4. **Live shadow behavior:** active surface, underground, transport, bank/replan and recovery routes produce
   enough categorized evidence to show no unexplained semantic divergence or planner failure. Stale and
   discarded shadow work is capacity telemetry, not a route mismatch.
5. **Explicit rollout decision and rollback:** record the evidence and choose the production core
   deliberately. The first production rollout must retain a tested, release-independent way to select the
   local planner and preserve comparable telemetry for both engines. During staged dual-running, any confirmed
   upstream planner failure or unexplained semantic divergence on a request selected for execution triggers
   rollback to the local planner. The normal-canary and forced-failure artifacts must jointly pass
   `scripts/evaluate-walker-rollout-evidence.py`; do not infer selection from packaging the adapter, enabling
   shadow mode or reviewing two self-reported harness checks independently. The paired artifact must also
   contain complete canary-readiness timing: route submission through both searches, comparison,
   selection/fallback and upstream materialization.
6. **Evidence-scoped rollout:** accepted F2P evidence permits only a match-gated F2P upstream-selecting
   rollout. Selecting upstream for members policy requires a separate representative members-world slice
   covering members-only executors, requirements and transport networks.

The current implementation satisfies all six gates at opt-in F2P canary scope. Shadowing exists for
synchronous, ordinary active and
cave routes. Its coordinate-free coverage counters, read-only Agent Server endpoint and opt-in F2P harness
capture produced an accepted 12-session aggregate covering recovery, transport, item-gated, bank-workflow,
surface, underground, walking-only, live-collision, active-route/replan, arrival and executor-diversity
minimums without a semantic divergence or planner failure. Five clean samples from the exact evaluated
revision pass gate 3 with a `0.407` upstream/local comparable-suite median ratio and no correctness, absolute
or per-case threshold failure.
The explicit `UPSTREAM_F2P_CANARY` selector now keeps members policy local, waits for both F2P candidates
before atomically publishing an active route, selects upstream only on a semantic match and records distinct
divergence/failure local fallbacks. A live underground object-transition run selected upstream 10/10 times
and arrived 10/10 times. A separate test-only forced-failure run selected upstream 0/10 times, recorded ten
failure fallbacks and still arrived 10/10 times. A fresh pair with aggregate canary-readiness telemetry passes:
normal readiness averaged `304.3 ms`, peaked at `645.7 ms` and averaged `172.0 ms` of non-search overhead;
forced rollback averaged `260.8 ms`, peaked at `675.1 ms` and averaged `123.4 ms` of non-search overhead.
Production default/release selection remains an explicit release decision; passing these gates does not change
`LOCAL` automatically.

The sanitized source snapshots and generated reports are tracked under
`docs/evidence/walker/2026-08-05/`. The tracked inputs reproduce the accepted live-shadow and paired rollback
reports byte-for-byte with the commands in that directory's README. Raw client logs, coordinates and account
or profile data are intentionally excluded.

## Performance protocol

Generate independent comparison reports from the exact code under review:

```bash
for sample in 1 2 3 4 5; do
  scripts/compare-shortest-path-planners.py \
    --require-all \
    --output-dir "build/shortest-path-performance/sample-${sample}"
done

scripts/report-shortest-path-planner-performance.py \
  build/shortest-path-performance/sample-*/report.json \
  --json-output build/shortest-path-performance/evidence.json \
  --markdown-output build/shortest-path-performance/evidence.md
```

The checked-in evaluator requires:

- five or more reports with identical local, upstream, RuneLite, corpus and adapter-patch identities;
- a clean local worktree for every sample;
- no correctness, packaged-adapter or unsupported-capability failure;
- an upstream/local median comparable-suite elapsed ratio no greater than `1.5`;
- for each comparable case, an upstream maximum no greater than both `2,000 ms` and the larger of three
  times the local maximum or a `100 ms` noise allowance.

The relative case threshold prevents a material regression from hiding in a favorable suite total. The
noise allowance avoids rejecting a sub-tick route because one engine takes a few additional milliseconds.
Thresholds are command-line options, but changing them for a decision requires an accompanying rationale.

The independent engine measurements do not capture the time an active dual-planner canary keeps the route in
the calculating state. The live paired evaluator therefore additionally requires:

- one `canaryPerformance` planning sample per completed comparison;
- successful upstream-search timing for every normal-canary comparison and none for the injected pre-search
  failure case;
- a maximum submission-to-ready duration no greater than `2,000 ms`; and
- average non-search overhead no greater than `250 ms` per decision after subtracting both measured searches.

The duration includes pathfinding-executor queue time, both searches, semantic comparison, selection or
fallback and upstream-route materialization. The readiness/local-search ratio remains diagnostic, while the
explicit overhead allowance avoids a fixed-cost short route failing solely because its local search is fast.
Change either threshold only through the evaluator arguments and record the release rationale with the
evidence.

Bank-aware cases are excluded from core timing because Microbot currently composes direct, to-bank and
from-bank searches while upstream models bank state in one graph search. Their route semantics remain part
of the correctness gate. The documented Twinflame provider-policy divergence is also excluded. Node counts
and peak-heap deltas remain diagnostic: the engines use different graph structures and independent JVM heap
baselines, so those values are not interchangeable resource measurements.

## Live shadow protocol

For every evidence slice, start a fresh client so its process-lifetime counters describe one review session,
enable the developer-only shadow mode and verify collection before running routes:

```bash
./microbot-cli plugin-config set shortestpath plannerSelectionMode SHADOW
./microbot-cli walker shadow
```

The schema-versioned endpoint reports only invocation and route-property tags; it does not expose route
coordinates, target names, paths or exception messages. It distinguishes deliberate recovery from ordinary
replanning, reports selected transport executor/type families and counts a live-collision route only when the
pinned overlay actually answers at least one collision-edge read. Exercise ordinary surface routes,
underground/cave routes, selected transports, bank-item planning, live collision and deliberate
recovery/replanning. Let the one-worker shadow queue settle, then capture it. The evaluator accepts multiple
fresh-session snapshots so resource- or account-specific harnesses remain isolated:

```bash
./microbot-cli walker shadow > build/walker-shadow-underground.json
scripts/evaluate-walker-shadow-evidence.py \
  build/walker-shadow-underground.json \
  build/walker-shadow-teleport.json \
  build/walker-shadow-network.json \
  --json-output build/walker-shadow-evidence.json \
  --markdown-output build/walker-shadow-evidence.md
```

Every input is validated independently before aggregation. All inputs must use schema v2 and the same pinned
candidate engine, every queue must be settled and each `startedAtEpochMillis` must be unique so the same
client session cannot be counted twice. A divergence, planner failure or invalid accounting in any member
session rejects the aggregate; splitting scenarios across clients does not relax any total or category
threshold.

The default gate requires at least 100 completed comparisons and zero divergences or planner failures. Its
overlapping coverage minimums are 75 ordinary active routes, 15 ordinary active replans, 10 recovery-triggered
replans, 60 surface routes, 20 routes using underground coordinates, 10 walking-only cave selections and 20
routes selecting transports. Five routes must select an item- or fare-gated transport. Ten comparisons must
be the explicit bank-to-target leg of `compareRoutes`, and five of those must select an item- or fare-gated
transport. This proves the bank workflow was compared without mislabeling every request with the bank setting
enabled as bank-dependent. Twenty-five routes must actually consult captured live-collision edges; merely
turning the setting on does not count.

Transport evidence must cover at least four distinct executor families. The default grouped minimums are five
local-transition routes (`OBJECT` or `BARROWS_DIG`), five teleport routes, five network routes and three
terminal-travel routes. Per-executor and per-type counters remain available for reviewing the concrete mix.
The accepted fresh-client inputs must contain at least 50 terminal blocking walks that arrived and at least
five arrivals after recovery was actually triggered. A recovery-triggered `UNREACHABLE` or `EXIT` rejects
the evidence; other terminal non-arrivals remain visible warnings for manual classification.
Terminal outcomes are route-scoped: the blocking walk captures whether its active logical route actually
entered planner comparison before arrival clears that route. A members-policy walk in
`UPSTREAM_F2P_CANARY`, or any walk in `LOCAL`, must not increment these counters merely because the
process-wide mode supports comparison. `SHADOW` routes and eligible F2P canary routes do increment them.
The candidate engine ID must equal the pinned reviewed commit, shadow mode must still be enabled and no
comparison may be pending at capture. Stale and discarded work is reported as sampling telemetry; it does not
become a semantic mismatch, and it does not count toward completed coverage when discarded before execution.

Exact walking-path equality is diagnostic rather than a semantic rejection because equal-cost tie paths are
valid. The endpoint reports a coordinate-free `routeShapeDifferences` total and preserves the most recently
completed differing comparison as `latestRouteShapeDifference`, even after the active route is torn down.
Every counted shape difference has already matched termination, endpoint, route cost and exact selected
transport identity; it is therefore an equal-cost route-shape alternative, not a semantic mismatch. Before a
rollout decision, classify the observed volume by invocation and by walking-only, transport, surface and
underground coverage. Inspect at least one representative route from every non-zero class and explain any
class responsible for more than one quarter of completed comparisons. A class associated with a non-arrival,
recovery regression or repeated visible detour remains rejecting until explained or fixed.

## Staged rollout and fallback sunset

Planner selection uses one explicit mode: `LOCAL`, `SHADOW` or `UPSTREAM_F2P_CANARY`. `LOCAL` remains the
default. `SHADOW` always executes local and compares upstream asynchronously. `UPSTREAM_F2P_CANARY` is
eligible only when the resolved immutable policy is non-members; it calculates both candidates and keeps the
active route in the calculating phase until selection completes. A semantic match may select the exact
upstream route. A planner failure or semantic divergence retains local and increments a dedicated fallback
counter; members requests remain local and are not canary comparisons. A route-shape-only difference remains
a semantic match when termination, endpoint, cost and exact selected transports agree.

The first upstream-selecting release is the F2P match-gated canary. Comparison remains enabled for eligible
production requests, with `LOCAL` selectable independently of the upstream source set. This canary is not yet
an upstream-authoritative mode: a different route cost or selected transport remains a divergence and retains
local, even when the upstream result might be a genuine improvement. Local fallback is
conservative containment, not evidence that local is the correct oracle; every divergence still requires
review and remains rejecting release evidence. The upstream result is temporarily materialized into the
legacy completed-pathfinder view for existing runtime consumers. Remove that compatibility shell with the
local core when the fallback sunset is reached.

### Post-canary authority contract

Do not implement an authority mode as "select upstream unless it throws." Before an `UPSTREAM_F2P` mode can
be enabled, all of the following must be implemented and tested:

1. An engine-independent validator must prove that the candidate belongs to the resolved request and immutable
   planning snapshot, has a coherent termination and endpoint, has contiguous route steps, and uses only exact
   admitted transport identities with an executable Microbot owner.
2. A digest-pinned reviewed-divergence policy must distinguish an approved improvement from an unexplained
   semantic difference. Approval requires a route-property regression and representative live execution for
   every newly admitted divergence class; a broad "shorter is safe" rule is insufficient.
3. Failure, invalid materialization, an invalid candidate or an unclassified divergence must execute the local
   result for the current request and emit a release-visible rollback signal. Selection and fallback counters
   must distinguish match-gated canary decisions from authoritative reviewed-divergence decisions.
4. Dual-planner telemetry and the release-independent `LOCAL` control remain enabled throughout the staged
   authority period. Adding the enum/config value does not authorize changing the default.

Only releases in which the authority mode can select a reviewed valid divergence count as
"upstream-authoritative" for the two-release/1,000-comparison fallback sunset. Match-gated canary releases do
not start that clock.

The vendored delta is also bounded: six upstream files may be patched and one adapter-only source may be
added. `scripts/check-shortest-path-vendored-core.py` rejects a larger surface. Raising the budget requires a
dated ADR amendment and a contribution-or-external-adapter assessment, so upstream convergence cannot quietly
turn into another growing local planner fork.

The local core becomes eligible for deletion only after every supported rollout scope has completed at least
two upstream-authoritative releases and at least 1,000 settled production dual-planner comparisons without an
unresolved semantic divergence or planner failure. The release test must prove the independent rollback path,
and no open walker incident may require the local core. Once these conditions pass, remove the local planner in
the next planned release. Any extension requires a dated ADR amendment identifying the incident, owner and new
expiry condition.

Semantic divergences and planner failures are always rejecting outcomes. The endpoint preserves their latest
coordinate-free summaries as `latestDivergence` and `latestFailure` for the process lifetime, including after
active-route teardown. The evaluator rejects a non-zero terminal counter that lacks its corresponding
diagnostic; a failure exposes only the exception class, never its message or route data.

## Current signal

Five clean samples from the exact evaluated revision pass the performance evaluator with no correctness or
threshold failure. The comparable-suite median is `1,365.3 ms` locally and `555.2 ms` upstream, an
upstream/local ratio of `0.407` against the `1.5` limit. Every comparable case also passes its absolute and
relative maximum. The durable aggregate is
`docs/evidence/walker/2026-08-05/performance-evidence.json`.

Twelve independently validated fresh sessions aggregate to 141/141 semantic matches, 71/71 exact walker
arrivals and zero divergence, planner failure, pending, discarded, unreachable or exit outcomes. All live
minimums pass: 75 ordinary active routes, 15 ordinary active replans, 11 recovery replans, 102 surface
comparisons, 39 underground comparisons, 18 walking-only cave selections, 97 transport selections, 59
item- or fare-gated selections, 10 explicit item-gated bank-to-target legs and 137 live-collision-consulted
searches. The executor evidence covers object transitions, spell teleports, canoes and terminal travel, so all
four grouped minimums and the four-executor diversity minimum pass. Two completed comparisons were stale
after deliberate route replacement; they remain valid completed semantic results but do not represent the
latest route generation.

Seventy-six comparisons used a different exact path while still matching termination, endpoint, route cost
and exact selected transports. The retained coordinate-free diagnostics classify these as equal-cost
route-shape alternatives. Thirty-one occurred in two transport-free surface replan/recovery sessions where
every comparison differed; both sessions still completed all blocking walks, including six recovered
arrivals. Thirty-three occurred in the bank/canoe session, whose retained representative selected the same
exact canoe and cost; that session completed 49/49 comparisons and all six walks. The remaining twelve were
spread across surface active-route/replan slices; retained representatives cover spell, canoe and
transport-free alternatives. None of the 39 underground comparisons differed—the two sewer-session shape
warnings came from their surface setup legs. No shape-only class coincided with a non-arrival, recovery
regression or visible route failure.
A prior canoe session recorded one non-canoe semantic divergence but did not retain the superseded comparison,
so it remains rejected historical evidence and is not part of the aggregate. The rebuilt three-repetition
canoe session completed 7/7 comparisons and 6/6 walks without reproducing it; any recurrence will now retain
its mismatch category.

The first dedicated bank session then exposed a reproducible local-core defect: all ten bank-to-target
comparisons selected the farther Barbarian Village canoe at cost 130 locally while upstream selected the
nearer Edgeville canoe at cost 78. The local frontier's geometric heuristic was not admissible in a graph with
long-distance transports. Cost-ordering the local frontier restored exact parity, and a real catalog/collision
regression pins that route. The rebuilt session completed 49/49 matching comparisons, including 10/10 explicit
item-gated bank-to-target legs, and all six terminal walks arrived. The rejected pre-fix session is not part of
the aggregate.

The headless correctness and live-shadow gates are closed, and the exact current tree passes the clean timing
thresholds. The opt-in F2P selector and independent rollback path are implemented and live-validated on the
underground F2P-17 route: the normal canary recorded 10/10 upstream selections and arrivals with zero
divergence/failure; the forced-failure run recorded 10/10 local failure fallbacks and arrivals with zero
upstream selections. Both were repeated after terminal evidence became route-generation scoped and retained
all 10 eligible arrivals; focused members-canary and local-mode regressions prove ineligible walks leave the
execution totals unchanged. The fresh paired release evaluator accepts the normal and forced-failure artifacts
with complete submission-to-ready telemetry and no failure, shortfall or warning. The normal canary averaged
`304.3 ms`, peaked at `645.7 ms` and averaged `172.0 ms` of non-search overhead; rollback averaged `260.8 ms`,
peaked at `675.1 ms` and averaged `123.4 ms` of non-search overhead. `LOCAL` remains the default. The evidence
permits an explicitly approved F2P-scoped release; it does not permit members-policy selection or silently
change the default.
