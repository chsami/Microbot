# Design: `/syncrepo` Claude Code Slash Command

**Date:** 2026-04-01
**Status:** Approved — post-review revision

---

## Overview

A Claude Code project-local slash command (`/syncrepo`) that fully automates the upstream sync procedure for the Microbot_Frieren fork. When invoked, Claude runs every git step, auto-resolves conflicts using the rules in `docs/sync-policy.md`, verifies the build, and pushes both branches.

---

## Deliverables

| File | Action |
|------|--------|
| `.claude/commands/syncrepo.md` | New — the slash command skill (directory must be created) |
| `docs/sync-policy.md` | Updated — machine-resolutive conflict rules per Section 2 |

---

## Section 1: Skill File

**Location:** `.claude/commands/syncrepo.md`

Claude Code picks up project-local commands from `.claude/commands/`. That directory does not yet exist and must be created. The file becomes `/syncrepo` available in any Claude Code session opened in the Microbot_Frieren directory.

### Pre-flight checks (run before any git operation)

1. Assert `git symbolic-ref --short HEAD` equals `dev` — if not, abort and tell the user which branch they are on
2. Assert `git status --porcelain` is empty — if not, abort and list the dirty files; do not stash silently

### Procedure

1. `git fetch upstream` — report how many new commits arrived on `upstream/main`
2. If 0 new commits → report "already up to date" and stop
3. **Local only:** fast-forward `upstream-tracking` to `upstream/main`:
   ```bash
   git checkout upstream-tracking
   git merge --ff-only upstream/main
   git checkout dev
   ```
   If not a fast-forward, stop and report the divergence (`git log upstream/main..upstream-tracking --oneline`). Do NOT force-push without explicit user confirmation.
4. `git merge upstream-tracking` (while on `dev`)
5. Resolve every conflicted file using the Resolution Algorithm (Section 2)
6. **Zone 2 verification — always, even after a clean merge:** run the required-pattern check on every Zone 2 file listed in Section 2. A clean merge can silently drop our code if upstream rewrote a method. Fix any missing required patterns before continuing.
7. Run `./gradlew :runelite-client:compileJava` — if errors exist, fix them before continuing; never comment out code to silence errors; identify whether each error is in our code or upstream's
8. Read `microbot.version` from the resolved `gradle.properties` — this is the upstream version label for the sync history entry
9. Update the sync history table in `docs/upstream-sync.md` with: date, upstream version (from step 8), commit count, notable changes
10. `git commit` using the standard message format:
    ```
    merge: sync with upstream chsami/Microbot v<version>

    <summary of notable upstream changes>

    Conflict resolution:
    - <file>: <why we kept our version or accepted upstream>

    Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
    ```
11. `git push origin upstream-tracking dev` — this is the **single push point** for both branches; if push fails (network/auth), report clearly; local state is consistent and the user can push manually
12. Print a summary: version synced, commits merged, files with conflicts and how each was resolved

---

## Section 2: Improved `sync-policy.md`

The existing document is restructured to be **machine-resolutive**: every Zone 2 file gets three explicit blocks that Claude can follow algorithmically. The improved doc also corrects two inconsistencies in the current version.

### Inconsistencies to fix in `sync-policy.md`

1. **MicrobotVersionChecker.java and RandomFactClient.java** are listed as "Deleted permanently" in `upstream-sync.md`'s intentional-delete table, but `sync-policy.md` correctly says they must remain as stubs (MicrobotPlugin.java still references them). The design uses `sync-policy.md` as authoritative: they are Zone 2 with `git checkout HEAD` resolution, not intentional deletes. Remove them from the intentional-delete table.

2. **LoginManager.java, RuneLite.java, ConfigManager.java** appear in `upstream-sync.md`'s "Security-Hardened Files" section but have no Zone 2 entry in `sync-policy.md`. They must be added (see per-file specs below).

### Structure per Zone 2 file

**Resolution command** — exact git command to run as the starting point.

**Forbidden patterns** — exact strings that must NOT appear. Claude removes the containing statement/block for each hit.

**Required patterns** — exact strings that MUST appear. If missing, Claude injects the canonical block (stored verbatim in the policy doc).

### Resolution algorithm (referenced by the skill)

```
Pre-merge:
  0. Run pre-flight checks (dirty tree, correct branch)

For each conflicted file:
  1. Identify its zone using this priority order:
       a. Zone 2 per-file spec exists → Zone 2 (overrides intentional-delete table)
       b. Listed in Zone 1 table → Zone 1
       c. Listed in intentional-delete table → Intentional delete
       d. Everything else → Zone 3
  2. Run the resolution command:
       Zone 1:             git checkout HEAD -- <file>
       Zone 2:             per-file "Resolution command" in sync-policy.md
                           (usually git checkout upstream-tracking, but
                            MicrobotVersionChecker and RandomFactClient use HEAD)
       Zone 3:             git checkout upstream-tracking -- <file>
       Intentional delete: git rm <file>
  3. Zone 2 only — after the resolution command:
       a. Search for each forbidden pattern — remove the containing line/block
       b. Search for each required pattern — if missing, inject the canonical block
  4. git add <file>
  5. Log: "<file>: <zone> — <what was changed>"

Post-merge (always, even if no conflicts):
  6. Run required-pattern check on ALL Zone 2 files
  7. ./gradlew :runelite-client:compileJava
  8. Fix any errors, then git add the fixed files
```

