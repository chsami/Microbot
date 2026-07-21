# Movement Gotchas

## 1. Do not recurse on failed minimap clicks without changing the click target

`Rs2Walker.processWalk` holds the walker lock while processing a path. If a minimap click is rejected because the calculated point is outside the minimap clip, immediately recursing with the same target can spin forever while still holding the lock. Shrink the click target toward the player or otherwise change the condition before retrying.

**Why this matters:** Quest steps that walk to a nearby object can repeatedly calculate a valid path but never move, starving other walk requests because the walker lock is never released.

**Pattern to follow:**

```java
WorldPoint clickTarget = getPointWithWallDistance(targetWp);
boolean clicked = Rs2Walker.walkMiniMap(clickTarget);
if (!clicked)
{
	clicked = walkMiniMapToward(clickTarget, playerLoc, MINIMAP_REACH_EUCLIDEAN - 1);
}
```

**Where this applies:** `Rs2Walker`, `Rs2MiniMap`, and shortest-path walking loops.

**Defensive check:** When debugging stalls, compare pathfinder logs with `./microbot-cli state`. A repeating valid path with an unchanged player position usually means the click layer failed after pathing succeeded.

## 2. Probe raw path obstacles before declaring the walker stuck

Path smoothing can collapse many adjacent raw path tiles into one minimap waypoint. Some doors and gates are not represented as blocking collision in the pathfinder map, so the smoothed segment may legally cross them while hiding the exact tile the object handler needs to inspect. Run nearby raw-path door/object checks as soon as the raw path is longer than the smoothed path and the obstacle is in scene range; do not wait for `stuckCount` to increment first.

**Why this matters:** A walk from Varrock castle's upper floors toward Varrock fountain can descend correctly, then stall at the plane-1 castle door because the smoothed waypoint skips over the door tile and the normal per-segment door check never sees it.

**Pattern to follow:**

```java
if (rawPath != null && path != null && rawPath.size() > path.size()
        && handleNearbyRawPathSceneObjects(rawPath, HANDLER_RANGE)) {
    doorOrTransportResult = true;
}
```

**Where this applies:** `Rs2Walker`, `PathSmoother`, and shortest-path obstacle handling.

**Defensive check:** When a path stalls beside a visible door while the pathfinder reports a complete route, compare raw and smoothed path lengths; if the raw path is longer, verify nearby raw-path obstacle probing happens before stall recovery.

## 3. Match wall doors by crossed edge, not nearby tile

Wall-object doors block the edge between the wall object's tile and the neighboring tile indicated by its orientation. Raw-path segment probes must only treat a wall door as relevant when the path segment actually transitions across that edge. Do not match a wall door merely because the path starts on, ends on, or passes near one side of the door.

**Why this matters:** At Draynor Manor's east/back door, the player can stand on the south-side door tile and need to walk southwest into the room. A broad "door near segment" match repeatedly re-opens the back door instead of allowing the next minimap walk step to run.

**Pattern to follow:**

```java
WorldPoint doorTile = wall.getWorldLocation();
WorldPoint blockedNeighbor = getWallDoorNeighborPoint(wall.getOrientationA(), doorTile);
return isDoorEdgeTransition(previousPathTile, nextPathTile, doorTile, blockedNeighbor);
```

**Where this applies:** `Rs2Walker.handleNearbyRawPathSceneObjects`, `Rs2Walker.findDoorNearSegment`, and any wall-door probe that uses `WallObject.getOrientationA()`.

**Defensive check:** Add a unit test for a path starting on the door's blocked-neighbor tile and moving away from the door; it must return false.

## 4. Do not raw-probe doors while the player is already moving

Raw-path scene-object probing is a recovery aid for smoothed paths that hide nearby obstacles. Once a door interaction has started movement, let that movement settle or reach the door edge before probing again. Re-running raw probes while the player is still moving can repeatedly interact with the same door and prevent the normal minimap/path step from taking over.

**Why this matters:** When leaving Draynor Manor through the east/back door, the walker can click the door, start moving toward it, then immediately re-enter raw-path probing and click the same door again instead of continuing through the path outside.

**Pattern to follow:**

```java
if (Rs2Player.isMoving()) {
    return false;
}
waitForDoorInteractionProgress(fromWp, toWp);
```

**Where this applies:** `Rs2Walker.handleNearbyRawPathSceneObjects`, door handlers that call `Rs2GameObject.interact`, and any recovery logic that recurses into `processWalk`.

**Defensive check:** In live testing, a door should produce one interaction followed by movement/path progress, not repeated `Raw path door handler resolved obstacle` messages every tick while the player is moving.

If a previous minimap click is still moving the player, do not attribute that movement to a new gate interaction. A valid gate can be blacklisted when the walker clicks an interim waypoint, notices the gate while movement is still in flight, then compares the pre/post positions from the old minimap movement against the gate edge. Wrong-traversal blacklisting should only run when the player started near the attempted door edge.

