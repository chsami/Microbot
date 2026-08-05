# Walker Roadmap

_Canonical plan. Last reviewed: 2026-08-05._

## Direction

Microbot is not a source fork of `Skretzo/shortest-path`, but it must not drift from it silently.
The ownership boundary is:

- **Shortest Path upstream:** primary reviewed external reference for static collision and
  transport/teleport semantics, and the maintenance-preferred candidate planner rather than merely a
  source of occasional fixes. This preference does not predetermine the production engine, and upstream
  data is not automatically authoritative for Microbot execution.
- **Microbot:** route execution, live collision, obstacle interaction, recovery, banking and automation
  policy.
- **Import rule:** review upstream changes by behavior and data semantics. Never overwrite Microbot
  executor behavior or local restrictions merely to make the trees look alike.
- **Planner rule:** choose the production engine from correctness, runtime reliability, performance and
  maintenance evidence gathered through the same engine-neutral boundary. New local search algorithms
  require a pinned incident or benchmark rather than divergence for its own sake.

The reviewed upstream commit and resource blobs live in
`scripts/shortest-path-upstream-baseline.json`. Run:

```bash
scripts/check-shortest-path-upstream.py
```

The command exits `2` when upstream moved and prints changes in tracked planner/data scopes. Use
`--json` for other automation or `--allow-drift` for an informational local check. The scheduled
`shortest-path-upstream-drift.yml` workflow runs it weekly and fails visibly when review is due.

The production-packaged planner core has a separate offline integrity gate:

```bash
scripts/check-shortest-path-vendored-core.py
```

This pins all 50 vendored Java files and adapter metadata. Passing `--upstream-checkout` additionally proves
every undeclared source is byte-identical to the exact reviewed checkout, the declared patch/addition lists
are complete, and the bundled license is unchanged.

Transport convergence has a second, stricter contract:

```bash
scripts/compare-shortest-path-transports.py --check-baseline
```

This compares routes by semantic identity, including named network endpoints when Microbot models a
different boarding or landing tile. It compares interaction object, requirements, fare/items,
membership, wilderness limit, consumability and duration separately. The exact reviewed debt is pinned
in `scripts/shortest-path-transport-baseline.json`; counts and content digests make route swaps visible,
not merely changes to the total. Updating that baseline requires reviewing the changed identities and
field dimensions. Intentional differences may carry a rationale bound to their exact field-drift digest;
the check fails when the underlying routes or fields change. Unexplained debt remains unresolved rather
than implicitly accepted, so the baseline is not an allowlist or a claim that remaining differences are correct.
For agility, RuneLite's explicit course-obstacle catalog classifies course traversal separately while
retaining those identities in total upstream-only debt; both the classified set and full set are digest-pinned.

## Current state

### Upstream convergence

- Reviewed upstream: `Skretzo/shortest-path@ff8e961b32120175709df9630ece9468cc11347f`.
- Collision map imported from that commit after the planner core, route corpus and benchmark all
  passed against it. It expands the map from 2,724 to 2,726 regions.
- All four spellbook home teleports are represented in the catalog. Microbot deliberately keeps one row
  per spellbook and does not copy upstream's animation-setting variants; animation display settings must
  not change route availability. They share an exact-name, zero-rune widget executor because RuneLite's
  `MagicAction` catalog only represents Lumbridge. Spellbook, quest, cooldown and Wilderness requirements
  remain catalog/planner gates rather than being weakened to accommodate that UI difference. Lumbridge
  has live round-trip evidence; the alternative-spellbook variants still require direct live evidence.
- Microbot-only `blocked_edges.tsv`, `dangerous_tiles.tsv`, `npcs.tsv` and `restrictions.tsv` remain
  local policy/data overlays.
- At the reviewed commit the semantic inventory has 6,601 shared route identities, 1,016
  upstream-only identities, 952 Microbot-only identities and 1,379 shared identities with at least one
  comparable field difference. These totals include unresolved representation differences and are a
  debt inventory, not a walker-quality score.
- `hot_air_balloons.tsv`, `magic_mushtrees.tsv`, `minecarts.tsv` and `teleportation_levers.tsv` have
  exact compared parity. The Lovakengj minecart import also fixes the old unconditional 20-coin fare:
  paid routes require varbit `7796<11`, while free routes require `7796=11`.
- Minigame teleport identities now fully match upstream: Guardians of the Rift lands inside the Temple
  of the Eye, the Keldagrim Rat Pits destination is restored, and Varrock uses the current landing. The
  transport model now enforces upstream's special Total level, Combat level and Quest-points
  requirements; this closes the previously ignored 40 Combat gate for Pest Control.
- The first teleport-item schema slice is behavior-compatible: explicit requirements preserve AND
  groups, OR alternatives and quantities, including upstream's maximum-quantity rule within an OR
  group, while legacy semicolon rows retain their historical any-item behavior. Pathfinder
  availability, refresh snapshots, bank withdrawal planning and Slayer transport preparation use the
  structured requirements rather than flattening them.
