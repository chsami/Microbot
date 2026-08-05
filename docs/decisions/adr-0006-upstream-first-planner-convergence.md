# ADR 0006: Establish an Upstream-Compatible Planner Boundary and Selection Gate

- Status: Architecture and F2P evidence gates accepted (2026-08-05);
  default/release selection pending explicit approval

## Context

Microbot needs Shortest Path's current collision, transport semantics and planner improvements, but it also
owns live interaction, recovery, banking and automation policy that the display plugin does not. Selectively
copying transport families improved correctness, yet continuing that work before proving an interchangeable
planner would preserve a second search implementation indefinitely behind a facade that still read mutable
local configuration.

The reviewed upstream planner also does not retain the exact selected transport in its public `PathStep`.
Endpoint rematching is ambiguous when multiple transports share an origin and destination, so production
execution cannot safely adopt it without exact edge identity.

## Decision

Make production-capable upstream planner convergence the next walker milestone:

- freeze opportunistic broad transport-family copying after the reviewed ship slice; continue parity work
  required by the adapter or selection gate, incident-driven data fixes and pinned collision updates;
- dispatch planning through Microbot-owned `Rs2RouteRequest`, immutable `Rs2RoutePolicy`, `Rs2RoutePlanner`
  and `Rs2RouteResult` contracts;
- resolve mutable client/config state before engine dispatch; an engine may not read Microbot plugin globals;
- inject Microbot executor capability as catalog-admission policy outside the pathfinder core;
- retain exact selected source identity only as an opaque package-private adapter payload;
- run the reviewed upstream engine in shadow mode against real requests before selecting it for execution;
- accept or reject the upstream core against a pinned commit, expanded corpus, runtime evidence and explicit
  performance report.

Planner selection is an explicit three-state policy, not two interacting booleans:

- `LOCAL` selects only the local planner and remains the default;
- `SHADOW` executes the local route and compares the upstream result asynchronously;
- `UPSTREAM_F2P_CANARY` calculates both candidates for non-members policy, publishes no executable route
  until comparison is complete, selects upstream only for a semantic match, and otherwise retains local.

The local result is a containment fallback, not an absolute correctness oracle. A semantic divergence is
therefore observable and rejecting evidence even though the canary conservatively executes the local route.
The active route remains calculating until one final candidate is selected atomically. Upstream results are
temporarily materialized into the legacy `Pathfinder` view so existing execution and overlay consumers retain
exact selected transport identity; that compatibility shell must be removed with the local planner after the
fallback sunset.

## Consequences

- `LOCAL` remains the production default until the final reachable revision passes the evidence gates and a
  release explicitly changes the scoped policy.
- New local search algorithms require a pinned incident or benchmark and must preserve the engine boundary.
- Upstream schema/data work continues where required for the adapter, but semantic-debt reduction alone is no
  longer the primary walker milestone.
- The production package now contains the reviewed upstream core in an isolated source set. Its exact-identity,
  live-collision and walking-cost hooks remain a small declared adapter patch surface, enforced by an offline
  tree digest and optional byte-for-byte comparison with the pinned checkout. The reviewed budget is six
  patched upstream files and one adapter-added file; growth requires an amendment to this ADR explaining why
  the hook cannot remain outside the core or be contributed upstream.
- Shadow mode is default-off and cannot select a route for execution. Synchronous queries, ordinary active
  walker routes and cave-route selection publish bounded comparison evidence while the local core remains
  authoritative.
- Walker execution, recovery, live collision capture and automation policy remain Microbot-owned whichever
  planner is selected.
- The production switch is not implied by this ADR. The repeatable headless thresholds and live-shadow
  requirements are defined in `docs/walker-planner-selection-gate.md`; until they pass, the accepted decision
  is to preserve the upstream-compatible boundary while retaining the local core as authoritative.
- Upstream is the maintenance-preferred candidate, not a predetermined production winner. The selection
  decision weighs correctness, runtime reliability, performance and maintenance cost through the same
  engine-neutral contract.
- Passing the selection gate permits a controlled rollout; it does not remove the local core. The first
  upstream-selecting release is a match-gated canary limited to F2P policy because that is the scope of the
  accepted live evidence. It is not yet an upstream-authoritative release: an otherwise valid upstream route
  with a different cost or selected transport still falls back to local. Expanding selection to members policy
  requires a separate representative live-shadow slice for
  members-only executor, requirement and network behavior. Every upstream-authoritative scope must retain a
  tested, release-independent local-planner fallback and dual-planner telemetry during staged rollout. Any
  confirmed upstream planner failure or unexplained semantic divergence on a request selected for execution
  triggers rollback to the local planner.
- A later upstream-authoritative mode must not turn the local result into a permanent correctness oracle or
  accept arbitrary differences. Before that mode exists, Microbot must define an engine-independent route
  validity check and a digest-pinned reviewed-divergence policy. Only a route that satisfies the resolved
  request and immutable planning snapshot, materializes exact executable transport identities and either
  matches or has a reviewed divergence may be selected. Failure, invalid materialization or an unclassified
  divergence retains local and trips the rollout rollback signal. Merely adding such a mode does not authorize
  enabling it or changing the default.
- The fallback has an explicit sunset instead of becoming permanent architecture. The local planner becomes
  eligible for removal only after every supported rollout scope has used upstream authoritatively for at
  least two completed releases and has accumulated at least 1,000 settled production dual-planner
  comparisons with no unresolved semantic divergence or planner failure. The rollback path must also have a
  passing release test and there must be no open incident that requires the local core. At that point the next
  planned release removes the local planner; extending it requires a dated decision record naming the incident
  and a new expiry condition.
- The opt-in F2P canary and rollback behavior were validated live on 2026-08-05 using the underground
  Varrock Sewers object-transition route. The normal run made ten upstream selections and ten arrivals with
  no divergence or failure. A separate test-only injected-failure run made zero upstream selections, recorded
  ten local failure fallbacks and still produced ten arrivals. This validates the selector and independent
  fallback mechanism; it does not by itself authorize changing the default or cutting a release.
- Canary evidence also records the coordinate-free duration from route submission through both searches,
  semantic comparison, selection/fallback and route materialization. The paired release evaluator requires
  one timing sample per completed comparison, a maximum readiness time of 2,000 ms and an aggregate
  non-search overhead average no greater than 250 ms per decision. This measures the user-visible dual-running
  cost that the independent engine benchmark cannot prove without making a fixed-cost short route fail a
  noisy ratio gate.
