package net.runelite.client.plugins.microbot.VoxPlugins.util.pvm.enums;

/**
 * weapon attack style enum for PvM combat system
 * represents all possible attack styles from game varbit (Varbit 43)
 *
 * OSRS Attack Style Varbit Values:
 * - 0 = Accurate (usually melee)
 * - 1 = Aggressive (usually melee)
 * - 2 = Defensive (usually melee)
 * - 3 = Controlled (melee)
 * - 4 = Rapid (ranged)
 * - 5 = Longrange (ranged)
 * - 6 = Autocast (magic)
 *
 * Author: Voxslyvae
 */
public enum WeaponStyle {
    /**
     * accurate style - melee
     * +3 attack bonus
     */
    ACCURATE(0, CombatStyle.MELEE, "Accurate"),

    /**
     * aggressive style - melee
     * +3 strength bonus
     */
    AGGRESSIVE(1, CombatStyle.MELEE, "Aggressive"),

    /**
     * defensive style - melee/ranged
     * +3 defence bonus
     */
    DEFENSIVE(2, CombatStyle.MELEE, "Defensive"),

    /**
     * controlled style - melee
     * +1 attack/strength/defence
     */
    CONTROLLED(3, CombatStyle.MELEE, "Controlled"),

    /**
     * rapid style - ranged
     * faster attack speed
     */
    RAPID(4, CombatStyle.RANGED, "Rapid"),

    /**
     * longrange style - ranged
     * +2 attack range, +3 defence
     */
    LONGRANGE(5, CombatStyle.RANGED, "Longrange"),

    /**
     * autocast style - magic
     * standard magic casting
     */
    AUTOCAST(6, CombatStyle.MAGIC, "Autocast"),

    /**
     * unknown/default style
     */
    UNKNOWN(-1, null, "Unknown");

    private final int varbitValue;
    private final CombatStyle combatStyle;
    private final String displayName;

    WeaponStyle(int varbitValue, CombatStyle combatStyle, String displayName) {
        this.varbitValue = varbitValue;
        this.combatStyle = combatStyle;
        this.displayName = displayName;
    }

    /**
     * get varbit value (for Varbit 43)
     */
    public int getVarbitValue() {
        return varbitValue;
    }

    /**
     * get associated combat style (melee, ranged, magic)
     */
    public CombatStyle getCombatStyle() {
        return combatStyle;
    }

    /**
     * get display name
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * check if this is a melee style
     */
    public boolean isMelee() {
        return combatStyle == CombatStyle.MELEE;
    }

    /**
     * check if this is a ranged style
     */
    public boolean isRanged() {
        return combatStyle == CombatStyle.RANGED;
    }

    /**
     * check if this is a magic style
     */
    public boolean isMagic() {
        return combatStyle == CombatStyle.MAGIC;
    }

    /**
     * get weapon style from varbit value
     *
     * @param varbitValue varbit 43 value (0-6)
     * @return weapon style enum
     */
    public static WeaponStyle fromVarbitValue(int varbitValue) {
        for (WeaponStyle style : values()) {
            if (style.varbitValue == varbitValue) {
                return style;
            }
        }
        return UNKNOWN;
    }

    /**
     * get weapon style from string (case-insensitive)
     *
     * @param styleName style name
     * @return weapon style enum
     */
    public static WeaponStyle fromString(String styleName) {
        if (styleName == null) {
            return UNKNOWN;
        }

        String normalized = styleName.toUpperCase().trim();

        // exact match first
        for (WeaponStyle style : values()) {
            if (style.name().equals(normalized)) {
                return style;
            }
        }

        // partial match (display name)
        for (WeaponStyle style : values()) {
            if (style.displayName.equalsIgnoreCase(styleName)) {
                return style;
            }
        }

        return UNKNOWN;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