- The symbolic requirement adapter now resolves pinned walking collections for axes, pickaxes,
  machetes, grapple gear, keys, passes, currencies and the upstream cape/apron families. Unsupported
  symbols fail closed. Rune collections delegate to the canonical `Runes`, `Rs2Staff` and `Rs2Tome`
  catalogs and retain raw/combo-rune alternatives separately from equipped staff and offhand providers.
  The owned route edge preserves those source semantics, and bank preparation produces one immutable
  withdrawal-and-equipment loadout so an inventory staff is never mistaken for an active provider.
  All 43 shipped non-home spell rows now carry the reviewed upstream item requirements without changing
  Microbot's existing landing coordinates; coordinate drift remains a separate evidence-gated decision.
- All 45 River Lum and River Dougne canoe routes have exact compared field parity, including the full
  twelve-axe collection and upstream route costs. The executor selects `CanoeMapLum` or
  `CanoeMapDougne` from the reviewed station object family, unknown stations fail closed and the route
  corpus proves selection of the western chain. Live River Dougne execution remains required.
- Barrows now has twelve deliberately Microbot-owned static edges because the reviewed Shortest Path
  artifact has no mound, individual-crypt or tunnel transitions. Six exact mound-to-crypt pairs require
  a spade and use a dedicated inventory-item executor; six staircase edges return each individual crypt
  to a representative anchor on its own surface mound. The exit spawn may vary within the mound, so
  runtime completion uses the existing bounded landing tolerance and then replans from the observed tile.
  The route corpus proves every mound/crypt pair and separately proves that no sarcophagus becomes a
  static tunnel edge. Quest Helper observes which sarcophagus is empty per run, so randomized tunnel
  entry remains fail-closed pending a state-aware executor and live evidence. One mound round trip is
  still required to verify the current staircase action, surface landing envelope and handoff logs.
- The direct Max-cape family and Quest-point cape now match the reviewed upstream identities and
  requirements. Four Max-cape destinations were restored, a duplicate Black chinchompa row was removed,
  and nested display labels resolve to their executable leaf action. POH home variants remain under
  Microbot's programmatic POH ownership.
- The Quetzal network has exact compared parity across all 28 station-side route identities. All 14
  whistle destinations, items and unlocks are represented inline; their 14 reviewed field differences
  are intentional because Microbot splits upstream's family-level consumable row into charged and
  perfected-infinite variants. This keeps item `33120` usable under Inventory (perm). The Gorge landing
  uses the current tile, Cam Torum uses the live map label and unlocks use the canonical varplayer bitmask.
- Laguna Aurorae now has all nine reviewed object-`26262` outbound spirit-tree perimeter origins rather
  than only an inbound destination. A resource-backed route proves the north-west approach can select the
  network and reach the Grand Exchange. Spirit-tree upstream-only debt falls from 11 identities to the two
  POH directions that remain intentionally programmatic; the corpus rejects static POH duplicates. The
  current `Travel` action and `E: Laguna Aurorae` destination label still require a live round trip.
- The ordinary-transport remainder is now fully classified. Two Elemental Workshop wall directions use
  the current object `26115`, a curated collision-edge override and the concrete battered-key requirement;
  the upstream steel-key-ring alternative remains fail-closed because possession of an arbitrary ring does
  not prove that this key is stored on it. The remaining 15 upstream-only identities are digest-pinned as
  four superseded Piscatoris gate anchors, eight id-less Marim staircases, one interaction-less Daero jump
  and two intentionally disabled Varrock Palace trellis routes. Static route coverage proves that the
  battered key selects the wall edge while a key ring alone cannot. Live wall interaction remains pending.
- Four missing Pandemonium ship routes now connect Port Sarim and Musa Point through the exact reviewed
  Captain Tobias, Customs officer and Seaman Morris interactions. Each direction is gated by the
  `Pandemonium` quest and a 30-coin fare, uses the direct terminal-travel executor and is selected only
  when both prerequisites are available. The six remaining upstream-only ship identities are fully
  classified as Microbot's current Corsair Cove, Ardougne and Void Outpost deck/landing representations.
  Static route coverage proves exact edge selection and fail-closed prerequisite behavior; a live
  Pandemonium round trip remains pending.
- The semantic comparator no longer treats a column missing from one schema as an empty requirement.
  This removes false membership drift from item/minigame/portal families without hiding fields that are
  present in both artifacts.
