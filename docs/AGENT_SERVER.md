# Agent Server

The Agent Server is an embedded HTTP server that exposes Microbot's widget system, game state, and game interactions to external AI agents at runtime. An agent can inspect the UI, query NPCs/objects/items, manage inventory, bank, walk, handle dialogues, and more - all through HTTP calls or the `microbot-cli` wrapper.

## Architecture

```
┌──────────────────────────────────────────────────┐
│  AI Agent (Claude Code, script, etc.)            │
│                                                  │
│  ./microbot-cli npcs --name Banker               │
│         │                                        │
│         ▼                                        │
│  curl http://127.0.0.1:8081/npcs?name=Banker     │
└────────────────────┬─────────────────────────────┘
                     │ HTTP (localhost only)
┌────────────────────▼─────────────────────────────┐
│  AgentServerPlugin                               │
│  ├─ AgentHandler (base class)                    │
│  │   ├─ JSON serialization, CORS, error handling │
│  │   ├─ Pagination, query parsing                │
│  │   └─ Request body reading with size limits    │
│  │                                               │
│  ├─ Widgets and UI interaction                   │
│  ├─ Game state, skills, varbits, varps           │
│  ├─ Inventory, bank, dialogue, walking           │
│  ├─ NPCs, objects, ground items                  │
│  ├─ Login, profiles, scripts, quest helper       │
│  ├─ Keyboard, screenshots, settings/config       │
│  └─ Dynamic script and state-machine tooling     │
└────────────────────┬─────────────────────────────┘
                     │ Client thread dispatch
┌────────────────────▼─────────────────────────────┐
│  RuneLite Client (widgets, caches, game state)   │
└──────────────────────────────────────────────────┘
```

TCP mode binds to `127.0.0.1` only. UDS mode binds to `~/.runelite/.agent.sock` and falls back to TCP if UDS startup fails. All game data access is thread-safe via `runOnClientThreadOptional` or the singleton cache queryable API.

## Server Lifecycle

- The server uses **daemon threads** so it never prevents the JVM from exiting when the client closes.
- A **JVM shutdown hook** stops the server cleanly on client exit (window close, kill signal, `System.exit`).
- On TCP startup, if the port is already in use, this client skips Agent Server startup for that instance.
- Toggling the plugin off and back on works cleanly — the old server is stopped before the new one starts.
- Optional stealth-bind mode opens the socket only while a Microbot script is active, then tears it down after an idle grace period.

## Setup

1. Build: `./gradlew :client:compileJava`
2. Launch the client
3. Enable **"Agent Server"** in the Microbot plugin list
4. Server starts on port `8081` (configurable)
5. The auth token is auto-generated and written to `~/.runelite/.agent-token`; `./microbot-cli` reads it automatically.

## Configuration

| Setting | Default | Description |
|---------|---------|-------------|
| Port | `8081` | HTTP server port |
| Max Results | `200` | Default limit for list/query endpoints |
| Auth token | generated | Required in `X-Agent-Token`; stored in `~/.runelite/.agent-token` for CLI use |
| Stealth bind | `false` | Open the socket only while scripts are active |
| Bind mode | `TCP` | `TCP` on localhost or `UDS` at `~/.runelite/.agent.sock` |

CLI environment variables:

| Variable | Default | Description |
|----------|---------|-------------|
| `MICROBOT_HOST` | `127.0.0.1` | Server host |
| `MICROBOT_PORT` | `8081` | Server port |
| `MICROBOT_TIMEOUT` | `30` | Request timeout in seconds |
| `MICROBOT_TOKEN` | unset | Override the `X-Agent-Token` value |
| `MICROBOT_TOKEN_FILE` | `~/.runelite/.agent-token` | Token file path |

## API Reference

### Widget Endpoints

#### GET /widgets/list

Returns all visible root-level widget interfaces.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `limit` | config max | Max results |
| `offset` | `0` | Skip first N results |

```bash
./microbot-cli widgets list
./microbot-cli widgets list --limit 50
```

#### GET /widgets/search

Searches visible widgets by keywords, ranked by relevance.

| Parameter | Required | Description |
|-----------|----------|-------------|
| `q` | Yes | Comma or space-separated keywords |
| `limit` | No | Max results |

```bash
./microbot-cli widgets search "notification,level-up"
```

#### GET /widgets/describe

Returns the widget tree under a specific widget.