## 5. Suppress the inverse adjacent transport after crossing a same-plane door

Some doors are represented in `transports.tsv` as two adjacent same-plane transports, one for each direction. After the walker clicks one side and arrives on the other, immediately accepting the inverse transport can bounce the player back through the same door instead of letting the next minimap step continue away from it. Mark both tiles of a successful adjacent same-plane transport as recently handled for a short window.

**Why this matters:** Leaving Draynor Manor through the east/back door can alternate between `3123,3360,0` and `3123,3361,0`, repeatedly logging raw-path/current-tile transport handling and burning the route timeout before walking back to Draynor.

**Pattern to follow:**

```java
boolean reachedDestination = sleepUntil(() -> atTransportDestination(transport), 5000);
if (reachedDestination && isAdjacentSamePlaneTransport(transport)) {
    markStationaryDoorOpened(transport.getOrigin());
    markStationaryDoorOpened(transport.getDestination());
}
```

**Where this applies:** `Rs2Walker.handleTransports`, current-tile transport recovery, raw-path transport probing, and bidirectional same-plane door/gate transports.

**Defensive check:** A successful adjacent same-plane transport should be followed by a minimap/path step away from the doorway, not by alternating `Raw path transport handler` and `Current-tile transport handler` logs for the same two tiles.

## 6. Recalculate after long-distance object transports

Not every large map transition changes plane or uses a teleport type. Some object transports, such as the Varrock Sewers ladder, remain on plane 0 while jumping between coordinate bands. After a successful object interaction reaches one of these destinations, run the normal transport finalizer so the shortest path is rebuilt from the new location.

**Why this matters:** A route from Varrock Sewers back to a surface origin can climb the ladder successfully, then continue using a path that was calculated from the underground coordinate band. The walker may drift off path or exit during setup even though the transport itself worked.

**Pattern to follow:**

```java
if (reachedDestination) {
    markAdjacentSamePlaneTransportHandled(transport, object);
    return finishHandledTransport(transport);
}
```

**Where this applies:** `Rs2Walker.handleTransports` object interactions and any object-transport handler that waits for the destination tile directly.

**Defensive check:** Same-plane object transports with a large `distanceTo2D` delta should produce a fresh pathfinder start near the post-transport player location before the next minimap step.

## 7. Model missing collision edges before tuning walker retries

Some static collision gaps are specific edges, not whole tiles. If the pathfinder repeatedly routes through a visible fence/wall and the live client keeps clicking fallback tiles near that boundary, add an explicit blocked edge to pathfinding and smoothing instead of trying to solve it with longer timeouts or broader minimap fallback.

**Why this matters:** The Varrock Palace garden south fence can be missing from the bundled collision map near `3229..3241,3472 -> 3471`. A no-agility F2P route to the Varrock Sewers manhole can walk around the trellis correctly, then stall against that garden boundary because the path says the south edge is traversable.

**Pattern to follow:**

```java
if (config.isBlockedTransportEdge(node.packedPosition, neighborPacked)) {
    continue;
}
```

**Where this applies:** `CollisionMap.getNeighbors`, `PathSmoother.lineOfSight`, and any path data correction where only one edge between adjacent tiles is invalid.

**Defensive check:** Add a core pathfinder regression from the observed stuck tile; assert neither the raw path nor smoothed path crosses the blocked edge, and that the route still reaches the original destination.

## 8. Do not click a visible endpoint before honoring pending route interactions

An endpoint being visible on the minimap does not mean it is the next correct click. If the computed shortest path reaches that endpoint through an intermediate door, gate, transport, shortcut, ladder, or other route object, the walker must process the first route interaction before issuing a direct endpoint click.

**Why this matters:** From Varrock Palace, a destination such as `3229,3473,0` can be visible on the minimap while the shorter route requires opening the palace doors first. Clicking the endpoint lets the game choose a longer collision-valid detour and bypasses the webwalker's route.

**Pattern to follow:**

```java
if (handleNearbyRawPathSceneObjects(rawPath, HANDLER_RANGE)) {
    return true;
}
if (!hasPendingExplicitTransportStepBeforeArrival(rawPath, target, distance)
        && !localRouteDetoursFromComputedRoute(rawPath, end, DIRECT_CLICK_MAX_DISTANCE)) {
    walkMiniMap(end);
}
```

**Where this applies:** `Rs2Walker.walkWithStateInternal`, short local walk kick-starts, final/minimap endpoint clicks, and any future fast-path that bypasses normal path iteration.

**Defensive check:** Reproduce with closed Varrock Palace doors toward `3229,3473,0`; the first action should target the door or route waypoint, not the final endpoint tile.

## 9. Preserve interrupts so walker cancellation stops waits immediately

Ctrl+X and script shutdown cancel the active walk task with `Future.cancel(true)` and clear the walker target. Shared sleep/poll helpers must preserve the interrupted flag and stop polling when interruption is observed; otherwise the walker can continue through several timeout cycles before noticing the cleared target.

