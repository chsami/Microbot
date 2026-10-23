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

## 12. Do not let optimistic recovery override unresolved door blockers

Unreachable-tile recovery is useful for outdoor false negatives, but in tight rooms it can fight the door resolver. If a route edge still has a door-like scene object on or adjacent to the raw path, suppress broad minimap recovery and let the door scanners retry after their normal cooldowns. Do not permanently blacklist a path-adjacent fallback door just because one attempt traversed the wrong way; in small door clusters the same object may be the correct blocker again once the player has moved to the other side.

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

**Defensive check:** Reproduce a route through a small room with three nearby doors and a POH portal. The walker should retry the route-door blocker and avoid repeated `unreachable optimistic recovery` loops around the room; it should not need the user to manually open the final door.

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

For long-route minimap walking, let the next checkpoint selection happen before the current minimap target is fully consumed. Waiting until the player is only a few tiles from the interim makes the walker visibly stop before issuing the next click; handing off at a moderate remaining distance keeps movement continuous without rapid re-clicking. If an interim clears as close and no nearby route door/transport is pending, issue the next route-aligned continuation click immediately instead of waiting for idle-nudge recovery.

Continuation clicks that keep an active route moving should be tail-exempt like `interim-in-flight`; otherwise very long routes can exhaust `MAX_PROCESS_WALK_TAIL_ITERATIONS` while still making progress and trigger an unnecessary auto-retry.

Sticky interim targets should also clear when route-index progress goes stale. If the player keeps moving but the closest path index does not advance for `INTERIM_PROGRESS_TIMEOUT_MS`, treat the checkpoint as stale and select a fresh route-aligned target instead of waiting for max-age expiry.

When a route-following minimap click is outside the minimap clip, fallback clicks must stay on the raw path. A generic "reachable tile closer to target" fallback can select a tile far away from the route in open areas, especially near the final destination.

For adjacent same-plane shortcuts, do not treat any movement away from the origin as success. Some shortcuts, such as stepping stones, can fail and place the player on a fallback tile; once the player is settled away from the expected destination, stop the landing wait and replan from the actual tile.

## 14. Match transport execution to its interaction mechanism and interface family

Transport rows do not all represent scene-object clicks, and related networks can use different widget groups. Before admitting new transport data, verify that the walker has an execution branch for the row's actual interaction and selects the interface from the origin object ID. Fail closed for unknown object IDs and tightly identify object-less item actions by their exact origin, destination, action, target, and item requirement.

**Why this matters:** Barrows mound entries use a spade inventory action and therefore have object ID `0`; the generic object executor skips them. River Lum and River Dougne canoe stations open different map interfaces, so waiting unconditionally for the Lum map makes every Dougne route time out.

**Pattern to follow:**

```java
if (isExactItemActionTransport(transport)) {
    interactRequiredItem();
    awaitDestination();
    return finishHandledTransport(transport);
}

int mapComponent = mapComponentForOriginObject(transport.getObjectId());
if (mapComponent < 0) {
    return false;
}
```

**Where this applies:** `Rs2Walker.handleTransports`, specialized transport handlers, and shortest-path transport resource additions.

**Defensive check:** Add pure unit tests for exact item-action recognition and for every supported origin-object-to-interface mapping, plus a loader test proving required item and unlock fields survive TSV parsing.

## 15. Apply the same click guards to every route entry point

Direct and fallback scene clicks must validate the projected click area against the viewport on the client thread. Reuse the validated canvas point when dispatching the click. An on-screen tile check alone can still produce a point outside the usable viewport.

Checkpoint handoffs in the main click branch must use the same close/expiry policy as the start-of-pass check. In particular, entering the preclick distance while moving does not bypass the retarget cooldown.

## 16. Scope global interaction recovery across nested door dispatch

When an object or NPC interaction reacts to the global can't-reach flag by starting a walker approach, keep ownership of that recovery on the current thread until the approach returns. Door interactions issued by that nested walk must bypass the outer can't-reach trigger while leaving the global flag and retry counter intact for the original interaction.

**Why this matters:** The legacy walker lock is reentrant. Without scoped ownership, opening a closed door during an object or NPC recovery starts another recovery walk from inside the first one, replaces the route target, and spends the shared retry budget instead of clicking the door.

**Pattern to follow:**

```java
if (CantReachTargetRecovery.shouldStart(detectionEnabled, cantReachTarget)) {
    if (CantReachTargetRecovery.walkTo(originalTarget, 2)) {
        clearCantReachState();
    }
}
```

