# Microbot Frieren

Private fork of [chsami/Microbot](https://github.com/chsami/Microbot) — an OSRS automation client built on RuneLite.

**Fork changes vs upstream:**
- Telemetry removed (`MicrobotVersionChecker`, `RandomFactClient`, `MicrobotApi` → `microbot.cloud` deleted)
- Security hardened (credential redaction, localhost-only Status API)
- Command Center integration (`commandcenter/` package)
- 5 CC bot scripts (woodcutting, mining, fishing, cooking, combat) with a shared behavior framework

See `docs/security-audit.md` for the full telemetry/security audit.

---

## Installing & Running

Download shaded releases from this repo's GitHub Releases page.

```
java -jar client-<version>-SNAPSHOT-shaded.jar [flags]
```

**Command Center CLI flags:**
- `--status-port-file <path>` — where the bot writes its ephemeral HTTP port for CC polling
- `--cc-profile-dir <path>` — directory containing `credentials.properties` and `commandcenter.properties`

Linux/macOS users: swap the shaded JAR into Bolt or replace `RuneLite.jar` as described in `docs/installation.md`.

---

## Building from Source

```bash
./gradlew :runelite-client:compileJava   # quick compile
./gradlew build                          # full build
./gradlew :runelite-client:test          # run tests
```

- **Java:** 17+ (JDK required)
- **Build system:** Gradle Kotlin DSL (composite: cache, runelite-api, runelite-client, runelite-jshell)
- **Version:** `gradle.properties` → `microbot.version`
- **CI:** `.github/workflows/build.yml` — semantic versioning (v1.0.N auto-increment), shaded JAR, GitHub Release

Full setup: `docs/development.md`

---

## Command Center Integration

The `commandcenter/` package wires this client into Command Center:

| Component | Purpose |
|-----------|---------|
| `StatusApiServer` | Localhost-only HTTP server; binds ephemeral port, writes it to `--status-port-file` |
| `StatusApiHandler` | `GET /status` (JSON schema v1) and `GET /health` |
| `BotStatusModel` | Thread-safe: character, login state, script info, player stats, XP, uptime |
| `AutoLoginPlugin` | Reads `credentials.properties` from `--cc-profile-dir`; injects email/password |
| `ScriptAutoStartPlugin` | Reads `commandcenter.properties`; activates plugin by `@PluginDescriptor` name |

---

## CC Bot Scripts

Five ready-to-run scripts under `commandcenter/scripts/`:

| Script | States | Key Config |
|--------|--------|-----------|
| CCWoodcutting | CHOPPING, IDLE | tree type, bank logs |
| CCMining | MINING, IDLE | ore type, bank mode (deposit/drop/manual) |
| CCFishing | FISHING, IDLE | fish type |
| CCCooking | COOKING, IDLE | food type, range location |
| CCCombat | FIGHTING, IDLE | monster name, eat%, loot items, bury bones |

All share 6 priority-ordered behaviors: `DeathRecovery` → `Eating` → `StuckDetection` → `Looting` → `BuryBones` → `Banking`.

To add a new script: create 4 files (`Config`, `Script`, `Plugin`, `Overlay`), add it to `CCScriptContractTest.SCRIPTS` and `CCScriptConfigureContractTest.SCRIPTS` — gets 11 contract tests for free.

---

## Developing Scripts

- New scripts belong in `runelite-client/src/main/java/net/runelite/client/plugins/microbot/`
- Prefer the Queryable API over legacy `Rs2*` util calls:

```java
var banker = Microbot.getRs2NpcCache().query()
    .withName("Banker").nearestOnClientThread();
```

Full API guide: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/QUERYABLE_API.md`

Examples: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/example/`

---

## Documentation

| Doc | Contents |
|-----|---------|
| `docs/development.md` | IDE setup, build commands, script patterns |
| `docs/installation.md` | Launcher / JAR setup, Linux notes |
| `docs/ARCHITECTURE.md` | Component map, data flows, runtime boundaries |
| `docs/security-audit.md` | Telemetry removal audit |
| `docs/upstream-sync.md` | How to sync with chsami/Microbot |
| `docs/decisions/` | 4 architecture decision records |
| `docs/api/` | 30+ utility API reference docs |
