#!/usr/bin/env python3
"""Compare upstream and Microbot transport TSVs by semantic route identity."""

from __future__ import annotations

import argparse
import csv
import hashlib
import io
import json
import re
import sys
import tarfile
import tempfile
import urllib.error
import urllib.request
from collections import defaultdict
from dataclasses import dataclass
from pathlib import Path
from typing import Callable, Iterable


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
BASELINE_PATH = Path(__file__).with_name("shortest-path-upstream-baseline.json")
TRANSPORT_BASELINE_PATH = Path(__file__).with_name("shortest-path-transport-baseline.json")
LOCAL_TRANSPORT_ROOT = (
    REPOSITORY_ROOT
    / "runelite-client/src/main/resources/net/runelite/client/plugins/microbot/shortestpath"
)
AGILITY_OBSTACLES_SOURCE = (
    REPOSITORY_ROOT / "runelite-client/src/main/java/net/runelite/client/plugins/agility/Obstacles.java"
)
OBJECT_ID_SOURCES = (
    REPOSITORY_ROOT / "runelite-api/src/main/java/net/runelite/api/gameval/ObjectID.java",
    REPOSITORY_ROOT / "runelite-api/src/main/java/net/runelite/api/gameval/ObjectID1.java",
)
UPSTREAM_TRANSPORT_SUBPATH = Path("src/main/resources/transports")
HOME_TELEPORT_SUFFIX = "home teleport"
NETWORK_IDENTITY_FILES = {
    "charter_ships.tsv",
    "magic_carpets.tsv",
    "ships.tsv",
}

# Exact reviewed exceptions in the otherwise-imported ordinary transport family. These remain
# visible in upstream-only debt; classification records why each identity is not an unexplained
# omission and digest-pins the decision so an upstream coordinate change reopens the review.
ORDINARY_UPSTREAM_ONLY_CLASSIFICATIONS = {
    "supersededPiscatorisGateAnchors": frozenset(
        {
            "2343 3662 0 -> 2343 3663 0",
            "2343 3663 0 -> 2343 3662 0",
            "2344 3662 0 -> 2344 3663 0",
            "2344 3663 0 -> 2344 3662 0",
        }
    ),
    "unsupportedInteractionlessDaeroTransition": frozenset(
        {"2724 2747 0 -> 2770 2793 0"}
    ),
    "unsupportedIdlessMarimStaircases": frozenset(
        {
            "2795 2793 0 -> 2795 2797 1",
            "2795 2797 1 -> 2795 2793 0",
            "2796 2793 0 -> 2796 2797 1",
            "2796 2797 1 -> 2796 2793 0",
            "2799 2793 0 -> 2799 2797 1",
            "2799 2797 1 -> 2799 2793 0",
            "2800 2793 0 -> 2800 2797 1",
            "2800 2797 1 -> 2800 2793 0",
        }
    ),
    "intentionalDisabledVarrockPalaceTrellis": frozenset(
        {
            "3228 3470 0 -> 3228 3472 0",
            "3228 3472 0 -> 3228 3470 0",
        }
    ),
}

ORDINARY_FIELD_DRIFT_CLASSIFICATIONS = {
    "concreteElementalWorkshopWallWithConservativeKeyRequirement": frozenset(
        {
            "2709 3495 0 -> 2709 3496 0",
            "2709 3496 0 -> 2709 3495 0",
        }
    ),
}