**Where this applies:** `Rs2GameObject.clickObject`, `Rs2Npc.interact`, `Rs2NpcModel.interact`, legacy walker door dispatch, and any future interaction helper that starts `Rs2Walker.walkTo` in response to the global can't-reach flag.

**Defensive check:** During a recovery route through a closed door, assert that the door click occurs once, the original object or NPC target is passed unchanged to the walker, nested recovery is suppressed, and retry exhaustion still returns failure.

## 17. Give catalogued opening doors a handler when search selects WALK

Live collision can mark an openable door edge passable so search routes through it. The resulting route may select an ordinary walking edge instead of the parallel catalog transport. Suppress generic door detection only when that opening door has a selected object transport; keep catalog ownership while the route is unavailable and for moves-you objects such as ladders and stiles.

**Why this matters:** Fishing Guild door `20925` stalled in both directions: generic door detection rejected catalog membership, while transport execution required a selected transport and the route contained only walking edges.

**Where this applies:** `Rs2DoorProbe.isCatalogTransportObject`, live collision door masking, and route-selected transport execution.

**Defensive check:** Verify the same catalog door is eligible for generic handling on a WALK edge and excluded on a selected TRANSPORT edge; test entry and exit on the live client.

## 18. Clear successful door crossings at the start of a new walk

The recently-opened suppression window belongs to the route that crossed the door. Clear it when a new walk starts, while retaining the separate per-edge attempt cooldown. Self-closing doors can require another interaction immediately on a return route.

**Why this matters:** Returning into the Fishing Guild within ten seconds of leaving hid entrance door `20925` from detection. A route through both doors selected the inner door first, failed to reach it, and only tried the entrance after the old suppression expired.

**Where this applies:** `Rs2Walker.resetWalkSessionState` and `DoorAttemptLedger`.

**Defensive check:** Exit the guild and immediately route back through both doors. The entrance must be selected before the inner door, with the anti-hammer cooldown still intact.

## 19. Select the first reachable route interaction before an approach click

A minimap target legitimately stops on the near side of a closed door or object transport. Before yielding to an active minimap interim or choosing another approach tile, inspect raw route edges in order and dispatch only the first unresolved interaction whose near-side approach is reachable. A transport origin may itself be blocked by the object, so a reachable adjacent predecessor is sufficient for that selected edge. Check selected transports before same-plane door geometry so ladders and trapdoors that change plane are not skipped. Keep the existing transport executor for its action, variant, and landing rules.

**Why this matters:** Smoothed segments can put an empty approach segment before a visible door or staircase. Treating the first processed segment as the first obstacle delays interaction until the player stops beside it. Continuing a scan after the first door is throttled or fails can click a second door through the first one. A nearby route endpoint also does not make a distant door object reachable.

**Pattern to follow:** Select one raw edge using reachable route tiles and the exact active transport selection; if its action is deferred or fails, keep that edge pending while the walker approaches or retries it. Use the object location for interaction range, not a smoothed segment endpoint.

**Where this applies:** `Rs2Walker` pre-click and active-interim handling, `Rs2WalkerDoors` segment and pending-door scans, and `Rs2DoorGeometry` range checks.

**Defensive check:** With two visible doors, a closed first door must remain selected after a failed click; opening it allows the second. An earlier wall or non-object transport must stop the ranged scan. A cross-plane object transport can be selected from a reachable adjacent approach tile even when its origin is absent from collision reachability.

## 20. Skip backtracked transport edges below the player's raw anchor

Door and scene-object scans retain a lookback window for nearby ordinary doors. Skip transport edges below the player's raw anchor rather than stopping the scan or dispatching them again. Only transports at or ahead of the anchor block later interactions. The recent handled-transport window expires after eight seconds and does not record crossings made by plain walking.

**Why this matters:** A player paused just beyond a same-plane gate could never handle a door ahead after recent-transport suppression expired.

**Where this applies:** `Rs2Walker` raw-route scene and pending-door scans, `handleFirstRouteInteractionAtRange` transport-map construction, and `Rs2WalkerDoors.handlePendingDoorNearRawPath`.

**Defensive check:** With a transport behind the raw anchor and a door ahead, select the door outside the suppression window and after plain walking. Transports at or ahead of the anchor must still block later doors. Test the production transport map used by both the pending-interaction predicate and the blocked-transport predicate, rather than filtering only the test predicates.

## 21. Apply the shared energy policy before clicking the run orb

