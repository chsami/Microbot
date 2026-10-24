# Agent Script Development Tools

A single-page reference for every tool available when **creating or debugging** a Microbot script. Aimed at LLM agents and developers working in a terminal.

> **This is a task-oriented index.** It tells you *which* tool to reach for and *when*. For full parameter details see [MICROBOT_CLI.md](MICROBOT_CLI.md) (CLI) and [AGENT_SERVER.md](AGENT_SERVER.md) (HTTP API).

---

## Prerequisites & Runtime Modes

| Mode | What's running | What works |
|------|---------------|------------|
| **Offline** | Nothing — just the repo | Compile, unit tests, client-thread lookup, doc browsing |
| **Logged out** | Client open, not logged in | All offline tools + `profile`, `login`, `state`, `widgets`, `screenshot` |
| **Logged in** | Client + game session | Everything |

The **Agent Server** plugin (port 8081, localhost) must be enabled for any CLI/HTTP command. It is off by default: enable **Agent Server** in the Microbot plugin list.

```bash
# Quick connectivity check
./microbot-cli state
```

---

## Tools at a Glance

### Offline (no game required)

| Tool | Purpose |
|------|---------|
| `./gradlew :client:compileJava` | Fast compile check (~15 s) |
| `./gradlew :client:runUnitTests` | CI-safe tests incl. client-thread guardrail |
| `./microbot-cli ct <method>` | Thread-safety lookup for any RuneLite API method |
| `./gradlew :client:runClientThreadScanner` | Regenerate thread-safety manifest + TSV |
| `./gradlew :client:regenerateClientThreadGuardrailBaseline` | Update guardrail allow-list after intentional refactors |

### Session & Configuration

| CLI Command | Purpose |
|-------------|---------|
| `profile` | List all profiles (shows which is active) |
| `profile switch <name>` | Switch active profile — used by the next `login now` |
| `login` | Login status (game state, active profile) |
| `login now [--world N] [--timeout S]` | Login with active profile (blocks until done) |
| `scripts start --class <cls>` | Start a plugin by fully qualified class |
| `scripts start --name <name>` | Start a plugin by display name |
| `scripts stop --class <cls>` | Stop a running plugin |
| `plugin-config get <group> <key>` | Read any `@ConfigGroup` value |
| `plugin-config set <group> <key> <val>` | Write any `@ConfigGroup` value |

### Observe (read-only game queries)

| CLI Command | Purpose |
|-------------|---------|
| `state` | Game state, player position, health, animation, combat level |
| `observe [--chat-since N] [--nearby D]` | One snapshot: state, inventory, equipment, dialogue, recent game chat, script health |
| `skills [--name X]` | All skill levels, boosted levels, XP |
| `varbit <id>` | Read a varbit value (critical for verifying settings) |
| `inventory` | Inventory items with id, name, quantity, slot, actions |
| `npcs [--name X] [--distance N]` | Nearby NPCs |
| `objects [--name X] [--distance N]` | Nearby game objects |
| `ground-items [--name X] [--distance N]` | Nearby ground items |
| `bank` | Bank status (open/closed) + item list when open |
| `dialogue` | Current dialogue state |
| `widgets list` | All visible widget groups |
| `widgets search <keywords>` | Search widgets by text (ranked results) |
| `widgets describe <group> <child> [--depth N]` | Widget tree inspection |
| `screenshot` | Get screenshot as PNG binary |
| `screenshot save [--label X]` | Save screenshot to disk (returns file path) |
| `scripts` | List all plugins with on/off status |
| `scripts status --class <cls>` | Script runtime state |
| `scripts health [--class <cls>]` | Heartbeat / stall detection |
| `scripts results --class <cls>` | Retrieve test results JSON |

### Act (mutate game state)

