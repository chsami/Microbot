# Walker planner evidence — 2026-08-05

This directory makes the accepted F2P upstream-planner decision evidence reviewable from the repository.
All live inputs are deliberately coordinate-free and omit client logs, screenshots, account/profile data and
session credentials.

The evidence covers the pinned upstream core at
`ff8e961b32120175709df9630ece9468cc11347f`. The performance report identifies the evaluated Microbot
revision as `3d05797e7f768dedcb56762a8fafd7896c9560cd`; later changes that add only this evidence directory do
not alter the evaluated planner code.

## Reproduce the live-shadow aggregate

```bash
scripts/evaluate-walker-shadow-evidence.py \
  docs/evidence/walker/2026-08-05/shadow-input-*.json \
  --json-output /tmp/walker-shadow-evidence.json \
  --markdown-output /tmp/walker-shadow-evidence.md

cmp /tmp/walker-shadow-evidence.json \
  docs/evidence/walker/2026-08-05/shadow-evidence.json
```

The twelve fresh-client inputs reproduce the accepted 141-comparison, 71-arrival aggregate byte-for-byte.

## Reproduce the selection and rollback aggregate

```bash
scripts/evaluate-walker-rollout-evidence.py \
  docs/evidence/walker/2026-08-05/rollout-normal-input.json \
  docs/evidence/walker/2026-08-05/rollout-rollback-input.json \
  --json-output /tmp/walker-rollout-evidence.json \
  --markdown-output /tmp/walker-rollout-evidence.md

cmp /tmp/walker-rollout-evidence.json \
  docs/evidence/walker/2026-08-05/rollout-evidence.json
```

The two inputs are minimal projections of the harness results containing every field consumed by the paired
evaluator. They reproduce its accepted report byte-for-byte while excluding route coordinates and verbose
runtime logs.

## Performance evidence

`performance-evidence.json` is the accepted five-sample aggregate for the exact evaluated planner revision.
The underlying headless reports can be regenerated with the protocol in
`docs/walker-planner-selection-gate.md`; they are not live-account artifacts.

## Scope

These artifacts justify an explicitly approved, match-gated F2P canary. They do not justify members-policy
selection, a permissive upstream-authoritative mode, or changing the default from `LOCAL` without a separate
release decision.
