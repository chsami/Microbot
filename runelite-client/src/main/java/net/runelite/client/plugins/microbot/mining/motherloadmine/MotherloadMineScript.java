package net.runelite.client.plugins.microbot.mining.motherloadmine;

import net.runelite.api.*;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMStatus;
import net.runelite.client.plugins.microbot.questhelper.questinfo.ExternalQuestResources;
import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.depositbox.DepositBoxLocation;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;


import lombok.extern.slf4j.Slf4j;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.Optional;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ScheduledFuture;

@Slf4j
public class MotherloadMineScript extends Script {
    public static final String VERSION = "2.8.0";
    private static boolean inti = false;

    private static final WorldArea WEST_UPPER_AREA = new WorldArea(3748, 5676, 7, 9, 0);
    private static final WorldArea EAST_UPPER_AREA = new WorldArea(3756, 5667, 8, 8, 0);
    // Static areas for lower floor to avoid getting stuck behind rockfall
    private static final WorldArea WEST_LOWER_AREA = new WorldArea(3729, 5653, 10, 22, 0);
    private static final WorldArea SOUTH_LOWER_AREA = new WorldArea(3740, 5640, 20, 20, 0);

    private static final WorldPoint HOPPER_DEPOSIT_DOWN = new WorldPoint(3748, 5672, 0);
    private static final WorldPoint HOPPER_DEPOSIT_UP = new WorldPoint(3755, 5677, 0);

    private static final int UPPER_FLOOR_HEIGHT = -490;
    private static final int SACK_LARGE_SIZE = 162;
    private static final int SACK_SIZE = 81;
    private static final int SACK_ID = 26688;

    public static MLMStatus status = MLMStatus.IDLE;
    public static WallObject oreVein;
    public static MLMMiningSpot miningSpot = MLMMiningSpot.IDLE;
    private int maxSackSize;
    private MotherloadMineConfig config;

    private String pickaxeName = "";
    private boolean shouldEmptySack = false;

