# Agentic Testing Loop Architecture

An autonomous feedback loop where an AI agent launches the Microbot client, runs scripts, reads results, fixes code, and repeats until the script works correctly — no human in the loop.

---

## Problem

Testing Microbot scripts requires a human to:
1. Build the client
2. Launch it and log in
3. Enable a plugin and observe behavior
4. Read logs, spot failures, mentally map them to code
5. Stop the client, fix the code, rebuild, repeat

This is slow and doesn't scale. We want an agent (Claude Code) to close this loop autonomously.

---

## High-Level Architecture

```
┌─────────────────────────────────────────────────────┐
│                  OUTER LOOP (Agent)                  │
│           Claude Code CLI (OAuth session)            │
│                                                     │
│  1. Build ──► 2. Launch ──► 3. Wait ──► 4. Read     │
│     ▲                                      │        │
│     │         6. Rebuild    5. Analyze ◄───┘        │
│     └────────────◄──────── Fix Code                 │
│                                                     │
└─────────────────────────────────────────────────────┘
        │                          ▲
        │  start process           │  filesystem I/O
        ▼                          │
┌─────────────────────────────────────────────────────┐
│                INNER HARNESS (JVM)                   │
│            Microbot Client + TestRunner              │
│                                                     │
│  AutoLogin ──► Start Script ──► Monitor ──► Capture │
│                                                     │
│  Outputs:                                           │
│    ~/.runelite/test-results/result.json              │
│    ~/.runelite/test-results/screenshots/*.png        │
│    ~/.runelite/logs/client.log                       │
└─────────────────────────────────────────────────────┘
```

---

## Layer 1: Inner Harness (Java-side)

The inner harness runs inside the Microbot JVM. It orchestrates a test session: login, run a target script, capture results, and exit.

### Activation

Via JVM system properties — no code changes needed to switch between normal and test mode:

```bash
java -jar microbot.jar \
  -Dmicrobot.test.mode=true \
  -Dmicrobot.test.script=ExampleScript \
  -Dmicrobot.test.timeout=120000 \
  -Dmicrobot.test.output=~/.runelite/test-results
```

| Property | Description | Default |
|---|---|---|
| `microbot.test.mode` | Enable test harness | `false` |
| `microbot.test.script` | Plugin descriptor name to auto-enable | required |
| `microbot.test.timeout` | Max runtime in ms before forced exit | `120000` |
| `microbot.test.output` | Directory for results/screenshots | `~/.runelite/test-results` |

### Components

#### TestRunnerPlugin

A hidden, always-on plugin that activates only when `microbot.test.mode=true`. Responsibilities:

1. **Wait for login** — Subscribe to `GameStateChanged`, proceed when `LOGGED_IN`.
2. **Enable target plugin** — Use `PluginManager` to find and start the plugin matching `microbot.test.script`.
3. **Monitor execution** — Watch the target script's `isRunning()` state. Capture screenshots at configurable intervals.
4. **Detect completion** — The target script calls `shutdown()` (success), the timeout fires (hang), or an uncaught exception propagates (crash).
5. **Write results** — Serialize `TestResult` to JSON, save screenshots, then `System.exit(code)`.

```java
@PluginDescriptor(name = "Test Runner", hidden = true, alwaysOn = true)
public class TestRunnerPlugin extends Plugin {

    private static final String TEST_MODE = "microbot.test.mode";

    @Override
    protected void startUp() {
        if (!"true".equals(System.getProperty(TEST_MODE))) return;
        // initialize harness, subscribe to GameStateChanged
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event) {
        if (event.getGameState() == GameState.LOGGED_IN) {
            startTargetScript();
            startMonitoring();
        }
    }
}
```

#### Screenshot Capture

Capture the client canvas as PNG at key moments:
- On script start (baseline)
- At periodic intervals (every 10s by default)
- On failure/error
- On script completion

```java
private void captureScreenshot(String label) {
    BufferedImage image = new BufferedImage(
        client.getCanvasWidth(), client.getCanvasHeight(), BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();
    client.getCanvas().paint(g);
    g.dispose();
    ImageIO.write(image, "png", new File(outputDir, label + ".png"));
}
```

