# Upstream Sync Workflow

Manual procedure to pull updates from `chsami/Microbot` into our fork.

## Steps

1. `git fetch upstream` — fetch latest from chsami/Microbot
2. `git checkout upstream-tracking`
3. `git merge upstream/main` — fast-forward mirror
4. `git checkout dev`
5. `git merge upstream-tracking` — bring upstream changes into dev
6. Resolve conflicts, test build: `./gradlew :runelite-client:build -x test`
7. PR dev → main — triggers release build

## Conflict minimization

All our custom code lives under `commandcenter/`. Upstream never touches this directory.
Conflicts are limited to:
- Files modified for security (telemetry removal)
- Build config (gradle files)
- Kept upstream scripts (if upstream modifies them)

## Future automation

A `upstream-sync.yml` GitHub Action can automate steps 1-5: periodically fetch
upstream, attempt merge into a `sync/upstream-<date>` branch, and open a PR to
`dev` if it succeeds (or alert if conflicts exist).
