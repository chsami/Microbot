#!/bin/bash
#
# review-loop.sh
#
# Autonomous code-review loop for Microbot. Every 15 minutes for ~8 hours
# it asks Claude Code to inspect a fresh slice of the codebase looking for
# performance wins, new feature ideas, or simplification opportunities.
# Findings are accumulated in REVIEW_FINDINGS.md, prioritised URGENT/HIGH/
# MEDIUM/LOW. Each iteration consults the existing findings so duplicates
# are skipped.
#
# The goal: launch this before bed, wake up 8 hours later, and have a
# triaged backlog ready to pick from.
#
# Usage:
#   nohup ./scripts/review-loop.sh > logs/review-loop.out 2>&1 &
#   echo $! > logs/review-loop.pid
#
# Tail progress:
#   tail -f logs/review-loop-*.log
#
# Stop early:
#   kill "$(cat logs/review-loop.pid)"
#
# Tunables:
#   MAX_ITER (default 32)         number of review passes
#   INTERVAL_SECONDS (default 900) seconds between iteration starts
#   ITER_TIMEOUT (default 720)    hard cap per iteration in seconds
#
# Safety nets included in this script:
#   - pre-flight check that `claude` is on PATH and reports a version
#   - lockfile so a second instance refuses to start
#   - REVIEW_FINDINGS.md is backed up before every iteration (.bak)
#   - findings file hash is compared before/after each iteration; a
#     loud warning is logged if nothing changed (catches auth failures)
#   - `git status` is snapshotted at startup; any modification outside
#     REVIEW_FINDINGS.md is logged so you can investigate in the morning
#   - per-iteration timeout via `timeout(1)` so a hung agent never
#     burns the rest of the night
#   - the loop never uses `set -e`, so a single iteration failure does
#     NOT abort the remaining iterations
#   - final summary at the end shows total findings added per priority
#

set -uo pipefail

PROJECT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
FINDINGS_FILE="$PROJECT_DIR/REVIEW_FINDINGS.md"
LOG_DIR="$PROJECT_DIR/logs"
LOG_FILE="$LOG_DIR/review-loop-$(date +%Y%m%d-%H%M%S).log"
LOCK_FILE="$LOG_DIR/review-loop.lock"
GIT_BASELINE_FILE="$LOG_DIR/.review-loop-git-baseline.$$"
PROMPT_FILE=""

MAX_ITER="${MAX_ITER:-32}"
INTERVAL_SECONDS="${INTERVAL_SECONDS:-900}"
ITER_TIMEOUT="${ITER_TIMEOUT:-720}"

mkdir -p "$LOG_DIR"

# Logging helper. Defined early so the trap and pre-flight checks can use it.
# stdout still goes through tee so nohup'd output also captures it.
log() {
  echo "[$(date '+%Y-%m-%d %H:%M:%S')] $*" | tee -a "$LOG_FILE"
}

cleanup() {
  local exit_code=$?
  [ -n "${PROMPT_FILE:-}" ] && rm -f "$PROMPT_FILE"
  rm -f "$GIT_BASELINE_FILE"
  # Only remove the lockfile if WE own it. We wrote our PID into it on startup.
  if [ -f "$LOCK_FILE" ] && [ "$(cat "$LOCK_FILE" 2>/dev/null)" = "$$" ]; then
    rm -f "$LOCK_FILE"
  fi
  exit "$exit_code"
}

trap cleanup EXIT
trap 'log "Loop interrupted by signal, exiting"; exit 130' INT TERM

# --- Pre-flight checks ----------------------------------------------------
# Each of these aborts the loop with a CLEAR error before sleeping for 8h.

if ! command -v claude >/dev/null 2>&1; then
  log "FATAL: \`claude\` is not on PATH. Install Claude Code or fix PATH and retry."
  exit 1
fi

claude_version=$(claude --version 2>&1 || true)
if [ -z "$claude_version" ]; then
  log "FATAL: \`claude --version\` returned nothing. Your install looks broken."
  exit 1
fi
log "claude binary OK: $claude_version"