# Six reviewed ship identities are already represented by Microbot's current deck or landing
# coordinates. Keep them visible in upstream-only debt while distinguishing representation drift
# from the four genuinely missing Pandemonium routes imported in the same review slice.
SHIP_UPSTREAM_ONLY_CLASSIFICATIONS = {
    "representedByCurrentCorsairCoveLandings": frozenset(
        {
            "2578 2840 0 -> rimmington",
            "rimmington -> 2578 2840 0",
        }
    ),
    "representedByCurrentArdougneShipDeck": frozenset(
        {
            "ardougne -> brimhaven",
            "ardougne -> rimmington",
        }
    ),
    "representedByCurrentVoidOutpostShipDeck": frozenset(
        {
            "2659 2676 0 -> 3041 3202 0",
            "3041 3202 0 -> 2659 2676 0",
        }
    ),
}
COMPARABLE_FIELDS = (
    "objectId",
    "skills",
    "quests",
    "varbits",
    "varplayers",
    "items",
    "cost",
    "members",
    "wildernessLevel",
    "consumable",
    "duration",
)
FIELD_HEADERS = {
    "objectId": ("interaction",),
    "skills": ("skills",),
    "quests": ("quests",),
    "varbits": ("varbits",),
    "varplayers": ("varplayers",),
    "items": ("items",),
    # Cost can be represented in a dedicated local Currency column or in upstream Items.
    "cost": ("currency", "items"),
    "members": ("ismembers",),
    "wildernessLevel": ("wildernesslevel",),
    "consumable": ("consumable",),
    "duration": ("duration",),
}


def canonical_header(value: str) -> str:
    compact = re.sub(r"[^a-z0-9]", "", value.lower())
    aliases = {
        "varplayer": "varplayers",
        "varplayers": "varplayers",
        "itemids": "items",
        "items": "items",
        "menuoptionmenutargetobjectid": "interaction",
        "displayinfo": "displayinfo",
    }
    return aliases.get(compact, compact)


def normalize_value(value: str) -> str:
    return " ".join(value.strip().lower().split())


def normalize_requirement(value: str) -> str:
    tokens = [normalize_value(token) for token in re.split(r";|&&", value) if token.strip()]
    return ";".join(sorted(tokens))


def normalize_display_info(value: str) -> str:
    normalized = normalize_value(value)
    normalized = normalized.replace("grand exchange", "ge")
    normalized = re.sub(r"^(?:\d+|[a-z]):\s*", "", normalized)
    normalized = re.sub(r"\s+minigame teleport$", "", normalized)
    return re.sub(
        r"^rat pits(?: minigame teleport)?:\s*(?:\d+\.\s*)?",
        "rat pits: ",
        normalized,
    )


def interaction_object_id(value: str) -> str:
    value = value.strip()
    if not value:
        return ""
    tail = value.rsplit(";", 1)[-1] if ";" in value else value.rsplit(None, 1)[-1]
    return tail if tail.isdigit() else ""


def normalize_cost(fields: dict[str, str]) -> str:
    currency = normalize_value(fields.get("currency", ""))
    if currency:
        match = re.fullmatch(r"(\d[\d,]*)\s+coins?", currency)
        if match:
            return f"coins={match.group(1).replace(',', '')}"
        return currency

    items = normalize_value(fields.get("items", ""))
    match = re.search(r"(?:^|[;&|\s])coins\s*=\s*(\d[\d,]*)", items)
    return f"coins={match.group(1).replace(',', '')}" if match else ""


def normalize_non_currency_items(fields: dict[str, str]) -> str:
    items = fields.get("items", "")
    without_coins = re.sub(
        r"(?:^|[;&|\s])coins\s*=\s*\d[\d,]*",
        " ",
        items,
        flags=re.IGNORECASE,
    )
    return normalize_requirement(without_coins)


def parse_world_point(value: str) -> tuple[int, int, int] | None:
    parts = value.split()
    if len(parts) != 3:
        return None
    try:
        return tuple(int(part) for part in parts)
    except ValueError:
        return None


