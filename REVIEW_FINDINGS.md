# Microbot Review Findings

Autonomous review loop output. Read this in the morning and pick what to
implement. Items at the top of each section are most actionable.

## How to use

- **URGENT** — crashes, client-thread blocking, leaks, security, data loss
- **HIGH** — significant performance wins or features users genuinely need
- **MEDIUM** — code simplification, refactor opportunities, dead code
- **LOW** — small cleanups, minor improvements, nice-to-haves

Each finding has a file:line reference, the issue, the proposed fix, and
the expected impact. Once a finding is implemented, prefix its title with
`[DONE]` so the loop knows not to revisit it.

## Reviewed Areas

<!-- The loop appends one line per iteration here so future iterations
     can rotate to a fresh slice of the codebase. Format:
     - <area> (iter N, YYYY-MM-DD HH:MM) — N findings -->

- runelite-client/.../microbot/util/bank (iter 1, 2026-04-09) — 4 findings
- runelite-client/.../microbot/util/inventory (iter 1, 2026-04-09) — 4 findings
- runelite-client/.../microbot/util/equipment (iter 2, 2026-04-09) — 3 findings
- runelite-client/.../microbot/util/walker (iter 3, 2026-04-09) — 4 findings
- runelite-client/.../microbot/util/npc (iter 4, 2026-04-09) — 3 findings
- runelite-client/.../microbot/util/gameobject (iter 5, 2026-04-09) — 3 findings
- runelite-client/.../microbot/util/combat (iter 6, 2026-04-09) — 3 findings
- runelite-client/.../microbot/util/grounditem (iter 7, 2026-04-09) — 3 findings
- runelite-client/.../microbot/util/magic (iter 8, 2026-04-09) — 4 findings
- runelite-client/.../microbot/util/prayer (iter 9, 2026-04-09) — 4 findings
- runelite-client/.../microbot/util/widget (iter 10, 2026-04-09) — 5 findings
- runelite-client/.../microbot/util/antiban (iter 11, 2026-04-09) — 5 findings
- runelite-client/.../microbot/util/dialogues (iter 12, 2026-04-09) — 4 findings
- runelite-client/.../microbot/util/mouse (iter 13, 2026-04-09) — 4 findings
- runelite-client/.../microbot/util/keyboard (iter 14, 2026-04-09) — 3 findings
- runelite-client/.../microbot/util/grandexchange (iter 15, 2026-04-09) — 4 findings
- runelite-client/.../microbot/util/shop (iter 16, 2026-04-09) — 5 findings
- runelite-client/.../microbot/util/depositbox (iter 17, 2026-04-09) — 4 findings
- runelite-client/.../microbot/util/farming (iter 18, 2026-04-09) — 4 findings
- runelite-client/.../microbot/breakhandler (iter 19, 2026-04-09) — 5 findings
- runelite-client/.../microbot/util/tabs (iter 20, 2026-04-09) — 3 findings
- runelite-client/.../microbot/util/coords (iter 21, 2026-04-09) — 4 findings
- runelite-client/.../microbot/util/poh (iter 22, 2026-04-09) — 4 findings
- core: Microbot.java, Script.java, BlockingEventManager (iter 23, 2026-04-09) — 3 findings
- runelite-client/.../microbot/ui (iter 24, 2026-04-09 06:13) — 4 findings

## URGENT

### [DONE] `getAttackableNpcs(true)` runs N BFS path searches on the client thread, freezing game rendering every combat tick

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/npc/Rs2Npc.java:406-416`, `Rs2Npc.java:220-306`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/coords/Rs2WorldPoint.java:94-121`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/tile/Rs2Tile.java:1067-1200`
- **Type:** performance
- **Found:** iter 21
- **Issue:** `getAttackableNpcs(true)` passes a filter predicate containing `playerLocation.distanceToPath(npc.getWorldLocation())` to `getNpcs(predicate)` at line 411. `getNpcs(predicate)` evaluates this predicate inside `runOnClientThreadOptional` (line 223) — meaning `distanceToPath()` runs on the client thread for every NPC in the scene. Each `distanceToPath()` call invokes `Rs2Tile.pathTo()`, which allocates two `int[128][128]` arrays and two `int[4096]` arrays, initialises all 32,768 cells in the 2D arrays, then runs a BFS over the scene collision data (up to 4,096 queue steps). With 20 attackable NPCs in a combat area, one `getAttackableNpcs(true)` call triggers 20 of these BFS runs on the client thread. Combat scripts call `getAttackableNpcs(true)` on every 600 ms tick. Total allocation per tick: ~20 × 163 KB = ~3.2 MB of short-lived arrays, all on the client thread.
- **Fix:** Remove `distanceToPath()` from the `getNpcs(predicate)` filter. Instead, replace the predicate's reachability check with a simple BFS from the player once (outside `runOnClientThreadOptional`) that returns the set of all reachable scene tiles; then `distanceToPath` is replaced with `reachableTiles.contains(npc.getSceneLocation())` — an O(1) lookup. Alternatively, move the `reachable` filter to a post-hoc `.filter()` on the stream returned by `getNpcs()`, which runs on the script thread at least not blocking game rendering.
- **Impact:** Eliminates N client-thread BFS allocations per combat tick; with 20 NPCs each requiring a 163 KB BFS, this removes ~3.2 MB/tick of client-thread garbage and the associated GC stalls that stutter the game.

### [DONE] `getNearestDepositBox` crashes with NPE via `List.of(null)` whenever no deposit box is in the visible scene

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/depositbox/Rs2DepositBox.java:461`, `Rs2DepositBox.java:419-431`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/gameobject/Rs2GameObject.java:451`
- **Type:** simplification
- **Found:** iter 17
- **Issue:** `getNearestDepositBox(WorldPoint, int)` at line 461 calls `List.of(Rs2GameObject.findDepositBox(maxObjectSearchRadius))`. `findDepositBox(int)` uses `.orElse(null)` (Rs2GameObject.java:451), so it returns null when no deposit box is within range. `List.of(null)` throws `NullPointerException` in Java 9+. The no-arg `getNearestDepositBox()` passes `Rs2Player.getWorldLocation()` as both the capture point and the later comparison (`Objects.equals`), so the guard at line 460 is almost always true, making this crash path execute whenever the player is not standing next to a deposit box. Every call to `walkToDepositBox()` and `walkToAndUseDepositBox()` hits this path when the player is far from a deposit box.
- **Fix:** Replace line 461 with a null-safe alternative: `GameObject depositBoxObj = Rs2GameObject.findDepositBox(maxObjectSearchRadius); List<TileObject> bankObjs = depositBoxObj != null ? List.of(depositBoxObj) : List.of();`. The empty list causes the `byObject` optional to be empty and falls through correctly to the pathfinder, which is the intended behaviour.
- **Impact:** Prevents a `NullPointerException` that crashes any script using `walkToDepositBox()` or `walkToAndUseDepositBox()` when the player is not already standing next to a deposit box; this is the normal starting state for every deposit-box script.

### [DONE] `shopItems` is a non-volatile static field written by the client thread and read by script threads, allowing stale list references to be observed indefinitely
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/shop/Rs2Shop.java:32`, `Rs2Shop.java:329`
- **Type:** performance
- **Found:** iter 16
- **Issue:** `shopItems` (line 32) is `public static List<Rs2ItemModel>` with no `volatile` qualifier. `storeShopItemsInMemory` (called from the `ItemContainerChanged` event — client thread) replaces the reference at line 329 (`shopItems = list`). Script threads read this reference in `hasStock`, `hasMinimumStock`, `isFull`, `getSlot`, and `buyItem` without any synchronization barrier. The JVM is free to cache the old reference in a script-thread CPU register or L1 cache, meaning the updated list may never be visible to the script thread. The `waitForShopChanges` reference-equality check (`shopItems != initialShopItems`, line 449) also relies on the script thread seeing the new reference, which is not guaranteed without `volatile`.
- **Fix:** Add `volatile` to the field declaration: `public static volatile List<Rs2ItemModel> shopItems = new ArrayList<>();`. No other changes are needed — `volatile` guarantees that the reference assignment on the client thread is immediately visible to all script threads.
- **Impact:** Prevents script threads from polling a stale shop item list indefinitely; any shop-buying script that calls `hasStock()` or `waitForShopChanges()` after the shop opens can currently observe the pre-open empty list forever.

### [DONE] `priceCache` and `mappingCache` are unsynchronized `HashMap` instances written by the script thread and HTTP async executor concurrently
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/grandexchange/Rs2GrandExchange.java:63-64`, `Rs2GrandExchange.java:1216-1224`, `Rs2GrandExchange.java:1497-1506`
- **Type:** performance
- **Found:** iter 15
- **Issue:** `priceCache` (line 63) and `mappingCache` (line 64) are `private static final Map` instances backed by plain `new HashMap<>()`. `getRealTimePrices()` is called from the script thread (via `sellInventory`, `sellLoot`, `getAdaptiveBuyPrice`) and both reads (`priceCache.get`) and writes (`priceCache.put`) occur on it. The HTTP responses from `sendAsync()` complete on the ForkJoinPool common thread (the async executor), and while `.join()` marshals the result back, the concurrent put from overlapping calls (e.g. two scripts checking prices simultaneously) can cause `HashMap`'s internal state to corrupt, producing infinite spin-loops or lost entries. `mappingCache` has the same vulnerability.
- **Fix:** Replace `new HashMap<>()` with `new ConcurrentHashMap<>()` for both `priceCache` and `mappingCache`. No other changes needed — all operations are single-key reads/writes that `ConcurrentHashMap` handles correctly without additional locking.
- **Impact:** Eliminates a potential infinite-loop / data-corruption hazard in any concurrent price-checking scenario; costs two characters per declaration to fix.

### [DONE] `renderAntibanOverlayComponents` dereferences `playStyle` before its null guard, causing NPE on every overlay render before `setActivity()` is called
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/antiban/Rs2Antiban.java:455`
- **Type:** simplification
- **Found:** iter 11
- **Issue:** Line 455 calls `playStyle.getSecondaryTickInterval()` to initialise the progress bar, but `playStyle` is only guarded by `if (playStyle != null)` at line 484 — which is 29 lines later. Any script that renders the antiban overlay before calling `setActivity()` (e.g. during the startup frame while the plugin loads) hits line 455 with `playStyle == null`, throwing `NullPointerException` in the overlay renderer on the EDT. The `ProgressBarComponent` built at lines 451-460 is only added to the panel inside the `if (playStyle != null)` block (line 497), making lines 451-460 entirely wasted allocation if `playStyle` is null.
- **Fix:** Add `if (playStyle == null) return;` at the start of `renderAntibanOverlayComponents` (before line 451), or move the entire `progressBarComponent` initialisation block inside the existing `if (playStyle != null)` check at line 484. Either approach eliminates both the NPE and the wasted allocation.
- **Impact:** Prevents EDT crashes in any plugin that calls `renderAntibanOverlayComponents` before the antiban activity is configured; the overlay currently throws silently into the overlay manager's exception handler and renders blank.

### [DONE] `SpiritTree.POH_SPIRIT_RING` invokes 127 000-field reflection scan during enum class initialization, freezing an incorrect result forever

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/farming/SpiritTree.java:204`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/gameobject/Rs2GameObject.java:1881-1902`
- **Type:** performance
- **Found:** iter 18
- **Issue:** The `POH_SPIRIT_RING` enum constant passes `Rs2GameObject.getObjectIdsByName("poh_spirit_ring")` as its `objectId` constructor argument (line 204). Enum constants are initialized at JVM class-load time — before the RuneLite client exists or any plugins have started. `getObjectIdsByName` is annotated `@SneakyThrows` and scans all public fields of three `ObjectID` classes (~127 000 fields total) via reflection. If any reflection call throws (e.g. `SecurityException` or `IllegalAccessException` from `setAccessible`), the exception propagates as an unchecked exception during class initialization, causing an `ExceptionInInitializerError` that makes the entire `SpiritTree` enum permanently unloadable — any class that references `SpiritTree` will then throw `NoClassDefFoundError`. Even when the scan succeeds, the `List<Integer>` is frozen into the `final` field for the session lifetime; if Jagex later adds new POH spirit tree object IDs (as they have done for league skins and seasonal variants, per the `// TODO` comment on line 192), the static list is permanently stale.
- **Fix:** Replace the `Rs2GameObject.getObjectIdsByName("poh_spirit_ring")` call at line 204 with a hardcoded `List.of()` (matching all other enum constants that list known object IDs) and add a comment with the object IDs once confirmed. If the IDs are genuinely unknown, keep `List.of()` as a placeholder rather than doing reflection at class-load time.
- **Impact:** Eliminates a class-load time `ExceptionInInitializerError` risk that makes the entire `SpiritTree` enum unloadable if the reflection fails; removes a 127 000-field scan from the plugin startup path; prevents stale frozen ID lists from silently breaking spirit tree object detection after game updates.

### [DONE] `dropAllExcept(int gpValue)` dispatches one blocking client-thread call per inventory item
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/inventory/Rs2Inventory.java:682-686`
- **Type:** performance
- **Found:** iter 1
- **Issue:** `dropAllExcept(int gpValue, String[] ignoreItems)` builds a `price` predicate that calls `Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getItemManager().getItemPrice(...))` for every item evaluated by the stream. `runOnClientThreadOptional` submits a `FutureTask` and blocks via `task.get(10000, ms)` when called off the client thread. With 28 inventory items and none in the ignore list, that is 28 sequential blocking round-trips — up to 28 × 10 s of potential stall on the script thread.
- **Fix:** Replace per-item dispatch with a single `runOnClientThreadOptional` call that iterates all items once and returns a `Map<Integer, Long>` of `itemId → price * quantity`. The predicate for the subsequent `dropAll` call then reads from that pre-built map with no further client-thread interaction.
- **Impact:** Reduces 28 blocking client-thread round-trips to 1; prevents the script thread from hanging for minutes if the client thread is briefly busy.

## HIGH

### [DONE] `MicrobotPluginHubPanel.onExternalPluginsChanged` calls `reloadPluginList()` from the client thread, violating Swing's single-thread rule

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/ui/MicrobotPluginHubPanel.java:906-909`, `MicrobotPluginHubPanel.java:762-768`
- **Type:** simplification
- **Found:** iter 24
- **Issue:** The `@Subscribe` handler `onExternalPluginsChanged` (lines 906-909) calls `reloadPluginList()` directly with no EDT dispatch. `reloadPluginList()` immediately accesses `refreshing.isVisible()` (line 763), `refreshing.setVisible(true)` (line 767), and `mainPanel.removeAll()` (line 768) — all Swing operations that must run on the EDT. RuneLite event subscribers are invoked on the client thread, not the EDT. Compare with `MicrobotPluginListPanel.onExternalPluginsChanged` (line 367-369), which correctly wraps in `SwingUtilities.invokeLater`. The hub panel version does not. Every plugin install, update, or removal fires `ExternalPluginsChanged` and triggers this EDT violation, risking `IllegalStateException` or visual corruption in the decorated Swing window title.
- **Fix:** Wrap the call at line 908: `SwingUtilities.invokeLater(this::reloadPluginList)`. This matches the pattern already used in `MicrobotPluginListPanel` and is a one-line fix.
- **Impact:** Eliminates a Swing threading violation that fires on every plugin install/update/remove action from the plugin hub; mirrors the already-correct pattern in the sibling panel class.

### [DONE] `Script.scheduledExecutorService` is a 10-thread non-daemon pool never shut down in `shutdown()`, leaking OS threads on plugin restart

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/Script.java:25-34`, `Script.java:52-66`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/shortestpath/ShortestPathPlugin.java:229`
- **Type:** performance
- **Found:** iter 23
- **Issue:** The base `Script` class creates `Executors.newScheduledThreadPool(10, threadFactory)` at field initialisation (line 25). The custom `ThreadFactory` at lines 26-33 does NOT call `t.setDaemon(true)`, so all 10 threads are user (non-daemon) threads. `Script.shutdown()` at lines 52-66 cancels `mainScheduledFuture` and `scheduledFuture` but never calls `scheduledExecutorService.shutdown()` or `scheduledExecutorService.shutdownNow()`. When a plugin creates a new script instance on each `startUp()` without a null-guard — as `ShortestPathPlugin` does at line 229 (`shortestPathScript = new ShortestPathScript()`) — the old executor's 10 threads remain alive blocking on the internal `DelayedWorkQueue` indefinitely. Since they are non-daemon, they also prevent JVM exit. Each stop+start cycle of such a plugin leaks another 10 threads.
- **Fix:** Add `scheduledExecutorService.shutdownNow()` at the start of `Script.shutdown()` (before the `mainScheduledFuture` null check). Change the `ThreadFactory` to set `t.setDaemon(true)` so that orphaned threads (if the executor is somehow not shut down) at least do not block JVM exit.
- **Impact:** Prevents 10 non-daemon thread leaks per stop+start cycle of any plugin that creates a new script instance; stops non-daemon threads from preventing JVM exit; `ShortestPathPlugin` is the confirmed instance, but any plugin following the "new instance each time" pattern is affected.

