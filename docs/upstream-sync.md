# Upstream Sync Guide

Procedures and decision-making rules for pulling updates from `chsami/Microbot` into this fork.

---

## Quick-start

```bash
git fetch upstream
git checkout upstream-tracking
git merge upstream/main          # fast-forward mirror
git checkout dev
git merge upstream-tracking      # apply to our branch
# resolve conflicts per the rules below
./gradlew :runelite-client:compileJava   # verify compile
git push origin upstream-tracking dev
```

---

## Branch Model

| Branch | Purpose |
|--------|---------|
| `upstream-tracking` | Mirror of `upstream/main`; contains only upstream commits, never our changes |
| `dev` | Our working branch; upstream changes enter here via `merge upstream-tracking` |
| `main` | Stable branch; `dev` → `main` via PR triggers CI release |

**Never commit our changes directly to `upstream-tracking`.**
**Never merge `dev` into `upstream-tracking`.**

---

## Sync Policy Reference

Before resolving any conflict, read **`docs/sync-policy.md`** — it lists every file we own,
every file we modified for security, and the exact lines to protect in each.

---

## Before You Start

```bash
# Confirm remotes are configured
git remote -v
# origin   https://github.com/Juanfra24/Microbot_Frieren.git
# upstream https://github.com/chsami/Microbot.git

# If upstream is missing:
git remote add upstream https://github.com/chsami/Microbot.git
```

---

## Step-by-Step Procedure

### 1. Fetch upstream

```bash
git fetch upstream
```

Check what's new:

```bash
git log upstream-tracking..upstream/main --oneline
git rev-list --count upstream-tracking..upstream/main
```

### 2. Advance upstream-tracking

```bash
git checkout upstream-tracking
git merge upstream/main    # should always be a fast-forward
git push origin upstream-tracking
```

