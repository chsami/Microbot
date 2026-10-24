# Detection Hardening Plan

Audit of Microbot detection surfaces and a phased plan to harden them. Scope: anything a server-side or client-side anticheat could sample to distinguish this client from vanilla RuneLite or the Jagex client, or to statistically separate bot activity from human play.

Findings cite `file:line` against the current `development` branch (commit `3f86a314c3`). All references verified by direct read, not inference.

---

## 1. Threat model

Three categories of observer, with different capabilities:

| Observer | What it sees | Hardest to fool |
|---|---|---|
| **Server-side behavioral** (Jagex gameplay telemetry) | Per-tick position, menu-action id, camera angle, animation, XP curves, chat volume, session cadence. Aggregates over millions of accounts. | Statistical shape of timing/action distributions, path repetition, fatigue curves. |
| **Client-side static** (bytecode/filesystem/window) | JVM classes loaded, files on disk, window title, process name, listening ports. Could be reached from Jagex-shipped code executing in the same process. | Window title, `~/.microbot/`, open localhost port, package names. |
| **Client-side dynamic** (runtime inspection) | `MouseEvent.getSource()`, reflection on `ClassLoader`, stack walks of event dispatch. | `setSource("Microbot")` watermark, `sun.misc.Unsafe` call sites, `MenuOptionClicked` without corresponding mouse motion. |

Static-observer hardening is cheap and delivers the largest immediate risk reduction. Behavioral hardening is more work but addresses the sampling vectors that survive any amount of local obfuscation.

---

## 2. Findings

Grouped by category and severity. Severity reflects **how trivially** a determined observer could detect the signal, not how bad the game-side consequence would be.

### 2.1 Static fingerprints (trivial detection)

| # | Severity | Finding | Evidence |
|---|---|---|---|
| S1 | Critical | `MouseEvent.setSource("Microbot")` tagged on every synthetic mouse event — literal watermark visible to any listener on the AWT event queue. | `runelite-client/.../util/mouse/VirtualMouse.java:150,160,171,185,199,230,236,242,248,254,260` |
| S2 | Critical | Window title is literally `Microbot`. Any JNI-capable observer sampling `GetWindowTextW` / `_NET_WM_NAME` gets the string. | `runelite-client/src/main/resources/net/runelite/client/runelite.properties:1` |
| S3 | Critical | Dedicated config dir `~/.microbot/` with `agent-token` file. Filesystem probe is a one-liner. | `.../microbot/agentserver/AgentServerPlugin.java:164` |
| S4 | Critical | Outbound calls to `microbot.cloud` (version check every ~10 min, random-fact fetch). SNI visible in TLS ClientHello even under HTTPS. | `.../microbot/MicrobotApi.java:27,33`; `.../MicrobotVersionChecker.java:27,182`; `.../RandomFactClient.java:18` |
| S5 | Critical | Agent server binds `127.0.0.1:<port>` (default 8081) with microbot-specific endpoints (`/varp`, `/ground-items`, `/scripts/deploy`, etc.). Port-scan + response shape = instant ID. | `.../microbot/agentserver/AgentServerPlugin.java:87`; `AgentServerConfig.java:21-23` |
| S6 | High | Package namespace `net.runelite.client.plugins.microbot.*` exposed via `ClassLoader.getResources`, `Class.getName`, any loaded-class enumeration. | Pervasive across `runelite-client/src/main/java/net/runelite/client/plugins/microbot/` |
| S7 | High | `microbot.version` / `microbot.commit` baked into `runelite.properties` — trivially readable JAR resource. | `runelite-client/src/main/resources/net/runelite/client/runelite.properties:3-4` |
| S8 | Medium | Hardcoded `User-Agent: Chrome/11.0.696.60` on outbound HTTP — both anomalously old and consistent, which is worse than no UA. | `globval/GlobalConfiguration.java:306-318,327` |
| S9 | Medium | Window title is further mutated at runtime (`"(NEW CLIENT AVAILABLE)"`, break-timer suffixes) — adds distinctive substrings on top of the base leak. | `.../MicrobotVersionChecker.java:120,141`; `.../BreakHandlerScript.java:198` |

### 2.2 Runtime / bytecode signatures

| # | Severity | Finding | Evidence |
|---|---|---|---|
| R1 | High | `sun.misc.Unsafe` used to write to `static final` fields (antiban instrumentation). Vanilla RuneLite does not do this; trivially detected by a bytecode scan inside the JVM. | `.../microbot/util/antiban/FieldUtil.java:3-26` |
| R2 | High | Custom `URLClassLoader` subclass loading `.class` files from `~/.runelite/dynamic-scripts/`. Non-standard classloader chain is enumerable via reflection. | `.../microbot/dynamicscripts/MicrobotPluginClassLoader.java:15`; `DynamicScriptManager.java:73-74` |
| R3 | Medium | ASM (`ClassReader`/`ClassNode`/`MethodNode`) used at runtime to introspect the deobfuscated Client class. Presence of ASM on the classpath plus a runtime ClassReader over `Client.class` is forensically distinctive. | `.../microbot/util/reflection/Rs2Reflection.java:10-12,40-87` |

### 2.3 Input-layer sampling

The input path is what Jagex sees most directly — it arrives on the same AWT queue the real mouse does.

| # | Severity | Finding | Evidence |
|---|---|---|---|
| I1 | Critical | `MenuOptionClicked` events emitted with no preceding mouse motion — classic "click appears out of nowhere" signature. Opcodes set directly via `.opcode(menuAction.getId())` on utility NPC/object/widget interactions. | `.../util/npc/Rs2Npc.java`; `.../util/gameobject/Rs2GameObject.java`; `.../util/widget/Rs2Widget.java`; `.../util/equipment/Rs2Equipment.java`; `.../util/shop/Rs2Shop.java` |
| I2 | High | Natural-mouse click-point generated with a fixed force parameter (`0.78`) — repeated clicks on the same rect show consistent directional offset bias. | `.../util/misc/Rs2UiHelper.java:47-61` |
| I3 | High | Click offset is truncated-Gaussian with a fixed ±10 px range and cutoff=4σ → tight central cluster detectable on a heatmap across a few hundred clicks. | `.../util/mouse/Mouse.java:44`; `.../util/math/Rs2Random.java:124-127,218-231` |
| I4 | High | Overshoot/corrective-move pattern from `DefaultOvershootManager` is deterministic per `ActivityIntensity`; repeated long-distance moves share the same curvature template. | `.../util/mouse/naturalmouse/support/DefaultOvershootManager.java`; `.../FactoryTemplates.java` |
| I5 | High | Camera set via `setCameraYawTarget` / `setCameraPitchTarget` with speed=3 → angle deltas are step ramps, not the smooth acceleration profiles humans produce. | `.../util/camera/Rs2Camera.java:120-142,169` |
| I6 | Medium | Keyboard inter-event gaps sampled from uniform `Rs2Random.between(20,200)` — histogram shape is a rectangle, not a human right-skew. | `.../util/keyboard/Rs2Keyboard.java:76,91,103,114,126,138` |
| I7 | Medium | Scroll events fire on a fixed ~70 ms delay after mouse-move. Narrow variance across samples. | `.../util/mouse/VirtualMouse.java:177-203` |
| I8 | Medium | Click-down / click-up / drag pauses sampled from narrow uniform ranges (`50-80`, `80-120` ms) producing discrete histogram bins. | `.../util/mouse/VirtualMouse.java:276-290` |

