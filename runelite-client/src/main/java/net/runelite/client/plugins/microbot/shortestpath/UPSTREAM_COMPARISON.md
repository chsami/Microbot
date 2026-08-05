# Shortest Path: Upstream vs Microbot Comparison

Comparison of [Skretzo/shortest-path](https://github.com/Skretzo/shortest-path) (upstream) against the Microbot fork.
Original baseline: `07fca57` ("Data fixes and minor cleanups (#400)"). **Everything below the "Re-baseline" section refers to that old baseline and is partly superseded — read the re-baseline first.**

The maintained plan and ownership boundary are in `docs/walker-roadmap.md`. The machine-readable
baseline is `scripts/shortest-path-upstream-baseline.json` and can be checked with
`scripts/check-shortest-path-upstream.py`.

---

## Re-baseline 2026-08-05 → upstream `ff8e961b32`

Upstream moved by two collision-map commits since the 2026-07-20 review. The exact reviewed commit and
resource blobs are now recorded outside this narrative document so drift is machine-detectable.

- Imported upstream `collision-map.zip` (`sha256:3a99d42fec10e12dbda96bbaae45b354d8e2270c4c1a453d033e95b7da2670d2`).
  The map grew from 2,724 to 2,726 regions. Before import, the candidate passed `ShortestPathCoreTest`,
  `WalkerRouteCorpusTest` and `PathfinderBenchmarkTest` in an isolated worktree.
- Closed the home-teleport coverage gap with Edgeville, Lunar and Arceuus destinations. Microbot uses one
  semantic row per spellbook and intentionally rejects upstream's animation-duration variants because a
  display/animation setting must not gate planner availability.
- Added `scripts/compare-shortest-path-transports.py` plus an exact reviewed semantic-debt baseline. The
  comparator matches named network endpoints when boarding/landing coordinates differ, compares fares and
  requirement dimensions, and makes identity swaps visible through content digests. It is enforced for
  affected pull requests and by the weekly upstream workflow.
- Brought `minecarts.tsv` to exact compared parity. The old local rows charged 20 coins after The Forsaken
  Tower; paid variants now require `7796<11` and free variants require `7796=11`.
- Closed all minigame-teleport identity gaps (Guardians of the Rift and the Varrock/Keldagrim Rat Pits
  landings) and added Total-level, Combat-level and Quest-points gating to the local transport model. The
  Pest Control minigame teleport now actually enforces its upstream 40 Combat requirement.
- Added a structured item-requirement compatibility layer. Numeric upstream expressions retain AND,
  OR and quantity semantics, including upstream's maximum quantity within an OR group; legacy
  `Item IDs` rows remain one OR group. Pathfinder,
  transport-refresh caching, bank planning and Slayer transport preparation now consume that model.
- Imported the direct Max-cape and Quest-point-cape family from the pinned teleport-item artifact:
  16 route identities converged, four previously missing Max-cape destinations were restored, and the
  duplicate Black chinchompa row was removed. Multi-level labels now resolve their leaf item sub-action;
  POH-home variants remain programmatic Microbot behavior.
- Added a pinned symbolic collection adapter for walking tools, grapple gear, keys, passes, currencies
  and cape/apron families. Unknown symbols fail closed. Rune symbols now delegate to Microbot's canonical
  rune, staff and tome catalogs, preserve equipment-provider semantics through the immutable route edge,
  and produce one atomic bank withdrawal/equipment loadout. All 43 shipped non-home spell rows carry the
  reviewed upstream item requirements while retaining the existing Microbot landing coordinates; disputed
  coordinate changes remain deferred for route/live evidence. All 45 River Lum and River Dougne canoe
  routes now have exact compared field parity; the executor chooses the chain-specific map interface and
  route-corpus coverage pins the new western network. Live River Dougne execution remains pending.
- Brought all 28 Quetzal network identities to exact compared parity and added a dedicated semantic
  mapping from upstream `quetzal_whistle.tsv` to Microbot's inline teleport-item rows. All 14 whistle
  destinations retain the current Quetzacalli Gorge landing, Cam Torum map label, canonical unlock
  bitmasks and Twilight's Promise gate. Microbot intentionally records 14 item/consumability differences:
  charged whistles remain consumable while perfected-infinite item `33120` remains available to the
  Inventory (perm) policy, rather than inheriting upstream's family-level `Consumable=T` flag.
- Corrected the executor's wilderness boundary check to the same inclusive maximum used by the planner.
  The old executor-only `+1` admitted a teleport one Wilderness level beyond the planned limit.
- The comparator now ignores a comparable field only when its column is absent from one entire schema.
  This reduced false field drift while preserving exact identity/content digests for real changes.
- Closed the lossless agility-shortcut requirement slice: all twelve reviewed grapple edges now require
  both a crossbow family and a mith grapple, and the Trollheim rope edge carries its rope plus unlock
  varbit in executable fields. Corrected current landings and durations for the Lumbridge-farm fence and
  northern Varlamore rocks; real pathfinder corpus cases select both edges. The comparator now uses
  RuneLite's explicit course-obstacle catalog to classify 114 known course identities without removing
  them from total debt. Three Trollheim climbing-rock ascents moved from generic transports to exact
  boots-gated agility edges while their descents remain generic; a pathfinder case proves the ascent.
  The complete 88-edge Isafdar forest family now matches upstream landings, levels and durations: 66
  unconditional generic rows became Agility shortcuts, 22 missing edges were added and four stale local
  landing variants were removed. The route corpus proves the three-edge dense-forest chain. The other 28
  exact cross-file identities that were still unrestricted generic transports now retain upstream's
  Agility requirements across Brimhaven Dungeon (6), the Lumbridge cellar (2), Karamja rocks (6), Slayer
  Tower (8) and Darkmeyer (6). Catalog tests pin their levels, durations and unlock varbits, and one real
  pathfinder route per family proves graph selection. The semantic comparator now exposes any upstream
  agility identity represented only as a local generic transport and pins that bypass class at zero. The
  two diagonal Darkmeyer approaches that upstream intentionally models both ways remain exactly once as
  generic transports alongside their gated shortcut variants. The current semantic inventory is 6,601
  shared, 1,016 upstream-only and 952 Microbot-only route identities, with 1,379 comparable field drifts;
  agility debt is 231 identities, split into 114 course obstacles and 117 ordinary-world or unresolved
  routes. Live interaction evidence for this slice remains pending.
- Added route-corpus coverage for Draynor's east sewer transition and for planner selection of `SHIP`,
  `NPC` and `BOAT` travel families while retaining Microbot's explicit ship-deck/gangplank model.
- Added twelve intentional Microbot-only Barrows edges absent from the reviewed upstream artifact: six
  exact spade-gated mound digs into the individual crypts and six object-backed crypt exits to
  representative anchors on their matching surface mounds. A dedicated registry capability prevents
  arbitrary object-less `Dig` rows from becoming executable. Static route coverage pins all six pairs and
  rejects every sarcophagus object as a deterministic tunnel edge; the empty crypt is randomized and must
  be observed by a future state-aware executor. Live mound round-trip evidence remains pending.
- Completed the static Laguna Aurorae spirit-tree perimeter from the pinned artifact. Nine object-`26262`
  origins now feed the existing `Travel` executor and the already-present Pandemonium-gated destination;
  route coverage proves the north-west origin selects the network. Spirit-tree shared identities rise from
  145 to 154 and upstream-only debt falls from 11 to the two POH directions already owned by Microbot's
  programmatic POH integration. Tests reject adding those POH routes to the static TSV. A live Laguna
  round trip remains required.
- Closed the executable part of ordinary `transports.tsv` debt. The two Elemental Workshop wall directions
  now use current object `26115`, require the concrete battered key and sit behind a curated collision-edge
  override so the planner cannot walk through the closed wall. The upstream steel-key-ring alternative is
  intentionally stricter locally: ring possession does not prove that the battered key is stored. The
  remaining 15 upstream-only identities are fully classified and digest-pinned (four superseded Piscatoris
  anchors, eight id-less Marim stairs, one interaction-less Daero jump and two unsafe Varrock trellis rows),
  leaving zero unexplained ordinary route identities. A route regression proves battered-key selection and
  key-ring-only rejection; live wall execution remains pending.
- Imported the four genuinely missing Pandemonium ship directions between Port Sarim, Musa Point and the
  island using the reviewed Captain Tobias, Customs officer and Seaman Morris ids/actions. All four retain
  the quest gate and 30-coin fare, resolve to direct terminal travel, and have real-pathfinder selection plus
  fail-closed prerequisite coverage. The remaining six upstream-only ship identities are exact-classified
  representations of Microbot's current Corsair Cove, Ardougne and Void Outpost deck/landing coordinates;
  a live Pandemonium round trip remains pending.
- Added a pinned dual-engine evaluation harness. A declared adapter patch retains upstream's exact selected
  transport object through `NodeGraph`/`PathStep`, avoiding the ambiguous endpoint rematch documented by
  upstream itself. Static, real White Wolf surface-tunnel-surface, same-edge network alternatives and
  bank-disabled/start-at-bank policies agree on reachability, termination, exact selected corpus IDs and
  route cost. A separate-bank detour and four source-aware spell slices (carried/banked raw runes,
  separate staff/tome, and a missing ordinary item) also agree. The corpus explicitly pins one reviewed, game-semantic
  divergence: upstream rejects one Twinflame staff as the provider for both fire and water clauses because
  its requirement evaluator consumes the staff substitution after the first clause; Microbot reuses the
  same selected combination staff across every compatible clause. The 16-case gate requires 15 exact
  parity results plus this one documented expected divergence, and fails if either an unexpected difference
  appears or the reviewed difference disappears. The local adapter performs several workflow searches while
  upstream carries bank state through one graph, so node/time metrics for that case are diagnostic rather
  than core-performance parity. The gate runs the local core, the production-packaged pinned upstream adapter
  and an independently compiled temporary checkout. It requires the two upstream executions to agree on all
  semantic result fields unless the corpus already declares an input-policy divergence.
- Converted the local side of that harness and synchronous production planning to one `Rs2RoutePlanner`
  boundary. `Rs2PathApi` now resolves an immutable policy snapshot before engine dispatch, including bank,
  Wilderness, dangerous-NPC, teleport, membership, live-collision, cutoff, enabled-family and restriction
  state; unresolved requests fail instead of consulting mutable globals. Exact local transport identity is
  retained only as an opaque package-private payload on the immutable edge. Microbot executor admission and
  zero-rune home-teleport capability are injected at plugin composition through `TransportPlanningPolicy`,
  and the pathfinder core is CI-guarded against importing the executor registry. ADR 0006 established the
  production-capable upstream shadow adapter as the next milestone before further broad family imports.
- Packaged the reviewed non-UI upstream core in an isolated source set and added a default-off production
  adapter. Both engines consume the same resolved request and immutable planning snapshot; upstream maps a
  selected transport back to the exact already-admitted Microbot edge by object identity. Shadow execution is
  bounded to one worker and one queued request, never publishes an execution route, rejects stale active-route
  generations and covers synchronous queries, ordinary active walker routes and cave-route selection. The
  facade exposes the latest structured comparison plus aggregate match, divergence, failure, stale, discard
  and exact-route-shape-difference counters. Completed outcomes distinguish ordinary replans from recovery,
  classify explicit bank-workflow legs, retain selected transport executor/type families and count live
  collision only when the overlay answers a search edge. The schema-versioned Agent Server endpoint exposes
  this coordinate-free evidence through `microbot-cli walker shadow`, and
  `evaluate-walker-shadow-evidence.py` enforces recovery, bank-workflow, collision and transport-family
  diversity plus terminal blocking-walk/recovered-arrival outcomes rather than treating enabled settings or
  a matching replan as behavioral evidence.
  Twelve accepted live-shadow sessions now provide 141/141 semantic matches and 71/71 walker arrivals. The
  aggregate closes every F2P live minimum, including 75 active routes, 15 active replans, 11 recovery replans,
  39 underground comparisons, 18 walking-only cave selections and ten explicit item-gated bank-to-target
  comparisons. There is no semantic divergence, planner failure, pending/discarded work, unreachable result or
  exit. Seventy-six exact-shape differences are equal-cost alternatives with matching selected transports;
  retained diagnostics classify them across transport-free replan/recovery, bank/canoe and mixed surface
  slices, with none in the underground comparisons and none associated with a non-arrival. Five clean samples
  from the exact evaluated revision pass the timing gate at a `0.407` upstream/local comparable-suite median
  ratio. Sanitized source snapshots and accepted reports are tracked under
  `docs/evidence/walker/2026-08-05/`. The explicit F2P selector and rollback test are also complete;
  members-policy selection requires separate representative members-world evidence.
  `check-shortest-path-vendored-core.py` pins all source/metadata digests and can prove every undeclared file
  byte-identical to the reviewed checkout.
- Added an explicit `LOCAL`, `SHADOW` and `UPSTREAM_F2P_CANARY` selection state. `LOCAL` remains the default;
  `SHADOW` cannot select a route; and the canary is eligible only for resolved non-members policy. The canary
  keeps active publication in the calculating phase until both candidates finish, selects upstream only for
  a semantic match, and otherwise retains local with separate divergence/failure fallback counters. This is
  conservative containment rather than treating local as a correctness oracle. Exact upstream selections
  are temporarily materialized into the legacy completed-pathfinder view for existing runtime consumers.
  Remove that shell with the local planner after the two-release/1,000-comparison fallback sunset. A live
  F2P-17 underground run made ten upstream selections and ten arrivals without divergence or failure. A
  separate test-only forced-failure run made zero upstream selections, ten local failure fallbacks and ten
  arrivals. This validates the opt-in selector; the default remains local until an F2P release is explicitly
  approved.
- Migrated active destination bank-item discovery to exact immutable `Rs2TransportEdge` values. Fare,
  rune, fairy-ring, purchasable-item and structured AND/OR requirement selection no longer require a
  concrete selected `Transport`; the transitional `LegacyRoutePlan` handoff is removed and CI prevents it
  returning. The deprecated concrete helper remains only as a Hub compatibility API, outside the active
  banking and executor contracts.
- Bound active runtime transport discovery to the exact selected route edge. Completed pathfinders publish
  an immutable, source-identity-checked route snapshot; raw-segment dispatch, ranged classification and
  nearby current-tile recovery no longer rescan all catalog rows at an origin. Immutable edges carry an
  explicit Microbot executor capability, while the exact local concrete object is retained only as an opaque
  package-private payload for behavior-bearing handlers such as POH. Catalog rows without a registered
  executor fail closed before planning. All four home teleports use one exact-name, zero-rune widget
  executor shared by planner capability and runtime dispatch. The 225 directed hot-air-balloon edges now
  use a dedicated exact-destination map executor and observed-landing contract. Static and dual-engine
  selection coverage is green; live evidence proves all edges remain unavailable when station unlocks are
  absent, while a successful flight still needs an unlocked-account run.
- Closed the live Port Sarim/Musa Point terminal-ship incident without changing upstream planning data.
  Current NPC menus expose `Travel` while the reviewed catalog retains destination labels, and current
  travel lands directly on the ground after auto-completing the catalogued deck/gangplank pair. The executor
  now preserves the configured action first, applies a conservative `SHIP`-only `Travel` fallback, limits an
  exact selected edge to one interaction per top-level walk, and accepts only the immediate planned landing
  continuation. Live walks in both directions produced one click, an observed handoff and no timeout.
- Tightened the Microbot-owned Al Kharid toll executor after live investigation exposed both a false landing
  and stale object-id collision. The raw door scanner now defers the catalog edge to one selected-transport
  owner; that owner resolves the transformed live Gate by configured action and exact edge geometry, with no
  historical-id fallback. Completion requires the exact opposite-side destination and an unresolved
  interaction bubbles back without a handoff. Rebuilt live walks in both directions selected the Gate,
  issued one toll interaction, reached the exact selected landing and emitted the expected handoff without
  raw-obstacle interception or timeout.
- Removed the local `Open;Manhole;881` Varrock Sewers row after a live walk proved that it modeled cover
  preparation as though it were a surface-to-underground transition. The reviewed upstream catalog contains
  only `Climb-down;Manhole;882`; Microbot's closed-object handling can still open `881`, refind `882` and
  execute that exact edge. A static catalog regression pins the distinction, and five consecutive F2P live
  walks arrived on the exact sewer tile without a trapdoor timeout or route stall.
- Replaced the terminal-travel type assumption with a row-level execution contract. `SHIP`, `NPC` and
  `BOAT` describe journeys whose configured target may be an NPC or scene object; immutable route edges now
  carry a direct or dialogue-destination mode in addition to `TERMINAL_TRAVEL`. Semantic live matching
  admits the direct Al Kharid/Tempoross `Board;Ferry` edge without trusting its historical object id.
  Forty-one multi-step terminal rows remain deliberately fail-closed and are pinned by interaction group
  until their destination-selection flows are implemented. The Ferry is statically selected by the route
  corpus, while successful members-world outbound/reverse execution remains pending; the rebuilt free-world
  run correctly admitted zero boat edges and therefore did not produce false runtime evidence.
- The July architectural conclusion still holds: use upstream as a tracked planner/data reference and
  retain Microbot ownership of runtime execution and automation policy.
- Advanced the production planner boundary without selecting a replacement engine: synchronous walker
  queries and active route restart/cancellation now enter through immutable `Rs2RouteRequest` policy and
  `Rs2PathApi`. Configuration refresh, cave walking-only selection, executor ownership and local
  `Pathfinder` construction are confined to that seam, with CI rejecting reintroduction in the walker and
  lifecycle packages. NPC target selection and bank-route comparison also use request-scoped policy; bank
  diagnostics retain the exact typed edges chosen by search rather than rematching the mutable catalog.
  The next migration slice is also complete: a generation-tagged immutable active-route status now serves
  walker progress/recovery, Quest Helper and obstacle consumers, while CI rejects concrete active planner
  reads outside the facade. Slayer bank-item preparation is request-scoped and exposes an exact immutable
  edge replacement for its deprecated concrete-transport helper. Explicit walker policy/config operations
  are now named facade calls, and CI rejects mutable configuration in the walker. Leagues cache invalidation
  also enters through the facade, while its catalog injection receives a narrow transport-usability
  predicate instead of `PathfinderConfig`; no production consumer outside the facade or shortest-path
  implementation imports the mutable config. Concrete transport payloads and overlay ownership are the next
  boundary decision, not another blanket type migration. The first classified payload slice is now complete:
  hot-air-balloon execution consumes the immutable selected edge, recovery and obstacle code ask only for
  transport-origin presence, door catalog classification uses immutable edge views, and bank-route distance
  scoring follows the exact ordered route steps rather than rematching a same-endpoint catalog entry.
  `TransportRouteAnalysis` now retains every compared leg's exact steps, and withdrawal planning consumes
  the selected bank-to-target edges instead of running a second search from the pre-bank location. CI
  prohibits concrete transport imports from migrated packages and rejects that compare-then-replan pattern.

Next work is to approve the F2P-scoped release decision and collect representative members-only evidence before
any members-policy selection. Broad family-by-family transport convergence remains paused except for
incident-driven fixes.
Runtime interaction changes still require live harness evidence. Do not use the old priority list at the
bottom of this historical document as the active queue.

The opt-in F2P harness exports the endpoint's coordinate-free schema-v2 snapshot and rejects empty, unsettled,
divergent or failed ordinary shadow runs. The full accepted aggregate now covers the required surface,
recovery, bank, teleport, network and terminal-travel mix. The fixed Varrock manhole route also serves as the
selection/rollback release case described above.

---

## Re-baseline 2026-07-20 → upstream `7e7e5bf94b`

Added the `skretzo` remote and fetched. Current upstream HEAD (`skretzo/master`) is **`7e7e5bf94b`** ("Update collision map") — **122 commits ahead** of the pinned `07fca57`, including a **major architectural refactor**. The `07fca57`-based punch list below is now largely obsolete; use this section as the current map.

### Upstream has restructured (the fork is now architecturally distinct)

Current upstream is no longer a monolith. New/renamed since `07fca57`:

- **`transport/` package** with a real parser pipeline: `TransportLoader`, `TransportType`, `TransportTypeConfig`, `LoadInterner`, `BankPickupRequirements`, and `transport/parser/*` (`TsvParser`, `FieldParser`, `TransportRecord`, `WorldPointParser`, `QuestParser`, `SkillRequirementParser`, `VarRequirementParser`, …). Microbot still parses TSV inline in the `Transport` constructor.
- **Model classes**: `Destination`, `DestinationRequirements`, `ItemVariations`, `JewelleryBoxTier`, `transport/requirement/{ItemRequirement,TransportItems}`.
- **Pathfinder internals**: `NodeGraph`, `IntDeque`, `IntMinHeap`, `PathStep`, `PathfinderResult`, `PathTerminationReason`, `AbstractNodeKind`, `TransportAvailability`, `PrimitiveIntList`, and a standalone **`WildernessChecker`**. Microbot still uses the `Node`/`Pathfinder` design (with its own A*, bidirectional search, smoothing, packed-int `Node`).
- **`leagues/` package** (`LeagueModeState`, `LeagueRegion`, `LeagueRegionChecker`) and **`PendingTask`** (deferred post-login refresh).
- Resources moved under `src/main/resources/transports/`.

**Implication:** a git merge is impossible (unrelated histories, different packages). Even *file-level* backports are now hard — upstream's logic lives in a different structure. Only **selective feature/data backports** are viable, and the Stage 1–3 **facade (`Rs2PathApi`)** is what makes them safe (consumers no longer see these internals). See `WEBWALKER_IMPROVEMENT_PLAN.md` "Facade migration".

### Open-item status re-checked against real current upstream source

| Item | Verdict vs `7e7e5bf94b` |
|---|---|
| **#2 wilderness boundaries** | ✅ **DONE / byte-identical.** Upstream `pathfinder/WildernessChecker.java` uses the exact same `WorldArea`s as Microbot `PathfinderConfig.java:41-46` (above-ground `2944,3525,448,448`; underground `2944,9918,518,458`; `LEVEL_20` 3680/10075; `LEVEL_30` 3760/10155; same `NOT_WILDERNESS_*`). No action. |
| **#3 POH** | ✅ **DONE (2026-07-20).** Diffed upstream `teleportation_portals_poh.tsv` (137 rows / 50 dests) and the game's 42 `POH_PORTAL_MAG_*` portal-chamber destinations against Microbot's `PohPortal` enum. 12 were missing — Barbarian Outpost, Fishing Guild, Port Khazard, Marim, Civitas illa Fortis, Ourania, Cemetery, Dareeyak, Trollheim, Lassar, Ice Plateau, Paddewwa — and were **backfilled** with upstream arrival tiles + compiler-verified MAG/MARBLE/TEAK object ids (`PohPortal` 28→40). They flow into the transport graph automatically via `findPortalsInPoh()` → `PohPanel` → `PohTransport`. Compiles clean; shortestpath regression suite green. |
| **#10 reachability prune**, **#12 SplitFlagMap LRU eviction** | ❌ **No upstream counterpart** — not present in upstream `PathfinderConfig`/`SplitFlagMap`. These are **Microbot-original** optimization ideas from the webwalker audit, *not* upstream backports. Treat as independent perf work, not re-baseline items. |
| **#30 one-way transports** | ❓ Not clearly present upstream on re-check; deprioritize (no confirmed upstream source to backport from). |
| **#11 `PrimitiveIntList`** | Upstream has `PrimitiveIntList` for its *output* paths, but Microbot's `Node` is already packed-int; the only `List<WorldPoint>` left is the `getPath()` facade boundary (see `WEBWALKER_IMPROVEMENT_PLAN.md` #11). Not a behind-facade change. |

### Transport data files: upstream vs Microbot (2026-07-20)

Microbot loads 22 TSVs (see `Transport.java`). File-level diff vs `skretzo/master:src/main/resources/transports/`:

- **Upstream-only files** — `teleportation_portals_poh.tsv` (✅ now programmatic, see #3), `teleportation_boxes.tsv` (✅ POH mounted items — `Mounted{Glory,Xerics,Digsite,Mythical}`), `quetzal_whistle.tsv` (✅ inline in `teleportation_items.tsv`), `teleportation_spells_home.tsv` (⚠️ Microbot has 2 home rows in `teleportation_spells.tsv` vs upstream's 16 variants).
- **Microbot-only files** (intentional): `blocked_edges.tsv`, `dangerous_tiles.tsv`, `npcs.tsv`, `restrictions.tsv`.

**`transports.tsv` content diff** (present in both; upstream 5168 rows / Microbot 4949): compared by origin→destination identity.

- Upstream 5140 distinct O→D pairs vs Microbot 4915. **778 upstream-only**, 553 Microbot-only.
- Of the 778: only **14 named** transports; **764 anonymous route objects** (372 Climb, 137 Ladder, 123 Stairs, 43 Staircase, gates/doors/caves…).
- **Not drift:** 737 distinct origins in the 778, of which only 24 overlap a Microbot origin — **713 are genuinely new origin tiles**. Concentrated central 2500–2999 (399), Varlamore/Kebos 1500–1999 (133), Misthalin 3000–3499 (131).
- **Verdict:** real coverage gap (new route objects + new areas Microbot's baseline predates).
- **✅ DONE (2026-07-20):** imported **768** of the 778 (`transports.tsv` 4949→5717), scripted + validated. Conversion: action `space`→`;` form; inserted `Currency`/`isMembers` columns; upstream named item variations + `|` OR-sets → Microbot numeric id-sets (`AXE`/`MACHETE`/`PICKAXE`/`ROPE` via `ItemVariations`→`ItemID`; `COINS=N`→Currency); fixed upstream typo `Shadows`→`Shadow of the Storm` (else the `Quest` gate silently drops). The original import excluded 15 id-less rows and **2 Garden of Tranquillity trellis rows (obj 2149)**. On 2026-08-05 the two Elemental Workshop wall directions were recovered with current object `26115`; the remaining 15 identities are now explicitly classified rather than unexplained. The trellis remains intentionally omitted and is caught by `testVarrockSewerPathAvoidsDisabledPalaceTrellisShortcut`. Paired with the **updated collision map** (`collision-map.zip` 2663→2724 regions) so new-area routes have collision coverage. Validated: shortestpath suite green (73 tests, incl. real cross-region pathfinding).
- **Caveat:** imported members-area routes carry an empty `isMembers` (upstream lacked that column) — same as upstream's own behaviour; harmless since F2P can't reach those origins anyway.

### Recommended next Stage-4 target
✅ Completed 2026-08-04. Edgeville, Lunar and Arceuus were added with spellbook, quest, membership and
cooldown requirements. Animation-setting duplicates were intentionally not imported. See the newer
re-baseline above for the next work.

---

## Critical Bug Fixes Missing Locally

> ⚠️ **HISTORICAL (2026-04-06). All six below are now FIXED** — see the "Re-baseline" section at the top and `WEBWALKER_IMPROVEMENT_PLAN.md`: #1 rehash `return`→`continue` (done), #2 wilderness boundaries (done, byte-identical to upstream `WildernessChecker`), #3 `growBucket` overflow (done), #4 `onGameStateChanged` refresh (done), #5 minimap null guard (done), #6 boat world-view `fromLocalInstance` (done). Kept for provenance only.

### 1. `PrimitiveIntHashMap.rehash()` — Data Loss Bug

**File:** `PrimitiveIntHashMap.java:210`
**Severity:** Critical

During rehash, when a target bucket overflows, the local code executes `return;` which **abandons all remaining entries that haven't been rehashed yet**. Upstream fixes this with `continue;`, properly completing the rehash of all entries.

This can silently drop transports or path nodes from the hash map during map growth, causing pathfinding to miss valid routes.

```java
// LOCAL (BUGGY)
if (bInd >= newBucket.length) {
    growBucket(bucketIndex)[newBucket.length] = oldBucket[ind];
    return; // <-- ABANDONS remaining entries
}

// UPSTREAM (FIXED)
if (bInd >= newBucket.length) {
    IntNode<V>[] grown = growBucket(bucketIndex);
    grown[newBucket.length] = oldBucket[ind];
    continue; // <-- Continues rehashing remaining entries
}
```

### 2. Wilderness Boundaries Are Wrong

**Files:** `Pathfinder.java`, `PathfinderConfig.java`
**Severity:** High

Local uses wilderness levels 19/29 with Y-coordinates shifted 8 tiles south compared to upstream's levels 20/30. This means teleports unlock at the wrong wilderness depths.

| Area | Upstream | Local |
|------|----------|-------|
| Above-ground wilderness start | Y=3525 | Y=3523 (2 tiles too far south) |
| Underground wilderness dimensions | 518×458 | 320×442 (198 tiles narrower, 16 tiles shorter) |
| Level 20 above ground | Y=3680 | Y=3672 (named "Level_19") |
| Level 30 above ground | Y=3760 | Y=3752 (named "Level_29") |
| Level 20 underground | Y=10075 | Y=10067 (named "Level_19") |
| Level 30 underground | Y=10155 | Y=10147 (named "Level_29") |

The undersized underground wilderness area (320×442 vs 518×458) means the pathfinder fails to recognize large portions of underground wilderness, potentially routing through it when "avoid wilderness" is enabled.

### 3. `PrimitiveIntHashMap.growBucket()` — Integer Overflow

**File:** `PrimitiveIntHashMap.java`
**Severity:** Medium

Local uses unchecked `oldBucket.length * 2` which can overflow for large buckets. Upstream guards with `Math.min(oldBucket.length, Integer.MAX_VALUE / 2 - 4) * 2`.

### 4. No `onGameStateChanged` Handler

**File:** `ShortestPathPlugin.java`
**Severity:** Medium

Upstream tracks game state transitions and uses `PendingTask` to defer `pathfinderConfig::refresh` until the next game tick after login. Local has no such handler, meaning the pathfinder config (teleports, transports, quest states) is not refreshed after re-logging.

### 5. `getMinimapClipArea()` Missing Null Guard

**File:** `ShortestPathPlugin.java`
**Severity:** Low

Upstream adds a null/hidden check on the minimap widget after cache invalidation. Local is missing this second null check, which can cause NPE when the minimap widget becomes null between the bounds check and the sprite logic.

### 6. Boat World View Not Handled in `WorldPointUtil`

**File:** `WorldPointUtil.java`
**Severity:** Medium

Upstream has `fromLocalInstance(Client client, Player localPlayer)` which handles boat world views — when the player is on a boat (`worldView != TOPLEVEL`), it resolves the correct `WorldEntity` location. Local does not have this, meaning player location is wrong on boats.

---

## Missing Features / Upstream Enrichments

### Transport System

> ⚠️ **This table was the 2026-04-06 (`07fca57`) snapshot and was badly stale. Re-verified against the live tree 2026-07-20 — most rows were already present.** Corrected `Local` column below.

| Feature | Upstream | Local (verified 2026-07-20) |
|---------|----------|-------|
| Hot Air Balloons | Yes | ✅ Yes — `hot_air_balloons.tsv` |
| Magic Mushtrees | Yes | ✅ Yes — `magic_mushtrees.tsv` |
| Seasonal Transports | Yes | ✅ Yes — `seasonal_transports.tsv` |
| Teleportation Boxes | Yes | ✅ Yes — POH mounted items (`Mounted{Glory,Xerics,Digsite,Mythical}`) |
| POH Teleportation Portals | Yes | ✅ Yes — `PohPortal` (40 dests after 2026-07-20 backfill) |
| Pendant of Ates (Kastori, Nemus) | Yes | ✅ Yes — in `teleportation_items.tsv` |
| Separate Kharedst's Memoir / Book of the Dead | Yes | ✅ Yes — in `teleportation_items.tsv` |
| Great Conch fairy ring (CJQ) | Yes | ✅ Yes — in `fairy_rings.tsv` |
| Cowbell Amulet | Yes | ✅ Yes — `teleportation_items.tsv` (item 33104), backfilled 2026-07-20 |
| Sailors' Amulet | Yes | ✅ Yes — `teleportation_items.tsv` (item 32399, 3 dests), backfilled 2026-07-20 |
| Laguna Aurorae spirit tree | Yes | ✅ Yes — `spirit_trees.tsv` (Pandemonium-gated dest), backfilled 2026-07-20 |
| **Bank-visit teleport discovery** | Yes | ❌ **Missing** — no `onItemContainerChanged` bank-knowledge tracking |
| **Per-transport-type cost tuning** | Yes (`TransportTypeConfig`) | ❌ **Missing** |
| **Currency *threshold*** (avoid expensive when low on gold) | Yes | ❌ **Missing** — note: currency *requirement* filtering (can-I-afford) **is** present (`PathfinderConfig.java:879`) |
| Transport data as TSV with parser package | Yes | Architectural difference (Microbot parses inline in `Transport`) — not a feature gap |

**Net still-missing (verified 2026-07-20):** bank-visit teleport discovery, per-transport-type cost tuning, currency-threshold cost avoidance. (Cowbell/Sailors'/Laguna Aurorae backfilled; everything else in the original table was already present.) The three remaining are feature work (config + event handling), not data adds.

### Player-Owned House (POH)

Upstream has granular POH support:
- `usePohFairyRing` — use fairy ring inside POH
- `usePohSpiritTree` — use spirit tree inside POH
- `usePohObelisk` — use obelisk inside POH
- `usePohMountedItems` — glory, xeric's talisman, digsite pendant, mythical cape
- `pohJewelleryBoxTier` — None / Basic / Fancy / Ornate filtering
- `useTeleportationPortalsPoh` — use portals in POH
- POH tile skipping in overlays (no collision data in POH)
- POH exit info display ("Nexus: Varrock", "Fairy Ring CIR", "Jewelry Box: Duel Arena")
- POH transport origin remapping to landing tile `(1923, 5709, 0)`

> ⚠️ **Stale (2026-04-06).** Re-verified 2026-07-20: Microbot's `PohPanel` now has granular sub-panels — `portalPanel` (`PohPortal`), `nexusPanel` (`NexusPortal`), `checkboxPanel` (fairy ring / spirit tree / obelisk toggles), and `jewelleryBoxPanel` — plus `Mounted{Glory,Xerics,Digsite,Mythical}`. Most of the "granular POH" list above is present; the single-`usePoh`-toggle description is obsolete.

### Pathfinding & Performance

| Feature | Upstream | Local |
|---------|----------|-------|
| `PrimitiveIntList` for paths | Yes (zero-alloc packed ints) | No (`List<WorldPoint>`, boxing overhead) |
| Packed transport map lookups | Yes (`getTransportsPacked()`) | No (unpacks to `WorldPoint` per node) |
| Packed coordinate overlays | Yes (separate X/Y int methods) | No (`Point` object allocation per tile) |
| Path shown during calculation | Yes (`colourPathCalculating`) | No (nothing shown until done) |
| Dynamic minimap tile scaling | Yes (`client.getMinimapZoom()`) | No (hardcoded 2×2 pixels) |

### Plugin UX Features

| Feature | Upstream | Local |
|---------|----------|-------|
| Fairy ring panel auto-scroll | Yes — scrolls to target code, highlights green, prepends "(Shortest Path)" | No |
| "Find closest" map menu | Yes — right-click map icons to pathfind to nearest matching destination | No |
| Unused targets overlay | Yes — draws alternate targets in calculating color | No |
| Transport debug messages | Yes — `postTransports` config posts `PluginMessage` with transport details | No |
| `onConfigChanged` cache refresh | Yes — calls `cacheConfigValues()` on every config change | No (only at startup) |
| Bank container tracking | Yes — `onItemContainerChanged` updates pathfinder bank knowledge | No |
| Path recalculation on game tick | Yes — checks if player deviated, recalculates or cancels | No (delegated to `Rs2Walker`) |

### Config Default Differences

| Setting | Upstream Default | Local Default |
|---------|-----------------|---------------|
| `useCanoes` | `false` | `true` |
| `useCharterShips` | `false` | `true` |
| `useTeleportationItems` | `INVENTORY_NON_CONSUMABLE` | `INVENTORY` |
| `useTeleportationMinigames` | `true` | `false` |
| `showTileCounter` | `DISABLED` | `REMAINING` |

### Dedicated WildernessChecker Class

Upstream extracts all wilderness boundary logic into a standalone `WildernessChecker.java` with unit tests (`WildernessCheckerTest.java`). Local inlines these checks in `PathfinderConfig.java` without tests.

---

## Microbot-Specific Additions (Not in Upstream)

These are intentional local customizations for the automation framework:

- `ShortestPathScript.java` — Script thread for automated walking
- `ETAOverlayPanel.java` — ETA overlay showing estimated time to arrival
- `MinimapOverlay.java` — Custom minimap overlay
- `Rs2Walker` integration — `setConfig()`, `exit()`, walker state machine
- `Restriction` system — Block tiles based on quests/varbits/skills/items
- `ignoreCollision` list — Hardcoded world points where collision is bypassed
- TOA puzzle room handling — Region 14162 ground object avoidance
- `ignoreTeleportAndItems` flag — Disable teleports for cave navigation
- `distanceBeforeUsingTeleport` — Minimum distance before considering teleports
- `filterSimilarTransports()` — Remove consumable items when non-consumable alternatives exist nearby
- `randomizeFinalTile` — Randomize the exact destination tile
- Spirit tree individual destination toggles
- `useNpcs` transport type
- Navigation panel (`ShortestPathPanel.java`)
- POH panel (`PohPanel.java`)
- Various `Rs2*` utility integrations (Rs2Magic rune checking, Rs2Player quest states, etc.)
- Gradient path coloring (red→green based on position)
- `CTRL + X` to stop webwalker
- `KeyListener` for keyboard shortcuts

---

## Recommended Actions (Priority Order)

1. **Fix `PrimitiveIntHashMap.rehash()` data loss** — Change `return;` to `continue;` at line 210. One-line fix, critical impact.
2. **Update wilderness boundaries** — Align area definitions with upstream's `WildernessChecker`. Fixes avoid-wilderness and teleport-level logic.
3. **Add `growBucket()` overflow guard** — Prevent integer overflow on large bucket arrays.
4. **Add `onGameStateChanged` handler** — Refresh pathfinder config after re-login.
5. **Add boat world view support** — Fix player location on boats via `WorldPointUtil.fromLocalInstance`.
6. **Backport `PrimitiveIntList`** — Significant performance improvement for pathfinding.
7. **Backport missing transport types** — Hot air balloons, magic mushtrees, seasonal transports, etc.
8. **Backport granular POH config** — Replace single toggle with per-feature POH controls.
9. **Backport fairy ring auto-scroll** — Quality-of-life improvement for manual use.
10. **Backport dynamic minimap scaling** — Fix minimap path rendering at different zoom levels.

---

*Generated: 2026-04-06*
*Upstream ref: Skretzo/shortest-path @ `07fca57`*
