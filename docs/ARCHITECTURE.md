# Architecture

## Components & Interaction
- **RuneLite client (`runelite-client`)**: Main application with Microbot hidden plugin (`MicrobotPlugin`) always enabled; entrypoint `net.runelite.client.RuneLite`. Builds shaded distribution via `shadowJar` + `microbotReleaseJar`.
- **Microbot runtime**: `Microbot` singleton exposes caches, utilities, and script lifecycle helpers; `Script` base class drives scheduled script loops; `BlockingEventManager` preempts scripts when required UI/game states are detected.
- **Queryable caches**: Guice-injected caches (`Rs2NpcCache`, `Rs2PlayerCache`, `Rs2TileItemCache`, `Rs2TileObjectCache`, `Rs2BoatCache`, `Rs2PlayerStateCache`) updated per tick and accessed via `Microbot.getRs2XxxCache().query()` or `.getStream()`. World-view aware for boats.
- **Utilities (`microbot/util`)**: Facades over RuneLite APIs for player, inventory, banking, walking, etc.; expected to run on script threads, not the client thread.
- **Included builds**: `runelite-api` (shared API artifacts), `runelite-gradle-plugin` (assemble/index/jarsign tasks), `cache` (cache tooling), `runelite-jshell` (JShell support). Root Gradle orchestrates via composite build.
- **Telemetry/API** (REMOVED in this fork): Upstream `MicrobotApi`, `MicrobotVersionChecker`, and `RandomFactClient` were deleted. No outbound calls to `microbot.cloud`. RuneLite core `TelemetryClient` remains but is gated by `--disable-telemetry`. See `docs/security-audit.md`.
- **Command Center integration**: `commandcenter/` package provides StatusApiServer (localhost-only HTTP for CC polling), AutoLoginPlugin (profile-based credentials), ScriptAutoStartPlugin (plugin activation by name), and 5 CC bot scripts with 6 reusable behaviors.

## Key Data Flows
- User starts client → `MicrobotClientLoader` fetches jav_config/world info → RuneLite client loads → `MicrobotPlugin` starts, registers overlays/config panels and initializes caches.
- Script loop (`Script.run()` implementations) executes on scheduled executors → queries caches via Queryable API → performs interactions through utilities (`Rs2Inventory`, `Rs2Walker`, etc.) → waits with `sleepUntil` helpers.
- Blocking events (`BlockingEventManager`) continuously validate (e.g., welcome screen, bank popups) → if triggered, they run on a dedicated executor and block script progression until resolved.
- Command Center flow: CC writes bot profile → launches Java process with env vars → StatusApiServer binds ephemeral localhost port → CC WatchdogService discovers port file → polls `/status` for live bot state (character, script, XP, location).

## Runtime Boundaries
- **Threads**: Client thread (never block/sleep); script/executor threads (automation logic, sleeps allowed); blocking-event executor (resolves UI blockers). Use `ClientThread.runOnClientThreadOptional` for safe client access.
- **Services vs libraries**: Microbot plugin runs inside client process; no external services beyond Microbot cloud API; caches are in-memory per client instance.
- **Distribution**: Shaded jar (`*-shaded.jar` and `microbot-<version>.jar`) produced in `runelite-client/build/libs`.

## Configuration & Environments
- Gradle properties: `gradle.properties` holds `microbot.version`, `microbot.commit.sha`, optional repo credentials (`microbot.repo.*`), and `glslang.path` (populated by CI script).
- Runtime config: `MicrobotClientLoader` consumes `RuneLiteProperties` for jav_config URL; falls back to world-supplied hosts on failure. CLI flags `--status-port-file` and `--cc-profile-dir` configure Command Center integration (via jopt-simple → System properties).
- Logging: Game chat logging configurable via `MicrobotConfig` (patterns/levels, microbot-only filter); logback appender wired at plugin startup.

## Non-Obvious Behaviors
- Caches refresh at most once per game tick; repeated queries within the same tick reuse cached lists.
- `BlockingEventManager` uses exponential backoff when no events validate to reduce overhead; events are re-queued if execution fails.
- Tests are disabled globally in `runelite-client/build.gradle.kts`; enabling requires flipping `enabled` on `Test` tasks or using `runTests`/`runDebugTests` tasks.
- World hopping short-circuits if the player is interacting or already hopping; confirmation widget auto-click handled in `Microbot.hopToWorld`.

## References
- Queryable API guide: `../runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/QUERYABLE_API.md`
- Development setup: `development.md`
- Decision records: `decisions/`
