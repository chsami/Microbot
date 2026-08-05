# Death Handling Gotchas

Rules for working with `Rs2Death`, graves, and Death's Office.

## Wiring it into a script

There is no config interface, mode enum, or options object — scripts read their own config values and
call the statics with plain scalars, the same way they call `Rs2Bank` or `Rs2Walker`.

```java
// walks to the grave, empties it, closes the interface. Death's Office is NOT visited.
if (Rs2Death.hasDeathToHandle()) {
    Rs2Death.recoverItems(config.deathBudget());   // 0 = free items only, MAX_VALUE = pay anything
    return State.BANK;                             // re-gear with whatever the script already does
}

// opt in to the Death's Office trip as well, if the script wants expired items back
Rs2Death.recoverItems(config.deathBudget(), config.useDeathsOffice());

// or price the office yourself before committing — the trip is free, only the reclaim costs
if (Rs2Death.walkToDeathsOffice() && Rs2Death.enterDeathsOffice() && Rs2Death.openDeathsOffice()) {
    Rs2Death.reclaimAll();        // no cap is possible — see rule 11
    Rs2Death.closeInterfaces();   // or inspect first and close to decline without paying
}
```

Or drive the steps yourself when the script wants its own logic in between:

```java
if (Rs2Death.hasGrave()) {
    Rs2Death.walkToGrave();
    Rs2Death.openGrave();
    Rs2Death.lootGraveFreeItems();

    if (Rs2Death.getGraveFee() < myThreshold) {
        Rs2Death.lootGravePaidItems(myThreshold);
    }
}
```

Banking, re-gearing, and walking back are deliberately *not* in this API — scripts already have their own
banking state, so bolting a second one on here would only fight it.

The typical flow an author builds around it:

**recover → bank → resupply from an inventory setup → back to the grind**

`Rs2Death` owns only the first step. The rest is the script's existing banking and `Rs2InventorySetup`
logic, which is why nothing here deposits, withdraws, or re-gears. Scripts that re-stock from a setup
mostly do not care what actually came back from the grave, which sidesteps rule 5 entirely.

## 0. Prefer the game's own numbers over estimating

The "Items Kept on Death" panel (`InterfaceID.Deathkeep`, group **4**, reached from the worn equipment
tab) publishes what the game has already calculated. Read it instead of computing anything:

| Component | Live content |
|---|---|
| `KEPT` (4.6) | item slots + caption `Items that are KEPT:` |
| `GRAVE` (4.7) | item slots + caption `Items that go to your GRAVESTONE: (Fee: None)` |
| `VALUE` (4.18) | `Guide risk value:<br>111,716` |
| 4.14–4.17 | scenario toggles: Protect Item / PK Skull / Killed by a player / Wilderness beyond level 20 |

`getPredictedGraveFee()` and `getRiskValue()` read these directly, so they are **authoritative** — the
per-unit valuation, ironman rate, and any discounted-death allowance are already applied. That beats any
GE-price arithmetic. Death's Office publishes nothing equivalent, and the API deliberately does not
estimate one — see rule 11.

Two things to watch:

- The captions live **inside** the item containers as ordinary children, not in their own components, so
  item slots and the label share a container. Skip entries whose item id is `-1`.
- The panel reflects whichever **scenario the toggles are set to**, not necessarily the player's real
  situation. It answers "what would happen under these conditions".

**Where this applies:** `Rs2Death.getItemsKeptOnDeath`, `getItemsSentToGrave`, `getPredictedGraveFee`,
`getRiskValue`.

## 1. A grave is an NPC, not a game object

Graves respond to `Rs2NpcCache`, not `Rs2GameObject`. Their ids run contiguously from
`NpcID.GRAVESTONE_DEFAULT` (9856) to `NpcID.GRAVESTONE_ANGEL_255` (10367) — 516 ids covering every
player-name and cosmetic permutation.

**Why this matters:** searching for a grave with the object helpers silently finds nothing, and the
failure looks identical to "no grave exists", so handling reports success and the items rot.

**Pattern to follow:**

```java
// Wrong — graves are not objects
Rs2GameObject.interact("Grave", "Loot");

// Right — match the id range against the NPC cache
Microbot.getRs2NpcCache().query()
    .where(npc -> npc.getId() >= NpcID.GRAVESTONE_DEFAULT && npc.getId() <= NpcID.GRAVESTONE_ANGEL_255)
    .nearest(deathLocation, SCENE_RADIUS);
```

