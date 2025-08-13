package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.widgets.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.gpu.GpuPlugin;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.tempoross.enums.HarpoonType;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldArea;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.Microbot.log;

public class TemporossScript extends Script {

    // Version string
    public static final String VERSION = "2.0";
    public static final Pattern DIGIT_PATTERN = Pattern.compile("(\\d+)");
    public static final int TEMPOROSS_REGION = 12078;

    // Game state variables

    public static int ENERGY;
    public static int INTENSITY;
    public static int ESSENCE;

    public static TemporossConfig temporossConfig;
    public static State state = State.INITIAL_CATCH;
    public static TemporossWorkArea workArea = null;
    public static boolean isFilling = false;
    public static boolean isFightingFire = false;
    public static boolean isRepairingTotem = false;
    public static HarpoonType harpoonType;
    public static Rs2NpcModel temporossPool;
    public static List<Rs2NpcModel> sortedFires = new ArrayList<>();
    public static List<GameObject> sortedClouds = new ArrayList<>();
    public static List<Rs2NpcModel> fishSpots = new ArrayList<>();
    public static List<WorldPoint> walkPath = new ArrayList<>();
    public static TileObject cachedBrokenTotem = null;
    public static TileObject cachedBrokenMast = null;

    public boolean run(TemporossConfig config) {
        temporossConfig = config;
        ENERGY = 0;
        INTENSITY = 0;
        ESSENCE = 0;
        workArea = null;
        TemporossPlugin.incomingWave = false;
        TemporossPlugin.isTethered = false;
        TemporossPlugin.fireClouds = 0;
        TemporossPlugin.waves = 0;
        state = State.INITIAL_CATCH;
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = false;
        Rs2AntibanSettings.takeMicroBreaks = false;
        Rs2AntibanSettings.microBreakChance = 0.2;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->{
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (BreakHandlerScript.isBreakActive()) return;

                if (!isInMinigame()) {
                    handleEnterMinigame();
                }
                if (isInMinigame()) {
                    if (workArea == null) {
                        determineWorkArea();
                        sleep(300, 600);
                    } else {

                        handleMinigame();
                        
                        // Prioritize tether action if a wave is coming - using the new method
                        if(checkAndHandleIncomingWave()) {
                            log("Wave incoming - tethering handled at top level");
                            return; // Return immediately after successful tethering
                        }
                        
                        // Prioritize repairing damaged masts and totems
                        if(checkAndHandleDamagedStructures()) {
                            log("Damaged structure detected - repair handled at top level");
                            return; // Return immediately after successful repair
                        }
                        
                        handleStateLoop();
                        if(areItemsMissing())
                            return;
                        // In solo mode, continuously handle fires.
                        // In mass world mode, fire-fighting is now handled dynamically before objectives.
                        handleFires();
                        // Removed redundant call to handleTether() as it's already called in checkAndHandleIncomingWave()
                        if(isFightingFire || TemporossPlugin.isTethered || TemporossPlugin.incomingWave)
                            return;
                        handleForfeit();

                        finishGame();
                        handleMainLoop();
                    }
                }
            } catch (Exception e) {
                log("Error in script: " + e.getMessage());
                e.printStackTrace();
            }

        }, 0, 300, TimeUnit.MILLISECONDS);
        return true;
    }

    private int getPhase() {
        return 1 + (TemporossPlugin.waves / 4); // every 3 waves, phase increases by 1
    }

    static boolean isInMinigame() {
        if(Microbot.getClient().getGameState() != GameState.LOGGED_IN)
            return false;
        int regionId = Rs2Player.getWorldLocation().getRegionID();
        return regionId == TEMPOROSS_REGION;
    }

    private boolean hasHarpoon() {
        return harpoonType.hasVariantInInventoryOrEquipment();
    }

    private void determineWorkArea() {
        if (workArea == null) {
            Rs2NpcModel forfeitNpc = Rs2Npc.getNearestNpcWithAction("Forfeit");
            Rs2NpcModel ammoCrate = Rs2Npc.getNearestNpcWithAction("Fill");

            if (forfeitNpc == null || ammoCrate == null) {
                log("Can't find forfeit NPC or ammo crate");
                return;
            }
            boolean isWest = forfeitNpc.getWorldLocation().getX() < ammoCrate.getWorldLocation().getX();
            workArea = new TemporossWorkArea(forfeitNpc.getWorldLocation(), isWest);
            // log tempoross work area if its west or east
            if(Rs2AntibanSettings.devDebug) {
                log("Tempoross work area: " + (isWest ? "west" : "east"));
                log(workArea.getAllPointsAsString());
            }
        }
    }

    private void finishGame() {
        Rs2WorldPoint playerLocation = new Rs2WorldPoint(Microbot.getClient().getLocalPlayer().getWorldLocation());
        Rs2NpcModel exitNpc = Rs2Npc.getNpcs()
                .filter(value -> value.getComposition() != null
                        && value.getComposition().getActions() != null
                        && Arrays.asList(value.getComposition().getActions()).contains("Leave"))
                .min(Comparator.comparingInt(value -> playerLocation.distanceToPath(value.getWorldLocation())))
                .orElse(null);

        if (exitNpc != null) {
            int desiredBuckets = temporossConfig.buckets();

            // Take buckets until reaching the desired amount
            while (Rs2Inventory.count(ItemID.BUCKET_EMPTY) + Rs2Inventory.count(ItemID.BUCKET_WATER) < desiredBuckets) {
                int before = Rs2Inventory.count(ItemID.BUCKET_EMPTY) + Rs2Inventory.count(ItemID.BUCKET_WATER);
                if (Rs2GameObject.interact(ObjectID.TEMPOROSS_CRATE_BUCKET, "Take")) {
                    sleepUntil(() -> Rs2Inventory.count(ItemID.BUCKET_EMPTY) + Rs2Inventory.count(ItemID.BUCKET_WATER) > before, 2000);
                } else {
                    break;
                }
            }

            // Fill all empty buckets
            while (Rs2Inventory.count(ItemID.BUCKET_EMPTY) > 0) {
                if (Rs2GameObject.interact(ObjectID.TEMPOROSS_WATER_PUMP_DOCK, "Fill-bucket")) {
                    sleepUntil(() -> Rs2Inventory.count(ItemID.BUCKET_EMPTY) == 0, 5000);
                } else {
                    break;
                }
            }

            if (Rs2Npc.interact(exitNpc, "Leave")) {
                reset();
                sleepUntil(() -> !isInMinigame(), 15000);
                BreakHandlerScript.setLockState(false);
                Rs2Antiban.takeMicroBreakByChance();
                // Reset ATTACK_TEMPOROSS tracking data
                EnergyStateManager.resetAttackTemporossTracking();
            }
        }
    }


    private void reset(){
        ENERGY = 0;
        INTENSITY = 0;
        ESSENCE = 0;
        workArea = null;
        isFilling = false;
        isFightingFire = false;
        walkPath = null;
        TemporossPlugin.incomingWave = false;
        TemporossPlugin.isTethered = false;
        TemporossPlugin.fireClouds = 0;
        TemporossPlugin.waves = 0;
        state = State.INITIAL_CATCH;
    }

    public void handleForfeit() {
        if ((INTENSITY >= 94 && state == State.THIRD_COOK)) {
            var forfeitNpc = Rs2Npc.getNearestNpcWithAction("Forfeit");
            if (forfeitNpc != null) {
                if (Rs2Npc.interact(forfeitNpc, "Forfeit")) {
                    sleepUntil(() -> !isInMinigame(), 10000);
                    reset();
                    BreakHandlerScript.setLockState(false);
                }
            }
        }
    }

    private void forfeit() {
        var forfeitNpc = Rs2Npc.getNearestNpcWithAction("Forfeit");
        if (forfeitNpc != null) {
            if (Rs2Npc.interact(forfeitNpc, "Forfeit")) {
                sleepUntil(() -> !isInMinigame(), 10000);
                reset();
                BreakHandlerScript.setLockState(false);
            }
        }
    }

    private void handleMinigame()
    {
        // Do not proceed if the minigame phase is too advanced
        if (getPhase() > 2)
            return;

        // Update the current harpoon type from the configuration
        harpoonType = temporossConfig.harpoonType();

        // Check if any required item is missing. If so, fetch it and return.
        if (areItemsMissing())
        {
            // Before interacting with crates, clear fires along the path to the crate.
            // In mass world mode, only fires blocking the path will be doused.
            fetchMissingItems();
        }

        // Continue with further minigame logic if all items are available
        // ...
    }

    private boolean areItemsMissing()
    {
        // Check for harpoon
        if (!hasHarpoon() && harpoonType != HarpoonType.BAREHAND)
        {
            return true;
        }

        // Check bucket counts (empty or full)
        int bucketCount = Rs2Inventory.count(item ->
                item.getId() == ItemID.BUCKET_EMPTY || item.getId() == ItemID.BUCKET_WATER);
        // Check if state is after INITIAL_FILL for the solo mode bucket count check
        boolean isAfterInitialFill = state == State.THIRD_CATCH || state == State.THIRD_COOK || 
                                     state == State.SECOND_FILL || state == State.ATTACK_TEMPOROSS || 
                                     state == State.EMERGENCY_FILL || state == State.EMERGENCY_CATCH;
        if ((bucketCount < temporossConfig.buckets() && state == State.INITIAL_CATCH) || bucketCount == 0 || 
            (temporossConfig.solo() && bucketCount <= 2 && isAfterInitialFill))
        {
            return true;
        }

        // Check full buckets of water
        if (Rs2Inventory.count(ItemID.BUCKET_WATER) <= 0)
        {
            return true;
        }

        // Check for rope
        if (temporossConfig.rope() && !temporossConfig.spiritAnglers() && !Rs2Inventory.contains(ItemID.ROPE))
        {
            return true;
        }

        // Check for hammer (skip if Imcando hammer off-hand is enabled)
        return temporossConfig.hammer() && !temporossConfig.imcandoHammerOffHand() && !Rs2Inventory.contains(ItemID.HAMMER);
    }

    /**
     * Fetches missing items required for the Tempoross minigame.
     * The bot will forfeit if there are fires blocking paths to required items.
     */
    private void fetchMissingItems()
    {
        // 1) Harpoon
        if (!hasHarpoon() && harpoonType != HarpoonType.BAREHAND)
        {
            harpoonType = HarpoonType.HARPOON;
            log("Missing selected harpoon, setting to default harpoon");
            TemporossPlugin.setHarpoonType(harpoonType);

            // Before interacting, clear fires along the path to the harpoon crate.
            if (!fightFiresInPath(workArea.harpoonPoint))
            {
                log("Could not douse fires in path to harpoon crate, forfeiting");
                forfeit();
                return;
            }

            if (Rs2GameObject.interact(workArea.getHarpoonCrate(), "Take"))
            {
                log("Taking harpoon");
                sleepUntil(this::hasHarpoon, 10000);
            }
            return;
        }

        // 2) Buckets
        int bucketCount = Rs2Inventory.count(item ->
                item.getId() == ItemID.BUCKET_EMPTY || item.getId() == ItemID.BUCKET_WATER);
        // Check if state is after INITIAL_FILL for the solo mode bucket count check
        boolean isAfterInitialFill = state == State.THIRD_CATCH || state == State.THIRD_COOK || 
                                     state == State.SECOND_FILL || state == State.ATTACK_TEMPOROSS || 
                                     state == State.EMERGENCY_FILL || state == State.EMERGENCY_CATCH;
        if ((bucketCount < temporossConfig.buckets() && state == State.INITIAL_CATCH) || bucketCount == 0 || 
            (temporossConfig.solo() && bucketCount <= 2 && isAfterInitialFill))
        {
            log("Buckets: " + bucketCount);

            // Check for fires along the path to each bucket crate and corresponding pump and select the one without fires
            TileObject bucketCrateObj = workArea.getBucketCrate();
            TileObject dockBucketObj = workArea.getDockBucketCrate();
            TileObject pumpObj = workArea.getPump();
            TileObject dockPumpObj = workArea.getDockPump();
            
            // Check path to main bucket crate and pump (since they're next to each other)
            boolean mainPathClear = fightFiresInPath(workArea.bucketPoint);
            if (bucketCrateObj != null) {
                mainPathClear = mainPathClear && fightFiresInPath(bucketCrateObj.getWorldLocation());
            }
            // Also check pump path since bucket and pump areas are adjacent
            mainPathClear = mainPathClear && fightFiresInPath(workArea.pumpPoint);
            if (pumpObj != null) {
                mainPathClear = mainPathClear && fightFiresInPath(pumpObj.getWorldLocation());
            }
            
            // Check path to dock bucket crate and dock pump (since they're next to each other)
            boolean dockPathClear = false;
            if (dockBucketObj != null) {
                dockPathClear = fightFiresInPath(dockBucketObj.getWorldLocation());
                // Also check dock pump path since dock bucket and dock pump areas are adjacent
                dockPathClear = dockPathClear && fightFiresInPath(workArea.dockPumpPoint);
                if (dockPumpObj != null) {
                    dockPathClear = dockPathClear && fightFiresInPath(dockPumpObj.getWorldLocation());
                }
            }
            
            // If both paths have fires, forfeit
            if (!mainPathClear && !dockPathClear)
            {
                log("Could not douse fires in path to either bucket crate");
                log("Forfeiting due to fires blocking both paths");
                forfeit();
                return;
            }
            
            // Determine which bucket crate to use based on fire status and distance
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            TileObject nearestBucket = null;
            
            if (bucketCrateObj != null && dockBucketObj != null) {
                // If one path is clear and the other isn't, use the clear path
                if (mainPathClear && !dockPathClear) {
                    nearestBucket = bucketCrateObj;
                    log("Using main bucket crate (dock path has fires)");
                } else if (!mainPathClear && dockPathClear) {
                    nearestBucket = dockBucketObj;
                    log("Using dock bucket crate (main path has fires)");
                } else {
                    // If both paths are clear or both have fires, use the closest one
                    int dist1 = playerLoc.distanceTo(bucketCrateObj.getWorldLocation());
                    int dist2 = playerLoc.distanceTo(dockBucketObj.getWorldLocation());
                    nearestBucket = dist1 <= dist2 ? bucketCrateObj : dockBucketObj;
                    log("Using nearest bucket crate (distance-based selection)");
                }
            } else {
                nearestBucket = bucketCrateObj != null ? bucketCrateObj : dockBucketObj;
            }
            
            // Use the nearest bucket crate
            final TileObject finalNearestBucket = nearestBucket;
            sleepUntil(() -> {
                int count = Rs2Inventory.count(item ->
                    item.getId() == ItemID.BUCKET_EMPTY || item.getId() == ItemID.BUCKET_WATER);
                // In INITIAL_CATCH, always collect up to configured amount
                // In other states after INITIAL_FILL, solo mode will attempt to collect more buckets if bucket count is at 2, allows for emergency firefighting
                return count >= temporossConfig.buckets() || (temporossConfig.solo() && count > 3 && isAfterInitialFill);
            },() -> {
                if (finalNearestBucket != null && Rs2GameObject.interact(finalNearestBucket, "Take")) {
                    log("Taking buckets");
                    Rs2Inventory.waitForInventoryChanges(3000);
                }},10000,300);


            return;
        }

        // 3) Fill Buckets
        int fullBucketCount = Rs2Inventory.count(ItemID.BUCKET_WATER);
        if (fullBucketCount <= 1)
        {
            TileObject pumpObj = workArea.getPump();
            TileObject dockPumpObj = workArea.getDockPump();

            // Check path to main pump
            boolean mainPathClear = fightFiresInPath(workArea.pumpPoint);
            if (pumpObj != null) {
                mainPathClear = mainPathClear && fightFiresInPath(pumpObj.getWorldLocation());
            }
            
            // Check path to dock pump
            boolean dockPathClear = false;
            if (dockPumpObj != null) {
                dockPathClear = fightFiresInPath(dockPumpObj.getWorldLocation());
            }
            
            // If both paths have fires, forfeit
            if (!mainPathClear && !dockPathClear) {
                log("Could not douse fires in path to either pump");
                log("Forfeiting due to fires blocking both pump paths");
                forfeit();
                return;
            }

            // Determine which pump to use based on fire status and distance
            WorldPoint playerLoc = Rs2Player.getWorldLocation();
            TileObject nearestPump = null;
            
            if (pumpObj != null && dockPumpObj != null) {
                // If one path is clear and the other isn't, use the clear path
                if (mainPathClear && !dockPathClear) {
                    nearestPump = pumpObj;
                    log("Using main pump (dock path has fires)");
                } else if (!mainPathClear && dockPathClear) {
                    nearestPump = dockPumpObj;
                    log("Using dock pump (main path has fires)");
                } else {
                    // If both paths are clear or both have fires, use the closest one
                    int dist1 = playerLoc.distanceTo(pumpObj.getWorldLocation());
                    int dist2 = playerLoc.distanceTo(dockPumpObj.getWorldLocation());
                    nearestPump = dist1 <= dist2 ? pumpObj : dockPumpObj;
                    log("Using nearest pump (distance-based selection)");
                }
            } else {
                nearestPump = pumpObj != null ? pumpObj : dockPumpObj;
            }

            if (nearestPump != null && Rs2GameObject.interact(nearestPump, "Use"))
            {
                log("Filling buckets");
                sleepUntil(() -> Rs2Inventory.count(ItemID.BUCKET_EMPTY) <= 0, 10000);
            }
            return;
        }

        // 4) Rope (if required)
        if (temporossConfig.rope() && !temporossConfig.spiritAnglers() && !Rs2Inventory.contains(ItemID.ROPE))
        {
            // Before interacting, clear fires along the path to the rope crate.
            if (!fightFiresInPath(workArea.ropePoint))
            {
                log("Could not douse fires in path to rope crate, forfeiting");
                forfeit();
                return;
            }

            if (Rs2GameObject.interact(workArea.getRopeCrate(), "Take"))
            {
                log("Taking rope");
                sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(8000));
            }
            return;
        }

        // 5) Hammer (if required and not using Imcando hammer off-hand)
        if (temporossConfig.hammer() && !temporossConfig.imcandoHammerOffHand() && !Rs2Inventory.contains(ItemID.HAMMER))
        {
            // Before interacting, clear fires along the path to the hammer crate.
            if (!fightFiresInPath(workArea.hammerPoint))
            {
                log("Could not douse fires in path to hammer crate, forfeiting");
                forfeit();
                return;
            }

            if (Rs2GameObject.interact(workArea.getHammerCrate(), "Take"))
            {
                log("Taking hammer");
                sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(10000));
            }
        }
    }

    private boolean isOnStartingBoat() {
        TileObject startingLadder = Rs2GameObject.findObjectById(ObjectID.TEMPOROSS_LOBBY_LADDER);
        if (startingLadder == null) {
            log("Failed to find starting ladder");
            return false;
        }
        return Rs2Player.getWorldLocation().getX() < startingLadder.getWorldLocation().getX();
    }

    private void handleEnterMinigame() {
        // Reset state variables
        reset();

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return;
        }
        TileObject startingLadder = Rs2GameObject.findObjectById(ObjectID.TEMPOROSS_LOBBY_LADDER);
        if (startingLadder == null) {
            log("Failed to find starting ladder");
            return;
        }
        int emptyBucketCount = Rs2Inventory.count(ItemID.BUCKET_EMPTY);
        // If we are east of the ladder, interact with it to get on the boat
        if (!isOnStartingBoat()) {
            if (Rs2GameObject.interact(startingLadder, ((emptyBucketCount > 0 && temporossConfig.solo()) || !temporossConfig.solo()) ? "Climb" : "Solo-start")) {
                BreakHandlerScript.setLockState(true);
                sleepUntil(() -> (isOnStartingBoat() || isInMinigame()), 15000);
                return;
            }
        }

        TileObject waterPump = Rs2GameObject.findObjectById(ObjectID.TEMPOROSS_WATER_PUMP);

        if (waterPump != null && emptyBucketCount > 0) {
            if (Rs2GameObject.interact(waterPump, "Use")) {
                Rs2Player.waitForAnimation(5000);
            }
        }
        sleepUntil(TemporossScript::isInMinigame, 30000);
        
        // Set camera yaw to 0 when entering the minigame in solo mode
        if (isInMinigame() && temporossConfig.solo()) {
        }
    }

    public static void handleWidgetInfo() {
        try {
            Widget energyWidget = Microbot.getClient().getWidget(InterfaceID.TEMPOROSS, 35);
            Widget essenceWidget = Microbot.getClient().getWidget(InterfaceID.TEMPOROSS, 45);
            Widget intensityWidget = Microbot.getClient().getWidget(InterfaceID.TEMPOROSS, 55);

            if (energyWidget == null || essenceWidget == null || intensityWidget == null) {
                if(Rs2AntibanSettings.devDebug)
                    log("Failed to find energy, essence, or intensity widget");
                return;
            }

            Matcher energyMatcher = DIGIT_PATTERN.matcher(energyWidget.getText());
            Matcher essenceMatcher = DIGIT_PATTERN.matcher(essenceWidget.getText());
            Matcher intensityMatcher = DIGIT_PATTERN.matcher(intensityWidget.getText());
            if (!energyMatcher.find() || !essenceMatcher.find() || !intensityMatcher.find())
            {
                if(Rs2AntibanSettings.devDebug)
                    log("Failed to parse energy, essence, or intensity");
                return;
            }

            ENERGY = Integer.parseInt(energyMatcher.group(0));
            ESSENCE = Integer.parseInt(essenceMatcher.group(0));
            INTENSITY = Integer.parseInt(intensityMatcher.group(0));

            // Handle state transitions
            handleStateTransitions();
        } catch (NumberFormatException e) {
            if(Rs2AntibanSettings.devDebug)
                log("Failed to parse energy, essence, or intensity");
        }
    }

    public static void updateFireData(){
        List<Rs2NpcModel> allFires = Rs2Npc
                .getNpcs(npc -> Arrays.asList(npc.getComposition().getActions()).contains("Douse"))
                .map(Rs2NpcModel::new)
                .collect(Collectors.toList());
        Rs2WorldPoint playerLocation = new Rs2WorldPoint(Microbot.getClient().getLocalPlayer().getWorldLocation());
        sortedFires = allFires.stream()
                .filter(y -> playerLocation.distanceToPath(y.getWorldLocation()) < 35)
                .sorted(Comparator.comparingInt(x -> playerLocation.distanceToPath(x.getWorldLocation())))
                .collect(Collectors.toList());
        TemporossOverlay.setNpcList(sortedFires);
    }

    public static void updateCloudData(){
        List<GameObject> allClouds = Rs2GameObject.getGameObjects().stream()
                .filter(obj -> obj.getId() == ObjectID.TEMPOROSS_LIGHTNING_SHADOW)
                .collect(Collectors.toList());
        Rs2WorldPoint playerLocation = new Rs2WorldPoint(Microbot.getClient().getLocalPlayer().getWorldLocation());
        sortedClouds = allClouds.stream()
                .filter(y -> playerLocation.distanceToPath(y.getWorldLocation()) < 30)
                .sorted(Comparator.comparingInt(x -> playerLocation.distanceToPath(x.getWorldLocation())))
                .collect(Collectors.toList());
        TemporossOverlay.setCloudList(sortedClouds);
    }


    // update ammo crate data
    public static void updateAmmoCrateData(){
        List<Rs2NpcModel> ammoCrates = Rs2Npc
                .getNpcs()
                .filter(npc -> Arrays.asList(npc.getComposition().getActions()).contains("Fill"))
                .filter(npc -> npc.getWorldLocation().distanceTo(workArea.mastPoint) <= 4)
                .filter(npc -> !inCloud(npc.getWorldLocation(),2))
                .map(Rs2NpcModel::new)
                .collect(Collectors.toList());
        TemporossOverlay.setAmmoList(ammoCrates);
    }

    public static void updateFishSpotData(){
        // if double fishing spot is present, prioritize it
        fishSpots = Rs2Npc.getNpcs()
                .filter(npc -> npc.getId() == NpcID.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SPECIAL || npc.getId() == NpcID.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SOUTH || npc.getId() == NpcID.TEMPOROSS_HARPOONFISH_FISHINGSPOT_NORTH)
                .filter(npc -> !inCloud(npc.getRuneliteNpc().getWorldLocation(),2))
                .filter(npc -> npc.getWorldLocation().distanceTo(workArea.rangePoint) <= 20)
                .sorted(Comparator
                        .comparingInt(npc -> npc.getId() == NpcID.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SPECIAL ? 0 : 1))
                .collect(Collectors.toList());
        TemporossOverlay.setFishList(fishSpots);
    }

    public static void updateTotemData() {
        if (workArea == null) {
            return;
        }
        
        // Check for broken totem at the totem location
        TileObject totem = Rs2GameObject.findGameObjectByLocation(workArea.totemPoint);
        if (totem != null && (totem.getId() == ObjectID.TEMPOROSS_TOTEM_NORTH_BROKEN || totem.getId() == ObjectID.TEMPOROSS_TOTEM_SOUTH_BROKEN)) {
            cachedBrokenTotem = totem;
        } else {
            cachedBrokenTotem = null;
        }
    }
    
    public static void updateMastData() {
        if (workArea == null) {
            return;
        }
        
        // Check for broken mast at the mast location
        TileObject mast = Rs2GameObject.findGameObjectByLocation(workArea.mastPoint);
        if (mast != null && (mast.getId() == ObjectID.TEMPOROSS_MAST_BOTTOM_WEST_BROKEN || mast.getId() == ObjectID.TEMPOROSS_MAST_BOTTOM_EAST_BROKEN)) {
            cachedBrokenMast = mast;
        } else {
            cachedBrokenMast = null;
        }
    }

    public static void updateLastWalkPath() {
        TemporossOverlay.setLastWalkPath(walkPath);
    }

    /**
     * Checks if target fish is reached for solo mode when energy is 11% or above
     * and activates EMERGENCY_FILL if needed.
     * Also checks if we should transition to EMERGENCY_CATCH state based on energy level.
     */
    public static void checkTargetFishReached() {
        // First check if we should transition to EMERGENCY_CATCH state
        if (temporossConfig != null && temporossConfig.solo()
            && ENERGY >= 11) {

            // Call shouldTransitionToEmergencyCatch to check if we should transition to EMERGENCY_CATCH
            EnergyStateManager.shouldTransitionToEmergencyCatch();

        }
    }

    /**
     * Handles state transitions based on completion status
     * This logic was moved from TemporossProgressionOverlay to ensure
     * it runs regardless of whether the overlay is visible
     */
    public static void handleStateTransitions() {
        // Check if target fish is reached for solo mode
        checkTargetFishReached();

        if (state != null && state.isComplete()) {
            isFilling = false;
            
            // Get the next state using the getNext() method
            State nextState = state.getNext();
            
            // Simple logging for SECOND_COOK transitions in solo mode
            if (state == State.SECOND_COOK && State.isSolo()) {
                log("In solo mode, cooked fish: " + State.getCookedFish() + ", proceeding to INITIAL_FILL");
            }
            
            // Reset isTransitioningToEmergencyFill when transitioning from EMERGENCY_FILL to another state
            if (state == State.EMERGENCY_FILL) {
                EnergyStateManager.resetEmergencyFillTransition();
                log("Transitioning from EMERGENCY_FILL to " + nextState);
            }
            
            state = nextState;
        }
    }

    /**
     * In solo mode, fires are continuously handled.
     * In mass world mode, this continuous loop is disabled so that fire-fighting
     * is only triggered dynamically when an objective is set.
     */
    private void handleFires() {
        if (!temporossConfig.solo()) {
            // Mass world mode: skip continuous fire-fighting.
            return;
        }
        if (sortedFires.isEmpty() || state == State.ATTACK_TEMPOROSS) {
            isFightingFire = false;
            return;
        }
        
        // Special handling for SECOND_COOK state - finish cooking before handling fires
        // But allow fire handling when cooking is complete and clouds are present (so clouds can turn into fires and be handled)
        if (state == State.SECOND_COOK && !state.isComplete()) {
            log("SECOND_COOK state: Fire detected but finishing cooking first before handling fire");
            isFightingFire = false;
            return;
        }
        
        // In SECOND_COOK state when cooking is complete, allow fire handling if clouds are present
        // This ensures that when clouds turn into fires, they can be handled properly
        if (state == State.SECOND_COOK && state.isComplete() && !sortedClouds.isEmpty()) {
            int rawFishCount = Rs2Inventory.count(ItemID.TEMPOROSS_RAW_HARPOONFISH);
            if (rawFishCount == 0) {
                log("SECOND_COOK state: Cooking complete and clouds present, allowing fire handling for cloud-to-fire transitions");
            }
        }
        
        // Skip fire handling if both energy and essence are low (solo mode only)
        if (temporossConfig.solo() && ENERGY <= 10 && ESSENCE <= 35) {
            log("Solo mode: Both energy (" + ENERGY + "%) and essence (" + ESSENCE + "%) are low, skipping fire handling");
            isFightingFire = false;
            return;
        }
        isFightingFire = true;
        
        // Get player location for distance calculations
        WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
        
        // Sort fires by distance to player
        List<Rs2NpcModel> prioritizedFires = new ArrayList<>(sortedFires);
        prioritizedFires.sort(Comparator.comparingInt(fire -> 
            playerLocation.distanceTo(fire.getWorldLocation())));
        
        for (Rs2NpcModel fire : prioritizedFires) {

            // Special handling for INITIAL_FILL and EMERGENCY_FILL states - don't skip fires even when filling
            // This fixes the issue where the bot dodges fire but then runs through it
            if (isFilling && state != State.INITIAL_FILL && state != State.EMERGENCY_FILL) {
                Microbot.log("Filling (not in INITIAL_FILL or EMERGENCY_FILL), skipping fire");
                return;
            }
            
            // For INITIAL_FILL state, skip fire handling when filling to avoid interrupting the process
            if (state == State.INITIAL_FILL) {
                // In solo mode with fish in inventory (less than 9), prioritize filling first
                // But still handle critical nearby fires to prevent getting stuck
                if (temporossConfig.solo() && State.getCookedFish() < 9 && State.getCookedFish() > 0) {
                    int distanceToFire = playerLocation.distanceTo(fire.getWorldLocation());
                    // Only handle fires that are very close (within 2 tiles) to prevent getting stuck
                    if (distanceToFire <= 2) {
                        log("INITIAL_FILL state: Handling critical nearby fire at distance " + distanceToFire + " (harpoonfish count: " + State.getCookedFish() + ")");
                        if (Rs2Npc.interact(fire, "Douse")) {
                            sleepUntil(() -> !Rs2Player.isInteracting(), 1300);
                            // Reset isFightingFire to allow bot to continue with INITIAL_FILL after handling critical fire
                            isFightingFire = false;
                            log("INITIAL_FILL state: Critical fire handled, resuming filling activities");
                            return;
                        }
                    } else {
                        log("INITIAL_FILL state: Skipping distant fire at distance " + distanceToFire + " to prioritize filling (harpoonfish count: " + State.getCookedFish() + ")");
                        continue; // Skip only distant fires, not all fires
                    }
                }
                // If inventory is empty (no cooked fish), handle all fires normally
                if (temporossConfig.solo() && State.getCookedFish() == 0) {
                    log("INITIAL_FILL state: No more fish in inventory, handling fire normally");
                }
                // For non-solo mode or when inventory is empty, handle fires within 5 tiles
                if (!temporossConfig.solo() || State.getCookedFish() == 0) {
                    int distanceToFire = playerLocation.distanceTo(fire.getWorldLocation());
                    if (distanceToFire <= 5) {
                        log("INITIAL_FILL state: Prioritizing nearby fire at distance " + distanceToFire);
                        if (Rs2Npc.interact(fire, "Douse")) {
                            sleepUntil(() -> !Rs2Player.isInteracting(), 1300);
                            // Reset isFightingFire to allow bot to continue with INITIAL_FILL after handling fire
                            isFightingFire = false;
                            log("INITIAL_FILL state: Fire handled, resuming filling activities");
                            return;
                        }
                    }
                }
            }
            
            if (Rs2Player.isInteracting()) {
                if (Objects.equals(Rs2Player.getInteracting(), fire)) {
                    return;
                }
            }
            
            if (Rs2Npc.interact(fire, "Douse")) {
                log("Dousing fire");
                sleepUntil(() -> !Rs2Player.isInteracting(), 1500);
                return;
            }
        }
    }

    private void handleDamagedMast() {
        TileObject damagedMast = workArea.getBrokenMast();
        if(damagedMast == null)
            return;

        // Check if within range and have hammer if needed (or using Imcando hammer off-hand)
        if (Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(damagedMast.getWorldLocation()) <= 6
                && (!temporossConfig.hammer() || temporossConfig.imcandoHammerOffHand() || Rs2Inventory.contains("Hammer"))) {

            // SOLO MODE ONLY: Special handling for lightning shadow - wait for it to become fire and douse it
            if (temporossConfig.solo()) {
                WorldPoint mastLocation = damagedMast.getWorldLocation();
                
                // Check for lightning shadow at the mast location using existing inCloud method
                if (inCloud(mastLocation, 0)) {
                    log("Lightning shadow detected near mast - waiting for it to become fire");
                    
                    // Wait for lightning shadow to transform into fire NPC (up to 5 seconds)
                    boolean fireAppeared = sleepUntil(() -> {
                        Rs2NpcModel fire = sortedFires.stream()
                                .filter(npc -> mastLocation.distanceTo(npc.getWorldLocation()) <= 3)
                                .findFirst()
                                .orElse(null);
                        return fire != null;
                    }, 5000);
                    
                    if (fireAppeared) {
                        // Find and douse the fire that appeared from lightning shadow using existing sortedFires
                        Rs2NpcModel lightningFire = sortedFires.stream()
                                .filter(npc -> mastLocation.distanceTo(npc.getWorldLocation()) <= 3)
                                .findFirst()
                                .orElse(null);
                        
                        if (lightningFire != null) {
                            log("Lightning shadow became fire - dousing before mast repair");
                            if (Rs2Npc.interact(lightningFire, "Douse")) {
                                sleepUntil(() -> !Rs2Player.isInteracting(), 1500);
                                return; // Exit to allow fire to be fully extinguished before mast repair
                            }
                        }
                    }
                }
            }

            // PRIORITY: Check for fires at the mast location first - if fire is there, handle it before repairing mast
            WorldPoint mastLocation = damagedMast.getWorldLocation();
            Rs2NpcModel nearbyFire = sortedFires.stream()
                    .filter(fire -> mastLocation.distanceTo(fire.getWorldLocation()) <= 3) // Fire within 3 tiles of mast
                    .findFirst()
                    .orElse(null);

            if (nearbyFire != null) {
                log("Fire detected at mast location - extinguishing fire before repairing mast");
                if (Rs2Npc.interact(nearbyFire, "Douse")) {
                    log("Dousing fire near mast before repair");
                    sleepUntil(() -> !Rs2Player.isInteracting(), 1500);
                    return; // Exit to allow fire to be fully extinguished before mast repair
                }
            }

            // Store current state to return to
            State previousState = state;

            // Force stop current activity
            if (Rs2Player.isInteracting()) {
                // Move one tile away to break interaction
                WorldPoint playerPos = Microbot.getClient().getLocalPlayer().getWorldLocation();
                WorldPoint breakPoint = playerPos.dx(1); // Move one tile east
                Rs2Walker.walkTo(breakPoint);
                sleepUntil(() -> !Rs2Player.isInteracting(), 2000);
            }

            sleep(300);
            if (Rs2GameObject.interact(damagedMast, "Repair")) {
                log("Prioritizing mast repair (after fire cleared)");
                Rs2Player.waitForXpDrop(Skill.CONSTRUCTION, 2500);
                
                // Check if a wave is coming and we're not tethered
                if (TemporossPlugin.incomingWave && !TemporossPlugin.isTethered) {
                    log("Wave incoming after mast repair - tethering");
                    handleTether();
                }

                // Restore previous state
                state = previousState;
                return;
            }
        }
    }

    private void handleDamagedTotem() {
        TileObject damagedTotem = workArea.getBrokenTotem();
        if(damagedTotem == null)
            return;

        // Check if within range and have hammer if needed (or using Imcando hammer off-hand)
        if (Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(damagedTotem.getWorldLocation()) <= 6
                && (!temporossConfig.hammer() || temporossConfig.imcandoHammerOffHand() || Rs2Inventory.contains("Hammer"))) {

            // Store current state to return to
            State previousState = state;

            // Force stop current activity
            if (Rs2Player.isInteracting()) {
                // Move one tile away to break interaction
                WorldPoint playerPos = Microbot.getClient().getLocalPlayer().getWorldLocation();
                WorldPoint breakPoint = playerPos.dx(1); // Move one tile east
                Rs2Walker.walkTo(breakPoint);
                sleepUntil(() -> !Rs2Player.isInteracting(), 2000);
            }

            sleep(300);
            if (Rs2GameObject.interact(damagedTotem, "Repair")) {
                log("Prioritizing totem repair");
                // Set the flag to indicate we're repairing a totem
                isRepairingTotem = true;
                Rs2Player.waitForXpDrop(Skill.CONSTRUCTION, 2500);
                // Clear the flag after repair is complete
                isRepairingTotem = false;
                
                // Check if a wave is coming and we're not tethered
                if (TemporossPlugin.incomingWave && !TemporossPlugin.isTethered) {
                    log("Wave incoming after totem repair - tethering");
                    handleTether();
                }

                // Restore previous state
                state = previousState;
                return;
            }
        }
    }

    /**
     * Checks if a wave is incoming and handles tethering immediately if needed
     * This method can be called from any point in the script to interrupt current activity
     * @return true if wave was handled, false otherwise
     */
    public boolean checkAndHandleIncomingWave() {
        if (TemporossPlugin.incomingWave && !TemporossPlugin.isTethered) {
            log("PRIORITY: Wave incoming detected - dropping current activity to tether");
            // Save current state to return to after tethering
            if (TemporossPlugin.previousState == null) {
                TemporossPlugin.previousState = state;
            }
            handleTether();
            return TemporossPlugin.isTethered;
        }
        return false;
    }

    /**
     * Wave-aware version of Rs2Player.waitForWalking() that checks for incoming waves
     * @param timeout maximum time to wait in milliseconds
     * @return true if walking completed or wave was handled, false if timeout
     */
    private boolean waitForWalkingWithWaveCheck(int timeout) {
        long startTime = System.currentTimeMillis();
        while (Rs2Player.isMoving() && (System.currentTimeMillis() - startTime) < timeout) {
            // Check for incoming waves every 100ms during walking
            if (checkAndHandleIncomingWave()) {
                log("Wave detected during walking - tethering took priority");
                return true; // Wave was handled, consider this successful
            }
            sleep(100);
        }
        return !Rs2Player.isMoving(); // Return true if walking completed
    }

    /**
     * Wave-aware version of sleepUntil that checks for incoming waves
     * @param condition the condition to wait for
     * @param timeout maximum time to wait in milliseconds
     * @return true if condition was met or wave was handled, false if timeout
     */
    private boolean sleepUntilWithWaveCheck(BooleanSupplier condition, int timeout) {
        long startTime = System.currentTimeMillis();
        while (!condition.getAsBoolean() && (System.currentTimeMillis() - startTime) < timeout) {
            // Check for incoming waves every 100ms during waiting
            if (checkAndHandleIncomingWave()) {
                log("Wave detected during sleepUntil - tethering took priority");
                return true; // Wave was handled, consider this successful
            }
            sleep(100);
        }
        return condition.getAsBoolean(); // Return true if condition was met
    }
    
    /**
     * Checks if there are damaged structures (mast or totem) and handles repairs immediately if needed
     * This method can be called from any point in the script to interrupt current activity
     * @return true if a repair was handled, false otherwise
     */
    public boolean checkAndHandleDamagedStructures() {
        // Check for damaged mast
        TileObject damagedMast = workArea.getBrokenMast();
        if (damagedMast != null) {
            // Check if within range and have hammer if needed
            if (Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(damagedMast.getWorldLocation()) <= 6
                    && (!temporossConfig.hammer() || temporossConfig.imcandoHammerOffHand() || Rs2Inventory.contains("Hammer"))) {
                
                log("PRIORITY: Damaged mast detected - dropping current activity to repair");
                
                // Store current state to return to
                State previousState = state;
                
                // Call the existing repair method
                handleDamagedMast();
                
                // Restore previous state if it was changed
                if (state != previousState) {
                    state = previousState;
                }
                
                return true;
            }
        }
        
        // Check for damaged totem
        TileObject damagedTotem = workArea.getBrokenTotem();
        if (damagedTotem != null) {
            // Check if within range and have hammer if needed
            if (Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(damagedTotem.getWorldLocation()) <= 6
                    && (!temporossConfig.hammer() || temporossConfig.imcandoHammerOffHand() || Rs2Inventory.contains("Hammer"))) {
                
                log("PRIORITY: Damaged totem detected - dropping current activity to repair");
                
                // Store current state to return to
                State previousState = state;
                
                // Call the existing repair method
                handleDamagedTotem();
                
                // Restore previous state if it was changed
                if (state != previousState) {
                    state = previousState;
                }
                
                return true;
            }
        }
        
        return false;
    }

    public void handleTether() {
        TileObject tether = workArea.getClosestTether();
        if (tether == null) {
            return;
        }
        if (TemporossPlugin.incomingWave != TemporossPlugin.isTethered) {
            if (Rs2Player.isInteracting()) {
                WorldPoint playerPos = Microbot.getClient().getLocalPlayer().getWorldLocation();
                List<WorldPoint> adjacentTiles = Arrays.asList(
                        playerPos.dx(1), playerPos.dx(-1),
                        playerPos.dy(1), playerPos.dy(-1)
                );
                WorldPoint breakPoint = adjacentTiles.get(new Random().nextInt(adjacentTiles.size()));
                Rs2Walker.walkTo(breakPoint);
                sleepUntil(() -> !Rs2Player.isInteracting(), 600);
            }

            ShortestPathPlugin.exit();
            Rs2Walker.setTarget(null);
            String action = TemporossPlugin.incomingWave ? "Tether" : "Untether";
            Rs2Camera.turnTo(tether);

            if (action.equals("Tether")) {
                if (Rs2GameObject.interact(tether, action)) {
                    log(action + "ing - INSTANT PRIORITY");
                    // Make tethering truly instant by setting isTethered to true immediately
                    TemporossPlugin.isTethered = true;
                    // Reduce wait time to absolute minimum (100ms) to ensure near-instant tethering
                    // This is just to allow the game to register the interaction
                    sleepUntil(() -> TemporossPlugin.isTethered == TemporossPlugin.incomingWave, 100);
                    
                    // DELAYED REPAIR: Check for damaged structures after 2-second delay following tethering
                    // This fixes the issue where game doesn't allow repairs while still tethered
                    log("Waiting 2 seconds before checking for damaged structures after tethering");
                    sleep(2000); // 2-second delay to allow game to fully register tethering/untethering before attempting repairs
                    log("Checking for damaged structures after tethering delay");
                    checkAndHandleDamagedStructures();
                }
            }
            if (action.equals("Untether")) {
                // Only attempt to untether if we're actually tethered
                if (TemporossPlugin.isTethered) {
                    log(action + "ing");
                    // Attempt to interact with the tether object to untether
                    if (Rs2GameObject.interact(tether, action)) {
                        log("Interacting with tether to untether");
                        // Wait for the untethering to complete
                        sleepUntil(() -> TemporossPlugin.isTethered == TemporossPlugin.incomingWave, 3500);
                    } else {
                        // If interaction fails, force the state to match to prevent getting stuck
                        log("Failed to interact with tether, forcing state match");
                        TemporossPlugin.isTethered = TemporossPlugin.incomingWave;
                    }
                } else {
                    log("Skipping untethering as player is not tethered");
                    // Force the state to match so we don't get stuck in a loop
                    TemporossPlugin.isTethered = TemporossPlugin.incomingWave;
                }
                
                // After untethering, ensure we reset the incomingWave flag to prevent getting stuck
                if (TemporossPlugin.isTethered == TemporossPlugin.incomingWave) {
                    log("Untethering complete, resetting incomingWave flag");
                    TemporossPlugin.incomingWave = false;
                    
                    // If we have a saved state from wave hit, restore it
                    if (TemporossPlugin.wasHitByWave && TemporossPlugin.previousState != null) {
                        log("Restoring previous state after untethering: " + TemporossPlugin.previousState);
                        
                        // Reset all state-specific flags to ensure the bot doesn't get stuck
                        isFightingFire = false;
                        isRepairingTotem = false;
                        
                        // Special handling for FILL states and COOK states hit by waves
                        // If the previous state was any FILL state, preserve the isFilling flag
                        // This ensures the bot continues filling the ammo crate after being hit by a wave
                        if (TemporossPlugin.previousState == State.EMERGENCY_FILL || 
                            TemporossPlugin.previousState == State.SECOND_FILL || 
                            TemporossPlugin.previousState == State.INITIAL_FILL) {
                            isFilling = true;
                            log("Preserving isFilling flag for " + TemporossPlugin.previousState + " state after wave hit");
                        } 
                        // Special handling for COOK states hit by waves - transition to appropriate CATCH states
                        // Since fish is lost when hit by wave during cooking, bot should go back to catching fish
                        else if (TemporossPlugin.previousState == State.SECOND_COOK || 
                                 TemporossPlugin.previousState == State.THIRD_COOK) {
                            isFilling = false;
                            log("Bot was hit by wave during " + TemporossPlugin.previousState + " - transitioning to CATCH state");
                            
                            // Map cooking states to appropriate catch states since fish is lost
                            if (TemporossPlugin.previousState == State.SECOND_COOK) {
                                // SECOND_COOK loses fish, need to go back to SECOND_CATCH to get fish again
                                state = State.SECOND_CATCH;
                                log("Transitioning from SECOND_COOK to SECOND_CATCH after wave hit");
                            } else if (TemporossPlugin.previousState == State.THIRD_COOK) {
                                // THIRD_COOK loses fish, need to go back to THIRD_CATCH to get fish again
                                state = State.THIRD_CATCH;
                                log("Transitioning from THIRD_COOK to THIRD_CATCH after wave hit");
                            }
                            
                            // Reset the wave hit state since we've handled the transition
                            TemporossPlugin.previousState = null;
                            TemporossPlugin.wasHitByWave = false;
                            TemporossPlugin.lastWaveHitTime = 0;
                            return; // Early return to prevent normal state restoration
                        } 
                        // Special handling for CATCH states hit by waves
                        // When hit by wave during catching, fish is lost so bot should continue with same CATCH state
                        else if (TemporossPlugin.previousState == State.INITIAL_CATCH || 
                                 TemporossPlugin.previousState == State.SECOND_CATCH || 
                                 TemporossPlugin.previousState == State.THIRD_CATCH || 
                                 TemporossPlugin.previousState == State.EMERGENCY_CATCH) {
                            isFilling = false;
                            log("Bot was hit by wave during " + TemporossPlugin.previousState + " - continuing with same CATCH state");
                            
                            // Continue with the same catch state since fish was lost and we need to catch again
                            state = TemporossPlugin.previousState;
                            log("Continuing with " + TemporossPlugin.previousState + " after wave hit");
                            
                            // Reset the wave hit state since we've handled the transition
                            TemporossPlugin.previousState = null;
                            TemporossPlugin.wasHitByWave = false;
                            TemporossPlugin.lastWaveHitTime = 0;
                            return; // Early return to prevent normal state restoration
                        }
                        else {
                            isFilling = false;
                        }
                        
                        log("Reset state flags before restoring state");
                        
                        // Restore the previous state
                        state = TemporossPlugin.previousState;
                        TemporossPlugin.previousState = null;
                        TemporossPlugin.wasHitByWave = false;
                        TemporossPlugin.lastWaveHitTime = 0;
                    }
                }
            }
        }
    }

    private void handleStateLoop() {
        
        temporossPool = Rs2Npc.getNpcs().filter(npc -> npc.getId() == NpcID.TEMPOROSS_P2_FISHINGSPOT).min(Comparator.comparingInt(x -> workArea.spiritPoolPoint.distanceTo(x.getWorldLocation()))).orElse(null);
        boolean doubleFishingSpot = !fishSpots.isEmpty() && fishSpots.get(0).getId() == NpcID.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SPECIAL;

        // Check if target fish is reached for solo mode
        checkTargetFishReached();

        if (TemporossScript.state == State.INITIAL_COOK && doubleFishingSpot) {
            log("Double fishing spot detected, skipping initial cook");
            TemporossScript.state = TemporossScript.state.next;
        }

        if ((TemporossScript.state == State.THIRD_CATCH || TemporossScript.state == State.EMERGENCY_FILL)
            && TemporossScript.ENERGY <= ( isFilling ? 0 : 5)
            && !temporossConfig.solo()) {
            log("Very low energy, better wait on Tempoross pool");
            TemporossScript.state = State.ATTACK_TEMPOROSS;
            return;
        }

        // Handle hover functionality for SECOND_FILL state to allow quicker transition
        if (temporossPool != null && TemporossScript.state == State.SECOND_FILL) {
            log("Tempoross pool detected during SECOND_FILL, hovering over actor for quicker transition");
            Rs2Npc.hoverOverActor(temporossPool);
        }

        if (temporossPool != null && TemporossScript.state != State.SECOND_FILL && TemporossScript.state != State.ATTACK_TEMPOROSS && TemporossScript.ENERGY < 94) {
            log("Tempoross pool detected, attacking Tempoross");
            TemporossScript.state = State.ATTACK_TEMPOROSS;
            return;
        }

        if (((TemporossScript.ENERGY < 30 && State.getAllFish() > 6)
            || (TemporossScript.ENERGY < 50 && State.getAllFish() >= State.getTotalAvailableFishSlots()))
            && !temporossConfig.solo()
            && TemporossScript.state != State.ATTACK_TEMPOROSS) {
            log("Low energy, going for emergency fill");
            TemporossScript.state = State.EMERGENCY_FILL;
        }

    }

    
    private void handleMainLoop() {
        Rs2Camera.setZoom(0); // Set to maximum zoom distance
        Rs2Camera.setPitch(383); // Set to maximum pitch (looking straight down)
        
        // PRIORITY CHECK: If a wave is incoming and we're not tethered, handle tethering immediately
        if (checkAndHandleIncomingWave()) {
            log("Successfully tethered in handleMainLoop, will continue with state: " + state);
            return; // Exit the method after successful tethering
        }


        // Check if we need to transition to EMERGENCY_FILL before handling the current state
        EnergyStateManager.handleEmergencyFillTransition();

        switch (state) {
            case INITIAL_CATCH:
            case SECOND_CATCH:
            case THIRD_CATCH:
            case EMERGENCY_CATCH:
                isFilling = false;
                if (inCloud(Microbot.getClient().getLocalPlayer().getWorldLocation(), 1)) {
                    GameObject cloud = sortedClouds.stream()
                            .findFirst()
                            .orElse(null);
                    if (cloud != null) {
                        Rs2Walker.walkNextToInstance(cloud);
                        waitForWalkingWithWaveCheck(3000);
                        if (inCloud(Microbot.getClient().getLocalPlayer().getWorldLocation(), 1)) {
                            Microbot.log("Current spot is clouded, looking for a better fishing spot...");

                            var playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();

                            var safeFishSpot = fishSpots.stream()
                                    .filter(spot -> !inCloud(spot.getWorldLocation(), 1))
                                    .min(Comparator.comparingInt(spot -> spot.getWorldLocation().distanceTo(playerLocation)))
                                    .orElse(null);

                            if (safeFishSpot != null) {
                                Rs2Camera.turnTo(safeFishSpot);
                                Rs2Npc.interact(safeFishSpot, "Harpoon");
                                Microbot.log("Moved to a " +
                                        (safeFishSpot.getId() == NpcID.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SPECIAL ? "double" : "single") +
                                        " fish spot.");
                                waitForWalkingWithWaveCheck(2000);
                            } else {
                                Microbot.log("No safe fishing spots found. Waiting...");
                            }
                            return;
                        }
                    }
                }

                var fishSpot = fishSpots.stream()
                        .findFirst()
                        .orElse(null);

                if (fishSpot != null) {
                    // In mass world mode, clear fires along the path to the fish spot before interacting.
                    if (!temporossConfig.solo()) {
                        if(!fightFiresInPath(fishSpot.getWorldLocation()))
                            return;
                    }
                    if (Rs2Player.isInteracting() && Rs2Player.getInteracting() != null) {
                        Actor interactingNpc = Microbot.getClient().getLocalPlayer().getInteracting();
                        if (interactingNpc.equals(fishSpot.getActor())) {
                            return;
                        }
                    }
                    Rs2Camera.turnTo(fishSpot);
                    Rs2Npc.interact(fishSpot, "Harpoon");
                    log("Interacting with " + (fishSpot.getId() == NpcID.TEMPOROSS_HARPOONFISH_FISHINGSPOT_SPECIAL ? "double" : "single") + " fish spot");
                    waitForWalkingWithWaveCheck(2000);
                } else {
                    // In mass world mode, clear fires along the path to the totem pole before moving.
                    if (!temporossConfig.solo()) {
                        if(!fightFiresInPath(workArea.totemPoint))
                            return;
                    }
                    LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(),workArea.totemPoint);
                    Rs2Camera.turnTo(localPoint);
                    if (Rs2Camera.isTileOnScreen(localPoint) && Microbot.isPluginEnabled(GpuPlugin.class)) {
                        Rs2Walker.walkFastLocal(localPoint);
                        log("Can't find the fish spot, walking to the totem pole");
                        waitForWalkingWithWaveCheck(2000);
                        return;
                    }
                    log("Can't find the fish spot, walking to the totem pole");
                    if (localPoint != null) {
                        Rs2Walker.walkTo(WorldPoint.fromLocalInstance(Microbot.getClient(),localPoint));
                    }
                    return;
                }
                break;

            case INITIAL_COOK:
            case SECOND_COOK:
            case THIRD_COOK:
                isFilling = false;
                int rawFishCount = Rs2Inventory.count(ItemID.TEMPOROSS_RAW_HARPOONFISH);
                TileObject range = workArea != null ? workArea.getRange() : null;
                
                
                if (range != null && rawFishCount > 0) {
                    if(Rs2Player.isInteracting()) {
                        if (Objects.equals(Rs2Player.getInteracting(), range))
                            return;
                    }
                    if (Rs2Player.isMoving() || Rs2Player.getAnimation() == AnimationID.HUMAN_COOKING) {
                        return;
                    }
                    // Make camera face south (1024) when cooking in solo mode, makes clicking ammo/safe area quicker
                    if (temporossConfig.solo()) {
                        Rs2Camera.setYaw(1024);
                    }
                    Rs2GameObject.interact(range, "Cook-at");
                    log("Interacting with range");
                    sleepUntilWithWaveCheck(Rs2Player::isAnimating, 5000);
                } else if (range == null) {
                    log("Can't find the range, walking to the range point");
                    LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(),workArea.rangePoint);
                    Rs2Camera.turnTo(localPoint);
                    Rs2Walker.walkFastLocal(localPoint);
                    waitForWalkingWithWaveCheck(3000);
                }
                break;

            case EMERGENCY_FILL:
            case SECOND_FILL:
            case INITIAL_FILL:
                
                //If inventory has no fish in SECOND_FILL state, immediately transition to ATTACK_TEMPOROSS (SOLO MODE ONLY), detection was to slow previously or stalled sometimes
                if (state == State.SECOND_FILL && temporossConfig.solo() && State.getAllFish() == 0) {
                    log("SECOND_FILL: Inventory empty of fish - instantly transitioning to ATTACK_TEMPOROSS (solo mode)");
                    if (temporossPool != null) {
                        log("SECOND_FILL: Immediately harpooning Tempoross");
                        Rs2Npc.interact(temporossPool, "Harpoon");
                        state = State.ATTACK_TEMPOROSS;
                        isFilling = false;
                        return;
                    } else {
                        // If tempoross pool not available, force transition and let ATTACK_TEMPOROSS case handle it
                        state = State.ATTACK_TEMPOROSS;
                        isFilling = false;
                        return;
                    }
                }
                
                // For SECOND_FILL state, hover over tempoross pool if available to allow quicker transition to ATTACK_TEMPOROSS
                if (state == State.SECOND_FILL && temporossPool != null) {
                    log("SECOND_FILL: Hovering over Tempoross pool for quicker transition to ATTACK_TEMPOROSS");
                    Rs2Npc.hoverOverActor(temporossPool);
                }
                
                // For solo mode in INITIAL_FILL state, prioritize filling first
                // No need to wait for clouds to turn into fire
                
                List<Rs2NpcModel> ammoCrates = Rs2Npc
                        .getNpcs()
                        .filter(npc -> npc.getComposition() != null && npc.getComposition().getActions() != null && Arrays.asList(npc.getComposition().getActions()).contains("Fill"))
                        .filter(npc -> npc.getWorldLocation().distanceTo(workArea.mastPoint) <= 4)
                        .filter(npc -> !inCloud(npc.getWorldLocation(),1))
                        .map(Rs2NpcModel::new)
                        .collect(Collectors.toList());

                // Check if SECOND_FILL state is complete (no fish left in inventory) - solo mode only
                // If complete, transition to ATTACK_TEMPOROSS instead of continuing to fill
                if (state == State.SECOND_FILL && temporossConfig.solo() && state.isComplete()) {
                    log("SECOND_FILL state is complete (solo mode), transitioning to ATTACK_TEMPOROSS");
                    state = State.ATTACK_TEMPOROSS;
                    isFilling = false;
                    return;
                }

                // Handle clouds in INITIAL_FILL state for solo mode
                if (state == State.INITIAL_FILL && temporossConfig.solo()) {
                    // Check if clouds/lightning shadows are present and we're not already filling
                    if (!sortedClouds.isEmpty() && !isFilling) {
                        log("INITIAL_FILL: Clouds/lightning shadows detected - waiting for them to turn into fire");
                        
                        // Find the closest cloud to wait next to
                        GameObject closestCloud = sortedClouds.stream()
                                .min(Comparator.comparingInt(cloud -> 
                                    Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(cloud.getWorldLocation())))
                                .orElse(null);
                        
                        if (closestCloud != null) {
                            WorldPoint playerLocation = Microbot.getClient().getLocalPlayer().getWorldLocation();
                            int distanceToCloud = playerLocation.distanceTo(closestCloud.getWorldLocation());
                            
                            // Move next to the cloud if not already close
                            if (distanceToCloud > 2) {
                                log("Moving next to cloud at " + closestCloud.getWorldLocation() + " to wait for fire spawn");
                                
                                // Find a safe position adjacent to the cloud (not inside it)
                                WorldPoint cloudLocation = closestCloud.getWorldLocation();
                                WorldPoint safePosition = null;
                                
                                // Check positions around the cloud to find a safe spot
                                for (int dx = -1; dx <= 1; dx++) {
                                    for (int dy = -1; dy <= 1; dy++) {
                                        if (dx == 0 && dy == 0) continue; // Skip the cloud center
                                        
                                        WorldPoint candidatePos = new WorldPoint(
                                            cloudLocation.getX() + dx,
                                            cloudLocation.getY() + dy,
                                            cloudLocation.getPlane()
                                        );
                                        
                                        // Check if this position is not inside any cloud
                                        if (!inCloud(candidatePos, 0)) {
                                            safePosition = candidatePos;
                                            break;
                                        }
                                    }
                                    if (safePosition != null) break;
                                }
                                
                                if (safePosition != null) {
                                    log("Walking to safe position next to cloud: " + safePosition);
                                    Rs2Walker.walkTo(safePosition);
                                    waitForWalkingWithWaveCheck(3000);
                                } else {
                                    log("No safe position found adjacent to cloud, walking to safe point");
                                    walkToSafePoint();
                                }
                            } else {
                                log("Already positioned near cloud, waiting for it to turn into fire");
                            }
                            
                            // Wait by the cloud instead of filling ammo crate
                            return;
                        }
                    } else if (!sortedClouds.isEmpty() && isFilling) {
                        log("INITIAL_FILL: Clouds detected but already filling - continuing with ammo crate interaction");
                    }
                    // If no clouds present, continue with normal filling logic
                    log("INITIAL_FILL: No clouds detected - proceeding with ammo crate filling");
                } else if (inCloud(Microbot.getClient().getLocalPlayer().getWorldLocation(),5) && !isFilling) {
                    // For other states or mass world mode, always avoid cloud
                    GameObject cloud = sortedClouds.stream()
                            .findFirst()
                            .orElse(null);
                    Rs2Walker.walkNextToInstance(cloud);
                    waitForWalkingWithWaveCheck(3000);
                    return;
                }

                if (ammoCrates.isEmpty()) {
                    log("Can't find ammo crate, walking to the safe point");
                    walkToSafePoint();
                    return;
                }

                if (inCloud(Microbot.getClient().getLocalPlayer().getLocalLocation())) {
                    // For all modes and states, switch to furthest ammo crate when in cloud
                    log("In cloud, switching to furthest ammo crate");
                    Rs2NpcModel ammoCrate = ammoCrates.stream()
                            .max(Comparator.comparingInt(value -> new Rs2WorldPoint(value.getWorldLocation()).distanceToPath(Microbot.getClient().getLocalPlayer().getWorldLocation()))).orElse(null);
                    Rs2Camera.turnTo(ammoCrate);
                    Rs2Npc.interact(ammoCrate, "Fill");
                    waitForWalkingWithWaveCheck(5000);
                    isFilling = true;
                    return;
                }

                // For solo mode in INITIAL_FILL state, check if there's fire/cloud above ammo crate
                if (state == State.INITIAL_FILL && temporossConfig.solo() && ammoCrates.size() > 1) {
                    // Check each ammo crate for clouds/fires above it
                    List<Rs2NpcModel> safeAmmoCrates = new ArrayList<>();
                    
                    for (Rs2NpcModel crate : ammoCrates) {
                        boolean hasFireOrCloud = false;
                        
                        // Check for clouds above this ammo crate
                        for (GameObject cloud : sortedClouds) {
                            if (cloud.getWorldLocation().distanceTo(crate.getWorldLocation()) <= 1) {
                                hasFireOrCloud = true;
                                log("Cloud detected above ammo crate during INITIAL_FILL in solo mode");
                                break;
                            }
                        }

                        // If this crate has no fire/cloud, add it to safe crates
                        if (!hasFireOrCloud) {
                            safeAmmoCrates.add(crate);
                        }
                    }
                    
                    // If we found safe ammo crates, use only those
                    if (!safeAmmoCrates.isEmpty()) {
                        log("Using ammo crate without fire/cloud above it");
                        ammoCrates = safeAmmoCrates;
                    } else {
                        log("All ammo crates have fire/cloud above them, using closest one");
                    }
                }
                
                var ammoCrate = ammoCrates.stream()
                        .min(Comparator.comparingInt(value -> new Rs2WorldPoint(value.getWorldLocation()).distanceToPath(Microbot.getClient().getLocalPlayer().getWorldLocation()))).orElse(null);

                // Clear fires along the path to the ammo crate before interacting.
                // For INITIAL_FILL state in solo mode, prioritize filling first
                // For other states or mass world mode, handle fires first
                if ((!temporossConfig.solo() || (state != State.INITIAL_FILL && temporossConfig.solo())) && ammoCrate != null) {
                    if(!fightFiresInPath(ammoCrate.getWorldLocation())) {
                        log("Fires in path to ammo crate, handling fires first");
                        return;
                    }
                } else if (state == State.INITIAL_FILL && temporossConfig.solo() && ammoCrate != null) {
                    // For solo mode in INITIAL_FILL state, we prioritize filling first
                    // Check for fires but don't let them block filling - just log for awareness
                    boolean firesInPath = !fightFiresInPath(ammoCrate.getWorldLocation());
                    if (firesInPath) {
                        log("INITIAL_FILL state: Fires detected in path to ammo crate, but continuing with filling (prioritizing fill over fire handling)");
                    }
                    // Continue with filling regardless of fires in path
                }

                if (Rs2Player.isInteracting()) {
                    if (Objects.equals(Objects.requireNonNull(Rs2Player.getInteracting()).getName(), ammoCrate.getName())) {
                        if(Rs2AntibanSettings.devDebug)
                            log("Interacting with: " + ammoCrate.getName());
                        return;
                    }
                }

                // Check if there are any fish left in the inventory before interacting with the ammo crate
                // If there are no fish left, force a state transition to THIRD_CATCH or ATTACK_TEMPOROSS
                if (state == State.EMERGENCY_FILL && State.getRawFish() == 0) {
                    log("No fish left in inventory, transitioning from EMERGENCY_FILL to THIRD_CATCH");
                    EnergyStateManager.resetEmergencyFillTransition();
                    state = State.THIRD_CATCH;
                    isFilling = false;
                    return;
                }
                
                if (temporossConfig.solo()) {
                    Rs2Camera.setYaw(400);
                    log("Setting camera to face north west for fill action in solo mode");
                } else {
                    Rs2Camera.turnTo(ammoCrate.getActor());
                }
                Rs2Npc.interact(ammoCrate, "Fill");
                log("Interacting with ammo crate");
                Rs2Inventory.waitForInventoryChanges(5000);
                isFilling = true;
                break;

            case ATTACK_TEMPOROSS:
                isFilling = false;
                if (temporossPool != null) {
                    // Clear fires in the path before attacking Tempoross
                    if (!fightFiresInPath(temporossPool.getWorldLocation())) {
                        log("Fires in path to Tempoross, handling fires first.");
                        return;
                    }
                    if (Rs2Player.isInteracting()) {
                        if (ENERGY >= 98) {
                            log("Energy is full, stopping attack");
                            state = null;
                        }
                        return;
                    }
                    int currentSpecEnergy = Rs2Combat.getSpecEnergy() / 10;
                    log("Current Spec Energy: " + currentSpecEnergy);
                    if (temporossConfig.enableHarpoonSpec()
                            && (temporossConfig.harpoonType() == HarpoonType.DRAGON_HARPOON
                            || temporossConfig.harpoonType() == HarpoonType.INFERNAL_HARPOON
                            || temporossConfig.harpoonType() == HarpoonType.CRYSTAL_HARPOON)
                            && currentSpecEnergy >= 100) {
                        Rs2Combat.setSpecState(true, 100);
                        sleep(600);
                        log("Using harpoon special attack at 100% energy");
                    } else {
                        log("Special energy is below 100%, not using harpoon special attack.");
                    }
                    Rs2Npc.interact(temporossPool, "Harpoon");
                    log("Harpooning Tempoross");
                    waitForWalkingWithWaveCheck(2000);
                } else {
                    if (ENERGY > 5) {
                        state = null;
                        return;
                    }
                    log("Can't find Tempoross, walking to the Tempoross pool");
                    walkToSpiritPool();
                }
                break;
        }
    }

    private static WorldPoint getTrueWorldPoint(WorldPoint point) {
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), point);
        assert localPoint != null;
        return WorldPoint.fromLocalInstance(Microbot.getClient(), localPoint);
    }

    /**
     * In mass world mode, before walking to the safe point, clear fires along the path.
     */
    private void walkToSafePoint() {
        if (!temporossConfig.solo()) {
            if(!fightFiresInPath(workArea.safePoint))
                return;
        }
        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(),workArea.safePoint);
        WorldPoint worldPoint = WorldPoint.fromLocalInstance(Microbot.getClient(),localPoint);
        Rs2Camera.turnTo(localPoint);
        if (Rs2Camera.isTileOnScreen(localPoint) && Microbot.isPluginEnabled(GpuPlugin.class)) {
            Rs2Walker.walkFastLocal(localPoint);
            waitForWalkingWithWaveCheck(2000);
        } else {
            Rs2Walker.walkTo(worldPoint);
        }
    }

    /**
     * In mass and solo mode, before walking to the spirit pool, clear fires along the path.
     */
    private void walkToSpiritPool() {
        // Clear fires in path to safe point
        if (!fightFiresInPath(workArea.safePoint))
            return;
        // Clear fires in path to spirit pool point
        if (!fightFiresInPath(workArea.spiritPoolPoint))
            return;

        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(), workArea.spiritPoolPoint);
        Rs2Camera.turnTo(localPoint);
        assert localPoint != null;
        if (Objects.equals(Microbot.getClient().getLocalDestinationLocation(), localPoint) ||
                Objects.equals(Microbot.getClient().getLocalPlayer().getWorldLocation(), workArea.spiritPoolPoint))
            return;
        if (Rs2Camera.isTileOnScreen(localPoint) && Microbot.isPluginEnabled(GpuPlugin.class)) {
            Rs2Walker.walkFastLocal(localPoint);
            waitForWalkingWithWaveCheck(2000);
        } else {
            Rs2Walker.walkTo(getTrueWorldPoint(workArea.spiritPoolPoint));
        }
    }

    private boolean inCloud(LocalPoint point) {
        if(sortedClouds.isEmpty())
            return false;
        GameObject cloud = Rs2GameObject.getGameObject(point);
        return cloud != null && cloud.getId() == ObjectID.TEMPOROSS_LIGHTNING_SHADOW;
    }

    public static boolean inCloud(WorldPoint point, int radius) {
        Rs2WorldArea area = new Rs2WorldArea(point.toWorldArea());
        area = area.offset(radius);
        if(sortedClouds.isEmpty())
            return false;
        Rs2WorldArea finalArea = area;
        return sortedClouds.stream().anyMatch(cloud -> finalArea.contains(cloud.getWorldLocation()));
    }

    // method to fight fires that is in a path to a location
    public boolean fightFiresInPath(WorldPoint location) {
        // Skip fire handling if both energy and essence are low (solo mode only)
        if (temporossConfig.solo() && ENERGY <= 10 && ESSENCE <= 35) {
            log("Solo mode: Both energy (" + ENERGY + "%) and essence (" + ESSENCE + "%) are low, skipping fire handling in path");
            return true; // Return true to allow walking to continue
        }
        
        Rs2WorldPoint playerLocation = new Rs2WorldPoint(Microbot.getClient().getLocalPlayer().getWorldLocation());
        List<WorldPoint> walkerPath = playerLocation.pathTo(location,true);
        walkPath = walkerPath;
        if (sortedFires.isEmpty()) {
            return true;
        }

        int fullBucketCount = Rs2Inventory.count(ItemID.BUCKET_WATER);


        // Filter fires that are actually on the path or near the path
        List<Rs2NpcModel> firesInPath = sortedFires.stream()
                .filter(fire -> {
                    if (!temporossConfig.solo()) {
                        return true; // In mass world, fight all fires
                    }
                    // For INITIAL_FILL state, be much more aggressive with fire handling
                    if (state == State.INITIAL_FILL) {
                        // Check if fire is within 5 tiles of any point on the path
                        // This ensures we handle fires that might be near our destination
                        return walkerPath.stream().anyMatch(pathPoint -> 
                            fire.getWorldLocation().distanceTo(pathPoint) <= 5);
                    } else {
                        // For other states, only handle fires directly on the path or within 2 tiles
                        return walkerPath.stream().anyMatch(pathPoint -> 
                            fire.getWorldArea().contains(pathPoint) || 
                            fire.getWorldLocation().distanceTo(pathPoint) <= 2);
                    }
                })
                .collect(Collectors.toList());

        if (firesInPath.isEmpty()) {
            return true;
        }
        
        // Sort fires by distance to player to handle the closest fires first
        firesInPath.sort((fire1, fire2) -> {
            int dist1 = playerLocation.distanceToPath(fire1.getWorldLocation());
            int dist2 = playerLocation.distanceToPath(fire2.getWorldLocation());
            return Integer.compare(dist1, dist2);
        });

        // Limit the number of fires doused based on available full buckets.
        if (firesInPath.size() > fullBucketCount) {
            firesInPath = firesInPath.subList(0, fullBucketCount);
        }

        for (Rs2NpcModel fire : firesInPath) {
            if (Rs2Npc.interact(fire, "Douse")) {
                log("Dousing fire in path" + (temporossConfig.solo() ? "" : " (mass world mode)"));
                sleepUntil(Rs2Player::isInteracting, 2000);
                sleepUntil(() -> !Rs2Player.isInteracting(), 10000);
            }
        }

        // Return true if sortedFires does not contain any fires in the path.
        return sortedFires.stream().noneMatch(fire -> walkerPath.stream().anyMatch(pathPoint -> fire.getWorldArea().contains(pathPoint)));
    }

    @Override
    public void shutdown() {
        super.shutdown();
        reset();
        BreakHandlerScript.setLockState(false);
        // Any cleanup code here
    }
}