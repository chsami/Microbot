#!/usr/bin/env bash
set -euo pipefail

CASE_ID="${1:-all}"
TIMEOUT_MS="${MICROBOT_WEBWALKER_TIMEOUT_MS:-1800000}"
LEG_TIMEOUT_MS="${MICROBOT_WEBWALKER_LEG_TIMEOUT_MS:-240000}"
OUTPUT_DIR="${MICROBOT_WEBWALKER_OUTPUT_DIR:-$HOME/.runelite/test-results/f2p-webwalker}"
USE_TELEPORTATION_SPELLS="${MICROBOT_WEBWALKER_USE_TELEPORTATION_SPELLS:-}"
UPSTREAM_PLANNER_SHADOW="${MICROBOT_WEBWALKER_UPSTREAM_PLANNER_SHADOW:-false}"
PLANNER_MODE="${MICROBOT_WEBWALKER_PLANNER_MODE:-}"
FORCE_UPSTREAM_FAILURE="${MICROBOT_WEBWALKER_FORCE_UPSTREAM_FAILURE:-false}"
EXPECT_LOCAL_FALLBACK="${MICROBOT_WEBWALKER_EXPECT_LOCAL_FALLBACK:-false}"

if [[ -z "$PLANNER_MODE" ]]; then
  if [[ "$UPSTREAM_PLANNER_SHADOW" == "true" ]]; then
    PLANNER_MODE="SHADOW"
  else
    PLANNER_MODE="LOCAL"
  fi
fi

cd "$(dirname "$0")/.."

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

./gradlew :client:compileJava

CMD=(
  ./gradlew :client:runTest
  -Dmicrobot.test.mode=true
  "-Dmicrobot.test.script=F2P Web Walker Harness"
  "-Dmicrobot.test.timeout=$TIMEOUT_MS"
  "-Dmicrobot.test.output=$OUTPUT_DIR"
  -Dmicrobot.test.webwalker.stopOnFailure=true
  "-Dmicrobot.test.webwalker.walkTimeoutMs=$LEG_TIMEOUT_MS"
  "-Dmicrobot.test.webwalker.plannerMode=$PLANNER_MODE"
  "-Dmicrobot.test.webwalker.expectLocalFallback=$EXPECT_LOCAL_FALLBACK"
  "-Dmicrobot.test.walker.forceUpstreamPlannerFailure=$FORCE_UPSTREAM_FAILURE"
)

if [[ "$CASE_ID" != "all" ]]; then
  CMD+=("-Dmicrobot.test.webwalker.case=$CASE_ID")
fi

if [[ -n "$USE_TELEPORTATION_SPELLS" ]]; then
  CMD+=("-Dmicrobot.test.webwalker.useTeleportationSpells=$USE_TELEPORTATION_SPELLS")
fi

set +e
"${CMD[@]}"
STATUS=$?
set -e

RESULT_FILE="$OUTPUT_DIR/result.json"
if [[ -f "$RESULT_FILE" ]]; then
  echo "Result written to $RESULT_FILE"
else
  echo "No result file was written at $RESULT_FILE" >&2
fi

exit "$STATUS"