| CLI Command | Purpose |
|-------------|---------|
| `inventory interact <name> <action>` | Use/eat/equip an item |
| `inventory drop <name> [--all]` | Drop item(s) |
| `npcs interact <name> <action>` | Interact with nearest NPC |
| `objects interact <name> <action>` | Interact with nearest object |
| `ground-items pickup <name>` | Pick up nearest ground item |
| `walk <x> <y> [plane] [--wait] [--timeout S]` | Walk to coordinates (non-blocking by default; `--wait` blocks) |
| `bank open` | Open nearest bank |
| `bank close` | Close bank interface |
| `bank deposit <name> [--all]` | Deposit items |
| `bank deposit-all` | Deposit entire inventory |
| `bank withdraw <name> <qty>` | Withdraw items |
| `dialogue continue` | Click continue in dialogue |
| `dialogue select <option>` | Select a dialogue option by text |
| `widgets click <group> <child>` | Click a widget by group/child ID |
| `widgets click --text "label"` | Click a widget by its text content |
| `widgets invoke <group> <child> --param0 N [--action X]` | Invoke a widget action (dropdowns, selects) |
| `keyboard type <text>` | Type text into the game |
| `keyboard enter` | Press Enter |
| `keyboard escape` | Press Escape |
| `keyboard backspace` | Press Backspace |
| `settings search <term>` | Open in-game settings and search |
| `scripts results post --class <cls> --data <json>` | Submit test results |

### Debug (script introspection)

| Tool | Purpose |
|------|---------|
| `scripts health` | Detect stalled scripts (heartbeat age) |
| `path <x> <y> [plane] [--full]` | Plan a walker route without moving: termination reason, transports, cost |
| `path active` | Route the walker is following right now |
| `GET /debug/snapshot` | State machine snapshot — current state, transitions, guard results, trace buffer |
| `screenshot save --label debug` | Visual state capture for multimodal analysis |

---

## Offline Tools (detail)

### Compile Check

```bash
./gradlew :client:compileJava
```

Fastest validation (~15 s). Run after every code change before testing in-game.

### Unit Tests

```bash
./gradlew :client:runUnitTests
```

Includes `ClientThreadGuardrailTest` — fails if an `Rs2*` utility or query API class calls a client-thread-only RuneLite method outside `clientThread.invoke*()`.

### Client-Thread Lookup

```bash
# Exact method name
./microbot-cli ct Player.getName

# Substring match
./microbot-cli ct getItem --like

# Filter by evidence kind
./microbot-cli ct getItems --evidence ASSERT

# JSON output for programmatic use
./microbot-cli ct getItems --json
```

**Verdicts:** `REQUIRED (asserted)` · `Strongly indicated (N signals)` · `Likely (single signal)`. Absence from the manifest does **not** prove a method is safe — it means no evidence was found by the scanner.

Companion docs: `docs/client-thread-manifest.md` (human-readable), `docs/client-thread-lookup.tsv` (machine-readable).

### Reference Documentation

Before writing or modifying any script, check the relevant docs:

| What you're doing | Read first |
|-------------------|-----------|
| Any script work | `runelite-client/.../microbot/CLAUDE.md` — threading, Rs2* utilities, cache, lifecycle |
| State machine scripts | `runelite-client/.../statemachine/CLAUDE.md` — when/how to use, best practices |
| Item interactions | `docs/entity-guides/items.md` — known gotchas for Rs2Inventory, Rs2Bank, etc. |
| Cache/queryable usage | `runelite-client/.../api/QUERYABLE_API.md` — singleton pattern, fluent queries |
| Specific Rs2* utility | `docs/api/Rs2<Name>.md` (35+ utility docs) |

---

## Session & Configuration (detail)

### Profile Management

```bash
# List all profiles (shows active indicator)
./microbot-cli profile

# Switch to a different profile
./microbot-cli profile switch "my-alt"

# Then login with that profile
./microbot-cli login now --world 381
```

Profiles are stored in `~/.runelite/microbot-profiles/profiles.json`. The switch is **synchronous** — the profile is active immediately, safe to call `login now` right after.

### Login Control

```bash
# Check login status
./microbot-cli login

# Login and block until complete (default 60s timeout)
./microbot-cli login now --world 381 --timeout 60
```

The login endpoint auto-detects errors: non-member on members world, bans, auth failures. It auto-dismisses error dialogs on retry — no manual intervention needed.

### Script Lifecycle

```bash
# List all plugins
./microbot-cli scripts

# Start by fully qualified class name
./microbot-cli scripts start --class "net.runelite.client.plugins.microbot.fishing.FishingPlugin"

# Start by display name
./microbot-cli scripts start --name "Auto Fishing"

# Stop a plugin
./microbot-cli scripts stop --class "net.runelite.client.plugins.microbot.fishing.FishingPlugin"
```

### Plugin Configuration

Read or write any `@ConfigGroup` key without restarting:

```bash
# Read
./microbot-cli plugin-config get "micro-fishing" "fishingSpot"

# Write
./microbot-cli plugin-config set "micro-fishing" "fishingSpot" "SHRIMP"
```

---

## Debugging Workflows

Step-by-step playbooks for common debugging scenarios. Each shows the commands to run and what to look for.

### "My script isn't doing anything"

```bash
# 1. Is the plugin running?
./microbot-cli scripts

# 2. Check runtime status
./microbot-cli scripts status --class "com.example.MyPlugin"

# 3. Is the loop stalled? (check heartbeat age)
./microbot-cli scripts health --class "com.example.MyPlugin"
# → A plugin class resolves to the Script classes in its package; several scripts come back as a "scripts" list
# → If stalledMs is very old, the script loop is stuck or crashed

# 4. Take a screenshot to see visual state
./microbot-cli screenshot save --label "debug-stuck"

# 5. For state machine scripts — get the full snapshot
curl -s http://127.0.0.1:8081/debug/snapshot | python3 -m json.tool
# → Shows current state, all transitions with guard results, recent trace
```

### "My script isn't transitioning states"

For `StateMachineScript`-based scripts:

```bash
# Get the state machine snapshot
curl -s http://127.0.0.1:8081/debug/snapshot

# Look at:
# - "currentState": which state is it stuck in?
# - "transitions": are all guards evaluating to false?
# - "traceBuffer": did a transition fire but error?

# Check the guard conditions manually:
./microbot-cli inventory          # Is the inventory state what the guard expects?
./microbot-cli state              # Player position, health, animation
./microbot-cli skills --name Mining   # Skill levels
```

