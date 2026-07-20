# Shortest Path: Upstream vs Microbot Comparison

Comparison of [Skretzo/shortest-path](https://github.com/Skretzo/shortest-path) (upstream) against the Microbot fork.
Original baseline: `07fca57` ("Data fixes and minor cleanups (#400)"). **Everything below the "Re-baseline" section refers to that old baseline and is partly superseded — read the re-baseline first.**

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
| **#3 POH** | ⚠️ **Real gap, concrete now.** Upstream ships `resources/transports/teleportation_portals_poh.tsv` (**137 rows**: Annakarl, Ardougne, Ape Atoll Dungeon, … POH nexus/portal destinations). Microbot has **no** POH TSV — it routes POH programmatically via `util/poh/`. **Action:** verify `util/poh` covers this 137-row destination set (diff the upstream TSV against what `PohTransport`/`PohTeleports` produce), backfill gaps. |
| **#10 reachability prune**, **#12 SplitFlagMap LRU eviction** | ❌ **No upstream counterpart** — not present in upstream `PathfinderConfig`/`SplitFlagMap`. These are **Microbot-original** optimization ideas from the webwalker audit, *not* upstream backports. Treat as independent perf work, not re-baseline items. |
| **#30 one-way transports** | ❓ Not clearly present upstream on re-check; deprioritize (no confirmed upstream source to backport from). |
| **#11 `PrimitiveIntList`** | Upstream has `PrimitiveIntList` for its *output* paths, but Microbot's `Node` is already packed-int; the only `List<WorldPoint>` left is the `getPath()` facade boundary (see `WEBWALKER_IMPROVEMENT_PLAN.md` #11). Not a behind-facade change. |

### Recommended next Stage-4 target
**#3 POH coverage** is the one concrete, real upstream gap: diff `skretzo/master:src/main/resources/transports/teleportation_portals_poh.tsv` against Microbot's programmatic POH set and backfill. It's data-level (low engine risk) and testable.

---

## Critical Bug Fixes Missing Locally

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

| Feature | Upstream | Local |
|---------|----------|-------|
| Hot Air Balloons | Yes | No |
| Magic Mushtrees | Yes | No |
| Seasonal Transports | Yes | No |
| Teleportation Boxes | Yes | No |
| POH Teleportation Portals | Yes | No |
| Cowbell Amulet | Yes | No |
| Pendant of Ates (Kastori, Nemus) | Yes | No |
| Sailors' Amulet | Yes | No |
| Separate Kharedst's Memoir / Book of the Dead | Yes | No |
| Great Conch fairy ring (CJQ) | Yes | No |
| Laguna Aurorae spirit tree | Yes | No |
| Bank-visit teleport discovery | Yes | No |
| Per-transport-type cost tuning (18 thresholds) | Yes | No |
| Currency threshold (avoid expensive transports) | Yes | No |
| Transport data as TSV with parser package | Yes | Inline string parsing |

Upstream's transport data model uses a dedicated parser package (`FieldParser`, `TransportRecord`, `TsvParser`, `WorldPointParser`, etc.) with a clean `TransportBuilder` pattern. Local parses TSV fields with raw string splitting directly in the `Transport` constructor.

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

Local has a single `usePoh` toggle with a `PohPanel` and `createMergedList()` approach, no POH-specific overlay handling.

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
