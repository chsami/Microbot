package net.runelite.client.plugins.microbot.mining.motherloadmine;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameObject;
import net.runelite.api.Perspective;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.WallObject;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMMiningSpot;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.MLMStatus;
import net.runelite.client.plugins.microbot.mining.motherloadmine.enums.Pickaxe;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.antiban.AntibanPlugin;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.ActivityIntensity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Gembag;

import java.util.Collections;
import java.util.Comparator;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
public class MotherloadMineScript extends Script
{
    public static final String VERSION = "1.8.0";

    private static final WorldArea WEST_UPPER_AREA = new WorldArea(3748, 5676, 7, 9, 0);
    private static final WorldArea EAST_UPPER_AREA = new WorldArea(3756, 5667, 8, 8, 0);
    // Static areas for lower floor to avoid getting stuck behind rockfall
    private static final WorldArea WEST_LOWER_AREA = new WorldArea(3729, 5653, 10, 22, 0);
    private static final WorldArea SOUTH_LOWER_AREA = new WorldArea(3740, 5640, 20, 20, 0);

    private static final WorldPoint HOPPER_DEPOSIT_DOWN = new WorldPoint(3748, 5672, 0);
    private static final WorldPoint HOPPER_DEPOSIT_UP = new WorldPoint(3755, 5677, 0);

	private static final WorldArea CRATE_AREA = new WorldArea(new WorldPoint(3750, 5659, 0), 10, 16);

	private static final WorldPoint[] CRATE_WALKPOINTS = new WorldPoint[]
	{
		new WorldPoint(3755, 5671, 0),
		new WorldPoint(3756, 5662, 0),
		new WorldPoint(3751, 5662, 0),
	};

    private static final int UPPER_FLOOR_HEIGHT = -490;
    private static final int SACK_LARGE_SIZE = 189;
    private static final int SACK_SIZE = 108;

    public static MLMStatus status = MLMStatus.IDLE;
    public static WallObject oreVein;
    public static MLMMiningSpot miningSpot = MLMMiningSpot.IDLE;
    private int maxSackSize;
	private Set<String> itemsToKeep;

	private final MotherloadMinePlugin plugin;
    private final MotherloadMineConfig config;

    private boolean shouldEmptySack = false;
	private boolean shouldRepairWaterwheel = false;
	private boolean pickedUpHammer = false;

	@Inject
	public MotherloadMineScript(MotherloadMinePlugin plugin, MotherloadMineConfig config)
	{
		this.plugin = plugin;
		this.config = config;
	}

    public boolean run()
    {
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
		shouldRepairWaterwheel = false;
    }

