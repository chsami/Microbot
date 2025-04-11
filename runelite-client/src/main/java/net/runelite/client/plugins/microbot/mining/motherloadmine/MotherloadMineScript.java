package net.runelite.client.plugins.microbot.mining.motherloadmine;

import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMPickaxes;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMStatus;
import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.depositbox.DepositBoxLocation;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Collections;
import java.util.stream.Collectors;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MotherloadMineScript extends Script {
    public static final String VERSION = "1.9.0";

    private static final List<Integer> MLM_REGIONS = Arrays.asList(14679, 14680, 14681, 14935, 14936, 14937, 15191,
            15192, 15193);
    private static final Integer DWARF_MINE_REGION_ID = 12184;
    private static final WorldPoint DWARF_MINE_ENTRANCE = new WorldPoint(3061, 3377, 0);

    private static final WorldArea WEST_UPPER_AREA = new WorldArea(3748, 5676, 7, 9, 0);
    private static final WorldArea EAST_UPPER_AREA = new WorldArea(3756, 5667, 8, 8, 0);
    // Static areas for lower floor to avoid getting stuck behind rockfall
    private static final WorldArea WEST_LOWER_AREA = new WorldArea(3729, 5653, 10, 22, 0);
    private static final WorldArea SOUTH_LOWER_AREA = new WorldArea(3740, 5640, 20, 20, 0);

    private static final WorldPoint HOPPER_DEPOSIT_DOWN = new WorldPoint(3748, 5672, 0);
    private static final WorldPoint HOPPER_DEPOSIT_UP = new WorldPoint(3755, 5677, 0);

    private static final int UPPER_FLOOR_HEIGHT = -490;
    private static final int SACK_ID = 26688;

    @Setter
    public static MLMStatus status = MLMStatus.IDLE;
    public static WallObject oreVein;
    public static MLMMiningSpot miningSpot = MLMMiningSpot.IDLE;
    private MotherloadMineConfig config;

    private String pickaxeName = "";
    @Setter
    private static boolean shouldEmptySack = false;

    public static boolean getShouldEmptySack() {
        return shouldEmptySack;
    }

    public boolean run(MotherloadMineConfig config) {
        this.config = config;
        initialize();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::executeTask, 0, 600,
                TimeUnit.MILLISECONDS);
        return true;
    }

    private void initialize() {
        debugMessage("Initialising script");

        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.naturalMouse = true;

        MotherloadMinePlugin.setMaxSackSize(
                Microbot.getVarbitValue(Varbits.SACK_UPGRADED) == 1
                        ? MotherloadMinePlugin.getSACK_LARGE_SIZE()
                        : MotherloadMinePlugin.getSACK_SMALL_SIZE());
        MotherloadMinePlugin.setCurSackSize(
                Microbot.getVarbitValue(Varbits.SACK_NUMBER));

        miningSpot = MLMMiningSpot.IDLE;
        status = MLMStatus.IDLE;
        shouldEmptySack = false;

        if (!Microbot.isLoggedIn() || Microbot.getClient().getGameState().ordinal() <= GameState.LOADING.ordinal()) {
            new Login();
            sleepUntil(() -> Microbot.isLoggedIn(), 1000);
        }

        if (!Microbot.isLoggedIn())
            return;

        if (checkInMlm() && hasRequiredTools())
            return;

        Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
        if (Rs2Bank.isOpen())
            Rs2Bank.closeBank();
        if (Rs2DepositBox.isOpen())
            Rs2DepositBox.closeDepositBox();

        // anywhere on the 0Z plane
        if (!Rs2Player.isInCave() && !checkInMlm()) {
            while (Rs2Player.getWorldLocation().getRegionID() != DWARF_MINE_REGION_ID) {
                Rs2Walker.walkTo(DWARF_MINE_ENTRANCE);
                Rs2Player.waitForWalking(18000);
                sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo2D(DWARF_MINE_ENTRANCE) <= 15);
                Rs2GameObject.interact("Staircase");
                sleepUntil(() -> Rs2Player.isInCave(), 600);
            }
        }

        // in dwarf mine
        if (Rs2Player.getWorldLocation().getRegionID() == DWARF_MINE_REGION_ID) {
            if ((config.pickAxeInInventory() && !Rs2Inventory.hasItem("pickaxe", false))
                    || (!config.pickAxeInInventory() && !Rs2Equipment.isWearing("pickaxe", false))) {
                Rs2GameObject.interact("Staircase");
                sleepUntil(() -> !Rs2Player.isInCave());
                Rs2Walker.walkTo(BankLocation.FALADOR_EAST.getWorldPoint());
                getPickaxe(!config.pickAxeInInventory());
                Rs2Walker.walkTo(DWARF_MINE_ENTRANCE);
                Rs2Player.waitForWalking(5000);
                Rs2GameObject.interact("Staircase");
                sleepUntil(() -> Rs2Player.isInCave());
            }
            Rs2GameObject.interact("Cave");
            sleepUntil(() -> checkInMlm(), 600);
        }

        // in mlm
        if (checkInMlm()) {
            Rs2Walker.walkTo(BankLocation.MOTHERLOAD.getWorldPoint());
            Rs2Player.waitForWalking(18000);
            ensureLowerFloor();
            if (!hasRequiredTools() || hasGemInInventory())
                bankItems();
            if (Rs2Inventory.hasItem(ItemID.PAYDIRT))
                depositHopper();
        }
    }

    private void executeTask() {
        if (!super.run() || Microbot.pauseAllScripts) {
            resetMiningState();
            return;
        }
        if (!Microbot.isLoggedIn() || Microbot.getClient().getGameState().ordinal() <= GameState.LOADING.ordinal())
            return;

        if (Rs2AntibanSettings.actionCooldownActive)
            return;

        if (Rs2Player.isAnimating() || Microbot.getClient().getLocalPlayer().isInteracting())
            return;

        Rs2Walker.disableTeleports = true;

        handleDragonPickaxeSpec();
        determineStatusFromInventory();

        switch (status) {
            case IDLE:
                break;
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

    private void determineStatusFromInventory() {
        if (!hasRequiredTools()) {
            bankItems();
            return;
        }

        int sackCount = MotherloadMinePlugin.getCurSackSize();
        if (sackCount + Rs2Inventory.count(ItemID.PAYDIRT) >= MotherloadMinePlugin.getMaxSackSize()
                || shouldEmptySack) {
            resetMiningState();
            status = MLMStatus.EMPTY_SACK;
        } else if (!Rs2Inventory.isFull()) {
            status = MLMStatus.MINING;
        } else { // Inventory is full
            resetMiningState();
            if (Rs2Inventory.hasItem(ItemID.PAYDIRT)
                    && sackCount + Rs2Inventory.count(ItemID.PAYDIRT) <= MotherloadMinePlugin.getMaxSackSize()) {
                if (Rs2GameObject.getGameObjects(ObjectID.BROKEN_STRUT).size() > 1 && Rs2Inventory.hasItem("hammer")) {
                    status = MLMStatus.FIXING_WATERWHEEL;
                } else {
                    status = MLMStatus.DEPOSIT_HOPPER;
                }
            } else {
                status = MLMStatus.BANKING;
            }
        }

        if (hasOreInInventory() && Rs2Inventory.isFull())
            status = MLMStatus.BANKING;
    }

    private boolean hasRequiredTools() {
        boolean hasHammer = Rs2Inventory.hasItem("hammer");
        boolean hasPickaxe = config.pickAxeInInventory()
                ? Rs2Inventory.hasItem("pickaxe", false)
                : Rs2Equipment.isWearing("pickaxe", false);
        return hasHammer && hasPickaxe;
    }

    private void handleMining() {
        if (oreVein != null && Rs2Antiban.isMining())
            return;

        if (miningSpot == MLMMiningSpot.IDLE) {
            selectRandomMiningSpot();
        }

        if (walkToMiningSpot()) {
            if (!Rs2Player.isMoving()) {
                attemptToMineVein();
                Rs2Antiban.actionCooldown();
                if (Rs2Antiban.takeMicroBreakByChance())
                    debugMessage("Taking a micro break");
            }
        }
    }

    private void emptySack() {
        ensureLowerFloor();
        Rs2Walker.walkTo(Rs2GameObject.findObjectById(SACK_ID).getWorldLocation());
        debugMessage("Emptying sack");
        while (MotherloadMinePlugin.getCurSackSize() > 0) {
            if (Rs2Inventory.hasItem(ItemID.PAYDIRT)) {
                if (MotherloadMinePlugin.getMaxSackSize() >= MotherloadMinePlugin.getCurSackSize() + Rs2Inventory
                        .count(ItemID.PAYDIRT)) {
                    debugMessage("Depositing extra payddirt");
                    Rs2GameObject.interact(HOPPER_DEPOSIT_DOWN);
                }
            }
            if (Rs2Inventory.count() >= 2) {
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
                ItemID.RUNITE_ORE,
                ItemID.ADAMANTITE_ORE,
                ItemID.MITHRIL_ORE,
                ItemID.GOLD_ORE,
                ItemID.COAL,
                ItemID.GOLDEN_NUGGET);
    }

    private boolean hasGemInInventory() {
        return Rs2Inventory.hasItem(
                ItemID.UNCUT_DIAMOND,
                ItemID.UNCUT_RUBY,
                ItemID.UNCUT_EMERALD,
                ItemID.UNCUT_SAPPHIRE,
                ItemID.UNCUT_OPAL,
                ItemID.UNCUT_JADE,
                ItemID.UNCUT_RED_TOPAZ,
                ItemID.UNCUT_DRAGONSTONE);
    }

    private void fixWaterwheel() {
        ensureLowerFloor();
        if (Rs2Walker.walkTo(new WorldPoint(3741, 5666, 0), 15)) {
            debugMessage("Fixing broken water wheel");
            Microbot.isGainingExp = false;
            if (Rs2GameObject.interact(ObjectID.BROKEN_STRUT)) {
                sleepUntil(() -> Microbot.isGainingExp);
            }
        }
    }

    public void depositHopper() {
        WorldPoint hopperDeposit = (isUpperFloor() && config.upstairsHopperUnlocked()) ? HOPPER_DEPOSIT_UP
                : HOPPER_DEPOSIT_DOWN;
        Optional<GameObject> hopper = Optional
                .ofNullable(Rs2GameObject.findObject(ObjectID.HOPPER_26674, hopperDeposit));

        if (isUpperFloor() && !config.upstairsHopperUnlocked())
            ensureLowerFloor();

        if (!hopper.isPresent())
            Rs2Walker.walkTo(hopperDeposit, 15);

        if (MotherloadMinePlugin.getCurSackSize() + Rs2Inventory.count(ItemID.PAYDIRT) >= MotherloadMinePlugin
                .getMaxSackSize()) {
            debugMessage("Sack will fill with current inventory");
            shouldEmptySack = true;
        }

        debugMessage("Depositing paydirt in hopper");
        Rs2Walker.walkTo(hopper.get().getWorldLocation());
        sleepUntil(() -> !Rs2Player.isMoving(), 300);
        Rs2GameObject.interact(hopper.get());
        sleepUntil(() -> !Rs2Player.isInteracting(), 300);

        if (hasGemInInventory()) {
            debugMessage("Depositing gems mined");
            bankItems();
        }
    }

    public void bankItems() {
        ensureLowerFloor();
        if (Rs2Bank.useBank()) {
            sleepUntil(Rs2Bank::isOpen);
            debugMessage("Banking non-essential items");
            Rs2Bank.depositAllExcept("hammer", pickaxeName, "pay-dirt");
            sleep(100, 300);
            if (!Rs2Inventory.hasItem("hammer")) {
                if (!Rs2Bank.hasItem("hammer")) {
                    Microbot.showMessage("No hammer found in the bank.");
                    shutdown();
                }
                debugMessage("Withdrawing hammer");
                Rs2Bank.withdrawOne("hammer", true);
            }
            getPickaxe(!config.pickAxeInInventory());
            Rs2Inventory.waitForInventoryChanges(600);
        }
    }

    private void getPickaxe(boolean equip) {
        if (!Rs2Bank.isOpen())
            Rs2Bank.openBank();
        sleepUntil(() -> Rs2Bank.isOpen(), 18000);
        List<Integer> picks = new ArrayList<>();
        picks.addAll(
                Rs2Bank.getItems().stream()
                        .filter((i) -> i.getName().contains("pickaxe"))
                        .map((w) -> w.getItemId())
                        .collect(Collectors.toList()));
        picks.addAll(
                Rs2Inventory.items().stream()
                        .filter((i) -> i.name.contains("pickaxe"))
                        .map((w) -> w.id)
                        .collect(Collectors.toList()));
        if (Rs2Equipment.isWearing("pickaxe")) {
            Rs2ItemModel pickWielded = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
            picks.add(pickWielded.id);
        }
        if (picks.size() == 0) {
            Microbot.showMessage("No pickaxes found", 60);
            shutdown();
        }
        MLMPickaxes bestPick = getBestPickaxe(picks);
        if (bestPick.getItemName() != pickaxeName)
            debugMessage("Best current pickaxe: " + bestPick.getItemName());
        pickaxeName = bestPick.getItemName();
        if (!Rs2Inventory.hasItem(bestPick.getItemId())) {
            Rs2Bank.depositAllExcept("hammer", pickaxeName, "pay-dirt");
            if (equip)
                Rs2Bank.withdrawAndEquip(bestPick.getItemId());
            else
                Rs2Bank.withdrawOne(bestPick.getItemId());
        }
    }

    private MLMPickaxes getBestPickaxe(List<Integer> items) {
        MLMPickaxes bestPickaxe = null;
        debugMessage("Determining best available pickaxe");
        for (MLMPickaxes pickaxe : MLMPickaxes.values()) {
            if (items.stream().noneMatch(i -> i.equals(pickaxe.getItemId())))
                continue;
            if (pickaxe.hasRequirements(config.pickAxeInInventory())) {
                if (bestPickaxe == null || pickaxe.getMiningLevel() >= bestPickaxe.getMiningLevel()) {
                    bestPickaxe = pickaxe;
                }
            } else if (pickaxe.hasRequirements(!config.pickAxeInInventory())) {
                if (bestPickaxe == null
                        || pickaxe.getMiningLevel() >= bestPickaxe.getMiningLevel()
                                && pickaxe.getAttackLevel() >= bestPickaxe.getAttackLevel()) {
                    bestPickaxe = pickaxe;
                }
            }
        }
        return bestPickaxe;
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
        debugMessage("Walking to mining location");
        WorldPoint target = miningSpot.getWorldPoint().get(0);
        if (config.mineUpstairs() && !isUpperFloor()) {
            goUp();
        }
        return config.mineUpstairs() && isUpperFloor() || Rs2Walker.walkTo(target, 10);
    }

    private void attemptToMineVein() {
        WallObject vein = findClosestVein();
        if (vein == null) {
            repositionCameraAndMove();
            return;
        }

        debugMessage("Attempting to mine vein");
        Rs2Camera.turnTo(vein);
        if (Rs2GameObject.interact(vein)) {
            oreVein = vein;
            sleepUntil(() -> Rs2Antiban.isMining(), 5000);
            if (!Rs2Antiban.isMining()) {
                oreVein = null;
            }
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
        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();
        WorldPoint walkableTile = Rs2Tile.getNearestWalkableTile(wallObject.getWorldLocation());
        if (walkableTile == null)
            return Integer.MAX_VALUE;
        return playerLoc.distanceTo2D(walkableTile);
    }

    private void repositionCameraAndMove() {
        Rs2Camera.resetPitch();
        Rs2Camera.resetZoom();
        Rs2Camera.turnTo(
                LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), miningSpot.getWorldPoint().get(0)));
        Rs2Walker.walkFastCanvas(miningSpot.getWorldPoint().get(0));
    }

    private void goUp() {
        if (isUpperFloor())
            return;
        Rs2GameObject.interact(NullObjectID.NULL_19044);
        sleepUntil(this::isUpperFloor);
    }

    private void goDown() {
        if (!isUpperFloor())
            return;
        Rs2GameObject.interact(NullObjectID.NULL_19045);
        sleepUntil(() -> !isUpperFloor());
    }

    private void ensureLowerFloor() {
        if (isUpperFloor())
            goDown();
    }

    private boolean isUpperFloor() {
        int height = Perspective.getTileHeight(
                Microbot.getClient(),
                Microbot.getClient().getLocalPlayer().getLocalLocation(),
                0);
        return height < UPPER_FLOOR_HEIGHT;
    }

    private boolean checkInMlm() {
        int currentMapRegionID = Rs2Player.getWorldLocation().getRegionID();
        return MLM_REGIONS.contains(currentMapRegionID);
    }

    private void resetMiningState() {
        oreVein = null;
        miningSpot = MLMMiningSpot.IDLE;
    }

    private void debugMessage(String msg) {
        if (!config.printDebugMessages())
            return;
        Microbot.log("[MLM] " + msg);
    }

    @Override
    public void shutdown() {
        Rs2Antiban.resetAntibanSettings();
        resetMiningState();
        status = MLMStatus.IDLE;
        pickaxeName = "";
        shouldEmptySack = false;
        Rs2Walker.setTarget(null);
        super.shutdown();
    }
}
