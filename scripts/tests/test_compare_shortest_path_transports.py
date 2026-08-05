import importlib.util
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "compare-shortest-path-transports.py"
SPEC = importlib.util.spec_from_file_location("compare_shortest_path_transports", SCRIPT)
MODULE = importlib.util.module_from_spec(SPEC)
assert SPEC.loader is not None
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class CompareShortestPathTransportsTest(unittest.TestCase):
    def write_tsv(self, root: Path, name: str, contents: str) -> Path:
        path = root / name
        path.write_text(contents, encoding="utf-8")
        return path

    def test_parser_normalizes_headers_and_trailing_empty_columns(self):
        with tempfile.TemporaryDirectory() as temp_dir:
            path = self.write_tsv(
                Path(temp_dir),
                "boats.tsv",
                "# Origin\tDestination\tVarPlayers\tDisplay info\n"
                "1 2 0\t3 4 0\t892@30\tExample Boat\n",
            )
            row = MODULE.read_transport_tsv(path)[0]

        self.assertEqual(("1 2 0", "3 4 0", ""), row.route_key)
        self.assertEqual("example boat", row.display_info)
        self.assertEqual("892@30", row.fields["varplayers"])

    def test_course_object_catalog_comes_from_runelite_obstacles(self):
        course_ids = MODULE.load_agility_course_object_ids()

        self.assertIn(23134, course_ids)  # Gnome course obstacle net
        self.assertNotIn(3921, course_ids)  # Regicide forest tripwire
        self.assertNotIn(16518, course_ids)  # Lumbridge-farm world shortcut

    def test_agility_course_classification_does_not_hide_world_shortcuts(self):
        with tempfile.TemporaryDirectory() as upstream_dir, tempfile.TemporaryDirectory() as local_dir:
            header = "# Origin\tDestination\tmenuOption menuTarget objectID\tSkills\n"
            self.write_tsv(
                Path(upstream_dir),
                "agility_shortcuts.tsv",
                header
                + "1 2 0\t3 4 0\tClimb-over Obstacle net 23134\t1 Agility\n"
                + "5 6 0\t7 8 0\tJump-over Fence 16518\t13 Agility\n",
            )
            self.write_tsv(Path(local_dir), "agility_shortcuts.tsv", header)

            result = MODULE.compare_spec(
                Path(upstream_dir),
                Path(local_dir),
                MODULE.ComparisonSpec("agility_shortcuts.tsv", "agility_shortcuts.tsv"),
            )

        self.assertEqual(2, len(result["upstreamOnlyRoutes"]))
        self.assertEqual(
            ["1 2 0 -> 3 4 0"],
            result["upstreamOnlyClassifications"]["knownAgilityCourseTraversal"],
        )
        self.assertIn("5 6 0 -> 7 8 0", result["upstreamOnlyRoutes"])

    def test_agility_shortcut_represented_as_generic_transport_stays_visible(self):
        with tempfile.TemporaryDirectory() as upstream_dir, tempfile.TemporaryDirectory() as local_dir:
            agility_header = "# Origin\tDestination\tmenuOption menuTarget objectID\tSkills\n"
            transport_header = "# Origin\tDestination\tmenuOption menuTarget objectID\tSkills\n"
            self.write_tsv(
                Path(upstream_dir),
                "agility_shortcuts.tsv",
                agility_header
                + "5 6 0\t7 8 0\tJump-over Fence 16518\t13 Agility\n",
            )
            self.write_tsv(Path(local_dir), "agility_shortcuts.tsv", agility_header)
            self.write_tsv(
                Path(local_dir),
                "transports.tsv",
                transport_header
                + "5 6 0\t7 8 0\tJump-over;Fence;16518\t\n",
            )

            result = MODULE.compare_spec(
                Path(upstream_dir),
                Path(local_dir),
                MODULE.ComparisonSpec("agility_shortcuts.tsv", "agility_shortcuts.tsv"),
            )

        self.assertEqual(["5 6 0 -> 7 8 0"], result["upstreamOnlyRoutes"])
        self.assertEqual(
            ["5 6 0 -> 7 8 0"],
            result["upstreamOnlyClassifications"]["representedAsLocalGenericTransport"],
        )

    def test_route_identity_ignores_display_and_format_specific_interaction(self):
        upstream = MODULE.TransportRow(
            "boats.tsv",
            2,
            {"origin": "1 2 0", "destination": "3 4 0", "displayinfo": "Old name"},
        )
        local = MODULE.TransportRow(
            "boats.tsv",
            2,
            {"origin": " 1  2  0 ", "destination": "3 4 0", "displayinfo": "New name"},
        )

        self.assertEqual(upstream.route_key, local.route_key)

    def test_comparison_separates_missing_routes_from_requirement_drift(self):
        with tempfile.TemporaryDirectory() as upstream_dir, tempfile.TemporaryDirectory() as local_dir:
            header = "# Origin\tDestination\tmenuOption menuTarget objectID\tQuests\tDuration\tDisplay info\n"
            self.write_tsv(
                Path(upstream_dir),
                "boats.tsv",
                header
                + "1 2 0\t3 4 0\tTravel Boatman 10\tQuest A\t5\tShared\n"
                + "5 6 0\t7 8 0\tTravel Boatman 11\t\t5\tMissing\n",
            )
            self.write_tsv(
                Path(local_dir),
                "boats.tsv",
                header
                + "1 2 0\t3 4 0\tTravel;Boatman;10\tQuest B\t5\tShared\n",
            )

            result = MODULE.compare_spec(
                Path(upstream_dir),
                Path(local_dir),
                MODULE.ComparisonSpec("boats.tsv", "boats.tsv"),
            )

        self.assertEqual(["5 6 0 -> 7 8 0"], result["upstreamOnlyRoutes"])
        self.assertEqual(1, len(result["comparableFieldDrift"]))

    def test_ordinary_transport_exclusions_are_exact_and_leave_unknown_routes_unclassified(self):
        with tempfile.TemporaryDirectory() as upstream_dir, tempfile.TemporaryDirectory() as local_dir:
            header = "# Origin\tDestination\tmenuOption menuTarget objectID\tItems\n"
            self.write_tsv(
                Path(upstream_dir),
                "transports.tsv",
                header
                + "2343 3662 0\t2343 3663 0\tOpen Colony gate\t\n"
                + "2724 2747 0\t2770 2793 0\t\t\n"
                + "2795 2793 0\t2795 2797 1\tClimb-up Staircase\t\n"
                + "3228 3470 0\t3228 3472 0\tClimb Trellis 2149\t\n"
                + "1 2 0\t3 4 0\tOpen Mystery 999\t\n",
            )
            self.write_tsv(Path(local_dir), "transports.tsv", header)

            result = MODULE.compare_spec(
                Path(upstream_dir),
                Path(local_dir),
                MODULE.ComparisonSpec("transports.tsv", "transports.tsv"),
            )

        classifications = result["upstreamOnlyClassifications"]
        self.assertEqual(
            ["2343 3662 0 -> 2343 3663 0"],
            classifications["supersededPiscatorisGateAnchors"],
        )
        self.assertEqual(
            ["2724 2747 0 -> 2770 2793 0"],
            classifications["unsupportedInteractionlessDaeroTransition"],
        )
        self.assertEqual(
            ["2795 2793 0 -> 2795 2797 1"],
            classifications["unsupportedIdlessMarimStaircases"],
        )
        self.assertEqual(
            ["3228 3470 0 -> 3228 3472 0"],
            classifications["intentionalDisabledVarrockPalaceTrellis"],
        )
        classified = {route for routes in classifications.values() for route in routes}
        self.assertNotIn("1 2 0 -> 3 4 0", classified)

    def test_elemental_wall_conservative_requirement_drift_is_classified(self):
        with tempfile.TemporaryDirectory() as upstream_dir, tempfile.TemporaryDirectory() as local_dir:
            self.write_tsv(
                Path(upstream_dir),
                "transports.tsv",
                "# Origin\tDestination\tmenuOption menuTarget objectID\tItems\n"
                "2709 3495 0\t2709 3496 0\tOpen Odd-looking wall\t2887=1|4446=1\n",
            )
            self.write_tsv(
                Path(local_dir),
                "transports.tsv",
                "# Origin\tDestination\tmenuOption menuTarget objectID\tItem IDs\n"
                "2709 3495 0\t2709 3496 0\tOpen;Odd-looking wall;26115\t2887=1\n",
            )

            result = MODULE.compare_spec(
                Path(upstream_dir),
                Path(local_dir),
                MODULE.ComparisonSpec("transports.tsv", "transports.tsv"),
            )

        self.assertEqual(1, len(result["comparableFieldDrift"]))
        self.assertEqual(
            ["2709 3495 0 -> 2709 3496 0"],
            result["comparableFieldDriftClassifications"][
                "concreteElementalWorkshopWallWithConservativeKeyRequirement"
            ],
        )

    def test_ship_landing_representations_are_exact_and_leave_unknown_routes_unclassified(self):
        with tempfile.TemporaryDirectory() as upstream_dir, tempfile.TemporaryDirectory() as local_dir:
            header = "# Origin\tDestination\tmenuOption menuTarget objectID\tDisplay info\n"
            self.write_tsv(
                Path(upstream_dir),
                "ships.tsv",
                header
                + "2578 2840 0\t2956 3146 0\tTravel Captain 1\tRimmington\n"
                + "1 2 0\t3 4 0\tTravel Captain 2\tMystery Port\n",
            )
            self.write_tsv(Path(local_dir), "ships.tsv", header)

            result = MODULE.compare_spec(
                Path(upstream_dir),
                Path(local_dir),
                MODULE.ComparisonSpec("ships.tsv", "ships.tsv", network_identity=True),
            )

        classifications = result["upstreamOnlyClassifications"]
        self.assertEqual(
            ["2578 2840 0 -> rimmington"],
            classifications["representedByCurrentCorsairCoveLandings"],
        )
        classified = {route for routes in classifications.values() for route in routes}
        self.assertNotIn("1 2 0 -> mystery port", classified)

    def test_home_teleport_mapping_filters_regular_spells(self):
        home = MODULE.TransportRow(
            "teleportation_spells.tsv",
            2,
            {"destination": "1 2 0", "displayinfo": "Lumbridge Home Teleport"},
        )
        regular = MODULE.TransportRow(
            "teleportation_spells.tsv",
            3,
            {"destination": "1 2 0", "displayinfo": "Lumbridge Teleport"},
        )

        self.assertTrue(MODULE.is_home_teleport(home))
        self.assertFalse(MODULE.is_home_teleport(regular))

    def test_inline_quetzal_whistles_are_compared_with_the_upstream_family(self):
        with tempfile.TemporaryDirectory() as upstream_dir, tempfile.TemporaryDirectory() as local_dir:
            upstream_root = Path(upstream_dir)
            local_root = Path(local_dir)
            self.write_tsv(
                upstream_root,
                "teleportation_items.tsv",
                "# Destination\tItems\tDuration\tDisplay info\n"
                "3 4 0\t200=1\t4\tRegular teleport\n",
            )
            self.write_tsv(
                upstream_root,
                "quetzal_whistle.tsv",
                "# Destination\tItems\tDuration\tDisplay info\n"
                "1 2 0\t100=1\t4\tQuetzal whistle: Aldarin\n",
            )
            self.write_tsv(
                local_root,
                "teleportation_items.tsv",
                "# Destination\tItem IDs\tDuration\tDisplay info\n"
                "3 4 0\t200=1\t4\tRegular teleport\n"
                "1 2 0\t100=1\t4\tQuetzal whistle: Aldarin\n",
            )

            specs, upstream_only, local_only = MODULE.comparison_specs(upstream_root, local_root)
            report = MODULE.build_report(upstream_root, local_root)

        self.assertEqual([], upstream_only)
        self.assertEqual([], local_only)
        self.assertEqual(2, len(specs))
        comparisons = {
            MODULE.comparison_id(comparison): comparison
            for comparison in report["comparisons"]
        }
        self.assertEqual(
            1,
            comparisons["teleportation_items.tsv -> teleportation_items.tsv"]["sharedRoutes"],
        )
        self.assertEqual(
            1,
            comparisons["quetzal_whistle.tsv -> teleportation_items.tsv"]["sharedRoutes"],
        )

    def test_split_infinite_whistle_variant_is_visible_as_reviewable_drift(self):
        with tempfile.TemporaryDirectory() as upstream_dir, tempfile.TemporaryDirectory() as local_dir:
            upstream_root = Path(upstream_dir)
            local_root = Path(local_dir)
            self.write_tsv(
                upstream_root,
                "quetzal_whistle.tsv",
                "# Destination\tItems\tDisplay info\tConsumable\n"
                "1 2 0\t10=1||11=1\tQuetzal whistle: Aldarin\tT\n",
            )
            self.write_tsv(
                local_root,
                "teleportation_items.tsv",
                "# Destination\tItem IDs\tDisplay info\tConsumable\n"
                "1 2 0\t10=1\tQuetzal whistle: Aldarin\tT\n"
                "1 2 0\t11=1\tQuetzal whistle: Aldarin\tF\n",
            )

            result = MODULE.compare_spec(
                upstream_root,
                local_root,
                MODULE.ComparisonSpec(
                    "quetzal_whistle.tsv",
                    "teleportation_items.tsv",
                    MODULE.is_quetzal_whistle,
                ),
            )

        self.assertEqual(1, result["sharedRoutes"])
        self.assertEqual([], result["upstreamOnlyRoutes"])
        self.assertEqual([], result["localOnlyRoutes"])
        self.assertEqual(1, len(result["comparableFieldDrift"]))
        self.assertEqual(
            ["items", "consumable"],
            result["comparableFieldDrift"][0]["differingFields"],
        )
        self.assertEqual(1, result["comparableFieldDrift"][0]["upstreamVariants"])
        self.assertEqual(2, result["comparableFieldDrift"][0]["localVariants"])

    def test_originless_routes_include_normalized_display_identity(self):
        ge_long = MODULE.TransportRow(
            "teleportation_spells.tsv",
            2,
            {"destination": "1 2 0", "displayinfo": "Varrock Teleport: Grand Exchange"},
        )
        ge_short = MODULE.TransportRow(
            "teleportation_spells.tsv",
            3,
            {"destination": "1 2 0", "displayinfo": "Varrock Teleport: GE"},
        )
        other = MODULE.TransportRow(
            "teleportation_spells.tsv",
            4,
            {"destination": "1 2 0", "displayinfo": "Different spell"},
        )

        self.assertEqual(ge_long.route_key, ge_short.route_key)
        self.assertNotEqual(ge_long.route_key, other.route_key)

    def test_origin_only_routes_do_not_require_a_display_label(self):
        upstream = MODULE.TransportRow(
            "wilderness_obelisks.tsv",
            2,
            {"origin": "1 2 0", "destination": "", "displayinfo": ""},
        )
        local = MODULE.TransportRow(
            "wilderness_obelisks.tsv",
            2,
            {"origin": "1 2 0", "destination": "", "displayinfo": "Level 13 Wilderness"},
        )

        self.assertEqual(upstream.route_key, local.route_key)

    def test_display_normalization_removes_selector_and_minigame_suffix(self):
        self.assertEqual(
            "barbarian assault",
            MODULE.normalize_display_info("1: Barbarian Assault Minigame Teleport"),
        )
        self.assertEqual(
            "rat pits: ardougne",
            MODULE.normalize_display_info("Rat Pits Minigame Teleport: 1. Ardougne"),
        )

    def test_coin_costs_compare_across_upstream_and_local_formats(self):
        upstream = MODULE.TransportRow(
            "minecarts.tsv",
            2,
            {"items": "COINS=20", "quests": "Quest A"},
        )
        local = MODULE.TransportRow(
            "minecarts.tsv",
            2,
            {"currency": "20 Coins", "quests": "Quest A"},
        )

        self.assertEqual(upstream.comparable_fingerprint, local.comparable_fingerprint)

    def test_network_identity_matches_different_execution_tiles_by_location(self):
        upstream = [
            MODULE.TransportRow(
                "ships.tsv",
                2,
                {"origin": "10 10 0", "destination": "20 20 0", "displayinfo": "Musa Point"},
            ),
            MODULE.TransportRow(
                "ships.tsv",
                3,
                {"origin": "20 20 0", "destination": "10 10 0", "displayinfo": "Port Sarim"},
            ),
        ]
        local = [
            MODULE.TransportRow(
                "ships.tsv",
                2,
                {"origin": "12 11 0", "destination": "22 21 1", "displayinfo": "Musa Point"},
            ),
            MODULE.TransportRow(
                "ships.tsv",
                3,
                {"origin": "22 21 0", "destination": "12 11 1", "displayinfo": "Port Sarim"},
            ),
        ]

        self.assertEqual(
            set(MODULE.index_rows(upstream, network_identity=True)),
            set(MODULE.index_rows(local, network_identity=True)),
        )

    def test_requirement_drift_identifies_the_changed_dimensions(self):
        with tempfile.TemporaryDirectory() as upstream_dir, tempfile.TemporaryDirectory() as local_dir:
            header = "# Origin\tDestination\tItems\tQuests\tDuration\tDisplay info\n"
            self.write_tsv(
                Path(upstream_dir),
                "boats.tsv",
                header + "1 2 0\t3 4 0\tCOINS=20\tQuest A\t5\tShared\n",
            )
            self.write_tsv(
                Path(local_dir),
                "boats.tsv",
                "# Origin\tDestination\tCurrency\tQuests\tDuration\tDisplay info\n"
                "1 2 0\t3 4 0\t20 Coins\tQuest B\t6\tShared\n",
            )

            result = MODULE.compare_spec(
                Path(upstream_dir),
                Path(local_dir),
                MODULE.ComparisonSpec("boats.tsv", "boats.tsv"),
            )

        drift = result["comparableFieldDrift"][0]
        self.assertEqual(["quests", "duration"], drift["differingFields"])

    def test_absent_schema_field_is_unknown_instead_of_empty(self):
        with tempfile.TemporaryDirectory() as upstream_dir, tempfile.TemporaryDirectory() as local_dir:
            self.write_tsv(
                Path(upstream_dir),
                "teleportation_items.tsv",
                "# Destination\tItems\tDuration\tDisplay info\n"
                "1 2 0\t100=1\t4\tExample teleport\n",
            )
            self.write_tsv(
                Path(local_dir),
                "teleportation_items.tsv",
                "# Destination\tItem IDs\tisMembers\tDuration\tDisplay info\n"
                "1 2 0\t100=1\tY\t4\tExample teleport\n",
            )

            result = MODULE.compare_spec(
                Path(upstream_dir),
                Path(local_dir),
                MODULE.ComparisonSpec("teleportation_items.tsv", "teleportation_items.tsv"),
            )

        self.assertEqual([], result["comparableFieldDrift"])

    def test_transport_baseline_detects_identity_changes_not_just_counts(self):
        report = {
            "upstreamOnlyFiles": [],
            "localOnlyFiles": ["restrictions.tsv"],
            "comparisons": [
                {
                    "upstreamFile": "boats.tsv",
                    "localFile": "boats.tsv",
                    "sharedRoutes": 1,
                    "upstreamOnlyRoutes": ["1 2 0 -> 3 4 0"],
                    "localOnlyRoutes": [],
                    "comparableFieldDrift": [],
                }
            ],
        }
        expected = MODULE.build_transport_baseline(report, "abc123")
        changed_report = {
            **report,
            "comparisons": [
                {
                    **report["comparisons"][0],
                    "upstreamOnlyRoutes": ["5 6 0 -> 7 8 0"],
                }
            ],
        }
        actual = MODULE.build_transport_baseline(changed_report, "abc123")

        differences = MODULE.baseline_differences(expected, actual)

        self.assertTrue(any("upstreamOnlyDigest" in difference for difference in differences))
        self.assertFalse(any("upstreamOnlyRoutes:" in difference for difference in differences))

    def test_reviewed_difference_rationale_is_bound_to_exact_drift_digest(self):
        report = {
            "upstreamOnlyFiles": [],
            "localOnlyFiles": [],
            "comparisons": [
                {
                    "upstreamFile": "boats.tsv",
                    "localFile": "boats.tsv",
                    "sharedRoutes": 1,
                    "upstreamOnlyRoutes": [],
                    "localOnlyRoutes": [],
                    "comparableFieldDrift": [
                        {
                            "route": "1 2 0 -> 3 4 0",
                            "upstreamVariants": 1,
                            "localVariants": 1,
                            "differingFields": ["duration"],
                        }
                    ],
                }
            ],
        }
        actual = MODULE.build_transport_baseline(report, "abc123")
        comparison_id = "boats.tsv -> boats.tsv"
        expected = {
            **actual,
            "reviewedDifferences": {
                comparison_id: {
                    "comparableFieldDriftDigest": actual["comparisons"][comparison_id][
                        "comparableFieldDriftDigest"
                    ],
                    "rationale": "Local duration reflects observed execution time.",
                }
            },
        }

        self.assertEqual([], MODULE.baseline_differences(expected, actual))

        expected["reviewedDifferences"][comparison_id][
            "comparableFieldDriftDigest"
        ] = "stale"
        differences = MODULE.baseline_differences(expected, actual)
        self.assertTrue(any("reviewed difference digest" in item for item in differences))

        expected["reviewedDifferences"][comparison_id][
            "comparableFieldDriftDigest"
        ] = actual["comparisons"][comparison_id]["comparableFieldDriftDigest"]
        expected["reviewedDifferences"][comparison_id]["rationale"] = ""
        differences = MODULE.baseline_differences(expected, actual)
        self.assertTrue(any("missing rationale" in item for item in differences))

    def test_transport_baseline_is_stable_for_identical_reports(self):
        report = {
            "upstreamOnlyFiles": [],
            "localOnlyFiles": [],
            "comparisons": [],
        }
        baseline = MODULE.build_transport_baseline(report, "abc123")

        self.assertEqual(2, baseline["schemaVersion"])
        self.assertEqual([], MODULE.baseline_differences(baseline, baseline))


if __name__ == "__main__":
    unittest.main()
