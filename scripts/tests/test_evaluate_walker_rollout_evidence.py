import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "evaluate-walker-rollout-evidence.py"
SPEC = importlib.util.spec_from_file_location("walker_rollout_evidence", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)

REVISION = "f" * 40
ENGINE = f"shortest-path-upstream@{REVISION}"


def result(*, rollback=False, started=123, completed=10, arrivals=10):
    matches = 0 if rollback else completed
    failures = completed if rollback else 0
    outcome = {
        "completed": completed,
        "matches": matches,
        "divergences": 0,
        "failures": failures,
    }
    snapshot = {
        "schemaVersion": 2,
        "enabled": True,
        "plannerMode": "UPSTREAM_F2P_CANARY",
        "candidateEngineId": ENGINE,
        "startedAtEpochMillis": started,
        "totals": {
            "submitted": completed,
            "completed": completed,
            "matches": matches,
            "divergences": 0,
            "failures": failures,
            "staleResults": 0,
            "discarded": 0,
            "pending": 0,
            "routeShapeDifferences": 0,
            "upstreamCanarySelections": 0 if rollback else completed,
            "localFallbackDivergences": 0,
            "localFallbackFailures": completed if rollback else 0,
        },
        "execution": {
            "terminal": arrivals,
            "arrived": arrivals,
            "unreachable": 0,
            "exited": 0,
            "recoveryTerminal": 0,
            "recoveryArrived": 0,
            "recoveryUnreachable": 0,
            "recoveryExited": 0,
        },
        "canaryPerformance": {
            "planningSamples": completed,
            "planningNanosTotal": completed * (60_000_000 if rollback else 100_000_000),
            "planningNanosMax": 60_000_000 if rollback else 100_000_000,
            "localSearchNanosTotal": completed * 40_000_000,
            "localSearchNanosMax": 40_000_000,
            "upstreamSearchSamples": 0 if rollback else completed,
            "upstreamSearchNanosTotal": 0 if rollback else completed * 30_000_000,
            "upstreamSearchNanosMax": 0 if rollback else 30_000_000,
        },
        "coverage": {
            "ACTIVE_ROUTE": dict(outcome),
            "UNDERGROUND_COORDINATES": dict(outcome),
        },
        "transportExecutors": {"OBJECT": dict(outcome)},
        "transportTypes": {},
    }
    if rollback:
        snapshot["latestFailure"] = {
            "status": "FAILED",
            "shadowEngineId": ENGINE,
            "failureType": "IllegalStateException",
        }
    return {
        "script": "F2P Web Walker Harness",
        "exitCode": 0,
        "exitReason": "completed",
        "errors": [],
        "plannerMode": "UPSTREAM_F2P_CANARY",
        "expectLocalFallback": rollback,
        "shadowSettled": True,
        "checks": [
            {"name": "F2P-17 route", "passed": True},
            {"name": "planner comparison and selection evidence", "passed": True},
        ],
        "selectedRoutes": ["F2P-17"],
        "routes": [
            {
                "id": "F2P-17",
                "passed": True,
                "repetitions": 5,
                "successfulAttempts": 5,
                "walkerState": "ARRIVED",
            }
        ],
        "shadowEvidence": snapshot,
    }


class WalkerRolloutEvidenceTest(unittest.TestCase):
    def test_representative_pair_is_accepted(self):
        report = MODULE.evaluate(
            result(started=123), result(rollback=True, started=456), REVISION
        )

        self.assertEqual("ACCEPTED", report["verdict"])
        self.assertEqual(10, report["normal"]["totals"]["upstreamCanarySelections"])
        self.assertEqual(10, report["rollback"]["totals"]["localFallbackFailures"])

    def test_normal_canary_must_select_every_match(self):
        normal = result()
        normal["shadowEvidence"]["totals"]["upstreamCanarySelections"] = 9

        report = MODULE.evaluate(normal, result(rollback=True, started=456), REVISION)

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(any("select upstream" in value for value in report["failures"]))

    def test_rollback_must_fall_back_for_every_failure(self):
        rollback = result(rollback=True, started=456)
        rollback["shadowEvidence"]["totals"]["localFallbackFailures"] = 9

        report = MODULE.evaluate(result(), rollback, REVISION)

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(any("locally fall back" in value for value in report["failures"]))

    def test_pair_must_come_from_distinct_client_sessions(self):
        report = MODULE.evaluate(result(), result(rollback=True), REVISION)

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(any("same client session" in value for value in report["failures"]))

    def test_low_comparison_and_arrival_counts_are_insufficient(self):
        report = MODULE.evaluate(
            result(completed=9, arrivals=9),
            result(rollback=True, started=456, completed=9, arrivals=9),
            REVISION,
        )

        self.assertEqual("INSUFFICIENT_EVIDENCE", report["verdict"])
        self.assertTrue(any("completed comparison" in value for value in report["evidenceShortfalls"]))
        self.assertTrue(any("terminal arrival" in value for value in report["evidenceShortfalls"]))

    def test_incomplete_live_route_rejects(self):
        normal = result()
        normal["routes"][0]["successfulAttempts"] = 4

        report = MODULE.evaluate(normal, result(rollback=True, started=456), REVISION)

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(any("every repetition" in value for value in report["failures"]))

    def test_canary_planning_samples_must_cover_every_comparison(self):
        normal = result()
        normal["shadowEvidence"]["canaryPerformance"]["planningSamples"] = 9

        report = MODULE.evaluate(normal, result(rollback=True, started=456), REVISION)

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(any("planningSamples" in value for value in report["failures"]))

    def test_canary_readiness_maximum_is_a_release_gate(self):
        normal = result()
        performance = normal["shadowEvidence"]["canaryPerformance"]
        performance["planningNanosMax"] = 2_000_000_001
        performance["planningNanosTotal"] = 2_900_000_001

        report = MODULE.evaluate(normal, result(rollback=True, started=456), REVISION)

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(any("planning maximum" in value for value in report["failures"]))

    def test_combined_canary_non_search_overhead_is_a_release_gate(self):
        normal = result()
        performance = normal["shadowEvidence"]["canaryPerformance"]
        performance["planningNanosTotal"] = 3_300_000_000

        report = MODULE.evaluate(normal, result(rollback=True, started=456), REVISION)

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(any("non-search overhead" in value for value in report["failures"]))

    def test_wrong_engine_and_failure_message_reject(self):
        rollback = result(rollback=True, started=456)
        rollback["shadowEvidence"]["candidateEngineId"] = "shortest-path-upstream@" + "0" * 40
        rollback["shadowEvidence"]["latestFailure"]["message"] = "sensitive details"

        report = MODULE.evaluate(result(), rollback, REVISION)

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(any("reviewed engine" in value for value in report["failures"]))
        self.assertTrue(any("must not expose" in value for value in report["failures"]))

    def test_markdown_contains_both_release_phases(self):
        report = MODULE.evaluate(
            result(started=123), result(rollback=True, started=456), REVISION
        )
        text = MODULE.markdown(report)

        self.assertIn("`ACCEPTED`", text)
        self.assertIn("Normal canary", text)
        self.assertIn("Forced rollback", text)
        self.assertIn("Ready/local", text)
        self.assertIn("Non-search avg ms", text)


if __name__ == "__main__":
    unittest.main()
