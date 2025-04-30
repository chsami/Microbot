package net.runelite.client.plugins.microbot.bee.chaosaltar;

import net.runelite.api.GameObject;
import net.runelite.api.ItemID;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.player.Rs2Pvp;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.concurrent.TimeUnit;

import static net.runelite.api.ItemID.*;
import static net.runelite.api.NpcID.CHAOS_FANATIC;
import static net.runelite.client.plugins.microbot.util.walker.Rs2Walker.walkTo;


public class ChaosAltarScript extends Script {

    public static final WorldArea CHAOS_ALTAR_AREA = new WorldArea(2947, 3818, 11, 6, 0);
    public static final WorldPoint CHAOS_ALTAR_POINT = new WorldPoint(2949, 3820,0);
    public static final WorldPoint CHAOS_ALTAR_POINT_SOUTH = new WorldPoint(2972, 3810,0);

    private ChaosAltarConfig config;

    private State currentState = State.UNKNOWN;

    public static boolean test = false;
    public boolean run(ChaosAltarConfig config) {
        Microbot.enableAutoRunOn = false;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn()) return;
                if (!super.run()) return;
                long startTime = System.currentTimeMillis();

                // Determine current state
                currentState = determineState();
                System.out.println("Current state: " + currentState);

                // Execute state action
                switch (currentState) {
                    case BANK:
                        handleBanking();
                        break;
                    case TELEPORT_TO_WILDERNESS:
                        teleportToWilderness();
                        break;
                    case OFFER_BONES:
                        if (config.giveBonesFast()) {offerBonesFast();} else offerBones();
                        break;
                    case WALK_TO_ALTAR:
                        walkTo(CHAOS_ALTAR_POINT_SOUTH);
                        offerBones();
                        break;
                    case DIE_TO_NPC:
                        dieToNpc();
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

    public boolean isAtChaosAltar() {
        for (TileObject obj : Rs2GameObject.getAll()) {
            if (obj.getId() == 411) {
                if (obj instanceof GameObject) {
                    GameObject gameObject = (GameObject) obj;
                    System.out.println("Found Chaos Altar GameObject at: " + gameObject.getWorldLocation());
                    if (Rs2GameObject.isReachable(gameObject)) {
                        System.out.println("Chaos Altar is reachable.");
                        return true;
                    } else {
                        System.out.println("Chaos Altar found but not reachable.");
                    }
                }
            }
        }
        return false;
    }


    private void dieToNpc() {
        System.out.println("Walking to dangerous NPC to die");
        Rs2Walker.walkTo(2978, 3854,0);
        sleepUntil(() -> Rs2Npc.getNpc(CHAOS_FANATIC) != null, 15000);
        // Attack chaos fanatic to die
        Rs2Npc.attack("Chaos Fanatic");
        // Wait until player dies
        sleepUntil(() -> Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS) == 0, 15000);
        sleepUntil(() -> !Rs2Pvp.isInWilderness(), 5000);
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
            walkTo(CHAOS_ALTAR_POINT);
        }
    }

    private void offerBones() {
        System.out.println("Offering bones at altar");
        if (Rs2Player.isInCombat()) {offerBonesFast(); return;}

        if (Rs2Inventory.isFull()){
            walkTo(CHAOS_ALTAR_POINT);
        }

        if (!CHAOS_ALTAR_AREA.contains(Rs2Player.getWorldLocation())) {
            if (Rs2Player.getWorldLocation().getY() > 3650)
            {walkTo(CHAOS_ALTAR_POINT);}
        }

        if (Rs2Inventory.contains(DRAGON_BONES) && isRunning()) {
            Rs2Inventory.slotInteract(2, "use");
            sleep(300, 500);
            Rs2GameObject.interact(411);
            sleep(300, 500);

            int randomWait = Rs2Random.between(1000,5000);
            Rs2Inventory.waitForInventoryChanges(randomWait);

            // Small random delay between offerings
            sleep(200, 400);
        }
    }

    private void offerBonesFast() {
        System.out.println("Offering bones at altar");

        if (Rs2Inventory.isFull()){
            walkTo(CHAOS_ALTAR_POINT);
        }

        if (!CHAOS_ALTAR_AREA.contains(Rs2Player.getWorldLocation())) {
            if (Rs2Player.getWorldLocation().getY() > 3650)
            {walkTo(CHAOS_ALTAR_POINT);}
        }

        if (Rs2Inventory.contains(DRAGON_BONES) && isRunning()) {
            Rs2Inventory.slotInteract(2, "use");
            sleep(100, 300);
            Rs2GameObject.interact(411);
            Rs2Player.waitForXpDrop(Skill.PRAYER);

            // Small random delay between offerings
            sleep(100, 200);
        }
    }


    private void handleBanking() {
        if (!Rs2Bank.isOpen()) {
            System.out.println("Opening bank");
            Rs2Bank.walkToBank();
            Rs2Bank.walkToBankAndUseBank();
        } else {
            Rs2Bank.depositAll();

            if(!Rs2Bank.hasItem(DRAGON_BONES)) {
                Microbot.log("NO BONES, SHUTTING DOWN");
                shutdown();
            }

            if(!Rs2Bank.hasItem(BURNING_AMULET5)) {
                Microbot.log("NO FULL BURNING AMULET, SHUTTING DOWN");
                shutdown();
            }

            // If amulet not equipped or in inventory
            if (!hasBurningAmulet()) {
                sleep(400);
                System.out.println("Withdrawing burning amulet");
                Rs2Bank.withdrawOne(BURNING_AMULET5);
                Rs2Inventory.waitForInventoryChanges(2000);
            }

            // If no bones in inventory, withdraw 28
            if (!Rs2Inventory.contains(DRAGON_BONES)) {
                System.out.println("Withdrawing bones");
                Rs2Bank.withdrawAll(DRAGON_BONES);
                Rs2Inventory.waitForInventoryChanges(2000);
            }

            Rs2Bank.closeBank();
        }
    }

    public boolean hasBurningAmulet() {
        return Rs2Inventory.contains(ItemID.BURNING_AMULET1) ||
                Rs2Inventory.contains(ItemID.BURNING_AMULET2) ||
                Rs2Inventory.contains(ItemID.BURNING_AMULET3) ||
                Rs2Inventory.contains(ItemID.BURNING_AMULET4) ||
                Rs2Inventory.contains(BURNING_AMULET5);
    }

    private State determineState() {
        boolean inWilderness = Rs2Pvp.isInWilderness();
        boolean hasBones = Rs2Inventory.count(DRAGON_BONES) > 4;
        boolean hasAnyBones = Rs2Inventory.contains(DRAGON_BONES);
        boolean atAltar = isAtChaosAltar();

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
        super.shutdown();
    }
}