### [DONE] `PohTeleports` static initializers trigger four `getObjectIdsByName` calls at class load, scanning `"poh_spirit_ring"` twice

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/poh/PohTeleports.java:207-222`
- **Type:** performance
- **Found:** iter 22
- **Issue:** `FAIRY_RING_IDS` (line 207) is initialized by `fairyRingIds()`, which calls `getObjectIdsByName("poh_spirit_ring")` and `getObjectIdsByName("poh_fairy_ring")`. `SPIRIT_TREE_IDS` (line 208) is initialized by `spiritTreeIds()`, which calls `getObjectIdsByName("poh_spirit_ring")` again and `getObjectIdsByName("poh_spirit_tree")`. This results in 4 calls to `getObjectIdsByName` at `PohTeleports` class-load time; the `"poh_spirit_ring"` reflection scan runs twice, wasting one complete 127,000-field scan. Per the HIGH finding from iter 5, `getObjectIdsByName` has no cache and each call re-scans all public fields of three `ObjectID` classes via reflection. If any of those reflection calls throws a `SecurityException`, the resulting unchecked exception during static field initialization causes an `ExceptionInInitializerError` that makes the entire `PohTeleports` class unloadable — the same risk identified in iter 18 for `SpiritTree.java`. The two fields are also non-`final`, meaning they can be accidentally set to null by future code.
- **Fix:** Eliminate the duplicate scan by extracting the shared result: `private static final List<Integer> POH_SPIRIT_RING_IDS = Rs2GameObject.getObjectIdsByName("poh_spirit_ring")`. Build `FAIRY_RING_IDS` and `SPIRIT_TREE_IDS` as `private static final Set<Integer>` that append to `POH_SPIRIT_RING_IDS` without re-scanning. This reduces 4 class-load reflection scans to 3 and prevents the non-`final` mutation risk.
- **Impact:** Eliminates one redundant 127,000-field reflection scan from the POH class-load path; also prevents an `ExceptionInInitializerError` that would make `PohTeleports` (and every class that imports it) permanently unloadable if the reflection call fails during startup.

### [DONE] `distanceToPath()` in stream `.min()` comparators reads scene tiles from the script thread and runs N sequential BFS searches

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/npc/Rs2Npc.java:1227`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/gameobject/Rs2GameObject.java:330-332`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/tile/Rs2Tile.java:687`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/coords/Rs2WorldPoint.java:53-91`
- **Type:** performance
- **Found:** iter 21
- **Issue:** Three call sites use `playerLocation.distanceToPath(...)` as a stream `.min()` comparator key after `getNpcs()`/`getGameObjects()` have already materialised their results on the script thread: `getNearestNpcWithAction()` (Rs2Npc.java:1227), `getReachableGameObject()` (Rs2GameObject.java:330-332), and `getNearestWalkableInteractPoint()` (Rs2Tile.java:687). Each comparator invocation calls `Rs2WorldPoint.pathTo()` (lines 55-91), which reads `Microbot.getClient().getTopLevelWorldView().getScene().getTiles()` directly from the script thread (unsafe during scene updates) and then delegates to `Rs2Tile.pathTo()` for a full BFS. With K elements reaching the `.min()`, this is K full BFS executions on the script thread — plus K unsafe off-thread scene reads. If a scene update is in flight, the `Tile[][][]` array returned at line 73 may reflect a partially-swapped scene, producing a path through an incorrect tile layout.
- **Fix:** Replace `distanceToPath()` in all three `.min()` comparators with `Rs2WorldPoint.quickDistance()` (already defined at Rs2WorldPoint.java:157 — Chebyshev distance, O(1), no client state read). Chebyshev distance is a valid approximation for the nearest-entity comparator; exact path distance is only needed for the binary reachability check (handled separately by the URGENT finding above).
- **Impact:** Eliminates K BFS searches and K unsafe scene reads per call to `getNearestNpcWithAction`, `getReachableGameObject`, or `getNearestWalkableInteractPoint`; every script using these to find nearby entities benefits on every invocation.

### [DONE] `Rs2Tile.pathTo()` allocates and initialises two 128×128 grids per call, causing severe GC pressure in combat scripts

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/tile/Rs2Tile.java:1082-1095`
- **Type:** performance
- **Found:** iter 21
- **Issue:** Every `Rs2Tile.pathTo()` call allocates `new int[128][128]` for `directions`, `new int[128][128]` for `distances`, `new int[4096]` for `bufferX`, and `new int[4096]` for `bufferY` — totalling approximately 163 KB per call. It then initialises all 32,768 cells of the two 2D arrays via a nested loop (lines 1088-1095) before running the BFS. Since `pathTo()` is called from `distanceToPath()`, which is called from stream predicates and comparators throughout the codebase, these temporary arrays are allocated and immediately discarded many times per script tick. Java's GC will eventually collect them, but minor GC pauses caused by such large short-lived allocations manifest as frame-time spikes visible as game stutter, particularly in combat scripts that trigger `getAttackableNpcs(true)` every 600 ms.
- **Fix:** Promote the four arrays to `private static final ThreadLocal<int[][]>` / `ThreadLocal<int[]>` fields (or, since `pathTo()` is only called on the client thread in the URGENT case and the script thread elsewhere, a pair of static `int[][]` scratch fields with a guard flag). Initialize them once and reset only the cells that were modified during each BFS by tracking the set of visited (x, y) pairs. Alternatively, use a flat `int[128*128]` layout instead of `int[128][128]` to improve cache locality and halve the allocation object count.
- **Impact:** Removes ~163 KB per BFS call from the GC heap; at 20 NPCs per combat tick, this saves ~3.2 MB/tick that would otherwise trigger minor GC pauses and game stutter.

### [DONE] `getCurrentTab()` throws `IllegalStateException` on any VarcInt outside 0–13, crashing script threads that poll it in `sleepUntil` predicates

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/tabs/Rs2Tab.java:50-54`
- **Type:** simplification
- **Found:** iter 20
- **Issue:** The switch in `getCurrentTab()` ends with `throw new IllegalStateException("Unexpected value: " + varcIntValue)` at line 53. VarcInt `TOPLEVEL_PANEL` can transiently hold values outside 0–13 during login, world hop, or interface transitions, and will produce a new value whenever Jagex adds a tab. The throw propagates out of the lambda in every `sleepUntil(() -> Rs2Tab.getCurrentTab() == ...)` predicate across the codebase (Rs2Combat.java:60, Rs2Combat.java:91, Rs2Walker.java:2167, Rs2Inventory.java:2108, Rs2Magic.java:127, Rs2Magic.java:269, Rs2SpellBookSettings.java:46), killing those script threads.
- **Fix:** Change the `default` case on line 53 from `throw new IllegalStateException(...)` to `return InterfaceTab.NOTHING_SELECTED;`. The sentinel value is the correct semantic ("no recognised tab is open") and callers already handle it.
- **Impact:** Prevents script thread crashes whenever the game transitions through an unexpected tab state; every script that polls tab state in a `sleepUntil` loop (combat options, inventory open, magic spellbook, walker) is currently vulnerable.

### [DONE] `checkForBan()` reads `getGameState()` and `getLoginIndex()` from the scheduler thread, risking false-positive ban detection

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/breakhandler/BreakHandlerScript.java:910-914`, `BreakHandlerScript.java:360`
- **Type:** simplification
- **Found:** iter 19
- **Issue:** `checkForBan()` calls `Microbot.getClient().getGameState()` and `Microbot.getClient().getLoginIndex()` directly from the scheduler thread (not the client thread). The login index cycles through values 0–14 as authentication progresses; if a stale or transitional value of 14 (`BANNED_LOGIN_INDEX`) is read from the scheduler thread during a normal login step, the ban-detection branch fires: a Discord webhook alert is sent and `Microbot.stopPlugin(BreakHandlerPlugin.class)` is called — stopping the entire break handler for a player who is not banned. The same unsafe pattern appears at line 360 where `Microbot.getClient().getWorld()` is read from the scheduler thread inside `handleInitiatingBreakState()`.
- **Fix:** Wrap both reads in a single `runOnClientThreadOptional` lambda that reads `getGameState()` and `getLoginIndex()` together: `Optional<Integer> loginIdx = Microbot.getClientThread().runOnClientThreadOptional(() -> client.getGameState() == GameState.LOGIN_SCREEN ? client.getLoginIndex() : -1); if (loginIdx.orElse(-1) == BANNED_LOGIN_INDEX) { ... }`. Apply the same pattern for `getWorld()` at line 360.
- **Impact:** Prevents false-positive ban detection during normal login sequences that would silently shut down the break handler plugin and halt all scripts for a non-banned player.

### [DONE] `breakIn`, `breakDuration`, and `totalBreaks` are non-volatile static fields shared between the scheduler thread and the game render thread

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/breakhandler/BreakHandlerScript.java:89-93`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/breakhandler/BreakHandlerOverlay.java:46,77,82`
- **Type:** performance
- **Found:** iter 19
- **Issue:** `breakIn`, `breakDuration`, `setBreakDurationTime`, and `totalBreaks` are declared as `public static int/Duration` without `volatile`. They are written by the break handler's 1-second scheduler thread in `updateBreakTimers()` (lines 815–831) and `updateBreakStatistics()` (line 780), but read by the game render thread in `BreakHandlerOverlay.render()` at lines 46, 77, and 82 with no synchronization. Without `volatile`, the JVM is free to cache the old value in the render thread's CPU register or L1 cache, causing the displayed countdown to freeze at a stale value indefinitely. In practice this means the "Break in: HH:MM:SS" and "Break duration: HH:MM:SS" counters shown in the overlay can appear stuck even while the break handler is actively counting down.
- **Fix:** Add `volatile` to the four field declarations at lines 89–92: `public static volatile int breakIn = -1; public static volatile int breakDuration = -1; public static volatile Duration setBreakDurationTime = Duration.ZERO; public static volatile int totalBreaks = 0;`. No other changes are needed — `volatile` on primitive fields guarantees visibility across threads without locking overhead.
- **Impact:** Ensures the overlay always displays the current countdown values; without the fix, the break timer shown to the user can freeze at a wrong value, making it impossible to tell when the next break will occur or how long the current break has remaining.

### [DONE] `SpiritTree.getAvailableForTravel()` dispatches 10 sequential blocking client-thread round-trips for 5 farmable trees

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/farming/SpiritTree.java:397-401`, `SpiritTree.java:261-277`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/farming/Rs2Farming.java:83-87`, `Rs2Farming.java:127-132`
- **Type:** performance
- **Found:** iter 18
- **Issue:** `getAvailableForTravel()` streams all 12 `SpiritTree` values through `isAvailableForTravel()`. For each of the 5 FARMABLE trees (PORT_SARIM, ETCETERIA, BRIMHAVEN, HOSIDIUS, FARMING_GUILD), `isAvailableForTravel()` calls `isPatchHealthyAndGrown()`, which first calls `Rs2Farming.getSpiritTreePatches()` (one `runOnClientThreadOptional` round-trip each) and then `Rs2Farming.predictPatchState(patch)` (another `runOnClientThreadOptional` round-trip). This produces 5 × 2 = 10 sequential blocking client-thread round-trips per `getAvailableForTravel()` call. `getAvailableForTravel()` is the primary entry point for spirit tree transport scripts, which call it to decide which destinations to offer.
- **Fix:** Hoist both the patch list fetch and the state prediction out of the per-tree loop. Add a `Map<FarmingPatch, CropState> batchPredictAll(List<FarmingPatch>)` method to `Rs2Farming` that calls `runOnClientThreadOptional` once and returns states for all patches. `getAvailableForTravel()` fetches `getSpiritTreePatches()` once, calls `batchPredictAll`, then evaluates each `SpiritTree` against the pre-built map without any further client-thread dispatch.
- **Impact:** Reduces 10 sequential blocking client-thread round-trips to 2 (one for patch list, one for all states); any spirit tree teleport script calling `getAvailableForTravel()` currently blocks the script thread for up to 10 × 10 s on a slow client thread.

### [DONE] `getReadyPatches`, `getHarvestablePatches`, `getPatchesNeedingAttention`, and `getEmptyPatches` each dispatch N sequential blocking client-thread round-trips for N patches

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/farming/Rs2Farming.java:141-193`, `Rs2Farming.java:127-132`
- **Type:** performance
- **Found:** iter 18
- **Issue:** All four filter methods stream their input `patches` list and call `predictPatchState(patch)` for each patch inside the filter predicate. `predictPatchState` submits a `FutureTask` to the client thread via `runOnClientThreadOptional` and blocks until it completes. With 10 herb patches, `getHarvestablePatches(getHerbPatches())` dispatches 10 sequential blocking round-trips — each incurring full `FutureTask` scheduling overhead. Any farming script that checks multiple patch types in sequence (herbs + trees + allotments) compounds this further; checking three categories over 25 patches total would dispatch 25 round-trips.
- **Fix:** Batch the prediction: add a `Map<FarmingPatch, CropState> predictAllPatchStates(List<FarmingPatch> patches)` helper that calls `runOnClientThreadOptional` once with a lambda that iterates all patches and returns the full map. Rewrite the four filter methods to call `predictAllPatchStates` once and filter the returned map, with no additional client-thread dispatch per patch.
- **Impact:** Reduces N blocking round-trips to 1 per filter call (N = number of patches checked); every farming herb-run or tree-run script that checks patch readiness per loop tick benefits directly.

### [DONE] `depositAll(Predicate)` fires successive `invokeMenu` calls for each matching item without waiting, silently losing all but the first deposit

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/depositbox/Rs2DepositBox.java:134`, `Rs2DepositBox.java:147`, `Rs2DepositBox.java:157-159`, `Rs2DepositBox.java:213-215`
- **Type:** simplification
- **Found:** iter 17
- **Issue:** `depositAll(Predicate<Rs2ItemModel>)` collects all matching items into a list at line 131, then iterates and calls `invokeMenu(6, item)` for each at line 134 with no sleep or wait between calls. `Microbot.doInvoke` (called by `invokeMenu`) injects a menu action into the game's input queue on the client thread; the game processes one action per game tick (~600ms). Firing N actions in rapid succession from the script thread means only the first is likely processed before the next arrives, dropping the rest. `depositAllExcept(Integer...)` (line 147), `depositAllExcept(String...)` (line 157), `depositAll(Integer...)` (line 213), and `depositAll(String...)` (line 225) all delegate through this method. By contrast, the button-based `depositAll()` at line 119 correctly calls `Rs2Inventory.waitForInventoryChanges(5000)` after its single click.
- **Fix:** After each `invokeMenu(6, item)` call inside the loop, add `sleepUntil(() -> !Rs2Inventory.hasItem(item.getId()), 2500)` to wait for that item to leave the inventory before depositing the next. This serialises deposits and confirms each one before proceeding.
- **Impact:** Ensures all matched items are actually deposited; without the fix, scripts using `depositAllExcept` or `depositAll(String...)` with multiple distinct item types will silently leave most items in the inventory.