If it is not a fast-forward, something went wrong — see [Troubleshooting](#troubleshooting).

### 3. Merge into dev

```bash
git checkout dev
git merge upstream-tracking
```

### 4. Resolve conflicts

See [Conflict Decision Rules](#conflict-decision-rules) below.

After resolving each file:

```bash
git add <resolved-file>
```

### 5. Verify build

```bash
./gradlew :runelite-client:compileJava
```

Fix any compilation errors before committing.

### 6. Commit

```bash
git commit -m "merge: sync with upstream chsami/Microbot v<version>

<summary of notable upstream changes>

Conflict resolution:
- <file>: <why we kept our version or accepted upstream>

Co-Authored-By: Claude Sonnet 4.6 <noreply@anthropic.com>"
```

### 7. Push and PR

```bash
git push origin upstream-tracking dev
```

Then open a PR: `dev → main` to trigger the CI release build.

---

## Conflict Decision Rules

Conflicts fall into two categories: **files we own** and **files upstream owns**. Apply the rule for each file individually.

### Files WE own — always keep our version

These files contain fork-specific work. When upstream modifies them, accept our version and integrate upstream's changes by hand only if they are additive and non-breaking.

| File / Pattern | Why we own it | What to watch for |
|----------------|--------------|-------------------|
| `commandcenter/**` | Entire CC package is ours; upstream never touches it | N/A — no conflicts expected |
| `CLAUDE.md` (root) | Our AI assistant guide; upstream has their own at a different path | Restore ours after merge if overwritten |
| `.github/workflows/build.yml` | Fork CI config (semantic versioning, shaded JAR, release) | Upstream may update build steps — evaluate case by case |
| `.gitignore` | We added `.worktrees/` and `debug_temp_version.txt` | Accept both sets of ignores; union them manually |
| `gradle.properties` | Version numbers differ; ours is intentionally diverged | Keep our version number; import upstream dep version bumps manually |
| `docs/ARCHITECTURE.md` | Describes our fork architecture | Keep ours; upstream may have a different one |
| `docs/security-audit.md`, `docs/upstream-sync.md`, `docs/network-verification.md` | Fork-specific docs | Keep ours |

**Resolution command when we own the file:**
```bash
git checkout HEAD -- <file>    # restore our version
git add <file>
```

### Files UPSTREAM owns — prefer upstream version

These files are vanilla RuneLite or Microbot infrastructure. Accept upstream changes unless they re-introduce something we deleted.

| File / Pattern | Notes |
|----------------|-------|
| `runelite-api/**` | RuneLite API; always take upstream |
| `cache/**` | Cache tooling; always take upstream |
| `runelite-client/build.gradle.kts`, `libs.versions.toml` | Build deps; take upstream, verify our CI still passes |
| `runelite-client/src/main/java/net/runelite/client/plugins/` (non-microbot) | RuneLite plugins; always take upstream |
| Microbot `util/**`, `api/**`, `example/**` | Core automation utilities; take upstream |
| Microbot `questhelper/**`, `accountselector/**`, `shortestpath/**` | Keep deleted — intentionally removed in commit `1460f4e9` |

**Resolution command when upstream owns the file:**
```bash
git checkout upstream-tracking -- <file>
git add <file>
```

### The "modify/delete" conflict type

When git reports `CONFLICT (modify/delete)` it means one side deleted the file and the other modified it.

**Decision tree:**

```
Was this file deleted intentionally in our dev branch?
├── YES (check git log -- <file>; look for "delete" or "remove" commit message)
│   └── Keep deleted: git rm <file>
└── NO (accidental or unclear)
    └── Accept upstream update: git checkout upstream-tracking -- <file> && git add <file>
```

Files intentionally deleted in this fork (as of 2026-03):

| File | Deleted in commit | Reason |
|------|-------------------|--------|
| `accountselector/AutoLoginScript.java` | `1460f4e9` | Replaced by `commandcenter/AutoLoginPlugin.java` |
| `questhelper/QuestScript.java` | `1460f4e9` | Community scripts cleanup |
| `questhelper/helpers/miniquests/enchantedkey/EnchantedKeyDigStep.java` | `1460f4e9` | Same |
| `questhelper/helpers/quests/monkeymadnessii/AgilityDungeonSteps.java` | `1460f4e9` | Same |
| `questhelper/managers/QuestMenuHandler.java` | `1460f4e9` | Same |
| `questhelper/runeliteobjects/Cheerer.java` | `1460f4e9` | Same |
| `questhelper/runeliteobjects/extendedruneliteobjects/RuneliteObjectManager.java` | `1460f4e9` | Same |
| `questhelper/statemanagement/PlayerStateManager.java` | `1460f4e9` | Same |
| `MicrobotApi.java` | Security hardening | Called `microbot.cloud` telemetry |

> Note: `MicrobotVersionChecker.java` and `RandomFactClient.java` are NOT in this table. They are permanently deleted (not stubbed) and have explicit per-file entries in Zone 2 of `docs/sync-policy.md` (Zone 2 covers all files with documented resolution rules, including permanent deletions). When upstream modifies them, the resolution is always `git rm <file>`.

---

## Telemetry Policy

This fork removes Microbot-specific outbound telemetry. RuneLite core telemetry is kept but gated.

| File | Action | Why |
|------|--------|-----|
| `MicrobotVersionChecker.java` | **Deleted permanently** | Phoned `microbot.cloud` without consent |
| `RandomFactClient.java` | **Deleted permanently** | Same |
| `MicrobotApi.java` | **Deleted permanently** | Same |
| `TelemetryClient.java` (RuneLite core) | **Keep upstream version** | Gated by `--disable-telemetry`; needed for upstream sync compatibility |

**Rule:** If upstream adds a new call to `microbot.cloud`, `themicrobot.com`, or any non-Jagex/non-RuneLite external host in a Microbot file, **delete or stub it** and document the decision here.

**Rule:** RuneLite core telemetry changes (in `TelemetryClient.java`, `RuneScapeProfileType`, etc.) are accepted as-is from upstream.

---

## Security-Hardened Files

These files were modified for security. When upstream touches them, accept the upstream diff and reapply our changes on top.

| File | Our change | What to check in upstream diff |
|------|-----------|-------------------------------|
| `RuneLite.java` | CLI flag parsing for `--status-port-file`, `--cc-profile-dir` | Upstream may add new flags; merge all flags together |
| `MicrobotPlugin.java` | Removed telemetry startup calls | Upstream whitespace/event-handler changes: accept; new outbound-call additions: reject |
| `LoginManager.java` | Credential injection for CC AutoLogin | Upstream may refactor world-select or login flow; integrate changes, preserve our credential injection |
| `ConfigManager.java` | Minor hardening | Review upstream diff; accept if non-telemetry |
| `ClientSessionManager.java` | Minor hardening | Review upstream diff; accept if non-telemetry |

---

## Evaluating Upstream Commits

Before merging, scan for anything that needs special handling:

```bash
git log upstream-tracking..upstream/main --oneline
```

Flag these patterns:

| Pattern to look for | What to do |
|--------------------|-----------|
| `microbot.cloud`, `themicrobot.com` | Reject; document in security-audit.md |
| `telemetry:` commits in `TelemetryClient.java` | Accept (RuneLite core) |
| `telemetry:` commits in microbot files | Evaluate; likely reject |
| `build(gradle): update microbot version` | Accept; update our `gradle.properties` version accordingly |
| Game revision updates (`Update * to 2026-*`) | Accept; pure ID/constant changes |
| `feat(external-plugins)` or `MicrobotPluginManager` | Review carefully; we don't use external plugin loading |
| Dependency bumps in `libs.versions.toml` | Accept; verify compile passes |

---

## Automating with GitHub Actions (Future)

A `upstream-sync.yml` workflow can automate steps 1–3 and open a PR:

```yaml
# .github/workflows/upstream-sync.yml
name: Upstream Sync
on:
  schedule:
    - cron: '0 6 * * 1'   # every Monday at 6 AM UTC
  workflow_dispatch:

jobs:
  sync:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with:
          fetch-depth: 0
          token: ${{ secrets.GITHUB_TOKEN }}
      - name: Add upstream remote
        run: git remote add upstream https://github.com/chsami/Microbot.git
      - name: Fetch upstream
        run: git fetch upstream
      - name: Advance upstream-tracking
        run: |
          git checkout upstream-tracking
          git merge --ff-only upstream/main
          git push origin upstream-tracking
      - name: Create sync branch
        run: |
          BRANCH="sync/upstream-$(date +%Y-%m-%d)"
          git checkout dev
          git checkout -b "$BRANCH"
          git merge upstream-tracking || true   # continue even with conflicts
          git push origin "$BRANCH"
      - name: Open PR
        run: |
          BRANCH="sync/upstream-$(date +%Y-%m-%d)"
          gh pr create \
            --base dev \
            --head "$BRANCH" \
            --title "chore: upstream sync $(date +%Y-%m-%d)" \
            --body "Automated upstream sync. Review conflicts before merging."
        env:
          GH_TOKEN: ${{ secrets.GITHUB_TOKEN }}
```

Until this is enabled, the sync is done manually following this document.

---

## Troubleshooting

### `upstream-tracking` is not a fast-forward

This means commits were accidentally added to `upstream-tracking` from our side.

```bash
# Identify the divergence
git log upstream/main..upstream-tracking --oneline

# If they are our commits that shouldn't be there, hard reset
# WARNING: destructive — only do this if upstream-tracking has no unique value
git checkout upstream-tracking
git reset --hard upstream/main
git push --force origin upstream-tracking
```

### Merge produces hundreds of unexpected conflicts

Likely `upstream-tracking` was rebased or force-pushed by upstream.

```bash
# Check if upstream/main was force-pushed
git log upstream/main --oneline | head -5
git log upstream-tracking --oneline | head -5
# If the histories have diverged, treat upstream/main as the new truth

git checkout upstream-tracking
git reset --hard upstream/main
git push --force origin upstream-tracking

# Then re-merge from the common ancestor
git checkout dev
git merge upstream-tracking
```

### Compilation errors after merge

1. Run `./gradlew :runelite-client:compileJava` and capture errors.
2. Most errors will be in files upstream modified that reference symbols we deleted.
3. Fix each error by:
   - Removing the reference (if it's to a deleted telemetry class), or
   - Accepting upstream's refactored call signature.
4. Never comment-out code to silence errors — fix the root cause.

### Build version mismatch

After accepting upstream's `gradle.properties`, our CI version (`microbot.version`) may conflict.

**Rule:** Keep our version scheme (`1.0.N` auto-increment via CI). Do not adopt upstream's version directly — use it only as a reference for what Microbot API version we're based on.

---

## Sync History

| Date | Upstream version | Commits merged | Notable changes |
|------|-----------------|----------------|-----------------|
| 2026-03-31 | 2.1.34 | 12 | LootManager dead-player location fix, Rs2Npc isDead inversion fix, Rs2Shop break fix, Rs2GameObject dedup filter, Rs2Walker slf4j logging |
| 2026-03-30 | 2.1.33 | 76 | RuneLite rev 237, queryable API fixes, LoginManager refactor, Brazil world hopper, new integration tests |
| (initial fork) | 2.1.24 | — | Fork baseline |