| Parameter | Required | Default | Description |
|-----------|----------|---------|-------------|
| `groupId` | Yes | - | Widget group ID |
| `childId` | Yes | - | Widget child ID |
| `depth` | No | `5` | Max depth (capped at 15) |

```bash
./microbot-cli widgets describe 134 0 --depth 3
```

#### POST /widgets/click

Clicks a widget. Returns diagnostic info on failure.

**By ID:** `{"groupId": 134, "childId": 42}`
**By text:** `{"text": "Toggle"}`

```bash
./microbot-cli widgets click 134 42
./microbot-cli widgets click --text "Toggle"
```

Response includes `reason` field when `clicked` is `false`.

### Game State

#### GET /state

```bash
./microbot-cli state
```

```json
{
  "loggedIn": true,
  "gameState": "LOGGED_IN",
  "player": {
    "name": "PlayerName",
    "combatLevel": 80,
    "healthRatio": 255,
    "healthScale": 255,
    "animating": false,
    "animationId": -1,
    "moving": false,
    "interacting": false,
    "position": {"x": 3222, "y": 3218, "plane": 0}
  },
  "scriptsPaused": false
}
```

#### GET /skills

| Parameter | Required | Description |
|-----------|----------|-------------|
| `name` | No | Filter by skill name (e.g., "Attack") |

```bash
./microbot-cli skills
./microbot-cli skills --name Attack
```

```json
{
  "count": 23,
  "totalLevel": 1250,
  "skills": [
    {"name": "Attack", "level": 70, "boostedLevel": 75, "xp": 737627}
  ]
}
```

### Inventory

#### GET /inventory

```bash
./microbot-cli inventory
```

```json
{
  "count": 15,
  "capacity": 28,
  "freeSlots": 13,
  "full": false,
  "items": [
    {"id": 379, "name": "Lobster", "quantity": 5, "slot": 0, "stackable": false, "noted": false, "actions": ["Eat"]}
  ]
}
```

#### POST /inventory/interact

Body: `{"name": "Lobster", "action": "Eat"}` or `{"id": 379, "action": "Eat"}`

```bash
./microbot-cli inventory interact "Lobster" "Eat"
```

#### POST /inventory/drop

Body: `{"name": "Logs"}` or `{"name": "Logs", "all": true}`

```bash
./microbot-cli inventory drop "Logs"
./microbot-cli inventory drop "Logs" --all
```

### NPCs

#### GET /npcs

| Parameter | Default | Description |
|-----------|---------|-------------|
| `name` | - | Filter by NPC name |
| `maxDistance` | `20` | Max tile distance |
| `limit` | config max | Max results |

```bash
./microbot-cli npcs --name Guard --distance 15
```

```json
{
  "count": 3,
  "total": 3,
  "npcs": [
    {"id": 3010, "index": 42, "name": "Guard", "combatLevel": 21, "healthRatio": 0, "healthScale": 0, "interacting": false, "distance": 5, "position": {"x": 3215, "y": 3219, "plane": 0}}
  ]
}
```

#### POST /npcs/interact

Body: `{"name": "Banker", "action": "Bank"}` or `{"id": 3010, "action": "Attack"}`

```bash
./microbot-cli npcs interact "Banker" "Bank"
```

### Objects

#### GET /objects

| Parameter | Default | Description |
|-----------|---------|-------------|
| `name` | - | Filter by object name |
| `maxDistance` | `20` | Max tile distance |
| `limit` | config max | Max results |

```bash
./microbot-cli objects --name "Oak tree" --distance 10
```

```json
{
  "count": 2,
  "total": 2,
  "objects": [
    {"id": 10820, "name": "Oak tree", "type": "GAME", "reachable": true, "position": {"x": 3205, "y": 3210, "plane": 0}}
  ]
}
```

#### GET /objects/neighbours

Same filters as `GET /objects`, plus the walkability of the tiles surrounding each match. Use it to
catalogue obstacles that sit *between* two walkable tiles — Motherlode rockfalls, rubble, rock
slides — where modelling the obstacle as a transport in `transports.tsv` needs both of its open
ends, which the object's own position does not give you.

| Parameter | Default | Description |
|-----------|---------|-------------|
| `id` / `name` / `nameContains` | - | Same filters as `GET /objects` |
| `maxDistance` | `20` | Max tile distance (scene radius is ~104) |
| `radius` | `1` | Neighbour ring size, clamped to 1-5 |
| `limit` | config max | Max results |