Do not enumerate the ids into a list, and do not match on name — anchor the search on the recorded death
location so another player's grave in the same area is never targeted.

Verified live at a real grave: NPC id **9856** (`GRAVESTONE_DEFAULT`), name **`Grave`**, standing on the
death tile. Individual item slots carry `Take` / `Examine`; the section buttons carry `Take-All`.

**Where this applies:** `Rs2Death.getGrave`, `Rs2Death.openGrave`.

## 2. Death handling is never automatic

There is no blocking event and no default-on behaviour. A script must poll
`Rs2Death.hasDeathToHandle()` and act on it itself — see the wiring example above.

**Why this matters:** recovery spends the account's coins on retrieval fees and walks it across the map.
Doing that to a script that never asked for it is worse than leaving the items where they are.

## 3. Bank *after* collecting, never before

A player who just died keeps at most a few items, so the inventory is effectively empty and always has
room for the grave's contents. Banking first is a wasted trip that burns grave timer.

**Why this matters:** this is the reverse of the usual "make space before looting" instinct, and the
instinct is wrong here specifically because death already emptied the inventory. `Rs2Death` does no
banking at all for this reason — the script does it afterwards, with the banking logic it already has.

## 4. A PvP death may leave no grave at all

Dying to another player in the Wilderness hands your tradeables straight to the killer. Untradeables go
to a grave below level 20, or are destroyed above it (unless locked with a Trouver parchment). So after a
PvP death there may be nothing to recover anywhere.

**Why this matters:** "no grave standing" is not the same as "the grave expired into Death's Office".
Treating them as the same sends the script across the map to an empty office, and if it then walks back
to the death spot it re-enters the Wilderness and dies to the same player again — a die-return-die loop.

**Pattern to follow:** `hasGraveExpired()` requires a grave to have actually been *seen* since the death
(`GRAVESTONE_VISIBLE` going **non-zero**, tracked via `Rs2Death.onVarbitChanged` — see rule 8, it is not
a boolean). Never derive it from `lastDeathTime != null && !hasGrave()`.

Check `Rs2Death.getDeathWildernessLevel()` before walking back to a death location.

**Where this applies:** `Rs2Death.hasGraveExpired`, `Rs2Death.handleActorDeath`.

## 5. Supply loss is situational, not universal

On an ordinary PvM death, food and potions go into the grave like everything else and come back normally.
Two cases break that:

- **Wilderness / PvP death.** Food, potions, and phoenix necklaces cannot be graved or dropped — they are
  deleted outright.
- **Dying again while a grave already holds supplies.** Cooked food and potions in the existing grave
  drop to the ground beneath it and despawn after an hour, and unstackable resources already in the grave
  (bones, ores, pure essence, unpowered orbs, planks) are pushed on to Death's Office. Only one
  inventory's worth of those persists per grave.

**Why this matters:** do not write a blanket "supplies are lost on death" assumption either way. A single
PvM death recovers fine; a Wilderness death does not; a second death on top of an uncollected grave
quietly relocates the first death's consumables.

In practice most scripts sidestep this entirely by re-stocking from an inventory setup rather than
depending on what came back — see the expected flow at the top of this guide.

## 6. Never compute the retrieval fee yourself

Read `getGraveFee()` / `getReclaimFee()` from the live interface. The posted fee already accounts for the
per-item tiers (free under 100k, then 1k / 10k / 100k, capped at 500k total), the 50% ironman discount,
and per-boss discounted deaths — Zulrah is free for the first 50 kills, Desert Treasure II bosses and
Yama and Doom of Mokhaiotl and Fortis Colosseum all have their own 75%-off allowances.

**Why this matters:** any fee calculated from item values will be wrong for a large and growing set of
content, and wrong in the expensive direction.

**The fee is never charged to carried coins.** It comes out of **Death's Coffer if it holds anything,
and the bank otherwise**. Do not gate a reclaim on `Rs2Inventory` coins — a freshly respawned player is
usually carrying nothing, so that check refuses reclaims the account can easily afford.

