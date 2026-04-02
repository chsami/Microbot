# Design: `/syncrepo` Claude Code Slash Command

**Date:** 2026-04-01
**Status:** Approved

---

## Overview

A Claude Code project-local slash command (`/syncrepo`) that fully automates the upstream sync procedure for the Microbot_Frieren fork. When invoked, Claude runs every git step, auto-resolves conflicts using the rules in `docs/sync-policy.md`, verifies the build, and pushes both branches.

---

## Deliverables

| File | Action |
|------|--------|
| `.claude/commands/syncrepo.md` | New — the slash command skill |
| `docs/sync-policy.md` | Updated — machine-resolutive conflict rules |

---

## Section 1: Skill File

**Location:** `.claude/commands/syncrepo.md`

Claude Code picks up project-local commands from `.claude/commands/`. The file becomes `/syncrepo` available in any Claude Code session opened in the Microbot_Frieren directory.

### Procedure the skill follows

1. `git fetch upstream` — report how many new commits arrived on `upstream/main`
2. If 0 new commits → report "already up to date" and stop
3. Fast-forward `upstream-tracking` to `upstream/main` — if not a fast-forward, stop and report the divergence; do not force-push without user confirmation
4. `git merge upstream-tracking` into `dev`
5. If merge is clean → go to step 7
6. If conflicts → for each conflicted file, apply the Resolution Algorithm from `sync-policy.md` (see Section 2); `git add` each resolved file
7. Run `./gradlew :runelite-client:compileJava` — if errors exist, fix them before continuing; never comment out code to silence errors
8. Update the sync history table in `docs/upstream-sync.md` with: date, upstream version, commit count, notable changes
9. `git commit` using the standard message format:
   ```
   merge: sync with upstream chsami/Microbot v<version>

   <summary of notable upstream changes>

   Conflict resolution:
   - <file>: <why we kept our version or accepted upstream>

   Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>
   ```
10. `git push origin upstream-tracking dev`
11. Print a summary: version synced, commits merged, files with conflicts and how each was resolved

---

## Section 2: Improved `sync-policy.md`

The existing document is restructured to be **machine-resolutive**: every Zone 2 file gets three explicit blocks that Claude can follow algorithmically.

### Structure per Zone 2 file

**Resolution command** — exact git command to run as the starting point:
```bash
git checkout upstream-tracking -- <file>
```
This takes upstream as the base, then the forbidden/required checks clean it up.

**Forbidden patterns** — exact strings that must NOT appear in the resolved file. Claude searches the file after the resolution command and removes the containing statement/block for each hit.

**Required patterns** — exact strings that MUST appear in the resolved file. If any are missing after the resolution command, Claude re-injects the canonical block (also stored in the policy doc).

### Resolution algorithm (referenced by the skill)

```
For each conflicted file:
  1. Identify its zone:
       - Zone 1: file listed in Zone 1 table in sync-policy.md
       - Zone 2: file listed in Zone 2 table in sync-policy.md
       - Intentional delete: file listed in "Intentionally Deleted Files" table
         in docs/upstream-sync.md
       - Zone 3: everything else
  2. Run the resolution command:
       Zone 1:             git checkout HEAD -- <file>
       Zone 2:             use the per-file "Resolution command" from sync-policy.md
                           (overrides the zone default; usually upstream-tracking
                           but some files use HEAD — see per-file specs below)
       Zone 3:             git checkout upstream-tracking -- <file>
       Intentional delete: git rm <file>
  3. Zone 2 only — after the resolution command:
       a. Search for each forbidden pattern — remove the containing line/block
       b. Search for each required pattern — if missing, inject the canonical block
  4. git add <file>
  5. Log: "<file>: <zone> — <what was changed>"
After all files resolved:
  6. ./gradlew :runelite-client:compileJava
  7. Fix any errors, then git add the fixed files
```

### Zone 2 file specifications (new content for sync-policy.md)

#### `MicrobotPlugin.java`
- **Forbidden:** `microbotVersionChecker.checkForUpdate()`, `Microbot.getPouchScript().startUp()`, `overlayManager.add(pouchOverlay)`, `@Inject PouchOverlay pouchOverlay`, `@Inject PouchScript pouchScript`
- **Required (in `startUp()`):** `String portFilePath = System.getProperty("status-port-file");`
- **Required (in `shutDown()`):** `if (statusApiServer != null) { statusApiServer.stop(); }`
- **Canonical inject block** stored in policy doc for re-injection if dropped

#### `Microbot.java`
- **Forbidden:** `@Inject PouchScript pouchScript`, references to `QuestHelperPlugin` or `MInventorySetupsPlugin` in the exclusion filter
- **Required:** `getPluginsWithoutExcluded()` filter contains only `MicrobotPlugin`, `ShortestPathPlugin`, `AntibanPlugin`, `ExamplePlugin`

#### `ClientSessionManager.java`
- **Forbidden:** `MicrobotApi`, `microbotSessionId`, `scheduledFutureMicroBot`, `microbotOpen()`, `microbotDelete()`, `microbotPing()`
- **Required:** constructor signature without `MicrobotApi` parameter

#### `MicrobotVersionChecker.java`
- **Resolution:** `git checkout HEAD -- <file>` (keep our stub, never take upstream)
- **Required:** class exists with empty `checkForUpdate()` and `shutdown()` methods
- **Forbidden:** any HTTP call, any `microbot.cloud` URL, any Swing dialog code

#### `RandomFactClient.java`
- **Resolution:** `git checkout HEAD -- <file>` (keep our stub)
- **Forbidden:** any HTTP call, any external URL

#### `gradle.properties`
- **Resolution:** `git checkout upstream-tracking -- <file>` (take upstream)
- **Post-check:** verify `project.build.version` is not overwritten; restore it from `HEAD` if it was

---

## Out of Scope

- Scheduled / automatic sync (no GitHub Actions workflow)
- Interactive conflict mode (all resolution is automatic per policy)
- ARM64 or Windows build verification (CI handles Linux builds)
