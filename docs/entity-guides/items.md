# Items — Entity Guide

Gotchas when interacting with items in inventory, bank, ground, equipment, shops, or deposit box.

Covers utilities under:
- `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/inventory/`
- `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/bank/`
- `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/equipment/`
- `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/grounditem/`
- `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/shop/`
- `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/depositbox/`

---

## 1. Always verify the menu action exists on the actual item before interacting

When implementing any code that interacts with an item via a hardcoded action string (`"Eat"`, `"Drink"`, `"Release"`, `"Use"`, `"Wield"`, `"Wear"`, `"Equip"`, etc.), **do not assume the action is universally present** across every item in a category. Read the item's actual menu options from `Rs2ItemModel.getInventoryActions()` and pick the first matching one.

**Why this matters:** Items that produce the same effect can use different menu verbs, and grouping them under one helper that hardcodes a single action will silently fail for the odd one out.

Real bug (fixed in `Rs2Player.java:1384`): `Rs2Potion.getPrayerPotionsVariants()` listed three items that all restore prayer:

| Item | Menu action |
|---|---|
| `Moonlight potion` | `Drink` |
| `Moonlight moth mix` | `Drink` |
| `Moonlight moth` (the bug itself) | `Release` |

`Rs2Player.usePotion(String...)` looked up the item by name and then unconditionally called `Rs2Inventory.interact(potion, "drink")`. When the player held a Moonlight moth, the helper found it, hovered it, and then dispatched a menu action that didn't exist on that item — so nothing happened. The script just stood there. The bug was invisible at the call site because `usePotion` *looks* like it can only receive potions; the type system doesn't enforce it.

**Pattern to follow:**

```java
// ❌ BAD - hardcoded action; silently fails for any item that doesn't expose it
return Rs2Inventory.interact(item, "drink");

// ✅ GOOD - derive the action from the item itself, with a prioritised fallback list
String action = Arrays.stream(item.getInventoryActions())
        .filter(Objects::nonNull)
        .filter(a -> a.equalsIgnoreCase("drink") || a.equalsIgnoreCase("release"))
        .findFirst()
        .orElse(null);
if (action == null) return false;
return Rs2Inventory.interact(item, action);
```

When you know the *intent* (consume, equip, drop) but not the verb the game uses, encode that intent as a small ordered list of acceptable verbs and pick the first one the item exposes. Never hardcode a single verb against a name list you didn't fully audit.

**Where this applies:**
- `Rs2Inventory.interact(item, action)` and all overloads
- `Rs2Bank.depositOne` / `withdrawX` action arguments
- `Rs2Equipment.interact()` / `Rs2Equipment.equip()`
- `Rs2GroundItem.interact()` / `Rs2GroundItem.loot()`
- `Rs2Shop.buyItem` action dispatch
- Any helper that accepts a list of item names (`Rs2Potion.getXxxVariants()`, `Rs2Food`, food/potion arrays) and applies a single action to whichever one is found

**Defensive check:** Before merging code that uses a hardcoded action against a curated list of item names, add a unit test that walks every name in the list and asserts the corresponding `ItemComposition` exposes the expected action. The test fails the moment a non-conforming item is added to the list — at PR time, not in production. The same pattern applies to any future `Rs2*.getXxxVariants()` list, food enum, or equipment set.

---

## 2. After opening the bank, wait for a live `ItemContainerChanged(BANK)` before trusting `bankItems()` / `hasBankItem`

`Rs2Bank` mirrors the bank into `Rs2BankData` from `ItemContainerChanged` on the client thread. The interface can report open before the first container event is processed, so a script that calls `hasBankItem` / `withdraw*` in the same tick can see an empty or stale cache and conclude the item is missing.

**Why this matters:** Intermittent false "not in bank" after `openBank()` and rare races when the cache is one tick behind the widget.

**Pattern to follow:**

- Use `Rs2Bank.openBank()` (it waits for a new bank epoch after the UI opens). If you open the bank through a custom path, wait until `ItemContainerChanged` has run or delay one game tick before bulk lookups.
- `hasBankItem` retries 1-2 ticks when the bank is open and the first lookup saw quantity zero (insufficient quantity still fails immediately).

