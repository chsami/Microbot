#!/usr/bin/env python3
"""Evaluate one or more fresh-client walker shadow snapshots against the live rollout gate."""

from __future__ import annotations

import argparse
import copy
import json
import sys
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_BASELINE = Path(__file__).with_name("shortest-path-upstream-baseline.json")
DEFAULT_REQUIRED_COVERAGE = {
    "ACTIVE_ROUTE": 75,
    "ACTIVE_REPLAN": 15,
    "RECOVERY_REPLAN": 10,
    "SURFACE_COORDINATES_ONLY": 60,
    "UNDERGROUND_COORDINATES": 20,
    "WALKING_ONLY_SELECTED": 10,
    "USES_TRANSPORT": 20,
    "SELECTS_ITEM_GATED_TRANSPORT": 5,
    "BANK_ROUTE_FROM_BANK": 10,
    "BANK_ROUTE_FROM_BANK_SELECTS_ITEM_GATED_TRANSPORT": 5,
    "LIVE_COLLISION_CONSULTED": 25,
}
DEFAULT_MINIMUM_DISTINCT_TRANSPORT_EXECUTORS = 4
DEFAULT_MINIMUM_WALKER_ARRIVALS = 50
DEFAULT_MINIMUM_RECOVERY_ARRIVALS = 5
DEFAULT_REQUIRED_EXECUTOR_GROUPS = {
    "LOCAL_TRANSITION": {
        "minimum": 5,
        "executors": ("OBJECT", "BARROWS_DIG"),
    },
    "TELEPORT": {
        "minimum": 5,
        "executors": (
            "ITEM_TELEPORT",
            "MINIGAME_TELEPORT",
            "SPELL_TELEPORT",
            "POH",
            "SEASONAL",
        ),
    },
    "NETWORK": {
        "minimum": 5,
        "executors": (
            "CANOE",
            "FAIRY_RING",
            "GNOME_GLIDER",
            "HOT_AIR_BALLOON",
            "MAGIC_CARPET",
            "MAGIC_MUSHTREE",
            "QUETZAL",
            "SPIRIT_TREE",
            "WILDERNESS_OBELISK",
        ),
    },
    "TERMINAL_TRAVEL": {
        "minimum": 3,
        "executors": ("CHARTER_SHIP", "TERMINAL_TRAVEL"),
    },
}

MEMBERS_REQUIRED_COVERAGE = {
    "ACTIVE_ROUTE": 20,
    "MEMBERS_WORLD_POLICY": 30,
    "USES_TRANSPORT": 15,
    "SELECTS_MEMBERS_TRANSPORT": 5,
    "SELECTS_NON_ITEM_REQUIREMENT_GATED_TRANSPORT": 5,
}
MEMBERS_REQUIRED_EXECUTOR_GROUPS = {
    "MEMBERS_NETWORK": {
        "minimum": 5,
        "executors": (
            "FAIRY_RING",
            "GNOME_GLIDER",
            "HOT_AIR_BALLOON",
            "MAGIC_CARPET",
            "MAGIC_MUSHTREE",
            "QUETZAL",
            "SPIRIT_TREE",
            "TERMINAL_TRAVEL",
            "WILDERNESS_OBELISK",
        ),
    },
}
MEMBERS_MINIMUM_COMPLETED = 30
MEMBERS_MINIMUM_DISTINCT_TRANSPORT_EXECUTORS = 2
MEMBERS_MINIMUM_WALKER_ARRIVALS = 10
MEMBERS_MINIMUM_RECOVERY_ARRIVALS = 1
MEMBERS_MINIMUM_SESSIONS = 3


def load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path}: expected a JSON object")
    return value


def non_negative_int(mapping: dict[str, Any], key: str, prefix: str) -> int:
    value = mapping.get(key)
    if not isinstance(value, int) or isinstance(value, bool) or value < 0:
        raise ValueError(f"{prefix}.{key} must be a non-negative integer")
    return value