For headless/CI environments, use Xvfb:
```bash
Xvfb :99 -screen 0 1280x720x24 &
export DISPLAY=:99
java -jar microbot.jar -Dmicrobot.test.mode=true ...
```

#### Structured Output

The harness writes a JSON result file that the outer agent can parse deterministically:

```json
{
  "timestamp": "2026-04-06T14:30:00Z",
  "script": "ExampleScript",
  "duration_ms": 45000,
  "exit_reason": "completed",
  "exit_code": 0,
  "checks": [
    { "name": "Microbot.isLoggedIn()", "passed": true, "error": null },
    { "name": "Rs2NpcCache stream has entries", "passed": false, "error": "no NPCs in cache" }
  ],
  "screenshots": [
    "screenshots/start.png",
    "screenshots/10s.png",
    "screenshots/failure_npc_cache.png",
    "screenshots/end.png"
  ],
  "log_tail": "last 200 lines of client.log...",
  "errors": [
    "java.lang.AssertionError: no NPCs in cache\n\tat ExampleScript.checkNpcCache..."
  ]
}
```

Exit codes:
- `0` — all checks passed
- `1` — one or more checks failed
- `2` — timeout (script hung)
- `3` — crash (uncaught exception)
- `4` — login failure

### Log Capture

Logback already writes to `~/.runelite/logs/client.log`. The harness adds a custom appender that also buffers log lines tagged with the test script's logger name, so the result JSON contains only relevant log output rather than the full client log.

```xml
<appender name="TEST_BUFFER" class="ch.qos.logback.core.read.ListAppender">
    <filter class="ch.qos.logback.classic.filter.ThresholdFilter">
        <level>DEBUG</level>
    </filter>
</appender>
```

---

## Layer 2: Outer Agent Loop (Claude Code CLI)

The outer loop is a Claude Code CLI session using the existing OAuth subscription (Max plan). No API key needed — `claude -p` works with the logged-in OAuth session. This keeps costs at zero beyond the subscription.

### Orchestration Flow

```
function agenticTestLoop(targetScript, maxIterations):
    for i in 1..maxIterations:
        1. compile()
        2. pid = launchClient(targetScript)
        3. result = waitForResult(pid, timeout=180s)
        4. analysis = analyzeResult(result)

        if analysis.allPassed:
            return SUCCESS

        5. fixes = generateFixes(analysis)
        6. applyFixes(fixes)

    return MAX_ITERATIONS_REACHED
```

### Step Details

#### 1. Build

```bash
./gradlew :client:compileJava 2>&1
```

If compilation fails, the agent reads the compiler errors, fixes the code, and retries before proceeding.

#### 2. Launch (blocking — client exits when test completes)

```bash
./gradlew :client:runTest \
  -Dmicrobot.test.mode=true \
  -Dmicrobot.test.script="Guard Killer Test" \
  -Dmicrobot.test.timeout=120000
EXIT_CODE=$?
```

This blocks until the JVM exits. The flow inside the client:
1. `TestRunnerPlugin` (alwaysOn, hidden) detects `-Dmicrobot.test.mode=true`
2. `AutoLoginPlugin` logs in using the configured profile
3. On `LOGGED_IN`, TestRunner auto-enables the target test plugin via `PluginManager`
4. The test script runs, writes `result.json`, then calls `System.exit(code)`

No polling needed — the Gradle process returns when the client exits.

#### 4. Read & Analyze

The agent reads:
1. `result.json` — structured pass/fail data
2. `screenshots/*.png` — visual game state (Claude is multimodal, it can read these)
3. `client.log` — full log context if needed

From these inputs, the agent determines:
- Which checks failed and why
- Whether the failure is a code bug, a game-state issue, or an environment problem
- Which source files are implicated (stack traces point to specific files/lines)

#### 5. Fix

The agent uses its code editing tools to:
- Read the implicated source files
- Understand the failure context
- Apply targeted fixes
- Avoid unrelated changes

#### 6. Rebuild & Repeat

