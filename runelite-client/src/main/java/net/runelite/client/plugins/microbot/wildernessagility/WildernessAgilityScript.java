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
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import javax.inject.Inject;
import static net.runelite.api.Skill.AGILITY;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import java.util.ArrayList;
import net.runelite.api.widgets.Widget;

public class WildernessAgilityScript extends Script {
    public static final String VERSION = "1.2.0";
    private WildernessAgilityConfig config;
    @Inject
    private WildernessAgilityPlugin plugin;

    private static final WorldPoint START_POINT = new WorldPoint(3004, 3936, 0);

    private final List<WildernessAgilityObstacleModel> obstacles = Arrays.asList(
            new WildernessAgilityObstacleModel(23137, false),
            new WildernessAgilityObstacleModel(23132, false),
            new WildernessAgilityObstacleModel(23556, false),
            new WildernessAgilityObstacleModel(23542, true),
            new WildernessAgilityObstacleModel(23640, false)
    );

    private static final int DISPENSER_ID = 53224;

    public int lapCount = 0;
    public int dispenserLoots = 0;
    private long startTime = 0;
    private boolean needsDispenserUnlock = false;

    private static final WorldPoint LEVER_TILE = new WorldPoint(3091, 3956, 0);
    private static final WorldPoint BANK_DEST_TELE = new WorldPoint(2539, 4712, 0);
    private static final int LEVER_ID = 5959;
    private static final int TICKET_ITEM_ID = 29460;
    private static final WorldPoint LOG_WALK_POINT = new WorldPoint(2996, 3955, 0);

    private enum ObstacleState {
        INIT, START, PIPE, ROPE, STONES, LOG, ROCKS, DISPENSER, CONFIG_CHECKS, BANKING, PIT_RECOVERY
    }
    private ObstacleState currentState = ObstacleState.START;

    private long lastInventoryCheck = 0;
    private long lastObjectCheck = 0;
    private int cachedInventoryValue = 0;
    private TileObject cachedDispenserObj = null;

    private ObstacleState pitRecoveryTarget = null;

    private static final WorldPoint DISPENSER_POINT = new WorldPoint(3004, 3936, 0);

    private static final int FOOD_PRIMARY = 24592;
    private static final int FOOD_SECONDARY = 24595;
    private static final int FOOD_TERTIARY = 24589;
    private static final int FOOD_DROP = 24598;
    private static final int KNIFE_ID = 946;
    private static final int TELEPORT_ID = 24963;
    private static final int COINS_ID = 995;

    private enum BankingStep { WALK, OPEN_BANK, DEPOSIT, RETURN }
    private BankingStep bankingStepEnum = BankingStep.WALK;

    private long lastInteractionTime = 0;
    private WorldPoint lastPosition = null;
    private static final int STUCK_TIMEOUT = 5000; // 5 seconds

    private long pipeInteractionStartTime = 0;
    private static final int PIPE_WAIT_TIMEOUT = 5000; // 5 seconds

    private int dispenserLootAttempts = 0;

    private int bankingProgress = 0;

    private int previousWorld = -1;
    private int currentWorld = -1;

    // List of allowed worlds for banking hops
    private static final List<Integer> ALLOWED_WORLDS = Arrays.asList(
        513, 375, 378, 325, 339, 343, 394, 307, 309, 311, 389, 369, 323, 340, 517, 362, 304, 329, 331, 333, 531, 336, 338, 374, 376, 341, 422, 463, 321, 320, 350, 535, 324, 328, 567, 573, 342, 446, 493, 578, 558, 348, 479, 506, 570, 344, 327, 480, 337, 514, 332, 481, 505, 523, 377, 330, 426, 465, 512, 365, 474, 533, 445, 464, 478, 522, 534, 312, 313, 532, 423, 388, 444, 487, 510, 315, 370, 305, 322, 477, 354, 334, 491, 314, 385, 508, 352, 355, 356, 357, 358, 386, 387, 395, 424, 466, 494, 495, 496, 515, 516, 306, 310, 346, 529, 303, 317, 347, 351, 359, 360, 367, 368, 371, 421, 425, 438, 439, 440, 441, 443, 458, 459, 482, 484, 485, 486, 488, 489, 490, 492, 507, 509, 511, 518, 519, 520, 521, 524, 525
    );
    private int bankingHopStep = 0; // 0 = first hop, 1 = second hop

    private boolean ropeInteractionStarted = false;
    private boolean ropeRecoveryWalked = false;

