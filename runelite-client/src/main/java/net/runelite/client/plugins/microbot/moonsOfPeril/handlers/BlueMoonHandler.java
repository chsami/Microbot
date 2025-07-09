package net.runelite.client.plugins.microbot.moonsOfPeril.handlers;

import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.AnimationID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.breakhandler.BreakHandlerScript;
import net.runelite.client.plugins.microbot.moonsOfPeril.enums.GameObjects;
import net.runelite.client.plugins.microbot.moonsOfPeril.enums.Locations;
import net.runelite.client.plugins.microbot.moonsOfPeril.enums.State;
import net.runelite.client.plugins.microbot.moonsOfPeril.enums.Widgets;
import net.runelite.client.plugins.microbot.moonsOfPeril.moonsOfPerilConfig;
import net.runelite.client.plugins.microbot.util.Rs2InventorySetup;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.prayer.Rs2Prayer;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.tile.Rs2Tile;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class BlueMoonHandler implements BaseHandler {

    private static final String bossName = "Blue Moon";
    private static final int bossHealthBarWidgetID = Widgets.BOSS_HEALTH_BAR.getID();
    private static final int bossStatusWidgetID = Widgets.BLUE_MOON_ID.getID();
    private static final int bossStatueObjectID = GameObjects.BLUE_MOON_STATUE_ID.getID();
    private static final WorldPoint bossLobbyLocation = Locations.BLUE_LOBBY.getWorldPoint();
    private static final WorldPoint bossArenaCenter = Locations.BLUE_ARENA_CENTER.getWorldPoint();
    private static final WorldPoint[] ATTACK_TILES = Locations.blueAttackTiles();
    private static final WorldPoint AFTER_TORNADO = Locations.BLUE_ATTACK_1.getWorldPoint();
    private final int sigilNpcID = GameObjects.SIGIL_NPC_ID.getID();
    private final Rs2InventorySetup equipmentNormal;
    private final moonsOfPerilConfig cfg;
    private final boolean enableBoss;
    private final BossHandler boss;
    private final boolean debugLogging;

    public BlueMoonHandler(moonsOfPerilConfig cfg, Rs2InventorySetup equipmentNormal) {
        this.cfg = cfg;
        this.equipmentNormal = equipmentNormal;
        this.enableBoss = cfg.enableBlue();
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
            sleepUntil(() -> Rs2Widget.isWidgetVisible(bossHealthBarWidgetID),5_000);
        }
        int bossNpcID = NpcID.PMOON_BOSS_BLUE_MOON_VIS;
        while (Rs2Widget.isWidgetVisible(bossHealthBarWidgetID) || Rs2Npc.getNpc(bossNpcID) != null) {
            if (isSpecialAttack1Sequence()) {
                specialAttack1Sequence();
            }
            else if (isSpecialAttack2Sequence()) {
                specialAttack2Sequence(this.cfg);
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
     * Returns True if the tornado NPC is found.
     */
    public boolean isSpecialAttack1Sequence() {
        return (Rs2Npc.getNpc(NpcID.PMOON_BOSS_WINTER_STORM) != null && Rs2Widget.isWidgetVisible(bossHealthBarWidgetID) && Rs2Npc.getNpc(sigilNpcID) == null);
    }

    public void specialAttack1Sequence()
    {
        sleep(2_400);
        Rs2Prayer.disableAllPrayers();
        if (debugLogging) {Microbot.log("Running to safe tile and waiting out the sequence");}
        Rs2Walker.walkFastCanvas(AFTER_TORNADO, true);
        boss.eatIfNeeded();
        boss.drinkIfNeeded();
        if (debugLogging) {Microbot.log("Sleeping until the special attack sequence is over");}
        sleepUntil(() -> Rs2Npc.getNpc(sigilNpcID) != null || !Rs2Widget.isWidgetVisible(bossHealthBarWidgetID), 35_000);
    }


    /**
     * Returns True if the icicle NPC is found.
     */
    public boolean isSpecialAttack2Sequence() {
        Rs2NpcModel icicle = Rs2Npc.getNpc(NpcID.PMOON_BOSS_ICICLE_UNCRACKED);
        Rs2NpcModel sigil = Rs2Npc.getNpc(sigilNpcID);
        if (icicle != null && sigil == null) {
            if (debugLogging) {Microbot.log("An icicle has spawned – We've entered Special Attack 2 Sequence");}
            return true;
        }
        return false;
    }

    /**  Blue Moon – Special Attack 2  (“weapon-freeze / icicle smash”)  */
    public void specialAttack2Sequence(moonsOfPerilConfig cfg)
    {
        final int  ICICLE_NPC_ID    = NpcID.PMOON_BOSS_ICICLE_UNCRACKED;
        final int  ICICLE_ANIM_ID   = AnimationID.VFX_DJINN_BLUE_ICE_BLOCK_IDLE_02;
        final long POLL_TIMEOUT_MS  = 5_000;
        final long PHASE_TIMEOUT_MS = 32_000;
        final WorldPoint SAFE_SPOT  = new WorldPoint(1440, 9681, 0);

        java.util.function.Supplier<String> ts =
                () -> "[" + System.currentTimeMillis() + "] ";

        if (debugLogging) {Microbot.log(ts.get() + "specialAttack2Sequence() START");}
        Rs2Walker.walkFastCanvas(bossArenaCenter, true);

        /* ---------- 1. Identify the animated icicle ----------------------- */
        List<Rs2NpcModel> matches = Collections.emptyList();
        long pollStart = System.currentTimeMillis();

        while (isSpecialAttack2Sequence() && matches.isEmpty() &&
                System.currentTimeMillis() - pollStart < POLL_TIMEOUT_MS)
        {
            matches = Rs2Npc.getNpcs(n ->
                            n.getId() == ICICLE_NPC_ID &&
                                    n.getAnimation() == ICICLE_ANIM_ID)
                    .collect(Collectors.toList());
            if (matches.isEmpty()) sleep(300);
        }

        if (matches.isEmpty()) {
            if (debugLogging) {Microbot.log(ts.get() + "Timed-out waiting for animated icicle — aborting");}
            return;
        }
        if (!isSpecialAttack2Sequence()) {
            return;
        }

        Rs2NpcModel icicle = matches.get(0);
        if (debugLogging) {Microbot.log(ts.get() + "Icicle found at " + icicle.getWorldLocation());}

        /* ---------- 2. Attack + dodge loop ------------------------------- */

        long phaseStart = System.currentTimeMillis();

        if (debugLogging) {Microbot.log(ts.get() + "Entering attack-and-dodge loop (timeout " + PHASE_TIMEOUT_MS + " ms)");}
        Rs2Prayer.disableAllPrayers();

        Rs2InventorySetup invSetup = equipmentNormal;
        while (!invSetup.doesEquipmentMatch() &&
                System.currentTimeMillis() - phaseStart < PHASE_TIMEOUT_MS)
        {
            if (!Rs2Combat.inCombat()) {
                Rs2Npc.attack(icicle);
                }
            WorldPoint attackTile = Rs2Player.getWorldLocation();
            if (debugLogging) {Microbot.log(ts.get() + "Attack location calculated as: " + attackTile);}
            sleepUntil(() -> BossHandler.inDanger(attackTile) || invSetup.doesEquipmentMatch(),3_000);
            if (invSetup.doesEquipmentMatch()) {
                break;
            }
            if (BossHandler.inDanger(attackTile)) {
                if (debugLogging) {Microbot.log(ts.get() + "Standing on dangerous tile: " + attackTile);}
                WorldPoint safeTile = Rs2Tile.getSafeTiles(1).get(0);
                if (debugLogging) {Microbot.log(ts.get() + "Safe tile calculated to be: " + safeTile);}

                if (safeTile != null) {
                    Rs2Walker.walkFastCanvas(safeTile, true);
                    if (debugLogging) {Microbot.log(ts.get() + "Now standing on safe tile: " + safeTile);}
                    sleepUntil(() -> !BossHandler.inDanger(attackTile));
                    if (debugLogging) {Microbot.log(ts.get() + "Attack tile now calculated as safe: " + attackTile);}
                }
            }
        }

        /* ---------- 3. Retreat to safespot ------------------------------- */
        if (debugLogging) {Microbot.log(ts.get() + "Retreating to SAFE_SPOT " + SAFE_SPOT);}
        Rs2Walker.walkFastCanvas(SAFE_SPOT, true);
        boss.eatIfNeeded();
        boss.drinkIfNeeded();

        if (debugLogging) {Microbot.log(ts.get() + "Waiting for all icicles to despawn…");}
        sleepUntil(() -> Rs2Npc.getNpc(ICICLE_NPC_ID) == null);

        if (debugLogging) {Microbot.log(ts.get() + "specialAttack2Sequence() COMPLETE");}
    }

}
