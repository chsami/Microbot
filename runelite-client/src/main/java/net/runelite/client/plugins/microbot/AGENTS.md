# Microbot Plugin & Script Development

Automation plugins live here. Every plugin = **Plugin class** (`@PluginDescriptor`, RuneLite lifecycle) + **Script class** (runs the loop on a background thread) + **Config** + optional **Overlay**.

Config UI uses the custom `MicrobotConfigPanel` (`plugins/microbot/ui`), not RuneLite's default.

## Threading — hard rules
- Game state reads/writes must happen on the client thread: wrap with `Microbot.getClientThread().runOnClientThreadOptional(...)` or `.invoke(...)`.
- Script loops run on a background executor; **never** block the client thread.
- Waits = `sleepUntil(condition, timeoutMs)`. No fixed `sleep(ms)` to wait on game state.
- Widget methods like `isHidden()`, `getBounds()`, menu entry access → client thread only.

## Cache / Queryable API (mandatory)
- Use singletons via `Microbot.getRs2XxxCache()` (NPCs, objects, ground items, inventory, bank, varbits, players, widgets, …).
- Query with `.query().<filters>.first() / .all() / .stream()` — not direct cache instantiation, not raw client iteration.
- Cache lists snapshot membership; their entity wrappers remain live. Re-query before acting on stale refs.
- Full reference: `runelite-client/.../microbot/api/QUERYABLE_API.md`.

## Interaction utilities
`Rs2Npc`, `Rs2GameObject`, `Rs2GroundItem`, `Rs2Inventory`, `Rs2Bank`, `Rs2Walker`, `Rs2Widget`, `Rs2Dialogue`, `Rs2Player`, `Rs2Combat`, etc. Prefer these over raw RuneLite APIs — they handle client-thread hopping, retries, and menu-entry correctness.

Check `docs/entity-guides/README.md` before modifying anything under `util/` — each entity type has documented footguns.

## Waits and multi-action helpers
- **A wait that returned is not a wait that succeeded.** Every `sleepUntil*` ends early on a timeout, an interrupt, or a human taking over. Discarding the result means acting as though the previous game action landed when nothing observed that.
- **Never report success you did not observe.** A helper returning `true` unconditionally after a wait leaves its caller no way to find out.
- **A helper spanning more than one game action exposes a step.** The game is a remote asynchronous server, so no sequence of actions can be made atomic; the only option is re-deriving from observed state, which the 600ms `Script.run()` tick already does. A helper blocking for seconds is reimplementing that loop inside one iteration of it.
- Copy `Rs2Walker`: `walkStep` issues one click and returns a `WalkerState`, with the blocking `walkTo` built on top. The enum matters as much as the split, since `if (walkStep(...))` does not compile while `if (someBooleanHelper(...))` silently discards the answer.

## Scripts
- Extend `Script` (or `StateMachineScript<S>` for 3+ phase scripts — see `statemachine/AGENTS.md`).
- Implement `run(TConfig)`; return `true` to signal successful start.
- Always `shutdown()` cleanly: cancel schedulers, clear state, unregister listeners.
- Use `Microbot.status` for user-visible status; keep logging sparse and at appropriate levels.

## Plugins
- `@PluginDescriptor(name, description, tags, enabledByDefault = false, ...)`.
- Inject dependencies via Guice constructor injection.
- `startUp()` / `shutDown()` must be idempotent and fast — do not launch scripts synchronously there.
- Communicate between plugins via the event bus, not static state.

## Anti-ban & timing
- Use randomized sleeps from `Rs2Random` / `Global.sleep*Gaussian*`.
- Don't hammer menu actions; honor tick timing (`Rs2Player.waitForAnimation`, `sleepUntilOnClientThread`).
- Respect blocking events (`BlockingEventManager`) — login, level-up, death, random events pause the script automatically when handlers are registered.

## Error handling
- Wrap script bodies in try/catch; log and stop cleanly rather than propagating into the client thread.
- Never swallow `InterruptedException` — restore the flag and exit the loop.

## Memory / performance
- Don't cache entity refs across ticks; cache by id/name or re-query.
- Unregister event subscribers in `shutDown()`.
- Avoid heavy work inside overlays (`render`) and events fired on the client thread.

## Commits / versioning
Follow existing conventional style. Bump the plugin version in the relevant `@PluginDescriptor` when behavior changes.

## See also
- Repo-root `AGENTS.md` — non-negotiable rules, build/test, CLI.
- `statemachine/AGENTS.md` — state machine framework.
- `util/settings/AGENTS.md` — widget/settings debugging.
- `docs/AGENT_SCRIPT_TOOLS.md` — runtime tooling for authoring/debugging.
- `docs/entity-guides/` — per-entity footguns.
