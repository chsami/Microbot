#!/usr/bin/env python3
"""Evaluate repeated planner-comparison reports for production-switch readiness."""

from __future__ import annotations

import argparse
import hashlib
import json
import statistics
import sys
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_CORPUS = Path(__file__).with_name("shortest-path-planner-corpus.json")
IDENTITY_FIELDS = (
    "schemaVersion",
    "localRevision",
    "upstreamRevision",
    "embeddedUpstreamRevision",
    "runeliteVersion",
    "corpusSha256",
    "upstreamIdentityPatchSha256",
)


def load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path}: expected a JSON object")
    return value


def ratio(numerator: float, denominator: float) -> float | None:
    return numerator / denominator if denominator > 0 else None


def median(values: list[float]) -> float:
    if not values:
        raise ValueError("cannot calculate a median without samples")
    return float(statistics.median(values))


def metric(case: dict[str, Any], engine: str, field: str) -> float:
    engine_result = case.get(engine)
    value = engine_result.get(field) if isinstance(engine_result, dict) else None
    if not isinstance(value, (int, float)) or isinstance(value, bool) or value < 0:
        raise ValueError(
            f"{case.get('id')}: {engine}.{field} must be a non-negative number"
        )
    return float(value)


def comparison_map(report: dict[str, Any]) -> dict[str, dict[str, Any]]:
    comparisons = report.get("comparisons")
    if not isinstance(comparisons, list):
        raise ValueError("comparison report has no comparisons array")
    result: dict[str, dict[str, Any]] = {}
    for comparison in comparisons:
        case_id = comparison.get("id") if isinstance(comparison, dict) else None
        if not isinstance(case_id, str) or case_id in result:
            raise ValueError(f"invalid or duplicate comparison id: {case_id!r}")
        result[case_id] = comparison
    return result


def corpus_case_map(corpus: dict[str, Any]) -> dict[str, dict[str, Any]]:
    cases = corpus.get("cases")
    if not isinstance(cases, list):
        raise ValueError("planner corpus has no cases array")
    result: dict[str, dict[str, Any]] = {}
    for case in cases:
        case_id = case.get("id") if isinstance(case, dict) else None
        if not isinstance(case_id, str) or case_id in result:
            raise ValueError(f"invalid or duplicate corpus case id: {case_id!r}")
        result[case_id] = case
    return result


def exclusion_reason(definition: dict[str, Any]) -> str | None:
    if not bool(definition.get("expectedParity", True)):
        return "documented input-policy divergence"
    transport_mode = definition.get("policy", {}).get("transportMode")
    if transport_mode == "BANK_AWARE_EXPLICIT_CATALOG":
        return (
            "bank-aware workflow shapes differ: Microbot composes searches while "
            "the reviewed upstream searches bank state in one pass"
        )
    return None