    public void run(MotherloadMineConfig config) {
        this.config = config;
        miningSpot = MLMMiningSpot.IDLE;
        status = MLMStatus.SETUP;
        shouldEmptySack = false;

        try {
            if (!super.run() || !Microbot.isLoggedIn()) return;
            if (Microbot.pauseAllScripts) return;
            if (Rs2AntibanSettings.actionCooldownActive) return;
            if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) return;

            if (config.pickAxeInInventory()) {
                pickaxeName = Optional.ofNullable(Rs2Inventory.get("pickaxe"))
                        .map(i -> i.name)
                        .orElse("");

                if (pickaxeName.isEmpty()) {
                    Microbot.showMessage("Pickaxe not found in your inventory");
                    return;
                }
            } else {
                if (!Rs2Equipment.isEquipped("pickaxe", EquipmentInventorySlot.WEAPON)) {
                    Microbot.showMessage("Pickaxe not found in your inventory and you haven't equipt one");
                    return;
                }
            }

            walkAndTrack(new WorldPoint(3759, 5664, 0));

            mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> { executeTask(); }
            , 0, 600, TimeUnit.MILLISECONDS);
        } catch (Exception ex) {
            System.err.println(ex.getMessage());
        }
    }

    private void executeTask() {
        if (!super.run() || !Microbot.isLoggedIn()) {
            resetMiningState();
            shutdown();
        }
        if (Microbot.pauseAllScripts) return;
        if (Rs2AntibanSettings.actionCooldownActive) return;
        if (Rs2Player.isAnimating() || Rs2Player.isInteracting()) return;

        handleDragonPickaxeSpec();
        determineStatusFromInventory();

        switch (status) {
            case IDLE:
                break;
            case SETUP:
                walkAndTrack(new WorldPoint(3759, 5664, 0));
                break;
            case MINING:
                if (Rs2Antiban.isMining())
                    Rs2Antiban.setActivityIntensity(ActivityIntensity.VERY_LOW);
                else
                    Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
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
        if (Rs2Equipment.isWearing("dragon pickaxe"))
            Rs2Combat.setSpecState(true, 1000);
    }

    private void determineStatusFromInventory() {
        updateSackSize();
        if (!hasRequiredTools()) {
            bankItems();
            return;
        }

        int sackCount = Microbot.getVarbitValue(Varbits.SACK_NUMBER);

        if (sackCount > maxSackSize || (shouldEmptySack && !Rs2Inventory.contains("pay-dirt"))) {
            resetMiningState();
            status = MLMStatus.EMPTY_SACK;
        } else if (!Rs2Inventory.isFull()) {
            status = MLMStatus.MINING;
        } else // Inventory is full
        {
            resetMiningState();
            if (Rs2Inventory.hasItem(ItemID.PAYDIRT))
            {
                if (Rs2GameObject.getGameObjects(ObjectID.BROKEN_STRUT).size() > 1 && Rs2Inventory.hasItem("hammer"))
                {
                    status = MLMStatus.FIXING_WATERWHEEL;
                } else {
                    status = MLMStatus.DEPOSIT_HOPPER;
                }
                if (Rs2Inventory.contains(ItemID.UNCUT_SAPPHIRE,
                ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND,
                ItemID.UNCUT_DRAGONSTONE))
                    status = MLMStatus.BANKING;
            } else {
                status = MLMStatus.BANKING;
            }
        }

        if (Rs2Inventory.hasItem("coal") && Rs2Inventory.isFull()) {
            status = MLMStatus.BANKING;
        }
    }

    private boolean hasRequiredTools() {
        boolean hasHammer = Rs2Inventory.hasItem("hammer");
        boolean hasPickaxe = !config.pickAxeInInventory() || Rs2Inventory.hasItem(pickaxeName)
                || Rs2Equipment.isEquipped("pickaxe", EquipmentInventorySlot.WEAPON, false);

        return hasHammer && hasPickaxe;
    }

    private void updateSackSize() {
        boolean sackUpgraded = Microbot.getVarbitValue(Varbits.SACK_UPGRADED) == 1;
        maxSackSize = sackUpgraded ? SACK_LARGE_SIZE : SACK_SIZE;
    }

    private void handleMining() {
        if (oreVein != null && AntibanPlugin.isMining())
            return;

        if (miningSpot == MLMMiningSpot.IDLE) {
            selectRandomMiningSpot();
        }

        if (walkToMiningSpot()) {
            if (!Rs2Player.isMoving()) {
                attemptToMineVein();
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
        }
    }

    private void emptySack() {
        ensureLowerFloor();

        while (Microbot.getVarbitValue(Varbits.SACK_NUMBER) > 0) {
            if (Rs2Inventory.size() <= 2) {
                Rs2GameObject.interact(SACK_ID);
                sleepUntil(this::hasOreInInventory);
            }
            if (hasOreInInventory()) {
                bankItems();
            }
        }

        shouldEmptySack = false;
        Rs2Antiban.takeMicroBreakByChance();
        status = MLMStatus.IDLE;
    }

    private boolean hasOreInInventory() {
        return Rs2Inventory.contains(
                ItemID.RUNITE_ORE, ItemID.ADAMANTITE_ORE, ItemID.MITHRIL_ORE,
                ItemID.GOLD_ORE, ItemID.COAL, ItemID.UNCUT_SAPPHIRE,
                ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND,
                ItemID.UNCUT_DRAGONSTONE);
    }

    private void fixWaterwheel() {
        ensureLowerFloor();
        if (walkAndTrack(new WorldPoint(3741, 5666, 0))) {
            Microbot.isGainingExp = false;
            if (Rs2GameObject.interact(ObjectID.BROKEN_STRUT)) {
                sleepUntil(() -> Microbot.isGainingExp || Rs2GameObject.getGameObjects().stream().noneMatch(obj -> obj.getId() == ObjectID.BROKEN_STRUT), 5000);
            }
        }
    }

    private void depositHopper() {
        WorldPoint hopperDeposit = (isUpperFloor() && config.upstairsHopperUnlocked()) ? HOPPER_DEPOSIT_UP
                : HOPPER_DEPOSIT_DOWN;
        Optional<GameObject> hopper = Optional
                .ofNullable(Rs2GameObject.findObject(ObjectID.HOPPER_26674, hopperDeposit));

        if (isUpperFloor() && !config.upstairsHopperUnlocked()) {
            ensureLowerFloor();
        }
        if (hopper.isPresent() && Rs2GameObject.interact(hopper.get())) {
            sleepUntil(() -> !Rs2Inventory.isFull());
            if (Microbot.getVarbitValue(Varbits.SACK_NUMBER) > maxSackSize - 28) {
                shouldEmptySack = true;
            }
        } else {
            walkAndTrack(hopperDeposit);
        }
    }

    private void bankItems() {
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), new WorldPoint(3759, 5664, 0));
        Rs2Camera.turnTo(localPoint);
        if (Rs2Bank.useBank()) {
            sleepUntil(Rs2Bank::isOpen);
            Rs2Bank.depositAllExcept("hammer", pickaxeName);
            Rs2Inventory.waitForInventoryChanges(500);

            if (!Rs2Inventory.hasItem("hammer")) {
                if (!Rs2Bank.hasBankItem("hammer")) {
                    Microbot.showMessage("No hammer found in the bank.");
                    shutdown();
                }
                Rs2Bank.withdrawOne("hammer", true);
            }

            if (config.pickAxeInInventory() && !Rs2Inventory.hasItem(pickaxeName)) {
                Rs2Bank.withdrawOne(pickaxeName);
                sleepUntil(() -> Rs2Inventory.hasItem(pickaxeName), 500);
            }
        }
        status = MLMStatus.IDLE;
    }

    private boolean walkAndTrack(WorldPoint location) {
        LocalPoint locationPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), location);
        Rs2Camera.turnTo(locationPoint) ;
        Microbot.getClient().setCameraYawTarget(Rs2Camera.calculateCameraYaw(Rs2Camera.angleToTile(locationPoint)));

        Microbot.log("Searching path found no rockfall obsticles");
        Rs2Walker.walkCanvas(location);
        Rs2Player.waitForWalking(18000);
    }

    private void selectRandomMiningSpot() {
        // Randomly decide which spot to go to
        // More variety can be added if needed
        miningSpot = (Rs2Random.between(1, 5) == 2)
                ? (config.mineUpstairs() ? MLMMiningSpot.WEST_UPPER : MLMMiningSpot.SOUTH)
                : (config.mineUpstairs() ? MLMMiningSpot.EAST_UPPER : MLMMiningSpot.WEST_LOWER);
        Collections.shuffle(miningSpot.getWorldPoint());
    }

    private boolean walkToMiningSpot() {
        WorldPoint target = miningSpot.getWorldPoint().get(0);

        if (config.mineUpstairs() && !isUpperFloor())
            goUp();
        if (config.mineUpstairs())
            ensureUpperFloor();

        return walkAndTrack(target);
    }

    private void attemptToMineVein() {
        WallObject vein = findClosestVein();
        if (vein == null) {
            repositionCameraAndMove();
            return;
        }

        Microbot.gtClient().setCameraYawTarget(Rs2Camera.calculateCameraYaw(Rs2Camera.angleToTile(vein.getLocalLocation())));
        Rs2Camera.turnTo(vein.getLocalLocation());
        if (Rs2GameObject.interact(vein)) {
            oreVein = vein;
            sleepUntil(() -> !Rs2Antiban.isMining(), 5000);
            oreVein = null;
        }
    }

    private WallObject findClosestVein() {
        return Rs2GameObject.getWallObjects().stream()
                .filter(this::isValidVein)
                .min(Comparator.comparing(this::distanceToPlayer))
                .orElse(null);
    }

    private boolean isValidVein(WallObject wallObject) {
        int id = wallObject.getId();
        boolean isVein = (id == 26661 || id == 26662 || id == 26663 || id == 26664);
        if (!isVein)
            return false;

        if (config.mineUpstairs()) {
            boolean inUpperArea = (miningSpot == MLMMiningSpot.WEST_UPPER
                    && WEST_UPPER_AREA.contains(wallObject.getWorldLocation()))
                    || (miningSpot == MLMMiningSpot.EAST_UPPER
                            && EAST_UPPER_AREA.contains(wallObject.getWorldLocation()));
            return inUpperArea && hasWalkableTilesAround(wallObject);
        } else {
            boolean inLowerArea = (miningSpot == MLMMiningSpot.WEST_LOWER
                    && WEST_LOWER_AREA.contains(wallObject.getWorldLocation()))
                    || (miningSpot == MLMMiningSpot.SOUTH && SOUTH_LOWER_AREA.contains(wallObject.getWorldLocation()));
            return inLowerArea && hasWalkableTilesAround(wallObject);
        }

    }

    private boolean hasWalkableTilesAround(WallObject wallObject) {
        return Rs2Tile.areSurroundingTilesWalkable(wallObject.getWorldLocation(), 1, 1);
    }

    private int distanceToPlayer(WallObject wallObject) {
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        WorldPoint walkableTile = Rs2Tile.getNearestWalkableTile(wallObject.getWorldLocation());
        if (walkableTile == null)
            return Integer.MAX_VALUE;
        return playerLoc.distanceTo2D(walkableTile);
    }

    private void repositionCameraAndMove() {
        Rs2Camera.resetPitch();
        Rs2Camera.resetZoom();
        walkAndTrack(miningSpot.getWorldPoint().get(0));
    }

    private void goUp() {
        if (isUpperFloor())
            return;
        walkAndTrack(new WorldPoint(3759, 5664, 0));
        Rs2GameObject.interact(NullObjectID.NULL_19044);
        sleepUntil(this::isUpperFloor);
    }

    private void goDown() {
        if (!isUpperFloor())
            return;
        walkAndTrack(new WorldPoint(3759, 5664, 0));
        Rs2GameObject.interact(NullObjectID.NULL_19045);
        sleepUntil(() -> !isUpperFloor());
    }

    private void ensureLowerFloor() {
        if (isUpperFloor())
            goDown();
    }

    private void ensureUpperFloor() {
        if (!isUpperFloor())
            goUp();
    }

    private boolean isUpperFloor() {
        int height = Perspective.getTileHeight(
                Microbot.getClient(),
                Microbot.getClient().getLocalPlayer().getLocalLocation(),
                0);
        return height < UPPER_FLOOR_HEIGHT;
    }

    private void resetMiningState() {
        oreVein = null;
        miningSpot = MLMMiningSpot.IDLE;
    }

    @Override
    public void shutdown() {
        status = MLMStatus.IDLE;
        oreVein = null;
        miningSpot = MLMMiningSpot.IDLE;
        maxSackSize = 0;
        pickaxeName = "";
        shouldEmptySack = false;
        resetMiningState();
        super.shutdown();
    }
}
