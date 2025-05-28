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
import net.runelite.api.events.ChatMessage;
import net.runelite.api.ChatMessageType;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import java.awt.event.KeyEvent;
import net.runelite.api.events.ActorDeath;
import java.util.Objects;

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

    private static final int LADDER_ID = 17358;
    private static final int LADDER_SEARCH_RADIUS = 30;

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

    public WildernessAgilityScript() {
        // Microbot.log("[DEBUG] WildernessAgilityScript constructor called");
        needsDispenserUnlock = false;
    }

    public boolean run(WildernessAgilityConfig config) {
        Microbot.log("[DEBUG] Entered WildernessAgilityScript.run()");
        this.config = config;
        startTime = System.currentTimeMillis();
        // On script start, ensure needsDispenserUnlock is set if coins are needed
        if (Rs2Inventory.itemQuantity("Coins") >= 150000) {
            needsDispenserUnlock = true;
            Microbot.log("[DEBUG] Script start: needsDispenserUnlock set to true");
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                if (playerLocation == null) return;

                // Check for player death
                if (Rs2Player.getHealthPercentage() == 0) {
                    Microbot.log("[WildernessAgility] Player has died. Shutting down script.");
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
                        Microbot.log("[DEBUG] Blocking pipe interaction: needsDispenserUnlock is true");
                        lastBlockLogTime = now;
                    }
                    // If config.startAtCourse() is enabled, skip coin check and proceed
                    if (config.startAtCourse()) {
                        Microbot.log("[DEBUG] startAtCourse config enabled, skipping coin/dispenser check.");
                        needsDispenserUnlock = false;
                        hasStartedCourse = true;
                        justCompletedPipe = false;
                        // Proceed to obstacle 0 logic
                    } else {
                        int distanceToStart = playerLocation.distanceTo(START_POINT);
                        int coinCount = Rs2Inventory.itemQuantity("Coins");
                        TileObject dispenserObj = Rs2GameObject.findObjectById(DISPENSER_ID);
                        Microbot.log("[DEBUG] Coin unlock check: distanceToStart=" + distanceToStart + ", coinCount=" + coinCount + ", dispenserObjFound=" + (dispenserObj != null));
                        if (distanceToStart > 6) {
                            Rs2Walker.walkTo(START_POINT, 2);
                            return;
                        }
                        if (coinCount >= 150000 && dispenserObj != null) {
                            Microbot.log("[DEBUG] Using coins on dispenser. Coins: " + coinCount);
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
                                    Microbot.log("[DEBUG] Coins removed from inventory after dispenser use.");
                                    coinsRemoved = true;
                                    break;
                                }
                                sleep(100);
                            }
                            if (!coinsRemoved) {
                                Microbot.log("[WARN] Coins were not removed from inventory after 5s timeout!");
                            }
                            if (coinsRemoved) {
                                needsDispenserUnlock = false;
                                hasStartedCourse = true;
                                justCompletedPipe = false;
                                Microbot.log("[DEBUG] Dispenser unlock complete, pipe can now be used.");
                            }
                        }
                        return;
                    }
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
                                }
                            }
                            break;
                        case 2:
                            if (!Rs2Player.isAnimating()) {
                                boolean interacted = Rs2GameObject.interact(currentObj);
                                if (interacted) {
                                    isWaitingForResult = true;
                                    obstacleStartingAgilityExp[currentObstacle] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                    obstacleStartTime = System.currentTimeMillis();
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
                    Microbot.log("[DEBUG] Dispenser step: nearDispenser=" + nearDispenser + ", isWaitingForResult=" + isWaitingForResult + ", dispenserLooted=" + dispenserLooted + ", ticketCount=" + ticketCount + ", threshold=" + config.useTicketsWhen());
                    if (nearDispenser && !isWaitingForResult) {
                        if (ticketCount >= config.useTicketsWhen()) {
                            Microbot.log("[DEBUG] Using " + ticketCount + " tickets at dispenser (using itemQuantity). Using tickets now.");
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
                                Microbot.log("[DEBUG] Ticket quantity changed from " + initialTicketCount + " to " + newTicketCount + ". Proceeding to next lap.");
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
                Microbot.log("[DEBUG] Received ticket. Now have " + currentTicketCount + " tickets. (using itemQuantity)");
                dispenserLoots++;
            }
            lastTicketCount = currentTicketCount;

            // After returning from bank, ensure needsDispenserUnlock is set if coins are needed
            if (shouldBank) {
                if (Rs2Inventory.itemQuantity("Coins") >= 150000) {
                    needsDispenserUnlock = true;
                    Microbot.log("[DEBUG] Post-bank: needsDispenserUnlock set to true");
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

    private void bankAtFerox() {
        // If already at bank and open, deposit all
        if (Rs2Bank.isOpen()) {
            Rs2Bank.depositAll();
            sleepUntil(() -> Rs2Inventory.isEmpty(), 8000);
            shouldBank = false;
            return;
        }
        // If not at bank, walk to Ferox Enclave and open bank
        if (Rs2Player.getWorldLocation().distanceTo(BankLocation.FEROX_ENCLAVE.getWorldPoint()) > 10) {
            Rs2Walker.walkTo(BankLocation.FEROX_ENCLAVE.getWorldPoint(), 8);
            return;
        }
        if (!Rs2Bank.isOpen()) {
            Rs2Bank.walkToBankAndUseBank(BankLocation.FEROX_ENCLAVE);
            sleepUntil(() -> Rs2Bank.isOpen(), 8000);
        }
    }

    // Waits for agility obstacle to finish (XP gain or animation/movement stop)
    private void waitForAgilityObstacleToFinish(int startingAgilityExp) {
        sleepUntil(() -> {
            int currentExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
            boolean xpGained = currentExp != startingAgilityExp;
            boolean notAnimating = !Rs2Player.isAnimating();
            boolean notMoving = !Rs2Player.isMoving();
            return xpGained || (notAnimating && notMoving);
        }, 10000);
    }

    // Add chat message handler for fall detection
    public void onChatMessage(ChatMessage event) {
        String msgLower = event.getMessage().trim().toLowerCase();
        String msgAlpha = msgLower.replaceAll("[^a-z ]", "");
        if (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.SPAM) {
            // Obstacle 1 (rope swing) fail handler
            if (msgAlpha.equals("you slip and fall to the pit below") && waitingForObstacle == 1) {
                new Thread(() -> {
                    try {
                        // Wait until the player is not animating or moving
                        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 5000);
                        sleep(1000); // Small delay after fall
                        // Web walk to pit ladder location
                        WorldPoint pitLadder = new WorldPoint(3004, 10363, 0);
                        Rs2Walker.walkTo(pitLadder, 2);
                        // Wait until close enough to ladder
                        sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(pitLadder) <= 2, 10000);
                        // Interact with ladder (ID 17385) if within 30 tiles
                        TileObject ladderObj = Rs2GameObject.findObjectById(17385);
                        WorldPoint playerLoc = Rs2Player.getWorldLocation();
                        if (ladderObj != null && playerLoc.distanceTo(ladderObj.getWorldLocation()) <= 30) {
                            Rs2GameObject.interact(ladderObj, "Climb-up");
                            sleep(600);
                            // Wait for climb-up animation to finish
                            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 5000);
                        }
                        // Web walk to rope swing start location
                        WorldPoint ropeStart = new WorldPoint(3005, 3952, 0);
                        Rs2Walker.walkTo(ropeStart, 2);
                        sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(ropeStart) <= 2, 10000);
                        // Interact with rope swing (ID: 23132) once close
                        TileObject ropeObj = Rs2GameObject.findObjectById(23132);
                        if (ropeObj != null && Rs2Player.getWorldLocation().distanceTo(ropeObj.getWorldLocation()) <= 2) {
                            Rs2GameObject.interact(ropeObj);
                        }
                        // Reset state
                        isWaitingForResult = false;
                        waitingForObstacle = -1;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
                return;
            }
            // Obstacle 2 (stepping stones) fail handler
            else if (msgAlpha.contains("lose your footing and fall into the lava")) {
                // Special case: Stepping stones fail, just interact with obstacle 2 again
                if (obstacles.size() > 2) {
                    WildernessAgilityObstacleModel steppingStones = obstacles.get(2);
                    TileObject steppingObj = Rs2GameObject.findObjectById(steppingStones.getObjectId());
                    if (steppingObj != null) {
                        Rs2GameObject.interact(steppingObj);
                    }
                }
                isWaitingForResult = false;
                waitingForObstacle = -1;
            }
            // Obstacle 3 (log balance) fail handler
            else if (msgAlpha.contains("lose your balance and fall off the log")) {
                new Thread(() -> {
                    try {
                        // Wait until the player is not animating or moving
                        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 5000);
                        sleep(2000); // Wait an additional 2 seconds after fall completes
                        // Web walk to pit ladder location
                        WorldPoint pitLadder = new WorldPoint(3004, 10363, 0);
                        Rs2Walker.walkTo(pitLadder, 2);
                        sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(pitLadder) <= 2, 10000);
                        // Interact with ladder (ID 17385) if within 30 tiles
                        TileObject ladderObj = Rs2GameObject.findObjectById(17385);
                        WorldPoint playerLoc = Rs2Player.getWorldLocation();
                        if (ladderObj != null && playerLoc.distanceTo(ladderObj.getWorldLocation()) <= 30) {
                            Rs2GameObject.interact(ladderObj, "Climb-up");
                            sleep(600);
                            // Wait for climb-up animation to finish
                            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 5000);
                        }
                        // Web walk back to log balance
                        WildernessAgilityObstacleModel logBalance = obstacles.get(3);
                        TileObject logObj = Rs2GameObject.findObjectById(logBalance.getObjectId());
                        if (logObj != null) {
                            Rs2Walker.walkTo(logObj.getWorldLocation(), 8);
                            // Wait until close enough to log
                            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(logObj.getWorldLocation()) <= 8, 4000);
                            // Interact with log once close
                            Rs2GameObject.interact(logObj);
                        }
                        isWaitingForResult = false;
                        waitingForObstacle = -1;
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }).start();
                return;
            }
        }
    }

    // --- Step Handlers ---
    private void handlePipe() {
        if (isWaitingForResult) return;
        int PIPE_ID = 23137;
        TileObject obj = Rs2GameObject.findObjectById(PIPE_ID);
        if (obj == null) return;
        if (Rs2Player.getWorldLocation().distanceTo(obj.getWorldLocation()) > 10) {
            Rs2Walker.walkTo(obj.getWorldLocation(), 10);
            return;
        }
        if (!Rs2Player.isAnimating() && Rs2GameObject.interact(obj)) {
            isWaitingForResult = true;
            waitingForObstacle = 0;
            obstacleStartingAgilityExp[0] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
        }
    }

    private void handleRopeSwing() {
        if (isWaitingForResult) return;
        int ROPE_ID = 23132;
        TileObject obj = Rs2GameObject.findObjectById(ROPE_ID);
        if (obj == null) return;
        if (Rs2Player.getWorldLocation().distanceTo(obj.getWorldLocation()) > 4) {
            Rs2Walker.walkTo(obj.getWorldLocation(), 4);
            return;
        }
        if (!Rs2Player.isAnimating() && Rs2GameObject.interact(obj)) {
            isWaitingForResult = true;
            waitingForObstacle = 1;
            obstacleStartingAgilityExp[1] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
        }
    }

    private void handleSteppingStone() {
        if (isWaitingForResult) return;
        int STONE_ID = 23556;
        TileObject obj = Rs2GameObject.findObjectById(STONE_ID);
        if (obj == null) return;
        if (Rs2Player.getWorldLocation().distanceTo(obj.getWorldLocation()) > 8) {
            Rs2Walker.walkTo(obj.getWorldLocation(), 8);
            return;
        }
        if (!Rs2Player.isAnimating() && Rs2GameObject.interact(obj)) {
            isWaitingForResult = true;
            waitingForObstacle = 2;
            obstacleStartingAgilityExp[2] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
        }
    }

    private void handleLogBalance() {
        if (isWaitingForResult) return;
        int LOG_ID = 23542;
        TileObject obj = Rs2GameObject.findObjectById(LOG_ID);
        if (obj == null) return;
        if (!Rs2Player.isAnimating() && Rs2GameObject.interact(obj)) {
            isWaitingForResult = true;
            waitingForObstacle = 3;
            obstacleStartingAgilityExp[3] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
        }
    }

    private void handleClimbRocks() {
        if (isWaitingForResult) return;
        int ROCKS_ID = 23640;
        TileObject obj = Rs2GameObject.findObjectById(ROCKS_ID);
        if (obj == null) return;
        if (Rs2Player.getWorldLocation().distanceTo(obj.getWorldLocation()) > 15) {
            Rs2Walker.walkTo(obj.getWorldLocation(), 15);
            return;
        }
        if (!Rs2Player.isAnimating() && Rs2GameObject.interact(obj)) {
            isWaitingForResult = true;
            waitingForObstacle = 4;
            obstacleStartingAgilityExp[4] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
        }
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

    private void handleBankBreak() {
        // 1. Web walk to Ferox Enclave safe spot
        WorldPoint feroxSafeSpot = new WorldPoint(3130, 3635, 0);
        // Enable Protect from Magic
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, true);
        Rs2Walker.walkTo(feroxSafeSpot, 2);
        sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(feroxSafeSpot) <= 2, 20000);
        // Disable Protect from Magic
        Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_MAGIC, false);
        // 2. Interact with Pool of Refreshment (ID 39651)
        TileObject pool = Rs2GameObject.findObjectById(39651);
        if (pool != null) {
            Rs2GameObject.interact(pool, "Drink");
            sleep(2000); // Wait for animation to start
            // Wait until player is not animating or moving (up to 5 seconds)
            sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 5000);
        }
        // 3. Interact with Bank (ID 26711)
        TileObject bank = Rs2GameObject.findObjectById(26711);
        if (bank != null) {
            boolean bankOpened = Rs2GameObject.interact(bank, "Bank");
            sleepUntil(() -> Rs2Bank.isOpen(), 4000);
            if (!Rs2Bank.isOpen()) {
                // Retry once if not open
                sleep(800);
                Rs2GameObject.interact(bank, "Bank");
                sleepUntil(() -> Rs2Bank.isOpen(), 4000);
            }
            if (Rs2Bank.isOpen()) {
                Rs2Bank.depositAll();
                sleepUntil(() -> Rs2Inventory.isEmpty(), 8000);
                // 4. Withdraw 150k coins and 1 Ice plateau teleport
                Rs2Bank.withdrawX("Coins", 150000);
                sleep(600);
                Rs2Bank.withdrawOne("Ice plateau teleport");
                sleep(600);
                // 5. Close bank via Rs2Bank.closeBank() or fallback to escape key
                if (!Rs2Bank.closeBank()) {
                    Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
                }
                sleepUntil(() -> !Rs2Bank.isOpen(), 4000);
                // 6. Break the teleport in inventory, then press yes in the game chat widget
                if (Rs2Inventory.contains("Ice plateau teleport")) {
                    Rs2Inventory.interact("Ice plateau teleport", "Break");
                    sleep(1000);
                    // Confirm in chat widget (simulate pressing yes)
                    Rs2Keyboard.enter();
                    sleep(2000);
                }
                // Explicitly set needsDispenserUnlock to true after banking and withdrawing coins
                needsDispenserUnlock = true;
                Microbot.log("[DEBUG] handleBankBreak: needsDispenserUnlock set to true after banking");
                // 7. Web walk back to the beginning of the course
                Rs2Walker.walkTo(START_POINT, 8);
                sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(START_POINT) <= 8, 20000);
                // After returning, use coins on dispenser and wait 2s before resuming course
                TileObject dispenserObj = Rs2GameObject.findObjectById(DISPENSER_ID);
                if (Rs2Inventory.itemQuantity("Coins") >= 150000 && dispenserObj != null) {
                    Rs2Inventory.use("Coins");
                    sleep(400);
                    Rs2GameObject.interact(dispenserObj, "Use");
                    sleep(2000); // Always wait 2s after using coins on dispenser
                    // Wait up to 5 seconds for coins to leave inventory
                    long waitStart = System.currentTimeMillis();
                    while (Rs2Inventory.itemQuantity("Coins") >= 150000 && System.currentTimeMillis() - waitStart < 5000 && isRunning()) {
                        sleep(200);
                    }
                }
                // Resume course loop
                currentObstacle = 0;
                isWaitingForResult = false;
                waitingForObstacle = -1;
                dispenserLooted = false;
                if (mainScheduledFuture != null && mainScheduledFuture.isCancelled()) {
                    // Restart the main loop
                    run(config);
                }
            }
        }
    }

    // Optionally, subscribe to ActorDeath event for more reliable detection
    public void onActorDeath(ActorDeath event) {
        if (event.getActor() == Microbot.getClient().getLocalPlayer()) {
            Microbot.log("[WildernessAgility] Player death detected via ActorDeath event. Shutting down script.");
            shutdown();
        }
    }
} 