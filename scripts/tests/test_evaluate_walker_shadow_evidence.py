import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "evaluate-walker-shadow-evidence.py"
SPEC = importlib.util.spec_from_file_location("walker_shadow_evidence", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)

REVISION = "f" * 40


def snapshot(*, completed=100, enabled=True, divergences=0, failures=0, started=123):
    matches = completed - divergences - failures
    coverage = {}
    for tag, required in MODULE.DEFAULT_REQUIRED_COVERAGE.items():
        coverage[tag] = {
            "completed": required,
            "matches": required,
            "divergences": 0,
            "failures": 0,
        }
    transport_executors = {}
    for requirement in MODULE.DEFAULT_REQUIRED_EXECUTOR_GROUPS.values():
        executor = requirement["executors"][0]
        minimum = requirement["minimum"]
        transport_executors[executor] = {
            "completed": minimum,
            "matches": minimum,
            "divergences": 0,
            "failures": 0,
        }
    return {
        "schemaVersion": 2,
        "enabled": enabled,
        "candidateEngineId": f"shortest-path-upstream@{REVISION}",
        "startedAtEpochMillis": started,
        "totals": {
            "submitted": completed,
            "completed": completed,
            "matches": matches,
            "divergences": divergences,
            "failures": failures,
            "staleResults": 0,
            "discarded": 0,
            "pending": 0,
            "routeShapeDifferences": 0,
        },
        "coverage": coverage,
        "transportExecutors": transport_executors,
        "transportTypes": {},
        "execution": {
            "terminal": 50,
            "arrived": 50,
            "unreachable": 0,
            "exited": 0,
            "recoveryTerminal": 5,
            "recoveryArrived": 5,
            "recoveryUnreachable": 0,
            "recoveryExited": 0,
        },
        "latest": None,
        "latestRouteShapeDifference": None,
        "latestDivergence": None,
        "latestFailure": None,
    }


def members_snapshot(*, started=123):
    value = snapshot(completed=30, started=started)
    value["plannerMode"] = "SHADOW"
    for tag, required in MODULE.MEMBERS_REQUIRED_COVERAGE.items():
        value["coverage"][tag] = {
            "completed": required,
            "matches": required,
            "divergences": 0,
            "failures": 0,
        }
    value["transportExecutors"].update(
        {
            "FAIRY_RING": {
                "completed": 3,
                "matches": 3,
                "divergences": 0,
                "failures": 0,
            },
            "TERMINAL_TRAVEL": {
                "completed": 2,
                "matches": 2,
                "divergences": 0,
                "failures": 0,
            },
        }
    )
    value["execution"] = {
        "terminal": 10,
        "arrived": 10,
        "unreachable": 0,
        "exited": 0,
        "recoveryTerminal": 1,
        "recoveryArrived": 1,
        "recoveryUnreachable": 0,
        "recoveryExited": 0,
    }
    return value


def evaluate_members(value):
    return MODULE.evaluate(
        value,
        REVISION,
        minimum_completed=MODULE.MEMBERS_MINIMUM_COMPLETED,
        required_coverage=MODULE.MEMBERS_REQUIRED_COVERAGE,
        minimum_distinct_transport_executors=(
            MODULE.MEMBERS_MINIMUM_DISTINCT_TRANSPORT_EXECUTORS
        ),
        required_executor_groups=MODULE.MEMBERS_REQUIRED_EXECUTOR_GROUPS,
        minimum_walker_arrivals=MODULE.MEMBERS_MINIMUM_WALKER_ARRIVALS,
        minimum_recovery_arrivals=MODULE.MEMBERS_MINIMUM_RECOVERY_ARRIVALS,
        minimum_sessions=1,
        expected_planner_mode="SHADOW",
        evidence_profile="members",
    )


