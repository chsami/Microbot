# Manual script pause

The pause icon in the client header, beside the input/community controls, pauses automation. An amber play icon means **scripts are paused**; click it to resume. Pausing enables manual mouse and keyboard input and focuses the game canvas. Resume restores the previous input-enabled setting unless you changed it while paused.

Plugins remain enabled. No script shutdown, restart, state reset, or executor cancellation is performed. Normal `Script` scheduler ticks stop entering script bodies; in-flight code cooperatively waits at shared `Global` wait checkpoints. Those wait deadlines exclude paused time. Cancellation still interrupts a paused worker if you deliberately stop its plugin.

Synthetic mouse, keyboard, menu, world-hop, camera and tab-switch entry points reject actions during pause. Held synthetic buttons/keys are released in the background. Resume waits for that release to finish. Input gestures and deferred commands crossing a pause are discarded, including unfinished typed strings, so stale clicks/characters do not replay into your chat. The next script iteration can re-evaluate game state. This can interrupt a multi-action helper midway; it is not an atomic transaction.

Manual pause is independent of `Microbot.pauseAllScripts`, which is also used temporarily by break handlers and interaction helpers. Finishing a break or stopping another script cannot clear the header pause. Resuming manually does not clear a break. The pause lasts until explicitly resumed, including through logout/world changes in the same client process; it is not saved across client restarts.

## Limits and plugin compatibility

- The game server keeps running. Walking, combat, production, logout, and actions already submitted can continue. Pause cannot undo an accepted command.
- Script objects, fields, and cooperative worker stacks survive; the game world does not freeze. Entity snapshots can become stale. On resume, helpers/scripts must check whether their intended action still applies.
- Plugin event subscribers keep receiving game events so caches and observations stay current. Their fields, custom wall-clock timers, break schedules, and independently managed threads are not frozen. A plugin that needs strict state-machine suspension must consult `ScriptPauseController.isPaused()` before advancing its own logic.
- Custom threads should use `ScriptPauseController.awaitResume()` at safe checkpoints **outside locks**. Never wait on the client thread or Swing EDT. Custom deadlines can use `activeTimeNanos()` to exclude manual pauses.
- Input submitted directly through raw game APIs, `Robot`, or private executors outside the guarded Microbot APIs is not covered. Such plugins need their own pause checks. This is cooperative pausing, not unsafe JVM thread suspension.
- Human-input yielding remains independent: after resume it may briefly defer automation while you are still typing or holding a key. Finish or clear chat before resuming.

The Agent Server state response reports the combined pause status in `scriptsPaused` and the explicit header pause in `manuallyPaused`.

## Verification

Unit coverage checks scheduler/state retention, paused wait budgets, cancellation, UI-thread nonblocking behavior, independent pause ownership, held-input release, manual mouse delivery, stale queued clicks, and interrupted strings. In-game acceptance: pause during walking/typing, use chat and inventory manually, then resume; repeat with input disabled, a break active, and a plugin stopped while paused.
