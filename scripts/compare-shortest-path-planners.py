#!/usr/bin/env python3
"""Run Microbot and reviewed upstream planners against one immutable headless corpus."""

from __future__ import annotations

import argparse
import hashlib
import json
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
CORPUS_PATH = Path(__file__).with_name("shortest-path-planner-corpus.json")
UPSTREAM_BASELINE_PATH = Path(__file__).with_name(
    "shortest-path-upstream-baseline.json"
)
HARNESS_ROOT = Path(__file__).with_name("shortest-path-planner-harness")
UPSTREAM_RUNNER = HARNESS_ROOT / "UpstreamPlannerComparisonMain.java"
UPSTREAM_INIT = HARNESS_ROOT / "upstream-planner-comparison.init.gradle"
UPSTREAM_IDENTITY_PATCH = HARNESS_ROOT / "upstream-exact-transport-identity.patch"
UPSTREAM_REPOSITORY = "https://github.com/Skretzo/shortest-path.git"


def load_json(path: Path) -> dict[str, Any]:
    return json.loads(path.read_text(encoding="utf-8"))


def run(command: list[str], cwd: Path) -> None:
    subprocess.run(command, cwd=cwd, check=True)


def capture(command: list[str], cwd: Path) -> str:
    return subprocess.run(
        command,
        cwd=cwd,
        check=True,
        capture_output=True,
        text=True,
    ).stdout.strip()


def reviewed_commit() -> str:
    baseline = load_json(UPSTREAM_BASELINE_PATH)
    commit = baseline.get("reviewedCommit")
    if not isinstance(commit, str) or len(commit) != 40:
        raise ValueError("upstream baseline has no valid reviewedCommit")
    return commit


def runelite_version() -> str:
    properties = REPOSITORY_ROOT / "gradle.properties"
    for line in properties.read_text(encoding="utf-8").splitlines():
        if line.startswith("project.build.version="):
            return line.split("=", 1)[1].strip()
    raise ValueError("gradle.properties has no project.build.version")


def prepare_upstream(
    destination: Path, commit: str, checkout: Path | None
) -> None:
    source = str(checkout.resolve()) if checkout else UPSTREAM_REPOSITORY
    run(["git", "clone", "--quiet", "--no-checkout", source, str(destination)],
        REPOSITORY_ROOT)
    run(["git", "checkout", "--quiet", "--detach", commit], destination)
    actual = capture(["git", "rev-parse", "HEAD"], destination)
    if actual != commit:
        raise RuntimeError(f"upstream checkout mismatch: expected {commit}, got {actual}")

    run(["git", "apply", "--check", str(UPSTREAM_IDENTITY_PATCH)], destination)
    run(["git", "apply", str(UPSTREAM_IDENTITY_PATCH)], destination)

    target = (
        destination
        / "src/test/java/shortestpath/pathfinder/UpstreamPlannerComparisonMain.java"
    )
    target.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(UPSTREAM_RUNNER, target)


def point_distance(point: dict[str, int] | None, target: dict[str, int]) -> int | None:
    if point is None or point.get("plane") != target.get("plane"):
        return None
    return max(abs(point["x"] - target["x"]), abs(point["y"] - target["y"]))


def case_map(result: dict[str, Any]) -> dict[str, dict[str, Any]]:
    cases = result.get("cases")
    if not isinstance(cases, list):
        raise ValueError("planner result has no cases array")
    mapped: dict[str, dict[str, Any]] = {}
    for case in cases:
        case_id = case.get("id")
        if not isinstance(case_id, str) or case_id in mapped:
            raise ValueError(f"invalid or duplicate planner case id: {case_id!r}")
        mapped[case_id] = case
    return mapped


def selected_transport_ids(result: dict[str, Any]) -> list[str]:
    selected = result.get("selectedTransports")
    if not isinstance(selected, list):
        raise ValueError("planner result has no selectedTransports array")
    ids: list[str] = []
    for edge in selected:
        edge_id = edge.get("id") if isinstance(edge, dict) else None
        if not isinstance(edge_id, str):
            raise ValueError(f"invalid selected transport edge: {edge!r}")
        ids.append(edge_id)
    return ids


