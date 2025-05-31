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

public class WildernessAgilityScript extends Script {
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

    private void info(String msg) { Microbot.log(msg); }

    private boolean isUnderground(WorldPoint loc) {
        return loc != null && loc.getY() > 10000;
    }

    private boolean waitForXpChange(int startXp, int timeoutMs) {
        return sleepUntil(() -> Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY) > startXp, timeoutMs);
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
        currentState = ObstacleState.START;
        startTime = System.currentTimeMillis();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;

                if (Rs2Player.getHealthPercentage() <= 0) {
                    info("Player died, logging out and shutting down script.");
                    sleep(7000);
                    Rs2Player.logout();
                    Microbot.stopPlugin(plugin);
                    return;
                }

                if (System.currentTimeMillis() - lastInventoryCheck > 3000) {
                    cachedInventoryValue = getInventoryValue();
                    lastInventoryCheck = System.currentTimeMillis();
                }

                if (System.currentTimeMillis() - lastObjectCheck > 2000) {
                    cachedDispenserObj = getDispenserObj();
                }

                if (isInPit()) {
                    currentState = ObstacleState.PIT_RECOVERY;
                }

                if (cachedInventoryValue >= config.leaveAtValue()) {
                    currentState = ObstacleState.BANKING;
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
            mainScheduledFuture = null;
        }
        lapCount = 0;
        dispenserLoots = 0;
        startTime = 0;
        currentState = ObstacleState.START;
        super.shutdown();
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
                Rs2GameObject.interact(ladderObj, "Climb-up");
                sleep(600);
                sleepUntil(() -> !Rs2Player.isAnimating() && !Rs2Player.isMoving(), 5000);
            }
            return; // Return here to let the next tick handle the state transition
        }

        // If we're here, we've successfully climbed out of the pit
        if (pitRecoveryTarget != null) {
            WorldPoint targetPoint;
            int obstacleIndex;
            switch (pitRecoveryTarget) {
                case ROPE:
                    targetPoint = new WorldPoint(3005, 3953, 0);
                    obstacleIndex = 1;
                    break;
                case LOG:
                    targetPoint = new WorldPoint(2996, 3955, 0);
                    obstacleIndex = 3;
                    break;
                default:
                    info("Unexpected pit recovery target: " + pitRecoveryTarget);
                    targetPoint = new WorldPoint(3005, 3953, 0); // Default to rope as fallback
                    obstacleIndex = 1;
                    break;
            }
            
            // First walk to the target point
            if (Rs2Player.getWorldLocation().distanceTo(targetPoint) > 4) {
                Rs2Walker.walkTo(targetPoint, 4);
                sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(targetPoint) <= 4, 8000);
                sleep(300, 600); // Small delay after walking
            }

            // Now try to interact with the obstacle
            TileObject obstacle = getObstacleObj(obstacleIndex);
            if (obstacle == null) {
                List<TileObject> obstacles = Rs2GameObject.getAll(o -> o.getId() == this.obstacles.get(obstacleIndex).getObjectId(), 104);
                obstacle = obstacles.isEmpty() ? null : obstacles.get(0);
            }

            if (obstacle != null) {
                // Set up the appropriate waiting state before interaction
                switch (pitRecoveryTarget) {
                    case ROPE:
                        isWaitingForRope = false;
                        ropeStartXp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                        break;
                    case LOG:
                        isWaitingForLog = false;
                        break;
                }

                // Interact with the obstacle
                Rs2GameObject.interact(obstacle);
                sleep(300, 600);
            }

            currentState = pitRecoveryTarget;
            pitRecoveryTarget = null;
        } else {
            // This should never happen now that we set pitRecoveryTarget when detecting falls
            info("No pit recovery target set - defaulting to rope");
            WorldPoint ropeStart = new WorldPoint(3005, 3953, 0);
            Rs2Walker.walkTo(ropeStart, 4);
            sleepUntil(() -> Rs2Player.getWorldLocation().distanceTo(ropeStart) <= 4, 8000);
            currentState = ObstacleState.ROPE;
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
        switch (bankingStepEnum) {
            case WALK:
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
                    bankingStepEnum = BankingStep.OPEN_BANK;
                }
                return;
            case OPEN_BANK:
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
                    bankingStepEnum = BankingStep.RETURN;
                }
                return;
            case RETURN:
                if (!isAt(START_POINT, 2)) {
                    Rs2Walker.walkTo(START_POINT, 2);
                    return;
                }
                TileObject dispenserObj = getDispenserObj();
                if (dispenserObj != null) {
                    Rs2Inventory.use("Coins");
                    sleep(400);
                    Rs2GameObject.interact(dispenserObj, "Use");
                    sleep(getActionDelay());
                    sleepUntil(() -> !Rs2Inventory.hasItem("Coins"), getXpTimeout());
                    if (Rs2Inventory.hasItem("Coins")) {
                        return;
                    }
                }
                needsDispenserUnlock = true;
                bankingStepEnum = BankingStep.WALK;
                currentState = ObstacleState.PIPE;
                break;
        }
    }
    private void handlePipe() {
        if (isWaitingForPipe) {
            WorldPoint loc = Rs2Player.getWorldLocation();
            int currentXp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
            if (loc != null && loc.getY() >= 3950) {
                isWaitingForPipe = false;
                currentState = ObstacleState.ROPE;
                return;
            }
            if (currentXp > pipeStartXp) {
                isWaitingForPipe = false;
                currentState = ObstacleState.ROPE;
                return;
            }
            return;
        }
        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
            WorldPoint loc = Rs2Player.getWorldLocation();
            TileObject pipe = getObstacleObj(0);
            if (pipe == null) return;
            int distanceToPipe = loc.distanceTo(pipe.getWorldLocation());
            if (distanceToPipe > 5) {
                if (!isAt(pipe.getWorldLocation(), 5)) {
                    Rs2Walker.walkTo(pipe.getWorldLocation(), 2);
                }
                return;
            }
            if (distanceToPipe <= 5) {
                boolean interacted = Rs2GameObject.interact(pipe);
                if (interacted) {
                    isWaitingForPipe = true;
                    pipeStartXp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                }
            }
        }
    }
    private void handleRope() {
        if (isWaitingForRope) {
            WorldPoint loc = Rs2Player.getWorldLocation();
            int currentXp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
            if (loc != null && loc.getY() >= 3958) {
                isWaitingForRope = false;
                currentState = ObstacleState.STONES;
                return;
            }
            if (isUnderground(loc)) {
                isWaitingForRope = false;
                pitRecoveryTarget = currentState;
                currentState = ObstacleState.PIT_RECOVERY;
                return;
            }
            if (currentXp > ropeStartXp) {
                isWaitingForRope = false;
                currentState = ObstacleState.STONES;
                return;
            }
            return;
        }
        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
            WorldPoint loc = Rs2Player.getWorldLocation();
            TileObject rope = getObstacleObj(1);
            if (rope != null && loc.distanceTo(rope.getWorldLocation()) <= 5) {
                boolean interacted = Rs2GameObject.interact(rope);
                if (interacted) {
                    isWaitingForRope = true;
                    ropeStartXp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                }
            }
        }
    }
    private void handleStones() {
        if (isWaitingForStones) {
            WorldPoint loc = Rs2Player.getWorldLocation();
            int currentXp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
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
            if (stones != null && loc.distanceTo(stones.getWorldLocation()) <= 10) {
                boolean interacted = Rs2GameObject.interact(stones);
                if (interacted) {
                    isWaitingForStones = true;
                    stonesStartXp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
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
                    isWaitingForLog = false;
                    pitRecoveryTarget = currentState;
                    currentState = ObstacleState.PIT_RECOVERY;
                }
                return;
            }
            TileObject log = getObstacleObj(3);
            if (log == null) {
                List<TileObject> logs = Rs2GameObject.getAll(o -> o.getId() == obstacles.get(3).getObjectId(), 104);
                log = logs.isEmpty() ? null : logs.get(0);
            }
            if (log != null && loc.distanceTo(LOG_WALK_POINT) <= 20) {
                boolean interacted = Rs2GameObject.interact(log);
                sleep(100);
                if (interacted && (Rs2Player.isAnimating() || Rs2Player.isMoving())) {
                    isWaitingForLog = true;
                }
            }
        } else {
            isWaitingForLog = false;
        }
    }
    private void handleRocks() {
        clearInventoryIfNeeded();
        if (!Rs2Player.isAnimating() && !Rs2Player.isMoving()) {
            TileObject rocks = getObstacleObj(4);
            if (rocks == null) {
                List<TileObject> rocksList = Rs2GameObject.getAll(o -> o.getId() == obstacles.get(4).getObjectId(), 104);
                rocks = rocksList.isEmpty() ? null : rocksList.get(0);
            }
            if (rocks != null && Rs2Player.getWorldLocation().distanceTo(rocks.getWorldLocation()) <= 14) {
                int startExp = Microbot.getClient().getSkillExperience(net.runelite.api.Skill.AGILITY);
                if (Rs2GameObject.interact(rocks)) {
                    if (waitForXpChange(startExp, getXpTimeout())) {
                        currentState = ObstacleState.DISPENSER;
                        return;
                    }
                }
            }
        }
    }
    private void handleDispenser() {
        if (Rs2Player.isMoving()) return;
        TileObject dispenser = cachedDispenserObj;
        if (dispenser == null || Rs2Player.getWorldLocation().distanceTo(dispenser.getWorldLocation()) > 12) return;
        int currentTickets = Rs2Inventory.itemQuantity(TICKET_ITEM_ID);
        Rs2GameObject.interact(dispenser, "Search");
        sleep(1200);
        sleepUntil(() -> Rs2Inventory.itemQuantity(TICKET_ITEM_ID) > currentTickets, 3000);
        if (Rs2Inventory.itemQuantity(TICKET_ITEM_ID) > currentTickets) {
            dispenserLoots++;
            lapCount++;
            currentState = ObstacleState.CONFIG_CHECKS;
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
        if (cachedInventoryValue >= config.leaveAtValue()) {
            currentState = ObstacleState.BANKING;
            return;
        }
        clearInventoryIfNeeded();
        currentState = ObstacleState.PIPE;
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
                }
                return;
            }
            int coinCount = Rs2Inventory.itemQuantity("Coins");
            if (coinCount < 150000) {
                currentState = ObstacleState.BANKING;
                return;
            }
            Rs2Inventory.use("Coins");
            sleep(400);
            Rs2GameObject.interact(dispenserObj, "Use");
            sleep(getActionDelay());
            sleepUntil(() -> Rs2Inventory.itemQuantity("Coins") < 150000, getXpTimeout());
            currentState = ObstacleState.PIPE;
            return;
        } else {
            if (!nearDispenser) {
                WorldPoint walkTarget = dispenserObj != null ? dispenserObj.getWorldLocation() : DISPENSER_POINT;
                if (!isAt(walkTarget, 4)) {
                    Rs2Walker.walkTo(walkTarget, 2);
                }
                return;
            }
            currentState = ObstacleState.PIPE;
        }
    }
}