`sides` pairs opposing neighbours on each axis, so a blocking obstacle's two endpoints can be read
straight off the response. Walkability comes from the **live scene** collision flags, not the
bundled collision map, so it reflects what the player can actually do right now — which is the
point when the bundled map has an obstacle baked in as a permanent wall.

```bash
curl -s -H "X-Agent-Token: $(cat ~/.runelite/.agent-token)" \
  "http://127.0.0.1:8081/objects/neighbours?id=26679&maxDistance=104&limit=200"
```

```json
{
  "count": 1, "total": 1, "radius": 1,
  "objects": [
    {
      "id": 26679, "name": "Rockfall", "type": "GAME", "reachable": true,
      "position": {"x": 3742, "y": 5668, "plane": 0},
      "selfWalkable": false,
      "neighbours": [
        {"position": {"x": 3741, "y": 5668, "plane": 0}, "dx": -1, "dy": 0, "walkable": true}
      ],
      "sides": {
        "eastWest":   {"a": {"...": "dx=-1"}, "b": {"...": "dx=+1"}, "bothWalkable": true},
        "northSouth": {"a": {"...": "dy=-1"}, "b": {"...": "dy=+1"}, "bothWalkable": false}
      }
    }
  ]
}
```

The axis whose `bothWalkable` is `true` gives the transport's origin/destination pair.

#### POST /objects/interact

Body: `{"name": "Oak tree", "action": "Chop down"}`

```bash
./microbot-cli objects interact "Oak tree" "Chop down"
```

### Ground Items

#### GET /ground-items

| Parameter | Default | Description |
|-----------|---------|-------------|
| `name` | - | Filter by item name |
| `maxDistance` | `20` | Max tile distance |
| `limit` | config max | Max results |

```bash
./microbot-cli ground-items --name "Dragon bones"
```

```json
{
  "count": 1,
  "total": 1,
  "items": [
    {"id": 536, "name": "Dragon bones", "quantity": 1, "geValue": 2500, "lootable": true, "position": {"x": 3200, "y": 3200, "plane": 0}}
  ]
}
```

#### POST /ground-items/pickup

Body: `{"name": "Dragon bones"}` or `{"id": 536}`

```bash
./microbot-cli ground-items pickup "Dragon bones"
```

### Movement

#### POST /walk

Body: `{"x": 3100, "y": 3500, "plane": 0, "wait": false, "timeout": 30}`

| Field | Default | Description |
|-------|---------|-------------|
| `x`, `y` | required | Destination world coordinates |
| `plane` | `0` | Destination plane |
| `wait` | `false` | If `true`, blocks until the walk arrives or `timeout` elapses. If `false`, returns immediately after kicking the walk off on a background thread. |
| `timeout` | `30` (max `600`) | Seconds to wait when `wait=true` |

Walking is non-blocking by default — long routes used to exceed the CLI's curl timeout and produce broken-pipe errors. Poll `/state` to check the player's position, or pass `--wait` to block.

```bash
./microbot-cli walk 3100 3500
./microbot-cli walk 3100 3500 1
./microbot-cli walk 3100 3500 --wait --timeout 120
```

Non-blocking response:

```json
{
  "destination": {"x": 3100, "y": 3500, "plane": 0},
  "success": true,
  "walking": true,
  "message": "Walk initiated",
  "playerPosition": {"x": 3200, "y": 3400, "plane": 0}
}
```

Blocking response (`wait=true`):

```json
{
  "destination": {"x": 3100, "y": 3500, "plane": 0},
  "success": true,
  "walking": false,
  "state": "ARRIVED",
  "playerPosition": {"x": 3100, "y": 3500, "plane": 0}
}
```

Blocking timeout response:

```json
{
  "destination": {"x": 3100, "y": 3500, "plane": 0},
  "success": false,
  "walking": true,
  "timedOut": true,
  "message": "Walk did not complete within 30s; still in progress",
  "playerPosition": {"x": 3150, "y": 3450, "plane": 0}
}
```

### Banking

#### GET /bank

Returns bank status. If open, includes items.

```bash
./microbot-cli bank
```

#### POST /bank/open

Opens nearest bank and waits up to 5 seconds.

```bash
./microbot-cli bank open
```

#### POST /bank/close

```bash
./microbot-cli bank close
```

#### POST /bank/deposit

Body: `{"all": true}` or `{"name": "Logs"}`

```bash
./microbot-cli bank deposit-all
./microbot-cli bank deposit "Logs"
```

#### POST /bank/withdraw

Body: `{"name": "Pure essence", "quantity": 28}`