### 2.4 Temporal / behavioural sampling

| # | Severity | Finding | Evidence |
|---|---|---|---|
| T1 | Critical | `sleepUntil` and `sleepUntilTrue` both poll at a literal `sleep(100)` — all reactions snap to a 100 ms grid. | `.../util/Global.java:81,115` |
| T2 | Critical | Walker waits baked to 1/2/4-tick multiples (`sleep(600)`, `sleep(1200)`, `sleep(2400)`, etc.). 15+ call sites. Dialogue / transport waits align to the 600 ms game-tick clock. | `.../util/walker/Rs2Walker.java:~1456-3937` (multiple) |
| T3 | High | Default randomness is uniform (`Rs2Random.between` → `nextInt`), not log-normal. Reaction-time distributions come out rectangular. Gaussian helpers exist but callers almost never use them. | `.../util/math/Rs2Random.java:470-487,124-127,381-384,497-511` |
| T4 | High | Antiban action cadence is a sine wave of amplitude ±2 ticks around a hardcoded PlayStyle range. Fourier transform reveals a fixed frequency line. | `.../microbot/util/antiban/PlayStyle.java:62-69,118-134`; `Rs2Antiban.java:263-310` |
| T5 | High | No session-level fatigue modelling — constant actions/hour for the full session. Break durations are uniform over a configured `[min,max]`. | `BreakHandlerV2Script.java` (whole) |
| T6 | Medium | No path diversification — `Rs2Walker` always calls shortest-path. Repeated trips produce pixel-identical server-side tile sequences. | `.../util/walker/Rs2Walker.java:229` |
| T7 | Medium | `PlayStyle` "profile switches" only swap the logging enum; underlying delay/precision do not actually change. | `PlayStyle.java:85-106,145-160` |

### 2.5 In-game behaviour

| # | Severity | Finding | Evidence |
|---|---|---|---|
| B1 | Critical | `STAMINA_ENERGY_THRESHOLD = 30` — every account toggles stamina at exactly the same 30 %. One of the clearest server-side signals. | `.../util/walker/Rs2Walker.java:686,696` |
| B2 | Critical | No handler for trade requests, moderator messages, or random events. An account that never responds to another player is a standing flag. | Absent; searched `ChatMessage`, `tradeRequest`, `modmute`. |
| B3 | High | Input→click latency is whatever the surrounding loop produces — often tens of ms. Human reaction median ≈ 250 ms. | `.../microbot/Microbot.java` (`doInvoke`) plus most plugins. |
| B4 | High | Camera rotation is *reactive* (right before an invoke), never *anticipatory* (humans rotate before deciding to act). | `.../util/npc/Rs2Npc.java:64` (`Rs2Camera.turnTo`) |
| B5 | Medium | Inventory item selection always picks the closest slot via Manhattan distance — humans have slot-bias from muscle memory but nontrivial scatter. | `.../util/inventory/Rs2Inventory.java:184-200` |
| B6 | Medium | Auto-login retry fires every 600 ms. A human notices a disconnect in 30-120 s. | `.../accountselector/AutoLoginScript.java:54-72,80-95` |
| B7 | Medium | No outbound chat. 50 h of play with zero messages is a trivial server-side filter. | Absent; `ChatMessage` used only for reads. |

---

## 3. Remediation plan

Ordered by detection-reduction per unit effort. Each item lists the highest-value findings it addresses.

### Phase 1 — drop the obvious watermarks (low effort, high impact)

P1-a. **Strip the `setSource("Microbot")` watermark.** Either leave source null (matches synthetic events from other injectors) or set it to a string that occurs in vanilla RuneLite code paths. One file, 11 call sites.
— addresses **S1**.

P1-b. **Change `runelite.title` to `RuneLite`** (or the upstream value) and stop mutating it at runtime for update banners / break-timer text. Surface those in the side panel or a titled overlay instead of the OS window title.
— addresses **S2, S9**.

P1-c. **Move config dir off `~/.microbot/`.** Options, in order of preference: (i) fold everything under `~/.runelite/microbot/` so the outer directory is indistinguishable from vanilla; (ii) honour an env var for the path. Keep `agent-token` file permissions (0600).
— addresses **S3**.

P1-d. **Strip the Chrome-11 `User-Agent`.** Use the default OkHttp/`HttpURLConnection` UA, or — where a UA is required — match the vanilla RuneLite user-agent exactly.
— addresses **S8**.

P1-e. **Strip or rename `microbot.version` / `microbot.commit`** in `runelite.properties`. Version info is useful; it does not need to be resource-discoverable by that name. Prefer a non-resource constant, or merge into an existing RuneLite property with a neutral name.
— addresses **S7**.

### Phase 2 — retire the `microbot.cloud` SNI signal

P2-a. **Make the version-check and random-fact endpoints configurable**, with the ability to disable them. Even with SNI, user-controlled DNS avoids a fixed signature tied to the client build.

P2-b. **Backoff the version-check cadence** (opportunistic on launch only, plus manual refresh) rather than every 10 minutes — the periodic heartbeat is what turns it into a tracking signal.
— addresses **S4**.

### Phase 3 — agent server surface reduction

P3-a. **Off-by-default with explicit opt-in** (already the case — keep it) *and* **randomise the default port** per install, stored in the user config, so fingerprinting by port-number alone fails. Document the port in the side panel, not in a public default.

