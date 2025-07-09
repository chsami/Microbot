package net.runelite.client.plugins.microbot.hal.blessedwine;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Slf4j
public class BlessedWineScript extends Script {
    private BlessedWineState state = BlessedWineState.INITIALIZING;
    private boolean initialized = false;


    // === Locations ===
    private static final WorldPoint EXPOSED_ALTAR = new WorldPoint(1437, 3143, 0);
    private static final WorldPoint LIBATION_BOWL = new WorldPoint(1457, 3188, 0);
    private static final WorldPoint SHRINE_OF_RALOS = new WorldPoint(1449, 3172, 0);
    private static final WorldPoint CAM_TORUM_BANK = new WorldPoint(1454, 9568, 1);
    private static final WorldArea ALTAR_TOP = new WorldArea(1427, 3137, 16, 16, 0);
    private static final WorldArea LIBATION_ROOM = new WorldArea(1449, 3186, 16, 16, 0);
    private static final WorldArea TEMPLE = new WorldArea(1445, 3170, 10, 10, 0);
    private static final WorldArea BANK = new WorldArea(1448, 9564, 8, 8, 1);

    // === Item IDs ===
    private static final List<Integer> CALCIFIED_MOTH = Arrays.asList(
            ItemID.VARLAMORE_MINING_TELEPORT,
            ItemID.VARLAMORE_MINING_TELEPORT_1,
            ItemID.VARLAMORE_MINING_TELEPORT_2,
            ItemID.VARLAMORE_MINING_TELEPORT_3,
            ItemID.VARLAMORE_MINING_TELEPORT_4,
            ItemID.VARLAMORE_MINING_TELEPORT_5,
            ItemID.VARLAMORE_MINING_TELEPORT_25);
    private static final Integer BLESSED_BONE_SHARD = ItemID.BLESSED_BONE_SHARD;
    private static final Integer JUG_OF_WINE = ItemID.JUG_WINE;
    private static final Integer BLESSED_WINE = ItemID.JUG_WINE_BLESSED;

