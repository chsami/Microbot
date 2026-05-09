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

<!-- Add new gotchas here as numbered entries (## 7, ## 8, ...). -->
