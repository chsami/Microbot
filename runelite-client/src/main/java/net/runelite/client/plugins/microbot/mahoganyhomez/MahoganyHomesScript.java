package net.runelite.client.plugins.microbot.mahoganyhomez;

import com.google.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.ObjectComposition;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.shortestpath.ShortestPathPlugin;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.magic.Rs2Magic;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.walker.WalkerState;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Slf4j
public class MahoganyHomesScript extends Script {

    public static String version = "0.0.6";
    @Inject
    MahoganyHomesPlugin plugin;

    public boolean run(MahoganyHomesConfig config) {
        applyAntiBanSettings(); // <-- Deine AntiBan Settings einmalig am Start

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;

                checkPlankSack();
                fix();
                finish();
                getNewContract();
                bank();             // <-- Hier wird ggf. Teleport zur Bank benutzt
                walkToHome();       // <-- Hier könnte man auch Teleport-Support für den Weg zum Haus ergänzen

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);

        return true;
    }

    private List<GameObject> getFixableObjects() {
        List<GameObject> objects = plugin.getObjectsToMark();
        List<Hotspot> fixableHotspots = Hotspot.getBrokenHotspots();
        HotspotObjects hotspotObjects = plugin.getCurrentHome().getHotspotObjects();

        // Precompute the set of IDs
        Set<Integer> ids = fixableHotspots.stream()
                .map(hotspot -> hotspotObjects.objects[hotspot.ordinal()].getObjectId())
                .collect(Collectors.toSet());

        // Filter using the precomputed set
        return objects.stream()
                .filter(Objects::nonNull)
                .filter(o -> ids.contains(o.getId()))
                .collect(Collectors.toList());
    }

    // Custom logging methods
    private void log(String message) {
        if (plugin.getConfig().logMessages()) {
            Microbot.log(message);
        }
    }

    private void log(String format, Object... args) {
        if (plugin.getConfig().logMessages()) {
            Microbot.log(String.format(format, args));
        }
    }

    private void logInfo(String message) {
        if (plugin.getConfig().logMessages()) {
            log.info(message);
        }
    }

    private void logInfo(String format, Object... args) {
        if (plugin.getConfig().logMessages()) {
            log.info(format, args);
        }
    }

    // Tasks section

    private void checkPlankSack() {
        if (plugin.getConfig().usePlankSack() && plugin.getPlankCount() == -1) {
            if (Rs2Inventory.contains(ItemID.PLANK_SACK)) {
                Rs2ItemModel plankSack = Rs2Inventory.get(ItemID.PLANK_SACK);
                if (plankSack != null) {
                    Rs2Inventory.interact(plankSack, "Check");
                    sleep(Rs2Random.randomGaussian(800, 200));
                }
            }
        }
    }

    private int planksInPlankSack() {
        if (plugin.getPlankCount() == -1) {
            return 0;
        }
        return plugin.getPlankCount();
    }

    private void fix() {
        if (plugin.getCurrentHome() == null
                || !plugin.getCurrentHome().isInside(Rs2Player.getWorldLocation())
                || Hotspot.isEverythingFixed()) {
            return;
        }

        Rs2WorldPoint playerLocation = Rs2Player.getRs2WorldPoint();
        MahoganyHomesOverlay.setFixableObjects(getFixableObjects());

        // Sort fixable objects by plane and distance
        List<GameObject> sortedObjects = getFixableObjects().stream()
                .sorted(Comparator.comparingInt(TileObject::getPlane).thenComparingInt(o -> o.getWorldLocation().distanceTo2D(playerLocation.getWorldPoint())))
                .collect(Collectors.toList());


        GameObject object = sortedObjects.stream()
                .findFirst()
                .orElse(null);

        if (object == null) {
            log("No fixable objects found.");
            return;
        }


        // Find the closest walkable tile around the object
        Rs2WorldPoint objectLocation = Rs2Tile.getNearestWalkableTile(object);


        int pathDistance = objectLocation != null ? objectLocation.distanceToPath(playerLocation.getWorldPoint()) : Integer.MAX_VALUE;
        log("Local Path Distance: " + pathDistance);

        if (pathDistance > 20) {
            if (openDoorToObject(object, objectLocation)) {
                return;
            }
            if (plugin.getCurrentHome().equals(Home.ROSS)) {
                log("Ross home, trying to use ladder.");
                tryToUseLadder();
                return;
            }
            log("Local Path Distance is too far or unreachable, switching to WebWalker.");

            WalkerState state = Rs2Walker.walkWithState(object.getWorldLocation(), 3);
            if (state == WalkerState.UNREACHABLE) {
                if (Rs2Player.getWorldLocation().getPlane() != object.getWorldLocation().getPlane()) {
                    tryToUseLadder();
                } else {
                    log("All pathing failed, trying to interact anyways.");
                    interactWithObject(object);
                }
            } else if (state == WalkerState.ARRIVED) {
                log("Arrived at object, trying to interact.");
                interactWithObject(object);
            }

        } else
            interactWithObject(object);

    }

    private void interactWithObject(GameObject object) {
        Hotspot hotspot = Hotspot.getByObjectId(object.getId());
        String action = Objects.requireNonNull(hotspot).getRequiredAction();
        if (Rs2GameObject.interact(object, action)) {
            sleepUntil(() -> {
                String newAction = Objects.requireNonNull(Hotspot.getByObjectId(object.getId())).getRequiredAction();
                return !newAction.equals(action);
            }, 5000);
            sleep(200, 600);
        }

    }

    private boolean openDoorToObject(GameObject object, Rs2WorldPoint objectLocation) {
        if (Rs2Player.getWorldLocation().getPlane() != object.getWorldLocation().getPlane()) {
            return false;
        }
        log("Local Path seems to be blocked, checking for doors to open.");
        List<WorldPoint> walkerPath = Rs2Walker.getWalkPath(objectLocation.getWorldPoint());
        List<TileObject> doors = new ArrayList<>();
        for (WorldPoint wp : walkerPath) {
            TileObject door = null;
            var tile = Rs2Walker.getTile(wp);

            if (tile != null)
                door = tile.getWallObject();

            if (door == null)
                door = Rs2GameObject.getGameObject(wp);

            if (door == null) continue;

            var objectComp = Rs2GameObject.getObjectComposition(door.getId());
            if (objectComp == null) continue;

            if (Arrays.asList(objectComp.getActions()).contains("Open")) {
                doors.add(door);
            }

        }

        logInfo("Found {} doors", doors.size());
        log("Doors found: %s", doors.size());

        for (TileObject door : doors) {
            ObjectComposition doorComp = Rs2GameObject.getObjectComposition(door.getId());
            List<String> actions = null;
            if (doorComp != null) {
                actions = Arrays.asList(doorComp.getActions());
            }
            if (actions != null && actions.contains("Open")) {

                log("Opening door at: %s", door.getWorldLocation());
                logInfo("Opening door at: {}", door.getWorldLocation());
                if (Rs2GameObject.interact(door, "Open")) {
                    Rs2Player.waitForWalking();
                    sleep(200, 500);
                    // if it's the last door in the list return true
                    if (door.equals(doors.get(doors.size() - 1)))
                        return true;
                }
            }
        }
        return false;
    }

    private void tryToUseLadder() {
        log("Walker missing transport, trying to find ladder manually.");
        int plane = Rs2Player.getWorldLocation().getPlane();
        TileObject closestLadder = Rs2GameObject.findObject(plugin.getCurrentHome().getLadders());
        if (Rs2GameObject.interact(closestLadder)) {
            sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() != plane, 5000);
            sleep(200, 600);
        }
    }


    // Finish by talking to the NPC
    private void finish() {
        if (plugin.getCurrentHome() != null
                && plugin.getCurrentHome().isInside(Rs2Player.getWorldLocation())
                && Hotspot.isEverythingFixed()) {
            if (plugin.getConfig().usePlankSack() && planksInPlankSack() > 0) {
                if (Rs2Inventory.contains(ItemID.PLANK_SACK) && Rs2Inventory.contains(ItemID.STEEL_BAR)) {
                    Rs2ItemModel plankSack = Rs2Inventory.get(ItemID.PLANK_SACK);
                    if (plankSack != null) {
                        Rs2Inventory.interact(plankSack, "Empty");
                        sleep(Rs2Random.randomGaussian(800, 200));
                    }
                }
            }
            var npc = Rs2Npc.getNpc(plugin.getCurrentHome().getNpcId());
            if (npc == null && Rs2Player.getWorldLocation().getPlane() > 0) {
                log("We are on the wrong floor, Trying to find ladder to go down");
                TileObject closestLadder = Rs2GameObject.findObject(plugin.getCurrentHome().getLadders());
                if (Rs2GameObject.interact(closestLadder))
                    sleepUntil(
                            () -> Rs2Player.getWorldLocation().getPlane() == 0
                            , 5000);
                return;
            }
            if (npc != null) {
                Rs2WorldPoint npcLocation = new Rs2WorldPoint(npc.getWorldLocation());
                log("Local NPC path distance: " + npcLocation.distanceToPath(Rs2Player.getWorldLocation()));
                if (npcLocation.distanceToPath(Rs2Player.getWorldLocation()) < 20) {
                    if (Rs2Npc.interact(npc, "Talk-to")) {
                        log("Getting reward from NPC");
                        sleepUntil(Rs2Dialogue::hasContinue, 10000);
                        sleepUntil(() -> !Rs2Dialogue.isInDialogue(), Rs2Dialogue::clickContinue, 6000, 300);
                        sleep(600, 1200);

                    }
                } else {
                    log("Local NPC path distance is too far, switching to WebWalker.");
                    Rs2Walker.walkTo(npc.getWorldLocation());
                    sleep(1200, 2200);
                }
            }
        }
    }

    // Get new contract
    private void getNewContract() {
        if (plugin.getCurrentHome() == null) {
            if (plugin.getConfig().useNpcContact()) {
                if (Rs2Magic.npcContact("amy")) {
                    handleContractDialogue();
                }
                return;
            }
            WorldPoint contractLocation = getClosestContractLocation();
            if (contractLocation.distanceTo2D(Rs2Player.getWorldLocation()) > 10) {
                log("Walking to contract NPC");
                Rs2Walker.walkWithState(contractLocation, 5);

            } else {
                log("Getting new contract");


                var npc = Rs2Npc.getNpcWithAction("Contract");
                if (npc == null) {
                    return;
                }
                log("NPC found: " + npc.getComposition().transform().getName());
                if (Rs2Npc.interact(npc, "Contract")) {
                    handleContractDialogue();
                }

            }

        }

    }

    public void handleContractDialogue() {
        sleepUntil(Rs2Dialogue::hasSelectAnOption, Rs2Dialogue::clickContinue, 10000, 300);
        Rs2Dialogue.keyPressForDialogueOption(plugin.getConfig().currentTier().getPlankSelection().getChatOption());
        sleepUntil(Rs2Dialogue::hasContinue, 10000);
        sleep(400, 800);
        sleepUntil(() -> !Rs2Dialogue.isInDialogue(), Rs2Dialogue::clickContinue, 6000, 300);
        sleep(1200, 2200);
    }

    // Bank if we need to
    private void bank() {
        Home currentHome = plugin.getCurrentHome();
        if (currentHome != null
                && plugin.distanceBetween(currentHome.getArea(), Rs2Player.getWorldLocation()) > 0
                && isMissingItems()) {
            ShortestPathPlugin.getPathfinderConfig().setIgnoreTeleportAndItems(true);
            BankLocation bankLocation = Rs2Bank.getNearestBank(currentHome.getLocation());

            if (useTeleportTabForBank(bankLocation)) {
                sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo2D(bankLocation.getWorldPoint()) < 15, 8000);
            }


            ShortestPathPlugin.getPathfinderConfig().setIgnoreTeleportAndItems(false);
            if (Rs2Bank.walkToBank(bankLocation)) {
                if (Rs2Bank.openBank()) {
                    sleep(600, 1200);
                    if (plugin.getConfig().usePlankSack()) {
                        if (Rs2Inventory.isFull() && !Rs2Inventory.contains(ItemID.STEEL_BAR)) {
                            Rs2Bank.depositAll(plugin.getConfig().currentTier().getPlankSelection().getPlankId());
                            Rs2Inventory.waitForInventoryChanges(5000);
                        }
                        if (steelBarsInInventory() < 4) {
                            Rs2Bank.withdrawX(ItemID.STEEL_BAR, 4 - steelBarsInInventory());
                            Rs2Inventory.waitForInventoryChanges(5000);
                        }

                        Global.sleepUntil(() -> planksInPlankSack() == 28, () -> {
                            Rs2Bank.withdrawAll(plugin.getConfig().currentTier().getPlankSelection().getPlankId());
                            sleep(Rs2Random.randomGaussian(800, 200));
                            Rs2ItemModel plankSack = Rs2Inventory.get(ItemID.PLANK_SACK);
                            if (plankSack != null) {
                                Rs2Inventory.interact(plankSack, "Use");
                                sleep(Rs2Random.randomGaussian(800, 200));
                            }
                        }, 20000, 1000);
                        if (Rs2Inventory.getEmptySlots() > 0)
                            Rs2Bank.withdrawAll(plugin.getConfig().currentTier().getPlankSelection().getPlankId());


                    } else {
                        if (Rs2Inventory.getEmptySlots() - steelBarsNeeded() > 0)
                            Rs2Bank.withdrawX(plugin.getConfig().currentTier().getPlankSelection().getPlankId(), Rs2Inventory.getEmptySlots() - steelBarsNeeded());
                        sleep(600, 1200);
                        if (steelBarsNeeded() > steelBarsInInventory()) {
                            Rs2Bank.withdrawX(ItemID.STEEL_BAR, steelBarsNeeded());
                            sleep(600, 1200);
                        }
                    }
                    Rs2Bank.closeBank();
                }

            }
        }
    }

    // Walk to current home
    private void walkToHome() {
        Home currentHome = plugin.getCurrentHome();
        if (currentHome != null
                && plugin.distanceBetween(currentHome.getArea(), Rs2Player.getWorldLocation()) > 0
                && !isMissingItems()) {
            Rs2Walker.walkWithState(plugin.getCurrentHome().getLocation(), 3);
        }
    }

    private boolean isMissingItems() {
        return (planksInInventory() + planksInPlankSack()) < planksNeeded()
                || steelBarsInInventory() < steelBarsNeeded();
    }

    private int planksNeeded() {
        return plugin.getCurrentHome().getRequiredPlanks(plugin.getContractTier());
    }

    private int steelBarsNeeded() {
        return plugin.getCurrentHome().getRequiredSteelBars(plugin.getContractTier());
    }

    private int planksInInventory() {
        return Rs2Inventory.count(plugin.getConfig().currentTier().getPlankSelection().getPlankId());
    }

    private int steelBarsInInventory() {
        return Rs2Inventory.count(ItemID.STEEL_BAR);
    }

    // Get closest contract location
    private WorldPoint getClosestContractLocation() {
        List<WorldPoint> contractLocations = new ArrayList<>();
        contractLocations.add(ContractLocation.MAHOGANY_HOMES_ARDOUGNE.getLocation());
        contractLocations.add(ContractLocation.MAHOGANY_HOMES_FALADOR.getLocation());
        contractLocations.add(ContractLocation.MAHOGANY_HOMES_HOSIDIUS.getLocation());
        contractLocations.add(ContractLocation.MAHOGANY_HOMES_VARROCK.getLocation());

        return contractLocations.stream()
                .min(Comparator.comparingInt(wp -> wp.distanceTo2D(Rs2Player.getWorldLocation())))
                .orElse(null);
    }

    private boolean useTeleportTabForBank(BankLocation bankLocation) {
        // Falador
        if (bankLocation == BankLocation.FALADOR_EAST || bankLocation == BankLocation.FALADOR_WEST) {
            if (Rs2Inventory.contains(ItemID.FALADOR_TELEPORT)) {
                Rs2Inventory.interact(ItemID.FALADOR_TELEPORT, "Break");
                sleep(1800, 2400); // Wartezeit für Teleport-Animation
                return true;
            }
        }
        // Ardougne
        if (bankLocation == BankLocation.ARDOUGNE_NORTH || bankLocation == BankLocation.ARDOUGNE_SOUTH) {
            if (Rs2Inventory.contains(ItemID.ARDOUGNE_TELEPORT)) {
                Rs2Inventory.interact(ItemID.ARDOUGNE_TELEPORT, "Break");
                sleep(1800, 2400);
                return true;
            }
        }
        // Varrock
        if (bankLocation == BankLocation.VARROCK_EAST || bankLocation == BankLocation.VARROCK_WEST) {
            if (Rs2Inventory.contains(ItemID.VARROCK_TELEPORT)) {
                Rs2Inventory.interact(ItemID.VARROCK_TELEPORT, "Break");
                sleep(1800, 2400);
                return true;
            }
        }
        Rs2Inventory.interact(ItemID.XERICS_TALISMAN, "Rub");
        sleep(800, 1200); // Kurzes Delay für das Menü

        Widget adventureLogWidget = Rs2Widget.getWidget(187, 3);
        if (adventureLogWidget != null) {
            // Liste deiner gewünschten Teleports in Prioritäts-Reihenfolge:
            List<String> bevorzugteZiele = Arrays.asList(
                    "Xeric's Glade",
                    "Xeric's Inferno",
                    "Xeric's Lookout",
                    "Xeric's Honour",
                    "Xeric's Heart"
            );
            Widget bestWidget = null;
            for (String ziel : bevorzugteZiele) {
                bestWidget = Rs2Widget.findWidget(ziel, List.of(adventureLogWidget));
                if (bestWidget != null) {
                    Rs2Widget.clickWidget(bestWidget);
                    Microbot.log("Teleported using: " + ziel);
                    sleep(2000, 2500);
                    break;
                }
            }
            if (bestWidget == null) {
                Microbot.log("Kein bevorzugtes Ziel gefunden!");
            }
        }


        return false;
    }

    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.simulateMistakes = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.playSchedule = true;
        Rs2AntibanSettings.actionCooldownChance = 0.1;
    }


    @Override
    public void shutdown() {
        super.shutdown();
    }
}