```bash
./microbot-cli bank withdraw "Pure essence" 28
```

### Dialogue

#### GET /dialogue

```bash
./microbot-cli dialogue
```

```json
{
  "inDialogue": true,
  "hasContinue": false,
  "hasOptions": true,
  "question": "What would you like?",
  "options": ["Buy sword", "Sell items", "Goodbye"]
}
```

#### POST /dialogue/continue

```bash
./microbot-cli dialogue continue
```

#### POST /dialogue/select

Body: `{"option": "Buy sword"}` or `{"index": 1}`

```bash
./microbot-cli dialogue select "Buy sword"
```

### Login

#### GET /login

Returns current login state, active profile, world, and login error detection.

When the client is on the login screen, the response includes `loginIndex` and `loginError` (if an error is detected). This lets callers detect issues like non-member accounts on members worlds, bans, and auth failures without polling the game state manually.

```bash
./microbot-cli login
```

**Normal login screen (no error):**

```json
{
  "loggedIn": false,
  "gameState": "LOGIN_SCREEN",
  "loginAttemptActive": false,
  "loginIndex": 0,
  "activeProfile": {
    "name": "player@email.com",
    "isMember": true,
    "selectedWorld": 360
  },
  "currentWorld": 360
}
```

**Non-member on members world (loginIndex 34):**

```json
{
  "loggedIn": false,
  "gameState": "LOGIN_SCREEN",
  "loginIndex": 34,
  "loginError": "Non-member account cannot login to members world",
  "currentWorld": 360
}
```

**Logged in:**

```json
{
  "loggedIn": true,
  "gameState": "LOGGED_IN",
  "loginAttemptActive": false,
  "loginDurationMs": 120000,
  "activeProfile": {
    "name": "player@email.com",
    "isMember": true,
    "selectedWorld": 360
  },
  "currentWorld": 360
}
```

**Login error codes:**

| loginIndex | loginError | Description |
|------------|------------|-------------|
| 3 | Authentication failed - invalid credentials | Wrong username/password |
| 4 | Invalid credentials | Wrong username/password |
| 14 | Account is banned | Account has been banned |
| 24 | Disconnected from server | Connection lost |
| 34 | Non-member account cannot login to members world | F2P account on P2P world |

`loginIndex` and `loginError` are only present when `gameState` is `LOGIN_SCREEN`. When logged in, these fields are omitted.

#### POST /login

Triggers a login using the active profile. **Blocks by default** until the login succeeds or fails, returning a definitive result.

**Request body parameters:**

| Parameter | Type | Default | Description |
|-----------|------|---------|-------------|
| `world` | number | - | Target world (omit for profile default) |
| `wait` | boolean | `true` | Block until login resolves. Set `false` for fire-and-forget. |
| `timeout` | number | `30` | Max seconds to wait (max 120) |

**Examples:**

```bash
# Block until logged in (default, up to 30s)
curl -X POST -H 'Content-Type: application/json' \
  -d '{"world": 360}' http://127.0.0.1:8081/login

# Block with custom timeout
curl -X POST -H 'Content-Type: application/json' \
  -d '{"world": 360, "timeout": 60}' http://127.0.0.1:8081/login

# Fire and forget (returns immediately)
curl -X POST -H 'Content-Type: application/json' \
  -d '{"wait": false}' http://127.0.0.1:8081/login
```

```bash
./microbot-cli login now --world 360
./microbot-cli login wait --timeout 60 --world 360
```

**Success response:**

```json
{
  "success": true,
  "message": "Login successful",
  "currentWorld": 360
}
```

**Failure responses:**

```json
{
  "success": false,
  "message": "Login failed: Non-member account cannot login to members world",
  "currentWorld": 360,
  "loginIndex": 34,
  "loginError": "Non-member account cannot login to members world"
}
```

```json
{
  "success": false,
  "message": "Login timed out after 30s",
  "currentWorld": 360
}
```

| Status | Meaning |
|--------|---------|
| 200 | Login successful (or already logged in) |
| 400 | Login rejected (no profile, not on login screen) |
| 401 | Login failed (auth failure, banned, non-member, or timeout) |
| 409 | Login attempt already in progress |
| 500 | Internal error (LoginManager not available) |

The response always includes `success` (boolean) and `message` (string). On auth-related failures, `loginIndex` and `loginError` identify the specific issue so the caller can decide whether to retry with a different world or abort.