def validate_terminal_diagnostic(
    value: Any,
    *,
    field: str,
    expected_status: str,
    expected_engine: str,
    failures: list[str],
) -> None:
    if not isinstance(value, dict):
        raise ValueError(f"snapshot.{field} must be an object or null")
    if value.get("status") != expected_status:
        failures.append(f"{field}.status must be {expected_status}")
    if value.get("shadowEngineId") != expected_engine:
        failures.append(f"{field}.shadowEngineId does not match the candidate engine")
    for key in (
        "terminationMatches",
        "endpointMatches",
        "costComparable",
        "costMatches",
        "selectedTransportsMatch",
        "pathMatches",
    ):
        if not isinstance(value.get(key), bool):
            failures.append(f"{field}.{key} must be a boolean")

    failure_type = value.get("failureType")
    if expected_status == "DIVERGENCE":
        if failure_type is not None:
            failures.append(f"{field}.failureType must be null for a divergence")
        semantic_fields = (
            "terminationMatches",
            "endpointMatches",
            "costComparable",
            "costMatches",
            "selectedTransportsMatch",
        )
        if all(value.get(key) is True for key in semantic_fields):
            failures.append(
                f"{field} does not contain a termination, endpoint, cost or transport mismatch"
            )
    elif not isinstance(failure_type, str) or not failure_type:
        failures.append(f"{field}.failureType must name the failed exception class")


