#!/usr/bin/env bash
set -euo pipefail

# Members evidence is shadow-only until its independent gate passes. Do not allow an inherited
# canary/default override to turn this harness into a selection experiment.
export MICROBOT_WEBWALKER_PLANNER_MODE="SHADOW"
export MICROBOT_WEBWALKER_OUTPUT_DIR="${MICROBOT_MEMBERS_WEBWALKER_OUTPUT_DIR:-$HOME/.runelite/test-results/members-webwalker}"

exec "$(dirname "$0")/run-f2p-webwalker-harness.sh" members
