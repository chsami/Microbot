package net.runelite.client.plugins.microbot.mining.motherloadmine;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.*;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.WallObject;
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
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

@Slf4j
public class MotherloadMineScript extends Script {
  private static final Integer DWARF_MINE_REGION_ID = 12184;
  private static final WorldPoint DWARF_MINE_ENTRANCE = new WorldPoint(3061, 3377, 0);

  private static final WorldArea WEST_UPPER_AREA = new WorldArea(3748, 5676, 7, 9, 0);
  private static final WorldArea EAST_UPPER_AREA = new WorldArea(3756, 5667, 8, 8, 0);
  // Static areas for lower floor to avoid getting stuck behind rockfall
  private static final WorldArea WEST_LOWER_AREA = new WorldArea(3729, 5653, 10, 22, 0);
  private static final WorldArea SOUTH_LOWER_AREA = new WorldArea(3740, 5640, 20, 20, 0);

  private static final WorldPoint HOPPER_DEPOSIT_DOWN = new WorldPoint(3748, 5672, 0);
  private static final WorldPoint HOPPER_DEPOSIT_UP = new WorldPoint(3755, 5677, 0);

  private static final int SACK_ID = 26688;

  public MLMStatus status = MLMStatus.IDLE;
  public static WallObject oreVein;
  public static MLMMiningSpot miningSpot = MLMMiningSpot.IDLE;
  private MotherloadMineConfig config;

  private String pickaxeName = "";

  public void run(MotherloadMineConfig config) {
    this.config = config;
    Rs2Walker.disableTeleports = true;
    miningSpot = MLMMiningSpot.IDLE;
    status = MLMStatus.SETUP;

    try {
      if (!super.run() || !Microbot.isLoggedIn() || !isMemberWorld())
        return;
      if (Microbot.pauseAllScripts)
        return;
      if (Rs2AntibanSettings.actionCooldownActive)
        return;
      if (Rs2Player.isAnimating() || Rs2Player.isInteracting())
        return;

      init();

      mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(
          () -> {
            executeTask();
          },
          0,
          600,
          TimeUnit.MILLISECONDS);
    } catch (Exception ex) {
      System.err.println(ex.getMessage());
    }
  }