def compare_upstream_adapters(
    corpus: dict[str, Any], embedded: dict[str, Any], external: dict[str, Any]
) -> tuple[list[str], list[str]]:
    """Prove the packaged adapter preserves the independently compiled pinned engine."""
    embedded_cases = case_map(embedded)
    external_cases = case_map(external)
    failures: list[str] = []
    expected_policy_divergences: list[str] = []
    if set(embedded_cases) != set(external_cases):
        failures.append(
            "embedded/external upstream case sets differ: "
            f"{sorted(embedded_cases)} != {sorted(external_cases)}"
        )
        return failures, expected_policy_divergences
    definitions = {case["id"]: case for case in corpus.get("cases", [])}
    compared_fields = (
        "supported",
        "unsupportedReason",
        "termination",
        "reached",
        "endpoint",
        "pathLength",
        "pathCost",
        "selectedTransports",
        "bankVisited",
    )
    for case_id in sorted(embedded_cases):
        packaged = embedded_cases[case_id]
        independent = external_cases[case_id]
        definition = definitions.get(case_id, {})
        if not bool(definition.get("expectedParity", True)):
            reason = definition.get("expectedDivergenceReason")
            expected_policy_divergences.append(f"{case_id}: {reason}")
            continue
        for field in compared_fields:
            if packaged.get(field) != independent.get(field):
                failures.append(
                    f"{case_id}: packaged upstream {field}={packaged.get(field)!r} "
                    f"!= independent upstream {independent.get(field)!r}"
                )
    return failures, expected_policy_divergences