P3-b. **Add an optional "stealth mode"** that only binds the agent server while a script is actively running, and only to a UNIX domain socket where the OS supports it (falling back to ephemeral TCP otherwise). This eliminates the port listener when the user is playing manually.

P3-c. **Neutral endpoint names / response shapes.** The current `/varp`, `/ground-items`, `/scripts/deploy` set is a signature by itself. At minimum, gate the entire router behind the `X-Agent-Token` header with no unauthenticated discovery path (currently the 401 responses may still leak shape).
— addresses **S5**.

### Phase 4 — input-layer humanisation

P4-a. **Require a mouse trajectory for every menu-option click.** Introduce a `performClick(MenuEntry)` primitive that runs a real natural-mouse motion to the entry's `getMenuTarget()` bounds first, then dispatches the click. Deprecate the direct-opcode helpers in `Rs2Npc`, `Rs2GameObject`, `Rs2Widget`, `Rs2Equipment`, `Rs2Shop` — `MenuOptionClicked` without a matching mouse path is the single biggest input-layer tell.
— addresses **I1, B3, B5**.

P4-b. **Replace uniform reaction-time RNG with a log-normal primitive.** Add `Rs2Random.reactionTime()` that samples log-normal with human-fit parameters (median ≈ 260 ms, σ ≈ 0.3), and replace `between(lo, hi)` at reaction sites with it. Keep `between` for range-constrained waits, but audit callers.
— addresses **T3, I6, I8**.

P4-c. **Randomise the click-point force and radius.** Make the `0.78` force parameter a per-session drawn value, and vary the click-offset radius per widget class (tight for small icons, loose for large canvases). Remove the fixed ±10 px box.
— addresses **I2, I3**.

P4-d. **Smooth camera.** Replace direct yaw/pitch setters with a motion that interpolates over a realistic duration (200-800 ms) with an ease-in-out profile. Add anticipatory rotation — kick off camera movement before the interaction decision is finalised.
— addresses **I5, B4**.

### Phase 5 — temporal de-quantisation

P5-a. **Jitter the `sleepUntil` poll interval.** Change the fixed `sleep(100)` in `Global.java:81,115` to a sampled interval (e.g. log-normal around 80-130 ms, re-sampled each iteration). One-line change, eliminates the 100 ms grid.
— addresses **T1**.

P5-b. **Un-align tick waits.** Introduce a `tickWait(n)` helper that returns `n*600 ± jitter_lognormal(80)` and replace literal `sleep(600*k)` sites in `Rs2Walker`. The action will still resolve on the same tick; the client's wake-up will not.
— addresses **T2**.

P5-c. **De-periodicise antiban.** Replace the sine-wave amplitude in `PlayStyle.evolvePlayStyle` with a brown-noise / OU-process jitter so Fourier analysis does not find a line. Make the inter-antiban cadence itself sampled log-normal rather than bounded uniform.
— addresses **T4, T7**.

P5-d. **Session fatigue.** Add a session-scoped multiplier on action delays that drifts upward over the session (e.g. +1 % per 15 minutes) and resets after breaks. Change break durations to a bimodal distribution: short (≤ 2 min) with high weight, long (30-120 min) with small weight.
— addresses **T5**.

### Phase 6 — behavioural coverage

P6-a. **Per-account stamina threshold.** Draw `STAMINA_ENERGY_THRESHOLD` once per account (20-55 %) and persist it, so repeated play from one account is consistent but population-wide it spreads.
— addresses **B1**.

P6-b. **Random-event / trade / mod-mute handlers.** Register listeners that pause the running script, perform a plausible response (decline trade after 10-40 s, dismiss random event through its own dialogue UI), and log the intervention for the user.
— addresses **B2**.

P6-c. **Auto-login backoff.** Replace the 600 ms retry with exponential backoff (5 s → 15 s → 60 s → 5 min) plus jitter, and require a minimum 5-second idle between disconnect detection and the first retry.
— addresses **B6**.

P6-d. ~~**Optional chat trickle.** Opt-in emote / occasional-reply feature so long-running accounts produce *some* outbound chat. Off by default; users turn it on if they care about the signal.~~ **Removed.** Timer-based chat unrelated to gameplay context is itself a detection signal; the mitigation was judged worse than the B7 signal it targeted.
— B7 remains an account-management problem.

### Phase 7 — runtime surface (lower priority)

P7-a. **Replace `sun.misc.Unsafe`** in `FieldUtil` with `VarHandle`+`setAccessible` or a plain reflective setter where possible. `Unsafe` is more forensically distinctive than accessible-reflection on the same fields.
— addresses **R1**.

P7-b. **Keep ASM runtime use localised**, or move it into build-time code generation where the result is just reflection at runtime. Harder but removes a classpath signature.
— addresses **R3**.

P7-c. **Dynamic-script classloader** — lower priority; `URLClassLoader` is common, the *name* `MicrobotPluginClassLoader` is the only giveaway. Rename.
— addresses **R2**.

---

## 4. Explicitly out of scope

- **Package renaming / ProGuard.** The `net.runelite.client.plugins.microbot.*` namespace is pervasive and project-defining. Renaming it is a separate, larger piece of work and is a weaker signal than items S1-S5 anyway.
- **Game-packet manipulation.** Nothing in `net.runelite.client.plugins.microbot.*` writes to the wire directly; all packet interaction goes through vanilla RuneLite hooks. No action needed.
- **Remote script loading.** `DynamicScriptManager` only loads from local disk; no remote class-fetch path exists. Not a current risk.

---

## 5. Progress

Status against the remediation plan in section 3. Each item links to the commit that implemented it.

### Phase 1 — drop the obvious watermarks ✅ complete

| Item | Finding | Status | Commit |
|---|---|---|---|
| P1-a | S1 mouse-event source watermark | ✅ | `feat(mouse): drop Microbot event-source watermark, bypass translator via pre-scaling` |
| P1-b | S2/S9 window title | ✅ | `chore(ui,resources): neutralize window title and rename microbot.* property keys` |
| P1-c | S3 config dir | ✅ | `feat(agentserver): flatten token to ~/.runelite/.agent-token, randomize default port` |
| P1-d | S8 Chrome-11 User-Agent | ✅ | `chore(http): drop dead Chrome-11 User-Agent path in GlobalConfiguration` |
| P1-e | S7 property key names | ✅ | `chore(ui,resources): neutralize window title and rename microbot.* property keys` |