**Why this matters:** A user pressing Ctrl+X expects the webwalker to stop issuing route actions immediately. If `InterruptedException` is swallowed, long waits in object, transport, dialogue, or animation handling can keep cycling until their normal timeout elapses.

**Pattern to follow:**

```java
try {
    Thread.sleep(delayMs);
} catch (InterruptedException ignored) {
    Thread.currentThread().interrupt();
}
while (!Thread.currentThread().isInterrupted() && !condition.getAsBoolean()) {
    sleep(pollMs);
}
```

**Where this applies:** `Global.sleep*`, `Global.sleepUntil*`, `Rs2Walker.setTarget(null)`, and any walker helper that waits after clicking a door, shortcut, transport, or minimap tile.

**Defensive check:** Start a long webwalk, press Ctrl+X during movement or a route-object wait, and verify no additional path recalculations or route-object interactions occur after the cancel log.

## 10. Do not treat reachable endpoint tiles as proof that a gate edge is open

Local reachability answers whether individual tiles can be reached within the sampled area; it does not prove that the computed path edge between two reachable tiles can be crossed without opening a gate, door, stile, or similar route object. Before skipping door handling, issuing a direct short minimap/checkpoint click, or yielding to an in-flight interim minimap target, scan the nearby remaining route for door-like scene objects that sit on the route segment. Include one or two raw edges before the closest path index; when the player is slightly off-path near a gate, the closest raw tile can already be on the far side of the gate edge. For diagonal hops beside small gates, also check the two cardinal sub-steps of the diagonal; the gate edge may sit on one of those sub-steps even when the direct diagonal segment does not equal the wall edge. Do not skip door probing just because the edge or object is catalogued as a transport when it is an `Open Gate` / door-like object transport. A raw-route scan may notice a future gate early, but the actual object interaction must still be range-gated against that gate edge before treating it as handled.

**Why this matters:** A short route near the Lumbridge farm allotment can correctly choose the gate as the shortest path, but the walker may see both sides as valid reachable tiles and click the minimap endpoint. The game then routes around the fence instead of opening the gate.

**Pattern to follow:**

```java
int scanStart = Math.max(0, closestRawIndex - 2);
if (bothEndpointTilesReachable
        && !hasDoorLikeSceneObjectOnSegment(from, to, playerLoc, HANDLER_RANGE)) {
    continue;
}
if (doorOpenedButPlayerDidNotTraverse) {
    // Only count this as success if the nudge actually reaches/crosses the door edge.
    tryDoorEdgeCrossNudge(from, to, currentTarget);
}
if (hasPendingDoorLikeSceneObjectBeforeDirectClick(rawPath, path, playerLoc, DIRECT_CLICK_MAX_DISTANCE)
        || handlePendingDoorBeforeRouteClick(rawPath, path, i, targetIdx, smoothedToRaw, timeoutMs,
        attemptedDoorEdgesThisPass, playerLoc)
        || handlePendingDoorNearRawPath(rawPath, timeoutMs, attemptedDoorEdgesThisPass, playerLoc, 2, 14)
        || handlePendingDoorDuringInterim(rawPath, timeoutMs, attemptedDoorEdgesThisPass, playerLoc)) {
    return WalkerState.MOVING;
}
```

**Where this applies:** `Rs2Walker.tryDirectShortWalk`, route checkpoint/minimap click selection, unreachable-smoothed-tile recovery, interim minimap movement waits, post-open door-edge nudges, `Rs2Walker.handleDoorsInRawSegment`, and any future optimization that skips route-object probing because tiles look locally reachable.

**Defensive check:** Reproduce from the Lumbridge farm road toward a target southwest of the allotment with both gates closed; the route should open each gate as it enters handler range, not stay in `interim-in-flight` until the server path has already routed around the field.

## 11. Clear sticky minimap interim targets outside the click branch

Sticky interim targets prevent click thrash while the player is moving toward a minimap checkpoint, but they are only useful while the checkpoint is still ahead. Clear them at the start of each walk pass when the player is already within the close threshold, the target is on another plane, or the checkpoint has aged out. Also clear them when stall-recalc fires, otherwise the replan can inherit the same stale checkpoint and spin without issuing a new movement command.

**Why this matters:** Long post-transport routes through cluttered areas can stop one tile from the sticky interim target. If the next pass does not enter the checkpoint-click branch, the stale interim remains in diagnostics and each stall-recalc repeats the same state until the tail iteration limit exits.

**Pattern to follow:**

```java
if (shouldClearInterimTarget(interimTargetWp, Rs2Player.getWorldLocation(), interimSetAtMs,
        interimLastProgressAtMs, nowMs)) {
    clearInterimTarget("close-or-expired");
}
if (isStuckTooLong()) {
    clearInterimTarget("stall-recalc");
    recalculatePath();
}
```

**Where this applies:** `Rs2Walker.processWalk`, recovery minimap clicks, post-transport walking, and any future logic that stores a sticky route checkpoint across loop iterations.