Every run-enable caller must pass through `Rs2Player.toggleRunEnergy`: energy must exceed
`Microbot.runEnergyThreshold` in hundredths of a percent (default 1000 = 10%). The
walker previously checked whole percentages in one path while direct scene/bank calls
bypassed the check. An already satisfied state and explicit disable do not require energy.

**Why this matters:** At zero energy, repeated requests cannot enable run. The orb's
canvas location is its bounding-box corner, outside its circular hit area. Read visibility
and bounds on the client thread and target the center; perform mouse gestures off-thread.
A click is only a request: the helper returns true only if the desired state is observed,
and throttles retries while the update is pending. Normal script iterations can retry.

**Where this applies:** `Rs2Player`, `Rs2Walker`, `Rs2WalkerMovement`, bank/deposit helpers,
and the base `Script` auto-run policy. The shared threshold now consistently uses raw
energy (>1000 by default), replacing the walker's rounded >10% (>=1100) check.

**Defensive check:** `Rs2PlayerRunEnergyTest` covers threshold boundaries, explicit disable,
missing/hidden orbs, interior geometry, pending updates, and client-thread requests.

## 22. A scene walk click is only valid on a tile rendered at click time

`MenuAction.WALK` carries canvas coordinates. The client resolves the destination from the
tile under that point during the next rendered frame. A point over an unrendered tile selects
nothing. If the camera or player moved after the point was computed, it selects a different
tile. Checking that the projection lies inside the viewport proves neither condition.

**Why this matters:** Software rendering (GPU off) draws only 25 tiles around the camera eye,
not around the player. `Scene.getDrawDistance()` keeps reporting the GPU value after GPU is
disabled, so read `client.isGpu()` first. Tiles beyond that square still project into the
viewport, and the old helper reported success for clicks that set no destination. Route
camera turns held by arrow keys can also move the view during the natural-mouse gesture.

**Where this applies:** `Rs2WalkerMovement.walkFastCanvasOnScreenOnly`, `dispatchSceneWalk`,
and the public `Rs2Walker.walkFastCanvas`. Toggle run before computing the point. Reject tiles
within 2 of the rendered edge, and skip scene clicks while the walker is turning the camera.
After dispatch, report success only once the client destination lies within 2 tiles of the
target. Otherwise return false so the caller's minimap fallback runs once. After two
consecutive failures, scene clicks pause for 3 seconds.

**Defensive check:** `SceneClickPolicyTest` covers draw-distance selection, camera-relative
bounds, destination classification, and bounded suppression.

## 23. Route camera turns are for targets the scene cannot reach

`Rs2Walker.alignCameraTowardWalkTarget` runs after every scene and minimap walk click. A
visible, rendered click target gains nothing from a turn. The turn only moves the view the
user is watching, and while its arrow keys are held, scene clicks are skipped.

**Why this matters:** The old alignment re-randomised yaw offset and pitch every 5–10 seconds
and turned even when the target was on screen. On five fixed routes, 22 of 29 turns started
while the destination was already clickable.

**Where this applies:** `RouteCameraPolicy.decide`. Turn only when the target is at least
4 tiles away, 1.2 seconds have passed since the last turn, and one of two conditions holds:
the target is not scene-clickable, or a scene click failed within the last 2 seconds. When
the heading is within 20 degrees, turn only if a view variation is due. View variation
(yaw offset and pitch) is applied only to turns that are already needed. After a scene
fallback click toward a nearer visible tile, the walker aligns toward the requested target.

**Defensive check:** `RouteCameraPolicyTest` covers visible, hidden, aligned, recently
failed, near, and throttled cases.

## 24. A camera turn is finished only when the yaw stops changing

Releasing the arrow key does not stop the camera. The yaw keeps easing for several client
ticks, and any canvas point computed in that window belongs to a view that no longer exists.

**Why this matters:** In a live probe at the Grand Exchange, the yaw kept changing for
115–126 ms (7 client ticks) after `setAngle` released the key. It drifted another 12.6–17.6
degrees, and the target object's canvas point moved 56–121 px. Door and transport
interactions click right after `turnTo`, so they could land on a stale position.

**Where this applies:** `Rs2Camera.setAngle`, `turnTo` and `adjustPitch` all go through
`holdUntil`. After releasing the key, it waits with `sleepUntil` (600 ms cap) until yaw and
pitch read the same on two samples taken on different client ticks. Each sample is read on
the client thread. Callers should not add their own sleeps after `turnTo`.

The settle check cannot tell our own coasting apart from anything else that moves the camera.
While `NpcTracker` is steering the yaw with `setCameraYawTarget`, the QoL plugin's smooth camera
tracking is on, or the user is dragging the camera, the yaw never holds still for two ticks
and every turn waits the full 600 ms cap before returning. That is the bound working, not a
hang; the view is still moving when the call returns, so a canvas point taken right after it
can still be stale.