- The first agility-shortcut correctness slice fixes all 13 shared item-field differences: twelve grapple
  edges require both a supported crossbow and a mith grapple, while the Trollheim rope edge retains both
  its rope item and attached-rope varbit. The Lumbridge-farm fence and northern Varlamore rocks use their
  current upstream landings and durations, with route-corpus tests proving the planner selects each edge.
  RuneLite's `Obstacles.OBSTACLE_IDS` authoritatively classifies 114 of the 231 remaining upstream-only
  identities as known course traversal; they stay visible in total debt but are not bulk-import candidates
  for the general walker graph. The other 117 remain ordinary-world or unresolved representation debt.
  Three Trollheim climbing-rock ascents were moved from generic transports to boots-gated agility edges,
  while their unrestricted descents remain generic; route coverage proves the asymmetric policy. The full
  88-edge Isafdar forest family now uses current upstream landings, Agility gates and traversal durations
  instead of unconditional generic transports; this adds 22 missing edges and removes four stale landing
  variants. A real three-obstacle dense-forest route pins the imported chain. The remaining 28 agility
  identities that Microbot had represented as unrestricted generic transports now retain upstream's
  requirements across Brimhaven Dungeon, the Lumbridge cellar, Karamja rocks, Slayer Tower and Darkmeyer.
  Catalog assertions pin all levels, durations and unlock varbits, while one real route per family proves
  planner selection. The comparator separately classifies this cross-file bypass pattern and pins it at
  zero; upstream's two intentional Darkmeyer diagonal generic approaches remain exactly once alongside
  their gated shortcut variants. Live interaction evidence for the corrected fence, rocks, grapple, rope,
  Isafdar and these five converted families remains pending.

### Executor reliability

- Live collision is enabled by default, revision/version keyed and prunes stale captures.
- Door and raw-scene observations are cached to bound repeated scanning.
- `Rs2PathApi` is the compatibility seam around mutable shortest-path plugin state. No in-tree consumer
  outside the facade or shortest-path implementation now constructs or reads `Pathfinder` or imports and
  mutates `PathfinderConfig`; legacy concrete accessors remain public only for binary compatibility.
  Presence-only recovery, obstacle and door-catalog queries now use named facade operations or immutable
  `Rs2TransportEdge` views; the hot-air-balloon handler consumes its owned selected edge directly. Concrete
  `Transport` dependencies still exist in behavior-bearing execution handlers, planner overlays and legacy
  compatibility APIs, so the final stable boundary remains intentionally narrower than the current
  compatibility surface.
- Synchronous planning now dispatches through the engine-neutral `Rs2RoutePlanner` contract. Before an
  engine receives a request, `Rs2PathApi` resolves an immutable `Rs2RoutePolicy` containing bank visibility,
  Wilderness and dangerous-NPC policy, teleport policy, membership, live-collision enablement, cutoff,
  enabled transport families and restricted points. Engines reject unresolved requests instead of reading
  mutable plugin globals. The local dual-engine runner uses this same contract rather than constructing its
  planner directly. Its selected immutable edge retains the exact local source object only as a package-private
  opaque identity, so comparison and execution never rematch ambiguous endpoints.
- Microbot executor capability is injected into `PathfinderConfig` through `TransportPlanningPolicy` at plugin
  composition. The pathfinder core no longer imports `TransportExecutionRegistry`; a boundary regression
  rejects that coupling if it returns. Headless planner tests can admit an explicit synthetic catalog, while
  production continues to fail closed through `Rs2TransportPlanningPolicy`.
- Planner termination is now explicit. The local pathfinder distinguishes target reached, exhausted
  graph, cutoff, cancellation and caught failure; `Rs2RouteResult` exposes the same states through a
  Microbot-owned enum instead of treating every stopped worker as a successful search.
- Completed-path materialization is now keyed to the exact final `Node` identity rather than a shared
  dirty boolean. A live forced-recovery run exposed a race where an older reader could clear the dirty
  flag after a newer best node was published, leaving the point path older than its edge sequence; the
  strict contiguous-route invariant and a focused regression now pin the fix. Blocking walks also treat
  up to two client-thread read timeouts as transient, retain their active route and use a bounded
  collision-free route-index fallback for the failed reachability read. Repeated stalls still fail visibly.
- Local frontier ordering now uses travelled cost rather than the old geometric A* score. A live bank-leg
  shadow run proved that the heuristic was not admissible once long-distance transports were present: the
  local core chose the farther Barbarian Village canoe at cost 130 while the pinned upstream core chose the
  nearer Edgeville canoe at cost 78. A real collision-map/canoe-catalog regression now pins exact edge and
  cost parity. The accepted rerun matched all 49 comparisons, including all ten explicit bank-to-target legs,
  and all six terminal canoe/setup walks arrived.
- Chosen routes now retain typed walking and transport edges. Forward and bidirectional reconstruction
  preserve the exact local `Transport` selected by the search, while `Rs2RouteResult` exposes only
  immutable Microbot-owned edge, transport and item-requirement values. Destination bank-item planning
  consumes those exact edges for fare, rune, fairy-ring, purchasable-item and AND/OR item-requirement
  selection. The transitional `LegacyRoutePlan` handoff has been removed and the boundary checker rejects
  its reintroduction. The old public `Transport`-returning helper remains deprecated for Hub binary
  compatibility; it is not used by the active banking flow or accepted as an executor contract.
- Transport executability is now explicit. `TransportExecutionRegistry` rejects catalog rows that have
  no live Microbot handler before pathfinding, and each immutable `Rs2TransportEdge` records its
  planner-independent `Rs2TransportExecutor`. The exact local `Transport` remains only as an opaque
  package-private handler payload because POH transports carry executable subtype behavior. This boundary
  is recorded in `docs/decisions/adr-0005-walker-transport-execution-boundary.md`.