@dataclass(frozen=True)
class TransportRow:
    source: str
    line: int
    fields: dict[str, str]

    @property
    def route_key(self) -> tuple[str, str, str] | None:
        origin = normalize_value(self.fields.get("origin", ""))
        destination = normalize_value(self.fields.get("destination", ""))
        if not origin and not destination:
            return None
        label = normalize_display_info(self.fields.get("displayinfo", "")) if not origin else ""
        return origin, destination, label

    @property
    def display_info(self) -> str:
        return normalize_display_info(self.fields.get("displayinfo", ""))

    @property
    def object_id(self) -> int | None:
        value = interaction_object_id(self.fields.get("interaction", ""))
        return int(value) if value else None

    @property
    def comparable_fingerprint(self) -> tuple[tuple[str, str], ...]:
        values = {
            "objectId": interaction_object_id(self.fields.get("interaction", "")),
            "skills": normalize_requirement(self.fields.get("skills", "")),
            "quests": normalize_requirement(self.fields.get("quests", "")),
            "varbits": normalize_requirement(self.fields.get("varbits", "")),
            "varplayers": normalize_requirement(self.fields.get("varplayers", "")),
            "items": normalize_non_currency_items(self.fields),
            "cost": normalize_cost(self.fields),
            "members": normalize_value(self.fields.get("ismembers", "")),
            "wildernessLevel": normalize_value(self.fields.get("wildernesslevel", "")),
            "consumable": normalize_value(self.fields.get("consumable", "")),
            "duration": normalize_value(self.fields.get("duration", "")),
        }
        return tuple((field, values[field]) for field in COMPARABLE_FIELDS)

    def has_comparable_field(self, field: str) -> bool:
        return any(header in self.fields for header in FIELD_HEADERS[field])


@dataclass(frozen=True)
class ComparisonSpec:
    upstream_file: str
    local_file: str
    local_filter: Callable[[TransportRow], bool] = lambda row: True
    network_identity: bool = False


def read_transport_tsv(path: Path) -> list[TransportRow]:
    lines = path.read_text(encoding="utf-8").splitlines()
    if not lines:
        return []
    headers = [canonical_header(header.lstrip("# ")) for header in lines[0].split("\t")]
    rows: list[TransportRow] = []
    for line_number, raw_line in enumerate(lines[1:], start=2):
        if not raw_line.strip() or raw_line.lstrip().startswith("#"):
            continue
        values = next(csv.reader([raw_line], delimiter="\t"))
        fields = {
            header: values[index] if index < len(values) else ""
            for index, header in enumerate(headers)
        }
        rows.append(TransportRow(path.name, line_number, fields))
    return rows


def load_agility_course_object_ids(
    obstacles_source: Path = AGILITY_OBSTACLES_SOURCE,
    object_id_sources: Iterable[Path] = OBJECT_ID_SOURCES,
) -> set[int]:
    """Resolve RuneLite's explicit agility-course obstacle catalog to numeric object ids."""
    source = obstacles_source.read_text(encoding="utf-8")
    marker = "public static final Set<Integer> OBSTACLE_IDS = ImmutableSet.of("
    if marker not in source:
        raise ValueError(f"unable to locate OBSTACLE_IDS in {obstacles_source}")
    block = source.split(marker, 1)[1].split("\n\t);", 1)[0]
    names = set(re.findall(r"ObjectID\.([A-Z0-9_]+)", block))
    if not names:
        raise ValueError(f"OBSTACLE_IDS is empty in {obstacles_source}")

    values: dict[str, int] = {}
    for path in object_id_sources:
        object_source = path.read_text(encoding="utf-8")
        values.update(
            (name, int(value))
            for name, value in re.findall(
                r"public static final int ([A-Z0-9_]+) = (\d+);", object_source
            )
        )
    unresolved = sorted(names - values.keys())
    if unresolved:
        raise ValueError(
            "unresolved agility-course ObjectID constants: " + ", ".join(unresolved)
        )
    return {values[name] for name in names}


def location_labels(rows: Iterable[TransportRow]) -> list[tuple[tuple[int, int, int], str]]:
    labels = []
    for row in rows:
        destination = parse_world_point(normalize_value(row.fields.get("destination", "")))
        label = row.display_info
        if destination is not None and label:
            labels.append((destination, label))
    return labels