def evaluate(
    snapshot: dict[str, Any],
    reviewed_commit: str,
    *,
    minimum_completed: int = 100,
    required_coverage: dict[str, int] | None = None,
    minimum_distinct_transport_executors: int = DEFAULT_MINIMUM_DISTINCT_TRANSPORT_EXECUTORS,
    required_executor_groups: dict[str, dict[str, Any]] | None = None,
    minimum_walker_arrivals: int = DEFAULT_MINIMUM_WALKER_ARRIVALS,
    minimum_recovery_arrivals: int = DEFAULT_MINIMUM_RECOVERY_ARRIVALS,
    minimum_sessions: int = 1,
    expected_planner_mode: str | None = None,
    evidence_profile: str = "f2p",
) -> dict[str, Any]:
    if snapshot.get("schemaVersion") != 2:
        raise ValueError("snapshot.schemaVersion must be 2")
    if len(reviewed_commit) != 40:
        raise ValueError("reviewed_commit must be a full 40-character revision")
    if minimum_completed <= 0:
        raise ValueError("minimum_completed must be positive")
    if minimum_distinct_transport_executors <= 0:
        raise ValueError("minimum_distinct_transport_executors must be positive")
    if minimum_walker_arrivals <= 0 or minimum_recovery_arrivals <= 0:
        raise ValueError("walker arrival requirements must be positive")
    if minimum_sessions <= 0:
        raise ValueError("minimum_sessions must be positive")
    requirements = dict(
        DEFAULT_REQUIRED_COVERAGE if required_coverage is None else required_coverage
    )
    if any(not isinstance(value, int) or value < 0 for value in requirements.values()):
        raise ValueError("coverage requirements must be non-negative integers")
    executor_group_requirements = dict(
        DEFAULT_REQUIRED_EXECUTOR_GROUPS
        if required_executor_groups is None
        else required_executor_groups
    )

    failures: list[str] = []
    shortfalls: list[str] = []
    warnings: list[str] = []
    expected_engine = f"shortest-path-upstream@{reviewed_commit}"
    actual_engine = snapshot.get("candidateEngineId")
    if actual_engine != expected_engine:
        failures.append(
            f"candidateEngineId={actual_engine!r}, expected {expected_engine!r}"
        )
    if snapshot.get("enabled") is not True:
        shortfalls.append("upstream planner shadow mode was not enabled at capture")
    planner_mode = snapshot.get("plannerMode")
    if expected_planner_mode is not None and planner_mode != expected_planner_mode:
        failures.append(
            f"plannerMode={planner_mode!r}, expected {expected_planner_mode!r}"
        )
    started_at_epoch_millis = non_negative_int(
        snapshot, "startedAtEpochMillis", "snapshot"
    )
    session_count = snapshot.get("sessionCount", 1)
    if not isinstance(session_count, int) or isinstance(session_count, bool) or session_count <= 0:
        raise ValueError("snapshot.sessionCount must be a positive integer")
    if session_count < minimum_sessions:
        shortfalls.append(
            f"only {session_count} fresh client session(s); {minimum_sessions} required"
        )

    totals = snapshot.get("totals")
    if not isinstance(totals, dict):
        raise ValueError("snapshot has no totals object")
    submitted = non_negative_int(totals, "submitted", "totals")
    completed = non_negative_int(totals, "completed", "totals")
    matches = non_negative_int(totals, "matches", "totals")
    divergences = non_negative_int(totals, "divergences", "totals")
    planner_failures = non_negative_int(totals, "failures", "totals")
    stale = non_negative_int(totals, "staleResults", "totals")
    discarded = non_negative_int(totals, "discarded", "totals")
    pending = non_negative_int(totals, "pending", "totals")
    route_shape_differences = non_negative_int(
        totals, "routeShapeDifferences", "totals"
    )
    if matches + divergences + planner_failures != completed:
        failures.append(
            "totals.completed does not equal matches + divergences + failures"
        )
    if submitted != completed + discarded + pending:
        failures.append("submitted does not equal completed + discarded + pending")
    if divergences:
        failures.append(f"observed {divergences} unexplained planner divergence(s)")
    if planner_failures:
        failures.append(f"observed {planner_failures} upstream planner failure(s)")
    if completed < minimum_completed:
        shortfalls.append(
            f"only {completed} completed comparison(s); {minimum_completed} required"
        )
    if pending:
        shortfalls.append(
            f"{pending} shadow comparison(s) still pending; capture after the queue settles"
        )
    if discarded:
        warnings.append(
            f"{discarded} queued comparison(s) were discarded by the bounded sampler"
        )
    if stale:
        warnings.append(
            f"{stale} completed result(s) belonged to superseded route generations"
        )
    if route_shape_differences:
        warnings.append(
            f"{route_shape_differences} completed comparison(s) used a different exact "
            "route shape"
        )
    latest_route_shape_difference = snapshot.get("latestRouteShapeDifference")
    if route_shape_differences == 0 and latest_route_shape_difference is not None:
        failures.append(
            "latestRouteShapeDifference is present while totals.routeShapeDifferences is zero"
        )
    elif route_shape_differences and latest_route_shape_difference is None:
        warnings.append(
            "route-shape differences have no preserved coordinate-free comparison; "
            "capture evidence from a client containing the current diagnostic field"
        )
    elif latest_route_shape_difference is not None:
        if not isinstance(latest_route_shape_difference, dict):
            raise ValueError("snapshot.latestRouteShapeDifference must be an object or null")
        if latest_route_shape_difference.get("status") != "MATCH":
            failures.append("latestRouteShapeDifference.status must be MATCH")
        if latest_route_shape_difference.get("shadowEngineId") != expected_engine:
            failures.append(
                "latestRouteShapeDifference.shadowEngineId does not match the candidate engine"
            )
        for key in (
            "terminationMatches",
            "endpointMatches",
            "costComparable",
            "costMatches",
            "selectedTransportsMatch",
        ):
            if latest_route_shape_difference.get(key) is not True:
                failures.append(f"latestRouteShapeDifference.{key} must be true")
        if latest_route_shape_difference.get("pathMatches") is not False:
            failures.append("latestRouteShapeDifference.pathMatches must be false")

    latest_divergence = snapshot.get("latestDivergence")
    if divergences == 0 and latest_divergence is not None:
        failures.append(
            "latestDivergence is present while totals.divergences is zero"
        )
    elif divergences and latest_divergence is None:
        failures.append(
            "planner divergences have no preserved coordinate-free diagnostic"
        )
    elif latest_divergence is not None:
        validate_terminal_diagnostic(
            latest_divergence,
            field="latestDivergence",
            expected_status="DIVERGENCE",
            expected_engine=expected_engine,
            failures=failures,
        )

    latest_failure = snapshot.get("latestFailure")
    if planner_failures == 0 and latest_failure is not None:
        failures.append("latestFailure is present while totals.failures is zero")
    elif planner_failures and latest_failure is None:
        failures.append(
            "upstream planner failures have no preserved coordinate-free diagnostic"
        )
    elif latest_failure is not None:
        validate_terminal_diagnostic(
            latest_failure,
            field="latestFailure",
            expected_status="FAILED",
            expected_engine=expected_engine,
            failures=failures,
        )

    coverage = snapshot.get("coverage")
    if not isinstance(coverage, dict):
        raise ValueError("snapshot has no coverage object")
    coverage_rows: list[dict[str, Any]] = []
    for tag, required in requirements.items():
        raw = coverage.get(tag)
        if not isinstance(raw, dict):
            failures.append(f"coverage.{tag} is missing")
            coverage_rows.append(
                {
                    "tag": tag,
                    "required": required,
                    "completed": 0,
                    "matches": 0,
                    "divergences": 0,
                    "failures": 0,
                    "status": "MISSING",
                }
            )
            continue
        tag_completed = non_negative_int(raw, "completed", f"coverage.{tag}")
        tag_matches = non_negative_int(raw, "matches", f"coverage.{tag}")
        tag_divergences = non_negative_int(raw, "divergences", f"coverage.{tag}")
        tag_failures = non_negative_int(raw, "failures", f"coverage.{tag}")
        if tag_matches + tag_divergences + tag_failures != tag_completed:
            failures.append(
                f"coverage.{tag}.completed does not equal its outcome counters"
            )
        if tag_divergences:
            failures.append(
                f"coverage.{tag} contains {tag_divergences} divergence(s)"
            )
        if tag_failures:
            failures.append(f"coverage.{tag} contains {tag_failures} failure(s)")
        if tag_completed < required:
            shortfalls.append(
                f"coverage.{tag} has {tag_completed} completed comparison(s); "
                f"{required} required"
            )
        coverage_rows.append(
            {
                "tag": tag,
                "required": required,
                "completed": tag_completed,
                "matches": tag_matches,
                "divergences": tag_divergences,
                "failures": tag_failures,
                "status": (
                    "FAIL"
                    if tag_divergences or tag_failures
                    else "PASS" if tag_completed >= required else "INSUFFICIENT"
                ),
            }
        )

    transport_executors = snapshot.get("transportExecutors")
    if not isinstance(transport_executors, dict):
        raise ValueError("snapshot has no transportExecutors object")
    executor_outcomes: dict[str, dict[str, int]] = {}
    for executor, raw in transport_executors.items():
        if not isinstance(executor, str) or not isinstance(raw, dict):
            raise ValueError("transportExecutors must map names to outcome objects")
        executor_completed = non_negative_int(
            raw, "completed", f"transportExecutors.{executor}"
        )
        executor_matches = non_negative_int(
            raw, "matches", f"transportExecutors.{executor}"
        )
        executor_divergences = non_negative_int(
            raw, "divergences", f"transportExecutors.{executor}"
        )
        executor_failures = non_negative_int(
            raw, "failures", f"transportExecutors.{executor}"
        )
        if executor_matches + executor_divergences + executor_failures != executor_completed:
            failures.append(
                f"transportExecutors.{executor}.completed does not equal its outcome counters"
            )
        if executor_divergences:
            failures.append(
                f"transportExecutors.{executor} contains "
                f"{executor_divergences} divergence(s)"
            )
        if executor_failures:
            failures.append(
                f"transportExecutors.{executor} contains {executor_failures} failure(s)"
            )
        executor_outcomes[executor] = {
            "completed": executor_completed,
            "matches": executor_matches,
            "divergences": executor_divergences,
            "failures": executor_failures,
        }

    observed_executors = sorted(
        executor
        for executor, outcomes in executor_outcomes.items()
        if outcomes["completed"] > 0
    )
    if len(observed_executors) < minimum_distinct_transport_executors:
        shortfalls.append(
            f"only {len(observed_executors)} distinct transport executor(s) observed; "
            f"{minimum_distinct_transport_executors} required"
        )
    executor_group_rows: list[dict[str, Any]] = []
    for group, raw_requirement in executor_group_requirements.items():
        if not isinstance(raw_requirement, dict):
            raise ValueError(f"executor group {group} must be an object")
        minimum = raw_requirement.get("minimum")
        executors = raw_requirement.get("executors")
        if not isinstance(minimum, int) or isinstance(minimum, bool) or minimum < 0:
            raise ValueError(f"executor group {group}.minimum must be non-negative")
        if not isinstance(executors, (list, tuple)) or not all(
            isinstance(value, str) for value in executors
        ):
            raise ValueError(f"executor group {group}.executors must be names")
        group_completed = sum(
            executor_outcomes.get(executor, {}).get("completed", 0)
            for executor in executors
        )
        if group_completed < minimum:
            shortfalls.append(
                f"transport executor group {group} has {group_completed} completed "
                f"comparison(s); {minimum} required"
            )
        executor_group_rows.append(
            {
                "group": group,
                "executors": list(executors),
                "completed": group_completed,
                "required": minimum,
                "status": "PASS" if group_completed >= minimum else "INSUFFICIENT",
            }
        )

    execution = snapshot.get("execution")
    if not isinstance(execution, dict):
        raise ValueError("snapshot has no execution object")
    terminal = non_negative_int(execution, "terminal", "execution")
    arrived = non_negative_int(execution, "arrived", "execution")
    unreachable = non_negative_int(execution, "unreachable", "execution")
    exited = non_negative_int(execution, "exited", "execution")
    recovery_terminal = non_negative_int(
        execution, "recoveryTerminal", "execution"
    )
    recovery_arrived = non_negative_int(
        execution, "recoveryArrived", "execution"
    )
    recovery_unreachable = non_negative_int(
        execution, "recoveryUnreachable", "execution"
    )
    recovery_exited = non_negative_int(execution, "recoveryExited", "execution")
    if arrived + unreachable + exited != terminal:
        failures.append("execution.terminal does not equal its outcome counters")
    if recovery_arrived + recovery_unreachable + recovery_exited != recovery_terminal:
        failures.append(
            "execution.recoveryTerminal does not equal its outcome counters"
        )
    if recovery_terminal > terminal or recovery_arrived > arrived:
        failures.append("recovery execution outcomes are not a subset of all outcomes")
    if arrived < minimum_walker_arrivals:
        shortfalls.append(
            f"only {arrived} blocking walk arrival(s); {minimum_walker_arrivals} required"
        )
    if recovery_arrived < minimum_recovery_arrivals:
        shortfalls.append(
            f"only {recovery_arrived} recovered walk arrival(s); "
            f"{minimum_recovery_arrivals} required"
        )
    if recovery_unreachable:
        failures.append(
            f"observed {recovery_unreachable} recovery-triggered unreachable walk(s)"
        )
    if recovery_exited:
        failures.append(f"observed {recovery_exited} recovery-triggered exited walk(s)")
    if unreachable:
        warnings.append(f"observed {unreachable} terminal unreachable walk(s)")
    if exited:
        warnings.append(f"observed {exited} terminal exited walk(s)")

    if failures:
        verdict = "REJECTED"
    elif shortfalls:
        verdict = "INSUFFICIENT_EVIDENCE"
    else:
        verdict = "ACCEPTED"
    return {
        "schemaVersion": 1,
        "evidenceProfile": evidence_profile,
        "verdict": verdict,
        "candidateEngineId": actual_engine,
        "reviewedCommit": reviewed_commit,
        "startedAtEpochMillis": started_at_epoch_millis,
        "plannerMode": planner_mode,
        "sessionCount": session_count,
        "minimumSessions": minimum_sessions,
        "minimumCompleted": minimum_completed,
        "totals": dict(totals),
        "coverage": coverage_rows,
        "transportExecutors": executor_outcomes,
        "observedTransportExecutors": observed_executors,
        "minimumDistinctTransportExecutors": minimum_distinct_transport_executors,
        "transportExecutorGroups": executor_group_rows,
        "execution": dict(execution),
        "minimumWalkerArrivals": minimum_walker_arrivals,
        "minimumRecoveryArrivals": minimum_recovery_arrivals,
        "latestRouteShapeDifference": latest_route_shape_difference,
        "latestDivergence": latest_divergence,
        "latestFailure": latest_failure,
        "failures": failures,
        "evidenceShortfalls": shortfalls,
        "warnings": warnings,
    }