- The catalog capability audit makes terminal-travel debt explicit instead of treating a whole resource
  family as executable. Every spell teleport and all 225 directed hot-air-balloon edges have an explicit
  executor. Forty-one multi-step terminal rows remain deliberately fail-closed: 30 multi-destination
  Boat/Boaty `Board` rows, six destination-selecting Rowboat rows and five unimplemented `Talk-to` rows.
  The exact interaction groups are test-pinned. Balloon dispatch recognizes the base baskets and
  their six unlocked-station transforms, selects one of the six exact RuneLite map components and requires
  an observed landing before completing the edge. The dual-engine corpus pins Castle Wars-to-Varrock
  selection and cost. Live prerequisite evidence also proves the planner fails closed: on an account with
  no unlocked balloon stations, enabling the feature evaluated all 225 edges and admitted zero. A successful
  map interaction and landing still require direct evidence on an account with an unlocked station.
- Route results now expose planner-independent cost, explored-node, checked-transport and elapsed-time
  metrics. Missing engine measurements remain explicitly unavailable rather than being reported as zero.
- The pinned dual-engine adapter runs the local planner and the reviewed upstream commit from one
  immutable policy corpus. Overland, underground, surface-to-underground-to-surface, unreachable,
  Wilderness-interior, ambiguous network, hot-air-balloon, start-at-bank and separate-bank-detour cases
  plus carried/banked raw-rune, separate staff/tome and missing-ordinary-item spell cases agree on reachability,
  termination, exact selected transport IDs and path cost. One reviewed divergence is pinned explicitly:
  upstream consumes its one allowed staff substitution on the first elemental clause and therefore rejects
  a Twinflame staff for a spell requiring both fire and water; Microbot deliberately reuses the same selected
  combination staff across every clause it provides, matching the executable loadout. The 16-case corpus
  therefore requires 15 exact parity results and one documented expected divergence; an unexpected new
  difference or disappearance of the reviewed difference fails the harness. The production-packaged upstream
  adapter and the independently compiled evaluation checkout both carry the selected `Transport` reference
  through `NodeGraph` into `PathStep`; each maps that exact object to a corpus or execution identity and never
  rematches by endpoints. The harness now fails when the packaged adapter differs semantically from the
  independent pinned build, except for the same explicitly documented input-policy divergence. It records
  node, elapsed and peak-heap diagnostics without treating noisy performance measurements as correctness
  gates. For bank detours the local workflow composes direct and bank-leg searches while upstream tracks
  bank state inside one search; their final route behavior is comparable, but their node/time measurements
  are not planner-core parity.
- `SHIP`, `NPC` and `BOAT` describe the journey, not whether the live target is an NPC or scene object.
  Immutable selected edges therefore carry both `TERMINAL_TRAVEL` and an explicit interaction mode.
  Direct travel resolves the configured name/action against both target kinds near the selected origin;
  Mountain Guide rows use the registered dialogue-destination mode, and unknown flows fail before
  planning. Travel attempts are terminal for the current path scan, and one exact selected
  edge can be clicked at most once per top-level walk invocation. Legacy `SHIP` destination labels retain
  their configured action first and may fall back only to the live `Travel` action; explicit dialogue,
  quick-travel and boat actions are never replaced. Arrival accepts the catalogued landing or its immediate
  planned continuation, covering current ships that auto-complete an obsolete deck/gangplank pair without
  scanning arbitrary later route points. Live Port Sarim-to-Musa Point and reverse walks each selected the
  exact ship edge, issued one `Travel` interaction, observed the current ground landing and completed the
  transport handoff without a timeout.
- The Al Kharid/Tempoross Ferry is the representative direct object-backed `BOAT` row. Static route-corpus
  coverage selects it, transformed-object matching is semantic (`Ferry` + `Board`) and completion requires
  the exact catalog landing. A rebuilt live attempt correctly admitted zero boat rows on a free world;
  the available profile was rejected by the game server when moved to a members world, so no ferry
  interaction occurred and successful outbound/reverse evidence remains pending.
- The Al Kharid toll-gate incident now has one execution owner and a fail-closed completion contract. The
  raw door scanner defers this catalog edge to the selected transport executor. That executor resolves the
  current transformed scene object by `Gate` name, configured action and exact edge geometry instead of
  trusting historical object ids, which collided with an unrelated live object in the current client. A
  ranged click may server-walk before presenting its confirmation, so completion waits for movement/dialogue
  onset and requires the exact selected tile on the opposite side. Unit tests reject adjacent-origin,
  wrong-name, wrong-action and wrong-location false positives. Rebuilt live walks in both directions selected
  the Gate, issued the configured toll action once, reached the exact opposite-side destination and emitted
  the expected handoff without an unresolved timeout or generic-door interception.
- The Varrock Sewers manhole incident removed a Microbot-only `Open;Manhole;881` row that incorrectly
  promoted object-state preparation into a surface-to-underground graph edge. The catalog now retains only
  upstream's traversing `Climb-down;Manhole;882` edge; the existing closed-object mapping may open object
  `881`, refind `882` and then execute the selected transition. A catalog regression rejects reintroducing
  the preparation edge, and the F2P live harness completed five of five exact arrivals at `3237,9858,0`
  without an unresolved trapdoor wait or route stall.