class WalkerShadowEvidenceTest(unittest.TestCase):
    def test_members_profile_accepts_members_requirement_and_network_evidence(self):
        result = evaluate_members(members_snapshot())

        self.assertEqual("ACCEPTED", result["verdict"])
        self.assertEqual("members", result["evidenceProfile"])
        self.assertEqual("SHADOW", result["plannerMode"])

    def test_members_profile_rejects_canary_mode(self):
        value = members_snapshot()
        value["plannerMode"] = "UPSTREAM_F2P_CANARY"

        result = evaluate_members(value)

        self.assertEqual("REJECTED", result["verdict"])
        self.assertTrue(any("plannerMode" in item for item in result["failures"]))

    def test_members_profile_requires_non_item_requirement_evidence(self):
        value = members_snapshot()
        value["coverage"]["SELECTS_NON_ITEM_REQUIREMENT_GATED_TRANSPORT"] = {
            "completed": 0,
            "matches": 0,
            "divergences": 0,
            "failures": 0,
        }

        result = evaluate_members(value)

        self.assertEqual("INSUFFICIENT_EVIDENCE", result["verdict"])
        self.assertTrue(
            any("NON_ITEM_REQUIREMENT" in item for item in result["evidenceShortfalls"])
        )

    def test_members_profile_requires_multiple_fresh_sessions(self):
        merged = MODULE.merge_snapshots(
            [members_snapshot(started=123), members_snapshot(started=456)],
            REVISION,
            expected_planner_mode="SHADOW",
        )
        result = MODULE.evaluate(
            merged,
            REVISION,
            minimum_completed=MODULE.MEMBERS_MINIMUM_COMPLETED,
            required_coverage=MODULE.MEMBERS_REQUIRED_COVERAGE,
            minimum_distinct_transport_executors=(
                MODULE.MEMBERS_MINIMUM_DISTINCT_TRANSPORT_EXECUTORS
            ),
            required_executor_groups=MODULE.MEMBERS_REQUIRED_EXECUTOR_GROUPS,
            minimum_walker_arrivals=MODULE.MEMBERS_MINIMUM_WALKER_ARRIVALS,
            minimum_recovery_arrivals=MODULE.MEMBERS_MINIMUM_RECOVERY_ARRIVALS,
            minimum_sessions=MODULE.MEMBERS_MINIMUM_SESSIONS,
            expected_planner_mode="SHADOW",
            evidence_profile="members",
        )

        self.assertEqual("INSUFFICIENT_EVIDENCE", result["verdict"])
        self.assertTrue(any("fresh client session" in item for item in result["evidenceShortfalls"]))

    def test_multiple_fresh_sessions_are_aggregated(self):
        merged = MODULE.merge_snapshots(
            [snapshot(started=123), snapshot(started=456)], REVISION
        )

        report = MODULE.evaluate(merged, REVISION)

        self.assertEqual("ACCEPTED", report["verdict"])
        self.assertEqual(2, report["sessionCount"])
        self.assertEqual(200, report["totals"]["completed"])
        self.assertEqual(100, report["execution"]["arrived"])

    def test_duplicate_client_session_cannot_be_counted_twice(self):
        with self.assertRaisesRegex(ValueError, "same client session twice"):
            MODULE.merge_snapshots([snapshot(), snapshot()], REVISION)

    def test_invalid_member_session_cannot_hide_in_aggregate(self):
        invalid = snapshot(started=456)
        invalid["totals"]["completed"] += 1

        with self.assertRaisesRegex(ValueError, "internally invalid"):
            MODULE.merge_snapshots([snapshot(), invalid], REVISION)

    def test_representative_clean_snapshot_is_accepted(self):
        report = MODULE.evaluate(snapshot(), REVISION)

        self.assertEqual("ACCEPTED", report["verdict"])
        self.assertEqual([], report["failures"])
        self.assertEqual([], report["evidenceShortfalls"])

    def test_missing_routes_and_disabled_shadow_are_insufficient(self):
        value = snapshot(completed=20, enabled=False)
        for coverage in value["coverage"].values():
            coverage["completed"] = 0
            coverage["matches"] = 0

        report = MODULE.evaluate(value, REVISION)

        self.assertEqual("INSUFFICIENT_EVIDENCE", report["verdict"])
        self.assertTrue(any("not enabled" in item for item in report["evidenceShortfalls"]))
        self.assertTrue(any("ACTIVE_ROUTE" in item for item in report["evidenceShortfalls"]))

    def test_any_divergence_rejects(self):
        value = snapshot(divergences=1)
        value["coverage"]["ACTIVE_ROUTE"] = {
            "completed": 75,
            "matches": 74,
            "divergences": 1,
            "failures": 0,
        }
        value["latestDivergence"] = {
            "status": "DIVERGENCE",
            "shadowEngineId": f"shortest-path-upstream@{REVISION}",
            "invocation": "ACTIVE_ROUTE",
            "terminationMatches": True,
            "endpointMatches": True,
            "costComparable": True,
            "costMatches": False,
            "selectedTransportsMatch": True,
            "pathMatches": False,
            "failureType": None,
        }

        report = MODULE.evaluate(value, REVISION)

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(any("divergence" in item for item in report["failures"]))

    def test_divergence_without_semantic_mismatch_rejects_diagnostic(self):
        value = snapshot(divergences=1)
        value["latestDivergence"] = {
            "status": "DIVERGENCE",
            "shadowEngineId": f"shortest-path-upstream@{REVISION}",
            "invocation": "ACTIVE_ROUTE",
            "terminationMatches": True,
            "endpointMatches": True,
            "costComparable": True,
            "costMatches": True,
            "selectedTransportsMatch": True,
            "pathMatches": False,
            "failureType": None,
        }

        report = MODULE.evaluate(value, REVISION)

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(
            any("does not contain" in item for item in report["failures"])
        )

    def test_planner_failure_requires_preserved_exception_class(self):
        value = snapshot(failures=1)
        value["latestFailure"] = {
            "status": "FAILED",
            "shadowEngineId": f"shortest-path-upstream@{REVISION}",
            "invocation": "ACTIVE_ROUTE",
            "terminationMatches": False,
            "endpointMatches": False,
            "costComparable": False,
            "costMatches": False,
            "selectedTransportsMatch": False,
            "pathMatches": False,
            "failureType": "IllegalStateException",
        }

        report = MODULE.evaluate(value, REVISION)

        self.assertEqual("REJECTED", report["verdict"])
        self.assertEqual("FAILED", report["latestFailure"]["status"])

    def test_wrong_pinned_engine_rejects(self):
        value = snapshot()
        value["candidateEngineId"] = "shortest-path-upstream@" + "0" * 40

        report = MODULE.evaluate(value, REVISION)

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(any("candidateEngineId" in item for item in report["failures"]))

    def test_pending_work_prevents_acceptance(self):
        value = snapshot()
        value["totals"]["submitted"] += 1
        value["totals"]["pending"] = 1

        report = MODULE.evaluate(value, REVISION)

        self.assertEqual("INSUFFICIENT_EVIDENCE", report["verdict"])
        self.assertTrue(any("still pending" in item for item in report["evidenceShortfalls"]))

    def test_markdown_contains_coverage_table(self):
        text = MODULE.markdown(MODULE.evaluate(snapshot(), REVISION))

        self.assertIn("`ACCEPTED`", text)
        self.assertIn("| ACTIVE_REPLAN |", text)
        self.assertIn("Transport executor diversity", text)

    def test_transport_executor_diversity_is_required(self):
        value = snapshot()
        value["transportExecutors"] = {
            "OBJECT": {
                "completed": 20,
                "matches": 20,
                "divergences": 0,
                "failures": 0,
            }
        }

        report = MODULE.evaluate(value, REVISION)

        self.assertEqual("INSUFFICIENT_EVIDENCE", report["verdict"])
        self.assertTrue(
            any("distinct transport executor" in item for item in report["evidenceShortfalls"])
        )

    def test_route_shape_difference_is_reported_as_diagnostic(self):
        value = snapshot()
        value["totals"]["routeShapeDifferences"] = 3
        value["latestRouteShapeDifference"] = {
            "status": "MATCH",
            "shadowEngineId": f"shortest-path-upstream@{REVISION}",
            "invocation": "ACTIVE_ROUTE",
            "terminationMatches": True,
            "endpointMatches": True,
            "costComparable": True,
            "costMatches": True,
            "selectedTransportsMatch": True,
            "pathMatches": False,
        }

        report = MODULE.evaluate(value, REVISION)

        self.assertEqual("ACCEPTED", report["verdict"])
        self.assertTrue(any("route shape" in item for item in report["warnings"]))
        self.assertEqual("MATCH", report["latestRouteShapeDifference"]["status"])

    def test_inconsistent_route_shape_diagnostic_rejects(self):
        value = snapshot()
        value["latestRouteShapeDifference"] = {
            "status": "MATCH",
            "shadowEngineId": f"shortest-path-upstream@{REVISION}",
            "terminationMatches": True,
            "endpointMatches": True,
            "costComparable": True,
            "costMatches": True,
            "selectedTransportsMatch": True,
            "pathMatches": False,
        }

        report = MODULE.evaluate(value, REVISION)

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(
            any("latestRouteShapeDifference" in item for item in report["failures"])
        )

    def test_executor_outcome_cannot_hide_divergence(self):
        value = snapshot()
        value["transportExecutors"]["OBJECT"] = {
            "completed": 5,
            "matches": 4,
            "divergences": 1,
            "failures": 0,
        }

        report = MODULE.evaluate(value, REVISION)

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(
            any("transportExecutors.OBJECT" in item for item in report["failures"])
        )

    def test_old_snapshot_schema_is_rejected(self):
        value = snapshot()
        value["schemaVersion"] = 1

        with self.assertRaisesRegex(ValueError, "schemaVersion"):
            MODULE.evaluate(value, REVISION)

    def test_failed_recovery_rejects_live_evidence(self):
        value = snapshot()
        value["execution"].update(
            {
                "terminal": 51,
                "unreachable": 1,
                "recoveryTerminal": 6,
                "recoveryUnreachable": 1,
            }
        )

        report = MODULE.evaluate(value, REVISION)

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(any("recovery-triggered" in item for item in report["failures"]))

    def test_execution_arrivals_are_required(self):
        value = snapshot()
        value["execution"].update(
            {
                "terminal": 0,
                "arrived": 0,
                "recoveryTerminal": 0,
                "recoveryArrived": 0,
            }
        )

        report = MODULE.evaluate(value, REVISION)

        self.assertEqual("INSUFFICIENT_EVIDENCE", report["verdict"])
        self.assertTrue(any("blocking walk arrival" in item for item in report["evidenceShortfalls"]))


if __name__ == "__main__":
    unittest.main()
