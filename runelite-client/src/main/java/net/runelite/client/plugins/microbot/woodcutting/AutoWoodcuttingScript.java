package net.runelite.client.plugins.microbot.woodcutting;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.woodcutting.Rs2Woodcutting;
import net.runelite.client.plugins.microbot.woodcutting.Forestry.PruningAction;
import net.runelite.client.plugins.microbot.woodcutting.enums.*;

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
    // Forestry event variables
    public final List<NPC> ritualCircles = new ArrayList<>();
    public ForestryEvents currentForestryEvent = ForestryEvents.NONE;
    public final GameObject[] saplingOrder = new GameObject[3];
    public boolean debugForestry = true;

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
                        // Only handle woodcutting normally if the forestry event logic permits you
                        if (!handleForestryEvents(config)) {
                            handleWoodcutting(config);
                        }
                        break;
                    case FIREMAKING:
                        handleFiremaking(config);
                        walkBack(config);
                        woodcuttingScriptState = WoodcuttingScriptState.RESETTING;
                        break;
                    case RESETTING:
                        if (!handleForestryEvents(config)) {
                            resetInventory(config);
                            break;
                        }
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

    private boolean handleForestryEvents(AutoWoodcuttingConfig config) {
        if (!config.enableForestry()) return false;

        if (CheckForStrugglingSapling()) {
            if (debugForestry) {
                Microbot.log("Struggling sapling found");
            }
            handleStrugglingSapling();
        }

        if (checkForTreeRoot()) {
            if (debugForestry) {
                Microbot.log("Infused tree root found");
            }
            handleTreeRoot();
            return true;
        }

        if (checkForFoxTrap()) {
            if (debugForestry) {
                Microbot.log("Fox trap found");
            }
            handleFoxTrap();
            return true;
        }

        if (checkForRainbow()) {
            if (debugForestry) {
                Microbot.log("Rainbow found");
            }
            handleRainbow();
            return true;
        }

        if (checkForBeeHive() && Rs2Inventory.contains(config.TREE().getLogID())) {
            if (debugForestry) {
                Microbot.log("Bee hive found");
            }
            handleBeeHive();
            return true;
        }

        if (checkForPheasant()) {
            if (debugForestry) {
                Microbot.log("Pheasant nest found");
            }
            handlePheasant();
            return true;
        }

        if (checkForRitualCircles()) {
            if (debugForestry) {
                Microbot.log("Ritual circles found");
            }
            handleRitualCircles();
            return true;
        }

        PruningAction pruningAction = findEntlingToPrune();
        if (pruningAction != null) {
            if (debugForestry) {
                Microbot.log("Entling found: " + pruningAction.getEntling().getName() + " with action: " + pruningAction.getAction());
            }
            handleEntling(pruningAction);
            return true;
        }

        if (checkForFoxPoacher()) {
            if (debugForestry) {
                Microbot.log("Fox poacher found");
            }
            handleFoxTrap();
            return true;
        }
        // If no forestry events are active, reset the current event state
        if (currentForestryEvent != ForestryEvents.NONE) {
            currentForestryEvent = ForestryEvents.NONE;
        }
        return false;
    }

    private void handleStrugglingSapling() {
        currentForestryEvent = ForestryEvents.STRUGGLING_SAPLING;
    }

    private boolean CheckForStrugglingSapling() {
        var strugglingSaplings = Rs2GameObject.getGameObjects(Rs2GameObject.nameMatches("Struggling sapling", false));
        if (strugglingSaplings == null) return false;
        if (strugglingSaplings.isEmpty()) return false;
        return strugglingSaplings.stream().anyMatch(obj ->
                Rs2GameObject.hasAction(Rs2GameObject.convertToObjectComposition(obj), "Add-mulch") &&
                        obj.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= FORESTRY_DISTANCE
        );
    }

    // Event detection methods
    private boolean checkForTreeRoot() {
        var roots = Rs2GameObject.getGameObjects(Rs2GameObject.nameMatches("infused tree root", false));
        if (roots == null) return false;
        if (roots.isEmpty()) return false;
        return roots.stream().anyMatch(obj ->
                Rs2GameObject.hasAction(Rs2GameObject.convertToObjectComposition(obj), "Chop down") &&
                        obj.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= FORESTRY_DISTANCE
        );
    }

    private boolean checkForFoxTrap() {
        var foxTraps = Rs2Npc.getNpcs("fox trap");
        if (foxTraps == null) return false;
        return foxTraps.findAny().isPresent();
    }

    private boolean checkForRainbow() {
        var rainbows = Rs2GameObject.getGameObjects(Rs2GameObject.nameMatches("ainbow", false));
        if (rainbows == null) return false;
        if (rainbows.isEmpty()) return false;
        return rainbows.stream().anyMatch(obj ->
                obj.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= FORESTRY_DISTANCE
        );
    }

    private boolean checkForBeeHive() {
        var beehives = Rs2Npc.getNpcs(x -> x.getId() == net.runelite.api.gameval.NpcID.GATHERING_EVENT_BEES_BEEBOX_1 || x.getId() == net.runelite.api.gameval.NpcID.GATHERING_EVENT_BEES_BEEBOX_2);
        return beehives.findAny().isPresent();
    }

    private boolean checkForSturdyBeeHive() {
        var sturdyBeehives = Rs2GameObject.getGameObjects(Rs2GameObject.nameMatches("sturdy beehive", false));
        if (sturdyBeehives == null) return false;
        return !sturdyBeehives.isEmpty();
    }

    private boolean checkForPheasant() {
        var pheasantNests = Rs2GameObject.getGameObjects(Rs2GameObject.nameMatches("pheasant nest", false));
        if (pheasantNests == null) return false;
        if (pheasantNests.isEmpty()) return false;
        return pheasantNests.stream().anyMatch(obj ->
                Rs2GameObject.hasAction(Rs2GameObject.convertToObjectComposition(obj), "Retrieve-egg") &&
                        obj.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= FORESTRY_DISTANCE
        );
    }

    private boolean checkForRitualCircles() {
        return !ritualCircles.isEmpty();
    }

    private boolean checkForFoxPoacher() {
        var poachers = Rs2Npc.getNpcs("poacher");
        if (poachers == null) return false;
        return poachers.findAny().isPresent();
    }

    // Event handling methods
    private void handleTreeRoot() {
        currentForestryEvent = ForestryEvents.TREE_ROOT;
        breakPlayersAnimation();

        // Use special attack if available
        if (Rs2Woodcutting.isWearingAxeWithSpecialAttack())
            Rs2Combat.setSpecState(true, 1000);

        var roots = Rs2GameObject.getGameObjects(Rs2GameObject.nameMatches("infused tree root", false)).stream().filter(
                x -> x != null &&
                        Rs2GameObject.hasAction(Rs2GameObject.convertToObjectComposition(x), "Chop down") &&
                        x.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= FORESTRY_DISTANCE
        ).collect(Collectors.toList());

        if (roots != null && !roots.isEmpty()) {
            Rs2GameObject.interact(roots.get(0), "Chop down");
            Rs2Player.waitForAnimation();
        }
    }

    private void handleFoxTrap() {
        currentForestryEvent = ForestryEvents.FOX_TRAP;
        breakPlayersAnimation();

        var foxTraps = Rs2Npc.getNpcs(npc ->
                npc.getName() != null &&
                        npc.getName().toLowerCase().contains("fox trap") &&
                        Rs2Npc.hasAction(npc.getId(), "Disarm")
        );

        var foxtrapOpt = foxTraps.findFirst();
        if (foxtrapOpt.isPresent()) {
            var foxTrap = foxtrapOpt.get();
            Rs2Npc.interact(foxTrap, "Disarm");
            Rs2Player.waitForAnimation();
        } else if (!checkForFoxPoacher()) {
            currentForestryEvent = ForestryEvents.NONE;
        }
    }

    private void handleRainbow() {
        currentForestryEvent = ForestryEvents.RAINBOW;
        breakPlayersAnimation();

        var rainbows = Rs2GameObject.getGameObjects(Rs2GameObject.nameMatches("ainbow", false)).stream().filter(
                x -> x != null &&
                        x.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= FORESTRY_DISTANCE
        ).collect(Collectors.toList());

        if (rainbows != null && !rainbows.isEmpty()) {
            TileObject rainbow = rainbows.get(0);
            if (!Rs2Player.getWorldLocation().equals(rainbow.getWorldLocation())) {
                Rs2Walker.walkTo(rainbow.getWorldLocation(),0);
                sleepUntil(() -> Rs2Player.getWorldLocation().equals(rainbow.getWorldLocation()) || !checkForRainbow(), 10000);
            }
        }
    }

    private void handleBeeHive() {
        currentForestryEvent = ForestryEvents.BEE_HIVE;
        breakPlayersAnimation();

        if (Rs2Widget.findWidget("How many logs would you like to add", null, false) != null) {
            Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
            sleepUntil(() -> !Rs2Player.isAnimating());
            return;
        }

        var beehives = Rs2Npc.getNpcs(x -> x.getId() == net.runelite.api.gameval.NpcID.GATHERING_EVENT_BEES_BEEBOX_1 || x.getId() == net.runelite.api.gameval.NpcID.GATHERING_EVENT_BEES_BEEBOX_2);
        var beehiveOpt = beehives.findFirst();
        if (beehiveOpt.isPresent()) {
            var beehive = beehiveOpt.get();
            Rs2Npc.interact(beehive, "Build");
            Rs2Player.waitForAnimation();
        } else if (checkForSturdyBeeHive()) {
            var sturdyBeehives = Rs2GameObject.getTileObjects(Rs2GameObject.nameMatches("sturdy beehive", false)).stream().filter(
                    x -> x != null &&
                            x.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= FORESTRY_DISTANCE &&
                            Rs2GameObject.hasAction(Rs2GameObject.convertToObjectComposition(x), "Take")
            ).collect(Collectors.toList());

            if (sturdyBeehives != null && !sturdyBeehives.isEmpty()) {
                Rs2GameObject.interact(sturdyBeehives.get(0), "Take");
                Rs2Player.waitForAnimation();
            }
        } else {
            currentForestryEvent = ForestryEvents.NONE;
        }
    }

    private void handlePheasant() {
        currentForestryEvent = ForestryEvents.PHEASANT;
        breakPlayersAnimation();

        if (Rs2Inventory.contains("Pheasant egg")) {
            var foresters = Rs2Npc.getNpcs(npc ->
                    npc.getName() != null &&
                            npc.getName().toLowerCase().contains("freaky forester")
            );

            var forester = foresters.findFirst().orElse(null);
            if (forester == null) {
                currentForestryEvent = ForestryEvents.NONE;
                return;
            }
            Rs2Npc.interact(forester, "Talk-to");
            sleepUntil(() -> Rs2Widget.findWidget("Freaky Forester", null, false) != null, 5000);
            return;
        }

        List<WorldPoint> pheasantLocations = Rs2Npc.getNpcs(npc ->
                npc.getName() != null &&
                        npc.getName().toLowerCase().contains("pheasant")
        ).map(npc -> npc.getWorldLocation()).collect(Collectors.toList());

        var pheasantNests = Rs2GameObject.getTileObjects(Rs2GameObject.nameMatches("pheasant nest", false)).stream().filter(
                x -> x != null &&
                        x.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()) <= FORESTRY_DISTANCE &&
                        Rs2GameObject.hasAction(Rs2GameObject.convertToObjectComposition(x), "Retrieve-egg") &&
                        !pheasantLocations.contains(x.getWorldLocation())
        ).collect(Collectors.toList());

        if (pheasantNests != null && !pheasantNests.isEmpty()) {
            Rs2GameObject.interact(pheasantNests.get(0), "Retrieve-egg");
            Rs2Player.waitForAnimation();
        } else {
            currentForestryEvent = ForestryEvents.NONE;
        }
    }

    private void handleRitualCircles() {
        currentForestryEvent = ForestryEvents.RITUAL_CIRCLES;
        breakPlayersAnimation();

        NPC targetCircle = solveCircles();
        if (targetCircle != null) {
            WorldPoint targetLocation = targetCircle.getWorldLocation();
            if (!Rs2Player.getWorldLocation().equals(targetLocation)) {
                Rs2Walker.walkTo(targetLocation,0);
                sleepUntil(() -> Rs2Player.getWorldLocation().equals(targetLocation) || !checkForRitualCircles(), 10000);
            }
        } else {
            currentForestryEvent = ForestryEvents.NONE;
            Microbot.log("Unable to solve ritual circles, not enough circles or invalid configuration.");
        }
    }

    private NPC solveCircles() {
        if (ritualCircles.size() != 5) {
            return null;
        }

        int s = 0;
        for (NPC npc : ritualCircles) {
            int off = npc.getId() - NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1;
            int shape = off / 4;
            int color = off % 4;
            int id = (16 << shape) | (1 << color);
            s = s ^ id; // XOR operation
        }

        for (NPC npc : ritualCircles) {
            int off = npc.getId() - NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1;
            int shape = off / 4;
            int color = off % 4;
            int id = (16 << shape) | (1 << color);
            if ((id & s) == id) { // Bitwise AND
                return npc;
            }
        }

        return null;
    }

    private void handleEntling(PruningAction pruningAction) {
        currentForestryEvent = ForestryEvents.ENTLING;
        breakPlayersAnimation();

        if (pruningAction != null) {
            pruningAction.execute();
            Rs2Player.waitForAnimation();
        } else {
            currentForestryEvent = ForestryEvents.NONE;
        }
    }

    private PruningAction findEntlingToPrune() {
        var entlings = Rs2Npc.getNpcs(npc ->
                npc.getName() != null &&
                        npc.getName().toLowerCase().contains("entling") &&
                        npc.getOverheadText() != null
        ).collect(Collectors.toList());

        entlings.sort(Comparator.comparingInt(e ->
                e.getWorldLocation().distanceTo(Rs2Player.getWorldLocation())
        ));

        for (Rs2NpcModel entling : entlings) {
            String request = entling.getOverheadText();
            if (request == null || request.isEmpty()) {
                return null;
            }
            String action = null;

            switch (request) {
                case "Breezy at the back!":
                case "Short back and sides!":
                    action = "Prune-back";
                    break;
                case "A leafy mullet!":
                case "Short on top!":
                    action = "Prune-top";
                    break;
                default:
                    continue;
            }

            return new PruningAction(entling, action);
        }

        return null;
    }

    public boolean breakPlayersAnimation() {
        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            if (Rs2Inventory.contains("logs")) {
                Rs2Inventory.interact(item -> item.getName().toLowerCase().contains("logs"), "Drop");
                return true;
            }
        }
        return false;
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

        return Rs2AntibanSettings.actionCooldownActive;
    }

    private void resetInventory(AutoWoodcuttingConfig config) {
        switch (config.resetOptions()) {
            case DROP:
                Rs2Inventory.dropAllExcept(false, config.interactOrder(), "axe", "tinderbox");
                woodcuttingScriptState = WoodcuttingScriptState.WOODCUTTING;
                break;
            case BANK:
                List<String> itemNames = Arrays.stream(config.itemsToBank().split(",")).map(String::toLowerCase).collect(Collectors.toList());

                if (!Rs2Bank.bankItemsAndWalkBackToOriginalPosition(itemNames, getReturnPoint(config)))
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
        ritualCircles.clear();
        currentForestryEvent = ForestryEvents.NONE;
        Rs2Antiban.resetAntibanSettings();
    }
}
