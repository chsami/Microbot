#!/usr/bin/env bash
set -euo pipefail

ITERATIONS="${MICROBOT_GE_LUMBRIDGE_ITERATIONS:-10}"
TIMEOUT_MS="${MICROBOT_GE_LUMBRIDGE_TIMEOUT_MS:-5400000}"
LEG_TIMEOUT_MS="${MICROBOT_GE_LUMBRIDGE_LEG_TIMEOUT_MS:-300000}"
OUTPUT_DIR="${MICROBOT_GE_LUMBRIDGE_OUTPUT_DIR:-$HOME/.runelite/test-results/ge-lumbridge-teleport}"
MONITOR_INTERVAL="${MICROBOT_GE_LUMBRIDGE_MONITOR_INTERVAL:-0.2}"
UPSTREAM_PLANNER_SHADOW="${MICROBOT_GE_LUMBRIDGE_UPSTREAM_PLANNER_SHADOW:-false}"

cd "$(dirname "$0")/.."

rm -rf "$OUTPUT_DIR"
mkdir -p "$OUTPUT_DIR"

MONITOR_FILE="$OUTPUT_DIR/monitor.jsonl"
RUN_LOG="$OUTPUT_DIR/run.log"

./gradlew :client:compileJava

CMD=(
  ./gradlew :client:runTest
  -Dmicrobot.test.mode=true
  "-Dmicrobot.test.script=GE Lumbridge Teleport Harness"
  "-Dmicrobot.test.timeout=$TIMEOUT_MS"
  "-Dmicrobot.test.output=$OUTPUT_DIR"
  "-Dmicrobot.test.geLumbridge.iterations=$ITERATIONS"
  "-Dmicrobot.test.geLumbridge.walkTimeoutMs=$LEG_TIMEOUT_MS"
  "-Dmicrobot.test.geLumbridge.upstreamPlannerShadow=$UPSTREAM_PLANNER_SHADOW"
)

set +e
"${CMD[@]}" 2>&1 | tee "$RUN_LOG" &
RUN_PID=$!

(
  while kill -0 "$RUN_PID" 2>/dev/null; do
    STATE="$(timeout 1 ./microbot-cli state 2>/dev/null | jq -c . 2>/dev/null)"
    STATUS=$?
    if [[ "$STATUS" -eq 0 && "$STATE" == \{* ]]; then
      printf '{"sampledAt":"%s","state":%s}\n' "$(date --iso-8601=ns)" "$STATE" >> "$MONITOR_FILE"
    fi
    sleep "$MONITOR_INTERVAL"
  done
) &
MONITOR_PID=$!

wait "$RUN_PID"
STATUS=$?
kill "$MONITOR_PID" 2>/dev/null || true
wait "$MONITOR_PID" 2>/dev/null || true
set -e

RESULT_FILE="$OUTPUT_DIR/result.json"
if [[ -f "$RESULT_FILE" ]]; then
  echo "Result written to $RESULT_FILE"
else
  echo "No result file was written at $RESULT_FILE" >&2
fi

if [[ -f "$MONITOR_FILE" ]]; then
  echo "Monitor written to $MONITOR_FILE"
fi

exit "$STATUS"
