# F2P Web Walker Harness

This harness runs live in-game webwalker regression routes for a fresh F2P account after Tutorial Island. It uses `Rs2Walker.walkWithState(...)` for both setup movement and the route under test.

The Microbot CLI may be used while investigating a failure to read state, nearby objects, nearby NPCs, screenshots, or logs. Do not use `./microbot-cli walk` or any other manual movement command to place the player at the destination.

## Run

Full fail-fast suite:

```bash
scripts/run-f2p-webwalker-harness.sh
```

Specific route:

```bash
scripts/run-f2p-webwalker-harness.sh F2P-15
```

The script compiles the client, starts RuneLite in test mode with AutoLogin enabled by `TestRunnerPlugin`, runs the hidden `F2P Web Walker Harness` plugin, and writes:

```text
~/.runelite/test-results/f2p-webwalker/result.json
```

Override the timeout or output directory:

```bash
MICROBOT_WEBWALKER_TIMEOUT_MS=2400000 \
MICROBOT_WEBWALKER_LEG_TIMEOUT_MS=300000 \
MICROBOT_WEBWALKER_OUTPUT_DIR=/tmp/f2p-webwalker \
scripts/run-f2p-webwalker-harness.sh F2P-15
```

The runner forwards route settings through `microbot.test.webwalker.*` system properties because the Gradle `runTest` task only propagates `microbot.test.*` properties into the launched client JVM.

`TestRunnerPlugin` starts before ordinary plugins and clears the persisted enabled flag for the selected
test target. It starts that target only after a game tick reports `LOGGED_IN`, a local player is present and
the welcome-screen Play widget is no longer visible, then clears the enabled flag again while leaving the
target active for the current process. This readiness contract is intentional: RuneLite can report
`LOGGED_IN` and expose a local player before the welcome overlay has stopped blocking interaction, and a
persisted harness flag must not start its private route timeout during the next client's login sequence.

## Planner modes and evidence

Planner selection is explicit and defaults to `LOCAL`:

- `LOCAL` runs only the local planner;
- `SHADOW` executes local and compares upstream asynchronously;
- `UPSTREAM_F2P_CANARY` calculates both candidates for non-members policy and selects upstream only after a
  semantic match. Members-policy requests remain local.

Enable shadow evidence for a harness run with:

```bash
MICROBOT_WEBWALKER_PLANNER_MODE=SHADOW \
scripts/run-f2p-webwalker-harness.sh F2P-17
```

`MICROBOT_WEBWALKER_UPSTREAM_PLANNER_SHADOW=true` remains a harness compatibility alias for `SHADOW`; new
automation should set the mode directly.

The harness waits up to two minutes for the bounded shadow worker to settle and embeds the same
coordinate-free schema-v2 object served by `/walker/shadow` under `shadowEvidence` in `result.json`. An
enabled run fails if it submits no comparison, leaves work pending, or observes a semantic divergence or
planner failure. Extract and run the full coverage evaluator with:

```bash
jq '.shadowEvidence' ~/.runelite/test-results/f2p-webwalker/result.json \
  > build/walker-shadow-snapshot.json
scripts/evaluate-walker-shadow-evidence.py \
  build/walker-shadow-snapshot.json \
  --json-output build/walker-shadow-evidence.json \
  --markdown-output build/walker-shadow-evidence.md
```

A single route can prove that evidence collection works but cannot satisfy the production selection gate's
cross-category minimums. Run the representative route mix described in `docs/walker-planner-selection-gate.md`.

The accepted 2026-08-05 aggregate combines 12 independently validated fresh-client snapshots and passes the
full F2P live gate: 141/141 semantic matches, 75 active routes, 15 active replans, 11 recovery replans, 39
underground comparisons, 18 walking-only cave selections and 71 exact arrivals, with no divergence, planner
failure, pending/discarded work, unreachable outcome or exit. Keep the inputs separate and use the evaluator;
do not treat one long process or hand-added counters as equivalent evidence.

Run the opt-in F2P selection canary separately:

```bash
MICROBOT_WEBWALKER_PLANNER_MODE=UPSTREAM_F2P_CANARY \
MICROBOT_WEBWALKER_OUTPUT_DIR=/tmp/microbot-f2p-canary \
scripts/run-f2p-webwalker-harness.sh F2P-17
```

