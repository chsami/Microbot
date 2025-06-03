package net.runelite.client.plugins.microbot.BurgerLooter;

import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.antiban.enums.Activity;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.shop.Rs2Shop;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import java.util.*;
import java.util.concurrent.TimeUnit;
import net.runelite.api.GameState;
import net.runelite.client.plugins.microbot.util.security.Login;

public class BurgerLooterScript extends Script {
    private enum State {
        INIT,
        WALKING_TO_LADDER,
        CLIMBING_LADDER_UP_1,
        CLIMBING_LADDER_UP_2,
        WALKING_TO_LOOT,
        LOOTING,
        WORLD_HOPPING,
        CLIMBING_LADDER_DOWN_1,
        CLIMBING_LADDER_DOWN_2,
        WALKING_TO_BANK,
        BANKING,
        WITHDRAW_NOTED,
        WALKING_TO_SHOP,
        SELLING,
        WALKING_TO_BANK_FROM_SHOP,
        RESET
    }

    private static final int RED_ECLIPSE_ID = 29415;
    private static final int COINS_ID = 995;
    private static final WorldPoint LOOT_TILE = new WorldPoint(1555, 3035, 2);
    private static final WorldPoint BANK_TILE = new WorldPoint(1542, 3041, 0);
    private static final WorldPoint SHOP_TILE = new WorldPoint(1362, 2923, 0);
    private static final int ANTONIUS_NPC_ID = 13916;
    private static final int LADDER_UP_ID = 52614;
    private static final int LADDER_DOWN_ID = 52620;
    private static final WorldPoint LADDER_TILE = new WorldPoint(1554, 3035, 0);

    private State state = State.INIT;
    private final LinkedList<Integer> lastWorlds = new LinkedList<>();
    private int totalLooted = 0;
    private BurgerLooterConfig config;
    private long startTime = 0;
    private long lastStateChangeTime = 0;
    private State lastState = null;
    private net.runelite.api.GameState lastLoggedGameState = null;