def merge_snapshots(
    snapshots: list[dict[str, Any]],
    reviewed_commit: str,
    *,
    expected_planner_mode: str | None = None,
) -> dict[str, Any]:
    if not snapshots:
        raise ValueError("at least one snapshot is required")

    expected_engine = f"shortest-path-upstream@{reviewed_commit}"
    starts: list[int] = []
    zero_coverage = {tag: 0 for tag in DEFAULT_REQUIRED_COVERAGE}
    zero_groups = {
        group: {"minimum": 0, "executors": requirement["executors"]}
        for group, requirement in DEFAULT_REQUIRED_EXECUTOR_GROUPS.items()
    }
    for index, snapshot in enumerate(snapshots, start=1):
        session_report = evaluate(
            snapshot,
            reviewed_commit,
            minimum_completed=1,
            required_coverage=zero_coverage,
            minimum_distinct_transport_executors=1,
            required_executor_groups=zero_groups,
            minimum_walker_arrivals=1,
            minimum_recovery_arrivals=1,
            expected_planner_mode=expected_planner_mode,
        )
        if session_report["failures"]:
            raise ValueError(
                f"snapshot {index} is internally invalid: "
                + "; ".join(session_report["failures"])
            )
        if snapshot.get("candidateEngineId") != expected_engine:
            raise ValueError(
                f"snapshot {index} candidateEngineId does not match {expected_engine}"
            )
        starts.append(
            non_negative_int(snapshot, "startedAtEpochMillis", f"snapshot[{index}]")
        )

    if len(set(starts)) != len(starts):
        raise ValueError(
            "duplicate startedAtEpochMillis values would count the same client session twice"
        )

    merged = copy.deepcopy(snapshots[0])
    merged["enabled"] = all(snapshot.get("enabled") is True for snapshot in snapshots)
    merged["candidateEngineId"] = expected_engine
    if expected_planner_mode is not None:
        merged["plannerMode"] = expected_planner_mode
    merged["startedAtEpochMillis"] = min(starts)
    merged["sessionCount"] = len(snapshots)
    merged["sessionStartedAtEpochMillis"] = starts

    def sum_named_objects(container: str) -> dict[str, dict[str, int]]:
        names: set[str] = set()
        for index, snapshot in enumerate(snapshots, start=1):
            value = snapshot.get(container)
            if not isinstance(value, dict):
                raise ValueError(f"snapshot {index} has no {container} object")
            names.update(value)
        combined: dict[str, dict[str, int]] = {}
        for name in sorted(names):
            combined[name] = {
                outcome: sum(
                    non_negative_int(
                        snapshot.get(container, {}).get(name, {}),
                        outcome,
                        f"snapshot[{index}].{container}.{name}",
                    )
                    if name in snapshot.get(container, {})
                    else 0
                    for index, snapshot in enumerate(snapshots, start=1)
                )
                for outcome in ("completed", "matches", "divergences", "failures")
            }
        return combined

    total_keys = (
        "submitted",
        "completed",
        "matches",
        "divergences",
        "failures",
        "staleResults",
        "discarded",
        "pending",
        "routeShapeDifferences",
    )
    merged["totals"] = {
        key: sum(
            non_negative_int(snapshot.get("totals", {}), key, f"snapshot[{index}].totals")
            for index, snapshot in enumerate(snapshots, start=1)
        )
        for key in total_keys
    }
    merged["coverage"] = sum_named_objects("coverage")
    merged["transportExecutors"] = sum_named_objects("transportExecutors")
    merged["transportTypes"] = sum_named_objects("transportTypes")

    execution_keys = (
        "terminal",
        "arrived",
        "unreachable",
        "exited",
        "recoveryTerminal",
        "recoveryArrived",
        "recoveryUnreachable",
        "recoveryExited",
    )
    merged["execution"] = {
        key: sum(
            non_negative_int(
                snapshot.get("execution", {}), key, f"snapshot[{index}].execution"
            )
            for index, snapshot in enumerate(snapshots, start=1)
        )
        for key in execution_keys
    }
    merged["latest"] = next(
        (snapshot.get("latest") for snapshot in reversed(snapshots) if snapshot.get("latest")),
        None,
    )
    merged["latestRouteShapeDifference"] = next(
        (
            snapshot.get("latestRouteShapeDifference")
            for snapshot in reversed(snapshots)
            if snapshot.get("latestRouteShapeDifference")
        ),
        None,
    )
    merged["latestDivergence"] = next(
        (
            snapshot.get("latestDivergence")
            for snapshot in reversed(snapshots)
            if snapshot.get("latestDivergence")
        ),
        None,
    )
    merged["latestFailure"] = next(
        (
            snapshot.get("latestFailure")
            for snapshot in reversed(snapshots)
            if snapshot.get("latestFailure")
        ),
        None,
    )
    return merged