- The route corpus proves selection of each terminal travel family and retains Microbot's explicit
  Port Sarim-to-Musa Point ship-deck/gangplank edge. It also proves both directions through the east
  Draynor sewer transition and the underground corridor to the west sewer.

## Workstreams

### Current priority: finish the F2P selection evidence before expanding breadth

The ship slice is the last opportunistic broad transport-family import before the engine decision. Do not
resume copying families merely to reduce tree drift or add local search algorithms until the following
production-adapter slice is complete. Adapter- or gate-required parity work, incident fixes and reviewed
collision updates continue through the same evidence boundary:

1. **Complete:** both local and reviewed-upstream engines consume the same resolved `Rs2RouteRequest` and
   `Rs2RoutePolicy` contract.
2. **Complete:** the already-filtered executable catalog, live-collision view and Microbot restrictions are
   projected into an engine-neutral immutable planning snapshot; executor admission stays outside both cores.
3. **Complete for the F2P evidence scope:** default-off shadow mode covers synchronous queries, ordinary active
   walker requests and cave-route selection. It compares termination, endpoint, cost and exact selected edge,
   uses a bounded one-worker/one-queued-request executor, invalidates stale route generations and exposes both
   the latest structured comparison and process-lifetime match/divergence/failure/discard counters. The latest
   semantic divergence, planner failure and route-shape-only difference survive route teardown as coordinate-
   free diagnostics. Completed
   outcomes are tagged by active/query/ordinary-replan/recovery origin, surface/underground coordinates, cave
   walking-only selection, selected transport executor/type, explicit bank-workflow leg and live collision
   actually consulted by the local search. Equal-cost alternate walking shapes are counted separately as a
   diagnostic. `microbot-cli walker shadow` exposes this evidence without coordinates, and
   `scripts/evaluate-walker-shadow-evidence.py` enforces stratified recovery, bank, collision and transport
   coverage rather than treating enabled settings as behavioral evidence. Blocking-walk terminal outcomes
   also prove that recovery-triggered routes arrived instead of merely producing a matching replan. Their
   accounting is bound to the logical route's actual comparison eligibility: members-policy walks in the F2P
   canary and all local-only walks cannot inflate execution totals, while shadow and eligible F2P canary
   routes retain their outcome after arrival clears the active route. The F2P
   harness can explicitly enable shadowing, waits for the worker to settle and embeds the same schema-v2
   coordinate-free snapshot in its result. Twelve accepted fresh sessions now aggregate to 141/141 matches
   and 71/71 exact arrivals with no divergence, failure, pending, discarded, unreachable or exit outcome.
   Every live minimum passes: 75 active routes, 15 active replans, 11 recovery replans, 102 surface and 39
   underground comparisons, 18 walking-only cave selections, 97 transport selections, 59 item- or fare-gated
   selections and ten explicit item-gated bank-to-target comparisons. Recovery, bank-workflow, collision,
   arrival, executor-group and four-executor diversity gates all pass. Two completed results were stale after
   deliberate route replacement. Seventy-six equal-cost route-shape differences remain diagnostic: 31 came
   from transport-free surface replan/recovery sessions, 33 from the bank/canoe session and 12 from mixed
   surface active slices; none of the 39 underground comparisons differed and no class coincided with a
   non-arrival or recovery regression. A prior rejected canoe session's one non-
   canoe divergence did not reproduce in the rebuilt 7/7 comparison, 6/6 arrival slice; future terminal
   outcomes retain their mismatch category across teardown.
   The evaluator can combine independently validated fresh-session slices only when their schema
   and pinned engine match, their queues are settled and their process start identities are unique. This
   avoids coupling underground, teleport, network and bank prerequisites into one brittle mega-harness while
   preserving every aggregate threshold.
4. **Complete for F2P selection:** the corpus and focused adapter tests include real underground routes,
   restrictions, an immutable collision override and representative executable-network slices. Repeated
   Varrock Sewers sessions close the underground and walking-only live minimums, and the full 12-session
   aggregate closes every F2P live-shadow threshold. Members-policy selection still requires its own
   representative members-only executor, requirement and network evidence.
