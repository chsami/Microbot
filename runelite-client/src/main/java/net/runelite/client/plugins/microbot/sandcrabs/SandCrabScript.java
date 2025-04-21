package net.runelite.client.plugins.microbot.sandcrabs;

import net.runelite.api.GameState;
import net.runelite.api.NPC;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.sandcrabs.enums.State;
import net.runelite.client.plugins.microbot.sandcrabs.models.ScanLocation;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Random;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class SandCrabScript extends Script {

    public static String version = "1.4.1";

    public int afkTimer = 0;
    public int hijackTimer = 0;

    public State state = State.FIGHT;

    public ScanLocation currentScanLocation;

    public int timesHopped = 0;

    List<ScanLocation> initSandCrabLocations = Arrays.asList(new ScanLocation(new WorldPoint(1843, 3462, 0)),
            new ScanLocation(new WorldPoint(1833, 3458, 0)),
            new ScanLocation(new WorldPoint(1790, 3468, 0)),
            new ScanLocation(new WorldPoint(1776, 3468, 0), true),
            new ScanLocation(new WorldPoint(1773, 3461, 0), true),
            new ScanLocation(new WorldPoint(1765, 3468, 0), true),
            new ScanLocation(new WorldPoint(1749, 3469, 0)),
            new ScanLocation(new WorldPoint(1738, 3468, 0)));

    List<ScanLocation> sandCrabLocations = new ArrayList<>();

    public boolean run(SandCrabConfig config) {
        initialPlayerLocation = null;
        if (config.threeNpcs()) {
            sandCrabLocations = initSandCrabLocations.stream().filter(x -> x.hasThreeNpcs).collect(Collectors.toList());
        } else {
            sandCrabLocations = initSandCrabLocations.stream().collect(Collectors.toList());
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                if (initialPlayerLocation == null) {
                    initialPlayerLocation = Rs2Player.getWorldLocation();
                }
                long startTime = System.currentTimeMillis();

                Rs2Combat.enableAutoRetialiate();

                if (otherPlayerDetected() && !Rs2Combat.inCombat())
                    hijackTimer++;
                else
                    hijackTimer = 0;

                if (hijackTimer > 10) {
                    if (sandCrabLocations.stream().anyMatch(x -> !x.isScanned())) {
                        state = State.SCAN_LOCATIONS;
                    } else {
                        state = State.HOP_WORLD;
                        resetScanLocations();
                    }
                }

                if (currentScanLocation != null && Microbot.getClient().getLocalPlayer().getWorldLocation().distanceTo(currentScanLocation.getWorldPoint()) > 10 && (state != State.RESET_AGGRO && state != State.WALK_BACK && state != State.BANK)) {
                    state = State.WALK_BACK;
                    resetAggro();
                    resetAfkTimer();
                }
                currentScanLocation = sandCrabLocations.stream()
                        .filter(x -> !x.isScanned())
                        .min(Comparator.comparingInt(x -> x.getWorldPoint().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation())))
                        .orElse(null);

                if (sandCrabLocations.stream()
                        .noneMatch(x -> x.getWorldPoint()
                                .equals(Microbot.getClient().getLocalPlayer().getWorldLocation()))
                        && currentScanLocation != null
                        && state != State.RESET_AGGRO
                        && state != State.WALK_BACK) {
                    state = State.SCAN_LOCATIONS;
                }

                if (config.useFood()) {
                    Rs2Player.eatAt(50);

                    if (Rs2Inventory.getInventoryFood().isEmpty()) {
                        state = State.BANK;
                        Rs2Walker.walkTo(new WorldPoint(1720, 3465, 0));
                        if (Rs2Bank.useBank()) {
                            Rs2Bank.withdrawAll(config.food().getName(), true);
                            state = State.WALK_BACK;
                        }
                        return;
                    }
                }



                switch (state) {
                    case FIGHT:
                        if (!Microbot.getClient().getLocalPlayer().isInteracting() && Rs2Combat.inCombat()) {
                            Rs2Tab.switchToCombatOptionsTab();
                            Rs2Combat.enableAutoRetialiate();
                        }
                        if (!isNpcAggressive() || afkTimer > 10) {
                            state = State.AFK;
                        }
                        break;
                    case AFK:
                        afkTimer++;
                        if (Rs2Combat.inCombat()) {
                            resetAfkTimer();
                        }
                        if (afkTimer > 10) {
                            state = State.RESET_AGGRO;
                        }
                        break;
                    case RESET_AGGRO:
                        resetAggro();
                        break;
                    case WALK_BACK:
                        walkBack();
                        break;
                    case HOP_WORLD:
                        int world = Login.getRandomWorld(true, null);
                        boolean isHopped = Microbot.hopToWorld(world);
                        if (isHopped) {
                            boolean result = sleepUntil(() -> Rs2Widget.findWidget("Switch World") != null);
                            if (result) {
                                Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);
                                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.HOPPING);
                                sleepUntil(() -> Microbot.getClient().getGameState() == GameState.LOGGED_IN);
                            }
                        }

                        if (timesHopped > 10) {
                            timesHopped = 0;
                            state = State.SCAN_LOCATIONS;
                        } else {
                            timesHopped++;
                            hijackTimer = 0;
                            resetAfkTimer();
                        }
                        resetScanLocations();
                        break;
                    case SCAN_LOCATIONS:
                        scanSandCrabLocations(config);
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println(totalTime);

            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    /**
     * Checks if there are sandy rocks spawned next to the player
     * This is used to know if the aggro timer has ran out
     *
     * @return true if npc is aggressive
     */
    private boolean isNpcAggressive() {
        List<Rs2NpcModel> npcs = Rs2Npc.getNpcs("Sandy rocks", true).collect(Collectors.toList());
        if (npcs.isEmpty()) {
            return false;
        }
        for (NPC sandyRock : npcs) {
            //ignore sandcrabs far away from the player
            if (!sandyRock.getWorldArea().isInMeleeDistance(Microbot.getClient().getLocalPlayer().getWorldArea()))
                continue;

            return false; //found a sandy rock crab near the player
        }
        return true; //did not find any sandy rocks near the player
    }

    /**
     * Reset aggro will walk 40 tiles north
     */
    private void resetAggro() {
        boolean walkedFarEnough = false;
        if (Rs2Player.getWorldLocation().getX() > 1805) {
            walkedFarEnough = Rs2Walker.walkTo(new WorldPoint(Rs2Random.between(1844, 1849), 3496, 0));
        } else {
            walkedFarEnough= Rs2Walker.walkTo(new WorldPoint(initialPlayerLocation.getX(), initialPlayerLocation.getY() + 40, initialPlayerLocation.getPlane()), 4);
        }

        if (!walkedFarEnough) return;

        state = State.WALK_BACK;
    }

    /**
     * Walks back to the initial player location when the script started
     */
    private void walkBack() {
        boolean backToInitialLocation = Rs2Walker.walkTo(initialPlayerLocation, 0);
        if (!backToInitialLocation) return;

        resetAfkTimer();
    }

    /**
     * Reset afk timer and sets state back to fight
     */
    private void resetAfkTimer() {
        afkTimer = 0;
        state = State.FIGHT;
    }

    private boolean otherPlayerDetected() {
        return otherPlayerDetected(Microbot.getClient().getLocalPlayer().getWorldLocation());
    }

    private boolean otherPlayerDetected(WorldPoint worldPoint) {
        for (Rs2PlayerModel player : Rs2Player.getPlayers(player -> true).collect(Collectors.toList())) {
            if (player.getWorldLocation().distanceTo(worldPoint) > 2)
                continue;
            return true;
        }
        return false;
    }

    private void scanSandCrabLocations(SandCrabConfig config) {
        currentScanLocation = sandCrabLocations.stream()
                .filter(x -> !x.isScanned())
                .min(Comparator.comparingInt(x -> x.getWorldPoint().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation())))
                .orElse(null);

        if (currentScanLocation == null) {

            state = State.HOP_WORLD;
            return;
        }
        //If the currentScan location is far away, we walk to it first
        if (currentScanLocation.getWorldPoint().distanceTo(Microbot.getClient().getLocalPlayer().getWorldLocation()) > 10) {
            boolean reachedLocation = Rs2Walker.walkTo(currentScanLocation.getWorldPoint());
            if (!reachedLocation) {
                if (currentScanLocation.triedWalking > 20) { //something went wrong, just skip this location
                    currentScanLocation.scanned = true;
                } else {
                    currentScanLocation.triedWalking++;
                }
            }
        }
        //check if there are other players on our spot
        if (otherPlayerDetected(currentScanLocation.getWorldPoint())) {
            currentScanLocation.scanned = true;
        } else {
            Rs2Walker.walkTo(currentScanLocation.getWorldPoint(), 0);
            initialPlayerLocation = currentScanLocation.getWorldPoint();
            state = State.FIGHT;
        }
    }

    private void resetScanLocations() {
        for(ScanLocation scanLocation: sandCrabLocations) {
            scanLocation.reset();
        }
    }
}