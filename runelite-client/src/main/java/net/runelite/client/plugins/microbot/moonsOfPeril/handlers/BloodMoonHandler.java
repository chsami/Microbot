package net.runelite.client.plugins.microbot.moonsOfPeril.handlers;

import net.runelite.api.GameObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.moonsOfPeril.enums.GameObjects;
import net.runelite.client.plugins.microbot.moonsOfPeril.enums.Locations;
import net.runelite.client.plugins.microbot.moonsOfPeril.enums.State;
import net.runelite.client.plugins.microbot.moonsOfPeril.enums.Widgets;
import net.runelite.client.plugins.microbot.moonsOfPeril.moonsOfPerilConfig;
import net.runelite.client.plugins.microbot.moonsOfPeril.moonsOfPerilPlugin;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class BloodMoonHandler implements BaseHandler {

    private static final String bossName = "Blood Moon";
    private static final int bossHealthBarWidgetID = Widgets.BOSS_HEALTH_BAR.getID();
    private static final int bossStatusWidgetID = Widgets.BLOOD_MOON_ID.getID();
    private static final int bossStatueObjectID = GameObjects.BLOOD_MOON_STATUE_ID.getID();
    private static final WorldPoint bossLobbyLocation = Locations.BLOOD_LOBBY.getWorldPoint();
    private static final WorldPoint[] ATTACK_TILES = Locations.bloodAttackTiles();
    private final int sigilNpcID = GameObjects.SIGIL_NPC_ID.getID();
    private final boolean enableBoss;
    private final Rs2InventorySetup equipmentNormal;
    private static final WorldPoint afterRainTile = Locations.BLOOD_ATTACK_6.getWorldPoint();
    public boolean arrived = false;
    private final BossHandler boss;
    private final boolean debugLogging;

    public BloodMoonHandler(moonsOfPerilConfig cfg, Rs2InventorySetup equipmentNormal) {
        this.enableBoss = cfg.enableBlood();
        this.equipmentNormal = equipmentNormal;
        this.boss = new BossHandler(cfg);
        this.debugLogging = cfg.debugLogging();
    }

    @Override
    public boolean validate() {
        if (!enableBoss) {
            return false;
        }
        return (boss.bossIsAlive(bossName, bossStatusWidgetID));
    }

    @Override
    public State execute() {
        if (!Rs2Widget.isWidgetVisible(bossHealthBarWidgetID)) {
            BreakHandlerScript.setLockState(true);
            boss.walkToBoss(equipmentNormal, bossName, bossLobbyLocation);
            boss.fightPreparation(equipmentNormal);
            boss.enterBossArena(bossName, bossStatueObjectID, bossLobbyLocation);
            sleepUntil(() -> Rs2Widget.isWidgetVisible(bossHealthBarWidgetID), 5_000);
        }

        int bossNpcID = NpcID.PMOON_BOSS_BLOOD_MOON_VIS;
        while (Rs2Widget.isWidgetVisible(bossHealthBarWidgetID) || Rs2Npc.getNpc(bossNpcID) != null) {
            if (isSpecialAttack1Sequence()) {
                specialAttack1Sequence();
            }
            else if (isSpecialAttack2Sequence()) {
                specialAttack2Sequence();
            }
            else if (BossHandler.isNormalAttackSequence(sigilNpcID)) {
                boss.normalAttackSequence(sigilNpcID, bossNpcID, ATTACK_TILES, equipmentNormal);
            }
            sleep(300);
        }
        if (debugLogging) {Microbot.log("The " + bossName + "boss health bar widget is no longer visible, the fight must have ended.");}
        Rs2Prayer.disableAllPrayers();
        sleep(2400);
        Rs2Prayer.disableAllPrayers();
        BossHandler.rechargeRunEnergy();
        BreakHandlerScript.setLockState(false);
        return State.IDLE;
    }

    /**
     * Returns True if the jaguar NPC is found.
     */
    public boolean isSpecialAttack1Sequence() {
        Rs2NpcModel jaguar = Rs2Npc.getNpc(NpcID.PMOON_BOSS_JAGUAR);
        return (jaguar != null && Rs2Widget.isWidgetVisible(bossHealthBarWidgetID));
    }

    /**
     * Returns True if the Moonfire gameobject is found and the boss is not attackable.
     */
    public boolean isSpecialAttack2Sequence() {
        return Rs2GameObject.exists(ObjectID.PMOON_BOSS_BLOOD_FIRE) && Rs2Npc.getNpc(sigilNpcID) == null && Rs2Widget.isWidgetVisible(bossHealthBarWidgetID);
    }

    /**  Blood Moon – Blood Rain Special Attack Handler */
    public void specialAttack2Sequence() {
        if (debugLogging) {Microbot.log("The moonfire has spawned – We've entered Special Attack 2, the Blood Rain Sequence");}
        sleepUntil(() -> !Rs2Player.isAnimating(),5000);
        Rs2Prayer.disableAllPrayers();
        Rs2Walker.walkFastCanvas(afterRainTile,true);
        sleepUntil(() -> Rs2Player.getWorldLocation().equals(afterRainTile));
        while (isSpecialAttack2Sequence()) {
            WorldPoint playerTile = Rs2Player.getWorldLocation();
            GameObject bloodPool = Rs2GameObject.getGameObject(o -> o.getId() == ObjectID.PMOON_BOSS_BLOOD_POOL && o.getWorldLocation().equals(playerTile));
            if (bloodPool != null) {
                if (debugLogging) {Microbot.log("Standing on dangerous tile: " + playerTile);}
                WorldPoint safeTile = getRandomSafeTile(ObjectID.PMOON_BOSS_BLOOD_POOL, 1);
                if (debugLogging) {Microbot.log("Safe tile calculated to be: " + safeTile);}
                if (safeTile != null) {
                    Rs2Walker.walkFastCanvas(safeTile, true);
                    if (debugLogging) {Microbot.log("Now standing on safe tile: " + safeTile);}
                    sleepUntil(() -> Rs2Player.getWorldLocation().equals(safeTile), 600);
                }
            }
            boss.eatIfNeeded();
            boss.drinkIfNeeded();
            sleep(600);
        }
    }

    /**  Blood Moon – Blood Jaguar Special Attack Handler */
    public void specialAttack1Sequence() {
        if (debugLogging) {Microbot.log("Entering Special Attack Sequence: Blood Jaguar");}
        Rs2Prayer.disableAllPrayers();
        final long startMs = System.currentTimeMillis();

        /* 1  find the sigil NPC (2×2, SW tile = sigilLoc) */
        Rs2NpcModel sigilNpc = Rs2Npc.getNpcs(n -> n.getId() == sigilNpcID).findFirst().orElse(null);
        if (sigilNpc == null) {
            if (debugLogging) {Microbot.log("no sigil NPC – bail");}
            return;
        }
        WorldPoint sigilLocation = sigilNpc.getWorldLocation();
        /* 2 derive the trio of tiles for this rotation */
        Locations.Rotation rot = Locations.bloodJaguarRotation(sigilLocation);
        if (rot == null) {
            if (debugLogging) {Microbot.log("Unknown sigil tile: " + sigilLocation);}
            return;
        }
        WorldPoint attackTile = rot.attack;
        WorldPoint evadeTile = rot.evade;
        WorldPoint spawnTile = rot.spawn;

        if (debugLogging) {Microbot.log("Resolved rotation. attackTile=" + attackTile + "  evadeTile=" + evadeTile + "  spawnTile=" + spawnTile);}

        if (attackTile == null || spawnTile == null || evadeTile == null) {
            if (debugLogging) {Microbot.log("Unknown sigilLocation tile: " + sigilLocation);}
            return;
        }

        /* 3 ─ move onto Attack tile ---------------------------------------- */
        if (debugLogging) {Microbot.log("Moving to attackTile " + attackTile);}
        Rs2Walker.walkFastCanvas(attackTile, true);
        sleep(600);
        if (!Rs2Player.getWorldLocation().equals(attackTile)) {
            Rs2Walker.walkFastCanvas(attackTile, true);
            sleepUntil(() -> Rs2Player.getWorldLocation().equals(attackTile));
        }
        boolean arrived = sleepUntil(() -> Rs2Player.getWorldLocation().equals(attackTile), 5_000);
        if (debugLogging) {Microbot.log(arrived
                ? "Arrived on attackTile"
                : "Failed to reach attackTile – aborting");}
        if (!arrived) return;

        /* 4 ─ lock the target jaguar (SW tile == spawnTile) ---------------- */
        Rs2NpcModel targetJaguar = Rs2Npc.getNpcs(n ->
                        n.getId() == NpcID.PMOON_BOSS_JAGUAR &&
                                n.getWorldLocation().equals(spawnTile))
                .findFirst().orElse(null);
        if (targetJaguar == null) {
            if (debugLogging) {Microbot.log("jaguar not on expected spawn");}
            return;
        }

        /* 5 ─ Jaguar evade & attack sequence ----------------------------- */
        final long TIMEOUT_MS = 30_000;

        while (isSpecialAttack1Sequence() && System.currentTimeMillis() - startMs < TIMEOUT_MS) {
            int bloodPoolTick = moonsOfPerilPlugin.bloodPoolTick;
            if (targetJaguar.getAnimation() == 12492) {
                sleep(600);
                break;
            }
            if (bloodPoolTick == 3) {
                if (debugLogging) {Microbot.log("EVADE to " + evadeTile);}
                Rs2Walker.walkFastCanvas(evadeTile, true);
            } else if (bloodPoolTick == 5) {
                if (debugLogging) {Microbot.log("ATTACK jaguar");}
                Rs2Npc.attack(targetJaguar);
            }
            else if (bloodPoolTick == 6) {
                if (debugLogging) {Microbot.log("Clicking on ground to stop attacking");}
                Rs2Walker.walkFastCanvas(attackTile, true);
            }
            sleep(100);   // OnGameTick method in MoonsOfPerilPlugin.java handles the game ticks
        }
    }

    /**
     * Returns a random safe WorldPoint within {@code distance} tiles of the player.
     * A tile is unsafe if it contains a GameObject with {@code dangerousId}.
     * Returns {@code null} when no safe tile exists.
     */
    public static WorldPoint getRandomSafeTile(int dangerousId, int distance)
    {
        // ── 1. player location ──
        WorldPoint centre = Rs2Player.getWorldLocation();
        if (centre == null) return null;

        // ── 2. collect all dangerous tiles within radius ──
        Set<WorldPoint> dangerTiles = Rs2GameObject
                .getGameObjects(o -> o.getId() == dangerousId, distance)
                .stream()
                .map(GameObject::getWorldLocation)
                .collect(Collectors.toSet());

        // ── 3. enumerate every tile in the square centred on the player ──
        List<WorldPoint> candidates = new ArrayList<>();
        for (int dx = -distance; dx <= distance; dx++) {
            for (int dy = -distance; dy <= distance; dy++) {
                WorldPoint wp = new WorldPoint(
                        centre.getX() + dx,
                        centre.getY() + dy,
                        centre.getPlane());
                if (!dangerTiles.contains(wp)) {
                    candidates.add(wp);
                }
            }
        }
        if (candidates.isEmpty()) {
            return null;
        }
        // ── 4. pick one at random ──
        return candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
    }
}