If the client-thread read fails (thread interrupted or the client-thread timeout), `getYaw` and
`getPitch` fall back to reading the camera directly instead of returning 0. A yaw of 0 is
north and a pitch of 0 sends `adjustPitch` holding UP, so a fake 0 looked like a real angle.
`adjustPitch` returns without pressing a key when the pitch is already at the target.

**Defensive check:** `Rs2CameraSettleTest` simulates a camera that coasts after release. It
also covers the timeout bound and the client-thread no-op.

## 25. Bound scene tile loops by the tile array, not `Constants.SCENE_SIZE`

The local player's worldview is not always the 104x104 top-level scene. On a sailing boat
the player stands in a sub-worldview whose `Scene.getTiles()` is only 8 or 16 tiles wide.
A loop that runs `x, y < Constants.SCENE_SIZE` over that array throws
`ArrayIndexOutOfBoundsException` at the boat's width.

**Why this matters:** `Rs2GameObject.getSceneObjects` read tiles from
`player.getWorldView().getScene()` and looped to `SCENE_SIZE`. Every scene-object query made
from a boat threw `Index 8 out of bounds for length 8`, which aborted the walker inside
`handleNearbyRawPathSceneObjects` and crashed banking in scripts that called `findBank`.

**Pattern to follow:** take the loop bounds from the array you index (`tiles[z].length`,
`tiles[z][0].length`), or from `worldView.getSizeX()` / `getSizeY()`, whenever the tiles come
from a worldview that is not guaranteed to be the top-level one.

**Defensive check:** `Rs2GameObjectSmallWorldViewTest` queries wall objects on an 8x8
worldview and expects the object on the last tile.

## 26. Null-check `LocalPoint.fromWorld` before converting instance coordinates

`LocalPoint.fromWorld(worldView, worldPoint)` returns `null` when the plane differs from the world view's plane or the tile is outside that scene, for example in an instance while the scene is still loading. The fallback below only prevents the crash: if the player is on a boat sub-worldview, `player.getLocalLocation()` is in boat space and does not give the real sea position. `WorldPoint.fromLocalInstance` dereferences its `LocalPoint` immediately, so the result must be checked first.

**Why this matters:** `Rs2Player.getWorldLocation_Internal()` passed the `fromWorld` result straight into `fromLocalInstance`. Telemetry for 2.6.30 and 2.6.31 recorded about 585 NullPointerExceptions, most from `KourendLibraryOverlay.render` calling `Rs2Player.getWorldLocation()` every frame.

**Pattern to follow:**

```java
// Wrong: NPE when the world point does not resolve in the top-level scene.
LocalPoint l = LocalPoint.fromWorld(client.getTopLevelWorldView(), player.getWorldLocation());
return WorldPoint.fromLocalInstance(client, l);

// Right: fall back to the player's own local point, then to the raw world point.
LocalPoint l = LocalPoint.fromWorld(client.getTopLevelWorldView(), player.getWorldLocation());
if (l == null) l = player.getLocalLocation();
if (l == null) return player.getWorldLocation();
return WorldPoint.fromLocalInstance(client, l);
```

**Where this applies:** `Rs2Player.getWorldLocation_Internal()` and any helper that converts an actor's world point to an instance template point.

**Defensive check:** Mock a world view whose plane differs from the player's world point and assert the helper returns a fallback point, as in `Rs2PlayerInstanceWorldLocationTest`.

## 27. Bounds-check a `LocalPoint` against its own worldview before projecting it

`Perspective.getCanvasTilePoly` only rejects points outside the extended scene (`getSizeX() + 2 * ESCENE_OFFSET` on the top-level view). A tile with scene X/Y in 104..143, or a negative one, still reaches `WorldView.getTileHeight`, which indexes the normal scene height array and throws `ArrayIndexOutOfBoundsException`. Resolve the worldview from `localPoint.getWorldView()` and check the scene X/Y against `0..getSizeX()-1` / `0..getSizeY()-1` before projecting.

**Why this matters:** `Rs2Camera.isTileOnScreen` passed NPC and tile-object local points straight to `getCanvasTilePoly`. Telemetry for 2.6.30 to 2.6.32 recorded `Index 109/111/126 out of bounds for length 105` from `Rs2NpcModel.click` and `Rs2TileObjectModel.click`, which aborted the interaction instead of turning the camera.

**Pattern to follow:**