5. **Complete for an explicit F2P match-gated release decision:** planner selection is one explicit
   `LOCAL`, `SHADOW` or `UPSTREAM_F2P_CANARY` mode. `LOCAL` remains the default. The canary is eligible only
   for resolved non-members policy, waits for both candidates before atomically publishing the route, and
   selects upstream only on a semantic match. Divergence or planner failure retains local while recording a
   dedicated fallback; local is conservative containment rather than a correctness oracle. Exact upstream
   routes are temporarily materialized through the legacy completed-pathfinder view, which is removed with
   the local core after the fallback sunset. A live F2P-17 underground run made 10/10 upstream selections and
   arrivals with no divergence/failure. A separate test-only forced-failure run made 0 upstream selections,
   10 local failure fallbacks and 10 arrivals. Both runs were repeated after terminal evidence was bound to
   the generation-matched ready route; each retained all 10 eligible arrivals, while members-canary and local
   regression routes leave execution totals unchanged. `evaluate-walker-rollout-evidence.py` now treats the
   normal and forced-failure artifacts as one release gate and accepts a fresh pair only when engine/session,
   route, accounting, selection, fallback, arrival, coverage, failure-opacity and submission-to-ready timing
   invariants all hold. A fresh readiness-aware pair is accepted without failures, shortfalls or warnings:
   normal readiness averaged `304.3 ms` and peaked at `645.7 ms`; forced rollback averaged `260.8 ms` and
   peaked at `675.1 ms`, with average non-search overhead below the `250 ms` limit in both phases. Five clean
   samples from the exact evaluated revision pass every correctness and performance threshold: the local
   comparable-suite median is `1,365.3 ms`, upstream is `555.2 ms`, and the ratio is `0.407`. `LOCAL` remains
   the default until a release explicitly selects the F2P canary. Members-policy selection still requires
   representative members-only evidence.
6. **Next authority milestone:** define and implement the engine-independent candidate validator and
   digest-pinned reviewed-divergence policy in `docs/walker-planner-selection-gate.md`. The current canary is
   intentionally match-gated and therefore does not yet permit a genuinely different upstream route or count
   toward the two-release upstream-authority sunset. Keep `LOCAL` as the default while this contract and its
   rollback telemetry are incomplete.

### 1. Keep planner data current

1. Run the drift checker during walker work and before releases.
2. When upstream moves, classify changed files as collision, transport data or planner logic.
3. For collision changes, run `ShortestPathCoreTest`, `WalkerRouteCorpusTest` and
   `PathfinderBenchmarkTest` against the candidate map before import.
4. For transport changes, convert by semantic identity (origin, destination, requirements), then add a
   regression assertion for every intentionally imported or rejected behavior.
5. Reduce pinned semantic debt only in an adapter-, gate- or incident-driven slice. Within that slice, first
   remove comparison noise, then classify every remaining upstream-only identity as import, programmatic
   equivalent, intentional exclusion or unresolved; do not resume family copying solely to lower the total.
6. Extend the pinned upstream-schema adapter one symbolic family at a time. Rune/staff/tome semantics and
   the shipped spell requirements are complete; remaining unsupported slot/category collections must
   continue to fail closed until their runtime semantics are explicit. Keep Microbot-only policy overlays
   separate so upstream refreshes cannot erase them.
7. Continue scheduled semantic review of upstream transport and teleport changes while the engine decision
   is pending. Import adapter- or incident-relevant behavior with executable coverage, and classify other
   user-visible changes without broad copying merely to reduce drift counts. A baseline update may not
   increase unexplained upstream-only or field-drift debt without a release-note rationale.
8. Treat agility courses separately from ordinary shortcuts. Do not infer general-walker safety from an
   executable object action alone: import course traversal only after a route-level use case proves that
   entering and leaving the sequence cannot strand or loop an ordinary walk.

### 2. Grow the route safety net

The corpus should cover route properties, not exact tile sequences. Existing coverage includes
underground routing and all three terminal-travel families. Continue adding cases for:

- underground entrances and exits, including a surface-to-underground-to-surface journey;
- each terminal-travel family (`SHIP`, `NPC`, `BOAT`);
- newly added map regions and high-change regions from each upstream collision update;
- known incident routes before changing their recovery or collision behavior.

Every corpus case must prove arrival or an intentional partial path and pin the transport/landmark that
makes the route valid.

### 3. Improve executor behavior from incidents

Prioritize reproducible symptoms over a wholesale `Rs2Walker` rewrite:

1. Complete one unlocked hot-air-balloon flight and require logs to show the exact selected edge, one
   basket interaction, the exact destination component and observed arrival. Locked-account fail-closed
   evidence is complete; do not infer successful UI execution from it.
2. Extend terminal-travel live evidence beyond the completed Port Sarim/Musa Point `SHIP` incident to
   one representative `NPC` and `BOAT` edge. The direct Al Kharid/Tempoross Ferry has static selection,
   semantic object matching and free-world fail-closed evidence, but still needs a members-world round trip.
   The dedicated shadow harness now also proves two outbound and one reverse ship selection: 3/3 exact
   `TERMINAL_TRAVEL` comparisons and 4/4 blocking-walk arrivals. Both directions use one interaction per
   invocation and accept the current landing without a false timeout; static planner and scan-classification
   coverage remains green for all three families.
3. Add a route and live reproduction for Misthalin Mystery door transitions before changing the door
   cascade.
4. Complete the remaining Barrows runtime slice. Static mound digs and individual-crypt exits are
   represented and corpus-pinned; run one live mound round trip, then add randomized tunnel entry only
   through observed empty-sarcophagus state. Do not encode a fixed sarcophagus-to-tunnel edge.
5. Extract a component only when a pinned incident requires changing that behavior; keep the existing
   stateful ordering until its replacement has equivalent harness and live evidence.