Back to step 1. The loop continues until all checks pass or `maxIterations` is reached.

### Agent Invocation

All invocations use the existing OAuth session (Max subscription). No API key required.

#### Via Claude Code CLI (recommended)

```bash
claude -p "
Run the agentic test loop for ExampleScript.
Build the client, launch with test mode, read results from
~/.runelite/test-results/result.json and screenshots,
fix any failures, and repeat until all checks pass.
Max 5 iterations.
" --allowedTools "Bash,Read,Edit,Write,Glob,Grep" \
  --model sonnet
```

Key flags:
- `-p` / `--print` — non-interactive mode, uses OAuth session, exits when done
- `--allowedTools` — pre-approve tools to avoid permission prompts in unattended runs
- `--model sonnet` — use Sonnet for cost-effective iterations (or `opus` for complex fixes)
- `--output-format json` — get structured output for programmatic consumption

For fully unattended execution (e.g., overnight runs), add `--dangerously-skip-permissions` but only in a sandboxed environment.

#### Via Shell Wrapper Script

`scripts/test-loop.sh` wraps the full cycle so you can kick it off with one command:

```bash
#!/bin/bash
SCRIPT_NAME="${1:-ExampleScript}"
MAX_ITER="${2:-5}"

claude -p "
You are running the Microbot agentic test loop. Follow docs/AGENTIC_TESTING_LOOP.md.
Target script: $SCRIPT_NAME. Max iterations: $MAX_ITER.

For each iteration:
1. ./gradlew :client:compileJava
2. Launch client with -Dmicrobot.test.mode=true -Dmicrobot.test.script=$SCRIPT_NAME
3. Wait for ~/.runelite/test-results/result.json
4. Read result.json and any screenshots in ~/.runelite/test-results/screenshots/
5. If failures, fix the code and loop back to step 1
6. If all pass, report success and stop
" --allowedTools "Bash,Read,Edit,Write,Glob,Grep" \
  --model sonnet \
  --output-format json
```

Usage:
```bash
./scripts/test-loop.sh ExampleScript 5
./scripts/test-loop.sh MyWoodcuttingScript 3
```

#### Via Custom Slash Command

Define a `/test-loop` skill in Claude Code:

```markdown
# .claude/skills/test-loop.md
Run the agentic test loop for the given script.
Build, launch, read results, fix failures, repeat.
See docs/AGENTIC_TESTING_LOOP.md for the full protocol.
```

Then from any interactive Claude Code session: `/test-loop ExampleScript`

### Authentication

The outer loop uses Claude Code's OAuth authentication — the same session you use interactively.

| Mode | Auth | Cost |
|---|---|---|
| `claude` (interactive) | OAuth (Max subscription) | Included in subscription |
| `claude -p` (non-interactive) | OAuth (Max subscription) | Included in subscription |
| `claude --bare -p` | API key only (OAuth disabled) | Pay-per-token |

**Do not use `--bare`** — it disables OAuth and requires an API key. The standard `claude -p` mode works with OAuth and is included in the Max plan.

For long-lived headless environments (CI servers), use `claude setup-token` to create a persistent auth token that doesn't require browser-based OAuth.

---

## Implementation Status

### Done: Structured Output + Test Runner + Agent Loop

All core infrastructure is implemented and compiles:

**Testing harness** (`microbot/testing/`):
- `TestResult.java` — Result data model (checks, errors, screenshots, exit codes)
- `TestResultWriter.java` — JSON serialization to `~/.runelite/test-results/result.json`
- `TestRunnerPlugin.java` — Hidden `alwaysOn` plugin that auto-enables the target test plugin when `-Dmicrobot.test.mode=true`

**Guard Killer example** (`microbot/guardkiller/`):
- `GuardKillerScript.java` — Combat loop: attack guards, loot bones, eat food, bury
- `GuardKillerPlugin.java` — Normal-use plugin with config + overlay
- `GuardKillerTestScript.java` — Automated verification: attacks one guard, checks combat, checks loot, writes `result.json`, calls `System.exit(code)`
- `GuardKillerTestPlugin.java` — Test plugin (target for TestRunner)