    private boolean pipeJustCompleted = false;

    private boolean forceBankNextLoot = false;

    private void info(String msg) { Microbot.log(msg); }

    private boolean isUnderground(WorldPoint loc) {
        return loc != null && loc.getY() > 10000;
    }

    private boolean waitForXpChange(int startXp, int timeoutMs) {
        return sleepUntil(() -> Microbot.getClient().getSkillExperience(AGILITY) > startXp, timeoutMs);
    }

    private boolean isAt(WorldPoint target, int dist) {
        WorldPoint loc = Rs2Player.getWorldLocation();
        return loc != null && target != null && loc.distanceTo(target) <= dist;
    }

    private static final int ACTION_DELAY = 3000;
    private static final int XP_TIMEOUT = 8000;

    private int getActionDelay() { return ACTION_DELAY; }
    private int getXpTimeout() { return XP_TIMEOUT; }

    private boolean isWaitingForPipe = false;
    private boolean isWaitingForRope = false;
    private boolean isWaitingForStones = false;
    private boolean isWaitingForLog = false;

    private int pipeStartXp = 0;
    private int ropeStartXp = 0;
    private int stonesStartXp = 0;

    public WildernessAgilityScript() {
        // Empty constructor
    }

    public boolean run(WildernessAgilityConfig config) {
        this.config = config;
        bankingHopStep = 0;
        currentState = ObstacleState.START;
        startTime = System.currentTimeMillis();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;

                if (Rs2Player.getHealthPercentage() <= 0) {
                    handlePlayerDeath();
                    return;
                }

                // Check for pit fall first, before any other state changes
                WorldPoint currentLoc = Rs2Player.getWorldLocation();
                if (currentLoc != null && isUnderground(currentLoc)) {
                    // Set recovery target based on current state
                    if (currentState == ObstacleState.ROPE || isWaitingForRope) {
                        pitRecoveryTarget = ObstacleState.ROPE;
                        isWaitingForRope = false;
                    } else if (currentState == ObstacleState.LOG || isWaitingForLog) {
                        pitRecoveryTarget = ObstacleState.LOG;
                        isWaitingForLog = false;
                    }
                    currentState = ObstacleState.PIT_RECOVERY;
                }

                if (System.currentTimeMillis() - lastInventoryCheck > 3000) {
                    cachedInventoryValue = getInventoryValue();
                    lastInventoryCheck = System.currentTimeMillis();
                }

                if (System.currentTimeMillis() - lastObjectCheck > 2000) {
                    cachedDispenserObj = getDispenserObj();
                }

                switch (currentState) {
                    case INIT:
                        currentState = ObstacleState.PIPE;
                        break;
                    case START:
                        handleStart();
                        break;
                    case PIPE:
                        handlePipe();
                        break;
                    case ROPE:
                        handleRope();
                        break;
                    case STONES:
                        pipeJustCompleted = false;
                        handleStones();
                        break;
                    case LOG:
                        handleLog();
                        break;
                    case ROCKS:
                        handleRocks();
                        break;
                    case DISPENSER:
                        handleDispenser();
                        break;
                    case CONFIG_CHECKS:
                        handleConfigChecks();
                        break;
                    case BANKING:
                        handleBanking();
                        break;
                    case PIT_RECOVERY:
                        recoverFromPit();
                        break;
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 100, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
        }
        // Reset all script state variables
        lapCount = 0;
        dispenserLoots = 0;
        startTime = 0;
        currentState = ObstacleState.START;
        pitRecoveryTarget = null;
        isWaitingForPipe = false;
        isWaitingForRope = false;
        isWaitingForStones = false;
        isWaitingForLog = false;
        lastInteractionTime = 0;
        lastPosition = null;
        bankingStepEnum = BankingStep.WALK;
        dispenserLootAttempts = 0;
        bankingProgress = 0;
        previousWorld = -1;
        currentWorld = -1;
        bankingHopStep = 0;
        ropeInteractionStarted = false;
        ropeRecoveryWalked = false;
        pipeJustCompleted = false;
        super.shutdown();
    }

    private void handlePlayerDeath() {
        info("Player died, logging out and shutting down script.");
        sleep(7000);
        Rs2Player.logout();
        Microbot.stopPlugin(plugin);
    }

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

