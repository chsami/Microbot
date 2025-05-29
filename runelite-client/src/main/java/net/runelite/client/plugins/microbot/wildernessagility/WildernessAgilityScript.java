package net.runelite.client.plugins.microbot.wildernessagility;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.TileObject;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import java.util.Objects;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.ChatMessageType;

public class WildernessAgilityScript extends Script {
    private WildernessAgilityConfig config;

    // Pipe obstacle (first obstacle in the course)
    private static final int PIPE_ID = 23137;
    private static final WorldPoint START_POINT = new WorldPoint(3004, 3936, 0); // Default starting location

    // Obstacles in order
    private final List<WildernessAgilityObstacleModel> obstacles = Arrays.asList(
            new WildernessAgilityObstacleModel(23137, false), // 1. Pipe
            new WildernessAgilityObstacleModel(23132, false), // 2. Rope Swing
            new WildernessAgilityObstacleModel(23556, false), // 3. Stepping Stone
            new WildernessAgilityObstacleModel(23542, true),  // 4. Log Balance
            new WildernessAgilityObstacleModel(23640, false)  // 5. Climb Rocks
    );

    // Dispenser
    private static final int DISPENSER_ID = 53224;

    // State tracking
    public int lapCount = 0;
    public int dispenserLoots = 0;
    private boolean shouldBank = false;
    private int currentObstacle = 0;
    private boolean isWaitingForResult = false;
    private int waitingForObstacle = -1;
    private boolean hasStartedCourse = false;
    // XP tracking for all obstacles (0-4)
    private int[] obstacleStartingAgilityExp = new int[5];


    // Flag to handle walking south after stepping stones
    private boolean shouldWalkSouthAfterSteppingStones = false;

    // Dispenser looted flag
    private boolean dispenserLooted = false;

    // Running time tracker
    private long startTime = 0;

    private boolean needsDispenserUnlock = false;

    private boolean initialSetupDone = false;

    // Add a flag to track if the pipe was just completed
    private boolean justCompletedPipe = false;

    // Add a field to track the start time of the current obstacle attempt
    private long obstacleStartTime = 0;

    private static final long MIN_WAIT_AFTER_OBSTACLE_0 = 8000; // 8 seconds

    // Add a field to track last ticket count for debug
    private int lastTicketCount = 0;

    // Add a flag to ensure we web walk to the rope start after failing obstacle 1
    private boolean mustWebWalkToRopeStart = false;

    // Used to throttle log spam for blocking pipe interaction
    private long lastBlockLogTime = 0;

    // Add a field to track pit recovery target obstacle
    private int pitRecoveryTargetObstacle = -1;

    public WildernessAgilityScript() {
        // Microbot.log("[DEBUG] WildernessAgilityScript constructor called");
        needsDispenserUnlock = false;
    }