**Auto-dismiss on retry:** When a login fails (e.g., non-member on members world), the game shows an error dialog that blocks further attempts. On the next `POST /login` call, `LoginManager` automatically dismisses the error dialog before submitting the new credentials — no manual intervention needed. This means you can immediately retry with a different world:

```bash
# First attempt — fails with loginIndex 34
curl -X POST -d '{"world":461,"timeout":30}' http://127.0.0.1:8081/login
# → {"success":false,"loginIndex":34,"loginError":"Non-member account..."}

# Retry — error dialog dismissed automatically, logs into F2P world
curl -X POST -d '{"world":383,"timeout":30}' http://127.0.0.1:8081/login
# → {"success":true,"message":"Login successful","currentWorld":383}
```

### Profiles

#### GET /profiles

Lists all non-internal profiles with their active indicator, membership status, and world selection.

```bash
curl http://127.0.0.1:8081/profiles
```

```json
{
  "count": 2,
  "activeProfile": "player@email.com",
  "profiles": [
    {
      "name": "player@email.com",
      "isMember": true,
      "selectedWorld": "random-members",
      "active": true
    },
    {
      "name": "alt-account@email.com",
      "isMember": false,
      "selectedWorld": 383,
      "active": false
    }
  ]
}
```

The `selectedWorld` field shows: `"auto"` (null — use fallback logic), `"random-members"` (-1), `"random-f2p"` (-2), or a specific world number.

#### POST /profiles

Switch the active profile. The switched profile will be used by the next `POST /login`.

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `name` | string | Yes | Profile name (case-insensitive exact match) |

```bash
# Switch profile
curl -X POST -H "Content-Type: application/json" \
  -d '{"name":"alt-account@email.com"}' \
  http://127.0.0.1:8081/profiles
```

```json
{
  "success": true,
  "message": "Switched to profile 'alt-account@email.com'",
  "profile": {
    "name": "alt-account@email.com",
    "isMember": false,
    "selectedWorld": 383,
    "active": true
  }
}
```

**Error responses:**
- `400` — Missing or blank `name` field
- `404` — No profile matches the given name (response includes `available` list)
- `503` — Profile system not initialized

### Script Lifecycle

#### GET /scripts

Lists all microbot plugins with their active/enabled status.

```bash
curl http://127.0.0.1:8081/scripts
```

```json
{
  "count": 12,
  "scripts": [
    {"name": "Micro Guard Killer", "className": "net.runelite.client.plugins.microbot.guardkiller.GuardKillerPlugin", "active": false, "enabled": false},
    {"name": "Micro Example", "className": "net.runelite.client.plugins.microbot.example.ExamplePlugin", "active": true, "enabled": true}
  ]
}
```

#### POST /scripts/start

Starts a plugin by fully qualified class name or display name.

Body: `{"className": "net.runelite.client.plugins.microbot.example.ExamplePlugin"}` or `{"name": "Example"}`

```bash
curl -X POST -H 'Content-Type: application/json' \
  -d '{"className":"net.runelite.client.plugins.microbot.example.ExamplePlugin"}' \
  http://127.0.0.1:8081/scripts/start
```

```json
{
  "success": true,
  "name": "Micro Example",
  "className": "net.runelite.client.plugins.microbot.example.ExamplePlugin",
  "status": "RUNNING",
  "startedAt": "2026-04-06T12:00:00Z"
}
```

#### POST /scripts/stop

Stops a running plugin.

Body: `{"className": "..."}` or `{"name": "..."}`

```bash
curl -X POST -H 'Content-Type: application/json' \
  -d '{"className":"net.runelite.client.plugins.microbot.example.ExamplePlugin"}' \
  http://127.0.0.1:8081/scripts/stop
```

```json
{"success": true, "className": "net.runelite.client.plugins.microbot.example.ExamplePlugin", "status": "STOPPED"}
```

#### GET /scripts/status

Gets detailed status of a specific plugin, including runtime.

| Parameter | Description |
|-----------|-------------|
| `className` | Fully qualified class name |
| `name` | Display name (partial, case-insensitive) |

```bash
curl 'http://127.0.0.1:8081/scripts/status?className=net.runelite.client.plugins.microbot.example.ExamplePlugin'
```

```json
{
  "name": "Micro Example",
  "className": "net.runelite.client.plugins.microbot.example.ExamplePlugin",
  "active": true,
  "status": "RUNNING",
  "startedAt": "2026-04-06T12:00:00Z",
  "runtimeMs": 45000
}
```

#### POST /scripts/results

