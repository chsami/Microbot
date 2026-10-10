# Entity Guides

Per-entity gotchas, footguns, and patterns for LLMs (and humans) writing or modifying Microbot code.

Each guide lists known pitfalls when working with one specific game entity type. **Read the relevant guide before implementing or modifying any code that interacts with that entity** — these documents capture lessons learned from real bugs that escaped review because the assumption was invisible at the call site.

## Guides

| Entity | File | When to read |
|--------|------|--------------|
| Items (inventory, bank, ground, equipment, shops) | [items.md](items.md) | Any code calling `Rs2Inventory`, `Rs2Bank`, `Rs2Equipment`, `Rs2GroundItem`, `Rs2Shop`, or `Rs2DepositBox` interaction helpers, or any helper that takes a list of item names and applies a single action to all of them |
| Movement (walker, minimap, pathing) | [movement.md](movement.md) | Any code calling or modifying `Rs2Walker`, `Rs2MiniMap`, shortest-path marker handling, or minimap/canvas walk-click logic |
| Death (graves, Death's Office, recovery) | [death.md](death.md) | Any code calling or modifying `Rs2Death`, `DeathRecoveryEvent`, `DeathEvent`, or handling graves, retrieval fees, and post-death item recovery |
| Worlds (selection, ping, hopping) | [worlds.md](worlds.md) | Any code measuring world latency, testing world reachability, selecting worlds based on network availability, or reading the local player while logging in or hopping |

Ground-item pickup: [propagate dispatch failures](items.md#11-propagate-ground-item-dispatch-failures) and [preserve explicit Take](items.md#12-preserve-an-explicit-ground-item-take-when-a-widget-is-selected).

Run-orb energy, geometry, and pending-click semantics: [movement gotcha 21](movement.md#21-apply-the-shared-energy-policy-before-clicking-the-run-orb).

Scene walk-click render validity and destination confirmation: [movement gotcha 22](movement.md#22-a-scene-walk-click-is-only-valid-on-a-tile-rendered-at-click-time).

Route camera turns only for unreachable targets: [movement gotcha 23](movement.md#23-route-camera-turns-are-for-targets-the-scene-cannot-reach).

## Format

Each entity guide is a numbered list of gotchas. Each entry follows this structure:

```
## N. <Short rule, imperative voice>

<One-paragraph explanation of the rule.>

**Why this matters:** <The concrete failure mode — ideally a real bug or near-miss — that motivated the rule.>

**Pattern to follow:**

<Code snippet showing the wrong way and the right way.>

**Where this applies:** <List of utility classes / call sites this rule covers.>

**Defensive check:** <Optional — assertion / unit-test pattern that catches violations at PR time, not in production.>
```

## Adding a new guide

1. Create `<entity>.md` in this directory using the format above.
2. Add the guide to the table at the top of this file.
3. Cross-reference it from `AGENTS.md` (top-level) and `runelite-client/src/main/java/net/runelite/client/plugins/microbot/CLAUDE.md` so future agent invocations discover it via the entry-point docs.

## Adding a new gotcha to an existing guide

Append a new numbered entry. Do **not** delete or rewrite existing entries unless they are factually wrong — each entry corresponds to a real failure mode and removing it loses the institutional memory of why the rule exists.

Required-supply stops must suppress AutoLogin without blocking the next run: [items gotcha 15](items.md#15-suppress-autologin-only-for-confirmed-terminal-supply-stops).