def nearest_location_label(
    value: str,
    labels: Iterable[tuple[tuple[int, int, int], str]],
    radius: int = 6,
) -> str | None:
    point = parse_world_point(value)
    if point is None:
        return None
    candidates = []
    for destination, label in labels:
        distance = max(abs(point[0] - destination[0]), abs(point[1] - destination[1]))
        if distance <= radius:
            candidates.append((distance, abs(point[2] - destination[2]), label))
    return min(candidates)[2] if candidates else None


def semantic_route_key(
    row: TransportRow,
    labels: Iterable[tuple[tuple[int, int, int], str]],
    network_identity: bool,
) -> tuple[str, str, str] | None:
    if not network_identity:
        return row.route_key
    origin = normalize_value(row.fields.get("origin", ""))
    destination = normalize_value(row.fields.get("destination", ""))
    if not origin and not destination:
        return None
    origin_identity = nearest_location_label(origin, labels) or origin
    destination_identity = row.display_info or nearest_location_label(destination, labels) or destination
    return origin_identity, destination_identity, ""


def index_rows(
    rows: Iterable[TransportRow],
    network_identity: bool = False,
) -> dict[tuple[str, str, str], list[TransportRow]]:
    rows = list(rows)
    labels = location_labels(rows) if network_identity else []
    indexed: dict[tuple[str, str, str], list[TransportRow]] = defaultdict(list)
    for row in rows:
        key = semantic_route_key(row, labels, network_identity)
        if key is not None:
            indexed[key].append(row)
    return dict(indexed)


def describe_route(key: tuple[str, str, str]) -> str:
    origin, destination, label = key
    route = f"{origin or '<anywhere>'} -> {destination or '<anywhere>'}"
    return f"{route} [{label}]" if label else route


def exact_route_classifications(
    routes: Iterable[str],
    reviewed: dict[str, frozenset[str]],
) -> dict[str, list[str]]:
    """Intersect current debt with exact reviewed identities without hiding unknown routes."""
    route_set = set(routes)
    return {
        name: sorted(route_set & reviewed_routes)
        for name, reviewed_routes in reviewed.items()
    }


