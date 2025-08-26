package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.LootingParameters;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.woodcutting.Rs2Woodcutting;
import net.runelite.client.plugins.microbot.woodcutting.enums.*;

import javax.inject.Inject;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static net.runelite.api.gameval.AnimationID.*;
import static net.runelite.api.gameval.ItemID.TINDERBOX;


public class AutoWoodcuttingScript extends Script {

    public static final List<Integer> BURNING_ANIMATION_IDS = List.of(
            FORESTRY_CAMPFIRE_BURNING_LOGS,
            FORESTRY_CAMPFIRE_BURNING_MAGIC_LOGS,
            FORESTRY_CAMPFIRE_BURNING_MAHOGANY_LOGS,
            FORESTRY_CAMPFIRE_BURNING_MAPLE_LOGS,
            FORESTRY_CAMPFIRE_BURNING_OAK_LOGS,
            FORESTRY_CAMPFIRE_BURNING_REDWOOD_LOGS,
            FORESTRY_CAMPFIRE_BURNING_TEAK_LOGS,
            FORESTRY_CAMPFIRE_BURNING_WILLOW_LOGS,
            FORESTRY_CAMPFIRE_BURNING_YEW_LOGS,
            HUMAN_CREATEFIRE
    );

    public static final int FORESTRY_DISTANCE = 15;
    public static String version = "1.7.0";
    private static WorldPoint returnPoint;
    public volatile boolean cannotLightFire = false;
    WoodcuttingScriptState woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
    private boolean hasAutoHopMessageShown = false;
    private final AutoWoodcuttingPlugin plugin;

    @Inject
    public AutoWoodcuttingScript(AutoWoodcuttingPlugin plugin) {
        this.plugin = plugin;
    }

    private static void handleFiremaking(AutoWoodcuttingConfig config) {
        if (!Rs2Inventory.hasItem(TINDERBOX)) {
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 20000);
            Rs2Bank.withdrawItem(true, "Tinderbox");
        }

