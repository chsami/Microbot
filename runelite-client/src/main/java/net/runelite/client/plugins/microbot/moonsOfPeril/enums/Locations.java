package net.runelite.client.plugins.microbot.moonsOfPeril.enums;

import net.runelite.api.coords.WorldPoint;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

import net.runelite.client.plugins.microbot.util.coords.Rs2WorldPoint;
import org.apache.commons.lang3.tuple.Pair;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Getter
@RequiredArgsConstructor
public enum Locations {
    ECLIPSE_LOBBY(new WorldPoint(1466, 9632, 0)),
    ECLIPSE_ARENA_CENTER(WorldPoint.fromRegion(6038, 16, 32, 0)),
    ECLIPSE_ATTACK_1(WorldPoint.fromRegion(6038, 17, 35, 0)),
    ECLIPSE_ATTACK_2(WorldPoint.fromRegion(6038, 19, 33, 0)),
    ECLIPSE_ATTACK_3(WorldPoint.fromRegion(6038, 19, 31, 0)),
    ECLIPSE_ATTACK_4(WorldPoint.fromRegion(6038, 13, 31, 0)),
    ECLIPSE_ATTACK_5(WorldPoint.fromRegion(6038, 13, 33, 0)),
    ECLIPSE_ATTACK_6(new WorldPoint(1487,9629,0)),
    ECLIPSE_EXIT_TILE(WorldPoint.fromRegion(6038, 7, 32, 0)),
    ECLIPSE_SHIELD_SPAWN_TILE(new WorldPoint(1491, 9628, 0)),
    BLOOD_LOBBY(new WorldPoint(1413, 9632, 0)),
    BLOOD_ARENA_CENTER(new WorldPoint(1392, 9632, 0)),
    BLOOD_ATTACK_1(WorldPoint.fromRegion(5526, 47, 29, 0)),
    BLOOD_ATTACK_2(WorldPoint.fromRegion(5526, 45, 31, 0)),
    BLOOD_ATTACK_3(WorldPoint.fromRegion(5526, 45, 33, 0)),
    BLOOD_ATTACK_4(WorldPoint.fromRegion(5526, 51, 33, 0)),
    BLOOD_ATTACK_5(WorldPoint.fromRegion(5526, 51, 31, 0)),
    BLOOD_ATTACK_6(WorldPoint.fromRegion(5526, 49, 35, 0)),
    BLUE_LOBBY(new WorldPoint(1440, 9658, 0)),
    BLUE_ARENA_CENTER(WorldPoint.fromRegion(5783, 32, 16, 0)),
    BLUE_ATTACK_1(WorldPoint.fromRegion(5783, 31, 13, 0)),
    BLUE_ATTACK_2(WorldPoint.fromRegion(5783, 29, 15, 0)),
    BLUE_ATTACK_3(WorldPoint.fromRegion(5783, 29, 17, 0)),
    BLUE_ATTACK_4(WorldPoint.fromRegion(5783, 35, 17, 0)),
    BLUE_ATTACK_5(WorldPoint.fromRegion(5783, 35, 15, 0)),
    BLUE_ATTACK_6(WorldPoint.fromRegion(5783, 33, 19, 0)),
    BLUE_ICESHARD_SAFEPOT(WorldPoint.fromRegion(5783, 32, 17, 0)),
    REWARDS_CHEST_LOBBY(new WorldPoint(1513, 9578, 0)),

    /* ── Blood-Jaguar spawn sets (2×2 NPC) ───────────────────────────── */
    BLOOD_JAGUAR_SW_1 (new WorldPoint(1390, 9637, 0)),
    BLOOD_JAGUAR_ATK_1(new WorldPoint(1390, 9636, 0)),
    BLOOD_JAGUAR_EVD_1(new WorldPoint(1390, 9635, 0)),

    BLOOD_JAGUAR_SW_2 (new WorldPoint(1393, 9637, 0)),
    BLOOD_JAGUAR_ATK_2(new WorldPoint(1393, 9636, 0)),
    BLOOD_JAGUAR_EVD_2(new WorldPoint(1393, 9635, 0)),

    BLOOD_JAGUAR_SW_3 (new WorldPoint(1386, 9633, 0)),
    BLOOD_JAGUAR_ATK_3(new WorldPoint(1388, 9633, 0)),
    BLOOD_JAGUAR_EVD_3(new WorldPoint(1389, 9633, 0)),