if ! command -v timeout >/dev/null 2>&1; then
  log "FATAL: \`timeout\` is not on PATH (install coreutils)."
  exit 1
fi

# Lockfile: refuse to start if another instance is already running.
if [ -f "$LOCK_FILE" ]; then
  existing_pid=$(cat "$LOCK_FILE" 2>/dev/null || true)
  if [ -n "$existing_pid" ] && kill -0 "$existing_pid" 2>/dev/null; then
    log "FATAL: another review-loop is already running (pid $existing_pid)."
    log "       Either wait for it to finish or kill it: kill $existing_pid"
    # Prevent cleanup() from deleting the other instance's lockfile.
    LOCK_FILE=""
    exit 1
  else
    log "WARNING: stale lockfile from pid $existing_pid; reclaiming"
    rm -f "$LOCK_FILE"
  fi
fi
echo "$$" > "$LOCK_FILE"

# Allocate the prompt scratch file and verify it.
PROMPT_FILE=$(mktemp "${TMPDIR:-/tmp}/review-loop-prompt.XXXXXX") || {
  log "FATAL: mktemp failed"; exit 1;
}
if [ -z "$PROMPT_FILE" ] || [ ! -w "$PROMPT_FILE" ]; then
  log "FATAL: prompt scratch file is empty or unwritable"
  exit 1
fi

cd "$PROJECT_DIR" || { log "FATAL: cannot cd to $PROJECT_DIR"; exit 1; }

# Initialise the findings file on the very first run.
if [ ! -f "$FINDINGS_FILE" ]; then
  cat > "$FINDINGS_FILE" <<'EOF'
# Microbot Review Findings

Autonomous review loop output. Read this in the morning and pick what to
implement. Items at the top of each section are most actionable.

## How to use

- **URGENT** — crashes, client-thread blocking, leaks, security, data loss
- **HIGH** — significant performance wins or features users genuinely need
- **MEDIUM** — code simplification, refactor opportunities, dead code
- **LOW** — small cleanups, minor improvements, nice-to-haves

Each finding has a file:line reference, the issue, the proposed fix, and
the expected impact. Once a finding is implemented, prefix its title with
`[DONE]` so the loop knows not to revisit it.

## Reviewed Areas

<!-- The loop appends one line per iteration here so future iterations
     can rotate to a fresh slice of the codebase. Format:
     - <area> (iter N, YYYY-MM-DD HH:MM) — N findings -->

## URGENT

## HIGH

## MEDIUM

## LOW

## Notes

<!-- The loop drops a line here when an iteration finds nothing new. -->
EOF
  log "Initialised $FINDINGS_FILE"
fi

if [ ! -w "$FINDINGS_FILE" ]; then
  log "FATAL: $FINDINGS_FILE is not writable"
  exit 1
fi

# Snapshot git state so we can detect if the agent went off-script and
# modified anything outside REVIEW_FINDINGS.md. If git is unavailable
# or we're not inside a worktree, this just degrades to a warning.
if command -v git >/dev/null 2>&1 && git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  git status --porcelain > "$GIT_BASELINE_FILE" 2>/dev/null || true
  log "git baseline captured ($(wc -l < "$GIT_BASELINE_FILE") pre-existing entries)"
else
  log "WARNING: git unavailable; off-script edits will not be detected"
  : > "$GIT_BASELINE_FILE"
fi

# Helper: count findings in each priority section. Used by the final summary
# and the per-iteration delta check.
count_findings() {
  awk '
    /^## URGENT$/   { sec="URGENT";  next }
    /^## HIGH$/     { sec="HIGH";    next }
    /^## MEDIUM$/   { sec="MEDIUM";  next }
    /^## LOW$/      { sec="LOW";     next }
    /^## /          { sec="";        next }
    /^### /         { if (sec != "") counts[sec]++ }
    END {
      printf "URGENT=%d HIGH=%d MEDIUM=%d LOW=%d\n",
        counts["URGENT"]+0, counts["HIGH"]+0, counts["MEDIUM"]+0, counts["LOW"]+0
    }
  ' "$1" 2>/dev/null || echo "URGENT=0 HIGH=0 MEDIUM=0 LOW=0"
}

