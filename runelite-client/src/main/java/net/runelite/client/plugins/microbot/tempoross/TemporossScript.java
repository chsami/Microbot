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

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.Microbot.log;

public class TemporossScript extends Script {

    // Version string
    public static final String VERSION = "1.2.0";
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
    static final Predicate<Rs2NpcModel> filterDangerousNPCs = npc -> !inCloud(Rs2WorldPoint.toLocalInstance(npc.getWorldLocation()),2);



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
        return 1 + (TemporossPlugin.waves / 3); // every 3 waves, phase increases by 1
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
        Rs2NpcModel exitNpc = Rs2Npc.getNearestNpcWithAction("Leave");
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
                }
            }
        }
    }

    private void handleMinigame() {


        if (getPhase() >= 2)
            return;

        harpoonType = temporossConfig.harpoonType();

        if (!hasHarpoon()) {
            // If we don't have a harpoon, set to the default harpoon that we can pick up
            log("Missing selected harpoon, setting to default harpoon");
            harpoonType = HarpoonType.HARPOON;
            TemporossPlugin.setHarpoonType(harpoonType);

            if (Rs2Player.isMoving() || Rs2Player.isInteracting())
                return;

            if (Rs2GameObject.interact(workArea.getHarpoonCrate(), "Take")) {
                log("Taking harpoon");
                sleepUntil(this::hasHarpoon, 10000);
            }
            return;
        }

        // Get bucket counts (empty and full)
        int bucketCount = Rs2Inventory.count(item -> item.getId() == ItemID.BUCKET || item.getId() == ItemID.BUCKET_OF_WATER);
        if ((bucketCount < temporossConfig.buckets() && state == State.INITIAL_CATCH) || bucketCount == 0) {
            log("Buckets: " + bucketCount);
            if (Rs2Player.isMoving() || Rs2Player.isInteracting())
                return;

            if (Rs2GameObject.interact(workArea.getBucketCrate(), "Take")) {
                log("Taking buckets");
                sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(10000));
            }
            return;
        }

        int fullBucketCount = Rs2Inventory.count(ItemID.BUCKET_OF_WATER);
        if (fullBucketCount <= 0) {
            if (Rs2Player.isMoving() || Rs2Player.isInteracting())
                return;

            if (Rs2GameObject.interact(workArea.getPump(), "Use")) {
                log("Filling buckets");
                sleepUntil(() -> Rs2Inventory.count(ItemID.BUCKET) <= 0, 10000);
            }
            return;
        }

        if (temporossConfig.rope() && !temporossConfig.spiritAnglers() && !Rs2Inventory.contains(ItemID.ROPE)) {
            if (Rs2Player.isMoving() || Rs2Player.isInteracting())
                return;

            if (Rs2GameObject.interact(workArea.getRopeCrate(), "Take")) {
                log("Taking rope");
                sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(10000));
            }
            return;
        }

        if (temporossConfig.hammer() && !Rs2Inventory.contains(ItemID.HAMMER)) {
            if (Rs2Player.isMoving() || Rs2Player.isInteracting())
                return;

            if (Rs2GameObject.interact(workArea.getHammerCrate(), "Take")) {
                log("Taking hammer");
                sleepUntil(() -> Rs2Inventory.waitForInventoryChanges(10000));
            }
        }
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
        if (Rs2Player.getWorldLocation().getX() > startingLadder.getWorldLocation().getX()) {
            if (Rs2GameObject.interact(startingLadder, ((emptyBucketCount > 0 && temporossConfig.solo()) || !temporossConfig.solo()) ? "Climb" : "Solo-start")) {
                BreakHandlerScript.setLockState(true);
                Rs2Player.waitForWalking();
                return;
            }
        }


        TileObject waterPump = Rs2GameObject.findObjectById(ObjectID.WATER_PUMP_41000);

        if (waterPump != null && emptyBucketCount > 0) {
            if (Rs2GameObject.interact(waterPump, "Use")) {
                Rs2Player.waitForAnimation(5000);
            }
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
                .filter(y -> playerLocation.distanceToPath(Rs2WorldPoint.toLocalInstance(y.getNpc().getWorldLocation())) < 35)
                .sorted(Comparator.comparingInt(x -> playerLocation.distanceToPath( Rs2WorldPoint.toLocalInstance(x.getNpc().getWorldLocation()))))
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
                .filter(npc -> !inCloud(Rs2WorldPoint.toLocalInstance(npc.getWorldLocation()),2))
                .map(Rs2NpcModel::new)
                .collect(Collectors.toList());
        TemporossOverlay.setAmmoList(ammoCrates);
    }

    public static void updateFishSpotData(){
        // if double fishing spot is present, prioritize it
        fishSpots = Rs2Npc.getNpcs()
                .filter(npc -> npc.getId() == NpcID.FISHING_SPOT_10569 || npc.getId() == NpcID.FISHING_SPOT_10568 || npc.getId() == NpcID.FISHING_SPOT_10565)
                .filter(npc -> !inCloud(npc.getNpc().getWorldLocation(),2))
                .filter(npc -> npc.getWorldLocation().distanceTo(workArea.rangePoint) <= 20)
                .sorted(Comparator
                        .comparingInt(npc -> npc.getId() == NpcID.FISHING_SPOT_10569 ? 0 : 1))
                .collect(Collectors.toList());
        TemporossOverlay.setFishList(fishSpots);
    }

    private void handleFires() {

        if (sortedFires.isEmpty() || isFilling || state == State.ATTACK_TEMPOROSS) {
            isFightingFire = false;
            return;
        }
            isFightingFire = true;
            for (Rs2NpcModel fire : sortedFires) {
                if (Rs2Player.isInteracting()) {
                    if (Microbot.getClient().getLocalPlayer().getInteracting().equals(fire.getActor())) {
                        return;
                    }
                }
                if (Rs2Npc.interact(fire)) {
                    sleepUntil(() -> !Rs2Player.isInteracting(), 1000);
                    return;
                }
            }
        }


    private void handleDamagedMast() {
        if (Rs2Player.isMoving() || Rs2Player.isInteracting() || (temporossConfig.hammer() && !Rs2Inventory.contains("Hammer")) || !temporossConfig.hammer())
            return;

        TileObject damagedMast = workArea.getBrokenMast();
        if(damagedMast == null)
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
        if (Rs2Player.isMoving() || Rs2Player.isInteracting() || (temporossConfig.hammer() && !Rs2Inventory.contains("Hammer")) || !temporossConfig.hammer())
            return;

        TileObject damagedTotem = workArea.getBrokenTotem();
        if(damagedTotem == null)
            return;
        if (Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(damagedTotem.getWorldLocation()) <= 5) {
            sleep(600);
            if (Rs2GameObject.interact(damagedTotem, "Repair")) {
                log("Repairing totem");
                Rs2Player.waitForXpDrop(Skill.CONSTRUCTION, 2500);
            }
        }
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
                    //sleep(600,1200);

            }
        }
    }

    private void handleStateLoop() {
        temporossPool = Rs2Npc.getNpc(NpcID.SPIRIT_POOL);
        Rs2NpcModel inactivePool = Rs2Npc.getNpc(NpcID.INACTIVE_SPIRIT_POOL);
        boolean doubleFishingSpot = !fishSpots.isEmpty() && fishSpots.get(0).getId() == NpcID.FISHING_SPOT_10569;
        if (TemporossScript.state.isComplete()) {
            if (TemporossScript.state == null) {
                log("Script looped, resetting to third catch");
                TemporossScript.state = State.THIRD_CATCH;
            }
        }
        if (TemporossScript.state == State.INITIAL_COOK && doubleFishingSpot) {
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
        switch (state) {
            case INITIAL_CATCH:
            case SECOND_CATCH:
            case THIRD_CATCH:
                isFilling = false;
                if (inCloud(Microbot.getClient().getLocalPlayer().getWorldLocation(),5)) {
                    GameObject cloud = sortedClouds.stream()
                            .findFirst()
                            .orElse(null);
                    Rs2Walker.walkNextToInstance(cloud);
                    Rs2Player.waitForWalking();
                    return;
                }

                var fishSpot = fishSpots.stream()
                        .findFirst()
                        .orElse(null);


                if (fishSpot != null) {
                    if (Rs2Player.isInteracting()) {
                        if (Microbot.getClient().getLocalPlayer().getInteracting().equals(fishSpot.getActor())) {
                            return;
                        }
                    }
                    Rs2Camera.turnTo(fishSpot);
                    Rs2Npc.interact(fishSpot, "Harpoon");
                    log("Interacting with " + (fishSpot.getId() == NpcID.FISHING_SPOT_10569 ? "double" : "single") + " fish spot");
                    Rs2Player.waitForWalking(2000);
                } else {
                    if(!sortedFires.isEmpty())
                        return;
                    WorldPoint pl = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(), workArea.totemPoint).stream().findFirst().orElse(null);

                    LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(),pl);
                    Rs2Camera.turnTo(localPoint);
                    if (Rs2Camera.isTileOnScreen(localPoint) && Microbot.isPluginEnabled(GpuPlugin.class)) {
                        Rs2Walker.walkFastLocal(localPoint);
                        log("Can't find the fish spot, walking to the totem pole");
                        Rs2Player.waitForWalking(2000);
                        return;
                    }
                    log("Can't find the fish spot, walking to the totem pole");
                    Rs2Walker.walkTo(WorldPoint.fromLocalInstance(Microbot.getClient(),localPoint));
                    return;

                }
                return;

            case INITIAL_COOK:
            case SECOND_COOK:
            case THIRD_COOK:
                isFilling = false;
                int rawFishCount = Rs2Inventory.count(ItemID.RAW_HARPOONFISH);
                TileObject range = workArea.getRange();
                if (range != null && rawFishCount > 0) {
                    if(Rs2Player.isInteracting()) {
                        if (Microbot.getClient().getLocalPlayer().getInteracting().equals(range))
                            return;
                    }
                    if (Rs2Player.isMoving() || Rs2Player.getAnimation() == AnimationID.COOKING_RANGE) {
                        return;
                    }
                    Rs2GameObject.interact(range, "Cook-at");
                    log("Interacting with range");
                    sleepUntil(Rs2Player::isAnimating, 5000);
                } else if (range == null) {
                    log("Can't find the range, walking to the range point");
                    WorldPoint pl = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(), workArea.rangePoint).stream().findFirst().orElse(null);

                    LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(),pl);
                    Rs2Camera.turnTo(localPoint);
                    Rs2Walker.walkFastLocal(localPoint);
                    Rs2Player.waitForWalking(3000);
                }
                break;

            case EMERGENCY_FILL:
            case SECOND_FILL:
            case INITIAL_FILL:
                List<Rs2NpcModel> ammoCrates = Rs2Npc
                        .getNpcs()
                        .filter(value -> value.getComposition() != null
                                && value.getComposition().getActions() != null
                                && Arrays.asList(value.getComposition().getActions()).contains("Fill"))
                        .filter(npc -> npc.getWorldLocation().distanceTo(workArea.mastPoint) <= 4)
                        .filter(npc -> !inCloud(Rs2WorldPoint.toLocalInstance(npc.getWorldLocation()),2))
                        .collect(Collectors.toList());

                if (inCloud(Microbot.getClient().getLocalPlayer().getLocalLocation())) {
                    log("In cloud, walking to safe point");
                    Rs2NpcModel ammoCrate = ammoCrates.stream()
                            .max(Comparator.comparingInt(value -> new Rs2WorldPoint(Rs2WorldPoint.toLocalInstance(value.getWorldLocation())).distanceToPath(Microbot.getClient().getLocalPlayer().getWorldLocation()))).orElse(null);
                    if (ammoCrate != null) {

                        Rs2Camera.turnTo(ammoCrate);
                        Rs2Npc.interact(ammoCrate, "Fill");
                        log("Switching ammo crate");
                        Rs2Player.waitForWalking(5000);
                        isFilling = true;
                        return;
                    }
                    return;
                }

                var ammoCrate =ammoCrates.stream()
                        .min(Comparator.comparingInt(value -> new Rs2WorldPoint(Rs2WorldPoint.toLocalInstance(value.getWorldLocation())).distanceToPath(Microbot.getClient().getLocalPlayer().getWorldLocation()))).orElse(null);

                if (ammoCrate != null) {
                    if (Rs2Player.isInteracting()) {
                        if (Objects.equals(Microbot.getClient().getLocalPlayer().getInteracting().getName(), ammoCrate.getActor().getName())) {
                            return;
                        }
                    }
                    Rs2Walker.setTarget(null);
                    Rs2Camera.turnTo(ammoCrate);


                    Rs2Npc.interact(ammoCrate, "Fill");
                    log("Interacting with ammo crate");
                    isFilling = true;
                    Rs2Player.waitForWalking(3000);
                } else {
                    log("Can't find the ammo crate, walking to safe point");
                    walkToSafePoint();
                }
                break;

            case ATTACK_TEMPOROSS:
                isFilling = false;
                if (temporossPool != null) {
                    if (Rs2Player.isInteracting()) {
                        if (ENERGY >= 95) {
                            log("Energy is full, stopping attack");
                            state = null;
                        }
                        return;
                    }
                    Rs2Npc.interact(temporossPool, "Harpoon");
                    log("Attacking Tempoross");
                    Rs2Player.waitForWalking(2000);
                } else {
                    if (ENERGY > 0) {
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

    private void walkToSafePoint() {
        WorldPoint pl = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(), workArea.safePoint).stream().findFirst().orElse(null);

        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(),pl);
        WorldPoint worldPoint = WorldPoint.fromLocalInstance(Microbot.getClient(),localPoint);
        Rs2Camera.turnTo(localPoint);
        if (Rs2Camera.isTileOnScreen(localPoint) && Microbot.isPluginEnabled(GpuPlugin.class)) {
            Rs2Walker.walkFastLocal(localPoint);
            Rs2Player.waitForWalking(2000);
        } else {
            Rs2Walker.walkTo(worldPoint);
        }
    }

    private void walkToSpiritPool() {
        WorldPoint pl = WorldPoint.toLocalInstance(Microbot.getClient().getTopLevelWorldView(), workArea.spiritPoolPoint).stream().findFirst().orElse(null);

        LocalPoint localPoint = LocalPoint.fromWorld(Microbot.getClient().getTopLevelWorldView(),pl);
        Rs2Camera.turnTo(localPoint);
        assert localPoint != null;
        if(Objects.equals(Microbot.getClient().getLocalDestinationLocation(), localPoint) || Objects.equals(Microbot.getClient().getLocalPlayer().getWorldLocation(), workArea.spiritPoolPoint))
            return;
        if(Rs2Camera.isTileOnScreen(localPoint) && Microbot.isPluginEnabled(GpuPlugin.class)) {
            Rs2Walker.walkFastLocal(localPoint);
            Rs2Player.waitForWalking(2000);
        }
        else
            Rs2Walker.walkTo(getTrueWorldPoint(workArea.spiritPoolPoint));
    }


    private boolean inCloud(LocalPoint point) {
        GameObject cloud = Rs2GameObject.getGameObject(point);
        return cloud != null && cloud.getId() == NullObjectID.NULL_41006;
    }

    public static boolean inCloud(WorldPoint point, int radius) {
        Rs2WorldArea area = new Rs2WorldArea(point.toWorldArea());
        area = area.offset(radius);
        if(sortedClouds.isEmpty())
            return false;
        Rs2WorldArea finalArea = area;
        return sortedClouds.stream().anyMatch(cloud -> finalArea.contains(cloud.getWorldLocation()));
    }


    @Override
    public void shutdown() {
        super.shutdown();
        reset();
        // Any cleanup code here
    }
}