### [DONE] `invokeMenu` dispatches a wasted `runOnClientThreadOptional` on every buy action and NPEs if the item definition is unavailable
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/shop/Rs2Shop.java:369-381`, `Rs2Shop.java:387-417`
- **Type:** simplification
- **Found:** iter 16
- **Issue:** `invokeMenu` fetches `ItemComposition` via `runOnClientThreadOptional` at lines 369-371 (a blocking client-thread round-trip), then at line 373 unconditionally calls `itemComposition.getInventoryActions()` with no null check — despite using `.orElse(null)`. If `getItemDefinition` is unavailable (e.g., during world hop or on an unknown item ID), `itemComposition` is null and line 373 throws `NullPointerException`, crashing the script thread. Critically, the `identifier` set by the loop at lines 375-380 is immediately overwritten inside the switch at lines 392-416 for every handled buy/value action — making lines 369-381 entirely dead: a blocking RPC, the null-deref risk, and the loop output are all wasted.
- **Fix:** Delete lines 369-381 entirely (the `itemComposition` fetch and the `identifier`-from-actions loop). The switch at lines 387-417 already sets the correct `identifier` for every handled action without needing the item composition. If shop sell actions are later added, scope the composition fetch inside those new cases only.
- **Impact:** Eliminates one blocking `runOnClientThreadOptional` round-trip from every `buyItem` call and removes a `NullPointerException` crash in the buy path when item definitions are temporarily unavailable.

### [DONE] `sellInventory()` always fails `isValidRequest()` — the SELL request it builds has `quantity=0` and `price=0`, which the validator rejects, making the method silently non-functional
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/grandexchange/Rs2GrandExchange.java:765-786`, `Rs2GrandExchange.java:334-353`
- **Type:** simplification
- **Found:** iter 15
- **Issue:** `sellInventory()` builds each `GrandExchangeRequest` with `action=SELL`, `itemName`, and `percent(-5)` but sets neither `quantity` nor `price` (both default to 0). `isValidRequest()` for the SELL case applies `DEFAULT_PREDICATE.and(PRICE_PREDICATE)`, which requires `request.getQuantity() > 0` AND `request.getPrice() > 0`. Both are 0, so `isValidRequest()` returns false, `processOffer()` returns false at line 167, and no sell offer is ever placed. The `forEachOrdered` loop in `sellInventory()` runs for every tradeable inventory item but accomplishes nothing. Additionally, `DEFAULT_PREDICATE` on line 339 contains a subtle closure bug — it captures `request.getQuantity()` (the method-level `request` parameter) instead of `gxr.getQuantity()` (the predicate argument), making the quantity check incorrect if the predicate were ever used with a different `gxr` argument.
- **Fix:** For `isValidRequest`, relax the SELL case to allow `price == 0` when `percent != 0` (percent-based pricing is a valid sell strategy). Also change `request.getQuantity()` to `gxr.getQuantity()` in `DEFAULT_PREDICATE` to fix the closure bug. In `sellInventory()`, either set `quantity` to the item's stack count in the inventory, or document that `quantity=0` means "use game default" and skip the quantity check in the SELL validator.
- **Impact:** Makes `sellInventory()` and `sellLoot()` functional — currently every call silently no-ops after building and discarding a request object per inventory item; any script using these methods to sell GE loot is broken.

### [DONE] Six GE price methods each allocate a new `HttpClient` per call and block the calling thread indefinitely with no timeout
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/grandexchange/Rs2GrandExchange.java:1186-1194`, `Rs2GrandExchange.java:1253-1262`, `Rs2GrandExchange.java:1388-1395`, `Rs2GrandExchange.java:1519-1525`, `Rs2GrandExchange.java:1577-1585`, `Rs2GrandExchange.java:1598-1606`, `Rs2GrandExchange.java:1627-1635`, `Rs2GrandExchange.java:1649-1657`
- **Type:** performance
- **Found:** iter 15
- **Issue:** `getOfferPrice`, `getSellPrice`, `getPrice`, `getBuyingVolume`, `getSellingVolume`, `getWikiPrices`, `fetchItemMappingData`, and `getTimeSeriesData` each call `HttpClient.newHttpClient()` locally — creating a fresh connection pool and internal thread for every invocation. `getRealTimePrices()` (called by `getAdaptiveBuyPrice`/`getAdaptiveSellPrice`) may call `getWikiPrices` then `getPrice`, `getSellPrice`, and `getBuyingVolume` in sequence — 4 new `HttpClient` objects per call. None of the requests set a timeout (`HttpRequest.Builder.timeout()`), so any network stall blocks the script thread indefinitely. `getTimeSeriesData` uses the fully synchronous `httpClient.send()` (line 1395), making this even more direct.
- **Fix:** Declare `private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build()` once at class level and reference it in all methods. Add `.timeout(Duration.ofSeconds(10))` to each `HttpRequest.Builder` chain. This replaces 8 per-call allocations with zero and bounds all network waits to 10 seconds.
- **Impact:** Eliminates 1–4 heavyweight `HttpClient` allocations per price-check call; prevents the script thread from hanging indefinitely on network failure during `sellInventory`, `sellLoot`, or any price-adaptive GE operation.

### [DONE] `move(Rectangle)` and `move(Polygon)` don't update `lastMove`, causing NaturalMouse to compute movement from the wrong start position
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/mouse/VirtualMouse.java:157-173`, `NaturalMouse.java:67-71`
- **Type:** simplification
- **Found:** iter 13
- **Issue:** `move(Rectangle rect)` (line 157) and `move(Polygon polygon)` (line 165) both dispatch a `MOUSE_MOVED` event but never call `setLastMove(point)`. `getMousePosition()` at line 204 returns `lastMove`, which is what `NaturalMouse.moveTo()` reads at line 67 to check whether the cursor is already at the destination. After either rectangle or polygon move, `lastMove` is stale (still points to wherever the cursor was before the move), so `NaturalMouse` always concludes the cursor is elsewhere and runs the full animated path starting from the wrong origin. This produces visibly incorrect, bot-like jumps that defeat the antiban naturalMouse feature.
- **Fix:** In `move(Rectangle rect)`, compute the target point before dispatching: `Point pt = new Point((int) rect.getCenterX(), (int) rect.getCenterY()); setLastMove(pt);` before (or after) the event dispatch. Apply the same fix to `move(Polygon polygon)` using the polygon's centre point. Both already compute the target coordinates and just need one additional `setLastMove` call.
- **Impact:** Corrects naturalMouse movement paths after any rectangle or polygon move, restoring the human-like cursor trajectory that antiban depends on; affects every plugin that uses object/NPC click (which derives click bounds as `Rectangle` or `Polygon`).

### [DONE] `AntibanPlugin` `Timer` created in `startUp()` is never cancelled in `shutDown()`, leaking a daemon thread and keeping the panel alive
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/antiban/AntibanPlugin.java:178-184`, `AntibanPlugin.java:191-196`
- **Type:** performance
- **Found:** iter 11
- **Issue:** `startUp()` creates a `java.util.Timer` at line 178 (`Timer timer = new Timer()`) with a `TimerTask` that calls `SwingUtilities.invokeLater(panel::loadSettings)` every 600 ms. The `Timer` reference is a local variable — it is never stored as a field and `timer.cancel()` is never called in `shutDown()`. After the plugin is disabled, the timer thread keeps running indefinitely, posting `loadSettings` to the EDT every 600 ms. The lambda `panel::loadSettings` also holds a strong reference to the `MasterPanel` instance, preventing it from being garbage-collected even after the panel is removed from the navigation toolbar.
- **Fix:** Promote `timer` to an instance field (`private Timer panelRefreshTimer`), assign it in `startUp()`, and call `panelRefreshTimer.cancel(); panelRefreshTimer = null;` in `shutDown()`. This stops the task, releases the `MasterPanel` reference, and allows the panel to be GC'd.
- **Impact:** Prevents a timer-thread leak and a `MasterPanel` memory leak that occur every time the Antiban plugin is restarted (e.g. when the user toggles it off and on); removes continuous 600 ms EDT work after shutdown.

### [DONE] `searchChildren` allocates a `List<Widget[]>` and a `List<Widget>` per widget node visited during tree traversal
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/widget/Rs2Widget.java:235-241` (text search), `Rs2Widget.java:327-333` (sprite search)
- **Type:** performance
- **Found:** iter 10
- **Issue:** Both `searchChildren(String, Widget, boolean)` and `searchChildren(int, Widget)` build `Stream.of(4 arrays).filter(Objects::nonNull).collect(toList())` to assemble a `List<Widget[]>` on every node visited, then immediately build a second `List<Widget>` for each non-null group via `Arrays.stream(childGroup).filter(...).collect(toList())` just to iterate. A `findWidget` call over a moderately complex interface (e.g. the bank, spell book, or production interface) visits 50–200 widget nodes, allocating ~50 `List<Widget[]>` objects and ~200 `List<Widget>` objects per search. Every `clickWidget(String)`, `hasWidget`, `hasWidgetText`, `sleepUntilHasWidget`, and `findWidget` call triggers this. Scripts that poll widget state on every tick (e.g. waiting for a production interface to close) hit this cost continuously.
- **Fix:** Replace the `Stream.of(...).filter(...).collect(toList())` block with direct null-checked array accesses: `Widget[][] groups = {child.getChildren(), child.getNestedChildren(), child.getDynamicChildren(), child.getStaticChildren()};`. Replace `Arrays.stream(childGroup).filter(...).collect(toList())` inner loop with a direct `for (Widget w : group) { if (w != null && !w.isHidden()) { ... } }`. Zero allocations per node visited.
- **Impact:** Eliminates ~250 transient `List` allocations per `findWidget` call across a typical interface; every plugin that uses text-based widget interaction benefits on every call.

### [DONE] `Rs2PrayerEnum.DEAD_EYE` and `MYSTIC_VIGOUR` track the wrong active-state varbits
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/prayer/Rs2PrayerEnum.java:33-35`
- **Type:** simplification
- **Found:** iter 9
- **Issue:** `DEAD_EYE` is constructed with `VarbitID.PRAYER_EAGLEEYE` as its `varbit` field, and `MYSTIC_VIGOUR` uses `VarbitID.PRAYER_MYSTICMIGHT` — the same varbits as `EAGLE_EYE` and `MYSTIC_MIGHT` respectively. `VarbitID.PRAYER_DEADEYE` (16090) and `VarbitID.PRAYER_MYSTICVIGOUR` (16091) both exist in `VarbitID.java` (lines 10707-10708). Every `isPrayerActive(DEAD_EYE)` call therefore reports Eagle Eye's state, and every `isPrayerActive(MYSTIC_VIGOUR)` reports Mystic Might's state. Prayer-flicking and combat scripts that switch to these new prayers will silently misread game state.
- **Fix:** In `Rs2PrayerEnum.java`, replace `VarbitID.PRAYER_EAGLEEYE` with `VarbitID.PRAYER_DEADEYE` on line 33, and `VarbitID.PRAYER_MYSTICMIGHT` with `VarbitID.PRAYER_MYSTICVIGOUR` on line 35. The correct interface-component indices (line 33's `InterfaceID.Prayerbook.PRAYER21` and line 35's `InterfaceID.Prayerbook.PRAYER24`) and quick-prayer indices also need verification against the game's prayer panel layout for these unlock-gated prayers.
- **Impact:** Corrects silent wrong-prayer-state reads for any script using `DEAD_EYE` or `MYSTIC_VIGOUR`; without the fix, toggling Deadeye will also report success whenever Eagle Eye happens to be active.

### [DONE] `addInventoryRunes` performs 22 full inventory scans per rune-availability check
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/magic/Rs2Magic.java:446-449`
- **Type:** performance
- **Found:** iter 8
- **Issue:** `addInventoryRunes` iterates all 22 `Runes.values()` entries and calls `Rs2Inventory.itemQuantity(rune.getItemId())` for each one. Each `itemQuantity(int id)` call does a full stream pass over `inventoryItems` filtering by ID (line 844-846 of `Rs2Inventory.java`). This means every call to `getRunes()` → `hasRequiredRunes()` → `canCast()` triggers 22 independent inventory scans. Magic scripts that call `canCast` or `hasRequiredRunes` in their main loop (alching, runecrafting, combat spell checks) pay this cost every tick.
- **Fix:** Replace the loop with a single-pass approach: snapshot `Rs2Inventory.all()` once, then for each item call `Runes.byItemId(item.getId())` (the static map already exists at `Runes.java:57`) to look up its rune type in O(1), and accumulate quantities directly. This reduces 22 inventory scans to 1 per `addInventoryRunes` call.
- **Impact:** Reduces 22 inventory stream passes to 1 per rune-check call; every magic script that calls `canCast`, `hasRequiredRunes`, or `getMissingRunes` on every tick benefits directly.

### [DONE] `lootItemBasedOnValue` and `isItemBasedOnValueOnGround` dispatch one blocking client-thread call per ground item for price lookup
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/grounditem/Rs2GroundItem.java:251-253`, `Rs2GroundItem.java:463-465`
- **Type:** performance
- **Found:** iter 7
- **Issue:** `lootItemBasedOnValue(int value, int range)` calls `Rs2GroundItem.getAll(range)` (one client-thread round-trip), then for every item that passes `hasLineOfSight`, calls `Microbot.getClientThread().runOnClientThreadOptional(() -> getItemPrice(...) * quantity)` inside the stream filter (lines 251-253) — one blocking round-trip per item. `isItemBasedOnValueOnGround` at lines 463-465 does the same inside `anyMatch`. The class is deprecated but still actively imported by dozens of combat and slayer plugins that call these methods on every loop tick.
- **Fix:** Batch the price lookups: replace both methods with a single `runOnClientThreadOptional` lambda that iterates `getAll(range)` once, builds a `Map<Integer, Long>` of `itemId → price * quantity`, then returns it. The subsequent `findFirst`/`anyMatch` reads from the pre-built map with no further client-thread dispatch.
- **Impact:** Reduces N blocking client-thread round-trips to 1 per call (N = ground items in range); prevents the script thread from sequentially stalling for up to N × 10 s on a slow client thread.

### [DONE] `WeaponsGenerator.generate()` rebuilds the full weapons `HashMap` on every `getAttackRange()` call
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/combat/Rs2Combat.java:197`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/combat/weapons/WeaponsGenerator.java:19-27`
- **Type:** performance
- **Found:** iter 6
- **Issue:** `getAttackRange()` calls `WeaponsGenerator.generate()` on every invocation. `generate()` allocates three new `HashMap` instances and constructs a `Weapon` object for every entry across all weapon type groups (200+ IDs across ballistae, blowpipes, crossbows, shortbows, longbows, darts, knives, thrownaxes, etc.). The weapon ID→range mapping is entirely static data derived from `WeaponIds` constants — it never changes at runtime. `getAttackRange()` is also called indirectly from `Rs2Npc.java:1358` (e.g. in `getAttackableNpcs` distance-gating), so combat scripts that poll for targets rebuild this map on every tick.
- **Fix:** Add `private static final Map<Integer, Weapon> WEAPONS = WeaponsGenerator.generate()` as a class-level field in `WeaponsGenerator` (or in `Rs2Combat`). Replace the `final Map<Integer, Weapon> weaponsMap = WeaponsGenerator.generate()` call at `Rs2Combat.java:197` with a reference to the cached field.
- **Impact:** Reduces 3 `HashMap` allocations and 200+ `Weapon` instantiations to zero on every `getAttackRange()` call after the first; all combat scripts and NPC range checks benefit on every tick.

### [DONE] `getObjectIdsByName` scans 127,000+ constant fields via reflection on every name-based object lookup, with no caching
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/gameobject/Rs2GameObject.java:1881-1902`, `Rs2GameObject.java:1603`
- **Type:** performance
- **Found:** iter 5
- **Issue:** Every call to a name-based object query (`getGameObject("Oak tree")`, `getTileObject("Bank booth")`, etc.) constructs a predicate via `nameMatches(name, exact)`, which calls `getObjectIdsByName(name)`. That method iterates all public fields of three `ObjectID` classes — `net.runelite.api.ObjectID` (~29 000 fields), `net.runelite.api.gameval.ObjectID` (~98 000 fields), and `microbot.util.gameobject.ObjectID` — using reflection (`clazz.getFields()`, `f.setAccessible(true)`, `f.getInt(null)`) with no memoization. Any script that looks up objects by name on every 600 ms tick (woodcutters, farmers, agility scripts, etc.) pays this 127 000-field reflection cost on every iteration.
- **Fix:** Add a `private static final Map<String, List<Integer>> NAME_TO_IDS_CACHE = new ConcurrentHashMap<>()` and wrap the body of `getObjectIdsByName` with `NAME_TO_IDS_CACHE.computeIfAbsent(name, k -> { ... })`. The scan runs once per unique name and the result is reused on all subsequent calls.
- **Impact:** Reduces 127 000 reflective field reads per tick to a single `HashMap.get` for any name queried more than once; all name-based object searches in every script benefit immediately.

