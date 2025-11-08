package net.runelite.client.plugins.microbot.VoxPlugins.PVM.gauntlet.util;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.NPC;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.prayer.Rs2PrayerEnum;

import java.util.Map;
import java.util.Set;

/**
 * gauntlet-specific constants and utilities
 */
@Slf4j
public class Rs2GauntletUtil {

    // individual npc id constants (for convenience)
    public static final int CRYSTALLINE_HUNLLEF = NpcID.CRYSTAL_HUNLLEF_MELEE;    // 9021
    public static final int CRYSTALLINE_HUNLLEF_9022 = NpcID.CRYSTAL_HUNLLEF_RANGED; // 9022
    public static final int CRYSTALLINE_HUNLLEF_9023 = NpcID.CRYSTAL_HUNLLEF_MAGIC;  // 9023
    public static final int CORRUPTED_HUNLLEF = NpcID.CRYSTAL_HUNLLEF_MELEE_HM;      // 9035
    public static final int CORRUPTED_HUNLLEF_9036 = NpcID.CRYSTAL_HUNLLEF_RANGED_HM; // 9036
    public static final int CORRUPTED_HUNLLEF_9037 = NpcID.CRYSTAL_HUNLLEF_MAGIC_HM;  // 9037

    // weapon item ids - normal gauntlet (crystalline)
    public static final int CRYSTAL_HALBERD_T1 = ItemID.GAUNTLET_MELEE_T1;     // 23895
    public static final int CRYSTAL_HALBERD_T2 = ItemID.GAUNTLET_MELEE_T2;     // 23896
    public static final int CRYSTAL_HALBERD_T3 = ItemID.GAUNTLET_MELEE_T3;     // 23897
    public static final int CRYSTAL_STAFF_T1 = ItemID.GAUNTLET_MAGIC_T1;       // 23898
    public static final int CRYSTAL_STAFF_T2 = ItemID.GAUNTLET_MAGIC_T2;       // 23899
    public static final int CRYSTAL_STAFF_T3 = ItemID.GAUNTLET_MAGIC_T3;       // 23900
    public static final int CRYSTAL_BOW_T1 = ItemID.GAUNTLET_RANGED_T1;        // 23901
    public static final int CRYSTAL_BOW_T2 = ItemID.GAUNTLET_RANGED_T2;        // 23902
    public static final int CRYSTAL_BOW_T3 = ItemID.GAUNTLET_RANGED_T3;        // 23903

    // weapon item ids - corrupted gauntlet (hard mode)
    public static final int CORRUPTED_HALBERD_T1 = ItemID.GAUNTLET_MELEE_T1_HM;     // 23849
    public static final int CORRUPTED_HALBERD_T2 = ItemID.GAUNTLET_MELEE_T2_HM;     // 23850
    public static final int CORRUPTED_HALBERD_T3 = ItemID.GAUNTLET_MELEE_T3_HM;     // 23851
    public static final int CORRUPTED_STAFF_T1 = ItemID.GAUNTLET_MAGIC_T1_HM;       // 23852
    public static final int CORRUPTED_STAFF_T2 = ItemID.GAUNTLET_MAGIC_T2_HM;       // 23853
    public static final int CORRUPTED_STAFF_T3 = ItemID.GAUNTLET_MAGIC_T3_HM;       // 23854
    public static final int CORRUPTED_BOW_T1 = ItemID.GAUNTLET_RANGED_T1_HM;        // 23855
    public static final int CORRUPTED_BOW_T2 = ItemID.GAUNTLET_RANGED_T2_HM;        // 23856
    public static final int CORRUPTED_BOW_T3 = ItemID.GAUNTLET_RANGED_T3_HM;        // 23857

    // weapon sets by tier
    public static final Set<Integer> MELEE_WEAPONS = Set.of(
        CRYSTAL_HALBERD_T1, CRYSTAL_HALBERD_T2, CRYSTAL_HALBERD_T3,
        CORRUPTED_HALBERD_T1, CORRUPTED_HALBERD_T2, CORRUPTED_HALBERD_T3
    );
    public static final Set<Integer> MAGIC_WEAPONS = Set.of(
        CRYSTAL_STAFF_T1, CRYSTAL_STAFF_T2, CRYSTAL_STAFF_T3,
        CORRUPTED_STAFF_T1, CORRUPTED_STAFF_T2, CORRUPTED_STAFF_T3
    );
    public static final Set<Integer> RANGED_WEAPONS = Set.of(
        CRYSTAL_BOW_T1, CRYSTAL_BOW_T2, CRYSTAL_BOW_T3,
        CORRUPTED_BOW_T1, CORRUPTED_BOW_T2, CORRUPTED_BOW_T3
    );

