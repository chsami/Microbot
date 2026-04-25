# Rs2HuntKit Class Documentation

## [Back](development.md)

## Overview

`Rs2HuntKit` automates the **Huntsman's kit** interface: a **bank-like** withdraw flow (`CC_OP` on the kit item grid) and **deposit** actions on the side inventory while the kit UI is open. It keeps an in-memory **cache** of kit contents for `InventoryID.HUNTSMANS_KIT` (855), updated from `ItemContainerChanged` the same way as bank/inventory helpers.

**Source:** `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/huntkit/Rs2HuntKit.java`

## Event cache and MicrobotPlugin

When the **Microbot** parent plugin is enabled, `MicrobotPlugin.onItemContainerChanged` forwards kit container events to `Rs2HuntKit.updateLocalKit(event)`. External (e.g. Hub) plugins **must not** also `@Subscribe` to the same container for the same cache update — duplicate work and racey double rebuilds. Call `updateLocalKit` yourself only if you run **outside** that forwarding path.

Off the client thread, updates schedule `rebuildKitFromCurrentClient` via `invokeLater` (event payload is not reused across ticks).

## UI and inventory helpers

- **`isOpen()`** — kit interface visible.
- **`openView()`** — use kit in inventory with "View"; waits until open.
- **`close()` / `closeView()`** — closes the kit UI (primary path uses the kit frame widget action `Close`).
- **`fill()`** — inventory interaction "Fill" (kit closed).
- **`fillWidget()`** — kit open: Fill chrome (menu op or widget click).
- **`emptyKit()`** — inventory "Empty" when kit UI is **closed** (in-game rule).
- **`emptyWidget()`** — kit UI **open**: Empty via Fill submenu / Empty widget.

### Menu op indexing (important)

Like `Rs2Bank`, the kit UI uses `MenuAction.CC_OP` on widgets. **The `identifier` is 1-based** (even though `Widget.getActions()` is a 0-based Java array). If you are building low-level calls yourself, use \(i + 1\) for the action index.

### Dev shell / client thread note

Many Microbot “Rs2*” utilities are designed to be called from script/executor threads (not the RuneLite client thread). If you drive them from a `GameTick` subscription in the dev shell, avoid blocking waits and expect some helpers (like `openView()` and `withdrawX()` amount entry) to behave differently than in real scripts.

## Reading the cache

- **`stream()`**, **`kitItems()`** — cached `Rs2ItemModel` list; if empty while logged in, a client-thread refresh may be **scheduled** (same eventual-consistency idea as `Rs2Inventory.items()`).
- **`findKitItem`**, **`hasKitItem`**, **`count`**, **`contains`**, **`containsAll`**, **`get(Predicate)`** — query helpers; null-safe predicates/ids where implemented.
- **Name matching:** `contains(String...)` is shorthand for **exact whole-name** match (case-insensitive) per name. For **substring** matching use `contains(false, "needle", ...)`.

## Withdraw operations (kit must be open)

Patterns mirror Rs2Bank naming where possible: `withdrawOne`, `withdrawAll`, `withdrawAllButOne`, `withdrawFive` (supports id + string overloads), `withdrawX`, `withdrawDeficit`, `withdrawItem` overloads, `withdrawAllStackables(maxOps)` (bounded inner passes; see Javadoc).

Inventory space checks apply (`Rs2Inventory.isFull()`, etc.).

## Deposit operations (kit must be open)

Uses **`KIT_SIDE_INVENTORY_PACKED_ID`** (side panel items) and resolves **Deposit-1 / -5 / -X / -All** op indices from slot widgets. Public helpers: `depositOne`, `depositFive`, `depositX`, `depositAll`, `depositAll(Predicate)` with bounded passes, `depositAllExcept` variants (ids, names, map, predicates).

## Low-level

- **`invokeMenu(int identifier, Rs2ItemModel rs2Item)`** — kit grid `MenuAction.CC_OP` with `ITEM_CONTAINER_PACKED_ID`; scrolls slot into view; slot is **clamped** for menu params and bounds.
- **Constants:** `INTERFACE_ROOT_PACKED_ID`, `ITEM_CONTAINER_PACKED_ID`, `KIT_SIDE_INVENTORY_PACKED_ID`, `KIT_ITEM_CONTAINER_ID`, `KIT_ITEM_ID`, `KIT_NAME`, `CAPACITY` (32).

## Related documentation

- Cursor skill: `.cursor/skills/microbot-api/SKILL.md` and `reference.md`
- Plugin & script rules: `runelite-client/.../microbot/AGENTS.md`
