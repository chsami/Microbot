```java
package net.runelite.client.plugins.microbot.tempoross;

import net.runelite.api.*;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
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
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.api.ItemID;


import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.Microbot.log;

public class TemporossScript extends Script {

    // Version string
    public static final String VERSION = "1.4.0";
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
    public static HarpoonType harpoonType;
    public static Rs2NpcModel temporossPool;
    public static List<Rs2NpcModel> sortedFires = new ArrayList<>();
    public static List<GameObject> sortedClouds = new ArrayList<>();
    public static List<Rs2NpcModel> fishSpots = new ArrayList<>();
    public static List<WorldPoint> walkPath = new ArrayList<>();

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
        Rs2AntibanSettings.takeMicroBreaks = true;
        Rs2AntibanSettings.microBreakChance = 0.2;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->{
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                if (!isInMinigame()) {
                    handleEnterMinigame();
                }
                if (isInMinigame()) {
                    if (workArea == null) {
                        determineWorkArea();
                        sleep(300, 600);
                    } else {

                        handleMinigame();
                        handleStateLoop();
                        if(areItemsMissing())
                            return;
                        // In solo mode, continuously handle fires.
                        // In mass world mode, fire-fighting is now handled dynamically before objectives.
                        handleFires();
                        handleTether();
                        if(isFightingFire || TemporossPlugin.isTethered || TemporossPlugin.incomingWave)
                            return;
                        handleDamagedMast();
                        handleDamagedTotem();
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
        return Rs2Inventory.contains(harpoonType.getId()) || Rs2Equipment.hasEquipped(harpoonType.getId());
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
            int emptyBucketCount = Rs2Inventory.count(ItemID.BUCKET);
            if (emptyBucketCount > 0) {
                if(Rs2GameObject.interact(41004, "Fill-bucket"))
                    sleepUntil(() -> Rs2Inventory.count(ItemID.BUCKET) < 1);

            }

            if (Rs2Npc.interact(exitNpc, "Leave")) {
                reset();
                sleepUntil(() -> !isInMinigame(), 15000);
                Rs2Antiban.takeMicroBreakByChance();
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
        BreakHandlerScript.setLockState(false);
    }

    public void handleForfeit() {
        if ((INTENSITY >= 94 && state == State.THIRD_COOK)) {
            var forfeitNpc = Rs2Npc.getNearestNpcWithAction("Forfeit");
            if (forfeitNpc != null) {
                if (Rs2Npc.interact(forfeitNpc, "Forfeit")) {
                    sleepUntil(() -> !isInMinigame(), 15000);
                    reset();
                }
            }
        }
    }

    private void forfeit() {
        var forfeitNpc = Rs2Npc.getNearestNpcWithAction("Forfeit");
        if (forfeitNpc != null) {
            if (Rs2Npc.interact(forfeitNpc, "Forfeit")) {
                sleepUntil(() -> !isInMinigame(), 15000);
                reset();
            }
        }
    }

private void handleMinigame() {
    if (getPhase() > 2) {
        return;
    }
    
    harpoonType = temporossConfig.harpoonType();
    if (areItemsMissing()) {
        fetchMissingItems();
        return;
    }
    
    handleStateLoop();
}

    private boolean areItemsMissing() {
        // Check for harpoon
        if (!hasHarpoon() && harpoonType != HarpoonType.BAREHAND) {
            return true;
        }

        // Check bucket counts (empty or full)
        int bucketCount = Rs2Inventory.count(item ->
                item.getId() == ItemID.BUCKET || item.getId() == ItemID.BUCKET_OF_WATER);
        if ((bucketCount < temporossConfig.buckets() && state == State.INITIAL_CATCH) || bucketCount == 0) {
            return true;
        }

        // Check full buckets of water
        if (Rs2Inventory.count(ItemID.BUCKET_OF_WATER) <= 0) {
            return true;
        }

        // Check for rope
        if (temporossConfig.rope() && !temporossConfig.spiritAnglers() && !Rs2Inventory.contains(ItemID.ROPE)) {
            return true;
        }

        // Check for hammer
        if (temporossConfig.hammer()
                && !(Rs2Inventory.contains(ItemID.IMCANDO_HAMMER)
                || Rs2Inventory.contains(ItemID.HAMMER)
                || (temporossConfig.imcandoHammerOffHand()
                && (Rs2Inventory.contains(ItemID.IMCANDO_HAMMER_OFFHAND)
                || Rs2Equipment.hasEquipped(ItemID.IMCANDO_HAMMER_OFFHAND))))) {
            return true;
        }
        
        return false; // Add this return statement
    }

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
                item.getId() == ItemID.BUCKET || item.getId() == ItemID.BUCKET_OF_WATER);
        if ((bucketCount < temporossConfig.buckets() && state == State.INITIAL_CATCH) || bucketCount == 0)
        {
            log("Buckets: " + bucketCount);

            // Before interacting, clear fires along the path to the bucket crate.
            if (!fightFiresInPath(workArea.bucketPoint))
            {
                log("Could not douse fires in path to bucket crate, forfeiting");
                forfeit();
                return;
            }
            sleepUntil(() -> Rs2Inventory.count(item ->
                    item.getId() == ItemID.BUCKET || item.getId() == ItemID.BUCKET_OF_WATER) >= temporossConfig.buckets(),() -> {
                if (Rs2GameObject.interact(workArea.getBucketCrate(), "Take")) {
                    log("Taking buckets");
                    Rs2Inventory.waitForInventoryChanges(3000);
                }},10000,300);


            return;
        }

        // 3) Fill Buckets
        int fullBucketCount = Rs2Inventory.count(ItemID.BUCKET_OF_WATER);
        if (fullBucketCount <= 0)
        {
            // Before interacting, clear fires along the path to the pump.
            if (!fightFiresInPath(workArea.pumpPoint))
            {
                log("Could not douse fires in path to pump, forfeiting");
                forfeit();
                return;
            }

            if (Rs2GameObject.interact(workArea.getPump(), "Use"))
            {
                log("Filling buckets");
                sleepUntil(() -> Rs2Inventory.count(ItemID.BUCKET) <= 0, 10000);
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
                sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(10000));
            }
            return;
        }

        // 5) Hammer (if required)
        if (temporossConfig.hammer()
            && !Rs2Inventory.contains(ItemID.HAMMER)
            && !Rs2Inventory.contains(ItemID.IMCANDO_HAMMER)
            && (!temporossConfig.imcandoHammerOffHand() || !Rs2Inventory.contains(ItemID.IMCANDO_HAMMER_OFFHAND)))
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
        TileObject startingLadder = Rs2GameObject.findObjectById(ObjectID.ROPE_LADDER_41305);
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
        TileObject startingLadder = Rs2GameObject.findObjectById(ObjectID.ROPE_LADDER_41305);
        if (startingLadder == null) {
            log("Failed to find starting ladder");
            return;
        }
        int emptyBucketCount = Rs2Inventory.count(ItemID.BUCKET);
        // If we are east of the ladder, interact with it to get on the boat
        if (!isOnStartingBoat()) {
            if (Rs2GameObject.interact(startingLadder, ((emptyBucketCount > 0 && temporossConfig.solo()) || !temporossConfig.solo()) ? "Climb" : "Solo-start")) {
                BreakHandlerScript.setLockState(true);
                sleepUntil(() -> (isOnStartingBoat() || isInMinigame()), 15000);
                return;
            }
        }

        TileObject waterPump = Rs2GameObject.findObjectById(ObjectID.WATER_PUMP_41000);

        if (waterPump != null && emptyBucketCount > 0) {
            if (Rs2GameObject.interact(waterPump, "Use")) {
                Rs2Player.waitForAnimation(5000);
            }
        }
        sleepUntil(TemporossScript::isInMinigame, 30000);
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
                .filter(obj -> obj.getId() == NullObjectID.NULL_41006)
                .collect(Collectors.toList());
        Rs2WorldPoint playerLocation = new Rs2WorldPoint(Microbot.getClient().getLocalPlayer().getWorldLocation());
        sortedClouds = allClouds.stream()
                .filter(y -> playerLocation.distanceToPath(y.getWorldLocation()) < 30)
                .sorted(Comparator.comparingInt(x -> playerLocation.distanceToPath(x.getWorldLocation())))
                .collect(Collectors.toList());
        TemporossOverlay.setCloudList(sortedClouds);
    }

    // update ammocrate data
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
                .filter(npc -> npc.getId() == NpcID.FISHING_SPOT_10569 || npc.getId() == NpcID.FISHING_SPOT_10568 || npc.getId() == NpcID.FISHING_SPOT_10565)
                .filter(npc -> !inCloud(npc.getRuneliteNpc().getWorldLocation(),2))
                .filter(npc -> npc.getWorldLocation().distanceTo(workArea.rangePoint) <= 20)
                .sorted(Comparator
                        .comparingInt(npc -> npc.getId() == NpcID.FISHING_SPOT_10569 ? 0 : 1))
                .collect(Collectors.toList());
        TemporossOverlay.setFishList(fishSpots);
    }

    public static void updateLastWalkPath() {
        TemporossOverlay.setLastWalkPath(walkPath);
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
        isFightingFire = true;
        for (Rs2NpcModel fire : sortedFires) {
            if(isFilling){
                Microbot.log("Filling, skipping fire");
                return;
            }
            if (Rs2Player.isInteracting()) {
                if (Objects.equals(Rs2Player.getInteracting(), fire)) {
                    return;
                }
            }
            if (Rs2Npc.interact(fire, "Douse")) {
                log("Dousing fire");
                sleepUntil(() -> !Rs2Player.isInteracting(), 3000);
                return;
            }
        }
    }

    private void handleDamagedMast() {
        if (Rs2Player.isMoving() || Rs2Player.isInteracting() || !hasValidHammer())
            return;

        TileObject damagedMast = workArea.getBrokenMast();
        if (damagedMast == null)
            return;

        if (Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(damagedMast.getWorldLocation()) <= 5) {
            sleep(600);
            if (Rs2GameObject.interact(damagedMast, "Repair")) {
                log("Repairing mast");
                Rs2Player.waitForXpDrop(Skill.CONSTRUCTION, 2500);
            }
        }
    }

    private void handleDamagedTotem() {
        if (Rs2Player.isMoving() || Rs2Player.isInteracting() || !hasValidHammer())
            return;

        TileObject damagedTotem = workArea.getBrokenTotem();
        if (damagedTotem == null)
            return;

        if (Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(damagedTotem.getWorldLocation()) <= 5) {
            sleep(600);
            if (Rs2GameObject.interact(damagedTotem, "Repair")) {
                log("Repairing totem");
                Rs2Player.waitForXpDrop(Skill.CONSTRUCTION, 2500);
            }
        }
    }

    // ✅ Helper method goes here
    private boolean hasValidHammer() {
        if (!temporossConfig.hammer()) {
            return true; // No hammer required
        }

        return Rs2Inventory.contains(ItemID.IMCANDO_HAMMER)
            || Rs2Inventory.contains(ItemID.HAMMER)
            || (temporossConfig.imcandoHammerOffHand() &&
                (Rs2Inventory.contains(ItemID.IMCANDO_HAMMER_OFFHAND)
                 || Rs2Equipment.hasEquipped(ItemID.IMCANDO_HAMMER_OFFHAND)));
    }

    // ... other methods, etc.
    }

    private void handleTether() {
        TileObject tether = workArea.getClosestTether();
        if (tether == null) {
            return;
        }
        if (TemporossPlugin.incomingWave != TemporossPlugin.isTethered) {
            ShortestPathPlugin.exit();
            Rs2Walker.setTarget(null);
            String action = TemporossPlugin.incomingWave ? "Tether" : "Untether";
            Rs2Camera.turnTo(tether);

            if (action.equals("Tether")) {
                if (Rs2GameObject.interact(tether, action)) {
                    log(action + "ing");
                    sleepUntil(() -> TemporossPlugin.isTethered == TemporossPlugin.incomingWave, 3500);
                }
            }
            if (action.equals("Untether")) {
                log(action + "ing");
                sleepUntil(() -> TemporossPlugin.isTethered == TemporossPlugin.incomingWave, 3500);
            }
        }
    }

    private void handleStateLoop() {
        temporossPool = Rs2Npc.getNpcs().filter(npc -> npc.getId() == NpcID.SPIRIT_POOL).min(Comparator.comparingInt(x -> workArea.spiritPoolPoint.distanceTo(x.getWorldLocation()))).orElse(null);
        boolean doubleFishingSpot = !fishSpots.isEmpty() && fishSpots.get(0).getId() == NpcID.FISHING_SPOT_10569;

        if (TemporossScript.state == State.INITIAL_COOK && doubleFishingSpot) {
            log("Double fishing spot detected, skipping initial cook");
            TemporossScript.state = TemporossScript.state.next;
        }

        if(TemporossScript.ENERGY < 30 && State.getAllFish() > 6 && !temporossConfig.solo() && TemporossScript.state != State.ATTACK_TEMPOROSS) {
            TemporossScript.state = State.EMERGENCY_FILL;
        }
        if (TemporossScript.ENERGY == 0 && !temporossConfig.solo() && TemporossScript.state != State.ATTACK_TEMPOROSS && TemporossScript.state != State.INITIAL_CATCH) {
            TemporossScript.state = State.ATTACK_TEMPOROSS;
        }
        if (temporossPool != null && TemporossScript.state != State.SECOND_FILL && TemporossScript.state != State.ATTACK_TEMPOROSS && TemporossScript.ENERGY < 94) {
            TemporossScript.state = State.ATTACK_TEMPOROSS;
        }

    }

private void handleMainLoop() {
    Rs2Camera.resetZoom();
    Rs2Camera.resetPitch();
    
    if (state == null) {
        return;
    }

    switch (state) {
        case INITIAL_CATCH:
        case SECOND_CATCH:
        case THIRD_CATCH:
            handleCatchPhase();
            break;
            
        case INITIAL_COOK:
        case SECOND_COOK:
        case THIRD_COOK:
            handleCookPhase(); 
            break;
            
        case EMERGENCY_FILL:
        case SECOND_FILL:
        case INITIAL_FILL:
            handleFillPhase();
            break;
            
        case ATTACK_TEMPOROSS:
            handleAttackPhase();
            break;
            
        default:
            log("Unknown state: " + state);
            break;
    }
}

private void handleAttackPhase() {
}

private boolean inCloud(WorldPoint point, int range) {
        return sortedClouds.stream().anyMatch(cloud -> cloud.getWorldLocation().distanceTo(point) < range);
    }

    private void handleCloudEvasion() {
        log("Evading cloud");
        Rs2WorldPoint safePoint = new Rs2WorldPoint(Microbot.getClient().getLocalPlayer().getWorldLocation()).approaching(2);

        //walkPath = Rs2Walker.buildPath(safePoint);
        Rs2Walker.walkTo(safePoint);
        sleepUntil(() -> !inCloud(Microbot.getClient().getLocalPlayer().getWorldLocation(), 5), 2000);
    }

    private void handleFishing() {
        updateFishSpotData();
        if (fishSpots.isEmpty()) {
            walkToSafePoint();
            return;
        }
        Rs2NpcModel fishingSpot = fishSpots.get(0);
        if (!Rs2Player.isInteracting()) {
            Rs2Camera.turnTo(fishingSpot);
            if (Rs2Npc.interact(fishingSpot, "Harpoon")) {
                log("Fishing");
                Rs2Player.waitForAnimation();
                sleepUntil(() -> Rs2Player.isAnimating() || Rs2Inventory.isInventoryFull(), 15000);
                state = state.next;
            }
        }
    }
    private List<Rs2NpcModel> getAvailableAmmoCrates() {
        updateAmmoCrateData();
        return TemporossOverlay.ammoList;
    }

    private void walkToSafePoint() {
        if (Rs2Player.isMoving())
            return;
        Rs2WorldPoint safePoint = new Rs2WorldPoint(workArea.safePoint);

        walkPath = Rs2Walker.buildPath(safePoint);
        updateLastWalkPath();
        Rs2Walker.walkTo(safePoint);
        log("Walking to safe point: " + safePoint.getX() + ", " + safePoint.getY());
        sleep(100, 200);
    }

    private void handleAmmoCrateInteraction(List<Rs2NpcModel> ammoCrates) {
        if (Rs2Player.isMoving() || Rs2Player.isInteracting())
            return;
        Rs2NpcModel ammoCrate = ammoCrates.get(0);

        if (ammoCrate == null) {
            walkToSafePoint();
            return;
        }

        if (Rs2Player.getWorldLocation().distanceTo(ammoCrate.getWorldLocation()) <= 5) {
            Rs2Camera.turnTo(ammoCrate);
            if (Rs2Npc.interact(ammoCrate, "Fill")) {
                isFilling = true;
                log("Filling");
                sleepUntil(() -> ESSENCE >= 70 || Rs2Player.isInteracting(), 10000);
                state = state.next;
            }
        } else {
            walkToSafePoint();
        }
    }

    private void handleCooking(TileObject range) {
        if (Rs2Player.isMoving() || Rs2Player.isInteracting())
            return;

        if (Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(range.getWorldLocation()) <= 5) {
            if (Rs2GameObject.interact(range, "Cook")) {
                Rs2Player.waitForAnimation(5000);
                log("Cooking");
                sleepUntil(() -> Rs2Inventory.isInventoryFull(), 25000);
                state = state.next;
            }
        } else {
            walkToRange();
        }
    }

    private void walkToRange() {
        if (Rs2Player.isMoving())
            return;
        TileObject range = workArea.getRange();

        if (range == null) {
            walkToSafePoint();
            return;
        }

        Rs2WorldPoint rangePoint = new Rs2WorldPoint(range.getWorldLocation());

        walkPath = Rs2Walker.buildPath(rangePoint);
        updateLastWalkPath();
        Rs2Walker.walkTo(rangePoint);
        log("Walking to range: " + rangePoint.getX() + ", " + rangePoint.getY());
        sleep(100, 200);
    }

    /**
     * Walks the player through any existing fires between their current position
     * and the specified world point.
     * This does not handle pathfinding around fires; the caller is responsible for providing
     * a world point that's actually reachable by the player.
     *
     * @param destination the world point we want to go to
     * @return true if the player successfully walked through all fires in their path;
     * false if the fires could not be doused
     */
    private boolean fightFiresInPath(WorldPoint destination) {
        // Iterate through and douse any fires in the way to our destination.
        WorldPoint startPoint = Microbot.getClient().getLocalPlayer().getWorldLocation();
        double distance = startPoint.distanceTo(destination);
        final int fireRange = 4;

        while (true)
        {
            updateFireData();
            Optional<Rs2NpcModel> fire = sortedFires.stream()
                    .filter(x -> x.getWorldLocation().distanceTo(destination) <= distance)
                    .filter(x -> x.getWorldLocation().distanceTo(startPoint) <= distance)
                    .filter(x -> Rs2Player.getWorldLocation().distanceTo(x.getWorldLocation()) <= fireRange)
                    .findFirst();

            if (fire.isEmpty())
            {
                // No fires detected in the path.
                break