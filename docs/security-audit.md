# Security Audit — Outbound Network Calls

**Date:** 2026-03-17
**Scope:** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/` + RuneLite core

---

## Summary

| Category | Count | Decision |
|----------|-------|----------|
| REMOVE — telemetry / phone-home | 4 files | Deleted |
| REMOVE — startup call cleanup | 2 files modified | Stripped |
| KEEP (user-controlled webhook) | Discord notifier | Kept — opt-in only |
| KEEP (game-essential) | World/client loader | Kept |
| KEEP (RuneLite core) | TelemetryClient | Already gated by `--disable-telemetry` flag |
| KEEP (GE price APIs) | Rs2GrandExchange | Kept — OSRS Wiki + ge-tracker, bot-useful |
| VERIFY → KEEP | GameChatAppender | Local only, no network |
| VERIFY → KEEP | MicrobotRSConfig | No remote fetch, local config object |
| VERIFY → KEEP | GlobalConfiguration.URLs | String constants only, never fetched |

---

## Step 1 — HTTP Client Hits

### `MicrobotVersionChecker.java`

**Classification: REMOVE**

- File: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/MicrobotVersionChecker.java`
- Phones `https://microbot.cloud/api/version/client` every 10 minutes.
- Tells the upstream Microbot cloud server our version — unnecessary telemetry for a private fork.
- **Action:** File deleted. References removed from `MicrobotPlugin.java`.

### `RandomFactClient.java`

**Classification: REMOVE**

- File: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/RandomFactClient.java`
- Polls `https://microbot.cloud/api/fact/random` every 20 seconds during the splash screen.
- Purely cosmetic, pings the upstream Microbot cloud API repeatedly on startup.
- **Action:** File deleted. Import + usage stripped from `SplashScreen.java`.

### `MicrobotApi.java`

**Classification: REMOVE**

- File: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/MicrobotApi.java`
- Three endpoints, all phoning `https://microbot.cloud/api`:
  - `microbotOpen()` — opens a session UUID on the upstream server.
  - `microbotPing()` — pings login-state every 10 minutes to upstream.
  - `microbotDelete()` — deletes session on shutdown.
  - `increasePluginInstall()` — reports every plugin install with a hardcoded telemetry token.
- This is the primary call-home mechanism. Tracks which plugins are running on which client.
- **Action:** File deleted. All usage removed from `ClientSessionManager.java`.

### `Rs2Discord.java` (util/discord/)

**Classification: KEEP — user-controlled, opt-in webhook**

- File: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/discord/Rs2Discord.java`
- Sends POST requests to a Discord webhook URL, but **only** if the user has configured a webhook URL in their RuneLite profile.
- Calls are initiated by the break handler (ban detection, break start/end) — useful bot-management feature.
- URL is supplied by the user; no hardcoded upstream endpoint.
- **Action:** Kept as-is. No changes required.

### `MicrobotClientLoader.java` / `MicrobotClientConfigLoader.java` / `WorldSupplier.java` / `MicrobotWorldSupplier.java`

**Classification: KEEP (game-essential)**

- These use `OkHttpClient` to load the RuneScape game client JAR from Jagex servers, fetch world lists, and parse the jav_config.
- The codebase URL is set to `http://<world.getAddress()>/` (Jagex game server).
- Essential for launching the game client. No upstream Microbot traffic.

### `Rs2GrandExchange.java`

**Classification: KEEP (game-utility)**

- File: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/grandexchange/Rs2GrandExchange.java`
- Uses `HttpClient` to query:
  - `https://prices.runescape.wiki/api/v1/osrs/...` — OSRS Wiki real-time GE prices (public API, no auth).
  - `https://www.ge-tracker.com/api/items/` — GE Tracker price API.
- Both are public read-only price APIs used for bot trading logic.
- No authentication tokens, no personal data transmitted.
- **Action:** Kept. These are legitimate bot-utility calls.

---

## Step 2 — Analytics / Telemetry Hits

### `MicrobotApi.java` — `pluginTelemetryToken`

**Classification: REMOVE** (covered above)

- Hardcoded token `"zeifkdsjqfiedfb15181=="` in `increasePluginInstall()`.
- Called every time a plugin is enabled — reports plugin name, version to Microbot cloud.

### `ClientSessionManager.java` — microbot session pinging

**Classification: REMOVE (cleaned up)**

- Injected `MicrobotApi` and called `microbotOpen()` / `microbotPing()` / `microbotDelete()`.
- Also has a `disableTelemetry` flag from RuneLite's `--disable-telemetry` CLI argument that gates the RuneLite session ping. The microbot-specific ping was **not** gated by this flag.
- **Action:** Microbot-specific fields and calls removed. RuneLite's own `sessionClient` ping remains (controlled by `--disable-telemetry`).

### `SplashScreen.java` — random fact polling

**Classification: REMOVE (cleaned up)**

- Calls `RandomFactClient.getRandomFactAsync()` every 20 seconds while splash is open.
- Purely cosmetic; pings Microbot cloud API.
- **Action:** Import, scheduled executor, and call removed. Static `factValue` field left as "Fetching a tip..." (harmless fallback text).

