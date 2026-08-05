import importlib.util
import json
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "compare-shortest-path-planners.py"
SPEC = importlib.util.spec_from_file_location("planner_compare", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


def result(case, **overrides):
    value = {
        "id": case,
        "supported": True,
        "termination": "TARGET_REACHED",
        "reached": True,
        "endpoint": {"x": 2, "y": 2, "plane": 0},
        "pathCost": 2,
        "nodesChecked": 3,
        "transportsChecked": 0,
        "selectedTransports": [],
        "bankVisited": False,
        "elapsedNanos": 10,
    }
    value.update(overrides)
    return value


class PlannerComparisonTest(unittest.TestCase):
    def setUp(self):
        self.corpus = {
            "schemaVersion": 3,
            "cases": [
                {
                    "id": "route",
                    "category": "overland",
                    "target": {"x": 2, "y": 2, "plane": 0},
                    "policy": {"transportMode": "STATIC_COLLISION_ONLY"},
                    "expectedReached": True,
                }
            ]
        }

    def compare(self, local_case, upstream_case, require_all=False):
        return MODULE.compare_results(
            self.corpus,
            {"schemaVersion": 3, "revision": "local", "cases": [local_case]},
            {"schemaVersion": 3, "revision": "upstream", "cases": [upstream_case]},
            require_all,
        )

    def test_equivalent_reached_route_passes(self):
        report, exit_code = self.compare(result("route"), result("route"))

        self.assertEqual(0, exit_code)
        self.assertEqual("PASS", report["comparisons"][0]["status"])

    def test_reached_cost_difference_fails(self):
        report, exit_code = self.compare(
            result("route"), result("route", pathCost=3)
        )

        self.assertEqual(1, exit_code)
        self.assertIn("reached-path cost differs", report["failures"][0])

    def test_transport_use_in_static_policy_fails(self):
        report, exit_code = self.compare(
            result("route", transportsChecked=1), result("route")
        )

        self.assertEqual(1, exit_code)
        self.assertTrue(
            any("checked transports" in failure for failure in report["failures"])
        )

    def test_exact_selected_transport_difference_fails(self):
        self.corpus["cases"][0]["policy"]["transportMode"] = "EXPLICIT_CATALOG"
        self.corpus["cases"][0]["expectedTransportIds"] = ["fast"]
        fast = {
            "id": "fast",
            "from": {"x": 1, "y": 1, "plane": 0},
            "to": {"x": 2, "y": 2, "plane": 0},
            "type": "SPIRIT_TREE",
            "duration": 5,
        }
        wrong = dict(fast, id="slow")

        report, exit_code = self.compare(
            result("route", selectedTransports=[fast]),
            result("route", selectedTransports=[wrong]),
        )

        self.assertEqual(1, exit_code)
        self.assertTrue(
            any("selected transports" in failure for failure in report["failures"])
        )

    def test_expected_bank_visit_is_gated(self):
        self.corpus["cases"][0]["expectedBankVisited"] = True

        report, exit_code = self.compare(
            result("route", bankVisited=True), result("route")
        )

        self.assertEqual(1, exit_code)
        self.assertTrue(
            any("bankVisited" in failure for failure in report["failures"])
        )

    def test_documented_engine_specific_divergence_passes(self):
        self.corpus["cases"][0]["policy"]["transportMode"] = "EXPLICIT_CATALOG"
        self.corpus["cases"][0]["expectedParity"] = False
        self.corpus["cases"][0]["expectedDivergenceReason"] = "reviewed provider difference"
        self.corpus["cases"][0]["expectedLocalTransportIds"] = ["spell"]
        self.corpus["cases"][0]["expectedUpstreamTransportIds"] = []
        spell = {
            "id": "spell",
            "from": {"x": 1, "y": 1, "plane": 0},
            "to": {"x": 2, "y": 2, "plane": 0},
            "type": "TELEPORTATION_SPELL",
            "duration": 4,
        }

        report, exit_code = self.compare(
            result("route", pathCost=4, selectedTransports=[spell]),
            result("route"),
        )

        self.assertEqual(0, exit_code)
        self.assertEqual("EXPECTED_DIVERGENCE", report["comparisons"][0]["status"])

    def test_undocumented_expected_divergence_fails(self):
        self.corpus["cases"][0]["expectedParity"] = False

        report, exit_code = self.compare(
            result("route"), result("route", pathCost=3)
        )

        self.assertEqual(1, exit_code)
        self.assertTrue(
            any("no documented reason" in failure for failure in report["failures"])
        )

    def test_expected_divergence_must_remain_observable(self):
        self.corpus["cases"][0]["expectedParity"] = False
        self.corpus["cases"][0]["expectedDivergenceReason"] = "reviewed provider difference"

        report, exit_code = self.compare(result("route"), result("route"))

        self.assertEqual(1, exit_code)
        self.assertTrue(
            any("was not observed" in failure for failure in report["failures"])
        )

    def test_unsupported_capability_is_fail_closed_when_required(self):
        unsupported = result(
            "route",
            supported=False,
            unsupportedReason="no exact transport identity",
        )

        report, exit_code = self.compare(result("route"), unsupported, True)

        self.assertEqual(2, exit_code)
        self.assertEqual("UNSUPPORTED", report["comparisons"][0]["status"])

    def test_packaged_upstream_adapter_exact_match_passes(self):
        packaged = {"cases": [result("route")]}
        independent = {"cases": [result("route")]}

        failures, expected = MODULE.compare_upstream_adapters(
            self.corpus, packaged, independent
        )

        self.assertEqual([], failures)
        self.assertEqual([], expected)

    def test_packaged_upstream_adapter_semantic_mismatch_fails(self):
        packaged = {"cases": [result("route", pathCost=3)]}
        independent = {"cases": [result("route", pathCost=2)]}

        failures, expected = MODULE.compare_upstream_adapters(
            self.corpus, packaged, independent
        )

        self.assertEqual([], expected)
        self.assertEqual(1, len(failures))
        self.assertIn("packaged upstream pathCost=3", failures[0])

    def test_packaged_upstream_expected_input_policy_difference_is_explicit(self):
        self.corpus["cases"][0]["expectedParity"] = False
        self.corpus["cases"][0]["expectedDivergenceReason"] = (
            "reviewed executable-catalog policy difference"
        )
        packaged = {"cases": [result("route", pathCost=3)]}
        independent = {"cases": [result("route", pathCost=2)]}

        failures, expected = MODULE.compare_upstream_adapters(
            self.corpus, packaged, independent
        )

        self.assertEqual([], failures)
        self.assertEqual(
            ["route: reviewed executable-catalog policy difference"], expected
        )

    def test_checked_in_corpus_covers_static_and_exact_network_slices(self):
        corpus_path = SCRIPT.with_name("shortest-path-planner-corpus.json")
        corpus = json.loads(corpus_path.read_text(encoding="utf-8"))
        categories = {case["category"] for case in corpus["cases"]}

        self.assertTrue(
            {
                "overland",
                "underground",
                "surface-underground-surface",
                "unreachable",
                "wilderness",
                "network",
            }.issubset(
                categories
            )
        )
        network_cases = [case for case in corpus["cases"] if case["category"] == "network"]
        self.assertTrue(network_cases)
        self.assertTrue(all(case["expectedTransportIds"] for case in network_cases))
        bank_cases = [case for case in corpus["cases"] if case["category"] == "bank"]
        self.assertEqual(3, len(bank_cases))
        self.assertEqual(
            {"EXPLICIT_CATALOG", "BANK_AWARE_EXPLICIT_CATALOG"},
            {case["policy"]["transportMode"] for case in bank_cases},
        )
        bank_aware_cases = [
            case
            for case in bank_cases
            if case["policy"]["transportMode"] == "BANK_AWARE_EXPLICIT_CATALOG"
        ]
        self.assertTrue(
            any(case["start"] not in case["bankLocations"] for case in bank_aware_cases)
        )
        for case in corpus["cases"]:
            self.assertIn(
                case["policy"]["transportMode"],
                {
                    "STATIC_COLLISION_ONLY",
                    "EXPLICIT_CATALOG",
                    "BANK_AWARE_EXPLICIT_CATALOG",
                },
            )
            self.assertGreater(case["policy"]["cutoffMillis"], 0)
            self.assertEqual(0, case["policy"]["cutoffMillis"] % 600)
            transport_ids = {transport["id"] for transport in case.get("transports", [])}
            for field in (
                "expectedTransportIds",
                "expectedLocalTransportIds",
                "expectedUpstreamTransportIds",
            ):
                self.assertTrue(set(case.get(field, [])).issubset(transport_ids))
            if not case.get("expectedParity", True):
                self.assertTrue(case.get("expectedDivergenceReason"))
            if case["policy"]["transportMode"] == "BANK_AWARE_EXPLICIT_CATALOG":
                self.assertTrue(case.get("bankLocations"))
                self.assertTrue(case.get("expectedBankVisited"))


if __name__ == "__main__":
    unittest.main()