**Defensive check:** Reproduce a long route after the Falador crumbling-wall shortcut toward Ardougne through the dead-tree field; if the player reaches one tile from the interim checkpoint, the next pass should log `interim_clear` and select a fresh movement target instead of repeating `STALL_RECALC` until `tail_max`.

For long open routes, retarget before the player fully stops when they are already close to the interim checkpoint, and keep normal minimap clicks slightly inside the observed minimap edge. This reduces visible stop/start pauses and outside-clip fallback clicks without reintroducing rapid click thrash.

## 12. Do not let local recovery override unresolved door blockers

Local-reachability recovery is useful for outdoor false negatives, but in tight rooms it can fight the door resolver. If a route edge still has a door-like scene object on or adjacent to the raw path, suppress broad minimap recovery and let the door scanners retry after their normal cooldowns. Do not permanently blacklist a path-adjacent fallback door just because one attempt traversed the wrong way; in small door clusters the same object may be the correct blocker again once the player has moved to the other side.

**Why this matters:** In POH-style tight rooms with several doors close together, a fallback door click can move the player away from the intended route. If that door tile is session-blacklisted and optimistic recovery keeps clicking route tiles beyond the blocker, the walker loops around the room until a user manually opens the final door.

**Pattern to follow:**

```java
if (tryResolvePathAdjacentBlocker(...)) {
    return MOVING;
}
if (hasUnresolvedDoorLikeObjectNearRawPath(...)) {
    return MOVING; // retry door handling next pass; do not broad-click recovery
}
clickOptimisticRecoveryTarget();
```

**Where this applies:** `Rs2Walker.processWalk` unreachable-tile handling, `tryResolvePathAdjacentBlocker`, and any fallback that issues minimap recovery clicks after door/path-adjacent scans fail.

**Defensive check:** Reproduce a route through a small room with three nearby doors and a POH portal. The walker should retry the route-door blocker and avoid repeated `route-backed local recovery click` loops around the room; it should not need the user to manually open the final door.

## 13. Stall recalculation must also issue fresh movement

Recalculating a path after a stationary stall is not enough by itself. If the player is idle and the next loop still cannot enter a normal click branch, repeated `STALL_RECALC` logs can continue forever until a user manually nudges the player. After clearing stale interim state and refreshing the route, issue a conservative minimap click along the reachable raw route so the server pathfinder gets a new movement command immediately. On active long routes, also nudge after a short stationary idle window, around a few ticks, instead of waiting for the full stall threshold.

**Why this matters:** Long routes can stop on a tile with no combat, animation, or interaction. Repeated stall recalcs refresh pathfinding state but leave the character standing still, so the route only resumes after manual movement changes the local path context.

**Pattern to follow:**

```java
if (isRouteActive() && playerIsIdleForShortWindow()) {
    tryIssueRouteRecoveryClick(rawPath, path, target);
    continue;
}
if (isStuckTooLong()) {
    clearInterimTarget("stall-recalc");
    setTarget(target);
    if (playerIsIdle()) {
        tryIssueRouteRecoveryClick(rawPath, path, target);
    }
    continue;
}
```

**Where this applies:** `Rs2Walker.processWalk` stall-recalc handling and any future stale-state recovery that clears route state while the player is idle.

**Defensive check:** Start a long route and observe a stationary pause. A short idle pause should log `active route idle nudge`; if it reaches full stall recalc, the next log sequence should include `stall recovery click` and a position delta, not another idle-only `STALL_RECALC` loop at the same tile.

After a handled transport, avoid expensive path-adjacent or raw transport scans on ordinary open-ground segments unless a nearby planned transport or recent door attempt exists. Those scans are recovery tools, and on long outdoor routes a no-op scan can add several seconds before the next minimap click.

For long-route minimap walking, let the next checkpoint selection happen before the current minimap target is fully consumed. Waiting until the player is only a few tiles from the interim makes the walker visibly stop before issuing the next click; handing off at a moderate remaining distance keeps movement continuous without rapid re-clicking. If an interim clears as close and no nearby route door/transport is pending, issue the next route-aligned continuation click immediately instead of waiting for idle-nudge recovery. Stale-progress checks must also count distance progress toward the actual interim checkpoint, not only smoothed path index progress; sparse smoothed paths can otherwise clear valid raw-path checkpoints as stale while the player is still walking toward them.

Before the first movement click, avoid timeout-backed speculative segment scans for doors, blockers, rockfalls, or transports. Let the first minimap click move the player toward the route; actual visible doors can still be handled by the dedicated pre-click route-door guard, and steady-state handling can resolve later obstacles once movement has started. Spending startup time scanning every nearby raw segment creates a visible cold start.

Continuation clicks that keep an active route moving should be tail-exempt like `interim-in-flight`; otherwise very long routes can exhaust `MAX_PROCESS_WALK_TAIL_ITERATIONS` while still making progress and trigger an unnecessary auto-retry.