def markdown(report: dict[str, Any]) -> str:
    totals = report["totals"]
    lines = [
        "# Walker live planner shadow evidence",
        "",
        f"**Verdict:** `{report['verdict']}`",
        "",
        f"Candidate: `{report['candidateEngineId']}`",
        "",
        f"Evidence profile: `{report['evidenceProfile']}`; planner mode: "
        f"`{report['plannerMode']}`.",
        "",
        f"Fresh client sessions: {report['sessionCount']} / "
        f"{report['minimumSessions']} required.",
        "",
        f"Completed: {totals['completed']} / {report['minimumCompleted']} required; "
        f"matches: {totals['matches']}; divergences: {totals['divergences']}; "
        f"failures: {totals['failures']}.",
        "",
        "| Coverage | Completed | Required | Matches | Divergences | Failures | Status |",
        "|---|---:|---:|---:|---:|---:|---|",
    ]
    for row in report["coverage"]:
        lines.append(
            f"| {row['tag']} | {row['completed']} | {row['required']} | "
            f"{row['matches']} | {row['divergences']} | {row['failures']} | "
            f"{row['status']} |"
        )
    lines.extend(
        [
            "",
            "## Transport executor diversity",
            "",
            f"Observed distinct executors: {len(report['observedTransportExecutors'])} / "
            f"{report['minimumDistinctTransportExecutors']} required.",
            "",
            "| Group | Completed | Required | Executors | Status |",
            "|---|---:|---:|---|---|",
        ]
    )
    for row in report["transportExecutorGroups"]:
        lines.append(
            f"| {row['group']} | {row['completed']} | {row['required']} | "
            f"{', '.join(row['executors'])} | {row['status']} |"
        )
    execution = report["execution"]
    lines.extend(
        [
            "",
            "## Walker execution outcomes",
            "",
            f"Arrived: {execution['arrived']} / {report['minimumWalkerArrivals']} required; "
            f"unreachable: {execution['unreachable']}; exited: {execution['exited']}.",
            "",
            f"Recovered arrivals: {execution['recoveryArrived']} / "
            f"{report['minimumRecoveryArrivals']} required; recovered unreachable: "
            f"{execution['recoveryUnreachable']}; recovered exited: "
            f"{execution['recoveryExited']}.",
        ]
    )
    latest_route_shape_difference = report.get("latestRouteShapeDifference")
    if latest_route_shape_difference is not None:
        lines.extend(
            [
                "",
                "## Latest route-shape diagnostic",
                "",
                f"Invocation: `{latest_route_shape_difference.get('invocation')}`; "
                f"semantic status: `{latest_route_shape_difference.get('status')}`; "
                "exact path match: "
                f"`{latest_route_shape_difference.get('pathMatches')}`.",
            ]
        )
    for heading, key in (
        ("Latest semantic divergence", "latestDivergence"),
        ("Latest planner failure", "latestFailure"),
    ):
        diagnostic = report.get(key)
        if diagnostic is not None:
            lines.extend(
                [
                    "",
                    f"## {heading}",
                    "",
                    f"Invocation: `{diagnostic.get('invocation')}`; "
                    f"status: `{diagnostic.get('status')}`; "
                    f"failure type: `{diagnostic.get('failureType')}`.",
                    "",
                    "Semantic equality — "
                    f"termination: `{diagnostic.get('terminationMatches')}`; "
                    f"endpoint: `{diagnostic.get('endpointMatches')}`; "
                    f"cost comparable: `{diagnostic.get('costComparable')}`; "
                    f"cost: `{diagnostic.get('costMatches')}`; "
                    f"selected transports: `{diagnostic.get('selectedTransportsMatch')}`.",
                ]
            )
    for heading, key in (
        ("Evidence shortfalls", "evidenceShortfalls"),
        ("Failures", "failures"),
        ("Notes", "warnings"),
    ):
        if report[key]:
            lines.extend(["", f"## {heading}", ""])
            lines.extend(f"- {value}" for value in report[key])
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("snapshot", type=Path, nargs="+")
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE)
    parser.add_argument("--profile", choices=("f2p", "members"), default="f2p")
    parser.add_argument("--minimum-completed", type=int)
    parser.add_argument("--json-output", type=Path)
    parser.add_argument("--markdown-output", type=Path)
    args = parser.parse_args()
    try:
        baseline = load_json(args.baseline)
        reviewed_commit = baseline.get("reviewedCommit")
        if not isinstance(reviewed_commit, str):
            raise ValueError("upstream baseline has no reviewedCommit")
        snapshots = [load_json(path) for path in args.snapshot]
        members_profile = args.profile == "members"
        expected_planner_mode = "SHADOW" if members_profile else None
        merged = snapshots[0] if len(snapshots) == 1 else merge_snapshots(
            snapshots, reviewed_commit, expected_planner_mode=expected_planner_mode
        )
        minimum_completed = args.minimum_completed
        if minimum_completed is None:
            minimum_completed = (
                MEMBERS_MINIMUM_COMPLETED if members_profile else 100
            )
        report = evaluate(
            merged,
            reviewed_commit,
            minimum_completed=minimum_completed,
            required_coverage=(MEMBERS_REQUIRED_COVERAGE if members_profile else None),
            minimum_distinct_transport_executors=(
                MEMBERS_MINIMUM_DISTINCT_TRANSPORT_EXECUTORS
                if members_profile
                else DEFAULT_MINIMUM_DISTINCT_TRANSPORT_EXECUTORS
            ),
            required_executor_groups=(
                MEMBERS_REQUIRED_EXECUTOR_GROUPS if members_profile else None
            ),
            minimum_walker_arrivals=(
                MEMBERS_MINIMUM_WALKER_ARRIVALS
                if members_profile
                else DEFAULT_MINIMUM_WALKER_ARRIVALS
            ),
            minimum_recovery_arrivals=(
                MEMBERS_MINIMUM_RECOVERY_ARRIVALS
                if members_profile
                else DEFAULT_MINIMUM_RECOVERY_ARRIVALS
            ),
            minimum_sessions=(MEMBERS_MINIMUM_SESSIONS if members_profile else 1),
            expected_planner_mode=expected_planner_mode,
            evidence_profile=args.profile,
        )
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2

    json_text = json.dumps(report, indent=2, sort_keys=True) + "\n"
    markdown_text = markdown(report)
    if args.json_output:
        args.json_output.parent.mkdir(parents=True, exist_ok=True)
        args.json_output.write_text(json_text, encoding="utf-8")
    if args.markdown_output:
        args.markdown_output.parent.mkdir(parents=True, exist_ok=True)
        args.markdown_output.write_text(markdown_text, encoding="utf-8")
    if not args.json_output and not args.markdown_output:
        print(markdown_text, end="")
    return 0 if report["verdict"] == "ACCEPTED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
