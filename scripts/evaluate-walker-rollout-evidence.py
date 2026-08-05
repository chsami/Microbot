#!/usr/bin/env python3
"""Evaluate paired normal-canary and forced-rollback walker release evidence."""

from __future__ import annotations

import argparse
import json
import sys
from pathlib import Path
from typing import Any


DEFAULT_BASELINE = Path(__file__).with_name("shortest-path-upstream-baseline.json")
PLANNER_MODE = "UPSTREAM_F2P_CANARY"
REQUIRED_COVERAGE = ("ACTIVE_ROUTE", "UNDERGROUND_COORDINATES")
REQUIRED_EXECUTORS = ("OBJECT",)
DEFAULT_MAXIMUM_CANARY_PLANNING_MS = 2_000.0
DEFAULT_MAXIMUM_CANARY_NON_SEARCH_OVERHEAD_MS = 250.0


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


def outcome_row(
    mapping: dict[str, Any], key: str, prefix: str, failures: list[str]
) -> dict[str, int]:
    raw = mapping.get(key)
    if not isinstance(raw, dict):
        raise ValueError(f"{prefix}.{key} must be an object")
    row = {
        field: non_negative_int(raw, field, f"{prefix}.{key}")
        for field in ("completed", "matches", "divergences", "failures")
    }
    if row["completed"] != row["matches"] + row["divergences"] + row["failures"]:
        failures.append(
            f"{prefix}.{key}.completed does not equal matches + divergences + failures"
        )
    return row


