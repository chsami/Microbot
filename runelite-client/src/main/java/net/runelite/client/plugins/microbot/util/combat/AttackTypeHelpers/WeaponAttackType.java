package net.runelite.client.plugins.microbot.util.combat.AttackTypeHelpers;

import com.google.common.base.Preconditions;
import lombok.Getter;
// https://github.com/Joshua-F/cs2-scripts/blob/master/scripts/%5Bclientscript%2Ccombat_interface_setup%5D.cs2
public enum WeaponAttackType {
    UNARMED(0, AttackType.CRUSH, AttackType.CRUSH, null, AttackType.CRUSH),
    // AXE
    TYPE_1(1, AttackType.SLASH, AttackType.SLASH, AttackType.CRUSH, AttackType.SLASH),
    // BLUNT
    TYPE_2(2, AttackType.CRUSH, AttackType.CRUSH, null, AttackType.CRUSH),
    // BOW
    TYPE_3(3, AttackType.RANGED, AttackType.RANGED, null, AttackType.RANGED),
    // CLAW
    TYPE_4(4, AttackType.SLASH, AttackType.SLASH, AttackType.STAB, AttackType.SLASH),
    // CROSSBOW
    TYPE_5(5, AttackType.RANGED, AttackType.RANGED, null, AttackType.RANGED),
    //SALAMANDER
    TYPE_6(6, AttackType.SLASH, AttackType.RANGED, AttackType.MAGIC, null),
    // CHINCHOMPA
    TYPE_7(7, AttackType.RANGED, AttackType.RANGED, null, AttackType.RANGED),
    // GUN
    TYPE_8(8, AttackType.NONE, AttackType.CRUSH, null, null),
    // SLASH SWORD
    TYPE_9(9, AttackType.SLASH, AttackType.SLASH, AttackType.STAB, AttackType.SLASH),
    // TWO HANDED SWORD
    TYPE_10(10, AttackType.SLASH, AttackType.SLASH, AttackType.CRUSH, AttackType.SLASH),
    // PICKAXE
    TYPE_11(11, AttackType.STAB, AttackType.STAB, AttackType.CRUSH, AttackType.STAB),
    // POLEARM
    TYPE_12(12, AttackType.STAB, AttackType.SLASH, null, AttackType.STAB),
    // POLESTAFF
    TYPE_13(13, AttackType.CRUSH, AttackType.CRUSH, null, AttackType.CRUSH),
    // SCYTHE
    TYPE_14(14, AttackType.SLASH, AttackType.SLASH, AttackType.CRUSH, AttackType.SLASH),
    // SPEAR
    TYPE_15(15, AttackType.STAB, AttackType.SLASH, AttackType.CRUSH, AttackType.STAB),
    // SPIKED
    TYPE_16(16, AttackType.CRUSH, AttackType.CRUSH, AttackType.STAB, AttackType.CRUSH),
    // STAB SWORD
    TYPE_17(17, AttackType.STAB, AttackType.STAB, AttackType.SLASH, AttackType.STAB),
    // STAFF
    TYPE_18(18, AttackType.CRUSH, AttackType.CRUSH, null, AttackType.CRUSH, AttackType.MAGIC, AttackType.MAGIC),
    // THROWN
    TYPE_19(19, AttackType.RANGED, AttackType.RANGED, null, AttackType.RANGED),
    // WHIP
    TYPE_20(20, AttackType.SLASH, AttackType.SLASH, null, AttackType.SLASH),
    // BLADED STAFF
    TYPE_21(21, AttackType.STAB, AttackType.SLASH, null, AttackType.CRUSH, AttackType.MAGIC, AttackType.MAGIC),

    TYPE_22(22, AttackType.STAB, AttackType.SLASH, null, AttackType.CRUSH, AttackType.MAGIC, AttackType.MAGIC),
    // TWO HANDED SWORD GODSWORD
    TYPE_23(23, AttackType.SLASH, AttackType.SLASH, AttackType.CRUSH, AttackType.SLASH),
    // POWERED STAFF
    TYPE_24(24, AttackType.MAGIC, AttackType.MAGIC, null, AttackType.MAGIC),
    TYPE_25(25, AttackType.STAB, AttackType.SLASH, AttackType.CRUSH, AttackType.STAB),
    TYPE_26(26, AttackType.STAB, AttackType.SLASH, null, AttackType.STAB),
    TYPE_27(27, AttackType.CRUSH, AttackType.CRUSH, null, AttackType.CRUSH),
    // BULWARK
    TYPE_28(28, AttackType.CRUSH, null, null, AttackType.NONE),
    TYPE_29(29, AttackType.MAGIC, AttackType.MAGIC, null, AttackType.MAGIC),
    TYPE_30(30, AttackType.STAB, AttackType.STAB, AttackType.CRUSH, AttackType.STAB);

    @Getter
    private final AttackType[] attackTypes;
    WeaponAttackType(int id, AttackType... attackTypes)
    {
        Preconditions.checkArgument(id == ordinal());
        Preconditions.checkArgument(attackTypes.length == 4 || attackTypes.length == 6,
                "WeaponType " + this + " does not have exactly 4 or 6 attack style arguments");
        this.attackTypes = attackTypes;
    }
    public static WeaponAttackType getWeaponAttackType(int id)
    {
        if (id < 0 || id > values().length)
        {
            return null;
        }
        return values()[id];
    }
}