        if (!Rs2Inventory.hasItem(config.TREE().getLog())) {
            Microbot.log("Opening bank");
            Rs2Bank.openBank();
            sleepUntil(Rs2Bank::isOpen, 20000);
            Rs2Bank.withdrawAll(config.TREE().getLog());
            Rs2Bank.closeBank();
            sleep(500, 1200);
        }
    }

    public static WorldPoint getReturnPoint(AutoWoodcuttingConfig config) {
        if (config.walkBack().equals(WoodcuttingWalkBack.LAST_LOCATION)) {
            return returnPoint == null ? Rs2Player.getWorldLocation() : returnPoint;
        } else {
            return initialPlayerLocation == null ? Rs2Player.getWorldLocation() : initialPlayerLocation;
        }
    }

    public boolean run(AutoWoodcuttingConfig config) {
        Rs2Antiban.resetAntibanSettings();
        Rs2Antiban.antibanSetupTemplates.applyWoodcuttingSetup();
        Rs2AntibanSettings.dynamicActivity = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        initialPlayerLocation = null;
        if (config.firemakeOnly()) {
            woodcuttingScriptState = WoodcuttingScriptState.FIREMAKING;
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (preFlightChecks(config)) return;
                switch (woodcuttingScriptState) {
                    case WOODCUTTING:
                        if (beforeCuttingTreesChecks(config)) return;
                        handleWoodcutting(config);
                        break;
                    case FIREMAKING:
                        handleFiremaking(config);
                        walkBack(config);
                        woodcuttingScriptState = WoodcuttingScriptState.RESETTING;
                        break;
                    case RESETTING:
                        resetInventory(config);
                }
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
                ex.printStackTrace();
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleWoodcutting(AutoWoodcuttingConfig config) {
        GameObject tree = null;
        if (config.HardwoodTreePatch()) {
            var patchIds = List.of(30480, 30481, 30482);
            var trees = Rs2GameObject.getGameObjects(x -> patchIds.contains(x.getId()) && Rs2GameObject.hasAction(Rs2GameObject.convertToObjectComposition(x), config.TREE().getAction()));
            tree = trees.stream().findFirst().orElse(null);
        } else {
            tree = Rs2GameObject.findReachableObject(config.TREE().getName(), true, config.distanceToStray(), getInitialPlayerLocation(), config.TREE().equals(WoodcuttingTree.REDWOOD), config.TREE().getAction());
        }

        if (tree != null) {
            if (Rs2GameObject.interact(tree, config.TREE().getAction())) {
                Rs2Player.waitForAnimation();
                Rs2Antiban.actionCooldown();

                if (config.walkBack().equals(WoodcuttingWalkBack.LAST_LOCATION)) {
                    returnPoint = Rs2Player.getWorldLocation();
                }
            }
        }
    }

    private boolean beforeCuttingTreesChecks(AutoWoodcuttingConfig config) {
        if (config.hopWhenPlayerDetected()) {
            if (Rs2Player.logoutIfPlayerDetected(1, 10000))
                return true;
        }

        if (Rs2Woodcutting.isWearingAxeWithSpecialAttack())
            Rs2Combat.setSpecState(true, 1000);

        if (Rs2Inventory.isFull()) {
            woodcuttingScriptState = WoodcuttingScriptState.RESETTING;
            return true;
        }

        if (handleLooting(config)) {
            Rs2Antiban.actionCooldown();
            return true;
        }

        return false;
    }

    private boolean preFlightChecks(AutoWoodcuttingConfig config) {
        if (!Microbot.isLoggedIn()) return true;
        if (!super.run()) return true;
        if (Rs2AntibanSettings.actionCooldownActive) return true;

        if (!hasAutoHopMessageShown && config.hopWhenPlayerDetected()) {
            Microbot.showMessage("Make sure autologin plugin is enabled and randomWorld checkbox is checked!");
            hasAutoHopMessageShown = true;
        }

        if (config.hopWhenPlayerDetected() && config.enableForestry()) {
            Microbot.showMessage("Autohop is not supported with forestry enabled, shutting down.");
            shutdown();
            return true;
        }

        if (initialPlayerLocation == null) {
            initialPlayerLocation = Rs2Player.getWorldLocation();
        }

        if (returnPoint == null) {
            returnPoint = Rs2Player.getWorldLocation();
        }

        if (!config.TREE().hasRequiredLevel()) {
            Microbot.showMessage("You do not have the required woodcutting level to cut this tree.");
            shutdown();
            return true;
        }

        if (!Rs2Inventory.hasItem("axe")) {
            if (!Rs2Equipment.isWearing("axe")) {
                Microbot.showMessage("Unable to find axe in inventory/equipped");
                shutdown();
                return true;
            }
        }

        if (woodcuttingScriptState != WoodcuttingScriptState.RESETTING &&
                (Rs2Player.isMoving() || (Rs2Player.isAnimating() && !BURNING_ANIMATION_IDS.contains(Rs2Player.getLastAnimationID())))) {
            return true;
        }

        if (this.plugin.currentForestryEvent != ForestryEvents.NONE) {
            this.plugin.currentForestryEvent = ForestryEvents.NONE;
        }

        return Rs2AntibanSettings.actionCooldownActive;
    }

    private void resetInventory(AutoWoodcuttingConfig config) {
        switch (config.resetOptions()) {
            case DROP:
                var itemNames = Arrays.stream(config.itemsToKeep().split(",")).map(String::trim).toArray(String[]::new);
                Rs2Inventory.dropAllExcept(false, config.interactOrder(), itemNames);
                woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
                break;
            case BANK:
                if (!handleBanking(config))
                    return;
                woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
                break;
            case CAMPFIRE_FIREMAKE:
            case FIREMAKE:
                burnLog(config);

                if (Rs2Inventory.contains(config.TREE().getLog())) return;

                walkBack(config);

                if (config.firemakeOnly()){
                    woodcuttingScriptState = WoodcuttingScriptState.FIREMAKING;
                } else {
                    woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
                }
                break;
            case FLETCH_ARROWSHAFT:
                fletchArrowShaft(config);

                walkBack(config);
                woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
                break;
        }
    }

    private boolean handleBanking(AutoWoodcuttingConfig config)
    {
        BankLocation nearestBank = Rs2Bank.getNearestBank();
        boolean isBankOpen = Rs2Bank.isNearBank(nearestBank, 8) ? Rs2Bank.openBank() : Rs2Bank.walkToBankAndUseBank(nearestBank);
        if (!isBankOpen || !Rs2Bank.isOpen()) return false;
        List<String> itemNames = Arrays.stream(config.itemsToBank().split(",")).map(String::toLowerCase).collect(Collectors.toList());
        Rs2Bank.depositAll(i -> itemNames.stream().anyMatch(itemName -> i.getName().toLowerCase().contains(itemName)));
        Rs2Inventory.waitForInventoryChanges(1800);
        Rs2Bank.emptyLogBasket();

        Rs2Bank.closeBank();
        sleepUntil(() -> !Rs2Bank.isOpen());

        Rs2Walker.walkTo(getReturnPoint(config));
        return true;
    }

    private boolean handleLooting(AutoWoodcuttingConfig config)
    {
        if (!config.lootBirdNests() && !config.lootSeeds()) {
            return false; // No looting options selected
        }

        List<String> itemsToLootList = new ArrayList<>();

            if (config.lootSeeds()) {
                itemsToLootList.add("seed");
            }
            if (config.lootBirdNests()) {
                itemsToLootList.add("nest");
            }

            String[] itemsToLoot = itemsToLootList.toArray(new String[0]);

        LootingParameters itemLootParams = new LootingParameters(
                15,
                1,
                1,
                1,
                false,
                config.lootMyItemsOnly(),
                itemsToLoot
        );
        return Rs2GroundItem.lootItemsBasedOnNames(itemLootParams);
    }

    private void burnLog(AutoWoodcuttingConfig config) {
        WorldPoint fireSpot;
        boolean useCampfire = false;

        // prioritize campfire if available
        GameObject fire = Rs2GameObject.getGameObject(49927, 6); // Forester's campfire
        if (fire == null) {
            fire = Rs2GameObject.getGameObject(26185, 6); // Regular fire
        }
        if (config.resetOptions() == WoodcuttingResetOptions.CAMPFIRE_FIREMAKE) {

            if (fire != null) {
                useCampfire = true;
            }
        }
        if ((Rs2Player.isStandingOnGameObject() || cannotLightFire) && !Rs2Player.isAnimating() && !useCampfire) {
            fireSpot = fireSpot(1);
            Rs2Walker.walkFastCanvas(fireSpot);
            cannotLightFire = false;
        }
        if (!isFiremake() && !useCampfire) {
            Rs2Inventory.waitForInventoryChanges(() -> {
                Rs2Inventory.use("tinderbox");
                sleepUntil(Rs2Inventory::isItemSelected);
                Rs2Inventory.useLast(config.TREE().getLogID());
            }, 300, 100);
        } else if (!isFiremake() && useCampfire) {
            Rs2Inventory.useItemOnObject(config.TREE().getLogID(), fire.getId());
            sleepUntil(() -> (!Rs2Player.isMoving() && Rs2Widget.findWidget("How many would you like to burn?", null, false) != null), 5000);
            Rs2Random.waitEx(400, 200);
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            sleepUntil(() -> !Rs2Inventory.contains(config.TREE().getLog()) || !Rs2Player.isAnimating(), 40000);

            return;
        }
        sleepUntil(() -> !isFiremake());
        if (!isFiremake()) {
            sleepUntil(() -> cannotLightFire, 1500);
        }
        if (!cannotLightFire && isFiremake()) {
            sleepUntil(() -> Rs2Player.waitForXpDrop(Skill.FIREMAKING, 40000), 40000);
        }
    }

    private WorldPoint fireSpot(int distance) {
        List<WorldPoint> worldPoints = Rs2Tile.getWalkableTilesAroundPlayer(distance);
        WorldPoint playerLocation = Rs2Player.getWorldLocation();

        // Create a map to group tiles by their distance from the player
        Map<Integer, WorldPoint> distanceMap = new HashMap<>();

        for (WorldPoint walkablePoint : worldPoints) {
            if (Rs2GameObject.getGameObject(o -> o.getWorldLocation().equals(walkablePoint), distance) == null) {
                int tileDistance = playerLocation.distanceTo(walkablePoint);
                distanceMap.putIfAbsent(tileDistance, walkablePoint);
            }
        }

        // Find the minimum distance that has walkable points
        Optional<Integer> minDistanceOpt = distanceMap.keySet().stream().min(Integer::compare);

        if (minDistanceOpt.isPresent()) {
            return distanceMap.get(minDistanceOpt.get());
        }

        // Recursively increase the distance if no valid point is found
        return fireSpot(distance + 1);
    }

    private boolean isFiremake() {
        if (cannotLightFire) return false;
        return Rs2Player.isAnimating(1800) && BURNING_ANIMATION_IDS.contains(Rs2Player.getLastAnimationID());
    }

    private void fletchArrowShaft(AutoWoodcuttingConfig config) {
        Rs2Inventory.combineClosest("knife", config.TREE().getLog());
        sleepUntil(Rs2Widget::isProductionWidgetOpen, 5000);
        Rs2Widget.clickWidget("arrow shafts");
        Rs2Player.waitForAnimation();
        sleepUntil(() -> !isFlectching(), 5000);
    }

    private boolean isFlectching() {
        return Rs2Player.isAnimating(3000) && Rs2Player.getLastAnimationID() == AnimationID.FLETCHING_BOW_CUTTING;
    }

    private void walkBack(AutoWoodcuttingConfig config) {
        Rs2Walker.walkTo(new WorldPoint(getReturnPoint(config).getX() - Rs2Random.between(-1, 1), getReturnPoint(config).getY() - Rs2Random.between(-1, 1), getReturnPoint(config).getPlane()));
        sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(getReturnPoint(config)) <= 4);
    }

    @Override
    public void shutdown() {
        super.shutdown();
        returnPoint = null;
        initialPlayerLocation = null;
        hasAutoHopMessageShown = false;
        Rs2Antiban.resetAntibanSettings();
    }
}