def phase_summary(
    result: dict[str, Any],
    *,
    label: str,
    expected_engine: str,
    expect_local_fallback: bool,
    minimum_comparisons: int,
    minimum_arrivals: int,
    maximum_canary_planning_ms: float,
    maximum_canary_non_search_overhead_ms: float,
    required_routes: set[str],
    failures: list[str],
    shortfalls: list[str],
    warnings: list[str],
) -> dict[str, Any]:
    prefix = label
    failure_count_before = len(failures)
    shortfall_count_before = len(shortfalls)
    if result.get("script") != "F2P Web Walker Harness":
        failures.append(f"{prefix}.script must be 'F2P Web Walker Harness'")
    if result.get("exitCode") != 0 or result.get("exitReason") != "completed":
        failures.append(f"{prefix} harness did not exit successfully")
    errors = result.get("errors")
    if not isinstance(errors, list):
        raise ValueError(f"{prefix}.errors must be an array")
    if errors:
        failures.append(f"{prefix}.errors is not empty")
    if result.get("plannerMode") != PLANNER_MODE:
        failures.append(f"{prefix}.plannerMode must be {PLANNER_MODE}")
    if result.get("expectLocalFallback") is not expect_local_fallback:
        failures.append(
            f"{prefix}.expectLocalFallback must be {str(expect_local_fallback).lower()}"
        )
    if result.get("shadowSettled") is not True:
        shortfalls.append(f"{prefix} planner evidence was not settled")

    checks = result.get("checks")
    if not isinstance(checks, list) or not checks:
        raise ValueError(f"{prefix}.checks must be a non-empty array")
    failed_checks = [
        check.get("name", "unnamed")
        for check in checks
        if not isinstance(check, dict) or check.get("passed") is not True
    ]
    if failed_checks:
        failures.append(f"{prefix} failed harness checks: {failed_checks}")

    selected = result.get("selectedRoutes")
    if not isinstance(selected, list) or any(not isinstance(value, str) for value in selected):
        raise ValueError(f"{prefix}.selectedRoutes must be an array of strings")
    selected_routes = set(selected)
    missing_routes = required_routes - selected_routes
    if missing_routes:
        shortfalls.append(f"{prefix} is missing required route(s): {sorted(missing_routes)}")

    routes = result.get("routes")
    if not isinstance(routes, list) or not routes:
        raise ValueError(f"{prefix}.routes must be a non-empty array")
    route_summaries: list[dict[str, Any]] = []
    observed_route_ids: set[str] = set()
    for index, route in enumerate(routes):
        if not isinstance(route, dict):
            raise ValueError(f"{prefix}.routes[{index}] must be an object")
        route_id = route.get("id")
        if not isinstance(route_id, str) or not route_id:
            raise ValueError(f"{prefix}.routes[{index}].id must be a string")
        if route_id in observed_route_ids:
            failures.append(f"{prefix} contains duplicate route {route_id}")
        observed_route_ids.add(route_id)
        repetitions = non_negative_int(route, "repetitions", f"{prefix}.routes[{index}]")
        successful = non_negative_int(
            route, "successfulAttempts", f"{prefix}.routes[{index}]"
        )
        passed = (
            route.get("passed") is True
            and route.get("walkerState") == "ARRIVED"
            and successful == repetitions
            and repetitions > 0
        )
        if not passed:
            failures.append(f"{prefix} route {route_id} did not complete every repetition")
        route_summaries.append(
            {
                "id": route_id,
                "repetitions": repetitions,
                "successfulAttempts": successful,
                "status": "PASS" if passed else "FAIL",
            }
        )
    if required_routes - observed_route_ids:
        shortfalls.append(
            f"{prefix}.routes has no outcome for {sorted(required_routes - observed_route_ids)}"
        )

    snapshot = result.get("shadowEvidence")
    if not isinstance(snapshot, dict):
        raise ValueError(f"{prefix}.shadowEvidence must be an object")
    if snapshot.get("schemaVersion") != 2:
        failures.append(f"{prefix}.shadowEvidence.schemaVersion must be 2")
    if snapshot.get("enabled") is not True:
        failures.append(f"{prefix}.shadowEvidence.enabled must be true")
    if snapshot.get("plannerMode") != PLANNER_MODE:
        failures.append(f"{prefix}.shadowEvidence.plannerMode must be {PLANNER_MODE}")
    if snapshot.get("candidateEngineId") != expected_engine:
        failures.append(f"{prefix}.candidateEngineId does not match the reviewed engine")
    started_at = non_negative_int(
        snapshot, "startedAtEpochMillis", f"{prefix}.shadowEvidence"
    )

    totals_raw = snapshot.get("totals")
    if not isinstance(totals_raw, dict):
        raise ValueError(f"{prefix}.shadowEvidence.totals must be an object")
    totals = {
        field: non_negative_int(totals_raw, field, f"{prefix}.totals")
        for field in (
            "submitted",
            "completed",
            "matches",
            "divergences",
            "failures",
            "staleResults",
            "discarded",
            "pending",
            "routeShapeDifferences",
            "upstreamCanarySelections",
            "localFallbackDivergences",
            "localFallbackFailures",
        )
    }
    if totals["completed"] != (
        totals["matches"] + totals["divergences"] + totals["failures"]
    ):
        failures.append(f"{prefix}.totals.completed accounting is invalid")
    if totals["submitted"] != (
        totals["completed"] + totals["discarded"] + totals["pending"]
    ):
        failures.append(f"{prefix}.totals.submitted accounting is invalid")
    if totals["completed"] < minimum_comparisons:
        shortfalls.append(
            f"{prefix} has {totals['completed']} completed comparison(s); "
            f"{minimum_comparisons} required"
        )
    for field in ("staleResults", "discarded", "pending"):
        if totals[field]:
            failures.append(f"{prefix}.totals.{field} must be zero")
    if totals["routeShapeDifferences"]:
        warnings.append(
            f"{prefix} observed {totals['routeShapeDifferences']} equal-cost route-shape difference(s)"
        )

    performance_raw = snapshot.get("canaryPerformance")
    if not isinstance(performance_raw, dict):
        raise ValueError(f"{prefix}.shadowEvidence.canaryPerformance must be an object")
    performance = {
        field: non_negative_int(performance_raw, field, f"{prefix}.canaryPerformance")
        for field in (
            "planningSamples",
            "planningNanosTotal",
            "planningNanosMax",
            "localSearchNanosTotal",
            "localSearchNanosMax",
            "upstreamSearchSamples",
            "upstreamSearchNanosTotal",
            "upstreamSearchNanosMax",
        )
    }
    if performance["planningSamples"] != totals["completed"]:
        failures.append(
            f"{prefix}.canaryPerformance.planningSamples must equal completed comparisons"
        )
    if performance["planningNanosTotal"] < performance["planningNanosMax"]:
        failures.append(f"{prefix} canary planning total is smaller than its maximum")
    if performance["localSearchNanosTotal"] < performance["localSearchNanosMax"]:
        failures.append(f"{prefix} local search total is smaller than its maximum")
    if performance["upstreamSearchNanosTotal"] < performance["upstreamSearchNanosMax"]:
        failures.append(f"{prefix} upstream search total is smaller than its maximum")
    if performance["planningNanosTotal"] < (
        performance["localSearchNanosTotal"] + performance["upstreamSearchNanosTotal"]
    ):
        failures.append(
            f"{prefix} canary readiness time does not contain both measured planner searches"
        )
    if performance["planningSamples"] and performance["localSearchNanosTotal"] == 0:
        failures.append(f"{prefix} has no measurable local planner time")
    planning_average_ms = (
        performance["planningNanosTotal"] / performance["planningSamples"] / 1_000_000.0
        if performance["planningSamples"] else 0.0
    )
    planning_max_ms = performance["planningNanosMax"] / 1_000_000.0
    planning_local_ratio = (
        performance["planningNanosTotal"] / performance["localSearchNanosTotal"]
        if performance["localSearchNanosTotal"] else 0.0
    )
    non_search_overhead_nanos = max(
        0,
        performance["planningNanosTotal"]
        - performance["localSearchNanosTotal"]
        - performance["upstreamSearchNanosTotal"],
    )
    non_search_overhead_average_ms = (
        non_search_overhead_nanos / performance["planningSamples"] / 1_000_000.0
        if performance["planningSamples"] else 0.0
    )
    performance.update(
        {
            "planningAverageMs": planning_average_ms,
            "planningMaxMs": planning_max_ms,
            "planningLocalRatio": planning_local_ratio,
            "nonSearchOverheadAverageMs": non_search_overhead_average_ms,
        }
    )
    if planning_max_ms > maximum_canary_planning_ms:
        failures.append(
            f"{prefix} canary planning maximum {planning_max_ms:.1f} ms exceeds "
            f"{maximum_canary_planning_ms:.1f} ms"
        )
    if non_search_overhead_average_ms > maximum_canary_non_search_overhead_ms:
        failures.append(
            f"{prefix} average canary non-search overhead "
            f"{non_search_overhead_average_ms:.1f} ms exceeds "
            f"{maximum_canary_non_search_overhead_ms:.1f} ms"
        )

    if expect_local_fallback:
        expected_zero = ("matches", "divergences", "upstreamCanarySelections", "localFallbackDivergences")
        for field in expected_zero:
            if totals[field] != 0:
                failures.append(f"{prefix}.totals.{field} must be zero in forced rollback")
        if totals["failures"] != totals["completed"]:
            failures.append(f"{prefix} must fail every upstream comparison")
        if totals["localFallbackFailures"] != totals["completed"]:
            failures.append(f"{prefix} must locally fall back for every planner failure")
        if performance["upstreamSearchSamples"] != 0:
            failures.append(
                f"{prefix}.canaryPerformance.upstreamSearchSamples must be zero "
                "for the injected pre-search failure"
            )
        latest_failure = snapshot.get("latestFailure")
        if not isinstance(latest_failure, dict):
            failures.append(f"{prefix}.latestFailure must preserve the forced failure")
        else:
            if latest_failure.get("status") != "FAILED":
                failures.append(f"{prefix}.latestFailure.status must be FAILED")
            if latest_failure.get("shadowEngineId") != expected_engine:
                failures.append(f"{prefix}.latestFailure has the wrong engine")
            failure_type = latest_failure.get("failureType")
            if not isinstance(failure_type, str) or not failure_type:
                failures.append(f"{prefix}.latestFailure must expose an exception class")
            if "failureMessage" in latest_failure or "message" in latest_failure:
                failures.append(f"{prefix}.latestFailure must not expose an exception message")
    else:
        for field in (
            "divergences",
            "failures",
            "localFallbackDivergences",
            "localFallbackFailures",
        ):
            if totals[field] != 0:
                failures.append(f"{prefix}.totals.{field} must be zero in a normal canary")
        if totals["matches"] != totals["completed"]:
            failures.append(f"{prefix} must semantically match every comparison")
        if totals["upstreamCanarySelections"] != totals["completed"]:
            failures.append(f"{prefix} must select upstream for every matching comparison")
        if performance["upstreamSearchSamples"] != totals["completed"]:
            failures.append(
                f"{prefix}.canaryPerformance.upstreamSearchSamples must equal "
                "completed comparisons"
            )

    execution_raw = snapshot.get("execution")
    if not isinstance(execution_raw, dict):
        raise ValueError(f"{prefix}.shadowEvidence.execution must be an object")
    execution = {
        field: non_negative_int(execution_raw, field, f"{prefix}.execution")
        for field in (
            "terminal",
            "arrived",
            "unreachable",
            "exited",
            "recoveryTerminal",
            "recoveryArrived",
            "recoveryUnreachable",
            "recoveryExited",
        )
    }
    if execution["terminal"] != (
        execution["arrived"] + execution["unreachable"] + execution["exited"]
    ):
        failures.append(f"{prefix}.execution.terminal accounting is invalid")
    if execution["recoveryTerminal"] != (
        execution["recoveryArrived"]
        + execution["recoveryUnreachable"]
        + execution["recoveryExited"]
    ):
        failures.append(f"{prefix}.execution.recoveryTerminal accounting is invalid")
    if execution["unreachable"] or execution["exited"]:
        failures.append(f"{prefix} contains a terminal non-arrival")
    if execution["arrived"] < minimum_arrivals:
        shortfalls.append(
            f"{prefix} has {execution['arrived']} terminal arrival(s); {minimum_arrivals} required"
        )

    coverage = snapshot.get("coverage")
    if not isinstance(coverage, dict):
        raise ValueError(f"{prefix}.shadowEvidence.coverage must be an object")
    coverage_summary = {
        key: outcome_row(coverage, key, f"{prefix}.coverage", failures)
        for key in REQUIRED_COVERAGE
    }
    executors = snapshot.get("transportExecutors")
    if not isinstance(executors, dict):
        raise ValueError(f"{prefix}.shadowEvidence.transportExecutors must be an object")
    executor_summary = {
        key: outcome_row(executors, key, f"{prefix}.transportExecutors", failures)
        for key in REQUIRED_EXECUTORS
    }
    for key, row in {**coverage_summary, **executor_summary}.items():
        if row["completed"] < minimum_comparisons:
            shortfalls.append(
                f"{prefix} has {row['completed']} {key} comparison(s); "
                f"{minimum_comparisons} required"
            )

    status = "FAIL" if len(failures) > failure_count_before else (
        "INSUFFICIENT" if len(shortfalls) > shortfall_count_before else "PASS"
    )
    return {
        "status": status,
        "startedAtEpochMillis": started_at,
        "selectedRoutes": sorted(selected_routes),
        "routes": route_summaries,
        "totals": totals,
        "execution": execution,
        "canaryPerformance": performance,
        "coverage": coverage_summary,
        "transportExecutors": executor_summary,
    }


