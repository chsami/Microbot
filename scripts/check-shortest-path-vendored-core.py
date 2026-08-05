#!/usr/bin/env python3
"""Verify the packaged Shortest Path planner pin and declared adapter patch surface."""

import argparse
import hashlib
import json
import subprocess
import sys
from pathlib import Path
from typing import Any


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
DEFAULT_ROOT = REPOSITORY_ROOT / "runelite-client" / "src" / "upstreamPlanner"
DEFAULT_BASELINE = Path(__file__).with_name(
    "shortest-path-vendored-core-baseline.json"
)
SOURCE_PREFIX = Path("src/main/java")
METADATA_FILES = ("ADAPTER_PATCHES.md", "LICENSE", "README.md", "UPSTREAM_REVISION")


def file_sha256(path: Path) -> str:
    return hashlib.sha256(path.read_bytes()).hexdigest()


def source_manifest(root: Path) -> dict[str, str]:
    source_root = root / SOURCE_PREFIX
    return {
        path.relative_to(source_root).as_posix(): file_sha256(path)
        for path in sorted(source_root.rglob("*.java"))
    }


def manifest_digest(manifest: dict[str, str]) -> str:
    digest = hashlib.sha256()
    for relative_path, file_digest in sorted(manifest.items()):
        digest.update(relative_path.encode("utf-8"))
        digest.update(b"\0")
        digest.update(file_digest.encode("ascii"))
        digest.update(b"\n")
    return digest.hexdigest()


def current_baseline_values(root: Path) -> dict[str, Any]:
    manifest = source_manifest(root)
    return {
        "sourceFileCount": len(manifest),
        "sourceTreeSha256": manifest_digest(manifest),
        "metadataSha256": {
            name: file_sha256(root / name) for name in METADATA_FILES
        },
    }


def verify_offline(root: Path, baseline: dict[str, Any]) -> list[str]:
    failures: list[str] = []
    revision_path = root / "UPSTREAM_REVISION"
    revision = revision_path.read_text(encoding="utf-8").strip()
    expected_revision = baseline.get("revision")
    if revision != expected_revision:
        failures.append(
            f"UPSTREAM_REVISION is {revision!r}, expected {expected_revision!r}"
        )

    current = current_baseline_values(root)
    if current["sourceFileCount"] != baseline.get("sourceFileCount"):
        failures.append(
            "vendored Java source count is "
            f"{current['sourceFileCount']}, expected {baseline.get('sourceFileCount')}"
        )
    if current["sourceTreeSha256"] != baseline.get("sourceTreeSha256"):
        failures.append(
            "vendored Java source tree digest changed: "
            f"{current['sourceTreeSha256']} != {baseline.get('sourceTreeSha256')}"
        )

    expected_metadata = baseline.get("metadataSha256", {})
    for name, digest in current["metadataSha256"].items():
        if digest != expected_metadata.get(name):
            failures.append(
                f"vendored metadata {name} digest changed: "
                f"{digest} != {expected_metadata.get(name)}"
            )

    manifest = source_manifest(root)
    patched = set(baseline.get("patchedUpstreamFiles", []))
    added = set(baseline.get("adapterAddedFiles", []))
    patch_policy = baseline.get("patchSurfacePolicy")
    if not isinstance(patch_policy, dict):
        failures.append("patchSurfacePolicy must be an object")
    else:
        maximum_patched = patch_policy.get("maximumPatchedUpstreamFiles")
        maximum_added = patch_policy.get("maximumAdapterAddedFiles")
        if not isinstance(maximum_patched, int) or isinstance(maximum_patched, bool) \
                or maximum_patched < 0:
            failures.append(
                "patchSurfacePolicy.maximumPatchedUpstreamFiles must be a non-negative integer"
            )
        elif len(patched) > maximum_patched:
            failures.append(
                f"patched upstream file count {len(patched)} exceeds reviewed budget "
                f"{maximum_patched}"
            )
        if not isinstance(maximum_added, int) or isinstance(maximum_added, bool) \
                or maximum_added < 0:
            failures.append(
                "patchSurfacePolicy.maximumAdapterAddedFiles must be a non-negative integer"
            )
        elif len(added) > maximum_added:
            failures.append(
                f"adapter-added file count {len(added)} exceeds reviewed budget {maximum_added}"
            )
        if patch_policy.get("growthRequiresAdrAmendment") is not True:
            failures.append(
                "patchSurfacePolicy.growthRequiresAdrAmendment must be true"
            )
    overlap = patched & added
    if overlap:
        failures.append(
            f"files cannot be both patched and adapter-added: {sorted(overlap)}"
        )
    missing_declared = (patched | added) - set(manifest)
    if missing_declared:
        failures.append(
            f"declared adapter files are missing: {sorted(missing_declared)}"
        )
    return failures


