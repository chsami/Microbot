package net.runelite.client.plugins.microbot.mining.motherloadmine;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMStatus;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;

import static net.runelite.client.plugins.microbot.util.math.Random.random;

@Slf4j
public class MotherloadMineScript extends Script {
    public static final String version = "1.6.5";
    private static final WorldArea UPSTAIRS = new WorldArea(new WorldPoint(3747, 5676, 0), 7, 8);
    private static final WorldPoint HOPPER = new WorldPoint(3748, 5674, 0);
    private static final int UPPER_FLOOR_HEIGHT = -490;
    private static final int SACK_LARGE_SIZE = 162;
    private static final int SACK_SIZE = 81;
    private static final int SACKID = 26688;
    public static MLMStatus status = MLMStatus.IDLE;
    public static WallObject oreVein;
    public static MLMMiningSpot miningSpot = MLMMiningSpot.IDLE;
    private static int maxSackSize;
    private static MotherloadMineConfig config;

    private String pickAxeInInventory = "";
    private boolean emptySack = false;

    public boolean run(MotherloadMineConfig config) {
        MotherloadMineScript.config = config;
        initialize();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::executeTask, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void initialize() {
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        miningSpot = MLMMiningSpot.IDLE;
        status = MLMStatus.IDLE;
        emptySack = false;

        if (config.pickAxeInInventory()) {
            pickAxeInInventory = Rs2Inventory.get("pickaxe").name;
        }
    }

    private void executeTask() {
        if (!super.run() || !Microbot.isLoggedIn() || Rs2Antiban.isIdleTooLong(50)) {
            oreVein = null;
            miningSpot = MLMMiningSpot.IDLE;
            return;
        }

        if (pickAxeInInventory.isEmpty() && config.pickAxeInInventory()) {
            Microbot.showMessage("Pickaxe was not found in your inventory");
            sleep(5000);
            return;
        }

        if (Rs2AntibanSettings.actionCooldownActive) return;

        if (Rs2Player.isAnimating() || Microbot.getClient().getLocalPlayer().isInteracting()) return;


        handleDragonPickaxeSpec();
        handleInventory();

        switch (status) {
            case IDLE:
                return;
            case MINING:
                Rs2Antiban.setActivityIntensity(Rs2Antiban.getActivity().getActivityIntensity());
                handleMining();
                break;
            case EMPTY_SACK:
                Rs2Antiban.setActivityIntensity(ActivityIntensity.EXTREME);
                emptySack();
                break;
            case FIXING_WATERWHEEL:
                fixWaterwheel();
                break;
            case DEPOSIT_HOPPER:
                depositHopper();
                break;
            case BANKING:
                bankItems();
                break;
        }
    }

    private void handleDragonPickaxeSpec() {
        if (Rs2Equipment.isWearing("dragon pickaxe")) {
            Rs2Combat.setSpecState(true, 1000);
        }
    }

    private void handleInventory() {
        boolean sackUpgraded = Microbot.getVarbitValue(Varbits.SACK_UPGRADED) == 1;
        maxSackSize = sackUpgraded ? SACK_LARGE_SIZE : SACK_SIZE;
        if (!Rs2Inventory.hasItem("hammer") || !Rs2Inventory.hasItem(pickAxeInInventory) && config.pickAxeInInventory()) {
            bank();
            return;
        }

        if (Microbot.getVarbitValue(Varbits.SACK_NUMBER) > maxSackSize || (emptySack && !Rs2Inventory.contains("pay-dirt"))) {
            oreVein = null;
            miningSpot = MLMMiningSpot.IDLE;
            status = MLMStatus.EMPTY_SACK;
        } else if (!Rs2Inventory.isFull()) {
            status = MLMStatus.MINING;
        } else if (Rs2Inventory.isFull()) {
            oreVein = null;
            miningSpot = MLMMiningSpot.IDLE;
            if (Rs2Inventory.hasItem(ItemID.PAYDIRT)) {
                status = Rs2GameObject.getGameObjects(ObjectID.BROKEN_STRUT).size() > 1 && Rs2Inventory.hasItem("hammer")
                        ? MLMStatus.FIXING_WATERWHEEL
                        : MLMStatus.DEPOSIT_HOPPER;
            } else {
                status = MLMStatus.BANKING;
            }
        }

        if (Rs2Inventory.hasItem("coal") && Rs2Inventory.isFull()) {
            status = MLMStatus.BANKING;
        }
    }

    private void handleMining() {
        if (oreVein != null) return;

        if (miningSpot == MLMMiningSpot.IDLE) {
            findRandomMiningSpot();
        } else {
            if (walkToMiningSpot()) {
                if (Rs2Player.isMoving()) return;
                mineVein();
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
        }
    }

    private void emptySack() {
        if (isUpperFloor()) goDown();

        while (Microbot.getVarbitValue(Varbits.SACK_NUMBER) > 0) {
            if (Rs2Inventory.size() <= 2) {
                Rs2GameObject.interact(SACKID);
                sleepUntil(() -> Rs2Inventory.contains(
                        ItemID.RUNITE_ORE, ItemID.ADAMANTITE_ORE, ItemID.MITHRIL_ORE,
                        ItemID.GOLD_ORE, ItemID.COAL, ItemID.UNCUT_SAPPHIRE,
                        ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND,
                        ItemID.UNCUT_DRAGONSTONE));
            }
            if (Rs2Inventory.contains(
                    ItemID.RUNITE_ORE, ItemID.ADAMANTITE_ORE, ItemID.MITHRIL_ORE,
                    ItemID.GOLD_ORE, ItemID.COAL, ItemID.UNCUT_SAPPHIRE,
                    ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND,
                    ItemID.UNCUT_DRAGONSTONE))
                bank();
        }
        emptySack = false;
        Rs2Antiban.takeMicroBreakByChance();
        status = MLMStatus.IDLE;
    }

    private void fixWaterwheel() {
        if (isUpperFloor()) goDown();
        if (Rs2Walker.walkTo(new WorldPoint(3741, 5666, 0), 15)) {
            Microbot.isGainingExp = false;
            if (Rs2GameObject.interact(ObjectID.BROKEN_STRUT))
                sleepUntil(() -> Microbot.isGainingExp);
        }

    }

    private void depositHopper() {
        if (!isUpperFloor() || isUpperFloor()) {
            if (Rs2GameObject.interact(ObjectID.HOPPER_26674)) {
                sleepUntil(() -> !Rs2Inventory.isFull());
                if (Microbot.getVarbitValue(Varbits.SACK_NUMBER) > maxSackSize - 28) {
                    emptySack = true;
                }
            } else {
                Rs2Walker.walkTo(HOPPER, 15);
            }
        }
    }

    private void bankItems() {
        //Rs2Walker.walkTo(new WorldPoint(3759, 5666, 0));
        bank();
    }

    private void bank() {
        if (Rs2Bank.useBank()) {
            sleepUntil(Rs2Bank::isOpen);
            Rs2Bank.depositAllExcept("hammer", pickAxeInInventory);
            sleep(100, 300);

            if (!Rs2Bank.hasItem("hammer") && !Rs2Inventory.hasItem("hammer")) {
                Microbot.showMessage("No hammer found in the bank.");
                sleep(5000);
                return;
            }
            if (!Rs2Inventory.hasItem("hammer")) Rs2Bank.withdrawOne("hammer", true);
            if (config.pickAxeInInventory() && !Rs2Inventory.hasItem(pickAxeInInventory)) {
                Rs2Bank.withdrawOne(pickAxeInInventory);
            }
            sleep(600);
        }
    }

    private void findRandomMiningSpot() {
        miningSpot = random(1, 5) == 2
                ? (config.mineUpstairs() ? MLMMiningSpot.WEST_UPPER : MLMMiningSpot.SOUTH)
                : (config.mineUpstairs() ? MLMMiningSpot.EAST_UPPER : MLMMiningSpot.WEST_LOWER);
        Collections.shuffle(miningSpot.getWorldPoint());
    }

    private boolean walkToMiningSpot() {
        WorldPoint miningWorldPoint = miningSpot.getWorldPoint().get(0);
        if (!isUpperFloor() && config.mineUpstairs()) goUp();
        return Rs2Walker.walkTo(miningWorldPoint, 10);
    }

    private boolean mineVein() {
        if (Rs2Player.isMoving()) return false;

        WallObject closestVein = findClosestVein();
        if (closestVein == null) {
            moveToMiningSpot();
            return false;
        }

        interactWithVein(closestVein);
        return true;
    }

    private WallObject findClosestVein() {
        return Rs2GameObject.getWallObjects().stream()
                .filter(this::isVein)
                .sorted(Comparator.comparingInt(this::distanceToPlayer))
                .filter(Rs2GameObject::hasLineOfSight)
                .findFirst()
                .orElse(null);
    }

    private boolean isVein(WallObject wallObject) {
        int id = wallObject.getId();
        return id == 26661 || id == 26662 || id == 26663 || id == 26664;
    }

    private int distanceToPlayer(WallObject wallObject) {
        return Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(wallObject.getWorldLocation());
    }

    private void moveToMiningSpot() {
        Rs2Walker.walkFastCanvas(miningSpot.getWorldPoint().get(0));
    }

    private void interactWithVein(WallObject vein) {
        if (Rs2GameObject.interact(vein))
            oreVein = vein;
        sleepUntil(Rs2Player::isAnimating, 5000);
        if (!Rs2Player.isAnimating()) {
            oreVein = null;
        }
    }

    private void goUp() {
        if (isUpperFloor()) return;
        Rs2GameObject.interact(NullObjectID.NULL_19044);
        sleepUntil(this::isUpperFloor);
    }

    private void goDown() {
        if (!isUpperFloor()) return;
        Rs2GameObject.interact(NullObjectID.NULL_19045);
        sleepUntil(() -> !isUpperFloor());
    }

    private boolean isUpperFloor() {
        return Perspective.getTileHeight(Microbot.getClient(), Microbot.getClient().getLocalPlayer().getLocalLocation(), 0) < UPPER_FLOOR_HEIGHT;
    }

    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        oreVein = null;
        miningSpot = MLMMiningSpot.IDLE;
        Rs2Walker.setTarget(null);
        super.shutdown();
    }
}
