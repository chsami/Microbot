package net.runelite.client.plugins.microbot.runecrafting.arceuus;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import org.apache.commons.lang3.NotImplementedException;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class ArceuusRcScript extends Script {
    public static final String version = "1.0.2";

    private static ArceuusRcConfig config;

    private static final int BLOOD_ESSENCE_ACTIVE = ItemID.BLOOD_ESSENCE_ACTIVE;
    private static final int BLOOD_ESSENCE = ItemID.BLOOD_ESSENCE_INACTIVE;

    private static final int DARK_ESSENCE_FRAGMENTS = ItemID.BIGBLANKRUNE;
    private static final int DENSE_ESSENCE_BLOCK = ItemID.ARCEUUS_ESSENCE_BLOCK;
    private static final int DARK_ESSENCE_BLOCK = ItemID.ARCEUUS_ESSENCE_BLOCK_DARK;

    private static final String DARK_ALTAR = "Dark altar";
    private static final String STR_DENSE_RUNESTONE = "Dense runestone";

    private static final WorldArea ARCEUUS_RC_AREA = new WorldArea(1672, 3819, 171, 93, 0);

    private static final WorldPoint ARCEUUS_BLOOD_ALTAR = new WorldPoint(1720, 3828, 0);
    private static final WorldPoint ARCEUUS_SOUL_ALTAR = new WorldPoint(1815, 3856, 0);
    private static final WorldPoint ARCEUUS_DARK_ALTAR = new WorldPoint(1718, 3880, 0);
    private static final WorldPoint DENSE_RUNESTONE = new WorldPoint(1760, 3853, 0);

    private static final int REACHED_DISTANCE = 5;

    @Getter
    private String state = "Unknown";
    private boolean hasChippedEssence = false;

    public boolean run(ArceuusRcConfig config) {
        ArceuusRcScript.config = config;
        Rs2Antiban.antibanSetupTemplates.applyUniversalAntibanSetup();
        hasChippedEssence = false;
        if(Microbot.isLoggedIn()) {
            hasChippedEssence = Rs2Inventory.hasItem(DARK_ESSENCE_FRAGMENTS);
        }
        if (config.showUpdateMessage()) log.info("Arceuus RC - Try out the new faster chiseling & soul altar!");
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(this::executeTask, 0, 600, TimeUnit.MILLISECONDS);
        return true;
    }

    private State getCurrentState() {
        final WorldPoint myLocation = Rs2Player.getWorldLocation();

        final int distanceToRuneStone = myLocation.distanceTo(DENSE_RUNESTONE);
        if (distanceToRuneStone < 20) {
            log.debug("At Runestone");
            if (Rs2Inventory.isFull()) {
                if (Rs2Inventory.hasItem(DENSE_ESSENCE_BLOCK)) {
                    return State.GO_TO_DARK_ALTAR;
                }
                log.warn("Was at rune stone but mined no essence blocks");
                if (Rs2Inventory.hasItem(DARK_ESSENCE_FRAGMENTS)) {
                    return State.GO_TO_ALTAR;
                }
                log.error("At runestone with full inv and no essence");
                return State.UNKNOWN;
            }
            if (distanceToRuneStone <= REACHED_DISTANCE) return State.MINE_ESSENCE;
            // walk closer so the object search can find it
            log.warn("Reactivating walker to walk closer to dense runestone");
            return State.GO_TO_RUNESTONE;
        }

        final int distanceToAltar = myLocation.distanceTo(getAltarWorldPoint());
        if (distanceToAltar < 20) {
            log.debug("At soul or blood altar");
            if (Rs2Inventory.hasItem(DARK_ESSENCE_FRAGMENTS)) {
                if (distanceToAltar <= REACHED_DISTANCE) return State.USE_ALTAR;
                // walk closer so the object search can find it
                log.warn("Reactivating walker to walk closer to altar");
                return State.GO_TO_ALTAR;
            }
            if (Rs2Inventory.hasItem(DARK_ESSENCE_BLOCK)) return State.CHIP_ESSENCE;
            return State.GO_TO_RUNESTONE;
        }

        final int distanceToDarkAltar = myLocation.distanceTo(ARCEUUS_DARK_ALTAR);
        if (distanceToDarkAltar < 20) {
            log.debug("At Dark Altar");
            if (Rs2Inventory.hasItem(DENSE_ESSENCE_BLOCK)) {
                if (distanceToDarkAltar <= REACHED_DISTANCE) return State.USE_DARK_ALTAR;
                log.warn("Reactivating walker to walk closer to dark altar");
                return State.GO_TO_DARK_ALTAR;
            }

            if (hasChippedEssence) {
                if (Rs2Inventory.isFull() || Rs2Inventory.hasItem(DARK_ESSENCE_BLOCK)) return State.GO_TO_ALTAR;
            } else if (Rs2Inventory.hasItem(DARK_ESSENCE_BLOCK)) return State.CHIP_ESSENCE;
            return State.GO_TO_RUNESTONE;

        }

        // user or walker error if we end up here
        if (ARCEUUS_RC_AREA.contains(myLocation)) {
            log.warn("Detected script error attempting recovery");
            hasChippedEssence = Rs2Inventory.hasItem(DARK_ESSENCE_FRAGMENTS);
            if (Rs2Inventory.isFull()) {
                if (Rs2Inventory.hasItem(DENSE_ESSENCE_BLOCK)) return State.GO_TO_DARK_ALTAR;
                if (!hasChippedEssence && Rs2Inventory.hasItem(DARK_ESSENCE_BLOCK)) return State.CHIP_ESSENCE;
                return State.GO_TO_ALTAR;
            }
            return State.GO_TO_RUNESTONE;
        }

        // wait for user to navigate to area
        while (!ARCEUUS_RC_AREA.contains(Rs2Player.getWorldLocation())) {
            log.error("We are not near anything - Please walk to the starting location");
            sleepUntil(() -> ARCEUUS_RC_AREA.contains(Rs2Player.getWorldLocation()), () -> {}, 60_000, 1_000);
        }
        int resumeSeconds = 4;
        while (resumeSeconds-- > 0) {
            log.info("Arceuus RC taking over in {}", resumeSeconds);
            sleep(1_000);
        }
        return State.UNKNOWN;
    }

    private void logWalk(WorldPoint dst) {
        WorldPoint myLocation = Rs2Player.getWorldLocation();
        if (myLocation == null) {
            log.error("MyLocation is null");
            return;
        }
        BreakHandlerScript.setLockState(true);
        log.info("Walking from ({},{},{}) to ({},{},{})",
                myLocation.getX(), myLocation.getY(), myLocation.getPlane(),
                dst.getX(), dst.getY(), dst.getPlane()
        );
        Rs2Walker.walkTo(dst, REACHED_DISTANCE);
        BreakHandlerScript.setLockState(false);
    }

    private void executeTask() {
        try {
            if (!super.run() || !Microbot.isLoggedIn()) {
                state = "Disabled";
                return;
            }

            State state = getCurrentState();
            log.debug("Current State={}", state);
            this.state = String.format("(%s) %s", getAltarName(), state);
            switch (state) {
                case GO_TO_RUNESTONE:
                    logWalk(DENSE_RUNESTONE);
                    break;
                case GO_TO_DARK_ALTAR:
                    logWalk(ARCEUUS_DARK_ALTAR);
                    break;
                case GO_TO_ALTAR:
                    logWalk(getAltarWorldPoint());
                    break;
                case MINE_ESSENCE:
                    mineEssence();
                    break;
                case USE_DARK_ALTAR:
                    useDarkAltar();
                    break;
                case CHIP_ESSENCE:
                    this.state += config.getChipEssenceFast() ? "_FAST" : "";
                    chipEssence(config.getChipEssenceFast());
                    break;
                case USE_ALTAR:
                    useAltar();
                    break;
                case UNKNOWN:
                    break;
                default:
                    log.error("Action not defined for State={}", state);
            }
        } catch (Exception e) {
            // in-case we error before setting it
            hasChippedEssence = Rs2Inventory.hasItem(DARK_ESSENCE_FRAGMENTS);
            Microbot.log("Error in Arceuus Runecrafter: " + e.getMessage());
        }
    }

    public Altar getAltar() {
        if (config.getAltar() != Altar.AUTO) return config.getAltar();
        final int level = Microbot.getClient().getRealSkillLevel(Skill.RUNECRAFT);
        // Cache not updated - but we don't want to shut down
        if (level == 0) throw new IllegalStateException("Runecraft Level cannot be 0");
        if (level >= 90) return Altar.SOUL;
        if (level >= 77) return Altar.BLOOD;

        this.shutdown();
        this.state = "Runecraft Level " + level + " to low";
        throw new IllegalStateException("Runecraft Level " + level + " to low");
    }

    public WorldPoint getAltarWorldPoint() {
        if (getAltar() == Altar.BLOOD) return ARCEUUS_BLOOD_ALTAR;
        return ARCEUUS_SOUL_ALTAR;
    }

    public String getAltarName() {
        if (getAltar() == Altar.BLOOD) return "Blood Altar";
        return "Soul Altar";
    }

    public void useAltar() {
        final GameObject altar = Rs2GameObject.getGameObject(getAltarName(), true, 11);
        if (altar != null) {
            if (Rs2GameObject.interact(altar,"Bind")) Rs2Inventory.waitForInventoryChanges(6_000);
            hasChippedEssence = Rs2Inventory.hasItem(DARK_ESSENCE_FRAGMENTS);
        }
    }

    public boolean moveChisel() {
        if (Rs2Inventory.slotContains(27, ItemID.CHISEL)) return true;
        final Rs2ItemModel chisel = Rs2Inventory.get(ItemID.CHISEL);
        if (chisel == null) {
            Microbot.log("No chisel found in inventory");
            return false;
        }
        if (Rs2Inventory.moveItemToSlot(chisel,27)) {
            if (!sleepUntil(() -> Rs2Inventory.slotContains(27, ItemID.CHISEL),6_000)) {
                Microbot.log("Failed to move chisel to slot 27");
                return false;
            }
        }
        return true;
    }

    public void chipEssence(boolean fast) {
        if (!moveChisel()) return;
        if (fast) chipEssenceFast();
        else chipEssenceSlow();
        hasChippedEssence = Rs2Inventory.hasItem(DARK_ESSENCE_FRAGMENTS);
        if (!hasChippedEssence) log.error("Failed to chip essence");
    }

    public boolean chipEssenceSlow() {
        if(Rs2Inventory.combineClosest(DARK_ESSENCE_BLOCK,ItemID.CHISEL)) {
            int blocks = Rs2Inventory.count(DARK_ESSENCE_BLOCK);
            while (blocks > 0) {
                Rs2Inventory.waitForInventoryChanges(5_000);
                final int newBlocks = Rs2Inventory.count(DARK_ESSENCE_BLOCK);
                if (newBlocks == blocks) {
                    log.warn("Failed to chip full inventory");
                    return false;
                }
                blocks = newBlocks;
            }
            return true;
        }
        log.warn("Failed to combine closest");
        return false;
    }

    private void reverse(int[] ints) {
        if (ints.length != 2) throw new NotImplementedException("reverse does not support length != 2");
        final int tmp = ints[0];
        ints[0] = ints[1];
        ints[1] = tmp;
    }

    public boolean chipEssenceFast() {
        final int[] ids = {ItemID.CHISEL, DARK_ESSENCE_BLOCK};
        long lastUpdate = System.currentTimeMillis();
        int blocks = Rs2Inventory.count(DARK_ESSENCE_BLOCK);
        while (Rs2Inventory.hasItem(ItemID.CHISEL) && blocks > 0) {
            if (!Rs2Inventory.combineClosest(ids[0], ids[1])) {
                Microbot.log("Failed to combine closest chisel & dark essence block");
                return false;
            }
            reverse(ids);
            if (System.currentTimeMillis()-lastUpdate > 3_000) {
                log.warn("Probably have max essence stopping combine");
                return true;
            }
            final int newBlocks = Rs2Inventory.count(DARK_ESSENCE_BLOCK);
            if (newBlocks < blocks) {
                lastUpdate = System.currentTimeMillis();
                blocks = newBlocks;
            }
        }
        return true;
    }

    public void useDarkAltar() {
        final GameObject darkAltar = Rs2GameObject.getGameObject(DARK_ALTAR, true, 11);
        if (darkAltar == null) return;

        Rs2GameObject.interact(darkAltar,"Venerate");
        sleepUntil(()->!Rs2Inventory.hasItem(DENSE_ESSENCE_BLOCK),6_000);
    }

    public void mineEssence() {
        if(getAltar() == Altar.BLOOD && !Rs2Inventory.hasItem(BLOOD_ESSENCE_ACTIVE)){
            Rs2Inventory.interact(BLOOD_ESSENCE, "Activate");
        }
        final GameObject runeStone = Rs2GameObject.getGameObject(STR_DENSE_RUNESTONE, true, 11);
        if (runeStone == null) { // should never happen bc shouldMineEssence checks for the runestone
            Microbot.log("Cannot find runestone");
            return;
        }
        Rs2GameObject.interact(runeStone,"Chip");

        // this checks if we are gaining essence from mining
        final AtomicInteger emptyCount = new AtomicInteger(Rs2Inventory.emptySlotCount());
        Rs2Player.waitForAnimation(10_000);
        while (emptyCount.get() > 0) {
            if (!Rs2Player.isAnimating(1_800)) return; // runestone probably mined - need to switch
            if (sleepUntil(() -> {
                if (!Rs2Player.isAnimating(1_800)) return true;
                final int newEmptyCount = Rs2Inventory.emptySlotCount();
                if (newEmptyCount >= emptyCount.get()) return false;
                emptyCount.set(newEmptyCount);
                return true;
            }, 10_000)) continue;
            log.warn("Failed to await mining essence");
            return;
        }

    }

    @Override
    public void shutdown() {
        super.shutdown();
    }

    private enum State {
        GO_TO_RUNESTONE,
        GO_TO_DARK_ALTAR,
        GO_TO_ALTAR, // blood or soul
        MINE_ESSENCE,
        USE_DARK_ALTAR,
        CHIP_ESSENCE,
        USE_ALTAR,
        UNKNOWN
    }
}