def git_head(checkout: Path) -> str:
    completed = subprocess.run(
        ["git", "rev-parse", "HEAD"],
        cwd=checkout,
        check=True,
        capture_output=True,
        text=True,
    )
    return completed.stdout.strip()


def verify_against_checkout(
    root: Path, baseline: dict[str, Any], checkout: Path
) -> list[str]:
    failures: list[str] = []
    expected_revision = baseline["revision"]
    try:
        checkout_revision = git_head(checkout)
    except (OSError, subprocess.CalledProcessError) as error:
        return [f"cannot read upstream checkout revision: {error}"]
    if checkout_revision != expected_revision:
        failures.append(
            f"upstream checkout is {checkout_revision}, expected {expected_revision}"
        )

    manifest = source_manifest(root)
    patched = set(baseline.get("patchedUpstreamFiles", []))
    added = set(baseline.get("adapterAddedFiles", []))
    upstream_source = checkout / SOURCE_PREFIX
    observed_patched: set[str] = set()
    observed_added: set[str] = set()
    for relative_path in manifest:
        vendored_path = root / SOURCE_PREFIX / relative_path
        upstream_path = upstream_source / relative_path
        if not upstream_path.exists():
            observed_added.add(relative_path)
            if relative_path not in added:
                failures.append(
                    f"undeclared adapter-added source: {relative_path}"
                )
            continue
        if vendored_path.read_bytes() != upstream_path.read_bytes():
            observed_patched.add(relative_path)
            if relative_path not in patched:
                failures.append(f"undeclared upstream source patch: {relative_path}")

    stale_patches = patched - observed_patched
    if stale_patches:
        failures.append(
            f"declared patches no longer differ from upstream: {sorted(stale_patches)}"
        )
    stale_added = added - observed_added
    if stale_added:
        failures.append(
            f"declared adapter-added files now exist upstream: {sorted(stale_added)}"
        )
    if (root / "LICENSE").read_bytes() != (checkout / "LICENSE").read_bytes():
        failures.append("vendored LICENSE does not match the pinned upstream checkout")
    return failures


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline", type=Path, default=DEFAULT_BASELINE)
    parser.add_argument("--vendored-root", type=Path, default=DEFAULT_ROOT)
    parser.add_argument(
        "--upstream-checkout",
        type=Path,
        help="also prove undeclared files are byte-identical to this pinned checkout",
    )
    parser.add_argument(
        "--print-current",
        action="store_true",
        help="print current digest fields for an explicitly reviewed baseline update",
    )
    args = parser.parse_args()

    if args.print_current:
        print(json.dumps(current_baseline_values(args.vendored_root), indent=2))
        return 0

    try:
        baseline = json.loads(args.baseline.read_text(encoding="utf-8"))
        failures = verify_offline(args.vendored_root, baseline)
        if args.upstream_checkout:
            failures.extend(
                verify_against_checkout(
                    args.vendored_root, baseline, args.upstream_checkout
                )
            )
    except (OSError, KeyError, ValueError, json.JSONDecodeError) as error:
        print(f"Unable to verify vendored shortest-path core: {error}", file=sys.stderr)
        return 1

    if failures:
        print("Vendored shortest-path core verification failed:", file=sys.stderr)
        for failure in failures:
            print(f"  - {failure}", file=sys.stderr)
        return 1
    print(
        "vendored shortest-path core matches reviewed revision "
        f"{baseline['revision']} with {baseline['sourceFileCount']} Java files"
    )
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
