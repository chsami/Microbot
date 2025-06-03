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
import java.text.NumberFormat;


public class WildernessAgilityScript extends Script {
    public static final String VERSION = "1.4.0";
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

    private static final int TICKET_ITEM_ID = 29460;

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

    private boolean ropeInteractionStarted = false;
    private boolean ropeRecoveryWalked = false;

    private boolean pipeJustCompleted = false;

    private boolean forceBankNextLoot = false;

    private long lastObstacleInteractTime = 0;
    private WorldPoint lastObstaclePosition = null;

    private int logStartXp = 0;

    private int dispenserPreValue = 0;

    private static final int LOG_WAIT_TIMEOUT = 8000; // 8 seconds, matches XP_TIMEOUT
    private long logInteractionStartTime = 0;

    private static final int ACTION_DELAY = 3000;
    private static final int XP_TIMEOUT = 8000;

    private boolean isWaitingForPipe = false;
    private boolean isWaitingForRope = false;
    private boolean isWaitingForStones = false;
    private boolean isWaitingForLog = false;

    private int pipeStartXp = 0;
    private int ropeStartXp = 0;
    private int stonesStartXp = 0;

    private long pipeYThresholdTime = 0;

    private int ladderTickCounter = 0;

    public WildernessAgilityScript() {
        // Empty constructor
    }

    private void info(String msg) {
        Microbot.log(msg);
        System.out.println(msg);
    }

