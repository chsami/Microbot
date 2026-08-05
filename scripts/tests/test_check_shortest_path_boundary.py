import subprocess
import sys
import tempfile
import unittest
from pathlib import Path


SCRIPT = Path(__file__).resolve().parents[1] / "check-shortest-path-boundary.py"


class ShortestPathBoundaryTest(unittest.TestCase):
    def run_check(self, files: dict[str, str]) -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as temporary:
            source_root = Path(temporary)
            for relative, contents in files.items():
                target = source_root / relative
                target.parent.mkdir(parents=True, exist_ok=True)
                target.write_text(contents, encoding="utf-8")
            return subprocess.run(
                [sys.executable, str(SCRIPT), "--source-root", str(source_root)],
                check=False,
                capture_output=True,
                text=True,
            )

    def test_allows_implementation_facade_and_plugin_identity(self):
        result = self.run_check(
            {
                "shortestpath/ShortestPathPlugin.java":
                    "class ShortestPathPlugin { void x() { ShortestPathPlugin.exit(); } }\n",
                "util/walker/Rs2PathApi.java":
                    "class Rs2PathApi { void x() { ShortestPathPlugin.exit(); } }\n",
                "breakhandler/breakhandlerv2/MicrobotPluginChoice.java": (
                    "import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;\n"
                    "class MicrobotPluginChoice { Class<?> type = ShortestPathPlugin.class; }\n"
                ),
            }
        )

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_rejects_direct_plugin_state_access(self):
        result = self.run_check(
            {
                "util/bank/Rs2Bank.java":
                    "class Rs2Bank { void x() { ShortestPathPlugin.getPathfinderConfig(); } }\n"
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("util/bank/Rs2Bank.java:1", result.stdout)
        self.assertIn("use util/walker/Rs2PathApi", result.stdout)

    def test_rejects_plugin_import_outside_allowed_seam(self):
        result = self.run_check(
            {
                "questhelper/QuestScript.java":
                    "import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;\n"
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("questhelper/QuestScript.java:1", result.stdout)

    def test_plugin_identity_exception_does_not_hide_state_access(self):
        result = self.run_check(
            {
                "breakhandler/breakhandlerv2/MicrobotPluginChoice.java": (
                    "class MicrobotPluginChoice { Object[] x = { ShortestPathPlugin.class, "
                    "ShortestPathPlugin.getPathfinder() }; }\n"
                )
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("MicrobotPluginChoice.java:1", result.stdout)

    def test_rejects_direct_pathfinder_in_migrated_synchronous_scope(self):
        result = self.run_check(
            {
                "util/bank/Rs2Bank.java": (
                    "import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;\n"
                    "class Rs2Bank { Object x(Object c, Object a, Object b) { "
                    "return new Pathfinder(c, a, b); } }\n"
                )
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn("direct-pathfinder util/bank/Rs2Bank.java:1", result.stdout)
        self.assertIn("direct-pathfinder util/bank/Rs2Bank.java:2", result.stdout)

    def test_rejects_new_pathfinder_construction_in_migrated_walker_utilities(self):
        result = self.run_check(
            {
                "util/walker/Rs2Walker.java":
                    "class Rs2Walker { Object x(Object c, Object a, Object b) { "
                    "return new Pathfinder(c, a, b); } }\n"
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn(
            "direct-pathfinder-construction util/walker/Rs2Walker.java:1",
            result.stdout,
        )

    def test_rejects_mutable_planner_config_in_walker(self):
        result = self.run_check(
            {
                "util/walker/Rs2Walker.java": (
                    "class Rs2Walker { void x() { "
                    "Rs2PathApi.getPathfinderConfig().refresh(); } }\n"
                )
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn(
            "direct-pathfinder-config util/walker/Rs2Walker.java:1",
            result.stdout,
        )

    def test_rejects_active_pathfinder_consumption_outside_facade(self):
        result = self.run_check(
            {
                "util/walker/Rs2Walker.java": (
                    "import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;\n"
                    "class Rs2Walker { Pathfinder x() { return Rs2PathApi.getPathfinder(); } }\n"
                )
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn(
            "direct-active-pathfinder util/walker/Rs2Walker.java:2",
            result.stdout,
        )

    def test_allows_owned_active_route_status_consumption(self):
        result = self.run_check(
            {
                "questhelper/QuestScript.java": (
                    "class QuestScript { boolean x() { "
                    "return Rs2PathApi.getActiveRouteStatus().isReady(); } }\n"
                )
            }
        )

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_rejects_concrete_planner_lifecycle_outside_facade(self):
        result = self.run_check(
            {
                "util/walker/lifecycle/Rs2WalkerLifecycleRuntime.java": (
                    "import net.runelite.client.plugins.microbot.shortestpath.pathfinder.Pathfinder;\n"
                    "class Rs2WalkerLifecycleRuntime { Object x(Object c, Object a, Object b) { "
                    "return new Pathfinder(c, a, b); } }\n"
                )
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn(
            "direct-pathfinder util/walker/lifecycle/Rs2WalkerLifecycleRuntime.java:1",
            result.stdout,
        )
        self.assertIn(
            "direct-pathfinder util/walker/lifecycle/Rs2WalkerLifecycleRuntime.java:2",
            result.stdout,
        )

    def test_rejects_mutable_planner_config_in_migrated_scope(self):
        result = self.run_check(
            {
                "util/npc/Rs2NpcManager.java": (
                    "import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;\n"
                    "class Rs2NpcManager { Object x() { "
                    "return Rs2PathApi.getPathfinderConfig(); } }\n"
                )
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn(
            "direct-pathfinder-config util/npc/Rs2NpcManager.java:1",
            result.stdout,
        )
        self.assertIn(
            "direct-pathfinder-config util/npc/Rs2NpcManager.java:2",
            result.stdout,
        )

    def test_rejects_mutable_planner_config_in_slayer_scope(self):
        result = self.run_check(
            {
                "util/skills/slayer/Rs2Slayer.java": (
                    "class Rs2Slayer { void x() { "
                    "Rs2PathApi.getPathfinderConfig().setUseBankItems(true); } }\n"
                )
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn(
            "direct-pathfinder-config util/skills/slayer/Rs2Slayer.java:1",
            result.stdout,
        )

    def test_rejects_mutable_planner_config_in_leagues_scope(self):
        result = self.run_check(
            {
                "util/leaguetransport/LeaguesTransportInjection.java": (
                    "import net.runelite.client.plugins.microbot.shortestpath.pathfinder.PathfinderConfig;\n"
                    "class LeaguesTransportInjection { PathfinderConfig config; }\n"
                )
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn(
            "direct-pathfinder-config util/leaguetransport/LeaguesTransportInjection.java:1",
            result.stdout,
        )

    def test_rejects_shortest_path_types_in_owned_route_values(self):
        result = self.run_check(
            {
                "util/walker/Rs2RouteStep.java": (
                    "import net.runelite.client.plugins.microbot.shortestpath.Transport;\n"
                    "class Rs2RouteStep { Transport selected; }\n"
                )
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn(
            "owned-value-dependency util/walker/Rs2RouteStep.java:1",
            result.stdout,
        )

    def test_rejects_microbot_executor_registry_inside_planner_core(self):
        result = self.run_check(
            {
                "shortestpath/pathfinder/PathfinderConfig.java": (
                    "class PathfinderConfig { boolean x(Transport t) { "
                    "return TransportExecutionRegistry.canExecute(t); } }\n"
                )
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn(
            "planner-executor-coupling shortestpath/pathfinder/PathfinderConfig.java:1",
            result.stdout,
        )

    def test_rejects_concrete_transport_in_migrated_recovery_scope(self):
        result = self.run_check(
            {
                "util/walker/recovery/RouteRecovery.java": (
                    "import net.runelite.client.plugins.microbot.shortestpath.Transport;\n"
                    "class RouteRecovery { Transport selected; }\n"
                )
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn(
            "concrete-transport util/walker/recovery/RouteRecovery.java:1",
            result.stdout,
        )

    def test_rejects_concrete_transport_in_migrated_execution_handler(self):
        result = self.run_check(
            {
                "util/walker/Rs2HotAirBalloon.java": (
                    "import net.runelite.client.plugins.microbot.shortestpath.Transport;\n"
                    "class Rs2HotAirBalloon { Transport selected; }\n"
                )
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn(
            "concrete-transport util/walker/Rs2HotAirBalloon.java:1",
            result.stdout,
        )

    def test_allows_java_and_runelite_api_types_in_owned_route_values(self):
        result = self.run_check(
            {
                "util/walker/Rs2RouteResult.java": (
                    "import java.util.List;\n"
                    "import net.runelite.api.coords.WorldPoint;\n"
                    "class Rs2RouteResult { List<WorldPoint> path; }\n"
                )
            }
        )

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_rejects_removed_legacy_route_handoff_even_in_facade(self):
        result = self.run_check(
            {
                "util/walker/Rs2PathApi.java": (
                    "class Rs2PathApi { LegacyRoutePlan planLegacy(Object request) { "
                    "return null; } }\n"
                )
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn(
            "legacy-route-handoff util/walker/Rs2PathApi.java:1",
            result.stdout,
        )

    def test_rejects_replanning_transport_requirements_after_bank_comparison(self):
        result = self.run_check(
            {
                "util/walker/Rs2Walker.java": (
                    "class Rs2Walker { void x(Object target) {\n"
                    "  getMissingTransportEdges(\n"
                    "    getTransportEdgesForDestination(target, true));\n"
                    "} }\n"
                )
            }
        )

        self.assertEqual(1, result.returncode)
        self.assertIn(
            "bank-route-replan util/walker/Rs2Walker.java:2",
            result.stdout,
        )


if __name__ == "__main__":
    unittest.main()
