package net.runelite.client.plugins.microbot.aiofighter.skill;

import java.util.Arrays;
import java.util.List;

// Script: Combat Interface Setup
// Script id: 420
// URL: https://github.com/Joshua-F/cs2-scripts
// scripts/[clientscript,combat_interface_setup].cs2

public enum WeaponAttackType {
    TYPE_1(1, Arrays.asList(
            new AttackOption(AttackType.CHOP, "Accurate", "Slash", List.of("Attack XP")),
            new AttackOption(AttackType.HACK, "Aggressive", "Slash", List.of("Strength XP")),
            new AttackOption(AttackType.SMASH, "Aggressive", "Crush", List.of("Strength XP")),
            new AttackOption(AttackType.BLOCK, "Defensive", "Slash", List.of("Defence XP"))
    )),
    TYPE_2(2, Arrays.asList(
            new AttackOption(AttackType.POUND, "Accurate", "Crush", List.of("Attack XP")),
            new AttackOption(AttackType.PUMMEL, "Aggressive", "Crush", List.of("Strength XP")),
            new AttackOption(AttackType.BLOCK, "Defensive", "Crush", List.of("Defence XP"))
    )),
    TYPE_3(3, Arrays.asList(
            new AttackOption(AttackType.ACCURATE, "Accurate", "Standard", List.of("Ranged XP")),
            new AttackOption(AttackType.RAPID, "Rapid", "Standard", List.of("Ranged XP")),
            new AttackOption(AttackType.LONGRANGE, "Longrange", "Standard", Arrays.asList("Ranged XP", "Defence XP"))
    )),
    TYPE_4(4, Arrays.asList(
            new AttackOption(AttackType.CHOP, "Accurate", "Slash", List.of("Attack XP")),
            new AttackOption(AttackType.SLASH, "Aggressive", "Slash", List.of("Strength XP")),
            new AttackOption(AttackType.LUNGE, "Controlled", "Stab", List.of("Shared XP")),
            new AttackOption(AttackType.BLOCK, "Defensive", "Slash", List.of("Defence XP"))
    )),
    TYPE_5(5, Arrays.asList(
            new AttackOption(AttackType.ACCURATE, "Accurate", "Heavy", List.of("Ranged XP")),
            new AttackOption(AttackType.RAPID, "Rapid", "Heavy", List.of("Ranged XP")),
            new AttackOption(AttackType.LONGRANGE, "Longrange", "Heavy", Arrays.asList("Ranged XP", "Defence XP"))
    )),
    TYPE_6(6, Arrays.asList(
            new AttackOption(AttackType.SCORCH, "Aggressive", "Slash", List.of("Strength XP")),
            new AttackOption(AttackType.FLARE, "Accurate", "Ranged", List.of("Ranged XP")),
            new AttackOption(AttackType.BLAZE, "Defensive", "Magic", List.of("Magic XP"))
    )),
    TYPE_7(7, Arrays.asList(
            new AttackOption(AttackType.SHORT_FUSE, "Short fuse", "Heavy", List.of("Ranged XP")),
            new AttackOption(AttackType.MEDIUM_FUSE, "Medium fuse", "Heavy", List.of("Ranged XP")),
            new AttackOption(AttackType.LONG_FUSE, "Long fuse", "Heavy", Arrays.asList("Ranged XP", "Defence XP"))
    )),
    TYPE_8(8, Arrays.asList(
            new AttackOption(AttackType.AIM_AND_FIRE, "Aim and Fire", "", List.of()),
            new AttackOption(AttackType.KICK, "Aggressive", "Crush", List.of("Strength XP"))
    )),
    TYPE_9(9, Arrays.asList(
            new AttackOption(AttackType.CHOP, "Accurate", "Slash", List.of("Attack XP")),
            new AttackOption(AttackType.SLASH, "Aggressive", "Slash", List.of("Strength XP")),
            new AttackOption(AttackType.LUNGE, "Controlled", "Stab", List.of("Shared XP")),
            new AttackOption(AttackType.BLOCK, "Defensive", "Slash", List.of("Defence XP"))
    )),
    TYPE_10(10, Arrays.asList(
            new AttackOption(AttackType.CHOP, "Accurate", "Slash", List.of("Attack XP")),
            new AttackOption(AttackType.SLASH, "Aggressive", "Slash", List.of("Strength XP")),
            new AttackOption(AttackType.SMASH, "Aggressive", "Crush", List.of("Strength XP")),
            new AttackOption(AttackType.BLOCK, "Defensive", "Slash", List.of("Defence XP"))
    )),
    TYPE_11(11, Arrays.asList(
            new AttackOption(AttackType.SPIKE, "Accurate", "Stab", List.of("Attack XP")),
            new AttackOption(AttackType.IMPALE, "Aggressive", "Stab", List.of("Strength XP")),
            new AttackOption(AttackType.SMASH, "Aggressive", "Crush", List.of("Strength XP")),
            new AttackOption(AttackType.BLOCK, "Defensive", "Stab", List.of("Defence XP"))
    )),
    TYPE_12(12, Arrays.asList(
            new AttackOption(AttackType.JAB, "Controlled", "Stab", List.of("Shared XP")),
            new AttackOption(AttackType.SWIPE, "Aggressive", "Slash", List.of("Strength XP")),
            new AttackOption(AttackType.FEND, "Defensive", "Stab", List.of("Defence XP"))
    )),
    TYPE_13(13, Arrays.asList(
            new AttackOption(AttackType.BASH, "Accurate", "Crush", List.of("Attack XP")),
            new AttackOption(AttackType.POUND, "Aggressive", "Crush", List.of("Strength XP")),
            new AttackOption(AttackType.BLOCK, "Defensive", "Crush", List.of("Defence XP"))
    )),
    TYPE_14(14, Arrays.asList(
            new AttackOption(AttackType.REAP, "Accurate", "Slash", List.of("Attack XP")),
            new AttackOption(AttackType.CHOP, "Aggressive", "Slash", List.of("Strength XP")),
            new AttackOption(AttackType.JAB, "Aggressive", "Crush", List.of("Strength XP")),
            new AttackOption(AttackType.BLOCK, "Defensive", "Slash", List.of("Defence XP"))
    )),
    TYPE_15(15, Arrays.asList(
            new AttackOption(AttackType.LUNGE, "Controlled", "Stab", List.of("Shared XP")),
            new AttackOption(AttackType.SWIPE, "Controlled", "Slash", List.of("Shared XP")),
            new AttackOption(AttackType.POUND, "Controlled", "Crush", List.of("Shared XP")),
            new AttackOption(AttackType.BLOCK, "Defensive", "Stab", List.of("Defence XP"))
    )),
    TYPE_16(16, Arrays.asList(
            new AttackOption(AttackType.POUND, "Accurate", "Crush", List.of("Attack XP")),
            new AttackOption(AttackType.PUMMEL, "Aggressive", "Crush", List.of("Strength XP")),
            new AttackOption(AttackType.SPIKE, "Controlled", "Stab", List.of("Shared XP")),
            new AttackOption(AttackType.BLOCK, "Defensive", "Crush", List.of("Defence XP"))
    )),
    TYPE_17(17, Arrays.asList(
            new AttackOption(AttackType.STAB, "Accurate", "Stab", List.of("Attack XP")),
            new AttackOption(AttackType.LUNGE, "Aggressive", "Stab", List.of("Strength XP")),
            new AttackOption(AttackType.SLASH, "Aggressive", "Slash", List.of("Strength XP")),
            new AttackOption(AttackType.BLOCK, "Defensive", "Stab", List.of("Defence XP"))
    )),
    TYPE_18(18, Arrays.asList(
            new AttackOption(AttackType.BASH, "Accurate", "Crush", List.of("Attack XP")),
            new AttackOption(AttackType.POUND, "Aggressive", "Crush", List.of("Strength XP")),
            new AttackOption(AttackType.FOCUS, "Defensive", "Crush", List.of("Defence XP"))
    )),
    TYPE_19(19, Arrays.asList(
            new AttackOption(AttackType.ACCURATE, "Accurate", "Light", List.of("Ranged XP")),
            new AttackOption(AttackType.RAPID, "Rapid", "Light", List.of("Ranged XP")),
            new AttackOption(AttackType.LONGRANGE, "Longrange", "Light", Arrays.asList("Ranged XP", "Defence XP"))
    )),
    TYPE_20(20, Arrays.asList(
            new AttackOption(AttackType.FLICK, "Accurate", "Slash", List.of("Attack XP")),
            new AttackOption(AttackType.LASH, "Controlled", "Slash", List.of("Shared XP")),
            new AttackOption(AttackType.DEFLECT, "Defensive", "Slash", List.of("Defence XP"))
    )),
    TYPE_21(21, Arrays.asList(
            new AttackOption(AttackType.JAB, "Accurate", "Stab", List.of("Attack XP")),
            new AttackOption(AttackType.SWIPE, "Aggressive", "Slash", List.of("Strength XP")),
            new AttackOption(AttackType.FEND, "Defensive", "Crush", List.of("Defence XP"))
    )),
    TYPE_22(22, Arrays.asList(
            new AttackOption(AttackType.JAB, "Accurate", "Stab", List.of("Attack XP")),
            new AttackOption(AttackType.SWIPE, "Aggressive", "Slash", List.of("Strength XP")),
            new AttackOption(AttackType.FEND, "Defensive", "Crush", List.of("Defence XP"))
    )),
    TYPE_23(23, Arrays.asList(
            new AttackOption(AttackType.CHOP, "Accurate", "Slash", List.of("Attack XP")),
            new AttackOption(AttackType.SLASH, "Aggressive", "Slash", List.of("Strength XP")),
            new AttackOption(AttackType.SMASH, "Aggressive", "Crush", List.of("Strength XP")),
            new AttackOption(AttackType.BLOCK, "Defensive", "Slash", List.of("Defence XP"))
    )),
    TYPE_24(24, Arrays.asList(
            new AttackOption(AttackType.ACCURATE, "Accurate", "", List.of("Magic XP")),
            new AttackOption(AttackType.ACCURATE, "Accurate", "", List.of("Magic XP")),
            new AttackOption(AttackType.LONGRANGE, "Longrange", "", Arrays.asList("Magic XP", "Defence XP"))
    )),
    TYPE_25(25, Arrays.asList(
            new AttackOption(AttackType.LUNGE, "Accurate", "Stab", List.of("Attack XP")),
            new AttackOption(AttackType.SWIPE, "Aggressive", "Slash", List.of("Strength XP")),
            new AttackOption(AttackType.POUND, "Controlled", "Crush", List.of("Shared XP")),
            new AttackOption(AttackType.BLOCK, "Defensive", "Stab", List.of("Defence XP"))
    )),
    TYPE_26(26, Arrays.asList(
            new AttackOption(AttackType.JAB, "Controlled", "Stab", List.of("Shared XP")),
            new AttackOption(AttackType.SWIPE, "Aggressive", "Slash", List.of("Strength XP")),
            new AttackOption(AttackType.FEND, "Defensive", "Stab", List.of("Defence XP"))
    )),
    TYPE_27(27, Arrays.asList(
            new AttackOption(AttackType.POUND, "Aggressive", "Crush", List.of("Strength XP")),
            new AttackOption(AttackType.PUMMEL, "Aggressive", "Crush", List.of("Strength XP")),
            new AttackOption(AttackType.SMASH, "Aggressive", "Crush", List.of("Strength XP"))
    )),
    TYPE_28(28, Arrays.asList(
            new AttackOption(AttackType.PUMMEL, "Accurate", "Crush", List.of("Attack XP")),
            new AttackOption(AttackType.BLOCK, "Defensive", "", List.of())
    )),
    TYPE_29(29, Arrays.asList(
            new AttackOption(AttackType.ACCURATE, "Accurate", "", List.of("Magic XP")),
            new AttackOption(AttackType.ACCURATE, "Accurate", "", List.of("Magic XP")),
            new AttackOption(AttackType.LONGRANGE, "Longrange", "", Arrays.asList("Magic XP", "Defence XP"))
    )),
    TYPE_30(30, Arrays.asList(
            new AttackOption(AttackType.STAB, "Accurate", "Stab", List.of("Attack XP")),
            new AttackOption(AttackType.LUNGE, "Aggressive", "Stab", List.of("Strength XP")),
            new AttackOption(AttackType.POUND, "Aggressive", "Crush", List.of("Strength XP")),
            new AttackOption(AttackType.BLOCK, "Defensive", "Stab", List.of("Defence XP"))
    ));

    private final int typeId;
    private final List<AttackOption> attackOptions;

    WeaponAttackType(int typeId, List<AttackOption> attackOptions) {
        this.typeId = typeId;
        this.attackOptions = attackOptions;
    }

    public int getTypeId() {
        return typeId;
    }

    public List<AttackOption> getAttackOptions() {
        return attackOptions;
    }

    public static WeaponAttackType getById(int id) {
        for (WeaponAttackType type : values()) {
            if (type.typeId == id) {
                return type;
            }
        }
        return null; // or throw an exception
    }
}