```java
// Wrong: out-of-scene points throw inside Perspective.
Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);

// Right: treat a point outside its worldview's scene as off screen.
if (localPoint == null) return false;
WorldView wv = client.getWorldView(localPoint.getWorldView());
if (wv == null || localPoint.getSceneX() < 0 || localPoint.getSceneY() < 0
        || localPoint.getSceneX() >= wv.getSizeX() || localPoint.getSceneY() >= wv.getSizeY()) return false;
Polygon poly = Perspective.getCanvasTilePoly(client, localPoint);
```

**Where this applies:** `Rs2Camera.isTileOnScreen(LocalPoint)`, `Rs2Camera.isTileOnScreen(TileObject)`, and any helper that projects an entity's local point to the canvas.

**Defensive check:** Mock a worldview whose `getTileHeight` throws and assert out-of-scene points return false without calling it, as in `Rs2CameraTileOnScreenTest`.

## 28. A door attempt that ends on the near side is a refusal, not a traversal

Judge a door crossing by the edge's own axis (`Rs2DoorGeometry.crossedDoorAxis`), not by whether the player changed side relative to the door object's tile. A wall door usually sits on the near-side tile of its edge, so walking onto that tile, or onto its row or column, flips the object-relative reading while the player is still in front of the door. Count a cross-nudge that leaves the player stationary on the near-side tile as a refusal too. Keep the walk-scoped strike-out blocks with the outermost walk: a can't-reach recovery walk nested inside it is not a new walk session.

**Why this matters:** During Misthalin Mystery auto-play the route went through locked door 30117. Each open click was read as a traversal because the player stepped from (1634,4839) to (1633,4838), level with the door at (1633,4837). That verdict wiped the door's strikes and hid it as recently opened, the follow-up nudge moved the player onto the near-side tile and was not counted, and `door_recovery_suppressed | reason=nearby-route-door` kept the walk waiting for handlers that would never strike the door out. The strike-outs that did land were lifted again by the nested can't-reach recovery walk the next door click started. The walker looped for about 11 minutes.

**Pattern to follow:**

```java
// Wrong: object-relative side flip counts the approach as a crossing.
return movedAcrossInteractedObject(start, end, doorTile) && end.distanceTo2D(toWp) < start.distanceTo2D(toWp);

// Right: on a cardinal edge the player must be at or beyond the far side.
if (isCardinalEdge(fromWp, toWp) && !Rs2DoorGeometry.crossedDoorAxis(fromWp, toWp, end)) return false;
```

**Where this applies:** `Rs2WalkerDoors.didTraverseInteractedDoor`, `Rs2WalkerDoors.tryDoorEdgeCrossNudge`, `Rs2Walker.withdrawPreviousWalkScopedBlocks`, and any door verdict that feeds `DoorAttemptLedger` strikes.

**Defensive check:** Replay the loop as pure verdicts against a `DoorAttemptLedger` and assert the edge strikes out within `DOOR_CROSS_FAILURE_STRIKE_LIMIT` cycles, as in `LockedDoorStrikeOutTest`.

## 29. Null-check a scene `Tile` before pathing from or to it

A slot in `scene.getTiles()[plane][x][y]` can be `null` even when the `LocalPoint` for that position resolved, for example an empty tile or a slot that has not been filled yet after a region change. `Rs2Tile.pathTo` and `Rs2Tile.fullPathTo` treat a missing endpoint as "no path", and callers that read a tile from the scene array must do the same before using it.

**Why this matters:** `Rs2WorldPoint.pathTo` passed the scene tile straight into `Rs2Tile.fullPathTo`, which called `source.getPlane()`. Telemetry for 2.6.38 recorded a `NullPointerException` from `Rs2GameObject.canReach`, which threw instead of returning `false`.

**Pattern to follow:**

```java
// Wrong: the scene slot may be empty.
Tile sourceTile = tiles[plane][x][y];
return Rs2Tile.fullPathTo(sourceTile, targetTile);

// Right: no tile means no path.
Tile sourceTile = tiles[plane][x][y];
if (sourceTile == null || targetTile == null) return null;
return Rs2Tile.fullPathTo(sourceTile, targetTile);
```

**Where this applies:** `Rs2WorldPoint.pathTo`, `Rs2Tile.pathTo`, `Rs2Tile.fullPathTo`, and any helper that reads a `Tile` from the scene array.

**Defensive check:** Mock a scene with an empty source or target slot and assert the path is `null`, as in `Rs2WorldPointNullSceneTileTest` and `Rs2TileNullPathEndpointTest`.
