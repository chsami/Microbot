# Walker Audit & Roadmap

_Audit date: 2026-07-25. Scope: `shortestpath/` (pathfinding) and `util/walker/` (runtime execution)._

## Verdict

**Path *generation* is healthy. The runtime *executor* is the problem, and collision-map
fidelity is the root trigger.** The common failure mode (player stuck far from the goal,
unable to route back, eventually idle-logged-out) is a runtime-recovery pathology, not a
pathfinding one.

## Healthy — leave alone

- **`Pathfinder` (809 lines)** — bidirectional A*, admissible network-landmark heuristics
  (fairy rings / spirit trees / gliders / quetzals folded in as landmarks),
  underground-aware distance, per-node random tiebreaker to kill the "identical route every
  trip" fingerprint. Mature and well-commented.
- **`PathSmoother` (118)** — LOS run-collapsing with correct invariants (transport anchors
  and collision walls preserved; segment cap keeps `isNearPath` intersecting long corridors).
- **`CollisionMap.canStep` (451)** — correct N/E edge model; the live-overlay pin
  (`beginSearch` / `pinnedLive`) prevents mixing two scenes into one path.
- **Live-collision store (Phase 2)** — already built, disk-backed, self-filling, 22 tests
  green. Gated behind `useLiveCollision`, which **defaults OFF**.

## Problems (by severity)

1. **`Rs2Walker` is a ~12,000-line, 336-method god-class.** ~40+ methods are door/recovery
   heuristics accreted over time (`handleDoors`×2, `handleDoorsWithTimeout`×3,
   `tryResolveNearbyDoorBlocker`, `tryResolveDoorBlockerLineOfSight`,
   `tryResolvePathAdjacentBlocker`, `handleDoorsInRawSegment`, `handleRockfallInRawSegment`,
   `findReachableRejoinRawPathPoint`, `recentlyOpenedStationaryDoorOnSegment`, …).
   Unmaintainable; this is where stalls/loops live.
2. **Recovery does expensive per-tick scene scans.** Field log:
   `slow raw scene scan: doorProbe=1265ms doorWait=1564ms` — ~3s stalls, repeated, plus
   `cancel:processWalk:after-stuck-check`. Overlapping "stuck" recovery paths can cancel each
   other and thrash instead of converging.
3. **Collision fidelity is the *trigger*.** The pathfinder plans on the static map, which
   (a) mis-derives some edges (dumper heuristics ≠ runtime `CollisionData`) and (b) cannot
   model dynamic obstacles (doors, rockfalls). So the plan routes through tiles the live
   scene blocks → every discrepancy invokes the recovery machinery in #1/#2.
4. **Decomposition started but ~90% unfinished.** `util/walker/{door,stall,transport,lifecycle}/`
   packages exist with a few classes; the monolith still holds the bulk.

## Root-cause chain

static-map edge/obstacle gaps → plan crosses a live-blocked tile → player stalls → 12k-line
recovery pile does ~3s scene scans, sometimes loops/cancels → no progress → caller gives up →
idle logout.

## Roadmap

### P0 — Stabilize (low risk, high payoff)
- **Turn on + harden the live-collision store.** It's built and tested; it directly attacks
  root cause #3 (learns real blocked edges as the bot travels, overriding bad static). Add a
  **code-version stamp** to the disk store so stale pre-fix captures auto-invalidate (removes
  the manual "Reset learned collision" dependency). Verify self-heal + persistence in-client,
  then decide whether `useLiveCollision` flips default-ON.
- **Bound recovery cost.** Cache door-probe results per segment per tick and cap the
  door-scan budget so one blocked edge can't cause repeated 3s stalls or the cancel-loop.

### P1 — Decompose `Rs2Walker` (maintainability)
Extract into the already-scaffolded packages, leaving `Rs2Walker` a thin facade:
- `WalkExecutor` — the `processWalk` loop + clicking.
- `RouteRecovery` — rejoin / off-path / blocker logic.
- `DoorService` — consolidate the ~20 door methods into `util/walker/door/`.
- `ObstacleService` — rockfall + future dynamic obstacles, handled uniformly.

### P2 — Unify the recovery model (the real fix)
Replace the pile of special cases with one principle: **when the live scene contradicts a
planned edge, write that edge to the live store and recalc.** Doors/rockfalls become
uniformly-handled obstacle transports, not bespoke handlers — this lets you *delete*
thousands of lines rather than reorganize them.

### P3 — Observability & safety net
`WebWalkLog` is already rich; add a compact per-tick decision trace and lean on the existing
walker test harness so P1/P2 refactors are regression-checked.

## Sequencing

P0 is independent and gives the biggest immediate relief for the stuck-far-away symptom — do
it first. P1 unblocks P2. (AIOHunting's v1.9.3 travel-retry is the correct band-aid until P0
lands.)