def compare_spec(
    upstream_root: Path,
    local_root: Path,
    spec: ComparisonSpec,
) -> dict[str, object]:
    upstream_rows = read_transport_tsv(upstream_root / spec.upstream_file)
    local_rows = [
        row for row in read_transport_tsv(local_root / spec.local_file) if spec.local_filter(row)
    ]
    upstream_index = index_rows(upstream_rows, spec.network_identity)
    local_index = index_rows(local_rows, spec.network_identity)
    upstream_keys = set(upstream_index)
    local_keys = set(local_index)
    shared_keys = upstream_keys & local_keys
    upstream_only_keys = upstream_keys - local_keys

    # A missing schema column is unknown, not an empty requirement. For example, upstream item and
    # minigame TSVs have no membership column while Microbot explicitly records member-only routes.
    # Comparing blank to "Y" manufactures drift that cannot be resolved from the source artifact.
    comparable_fields = tuple(
        field
        for field in COMPARABLE_FIELDS
        if any(row.has_comparable_field(field) for row in upstream_rows)
        and any(row.has_comparable_field(field) for row in local_rows)
    )

    field_drift = []
    for key in sorted(shared_keys):
        upstream_fingerprints = {
            tuple(
                (field, dict(row.comparable_fingerprint)[field])
                for field in comparable_fields
            )
            for row in upstream_index[key]
        }
        local_fingerprints = {
            tuple(
                (field, dict(row.comparable_fingerprint)[field])
                for field in comparable_fields
            )
            for row in local_index[key]
        }
        if upstream_fingerprints != local_fingerprints:
            upstream_by_field = {
                field: {dict(fingerprint)[field] for fingerprint in upstream_fingerprints}
                for field in comparable_fields
            }
            local_by_field = {
                field: {dict(fingerprint)[field] for fingerprint in local_fingerprints}
                for field in comparable_fields
            }
            field_drift.append(
                {
                    "route": describe_route(key),
                    "upstreamVariants": len(upstream_fingerprints),
                    "localVariants": len(local_fingerprints),
                    "differingFields": [
                        field
                        for field in comparable_fields
                        if upstream_by_field[field] != local_by_field[field]
                    ],
                }
            )

    classifications: dict[str, list[str]] = {}
    if spec.upstream_file == "agility_shortcuts.tsv":
        course_object_ids = load_agility_course_object_ids()
        course_keys = [
            key
            for key in sorted(upstream_only_keys)
            if upstream_index[key]
            and all(
                row.object_id is not None and row.object_id in course_object_ids
                for row in upstream_index[key]
            )
        ]
        if course_keys:
            classifications["knownAgilityCourseTraversal"] = [
                describe_route(key) for key in course_keys
            ]
        # Pin cross-file representation debt too. A shortcut copied into transports.tsv remains
        # executable, but silently loses the Agility toggle, membership policy, unavailable-edge
        # blocking and animation-aware executor path. Keep an explicit zero baseline once all such
        # rows are converged so this category cannot return unnoticed.
        generic_path = local_root / "transports.tsv"
        generic_index = (
            index_rows(read_transport_tsv(generic_path)) if generic_path.exists() else {}
        )
        classifications["representedAsLocalGenericTransport"] = [
            describe_route(key)
            for key in sorted(upstream_only_keys & set(generic_index))
        ]
    elif spec.upstream_file == "transports.tsv":
        upstream_only_routes = {describe_route(key) for key in upstream_only_keys}
        classifications.update(
            exact_route_classifications(
                upstream_only_routes, ORDINARY_UPSTREAM_ONLY_CLASSIFICATIONS
            )
        )
    elif spec.upstream_file == "ships.tsv":
        upstream_only_routes = {describe_route(key) for key in upstream_only_keys}
        classifications.update(
            exact_route_classifications(
                upstream_only_routes, SHIP_UPSTREAM_ONLY_CLASSIFICATIONS
            )
        )

    drift_classifications: dict[str, list[str]] = {}
    if spec.upstream_file == "transports.tsv":
        drift_routes = {item["route"] for item in field_drift}
        for name, reviewed_routes in ORDINARY_FIELD_DRIFT_CLASSIFICATIONS.items():
            drift_classifications[name] = sorted(drift_routes & reviewed_routes)

    result = {
        "upstreamFile": spec.upstream_file,
        "localFile": spec.local_file,
        "upstreamRows": len(upstream_rows),
        "localRows": len(local_rows),
        "upstreamRoutes": len(upstream_keys),
        "localRoutes": len(local_keys),
        "sharedRoutes": len(shared_keys),
        "upstreamOnlyRoutes": [describe_route(key) for key in sorted(upstream_only_keys)],
        "localOnlyRoutes": [describe_route(key) for key in sorted(local_keys - upstream_keys)],
        "comparableFieldDrift": field_drift,
    }
    if classifications:
        result["upstreamOnlyClassifications"] = classifications
    if drift_classifications:
        result["comparableFieldDriftClassifications"] = drift_classifications
    return result


def is_home_teleport(row: TransportRow) -> bool:
    return row.display_info.endswith(HOME_TELEPORT_SUFFIX)


def is_quetzal_whistle(row: TransportRow) -> bool:
    return row.display_info.startswith("quetzal whistle:")


