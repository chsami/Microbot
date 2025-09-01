package net.runelite.client.plugins.microbot.moonsOfPeril.handlers;

import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.moonsOfPeril.enums.Widgets;
import net.runelite.client.plugins.microbot.moonsOfPeril.moonsOfPerilConfig;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;


import java.util.*;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public final class BossHandler {

    private final int healthPercentage;
    private final int prayerPercentage;
    private final boolean debugLogging;

    public BossHandler(moonsOfPerilConfig cfg) {
        this.healthPercentage = cfg.healthPercentage();
        this.prayerPercentage = cfg.prayerPercentage();
        this.debugLogging = cfg.debugLogging();
    }

    /** Walks to the chosen boss lobby. */
    public void walkToBoss(Rs2InventorySetup inventorySetup, String bossName, WorldPoint bossWorldPoint) {
        if (inventorySetup != null) {
            equipInventorySetup(inventorySetup);
        }
        if (debugLogging) {Microbot.log("Walking to " + bossName + " lobby");}
        Rs2Walker.walkWithState(bossWorldPoint, 0);
        sleep(600);
        if (!Rs2Player.getWorldLocation().equals(bossWorldPoint)) {
            Rs2Walker.walkFastCanvas(bossWorldPoint);
            sleepUntil(() -> Rs2Player.getWorldLocation().equals(bossWorldPoint));
        }
        if (Rs2Player.getWorldLocation().distanceTo(bossWorldPoint) <= 3) {
            if (debugLogging) {Microbot.log("Arrived at " + bossName + " lobby");}
            return;
        }
        if (debugLogging) {Microbot.log("Something went wrong. Did not arrive at " + bossName + " lobby");}
    }

    /** True while the “boss check mark” widget is hidden on-screen. i.e. the boss is alive*/
    public boolean bossIsAlive(String bossName, int aliveWidgetId) {
        if (debugLogging) {Microbot.log(bossName + " is alive: " + Rs2Widget.isHidden(aliveWidgetId));}
        return Rs2Widget.isHidden(aliveWidgetId);
    }

    /** Interacts with the Boss statue and waits for the player to be teleported into Boss Arena*/
    public void enterBossArena(String bossName, int bossStatueID, WorldPoint bossWorldPoint) {
        if (!Rs2Player.getWorldLocation().equals(bossWorldPoint)) {
            if (Rs2Walker.walkFastCanvas(bossWorldPoint)) {
                if (debugLogging) {Microbot.log("Walking to statue tile");}
            }
            sleepUntil(() -> Rs2Player.getWorldLocation().equals(bossWorldPoint));
        }
        if (Rs2GameObject.interact(bossStatueID, "Use")) {
            if (debugLogging) {Microbot.log("Entering " + bossName + " arena");}
            sleepUntil(() -> !Rs2Player.getWorldLocation().equals(bossWorldPoint),5_000);
        }
    }

    /** 1. Equips the player's chosen weapon + offhand
     * 2. Eats food if required
     * 3. Drinks potions if required
     * 4. Turns on Player's best offensive melee prayer*/
    public void fightPreparation(Rs2InventorySetup inventorySetup) {
        equipInventorySetup(inventorySetup);
        sleep(600);
        eatIfNeeded();
        sleep(600);
        drinkIfNeeded();
        sleep(600);
        Rs2Player.toggleRunEnergy(true);
        Rs2Camera.resetPitch();
        Rs2Camera.resetZoom();
    }

    /**
     * Equips the inventory setup.
     */
    public void equipInventorySetup(Rs2InventorySetup inventorySetup) {
        if (!inventorySetup.doesEquipmentMatch()) {
            inventorySetup.wearEquipment();
        }
    }

    /**
     * Eats food if hitpoints below percentage threshold
     */
    public void eatIfNeeded() {
        Rs2Player.eatAt(healthPercentage);
    }

    /**
     * Drinks prayer potion if prayer points below percentage threshold
     */
    public void drinkIfNeeded() {
        int maxPrayer = Rs2Player.getRealSkillLevel(Skill.PRAYER);
        int minimumPrayerPoint = (maxPrayer * prayerPercentage) / 100;
        if (!Rs2Player.hasMoonlightActive()) {
            Rs2Player.drinkPrayerPotion();
        }
        Rs2Player.drinkPrayerPotionAt(minimumPrayerPoint);
    }

    /**
     * Turns on Player's best offensive melee prayer and returns true
     */
    public static void meleePrayerOn() {
        Rs2Prayer.toggle(Objects.requireNonNull(Rs2Prayer.getBestMeleePrayer()), true);
    }

    /**
     * Returns true if the sigil NPC (the highlighted attack tile) is present
     */
    public static boolean isNormalAttackSequence(int sigilNpcID) {
        return Rs2Npc.getNpc(sigilNpcID) != null;
    }

    /**
     * “Normal” attack phase: follow ≤ 3 sigil squares, stand on the
     * matching attack tile, and keep attacking the boss.
     *
     * @param sigilNpcID  NPC ID of the 2×2 sigil marker
     * @param bossNpcID   NPC ID of the Eclipse boss
     * @param attackTiles WorldPoints from Locations enum
     */
    public void normalAttackSequence(int sigilNpcID,
                                            int bossNpcID,
                                            WorldPoint[] attackTiles, Rs2InventorySetup inventorySetup)
    {
        if (debugLogging) {Microbot.log("Script has entered the normal attack sequence loop");}
        WorldPoint lastSigilSW = null;
        WorldPoint currentTarget = null;
        int sigilMoves = 0;
        equipInventorySetup(inventorySetup);
        sleep(150);
        meleePrayerOn();
        Rs2Player.toggleRunEnergy(true);
        sleep(150);

        while (sigilMoves <= 3 && isNormalAttackSequence(sigilNpcID))
        {
            /* 1 ─ detect a new sigil square */
            Rs2NpcModel sigil = Rs2Npc.getNpc(sigilNpcID);
            if (sigil == null) {
                sleep(300);
                if (debugLogging) {Microbot.log("Sigil not found. Breaking out of sequence");}
                break;
            }
            WorldPoint sigilLocation = sigil.getWorldLocation();
            if (!sigilLocation.equals(lastSigilSW)) {
                ++sigilMoves;
                lastSigilSW = sigilLocation;

                /* 2 ─ choose *once* the nearest predefined attack tile ≤1 away */
                currentTarget = sigilLocation;
                for (WorldPoint atk : attackTiles) {
                    if (atk.distanceTo(sigilLocation) <= 1) {
                        currentTarget = atk;
                        break;
                    }
                }
                if (debugLogging) {Microbot.log("Sigil #" + sigilMoves + ": target tile = " + currentTarget);}
            }

            /* 3 ─ run onto the target tile if not already there */
            if (Rs2Player.distanceTo(currentTarget) > 1) {
                if (debugLogging) {Microbot.log("Running to attack tile target location: " + currentTarget);}
                if (Rs2Walker.walkFastCanvas(currentTarget, true)) {
                    final WorldPoint dest = currentTarget;
                    sleepUntil(() -> Rs2Player.getWorldLocation().equals(dest), 3_000);
                }
            }

            /* 4 ─ attack the boss whenever not in combat */
            Rs2NpcModel boss = Rs2Npc.getNpc(bossNpcID);
            if (boss != null && !Rs2Combat.inCombat()) {
                if (debugLogging) {Microbot.log("Attacking the boss");}
                Rs2Npc.attack(bossNpcID);
            }

            sleep(300);
        }
        if (debugLogging) {Microbot.log("Breaking out of the normal attack sequence");}
    }

    /** True if the WorldPoint param is located on a dangerous tile*/
    public static boolean inDanger(WorldPoint location) {
        return Rs2Tile.getDangerousGraphicsObjectTiles().containsKey(location);
    }

    /** Runs the player out of the arena */
    public void bossBailOut(WorldPoint bailOutLocation) {
        int exitStairsGroundObjectID = 53003;
        long endTime = System.currentTimeMillis() + 10_000;

        while (System.currentTimeMillis() < endTime) {
            if (Rs2GameObject.interact(exitStairsGroundObjectID)) {
                sleepUntil(() -> Rs2Widget.isWidgetVisible(Widgets.BOSS_HEALTH_BAR.getID()),5_000);
                if (debugLogging) {Microbot.log("Successfully bailed out of the boss arena");}
                return;
            }

            if (debugLogging) {Microbot.log("Couldn't interact with the door. Attempting to move closer");}
            Rs2Walker.walkFastCanvas(bailOutLocation, true);
            sleep(600);
        }
        if (debugLogging) {Microbot.log("Timeout: Failed to bail out of the boss arena after 10 seconds.");}
    }

    /** If current run energy is less than 80%, recharges run energy at a campfire located on the world canvas */
    public static void rechargeRunEnergy() {
        if (Rs2GameObject.getGameObject(ObjectID.PMOON_RANGE) != null && Rs2Player.getRunEnergy() <=80) {
            Rs2GameObject.interact(ObjectID.PMOON_RANGE, "Make-cuppa");
            sleep(600);
        }
    }
}