Sticky interim targets should also clear when route-index progress goes stale. If the player keeps moving but the closest path index does not advance for `INTERIM_PROGRESS_TIMEOUT_MS`, treat the checkpoint as stale and select a fresh route-aligned target instead of waiting for max-age expiry.

When a route-following minimap click is outside the minimap clip, fallback clicks must stay on the raw path. This includes the "clicked but no movement yet" retry after a route click and route-backed direct short-walks near the final destination. A generic "reachable tile closer to target" fallback can select a tile far away from the route in open areas, especially near fences and the final destination. Raw fallback must be anchored from the stabilized route index, not a fresh nearest-raw-tile lookup, or the walker can snap backward to an already-travelled branch.

For adjacent same-plane shortcuts, do not treat any movement away from the origin as success. Some shortcuts, such as stepping stones, can fail and place the player on a fallback tile; once the player is settled away from the expected destination, stop the landing wait and replan from the actual tile. If the player is settled within one tile of the expected destination and is no longer on the origin, accept the landing instead of waiting for an exact tile that server pathing may not choose.

## 14. Keep local recovery clicks route-backed and paced

Local-reachability recovery is a fallback for bounded reachability false negatives, not a license to pick an arbitrary minimap point. Recovery clicks should use the same route-backed raw-path fallback as normal route movement, then store the actual clicked target as the sticky interim checkpoint. After issuing the click, wait only for movement to start or the checkpoint/goal to be reached; do not wait for a full idle cycle before returning to the walk loop.

**Why this matters:** Bounded local reachability can report a valid route tile as unreachable even though the server pathfinder can still walk toward it. Arbitrary or repeated recovery clicks can pull the player away from the planned route and create visible stop-start movement.

**Pattern to follow:**

```java
int reach = STALL_RECOVERY_MINIMAP_REACH_EUCLIDEAN;
WorldPoint clickTarget = clampToEuclideanRadius(playerLoc, recoverTarget, reach - 1);
WorldPoint clickedTarget = clickMiniMapOrFallback(rawPath, clickTarget, playerLoc, reach - 1, false, rawAnchorIndex);
if (clickedTarget != null) {
    interimTargetWp = clickedTarget;
    waitForMovementStartAfterRecovery(target, playerLoc, clickedTarget, target, finishThreshold);
}
```

**Where this applies:** `Rs2Walker.processWalk` local-reachability recovery, route-backed direct short-walks, and any fallback that runs after local reachability says a route tile is not currently reachable.

**Defensive check:** Exercise a long outdoor route and a route with gates or transports. Recovery logs should select route-backed points, and there should be no long idle pause between `route-backed local recovery click` and the next route-aligned movement.

After recovery sets a sticky interim target, do not issue another optimistic recovery while that interim is still active and movement/progress is fresh. Yield as `interim-in-flight` or another tail-exempt movement state until the checkpoint is close, stale, or expired. Otherwise the walker can loop through several recovery clicks while the player is already moving, especially when local reachability reports the next smoothed tile as unreachable.

## 15. Keep primary route clicks on the raw route

When a shortest-path raw route exists, choose the next minimap target from that raw route before applying smoothed-waypoint wall-distance nudges, bounded reachability shortcuts, or generic directional fallbacks. Sideways reachable tiles can be valid server walk targets while still being the wrong side of a fence, gate, or corridor corner. Generic fallback is only appropriate when no route-backed raw point is available.

**Why this matters:** Tight routes near gates and fences can fail when the walker clicks a tile left or right of the drawn route line. The game then chooses its own path around the obstacle, which fights the planned door/transport handlers and produces stop-start recovery.

**Pattern to follow:**

```java
WorldPoint rawTarget = findFurthestRawPathPointMatching(rawPath, playerLoc, reach, rawAnchor,
        Rs2Walker::isKnownWalkableOrUnloaded);
WorldPoint clickTarget = rawTarget != null ? rawTarget : getPointWithWallDistance(smoothedTarget);
```

**Where this applies:** `Rs2Walker.processWalk`, recovery clicks after false unreachable reports, route continuation clicks, direct short-walk fallbacks, and any minimap click helper used while a shortest-path raw route exists.

**Defensive check:** Unit-test raw-route target selection with a route that doubles back near an earlier branch; the selected tile must stay at or ahead of the anchored raw route index.

## 16. Yield before scans while an interim route click is in flight

Sticky interim targets only reduce click thrash if they are checked before broad route scans. When a fresh interim checkpoint is still far away and the player is moving or has very recent checkpoint progress, yield before raw-scene scans, current-tile transport scans, direct short-walk attempts, and path segment scans. Resume normal route work once the checkpoint is close, stale, expired, on another plane, or movement has genuinely stopped.

**Why this matters:** Long post-transport routes can otherwise run several no-op scans and select several small follow-up clicks while the previous minimap flag is still carrying the player. That looks like stop-start walking and burns tail iterations even though the route is making progress.