### [DONE] `Rs2NpcManager.getAttackSpeed()` crashes with NullPointerException on unknown NPC ID and logs unconditionally
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/npc/Rs2NpcManager.java:212-214`
- **Type:** performance
- **Found:** iter 4
- **Issue:** `getAttackSpeed(int npcId)` calls `statsMap.get(npcId)` and immediately dereferences the result via `s.toString()` and `s.getAttackSpeed()` with no null check — unlike the parallel `getHealth()` method (line 200-203) which correctly guards with `s != null`. Any combat plugin calling `getAttackSpeed` for an NPC not present in the stats JSON (e.g. recently added monsters, custom NPCs, or a typo in the ID) will throw `NullPointerException` and crash the script thread. Additionally, `Microbot.log(s.toString())` fires unconditionally on every valid call, flooding the log at INFO level during every tick of any combat script that polls this method.
- **Fix:** Add a null guard matching `getHealth()`: `if (s == null) return -1;`. Remove `Microbot.log(s.toString())` entirely (it has no diagnostic value in production and is not guarded by a log-level check).
- **Impact:** Prevents `NullPointerException` crashes in combat scripts targeting NPCs absent from the stats map; eliminates per-tick log spam in any script that calls `getAttackSpeed` in its main loop.

### [DONE] `walkWithStateInternal` runs BFS collision check on every `walkTo` call even when target is far away
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/walker/Rs2Walker.java:129-136`
- **Type:** performance
- **Found:** iter 3
- **Issue:** Line 129 unconditionally runs `Rs2Tile.getReachableTilesFromTile(Rs2Player.getWorldLocation(), distance)` — a BFS over the collision map — before computing `distToTarget` (line 132). When the player is more than `distance` tiles from the target (true for the entire walk except the final approach), the BFS can never find the target, yet it still allocates a `HashMap` and expands all reachable tiles within `distance`. Since scripts call `walkTo` on every tick during a walk, this BFS runs continuously for no benefit.
- **Fix:** Compute `distToTarget` first (move line 132 above line 129) and guard the BFS with `if (distToTarget <= distance)`: only then run `getReachableTilesFromTile`. The second arrival condition (`!walkableCheck && distToTarget <= distance`) already uses `distToTarget` directly and never needed the BFS anyway.
- **Impact:** Eliminates a per-tick BFS allocation and expansion for the entire duration of any walk; scripts that call `walkTo` in a polling loop (virtually all of them) will see this on every iteration until the final few tiles.

### [DONE] `isNearBank(int)` triggers full pathfinder run when no bank object is in the scene
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/bank/Rs2Bank.java:2127-2129`, `Rs2Bank.java:1957-2039`
- **Type:** performance
- **Found:** iter 1
- **Issue:** `isNearBank(int distance)` delegates to `getNearestBank()`, which calls `getPathAndBankToNearestBank()`. When no bank booth `TileObject` is within 20 tiles (e.g. the player is walking toward a bank), the method falls through to `new Pathfinder(...).run()` — a synchronous graph search over all accessible bank `WorldPoint` targets. Any script that polls `isNearBank` while the player is en route to a bank will trigger a full pathfinder run on every loop iteration.
- **Fix:** Replace the body of `isNearBank(int)` with a straight-line distance scan over `BankLocation.values()`: find the minimum `worldPoint.distanceTo2D(Rs2Player.getWorldLocation())` and compare it to `distance`. No pathfinding is needed for a simple proximity check. The parameterised overload `isNearBank(BankLocation, distance)` already does exactly this math; `isNearBank(int)` just needs to supply the nearest location without invoking the pathfinder.
- **Impact:** Eliminates a multi-millisecond synchronous pathfinder invocation from a hot-path proximity check called in every script loop that gates walking to the bank.

## MEDIUM

### [DONE] `reloadPluginList` checks `isInstalled` with an O(N×M) stream scan inside the manifest map loop

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/ui/MicrobotPluginHubPanel.java:831-843`, `MicrobotPluginHubPanel.java:811`
- **Type:** performance
- **Found:** iter 24
- **Issue:** `reloadPluginList(Collection, Map)` streams over `manifestByName.entrySet()` at line 831 and for each manifest checks `installed.stream().anyMatch(im -> im.getClass().getSimpleName().equalsIgnoreCase(simpleName))` at line 839. `installed` is the full list of installed plugins (line 811: `new ArrayList<>(microbotPluginManager.getInstalledPlugins())`). With N manifests (potentially hundreds) and M installed plugins, this inner stream scan is O(N×M) — up to tens of thousands of case-insensitive string comparisons per `reloadPluginList` call. The same pattern for the `pluginsByName` lookup (line 837) already uses a pre-built `Map` for O(1) access, proving the pattern is established.
- **Fix:** Before the `.stream().map()` at line 831, build `Set<String> installedNames = installed.stream().map(im -> im.getClass().getSimpleName().toLowerCase(Locale.ROOT)).collect(Collectors.toSet())`. Replace line 839 with `boolean isInstalled = installedNames.contains(simpleName.toLowerCase(Locale.ROOT))`. This reduces the total cost from O(N×M) to O(N+M).
- **Impact:** Reduces a quadratic scan to linear in the plugin hub rebuild path; with 200 manifests and 50 installed plugins, cuts from 10,000 comparisons to 250 per panel activation.