The canary keeps the active route calculating until both planners have completed, then atomically exposes one
selected route. It fails evidence collection if an ordinary canary run records a semantic divergence, planner
failure or no upstream selection. The accepted 2026-08-05 underground run completed five repetitions, made
ten upstream selections and recorded ten arrivals with no divergence or failure.

The test-only forced-failure mode proves the release-independent local fallback without changing production
failure handling:

```bash
MICROBOT_WEBWALKER_PLANNER_MODE=UPSTREAM_F2P_CANARY \
MICROBOT_WEBWALKER_FORCE_UPSTREAM_FAILURE=true \
MICROBOT_WEBWALKER_EXPECT_LOCAL_FALLBACK=true \
MICROBOT_WEBWALKER_OUTPUT_DIR=/tmp/microbot-f2p-rollback \
scripts/run-f2p-webwalker-harness.sh F2P-17
```

The failure hook is honored only in test mode. An accepted rollback run requires planner failures and local
failure fallbacks, requires zero upstream selections, and still requires every live route arrival. The
2026-08-05 run recorded ten injected failures, ten local fallbacks and ten arrivals. Terminal outcomes are
bound to the generation-matched ready route, so `LOCAL` walks and members-policy walks under the F2P canary
cannot inflate these counts; the normal and forced-failure F2P-17 runs each retained all ten eligible arrivals.

Evaluate the two fresh-client artifacts as one release decision instead of reviewing their embedded checks
independently:

```bash
scripts/evaluate-walker-rollout-evidence.py \
  /tmp/microbot-f2p-canary/result.json \
  /tmp/microbot-f2p-rollback/result.json \
  --json-output build/walker-f2p-rollout-evidence.json \
  --markdown-output build/walker-f2p-rollout-evidence.md
```

The paired evaluator requires the pinned candidate, distinct client sessions, the same required route set,
settled accounting, ten normal upstream selections and arrivals, and ten forced failure fallbacks and
arrivals. It also requires one coordinate-free canary-readiness timing sample per completed comparison,
rejects a submission-to-ready maximum above `2,000 ms`, and rejects average non-search overhead above `250 ms`
per decision after subtracting both measured searches. Readiness includes executor queueing, both searches,
semantic comparison, selection/fallback and route materialization. Its report is coordinate-free and rejects
exception-message exposure. The fresh 2026-08-05 pair passes with no failure, shortfall or warning: normal
readiness averaged `304.3 ms`, peaked at `645.7 ms` and averaged `172.0 ms` of non-search overhead; forced
rollback averaged `260.8 ms`, peaked at `675.1 ms` and averaged `123.4 ms` of non-search overhead.

Prerequisite-bearing selection-gate routes are intentionally excluded from the default fresh-account suite.
Run them explicitly on a suitable profile:

```bash
MICROBOT_WEBWALKER_PLANNER_MODE=SHADOW \
scripts/run-f2p-webwalker-harness.sh F2P-18
```

It first performs ten real `compareRoutes` calls and requires all ten explicit bank-to-target shadow legs to
settle and select an item-gated transport. It then performs three Lumbridge-to-Champions' Guild repetitions,
disables agility shortcuts and teleports, enables canoes, and requires at least five completed `CANOE` shadow
selections in addition to exact arrivals. The accepted 2026-08-05 rerun produced 49/49 matching comparisons,
10/10 matching item-gated bank legs and six exact terminal arrivals.

The representative terminal-travel slice requires at least 90 coins for two outbound journeys and one reverse
setup journey:

```bash
MICROBOT_WEBWALKER_PLANNER_MODE=SHADOW \
scripts/run-f2p-webwalker-harness.sh F2P-19
```

It disables agility shortcuts and teleports, enables ships, and requires at least three completed
`TERMINAL_TRAVEL` shadow selections in addition to exact arrivals.

The ordinary-replan slice injects twelve replans only while a long surface route is active and requires each
one to finish in the upstream shadow before accepting the final arrival:

```bash
MICROBOT_WEBWALKER_PLANNER_MODE=SHADOW \
scripts/run-f2p-webwalker-harness.sh F2P-20
```

The recovery slice queues its replans through a test-only hook that is consumed by the walker thread. This
exercises the same recovery evidence context as a real stall without manufacturing a client-thread sleep or
calling the recovery helper from the harness thread. Three repetitions produce at least five alternating Port
Sarim / Rimmington legs; each sufficiently long outbound or reverse setup leg attempts two progress-gated
recovery replans. The verifier uses observed results rather than requested injections and requires ten
completed comparisons plus five recovered arrivals. The accepted 2026-08-05 session produced 11/11 matching
recovery comparisons and six recovered arrivals with no exit or unreachable outcome:

```bash
MICROBOT_WEBWALKER_PLANNER_MODE=SHADOW \
scripts/run-f2p-webwalker-harness.sh F2P-21
```

The existing spell-teleport stress harness can capture a separate fresh-session slice:

```bash
MICROBOT_GE_LUMBRIDGE_ITERATIONS=3 \
MICROBOT_GE_LUMBRIDGE_UPSTREAM_PLANNER_SHADOW=true \
scripts/run-ge-lumbridge-teleport-harness.sh
```

Extract its `shadowEvidence` as a second file and pass both snapshots to the evaluator. It validates every
session before aggregation and rejects duplicate session start identities. Do not concatenate JSON or add
counters by hand.

## Agent Loop

1. Run the full suite.
2. If a route fails, inspect `result.json`, `~/.runelite/logs/client.log`, and optional observational CLI output such as `./microbot-cli state`, `objects`, `npcs`, or screenshots.
3. Understand whether the failure was setup movement or the route itself.
4. Patch the walker or supporting path data.
5. Rebuild and rerun only the failed route, for example `scripts/run-f2p-webwalker-harness.sh F2P-15`.
6. Once the failed route passes, rerun the full suite.

## Required Routes

| ID | From | To | Coverage |
|---|---:|---:|---|
| F2P-01 | `3222,3218,0` | `3208,3220,2` | Lumbridge castle stairs and plane change |
| F2P-02 | `3208,3220,2` | `3253,3266,0` | Castle exit to local outdoor area |
| F2P-03 | `3253,3266,0` | `3092,3245,0` | Lumbridge/Draynor open-world routing |
| F2P-04 | `3092,3245,0` | `3109,3168,0` | Draynor to Wizards' Tower bridge |
| F2P-05 | `3109,3168,0` | `3029,3217,0` | Bridge to Port Sarim docks |
| F2P-06 | `3029,3217,0` | `2957,3214,0` | Port Sarim to Rimmington |
| F2P-07 | `2957,3214,0` | `2946,3368,0` | Rimmington to Falador |
| F2P-08 | `2946,3368,0` | `3082,3420,0` | Falador to Barbarian Village |
| F2P-09 | `3082,3420,0` | `3093,3493,0` | Barbarian Village to Edgeville |
| F2P-10 | `3093,3493,0` | `3164,3486,0` | Edgeville to Grand Exchange |
| F2P-11 | `3164,3486,0` | `3185,3441,0` | Grand Exchange to Varrock west bank |
| F2P-12 | `3185,3441,0` | `3253,3420,0` | Varrock west-to-east city routing |
| F2P-13 | `3253,3420,0` | `3222,3218,0` | Varrock to Lumbridge long return |
| F2P-14 | `3092,3245,0` | `3109,3341,0` | Draynor Manor approach |
| F2P-15 | `3109,3341,0` | `3106,3363,0` | Draynor Manor door/object handling |
| F2P-16 | `3106,3363,0` | `3092,3245,0` | Reverse manor exit behavior |
| F2P-17 | `3236,3458,0` | `3237,9858,0` | Walks from the fixed Varrock surface manhole to the sewers 5 times on a F2P world with agility shortcuts and teleports disabled; setup climbs out before every repetition, so a prior run ending underground cannot turn the case into a no-op |

## Selection-gate routes

These prerequisite-bearing routes are available by explicit ID and are not included by the default `all`
filter.

| ID | From | To | Coverage |
|---|---:|---:|---|
| F2P-18 | `3243,3237,0` | `3199,3344,0` | Ten explicit item-gated bank-to-target comparisons plus three River Lum canoe repetitions and five or more `CANOE` planner selections |
| F2P-19 | `3029,3217,0` | `2956,3146,0` | Two Port Sarim-to-Musa Point repetitions with three or more fare-gated `TERMINAL_TRAVEL` planner selections |
| F2P-20 | `3029,3217,0` | `2946,3368,0` | Long surface walk with twelve deliberately injected and settled `ACTIVE_REPLAN` comparisons |
| F2P-21 | `3029,3217,0` | `2957,3214,0` | Five alternating surface walks with two walker-thread `RECOVERY_REPLAN` comparisons each and five recovered arrivals |