**Common causes:**
- Guard depends on game state that hasn't loaded yet → add a `Microbot.isLoggedIn()` check
- Guard reads a varbit that's 0 on login → use `sleepUntil` before evaluating
- All transitions have `() -> true` guards → use `actionDone` flag pattern (see statemachine CLAUDE.md, best practice #7)

### "Widget ID changed after a game update"

Don't hardcode widget IDs across updates. Use the search → describe → click workflow:

```bash
# 1. Search by keywords
./microbot-cli widgets search "deposit,bank"

# 2. Drill into the match
./microbot-cli widgets describe 12 0 --depth 3

# 3. Click the target
./microbot-cli widgets click 12 42

# Or click by text (more resilient)
./microbot-cli widgets click --text "Deposit inventory"
```

### "Bank/inventory interaction fails silently"

```bash
# Check current inventory
./microbot-cli inventory

# Check if bank is open
./microbot-cli bank

# Read entity gotchas FIRST
# → docs/entity-guides/items.md
# Common issue: item name mismatch (noted vs unnoted, charges in name)
```

**Key gotcha:** Item names can include charges (e.g., "Ring of dueling(7)") or noted/unnoted variants. Always check exact item names with `inventory` or `bank` first.

### "Change an in-game setting safely"

Never navigate settings tabs by index — they shift on game updates. Use the search bar:

```bash
# 1. Open Settings tab
./microbot-cli widgets click 548 52
sleep 1

# 2. Click "All Settings"
./microbot-cli widgets click --text "All Settings"
sleep 1

# 3. Click Search bar
./microbot-cli widgets click 134 11
sleep 1

# 4. Type the setting name
./microbot-cli keyboard type "level-up"
sleep 1

# 5. Click the setting option
./microbot-cli widgets click --text "Show level only"

# 6. VERIFY via varbit (critical!)
./microbot-cli varbit 13994
```

### "Thread safety violation in CI"

```bash
# 1. Look up the method in question
./microbot-cli ct Player.getName

# 2. If verdict is "REQUIRED" — must use clientThread
# Wrap the call:
#   clientThread.runOnClientThread(() -> player.getName())

# 3. If it's a pre-existing violation you're intentionally keeping:
./gradlew :client:regenerateClientThreadGuardrailBaseline
# Then commit the updated baseline file
```

### "Automated test loop — read results"

```bash
# Get test results from a running or completed test
./microbot-cli scripts results --class "com.example.MyPlugin"

# Check for result files on disk
ls ~/.runelite/test-results/
cat ~/.runelite/test-results/result.json

# Check screenshots from the test
ls ~/.runelite/test-results/screenshots/
```

---

## State Machine Debugging

The `StateMachineDebugHandler` provides rich introspection for scripts extending `StateMachineScript<S>`.

### Getting a Snapshot

```bash
curl -s http://127.0.0.1:8081/debug/snapshot | python3 -m json.tool
```

Response structure:

```json
{
  "scriptName": "MyScript",
  "currentState": "WALK_TO_TARGET",
  "tickCount": 42,
  "lastTransitionAt": "2026-04-16T17:00:00Z",
  "transitions": [
    {
      "from": "WALK_TO_TARGET",
      "to": "INTERACT",
      "reason": "Arrived at target",
      "guardResult": false
    }
  ],
  "traceBuffer": [
    {
      "tick": 40,
      "from": "FIND_TARGET",
      "to": "WALK_TO_TARGET",
      "reason": "Target found",
      "timestamp": "2026-04-16T16:59:58Z"
    }
  ]
}
```

### Reading the Snapshot

| Field | What it tells you |
|-------|-------------------|
| `currentState` | Where the script is now |
| `transitions[].guardResult` | Why no transition fired (all false = stuck) |
| `traceBuffer` | Recent transition history — did it fire and revert? Loop between two states? |
| `lastTransitionAt` | How long since the last transition (stale = stuck) |
| `tickCount` | Total ticks — low count after a long time means the loop is slow or blocked |

### Force a State (recovery)

If a script is stuck, you can force it to a specific state via the `forceState()` method. This is available programmatically but not yet exposed via CLI — use the HTTP endpoint or debugger.

---

## Agentic Testing Loop

For fully autonomous test-fix-rebuild cycles, see [AGENTIC_TESTING_LOOP.md](AGENTIC_TESTING_LOOP.md).

**Quick summary:**

| Layer | What it does |
|-------|-------------|
| **Inner harness** (JVM) | Runs inside Microbot — auto-login, start script, capture results + screenshots |
| **Outer loop** (agent) | Reads results, analyzes failures, edits code, rebuilds, re-launches |

Activate test mode:

```bash
java -jar microbot.jar \
  -Dmicrobot.test.mode=true \
  -Dmicrobot.test.script=MyPlugin
```

| Exit code | Meaning |
|-----------|---------|
| 0 | Pass |
| 1 | Fail |
| 2 | Timeout |
| 3 | Crash |
| 4 | Login failure |

Results: `~/.runelite/test-results/result.json` + `screenshots/`

---

## Documentation Index

All docs an agent should know about, organized by topic.

### Script Authoring
| Document | What's in it |
|----------|-------------|
| `runelite-client/.../microbot/CLAUDE.md` | **The canonical guide** — threading model, Rs2* utilities, cache system, plugin-script lifecycle (2300+ lines) |
| `runelite-client/.../statemachine/CLAUDE.md` | State machine framework — when to use, quick start, best practices |
| `runelite-client/.../statemachine/AGENTS.md` | Architecture and design decisions for the state machine |
| `runelite-client/.../api/QUERYABLE_API.md` | Singleton cache pattern, fluent query builders |

### API Reference
| Document | What's in it |
|----------|-------------|
| `docs/api/Rs2*.md` (35+ files) | One doc per utility — Rs2Inventory, Rs2Bank, Rs2Npc, Rs2Walker, etc. |
| `docs/entity-guides/items.md` | Gotchas for item-related utilities (name mismatches, noted items, etc.) |
| `docs/entity-guides/README.md` | Index of all entity gotcha guides |

### CLI & HTTP API
| Document | What's in it |
|----------|-------------|
| [MICROBOT_CLI.md](MICROBOT_CLI.md) | Full CLI command reference with examples |
| [AGENT_SERVER.md](AGENT_SERVER.md) | HTTP API — every endpoint, request/response schemas, error codes |

### Architecture
| Document | What's in it |
|----------|-------------|
| `docs/ARCHITECTURE.md` | System architecture overview |
| `docs/decisions/adr-*.md` | Architecture Decision Records (composite builds, queryable cache, shaded packaging) |
| `docs/client-thread-manifest.md` | Human-readable thread safety verdicts for 1000+ RuneLite APIs |
| `docs/client-thread-lookup.tsv` | Machine-readable companion (used by `./microbot-cli ct`) |

### Testing
| Document | What's in it |
|----------|-------------|
| [AGENTIC_TESTING_LOOP.md](AGENTIC_TESTING_LOOP.md) | Autonomous 2-layer test harness — inner JVM harness + outer agent loop |
