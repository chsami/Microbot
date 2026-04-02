# `/syncrepo` Slash Command Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a `/syncrepo` Claude Code slash command that automates the full upstream sync procedure for the Microbot_Frieren fork, including machine-resolutive conflict resolution driven by an improved `sync-policy.md`.

**Architecture:** A project-local `.claude/commands/syncrepo.md` skill file that Claude follows step-by-step when invoked. The skill references `docs/sync-policy.md` as its authoritative conflict resolution rulebook. The policy doc is restructured to include per-file forbidden/required patterns and a deterministic resolution algorithm. No code compilation or test suite — all deliverables are markdown/config files.

**Tech Stack:** Git, Gradle (`:runelite-client:compileJava`), Claude Code project-local commands (`.claude/commands/`)

**Spec:** `docs/superpowers/specs/2026-04-01-syncrepo-skill-design.md`

---

### Task 1: Rewrite Zone 2 entries in `sync-policy.md` for existing files

Add machine-resolutive structure (resolution command, forbidden patterns, required patterns, canonical blocks) to the five files already listed in Zone 2.

**Files:**
- Modify: `docs/sync-policy.md`

- [ ] **Step 1: Replace the entire Zone 2 section**

Open `docs/sync-policy.md`. Replace the existing `## Zone 2 — Files We Modified for Security (protect carefully)` section (from that heading down to `## Zone 3`) with the following content:

```markdown
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
```

- [ ] **Step 2: Verify the edit is correct**

Run:
```bash
grep -n "Resolution\|Forbidden\|Required\|statusApiServer\|MicrobotApi\|portFilePath" docs/sync-policy.md
```
Expected: lines referencing each of those terms appear across the five Zone 2 entries.

- [ ] **Step 3: Commit**

```bash
git add docs/sync-policy.md
git commit -m "docs(sync-policy): rewrite Zone 2 with machine-resolutive per-file rules"
```

---

### Task 2: Add missing Zone 2 files to `sync-policy.md`

Add entries for `RuneLite.java`, `LoginManager.java`, `ConfigManager.java`, and `gradle.properties` — all of which have fork-specific changes but were missing from Zone 2.

**Files:**
- Modify: `docs/sync-policy.md`

- [ ] **Step 1: Insert four new Zone 2 entries**

In `docs/sync-policy.md`, append the following four entries immediately before the `## Zone 3` heading:

```markdown
---

### `runelite-client/src/main/java/net/runelite/client/RuneLite.java`

**Resolution:** `git checkout upstream-tracking -- <file>`

**What we did:** Added `--cc-profile-dir` and `--status-port-file` CLI flags.

**Forbidden patterns:** none (our additions are purely additive)

**Required patterns** (must appear — exact strings):
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

**Conflict trigger:** If upstream adds new CLI flags, merge all flags together.

---

### `runelite-client/src/main/java/net/runelite/client/plugins/microbot/util/security/LoginManager.java`

**Resolution:** `git checkout upstream-tracking -- <file>`

**What we did:** Replaced a raw `System.out.println` of the profile name with a redacted log line using `CredentialRedactor`, and removed the unused `@Setter public static ConfigProfile activeProfile` static field.

**Forbidden patterns** (must not appear):
- `System.out.println(getActiveProfile())`
- `@Setter`
- `public static ConfigProfile activeProfile = null;`

**Required patterns** (must appear):
- `import net.runelite.client.plugins.microbot.commandcenter.CredentialRedactor;`
- `CredentialRedactor.redact(getActiveProfile().getName())`

**Conflict trigger:** If upstream refactors the login flow, accept their change and re-apply the `CredentialRedactor` substitution at the point where the profile name is logged or printed.

---

### `runelite-client/src/main/java/net/runelite/client/config/ConfigManager.java`

**Resolution:** `git checkout upstream-tracking -- <file>`

**What we did:** Removed references to the deleted `InventorySetups` plugin and removed `LoginManager.setActiveProfile()` call (we removed the static field it set).

**Forbidden patterns** (must not appear):
- `ConfigInventorySetupDataManager`
- `InventorySetup` imports from the microbot package
- `LoginManager.setActiveProfile`
- `isInventorySetup`

**Required patterns:** none (our changes are purely removals)

**Conflict trigger:** If upstream re-adds InventorySetups references, remove them again. If upstream adds a new `LoginManager.setActiveProfile` call, remove it.

---

### `gradle.properties`

**Resolution:** `git checkout upstream-tracking -- <file>`

**What we did:** Nothing structural — our CI auto-increments `project.build.version`, which upstream does not touch. We accept upstream's `microbot.version` bump each sync.

**Forbidden patterns:** none

**Required patterns:** `project.build.version` key must be present. If upstream overwrites it (unlikely), restore the value from `HEAD`:
```bash
git show HEAD:gradle.properties | grep "^project.build.version" 
# then set that value in the resolved file
```

**Post-sync note:** After accepting upstream's `gradle.properties`, read `microbot.version` — that value is the upstream version label for the sync history entry in `docs/upstream-sync.md`.

---
```

