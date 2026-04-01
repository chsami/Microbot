# Upstream Sync Policy

Reference for every upstream sync. Defines what to accept from upstream, what to protect,
and where conflicts are expected.

---

## Our Fork's Purpose

This fork integrates Microbot with **Command Center** — a bot-farm management app.
The integration requires three things from the client side:

1. **Status API** — a localhost HTTP server the Command Center polls for bot state
2. **Auto-login** — credential injection from a profile directory at launch
3. **Script auto-start** — starting a named script automatically on login
4. **Security hardening** — removing outbound telemetry to `microbot.cloud`

All of this lives under `commandcenter/`. **Upstream never touches this directory.**
The conflict surface is limited to the handful of upstream files we had to modify.

---

## Zone 1 — Our Exclusive Code (never accept upstream's version)

These files do not exist upstream. Accept all incoming upstream changes as non-conflicting additions.

| Path | What it is |
|------|-----------|
| `commandcenter/AutoLoginPlugin.java` | Reads `credentials.properties` from `--cc-profile-dir`, injects email/password |
| `commandcenter/ScriptAutoStartPlugin.java` | Reads `commandcenter.properties`, starts named script via PluginManager |
| `commandcenter/CredentialRedactor.java` | Strips credentials from log output |
| `commandcenter/status/StatusApiServer.java` | Localhost HTTP server, writes port to `--status-port-file` |
| `commandcenter/status/StatusApiHandler.java` | `/status` and `/health` endpoints |
| `commandcenter/status/BotStatusModel.java` | Thread-safe JSON response builder |
| `commandcenter/scripts/core/` | `CCScript`, `CCBehavior`, `StubCCScript`, test base |
| `commandcenter/scripts/{woodcutting,mining,fishing,cooking,combat}/` | 5 CC bot scripts |
| `.github/workflows/build.yml` | Our CI/CD — semantic versioning, shaded JAR, GitHub Release |
| `.github/workflows/nightly.yml` | Nightly build — must use JDK 17, relative artifact path |
| `.github/workflows/manual_nightly.yml` | Manual trigger of nightly |
| `docs/` | All documentation |
| `CLAUDE.md` | AI assistant instructions — keep ours, discard upstream's |

---

## Zone 2 — Files We Modified for Security (protect carefully)

These files exist upstream and are actively developed there. We have **removed telemetry** from them.
During a merge, upstream will re-introduce the removed code as additions. **Always keep our version's removals.**

### `runelite-client/src/main/java/net/runelite/client/plugins/microbot/MicrobotVersionChecker.java`

**What we did:** Gutted the class body. Upstream's version pings `microbot.cloud` with the client version and shows an update-available dialog.

**What to maintain:** The file must remain as a stub (empty `checkForUpdate()` / `shutdown()` methods) so compilation doesn't break — `MicrobotPlugin.java` still references it for now. Never restore the HTTP call or the Swing dialog.

**Conflict trigger:** If upstream renames or removes the class, update our stub to match the new signature.

---

### `runelite-client/src/main/java/net/runelite/client/plugins/microbot/RandomFactClient.java`

**What we did:** Gutted the class body. Upstream's version fetches a "random fact" from an external server and shows it in a dialog on startup.

**What to maintain:** Keep as a stub or delete entirely. Never restore the external HTTP call.

**Conflict trigger:** If upstream removes the class, remove our stub and any references.

---

### `runelite-client/src/main/java/net/runelite/client/ClientSessionManager.java`

**What we did:** Removed the `MicrobotApi` session tracking — the 10-minute ping to `microbot.cloud` that reports an active session UUID.

**Lines to protect:**
- Constructor no longer takes `MicrobotApi microbotApi`
- `microbotSessionId`, `scheduledFutureMicroBot`, `microbotApi` fields are removed
- `microbotOpen()` / `microbotDelete()` calls are removed
- `microbotPing()` method is removed