def compare_results(
    corpus: dict[str, Any],
    local: dict[str, Any],
    upstream: dict[str, Any],
    require_all: bool,
) -> tuple[dict[str, Any], int]:
    schema_version = corpus.get("schemaVersion")
    if schema_version != 3:
        raise ValueError(f"unsupported planner corpus schema: {schema_version!r}")
    for engine, result in (("local", local), ("upstream", upstream)):
        if result.get("schemaVersion") != schema_version:
            raise ValueError(
                f"{engine} result schema {result.get('schemaVersion')!r} "
                f"does not match corpus schema {schema_version}"
            )
    local_cases = case_map(local)
    upstream_cases = case_map(upstream)
    comparisons: list[dict[str, Any]] = []
    failures: list[str] = []
    unsupported: list[str] = []

    for definition in corpus.get("cases", []):
        case_id = definition["id"]
        local_case = local_cases.get(case_id)
        upstream_case = upstream_cases.get(case_id)
        if local_case is None or upstream_case is None:
            failures.append(f"{case_id}: missing result from one or both engines")
            continue

        supported = bool(local_case.get("supported")) and bool(
            upstream_case.get("supported")
        )
        row: dict[str, Any] = {
            "id": case_id,
            "category": definition.get("category"),
            "supported": supported,
            "local": local_case,
            "upstream": upstream_case,
        }
        if not supported:
            reason = (
                local_case.get("unsupportedReason")
                or upstream_case.get("unsupportedReason")
                or "engine did not provide a reason"
            )
            unsupported.append(f"{case_id}: {reason}")
            row["status"] = "UNSUPPORTED"
            comparisons.append(row)
            continue

        case_failures: list[str] = []
        expected_reached = bool(definition.get("expectedReached"))
        expected_bank_visited = bool(definition.get("expectedBankVisited", False))
        transport_mode = definition.get("policy", {}).get("transportMode")
        expected_transport_ids = {
            "local": definition.get(
                "expectedLocalTransportIds",
                definition.get("expectedTransportIds", []),
            ),
            "upstream": definition.get(
                "expectedUpstreamTransportIds",
                definition.get("expectedTransportIds", []),
            ),
        }
        expected_parity = bool(definition.get("expectedParity", True))
        divergence_reason = definition.get("expectedDivergenceReason")
        if not expected_parity and not isinstance(divergence_reason, str):
            case_failures.append("expected divergence has no documented reason")
        row["expectedParity"] = expected_parity
        row["expectedDivergenceReason"] = divergence_reason
        for engine, result in (("local", local_case), ("upstream", upstream_case)):
            if bool(result.get("reached")) != expected_reached:
                case_failures.append(
                    f"{engine} reached={result.get('reached')} expected={expected_reached}"
                )
            if bool(result.get("bankVisited")) != expected_bank_visited:
                case_failures.append(
                    f"{engine} bankVisited={result.get('bankVisited')} "
                    f"expected={expected_bank_visited}"
                )
            actual_transport_ids = selected_transport_ids(result)
            if actual_transport_ids != expected_transport_ids[engine]:
                case_failures.append(
                    f"{engine} selected transports {actual_transport_ids} "
                    f"expected {expected_transport_ids[engine]}"
                )
            if transport_mode == "STATIC_COLLISION_ONLY" and result.get("transportsChecked") != 0:
                case_failures.append(
                    f"{engine} checked transports in STATIC_COLLISION_ONLY policy"
                )

        if expected_parity:
            if local_case.get("reached") != upstream_case.get("reached"):
                case_failures.append("reachability differs")
            if local_case.get("termination") != upstream_case.get("termination"):
                case_failures.append(
                    "termination differs: "
                    f"{local_case.get('termination')} != {upstream_case.get('termination')}"
                )
            if local_case.get("selectedTransports") != upstream_case.get("selectedTransports"):
                case_failures.append("exact selected transport edges differ")
            if local_case.get("bankVisited") != upstream_case.get("bankVisited"):
                case_failures.append("bank-visited state differs")
        else:
            compared_fields = (
                "reached",
                "termination",
                "selectedTransports",
                "bankVisited",
                "pathCost",
            )
            if all(local_case.get(field) == upstream_case.get(field) for field in compared_fields):
                case_failures.append("documented planner divergence was not observed")

        target = definition["target"]
        row["localEndpointDistance"] = point_distance(local_case.get("endpoint"), target)
        row["upstreamEndpointDistance"] = point_distance(
            upstream_case.get("endpoint"), target
        )
        if expected_reached:
            if local_case.get("endpoint") != target:
                case_failures.append("local reached a non-target endpoint")
            if upstream_case.get("endpoint") != target:
                case_failures.append("upstream reached a non-target endpoint")
            if expected_parity and local_case.get("pathCost") != upstream_case.get("pathCost"):
                case_failures.append(
                    "reached-path cost differs: "
                    f"{local_case.get('pathCost')} != {upstream_case.get('pathCost')}"
                )

        row["status"] = (
            "FAIL"
            if case_failures
            else "PASS" if expected_parity else "EXPECTED_DIVERGENCE"
        )
        row["differences"] = case_failures
        comparisons.append(row)
        failures.extend(f"{case_id}: {failure}" for failure in case_failures)

    extra_local = sorted(set(local_cases) - {case["id"] for case in corpus["cases"]})
    extra_upstream = sorted(
        set(upstream_cases) - {case["id"] for case in corpus["cases"]}
    )
    if extra_local or extra_upstream:
        failures.append(
            f"unexpected cases: local={extra_local}, upstream={extra_upstream}"
        )

    report = {
        "schemaVersion": schema_version,
        "localRevision": local.get("revision"),
        "upstreamRevision": upstream.get("revision"),
        "comparisons": comparisons,
        "failures": failures,
        "unsupported": unsupported,
    }
    if failures:
        return report, 1
    if unsupported and require_all:
        return report, 2
    return report, 0


