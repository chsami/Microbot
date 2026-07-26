# Walker P2 — Unify the Obstacle Model

_Plan date: 2026-07-26. Depends on P1 (docs/walker-audit.md): `state/WalkerRouteState`,
`recovery/RouteRecovery` + harness, `geometry/WalkerPathGeometry`, and the pre-existing
`door/` + `obstacle/` packages are in place._

## Status / outcome (2026-07-26)

The plan below was executed **partially and deliberately** — the parts that pay off were taken, the parts
that would trade a working system for architectural tidiness were **not**. What shipped:

- ✅ **Abstractions + registry** (`obstacle/`: `PlannedEdge`, `ObstacleResolution`, `ObstacleResolver`,
  `ObstacleRegistry`, `LiveScene`, `WalkerActions`) — headless-tested.
- ✅ **`MineableResolver` + `TransportResolver`** — decision logic pure, headless-tested.
- ✅ **Dispatch cutover in the recovery block** (`Rs2Walker.resolveRecoveryObstacle`) — one
  `ObstacleResolution` switch replacing the inline rockfall mine + the stepping-stone override.
  **Live-verified** (user walked door route + MLM rockfall + River Lum stones, "works fine").
- ✅ **Rockfall fully migrated + legacy deleted** — all three rockfall sites route through
  `MineableResolver` (`resolveRockfallOnSegment`/`resolveRockfallOnEdge`); `applyRockfall` and
  `handleRockfallInRawSegment` are gone. The complete adapter→cutover→delete lifecycle on one obstacle type.
- ✅ **Door decision cores harnessed** (`Rs2DoorClassifierTest`, `Rs2DoorGeometryTest`) — the "tests first"
  net, without touching the cascade.

**Deliberately NOT done (and why):** the **door cascade** and the **transport interaction** (`handleTransports`,
charter ships / stairs / precomputed continuations) were left on their existing paths. Unlike rockfall (an
isolated, per-edge obstacle that fit the model cleanly), these are **stateful, order-dependent cascades**
whose complexity is largely *essential* (real scenario diversity), not the accidental sprawl this plan
assumed. Forcing them into per-edge `resolve()` would **reorder** working recovery logic for limited gain —
a poor risk/reward on the walker's subtlest, working subsystem. The `door/` package is already well-
decomposed and now tested at the decision level, which is the right end state for it. Revisit only if a
specific symptom demands it — targeted, harness-pinned — not as a wholesale rewrite. `Rs2LiveScene` (the
live read adapter) is retained for a future per-edge dispatch model but is currently unused in production
(the shipped dispatch never needed the scene view).

The plan as originally written follows, for context.

## Problem being solved

The walker plans on a static/overlay collision map, then discovers at runtime that a planned edge is
blocked or gated by something the map can't model: a closed **door**/gate, a **rockfall**/rockslide, an
agility **shortcut** (stepping stone, grapple, pipe), or a **transport** (stairs, ladder, cave, teleport,
boat, fairy ring). Today each of these has its **own bespoke handler and its own recovery path**:

- doors: `handleDoors`×2, `handleDoorsInRawSegment`, `handleUnresolvedDoorNearRawPath`,
  `tryResolvePathAdjacentBlocker`, `tryResolveNearbyDoorBlocker`, the whole `door/` package, plus
  door-attempt bookkeeping (`lastDoorAttempt*`, `nextDoorInteractionAllowedAtMs`, `rawScanFocusedDoor*`).
- rockfalls: `obstacle/Rs2ObstacleHandler` (already returns a `RockfallResult`).
- shortcuts/transports: the ~760-line `handleTransports`, `handleTransportsInRawSegment`,
  `finishHandledTransport`, `RouteRecovery.findReachableTransportOriginAhead` (the stepping-stone fix).

They are gated differently, fire at different points in `processWalk`, and interact — which is why a fix
in one (rockfall passable → smoother walks through → recovery clicks the far bank) breaks another. That is
the whack-a-mole. **P2 collapses all of it into one path so a fix lands in one place, and thousands of
lines of special-casing get deleted rather than reorganised.**

## The one principle

> When the live scene contradicts a planned edge, identify what blocks it and resolve it uniformly:
> walk to the interaction tile if needed, perform the one right interaction, wait for it to clear, then
> continue the route. If nothing can resolve it, record the real blocked edge and recalculate.

Every door/rockfall/shortcut/transport is an instance of this. There is exactly one dispatch.

## Core abstractions (new, in `util/walker/obstacle/`)

```
PlannedEdge        { WorldPoint from, to; boolean adjacent; }          // the route step in question
LiveScene          // read-only injected view: reachable set, transports map, tile objects/actions,
                   // player tile. NO Rs2* statics inside resolvers -> harness-testable.
ObstacleResolution enum { CROSSED, INTERACTED, WALK_TO_ORIGIN(WorldPoint), WAITING, ABORT(reason),
                          NOT_APPLICABLE }
ObstacleResolver   interface {
    boolean handles(PlannedEdge edge, LiveScene scene);      // pure, cheap classification
    ObstacleResolution resolve(PlannedEdge edge, LiveScene scene, WalkerActions io);  // may act
}
ObstacleRegistry   // ordered list of resolvers; first that handles() wins.
```