### Phase 2 — retire the `microbot.cloud` SNI signal ✅ complete

| Item | Finding | Status | Commit |
|---|---|---|---|
| P2-a | S4 make calls configurable / disableable | ✅ | `feat(microbot): disableTelemetry toggle + per-install identity seed, gate microbot.cloud calls` |
| P2-b | S4 drop periodic 10-min version-check heartbeat | ✅ | (same commit) |

### Phase 3 — agent server surface reduction ✅ complete

| Item | Finding | Status | Commit |
|---|---|---|---|
| P3-a | S5 randomize default port per install | ✅ | `feat(agentserver): flatten token to ~/.runelite/.agent-token, randomize default port` |
| P3-b | S5 bind only while scripts run / UDS option | ✅ two complementary mitigations. (i) Opt-in "Stealth bind" mode: server starts only when a script is actively running (ScriptHeartbeatRegistry) and tears down after 20s idle. (ii) Opt-in UDS bind mode: new `UdsHttpServer` / `UdsHttpExchange` / `UdsHttp1Parser` under `agentserver/uds/` bind a Unix domain socket at `~/.runelite/.agent.sock` instead of TCP. Socket permissions set to 0600; parent directory locked to owner-only (chmod 0700 on POSIX, single allow-owner ACL entry on Windows/NTFS); stale-socket cleanup on startup; path-length validation per-OS. UDS types reached reflectively so the compile target stays at Java 11. Handlers are untouched — `UdsHttpExchange extends HttpExchange`. | (this commit) |
| P3-c | S5 token-gated endpoint discovery | ✅ every pre-auth failure now returns an opaque 404 with a plain-text "Not Found" body. Matches the JDK HttpServer's default for unbound paths, so scanners can't distinguish `/varp` from `/does-not-exist` without the token. | (this commit) |

### Phase 4 — input-layer humanisation ✅ complete

| Item | Finding | Status | Commit |
|---|---|---|---|
| P4-a | I1 real mouse trajectory on every `MenuOptionClicked` | ✅ ungated motion in `VirtualMouse.click` / `drag` (was gated off by `Rs2AntibanSettings.naturalMouse=false` default); removed the same-pixel early-return in `Rs2UiHelper.getClickingPoint` so back-to-back clicks on the same rect always re-randomise; unconditionalised the post-click compensating sleeps in `Rs2Inventory` (4 sites) and `Rs2GrandExchange` (3 sites) so they survive the default flip; flipped `Rs2AntibanSettings.naturalMouse` default to `true`. The flag now only toggles click-point anchoring strategy (mouse-pos vs last-click) and a few hover-gate methods in `Rs2Npc` / `Rs2Bank` / `Rs2Inventory` / `Rs2GameObject` / `Rs2Tile`, which now work by default | `feat(mouse): ungate natural-mouse trajectory from Rs2AntibanSettings gate`; `fix(ui): always randomise click point — drop same-pixel early-return in Rs2UiHelper.getClickingPoint`; `refactor(inventory,ge): unconditionalise post-click settle sleeps, flip naturalMouse default on` |
| P4-b | T3/I6/I8 log-normal reaction-time primitive | ✅ callers migrated: added `Rs2Random.logNormalBounded(min,max)` and routed the uniform-ranged sites in `Rs2Keyboard` (inter-event gaps), `VirtualMouse` (scroll + drag button pauses), and `Microbot.click`/`drag` (post-click settle) through it; `Rs2Random.reactionTime()` now applies `SessionFatigue.applyTo(...)` transparently. | (this commit) |
| P4-c | I2 randomise click-point force | ✅ | `feat(ui): per-session randomized click-point force` |
| P4-d | I5 smooth camera rotation | ✅ `Rs2Camera.setPitch`/`setYaw` interpolate target over 220–780ms in 10 ease-in-out steps; direct one-shot setters retained as `setPitchInstant`/`setYawInstant` for callers that need them. | (this commit) |

### Phase 5 — temporal de-quantisation ✅ complete

| Item | Finding | Status | Commit |
|---|---|---|---|
| P5-a | T1 jitter the `sleepUntil` poll | ✅ | `feat(global): log-normal sleepUntil jitter + jittered tick-wait helper` |
| P5-b | T2 un-align tick waits | ✅ | `feat(walker): per-account bimodal stamina threshold + jittered tick-waits` |
| P5-c | T4 de-periodicise antiban (sine → OU) | ✅ | `refactor(antiban): remove sun.misc.Unsafe, replace sine evolve with Ornstein-Uhlenbeck, add SessionFatigue primitive` |
| P5-d | T5 session fatigue | ✅ callers wired: added `Global.sleepFatigued`, `Global.sleepGaussianFatigued`, `Global.sleepTickJitterFatigued`; `Script.run()` starts the session on first logged-in heartbeat and `BreakHandlerV2Script` ends it on every transition into `LOGGED_OUT` so fatigue resets after breaks. | (this commit) |

### Phase 6 — behavioural coverage ✅ complete

| Item | Finding | Status | Commit |
|---|---|---|---|
| P6-a | B1 per-account stamina threshold | ✅ | `feat(walker): per-account bimodal stamina threshold + jittered tick-waits` |
| P6-b | B2 random-event / trade / mod-mute handlers | ✅ three new `BlockingEvent`s pre-registered in `BlockingEventManager`: `TradeRequestEvent` (pauses 10–40s on TRADEREQ), `ModeratorMessageEvent` (HIGHEST priority, 30–120s pause on MODCHAT/MODPRIVATECHAT), `RandomEventNpcEvent` (10–30s pause when a random-event NPC is interacting — deliberately no auto-dismiss). Backed by `ChatEventMonitor` on the event bus. | (this commit) |
| P6-c | B6 auto-login backoff | ✅ | `feat(autologin): exponential retry backoff between login attempts` |
| P6-d | B7 optional chat trickle | 🗑️ Reverted. `ChatTricklePlugin` was shipped then removed — timer-based out-of-context chat was judged to introduce a worse signal than the zero-chat-volume one it targeted. B7 is now classified as account-management. | (rollback) |

### Phase 7 — runtime surface ✅ complete

