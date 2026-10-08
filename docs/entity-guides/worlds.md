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
return Microbot.getClient().getLocalPlayer().getPoseAnimation();

// Right: read once, guard, and fall back to the documented "no player" value.
Player localPlayer = Microbot.getClient().getLocalPlayer();
return localPlayer == null ? null : localPlayer.getPoseAnimation();
```

**Where this applies:** `Rs2Player` accessors (`getWorldLocation_Internal`, `getPoseAnimation`, `getAnimation`, `getCombatLevel`, `getLocalPlayer`, `getLocalLocation`, `getGraphicId`, `hasSpotAnimation`) and any new helper that reads the local player.

**Defensive check:** Mock `Client.getLocalPlayer()` to return `null` and assert each accessor returns its fallback, as in `Rs2PlayerNullLocalPlayerTest`.