    public String getRunningTime() {
        long elapsed = System.currentTimeMillis() - startTime;
        long seconds = (elapsed / 1000) % 60;
        long minutes = (elapsed / (1000 * 60)) % 60;
        long hours = (elapsed / (1000 * 60 * 60));
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    public void setPlugin(WildernessAgilityPlugin plugin) {
        this.plugin = plugin;
    }

    private TileObject getDispenserObj() {
        return Rs2GameObject.findObjectById(DISPENSER_ID);
    }
    private TileObject getObstacleObj(int index) {
        return Rs2GameObject.findObjectById(obstacles.get(index).getObjectId());
    }
    private boolean isInPit() {
        WorldPoint loc = Rs2Player.getWorldLocation();
        return loc != null && loc.getY() > 10000;
    }
    private void recoverFromPit() {
        // First check if we're still in the pit
        if (isInPit()) {
            List<TileObject> ladders = Rs2GameObject.getAll(o -> o.getId() == 17385, 104);
            TileObject ladderObj = ladders.isEmpty() ? null : ladders.get(0);
            if (ladderObj != null && Rs2Player.getWorldLocation().distanceTo(ladderObj.getWorldLocation()) <= 50) {
                // Try to interact with the ladder until the player is moving
                int ladderAttempts = 0;
                while (!Rs2Player.isMoving() && ladderAttempts < 5 && isInPit()) {
                    Rs2GameObject.interact(ladderObj, "Climb-up");
                    sleep(200);
                    ladderAttempts++;
                }
                // Now wait until above ground as before
                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 5000);
            }
            return; // Return here to let the next tick handle the state transition
        }

        // If we're here, we've successfully climbed out of the pit
        if (pitRecoveryTarget != null) {
            info("Recovering to " + pitRecoveryTarget);
            switch (pitRecoveryTarget) {
                case ROPE:
                    // Fast walk back to rope, but only once
                    WorldPoint ropePoint = new WorldPoint(3005, 3953, 0);
                    if (!ropeRecoveryWalked) {
                        Rs2Walker.walkFastCanvas(ropePoint);
                        ropeRecoveryWalked = true;
                    }
                    if (Rs2Player.getWorldLocation().distanceTo(ropePoint) > 1) {
                        // Not close enough yet? wait for main loop to walk
                        return;
                    }
                    sleep(300, 600);
                    
                    // Now interact with rope
                    TileObject rope = getObstacleObj(1);
                    if (rope != null && !Rs2Player.isMoving()) {
                        isWaitingForRope = false;
                        ropeStartXp = Microbot.getClient().getSkillExperience(AGILITY);
                        boolean interacted = Rs2GameObject.interact(rope);
                        if (interacted) {
                            isWaitingForRope = true;
                        }
                    }
                    break;

                case LOG:
                    // Wide range detection for log, just like ladder detection
                    List<TileObject> logs = Rs2GameObject.getAll(o -> o.getId() == obstacles.get(3).getObjectId(), 104);
                    TileObject log = logs.isEmpty() ? null : logs.get(0);
                    if (log != null) {
                        isWaitingForLog = false;
                        boolean interacted = Rs2GameObject.interact(log);
                        if (interacted) {
                            isWaitingForLog = true;
                            sleep(300, 600);
                        }
                    }
                    break;

                default:
                    info("Unexpected pit recovery target: " + pitRecoveryTarget);
                    break;
            }

            currentState = pitRecoveryTarget;
            pitRecoveryTarget = null;
            ropeRecoveryWalked = false; // Reset after recovery
        } else {
            // This should never happen now that we properly set pitRecoveryTarget
            info("No pit recovery target set - defaulting to rope");
            WorldPoint ropeStart = new WorldPoint(3005, 3953, 0);
            Rs2Walker.walkFastCanvas(ropeStart);
            sleepUntil(() -> !Rs2Player.isMoving(), 5000);
            currentState = ObstacleState.ROPE;
            isWaitingForRope = false;
            ropeRecoveryWalked = false; // Reset if no recovery target
        }
    }
    private WorldPoint getCurrentObstacleStart() {
        switch (currentState) {
            case PIPE:
                return new WorldPoint(3004, 3937, 0);
            case ROPE:
                return new WorldPoint(3005, 3953, 0);
            case STONES:
                return new WorldPoint(2993, 3960, 0);
            case LOG:
                return new WorldPoint(2996, 3955, 0);
            case ROCKS:
                return new WorldPoint(2993, 3937, 0);
            default:
                return null;
        }
    }
    private void clearInventoryIfNeeded() {
        while (Rs2Inventory.items().size() >= 26 && isRunning()) {
            if (Rs2Inventory.contains(FOOD_PRIMARY)) {
                Rs2Inventory.interact(FOOD_PRIMARY, "Eat");
                waitForInventoryChanges(getActionDelay());
            } else if (Rs2Inventory.contains(FOOD_DROP)) {
                Rs2Inventory.interact(FOOD_DROP, "Drop");
                waitForInventoryChanges(800);
            } else if (Rs2Inventory.contains(FOOD_SECONDARY)) {
                Rs2Inventory.interact(FOOD_SECONDARY, "Eat");
                waitForInventoryChanges(getActionDelay());
            } else if (Rs2Inventory.contains(FOOD_TERTIARY)) {
                Rs2Inventory.interact(FOOD_TERTIARY, "Eat");
                waitForInventoryChanges(getActionDelay());
            } else {
                break;
            }
        }
    }
    private void handleBanking() {
        handleBankingSequence();
    }
    private void handleBankingSequence() {
        // If world hopping is disabled, skip steps 0 and 1
        if (!config.enableWorldHop()) {
            if (bankingProgress < 3) {
                bankingProgress = 3;
            }
        }
        switch (bankingProgress) {
            case 0:
                info("Banking progress 0: starting first world hop");
                worldHop();
                bankingProgress++;
                break;
            case 1:
                if (isInNewWorld()) {
                    info("Banking progress 1: dropping FC");
                    dropFC();
                    bankingProgress++;
                }
                break;
            case 2:
                info("Banking progress 2: starting second world hop");
                worldHop();
                bankingProgress++;
                break;
            case 3:
                if (isInNewWorld() || !config.enableWorldHop()) {
                    info("Banking progress 3: walking to lever");
                    if (!isAt(LEVER_TILE, 2)) {
                        Rs2Walker.walkTo(LEVER_TILE, 2);
                        return;
                    }
                    TileObject lever = Rs2GameObject.findObjectById(LEVER_ID);
                    if (lever != null && !Rs2Player.isAnimating()) {
                        Rs2GameObject.interact(lever, "Pull");
                        sleepUntil(() -> Rs2Player.isAnimating(), 2000);
                    }
                    sleepUntil(() -> {
                        WorldPoint currentLoc = Rs2Player.getWorldLocation();
                        return currentLoc != null && currentLoc.equals(BANK_DEST_TELE) && !Rs2Player.isAnimating() && !Rs2Player.isMoving();
                    }, getXpTimeout());
                    WorldPoint finalLoc = Rs2Player.getWorldLocation();
                    if (finalLoc != null && finalLoc.equals(BANK_DEST_TELE)) {
                        bankingProgress++;
                    }
                }
                break;
            case 4:
                info("Banking progress 4: opening bank");
                if (!Rs2Player.getWorldLocation().equals(BANK_DEST_TELE)) {
                    return;
                }
                if (!Rs2Bank.isOpen()) {
                    Rs2Bank.openBank();
                    sleepUntil(Rs2Bank::isOpen, 20000);
                    if (!Rs2Bank.isOpen()) {
                        return;
                    }
                }
                bankingProgress++;
                break;
            case 5:
                info("Banking progress 5: depositing all and withdrawing items");
                Rs2Bank.depositAll();
                sleep(getActionDelay());
                Rs2Bank.withdrawX(COINS_ID, 150000);
                sleep(getActionDelay());
                Rs2Bank.withdrawOne(TELEPORT_ID);
                sleep(getActionDelay());
                Rs2Bank.withdrawOne(KNIFE_ID);
                sleep(getActionDelay());
                Rs2Bank.closeBank();
                sleep(getActionDelay());
                if (Rs2Inventory.hasItem(COINS_ID) && Rs2Inventory.hasItem(TELEPORT_ID) && Rs2Inventory.hasItem(KNIFE_ID)) {
                    bankingProgress++;
                }
                break;
            case 6:
                info("Banking progress 6: returning to course start");
                if (!isAt(START_POINT, 2)) {
                    Rs2Walker.walkTo(START_POINT, 2);
                    return;
                }
                TileObject dispenserObj = getDispenserObj();
                if (dispenserObj != null) {
                    int coinCount = Rs2Inventory.itemQuantity("Coins");
                    if (coinCount >= 150000) {
                        Rs2Inventory.use("Coins");
                        sleep(400);
                        Rs2GameObject.interact(dispenserObj, "Use");
                        sleep(getActionDelay());
                        // Wait for coins to be used
                        if (sleepUntil(() -> Rs2Inventory.itemQuantity("Coins") < coinCount, getXpTimeout())) {
                            info("Coins used on dispenser, transitioning to PIPE");
                            needsDispenserUnlock = true;
                            bankingProgress = 0;  // Reset for next time
                            currentState = ObstacleState.PIPE;
                            isWaitingForPipe = false;  // Ensure we start fresh
                            return;
                        }
                    }
                }
                break;
            case 7:
                info("Banking progress 7: complete");
                bankingProgress = 0;
                break;
            default:
                bankingProgress = 0;
                break;
        }
    }

