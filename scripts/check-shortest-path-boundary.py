#!/usr/bin/env python3
"""Reject shortest-path plugin-state access outside Microbot's compatibility seam."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_SOURCE_ROOT = (
    REPOSITORY_ROOT
    / "runelite-client/src/main/java/net/runelite/client/plugins/microbot"
)
FACADE = Path("util/walker/Rs2PathApi.java")
PLUGIN_IDENTITY = Path("breakhandler/breakhandlerv2/MicrobotPluginChoice.java")
PLUGIN_IMPORT = (
    "import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;"
)
PLUGIN_REFERENCE = re.compile(r"\bShortestPathPlugin\s*(?:\.|;)")
DIRECT_PATHFINDER_REFERENCE = re.compile(
    r"import\s+net\.runelite\.client\.plugins\.microbot\.shortestpath\.pathfinder\.Pathfinder\s*;"
    r"|\bnew\s+Pathfinder\s*\("
)
DIRECT_PATHFINDER_CONSTRUCTION = re.compile(r"\bnew\s+Pathfinder\s*\(")
DIRECT_PATHFINDER_CONFIG_REFERENCE = re.compile(
    r"import\s+net\.runelite\.client\.plugins\.microbot\.shortestpath\.pathfinder\.PathfinderConfig\s*;"
    r"|\bRs2PathApi\s*\.\s*getPathfinderConfig\s*\("
)
DIRECT_ACTIVE_PATHFINDER_REFERENCE = re.compile(
    r"\bRs2PathApi\s*\.\s*getPathfinder\s*\("
)
LEGACY_ROUTE_HANDOFF_REFERENCE = re.compile(r"\b(?:LegacyRoutePlan|planLegacy)\b")
BANK_ROUTE_REPLAN_REFERENCE = re.compile(
    r"getMissingTransportEdges\s*\(\s*getTransportEdgesForDestination\s*\("
)
SHORTEST_PATH_IMPORT = re.compile(
    r"^\s*import\s+net\.runelite\.client\.plugins\.microbot\.shortestpath(?:\.|;)"
)
CONCRETE_TRANSPORT_IMPORT = re.compile(
    r"^\s*import\s+net\.runelite\.client\.plugins\.microbot\.shortestpath\.Transport\s*;"
)
EXECUTOR_REGISTRY_REFERENCE = re.compile(r"\bTransportExecutionRegistry\b")
PLANNER_CORE = Path("shortestpath/pathfinder")
MIGRATED_SYNCHRONOUS_PLANNING_SCOPES = (
    Path("util/bank"),
    Path("util/depositbox"),
    Path("util/leaguetransport"),
    Path("util/npc"),
    Path("util/skills/slayer"),
    Path("util/walker/banking"),
    Path("util/walker/lifecycle"),
)
MIGRATED_SYNCHRONOUS_PLANNING_FILES = {
    Path("util/walker/Rs2Walker.java"),
}
MIGRATED_CONFIG_FILES = {
    Path("util/walker/Rs2Walker.java"),
}
MIGRATED_CONCRETE_TRANSPORT_SCOPES = (
    Path("util/walker/door"),
    Path("util/walker/obstacle"),
    Path("util/walker/recovery"),
)
MIGRATED_CONCRETE_TRANSPORT_FILES = {
    Path("util/walker/Rs2HotAirBalloon.java"),
}
OWNED_ROUTE_VALUES = {
    Path("util/walker/Rs2ActiveRouteStatus.java"),
    Path("util/walker/Rs2RouteRequest.java"),
    Path("util/walker/Rs2RouteResult.java"),
    Path("util/walker/Rs2RouteMetrics.java"),
	Path("util/walker/Rs2PlanningSnapshot.java"),
	Path("util/walker/Rs2PlannerShadowComparison.java"),
	Path("util/walker/Rs2PlannerShadowStats.java"),
    Path("util/walker/Rs2RoutePlanner.java"),
    Path("util/walker/Rs2RoutePolicy.java"),
    Path("util/walker/Rs2RouteStep.java"),
    Path("util/walker/Rs2RouteTermination.java"),
    Path("util/walker/Rs2TransportEdge.java"),
    Path("util/walker/Rs2TransportExecutor.java"),
    Path("util/walker/Rs2TransportItemRequirement.java"),
    Path("util/walker/Rs2TransportLoadout.java"),
    Path("util/walker/Rs2TransportType.java"),
}


def allowed_identity_reference(line: str) -> bool:
    stripped = line.strip()
    if stripped == PLUGIN_IMPORT:
        return True
    without_class_literal = re.sub(
        r"\bShortestPathPlugin\s*\.\s*class\b", "", line
    )
    return without_class_literal != line and not PLUGIN_REFERENCE.search(
        without_class_literal
    )


def violations(source_root: Path) -> list[str]:
    failures: list[str] = []
    for java_file in sorted(source_root.rglob("*.java")):
        relative = java_file.relative_to(source_root)
        if relative.is_relative_to(PLANNER_CORE):
            for line_number, line in enumerate(
                java_file.read_text(encoding="utf-8").splitlines(), start=1
            ):
                if EXECUTOR_REGISTRY_REFERENCE.search(line):
                    failures.append(
                        f"planner-executor-coupling {relative}:{line_number}: {line.strip()}"
                    )
        if relative.parts and relative.parts[0] == "shortestpath":
            continue
        lines = java_file.read_text(encoding="utf-8").splitlines()
        source = "\n".join(lines)
        for match in BANK_ROUTE_REPLAN_REFERENCE.finditer(source):
            line_number = source.count("\n", 0, match.start()) + 1
            failures.append(
                f"bank-route-replan {relative}:{line_number}: "
                "consume exact compared bank-route edges instead"
            )
        for line_number, line in enumerate(lines, start=1):
            if LEGACY_ROUTE_HANDOFF_REFERENCE.search(line):
                failures.append(
                    f"legacy-route-handoff {relative}:{line_number}: {line.strip()}"
                )
        if relative == FACADE:
            continue

        in_migrated_scope = any(
            relative.is_relative_to(scope)
            for scope in MIGRATED_SYNCHRONOUS_PLANNING_SCOPES
        )
        is_owned_route_value = relative in OWNED_ROUTE_VALUES
        in_migrated_transport_scope = any(
            relative.is_relative_to(scope)
            for scope in MIGRATED_CONCRETE_TRANSPORT_SCOPES
        )

        for line_number, line in enumerate(lines, start=1):
            if PLUGIN_REFERENCE.search(line):
                if not (
                    relative == PLUGIN_IDENTITY and allowed_identity_reference(line)
                ):
                    failures.append(
                        f"plugin-state {relative}:{line_number}: {line.strip()}"
                    )

            if in_migrated_scope and DIRECT_PATHFINDER_REFERENCE.search(line):
                failures.append(
                    f"direct-pathfinder {relative}:{line_number}: {line.strip()}"
                )

            if in_migrated_scope and DIRECT_PATHFINDER_CONFIG_REFERENCE.search(line):
                failures.append(
                    f"direct-pathfinder-config {relative}:{line_number}: {line.strip()}"
                )

            if (
                relative in MIGRATED_CONFIG_FILES
                and DIRECT_PATHFINDER_CONFIG_REFERENCE.search(line)
            ):
                failures.append(
                    f"direct-pathfinder-config {relative}:{line_number}: {line.strip()}"
                )

            if DIRECT_ACTIVE_PATHFINDER_REFERENCE.search(line):
                failures.append(
                    f"direct-active-pathfinder {relative}:{line_number}: {line.strip()}"
                )

            if (
                relative in MIGRATED_SYNCHRONOUS_PLANNING_FILES
                and DIRECT_PATHFINDER_CONSTRUCTION.search(line)
            ):
                failures.append(
                    f"direct-pathfinder-construction {relative}:{line_number}: {line.strip()}"
                )

            if is_owned_route_value and SHORTEST_PATH_IMPORT.search(line):
                failures.append(
                    f"owned-value-dependency {relative}:{line_number}: {line.strip()}"
                )

            if (
                (in_migrated_transport_scope or relative in MIGRATED_CONCRETE_TRANSPORT_FILES)
                and CONCRETE_TRANSPORT_IMPORT.search(line)
            ):
                failures.append(
                    f"concrete-transport {relative}:{line_number}: {line.strip()}"
                )
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--source-root",
        type=Path,
        default=DEFAULT_SOURCE_ROOT,
        help="Microbot Java source root (used by the regression tests)",
    )
    args = parser.parse_args()

    failures = violations(args.source_root.resolve())
    if failures:
        print("Shortest-path boundary violations (use util/walker/Rs2PathApi):")
        for failure in failures:
            print(f"  {failure}")
        return 1

    print("shortest-path plugin-state boundary is clean")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
