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