def comparison_specs(upstream_root: Path, local_root: Path) -> tuple[list[ComparisonSpec], list[str], list[str]]:
    upstream_files = {path.name for path in upstream_root.glob("*.tsv")}
    local_files = {path.name for path in local_root.glob("*.tsv")}
    shared = sorted(upstream_files & local_files)
    specs = []
    for filename in shared:
        if filename == "teleportation_spells.tsv":
            local_filter = lambda row: not is_home_teleport(row)
        elif filename == "teleportation_items.tsv":
            local_filter = lambda row: not is_quetzal_whistle(row)
        else:
            local_filter = lambda row: True
        specs.append(
            ComparisonSpec(
                filename,
                filename,
                local_filter,
                network_identity=filename in NETWORK_IDENTITY_FILES,
            )
        )
    if "teleportation_spells_home.tsv" in upstream_files and "teleportation_spells.tsv" in local_files:
        specs.append(
            ComparisonSpec(
                "teleportation_spells_home.tsv",
                "teleportation_spells.tsv",
                is_home_teleport,
                network_identity=False,
            )
        )
    if "quetzal_whistle.tsv" in upstream_files and "teleportation_items.tsv" in local_files:
        specs.append(
            ComparisonSpec(
                "quetzal_whistle.tsv",
                "teleportation_items.tsv",
                is_quetzal_whistle,
                network_identity=False,
            )
        )
    compared_upstream = {spec.upstream_file for spec in specs}
    compared_local = {spec.local_file for spec in specs}
    return specs, sorted(upstream_files - compared_upstream), sorted(local_files - compared_local)


def build_report(upstream_root: Path, local_root: Path) -> dict[str, object]:
    specs, upstream_only_files, local_only_files = comparison_specs(upstream_root, local_root)
    comparisons = [compare_spec(upstream_root, local_root, spec) for spec in specs]
    return {
        "comparisons": comparisons,
        "upstreamOnlyFiles": upstream_only_files,
        "localOnlyFiles": local_only_files,
        "totals": {
            "upstreamOnlyRoutes": sum(len(item["upstreamOnlyRoutes"]) for item in comparisons),
            "localOnlyRoutes": sum(len(item["localOnlyRoutes"]) for item in comparisons),
            "sharedRoutes": sum(item["sharedRoutes"] for item in comparisons),
            "comparableFieldDrift": sum(
                len(item["comparableFieldDrift"]) for item in comparisons
            ),
        },
    }


def comparison_id(comparison: dict[str, object]) -> str:
    return f"{comparison['upstreamFile']} -> {comparison['localFile']}"


def content_digest(value: object) -> str:
    encoded = json.dumps(value, sort_keys=True, separators=(",", ":")).encode("utf-8")
    return hashlib.sha256(encoded).hexdigest()


def build_transport_baseline(report: dict[str, object], reviewed_commit: str) -> dict[str, object]:
    comparisons = {}
    for comparison in report["comparisons"]:
        upstream_only = comparison["upstreamOnlyRoutes"]
        local_only = comparison["localOnlyRoutes"]
        drift = comparison["comparableFieldDrift"]
        baseline_comparison = {
            "sharedRoutes": comparison["sharedRoutes"],
            "upstreamOnlyRoutes": len(upstream_only),
            "upstreamOnlyDigest": content_digest(upstream_only),
            "localOnlyRoutes": len(local_only),
            "localOnlyDigest": content_digest(local_only),
            "comparableFieldDrift": len(drift),
            "comparableFieldDriftDigest": content_digest(drift),
        }
        classifications = comparison.get("upstreamOnlyClassifications", {})
        if classifications:
            baseline_comparison["upstreamOnlyClassifications"] = {
                name: {
                    "routes": len(routes),
                    "digest": content_digest(routes),
                }
                for name, routes in sorted(classifications.items())
            }
        drift_classifications = comparison.get("comparableFieldDriftClassifications", {})
        if drift_classifications:
            baseline_comparison["comparableFieldDriftClassifications"] = {
                name: {
                    "routes": len(routes),
                    "digest": content_digest(routes),
                }
                for name, routes in sorted(drift_classifications.items())
            }
        comparisons[comparison_id(comparison)] = baseline_comparison
    return {
        "schemaVersion": 2,
        "reviewedCommit": reviewed_commit,
        "upstreamOnlyFiles": report["upstreamOnlyFiles"],
        "localOnlyFiles": report["localOnlyFiles"],
        "comparisons": comparisons,
    }