`WalkerActions` is the thin imperative shell (interact-with-object, click-tile, sleepUntil) — the only
part that touches the live client. Resolvers keep their **decision** logic pure (classification +
what-to-do), so each is exercised headlessly by the harness exactly like `RouteRecoveryTest` does now.

## Resolvers (each replaces a pile)

| Resolver | handles() when the edge is blocked by… | resolve() |
|----------|----------------------------------------|-----------|
| `DoorResolver` | an openable door/gate on the edge (uses `door/Rs2DoorDetection`/`Rs2DoorClassifier`) | open it, wait for the edge to open |
| `MineableResolver` | a rockfall/rockslide on/adjacent (folds in `Rs2ObstacleHandler`) | mine it, wait for it to clear |
| `TransportResolver` | the edge is a transport/shortcut origin (stepping stone, ladder, cave, teleport) | `WALK_TO_ORIGIN` if not standing on it (the stepping-stone fix), else take it |

Adding a new dynamic obstacle later = one new resolver, registered — never another branch in `processWalk`.

## Resolution flow (replaces the cascade)

`processWalk`'s "stuck at unreachable tile" recovery and the post-transport segment handlers both become:

```
PlannedEdge edge = nextBlockedPlannedEdge(path, player, scene);   // the frontier we can't cross
if (edge != null) {
    switch (registry.resolve(edge, scene, io)) {
        case WALK_TO_ORIGIN(t): clickToward(t); return;    // e.g. step onto the stone
        case INTERACTED / WAITING: return;                 // door opening / rock mining
        case CROSSED: continue;
        case ABORT(r): liveStore.markBlocked(edge); recalculate(); return;   // learn + replan
        case NOT_APPLICABLE: fall through to the plain minimap-recovery click.
    }
}
```

One place. The ~40 handlers become 3 resolvers behind `resolve()`.

## Migration — strangler, ordered (each step compile+harness green, live-test the behavior ones)

1. **Define the abstractions** above + a `LiveScene`/`WalkerActions` adapter over the current
   `Rs2Player`/`Rs2Tile`/`Rs2GameObject`/`Rs2PathApi` calls. No behavior change.
2. **Adapter resolvers first (no rewrite):** wrap the *existing* `Rs2DoorHandler`, `Rs2ObstacleHandler`,
   and `handleTransports` logic as `DoorResolver`/`MineableResolver`/`TransportResolver` that delegate to
   today's code. Register them. Still no behavior change — just reachable through one interface.
3. **Cut over the dispatch:** replace the recovery/segment-handler cascade in `processWalk` with the
   single `registry.resolve(edge)` call above. This is the first behavior-affecting step → **live-test**
   (door route, MLM rockfall, stepping stone). Because the resolvers still wrap the old code, behavior
   should be identical; the cutover just proves the single path.
4. **Rewrite resolvers into pure decision + thin action, one at a time**, deleting the wrapped legacy
   methods as each is replaced. Start with `TransportResolver` (already partly pure via
   `findReachableTransportOriginAhead`), then `MineableResolver` (already clean), then `DoorResolver`
   (the biggest deletion). Each rewrite is harness-tested headlessly + one live walk.
5. **Delete** the now-dead special-cases and their bookkeeping state.

## Deletion targets (the payoff)

- The ~20 door methods + `door/`'s overlap with `processWalk`, and door-attempt state fields.
- The special-case branches inside the ~760-line `handleTransports` that duplicate segment/recovery logic.
- The duplicated recovery paths (`route-fold-continuation`, per-obstacle `*InRawSegment` scans, the
  far-tile fallback's obstacle guesses). Net: target four-figure line reduction, not reorganisation.

## Testing

Every resolver's `handles()`/`resolve()` decision is pure and headless-tested in
`recovery`/`obstacle` test classes using in-memory `LiveScene` fixtures (the pattern `RouteRecoveryTest`
already establishes). `WalkerActions` is mocked. The only thing needing a live walk is the **dispatch
cutover (step 3)** and each **resolver rewrite (step 4)** — a handful of walks, not one per fix.

## Where the cataloged "weirdness" gets fixed

Each symptom the user is cataloging (far-side clicks, oscillation, mis-timed door probes, transports not
taken) is a property of **one** resolver or the single dispatch — fix it there, once, with a harness test
that pins it. No more "fix here, break there," because there is no longer a "there."

## Risks & mitigation

- **Behavior drift during cutover** → steps 2–3 keep the *old* logic behind the interface, so the cutover
  is a dispatch change, not a logic change; live-tested.
- **Un-unit-tested `processWalk`** → the strangler keeps `processWalk` mostly intact; only the recovery
  dispatch is swapped, and each resolver rewrite is guarded by harness + one live walk.
- **Scope creep** → resolvers are added/rewritten one at a time; the branch is always shippable.