- [ ] **Step 2: Verify entries appear**

```bash
grep -n "RuneLite.java\|LoginManager.java\|ConfigManager.java\|gradle.properties" docs/sync-policy.md
```
Expected: four lines, one per new entry heading.

- [ ] **Step 3: Commit**

```bash
git add docs/sync-policy.md
git commit -m "docs(sync-policy): add missing Zone 2 entries for RuneLite, LoginManager, ConfigManager, gradle.properties"
```

---

### Task 3: Add Resolution Algorithm section to `sync-policy.md`

Add the deterministic algorithm the skill will follow verbatim.

**Files:**
- Modify: `docs/sync-policy.md`

- [ ] **Step 1: Insert Resolution Algorithm section**

Insert the following section immediately before `## Conflict Probability by File`:

```markdown
## Resolution Algorithm

The `/syncrepo` skill follows this algorithm exactly. The algorithm references zones defined in the sections above.

### Pre-flight (before any git operation)

```bash
# Must be on dev branch
git symbolic-ref --short HEAD   # must output: dev

# Must have clean working tree
git status --porcelain           # must output: (empty)
```
If either check fails, abort and report the issue. Do not stash silently.

### Per-file conflict resolution

```
For each file listed in `git diff --name-only --diff-filter=U`:

  1. Identify zone using this priority order:
       a. File has a Zone 2 per-file entry above  →  Zone 2
       b. File is in Zone 1 table                 →  Zone 1
       c. File is in intentional-delete table     →  Intentional delete
       d. Everything else                         →  Zone 3

  2. Run resolution command:
       Zone 1:             git checkout HEAD -- <file>
       Zone 2:             use the per-file "Resolution" command
                           (usually upstream-tracking; some files use HEAD)
       Zone 3:             git checkout upstream-tracking -- <file>
       Intentional delete: git rm <file>

  3. Zone 2 only — after the resolution command:
       a. Search for each forbidden pattern — remove the containing statement/block
       b. Search for each required pattern — if missing, inject the canonical block

  4. git add <file>
  5. Log: "<file>: <zone> — <what changed>"
```

### Post-merge verification (always run, even after a clean merge)

A clean merge can silently drop our code if upstream rewrote a method. Always verify:

```
For each Zone 2 file with a "Required patterns" entry:
  - Check every required pattern is present in the current working file
  - If any are missing, inject the canonical block and git add the file
```

Then compile:
```bash
./gradlew :runelite-client:compileJava
```
If errors exist: fix them before committing. Never comment out code to silence errors. Identify whether each error is in our code or upstream's, and fix the root cause.

---
```

- [ ] **Step 2: Verify algorithm section is present**

```bash
grep -n "Resolution Algorithm\|Pre-flight\|Per-file conflict\|Post-merge verification" docs/sync-policy.md
```
Expected: four matching lines.

- [ ] **Step 3: Fix the Gradle task name in the existing checklist**

Find the existing checklist line:
```
./gradlew :client:compileJava
```
Replace with:
```
./gradlew :runelite-client:compileJava
```