**The two schedules are unrelated — never reuse one for the other.** A grave charges flat coin amounts by
tier with a hard cap; the office charges an uncapped percentage. Numbers that look interchangeable at the
bottom bracket (100k x 1% = the grave's flat 1,000) diverge fast: a 1m item costs 10,000 at a grave and
50,000 at the office.

**Both test unit price, not stack or cumulative value.** Confirmed in game for each:

- *Grave:* 740 noted coal worth 111,000 in total at 150 each showed `(Fee: None)` — over the 100k stack
  threshold, but a single coal is not, so free.
- *Office:* a reclaim of 862 coal + 875 iron ore + 142 steel bars — **~307,000 in total, nothing worth
  100k each** — cost **0**. Bank was 90,702 before and after. That single result rules out both a
  cumulative charge (would have billed ~15k on the 307k) and a per-stack threshold (would have billed the
  125k coal slot).

So the two schedules share the **same 100k per-unit threshold**; they differ only in the fee. Every
stackable item under 100k each is free from both, regardless of stack size. An earlier note here claimed
the office charges on cumulative value — that was wrong, and the test above disproves it.

One half is still unobserved in game: that an item **over** 100k is billed at exactly 5% (office) or the
flat tier (grave). The rates below are confirmed against the wiki's own tables, but a non-zero charge has
never been watched happen here.

**Documented exceptions exist, and they break the per-unit rule.** The wiki lists items "to which the
above rules do not neatly apply" — notably *stacks of amulet of glory (6) worth over 100,000 are charged
**10%** at Death's Office*: double the normal rate, and assessed on the **stack's** value rather than per
unit. Such an item is charged where the per-unit rule predicts free. The wiki's list is explicitly
non-exhaustive, which is the main reason this API does not try to predict an office fee at all.

For reference, the tiers the interface already applies for you:

| Source | Regular | Ironman |
|---|---|---|
| Gravestone, per item | free <100k, then 1k / 10k / 100k by 100k–1m / 1m–10m / 10m+ tiers, **capped at 500k total** | 50% off |
| Death's Office | flat **5%** of value, items 100k+, no cap | 2.5% |

## 7. Abandoned items are deferred, not destroyed

Items left behind because of a zero or exceeded budget stay in the grave for its remaining life, then
move to Death's Office and keep there indefinitely, reclaimable at 5% of value (2.5% ironman).

**Why this matters:** `lootGravePaidItems` returning `false` is a normal, intended outcome — do not treat
it as an error or retry it. `recoverItems` deliberately still returns `true` in that case.

Note the difference between two similarly-named things: **Death's Office** is where unclaimed items go.
**Death's Coffer** is a separate credit pot you deposit items into (for 105% of GE price) to pay future
fees from. This API does not touch the coffer.

## 8. The grave varbits are not what their names suggest

Both were verified against a live grave, and both had my first implementation wrong:

- **`GRAVESTONE_VISIBLE` (10464) is not a boolean.** It reads **0** with no grave and a steady **133**
  with one standing — constant across repeated samples, so neither a flag nor a countdown. Only zero
  versus non-zero is meaningful. Testing `== 1` reports "no grave" while a grave is standing, silently
  disabling the whole recovery path.
- **`GRAVESTONE_DURATION` (10465) counts game ticks, not seconds.** Observed decrementing 1461 → 1377
  over roughly 50 seconds, starting from 1500 (1500 × 0.6s = 900s = the nominal 15 minutes). Reading it
  as seconds overstates remaining time by 40%.

```java
// Wrong
hasGrave()              -> getVarbitValue(GRAVESTONE_VISIBLE) == 1
getGraveTimeRemaining() -> Duration.ofSeconds(getVarbitValue(GRAVESTONE_DURATION))

// Right
hasGrave()              -> getVarbitValue(GRAVESTONE_VISIBLE) != 0
getGraveTimeRemaining() -> Duration.ofMillis(getVarbitValue(GRAVESTONE_DURATION) * 600L)
```

The varbit also drops to zero identically whether the grave was emptied or timed out into Death's Office,
so it cannot distinguish those two on its own.

**Why this matters:** clearing the recorded death when the varbit hits zero permanently disables the
Death's Office path — the very state that path needs to detect is the state that erases it.

**Pattern to follow:** combine it with the "a grave was seen" flag from rule 4, and clear the record only
once handling has completed (`Rs2Death.clearDeathState`). A script that loots its own grave by hand must
call `clearDeathState()` itself, or handling will later walk to an empty Death's Office.

**Where this applies:** `Rs2Death.hasGraveExpired`, `Rs2Death.recoverItems`.

## 9. The grave interface is group 672, and its FEE is prose

Verified live with a grave open. The loaded group is `InterfaceID.GravestoneGeneric` (0x02a0 = **672**),
not `GravestoneRetrieval` (602):

| Component | Live text / action |
|---|---|
| `FRAME` (672.2) | `Gravestone (2/120)` |
| `FREE_CONTAINER_TEXT0` (672.5) | `Free to reclaim:` |
| `FREEBUTTON` (672.8) | action `Take-All` |
| `FEE` (672.12) | `Fee: Paid` |
| `PAYBUTTON` (672.15) | action `Take-All` |
| `INFO` (672.18) | `Death's Coffer: Empty<br>Discard items to reduce a fee.` |

**The `FEE` component is a sentence, not a number.** With the pay section settled it reads `Fee: Paid`,
which contains no digits, so any digit-scan parse returns `0`.

**Why this matters:** `0` here means *nothing is owed*, **not** *there is nothing to claim*. Skipping the
`PAYBUTTON` click on a zero fee abandons items that cost nothing to take:

```java
// Wrong — never clicks PAYBUTTON when the fee reads "Fee: Paid"
int fee = getGraveFee();
if (fee <= 0) return true;
...
clickAndSettle(PAYBUTTON);

// Right — the fee only gates, it never cancels the claim
int fee = getGraveFee();
if (fee > 0) {
    if (fee > budget) return false;
    if (coinsCarried() < fee) return false;
}
clickAndSettle(PAYBUTTON);
```

**Hazard:** `INCINERATOR` (672.17) sits in the bottom-right of the pay section and **destroys items**.
Never click by position in this interface — always target the named component.

**Where this applies:** `Rs2Death.getGraveFee`, `Rs2Death.lootGravePaidItems`.

## 10. `/widgets/list` under-reports; use `/widgets/search`

When debugging interfaces through the agent server, `/widgets/list` reported only group 164 while the
grave interface was open, and `/widgets/search` found group 672 fully populated at the same moment.

**Why this matters:** concluding "the interface is not loaded" from `/widgets/list` sends you looking for
the wrong group entirely. Confirm with a search or a direct `describe` before believing it.

## 11. The Death's Office reclaim has no spending limit, and cannot have one

Verified live with an item waiting. `InterfaceID.DeathOffice` (669) is the right group — title
`Death's Office Item Retrieval (1/120)` — but the cost is never on screen before it is charged:

| Component | Actions | With an item present |
|---|---|---|
| 669.1 idx=1 | — | `Death's Office Item Retrieval (1/120)` |
| 669.1 idx=11 | `Close` | visible |
| 669.3 (`ITEMS`) | `Select`, `Examine` | the item |
| 669.6/7/8/9 | `1` `5` `X` `All` | **hidden** until an item is selected |
| 669.10 (`TAKEALL`) | `Take-All` | visible |
| 669.11 (`INFO`) | — | `Select an item to retrieve.<br>Death's Coffer: 0` — **identical to empty** |

The group has no `FEE` component, `INFO` does not change when items are waiting, the quantity buttons
stay hidden until selection, and `Take-All` never selects. So `reclaimAll()` takes **no budget** — a cap
would be fiction, and `getReclaimFee()` was removed rather than left returning a permanent `0` for
callers to trust.

So the office trip is **opt-in**, not budget-controlled: `recoverItems(budget)` never goes there, and
`recoverItems(budget, true)` does. Whether an account should spend an unknowable amount to recover
expired items is a script-writer decision, not something this API should make on their behalf. The
default is off because the items keep at Death's Office indefinitely, so declining costs nothing and
stays reversible by hand.

When the grave has expired and the office was not requested, `recoverItems` clears the death record and
returns `true` — otherwise `hasDeathToHandle()` would keep reporting a death the caller has already
decided to ignore, and the script would spin on it forever.

**Why this matters:** the grave and the office are not symmetric. A grave publishes its fee in `FEE`
(672.12) and can be budgeted properly; the office cannot. Do not assume a limit that worked at the grave
carries over.

**Contrast:** at a grave the worst case is the 500k cap. At the office it is an uncapped 5% of value, so
a 10M-gear death costs 500k there with nothing to stop it.

**There is deliberately no fee estimator.** An earlier version priced the office contents from GE data
and offered `reclaimAll(maxEstimatedFee)` as a ceiling. It was removed: the office never publishes the fee
before charging, so any such number is a guess, and it guessed **low** on documented exceptions (a glory
stack is charged 10% on the stack, not 5% per unit — see rule 7). A ceiling that can be exceeded is worse
than no ceiling, because callers trust it.

What to use instead:

- `getPredictedGraveFee()` for a real number — the game computes it, the API just reads it (rule 0).
- `reclaimAll()` when the script accepts whatever it costs.
- Walk in, inspect what Death is holding, and `closeInterfaces()` to decline. The trip is free; only the
  reclaim costs. A script that insists on its own cap can price the contents itself and owns that
  assumption.

**Where this applies:** `Rs2Death.reclaimAll`, `Rs2Death.recoverItems`.

## 12. Death's Office needs the entrance object, then a dialogue — not an NPC click

Death stands inside **Death's Domain**, an instanced region (12633). Walking to the entrance coordinate
is not enough — the NPC is never in the scene until you step through the object.

Verified in-game at Lumbridge: the object is `Death's Domain`, id **38426**
(`gameval.ObjectID1.DEATH_OFFICE_ACCESS_GRAVE`), at **(3238, 3192, 0)**, with the action
**`Enter Death's Domain`**.

Note the id lives in `ObjectID1.java`, the overflow file — grepping only `gameval/ObjectID.java` misses
it. The legacy alias is `net.runelite.api.ObjectID.DEATHS_DOMAIN`.

**The interface opens through dialogue, not a menu action on Death.** Verified in game: stepping through
the object auto-walks the player to Death and starts the conversation, so there is no "Collect"/"Talk-to"
click to make. Advance Death's lines, then choose **`Yes, have you got anything for me?`** (group 219):

```
219.1 idx=1  'How does that work?'
219.1 idx=2  'What is this place?'
219.1 idx=3  'Yes, have you got anything for me?'   <- the reclaim option
219.1 idx=4  'More options...'
```

Match on **text**, not the index. The `More options...` entry means the list can grow and shift, so a
hardcoded "option 3" would eventually pick the wrong line. `Rs2Dialogue.clickOption("have you got
anything for me")` does a case-insensitive substring match and resolves the key press itself.

**Sequence:** `walkToDeathsOffice()` → `enterDeathsOffice()` → `openDeathsOffice()` (drives the dialogue)
→ `reclaimAll()`.

The other seven entrance coordinates come from the wiki's map pins (available in the page's raw
wikitext, not the rendered table). Lumbridge calibrates them: the pin says (3238, 3194) against a real
object at (3238, 3192), so expect ~2 tiles of error. That is harmless here — the walk only has to load
the object into the scene, and `enterDeathsOffice()` then matches it by **id**, never by coordinate.
Resolve entrances by id rather than pinning exact tiles.