def baseline_differences(expected: dict[str, object], actual: dict[str, object]) -> list[str]:
    differences = []
    for field in ("schemaVersion", "reviewedCommit", "upstreamOnlyFiles", "localOnlyFiles"):
        if expected.get(field) != actual.get(field):
            differences.append(f"{field}: expected {expected.get(field)!r}, got {actual.get(field)!r}")

    expected_comparisons = expected.get("comparisons", {})
    actual_comparisons = actual.get("comparisons", {})
    for name in sorted(set(expected_comparisons) | set(actual_comparisons)):
        if name not in expected_comparisons:
            differences.append(f"{name}: new comparison")
            continue
        if name not in actual_comparisons:
            differences.append(f"{name}: comparison removed")
            continue
        for field in actual_comparisons[name]:
            expected_value = expected_comparisons[name].get(field)
            actual_value = actual_comparisons[name][field]
            if expected_value != actual_value:
                differences.append(
                    f"{name} {field}: expected {expected_value!r}, got {actual_value!r}"
                )

    # Reviewed decisions explain why a specific drift digest is intentional. Binding the rationale
    # to the digest makes the decision stale as soon as any route or differing field changes.
    reviewed_differences = expected.get("reviewedDifferences", {})
    if not isinstance(reviewed_differences, dict):
        differences.append("reviewedDifferences: expected an object")
        return differences
    for name, review in reviewed_differences.items():
        if not isinstance(review, dict):
            differences.append(f"{name} reviewed difference: expected an object")
            continue
        rationale = review.get("rationale")
        if not isinstance(rationale, str) or not rationale.strip():
            differences.append(f"{name} reviewed difference: missing rationale")
        comparison = actual_comparisons.get(name)
        if comparison is None:
            differences.append(f"{name} reviewed difference: comparison is absent")
            continue
        reviewed_digest = review.get("comparableFieldDriftDigest")
        actual_digest = comparison.get("comparableFieldDriftDigest")
        if reviewed_digest != actual_digest:
            differences.append(
                f"{name} reviewed difference digest: expected {reviewed_digest!r}, "
                f"got {actual_digest!r}"
            )
    return differences


def print_text_report(report: dict[str, object], limit: int) -> None:
    totals = report["totals"]
    print(
        "Transport route coverage: "
        f"{totals['sharedRoutes']} shared, "
        f"{totals['upstreamOnlyRoutes']} upstream-only, "
        f"{totals['localOnlyRoutes']} Microbot-only"
    )
    print(f"Comparable field drift: {totals['comparableFieldDrift']} routes")
    if report["upstreamOnlyFiles"]:
        print("Upstream-only files: " + ", ".join(report["upstreamOnlyFiles"]))
    if report["localOnlyFiles"]:
        print("Microbot-only files: " + ", ".join(report["localOnlyFiles"]))

    for comparison in report["comparisons"]:
        upstream_only = comparison["upstreamOnlyRoutes"]
        drift = comparison["comparableFieldDrift"]
        if not upstream_only and not drift:
            continue
        print(
            f"\n{comparison['upstreamFile']} -> {comparison['localFile']}: "
            f"{len(upstream_only)} upstream-only routes, {len(drift)} field differences"
        )
        classifications = comparison.get("upstreamOnlyClassifications", {})
        classified_count = sum(len(routes) for routes in classifications.values())
        for name, routes in classifications.items():
            if routes:
                print(f"  classified: {len(routes)} {name}")
        if classified_count:
            print(f"  world-or-unresolved: {len(upstream_only) - classified_count}")
        for route in upstream_only[:limit]:
            print(f"  missing: {route}")
        if len(upstream_only) > limit:
            print(f"  ... {len(upstream_only) - limit} more missing routes")
        drift_classifications = comparison.get("comparableFieldDriftClassifications", {})
        classified_drift_count = sum(len(routes) for routes in drift_classifications.values())
        for name, routes in drift_classifications.items():
            if routes:
                print(f"  classified drift: {len(routes)} {name}")
        if classified_drift_count:
            print(f"  world-or-unresolved drift: {len(drift) - classified_drift_count}")
        for item in drift[:limit]:
            print(
                f"  requirements: {item['route']} "
                f"({item['upstreamVariants']} upstream variants, {item['localVariants']} local variants; "
                f"fields: {', '.join(item['differingFields'])})"
            )
        if len(drift) > limit:
            print(f"  ... {len(drift) - limit} more field differences")


