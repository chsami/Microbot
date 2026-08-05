import importlib.util
import tempfile
import unittest
from pathlib import Path
from unittest import mock


SCRIPT = Path(__file__).resolve().parents[1] / "check-shortest-path-vendored-core.py"
SPEC = importlib.util.spec_from_file_location("vendored_core_check", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
SPEC.loader.exec_module(MODULE)


class VendoredCoreCheckTest(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory()
        base = Path(self.temporary.name)
        self.vendored = base / "vendored"
        self.upstream = base / "upstream"
        for root in (self.vendored, self.upstream):
            (root / "src/main/java/shortestpath").mkdir(parents=True)
        (self.vendored / "src/main/java/shortestpath/Exact.java").write_text(
            "class Exact {}\n", encoding="utf-8"
        )
        (self.upstream / "src/main/java/shortestpath/Exact.java").write_text(
            "class Exact {}\n", encoding="utf-8"
        )
        (self.vendored / "src/main/java/shortestpath/Patched.java").write_text(
            "class Patched { int adapter; }\n", encoding="utf-8"
        )
        (self.upstream / "src/main/java/shortestpath/Patched.java").write_text(
            "class Patched {}\n", encoding="utf-8"
        )
        (self.vendored / "src/main/java/shortestpath/Added.java").write_text(
            "class Added {}\n", encoding="utf-8"
        )
        for name in MODULE.METADATA_FILES:
            value = "revision\n" if name == "UPSTREAM_REVISION" else f"{name}\n"
            (self.vendored / name).write_text(value, encoding="utf-8")
        (self.upstream / "LICENSE").write_bytes((self.vendored / "LICENSE").read_bytes())
        current = MODULE.current_baseline_values(self.vendored)
        self.baseline = {
            "revision": "revision",
            **current,
            "patchedUpstreamFiles": ["shortestpath/Patched.java"],
            "adapterAddedFiles": ["shortestpath/Added.java"],
            "patchSurfacePolicy": {
                "maximumPatchedUpstreamFiles": 1,
                "maximumAdapterAddedFiles": 1,
                "growthRequiresAdrAmendment": True,
            },
        }

    def tearDown(self):
        self.temporary.cleanup()

    def test_offline_pin_and_declared_checkout_surface_pass(self):
        self.assertEqual([], MODULE.verify_offline(self.vendored, self.baseline))
        with mock.patch.object(MODULE, "git_head", return_value="revision"):
            self.assertEqual(
                [],
                MODULE.verify_against_checkout(
                    self.vendored, self.baseline, self.upstream
                ),
            )

    def test_offline_source_mutation_changes_tree_digest(self):
        (self.vendored / "src/main/java/shortestpath/Exact.java").write_text(
            "class Exact { int drift; }\n", encoding="utf-8"
        )

        failures = MODULE.verify_offline(self.vendored, self.baseline)

        self.assertTrue(any("source tree digest changed" in item for item in failures))

    def test_checkout_rejects_undeclared_patch(self):
        (self.vendored / "src/main/java/shortestpath/Exact.java").write_text(
            "class Exact { int drift; }\n", encoding="utf-8"
        )

        with mock.patch.object(MODULE, "git_head", return_value="revision"):
            failures = MODULE.verify_against_checkout(
                self.vendored, self.baseline, self.upstream
            )

        self.assertTrue(
            any("undeclared upstream source patch" in item for item in failures)
        )

    def test_offline_check_rejects_patch_surface_growth_beyond_budget(self):
        self.baseline["patchedUpstreamFiles"].append("shortestpath/Exact.java")

        failures = MODULE.verify_offline(self.vendored, self.baseline)

        self.assertTrue(any("exceeds reviewed budget" in item for item in failures))

    def test_offline_check_requires_adr_amendment_policy(self):
        self.baseline["patchSurfacePolicy"]["growthRequiresAdrAmendment"] = False

        failures = MODULE.verify_offline(self.vendored, self.baseline)

        self.assertTrue(any("growthRequiresAdrAmendment" in item for item in failures))


if __name__ == "__main__":
    unittest.main()