Verify:
```bash
grep -n "client:compileJava" docs/sync-policy.md
```
Expected: one line, using `:runelite-client:compileJava`.

- [ ] **Step 4: Commit**

```bash
git add docs/sync-policy.md
git commit -m "docs(sync-policy): add resolution algorithm and fix gradle task name"
```

---

### Task 4: Fix intentional-delete table in `upstream-sync.md`

Remove `MicrobotVersionChecker.java` and `RandomFactClient.java` from the intentional-delete table — they are stubs, not deleted files, and listing them there causes a zone classification conflict.

**Files:**
- Modify: `docs/upstream-sync.md`

- [ ] **Step 1: Remove the two rows from the intentional-delete table**

In `docs/upstream-sync.md`, find the table under `## The "modify/delete" conflict type` → `Files intentionally deleted in this fork`. Remove these two rows:

```
| `MicrobotVersionChecker.java` | Security hardening | Called `microbot.cloud` telemetry |
| `RandomFactClient.java` | Security hardening | Called `microbot.cloud` telemetry |
```

Add a note below the table:
```markdown
> Note: `MicrobotVersionChecker.java` and `RandomFactClient.java` are **not** deleted — they remain as empty stubs so `MicrobotPlugin.java` can still reference them. They are Zone 2 files. See `sync-policy.md` for their resolution rules.
```

- [ ] **Step 2: Verify the rows are gone**

```bash
grep -n "MicrobotVersionChecker\|RandomFactClient" docs/upstream-sync.md
```
Expected: lines appear only in the Security-Hardened Files table and the note, not in the intentional-delete table.

- [ ] **Step 3: Commit**

```bash
git add docs/upstream-sync.md
git commit -m "docs(upstream-sync): remove stub files from intentional-delete table, add clarifying note"
```

---

### Task 5: Create the `/syncrepo` slash command skill

Create the `.claude/commands/` directory and write the skill file that Claude Code will load when `/syncrepo` is invoked.

**Files:**
- Create: `.claude/commands/syncrepo.md`

- [ ] **Step 1: Create the commands directory**

```bash
mkdir -p .claude/commands
```

- [ ] **Step 2: Write the skill file**

Create `.claude/commands/syncrepo.md` with this exact content:

```markdown
# Sync Upstream

Sync the Microbot_Frieren fork with upstream (`chsami/Microbot`). Follow every step below exactly. Do not skip steps or reorder them.

## Working directory

All git commands run from the root of the Microbot_Frieren repository:
`C:\Users\Juanfra\projects\Microbot_Frieren`

## Pre-flight checks

Run both checks before touching git:

```bash
git symbolic-ref --short HEAD
```
Must output `dev`. If not, stop and tell the user which branch they are on.

```bash
git status --porcelain
```
Must output nothing. If there are uncommitted changes, stop and list the dirty files. Do not stash.

## Step 1 — Fetch upstream

```bash
git fetch upstream
```

Count new commits:
```bash
git rev-list --count upstream-tracking..upstream/main
```

If the count is 0, report "Already up to date with upstream. Nothing to sync." and stop.

Otherwise report: "Found N new upstream commits. Starting sync."

Show the commit list:
```bash
git log upstream-tracking..upstream/main --oneline
```

Scan the list for these patterns that need special handling:
- `microbot.cloud` or `themicrobot.com` in any commit title → flag for telemetry review
- `Update * to 2026-*` → game revision update, safe to accept
- `build(gradle)` or `microbot version` → version bump, safe to accept

## Step 2 — Advance upstream-tracking (local only)

```bash
git checkout upstream-tracking
git merge --ff-only upstream/main
git checkout dev
```

If `merge --ff-only` fails (not a fast-forward), stop immediately and report:
```bash
git log upstream/main..upstream-tracking --oneline
```
Tell the user what commits are on `upstream-tracking` that aren't on `upstream/main`. Do NOT force-push without explicit user confirmation.

## Step 3 — Merge into dev

```bash
git merge upstream-tracking
```

If the merge exits cleanly (exit code 0, no conflict markers), skip to **Step 5**.

If conflicts exist, list them:
```bash
git diff --name-only --diff-filter=U
```

## Step 4 — Resolve conflicts

For each conflicted file, apply the resolution algorithm from `docs/sync-policy.md` → **Resolution Algorithm** section. Read that section now before resolving any file.

Zone priority order (highest to lowest):
1. File has a Zone 2 per-file entry in `sync-policy.md` → Zone 2
2. File is listed in Zone 1 table → Zone 1  
3. File is in the intentional-delete table in `upstream-sync.md` → Intentional delete
4. Everything else → Zone 3

Resolution commands:
- Zone 1: `git checkout HEAD -- <file>`
- Zone 2: use the per-file "Resolution" command from `sync-policy.md`
- Zone 3: `git checkout upstream-tracking -- <file>`
- Intentional delete: `git rm <file>`

After each file is resolved, `git add <file>`. Log what you did for each file.

## Step 5 — Zone 2 post-merge verification

**Always run this, even if Step 3 was a clean merge.** A clean merge can silently drop our code.

For each Zone 2 file that has "Required patterns" in `sync-policy.md`:
1. Read the current file
2. Check each required pattern is present verbatim
3. If any pattern is missing, inject the canonical block from `sync-policy.md` and `git add <file>`

## Step 6 — Compile check

```bash
cd C:\Users\Juanfra\projects\Microbot_Frieren
./gradlew :runelite-client:compileJava
```

If errors exist:
- Identify whether each error is in our code or upstream's
- Fix the root cause — never comment out code to silence errors
- `git add` any fixed files

Warnings are acceptable. Only errors block the commit.

## Step 7 — Read upstream version

```bash
grep "^microbot.version" gradle.properties
```

This value (e.g., `2.1.35`) is the upstream version label for the sync history entry.

## Step 8 — Update sync history

Edit `docs/upstream-sync.md`. In the `## Sync History` table, prepend a new row:

```
| <today's date YYYY-MM-DD> | <microbot.version from Step 7> | <N commits merged> | <brief summary of notable upstream changes> |
```

## Step 9 — Commit

```bash
git commit -m "merge: sync with upstream chsami/Microbot v<version>

<2-4 bullet summary of notable upstream changes>

Conflict resolution:
- <file>: <what was done and why>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

If there were no conflicts, omit the "Conflict resolution:" section.

## Step 10 — Push

```bash
git push origin upstream-tracking dev
```

If push fails (network/auth error), report the error clearly. The local state is consistent — the user can run `git push origin upstream-tracking dev` manually. Do not attempt to roll back the commit.

## Step 11 — Summary

Print a summary table:

| Item | Value |
|------|-------|
| Upstream version | vX.X.X |
| Commits merged | N |
| Conflicted files | file1 (Zone 2), file2 (Zone 3), … |
| Post-merge fixes | file3: missing required pattern injected |
| Compile | PASS / FAIL (fixed) |
| Pushed | Yes / No (manual push needed) |
```

- [ ] **Step 3: Verify the file was created**

```bash
ls .claude/commands/syncrepo.md
```
Expected: file exists.

- [ ] **Step 4: Commit**

```bash
git add .claude/commands/syncrepo.md
git commit -m "feat: add /syncrepo Claude Code slash command"
```

---

### Task 6: Smoke-test the skill

Run `/syncrepo` to verify the pre-flight checks and early-exit path work correctly. The repo is currently up to date with upstream, so the skill should exit at Step 1 with "Already up to date."

**Files:** none

- [ ] **Step 1: Open Claude Code in the Microbot_Frieren project**

The project directory is `C:\Users\Juanfra\projects\Microbot_Frieren`.

- [ ] **Step 2: Invoke the command**

Type `/syncrepo` and press Enter.

- [ ] **Step 3: Verify expected output**

Claude should:
1. Run pre-flight checks (branch = dev, clean tree) — both pass
2. Run `git fetch upstream`
3. Count 0 new commits
4. Report "Already up to date with upstream. Nothing to sync." and stop

If any other output is produced, read the skill file and fix the issue.

- [ ] **Step 4: Push the dev branch to origin**

```bash
git push origin dev
```