    public boolean run(WildernessAgilityConfig config) {
        // Microbot.log("[DEBUG] Entered WildernessAgilityScript.run()");
        this.config = config;
        startTime = System.currentTimeMillis();
        // On script start, ensure needsDispenserUnlock is set if coins are needed
        if (Rs2Inventory.itemQuantity("Coins") >= 150000) {
            needsDispenserUnlock = true;
            // Microbot.log("[DEBUG] Script start: needsDispenserUnlock set to true");
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                if (playerLocation == null) return;

                // Check for player death
                if (Rs2Player.getHealthPercentage() == 0) {
                    // Microbot.log("[WildernessAgility] Player has died. Shutting down script.");
                    shutdown();
                    return;
                }

                // Eat/drop priority if inventory is full (27 items): eat 24592, drop 24598, then eat 24595/24589
                if (Rs2Inventory.items().size() >= 27) {
                    if (Rs2Inventory.contains(24592)) {
                        Rs2Inventory.interact(24592, "Eat");
                        waitForInventoryChanges(1200);
                    } else if (Rs2Inventory.contains(24598)) {
                        Rs2Inventory.interact(24598, "Drop");
                        waitForInventoryChanges(800);
                    } else if (Rs2Inventory.contains(24595)) {
                        Rs2Inventory.interact(24595, "Eat");
                        waitForInventoryChanges(1200);
                    } else if (Rs2Inventory.contains(24589)) {
                        Rs2Inventory.interact(24589, "Eat");
                        waitForInventoryChanges(1200);
                    }
                }

                // --- Startup/reset logic: Only web walk to start if not near the pipe and not already on course ---
                TileObject pipeObj = Rs2GameObject.findObjectById(PIPE_ID);
                boolean nearPipe = pipeObj != null && playerLocation.distanceTo(pipeObj.getWorldLocation()) <= 8;
                // Only walk to start if script is just starting, not after pipe is completed
                if (currentObstacle == 0 && !nearPipe && !hasStartedCourse) {
                    Rs2Walker.walkTo(START_POINT, 8);
                    needsDispenserUnlock = true;
                    return;
                }
                // Prevent pipe interaction if needsDispenserUnlock is still true (enforced before any pipe logic)
                if (currentObstacle == 0 && needsDispenserUnlock) {
                    // Only log the block message every 2 seconds
                    long now = System.currentTimeMillis();
                    if (lastBlockLogTime == 0 || now - lastBlockLogTime > 2000) {
                        // Microbot.log("[DEBUG] Blocking pipe interaction: needsDispenserUnlock is true");
                        lastBlockLogTime = now;
                    }
                    // If config.startAtCourse() is enabled, skip coin check and proceed
                    if (config.startAtCourse()) {
                        // Microbot.log("[DEBUG] startAtCourse config enabled, skipping coin/dispenser check.");
                        needsDispenserUnlock = false;
                        hasStartedCourse = true;
                        justCompletedPipe = false;
                        // Proceed to obstacle 0 logic
                    } else {
                        int distanceToStart = playerLocation.distanceTo(START_POINT);
                        int coinCount = Rs2Inventory.itemQuantity("Coins");
                        TileObject dispenserObj = Rs2GameObject.findObjectById(DISPENSER_ID);
                        // Microbot.log("[DEBUG] Coin unlock check: distanceToStart=" + distanceToStart + ", coinCount=" + coinCount + ", dispenserObjFound=" + (dispenserObj != null));
                        if (distanceToStart > 6) {
                            Rs2Walker.walkTo(START_POINT, 2);
                            return;
                        }
                        if (coinCount >= 150000 && dispenserObj != null) {
                            // Microbot.log("[DEBUG] Using coins on dispenser. Coins: " + coinCount);
                            Rs2Inventory.use("Coins");
                            sleep(400);
                            Rs2GameObject.interact(dispenserObj, "Use");
                            sleep(1200);
                            // Wait for coins to be removed from inventory (or timeout)
                            long coinWaitStart = System.currentTimeMillis();
                            boolean coinsRemoved = false;
                            while (System.currentTimeMillis() - coinWaitStart < 5000 && isRunning()) { // Wait up to 5 seconds
                                int coinsLeft = Rs2Inventory.itemQuantity("Coins");
                                if (coinsLeft < 150000) {
                                    // Microbot.log("[DEBUG] Coins removed from inventory after dispenser use.");
                                    coinsRemoved = true;
                                    break;
                                }
                                sleep(100);
                            }
                            if (!coinsRemoved) {
                                // Microbot.log("[WARN] Coins were not removed from inventory after 5s timeout!");
                            }
                            if (coinsRemoved) {
                                needsDispenserUnlock = false;
                                hasStartedCourse = true;
                                justCompletedPipe = false;
                                // Microbot.log("[DEBUG] Dispenser unlock complete, pipe can now be used.");
                            }
                        }
                        return;
                    }
                }

                // --- Pit recovery logic: if player is in the pit (y > 10000), always web walk to ladder and interact ---
                if (Rs2Player.getWorldLocation() != null && Rs2Player.getWorldLocation().getY() > 10000) {
                    WorldPoint pitLadder = new WorldPoint(3004, 10363, 0);
                    Rs2Walker.walkTo(pitLadder, 2);
                    sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(pitLadder) <= 2, 10000);
                    TileObject ladderObj = Rs2GameObject.findObjectById(17385);
                    if (ladderObj != null && Rs2Player.getWorldLocation().distanceTo(ladderObj.getWorldLocation()) <= 30) {
                        Rs2GameObject.interact(ladderObj, "Climb-up");
                        sleep(600);
                        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 5000);
                    }
                    // After climbing up, set a flag to web walk to the correct obstacle
                    if (waitingForObstacle == 1) {
                        pitRecoveryTargetObstacle = 1;
                    } else if (waitingForObstacle == 3) {
                        pitRecoveryTargetObstacle = 3;
                    }
                    isWaitingForResult = false;
                    waitingForObstacle = -1;
                    return;
                }

