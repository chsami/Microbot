# World Gotchas

## 1. Probe supported endpoints; do not assign ports by world ID

World-list entries provide hostnames, not a stable per-world port declaration. Use the shared world-hopper TCP ping routine, which tries the supported endpoints with bounded connect timeouts. Do not maintain a table of world IDs or assume that a successful or failed connection observed once is permanent.

**Why this matters:** World endpoint availability can change within minutes. A world reachable only on TCP port 443 during one probe may later accept port 43594 as well, while another endpoint may temporarily accept neither. Hard-coding port 43594 caused valid worlds to be scored as unreachable by `Rs2WorldUtil`.

**Pattern to follow:**

```java
// Wrong: duplicates one endpoint and can drift from World Hopper.
socket.connect(new InetSocketAddress(world.getAddress(), 43594), 3000);

// Right: shares the bounded endpoint fallback used by World Hopper.
int ping = Ping.tcpPing(InetAddress.getByName(world.getAddress()));
```

**Where this applies:** `Ping`, `Rs2WorldUtil`, world selectors, login-world scoring, and any helper that tests world reachability.

**Defensive check:** Test fallback with local sockets so the first port refuses the connection and the second succeeds. Live endpoint probes are useful evidence, but must not become a permanent world-to-port mapping.

## 2. Treat the local player as absent during login, hop, and loading

`Client.getLocalPlayer()` returns `null` on the login screen, while hopping, and during some loading states. Scripts keep ticking through those states, so any helper that reads the local player must check for `null` and return the value its callers already treat as "no player".

**Why this matters:** `Rs2Player.getWorldLocation_Internal()` and `Rs2Player.getPoseAnimation()` dereferenced the local player directly. Telemetry for 2.6.30 recorded about 100 NullPointerExceptions from these lambdas in one day, many reached through `Rs2PlayerStateCache.refreshLocalPlayer()` from ordinary `Rs2Player.getWorldLocation()` calls.

**Pattern to follow:**

```java
// Wrong: throws on the client thread whenever the player is not loaded.
return Microbot.getClientThread().runOnClientThreadOptional(() ->
        Microbot.getClient().getLocalPlayer().getPoseAnimation()
).orElse(-1);

// Right: read once, yield an empty Optional, and let orElse supply the "no player" value.
return Microbot.getClientThread().runOnClientThreadOptional(() -> {
    Player localPlayer = Microbot.getClient().getLocalPlayer();
    return localPlayer == null ? null : localPlayer.getPoseAnimation();
}).orElse(-1);
```

**Where this applies:** `Rs2Player` accessors (`getWorldLocation_Internal`, `getPoseAnimation`, `getAnimation`, `getCombatLevel`, `getLocalPlayer`, `getLocalLocation`, `getGraphicId`, `hasSpotAnimation`), the position accessors of `Rs2ActorModel` (`getWorldView`, `getWorldLocation`, `getLocalLocation`, `projectActorLocationToMainWorld`) and its `getAnimation` (returns -1), which `new Rs2PlayerModel()` reaches with a null actor, the callers of those accessors (`Rs2Camera.angleToTile` returns the current facing so a turn is a no-op), both `Rs2NpcModel` classes' `getDistanceFromPlayer` (returns `Integer.MAX_VALUE`) and `isWithinDistanceFromPlayer` (returns `false`), `Rs2GameObject.clickObject` (returns `false` when it falls back to world-point distance), `Rs2GroundItem.getAllFromWorldPoint` (returns an empty array, and sorts against one captured `LocalPoint` instead of re-reading the player in the comparator), and any new helper that reads the local player.

**Defensive check:** Mock `Client.getLocalPlayer()` to return `null` and assert each accessor returns its fallback, as in `Rs2PlayerNullLocalPlayerTest`, `Rs2PlayerModelNullLocalPlayerTest`, `Rs2NpcModelDistanceFromPlayerTest` and `Rs2GroundItemNullLocalPlayerTest`.

**Wrapped locations:** `Rs2Player.getRs2WorldPoint()` still returns an `Rs2WorldPoint`, but it can wrap a `null` `WorldPoint` in these states. `pathTo` returns `null` and `distanceToPath` returns `Integer.MAX_VALUE` for it; check `getWorldPoint() != null` before calling `getX()`, `getY()` or `getPlane()`, as in `Rs2WorldPointNullPointTest`.

**Location consumers:** `Rs2Player.getWorldLocation()` itself returns `null` in these states. Guard it before passing it to `Rs2RouteRequest.to`/`toAny` (which reject a null start) or `WorldPoint.distanceTo`. `Rs2Bank.getNearestBank`, `getNearestBankRoute` and `getPathToNearestBank` return no bank, `isNearBank` and `walkToBank` return `false`, `BankLocation.MINING_GUILD.hasRequirements` returns `false`, `Rs2Walker.getDistanceBetween` returns `Integer.MAX_VALUE` for a null endpoint, as in `Rs2BankNullPlayerLocationTest`, and `Rs2Walker.proximityWaitSatisfied` returns `false` for a null location, so once the `processWalk` post-click wait reaches it (the player is still moving after 600 ms) it keeps waiting until its timeout, as in `Rs2WalkerProximityWaitTest`.

## 3. Membership reads as 0 until the server sends it

`VarPlayerID.ACCOUNT_CREDIT` (days of membership left) and `VarClientID.PLAYERMEMBER` (the member flag) both read 0 until the server sends them after login, which looks the same as a free-to-play account. A member can also have 0 credit days left, so read both.

**Why this matters:** the profile's saved membership picks a members or free world before the next login. Saving a single reading taken when the game state turns `LOGGED_IN` can record a member as free-to-play.

**Pattern to follow:** `MembershipRecorder` reads both values on each game tick once the local player exists and saves them again whenever they change, which also catches a bond redeemed during the session. Read the saved value through `MembershipRecorder.remainingDays`, which counts the days down from when they were saved.

**Defensive check:** `MembershipRecorderTest` feeds a 0 reading before the real one and asserts that the later reading replaces it.
