# Development

Setup guide for building Microbot Frieren locally and creating scripts.

## Prerequisites

- **Java 17+** — JDK required (not just JRE)
- **Git** and the included Gradle wrapper (`./gradlew`) — no system Gradle needed
- **IntelliJ IDEA** recommended; follow the RuneLite wiki for baseline IntelliJ config:
  https://github.com/runelite/runelite/wiki/Building-with-IntelliJ-IDEA

## Project Layout

```
runelite-client/src/main/java/net/runelite/client/plugins/microbot/
├── commandcenter/          # Fork-specific: CC integration + bot scripts
│   ├── AutoLoginPlugin.java
│   ├── ScriptAutoStartPlugin.java
│   ├── CredentialRedactor.java
│   ├── status/             # StatusApiServer, StatusApiHandler, BotStatusModel
│   └── scripts/
│       ├── core/           # CCScript base, CCBehavior interface, 6 behaviors
│       ├── woodcutting/
│       ├── mining/
│       ├── fishing/
│       ├── cooking/
│       └── combat/
├── api/                    # Queryable API v2.1.0 (fluent entity caches)
├── util/                   # Legacy utilities (Rs2Inventory, Rs2Bank, Rs2Walker, …)
├── example/                # Example scripts
├── Microbot.java           # Singleton: caches, utilities, lifecycle
├── Script.java             # Base script class (upstream)
└── MicrobotPlugin.java     # Main RuneLite plugin
```

Tests mirror this under `runelite-client/src/test/java/.../commandcenter/`.

## Build & Run

```bash
./gradlew :runelite-client:compileJava   # quick compile (client only)
./gradlew build                          # full build (all included builds)
./gradlew cleanAll                       # clean everything
./gradlew :runelite-client:test          # run tests (must be enabled first — see below)
```

> Tests are disabled globally in `runelite-client/build.gradle.kts`.
> Flip `enabled` on the `Test` task or use the `runTests` / `runDebugTests` tasks to run them.

## IDE Setup (IntelliJ)

1. Open the root `build.gradle.kts` as a Gradle project.
2. Set **Project SDK** and **Gradle JVM** to Java 17.
3. Let IntelliJ import included builds (`cache`, `runelite-api`, `runelite-client`, `runelite-jshell`).
4. Create a Gradle Run Configuration for `:runelite-client:run` to launch the client.

**Command Center CLI flags** (pass via Run Configuration VM args or a launch script):

| Flag | Purpose |
|------|---------|
| `--status-port-file <path>` | Where the bot writes its ephemeral HTTP port for CC polling |
| `--cc-profile-dir <path>` | Directory with `credentials.properties` and `commandcenter.properties` |
| `--disable-telemetry` | Suppresses RuneLite core telemetry |

## Developing Scripts

### Option A — CC Script (Command Center managed)

Use the CC framework under `commandcenter/scripts/`:

1. Create 4 files: `CC<Name>Config.java`, `CC<Name>Script.java`, `CC<Name>Plugin.java`, `CC<Name>Overlay.java`
2. Extend `CCScript<YourStateEnum>` in the Script class; implement `configure()`, `getInitialState()`, `onTick(State)`.
3. Add behaviors to `getBehaviors()` or inherit the defaults from `CCScript`.
4. Add plugin descriptor `@PluginDescriptor(name = "CC <Name>")` — required for `ScriptAutoStartPlugin` lookup.
5. Add an entry to `CCScriptContractTest.SCRIPTS` and `CCScriptConfigureContractTest.SCRIPTS` to get 11 free contract tests.

**CCScript template:**
```java
public class CCExampleScript extends CCScript<CCExampleScript.State> {
    public enum State { WORKING, IDLE }

    @Inject private CCExampleConfig config;

    @Override protected void configure() {
        setAntiBanTemplate(AntiBanConfig.getRandomAntiBanTemplate());
    }

    @Override protected State getInitialState() { return State.IDLE; }

    @Override protected void onTick(State state) {
        switch (state) {
            case WORKING -> doWork();
            case IDLE    -> setState(State.WORKING);
        }
    }
}
```

### Option B — Standalone Script (upstream pattern)

Place scripts in `runelite-client/src/main/java/net/runelite/client/plugins/microbot/`.
Reusable helpers go in `microbot/util`.

**Minimal script loop:**
```java
public class ExampleScript extends Script {
    public boolean run(ExampleConfig config) {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!super.run()) return;
            try {
                // your logic here
            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 800, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override public void shutdown() { super.shutdown(); }
}
```

## Queryable API

Prefer the Queryable API over legacy `Rs2Npc` / `Rs2Player` util calls:

```java
// NPC query
var banker = Microbot.getRs2NpcCache().query()
    .withName("Banker").nearestOnClientThread();

// Ground item within radius
var coins = Microbot.getRs2TileItemCache().query()
    .withName("Coins").within(10).firstOnClientThread();
```

Rules:
- Never instantiate caches or queryables directly — always go through `Microbot.getRs2XxxCache()`.
- Use `*OnClientThread()` terminal helpers when filtering by name.
- Use `.fromWorldView()` for boat world views.
- Limit radius with `.within(N)` to reduce overhead.
- Never sleep on the client thread; sleeps belong on script/executor threads.

Full guide: `runelite-client/src/main/java/net/runelite/client/plugins/microbot/api/QUERYABLE_API.md`
Examples: `api/*/*ApiExample.java`

## Test Framework

119 tests under `runelite-client/src/test/java/.../commandcenter/`:

| File | Coverage |
|------|---------|
| `CCScriptTest.java` | State machine lifecycle (4 tests) |
| `CCScriptContractTest.java` | Parameterized: 7 tests × 5 scripts = 35 |
| `CCScriptConfigureContractTest.java` | Parameterized: 4 tests × 5 scripts = 20 |
| `core/behaviors/*` | 6 behavior tests (each extends `CCBehaviorTestBase`) |
| `CredentialRedactorTest.java` | Log redaction |

**Key test utilities:**

- `CCScriptTestUtils.injectConfig()` — walks class hierarchy to find `@Inject Config` field via reflection
- `CCScriptTestUtils.getBehaviors()` — accesses private `behaviors` list
- `CCScriptTestUtils.callConfigure()` — invokes protected `configure()` without modifying production visibility
- `StubCCScript` — minimal stub with `scheduledExecutorService = Executors.newScheduledThreadPool(0)` to avoid idle threads
- `CCBehaviorTestBase<B>` — extend for any behavior test; provides 3 free contract tests (priority, name, reset)

## Security Notes

- **Telemetry removed:** `MicrobotVersionChecker`, `RandomFactClient`, `MicrobotApi` deleted. No calls to `microbot.cloud`.
- **CredentialRedactor** strips sensitive data from log output.
- **Status API** binds `127.0.0.1` only — never exposed externally.
- RuneLite core `TelemetryClient` is gated by `--disable-telemetry` (not removed — preserves upstream sync compatibility).
- Full audit: `docs/security-audit.md`

## References

- Installation: `docs/installation.md`
- Architecture: `docs/ARCHITECTURE.md`
- Upstream sync: `docs/upstream-sync.md`
- Decision records: `docs/decisions/` (4 ADRs)
- API reference: `docs/api/` (30+ utility docs)