    // npc ids (VERIFIED from RLCGPerformanceTracker)
    public static final Set<Integer> HUNLLEF_IDS = Set.of(
        CRYSTALLINE_HUNLLEF,        // base/melee
        CRYSTALLINE_HUNLLEF_9022,   // ranged
        CRYSTALLINE_HUNLLEF_9023,   // magic
        CORRUPTED_HUNLLEF,          // corrupted base/melee
        CORRUPTED_HUNLLEF_9036,     // corrupted ranged
        CORRUPTED_HUNLLEF_9037      // corrupted magic
    );

    public static final Set<Integer> HUNLLEF_DEATH_IDS = Set.of(
        NpcID.CRYSTAL_HUNLLEF_DEATH,      // 9024 - death animation
        NpcID.CRYSTAL_HUNLLEF_DEATH_HM    // 9038 - corrupted death animation
    );

    // hazard ids (tornadoes and floor tiles)
    public static final int TORNADO_NORMAL = NpcID.CRYSTAL_HUNLLEF_CRYSTALS;     // 9025
    public static final int TORNADO_CORRUPTED = NpcID.CRYSTAL_HUNLLEF_CRYSTALS_HM; // 9039
    public static final int DAMAGE_TILE_ID = 36048;  // dangerous floor tile object ID

    // varbit ids (VERIFIED from RLCGPerformanceTracker)
    public static final int GAUNTLET_BOSS_START_VARBIT = 9177;
    public static final int GAUNTLET_MAZE_VARBIT = 9178;

    // attack cycle configuration
    public static final int HUNLLEF_ATTACK_CYCLE_LENGTH = 4;
    public static final int PLAYER_ATTACK_CYCLE_LENGTH = 6;
    public static final int HUNLLEF_ATTACK_SPEED_TICKS = 5; // 3.0 seconds (confirmed OSRS Wiki)

    // projectile ids for prayer-disabling attacks (one per cycle)
    public static final int PRAYER_DISABLE_PROJECTILE_1 = 1713; // normal gauntlet
    public static final int PRAYER_DISABLE_PROJECTILE_2 = 1714; // corrupted gauntlet

    // animation ids (VERIFIED from RLCGPerformanceTracker)
    public static final int BOSS_ATTACK_ANIMATION = 8419;              // AMBIGUOUS: ranged OR magic
    public static final int BOSS_STOMP_ANIMATION = 8420;               // melee stomp (deterministic)
    public static final int BOSS_SWITCH_TO_MAGE_ANIMATION = 8754;     // hunllef is using mage
    public static final int BOSS_SWITCH_TO_RANGE_ANIMATION = 8755;    // hunllef is using range

    // npc id to prayer mapping (MOST RELIABLE - NPC ID changes with attack style)
    private static final Map<Integer, Rs2PrayerEnum> NPC_ID_PRAYERS = Map.ofEntries(
        // normal mode
        Map.entry(CRYSTALLINE_HUNLLEF, Rs2PrayerEnum.PROTECT_MELEE),
        Map.entry(CRYSTALLINE_HUNLLEF_9022, Rs2PrayerEnum.PROTECT_RANGE),
        Map.entry(CRYSTALLINE_HUNLLEF_9023, Rs2PrayerEnum.PROTECT_MAGIC),
        // corrupted mode
        Map.entry(CORRUPTED_HUNLLEF, Rs2PrayerEnum.PROTECT_MELEE),
        Map.entry(CORRUPTED_HUNLLEF_9036, Rs2PrayerEnum.PROTECT_RANGE),
        Map.entry(CORRUPTED_HUNLLEF_9037, Rs2PrayerEnum.PROTECT_MAGIC)
    );

    /**
     * check if npc is hunllef
     */
    public static boolean isHunllef(NPC npc) {
        return npc != null && HUNLLEF_IDS.contains(npc.getId());
    }