**Gradle task** (`runelite-client/build.gradle.kts`):
- `runTest` — Launches client with test mode system properties forwarded

**Agent loop** (`scripts/test-loop.sh`):
- Fully autonomous shell wrapper that invokes `claude -p` with the complete loop prompt

### Remaining: CI/Headless Support

Enable running the full loop in CI (GitHub Actions) or on a headless server.

Additions:
- Xvfb setup in CI pipeline
- Docker image with JDK 17 + Xvfb + virtual display
- GitHub Actions workflow that runs the test loop on PR

---

## File Layout

```
microbot/
├── testing/
│   ├── TestRunnerPlugin.java      # alwaysOn, hidden — auto-enables target plugin in test mode
│   ├── TestResult.java            # Result data model (checks, errors, exit codes)
│   └── TestResultWriter.java      # JSON serialization + log tail capture
├── guardkiller/
│   ├── GuardKillerPlugin.java     # Normal-use plugin (combat loop)
│   ├── GuardKillerScript.java     # Main script: attack guards, loot bones, eat, bury
│   ├── GuardKillerConfig.java     # Config: eat %, loot toggle, bury toggle, radius
│   ├── GuardKillerOverlay.java    # HUD: kills, bones looted, status
│   ├── GuardKillerTestPlugin.java # Test plugin (target for TestRunner)
│   └── GuardKillerTestScript.java # Automated test: attack, verify combat, verify loot
scripts/
├── test-loop.sh                   # Fully autonomous agent loop wrapper
docs/
└── AGENTIC_TESTING_LOOP.md        # This document

runelite-client/build.gradle.kts   # Contains runTest task
```

---

## Design Decisions

### Filesystem for communication (not sockets/RPC)

The agent reads files; the harness writes files. This is the simplest approach that works reliably across local dev, CI, and headless environments. No port management, no protocol negotiation, no connection state.

### System properties for activation (not config files)

Test mode is transient — it should never accidentally persist. System properties are set per-launch and disappear when the JVM exits. This prevents the test harness from activating during normal use.

### Exit codes for fast triage

The agent's first signal is the process exit code. `0` means done. Non-zero means read the result file. This lets the outer loop short-circuit without parsing JSON on success.

### Screenshots are first-class output

Claude is multimodal. A screenshot of the game client showing a stuck character, a closed bank interface, or an NPC dialogue is worth more than 50 log lines. The harness captures screenshots at key moments so the agent has visual context alongside structured data.

### ExampleScript as the reference test

`ExampleScript` is no longer bundled with the client; it now comes from the Microbot-Hub example plugin (same package), which is an empty loop template. It is still a convenient first target for exercising the harness. Scripts that need real coverage should follow a `check()` pattern and write `TestResult` output.

---

## Failure Taxonomy

The agent should classify failures to choose the right fix strategy:

| Category | Signal | Agent Action |
|---|---|---|
| **Compilation error** | `./gradlew` exits non-zero | Read compiler output, fix syntax/type errors |
| **Login failure** | Exit code 4, result `exit_reason=login_failure` | Check credentials, world selection, ban status |
| **Script assertion failure** | Exit code 1, specific check failed | Read error + screenshot, fix script logic |
| **Script timeout/hang** | Exit code 2, no completion | Read last screenshot + logs, find infinite loop or blocking call |
| **Client crash** | Exit code 3, stack trace in errors | Read stack trace, fix null pointer / threading issue |
| **Environment issue** | Display errors, missing deps | Fix Xvfb setup, install dependencies |

---

## Constraints & Limitations

- **Game server dependency**: Tests run against live OSRS servers. Network issues, server updates, or maintenance can cause false failures.
- **Non-deterministic game state**: NPC positions, other players, random events — the game world is not controlled. Test assertions must be tolerant of variance.
- **Account risk**: Automated login/play may trigger Jagex's anti-cheat. Use dedicated test accounts on low-risk activities.
- **Display requirement**: The OSRS client requires a graphical display. Headless environments need Xvfb or equivalent.
- **Single-account concurrency**: One client instance per account. Parallel testing requires multiple accounts.