                // --- After pit recovery, web walk to the correct obstacle and interact ---
                if (pitRecoveryTargetObstacle == 1) {
                    WorldPoint ropeStart = new WorldPoint(3005, 3952, 0);
                    Rs2Walker.walkTo(ropeStart, 4);
                    if (Rs2Player.getWorldLocation().distanceTo(ropeStart) <= 4) {
                        TileObject ropeObj = Rs2GameObject.findObjectById(23132);
                        if (ropeObj != null && Rs2Player.getWorldLocation().distanceTo(ropeObj.getWorldLocation()) <= 4) {
                            Rs2GameObject.interact(ropeObj);
                            pitRecoveryTargetObstacle = -1;
                        }
                    }
                    return;
                } else if (pitRecoveryTargetObstacle == 2) {
                    // Remove pit recovery logic for obstacle 2
                    // ... existing code ...
                } else if (pitRecoveryTargetObstacle == 3) {
                    WildernessAgilityObstacleModel logBalance = obstacles.get(3);
                    TileObject logObj = Rs2GameObject.findObjectById(logBalance.getObjectId());
                    if (logObj != null) {
                        Rs2Walker.walkTo(logObj.getWorldLocation(), 8);
                        if (Rs2Player.getWorldLocation().distanceTo(logObj.getWorldLocation()) <= 8) {
                            Rs2GameObject.interact(logObj);
                            pitRecoveryTargetObstacle = -1;
                        }
                    }
                    return;
                }

                // --- Agility course loop ---
                boolean nearAnyObstacle = false;
                TileObject currentObj = null;
                if (currentObstacle < obstacles.size()) {
                    currentObj = Rs2GameObject.findObjectById(obstacles.get(currentObstacle).getObjectId());
                    if (currentObj != null && Rs2Player.getWorldLocation().distanceTo(currentObj.getWorldLocation()) <= 13) {
                        nearAnyObstacle = true;
                    }
                }

                // For obstacle 3 (log balance), walk if not close enough, otherwise interact
                if (currentObstacle == 3 && currentObj != null) {
                    if (Rs2Player.getWorldLocation().distanceTo(currentObj.getWorldLocation()) > 8) {
                        Rs2Walker.walkTo(currentObj.getWorldLocation(), 8);
                        return;
                    }
                    // If already close enough, fall through to the interaction logic in the switch!
                }