**Where this applies:** `Rs2Death.enterDeathsOffice`, `Rs2Death.isInDeathsOffice`,
`DeathsOfficeLocation`.

## 13. Always close the retrieval interface

The grave timer pauses while its interface is open. Leaving it up after a partial claim silently freezes
the countdown and confuses any later timing logic.

An interface left open by accident holds the timer indefinitely and makes `getGraveTimeRemaining()` look
stuck. Close it unless you are pausing on purpose.

**Where this applies:** `Rs2Death.closeInterfaces`.

## 14. The grave interface does not close on the last item

Use the `GRAVESTONE_VISIBLE` varbit to confirm a grave was emptied, not the interface's visibility.

**Why this matters:** waiting on `!isGraveOpen()` reports failure on a fully successful loot whenever the
interface lingers.

## 15. The grave timer is not wall-clock

The nominal 15 minutes pauses **while logged out**, **while the grave interface is open**, and **while the
player stands idle**. The idle pause engages after a few ticks, not instantly — a sample taken right after
stopping still shows the countdown moving, which is why an early reading looks like idle does not pause it.
It does; give it a moment.

**Why this matters:** do not compute remaining time from `getLastDeathTime()` — read
`Rs2Death.getGraveTimeRemaining()`, which reflects the real `GRAVESTONE_DURATION` varbit.

## 16. `DeathEvent` and `Rs2Death` are different things

`DeathEvent` is a blocking event handling the one-off first-death Death's Domain tutorial (varp 4517,
region 12633), exiting via the portal. It normally fires once per account and stays automatic. `Rs2Death`
handles every normal death afterwards and is opt-in. Do not merge them.