    /**
     * check if hunllef is dead
     */
    public static boolean isHunllefDead(NPC npc) {
        return npc != null && HUNLLEF_DEATH_IDS.contains(npc.getId());
    }

    /**
     * get prayer based on hunllef's current npc id
     * most reliable method as npc id changes with attack style
     */
    public static Rs2PrayerEnum getPrayerForNpcId(int npcId) {
        return NPC_ID_PRAYERS.getOrDefault(npcId, null);
    }

    /**
     * get next prayer based on attack cycle count
     */
    public static Rs2PrayerEnum getNextPrayerForCycle(int attackCount) {
        // hunllef starts with ranged, does 4, then magic for 4, repeat
        int cyclePosition = attackCount % (HUNLLEF_ATTACK_CYCLE_LENGTH * 2);

        if (cyclePosition < HUNLLEF_ATTACK_CYCLE_LENGTH) {
            return Rs2PrayerEnum.PROTECT_RANGE; // first 4 are ranged
        } else {
            return Rs2PrayerEnum.PROTECT_MAGIC; // next 4 are magic
        }
    }

    /**
     * check if npc is a tornado
     */
    public static boolean isTornado(NPC npc) {
        if (npc == null) return false;
        int id = npc.getId();
        return id == TORNADO_NORMAL || id == TORNADO_CORRUPTED;
    }

    /**
     * check if game object is a dangerous floor tile
     */
    public static boolean isDamageTile(int gameObjectId) {
        return gameObjectId == DAMAGE_TILE_ID;
    }

    /**
     * check if boss fight has started via varbit
     */
    public static boolean isBossFightActive() {
        return Microbot.getClient().getVarbitValue(GAUNTLET_BOSS_START_VARBIT) == 1;
    }

    /**
     * check if in gauntlet maze via varbit
     */
    public static boolean isInGauntlet() {
        return Microbot.getClient().getVarbitValue(GAUNTLET_MAZE_VARBIT) != 0;
    }

    /**
     * check if projectile ID is a prayer-disabling attack
     */
    public static boolean isPrayerDisableProjectile(int projectileId) {
        return projectileId == PRAYER_DISABLE_PROJECTILE_1 || projectileId == PRAYER_DISABLE_PROJECTILE_2;
    }

    /**
     * check if weapon ID is a melee weapon (halberd)
     */
    public static boolean isMeleeWeapon(int weaponId) {
        return MELEE_WEAPONS.contains(weaponId);
    }

    /**
     * check if weapon ID is a magic weapon (staff)
     */
    public static boolean isMagicWeapon(int weaponId) {
        return MAGIC_WEAPONS.contains(weaponId);
    }

    /**
     * check if weapon ID is a ranged weapon (bow)
     */
    public static boolean isRangedWeapon(int weaponId) {
        return RANGED_WEAPONS.contains(weaponId);
    }

    /**
     * get weapon tier (1, 2, or 3)
     * returns 0 if not a gauntlet weapon
     */
    public static int getWeaponTier(int weaponId) {
        // check tier 3 weapons (best)
        if (weaponId == CRYSTAL_HALBERD_T3 || weaponId == CORRUPTED_HALBERD_T3 ||
            weaponId == CRYSTAL_STAFF_T3 || weaponId == CORRUPTED_STAFF_T3 ||
            weaponId == CRYSTAL_BOW_T3 || weaponId == CORRUPTED_BOW_T3) {
            return 3;
        }

        // check tier 2 weapons
        if (weaponId == CRYSTAL_HALBERD_T2 || weaponId == CORRUPTED_HALBERD_T2 ||
            weaponId == CRYSTAL_STAFF_T2 || weaponId == CORRUPTED_STAFF_T2 ||
            weaponId == CRYSTAL_BOW_T2 || weaponId == CORRUPTED_BOW_T2) {
            return 2;
        }

        // check tier 1 weapons
        if (weaponId == CRYSTAL_HALBERD_T1 || weaponId == CORRUPTED_HALBERD_T1 ||
            weaponId == CRYSTAL_STAFF_T1 || weaponId == CORRUPTED_STAFF_T1 ||
            weaponId == CRYSTAL_BOW_T1 || weaponId == CORRUPTED_BOW_T1) {
            return 1;
        }

        return 0; // not a gauntlet weapon
    }
}