baseline_counts=$(count_findings "$FINDINGS_FILE")
log "starting findings: $baseline_counts"

stale_iterations=0

# Build the prompt once per iteration so $iter is interpolated correctly.
build_prompt() {
  local iter="$1"
  local total="$2"

  cat > "$PROMPT_FILE" <<PROMPT_EOF
You are running iteration $iter of $total in an autonomous review loop over
the Microbot codebase. Your job: identify ONE area of the codebase to
inspect, find a small number of concrete improvement opportunities there,
and append them to REVIEW_FINDINGS.md. The user will read that file in the
morning and pick what to implement.

Project dir: $PROJECT_DIR
Findings file: $FINDINGS_FILE

CRITICAL CONSTRAINTS

1. DO NOT modify any source code. The ONLY file you may edit is
   REVIEW_FINDINGS.md. No code changes, no new files anywhere else.

2. DO NOT propose anything already in REVIEW_FINDINGS.md. Read the file
   FIRST. Check the "Reviewed Areas" section to see what has been covered,
   and skim the existing findings under URGENT/HIGH/MEDIUM/LOW so you
   don't restate them. Findings prefixed with [DONE] are already shipped
   — treat them as off-limits too.

3. If you have nothing genuinely new to add this iteration, append a
   single dated bullet to the "## Notes" section explaining why and stop.
   A duplicate finding wastes the user's morning; an honest "nothing new
   in util/bank this pass" is fine.

4. Each finding MUST reference exact file paths with line numbers. Be
   concrete. "Refactor this" is useless. "Replace the synchronous HTTP
   call at MicrobotPluginClient.java:142 with a CompletableFuture so the
   plugin hub UI thread doesn't stall on slow networks" is useful.

PROCESS

Step 1 — Read REVIEW_FINDINGS.md end to end. Note which areas are listed
under "Reviewed Areas" and roughly what categories of issue have already
been raised in each section.

Step 2 — Pick ONE area not yet covered. Suggested rotation (skip any that
appear in "Reviewed Areas"):

  - runelite-client/.../microbot/util/bank
  - runelite-client/.../microbot/util/inventory
  - runelite-client/.../microbot/util/equipment
  - runelite-client/.../microbot/util/walker
  - runelite-client/.../microbot/util/cache
  - runelite-client/.../microbot/util/coords
  - runelite-client/.../microbot/util/npc
  - runelite-client/.../microbot/api/tileobject
  - runelite-client/.../microbot/util/grounditem
  - runelite-client/.../microbot/util/combat
  - runelite-client/.../microbot/util/magic
  - runelite-client/.../microbot/util/prayer
  - runelite-client/.../microbot/util/widget
  - runelite-client/.../microbot/util/dialogues
  - runelite-client/.../microbot/util/tabs
  - runelite-client/.../microbot/util/loginscreen
  - runelite-client/.../microbot/util/antiban
  - runelite-client/.../microbot/util/mouse
  - runelite-client/.../microbot/util/keyboard
  - runelite-client/.../microbot/util/grandexchange
  - runelite-client/.../microbot/util/shop
  - runelite-client/.../microbot/util/depositbox
  - runelite-client/.../microbot/util/farming
  - runelite-client/.../microbot/util/poh
  - runelite-client/.../microbot/api (queryable API)
  - runelite-client/.../microbot/agentserver
  - runelite-client/.../microbot/testing
  - runelite-client/.../microbot/ui (panels including MicrobotPluginHubPanel)
  - runelite-client/.../microbot/breakhandler
  - runelite-client/.../microbot/externalplugins (MicrobotPluginManager)
  - core: Microbot.java, Script.java, BlockingEventManager
  - microbot-cli (the bash CLI script + its server-side handlers)

If every area on the list has already been covered, revisit the area with
the FEWEST findings recorded in REVIEW_FINDINGS.md and look for a
DIFFERENT category (perf vs feature vs simplification) than was raised
last time.

Step 3 — Read between 3 and 8 source files in that area. Use Glob and
Grep to navigate; do not waste budget reading the same file twice. If a
file is enormous (Rs2Bank, Rs2Walker, Rs2Inventory) read targeted
sections, not the whole file.

Step 4 — Identify between 0 and 5 concrete findings. Look for:

  PERFORMANCE
  - blocking the client thread (sleep/IO/heavy CPU on the EDT or client thread)
  - per-tick recomputation that should be cached
  - leaky caches (no eviction, growing forever)
  - quadratic loops over scene-sized collections
  - repeated I/O or repeated reflection lookups
  - excessive allocations in tight loops

  NEW FEATURES
  - missing helpers users keep reimplementing in plugins
  - gaps in coverage of game subsystems (e.g. a tab/widget/skill with no Rs2 wrapper)
  - ergonomic improvements to common APIs (named params, builders, fluent chains)
  - missing convenience for the queryable API

  SIMPLIFICATION
  - dead code, commented-out blocks, unused fields
  - duplicated logic across utilities that should share a helper
  - over-abstracted single-use interfaces
  - legacy static APIs that have a queryable replacement (and vice versa)
  - inconsistent naming or signatures across similar utilities

Step 5 — For each finding, decide its priority:

  URGENT  client-thread freezes, deadlocks, memory leaks, crashes,
          security/credential issues, anything that can lose user data.
  HIGH    significant perf wins (>= ~10% in a hot path), broadly useful
          new helpers, fixes for issues that bite many users.
  MEDIUM  refactors, simplifications, dead-code removal, ergonomic API
          improvements that mostly benefit plugin authors.
  LOW     cosmetic cleanups, minor renamings, tiny convenience methods.

Step 6 — Append each new finding to the matching section of
REVIEW_FINDINGS.md using exactly this format (keep the leading ###):

   ### <short imperative title>
   - **File(s):** path/to/File.java:NN-MM (and any other files involved)
   - **Type:** performance | feature | simplification
   - **Found:** iter $iter
   - **Issue:** 1-3 sentences describing the problem precisely.
   - **Fix:** 1-3 sentences describing the proposed change.
   - **Impact:** who benefits and roughly how much (e.g. "removes a
     ~200ms freeze whenever the bank panel opens", "lets plugin authors
     replace 6 lines of boilerplate with one helper call").

New findings go at the TOP of their priority section (most recent first).
Do not reorder existing findings.

Step 7 — Append one bullet to the "## Reviewed Areas" section using this
format (one line, no wrapping):

   - <area path or name> (iter $iter, $(date '+%Y-%m-%d %H:%M')) — N findings

Where N is the number of findings you actually added this iteration. If
you added zero, also drop one bullet under "## Notes" explaining why.

Step 8 — Stop. Do not start another area in the same iteration. Do not
edit anything other than REVIEW_FINDINGS.md.

Begin now by reading REVIEW_FINDINGS.md.
PROMPT_EOF
}

log "===== Microbot review loop starting ====="
log "Project dir: $PROJECT_DIR"
log "Findings file: $FINDINGS_FILE"
log "Iterations: $MAX_ITER, interval: ${INTERVAL_SECONDS}s, per-iter timeout: ${ITER_TIMEOUT}s"
log "Log file: $LOG_FILE"

for iter in $(seq 1 "$MAX_ITER"); do
  start_epoch=$(date +%s)
  log "--- iteration $iter/$MAX_ITER starting ---"

  # Back up findings file so a corrupt write is recoverable.
  cp "$FINDINGS_FILE" "$FINDINGS_FILE.bak" 2>/dev/null || \
    log "WARNING: could not back up findings file"

  before_hash=$(md5sum "$FINDINGS_FILE" 2>/dev/null | cut -d' ' -f1)

  build_prompt "$iter" "$MAX_ITER"

  # `bash -c` so the pipeline (cat | claude) is the child of timeout(1),
  # otherwise timeout would only kill cat. --verbose is intentionally OFF
  # so 32 iterations don't blow up the log file.
  timeout "$ITER_TIMEOUT" bash -c '
    cat "$1" | claude -p \
      --allowedTools "Read,Edit,Write,Glob,Grep" \
      --model sonnet
  ' _ "$PROMPT_FILE" >> "$LOG_FILE" 2>&1
  iter_status=$?

  if [ $iter_status -eq 0 ]; then
    log "iteration $iter/$MAX_ITER finished cleanly"
  elif [ $iter_status -eq 124 ]; then
    log "iteration $iter/$MAX_ITER hit ${ITER_TIMEOUT}s timeout (kept partial output)"
  elif [ $iter_status -eq 127 ]; then
    log "iteration $iter/$MAX_ITER: command not found inside timeout — claude vanished?"
  else
    log "iteration $iter/$MAX_ITER exited with status $iter_status"
  fi

  # Did the agent actually write anything new?
  after_hash=$(md5sum "$FINDINGS_FILE" 2>/dev/null | cut -d' ' -f1)
  if [ "$before_hash" = "$after_hash" ]; then
    stale_iterations=$((stale_iterations + 1))
    log "WARNING: REVIEW_FINDINGS.md unchanged this iteration (stale streak: $stale_iterations)"
    if [ "$iter" -eq 1 ]; then
      log "WARNING: very first iteration produced nothing. Likely causes:"
      log "         - claude session expired (run \`claude\` interactively to refresh)"
      log "         - network/API outage"
      log "         - prompt was rejected"
      log "         The loop will keep retrying every ${INTERVAL_SECONDS}s."
    fi
    if [ "$stale_iterations" -ge 3 ]; then
      log "WARNING: $stale_iterations consecutive stale iterations. Consider killing the loop and investigating logs/."
    fi
  else
    if [ "$stale_iterations" -gt 0 ]; then
      log "stale streak broken after $stale_iterations iteration(s)"
    fi
    stale_iterations=0
    log "findings now: $(count_findings "$FINDINGS_FILE")"
  fi

  # Did the agent edit anything outside REVIEW_FINDINGS.md?
  if [ -s "$GIT_BASELINE_FILE" ] || command -v git >/dev/null 2>&1; then
    current_git=$(git status --porcelain 2>/dev/null || true)
    baseline_git=$(cat "$GIT_BASELINE_FILE" 2>/dev/null || true)
    if [ "$current_git" != "$baseline_git" ]; then
      unexpected=$(diff <(printf '%s\n' "$baseline_git") <(printf '%s\n' "$current_git") 2>/dev/null \
        | grep '^>' | sed 's/^> //' \
        | grep -vE '(REVIEW_FINDINGS\.md|REVIEW_FINDINGS\.md\.bak|\.review-loop|review-loop-)' \
        || true)
      if [ -n "$unexpected" ]; then
        log "WARNING: agent touched files outside REVIEW_FINDINGS.md this iteration:"
        while IFS= read -r line; do
          [ -n "$line" ] && log "    $line"
        done <<< "$unexpected"
        log "         Review these changes in the morning before doing anything else."
      fi
    fi
  fi

  if [ "$iter" -lt "$MAX_ITER" ]; then
    now_epoch=$(date +%s)
    elapsed=$((now_epoch - start_epoch))
    sleep_for=$((INTERVAL_SECONDS - elapsed))
    if [ "$sleep_for" -gt 0 ]; then
      log "sleeping ${sleep_for}s until next iteration"
      sleep "$sleep_for"
    else
      log "iteration overran by $((-sleep_for))s, starting next immediately"
    fi
  fi
done

# --- Final summary --------------------------------------------------------
final_counts=$(count_findings "$FINDINGS_FILE")
log "===== Microbot review loop complete ====="
log "Findings file: $FINDINGS_FILE"
log "Baseline:  $baseline_counts"
log "Final:     $final_counts"
if [ "$baseline_counts" = "$final_counts" ]; then
  log "WARNING: nothing was added across $MAX_ITER iterations. Check $LOG_FILE for clues."
fi