    private void executeTask()
    {
        if (!super.run() || !Microbot.isLoggedIn())
        {
            resetMiningState(true);
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
            setupInventory();
            return;
        }

        if (shouldRepairWaterwheel && getBrokenStrutCount() > 1) {
            status = MLMStatus.FIXING_WATERWHEEL;
            return;
        }

		if (Rs2Inventory.isFull() && Rs2Inventory.hasItem(ItemID.PAYDIRT))
		{
			resetMiningState();
			status = MLMStatus.DEPOSIT_HOPPER;
			return;
		}

		int sackCount = Microbot.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT);
        if (sackCount >= maxSackSize || (shouldEmptySack && !Rs2Inventory.contains(ItemID.PAYDIRT)))
        {
            resetMiningState();
            status = MLMStatus.EMPTY_SACK;
            return;
        }
        status = MLMStatus.MINING;
    }

    private boolean hasRequiredTools()
    {
		return Pickaxe.hasItem();
    }

    private void updateSackSize()
    {
        boolean sackUpgraded = Microbot.getVarbitValue(VarbitID.MOTHERLODE_BIGGERSACK) == 1;
        maxSackSize = sackUpgraded ? SACK_LARGE_SIZE : SACK_SIZE;
    }

	private void handleMining()
	{
		if (oreVein != null && AntibanPlugin.isMining()) return;

		if (Rs2Gembag.isUnknown()) {
			Rs2Gembag.checkGemBag();
		}

		shouldRepairWaterwheel = false;

		if (miningSpot == MLMMiningSpot.IDLE)
		{
			selectMiningSpotFromConfig();
		}

		if (!walkToMiningSpot()) return;

		if (attemptToMineVein())
		{
			Rs2Antiban.actionCooldown();
			Rs2Antiban.takeMicroBreakByChance();
		} else {
			oreVein = null;
		}
	}


	private void emptySack()
	{
		ensureLowerFloor();

		while (Microbot.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT) > 0 && isRunning())
		{
			if (hasOreInInventory())
			{
				useDepositBox();
			}
			else
			{
				Rs2GameObject.interact(ObjectID.MOTHERLODE_SACK);
				sleepUntil(this::hasOreInInventory);
			}
		}

		shouldEmptySack = false;
		shouldRepairWaterwheel = false;
		Rs2Antiban.takeMicroBreakByChance();
		status = MLMStatus.IDLE;
	}

    private boolean hasOreInInventory()
    {
        return Rs2Inventory.contains(
                ItemID.RUNITE_ORE, ItemID.ADAMANTITE_ORE, ItemID.MITHRIL_ORE,
                ItemID.GOLD_ORE, ItemID.COAL
        );
    }

    private void fixWaterwheel()
    {
        ensureLowerFloor();

		if (!hasHammer()) {
			if (!obtainHammer()) return;
		}

		if (Rs2GameObject.interact(ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN))
		{
			// We use a modified version of waitForXpDrop to ensure we break out of the sleep if the strut is repaired
			final int skillExp = Microbot.getClient().getSkillExperience(Skill.SMITHING);
			sleepUntilTrue(() -> skillExp != Microbot.getClient().getSkillExperience(Skill.SMITHING) || getBrokenStrutCount() <= 1, 100, 10_000);

			dropHammerIfNeeded();
			shouldRepairWaterwheel = false;
		}
    }

    private void depositHopper()
    {
        // if using a gem bag, fill the gem bag and return to mining if the inventory is no longer full
        if (Rs2Inventory.isFull() && (Rs2Gembag.hasGemBag() && !Rs2Gembag.isGemBagOpen()))
        {
			Rs2Inventory.interact("gem bag", "open");
			sleepUntil(Rs2Gembag::isGemBagOpen);
            Rs2Inventory.interact("gem bag", "fill");
            if (!Rs2Inventory.isFull())
            {
                return;
            }
        }

        WorldPoint hopperDeposit = (isUpperFloor() && config.upstairsHopperUnlocked()) ? HOPPER_DEPOSIT_UP : HOPPER_DEPOSIT_DOWN;
        Optional<GameObject> hopper = Optional.ofNullable(Rs2GameObject.getGameObject(ObjectID.MOTHERLODE_HOPPER, hopperDeposit));

        if(isUpperFloor() && !config.upstairsHopperUnlocked())
        {
            ensureLowerFloor();
        }

		final int paydirtToDeposit = Rs2Inventory.count(ItemID.PAYDIRT);

        if (hopper.isPresent() && Rs2GameObject.interact(hopper.get()))
        {
			sleepUntil(() -> !Rs2Inventory.isFull() && !Rs2Player.isAnimating(), 10_000);

			shouldRepairWaterwheel = true;

			// Calculate the effective sack size after deposit as VarbitID.MOTHERLODE_SACK_TRANSMIT takes time to update
			final int currentSackAmount = Microbot.getVarbitValue(VarbitID.MOTHERLODE_SACK_TRANSMIT);
			final int effectiveSackAmount = Math.max(currentSackAmount, Math.min(maxSackSize, currentSackAmount + paydirtToDeposit));

			shouldEmptySack = effectiveSackAmount >= (maxSackSize - 28);
        }
        else
        {
            Rs2Walker.walkTo(hopperDeposit, 15);
        }
    }

    private void useDepositBox()
    {
        if (Rs2DepositBox.openDepositBox())
        {
            sleepUntil(Rs2DepositBox::isOpen);

            // if using the gem sack, empty its contents directly into the bank
            if (Rs2Gembag.hasGemBag() && Rs2Gembag.getGemBagContents().stream().anyMatch(s -> s.getQuantity() > 30))
            {
				Rs2Bank.emptyGemBag();
				sleep(100, 300);
            }

			if (config.useDepositAll()) {
				Rs2DepositBox.depositAll();
			} else {
				String[] _itemsToKeep = getItemsToKeep().toArray(new String[0]);
				Rs2DepositBox.depositAllExcept(_itemsToKeep);
				Rs2Inventory.waitForInventoryChanges(5000);
			}

			Rectangle gameObjectBounds = getMotherloadSackBounds();
			Rectangle depositBoxBounds = Rs2DepositBox.getDepositBoxBounds();
			if (depositBoxBounds != null && (!Rs2UiHelper.isRectangleWithinViewport(gameObjectBounds) || depositBoxBounds.intersects(gameObjectBounds))) {
				Rs2DepositBox.closeDepositBox();
			}
        }
    }

	private void setupInventory() {
		if (!config.useInventorySetup()) {
			Rs2ItemModel pickaxe = Pickaxe.getBestPickaxe(false);
			if (pickaxe == null) {
				pickaxe = Pickaxe.getBestPickaxe(true);
				if (pickaxe == null) {
					Rs2Bank.openBank();
					sleepUntil(Rs2Bank::isOpen);
					pickaxe = Pickaxe.getBestBankedPickaxe(true);
					if (pickaxe == null) {
						Microbot.showMessage("No pickaxe found in bank or inventory. Please bank a pickaxe.");
						Microbot.stopPlugin(plugin);
						return;
					}

					if (Rs2Inventory.isFull()) {
						Rs2Bank.depositAll();
					}

					boolean hasAttackRequirements = Pickaxe.hasAttackLevelRequirement(pickaxe.getId());
					if (hasAttackRequirements) {
						final Rs2ItemModel currentWeaponSlot = Rs2Equipment.get(EquipmentInventorySlot.WEAPON);
						final Rs2ItemModel _pickaxe = pickaxe;
						Rs2Bank.withdrawAndEquip(_pickaxe.getId());
						sleepUntil(() -> Rs2Equipment.isWearing(_pickaxe.getId()));
						if (currentWeaponSlot != null) {
							Rs2Bank.depositOne(currentWeaponSlot.getId());
							Rs2Inventory.waitForInventoryChanges(5000);
						}
					} else {
						Rs2Bank.withdrawOne(pickaxe.getId());
						Rs2Inventory.waitForInventoryChanges(5000);
					}
					final int[] gemBagIDs = {ItemID.GEM_BAG, ItemID.GEM_BAG_OPEN};

					for (int gemBagID : gemBagIDs) {
						if (!isRunning()) break;

						if (Rs2Bank.withdrawOne(gemBagID)) {
							Rs2Inventory.waitForInventoryChanges(5000);
							break;
						}
					}

					if (Rs2Random.dicePercentage(10) && !hasHammer()) {
						if (Rs2Bank.withdrawOne("hammer")) {
							Rs2Inventory.waitForInventoryChanges(5000);
						}
					}

					Rs2Bank.toggleItemLock("hammer", false);
					Rs2Bank.toggleItemLock("gem bag", false);
				}
			}

		} else {
			Rs2InventorySetup mlmInventorySetup = new Rs2InventorySetup(config.getInventorySetup(), mainScheduledFuture);
			boolean doesEquipmentMatch = true;
			boolean doesInventoryMatch = true;

			if (!mlmInventorySetup.doesEquipmentMatch()) {
				doesEquipmentMatch = mlmInventorySetup.loadEquipment();
			}

			if (!mlmInventorySetup.doesInventoryMatch()) {
				doesInventoryMatch = mlmInventorySetup.loadInventory();
			}

			if (!doesEquipmentMatch || !doesInventoryMatch) {
				Microbot.showMessage("Failed to load inventory setup. Please check your settings.");
				Microbot.stopPlugin(plugin);
				return;
			}
		}

		Rs2Bank.closeBank();
		sleepUntil(() -> !Rs2Bank.isOpen());
	}

    private void selectMiningSpotFromConfig() {
        MLMMiningSpot selected = MLMMiningSpot.valueOf(config.miningArea().name());

        if (selected == MLMMiningSpot.ANY) {
            if (config.mineUpstairs()) {
                miningSpot = Rs2Random.between(0, 1) == 0 ? MLMMiningSpot.WEST_UPPER : MLMMiningSpot.EAST_UPPER;
            }
            else {
				MLMMiningSpot[] filteredSpots = Arrays.stream(MLMMiningSpot.values())
					.filter(s -> s.getWorldPoint() != null && s.isDownstairs())
					.toArray(MLMMiningSpot[]::new);

				int size = filteredSpots.length;
				if (size == 0) return;

				int randomIndex = Rs2Random.randomGaussian(size / 2.0, size / 6.0);
				randomIndex = Math.max(0, Math.min(size - 1, randomIndex));

				miningSpot = filteredSpots[randomIndex];
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
                    Microbot.stopPlugin(plugin);
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

	private boolean attemptToMineVein() {
		WallObject vein = findClosestVein();
		if (vein == null) {
			repositionCameraAndMove();
			return false;
		}

		handlePickaxeSpec();

		if (!Rs2GameObject.interact(vein)) return false;
		oreVein = vein;

		return sleepUntil(() -> {
			WallObject _vein = Rs2GameObject.getWallObject(o -> Objects.equals(o.getWorldLocation(), vein.getWorldLocation()));
			if (_vein == null || !isValidVein(_vein)) return false;
			return AntibanPlugin.isMining() && _vein.getWorldLocation().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation()) <= 2;
		}, 10_000);
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

		if (!config.mineUpstairs() && config.useAntiCrash())
		{
			boolean isPlayerNearBy = Rs2Player.getPlayers(p -> p != null && p.getWorldLocation().distanceTo(wallObject.getWorldLocation()) <= 2).findAny().isPresent();
			if (isPlayerNearBy) return false;
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
        Rs2GameObject.interact(ObjectID.MOTHERLODE_LADDER_BOTTOM);
        sleepUntil(this::isUpperFloor);
    }

    private void goDown()
    {
        if (!isUpperFloor()) return;
        Rs2GameObject.interact(ObjectID.MOTHERLODE_LADDER_TOP);
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

    private void resetMiningState(boolean force)
    {
        oreVein = null;
        miningSpot = (ThreadLocalRandom.current().nextBoolean() || force) ? MLMMiningSpot.IDLE : miningSpot;
    }

	private void resetMiningState()
	{
		resetMiningState(false);
	}

	private boolean hasHammer() {
		return Rs2Equipment.isWearing("hammer") || Rs2Inventory.hasItem("hammer");
	}

	private boolean obtainHammer() {
		/*

			Typically, the hammer is located near the hopper on the lower floor OR near the sack,
			so we should be close enough to directly interact with it.

			WorldPoint nearestCratePoint = Arrays.stream(CRATE_WALKPOINTS)
				.min(WorldPoint::distanceTo)
				.orElse(CRATE_WALKPOINTS[0]);
			if (!Rs2Walker.walkTo(nearestCratePoint)) return false;
		 */

		while (!Rs2Inventory.hasItem("hammer") && isRunning()) {
			List<GameObject> crates = Rs2GameObject.getGameObjects(o -> !plugin.getBlacklistedCrates().contains(o.getWorldLocation()) && o.getId() == ObjectID.CRATE2_OLD);
			Optional<GameObject> closestCrate = Rs2GameObject.pickClosest(crates, GameObject::getWorldLocation, Microbot.getClient().getLocalPlayer().getWorldLocation());

			if (closestCrate.isEmpty()) {
				log.error("Unable to find any crates to search for a hammer.");
				break;
			}

			GameObject crate = closestCrate.get();

			Rs2GameObject.interact(crate);
			Rs2Inventory.waitForInventoryChanges(5_000);
			if (!Rs2Inventory.hasItem("hammer")) {
				plugin.getBlacklistedCrates().add(crate.getWorldLocation());
				Rs2Inventory.drop("bronze pickaxe");
				Rs2Inventory.waitForInventoryChanges(5_000);
			} else {
				pickedUpHammer = true;
				break;
			}

			sleep(50, 100);
		}

		return pickedUpHammer;
	}

	private void dropHammerIfNeeded() {
		if (pickedUpHammer) {
			Rs2Inventory.drop("hammer");
			sleepUntil(() -> !Rs2Inventory.hasItem("hammer"));
			pickedUpHammer = false;
		}
	}

	private Rectangle getMotherloadSackBounds() {
		TileObject sack = Rs2GameObject.getAll(o -> o.getId() == ObjectID.MOTHERLODE_SACK).stream().findFirst().orElse(null);
		return Rs2UiHelper.getObjectClickbox(sack);
	}

	private int getBrokenStrutCount() {
		List<GameObject> brokenStruts = Rs2GameObject.getGameObjects(o -> o.getId() == ObjectID.MOTHERLODE_WHEEL_STRUT_BROKEN);
		return brokenStruts.isEmpty() ? 0 : brokenStruts.size();
	}

	private Set<String> getItemsToKeep() {
		if (itemsToKeep == null) {
			Set<String> _itemsToKeep = new HashSet<>();
			if (Rs2Inventory.hasItem("hammer")) {
				_itemsToKeep.add("hammer");
			}
			if (Rs2Inventory.hasItem("pickaxe")) {
				_itemsToKeep.add("pickaxe");
			}
			if (Rs2Gembag.hasGemBag()) {
				_itemsToKeep.add("gem bag");
			}
			itemsToKeep = _itemsToKeep;
		}
		return itemsToKeep;
	}

    @Override
    public void shutdown()
    {
        Rs2Antiban.resetAntibanSettings();
        Rs2Walker.setTarget(null);
		itemsToKeep = null;
        super.shutdown();
    }
}