    public boolean run() {
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            if (!Microbot.isLoggedIn()) return;
            if (!super.run()) return;
            int maxPrayerLevel = Microbot.getClient().getRealSkillLevel(Skill.PRAYER);
            int currentPrayerPoints = (Rs2Player.getPrayerPercentage() * maxPrayerLevel) / 100;

            switch (state) {
                case INITIALIZING:
                    BlessedWinePlugin.status = "Initializing run...";
                    initialize();
                    break;

                case WALK_TO_ALTAR:
                    BlessedWinePlugin.status = "Walking to Exposed Altar...";
                    Rs2Walker.walkTo(EXPOSED_ALTAR);
                    Rs2Player.waitForWalking();
                    if (!ALTAR_TOP.contains(Rs2Player.getWorldLocation())) return;
                    state = BlessedWineState.BLESS_AT_ALTAR;
                    break;

                case BLESS_AT_ALTAR:
                    BlessedWinePlugin.status = "Blessing wine at altar...";
                    Rs2GameObject.interact(52799, "Bless");
                    Rs2Inventory.waitForInventoryChanges(1200);
                    if (!Rs2Inventory.hasItem(BLESSED_WINE)) return;
                    state = BlessedWineState.WALK_TO_BOWL;
                    break;

                case WALK_TO_BOWL:
                    BlessedWinePlugin.status = "Walking to Libation Bowl...";
                    Rs2Walker.walkTo(LIBATION_BOWL);
                    Rs2Player.waitForWalking();
                    if (!LIBATION_ROOM.contains(Rs2Player.getWorldLocation())) return;
                    state = BlessedWineState.USE_LIBATION_BOWL;
                    break;

                case USE_LIBATION_BOWL:
                    BlessedWinePlugin.status = "Using Libation Bowl...";
                    Rs2GameObject.interact(53018, "Fill");
                    if (currentPrayerPoints > 2 && !Rs2Player.isAnimating()) return;
                    if (currentPrayerPoints < 2 && !Rs2Player.isAnimating()) {
                        state = BlessedWineState.WALK_TO_SHRINE;
                    }
                    if (!Rs2Inventory.hasItem(BLESSED_WINE) || !Rs2Inventory.hasItem(BLESSED_BONE_SHARD)) {
                        state = BlessedWineState.TELEPORT_TO_BANK;
                    }
                    break;

                case WALK_TO_SHRINE:
                    BlessedWinePlugin.status = "Walking to Shrine of Ralos...";
                    Rs2Walker.walkTo(SHRINE_OF_RALOS);
                    Rs2Player.waitForWalking();
                    if (!TEMPLE.contains(Rs2Player.getWorldLocation())) return;
                    state = BlessedWineState.RESTORE_PRAYER;
                    break;

                case RESTORE_PRAYER:
                    BlessedWinePlugin.status = "Restoring prayer...";
                    Rs2GameObject.interact(52405, "Bask");
                    Rs2Player.waitForAnimation(5000);
                    if (currentPrayerPoints != maxPrayerLevel) return;
                    if (!Rs2Inventory.hasItem(BLESSED_WINE)) {
                        state = BlessedWineState.TELEPORT_TO_BANK;
                    } else {
                        state = BlessedWineState.WALK_TO_BOWL;
                    }
                    break;

                case TELEPORT_TO_BANK:
                    BlessedWinePlugin.status = "Teleporting back to Cam Torum Bank...";
                    if (Rs2Player.getWorldLocation().getPlane() != 1) {
                        for (int id : CALCIFIED_MOTH) {
                            if (!isRunning()) break;
                            if (Rs2Inventory.hasItem(id)) {
                                Rs2Inventory.interact(id, "Crush");
                                Rs2Player.waitForAnimation();
                            }
                        }
                    }
                    if (Rs2Player.getWorldLocation().getPlane() != 0) {
                        Rs2Walker.walkTo(CAM_TORUM_BANK);
                        Rs2Player.waitForWalking();
                    }
                    if (!BANK.contains(Rs2Player.getWorldLocation())) return;
                    if (!Rs2Bank.isOpen()) {
                        Rs2Bank.openBank();
                        sleepUntil(Rs2Bank::isOpen, 20000);
                    }
                    if (Rs2Bank.isOpen()) {
                        state = BlessedWineState.BANKING;
                    }
                    break;

                case BANKING:
                    BlessedWinePlugin.status = "Banking and preparing next run...";
                    if (!Rs2Bank.isOpen()) {
                        Rs2Bank.openBank();
                        sleepUntil(Rs2Bank::isOpen, 20000);
                        return;
                    }
                    if (!Rs2Bank.hasItem(JUG_OF_WINE)) {
                        state = BlessedWineState.FINISHED;
                    }
                    if (!Rs2Inventory.hasItem(BLESSED_BONE_SHARD) && !Rs2Bank.hasItem(BLESSED_BONE_SHARD)) {
                        state = BlessedWineState.FINISHED;
                    }
                    BlessedWinePlugin.totalWinesToBless = Rs2Bank.count(JUG_OF_WINE);
                    Rs2Bank.withdrawAll(JUG_OF_WINE);
                    Rs2Inventory.waitForInventoryChanges(1200);
                    if (!Rs2Inventory.contains(BLESSED_BONE_SHARD, JUG_OF_WINE)) {
                        state = BlessedWineState.FINISHED;
                    }
                    int currentXp = getCurrentXp();
                    BlessedWinePlugin.endingXp = currentXp - BlessedWinePlugin.startingXp;
                    BlessedWinePlugin.loopCount++;
                    state = BlessedWineState.WALK_TO_ALTAR;
                    break;

                case FINISHED:
                    BlessedWinePlugin.status = "Finished: out of materials or XP complete.";
                    shutdown();
                    break;
            }

        }, 0, 1200, TimeUnit.MILLISECONDS);

        return true;
    }



    private void initialize() {
        Rs2Walker.walkTo(CAM_TORUM_BANK);
        if (!Rs2Bank.openBank()) return;
        if (!Rs2Bank.isOpen()) return;
        if (!Rs2Bank.hasItem(BLESSED_BONE_SHARD) || !Rs2Bank.hasItem(JUG_OF_WINE)) {
            state = BlessedWineState.FINISHED;
            return;
        }
        int jugCount = Rs2Bank.count(JUG_OF_WINE);
        int boneCount = Rs2Bank.count(BLESSED_BONE_SHARD);

        int wineMultiplier = jugCount * 400;
        int blessingCapacity = (wineMultiplier > boneCount)
                ? (boneCount / 400) * 400
                : wineMultiplier;

        int winesTotal = blessingCapacity / 400;
        int expectedXp = blessingCapacity * 5;
        int expectedRuns = winesTotal / 26;

        Rs2Bank.depositAll();
        sleepUntilTrue(() -> !Rs2Inventory.isFull(), 300, 2000);

        for (int id : CALCIFIED_MOTH) {
            if (!isRunning()) break;
            if (Rs2Bank.hasItem(id)) {
                Rs2Bank.withdrawAll(id);
                Rs2Inventory.waitForInventoryChanges(2400);
                break;
            }
        }

        Rs2Bank.withdrawAll(BLESSED_BONE_SHARD);
        Rs2Inventory.waitForInventoryChanges(2400);

        Rs2Bank.withdrawAll(JUG_OF_WINE);
        Rs2Inventory.waitForInventoryChanges(2400);

        if (!Rs2Inventory.isFull()) return;

        // Overlay state
        BlessedWinePlugin.loopCount = 0;
        BlessedWinePlugin.totalLoops = expectedRuns;
        BlessedWinePlugin.startingXp = getCurrentXp();
        BlessedWinePlugin.expectedXp = expectedXp + getCurrentXp();
        BlessedWinePlugin.endingXp = 0;
        BlessedWinePlugin.totalWinesToBless = winesTotal;

        state = BlessedWineState.WALK_TO_ALTAR;
        initialized = true;
    }

    private int getCurrentXp() {
        return Microbot.getClient().getSkillExperience(Skill.PRAYER);
    }

    @Override
    public void shutdown() {
        state = BlessedWineState.INITIALIZING;
        initialized = false;
        super.shutdown();
    }
}