  private void executeTask() {
    if (!super.run() || !Microbot.isLoggedIn()) {
      resetMiningState();
      shutdown();
    }
    if (Microbot.pauseAllScripts)
      return;
    if (Rs2AntibanSettings.actionCooldownActive)
      return;
    if (Rs2Player.isAnimating() || Rs2Player.isInteracting())
      return;

    handleDragonPickaxeSpec();
    determineStatusFromInventory();
    if (hasOreInInventory()) {
      depositNonEssentials(true);
      Rs2Antiban.actionCooldown();
    }

    switch (status) {
      case IDLE:
        break;
      case SETUP:
        if (!hasRequiredTools() || !checkInMlm())
          init();
        break;
      case MINING:
        if (Rs2Antiban.isMining()) {
          Rs2Antiban.setActivityIntensity(ActivityIntensity.VERY_LOW);
          Rs2Antiban.moveMouseOffScreen();
          Rs2Antiban.takeMicroBreakByChance();
        } else {
          Rs2Antiban.setActivityIntensity(ActivityIntensity.HIGH);
          handleMining();
        }
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

  private void init() {
    Rs2Antiban.antibanSetupTemplates.applyGeneralBasicSetup();
    if (Rs2Bank.isOpen())
      Rs2Bank.closeBank();
    if (Rs2DepositBox.isOpen())
      Rs2DepositBox.closeDepositBox();

    // get our pickaxe and hammer as soon as possible
    if ((config.pickAxeInInventory() && !Rs2Inventory.hasItem("pickaxe", false))
        || (!config.pickAxeInInventory() && !Rs2Equipment.isWearing("pickaxe", false))) {
      if (Rs2Bank.walkToBankAndUseBank()) {
        Rs2Player.waitForWalking(18000);
        getPickaxe(!config.pickAxeInInventory());
        Rs2Antiban.actionCooldown();
        Rs2Antiban.takeMicroBreakByChance();
      }
    }

    // anywhere on the 0Z plane
    if (!Rs2Player.isInCave() && !checkInMlm()) {
      if (Rs2Player.getWorldLocation().distanceTo2D(DWARF_MINE_ENTRANCE) < 1000)
        Rs2Walker.disableTeleports = false;
      else
        Rs2Walker.disableTeleports = true;
      walkAndTrack(DWARF_MINE_ENTRANCE);
      Rs2Player.waitForWalking(18000);
      sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo2D(DWARF_MINE_ENTRANCE) <= 15);
      Rs2GameObject.interact("Staircase");
      sleepUntil(() -> Rs2Player.isInCave());
    }

    // in dwarf mine
    if (Rs2Player.getWorldLocation().getRegionID() == DWARF_MINE_REGION_ID) {
      if ((config.pickAxeInInventory() && !Rs2Inventory.hasItem("pickaxe", false))
          || (!config.pickAxeInInventory() && !Rs2Equipment.isWearing("pickaxe", false))) {
        Rs2GameObject.interact("Staircase");
        sleepUntil(() -> !Rs2Player.isInCave());
        walkAndTrack(BankLocation.FALADOR_EAST.getWorldPoint());
        getPickaxe(!config.pickAxeInInventory());
        Rs2Walker.walkTo(DWARF_MINE_ENTRANCE);
        Rs2Player.waitForWalking(5000);
        Rs2GameObject.interact("Staircase");
        sleepUntil(() -> Rs2Player.isInCave());
      }
      Rs2GameObject.interact("Cave");
      sleepUntil(() -> Rs2Player.getWorldLocation().getRegionID() == 14936);
    }

    // in mlm
    if (checkInMlm()) {
      walkAndTrack(BankLocation.MOTHERLOAD.getWorldPoint());
      Rs2Player.waitForWalking(18000);
      ensureLowerFloor();
      if (!hasRequiredTools() || hasGemInInventory())
        bankItems();
      if (Rs2Inventory.hasItem(ItemID.PAYDIRT))
        depositHopper();
    }

    status = MLMStatus.IDLE;
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
      Microbot.log("Best current pickaxe: " + bestPick.getItemName());
    pickaxeName = bestPick.getItemName();
    if (!Rs2Inventory.hasItem(bestPick.getItemId())) {
      Rs2Bank.depositAllExcept("hammer", "pay-dirt");
      if (equip)
        Rs2Bank.withdrawAndEquip(bestPick.getItemId());
      else
        Rs2Bank.withdrawOne(bestPick.getItemId());
      Rs2Inventory.waitForInventoryChanges(600);
    }
    Rs2Bank.closeBank();
    Rs2Antiban.actionCooldown();
  }

  private void handleDragonPickaxeSpec() {
    if (Rs2Equipment.isWearing("dragon pickaxe"))
      Rs2Combat.setSpecState(true, 1000);
  }

  private void determineStatusFromInventory() {
    if (!hasRequiredTools()) {
      withdrawEssentials();
      return;
    }

    if (Rs2Inventory.isFull())
      depositNonEssentials(true);
    int sackCount = Microbot.getVarbitValue(Varbits.SACK_NUMBER);
    if (sackCount + Rs2Inventory.count(ItemID.PAYDIRT) >= MotherloadMinePlugin.getMaxSackSize()
        || (MotherloadMinePlugin.getShouldEmptySack() && !Rs2Inventory.hasItem(ItemID.PAYDIRT))) {
      resetMiningState();
      status = MLMStatus.EMPTY_SACK;
    } else if (!Rs2Inventory.isFull())
      status = MLMStatus.MINING;
    else {
      resetMiningState();
      if (Rs2Inventory.hasItem(ItemID.PAYDIRT)) {
        if (Rs2GameObject.getGameObjects(ObjectID.BROKEN_STRUT).size() == 2
            && Rs2Inventory.hasItem("hammer"))
          status = MLMStatus.FIXING_WATERWHEEL;
        else
          status = MLMStatus.DEPOSIT_HOPPER;
      } else
        status = MLMStatus.BANKING;
    }
  }

  private boolean hasRequiredTools() {
    boolean hasHammer = Rs2Inventory.hasItem("hammer");
    boolean hasPickaxe = config.pickAxeInInventory()
        ? Rs2Inventory.hasItem("pickaxe", false)
        : Rs2Equipment.isWearing("pickaxe", false);
    return hasHammer && hasPickaxe;
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
    Microbot.status = "Emptying Sack";
    ensureLowerFloor();
    while (Microbot.getVarbitValue(Varbits.SACK_NUMBER) > 0) {
      Rs2GameObject.interact(SACK_ID);
      sleepUntil(this::hasOreInInventory);
      if (hasOreInInventory() || hasGemInInventory())
        depositNonEssentials(true);
    }
    Rs2Antiban.takeMicroBreakByChance();
    status = MLMStatus.IDLE;
  }

  private boolean hasGemInInventory() {
    Microbot.status = "Checking for gems in bag";
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

  private boolean hasOreInInventory() {
    return Rs2Inventory.hasItem(MLM_ORE_TYPES.stream().mapToInt(i -> i).toArray());
  }

  private void fixWaterwheel() {
    ensureLowerFloor();
    walkAndTrack(new WorldPoint(3741, 5666, 0));
    if (Rs2GameObject.getGameObjects(ObjectID.BROKEN_STRUT).size() > 1) {
      if (Rs2GameObject.interact(ObjectID.BROKEN_STRUT)) {
        Rs2Player.waitForXpDrop(Skill.SMITHING);
      }
    }
  }

  private void depositHopper() {
    GameObject hopper = Rs2GameObject.getGameObject(HOPPER_DEPOSIT_DOWN);
    if (config.upstairsHopperUnlocked())
      if (isUpperFloor())
        hopper = Rs2GameObject.getGameObject(HOPPER_DEPOSIT_UP);
    int sackDelta = Rs2Inventory.count(ItemID.PAYDIRT);
    if (hopper == null)
      hopper = Rs2GameObject.get("hopper", false);
    walkAndTrack(hopper.getWorldLocation());
    Rs2Player.waitForWalking(5000);
    if (Rs2GameObject.interact(hopper)) {
      sleepUntil(() -> !Rs2Inventory.contains(ItemID.PAYDIRT), 5000);
      if (hasGemInInventory() || hasOreInInventory())
        depositNonEssentials(true);
    }
    Rs2Antiban.actionCooldown();
  }

  private void depositNonEssentials(boolean close) {
    if (!Rs2Bank.isOpen()) {
      if (isUpperFloor()) {
        ensureLowerFloor();
      }
      LocalPoint locationPoint = LocalPoint.fromWorld(
          Microbot.getClient().getTopLevelWorldView(), BankLocation.MOTHERLOAD.getWorldPoint());
      Rs2Camera.turnTo(locationPoint);
      Microbot.getClient()
          .setCameraYawTarget(Rs2Camera.calculateCameraYaw(Rs2Camera.angleToTile(locationPoint)));
      if (Rs2Bank.useBank())
        sleepUntil(Rs2Bank::isOpen);
    }
    if (pickaxeName == "")
      pickaxeName = "pickaxe";
    Rs2Bank.depositAllExcept("hammer", pickaxeName, "pay-dirt");
    Rs2Inventory.waitForInventoryChanges(500);
    if (close) {
      sleepUntil(() -> Rs2Bank.closeBank(), 300);
      if (Rs2Inventory.hasItem(ItemID.PAYDIRT))
        depositHopper();
    }
    Rs2Antiban.actionCooldown();
  }

  private void withdrawEssentials() {
    if (!Rs2Bank.isOpen()) {
      if (isUpperFloor()) {
        ensureLowerFloor();
      }
      BankLocation targetBank = checkInMlm() ? BankLocation.MOTHERLOAD : BankLocation.FALADOR_EAST;
      LocalPoint locationPoint = LocalPoint.fromWorld(
          Microbot.getClient().getTopLevelWorldView(), targetBank.getWorldPoint());
      Rs2Camera.turnTo(locationPoint);
      if (locationPoint != null) {
        Microbot.getClient()
            .setCameraYawTarget(Rs2Camera.calculateCameraYaw(Rs2Camera.angleToTile(locationPoint)));
      }
      if (Rs2Bank.useBank())
        sleepUntil(Rs2Bank::isOpen);
    }
    if (!Rs2Inventory.hasItem("hammer")) {
      if (!Rs2Bank.hasBankItem("hammer")) {
        Microbot.log("No hammer found in the bank.");
        shutdown();
      }
      Rs2Bank.withdrawOne("hammer", true);
    }
    getPickaxe(!config.pickAxeInInventory());
  }

  private void bankItems() {
    depositNonEssentials(false);
    withdrawEssentials();
    status = MLMStatus.IDLE;
  }

  private boolean walkAndTrack(WorldPoint location) {
    LocalPoint locationPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), location);
    if (locationPoint != null) {
      Rs2Camera.turnTo(locationPoint);
      Microbot.getClient()
          .setCameraYawTarget(Rs2Camera.calculateCameraYaw(Rs2Camera.angleToTile(locationPoint)));
    }
    return Rs2Walker.walkTo(location);
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
    Microbot.getClient()
        .setCameraYawTarget(
            Rs2Camera.calculateCameraYaw(Rs2Camera.angleToTile(vein.getLocalLocation())));
    Rs2Camera.turnTo(vein.getLocalLocation());
    if (Rs2GameObject.interact(vein)) {
      oreVein = vein;
      sleepUntil(Rs2Player::isAnimating, 5000);
      if (!Rs2Player.isAnimating())
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
          || (miningSpot == MLMMiningSpot.SOUTH
              && SOUTH_LOWER_AREA.contains(wallObject.getWorldLocation()));
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
    return Perspective.getTileHeight(Microbot.getClient(), Rs2Player.getLocalLocation(), 0) < UPPER_FLOOR_HEIGHT;
  }

  private MLMPickaxes getBestPickaxe(List<Integer> items) {
    MLMPickaxes bestPickaxe = null;
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

  private boolean isMemberWorld() {
    return Microbot.getClient().getWorldType().contains(WorldType.MEMBERS);
  }

  private void resetMiningState() {
    oreVein = null;
    miningSpot = MLMMiningSpot.IDLE;
  }

  public boolean checkInMlm() {
    int currentMapRegionID = Rs2Player.getWorldLocation().getRegionID();
    return MLM_REGIONS.contains(currentMapRegionID);
  }

  @Override
  public void shutdown() {
    status = MLMStatus.IDLE;
    oreVein = null;
    miningSpot = MLMMiningSpot.IDLE;
    pickaxeName = "";
    resetMiningState();
    super.shutdown();
  }
}