def evaluate_reports(
    reports: list[dict[str, Any]],
    corpus: dict[str, Any],
    *,
    corpus_sha256: str | None = None,
    minimum_samples: int = 5,
    maximum_suite_ratio: float = 1.5,
    maximum_case_ratio: float = 3.0,
    case_ratio_slack_millis: float = 100.0,
    maximum_case_millis: float = 2000.0,
) -> dict[str, Any]:
    if not reports:
        raise ValueError("at least one comparison report is required")
    if minimum_samples <= 0:
        raise ValueError("minimum_samples must be positive")
    if maximum_suite_ratio <= 0 or maximum_case_ratio <= 0:
        raise ValueError("performance ratios must be positive")
    if case_ratio_slack_millis < 0 or maximum_case_millis <= 0:
        raise ValueError("performance millisecond thresholds are invalid")

    definitions = corpus_case_map(corpus)
    reported_corpus_hash = reports[0].get("corpusSha256")
    identity = {field: reports[0].get(field) for field in IDENTITY_FIELDS}
    failures: list[str] = []
    evidence_shortfalls: list[str] = []
    performance_failures: list[str] = []
    warnings: list[str] = [
        "Node expansion and peak-heap deltas are diagnostic only; the engines use "
        "different data structures and heap baselines."
    ]

    if corpus.get("schemaVersion") != 3:
        failures.append(f"unsupported corpus schema: {corpus.get('schemaVersion')!r}")
    if reported_corpus_hash is None:
        failures.append("comparison report is missing corpusSha256")
    elif corpus_sha256 is not None and reported_corpus_hash != corpus_sha256:
        failures.append(
            "comparison corpusSha256 does not match the supplied corpus bytes: "
            f"{reported_corpus_hash!r} != {corpus_sha256!r}"
        )

    maps: list[dict[str, dict[str, Any]]] = []
    for index, report in enumerate(reports, start=1):
        label = f"sample {index}"
        for field in IDENTITY_FIELDS:
            if report.get(field) != identity[field]:
                failures.append(
                    f"{label}: {field}={report.get(field)!r} differs from "
                    f"{identity[field]!r}"
                )
        for field in ("failures", "unsupported", "embeddedUpstreamFailures"):
            values = report.get(field, [])
            if not isinstance(values, list):
                failures.append(f"{label}: {field} is not an array")
            elif values:
                failures.append(f"{label}: {field} is not empty: {values}")
        if report.get("localWorkingTreeDirty") is not False:
            evidence_shortfalls.append(
                f"{label}: localWorkingTreeDirty must be false so results identify "
                "the code under review"
            )
        maps.append(comparison_map(report))

    expected_ids = set(definitions)
    for index, mapped in enumerate(maps, start=1):
        if set(mapped) != expected_ids:
            failures.append(
                f"sample {index}: comparison case set differs from the corpus"
            )

    included_ids: list[str] = []
    excluded: list[dict[str, str]] = []
    for case_id, definition in definitions.items():
        reason = exclusion_reason(definition)
        if reason is None:
            included_ids.append(case_id)
        else:
            excluded.append({"id": case_id, "reason": reason})

    if len(reports) < minimum_samples:
        evidence_shortfalls.append(
            f"only {len(reports)} independent sample(s); at least "
            f"{minimum_samples} are required"
        )

    case_rows: list[dict[str, Any]] = []
    suite_local_samples = [0.0 for _ in reports]
    suite_upstream_samples = [0.0 for _ in reports]
    for case_id in included_ids:
        definition = definitions[case_id]
        local_elapsed: list[float] = []
        upstream_elapsed: list[float] = []
        local_nodes: list[float] = []
        upstream_nodes: list[float] = []
        local_heap: list[float] = []
        upstream_heap: list[float] = []
        for sample_index, mapped in enumerate(maps):
            comparison = mapped.get(case_id)
            if comparison is None:
                continue
            if comparison.get("status") != "PASS":
                failures.append(
                    f"sample {sample_index + 1}: {case_id} status is "
                    f"{comparison.get('status')!r}, expected 'PASS'"
                )
            local_value = metric(comparison, "local", "elapsedNanos")
            upstream_value = metric(comparison, "upstream", "elapsedNanos")
            local_elapsed.append(local_value)
            upstream_elapsed.append(upstream_value)
            local_nodes.append(metric(comparison, "local", "nodesChecked"))
            upstream_nodes.append(metric(comparison, "upstream", "nodesChecked"))
            local_heap.append(metric(comparison, "local", "peakHeapDeltaBytes"))
            upstream_heap.append(metric(comparison, "upstream", "peakHeapDeltaBytes"))
            suite_local_samples[sample_index] += local_value
            suite_upstream_samples[sample_index] += upstream_value

        if len(local_elapsed) != len(reports):
            continue
        local_median_ms = median(local_elapsed) / 1_000_000.0
        upstream_median_ms = median(upstream_elapsed) / 1_000_000.0
        local_max_ms = max(local_elapsed) / 1_000_000.0
        upstream_max_ms = max(upstream_elapsed) / 1_000_000.0
        relative_budget_ms = max(
            local_max_ms * maximum_case_ratio, case_ratio_slack_millis
        )
        case_failures: list[str] = []
        if len(reports) >= minimum_samples:
            if upstream_max_ms > relative_budget_ms:
                case_failures.append(
                    f"upstream max {upstream_max_ms:.3f} ms exceeds relative/noise "
                    f"budget {relative_budget_ms:.3f} ms"
                )
            if upstream_max_ms > maximum_case_millis:
                case_failures.append(
                    f"upstream max {upstream_max_ms:.3f} ms exceeds absolute "
                    f"budget {maximum_case_millis:.3f} ms"
                )
        performance_failures.extend(f"{case_id}: {value}" for value in case_failures)
        case_rows.append(
            {
                "id": case_id,
                "category": definition.get("category"),
                "sampleCount": len(local_elapsed),
                "localMedianMillis": local_median_ms,
                "upstreamMedianMillis": upstream_median_ms,
                "upstreamToLocalMedianRatio": ratio(
                    upstream_median_ms, local_median_ms
                ),
                "localMaxMillis": local_max_ms,
                "upstreamMaxMillis": upstream_max_ms,
                "relativeOrNoiseBudgetMillis": relative_budget_ms,
                "absoluteBudgetMillis": maximum_case_millis,
                "localMedianNodes": median(local_nodes),
                "upstreamMedianNodes": median(upstream_nodes),
                "upstreamToLocalNodeRatio": ratio(
                    median(upstream_nodes), median(local_nodes)
                ),
                "localMedianPeakHeapDeltaBytes": median(local_heap),
                "upstreamMedianPeakHeapDeltaBytes": median(upstream_heap),
                "status": "FAIL" if case_failures else "PASS",
                "failures": case_failures,
            }
        )

    suite_local_median_ms = median(suite_local_samples) / 1_000_000.0
    suite_upstream_median_ms = median(suite_upstream_samples) / 1_000_000.0
    suite_ratio = ratio(suite_upstream_median_ms, suite_local_median_ms)
    if (
        len(reports) >= minimum_samples
        and suite_ratio is not None
        and suite_ratio > maximum_suite_ratio
    ):
        performance_failures.append(
            f"suite upstream/local median elapsed ratio {suite_ratio:.3f} exceeds "
            f"{maximum_suite_ratio:.3f}"
        )

    if failures:
        verdict = "REJECTED"
    elif evidence_shortfalls:
        verdict = "INSUFFICIENT_EVIDENCE"
    elif performance_failures:
        verdict = "REJECTED"
    else:
        verdict = "ACCEPTED"

    return {
        "schemaVersion": 1,
        "verdict": verdict,
        "sampleCount": len(reports),
        "minimumSamples": minimum_samples,
        "identity": identity,
        "thresholds": {
            "maximumSuiteUpstreamToLocalMedianRatio": maximum_suite_ratio,
            "maximumCaseUpstreamToLocalMaxRatio": maximum_case_ratio,
            "caseRatioSlackMillis": case_ratio_slack_millis,
            "maximumCaseUpstreamMaxMillis": maximum_case_millis,
        },
        "comparability": {
            "includedCaseIds": included_ids,
            "excludedCases": excluded,
        },
        "suite": {
            "localMedianMillis": suite_local_median_ms,
            "upstreamMedianMillis": suite_upstream_median_ms,
            "upstreamToLocalMedianRatio": suite_ratio,
        },
        "cases": case_rows,
        "failures": failures,
        "evidenceShortfalls": evidence_shortfalls,
        "performanceFailures": performance_failures,
        "warnings": warnings,
    }