### Zone 2 file specifications

#### `MicrobotPlugin.java`
**Resolution:** `git checkout upstream-tracking -- <file>`
**Forbidden:**
- `microbotVersionChecker.checkForUpdate()`
- `Microbot.getPouchScript().startUp()`
- `overlayManager.add(pouchOverlay)`
- `@Inject PouchOverlay pouchOverlay`
- `@Inject PouchScript pouchScript`

**Required (in `startUp()`)** — inject full block if missing:
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

**Required (in `shutDown()`):**
```java
if (statusApiServer != null) {
    statusApiServer.stop();
}
```

**Required field:**
```java
private StatusApiServer statusApiServer;
```

---

#### `Microbot.java`
**Resolution:** `git checkout upstream-tracking -- <file>`
**Forbidden:**
- `@Inject PouchScript pouchScript`
- `QuestHelperPlugin` (in the exclusion filter)
- `MInventorySetupsPlugin` (in the exclusion filter)

**Required — exact form of the exclusion filter (lines 758–761 in current HEAD):**
```java
.filter(x -> !x.getClass().getSimpleName().equalsIgnoreCase("MicrobotPlugin")
        && !x.getClass().getSimpleName().equalsIgnoreCase("ShortestPathPlugin")
        && !x.getClass().getSimpleName().equalsIgnoreCase("AntibanPlugin")
        && !x.getClass().getSimpleName().equalsIgnoreCase("ExamplePlugin"))
```
Note: verify the exact surrounding method context — `getActiveMicrobotPlugins()`. If upstream refactors the method signature, adapt while preserving the four exclusions and no others.

---

#### `ClientSessionManager.java`
**Resolution:** `git checkout upstream-tracking -- <file>`
**Forbidden:**
- `MicrobotApi`
- `microbotSessionId`
- `scheduledFutureMicroBot`
- `microbotOpen()`
- `microbotDelete()`
- `microbotPing()`

**Required:** constructor exists and does NOT take a `MicrobotApi` parameter.

---

#### `RuneLite.java`
**Resolution:** `git checkout upstream-tracking -- <file>`
**Forbidden:** none (our additions are additions, not replacements)
**Required:**
```java
final ArgumentAcceptingOptionSpec<String> ccProfileDir = parser.accepts("cc-profile-dir", "Command Center profile directory")
```
```java
final ArgumentAcceptingOptionSpec<String> statusPortFile = parser.accepts("status-port-file", "Path to write Status API port")
```
```java
System.setProperty("cc-profile-dir", options.valueOf(ccProfileDir));
```
```java
System.setProperty("status-port-file", options.valueOf(statusPortFile));
```

---

#### `LoginManager.java`
**Resolution:** `git checkout upstream-tracking -- <file>`
**Note:** sync-policy.md describes "credential injection for CC AutoLogin" as our change. Before the implementation session, inspect the current `LoginManager.java` to confirm what the exact injected lines are and add them as required patterns. If credential injection was moved entirely to `AutoLoginPlugin.java` (commandcenter/), there may be nothing to protect in `LoginManager.java` and it can be reclassified as Zone 3.

---

#### `ConfigManager.java`
**Resolution:** `git checkout upstream-tracking -- <file>`
**Note:** sync-policy.md describes "minor hardening." Inspect before implementation to confirm exact changes and add required/forbidden patterns. If no fork-specific changes remain, reclassify as Zone 3.

---

#### `MicrobotVersionChecker.java`
**Resolution:** `git checkout HEAD -- <file>` (keep our stub — NOT an intentional delete)
**Forbidden:**
- Any URL containing `microbot.cloud` or `themicrobot.com`
- Any `HttpClient` or `HttpRequest` call
- Any `JOptionPane` or Swing dialog code

**Required:** class compiles with at minimum empty `checkForUpdate()` and `shutdown()` methods.

---

#### `RandomFactClient.java`
**Resolution:** `git checkout HEAD -- <file>` (keep our stub — NOT an intentional delete)
**Forbidden:**
- Any URL containing `microbot.cloud`, `themicrobot.com`, or any non-localhost external host
- Any `HttpClient` or `HttpRequest` call

---

#### `gradle.properties`
**Resolution:** `git checkout upstream-tracking -- <file>`
**Post-check:** read `project.build.version` from `HEAD`'s `gradle.properties`; if the value was overwritten by upstream's version, restore it. `microbot.version` is intentionally taken from upstream.

---

## Out of Scope

- Scheduled / automatic sync (no GitHub Actions workflow)
- Interactive conflict mode (all resolution is automatic per policy)
- Opening a PR after push (user opens PR manually as needed)
- ARM64 or Windows build verification (CI handles Linux builds)