**Where this applies:** `Rs2Bank.openBank`, `updateLocalBank`, `hasBankItem`, `count`, `findBankItem` call sites.

**Defensive check:** Enable DEBUG and watch for `[Rs2Bank] hasBankItem miss after cache retry` or `no BANK ItemContainerChanged within` after opening.

---

## 3. Bank cache skips placeholder rows; automation only sees real stacks

`Rs2Bank.updateLocalBank` drops items whose `ItemComposition.getPlaceholderTemplateId() > 0`. The client may show a placeholder in the slot; the cached list has no entry for it.

**Why this matters:** Scripts that expect "any bank stack" for an item id may see false negatives when the account only has a placeholder until a real item is deposited.

**Pattern to follow:** Treat placeholder-only slots as "not withdrawable" unless you add a dedicated placeholder-aware path. Document user-facing behavior in script configs.

**Where this applies:** `Rs2Bank.updateLocalBank`, any helper using `bankItems()` / `findBankItem`.

---

## 4. Saved item id vs bank row (noted/unnoted and cache drift)

`hasBankItem(id)`, `count(id)`, `hasItem(id)`, and id-based `withdraw*` resolve the bank row in order: **exact id** → **linked noted/unnoted id** from `ItemComposition` → **fuzzy name** from composition (`getMembersName` / `getName`) against cached bank stacks.

**Why this matters:** Inventory setups and scripts often store `ItemID` constants; Jagex renumbers or the bank holds the noted variant while the preset uses the unnoted id (or vice versa). Without fallback you get false “not in bank”.

**Pattern to follow:** Prefer fuzzy or name-based setup rows when ids are unstable; enable DEBUG to see `[Rs2Bank] bank id drift` when a fallback row differs from the requested id.

**Where this applies:** `Rs2Bank.findBankStackRowForSavedId`, `resolveBankStackForSavedId`, id overloads of `hasBankItem` / `count` / `withdraw*`.

---

## 5. Optional inventory-setup validation (Tier A.3)

Set JVM flag `-Dmicrobot.bank.validateInventorySetup=true` so `Rs2InventorySetup.loadInventory` warns once per issue: invalid id, missing `ItemComposition`, id/name mismatch vs cache, and a **single inventory row** with quantity greater than 1 for a **non-stackable** item (same rule as withdraw grouping: use one row per unstacked item or fuzzy mode).

**Where this applies:** `Rs2InventorySetup.validateInventorySetupAgainstDefsIfEnabled`.

---

## 6. Inventory-setup load: keep-list uses ids + names, deposit only when needed

`Rs2InventorySetup.loadInventory()` (default) skips the bank when the inventory already matches the setup **and** there are no “foreign” stacks (items not in the setup’s keep list) **and** quantities do not exceed the setup’s grouped targets. Otherwise it opens the bank and calls `Rs2Bank.depositAllExcept(Set<Integer>, Map<String, Boolean>)`: non-fuzzy rows contribute exact ids (plus linked noted/unnoted ids); fuzzy rows contribute name keys (`true` = substring keep). The keep list includes inventory, equipment, additional filtered items, and rune pouch entries.

**Why this matters:** Name-only `depositAllExcept(Map)` missed noted/unnoted pairs and extra sections; unconditional deposit caused unnecessary UI churn.

**Pattern to follow:** Use `loadInventory(false)` when you must always open the bank (legacy behavior). For custom scripts, reuse `Rs2Bank.isInventoryItemRetainedForSetupDeposit` semantics when building keep predicates.

**Where this applies:** `Rs2InventorySetup.loadInventory`, `loadEquipment`, `Rs2Bank.depositAllExcept(Set, Map)`.

---

## 7. Release / regression — bank mirror (Tier C)

**Automated (CI):** `Rs2BankSetupDepositRetainTest` covers `isInventoryItemRetainedForSetupDeposit` (id + fuzzy + exact name).

