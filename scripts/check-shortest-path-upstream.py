#!/usr/bin/env python3
"""Report whether Skretzo/shortest-path changed since Microbot's reviewed baseline."""

import argparse
import json
import os
import sys
import urllib.error
import urllib.parse
import urllib.request
from pathlib import Path


BASELINE_PATH = Path(__file__).with_name("shortest-path-upstream-baseline.json")
GITHUB_API = "https://api.github.com"


def github_json(path: str) -> object:
    request = urllib.request.Request(
        GITHUB_API + path,
        headers={
            "Accept": "application/vnd.github+json",
            "User-Agent": "microbot-shortest-path-drift-checker",
            "X-GitHub-Api-Version": "2022-11-28",
        },
    )
    token = os.environ.get("GITHUB_TOKEN")
    if token:
        request.add_header("Authorization", f"Bearer {token}")
    with urllib.request.urlopen(request, timeout=30) as response:
        return json.load(response)


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--allow-drift",
        action="store_true",
        help="report drift but return success (useful for scheduled informational checks)",
    )
    parser.add_argument("--json", action="store_true", help="emit machine-readable JSON")
    args = parser.parse_args()

    baseline = json.loads(BASELINE_PATH.read_text(encoding="utf-8"))
    repository = baseline["repository"]
    branch = baseline["branch"]
    reviewed = baseline["reviewedCommit"]

    try:
        branch_data = github_json(
            f"/repos/{repository}/commits/{urllib.parse.quote(branch, safe='')}"
        )
        current = branch_data["sha"]
        changed_files = []
        compare_status = "identical"
        total_commits = 0
        if current != reviewed:
            comparison = github_json(f"/repos/{repository}/compare/{reviewed}...{current}")
            compare_status = comparison["status"]
            total_commits = comparison["total_commits"]
            changed_files = [entry["filename"] for entry in comparison.get("files", [])]
    except (urllib.error.HTTPError, urllib.error.URLError, TimeoutError, KeyError) as error:
        print(f"Unable to check shortest-path upstream: {error}", file=sys.stderr)
        return 1

    scopes = baseline["trackedScopes"]
    relevant = []
    for filename in changed_files:
        matches = [scope for scope in scopes if filename.startswith(scope["upstreamPrefix"])]
        if matches:
            relevant.append(
                {
                    "path": filename,
                    "policies": sorted({match["policy"] for match in matches}),
                }
            )

    result = {
        "repository": repository,
        "branch": branch,
        "reviewedCommit": reviewed,
        "currentCommit": current,
        "status": compare_status,
        "commitsSinceReview": total_commits,
        "changedFiles": changed_files,
        "relevantChanges": relevant,
        "drifted": current != reviewed,
    }

    if args.json:
        print(json.dumps(result, indent=2, sort_keys=True))
    elif current == reviewed:
        print(f"shortest-path is aligned with reviewed commit {reviewed}")
    else:
        print(f"shortest-path drift detected: {reviewed} -> {current} ({total_commits} commits)")
        if relevant:
            print("Relevant changes:")
            for entry in relevant:
                print(f"  {entry['path']} [{', '.join(entry['policies'])}]")
        else:
            print("No files in a tracked planner/data scope changed.")

    return 0 if current == reviewed or args.allow_drift else 2


if __name__ == "__main__":
    raise SystemExit(main())
