# Looting and pickup contracts

The looting repair is developed on `codex/looting-api-repairs`, based directly on upstream `development`. It does not contain the earlier API repair commit or Walker-V2 history.

## Selection and execution

`Rs2LootEngine.with(params)` builds a reusable selection plan. `addByValue`, `addByNames`, coins and other intents are combined before execution. `loot()` takes fresh client-thread snapshots through `Microbot.getRs2TileItemCache()`, limited to the player's native world view. It does not depend on the Ground Items overlay plugin being enabled.

Ignored names and minimum quantity apply before the combined minimum-item and delayed-looting gates. Range and stack-value bounds are inclusive. Both minimum and maximum values refer to the entire stack, using long arithmetic; maximum zero is unlimited. `minInvSlots` reserves free slots *after* the pickup. Existing stacks require no new slot. An optional food action must actually free space before looting proceeds.

Each selected tile-item identity is considered once per pass. A later pass refreshes remaining quantities. The default action performs a real pickup; `withLootAction` is an optional override, whose completion is still checked. A pass returns false for no eligible candidates, a closed delay gate, cancellation, insufficient space or failed attempts. True means every selected target made progress, not necessarily that the player collected every item.

## Results

`GroundItemPickup.take(snapshot, minimumFreeSlots)` and `Rs2GroundItem.coreLootResult(item)` return:

- `COLLECTED`: an inventory quantity increase was observed after submission.
- `GROUND_CHANGED`: the target disappeared or its quantity decreased without a confirmed inventory increase. This can include bag/sack storage, despawn, another player's pickup or a scene transition; it is not proof of collection.
- `NO_SPACE`, `REJECTED`, `TIMED_OUT`, or `CANCELLED`: no confirmed progress.

Inventory changes are observational evidence, not transaction identifiers; unrelated concurrent gains of the same item cannot be attributed with certainty. `coreLoot` returns true only for `COLLECTED`. `Builder.getLastResult()` reports the last attempt; the pass boolean also retains failures from earlier attempts.

## Pickup dispatch

`Rs2TileItemModel.pickup()` and its default `click()` request `Take`. Legacy interaction helpers delegate to the same pickup dispatcher. The model retains its originating world view. Location-only legacy requests search only the local player's current view.

Ground actions are read reflectively on the client thread using strict resolution. Unresolved actions are rejected rather than assuming the third action is Take. The existing fallback resolver remains available to unrelated legacy callers. Active item/spell selection is rejected, so Take cannot silently become Use/Cast on the item.

The dispatcher validates the registered view, scene tile, exact TileItem identity, action and click bounds, then moves the mouse off-thread and revalidates. It first waits up to 1.2 seconds for the client to install the requested menu entry, revalidates again, then emits the existing synthetic-menu mouse click and waits up to 1.2 seconds for a matching, unconsumed menu event. The pending menu belongs to that attempt and is cleared on completion, timeout, interruption or exception. An observed menu event is not game completion; the pickup attempt separately waits up to five seconds for inventory or ground changes.

## Pause ownership

Looting is serialized off the client thread. A scoped looting counter gates `Script.run()` without changing `Microbot.pauseAllScripts`. Exceptions release the scope, and a user pause remains intact. A callback must remain bounded; there is no forced termination of arbitrary user code.

## Validation

The initial live probe reproduced the exception leaving scripts paused and restored the original state itself. The rebuilt client passed the same exception probe. Focused regressions cover manual pause preservation, full inventory, partial ground quantity changes, fresh selection, ignored items, reserved slots, long values, menu acknowledgements, exception cleanup, stale targets, selected-item rejection and strict reflection resolution.

The integrated Microbot suite ran 1,221 tests: 1,219 passed and 2 skipped. After the final dispatch changes, all 42 focused checks passed, including event-bus registration and rejecting a click before menu preparation. The client-thread baseline update removes moved legacy violations and records changed lambda numbering, thread-detection guards and existing callers newly inferred from client-thread reads. Scanner enforcement is unchanged.

Live verification on the rebuilt client confirmed strict `Take` resolution, successful drop, ground-item discovery, `COLLECTED`, restored inventory quantity and cleared pending menu. The exception probe also passed. Initial dispatch testing exposed an invalid event-handler name that prevented MicrobotPlugin startup; acknowledgement now uses its existing correctly named handler, covered by a registration regression. Two lobsters were consumed during earlier failed setup attempts. The final isolated attempt restored its starting inventory count. The final client log contained no new relevant errors.