def print_summary(report: dict[str, Any]) -> None:
    for comparison in report["comparisons"]:
        local = comparison["local"]
        upstream = comparison["upstream"]
        print(
            f"{comparison['status']:>19}  {comparison['id']:<40} "
            f"cost={local.get('pathCost')}/{upstream.get('pathCost')} "
            f"nodes={local.get('nodesChecked')}/{upstream.get('nodesChecked')} "
            f"transports={selected_transport_ids(local)}/"
            f"{selected_transport_ids(upstream)} "
            f"bank={local.get('bankVisited')}/{upstream.get('bankVisited')} "
            f"ms={local.get('elapsedNanos', -1) / 1_000_000:.1f}/"
            f"{upstream.get('elapsedNanos', -1) / 1_000_000:.1f}"
        )
    for failure in report["failures"]:
        print(f"FAIL: {failure}", file=sys.stderr)
    for blocked in report["unsupported"]:
        print(f"UNSUPPORTED: {blocked}", file=sys.stderr)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--upstream-checkout",
        type=Path,
        help="Existing Skretzo/shortest-path clone; copied before harness injection",
    )
    parser.add_argument(
        "--output-dir",
        type=Path,
        default=REPOSITORY_ROOT / "build/shortest-path-comparison",
    )
    parser.add_argument(
        "--require-all",
        action="store_true",
        help="Exit 2 when either engine explicitly rejects a corpus capability",
    )
    args = parser.parse_args()

    commit = reviewed_commit()
    corpus = load_json(CORPUS_PATH)
    output_dir = args.output_dir.resolve()
    output_dir.mkdir(parents=True, exist_ok=True)
    local_output = output_dir / "local.json"
    embedded_upstream_output = output_dir / "upstream-embedded.json"
    upstream_output = output_dir / "upstream.json"
    report_output = output_dir / "report.json"
    local_revision = capture(["git", "rev-parse", "HEAD"], REPOSITORY_ROOT)
    local_dirty = bool(capture(["git", "status", "--porcelain"], REPOSITORY_ROOT))

    with tempfile.TemporaryDirectory(prefix="microbot-planner-comparison-") as temp:
        upstream_root = Path(temp) / "upstream"
        prepare_upstream(upstream_root, commit, args.upstream_checkout)

        run(
            [
                "./gradlew",
                ":client:exportLocalPlannerComparison",
                f"-PplannerCorpus={CORPUS_PATH}",
                f"-PplannerOutput={local_output}",
                f"-PplannerRevision={local_revision}",
                "--console=plain",
            ],
            REPOSITORY_ROOT,
        )
        run(
            [
                "./gradlew",
                ":client:exportEmbeddedUpstreamPlannerComparison",
                f"-PplannerCorpus={CORPUS_PATH}",
                f"-PplannerOutput={embedded_upstream_output}",
                f"-PplannerRevision={commit}",
                "--console=plain",
            ],
            REPOSITORY_ROOT,
        )
        run(
            [
                "./gradlew",
                "--no-daemon",
                "-I",
                str(UPSTREAM_INIT),
                "exportPlannerComparison",
                f"-PplannerCorpus={CORPUS_PATH}",
                f"-PplannerOutput={upstream_output}",
                f"-PplannerRevision={commit}",
                f"-PplannerRuneliteVersion={runelite_version()}",
                "--console=plain",
            ],
            upstream_root,
        )

    local = load_json(local_output)
    embedded_upstream = load_json(embedded_upstream_output)
    upstream = load_json(upstream_output)
    if upstream.get("revision") != commit:
        raise RuntimeError("upstream adapter did not report the reviewed commit")
    if embedded_upstream.get("revision") != commit:
        raise RuntimeError("packaged upstream adapter did not report the reviewed commit")
    report, exit_code = compare_results(corpus, local, upstream, args.require_all)
    adapter_failures, adapter_policy_divergences = compare_upstream_adapters(
        corpus, embedded_upstream, upstream
    )
    report["embeddedUpstreamRevision"] = embedded_upstream.get("revision")
    report["embeddedUpstreamFailures"] = adapter_failures
    report["embeddedUpstreamExpectedPolicyDivergences"] = adapter_policy_divergences
    if adapter_failures:
        report["failures"].extend(adapter_failures)
        exit_code = 1
    report["runeliteVersion"] = runelite_version()
    report["corpusSha256"] = hashlib.sha256(CORPUS_PATH.read_bytes()).hexdigest()
    report["upstreamIdentityPatchSha256"] = hashlib.sha256(
        UPSTREAM_IDENTITY_PATCH.read_bytes()
    ).hexdigest()
    report["localWorkingTreeDirty"] = local_dirty
    report_output.write_text(
        json.dumps(report, indent=2, sort_keys=True) + "\n", encoding="utf-8"
    )
    print_summary(report)
    print(f"Report: {report_output}")
    return exit_code


if __name__ == "__main__":
    raise SystemExit(main())