**Conflict trigger:** If upstream modifies `ClientSessionManager` for unrelated reasons, accept their change but re-remove the `MicrobotApi` lines. Do not restore any call to `microbotApi.*`.

---

### `runelite-client/src/main/java/net/runelite/client/plugins/microbot/Microbot.java`

**What we did:** Removed `PouchScript` injection and removed `QuestHelperPlugin` / `MInventorySetupsPlugin` from the plugin exclusion filter.

**What to maintain:**
- No `@Inject PouchScript pouchScript` field
- The `getPluginsWithoutExcluded()` filter only excludes `MicrobotPlugin`, `ShortestPathPlugin`, `AntibanPlugin`, `ExamplePlugin` — not Quest Helper or MInventorySetups (we don't ship those)

**Conflict trigger:** Upstream frequently updates `Microbot.java`. Accept all their changes but re-apply the two removals above after merging.

---

### `runelite-client/src/main/java/net/runelite/client/plugins/microbot/MicrobotPlugin.java`

**What we did:** Two changes with different reasons:
1. **Removed** `MicrobotVersionChecker.checkForUpdate()` call on startup (telemetry removal)
2. **Removed** `PouchOverlay` / `PouchScript` references (we don't ship the pouch plugin)
3. **Added** Status API initialization on startup (our integration)

**What to maintain:**
```java
// KEEP — our Status API startup block in startUp()
String portFilePath = System.getProperty("status-port-file");
if (portFilePath != null && !portFilePath.isEmpty()) { ... }

// KEEP — our Status API shutdown in shutDown()
if (statusApiServer != null) { statusApiServer.stop(); }

// NEVER RESTORE
microbotVersionChecker.checkForUpdate();     // telemetry
Microbot.getPouchScript().startUp();         // pouch plugin
overlayManager.add(pouchOverlay);            // pouch overlay
```

**Conflict trigger:** High probability — upstream actively modifies this file. Always the most careful merge.

---

## Zone 3 — Files We Accept Fully From Upstream

These were never modified by us. Always take upstream's version without review:

- All `util/` classes (`Rs2Walker`, `Rs2Npc`, `Rs2Shop`, `Rs2GameObject`, etc.)
- All upstream scripts and plugins we don't ship customized versions of
- `gradle.properties` — accept upstream's `microbot.version` bump, but verify `project.build.version` doesn't break our CI
- `LootManager.java` and other RuneLite core classes

---

## Conflict Probability by File

| File | Conflict frequency | Notes |
|------|--------------------|-------|
| `MicrobotPlugin.java` | **High** | Upstream adds features here constantly |
| `Microbot.java` | **High** | Central singleton, frequently modified |
| `ClientSessionManager.java` | **Low** | Rarely touched upstream |
| `MicrobotVersionChecker.java` | **Low** | We gutted it — upstream changes don't matter |
| `RandomFactClient.java` | **Low** | We gutted it — upstream changes don't matter |
| `gradle.properties` | **Every sync** | Version always bumps — auto-resolvable |
| `commandcenter/` | **Never** | Upstream doesn't know this directory exists |

---

## Checklist for Every Sync

After `git merge upstream-tracking` into `dev`:

- [ ] **`MicrobotPlugin.java`** — verify Status API block is present in `startUp()` and `shutDown()`; verify `checkForUpdate()` is NOT present
- [ ] **`Microbot.java`** — verify no `PouchScript` field; verify plugin filter doesn't include QuestHelper/MInventorySetups
- [ ] **`ClientSessionManager.java`** — verify no `MicrobotApi` import or usage
- [ ] **`gradle.properties`** — accept upstream `microbot.version`; bump `project.build.version` if CI requires it
- [ ] **`commandcenter/`** — confirm none of our files were accidentally modified (should never happen)
- [ ] Build: `./gradlew :client:compileJava` — warnings OK, errors not OK
- [ ] Run code review agent against the changed upstream files to check impact on CC scripts