                // XP-driven obstacle progression with fallback for obstacle 0 and 4
                if (isWaitingForResult && currentObj != null) {
                    int currentExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                    boolean xpGained = currentExp > obstacleStartingAgilityExp[currentObstacle];
                    boolean fallback = false;
                    // Only use fallback for obstacle 0 and obstacle 4
                    if (currentObstacle == 0 || currentObstacle == 4) {
                        boolean notAnimating = !Rs2Player.isAnimating();
                        boolean notMoving = !Rs2Player.isMoving();
                        boolean waitedLongEnough = System.currentTimeMillis() - obstacleStartTime > 4000; // 4 seconds
                        fallback = notAnimating && notMoving && waitedLongEnough;
                    }
                    if (xpGained || fallback) {
                        currentObstacle++;
                        isWaitingForResult = false;
                    }
                } else if (nearAnyObstacle && !isWaitingForResult && currentObj != null) {
                    switch (currentObstacle) {
                        case 0:
                            if (Rs2Player.getWorldLocation().distanceTo(currentObj.getWorldLocation()) > 4) {
                                return;
                            }
                            if (!Rs2Player.isAnimating()) {
                                boolean interacted = Rs2GameObject.interact(currentObj);
                                if (interacted) {
                                    isWaitingForResult = true;
                                    obstacleStartingAgilityExp[currentObstacle] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                    obstacleStartTime = System.currentTimeMillis();
                                }
                            }
                            break;
                        case 1:
                            boolean enoughTimePassed = System.currentTimeMillis() - obstacleStartTime >= MIN_WAIT_AFTER_OBSTACLE_0;
                            boolean notAnimating = !Rs2Player.isAnimating();
                            if (!(enoughTimePassed && notAnimating)) {
                                return;
                            }
                            if (!Rs2Player.isAnimating()) {
                                boolean interacted = Rs2GameObject.interact(currentObj);
                                if (interacted) {
                                    isWaitingForResult = true;
                                    obstacleStartingAgilityExp[currentObstacle] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                    obstacleStartTime = System.currentTimeMillis();
                                    // Wait for XP or fall
                                    int startExp = obstacleStartingAgilityExp[currentObstacle];
                                    long waitStart = System.currentTimeMillis();
                                    while (System.currentTimeMillis() - waitStart < 5000 && isRunning()) {
                                        int curExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                        int y = Rs2Player.getWorldLocation().getY();
                                        if (curExp > startExp) {
                                            // Success, move on as normal
                                            currentObstacle++;
                                            isWaitingForResult = false;
                                            return;
                                        }
                                        if (y > 10000) {
                                            pitRecoveryTargetObstacle = 1;
                                            break;
                                        }
                                        sleep(100);
                                    }
                                }
                            }
                            break;
                        case 2:
                            if (!Rs2Player.isAnimating()) {
                                int lastExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                while (true) {
                                    boolean interacted = Rs2GameObject.interact(currentObj);
                                    if (!interacted) break;
                                    long waitStart = System.currentTimeMillis();
                                    while (System.currentTimeMillis() - waitStart < 5000 && isRunning()) {
                                        int curExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                        int y = Rs2Player.getWorldLocation().getY();
                                        if (curExp > lastExp) {
                                            currentObstacle++;
                                            isWaitingForResult = false;
                                            return;
                                        }
                                        if (y > 3960) {
                                            break;
                                        }
                                        sleep(100);
                                    }
                                    // Update lastExp in case we retry
                                    lastExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                }
                            }
                            break;
                        case 3:
                            if (!Rs2Player.isAnimating()) {
                                boolean interacted = Rs2GameObject.interact(currentObj);
                                if (interacted) {
                                    isWaitingForResult = true;
                                    obstacleStartingAgilityExp[currentObstacle] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                    obstacleStartTime = System.currentTimeMillis();
                                    // Wait for XP or fall
                                    int startExp = obstacleStartingAgilityExp[currentObstacle];
                                    long waitStart = System.currentTimeMillis();
                                    while (System.currentTimeMillis() - waitStart < 5000 && isRunning()) {
                                        int curExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                        int y = Rs2Player.getWorldLocation().getY();
                                        if (curExp > startExp) {
                                            // Success, move on as normal
                                            currentObstacle++;
                                            isWaitingForResult = false;
                                            return;
                                        }
                                        if (y > 10000) {
                                            pitRecoveryTargetObstacle = 3;
                                            break;
                                        }
                                        sleep(100);
                                    }
                                }
                            }
                            break;
                        case 4:
                            if (!Rs2Player.isAnimating()) {
                                boolean interacted = Rs2GameObject.interact(currentObj);
                                if (interacted) {
                                    isWaitingForResult = true;
                                    obstacleStartingAgilityExp[currentObstacle] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                    obstacleStartTime = System.currentTimeMillis();
                                }
                            }
                            break;
                        default:
                            break;
                    }
                }

                // --- Combined dispenser logic for currentObstacle == 5 ---
                if (currentObstacle == 5) {
                    TileObject dispenserObj = Rs2GameObject.findObjectById(DISPENSER_ID);
                    boolean nearDispenser = dispenserObj != null && Rs2Player.getWorldLocation().distanceTo(dispenserObj.getWorldLocation()) <= 12;
                    int ticketCount = Rs2Inventory.itemQuantity(29460);
                    int initialTicketCount = ticketCount;
                    // Microbot.log("[DEBUG] Dispenser step: nearDispenser=" + nearDispenser + ", isWaitingForResult=" + isWaitingForResult + ", dispenserLooted=" + dispenserLooted + ", ticketCount=" + ticketCount + ", threshold=" + config.useTicketsWhen());
                    if (nearDispenser && !isWaitingForResult) {
                        if (ticketCount >= config.useTicketsWhen()) {
                            // Microbot.log("[DEBUG] Using " + ticketCount + " tickets at dispenser (using itemQuantity). Using tickets now.");
                            Rs2Inventory.interact(29460, "Use");
                            sleep(400);
                            Rs2GameObject.interact(dispenserObj, "Use");
                            sleep(1200);
                        } else {
                            // Directly interact with dispenser if below threshold
                            Rs2GameObject.interact(dispenserObj, "Search");
                            sleep(1200);
                        }
                        // Wait for ticket quantity to change before transitioning
                        long waitStart = System.currentTimeMillis();
                        while (System.currentTimeMillis() - waitStart < 8000 && isRunning()) { // Wait up to 8 seconds
                            int newTicketCount = Rs2Inventory.itemQuantity(29460);
                            if (newTicketCount != initialTicketCount) {
                                // Microbot.log("[DEBUG] Ticket quantity changed from " + initialTicketCount + " to " + newTicketCount + ". Proceeding to next lap.");
                                break;
                            }
                            sleep(100);
                        }
                        // After ticket quantity change (or timeout), transition to obstacle 0
                        currentObstacle = 0;
                        isWaitingForResult = false;
                        waitingForObstacle = -1;
                        dispenserLooted = false;
                        lapCount++;
                        return;
                    }
                    return;
                }
                if (currentObstacle == 6) {
                    handlePauseAndRepeat();
                    return;
                }