def safe_extract(archive_bytes: bytes, destination: Path) -> Path:
    with tarfile.open(fileobj=io.BytesIO(archive_bytes), mode="r:gz") as archive:
        members = archive.getmembers()
        roots = {Path(member.name).parts[0] for member in members if Path(member.name).parts}
        if len(roots) != 1:
            raise ValueError("unexpected upstream archive layout")
        root_name = next(iter(roots))
        destination_resolved = destination.resolve()
        for member in members:
            target = (destination / member.name).resolve()
            if destination_resolved not in target.parents and target != destination_resolved:
                raise ValueError(f"unsafe archive member: {member.name}")
        archive.extractall(destination)
    return destination / root_name / UPSTREAM_TRANSPORT_SUBPATH


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--upstream-root", type=Path, help="path to upstream transports directory")
    parser.add_argument("--local-root", type=Path, default=LOCAL_TRANSPORT_ROOT)
    parser.add_argument("--json", action="store_true", help="emit the complete report as JSON")
    parser.add_argument(
        "--print-baseline",
        action="store_true",
        help="emit a compact exact semantic-debt baseline as JSON",
    )
    parser.add_argument(
        "--check-baseline",
        action="store_true",
        help="return exit code 3 when the exact semantic-debt baseline changed",
    )
    parser.add_argument(
        "--baseline",
        type=Path,
        default=TRANSPORT_BASELINE_PATH,
        help="semantic-debt baseline used by --check-baseline",
    )
    parser.add_argument("--limit", type=int, default=10, help="examples per changed file in text output")
    parser.add_argument(
        "--fail-on-upstream-only",
        action="store_true",
        help="return exit code 2 when upstream has route identities missing in Microbot",
    )
    args = parser.parse_args()

    baseline = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    try:
        if args.upstream_root:
            report = build_report(args.upstream_root, args.local_root)
        else:
            repository = baseline["repository"]
            commit = baseline["reviewedCommit"]
            url = f"https://github.com/{repository}/archive/{commit}.tar.gz"
            request = urllib.request.Request(url, headers={"User-Agent": "microbot-transport-diff"})
            with urllib.request.urlopen(request, timeout=60) as response:
                archive_bytes = response.read()
            with tempfile.TemporaryDirectory(prefix="microbot-transport-diff-") as temp_dir:
                upstream_root = safe_extract(archive_bytes, Path(temp_dir))
                report = build_report(upstream_root, args.local_root)
    except (OSError, ValueError, KeyError, urllib.error.HTTPError, urllib.error.URLError) as error:
        print(f"Unable to compare Shortest Path transports: {error}", file=sys.stderr)
        return 1

    transport_baseline = build_transport_baseline(report, baseline["reviewedCommit"])
    if args.print_baseline:
        print(json.dumps(transport_baseline, indent=2, sort_keys=True))
    elif args.json:
        print(json.dumps(report, indent=2, sort_keys=True))
    else:
        print_text_report(report, max(0, args.limit))
    if args.fail_on_upstream_only and report["totals"]["upstreamOnlyRoutes"]:
        return 2
    if args.check_baseline:
        try:
            expected_baseline = json.loads(args.baseline.read_text(encoding="utf-8"))
        except (OSError, ValueError) as error:
            print(f"Unable to read transport baseline: {error}", file=sys.stderr)
            return 1
        differences = baseline_differences(expected_baseline, transport_baseline)
        if differences:
            print("Shortest Path transport semantic baseline changed:", file=sys.stderr)
            for difference in differences:
                print(f"  {difference}", file=sys.stderr)
            return 3
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