    private void attemptLogoutUntilLoggedOut() {
        int maxAttempts = 30; // Try for up to 30 seconds
        int attempts = 0;
        while (!"LOGIN_SCREEN".equals(Microbot.getClient().getGameState().toString()) && attempts < maxAttempts) {
            Rs2Player.logout();
            sleep(1000); // Wait 1 second before trying again
            attempts++;
        }
    }

    private void worldHop() {
        // Determine which config value to use
        WildernessAgilityConfig.BankWorldOption worldConfig = (bankingHopStep == 0) ? config.bankWorld1() : config.bankWorld2();
        WildernessAgilityConfig.BankWorldOption otherConfig = (bankingHopStep == 0) ? config.bankWorld2() : config.bankWorld1();
        int current = Rs2Player.getWorld();
        int otherWorld = -1;
        if (otherConfig != WildernessAgilityConfig.BankWorldOption.Random) {
            try {
                otherWorld = Integer.parseInt(otherConfig.name().substring(1));
            } catch (Exception ignored) {}
        }
        int targetWorld = -1;
        if (worldConfig != WildernessAgilityConfig.BankWorldOption.Random) {
            try {
                targetWorld = Integer.parseInt(worldConfig.name().substring(1));
            } catch (Exception ignored) {}
        }
        // If not set or invalid, pick a random world from the allowed list (excluding current and other selected world)
        if (targetWorld == -1 || !ALLOWED_WORLDS.contains(targetWorld) || targetWorld == current || targetWorld == otherWorld) {
            List<Integer> candidates = new ArrayList<>(ALLOWED_WORLDS);
            candidates.remove(Integer.valueOf(current));
            if (otherWorld != -1) candidates.remove(Integer.valueOf(otherWorld));
            if (!candidates.isEmpty()) {
                targetWorld = candidates.get((int)(Math.random() * candidates.size()));
            } else {
                targetWorld = current; // fallback, shouldn't happen
            }
        }
        info("Preparing to hop to world: " + targetWorld);
        // If logged in, robustly attempt to logout
        if (Microbot.isLoggedIn()) {
            info("Attempting to logout before world hop...");
            attemptLogoutUntilLoggedOut();
        }
        if ("LOGIN_SCREEN".equals(Microbot.getClient().getGameState().toString())) {
            info("Using Login class to hop to world: " + targetWorld);
            new Login(targetWorld);
        }
        previousWorld = current;
        currentWorld = targetWorld;
        bankingHopStep = (bankingHopStep + 1) % 2;
    }