def markdown(report: dict[str, Any]) -> str:
    suite = report["suite"]
    lines = [
        "# Shortest-path planner performance evidence",
        "",
        f"**Verdict:** `{report['verdict']}`",
        "",
        f"Samples: {report['sampleCount']} / {report['minimumSamples']} required.",
        "",
        "The production-core switch remains a separate decision from correctness parity "
        "and live shadow-route acceptance.",
        "",
        "## Comparable suite",
        "",
        f"- Local median total: {suite['localMedianMillis']:.3f} ms",
        f"- Upstream median total: {suite['upstreamMedianMillis']:.3f} ms",
        f"- Upstream/local ratio: {suite['upstreamToLocalMedianRatio']:.3f}",
        "",
        "| Case | Local median ms | Upstream median ms | Ratio | Upstream max ms | Status |",
        "|---|---:|---:|---:|---:|---|",
    ]
    for case in report["cases"]:
        case_ratio = case["upstreamToLocalMedianRatio"]
        ratio_text = "n/a" if case_ratio is None else f"{case_ratio:.3f}"
        lines.append(
            f"| {case['id']} | {case['localMedianMillis']:.3f} | "
            f"{case['upstreamMedianMillis']:.3f} | {ratio_text} | "
            f"{case['upstreamMaxMillis']:.3f} | {case['status']} |"
        )
    for heading, key in (
        ("Evidence shortfalls", "evidenceShortfalls"),
        ("Validation failures", "failures"),
        ("Performance failures", "performanceFailures"),
        ("Notes", "warnings"),
    ):
        values = report[key]
        if values:
            lines.extend(["", f"## {heading}", ""])
            lines.extend(f"- {value}" for value in values)
    excluded = report["comparability"]["excludedCases"]
    if excluded:
        lines.extend(["", "## Excluded from core timing", ""])
        lines.extend(f"- `{value['id']}`: {value['reason']}" for value in excluded)
    return "\n".join(lines) + "\n"


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("reports", nargs="+", type=Path)
    parser.add_argument("--corpus", type=Path, default=DEFAULT_CORPUS)
    parser.add_argument("--minimum-samples", type=int, default=5)
    parser.add_argument("--maximum-suite-ratio", type=float, default=1.5)
    parser.add_argument("--maximum-case-ratio", type=float, default=3.0)
    parser.add_argument("--case-ratio-slack-millis", type=float, default=100.0)
    parser.add_argument("--maximum-case-millis", type=float, default=2000.0)
    parser.add_argument("--json-output", type=Path)
    parser.add_argument("--markdown-output", type=Path)
    args = parser.parse_args()

    try:
        corpus_bytes = args.corpus.read_bytes()
        corpus = json.loads(corpus_bytes)
        if not isinstance(corpus, dict):
            raise ValueError(f"{args.corpus}: expected a JSON object")
        result = evaluate_reports(
            [load_json(path) for path in args.reports],
            corpus,
            corpus_sha256=hashlib.sha256(corpus_bytes).hexdigest(),
            minimum_samples=args.minimum_samples,
            maximum_suite_ratio=args.maximum_suite_ratio,
            maximum_case_ratio=args.maximum_case_ratio,
            case_ratio_slack_millis=args.case_ratio_slack_millis,
            maximum_case_millis=args.maximum_case_millis,
        )
    except (OSError, ValueError, json.JSONDecodeError) as error:
        print(f"ERROR: {error}", file=sys.stderr)
        return 2

    json_text = json.dumps(result, indent=2, sort_keys=True) + "\n"
    markdown_text = markdown(result)
    if args.json_output:
        args.json_output.parent.mkdir(parents=True, exist_ok=True)
        args.json_output.write_text(json_text, encoding="utf-8")
    if args.markdown_output:
        args.markdown_output.parent.mkdir(parents=True, exist_ok=True)
        args.markdown_output.write_text(markdown_text, encoding="utf-8")
    if not args.json_output and not args.markdown_output:
        print(markdown_text, end="")
    return 0 if result["verdict"] == "ACCEPTED" else 1


if __name__ == "__main__":
    raise SystemExit(main())