Submits test results from a script or test harness. Results are stored in-memory and can be retrieved via GET.

Body: `{"className": "...", "passed": true, "details": {...}}`

```bash
curl -X POST -H 'Content-Type: application/json' \
  -d '{"className":"com.hub.MyPlugin","passed":true,"kills":10}' \
  http://127.0.0.1:8081/scripts/results
```

Scripts running inside the JVM can also submit results directly via Java:

```java
import net.runelite.client.plugins.microbot.agentserver.handler.ScriptResultStore;

ScriptResultStore.submit("com.hub.MyPlugin", Map.of("passed", true, "kills", 10));
```

#### GET /scripts/results

Retrieves stored test results for a plugin.

| Parameter | Description |
|-----------|-------------|
| `className` | Fully qualified class name |
| `name` | Display name (partial, case-insensitive) |

```bash
curl 'http://127.0.0.1:8081/scripts/results?className=com.hub.MyPlugin'
```

```json
{
  "className": "com.hub.MyPlugin",
  "count": 1,
  "results": [
    {"passed": true, "kills": 10, "timestamp": "2026-04-06T12:00:45Z"}
  ]
}
```

## Handler Architecture

Adding a new endpoint requires one file:

```java
public class MyHandler extends AgentHandler {
    public MyHandler(Gson gson) { super(gson); }

    @Override
    public String getPath() { return "/my-endpoint"; }

    @Override
    protected void handleRequest(HttpExchange exchange) throws IOException {
        requireGet(exchange);  // or requirePost
        // your logic
        sendJson(exchange, 200, result);
    }
}
```

Then register in `AgentServerPlugin.startUp()`:

```java
handlers.add(new MyHandler(gson));
```

The `AgentHandler` base class provides: `sendJson()`, `parseQuery()`, `readJsonBody()`, `errorResponse()`, `paginate()`, `getIntParam()`, `getSubPath()`, CORS headers, and try/catch wrapping.

## File Reference

| File | Purpose |
|------|---------|
| `agentserver/AgentServerPlugin.java` | Plugin lifecycle, handler registration |
| `agentserver/AgentServerConfig.java` | Port and max results config |
| `agentserver/handler/AgentHandler.java` | Abstract base class for all handlers |
| `agentserver/handler/WidgetListHandler.java` | `GET /widgets/list` |
| `agentserver/handler/WidgetSearchHandler.java` | `GET /widgets/search` |
| `agentserver/handler/WidgetDescribeHandler.java` | `GET /widgets/describe` |
| `agentserver/handler/WidgetClickHandler.java` | `POST /widgets/click` |
| `agentserver/handler/StateHandler.java` | `GET /state` |
| `agentserver/handler/InventoryHandler.java` | `/inventory` endpoints |
| `agentserver/handler/NpcHandler.java` | `/npcs` endpoints |
| `agentserver/handler/ObjectHandler.java` | `/objects` endpoints |
| `agentserver/handler/WalkHandler.java` | `POST /walk` |
| `agentserver/handler/BankHandler.java` | `/bank` endpoints |
| `agentserver/handler/DialogueHandler.java` | `/dialogue` endpoints |
| `agentserver/handler/GroundItemHandler.java` | `/ground-items` endpoints |
| `agentserver/handler/SkillsHandler.java` | `GET /skills` |
| `agentserver/handler/LoginHandler.java` | `/login` endpoints |
| `agentserver/handler/ScriptHandler.java` | `/scripts` endpoints |
| `agentserver/handler/ScriptSession.java` | Script execution session tracking |
| `agentserver/handler/ScriptResultStore.java` | Static result store (Java API + HTTP) |
| `util/widget/Rs2WidgetInspector.java` | Widget tree inspection logic |
| `microbot-cli` (repo root) | Bash CLI wrapper |

## Error Handling

All errors return JSON:

```json
{"error": "Missing required parameter: q"}
```

| HTTP Status | Meaning |
|-------------|---------|
| 200 | Success |
| 400 | Bad request (missing/invalid parameters) |
| 404 | Unknown sub-path |
| 405 | Wrong HTTP method |
| 500 | Internal server error |

Widget click failures include a `reason` field:
```json
{"clicked": false, "reason": "Widget not found or not visible", "groupId": 134, "childId": 42}
```

NPC/object/item interaction failures include a `reason` field:
```json
{"success": false, "reason": "NPC not found"}
```

## Security

The server binds exclusively to `127.0.0.1` (loopback). It cannot be reached from other machines.