| Item | Finding | Status | Commit |
|---|---|---|---|
| P7-a | R1 replace `sun.misc.Unsafe` | ✅ (deleted — was dead code) | `refactor(antiban): remove sun.misc.Unsafe, replace sine evolve with Ornstein-Uhlenbeck, add SessionFatigue primitive` |
| P7-b | R3 move runtime ASM to build-time | ✅ four-stage fix, fully committed. (i) Localised: all `org.objectweb.asm.*` imports moved into `MenuActionAsmResolver`; `Rs2Reflection.class` carries no ASM strings in its constant pool. (ii) Persistent install-level cache via `MenuActionInfoCache` at `~/.runelite/.menu-action-info.properties` — after first successful scan the resolution is pinned; subsequent startups resolve via plain reflection without touching `MenuActionAsmResolver`. (iii) Classpath pre-seed path: `MenuActionInfoCache.load()` consults a bundled resource `menu-action-info.properties` under the package. (iv) Build-time generator: `MenuActionResourceSeeder` + the `:client:seedMenuActionInfo` Gradle task produce the pre-seed by scanning the injected-client jar at build time (the jar ships with RuneLite mixins already applied, so the wrapper method is present at compile time). A pre-seed file generated against the pinned injected-client is committed with this change, so even a fresh install skips ASM load on first launch. Regenerate via `:client:seedMenuActionInfo` whenever the injected-client version bumps. | (this commit) |
| P7-c | R2 rename `MicrobotPluginClassLoader` | ✅ | `refactor(externalplugins): rename MicrobotPluginClassLoader to PluginJarClassLoader` |

### Regression guards added

