package net.runelite.client.plugins.microbot.bee.chaosaltar;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameObject;
import net.runelite.api.Skill;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static net.runelite.api.ItemID.DRAGON_BONES;
import static net.runelite.api.NpcID.CHAOS_FANATIC;
import static net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkTo;

@Slf4j
public class ChaosAltarScript extends Script {

    public static final WorldArea CHAOS_ALTAR_AREA = new WorldArea(2947, 3818, 11, 6, 0);
    public static final WorldPoint CHAOS_ALTAR_POINT = new WorldPoint(2949, 3820,0);
    public static final WorldPoint CHAOS_ALTAR_POINT_SOUTH = new WorldPoint(2972, 3810,0);

    private static final int CHAOS_ALTAR = 411;

    private ChaosAltarConfig config;
    private boolean autoRetaliate = false;

    private State currentState = State.UNKNOWN;

    public static boolean didWeDie = false;

    public boolean run(ChaosAltarConfig config, ChaosAltarPlugin plugin) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                if (!autoRetaliate) {
                    Rs2Combat.setAutoRetaliate(false);
                    autoRetaliate = true;
                }

                // Determine current state
                currentState = determineState();
                Microbot.log("Current state: " + currentState);

                // Execute state action
                switch (currentState) {
                    case BANK:
                        plugin.lockCondition.lock();
                        handleBanking();
                        break;
                    case TELEPORT_TO_WILDERNESS:
                        teleportToWilderness();
                        break;
                    case OFFER_BONES:
                        if (config.giveBonesFast()) offerBonesFast();
                        else offerBones();
                        break;
                    case WALK_TO_ALTAR:
                        walkTo(CHAOS_ALTAR_POINT_SOUTH);
                        if (config.giveBonesFast()) offerBonesFast();
                        else offerBones();
                        break;
                    case DIE_TO_NPC:
                        dieToNpc();
                        plugin.lockCondition.unlock();
                        handleBanking();
                        break;
                    default:
                        System.out.println("Unknown state. Resetting...");
                        break;
                }