### 4. Observability

Retain a compact decision trail for path recalculation, transport attempts, obstacle resolution and
learned collision. A failed live harness run must make the last planned edge, selected transport and
recovery decision discoverable without reproducing under a debugger.

Per-edge transport rejection reasons are TRACE-only because a refresh evaluates every expanded catalog
edge. DEBUG retains aggregate per-type counts and timing, while slow refreshes remain visible at INFO;
verbose diagnostics must not make an otherwise healthy refresh exceed the walker timeout.

### 5. Tighten the planner boundary

Direct `ShortestPathPlugin` state access outside `Rs2PathApi` has been removed and
`scripts/check-shortest-path-boundary.py` enforces that invariant; the plugin class literal used for
plugin selection is the only non-state exception. Continue replacing the compatibility seam's concrete
internals with Microbot-owned operations and immutable values:

1. introduce route request/result values that capture targets and planning policy without exposing
   `PathfinderConfig`; the first slice is complete: immutable `Rs2RouteRequest`/`Rs2RouteResult` values
   and `Rs2PathApi.plan(...)` now own synchronous refresh, search timing and honest-partial results. A fully
   resolved immutable `Rs2RoutePolicy` and `Rs2RoutePlanner` engine interface now sit at dispatch; the local
   engine and local side of the dual-engine harness both use them, and unresolved requests fail before search;
2. move pathfinder creation, refresh, cancellation and executor ownership behind the seam;
   bank discovery, deposit-box discovery and destination transport planning no longer construct
   `Pathfinder` directly, and temporary bank-item policy is serialized on the walker mutex and restored
   inside the API. The synchronous `Rs2Walker` query helpers (`getTotalTiles`, `canReach`, `getWalkPath`,
   multi-target reachability/distance and nearest-accessible-target selection) now use the same immutable
   request/result operation; the boundary checker rejects new local-core construction in that class.
   Bank-aware queries restore both the previous bank policy and the caller's refresh target. Active route
   restart, cancellation, configuration refresh, cave walking-only selection and executor ownership now
   also live behind `Rs2PathApi`; the lifecycle package has no concrete planner/config dependency and the
   boundary checker enforces that state. Cave selection evaluates every requested target rather than the
   iteration order's first target. NPC location selection and direct-vs-bank route comparison no longer
   mutate the shared bank-item flag: each route leg declares its policy in its request, restoration avoids
   a redundant refresh when the policy was already active, and banking diagnostics consume the exact typed
   edges selected by that leg. Active-route consumers outside the shortest-path implementation now read a
   generation-tagged immutable `Rs2ActiveRouteStatus` with explicit absent/calculating/ready phases, copied
   raw and smoothed paths, owned termination and metrics. `Rs2Walker`, Quest Helper and obstacle handling no
   longer import or inspect `Pathfinder`; generation-aware waits cannot accidentally accept a superseded
   calculation. Slayer item preparation no longer flips shared bank policy and has an exact immutable-edge
   replacement for its deprecated concrete-transport API. The boundary checker now rejects direct active
   planner reads everywhere outside the facade, and rejects mutable planner configuration in bank,
   deposit-box, NPC, Slayer, banking, lifecycle, walker and Leagues scopes. The walker's collision
   preflight, dangerous-NPC recovery policy, restriction refresh, learned-edge recording, spirit-tree
   policy, Wilderness classification, teleport-item classification and post-bank inventory-only switch
   are named facade operations rather than configuration reads. Leagues invalidation uses the same seam,
   while its catalog injection receives only a transport-usability predicate from the local engine instead
   of the mutable config. No production code outside the facade or shortest-path implementation imports
   `PathfinderConfig`. Remaining work is to decide which concrete-transport overlays are implementation
   internals and migrate only the execution-facing contracts that need planner independence;
3. expose immutable transport-edge views needed by execution and banking rather than the mutable catalog;
   the banking slice is complete: local path reconstruction retains exact transport identity in both search
   directions, `Rs2RouteResult` exposes a contiguous immutable step list, and destination banking consumes
   `Rs2TransportEdge` directly. `TransportRouteAnalysis` now snapshots the exact direct, start-to-bank and
   bank-to-target step sequences; withdrawal planning consumes the compared bank-to-target sequence instead
   of performing a second search from the pre-bank player location. `LegacyRoutePlan` is removed and CI
   rejects both its return and the compare-then-replan banking pattern. Active completed
   routes now publish an identity-checked immutable snapshot, and both raw-segment dispatch and nearby
   current-tile recovery take only the exact transport selected for that route. They no longer rescan the
   mutable origin catalog or infer a choice from destination membership. Each edge now carries an explicit
   Microbot executor capability, and unregistered catalog rows fail closed before planning. Existing
   interaction handlers receive the exact local `Transport` only as an opaque package-private execution
   payload where behavior requires it; a blanket conversion of handler signatures to `Rs2TransportEdge`
   is explicitly not the architecture target. Replace a payload only when its executor has an equivalent
   Microbot-owned command/context interface;