    public boolean run(WildernessAgilityConfig config) {
        this.config = config;
        bankingProgress = 0;
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

                if (lastObstacleInteractTime > 0 && lastObstaclePosition != null && System.currentTimeMillis() - lastObstacleInteractTime > 2000) {
                    WorldPoint currentPos = Rs2Player.getWorldLocation();
                    if (currentPos != null && currentPos.equals(lastObstaclePosition)) {
                        // Player hasn't moved, reset the current obstacle state
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
                                if (!pipeJustCompleted) {
                                    isWaitingForPipe = false;
                                }
                                break;
                        }
                        lastObstacleInteractTime = 0;
                        lastObstaclePosition = null;
                    }
                }
            } catch (Exception ex) {
                Microbot.logStackTrace(this.getClass().getSimpleName(), ex);
            }
        }, 0, 200, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        if (mainScheduledFuture != null && !mainScheduledFuture.isCancelled()) {
            mainScheduledFuture.cancel(true);
        }
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
        ropeInteractionStarted = false;
        ropeRecoveryWalked = false;
        pipeJustCompleted = false;
        super.shutdown();
    }

    private void handlePlayerDeath() {
        if (config.runBack()) {
            sleep(10000); // Wait 10 seconds after dying
            Rs2Bank.walkToBank();
            bankingProgress = 4;
            currentState = ObstacleState.BANKING;
            return;
        }
        sleep(7000);
        attemptLogoutUntilLoggedOut();
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
            ladderTickCounter++;
            List<TileObject> ladders = Rs2GameObject.getAll(o -> o.getId() == 17385, 104);
            TileObject ladderObj = ladders.isEmpty() ? null : ladders.get(0);
            if (ladderObj != null && Rs2Player.getWorldLocation().distanceTo(ladderObj.getWorldLocation()) <= 50) {
                // Only attempt to interact with the ladder every 3 ticks
                if (ladderTickCounter % 3 == 0) {
                    Rs2GameObject.interact(ladderObj, "Climb-up");
                }
            }
            return; // Return here to let the next tick handle the state transition
        } else {
            ladderTickCounter = 0;
        }

        // If we're here, we've successfully climbed out of the pit
        if (pitRecoveryTarget != null) {
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
                    break;
            }

            currentState = pitRecoveryTarget;
            pitRecoveryTarget = null;
            ropeRecoveryWalked = false; // Reset after recovery
        } else {
            // This should never happen now that we properly set pitRecoveryTarget
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
        switch (bankingProgress) {
            case 0:
                WorldPoint mageBankTile = new WorldPoint(2534, 4712, 0);
                if (!isAt(mageBankTile, 2)) {
                    Rs2Walker.walkTo(mageBankTile, 2);
                    return;
                }
                bankingProgress++;
                break;
            case 1:
                if (!Rs2Bank.isOpen()) {
                    Rs2Bank.openBank();
                    sleepUntil(Rs2Bank::isOpen, 20000);
                    if (!Rs2Bank.isOpen()) {
                        return;
                    }
                }
                bankingProgress++;
                break;
            case 2:
                Rs2Bank.depositAll();
                sleep(getActionDelay());
                Rs2Bank.withdrawX(COINS_ID, 150000);
                sleep(getActionDelay());
                if (config.useIcePlateauTp()) {
                    Rs2Bank.withdrawOne(TELEPORT_ID);
                    sleep(getActionDelay());
                }
                Rs2Bank.withdrawOne(KNIFE_ID);
                sleep(getActionDelay());
                Rs2Bank.closeBank();
                sleep(getActionDelay());
                boolean hasAll = Rs2Inventory.hasItem(COINS_ID) && Rs2Inventory.hasItem(KNIFE_ID);
                if (config.useIcePlateauTp()) {
                    hasAll = hasAll && Rs2Inventory.hasItem(TELEPORT_ID);
                }
                if (hasAll) {
                    bankingProgress++;
                }
                break;
            case 3:
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
                            needsDispenserUnlock = true;
                            bankingProgress = 0;  // Reset for next time
                            currentState = ObstacleState.PIPE;
                            isWaitingForPipe = false;  // Ensure we start fresh
                            return;
                        }
                    }
                }
                break;
            case 4:
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

    private void handlePipe() {
        if (isWaitingForPipe) {
            // Use XP drop to confirm pipe completion
            if (waitForXpChange(pipeStartXp, getXpTimeout())) {
                isWaitingForPipe = false;
                pipeJustCompleted = false; // Clear after XP drop
                currentState = ObstacleState.ROPE;
                return;
            }
            // Timeout: if too much time has passed, reset and retry
            if (System.currentTimeMillis() - pipeInteractionStartTime > PIPE_WAIT_TIMEOUT) {
                if (!pipeJustCompleted) {
                    isWaitingForPipe = false;
                    pipeJustCompleted = false; // Clear on timeout
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
            if (pipe == null) {
                return;
            }
            boolean interacted = Rs2GameObject.interact(pipe);
            if (interacted) {
                isWaitingForPipe = true;
                pipeJustCompleted = true; // Set immediately after interaction
                pipeStartXp = Microbot.getClient().getSkillExperience(AGILITY);
                pipeInteractionStartTime = System.currentTimeMillis();
            }
        }
    }
    private void handleRope() {
        pipeJustCompleted = false;
        WorldPoint loc = Rs2Player.getWorldLocation();
        // Always check for pitfall first, before any other logic
        if (isUnderground(loc)) {
            if (pitRecoveryTarget != ObstacleState.ROPE) {
                pitRecoveryTarget = ObstacleState.ROPE;
                currentState = ObstacleState.PIT_RECOVERY;
            }
            isWaitingForRope = false;
            ropeInteractionStarted = false;
            return;
        }
        if (isWaitingForRope) {
            if (!ropeInteractionStarted) {
                if (Rs2Player.isAnimating() || Rs2Player.isMoving()) {
                    ropeInteractionStarted = true;
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
            return;
        }
        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
            TileObject rope = getObstacleObj(1);
            if (rope != null) {
                boolean interacted = Rs2GameObject.interact(rope);
                if (interacted) {
                    isWaitingForRope = true;
                    ropeInteractionStarted = false;
                    ropeStartXp = Microbot.getClient().getSkillExperience(AGILITY);
                    lastObstacleInteractTime = System.currentTimeMillis();
                    lastObstaclePosition = Rs2Player.getWorldLocation();
                }
            }
        }
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
                    lastObstacleInteractTime = System.currentTimeMillis();
                    lastObstaclePosition = Rs2Player.getWorldLocation();
                }
            }
        }
    }
    private void handleLog() {
        if (isWaitingForLog) {
            WorldPoint loc = Rs2Player.getWorldLocation();
            if (loc != null && loc.getX() == 2994) {
                isWaitingForLog = false;
                currentState = ObstacleState.ROCKS;
                return;
            }
            if (isUnderground(loc)) {
                if (pitRecoveryTarget != ObstacleState.LOG) {
                    pitRecoveryTarget = ObstacleState.LOG;
                    currentState = ObstacleState.PIT_RECOVERY;
                }
                return;
            }
        }
        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
            TileObject log = getObstacleObj(3);
            if (log == null) {
                List<TileObject> logs = Rs2GameObject.getAll(o -> o.getId() == obstacles.get(3).getObjectId(), 104);
                log = logs.isEmpty() ? null : logs.get(0);
            }
            if (log != null) {
                boolean interacted = Rs2GameObject.interact(log);
                sleep(100);
                if (interacted) {
                    sleep(250); // Wait a short time for animation/movement to start
                    // Inventory check and eat/drop as needed
                    clearInventoryIfNeeded();
                    // Always re-interact with the log
                    Rs2GameObject.interact(log);
                    isWaitingForLog = true;
                    logStartXp = Microbot.getClient().getSkillExperience(AGILITY);
                    logInteractionStartTime = System.currentTimeMillis();
                    lastObstacleInteractTime = System.currentTimeMillis();
                    lastObstaclePosition = Rs2Player.getWorldLocation();
                }
            }
        } else {
            // Check for pit fall while moving/animating
            WorldPoint loc = Rs2Player.getWorldLocation();
            if (isUnderground(loc)) {
                isWaitingForLog = false;
                pitRecoveryTarget = ObstacleState.LOG;  // Set recovery target for wide detection
                currentState = ObstacleState.PIT_RECOVERY;
            }
        }
    }
    private void handleRocks() {
        WorldPoint loc = Rs2Player.getWorldLocation();
        if (loc != null && loc.getY() <= 3933) {
            currentState = ObstacleState.DISPENSER;
            handleDispenser();
            return;
        }
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
                    // If Y <= 3933 after interaction, immediately set state to DISPENSER
                    loc = Rs2Player.getWorldLocation();
                    if (loc != null && loc.getY() <= 3933) {
                        currentState = ObstacleState.DISPENSER;
                        handleDispenser();
                        return;
                    }
                    if (waitForXpChange(startExp, getXpTimeout())) {
                        currentState = ObstacleState.DISPENSER;
                        handleDispenser();
                        return;
                    }
                }
            }
            // Fallback: if player Y < 3934, consider rocks completed
            loc = Rs2Player.getWorldLocation();
            if (loc != null && loc.getY() < 3934) {
                currentState = ObstacleState.DISPENSER;
                handleDispenser();
                return;
            }
        }
    }
    private void handleDispenser() {
        if (Rs2Player.isMoving()) return;
        TileObject dispenser = cachedDispenserObj;
        if (dispenser == null || Rs2Player.getWorldLocation().distanceTo(dispenser.getWorldLocation()) > 12) return;

        // Update inventory value only here
        cachedInventoryValue = getInventoryValue();
        
        int currentTickets = Rs2Inventory.itemQuantity(TICKET_ITEM_ID);
        if (dispenser != null) {
            if (dispenserLootAttempts == 0) {
                dispenserPreValue = getInventoryValue();
                Rs2GameObject.interact(dispenser, "Search");
                sleep(1200);
                sleepUntil(() -> Rs2Inventory.itemQuantity(TICKET_ITEM_ID) > currentTickets, 3000);
                if (Rs2Inventory.itemQuantity(TICKET_ITEM_ID) > currentTickets) {
                    dispenserLoots++;
                    lapCount++;
                    int dispenserValue = getInventoryValue() - dispenserPreValue;
                    String formattedValue = NumberFormat.getIntegerInstance().format(dispenserValue);
                    info("Dispenser Value: " + formattedValue);
                    currentState = ObstacleState.CONFIG_CHECKS;
                    dispenserLootAttempts = 0;
                } else {
                    dispenserLootAttempts = 1; // Try one more time
                }
            } else if (dispenserLootAttempts == 1) {
                dispenserPreValue = getInventoryValue();
                Rs2GameObject.interact(dispenser, "Search");
                sleep(1200);
                sleepUntil(() -> Rs2Inventory.itemQuantity(TICKET_ITEM_ID) > currentTickets, 3000);
                if (Rs2Inventory.itemQuantity(TICKET_ITEM_ID) > currentTickets) {
                    dispenserLoots++;
                    lapCount++;
                    int dispenserValue = getInventoryValue() - dispenserPreValue;
                    String formattedValue = NumberFormat.getIntegerInstance().format(dispenserValue);
                    info("Dispenser Value: " + formattedValue);
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
            bankingProgress = 0;
            currentState = ObstacleState.BANKING;
            return;
        }
        // Only check banking threshold here
        if (cachedInventoryValue >= config.leaveAtValue()) {
            currentState = ObstacleState.BANKING;
            return;
        }
        currentState = ObstacleState.PIPE;
        dispenserLootAttempts = 0;
        // Immediately call handlePipe() if player is ready
        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
            WorldPoint pipeFrontTile = new WorldPoint(3004, 3937, 0);
            WorldPoint loc = Rs2Player.getWorldLocation();
            if (loc != null && loc.distanceTo(pipeFrontTile) <= 4) {
                handlePipe();
            }
        }
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

    private int getActionDelay() { return ACTION_DELAY; }
    private int getXpTimeout() { return XP_TIMEOUT; }
}
