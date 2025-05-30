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
import net.runelite.client.plugins.microbot.util.gameobject.Rs2WallObject;
import net.runelite.api.Tile;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;

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

    // Add state variables for banking
    private int bankingStep = 0;
    private static final WorldPoint BANK_ROUTE_1 = new WorldPoint(3095, 3957, 0);
    private static final WorldPoint BANK_ROUTE_2 = new WorldPoint(3093, 3957, 0);
    private static final WorldPoint BANK_ROUTE_3 = new WorldPoint(3091, 3957, 0);
    private static final WorldPoint BANK_DEST_TELE = new WorldPoint(2539, 4712, 0);
    private static final int WALL_ID_CLOSED = 733;
    private static final int WALL_ID_OPEN = 734;
    private static final int LEVER_ID = 5959;
    private static final int BANK_OBJECT_ID_NEW = 26707;
    private static final String TELEPORT_NAME = "Ice plateau teleport";

    public WildernessAgilityScript() {
        // Microbot.log("[DEBUG] WildernessAgilityScript constructor called");
        needsDispenserUnlock = false;
    }

    public boolean run(WildernessAgilityConfig config) {
        // Microbot.log("[DEBUG] Entered WildernessAgilityScript.run()");
        this.config = config;
        shouldBank = false;
        bankingStep = 0;
        startTime = System.currentTimeMillis();
        // On script start, ensure needsDispenserUnlock is set if coins are needed
        if (Rs2Inventory.itemQuantity("Coins") >= 150000) {
            needsDispenserUnlock = true;
            // Microbot.log("[DEBUG] Script start: needsDispenserUnlock set to true");
        }
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) {
                    return;
                }

                if (Rs2Player.getHealthPercentage() == 0) {
                    sleep(10000);
                    net.runelite.client.plugins.microbot.Microbot.pauseAllScripts = true;
                    Rs2Player.logout();
                    shutdown();
                    return;
                }

                // Check if we need to bank before processing any obstacles
                if (!shouldBank && getInventoryValue() >= config.leaveAtValue()) {
                    shouldBank = true;
                    bankingStep = 0;
                    return;
                }

                WorldPoint playerLocation = Rs2Player.getWorldLocation();
                if (playerLocation == null) return;

                // Eat/drop priority if inventory is full (26 items): eat 24592, drop 24598, then eat 24595/24589
                if (Rs2Inventory.items().size() >= 26) {
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
                if (currentObstacle == 0 && !nearPipe && !hasStartedCourse && !shouldBank) {
                    TileObject dispenserObj = Rs2GameObject.findObjectById(DISPENSER_ID);
                    if (dispenserObj == null || playerLocation.distanceTo(dispenserObj.getWorldLocation()) > 4) {
                        Rs2Walker.walkTo(START_POINT, 2);
                        return;
                    }
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
                    sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(pitLadder) <= 4, 10000);
                    TileObject ladderObj = Rs2GameObject.findObjectById(17385);
                    if (ladderObj != null && Rs2Player.getWorldLocation().distanceTo(ladderObj.getWorldLocation()) <= 30) {
                        Rs2GameObject.interact(ladderObj, "Climb-up");
                        sleep(600);
                        sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 5000);
                    }
                    // After climbing up, set a flag to web walk to the correct obstacle
                    if (waitingForObstacle == 1) {
                        mustWebWalkToRopeStart = true;
                        WorldPoint ropeStart = new WorldPoint(3005, 3953, 0);
                        Rs2Walker.walkTo(ropeStart, 2);
                    } else if (waitingForObstacle == 3) {
                        pitRecoveryTargetObstacle = 3;
                    }
                    isWaitingForResult = false;
                    waitingForObstacle = -1;
                    return;
                }

                // --- After pit recovery, web walk to the correct obstacle and interact ---
                if (pitRecoveryTargetObstacle == 3) {
                    WildernessAgilityObstacleModel logBalance = obstacles.get(3);
                    TileObject logObj = Rs2GameObject.findObjectById(logBalance.getObjectId());
                    if (logObj != null) {
                        long walkStartTime = System.currentTimeMillis();
                        Rs2Walker.walkTo(logObj.getWorldLocation(), 8);
                        // Wait up to 10 seconds to get close enough
                        while (System.currentTimeMillis() - walkStartTime < 10000 && Rs2Player.getWorldLocation().distanceTo(logObj.getWorldLocation()) > 8 && isRunning()) {
                            sleep(200);
                        }
                        // If after 10 seconds we're still not close enough, try walking again
                        if (Rs2Player.getWorldLocation().distanceTo(logObj.getWorldLocation()) > 8) {
                            Rs2Walker.walkTo(logObj.getWorldLocation(), 8);
                            return;
                        }
                        if (Rs2Player.getWorldLocation().distanceTo(logObj.getWorldLocation()) <= 8) {
                            obstacleStartingAgilityExp[currentObstacle] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                            Rs2GameObject.interact(logObj);
                            // Wait for XP gain
                            sleepUntil(() -> {
                                int currentExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                return currentExp > obstacleStartingAgilityExp[currentObstacle];
                            }, 8000);

                            // If we got XP, increment obstacle and clear recovery flag
                            int currentExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                            if (currentExp > obstacleStartingAgilityExp[currentObstacle]) {
                                currentObstacle = 4; // Explicitly set to obstacle 4
                                pitRecoveryTargetObstacle = -1;
                                isWaitingForResult = false;
                            }
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
                    int startingExp = obstacleStartingAgilityExp[currentObstacle];
                    double startingHealth = Rs2Player.getHealthPercentage();
                    waitForWildernessObstacleToFinish(startingExp, startingHealth);
                } else if (nearAnyObstacle && !isWaitingForResult && currentObj != null) {
                    switch (currentObstacle) {
                        case 0:
                            if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
                                // Only interact if pipe is within 5 tiles
                                if (currentObj != null && Rs2Player.getWorldLocation().distanceTo(currentObj.getWorldLocation()) <= 5) {
                                    obstacleStartingAgilityExp[currentObstacle] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                    boolean interacted = Rs2GameObject.interact(currentObj);
                                    if (interacted) {
                                        long waitStart = System.currentTimeMillis();
                                        while (System.currentTimeMillis() - waitStart < 11000 && isRunning()) {
                                            int curExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                            if (curExp > obstacleStartingAgilityExp[currentObstacle]) {
                                                currentObstacle++;
                                                isWaitingForResult = false;
                                                return;
                                            }
                                            sleep(100);
                                        }
                                    }
                                }
                            }
                            break;
                        case 1:
                            if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
                                if (mustWebWalkToRopeStart) {
                                    WorldPoint ropeStart = new WorldPoint(3005, 3953, 0);
                                    if (Rs2Player.getWorldLocation().distanceTo(ropeStart) > 2) {
                                        Rs2Walker.walkTo(ropeStart, 2);
                                        return;
                                    } else {
                                        mustWebWalkToRopeStart = false;
                                    }
                                }
                                obstacleStartingAgilityExp[currentObstacle] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                boolean interacted = Rs2GameObject.interact(currentObj);
                                if (interacted) {
                                    long waitStart = System.currentTimeMillis();
                                    while (System.currentTimeMillis() - waitStart < 11000 && isRunning()) {
                                        int curExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                        int y = Rs2Player.getWorldLocation().getY();
                                        if (curExp > obstacleStartingAgilityExp[currentObstacle]) {
                                            currentObstacle++;
                                            isWaitingForResult = false;
                                            return;
                                        }
                                        if (y > 10000) {
                                            pitRecoveryTargetObstacle = currentObstacle;
                                            break;
                                        }
                                        sleep(100);
                                    }
                                }
                            }
                            break;
                        case 2:
                            if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
                                obstacleStartingAgilityExp[currentObstacle] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                boolean interacted = Rs2GameObject.interact(currentObj);
                                if (interacted) {
                                    long waitStart = System.currentTimeMillis();
                                    while (System.currentTimeMillis() - waitStart < 11000 && isRunning()) {
                                        int curExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                        WorldPoint currentLoc = Rs2Player.getWorldLocation();
                                        if (curExp > obstacleStartingAgilityExp[currentObstacle]) {
                                            currentObstacle++;
                                            isWaitingForResult = false;
                                            return;
                                        }
                                        // If we've gone above the obstacle, we need to retry
                                        if (currentLoc != null && currentLoc.getY() > 3960) {
                                            return;  // Return to retry the obstacle immediately
                                        }
                                        sleep(100);
                                    }
                                }
                            }
                            break;
                        case 3:
                            // Allow interaction with log balance even if we need to walk closer
                            if (currentObj != null) {
                                if (Rs2Player.getWorldLocation().distanceTo(currentObj.getWorldLocation()) > 8) {
                                    Rs2Walker.walkTo(currentObj.getWorldLocation(), 8);
                                }
                                if (!Rs2Player.isAnimating()) {
                                    obstacleStartingAgilityExp[currentObstacle] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                    boolean interacted = Rs2GameObject.interact(currentObj);
                                    if (interacted) {
                                        long waitStart = System.currentTimeMillis();
                                        while (System.currentTimeMillis() - waitStart < 11000 && isRunning()) {
                                            int curExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                            int y = Rs2Player.getWorldLocation().getY();
                                            if (curExp > obstacleStartingAgilityExp[currentObstacle]) {
                                                currentObstacle++;
                                                isWaitingForResult = false;
                                                return;
                                            }
                                            if (y > 10000) {
                                                pitRecoveryTargetObstacle = currentObstacle;
                                                break;
                                            }
                                            sleep(100);
                                        }
                                    }
                                }
                            }
                            break;
                        case 4:
                            if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
                                obstacleStartingAgilityExp[currentObstacle] = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                boolean interacted = Rs2GameObject.interact(currentObj);
                                if (interacted) {
                                    long waitStart = System.currentTimeMillis();
                                    while (System.currentTimeMillis() - waitStart < 11000 && isRunning()) {
                                        int curExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                                        if (curExp > obstacleStartingAgilityExp[currentObstacle]) {
                                            currentObstacle++;
                                            isWaitingForResult = false;
                                            return;
                                        }
                                        sleep(100);
                                    }
                                    // Timeout reached: treat as fail and retry
                                }
                            }
                            break;
                        default:
                            break;
                    }
                }

                // --- Combined dispenser logic for currentObstacle == 5 ---
                if (currentObstacle == 5) {
                    // Prevent any web walking if we're at obstacle 5
                    if (Rs2Player.isMoving()) {
                        return;
                    }

                    // Check inventory value before any other logic
                    if (getInventoryValue() >= config.leaveAtValue()) {
                        shouldBank = true;
                        bankingStep = 0;
                        return;
                    }

                    TileObject dispenserObj = Rs2GameObject.findObjectById(DISPENSER_ID);
                    if (dispenserObj == null || Rs2Player.getWorldLocation().distanceTo(dispenserObj.getWorldLocation()) > 12) {
                        return;
                    }

                    int ticketCount = Rs2Inventory.itemQuantity(29460);
                    boolean didInteract = false;

                    if (ticketCount >= config.useTicketsWhen()) {
                        // If above threshold, use tickets then search
                        didInteract = Rs2Inventory.interact(29460, "Use");
                        if (didInteract) {
                            sleep(600);
                            didInteract = Rs2GameObject.interact(dispenserObj, "Use");
                            if (didInteract) {
                                // Wait for tickets to be used (check inventory)
                                sleepUntil(() -> Rs2Inventory.itemQuantity(29460) < ticketCount, 5000);
                                sleep(1200); // Extra safety sleep after tickets are used

                                // Always do second interaction after tickets are used
                                Rs2GameObject.interact(dispenserObj, "Search");
                                sleep(1200);
                            }
                        }
                    } else {
                        // If below threshold, just search once
                        Rs2GameObject.interact(dispenserObj, "Search");
                        sleep(1200);
                    }

                    // Force state transition regardless of interaction success
                    currentObstacle = 0;
                    isWaitingForResult = false;
                    waitingForObstacle = -1;
                    dispenserLooted = true;  // Mark as looted to prevent re-entry
                    lapCount++;

                    // Ensure we break out of dispenser state
                    return;
                }

                // Add a safety check to prevent re-entering dispenser state
                if (dispenserLooted && currentObstacle == 5) {
                    currentObstacle = 0;
                    return;
                }

                if (currentObstacle == 6) {
                    handlePauseAndRepeat();
                    return;
                }

                // After climbing rocks (obstacle 4), drop potions first, then eat food if inventory is full
                if (currentObstacle == 4) {
                    while ((Rs2Inventory.items().size() >= 26) && isRunning()) {
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

                // After climbing up from pit, set flag to web walk to rope start
                if (waitingForObstacle == 1) {
                    mustWebWalkToRopeStart = true;
                }

                if (mustWebWalkToRopeStart) {
                    WorldPoint ropeStart = new WorldPoint(3005, 3951, 0);
                    if (Rs2Player.getWorldLocation().distanceTo(ropeStart) > 2) {
                        Rs2Walker.walkTo(ropeStart, 2);
                        return;
                    } else {
                        mustWebWalkToRopeStart = false;
                    }
                }

                if (shouldBank) {
                    switch (bankingStep) {
                        case 0:
                            Rs2Walker.walkTo(BANK_ROUTE_1, 2);
                            if (Rs2Player.isMoving() || Rs2Player.getWorldLocation().distanceTo(BANK_ROUTE_1) > 4) return;
                            // Only interact with the wall at the exact BANK_ROUTE_1 location
                            net.runelite.api.WallObject wall1 = Arrays.stream(Microbot.getClient().getScene().getTiles()[Microbot.getClient().getPlane()])
                                    .flatMap(Arrays::stream)
                                    .filter(Objects::nonNull)
                                    .map(Tile::getWallObject)
                                    .filter(Objects::nonNull)
                                    .filter(wall -> wall.getId() == WALL_ID_CLOSED && wall.getWorldLocation().equals(BANK_ROUTE_1))
                                    .findFirst()
                                    .orElse(null);
                            if (wall1 != null) {
                                Rs2WallObject.interact(wall1, 1); // Interact once
                                // Wait for this specific wall to change to 734
                                boolean changed = Arrays.stream(Microbot.getClient().getScene().getTiles()[Microbot.getClient().getPlane()])
                                        .flatMap(Arrays::stream)
                                        .filter(Objects::nonNull)
                                        .map(Tile::getWallObject)
                                        .filter(Objects::nonNull)
                                        .anyMatch(wall -> wall.getId() == WALL_ID_OPEN && wall.getWorldLocation().equals(BANK_ROUTE_1));
                                if (!changed) {
                                    return; // Try again next tick
                                }
                            } else {
                                // If the wall at the location is not 733 or 734, wait
                                boolean changed = Arrays.stream(Microbot.getClient().getScene().getTiles()[Microbot.getClient().getPlane()])
                                        .flatMap(Arrays::stream)
                                        .filter(Objects::nonNull)
                                        .map(Tile::getWallObject)
                                        .filter(Objects::nonNull)
                                        .anyMatch(wall -> wall.getId() == WALL_ID_OPEN && wall.getWorldLocation().equals(BANK_ROUTE_1));
                                if (!changed) {
                                    return; // Still not open, try again next tick
                                }
                            }
                            bankingStep++;
                            break;
                        case 1:
                            Rs2Walker.walkTo(BANK_ROUTE_2, 2);
                            if (Rs2Player.isMoving() || Rs2Player.getWorldLocation().distanceTo(BANK_ROUTE_2) > 4) return;
                            // Only interact with the wall at the exact (3092, 3957) location
                            net.runelite.api.WallObject wall2 = Arrays.stream(Microbot.getClient().getScene().getTiles()[Microbot.getClient().getPlane()])
                                    .flatMap(Arrays::stream)
                                    .filter(Objects::nonNull)
                                    .map(Tile::getWallObject)
                                    .filter(Objects::nonNull)
                                    .filter(wall -> wall.getId() == WALL_ID_CLOSED && wall.getWorldLocation().getX() == 3092 && wall.getWorldLocation().getY() == 3957)
                                    .findFirst()
                                    .orElse(null);
                            if (wall2 != null) {
                                Rs2WallObject.interact(wall2, 1); // Interact once
                                // Wait for this specific wall to change to 734
                                boolean changed = Arrays.stream(Microbot.getClient().getScene().getTiles()[Microbot.getClient().getPlane()])
                                        .flatMap(Arrays::stream)
                                        .filter(Objects::nonNull)
                                        .map(Tile::getWallObject)
                                        .filter(Objects::nonNull)
                                        .anyMatch(wall -> wall.getId() == WALL_ID_OPEN && wall.getWorldLocation().getX() == 3092 && wall.getWorldLocation().getY() == 3957);
                                if (!changed) {
                                    return; // Try again next tick
                                }
                            } else {
                                // If the wall at the location is not 733 or 734, wait
                                boolean changed = Arrays.stream(Microbot.getClient().getScene().getTiles()[Microbot.getClient().getPlane()])
                                        .flatMap(Arrays::stream)
                                        .filter(Objects::nonNull)
                                        .map(Tile::getWallObject)
                                        .filter(Objects::nonNull)
                                        .anyMatch(wall -> wall.getId() == WALL_ID_OPEN && wall.getWorldLocation().getX() == 3092 && wall.getWorldLocation().getY() == 3957);
                                if (!changed) {
                                    return; // Still not open, try again next tick
                                }
                            }
                            bankingStep++;
                            break;
                        case 2:
                            Rs2Walker.walkTo(BANK_ROUTE_3, 2);
                            WorldPoint leverTile = new WorldPoint(3091, 3956, 0);
                            if (Rs2Player.getWorldLocation().distanceTo(leverTile) > 0) {
                                Rs2Walker.walkTo(leverTile, 0);
                                return;
                            }
                            if (Rs2Player.isMoving()) return;

                            TileObject lever = Rs2GameObject.findObjectById(LEVER_ID);
                            if (lever != null) {
                                // Only pull lever if we haven't started teleporting yet
                                if (!Rs2Player.isAnimating()) {
                                    Rs2GameObject.interact(lever, "Pull");
                                    // Wait for animation to start
                                    sleepUntil(() -> Rs2Player.isAnimating(), 2000);
                                }

                                // Wait for teleport to complete - check both animation end and new location
                                sleepUntil(() -> {
                                    WorldPoint currentLoc = Rs2Player.getWorldLocation();
                                    return currentLoc != null &&
                                            currentLoc.equals(BANK_DEST_TELE) &&
                                            !Rs2Player.isAnimating() &&
                                            !Rs2Player.isMoving();
                                }, 5000);

                                // Final verification we're at bank location
                                WorldPoint finalLoc = Rs2Player.getWorldLocation();
                                if (finalLoc != null && finalLoc.equals(BANK_DEST_TELE)) {
                                    bankingStep++;
                                }
                            }
                            return;
                        case 3:
                            // First ensure we're at the teleport destination
                            if (!Rs2Player.getWorldLocation().equals(BANK_DEST_TELE)) {
                                return;
                            }

                            // Open bank if not open
                            if (!Rs2Bank.isOpen()) {
                                Rs2Bank.openBank();
                                sleepUntil(Rs2Bank::isOpen, 20000);
                                if (!Rs2Bank.isOpen()) {
                                    return;  // Try again next tick if bank didn't open
                                }
                            }

                            // Handle banking operations
                            Rs2Bank.depositAll();
                            sleep(600);
                            Rs2Bank.withdrawX(995, 150000);
                            sleep(600);
                            Rs2Bank.withdrawOne(24963); // Ice plateau teleport
                            sleep(600);
                            Rs2Bank.withdrawOne("Knife");
                            sleep(600);
                            Rs2Bank.closeBank();
                            sleep(1000);

                            // Verify we got our items before proceeding
                            if (Rs2Inventory.hasItem(995) && Rs2Inventory.hasItem(24963) && Rs2Inventory.hasItem("Knife")) {
                                bankingStep++;
                            }
                            return;
                        case 4:
                            Rs2Walker.walkTo(START_POINT, 2);
                            if (Rs2Player.getWorldLocation().distanceTo(START_POINT) > 2) return;

                            // After reaching start point, use coins on dispenser
                            TileObject dispenserObj = Rs2GameObject.findObjectById(DISPENSER_ID);
                            if (dispenserObj != null) {
                                Rs2Inventory.use("Coins");
                                sleep(400);
                                Rs2GameObject.interact(dispenserObj, "Use");
                                sleep(1200);

                                // Wait for coins to be removed from inventory (or timeout)
                                sleepUntil(() -> !Rs2Inventory.hasItem("Coins"), 5000);

                                if (Rs2Inventory.hasItem("Coins")) {
                                    // If coins weren't removed, try again next tick
                                    return;
                                }
                            }

                            needsDispenserUnlock = true;
                            shouldBank = false;
                            bankingStep = 0;
                            currentObstacle = 0;  // Set to obstacle 0 after successful banking
                            break;
                    }
                    return;
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
        shouldBank = false;
        bankingStep = 0;
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

    private boolean waitForWildernessObstacleToFinish(final int startingExp, final double startingHealth) {
        sleepUntil(() -> {
            int currentExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
            double currentHealth = Rs2Player.getHealthPercentage();
            return currentExp > startingExp || currentHealth < startingHealth;
        }, 10000);
        int currentExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
        double currentHealth = Rs2Player.getHealthPercentage();

        if (currentExp > startingExp) {
            currentObstacle++;
            isWaitingForResult = false;
            return true;
        } else if (currentHealth < startingHealth) {
            isWaitingForResult = false;
            return false;
        }
        isWaitingForResult = false;
        return false;
    }

}