---

## Step 3 — External URL Hits

### `GlobalConfiguration.java` — `URLs` inner class

**Classification: KEEP (string constants only)**

- File: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/globval/GlobalConfiguration.java`
- Contains string constants pointing to `https://github.com/OSRSB/` and `https://osrsbot.org/`.
- The `getHttpConnection()` helper method exists but is never called from production paths in microbot itself (legacy RSB code).
- No automatic network calls. Strings are only used if explicitly invoked.
- **Action:** No change. Dead code; will not phone home.

### `MicrobotRSConfig.java`

**Classification: KEEP (no network)**

- Pure in-memory config object. Stores `codebase`, `initial_jar`, `initial_class` as local string properties.
- The codebase value is set programmatically from the game world address — not fetched remotely by this class.
- **Action:** No change.

### `MicrobotVersionChecker.java` — `REMOTE_VERSION_URL`

**Classification: REMOVE** (covered above)

- `https://microbot.cloud/api/version/client`

### `RandomFactClient.java` — `MICROBOT_API_URL`

**Classification: REMOVE** (covered above)

- `https://microbot.cloud/api`

### `MicrobotApi.java` — `microbotApiUrl`

**Classification: REMOVE** (covered above)

- `https://microbot.cloud/api`

### `MicrobotRSAppletStub.java` — "Game Status" button

**Classification: KEEP (user-initiated only)**

- `https://secure.runescape.com/m=news/game-status-information-centre?oldschool=1`
- Opens in a browser via `LinkBrowser.browse()` only when the user explicitly clicks "Game Status".
- No automatic calls.

### `MicrobotPluginConfigurationDescriptor.java` — plugin hub links

**Classification: KEEP (user-initiated only)**

- `https://chsami.github.io/Microbot-Hub/<pluginName>`
- Opens in a browser only on explicit user click. No automatic calls.

### `DiscordEmbed.java` — example URLs in Javadoc

**Classification: KEEP (documentation only)**

- `https://example.com` — appears only in class Javadoc comments, never executed.

### `Rs2WorldPoint.java` — algorithm reference URL

**Classification: KEEP (comment only)**

- URL in a code comment, never executed.

---

## Step 4 — RuneLite Core Telemetry

### `TelemetryClient.java`

**Classification: KEEP (already opt-out gated)**

- File: `runelite-client/src/main/java/net/runelite/client/TelemetryClient.java`
- Submits system telemetry (Java version, OS, RAM, CPU) and JVM crash logs to RuneLite API.
- In `RuneLiteModule.java`: `return disableTelemetry ? null : new TelemetryClient(...)` — returns `null` when `--disable-telemetry` flag is passed.
- In `Hooks.java` and `RuneLite.java`: null-checked before use.
- **Action:** No change. Already properly gated. Fork should launch with `--disable-telemetry` to ensure this never fires.

### `ClientSessionManager.java` — RuneLite session ping

**Classification: KEEP (already gated)**

- The `sessionClient.open()` / `sessionClient.ping()` calls ping RuneLite's own API (`api.runelite.net`).
- Gated by the same `disableTelemetry` flag in `start()` and `onClientShutdown()`.
- **Action:** No change (microbot-specific calls were removed; RuneLite's own session management remains as-is, gated by flag).

---

## Files Deleted

| File | Reason |
|------|--------|
| `MicrobotVersionChecker.java` | Polls `microbot.cloud` for version every 10 min |
| `RandomFactClient.java` | Polls `microbot.cloud` every 20 sec during splash |
| `MicrobotApi.java` | Session + plugin install telemetry to `microbot.cloud` |

## Files Modified

| File | Change |
|------|--------|
| `MicrobotPlugin.java` | Removed `@Inject MicrobotVersionChecker`, removed `microbotVersionChecker.checkForUpdate()` in `startUp()`, removed `microbotVersionChecker.shutdown()` in `shutDown()` |
| `ClientSessionManager.java` | Removed `MicrobotApi` import/field/constructor param, removed `microbotSessionId`, `scheduledFutureMicroBot`, `microbotPing()` method, all microbot-specific call blocks |
| `SplashScreen.java` | Removed `RandomFactClient` import, `scheduledRandomFactExecutorService`, `scheduledRandomFactFuture` fields, and the `scheduleAtFixedRate` block in `init()` |

---

## Final Verification

After removals, the bot connects only to:

- **Jagex game servers** — `*.runescape.com` (game client, world list)
- **RuneLite API** — `api.runelite.net` (only if `--disable-telemetry` is NOT set; gated)
- **OSRS Wiki prices** — `prices.runescape.wiki` (GE price lookups, optional bot feature)
- **GE Tracker** — `www.ge-tracker.com` (GE price lookups, optional bot feature)
- **User-configured Discord webhook** — only when user sets a webhook URL in profile (opt-in)
- **`secure.runescape.com`** — game status page, user-initiated browser open only