    public boolean run(BurgerLooterConfig config) {
        this.config = config;
        startTime = System.currentTimeMillis();
        Rs2Antiban.resetAntibanSettings();
        applyAntiBanSettings();
        Rs2Antiban.setActivity(Activity.GENERAL_COLLECTING);
        detectInitialState();
        lastState = state;
        lastStateChangeTime = System.currentTimeMillis();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (Rs2AntibanSettings.actionCooldownActive) return;
                if (Microbot.pauseAllScripts) return;

                // 1. Log all GameState transitions
                net.runelite.api.GameState currentGameState = Microbot.getClient().getGameState();
                if (lastLoggedGameState == null || currentGameState != lastLoggedGameState) {
                    Microbot.log("DEBUG: GameState changed from " + lastLoggedGameState + " to " + currentGameState);
                    lastLoggedGameState = currentGameState;
                }

                // 2. If not logged in, and not just loading/hopping, try to relog
                if (currentGameState != net.runelite.api.GameState.LOGGED_IN &&
                    currentGameState != net.runelite.api.GameState.LOADING &&
                    currentGameState != net.runelite.api.GameState.HOPPING) {
                    Microbot.log("DEBUG: Detected not logged in (GameState=" + currentGameState + "). Attempting relogin.");
                    int nextWorld = getNextWorld();
                    if (nextWorld != -1) {
                        new Login(nextWorld);
                        sleep(5000);
                        state = State.LOOTING;
                        lastState = state;
                        lastStateChangeTime = System.currentTimeMillis();
                    }
                    return;
                }

                // 3. Fallback/heartbeat: if no state change for 15s and not LOGGED_IN, log and relog
                if (System.currentTimeMillis() - lastStateChangeTime > 15000 &&
                    currentGameState != net.runelite.api.GameState.LOGGED_IN) {
                    Microbot.log("DEBUG: No state change in 15s and not logged in (GameState=" + currentGameState + "). Attempting relogin.");
                    int nextWorld = getNextWorld();
                    if (nextWorld != -1) {
                        new Login(nextWorld);
                        sleep(5000);
                        state = State.LOOTING;
                        lastState = state;
                        lastStateChangeTime = System.currentTimeMillis();
                    }
                    return;
                }

                // Detect state change
                if (state != lastState) {
                    lastState = state;
                    lastStateChangeTime = System.currentTimeMillis();
                }

                // If no state change in 10 seconds, try to login
                if (System.currentTimeMillis() - lastStateChangeTime > 10000) {
                    Microbot.log("DEBUG: No state change in 10s. GameState=" + Microbot.getClient().getGameState() + ", State=" + state + ", lastState=" + lastState);
                    int nextWorld = getNextWorld();
                    if (nextWorld != -1) {
                        Microbot.log("DEBUG: Attempting login to world " + nextWorld + " after timeout.");
                        new Login(nextWorld);
                        sleep(5000);
                        state = State.LOOTING; 
                        lastState = state;
                        lastStateChangeTime = System.currentTimeMillis();
                    }
                    return;
                }

                // Additional logout/login screen checks (only if on LOGIN_SCREEN)
                if (Microbot.getClient().getGameState() == net.runelite.api.GameState.LOGIN_SCREEN) {
                    boolean detectedLogout = false;
                    // 1. Microbot.isLoggedIn()
                    if (!Microbot.isLoggedIn()) {
                        Microbot.log("DEBUG: [LOGIN_SCREEN] Detected not logged in via Microbot.isLoggedIn().");
                        detectedLogout = true;
                    }
                    // 2. Rs2Player.getWorldLocation() null or invalid
                    try {
                        var loc = Rs2Player.getWorldLocation();
                        if (loc == null || loc.getX() <= 0 || loc.getY() <= 0) {
                            Microbot.log("DEBUG: [LOGIN_SCREEN] Detected not logged in via Rs2Player.getWorldLocation() null/invalid.");
                            detectedLogout = true;
                        }
                    } catch (Exception ex) {
                        Microbot.log("DEBUG: [LOGIN_SCREEN] Exception in Rs2Player.getWorldLocation(): " + ex.getMessage());
                        detectedLogout = true;
                    }
                    // 8. UI widget check (login screen widget is usually group 378)
                    try {
                        var loginWidget = Microbot.getClient().getWidget(378, 0);
                        if (loginWidget != null && !loginWidget.isHidden()) {
                            Microbot.log("DEBUG: [LOGIN_SCREEN] Detected login screen via widget 378:0 visible.");
                            detectedLogout = true;
                        }
                    } catch (Exception ex) {
                        Microbot.log("DEBUG: [LOGIN_SCREEN] Exception in login widget check: " + ex.getMessage());
                    }
                    if (detectedLogout) {
                        int nextWorld = getNextWorld();
                        if (nextWorld != -1) {
                            Microbot.log("DEBUG: [LOGIN_SCREEN] Attempting login to world " + nextWorld + " after detecting logout by additional checks.");
                            new Login(nextWorld);
                            sleep(5000);
                            state = State.LOOTING;
                            lastState = state;
                            lastStateChangeTime = System.currentTimeMillis();
                        }
                        return;
                    }
                }

                if (Microbot.getClient().getGameState() == GameState.LOGIN_SCREEN) {
                    Microbot.log("DEBUG: Detected LOGIN_SCREEN. State=" + state + ", lastState=" + lastState);
                    int nextWorld = getNextWorld();
                    if (nextWorld != -1) {
                        Microbot.log("DEBUG: Attempting login to world " + nextWorld + " after detecting LOGIN_SCREEN.");
                        new Login(nextWorld);
                        sleep(5000);
                        state = State.LOOTING; 
                        lastState = state;
                        lastStateChangeTime = System.currentTimeMillis();
                    }
                    return;
                }

                switch (state) {
                    case INIT:
                        detectInitialState();
                        break;
                    case WALKING_TO_LADDER:
                        if (Rs2Player.getWorldLocation().distanceTo(LADDER_TILE) > 4) {
                            Rs2Walker.walkTo(LADDER_TILE, 4);
                        } else {
                            state = State.CLIMBING_LADDER_UP_1;
                        }
                        break;
                    case CLIMBING_LADDER_UP_1: {
                        List<TileObject> ladders = Rs2GameObject.getAll(o -> o.getId() == LADDER_UP_ID, 104);
                        TileObject ladder1 = ladders.isEmpty() ? null : ladders.get(0);
                        int startPlane = Rs2Player.getWorldLocation().getPlane();
                        if (ladder1 != null && Rs2GameObject.interact(ladder1, "Climb-up")) {
                            sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() != startPlane, 3000);
                            state = State.CLIMBING_LADDER_UP_2;
                        }
                        break;
                    }
                    case CLIMBING_LADDER_UP_2: {
                        List<TileObject> ladders = Rs2GameObject.getAll(o -> o.getId() == LADDER_UP_ID, 104);
                        TileObject ladder2 = ladders.isEmpty() ? null : ladders.get(0);
                        int startPlane = Rs2Player.getWorldLocation().getPlane();
                        if (ladder2 != null && Rs2GameObject.interact(ladder2, "Climb-up")) {
                            sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() != startPlane, 3000);
                            Rs2Walker.walkFastCanvas(LOOT_TILE);
                            state = State.WALKING_TO_LOOT;
                        }
                        break;
                    }
                    case WALKING_TO_LOOT:
                        if (Rs2Player.getWorldLocation().distanceTo(LOOT_TILE) > 1) {
                            Rs2Walker.walkTo(LOOT_TILE, 1);
                        } else {
                            int plane = Rs2Player.getWorldLocation().getPlane();
                            if (plane != 2) {
                                if (plane == 0) {
                                    state = State.CLIMBING_LADDER_UP_1;
                                } else if (plane == 1) {
                                    state = State.CLIMBING_LADDER_UP_2;
                                }
                            } else {
                                state = State.LOOTING;
                            }
                        }
                        break;
                    case LOOTING: {
                        long lootStart = System.currentTimeMillis();
                        boolean looted = false;
                        while (System.currentTimeMillis() - lootStart < config.lootDetectionWindow()) {
                            if (Rs2Inventory.isFull()) {
                                state = State.CLIMBING_LADDER_DOWN_1;
                                return;
                            }
                            if (Rs2GroundItem.exists(RED_ECLIPSE_ID, 2)) {
                                if (Rs2GroundItem.lootItemsBasedOnLocation(LOOT_TILE, RED_ECLIPSE_ID)) {
                                    totalLooted++;
                                    looted = true;
                                    break;
                                }
                            }
                            sleep(100);
                        }
                        if (looted) {
                            sleep(config.postLootSleep());
                        }
                        state = State.WORLD_HOPPING;
                        break;
                    }
                    case WORLD_HOPPING: {
                        int prevWorld = Rs2Player.getWorld();
                        int nextWorld = getNextWorld();
                        if (nextWorld != -1) {
                            Microbot.hopToWorld(nextWorld);
                            lastWorlds.add(nextWorld);
                            if (lastWorlds.size() > 4) lastWorlds.removeFirst();
                            // Wait up to 3 seconds for world to change
                            long hopStart = System.currentTimeMillis();
                            boolean worldChanged = false;
                            while (System.currentTimeMillis() - hopStart < 3000) {
                                if (Rs2Player.getWorld() != prevWorld) {
                                    worldChanged = true;
                                    break;
                                }
                                sleep(100);
                            }
                            if (!worldChanged) {
                                Microbot.log("World hop failed, retrying...");
                                break; // Try again
                            }
                            // Successful hop, check plane before looting
                            int plane = Rs2Player.getWorldLocation().getPlane();
                            if (plane != 2) {
                                if (plane == 0) {
                                    state = State.CLIMBING_LADDER_UP_1;
                                } else if (plane == 1) {
                                    state = State.CLIMBING_LADDER_UP_2;
                                }
                            } else {
                                state = State.LOOTING;
                            }
                        }
                        break;
                    }
                    case CLIMBING_LADDER_DOWN_1: {
                        List<TileObject> ladders = Rs2GameObject.getAll(o -> o.getId() == LADDER_DOWN_ID, 104);
                        TileObject ladderDown1 = ladders.isEmpty() ? null : ladders.get(0);
                        int startPlane = Rs2Player.getWorldLocation().getPlane();
                        if (ladderDown1 != null && Rs2GameObject.interact(ladderDown1, "Climb-down")) {
                            sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() != startPlane, 3000);
                            state = State.CLIMBING_LADDER_DOWN_2;
                        }
                        break;
                    }
                    case CLIMBING_LADDER_DOWN_2: {
                        List<TileObject> ladders = Rs2GameObject.getAll(o -> o.getId() == LADDER_DOWN_ID, 104);
                        TileObject ladderDown2 = ladders.isEmpty() ? null : ladders.get(0);
                        int startPlane = Rs2Player.getWorldLocation().getPlane();
                        if (ladderDown2 != null && Rs2GameObject.interact(ladderDown2, "Climb-down")) {
                            sleepUntil(() -> Rs2Player.getWorldLocation().getPlane() != startPlane, 3000);
                            state = State.WALKING_TO_BANK;
                        }
                        break;
                    }
                    case WALKING_TO_BANK: {
                        var bankLocation = Rs2Bank.getNearestBank(Rs2Player.getWorldLocation(), 104);
                        TileObject bankChest = null;
                        if (bankLocation != null) {
                            bankChest = Rs2GameObject.getTileObject(26254, bankLocation.getWorldPoint(), 4);
                            if (bankChest == null) {
                                bankChest = Rs2GameObject.getTileObject(53015, bankLocation.getWorldPoint(), 4);
                            }
                        }
                        boolean bankOpened = false;
                        if (bankChest != null) {
                            bankOpened = Rs2Bank.openBank(bankChest);
                        }
                        if (!bankOpened) {
                            bankOpened = Rs2Bank.openBank();
                        }
                        if (bankOpened) {
                            state = State.BANKING;
                        } else if (Rs2Player.getWorldLocation().distanceTo(BANK_TILE) > 2) {
                            Rs2Walker.walkTo(BANK_TILE, 2);
                        }
                        break;
                    }
                    case BANKING:
                        if (Rs2Bank.isOpen()) {
                            Rs2Bank.depositAll();
                            sleep(600, 900);
                            if (Rs2Bank.count(RED_ECLIPSE_ID) >= config.burgerThreshold()) {
                                Rs2Bank.setWithdrawAsNote();
                                Rs2Bank.withdrawAll(RED_ECLIPSE_ID);
                                Rs2Bank.setWithdrawAsItem();
                                Rs2Bank.withdrawX(COINS_ID, 20);
                                sleep(600, 900);
                                Rs2Bank.closeBank();
                                state = State.WALKING_TO_SHOP;
                            } else {
                                Rs2Bank.closeBank();
                                state = State.WALKING_TO_LADDER;
                            }
                        }
                        break;
                    case WALKING_TO_SHOP:
                        if (Rs2Player.getWorldLocation().distanceTo(SHOP_TILE) > 2) {
                            Rs2Walker.walkTo(SHOP_TILE, 2);
                        } else {
                            state = State.SELLING;
                        }
                        break;
                    case SELLING:
                        if (!Rs2Shop.isOpen()) {
                            Rs2Shop.openShop("Antonius", true);
                        } else {
                            while (Rs2Inventory.hasItem(29416)) {
                                Rs2Inventory.interact(29416, "Sell 50");
                                sleep(600, 900);
                            }
                            Rs2Shop.closeShop();
                            state = State.WALKING_TO_BANK_FROM_SHOP;
                        }
                        break;
                    case WALKING_TO_BANK_FROM_SHOP: {
                        var bankLocation = Rs2Bank.getNearestBank(Rs2Player.getWorldLocation(), 104);
                        TileObject bankChest = null;
                        if (bankLocation != null) {
                            bankChest = Rs2GameObject.getTileObject(26254, bankLocation.getWorldPoint(), 4);
                            if (bankChest == null) {
                                bankChest = Rs2GameObject.getTileObject(53015, bankLocation.getWorldPoint(), 4);
                            }
                        }
                        boolean bankOpened = false;
                        if (bankChest != null) {
                            bankOpened = Rs2Bank.openBank(bankChest);
                        }
                        if (!bankOpened) {
                            bankOpened = Rs2Bank.openBank();
                        }
                        if (bankOpened) {
                            Rs2Bank.depositAll();
                            sleep(600, 900);
                            Rs2Bank.closeBank();
                            state = State.RESET;
                        } else if (Rs2Player.getWorldLocation().distanceTo(BANK_TILE) > 2) {
                            Rs2Walker.walkTo(BANK_TILE, 2);
                        }
                        break;
                    }
                    case RESET:
                        state = State.WALKING_TO_LADDER;
                        break;
                }
            } catch (Exception ex) {
                Microbot.log("Error in BurgerLooterScript: " + ex.getMessage());
            }
        }, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private void detectInitialState() {
        if (Rs2Player.getWorldLocation().equals(LOOT_TILE) && !Rs2Inventory.isFull()) {
            state = State.LOOTING;
        } else if (Rs2Player.getWorldLocation().equals(LADDER_TILE)) {
            state = State.CLIMBING_LADDER_UP_1;
        } else if (Rs2Player.getWorldLocation().equals(BANK_TILE) && Rs2Inventory.isFull()) {
            state = State.BANKING;
        } else if (Rs2Player.getWorldLocation().equals(SHOP_TILE)) {
            state = State.SELLING;
        } else {
            state = State.WALKING_TO_LADDER;
        }
    }

    private void applyAntiBanSettings() {
        Rs2AntibanSettings.antibanEnabled = true;
        Rs2AntibanSettings.usePlayStyle = true;
        Rs2AntibanSettings.simulateFatigue = true;
        Rs2AntibanSettings.simulateAttentionSpan = true;
        Rs2AntibanSettings.behavioralVariability = true;
        Rs2AntibanSettings.nonLinearIntervals = true;
        Rs2AntibanSettings.naturalMouse = true;
        Rs2AntibanSettings.moveMouseOffScreen = true;
        Rs2AntibanSettings.contextualVariability = true;
        Rs2AntibanSettings.dynamicIntensity = true;
        Rs2AntibanSettings.devDebug = false;
        Rs2AntibanSettings.moveMouseRandomly = true;
        Rs2AntibanSettings.microBreakDurationLow = 3;
        Rs2AntibanSettings.microBreakDurationHigh = 15;
        Rs2AntibanSettings.actionCooldownChance = 0.4;
        Rs2AntibanSettings.microBreakChance = 0.15;
        Rs2AntibanSettings.moveMouseRandomlyChance = 0.1;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        Rs2Antiban.resetAntibanSettings();
    }

    public String getStateName() {
        return state != null ? state.name() : "N/A";
    }

    public int getTotalLooted() {
        return totalLooted;
    }

    private int getNextWorld() {
        List<Integer> allWorlds = Arrays.asList(
            303, 304, 305, 306, 307, 309, 310, 311, 312, 313, 314, 315, 317, 320, 321, 322, 323, 324, 325, 327, 328, 329, 330, 331, 332, 333, 334, 336, 337, 338, 339, 340, 341, 342, 343, 344, 346, 347, 350, 352, 354, 355, 356, 357, 358, 359, 360, 362, 367, 368, 369, 370, 371, 374, 375, 376, 377, 378, 385, 386, 387, 388, 389, 391, 394, 395, 421, 422, 423, 424, 425, 426, 438, 439, 440, 441, 443, 444, 445, 446, 458, 459, 463, 464, 465, 466, 474, 477, 478, 480, 481, 482, 484, 485, 486, 487, 488, 489, 490, 491, 492, 493, 494, 495, 496, 505, 507, 508, 509, 510, 511, 512, 513, 514, 515, 516, 517, 518, 519, 520, 521, 522, 523, 524, 525, 529, 531, 532, 533, 534, 535, 567, 573
        );
        List<List<Integer>> groups = new ArrayList<>();
        int groupSize = allWorlds.size() / 8;
        for (int i = 0; i < 8; i++) {
            int from = i * groupSize;
            int to = (i == 7) ? allWorlds.size() : (i + 1) * groupSize;
            groups.add(allWorlds.subList(from, to));
        }
        List<Integer> enabledWorlds = new ArrayList<>();
        if (config.burgerWorldGroup1()) enabledWorlds.addAll(groups.get(0));
        if (config.burgerWorldGroup2()) enabledWorlds.addAll(groups.get(1));
        if (config.burgerWorldGroup3()) enabledWorlds.addAll(groups.get(2));
        if (config.burgerWorldGroup4()) enabledWorlds.addAll(groups.get(3));
        if (config.burgerWorldGroup5()) enabledWorlds.addAll(groups.get(4));
        if (config.burgerWorldGroup6()) enabledWorlds.addAll(groups.get(5));
        if (config.burgerWorldGroup7()) enabledWorlds.addAll(groups.get(6));
        if (config.burgerWorldGroup8()) enabledWorlds.addAll(groups.get(7));
        enabledWorlds.removeAll(lastWorlds);
        if (enabledWorlds.isEmpty()) return -1;
        return enabledWorlds.get(new Random().nextInt(enabledWorlds.size()));
    }

    public String getRunningTime() {
        long elapsed = System.currentTimeMillis() - startTime;
        long seconds = (elapsed / 1000) % 60;
        long minutes = (elapsed / (1000 * 60)) % 60;
        long hours = (elapsed / (1000 * 60 * 60));
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