**Pattern to follow:**

```java
if (shouldYieldForActiveRouteInterim(playerLoc, path, nowMs)) {
    exitReason = "interim-in-flight";
    continue;
}
```

**Where this applies:** `Rs2Walker.processWalk` before broad raw-scene handling, current-tile transport handling, direct short-walk handling, and the main path loop.

**Defensive check:** Start a long route immediately after a transport. While the player is still moving toward the active minimap checkpoint, the walker should log a tail-exempt `interim-in-flight` yield instead of repeated `post_transport_path_selected` clicks for nearby path indices.

## 17. Dispatch immediate planned transports before idle nudges

When the current route index is an explicit shortest-path transport edge, let the segment transport handler run even during startup, before route idle nudges or stall recovery clicks. Keep startup door/rockfall probes skipped, but do not postpone teleports, ships, ladders, or other catalog-backed route transports that start at the player's current segment.

**Why this matters:** Originless spell/item teleports can be the first edge in a route. If startup logic suppresses the segment handler and idle recovery runs first, the walker can sit on the origin, spam no-op route nudges, trigger stall recovery, and only then cast the teleport.

**Pattern to follow:**

```java
boolean immediateTransport = hasImmediatePlannedTransportStep(path, routeStartIdx, playerLoc);
if (shouldRunActiveRouteIdleNudge(idleNudgeDue, immediateTransport)) {
    tryIssueRouteRecoveryClick(rawPath, path, target, "active route idle nudge");
}
```

**Where this applies:** `Rs2Walker.processWalk` startup handling, active route idle nudges, stall recovery clicks, and segment transport dispatch.

**Defensive check:** Start a route whose first planned edge is Home Teleport. Logs should show `transport_handoff_enter` for `TELEPORTATION_SPELL` without several `active route idle nudge: clicked=false` entries first.

## 18. Model conditional paid gates as transports plus blocked edges

When a gate can be crossed by either a quest unlock or a currency payment, do not model the gate tiles as quest-only restrictions. A tile restriction cannot express "quest complete OR pay coins", and it can prevent the walker from reaching the paid transport origin. Model each crossing as explicit paid/free transports and add a blocked edge override so normal walking cannot bypass the unavailable transport.

**Why this matters:** The Lumbridge-to-Al Kharid toll gate can be opened freely after Prince Ali Rescue or paid through with 10 coins. A quest-only restriction blocks the paid route for accounts without the quest, while no edge block can let the pathfinder walk through the gate without paying.

**Pattern to follow:**

```java
// Wrong: restrict both sides of the gate to one quest state.
3267 3227 0        Prince Ali Rescue

// Right: transport data decides availability; blocked_edges prevents free walking.
3267 3227 0    3268 3227 0    Pay-toll(10gp);Gate;2786    ...    10 Coins
3267 3227 0    3268 3227 0    Open;Gate;2786              ...    Prince Ali Rescue
```

**Where this applies:** `transports.tsv`, `blocked_edges.tsv`, `restrictions.tsv`, `PathfinderConfig`, and `Rs2Walker.handleTransports`.

**Defensive check:** Add resource tests that assert both paid and quest-free transports load, the gate tiles are not quest-only restricted, and the gate edge is blocked without a usable transport.

## 19. Gate minimap click targets on reachability, not walkability — and allow bidirectional rejoin

A tile being *walkable* (`Rs2Tile.isWalkable`, i.e. not `BLOCK_MOVEMENT_FULL`) is not the same as being *reachable from the player*. Next to a wall the two diverge hard: a tile flush on the far side of a castle wall is walkable and Euclidean-close, but only reachable via a long detour. Any minimap click-target selection that filters on walkability/clip-visibility alone will happily pick the wrong side of the wall and stall. Select the next raw-route click point from the player's collision-**reachable** set (`Rs2Tile.getReachableTilesFromTile`, which honors edge flags) with a generous BFS depth (~2× the click radius) so real around-a-corner detours still qualify while the wrong side is excluded. Only fall back to a walkable/off-scene point when the target is genuinely off the loaded scene (collision can't be verified there). Likewise, `getPointWithWallDistance` computes tiles reachable *from the target*, so its nudge can land across a wall or inside a building — verify its result with `Rs2Tile.isTileReachable` before clicking it.

Forward-only anchoring (introduced to stop the walker snapping back to already-travelled branches) overcorrects when the player is pushed *off* the path: if nothing ahead is reachable, forward-only recovery returns nothing and the walker stalls against the wall. Recovery must therefore be **bidirectional**: scan a bounded index window on both sides of the anchor and rejoin via the nearest reachable raw point, preferring the furthest-forward one so normal progress is never sacrificed. This is a rejoin path for the off-path case only; steady-state progress stays forward-only.