                long endTime = System.currentTimeMillis();
                long totalTime = endTime - startTime;
                System.out.println("Total time for loop " + totalTime);

            } catch (Exception ex) {
                System.out.println(ex.getMessage());
            }
        }, 0, 1000, TimeUnit.MILLISECONDS);
        return true;
    }

    private GameObject getChaosAltar() {
        return (GameObject) Rs2GameObject
                .getAll(obj -> obj.getId() == CHAOS_ALTAR && obj instanceof GameObject)
                .stream().findFirst().orElse(null);
    }

    public boolean isAtChaosAltar() {
        final GameObject gameObject = getChaosAltar();
        if (gameObject == null) return false;

        final boolean reachable = Rs2GameObject.isReachable(gameObject);
        log.info("Found Chaos Altar GameObject at: {}. Reachable={}", gameObject.getWorldLocation(), reachable);
        return reachable;
    }


    private void dieToNpc() {
        Microbot.log("Walking to dangerous NPC to die");
        Rs2Walker.walkTo(2979, 3845,0);
        sleepUntil(() -> Rs2Npc.getNpc(CHAOS_FANATIC) != null, 60000);
        // Attack chaos fanatic to die
        Rs2Npc.attack("Chaos Fanatic");
        // Wait until player dies
        sleepUntil(() -> Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) == 0, 60000);
        sleepUntil(() -> !Rs2Pvp.isInWilderness(), 15000);
        sleep(1000,2000);
    }


    private void teleportToWilderness() {

        // Enable protect item if needed
        if (!Rs2Prayer.isPrayerActive(Rs2PrayerEnum.PROTECT_ITEM)) {
            System.out.println("Enabling Protect Item");
            Rs2Prayer.toggle(Rs2PrayerEnum.PROTECT_ITEM, true);
            sleep(500, 800);
        }

        if (hasBurningAmulet()) {
            walkTo(CHAOS_ALTAR_POINT_SOUTH);
        }
    }

    private Rs2ItemModel getLastBone() {
        return Rs2Inventory.getBones().stream()
                .max(Comparator.comparingInt(Rs2ItemModel::getSlot)).orElse(null);
    }

    private void offerBones() {
        System.out.println("Offering bones at altar- IN OFFERBONES1");

        if (!CHAOS_ALTAR_AREA.contains(Rs2Player.getWorldLocation())) {
            if (Rs2Player.getWorldLocation().getY() > 3650) {
                walkTo(CHAOS_ALTAR_POINT);
            }
        }

        if (Rs2Player.isInCombat()) {
            offerBonesFast();
            return;
        }

        final Rs2ItemModel lastBones = getLastBone();
        if (lastBones != null && isRunning()) {
            Rs2Inventory.interact(lastBones, "use");
            sleep(300, 500);
            Rs2GameObject.interact(CHAOS_ALTAR);
            sleep(300, 500);

            Rs2Inventory.waitForInventoryChanges(Rs2Random.between(500,2000));
        }
    }

    private void offerBonesFast() {
        Microbot.log("Offering bones at altar - IN OFFERBONES");

        if (!CHAOS_ALTAR_AREA.contains(Rs2Player.getWorldLocation())) {
            if (Rs2Player.getWorldLocation().getY() > 3650) {walkTo(
                    CHAOS_ALTAR_POINT);
            }
        }

        Rs2ItemModel lastBones;
        while ((lastBones = getLastBone()) != null
                && isRunning()
                && !Rs2Player.isInCombat()
                && Rs2GameObject.exists(CHAOS_ALTAR)) {
            Rs2Inventory.interact(lastBones, "use");
            sleep(100, 300);
            Rs2GameObject.interact(CHAOS_ALTAR);
            Rs2Player.waitForXpDrop(Skill.PRAYER);

            // Small random delay between offerings
            sleep(100, 200);
        }
    }

    private void handleBanking() {
        if(Rs2Inventory.contains(Rs2ItemModel.matches(false, "Burning amulet"))){
            Rs2Inventory.wear("Burning amulet");
        }

        if (!Rs2Bank.isOpen()) {
            log.info("Opening bank");
            if (!Rs2Bank.walkToBankAndUseBank()) {
                log.error("Failed to walk to or use bank");
                return;
            }
        }

        log.info("Depositing All");
        Rs2Bank.depositAll();

        if(!Rs2Bank.hasItem(DRAGON_BONES)) {
            Microbot.log("NO BONES, SHUTTING DOWN");
            shutdown();
            return;
        }

        if(!Rs2Bank.hasBankItem("Burning Amulet")) {
            Microbot.log("NO BURNING AMULET, SHUTTING DOWN");
            shutdown();
            return;
        }

        // If amulet not equipped or in inventory
        if (!hasBurningAmulet()) {
            sleep(400);
            Microbot.log("Withdrawing burning amulet");
            Rs2Bank.withdrawAndEquip("burning amulet");
            Rs2Inventory.waitForInventoryChanges(2000);
        }

        // If no bones in inventory, withdraw 28
        if (!Rs2Inventory.contains(DRAGON_BONES)) {
            log.info("Withdrawing bones");
            Rs2Bank.withdrawAll(DRAGON_BONES);
            Rs2Inventory.waitForInventoryChanges(2000);
        }

        log.info("Closing Bank. Ammy={}, Bones={}", hasBurningAmulet(), Rs2Inventory.getBones().size());
        if (!Rs2Bank.closeBank()) {
            log.error("Failed to close bank");
        }
    }

    public boolean hasBurningAmulet() {
        return Rs2Inventory.contains(x-> x != null && x.getName().contains("Burning amulet")) || Rs2Equipment.isWearing("Burning amulet", false);
    }

    private State determineState() {
        final int boneCount = Rs2Inventory.getBones().size();
        final boolean inWilderness = Rs2Pvp.isInWilderness();
        final boolean hasBones = boneCount > 4;
        final boolean hasAnyBones = boneCount > 0;
        final boolean atAltar = isAtChaosAltar();

        if(didWeDie){
            didWeDie = false;
            Microbot.log("We died! Going to the bank...");
            return State.BANK;
        }
        if (!inWilderness && !hasBones) {
            return State.BANK;
        }
        if (!inWilderness && hasBones) {
            return State.TELEPORT_TO_WILDERNESS;
        }
        if (inWilderness && hasAnyBones && !atAltar) {
            return State.WALK_TO_ALTAR;
        }
        if (inWilderness && hasAnyBones && atAltar) {
            return State.OFFER_BONES;
        }
        if (inWilderness && !hasAnyBones) {
            return State.DIE_TO_NPC;
        }

        return State.UNKNOWN;
    }

    @Override
    public void shutdown() {
        autoRetaliate = false;
        super.shutdown();
    }
}
