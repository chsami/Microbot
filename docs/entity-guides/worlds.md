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