**Why this matters:** Walking north up the west side of Varrock castle, the path runs flush against the wall. The selector picked a forward raw/nudged point on the far (east) side of the wall and clicked it, so the player pressed into the wall and stalled. The same mechanism clicks into houses (interior tile is walkable but unreachable) and produces "random far clicks" right after a teleport, when a stale smoothed waypoint or blind scaled-radius directional guess targets an unreachable tile.

**Pattern to follow:**

```java
// Primary: furthest-forward COLLISION-REACHABLE raw point (wrong side of wall excluded).
WorldPoint rawRouteTarget = selectRouteClickTarget(rawPath, playerLoc, reach, rawAnchorIndex);
if (rawRouteTarget != null) {
    clickTarget = rawRouteTarget;
} else {
    clickTarget = getPointWithWallDistance(targetWp);
    if (!Rs2Tile.isTileReachable(clickTarget)) {
        WorldPoint rejoin = findReachableRejoinRawPathPoint(rawPath, playerLoc, reach, rawAnchorIndex);
        if (rejoin != null) clickTarget = rejoin; // step back onto the line if needed
    }
}
// Directional scaled-radius guesses must also be reachability-gated:
if (!reachable.contains(scaledFallback)) continue;
```

**Where this applies:** `Rs2Walker.selectRouteClickTarget`, `Rs2Walker.findReachableRejoinRawPathPoint`, `Rs2Walker.walkMiniMapToward`, `getPointWithWallDistance` consumers, and any minimap click-target selector used while a raw route exists.

**Defensive check:** Unit-test the rejoin core with an injected reachability predicate: (a) player pushed one tile off-path with nothing ahead reachable must return a point *behind* the anchor; (b) with points ahead reachable it must return the furthest-forward one inside the reach circle; (c) nothing reachable must return null so stall/recalc can take over. Live-repro: walk north flush against Varrock castle's west wall — the walker should round the corner instead of pressing into the wall, and a fresh teleport should not produce a click far off the route.

## 20. Keep minimap click targets on the raw route — do NOT clamp them to line-of-sight

A minimap click is resolved by the **game's own pathing**, so line of sight is irrelevant to walking: a player clicks past a corner, through a doorway, or around a building and the server routes them there. The invariant that matters is that the click target sits **on the raw route** — then wherever the server chooses to walk, we arrive on the planned path. Do not select the click by clamping a far smoothed waypoint to a Euclidean radius either; that is what produced an off-route target in the first place.

