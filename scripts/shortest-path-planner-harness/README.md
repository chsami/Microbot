# Dual-engine planner harness

This directory independently adapts the exact reviewed `Skretzo/shortest-path` commit for the comparison in
`../compare-shortest-path-planners.py`. It remains test infrastructure. Microbot separately packages a pinned,
non-UI core under `runelite-client/src/upstreamPlanner`; the harness proves that production adapter has not
drifted semantically from this independently compiled checkout.

## Exact transport identity

The reviewed upstream `PathStep` stores position and bank state but not the transport selected by the
search. Reconstructing an edge from its endpoints is ambiguous when multiple transports connect the same
points. The orchestrator therefore applies `upstream-exact-transport-identity.patch` to its temporary,
detached checkout before compiling the runner. The patch only carries the selected `Transport` reference
through `NodeGraph` into `PathStep`; it does not change neighbor generation, queue order, costs, visited
state, or termination.

Both runners create transports from immutable corpus definitions and retain an identity map from the exact
object to its corpus ID. A selected object that is not in that map is an error. The comparison also checks
the complete ordered edge records, so endpoint-based rematching cannot pass the ambiguous-network case.

## Policy scope

Schema 3 supports:

- `STATIC_COLLISION_ONLY`: no catalog transports;
- `EXPLICIT_CATALOG`: only transports marked `ALWAYS` are available;
- `BANK_AWARE_EXPLICIT_CATALOG`: transports marked `AFTER_BANK` become available when the declared start
  tile is a bank or a declared bank is reached.

The bank cases prove unavailable, start-at-bank and separate-bank-detour policy transitions and retain the
exact selected edge. Upstream represents the detour with bank state inside one search. Microbot's current
workflow compares a direct search with composed start-to-bank and bank-to-target searches. Reachability,
final path cost, selected edges and bank-visited state are correctness-comparable; node, elapsed-time and
peak-memory measurements for the composed workflow are not planner-core performance parity.

Run the strict harness from the repository root:

```bash
scripts/compare-shortest-path-planners.py --require-all
```

The report records the reviewed upstream revision, production-packaged upstream revision, RuneLite version,
corpus digest, instrumentation-patch digest, packaged-adapter failures, explicitly expected input-policy
divergences, and whether the local worktree was dirty.

One run is a correctness gate and a performance diagnostic, not production-selection evidence. Collect at
least five clean, same-revision reports and evaluate them with:

```bash
scripts/report-shortest-path-planner-performance.py \
  build/shortest-path-performance/sample-*/report.json
```

The rationale, comparability rules, thresholds and live evidence requirement are documented in
`docs/walker-planner-selection-gate.md`.
