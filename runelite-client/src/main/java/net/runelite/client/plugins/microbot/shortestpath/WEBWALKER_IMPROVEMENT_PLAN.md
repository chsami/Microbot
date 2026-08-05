# Webwalker Improvement Plan

Consolidated findings from a four-angle audit (pathfinder algorithm, Rs2Walker robustness, transport data completeness, concurrency/observability). Sibling to `UPSTREAM_COMPARISON.md` — this plan goes **beyond** known upstream gaps.

*Generated: 2026-04-18*
*Baseline commits: `e04669b7d4` (walker stalling fix), `b5aa6a7165` (handleTransports perf), `9a8b845fa0` (pathfinder upstream sync)*

**Status key:** `DONE` (code in tree implements it) · `PARTIAL` (some landed, some gaps) · `OPEN` (not implemented)

**Rollup (2026-04-18 audit):** 22 DONE · 9 PARTIAL · 8 OPEN · total 39. (Tier 3 closed.)

> **2026-07-20 Stage 4 audit — Tier 1–2 backlog is largely already implemented.** With the facade in place (Stages 1–3 done), a live-code sweep plus re-baseline against the configured `skretzo` remote found most flagged "backport" items already landed: **#2 wilderness DONE** (boundaries match upstream), **#3 POH DONE** (12 destinations backfilled), **#8 bidirectional BFS DONE**, **#31 currency filter DONE**, **#33/#34 transport-index perf DONE**, and **#11 closed as a stale premise** (Node already uses packed ints; the remaining `List<WorldPoint>` is the public output boundary). Genuinely open internal items are **#10** (transport reachability prune), **#12** (SplitFlagMap region LRU eviction), and **#30** (one-way transport flag); the re-baseline found no confirmed upstream implementation for those, so treat them as independent Microbot work rather than backports. The remaining confirmed upstream data gap is home-teleport coverage (`teleportation_spells_home.tsv` variants).
>
> **2026-07-20 reconciliation — direction set: FACADE-FIRST.** Spot-verified against the live tree on `So-I-dont-Fuck-The-Walker-KEK`. Confirmed: boat world-view is **DONE** (`WorldPointUtil.fromLocalInstance`, closes `UPSTREAM_COMPARISON.md` #6); #11 is **CLOSED (stale premise)**; #3 POH coverage is **DONE** after the 12-destination backfill; and #27 is **12/16** pairs (was 8). A full 39-item re-audit was **not** performed — only the items below were re-checked. The facade-first decision has since been completed in Stages 1–3, so further selective backports can change internals without exposing them to `Rs2Walker` and other consumers. See the **"Facade migration (2026-07-20)"** section at the bottom for the frozen coupling contract and staged plan.

---

## Tier 1 — Correctness bugs (fix first)

Small patches, big payoff. Most are known but still unlanded.

| # | Status | Issue | File | Effort |
|---|---|---|---|---|
| 1 | DONE | `PrimitiveIntHashMap.rehash()` data loss — `return;` → `continue;` | `PrimitiveIntHashMap.java:210` | S |
| 2 | DONE | Wilderness boundaries — **re-verified 2026-07-20 against upstream values**: `PathfinderConfig.java:41-46` now uses above-ground start Y=3525, level-20 Y=3680, level-30 Y=3760, underground 518×458, level-20 Y=10075, level-30 Y=10155 (all match `UPSTREAM_COMPARISON.md`), named `LEVEL_20`/`LEVEL_30`, wired via `isInLevel20/30Wilderness`. Chebyshev mod-6400 also landed. | `Pathfinder.java`, `PathfinderConfig.java` | M |
| 3 | DONE | POH is routed programmatically via `util/poh/` (not a TSV). Coverage diffed against upstream `teleportation_portals_poh.tsv` (137 rows / 50 dests) and against the game's 42 `ObjectID.POH_PORTAL_MAG_*` portal-chamber destinations. `PohPortal` was missing 12 (Barbarian Outpost, Fishing Guild, Port Khazard, Marim, Civitas illa Fortis, Ourania, Cemetery, Dareeyak, Trollheim, Lassar, Ice Plateau, Paddewwa) — **backfilled 2026-07-20** with upstream arrival tiles + compiler-verified MAG/MARBLE/TEAK object ids (`PohPortal` 28→40). They auto-flow into the transport graph via `PohPortal.findPortalsInPoh()` → `PohPanel` → `PohTransport`. | `util/poh/data/PohPortal.java` | M |
| 4 | DONE | `growBucket()` integer overflow — `Math.min(len, MAX/2-4)*2` | `PrimitiveIntHashMap.java:147` | S |
| 5 | DONE | Missing `onGameStateChanged` refresh — quest/varbit goes stale after re-login | `ShortestPathPlugin.java:488` | S |

---

## Tier 2 — Pathfinder performance

Measurable against existing `PathfinderBenchmarkTest`.

| # | Status | Change | Expected impact | Effort |
|---|---|---|---|---|
| 6 | DONE | A* with Chebyshev heuristic (walking subgraph; transports stay in `pending` PQ) | 30–50% node reduction on long routes | M |
| 7 | DONE | Line-of-sight path smoothing post-BFS — 78–85% path-tile reduction measured across corpus (`PathSmoother.java`, `Pathfinder.getWalkablePath()`) | 15–25% fewer walker clicks | M |
| 8 | DONE | Bidirectional BFS for long routes — implemented (`Pathfinder.java` meet-in-the-middle, `BIDIRECTIONAL_MIN_CHEBYSHEV=2000`, fwd/back landmark residuals). *Verified 2026-07-20.* | √N speedup when src/dst far apart | L |
| 9 | PARTIAL | Cache `SplitFlagMap` + parsed transports as singletons in `PathfinderConfig` — fields cached per instance, singleton discipline across Pathfinder constructions unclear | Eliminates per-pathfinder re-parse | S |
| 10 | OPEN | Transport reachability prune at config load | 10–20% fewer teleports to explore | M |
| 11 | STALE-DESC | Premise no longer holds (2026-07-20): `Node` already uses a packed-int (`packedPosition`) linked structure, **not** `List<Integer>`. The only remaining `List<WorldPoint>` is the *output* path (`Pathfinder.path/smoothedPath/joinedPath`), which is the `getPath()` facade boundary — packing it would change the public signature, so it is explicitly **not** a behind-the-facade change. Close unless a measured alloc hotspot justifies an internal packed path + conversion at `getPath()`. | `Node.java`, `Pathfinder.java` | M |
| 12 | OPEN | Region LRU eviction in `SplitFlagMap` | Long-session memory bound | M |

---

## Tier 3 — Rs2Walker robustness

The "completeness of navigating the world" half.

| # | Status | Issue | File:Line | Effort |
|---|---|---|---|---|
| 13 | DONE | Static mutable walker state serialized via `walkerLock` `ReentrantLock` at both top-level entry points (`walkWithState`, `walkWithBankedTransportsAndState`); reentrant so same-thread dispatch/recursion works, contention logs a warning then blocks. `setTarget`/`recalculatePath` stay unlocked as the cross-thread cancel path (`currentTarget` stays volatile). | `Rs2Walker.java:90–97, 172–195, 3550–3576` | M |
| 14 | DONE | Stall threshold now activity-aware via `stallThresholdMs()`: base 10s × max(1.0, 2.0 if in combat, 1.5 if animating, 1.5 if interacting). Stall-recalc log now reports effective threshold + activity flags. No real-time ping in RuneLite API, so pure latency scaling is skipped (documented in-line). | `Rs2Walker.java:2290–2323, 315–324` | S |
| 15 | DONE | Sidestep recovery now ranks reachable tiles by Chebyshev distance-toward-target and draws from top-3 with weighted randomness (fallback keeps variance so repeated stalls don't lock onto the same blocked tile) | `Rs2Walker.java:328–342` | S |
| 16 | DONE | Diagonal door probes now early-exit when `fromWp.getPlane() != toWp.getPlane()` (cross-plane path steps are always transports: stairs/ladders/trapdoors). Prevents wrong-plane corner probes and misleading "Found door" logs at stair steps. | `Rs2Walker.java:1236–1242` | M |
| 17 | DONE | Closed trapdoor/manhole/grate/hatch variants now detected dynamically from `ObjectComposition` (any nearby object with an "Open" action + matching name) when the transport action is "Climb-down". If the found object lacks the transport action but advertises "Open", the walker interacts with "Open" first, re-finds the now-open object, then runs the transport. Legacy `OPEN_TO_CLOSED_MAPPINGS` retained as a fallback. | `Rs2Walker.java:1923–2003` | M |
| 18 | DONE | `manageRunEnergy(pathRemaining)` called once per `processWalk` iteration: toggles run when disabled, and on paths ≥20 tiles drinks stamina/energy potion when run energy < 30%, stamina buff not already active, and 10s has elapsed since last dose. Pot preference via `Rs2Inventory.useRestoreEnergyItem()` (stamina preferred). | `Rs2Walker.java:693–720, 376` | M |
| 19 | DONE | After a door interact, if the player didn't move and `isQuestLockedDoorDialogue()` matches ("quest" / "you need to" / "you must" / "cannot enter" / "requires you" / …), log `warn` with door details + dialogue text, add the tile to `sessionBlacklistedDoors`, close the dialogue, refresh `PathfinderConfig` (re-read quest/varbit state) and `recalculatePath()`. Entry of `handleDoors` short-circuits on blacklisted tiles to break the retry loop. | `Rs2Walker.java:1256–1275, 1376–1396` | M |
| 20 | DONE | POH `convertInstancedWorldPoint()` null-path diagnostics added: `handleDoors` null log now includes rawFrom/rawTo/fromWp/toWp and `idx/pathSize`; `setTarget` POH instance start now null-checks `WorldPoint.fromLocalInstance` (falls back to raw world location with a `warn` when it returns null) | `Rs2Walker.java:1206–1213, 1473–1486` | M |
| 21 | DONE | Minimap click now scans forward from first past-threshold tile to the furthest same-plane, non-transport-origin tile within ~14-tile Chebyshev reach, then advances the loop index past the intermediate tiles. Cuts tick count on long diagonal runs by ~30-40% since Chebyshev reach is 1.4× the cardinal step count. | `Rs2Walker.java:476–531` | M |
| 22 | DONE | `Telemetry.recordUnreachable(cause, player, target, pathEndpoint, pathSize, threshold, routeMetrics)` logs at `warn` with planner-independent route metrics; wired into both UNREACHABLE exits (no-walkable-path and partial-retries-exhausted) with `unreachableCount` counter exposed to probes | `Rs2Walker.java` | S |

---

## Tier 4 — Transport data completeness

| # | Status | Missing / wrong | Notes | Effort |
|---|---|---|---|---|
| 23 | PARTIAL | Falador wall (5 Agi, 2950,3373↔3374), Edgeville Monastery wall (10 Agi) — some rows exist in `agility_shortcuts.tsv`, full F2P shortcut set not confirmed | Core F2P shortcuts | S |
| 24 | PARTIAL | Quest Cape + Max Cape teleport rows — Max Cape rows present in `teleportation_items.tsv`, Quest Cape rows still missing | Endgame automation | S |
| 25 | OPEN | 20+ skill cape teleports missing (Cooking, Magic, Prayer, Slayer, Herblore, …) — only Slayer/Skills necklace covered | | M |
| 26 | PARTIAL | Achievement diary teleport toggles incomplete — only Varrock medium fully wired; Ardougne/Karamja/Desert harder tiers missing | | M |
| 27 | PARTIAL | Magic Mushtree permutation matrix incomplete — **12 of expected 16** data rows present in `magic_mushtrees.tsv` (re-counted 2026-07-20; was 8) | Recent commit `75b1f167ce` added feature; confirm remaining 4 pairs | M |
| 28 | PARTIAL | Stronghold of Security spirit tree + Prifddinas crystal-tier destinations — placeholders in `spirit_trees.tsv`, actual rows incomplete | | S |
| 29 | PARTIAL | Slayer Ring Ankous (3193,3611), Fairy Ring CJQ (Great Conch) — CJQ present, Ankous Slayer Ring variant still missing | | S |
| 30 | OPEN | No `isOneWay` flag on transports — return-only boats and Legends' jagged-wall used bidirectionally | | M |
| 31 | DONE | Currency-aware transport filtering present — `PathfinderConfig.java:879-892` checks `Transport.currencyAmount`/`currencyName` against inventory + (optionally) bank before allowing a transport. *Verified 2026-07-20.* | | L |

---

## Tier 5 — Concurrency, observability, testing

| # | Status | Issue | File:Line | Effort |
|---|---|---|---|---|
| 32 | PARTIAL | `log.debug()` unconditionally builds joined transport strings — guards present at `Rs2Walker.java:1548, 1604`; other sites (`~894, 1083, 1174`) still unguarded | `Rs2Walker.java:1548, 1579, 1604, 2610` | S |
| 33 | DONE | `getTransportsForPath` now builds `buildPathFirstIndex(path)` (a `Map<WorldPoint,Integer>`) once and does `pathFirstIndex.get(...)` lookups instead of per-transport `indexOf` (`Rs2Walker.java:3498, 3540`). *Verified 2026-07-20.* | | S |
| 34 | DONE | `handleTransports` rebuilds `pathFirstIndex` HashMap every call — now cached per call | `Rs2Walker.java:1560–1562` | S |
| 35 | OPEN | `Rs2Player.getWorldLocation()` called 10+ times per tick — capture once per loop | `Rs2Walker.java:182, 268, 318` | S |
| 36 | PARTIAL | Pathfinder `Future` cancellation has no wait/timeout — `.cancel(true)` in place, bounded-wait still missing | `ShortestPathPlugin.java:294` | M |
| 37 | OPEN | `WalkerStateListener` callback interface — replace polling with events | `Rs2Walker.java:163–220` | M |
| 38 | DONE | Unit tests for `VisitedTiles`, `FlagMap` region edges, transport graph, `Restriction` matching — `PathfinderBenchmarkTest`, `ShortestPathCoreTest`, `ShortestPathTier1RegressionTest` present | `test/.../shortestpath/` | M |
| 39 | DONE | Live pathfinder stats (volatile `nodesChecked`, elapsed) exposed during run | `Pathfinder.java:88–100` | S |

---

## Suggested sequencing

- **Week 1 (correctness):** #1, #3, #4, #5, #2, #32 — small, high-value, low risk.
- **Week 2 (perf foundation):** #9, #33, #34, #35, then #6 (A*); verify via `PathfinderBenchmarkTest`.
- **Week 3 (robustness):** #13, #14, #15, #20, #22 — walker concurrency + stall diagnostics.
- **Week 4 (data):** #23, #24, #25, #26, #27 — bulk TSV backfill; add unit tests as we go (#38).
- **Later:** #8 bidirectional, #16–#19 door/energy, #30/#31 cost modeling, #37 listener API.

---

## Caveats — verify before acting

- **#3 (POH TSV empty)** and **#23 (Falador/Edgeville wall missing)** came from sub-agent reads — eyeball actual TSVs before committing.
- **#14 (stall timeout line)** — `e04669b7d4 fix walker stalling` may already have adjusted the heuristic; re-check.
- **#27 (Mushtree permutations)** — commit `75b1f167ce` added the feature; confirm how much of the matrix landed.
- Line numbers marked `~` are approximate (from the audit pass); re-grep before editing.

---

## Source audits

This plan synthesizes four parallel sub-agent reports, each with its own reasoning trail:
1. Pathfinder algorithm & data structures (BFS→A*, smoothing, bidirectional, VisitedTiles allocations, TransportNode, reachability prune).
2. Rs2Walker robustness (stall detection, door/gate handling, instances, POH, teleport selection, energy, diagonal clicking).
3. Transport data completeness (agility shortcuts, skill capes, diary toggles, spirit trees, mushtrees, one-way flag, currency).
4. Concurrency / observability (static state, client-thread compliance, allocation hotspots, region eviction, structured logging, metrics, tests).

Items already catalogued in `UPSTREAM_COMPARISON.md` are surfaced here only where they overlap with an execution tier; refer to that doc for the full upstream-sync punch list.

---

## Facade migration (2026-07-20)

> **2026-08-05 boundary review:** direct `ShortestPathPlugin` state access has been migrated back behind
> `Rs2PathApi` and is now CI-enforced by `scripts/check-shortest-path-boundary.py`. The class remains a
> compatibility seam rather than the final stable API because it still exposes concrete `Pathfinder`,
> mutable `PathfinderConfig` and `Transport` values. The canonical next steps are in
> `docs/walker-roadmap.md` “Tighten the planner boundary.” The first operation-level slice now provides
> immutable route requests/results and has migrated bank, deposit-box and banked-destination searches off
> direct `Pathfinder` construction.

**Goal:** decouple automation from the shortest-path *internals* so future upstream backports stop rippling into `Rs2Walker` and the other consumers. The fork stays a fork; this is a boundary, not a rewrite. Once the boundary exists, backporting an upstream fix means changing code behind the facade only.

### Why first
`Rs2Walker` (~9k lines) reaches directly into `ShortestPathPlugin` static state — `getPathfinder`, `setPathfinder`, `getPathfinderFuture`, `getPathfinderMutex`, `getMarker`, `isStartPointSet`, etc. Every upstream change that touches those internals currently risks the walker (hence the branch name). A facade freezes the surface the walker sees.

### Frozen coupling contract (what the facade MUST expose)
Verified 2026-07-20 by grepping all consumers under `plugins/microbot/`.

`ShortestPathPlugin` static members referenced externally (23):

| Category | Members |
|---|---|
| Pathfinder lifecycle | `getPathfinder` / `setPathfinder` / `pathfinder`, `getPathfinderFuture` / `setPathfinderFuture`, `getPathfindingExecutor` / `setPathfindingExecutor`, `getPathfinderMutex` |
| Config | `getPathfinderConfig` / `pathfinderConfig`, `setReachedDistance`, `configOverride`, `override`, `CONFIG_GROUP` |
| Target / state | `exit`, `isStartPointSet` / `setStartPointSet`, `setLastLocation` |
| Marker (overlay) | `getMarker` / `setMarker`, `MARKER_IMAGE` |
| Data | `getTransports` |

`Pathfinder` external surface is small — only `getPath()` and `isDone()`. `PathfinderConfig` is heavily used *inside* `Rs2Walker` (43 refs) and by `leaguetransport` — its surface must be catalogued before Stage 2.

### Stage 1 catalogue — complete external contract (2026-07-20)

Read-only sweep of all **26** consumer files (`grep` for `microbot.shortestpath` usage outside the `shortestpath/` package). Counts are call-site occurrences, distinct methods only. This is the surface `Rs2PathApi` must reproduce.

**`Pathfinder`** (instance, via `ShortestPathPlugin.getPathfinder()`):

| Member | Uses |
|---|---|
| `getPath()` | 1 |
| `isDone()` | 2 |

**`PathfinderConfig`** (via `getPathfinderConfig()` / `pathfinderConfig`):

| Member | Uses |
|---|---|
| `refresh()` | 18 |
| `setUseBankItems(boolean)` | 13 |
| `isUseBankItems()` | 5 |
| `getTransports()` | 4 |
| `getAllTransports()` | 2 |
| `setIgnoreTeleportAndItems(boolean)` | 2 |
| `isTransportUsableWithLeaguesContext(...)` | 2 |
| `isUseSpiritTrees()` | 1 |

**`Transport`** (instance getters/setters):

| Member | Uses | | Member | Uses |
|---|---|---|---|---|
| `getDestination()` | 96 | | `getName()` | 11 |
| `getDisplayInfo()` | 89 | | `getCurrencyAmount()` | 9 |
| `getType()` | 85 | | `getCurrencyName()` | 7 |
| `getOrigin()` | 48 | | `setItemIdRequirements(...)` | 3 |
| `getObjectId()` | 20 | | `isMembers()` | 2 |
| `getAction()` | 18 | | `getMaxWildernessLevel()` | 2 |
| `getItemIdRequirements()` | 14 | | `setName(String)` | 1 |
| | | | `distanceTo(...)` | 1 |

**`TransportType`** (enum): all 20 constants referenced (`AGILITY_SHORTCUT`, `BOAT`, `CANOE`, `CHARTER_SHIP`, `FAIRY_RING`, `GNOME_GLIDER`, `MAGIC_CARPET`, `MINECART`, `NPC`, `POH`, `QUETZAL`, `SEASONAL_TRANSPORT`, `SHIP`, `SPIRIT_TREE`, `TELEPORTATION_ITEM`, `TELEPORTATION_MINIGAME`, `TELEPORTATION_PORTAL`, `TELEPORTATION_SPELL`, `TRANSPORT`, `WILDERNESS_OBELISK`) plus `isTeleport()` and `valueOf(...)`.

**`WorldPointUtil`** (static): `packWorldPoint(...)` ×19, `unpackWorldPoint(...)` ×2, `undergroundAwareDistance(...)` ×2. (Note: also exposes `fromLocalInstance` internally — not called by consumers.)

**`CollisionMap`**: only the static field `CollisionMap.ignoreCollisionPacked` (packed-point set), referenced once in `Rs2Tile`. No instance methods called externally.

**Caveat:** counts include `Rs2Walker` (the largest consumer). `Transport` getter counts are call-sites, not distinct code paths. The `Transport`/`TransportType` value types and `WorldPointUtil` static helpers are cheap to keep as-is behind the facade (they are effectively pure data / pure functions); the facade's real job is wrapping the **mutable plugin state** (`Pathfinder`/`PathfinderConfig`/marker/future/mutex) enumerated in the contract table above.

### Consumers to migrate (24 files)
`util/walker/*` (Rs2Walker + lifecycle/awaits/banking/shared), `util/bank/Rs2Bank`, `util/depositbox/Rs2DepositBox`, `util/skills/slayer/Rs2Slayer`, `util/poh/*`, `util/leaguetransport/*`, `util/tile/Rs2Tile`, `util/reachable/Rs2Reachable`, `util/npc/Rs2NpcManager`, `questhelper/QuestScript`, `breakhandler/**`, `Script.java`.

### Staged plan (each stage compiles + walker still runs before the next)
1. ✅ **DONE** (`2119eb9e77`) — **Catalogue** the full external surface. See "Stage 1 catalogue" above.
2. ✅ **DONE** (`e52b781575`) — **Introduce `Rs2PathApi`** at `util/walker/Rs2PathApi.java`, thin 1:1 delegation, no behaviour change.
3. ✅ **DONE** — **Migrate consumers** to `Rs2PathApi`, package by package, walker last:
   - `3a` (`eb0e274d43`): leaf consumers — `Script`, `QuestScript`, `Rs2Bank`, `Rs2DepositBox`, `Rs2NpcManager`, `Rs2Slayer`, `Rs2LeaguesTransport`.
   - `3b` (`6ee086b835`): walker helpers — `Rs2WalkerLifecycleRuntime`, `Rs2WalkerBankingPlanner`.
   - `3c` (`e6e7f0c1fc`): `Rs2Walker` itself (77 refs). Verified: `:client:compileJava` clean + 73 shortestpath tests green (Core 43, Tier1 regression 18, PathSmoother 6, TransportType 4, +2).
   - Remaining `ShortestPathPlugin` references repo-wide are plugin **identity** only (`MicrobotPluginChoice`'s `.class`, `Microbot`'s `getSimpleName()` string check) — intentionally not routed through the facade. **100% of plugin-state access now goes through `Rs2PathApi`.**
4. ⬜ **NEXT** — **Backport behind the facade** — close confirmed selective feature/data gaps (currently the missing home-teleport variants) while keeping consumers untouched; track #10/#12/#30 separately as Microbot-original work.
5. ✅ **DONE (re-baseline)** — added the `skretzo` remote, fetched, and re-baselined against upstream HEAD `7e7e5bf94b` (122 commits + major refactor past `07fca57`). Findings in `UPSTREAM_COMPARISON.md` "Re-baseline 2026-07-20": #2 wilderness is byte-identical to upstream `WildernessChecker` (DONE); #3 exposed a concrete POH coverage gap that was subsequently backfilled (DONE); #11 is a stale premise; #10/#12 have no upstream counterpart (Microbot-original); and #30 has no confirmed upstream implementation. Upstream is now architecturally distinct — only selective feature/data backports are viable, which the facade enables.

### Guardrails
- **No walker edits** until the facade exists and the contract is frozen.
- Keep every `ShortestPathPlugin` member above **binary-compatible** through the migration (the facade delegates; it does not replace them until all consumers move).
- Validate each stage with `./gradlew :client:compileJava` and the existing `ShortestPathTier1RegressionTest` / `PathfinderBenchmarkTest`.