- `MouseSourceTest` — ASM constant-pool scan asserts no `"Microbot"` string literal in `VirtualMouse.class` / `Mouse.class`; asserts `Mouse.VIRTUAL_SOURCE` is gone; stretched-mode translator end-to-end.
- `GlobalPollIntervalTest` — bounds, spread, median not on 100 ms, tick-jitter breaks the 600 ms grid.
- `Rs2WalkerStaminaTest` — range, determinism, case-insensitivity, install-seed scatter, bimodality, null-fallback.
- `Rs2RandomReactionTimeTest` — bounds, ~260 ms median, right-skew, target-median tracking, inactive-session median preservation.
- `Rs2RandomLogNormalBoundedTest` — bounds, geometric-mean median, right-skew, degenerate/swapped-bound handling.
- `Rs2UiHelperClickForceTest` — range, session stability, explicit non-equality with legacy 0.78.
- `VirtualMouseUngatedMotionTest` — bytecode scan asserts `VirtualMouse.class` has no `GETSTATIC` of `Rs2AntibanSettings.naturalMouse`; reflective check that the flag defaults to `true`.
- `Rs2UiHelperClickPointJitterTest` — asserts `Rs2Random.randomPointEx` still produces variance with an in-rect anchor, and bytecode scan asserts `Rs2UiHelper.getClickingPoint` no longer calls `isMouseWithinRectangle` (i.e. the same-pixel early-return is gone).
- `AutoLoginBackoffTest` — early-retry bounds, monotonic escalation, cap, user floor.
- `UnsafeUsageGuardTest` — filesystem scan: no `sun.misc.Unsafe` import anywhere under `plugins/microbot/`.
- `PlayStyleOrnsteinUhlenbeckTest` — stationarity, mean-reversion, strict positivity, autocorrelation (no fixed period).
- `SessionFatigueTest` — inactive baseline, linear growth to cap, reset.
- `SessionFatigueWiringTest` — start/end lifecycle idempotency and applyTo identity-on-zero.
- `Rs2CameraSmoothingTest` — `setPitch`/`setYaw` no longer directly call `setCameraPitchTarget`/`setCameraYawTarget`, instant-helper escape hatches exist, smoothing duration/step counts satisfy minimum thresholds.
- `ChatEventMonitorTest` — TRADEREQ matches only messages with "wishes to trade", MODCHAT and MODPRIVATECHAT both stamp the mod timestamp, unrelated chat types do not fire, `acknowledge*` clears freshness.
- `AgentHandlerAuthOpacityTest` — every pre-auth failure (missing token, wrong token, cross-origin header, spoofed Host header) returns a body- and status-identical 404 to an unbound-path probe; correct token passes through.
- `Rs2ReflectionAsmIsolationTest` — constant-pool scan asserts `Rs2Reflection.class` contains no `org.objectweb.asm.*` strings, and a companion sanity check that `MenuActionAsmResolver.class` still does (so the test isn't vacuous).
- `MenuActionInfoCacheTest` — round-trips the install-level cache and pins the bust conditions: missing fields, malformed props, bogus owner class, dropped method, descriptor drift. Also asserts the supported integral garbage-value kinds (Byte/Short/Integer/Long) serialize and deserialize losslessly.
- `UdsHttpServerTest` — end-to-end HTTP/1.1 over a real UNIX socket on Java 16+ (skipped below). Unknown-path 404, JSON ping round-trip, opaque-auth enforcement in both directions, malformed-request 400, path-length validator rejection.
- `UdsParentDirLockdownTest` — POSIX assertion that the socket's parent directory is locked to 0700 (owner-only traversal) after `start()`, including creation of missing parents. Guards against a regression where others-traversal would let a second local account connect to the socket regardless of the socket file's own mode.
- `MenuActionInfoCacheTest#bundledLoaderResolvesAgainstTheShippedClientJar` — asserts the shipped pre-seed resolves to a real `Method` at test time. If the resource stops shipping, or drifts from the injected-client jar, this test flips red and the fix is `:client:seedMenuActionInfo`.

### Remaining follow-ups

The original plan (phases 1–7) is fully addressed and every mitigation is shipped, tested, and committed. Only one operational reminder remains:

- **Regenerate the pre-seed on injected-client bumps.** The bundled `menu-action-info.properties` is tied to the obfuscator seed of the specific injected-client build. When the dependency in `libs.versions.toml` moves, run `./gradlew :client:seedMenuActionInfo` and commit the updated resource. If the resource goes stale without regeneration, `bundledLoaderResolvesAgainstTheShippedClientJar` catches it in CI; in production the bundled cache self-busts via the owner-class-not-found path and falls through to the user cache / ASM scan without functional impact (only the first-run detection-surface benefit is lost until the next regeneration).

---

## Phase 8 — Deferred: in-process runtime detection vectors

Phases 1–7 close everything observable to a passive observer (network, filesystem, at-rest bytecode). What remains is what an **active observer running inside the same JVM** could see — i.e. detection code shipped by Jagex as part of their client jar and executed at runtime. None of the below are exploited by Jagex today as far as we know; documenting for later.

### 8.1 OS-cursor desync (strongest single tell)

`VirtualMouse.java:72,84,96` and `Rs2Keyboard.java:62` dispatch input via `Canvas.dispatchEvent(event)`. The synthetic `MouseEvent` never moves the real OS cursor. A two-line detector —

```java
Point os = MouseInfo.getPointerInfo().getLocation();
Point aw = lastMouseEvent.getLocationOnScreen();
if (os.distance(aw) > epsilon) { /* synthetic */ }
```

— or its JNI equivalent (`user32!GetCursorPos`, `CGEventGetLocation`, `XQueryPointer`) catches every click.

**Why we haven't closed this:** the naïve fix is `java.awt.Robot.mouseMove()` before dispatch, which moves the actual OS cursor. That breaks multi-instance botting on a single desktop — one cursor per machine = one bot per machine. For end users running a single client this is fine; for anyone scaling (the majority of serious users), it's a product-killing regression.

**Mitigation options, ranked by engineering cost:**

1. **Virtual displays (recommended long-term).** Each Microbot instance runs inside its own isolated display server — `Xvfb` / `Xephyr` on Linux, separate RDP sessions or Hyper-V VMs on Windows. Each session has its own X/Win32 cursor state, so `Robot.mouseMove()` inside the session is both real (satisfies `MouseInfo` polling) and isolated from siblings. This is how every serious bot farm operates.
   - **Cost:** deployment complexity. End users have to learn Docker / RDP-session orchestration. We'd likely ship a reference `docker-compose.yml` with one Xvfb service per bot and let users fan out from there.
   - **Upside:** solves the cursor problem *for real* rather than papering over it, and provides a clean abstraction for account isolation (IP, config dir, session cookies) in the process.
2. **Per-window input (Windows only).** `PostMessage(hwnd, WM_MOUSEMOVE, …)` hits the target HWND without moving the OS cursor. Only defeats detectors that look at the AWT event stream; global cursor polls (`GetCursorPos`) still see the discrepancy. So this is a partial fix with no Linux/macOS equivalent.
3. **Java agent that spoofs `MouseInfo.getPointerInfo()`.** A `-javaagent:` bundled with the launcher rewrites `java.awt.MouseInfo` to return a `ThreadLocal` last-dispatched position. Keeps multi-instance-per-JVM scaling. Brittle: Jagex can defeat it in a single commit by reading cursor position via JNI instead of the Java API. Requires maintaining the agent across Java versions.

**Decision**: none of the three are worth shipping now. We've noted the vector and the scaling constraint. Revisit if Jagex ships a detector that exploits it (behavioral signals from ban waves would make this obvious within days of deployment).

### 8.2 JVM introspection (thread names, class enumeration, stack walks)

Anything in the JVM can enumerate:

- **Thread names.** `Script.java:33` names threads `<ScriptClass>-<n>`. `Thread.getAllStackTraces().keySet()` dumps them all. A detector searching for class names ending in `Script` or matching a list of known Microbot script names has near-zero false-positive rate.
- **Loaded classes.** Any walk of the classloader chain finds `net.runelite.client.plugins.microbot.*`. Vanilla RuneLite has zero classes in that package.
- **Stack traces during event dispatch.** When a synthetic click fires, the call stack includes `Rs2Npc.interact → VirtualMouse.click → Canvas.dispatchEvent`. Vanilla RuneLite's mouse stack starts at the EDT with an OS-originated event. Sampling the stack on the EDT during a `MouseEvent` handler separates the two.
- **String constants in the heap.** `MicrobotConfig.configGroup = "microbot"`, plugin descriptor names, `@ConfigGroup` annotations, log format strings. `jcmd <pid> GC.class_histogram` or a plain heap scan finds hundreds.

**Mitigation options:**
- Rename the microbot package wholesale (e.g. into `net.runelite.client.plugins.<innocuous>`) and every string literal that references it. Multi-day refactor, breaks every downstream plugin that imports microbot APIs.
- Rename script threads to match RuneLite's existing pools (`Client`, `Scheduled-Thread-…`, EDT derivatives). Easier; ~1 day.
- Dispatch synthetic events from the EDT rather than script threads so the stack root matches vanilla. Requires reworking every `Canvas.dispatchEvent` call site.

**Decision**: deferred. Doing it properly requires breaking the public plugin API for every downstream bot author, which is a community-breaking change with no matching benefit unless Jagex actually starts scanning.

### 8.3 Filesystem artifacts visible to the JVM

Even with UDS and in-config-group tokens, these remain on disk and are reachable via `new File(...).exists()` from inside the JVM:
- `~/.runelite/.agent-token`
- `~/.runelite/agent/agent.sock` (UDS mode)
- `~/.runelite/agent-trace.jsonl` (only while *Trace requests* is on)
- `~/.runelite/.menu-action-info.properties`

**Mitigation:** fold all three into the standard RuneLite `ConfigManager`-backed config group so they live inside `settings.properties` alongside every other RuneLite setting. A few hours of work, but changes on-disk layout for existing users — needs a migration path.

**Decision**: deferred. The vectors are only reachable from code running in-JVM, so they're already gated by 8.2 — if Jagex can enumerate our classes, they don't need to stat our sidecar files.

### 8.4 Event metadata (`MouseEvent.getWhen()`, idle-motion streams)

Two related signals:
- **`MouseEvent.getWhen()`** is currently set to `System.currentTimeMillis()` at dispatch. Real AWT events carry a kernel-originated timestamp; under load these drift. Detectable via correlation with `System.nanoTime()` sampled in the same handler.
- **Idle motion.** A real player's cursor generates a continuous stream of `MouseMotionEvent`s while hovering between clicks. Microbot emits motion only around clicks (phase 4 ungated the natural-mouse trajectory for clicks, but does not fire idle hover motion between them). Flat idle history is a weak signal on its own but compounds with the other behavioral ones.

**Mitigation:** emit phantom hover events during script idle. Cheap to prototype; unclear yield without a controlled test against a detector we can't see.

**Decision**: deferred, blocked on 8.1 (phantom hover still desyncs from the OS cursor).

### 8.5 Plugin manager enumeration

`PluginManager.getPlugins()` is Guice-injectable from any class. A call inside Jagex's code returns every `@PluginDescriptor` — which includes AutoFighter, AgentServer, BreakHandler, and every script plugin.

**Mitigation:** wrap `PluginManager.getPlugins()` with a caller-filter that hides microbot plugins when the caller isn't in the microbot package. Fragile against any reflective workaround, and breaks legitimate introspection by RuneLite core features (side panel, config UI). Not worth it.

**Decision**: won't fix. Falls under 8.2 — if Jagex is already scanning our classes, our plugin list is redundant.

### 8.6 Behavioral / statistical (server-side, client-side changes don't help)

Documented for completeness; out of scope for client hardening:
- A* optimal paths vs. human scenic routing.
- Camera mostly static vs. human constant orbiting.
- Log-normal reaction times still have a tighter tail than human data.
- No scroll, no right-click-examine, no idle misclicks.
- 24/7 activity lacks circadian structure.
- Zero outgoing chat (account-management problem; no client-side mitigation ships).

These are account-management problems (break schedules, skill diversification, IP rotation, play-window discipline), not client problems. No amount of client hardening closes them.

### Summary of deferred work

| Phase | Vector | Blocker on fixing |
|---|---|---|
| 8.1 | OS-cursor desync | Single-cursor-per-machine kills multi-instance scaling; virtual displays are the real fix but require user-facing deployment changes. |
| 8.2 | JVM introspection (threads/classes/stacks/strings) | Package rename breaks every downstream plugin's imports. |
| 8.3 | Sidecar files | Gated behind 8.2; not reachable without JVM access. |
| 8.4 | Event metadata, idle motion | Gated behind 8.1 (phantom motion without OS-cursor sync just adds more synthetic events to reject). |
| 8.5 | Plugin enumeration | Gated behind 8.2; redundant signal. |
| 8.6 | Behavioral/statistical | Not a client problem. |

The practical signal to revisit this: if a ban wave correlates with a specific Microbot build *and* vanilla RuneLite users on the same accounts are unaffected, one of 8.1–8.5 is being actively exploited and we triage from there.

---

## Phase 9 — hardware identifier spoofing (-javaagent) ✅ complete

Vanilla clients send two machine-bound identifiers with every login: the device ID produced by the gamepack `PlatformInfo` class (reads `wmic csproduct get UUID` on Windows, `system_profiler SPHardwareDataType` on macOS, `/etc/machine-id` on Linux) and the 24-byte `random.dat` seed read from the jagex store. Spoofing one without the other still links accounts (fresh device ID + constant random.dat = contradiction only a spoofed client produces; constant device ID + fresh random.dat per launch is already how the vanilla server-side signal behaves across repeated logins from one machine — the pair must both look machine-independent).

Design (inspired by VitaLite's TDeviceIDMixin / TRandomDatMixin, MIT):

- **Agent**: `Microbot/hwid-agent/` is a standalone Gradle module producing `hwid-agent-<microbot.version>.jar` (manifest `Premain-Class=com.microbot.hwid.HwidAgent`, Java 11 bytecode). ASM is NOT bundled — it resolves from the shaded client jar at runtime.
- **Device ID** (`PlatformInfoTransformer`): identifies the platform class structurally (class name rotates between revisions; its String-returning fingerprint methods contain the commands as LDC constants: two in 1.12.36, one in 1.13.3, current names in the generated table below) and rewrites each such method body to `iload osType; invokestatic com.microbot.hwid.HwidAgent.deviceId(I)Ljava/lang/String;; areturn`. Cleared try-catch blocks / local variable tables are required or the JVM rejects the rewritten class (`ClassFormatError: Illegal exception table range`).
- **random.dat** (`RandomDatTransformer`): every login-send method guards on the Client's static `byte[]` field: if it is set, those 24 bytes are sent; otherwise the method reads `random.dat` into a local `new byte[24]` (all-`0xFF` fallback, which is why VitaLite forces `0xFF`). Up to 1.12.36 that read lived in a separate provider `jl.ab:(B)[B`; from 1.13.0 it is inlined into every send site, so hooking the provider no longer works (the original provider swap also only patched the `client` class and missed the four send sites in `dd`/`gy`). The transformer instead hooks the field, which covers both shapes: on the `client` class it takes the only static `byte[]` field, then in any method that reads that field and never writes it, inserts `getstatic F; invokestatic HwidAgent.randomDatField([B)[B; putstatic F` before each null guard on `F` (`ifnull`, or `aconst_null` + `if_acmpeq`). `randomDatField` fills a null field with the per-account spoofed seed and clears its own value when the toggle is off so vanilla reads resume. A server push (`ae.gg` on 1.13.3, via the copy helpers) copies the new seed into the array the agent injected; the agent saves it for the account it injected that array for, so that account keeps it in later sessions and the next account logging in on the same client gets its own seed instead. The same push would also write the seed into the real `random.dat` on disk; `RandomDatWriteTransformer` prevents that. In any method that rewinds a static file handle (`getstatic H; lconst_0; invokevirtual (J)V`) and contains `bipush 24`, it inserts `HwidAgent.randomDatFile(Object)` right after each null guard's `getstatic H`, which returns `null` while spoofing is on. The client then skips the write (and any guarded read) and leaves the real file untouched; with spoofing off the handle passes through unchanged. On 1.13.3 this guards `ae.gg`; on 1.12.x the `me` read/write helpers and `bz.ag`. Copy helpers and `<clinit>` write the field, so they are not hooked. Every patched class is written back through `FrameClassWriter` (`ClassWriter.COMPUTE_FRAMES`): ASM cannot faithfully copy the gamepack's original stack map frames when the class is rebuilt — on `ae` it mis-resolves the uninitialised types in an unrelated method and the class then fails JVM verification at load (`Call to wrong <init> method`), which stalls login — so the frames are recomputed instead, resolving the class hierarchy from `.class` resources to avoid classloading the gamepack mid-transform. `HwidAgentTest.everyKnownClientRevisionIsHooked` runs both transformers against the current injected-client and every version in `hwid-agent/historical-clients.txt` (1.12.34.1 onward, covering both shapes) and fails if the device ID class is not found, the field is not on `client`, or any method that reads the field without writing it is left unhooked. `HwidAgentTest.transformedClientClassesPassJvmVerification` loads every patched class on every revision through the real JVM verifier and fails if any is rejected. The current field, send sites and helpers are in the generated table below.
- **Stores**: per-account UUID (`spoofed-device-ids.properties`) and per-account 24-byte Base64 seed (`spoofed-random-dats.properties`) under `~/.runelite/`, SHA-256-of-identifier keyed so no PII is stored or logged; override via `microbot.hwid.store` / `microbot.hwid.randomdat.store`. Identifier chain: injected client `getUsername()` → env `JX_CHARACTER_ID` → `JX_CHARACTER_ID` in `~/.runelite/credentials.properties` (read once at premain, which is where the launcher puts Jagex account credentials) → `default`. `getOrCreate` MUST `load()` before checking the in-memory `Properties` — a missing load regenerates a new value every JVM (caught by live smoke testing, see below).
- **Toggle**: `microbot.hwid.spoof` system property, default `true`, re-read per call. Wired to the in-game settings item `MicrobotConfig.keySpoofDeviceId = "spoofDeviceId"` ("Spoof device ID", General section, position 3) in `MicrobotPlugin.startUp()` / `onConfigChanged` (`HWID_SPOOF_PROPERTY` string, no agent compile dependency). Disabling keeps the transformers installed but passes through to the original code (`VanillaDeviceId` port of vita's `DeviceID.vanillaGetDeviceID`; random.dat leaves the field null so the client reads the file itself).
- **Launcher toggle**: the launcher's "Spoof hardware IDs" checkbox (property `hwid_spoof`, default on) decides whether the agent is attached at all. When it is off the launcher starts the client without `-javaagent` and passes `-Dmicrobot.hwid.launcherDisabled=true`, which `HwidSpoofCheck` treats as an intentional opt-out and stays silent. With the agent attached, the in-client "Spoof device ID" setting still switches spoofing per login.
- **Launcher**: `Microbot-Launcher/libs/hwid-agent.js` downloads `hwid-agent-<version>.jar` from the release storage next to the client jar (graceful 404 fallback and keeps launching); `-javaagent` is unshifted before `-jar` in `libs/jar-executor.js` (both launch handlers); `libs/ipc-handlers.js` awaits `ensureHwidAgentJar` in `download-client` and fire-and-forget in `client-exists`.
- **CI**: all three workflows (`nightly.yml`, `manual_nightly.yml`, `release.yml`) build `:hwid-agent:jar`, copy it next to the client artifacts for scp and the GitHub release `files:` list (note differing scp `strip_components`).
- **Device ID is currently unused**: in every injected-client from 1.12.34.1 to 1.13.3 the device ID methods have no callers, and no other class runs a shell command or reads a hardware address, so the client never collects a hardware UUID and `PlatformInfoTransformer` has nothing to replace. It stays installed in case a later gamepack calls it again; the generated table lists the callers, so CI surfaces the change when one appears. Live-verified on 1.13.3 on 2026-10-22: login and a world hop ran the random.dat hook (a spoofed seed was generated for the account), while `spoofed-device-ids.properties` was never created.
- **Login check**: the agent publishes `microbot.hwid.agent` (premain ran), `microbot.hwid.device.installed` (device ID hook installed) and `microbot.hwid.randomdat.lastCall` (epoch millis of the last random.dat hook call) as system properties. `HwidSpoofCheck` in `MicrobotPlugin` marks a login start on `LOGIN_SCREEN`/`HOPPING` and, on the first `LOGGED_IN` after it with Spoof device ID on, warns in the log and game chat if the agent is missing, the device ID hook is missing, or the random.dat hook did not run since the login started. Each problem is reported once per session.
- **Per-revision values**: after every injected-client bump run `scripts/update-obfuscation-values.sh`. It regenerates `menu-action-info.properties`, rewrites the table below and `docs/obfuscation/injected-getters.tsv` (every injected RuneLite accessor with the obfuscated field chain it reads or writes, its multiplier and the inverse to use when writing), then runs `:hwid-agent:test`. When the injected-client version changed it appends the previous version to `hwid-agent/historical-clients.txt`, so every past revision stays in the regression sweep. It exits non-zero if a hook can no longer be located. CI runs it after the build and fails if the committed generated files differ from what it produces.

<!-- obfuscation-values:start -->
_Generated by `scripts/update-obfuscation-values.sh` against injected-client 1.13.4. Do not edit by hand._

| Hook | Value |
|---|---|
| random.dat field | `client.lx` |
| random.dat hooked send sites (7) | `client.ih(B)V`, `client.adk()V`, `client.aef()V`, `client.adz()V`, `aj.as(I)V`, `ar.ck()V`, `ar.cb()V` |
| random.dat field accessed but not hooked | `client.<clinit>()V`, `client.zg([BI)V`, `client.zt([BI)V`, `client.zu([BI)V`, `lm.ge([BII)V` |
| random.dat file guarded while spoofing | `ae.gg(Lxw;II)V` |
| Device ID methods | `vo.ak(I)Ljava/lang/String;` |
| Device ID method callers | none: the client never reads the device ID, so the hook has nothing to replace |
| menuAction | `iv.ev(IIIIIILjava/lang/String;Ljava/lang/String;IIB)V`, garbage `83` (Byte) |
| Injected accessors | 1058 rows in [obfuscation/injected-getters.tsv](obfuscation/injected-getters.tsv) |
<!-- obfuscation-values:end -->

Validation (unit + live smoke, all green):

- `:hwid-agent:test` (JUnit 4) — 14 tests: synthetic patch/define/invoke for both transformers (random.dat: both guard shapes, server-pushed passthrough, server push kept per account across an account switch, toggle-off vanilla read, copy helper left alone, idempotent), real-jar scans of the shipped injected-client (device ID: exactly 1 class patched, fingerprint LDCs gone; random.dat: field resolves to `client`, no null guard left unhooked), identifier fallback (including launcher credentials), and the device-ID toggle. The `jar` task carries the `Premain-Class` manifest that the CI workflows ship.
- Live smoke on the real gamepack jar with both transformers registered: patch installs on `vt` and `client`, patched classes define and run, spoofed device ID and random.dat seed stable across JVM runs, per-account (`JX_CHARACTER_ID=sam`) values distinct, disabled toggle returns vanilla output, no side effects on disk outside the store files.
- `:client:compileJava` + `:client:runUnitTests` green. Microbot itself never sends an HWID; only the gamepack does.

Remaining (flagged, not blocking):
- In-game login validation on a live account (the true end-to-end check that Jagex receives the spoofed pair).
- The device ID patch removes `try-catch`/local-table debug info from the two fingerprint methods; harmless but reviewers should not try to restore StackMapTable across a whole-body rewrite.

Key lesson for the next revision: never trust a store that loads lazily, and never rewrite a method body without also clearing its try-catch ranges — both were live-class failures, not unit-test ones.