    private boolean isInNewWorld() {
        // Update currentWorld if not set
        if (currentWorld == -1) currentWorld = Rs2Player.getWorld();
        return Rs2Player.getWorld() != previousWorld;
    }

    private void dropFC() {
        // Switch to friends tab
        Rs2Tab.switchToFriendsTab();
        sleep(400, 800);
        // Try to find and click the "Leave Chat" button (widget id may need adjustment)
        // Widget id for leave chat is usually 42991621 (Friends Chat tab, "Leave Chat" button)
        Widget leaveChatWidget = Rs2Widget.getWidget(42991621);
        if (leaveChatWidget != null) {
            Rs2Widget.clickWidget(leaveChatWidget);
            sleep(600, 1200);
        } else {
            // As a fallback, type /leave in chat
            Rs2Keyboard.typeString("/leave");
            Rs2Keyboard.enter();
            sleep(600, 1200);
        }
    }

    private void handlePipe() {
        if (isWaitingForPipe) {
            // Use XP drop to confirm pipe completion
            if (waitForXpChange(pipeStartXp, getXpTimeout())) {
                isWaitingForPipe = false;
                pipeJustCompleted = true;
                currentState = ObstacleState.ROPE;
                return;
            }
            // Timeout: if too much time has passed, reset and retry
            if (System.currentTimeMillis() - pipeInteractionStartTime > PIPE_WAIT_TIMEOUT) {
                if (!pipeJustCompleted) {
                    isWaitingForPipe = false;
                    // Optionally log: info("Pipe interaction timed out, retrying...");
                }
            }
            return;
        }
        WorldPoint loc = Rs2Player.getWorldLocation();
        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
            // Player must be within 4 tiles of (3004, 3937, 0) to interact with the pipe at (3004, 3938, 0)
            WorldPoint pipeTile = new WorldPoint(3004, 3938, 0);
            WorldPoint pipeFrontTile = new WorldPoint(3004, 3937, 0);
            int distanceToPipeFront = loc.distanceTo(pipeFrontTile);
            if (distanceToPipeFront > 4) {
                if (!isAt(pipeFrontTile, 4)) {
                    Rs2Walker.walkTo(pipeFrontTile, 2);
                }
                return;
            }
            // Find the pipe object at the exact tile (3004, 3938, 0)
            TileObject pipe = Rs2GameObject.getAll(o -> o.getId() == obstacles.get(0).getObjectId() &&
                                                    o.getWorldLocation().equals(pipeTile), 10)
                                        .stream().findFirst().orElse(null);
            if (pipe == null) return;
            boolean interacted = Rs2GameObject.interact(pipe);
            if (interacted) {
                isWaitingForPipe = true;
                pipeStartXp = Microbot.getClient().getSkillExperience(AGILITY);
                pipeInteractionStartTime = System.currentTimeMillis();
            }
        }
    }
    private void handleRope() {
        // Reset pipeJustCompleted when advancing to rope
        pipeJustCompleted = false;
        if (isWaitingForRope) {
            WorldPoint loc = Rs2Player.getWorldLocation();
            if (!ropeInteractionStarted) {
                // Wait for animation or movement to start
                if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
                    ropeInteractionStarted = true;
                }
                // Also check for pit fall while waiting for interaction
                if (isUnderground(loc)) {
                    if (pitRecoveryTarget != ObstacleState.ROPE) {
                        info("Fell at ROPE - setting recovery target");
                    }
                    isWaitingForRope = false;
                    pitRecoveryTarget = ObstacleState.ROPE;
                    currentState = ObstacleState.PIT_RECOVERY;
                }
                return;
            }
            // Now wait for XP drop or Y >= 3958
            if (ropeInteractionStarted && (waitForXpChange(ropeStartXp, getXpTimeout()) || (loc != null && loc.getY() >= 3958))) {
                isWaitingForRope = false;
                ropeInteractionStarted = false;
                currentState = ObstacleState.STONES;
                return;
            }
            // Pit fall check after interaction started
            if (isUnderground(loc)) {
                if (pitRecoveryTarget != ObstacleState.ROPE) {
                    info("Fell at ROPE - setting recovery target");
                }
                isWaitingForRope = false;
                ropeInteractionStarted = false;
                pitRecoveryTarget = ObstacleState.ROPE;
                currentState = ObstacleState.PIT_RECOVERY;
            }
            return;
        }
        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
            WorldPoint loc = Rs2Player.getWorldLocation();
            TileObject rope = getObstacleObj(1);
            if (rope != null) {
                boolean interacted = Rs2GameObject.interact(rope);
                if (interacted) {
                    isWaitingForRope = true;
                    ropeInteractionStarted = false;
                    ropeStartXp = Microbot.getClient().getSkillExperience(AGILITY);
                    lastInteractionTime = System.currentTimeMillis();
                }
            }
        } else {
            // Check for pit fall while moving/animating
            WorldPoint loc = Rs2Player.getWorldLocation();
            if (isUnderground(loc)) {
                isWaitingForRope = false;
                ropeInteractionStarted = false;
                pitRecoveryTarget = ObstacleState.ROPE;  // Set recovery target for fast walk
                currentState = ObstacleState.PIT_RECOVERY;
                info("Fell at ROPE - setting recovery target to ROPE");
            }
        }
        checkStuckState();
    }
    private void handleStones() {
        if (isWaitingForStones) {
            WorldPoint loc = Rs2Player.getWorldLocation();
            int currentXp = Microbot.getClient().getSkillExperience(AGILITY);
            if (loc != null && loc.getY() > 3961) {
                isWaitingForStones = false;
                return;
            }
            if (loc != null && loc.getX() == 2996) {
                isWaitingForStones = false;
                currentState = ObstacleState.LOG;
                return;
            }
            if (currentXp > stonesStartXp) {
                isWaitingForStones = false;
                currentState = ObstacleState.LOG;
                return;
            }
            return;
        }
        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
            WorldPoint loc = Rs2Player.getWorldLocation();
            TileObject stones = getObstacleObj(2);
            if (stones != null) {
                boolean interacted = Rs2GameObject.interact(stones);
                if (interacted) {
                    isWaitingForStones = true;
                    stonesStartXp = Microbot.getClient().getSkillExperience(AGILITY);
                }
            }
        }
    }
    private void handleLog() {
        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
            WorldPoint loc = Rs2Player.getWorldLocation();
            if (loc != null && loc.getX() == 2994) {
                isWaitingForLog = false;
                currentState = ObstacleState.ROCKS;
                return;
            }
            if (isWaitingForLog) {
                if (isUnderground(loc)) {
                    if (pitRecoveryTarget != ObstacleState.LOG) {
                        info("Fell at LOG - setting recovery target");
                    }
                    isWaitingForLog = false;
                    pitRecoveryTarget = ObstacleState.LOG;
                    currentState = ObstacleState.PIT_RECOVERY;
                }
                return;
            }
            TileObject log = getObstacleObj(3);
            if (log == null) {
                List<TileObject> logs = Rs2GameObject.getAll(o -> o.getId() == obstacles.get(3).getObjectId(), 104);
                log = logs.isEmpty() ? null : logs.get(0);
            }
            if (log != null) {
                boolean interacted = Rs2GameObject.interact(log);
                sleep(100);
                if (interacted && (Rs2Player.isAnimating() || Rs2Player.isMoving())) {
                    isWaitingForLog = true;
                    lastInteractionTime = System.currentTimeMillis();
                }
            }
        } else {
            // Check for pit fall while moving/animating
            WorldPoint loc = Rs2Player.getWorldLocation();
            if (isUnderground(loc)) {
                isWaitingForLog = false;
                pitRecoveryTarget = ObstacleState.LOG;  // Set recovery target for wide detection
                currentState = ObstacleState.PIT_RECOVERY;
                info("Fell at LOG - setting recovery target to LOG");
            }
        }
        checkStuckState();
    }
    private void handleRocks() {
        clearInventoryIfNeeded();
        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
            TileObject rocks = getObstacleObj(4);
            if (rocks == null) {
                List<TileObject> rocksList = Rs2GameObject.getAll(o -> o.getId() == obstacles.get(4).getObjectId(), 104);
                rocks = rocksList.isEmpty() ? null : rocksList.get(0);
            }
            if (rocks != null) {
                int startExp = Microbot.getClient().getSkillExperience(AGILITY);
                if (Rs2GameObject.interact(rocks)) {
                    if (waitForXpChange(startExp, getXpTimeout())) {
                        currentState = ObstacleState.DISPENSER;
                        return;
                    }
                }
            }
            // Fallback: if player Y < 3934, consider rocks completed
            WorldPoint loc = Rs2Player.getWorldLocation();
            if (loc != null && loc.getY() < 3934) {
                currentState = ObstacleState.DISPENSER;
                return;
            }
        }
    }
    private void handleDispenser() {
        if (Rs2Player.isMoving()) return;
        TileObject dispenser = cachedDispenserObj;
        if (dispenser == null || Rs2Player.getWorldLocation().distanceTo(dispenser.getWorldLocation()) > 12) return;

        // Check inventory space before interacting
        if (Rs2Inventory.items().size() >= 26) {
            info("Clearing inventory before looting dispenser");
            clearInventoryIfNeeded();
            return;
        }
        
        int currentTickets = Rs2Inventory.itemQuantity(TICKET_ITEM_ID);
        if (dispenser != null) {
            if (dispenserLootAttempts == 0) {
                Rs2GameObject.interact(dispenser, "Search");
                sleep(1200);
                sleepUntil(() -> Rs2Inventory.itemQuantity(TICKET_ITEM_ID) > currentTickets, 3000);
                if (Rs2Inventory.itemQuantity(TICKET_ITEM_ID) > currentTickets) {
                    dispenserLoots++;
                    lapCount++;
                    currentState = ObstacleState.CONFIG_CHECKS;
                    dispenserLootAttempts = 0;
                } else {
                    dispenserLootAttempts = 1; // Try one more time
                }
            } else if (dispenserLootAttempts == 1) {
                Rs2GameObject.interact(dispenser, "Search");
                sleep(1200);
                sleepUntil(() -> Rs2Inventory.itemQuantity(TICKET_ITEM_ID) > currentTickets, 3000);
                if (Rs2Inventory.itemQuantity(TICKET_ITEM_ID) > currentTickets) {
                    dispenserLoots++;
                    lapCount++;
                    currentState = ObstacleState.CONFIG_CHECKS;
                    dispenserLootAttempts = 0;
                } else {
                    // After two failed attempts, move on to PIPE
                    dispenserLootAttempts = 0;
                    currentState = ObstacleState.PIPE;
                }
            }
        }
    }
    private void handleConfigChecks() {
        TileObject dispenser = cachedDispenserObj;
        if (dispenser == null) return;
        int ticketCount = Rs2Inventory.itemQuantity(TICKET_ITEM_ID);
        if (ticketCount >= config.useTicketsWhen()) {
            boolean didInteract = Rs2Inventory.interact(TICKET_ITEM_ID, "Use");
            if (didInteract) {
                sleep(600);
                didInteract = Rs2GameObject.interact(dispenser, "Use");
                if (didInteract) {
                    sleepUntil(() -> Rs2Inventory.itemQuantity(TICKET_ITEM_ID) < ticketCount, 5000);
                    sleep(1200);
                }
            }
        }
        // Force banking if config.bankNow() is enabled
        if (config.bankNow() || forceBankNextLoot) {
            forceBankNextLoot = false;
            if (config.enableWorldHop()) {
                bankingProgress = 0;
            } else {
                bankingProgress = 3;
            }
            currentState = ObstacleState.BANKING;
            return;
        }
        // Only check banking threshold here
        if (cachedInventoryValue >= config.leaveAtValue()) {
            currentState = ObstacleState.BANKING;
            return;
        }
        clearInventoryIfNeeded();
        currentState = ObstacleState.PIPE;
        dispenserLootAttempts = 0;
    }
    private void handleStart() {
        TileObject dispenserObj = getDispenserObj();
        WorldPoint playerLoc = Rs2Player.getWorldLocation();
        boolean nearDispenser = dispenserObj != null && playerLoc != null && playerLoc.distanceTo(dispenserObj.getWorldLocation()) <= 4;
        
        if (!config.startAtCourse()) {
            if (!nearDispenser) {
                WorldPoint walkTarget = dispenserObj != null ? dispenserObj.getWorldLocation() : DISPENSER_POINT;
                if (!isAt(walkTarget, 4)) {
                    Rs2Walker.walkTo(walkTarget, 2);
                    return;
                }
            }
            int coinCount = Rs2Inventory.itemQuantity("Coins");
            if (coinCount < 150000) {
                currentState = ObstacleState.BANKING;
                return;
            }
            if (dispenserObj != null) {
                Rs2Inventory.use("Coins");
                sleep(400);
                Rs2GameObject.interact(dispenserObj, "Use");
                sleep(getActionDelay());
                sleepUntil(() -> Rs2Inventory.itemQuantity("Coins") < coinCount, getXpTimeout());
            }
            currentState = ObstacleState.PIPE;
            return;
        } else {
            if (!nearDispenser) {
                WorldPoint walkTarget = dispenserObj != null ? dispenserObj.getWorldLocation() : DISPENSER_POINT;
                if (!isAt(walkTarget, 4)) {
                    Rs2Walker.walkTo(walkTarget, 2);
                    return;
                }
            }
            sleep(300, 600);
            currentState = ObstacleState.PIPE;
        }
    }

    private void checkStuckState() {
        WorldPoint currentPos = Rs2Player.getWorldLocation();
        if (lastPosition != null && currentPos.equals(lastPosition) && 
            System.currentTimeMillis() - lastInteractionTime > STUCK_TIMEOUT &&
            !Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
            // We're stuck, reset the state to try again
            info("Detected stuck state, retrying obstacle");
            switch (currentState) {
                case ROPE:
                    isWaitingForRope = false;
                    break;
                case LOG:
                    isWaitingForLog = false;
                    break;
                case STONES:
                    isWaitingForStones = false;
                    break;
                case PIPE:
                    isWaitingForPipe = false;
                    break;
            }
            lastInteractionTime = 0;
        }
        lastPosition = currentPos;
    }
}
