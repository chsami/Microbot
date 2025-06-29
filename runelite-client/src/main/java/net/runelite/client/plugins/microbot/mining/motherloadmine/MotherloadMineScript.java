package net.runelite.client.plugins.microbot.mining.motherloadmine;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMMiningSpotList;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMStatus;
import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Gembag;

import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Slf4j
public class MotherloadMineScript extends Script
{
    public static final String VERSION = "1.7.2";

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
    private boolean gemBagEmptiedThisCycle = false;


    public boolean run(MotherloadMineConfig config)
    {
        this.config = config;
        initialize();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::executeTask, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void initialize()
    {
        Rs2Antiban.antibanSetupTemplates.applyMiningSetup();
        miningSpot = MLMMiningSpot.IDLE;
        status = MLMStatus.IDLE;
        shouldEmptySack = false;

        if (config.pickAxeInInventory())
        {
            pickaxeName = Optional.ofNullable(Rs2Inventory.get("pickaxe"))
                    .map(Rs2ItemModel::getName)
                    .orElse("");
        }
    }

    private void executeTask()
    {
        if (!super.run() || !Microbot.isLoggedIn())
        {
            resetMiningState();
            return;
        }

        if (config.pickAxeInInventory() && pickaxeName.isEmpty())
        {
            Microbot.showMessage("Pickaxe not found in your inventory");
            shutdown();
            return;
        }

        if (Rs2AntibanSettings.actionCooldownActive) return;
        if (Rs2Player.isAnimating() || Microbot.getClient().getLocalPlayer().isInteracting()) return;

        determineStatusFromInventory();

        switch (status)
        {
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

    private void handlePickaxeSpec()
    {
        if (Rs2Equipment.isWearing("dragon pickaxe") || Rs2Equipment.isWearing("crystal pickaxe"))
        {
            Rs2Combat.setSpecState(true, 1000);
        }
    }

    private void determineStatusFromInventory()
    {
        updateSackSize();
        if (!hasRequiredTools())
        {
            bankItems();
            return;
        }

        int sackCount = Microbot.getVarbitValue(Varbits.SACK_NUMBER);

        if (sackCount > maxSackSize || (shouldEmptySack && !Rs2Inventory.contains("pay-dirt")))
        {
            resetMiningState();
            status = MLMStatus.EMPTY_SACK;
        }
        else if (!Rs2Inventory.isFull())
        {
            status = MLMStatus.MINING;
        }
        else // Inventory is full
        {
            resetMiningState();
            if (Rs2Inventory.hasItem(ItemID.PAYDIRT))
            {
                if (Rs2GameObject.getGameObjects(o -> o.getId() == ObjectID.BROKEN_STRUT).size() > 1 && (Rs2Inventory.hasItem("hammer") || Rs2Equipment.isWearing("hammer")))
                {
                    status = MLMStatus.FIXING_WATERWHEEL;
                }
                else
                {
                    status = MLMStatus.DEPOSIT_HOPPER;
                }
            }
            else
            {
                status = MLMStatus.BANKING;
            }
        }

        if (Rs2Inventory.hasItem("coal") && Rs2Inventory.isFull())
        {
            status = MLMStatus.BANKING;
        }
    }

    private boolean hasRequiredTools()
    {
        boolean hasHammer = Rs2Inventory.hasItem("hammer") || Rs2Equipment.isWearing("hammer");
        boolean hasPickaxe = !config.pickAxeInInventory() || Rs2Inventory.hasItem(pickaxeName);
        return hasHammer && hasPickaxe;
    }

    private void updateSackSize()
    {
        boolean sackUpgraded = Microbot.getVarbitValue(Varbits.SACK_UPGRADED) == 1;
        maxSackSize = sackUpgraded ? SACK_LARGE_SIZE : SACK_SIZE;
    }

    private void handleMining()
    {
        if (oreVein != null && AntibanPlugin.isMining()) return;

        if (miningSpot == MLMMiningSpot.IDLE)
        {
            selectMiningSpotFromConfig();
        }

        if (walkToMiningSpot())
        {
            if (!Rs2Player.isMoving())
            {
                attemptToMineVein();
                Rs2Antiban.actionCooldown();
                Rs2Antiban.takeMicroBreakByChance();
            }
        }
    }

    private void emptySack()
    {
        ensureLowerFloor();

        while (Microbot.getVarbitValue(Varbits.SACK_NUMBER) > 0)
        {
            if (Rs2Inventory.size() <= 2)
            {
                Rs2GameObject.interact(SACK_ID);
                sleepUntil(this::hasOreInInventory);
            }
            if (hasOreInInventory())
            {
                bankItems();
            }
        }

        shouldEmptySack = false;
        Rs2Antiban.takeMicroBreakByChance();
        status = MLMStatus.IDLE;
    }

    private boolean hasOreInInventory()
    {
        return Rs2Inventory.contains(
                ItemID.RUNITE_ORE, ItemID.ADAMANTITE_ORE, ItemID.MITHRIL_ORE,
                ItemID.GOLD_ORE, ItemID.COAL, ItemID.UNCUT_SAPPHIRE,
                ItemID.UNCUT_EMERALD, ItemID.UNCUT_RUBY, ItemID.UNCUT_DIAMOND,
                ItemID.UNCUT_DRAGONSTONE
        );
    }

    private void fixWaterwheel()
    {
        ensureLowerFloor();
        if (Rs2Walker.walkTo(new WorldPoint(3741, 5666, 0), 15))
        {
            Microbot.isGainingExp = false;
            if (Rs2GameObject.interact(ObjectID.BROKEN_STRUT))
            {
                sleepUntil(() -> Microbot.isGainingExp);
            }
        }
    }

    private void depositHopper()
    {
        // if using a gem bag, fill the gem bag and return to mining if the inventory is no longer full
        if (Rs2Inventory.isFull() && Rs2Gembag.hasGemBag())
        {
            Rs2Inventory.interact("gem bag", "Fill");
            gemBagEmptiedThisCycle = false;
            if (!Rs2Inventory.isFull())
            {
                return;
            }
        }

        WorldPoint hopperDeposit = (isUpperFloor() && config.upstairsHopperUnlocked()) ? HOPPER_DEPOSIT_UP : HOPPER_DEPOSIT_DOWN;
        Optional<GameObject> hopper = Optional.ofNullable(Rs2GameObject.findObject(ObjectID.HOPPER_26674, hopperDeposit));

        if(isUpperFloor() && !config.upstairsHopperUnlocked())
        {
            ensureLowerFloor();
        }
        if (hopper.isPresent() && Rs2GameObject.interact(hopper.get()))
        {
            sleepUntil(() -> !Rs2Inventory.isFull());
            if (Microbot.getVarbitValue(Varbits.SACK_NUMBER) > maxSackSize - 28)
            {
                shouldEmptySack = true;
            }
        }
        else
        {
            Rs2Walker.walkTo(hopperDeposit, 15);
        }
    }

    private void bankItems()
    {
        if (Rs2Bank.useBank())
        {
            sleepUntil(Rs2Bank::isOpen);

            // if using the gem sack, empty its contents directly into the bank
            if (Rs2Gembag.hasGemBag() && !gemBagEmptiedThisCycle)
            {
                Rs2Gembag.checkGemBag();
                if (Rs2Gembag.getTotalGemCount() > 0)
                {
                    Rs2Inventory.interact("gem bag", "Empty");
                    sleep(100, 300);
                }
                gemBagEmptiedThisCycle = true;
            }

            Rs2Bank.depositAllExcept("hammer", pickaxeName, "gem bag");
            sleep(100, 300);

            if (!Rs2Inventory.hasItem("hammer") && !Rs2Equipment.isWearing("hammer"))
            {
                if (!Rs2Bank.hasItem("hammer"))
                {
                    Microbot.showMessage("No hammer found in the bank.");
                    sleep(5000);
                    return;
                }
                Rs2Bank.withdrawOne("hammer", true);
            }

            if (config.pickAxeInInventory() && !Rs2Inventory.hasItem(pickaxeName))
            {
                Rs2Bank.withdrawOne(pickaxeName);
            }
            sleep(600);
        }
        status = MLMStatus.IDLE;
    }

    private void selectMiningSpotFromConfig() {
        MLMMiningSpot selected = MLMMiningSpot.valueOf(config.miningArea().name());

        if (selected == MLMMiningSpot.ANY) {
            if (config.mineUpstairs()) {
                miningSpot = Rs2Random.between(0, 1) == 0 ? MLMMiningSpot.WEST_UPPER : MLMMiningSpot.EAST_UPPER;
            }
            else {
                miningSpot = Rs2Random.between(0, 1) == 0 ? MLMMiningSpot.WEST_LOWER : MLMMiningSpot.WEST_MID;
                miningSpot = Rs2Random.between(0, 1) == 0 ? MLMMiningSpot.SOUTH_WEST : MLMMiningSpot.SOUTH_EAST;
            }
        } else {
            switch (selected) {
                case EAST_UPPER:
                case WEST_UPPER:
                case WEST_LOWER:
                case WEST_MID:
                case SOUTH_WEST:
                case SOUTH_EAST:
                    miningSpot = selected;
                    break;
                default:
                    Microbot.showMessage("Invalid mining area selected.");
                    shutdown();
                    return;
            }
        }

        // Shuffle order of veins within the selected area
        if (miningSpot.getWorldPoint() != null) {
            Collections.shuffle(miningSpot.getWorldPoint());
        }
    }

    private boolean walkToMiningSpot()
    {
        WorldPoint target = miningSpot.getWorldPoint().get(0);

        // Navigates to correct floor based on selected mining area
        if (miningSpot.isUpstairs() && !isUpperFloor())
        {
            goUp();
            return false; // Wait until we've gone up
        }

        if (miningSpot.isDownstairs() && isUpperFloor()) {
            goDown();
            return false; // Wait until we've gone down
        }

        // Walk to actual mining target tile
        return Rs2Walker.walkTo(target, 10);
    }

    private void attemptToMineVein() {
        WallObject vein = findClosestVein();
        if (vein == null) {
            repositionCameraAndMove();
            return;
        }
        // once a vein is found and ready to be interacted (mined), trigger the pickaxe special attack function
        handlePickaxeSpec();
        if (Rs2GameObject.interact(vein))
        {
            oreVein = vein;
            sleepUntil(Rs2Player::isAnimating, 5000);
            if (!Rs2Player.isAnimating())
            {
                oreVein = null;
            }
        }
    }

    private WallObject findClosestVein()
    {
        return Rs2GameObject.getWallObjects().stream()
                .filter(this::isValidVein)
                .min(Comparator.comparing(this::distanceToPlayer))
                .orElse(null);
    }

    private boolean isValidVein(WallObject wallObject)
    {
        int id = wallObject.getId();
        boolean isVein = (id == 26661 || id == 26662 || id == 26663 || id == 26664);
        if (!isVein) return false;

        WorldPoint location = wallObject.getWorldLocation();

        if (!config.mineUpstairs())
        {
            Stream<Rs2PlayerModel> players = Rs2Player.getPlayers(it -> it != null && it.getWorldLocation().distanceTo(wallObject.getWorldLocation()) <= 2);
            if (players.findAny().isPresent()) return false;
        }

        if (config.mineUpstairs())
        {
        boolean inUpperArea = (miningSpot == MLMMiningSpot.WEST_UPPER && WEST_UPPER_AREA.contains(location))
                || (miningSpot == MLMMiningSpot.EAST_UPPER && EAST_UPPER_AREA.contains(location));
            return inUpperArea && hasWalkableTilesAround(wallObject);
        }
        else
        {
        boolean inLowerArea = (miningSpot == MLMMiningSpot.WEST_LOWER && WEST_LOWER_AREA.contains(location))
                || (miningSpot == MLMMiningSpot.WEST_MID && WEST_LOWER_AREA.contains(location))
                || (miningSpot == MLMMiningSpot.SOUTH_WEST && SOUTH_LOWER_AREA.contains(location))
                || (miningSpot == MLMMiningSpot.SOUTH_EAST && SOUTH_LOWER_AREA.contains(location));
        return inLowerArea && hasWalkableTilesAround(wallObject);
        }
    }

    private boolean hasWalkableTilesAround(WallObject wallObject)
    {
        return Rs2Tile.areSurroundingTilesWalkable(wallObject.getWorldLocation(), 1, 1);
    }

    private int distanceToPlayer(WallObject wallObject)
    {
        WorldPoint playerLoc = Microbot.getClient().getLocalPlayer().getWorldLocation();
        WorldPoint walkableTile = Rs2Tile.getNearestWalkableTile(wallObject.getWorldLocation());
        if (walkableTile == null) return Integer.MAX_VALUE;
        return playerLoc.distanceTo2D(walkableTile);
    }

    private void repositionCameraAndMove()
    {
        Rs2Camera.resetPitch();
        Rs2Camera.resetZoom();
        Rs2Camera.turnTo(LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), miningSpot.getWorldPoint().get(0)));
        Rs2Walker.walkFastCanvas(miningSpot.getWorldPoint().get(0));
    }

    private void goUp()
    {
        if (isUpperFloor()) return;
        Rs2GameObject.interact(NullObjectID.NULL_19044);
        sleepUntil(this::isUpperFloor);
    }

    private void goDown()
    {
        if (!isUpperFloor()) return;
        Rs2GameObject.interact(NullObjectID.NULL_19045);
        sleepUntil(() -> !isUpperFloor());
    }

    private void ensureLowerFloor()
    {
        if (isUpperFloor()) goDown();
    }

    private boolean isUpperFloor()
    {
        int height = Perspective.getTileHeight(
                Microbot.getClient(),
                Microbot.getClient().getLocalPlayer().getLocalLocation(),
                0
        );
        return height < UPPER_FLOOR_HEIGHT;
    }

    private void resetMiningState()
    {
        oreVein = null;
        miningSpot = MLMMiningSpot.IDLE;
    }

    @Override
    public void shutdown()
    {
        Rs2Antiban.resetAntibanSettings();
        resetMiningState();
        Rs2Walker.setTarget(null);
        super.shutdown();
    }
}