**Why this matters:** Walking past the Varrock West Bank, selection returned null on a stale anchor (see #24), so the caller clamped a far smoothed waypoint to a ~10 tile radius and clicked `(3176,3428)` — a tile that is *not* on the raw route. The game pathed there its own way, deviating wide and backtracking.

**Do not "fix" this with line-of-sight.** An earlier revision clamped clicks to a straight walkable line. It removed the symptom but made the walker advance corner-to-corner — stopping at every corner to re-aim, because it would only click as far as it could see in a straight line. That is a visible behavioural tell and it bought no correctness: an on-route target is safe regardless of how the server walks to it. It was reverted.

**Pattern to follow:**

```java
// Furthest forward point ON THE RAW ROUTE within minimap reach. No LOS requirement.
WorldPoint forward = findFurthestRawPathPointMatching(rawPath, playerLoc, maxEuclidean,
        rawAnchorIndex, Rs2Walker::isKnownWalkableOrUnloaded);
```

**Where this applies:** `Rs2Walker.selectRouteClickTarget`, `findFurthestVisibleKnownRawPathPoint`, and any minimap click selection while a raw route exists. Pending doors/gates are handled by `handlePendingDoorBeforeRouteClick` — not by shortening the click.

**Defensive check:** `RouteClickTargetRegressionTest` asserts the historic `(3176,3428)` is not on the raw route, and that the route offers several on-route candidates within minimap reach so a click is never artificially shortened to the nearest corner.

## 21. Route continuation is one-shot — keep the idle-nudge window short

`tryIssueRouteContinuationClick` only runs on the single pass where `clearInterimTargetIfReachedOrExpired` returns `true` (the transition where the interim checkpoint is cleared). If any guard blocks it on that exact pass — `isInteracting`, `isAnimating`, door/transport settling, or its own unresolved-door / upcoming-transport checks — the opportunity is lost and **nothing retries it**. The route then only resumes via the active-route idle nudge, so that stationary window is the visible dead stop between hops. Keep it to a few ticks; do not treat it as a rare recovery path, because in practice it is what continues normal routes.

**Why this matters:** A 41-tile walk from `(3183,3435)` to `(3173,3399)` clicked correctly, walked to its checkpoint, then sat fully stopped for the whole `2500ms` idle window before issuing the next click — roughly a third of the total walk time spent standing still, once per hop.

**Pattern to follow:**

```java
// One-shot: only fires on the pass that clears the interim checkpoint.
if (clearedInterimTarget && !Rs2Player.isInteracting() && !Rs2Player.isAnimating()
        && tryIssueRouteContinuationClick(rawPath, path, target)) { ... }

// So the nudge window is the real continuation path — keep it a few ticks, not seconds.
private static final long ACTIVE_ROUTE_IDLE_NUDGE_MS = 1_200L;
```

**Where this applies:** `Rs2Walker.tryIssueRouteContinuationClick`, `clearInterimTargetIfReachedOrExpired`, `shouldIssueActiveRouteIdleNudge`, and `ACTIVE_ROUTE_IDLE_NUDGE_MS` / `ACTIVE_ROUTE_IDLE_NUDGE_COOLDOWN_MS`.

**Defensive check:** On a multi-hop route, the gap between `first_minimap_click` / route-fallback clicks and the next `active route idle nudge` should be roughly the walk time plus a few ticks — not walk time plus a full idle window. A repeating ~2.5s stationary gap per hop means continuation is being missed and only the nudge is driving the route.

## 22. Suppress the inverse adjacent transport even when the landing check fails

Gotcha #5 suppresses the inverse of an adjacent same-plane transport after a crossing, but only on the success path. Adjacent same-plane transports require landing on the *exact* destination tile, and agility shortcuts routinely deposit the player a tile off it — so a crossing can physically succeed while the landing check reports failure. On that path nothing was suppressed, leaving the inverse entry immediately eligible. Record the suppression whenever the player is no longer on the origin, regardless of the landing verdict; the landing result itself should stay unchanged so the route still replans.

**Why this matters:** Near `(3150..3151, 3363)` the walker crossed an `AGILITY_SHORTCUT`, landed a tile off the catalogued destination, logged `post-handleObject landing unresolved`, then immediately took the opposing entry back across and stranded itself — roughly 19s of a 2m26s walk spent bouncing and recovering.

**Pattern to follow:**

```java
boolean landed = waitForPostHandleObjectLanding(transport, destWait, maxInclusive);
if (!landed) {
    WorldPoint after = Rs2Player.getWorldLocation();
    // Off the origin => we crossed, even though the strict landing check failed.
    if (isAdjacentSamePlaneTransport(transport) && after != null && !after.equals(transport.getOrigin())) {
        markAdjacentSamePlaneTransportHandled(transport, object);
    }
}
```

**Where this applies:** `Rs2Walker.handleTransports` object interactions, `waitForPostHandleObjectLanding` callers, `markAdjacentSamePlaneTransportHandled`, and any adjacent same-plane transport regardless of `TransportType` — suppression is deliberately type-agnostic, so shortcuts and doors are covered alike.

**Defensive check:** A crossed shortcut should produce at most one `post-handleObject landing unresolved` followed by route progress away from the shortcut — not a second interaction with the opposing entry at an adjacent destination tile (`dest=3150,3363` then `dest=3151,3363`).

## 23. Never hash a raw cooldown timer into the transport cache key

The transport refresh verification hash must contain values that change only when a transport's *usability* changes. A `COOLDOWN_MINUTES` varplayer compares against wall-clock minutes, so its raw value advances continuously — hashing it guarantees cache misses forever. Worse, casting Home Teleport writes `LAST_HOME_TELEPORT`, which is one of the hashed varplayers, so **the teleport invalidated the transport cache simply by being used**, forcing a full re-evaluation of every transport (~2.6s) immediately after the teleport it had just performed. Hash the condition *verdict* (`TransportVarPlayer.matches`) plus the condition identity instead of the raw value: the verdict flips exactly once, when the cooldown expires and the transport actually becomes usable.

This is the same rule as #19's skill fix: only inputs that can flip a transport's usability may participate. Timers, regenerating stats, and other monotonically-drifting values must not.

**Why this matters:** A Camelot -> Draynor walk beginning with a Home Teleport logged
`transport_handoff ... ms=16562` with `refresh_transports cache_miss reason=verify ... changed=varplayers` firing inside that window — about 2.9s of a 16.5s teleport was the teleport invalidating its own cache.

**Pattern to follow:**

```java
// Wrong: raw value ticks every minute, and casting the spell writes it.
h = 31 * h + Microbot.getVarbitPlayerValue(id);

// Right: only a usability flip participates.
boolean satisfied = new TransportVarPlayer(id, value, operator).matches(actualValue);
h = 31 * h + id; h = 31 * h + operatorOrdinal; h = 31 * h + value;
h = 31 * h + (satisfied ? 1 : 0);
```

**Where this applies:** `PathfinderConfig.hashVarplayerConditionVerdicts`, `PathfinderConfig.hashVarbitConditionVerdicts`, `computeTransportRefreshVerificationHash`, the transport refresh snapshot, and any future cache key covering varbits/varplayers/skills. Varbits get the identical treatment: a raw varbit moving without flipping a condition verdict must not invalidate, which is what produced the ~2.4s dead time between pressing go and the first action.

**Defensive check:** `refresh_transports cache_miss reason=verify ... changed=varplayers` should not appear immediately after a teleport. Unit-test that two different raw values inside the same cooldown window hash identically, and that crossing the threshold does not.
