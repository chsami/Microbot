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

**Resolution:** `git rm <file>` *(permanently deleted — keep it gone)*

**What we did:** Deleted entirely. Upstream's version pings `microbot.cloud` with the client version and shows an update dialog.

**Why deleted, not stubbed:** `MicrobotPlugin.java` no longer references this class (import and call were removed). Deleting the file entirely is safe.

**Forbidden patterns** (must not appear):
- Any URL containing `microbot.cloud` or `themicrobot.com`
- Any `HttpClient` or `HttpRequest` call
- Any `JOptionPane` or Swing dialog code

**Conflict trigger:** Upstream will produce a `modify/delete` conflict. Always keep deleted (`git rm`). If upstream adds a new file with similar telemetry behaviour, delete that too and document it here.

---

### `runelite-client/src/main/java/net/runelite/client/plugins/microbot/RandomFactClient.java`

**Resolution:** `git rm <file>` *(permanently deleted — keep it gone)*

**What we did:** Deleted entirely. Upstream's version fetches a "random fact" from an external server on startup.

**Forbidden patterns** (must not appear):
- Any URL containing `microbot.cloud`, `themicrobot.com`, or any non-localhost external host
- Any `HttpClient` or `HttpRequest` call

**Conflict trigger:** Upstream will produce a `modify/delete` conflict. Always keep deleted (`git rm`). If upstream adds a new file with similar external-call behaviour, delete that too and document it here.

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

### `runelite-client/src/main/java/net/runelite/client/RuneLite.java`

**Resolution:** `git checkout upstream-tracking -- <file>`

**What we did:** Added `--cc-profile-dir` and `--status-port-file` CLI flags.

**Forbidden patterns:** none (our additions are purely additive)

**Required patterns** (must appear — exact strings):
- `final ArgumentAcceptingOptionSpec<String> ccProfileDir = parser.accepts("cc-profile-dir", "Command Center profile directory")`
- `final ArgumentAcceptingOptionSpec<String> statusPortFile = parser.accepts("status-port-file", "Path to write Status API port")`
- `System.setProperty("cc-profile-dir", options.valueOf(ccProfileDir));`
- `System.setProperty("status-port-file", options.valueOf(statusPortFile));`

**Conflict trigger:** If upstream adds new CLI flags, merge all flags together. Our flags must survive the merge.

---

### `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/security/LoginManager.java`

**Resolution:** `git checkout upstream-tracking -- <file>`

**What we did:** Replaced a raw `System.out.println` of the profile name with a redacted log line using `CredentialRedactor`, and removed the unused `@Setter public static ConfigProfile activeProfile` static field (login now uses `Microbot.getConfigManager().getProfile()` directly).

**Forbidden patterns** (must not appear):
- `System.out.println(getActiveProfile())`
- `public static ConfigProfile activeProfile = null;`
- `LoginManager.setActiveProfile`

**Required patterns** (must appear):
- `import net.runelite.client.plugins.microbot.commandcenter.CredentialRedactor;`
- `CredentialRedactor.redact(getActiveProfile().getName())`

**Conflict trigger:** If upstream refactors the login flow, accept their change and re-apply the `CredentialRedactor` substitution at the point where the profile name is logged or printed. The raw `System.out.println` of credentials must never be restored.

---

### `runelite-client/src/main/java/net/runelite/client/config/ConfigManager.java`

**Resolution:** `git checkout upstream-tracking -- <file>`

**What we did:** Removed references to the deleted `InventorySetups` plugin (`ConfigInventorySetupDataManager`, `InventorySetup` imports, `@Inject ConfigInventorySetupDataManager` field, `isInventorySetup` check) and removed a `LoginManager.setActiveProfile(newProfile)` call from `switchProfile()` (the static field it set no longer exists).

**Forbidden patterns** (must not appear):
- `ConfigInventorySetupDataManager`
- `import net.runelite.client.plugins.microbot.inventorysetups.InventorySetup`
- `LoginManager.setActiveProfile`
- `isInventorySetup`

**Required patterns:** none (our changes are purely removals)

**Conflict trigger:** If upstream re-adds InventorySetups references, remove them again. If upstream adds a new `LoginManager.setActiveProfile` call, remove it.

---

### `gradle.properties`

**Resolution:** `git checkout upstream-tracking -- <file>`

**What we did:** Nothing structural — our CI auto-increments `project.build.version`, which upstream does not touch. We accept upstream's `microbot.version` bump each sync.

**Forbidden patterns:** none

**Required patterns:** `project.build.version` key must be present. If upstream overwrites it (unlikely), restore the value from HEAD:
```bash
git show HEAD:gradle.properties | grep "^project.build.version"
# then set that value in the resolved file
```

**Post-sync note:** After resolving `gradle.properties`, read `microbot.version` — that value is the upstream version label for the sync history entry in `docs/upstream-sync.md`.

---

## Zone 3 — Files We Accept Fully From Upstream

These were never modified by us. Always take upstream's version without review:

- All `util/` classes (`Rs2Walker`, `Rs2Npc`, `Rs2Shop`, `Rs2GameObject`, etc.)
- All upstream scripts and plugins we don't ship customized versions of
- `LootManager.java` and other RuneLite core classes

---

## Resolution Algorithm

The `/syncrepo` skill follows this algorithm exactly. Zones are defined in the sections above.

### Pre-flight (run before any git operation)

```bash
# Must be on dev branch
git symbolic-ref --short HEAD   # must output: dev

# Must have clean working tree
git status --porcelain           # must output: (empty)
```
If either check fails, abort and report. Do not stash silently.

### Per-file conflict resolution

```
For each file listed in `git diff --name-only --diff-filter=U`:

  1. Identify zone using this priority order:
       a. File has a Zone 2 per-file entry in sync-policy.md  →  Zone 2
       b. File is in Zone 1 table                             →  Zone 1
       c. File is in intentional-delete table (upstream-sync.md)  →  Intentional delete
       d. Everything else                                     →  Zone 3

  2. Run resolution command:
       Zone 1:             git checkout HEAD -- <file>
       Zone 2:             use the per-file "Resolution" command from this doc
       Zone 3:             git checkout upstream-tracking -- <file>
       Intentional delete: git rm <file>

  3. Zone 2 only — after the resolution command:
       a. Search for each forbidden pattern — remove the containing statement/block
       b. Search for each required pattern — if missing, inject the canonical block

  4. git add <file>
  5. Log: "<file>: <zone> — <what changed>"
```

### Post-merge verification (always, even after a clean merge)

A clean merge can silently drop our code if upstream rewrote a method. Always verify:

```
For each Zone 2 file that has "Required patterns":
  - Check every required pattern is present in the current working file
  - If any are missing, inject the canonical block and git add the file
```

Then compile:
```bash
./gradlew :runelite-client:compileJava
```
Warnings are acceptable. Errors block the commit — fix the root cause; never comment out code to silence errors.

---

## Conflict Probability by File

| File | Conflict frequency | Notes |
|------|--------------------|-------|
| `MicrobotPlugin.java` | **High** | Upstream adds features here constantly |
| `Microbot.java` | **High** | Central singleton, frequently modified |
| `ClientSessionManager.java` | **Low** | Rarely touched upstream |
| `MicrobotVersionChecker.java` | **Low** | Permanently deleted — upstream produces modify/delete conflict; always `git rm` |
| `RandomFactClient.java` | **Low** | Permanently deleted — upstream produces modify/delete conflict; always `git rm` |
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
- [ ] Build: `./gradlew :runelite-client:compileJava` — warnings OK, errors not OK
- [ ] Run code review agent against the changed upstream files to check impact on CC scripts