4. prohibit new direct `Pathfinder`, `PathfinderConfig` and mutable transport dependencies only after each
   corresponding consumer slice has migrated and equivalent route/runtime evidence exists.

#### Concrete transport dependency inventory

The remaining production imports are classified rather than treated as one undifferentiated migration:

| Ownership | Remaining consumers | Decision |
|---|---|---|
| Planner implementation | `shortestpath/pathfinder/**`, `Rs2PathApi` | Allowed behind the facade. The facade maps selected/catalog entries to immutable Microbot values. |
| Behavior-bearing execution payload | `Rs2Walker`, `PohTransport`, seasonal handlers | Keep only while behavior cannot be represented losslessly by an owned command. POH subtype execution is the explicit current exception. |
| Planner overlay and evidence ingestion | Leagues injection, attempts and observations | Treat as local-engine internals for now; redesign the overlay input/output contract before attempting an engine replacement. Mutable config access is already removed. |
| Deprecated compatibility surface | `Rs2Walker.getTransportsForPath`, concrete-returning `TransportRouteAnalysis` methods, Slayer and banking concrete overloads | Preserve for Hub binary compatibility and keep out of active planning/execution flows. `TransportRouteAnalysis` itself is now an owned immutable analysis value with exact-step getters. |
| Migrated execution/catalog consumers | hot-air balloon, door probing, unified obstacle scene and route recovery | Concrete imports are prohibited by `check-shortest-path-boundary.py`. Presence checks use `hasCatalogTransportOrigin`; classification uses immutable catalog edges. |

The bank-route distance heuristic and withdrawal selector both consume the exact ordered `Rs2RouteStep`
selected by the compared bank search; neither rematches the mutable catalog nor replans from a different
origin to guess the transport. The next execution-boundary
candidate is a seasonal or POH command contract, selected only after its full runtime inputs and success
conditions are captured in tests and live evidence.

### 6. Evaluate planner-core convergence

The ownership split does not justify maintaining a second planner forever. Once the request/result seam
can represent typed route edges and explicit termination without exposing either engine's classes:

1. **Complete:** package an adapter for the reviewed upstream core and run it beside the local planner from the
   same immutable requests and policy snapshots. Static collision, exact explicit-catalog network selection,
   start-at-bank availability and separate-bank workflow slices are covered by
   `scripts/compare-shortest-path-planners.py`. The gate also compiles an independent pinned checkout and
   proves its semantic output matches the production-packaged adapter;
2. compare exact reachability, termination, selected transport identities, path cost, nodes explored,
   elapsed time and peak search memory across overland, surface-to-underground-to-surface, unreachable,
   wilderness, network-transport and banked/unbanked routes;
3. require preservation of Microbot-only restrictions and prove that upstream path steps contain enough
   edge identity for the existing executor, recovery and observability contracts;
4. replace the local core only after the headless corpus is equivalent and the live harness passes the
   interaction routes affected by the candidate. Route execution remains Microbot-owned whichever core
   wins;
5. record the benchmark and acceptance decision against an exact upstream commit. A failed candidate is
   evidence for a specific local requirement, not permission for untracked architectural drift.

The reviewed upstream `PathStep` still carries only packed position and bank-visited state in its source
tree. Upstream's own display fallback documents that reconstructing a transport from adjacent steps is
ambiguous when more than one valid transport shares an edge. The evaluation checkout therefore applies
`upstream-exact-transport-identity.patch` at the exact reviewed commit: it retains the selected object in
the primitive node graph without changing search ordering or cost. A same-origin/destination corpus case
proves that the faster of two alternatives is reported exactly. This is sufficient for evaluation, but a
production planner replacement still requires equivalent metadata to land upstream or be maintained as
an explicitly reviewed adapter patch. Origin/destination rematching is not an acceptable executor contract.

Run the current comparison against an existing exact upstream checkout with:

```bash
scripts/compare-shortest-path-planners.py \
  --upstream-checkout /path/to/Skretzo-shortest-path \
  --require-all
```

Without `--upstream-checkout`, the harness clones the reviewed commit into a temporary worktree. The
`--require-all` option exits `2` as soon as a future corpus case requests a capability either engine
explicitly cannot represent; it must be used before any planner replacement decision.

## Validation gates

A walker change is ready to merge only when all applicable gates pass:

1. focused unit/regression tests for the changed behavior;
2. planner core + route corpus for collision or transport-data changes;
3. `./ci/build.sh`;
4. a live harness walk when behavior touches runtime interaction;
5. upstream baseline updated only after the reviewed changes and import/rejection decisions are recorded.
6. semantic transport baseline unchanged, or deliberately updated with the reviewed route identities and
   differing fields documented.

## Historical plans

- `runelite-client/src/main/java/net/runelite/client/plugins/microbot/shortestpath/UPSTREAM_COMPARISON.md`
  contains the detailed upstream archaeology.
- `WEBWALKER_IMPROVEMENT_PLAN.md`, `docs/walker-audit.md` and `docs/walker-p2-unification.md` preserve
  earlier audits and completed/abandoned design stages. Where they conflict with this file, this roadmap
  is authoritative.