### [DONE] `rebuildPluginList` calls `getAnnotation(PluginDescriptor.class)` three times per plugin via two filter predicates and one map step

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/ui/MicrobotPluginListPanel.java:179-194`
- **Type:** simplification
- **Found:** iter 24
- **Issue:** `rebuildPluginList()` creates `isExternalPlugin` (line 184) as `plugin.getClass().getAnnotation(PluginDescriptor.class).isExternal()`, uses `.filter(!...hidden())` at line 190 via another `getAnnotation` call, and then assigns `descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class)` at line 194 in the `.map()` step. This is three reflective annotation lookups per plugin on every plugin list rebuild (`onExternalPluginsChanged`, `onProfileChanged`). Java caches annotation results internally, so this is primarily a code clarity issue, but the triple call pattern means all three branches must be consistent.
- **Fix:** In the `.map()` step at lines 192-209, fetch `PluginDescriptor descriptor = plugin.getClass().getAnnotation(PluginDescriptor.class)` once at the top of the lambda and replace the two filter predicates with pre-fetched values by pushing the filter logic into the map+filter step: replace the two `.filter()` calls with a single `.filter(p -> { PluginDescriptor d = p.getClass().getAnnotation(PluginDescriptor.class); return !d.hidden() && (isMicrobotPkg || d.isExternal()); })` that reads the annotation once and checks both conditions.
- **Impact:** Eliminates two redundant reflective annotation lookups per plugin per rebuild; one-pass approach also makes the filtering and mapping logic cohesive and easier to extend.

### [DONE] `Script.run()` reads `Microbot.getClient().getEnergy()` from the script thread, bypassing the thread-safe `Rs2PlayerStateCache`

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/Script.java:89`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/player/Rs2Player.java:1662`
- **Type:** performance
- **Found:** iter 23
- **Issue:** `Script.run()` line 89 calls `Microbot.getClient().getEnergy()` directly from the script thread. `Client.getEnergy()` reads the run-energy varp (`RUNNING_ENERGY_STORED`) from raw game memory — not through the `Rs2PlayerStateCache` that the rest of the framework uses for safe cross-thread varbit/varp access. The same unsafe pattern is repeated in `Rs2Player.getRunEnergy()` at line 1662. Since `run()` is called on the `scheduledExecutorService` thread for every script loop iteration (every ~600 ms), this is a high-frequency off-thread raw client read. While it reads a memory-mapped value rather than a pointer chain (so crashes are unlikely), it is inconsistent with the framework's threading model and can return a value that lags behind the client-thread cache by one server-update cycle.
- **Fix:** In `Script.run()`, replace `Microbot.getClient().getEnergy()` with `Microbot.getVarbitPlayerValue(VarPlayer.RUNNING_ENERGY_STORED)` which routes through `rs2PlayerStateCache` and is explicitly safe from any thread. Update `Rs2Player.getRunEnergy()` identically. Both callers then divide by 100 to get the percentage.
- **Impact:** Aligns the run-energy read in every script's main loop with the framework's thread-safety contract; removes the last raw-client-API read from `Script.run()`, which is called by every running script on every tick.

### [DONE] `interactWithJewelleryBoxWidget` dereferences `widget.getText()` with no null guard, crashing any `useJewelleryBox()` call for an unconfigured destination

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/poh/PohTeleports.java:124-126`
- **Type:** simplification
- **Found:** iter 22
- **Issue:** Line 124 calls `Rs2Widget.findWidget(jewelleryLocationEnum.getDestination().toLowerCase(), ...)` and assigns the result to `widget`. Line 126 immediately calls `widget.getText().contains("<str>")` with no null check. `findWidget` returns `null` when no child widget has text matching the destination (e.g., when the player's jewellery box does not include the requested destination, or during partial UI load). The null dereference throws `NullPointerException` on the script thread for any `useJewelleryBox()` call where the destination widget is absent. `interactWithPortalNexusWidget` already guards against this correctly at line 178 (`if (widget == null) return false`), so the fix pattern is established.
- **Fix:** Add `if (widget == null) { Microbot.log(jewelleryLocationEnum.getDestination() + " widget not found in jewellery box"); return false; }` immediately after line 124. This matches the guard already present in `interactWithPortalNexusWidget`.
- **Impact:** Prevents NPE crashes in any script using `useJewelleryBox()` when the jewellery box is not configured for the requested destination or the interface is still loading; costs one null check to fix.

### [DONE] `PohPortal.findPortalsInPoh()` makes 29 individual full-scene scans to find at most 3 portals

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/poh/data/PohPortal.java:155-163`, `PohPortal.java:122-124`
- **Type:** performance
- **Found:** iter 22
- **Issue:** `findPortalsInPoh()` iterates all 29 `PohPortal` constants at line 157 and calls `portal.getPortal()` for each at line 158. `getPortal()` calls `Rs2GameObject.getGameObject(objectIds)` (line 123) — a full scene scan across all game objects, filtered by the portal's 3-4 object IDs. A POH has at most 3 portal frames, so 26 of the 29 calls will return null after scanning the full scene for nothing. Any walker or transport-builder code that calls `findPortalsInPoh()` to discover the POH portal configuration (e.g., to build POH transport options for route planning) pays the cost of 29 scene scans.
- **Fix:** Invert the scan: collect all game objects in the scene that match any `PohPortal` ID in one pass — `Rs2GameObject.getGameObjects(o -> BY_OBJECT_ID.containsKey(o.getId()))` — then map each found object to its `PohPortal` via a pre-built `Map<Integer, PohPortal> BY_OBJECT_ID`. This is 1 scene scan instead of 29.
- **Impact:** Reduces 29 full-scene scans to 1 per `findPortalsInPoh()` call; any script or pathfinder that enumerates POH portals to build transport options benefits directly.

### [DONE] `Rs2LocalPoint.fromWorldInstance()` reads instance template chunks directly off the client thread from background callers

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/coords/Rs2LocalPoint.java:18-23`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/coords/Rs2WorldPoint.java:132-140`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/walker/Rs2Walker.java:1048,1051,1070`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/shortestpath/pathfinder/CollisionMap.java:191`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/shortestpath/pathfinder/SplitFlagMap.java:52`
- **Type:** performance
- **Found:** iter 21
- **Issue:** `fromWorldInstance()` reads `Microbot.getClient().getTopLevelWorldView().getInstanceTemplateChunks()` at line 18 and `getPlane()` at line 22 directly, with no client-thread dispatch. Its callers include `Rs2Walker.java:1048,1051,1070` (script thread) and the pathfinder's `CollisionMap.java:191` and `SplitFlagMap.java:52` (background pathfinding threads). Instance template chunks are written by the client during instance region loading; reading them from a background thread while the client is updating the instance map can produce a partially-rotated `templateChunk` value, silently placing a `LocalPoint` in the wrong tile and corrupting all subsequent instance-based object/NPC lookups. `getPlane()` also reads the current plane, which can change during floor-transition loading.
- **Fix:** Add a client-thread guard to `fromWorldInstance()`: check `Microbot.getClient().isClientThread()` and, if not on the client thread, dispatch via `runOnClientThreadOptional`. Since both `CollisionMap` and the walker call this synchronously (they need the result before continuing), `runOnClientThreadOptional` with blocking semantics is appropriate. Alternatively, have the pathfinder snapshot `instanceTemplateChunks` once at the start of each pathfinding run (on the client thread) and pass the snapshot into `CollisionMap`/`SplitFlagMap`.
- **Impact:** Prevents silent coordinate corruption in instanced areas (POH, raids, dungeons) that can cause scripts to interact with objects in the wrong room; fixes an unsafe read from both the background pathfinding thread and the walker's script thread.

### [DONE] `getCurrentTab()` reads VarcInt directly from the game client off the client thread; most callers are script threads

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/tabs/Rs2Tab.java:20`, callers: `Rs2Combat.java:60,91`, `Rs2Walker.java:2165,2167`, `Rs2Inventory.java:1384,2108`, `Rs2Prayer.java:67`, `Rs2Magic.java:87,125,127,269`, `Rs2SpellBookSettings.java:46`
- **Type:** performance
- **Found:** iter 20
- **Issue:** `getCurrentTab()` calls `Microbot.getClient().getVarcIntValue(VarClientID.TOPLEVEL_PANEL)` (line 20) — a direct raw client API read that is only safe on the client thread. Unlike `Microbot.getVarbitValue()`, which routes through the cached `rs2PlayerStateCache`, there is no equivalent VarcInt cache. All the callers listed above invoke `getCurrentTab()` directly from script threads without client-thread dispatch, producing an unsafe read. A few callers (Rs2EnsouledHead.java:77, Rs2Magic.java:306,309,321) correctly wrap it in `runOnClientThreadOptional`, proving the hazard is understood but not fixed at the source.
- **Fix:** Maintain a `private static volatile InterfaceTab cachedTab = InterfaceTab.NOTHING_SELECTED` field and subscribe to `VarClientChanged` events for `VarClientID.TOPLEVEL_PANEL` to update it on the client thread. `getCurrentTab()` then returns `cachedTab` with no client API call. All existing callers become safe automatically.
- **Impact:** Eliminates an unsafe off-client-thread client read from the hot path of every tab-check call across nine utility classes; brings `Rs2Tab` in line with the threading model used by the equipment, shop, and inventory caches.

### [DONE] `switchTo()` silently fails for players with no F-key bindings; `TAB_SWITCH_SCRIPT = 915` is declared but never used

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/tabs/Rs2Tab.java:17,67-73`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/globval/enums/InterfaceTab.java:38-57`
- **Type:** feature
- **Found:** iter 20
- **Issue:** `switchTo()` resolves the target tab via `tab.getHotkey()` (line 67), which reads the tab's F-key binding varbit at `InterfaceTab.java:42`. If the player has not bound an F-key to the tab (varbit value 0), `getHotkey()` falls to `default: return -1`, and `switchTo()` logs "Tab X does not have a hotkey assigned" and returns `false` — doing nothing (lines 68-70). This silently breaks every call to `Rs2Tab.switchTo()`, `Rs2Magic.cast()`, `Rs2Combat.setAutoCast()`, `Rs2Bank.switchToTab()`, etc. for players without F-key configuration. Line 17 declares `private static final int TAB_SWITCH_SCRIPT = 915`, the RuneScript ID for the client's own tab-switch script that takes the tab's VarcInt index as its argument, which would bypass F-key requirements entirely — but it is never called.
- **Fix:** In `switchTo()`, replace `Rs2Keyboard.keyPress(hotkey)` with a client-thread invocation of `Microbot.getClient().runScript(TAB_SWITCH_SCRIPT, varcIntIndex)` where `varcIntIndex` is the tab's 0-based VarcInt value (add `getVarcIntIndex()` to `InterfaceTab` returning the integer that `getCurrentTab()`'s switch maps back to for each constant). Fall back to the F-key path only if the script invocation fails. This eliminates the hotkey varbit read entirely.
- **Impact:** Makes `switchTo()` work for all players regardless of F-key configuration; silently broken script behaviours (tab not opening, `setAutoCast` returning false) in setups without F-keys would be fixed; removes dead `TAB_SWITCH_SCRIPT` constant.

### [DONE] `updateWindowTitle()` calls `JFrame.setTitle()` from the scheduler thread — Swing EDT violation on every second

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/breakhandler/BreakHandlerScript.java:836-846` (`updateWindowTitle`), `BreakHandlerScript.java:788` (`resetWindowTitle`), `BreakHandlerScript.java:384-385` (`handleClientShutdown`)
- **Type:** simplification
- **Found:** iter 19
- **Issue:** `updateWindowTitle()` calls `ClientUI.getFrame().setTitle(...)` from the scheduler thread every 1000ms. `JFrame.setTitle()` is a Swing operation that must be called on the Event Dispatch Thread; calling it from a non-EDT background thread violates Swing's single-thread rule. On some JDK/look-and-feel implementations, `setTitle()` internally fires a `propertyChange` event that synchronously attempts to update decorated window chrome from the caller's thread, causing `IllegalStateException` or visual corruption. `resetWindowTitle()` (line 788) and the shutdown path (line 384) have the same issue.
- **Fix:** In both `updateWindowTitle()` and `resetWindowTitle()`, wrap the `setTitle` call with `SwingUtilities.invokeLater(() -> ClientUI.getFrame().setTitle(...))`. `invokeLater` returns immediately, so it does not block the scheduler thread.
- **Impact:** Eliminates a Swing threading violation that can cause intermittent visual glitches or exceptions during title updates; costs one `SwingUtilities.invokeLater` wrapper per method.

### [DONE] `BreakHandlerV2Script.handleInitiatingBreak()` and `handleLogoutRequested()` call `sleep()` inside the `scheduleWithFixedDelay` callback, blocking the executor thread for 2–5 seconds

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/breakhandler/breakhandlerv2/BreakHandlerV2Script.java:277` (`handleInitiatingBreak`), `BreakHandlerV2Script.java:308` (`handleLogoutRequested`)
- **Type:** performance
- **Found:** iter 19
- **Issue:** `handleInitiatingBreak()` calls `sleep(SAFETY_CHECK_DELAY_MS)` (5000ms) inside the `scheduleWithFixedDelay` callback when the player is still in combat. `handleLogoutRequested()` calls `sleep(2000, 3000)` after each logout attempt. `Global.sleep()` calls `Thread.sleep()`, which blocks the executor thread directly. Since `scheduleWithFixedDelay` computes the next delay *after* the task completes, a 5-second block extends each state-machine cycle to 6+ seconds. During these sleeps, the executor thread cannot process the ban-detection check, watchdog updates, or any other state transition — the break handler becomes entirely unresponsive for up to 5 seconds per safety check while waiting for the player to leave combat.
- **Fix:** Remove both `sleep()` calls. For `handleInitiatingBreak`, rely on the already-tracked `safetyCheckAttempts` counter and the 1000ms scheduler interval as the wait mechanism (the method re-enters naturally). For `handleLogoutRequested`, the existing retry-delay logic already defers via `stateChangeTime`; the `sleep(2000, 3000)` after calling `Rs2Player.logout()` is redundant because the next scheduler cycle checks `Microbot.isLoggedIn()` anyway.
- **Impact:** Keeps the executor thread unblocked during safety checks and logout attempts; allows ban detection, watchdog expiry, and other state transitions to proceed on schedule while waiting for safe conditions.

### [DONE] `Rs2Farming.hasQuestRequirement(QuestState)` is a permanently unimplemented placeholder that always returns `true`

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/farming/Rs2Farming.java:231-234`
- **Type:** simplification
- **Found:** iter 18
- **Issue:** `hasQuestRequirement(QuestState questState)` contains only a comment ("This would need to be implemented based on specific quest requirements") and `return true`. The `questState` parameter is never read. The method is `public static`, so plugin authors calling it to gate quest-restricted farm patches (e.g. checking that a quest is `FINISHED` before attempting to access a locked location) silently skip the check and proceed regardless of quest state. There are no callers inside the `Rs2Farming` class itself (all internal use goes through `Rs2Player.getQuestState` directly in `SpiritTree`).
- **Fix:** Either implement the method by delegating to `Rs2Player.getQuestState(Quest)` with a matching enum value, or delete it entirely (as no internal code calls it) so plugin authors are not misled into using a no-op helper. If the method is retained, its signature should accept a `Quest` enum value rather than a `QuestState` (you check what state a quest *is*, not whether it equals a target state, from within this layer).
- **Impact:** Removes a silently broken public API that lets any quest-gated farming action proceed on accounts that have not completed required quests; deleting or correctly implementing it prevents subtle script logic errors.

### [DONE] `hasRequirements()` reads `getLocalPlayer()` and `getTopLevelWorldView()` off the client thread 65 times per `getNearestDepositBox` call

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/depositbox/DepositBoxLocation.java:92`, `Rs2DepositBox.java:451`
- **Type:** performance
- **Found:** iter 17
- **Issue:** `getNearestDepositBox` streams all 65 `DepositBoxLocation` constants and calls `hasRequirements()` on each via `filter(DepositBoxLocation::hasRequirements)` (line 451 of Rs2DepositBox.java). `hasRequirements()` at line 92 of DepositBoxLocation.java calls `Microbot.getClient().getLocalPlayer().getWorldArea().hasLineOfSightTo(Microbot.getClient().getTopLevelWorldView(), worldPoint)` — an unsafe off-client-thread read of both `getLocalPlayer()` (can return null) and `getTopLevelWorldView()` (reads client scene state). This runs 65 times on every `walkToDepositBox()` or `walkToAndUseDepositBox()` call from the script thread, mirroring the same unsafe access pattern already found in `Rs2Combat.getSpecState()` and `Rs2Npc.getAttackableNpcs()`.
- **Fix:** Wrap the `hasLineOfSight` computation inside `hasRequirements()` in a `Microbot.getClientThread().runOnClientThreadOptional(...)` call, or move it to execute once per `getNearestDepositBox` invocation (on the client thread) before the filter, caching which locations are currently in line-of-sight. Alternatively, move the LOS check to only run when the player is in a known restricted area (e.g. near a guild), since most constants' `hasRequirements` does not need LOS.
- **Impact:** Removes 65 unsafe off-client-thread client reads per `walkToDepositBox` call; prevents a potential `NullPointerException` from `getLocalPlayer()` returning null during world hops or login transitions.

### [DONE] `buyItem` performs four separate linear scans of `shopItems` per call via `stream`, `hasStock`, `getSlot`, and `itemBounds`
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/shop/Rs2Shop.java:148-156`, `Rs2Shop.java:340-350`, `Rs2Shop.java:383`, `Rs2Shop.java:460`
- **Type:** performance
- **Found:** iter 16
- **Issue:** `buyItem(String, String)` first streams `shopItems` at line 148-150 to find the item model. It then calls `hasStock(itemName)` at line 154, which iterates `shopItems` again (line 240-246). If in stock, it calls `invokeMenu`, which calls `getSlot(rs2Item.getName())` at line 383 (a third full pass) and then calls `itemBounds(rs2Item)` at line 460, which calls `getSlot` again (a fourth pass). All four passes search for the same item by name in the same unchanged list. `buyItem(int, String)` has the identical pattern at lines 177-186.
- **Fix:** Combine into a single pass at the start of `buyItem`: `Rs2ItemModel rs2Item = shopItems.stream().filter(item -> item.getName().equalsIgnoreCase(itemName) && item.getQuantity() > 0).findFirst().orElse(null); if (rs2Item == null) return false;`. Pass `rs2Item.getSlot()` directly into `invokeMenu` and `itemBounds` instead of re-searching by name. This eliminates the separate `hasStock` and two `getSlot` calls.
- **Impact:** Reduces four O(n) list scans to one per `buyItem` call; `buyItemOptimally` calls `buyItem` repeatedly (up to 50 times for 50-quantity purchases), making the savings proportional to the desired quantity.

### [DONE] `getNearestShopNpc` re-sorts an already-distance-sorted stream
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/shop/Rs2Shop.java:94`, `Rs2Shop.java:123`
- **Type:** simplification
- **Found:** iter 16
- **Issue:** `getNearestShopNpc` calls `Rs2Npc.getNpcs(npcName, exact)` at line 94, which internally sorts the entire NPC stream by distance from the player before returning it (Rs2Npc.java:285-295). At line 123, `getNearestShopNpc` applies a second `.sorted(Comparator.comparingInt(npc -> npc.getDistanceFromPlayer()))` on the already-sorted, filter-only stream. The `.filter()` applied between lines 95-122 never reorders elements, so the post-filter stream is already distance-ordered, and the second sort is O(n log n) wasted work.
- **Fix:** Delete the `.sorted(...)` at line 123. Replace `.sorted(...).findFirst()` with `.findFirst()`. The nearest NPC is already the first element after `getNpcs`'s internal sort.
- **Impact:** Removes one O(n log n) sort per `openShop` / `getNearestShopNpc` call; mirrors the same fix already recommended for `getAttackableNpcs` and `getNpcsForPlayer` in iter 4.

### [DONE] `setQuantity` and `setPrice` dereference widget bounds without null-checking the widget returned by `GrandExchangeWidget`
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/grandexchange/Rs2GrandExchange.java:591-592`, `Rs2GrandExchange.java:620-621`
- **Type:** simplification
- **Found:** iter 15
- **Issue:** `setQuantity(int)` calls `GrandExchangeWidget.getQuantityButton_X()` at line 591 and immediately calls `.getBounds()` on the result at line 592 with no null check. `setPrice(int)` does the same at lines 620-621 with `getPricePerItemButton_X()`. Both widget-getter methods can return null if the GE offer screen hasn't fully rendered yet. Either call from a transition frame throws `NullPointerException` on the script thread. `setQuantity` and `setPrice` are called from `processOffer` (the main buy/sell path), so any partially-loaded GE interface during a buy or sell offer crashes the script.
- **Fix:** In `setQuantity`, add `if (quantityButtonX == null) { log.warn("Quantity button not found"); tries++; continue; }` after line 591. In `setPrice`, add `if (pricePerItemButtonX == null) return;` after line 620. Both callers already handle the failure case (retry loops or returning early).
- **Impact:** Prevents NPE crashes in the buy/sell offer path during GE interface load; costs two null checks to fix.

### [DONE] `enter()` dispatches `KEY_RELEASED` for VK_ENTER twice, sending an orphaned release event to the game client
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/keyboard/Rs2Keyboard.java:166-167`
- **Type:** simplification
- **Found:** iter 14
- **Issue:** `enter()` calls `keyPress(KeyEvent.VK_ENTER)` at line 166, which internally calls `keyHold` (dispatches `KEY_PRESSED`) then `keyRelease` (dispatches `KEY_RELEASED`). Line 167 then dispatches a second `KEY_RELEASED` for VK_ENTER directly. The game client sees: KEY_PRESSED → KEY_RELEASED → KEY_RELEASED. The second release has no matching press; any input handler that tracks press/release pairs will observe an unmatched KEY_RELEASED, leaving key state inconsistent. `enter()` is called from `Rs2GrandExchange`, `Rs2Bank`, `Rs2DepositBox`, `LoginManager`, and `Login` — all high-frequency, correctness-sensitive paths.
- **Fix:** Delete line 167 (`dispatchKeyEvent(KeyEvent.KEY_RELEASED, KeyEvent.VK_ENTER, CHAR_UNDEFINED, 10)`). The VK_ENTER KEY_RELEASED is already dispatched by `keyRelease` inside `keyPress`. Keep `resetEnter()` at line 171 — it dispatches the `KEY_TYPED '\n'` that actually prevents the Jagex-account auto-login, which is the real workaround documented in the comment.
- **Impact:** Removes a spurious unmatched KEY_RELEASED from every `enter()` call; affects GE price-setting, bank withdraw, deposit-box quantity, and login sequences — anywhere a quantity or text is typed and confirmed.

### [DONE] `resetEnter()` bypasses `withFocusCanvas`, the only public method that does not guard canvas focus
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/keyboard/Rs2Keyboard.java:178-188`
- **Type:** simplification
- **Found:** iter 14
- **Issue:** Every other public method in `Rs2Keyboard` (lines 79, 96, 107, 118, 131, 143) routes through `withFocusCanvas`, which temporarily sets `canvas.setFocusable(true)` when the canvas is not already focusable before dispatching key events. `resetEnter()` (lines 178-188) constructs a `KeyEvent` and calls `getCanvas().dispatchEvent(event3)` directly, skipping this guard entirely. If the game canvas is not focusable at the moment `resetEnter()` runs (the same edge case `withFocusCanvas` was introduced to handle), the `KEY_TYPED '\n'` event may not be processed. `resetEnter()` is also called from `Rs2Player.java:447` independently, not always via `enter()`.
- **Fix:** Replace the body of `resetEnter()` with `withFocusCanvas(() -> dispatchKeyEvent(KeyEvent.KEY_TYPED, KeyEvent.VK_UNDEFINED, '\n', 10))`. This eliminates the inline `KeyEvent` construction, reuses the existing `dispatchKeyEvent` helper, and restores the canvas-focus guard.
- **Impact:** Closes a correctness gap where the anti-auto-login workaround silently fails on canvases with non-default focus settings; one-line fix that also removes 7 lines of duplicated key-event construction.

### [DONE] `NaturalMouse.getFactory()` allocates a new factory, speed manager, and 5–8 `Flow` objects on every mouse move
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/mouse/naturalmouse/NaturalMouse.java:81-82`, `naturalmouse/util/FactoryTemplates.java:116-146`
- **Type:** performance
- **Found:** iter 13
- **Issue:** `move(int dx, int dy)` calls `getFactory()` on line 82, which unconditionally allocates a new `MouseMotionFactory`, a new `ArrayList` containing 5–8 `new Flow(FlowTemplates.xxx())` objects, a new `DefaultSpeedManager`, a new `SinusoidalDeviationProvider`, and a new `DefaultNoiseProvider` on every invocation. `getFactory()` is gated on `ActivityIntensity` (which changes only when a script reconfigures antiban) and `Rs2AntibanSettings.simulateFatigue`/`simulateMistakes` (which change only on user action). Despite being effectively static for the lifetime of an activity, this full object graph is reconstructed for every single mouse move — multiple times per click when naturalMouse is enabled.
- **Fix:** Cache the factory in a `private volatile MouseMotionFactory cachedFactory` field. Invalidate the cache (set to `null`) whenever `ActivityIntensity`, `simulateFatigue`, or `simulateMistakes` changes. In `getFactory()`, return the cached instance if non-null; otherwise build and cache a new one. This reduces per-move allocation from ~10 objects to zero for the duration of any activity.
- **Impact:** Removes ~10 object allocations per mouse move; scripts with natural mouse enabled produce 2–10 moves per click, so this saves dozens of allocations per game interaction — directly reduces GC pressure during click-heavy scripts.
- **Outcome:** The cache (af26009f28) was keyed on the intensity alone and has been removed as a bug. The premise does not hold: with fatigue on, the templates read `AntibanPlugin.ticksSinceLogin`, which changes every game tick, and draw fresh fatigue noise per build, so motions froze at stale settings. Do not reintroduce a cache here.

### [DONE] `VirtualMouse.scheduledExecutorService` is sized at 10 threads for a serial-only click dispatch path
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/mouse/VirtualMouse.java:30`
- **Type:** simplification
- **Found:** iter 13
- **Issue:** The constructor at line 30 creates `Executors.newScheduledThreadPool(10)`. This pool is only used at lines 67 and 107 to dispatch a single `clickAction` off the client thread when `isClientThread()` is true. Mouse clicks must be serial — concurrent clicks would corrupt the `entered/exited/moved/pressed/released/clicked` event sequence. With 10 threads, two overlapping `click()` calls submitted simultaneously can both run concurrently inside `handleClick`, interleaving their event dispatches. A single-thread pool would correctly serialize all off-client-thread click dispatches. There is also no `shutdown()` call, so if `VirtualMouse` were ever recreated the 10 threads would be orphaned.
- **Fix:** Replace `Executors.newScheduledThreadPool(10)` with `Executors.newSingleThreadScheduledExecutor()`. This guarantees serial dispatch, eliminates 9 idle threads, and prevents the interleaving correctness issue. Add a `shutdown()` method that calls `scheduledExecutorService.shutdownNow()` and wire it into any future `VirtualMouse` teardown.
- **Impact:** Reduces idle thread count from 10 to 1; prevents concurrent `handleClick` interleaving; costs one word to fix.

### [DONE] `hasDialogueOptionTitle()` crashes with `ArrayIndexOutOfBoundsException` (or NPE) when the dynamic children array is null or empty
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/dialogues/Rs2Dialogue.java:199-200`
- **Type:** simplification
- **Found:** iter 12
- **Issue:** Line 199 calls `dialogueOption.getDynamicChildren()` and assigns the result to `dynamicWidgetOptions` with no null check on the array itself. Line 200 then immediately accesses `dynamicWidgetOptions[0]` with no prior length guard. If `getDynamicChildren()` returns `null` (possible during interface transitions), line 200 throws `NullPointerException`. If it returns a zero-length array (also possible when the dialogue option widget has not yet populated its children), line 200 throws `ArrayIndexOutOfBoundsException`. Both paths crash the script thread.
- **Fix:** Replace lines 199–200 with: `Widget[] dynamicWidgetOptions = dialogueOption.getDynamicChildren(); if (dynamicWidgetOptions == null || dynamicWidgetOptions.length == 0 || dynamicWidgetOptions[0] == null) return false;`
- **Impact:** Prevents script-thread crashes in any plugin calling `hasDialogueOptionTitle` while a dialogue option interface is opening, closing, or partially loaded; costs one null/length guard to fix.

### [DONE] `getDialogueOption()` and `getCombinationOption()` each traverse their option list twice per call
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/dialogues/Rs2Dialogue.java:253`, `Rs2Dialogue.java:257`, `Rs2Dialogue.java:599`, `Rs2Dialogue.java:601`
- **Type:** simplification
- **Found:** iter 12
- **Issue:** `getDialogueOption(String, boolean)` calls `getDialogueOptions()` at line 253 (to check `.isEmpty()`) and again at line 257 (to build the filter stream). `getDialogueOptions()` constructs the list fresh each time by fetching the widget from the client and iterating its dynamic children. The first call's list is discarded immediately. `getCombinationOption(String, boolean)` has the exact same pattern at lines 599 and 601, calling `getCombinationOptions()` twice. Both are called from `keyPressForDialogueOption`, `clickOption(String, boolean)`, and `clickCombinationOption`, which themselves may be called in a tight quest loop.
- **Fix:** In both methods, call the list-builder once and store the result: `List<Widget> options = getDialogueOptions(); if (options.isEmpty()) return null;` then use `options.stream()...` for the search. Eliminates the redundant widget traversal.
- **Impact:** Halves the number of dialogue widget list builds per `getDialogueOption`/`getCombinationOption` call; every `clickOption(String)`, `keyPressForDialogueOption(String)`, and `clickCombinationOption` call benefits.

### [DONE] `hasItemContinue()` is fully subsumed by `hasSpriteContinue()`, causing `DIALOG_SPRITE child 0` to be checked twice on every `hasContinue()` call
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/dialogues/Rs2Dialogue.java:47-49`, `Rs2Dialogue.java:89-91`, `Rs2Dialogue.java:109-111`
- **Type:** simplification
- **Found:** iter 12
- **Issue:** `hasSpriteContinue()` (line 90) already evaluates `Rs2Widget.isWidgetVisible(InterfaceID.DIALOG_SPRITE, 0)` as its very first OR clause. `hasItemContinue()` (line 110) evaluates the identical expression and nothing else. `hasContinue()` at line 47-49 calls both `hasSpriteContinue()` and `hasItemContinue()`, making the `isWidgetVisible(DIALOG_SPRITE, 0)` call redundant on every `hasContinue()` invocation. `hasContinue()` is called from `isInDialogue()`, `clickContinue()`, and every `sleepUntilHasContinue()` poll — so this is double work on every dialogue check in every script.
- **Fix:** Delete `hasItemContinue()` entirely and remove its call from `hasContinue()`. `hasSpriteContinue()` already covers the `DIALOG_SPRITE, 0` case.
- **Impact:** Removes one redundant `isWidgetVisible` call from every `hasContinue()` invocation; dead code removed.

### [DONE] `validateAndSetBreakDurations()` runs three bounds-checks on every game tick despite settings only changing on user action
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/antiban/AntibanPlugin.java:257`, `AntibanPlugin.java:411-429`
- **Type:** performance
- **Found:** iter 11
- **Issue:** `onGameTick()` unconditionally calls `validateAndSetBreakDurations()` at line 257 on every ~600 ms tick. The method performs 3 range comparisons and up to 3 field assignments to guard against out-of-bounds micro-break duration values. Break duration settings only change when the user edits them in the UI, when the plugin starts up (`startUp()` line 176), or when a profile is loaded (`onProfileChanged()` line 209). Running the validation on every tick is wasted work on every game tick for the lifetime of the session.
- **Fix:** Remove the `validateAndSetBreakDurations()` call from line 257 of `onGameTick()`. The two existing call sites in `startUp()` and `onProfileChanged()` are sufficient; add a third call inside the UI panel's save handler when the user commits new break-duration values.
- **Impact:** Removes 3 integer comparisons from every game tick (~100/minute) for every player running any script; trivially free to fix.

### [DONE] `FieldUtil.java` is dead code that uses `sun.misc.Unsafe` internal API
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/antiban/FieldUtil.java:1-27`
- **Type:** simplification
- **Found:** iter 11
- **Issue:** `FieldUtil.setFinalStatic(Field, Object)` is the only method in the class. A full-codebase grep confirms it is never called anywhere in the microbot source tree. The class imports and uses `sun.misc.Unsafe` — an internal JDK API that emits a compiler warning under Java 11 and may become inaccessible in future JDK releases. The static initialiser catches reflection exceptions and silently prints the stack trace via `ex.printStackTrace()` rather than a logger, so init failures would be invisible to the structured logging system.
- **Fix:** Delete `FieldUtil.java` entirely. It is unreachable code that carries a JDK-internal API dependency with no benefit.
- **Impact:** Eliminates one source of `--add-opens` / internal-API compiler warnings and removes a class that could be accidentally invoked by a future refactor.

### [DONE] `clickChildWidget` calls `widget.getChild(childId).getBounds()` with no null check on the child
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/widget/Rs2Widget.java:144-148`
- **Type:** simplification
- **Found:** iter 10
- **Issue:** `clickChildWidget(int id, int childId)` fetches the root widget on the client thread, then calls `widget.getChild(childId).getBounds()` on the script thread with no null guard. `Widget.getChild(int)` returns null when `childId` is out of range for the widget's children array — a common occurrence when called while an interface is transitioning or with a stale child ID. The null dereference throws `NullPointerException` on the script thread, crashing the script. The method also has a double semicolon on line 145 (minor cosmetic).
- **Fix:** Add a null check: `Widget child = widget.getChild(childId); if (child == null) return false; Microbot.getMouse().click(child.getBounds());`. Remove the duplicate semicolon.
- **Impact:** Prevents NPE crashes in any script that calls `clickChildWidget` while an interface is being opened or closed; costs two lines to fix.

### [DONE] `getChildWidgetSpriteID` silently swallows NPE on null widget, making zero indistinguishable from "not found"
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/widget/Rs2Widget.java:124-127`
- **Type:** simplification
- **Found:** iter 10
- **Issue:** `getChildWidgetSpriteID(int id, int childId)` passes `Microbot.getClient().getWidget(id, childId).getSpriteId()` as a lambda to `runOnClientThreadOptional`. If `getWidget` returns null, `getSpriteId()` throws `NullPointerException` inside the `FutureTask`; the exception is caught internally and the optional returns empty → `.orElse(0)`. The caller receives 0, which is a valid sprite ID and indistinguishable from "widget not found." Any caller testing for a sprite ID of 0 to detect absence will silently misbehave when the widget doesn't exist at all.
- **Fix:** Add an explicit null check inside the lambda: `Widget w = Microbot.getClient().getWidget(id, childId); if (w == null) return -1; return w.getSpriteId();` and change `.orElse(0)` to `.orElse(-1)`. Callers can then reliably distinguish "widget absent" from "sprite ID is 0."
- **Impact:** Eliminates a silent correctness bug for any caller testing widget sprite state during interface transitions; `-1` as a sentinel is already used elsewhere in the RuneLite API for absent sprites.

### [DONE] `getBestRangePrayer` and `getBestMagePrayer` silently ignore Deadeye and Mystic Vigour even when unlocked
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/prayer/Rs2Prayer.java:393-423`
- **Type:** feature
- **Found:** iter 9
- **Issue:** `getBestRangePrayer()` (lines 409-423) returns at most `RIGOUR` or `EAGLE_EYE`/`HAWK_EYE`/`SHARP_EYE` and never considers `DEAD_EYE`. `getBestMagePrayer()` (lines 393-407) never considers `MYSTIC_VIGOUR`. Yet `isDeadeyeUnlocked()` (line 458) and `isMysticVigourUnlocked()` (line 461) both exist — they are used nowhere else in the class. Any PvM script calling `getBestRangePrayer()` on a player who has unlocked Deadeye will be directed to activate Eagle Eye instead.
- **Fix:** In `getBestRangePrayer()`, add a check at the top (before the Rigour check) for `isDeadeyeUnlocked() && prayerLevel >= Rs2PrayerEnum.DEAD_EYE.getLevel()` returning `DEAD_EYE`. In `getBestMagePrayer()`, add an equivalent check for `isMysticVigourUnlocked() && prayerLevel >= Rs2PrayerEnum.MYSTIC_VIGOUR.getLevel()` returning `MYSTIC_VIGOUR` (or position it between Augury and Mystic Might based on unlock tier).
- **Impact:** Ensures prayer-selecting scripts activate the strongest available prayer rather than silently falling back to an inferior tier; affects any player who has completed the unlock requirements for Deadeye or Mystic Vigour.

### [DONE] `setQuickPrayers` accumulates quick prayers instead of replacing them
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/prayer/Rs2Prayer.java:163-199`
- **Type:** simplification
- **Found:** iter 9
- **Issue:** The loop at lines 178-188 skips any prayer already set (`if(isQuickPrayerSet(prayer)) continue`) and adds any that are missing. It never removes a prayer that is currently set but is absent from the `prayers` parameter. Calling `setQuickPrayers(new Rs2PrayerEnum[]{PROTECT_MELEE, RIGOUR})` when PIETY was previously set leaves PIETY still active as a quick prayer. The method's name implies a full replacement, not an additive operation, so callers are silently left with extra prayers set.
- **Fix:** Before the add loop (line 178), add a removal loop: `for (Rs2PrayerEnum existing : Rs2PrayerEnum.values()) { if (isQuickPrayerSet(existing) && !Arrays.asList(prayers).contains(existing)) { /* invoke toggle-off for existing */ } }`. This ensures the resulting set of quick prayers exactly matches the input array.
- **Impact:** Prevents unexpected accumulation of stale quick prayers; any script that dynamically reconfigures quick prayers between phases (e.g. melee phase → range phase in raids) will produce correct quick-prayer state after the first call.

### [DONE] `getMissingRunes` silently mutates the caller's input map
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/magic/Rs2Magic.java:555-563`
- **Type:** simplification
- **Found:** iter 8
- **Issue:** `getMissingRunes(Map<Runes, Integer> reqRunes, RuneFilter)` calls `reqRunes.replaceAll(...)` (line 559) and `reqRunes.keySet().removeIf(...)` (line 560) directly on the parameter map — it mutates the caller's object in place and then returns the same reference. Any plugin that saves the result of `getRequiredRunes(spell, N)` and then calls `hasRequiredRunes` or `getMissingRunes` more than once on that same map gets silently wrong results on the second call because the map was drained to empty by the first.
- **Fix:** Replace lines 558-562 with an operation on a defensive copy: `final Map<Runes, Integer> diff = new HashMap<>(reqRunes); diff.replaceAll(...); diff.keySet().removeIf(...); return diff;`. The `reqRunes` parameter is then left unmodified.
- **Impact:** Prevents silent correctness bugs for any plugin author who reuses a rune-requirement map across multiple `hasRequiredRunes`/`getMissingRunes` calls; costs one `HashMap` allocation per call.

### [DONE] `canCast(MagicAction)` dereferences spellbook widget without a null guard
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/magic/Rs2Magic.java:106`
- **Type:** simplification
- **Found:** iter 8
- **Issue:** Line 106 calls `Rs2Widget.getWidget(218, 3).getStaticChildren()` with no null check on the widget. `Rs2Widget.getWidget(218, 3)` can return null while the magic tab is still loading or during interface transitions, causing a `NullPointerException` that crashes the script thread. The parallel `quickCanCast(MagicAction)` method at line 130-131 guards against this correctly with `if (spellbookWidget == null) return false`, but `canCast` does not.
- **Fix:** Add a null guard immediately after line 106's widget fetch: `Widget spellbook = Rs2Widget.getWidget(218, 3); if (spellbook == null || spellbook.getStaticChildren() == null) return false;` and replace the inline dereference accordingly.
- **Impact:** Prevents NPE crashes in any script that calls `cast(MagicAction)` during interface transitions; mirrors the guard already present in `quickCanCast`.

### [DONE] `getRs2Staff` and `getRs2Tome` do O(n) linear enum scans on every equipment rune check
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/magic/Rs2Magic.java:419-429`
- **Type:** performance
- **Found:** iter 8
- **Issue:** `getRs2Staff(int itemID)` (line 419) and `getRs2Tome(int itemID)` (line 425) both call `values()` (cloning the backing array) and do a linear scan for a matching item ID. Both are called from `addEquipmentRunes()` (line 455, 463), which is called every time `getRunes()` runs — i.e., on every `canCast`, `hasRequiredRunes`, or `getMissingRunes` invocation. `Runes.java` already shows the pattern for this fix with its own `BY_ITEM_ID` map.
- **Fix:** Add `private static final Map<Integer, Rs2Staff> BY_ITEM_ID = Arrays.stream(values()).filter(s -> s != NONE).collect(Collectors.toMap(Rs2Staff::getItemID, Function.identity()))` to `Rs2Staff`, and an equivalent map to `Rs2Tome`. Replace the stream scan in `getRs2Staff` and `getRs2Tome` with a single `BY_ITEM_ID.getOrDefault(itemID, NONE)` lookup.
- **Impact:** Reduces each lookup from O(n enum entries) to O(1); every magic script that checks rune availability per tick (alching, runecrafting, combat spell selection) benefits on every loop iteration.

### [DONE] `Rs2LootEngine.Builder.collect()` re-scans the full ground items table and re-applies the base distance filter on every `addXxx()` call
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/grounditem/Rs2LootEngine.java:173-203` (collect), `Rs2LootEngine.java:167-168` (loot validation scan)
- **Type:** performance
- **Found:** iter 7
- **Issue:** Each `addByNames()`, `addCoins()`, `addUntradables()`, `addBones()`, etc. call delegates to `collect()`, which calls `getGroundItems().values().stream().filter(combined)...` — a full scan of the ground items `Table` with the base range/ownership predicate re-evaluated for every item in each scan. A typical combat script chain like `.addByNames().addCoins().addUntradables()` does 3 independent full table scans. The validation pass in `loot()` at line 167-168 performs a 4th, and also recomputes `uniqueKey` for every bucket item a second time.
- **Fix:** Pre-apply the base filter once: at the start of `loot()` (or when the first `add*()` call is made), snapshot `getGroundItems().values().stream().filter(baseRangeAndOwnershipFilter(params)).collect(toList())` into a `Builder` field. Each subsequent `collect()` call then filters that pre-built list rather than re-scanning the table. Cache `Rs2Player.getWorldLocation()` once per `loot()` call instead of once per `collect()` call.
- **Impact:** Reduces M full table scans to 1 per `loot()` invocation (M = number of `addXxx()` calls); combat and slayer scripts that call `Rs2LootEngine.with(params).addByNames().addCoins().addUntradables().loot()` every loop tick benefit on every iteration.

### [DONE] `findBank` and `findDepositBox` create a stream over a 600-element `Integer[]` for every game object in the scene
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/gameobject/Rs2GameObject.java:423`, `Rs2GameObject.java:448`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/gameobject/Rs2BankID.java:4`
- **Type:** performance
- **Found:** iter 5
- **Issue:** Both `findBank(maxSearchRadius)` and `findDepositBox(maxSearchRadius)` pass the predicate `o -> Arrays.stream(Rs2BankID.bankIds).anyMatch(bid -> o.getId() == bid)` to `getGameObjects`. For every `GameObject` in the scene this predicate creates a new `Stream` over the `bankIds` array (~600 `Integer` entries) and performs a linear scan. With ~200 game objects in a populated area, each call does ~120 000 integer comparisons and allocates ~200 stream objects.
- **Fix:** Add `public static final Set<Integer> BANK_ID_SET = new HashSet<>(Arrays.asList(bankIds))` to `Rs2BankID`, then replace both predicates with `o -> Rs2BankID.BANK_ID_SET.contains(o.getId())`. An identical fix applies to the local `grandExchangeBoothIds` array in `findGrandExchangeBooth` (line 455).
- **Impact:** Reduces per-object predicate cost from O(600) to O(1); noticeable on every call to `openBank()` or any code that calls `findBank()` / `findDepositBox()`.

### [DONE] `inCombat()` dispatches two sequential blocking client-thread tasks when one suffices
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/combat/Rs2Combat.java:174-182`
- **Type:** performance
- **Found:** iter 6
- **Issue:** `inCombat()` calls `runOnClientThreadOptional(player::getInteracting)` (line 174) and then, if the result is non-null, calls a second `runOnClientThreadOptional(...)` (line 177) to read `interactingActor.getCombatLevel()` and `player.getAnimation()`. Each `runOnClientThreadOptional` submits a `FutureTask` and blocks the script thread for up to 10 seconds waiting for the client thread to execute it. Two sequential round-trips means the script thread stalls twice every time `inCombat()` is called. Combat scripts typically call this on every 600 ms tick to decide whether to attack.
- **Fix:** Merge both lambdas into a single `runOnClientThreadOptional`: fetch `player.getInteracting()` inside the lambda, null-check it there, check `combatLevel`, and return `player.getAnimation() != -1 || player.isInteracting()` in one pass. This halves the number of client-thread round-trips from 2 to 1 per `inCombat()` call.
- **Impact:** Halves the client-thread scheduling overhead for every `inCombat()` call; combat scripts calling this in their main polling loop benefit on every tick.

### [DONE] `getSpecState()` reads a client widget directly off the client thread with a hard crash on null
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/combat/Rs2Combat.java:147-151`, `Rs2Combat.java:108-113`
- **Type:** performance
- **Found:** iter 6
- **Issue:** `getSpecState()` calls `Microbot.getClient().getWidget(WidgetInfo.MINIMAP_SPEC_ORB.getId() + 4)` directly (line 147) and immediately throws `RuntimeException("Somehow the spec orb is null!")` if the widget is null (line 148). This method is called from `setSpecState()` (line 111), which is invoked by combat scripts from the script thread. Widget access via `getClient()` is only safe on the client thread; reading widget state off the client thread can return null during interface updates even when the spec orb is genuinely present, causing a spurious `RuntimeException` that crashes the script. Separately, `setSpecState()` also calls `Microbot.getClient().getVarpValue(VarPlayer.SPECIAL_ATTACK_PERCENT)` at line 108 — another direct off-thread client read.
- **Fix:** Wrap both reads in `runOnClientThreadOptional`: fetch the spec varbit and the widget sprite ID together in a single client-thread call and return them as an `Optional<int[]>`. Replace the hard `throw` in `getSpecState()` with an `Optional.empty()` result (return `false` as the default if the widget is unavailable instead of crashing).
- **Impact:** Prevents intermittent `RuntimeException` crashes in any combat script that calls `setSpecState` when interface updates are in flight; also removes an unsafe off-thread widget read from a method called on every spec-check tick.

### [DONE] `getAttackableNpcs()` re-sorts an already-sorted stream and reads client state off the client thread
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/npc/Rs2Npc.java:381-387`, `Rs2Npc.java:406-416`
- **Type:** performance
- **Found:** iter 4
- **Issue:** `getNpcs(predicate)` already distance-sorts its results inside the client-thread lambda (lines 285-295) and returns an `Arrays.stream` of a pre-sorted array. Both `getAttackableNpcs()` overloads then apply a second `.sorted(Comparator.comparingInt(...))` on the already-sorted stream (lines 384-386 and 413-415), performing an unnecessary O(n log n) re-sort. The comparator in both cases calls `Microbot.getClient().getLocalPlayer().getLocalLocation()` directly from the script thread (unsafe off-client-thread access that can return null).
- **Fix:** Remove the `.sorted(...)` from lines 384-386 and 413-415 in both `getAttackableNpcs` overloads. Since `.filter()` only removes elements and never reorders them, the stream arriving at `.sorted()` is already distance-ordered, so removing the second sort is safe and correct.
- **Impact:** Eliminates a redundant O(n log n) sort and an unsafe client-thread read in every `getAttackableNpcs` call; combat scripts that call this each tick (to pick a new target) will benefit on every iteration.

### [DONE] `getNpcsForPlayer(String, boolean)` sorts an already-sorted stream a second time
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/npc/Rs2Npc.java:168-173`
- **Type:** simplification
- **Found:** iter 4
- **Issue:** `getNpcsForPlayer(Predicate)` collects into a `List` that is already distance-sorted (line 134-136). `getNpcsForPlayer(String, boolean)` at line 172 streams that list and immediately applies `.sorted(Comparator.comparingInt(...))` — a second full sort on data that is already in distance order. The name filter at lines 169-171 only removes non-matching elements; it never reorders them, so the post-filter sort is redundant.
- **Fix:** Remove the `.sorted(Comparator.comparingInt(value -> value.getLocalLocation().distanceTo(playerLocation)))` call at line 172. The method already returns a correctly ordered stream without it.
- **Impact:** Removes one unnecessary O(n log n) sort from every `getNpcsForPlayer(String, boolean)` call; scripts that use this to track which NPCs are attacking the player call it on every tick.

### [DONE] `getClosestTileIndex` makes two stream passes over `path` when one suffices
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/walker/Rs2Walker.java:1253-1258`
- **Type:** performance
- **Found:** iter 3
- **Issue:** After computing `startPoint = path.stream().min(Comparator.comparingInt(a -> _tiles.getOrDefault(a, Integer.MAX_VALUE)))` (line 1253), the method immediately runs a second full pass `path.stream().allMatch(a -> _tiles.getOrDefault(a, Integer.MAX_VALUE) == Integer.MAX_VALUE)` to set `noMatchingTileFound` (line 1257). This is redundant: if every element maps to `Integer.MAX_VALUE` in `_tiles`, then the `min` also maps to `Integer.MAX_VALUE`. The two conditions are equivalent. `getClosestTileIndex` is called on every `processWalk` invocation (line 239), which itself is called recursively until the player arrives.
- **Fix:** Delete lines 1257-1258 and replace the `if (startPoint == null || noMatchingTileFound)` condition on line 1264 with `if (startPoint == null || _tiles.getOrDefault(startPoint, Integer.MAX_VALUE) == Integer.MAX_VALUE)`.
- **Impact:** Saves one O(path.length) stream pass on every `processWalk` invocation; path lists can be 100-500 elements long for cross-region walks.

### [DONE] `getTransportsForPath` scans `path` twice for the same teleport destination
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/walker/Rs2Walker.java:865-867`, `Rs2Walker.java:895-905`
- **Type:** performance
- **Found:** iter 3
- **Issue:** At lines 865-867, `path.contains(transport.getDestination())` performs an O(n) list scan, and then `path.indexOf(transport.getDestination())` immediately performs a second O(n) scan of the same list for the same value. At lines 895-905, `indexOfDestination` is computed at line 896, but then `path.indexOf(transport.getDestination())` is called again at line 905, making the same scan a third time within the same method invocation for non-teleportation transports.
- **Fix:** Replace the `path.contains` + `path.indexOf` pair at lines 865-867 with a single `int destIndex = path.indexOf(transport.getDestination()); if (destIndex != -1)`. At line 905, reuse the `indexOfDestination` variable already computed at line 896 instead of calling `path.indexOf` again.
- **Impact:** Eliminates 2-3 redundant O(n) list scans per transport evaluated during route planning; each scan on a 300-tile path costs ~300 `WorldPoint.equals` comparisons.

### [DONE] `equipment()` public method bypasses the thread-safe cache
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/equipment/Rs2Equipment.java:28-30`
- **Type:** performance
- **Found:** iter 2
- **Issue:** The public `equipment()` method calls `Microbot.getClient().getItemContainer(InventoryID.WORN)` directly with no thread-safety guard. It is only safe on the client thread; script threads will observe a race with the client thread's container updates. The thread-safe cached alternative `items()` (line 32) exists and is what all internal methods already use. The example usage in `ExampleScript.java:249` wraps the call in `invoke()`, but nothing in the API signature indicates this requirement — plugin authors calling `Rs2Equipment.equipment()` directly from a script loop will silently get stale or null data.
- **Fix:** Deprecate `equipment()` in favour of `items()` with a Javadoc note ("@deprecated Use {@link #items()} for thread-safe access to cached equipment"). If raw `ItemContainer` access is genuinely needed (e.g. for quantity checks not surfaced by `Rs2ItemModel`), rename the method to `equipmentUnsafe()` and add an `assert Microbot.getClient().isClientThread()` guard.
- **Impact:** Prevents silent correctness bugs in any plugin that reads `equipment()` off the client thread; mirrors the fix already applied to `Rs2Inventory.getFirstEmptySlot()`.

### [DONE] `invokeMenu` uses an 11-branch if-else chain to map equipment slots to widget params
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/equipment/Rs2Equipment.java:344-377`
- **Type:** simplification
- **Found:** iter 2
- **Issue:** The `invokeMenu` method resolves `param1` and the `getSafeBounds` child ID via an `if … else if` ladder with one branch per `EquipmentInventorySlot` (11 branches, up to 22 comparisons in the worst case). Adding a new slot (e.g. a future secondary weapon slot) requires editing the middle of a large method, and it is easy to leave a slot unhandled — which silently falls through to the full-canvas default rectangle instead of the correct widget bounds.
- **Fix:** Replace the chain with a `private static final Map<Integer, int[]> SLOT_PARAMS` populated in a static initialiser: keys are `EquipmentInventorySlot.X.getSlotIdx()`, values are `int[]{param1, childId}`. The body of `invokeMenu` then becomes a single `int[] params = SLOT_PARAMS.getOrDefault(rs2Item.getSlot(), null)` lookup followed by a null check.
- **Impact:** O(1) lookup instead of O(11) linear scan; adding or changing a slot mapping requires touching only the static map, not the method body.

### [DONE] `getFirstEmptySlot()` accesses raw client API off the client thread
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/inventory/Rs2Inventory.java:1050-1056`
- **Type:** performance
- **Found:** iter 1
- **Issue:** `getFirstEmptySlot()` calls `inventory()` (line 1053), which calls `Microbot.getClient().getItemContainer(InventoryID.INV)` directly. That call is only safe on the client thread; from a script thread it can return `null` silently, causing the method to loop over a null container and return -1 incorrectly — exactly what the `// TODO: might be broken` comment flags. The cached `inventoryItems` list already excludes empty slots and is safe to read from any thread.
- **Fix:** Replace the entire loop body with a check of which slot indices 0–27 are absent from the cached `inventoryItems` list: `IntStream.range(0, CAPACITY).filter(i -> inventoryItems.stream().noneMatch(x -> x.getSlot() == i)).findFirst().orElse(-1)`. Remove the `// TODO` comment.
- **Impact:** Makes the method thread-safe and reliable; currently it silently returns -1 (no empty slot found) from script threads, causing downstream logic to fail.

### [DONE] `containsAll(int/String)` performs N full inventory scans for N items
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/inventory/Rs2Inventory.java:323-335`
- **Type:** performance
- **Found:** iter 1
- **Issue:** `containsAll(int... ids)` calls `Arrays.stream(ids).allMatch(Rs2Inventory::contains)`. Each `contains(int id)` call streams the full `inventoryItems` list, so checking K IDs requires K complete passes. `containsAll(String... names)` has the same structure. Plugin authors frequently call these to gate banking/crafting logic, so this runs in every script loop.
- **Fix:** Implement each as a single pass: collect the present item IDs (or lowercased names) into a `Set`, then check all required IDs/names against the set in O(1) each. For example: `Set<Integer> present = inventoryItems.stream().map(Rs2ItemModel::getId).collect(toSet()); return Arrays.stream(ids).allMatch(present::contains);`
- **Impact:** Reduces K inventory scans to 1 for every `containsAll` call; most noticeable when scripts check for 4–6 supplies simultaneously on every tick.

### [DONE] `updateTabCounts()` issues 9 varbit reads on every bank-item interaction
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/bank/Rs2Bank.java:2722-2731` (updateTabCounts), `Rs2Bank.java:2764-2778` (getItemTabForBankItem), `Rs2Bank.java:109-114` (invokeMenu call site)
- **Type:** performance
- **Found:** iter 1
- **Issue:** Every withdraw or scroll-to-slot operation calls `invokeMenu` → `getItemTabForBankItem` → `updateTabCounts`, which issues 9 separate `Microbot.getVarbitValue()` calls (one per bank tab). Bank tab counts only change when the player reorganises tabs, not on each item interaction — so these reads are redundant on every call.
- **Fix:** Read the 9 tab-count varbits once when the bank opens (e.g. inside `updateLocalBank` on `ItemContainerChanged`) and store them in `bankTabCounts`. Add a dirty flag or simply re-read them only when a tab-open or tab-close event is detected. `getItemTabForBankItem` should then read the pre-populated array without calling `updateTabCounts`.
- **Impact:** Cuts 9 varbit reads from every single bank item interaction; noticeable when rapidly withdrawing or depositing multiple stacks.

### [DONE] `makeInventorySpace()` re-scans the full inventory inside its deposit loop
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/bank/Rs2Bank.java:738-787`
- **Type:** performance
- **Found:** iter 1
- **Issue:** The method first groups inventory items by ID via `Collectors.groupingBy` (line 740), discarding the per-group `List<Rs2ItemModel>` sizes. Then, inside the loop (line 762), it calls `Rs2Inventory.all().stream().filter(invItem -> invItem.getId() == itemId).count()` to recompute `slotsUsedByItem` — a full O(n) inventory scan for each loop iteration, making the overall complexity O(n²) in inventory size.
- **Fix:** During the grouping step, retain the group's `.size()` alongside the representative `Rs2ItemModel`; assign `slotsUsedByItem = group.size()` directly. No second scan is needed.
- **Impact:** Removes repeated full-inventory scans during space-making; most visible when the inventory holds many distinct item types (e.g. mixed loot before a deposit).

## LOW

### [DONE] `MicrobotPluginSearch.search()` ignores `getKeywords()` and only filters by display name, making author and tag searches return nothing

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/ui/search/MicrobotPluginSearch.java:16-17`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/ui/MicrobotPluginHubPanel.java:223-229`
- **Type:** feature
- **Found:** iter 24
- **Issue:** `MicrobotPluginSearch.search()` filters only on `plugin.getSearchableName().toLowerCase().contains(q)` (line 17). `SearchablePlugin` also exposes `getKeywords()`, which `PluginItem` populates with the plugin's description words, all authors, and all tags (lines 223-229 of `MicrobotPluginHubPanel.java`). None of these keywords are searched. A user who types the plugin author's name or a tag like "woodcutting" or "combat" in the hub's search bar will see no results even when matching plugins exist. The RuneLite upstream `PluginHub` search correctly checks both name and keywords.
- **Fix:** In `search()`, extend the filter predicate: `plugin.getSearchableName().toLowerCase().contains(q) || plugin.getKeywords().stream().anyMatch(k -> k.toLowerCase().contains(q))`. Convert `q` to lowercase once before the stream (already done at line 14) and reuse it. The `getKeywords()` call is already part of the `SearchablePlugin` contract.
- **Impact:** Allows users to find plugins by typing an author name, a tag, or a keyword from the description; currently typing anything other than the exact display name returns nothing.

### [DONE] `PohPortal.getPohPortal()`, `PohTeleports.isFairyRing()`, and `PohTeleports.isSpiritTree()` use linear scans where an O(1) map/set lookup is possible

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/poh/data/PohPortal.java:142-153`, `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/poh/PohTeleports.java:232-237`
- **Type:** simplification
- **Found:** iter 22
- **Issue:** `getPohPortal(GameObject)` (line 147) loops over all 29 `PohPortal` constants and for each calls `Arrays.stream(pohPortal.objectIds).anyMatch(id -> id == objId)` — allocating a new `Stream<Integer>` per constant. In the worst case, this is 29 stream allocations and 29×4=116 comparisons per lookup. `isFairyRing(TileObject)` (line 233) and `isSpiritTree(TileObject)` (line 237) call `FAIRY_RING_IDS.stream().anyMatch(...)` and `SPIRIT_TREE_IDS.stream().anyMatch(...)` respectively, allocating a new `Stream<Integer>` per `TileObject` evaluated by `Rs2GameObject.getGameObject()`. These predicates run for every `TileObject` in the scene each time `getFairyRings()` or `getSpiritTree()` is called.
- **Fix:** Add `private static final Map<Integer, PohPortal> BY_OBJECT_ID` built once at class load from all (portal, id) pairs across `PohPortal.values()`. Replace the loop+stream in `getPohPortal` with `return BY_OBJECT_ID.get(objId)`. Convert `FAIRY_RING_IDS` and `SPIRIT_TREE_IDS` to `Set<Integer>` fields and use `.contains(tileObject.getId())` in `isFairyRing` and `isSpiritTree`, eliminating the per-check stream allocation.
- **Impact:** Reduces `getPohPortal` from up to 29 stream allocations + 116 comparisons to a single hash map lookup; reduces each per-tile predicate from a stream allocation + linear scan to a `Set.contains` check; every call to `getFairyRings()`, `getSpiritTree()`, and transport builder portal lookups benefits.

### [DONE] `depositAllExcept` and three other varargs overloads rebuild a stream over the exclusion array for every inventory item evaluated

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/depositbox/Rs2DepositBox.java:147`, `Rs2DepositBox.java:158`, `Rs2DepositBox.java:214`, `Rs2DepositBox.java:225`
- **Type:** simplification
- **Found:** iter 17
- **Issue:** `depositAllExcept(Integer... ids)` at line 147 passes `x -> Arrays.stream(ids).noneMatch(id -> id == x.getId())` to `depositAll(Predicate)`. `Arrays.stream(ids)` creates a new `IntStream` (via boxing) for every inventory item the predicate is evaluated against — with 28 inventory slots and 5 excluded IDs, that is 28 stream allocations per call. `depositAllExcept(String... names)` at line 158, `depositAll(Integer... ids)` at line 214, and `depositAll(String... names)` at line 225 have the exact same pattern.
- **Fix:** Convert the varargs to a `Set` once before building the predicate. For example: `Set<Integer> idSet = new HashSet<>(Arrays.asList(ids)); return depositAll(x -> idSet.contains(x.getId()));`. An equivalent `Set<String>` fix applies to the name overloads.
- **Impact:** Reduces N stream allocations to 0 per `depositAllExcept` call (N = inventory size); trivial fix that harmonises with the same recommendation already made for `disableAllPrayersExcept` in iter 9.

### [DONE] `Rs2Shop` contains 17 `System.out.println` calls that bypass the `@Slf4j` logger and produce unfiltered stdout noise
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/shop/Rs2Shop.java:152,155,181,184,236-237,245,258-259,264,268,288,301,304,316,328,384,414`
- **Type:** simplification
- **Found:** iter 16
- **Issue:** The class is annotated `@Slf4j` but 17 debug/info messages throughout `buyItem`, `hasStock`, `hasMinimumStock`, `storeShopItemsInMemory`, and `invokeMenu` use `System.out.println` directly. These bypass the SLF4J log level system, are always emitted regardless of log configuration, and are invisible to any log aggregation or filtering tooling. The remaining microbot utilities consistently use `log.info`/`log.debug`/`log.warn`.
- **Fix:** Replace each `System.out.println(...)` with the appropriate `log.debug(...)` or `log.warn(...)` call. Trace-level messages (e.g., "Checking if item X is in stock") should be `log.debug`; warnings (e.g., "X isn't in stock") can remain as `log.warn`. Remove the redundant "Amount of items in the shop" prints (already implied by the check failing).
- **Impact:** Eliminates stdout pollution on every shop interaction; allows operators to suppress shop diagnostics via log configuration without code changes.

### [DONE] `dispatchKeyEvent` calls `getCanvas()` twice for a single event dispatch
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/keyboard/Rs2Keyboard.java:61-68`
- **Type:** simplification
- **Found:** iter 14
- **Issue:** `dispatchKeyEvent` calls `getCanvas()` at line 61 to pass as the event source to `new KeyEvent(getCanvas(), ...)`, then calls `getCanvas()` again at line 68 for `getCanvas().dispatchEvent(event)`. Two calls to `Microbot.getClient().getCanvas()` are made where one suffices. `dispatchKeyEvent` is called on every character of every `typeString` call and on every `keyHold`, `keyRelease`, `holdShift`, `releaseShift`, and (after fixing `resetEnter`) `resetEnter` invocation.
- **Fix:** Hoist to a single local variable at the top of the method: `Canvas canvas = getCanvas(); KeyEvent event = new KeyEvent(canvas, id, System.currentTimeMillis() + delay, 0, keyCode, keyChar); canvas.dispatchEvent(event);`. This also makes the source and dispatch target visibly the same object.
- **Impact:** Removes one redundant `getCanvas()` call per key event dispatch; costs one local variable to fix.

### [DONE] `private boolean exited` in `VirtualMouse` is set but never read — dead state
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/mouse/VirtualMouse.java:25`, `VirtualMouse.java:248`, `VirtualMouse.java:255`
- **Type:** simplification
- **Found:** iter 13
- **Issue:** `private boolean exited = true` (line 25) is assigned `true` inside `exited()` (line 248) and `false` inside `entered()` (line 255), but it is never read anywhere in the class. Because it is `private`, no subclass can observe it either. The original intent may have been to track whether the cursor was inside the canvas to avoid firing a spurious `MOUSE_ENTERED` on the first click, but since it is never consumed the field has no effect.
- **Fix:** Remove the `exited` field and the two assignments. If tracking entered/exited state is genuinely needed in the future, add a getter and document its contract.
- **Impact:** Removes dead state; makes the code's intent clearer.

### [DONE] `takeMicroBreakByChance()` uses `Math.random()` instead of the project's `Rs2Random.diceFractional()`
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/antiban/Rs2Antiban.java:349`
- **Type:** simplification
- **Found:** iter 11
- **Issue:** Line 349 uses `Math.random() < Rs2AntibanSettings.microBreakChance` to decide whether to trigger a micro-break. Every other probabilistic decision in the same class uses `Rs2Random.diceFractional()` (e.g. `actionCooldown()` at line 276 and `performActionCooldown()` at line 303). `Math.random()` delegates to `ThreadLocalRandom.current().nextDouble()`, a different RNG source than `Rs2Random`, making the micro-break roll inconsistent with the rest of the antiban system's randomness model.
- **Fix:** Replace `Math.random() < Rs2AntibanSettings.microBreakChance` with `Rs2Random.diceFractional(Rs2AntibanSettings.microBreakChance)` to match the usage pattern in `actionCooldown()` on line 276.
- **Impact:** Eliminates an inconsistent RNG call; one-line fix that harmonises the antiban system's randomness source.

### [DONE] `sleepUntilHasWidget` traverses the full widget tree twice for one boolean result
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/widget/Rs2Widget.java:35-38`
- **Type:** simplification
- **Found:** iter 10
- **Issue:** `sleepUntilHasWidget(String text)` calls `sleepUntil(() -> findWidget(text, null, false) != null)` on line 36, then calls `findWidget(text, null, false) != null` again on line 37 for the return value. `sleepUntil` only exits when the predicate returns true (or on timeout), so the second `findWidget` call performs an entire widget-tree traversal redundantly — the first call inside `sleepUntil` already confirmed the widget is present.
- **Fix:** Replace both lines with `return sleepUntil(() -> findWidget(text, null, false) != null);`. `sleepUntil` already returns a boolean indicating whether the condition was met.
- **Impact:** Removes one full widget-tree traversal per `sleepUntilHasWidget` call; every script that polls for UI elements (dialogue boxes, processing interfaces) benefits.

### [DONE] `matchesWildCardText` declares a local `actions` variable it never uses, calling `widget.getActions()` three times
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/widget/Rs2Widget.java:832-835`
- **Type:** simplification
- **Found:** iter 10
- **Issue:** Lines 832-835 call `widget.getActions()` three times: once in the null check, once in the (unused) `String[] actions` assignment, and once in the for-each loop header. The local variable `actions` is never read — the loop iterates over a fresh `widget.getActions()` call instead. Three method calls are performed where one suffices. `matchesWildCardText` is called once per widget in `findWidgetsWithAction`, which iterates all children of a widget group.
- **Fix:** Remove the unused `String[] actions = widget.getActions();` assignment (line 833). Replace the for-each loop at line 835 with `String[] actions = widget.getActions(); for (String action : actions)` (assign once, null-check already done on line 832, use the variable in the loop).
- **Impact:** Removes two redundant `widget.getActions()` calls per widget evaluated in `findWidgetsWithAction`; costs one line to fix.

### [DONE] `disableAllPrayersExcept` allocates a new `List` wrapper on every stream predicate evaluation and `getPrayerPoints()` is missing
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/prayer/Rs2Prayer.java:322-325`, `Rs2Prayer.java` (no `getPrayerPoints` method)
- **Type:** simplification
- **Found:** iter 9
- **Issue:** `disableAllPrayersExcept(Rs2PrayerEnum[], boolean)` (line 324) evaluates `!Arrays.asList(prayersToKeep).contains(prayer)` inside a `filter` predicate. `Arrays.asList(prayersToKeep)` creates a new `List` wrapper on every invocation of the predicate — once per active prayer (~30 varbits checked). Separately, the CLAUDE.md quick-reference card documents `Rs2Prayer.getPrayerPoints()` as a commonly used method but no such method exists; plugin authors must reach directly into `Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER)`.
- **Fix:** For `disableAllPrayersExcept`: convert `prayersToKeep` to a `Set<Rs2PrayerEnum>` once before the stream: `Set<Rs2PrayerEnum> keepSet = new HashSet<>(Arrays.asList(prayersToKeep));` and use `!keepSet.contains(prayer)` in the predicate. For the missing helper: add `public static int getPrayerPoints() { return Microbot.getClient().getBoostedSkillLevel(Skill.PRAYER); }` to `Rs2Prayer`.
- **Impact:** Eliminates up to 30 redundant `List` allocations per `disableAllPrayersExcept` call; adds the documented convenience method so plugin authors don't need to access the raw client API for a basic check.

### [DONE] `interact(InteractModel, String)` computes `LocalPoint.fromWorld` twice for the same location
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/grounditem/Rs2GroundItem.java:79`, `Rs2GroundItem.java:108`
- **Type:** simplification
- **Found:** iter 7
- **Issue:** `interact(InteractModel, String)` calls `LocalPoint.fromWorld(client, groundItem.getLocation())` at line 79 and stores the result in `localPoint`. At line 80 it returns early if `localPoint == null`. At line 108 it calls `LocalPoint.fromWorld(client, groundItem.location)` again (same field, same world point) and assigns it to `localPoint1`. Since `localPoint` is already confirmed non-null at this point and resolves the same `WorldPoint`, `localPoint1` is always identical to `localPoint`.
- **Fix:** Replace `LocalPoint localPoint1 = LocalPoint.fromWorld(Microbot.getClient(), groundItem.location)` at line 108 with `LocalPoint localPoint1 = localPoint` (reuse the value already computed at line 79).
- **Impact:** Removes one redundant `LocalPoint.fromWorld` resolution per ground item interaction; costs one word to fix.

### [DONE] `fetchGameObjects(Predicate, WorldPoint)` silently calls `fetchTileObjects` due to a copy-paste error
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/gameobject/Rs2GameObject.java:1681-1683`
- **Type:** simplification
- **Found:** iter 5
- **Issue:** The private 2-argument `fetchGameObjects(Predicate<? super T>, WorldPoint)` overload delegates to `fetchTileObjects(predicate, anchor, Constants.SCENE_SIZE)` instead of `fetchGameObjects(predicate, anchor, Constants.SCENE_SIZE)`. If this overload were ever called directly, `GameObject`s would be skipped entirely and `TileObject`s scanned twice. It is currently unreachable (all callers use the 3-argument form), but is a latent bug waiting for a future refactor.
- **Fix:** Change line 1682 from `return fetchTileObjects(predicate, anchor, Constants.SCENE_SIZE)` to `return fetchGameObjects(predicate, anchor, Constants.SCENE_SIZE)`.
- **Impact:** Eliminates a latent correctness bug; costs one line to fix.

### [DONE] `getTransportsForPath` builds string argument for `log.info` unconditionally
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/walker/Rs2Walker.java:921-922`
- **Type:** performance
- **Found:** iter 3
- **Issue:** `log.info("\n\nFound " + transportList.size() + " transports for path from " + path.get(0) + " to " + path.get(path.size() - 1))` uses string concatenation, which evaluates and allocates the string before the SLF4J level check inside `log.info`. Even when INFO logging is disabled, this allocates at least two intermediate `String` objects and calls `WorldPoint.toString()` twice on every route-planning call.
- **Fix:** Switch to parameterised logging: `log.info("\n\nFound {} transports for path from {} to {}", transportList.size(), path.get(0), path.get(path.size() - 1))`. SLF4J then defers object-to-string conversion until it is certain a handler will consume the message.
- **Impact:** Avoids two `WorldPoint.toString()` calls and at least one string allocation per route-planning call; most visible in builds that suppress INFO logs (production runs).

### [DONE] `EquipmentInventorySlot.values()` array cloned on every `storeEquipmentItemsInMemory` and `isWearing` call
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/equipment/Rs2Equipment.java:44`, `Rs2Equipment.java:162`
- **Type:** performance
- **Found:** iter 2
- **Issue:** `storeEquipmentItemsInMemory` (line 44) calls `EquipmentInventorySlot.values()` in the loop bound, and `isWearing(names, exact, slots, areSearchSlots)` (line 162) calls it to build the inverse slot set. Java enum `values()` clones the backing array on each call. `storeEquipmentItemsInMemory` fires on every `ItemContainerChanged` for the worn container; `isWearing(…, areSearchSlots)` is called in combat and walker hot-paths (e.g. `SpecialAttackConfigs.useSpecWeapon`, `Rs2Walker`).
- **Fix:** Add `private static final EquipmentInventorySlot[] ALL_SLOTS = EquipmentInventorySlot.values();` and use it in both call sites instead of `EquipmentInventorySlot.values()`.
- **Impact:** Removes one transient array allocation per worn-container event and per `isWearing` call that uses the `areSearchSlots` overload; minor but costs nothing to fix.

### [DONE] `getLast(int id)` materialises filtered stream into a full array to read one element
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/inventory/Rs2Inventory.java:725-728`
- **Type:** simplification
- **Found:** iter 1
- **Issue:** `getLast(int id)` collects all matching items via `items(...).toArray(Rs2ItemModel[]::new)`, allocates an `Rs2ItemModel[]`, then reads only the last index. The array is allocated and immediately discarded.
- **Fix:** Replace with `items(item -> item.getId() == id).reduce((a, b) -> b).orElse(null)` — no allocation needed.
- **Impact:** Removes one transient array allocation per call; minor but shows up in high-frequency scripts that use `useLast()` in a tight loop.

### [DONE] `hasDialogueOption(String, boolean)` allocates an intermediate `List<String>` that is consumed by a single `anyMatch` call
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/dialogues/Rs2Dialogue.java:284-291`
- **Type:** simplification
- **Found:** iter 12
- **Issue:** Lines 285-291 collect `dialogueOptions.stream().map(Widget::getText).collect(Collectors.toList())` into `dialogueText` and then immediately stream that list for `.anyMatch(...)`. The intermediate `List<String>` is allocated, populated, and discarded in a single expression — no element of it is used more than once. The check could be performed in a single pass over the original `dialogueOptions` list by calling `getText()` inline in the `anyMatch` predicate.
- **Fix:** Replace lines 285–291 with: `return dialogueOptions.stream().anyMatch(w -> exact ? w.getText().equalsIgnoreCase(text) : w.getText().toLowerCase().contains(text.toLowerCase()));`. Removes the intermediate list allocation entirely.
- **Impact:** Removes one transient `List<String>` allocation per `hasDialogueOption` call; every script polling for dialogue options (e.g. `sleepUntilHasDialogueOption`) benefits on each check iteration.

### [DONE] `BreakHandlerV2Script` constructor uses `System.out.println` for a debug log, bypassing SLF4J

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/breakhandler/breakhandlerv2/BreakHandlerV2Script.java:39`
- **Type:** simplification
- **Found:** iter 19
- **Issue:** The constructor emits `System.out.println("[DEBUG] BreakHandlerV2Script instance #" + instanceId + " created. Hash: " + System.identityHashCode(this))` directly to stdout, bypassing the `@Slf4j` logger. This fires unconditionally on every plugin startup regardless of log configuration, pollutes structured logs, and is inconsistent with every other logging call in the class.
- **Fix:** Replace with `log.debug("[BreakHandlerV2] Script instance #{} created (hash={})", instanceId, System.identityHashCode(this))`.
- **Impact:** Removes unconditional stdout noise on break handler startup; one-line fix.

### [DONE] `toggleAllLocks()` calls `findLockedSlots()` a second time needlessly
- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/bank/Rs2Bank.java:3138-3144`
- **Type:** simplification
- **Found:** iter 1
- **Issue:** Line 3139 populates `lockedSlots`; if it is empty the method returns `false` immediately. Line 3144 then calls `findLockedSlots()` again to initialise `anyUnlocked = !findLockedSlots().isEmpty()`. At that point `lockedSlots` is guaranteed non-empty, so `anyUnlocked` is always `true` — the second widget-tree traversal is dead work.
- **Fix:** Delete line 3144 and initialise `anyUnlocked` to `false`, setting it to `true` inside the loop whenever `toggleItemLock(item)` succeeds.
- **Impact:** Removes one redundant full bank-inventory widget scan per `toggleAllLocks()` call.

### [DONE] `Microbot.scriptRuntimes` is an allocated but permanently-empty `HashMap` exposed via `@Getter` — dead allocation

- **File(s):** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/Microbot.java:189`
- **Type:** simplification
- **Found:** iter 23
- **Issue:** Line 189 declares `private static HashMap<String, Integer> scriptRuntimes = new HashMap<>()` and annotates it with `@Getter`, generating `getScriptRuntimes()`. A full-codebase search finds exactly one reference to `scriptRuntimes` — the declaration itself. The map is never written to and never read from anywhere else in the codebase. The `@Getter` exposes a live mutable `HashMap` via a public static method, which means external callers could accidentally write to it or observe it as empty, both of which are meaningless. If runtime tracking was intended, the implementation was never started.
- **Fix:** Delete the `scriptRuntimes` field and its `@Getter`. If per-script runtime tracking is desired in the future, it belongs in a dedicated `ScriptRuntimeTracker` class with explicit start/stop methods, not as a static map in `Microbot`.
- **Impact:** Removes a dead public API that could mislead plugin authors into calling `Microbot.getScriptRuntimes()` expecting populated data; eliminates one gratuitous `HashMap` allocation per class load.

## Notes

<!-- The loop drops a line here when an iteration finds nothing new. -->
