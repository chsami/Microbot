import importlib.util
import unittest
from pathlib import Path


SCRIPT = (
    Path(__file__).resolve().parents[1]
    / "report-shortest-path-planner-performance.py"
)
SPEC = importlib.util.spec_from_file_location("planner_performance", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


def corpus():
    return {
        "schemaVersion": 3,
        "cases": [
            {
                "id": "core",
                "category": "overland",
                "policy": {"transportMode": "STATIC_COLLISION_ONLY"},
            },
            {
                "id": "bank",
                "category": "bank",
                "policy": {"transportMode": "BANK_AWARE_EXPLICIT_CATALOG"},
            },
            {
                "id": "policy-divergence",
                "category": "spell-provider-divergence",
                "policy": {"transportMode": "EXPLICIT_CATALOG"},
                "expectedParity": False,
            },
        ],
    }


def engine_result(elapsed_nanos, nodes=10, heap=1024):
    return {
        "elapsedNanos": elapsed_nanos,
        "nodesChecked": nodes,
        "peakHeapDeltaBytes": heap,
    }


def comparison(case_id, local_nanos=10_000_000, upstream_nanos=12_000_000):
    return {
        "id": case_id,
        "status": "PASS",
        "local": engine_result(local_nanos),
        "upstream": engine_result(upstream_nanos, nodes=20, heap=2048),
    }


def sample(*, dirty=False, upstream_nanos=12_000_000):
    return {
        "schemaVersion": 3,
        "localRevision": "local",
        "upstreamRevision": "upstream",
        "embeddedUpstreamRevision": "upstream",
        "runeliteVersion": "runelite",
        "corpusSha256": "corpus",
        "upstreamIdentityPatchSha256": "patch",
        "localWorkingTreeDirty": dirty,
        "failures": [],
        "unsupported": [],
        "embeddedUpstreamFailures": [],
        "comparisons": [
            comparison("core", upstream_nanos=upstream_nanos),
            comparison("bank"),
            {
                **comparison("policy-divergence"),
                "status": "EXPECTED_DIVERGENCE",
            },
        ],
    }


class PlannerPerformanceReportTest(unittest.TestCase):
    def test_one_dirty_sample_is_insufficient_and_excludes_non_core_workflows(self):
        report = MODULE.evaluate_reports([sample(dirty=True)], corpus())

        self.assertEqual("INSUFFICIENT_EVIDENCE", report["verdict"])
        self.assertEqual(["core"], report["comparability"]["includedCaseIds"])
        self.assertEqual(2, len(report["comparability"]["excludedCases"]))
        self.assertTrue(
            any("localWorkingTreeDirty" in value for value in report["evidenceShortfalls"])
        )
        self.assertTrue(
            any("at least 5" in value for value in report["evidenceShortfalls"])
        )

    def test_five_clean_samples_accept_within_thresholds(self):
        report = MODULE.evaluate_reports([sample() for _ in range(5)], corpus())

        self.assertEqual("ACCEPTED", report["verdict"])
        self.assertAlmostEqual(1.2, report["suite"]["upstreamToLocalMedianRatio"])
        self.assertEqual("PASS", report["cases"][0]["status"])

    def test_repeated_material_regression_is_rejected(self):
        report = MODULE.evaluate_reports(
            [sample(upstream_nanos=150_000_000) for _ in range(5)], corpus()
        )

        self.assertEqual("REJECTED", report["verdict"])
        self.assertEqual("FAIL", report["cases"][0]["status"])
        self.assertTrue(report["performanceFailures"])

    def test_correctness_failure_rejects_even_before_minimum_samples(self):
        failing = sample()
        failing["failures"] = ["route differs"]

        report = MODULE.evaluate_reports([failing], corpus())

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(any("route differs" in value for value in report["failures"]))

    def test_mixed_revisions_are_rejected(self):
        other = sample()
        other["localRevision"] = "other"

        report = MODULE.evaluate_reports([sample(), other], corpus())

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(any("localRevision" in value for value in report["failures"]))

    def test_supplied_corpus_digest_must_match_report(self):
        report = MODULE.evaluate_reports(
            [sample()], corpus(), corpus_sha256="different"
        )

        self.assertEqual("REJECTED", report["verdict"])
        self.assertTrue(any("supplied corpus" in value for value in report["failures"]))

    def test_markdown_explains_verdict_and_exclusions(self):
        report = MODULE.evaluate_reports([sample()], corpus())

        value = MODULE.markdown(report)

        self.assertIn("`INSUFFICIENT_EVIDENCE`", value)
        self.assertIn("Excluded from core timing", value)
        self.assertIn("bank-aware workflow shapes differ", value)


if __name__ == "__main__":
    unittest.main()
