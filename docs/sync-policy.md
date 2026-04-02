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

These files exist upstream and are actively developed there. Each entry has a **resolution command** (run first), **forbidden patterns** (remove if found), and **required patterns** (inject if missing).

> Rule: per-file resolution commands override the zone default in the algorithm.

---

### `runelite-client/src/main/java/net/runelite/client/plugins/microbot/MicrobotVersionChecker.java`

**Resolution:** `git checkout HEAD -- <file>` *(keep our stub — do NOT take upstream)*

**What we did:** Gutted the class body. Upstream's version pings `microbot.cloud`.

**Forbidden patterns** (must not appear):
- Any URL containing `microbot.cloud` or `themicrobot.com`
- Any `HttpClient` or `HttpRequest` call
- Any `JOptionPane` or Swing dialog code

**Required patterns** (must appear — inject if missing):
- Class compiles with at minimum empty `checkForUpdate()` and `shutdown()` methods

**Conflict trigger:** If upstream removes the class, remove our stub and any references in `MicrobotPlugin.java`.

---

### `runelite-client/src/main/java/net/runelite/client/plugins/microbot/RandomFactClient.java`

**Resolution:** `git checkout HEAD -- <file>` *(keep our stub — do NOT take upstream)*

**What we did:** Gutted the class body. Upstream's version fetches a "random fact" from an external server.

**Forbidden patterns** (must not appear):
- Any URL containing `microbot.cloud`, `themicrobot.com`, or any non-localhost external host
- Any `HttpClient` or `HttpRequest` call

**Conflict trigger:** If upstream removes the class, remove our stub.

---

### `runelite-client/src/main/java/net/runelite/client/ClientSessionManager.java`

**Resolution:** `git checkout upstream-tracking -- <file>`

**What we did:** Removed the `MicrobotApi` session tracking — a 10-minute ping to `microbot.cloud`.

**Forbidden patterns** (must not appear):
- `MicrobotApi`
- `microbotSessionId`
- `scheduledFutureMicroBot`
- `microbotOpen()`
- `microbotDelete()`
- `microbotPing()`

**Required patterns** (must appear):
- Constructor exists and does NOT take a `MicrobotApi` parameter

**Conflict trigger:** If upstream modifies `ClientSessionManager` for unrelated reasons, accept their change but re-remove the `MicrobotApi` lines.

---

### `runelite-client/src/main/java/net/runelite/client/plugins/microbot/Microbot.java`

**Resolution:** `git checkout upstream-tracking -- <file>`

**What we did:** Removed `PouchScript` injection and removed `QuestHelperPlugin` / `MInventorySetupsPlugin` from the plugin exclusion filter.

**Forbidden patterns** (must not appear):
- `@Inject PouchScript pouchScript`
- `QuestHelperPlugin` inside the exclusion filter
- `MInventorySetupsPlugin` inside the exclusion filter

**Required patterns** (must appear — exact form):
```java
.filter(x -> !x.getClass().getSimpleName().equalsIgnoreCase("MicrobotPlugin")
        && !x.getClass().getSimpleName().equalsIgnoreCase("ShortestPathPlugin")
        && !x.getClass().getSimpleName().equalsIgnoreCase("AntibanPlugin")
        && !x.getClass().getSimpleName().equalsIgnoreCase("ExamplePlugin"))
```
This filter must contain exactly these four exclusions and no others. Verify the surrounding method is `getActiveMicrobotPlugins()`.

**Conflict trigger:** Upstream frequently updates `Microbot.java`. Accept all their changes but re-apply the two removals above.

---

### `runelite-client/src/main/java/net/runelite/client/plugins/microbot/MicrobotPlugin.java`

**Resolution:** `git checkout upstream-tracking -- <file>`

**What we did:**
1. Removed telemetry startup calls
2. Removed `PouchOverlay` / `PouchScript` references
3. Added Status API initialization on startup

**Forbidden patterns** (must not appear):
- `microbotVersionChecker.checkForUpdate()`
- `Microbot.getPouchScript().startUp()`
- `overlayManager.add(pouchOverlay)`
- `@Inject PouchOverlay pouchOverlay`
- `@Inject PouchScript pouchScript`

**Required field** (must appear):
```java
private StatusApiServer statusApiServer;
```

**Required block in `startUp()`** — inject in full if missing:
```java
// Start Status API if port file path is configured
String portFilePath = System.getProperty("status-port-file");
if (portFilePath != null && !portFilePath.isEmpty()) {
    try {
        String profileDir = System.getProperty("cc-profile-dir");
        int charId = 0;
        String charName = "";
        if (profileDir != null) {
            String dirName = Paths.get(profileDir).getFileName().toString();
            if (dirName.startsWith("bot-")) {
                charId = Integer.parseInt(dirName.substring(4));
            }
        }
        botStatusModel = new BotStatusModel(charId, charName);
        StatusApiHandler handler = new StatusApiHandler(botStatusModel);
        statusApiServer = new StatusApiServer(Path.of(portFilePath), handler);
        statusApiServer.start();
    } catch (Exception e) {
        log.error("Failed to start Status API: {}", e.getMessage());
    }
}

// Auto-enable Command Center plugins when launched from Command Center
String ccProfileDir = System.getProperty("cc-profile-dir");
if (ccProfileDir != null && !ccProfileDir.isEmpty()) {
    enableCCPlugin("CC Auto Login");
    enableCCPlugin("CC Script Auto-Start");
}
```

**Required block in `shutDown()`** — inject if missing:
```java
if (statusApiServer != null) {
    statusApiServer.stop();
}
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