**Manual smoke after a banking-affecting change:**

1. Open bank on a live profile; confirm inventory-setup load (or a script using `Rs2Bank.hasBankItem`) sees **coins** (`995`) and at least **one rune** you know is in the bank.
2. If setup load aborts with `Bank item mirror not ready after open`, capture DEBUG `Rs2Bank` logs and check `getBankLiveEpoch()` / `ItemContainerChanged(BANK)` delivery.

**Where this applies:** `Rs2Bank.getBankLiveEpoch`, `verifyBankMirrorAfterOpen`, `Rs2InventorySetup.loadInventory` / `loadEquipment`.

---

<!-- Add new gotchas here as numbered entries (## 8, ## 9, ...). -->

## 8. Ground-item action reflection must fail closed to `Take`, not `CANCEL`

For non-pickup actions, `Rs2TileItemModel` recovers ground-item actions from the injected client's `ItemComposition`. Ordinary `Take` uses `GROUND_ITEM_THIRD_OPTION` directly and does not depend on reflection. That backing layout is obfuscated and can shift on RuneLite bumps. If reflection cannot find a real action list, treat the ground item as exposing `Take` in the injected client's third ground-item slot instead of returning an empty action array.

**Why this matters:** After the RuneLite 1.12.30 bump, the reflection path returned `[]` for ordinary loot such as Cowhide. Loot helpers then failed to map `"Take"` to `GROUND_ITEM_THIRD_OPTION`, silently dispatched a no-op/cancel action, and ExampleScript's drop-and-loot smoke test failed even though the dropped item was visible and lootable.

**Pattern to follow:**

```java
String[] actions = Rs2Reflection.getGroundItemActions(itemComposition);
// If reflection cannot recover actions, this must still contain "Take" at index 2.
```

Do not make call sites compensate by assuming `index == 0` when the action array is empty; keep the fallback centralized in `Rs2Reflection.getGroundItemActions`.

**Where this applies:** `Rs2Reflection.getGroundItemActions`, `Rs2GroundItem.interact`, `Rs2TileItemModel.click`, and any future ground-item interaction helper that derives a `MenuAction` from item actions.

**Defensive check:** Live smoke with ExampleScript's "Drop and loot item" check after any RuneLite or injected-client version bump.

---

## 9. Dispatch ground-item actions through the synthetic target menu

Resolve the ground item's action and `MenuAction` first, then dispatch it with `Microbot.doInvoke(NewMenuEntry, bounds)`. Do not call `Rs2Reflection.invokeMenu` directly for ground-item interactions.

**Why this matters:** A raw canvas click can select a door or NPC that visually overlaps the ground item's tile. The reflected client-menu call can loot successfully but still emit `Unable to find clicked menu op` engine messages because it bypasses the normal clicked-menu correlation. `Microbot.doInvoke` sets `Microbot.targetMenu` before clicking, allowing `MicrobotPlugin` to replace the generated scene menu with the intended ground-item entry regardless of what is under the cursor.

**Pattern to follow:**

```java
Microbot.doInvoke(new NewMenuEntry()
        .option(action)
        .target(target)
        .identifier(itemId)
        .opcode(menuAction.getId())
        .param0(sceneX)
        .param1(sceneY)
        .itemId(-1)
        .worldViewId(worldViewId), bounds);
```

Keep action discovery and dispatch separate: `Rs2TileItemModel.click` owns the shared dispatcher, and legacy `Rs2GroundItem` resolves the current item through the tile-item cache before delegating. `Take` uses the third option directly; reflection is only needed for other actions. `Microbot.doInvoke` owns the interaction.

**Where this applies:** `Rs2GroundItem.interact`, `Rs2TileItemModel.click`, and future ground-item interaction helpers.

**Defensive check:** Drop loot on a tile visually overlapped by an NPC and beside an openable door. Verify the intended item is taken from multiple camera angles and no `Unable to find clicked menu op` engine message appears.

## 10. Distinguish a restored bank snapshot from a live bank update

Bank snapshots are saved per RuneScape profile and restored after a restart. The legacy walker's route planning can use `Rs2Bank.hasBankMirrorSnapshot()` to avoid an unnecessary cache-bootstrap bank trip, but restored data must not advance `getBankLiveEpoch()`. Opening the bank and changing its contents refresh the snapshot through `ItemContainerChanged(BANK)`; publish the complete item list before advancing the epoch.

**Why this matters:** Requiring a live epoch for route planning ignores persisted data, while treating restored data as live can allow bank actions to trust an outdated snapshot.

**Where this applies:** `Rs2Bank`, `Rs2BankData`, and the legacy `Rs2Walker` bank-cache bootstrap check.

**Defensive check:** Restart with a saved snapshot and verify it is available with epoch zero, then open the bank and verify the epoch advances and the saved contents match the live container.

## 11. Propagate ground-item dispatch failures

Legacy wrappers must return the result of the shared tile-item dispatcher. A rejected action must return false. A true result from click, pickup, or a legacy interaction only means a click was dispatched; callers needing pickup confirmation must observe the inventory or relevant ground-stack change with a bounded condition wait. Never block the client thread to wait for pickup.

**Why this matters:** A live drop-and-pickup probe showed that an unsupported action returned true through the legacy RS2Item wrapper even though the dispatcher rejected it.

**Where this applies:** Rs2GroundItem interaction wrappers, Rs2TileItemModel, and ground-item API callers.

## 12. Preserve an explicit ground-item Take when a widget is selected

An explicit `Take` must remain `GROUND_ITEM_THIRD_OPTION`, even when an inventory item or spell is selected. Only generic/custom interactions may resolve to `WIDGET_TARGET_ON_GROUND_ITEM`.

**Why this matters:** A live probe selected an inventory item before calling `pickup()`. The old dispatcher returned true but used the selected item on the ground stack instead of collecting it; inventory never recovered the dropped item.

**Defensive check:** Drop one item, select another inventory item with `Use`, call `pickup()`, and verify the inventory count is restored.

## 13. Verify stackable withdrawals by quantity, not inventory slot count

`Rs2Inventory.count(id)` counts matching inventory slots. A stack of three air runes counts as one slot; use `Rs2Inventory.itemQuantity(id)` when comparing the amount before and after `Rs2Bank.withdrawX(id, amount)`.

**Why this matters:** The walker waited for three new matching slots after withdrawing three air runes for Falador Teleport. The runes occupy one stack, so the wait timed out and the walker incorrectly abandoned the banking route even if the withdrawal succeeded.

Two more traps sit on the same check. An id-based `withdrawX` can resolve a saved id to a different bank row (linked or same-name id, see section 4), so the inventory gains the row id, not the requested one. With the bank in noted mode a non-stackable item such as jewellery or a staff arrives noted and is unusable for the transport. The bank now has a single Note toggle (`InterfaceID.Bankmain.NOTE`, actions "Enable Notes"/"Disable Notes"); `setWithdrawAs` clicks it in both directions, since the old Item button is gone and `QUANTITY1_TEXT` only selects quantity 1. Live, the game reset the toggle to Item every time the bank was opened, so noted mode only matters within one bank session; the walker switches only for non-stackable withdrawals and restores the previous mode before closing. A fixed short wait also turns a slow tick into a reported failure.

**Pattern to follow:** switch to item mode only for a non-stackable item while noted mode is on, restore it in `finally`, then confirm on inventory quantity of the requested id plus the bank row id, waiting until confirmed or the bank closes, and decide from the final state.

```java
Rs2ItemModel row = Rs2Bank.getBankItemForSavedId(itemId);
boolean restoreNoted = WithdrawNoteModePolicy.shouldSwitchToItemMode(
        row == null || !row.isStackable(), Rs2Bank.hasWithdrawAsNote());
try {
    if (restoreNoted && !Rs2Bank.setWithdrawAsItem()) {
        return false;
    }
    TransportWithdrawalConfirmation confirmation = TransportWithdrawalConfirmation.start(
            itemId, row == null ? -1 : row.getId(), amount, Rs2Inventory::itemQuantity);
    if (Rs2Bank.withdrawX(itemId, amount)) {
        sleepUntil(() -> confirmation.evaluate(Rs2Inventory::itemQuantity, Rs2Bank.isOpen())
                != TransportWithdrawalConfirmation.State.PENDING, TransportWithdrawalConfirmation.TIMEOUT_MS);
    }
    return confirmation.evaluate(Rs2Inventory::itemQuantity, true) == TransportWithdrawalConfirmation.State.CONFIRMED;
} finally {
    if (restoreNoted) {
        Rs2Bank.setWithdrawAsNote();
    }
}
```

**Where this applies:** `Rs2Walker.walkWithBankingState` and any bank or inventory workflow that verifies a quantity of stackable items.

## 14. Address chatbox and Grand Exchange widgets through gameval, and read the offer price from its long varp

Chatbox (group 162) child indices shift when Jagex adds a component; RuneLite regenerates `net.runelite.api.gameval.InterfaceID` each update, but raw `(162, n)` pairs stay stale. The Grand Exchange offer price is no longer a varbit: varbit 4398 was removed on 30 Sep 2026 and the in-progress price now lives in long varp 5753, read with `client.getVarpLongValue`.

**Why this matters:** After the 30 Sep 2026 update, `MES_LAYER_SCROLLCONTENTS` moved from 162:52 to 162:53. The buy flow waited 5 s on 162:52 for the search prompt every time, `getVarbitValue(4398)` threw `IndexOutOfBoundsException` on every price check and printed the stack trace in chat, and buy/sell returned success before the offer was placed because they waited on the details panel (465:15) instead of the setup panel (465:26).

**Pattern to follow:**

```java
// Wrong
Rs2Widget.sleepUntilHasWidgetText("Start typing", 162, 52, false, 5000);
Microbot.getVarbitValue(4398);

// Right
Rs2Widget.sleepUntilHasWidgetText("Start typing", InterfaceID.CHATBOX,
        InterfaceID.Chatbox.MES_LAYER_SCROLLCONTENTS & 0xFFFF, false, 5000);
Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().getVarpLongValue(5753));
```

**Where this applies:** `Rs2GrandExchange`, `GrandExchangeWidget`, `Rs2Dialogue`, `Rs2Bank` X-amount prompts, and any helper reading chatbox prompts or GE offer state.


## 15. Suppress AutoLogin only for confirmed terminal supply stops

An ordinary logout can be a break or world hop, so it must keep normal AutoLogin behavior. After confirming a required supply is unavailable in a live bank mirror, call `Rs2Player.logoutWithoutAutoLogin()` off the client thread and cancel the owning run's scheduler. Existing Inventory Setup callers retain their global-pause behavior. Scripts that prefer terminal logout must opt in per loader with `withMissingSupplyPolicy(Rs2InventorySetup.MissingSupplyPolicy.LOGOUT_AND_STOP)`; do not globally change every plugin's supply policy. Partial-stack acceptance and optional supply fallbacks remain separate.

**Why this matters:** AutoLogin otherwise logs back in after supply exhaustion and the stopped bank loop retries indefinitely. Suppression clears on a new gameplay script run, a manual login after the logout, or an AutoLogin restart. Background AutoLogin and BreakHandler starts do not clear it.

**Pattern to follow:** The helper arms suppression before closing the bank and reports whether logout was observed or only requested. Uncompleted requests expire after 15 seconds using a monotonic clock, including when no game-state event occurs; observed terminal logouts do not expire. Ordinary `logout()` is unchanged; scripts that use it for terminal failure must explicitly adopt the new API.

**Defensive check:** Run `AutoLoginSuppressionTest`, `AutoLoginPluginSupplyStopTest`, `ScriptStartedTest`, `TerminalSupplyLogoutTest`, and `InventorySetupSupplyLogoutTest`. Verify live that a shortage stays logged out and restarting a gameplay script permits login.
