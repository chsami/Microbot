package net.runelite.client.plugins.microbot.example;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.api.npc.Rs2NpcCache;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@Slf4j
public class ExampleScript extends Script {

    private final List<CheckResult> results = new ArrayList<>();

    public boolean run() {
        results.clear();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                log.info("========================================");
                log.info("  MICROBOT POST-MERGE SMOKE TEST");
                log.info("========================================");

                checkClientState();
                checkPlayerState();
                checkWorldViewAndThreading();
                checkNpcCache();
                checkTileObjectCache();
                checkGroundItemCache();
                checkPlayerCache();
                checkInventory();
                checkEquipment();
                checkWidgets();
                checkWalker();
                checkDialogue();
                checkLooting();
                checkHomeTeleport();

                printSummary();
                shutdown();

            } catch (Exception ex) {
                log.error("[SmokeTest] Unexpected top-level error: ", ex);
                shutdown();
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private void check(String name, Runnable test) {
        try {
            test.run();
            results.add(new CheckResult(name, true, null));
            log.info("[PASS] {}", name);
        } catch (Exception e) {
            results.add(new CheckResult(name, false, e.getMessage()));
            log.error("[FAIL] {} - {}", name, e.getMessage());
        }
    }

    private void checkClientState() {
        log.info("--- Client & Login State ---");

        check("Microbot.isLoggedIn()", () -> {
            assertTrue(Microbot.isLoggedIn(), "expected logged in");
        });

        check("Client.getLocalPlayer() not null", () -> {
            assertNotNull(Microbot.getClient().getLocalPlayer(), "local player is null");
        });

        check("GameState is LOGGED_IN", () -> {
            GameState state = Microbot.getClient().getGameState();
            assertTrue(state == GameState.LOGGED_IN || state == GameState.LOADING,
                    "expected LOGGED_IN or LOADING, got " + state);
        });
    }

    private void checkPlayerState() {
        log.info("--- Player State ---");

        check("Rs2Player.getWorldLocation() valid", () -> {
            WorldPoint wp = Rs2Player.getWorldLocation();
            assertNotNull(wp, "world location is null");
            assertTrue(wp.getX() > 0 && wp.getY() > 0, "world location coords are zero: " + wp);
        });

        check("Rs2Player.getLocalLocation() not null", () -> {
            LocalPoint lp = Rs2Player.getLocalLocation();
            assertNotNull(lp, "local location is null");
        });

        check("Rs2Player.getHealthPercentage() in range", () -> {
            double hp = Rs2Player.getHealthPercentage();
            assertTrue(hp >= 0 && hp <= 100, "health percentage out of range: " + hp);
        });

        check("Rs2Player.getRealSkillLevel(HITPOINTS) > 0", () -> {
            int level = Rs2Player.getRealSkillLevel(Skill.HITPOINTS);
            assertTrue(level > 0, "hitpoints level is " + level);
        });

        check("Rs2Player.isAnimating() callable", () -> {
            Rs2Player.isAnimating();
        });

        check("Rs2Player.isMoving() callable", () -> {
            Rs2Player.isMoving();
        });

        check("Rs2Player.isInCombat() callable", () -> {
            Rs2Player.isInCombat();
        });
    }

    private void checkWorldViewAndThreading() {
        log.info("--- WorldView & Threading ---");

        check("TopLevelWorldView not null", () -> {
            WorldView wv = Microbot.getClient().getTopLevelWorldView();
            assertNotNull(wv, "top level world view is null");
        });

        check("Player WorldView is top level", () -> {
            Player player = Microbot.getClient().getLocalPlayer();
            assertNotNull(player, "local player is null");
            WorldView wv = Microbot.getClientThread().invoke(() -> player.getWorldView());
            assertNotNull(wv, "player world view is null");
            assertTrue(wv.isTopLevel(), "player world view is not top level");
        });

        check("ClientThread.invoke() works", () -> {
            String result = Microbot.getClientThread().invoke(() -> "ok");
            assertTrue("ok".equals(result), "invoke returned unexpected value: " + result);
        });
    }

    private void checkNpcCache() {
        log.info("--- NPC Cache ---");

        check("Rs2NpcCache.getStream() not null", () -> {
            Stream<Rs2NpcModel> stream = Microbot.getRs2NpcCache().getStream();
            assertNotNull(stream, "npc stream is null");
        });

        check("Rs2NpcCache stream has entries", () -> {
            long count = Microbot.getRs2NpcCache().getStream().count();
            assertTrue(count > 0, "no NPCs in cache (are there NPCs nearby?)");
        });

        check("First NPC has valid world location", () -> {
            Rs2NpcModel npc = Microbot.getRs2NpcCache().getStream().findFirst().orElse(null);
            assertNotNull(npc, "no NPC found");
            WorldPoint wp = npc.getWorldLocation();
            assertNotNull(wp, "NPC world location is null");
        });

        check("Rs2NpcCache.query().nearest() works", () -> {
            Microbot.getRs2NpcCache().query().nearest();
        });
    }

    private void checkTileObjectCache() {
        log.info("--- Tile Object Cache ---");

        check("Rs2TileObjectCache.getStream() not null", () -> {
            Stream<Rs2TileObjectModel> stream = Microbot.getRs2TileObjectCache().getStream();
            assertNotNull(stream, "tile object stream is null");
        });

        check("Rs2TileObjectCache stream has entries", () -> {
            long count = Microbot.getRs2TileObjectCache().getStream().count();
            assertTrue(count > 0, "no tile objects in cache");
        });

        check("First TileObject has valid ID", () -> {
            Rs2TileObjectModel obj = Microbot.getRs2TileObjectCache().getStream().findFirst().orElse(null);
            assertNotNull(obj, "no tile object found");
            assertTrue(obj.getId() > 0, "tile object ID is " + obj.getId());
        });

        check("Rs2TileObjectCache.query().nearest() works", () -> {
            Microbot.getRs2TileObjectCache().query().nearest();
        });
    }

    private void checkGroundItemCache() {
        log.info("--- Ground Item Cache ---");

        check("Rs2TileItemCache.getStream() callable", () -> {
            Microbot.getRs2TileItemCache().getStream();
        });
    }

    private void checkPlayerCache() {
        log.info("--- Player Cache ---");

        check("Rs2PlayerCache.getStream() callable", () -> {
            Microbot.getRs2PlayerCache().getStream();
        });
    }

    private void checkInventory() {
        log.info("--- Inventory ---");

        check("Rs2Inventory.all() returns list", () -> {
            List<?> items = Rs2Inventory.all();
            assertNotNull(items, "inventory all() returned null");
        });

        check("Rs2Inventory.count() >= 0", () -> {
            int count = Rs2Inventory.count();
            assertTrue(count >= 0, "inventory count is negative: " + count);
        });

        check("Rs2Inventory.isFull() callable", () -> {
            Rs2Inventory.isFull();
        });

        check("Rs2Inventory.isEmpty() callable", () -> {
            Rs2Inventory.isEmpty();
        });
    }

    private void checkEquipment() {
        log.info("--- Equipment ---");

        check("Rs2Equipment.equipment() callable", () -> {
            Microbot.getClientThread().invoke(() -> Rs2Equipment.equipment());
        });

        check("Rs2Equipment.isWearing() callable", () -> {
            Rs2Equipment.isWearing();
        });
    }

    private void checkWidgets() {
        log.info("--- Widgets ---");

        check("Rs2Widget.getWidget(149, 0) callable", () -> {
            Rs2Widget.getWidget(149, 0);
        });
    }

    private void checkWalker() {
        log.info("--- Walker ---");

        check("Rs2Walker.canReach(own location) returns true", () -> {
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            assertNotNull(playerLoc, "player location is null");
            boolean reachable = Rs2Walker.canReach(playerLoc);
            assertTrue(reachable, "player cannot reach own tile");
        });
    }

    private void checkDialogue() {
        log.info("--- Dialogue ---");

        check("Rs2Dialogue.isInDialogue() callable", () -> {
            Rs2Dialogue.isInDialogue();
        });
    }

    private void checkHomeTeleport() {
        log.info("--- Home Teleport ---");

        check("Home teleport starts animation", () -> {
            boolean cast = Rs2Magic.cast(MagicAction.LUMBRIDGE_HOME_TELEPORT);
            assertTrue(cast, "Rs2Magic.cast(LUMBRIDGE_HOME_TELEPORT) returned false");
            boolean animating = sleepUntil(Rs2Player::isAnimating, 3000);
            assertTrue(animating, "player did not start home teleport animation");
        });
    }

    private void checkLooting() {
        log.info("--- Looting ---");

        Rs2ItemModel itemToDrop = Rs2Inventory.all().stream().findFirst().orElse(null);
        if (itemToDrop == null) {
            log.warn("[SKIP] Looting - inventory is empty, nothing to drop and loot");
            results.add(new CheckResult("Drop and loot item", false, "inventory is empty, cannot test looting"));
            return;
        }

        String itemName = itemToDrop.getName();
        int itemId = itemToDrop.getId();
        int countBefore = Rs2Inventory.count(itemId);
        log.info("[Loot] Testing with item: {} (id={}, count={})", itemName, itemId, countBefore);

        check("Drop item from inventory", () -> {
            boolean dropped = Rs2Inventory.drop(itemId);
            assertTrue(dropped, "Rs2Inventory.drop() returned false");
            boolean disappeared = sleepUntil(() -> Rs2Inventory.count(itemId) < countBefore, 5000);
            assertTrue(disappeared, "item did not leave inventory after drop");
        });

        int countAfterDrop = Rs2Inventory.count(itemId);

        check("Loot item from ground", () -> {
            boolean itemAppeared = sleepUntil(() -> Rs2GroundItem.exists(itemName, 5), 5000);
            assertTrue(itemAppeared, "item did not appear on ground after drop");
            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 5000);
            boolean looted = Rs2GroundItem.loot(itemName, 5);
            assertTrue(looted, "Rs2GroundItem.loot() returned false");
            boolean pickedUp = sleepUntil(() -> Rs2Inventory.count(itemId) > countAfterDrop, 5000);
            assertTrue(pickedUp, "item did not return to inventory after loot");
        });
    }

    private void printSummary() {
        long passed = results.stream().filter(r -> r.passed).count();
        long failed = results.stream().filter(r -> !r.passed).count();
        long total = results.size();

        log.info("========================================");
        log.info("  SMOKE TEST RESULTS: {}/{} PASSED", passed, total);
        log.info("========================================");

        if (failed > 0) {
            log.warn("FAILURES:");
            results.stream()
                    .filter(r -> !r.passed)
                    .forEach(r -> log.warn("  [FAIL] {} - {}", r.name, r.error));
        } else {
            log.info("All checks passed!");
        }

        log.info("========================================");
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }

    private static void assertNotNull(Object obj, String message) {
        if (obj == null) throw new AssertionError(message);
    }

    private static class AssertionError extends RuntimeException {
        AssertionError(String message) {
            super(message);
        }
    }

    private static class CheckResult {
        final String name;
        final boolean passed;
        final String error;

        CheckResult(String name, boolean passed, String error) {
            this.name = name;
            this.passed = passed;
            this.error = error;
        }
    }
}