    BLOOD_JAGUAR_SW_4 (new WorldPoint(1386, 9630, 0)),
    BLOOD_JAGUAR_ATK_4(new WorldPoint(1388, 9630, 0)),
    BLOOD_JAGUAR_EVD_4(new WorldPoint(1389, 9630, 0)),

    BLOOD_JAGUAR_SW_5 (new WorldPoint(1390, 9626, 0)),
    BLOOD_JAGUAR_ATK_5(new WorldPoint(1390, 9628, 0)),
    BLOOD_JAGUAR_EVD_5(new WorldPoint(1390, 9629, 0)),

    BLOOD_JAGUAR_SW_6 (new WorldPoint(1393, 9626, 0)),
    BLOOD_JAGUAR_ATK_6(new WorldPoint(1393, 9628, 0)),
    BLOOD_JAGUAR_EVD_6(new WorldPoint(1393, 9629, 0)),

    BLOOD_JAGUAR_SW_7 (new WorldPoint(1397, 9630, 0)),
    BLOOD_JAGUAR_ATK_7(new WorldPoint(1396, 9630, 0)),
    BLOOD_JAGUAR_EVD_7(new WorldPoint(1395, 9630, 0)),

    BLOOD_JAGUAR_SW_8 (new WorldPoint(1397, 9633, 0)),
    BLOOD_JAGUAR_ATK_8(new WorldPoint(1396, 9633, 0)),
    BLOOD_JAGUAR_EVD_8(new WorldPoint(1395, 9633, 0));

    public final WorldPoint worldPoint;

    /** All Eclipse Moon attack tiles as a WorldPoint[] array. */
    public static WorldPoint[] eclipseAttackTiles() {
        return Arrays.stream(values())
                .filter(l -> l.name().startsWith("ECLIPSE_ATTACK_"))
                .map(l -> l.worldPoint)
                .toArray(WorldPoint[]::new);      // ← returns a WorldPoint[]
    }

    /** All Blue Moon attack tiles as a WorldPoint[] array. */
    public static WorldPoint[] blueAttackTiles() {
        return Arrays.stream(values())
                .filter(l -> l.name().startsWith("BLUE_ATTACK_"))
                .map(l -> l.worldPoint)
                .toArray(WorldPoint[]::new);      // ← returns a WorldPoint[]
    }

    /** All Blood Moon attack tiles as a WorldPoint[] array. */
    public static WorldPoint[] bloodAttackTiles() {
        return Arrays.stream(values())
                .filter(l -> l.name().startsWith("BLOOD_ATTACK_"))
                .map(l -> l.worldPoint)
                .toArray(WorldPoint[]::new);      // ← returns a WorldPoint[]
    }

    /* ── Blood-Jaguar helpers ────────────────────────────────────────── */
    /* ─ lookup object ─ */
    public static final class Rotation {
        final public WorldPoint attack, evade, spawn;
        Rotation(WorldPoint atk, WorldPoint evd, WorldPoint spn) {
            attack = atk; evade = evd; spawn = spn;
        }
    }

    /* ─ Evade-or-Attack tile → Rotation ─ */
    private static final java.util.Map<WorldPoint, Rotation> BLOOD_JAG_LOOKUP;
    static {
        java.util.Map<WorldPoint, Rotation> map = new java.util.HashMap<>();

        for (int i = 1; i <= 8; i++) {
            WorldPoint spawn  = valueOf("BLOOD_JAGUAR_SW_"  + i).worldPoint;
            WorldPoint attack = valueOf("BLOOD_JAGUAR_ATK_" + i).worldPoint;
            WorldPoint evade  = valueOf("BLOOD_JAGUAR_EVD_" + i).worldPoint;

            Rotation r = new Rotation(attack, evade, spawn);

            map.put(evade , r);    // key 1
            map.put(attack, r);    // key 2
        }
        BLOOD_JAG_LOOKUP = java.util.Collections.unmodifiableMap(map);
    }

    /** Given *either* the Evade- or Attack-tile, return the full rotation. */
    public static Rotation bloodJaguarRotation(WorldPoint tile) {
        return BLOOD_JAG_LOOKUP.get(tile);
    }
}