def evaluate(
    normal: dict[str, Any],
    rollback: dict[str, Any],
    reviewed_commit: str,
    *,
    minimum_comparisons: int = 10,
    minimum_arrivals: int = 10,
    maximum_canary_planning_ms: float = DEFAULT_MAXIMUM_CANARY_PLANNING_MS,
    maximum_canary_non_search_overhead_ms: float = (
        DEFAULT_MAXIMUM_CANARY_NON_SEARCH_OVERHEAD_MS
    ),
    required_routes: set[str] | None = None,
) -> dict[str, Any]:
    if len(reviewed_commit) != 40:
        raise ValueError("reviewed_commit must be a full 40-character revision")
    if minimum_comparisons <= 0 or minimum_arrivals <= 0:
        raise ValueError("minimum comparison and arrival requirements must be positive")
    if (maximum_canary_planning_ms <= 0
            or maximum_canary_non_search_overhead_ms <= 0):
        raise ValueError("canary performance thresholds must be positive")
    routes = {"F2P-17"} if required_routes is None else set(required_routes)
    if not routes:
        raise ValueError("at least one required route is needed")
    failures: list[str] = []
    shortfalls: list[str] = []
    warnings: list[str] = []
    expected_engine = f"shortest-path-upstream@{reviewed_commit}"
    normal_summary = phase_summary(
        normal,
        label="normal",
        expected_engine=expected_engine,
        expect_local_fallback=False,
        minimum_comparisons=minimum_comparisons,
        minimum_arrivals=minimum_arrivals,
        maximum_canary_planning_ms=maximum_canary_planning_ms,
        maximum_canary_non_search_overhead_ms=maximum_canary_non_search_overhead_ms,
        required_routes=routes,
        failures=failures,
        shortfalls=shortfalls,
        warnings=warnings,
    )
    rollback_summary = phase_summary(
        rollback,
        label="rollback",
        expected_engine=expected_engine,
        expect_local_fallback=True,
        minimum_comparisons=minimum_comparisons,
        minimum_arrivals=minimum_arrivals,
        maximum_canary_planning_ms=maximum_canary_planning_ms,
        maximum_canary_non_search_overhead_ms=maximum_canary_non_search_overhead_ms,
        required_routes=routes,
        failures=failures,
        shortfalls=shortfalls,
        warnings=warnings,
    )
    if normal_summary["startedAtEpochMillis"] == rollback_summary["startedAtEpochMillis"]:
        failures.append("normal and rollback evidence came from the same client session")
    if normal_summary["selectedRoutes"] != rollback_summary["selectedRoutes"]:
        failures.append("normal and rollback evidence selected different route sets")

    verdict = "REJECTED" if failures else (
        "INSUFFICIENT_EVIDENCE" if shortfalls else "ACCEPTED"
    )
    return {
        "schemaVersion": 1,
        "verdict": verdict,
        "reviewedCommit": reviewed_commit,
        "candidateEngineId": expected_engine,
        "requiredRoutes": sorted(routes),
        "minimumComparisonsPerPhase": minimum_comparisons,
        "minimumArrivalsPerPhase": minimum_arrivals,
        "maximumCanaryPlanningMillis": maximum_canary_planning_ms,
        "maximumCanaryNonSearchOverheadMillis": maximum_canary_non_search_overhead_ms,
        "normal": normal_summary,
        "rollback": rollback_summary,
        "failures": failures,
        "evidenceShortfalls": shortfalls,
        "warnings": warnings,
    }