                // After climbing rocks (obstacle 4), drop potions first, then eat food if inventory is full
                if (currentObstacle == 4) {
                    while ((Rs2Inventory.items().size() >= 27) && isRunning()) {
                        if (Rs2Inventory.contains(24592)) {
                            Rs2Inventory.interact(24592, "Eat");
                            waitForInventoryChanges(1200);
                        } else if (Rs2Inventory.contains(24598)) {
                            Rs2Inventory.interact(24598, "Drop");
                            waitForInventoryChanges(800);
                        } else if (Rs2Inventory.contains(24595)) {
                            Rs2Inventory.interact(24595, "Eat");
                            waitForInventoryChanges(1200);
                        } else if (Rs2Inventory.contains(24589)) {
                            Rs2Inventory.interact(24589, "Eat");
                            waitForInventoryChanges(1200);
                        } else {
                            break;
                        }
                    }
                }

                // If mustWebWalkToRopeStart is set, walk to rope start and wait until there before resuming
                if (mustWebWalkToRopeStart) {
                    WorldPoint ropeStart = new WorldPoint(3005, 3951, 0);
                    if (Rs2Player.getWorldLocation().distanceTo(ropeStart) > 2) {
                        Rs2Walker.walkTo(ropeStart, 2);
                        return;
                    } else {
                        mustWebWalkToRopeStart = false;
                    }
                }

                // After climbing up, if failed obstacle 1, set mustWebWalkToRopeStart flag
                if (waitingForObstacle == 1) {
                    mustWebWalkToRopeStart = true;
                }
            } catch (Exception ex) {
                // Microbot.log("An error occurred: " + ex.getMessage(), ex);
            }

            // In the main loop, after getting inventory value, check for ticket gain and log
            int currentTicketCount = Rs2Inventory.itemQuantity(29460);
            if (currentTicketCount > lastTicketCount) {
                // Microbot.log("[DEBUG] Received ticket. Now have " + currentTicketCount + " tickets. (using itemQuantity)");
                dispenserLoots++;
            }
            lastTicketCount = currentTicketCount;

            // After returning from bank, ensure needsDispenserUnlock is set if coins are needed
            if (shouldBank) {
                if (Rs2Inventory.itemQuantity("Coins") >= 150000) {
                    needsDispenserUnlock = true;
                    // Microbot.log("[DEBUG] Post-bank: needsDispenserUnlock set to true");
                }
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        currentObstacle = 0;
        hasStartedCourse = false;
        isWaitingForResult = false;
        waitingForObstacle = -1;
        lapCount = 0;
        dispenserLoots = 0;
        startTime = 0;
        needsDispenserUnlock = false;
        initialSetupDone = false;
        // Microbot.log("WildernessAgilityScript: Shutdown called");
    }

    // Utility: Wait for inventory to change (returns true if changed, false if timeout)
    private boolean waitForInventoryChanges(int timeoutMs) {
        List<Rs2ItemModel> before = Rs2Inventory.items();
        long start = System.currentTimeMillis();
        while (System.currentTimeMillis() - start < timeoutMs && isRunning()) {
            List<Rs2ItemModel> after = Rs2Inventory.items();
            if (after.size() != before.size()) return true;
            sleep(50);
        }
        return false;
    }

    public int getInventoryValue() {
        return Rs2Inventory.items().stream().filter(Objects::nonNull).mapToInt(Rs2ItemModel::getPrice).sum();
    }

    private boolean shouldBankNow() {
        int value = getInventoryValue();
        if (value >= this.config.leaveAtValue()) {
            return true;
        }
        return false;
    }

    private void handlePauseAndRepeat() {
        sleep(2000);
        lapCount++;
        currentObstacle = 0;
    }

    public String getRunningTime() {
        long elapsed = System.currentTimeMillis() - startTime;
        long seconds = (elapsed / 1000) % 60;
        long minutes = (elapsed / (1000 * 60)) % 60;
        long hours = (elapsed / (1000 * 60 * 60));
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

}
