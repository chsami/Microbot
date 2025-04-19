package net.runelite.client.plugins.microbot.bee.chaosaltar;

import net.runelite.api.*;
import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
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
    public static final WorldPoint CHAOS_ALTAR_POINT = new WorldPoint(2949, 3821,0);
    public static final WorldPoint CHAOS_ALTAR_POINT_SOUTH = new WorldPoint(2949, 3813,0);

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
                        offerBones();
                        break;
                    case WALK_TO_ALTAR:
                        walkTo(CHAOS_ALTAR_POINT_SOUTH);
                        sleep(100,600);
                        walkTo(CHAOS_ALTAR_POINT);
                        sleep(300,600);
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
                Tile altarTile = obj.getWorldView().getSelectedSceneTile();
                if (Rs2GameObject.isReachable((GameObject) altarTile)) {
                    return true;
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

        if (Rs2Inventory.contains(DRAGON_BONES) && isRunning()) {
            Rs2Inventory.slotInteract(20, "use");
            Rs2Inventory.useItemOnObject(BIG_BONES, 411);
            sleep(300, 500);

            Rs2Inventory.waitForInventoryChanges(5000);

            // Small random delay between offerings
            sleep(200, 400);
        }
    }

    private void handleBanking() {
        if (!Rs2Bank.isOpen()) {
            System.out.println("Opening bank");
            Rs2Bank.walkToBankAndUseBank();
        } else {
            Rs2Bank.depositAll();
            // If amulet not equipped or in inventory
            if (!hasBurningAmulet()) {
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