def markdown(report: dict[str, Any]) -> str:
    lines = [
        "# Walker F2P planner rollout evidence",
        "",
        f"**Verdict:** `{report['verdict']}`",
        "",
        f"Candidate: `{report['candidateEngineId']}`",
        "",
        f"Required routes: {', '.join(report['requiredRoutes'])}.",
        "",
        "| Phase | Completed | Matches | Planner failures | Upstream selections | "
        "Local failure fallbacks | Arrivals | Ready avg ms | Ready max ms | "
        "Non-search avg ms | Ready/local | Status |",
        "|---|---:|---:|---:|---:|---:|---:|---:|---:|---:|---:|---|",
    ]
    for key, label in (("normal", "Normal canary"), ("rollback", "Forced rollback")):
        phase = report[key]
        totals = phase["totals"]
        execution = phase["execution"]
        performance = phase["canaryPerformance"]
        lines.append(
            f"| {label} | {totals['completed']} | {totals['matches']} | "
            f"{totals['failures']} | {totals['upstreamCanarySelections']} | "
            f"{totals['localFallbackFailures']} | {execution['arrived']} | "
            f"{performance['planningAverageMs']:.1f} | {performance['planningMaxMs']:.1f} | "
            f"{performance['nonSearchOverheadAverageMs']:.1f} | "
            f"{performance['planningLocalRatio']:.3f} | {phase['status']} |"
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
    parser.add_argument("normal", type=Path)
    parser.add_argument("rollback", type=Path)
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE)
    parser.add_argument("--minimum-comparisons", type=int, default=10)
    parser.add_argument("--minimum-arrivals", type=int, default=10)
    parser.add_argument(
        "--maximum-canary-planning-ms",
        type=float,
        default=DEFAULT_MAXIMUM_CANARY_PLANNING_MS,
    )
    parser.add_argument(
        "--maximum-canary-non-search-overhead-ms",
        type=float,
        default=DEFAULT_MAXIMUM_CANARY_NON_SEARCH_OVERHEAD_MS,
    )
    parser.add_argument("--required-route", action="append", default=[])
    parser.add_argument("--json-output", type=Path)
    parser.add_argument("--markdown-output", type=Path)
    args = parser.parse_args()
    try:
        baseline = load_json(args.baseline)
        reviewed_commit = baseline.get("reviewedCommit")
        if not isinstance(reviewed_commit, str):
            raise ValueError("upstream baseline has no reviewedCommit")
        report = evaluate(
            load_json(args.normal),
            load_json(args.rollback),
            reviewed_commit,
            minimum_comparisons=args.minimum_comparisons,
            minimum_arrivals=args.minimum_arrivals,
            maximum_canary_planning_ms=args.maximum_canary_planning_ms,
            maximum_canary_non_search_overhead_ms=(
                args.maximum_canary_non_search_overhead_ms
            ),
            required_routes=set(args.required_route) if args.required_route else None,
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
